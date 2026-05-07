package com.sxilverr.enhancedcelestialstweaks;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(EnhancedCelestialsTweaks.MOD_ID)
public final class EnhancedCelestialsTweaks {

    public static final String MOD_ID = "enhancedcelestialstweaks";
    public static final String EC_MOD_ID = "enhancedcelestials";
    public static final Logger LOGGER = LogManager.getLogger("EnhancedCelestialsTweaks");

    public EnhancedCelestialsTweaks() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ECTweaksConfig.SPEC, MOD_ID + "-common.toml");
        MinecraftForge.EVENT_BUS.register(ECTweaksApplier.class);
    }
}
