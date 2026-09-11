package com.lydiath.silent_sun.block;

import com.lydiath.silent_sun.data.RediosBattleData;
import com.lydiath.silent_sun.data.RediosCooldownData;
import com.lydiath.silent_sun.entity.RediosEntity;
import com.lydiath.silent_sun.registry.ModBlockEntities;
import com.lydiath.silent_sun.registry.ModEntities;
import com.lydiath.silent_sun.registry.ModItems;
import com.lydiath.silent_sun.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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

    /** 钻石放置倒计时：>=0 表示已放置钻石、正在等待雷击（每 tick 递减）；<0 表示未放置。 */
    public int diamondCountdown = -1;
    /** 已放置钻石的漂浮实体 UUID（触发时移除；区块重载后可能失效，容忍 null）。 */
    public UUID diamondEntityUuid = null;
    /** 2026-09-10（用户裁决）：落雷后延迟掉落召唤器的 tick 数。
     *  取值依据：LightningBolt 寿命最坏 ≈ 38 tick（life=2、flashes≤3、每轮最多 11 tick、最多 4 轮），
     *  60 tick 覆盖最坏情况并留余量。详见 docs/实现计划-2026-09-10-落雷错开召唤器掉落.md。 */
    public static final int SUMMONER_DROP_DELAY_TICKS = 60;
    /** 召唤器掉落倒计时：>0 递减中，归零时掉落；-1 表示无待掉落。
     *  与落雷错开是为了避开雷击直伤（雷击 5.0 = 掉落实体满血 5，且 fireResistant 拦不住雷击伤害）。 */
    public int summonerDropCountdown = -1;
    /** 召唤序列计数：-1=未进行；>=0 进行中（敲钟三下 → 末地传送门音效 → 开场动画）。 */
    public int summonTicks = -1;
    /** 触发召唤的玩家 UUID（序列结束后传送到 Boss 附近）。 */
    public UUID summoningPlayerUuid = null;

    public CleavingPainBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEAVING_PAIN.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("fluid", fluid);
        tag.putBoolean("hasDiamond", hasDiamond);
        tag.putInt("diamondCountdown", diamondCountdown);
        tag.putInt("summonerDropCountdown", summonerDropCountdown);
        if (diamondEntityUuid != null) {
            tag.putUUID("diamondEntityUuid", diamondEntityUuid);
        }
        tag.putInt("summonTicks", summonTicks);
        if (summoningPlayerUuid != null) {
            tag.putUUID("summoningPlayerUuid", summoningPlayerUuid);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        fluid = tag.getInt("fluid");
        hasDiamond = tag.getBoolean("hasDiamond");
        diamondCountdown = tag.contains("diamondCountdown") ? tag.getInt("diamondCountdown") : -1;
        summonerDropCountdown = tag.contains("summonerDropCountdown") ? tag.getInt("summonerDropCountdown") : -1;
        diamondEntityUuid = tag.contains("diamondEntityUuid") ? tag.getUUID("diamondEntityUuid") : null;
        summonTicks = tag.contains("summonTicks") ? tag.getInt("summonTicks") : -1;
        summoningPlayerUuid = tag.contains("summoningPlayerUuid") ? tag.getUUID("summoningPlayerUuid") : null;
    }

    /** 客户端同步（修复客户端恒 FLUID_NONE）：携带流体/钻石状态与倒计时。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("fluid", fluid);
        tag.putBoolean("hasDiamond", hasDiamond);
        tag.putInt("diamondCountdown", diamondCountdown);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        fluid = tag.getInt("fluid");
        hasDiamond = tag.getBoolean("hasDiamond");
        diamondCountdown = tag.getInt("diamondCountdown");
    }

    /** 每 tick：处理钻石放置倒计时 → 触发雷击 → （3 秒后）掉落召唤器（不再做 AABB 投掷检测，钻石改为右键放置）。 */
    public static void tick(Level level, BlockPos pos, BlockState state, CleavingPainBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        // 召唤序列（2026-09-08 用户裁决）：敲钟三下（间隔 20 tick）→ 末地传送门生成音效 → 开场动画
        if (be.summonTicks >= 0) {
            be.summonTicks++;
            // 召唤期间祭坛周围升黑雾粒子（2026-09-08 用户要求：真伤光环同款黑雾，纯视觉、无伤害）
            if (be.summonTicks % 5 == 0 && level instanceof ServerLevel serverLevel) {
                spawnSummonBlackSmoke(serverLevel, pos, be.summonTicks);
            }
            if (be.summonTicks == 1 || be.summonTicks == 21 || be.summonTicks == 41) {
                level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 1.0f);
            } else if (be.summonTicks >= 60) {
                be.summonTicks = -1;
                level.playSound(null, pos, ModSounds.REDIOS_SUMMON.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                be.summonBossAfterSequence(level, pos);
            }
        }
        // 2026-09-11（代码审计 G10 #9 修复）：原 diamondCountdown > 0 分支末尾直接 `return`，
        // 导致「上一次落雷已排定的召唤器掉落倒计时」在玩家又放一颗钻石期间被**整体冻结** ——
        // 召唤器迟迟不掉（要等新一轮钻石倒计时走完才恢复推进）。两个倒计时彼此独立，
        // 改为 else-if 串联，让掉落倒计时照常推进。
        if (be.diamondCountdown > 0) {
            be.diamondCountdown--;
        } else if (be.diamondCountdown == 0) {
            be.diamondCountdown = -1;
            be.removePlacedDiamond(level);
            be.fluid = FLUID_NONE;
            be.hasDiamond = false;
            be.setChanged();
            be.syncFluidState(level, pos);
            // 雷击（真实落雷）
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                bolt.setVisualOnly(false);
                level.addFreshEntity(bolt);
            }
            // 掉落莱德厄斯召唤器 —— 2026-09-10（用户裁决）：与落雷错开时间，不原地掉落。
            // 落雷在 life>=0 的每 tick 对 3 格 AABB 内所有存活实体调 thunderHit（无 ItemEntity 例外），
            // 雷击直伤 5.0 = 掉落实体满血 5，且 lightning_bolt 不在 #minecraft:is_fire 内
            // → .fireResistant() 拦不住 → 原地掉落会被自家落雷当场销毁。
            be.summonerDropCountdown = SUMMONER_DROP_DELAY_TICKS;
            be.setChanged();
        }
        // 召唤器掉落倒计时（等落雷实体消亡后再掉）
        if (be.summonerDropCountdown > 0) {
            be.summonerDropCountdown--;
            if (be.summonerDropCountdown == 0) {
                be.summonerDropCountdown = -1;
                be.setChanged();
                spawnSummonerItem(level, pos);
            }
        }
    }

    /** 在祭坛上方生成「莱德厄斯召唤器」掉落物（火/岩浆免疫由物品的 FIRE_RESISTANT 组件保证）。 */
    private static void spawnSummonerItem(Level level, BlockPos pos) {
        ItemStack summoner = new ItemStack(ModItems.REDIOS_SIGIL.get());
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, summoner));
    }

    /** 召唤期间绕祭坛升起黑雾粒子（LARGE_SMOKE，同 Boss 真伤光环黑烟，纯视觉无伤害）。 */
    private static void spawnSummonBlackSmoke(ServerLevel serverLevel, BlockPos pos, int tick) {
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        // 外围黑烟环（半径 2.5，随召唤 tick 自旋上浮）
        int ring = 10;
        double spin = (double) tick * 0.08;
        for (int i = 0; i < ring; ++i) {
            double angle = Math.PI * 2.0 * (double) i / (double) ring + spin;
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                cx + Math.cos(angle) * 2.5,
                pos.getY() + 0.4 + serverLevel.random.nextDouble() * 1.8,
                cz + Math.sin(angle) * 2.5,
                1, 0.0, 0.08, 0.0, 0.03);
        }
        // 中心一束上浮黑烟（烟柱）
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
            cx, pos.getY() + 0.3, cz, 2, 0.3, 0.4, 0.3, 0.03);
    }

    /** 移除已放置的漂浮钻石实体（按 UUID，容忍区块重载后实体丢失）。 */
    private void removePlacedDiamond(Level level) {
        if (diamondEntityUuid != null && level instanceof ServerLevel sl) {
            Entity e = sl.getEntity(diamondEntityUuid);
            if (e != null) {
                e.discard();
            }
        }
        diamondEntityUuid = null;
    }

    /** 岩浆状态右键钻石：放置漂浮旋转钻石（似掉落物、不可拾取），1 秒后雷击，再 3 秒后掉落召唤器。 */
    public ItemInteractionResult placeDiamond(Player player, InteractionHand hand, Level level, BlockPos pos) {
        if (fluid != FLUID_LAVA || diamondCountdown >= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ItemEntity diamond = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
            new ItemStack(Items.DIAMOND));
        diamond.setPickUpDelay(32767);
        diamond.setDeltaMovement(0.0, 0.0, 0.0);
        level.addFreshEntity(diamond);
        diamondEntityUuid = diamond.getUUID();
        diamondCountdown = 20; // 1 秒（20 tick）
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    /** 空桶右键：取走已有液体（岩浆→岩浆桶，水→水桶），清空后可换装。 */
    public ItemInteractionResult tryTakeLiquid(Player player, InteractionHand hand, Level level, BlockPos pos) {
        if (fluid == FLUID_NONE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // 2026-09-10 实测修复（W4 回归）：钻石放置倒计时进行中不许取回液体——原实现丢掉 HEAD 的
        // 岩浆守卫后，玩家可"右键钻石 → 空桶收回岩浆"，1 秒后照样落雷并掉召唤器（白得召唤器 +
        // 拿回岩浆，且祭坛显示空液体却在落雷）。
        if (diamondCountdown >= 0) {
            player.displayClientMessage(Component.translatable("message.silent_sun.cleaving_pain.fluid_blocked"), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        ItemStack result = new ItemStack(fluid == FLUID_LAVA ? Items.LAVA_BUCKET : Items.WATER_BUCKET);
        fluid = FLUID_NONE;
        setChanged();
        syncFluidState(level, pos);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, result);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 岩浆桶/水桶右键：空则装液；已有液体不许替换，动作栏提示"好像被空气墙拦住了。" */
    public ItemInteractionResult tryFill(Player player, InteractionHand hand, Level level, BlockPos pos, int targetFluid) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (fluid != FLUID_NONE) {
            player.displayClientMessage(Component.translatable("message.silent_sun.cleaving_pain.fluid_blocked"), true);
            return ItemInteractionResult.SUCCESS;
        }
        fluid = targetFluid;
        hasDiamond = false;
        setChanged();
        syncFluidState(level, pos);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 倒水后手持莱德厄斯召唤器右键：冷却检查通过后启动召唤序列（敲钟→末地传送门音效→开场动画）。 */
    public ItemInteractionResult trySummon(Player player, Level level, BlockPos pos) {
        // 2026-09-11（代码审计 G10 #7 修复）：召唤序列进行中时拒绝再次启动。
        // 原实现直接执行 `summonTicks = 0` → 右击可让序列反复重跑（敲钟 / 末地传送门音效 /
        // 开场动画从头来一遍），且 summoningPlayerUuid 会被后来者覆盖。
        if (summonTicks >= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (fluid != FLUID_WATER) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        // 2026-09-10 用户裁决（选项 A）：可召唤性预检**提到消耗材料之前**。
        // 原实现把"账本有记录 → 阻止重复召唤"放在开场动画结束后的 summonBossAfterSequence 里，
        // 而材料（水 + 钻石）在序列启动时就已经消耗 → 重启后 10 分钟内（记录最长留 12000 tick）
        // 玩家敲完钟、看完动画，得到的是"材料没了、Boss 没来、也没有任何提示"的静默吞掉。
        // 现在：账本有记录且场上无 Boss（= 远处还有一场没打完）→ 明确提示 + 不消耗材料 + 不启动序列。
        if (findExistingRedios(serverLevel) == null && RediosBattleData.get(serverLevel).hasAnyRecord()) {
            player.sendSystemMessage(Component.translatable("message.silent_sun.redios_sigil.battle_pending")
                .withStyle(ChatFormatting.GOLD));
            return ItemInteractionResult.SUCCESS;
        }
        RediosCooldownData cooldown = RediosCooldownData.get(serverLevel);
        if (cooldown.isOnCooldown(serverLevel)) {
            int attempts = cooldown.recordSummonAttempt(serverLevel);
            double days = (double) cooldown.remainingTicks(serverLevel) / 24000.0;
            player.sendSystemMessage(Component.translatable("message.silent_sun.redios_sigil.busy",
                String.format(Locale.ROOT, "%.1f", days)));
            if (attempts > 5) {
                player.sendSystemMessage(Component.translatable("message.silent_sun.redios_sigil.already_busy_extra")
                    .withStyle(ChatFormatting.GRAY));
            }
            return ItemInteractionResult.SUCCESS;
        }
        cooldown.resetSummonAttempt();
        fluid = FLUID_NONE;
        hasDiamond = false;
        summonTicks = 0;
        summoningPlayerUuid = player.getUUID();
        setChanged();
        syncFluidState(level, pos);
        return ItemInteractionResult.SUCCESS;
    }

    /** 召唤序列结束后：生成 Boss + 开场动画；仅「追击」（Boss 已存在）时才传送触发玩家。 */
    private void summonBossAfterSequence(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Player player = summoningPlayerUuid != null ? serverLevel.getPlayerByUUID(summoningPlayerUuid) : null;
        RediosEntity boss = findExistingRedios(serverLevel);
        boolean newlySummoned = false;
        if (boss == null) {
            // M21：账本有记录 → Boss 存在但区块未加载，阻止重复召唤
            if (!RediosBattleData.get(serverLevel).hasAnyRecord()) {
                Entity spawned = ModEntities.REDIOS.get().spawn(serverLevel, ItemStack.EMPTY, player, pos.above(), MobSpawnType.SPAWN_EGG, true, false);
                boss = spawned instanceof RediosEntity redios ? redios : null;
                if (boss != null) {
                    // 2026-09-11（A-3 依设计 §2.1 L111 补齐）：
                    // ① 播放 WITHER_SPAWN；② 全服广播「<召唤者名> 文案」（文案取配置，留空关闭）。
                    serverLevel.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 1.0f);
                    String broadcast = com.lydiath.silent_sun.config.SilentSunConfig.SUMMON_BROADCAST_MESSAGE.get();
                    if (broadcast != null && !broadcast.isBlank()) {
                        String summonerName = player == null ? "" : player.getName().getString();
                        Component broadcastMsg = Component.literal("<" + summonerName + "> " + broadcast);
                        for (var online : serverLevel.getServer().getPlayerList().getPlayers()) {
                            online.sendSystemMessage(broadcastMsg);
                        }
                    }
                    // 新召唤 → 播放切阶段立方体动画 + 烟圈收缩帧散射繁星爆闪（2026-09-04）
                    boss.beginSummonCinematic();
                    newlySummoned = true;
                }
            }
        }
        // 2026-09-08 用户裁决：召唤完 Boss 不传送玩家；仅「追击」（Boss 已存在）时传送
        if (boss != null && player != null && !newlySummoned) {
            teleportPlayerNearBoss(player, serverLevel, boss);
        }
        summoningPlayerUuid = null;
    }

    /** 把 BlockEntity 的 fluid 同步到方块状态（供模型变体显示液体层）。 */
    private void syncFluidState(Level level, BlockPos pos) {
        if (level instanceof ServerLevel) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(CleavingPainBlock.FLUID)) {
                level.setBlock(pos, state.setValue(CleavingPainBlock.FLUID, fluid), 3);
            }
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
        // M22：随机尝试多个方位找安全落点（脚下有方块、身位是空气），找不到才退回 Boss 高度
        for (int i = 0; i < 12; i++) {
            double angle = bossLevel.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = 3.0 + bossLevel.getRandom().nextDouble() * 2.0;
            double x = boss.getX() + Math.cos(angle) * dist;
            double z = boss.getZ() + Math.sin(angle) * dist;
            double y = boss.getY() + 0.5;
            BlockPos feet = BlockPos.containing(x, y, z);
            if (bossLevel.getBlockState(feet).isAir()
                && bossLevel.getBlockState(feet.above()).isAir()
                && !bossLevel.getBlockState(feet.below()).isAir()) {
                player.teleportTo(bossLevel, x, y, z, Set.of(), player.getYRot(), player.getXRot());
                return;
            }
        }
        double angle = bossLevel.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = 3.0 + bossLevel.getRandom().nextDouble() * 2.0;
        player.teleportTo(bossLevel, boss.getX() + Math.cos(angle) * dist, boss.getY() + 0.5, boss.getZ() + Math.sin(angle) * dist, Set.of(), player.getYRot(), player.getXRot());
    }
}
