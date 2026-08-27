/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.client;

import com.lydiath.silent_sun.network.BlackSunRespawnPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 黑日天光（2.7）完败的"死亡特效"界面。
 * <p>
 * 并非原版死亡界面：玩家仍存活，只是被 Boss 强制展示败北演出并自行选择返回出生点。
 * 因此不进入原版死亡流程、不增加死亡计数；极限模式下按钮同样可用。
 */
public final class BlackSunDefeatScreen extends Screen {
    public BlackSunDefeatScreen() {
        super(Component.translatable("screen.silent_sun.redios.black_sun_defeat.title"));
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(
            Component.translatable("screen.silent_sun.redios.black_sun_defeat.respawn"),
            button -> this.onRespawn())
            .bounds(this.width / 2 - 100, this.height / 2 + 40, 200, 20)
            .build());
    }

    private void onRespawn() {
        PacketDistributor.sendToServer(new BlackSunRespawnPayload());
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x80120000, 0xC0180000);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
            Component.translatable("screen.silent_sun.redios.black_sun_defeat.subtitle"),
            this.width / 2, this.height / 2 - 40, 0xD0D0D0);
    }
}
