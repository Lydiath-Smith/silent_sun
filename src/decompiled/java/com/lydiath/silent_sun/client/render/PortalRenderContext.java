package com.lydiath.silent_sun.client.render;

import net.minecraft.world.entity.LivingEntity;

/**
 * 跨 Mixin 的渲染上下文（ThreadLocal）。
 *
 * <p><b>为什么需要它</b>：残影的**最佳注入点**是拔刀剑的
 * {@code BladeRenderState.renderOverrided(...)} —— 那里能同时拿到
 * <b>精确的刀身矩阵</b>、<b>真实模型面</b>与<b>组名</b>（据此只对 {@code blade} 组写 overlay）。
 * 但该方法的参数里**没有实体**，而本模组的激活判据
 * （{@link PortalBladeCondition#isActive(LivingEntity)}＝Boss + 断魂值）**必须要有实体**。
 *
 * <p>⇒ 采用「双 Mixin + ThreadLocal 传上下文」：{@code LayerMainBladeMixin} 在实体手持刀身的
 * 渲染入栈时把 Boss 放进本上下文，{@code BladeRenderStateMixin} 在刀身写入 buffer 之后读出来判断。
 * 两者在**同一调用栈**（{@code LayerMainBlade.render} → {@code BladeRenderState.renderOverrided}），
 * 渲染又固定在客户端主线程 ⇒ ThreadLocal 安全且无竞态。
 *
 * <p>这个模式照搬自灭却之日的 {@code fx/PortalRenderContext}（交接文档 §五）。
 *
 * <p><b>⚠️ 本类必须在 mixin 包之外</b>（当前在 {@code client.render}）—— 交接文档踩坑清单 #7：
 * 放进 {@code com...mixin} 包会被 Mixin 处理器视为 mixin 包内类，普通代码引用它会
 * {@code IllegalClassLoadError}。
 */
public final class PortalRenderContext {

    /**
     * 当前正在渲染其手持刀身的实体（由 {@code LayerMainBladeMixin} 设置 / 清理）。
     * <p>为 {@code null} 表示"本次 {@code renderOverrided} 调用不来自实体手持刀身"
     *（例如玩家第一人称、GUI 图标）⇒ 调用方应直接跳过，不做 overlay。
     */
    public static final ThreadLocal<LivingEntity> CURRENT_HOLDER = new ThreadLocal<>();

    private PortalRenderContext() {
    }
}
