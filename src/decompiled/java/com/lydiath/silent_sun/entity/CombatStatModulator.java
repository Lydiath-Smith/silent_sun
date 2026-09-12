package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.registry.ModEffects;
import com.lydiath.silent_sun.rules.RediosRules;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 战斗属性调制器：攻击冷却（攻速）、攻击范围、激怒等级**三个读数的唯一出口**。
 * <p>
 * 由 RediosEntity 薄委托调用，逻辑自 RediosEntity 原样搬迁，不改变算法。
 * 攻击冷却轨是攻速的唯一真相；vanilla {@code ATTACK_SPEED} 属性降级为稳定基线
 * （不再被激怒乘爆），杜绝「属性轨 vs 自定义冷却轨」双轨脱钩。
 * <p>
 * 2026-09-12（审计清理 G06 #7）：原文写「攻击冷却、攻击范围、移动速度、激怒等级四个读数的唯一出口」——
 * 其中「移动速度」由 {@code moveSpeed()} 承担，而该方法全库零调用者，已删除，故读数由四个订正为三个。
 * 移动速度的实际取值在索敌 / 移动 Goal 内自行读取（如 RediosEntity 内的局部 {@code speed}），不在本类出口内。
 */
final class CombatStatModulator {

    /** P2 失控疾驰攻速冷却（tick）：用户裁决为「寻常拔刀剑攻速两倍半」。 */
    private static final int P2_SPRINT_ATTACK_COOLDOWN_TICKS = 5;

    // 2026-09-12（审计清理 G06 #7）：此处原有死常量 `BLADE_BASE_ATTACK_COOLDOWN_TICKS = 5`
    // （注释自称「灭刀·断·试做原版攻速 4.0 → 20/4.0 = 5 tick」），已删除 —— 全库检索确认零引用
    // （唯一命中即其自身声明行），改它不会产生任何行为变化。
    // ⚠️ 同义量仍以内联字面量硬编码在 attackCooldownTicks() 里（`20.0 / (4.0 * speed)` 的 4.0）
    // —— 该处本轮**刻意不动**（把基准攻速提名为常量的重构属独立议题），故删除后此值仍是单一来源的字面量。

    /** 解除攻速限制后的冷却下限（tick）：原版攻速 ×2 ≈ 3 tick 封顶（用户裁决）。 */
    private static final int BLADE_MAX_SPEED_COOLDOWN_TICKS = 3;

    /** 激怒攻速加成系数：用户裁决「加成只有原来的 20%」（原每级 +0.3 → 现每级 +0.06）。 */
    private static final double ENRAGE_SPEED_BONUS_FACTOR = 0.3 * 0.2;

    private final RediosEntity host;

    CombatStatModulator(RediosEntity host) {
        this.host = host;
    }

    /** 攻击冷却（tick）：近战与拔刀剑实体斩击的统一节奏源。
     *  基准 = 灭刀·断·试做原版攻速 4.0（5 tick）；激怒加成仅保留原 20%；
     *  解除限制后冷却下限 3 tick（= 原版攻速 ×2 封顶）。 */
    int attackCooldownTicks() {
        if (this.host.weaponWeakpointSlowTicks > 0) {
            return Math.max(1, RediosRules.weaponWeakpointFixedCooldown());
        }
        if (this.host.isUncontrolledSprintActive()) {
            return P2_SPRINT_ATTACK_COOLDOWN_TICKS;
        }
        int level = this.enrageLevel();
        double speed = 1.0 + ENRAGE_SPEED_BONUS_FACTOR * (double) level;
        int base = Math.max(1, (int) Math.round(20.0 / (4.0 * speed)));
        int jitter = this.host.getRandom().nextInt(2);
        return Math.max(BLADE_MAX_SPEED_COOLDOWN_TICKS, base + jitter);
    }

    /** 攻击范围（格）：基础值 + 激怒等级加成（上限 +3）。 */
    double attackReach() {
        return (Double) SilentSunConfig.BASE_ATTACK_REACH.get() + (double) Math.min(3, this.enrageLevel());
    }

    /** 激怒等级：读 ENRAGE 效果放大器，范围 0~10。 */
    int enrageLevel() {
        MobEffectInstance effect = this.host.getEffect((Holder) ModEffects.ENRAGE);
        if (effect == null) {
            return 0;
        }
        return Math.min(10, effect.getAmplifier() + 1);
    }

    // 2026-09-12（审计清理 G06 #7）：删除 `double moveSpeed() { return this.host.getAttributeValue(Attributes.MOVEMENT_SPEED); }`
    // —— 全库检索确认零调用者（同名命中只有 RediosEntity 内一个无关的局部变量 `double moveSpeed`）。
    // 移动速度实际由索敌 / 移动 Goal 自行读取实体属性与自身 speed，不经本类出口；
    // 保留该方法会让「本类是四读数唯一出口」的约定失真（后续有人按注释来此收敛读取只会拿到死方法）。
    // 连带：随之失去唯一用法的 `Attributes` import 已一并移除；类注释中的「四个读数」已订正为三个。
    // 依据：docs\_审计-2026-09-11\G06.md §7（本轮已回源码复核引用计数）。
}
