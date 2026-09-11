package com.lydiath.silent_sun.util;

import com.lydiath.silent_sun.SilentSunMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BookTextCache {
    private static final Map<ResourceLocation, String> CACHE = new ConcurrentHashMap<>();

    /**
     * 取结局书正文；成功才入缓存。
     * <p>
     * 2026-09-11（代码审计 G04 #4 修复）：原实现是 {@code CACHE.computeIfAbsent(id, key -> ...)}
     * 且异常分支直接 {@code return fallback} —— lambda 的返回值会被当作「这本书的正文」写进缓存：
     * ① 资源缺失/路径拼错时，兜底文案被**永久固化**，此后补回文件也不生效（只能等 {@code /reload}
     * 触发 {@code BookTextReloadListener} 的 {@link #clear()}）；
     * ② {@code catch (Exception ignored)} 无日志，整合包作者只看到「书的文案不对」，无从定位；
     * ③ 配置键 {@code redios_note_phase1_win_phase2_lose} 的旧值同样会被固化。
     * 现改为「先查 → miss 时读 → **只有成功才 put**」：失败不入缓存，下次调用自动重试（自愈）。
     */
    public static String getOrDefault(MinecraftServer server, ResourceLocation id, String fallback) {
        if (server == null || id == null) return fallback;
        String cached = CACHE.get(id);
        if (cached != null) return cached;
        try {
            Resource res = server.getResourceManager().getResourceOrThrow(id);
            String text;
            try (InputStream in = res.open()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            CACHE.put(id, text);
            return text;
        } catch (Exception e) {
            // 不入缓存 —— 这是「自愈」的关键，勿改回 computeIfAbsent
            SilentSunMod.LOGGER.warn("[BookTextCache] 结局书文本加载失败，本次使用回退文案：{}", id, e);
            return fallback;
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    private BookTextCache() {
    }
}
