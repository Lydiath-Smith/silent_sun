package com.lydiath.silent_sun.datagen;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.lydiath.silent_sun.registry.ModSounds;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.JukeboxSong;

/**
 * 唱片歌曲（jukebox_song）注册表数据生成。
 * <p>
 * 输出 {@code data/silent_sun/jukebox_song/redios_disc_phase{1,2}.json}，
 * 与 {@code ModItems.REDIOS_DISC_PHASE1/2} 的 {@code JukeboxPlayable} 引用对应
 * （ResourceKey → silent_sun:redios_disc_phase1 / redios_disc_phase2）。
 */
public final class ModJukeboxSongProvider implements DataProvider {

    private final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> registries;

    public ModJukeboxSongProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.pathProvider = output.createRegistryElementsPathProvider(Registries.JUKEBOX_SONG);
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return this.registries.thenCompose(regs -> {
            CompletableFuture<?>[] futures = new CompletableFuture<?>[]{
                save(cache, regs, "redios_disc_phase1", ModSounds.REDIOS_BATTLE_MUSIC_PHASE1, 223.0f),
                save(cache, regs, "redios_disc_phase2", ModSounds.REDIOS_BATTLE_MUSIC_PHASE2, 218.39f),
            };
            return CompletableFuture.allOf(futures);
        });
    }

    private CompletableFuture<?> save(CachedOutput cache, HolderLookup.Provider regs,
                                      String name, Holder<SoundEvent> sound, float lengthSeconds) {
        JukeboxSong song = new JukeboxSong(
            sound,
            Component.translatable("item.silent_sun." + name + ".desc"),
            lengthSeconds,
            15);
        Path path = this.pathProvider.json(ResourceLocation.fromNamespaceAndPath("silent_sun", name));
        return DataProvider.saveStable(cache, regs, JukeboxSong.DIRECT_CODEC, song, path);
    }

    @Override
    public String getName() {
        return "Silent Sun Jukebox Songs";
    }
}
