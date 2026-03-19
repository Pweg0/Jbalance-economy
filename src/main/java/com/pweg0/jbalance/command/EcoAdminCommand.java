package com.pweg0.jbalance.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.util.CurrencyFormatter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Admin commands: /ecoadmin give, take, set.
 * Requires OP level 4 (hidden from non-OPs via .requires).
 * Player argument uses StringArgumentType (not EntityArgument) to support offline players.
 * Offline players are resolved by display_name via BalanceRepository.findByDisplayName().
 * Target players are NEVER notified — only the executing admin sees feedback.
 */
public class EcoAdminCommand {

    private EcoAdminCommand() {}

    /**
     * Registers the /ecoadmin command tree with the given dispatcher.
     * Called by CommandRegistrar during RegisterCommandsEvent.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ecoadmin")
                .requires(src -> src.hasPermission(4))
                .then(Commands.literal("give")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(EcoAdminCommand::give))))
                .then(Commands.literal("take")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(EcoAdminCommand::take))))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder))
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                            .executes(EcoAdminCommand::set))))
        );
    }

    // -------------------------------------------------------------------------
    // /ecoadmin give <player> <amount> — credit coins to any player (CURR-05)
    // -------------------------------------------------------------------------

    private static int give(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");

        EconomyService.getInstance().findByDisplayName(targetName)
            .whenComplete((record, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] findByDisplayName failed for {}", targetName, ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao buscar jogador."));
                    return;
                }
                if (record == null) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cJogador '" + targetName + "' nao encontrado."
                    ));
                    return;
                }
                boolean isOnline = src.getServer().getPlayerList().getPlayer(record.uuid()) != null;
                String offlineTag = isOnline ? "" : " \u00a77(jogador offline)";

                EconomyService.getInstance().give(record.uuid(), amount)
                    .whenComplete((success, ex2) -> src.getServer().execute(() -> {
                        if (ex2 != null) {
                            JBalance.LOGGER.error("[JBalance] give failed for {}", record.uuid(), ex2);
                            src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao dar moedas."));
                            return;
                        }
                        String formatted = CurrencyFormatter.formatBalance(amount);
                        src.sendSuccess(() -> Component.literal(
                            "\u00a76[JBalance] \u00a77Dado \u00a76" + formatted + " \u00a77para \u00a76"
                            + record.displayName() + offlineTag
                        ), false);
                    }));
            }));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /ecoadmin take <player> <amount> — deduct coins from any player (CURR-06)
    // -------------------------------------------------------------------------

    private static int take(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");

        EconomyService.getInstance().findByDisplayName(targetName)
            .whenComplete((record, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] findByDisplayName failed for {}", targetName, ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao buscar jogador."));
                    return;
                }
                if (record == null) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cJogador '" + targetName + "' nao encontrado."
                    ));
                    return;
                }
                boolean isOnline = src.getServer().getPlayerList().getPlayer(record.uuid()) != null;
                String offlineTag = isOnline ? "" : " \u00a77(jogador offline)";

                EconomyService.getInstance().take(record.uuid(), amount)
                    .whenComplete((success, ex2) -> src.getServer().execute(() -> {
                        if (ex2 != null) {
                            JBalance.LOGGER.error("[JBalance] take failed for {}", record.uuid(), ex2);
                            src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao remover moedas."));
                            return;
                        }
                        if (!success) {
                            src.sendFailure(Component.literal(
                                "\u00a76[JBalance] \u00a7cSaldo insuficiente para " + record.displayName() + "."
                            ));
                            return;
                        }
                        String formatted = CurrencyFormatter.formatBalance(amount);
                        src.sendSuccess(() -> Component.literal(
                            "\u00a76[JBalance] \u00a77Removido \u00a76" + formatted + " \u00a77de \u00a76"
                            + record.displayName() + offlineTag
                        ), false);
                    }));
            }));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /ecoadmin set <player> <amount> — set exact balance for any player (CURR-07)
    // -------------------------------------------------------------------------

    private static int set(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");
        long amount = LongArgumentType.getLong(ctx, "amount");

        EconomyService.getInstance().findByDisplayName(targetName)
            .whenComplete((record, ex) -> src.getServer().execute(() -> {
                if (ex != null) {
                    JBalance.LOGGER.error("[JBalance] findByDisplayName failed for {}", targetName, ex);
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao buscar jogador."));
                    return;
                }
                if (record == null) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cJogador '" + targetName + "' nao encontrado."
                    ));
                    return;
                }
                boolean isOnline = src.getServer().getPlayerList().getPlayer(record.uuid()) != null;
                String offlineTag = isOnline ? "" : " \u00a77(jogador offline)";

                EconomyService.getInstance().setBalance(record.uuid(), amount)
                    .whenComplete((success, ex2) -> src.getServer().execute(() -> {
                        if (ex2 != null) {
                            JBalance.LOGGER.error("[JBalance] setBalance failed for {}", record.uuid(), ex2);
                            src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cErro ao definir saldo."));
                            return;
                        }
                        if (!success) {
                            src.sendFailure(Component.literal(
                                "\u00a76[JBalance] \u00a7cFalha ao definir saldo para " + record.displayName() + "."
                            ));
                            return;
                        }
                        String formatted = CurrencyFormatter.formatBalance(amount);
                        src.sendSuccess(() -> Component.literal(
                            "\u00a76[JBalance] \u00a77Saldo de \u00a76" + record.displayName()
                            + " \u00a77definido para \u00a76" + formatted + offlineTag
                        ), false);
                    }));
            }));
        return Command.SINGLE_SUCCESS;
    }
}
