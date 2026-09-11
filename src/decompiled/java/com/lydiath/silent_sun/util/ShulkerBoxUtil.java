/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.util;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShulkerBoxUtil {
    // 2026-09-11（代码审计 G11 #8 修复）：原二参重载 createShulkerBox(items, name) 全库无调用者，
    // 且把箱色硬编码成 Items.SHULKER_BOX（与现有两处奖励箱的白 / 棕都不符），误用会掉出无色箱 —— 已删除。
    public static ItemStack createShulkerBox(Item boxItem, List<ItemStack> items, Component name) {
        ItemStack box = new ItemStack((ItemLike)boxItem);
        if (name != null) {
            box.set(DataComponents.CUSTOM_NAME, name);
        }
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        // 2026-09-10 防火：奖励盒可能掉在 Boss 死亡的岩浆/火里（本模组祭坛触发时还会生成真实落雷），
        // 被烧毁即全部奖励永久损失；打 FIRE_RESISTANT 组件使其掉落物实体免疫销毁（同下界合金机制）。
        box.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        return box;
    }

    public static boolean placeShulkerBox(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> items, Component name) {
        if (level == null || pos == null || state == null) {
            return false;
        }
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        level.setBlock(pos, state, 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ShulkerBoxBlockEntity)) {
            return false;
        }
        ShulkerBoxBlockEntity shulker = (ShulkerBoxBlockEntity)be;
        int capacity = shulker.getContainerSize();
        int size = Math.min(items.size(), capacity);
        int i = 0;
        while (i < size) {
            shulker.setItem(i, items.get(i));
            ++i;
        }
        shulker.setChanged();
        // 2026-09-11（代码审计 G11 修复）：潜影盒只有 capacity（27）格，超出的奖励原先被**静默丢弃**
        // 却仍返回 true（调用方以为已全额发放）。改为把溢出部分掉落在箱子处，避免奖励永久损失。
        for (int j = capacity; j < items.size(); ++j) {
            ItemStack extra = items.get(j);
            if (extra != null && !extra.isEmpty()) {
                Containers.dropItemStack(level, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, extra);
            }
        }
        return true;
    }

    private ShulkerBoxUtil() {
    }
}
