/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.registry;

import com.lydiath.silent_sun.item.CleavingPainBlockItem;
import com.lydiath.silent_sun.item.RediosDiscItem;
import com.lydiath.silent_sun.item.RediosSigilItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("silent_sun");
    public static final DeferredHolder<Item, Item> REDIOS_SIGIL = ITEMS.register("redios_sigil", () -> new RediosSigilItem(new Item.Properties().stacksTo(1).fireResistant()));
    /** 裂解之痛（召唤祭坛）方块物品（2026-09-04；tooltip 补全 2026-09-09；2026-09-10 防火：
     *  祭坛用法强制相邻岩浆且触发时生成真实落雷，掉落物被烧毁即永久损失，故打 FIRE_RESISTANT 组件）。 */
    public static final DeferredHolder<Item, Item> CLEAVING_PAIN = ITEMS.register("cleaving_pain",
        () -> new CleavingPainBlockItem(ModBlocks.CLEAVING_PAIN.get(), new Item.Properties().fireResistant()));
    /**
     * 一阶段战斗曲唱片：掉落于一阶段奖励，音频复用 redios_battle_music_phase1。
     * 封面贴图来自用户提供的 image (1).png（转为圆形唱片贴图）。
     */
    public static final DeferredHolder<Item, Item> REDIOS_DISC_PHASE1 = ITEMS.register("redios_disc_phase1", () -> new RediosDiscItem(new Item.Properties().stacksTo(1).fireResistant().component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(new EitherHolder<>(ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_disc_phase1"))), true)), "tooltip.silent_sun.redios_disc.phase1"));
    /**
     * 二阶段战斗曲唱片：掉落于二阶段奖励，音频复用 redios_battle_music_phase2。
     * 封面贴图来自用户提供的 image (2).png（转为圆形唱片贴图）。
     */
    public static final DeferredHolder<Item, Item> REDIOS_DISC_PHASE2 = ITEMS.register("redios_disc_phase2", () -> new RediosDiscItem(new Item.Properties().stacksTo(1).fireResistant().component(DataComponents.JUKEBOX_PLAYABLE, new JukeboxPlayable(new EitherHolder<>(ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_disc_phase2"))), true)), "tooltip.silent_sun.redios_disc.phase2"));
    // 2026-09-14（体检 P2-C / G01 #7 **已闭环**）：item.silent_sun.redios_trident 翻译键已补入**两份真源 lang**
    // （zh_cn「莱德厄斯之刃」/ en_us「Redios's Blade」，名称由执行者拟定，作者可一句改）。
    // ⚠️ lang 真源在 **bin/main**（assets/data 手写资源），**不是** src/main/resources —— 改文案改这里。
    // 原 TODO(审计清理 G01 #7) 已闭环，勿重做。
    /**
     * 莱德厄斯常规状态手持的三叉戟型武器（未拔刀形态）。
     * 模型复用拔刀剑 OBJ（models/item/miedao_duan.obj，见 redios_trident.json），
     * 属性对齐原版三叉戟。仅 Boss 装备使用，不进创造栏、不参与掉落。
     * 注意：不能用 TridentItem 子类——1.21.1 的 ItemStackRenderer 对 TridentItem
     * 走 BEWLR 特判（渲染原版三叉戟模型），OBJ 模型会失效，故用普通 Item。
     */
    public static final DeferredHolder<Item, Item> REDIOS_TRIDENT = ITEMS.register("redios_trident", () -> new Item(new Item.Properties().stacksTo(1).attributes(
        ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 7.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.9, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build())));

    private ModItems() {
    }
}

