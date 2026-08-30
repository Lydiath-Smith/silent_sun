package com.lydiath.silent_sun.integration;

import com.lydiath.silent_sun.entity.IntegrationContract;
import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.world.InteractionHand;
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

        // combo 状态机驱动（2026-09-01 去重）：
        // slashblade（重锋 2.0.3/2.0.7、Refix 三版一致）ItemSlashBlade.inventoryTick 对持刀的
        // 任意 LivingEntity（含 Boss Mob）每 tick 自己驱动 resolvCurrentComboState + tickAction
        //（反编译确认，isInMainhand 对 Mob 主手成立）——我们不再手动驱动 tickAction，否则
        // 双重驱动 → tickAction 每 tick 两次 → 刀光翻倍（"刚切刀准备攻击就有刀光"）。
        // 这里只保留 combo 卡死守卫：combo 距上次回 NONE/standby 超阈值（400 tick）强制重置，
        // 防重锋版 combo 卡活跃段无限刷刀光。近身普攻频率仍由 comboCooldown = 10 + rand(8) 控制。
        IntegrationContract.tryTickBladeComboStuckGuard(boss);

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
                // 普攻挥刀刀光：复刻玩家左键 combo_a1 的斩击轨迹
                //（EntitySlashEffect，damage=0 纯视觉，伤害仍由近战 doHurtTarget 结算）。
                IntegrationContract.trySpawnBossSlashEffect(boss, target);
                // 推进 combo：updateComboSeq 内部无条件调 clickAction（A1 段攻击动作）。
                IntegrationContract.tryProgressCombo(boss);
                // 普攻频率（恢复历史）：10~18 tick（每秒约 1.1~1.4 次），不随攻速放大
                //（历史 jar 对照：comboCooldown = 10 + rand(8)）。
                comboCooldown = 10 + boss.getRandom().nextInt(8);
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

        // 收尾补 shooter：tryProgressCombo / tryInvokeRandomSA 走 slashblade 的 combo/SA 时间轴，
        // 对 Mob（非 Player）caster 生成的投射物（刀光 EntitySlashEffect / 次元斩 EntityJudgementCut /
        // 剑气 EntityDrive / 幻影剑 SummonedSword）可能不带 owner。RediosEntity.tick 里的 sanitize 在
        // 本 goal 之前跑，赶不上本 tick 刚生成的实体；这里在同一 tick 收尾再补一次，把「生成→被玩家
        // ArrowReflector 扫描」的 1 tick 空窗关掉，避免 getShooter()==null 崩端。
        if (this.redios != null) {
            IntegrationContract.sanitizeBossSummonedSwordShooters(this.redios);
        }
    }
}
