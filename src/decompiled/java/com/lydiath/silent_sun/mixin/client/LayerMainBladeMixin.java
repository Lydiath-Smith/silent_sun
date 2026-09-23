package com.lydiath.silent_sun.mixin.client;

import com.lydiath.silent_sun.client.render.PortalRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⚠️ <b>2026-09-23 起本类**未注册**</b>（{@code silent_sun.mixins.json} 的 {@code client} 数组已清空）——
 * 作者最终裁决「刀身和残影不做了」；**代码保留**。此前 9/20 晚曾短暂恢复。
 * 恢复方式：把 {@code "client.LayerMainBladeMixin"} 加回该 json 的 {@code client} 数组。
 *
 * <p>把「当前正在渲染谁的刀身」传给下游（{@link PortalRenderContext}）。
 *
 * <p><b>本类现在只干这一件事</b>（2026-09-14 重构）：真正的 overlay / 残影逻辑搬去了
 * {@link BladeRenderStateMixin}（注入 {@code BladeRenderState.renderOverrided} 9 参 TAIL），
 * 因为那里才有**精确的刀身矩阵**与**真实模型面**。原先我在本类 {@code render} 的 TAIL
 * 直接写残影，拿到的是已被 {@code MSAutoCloser.close()} 恢复的 layer 基准矩阵 ⇒ 残影会飘。
 *
 * <p><b>为什么还需要本类</b>：{@code renderOverrided} 的参数里**没有实体**，而激活判据
 * （{@code PortalBladeCondition.isActive(LivingEntity)}＝Boss + 断魂值）必须有实体。
 * {@code LayerMainBlade.render} 是该调用链的**上一层**且带 {@code LivingEntity entity}
 * ⇒ 在这里入栈时把实体放进 ThreadLocal，下游 TAIL 取用（交接文档 §五 的模式）。
 *
 * <p>调用链：{@code LayerMainBlade.render} → {@code BladeRenderState.renderOverrided}（同一调用栈、
 * 同一线程）⇒ ThreadLocal 可靠。
 *
 * <p>⚠️ 若 {@code render} 中途抛异常，TAIL 不会执行、ThreadLocal 会残留一个旧实体；
 * 但 HEAD 每次都会覆盖写入 ⇒ 影响仅限"异常后首个非实体路径的 renderOverrided 可能误判一次"，
 * 而渲染期抛异常本身已属崩客户端范畴，故不额外加保护（简单优先）。
 */
@Mixin(LayerMainBlade.class)
public class LayerMainBladeMixin {

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD")
    )
    private void silentSun$pushHolder(PoseStack pose, MultiBufferSource bufferSource, int packedLight,
                                      LivingEntity entity, float limbSwing, float limbSwingAmount,
                                      float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                      CallbackInfo ci) {
        PortalRenderContext.CURRENT_HOLDER.set(entity);
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At("TAIL")
    )
    private void silentSun$popHolder(PoseStack pose, MultiBufferSource bufferSource, int packedLight,
                                     LivingEntity entity, float limbSwing, float limbSwingAmount,
                                     float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                     CallbackInfo ci) {
        PortalRenderContext.CURRENT_HOLDER.remove();
    }
}
