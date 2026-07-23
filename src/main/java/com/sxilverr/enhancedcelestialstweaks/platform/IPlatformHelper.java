package com.sxilverr.enhancedcelestialstweaks.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public interface IPlatformHelper {

    boolean isModLoaded(String modId);

    CompoundTag getPersistentData(Entity entity);

    void applyMultiplierAttributes(LivingEntity entity, double healthMul, double damageMul, double speedMul, double swimMul);

    boolean applyMobEffect(LivingEntity entity, ResourceLocation effectId, int amplifier, int duration);

    boolean populateEquipment(Mob mob, RandomSource random, DifficultyInstance difficulty);
}
