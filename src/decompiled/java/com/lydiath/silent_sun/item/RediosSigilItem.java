/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.item;

import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.data.RediosBattleData;
import com.lydiath.silent_sun.data.RediosCooldownData;
import com.lydiath.silent_sun.entity.RediosEntity;
import com.lydiath.silent_sun.registry.ModEntities;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class RediosSigilItem
extends Item {
    public RediosSigilItem(Item.Properties properties) {
        super(properties);
    }

    // TODO(审计清理 G10 #6)：getUseAnimation() 零调用（返回常量 UseAnim.NONE，与不覆写等价）；同族零调用方法见 entity/StarfallSalvoEntity.java 的 isFalling() / getOwnerUuid()。注：StarfallCurtainEntity.getLifetimeTicks() 已于 D-5 裁定后「由死变活」，不要删 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 右键不再直接召唤 Boss（2026-09-08 用户裁决）：召唤走注水祭坛
        // （CleavingPainBlock.useItemOn → CleavingPainBlockEntity.trySummon 负责冷却检查与召唤序列）。
        // 此处右键改为「追击回调」：莱德厄斯已存在时，把玩家传送到其附近追击。
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        ServerLevel serverLevel = (ServerLevel) level;
        RediosEntity boss = RediosEntity.findExisting(serverLevel, null);
        if (boss != null) {
            // 2026-09-14（体检 P1-1 同源化）：查找与传送均改调 RediosEntity 的**唯一来源**
            //（本类原有的 findExistingRedios / teleportPlayerNearBoss 两份重复实现已删除）。
            RediosEntity.teleportPlayerNearBoss(player, boss);
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.silent_sun.redios_sigil.tooltip.1").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("item.silent_sun.redios_sigil.tooltip.2").withStyle(ChatFormatting.GRAY));
    }
}
