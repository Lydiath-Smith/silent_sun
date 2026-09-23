package com.lydiath.silent_sun.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

@EventBusSubscriber(modid = "silent_sun", value = Dist.CLIENT)
public class CustomBossBarRenderer {

    // 使用已剔除白底的干净贴图
    private static final ResourceLocation CUSTOM_BAR = ResourceLocation.fromNamespaceAndPath("silent_sun", "textures/gui/custom_boss_bar_clean.png");
    private static final int TEX_WIDTH = 784;
    private static final int TEX_HEIGHT = 336;
    
    // 强制压扁缩放，贴近原版 BossBar 的细长比例，高度放大约 2.5 倍 (12 -> 30)
    private static final int DRAW_WIDTH = 256;
    private static final int DRAW_HEIGHT = 30;

    @SubscribeEvent
    public static void onRenderBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bossEvent = event.getBossEvent();
        String nameStr = bossEvent.getName().getString();
        
        // 匹配 Redios Boss
        if (nameStr.contains("莱德厄斯") || nameStr.contains("Redios") || nameStr.contains("碎镜之影")) {
            // 拦截并取消原版 Boss Bar 的渲染
            event.setCanceled(true);

            GuiGraphics guiGraphics = event.getGuiGraphics();
            int screenWidth = event.getWindow().getGuiScaledWidth();
            
            // 居中计算
            int x = (screenWidth - DRAW_WIDTH) / 2;
            int y = event.getY();
            
            float progress = bossEvent.getProgress();

            // 1. 绘制 Boss 名称和锁血倒计时（同原版，位于血条上方）
            Component name = bossEvent.getName();
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, name, screenWidth / 2, y - 10, 0xFFFFFF);

            // 2. 绘制血条内容物（实心颜色，放在框的下层）
            // 根据图像像素精测计算：原图(784x336)中间镂空区域的边界为 Left:112, Right:672, Top:114, Bottom:223
            // 映射到 256x30 的尺寸：
            // 严格对齐边缘：marginX ≈ 37, marginY ≈ 10
            // 为了视觉上的“嵌入感”更好，我们让实心内容向外扩张 1 像素，与半透明的边框内侧产生轻微重叠（不会溢出到透明区域）
            int marginX = 36;
            int marginY = 9;
            int fillMaxWidth = 256 - marginX * 2; 
            int fillHeight = 30 - marginY * 2;    
            int currentFillWidth = (int) (fillMaxWidth * progress);

            // 绘制当前血量（紫色，符合Boss本身的颜色设定，如图中所示）
            if (currentFillWidth > 0) {
                guiGraphics.fill(x + marginX, y + marginY, x + marginX + currentFillWidth, y + marginY + fillHeight, 0xFFA020F0);
            }

            // 绘制底槽（空血部分的深灰色），只绘制未被紫条覆盖的区域，避免重叠渲染
            if (currentFillWidth < fillMaxWidth) {
                guiGraphics.fill(x + marginX + currentFillWidth, y + marginY, x + marginX + fillMaxWidth, y + marginY + fillHeight, 0xFF333333);
            }

            // 3. 绘制边框（完整不裁剪，不受进度影响）
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            guiGraphics.blit(CUSTOM_BAR, x, y, DRAW_WIDTH, DRAW_HEIGHT, 0.0f, 0.0f, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
            
            // 增加 Y 轴增量，确保如果有多个 BossBar，下一个会画在下面
            event.setIncrement(DRAW_HEIGHT + 12);
        }
    }
}