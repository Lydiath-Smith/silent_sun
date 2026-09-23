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
 * 4 个 advancement 全部使用 {@code minecraft:impossible} 触发——由
 * {@code RediosEntity.grantAdvancement} / {@code grantAdvancementToParticipants}
 * 在战斗流程中运行时授予（phase1_clear / phase2_countdown / phase2_win / teleport_expel）。
 * <p>
 * 2026-09-12（用户裁决）：原 {@code silent_sun:root} 已**删除** —— 它是 {@code impossible} 触发却
 * 无任何运行时授予点 = 永远拿不到的根进度（既有审计项 G11 #7）。4 个成就的父节点改接到灭却之日的
 * {@code extinction_day_mod_1784441698:miedao_duan}（「获得灭刀·断」）⇒ 本模组成就并入其进度树。
 * <p>
 * 副作用（知情）：silent_sun 不再有自己的根节点，进度界面里**不再单独成页**，4 个成就显示在
 * 灭却之日的进度页内、挂在「我见这不幸，蚀透世界」之下。跨模组父节点是安全的：
 * {@code extinction_day_mod_1784441698} 在 {@code neoforge.mods.toml} 里声明为 {@code type="required"}。
 */
public final class ModAdvancementProvider extends AdvancementProvider {

    public ModAdvancementProvider(PackOutput output,
                                  CompletableFuture<HolderLookup.Provider> registries,
                                  List<AdvancementSubProvider> subProviders) {
        super(output, registries, subProviders);
    }

    /** 子 provider：实际定义 4 个 advancement 条目（根节点已改接灭却之日的「获得灭刀·断」）。 */
    public static final class ModAdvancementSubProvider implements AdvancementSubProvider {

        private static Criterion<?> impossible() {
            return new Criterion<>(CriteriaTriggers.IMPOSSIBLE, new ImpossibleTrigger.TriggerInstance());
        }

        @Override
        public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver) {
            // 2026-09-12（用户裁决）：原 `silent_sun:root` 已删除（impossible 触发却无授予点 = 永远拿不到，
            // 审计项 G11 #7）。4 个成就的父节点改接到灭却之日的「获得灭刀·断」成就
            // （`extinction_day_mod_1784441698:miedao_duan`，其 criterion 名为 obtain_miedao_duan）。
            // 语义：先拿到灭刀·断（灭却之日那把刀）才谈得上打这个 Boss；本模组成就因此并入其进度树。
            // 跨模组父节点安全：extinction_day_mod_1784441698 是 mods.toml 里的 type="required"。
            ResourceLocation parentId =
                ResourceLocation.fromNamespaceAndPath("extinction_day_mod_1784441698", "miedao_duan");

            Advancement.Builder.advancement()
                .parent(parentId)
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
                .parent(parentId)
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
                .parent(parentId)
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
                .parent(parentId)
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
