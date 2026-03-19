package com.pweg0.jbalance.shop;

import com.pweg0.jbalance.data.db.ShopRepository;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending buy/sell transactions waiting for player to type a quantity.
 * Flow:
 * 1. Player right-clicks display block → sees [Comprar] [Vender] in chat
 * 2. Player clicks one → sees available qty, asked to type a number
 * 3. Player types the number → transaction processed
 */
public class ShopPendingTransaction {

    public enum Type { BUY, SELL }

    public record Pending(Type type, int shopItemId, UUID shopOwner, String itemId,
                          int maxQty, long pricePerUnit, long ownerBalance) {}

    private static final ConcurrentHashMap<UUID, Pending> pendingBuy = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Pending> pendingSell = new ConcurrentHashMap<>();

    public static void set(UUID player, Pending tx) {
        if (tx.type() == Type.BUY) pendingBuy.put(player, tx);
        else pendingSell.put(player, tx);
    }

    public static Pending getBuy(UUID player) {
        return pendingBuy.get(player);
    }

    public static Pending getSell(UUID player) {
        return pendingSell.get(player);
    }

    public static Pending removeBuy(UUID player) {
        return pendingBuy.remove(player);
    }

    public static Pending removeSell(UUID player) {
        return pendingSell.remove(player);
    }

    public static void clearAll(UUID player) {
        pendingBuy.remove(player);
        pendingSell.remove(player);
    }
}
