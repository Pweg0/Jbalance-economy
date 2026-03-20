package com.pweg0.jbalance.shop;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.data.db.ShopRepository;
import com.pweg0.jbalance.service.ShopService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages floating item display entities above shop display blocks.
 * Uses ItemDisplay entities (1.19.4+) — purely visual, cannot be picked up.
 */
public class ShopDisplayManager {

    private static ShopDisplayManager instance;

    /**
     * Tracks spawned display entities by their shop item DB id.
     * Used to remove/refresh displays when items are sold out or removed.
     */
    private final ConcurrentHashMap<Integer, UUID> displayEntities = new ConcurrentHashMap<>();

    public ShopDisplayManager() {
        instance = this;
    }

    public static ShopDisplayManager getInstance() {
        return instance;
    }

    /**
     * Spawns a floating item display entity above the given block position.
     * The item hovers at blockPos + (0.5, 1.2, 0.5) and slowly rotates.
     */
    public void spawnDisplay(ServerLevel level, int shopItemId, BlockPos displayBlock, String itemId) {
        if (!JBalanceConfig.SHOP_SHOW_ITEM_DISPLAY.get()) return;

        // Remove existing display for this item if any
        removeDisplay(level, shopItemId);

        ItemStack stack = createItemStack(itemId);
        if (stack.isEmpty()) {
            JBalance.LOGGER.warn("[JBalance] Cannot create display for unknown item: {}", itemId);
            return;
        }

        ItemEntity entity = new ItemEntity(level,
            displayBlock.getX() + 0.5,
            displayBlock.getY() + 1.25,
            displayBlock.getZ() + 0.5,
            stack
        );
        // Make it float: no gravity, no despawn, no pickup
        entity.setNoGravity(true);
        entity.setUnlimitedLifetime();
        entity.setNeverPickUp();
        entity.setDeltaMovement(0, 0, 0);

        // Add tags so we can identify it later
        entity.addTag("jbalance_shop_display");
        entity.addTag("jbalance_sid_" + shopItemId);

        level.addFreshEntity(entity);
        displayEntities.put(shopItemId, entity.getUUID());

        JBalance.LOGGER.debug("[JBalance] Spawned shop display for item {} at {}", itemId, displayBlock);
    }

    /**
     * Removes the floating display entity for a shop item.
     */
    public void removeDisplay(ServerLevel level, int shopItemId) {
        UUID entityUuid = displayEntities.remove(shopItemId);
        if (entityUuid != null) {
            var entity = level.getEntity(entityUuid);
            if (entity != null) {
                entity.discard();
            }
        }
        // Also scan by tag as fallback (entity UUID may have changed after restart)
        level.getEntities().getAll().forEach(e -> {
            if (e.getTags().contains("jbalance_sid_" + shopItemId)) {
                e.discard();
            }
        });
    }

    /**
     * Respawns all displays for a given level. Called on server start.
     */
    public void loadDisplaysForLevel(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        ShopService svc = ShopService.getInstance();
        if (svc == null) return;

        svc.listShops().thenAccept(shops -> {
            for (var shop : shops) {
                if (!shop.dimension().equals(dimension)) continue;
                svc.getShopItems(shop.uuid()).thenAccept(items -> {
                    level.getServer().execute(() -> {
                        for (var item : items) {
                            spawnDisplay(level, item.id(),
                                new BlockPos(item.displayX(), item.displayY(), item.displayZ()),
                                item.itemId());
                        }
                    });
                });
            }
        });
    }

    /**
     * Removes all display entities in a level. Called on server stop.
     */
    public void clearAllDisplays(ServerLevel level) {
        level.getEntities().getAll().forEach(e -> {
            if (e.getTags().contains("jbalance_shop_display")) {
                e.discard();
            }
        });
        displayEntities.clear();
    }

    private ItemStack createItemStack(String itemId) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}
