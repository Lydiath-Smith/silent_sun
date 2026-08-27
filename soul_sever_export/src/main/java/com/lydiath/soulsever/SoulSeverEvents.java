package com.lydiath.soulsever;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 断魂效果的核心事件处理。
 * <p>
 * 机制说明：
 * <ol>
 *   <li>外部系统（如 Boss）调用 {@link #addSoulSeverBonus(LivingEntity, long)} 为目标叠加断魂附加值。</li>
 *   <li>目标受击后（{@link LivingDamageEvent.Post}），根据断魂附加值追加等量绝对伤害。</li>
 *   <li>支持"砺锋尝胆"机制：通过 {@link #markSharpenSoulSever} 标记玩家、每 tick 刷新断魂效果等级。</li>
 * </ol>
 */
@EventBusSubscriber(modid = SoulSeverMod.MOD_ID)
public final class SoulSeverEvents {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, SoulSeverMod.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> SOUL_SEVER =
        MOB_EFFECTS.register("soul_sever", () -> new SoulSeverEffect(MobEffectCategory.NEUTRAL, 0x4B1F7D));

    // ── PersistentData keys ──
    private static final String SOUL_SEVER_BONUS_KEY = SoulSeverMod.MOD_ID + ":soul_sever_bonus";
    private static final String SOUL_SEVER_APPLYING_KEY = SoulSeverMod.MOD_ID + ":soul_sever_applying";
    private static final String SHARPEN_SOUL_SEVER_BOSS_KEY = SoulSeverMod.MOD_ID + ":sharpen_soul_sever_boss";
    private static final String SHARPEN_SOUL_SEVER_AMP_KEY = SoulSeverMod.MOD_ID + ":sharpen_soul_sever_amp";

    // ── Public API ──

    /**
     * 为目标叠加断魂附加值（x+y 中的 y 值）。
     * 调用方（如 Boss）应在受击事件中调用此方法。
     */
    public static void addSoulSeverBonus(LivingEntity target, long amount) {
        if (amount <= 0L || target.getEffect(SOUL_SEVER) == null) return;
        CompoundTag data = target.getPersistentData();
        long current = data.getLong(SOUL_SEVER_BONUS_KEY);
        data.putLong(SOUL_SEVER_BONUS_KEY, Math.min(current + amount, 1_000_000_000L));
    }

    /** 标记玩家处于"砺锋尝胆"断魂锁定状态，每 tick 自动刷新断魂效果。 */
    public static void markSharpenSoulSever(LivingEntity target, Entity boss, int amplifier) {
        CompoundTag data = target.getPersistentData();
        data.putUUID(SHARPEN_SOUL_SEVER_BOSS_KEY, boss.getUUID());
        data.putInt(SHARPEN_SOUL_SEVER_AMP_KEY, amplifier);
    }

    /** 清除"砺锋尝胆"标记并移除断魂效果。 */
    public static void clearSharpenSoulSever(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        data.remove(SHARPEN_SOUL_SEVER_BOSS_KEY);
        data.remove(SHARPEN_SOUL_SEVER_AMP_KEY);
        target.removeEffect(SOUL_SEVER);
        data.remove(SOUL_SEVER_BONUS_KEY);
        data.remove(SOUL_SEVER_APPLYING_KEY);
    }

    /** 检查玩家是否被指定 Boss 标记了砺锋尝胆。 */
    public static boolean isSharpenSoulSeverMarked(LivingEntity target, Entity boss) {
        CompoundTag data = target.getPersistentData();
        return data.hasUUID(SHARPEN_SOUL_SEVER_BOSS_KEY)
            && boss.getUUID().equals(data.getUUID(SHARPEN_SOUL_SEVER_BOSS_KEY));
    }

    // ── Event handlers ──

    /**
     * 受击后追加断魂绝对伤害。
     * <p>
     * 从目标的 PersistentData 读取断魂附加值，以 magic 伤害源
     * 通过 {@link AbsoluteDamageUtil} 施加等量绝对伤害。
     * 使用 SOUL_SEVER_APPLYING_KEY 防重入。
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        CompoundTag data = target.getPersistentData();
        MobEffectInstance effect = target.getEffect(SOUL_SEVER);
        if (effect == null) {
            data.remove(SOUL_SEVER_BONUS_KEY);
            data.remove(SOUL_SEVER_APPLYING_KEY);
            return;
        }
        if (data.getBoolean(SOUL_SEVER_APPLYING_KEY)) {
            return; // 防重入
        }
        long bonus = data.getLong(SOUL_SEVER_BONUS_KEY);
        if (bonus <= 0L) return;

        float appliedBonus = bonus >= 1_000_000_000L ? 1.0E9f : (float) bonus;
        data.putBoolean(SOUL_SEVER_APPLYING_KEY, true);
        try {
            AbsoluteDamageUtil.damage(target, target.damageSources().magic(), appliedBonus);
        } finally {
            data.putBoolean(SOUL_SEVER_APPLYING_KEY, false);
        }
    }

    /**
     * 每 tick 刷新砺锋尝胆标记玩家的断魂效果等级。
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        CompoundTag data = event.getEntity().getPersistentData();
        if (!data.hasUUID(SHARPEN_SOUL_SEVER_BOSS_KEY)) return;

        Entity boss = event.getEntity().level().getEntity(data.getUUID(SHARPEN_SOUL_SEVER_BOSS_KEY));
        if (boss == null || !boss.isAlive()) {
            clearSharpenSoulSever(event.getEntity());
            return;
        }

        int amp = data.getInt(SHARPEN_SOUL_SEVER_AMP_KEY);
        MobEffectInstance current = event.getEntity().getEffect(SOUL_SEVER);
        if (current == null || current.getAmplifier() < amp) {
            event.getEntity().addEffect(new MobEffectInstance(SOUL_SEVER, 40, amp, true, true));
        } else if (current.getDuration() < 20) {
            event.getEntity().addEffect(new MobEffectInstance(SOUL_SEVER, 40, current.getAmplifier(), true, true));
        }
    }
}
