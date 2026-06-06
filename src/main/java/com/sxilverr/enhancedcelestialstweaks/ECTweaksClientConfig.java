package com.sxilverr.enhancedcelestialstweaks;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ECTweaksClientConfig {

    public static final ForgeConfigSpec SPEC;
    public static final General GENERAL;
    public static final Map<String, EventClient> EVENTS = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Client-only settings.").push("general");
        GENERAL = new General(b);
        b.pop();

        b.comment("Per-event client-only visuals and sound.").push("events");
        for (Map.Entry<String, ECTweaksConfig.EventDefaults> entry : ECTweaksConfig.EVENT_DEFAULTS.entrySet()) {
            b.push(entry.getKey());
            EVENTS.put(entry.getKey(), new EventClient(b, entry.getValue()));
            b.pop();
        }
        b.pop();

        SPEC = b.build();
    }

    public static final class General {
        public final ForgeConfigSpec.ConfigValue<String> sleepPreventedMessage;

        General(ForgeConfigSpec.Builder b) {
            sleepPreventedMessage = b.comment(
                    "Text shown above the hotbar when sleep is blocked by a lunar event. Supports & color codes.",
                    "Client-only: overrides the message on your own client. On a server each player sets their own value, and players without this mod see Enhanced Celestials' default text.",
                    "Enhanced Celestials forces the base color to red, so a leading color code may have no effect; use & codes mid-text for per-segment coloring.")
                    .define("sleep_prevented_message", "You may not rest now because the current lunar event prevents it.");
        }
    }

    public static final class EventClient {
        public final ForgeConfigSpec.ConfigValue<String> fogColor;
        public final ForgeConfigSpec.DoubleValue fogDensityMultiplier;
        public final ForgeConfigSpec.ConfigValue<String> soundtrack;
        public final ForgeConfigSpec.DoubleValue soundtrackVolume;
        public final ForgeConfigSpec.DoubleValue soundtrackPitch;
        public final ForgeConfigSpec.BooleanValue soundtrackLoop;

        EventClient(ForgeConfigSpec.Builder b, ECTweaksConfig.EventDefaults defaults) {
            fogColor = b.comment("Fog hex color during the event. Empty = vanilla.")
                    .define("fog_color", "");
            fogDensityMultiplier = b.comment("Fog density multiplier during the event. 1.0 = vanilla.")
                    .defineInRange("fog_density_multiplier", 1.0, 0.01, 100.0);
            soundtrack = b.comment("Sound event id played during the event. Empty = no music.")
                    .define("soundtrack", defaults.soundtrack());
            soundtrackVolume = b.comment("Soundtrack volume.")
                    .defineInRange("soundtrack_volume", 1.0, 0.0, 10.0);
            soundtrackPitch = b.comment("Soundtrack pitch.")
                    .defineInRange("soundtrack_pitch", 1.0, 0.1, 10.0);
            soundtrackLoop = b.comment("Loop the soundtrack while the event is active.")
                    .define("soundtrack_loop", true);
        }
    }
}
