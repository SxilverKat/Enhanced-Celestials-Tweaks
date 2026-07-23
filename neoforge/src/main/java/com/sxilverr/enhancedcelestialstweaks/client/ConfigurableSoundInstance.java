package com.sxilverr.enhancedcelestialstweaks.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class ConfigurableSoundInstance extends AbstractTickableSoundInstance {

    public ConfigurableSoundInstance(SoundEvent sound, float volume, float pitch, boolean loop) {
        super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.volume = volume;
        this.pitch = pitch;
        this.looping = loop;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
    }

    public void fadeOut() {
        this.stop();
    }
}
