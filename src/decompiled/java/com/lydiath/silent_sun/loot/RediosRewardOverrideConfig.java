/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lydiath.silent_sun.SilentSunMod;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.loading.FMLPaths;

public final class RediosRewardOverrideConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile List<RewardOverride> overrides = List.of();
    private static volatile String lastError = null;

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("silent_sun").resolve("redios_reward_overrides.json");
    }

    public static void loadOrCreate() {
        RediosRewardOverrideConfig.reload();
    }

    public static boolean reload() {
        Path path = RediosRewardOverrideConfig.getConfigPath();
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            if (!Files.exists(path, new LinkOption[0])) {
                try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, new OpenOption[0]);){
                    writer.write(GSON.toJson(overrides));
                }
                lastError = null;
                return true;
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);){
                RewardOverride[] parsed = (RewardOverride[])GSON.fromJson((Reader)reader, RewardOverride[].class);
                overrides = parsed == null ? List.of() : List.of(parsed);
            }
            lastError = null;
            return true;
        }
        catch (Exception e) {
            SilentSunMod.LOGGER.warn("Failed to load {}: {}", (Object)path, (Object)e.toString());
            overrides = List.of();
            lastError = e.toString();
            return false;
        }
    }

    public static int overrideCount() {
        return overrides.size();
    }

    public static String lastError() {
        return lastError;
    }

    public static List<ItemStack> getOverrideStacks(int phase, int titleIndex) {
        for (RewardOverride o : overrides) {
            // TODO(审计清理 G11 #5)：重复的 (phase,titleIndex) 覆盖条目只有第一条生效（命中即 return），其余同键条目静默失效、无告警 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
            if (o == null || o.phase != phase || o.titleIndex != titleIndex) continue;
            List<RewardItem> items = o.items;
            if (items == null || items.isEmpty()) {
                return List.of();
            }
            ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
            for (RewardItem item : items) {
                int count;
                Item registryItem;
                if (item == null || item.item == null || (registryItem = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.parse((String)item.item))) == null || (count = Math.max(0, item.count)) <= 0) continue;
                stacks.add(new ItemStack((ItemLike)registryItem, count));
            }
            return stacks;
        }
        return List.of();
    }

    private RediosRewardOverrideConfig() {
    }

    public record RewardItem(String item, int count) {
    }

    public record RewardOverride(int phase, int titleIndex, List<RewardItem> items) {
    }
}
