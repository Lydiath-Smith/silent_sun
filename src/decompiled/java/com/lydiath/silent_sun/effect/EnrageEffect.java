package com.lydiath.silent_sun.effect;

import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class EnrageEffect extends MobEffect {
    private static final ResourceLocation MOVE_SPEED_ID = ResourceLocation.fromNamespaceAndPath("silent_sun", "enrage_move_speed");
    private static final ResourceLocation ATTACK_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("silent_sun", "enrage_attack_damage");

    /**
     * 触发脆弱效果的激怒等级阈值。
     * <p>
     * RediosEntity 的激怒等级上限为 9（grantEnrageLevels 内 Math.min(9, ...)），
     * 因此阈值必须 ≤ 9，否则该逻辑永不触发。满层（amplifier=9）后继续叠加
     * 激怒即施加第一层脆弱。
     */
    public static final int FRAGILE_TRIGGER_LEVEL = 9;

    public EnrageEffect(MobEffectCategory category, int color) {
        super(category, color);
        Int2DoubleFunction perLevel = amplifier -> 0.3 * (double)(amplifier + 1);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MOVE_SPEED_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, perLevel);
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, perLevel);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /**
     * 激怒满层（10级）后继续获取激怒时，对持有者施加脆弱效果。
     * 由 RediosEntity 在叠加激怒时检查等级并调用。
     */
    public static void applyFragileIfEnraged(LivingEntity enragedEntity, int enrageAmplifier) {
        if (enrageAmplifier >= FRAGILE_TRIGGER_LEVEL) {
            int fragileAmp = enrageAmplifier - FRAGILE_TRIGGER_LEVEL;
            if (fragileAmp > 255) fragileAmp = 255;
            MobEffectInstance existing = enragedEntity.getEffect(
                com.lydiath.silent_sun.registry.ModEffects.FRAGILE);
            int newAmp = (existing != null) ? existing.getAmplifier() + 1 : 0;
            if (newAmp > 255) newAmp = 255;
            enragedEntity.addEffect(new MobEffectInstance(
                com.lydiath.silent_sun.registry.ModEffects.FRAGILE,
                MobEffectInstance.INFINITE_DURATION, newAmp, false, true));
        }
    }
}
