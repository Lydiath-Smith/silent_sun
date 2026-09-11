/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.client;

import com.lydiath.silent_sun.block.CleavingPainBlock;
import com.lydiath.silent_sun.block.CleavingPainBlockEntity;
import com.lydiath.silent_sun.client.render.RediosRenderer;
import com.lydiath.silent_sun.client.render.StarfallCurtainRenderer;
import com.lydiath.silent_sun.client.render.StarfallSalvoRenderer;
import com.lydiath.silent_sun.registry.ModBlocks;
import com.lydiath.silent_sun.registry.ModEntities;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

public final class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer((EntityType)ModEntities.REDIOS.get(), RediosRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntities.STARFALL_SALVO.get(), StarfallSalvoRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntities.STARFALL_CURTAIN.get(), StarfallCurtainRenderer::new);
    }

    /** 祭坛整体用 translucent：水（半透明）与底座透明擦除区都正确渲染（2026-09-08 用户裁决，对齐炼药锅）。 */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.CLEAVING_PAIN.get(), RenderType.translucent());
    }

    /** 只有水状态（fluid=2）着水色 tint；空/岩浆不着色（-1 = 白，即原纹理色）。 */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (state.getValue(CleavingPainBlock.FLUID) == CleavingPainBlockEntity.FLUID_WATER) {
                return level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : -1;
            }
            return -1;
        }, ModBlocks.CLEAVING_PAIN.get());
    }

    private ClientModEvents() {
    }
}
