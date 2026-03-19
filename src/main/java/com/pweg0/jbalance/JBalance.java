package com.pweg0.jbalance;

import com.mojang.logging.LogUtils;
import com.pweg0.jbalance.command.CommandRegistrar;
import com.pweg0.jbalance.command.JBalancePermissions;
import com.pweg0.jbalance.config.JBalanceConfig;
import com.pweg0.jbalance.data.db.DatabaseManager;
import com.pweg0.jbalance.data.db.PlaytimeRepository;
import com.pweg0.jbalance.event.EarningsEventHandler;
import com.pweg0.jbalance.event.PlayerEventHandler;
import com.pweg0.jbalance.service.EconomyService;
import com.pweg0.jbalance.service.PlaytimeService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod("jbalance")
public class JBalance {
    public static final Logger LOGGER = LogUtils.getLogger();

    private static DatabaseManager dbManager;
    private static EconomyService economyService;
    private static PlaytimeService playtimeService;

    public JBalance(ModContainer container) {
        LOGGER.info("[JBalance] Initializing JBalance economy mod...");

        // 1. Register SERVER config (available before ServerAboutToStartEvent)
        container.registerConfig(ModConfig.Type.SERVER, JBalanceConfig.SPEC);

        // 2. Subscribe to mod-bus events for config reload
        IEventBus modBus = container.getEventBus();
        modBus.addListener(this::onConfigReloading);

        // 3a. Permission nodes — game bus event, not mod bus
        NeoForge.EVENT_BUS.addListener(JBalancePermissions::onGatherPermissions);

        // 3. Subscribe to game-bus events: player join, server start/stop
        NeoForge.EVENT_BUS.addListener(PlayerEventHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(JBalance::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(JBalance::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CommandRegistrar::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onServerTick);
        NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(EarningsEventHandler::onPlayerLoggedOut);
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == JBalanceConfig.SPEC) {
            LOGGER.info("[JBalance] Config reloaded - new values active immediately");
        }
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        dbManager = new DatabaseManager();
        economyService = new EconomyService(dbManager);
        PlaytimeRepository playtimeRepo = new PlaytimeRepository(dbManager.getDataSource(), dbManager.isMysql());
        playtimeService = new PlaytimeService(playtimeRepo, java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "JBalance-Playtime-DB");
            t.setDaemon(true);
            return t;
        }));
        LOGGER.info("[JBalance] Database and economy service initialized");
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        if (playtimeService != null) playtimeService.flushAll();
        if (economyService != null) economyService.shutdown();
        if (dbManager != null) dbManager.shutdown();
        LOGGER.info("[JBalance] Database connections closed");
    }

    public static DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    public static EconomyService getEconomyService() {
        return economyService;
    }
}
