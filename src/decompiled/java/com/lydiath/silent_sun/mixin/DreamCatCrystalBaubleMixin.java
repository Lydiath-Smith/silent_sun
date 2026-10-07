package com.lydiath.silent_sun.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 时停免疫衔接（2026-10-03）：
 *
 * <p>目标类（车万女仆：万法皆通 1.9.0）的 {@code freezeTarget(Mob)} 原本对目标无条件执行
 * {@code setNoAi(true)} 并登记到期解除；本模组的行动刻防护会在数 tick 内把 NoAi 改回，
 * 造成双方每 tick 互相覆盖、NBT 状态残留的抖动。
 *
 * <p>本 Mixin 在 freezeTarget 的 HEAD 拦截：目标命中通用标签
 * {@code silent_sun:time_stop_immune} 则直接返回，时停从施加源头被跳过——
 * 不写 NBT、不设 NoAi、不调度解除，零残留。
 *
 * <p>目标模组为<b>可选依赖</b>：目标类不存在时本 Mixin 不生效（见
 * silent_sun.compat.mixins.json 的 required=false），不引用目标类的任何类型，
 * 仅通过字符串定位方法。
 */
@Mixin(targets = "com.github.yimeng261.maidspell.item.bauble.dreamCatCrystal.DreamCatCrystalBauble")
public abstract class DreamCatCrystalBaubleMixin {

    private static final TagKey<EntityType<?>> TIME_STOP_IMMUNE =
        TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("silent_sun", "time_stop_immune"));

    @Inject(method = "freezeTarget(Lnet/minecraft/world/entity/Mob;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void silentSun$skipFreezeForImmune(Mob target, CallbackInfo ci) {
        if (target != null && target.getType().is(TIME_STOP_IMMUNE)) {
            ci.cancel();
        }
    }

    private DreamCatCrystalBaubleMixin() {}
}
