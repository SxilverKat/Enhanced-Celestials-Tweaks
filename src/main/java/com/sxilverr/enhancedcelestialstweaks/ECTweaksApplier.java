package com.sxilverr.enhancedcelestialstweaks;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private static final UUID HEALTH_MOD = UUID.fromString("9b62de1f-1100-4001-9001-000000000001");
    private static final UUID DAMAGE_MOD = UUID.fromString("9b62de1f-1100-4001-9001-000000000002");
    private static final UUID SPEED_MOD = UUID.fromString("9b62de1f-1100-4001-9001-000000000003");
    private static final UUID SWIM_MOD = UUID.fromString("9b62de1f-1100-4001-9001-000000000004");


    private static volatile Method POPULATE_EQUIPMENT;
    private static volatile boolean populateLookupTried = false;

    private static final Map<String, Set<EntityType<?>>> REMOVALS_BY_EVENT = new HashMap<>();
    private static final Map<ResourceKey<Level>, String> LAST_KNOWN_EVENT = new ConcurrentHashMap<>();
    private static final Map<String, Long> EVENT_END_TIMES = new ConcurrentHashMap<>();
    private static volatile Method LUNAR_FORECAST_METHOD;
    private static volatile boolean reflectionFailed = false;

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        REMOVALS_BY_EVENT.clear();
        LAST_KNOWN_EVENT.clear();
        EVENT_END_TIMES.clear();
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        if (!ModList.get().isLoaded(EnhancedCelestialsTweaks.EC_MOD_ID)) {
            EnhancedCelestialsTweaks.LOGGER.info("Enhanced Celestials not loaded, skipping tweaks.");
            return;
        }
        try {
            apply(event.getServer().registryAccess());
        } catch (Throwable t) {
            EnhancedCelestialsTweaks.LOGGER.error("Failed to apply Enhanced Celestials tweaks", t);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        if (!ECTweaksConfig.GENERAL.recomputeForecastOnStart.get()) return;
        if (!ModList.get().isLoaded(EnhancedCelestialsTweaks.EC_MOD_ID)) return;
        boolean log = ECTweaksConfig.GENERAL.logTweaks.get();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            try {
                recomputeForecast(level, log);
            } catch (Throwable t) {
                EnhancedCelestialsTweaks.LOGGER.error("Failed to recompute forecast for {}", level.dimension().location(), t);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        REMOVALS_BY_EVENT.clear();
        LAST_KNOWN_EVENT.clear();
        EVENT_END_TIMES.clear();
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        Level level = mob.level();
        if (level.isClientSide) return;
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;

        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return;

        Set<EntityType<?>> removals = REMOVALS_BY_EVENT.get(currentEvent);
        if (removals != null && removals.contains(mob.getType())) {
            event.setSpawnCancelled(true);
            event.setCanceled(true);
            return;
        }

        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;

        applyAttribute(mob, Attributes.MAX_HEALTH, HEALTH_MOD, "ec_tweaks.health", tweaks.mobHealthMultiplier.get());
        applyAttribute(mob, Attributes.ATTACK_DAMAGE, DAMAGE_MOD, "ec_tweaks.damage", tweaks.mobDamageMultiplier.get());
        applyAttribute(mob, Attributes.MOVEMENT_SPEED, SPEED_MOD, "ec_tweaks.speed", tweaks.mobSpeedMultiplier.get());
        Attribute swim = ForgeMod.SWIM_SPEED.get();
        if (swim != null) applyAttribute(mob, swim, SWIM_MOD, "ec_tweaks.swim", tweaks.mobSwimSpeedMultiplier.get());

        if (tweaks.mobHealthMultiplier.get() != 1.0) {
            mob.setHealth(mob.getMaxHealth());
        }

        double gearMul = tweaks.mobGearChanceMultiplier.get();
        if (gearMul > 1.0 && mob instanceof Monster) {
            boostGear(mob, level.random, event.getDifficulty(), gearMul, tweaks.mobDropsEventGear.get());
        }

        if (tweaks.preventMobDespawn.get() || tweaks.forceDespawnAfterEvent.get()) {
            mob.setPersistenceRequired();
            CompoundTag data = mob.getPersistentData();
            data.putBoolean(NBT_TAG_EVENT_MOB, true);
            data.putString(NBT_TAG_EVENT_ID, currentEvent);
        }
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;

        Level level = (Level) event.getLevel();
        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;

        int maxLight = tweaks.monsterSpawnLightLevel.get();
        if (maxLight > 0) {
            BlockPos pos = mob.blockPosition();
            int blockLight = event.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
            if (blockLight <= maxLight) {
                event.setResult(Event.Result.ALLOW);
            }
        }
    }

    @SubscribeEvent
    public static void onAllowDespawn(MobSpawnEvent.AllowDespawn event) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        LivingEntity entity = event.getEntity();
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(NBT_TAG_EVENT_MOB)) return;
        String mobEventId = data.getString(NBT_TAG_EVENT_ID);
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(mobEventId);
        if (tweaks != null && tweaks.preventMobDespawn.get()) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        LivingEntity entity = event.getEntity();
        if (entity.tickCount % 20 != 5) return;
        Level level = entity.level();
        if (level.isClientSide) return;

        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        List<? extends String> effects = tweaks.mobEffects.get();
        if (effects.isEmpty()) return;

        boolean isPlayer = entity instanceof Player;
        boolean isMonster = entity.getType().getCategory() == MobCategory.MONSTER;

        for (String effectStr : effects) {
            String[] parts = effectStr.split(";");
            if (parts.length < 3) continue;
            ResourceLocation effectId = ResourceLocation.tryParse(parts[0].trim());
            if (effectId == null) continue;
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
            if (effect == null) continue;
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
                    entity.addEffect(new MobEffectInstance(effect, dur, amp, true, false, false));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide) return;

        String currentEvent = getCurrentLunarEventPath(level);
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        double mul = tweaks.rareDropMultiplier.get();
        if (mul <= 1.0) return;

        RandomSource rand = level.random;
        java.util.Collection<ItemEntity> drops = event.getDrops();
        java.util.List<ItemEntity> additional = new ArrayList<>();
        for (ItemEntity item : drops) {
            ItemStack stack = item.getItem();
            if (stack.getRarity() == Rarity.COMMON) continue;
            if (rand.nextDouble() < (mul - 1.0)) {
                additional.add(new ItemEntity(level, item.getX(), item.getY(), item.getZ(), stack.copy()));
            }
        }
        drops.addAll(additional);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        if (!(event.level instanceof ServerLevel sLevel)) return;

        String currentEvent = getCurrentLunarEventPath(sLevel);
        ResourceKey<Level> dimKey = sLevel.dimension();
        String last = LAST_KNOWN_EVENT.get(dimKey);
        if (last != null && !last.isEmpty() && !last.equals(currentEvent == null ? "" : currentEvent)) {
            EVENT_END_TIMES.put(last, sLevel.getGameTime());
        }
        LAST_KNOWN_EVENT.put(dimKey, currentEvent == null ? "" : currentEvent);

        adjustNightLength(sLevel, currentEvent);

        if (sLevel.getGameTime() % 100 == 0) {
            forcedDespawnPass(sLevel, currentEvent);
        }
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (!ECTweaksConfig.GENERAL.disableEcCommandsForNonOps.get()) return;
        try {
            String input = event.getParseResults().getReader().getString().trim();
            if (input.startsWith("/")) input = input.substring(1);
            if (!input.startsWith("lunarforecast") && !input.startsWith("setlunarevent")) return;
            CommandSourceStack source = event.getParseResults().getContext().getSource();
            if (source.hasPermission(2)) return;
            event.setCanceled(true);
            source.sendFailure(Component.literal("Enhanced Celestials commands are restricted to operators."));
        } catch (Throwable ignored) {}
    }

    private static void adjustNightLength(ServerLevel sLevel, String currentEvent) {
        if (currentEvent == null) return;
        ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        long nightLen = tweaks.nightLengthTicks.get();
        if (nightLen == 12000L) return;

        long dayTime = sLevel.getDayTime() % 24000L;
        if (dayTime < 13000L || dayTime >= 23000L) return;

        double slowdown = nightLen / 12000.0;
        if (slowdown > 1.0) {
            if (sLevel.random.nextDouble() < (1.0 - 1.0 / slowdown)) {
                sLevel.setDayTime(sLevel.getDayTime() - 1);
            }
        } else if (slowdown < 1.0) {
            long extra = (long) ((1.0 / slowdown) - 1.0);
            if (extra > 0) sLevel.setDayTime(sLevel.getDayTime() + extra);
        }
    }

    private static void forcedDespawnPass(ServerLevel sLevel, String currentEvent) {
        long now = sLevel.getGameTime();
        for (Entity entity : sLevel.getAllEntities()) {
            if (!(entity instanceof Mob mob)) continue;
            CompoundTag data = mob.getPersistentData();
            if (!data.getBoolean(NBT_TAG_EVENT_MOB)) continue;
            String mobEventId = data.getString(NBT_TAG_EVENT_ID);
            if (mobEventId.equals(currentEvent)) continue;
            ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(mobEventId);
            if (tweaks == null || !tweaks.forceDespawnAfterEvent.get()) continue;
            Long endTime = EVENT_END_TIMES.get(mobEventId);
            if (endTime == null) continue;
            long delay = (long) tweaks.forceDespawnDelaySeconds.get() * 20L;
            if (now - endTime >= delay) {
                mob.discard();
            }
        }
    }

    private static void applyAttribute(LivingEntity entity, Attribute attr, UUID uuid, String name, double multiplier) {
        if (multiplier == 1.0 || attr == null) return;
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance == null) return;
        instance.removeModifier(uuid);
        instance.addPermanentModifier(new AttributeModifier(uuid, name, multiplier - 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    private static Method getPopulateMethod() {
        if (populateLookupTried) return POPULATE_EQUIPMENT;
        synchronized (ECTweaksApplier.class) {
            if (populateLookupTried) return POPULATE_EQUIPMENT;
            try {
                POPULATE_EQUIPMENT = ObfuscationReflectionHelper.findMethod(Mob.class, "m_213945_", RandomSource.class, DifficultyInstance.class);
            } catch (Throwable t) {
                try {
                    POPULATE_EQUIPMENT = ObfuscationReflectionHelper.findMethod(Mob.class, "populateDefaultEquipmentSlots", RandomSource.class, DifficultyInstance.class);
                } catch (Throwable t2) {
                    EnhancedCelestialsTweaks.LOGGER.warn("Could not access populateDefaultEquipmentSlots; gear chance multiplier disabled", t2);
                }
            }
            populateLookupTried = true;
            return POPULATE_EQUIPMENT;
        }
    }

    private static void boostGear(Mob mob, RandomSource random, DifficultyInstance difficulty, double chanceMul, boolean dropGear) {
        Method m = getPopulateMethod();
        if (m == null) return;

        java.util.EnumMap<EquipmentSlot, ItemStack> before = new java.util.EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            before.put(slot, mob.getItemBySlot(slot).copy());
        }

        try {
            int extraRolls = (int) Math.floor(chanceMul - 1.0);
            for (int i = 0; i < extraRolls; i++) {
                m.invoke(mob, random, difficulty);
            }
            double frac = (chanceMul - 1.0) - extraRolls;
            if (frac > 0 && random.nextDouble() < frac) {
                m.invoke(mob, random, difficulty);
            }
        } catch (Throwable t) {
            EnhancedCelestialsTweaks.LOGGER.warn("Failed to invoke populateDefaultEquipmentSlots", t);
            return;
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

        boolean log = ECTweaksConfig.GENERAL.logTweaks.get();
        boolean visualOnly = ECTweaksConfig.GENERAL.eventsVisualOnly.get();

        try {
            addDimensions(registryAccess, lunarEventKey, lunarDimSettingsKey, log);
        } catch (Throwable t) {
            EnhancedCelestialsTweaks.LOGGER.error("Failed to add dimensions", t);
        }

        Optional<Registry<Object>> lunarEventReg = registryAccess.registry(lunarEventKey);
        if (lunarEventReg.isEmpty()) {
            if (log) EnhancedCelestialsTweaks.LOGGER.warn("Lunar event registry not found.");
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
                EnhancedCelestialsTweaks.LOGGER.error("Failed to apply tweaks for {}", path, t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addDimensions(RegistryAccess registryAccess, ResourceKey<Registry<Object>> lunarEventKey, ResourceKey<Registry<Object>> lunarDimSettingsKey, boolean log) throws Exception {
        Map<String, List<String>> dimsByEvent = new HashMap<>();
        Set<String> allDims = new HashSet<>();
        for (Map.Entry<String, ECTweaksConfig.EventTweaks> entry : ECTweaksConfig.EVENTS.entrySet()) {
            List<? extends String> eventDims = entry.getValue().addedDimensions.get();
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

        ResourceLocation overworldLoc = new ResourceLocation("minecraft", "overworld");
        ResourceKey<Object> overworldDimKey = ResourceKey.create(lunarDimSettingsKey, overworldLoc);
        Object overworldSettings = dimRegistry.get(overworldDimKey);
        if (overworldSettings == null) {
            if (log) EnhancedCelestialsTweaks.LOGGER.warn("No overworld lunar dimension settings; cannot add dimensions");
            return;
        }

        if (dimRegistry instanceof MappedRegistry<Object> mappedReg) {
            mappedReg.unfreeze();
            try {
                for (String dimId : allDims) {
                    ResourceLocation loc = ResourceLocation.tryParse(dimId.trim());
                    if (loc == null) continue;
                    ResourceKey<Object> dimKey = ResourceKey.create(lunarDimSettingsKey, loc);
                    if (mappedReg.containsKey(dimKey)) continue;
                    mappedReg.register(dimKey, overworldSettings, Lifecycle.stable());
                    if (log) EnhancedCelestialsTweaks.LOGGER.info("Registered lunar dimension settings for {}", loc);
                }
            } finally {
                mappedReg.freeze();
            }
        } else {
            if (log) EnhancedCelestialsTweaks.LOGGER.warn("Lunar dimension registry is not a MappedRegistry; skipping dimension addition");
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
                if (log) EnhancedCelestialsTweaks.LOGGER.info("Added event {} to dimension {}", eventPath, loc);
            }
            eventChancesField.set(lunarEvent, newChances);
        }
    }

    private static void applyEventTweaks(Object lunarEvent, ECTweaksConfig.EventTweaks tweaks, boolean visualOnly, String path, boolean log) throws Exception {
        Class<?> lunarEventClass = lunarEvent.getClass();

        if (visualOnly) {
            replaceField(lunarEventClass, lunarEvent, "lunarMobSettings", buildNeutralMobSettings(getField(lunarEventClass, lunarEvent, "lunarMobSettings")));
            replaceField(lunarEventClass, lunarEvent, "dropSettings", constructRecord(getField(lunarEventClass, lunarEvent, "dropSettings").getClass(), 1, new HashMap<>()));
            if (log) EnhancedCelestialsTweaks.LOGGER.info("Neutralized {} (visual only)", path);
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

        if (log) EnhancedCelestialsTweaks.LOGGER.info("Applied tweaks for {}", path);
    }

    @SuppressWarnings("unchecked")
    private static Object buildNewDropSettings(Object oldDropSettings, ECTweaksConfig.EventTweaks tweaks) throws Exception {
        if (tweaks.enableCropDropBoost == null) {
            return oldDropSettings;
        }
        Class<?> dsClass = oldDropSettings.getClass();
        Map<TagKey<Item>, Double> oldEnhancer = (Map<TagKey<Item>, Double>) getField(dsClass, oldDropSettings, "dropEnhancer");

        Map<TagKey<Item>, Double> newEnhancer = new HashMap<>(oldEnhancer);
        TagKey<Item> harvestTag = TagKey.create(Registries.ITEM, new ResourceLocation("enhancedcelestials", "harvest_moon_crops"));
        newEnhancer.remove(harvestTag);
        for (String entry : tweaks.cropDropTags.get()) {
            String[] parts = entry.split(";");
            if (parts.length != 2) continue;
            ResourceLocation loc = ResourceLocation.tryParse(parts[0].trim());
            if (loc == null) continue;
            newEnhancer.remove(TagKey.create(Registries.ITEM, loc));
        }

        if (tweaks.enableCropDropBoost.get()) {
            for (String entry : tweaks.cropDropTags.get()) {
                String[] parts = entry.split(";");
                if (parts.length != 2) continue;
                ResourceLocation loc = ResourceLocation.tryParse(parts[0].trim());
                if (loc == null) continue;
                try {
                    double mul = Double.parseDouble(parts[1].trim());
                    newEnhancer.put(TagKey.create(Registries.ITEM, loc), mul);
                } catch (NumberFormatException ex) {
                    EnhancedCelestialsTweaks.LOGGER.warn("Bad crop drop multiplier in entry: {}", entry);
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
        double mobMul = tweaks.mobSpawnMultiplier.get();
        for (Map.Entry<MobCategory, Double> e : oldSpawnCat.entrySet()) {
            newSpawnCat.put(e.getKey(), e.getValue() * mobMul);
        }
        for (String entry : tweaks.mobCategoryMultipliers.get()) {
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            try {
                MobCategory cat = MobCategory.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                double value = Double.parseDouble(parts[1].trim());
                newSpawnCat.put(cat, value);
            } catch (IllegalArgumentException ex) {
                EnhancedCelestialsTweaks.LOGGER.warn("Bad category multiplier in {}: {}", path, entry);
            }
        }

        Object newSpawnInfo = buildNewSpawnInfo(oldSpawnInfo, tweaks, path);

        Object newBlockSleep = oldBlockSleep;
        BoolOverride sleepOverride = tweaks.blockSleeping.get();
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

        BoolOverride useBiome = tweaks.useBiomeSpawnSettings.get();
        BoolOverride forceSurface = tweaks.forceSurfaceSpawning.get();
        BoolOverride slimes = tweaks.slimesSpawnEverywhere.get();
        List<? extends String> additions = tweaks.spawnAdditions.get();

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
            int count = 0;
            for (String entry : additions) {
                String[] parts = entry.split(";");
                if (parts.length != 4) continue;
                ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
                if (id == null) continue;
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
                if (type == null) {
                    EnhancedCelestialsTweaks.LOGGER.warn("Unknown entity in {} spawn_additions: {}", path, parts[0]);
                    continue;
                }
                try {
                    int weight = Integer.parseInt(parts[1].trim());
                    int min = Integer.parseInt(parts[2].trim());
                    int max = Integer.parseInt(parts[3].trim());
                    builder.addSpawn(type.getCategory(), new MobSpawnSettings.SpawnerData(type, weight, min, max));
                    count++;
                } catch (NumberFormatException e) {
                    EnhancedCelestialsTweaks.LOGGER.warn("Bad number in {} spawn_additions: {}", path, entry);
                }
            }
            if (count > 0) innerSettings = builder.build();
        }
        return constructRecord(spawnInfoClass, 4, useBiomeVal, forceSurfaceVal, slimesVal, innerSettings);
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
        Object moonTexture = getField(clientSettingsClass, oldClientSettings, "moonTextureLocation");

        Class<?> csClass = oldColorSettings.getClass();
        int oldSky = field(csClass, "skyLightColor").getInt(oldColorSettings);
        int oldMoon = field(csClass, "moonTextureColor").getInt(oldColorSettings);

        String skyHex = tweaks.skyColor.get().trim();
        String moonHex = tweaks.moonColor.get().trim();
        int newSky = skyHex.isEmpty() ? Integer.MAX_VALUE : parseHexColor(skyHex, oldSky);
        int newMoon = moonHex.isEmpty() ? Integer.MAX_VALUE : parseHexColor(moonHex, oldMoon);

        Constructor<?> csCtor = csClass.getConstructor(int.class, int.class);
        Object newColorSettings = csCtor.newInstance(newSky, newMoon);

        String moonTextureStr = tweaks.moonTexture.get().trim();
        Object newMoonTexture = moonTexture;
        if (!moonTextureStr.isEmpty()) {
            ResourceLocation parsed = ResourceLocation.tryParse(moonTextureStr);
            if (parsed != null) newMoonTexture = parsed;
        }

        return constructRecord(clientSettingsClass, 4, newColorSettings, moonSize, newMoonTexture, (SoundEvent) null);
    }

    private static Object buildNewTextComponents(Object oldTextComponents, ECTweaksConfig.EventTweaks tweaks) throws Exception {
        Class<?> textComponentsClass = oldTextComponents.getClass();
        Object name = getField(textComponentsClass, oldTextComponents, "name");

        Optional<?> newRise = Optional.of(buildNotification(tweaks.startMessage.get(), tweaks.startMessage.get().isEmpty()));
        Optional<?> newSet = Optional.of(buildNotification(tweaks.endMessage.get(), tweaks.endMessage.get().isEmpty()));

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
        Object ctc = ctcCtor.newInstance(none ? "" : cleanText.toString(), style, List.of());

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
            EnhancedCelestialsTweaks.LOGGER.warn("Could not parse hex color: {}", input);
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
        List<? extends String> removals = tweaks.spawnRemovals.get();
        if (removals.isEmpty()) return;
        Set<EntityType<?>> set = new HashSet<>();
        for (String s : removals) {
            ResourceLocation id = ResourceLocation.tryParse(s.trim());
            if (id == null) continue;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (type == null) {
                EnhancedCelestialsTweaks.LOGGER.warn("Unknown entity in {} spawn_removals: {}", path, s);
                continue;
            }
            set.add(type);
        }
        if (!set.isEmpty()) {
            REMOVALS_BY_EVENT.put(path, set);
            if (log) EnhancedCelestialsTweaks.LOGGER.info("Cached {} spawn removals for {}", set.size(), path);
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
            Method holderM = data.getClass().getMethod("currentLunarEventHolder");
            net.minecraft.core.Holder<?> holder = (net.minecraft.core.Holder<?>) holderM.invoke(data);
            return holder.unwrapKey().map(k -> k.location().getPath()).orElse(null);
        } catch (Throwable t) {
            reflectionFailed = true;
            EnhancedCelestialsTweaks.LOGGER.error("Failed to read current lunar event", t);
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

        if (log) EnhancedCelestialsTweaks.LOGGER.info("Recomputed lunar forecast for {} (active event preserved)", level.dimension().location());
    }
}
