package com.lydiath.silent_sun.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * 裂解之痛（召唤祭坛）方块物品：tooltip 完整说明使用流程
 * （2026-09-09 用户测试期间要求补全：装岩浆→投钻石→雷击掉召唤器→换水→召唤）。
 */
public final class CleavingPainBlockItem extends BlockItem {
    public CleavingPainBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.silent_sun.cleaving_pain.1").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.silent_sun.cleaving_pain.2").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.silent_sun.cleaving_pain.3").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.silent_sun.cleaving_pain.4").withStyle(ChatFormatting.GRAY));
    }
}
