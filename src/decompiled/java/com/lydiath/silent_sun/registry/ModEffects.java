package com.lydiath.silent_sun.registry;

import com.lydiath.silent_sun.effect.EnrageEffect;
import com.lydiath.silent_sun.effect.FragileEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "silent_sun");
    /**
     * 断魂效果统一到灭却之日（2026-09-01 用户裁决：效果与伤害类型应为同一个，走我们 9 bypass）：
     * silent_sun 不再注册自己的 soul_sever MobEffect（原为空壳类 SoulSeverEffect），
     * 直接别名灭却之日的 {@code extinction_day_mod_1784441698:soul_sever}（SoulSeverMobEffect）。
     * 结算统一走灭却之日账本（玩家账本 + Boss 账本双轨求和，见 SoulSeverMobEffect），
     * 本效果仅作视觉层数显示。所有引用点（getEffect/addEffect/removeEffect）无需改动。
     */
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_SEVER =
            cn.autoforged.extinction_day_mod_1784441698.effect.ModEffects.SOUL_SEVER;
    public static final DeferredHolder<MobEffect, MobEffect> ENRAGE = MOB_EFFECTS.register("enrage", () -> new EnrageEffect(MobEffectCategory.BENEFICIAL, 11869726));
    public static final DeferredHolder<MobEffect, MobEffect> FRAGILE = MOB_EFFECTS.register("fragile", () -> new FragileEffect(MobEffectCategory.NEUTRAL, 0x8B0000));

    private ModEffects() {
    }
}
