package com.sxilverr.enhancedcelestialstweaks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = "enhancedcelestialstweaks")
public final class ECTweaksEvents {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ECTweaksApplier.serverAboutToStart(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ECTweaksApplier.serverStarting(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ECTweaksApplier.serverStopped();
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (ECTweaksApplier.finalizeSpawn(event.getEntity(), event.getDifficulty())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onMobDespawn(MobDespawnEvent event) {
        if (ECTweaksApplier.shouldPreventDespawn(event.getEntity())) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            ECTweaksApplier.livingTick(living);
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        ECTweaksApplier.livingDrops(event.getEntity(), event.getDrops());
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        int base = event.getDroppedExperience();
        int modified = ECTweaksApplier.modifyXpDrop(event.getEntity(), base);
        if (modified != base) {
            event.setDroppedExperience(modified);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel sLevel) {
            ECTweaksApplier.levelTick(sLevel);
        }
    }

    private ECTweaksEvents() {
    }
}
