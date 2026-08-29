package com.lydiath.silent_sun.integration;

import com.lydiath.silent_sun.entity.IntegrationContract;
import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;

/**
 * 拔刀剑 Boss 攻击 Goal — 硬依赖 extinction_day_mod / slashblade / sbr_core
 * （2026-08-13 落地为 B，见 neoforge.mods.toml 必需前置声明）。
 * <p>
 * 反射调用通过 {@link IntegrationContract} 统一管理，具备日志输出和缓存刷新能力。
 * <p>
 * 近身（&lt;3 格）像玩家左键一样周期性推进普攻连击（progressCombo）；
 * 中距离（3~15 格）从 slash_arts 注册表随机施放一个 SA，冷却 80~120 tick。
 * 与 Boss 自身技能不冲突：仅当距离合适且冷却归零时释放，
 * 其余时间交给 Boss 自己的 Goal 处理。
 */
public class BladeAttackGoal extends Goal {
    private final Mob boss;
    private final RediosEntity redios;
    private final boolean available;
    private int cooldown;
    private int comboCooldown;
    /** 剑气（EntityDrive，super_burst_drive 效果）发射冷却：约 1.25 秒一发 */
    private int burstDriveCooldown;
    /** 幻影剑齐射冷却：约 3~4.5 秒一波。Boss（Mob）进不了 SummonedSwordArts 玩家入口，由 silent_sun 代打直发 */
    private int phantomSwordCooldown;
    /**
     * 拔刀剑实体斩击统一门控：驱动 {@code tryTickBladeCombo}（ComboState.tickAction，
     * 剑气/斩击/幻影剑实体的实际产出点）的节奏。归零时才执行一次并重置为
     * {@link RediosEntity#getAttackCooldownTicks()}，使拔刀剑实体斩击速度与近战攻击速度一致，
     * 避免 blade mode 一激活就每 tick 无节流产实体、把剑技速度拉满导致卡顿。
     */
    private int bladeEntityCooldown;

    /** 刀光实体护栏（2026-08-30）：Boss 周围 64 格内 slash_effect 存量上限，超出跳过 combo 驱动。
     *  实测稳态存量约 45 条（原阈值 80 不触发），收紧到 10 让护栏真正生效。 */
    private static final int SLASH_EFFECT_CAP = 10;

    /** combo 驱动最小间隔（tick）：用户裁决「0 加成时每秒 3 条刀光就够多了」。
     *  驱动频率下限 = 7 tick（每秒约 3 次驱动）——低于此会因 slashblade 内部 combo
     *  时间轴一次性全量产出导致 slash_effect 洪峰（实测 10 秒上万条 + 触发清扫误杀）。 */
    private static final int BLADE_COMBO_DRIVE_MIN_INTERVAL_TICKS = 7;

    public static boolean isAvailable() {
        return IntegrationContract.isSlashBladeIntegrationAvailable();
    }

    public BladeAttackGoal(Mob boss) {
        this.boss = boss;
        this.redios = boss instanceof RediosEntity r ? r : null;
        this.setFlags(EnumSet.of(Flag.LOOK));
        this.available = IntegrationContract.isSlashBladeIntegrationAvailable();
    }

    @Override
    public void start() {
        if (!available || this.redios == null) return;
        // 首拍零冷却治理：四路攻击（近身连击/剑气/SA/幻影剑）给随机初值错峰，
        // 避免刀窗口开启第一个 tick 四路齐射导致斩击实体爆发。
        this.cooldown = 40 + boss.getRandom().nextInt(40);
        this.comboCooldown = 10 + boss.getRandom().nextInt(8);
        this.burstDriveCooldown = 20 + boss.getRandom().nextInt(10);
        this.phantomSwordCooldown = 30 + boss.getRandom().nextInt(20);
        this.bladeEntityCooldown = Math.max(
            this.redios != null ? this.redios.getAttackCooldownTicks() : 10,
            BLADE_COMBO_DRIVE_MIN_INTERVAL_TICKS);
        ItemStack blade = IntegrationContract.tryEquipBlade(this.redios);
        if (!blade.isEmpty()) {
            boss.setItemInHand(InteractionHand.MAIN_HAND, blade);
        }
    }

    @Override
    public boolean canUse() {
        if (!available) return false;
        if (this.redios != null && !this.redios.isBladeAttackAllowed()) {
            return false;
        }
        LivingEntity target = boss.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!available) return;
        if (this.redios != null && (!this.redios.isBladeAttackAllowed() || !this.redios.isBladeModeActive())) {
            return;
        }
        LivingEntity target = boss.getTarget();
        if (target == null || boss.level().isClientSide()) return;

        // 强制 Boss 面向目标：slashblade 的剑气/斩击/幻影剑/SA 发射方向取
        // caster.getLookAngle()（基于 yRot/xRot），而 getLookControl().setLookAt 只改
        // yHeadRot；Mob 站桩不移动时 yRot 不刷新，特效会朝旧方向打偏。
        // lookAt(360,360) 立即把 yRot/xRot 指向目标，保证幻影剑等特效朝敌人发射。
        boss.lookAt(target, 360.0F, 360.0F);

        // 每 tick 驱动 ComboState 生命周期：Mob 无 inventoryTick，需手动推进
        // TimeLineTickAction（剑气/斩击/幻影剑等特效），与玩家拔刀完全一致。
        // 但实体斩击须与近战攻击冷却 getAttackCooldownTicks() 对齐：blade mode 一激活
        // 每 tick 无节流驱动 tickAction 会把剑技速度拉满、实体爆发导致卡顿，这里按攻击速度门控。
        if (bladeEntityCooldown > 0) {
            bladeEntityCooldown--;
        } else {
            // 2026-08-30 实体护栏：slashblade 时间轴对 Mob 一次性全量产出刀光，
            // Boss 周围 64 格内 slash_effect 存量超阈值时跳过本次驱动并延长冷却，
            // 让存量实体先被自然清理，防止 10 秒万条级实体爆发卡服。
            if (boss.level() instanceof ServerLevel serverLevel) {
                java.util.List<Entity> effects = serverLevel.getEntitiesOfClass(Entity.class,
                    boss.getBoundingBox().inflate(64.0),
                    e -> IntegrationContract.isSlashEffectEntity(e) && e.isAlive());
                if (effects.size() > SLASH_EFFECT_CAP) {
                    bladeEntityCooldown = 20;
                    return;
                }
            }
            IntegrationContract.tryTickBladeCombo(boss);
            // 降速（2026-08-30）：combo 驱动间隔不低于 7 tick（每秒约 3 次），
            // 从源头限制 slashblade 内部 combo 时间轴产出的刀光速率。
            int base = this.redios != null ? this.redios.getAttackCooldownTicks() : 10;
            bladeEntityCooldown = Math.max(base, BLADE_COMBO_DRIVE_MIN_INTERVAL_TICKS);
        }

        boss.getLookControl().setLookAt(target);

        if (cooldown > 0) {
            cooldown--;
        }
        if (comboCooldown > 0) {
            comboCooldown--;
        }
        if (burstDriveCooldown > 0) {
            burstDriveCooldown--;
        }
        if (phantomSwordCooldown > 0) {
            phantomSwordCooldown--;
        }

        double dist = boss.distanceTo(target);
        // 近身（<3 格）：像玩家左键一样周期性推进普攻连击（progressCombo）
        if (dist < 3.0) {
            if (comboCooldown <= 0) {
                // D-断魂：拔刀剑攻击发起时统一补挂断魂（海天解锁时；低频率，防 amplifier 秒满）
                if (this.redios != null) this.redios.markSoulSeverIfUnlocked(target);
                // 2026-08-12 普攻挥刀刀光：复刻玩家左键 combo_a1 的斩击轨迹
                //（EntitySlashEffect，damage=0 纯视觉，伤害仍由近战 doHurtTarget 结算）。
                IntegrationContract.trySpawnBossSlashEffect(boss, target);
                IntegrationContract.tryProgressCombo(boss);
                comboCooldown = this.redios != null ? this.redios.getAttackCooldownTicks() : 10 + boss.getRandom().nextInt(8);
                // 剑气跟随普通攻击：super_burst_drive 的剑气链路在灭却之日里被
                // instanceof Player 检查挡掉（Boss 是 Mob），这里由 silent_sun 反射直发
                // slashblade:drive 剑气，方向随 Boss 当前朝向（lookAt 已同步 yRot）。
                if (burstDriveCooldown <= 0) {
                    IntegrationContract.trySpawnBurstDrive(boss);
                    burstDriveCooldown = 25;
                }
            }
        } else if (dist <= 15.0 && cooldown <= 0) {
            // 中距离（3~15 格）：从 slash_arts 注册表随机施放一个 SA
            if (this.redios != null) this.redios.markSoulSeverIfUnlocked(target);
            IntegrationContract.tryInvokeRandomSA(boss);
            cooldown = 80 + boss.getRandom().nextInt(40);
        }

        // 幻影剑齐射：Boss 进不了 SummonedSwordArts（perform* 均要求 ServerPlayer），
        // 这里由 silent_sun 代打生成幻影剑直射目标；命中由 RediosEntity.tick 的
        // tryTickBossBladePlayerHits 用 doForceHitEntity 绕过 pvp_enable=false 强制结算。
        if (phantomSwordCooldown <= 0 && dist <= 20.0) {
            if (this.redios != null) this.redios.markSoulSeverIfUnlocked(target);
            IntegrationContract.trySpawnBossPhantomSwords(boss, target);
            phantomSwordCooldown = 60 + boss.getRandom().nextInt(30);
        }

        // 收尾补 shooter：tryTickBladeCombo / tryInvokeRandomSA 走 slashblade 的 combo/SA 时间轴，
        // 对 Mob（非 Player）caster 生成的投射物（刀光 EntitySlashEffect / 次元斩 EntityJudgementCut /
        // 剑气 EntityDrive / 幻影剑 SummonedSword）可能不带 owner。RediosEntity.tick 里的 sanitize 在
        // 本 goal 之前跑，赶不上本 tick 刚生成的实体；这里在同一 tick 收尾再补一次，把「生成→被玩家
        // ArrowReflector 扫描」的 1 tick 空窗关掉，避免 getShooter()==null 崩端。
        if (this.redios != null) {
            IntegrationContract.sanitizeBossSummonedSwordShooters(this.redios);
        }
    }
}
