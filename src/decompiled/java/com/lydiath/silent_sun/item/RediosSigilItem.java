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
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
        RediosEntity boss = findExistingRedios(serverLevel);
        if (boss != null) {
            teleportPlayerNearBoss(player, boss);
            serverLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** 查找当前任意维度里存活且未移除的莱德厄斯；找不到返回 null。 */
    private RediosEntity findExistingRedios(ServerLevel serverLevel) {
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel sl : server.getAllLevels()) {
            for (Entity e : sl.getEntities().getAll()) {
                if (e instanceof RediosEntity redios && redios.isAlive() && !redios.isRemoved()) {
                    return redios;
                }
            }
        }
        return null;
    }

    /** 把玩家传送到 Boss 附近水平 3~5 格随机落点（2026-08-30：落点高度 = Boss 所在高度，
     *  Boss 在空中/高处时玩家也传到同高度，不再回落地表）。M22：优先找安全落点。 */
    private void teleportPlayerNearBoss(Player player, RediosEntity boss) {
        ServerLevel sl = (ServerLevel) boss.level();
        for (int i = 0; i < 12; i++) {
            double angle = sl.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = 3.0 + sl.getRandom().nextDouble() * 2.0;
            double x = boss.getX() + Math.cos(angle) * dist;
            double z = boss.getZ() + Math.sin(angle) * dist;
            double y = boss.getY() + 0.5;
            BlockPos feet = BlockPos.containing(x, y, z);
            if (sl.getBlockState(feet).isAir()
                && sl.getBlockState(feet.above()).isAir()
                && !sl.getBlockState(feet.below()).isAir()) {
                player.teleportTo(sl, x, y, z, Set.of(), player.getYRot(), player.getXRot());
                return;
            }
        }
        double angle = sl.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = 3.0 + sl.getRandom().nextDouble() * 2.0;
        player.teleportTo(sl, boss.getX() + Math.cos(angle) * dist, boss.getY() + 0.5, boss.getZ() + Math.sin(angle) * dist, Set.of(), player.getYRot(), player.getXRot());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.silent_sun.redios_sigil.tooltip.1").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("item.silent_sun.redios_sigil.tooltip.2").withStyle(ChatFormatting.GRAY));
    }
}
