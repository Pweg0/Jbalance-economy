package com.pweg0.jbalance.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.data.db.BalanceRepository;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.util.CurrencyFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player commands: /eco balance, /eco pay, /eco top.
 * All DB operations are dispatched asynchronously via EconomyService,
 * then results are sent on the game thread via server.execute().
 */
public class EcoCommand {

    private EcoCommand() {}

    /** Tracks the last successful /eco pay timestamp per player for cooldown enforcement. */
    private static final ConcurrentHashMap<UUID, Instant> lastTransfer = new ConcurrentHashMap<>();

    /** Volatile cache for /eco top results. */
    private static volatile List<BalanceRepository.TopEntry> cachedTop = Collections.emptyList();

    /** Expiry timestamp for the /eco top cache. Initialised to EPOCH so first call always refreshes. */
    private static volatile Instant cacheExpiry = Instant.EPOCH;

    /**
     * Registers the /eco command tree with the given dispatcher.
     * Called by CommandRegistrar during RegisterCommandsEvent.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("eco")
                .then(Commands.literal("balance")
                    .executes(EcoCommand::balance)
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(EcoCommand::balanceOther)))
                .then(Commands.literal("pay")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(EcoCommand::pay))))
                .then(Commands.literal("top")
                    .executes(EcoCommand::top))
        );
    }

    // -------------------------------------------------------------------------
    // /eco balance — own balance (CURR-01)
    // -------------------------------------------------------------------------

    private static int balance(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        UUID uuid = player.getUUID();

        EconomyService.getInstance().getBalance(uuid)
            .whenComplete((bal, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] Failed to get balance for {}", uuid, ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao consultar saldo."));
                    return;
                }
                String formatted = CurrencyFormatter.formatBalance(bal);
                src.sendSuccess(() -> Component.literal("\u00a76[JBalance] \u00a77Seu saldo: \u00a76" + formatted), false);
            }));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /eco balance <player> — another online player's balance (CURR-02)
    // -------------------------------------------------------------------------

    private static int balanceOther(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        UUID targetUuid = target.getUUID();
        String targetName = target.getName().getString();

        EconomyService.getInstance().getBalance(targetUuid)
            .whenComplete((bal, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] Failed to get balance for {}", targetUuid, ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao consultar saldo."));
                    return;
                }
                String formatted = CurrencyFormatter.formatBalance(bal);
                src.sendSuccess(() -> Component.literal(
                    "\u00a76[JBalance] \u00a77Saldo de " + targetName + ": \u00a76" + formatted
                ), false);
            }));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /eco pay <player> <amount> — transfer coins (CURR-03)
    // -------------------------------------------------------------------------

    private static int pay(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer sender = src.getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");

        UUID senderId = sender.getUUID();
        UUID targetId = target.getUUID();
        String senderName = sender.getName().getString();
        String targetName = target.getName().getString();

        // Guard 1: self-pay
        if (senderId.equals(targetId)) {
            src.sendFailure(Component.literal(
                "\u00a76[JBalance] \u00a7cVoce nao pode enviar dinheiro para si mesmo."
            ));
            return Command.SINGLE_SUCCESS;
        }

        // Guard 2: minimum transfer (read config on game thread before async)
        long minTransfer = JBalanceConfig.MIN_TRANSFER.get();
        if (amount < minTransfer) {
            String minFormatted = CurrencyFormatter.formatBalance(minTransfer);
            src.sendFailure(Component.literal(
                "\u00a76[JBalance] \u00a7cValor minimo para transferencia: \u00a76" + minFormatted
            ));
            return Command.SINGLE_SUCCESS;
        }

        // Guard 3: cooldown (read config on game thread before async)
        long cooldownSeconds = JBalanceConfig.TRANSFER_COOLDOWN_SECONDS.get();
        Instant last = lastTransfer.get(senderId);
        if (last != null && Duration.between(last, Instant.now()).getSeconds() < cooldownSeconds) {
            src.sendFailure(Component.literal(
                "\u00a76[JBalance] \u00a7cAguarde antes de enviar novamente."
            ));
            return Command.SINGLE_SUCCESS;
        }

        EconomyService.getInstance().transfer(senderId, targetId, amount)
            .whenComplete((success, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] Transfer failed from {} to {}", senderId, targetId, ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao processar transferencia."));
                    return;
                }
                if (!success) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cSaldo insuficiente ou transferencia em andamento."
                    ));
                    return;
                }
                // Update cooldown after successful transfer
                lastTransfer.put(senderId, Instant.now());
                String formatted = CurrencyFormatter.formatBalance(amount);
                // Notify sender
                src.sendSuccess(() -> Component.literal(
                    "\u00a76[JBalance] \u00a77Voce enviou \u00a76" + formatted + " \u00a77para \u00a76" + targetName
                ), false);
                // Notify receiver (if still online)
                ServerPlayer onlineTarget = src.getServer().getPlayerList().getPlayer(targetId);
                if (onlineTarget != null) {
                    onlineTarget.sendSystemMessage(Component.literal(
                        "\u00a76[JBalance] \u00a77Voce recebeu \u00a76" + formatted + " \u00a77de \u00a76" + senderName
                    ));
                }
            }));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /eco top — top 10 richest players with cache and caller rank (CURR-04)
    // -------------------------------------------------------------------------

    private static int top(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        // Read config on game thread before going async
        long cacheTtlSeconds = JBalanceConfig.TOP_CACHE_SECONDS.get();

        // Determine if caller is a player (console callers see the list but no rank suffix)
        ServerPlayer caller = src.isPlayer() ? src.getPlayerOrException() : null;
        UUID callerUuid = caller != null ? caller.getUUID() : null;

        if (Instant.now().isBefore(cacheExpiry) && !cachedTop.isEmpty()) {
            // Serve from cache on game thread (no async needed)
            renderTop(src, cachedTop, callerUuid, cacheTtlSeconds);
            return Command.SINGLE_SUCCESS;
        }

        // Cache miss — go async to refresh
        EconomyService.getInstance().getTopBalances(10)
            .whenComplete((entries, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] Failed to load top balances", ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao carregar ranking."));
                    return;
                }
                cachedTop = entries;
                cacheExpiry = Instant.now().plusSeconds(cacheTtlSeconds);
                renderTop(src, entries, callerUuid, cacheTtlSeconds);
            }));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Renders the top-10 list to the source. If the caller is a player not appearing in
     * the list, fetches and appends their rank and balance on the next line.
     */
    private static void renderTop(
            CommandSourceStack src,
            List<BalanceRepository.TopEntry> entries,
            UUID callerUuid,
            long cacheTtlSeconds) {

        src.sendSuccess(() -> Component.literal("\u00a76[JBalance] \u00a77--- Top 10 ---"), false);

        boolean callerInTop = false;
        for (int i = 0; i < entries.size(); i++) {
            BalanceRepository.TopEntry entry = entries.get(i);
            String formatted = CurrencyFormatter.formatBalance(entry.balance());
            int rank = i + 1;
            src.sendSuccess(() -> Component.literal(
                "\u00a77#" + rank + " \u00a76" + entry.displayName() + " \u00a77- \u00a76" + formatted
            ), false);
            // Check if caller appears in the list by display name
            if (callerUuid != null && src.isPlayer()) {
                try {
                    String callerName = src.getPlayerOrException().getName().getString();
                    if (entry.displayName().equalsIgnoreCase(callerName)) {
                        callerInTop = true;
                    }
                } catch (Exception ignored) {}
            }
        }

        // If caller is a player and not in top 10, show their rank below the list
        if (callerUuid != null && !callerInTop) {
            EconomyService.getInstance().getPlayerRank(callerUuid)
                .whenComplete((rank, ex) -> src.getServer().execute(() -> {
                    if (ex != null || rank == null || rank < 0) return;
                    EconomyService.getInstance().getBalance(callerUuid)
                        .whenComplete((bal, ex2) -> src.getServer().execute(() -> {
                            if (ex2 != null || bal == null) return;
                            String formatted = CurrencyFormatter.formatBalance(bal);
                            src.sendSuccess(() -> Component.literal(
                                "\u00a77Voce: \u00a76#" + rank + " \u00a77com \u00a76" + formatted
                            ), false);
                        }));
                }));
        }
    }
}
