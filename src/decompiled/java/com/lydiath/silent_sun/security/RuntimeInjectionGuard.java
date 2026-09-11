package com.lydiath.silent_sun.security;

import com.lydiath.silent_sun.SilentSunMod;
import java.lang.management.ManagementFactory;
import java.security.CodeSource;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.loading.FMLEnvironment;

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
        // 2026-09-11（代码审计 G04 #6 修复）：开发环境跳过整条加载来源校验。
        // 原白名单是三个 contains（silent_sun / mod_classes / bin/main），不含 Gradle 的
        // build/classes/java/main ⇒ 开发环境每次启动必打一条「核心类被覆盖」的误导性 WARN
        // （实测该目录确实是标准 compileJava 输出，且本项检测结果全库无任何消费者，纯噪音）。
        // dev 的类路径形态（IDE 输出 / Gradle 输出 / 热重载）永远追不上字符串白名单；而本检测的
        // 攻防意义只在正式服与整合包（攻击者把改造后的 class 塞进其他 jar 抢 classpath 前端）。
        // 故 dev 直接跳过来源校验，scanAgentFlags() 不受影响，生产判据完全不变。
        // 注：不采用「把 build/classes/java/main 加进白名单」的写法——contains 匹配下加宽泛子串
        // （classes / java / build）会实质削弱防护，而该条目本身就是 Eclipse 时代的遗留
        // （bin/main 在本 Gradle 工程只是资源源目录，作为类加载来源几乎不可能命中）。
        if (!FMLEnvironment.production) {
            return;
        }
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
