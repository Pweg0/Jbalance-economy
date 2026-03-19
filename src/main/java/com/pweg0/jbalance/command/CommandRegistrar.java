package com.pweg0.jbalance.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Central registration point for all JBalance commands.
 * Wired via NeoForge.EVENT_BUS.addListener in JBalance constructor.
 */
public class CommandRegistrar {

    private CommandRegistrar() {} // static utility

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        EcoCommand.register(dispatcher);
        EcoAdminCommand.register(dispatcher);
    }
}
