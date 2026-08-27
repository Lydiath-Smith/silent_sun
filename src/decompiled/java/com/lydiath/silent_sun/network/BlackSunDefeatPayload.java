/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.network;

import com.lydiath.silent_sun.SilentSunMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 -> 客户端：黑日天光（2.7）完败时，通知仍存活的参战玩家弹出"死亡特效"界面。
 * <p>
 * 该界面并非原版死亡，而是 Boss 主动展示的败北演出；玩家点击按钮自行选择返回出生点，
 * 不会进入原版死亡流程，因此不增加玩家死亡计数。
 */
public record BlackSunDefeatPayload() implements CustomPacketPayload {
    public static final Type<BlackSunDefeatPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SilentSunMod.MODID, "black_sun_defeat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlackSunDefeatPayload> STREAM_CODEC =
        StreamCodec.unit(new BlackSunDefeatPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理（已在主线程）：通过反射加载客户端专用类并打开死亡特效屏幕，
     * 避免在专用服务端加载 {@code net.minecraft.client} 相关类。
     */
    public static void handleClient(BlackSunDefeatPayload payload, IPayloadContext context) {
        try {
            Class<?> clazz = Class.forName("com.lydiath.silent_sun.client.BlackSunClient");
            clazz.getMethod("openDefeatScreen").invoke(null);
        } catch (Throwable t) {
            SilentSunMod.LOGGER.warn("Failed to open black sun defeat screen", t);
        }
    }
}
