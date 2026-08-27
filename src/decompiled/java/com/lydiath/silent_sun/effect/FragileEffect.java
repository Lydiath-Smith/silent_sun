package com.lydiath.silent_sun.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 脆弱效果 —— 激怒满层（10级）后继续叠加激怒时触发。
 * 每级增加受击者受到的伤害百分比。
 * 持续时间与 Boss 激怒效果绑定。
 */
public final class FragileEffect extends MobEffect {
    public FragileEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
