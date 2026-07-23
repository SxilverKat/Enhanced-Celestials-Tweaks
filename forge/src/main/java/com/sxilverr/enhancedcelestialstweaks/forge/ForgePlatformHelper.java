package com.sxilverr.enhancedcelestialstweaks.forge;

import com.sxilverr.enhancedcelestialstweaks.platform.IPlatformHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ForgePlatformHelper implements IPlatformHelper {

    private static final UUID HEALTH = UUID.fromString("9b62de1f-1100-4001-9001-000000000001");
    private static final UUID DAMAGE = UUID.fromString("9b62de1f-1100-4001-9001-000000000002");
    private static final UUID SPEED = UUID.fromString("9b62de1f-1100-4001-9001-000000000003");
    private static final UUID SWIM = UUID.fromString("9b62de1f-1100-4001-9001-000000000004");

    private static volatile Method populate;
    private static volatile boolean populateTried;

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public CompoundTag getPersistentData(Entity entity) {
        return entity.getPersistentData();
    }

    @Override
    public void applyMultiplierAttributes(LivingEntity entity, double healthMul, double damageMul, double speedMul, double swimMul) {
        apply(entity, Attributes.MAX_HEALTH, HEALTH, "ec_tweaks.health", healthMul);
        apply(entity, Attributes.ATTACK_DAMAGE, DAMAGE, "ec_tweaks.damage", damageMul);
        apply(entity, Attributes.MOVEMENT_SPEED, SPEED, "ec_tweaks.speed", speedMul);
        Attribute swim = ForgeMod.SWIM_SPEED.get();
        if (swim != null) apply(entity, swim, SWIM, "ec_tweaks.swim", swimMul);
    }

    private static void apply(LivingEntity entity, Attribute attr, UUID id, String name, double mul) {
        if (mul == 1.0 || attr == null) return;
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, name, mul - 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    @Override
    public boolean applyMobEffect(LivingEntity entity, ResourceLocation effectId, int amplifier, int duration) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(effectId);
        if (effect == null) return false;
        entity.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, false));
        return true;
    }

    @Override
    public boolean populateEquipment(Mob mob, RandomSource random, DifficultyInstance difficulty) {
        Method m = method();
        if (m == null) return false;
        try {
            m.invoke(mob, random, difficulty);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method method() {
        if (populateTried) return populate;
        synchronized (ForgePlatformHelper.class) {
            if (populateTried) return populate;
            try {
                populate = ObfuscationReflectionHelper.findMethod(Mob.class, "m_213945_", RandomSource.class, DifficultyInstance.class);
            } catch (Throwable t) {
                try {
                    populate = ObfuscationReflectionHelper.findMethod(Mob.class, "populateDefaultEquipmentSlots", RandomSource.class, DifficultyInstance.class);
                } catch (Throwable t2) {
                    populate = null;
                }
            }
            populateTried = true;
            return populate;
        }
    }
}
