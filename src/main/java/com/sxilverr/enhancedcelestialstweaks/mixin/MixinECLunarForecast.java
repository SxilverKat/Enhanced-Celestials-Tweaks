package com.sxilverr.enhancedcelestialstweaks.mixin;

import com.sxilverr.enhancedcelestialstweaks.ECTweaksConfig;
import com.sxilverr.enhancedcelestialstweaks.EnhancedCelestialsTweaks;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

@Mixin(targets = "dev.corgitaco.enhancedcelestials.lunarevent.EnhancedCelestialsLunarForecastWorldData", remap = false)
public class MixinECLunarForecast {

    @Inject(
            method = "createLunarEventSpawnRequirements",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void enhancedcelestialstweaks$applyConfigTweaks(
            ResourceKey<Level> dimension,
            Registry<?> lunarEventProbabilitiesRegistry,
            Registry<?> lunarEvents,
            Holder.Reference<?> dimensionSettingsHolder,
            CallbackInfoReturnable<Object2ObjectOpenHashMap<Holder<Object>, Object>> cir
    ) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) {
            return;
        }
        try {
            Object2ObjectOpenHashMap<Holder<Object>, Object> original = cir.getReturnValue();
            Object2ObjectOpenHashMap<Holder<Object>, Object> modified = new Object2ObjectOpenHashMap<>();
            boolean log = ECTweaksConfig.GENERAL.logTweaks.get();

            for (Map.Entry<Holder<Object>, Object> entry : original.entrySet()) {
                Holder<Object> holder = entry.getKey();
                Object spawnReqs = entry.getValue();

                String path = holder.unwrapKey()
                        .map(k -> ((ResourceKey<?>) k).location().getPath())
                        .orElse(null);

                if (path == null) {
                    modified.put(holder, spawnReqs);
                    continue;
                }

                ECTweaksConfig.EventTweaks tweaks = ECTweaksConfig.EVENTS.get(path);
                if (tweaks == null) {
                    modified.put(holder, spawnReqs);
                    continue;
                }

                double mul = tweaks.chanceMultiplier.get();
                int minNights = tweaks.minNightsBetween.get();

                if (mul == 1.0 && minNights < 0) {
                    modified.put(holder, spawnReqs);
                    continue;
                }

                Class<?> srClass = spawnReqs.getClass();
                Field chanceF = srClass.getDeclaredField("chance");
                Field minF = srClass.getDeclaredField("minNumberOfNights");
                Field phasesF = srClass.getDeclaredField("validMoonPhases");
                chanceF.setAccessible(true);
                minF.setAccessible(true);
                phasesF.setAccessible(true);

                double oldChance = chanceF.getDouble(spawnReqs);
                int oldMin = minF.getInt(spawnReqs);
                Object phases = phasesF.get(spawnReqs);

                double newChance = mul != 1.0 ? oldChance * mul : oldChance;
                int newMin = minNights >= 0 ? minNights : oldMin;

                if (newChance <= 0.0) {
                    if (log) {
                        EnhancedCelestialsTweaks.LOGGER.info("Disabled lunar event {} via mixin", path);
                    }
                    continue;
                }

                Constructor<?> canonical = findCanonicalConstructor(srClass);
                if (canonical == null) {
                    modified.put(holder, spawnReqs);
                    continue;
                }
                canonical.setAccessible(true);
                Object newReqs = canonical.newInstance(newChance, newMin, phases);
                modified.put(holder, newReqs);

                if (log) {
                    EnhancedCelestialsTweaks.LOGGER.info("Mixin tweaked {}: chance={} minNights={}", path, newChance, newMin);
                }
            }

            cir.setReturnValue(modified);
        } catch (Throwable t) {
            EnhancedCelestialsTweaks.LOGGER.error("Mixin failed to apply Enhanced Celestials tweaks", t);
        }
    }

    @Inject(
            method = "checkEmptyForecastOrThrow",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void enhancedcelestialstweaks$preventEmptyForecastCrash(CallbackInfo ci) {
        if (ECTweaksConfig.GENERAL.enabled.get()) {
            ci.cancel();
        }
    }

    private static Constructor<?> findCanonicalConstructor(Class<?> srClass) {
        for (Constructor<?> c : srClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 3 && params[0] == double.class && params[1] == int.class && IntArraySet.class.isAssignableFrom(params[2])) {
                return c;
            }
        }
        return null;
    }
}
