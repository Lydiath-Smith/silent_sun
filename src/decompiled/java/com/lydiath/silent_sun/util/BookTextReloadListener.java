package com.lydiath.silent_sun.util;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class BookTextReloadListener extends SimplePreparableReloadListener<Void> {
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    protected void apply(Void object, ResourceManager resourceManager, ProfilerFiller profiler) {
        BookTextCache.clear();
    }
}

