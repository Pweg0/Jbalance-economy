package com.pweg0.jbalance;

import com.mojang.logging.LogUtils;
import com.pweg0.jbalance.config.JBalanceConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@Mod("jbalance")
public class JBalance {
    public static final Logger LOGGER = LogUtils.getLogger();

    public JBalance(ModContainer container) {
        LOGGER.info("[JBalance] Initializing JBalance economy mod...");

        // 1. Register SERVER config (available before ServerAboutToStartEvent)
        container.registerConfig(ModConfig.Type.SERVER, JBalanceConfig.SPEC);

        // 2. Subscribe to mod-bus events for config reload
        IEventBus modBus = container.getEventBus();
        modBus.addListener(this::onConfigReloading);

        // Database and event wiring will be added in Plan 01-03
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == JBalanceConfig.SPEC) {
            LOGGER.info("[JBalance] Config reloaded - new values active immediately");
        }
    }
}
