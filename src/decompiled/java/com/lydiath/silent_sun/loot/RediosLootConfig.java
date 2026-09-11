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
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.loading.FMLPaths;

public final class RediosLootConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile List<LootEntry> entries = RediosLootConfig.defaultEntries();
    private static volatile String lastError = null;

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("silent_sun").resolve("redios_loot.json");
    }

    public static void loadOrCreate() {
        RediosLootConfig.reload();
    }

    public static boolean reload() {
        Path path = RediosLootConfig.getConfigPath();
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);
            if (!Files.exists(path, new LinkOption[0])) {
                try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, new OpenOption[0]);){
                    writer.write(GSON.toJson(entries));
                }
                lastError = null;
                return true;
            }
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);){
                LootEntry[] parsed = (LootEntry[])GSON.fromJson((Reader)reader, LootEntry[].class);
                entries = parsed == null ? RediosLootConfig.defaultEntries() : List.of(parsed);
            }
            lastError = null;
            return true;
        }
        catch (Exception e) {
            SilentSunMod.LOGGER.warn("Failed to load {}: {}", (Object)path, (Object)e.toString());
            entries = RediosLootConfig.defaultEntries();
            lastError = e.toString();
            return false;
        }
    }

    public static int entryCount() {
        return entries.size();
    }

    public static String lastError() {
        return lastError;
    }

    public static List<ItemStack> roll(RandomSource random) {
        ArrayList<ItemStack> stacks = new ArrayList<ItemStack>();
        for (LootEntry entry : entries) {
            if (entry == null || entry.item == null || entry.item.isBlank()) {
                continue;
            }
            // 2026-09-11（代码审计 G11 修复）：原实现直接 ResourceLocation.parse(entry.item)——
            // 物品 id 拼错或含非法字符时抛 ResourceLocationException 逃出 roll()，而 roll() 在
            // 结算生成掉落时被调用 → 整次结算中断。改用 tryParse 跳过非法条目并告警。
            // 另注：Registry.get() 对未注册 id 返回 Items.AIR 而非 null，原先的 == null 判据无效。
            ResourceLocation id = ResourceLocation.tryParse(entry.item);
            if (id == null) {
                SilentSunMod.LOGGER.warn("redios_loot.json 条目物品 id 非法，已跳过：{}", entry.item);
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) {
                SilentSunMod.LOGGER.warn("redios_loot.json 条目物品未注册，已跳过：{}", entry.item);
                continue;
            }
            // 2026-09-11（代码审计 G11 #4 修复 / 作者裁决=方案 C）：区分「字段缺失」与「显式 0」。
            // 原实现 `entry.chance <= 0.0 ? 1.0 : min(1.0, chance)` 让三种写法全变成「必掉」：
            //   · "chance": 0   → 管理员本意「永不掉落」→ 实际 100% 掉
            //   · 键缺失        → record 的 double 默认 0.0 → 同样 100% 掉
            //   · "chance": NaN → NaN<=0 为 false、min(1.0,NaN)=NaN、nextDouble()>NaN 恒 false → 100% 掉
            // 现口径（字段类型改为 Double 以区分 null）：
            //   · 键缺失(null) = 必掉（兼容存量配置，零破坏）
            //   · 显式 0 / 负数 = 不掉（与姊妹配置 RediosRewardOverrideConfig 的 count「0 = 不给」对齐）
            //   · NaN = 非法值，跳过并告警
            Double rawChance = entry.chance;
            if (rawChance == null) {
                rawChance = Double.valueOf(1.0);
            } else if (rawChance.isNaN()) {
                SilentSunMod.LOGGER.warn("redios_loot.json 条目 chance 非法（NaN），已跳过：{}", entry.item);
                continue;
            }
            double chance = Math.max(0.0, Math.min(1.0, rawChance.doubleValue()));
            if (chance <= 0.0) continue;
            if (random.nextDouble() > chance) continue;
            int min = Math.max(0, entry.min);
            int max = Math.max(min, entry.max);
            int count = min == max ? min : min + random.nextInt(max - min + 1);
            if (count <= 0) continue;
            stacks.add(new ItemStack((ItemLike)item, count));
        }
        return stacks;
    }

    private static List<LootEntry> defaultEntries() {
        return List.of(new LootEntry("minecraft:nether_star", 1, 1, 1.0), new LootEntry("minecraft:diamond", 8, 16, 1.0), new LootEntry("minecraft:enchanted_golden_apple", 1, 1, 0.25));
    }

    private RediosLootConfig() {
    }

    /**
     * 掉落条目。
     * <p>
     * 2026-09-11（代码审计 G11 #4 / 作者裁决）：{@code chance} 由 {@code double} 改为 {@link Double}，
     * 以便区分「JSON 省略该键」（Gson 反射构造 → null）与「显式写 0」（不掉）。
     * 若仍是基本类型 double，省略键会被填成 0.0，与显式 0 无法区分，
     * 于是「0 = 不掉」与「省略 = 必掉」这两个语义不可能同时成立。
     */
    public record LootEntry(String item, int min, int max, Double chance) {
    }
}
