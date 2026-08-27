/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 莱德厄斯战斗曲唱片：保留原版唱片机“歌名 · 创作者”描述，
 * 并在其下追加一行“Boss 阶段”提示（灰色斜体）。
 */
public final class RediosDiscItem extends Item {
    private final String phaseTooltipKey;

    public RediosDiscItem(Item.Properties properties, String phaseTooltipKey) {
        super(properties);
        this.phaseTooltipKey = phaseTooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable(this.phaseTooltipKey)
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
