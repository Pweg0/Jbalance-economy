package com.pweg0.jbalance.data.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * SQL CRUD operations for player balances.
 * All balance mutations use atomic SQL to prevent race conditions.
 */
public class BalanceRepository {

    private final DataSource dataSource;
    private final boolean isMysql;

    public BalanceRepository(DataSource dataSource, boolean isMysql) {
        this.dataSource = dataSource;
        this.isMysql = isMysql;
    }

    /**
     * Returns the current balance for the given player UUID.
     * Returns -1L if the player is not found in the database.
     */
    public long getBalance(UUID uuid) {
        String sql = "SELECT balance FROM jbalance_players WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("balance") : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getBalance failed for " + uuid, e);
        }
    }

    /**
     * Inserts a new player row with the given starting balance if no row exists.
     * Uses dialect-aware INSERT IGNORE (MySQL) or INSERT OR IGNORE (SQLite).
     * Returns true if a new row was inserted (player is new), false if already existed.
     */
    public boolean initPlayerIfAbsent(UUID uuid, String displayName, long startingBalance) {
        // MySQL: INSERT IGNORE INTO; SQLite: INSERT OR IGNORE INTO
        String sql = isMysql
            ? "INSERT IGNORE INTO jbalance_players (uuid, display_name, balance) VALUES (?, ?, ?)"
            : "INSERT OR IGNORE INTO jbalance_players (uuid, display_name, balance) VALUES (?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, displayName);
            ps.setLong(3, startingBalance);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("initPlayerIfAbsent failed for " + uuid, e);
        }
    }

    /**
     * Atomically adjusts a player's balance by the given delta.
     * For positive delta: unconditional credit (UPDATE SET balance = balance + ?).
     * For negative delta: conditional deduct (UPDATE SET balance = balance + ? WHERE balance >= |delta|).
     * Returns true if exactly one row was updated (success), false if insufficient funds.
     */
    public boolean adjustBalance(UUID uuid, long delta) {
        String sql = delta >= 0
            ? "UPDATE jbalance_players SET balance = balance + ? WHERE uuid = ?"
            : "UPDATE jbalance_players SET balance = balance + ? WHERE uuid = ? AND balance >= ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, delta);
            ps.setString(2, uuid.toString());
            if (delta < 0) {
                ps.setLong(3, -delta); // ensure balance >= |delta|
            }
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("adjustBalance failed for " + uuid, e);
        }
    }

    /**
     * Atomically transfers an amount from one player to another using a single DB transaction.
     * Deducts from sender (fails if insufficient funds), then credits receiver.
     * Returns true if the transfer succeeded, false if sender has insufficient funds.
     */
    public boolean transfer(UUID from, UUID to, long amount) {
        String deduct = "UPDATE jbalance_players SET balance = balance - ? WHERE uuid = ? AND balance >= ?";
        String credit = "UPDATE jbalance_players SET balance = balance + ? WHERE uuid = ?";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement deductPs = c.prepareStatement(deduct);
                 PreparedStatement creditPs = c.prepareStatement(credit)) {
                deductPs.setLong(1, amount);
                deductPs.setString(2, from.toString());
                deductPs.setLong(3, amount);
                if (deductPs.executeUpdate() != 1) {
                    c.rollback();
                    return false;
                }
                creditPs.setLong(1, amount);
                creditPs.setString(2, to.toString());
                creditPs.executeUpdate();
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("transfer failed from " + from + " to " + to, e);
        }
    }
}
