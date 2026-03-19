package com.pweg0.jbalance.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class JBalanceConfig {
    public static final ModConfigSpec SPEC;

    // Currency section
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_NAME;
    public static final ModConfigSpec.ConfigValue<String> CURRENCY_SYMBOL;
    public static final ModConfigSpec.LongValue STARTING_BALANCE;
    public static final ModConfigSpec.LongValue MIN_TRANSFER;

    // Currency advanced
    public static final ModConfigSpec.LongValue TOP_CACHE_SECONDS;
    public static final ModConfigSpec.LongValue TRANSFER_COOLDOWN_SECONDS;

    // Database section
    public static final ModConfigSpec.BooleanValue USE_MYSQL;
    public static final ModConfigSpec.ConfigValue<String> DB_HOST;
    public static final ModConfigSpec.IntValue DB_PORT;
    public static final ModConfigSpec.ConfigValue<String> DB_NAME;
    public static final ModConfigSpec.ConfigValue<String> DB_USER;
    public static final ModConfigSpec.ConfigValue<String> DB_PASSWORD;

    // Discord Webhook section
    public static final ModConfigSpec.BooleanValue WEBHOOK_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.BooleanValue WEBHOOK_LOG_PAY;
    public static final ModConfigSpec.BooleanValue WEBHOOK_LOG_ADMIN;
    public static final ModConfigSpec.BooleanValue WEBHOOK_LOG_EARNINGS;
    public static final ModConfigSpec.BooleanValue WEBHOOK_LOG_BALANCE;
    public static final ModConfigSpec.BooleanValue WEBHOOK_LOG_SHOP;

    // Shop section
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SHOP_DISPLAY_BLACKLIST;
    public static final ModConfigSpec.LongValue SHOP_TAX_PERCENT;

    // Earnings - Mob Kills section
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MOB_KILL_REWARDS;
    public static final ModConfigSpec.LongValue KILL_NOTIFICATION_INTERVAL;

    // Earnings - Milestones section
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> MILESTONES;
    public static final ModConfigSpec.LongValue AFK_TIMEOUT_MINUTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("JBalance Currency Settings").push("currency");
        CURRENCY_NAME = builder.comment("Name of the currency").define("name", "JCoins");
        CURRENCY_SYMBOL = builder.comment("Symbol shown before the value (e.g. J$)").define("symbol", "J$");
        STARTING_BALANCE = builder.comment("Balance granted to new players on first join")
                                  .defineInRange("starting_balance", 100L, 0L, Long.MAX_VALUE);
        MIN_TRANSFER = builder.comment("Minimum amount for /eco pay transfers")
                              .defineInRange("min_transfer", 1L, 1L, Long.MAX_VALUE);
        TOP_CACHE_SECONDS = builder.comment("Seconds to cache /eco top results")
                                   .defineInRange("top_cache_seconds", 60L, 1L, 3600L);
        TRANSFER_COOLDOWN_SECONDS = builder.comment("Minimum seconds between /eco pay transfers for the same player")
                                           .defineInRange("transfer_cooldown_seconds", 3L, 0L, 300L);
        builder.pop();

        builder.comment("JBalance Database Settings").push("database");
        USE_MYSQL = builder.comment("true = MySQL (production), false = SQLite (dev/fallback)")
                           .define("use_mysql", false);
        DB_HOST = builder.comment("MySQL server hostname").define("host", "localhost");
        DB_PORT = builder.comment("MySQL server port")
                         .defineInRange("port", 3306, 1, 65535);
        DB_NAME = builder.comment("MySQL database name").define("database", "jbalance");
        DB_USER = builder.comment("MySQL username").define("user", "jbalance");
        DB_PASSWORD = builder.comment("MySQL password").define("password", "changeme");
        builder.pop();

        builder.comment("Discord Webhook for economy audit logs").push("webhook");
        WEBHOOK_ENABLED = builder.comment("Enable Discord webhook logging")
                                  .define("enabled", false);
        WEBHOOK_URL = builder.comment("Discord webhook URL (create in Discord channel settings > Integrations > Webhooks)")
                              .define("url", "");
        WEBHOOK_LOG_PAY = builder.comment("Log /eco pay transfers")
                                  .define("log_pay", true);
        WEBHOOK_LOG_ADMIN = builder.comment("Log /ecoadmin give/take/set commands")
                                    .define("log_admin", true);
        WEBHOOK_LOG_EARNINGS = builder.comment("Log mob kill and milestone earnings")
                                       .define("log_earnings", true);
        WEBHOOK_LOG_BALANCE = builder.comment("Log first join (new player balance)")
                                      .define("log_balance", true);
        WEBHOOK_LOG_SHOP = builder.comment("Log shop events (create, delete, buy, sell, expose item, out of stock)")
                                   .define("log_shop", true);
        builder.pop();

        builder.comment("JBalance Shop Settings").push("shop");
        SHOP_DISPLAY_BLACKLIST = builder
            .comment("Blocks that CANNOT be used as shop display stands. Format: \"minecraft:block_id\"")
            .defineListAllowEmpty("display_blacklist",
                java.util.List.of("minecraft:bedrock", "minecraft:command_block",
                    "minecraft:chain_command_block", "minecraft:repeating_command_block",
                    "minecraft:barrier", "minecraft:structure_block", "minecraft:jigsaw"),
                e -> e instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"));
        SHOP_TAX_PERCENT = builder.comment("Tax percentage on shop sales (e.g. 3 = 3%)")
                                   .defineInRange("tax_percent", 3L, 0L, 100L);
        builder.pop();

        builder.comment("JBalance Earnings Settings").push("earnings");

        builder.comment("Mob kill reward settings").push("mob_kills");
        MOB_KILL_REWARDS = builder
            .comment("Mob kill rewards. Format: \"minecraft:mob_id=reward_amount\". Only listed mobs give coins.")
            .defineListAllowEmpty("rewards",
                java.util.List.of(
                    "minecraft:zombie=10", "minecraft:skeleton=10",
                    "minecraft:creeper=15", "minecraft:spider=10",
                    "minecraft:enderman=25", "minecraft:witch=20",
                    "minecraft:blaze=30", "minecraft:wither_skeleton=40"),
                e -> e instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+=\\d+"));
        KILL_NOTIFICATION_INTERVAL = builder
            .comment("Seconds between batched kill reward notifications (0 = immediate per kill)")
            .defineInRange("kill_notification_interval", 60L, 0L, 3600L);
        builder.pop();

        builder.comment("Playtime milestone settings").push("milestones");
        MILESTONES = builder
            .comment("Playtime milestones. Format: \"hours=N,reward=X\". Each milestone granted once per player.")
            .defineListAllowEmpty("milestones",
                java.util.List.of(
                    "hours=1,reward=100", "hours=2,reward=200",
                    "hours=5,reward=500", "hours=10,reward=1000", "hours=24,reward=2500"),
                e -> e instanceof String s && s.matches("hours=\\d+,reward=\\d+"));
        AFK_TIMEOUT_MINUTES = builder
            .comment("Minutes of inactivity before a player is marked AFK (AFK time does not count toward milestones)")
            .defineInRange("afk_timeout_minutes", 5L, 1L, 60L);
        builder.pop();

        builder.pop(); // earnings

        SPEC = builder.build();
    }

    /**
     * Parses the MOB_KILL_REWARDS config list into a Map of entity type key -> reward amount.
     * MUST be called on the game thread (config values are not thread-safe).
     */
    public static java.util.Map<String, Long> parsedMobRewards() {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        for (Object entry : MOB_KILL_REWARDS.get()) {
            String[] parts = entry.toString().split("=", 2);
            if (parts.length == 2) {
                try {
                    map.put(parts[0].trim(), Long.parseLong(parts[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public record MilestoneEntry(long hours, long reward) {}

    /**
     * Parses the MILESTONES config list into a sorted list of MilestoneEntry.
     * MUST be called on the game thread (config values are not thread-safe).
     */
    public static java.util.List<MilestoneEntry> parsedMilestones() {
        java.util.List<MilestoneEntry> list = new java.util.ArrayList<>();
        for (Object entry : MILESTONES.get()) {
            String s = entry.toString();
            try {
                String hoursStr = s.substring(s.indexOf("hours=") + 6, s.indexOf(","));
                String rewardStr = s.substring(s.indexOf("reward=") + 7);
                list.add(new MilestoneEntry(Long.parseLong(hoursStr), Long.parseLong(rewardStr)));
            } catch (Exception ignored) {}
        }
        list.sort(java.util.Comparator.comparingLong(MilestoneEntry::hours));
        return list;
    }

    private JBalanceConfig() {} // prevent instantiation
}
