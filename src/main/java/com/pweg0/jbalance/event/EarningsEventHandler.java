package com.pweg0.jbalance.event;

import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.util.CurrencyFormatter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles mob kill earnings for the JBalance economy system.
 * Tags spawner-spawned mobs at spawn, rewards players for killing TOML-listed mobs,
 * and delivers batched kill notifications at a configurable interval.
 * All methods are static (same pattern as PlayerEventHandler).
 */
public class EarningsEventHandler {

    private EarningsEventHandler() {} // prevent instantiation — all methods are static

    // Kill accumulator: in-memory pending state per player, flushed every N ticks
    private static final ConcurrentHashMap<UUID, Long> pendingCoins = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> pendingKills = new ConcurrentHashMap<>();
    private static long flushTickCounter = 0;

    /**
     * Tags mobs spawned from mob spawner blocks with a persistent data flag.
     * Called at spawn time so the flag survives until the mob dies.
     */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) {
            event.getEntity().getPersistentData().putBoolean("jbalance_from_spawner", true);
        }
    }

    /**
     * Handles mob deaths caused by players.
     * Guards: killer must be ServerPlayer, dead entity must not be a player, spawner-tagged mobs are excluded.
     * Reward is looked up from TOML config on the game thread and accumulated for batched notification.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        // Killer must be a ServerPlayer
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        // Skip PvP kills — dead entity must not be a player
        if (event.getEntity() instanceof Player) return;
        // Skip spawner-spawned mobs
        if (event.getEntity().getPersistentData().getBoolean("jbalance_from_spawner")) return;

        // Resolve mob type key, e.g. "minecraft:zombie" — on game thread
        String typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();

        // Read reward map on game thread (config values are not thread-safe)
        Map<String, Long> rewards = JBalanceConfig.parsedMobRewards();
        Long reward = rewards.get(typeKey);
        if (reward == null || reward <= 0) return;

        accumulateKill(player.getUUID(), reward);
    }

    /**
     * Accumulates coins and kill count for a player, to be flushed in the next batch notification.
     */
    private static void accumulateKill(UUID uuid, long reward) {
        pendingCoins.merge(uuid, reward, Long::sum);
        pendingKills.merge(uuid, 1, Integer::sum);
    }

    /**
     * Called every server tick. Flushes the kill accumulator according to the configured interval,
     * sending batched notifications and crediting coins for each player with pending kills.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        flushTickCounter++;

        // Read notification interval on game thread
        long intervalSeconds = JBalanceConfig.KILL_NOTIFICATION_INTERVAL.get();

        boolean shouldFlush;
        if (intervalSeconds <= 0) {
            // Immediate mode: flush every tick
            shouldFlush = true;
        } else {
            // Flush when tick counter exceeds interval (20 ticks per second)
            shouldFlush = flushTickCounter >= intervalSeconds * 20;
        }

        if (!shouldFlush || pendingCoins.isEmpty()) return;

        // Reset counter before iterating to avoid missing kills during flush
        flushTickCounter = 0;

        // Iterate over pending coins and flush each player
        for (UUID uuid : pendingCoins.keySet()) {
            Long coins = pendingCoins.remove(uuid);
            Integer kills = pendingKills.remove(uuid);
            if (coins == null || coins <= 0) continue;
            int killCount = kills != null ? kills : 1;

            // Send notification to online player (game thread — we are inside ServerTickEvent)
            ServerPlayer onlinePlayer = event.getServer().getPlayerList().getPlayer(uuid);
            if (onlinePlayer != null) {
                onlinePlayer.sendSystemMessage(Component.literal(
                    "§6[JBalance] §7Voce recebeu §6" + CurrencyFormatter.formatBalance(coins)
                    + " §7por matar §6" + killCount + " §7mobs"
                ));
            }

            // Credit coins asynchronously — fire and forget
            EconomyService.getInstance().give(uuid, coins);
        }
    }
}
