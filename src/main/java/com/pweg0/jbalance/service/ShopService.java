package com.pweg0.jbalance.service;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.data.db.ShopRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Business logic for the shop system.
 * Wraps ShopRepository with async execution and tax calculation.
 */
public class ShopService {

    private static ShopService instance;

    private final ShopRepository repo;
    private final ExecutorService dbExecutor;

    /** 3% tax on all sales */
    public static final double TAX_RATE = 0.03;

    public ShopService(ShopRepository repo, ExecutorService dbExecutor) {
        this.repo = repo;
        this.dbExecutor = dbExecutor;
        instance = this;
    }

    public static ShopService getInstance() {
        return instance;
    }

    public ShopRepository getRepo() {
        return repo;
    }

    // ── Shop operations ──

    public CompletableFuture<Void> createShop(UUID uuid, double x, double y, double z,
                                               float yaw, float pitch, String dimension) {
        return CompletableFuture.runAsync(
            () -> repo.createShop(uuid, x, y, z, yaw, pitch, dimension), dbExecutor);
    }

    public CompletableFuture<Void> deleteShop(UUID uuid) {
        return CompletableFuture.runAsync(() -> repo.deleteShop(uuid), dbExecutor);
    }

    public CompletableFuture<ShopRepository.ShopData> getShop(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> repo.getShop(uuid), dbExecutor);
    }

    public CompletableFuture<List<ShopRepository.ShopListEntry>> listShops() {
        return CompletableFuture.supplyAsync(repo::listShops, dbExecutor);
    }

    public CompletableFuture<UUID> findShopByOwnerName(String name) {
        return CompletableFuture.supplyAsync(() -> repo.findShopByOwnerName(name), dbExecutor);
    }

    // ── Shop items ──

    public CompletableFuture<Integer> createShopItem(UUID shopUuid,
                                                      int dx, int dy, int dz,
                                                      int sx, int sy, int sz,
                                                      String itemId, String itemNbt,
                                                      int sellQty, long sellPrice,
                                                      int buyQty, long buyPrice) {
        return CompletableFuture.supplyAsync(
            () -> repo.createShopItem(shopUuid, dx, dy, dz, sx, sy, sz,
                                      itemId, itemNbt, sellQty, sellPrice, buyQty, buyPrice),
            dbExecutor);
    }

    public CompletableFuture<Void> deleteShopItem(int itemId) {
        return CompletableFuture.runAsync(() -> repo.deleteShopItem(itemId), dbExecutor);
    }

    public CompletableFuture<List<ShopRepository.ShopItemData>> getShopItems(UUID shopUuid) {
        return CompletableFuture.supplyAsync(() -> repo.getShopItems(shopUuid), dbExecutor);
    }

    public CompletableFuture<Integer> countShopItems(UUID shopUuid) {
        return CompletableFuture.supplyAsync(() -> repo.countShopItems(shopUuid), dbExecutor);
    }

    public CompletableFuture<ShopRepository.ShopItemData> getShopItemByDisplayPos(
            int dx, int dy, int dz, String dimension) {
        return CompletableFuture.supplyAsync(
            () -> repo.getShopItemByDisplayPos(dx, dy, dz, dimension), dbExecutor);
    }

    // ── Tax calculation ──

    /** Calculate tax amount from a sale price. */
    public static long calculateTax(long salePrice) {
        return Math.max(1, (long) Math.ceil(salePrice * TAX_RATE));
    }

    /** Amount the seller actually receives after tax. */
    public static long sellerReceives(long salePrice) {
        return salePrice - calculateTax(salePrice);
    }
}
