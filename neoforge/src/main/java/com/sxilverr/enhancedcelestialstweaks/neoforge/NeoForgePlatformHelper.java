package com.sxilverr.enhancedcelestialstweaks.neoforge;

import com.sxilverr.enhancedcelestialstweaks.platform.IPlatformHelper;
import net.minecraft.core.Holder;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.lang.reflect.Method;

public final class NeoForgePlatformHelper implements IPlatformHelper {

    private static final ResourceLocation HEALTH = ResourceLocation.fromNamespaceAndPath("enhancedcelestialstweaks", "health");
    private static final ResourceLocation DAMAGE = ResourceLocation.fromNamespaceAndPath("enhancedcelestialstweaks", "damage");
    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath("enhancedcelestialstweaks", "speed");
    private static final ResourceLocation SWIM = ResourceLocation.fromNamespaceAndPath("enhancedcelestialstweaks", "swim");

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
        apply(entity, Attributes.MAX_HEALTH, HEALTH, healthMul);
        apply(entity, Attributes.ATTACK_DAMAGE, DAMAGE, damageMul);
        apply(entity, Attributes.MOVEMENT_SPEED, SPEED, speedMul);
        apply(entity, NeoForgeMod.SWIM_SPEED, SWIM, swimMul);
    }

    private static void apply(LivingEntity entity, Holder<Attribute> attr, ResourceLocation id, double mul) {
        if (mul == 1.0 || attr == null) return;
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, mul - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public boolean applyMobEffect(LivingEntity entity, ResourceLocation effectId, int amplifier, int duration) {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null);
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
        synchronized (NeoForgePlatformHelper.class) {
            if (populateTried) return populate;
            try {
                populate = Mob.class.getDeclaredMethod("populateDefaultEquipmentSlots", RandomSource.class, DifficultyInstance.class);
                populate.setAccessible(true);
            } catch (Throwable t) {
                populate = null;
            }
            populateTried = true;
            return populate;
        }
    }
}
