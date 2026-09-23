package com.lydiath.silent_sun.client.render;

import java.lang.reflect.Method;

/**
 * Iris（光影）状态检测 —— **反射软依赖**，不硬引用 Iris。
 *
 * <p><b>为什么需要它</b>（2026-09-13/14 灭却之日实测 + 官方文档，见其交接文档 §十）：
 * <blockquote>Custom shaders added by mods or resource packs are <b>ignored by Iris</b> when an Iris
 * shader pack is loaded. …it is recommended to <b>create fallback rendering pathways that do not use
 * custom shaders</b>.</blockquote>
 * ⇒ 本模组的 {@code PortalTrailRenderType.PORTAL_TRAIL} 是**自建 shader** ⇒ **光影开启时完全不渲染**
 *（静默不显示、无报错）。甚至**原版 {@code RenderType.endPortal()} 在光影下同样不渲染**（同日实测更正）——
 * 因为 Iris 会把它一并重定向到 gbuffers，而光影包对 {@code end_portal} 这类专用多层着色器没有实现。
 *
 * <p><b>唯一可靠通道</b>是前置（拔刀剑）的
 * {@code BladeRenderState.getSlashBladeBlendLuminous(ResourceLocation)} —— 它走
 * {@code RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER} 这条**常规实体 gbuffer 通道**，光影包认识。
 *
 * <p><b>⚠️ Method 缓存、但结果不缓存</b>：玩家可以在游戏运行中开关光影 ⇒ 每次问都重新取值。
 * 反射调用本身每帧两次，相对渲染开销可忽略。
 */
public final class IrisCompat {

    private static volatile boolean tried = false;
    private static volatile Method getInstanceMethod;
    private static volatile Method isShaderPackInUseMethod;

    private IrisCompat() {
    }

    private static void ensure() {
        if (tried) {
            return;
        }
        tried = true;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            getInstanceMethod = api.getMethod("getInstance");
            isShaderPackInUseMethod = api.getMethod("isShaderPackInUse");
        } catch (Exception ignored) {
            // 未安装 Iris ⇒ 两个字段保持 null ⇒ isShaderPackInUse() 恒为 false（当作关闭处理）
        }
    }

    /**
     * 当前是否正在使用光影包。
     * <p>⚠️ **结果不缓存**（玩家可运行中切换光影）；反射不可用时按 {@code false} 处理。
     */
    public static boolean isShaderPackInUse() {
        ensure();
        Method getInst = getInstanceMethod;
        Method isOn = isShaderPackInUseMethod;
        if (getInst == null || isOn == null) {
            return false;
        }
        try {
            Object instance = getInst.invoke(null);
            Object result = isOn.invoke(instance);
            return result instanceof Boolean b && b;
        } catch (Exception ignored) {
            return false;
        }
    }
}
