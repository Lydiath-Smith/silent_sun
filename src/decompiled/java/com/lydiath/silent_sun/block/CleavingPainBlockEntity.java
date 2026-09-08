package com.lydiath.silent_sun.block;

import com.lydiath.silent_sun.entity.RediosEntity;
import com.lydiath.silent_sun.registry.ModBlockEntities;
import com.lydiath.silent_sun.registry.ModEntities;
import com.lydiath.silent_sun.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * 裂解之痛（莱德厄斯召唤器基底）方块实体：
 * <ul>
 *   <li>{@code fluid}：0=无 / 1=岩浆 / 2=水（类似炼药锅的装液）；</li>
 *   <li>{@code hasDiamond}：是否已投入钻石。</li>
 * </ul>
 * 岩浆 + 钻石到位后自动引发雷击、清除并掉落「莱德厄斯召唤器」；倒水后用召唤器右键触发 Boss 召唤。
 */
public class CleavingPainBlockEntity extends BlockEntity {

    public static final int FLUID_NONE = 0;
    public static final int FLUID_LAVA = 1;
    public static final int FLUID_WATER = 2;

    public int fluid = FLUID_NONE;
    public boolean hasDiamond = false;

    public CleavingPainBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEAVING_PAIN.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("fluid", fluid);
        tag.putBoolean("hasDiamond", hasDiamond);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        fluid = tag.getInt("fluid");
        hasDiamond = tag.getBoolean("hasDiamond");
    }

    /** 岩浆状态时每 tick 检查是否有钻石物品落到本方块上：有则雷击 + 清除 + 掉落召唤器。 */
    public static void tick(Level level, BlockPos pos, BlockState state, CleavingPainBlockEntity be) {
        if (level.isClientSide() || be.fluid != FLUID_LAVA || be.hasDiamond) {
            return;
        }
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos))) {
            if (ie.getItem().is(Items.DIAMOND)) {
                ie.discard();
                be.fluid = FLUID_NONE;
                be.hasDiamond = false;
                be.setChanged();
                // 雷击（真实落雷）
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                }
                // 掉落召唤器
                ItemStack summoner = new ItemStack(ModItems.REDIOS_SUMMONER.get());
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, summoner));
                break;
            }
        }
    }

    /** 倒水后手持召唤器右键：清除水并召唤/追击 Boss。 */
    public ItemInteractionResult trySummon(Player player, Level level, BlockPos pos) {
        if (fluid != FLUID_WATER) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        fluid = FLUID_NONE;
        hasDiamond = false;
        setChanged();
        summonOrPursue(level, player, pos);
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * 召唤器职能：召唤媒介 + 追击传送。
     * <ul>
     *   <li>全维度查找已存在且存活的 RediosEntity：有则直接把玩家传送到其附近（追击）；</li>
     *   <li>没有则生成新 Boss（入场动画由 finalizeSpawn → startIntro 触发），再传送到其附近。</li>
     * </ul>
     */
    private static void summonOrPursue(Level level, Player player, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        RediosEntity boss = findExistingRedios(serverLevel);
        if (boss == null) {
            Entity spawned = ModEntities.REDIOS.get().spawn(serverLevel, player.getMainHandItem(), player, pos.above(), MobSpawnType.SPAWN_EGG, true, false);
            boss = spawned instanceof RediosEntity redios ? redios : null;
            if (boss != null) {
                // 新召唤 → 播放切阶段立方体动画 + 烟圈收缩帧散射繁星爆闪（2026-09-04）
                boss.beginSummonCinematic();
            }
        }
        if (boss != null) {
            teleportPlayerNearBoss(player, serverLevel, boss);
        }
    }

    /** 全维度查找存活且未移除的 RediosEntity。 */
    private static RediosEntity findExistingRedios(ServerLevel serverLevel) {
        MinecraftServer server = serverLevel.getServer();
        if (server == null) {
            return null;
        }
        for (ServerLevel lvl : server.getAllLevels()) {
            for (Entity e : lvl.getEntities().getAll()) {
                if (e instanceof RediosEntity redios && redios.isAlive() && !redios.isRemoved()) {
                    return redios;
                }
            }
        }
        return null;
    }

    /** 把玩家传送到 Boss 附近（距离 3~5 格的随机方位），用于追击。 */
    private static void teleportPlayerNearBoss(Player player, ServerLevel bossLevel, RediosEntity boss) {
        double angle = bossLevel.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = 3.0 + bossLevel.getRandom().nextDouble() * 2.0;
        double x = boss.getX() + Math.cos(angle) * dist;
        double z = boss.getZ() + Math.sin(angle) * dist;
        double y = boss.getY() + 0.5;
        player.teleportTo(bossLevel, x, y, z, Set.of(), player.getYRot(), player.getXRot());
    }
}
