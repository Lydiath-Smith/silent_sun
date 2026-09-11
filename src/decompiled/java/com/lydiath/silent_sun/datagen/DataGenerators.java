package com.lydiath.silent_sun.datagen;

import com.lydiath.silent_sun.SilentSunMod;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * 数据生成入口（mod bus 事件）。
 * <p>
 * 生成 silent_sun 的 advancement 与 jukebox_song 注册表数据到
 * {@code src/generated/resources}（build.gradle 的 data run 已挂 output 与 existing）。
 * 运行：{@code gradlew.bat runData}。
 */
// B-21（2026-09-11 依设计 §十三 L1001 约定）：bus 参数自 1.21.1 起 @Deprecated(forRemoval = true)，
// 其 javadoc 明写「this value is ignored, and the bus is determined automatically.
// Do not specify a bus at all」→ 去掉显式 bus，由事件类型自动判定（GatherDataEvent 属 mod bus）。
@EventBusSubscriber(modid = SilentSunMod.MODID)
public final class DataGenerators {

    private DataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        // advancement 与 jukebox_song 均为 server 侧注册表数据（--all / --server 时执行）。
        generator.addProvider(event.includeServer(),
            new ModAdvancementProvider(packOutput, lookup,
                List.of(new ModAdvancementProvider.ModAdvancementSubProvider())));
        generator.addProvider(event.includeServer(),
            new ModJukeboxSongProvider(packOutput, lookup));
    }
}
