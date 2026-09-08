package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.registry.ModItems;
import com.lydiath.silent_sun.rules.RediosRules;
import com.lydiath.silent_sun.util.AbsoluteDamageUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Centralized weapon and combat tool manager for RediosEntity.
 * <p>
 * Manages three subsystems:
 * <ol>
 *   <li><b>Equipment</b> — weapon inventory (trident / blade switching)</li>
 *   <li><b>Guard</b> — adaptive block probability (1.9 有所不为)</li>
 *   <li><b>BlockBomb</b> — mining obstructing blocks + throwing as bombs</li>
 * </ol>
 * Also tracks uncontrolled sprint extra hit cooldown.
 */
final class WeaponManager {

    private final RediosEntity boss;

    // ── Equipment ──

    /** 拔刀剑攻击窗口剩余 tick（>0 表示 Boss 正处于"用刀"阶段）；窗口结束自动切回三叉戟。 */
    int bladeModeTicks = 0;
    /** 拔刀剑窗口结束后的冷却剩余 tick。 */
    int bladeModeCooldownTicks = 0;
    /** 是否已完成「开战热身」：首次进入战斗先垫一段冷却，避免开战瞬间即切刀。 */
    private boolean bladeModeWarmupDone = false;

    // ── Guard ──

    int guardCooldownTicks = 0;
    int guardActiveTicks = 0;
    final Map<UUID, Integer> guardLastHitTick = new HashMap<>();
    final Map<UUID, Double> guardAvgInterval = new HashMap<>();

    // ── BlockBomb ──

    int stageDigCooldownTicks = 0;
    int stageBlockBombCooldownTicks = 0;
    final ArrayDeque<BlockState> stageMinedBlocks = new ArrayDeque<>();

    // ── Uncontrolled Sprint extra hit ──

    private int uncontrolledSprintExtraCooldownTicks = 0;

    // ── Constants ──

    private static final int STAGE_DIG_RAY_STEPS = 6;
    private static final int STAGE_DIG_INTERVAL_TICKS = 2;
    private static final int STAGE_BLOCK_BOMB_COOLDOWN_TICKS = 35;
    private static final float STAGE_BLOCK_BOMB_EXPLOSION_POWER = 2.5f;

    // ── 拔刀剑窗口参数 ──
    /** 刀窗口最短 5 秒（100 tick） */
    private static final int BLADE_MODE_MIN_TICKS = 100;
    /** 刀窗口 5~10 秒（随机追加 0~100 tick） */
    private static final int BLADE_MODE_EXTRA_TICKS = 100;
    /** 窗口冷却最短 8 秒（160 tick） */
    private static final int BLADE_MODE_COOLDOWN_MIN_TICKS = 160;
    /** 窗口冷却 8~15 秒（随机追加 0~140 tick） */
    private static final int BLADE_MODE_COOLDOWN_EXTRA_TICKS = 140;
    /** 冷却归零且无目标时垫 2 秒（40 tick）再重试 */
    private static final int BLADE_MODE_RETRY_TICKS = 40;
    /** 开战热身冷却：进入战斗后前 3 秒（60 tick）不切刀，先走空手近战/移动节奏。 */
    private static final int BLADE_MODE_WARMUP_TICKS = 60;
    /** 冷却归零且有目标时进入刀窗口的概率 */
    private static final double BLADE_MODE_ENTER_CHANCE = 0.35;

    WeaponManager(RediosEntity boss) {
        this.boss = boss;
    }

    // ── Tick ──

    void tick() {
        tickBladeMode();
        tickEquipment();
        tickGuard();
        tickUncontrolledSprintCooldown();
    }

    void tickStageAbilities(ServerLevel serverLevel) {
        if (this.stageDigCooldownTicks > 0) {
            --this.stageDigCooldownTicks;
        }
        if (this.stageBlockBombCooldownTicks > 0) {
            --this.stageBlockBombCooldownTicks;
        }
        if (boss.bossState.isPhase1()) {
            if (boss.bossState.isPhase1Combat() && boss.getTarget() != null) {
                boss.setSprinting(true);
            }
            tryDigBlockingBlocks(serverLevel);
        }
        if (boss.bossState.isPhase1() || boss.bossState.isPhase2()) {
            tryThrowBlockBomb(serverLevel);
        }
    }

    // ── Equipment ──

    /** 拔刀剑攻击窗口状态机：战斗阶段内概率进入刀窗口，窗口结束进冷却。 */
    private void tickBladeMode() {
        if (!IntegrationContract.isSlashBladeIntegrationAvailable()) return;
        // 脱离战斗阶段（过渡/投票等）时强制收回刀，并重置热身标记。
        // 2026-09-04：pending（濒死锁血）不在此列——pending 期间 Boss 照常战斗（拔刀剑攻击
        // 不中断），仅锁 1 血防击杀误判，刀窗口维持。
        if (!boss.bossState.isCombat()
            && boss.bossState != BossState.PHASE1_PENDING
            && boss.bossState != BossState.PHASE2_PENDING) {
            this.bladeModeTicks = 0;
            this.bladeModeCooldownTicks = 0;
            this.bladeModeWarmupDone = false;
            return;
        }
        // 开战热身：每次（重新）进入战斗先垫一段冷却，避免首个 tick 即概率切刀
        if (!this.bladeModeWarmupDone) {
            this.bladeModeWarmupDone = true;
            this.bladeModeCooldownTicks = BLADE_MODE_WARMUP_TICKS;
            return;
        }
        if (this.bladeModeTicks > 0) {
            if (--this.bladeModeTicks <= 0) {
                this.bladeModeCooldownTicks = BLADE_MODE_COOLDOWN_MIN_TICKS
                    + boss.getRandom().nextInt(BLADE_MODE_COOLDOWN_EXTRA_TICKS);
            }
            return;
        }
        if (this.bladeModeCooldownTicks > 0) {
            this.bladeModeCooldownTicks--;
            return;
        }
        // 冷却归零且有目标 → 概率进入刀窗口；未进入则垫一小段冷却再重试
        LivingEntity target = boss.getTarget();
        if (target != null && target.isAlive()
            && boss.getRandom().nextDouble() < BLADE_MODE_ENTER_CHANCE) {
            this.bladeModeTicks = BLADE_MODE_MIN_TICKS
                + boss.getRandom().nextInt(BLADE_MODE_EXTRA_TICKS);
        } else {
            this.bladeModeCooldownTicks = BLADE_MODE_RETRY_TICKS;
        }
    }

    /** 拔刀剑攻击窗口是否开启（BladeAttackGoal 据此决定是否放 SA/连击）。 */
    boolean isBladeModeActive() {
        return this.bladeModeTicks > 0;
    }

    private void tickEquipment() {
        if (boss.tickCount % 2 != 1) {
            return;
        }
        if (IntegrationContract.isSlashBladeIntegrationAvailable()) {
            ItemStack main = boss.getMainHandItem();
            if (this.bladeModeTicks > 0) {
                // 刀窗口：确保主手是拔刀剑（命名刀，带全套 NBT/模型）
                if (main.isEmpty() || !IntegrationContract.isSlashBladeItem(main.getItem())) {
                    ItemStack blade = IntegrationContract.tryEquipBlade(boss);
                    if (!blade.isEmpty()) {
                        boss.setItemSlot(EquipmentSlot.MAINHAND, blade);
                    }
                }
            } else if (main.isEmpty() || !main.is(ModItems.REDIOS_TRIDENT.get())) {
                // 窗口关闭：切回三叉戟型武器（redios_trident，模型即刀）
                boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REDIOS_TRIDENT.get()));
            }
            return;
        }
        ItemStack main = boss.getMainHandItem();
        if (main.isEmpty() || !main.is(ModItems.REDIOS_TRIDENT.get())) {
            boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REDIOS_TRIDENT.get()));
        }
    }

    // ── Guard ──

    private void tickGuard() {
        if (this.guardCooldownTicks > 0) {
            --this.guardCooldownTicks;
        }
        if (this.guardActiveTicks > 0) {
            --this.guardActiveTicks;
        }
    }

    boolean tryGuardBlock(DamageSource source) {
        if (boss.bossState.isVoteOrTransition()) return false;
        if (this.guardCooldownTicks > 0) return false;
        if (source == null) return false;
        if (boss.isVoidDamage(source) || boss.isTrueDamage(source)) return false;

        LivingEntity attacker = boss.tryResolveDamageAttacker(source);
        if (attacker == null) return false;
        if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return false;

        UUID id = attacker.getUUID();
        Integer last = this.guardLastHitTick.get(id);
        int nowTick = boss.tickCount;
        if (last != null) {
            int diff = Math.max(1, nowTick - last);
            double current = this.guardAvgInterval.getOrDefault(id, Double.valueOf(diff));
            double next = current * 0.7 + (double) diff * 0.3;
            this.guardAvgInterval.put(id, next);
        }
        this.guardLastHitTick.put(id, nowTick);

        double avg = this.guardAvgInterval.getOrDefault(id, 20.0);
        double triggerThreshold = 20.0 / Math.max(1, RediosRules.adaptiveBlockTriggerHitsPerSecond());
        double chance = avg <= triggerThreshold ? 0.85
            : (avg <= triggerThreshold * 2.5 ? 0.7 : 0.55);

        if (boss.getRandom().nextDouble() >= chance) return false;

        this.guardCooldownTicks = Math.max(0, RediosRules.adaptiveBlockCooldownTicks());
        this.guardActiveTicks = Math.max(0, RediosRules.adaptiveBlockDurationTicks());
        Level level = boss.level();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, boss.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        return true;
    }

    /** 移除某参战者的自适应格挡统计（玩家被逐出/超距移除时调用，避免 Map 随参战者轮换无限增长）。 */
    void removeGuardStats(UUID id) {
        this.guardLastHitTick.remove(id);
        this.guardAvgInterval.remove(id);
    }

    // ── BlockBomb ──

    private void tryDigBlockingBlocks(ServerLevel serverLevel) {
        if (this.stageDigCooldownTicks > 0) return;

        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive() || target.level() != boss.level()) return;

        Vec3 from = boss.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-6) return;
        dir = dir.normalize();

        for (int i = 1; i <= STAGE_DIG_RAY_STEPS; i++) {
            Vec3 point = from.add(dir.scale(i));
            BlockPos pos = BlockPos.containing(point);
            BlockState state = serverLevel.getBlockState(pos);
            if (!state.isAir()) {
                if (state.getDestroySpeed(serverLevel, pos) < 0.0f) break;
                recordMinedBlock(state);
                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 35);
                serverLevel.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
                this.stageDigCooldownTicks = STAGE_DIG_INTERVAL_TICKS;
                break;
            }
        }
    }

    void recordMinedBlock(BlockState state) {
        int max = Math.max(0, SilentSunConfig.STAGE_MINED_BLOCK_QUEUE_MAX.get());
        if (max <= 0) return;
        while (this.stageMinedBlocks.size() >= max) {
            this.stageMinedBlocks.removeFirst();
        }
        this.stageMinedBlocks.addLast(state);
    }

    private void tryThrowBlockBomb(ServerLevel serverLevel) {
        if (this.stageBlockBombCooldownTicks > 0) return;
        if (this.stageMinedBlocks.isEmpty()) return;

        double reach = boss.getCurrentAttackReach();
        double sightRange = reach * 4.0;
        double sightSqr = sightRange * sightRange;
        double reachSqr = reach * reach;

        // 选择投掷目标：优先主目标；否则从视距内有效敌方中取最近者
        LivingEntity target = boss.getTarget();
        if (target == null || !BossTargeting.isValidAttackTarget(boss, target)
            || target.level() != boss.level() || boss.distanceToSqr(target) > sightSqr) {
            target = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class,
                boss.getBoundingBox().inflate(sightRange), e -> e.isAlive())) {
                if (!BossTargeting.isValidAttackTarget(boss, e) || e.level() != boss.level()) continue;
                double d = boss.distanceToSqr(e);
                if (d <= sightSqr && d < bestDist) {
                    bestDist = d;
                    target = e;
                }
            }
        }
        if (target == null) return;

        double distSqr = boss.distanceToSqr(target);
        boolean los = boss.getSensing().hasLineOfSight(target);
        // 攻击范围内且有视线 → 交给近战，不投掷
        if (distSqr <= reachSqr && los) return;
        // 攻击范围内隔墙且薄墙 → 交给穿墙近战，不投掷；厚墙 → 投掷突破
        if (distSqr <= reachSqr && !los
            && boss.countWallThickness(target) < RediosEntity.WALL_THICKNESS_BREAK_THRESHOLD) return;
        // 其余（攻击范围外视距内远程狙击 / 攻击范围内厚墙突破）→ 投掷

        BlockState state = this.stageMinedBlocks.removeFirst();
        Vec3 from = boss.getEyePosition();
        Vec3 to = target.getEyePosition();
        BlockHitResult hit = serverLevel.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, boss));
        Vec3 hitPos = hit.getLocation();

        if (RediosRules.wallAttackTraceParticles()) {
            sendWallAttackTraceParticles(serverLevel, from, hitPos);
        }
        if (target instanceof ServerPlayer player) {
            boss.notifyWallAttack(player);
        }
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
            hitPos.x, hitPos.y, hitPos.z, 18, 0.25, 0.25, 0.25, 0.02);
        serverLevel.explode(boss, hitPos.x, hitPos.y, hitPos.z,
            STAGE_BLOCK_BOMB_EXPLOSION_POWER, Level.ExplosionInteraction.MOB);
        // D-断魂：爆炸为 AoE 伤害途径，海天解锁时对爆炸波及的存活实体统一补挂断魂
        //（爆炸伤害本身走 hurt 会触发全局断魂追伤，但不会给目标挂断魂，此处补齐）。
        double blastRadius = STAGE_BLOCK_BOMB_EXPLOSION_POWER * 2.0;
        List<LivingEntity> blastVictims = serverLevel.getEntitiesOfClass(LivingEntity.class,
            new net.minecraft.world.phys.AABB(hitPos.x - blastRadius, hitPos.y - blastRadius, hitPos.z - blastRadius,
                hitPos.x + blastRadius, hitPos.y + blastRadius, hitPos.z + blastRadius),
            e -> e.isAlive() && e != boss && e.distanceToSqr(hitPos) <= blastRadius * blastRadius);
        for (LivingEntity victim : blastVictims) {
            boss.markSoulSeverIfUnlocked(victim);
        }
        this.stageBlockBombCooldownTicks = STAGE_BLOCK_BOMB_COOLDOWN_TICKS;
    }

    private void sendWallAttackTraceParticles(ServerLevel serverLevel, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0E-6) return;
        int steps = 12;
        Vec3 step = dir.scale(1.0 / steps);
        Vec3 p = from;
        for (int i = 0; i <= steps; i++) {
            serverLevel.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            p = p.add(step);
        }
    }

    // ── Uncontrolled Sprint extra hit ──

    private void tickUncontrolledSprintCooldown() {
        if (this.uncontrolledSprintExtraCooldownTicks > 0) {
            --this.uncontrolledSprintExtraCooldownTicks;
        }
    }

    void tryApplyUncontrolledSprintExtraHits(LivingEntity target) {
        if (!boss.isUncontrolledSprintActive()) return;
        if (this.uncontrolledSprintExtraCooldownTicks > 0) return;

        int hits = RediosRules.uncontrolledSprintExtraHits();
        if (hits <= 0) return;
        double ratio = RediosRules.uncontrolledSprintExtraDamageRatio();
        if (!Double.isFinite(ratio) || ratio <= 0.0) return;

        float baseDamage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = baseDamage * (float) ratio;
        if (damage <= 0.0f) return;

        this.uncontrolledSprintExtraCooldownTicks = RediosRules.uncontrolledSprintExtraCooldownTicks();
        for (int i = 0; i < hits; i++) {
            AbsoluteDamageUtil.damage(target, boss.damageSources().mobAttack(boss), damage);
            // D-断魂：失控疾驰额外连击 AbsoluteDamage 路径统一补挂（结算由灭却之日 Post 统一触发）
            boss.markSoulSeverIfUnlocked(target);
        }
    }

    // ── NBT persistence ──

    void readAdditionalSaveData(CompoundTag tag) {
        this.guardCooldownTicks = tag.getInt("SilentSunGuardCooldown");
        this.guardActiveTicks = tag.getInt("SilentSunGuardActive");
        this.stageDigCooldownTicks = tag.getInt("SilentSunDigCooldown");
        this.stageBlockBombCooldownTicks = tag.getInt("SilentSunBlockBombCooldown");

        this.stageMinedBlocks.clear();
        ListTag minedList = tag.getList("SilentSunMinedBlocks", 8);
        for (int i = 0; i < minedList.size(); i++) {
            String name = minedList.getString(i);
            try {
                net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(name);
                if (rl != null) {
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(rl)
                        .ifPresent(b -> this.stageMinedBlocks.addLast(b.defaultBlockState()));
                }
            } catch (Exception ignored) {}
        }
    }

    void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("SilentSunGuardCooldown", this.guardCooldownTicks);
        tag.putInt("SilentSunGuardActive", this.guardActiveTicks);
        tag.putInt("SilentSunDigCooldown", this.stageDigCooldownTicks);
        tag.putInt("SilentSunBlockBombCooldown", this.stageBlockBombCooldownTicks);

        ListTag minedList = new ListTag();
        for (BlockState state : this.stageMinedBlocks) {
            minedList.add(StringTag.valueOf(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        }
        tag.put("SilentSunMinedBlocks", minedList);
    }
}
