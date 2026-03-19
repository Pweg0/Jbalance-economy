package com.pweg0.jbalance.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.pweg0.jbalance.JBalance;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.util.CurrencyFormatter;
import com.pweg0.jbalance.service.ShopService;
import com.pweg0.jbalance.util.CurrencyFormatter;
import com.pweg0.jbalance.util.DiscordWebhook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.server.permission.PermissionAPI;

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
                .requires(EcoAdminCommand::isAdmin)
                .executes(EcoAdminCommand::help)
                .then(Commands.literal("help")
                    .executes(EcoAdminCommand::help))
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
                .then(Commands.literal("setloja")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(EcoAdminCommand::adminSetLoja)))
                .then(Commands.literal("delloja")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ctx.getSource().getServer().getPlayerNames(), builder))
                        .executes(EcoAdminCommand::adminDelLoja)))
        );
    }

    // -------------------------------------------------------------------------
    // Permission check — uses NeoForge PermissionAPI (LuckPerms compatible)
    // Console always passes. Fallback: OP level 4.
    // -------------------------------------------------------------------------

    private static boolean isAdmin(CommandSourceStack src) {
        if (!src.isPlayer()) return true;
        try {
            // Any admin permission grants access to the base /ecoadmin command
            var player = src.getPlayerOrException();
            return PermissionAPI.getPermission(player, JBalancePermissions.ADMIN_GIVE)
                || PermissionAPI.getPermission(player, JBalancePermissions.ADMIN_TAKE)
                || PermissionAPI.getPermission(player, JBalancePermissions.ADMIN_SET);
        } catch (Exception e) {
            return src.hasPermission(4);
        }
    }

    // -------------------------------------------------------------------------
    // /ecoadmin help
    // -------------------------------------------------------------------------

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("\u00a76[JBalance Admin] \u00a77Comandos disponiveis:"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/ecoadmin give <jogador> <valor> \u00a77- Dar moedas"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/ecoadmin take <jogador> <valor> \u00a77- Remover moedas"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/ecoadmin set <jogador> <valor> \u00a77- Definir saldo"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/ecoadmin setloja <jogador> \u00a77- Criar loja para jogador"), false);
        src.sendSuccess(() -> Component.literal("\u00a76/ecoadmin delloja <jogador> \u00a77- Deletar loja de jogador"), false);
        return Command.SINGLE_SUCCESS;
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
                        String adminName = src.getTextName();
                        DiscordWebhook.logAdminGive(adminName, record.displayName(), formatted, isOnline);
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
                        String adminName = src.getTextName();
                        DiscordWebhook.logAdminTake(adminName, record.displayName(), formatted, isOnline);
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
                        String adminName = src.getTextName();
                        DiscordWebhook.logAdminSet(adminName, record.displayName(), formatted, isOnline);
                        src.sendSuccess(() -> Component.literal(
                            "\u00a76[JBalance] \u00a77Saldo de \u00a76" + record.displayName()
                            + " \u00a77definido para \u00a76" + formatted + offlineTag
                        ), false);
                    }));
            }));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /ecoadmin setloja <player> — create shop for a player at admin's position
    // -------------------------------------------------------------------------

    private static int adminSetLoja(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");

        EconomyService.getInstance().findByDisplayName(targetName)
            .whenComplete((record, ex) -> src.getServer().execute(() -> {
                if (record == null) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cJogador '" + targetName + "' nao encontrado."
                    ));
                    return;
                }
                double x, y, z;
                float yaw, pitch;
                String dim;
                if (src.isPlayer()) {
                    try {
                        ServerPlayer admin = src.getPlayerOrException();
                        x = admin.getX(); y = admin.getY(); z = admin.getZ();
                        yaw = admin.getYRot(); pitch = admin.getXRot();
                        dim = admin.level().dimension().location().toString();
                    } catch (Exception e) { return; }
                } else {
                    src.sendFailure(Component.literal("\u00a76[JBalance] \u00a7cApenas jogadores podem usar este comando."));
                    return;
                }
                ShopService.getInstance().createShop(record.uuid(), x, y, z, yaw, pitch, dim)
                    .whenComplete((v, ex2) -> src.getServer().execute(() -> {
                        src.sendSuccess(() -> Component.literal(
                            "\u00a76[JBalance] \u00a77Loja criada para \u00a76" + record.displayName()
                        ), false);
                        DiscordWebhook.send("Admin: Loja Criada",
                            "**Admin:** " + src.getTextName() + "\n**Para:** " + record.displayName(),
                            DiscordWebhook.COLOR_ADMIN);
                    }));
            }));
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /ecoadmin delloja <player> — delete shop for a player
    // -------------------------------------------------------------------------

    private static int adminDelLoja(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");

        EconomyService.getInstance().findByDisplayName(targetName)
            .whenComplete((record, ex) -> src.getServer().execute(() -> {
                if (record == null) {
                    src.sendFailure(Component.literal(
                        "\u00a76[JBalance] \u00a7cJogador '" + targetName + "' nao encontrado."
                    ));
                    return;
                }
                ShopService.getInstance().deleteShop(record.uuid())
                    .whenComplete((v, ex2) -> src.getServer().execute(() -> {
                        src.sendSuccess(() -> Component.literal(
                            "\u00a76[JBalance] \u00a77Loja de \u00a76" + record.displayName() + " \u00a77removida."
                        ), false);
                        DiscordWebhook.send("Admin: Loja Removida",
                            "**Admin:** " + src.getTextName() + "\n**Jogador:** " + record.displayName(),
                            DiscordWebhook.COLOR_ADMIN);
                    }));
            }));
        return Command.SINGLE_SUCCESS;
    }
}
