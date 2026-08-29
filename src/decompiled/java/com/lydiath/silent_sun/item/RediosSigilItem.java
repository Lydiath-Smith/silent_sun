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
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel)) {
            return InteractionResultHolder.success(stack);
        }
        ServerLevel serverLevel = (ServerLevel)level;
        RediosCooldownData cooldown = RediosCooldownData.get(serverLevel);
        if (cooldown.isOnCooldown(serverLevel)) {
            int attempts = cooldown.recordSummonAttempt(serverLevel);
            double days = (double)cooldown.remainingTicks(serverLevel) / 24000.0;
            String s = String.format(Locale.ROOT, "%.1f", days);
            player.sendSystemMessage((Component)Component.translatable("message.silent_sun.redios_sigil.busy", (Object[])new Object[]{s}));
            if (attempts > 5) {
                MutableComponent msg = Component.translatable("message.silent_sun.redios_sigil.already_busy_extra").withStyle(ChatFormatting.GRAY);
                player.sendSystemMessage(msg);
            }
            return InteractionResultHolder.consume(stack);
        }
        // 已有 Boss 在场：传送玩家至 Boss 附近 + 20s 发光，不消耗（设计文档 L501-502 语义）。
        RediosEntity existing = findExistingRedios(serverLevel);
        if (existing != null) {
            teleportPlayerNearBoss(player, existing);
            existing.addEffect(new MobEffectInstance(MobEffects.GLOWING, 400, 0));
            return InteractionResultHolder.consume(stack);
        }
        // 无 Boss 才走纯残留兜底（此时本不该有残留），再正常生成。
        clearResidualRedios(serverLevel);
        BlockPos pos = player.blockPosition().above();
        Entity entity = ((EntityType)ModEntities.REDIOS.get()).spawn(serverLevel, stack, player, pos, MobSpawnType.SPAWN_EGG, true, false);
        // 兜底：finalizeSpawn 拒绝（极短竞态下第二个 Boss）时 entity.isRemoved()==true，不消耗不播音效
        if (entity != null && !entity.isRemoved()) {
            serverLevel.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 1.0f);
            // 2026-08-12 用户决策：召唤器永不消耗；召唤成功时全服广播（文案可配置，留空关闭）
            String broadcast = SilentSunConfig.SUMMON_BROADCAST_MESSAGE.get();
            if (broadcast != null && !broadcast.isEmpty()) {
                serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("<" + player.getScoreboardName() + "> " + broadcast), false);
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    /** 召唤前全维暴力清除残留莱德厄斯：静默剔除所有已加载残留实体 + 清空战斗账本。
     *  裁决「一律清除」：CD 已好时仍存在的 Boss 即残留（正常 Boss 应在 CD 内结算），
     *  不回收、不传送、不结算、不设 CD、不广播。 */
    private void clearResidualRedios(ServerLevel serverLevel) {
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return;
        }
        for (ServerLevel sl : server.getAllLevels()) {
            for (Entity e : sl.getEntities().getAll()) {
                if (e instanceof RediosEntity redios && !redios.isRemoved()) {
                    redios.forceDiscardSilently();
                }
            }
        }
        RediosBattleData.get(serverLevel).clearAllRecords();
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
     *  Boss 在空中/高处时玩家也传到同高度，不再回落地表）。 */
    private void teleportPlayerNearBoss(Player player, RediosEntity boss) {
        ServerLevel sl = (ServerLevel) boss.level();
        double angle = sl.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = 3.0 + sl.getRandom().nextDouble() * 2.0;
        double x = boss.getX() + Math.cos(angle) * dist;
        double z = boss.getZ() + Math.sin(angle) * dist;
        player.teleportTo(sl, x, boss.getY() + 0.5, z, Set.of(), player.getYRot(), player.getXRot());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.silent_sun.redios_sigil.tooltip.1").withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("item.silent_sun.redios_sigil.tooltip.2").withStyle(ChatFormatting.GRAY));
    }
}
