package com.lydiath.soulsever;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 断魂效果 — 基于目标身上的断魂附加值，在受击后追加绝对伤害。
 * <p>
 * 断魂效果本身只是一个标记效果（NEUTRAL，深紫色 #4B1F7D），
 * 实际的伤害追加逻辑由 {@link SoulSeverEvents#onLivingDamagePost} 处理。
 */
public final class SoulSeverEffect extends MobEffect {
    public SoulSeverEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
