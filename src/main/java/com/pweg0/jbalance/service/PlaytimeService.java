package com.pweg0.jbalance.service;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.data.db.PlaytimeRepository;
import com.pweg0.jbalance.util.CurrencyFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Tracks per-player active playtime with AFK detection and milestone rewards.
 * Milestones are granted exactly once per player (per threshold) and persisted to DB.
 * Playtime is flushed to DB every 5 minutes and on player logout/server shutdown.
 *
 * THREAD SAFETY:
 * - onTick, onLogin, onLogout, flushAll are called on the game thread.
 * - DB operations are dispatched to dbExecutor (single daemon thread).
 * - Config values (parsedMilestones, AFK_TIMEOUT_MINUTES) are ALWAYS read on the game thread.
 */
public class PlaytimeService {

    private static PlaytimeService instance;

    private final PlaytimeRepository repo;
    private final ExecutorService dbExecutor;

    // Per-player in-memory state
    private final ConcurrentHashMap<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    // Periodic flush counter (ticks)
    private long flushTickCounter = 0;
    private static final long FLUSH_INTERVAL_TICKS = 6000L; // 5 minutes = 5 * 60 * 20

    /**
     * Mutable per-player state (mutable class avoids object creation every tick).
     */
    private static class PlayerState {
        /** Total non-AFK ticks since tracking began (accumulated across sessions). */
        long activeTicks;
        /** Set of milestone hour thresholds already claimed (authoritative in-memory). */
        final Set<Long> claimedHours;
        double lastX, lastY, lastZ;
        float lastYRot, lastXRot;
        long ticksSinceLastMove;

        PlayerState(long activeTicks, Set<Long> claimedHours,
                    double x, double y, double z, float yRot, float xRot) {
            this.activeTicks = activeTicks;
            this.claimedHours = claimedHours;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastYRot = yRot;
            this.lastXRot = xRot;
            this.ticksSinceLastMove = 0;
        }

        /** Derived active seconds from accumulated non-AFK ticks. */
        long activeSeconds() {
            return activeTicks / 20;
        }
    }

    public PlaytimeService(PlaytimeRepository repo, ExecutorService dbExecutor) {
        this.repo = repo;
        this.dbExecutor = dbExecutor;
        instance = this;
    }

    /**
     * Returns the singleton instance. Available after server starts.
     */
    public static PlaytimeService getInstance() {
        return instance;
    }

    /**
     * Called when a player logs in. Loads playtime data from DB asynchronously.
     * Must be called on the game thread.
     */
    public void onLogin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // Capture position snapshot on game thread before going async
        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yRot = player.getYRot(), xRot = player.getXRot();

        CompletableFuture
            .supplyAsync(() -> repo.loadPlaytime(uuid), dbExecutor)
            .thenAccept(data -> {
                // Back to game thread for state mutation
                player.getServer().execute(() -> {
                    long activeTicks;
                    Set<Long> claimed;
                    if (data != null) {
                        // Convert stored seconds back to ticks for internal tracking
                        activeTicks = data.activeSeconds() * 20;
                        claimed = new HashSet<>(data.claimedHours());
                    } else {
                        activeTicks = 0;
                        claimed = new HashSet<>();
                    }
                    playerStates.put(uuid, new PlayerState(activeTicks, claimed, x, y, z, yRot, xRot));
                    JBalance.LOGGER.debug("[JBalance] Loaded playtime for {}: {}s, claimed: {}",
                        uuid, activeTicks / 20, claimed);
                });
            })
            .exceptionally(ex -> {
                JBalance.LOGGER.error("[JBalance] Failed to load playtime for {}: {}", uuid, ex.getMessage());
                return null;
            });
    }

    /**
     * Called when a player logs out. Flushes playtime to DB asynchronously.
     * Must be called on the game thread.
     */
    public void onLogout(UUID uuid) {
        PlayerState state = playerStates.remove(uuid);
        if (state == null) return;

        long activeSeconds = state.activeSeconds();
        String claimedStr = formatClaimedHours(state.claimedHours);

        CompletableFuture
            .runAsync(() -> repo.upsertPlaytime(uuid, activeSeconds, claimedStr), dbExecutor)
            .exceptionally(ex -> {
                JBalance.LOGGER.error("[JBalance] Failed to flush playtime for {}: {}", uuid, ex.getMessage());
                return null;
            });
    }

    /**
     * Called every server tick per active player. Handles AFK detection, active time accumulation,
     * and milestone checking. Must be called on the game thread.
     */
    public void onTick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerState state = playerStates.get(uuid);
        if (state == null) return; // still loading from DB

        // AFK detection: compare current position and look to stored values
        double cx = player.getX(), cy = player.getY(), cz = player.getZ();
        float cyRot = player.getYRot(), cxRot = player.getXRot();

        boolean moved = cx != state.lastX || cy != state.lastY || cz != state.lastZ
            || cyRot != state.lastYRot || cxRot != state.lastXRot;

        if (moved) {
            state.lastX = cx;
            state.lastY = cy;
            state.lastZ = cz;
            state.lastYRot = cyRot;
            state.lastXRot = cxRot;
            state.ticksSinceLastMove = 0;
        } else {
            state.ticksSinceLastMove++;
        }

        // Read AFK timeout on game thread
        long afkTimeoutTicks = JBalanceConfig.AFK_TIMEOUT_MINUTES.get() * 60L * 20L;

        // AFK check: if no movement for afkTimeoutTicks, do not accumulate
        if (state.ticksSinceLastMove >= afkTimeoutTicks) {
            return;
        }

        // Accumulate one tick of active time
        state.activeTicks++;

        // Check milestones (read config on game thread)
        List<JBalanceConfig.MilestoneEntry> milestones = JBalanceConfig.parsedMilestones();
        long activeSeconds = state.activeSeconds();
        for (JBalanceConfig.MilestoneEntry milestone : milestones) {
            long thresholdSeconds = milestone.hours() * 3600L;
            if (activeSeconds >= thresholdSeconds && !state.claimedHours.contains(milestone.hours())) {
                // Claim in-memory FIRST to prevent double-award (authoritative)
                state.claimedHours.add(milestone.hours());

                // Credit coins async
                EconomyService.getInstance().give(uuid, milestone.reward());

                // Send milestone notification (on game thread)
                player.sendSystemMessage(Component.literal(
                    "§6[JBalance] §aVoce completou " + milestone.hours() + "h de jogo! Recompensa: §6"
                    + CurrencyFormatter.formatBalance(milestone.reward())
                ));

                JBalance.LOGGER.info("[JBalance] Milestone granted to {}: {}h -> {} coins",
                    uuid, milestone.hours(), milestone.reward());
            }
        }
    }

    /**
     * Called every server tick (from EarningsEventHandler.onServerTick).
     * Handles periodic playtime flush (every 5 minutes).
     */
    public void onServerTick() {
        flushTickCounter++;
        if (flushTickCounter >= FLUSH_INTERVAL_TICKS) {
            flushTickCounter = 0;
            if (!playerStates.isEmpty()) {
                JBalance.LOGGER.debug("[JBalance] Periodic playtime flush for {} players", playerStates.size());
                for (Map.Entry<UUID, PlayerState> entry : playerStates.entrySet()) {
                    UUID uuid = entry.getKey();
                    PlayerState state = entry.getValue();
                    long activeSeconds = state.activeSeconds();
                    String claimedStr = formatClaimedHours(state.claimedHours);
                    CompletableFuture
                        .runAsync(() -> repo.upsertPlaytime(uuid, activeSeconds, claimedStr), dbExecutor)
                        .exceptionally(ex -> {
                            JBalance.LOGGER.error("[JBalance] Periodic playtime flush failed for {}: {}",
                                uuid, ex.getMessage());
                            return null;
                        });
                }
            }
        }
    }

    /**
     * Flushes all in-memory playtime to DB synchronously. Called on server shutdown.
     */
    public void flushAll() {
        if (playerStates.isEmpty()) return;
        JBalance.LOGGER.info("[JBalance] Flushing playtime for {} players on shutdown", playerStates.size());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<UUID, PlayerState> entry : playerStates.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerState state = entry.getValue();
            long activeSeconds = state.activeSeconds();
            String claimedStr = formatClaimedHours(state.claimedHours);
            futures.add(CompletableFuture.runAsync(
                () -> repo.upsertPlaytime(uuid, activeSeconds, claimedStr), dbExecutor));
        }
        // Wait for all flushes to complete before server goes down
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        playerStates.clear();
    }

    /**
     * Converts a set of claimed milestone hours to a sorted comma-delimited string.
     */
    private static String formatClaimedHours(Set<Long> hours) {
        return hours.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }
}
