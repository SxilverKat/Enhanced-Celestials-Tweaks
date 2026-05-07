package com.sxilverr.enhancedcelestialstweaks;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ECTweaksConfig {

    public static final Map<String, EventDefaults> EVENT_DEFAULTS = new LinkedHashMap<>();
    static {
        EVENT_DEFAULTS.put("blood_moon", new EventDefaults(
                List.of("MONSTER:2.25"),
                "990000", "990000",
                "enhancedcelestials:blood_moon",
                "&cThe \"Blood Moon\" rises... Distant sounds of the undead can be heard...",
                "&cThe \"Blood Moon\" sets... The undead begin to burn...",
                false, List.of(),
                List.of()
        ));
        EVENT_DEFAULTS.put("super_blood_moon", new EventDefaults(
                List.of("MONSTER:4.5"),
                "ff0000", "ff0000",
                "enhancedcelestials:blood_moon",
                "&c&lThe \"Super Blood Moon\" rises... Distant sounds of the undead can be heard...",
                "&cThe \"Super Blood Moon\" sets... The undead begin to burn...",
                false, List.of(),
                List.of()
        ));
        EVENT_DEFAULTS.put("harvest_moon", new EventDefaults(
                List.of(),
                "665828", "99833b",
                "enhancedcelestials:harvest_moon",
                "&eThe \"Harvest Moon\" rises... Your crops provide a better harvest.",
                "&eThe \"Harvest Moon\" sets...",
                true, List.of("enhancedcelestials:harvest_moon_crops;2.0"),
                List.of()
        ));
        EVENT_DEFAULTS.put("super_harvest_moon", new EventDefaults(
                List.of(),
                "ffdb63", "ffdb63",
                "enhancedcelestials:harvest_moon",
                "&e&lThe \"Super Harvest Moon\" rises... Your crops provide an exceptional harvest.",
                "&eThe \"Super Harvest Moon\" sets...",
                true, List.of("enhancedcelestials:harvest_moon_crops;4.0"),
                List.of()
        ));
        EVENT_DEFAULTS.put("blue_moon", new EventDefaults(
                List.of(),
                "009999", "009999",
                "enhancedcelestials:blue_moon",
                "&bThe \"Blue Moon\" rises... You feel lucky!",
                "&bThe \"Blue Moon\" sets... You don't feel lucky anymore...",
                false, List.of(),
                List.of("minecraft:luck;0;1210")
        ));
        EVENT_DEFAULTS.put("super_blue_moon", new EventDefaults(
                List.of(),
                "00ffff", "00ffff",
                "enhancedcelestials:blue_moon",
                "&bThe \"Super Blue Moon\" rises... You feel very lucky!",
                "&bThe \"Super Blue Moon\" sets... You don't feel very lucky anymore...",
                false, List.of(),
                List.of("minecraft:luck;4;1210")
        ));
        EVENT_DEFAULTS.put("super_moon", new EventDefaults(
                List.of(),
                "ffffff", "6766ff",
                "",
                "&dThe \"Super Moon\" rises... Slimes begin to spawn nearby.",
                "&dThe \"Super Moon\" sets...",
                false, List.of(),
                List.of()
        ));
    }

    public static final ForgeConfigSpec SPEC;
    public static final General GENERAL;
    public static final Dimension OVERWORLD;
    public static final Map<String, EventTweaks> EVENTS = new LinkedHashMap<>();

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("General settings.").push("general");
        GENERAL = new General(b);
        b.pop();

        b.comment("Overworld dimension settings.").push("overworld");
        OVERWORLD = new Dimension(b);
        b.pop();

        b.comment("Per-event tweaks. Set chance_multiplier to 0 to disable an event.").push("events");
        for (Map.Entry<String, EventDefaults> entry : EVENT_DEFAULTS.entrySet()) {
            b.push(entry.getKey());
            EVENTS.put(entry.getKey(), new EventTweaks(b, entry.getValue(), entry.getKey()));
            b.pop();
        }
        b.pop();

        SPEC = b.build();
    }

    public record EventDefaults(
            List<String> mobCategoryMultipliers,
            String moonColor,
            String skyColor,
            String soundtrack,
            String startMessage,
            String endMessage,
            boolean enableCropDropBoost,
            List<String> cropDropTags,
            List<String> mobEffects
    ) {}

    public static final class General {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.BooleanValue logTweaks;
        public final ForgeConfigSpec.BooleanValue eventsVisualOnly;
        public final ForgeConfigSpec.BooleanValue recomputeForecastOnStart;
        public final ForgeConfigSpec.BooleanValue disableEcCommandsForNonOps;
        public final ForgeConfigSpec.ConfigValue<String> sleepPreventedMessage;

        General(ForgeConfigSpec.Builder b) {
            enabled = b.comment("Master toggle for all tweaks.").define("enabled", true);
            logTweaks = b.comment("Log applied tweaks to the console.").define("log_tweaks", false);
            eventsVisualOnly = b.comment("Makes events a visual only").define("events_visual_only", false);
            recomputeForecastOnStart = b.comment("Rebuild the lunar forecast on server start so config changes apply to scheduled events.")
                    .define("recompute_forecast_on_start", true);
            disableEcCommandsForNonOps = b.comment("Block Enhanced Celestials commands for non-operators.")
                    .define("disable_ec_commands_for_non_ops", false);
            sleepPreventedMessage = b.comment("Text shown above the hotbar when sleep is blocked by a lunar event. Supports & color codes.")
                    .define("sleep_prevented_message", "You may not rest now because the current lunar event prevents it.");
        }
    }

    public static final class Dimension {
        public final ForgeConfigSpec.EnumValue<BoolOverride> requireClearSkies;
        public final ForgeConfigSpec.LongValue minDaysBetweenEvents;
        public final ForgeConfigSpec.LongValue maxDaysBetweenEvents;
        public final ForgeConfigSpec.LongValue dayLength;
        public final ForgeConfigSpec.LongValue yearLengthInDays;

        Dimension(ForgeConfigSpec.Builder b) {
            requireClearSkies = b.comment("Whether events require clear skies.")
                    .defineEnum("require_clear_skies", BoolOverride.DEFAULT);
            minDaysBetweenEvents = b.comment("Minimum days between events. -1 keeps default.")
                    .defineInRange("min_days_between_events", -1L, -1L, Long.MAX_VALUE);
            maxDaysBetweenEvents = b.comment("Maximum days between events. -1 keeps default.")
                    .defineInRange("max_days_between_events", -1L, -1L, Long.MAX_VALUE);
            dayLength = b.comment("Day length in ticks. -1 keeps default.")
                    .defineInRange("day_length", -1L, -1L, Long.MAX_VALUE);
            yearLengthInDays = b.comment("Year length in days. -1 keeps default.")
                    .defineInRange("year_length_in_days", -1L, -1L, Long.MAX_VALUE);
        }
    }

    public static final class EventTweaks {
        public final ForgeConfigSpec.DoubleValue chanceMultiplier;
        public final ForgeConfigSpec.IntValue minNightsBetween;
        public final ForgeConfigSpec.DoubleValue mobSpawnMultiplier;
        public final ForgeConfigSpec.EnumValue<BoolOverride> blockSleeping;
        public final ForgeConfigSpec.EnumValue<BoolOverride> useBiomeSpawnSettings;
        public final ForgeConfigSpec.EnumValue<BoolOverride> forceSurfaceSpawning;
        public final ForgeConfigSpec.EnumValue<BoolOverride> slimesSpawnEverywhere;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mobCategoryMultipliers;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> spawnAdditions;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> spawnRemovals;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> addedDimensions;
        public final ForgeConfigSpec.IntValue monsterSpawnLightLevel;
        public final ForgeConfigSpec.DoubleValue mobGearChanceMultiplier;
        public final ForgeConfigSpec.BooleanValue mobDropsEventGear;
        public final ForgeConfigSpec.DoubleValue rareDropMultiplier;
        public final ForgeConfigSpec.DoubleValue mobHealthMultiplier;
        public final ForgeConfigSpec.DoubleValue mobDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue mobSpeedMultiplier;
        public final ForgeConfigSpec.DoubleValue mobSwimSpeedMultiplier;
        public final ForgeConfigSpec.BooleanValue preventMobDespawn;
        public final ForgeConfigSpec.BooleanValue forceDespawnAfterEvent;
        public final ForgeConfigSpec.IntValue forceDespawnDelaySeconds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mobEffects;
        public final ForgeConfigSpec.LongValue nightLengthTicks;
        public final ForgeConfigSpec.ConfigValue<String> moonColor;
        public final ForgeConfigSpec.ConfigValue<String> skyColor;
        public final ForgeConfigSpec.ConfigValue<String> moonTexture;
        public final ForgeConfigSpec.DoubleValue fogDensityMultiplier;
        public final ForgeConfigSpec.ConfigValue<String> fogColor;
        public final ForgeConfigSpec.ConfigValue<String> soundtrack;
        public final ForgeConfigSpec.DoubleValue soundtrackVolume;
        public final ForgeConfigSpec.DoubleValue soundtrackPitch;
        public final ForgeConfigSpec.BooleanValue soundtrackLoop;
        public final ForgeConfigSpec.ConfigValue<String> startMessage;
        public final ForgeConfigSpec.ConfigValue<String> endMessage;
        public final ForgeConfigSpec.BooleanValue enableCropDropBoost;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> cropDropTags;

        EventTweaks(ForgeConfigSpec.Builder b, EventDefaults defaults, String eventName) {
            chanceMultiplier = b.comment("Multiplier on the event's spawn chance. 0 disables it.")
                    .defineInRange("chance_multiplier", 1.0, 0.0, 1000.0);
            minNightsBetween = b.comment("Minimum nights between this event. -1 keeps default.")
                    .defineInRange("min_nights_between", -1, -1, Integer.MAX_VALUE);
            mobSpawnMultiplier = b.comment("Multiplier applied to this event's mob spawn rates.")
                    .defineInRange("mob_spawn_multiplier", 1.0, 0.0, 1000.0);
            blockSleeping = b.comment("Whether this event blocks sleeping.")
                    .defineEnum("block_sleeping", BoolOverride.DEFAULT);
            useBiomeSpawnSettings = b.comment("Use the biome's normal spawn list during this event.")
                    .defineEnum("use_biome_spawn_settings", BoolOverride.DEFAULT);
            forceSurfaceSpawning = b.comment("Force mobs to spawn on the surface during this event.")
                    .defineEnum("force_surface_spawning", BoolOverride.DEFAULT);
            slimesSpawnEverywhere = b.comment("Allow slimes to spawn anywhere during this event.")
                    .defineEnum("slimes_spawn_everywhere", BoolOverride.DEFAULT);
            // MONSTER, CREATURE, AMBIENT, AXOLOTLS, UNDERGROUND_WATER_CREATURE, WATER_CREATURE, WATER_AMBIENT, MISC
            mobCategoryMultipliers = b.comment("Per-category spawn multipliers. Format: \"CATEGORY:value\"")
                    .defineList("mob_category_multipliers", defaults.mobCategoryMultipliers(), o -> o instanceof String s && s.split(":").length == 2);
            spawnAdditions = b.comment("Mobs to add during this event. Format: \"entity_id;weight;min;max\".")
                    .defineList("spawn_additions", List.of(), o -> o instanceof String s && s.split(";").length == 4);
            spawnRemovals = b.comment("Entity ids to block from spawning during this event.")
                    .defineList("spawn_removals", List.of(), o -> o instanceof String);
            addedDimensions = b.comment("Dimensions to add this event to. Format: \"namespace:dimension_id\". Uses overworld settings as template.")
                    .defineList("added_dimensions", List.of(), o -> o instanceof String);
            monsterSpawnLightLevel = b.comment("Maximum block light level monsters can spawn at during this event. 0 = vanilla.")
                    .defineInRange("monster_spawn_light_level", 0, 0, 15);
            mobGearChanceMultiplier = b.comment("Multiplier on the vanilla chance for monsters to spawn with gear (armor + weapons).")
                    .defineInRange("mob_gear_chance_multiplier", 1.0, 0.0, 1000.0);
            mobDropsEventGear = b.comment("Whether mobs drop the gear given to them by this event.")
                    .define("mob_drops_event_gear", false);
            rareDropMultiplier = b.comment("Chance multiplier for non-common drops (uncommon, rare, epic) during this event.")
                    .defineInRange("rare_drop_multiplier", 1.0, 1.0, 100.0);
            mobHealthMultiplier = b.comment("Multiplier on max health for mobs spawned during this event.")
                    .defineInRange("mob_health_multiplier", 1.0, 0.01, 1000.0);
            mobDamageMultiplier = b.comment("Multiplier on attack damage for mobs spawned during this event.")
                    .defineInRange("mob_damage_multiplier", 1.0, 0.01, 1000.0);
            mobSpeedMultiplier = b.comment("Multiplier on movement speed for mobs spawned during this event.")
                    .defineInRange("mob_speed_multiplier", 1.0, 0.01, 1000.0);
            mobSwimSpeedMultiplier = b.comment("Multiplier on swim speed for mobs spawned during this event.")
                    .defineInRange("mob_swim_speed_multiplier", 1.0, 0.01, 1000.0);
            preventMobDespawn = b.comment("Prevent mobs spawned during this event from despawning.")
                    .define("prevent_mob_despawn", false);
            forceDespawnAfterEvent = b.comment("Despawn all mobs spawned during this event after the event ends.")
                    .define("force_despawn_after_event", false);
            forceDespawnDelaySeconds = b.comment("Seconds after the event ends before forced despawn happens.")
                    .defineInRange("force_despawn_delay_seconds", 180, 0, Integer.MAX_VALUE);
            // Format: "effect_id;amplifier;duration[;target]" where target = all (default), players, mobs, monsters
            mobEffects = b.comment("Status effects applied while the event is active. Format: \"effect_id;amplifier;duration[;target]\"")
                    .defineList("mob_effects", defaults.mobEffects(), o -> o instanceof String s && s.split(";").length >= 3);
            nightLengthTicks = b.comment("How many ticks the night lasts during this event (vanilla = 12000).")
                    .defineInRange("night_length_ticks", 12000L, 1L, Long.MAX_VALUE);
            moonColor = b.comment("Moon texture hex color. Empty = white.")
                    .define("moon_color", defaults.moonColor());
            skyColor = b.comment("Sky/light hex color during the event. Empty = white.")
                    .define("sky_color", defaults.skyColor());
            moonTexture = b.comment("Moon texture override (ResourceLocation). Empty = vanilla phases.")
                    .define("moon_texture", "");
            fogDensityMultiplier = b.comment("Fog density multiplier during the event. 1.0 = vanilla.")
                    .defineInRange("fog_density_multiplier", 1.0, 0.0, 100.0);
            fogColor = b.comment("Fog hex color during the event. Empty = vanilla.")
                    .define("fog_color", "");
            soundtrack = b.comment("Sound event id played during the event. Empty = no music.")
                    .define("soundtrack", defaults.soundtrack());
            soundtrackVolume = b.comment("Soundtrack volume.")
                    .defineInRange("soundtrack_volume", 1.0, 0.0, 10.0);
            soundtrackPitch = b.comment("Soundtrack pitch.")
                    .defineInRange("soundtrack_pitch", 1.0, 0.1, 10.0);
            soundtrackLoop = b.comment("Loop the soundtrack while the event is active.")
                    .define("soundtrack_loop", true);
            startMessage = b.comment("Message shown when the event starts. Supports & color codes. Empty = no message.")
                    .define("start_message", defaults.startMessage());
            endMessage = b.comment("Message shown when the event ends. Supports & color codes. Empty = no message.")
                    .define("end_message", defaults.endMessage());

            boolean isHarvest = eventName.equals("harvest_moon") || eventName.equals("super_harvest_moon");
            if (isHarvest) {
                enableCropDropBoost = b.comment("Multiply drops for items in the configured tags while this event is active.")
                        .define("enable_crop_drop_boost", defaults.enableCropDropBoost());
                cropDropTags = b.comment("Item tags to multiply drops for. Format: \"tag_id;multiplier\".")
                        .defineList("crop_drop_tags", defaults.cropDropTags(), o -> o instanceof String s && s.split(";").length == 2);
            } else {
                enableCropDropBoost = null;
                cropDropTags = null;
            }
        }
    }
}
