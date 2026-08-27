/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.client;

import net.minecraft.client.Minecraft;

/**
 * 客户端专用入口，供公共代码通过反射调用以打开黑日天光完败界面，
 * 避免在专用服务端加载客户端 GUI 类。
 */
public final class BlackSunClient {
    public static void openDefeatScreen() {
        Minecraft.getInstance().setScreen(new BlackSunDefeatScreen());
    }

    private BlackSunClient() {
    }
}
