package com.pweg0.jbalance.util;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.config.JBalanceConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends audit log messages to a Discord webhook.
 * All sends are async on a dedicated thread — never blocks the game thread.
 */
public final class DiscordWebhook {

    private DiscordWebhook() {}

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JBalance-Webhook");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ── Color constants (Discord embed sidebar) ──
    public static final int COLOR_PAY      = 0x3498DB; // azul — transferencias
    public static final int COLOR_ADMIN    = 0xE74C3C; // vermelho — admin commands
    public static final int COLOR_EARN     = 0x2ECC71; // verde — earnings
    public static final int COLOR_JOIN     = 0xF39C12; // laranja — first join
    public static final int COLOR_MILESTONE = 0x9B59B6; // roxo — milestones

    /**
     * Send a simple embed to the webhook. Non-blocking.
     *
     * @param title  Embed title (e.g. "Transferencia")
     * @param description Embed body with details
     * @param color  Sidebar color (use constants above)
     */
    public static void send(String title, String description, int color) {
        if (!JBalanceConfig.WEBHOOK_ENABLED.get()) return;
        String url = JBalanceConfig.WEBHOOK_URL.get();
        if (url == null || url.isBlank()) return;

        String json = buildPayload(title, description, color);

        EXECUTOR.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(10))
                        .build();
                HttpResponse<String> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 429) {
                    // Rate limited — wait and retry once
                    Thread.sleep(2000);
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                } else if (resp.statusCode() >= 400) {
                    JBalance.LOGGER.warn("[JBalance] Webhook returned HTTP {}", resp.statusCode());
                }
            } catch (Exception e) {
                JBalance.LOGGER.warn("[JBalance] Failed to send webhook: {}", e.getMessage());
            }
        });
    }

    // ── Convenience methods for each log category ──

    /** Log a /eco pay transfer */
    public static void logPay(String sender, String receiver, String amount, String senderBalance) {
        if (!JBalanceConfig.WEBHOOK_LOG_PAY.get()) return;
        send("Transferencia",
             "**De:** " + sender + "\n**Para:** " + receiver + "\n**Valor:** " + amount + "\n**Saldo restante:** " + senderBalance,
             COLOR_PAY);
    }

    /** Log an admin give command */
    public static void logAdminGive(String admin, String target, String amount, boolean targetOnline) {
        if (!JBalanceConfig.WEBHOOK_LOG_ADMIN.get()) return;
        String status = targetOnline ? "online" : "offline";
        send("Admin Give",
             "**Admin:** " + admin + "\n**Jogador:** " + target + " (" + status + ")\n**Valor:** +" + amount,
             COLOR_ADMIN);
    }

    /** Log an admin take command */
    public static void logAdminTake(String admin, String target, String amount, boolean targetOnline) {
        if (!JBalanceConfig.WEBHOOK_LOG_ADMIN.get()) return;
        String status = targetOnline ? "online" : "offline";
        send("Admin Take",
             "**Admin:** " + admin + "\n**Jogador:** " + target + " (" + status + ")\n**Valor:** -" + amount,
             COLOR_ADMIN);
    }

    /** Log an admin set command */
    public static void logAdminSet(String admin, String target, String amount, boolean targetOnline) {
        if (!JBalanceConfig.WEBHOOK_LOG_ADMIN.get()) return;
        String status = targetOnline ? "online" : "offline";
        send("Admin Set",
             "**Admin:** " + admin + "\n**Jogador:** " + target + " (" + status + ")\n**Saldo definido:** " + amount,
             COLOR_ADMIN);
    }

    /** Log mob kill earnings (batched) */
    public static void logMobEarnings(String player, String amount, int mobCount) {
        if (!JBalanceConfig.WEBHOOK_LOG_EARNINGS.get()) return;
        send("Mob Kill Earnings",
             "**Jogador:** " + player + "\n**Mobs:** " + mobCount + "\n**Ganho:** +" + amount,
             COLOR_EARN);
    }

    /** Log milestone reached */
    public static void logMilestone(String player, long hours, String reward) {
        if (!JBalanceConfig.WEBHOOK_LOG_EARNINGS.get()) return;
        send("Milestone Atingido",
             "**Jogador:** " + player + "\n**Milestone:** " + hours + "h de jogo\n**Recompensa:** +" + reward,
             COLOR_MILESTONE);
    }

    /** Log first join */
    public static void logFirstJoin(String player, String startingBalance) {
        if (!JBalanceConfig.WEBHOOK_LOG_BALANCE.get()) return;
        send("Novo Jogador",
             "**Jogador:** " + player + "\n**Saldo inicial:** " + startingBalance,
             COLOR_JOIN);
    }

    // ── JSON builder (no external deps) ──

    private static String buildPayload(String title, String description, int color) {
        return "{\"embeds\":[{"
                + "\"title\":" + jsonString(title) + ","
                + "\"description\":" + jsonString(description) + ","
                + "\"color\":" + color + ","
                + "\"footer\":{\"text\":\"JBalance Economy Log\"},"
                + "\"timestamp\":\"" + java.time.Instant.now().toString() + "\""
                + "}]}";
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                + "\"";
    }
}
