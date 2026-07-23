package com.sxilverr.enhancedcelestialstweaks.neoforge;

import com.sxilverr.enhancedcelestialstweaks.NeoForgeConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod("enhancedcelestialstweaks")
public final class EnhancedCelestialsTweaksNeoForge {

    public EnhancedCelestialsTweaksNeoForge(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SPEC, "enhancedcelestialstweaks-common.toml");
        container.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfig.CLIENT_SPEC, "enhancedcelestialstweaks-client.toml");
    }
}
