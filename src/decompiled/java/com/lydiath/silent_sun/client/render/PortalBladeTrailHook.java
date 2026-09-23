package com.lydiath.silent_sun.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * 传送门刀身 overlay + 残影的**业务逻辑**（与 Mixin 分离，便于阅读与独立验证）。
 *
 * <p><b>注入链路</b>（2026-09-14 按灭却之日交接文档重做）：
 * <pre>
 *   LayerMainBlade.render(pose, buf, light, entity, …)        ← 实体手持刀身（Boss）
 *       ├─ HEAD: PortalRenderContext.CURRENT_HOLDER = entity   ← 把"是谁在拿刀"传下去
 *       └─ 内部调 BladeRenderState.renderOverrided(stack, model, target, tex, pose, buf, …)
 *              └─ TAIL: 本类 onBladeRendered(...)              ← 精确矩阵 + 真实模型面 + 组名
 * </pre>
 *
 * <p><b>2026-09-14 光影（Iris）双路径</b>（依据：灭却之日交接文档 **§十**，及其引用的 Iris 官方文档
 * 「mod 添加的自定义 shader 会被 Iris 忽略」）：
 * <ul>
 *   <li><b>光影关</b> ⇒ 自建 {@code PORTAL_TRAIL}：噪点**流动**、可置顶（{@code NO_DEPTH_TEST}）。</li>
 *   <li><b>光影开</b> ⇒ 前置 {@code getSlashBladeBlendLuminous}：走常规实体 gbuffer 通道（Iris 认识）。
 *       代价是噪点**退化为静态**、不再流动 —— Iris 的硬限制，消不掉。</li>
 * </ul>
 * ⚠️ <b>坑 ①（§10.4，我原先正是这么写的）</b>：**不能**因「自定义 shader 未就绪」就整个 {@code return} ——
 * 光影下它本就不可用，那样会导致**光影开启时什么都不画**。正确判据是
 * {@code if (!irisOn && shader == null) return;}。
 *
 * <p><b>异常策略</b>：本方法在渲染线程每帧执行，任何异常都不允许冒出（否则崩客户端），故整体 try-catch 静默降级。
 */
public final class PortalBladeTrailHook {

    /** 只对刀身组写 overlay：刀柄 / 鞘（sheath）、发光层（blade_luminous）、充能特效等保持父模组原贴图。 */
    private static final String BLADE_GROUP = "blade";

    /** 残影轨迹 key 前缀（后接实体 UUID，避免多实体互相污染）。 */
    private static final String TRAIL_KEY_PREFIX = "silent_sun:blade:";

    private PortalBladeTrailHook() {
    }

    /**
     * 刀身已写入 buffer 之后调用（{@code BladeRenderState.renderOverrided} 9 参的 TAIL）。
     *
     * @param model  OBJ 模型（用于取真实刀身面）
     * @param target 组名（只处理 {@code "blade"}）
     */
    public static void onBladeRendered(ItemStack stack, WavefrontObject model, String target,
                                       PoseStack pose, MultiBufferSource buffer) {
        try {
            if (pose == null || buffer == null || model == null) {
                return;
            }
            // 只在"实体手持刀身"的调用栈里工作：玩家第一人称 / GUI 图标 ⇒ ThreadLocal 为空 ⇒ 跳过
            LivingEntity holder = PortalRenderContext.CURRENT_HOLDER.get();
            if (holder == null) {
                return;
            }
            // 只对刀身组；其余组保持父模组原贴图（避免第一人称整把刀被糊满）
            if (!BLADE_GROUP.equals(target)) {
                return;
            }
            String key = TRAIL_KEY_PREFIX + holder.getUUID();
            if (!PortalBladeCondition.isActive(holder)) {
                BladeTrailRenderer.clear(key);
                return;
            }

            boolean irisOn = IrisCompat.isShaderPackInUse();
            // ⚠️ 坑 ①（交接文档 §10.4）：光影下自建 shader 本就不可用 ⇒ **不能**因此整个 return。
            //    只有「非光影 + shader 尚未就绪（资源未加载）」才跳过。
            if (!irisOn && PortalTrailShaders.portalTrailShader == null) {
                return;
            }

            Matrix4f matrix = pose.last().pose();
            float swing = holder.attackAnim; // 有挥动才亮（静止时系数 0.35）

            if (irisOn) {
                // ── 光影开：前置 luminous 管线（常规 gbuffer 通道，Iris 认识）──
                VertexConsumer vc = buffer.getBuffer(
                    BladeRenderState.getSlashBladeBlendLuminous(TheEndPortalRenderer.END_PORTAL_LOCATION));
                // ① 持续 overlay：整片刀身覆上传送门材质（白色 + 全 alpha ⇒ 保原色）
                BladeRenderState.setCol(0xFFFFFFFF);
                model.tessellateOnly(vc, pose, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, target);
                BladeRenderState.resetCol();
                // ② 挥动残影：记录当前矩阵，再沿历史矩阵渐隐重画
                BladeTrailRenderer.record(key, matrix);
                BladeTrailRenderer.renderLuminous(vc, model, target, swing, key, pose);
            } else {
                // ── 光影关：自建 PORTAL_TRAIL（噪点流动、NO_DEPTH_TEST 置顶）──
                VertexConsumer vc = buffer.getBuffer(PortalTrailRenderType.PORTAL_TRAIL);
                PortalMeshRenderer.writePortalTrail(vc, matrix, model, target, 0xFFFFFFFF);
                BladeTrailRenderer.record(key, matrix);
                BladeTrailRenderer.render(vc, model, target, swing, key);
            }
        } catch (Throwable ignored) {
            // 渲染期异常一律吞掉：残影是纯视觉增益，绝不能因此崩客户端。
        }
    }
}
