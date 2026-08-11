package com.sxilverr.enhancedcelestialstweaks;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = "enhancedcelestialstweaks")
public final class NeoForgeConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    private static final GeneralSpec GENERAL;
    private static final DimensionSpec OVERWORLD;
    private static final Map<String, EventSpec> EVENTS = new java.util.LinkedHashMap<>();
    private static final ClientGeneralSpec CLIENT_GENERAL;
    private static final Map<String, ClientEventSpec> CLIENT_EVENTS = new java.util.LinkedHashMap<>();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("General settings.").push("general");
        GENERAL = new GeneralSpec(b);
        b.pop();
        b.comment("Overworld dimension settings.").push("overworld");
        OVERWORLD = new DimensionSpec(b);
        b.pop();
        b.comment("Per-event tweaks. Set chance_multiplier to 0 to disable an event.").push("events");
        for (Map.Entry<String, ECTweaksConfig.EventDefaults> entry : ECTweaksConfig.EVENT_DEFAULTS.entrySet()) {
            b.push(entry.getKey());
            EVENTS.put(entry.getKey(), new EventSpec(b, entry.getValue(), entry.getKey()));
            b.pop();
        }
        b.pop();
        SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.comment("Client-only settings.").push("general");
        CLIENT_GENERAL = new ClientGeneralSpec(c);
        c.pop();
        c.comment("Per-event client-only visuals and sound.").push("events");
        for (Map.Entry<String, ECTweaksConfig.EventDefaults> entry : ECTweaksConfig.EVENT_DEFAULTS.entrySet()) {
            c.push(entry.getKey());
            CLIENT_EVENTS.put(entry.getKey(), new ClientEventSpec(c, entry.getValue()));
            c.pop();
        }
        c.pop();
        CLIENT_SPEC = c.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        bake();
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        bake();
    }

    private static void bake() {
        if (SPEC.isLoaded()) {
            ECTweaksConfig.GENERAL.enabled = GENERAL.enabled.get();
            ECTweaksConfig.GENERAL.logTweaks = GENERAL.logTweaks.get();
            ECTweaksConfig.GENERAL.eventsVisualOnly = GENERAL.eventsVisualOnly.get();
            ECTweaksConfig.GENERAL.recomputeForecastOnStart = GENERAL.recomputeForecastOnStart.get();

            ECTweaksConfig.OVERWORLD.requireClearSkies = OVERWORLD.requireClearSkies.get();
            ECTweaksConfig.OVERWORLD.minDaysBetweenEvents = OVERWORLD.minDaysBetweenEvents.get();
            ECTweaksConfig.OVERWORLD.maxDaysBetweenEvents = OVERWORLD.maxDaysBetweenEvents.get();
            ECTweaksConfig.OVERWORLD.dayLength = OVERWORLD.dayLength.get();
            ECTweaksConfig.OVERWORLD.yearLengthInDays = OVERWORLD.yearLengthInDays.get();

            for (Map.Entry<String, EventSpec> e : EVENTS.entrySet()) {
                EventSpec s = e.getValue();
                ECTweaksConfig.EventTweaks h = ECTweaksConfig.EVENTS.get(e.getKey());
                if (h == null) continue;
                h.chanceMultiplier = s.chanceMultiplier.get();
                h.minNightsBetween = s.minNightsBetween.get();
                h.validMoonPhases = ECTweaksConfig.toMoonPhaseStrings(s.validMoonPhases.get());
                h.mobSpawnMultiplier = s.mobSpawnMultiplier.get();
                h.mobCapMultiplier = s.mobCapMultiplier.get();
                h.blockSleeping = s.blockSleeping.get();
                h.useBiomeSpawnSettings = s.useBiomeSpawnSettings.get();
                h.forceSurfaceSpawning = s.forceSurfaceSpawning.get();
                h.slimesSpawnEverywhere = s.slimesSpawnEverywhere.get();
                h.weatherOverride = s.weatherOverride.get();
                h.restoreWeatherAfterEvent = s.restoreWeatherAfterEvent.get();
                h.mobCategoryMultipliers = copy(s.mobCategoryMultipliers.get());
                h.spawnAdditions = copy(s.spawnAdditions.get());
                h.spawnRemovals = copy(s.spawnRemovals.get());
                h.addedDimensions = copy(s.addedDimensions.get());
                h.monsterSpawnLightLevel = s.monsterSpawnLightLevel.get();
                h.mobGearChanceMultiplier = s.mobGearChanceMultiplier.get();
                h.mobDropsEventGear = s.mobDropsEventGear.get();
                h.rareDropMultiplier = s.rareDropMultiplier.get();
                h.rareDropOnlyEventMobs = s.rareDropOnlyEventMobs.get();
                h.xpDropMultiplier = s.xpDropMultiplier.get();
                h.xpOnlyEventMobs = s.xpOnlyEventMobs.get();
                h.mobHealthMultiplier = s.mobHealthMultiplier.get();
                h.mobDamageMultiplier = s.mobDamageMultiplier.get();
                h.mobSpeedMultiplier = s.mobSpeedMultiplier.get();
                h.mobSwimSpeedMultiplier = s.mobSwimSpeedMultiplier.get();
                h.preventMobDespawn = s.preventMobDespawn.get();
                h.forceDespawnAfterEvent = s.forceDespawnAfterEvent.get();
                h.forceDespawnDelaySeconds = s.forceDespawnDelaySeconds.get();
                h.mobEffects = copy(s.mobEffects.get());
                h.nightLengthTicks = s.nightLengthTicks.get();
                h.moonColor = s.moonColor.get();
                h.skyColor = s.skyColor.get();
                h.moonTexture = s.moonTexture.get();
                h.moonSizeMultiplier = s.moonSizeMultiplier.get();
                h.startMessage = s.startMessage.get();
                h.endMessage = s.endMessage.get();
                if (s.enableCropDropBoost != null) {
                    h.enableCropDropBoost = s.enableCropDropBoost.get();
                    h.cropDropTags = copy(s.cropDropTags.get());
                }
            }
            ECTweaksApplier.recomputeRuntimeFlags();
        }

        if (CLIENT_SPEC.isLoaded()) {
            ECTweaksClientConfig.GENERAL.sleepPreventedMessage = CLIENT_GENERAL.sleepPreventedMessage.get();
            for (Map.Entry<String, ClientEventSpec> e : CLIENT_EVENTS.entrySet()) {
                ClientEventSpec s = e.getValue();
                ECTweaksClientConfig.EventClient h = ECTweaksClientConfig.EVENTS.get(e.getKey());
                if (h == null) continue;
                h.fogColor = s.fogColor.get();
                h.fogDensityMultiplier = s.fogDensityMultiplier.get();
                h.soundtrack = s.soundtrack.get();
                h.soundtrackVolume = s.soundtrackVolume.get();
                h.soundtrackPitch = s.soundtrackPitch.get();
                h.soundtrackLoop = s.soundtrackLoop.get();
            }
        }
    }

    private static List<String> copy(List<? extends String> in) {
        return new ArrayList<>(in);
    }

    static final class GeneralSpec {
        final ModConfigSpec.BooleanValue enabled;
        final ModConfigSpec.BooleanValue logTweaks;
        final ModConfigSpec.BooleanValue eventsVisualOnly;
        final ModConfigSpec.BooleanValue recomputeForecastOnStart;

        GeneralSpec(ModConfigSpec.Builder b) {
            enabled = b.comment("Master toggle for all tweaks.").define("enabled", true);
            logTweaks = b.comment("Log applied tweaks to the console.").define("log_tweaks", false);
            eventsVisualOnly = b.comment("Make all events visual-only.")
                    .define("events_visual_only", false);
            recomputeForecastOnStart = b.comment("Rebuild the lunar forecast on server start so config changes apply to scheduled events.")
                    .define("recompute_forecast_on_start", true);
        }
    }

    static final class DimensionSpec {
        final ModConfigSpec.EnumValue<BoolOverride> requireClearSkies;
        final ModConfigSpec.LongValue minDaysBetweenEvents;
        final ModConfigSpec.LongValue maxDaysBetweenEvents;
        final ModConfigSpec.LongValue dayLength;
        final ModConfigSpec.LongValue yearLengthInDays;

        DimensionSpec(ModConfigSpec.Builder b) {
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

    static final class EventSpec {
        final ModConfigSpec.DoubleValue chanceMultiplier;
        final ModConfigSpec.IntValue minNightsBetween;
        final ModConfigSpec.ConfigValue<List<? extends Object>> validMoonPhases;
        final ModConfigSpec.DoubleValue mobSpawnMultiplier;
        final ModConfigSpec.DoubleValue mobCapMultiplier;
        final ModConfigSpec.EnumValue<BoolOverride> blockSleeping;
        final ModConfigSpec.EnumValue<BoolOverride> useBiomeSpawnSettings;
        final ModConfigSpec.EnumValue<BoolOverride> forceSurfaceSpawning;
        final ModConfigSpec.EnumValue<BoolOverride> slimesSpawnEverywhere;
        final ModConfigSpec.EnumValue<WeatherOverride> weatherOverride;
        final ModConfigSpec.BooleanValue restoreWeatherAfterEvent;
        final ModConfigSpec.ConfigValue<List<? extends String>> mobCategoryMultipliers;
        final ModConfigSpec.ConfigValue<List<? extends String>> spawnAdditions;
        final ModConfigSpec.ConfigValue<List<? extends String>> spawnRemovals;
        final ModConfigSpec.ConfigValue<List<? extends String>> addedDimensions;
        final ModConfigSpec.IntValue monsterSpawnLightLevel;
        final ModConfigSpec.DoubleValue mobGearChanceMultiplier;
        final ModConfigSpec.BooleanValue mobDropsEventGear;
        final ModConfigSpec.DoubleValue rareDropMultiplier;
        final ModConfigSpec.BooleanValue rareDropOnlyEventMobs;
        final ModConfigSpec.DoubleValue xpDropMultiplier;
        final ModConfigSpec.BooleanValue xpOnlyEventMobs;
        final ModConfigSpec.DoubleValue mobHealthMultiplier;
        final ModConfigSpec.DoubleValue mobDamageMultiplier;
        final ModConfigSpec.DoubleValue mobSpeedMultiplier;
        final ModConfigSpec.DoubleValue mobSwimSpeedMultiplier;
        final ModConfigSpec.BooleanValue preventMobDespawn;
        final ModConfigSpec.BooleanValue forceDespawnAfterEvent;
        final ModConfigSpec.IntValue forceDespawnDelaySeconds;
        final ModConfigSpec.ConfigValue<List<? extends String>> mobEffects;
        final ModConfigSpec.LongValue nightLengthTicks;
        final ModConfigSpec.ConfigValue<String> moonColor;
        final ModConfigSpec.ConfigValue<String> skyColor;
        final ModConfigSpec.ConfigValue<String> moonTexture;
        final ModConfigSpec.DoubleValue moonSizeMultiplier;
        final ModConfigSpec.ConfigValue<String> startMessage;
        final ModConfigSpec.ConfigValue<String> endMessage;
        final ModConfigSpec.BooleanValue enableCropDropBoost;
        final ModConfigSpec.ConfigValue<List<? extends String>> cropDropTags;

        EventSpec(ModConfigSpec.Builder b, ECTweaksConfig.EventDefaults defaults, String eventName) {
            chanceMultiplier = b.comment("Multiplier on the event's spawn chance. 0 disables it.")
                    .defineInRange("chance_multiplier", 1.0, 0.0, 1000.0);
            minNightsBetween = b.comment("Minimum nights between this event. -1 keeps default.")
                    .defineInRange("min_nights_between", -1, -1, Integer.MAX_VALUE);
            validMoonPhases = b.comment("Moon phases this event can occur on. 0 = full moon, 4 = new moon. Example: [0] or [0, 4]. Empty keeps default.")
                    .defineListAllowEmpty("valid_moon_phases", List.of(), ECTweaksConfig::isMoonPhase);
            mobSpawnMultiplier = b.comment("Global multiplier applied to every mob category's spawn rate during this event, including categories set by mob_category_multipliers.")
                    .defineInRange("mob_spawn_multiplier", 1.0, 0.0, 1000.0);
            mobCapMultiplier = b.comment("Multiplier on the mob-spawn cap during this event. 1.0 = vanilla. -1 = no cap, mobs keep spawning.")
                    .defineInRange("mob_cap_multiplier", 1.0, -1.0, 10000.0);
            blockSleeping = b.comment("Whether this event blocks sleeping.")
                    .defineEnum("block_sleeping", BoolOverride.DEFAULT);
            useBiomeSpawnSettings = b.comment("Use the biome's normal spawn list during this event.")
                    .defineEnum("use_biome_spawn_settings", BoolOverride.DEFAULT);
            forceSurfaceSpawning = b.comment("Force mobs to spawn on the surface during this event.")
                    .defineEnum("force_surface_spawning", BoolOverride.DEFAULT);
            slimesSpawnEverywhere = b.comment("Allow slimes to spawn anywhere during this event.")
                    .defineEnum("slimes_spawn_everywhere", BoolOverride.DEFAULT);
            weatherOverride = b.comment("Force weather while this event is active. DEFAULT keeps vanilla weather.")
                    .defineEnum("weather_override", WeatherOverride.DEFAULT);
            restoreWeatherAfterEvent = b.comment("Allow vanilla weather to return after the event ends.")
                    .define("restore_weather_after_event", true);
            mobCategoryMultipliers = b.comment("Per-category spawn multipliers. Format: \"CATEGORY:value\"")
                    .defineListAllowEmpty("mob_category_multipliers", defaults.mobCategoryMultipliers(), o -> o instanceof String s && s.split(":").length == 2);
            spawnAdditions = b.comment("Mobs to add during this event. Format: \"target;weight;min;max\". target = entity id, #entity_tag, or @modid (all of a mod's mobs).")
                    .defineListAllowEmpty("spawn_additions", List.of(), o -> o instanceof String s && s.split(";").length == 4);
            spawnRemovals = b.comment("Entities blocked from spawning during this event. Each entry = entity id, #entity_tag, or @modid.")
                    .defineListAllowEmpty("spawn_removals", List.of(), o -> o instanceof String);
            addedDimensions = b.comment("Dimensions to add this event to. Format: \"namespace:dimension_id\". Uses overworld settings as template.")
                    .defineListAllowEmpty("added_dimensions", List.of(), o -> o instanceof String);
            monsterSpawnLightLevel = b.comment("Maximum block light level monsters can spawn at during this event. 0 = vanilla.")
                    .defineInRange("monster_spawn_light_level", 0, 0, 15);
            mobGearChanceMultiplier = b.comment("Multiplier on the vanilla chance for monsters to spawn with gear (armor + weapons).")
                    .defineInRange("mob_gear_chance_multiplier", 1.0, 0.0, 1000.0);
            mobDropsEventGear = b.comment("Whether mobs drop the gear given to them by this event.")
                    .define("mob_drops_event_gear", false);
            rareDropMultiplier = b.comment("Chance multiplier for non-common drops (uncommon, rare, epic) during this event.")
                    .defineInRange("rare_drop_multiplier", 1.0, 1.0, 100.0);
            rareDropOnlyEventMobs = b.comment("Only mobs spawned by the event are affected by rare_drop_multiplier.")
                    .define("rare_drop_only_event_mobs", true);
            xpDropMultiplier = b.comment("Multiplier on experience dropped by mobs killed during this event.")
                    .defineInRange("xp_drop_multiplier", 1.0, 0.0, 1000.0);
            xpOnlyEventMobs = b.comment("Only mobs spawned by the event are affected by xp_drop_multiplier.")
                    .define("xp_only_event_mobs", true);
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
            mobEffects = b.comment("Status effects applied while the event is active. Format: \"effect_id;amplifier;duration[;target]\"")
                    .defineListAllowEmpty("mob_effects", defaults.mobEffects(), o -> o instanceof String s && s.split(";").length >= 3);
            nightLengthTicks = b.comment("How many ticks the night lasts during this event (vanilla = 12000).")
                    .defineInRange("night_length_ticks", 12000L, 1L, Long.MAX_VALUE);
            moonColor = b.comment("Moon texture hex color. Empty = white.")
                    .define("moon_color", defaults.moonColor());
            skyColor = b.comment("Sky/light hex color during the event. Empty = white.")
                    .define("sky_color", defaults.skyColor());
            moonTexture = b.comment("Moon texture override (ResourceLocation). Empty = vanilla phases.")
                    .define("moon_texture", "");
            moonSizeMultiplier = b.comment("Multiplier on the moon's rendered size during this event. 1.0 = default.")
                    .defineInRange("moon_size_multiplier", 1.0, 0.01, 100.0);
            startMessage = b.comment("Message shown when the event starts. Supports & color codes. Empty = no message.")
                    .define("start_message", defaults.startMessage());
            endMessage = b.comment("Message shown when the event ends. Supports & color codes. Empty = no message.")
                    .define("end_message", defaults.endMessage());

            boolean isHarvest = eventName.equals("harvest_moon") || eventName.equals("super_harvest_moon");
            if (isHarvest) {
                enableCropDropBoost = b.comment("Multiply drops for items in the configured tags while this event is active.")
                        .define("enable_crop_drop_boost", defaults.enableCropDropBoost());
                cropDropTags = b.comment("Item tags to multiply drops for. Format: \"tag_id;multiplier\".")
                        .defineListAllowEmpty("crop_drop_tags", defaults.cropDropTags(), o -> o instanceof String s && s.split(";").length == 2);
            } else {
                enableCropDropBoost = null;
                cropDropTags = null;
            }
        }
    }

    static final class ClientGeneralSpec {
        final ModConfigSpec.ConfigValue<String> sleepPreventedMessage;

        ClientGeneralSpec(ModConfigSpec.Builder b) {
            sleepPreventedMessage = b.comment(
                    "Text shown above the hotbar when sleep is blocked by a lunar event. Supports & color codes.",
                    "Client-only: overrides the message on your own client. On a server each player sets their own value, and players without this mod see Enhanced Celestials' default text.",
                    "Enhanced Celestials forces the base color to red, so a leading color code may have no effect; use & codes mid-text for per-segment coloring.")
                    .define("sleep_prevented_message", "You may not rest now because the current lunar event prevents it.");
        }
    }

    static final class ClientEventSpec {
        final ModConfigSpec.ConfigValue<String> fogColor;
        final ModConfigSpec.DoubleValue fogDensityMultiplier;
        final ModConfigSpec.ConfigValue<String> soundtrack;
        final ModConfigSpec.DoubleValue soundtrackVolume;
        final ModConfigSpec.DoubleValue soundtrackPitch;
        final ModConfigSpec.BooleanValue soundtrackLoop;

        ClientEventSpec(ModConfigSpec.Builder b, ECTweaksConfig.EventDefaults defaults) {
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

    private NeoForgeConfig() {
    }
}
