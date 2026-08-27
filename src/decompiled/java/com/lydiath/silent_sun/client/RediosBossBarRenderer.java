/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.client;

import com.lydiath.silent_sun.SilentSunMod;
import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * 碎镜之影·莱德厄斯 的自定义 Boss 血条（完全替换原版顶部血条）。
 * <p>
 * 渲染走 NeoForge 1.21.1 的 GUI Layer 体系：在 {@link RegisterGuiLayersEvent} 中注册到
 * {@code VanillaGuiLayers.BOSS_OVERLAY} 之上；本类同时监听 {@link CustomizeGuiOverlayEvent.BossEventProgress}
 * 取消原版血条渲染，并把「本帧原版血条可见」作为自定义血条的可见性依据（与原版对玩家的可见性保持一致）。
 */
@EventBusSubscriber(modid = SilentSunMod.MODID, value = Dist.CLIENT)
public final class RediosBossBarRenderer {

    // 尺寸：与原版（182x5）同级偏窄。横向 236 缩 1/3 ≈ 157，纵向 10 减半 = 5。
    private static final int BAR_WIDTH = 157;
    private static final int BAR_HEIGHT = 5;
    private static final int SEGMENTS = 10;

    // 外框图层（image (3).png 的无色抽象矩形）外扩像素。
    // 原纹理 488x36 非 2 的幂，NPOT GUI 纹理在 mipmap 下加载异常显示为丢失纹理，
    // 故改用代码绘制紫黑环（中心透明），效果一致且不依赖纹理加载。
    private static final int FRAME_PAD = 2;
    private static final int COLOR_FRAME = 0xFF221453; // 紫黑外框环（对应原纹理主色）

    // 紫黑色调（AARRGGBB）
    private static final int COLOR_SEG_FULL = 0xFF7C3AED;
    private static final int COLOR_SEG_CURRENT = 0xFFC084FC;
    private static final int COLOR_SEG_EMPTY = 0xFF1E0B30;
    private static final int COLOR_DIVIDER = 0xFF2A1240;
    private static final int COLOR_TEXT = 0xFFF3E8FF;

    /** 本帧原版血条是否正在渲染（由 BossEventProgress 置位，render 消费后复位）。 */
    private static boolean barVisible = false;

    /** 本 Boss 在原版血条堆叠中的槽位 Y（bar 顶部，由 BossEventProgress 记录，用于对齐避免遮挡其它 Boss）。 */
    private static int bossBarY;

    private RediosBossBarRenderer() {
    }

    private static RediosEntity findActiveBoss() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return null;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof RediosEntity redios && redios.isAlive()) {
                return redios;
            }
        }
        return null;
    }

    /** 取消原版血条并标记本帧可见。仅匹配本 Boss 的原版血条，不影响其它 mod 的 Boss 条。 */
    @SubscribeEvent
    public static void onBossEventProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        RediosEntity boss = findActiveBoss();
        if (boss == null) {
            return;
        }
        String bossName = boss.getType().getDescription().getString();
        if (event.getBossEvent().getName().getString().startsWith(bossName)) {
            // 开关开启时：记录本 Boss 的原版槽位、取消原版渲染并绘制自制血条。
            // 开关关闭时：不介入，沿用原版默认血条。
            if (SilentSunConfig.CUSTOM_BOSS_BAR_ENABLED.get()) {
                bossBarY = event.getY();
                event.setCanceled(true);
                barVisible = true;
            }
        }
    }

    /** GUI Layer 入口：在 {@code BOSS_OVERLAY} 之后渲染自定义血条。 */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        boolean visible = barVisible;
        barVisible = false;
        RediosEntity boss = findActiveBoss();
        if (boss == null || !visible) {
            return;
        }
        drawBossBar(graphics, boss);
    }

    private static void drawBossBar(GuiGraphics g, RediosEntity boss) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int barX = (screenW - BAR_WIDTH) / 2;
        // 对齐原版槽位：bar 顶部在 getY()，名字在 getY()-9；外框向外扩 FRAME_PAD，故名字再上移 FRAME_PAD。
        int barY = bossBarY;
        int nameY = barY - 9 - FRAME_PAD;

        // 名称行：阶段 + 实体名 · 头衔（锁血时追加金色倒计时）
        Component name = buildName(boss);
        int lockTicks = boss.getClientTitleLockTicks();
        if (lockTicks > 0) {
            name = name.copy().append(Component.literal(" \u00b7 " + formatSeconds(lockTicks)).withStyle(ChatFormatting.GOLD));
        }
        g.drawCenteredString(font, name, screenW / 2, nameY, COLOR_TEXT);

        // 外框：紫黑环作为血条外框图层（套在血条上，代码绘制）。
        drawFrame(g, barX - FRAME_PAD, barY - FRAME_PAD, BAR_WIDTH + FRAME_PAD * 2, BAR_HEIGHT + FRAME_PAD * 2);

        // 10 段血条（左满右空：血量从右向左削减，右边先空）
        drawSegments(g, boss, barX, barY);
    }

    private static Component buildName(RediosEntity boss) {
        int phase = boss.getClientPhase();
        int titleIndex = boss.getClientTitleIndex();
        Component phaseC = Component.translatable("hud.silent_sun.redios.phase" + phase);
        Component titleC = Component.translatable("title.silent_sun.redios.phase" + phase + "." + titleIndex);
        return Component.literal("[").append(phaseC).append("] ")
            .append(boss.getType().getDescription().copy())
            .append(Component.literal(" \u00b7 "))
            .append(titleC);
    }

    /** 用代码绘制紫黑外框环（中心透明），替代纹理，避免 NPOT 纹理加载异常导致丢失纹理。 */
    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
        int t = FRAME_PAD;
        g.fill(x, y, x + w, y + t, COLOR_FRAME);              // 上边
        g.fill(x, y + h - t, x + w, y + h, COLOR_FRAME);      // 下边
        g.fill(x, y + t, x + t, y + h - t, COLOR_FRAME);      // 左边
        g.fill(x + w - t, y + t, x + w, y + h - t, COLOR_FRAME); // 右边
    }

    private static void drawSegments(GuiGraphics g, RediosEntity boss, int barX, int barY) {
        int segW = BAR_WIDTH / SEGMENTS;
        float maxHealth = boss.getMaxHealth();
        float health = boss.getHealth();
        float segHp = maxHealth / SEGMENTS;

        for (int i = 0; i < SEGMENTS; i++) {
            int sx = barX + i * segW;
            // 第 i 段覆盖血量区间 [i*segHp, (i+1)*segHp]（i=0 为最左）。
            // 满血段从左往右，血量下降时右侧段先空。
            float segLow = i * segHp;
            float segHigh = segLow + segHp;
            float fill;
            if (health >= segHigh) {
                fill = 1.0f;
            } else if (health <= segLow) {
                fill = 0.0f;
            } else {
                fill = (health - segLow) / segHp;
            }
            fill = Math.max(0.0f, Math.min(1.0f, fill));

            int innerX = sx + 1;
            int innerY = barY + 1;
            int innerW = segW - 2;
            int innerH = BAR_HEIGHT - 2;

            // 底色（空段）
            g.fill(innerX, innerY, innerX + innerW, innerY + innerH, COLOR_SEG_EMPTY);
            if (fill > 0.0f) {
                int fillW = Math.max(1, (int) (innerW * fill));
                boolean current = health >= segLow && health < segHigh;
                int fillColor = current ? COLOR_SEG_CURRENT : COLOR_SEG_FULL;
                g.fill(innerX, innerY, innerX + fillW, innerY + innerH, fillColor);
            }
            // 段分隔线
            if (i < SEGMENTS - 1) {
                g.fill(sx + segW - 1, barY, sx + segW, barY + BAR_HEIGHT, COLOR_DIVIDER);
            }
        }
    }

    private static String formatSeconds(int ticks) {
        return String.format("%.1fs", ticks / 20.0f);
    }
}
