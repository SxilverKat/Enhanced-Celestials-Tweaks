package com.sxilverr.enhancedcelestialstweaks.mixin;

import com.sxilverr.enhancedcelestialstweaks.ECTweaksConfig;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(targets = "dev.corgitaco.enhancedcelestials.lunarevent.EnhancedCelestialsLunarForecastWorldData", remap = false)
public abstract class MixinECRefresh {

    @Unique
    private int enhancedcelestialstweaks$cooldown = 0;

    @Inject(method = "readFromNetwork", at = @At("RETURN"), remap = false)
    private void enhancedcelestialstweaks$undoSpuriousFade(CompoundTag tag, CallbackInfo ci) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        enhancedcelestialstweaks$primeFields();
        enhancedcelestialstweaks$cooldown = 40;
    }

    @Inject(method = "baseTick", at = @At("RETURN"), remap = false)
    private void enhancedcelestialstweaks$preventPostSyncFade(CallbackInfo ci) {
        if (!ECTweaksConfig.GENERAL.enabled.get()) return;
        if (enhancedcelestialstweaks$cooldown <= 0) return;
        enhancedcelestialstweaks$cooldown--;
        enhancedcelestialstweaks$primeFields();
    }

    @Unique
    private void enhancedcelestialstweaks$primeFields() {
        try {
            Method m = this.getClass().getMethod("currentLunarEventHolder");
            Object current = m.invoke(this);

            Field lt = this.getClass().getDeclaredField("lastTickEvent");
            lt.setAccessible(true);
            lt.set(this, current);

            Field ls = this.getClass().getDeclaredField("lastStoredEvent");
            ls.setAccessible(true);
            ls.set(this, current);

            Field blend = this.getClass().getDeclaredField("blend");
            blend.setAccessible(true);
            blend.setFloat(this, 1.0F);
        } catch (Throwable ignored) {
        }
    }
}
