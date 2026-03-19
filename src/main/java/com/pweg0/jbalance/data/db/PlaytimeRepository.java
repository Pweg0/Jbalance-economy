package com.pweg0.jbalance.data.db;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * SQL CRUD operations for player playtime tracking.
 * Provides load and upsert for the jbalance_playtime table.
 */
public class PlaytimeRepository {

    public record PlaytimeData(long activeSeconds, Set<Long> claimedHours) {}

    private final DataSource dataSource;
    private final boolean isMysql;

    public PlaytimeRepository(DataSource dataSource, boolean isMysql) {
        this.dataSource = dataSource;
        this.isMysql = isMysql;
    }

    /**
     * Loads playtime data for a player. Returns null if no record exists.
     */
    public PlaytimeData loadPlaytime(UUID uuid) {
        String sql = "SELECT active_seconds, claimed_hours FROM jbalance_playtime WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long seconds = rs.getLong("active_seconds");
                    Set<Long> claimed = parseClaimedHours(rs.getString("claimed_hours"));
                    return new PlaytimeData(seconds, claimed);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("loadPlaytime failed for " + uuid, e);
        }
    }

    /**
     * Upserts playtime data. MySQL uses INSERT ... ON DUPLICATE KEY UPDATE; SQLite uses INSERT OR REPLACE.
     */
    public void upsertPlaytime(UUID uuid, long activeSeconds, String claimedHoursStr) {
        String sql = isMysql
            ? "INSERT INTO jbalance_playtime (uuid, active_seconds, claimed_hours) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE active_seconds = VALUES(active_seconds), claimed_hours = VALUES(claimed_hours)"
            : "INSERT OR REPLACE INTO jbalance_playtime (uuid, active_seconds, claimed_hours) VALUES (?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, activeSeconds);
            ps.setString(3, claimedHoursStr);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("upsertPlaytime failed for " + uuid, e);
        }
    }

    private static Set<Long> parseClaimedHours(String str) {
        Set<Long> set = new HashSet<>();
        if (str == null || str.isEmpty()) return set;
        for (String part : str.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try { set.add(Long.parseLong(trimmed)); } catch (NumberFormatException ignored) {}
            }
        }
        return set;
    }
}
