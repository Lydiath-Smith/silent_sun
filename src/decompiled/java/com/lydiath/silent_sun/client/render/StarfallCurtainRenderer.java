/*
 * 繁星爆闪 (StarfallSalvo) 白色幕布渲染器：面向相机的公告板，
 * 满亮度、无阴影，随实体生命周期线性淡出（世界内白幕布演出）。
 */
package com.lydiath.silent_sun.client.render;

import com.lydiath.silent_sun.entity.StarfallCurtainEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class StarfallCurtainRenderer extends EntityRenderer<StarfallCurtainEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("silent_sun", "textures/entity/starfall_curtain.png");
    /** 幕布尺寸（格）：足够大的一块白色幕布，放置在远处填满视野。 */
    private static final float CURTAIN_SIZE = 20.0f;
    /** 峰值不透明度（0~255）：下界等环境需要近乎完全不透明，故拉满。 */
    private static final int PEAK_ALPHA = 255;

    public StarfallCurtainRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public void render(StarfallCurtainEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 2026-09-11（代码审计 G10 #3 → 作者裁定采用本方案）：淡出进度改用**实体实例真值**。
        // 原实现的总时长直接读静态常量 MAX_LIFETIME_TICKS ⇒ 一旦 initCurtain 传入别的时长，
        // 渲染进度就与实体实际生命周期脱钩（同一语义两处独立定义）。
        // 现由实体的「剩余 tick / 本次总时长」计算，与实体实际生命周期同源。
        float total = Math.max(1, entity.getTotalLifetimeTicks());
        float remaining = Math.max(0, entity.getLifetimeTicks());
        float progress = 1.0f - remaining / total;
        int alpha = Math.max(0, (int) ((1.0f - progress) * PEAK_ALPHA));
        if (alpha <= 0) {
            return;
        }
        float half = CURTAIN_SIZE / 2.0f;
        poseStack.pushPose();
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        vc.addVertex(pose, -half, half, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, -half, -half, 0.0f).setColor(255, 255, 255, alpha).setUv(0.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, half, -half, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, half, half, 0.0f).setColor(255, 255, 255, alpha).setUv(1.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public ResourceLocation getTextureLocation(StarfallCurtainEntity entity) {
        return TEXTURE;
    }
}
