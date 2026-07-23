package com.sxilverr.enhancedcelestialstweaks.mixin;

import com.sxilverr.enhancedcelestialstweaks.BoolOverride;
import com.sxilverr.enhancedcelestialstweaks.ECTweaksApplier;
import com.sxilverr.enhancedcelestialstweaks.ECTweaksConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.corgitaco.enhancedcelestials.api.lunarevent.LunarDimensionSettings", remap = false)
public class MixinLunarDimSettings {

    @Inject(method = "requiresClearSkies", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhancedcelestialstweaks$skies(CallbackInfoReturnable<Boolean> cir) {
        if (!ECTweaksConfig.GENERAL.enabled) return;
        BoolOverride o = ECTweaksConfig.OVERWORLD.requireClearSkies;
        if (o == BoolOverride.TRUE) cir.setReturnValue(Boolean.TRUE);
        else if (o == BoolOverride.FALSE) cir.setReturnValue(Boolean.FALSE);
        if (ECTweaksApplier.activeEventForcesPrecipitation()) cir.setReturnValue(Boolean.FALSE);
    }

    @Inject(method = "dayLength", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhancedcelestialstweaks$dayLength(CallbackInfoReturnable<Long> cir) {
        if (!ECTweaksConfig.GENERAL.enabled) return;
        long val = ECTweaksConfig.OVERWORLD.dayLength;
        if (val >= 1) cir.setReturnValue(val);
    }

    @Inject(method = "yearLengthInDays", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhancedcelestialstweaks$yearLength(CallbackInfoReturnable<Long> cir) {
        if (!ECTweaksConfig.GENERAL.enabled) return;
        long val = ECTweaksConfig.OVERWORLD.yearLengthInDays;
        if (val >= 1) cir.setReturnValue(val);
    }

    @Inject(method = "minDaysBetweenEvents", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhancedcelestialstweaks$minDays(CallbackInfoReturnable<Long> cir) {
        if (!ECTweaksConfig.GENERAL.enabled) return;
        long val = ECTweaksConfig.OVERWORLD.minDaysBetweenEvents;
        if (val >= 0) cir.setReturnValue(val);
    }

    @Inject(method = "maxDaysBetweenEvents", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhancedcelestialstweaks$maxDays(CallbackInfoReturnable<Long> cir) {
        if (!ECTweaksConfig.GENERAL.enabled) return;
        long val = ECTweaksConfig.OVERWORLD.maxDaysBetweenEvents;
        if (val >= 0) cir.setReturnValue(val);
    }
}
