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
            double chance = entry.chance <= 0.0 ? 1.0 : Math.min(1.0, entry.chance);
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

    public record LootEntry(String item, int min, int max, double chance) {
    }
}
