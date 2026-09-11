/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.client.render;

import com.lydiath.silent_sun.SilentSunMod;
import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.entity.RediosEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class RediosRenderer
extends HumanoidMobRenderer<RediosEntity, HumanoidModel<RediosEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("silent_sun", "textures/entity/redios.png");
    private static final ResourceLocation BEACON_BEAM_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/beacon_beam.png");
    private static final Map<UUID, Integer> LAST_TRANSITION_IMPACT_TICK = new HashMap<UUID, Integer>();

    public RediosRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER)), 0.65f);
        // 手持物品渲染层：HumanoidMobRenderer 不会自动添加，
        // 缺此层时 Boss 主手武器（三叉戟替换模型 / 拔刀剑命名刀）不会渲染
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        tryAttachMainBladeLayer();
    }

    /**
     * 有拔刀剑（SlashBlade）时挂拄刀/挥刀层：LayerMainBlade 泛型化支持任意 LivingEntity，
     * Boss 拿命名刀后即可像玩家一样拄刀并播放 combo VMD 挥刀动作。无 SlashBlade 时静默跳过。
     */
    private void tryAttachMainBladeLayer() {
        try {
            Class<?> layerClass = Class.forName("mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade");
            java.lang.reflect.Constructor<?> ctor = layerClass.getConstructor(RenderLayerParent.class);
            Object layer = ctor.newInstance(this);
            this.addLayer((RenderLayer) layer);
            SilentSunMod.LOGGER.info("[SilentSun] SlashBlade main-blade layer attached to RediosRenderer");
        } catch (Throwable ignored) {
        }
    }

    public ResourceLocation getTextureLocation(RediosEntity entity) {
        return TEXTURE;
    }

    protected boolean shouldShowName(RediosEntity entity) {
        if (entity.getClientBossState().isVoting() || entity.getClientTransitionTicks() > 0) {
            return false;
        }
        return super.shouldShowName(entity);
    }

    public void render(RediosEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        int transitionTicks = entity.getClientTransitionTicks();
        if (transitionTicks > 0 && !entity.getClientBossState().isVoting()) {
            poseStack.pushPose();
            this.renderTransitionEffects(entity, transitionTicks, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        }
        // 召唤演出（2026-09-04）：裂解之痛召唤时复用切阶段立方体动画（烟圈收缩帧服务端触发散射爆闪）。
        int summonTicks = entity.getClientSummonIntroTicks();
        if (summonTicks > 0 && transitionTicks <= 0) {
            poseStack.pushPose();
            this.renderTransitionEffects(entity, summonTicks, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
        }
        // 2026-09-11（代码审计 G20 #2）：两种演出都未进行 ⇒ 回收本实体的去重条目。
        // LAST_TRANSITION_IMPACT_TICK 是 static 表（客户端 JVM 内跨世界/跨重连存活），原先只 put 不 remove：
        // 每次召唤都是新 UUID ⇒ 旧条目永久变垃圾并被静态表强引用。
        // 此处每帧 O(1) 一次 remove，与 114 行 getOrDefault 同级开销，不引入任何扫描；
        // 去重窗口（elapsed == impactTick 且效果仍在进行）此时早已关闭，不会误删正在使用的条目。
        if (transitionTicks <= 0 && summonTicks <= 0) {
            LAST_TRANSITION_IMPACT_TICK.remove(entity.getUUID());
        }
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    private void renderTransitionEffects(RediosEntity entity, int remainingTicks, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float fieldT;
        int last;
        int totalTicks = Math.max(1, (Integer)SilentSunConfig.PHASE_TRANSITION_SECONDS.get() * 20);
        int elapsed = Mth.clamp((int)(totalTicks - remainingTicks), (int)0, (int)totalTicks);
        float t = ((float)elapsed + partialTick) / (float)totalTicks;
        int impactTick = 6;
        float impactLerp = Mth.clamp((float)(((float)elapsed + partialTick) / (float)impactTick), (float)0.0f, (float)1.0f);
        float lift = 0.0f; // 2026-08-15：移除 1.5 格上移，转阶段方块从 Boss 脚下开始包裹全身
        float height = entity.getBbHeight(); // 包裹 Boss 全身（碰撞箱高度 ≈ 1.95）
        // 落地方块终点 = 方块中心高度（方块底 lift + 高 height 的一半）
        float fallEnd = lift + height * 0.5f;
        float fallY = Mth.lerp((float)impactLerp, (float)(fallEnd + 3.0f), (float)fallEnd);
        float fallScale = 0.9f;
        if (elapsed <= impactTick + 2) {
            poseStack.pushPose();
            poseStack.translate(-fallScale * 0.5f, fallY, -fallScale * 0.5f);
            poseStack.scale(fallScale, fallScale, fallScale);
            VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent((ResourceLocation)BEACON_BEAM_TEXTURE));
            this.renderBoxQuads(vc, poseStack, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0, 0, 0, 255, packedLight);
            poseStack.popPose();
        }
        if (elapsed == impactTick && entity.tickCount != (last = LAST_TRANSITION_IMPACT_TICK.getOrDefault(entity.getUUID(), Integer.MIN_VALUE).intValue())) {
            LAST_TRANSITION_IMPACT_TICK.put(entity.getUUID(), entity.tickCount);
            this.spawnImpactParticles(entity);
        }
        float s = (fieldT = Mth.clamp((float)(((float)elapsed + partialTick - (float)impactTick) / Math.max(1.0f, (float)(totalTicks - impactTick))), (float)0.0f, (float)1.0f)) < 0.5f ? fieldT * 2.0f : (1.0f - fieldT) * 2.0f;
        s = Mth.clamp((float)s, (float)0.0f, (float)1.0f);
        float eased = s * s * (3.0f - 2.0f * s);
        float half = 5.0f * eased; // 2026-08-12：方块半径翻倍（原 2.5）
        int alpha = (int)(150.0f * eased);
        int beamAlpha = (int)(200.0f * eased);
        int r = 70;
        int g = 0;
        int b = 95;
        if (eased > 0.001f) {
            poseStack.pushPose();
            poseStack.translate(0.0, 0.02 + lift, 0.0);
            VertexConsumer boxVc = bufferSource.getBuffer(RenderType.entityTranslucent((ResourceLocation)BEACON_BEAM_TEXTURE));
            this.renderBoxQuads(boxVc, poseStack, -half, 0.0f, -half, half, height, half, r, g, b, alpha, packedLight);
            VertexConsumer lineVc = bufferSource.getBuffer(RenderType.lines());
            this.renderBoxLines(lineVc, poseStack, -half, 0.0f, -half, half, height, half, r, g, b, Math.min(255, alpha + 40), packedLight);
            poseStack.popPose();
            this.renderBeam(entity, partialTick, poseStack, bufferSource, packedLight, eased, r, g, b, beamAlpha);
        }
    }

    private void spawnImpactParticles(RediosEntity entity) {
        double vz;
        double vy;
        double vx;
        Level level = entity.level();
        if (!(level instanceof ClientLevel)) {
            return;
        }
        Vec3 base = entity.position().add(0.0, (double)entity.getBbHeight() * 0.9, 0.0);
        int i = 0;
        while (i < 18) {
            vx = (level.random.nextDouble() - 0.5) * 0.6;
            vy = level.random.nextDouble() * 0.35;
            vz = (level.random.nextDouble() - 0.5) * 0.6;
            level.addParticle((ParticleOptions)ParticleTypes.LARGE_SMOKE, base.x, base.y, base.z, vx, vy, vz);
            ++i;
        }
        i = 0;
        while (i < 12) {
            vx = (level.random.nextDouble() - 0.5) * 0.9;
            vy = level.random.nextDouble() * 0.2;
            vz = (level.random.nextDouble() - 0.5) * 0.9;
            level.addParticle((ParticleOptions)ParticleTypes.PORTAL, base.x, base.y, base.z, vx, vy, vz);
            ++i;
        }
    }

    private void renderBeam(RediosEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float scale, int r, int g, int b, int a) {
        float time;
        if (Minecraft.getInstance().level == null) {
            return;
        }
        int maxY = entity.level().getMaxBuildHeight();
        int baseY = Mth.floor((double)entity.getY());
        float height = Math.max(4.0f, (float)(maxY - baseY));
        float radius = 0.35f * scale * 10.0f; // 2026-08-12：光束半径翻倍（原 *5.0）
        float v0 = time = ((float)entity.level().getGameTime() + partialTick) * 0.02f;
        float v1 = time + height * 0.15f;
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityTranslucent((ResourceLocation)BEACON_BEAM_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        float x0 = -radius;
        float x1 = radius;
        float z0 = -radius;
        float z1 = radius;
        this.addQuad(vc, pose, x0, 0.0f, z0, x0, height, z0, x1, height, z0, x1, 0.0f, z0, r, g, b, a, 0.0f, v0, 0.0f, v1, packedLight, 0.0f, 0.0f, -1.0f);
        this.addQuad(vc, pose, x1, 0.0f, z1, x1, height, z1, x0, height, z1, x0, 0.0f, z1, r, g, b, a, 0.0f, v0, 0.0f, v1, packedLight, 0.0f, 0.0f, 1.0f);
        this.addQuad(vc, pose, x0, 0.0f, z1, x0, height, z1, x0, height, z0, x0, 0.0f, z0, r, g, b, a, 0.0f, v0, 0.0f, v1, packedLight, -1.0f, 0.0f, 0.0f);
        this.addQuad(vc, pose, x1, 0.0f, z0, x1, height, z0, x1, height, z1, x1, 0.0f, z1, r, g, b, a, 0.0f, v0, 0.0f, v1, packedLight, 1.0f, 0.0f, 0.0f);
    }

    private void renderBoxQuads(VertexConsumer vc, PoseStack poseStack, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int r, int g, int b, int a, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        this.addQuad(vc, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a, 0.0f, 0.0f, 1.0f, 1.0f, packedLight, 0.0f, 0.0f, -1.0f);
        this.addQuad(vc, pose, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, minX, minY, maxZ, r, g, b, a, 0.0f, 0.0f, 1.0f, 1.0f, packedLight, 0.0f, 0.0f, 1.0f);
        this.addQuad(vc, pose, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, minX, minY, minZ, r, g, b, a, 0.0f, 0.0f, 1.0f, 1.0f, packedLight, -1.0f, 0.0f, 0.0f);
        this.addQuad(vc, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a, 0.0f, 0.0f, 1.0f, 1.0f, packedLight, 1.0f, 0.0f, 0.0f);
        this.addQuad(vc, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a, 0.0f, 0.0f, 1.0f, 1.0f, packedLight, 0.0f, 1.0f, 0.0f);
        this.addQuad(vc, pose, minX, minY, maxZ, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, 0.0f, 0.0f, 1.0f, 1.0f, packedLight, 0.0f, -1.0f, 0.0f);
    }

    private void renderBoxLines(VertexConsumer vc, PoseStack poseStack, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int r, int g, int b, int a, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        this.addLine(vc, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, packedLight);
        this.addLine(vc, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, packedLight);
    }

    private void addLine(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a, int packedLight) {
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0.0f, 1.0f, 0.0f).setLight(packedLight);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0.0f, 1.0f, 0.0f).setLight(packedLight);
    }

    private void addQuad(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int a, float u0, float v0, float u1, float v1, int packedLight, float nx, float ny, float nz) {
        int overlay = OverlayTexture.NO_OVERLAY;
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x4, y4, z4).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(packedLight).setNormal(pose, nx, ny, nz);
    }
}
