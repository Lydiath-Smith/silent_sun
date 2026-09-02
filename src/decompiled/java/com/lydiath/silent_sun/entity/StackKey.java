package com.lydiath.silent_sun.entity;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;

/**
 * 物品聚合键：item + 完整 NBT（CompoundTag 具备内容 equals/hashCode）。
 * <p>
 * 2026-09-01 从 AntiCheatLayer 内部类提升为顶层类：Sinytra Connector 的
 * ModuleClassLoader 在转换 jar 时丢失嵌套类 NestHost/NestMembers 信息，导致
 * {@code AntiCheatLayer$StackKey} ClassNotFoundException（创造模式玩家攻击 Boss 时
 * 崩溃：stageCreativeModeGuard → beginCreativeTracking → addStackToAggregate）。
 * 顶层类不依赖 nest 属性，规避该加载问题。
 */
public final class StackKey {
    final Item item;
    final CompoundTag tag;

    StackKey(ItemStack stack) {
        this.item = stack.getItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        this.tag = customData == null ? null : customData.copyTag();
    }

    StackKey(Item item, CompoundTag tag) {
        this.item = item;
        this.tag = tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StackKey other)) return false;
        return this.item == other.item && Objects.equals(this.tag, other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.item, this.tag);
    }
}
