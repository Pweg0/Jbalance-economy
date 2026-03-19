package com.pweg0.jbalance;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod("jbalance")
public class JBalance {
    public static final Logger LOGGER = LogUtils.getLogger();

    public JBalance(ModContainer container) {
        LOGGER.info("[JBalance] Initializing JBalance economy mod...");
        // Config registration and event wiring will be added in Plan 01-02 and 01-03
    }
}
