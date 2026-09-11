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
    // TODO(审计清理 G09 #2)：激怒等级两套口径（amplifier 0~9 / level 1~10）裸值 9/10 分散 4 处 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
    public static final int FRAGILE_TRIGGER_LEVEL = 9;

    /**
     * 2026-09-10（用户裁决）：脆弱**等级上限 10**（设计稿 §3.9 / D3 / §6.4 原写 255，以本次裁定为准）。
     * <p>
     * 等级 = 游戏内显示层数 = amplifier + 1（见 {@code CommonEvents.applyFragileDamage} 的
     * {@code (amp+1)×5%}），故 amplifier 上限 = {@link #FRAGILE_MAX_AMPLIFIER}，
     * 追伤最高 = 10 × 5% = **+50%**（而非原设计的 +1280%）。
     */
    public static final int FRAGILE_MAX_LEVEL = 10;
    /** 脆弱 amplifier 上限（等级上限 - 1）。 */
    public static final int FRAGILE_MAX_AMPLIFIER = FRAGILE_MAX_LEVEL - 1;

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
     * 激怒满层（amplifier ≥ {@link #FRAGILE_TRIGGER_LEVEL}）后继续获取激怒时，对目标施加 / 叠层「脆弱」。
     * <p>
     * 2026-09-10（用户裁决 D1）：施加目标由「Boss 自身」改为「参战玩家」。脆弱链的语义是
     * 「玩家被打更疼」（设计 §2.8 触发玩家脆弱、§3.9 受击者增伤），而 Boss 免疫自身挂上的
     * 负面效果 → 原写法（`applyFragileIfEnraged(this, ...)`）整条链从未生效，是死代码。
     * 追伤通道见 {@code CommonEvents#applyFragileDamage}（受击 Post，按 (amp+1)×5% 追加真伤）。
     *
     * @param target          施加目标（由调用方传入参战玩家）
     * @param enrageAmplifier 当前激怒等级；低于阈值时不做任何事
     */
    public static void applyFragile(LivingEntity target, int enrageAmplifier) {
        if (enrageAmplifier < FRAGILE_TRIGGER_LEVEL) {
            return;
        }
        MobEffectInstance existing = target.getEffect(
            com.lydiath.silent_sun.registry.ModEffects.FRAGILE);
        if (existing != null && existing.getAmplifier() >= FRAGILE_MAX_AMPLIFIER) {
            return; // 已达等级上限（10 级）→ 不再叠层
        }
        int newAmp = Math.min(FRAGILE_MAX_AMPLIFIER, existing != null ? existing.getAmplifier() + 1 : 0);
        target.addEffect(new MobEffectInstance(
            com.lydiath.silent_sun.registry.ModEffects.FRAGILE,
            MobEffectInstance.INFINITE_DURATION, newAmp, false, true));
    }
}
