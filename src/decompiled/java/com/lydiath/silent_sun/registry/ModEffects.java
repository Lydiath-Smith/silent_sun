package com.lydiath.silent_sun.registry;

import com.lydiath.silent_sun.effect.EnrageEffect;
import com.lydiath.silent_sun.effect.FragileEffect;
import com.lydiath.silent_sun.effect.SoulSeverEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "silent_sun");
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_SEVER = MOB_EFFECTS.register("soul_sever", () -> new SoulSeverEffect(MobEffectCategory.NEUTRAL, 4922173));
    public static final DeferredHolder<MobEffect, MobEffect> ENRAGE = MOB_EFFECTS.register("enrage", () -> new EnrageEffect(MobEffectCategory.BENEFICIAL, 11869726));
    public static final DeferredHolder<MobEffect, MobEffect> FRAGILE = MOB_EFFECTS.register("fragile", () -> new FragileEffect(MobEffectCategory.NEUTRAL, 0x8B0000));

    private ModEffects() {
    }
}
