package com.sxilverr.enhancedcelestialstweaks.mixin;

import com.sxilverr.enhancedcelestialstweaks.ECTweaksApplier;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NaturalSpawner.SpawnState.class)
public class MixinNaturalSpawnerState {

    @Inject(method = "canSpawnForCategory", at = @At("HEAD"), cancellable = true)
    private void enhancedcelestialstweaks$mobCap(MobCategory category, ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        double mult = ECTweaksApplier.contextMobCapMultiplier();
        if (mult == 1.0) return;
        if (mult < 0.0) {
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        NaturalSpawner.SpawnState self = (NaturalSpawner.SpawnState) (Object) this;
        int cap = (int) (category.getMaxInstancesPerChunk() * self.getSpawnableChunkCount() / 289.0 * mult);
        cir.setReturnValue(self.getMobCategoryCounts().getInt(category) < cap);
    }
}
