/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create((ResourceKey)Registries.SOUND_EVENT, "silent_sun");
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE1 = SOUND_EVENTS.register("redios_battle_music_phase1", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase1")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE2 = SOUND_EVENTS.register("redios_battle_music_phase2", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase2")));
    public static final DeferredHolder<SoundEvent, SoundEvent> STARFALL_SALVO_PRE_EXPLOSION = SOUND_EVENTS.register("starfall_salvo_pre_explosion", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "starfall_salvo_pre_explosion")));

    private ModSounds() {
    }
}

