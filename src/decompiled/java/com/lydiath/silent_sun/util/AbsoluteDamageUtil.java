/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.util;

import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AbsoluteDamageUtil {
    /** 绝对真实伤害标记：目标当前这一次 hurt 应锁定为的最终伤害值。 */
    public static final String ABSOLUTE_DAMAGE_KEY = "silent_sun:absolute_damage";
    /** 防重入标记：hurt() 内部递归触发本工具时直接放行。 */
    public static final String ABSOLUTE_DAMAGE_APPLYING_KEY = "silent_sun:absolute_damage_applying";
    /** 断魂伤害标记：目标当前这一次 hurt 应锁定的最终断魂伤害值（无 200 上限、无 5% 保底减免）。 */
    public static final String SOUL_SEVER_DAMAGE_KEY = "silent_sun:soul_sever_damage";
    /** 断魂伤害防重入标记。 */
    public static final String SOUL_SEVER_DAMAGE_APPLYING_KEY = "silent_sun:soul_sever_damage_applying";

    // ─────────────────────────────────────────────────────────────────────────
    // 2026-09-11 用户裁决（C1 → 选项 A）：标记载体由「实体持久数据 ForgeData」改为**本模组内存映射**。
    // 原实现把标记写进 ForgeData，而键是 public 常量 → 任何模组 / 数据包 / op 只要往实体上写
    // `silent_sun:absolute_damage_applying=true` + `silent_sun:absolute_damage=<大数>`，之后任意
    // 一次命中就会把最终伤害改写成那个值，**绕过全部 22 条管线阶段 + 锁血 + 防死拦截（一击致死）**。
    // 改到内存后第三方无法写入；hurt → LivingDamageEvent.Pre 是服务器线程内的同步调用，
    // 写入 / 读取 / 清除都发生在同一次 damage() 调用内，语义与原先完全等价。
    // NBT 键常量保留（第三方可能按老约定读取），但已不再作为信任载体。
    // ─────────────────────────────────────────────────────────────────────────
    /** 非 Boss 来源绝对伤害的保底减免（5%）与硬上限（默认 200，见 {@link #adjustAbsoluteDamage}）。 */
    private static final float ABSOLUTE_MIN_MITIGATION = 0.05f;
    private static final float ABSOLUTE_MAX_DAMAGE = 200.0f;

    private static final Map<LivingEntity, Float> PENDING_ABSOLUTE = new ConcurrentHashMap<LivingEntity, Float>();
    private static final Set<LivingEntity> ABSOLUTE_APPLYING = ConcurrentHashMap.newKeySet();
    private static final Map<LivingEntity, Float> PENDING_SEVER = new ConcurrentHashMap<LivingEntity, Float>();
    private static final Set<LivingEntity> SEVER_APPLYING = ConcurrentHashMap.newKeySet();

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
        float adjusted = AbsoluteDamageUtil.adjustAbsoluteDamage(source, target, amount);
        if (adjusted <= 0.0f) {
            return false;
        }
        if (ABSOLUTE_APPLYING.contains(target)) {
            return false; // 防重入
        }
        // 走 hurt() 完整死亡链路，避免 setHealth 旁门绕过掉落/死亡信息/LivingDeathEvent。
        // 标记用于在 LivingDamageEvent.Pre(LOWEST) 中把最终伤害锁回 adjusted，绕过护甲/附魔减免。
        // 2026-09-11：标记改存内存（见文件顶部说明），第三方无法伪造。
        int savedInvuln = target.invulnerableTime;
        ABSOLUTE_APPLYING.add(target);
        PENDING_ABSOLUTE.put(target, Float.valueOf(adjusted));
        boolean dealt;
        try {
            target.invulnerableTime = 0;
            dealt = target.hurt(source, adjusted);
        } finally {
            target.invulnerableTime = savedInvuln;
            ABSOLUTE_APPLYING.remove(target);
            PENDING_ABSOLUTE.remove(target);
        }
        return dealt;
    }

    /** 目标当前是否正处于"绝对真实伤害"标记中（hurt 链路内）。 */
    public static boolean isAbsoluteDamageMarked(LivingEntity target) {
        return target != null && ABSOLUTE_APPLYING.contains(target);
    }

    /** 读取当前"绝对真实伤害"标记锁定的最终伤害值。 */
    public static float getAbsoluteDamageValue(LivingEntity target) {
        Float value = target == null ? null : PENDING_ABSOLUTE.get(target);
        return value == null ? 0.0f : value.floatValue();
    }

    /**
     * 断魂伤害专用通道：平铺 x+y 无视一切防护。
     * <p>
     * 与 {@link #damage} 的区别：不做 {@link #adjustAbsoluteDamage}（无 200 上限、无 5% 保底减免），
     * 伤害值（baseX + soulSeverY）原样锁定，保证断魂按设计值全额命中。
     * 仍走 hurt() 完整死亡链路，图腾 / 掉落 / 死亡信息正常触发。
     * <p>
     * ⚠️ 2026-09-11（代码审计 G04 #1）：本方法**全库零调用者**，属未接线能力。连带后果是
     * {@code CommonEvents.onSoulSeverDamageLockPre} / {@code ...Fallback} 两条监听器里的
     * {@code isSoulSeverDamageMarked} 分支恒不执行（断魂伤害目前走 {@link #damage} 的绝对伤害链）。
     * 是「接线（让断魂加点改走本通道，复活独立的『断魂限伤 + 硬核 1 血锁』锁定链）」还是
     * 「删除本方法 + 两条监听器（会使该链彻底消失）」，需作者裁决 —— 已登记在
     * {@code docs/实现计划-2026-09-11-E-1剩余中危批次.md} §三，本次不动逻辑。
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
        if (SEVER_APPLYING.contains(target)) {
            return false;
        }
        int savedInvuln = target.invulnerableTime;
        SEVER_APPLYING.add(target);
        PENDING_SEVER.put(target, Float.valueOf(amount));
        boolean dealt;
        try {
            target.invulnerableTime = 0;
            dealt = target.hurt(source, amount);
        } finally {
            target.invulnerableTime = savedInvuln;
            SEVER_APPLYING.remove(target);
            PENDING_SEVER.remove(target);
        }
        return dealt;
    }

    /** 目标当前是否正处于"断魂伤害"标记中（hurt 链路内）。 */
    public static boolean isSoulSeverDamageMarked(LivingEntity target) {
        return target != null && SEVER_APPLYING.contains(target);
    }

    /** 读取当前"断魂伤害"标记锁定的最终伤害值。 */
    public static float getSoulSeverDamageValue(LivingEntity target) {
        Float value = target == null ? null : PENDING_SEVER.get(target);
        return value == null ? 0.0f : value.floatValue();
    }

    /**
     * 真伤保底减免与硬上限。
     * <p>
     * 2026-09-11（代码审计 G02 #2 / G04 #2 修复，依设计稿 §7.1 A1）：<b>仅 Boss 的绝对伤害无视
     * §3.1 全局「硬上限 200 + 动态减伤」</b>，其它来源的绝对伤害仍受 200 上限约束。
     * <p>
     * 原实现对**所有**绝对伤害一律乘 0.95 再钳 200 —— 包括 Boss → 玩家的断魂/混沌真伤，
     * 与 A1 直接矛盾（§7.1 开头已声明「凡与既有正文冲突处，以本章为准」）。
     * <p>
     * 注意玩家 → Boss 的真伤本来就在 {@link #damage} 开头的 {@code IAbsoluteDamageImmune}
     * 分支早退，其 200 上限由 {@code RediosEntity.applyDamageCap}（读 {@code redios.damageHardCap}）承担。
     */
    private static float adjustAbsoluteDamage(DamageSource source, LivingEntity target, float amount) {
        if (target instanceof IAbsoluteDamageImmune) {
            return amount; // 防御性：damage() 开头已对免疫目标早退，正常不可达
        }
        if (source != null && source.getEntity() instanceof RediosEntity) {
            return amount; // 设计稿 §7.1 A1：Boss 的绝对伤害无视全局硬上限与保底减免
        }
        amount *= 1.0f - ABSOLUTE_MIN_MITIGATION;
        return Math.min(amount, ABSOLUTE_MAX_DAMAGE);
    }

    private AbsoluteDamageUtil() {
    }
}
