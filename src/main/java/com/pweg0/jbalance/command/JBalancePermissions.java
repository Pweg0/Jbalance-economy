package com.pweg0.jbalance.command;

import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Central registry for all JBalance permission nodes.
 * LuckPerms (and other permission mods) automatically detect these nodes.
 *
 * Nodes:
 *   jbalance.eco.balance       — /eco balance, /bal (default: all players)
 *   jbalance.eco.balance.other — /eco balance <player> (default: all players)
 *   jbalance.eco.pay           — /eco pay (default: all players)
 *   jbalance.eco.top           — /eco top (default: all players)
 *   jbalance.admin.give        — /ecoadmin give (default: OP 4)
 *   jbalance.admin.take        — /ecoadmin take (default: OP 4)
 *   jbalance.admin.set         — /ecoadmin set (default: OP 4)
 */
public final class JBalancePermissions {

    private JBalancePermissions() {}

    // Player commands — default: everyone can use
    public static final PermissionNode<Boolean> ECO_BALANCE = new PermissionNode<>(
            "jbalance", "eco.balance", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ECO_BALANCE_OTHER = new PermissionNode<>(
            "jbalance", "eco.balance.other", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ECO_PAY = new PermissionNode<>(
            "jbalance", "eco.pay", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ECO_TOP = new PermissionNode<>(
            "jbalance", "eco.top", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    // Shop commands — default: everyone can use
    // Default: false — only players with explicit permission can create shops
    public static final PermissionNode<Boolean> SHOP_CREATE = new PermissionNode<>(
            "jbalance", "shop.create", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> false
    );

    public static final PermissionNode<Boolean> SHOP_TELEPORT = new PermissionNode<>(
            "jbalance", "shop.teleport", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> SHOP_SELL = new PermissionNode<>(
            "jbalance", "shop.sell", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> SHOP_BUY = new PermissionNode<>(
            "jbalance", "shop.buy", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    // Shop item limit — default: 6 items per shop
    // LuckPerms can override per-group (e.g. VIP = 30)
    public static final PermissionNode<Integer> SHOP_ITEM_LIMIT = new PermissionNode<>(
            "jbalance", "shop.limit", PermissionTypes.INTEGER,
            (player, uuid, ctx) -> 6
    );

    // Admin commands — default: OP level 4 only
    public static final PermissionNode<Boolean> ADMIN_GIVE = new PermissionNode<>(
            "jbalance", "admin.give", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(4)
    );

    public static final PermissionNode<Boolean> ADMIN_TAKE = new PermissionNode<>(
            "jbalance", "admin.take", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(4)
    );

    public static final PermissionNode<Boolean> ADMIN_SET = new PermissionNode<>(
            "jbalance", "admin.set", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(4)
    );

    public static final PermissionNode<Boolean> ADMIN_SHOP = new PermissionNode<>(
            "jbalance", "admin.shop", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(4)
    );

    /**
     * Register all permission nodes. Called from JBalance via PermissionGatherEvent.Nodes.
     */
    public static void onGatherPermissions(PermissionGatherEvent.Nodes event) {
        event.addNodes(
                ECO_BALANCE, ECO_BALANCE_OTHER, ECO_PAY, ECO_TOP,
                SHOP_CREATE, SHOP_TELEPORT, SHOP_SELL, SHOP_BUY, SHOP_ITEM_LIMIT,
                ADMIN_GIVE, ADMIN_TAKE, ADMIN_SET, ADMIN_SHOP
        );
    }
}
