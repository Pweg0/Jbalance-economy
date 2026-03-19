package com.pweg0.jbalance.service;

import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.data.db.BalanceRepository;
import com.pweg0.jbalance.data.db.DatabaseManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async balance operations with per-player in-flight locks.
 * All DB calls run on DB_EXECUTOR (dedicated thread pool), never blocking the server tick.
 * Config values are read on the game thread before going async to avoid off-thread config access.
 */
public class EconomyService {

    /** Dedicated thread pool for database operations. Daemon threads do not prevent JVM shutdown. */
    private static final ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "JBalance-DB");
        t.setDaemon(true);
        return t;
    });

    /** Per-player transfer lock prevents concurrent overlapping mutations for the same player. */
    private final ConcurrentHashMap<UUID, AtomicBoolean> inFlight = new ConcurrentHashMap<>();

    private final BalanceRepository repo;

    /** Singleton instance, set during server startup. */
    private static EconomyService instance;

    public EconomyService(DatabaseManager dbManager) {
        this.repo = new BalanceRepository(dbManager.getDataSource(), dbManager.isMysql());
        instance = this;
    }

    /**
     * Returns the singleton instance. Available after server starts.
     */
    public static EconomyService getInstance() {
        return instance;
    }

    /**
     * Asynchronously retrieves the balance for the given player.
     * Returns -1L if the player has no database record.
     */
    public CompletableFuture<Long> getBalance(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> repo.getBalance(playerId), DB_EXECUTOR);
    }

    /**
     * Asynchronously credits the given amount to a player's balance.
     * Returns true on success.
     */
    public CompletableFuture<Boolean> give(UUID playerId, long amount) {
        return CompletableFuture.supplyAsync(() -> repo.adjustBalance(playerId, amount), DB_EXECUTOR);
    }

    /**
     * Asynchronously deducts the given amount from a player's balance.
     * Returns false if the player has insufficient funds.
     */
    public CompletableFuture<Boolean> take(UUID playerId, long amount) {
        return CompletableFuture.supplyAsync(() -> repo.adjustBalance(playerId, -amount), DB_EXECUTOR);
    }

    /**
     * Asynchronously transfers an amount from one player to another.
     * Uses a per-player in-flight lock to prevent concurrent overlapping transfers from the same sender.
     * Returns false immediately if a transfer for the sender is already in progress.
     */
    public CompletableFuture<Boolean> transfer(UUID from, UUID to, long amount) {
        AtomicBoolean lock = inFlight.computeIfAbsent(from, k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) {
            // Already in flight for this player
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return repo.transfer(from, to, amount);
            } finally {
                lock.set(false);
            }
        }, DB_EXECUTOR);
    }

    /**
     * Initializes a player's balance row if they have never joined before.
     * IMPORTANT: startingBalance is read from config on the CALLING (game) thread,
     * then passed into the async operation to avoid reading ConfigValue from DB_EXECUTOR.
     * Returns true if a new row was inserted (player is new).
     */
    public CompletableFuture<Boolean> initPlayerIfAbsent(UUID uuid, String displayName) {
        // Read config on game thread before going async
        long startingBalance = JBalanceConfig.STARTING_BALANCE.get();
        return CompletableFuture.supplyAsync(
            () -> repo.initPlayerIfAbsent(uuid, displayName, startingBalance),
            DB_EXECUTOR
        );
    }

    /**
     * Shuts down the DB executor. Called on server stopping event.
     */
    public void shutdown() {
        DB_EXECUTOR.shutdownNow();
    }
}
