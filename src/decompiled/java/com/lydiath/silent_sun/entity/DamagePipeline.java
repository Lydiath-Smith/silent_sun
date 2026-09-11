package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.entity.pipeline.DamageContext;
import com.lydiath.silent_sun.entity.pipeline.DamagePipelineStage;
import com.lydiath.silent_sun.entity.pipeline.DamageResult;
import com.lydiath.silent_sun.rules.RediosRules;
import com.lydiath.silent_sun.util.AbsoluteDamageUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Damage pipeline for RediosEntity.
 * <p>
 * Replaces the monolithic {@code hurt()} method with 21 ordered stages
 * (见设计稿《设计文稿-重制版.md》§3.1/§7.1 判定顺序；原始 11 段方案见合集历史),
 * each responsible for one concern.  Stages run in declaration order.
 * The pipeline short-circuits on the first {@link DamageResult#cancel()}.
 * <p>
 * Lives in the {@code entity} package to access RediosEntity package-private
 * fields and helpers without exposing them as public API.
 */
public final class DamagePipeline {

    private DamagePipeline() {}

    // ────────────── Pipeline ──────────────

    private static final DamagePipelineStage[] STAGES = {
        DamagePipeline::stageDirectKillGuard,
        DamagePipeline::stageSelfDamageGuard,
        DamagePipeline::stagePhaseConfig,
        DamagePipeline::stageCreativeModeGuard,
        DamagePipeline::stageExpelledPlayerGuard,
        DamagePipeline::stagePhase1AbsoluteDefense,
        // 2026-09-11 实测修复（C3 顺序洞）：`stageNoResurrectionGuard` / `stagePhase1VoteGuard`
        // 原先排在 `stageAttackerResolution` **之前** → 创造/旁观/Mode2 玩家或无效攻击者的命中
        // 虽然在 idx8 被取消，但**阶段推进已经发生**（setHealth(1) + enterNoResurrectionPhase2 /
        // transitionTo(PHASE1_PENDING) + enterPendingState）→ "不掉血却推阶段"。
        // 移到攻击者校验之后：无效攻击者的伤害在 idx8 取消 → 管线终止 → 不再推进。
        DamagePipeline::stageAttackerResolution,
        DamagePipeline::stageNoResurrectionGuard,
        DamagePipeline::stagePhase1VoteGuard,
        DamagePipeline::stageAdaptiveGuardBlock,
        DamagePipeline::stageDamageCap,
        DamagePipeline::stageSorrowToil,
        DamagePipeline::stageHealImmunity,
        DamagePipeline::stageUnityColorless,
        DamagePipeline::stageUncontrolledSprintDodge,
        DamagePipeline::stageDodge,
        DamagePipeline::stageVoidAllThingsDodge,
        DamagePipeline::stageChaosRuin,
        DamagePipeline::stageDeathCheat,
        DamagePipeline::stagePhase1Lock,
        DamagePipeline::stagePhase2Pending,
        // 2026-09-11 实测修复（R1 白嫖反伤）：反射**移到锁血阶段之后**。原先它在锁血之前，
        // 于是段底锁血窗口（1.7 全反射 ratio=1.0 / 2.8 无色期，titleLockTicks 15~30 秒）里
        // 玩家打不动 Boss（伤害被钳段底并取消）却照吃满额反伤。移到末端后：锁血一旦 cancel，
        // 管线立即终止，反射不再执行；未取消时反射照常发生（且此时 ctx.amount 已是最终生效值）。
        DamagePipeline::stageReflect,
    };

    /**
     * Run all pipeline stages.  Returns the final context.
     * <p>
     * Callers should check {@link DamageContext#cancelled} and, if false,
     * pass {@code ctx.amount} to {@code super.hurt()}.
     */
    public static DamageContext run(RediosEntity boss, DamageSource source, float amount) {
        DamageContext ctx = new DamageContext(boss, source, amount);
        for (DamagePipelineStage stage : STAGES) {
            if (stage.process(ctx).cancelled()) {
                ctx.cancelled = true;
                return ctx;
            }
        }
        return ctx;
    }

    // ────────────── Stage 0: Direct command kill guard ──────────────

    /**
     * 直接指令性击杀无效化。
     * <p>
     * /kill、/damage ... generic_kill、命令/模组"强制清除生物"等无任何实体来源的
     * 指令性伤害一律取消。这类伤害与战斗流程无关，放行会导致：
     * ① P2 战斗中被 stagePhase2Pending 判濒死 → 白嫖胜利与掉落；
     * ② 冻结期（投票/转阶段）被 stageDeathCheat 每 tick 触发 → 音效与广播反复刷屏。
     * <p>
     * 竭力之悲自损（setHealth 直扣）不经管线，不受影响；有实体来源的攻击全部放行；
     * 无实体源的 magic 仅可能来自命令（项目内 magic 伤害只经 AbsoluteDamageUtil 攻击
     * 他人，且对 RediosEntity 硬编码免疫）。虚空伤害（outOfWorld）不在此拦截，
     * 保留竭力之悲激活时的虚空吸收（stageSorrowToil）与 tickHeightFlight 防坠落。
     */
    private static DamageResult stageDirectKillGuard(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide || ctx.source == null) {
            return DamageResult.proceed();
        }
        if (ctx.source.getDirectEntity() == null && ctx.source.getEntity() == null) {
            String msgId = ctx.source.getMsgId();
            // 2026-09-09 防打穿：原版 generic_kill 的 message_id 是驼峰 "genericKill"（/kill、kill()、
            // /damage 无实体源路径），补上与蛇形 "generic_kill" 一并拦截，避免 P2 濒死合法击杀窗口被白嫖。
            // 2026-09-10（用户裁决）：**移除 "magic" 这一项**。"无实体源的 magic"在模组环境里太常见
            // （范围/环境魔法、脚本伤害等），把它当 /kill 拦掉会变成"某类攻击打 Boss 不掉血"的误判，
            // 误伤面大于收益。真正要拦的作弊路径是 generic_kill / kill。
            if ("generic_kill".equals(msgId) || "genericKill".equals(msgId)
                || "kill".equals(msgId)) {
                return DamageResult.cancel();
            }
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 0.5: Self-damage guard ──────────────

    /**
     * 自伤豁免：Boss 被「自己的 slashblade 投射物」打到的伤害一律取消。
     * <p>
     * 2026-08-30 实测：slashblade 内部 combo 时间轴（ItemSlashBlade.inventoryTick 对持刀 Mob
     * 每 tick 驱动 tickAction，2026-09-01 确认）生成的剑气/刀光
     * 在生成瞬间 shooter/owner 为空，slashblade 的 onHitEntity 用「实体自身」作伤害源结算
     * （getShooter()==null → indirectMagic(实体, 实体)），密集命中时会把 Boss 自己「穿死」。
     * 判定：directEntity 是 slashblade 实体（包名 mods.flammpfeil.slashblade.entity.）且
     * 其 owner == Boss，或 source.getEntity() == Boss——都是 Boss 自己的武器误伤自己。
     */
    private static DamageResult stageSelfDamageGuard(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide || ctx.source == null) {
            return DamageResult.proceed();
        }
        Entity direct = ctx.source.getDirectEntity();
        Entity sourceEnt = ctx.source.getEntity();
        if (direct instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
            // 投射物（slashblade 剑气/刀光等）的 owner == Boss → 自己的武器误伤自己。
            if (projectile.getOwner() == boss || sourceEnt == boss) {
                return DamageResult.cancel();
            }
        } else if (sourceEnt == boss) {
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 1: PhaseArmor + config + debug ──────────────

    private static DamageResult stagePhaseConfig(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }

        if (boss.bossState.isPhase1()) {
            double armor = SilentSunConfig.PHASE1_ARMOR_VALUE.get();
            ctx.amount *= (1.0f - SilentSunConfig.PHASE1_DAMAGE_REDUCTION.get().floatValue());
            if (boss.isWeaponWeakpointWindowActive()) {
                // 振刀弱点窗口：对 Boss 造成额外伤害并削减其护甲，
                // 使玩家在该窗口内的输出明显高于普通攻击（即使面对高护甲）。
                armor *= (1.0 - RediosRules.weaponWeakpointArmorPierce());
                ctx.amount *= (float) RediosRules.weaponWeakpointDamageMultiplier();
            }
            boss.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        }
        if (boss.bossState.isPhase2()) {
            double armor = SilentSunConfig.PHASE2_ARMOR_VALUE.get();
            ctx.amount *= (1.0f - SilentSunConfig.PHASE2_DAMAGE_REDUCTION.get().floatValue());
            if (boss.isWeaponWeakpointWindowActive()) {
                armor *= (1.0 - RediosRules.weaponWeakpointArmorPierce());
                ctx.amount *= (float) RediosRules.weaponWeakpointDamageMultiplier();
            }
            boss.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        }

        if (RediosRules.damageSourceDebug() && ctx.amount > 0.0f) {
            boss.debugLogUnknownDamageSource(ctx.source, ctx.amount);
        }

        return DamageResult.proceed();
    }

    // ────────────── Stage 2: Creative mode guard ──────────────

    private static DamageResult stageCreativeModeGuard(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }

        if (!BossTargeting.playerOnlyMode()) {
            return DamageResult.proceed();
        }
        Entity sourceEnt = ctx.source.getEntity();
        if (!(sourceEnt instanceof ServerPlayer creativePlayer) || !creativePlayer.isCreative()
            || ctx.amount <= 0.0f) {
            return DamageResult.proceed();
        }

        UUID creativeId = creativePlayer.getUUID();
        if (boss.anticheat.creativeStrikers.add(creativeId)) {
            boss.clearAllExternalEffects();
            boss.reapplySelfBuffs();
            boss.anticheat.counterCheatAttacker(creativePlayer);
            creativePlayer.sendSystemMessage(
                boss.rediosSigned(
                    Component.translatable("message.silent_sun.redios.anticheat.creative_return")
                        .withStyle(ChatFormatting.GOLD)));

            // G4: 记录该创造玩家"检测开始(首次攻击) → 切回生存"期间的物品获得追踪，
            // 期间拾取的物品切回生存时每种按最大堆叠一组归还
            boss.anticheat.beginCreativeTracking(creativeId, creativePlayer);
        }

        if (boss.anticheat.creativeLeaveTimerTicks < 0) {
            // 2026-08-13：创造玩家一直在（区块保持加载）则 10 分钟后再撤离；
            // 中途离开/区块卸载由 checkBattleAreaUnloaded 走区块卸载结算提前退场。
            boss.anticheat.creativeLeaveTimerTicks = AntiCheatLayer.CREATIVE_LEAVE_WINDOW_TICKS;
            MutableComponent timerMsg = Component.translatable("message.silent_sun.redios.anticheat.creative_leave_timer").withStyle(ChatFormatting.GOLD);
            boss.broadcastToParticipants(boss.rediosSigned(timerMsg));
        }

        return DamageResult.proceed();
    }

    // ────────────── Stage 3: Expelled player guard ──────────────

    private static DamageResult stageExpelledPlayerGuard(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        if (boss.isDamageFromExpelledPlayer(ctx.source)) {
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 3.5: Phase1.9 absolute defense (optional) ──────────────

    /**
     * Phase1.9 完全防御（兼容性补丁，默认关闭）。
     * <p>
     * 当 {@code redios.phase1AbsoluteDefense} 开启，且 Boss 处于 phase=1、
     * titleIndex=9（有所不为）时，对一切伤害强制免伤。用于排查整合包中该头衔
     * 被外部模组异常破防/秒杀的问题；开启后此头衔期间 Boss 无法受伤，正常投票
     * 推进也会被暂停，仅作诊断隔离使用。
     */
    private static DamageResult stagePhase1AbsoluteDefense(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        if (SilentSunConfig.PHASE1_ABSOLUTE_DEFENSE.get()
            && boss.phase == 1 && boss.titleIndex == 9) {
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 4: noResurrection Phase 2 entry ──────────────

    private static DamageResult stageNoResurrectionGuard(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        // 收紧为 COMBAT（2026-09-01 修复）：原用 isPhase1()（含 VOTE/PENDING/TRANSITION），
        // 冻结态血量恒 1 → `health - amount <= 1` 恒真 → 幻影攻击即触发
        // enterNoResurrectionPhase2（满血 P2），废掉投票与 1.9 锁血。
        if (boss.noResurrection && boss.bossState.isPhase1Combat()
            && boss.getHealth() - ctx.amount <= 1.0f) {
            boss.setHealth(1.0f);
            // 合法推进：同步反作弊基线，避免跨 tick 低血量篡改误判
            boss.anticheat.markLegalHealthChange(1.0f);
            boss.clearAllExternalEffects();
            boss.enterNoResurrectionPhase2();
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 5: Phase 1 last title → vote ──────────────

    private static DamageResult stagePhase1VoteGuard(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        // 200.0f 原为「段长」魔法数（maxHealth 2000 / 10 头衔）；phaseMaxHealth 可配置，
        // 必须按实际段长计算，否则改最大血量后 1.9 濒死判定错位（2026-09-01 修复）。
        float segment = boss.getMaxHealth() / (float) RediosEntity.PHASE1_TITLES.size();
        if (boss.bossState.isPhase1() && boss.titleIndex == RediosEntity.PHASE1_TITLES.size() - 1
            && boss.getHealth() < segment && ctx.amount > 0.0f
            && boss.getHealth() - ctx.amount <= 1.0f) {
            boss.setHealth(1.0f);
            // 合法推进：同步反作弊基线，避免跨 tick 低血量篡改误判
            boss.anticheat.markLegalHealthChange(1.0f);
            // 仅从 PHASE1_COMBAT 合法进入 PENDING；濒死/投票/转阶段期间只取消伤害，
            // 不再重复重置状态，防止 beginPhase2Choice 被反复调用导致投票提示文案重发。
            if (boss.bossState == BossState.PHASE1_COMBAT) {
                boss.transitionTo(BossState.PHASE1_PENDING);
                boss.enterPendingState();
            }
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 6: Attacker resolution + mode + cap ──────────────

    private static DamageResult stageAttackerResolution(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }

        LivingEntity attacker = boss.tryResolveDamageAttacker(ctx.source);
        ctx.attacker = attacker;

        if (attacker != null) {
            // 统一受伤判定：无效攻击者（模式不符 / 被驱逐 / 反作弊免疫）的伤害直接取消
            if (!BossTargeting.isValidDamageAttacker(boss, attacker)) {
                return DamageResult.cancel();
            }
            // 有主人的宠物在两种模式下都受 FRIENDLY_MOB_DAMAGE_CAP 上限约束。
            // 2026-09-11（代码审计 G06 #2）：此处**刻意保持**「只判有主人」——本处管的是**限伤范围**，
            // 不是攻击合法性。合法性已由上方 isValidDamageAttacker 统一为「Mode 1 要求主人参战」；
            // 而 Mode 2 下 battleParticipants 恒为空（markBattleParticipant 在 !playerOnly 时直接返回），
            // 若此处一并改判「主人参战」，会连带取消斗蛐蛐模式下宠物的 25 点限伤 —— 那是行为变更，非本次目标。
            if (attacker instanceof OwnableEntity ownable) {
                if (ownable.getOwnerUUID() != null) {
                    ctx.amount = Math.min(ctx.amount,
                        SilentSunConfig.FRIENDLY_MOB_DAMAGE_CAP.get().floatValue());
                }
            }

            boss.markBattleParticipant(attacker);
            // 2026-09-11（代码审计 G06 #8 / G07 #7 修复）：原此处还写 ctx.participantMarked = true，
            // 但该字段**全库零读取** —— DamageContext 里承诺的「防止后续阶段重复调用」从未实现，
            // 真正防重的是 stageAttackerResolution 只被调用一次。字段与本次写入一并删除。
        }

        return DamageResult.proceed();
    }

    // ────────────── Stage 7: 1.9 Adaptive guard block ──────────────

    private static DamageResult stageAdaptiveGuardBlock(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }

        if (boss.guardUnlocked && ctx.amount > 0.0f) {
            if (boss.weapons.guardActiveTicks <= 0) {
                boss.weapons.tryGuardBlock(ctx.source);
            }
            if (boss.weapons.guardActiveTicks > 0) {
                // 2026-09-10（用户裁决 C6/待确认 6）：「格挡就全免」——格挡窗口内本次伤害全额免除，
                // 不再按 adaptive_block_damage_reduction 打折（原 0.8 口径改成减伤口径的产物）。
                // 该配置键自此不再被消费，保留仅为旧配置兼容（见 RediosRules 同名 setter 注释）。
                return DamageResult.cancel();
            }
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 8: Damage cap ──────────────

    private static DamageResult stageDamageCap(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        if (ctx.amount > 0.0f) {
            ctx.amount = boss.applyDamageCap(ctx.amount, ctx.source);
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 9: Sorrow Toil void absorption ──────────────

    private static DamageResult stageSorrowToil(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        if (boss.isSorrowToilActive() && boss.isVoidDamage(ctx.source)) {
            boss.addSoulSeverY(Math.max(0L, (long) Math.ceil(ctx.amount)));
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 10: Heal immunity absorption ──────────────

    private static DamageResult stageHealImmunity(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        if ((boss.bossState.isPhase1() || boss.bossState.isPhase2()) && ctx.amount > 0.0f
            && boss.isHealImmunityDamage(ctx.source)) {
            long inc = Math.max(0L, (long) Math.ceil(ctx.amount));
            boss.addSoulSeverY(inc);
            boss.setHeal(ctx.amount);
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 11: Unity Power / Colorless 20% absorb ──────────────

    private static DamageResult stageUnityColorless(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        if ((boss.isUnityPowerActive() || boss.isColorlessActive())
            && ctx.amount > 0.0f && boss.getRandom().nextFloat() < 0.2f) {
            boss.addSoulSeverY(Math.max(0L, (long) Math.ceil(ctx.amount)));
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 12: Phase 2.1 dodge (AoE + single-target) ──────────────

    private static DamageResult stageUncontrolledSprintDodge(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        // 2.1 失控疾驰：范围攻击（爆炸/间接魔法/龙息/药水云）与个体锁定（单体直接
        // 命中：近战/箭/投掷物等）均按 25% 闪避。此前只躲 isAreaDamage，单体命中漏闪。
        // 5.1：该闪避为激活后直到死亡都生效的永久效果，由 uncontrolledSprintUnlocked
        //（进入过 2.1 即永久为 true）驱动，头衔转换不失效。
        if (boss.uncontrolledSprintUnlocked
            && (boss.isAreaDamage(ctx.source) || ctx.source.getEntity() instanceof LivingEntity)
            && boss.getRandom().nextDouble() < RediosRules.uncontrolledSprintAoEDodgeChance()) {
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 13: Wrong interference / dodge ──────────────

    private static DamageResult stageDodge(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }

        if (boss.isWrongInterferenceActive()) {
            if (ctx.source.getEntity() instanceof LivingEntity
                && boss.getRandom().nextFloat() > 0.2f) {
                return DamageResult.cancel();
            }
        } else if (boss.dodgeChance > 0.0
            && ctx.source.getEntity() instanceof LivingEntity
            && boss.getRandom().nextDouble() < boss.dodgeChance) {
            return DamageResult.cancel();
        }

        return DamageResult.proceed();
    }

    // ────────────── Stage 13.5: phase2.9 确定性闪避 + 反应式传送避让 ──────────────

    /**
     * phase2.9（空无万象）专属确定性闪避：被玩家远程立体范围锁定（无妄之终球体扫描）
     * 命中时 100% 取消该次伤害，并置反应式避让传送标志，让 {@code tickVoidAllThings}
     * 立即执行一次独立于 40 tick 常规冷却的脱离传送。
     */
    private static DamageResult stageVoidAllThingsDodge(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide || ctx.source == null) {
            return DamageResult.proceed();
        }
        if (!boss.isVoidAllThingsActive()) {
            return DamageResult.proceed();
        }
        Entity sourceEntity = ctx.source.getEntity();
        if (!(sourceEntity instanceof ServerPlayer player) || !player.isAlive()) {
            return DamageResult.proceed();
        }
        if (!boss.battleParticipants.contains(player.getUUID())) {
            return DamageResult.proceed();
        }
        // 排除拔刀剑投射物（刀光/剑气/幻影剑/次元斩）：它们的伤害非立体范围锁定。
        Entity directEntity = ctx.source.getDirectEntity();
        if (directEntity != null && isSlashBladeProjectile(directEntity)) {
            return DamageResult.proceed();
        }
        // 范围伤害判据：爆炸/魔法/AOE 云，或 Boss 距玩家 > 玩家交互距离（远程立体锁定）。
        boolean areaHit = boss.isAreaDamage(ctx.source);
        boolean remoteLock = boss.distanceTo(player)
            > player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        if (!areaHit && !remoteLock) {
            return DamageResult.proceed();
        }
        // 确定性闪避：取消该次伤害 + 置反应式避让传送。
        boss.voidDodgeTeleportPending = true;
        return DamageResult.cancel();
    }

    private static boolean isSlashBladeProjectile(Entity directEntity) {
        return directEntity.getClass().getName().startsWith("mods.flammpfeil.slashblade.entity.");
    }

    // ────────────── Stage 14: Chaos Ruin absolute damage ──────────────

    private static DamageResult stageChaosRuin(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }

        if (boss.isChaosRuinActive() && RediosRules.chaosRuinIncomingAbsoluteEnabled()
            && ctx.attacker != null) {
            if (!boss.isVoidAllThingsActive() && boss.getHealth() - ctx.amount <= 1.0f) {
                // 防止一击打穿死亡保护；同步反作弊基线，避免跨 tick 低血量误判
                boss.setHealth(1.0f);
                boss.anticheat.markLegalHealthChange(1.0f);
                return DamageResult.cancel();
            }
            // 绝对伤害直接减血（无视减伤但吃 cap）——不走 AbsoluteDamageUtil：
            // 该工具对 RediosEntity 硬编码免疫（防外部断魂伤害类绝对伤害绕过反作弊），
            // 混沌之墟是内部设计路径，需自行施加并同步合法伤害累计，防低血量篡改误判。
            // applyDamageCap 只减不增（超额部分按比例削减 + 硬上限），因此 finalDamage
            // 必然 ≤ ctx.amount；voidAllThings 激活时打穿到 ≤1 锁 1 血不推进（无敌语义）。
            // 2026-09-11（代码审计 P2 修复）：ctx.amount 已在 stageDamageCap（管线 idx 10）结算过，
            // 而 applyDamageCap 非幂等（先乘动态减伤、再对超阈值部分按比例削减），此处再算一次
            // 等于把减伤打两遍。按默认配置（initial 0.8 / ratio 0.5 / threshold 100 / hardCap 200）
            // 实测算例：原始 1000 经 stagePhaseConfig(×0.55) 与一次 cap 后为 105，二次 cap 后仅 21
            // —— 只有普通命中路径的 1/5，与注释宣称的「无视减伤但吃 cap」不符。
            float finalDamage = ctx.amount;
            float next = boss.getHealth() - finalDamage;
            if (next <= 1.0f) {
                boss.setHealth(1.0f);
                boss.anticheat.markLegalHealthChange(1.0f);
            } else if (finalDamage > 0.0f) {
                boss.setHealth(next);
                boss.anticheat.recordLegalDamage(finalDamage);
            }
            return DamageResult.cancel();
        }

        return DamageResult.proceed();
    }

    // ────────────── Stage 15: Reflect (thorns) ──────────────

    private static DamageResult stageReflect(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        // 2026-09-11 用户裁决「历史版本为准」——历史 A3 最终口径（`设计文稿合集.md:2399-2401`）：
        // **绝对伤害不反弹**。9bypass 真伤（断魂 / 2.7 全属性 / 第三方穿甲）命中 Boss 时不再触发反射反伤，
        // 与 A1「玩家对 Boss 的伤害仍受 200 上限与动态减伤约束」配套（既不豁免减伤、也不再吃反伤）。
        if (boss.isTrueDamage(ctx.source)) {
            return DamageResult.proceed();
        }
        if (boss.level().isClientSide || boss.reflectApplying
            || !(boss.reflectRatio > 0.0)) {
            return DamageResult.proceed();
        }
        // 冻结态跳过反射（2026-09-01 修复）：投票/转阶段/濒死锁血期血量恒 1，
        // 伤害随后被锁血阶段 setHealth(1)+cancel 吞掉——原实现在此之前先全额反伤，
        // 攻击者被「零伤害命中」白嫖反伤；无敌帧短路（hurt 入口）后普通攻击
        // 已不再进入管线，此处再拦冻结态确保反射只在正常战斗放行的伤害上触发。
        if (boss.bossState.isVoteOrTransition()
            || boss.bossState == BossState.PHASE1_PENDING
            || boss.bossState == BossState.PHASE2_PENDING) {
            return DamageResult.proceed();
        }

        Entity entity = ctx.source.getEntity();
        if (!(entity instanceof LivingEntity attacker)) {
            return DamageResult.proceed();
        }

        if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return DamageResult.proceed();
        }

        // 2026-09-11（代码审计 G07 #3 修复）：reflectApplying 是「反伤进行中」的重入闸门（L574 读取），
        // 全库无第二处复位点、也不入档 —— 原先无 try/finally，attacker.hurt 走进第三方 hurt/事件链
        // 一旦抛异常，闸门永久保持 true，Boss 的反伤静默失效直至实体重载。
        boss.reflectApplying = true;
        try {
            attacker.hurt(boss.damageSources().thorns(boss),
                (float) ((double) ctx.amount * boss.reflectRatio));
        } finally {
            boss.reflectApplying = false;
        }

        return DamageResult.proceed();
    }

    // ────────────── Stage 16: Death cheat ──────────────

    private static DamageResult stageDeathCheat(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide || boss.bossState == BossState.PHASE2_PENDING
            || boss.bossState == BossState.PHASE1_PENDING) {
            return DamageResult.proceed();
        }
        if (boss.getHealth() - ctx.amount > 0.0f) {
            return DamageResult.proceed();
        }
        // 正常战斗状态下伤害把血量打到 ≤0 属于合法击杀流程，不视为死亡作弊：
        //  - P1：由 stagePhase1Lock 锁 1 血并推进头衔/阶段过渡；
        //  - P2：由 stagePhase2Pending 锁 1 血并进入 PHASE2_PENDING 内部死亡过渡。
        // 放行让后续阶段处理，避免高爆发一击打穿最后 1 血被误判为作弊反复触发反作弊。
        // 2026-09-10：投票/转场同样属于"锁血窗口"（stagePhase1Lock 会钳 1 并取消），
        // 原实现把它们落到下方惩罚分支 → 断魂等机制触发时误报作弊 + 清效果 + 广播惊扰玩家。
        // 2026-09-11 作者裁决（`docs/设计文稿-重制版.md` L311）：六个 BossState 全放行使下方惩罚分支
        // **事实上不可达**，且**明确「保持不恢复」**（不补判据、不恢复可达性）—— 作弊惩罚已改由
        // 「重建回场」路径触发（`rebuildFromRecord` → `counterAllCheatAttackers(level, true)`，设计稿 L312-313）。
        // 故下方 `deathCheatStrikeCount`/惩罚体恒不执行属**既定设计口径**，不是可修缺陷；
        // 考古依据（勿再按「死代码」提案恢复）：e97dff1 的 diff 显示本判据是为修「投票/转场误报作弊+刷屏」
        // 而**故意**扩大的（项目彻查报告 C10）。
        if (boss.bossState.isPhase1Combat() || boss.bossState.isPhase2Combat()
            || boss.bossState == BossState.PHASE1_VOTE
            || boss.bossState == BossState.PHASE1_TRANSITION) {
            return DamageResult.proceed();
        }

        boss.anticheat.deathCheatStrikeCount++;
        if (boss.anticheat.deathCheatStrikeCount > 9999) boss.anticheat.deathCheatStrikeCount = 9999;
        // 锁血恢复：仅在血量偏离 1 时写入，避免冻结期每 tick 冗余 setHealth 触发属性同步
        if (boss.getHealth() != 1.0f) {
            boss.setHealth(1.0f);
        }

        if (boss.anticheat.deathCheatStrikeCount == 1) {
            boss.clearAllExternalEffects();
            boss.reapplySelfBuffs();
            boss.level().playSound(null, boss.blockPosition(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.0f, 0.5f);
            MutableComponent tip = Component.translatable("message.silent_sun.redios.anticheat.death_cheat_1")
                .withStyle(ChatFormatting.GRAY);
            boss.broadcastToParticipants(boss.rediosSigned(tip));

        } else if (boss.anticheat.deathCheatStrikeCount == 2) {
            boss.clearAllExternalEffects();
            boss.reapplySelfBuffs();
            boss.anticheat.counterCheatAttacker(ctx.source.getEntity());
            boss.anticheat.counterAllCheatAttackers((ServerLevel) boss.level());
            boss.level().playSound(null, boss.blockPosition(),
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f);
            MutableComponent warn = Component.translatable("message.silent_sun.redios.anticheat.death_cheat_2")
                .withStyle(ChatFormatting.DARK_RED);
            boss.broadcastToParticipants(boss.rediosSigned(warn));

        } else {
            // 第 3 次起的清效果 + 重挂 Buff + 音效 + 广播全部纳入全局 30s 惩罚门：
            // 冻结期高频触发时每 30s 最多完整响应一次（counter 方法内部受同一门约束
            // 转为静默），避免反复重复极高频率触发导致的清效果 + 重挂 Buff + 音效广播叠加刷屏。
            if (boss.anticheat.tryAcquirePunishGate()) {
                boss.clearAllExternalEffects();
                boss.reapplySelfBuffs();
                boss.level().playSound(null, boss.blockPosition(),
                    SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.0f, 1.5f);
                MutableComponent bye = Component.translatable("message.silent_sun.redios.no_loot_farewell")
                    .withStyle(ChatFormatting.GOLD);
                boss.broadcastToParticipants(boss.rediosSigned(bye));
            }
            boss.anticheat.counterCheatAttacker(ctx.source.getEntity());
            boss.anticheat.counterAllCheatAttackers((ServerLevel) boss.level());
            // 反作弊后果调整：死亡作弊仅警告，不再退场（bossLeaveNoLoot），
            // 也不直接进入全盛状态（enableNoResurrection / enterNoResurrectionPhase2 不再触发）。
        }

        return DamageResult.cancel();
    }

    // ────────────── Stage 17: Phase 1 lock threshold ──────────────

    private static DamageResult stagePhase1Lock(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        // 一阶段锁血（2026-08-30 用户规范）：
        //   x.9（1.9）濒死锁血：COMBAT 最后头衔 + PENDING 全程生效——最低血量 1、允许 ≥1、
        //     不允许 ≤0；回血/改血到 >1 允许（保底不封顶）。解除 = 1.9 锁血时间结束。
        //   非 x.9 头衔锁血（用户裁决「钳在头衔段底，逐格推进」）：大伤害打到段底以下 →
        //     钳在当前头衔段底 + 立即推进头衔（titleIndex+1 + 重设锁血），不锁 1 血、不跳段，
        //     保证中间头衔逐格走完（BossFlag 逐个授予），且不被一次大伤害直接打死。
        // 2026-09-10 实测修复：挂起窗口统一处理——原实现只认 PHASE1_PENDING，PHASE1_VOTE 与
        // PHASE1_TRANSITION 落进 proceed()（完全不锁血），而实测 1.9 被打死正发生在这两个窗口
        // （前置模组断魂在 Post 里直写血量 → hurt 收尾判定即击杀 → 一阶段未清 → 无掉落不进投票）。
        boolean frozenPhase1 = boss.bossState == BossState.PHASE1_PENDING
            || boss.bossState == BossState.PHASE1_VOTE
            || boss.bossState == BossState.PHASE1_TRANSITION;
        if (frozenPhase1) {
            // 挂起窗口全程锁 1 血：>1 的伤害正常结算（与 PENDING 既有口径一致），任何会把血量
            // 打到 <1 的一律钳 1 + 取消。
            if (boss.getHealth() - ctx.amount < 1.0f) {
                boss.setHealth(1.0f);
                boss.anticheat.markLegalHealthChange(1.0f);
                return DamageResult.cancel();
            }
            return DamageResult.proceed();
        }
        if (!boss.bossState.isPhase1Combat()) {
            return DamageResult.proceed();
        }
        boolean atLastTitle = boss.titleIndex == RediosEntity.PHASE1_TITLES.size() - 1;
        float segment = boss.getMaxHealth() / (float) RediosEntity.PHASE1_TITLES.size();
        // 当前头衔段底（剩余血量下限）：index 段 = [maxHealth-(index+1)*seg, maxHealth-index*seg]
        float low = boss.getMaxHealth() - (float)(boss.titleIndex + 1) * segment;
        if (boss.titleIndex == RediosEntity.PHASE1_TITLES.size() - 1) {
            low = 0.0f;
        }
        if (boss.bossState == BossState.PHASE1_COMBAT && atLastTitle
            && boss.getHealth() - ctx.amount <= 1.0f) {
            // x.9 濒死：锁 1 血进 PENDING。
            boss.setHealth(1.0f);
            boss.anticheat.markLegalHealthChange(1.0f);
            boss.transitionTo(BossState.PHASE1_PENDING);
            boss.enterPendingState();
            return DamageResult.cancel();
        }
        if (boss.bossState == BossState.PHASE1_COMBAT && !atLastTitle
            && boss.getHealth() - ctx.amount < low) {
            // 非 x.9：伤害打穿当前头衔段底 → 钳到段底（血不掉穿段底）。
            // 2026-09-01：推进尊重锁血节奏——原 9afc91d「立即推进」被前置模组 9pass 断魂
            // 高频触发暴露（soul_sever 每 tick 结算一次，无视无敌帧）：每 tick 打穿 → 每 tick
            // 推进 = 快速跳阶段。改钳段底后仅当锁血已归零（titleLockTicks ≤ 0）才 advanceTitleFromDamage，
            // 锁血中只钳不推，锁血到期由 updateTitle/tryForceAdvanceOnLockEnd 自然推进一格
            //（每段至少 15s/30s，符合设计稿 §2.2「每个头衔都有锁血」）。
            boss.setHealth(low);
            boss.anticheat.markLegalHealthChange(low);
            if (boss.titleLockTicks <= 0) {
                boss.advanceTitleFromDamage();
            }
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }

    // ────────────── Stage 18: Phase 2 pending death ──────────────

    private static DamageResult stagePhase2Pending(DamageContext ctx) {
        RediosEntity boss = ctx.boss;
        if (boss.level().isClientSide) {
            return DamageResult.proceed();
        }
        // 二阶段濒死锁血（2026-08-30 用户规范）：同 stagePhase1Lock——
        //   封锁（生效期间）：PHASE2_COMBAT 与 PHASE2_PENDING 全程锁 1 血（保底不封顶）；
        //   解除（失效条件）：phase2.9 头衔锁血时间结束 → pendingLockReleased=true 回 COMBAT
        //                    允许击杀（die 设 CD）。
        //   非 x.9 大伤害（用户裁决「钳在段底，逐格推进」）：钳到当前头衔段底 + 立即推进头衔。
        if (!boss.bossState.isPhase2Combat() && boss.bossState != BossState.PHASE2_PENDING) {
            return DamageResult.proceed();
        }
        boolean atLastTitle = boss.titleIndex == RediosEntity.PHASE2_TITLES.size() - 1;
        float segment = boss.getMaxHealth() / (float) RediosEntity.PHASE2_TITLES.size();
        float low = boss.getMaxHealth() - (float)(boss.titleIndex + 1) * segment;
        if (atLastTitle) {
            low = 0.0f;
        }
        if (boss.bossState.isPhase2Combat() && atLastTitle && !boss.pendingLockReleased
            && boss.getHealth() - ctx.amount <= 1.0f) {
            boss.setHealth(1.0f);
            // 合法推进：同步反作弊基线，避免跨 tick 低血量篡改误判
            boss.anticheat.markLegalHealthChange(1.0f);
            boss.transitionTo(BossState.PHASE2_PENDING);
            boss.enterPendingState();
            return DamageResult.cancel();
        }
        if (boss.bossState == BossState.PHASE2_PENDING && !boss.pendingLockReleased
            && boss.getHealth() - ctx.amount < 1.0f) {
            // pending 唯一目的 = 防止击杀误判（2026-09-04 最终口径）：仅当本次伤害会把血量扣到
            // <1 时钳 1 并取消（防死）；>1 伤害正常结算。pendingLockReleased=true（可击杀）后
            // 不再拦截，伤害可正常致死（die 设 CD）。
            boss.setHealth(1.0f);
            boss.anticheat.markLegalHealthChange(1.0f);
            return DamageResult.cancel();
        }
        if (boss.bossState.isPhase2Combat() && !atLastTitle
            && boss.getHealth() - ctx.amount < low) {
            // 非 x.9：同 stagePhase1Lock——钳段底 + 推进尊重锁血节奏（2026-09-01，
            // 防 9pass 断魂每 tick 打穿 → 每 tick 推进跳阶段）。
            boss.setHealth(low);
            boss.anticheat.markLegalHealthChange(low);
            if (boss.titleLockTicks <= 0) {
                boss.advanceTitleFromDamage();
            }
            return DamageResult.cancel();
        }
        return DamageResult.proceed();
    }
}
