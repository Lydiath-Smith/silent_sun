package com.lydiath.silent_sun.security;

import com.lydiath.silent_sun.SilentSunMod;
import java.lang.management.ManagementFactory;
import java.security.CodeSource;
import java.util.List;
import java.util.Locale;

/**
 * 运行时注入 / 反调试轻量探测（对应攻击者手段：虚拟机注入修改、虚拟机内存层覆写、注入阶段剔除）。
 * <p>
 * 说明：纯 JVM 层无法阻止 agent / 调试器附加，但可以低频检测常用注入特征并交由反作弊层处理。
 * 检测项：
 * <ul>
 *   <li>① JVM 启动参数含 {@code -agentlib:jdwp}（调试代理）或 {@code -javaagent}（agent 注入，正式服罕见）；</li>
 *   <li>② 由调用方指定的核心防护类的加载来源并非本模组 jar / 开发 class 目录
 *       —— 若攻击者把改造后的 class 塞进其他 jar 并置于 classpath 前端以覆盖原类，可被识别。</li>
 * </ul>
 * 检测结果仅记录并暴露给反作弊层参考，不主动惩罚，避免对正常环境（如开发热重载工具）误伤。
 */
public final class RuntimeInjectionGuard {

    private static volatile boolean agentDetected = false;
    private static volatile boolean classSourceSuspicious = false;
    private static volatile long lastScanMs = 0L;
    private static final long SCAN_INTERVAL_MS = 10_000L;

    private RuntimeInjectionGuard() {
    }

    /** 每 {@link #SCAN_INTERVAL_MS} 毫秒最多执行一次探测；由宿主实体低频调用，{@code guardClass} 为待校验加载来源的核心防护类。 */
    public static void scanIfNeeded(Class<?> guardClass) {
        long now = System.currentTimeMillis();
        if (now - lastScanMs < SCAN_INTERVAL_MS) {
            return;
        }
        lastScanMs = now;
        boolean prevAgent = agentDetected;
        boolean prevSource = classSourceSuspicious;
        try {
            scanAgentFlags();
            scanClassSource(guardClass);
        } catch (Throwable ignored) {
            // 探测失败不影响游戏运行
        }
        // 首次检测到即记录一次，供管理员排查（不主动惩罚，避免误伤正常开发/热重载环境）。
        if (!prevAgent && agentDetected) {
            SilentSunMod.LOGGER.warn("[RuntimeInjectionGuard] 检测到调试器/agent 注入特征（-javaagent / -agentlib:jdwp），请管理员排查。");
        }
        if (!prevSource && classSourceSuspicious) {
            SilentSunMod.LOGGER.warn("[RuntimeInjectionGuard] 检测到核心防护类 " + guardClass.getSimpleName() + " 加载来源可疑（非本模组 jar/开发目录），疑似核心类被覆盖。");
        }
    }

    /** 检测到调试器 / agent 注入特征。 */
    public static boolean agentDetected() {
        return agentDetected;
    }

    /** 核心防护类加载来源可疑（非本模组加载源）。 */
    public static boolean classSourceSuspicious() {
        return classSourceSuspicious;
    }

    private static void scanAgentFlags() {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.contains("-agentlib:jdwp") || lower.contains("-xrunjdwp")) {
                agentDetected = true;
            } else if (lower.contains("-javaagent") && !lower.contains("minecraft")) {
                // 仅记录：部分正常开发工具（热重载等）会挂 agent，不能误伤，交给上层决定是否处置
                agentDetected = true;
            }
        }
    }

    private static void scanClassSource(Class<?> guardClass) {
        CodeSource cs = guardClass.getProtectionDomain().getCodeSource();
        if (cs == null || cs.getLocation() == null) {
            classSourceSuspicious = true;
            return;
        }
        String loc = cs.getLocation().toString().toLowerCase(Locale.ROOT);
        // M3：归一化连字符——jar 常被重命名为 silent-sun-1.0.0.jar（连字符）导致误报。
        // 预期加载源：silent_sun 产物 jar，或开发编译目录（mod-classes、bin/main）。
        String normalized = loc.replace('-', '_');
        if (!normalized.contains("silent_sun") && !normalized.contains("mod_classes") && !normalized.contains("bin/main")) {
            classSourceSuspicious = true;
        }
    }
}
