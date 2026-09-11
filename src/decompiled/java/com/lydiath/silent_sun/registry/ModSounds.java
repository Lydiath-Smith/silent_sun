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
    // A-1（2026-09-11）：战斗音乐 intro / loop / outro 三段，每阶段一套共 6 个音效事件。
    // 对应的 ogg 与 sounds.json 条目同名；attenuation_distance 见 sounds.json（须与
    // RediosEntity 的音乐可听半径一致，后者读 RediosRules.battleRadiusBlocks()）。
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE1_INTRO = SOUND_EVENTS.register("redios_battle_music_phase1_intro", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase1_intro")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE1_LOOP = SOUND_EVENTS.register("redios_battle_music_phase1_loop", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase1_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE1_OUTRO = SOUND_EVENTS.register("redios_battle_music_phase1_outro", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase1_outro")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE2_INTRO = SOUND_EVENTS.register("redios_battle_music_phase2_intro", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase2_intro")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE2_LOOP = SOUND_EVENTS.register("redios_battle_music_phase2_loop", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase2_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_BATTLE_MUSIC_PHASE2_OUTRO = SOUND_EVENTS.register("redios_battle_music_phase2_outro", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_battle_music_phase2_outro")));
    public static final DeferredHolder<SoundEvent, SoundEvent> STARFALL_SALVO_PRE_EXPLOSION = SOUND_EVENTS.register("starfall_salvo_pre_explosion", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "starfall_salvo_pre_explosion")));
    /** 召唤高潮音效（2026-09-08）：音频复用原版末地传送门生成，字幕为模组自有键「宝贝我是如此的爱你」。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_SUMMON = SOUND_EVENTS.register("redios_summon", () -> SoundEvent.createVariableRangeEvent((ResourceLocation)ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_summon")));

    private ModSounds() {
    }
}

