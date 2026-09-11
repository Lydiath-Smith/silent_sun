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
 * 近身（&lt;3 格）像玩家左键一样**按刀攻速**推进普攻连击（progressCombo，伪玩家设计）；
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
    /** 导航刷新节流（W6）：不必每 tick 重算路径 */
    private int pathRefreshCooldown;
    private double lastPathX;
    private double lastPathZ;

    public static boolean isAvailable() {
        return IntegrationContract.isSlashBladeIntegrationAvailable();
    }

    public BladeAttackGoal(Mob boss) {
        this.boss = boss;
        this.redios = boss instanceof RediosEntity r ? r : null;
        this.setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
        this.available = IntegrationContract.isSlashBladeIntegrationAvailable();
    }

    @Override
    public void start() {
        if (!available || this.redios == null) return;
        // 首拍零冷却治理：四路攻击（近身连击/剑气/SA/幻影剑）给随机初值错峰，
        // 避免刀窗口开启第一个 tick 四路齐射导致斩击实体爆发。
        // 2026-09-10 用户裁决：SA 首拍定在 1~3 秒（20~59 tick），切刀后就能甩出 SA。
        this.cooldown = 20 + boss.getRandom().nextInt(40);
        // 普攻首拍按刀攻速（伪玩家，2026-09-01）：灭刀断 4.0 → 5 tick，随激怒缩短
        this.comboCooldown = this.redios.getAttackCooldownTicks();
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
        // 2026-09-10（W6 修复）：补上刀窗口判定——原先只判 isBladeAttackAllowed，而本 goal
        // 抢着 Flag.MOVE（压制 RandomStrollGoal 等），tick() 里又在非刀窗口直接 return →
        // 结果"刀没出鞘也占着移动标志每 tick 空转"，Boss 在非刀窗口既不能漫游也不受别的 goal 支配。
        // 刀窗口是否开启是 tick() 里早就有的判据，这里补齐即可（不改变"刀窗口期间追击"的设计）。
        if (this.redios != null && !this.redios.isBladeModeActive()) {
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
        // M18：刀窗口期间追击目标（MOVE flag + 导航逼近），玩家退到 3 格外不再站桩。
        // 2026-09-10（W6 修复）：导航加节流——6 tick 一次，或目标水平位移超过 1.5 格才重算，
        // 避免每 tick 调 moveTo 重置寻路重算计时（配合 canUse 的刀窗口判定，非窗口不再占 MOVE）。
        if (this.pathRefreshCooldown > 0) {
            this.pathRefreshCooldown--;
        }
        double pathDx = target.getX() - this.lastPathX;
        double pathDz = target.getZ() - this.lastPathZ;
        if (this.pathRefreshCooldown <= 0 || pathDx * pathDx + pathDz * pathDz > 2.25) {
            boss.getNavigation().moveTo(target, 1.0);
            this.lastPathX = target.getX();
            this.lastPathZ = target.getZ();
            this.pathRefreshCooldown = 6;
        }

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
        // 近身（<3 格）：像玩家左键一样按攻速推进普攻连击（progressCombo）
        if (dist < 3.0) {
            if (comboCooldown <= 0) {
                // D-断魂：拔刀剑攻击发起时统一补挂断魂（海天解锁时；低频率，防 amplifier 秒满）
                if (this.redios != null) this.redios.markSoulSeverIfUnlocked(target);
                // 推进 combo：updateComboSeq 内部无条件调 clickAction（A1 段攻击动作 = 玩家左键
                // doSlash，刀光由此产出——伪玩家不额外直发刀光，2026-09-01 移除 trySpawnBossSlashEffect）。
                IntegrationContract.tryProgressCombo(boss);
                // 普攻频率（2026-09-01 伪玩家）：按刀攻速——灭刀断 4.0 → 5 tick/刀，
                // 激怒加成仅原 20%（满层 1.6x → 3 tick 封顶），P2 失控疾驰 5 tick 固定，
                // weakpoint 固定冷却（见 CombatStatModulator.attackCooldownTicks）。
                // 之前 10+rand(8) 是双重驱动时代防爆发的临时节奏，去重后恢复攻速（设计稿 §3.1）。
                comboCooldown = this.redios.getAttackCooldownTicks();
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
            // 2026-09-10 用户裁决：SA 间隔 3~5 秒（60~99 tick）
            cooldown = 60 + boss.getRandom().nextInt(40);
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
