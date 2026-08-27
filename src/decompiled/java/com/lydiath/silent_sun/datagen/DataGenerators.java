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
@EventBusSubscriber(modid = SilentSunMod.MODID, bus = EventBusSubscriber.Bus.MOD)
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
