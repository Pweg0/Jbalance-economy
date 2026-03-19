package com.pweg0.jbalance.util;

import com.pweg0.jbalance.config.JBalanceConfig;
import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyFormatter {

    private static final Locale PT_BR = new Locale("pt", "BR");

    private CurrencyFormatter() {} // prevent instantiation

    /**
     * Formats a balance amount using PT-BR standard.
     * Example: 1500 -> "J$ 1.500"
     * Uses dot as thousands separator, no decimal places.
     * Symbol is read from TOML config at call time (supports hot-reload).
     */
    public static String formatBalance(long amount) {
        String symbol = JBalanceConfig.CURRENCY_SYMBOL.get();
        NumberFormat fmt = NumberFormat.getIntegerInstance(PT_BR);
        return symbol + " " + fmt.format(amount);
    }

    /**
     * Returns the configured currency name (e.g., "JCoins").
     * Read from TOML config at call time (supports hot-reload).
     */
    public static String getCurrencyName() {
        return JBalanceConfig.CURRENCY_NAME.get();
    }
}
