/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.loot;

import com.lydiath.silent_sun.loot.RediosLootConfig;
import com.lydiath.silent_sun.loot.RediosRewardOverrideConfig;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class RediosLocalConfigReloadListener
extends SimplePreparableReloadListener<Void> {
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    protected void apply(Void object, ResourceManager resourceManager, ProfilerFiller profiler) {
        RediosLootConfig.reload();
        RediosRewardOverrideConfig.reload();
    }
}
