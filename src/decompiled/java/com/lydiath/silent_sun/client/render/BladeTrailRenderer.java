package com.lydiath.silent_sun.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * 刀身拖尾残影的矩阵队列与渐隐渲染（本模组版）。
 *
 * <p>按**灭却之日**的 {@code fx/BladeTrailRenderer}（交接文档 §四 / §十）对齐：跳过最新采样、`swing` 调制、
 * 白色残影（{@code 0xFFFFFF} —— shader 里是 {@code color * vertexColor.rgb}，白色才保传送门原色）、
 * 按 key 隔离去重时间戳（全局共享会导致"先记录者之后 2ms 内其余轨迹被整体跳过"）。
 *
 * <p><b>2026-09-14 新增光影分支</b>：{@link #render} 走自建 {@code PORTAL_TRAIL}（**光影下失效**），
 * {@link #renderLuminous} 走前置 luminous 管线（**光影下唯一可用通道**）。二者共用同一份历史队列，
 * 由 {@code PortalBladeTrailHook} 按 {@link IrisCompat#isShaderPackInUse()} 二选一。
 */
public final class BladeTrailRenderer {

    /** 拖尾残影最大历史帧数。 */
    public static final int TRAIL_LENGTH = 12;

    /** 同帧重复渲染（如第一/第三人称同时）去重阈值（纳秒）。 */
    private static final long DEDUP_NANOS = 2_000_000L;

    private static final Map<String, ArrayDeque<Matrix4f>> TRAILS = new HashMap<>();
    private static final Map<String, Long> LAST_RECORD_NANOS = new HashMap<>();

    private BladeTrailRenderer() {
    }

    private static ArrayDeque<Matrix4f> queueOf(String key) {
        return TRAILS.computeIfAbsent(key, k -> new ArrayDeque<>());
    }

    /** 记录一次刀身 model 矩阵（内部深拷贝，调用方后续修改不受影响）。 */
    public static void record(String key, Matrix4f pose) {
        long now = System.nanoTime();
        Long last = LAST_RECORD_NANOS.get(key);
        if (last != null && now - last < DEDUP_NANOS) {
            return;
        }
        LAST_RECORD_NANOS.put(key, now);
        ArrayDeque<Matrix4f> queue = queueOf(key);
        queue.addFirst(new Matrix4f(pose));
        while (queue.size() > TRAIL_LENGTH) {
            queue.removeLast();
        }
    }

    /** 该帧的打包 ARGB（越老越透明；`swing` 越大越亮）。返回 0 表示这一帧不画。 */
    private static int frameArgb(int index, float swing) {
        float age = (float) (index - 1) / TRAIL_LENGTH;
        if (age >= 1.0F) {
            return 0;
        }
        float s = Math.max(0.0F, Math.min(1.0F, swing));
        float alpha = (1.0F - age) * (0.35F + 0.65F * s);
        int a = (int) (255.0F * alpha);
        return a <= 0 ? 0 : ((a << 24) | 0xFFFFFF);
    }

    /**
     * 沿历史矩阵渲染渐隐残影 —— **光影关闭**时用（自建 {@code PORTAL_TRAIL}，噪点会流动、可置顶）。
     *
     * @param swing 运动强度 0–1（本模组传持有者的 {@code attackAnim}）
     */
    public static void render(VertexConsumer buf, WavefrontObject model, String groupName, float swing, String key) {
        ArrayDeque<Matrix4f> queue = queueOf(key);
        int i = 0;
        for (Matrix4f m : queue) {
            i++;
            if (i == 1) {
                continue; // 跳过最新采样（即当前本体），否则残影会与自己重合画一层
            }
            int argb = frameArgb(i, swing);
            if (argb == 0) {
                if ((float) (i - 1) / TRAIL_LENGTH >= 1.0F) {
                    break;
                }
                continue;
            }
            PortalMeshRenderer.writePortalTrail(buf, m, model, groupName, argb);
        }
    }

    /**
     * 沿历史矩阵渲染渐隐残影 —— **光影开启**时用（前置 luminous 管线；代价：噪点静态、不流动）。
     *
     * <p>做法照灭却之日交接文档 **§10.4-③**：每帧用 {@code pose.last().pose().set(历史矩阵)} 临时换矩阵，
     * 颜色走 {@code BladeRenderState.setCol(argb)}（前置的 {@code Face.putVertex} 从中取 RGBA）。
     * <p>⚠️ **用完必须复位颜色**（这里用 {@code resetCol()} 而非留下残值），否则会污染之后所有渲染。
     *
     * @param vc 前置 luminous 管线的 buffer：{@code buffer.getBuffer(BladeRenderState.getSlashBladeBlendLuminous(...))}
     */
    public static void renderLuminous(VertexConsumer vc, WavefrontObject model, String groupName,
                                      float swing, String key, PoseStack pose) {
        ArrayDeque<Matrix4f> queue = queueOf(key);
        int i = 0;
        for (Matrix4f m : queue) {
            i++;
            if (i == 1) {
                continue;
            }
            int argb = frameArgb(i, swing);
            if (argb == 0) {
                if ((float) (i - 1) / TRAIL_LENGTH >= 1.0F) {
                    break;
                }
                continue;
            }
            BladeRenderState.setCol(argb);
            pose.pushPose();
            pose.last().pose().set(m); // 换成历史矩阵（joml 的 Matrix4f.set 直接改写当前栈帧的矩阵）
            model.tessellateOnly(vc, pose, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, groupName);
            pose.popPose();
        }
        BladeRenderState.resetCol();
    }

    /** 丢弃某条轨迹（残影关闭 / 实体离场时调用，避免 key 集合随实体 UUID 增长）。 */
    public static void clear(String key) {
        TRAILS.remove(key);
        LAST_RECORD_NANOS.remove(key);
    }
}
