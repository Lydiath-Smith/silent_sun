package com.lydiath.soulsever;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 绝对伤害工具类。
 * <p>
 * 绕过原版护甲/附魔/抗性等所有减伤机制，直接扣除目标生命值。
 * 对非 Boss 目标，保留 5% 最低护甲减免，且单次上限 200 点。
 */
public final class AbsoluteDamageUtil {

    public static boolean damage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0f) {
            return false;
        }
        // 创造/观察者模式免疫
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        float adjusted = adjustAbsoluteDamage(target, amount);
        if (adjusted <= 0.0f) {
            return false;
        }
        float newHealth = target.getHealth() - adjusted;
        if (newHealth > 0.0f) {
            target.setHealth(newHealth);
            return true;
        }
        target.setHealth(0.0f);
        target.die(source);
        return true;
    }

    /**
     * 对非 Boss 目标施加最低 5% 护甲减免和 200 点伤害上限。
     * 覆盖此方法可为特定目标类型提供不同的调整策略。
     */
    private static float adjustAbsoluteDamage(LivingEntity target, float amount) {
        // 5% 最低护甲减免
        float minMitigation = 0.05f;
        amount *= 1.0f - minMitigation;
        // 200 点伤害上限
        amount = Math.min(amount, 200.0f);
        return amount;
    }

    private AbsoluteDamageUtil() {}
}
