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
    /**
     * 走 {@code ServerLevel.playSound} 广播的音效的可闻半径（2026-09-11 代码审计 G01 #1 修复）。
     * <p>
     * 必须用 {@link SoundEvent#createFixedRangeEvent}：原版 {@code SoundEvent.getRange(volume)} 对
     * variable-range 事件返回 {@code volume > 1.0F ? 16.0F * volume : 16.0F} —— 而这两个音效的调用处
     * volume 恒为 1.0 ⇒ 广播半径**恒为 16 格**；{@code sounds.json} 里的 {@code attenuation_distance}
     * 只决定「客户端收到包之后的通道衰减」，**不参与服务端广播半径**。于是「全图可闻」的配置实际
     * 效果是「16 格内满音量、16 格外完全无声」，而参战半径默认就有 72 格。
     * <p>
     * 取 72 与 {@code RediosRules.battleRadiusBlocks()} 默认值对齐（本类是静态注册，读不到运行时配置）。
     * 注：战斗音乐的 8 个事件**不需要**改 —— 它们由 {@code RediosEntity} 直接向参战者发
     * {@code ClientboundSoundPacket}，不经过 {@code playSound} 的半径广播。
     */
    private static final float BROADCAST_SOUND_RANGE_BLOCKS = 72.0f;

    public static final DeferredHolder<SoundEvent, SoundEvent> STARFALL_SALVO_PRE_EXPLOSION = SOUND_EVENTS.register("starfall_salvo_pre_explosion", () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath("silent_sun", "starfall_salvo_pre_explosion"), BROADCAST_SOUND_RANGE_BLOCKS));
    /** 召唤高潮音效（2026-09-08）：音频复用原版末地传送门生成，字幕为模组自有键「宝贝我是如此的爱你」。
     *  <p>固定范围事件，理由见 {@link #BROADCAST_SOUND_RANGE_BLOCKS}。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> REDIOS_SUMMON = SOUND_EVENTS.register("redios_summon", () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_summon"), BROADCAST_SOUND_RANGE_BLOCKS));

    private ModSounds() {
    }
}

