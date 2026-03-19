package com.pweg0.jbalance.shop;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.data.db.ShopRepository;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.service.ShopService;
import com.pweg0.jbalance.util.CurrencyFormatter;
import com.pweg0.jbalance.util.DiscordWebhook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Handles block left-clicks during shop setup, and right-clicks for buying/selling.
 *
 * Setup flow:
 * 1. Player runs /jshop venda → session created (WAITING_DISPLAY_BLOCK)
 * 2. Player left-clicks a block → display block recorded, advances to WAITING_STORAGE_BLOCK
 * 3. Player left-clicks a chest/barrel → storage recorded, shop item created, floating display spawned
 *
 * Purchase flow:
 * Player right-clicks a display block with a shop item → purchase processed
 */
public class ShopInteractionHandler {

    private ShopInteractionHandler() {}

    /**
     * Handles left-click on block — both setup flow AND shop interaction (buy/sell).
     * Uses left-click instead of right-click to avoid FTB Chunks protection blocking.
     */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        UUID uuid = player.getUUID();

        // If no setup session active, check if this is a shop display block
        ShopSetupSession.Session session = ShopSetupSession.get(uuid);
        if (session == null) {
            // Try shop interaction (buy/sell)
            tryShopInteraction(player, event);
            return;
        }

        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) player.level();
        String dimension = level.dimension().location().toString();

        // Remove mode: sellQty == -1
        if (session.sellQty() == -1) {
            handleRemoveMode(player, pos, dimension, level);
            event.setCanceled(true);
            return;
        }

        switch (session.phase()) {
            case WAITING_DISPLAY_BLOCK -> {
                // Record display block position
                ShopSetupSession.update(uuid, session.withDisplayBlock(pos));
                player.sendSystemMessage(Component.literal(
                    "\u00a76[JBalance] \u00a7aBloco de exposicao marcado! " +
                    "\u00a7eBata no bau/barrel onde o item ficara guardado."
                ));
                event.setCanceled(true);
            }
            case WAITING_STORAGE_BLOCK -> {
                // Validate storage is a container
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof BaseContainerBlockEntity)) {
                    player.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cIsso nao e um bau ou barrel! Bata em um container."
                    ));
                    event.setCanceled(true);
                    return;
                }

                // Chunk validation: display and storage must be in same chunk
                BlockPos displayPos = session.displayBlock();
                ChunkPos displayChunk = new ChunkPos(displayPos);
                ChunkPos storageChunk = new ChunkPos(pos);
                if (!displayChunk.equals(storageChunk)) {
                    player.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cO bau esta muito longe do mostruario! " +
                        "Deve estar no mesmo chunk."
                    ));
                    event.setCanceled(true);
                    return;
                }

                // Get item from player's main hand
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty()) {
                    player.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cSegure o item que deseja expor na mao!"
                    ));
                    event.setCanceled(true);
                    return;
                }

                String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

                // Create shop item in DB
                ShopService.getInstance().createShopItem(uuid,
                        displayPos.getX(), displayPos.getY(), displayPos.getZ(),
                        pos.getX(), pos.getY(), pos.getZ(),
                        itemId, "",
                        session.sellQty(), session.sellPrice(),
                        session.buyQty(), session.buyPrice())
                    .whenComplete((itemDbId, ex) -> player.getServer().execute(() -> {
                        if (ex != null) {
                            JBalance.LOGGER.error("[JBalance] Failed to create shop item", ex);
                            player.sendSystemMessage(Component.literal(
                                "\u00a76[JBalance] \u00a7cErro ao criar item na loja."
                            ));
                            return;
                        }

                        // Spawn floating display
                        ShopDisplayManager.getInstance().spawnDisplay(level, itemDbId, displayPos, itemId);

                        String desc = "";
                        if (session.sellQty() > 0) {
                            desc = "Venda: " + session.sellQty() + "x por " + CurrencyFormatter.formatBalance(session.sellPrice());
                        }
                        if (session.buyQty() > 0) {
                            desc += (desc.isEmpty() ? "" : " | ") +
                                    "Compra: " + session.buyQty() + "x por " + CurrencyFormatter.formatBalance(session.buyPrice());
                        }

                        player.sendSystemMessage(Component.literal(
                            "\u00a76[JBalance] \u00a7aItem exposto com sucesso!\n" +
                            "\u00a76[JBalance] \u00a77" + held.getHoverName().getString() + " - " + desc
                        ));

                        DiscordWebhook.send("Item Exposto",
                            "**Jogador:** " + player.getName().getString() +
                            "\n**Item:** " + held.getHoverName().getString() +
                            "\n**" + desc + "**",
                            DiscordWebhook.COLOR_EARN);
                    }));

                ShopSetupSession.remove(uuid);
                event.setCanceled(true);
            }
        }
    }

    /**
     * Checks if the left-clicked block is a shop display and shows trade options.
     */
    private static void tryShopInteraction(ServerPlayer player, PlayerInteractEvent.LeftClickBlock event) {
        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) player.level();
        String dimension = level.dimension().location().toString();

        ShopService.getInstance().getShopItemByDisplayPos(
                pos.getX(), pos.getY(), pos.getZ(), dimension)
            .whenComplete((shopItem, ex) -> player.getServer().execute(() -> {
                if (ex != null || shopItem == null) return;
                if (shopItem.shopUuid().equals(player.getUUID())) return;

                event.setCanceled(true);
                showTradeOptions(player, shopItem, level);
            }));
    }

    /**
     * Shows clickable buy/sell options in chat with stock/inventory info.
     */
    private static void showTradeOptions(ServerPlayer player, ShopRepository.ShopItemData shopItem, ServerLevel level) {
        String itemId = shopItem.itemId();
        var targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        String itemName = targetItem.getDescription().getString();

        // Count stock in storage
        BlockPos storagePos = new BlockPos(shopItem.storageX(), shopItem.storageY(), shopItem.storageZ());
        BlockEntity be = level.getBlockEntity(storagePos);
        int stock = 0;
        if (be instanceof BaseContainerBlockEntity container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (s.is(targetItem)) stock += s.getCount();
            }
        }

        // Count how many the player has in inventory
        int playerHas = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(targetItem)) playerHas += s.getCount();
        }

        player.sendSystemMessage(Component.literal(
            "\u00a76[JBalance] \u00a77--- \u00a76" + itemName + " \u00a77---"
        ));

        // Buy option (if shop sells)
        if (shopItem.sellQty() > 0 && shopItem.sellPrice() > 0) {
            if (stock > 0) {
                int maxBuy = stock;
                // Store pending transaction
                ShopPendingTransaction.set(player.getUUID(), new ShopPendingTransaction.Pending(
                    ShopPendingTransaction.Type.BUY, shopItem.id(), shopItem.shopUuid(),
                    itemId, maxBuy, shopItem.sellPrice(), 0
                ));

                Component buyBtn = Component.literal("\u00a7a\u00a7l[COMPRAR]").withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/jshop confirmar "))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Clique e digite a quantidade (1-" + maxBuy + ")")))
                );

                player.sendSystemMessage(Component.literal(
                    "\u00a77Preco: \u00a76" + CurrencyFormatter.formatBalance(shopItem.sellPrice()) +
                    " \u00a77cada | Estoque: \u00a76" + stock + " \u00a77| "
                ).append(buyBtn));
            } else {
                player.sendSystemMessage(Component.literal(
                    "\u00a7cSem estoque para compra!"
                ));
                notifyOwnerOutOfStock(player.getServer(), shopItem.shopUuid(), shopItem);
                ShopDisplayManager.getInstance().removeDisplay(level, shopItem.id());
                ShopService.getInstance().getRepo().setShopItemActive(shopItem.id(), false);
            }
        }

        // Sell option (if shop buys)
        if (shopItem.buyQty() > 0 && shopItem.buyPrice() > 0) {
            // Check how much the shop owner can afford
            final int fPlayerHas = playerHas;
            final int fStock = stock;
            EconomyService.getInstance().getBalance(shopItem.shopUuid())
                .whenComplete((ownerBal, ex2) -> player.getServer().execute(() -> {
                    long bal = (ex2 != null || ownerBal == null) ? 0 : ownerBal;
                    int maxByMoney = (int) (bal / shopItem.buyPrice());
                    int maxSell = Math.min(fPlayerHas, maxByMoney);

                    if (maxSell <= 0) {
                        player.sendSystemMessage(Component.literal(
                            "\u00a7cNao e possivel vender: " +
                            (fPlayerHas == 0 ? "voce nao tem este item" : "dono da loja sem saldo")
                        ));
                        return;
                    }

                    ShopPendingTransaction.set(player.getUUID(), new ShopPendingTransaction.Pending(
                        ShopPendingTransaction.Type.SELL, shopItem.id(), shopItem.shopUuid(),
                        itemId, maxSell, shopItem.buyPrice(), bal
                    ));

                    Component sellBtn = Component.literal("\u00a7e\u00a7l[VENDER]").withStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/jshop confirmar "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Clique e digite a quantidade (1-" + maxSell + ")")))
                    );

                    player.sendSystemMessage(Component.literal(
                        "\u00a77Compra por: \u00a76" + CurrencyFormatter.formatBalance(shopItem.buyPrice()) +
                        " \u00a77cada | Voce tem: \u00a76" + fPlayerHas + " \u00a77| Max: \u00a76" + maxSell + " \u00a77| "
                    ).append(sellBtn));
                }));
        }

        player.sendSystemMessage(Component.literal(
            "\u00a77Use \u00a76/jshop confirmar <qtd> \u00a77para confirmar."
        ));
    }

    private static void handleRemoveMode(ServerPlayer player, BlockPos pos, String dimension, ServerLevel level) {
        ShopSetupSession.remove(player.getUUID());

        ShopService.getInstance().getShopItemByDisplayPos(
                pos.getX(), pos.getY(), pos.getZ(), dimension)
            .whenComplete((item, ex) -> player.getServer().execute(() -> {
                if (item == null) {
                    player.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cNenhum item da loja neste bloco."
                    ));
                    return;
                }
                if (!item.shopUuid().equals(player.getUUID())) {
                    player.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cEste item nao pertence a sua loja!"
                    ));
                    return;
                }
                ShopService.getInstance().deleteShopItem(item.id());
                ShopDisplayManager.getInstance().removeDisplay(level, item.id());
                player.sendSystemMessage(Component.literal(
                    "\u00a76[JBalance] \u00a77Item removido da loja."
                ));
            }));
    }

    private static void notifyOwnerOutOfStock(net.minecraft.server.MinecraftServer server,
                                               UUID ownerUuid, ShopRepository.ShopItemData item) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
        if (owner != null) {
            String itemName = BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(item.itemId()))
                .getDescription().getString();
            owner.sendSystemMessage(Component.literal(
                "\u00a76[JBalance] \u00a7c\u00a7lSem estoque! \u00a7e" + itemName +
                " \u00a77esta sem estoque na sua loja. Reponha o bau!"
            ));
        }
    }
}
