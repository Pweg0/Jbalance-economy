package com.pweg0.jbalance.data.db;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * SQL CRUD for jbalance_shops and jbalance_shop_items tables.
 */
public class ShopRepository {

    private final DataSource dataSource;
    private final boolean isMysql;

    public ShopRepository(DataSource dataSource, boolean isMysql) {
        this.dataSource = dataSource;
        this.isMysql = isMysql;
    }

    // ── Shop records ──

    public record ShopData(UUID uuid, double x, double y, double z,
                           float yaw, float pitch, String dimension) {}

    public record ShopItemData(int id, UUID shopUuid,
                               int displayX, int displayY, int displayZ,
                               int storageX, int storageY, int storageZ,
                               String itemId, String itemNbt,
                               int sellQty, long sellPrice,
                               int buyQty, long buyPrice, boolean active) {}

    // ── Shop CRUD ──

    public void createShop(UUID uuid, double x, double y, double z,
                           float yaw, float pitch, String dimension) {
        String sql = isMysql
            ? "INSERT INTO jbalance_shops (uuid, x, y, z, yaw, pitch, dimension) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), dimension=VALUES(dimension)"
            : "INSERT OR REPLACE INTO jbalance_shops (uuid, x, y, z, yaw, pitch, dimension) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setFloat(5, yaw);
            ps.setFloat(6, pitch);
            ps.setString(7, dimension);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createShop failed for " + uuid, e);
        }
    }

    public void deleteShop(UUID uuid) {
        // Delete items first (SQLite doesn't always enforce FK cascade)
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM jbalance_shop_items WHERE shop_uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM jbalance_shops WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("deleteShop failed for " + uuid, e);
        }
    }

    public ShopData getShop(UUID uuid) {
        String sql = "SELECT x, y, z, yaw, pitch, dimension, created_at FROM jbalance_shops WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ShopData(uuid, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getString("dimension"));
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getShop failed for " + uuid, e);
        }
    }

    /**
     * Returns the created_at timestamp string for a shop, or null if no shop exists.
     */
    public String getShopCreatedAt(UUID uuid) {
        String sql = "SELECT created_at FROM jbalance_shops WHERE uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("created_at") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getShopCreatedAt failed for " + uuid, e);
        }
    }

    /**
     * Returns all shops with the owner's display_name from jbalance_players.
     */
    public List<ShopListEntry> listShops() {
        String sql = "SELECT s.uuid, p.display_name, s.x, s.y, s.z, s.yaw, s.pitch, s.dimension " +
                     "FROM jbalance_shops s JOIN jbalance_players p ON s.uuid = p.uuid ORDER BY p.display_name";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<ShopListEntry> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new ShopListEntry(
                    UUID.fromString(rs.getString("uuid")),
                    rs.getString("display_name"),
                    rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                    rs.getFloat("yaw"), rs.getFloat("pitch"), rs.getString("dimension")
                ));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("listShops failed", e);
        }
    }

    public record ShopListEntry(UUID uuid, String displayName, double x, double y, double z,
                                float yaw, float pitch, String dimension) {}

    /** Find shop owner UUID by display name (case-insensitive). */
    public UUID findShopByOwnerName(String name) {
        String sql = "SELECT s.uuid FROM jbalance_shops s JOIN jbalance_players p ON s.uuid = p.uuid WHERE LOWER(p.display_name) = LOWER(?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString("uuid")) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findShopByOwnerName failed for " + name, e);
        }
    }

    // ── Shop Items CRUD ──

    public int createShopItem(UUID shopUuid, int dx, int dy, int dz,
                              int sx, int sy, int sz,
                              String itemId, String itemNbt,
                              int sellQty, long sellPrice,
                              int buyQty, long buyPrice) {
        String sql = "INSERT INTO jbalance_shop_items (shop_uuid, display_x, display_y, display_z, " +
                     "storage_x, storage_y, storage_z, item_id, item_nbt, sell_qty, sell_price, buy_qty, buy_price) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, shopUuid.toString());
            ps.setInt(2, dx); ps.setInt(3, dy); ps.setInt(4, dz);
            ps.setInt(5, sx); ps.setInt(6, sy); ps.setInt(7, sz);
            ps.setString(8, itemId);
            ps.setString(9, itemNbt);
            ps.setInt(10, sellQty); ps.setLong(11, sellPrice);
            ps.setInt(12, buyQty); ps.setLong(13, buyPrice);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("createShopItem failed", e);
        }
    }

    public void deleteShopItem(int itemId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM jbalance_shop_items WHERE id = ?")) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteShopItem failed for id " + itemId, e);
        }
    }

    public void setShopItemActive(int itemId, boolean active) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE jbalance_shop_items SET active = ? WHERE id = ?")) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("setShopItemActive failed for id " + itemId, e);
        }
    }

    public List<ShopItemData> getShopItems(UUID shopUuid) {
        String sql = "SELECT * FROM jbalance_shop_items WHERE shop_uuid = ? AND active = 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, shopUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<ShopItemData> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapItem(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getShopItems failed for " + shopUuid, e);
        }
    }

    public ShopItemData getShopItemByDisplayPos(int dx, int dy, int dz, String dimension) {
        String sql = "SELECT si.* FROM jbalance_shop_items si " +
                     "JOIN jbalance_shops s ON si.shop_uuid = s.uuid " +
                     "WHERE si.display_x = ? AND si.display_y = ? AND si.display_z = ? AND s.dimension = ? AND si.active = 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, dx); ps.setInt(2, dy); ps.setInt(3, dz);
            ps.setString(4, dimension);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapItem(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getShopItemByDisplayPos failed", e);
        }
    }

    public List<ShopItemData> getShopItemsByStoragePos(int sx, int sy, int sz, String dimension) {
        String sql = "SELECT si.* FROM jbalance_shop_items si " +
                     "JOIN jbalance_shops s ON si.shop_uuid = s.uuid " +
                     "WHERE si.storage_x = ? AND si.storage_y = ? AND si.storage_z = ? AND s.dimension = ? AND si.active = 1";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sx); ps.setInt(2, sy); ps.setInt(3, sz);
            ps.setString(4, dimension);
            try (ResultSet rs = ps.executeQuery()) {
                List<ShopItemData> list = new ArrayList<>();
                while (rs.next()) list.add(mapItem(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("getShopItemsByStoragePos failed", e);
        }
    }

    public int countShopItems(UUID shopUuid) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM jbalance_shop_items WHERE shop_uuid = ? AND active = 1")) {
            ps.setString(1, shopUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("countShopItems failed", e);
        }
    }

    private ShopItemData mapItem(ResultSet rs) throws SQLException {
        return new ShopItemData(
            rs.getInt("id"), UUID.fromString(rs.getString("shop_uuid")),
            rs.getInt("display_x"), rs.getInt("display_y"), rs.getInt("display_z"),
            rs.getInt("storage_x"), rs.getInt("storage_y"), rs.getInt("storage_z"),
            rs.getString("item_id"), rs.getString("item_nbt"),
            rs.getInt("sell_qty"), rs.getLong("sell_price"),
            rs.getInt("buy_qty"), rs.getLong("buy_price"),
            rs.getInt("active") == 1
        );
    }
}
