package com.lydiath.silent_sun.client.render;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD, modid = "silent_sun")
public final class PortalTrailShaders {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static ShaderInstance portalTrailShader;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("silent_sun", "rendertype_portal_trail"),
                    DefaultVertexFormat.POSITION_COLOR
                ),
                shader -> portalTrailShader = shader
            );
        } catch (Exception e) {
            LOGGER.error("shader registration FAILED for rendertype_portal_trail", e);
        }
    }
}
