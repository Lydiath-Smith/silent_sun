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
    /**
     * 天空盘构建失败后熔断（渲染线程独占，无需 volatile）。
     * <p>
     * 2026-09-11（代码审计 G20 #4）：原实现失败后把 {@code daySkyBuffer} 置 null 就返回，
     * 而调用方每帧都会重试 ⇒ 断光之刻期间**每帧**重走一次完整构建并打一条 warn（≈60+ 条/秒）。
     */
    private static boolean daySkyBuildFailed = false;

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

    /**
     * 懒构建白天天空盘；失败后熔断，不再每帧重试。
     * <p>
     * 2026-09-11（代码审计 G20 #4）：三处修复 ——
     * ① <b>失败熔断</b>：原实现失败即 {@code daySkyBuffer = null} 返回，调用方每帧重试 ⇒ 日志刷屏；
     * ② <b>失败路径释放 GL 对象</b>：{@code VertexBuffer} 在 1.21.1 构造时即分配 2 个 buffer + 1 个 VAO
     *    （{@code _glGenBuffers} ×2 + {@code _glGenVertexArrays}），原 catch 分支既不 close 也不 unbind，
     *    若异常抛自 {@code upload}（此时已 bind 成功）⇒ 每次失败泄漏一组 GL 对象并把 VAO 留在绑定态；
     * ③ {@code MeshData} 归属：{@code upload} 内部会自行 close 它（成功与异常两条路径都关），
     *    故只有「{@code buildOrThrow} 之后、{@code upload} 之前」抛异常时才需我们兜底 close，
     *    用 {@code mesh = null} 标记「已交给 upload」，避免双重 close。
     * <p>
     * 正常路径行为不变：{@code daySkyBuffer} 只在首次构建时创建一次，随客户端进程存活（设计上的常驻缓存）。
     */
    private static VertexBuffer getDaySkyBuffer() {
        if (daySkyBuffer != null || daySkyBuildFailed) {
            return daySkyBuffer;
        }
        VertexBuffer buffer = null;
        MeshData mesh = null;
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
            mesh = builder.buildOrThrow();
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            mesh = null;
            daySkyBuffer = buffer;
        } catch (Exception e) {
            if (mesh != null) {
                try {
                    mesh.close();
                } catch (Exception ignored) {
                    // 兜底释放失败不影响熔断语义
                }
            }
            if (buffer != null) {
                try {
                    buffer.close();
                } catch (Exception ignored) {
                    // 同上
                }
            }
            daySkyBuildFailed = true;
            SilentSunMod.LOGGER.warn("Failed to build twilight daytime sky disc: {}", e.getMessage());
            return null;
        } finally {
            // 成功与失败都恢复 VAO 0：覆盖「bind 之后才抛异常」的路径（原实现在成功路径上单独 unbind）
            VertexBuffer.unbind();
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
