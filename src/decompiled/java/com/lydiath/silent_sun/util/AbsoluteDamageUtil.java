/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class AbsoluteDamageUtil {
    /** 绝对真实伤害标记：目标当前这一次 hurt 应锁定为的最终伤害值。 */
    public static final String ABSOLUTE_DAMAGE_KEY = "silent_sun:absolute_damage";
    /** 防重入标记：hurt() 内部递归触发本工具时直接放行。 */
    public static final String ABSOLUTE_DAMAGE_APPLYING_KEY = "silent_sun:absolute_damage_applying";
    /** 断魂伤害标记：目标当前这一次 hurt 应锁定的最终断魂伤害值（无 200 上限、无 5% 保底减免）。 */
    public static final String SOUL_SEVER_DAMAGE_KEY = "silent_sun:soul_sever_damage";
    /** 断魂伤害防重入标记。 */
    public static final String SOUL_SEVER_DAMAGE_APPLYING_KEY = "silent_sun:soul_sever_damage_applying";

    public static boolean damage(LivingEntity target, DamageSource source, float amount) {
        return AbsoluteDamageUtil.damage(target, source, amount, false);
    }

    /**
     * 2026-08-12：重载——允许调用方放行"创造模式玩家"的真伤。
     * <p>
     * 默认（{@code allowCreative=false}）保持原防作弊语义：创造/旁观者玩家免疫真伤。
     * Boss 的混沌/砺锋等测试刚需路径传 {@code redios.bossDamageCreative} 配置，
     * 使创造模式下也能直观验证真伤数值；旁观者无论开关如何都免疫。
     */
    public static boolean damage(LivingEntity target, DamageSource source, float amount, boolean allowCreative) {
        if (amount <= 0.0f) {
            return false;
        }
        if (target instanceof IAbsoluteDamageImmune) {
            return false; // Boss 免疫绝对伤害（断魂伤害）
        }
        if (target instanceof Player player && (player.isSpectator() || (player.isCreative() && !allowCreative))) {
            return false;
        }
        float adjusted = AbsoluteDamageUtil.adjustAbsoluteDamage(target, amount);
        if (adjusted <= 0.0f) {
            return false;
        }
        CompoundTag data = target.getPersistentData();
        if (data.getBoolean(ABSOLUTE_DAMAGE_APPLYING_KEY)) {
            return false; // 防重入
        }
        // 走 hurt() 完整死亡链路，避免 setHealth 旁门绕过掉落/死亡信息/LivingDeathEvent。
        // 标记用于在 LivingDamageEvent.Pre(LOWEST) 中把最终伤害锁回 adjusted，绕过护甲/附魔减免。
        int savedInvuln = target.invulnerableTime;
        data.putBoolean(ABSOLUTE_DAMAGE_APPLYING_KEY, true);
        data.putFloat(ABSOLUTE_DAMAGE_KEY, adjusted);
        boolean dealt;
        try {
            target.invulnerableTime = 0;
            dealt = target.hurt(source, adjusted);
        } finally {
            target.invulnerableTime = savedInvuln;
            data.remove(ABSOLUTE_DAMAGE_APPLYING_KEY);
            data.remove(ABSOLUTE_DAMAGE_KEY);
        }
        return dealt;
    }

    /** 目标当前是否正处于"绝对真实伤害"标记中（hurt 链路内）。 */
    public static boolean isAbsoluteDamageMarked(LivingEntity target) {
        return target.getPersistentData().getBoolean(ABSOLUTE_DAMAGE_APPLYING_KEY);
    }

    /** 读取当前"绝对真实伤害"标记锁定的最终伤害值。 */
    public static float getAbsoluteDamageValue(LivingEntity target) {
        return target.getPersistentData().getFloat(ABSOLUTE_DAMAGE_KEY);
    }

    /**
     * 断魂伤害专用通道：平铺 x+y 无视一切防护。
     * <p>
     * 与 {@link #damage} 的区别：不做 {@link #adjustAbsoluteDamage}（无 200 上限、无 5% 保底减免），
     * 伤害值（baseX + soulSeverY）原样锁定，保证断魂按设计值全额命中。
     * 仍走 hurt() 完整死亡链路，图腾 / 掉落 / 死亡信息正常触发。
     */
    public static boolean soulSeverDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f) {
            return false;
        }
        if (target instanceof IAbsoluteDamageImmune) {
            return false;
        }
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        CompoundTag data = target.getPersistentData();
        if (data.getBoolean(SOUL_SEVER_DAMAGE_APPLYING_KEY)) {
            return false;
        }
        int savedInvuln = target.invulnerableTime;
        data.putBoolean(SOUL_SEVER_DAMAGE_APPLYING_KEY, true);
        data.putFloat(SOUL_SEVER_DAMAGE_KEY, amount);
        boolean dealt;
        try {
            target.invulnerableTime = 0;
            dealt = target.hurt(source, amount);
        } finally {
            target.invulnerableTime = savedInvuln;
            data.remove(SOUL_SEVER_DAMAGE_APPLYING_KEY);
            data.remove(SOUL_SEVER_DAMAGE_KEY);
        }
        return dealt;
    }

    /** 目标当前是否正处于"断魂伤害"标记中（hurt 链路内）。 */
    public static boolean isSoulSeverDamageMarked(LivingEntity target) {
        return target.getPersistentData().getBoolean(SOUL_SEVER_DAMAGE_APPLYING_KEY);
    }

    /** 读取当前"断魂伤害"标记锁定的最终伤害值。 */
    public static float getSoulSeverDamageValue(LivingEntity target) {
        return target.getPersistentData().getFloat(SOUL_SEVER_DAMAGE_KEY);
    }

    private static float adjustAbsoluteDamage(LivingEntity target, float amount) {
        if (!(target instanceof IAbsoluteDamageImmune)) {
            float minMitigation = 0.05f;
            amount *= 1.0f - minMitigation;
            amount = Math.min(amount, 200.0f);
        }
        return amount;
    }

    private AbsoluteDamageUtil() {
    }
}
