package com.lydiath.silent_sun.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.lydiath.silent_sun.registry.ModItems;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * Redios 战斗相关的 advancement 数据生成。
 * <p>
 * 5 个 advancement 全部使用 {@code minecraft:impossible} 触发——由
 * {@code RediosEntity.grantAdvancement} / {@code grantAdvancementToParticipants}
 * 在战斗流程中运行时授予（phase1_clear / phase2_countdown / phase2_win /
 * teleport_expel），root 为无父根进度。
 */
public final class ModAdvancementProvider extends AdvancementProvider {

    public ModAdvancementProvider(PackOutput output,
                                  CompletableFuture<HolderLookup.Provider> registries,
                                  List<AdvancementSubProvider> subProviders) {
        super(output, registries, subProviders);
    }

    /** 子 provider：实际定义 5 个 advancement 条目。 */
    public static final class ModAdvancementSubProvider implements AdvancementSubProvider {
        private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/obsidian.png");

        private static Criterion<?> impossible() {
            return new Criterion<>(CriteriaTriggers.IMPOSSIBLE, new ImpossibleTrigger.TriggerInstance());
        }

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver) {
            // TODO(审计清理 G11 #7)：root advancement 无任何运行时授予点（全库只授 phase1_clear / phase2_countdown / phase2_win / teleport_expel），配 impossible 触发即永不可获得 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
            AdvancementHolder root = Advancement.Builder.advancement()
                .display(
                    ModItems.REDIOS_SIGIL.get(),
                    Component.translatable("advancement.silent_sun.root.title"),
                    Component.translatable("advancement.silent_sun.root.desc"),
                    BACKGROUND,
                    AdvancementType.TASK,
                    false,   // showToast
                    false,   // announceToChat
                    false)   // hidden
                .addCriterion("trigger", impossible())
                .requirements(AdvancementRequirements.Strategy.AND)
                .save(saver, "silent_sun:root");

            Advancement.Builder.advancement()
                .parent(root)
                .display(
                    ModItems.REDIOS_DISC_PHASE1.get(),
                    Component.translatable("advancement.silent_sun.phase1_clear.title"),
                    Component.translatable("advancement.silent_sun.phase1_clear.desc"),
                    null,
                    AdvancementType.GOAL,
                    true, true, false)
                .addCriterion("trigger", impossible())
                .requirements(AdvancementRequirements.Strategy.AND)
                .save(saver, "silent_sun:phase1_clear");

            Advancement.Builder.advancement()
                .parent(root)
                .display(
                    Items.CLOCK,
                    Component.translatable("advancement.silent_sun.phase2_countdown.title"),
                    Component.translatable("advancement.silent_sun.phase2_countdown.desc"),
                    null,
                    AdvancementType.GOAL,
                    true, true, false)
                .addCriterion("trigger", impossible())
                .requirements(AdvancementRequirements.Strategy.AND)
                .save(saver, "silent_sun:phase2_countdown");

            Advancement.Builder.advancement()
                .parent(root)
                .display(
                    ModItems.REDIOS_DISC_PHASE2.get(),
                    Component.translatable("advancement.silent_sun.phase2_win.title"),
                    Component.translatable("advancement.silent_sun.phase2_win.desc"),
                    null,
                    AdvancementType.CHALLENGE,
                    true, true, false)
                .addCriterion("trigger", impossible())
                .requirements(AdvancementRequirements.Strategy.AND)
                .save(saver, "silent_sun:phase2_win");

            Advancement.Builder.advancement()
                .parent(root)
                .display(
                    Items.COMPASS,
                    Component.translatable("advancement.silent_sun.teleport_expel.title"),
                    Component.translatable("advancement.silent_sun.teleport_expel.desc"),
                    null,
                    AdvancementType.TASK,
                    true, true, true)  // hidden
                .addCriterion("trigger", impossible())
                .requirements(AdvancementRequirements.Strategy.AND)
                .save(saver, "silent_sun:teleport_expel");
        }
    }
}
