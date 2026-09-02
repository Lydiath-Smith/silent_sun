/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.client;

import com.lydiath.silent_sun.client.render.RediosRenderer;
import com.lydiath.silent_sun.client.render.StarfallCurtainRenderer;
import com.lydiath.silent_sun.client.render.StarfallSalvoRenderer;
import com.lydiath.silent_sun.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer((EntityType)ModEntities.REDIOS.get(), RediosRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntities.STARFALL_SALVO.get(), StarfallSalvoRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntities.STARFALL_CURTAIN.get(), StarfallCurtainRenderer::new);
    }

    private ClientModEvents() {
    }
}

