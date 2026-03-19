package com.pweg0.jbalance.shop;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.config.JBalanceConfig;
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
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

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
        // NeoForge fires LeftClickBlock multiple times per click — only process the first
        if (event.getAction() != net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.Action.START) return;

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
                // Check blacklist
                String blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
                java.util.List<? extends String> blacklist = JBalanceConfig.SHOP_DISPLAY_BLACKLIST.get();
                if (blacklist.contains(blockId)) {
                    player.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7cEste bloco nao pode ser usado como mostruario!"
                    ));
                    event.setCanceled(true);
                    return;
                }
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

                // Check if there's already an item at this display position — merge if so
                var repo = ShopService.getInstance().getRepo();
                ShopRepository.ShopItemData existing = repo.getShopItemByDisplayPos(
                    displayPos.getX(), displayPos.getY(), displayPos.getZ(),
                    level.dimension().location().toString());

                if (existing != null && existing.shopUuid().equals(uuid)) {
                    // Merge: update existing item with new sell/buy values
                    repo.mergeShopItem(existing.id(),
                        session.sellQty(), session.sellPrice(),
                        session.buyQty(), session.buyPrice());

                    String desc = buildDesc(session, existing);
                    player.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a7aMostruario atualizado!\n" +
                        "\u00a76[JBalance] \u00a77" + held.getHoverName().getString() + " - " + desc
                    ));
                    DiscordWebhook.logShopItemExposed(
                        player.getName().getString(), held.getHoverName().getString(), desc);
                    ShopSetupSession.remove(uuid);
                    event.setCanceled(true);
                    return;
                }

                // Create new shop item in DB
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

                        DiscordWebhook.logShopItemExposed(
                            player.getName().getString(), held.getHoverName().getString(), desc);
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
     * Shows clickable buy/sell options with quantity buttons.
     * All verifications: buyer balance, seller balance, stock, inventory space.
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
            "\u00a76[JBalance] \u00a77========= \u00a76" + itemName + " \u00a77========="
        ));

        // ── BUY section ──
        if (shopItem.sellQty() > 0 && shopItem.sellPrice() > 0) {
            if (stock <= 0) {
                player.sendSystemMessage(Component.literal(
                    "\u00a77Comprar: \u00a7cSem estoque!"
                ));
                notifyOwnerOutOfStock(player.getServer(), shopItem.shopUuid(), shopItem);
                ShopDisplayManager.getInstance().removeDisplay(level, shopItem.id());
                ShopService.getInstance().getRepo().setShopItemActive(shopItem.id(), false);
            } else {
                // Check buyer balance to determine max affordable
                final int fStock = stock;
                EconomyService.getInstance().getBalance(player.getUUID())
                    .whenComplete((buyerBal, exBal) -> player.getServer().execute(() -> {
                        long bal = (exBal != null || buyerBal == null) ? 0 : buyerBal;
                        int maxByMoney = (int) (bal / shopItem.sellPrice());
                        int maxBuy = Math.min(fStock, maxByMoney);

                        if (maxBuy <= 0) {
                            player.sendSystemMessage(Component.literal(
                                "\u00a77Comprar: \u00a76" + CurrencyFormatter.formatBalance(shopItem.sellPrice()) +
                                " \u00a77cada | Estoque: \u00a76" + fStock +
                                " \u00a77| \u00a7cSaldo insuficiente!"
                            ));
                            return;
                        }

                        player.sendSystemMessage(Component.literal(
                            "\u00a77Comprar: \u00a76" + CurrencyFormatter.formatBalance(shopItem.sellPrice()) +
                            " \u00a77cada | Estoque: \u00a76" + fStock +
                            " \u00a77| Seu saldo: \u00a76" + CurrencyFormatter.formatBalance(bal)
                        ));

                        // Build quantity buttons
                        Component buyLine = Component.literal("\u00a7a\u00a7lCOMPRAR: ");
                        int[] qtys = {1, 5, 10, 32, 64};
                        for (int q : qtys) {
                            if (q > maxBuy) continue;
                            long totalCost = shopItem.sellPrice() * q;
                            buyLine = buyLine.copy().append(
                                Component.literal("\u00a7a[" + q + "] ").withStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/jshop comprar " + q))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(q + "x " + itemName + "\nCusto: " +
                                            CurrencyFormatter.formatBalance(totalCost)))))
                            );
                        }
                        // Add max button if not already in the list
                        if (maxBuy > 1 && maxBuy != 5 && maxBuy != 10 && maxBuy != 32 && maxBuy != 64) {
                            long totalCost = shopItem.sellPrice() * maxBuy;
                            buyLine = buyLine.copy().append(
                                Component.literal("\u00a7a[MAX:" + maxBuy + "] ").withStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/jshop comprar " + maxBuy))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal(maxBuy + "x " + itemName + "\nCusto: " +
                                            CurrencyFormatter.formatBalance(totalCost)))))
                            );
                        }

                        // Store pending transaction for /jshop confirmar
                        ShopPendingTransaction.set(player.getUUID(), new ShopPendingTransaction.Pending(
                            ShopPendingTransaction.Type.BUY, shopItem.id(), shopItem.shopUuid(),
                            itemId, maxBuy, shopItem.sellPrice(), 0
                        ));

                        player.sendSystemMessage(buyLine);
                    }));
            }
        }

        // ── SELL section ──
        if (shopItem.buyQty() > 0 && shopItem.buyPrice() > 0) {
            final int fPlayerHas = playerHas;
            EconomyService.getInstance().getBalance(shopItem.shopUuid())
                .whenComplete((ownerBal, ex2) -> player.getServer().execute(() -> {
                    long bal = (ex2 != null || ownerBal == null) ? 0 : ownerBal;
                    int maxByMoney = (int) (bal / shopItem.buyPrice());
                    int maxSell = Math.min(fPlayerHas, maxByMoney);

                    if (fPlayerHas == 0) {
                        player.sendSystemMessage(Component.literal(
                            "\u00a77Vender: \u00a7cVoce nao tem este item!"
                        ));
                        return;
                    }
                    if (maxByMoney == 0) {
                        player.sendSystemMessage(Component.literal(
                            "\u00a77Vender: \u00a76" + CurrencyFormatter.formatBalance(shopItem.buyPrice()) +
                            " \u00a77cada | \u00a7cDono da loja sem saldo!"
                        ));
                        return;
                    }
                    if (maxSell <= 0) {
                        player.sendSystemMessage(Component.literal(
                            "\u00a77Vender: \u00a7cNao e possivel vender no momento."
                        ));
                        return;
                    }

                    long tax1 = ShopService.calculateTax(shopItem.buyPrice());
                    long youGet1 = ShopService.sellerReceives(shopItem.buyPrice());

                    player.sendSystemMessage(Component.literal(
                        "\u00a77Vender: \u00a76" + CurrencyFormatter.formatBalance(shopItem.buyPrice()) +
                        " \u00a77cada (voce recebe \u00a76" + CurrencyFormatter.formatBalance(youGet1) +
                        "\u00a77, taxa 3%) | Voce tem: \u00a76" + fPlayerHas +
                        " \u00a77| Dono pode comprar: \u00a76" + maxByMoney
                    ));

                    // Build quantity buttons
                    Component sellLine = Component.literal("\u00a7e\u00a7lVENDER: ");
                    int[] qtys = {1, 5, 10, 32, 64};
                    for (int q : qtys) {
                        if (q > maxSell) continue;
                        long totalEarn = ShopService.sellerReceives(shopItem.buyPrice() * q);
                        sellLine = sellLine.copy().append(
                            Component.literal("\u00a7e[" + q + "] ").withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/jshop vender2 " + q))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal(q + "x " + itemName + "\nVoce recebe: " +
                                        CurrencyFormatter.formatBalance(totalEarn) + " (taxa 3%)"))))
                        );
                    }
                    if (maxSell > 1 && maxSell != 5 && maxSell != 10 && maxSell != 32 && maxSell != 64) {
                        long totalEarn = ShopService.sellerReceives(shopItem.buyPrice() * maxSell);
                        sellLine = sellLine.copy().append(
                            Component.literal("\u00a7e[MAX:" + maxSell + "] ").withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/jshop vender2 " + maxSell))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal(maxSell + "x " + itemName + "\nVoce recebe: " +
                                        CurrencyFormatter.formatBalance(totalEarn) + " (taxa 3%)"))))
                        );
                    }

                    // Store pending — note: if buy buttons were also shown,
                    // this overwrites. Last section shown wins the pending slot.
                    // To handle both, we check which the user actually clicks.
                    ShopPendingTransaction.set(player.getUUID(), new ShopPendingTransaction.Pending(
                        ShopPendingTransaction.Type.SELL, shopItem.id(), shopItem.shopUuid(),
                        itemId, maxSell, shopItem.buyPrice(), bal
                    ));

                    player.sendSystemMessage(sellLine);
                }));
        }
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
                String removedItemName = BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse(item.itemId())).getDescription().getString();
                ShopService.getInstance().deleteShopItem(item.id());
                ShopDisplayManager.getInstance().removeDisplay(level, item.id());
                player.sendSystemMessage(Component.literal(
                    "\u00a76[JBalance] \u00a77Item removido da loja."
                ));
                DiscordWebhook.logShopItemRemoved(player.getName().getString(), removedItemName);
            }));
    }

    /**
     * When a block is broken, check if it's a shop display or storage block.
     * If so, deactivate the shop item and remove the floating display.
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) player.level();
        String dimension = level.dimension().location().toString();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();

        var repo = ShopService.getInstance().getRepo();

        // Check if this block is a display block
        ShopRepository.ShopItemData displayItem = repo.getShopItemByDisplayPos(x, y, z, dimension);
        if (displayItem != null) {
            // Only the owner (or OP) can break it
            if (!displayItem.shopUuid().equals(player.getUUID()) && !player.hasPermissions(2)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(
                    "\u00a76[JBalance] \u00a7cEssa loja nao e sua!"
                ));
                return;
            }
            deactivateShopItem(player, displayItem, level, "mostruario quebrado");
            return;
        }

        // Check if this block is a storage block
        List<ShopRepository.ShopItemData> storageItems = repo.getShopItemsByStoragePos(x, y, z, dimension);
        if (!storageItems.isEmpty()) {
            // Check ownership — all items in storage belong to same shop owner
            UUID ownerUuid = storageItems.get(0).shopUuid();
            if (!ownerUuid.equals(player.getUUID()) && !player.hasPermissions(2)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal(
                    "\u00a76[JBalance] \u00a7cEssa loja nao e sua!"
                ));
                return;
            }
            for (var item : storageItems) {
                deactivateShopItem(player, item, level, "bau/barrel quebrado");
            }
        }
    }

    private static void deactivateShopItem(ServerPlayer player, ShopRepository.ShopItemData item,
                                            ServerLevel level, String reason) {
        String itemName = BuiltInRegistries.ITEM.get(
            ResourceLocation.parse(item.itemId())).getDescription().getString();

        ShopService.getInstance().getRepo().setShopItemActive(item.id(), false);
        ShopDisplayManager.getInstance().removeDisplay(level, item.id());

        player.sendSystemMessage(Component.literal(
            "\u00a76[JBalance] \u00a7eItem \u00a76" + itemName +
            " \u00a7eremovido da loja (" + reason + ")."
        ));

        DiscordWebhook.logShopItemRemoved(player.getName().getString(),
            itemName + " (" + reason + ")");
    }

    /**
     * Builds description string for merged or new shop item.
     */
    private static String buildDesc(ShopSetupSession.Session session, ShopRepository.ShopItemData existing) {
        StringBuilder desc = new StringBuilder();
        int sellQty = session.sellQty() > 0 ? session.sellQty() : (existing != null ? existing.sellQty() : 0);
        long sellPrice = session.sellPrice() > 0 ? session.sellPrice() : (existing != null ? existing.sellPrice() : 0);
        int buyQty = session.buyQty() > 0 ? session.buyQty() : (existing != null ? existing.buyQty() : 0);
        long buyPrice = session.buyPrice() > 0 ? session.buyPrice() : (existing != null ? existing.buyPrice() : 0);

        if (sellQty > 0) {
            desc.append("Venda: ").append(sellQty).append("x por ")
                .append(CurrencyFormatter.formatBalance(sellPrice));
        }
        if (buyQty > 0) {
            if (!desc.isEmpty()) desc.append(" | ");
            desc.append("Compra: ").append(buyQty).append("x por ")
                .append(CurrencyFormatter.formatBalance(buyPrice));
        }
        return desc.toString();
    }

    private static void notifyOwnerOutOfStock(net.minecraft.server.MinecraftServer server,
                                               UUID ownerUuid, ShopRepository.ShopItemData item) {
        String itemName = BuiltInRegistries.ITEM.get(
            ResourceLocation.parse(item.itemId()))
            .getDescription().getString();
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
        String ownerName = owner != null ? owner.getName().getString() : ownerUuid.toString();
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(
                "\u00a76[JBalance] \u00a7c\u00a7lSem estoque! \u00a7e" + itemName +
                " \u00a77esta sem estoque na sua loja. Reponha o bau!"
            ));
        }
        DiscordWebhook.logShopOutOfStock(ownerName, itemName);
    }
}
