package com.pweg0.jbalance.event;

import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.util.CurrencyFormatter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

/**
 * Handles player lifecycle events for the economy system.
 * Detects first join and grants the configured starting balance with a PT-BR welcome message.
 */
public class PlayerEventHandler {

    private PlayerEventHandler() {} // prevent instantiation — all methods are static

    /**
     * Called when a player logs into the server.
     * Initializes a balance row for new players and sends them a welcome message.
     * All DB operations run asynchronously; chat messages are sent back on the game thread.
     */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        String name = player.getName().getString();

        EconomyService.getInstance().initPlayerIfAbsent(uuid, name)
            .whenComplete((isNew, ex) -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] Failed to initialize player balance for {}", uuid, ex);
                    return;
                }
                if (Boolean.TRUE.equals(isNew)) {
                    // Read config on game thread re-entry for the welcome message
                    long startBal = JBalanceConfig.STARTING_BALANCE.get();
                    String formattedBalance = CurrencyFormatter.formatBalance(startBal);
                    // Re-enter game thread before sending chat message
                    var server = player.getServer();
                    server.execute(() ->
                        player.sendSystemMessage(Component.literal(
                            "§6[JBalance] §7Bem-vindo! Voce recebeu §6" + formattedBalance + " §7de saldo inicial."
                        ))
                    );
                }
            });
    }
}
