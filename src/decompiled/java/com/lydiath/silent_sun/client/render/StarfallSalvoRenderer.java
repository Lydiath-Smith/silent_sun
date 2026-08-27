/*
 * 繁星爆闪 (StarfallSalvo) 星星渲染器：面向相机的公告板纹理，
 * 满亮度、无阴影、无剔除，配合实体下落/悬停演出。
 */
package com.lydiath.silent_sun.client.render;

import com.lydiath.silent_sun.entity.StarfallSalvoEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class StarfallSalvoRenderer extends EntityRenderer<StarfallSalvoEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("silent_sun", "textures/entity/starfall_salvo.png");
    /** 局内显示尺寸：5 × 5 格的正方形公告板（厚度 0.05 格为概念值，实际为零厚度平面）。 */
    private static final float SCALE = 5.0f;

    public StarfallSalvoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public void render(StarfallSalvoEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 显示豁免（入场演出星星）：临时关闭全局雾，穿透黑雾全程可见，渲染完恢复。
        boolean exempt = entity.isDisplayExempt();
        float savedFogStart = 0.0f;
        float savedFogEnd = 0.0f;
        if (exempt) {
            savedFogStart = RenderSystem.getShaderFogStart();
            savedFogEnd = RenderSystem.getShaderFogEnd();
            FogRenderer.setupNoFog();
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        }
        float half = SCALE / 2.0f;
        poseStack.pushPose();
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        vc.addVertex(pose, -half, half, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, -half, -half, 0.0f).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, half, -half, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        vc.addVertex(pose, half, half, 0.0f).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0.0f, 0.0f, 1.0f);
        poseStack.popPose();
        if (exempt) {
            RenderSystem.setShaderFogStart(savedFogStart);
            RenderSystem.setShaderFogEnd(savedFogEnd);
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    public ResourceLocation getTextureLocation(StarfallSalvoEntity entity) {
        return TEXTURE;
    }
}
