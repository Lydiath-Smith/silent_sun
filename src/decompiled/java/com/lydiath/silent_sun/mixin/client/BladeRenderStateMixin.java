package com.lydiath.silent_sun.mixin.client;

import com.lydiath.silent_sun.client.render.PortalBladeTrailHook;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * ⚠️ <b>2026-09-23 起本类**未注册**</b>（{@code silent_sun.mixins.json} 的 {@code client} 数组已清空）——
 * 作者最终裁决「刀身和残影不做了」；**代码保留**。此前 9/20 晚曾随前置 1.16.0 对接短暂恢复。
 * 恢复方式：把 {@code "client.BladeRenderStateMixin"} 加回该 json 的 {@code client} 数组（本类无需改动）。
 *
 * <p>刀身 overlay / 残影的**主注入点**：拔刀剑 {@code BladeRenderState.renderOverrided} 的 9 参 TAIL。
 *
 * <p><b>为什么是这里而不是 {@code SlashBladeTEISR}</b>：TEISR 的 {@code renderBlade} 对
 * {@code THIRD_PERSON_*} 直接 {@code return false} 不渲染（只画第一人称）⇒ 实体手持刀身不经过它。
 * 而 {@code LayerMainBlade}（实体 RenderLayer）最终会把刀身交给 {@code BladeRenderState.renderOverrided}
 * 绘制 ⇒ 在它的 TAIL 补写，天然画在刀身之上。
 *
 * <p><b>为什么不用 {@code LayerMainBlade.render} 的 TAIL</b>：那里 {@code pose} 已被
 * {@code MSAutoCloser.close()} 恢复成 layer 基准矩阵，不含刀身骨骼变换 ⇒ 残影位置不准。
 * 本注入点拿到的是**刀身自己的 PoseStack**，且带 {@code model} 与组名 {@code target}。
 * 代价是这里没有实体 ⇒ 实体由 {@code LayerMainBladeMixin} 经
 * {@code PortalRenderContext.CURRENT_HOLDER}（ThreadLocal）传下来。
 *
 * <p><b>注入点写法参考灭却之日的 {@code BladeRenderStateMixin}（交接文档 §八）</b>：
 * 只注 9 参版本即可（7 参会委托给它）。他们额外注入 7 参 HEAD 是为了兼容整合包里的
 * {@code a_belated_gift}（它在 7 参 HEAD 截胡并 cancel，导致 9 参永不执行）——
 * 本模组暂不加那层兼容：**若实测发现装了该模组时 overlay 不出现，再按同样手法补**。
 *
 * <p>⚠️ {@code renderOverrided} 是**静态方法** ⇒ 注入方法必须 {@code static}。
 */
@Mixin(value = BladeRenderState.class, priority = 999)
public abstract class BladeRenderStateMixin {

    @Inject(
        method = "renderOverrided(Lnet/minecraft/world/item/ItemStack;Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILjava/util/function/Function;Z)V",
        at = @At("TAIL")
    )
    private static void silentSun$portalBladeOverlay(ItemStack stack, WavefrontObject model, String target,
                                                     ResourceLocation texture, PoseStack pose,
                                                     MultiBufferSource buffer, int packedLight,
                                                     Function<ResourceLocation, RenderType> renderTypeFn,
                                                     boolean enableEffect, CallbackInfo ci) {
        PortalBladeTrailHook.onBladeRendered(stack, model, target, pose, buffer);
    }
}
