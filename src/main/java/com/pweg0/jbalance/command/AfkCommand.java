package com.pweg0.jbalance.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.pweg0.jbalance.service.PlaytimeService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;

/**
 * /afk — toggles AFK status. Requires jbalance.afk permission.
 * Players with the permission can stay AFK without being kicked.
 */
public class AfkCommand {

    private AfkCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("afk")
                .executes(AfkCommand::toggleAfk)
        );
    }

    private static int toggleAfk(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();

        // Permission check
        try {
            if (!PermissionAPI.getPermission(player, JBalancePermissions.AFK)) {
                src.sendFailure(Component.literal(
                    "\u00a76[JBalance] \u00a7cVoce nao tem permissao para usar /afk."
                ));
                return Command.SINGLE_SUCCESS;
            }
        } catch (Exception ignored) {
            src.sendFailure(Component.literal(
                "\u00a76[JBalance] \u00a7cVoce nao tem permissao para usar /afk."
            ));
            return Command.SINGLE_SUCCESS;
        }

        PlaytimeService svc = PlaytimeService.getInstance();
        if (svc == null) return Command.SINGLE_SUCCESS;

        boolean nowAfk = svc.toggleAfk(player.getUUID());

        if (nowAfk) {
            // Broadcast to all players
            src.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("\u00a77* \u00a76" + player.getName().getString() + " \u00a77ficou AFK."),
                false
            );
        } else {
            src.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("\u00a77* \u00a76" + player.getName().getString() + " \u00a77voltou do AFK."),
                false
            );
        }

        return Command.SINGLE_SUCCESS;
    }
}
