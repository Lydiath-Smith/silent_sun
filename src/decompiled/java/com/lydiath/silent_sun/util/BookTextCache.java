package com.lydiath.silent_sun.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BookTextCache {
    private static final Map<ResourceLocation, String> CACHE = new ConcurrentHashMap<>();

    public static String getOrDefault(MinecraftServer server, ResourceLocation id, String fallback) {
        if (server == null || id == null) return fallback;
        return CACHE.computeIfAbsent(id, key -> {
            try {
                Resource res = server.getResourceManager().getResourceOrThrow(key);
                try (InputStream in = res.open()) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
                return fallback;
            }
        });
    }

    public static void clear() {
        CACHE.clear();
    }

    private BookTextCache() {
    }
}
