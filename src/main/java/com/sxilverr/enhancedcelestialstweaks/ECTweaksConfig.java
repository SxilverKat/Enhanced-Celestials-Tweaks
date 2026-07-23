package com.sxilverr.enhancedcelestialstweaks;

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

    public static final General GENERAL = new General();
    public static final Dimension OVERWORLD = new Dimension();
    public static final Map<String, EventTweaks> EVENTS = new LinkedHashMap<>();
    static {
        for (String key : EVENT_DEFAULTS.keySet()) {
            EVENTS.put(key, new EventTweaks(key));
        }
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
        public boolean enabled = true;
        public boolean logTweaks = false;
        public boolean eventsVisualOnly = false;
        public boolean recomputeForecastOnStart = true;
    }

    public static final class Dimension {
        public BoolOverride requireClearSkies = BoolOverride.DEFAULT;
        public long minDaysBetweenEvents = -1L;
        public long maxDaysBetweenEvents = -1L;
        public long dayLength = -1L;
        public long yearLengthInDays = -1L;
    }

    public static final class EventTweaks {
        public double chanceMultiplier = 1.0;
        public int minNightsBetween = -1;
        public List<String> validMoonPhases = List.of();
        public double mobSpawnMultiplier = 1.0;
        public double mobCapMultiplier = 1.0;
        public BoolOverride blockSleeping = BoolOverride.DEFAULT;
        public BoolOverride useBiomeSpawnSettings = BoolOverride.DEFAULT;
        public BoolOverride forceSurfaceSpawning = BoolOverride.DEFAULT;
        public BoolOverride slimesSpawnEverywhere = BoolOverride.DEFAULT;
        public WeatherOverride weatherOverride = WeatherOverride.DEFAULT;
        public boolean restoreWeatherAfterEvent = true;
        public List<String> mobCategoryMultipliers;
        public List<String> spawnAdditions = List.of();
        public List<String> spawnRemovals = List.of();
        public List<String> addedDimensions = List.of();
        public int monsterSpawnLightLevel = 0;
        public double mobGearChanceMultiplier = 1.0;
        public boolean mobDropsEventGear = false;
        public double rareDropMultiplier = 1.0;
        public boolean rareDropOnlyEventMobs = true;
        public double xpDropMultiplier = 1.0;
        public boolean xpOnlyEventMobs = true;
        public double mobHealthMultiplier = 1.0;
        public double mobDamageMultiplier = 1.0;
        public double mobSpeedMultiplier = 1.0;
        public double mobSwimSpeedMultiplier = 1.0;
        public boolean preventMobDespawn = false;
        public boolean forceDespawnAfterEvent = false;
        public int forceDespawnDelaySeconds = 180;
        public List<String> mobEffects;
        public long nightLengthTicks = 12000L;
        public String moonColor;
        public String skyColor;
        public String moonTexture = "";
        public double moonSizeMultiplier = 1.0;
        public String startMessage;
        public String endMessage;
        public final boolean isHarvest;
        public Boolean enableCropDropBoost;
        public List<String> cropDropTags;

        public EventTweaks(String eventName) {
            this.isHarvest = eventName.equals("harvest_moon") || eventName.equals("super_harvest_moon");
            EventDefaults d = EVENT_DEFAULTS.get(eventName);
            this.mobCategoryMultipliers = d.mobCategoryMultipliers();
            this.mobEffects = d.mobEffects();
            this.moonColor = d.moonColor();
            this.skyColor = d.skyColor();
            this.startMessage = d.startMessage();
            this.endMessage = d.endMessage();
            if (isHarvest) {
                this.enableCropDropBoost = d.enableCropDropBoost();
                this.cropDropTags = d.cropDropTags();
            } else {
                this.enableCropDropBoost = null;
                this.cropDropTags = null;
            }
        }
    }

    private ECTweaksConfig() {
    }
}
