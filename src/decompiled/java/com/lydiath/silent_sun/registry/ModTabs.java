/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.registry;

import com.lydiath.silent_sun.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create((ResourceKey)Registries.CREATIVE_MODE_TAB, "silent_sun");
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder().title((Component)Component.translatable("itemGroup.silent_sun")).withTabsBefore(new ResourceKey[]{CreativeModeTabs.COMBAT}).icon(() -> ((Item)ModItems.REDIOS_SIGIL.get()).getDefaultInstance()).displayItems((parameters, output) -> { output.accept(ModItems.REDIOS_SIGIL.get()); output.accept(ModItems.REDIOS_DISC_PHASE1.get()); output.accept(ModItems.REDIOS_DISC_PHASE2.get()); }).build());

    private ModTabs() {
    }
}

