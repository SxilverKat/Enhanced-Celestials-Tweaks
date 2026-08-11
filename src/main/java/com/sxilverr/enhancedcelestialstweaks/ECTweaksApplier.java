package com.sxilverr.enhancedcelestialstweaks;

import com.sxilverr.enhancedcelestialstweaks.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.storage.ServerLevelData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ECTweaksApplier {

    private static final String EC_MAIN_CLASS = "dev.corgitaco.enhancedcelestials.EnhancedCelestials";
    private static final String EC_REGISTRY_CLASS = "dev.corgitaco.enhancedcelestials.api.EnhancedCelestialsRegistry";
    private static final String CTC_CLASS = "dev.corgitaco.enhancedcelestials.util.CustomTranslationTextComponent";
    private static final String NOTIFICATION_CLASS = "dev.corgitaco.enhancedcelestials.api.lunarevent.LunarTextComponents$Notification";
    private static final String NOTIFICATION_TYPE_CLASS = "dev.corgitaco.enhancedcelestials.api.lunarevent.LunarTextComponents$NotificationType";
    private static final String ANY_CONDITION_CLASS = "corgitaco.corgilib.entity.condition.AnyCondition";
    private static final String FLIP_CONDITION_CLASS = "corgitaco.corgilib.entity.condition.FlipCondition";
    private static final String CONDITION_CLASS = "corgitaco.corgilib.entity.condition.Condition";

    private static final String NBT_TAG_EVENT_MOB = "ec_tweaks_event_mob";
    private static final String NBT_TAG_EVENT_ID = "ec_tweaks_event_id";

    private static final Map<String, Set<EntityType<?>>> REMOVALS_BY_EVENT = new HashMap<>();
    private static final Map<ResourceKey<Level>, String> LAST_KNOWN_EVENT = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<String, Long>> EVENT_END_TIMES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, WeatherSnapshot> WEATHER_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Set<ResourceKey<Level>> OVERWORLD_LINKED_DIMS = ConcurrentHashMap.newKeySet();
    private static volatile Object overworldDimSettings;
    private static volatile Method LUNAR_FORECAST_METHOD;
    private static volatile Method CURRENT_EVENT_HOLDER_METHOD;
    private static volatile boolean reflectionFailed = false;
    private static volatile boolean loggedLunarReadError = false;
    private static volatile boolean anyForceDespawn = false;

    public static void serverAboutToStart(MinecraftServer server) {
        REMOVALS_BY_EVENT.clear();
        LAST_KNOWN_EVENT.clear();
        EVENT_END_TIMES.clear();
        WEATHER_SNAPSHOTS.clear();
        overworldDimSettings = null;
        reflectionFailed = false;
        loggedLunarReadError = false;
        recomputeRuntimeFlags();
        if (!ECTweaksConfig.GENERAL.enabled) return;
        if (!Services.PLATFORM.isModLoaded(Constants.EC_MOD_ID)) {
            Constants.LOGGER.info("Enhanced Celestials not loaded, skipping tweaks.");
            return;
        }
        try {
            apply(server.registryAccess());
        } catch (Throwable t) {
            Constants.LOGGER.error("Failed to apply Enhanced Celestials tweaks", t);
        }
    }

    public static void serverStarting(MinecraftServer server) {
        if (!ECTweaksConfig.GENERAL.enabled) return;
        if (!ECTweaksConfig.GENERAL.recomputeForecastOnStart) return;
        if (!Services.PLATFORM.isModLoaded(Constants.EC_MOD_ID)) return;
        boolean log = ECTweaksConfig.GENERAL.logTweaks;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                recomputeForecast(level, log);
            } catch (Throwable t) {
                Constants.LOGGER.error("Failed to recompute forecast for {}", level.dimension().location(), t);
            }
        }
    }

    public static void serverStopped() {
        REMOVALS_BY_EVENT.clear();
        LAST_KNOWN_EVENT.clear();
        EVENT_END_TIMES.clear();
        WEATHER_SNAPSHOTS.clear();
        overworldDimSettings = null;
    }

    public static void recomputeRuntimeFlags() {
        boolean any = false;
        Set<ResourceKey<Level>> linked = new HashSet<>();
        linked.add(Level.OVERWORLD);
        for (ECTweaksConfig.EventTweaks t : ECTweaksConfig.EVENTS.values()) {
            if (t.forceDespawnAfterEvent) {
                any = true;
            }
            for (String dimId : t.addedDimensions) {
                ResourceLocation loc = ResourceLocation.tryParse(dimId.trim());
                if (loc != null) linked.add(ResourceKey.create(Registries.DIMENSION, loc));
            }
        }
        anyForceDespawn = any;
        OVERWORLD_LINKED_DIMS.clear();
        OVERWORLD_LINKED_DIMS.addAll(linked);
    }

    private static boolean gameplayTweaksDisabled() {
        return !ECTweaksConfig.GENERAL.enabled || ECTweaksConfig.GENERAL.eventsVisualOnly;
    }

    public static boolean finalizeSpawn(Mob mob, DifficultyInstance difficulty) {
        Level level = mob.level();
        if (level.isClientSide) return false;
        if (gameplayTweaksDisabled()) return false;

        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return false;

        Set<EntityType<?>> removals = REMOVALS_BY_EVENT.get(currentEvent);
        if (removals != null && removals.contains(mob.getType())) {
            return true;
        }

        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return false;

        Services.PLATFORM.applyMultiplierAttributes(mob, tweaks.mobHealthMultiplier, tweaks.mobDamageMultiplier, tweaks.mobSpeedMultiplier, tweaks.mobSwimSpeedMultiplier);

        if (tweaks.mobHealthMultiplier != 1.0) {
            mob.setHealth(mob.getMaxHealth());
        }

        double gearMul = tweaks.mobGearChanceMultiplier;
        if (gearMul > 1.0 && mob instanceof Monster) {
            boostGear(mob, level.random, difficulty, gearMul, tweaks.mobDropsEventGear);
        }

        CompoundTag data = Services.PLATFORM.getPersistentData(mob);
        data.putBoolean(NBT_TAG_EVENT_MOB, true);
        data.putString(NBT_TAG_EVENT_ID, currentEvent);
        if (tweaks.preventMobDespawn || tweaks.forceDespawnAfterEvent) {
            mob.setPersistenceRequired();
        }
        return false;
    }

    private static boolean isEventSpawnedMob(LivingEntity entity, String currentEvent) {
        CompoundTag data = Services.PLATFORM.getPersistentData(entity);
        return data.getBoolean(NBT_TAG_EVENT_MOB) && currentEvent.equals(data.getString(NBT_TAG_EVENT_ID));
    }

    public static boolean allowMonsterSpawnAtLight(ServerLevelAccessor levelAccessor, BlockPos pos) {
        if (gameplayTweaksDisabled()) return false;
        ServerLevel level = levelAccessor.getLevel();
        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return false;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return false;
        int maxLight = tweaks.monsterSpawnLightLevel;
        if (maxLight <= 0) return false;
        return level.getMaxLocalRawBrightness(pos) <= maxLight;
    }

    public static boolean shouldPreventDespawn(LivingEntity entity) {
        if (gameplayTweaksDisabled()) return false;
        CompoundTag data = Services.PLATFORM.getPersistentData(entity);
        if (!data.getBoolean(NBT_TAG_EVENT_MOB)) return false;
        String mobEventId = data.getString(NBT_TAG_EVENT_ID);
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(mobEventId);
        return tweaks != null && tweaks.preventMobDespawn;
    }

    public static void livingTick(LivingEntity entity) {
        if (gameplayTweaksDisabled()) return;
        if (entity.tickCount % 20 != 5) return;
        Level level = entity.level();
        if (level.isClientSide) return;

        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        List<? extends String> effects = tweaks.mobEffects;
        if (effects.isEmpty()) return;

        boolean isPlayer = entity instanceof Player;
        boolean isMonster = entity.getType().getCategory() == MobCategory.MONSTER;

        for (String effectStr : effects) {
            String[] parts = effectStr.split(";");
            if (parts.length < 3) continue;
            ResourceLocation effectId = ResourceLocation.tryParse(parts[0].trim());
            if (effectId == null) continue;
            try {
                int amp = Integer.parseInt(parts[1].trim());
                int dur = Integer.parseInt(parts[2].trim());
                String target = parts.length >= 4 ? parts[3].trim().toLowerCase(Locale.ROOT) : "players";
                boolean apply = switch (target) {
                    case "players" -> isPlayer;
                    case "mobs" -> !isPlayer;
                    case "monsters" -> isMonster;
                    default -> true;
                };
                if (apply) {
                    Services.PLATFORM.applyMobEffect(entity, effectId, amp, dur);
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    public static void livingDrops(LivingEntity entity, Collection<ItemEntity> drops) {
        if (gameplayTweaksDisabled()) return;
        Level level = entity.level();
        if (level.isClientSide) return;

        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        double mul = tweaks.rareDropMultiplier;
        if (mul <= 1.0) return;
        if (tweaks.rareDropOnlyEventMobs && !isEventSpawnedMob(entity, currentEvent)) return;

        RandomSource rand = level.random;
        List<ItemEntity> additional = new ArrayList<>();
        for (ItemEntity item : drops) {
            ItemStack stack = item.getItem();
            if (stack.isEmpty() || stack.getRarity() == Rarity.COMMON) continue;
            if (!stack.isStackable() || stack.getMaxStackSize() <= 1) continue;

            int whole = (int) Math.floor(mul) - 1;
            double frac = mul - Math.floor(mul);
            int extraUnits = whole * stack.getCount();
            if (rand.nextDouble() < frac) extraUnits += stack.getCount();
            if (extraUnits <= 0) continue;

            int max = stack.getMaxStackSize();
            int toExisting = Math.min(max - stack.getCount(), extraUnits);
            if (toExisting > 0) stack.grow(toExisting);
            int remaining = extraUnits - toExisting;
            while (remaining > 0) {
                int n = Math.min(max, remaining);
                ItemStack extra = stack.copy();
                extra.setCount(n);
                additional.add(new ItemEntity(level, item.getX(), item.getY(), item.getZ(), extra));
                remaining -= n;
            }
        }
        drops.addAll(additional);
    }

    public static int modifyXpDrop(LivingEntity entity, int base) {
        if (gameplayTweaksDisabled()) return base;
        Level level = entity.level();
        if (level.isClientSide) return base;

        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return base;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return base;
        double mul = tweaks.xpDropMultiplier;
        if (mul == 1.0) return base;
        if (tweaks.xpOnlyEventMobs && !isEventSpawnedMob(entity, currentEvent)) return base;
        if (base <= 0) return base;
        return (int) Math.round(base * mul);
    }

    public static void levelTick(ServerLevel sLevel) {
        if (gameplayTweaksDisabled()) return;

        String currentEvent = getCurrentLunarEventPath(sLevel);
        ResourceKey<Level> dimKey = sLevel.dimension();
        String cur = currentEvent == null ? "" : currentEvent;
        String last = LAST_KNOWN_EVENT.get(dimKey);
        if (last == null) last = "";
        if (!last.equals(cur)) {
            if (!last.isEmpty()) {
                EVENT_END_TIMES.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>()).put(last, sLevel.getGameTime());
                onWeatherEventEnd(sLevel, dimKey, last);
            }
            if (!cur.isEmpty()) {
                onWeatherEventStart(sLevel, dimKey, cur);
            }
        }
        LAST_KNOWN_EVENT.put(dimKey, cur);

        adjustNightLength(sLevel, currentEvent);
        applyWeather(sLevel, currentEvent);

        if (anyForceDespawn && sLevel.getGameTime() % 100 == 0) {
            forcedDespawnPass(sLevel, currentEvent);
        }
    }

    private static void applyWeather(ServerLevel sLevel, String currentEvent) {
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        WeatherOverride weather = tweaks.weatherOverride;
        if (weather == WeatherOverride.DEFAULT) return;
        if (!sLevel.dimensionType().hasSkyLight() || sLevel.dimensionType().hasCeiling()) return;

        long dayTime = sLevel.getDayTime() % 24000L;
        boolean nightWindow = dayTime >= 13000L && dayTime < 23000L;
        boolean rain = nightWindow && (weather == WeatherOverride.RAIN || weather == WeatherOverride.THUNDER);
        boolean thunder = nightWindow && weather == WeatherOverride.THUNDER;
        if (sLevel.isRaining() == rain && sLevel.isThundering() == thunder) return;
        sLevel.setWeatherParameters(rain ? 0 : 6000, rain ? 6000 : 0, rain, thunder);
    }

    private static volatile double spawnContextCapMultiplier = 1.0;

    public static void beginSpawnContext(ServerLevel level) {
        spawnContextCapMultiplier = computeMobCapMultiplier(level);
    }

    public static void endSpawnContext() {
        spawnContextCapMultiplier = 1.0;
    }

    public static double contextMobCapMultiplier() {
        return spawnContextCapMultiplier;
    }

    private static double computeMobCapMultiplier(ServerLevel level) {
        if (gameplayTweaksDisabled()) return 1.0;
        String currentEvent = LAST_KNOWN_EVENT.get(level.dimension());
        if (currentEvent == null || currentEvent.isEmpty()) return 1.0;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return 1.0;
        return tweaks.mobCapMultiplier;
    }

    private static void captureOverworldDimSettings(RegistryAccess registryAccess, ResourceKey<Registry<Object>> lunarDimSettingsKey) {
        try {
            Optional<Registry<Object>> reg = registryAccess.registry(lunarDimSettingsKey);
            if (reg.isEmpty()) return;
            ResourceLocation loc = ResourceLocation.tryParse("minecraft:overworld");
            if (loc == null) return;
            overworldDimSettings = reg.get().get(ResourceKey.create(lunarDimSettingsKey, loc));
        } catch (Throwable t) {
            overworldDimSettings = null;
        }
    }

    public static boolean isOverworldLunarDimensionSettings(Object settings) {
        Object captured = overworldDimSettings;
        return captured == null || captured == settings;
    }

    public static boolean activeEventForcesPrecipitation() {
        for (Map.Entry<ResourceKey<Level>, String> e : LAST_KNOWN_EVENT.entrySet()) {
            if (!OVERWORLD_LINKED_DIMS.contains(e.getKey())) continue;
            String eventPath = e.getValue();
            if (eventPath == null || eventPath.isEmpty()) continue;
            ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(eventPath);
            if (tweaks == null) continue;
            WeatherOverride weather = tweaks.weatherOverride;
            if (weather == WeatherOverride.RAIN || weather == WeatherOverride.THUNDER) return true;
        }
        return false;
    }

    private static void onWeatherEventStart(ServerLevel sLevel, ResourceKey<Level> dimKey, String eventPath) {
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(eventPath);
        if (tweaks == null || tweaks.weatherOverride == WeatherOverride.DEFAULT) return;
        if (!sLevel.dimensionType().hasSkyLight() || sLevel.dimensionType().hasCeiling()) return;
        if (sLevel.getLevelData() instanceof ServerLevelData data) {
            WEATHER_SNAPSHOTS.put(dimKey, new WeatherSnapshot(
                    data.isRaining(), data.getRainTime(),
                    data.isThundering(), data.getThunderTime(),
                    data.getClearWeatherTime()));
        }
    }

    private static void onWeatherEventEnd(ServerLevel sLevel, ResourceKey<Level> dimKey, String eventPath) {
        WeatherSnapshot snap = WEATHER_SNAPSHOTS.remove(dimKey);
        if (snap == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(eventPath);
        if (tweaks == null || !tweaks.restoreWeatherAfterEvent) return;
        if (sLevel.getLevelData() instanceof ServerLevelData data) {
            data.setClearWeatherTime(snap.clearTime());
            data.setRaining(snap.raining());
            data.setRainTime(snap.rainTime());
            data.setThundering(snap.thundering());
            data.setThunderTime(snap.thunderTime());
        }
    }

    private record WeatherSnapshot(boolean raining, int rainTime, boolean thundering, int thunderTime, int clearTime) {}

    private static void adjustNightLength(ServerLevel sLevel, String currentEvent) {
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        long nightLen = tweaks.nightLengthTicks;
        if (nightLen == 12000L) return;

        long dayTime = sLevel.getDayTime() % 24000L;
        if (dayTime < 13000L || dayTime >= 23000L) return;

        double slowdown = nightLen / 12000.0;
        if (slowdown > 1.0) {
            if (sLevel.random.nextDouble() < (1.0 - 1.0 / slowdown)) {
                sLevel.setDayTime(sLevel.getDayTime() - 1);
            }
        } else if (slowdown < 1.0) {
            double need = (1.0 / slowdown) - 1.0;
            long whole = (long) need;
            double frac = need - whole;
            long extra = whole + (sLevel.random.nextDouble() < frac ? 1 : 0);
            if (extra > 0) sLevel.setDayTime(sLevel.getDayTime() + extra);
        }
    }

    private static void forcedDespawnPass(ServerLevel sLevel, String currentEvent) {
        Map<String, Long> endTimes = EVENT_END_TIMES.get(sLevel.dimension());
        if (endTimes == null) return;
        long now = sLevel.getGameTime();
        for (Entity entity : sLevel.getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            CompoundTag data = Services.PLATFORM.getPersistentData(mob);
            if (!data.getBoolean(NBT_TAG_EVENT_MOB)) continue;
            String mobEventId = data.getString(NBT_TAG_EVENT_ID);
            if (mobEventId.equals(currentEvent)) continue;
            ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(mobEventId);
            if (tweaks == null || !tweaks.forceDespawnAfterEvent) continue;
            Long endTime = endTimes.get(mobEventId);
            if (endTime == null) continue;
            long delay = (long) tweaks.forceDespawnDelaySeconds * 20L;
            if (now - endTime >= delay) {
                mob.discard();
            }
        }
    }

    private static void boostGear(Mob mob, RandomSource random, DifficultyInstance difficulty, double chanceMul, boolean dropGear) {
        java.util.EnumMap<EquipmentSlot, ItemStack> before = new java.util.EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            before.put(slot, mob.getItemBySlot(slot).copy());
        }

        int extraRolls = (int) Math.floor(chanceMul - 1.0);
        boolean populated = true;
        for (int i = 0; i < extraRolls && populated; i++) {
            populated = Services.PLATFORM.populateEquipment(mob, random, difficulty);
        }
        double frac = (chanceMul - 1.0) - extraRolls;
        if (populated && frac > 0 && random.nextDouble() < frac) {
            Services.PLATFORM.populateEquipment(mob, random, difficulty);
        }

        if (!dropGear) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack now = mob.getItemBySlot(slot);
                ItemStack was = before.get(slot);
                if (!ItemStack.matches(now, was)) {
                    mob.setDropChance(slot, 0.0f);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void apply(RegistryAccess registryAccess) throws Exception {
        Class<?> ecRegistryClass = Class.forName(EC_REGISTRY_CLASS);
        ResourceKey<Registry<Object>> lunarEventKey = (ResourceKey<Registry<Object>>) ecRegistryClass.getField("LUNAR_EVENT_KEY").get(null);
        ResourceKey<Registry<Object>> lunarDimSettingsKey = (ResourceKey<Registry<Object>>) ecRegistryClass.getField("LUNAR_DIMENSION_SETTINGS_KEY").get(null);

        boolean log = ECTweaksConfig.GENERAL.logTweaks;
        boolean visualOnly = ECTweaksConfig.GENERAL.eventsVisualOnly;

        captureOverworldDimSettings(registryAccess, lunarDimSettingsKey);

        try {
            addDimensions(registryAccess, lunarEventKey, lunarDimSettingsKey, log);
        } catch (Throwable t) {
            Constants.LOGGER.error("Failed to add dimensions", t);
        }

        Optional<Registry<Object>> lunarEventReg = registryAccess.registry(lunarEventKey);
        if (lunarEventReg.isEmpty()) {
            if (log) Constants.LOGGER.warn("Lunar event registry not found.");
            return;
        }

        for (Map.Entry<ResourceKey<Object>, Object> entry : lunarEventReg.get().entrySet()) {
            String path = entry.getKey().location().getPath();
            ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(path);
            try {
                applyEventTweaks(entry.getValue(), tweaks, visualOnly, path, log);
                if (tweaks != null && !visualOnly) {
                    cacheRemovals(path, tweaks, log);
                }
            } catch (Throwable t) {
                Constants.LOGGER.error("Failed to apply tweaks for {}", path, t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addDimensions(RegistryAccess registryAccess, ResourceKey<Registry<Object>> lunarEventKey, ResourceKey<Registry<Object>> lunarDimSettingsKey, boolean log) throws Exception {
        Map<String, List<String>> dimsByEvent = new HashMap<>();
        Set<String> allDims = new HashSet<>();
        for (Map.Entry<String, ECTweaksConfig.EventTweaks> entry : ECTweaksConfig.EVENTS.entrySet()) {
            List<? extends String> eventDims = entry.getValue().addedDimensions;
            if (eventDims.isEmpty()) continue;
            List<String> list = new ArrayList<>();
            for (String s : eventDims) list.add(s);
            dimsByEvent.put(entry.getKey(), list);
            allDims.addAll(list);
        }
        if (allDims.isEmpty()) return;

        Optional<Registry<Object>> dimRegOpt = registryAccess.registry(lunarDimSettingsKey);
        if (dimRegOpt.isEmpty()) return;
        Registry<Object> dimRegistry = dimRegOpt.get();

        ResourceLocation overworldLoc = ResourceLocation.tryParse("minecraft:overworld");
        ResourceKey<Object> overworldDimKey = ResourceKey.create(lunarDimSettingsKey, overworldLoc);
        Object overworldSettings = dimRegistry.get(overworldDimKey);
        if (overworldSettings == null) {
            if (log) Constants.LOGGER.warn("No overworld lunar dimension settings; cannot add dimensions");
            return;
        }

        if (dimRegistry instanceof MappedRegistry<Object> mappedReg) {
            mappedReg.getClass().getMethod("unfreeze").invoke(mappedReg);
            try {
                for (String dimId : allDims) {
                    ResourceLocation loc = ResourceLocation.tryParse(dimId.trim());
                    if (loc == null) continue;
                    ResourceKey<Object> dimKey = ResourceKey.create(lunarDimSettingsKey, loc);
                    if (mappedReg.containsKey(dimKey)) continue;
                    //? if >=1.21.1 {
                    /*mappedReg.register(dimKey, overworldSettings, net.minecraft.core.RegistrationInfo.BUILT_IN);
                    *///?} else {
                    mappedReg.register(dimKey, overworldSettings, com.mojang.serialization.Lifecycle.stable());
                    //?}
                    if (log) Constants.LOGGER.info("Registered lunar dimension settings for {}", loc);
                }
            } finally {
                mappedReg.freeze();
            }
        } else {
            if (log) Constants.LOGGER.warn("Lunar dimension registry is not a MappedRegistry; skipping dimension addition");
            return;
        }

        Optional<Registry<Object>> eventRegOpt = registryAccess.registry(lunarEventKey);
        if (eventRegOpt.isEmpty()) return;

        for (Map.Entry<ResourceKey<Object>, Object> entry : eventRegOpt.get().entrySet()) {
            String eventPath = entry.getKey().location().getPath();
            List<String> configuredDims = dimsByEvent.get(eventPath);
            if (configuredDims == null) continue;

            Object lunarEvent = entry.getValue();
            Class<?> lunarEventClass = lunarEvent.getClass();
            Field eventChancesField = lunarEventClass.getDeclaredField("eventChancesByDimension");
            eventChancesField.setAccessible(true);
            Map<Object, Object> oldChances = (Map<Object, Object>) eventChancesField.get(lunarEvent);

            Object overworldEntry = null;
            for (Map.Entry<Object, Object> e : oldChances.entrySet()) {
                ResourceKey<?> key = (ResourceKey<?>) e.getKey();
                if (key.location().equals(overworldLoc)) {
                    overworldEntry = e.getValue();
                    break;
                }
            }
            if (overworldEntry == null) continue;

            Map<Object, Object> newChances = new LinkedHashMap<>(oldChances);
            for (String dimId : configuredDims) {
                ResourceLocation loc = ResourceLocation.tryParse(dimId.trim());
                if (loc == null) continue;
                ResourceKey<Level> dimLevelKey = ResourceKey.create(Registries.DIMENSION, loc);
                if (newChances.containsKey(dimLevelKey)) continue;
                newChances.put(dimLevelKey, overworldEntry);
                if (log) Constants.LOGGER.info("Added event {} to dimension {}", eventPath, loc);
            }
            eventChancesField.set(lunarEvent, newChances);
        }
    }

    private static void applyEventTweaks(Object lunarEvent, ECTweaksConfig.EventTweaks tweaks, boolean visualOnly, String path, boolean log) throws Exception {
        Class<?> lunarEventClass = lunarEvent.getClass();

        if (visualOnly) {
            replaceField(lunarEventClass, lunarEvent, "lunarMobSettings", buildNeutralMobSettings(getField(lunarEventClass, lunarEvent, "lunarMobSettings")));
            replaceField(lunarEventClass, lunarEvent, "dropSettings", constructRecord(getField(lunarEventClass, lunarEvent, "dropSettings").getClass(), 1, new HashMap<>()));
            if (log) Constants.LOGGER.info("Neutralized {} (visual only)", path);
            return;
        }

        if (tweaks == null) return;

        Object oldMobSettings = getField(lunarEventClass, lunarEvent, "lunarMobSettings");
        Object newMobSettings = buildNewMobSettings(oldMobSettings, tweaks, path);
        replaceField(lunarEventClass, lunarEvent, "lunarMobSettings", newMobSettings);

        Object oldClientSettings = getField(lunarEventClass, lunarEvent, "clientSettings");
        Object newClientSettings = buildNewClientSettings(oldClientSettings, tweaks);
        replaceField(lunarEventClass, lunarEvent, "clientSettings", newClientSettings);

        Object oldTextComponents = getField(lunarEventClass, lunarEvent, "textComponents");
        Object newTextComponents = buildNewTextComponents(oldTextComponents, tweaks);
        replaceField(lunarEventClass, lunarEvent, "textComponents", newTextComponents);

        Object oldDropSettings = getField(lunarEventClass, lunarEvent, "dropSettings");
        Object newDropSettings = buildNewDropSettings(oldDropSettings, tweaks);
        replaceField(lunarEventClass, lunarEvent, "dropSettings", newDropSettings);

        if (log) Constants.LOGGER.info("Applied tweaks for {}", path);
    }

    @SuppressWarnings("unchecked")
    private static Object buildNewDropSettings(Object oldDropSettings, ECTweaksConfig.EventTweaks tweaks) throws Exception {
        if (tweaks.enableCropDropBoost == null) {
            return oldDropSettings;
        }
        Class<?> dsClass = oldDropSettings.getClass();
        Map<TagKey<Item>, Double> oldEnhancer = (Map<TagKey<Item>, Double>) getField(dsClass, oldDropSettings, "dropEnhancer");

        Map<TagKey<Item>, Double> newEnhancer = new HashMap<>(oldEnhancer);
        TagKey<Item> harvestTag = TagKey.create(Registries.ITEM, ResourceLocation.tryParse("enhancedcelestials:harvest_moon_crops"));
        newEnhancer.remove(harvestTag);
        for (String entry : tweaks.cropDropTags) {
            String[] parts = entry.split(";");
            if (parts.length != 2) continue;
            ResourceLocation loc = ResourceLocation.tryParse(parts[0].trim());
            if (loc == null) continue;
            newEnhancer.remove(TagKey.create(Registries.ITEM, loc));
        }

        if (tweaks.enableCropDropBoost) {
            for (String entry : tweaks.cropDropTags) {
                String[] parts = entry.split(";");
                if (parts.length != 2) continue;
                ResourceLocation loc = ResourceLocation.tryParse(parts[0].trim());
                if (loc == null) continue;
                try {
                    double mul = Double.parseDouble(parts[1].trim());
                    newEnhancer.put(TagKey.create(Registries.ITEM, loc), mul);
                } catch (NumberFormatException ex) {
                    Constants.LOGGER.warn("Bad crop drop multiplier in entry: {}", entry);
                }
            }
        }
        return constructRecord(dsClass, 1, newEnhancer);
    }

    @SuppressWarnings("unchecked")
    private static Object buildNewMobSettings(Object oldMobSettings, ECTweaksConfig.EventTweaks tweaks, String path) throws Exception {
        Class<?> msClass = oldMobSettings.getClass();
        Map<MobCategory, Double> oldSpawnCat = (Map<MobCategory, Double>) getField(msClass, oldMobSettings, "spawnCategoryMultiplier");
        Object oldSpawnInfo = getField(msClass, oldMobSettings, "lunarMobSpawnInfo");
        Object oldBlockSleep = getField(msClass, oldMobSettings, "blockSleeping");

        Map<MobCategory, Double> newSpawnCat = new LinkedHashMap<>();
        for (Map.Entry<MobCategory, Double> e : oldSpawnCat.entrySet()) {
            newSpawnCat.put(e.getKey(), e.getValue());
        }
        for (String entry : tweaks.mobCategoryMultipliers) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            try {
                MobCategory cat = MobCategory.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                double value = Double.parseDouble(parts[1].trim());
                newSpawnCat.put(cat, value);
            } catch (IllegalArgumentException ex) {
                Constants.LOGGER.warn("Bad category multiplier in {}: {}", path, entry);
            }
        }
        double mobMul = tweaks.mobSpawnMultiplier;
        if (mobMul != 1.0) {
            for (Map.Entry<MobCategory, Double> e : newSpawnCat.entrySet()) {
                e.setValue(e.getValue() * mobMul);
            }
        }

        Object newSpawnInfo = buildNewSpawnInfo(oldSpawnInfo, tweaks, path);

        Object newBlockSleep = oldBlockSleep;
        BoolOverride sleepOverride = tweaks.blockSleeping;
        if (sleepOverride == BoolOverride.TRUE) {
            newBlockSleep = Class.forName(ANY_CONDITION_CLASS).getField("INSTANCE").get(null);
        } else if (sleepOverride == BoolOverride.FALSE) {
            Object any = Class.forName(ANY_CONDITION_CLASS).getField("INSTANCE").get(null);
            newBlockSleep = Class.forName(FLIP_CONDITION_CLASS).getConstructor(Class.forName(CONDITION_CLASS)).newInstance(any);
        }

        return constructRecord(msClass, 4, newSpawnCat, newSpawnInfo, new ArrayList<>(), newBlockSleep);
    }

    private static Object buildNewSpawnInfo(Object oldSpawnInfo, ECTweaksConfig.EventTweaks tweaks, String path) throws Exception {
        Class<?> spawnInfoClass = oldSpawnInfo.getClass();

        BoolOverride useBiome = tweaks.useBiomeSpawnSettings;
        BoolOverride forceSurface = tweaks.forceSurfaceSpawning;
        BoolOverride slimes = tweaks.slimesSpawnEverywhere;
        List<? extends String> additions = tweaks.spawnAdditions;

        Field useBiomeF = field(spawnInfoClass, "useBiomeSpawnSettings");
        Field forceSurfaceF = field(spawnInfoClass, "forceSurfaceSpawning");
        Field slimesF = field(spawnInfoClass, "slimesSpawnEverywhere");
        Field innerSettingsF = field(spawnInfoClass, "spawnInfo");

        boolean useBiomeVal = useBiome != BoolOverride.DEFAULT ? (useBiome == BoolOverride.TRUE) : useBiomeF.getBoolean(oldSpawnInfo);
        boolean forceSurfaceVal = forceSurface != BoolOverride.DEFAULT ? (forceSurface == BoolOverride.TRUE) : forceSurfaceF.getBoolean(oldSpawnInfo);
        boolean slimesVal = slimes != BoolOverride.DEFAULT ? (slimes == BoolOverride.TRUE) : slimesF.getBoolean(oldSpawnInfo);
        Object innerSettings = innerSettingsF.get(oldSpawnInfo);

        if (!additions.isEmpty()) {
            MobSpawnSettings.Builder builder = new MobSpawnSettings.Builder();
            if (innerSettings instanceof MobSpawnSettings oldInner) {
                for (MobCategory cat : MobCategory.values()) {
                    for (MobSpawnSettings.SpawnerData data : oldInner.getMobs(cat).unwrap()) {
                        builder.addSpawn(cat, data);
                    }
                }
            }
            int count = 0;
            for (String entry : additions) {
                String[] parts = entry.split(";");
                if (parts.length != 4) continue;
                int weight;
                int min;
                int max;
                try {
                    weight = Integer.parseInt(parts[1].trim());
                    min = Integer.parseInt(parts[2].trim());
                    max = Integer.parseInt(parts[3].trim());
                } catch (NumberFormatException e) {
                    Constants.LOGGER.warn("Bad number in {} spawn_additions: {}", path, entry);
                    continue;
                }
                for (EntityType<?> type : resolveSpawnTargets(parts[0].trim(), path, "spawn_additions")) {
                    builder.addSpawn(type.getCategory(), new MobSpawnSettings.SpawnerData(type, weight, min, max));
                    count++;
                }
            }
            if (count > 0) innerSettings = builder.build();
        }
        return constructRecord(spawnInfoClass, 4, useBiomeVal, forceSurfaceVal, slimesVal, innerSettings);
    }

    private static List<EntityType<?>> resolveSpawnTargets(String spec, String path, String field) {
        List<EntityType<?>> types = new ArrayList<>();
        if (spec.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(spec.substring(1).trim());
            if (tagId != null) {
                Optional<? extends Iterable<Holder<EntityType<?>>>> tag = BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, tagId));
                if (tag.isPresent()) {
                    for (Holder<EntityType<?>> holder : tag.get()) {
                        types.add(holder.value());
                    }
                }
            }
            if (types.isEmpty()) Constants.LOGGER.warn("Empty or unknown entity tag in {} {}: {}", path, field, spec);
        } else if (spec.startsWith("@")) {
            String modId = spec.substring(1).trim();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (key != null && key.getNamespace().equals(modId) && type.getCategory() != MobCategory.MISC) {
                    types.add(type);
                }
            }
            if (types.isEmpty()) Constants.LOGGER.warn("No mobs found for mod in {} {}: {}", path, field, spec);
        } else {
            ResourceLocation id = ResourceLocation.tryParse(spec);
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) Constants.LOGGER.warn("Unknown entity in {} {}: {}", path, field, spec);
            else types.add(type);
        }
        return types;
    }

    private static Object buildNeutralMobSettings(Object oldMobSettings) throws Exception {
        Class<?> msClass = oldMobSettings.getClass();
        Object oldSpawnInfo = getField(msClass, oldMobSettings, "lunarMobSpawnInfo");
        Object newSpawnInfo = constructRecord(oldSpawnInfo.getClass(), 4, true, false, false, MobSpawnSettings.EMPTY);
        Object any = Class.forName(ANY_CONDITION_CLASS).getField("INSTANCE").get(null);
        Object never = Class.forName(FLIP_CONDITION_CLASS).getConstructor(Class.forName(CONDITION_CLASS)).newInstance(any);
        return constructRecord(msClass, 4, new LinkedHashMap<MobCategory, Double>(), newSpawnInfo, new ArrayList<>(), never);
    }

    private static Object buildNewClientSettings(Object oldClientSettings, ECTweaksConfig.EventTweaks tweaks) throws Exception {
        Class<?> clientSettingsClass = oldClientSettings.getClass();
        Object oldColorSettings = getField(clientSettingsClass, oldClientSettings, "colorSettings");
        float moonSize = field(clientSettingsClass, "moonSize").getFloat(oldClientSettings);
        float newMoonSize = (float) (moonSize * tweaks.moonSizeMultiplier);
        Object moonTexture = getField(clientSettingsClass, oldClientSettings, "moonTextureLocation");

        Class<?> csClass = oldColorSettings.getClass();
        int oldSky = field(csClass, "skyLightColor").getInt(oldColorSettings);
        int oldMoon = field(csClass, "moonTextureColor").getInt(oldColorSettings);

        String skyHex = tweaks.skyColor.trim();
        String moonHex = tweaks.moonColor.trim();
        int newSky = skyHex.isEmpty() ? Integer.MAX_VALUE : parseHexColor(skyHex, oldSky);
        int newMoon = moonHex.isEmpty() ? Integer.MAX_VALUE : parseHexColor(moonHex, oldMoon);

        Constructor<?> csCtor = csClass.getConstructor(int.class, int.class);
        Object newColorSettings = csCtor.newInstance(newSky, newMoon);

        String moonTextureStr = tweaks.moonTexture.trim();
        Object newMoonTexture = moonTexture;
        if (!moonTextureStr.isEmpty()) {
            ResourceLocation parsed = ResourceLocation.tryParse(moonTextureStr);
            if (parsed != null) newMoonTexture = parsed;
        }

        return constructRecord(clientSettingsClass, 4, newColorSettings, newMoonSize, newMoonTexture, (SoundEvent) null);
    }

    private static Object buildNewTextComponents(Object oldTextComponents, ECTweaksConfig.EventTweaks tweaks) throws Exception {
        Class<?> textComponentsClass = oldTextComponents.getClass();
        Object name = getField(textComponentsClass, oldTextComponents, "name");

        Optional<?> newRise = Optional.of(buildNotification(tweaks.startMessage, tweaks.startMessage.isEmpty()));
        Optional<?> newSet = Optional.of(buildNotification(tweaks.endMessage, tweaks.endMessage.isEmpty()));

        Class<?> ctcClass = name.getClass();
        Constructor<?> ctor = textComponentsClass.getDeclaredConstructor(ctcClass, Optional.class, Optional.class);
        ctor.setAccessible(true);
        return ctor.newInstance(name, newRise, newSet);
    }

    private static Object buildNotification(String text, boolean none) throws Exception {
        Class<?> ctcClass = Class.forName(CTC_CLASS);
        Constructor<?> ctcCtor = ctcClass.getConstructor(String.class, Style.class, List.class);
        StringBuilder cleanText = new StringBuilder();
        Style style = none ? Style.EMPTY : StyleParser.parse(text, cleanText);
        String key = none ? "" : cleanText.toString().replace("%", "%%");
        Object ctc = ctcCtor.newInstance(key, style, List.of());

        Class<?> notifClass = Class.forName(NOTIFICATION_CLASS);
        Class<?> notifTypeClass = Class.forName(NOTIFICATION_TYPE_CLASS);
        Object notifType = notifTypeClass.getField(none ? "NONE" : "CHAT").get(null);

        Constructor<?> notifCtor = notifClass.getDeclaredConstructor(ctcClass, notifTypeClass);
        notifCtor.setAccessible(true);
        return notifCtor.newInstance(ctc, notifType);
    }

    private static int parseHexColor(String input, int fallback) {
        String clean = input.replace("#", "").replace("0x", "").trim();
        try {
            return (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException e) {
            Constants.LOGGER.warn("Could not parse hex color: {}", input);
            return fallback;
        }
    }

    private static Object constructRecord(Class<?> recordClass, int paramCount, Object... args) throws Exception {
        for (Constructor<?> c : recordClass.getDeclaredConstructors()) {
            if (c.getParameterCount() == paramCount) {
                c.setAccessible(true);
                return c.newInstance(args);
            }
        }
        throw new RuntimeException("No constructor with " + paramCount + " params for " + recordClass.getName());
    }

    private static Field field(Class<?> cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Object getField(Class<?> cls, Object instance, String name) throws Exception {
        return field(cls, name).get(instance);
    }

    private static void replaceField(Class<?> cls, Object instance, String name, Object value) throws Exception {
        field(cls, name).set(instance, value);
    }

    private static void cacheRemovals(String path, ECTweaksConfig.EventTweaks tweaks, boolean log) {
        List<? extends String> removals = tweaks.spawnRemovals;
        if (removals.isEmpty()) return;
        Set<EntityType<?>> set = new HashSet<>();
        for (String s : removals) {
            set.addAll(resolveSpawnTargets(s.trim(), path, "spawn_removals"));
        }
        if (!set.isEmpty()) {
            REMOVALS_BY_EVENT.put(path, set);
            if (log) Constants.LOGGER.info("Cached {} spawn removals for {}", set.size(), path);
        }
    }

    public static String getCurrentLunarEventPath(Level level) {
        if (reflectionFailed) return null;
        try {
            if (LUNAR_FORECAST_METHOD == null) {
                LUNAR_FORECAST_METHOD = Class.forName(EC_MAIN_CLASS).getMethod("lunarForecastWorldData", Level.class);
            }
            Optional<?> opt = (Optional<?>) LUNAR_FORECAST_METHOD.invoke(null, level);
            if (opt.isEmpty()) return null;
            Object data = opt.get();
            Method holderM = CURRENT_EVENT_HOLDER_METHOD;
            if (holderM == null) {
                holderM = data.getClass().getMethod("currentLunarEventHolder");
                CURRENT_EVENT_HOLDER_METHOD = holderM;
            }
            Holder<?> holder = (Holder<?>) holderM.invoke(data);
            String path = holder.unwrapKey().map(k -> k.location().getPath()).orElse(null);
            return (path != null && ECTweaksConfig.EVENTS.containsKey(path)) ? path : null;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            reflectionFailed = true;
            Constants.LOGGER.error("Enhanced Celestials lunar-event API not found; disabling runtime tweaks", e);
            return null;
        } catch (Throwable t) {
            if (!loggedLunarReadError) {
                loggedLunarReadError = true;
                Constants.LOGGER.warn("Transient failure reading current lunar event.", t);
            }
            return null;
        }
    }

    private static void recomputeForecast(ServerLevel level, boolean log) throws Exception {
        if (LUNAR_FORECAST_METHOD == null) {
            LUNAR_FORECAST_METHOD = Class.forName(EC_MAIN_CLASS).getMethod("lunarForecastWorldData", Level.class);
        }
        Optional<?> opt = (Optional<?>) LUNAR_FORECAST_METHOD.invoke(null, level);
        if (opt.isEmpty()) return;
        Object data = opt.get();
        Class<?> dataClass = data.getClass();

        long currentDay = (long) dataClass.getMethod("getCurrentDay").invoke(data);
        Field forecastField = dataClass.getDeclaredField("forecast");
        forecastField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> forecast = (List<Object>) forecastField.get(data);

        Object activeEvent = null;
        if (!forecast.isEmpty()) {
            Object first = forecast.get(0);
            Method activeM = first.getClass().getMethod("active", long.class);
            if ((boolean) activeM.invoke(first, currentDay)) {
                activeEvent = first;
            }
        }

        dataClass.getMethod("recomputeForecast").invoke(data);

        if (activeEvent != null) {
            @SuppressWarnings("unchecked")
            List<Object> newForecast = (List<Object>) forecastField.get(data);
            Method getKeyM = activeEvent.getClass().getMethod("getLunarEventKey");
            Method scheduledM = activeEvent.getClass().getMethod("scheduledDay");
            Object activeKey = getKeyM.invoke(activeEvent);
            boolean already = false;
            for (Object instance : newForecast) {
                long sd = (long) scheduledM.invoke(instance);
                if (sd == currentDay && activeKey.equals(getKeyM.invoke(instance))) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                Method addEventM = null;
                for (Method m : dataClass.getMethods()) {
                    if (m.getName().equals("addEventToForecast") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == int.class) {
                        addEventM = m;
                        break;
                    }
                }
                if (addEventM != null) {
                    addEventM.invoke(data, 0, activeEvent);
                } else {
                    newForecast.add(0, activeEvent);
                }
            }
        }

        if (log) Constants.LOGGER.info("Recomputed lunar forecast for {} (active event preserved)", level.dimension().location());
    }

    private ECTweaksApplier() {
    }
}
