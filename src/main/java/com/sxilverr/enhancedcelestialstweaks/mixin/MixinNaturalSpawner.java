package com.sxilverr.enhancedcelestialstweaks.mixin;

import com.sxilverr.enhancedcelestialstweaks.ECTweaksApplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NaturalSpawner.class, remap = false)
public class MixinNaturalSpawner {

    @Inject(method = "m_47029_", at = @At("HEAD"))
    private static void enhancedcelestialstweaks$beginSpawnContext(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, boolean spawnFriendlies, boolean spawnMonsters, boolean forcedDespawn, CallbackInfo ci) {
        ECTweaksApplier.beginSpawnContext(level);
    }

    @Inject(method = "m_47029_", at = @At("RETURN"))
    private static void enhancedcelestialstweaks$endSpawnContext(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, boolean spawnFriendlies, boolean spawnMonsters, boolean forcedDespawn, CallbackInfo ci) {
        ECTweaksApplier.endSpawnContext();
    }
}
