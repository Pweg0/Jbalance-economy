package com.pweg0.jbalance.shop;

import net.minecraft.core.BlockPos;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-progress shop item setup sessions.
 * Flow: player runs /jshop venda → hits display block → hits storage block → item created.
 */
public class ShopSetupSession {

    public enum Phase {
        WAITING_DISPLAY_BLOCK,  // "Bata no bloco para expor o item"
        WAITING_STORAGE_BLOCK   // "Bata no bau/barrel onde o item ficara guardado"
    }

    public record Session(Phase phase, int sellQty, long sellPrice, int buyQty, long buyPrice,
                          BlockPos displayBlock) {
        /** Create initial session waiting for display block hit. */
        public static Session create(int sellQty, long sellPrice, int buyQty, long buyPrice) {
            return new Session(Phase.WAITING_DISPLAY_BLOCK, sellQty, sellPrice, buyQty, buyPrice, null);
        }

        /** Advance to next phase after display block is hit. */
        public Session withDisplayBlock(BlockPos pos) {
            return new Session(Phase.WAITING_STORAGE_BLOCK, sellQty, sellPrice, buyQty, buyPrice, pos);
        }
    }

    private static final ConcurrentHashMap<UUID, Session> activeSessions = new ConcurrentHashMap<>();

    public static void start(UUID player, int sellQty, long sellPrice, int buyQty, long buyPrice) {
        activeSessions.put(player, Session.create(sellQty, sellPrice, buyQty, buyPrice));
    }

    public static Session get(UUID player) {
        return activeSessions.get(player);
    }

    public static void update(UUID player, Session session) {
        activeSessions.put(player, session);
    }

    public static Session remove(UUID player) {
        return activeSessions.remove(player);
    }

    public static boolean has(UUID player) {
        return activeSessions.containsKey(player);
    }
}
