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

        SPEC = builder.build();
    }

    private JBalanceConfig() {} // prevent instantiation
}
