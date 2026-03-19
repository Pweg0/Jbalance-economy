package com.pweg0.jbalance.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.data.db.ShopRepository;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.service.ShopService;
import com.pweg0.jbalance.shop.ShopDisplayManager;
import com.pweg0.jbalance.shop.ShopPendingTransaction;
import com.pweg0.jbalance.shop.ShopSetupSession;
import com.pweg0.jbalance.util.CurrencyFormatter;
import com.pweg0.jbalance.util.DiscordWebhook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import java.util.UUID;

/**
 * /jshop venda <qtd> <preco> — expose item for sale
 * /jshop compra <qtd> <preco> — create buy order
 * /jshop remover — enter remove mode (hit display to remove)
 * /jshop cancelar — cancel current setup session
 * /jshop help — list commands
 */
public class JShopCommand {

    private JShopCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("jshop")
                .executes(JShopCommand::help)
                // /jshop venda 1 20
                // /jshop venda 1 20 : compra 1 5
                .then(Commands.literal("venda")
                    .requires(JShopCommand::canSell)
                    .then(Commands.argument("vqtd", IntegerArgumentType.integer(1))
                        .then(Commands.argument("vpreco", LongArgumentType.longArg(1))
                            .executes(JShopCommand::sellOnly)
                            .then(Commands.literal(":")
                                .then(Commands.literal("compra")
                                    .then(Commands.argument("cqtd", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("cpreco", LongArgumentType.longArg(1))
                                            .executes(JShopCommand::sellAndBuy))))))))
                // /jshop compra 1 5
                // /jshop compra 1 5 : venda 1 20
                .then(Commands.literal("compra")
                    .requires(JShopCommand::canBuy)
                    .then(Commands.argument("cqtd", IntegerArgumentType.integer(1))
                        .then(Commands.argument("cpreco", LongArgumentType.longArg(1))
                            .executes(JShopCommand::buyOnly)
                            .then(Commands.literal(":")
                                .then(Commands.literal("venda")
                                    .then(Commands.argument("vqtd", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("vpreco", LongArgumentType.longArg(1))
                                            .executes(JShopCommand::buyAndSell))))))))
                .then(Commands.literal("remover")
                    .executes(JShopCommand::remove))
                .then(Commands.literal("cancelar")
                    .executes(JShopCommand::cancel))
                .then(Commands.literal("comprar")
                    .then(Commands.argument("qtd", IntegerArgumentType.integer(1))
                        .executes(ctx -> confirmTyped(ctx, ShopPendingTransaction.Type.BUY))))
                .then(Commands.literal("vender2")
                    .then(Commands.argument("qtd", IntegerArgumentType.integer(1))
                        .executes(ctx -> confirmTyped(ctx, ShopPendingTransaction.Type.SELL))))
                .then(Commands.literal("help")
                    .executes(JShopCommand::help))
        );
    }

    // ── Permission checks ──

    private static boolean canSell(CommandSourceStack src) {
        if (!src.isPlayer()) return true;
        try { return PermissionAPI.getPermission(src.getPlayerOrException(), JBalancePermissions.SHOP_SELL); }
        catch (Exception e) { return true; }
    }

    private static boolean canBuy(CommandSourceStack src) {
        if (!src.isPlayer()) return true;
        try { return PermissionAPI.getPermission(src.getPlayerOrException(), JBalancePermissions.SHOP_BUY); }
        catch (Exception e) { return true; }
    }

    // ── Command handlers — all delegate to startSetup ──

    private static int sellOnly(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return startSetup(ctx,
            IntegerArgumentType.getInteger(ctx, "vqtd"), LongArgumentType.getLong(ctx, "vpreco"),
            0, 0);
    }

    private static int buyOnly(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return startSetup(ctx,
            0, 0,
            IntegerArgumentType.getInteger(ctx, "cqtd"), LongArgumentType.getLong(ctx, "cpreco"));
    }

    private static int sellAndBuy(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return startSetup(ctx,
            IntegerArgumentType.getInteger(ctx, "vqtd"), LongArgumentType.getLong(ctx, "vpreco"),
            IntegerArgumentType.getInteger(ctx, "cqtd"), LongArgumentType.getLong(ctx, "cpreco"));
    }

    private static int buyAndSell(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return startSetup(ctx,
            IntegerArgumentType.getInteger(ctx, "vqtd"), LongArgumentType.getLong(ctx, "vpreco"),
            IntegerArgumentType.getInteger(ctx, "cqtd"), LongArgumentType.getLong(ctx, "cpreco"));
    }

    private static int startSetup(CommandContext<CommandSourceStack> ctx,
                                   int sellQty, long sellPrice, int buyQty, long buyPrice)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();

        ShopService svc = ShopService.getInstance();
        svc.getShop(uuid).whenComplete((shop, ex) -> src.getServer().execute(() -> {
            if (shop == null) {
                src.sendFailure(Component.literal(
                    "\u00a76[JBalance] \u00a7cVoce nao tem uma loja! Use \u00a76/setloja \u00a7cprimeiro."
                ));
                return;
            }
            svc.countShopItems(uuid).whenComplete((count, ex2) -> src.getServer().execute(() -> {
                int limit = getItemLimit(player);
                if (count >= limit) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cLimite de \u00a76" + limit + " \u00a7citens atingido."
                    ));
                    return;
                }
                ShopSetupSession.start(uuid, sellQty, sellPrice, buyQty, buyPrice);

                StringBuilder msg = new StringBuilder("\u00a76[JBalance] \u00a7aCriando mostruario: ");
                if (sellQty > 0) {
                    msg.append("\u00a77Venda: \u00a76").append(sellQty).append("x por ")
                       .append(CurrencyFormatter.formatBalance(sellPrice));
                }
                if (buyQty > 0) {
                    if (sellQty > 0) msg.append(" \u00a77| ");
                    msg.append("\u00a77Compra: \u00a76").append(buyQty).append("x por ")
                       .append(CurrencyFormatter.formatBalance(buyPrice));
                }
                msg.append("\n\u00a76[JBalance] \u00a7eBata no bloco para expor o item.");
                src.sendSuccess(() -> Component.literal(msg.toString()), false);
            }));
        }));
        return Command.SINGLE_SUCCESS;
    }

    // ── /jshop remover ──

    private static int remove(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        // Use a special session with -1 prices to signal "remove mode"
        ShopSetupSession.start(player.getUUID(), -1, -1, -1, -1);
        src.sendSuccess(() -> Component.literal(
            "\u00a76[JBalance] \u00a7eBata no bloco do item que deseja remover."
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    // ── /jshop cancelar ──

    private static int cancel(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        if (ShopSetupSession.remove(player.getUUID()) != null) {
            src.sendSuccess(() -> Component.literal(
                "\u00a76[JBalance] \u00a77Operacao cancelada."
            ), false);
        } else {
            src.sendFailure(Component.literal(
                "\u00a76[JBalance] \u00a7cNenhuma operacao em andamento."
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ── /jshop comprar <qtd> | /jshop vender2 <qtd> ──

    private static int confirmTyped(CommandContext<CommandSourceStack> ctx, ShopPendingTransaction.Type type)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        int qty = IntegerArgumentType.getInteger(ctx, "qtd");

        ShopPendingTransaction.Pending tx = (type == ShopPendingTransaction.Type.BUY)
            ? ShopPendingTransaction.removeBuy(player.getUUID())
            : ShopPendingTransaction.removeSell(player.getUUID());

        if (tx == null) {
            src.sendFailure(Component.literal(
                "\u00a76[JBalance] \u00a7cNenhuma transacao pendente. Interaja com um mostruario primeiro."
            ));
            return Command.SINGLE_SUCCESS;
        }

        if (qty > tx.maxQty()) {
            src.sendFailure(Component.literal(
                "\u00a76[JBalance] \u00a7cQuantidade maxima disponivel: \u00a76" + tx.maxQty()
            ));
            return Command.SINGLE_SUCCESS;
        }

        if (type == ShopPendingTransaction.Type.BUY) {
            processConfirmBuy(player, tx, qty);
        } else {
            processConfirmSell(player, tx, qty);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void processConfirmBuy(ServerPlayer buyer, ShopPendingTransaction.Pending tx, int qty) {
        long totalPrice = tx.pricePerUnit() * qty;
        long tax = ShopService.calculateTax(totalPrice);
        long sellerGets = ShopService.sellerReceives(totalPrice);

        ServerLevel level = (ServerLevel) buyer.level();

        // Verify buyer balance
        EconomyService.getInstance().getBalance(buyer.getUUID())
            .whenComplete((balance, ex) -> buyer.getServer().execute(() -> {
                if (ex != null || balance < totalPrice) {
                    buyer.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cSaldo insuficiente! Preco total: \u00a76" +
                        CurrencyFormatter.formatBalance(totalPrice)
                    ));
                    return;
                }

                // Get shop item data to find storage
                ShopService.getInstance().getRepo().getShopItems(tx.shopOwner()).stream()
                    .filter(i -> i.id() == tx.shopItemId())
                    .findFirst()
                    .ifPresentOrElse(item -> {
                        BlockPos storagePos = new BlockPos(item.storageX(), item.storageY(), item.storageZ());
                        BlockEntity be = level.getBlockEntity(storagePos);
                        if (!(be instanceof BaseContainerBlockEntity container)) {
                            buyer.sendSystemMessage(Component.literal("\u00a76[JBalance] \u00a7cBau nao encontrado!"));
                            return;
                        }

                        var targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tx.itemId()));
                        // Remove from storage
                        int remaining = qty;
                        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                            ItemStack stack = container.getItem(i);
                            if (stack.is(targetItem)) {
                                int take = Math.min(remaining, stack.getCount());
                                stack.shrink(take);
                                remaining -= take;
                            }
                        }
                        container.setChanged();

                        // Give to buyer
                        ItemStack give = new ItemStack(targetItem, qty);
                        if (!buyer.getInventory().add(give)) {
                            buyer.drop(give, false);
                        }

                        // Process payment
                        EconomyService.getInstance().take(buyer.getUUID(), totalPrice);
                        EconomyService.getInstance().give(tx.shopOwner(), sellerGets);

                        String priceStr = CurrencyFormatter.formatBalance(totalPrice);
                        buyer.sendSystemMessage(Component.literal(
                            "\u00a76[JBalance] \u00a7aCompra realizada! \u00a77" + qty + "x " +
                            targetItem.getDescription().getString() + " por \u00a76" + priceStr
                        ));

                        // Notify seller
                        ServerPlayer seller = buyer.getServer().getPlayerList().getPlayer(tx.shopOwner());
                        if (seller != null) {
                            seller.sendSystemMessage(Component.literal(
                                "\u00a76[JBalance] \u00a7a" + buyer.getName().getString() +
                                " \u00a77comprou \u00a76" + qty + "x " + targetItem.getDescription().getString() +
                                "\u00a77! Recebido: \u00a76" + CurrencyFormatter.formatBalance(sellerGets) +
                                " \u00a77(taxa: " + CurrencyFormatter.formatBalance(tax) + ")"
                            ));
                        }

                        String sellerName = seller != null ? seller.getName().getString() : tx.shopOwner().toString();
                        DiscordWebhook.logShopPurchase(
                            buyer.getName().getString(), sellerName,
                            targetItem.getDescription().getString(), qty,
                            priceStr, CurrencyFormatter.formatBalance(tax),
                            CurrencyFormatter.formatBalance(sellerGets));

                        // Check remaining stock
                        int stock = 0;
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            ItemStack s = container.getItem(i);
                            if (s.is(targetItem)) stock += s.getCount();
                        }
                        if (stock == 0) {
                            ShopDisplayManager.getInstance().removeDisplay(level, item.id());
                            ShopService.getInstance().getRepo().setShopItemActive(item.id(), false);
                            if (seller != null) {
                                seller.sendSystemMessage(Component.literal(
                                    "\u00a76[JBalance] \u00a7c\u00a7lSem estoque! \u00a7e" +
                                    targetItem.getDescription().getString() + " \u00a77acabou na sua loja!"
                                ));
                            }
                        }
                    }, () -> buyer.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cItem nao encontrado na loja."
                    )));
            }));
    }

    private static void processConfirmSell(ServerPlayer seller, ShopPendingTransaction.Pending tx, int qty) {
        long totalPrice = tx.pricePerUnit() * qty;
        ServerLevel level = (ServerLevel) seller.level();

        // Check shop owner has enough balance
        EconomyService.getInstance().getBalance(tx.shopOwner())
            .whenComplete((ownerBal, ex) -> seller.getServer().execute(() -> {
                if (ex != null || ownerBal < totalPrice) {
                    seller.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cO dono da loja nao tem saldo suficiente para comprar!"
                    ));
                    return;
                }

                var targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tx.itemId()));

                // Check seller has items
                int heldCount = 0;
                for (int i = 0; i < seller.getInventory().getContainerSize(); i++) {
                    ItemStack s = seller.getInventory().getItem(i);
                    if (s.is(targetItem)) heldCount += s.getCount();
                }
                if (heldCount < qty) {
                    seller.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cVoce nao tem itens suficientes! Tem: \u00a76" + heldCount
                    ));
                    return;
                }

                // Remove items from seller inventory
                int remaining = qty;
                for (int i = 0; i < seller.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack s = seller.getInventory().getItem(i);
                    if (s.is(targetItem)) {
                        int take = Math.min(remaining, s.getCount());
                        s.shrink(take);
                        remaining -= take;
                    }
                }

                // Put items in shop storage
                ShopService.getInstance().getRepo().getShopItems(tx.shopOwner()).stream()
                    .filter(i -> i.id() == tx.shopItemId())
                    .findFirst()
                    .ifPresent(item -> {
                        BlockPos storagePos = new BlockPos(item.storageX(), item.storageY(), item.storageZ());
                        BlockEntity be = level.getBlockEntity(storagePos);
                        if (be instanceof BaseContainerBlockEntity container) {
                            ItemStack toStore = new ItemStack(targetItem, qty);
                            for (int i = 0; i < container.getContainerSize(); i++) {
                                if (toStore.isEmpty()) break;
                                ItemStack slot = container.getItem(i);
                                if (slot.isEmpty()) {
                                    container.setItem(i, toStore.copy());
                                    toStore.setCount(0);
                                } else if (ItemStack.isSameItemSameComponents(slot, toStore) &&
                                           slot.getCount() < slot.getMaxStackSize()) {
                                    int add = Math.min(toStore.getCount(), slot.getMaxStackSize() - slot.getCount());
                                    slot.grow(add);
                                    toStore.shrink(add);
                                }
                            }
                            container.setChanged();
                        }
                    });

                // Payment: take from shop owner, give to seller (with tax)
                long tax = ShopService.calculateTax(totalPrice);
                long sellerGets = ShopService.sellerReceives(totalPrice);
                EconomyService.getInstance().take(tx.shopOwner(), totalPrice);
                EconomyService.getInstance().give(seller.getUUID(), sellerGets);

                seller.sendSystemMessage(Component.literal(
                    "\u00a76[JBalance] \u00a7aVenda realizada! \u00a77" + qty + "x " +
                    targetItem.getDescription().getString() + " por \u00a76" +
                    CurrencyFormatter.formatBalance(sellerGets) +
                    " \u00a77(taxa: " + CurrencyFormatter.formatBalance(tax) + ")"
                ));

                ServerPlayer owner = seller.getServer().getPlayerList().getPlayer(tx.shopOwner());
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7a" + seller.getName().getString() +
                        " \u00a77vendeu \u00a76" + qty + "x " + targetItem.getDescription().getString() +
                        " \u00a77na sua loja!"
                    ));
                }

                String ownerName = owner != null ? owner.getName().getString() : tx.shopOwner().toString();
                DiscordWebhook.logShopSale(
                    seller.getName().getString(), ownerName,
                    targetItem.getDescription().getString(), qty,
                    CurrencyFormatter.formatBalance(totalPrice),
                    CurrencyFormatter.formatBalance(tax),
                    CurrencyFormatter.formatBalance(sellerGets));
            }));
    }

    // ── /jshop help ──

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("\u00a76[JBalance] \u00a77========= \u00a76Loja - Ajuda \u00a77========="), false);
        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("\u00a7e\u00a7lCriando sua loja:"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/setloja \u00a77- Cria sua loja na posicao atual"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/delloja \u00a77- Deleta sua loja"), false);
        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("\u00a7e\u00a7lVisitando lojas:"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/lojas \u00a77- Lista todas as lojas (clique pra teleportar)"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/loja <jogador> \u00a77- Teleporta pra loja do jogador"), false);
        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("\u00a7e\u00a7lExpondo itens:"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/jshop venda <qtd> <preco> \u00a77- Vender item"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/jshop compra <qtd> <preco> \u00a77- Comprar item"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/jshop venda 1 20 : compra 1 5 \u00a77- Venda e compra juntos"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/jshop remover \u00a77- Remove item (bata no mostruario)"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/jshop cancelar \u00a77- Cancela operacao em andamento"), false);
        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("\u00a7e\u00a7lComo funciona:"), false);
        src.sendSuccess(() -> Component.literal("\u00a77 1. Crie sua loja com \u00a76/setloja"), false);
        src.sendSuccess(() -> Component.literal("\u00a77 2. Segure o item na mao e use \u00a76/jshop venda 1 20"), false);
        src.sendSuccess(() -> Component.literal("\u00a77 3. Bata no bloco que sera o mostruario"), false);
        src.sendSuccess(() -> Component.literal("\u00a77 4. Bata no bau/barrel onde o item ficara guardado"), false);
        src.sendSuccess(() -> Component.literal("\u00a77 5. O item aparece flutuando! Outros jogadores"), false);
        src.sendSuccess(() -> Component.literal("\u00a77    clicam no mostruario pra comprar/vender"), false);
        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("\u00a7c\u00a7lObs: \u00a77O bau deve estar no mesmo chunk que o mostruario."), false);
        src.sendSuccess(() -> Component.literal("\u00a77Taxa de \u00a76" + JBalanceConfig.SHOP_TAX_PERCENT.get() + "% \u00a77em toda venda."), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Get item limit from PermissionAPI. Default: 6 items.
     */
    private static int getItemLimit(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, JBalancePermissions.SHOP_ITEM_LIMIT);
        } catch (Exception e) {
            return 6;
        }
    }
}
