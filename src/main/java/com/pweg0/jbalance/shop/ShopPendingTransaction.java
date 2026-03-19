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

    private static final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();

    public static void set(UUID player, Pending tx) {
        pending.put(player, tx);
    }

    public static Pending get(UUID player) {
        return pending.get(player);
    }

    public static Pending remove(UUID player) {
        return pending.remove(player);
    }

    public static boolean has(UUID player) {
        return pending.containsKey(player);
    }
}
