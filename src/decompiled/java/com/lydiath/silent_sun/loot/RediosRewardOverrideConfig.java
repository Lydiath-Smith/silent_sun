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
            // 2026-09-12（审计清理 G11 #5 修复，选项①：只补日志、不改生效语义）：对重复的 (phase,titleIndex)
            // 键发一次 WARN，把「配置写了却不生效」变成可见。**保持「第一条生效」**，不改为后写覆盖前写 ——
            // 理由：已成型的配置文件里若已存在重复键，改成后写覆盖会让既有存档的掉落奖励凭空变化（发给玩家
            // 的物品会变），属高风险静默行为变更；而保留先写语义 + 告警既能提示管理员，又零行为变化。
            warnDuplicateKeys(overrides);
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
            // 2026-09-12（审计清理 G11 #5 已清理）：本循环「命中即 return」＝同键条目**第一条生效**、其余静默失效。
            // 该语义**刻意保留**（改成后写覆盖前写会让既有配置文件的掉落凭空变化，属高风险行为变更）；
            // 未生效的重复键现在由 reload() 里的 warnDuplicateKeys() 明确告警。
            // 依据：docs\_审计-2026-09-11\G11.md §5（两个消费点行为一致，问题不会自行暴露）。
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

    /** 2026-09-12（审计清理 G11 #5）：告警重复的 (phase,titleIndex) 键 —— 生效的是**先出现**的那一条。 */
    private static void warnDuplicateKeys(List<RewardOverride> list) {
        ArrayList<String> seen = new ArrayList<String>();
        for (RewardOverride o : list) {
            if (o == null) continue;
            String key = "phase=" + o.phase + ",titleIndex=" + o.titleIndex;
            if (seen.contains(key)) {
                SilentSunMod.LOGGER.warn(
                    "[Redios] 奖励覆盖配置存在重复键 {}：生效的是**先出现**的那一条，本条被忽略（getOverrideStacks 命中即 return）。请删除重复条目。",
                    key);
            } else {
                seen.add(key);
            }
        }
    }

    private RediosRewardOverrideConfig() {
    }

    public record RewardItem(String item, int count) {
    }

    public record RewardOverride(int phase, int titleIndex, List<RewardItem> items) {
    }
}
