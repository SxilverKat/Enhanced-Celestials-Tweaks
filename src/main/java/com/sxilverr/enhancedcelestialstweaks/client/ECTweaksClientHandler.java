package com.sxilverr.enhancedcelestialstweaks.client;

import com.sxilverr.enhancedcelestialstweaks.ECTweaksApplier;
import com.sxilverr.enhancedcelestialstweaks.ECTweaksClientConfig;
import com.sxilverr.enhancedcelestialstweaks.ECTweaksConfig;
import com.sxilverr.enhancedcelestialstweaks.EnhancedCelestialsTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = EnhancedCelestialsTweaks.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ECTweaksClientHandler {

    private static final int HEX_PARSE_FAILED = -1;

    private static ConfigurableSoundInstance currentSound;
    private static String currentSoundEvent;

    private static Language lastLang;
    private static String lastAppliedSleepMessage;
    private static Field cachedStorageField;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        applySleepFailOverride();
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;

        if (level == null || !ECTweaksConfig.GENERAL.enabled.get()) {
            stopCurrent(mc);
            return;
        }

        String eventPath = ECTweaksApplier.getCurrentLunarEventPath(level);
        if (eventPath == null) {
            stopCurrent(mc);
            return;
        }

        boolean isNewEvent = !eventPath.equals(currentSoundEvent);
        boolean soundDead = currentSound == null || currentSound.isStopped()
                || !mc.getSoundManager().isActive(currentSound);

        if (!isNewEvent && !soundDead) {
            return;
        }

        ECTweaksClientConfig.EventClient tweaks = ECTweaksClientConfig.EVENTS.get(eventPath);
        if (tweaks == null) {
            stopCurrent(mc);
            currentSoundEvent = eventPath;
            return;
        }

        if (!isNewEvent && soundDead && !tweaks.soundtrackLoop.get()) {
            return;
        }

        if (isNewEvent) {
            stopCurrent(mc);
        } else if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        currentSoundEvent = eventPath;

        String soundId = tweaks.soundtrack.get().trim();
        if (soundId.isEmpty()) return;

        ResourceLocation soundLoc = ResourceLocation.tryParse(soundId);
        if (soundLoc == null) return;

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
        if (sound == null) return;

        float vol = tweaks.soundtrackVolume.get().floatValue();
        float pitch = tweaks.soundtrackPitch.get().floatValue();
        boolean loop = tweaks.soundtrackLoop.get();

        currentSound = new ConfigurableSoundInstance(sound, vol, pitch, loop);
        mc.getSoundManager().play(currentSound);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stopCurrent(Minecraft.getInstance());
    }

    private static void stopCurrent(Minecraft mc) {
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        currentSoundEvent = null;
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        String currentEvent = ECTweaksApplier.getCurrentLunarEventPath(level);
        if (currentEvent == null) return;
        ECTweaksClientConfig.EventClient tweaks = ECTweaksClientConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        String fogColorHex = tweaks.fogColor.get().trim();
        if (fogColorHex.isEmpty()) return;
        int color = parseHex(fogColorHex);
        if (color == HEX_PARSE_FAILED) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        String currentEvent = ECTweaksApplier.getCurrentLunarEventPath(level);
        if (currentEvent == null) return;
        ECTweaksClientConfig.EventClient tweaks = ECTweaksClientConfig.EVENTS.get(currentEvent);
        if (tweaks == null) return;
        double mul = tweaks.fogDensityMultiplier.get();
        if (mul == 1.0 || !(mul > 0.0)) return;
        event.setFarPlaneDistance((float) (event.getFarPlaneDistance() / mul));
        event.setNearPlaneDistance((float) (event.getNearPlaneDistance() / mul));
        event.setCanceled(true);
    }

    @SuppressWarnings("unchecked")
    private static void applySleepFailOverride() {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        Language lang = Language.getInstance();
        if (lang == null) return;
        String processed = ECTweaksClientConfig.GENERAL.sleepPreventedMessage.get().replace('&', '§');
        if (lang == lastLang && processed.equals(lastAppliedSleepMessage)) return;
        try {
            if (cachedStorageField == null || lang != lastLang) {
                cachedStorageField = null;
                for (Field f : lang.getClass().getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        cachedStorageField = f;
                        break;
                    }
                }
            }
            if (cachedStorageField == null) return;
            Map<String, String> map = (Map<String, String>) cachedStorageField.get(lang);
            if (map == null) return;
            Map<String, String> updated = new HashMap<>(map);
            updated.put("enhancedcelestials.sleep.fail", processed);
            cachedStorageField.set(lang, updated);
            lastLang = lang;
            lastAppliedSleepMessage = processed;
        } catch (Throwable ignored) {
        }
    }

    private static int parseHex(String input) {
        String clean = input.replace("#", "").replace("0x", "").trim();
        if (clean.length() == 3) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 3; i++) {
                sb.append(clean.charAt(i)).append(clean.charAt(i));
            }
            clean = sb.toString();
        }
        try {
            return (int) (Long.parseLong(clean, 16) & 0xFFFFFFL);
        } catch (NumberFormatException e) {
            EnhancedCelestialsTweaks.LOGGER.warn("Could not parse fog hex color: {}", input);
            return HEX_PARSE_FAILED;
        }
    }
}
