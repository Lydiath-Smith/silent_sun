/*
 * 断光之刻（2.5）视觉平替：世界黑块 + 白天天空。
 *
 * 服务端在「断光之刻」锁血窗口内通过 RediosEntity.CLIENT_TWILIGHT_ACTIVE 同步激活标记；
 * 客户端据此：
 *   1) 将雾色压为纯黑并把地形雾距离缩短 → 方块随距离快速变纯黑（黑块观感）；
 *   2) 在 AFTER_SKY 阶段覆盖绘制一块白天蓝色天空盘 → 午夜仍显示白天天空。
 *
 * 纯视觉实现，不改动世界时间/天气/方块光照，也不影响玩家死亡界面、REI 等
 * 仅依赖客户端世界渲染的 GUI/系统；离开战斗（Boss 脱战/被移除/切维）后自动恢复。
 */
package com.lydiath.silent_sun.client;

import com.lydiath.silent_sun.SilentSunMod;
import com.lydiath.silent_sun.entity.RediosEntity;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = "silent_sun", value = Dist.CLIENT)
public final class TwilightVisualEvents {

    /** 断光之刻视觉生效半径（格）：与战斗锚点/反流放范围对齐。 */
    private static final double BOSS_RADIUS = 256.0;
    /** 黑块雾的终止距离：超过此距离的方块被雾完全覆盖为纯黑。 */
    private static final float BLACK_FOG_END = 8.0F;
    /** 入场演出雾的终止距离：约 3 格，营造失明观感（无 debuff）。 */
    private static final float INTRO_FOG_END = 3.0F;
    /** 白天天空盘颜色（浅蓝）。 */
    private static final float DAY_SKY_R = 0.55F;
    private static final float DAY_SKY_G = 0.72F;
    private static final float DAY_SKY_B = 1.0F;

    /** 缓存的白天天空盘网格（懒加载，仅在渲染线程创建）。 */
    private static VertexBuffer daySkyBuffer = null;

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isTwilightActive() && !isIntroActive()) {
            return;
        }
        event.setRed(0.0F);
        event.setGreen(0.0F);
        event.setBlue(0.0F);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        boolean twilight = isTwilightActive();
        boolean intro = isIntroActive();
        if (!twilight && !intro) {
            return;
        }
        // 只压缩地形雾（方块/实体），天空由 AFTER_SKY 阶段单独覆盖，不参与此处。
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        event.setNearPlaneDistance(0.0F);
        event.setFarPlaneDistance(intro ? INTRO_FOG_END : BLACK_FOG_END);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        if (!isTwilightActive()) {
            return;
        }
        renderDaySky(event);
    }

    private static void renderDaySky(RenderLevelStageEvent event) {
        VertexBuffer buffer = getDaySkyBuffer();
        if (buffer == null) {
            return;
        }
        Matrix4f modelView = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        FogRenderer.setupNoFog();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(DAY_SKY_R, DAY_SKY_G, DAY_SKY_B, 1.0F);
        ShaderInstance shader = GameRenderer.getPositionShader();
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
    }

    private static VertexBuffer getDaySkyBuffer() {
        if (daySkyBuffer == null) {
            try {
                Tesselator tesselator = Tesselator.getInstance();
                float y = 16.0F;
                float radius = 512.0F;
                BufferBuilder builder = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
                builder.addVertex(0.0F, y, 0.0F);
                for (int i = -180; i <= 180; i += 45) {
                    double rad = Math.toRadians(i);
                    builder.addVertex(radius * (float) Math.cos(rad), y, radius * (float) Math.sin(rad));
                }
                MeshData mesh = builder.buildOrThrow();
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(mesh);
                VertexBuffer.unbind();
                daySkyBuffer = buffer;
            } catch (Exception e) {
                SilentSunMod.LOGGER.warn("Failed to build twilight daytime sky disc: {}", e.getMessage());
                daySkyBuffer = null;
            }
        }
        return daySkyBuffer;
    }

    private static boolean isTwilightActive() {
        return hasBossWith(RediosEntity::isClientTwilightActive);
    }

    private static boolean isIntroActive() {
        return hasBossWith(RediosEntity::isClientIntroActive);
    }

    private static boolean hasBossWith(Predicate<RediosEntity> predicate) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) {
            return false;
        }
        AABB box = AABB.ofSize(mc.player.position(), BOSS_RADIUS * 2, BOSS_RADIUS * 2, BOSS_RADIUS * 2);
        List<RediosEntity> list = mc.level.getEntitiesOfClass(RediosEntity.class, box,
            e -> e.isAlive() && predicate.test(e));
        return !list.isEmpty();
    }

    private TwilightVisualEvents() {
    }
}
