package com.sxilverr.enhancedcelestialstweaks.mixin;

import com.sxilverr.enhancedcelestialstweaks.ECTweaksApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
public abstract class MixinMonster {

    @Inject(method = "isDarkEnoughToSpawn", at = @At("RETURN"), cancellable = true)
    private static void enhancedcelestialstweaks$monsterLightLevel(ServerLevelAccessor level, BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (ECTweaksApplier.allowMonsterSpawnAtLight(level, pos)) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
