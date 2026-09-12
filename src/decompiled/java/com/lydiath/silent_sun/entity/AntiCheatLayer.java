package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.util.AbsoluteDamageUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.Method;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Independent anti-cheat layer for RediosEntity.
 * <p>
 * Extracts all anti-cheat state fields and detection/punishment logic
 * from RediosEntity into a dedicated class.  The host entity calls
 * {@link #tick(ServerLevel)} each tick from its own tick method.
 * <p>
 * Package-private fields are accessible from DamagePipeline in the same package.
 */
final class AntiCheatLayer {

    private final RediosEntity boss;

    // ── state fields ──

    double expectedMaxHealth = -1.0;
    double lastObservedMaxHealth = -1.0;
    private float expectedHealth = -1.0f;
    /**
     * 距上次 expectedHealth 刷新以来，经 DamagePipeline 结算并实际生效的伤害总量。
     * 用于区分"单 tick 内合法高额伤害（多段连击/爆裂）导致的骤降"与"直接篡改血量"。
     */
    private float damageSinceExpectedHealthRefresh = 0.0f;
    boolean antiCheatNoLoot = false;
    /**
     * 反作弊惩罚窗口截止 tick（2026-09-01 用户裁决）：篡改响应后 5s（100 tick）内，
     * Boss 全攻击无视目标自定义无敌帧（强制命中，无论是谁；异常离场返场也算——
     * 窗口按 Boss 实体记录，被惩罚玩家返场仍生效）。
     */
    int punishUntilTick = 0;
    int deathCheatStrikeCount = 0;
    /** 反破解：被拦截的强制移除/归零/强杀尝试次数（onRemove清除/isdead强死等）。 */
    int removalAttemptCount = 0;
    private int attributeTamperCount = 0;
    int attributeTamperFlagTicks = 0;
    /** 篡改警报广播限频：每 30 秒（600 tick）最多向参战者广播一次"搞么子。"。 */
    private int tamperBroadcastCooldownTicks = 0;
    /**
     * 全局反作弊惩罚门：每 30 秒（600 tick）最多执行一次实际惩罚（物品冷却/警告）。
     * 多条检测路径（血量篡改/死亡作弊/die 拦截/创造模式）共享同一门，
     * 杜绝高爆发输出或多路径检测导致的战斗被反复打断与警告刷屏。
     */
    private int antiCheatPunishGlobalCooldownTicks = 0;
    private int antiHardStunCooldownTicks = 0;
    private int ridePunishCooldownTicks = 0;
    int creativeLeaveTimerTicks = -1;
    /** 创造模式离场窗口：创造/旁观参战者出现后给的 10 分钟切回生存时间（DamagePipeline 与
     *  回场复核共用同一常量，避免两处硬编码漂移）。 */
    static final int CREATIVE_LEAVE_WINDOW_TICKS = 12000;

    // Package-private: accessed by DamagePipeline
    /**
     * 创造归还的防爆上限（2026-09-11 代码审计 G08 #2 修复）。
     * <p>
     * {@code creativeGainedItems} 的 value 来自存档 NBT 的 {@code Count}，而读档时原先只做
     * {@code Math.max(1, ...)}、**无上界**；归还时又按 {@code maxStackSize} 逐组展开
     * （不可堆叠物品 = 每个计数单位一个 {@code ItemStack}）⇒ 存档里写一个巨大 Count，
     * 即可让归还那一 tick 构造海量对象（冻结 / OOM）。
     * <p>
     * 上限取 3456 栈 = 54 格 × 64，远超「创造模式下正常拾取」的合理量级；超出即截断并告警。
     */
    private static final int CREATIVE_RETURN_MAX_COUNT_PER_ENTRY = 3456;
    private static final int CREATIVE_RETURN_MAX_STACKS = 3456;

    final Set<UUID> creativeStrikers = new HashSet<>();
    // G4: 追踪"检测开始(首次攻击) → 切回生存"期间该玩家获得的所有物品。
    // creativeGainedItems 为单调累计净拾取量；creativePrevInventory 为上一 tick
    // 物品栏聚合基线（仅用于计算增量，整理/消耗不会误记）。
    final Map<UUID, Map<StackKey, Integer>> creativeGainedItems = new HashMap<>();
    final Map<UUID, Map<StackKey, Integer>> creativePrevInventory = new HashMap<>();

    // 2026-09-11（代码审计 G08 #3 修复）：原 antiCheatCooldownPlayers 只写不读 —— 唯一「读」是把集合
    // 写进存档，即每存档写一份**无用**的 UUID 列表。字段、3 处 add、读写档块与
    // RediosEntity.removeAntiCheatCooldowns() 已一并删除。

    AntiCheatLayer(RediosEntity boss) {
        this.boss = boss;
    }

    // ── declarative legal-change channel（声明式合法通道）──

    // ── 2026-09-12（战斗流程报告）：反作弊留痕（旁路观测，零行为变更） ──

    /**
     * 记录一次反作弊事件。整个链路自带 null 判定与 try/catch（见 {@code RediosEntity.flowAntiCheat}），
     * 记录失败只 warn，绝不影响战斗；开关关闭时 {@code boss} 侧记录器为 null ⇒ 零开销。
     */
    private void reportAntiCheat(String kind, Entity offender, String punish, boolean gated, String note) {
        this.boss.flowAntiCheat(kind, describeOffender(offender), punish, gated, note);
    }

    /** 涉事实体描述：玩家记 UUID，其余记「实体类型#UUID」（与设计 §2.2 的 offender 口径一致）。 */
    static String describeOffender(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Player player) {
            return "player:" + player.getUUID();
        }
        return entity.getType() + "#" + entity.getUUID();
    }

    /** 全局 30s 惩罚门是否处于压制状态（只读；供 DamagePipeline 判定 gatedBy30s）。 */
    boolean isPunishGateClosed() {
        return this.antiCheatPunishGlobalCooldownTicks > 0;
    }

    /**
     * 声明式合法血量变更通道：Boss 因内部机制（锁血/阶段切换/恢复/重建）合法改变血量后，
     * 调用本方法同步反作弊基线并清零合法伤害累计，避免 tickHealthCheatCheck 误判为篡改。
     * <p>
     * 这是 {@code expectedHealth} 的唯一外部写入入口；外部直接赋值已被移除。
     */
    void markLegalHealthChange(float newExpectedHealth) {
        this.expectedHealth = newExpectedHealth;
        this.damageSinceExpectedHealthRefresh = 0.0f;
    }

    /**
     * 声明式合法伤害累计通道：DamagePipeline / 内部伤害结算实际扣血后累加合法伤害量，
     * 供 tickHealthCheatCheck 区分「合法高额伤害（多段连击/爆裂）」与「血量篡改」。
     */
    void recordLegalDamage(float amount) {
        if (amount > 0.0f) {
            this.damageSinceExpectedHealthRefresh += amount;
        }
    }

    // ── main tick ──

    void tick(ServerLevel serverLevel) {
        tickMaxHealthCheatCheck(serverLevel);
        tickHealthCheatCheck(serverLevel);
        if (this.tamperBroadcastCooldownTicks > 0) {
            --this.tamperBroadcastCooldownTicks;
        }
        if (this.antiCheatPunishGlobalCooldownTicks > 0) {
            --this.antiCheatPunishGlobalCooldownTicks;
        }
        if (this.attributeTamperFlagTicks > 0) {
            --this.attributeTamperFlagTicks;
            if (this.attributeTamperFlagTicks == 0) {
                boss.restoreExpectedMaxHealth();
                this.lastObservedMaxHealth = this.expectedMaxHealth;
            }
        }
        tickMaxHealthIntegrity(serverLevel);
        tickAntiHardStun(serverLevel);
    }

    /** @return true if the creative leave timer just expired (crossed 0 → -1) */
    boolean tickCreativeRelated(ServerLevel serverLevel) {
        if (this.creativeLeaveTimerTicks >= 0) {
            this.creativeLeaveTimerTicks--;
            // G4: 每 tick 先累计物品获得增量，再判定是否切回生存
            for (UUID id : new ArrayList<>(this.creativeStrikers)) {
                ServerPlayer player = boss.getServerPlayer(id);
                if (player != null && player.isAlive()) {
                    this.tickCreativeTracking(id, player);
                }
            }
            tickCreativeToSurvivalCheck(serverLevel);
            // 2026-09-10 实测修复：这个窗口只在「**当下**仍有创造/旁观参战者」时才有意义。
            // 原实现只在玩家切回生存时把他移出 creativeStrikers，却**不撤计时器**——于是计时器
            // 继续跑满 10 分钟，到点时玩家早已是生存模式，Boss 仍被判"创造窗口到期"→ 友好离场 +
            // 无掉落 + 设冷却（实测 P2 掉落就是这样丢的；玩家当时并未处于创造模式）。
            if (!this.hasActiveCreativeStriker()) {
                this.creativeLeaveTimerTicks = -1;
                return false;
            }
            return this.creativeLeaveTimerTicks < 0;
        }
        return false;
    }

    /** 是否仍有"当下处于创造/旁观且在线"的参战者（离线或已切回生存的不算）。 */
    private boolean hasActiveCreativeStriker() {
        for (UUID id : this.creativeStrikers) {
            ServerPlayer player = boss.getServerPlayer(id);
            if (player != null && player.isAlive() && (player.isCreative() || player.isSpectator())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 回场/读档后复核创造离场窗口（2026-09-10 实测修复）。
     * <p>
     * 现场证据：Boss 被第三方模组（寰宇支配之剑的"清除实体"）删除 → 账本在 400 tick 宽限期后
     * 重建回场（`Redios rebuilt from battle record … externally removed`）；但该计时器**只在实体
     * tick 时递减**，Boss 被删除期间冻在原地，回场后第一 tick 即跨过 0 → 刚回来的 Boss 立刻走
     * 「创造模式离场」（友好离场 + 无掉落 + 设冷却，实测量级 48 毫秒）→ 掉落与后续流程全丢。
     * <p>
     * 修复：读档/回场一律不继承残留倒计时，而是按**当下实际状态**重新判定——仍有创造/旁观
     * 参战者则重新给满窗口（期间每次命中照常续），否则关掉计时器，交给常规脱战/卸载判定。
     */
    void rearmCreativeLeaveAfterRestore() {
        this.creativeLeaveTimerTicks = this.hasActiveCreativeStriker() ? CREATIVE_LEAVE_WINDOW_TICKS : -1;
    }

    void tickRidePunish(ServerLevel serverLevel) {
        if (this.ridePunishCooldownTicks > 0) {
            --this.ridePunishCooldownTicks;
            return;
        }
        ArrayList<LivingEntity> offenders = new ArrayList<>();
        if (boss.isPassenger()) {
            Entity vehicle = boss.getVehicle();
            boss.stopRiding();
            if (vehicle instanceof LivingEntity living) {
                if (!(living instanceof Player p) || (!p.isCreative() && !p.isSpectator())) {
                    offenders.add(living);
                }
            }
        } else if (boss.isVehicle()) {
            ArrayList<Entity> passengers = new ArrayList<>(boss.getPassengers());
            boss.ejectPassengers();
            for (Entity passenger : passengers) {
                if (passenger instanceof LivingEntity living) {
                    if (living instanceof Player p && (p.isCreative() || p.isSpectator())) {
                        continue;
                    }
                    offenders.add(living);
                }
            }
        }
        offenders.removeIf(o -> !o.isAlive() || o.level() != boss.level());
        if (offenders.isEmpty()) {
            return;
        }
        this.ridePunishCooldownTicks = RediosEntity.RIDE_PUNISH_COOLDOWN_TICKS;
        // 2026-09-12（作者确认：有意设计，勿当缺陷改）：骑乘惩罚就是「一击必杀」——
        // 按 ATTACK_DAMAGE 全额走**绝对真伤**（绕过护甲/减伤/无敌帧），基础 30、满激怒 120，
        // 而玩家满血仅 20 点 ⇒ 骑上 Boss 必死。设计稿 §3.4 已将其列为「骑乘惩罚」，
        // **不要**改成按比例伤害或击退警告。
        float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity offender : offenders) {
            // 2026-09-12（战斗流程报告）：骑乘惩罚留痕（一击必杀为作者确认的有意设计，报告仅用于说明时间线）。
            this.reportAntiCheat("RIDE_PUNISH", offender, "absoluteDamage(" + damage + ")+soulSever",
                false, "有实体骑乘 Boss（一击必杀，作者确认为有意设计）");
            boss.addSoulSeverY(50L);
            offender.hurtTime = 0;
            offender.hurtDuration = 0;
            try {
                offender.invulnerableTime = 0;
            } catch (Throwable ignored) {
            }
            AbsoluteDamageUtil.damage(offender, boss.damageSources().mobAttack(boss), damage);
            boss.applySoulSeverToTarget(offender);
        }
    }

    // ── max health tamper detection ──

    private void tickMaxHealthCheatCheck(ServerLevel serverLevel) {
        if (!boss.phaseMaxHealthApplied || this.expectedMaxHealth <= 0.0) {
            return;
        }
        double current = boss.getMaxHealth();
        if (!Double.isFinite(current) || current <= 0.0) {
            // 上限被写成 NaN/负值/0 → 立即恢复期望上限，防"血量上限锁死/归零"。
            boss.restoreExpectedMaxHealth();
            this.lastObservedMaxHealth = this.expectedMaxHealth;
            this.attributeTamperCount++;
            if (this.attributeTamperCount > 9999) this.attributeTamperCount = 9999;
            if (this.tamperBroadcastCooldownTicks <= 0) {
                MutableComponent msg = Component.translatable("message.silent_sun.redios.anticheat.tamper").withStyle(ChatFormatting.RED);
                boss.broadcastToParticipants(boss.rediosSigned(msg));
                this.tamperBroadcastCooldownTicks = 600;
            }
            respondToTamper("MAX_HEALTH_TAMPER");
            return;
        }
        if (this.lastObservedMaxHealth <= 0.0) {
            this.lastObservedMaxHealth = current;
            return;
        }
        // 上限相对观测基线：压低超过 0.001 或抬高超过 0.5 均视为篡改。
        // 合法阶段切换会经 applyPhaseMaxHealth 同步重设 lastObservedMaxHealth，此处不会误判。
        if (current + 0.001 >= this.lastObservedMaxHealth && current <= this.lastObservedMaxHealth + 0.5) {
            this.lastObservedMaxHealth = current;
            return;
        }
        this.attributeTamperCount++;
        if (this.attributeTamperCount > 9999) this.attributeTamperCount = 9999;
        // 警报广播限频：每 30 秒最多响应一次，防刷屏
        // TODO(审计清理 G08 #6)：限频/钳位块全项目 8 份逐字重复（上限校验、反硬直各两套并行实现），此处为最典型的一份 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
        if (this.tamperBroadcastCooldownTicks <= 0) {
            MutableComponent msg = Component.translatable("message.silent_sun.redios.anticheat.tamper").withStyle(ChatFormatting.RED);
            boss.broadcastToParticipants(boss.rediosSigned(msg));
            this.tamperBroadcastCooldownTicks = 600;
        }
        // 立即恢复属性上限，不膨胀（避免污染客户端血条与内部逻辑）
        boss.restoreExpectedMaxHealth();
        // 重响应动作（音效/清效果/重挂 Buff）纳入全局 30s 惩罚门：
        // 连续篡改（如每 tick 写血量上限）时仅恢复与计数每 tick 执行，其余每 30s 一次。
        respondToTamper("MAX_HEALTH_TAMPER");
        // 全盛状态暂时无途径触发：血量上限篡改仅警告，不再解锁全盛
    }

    // ── health tamper detection ──

    private void tickHealthCheatCheck(ServerLevel serverLevel) {
        if (!boss.phaseMaxHealthApplied) {
            return;
        }
        float current = boss.getHealth();
        if (!Float.isFinite(current) || current < 0.0f) {
            // 反射/注入绕过 setHealth 覆写把血量写成 NaN/负值 → 恢复期望血量，防永久杀不死/卡死。
            boss.restoreExpectedMaxHealth();
            float restore = this.expectedHealth > 0.0f ? this.expectedHealth : Math.max(1.0f, boss.getMaxHealth());
            boss.setHealth(restore);
            this.attributeTamperCount++;
            if (this.attributeTamperCount > 9999) this.attributeTamperCount = 9999;
            if (this.tamperBroadcastCooldownTicks <= 0) {
                MutableComponent msg = Component.translatable("message.silent_sun.redios.anticheat.tamper").withStyle(ChatFormatting.RED);
                boss.broadcastToParticipants(boss.rediosSigned(msg));
                this.tamperBroadcastCooldownTicks = 600;
            }
            respondToTamper("HEALTH_TAMPER");
            return;
        }
        if (this.expectedHealth < 0.0f) {
            this.expectedHealth = current;
            return;
        }
        boolean tampered = false;
        float restoreTo = this.expectedHealth;
        float maxHealth = boss.getMaxHealth();
        if (current > maxHealth + 0.5f) {
            restoreTo = maxHealth;
            tampered = true;
        } else if (current > this.expectedHealth + 0.5f) {
            tampered = true;
        } else if (current < this.expectedHealth - 0.5f) {
            // 反破解补充：攻击者反射覆写同步血量字段（绕过 setHealth）把血量压低时，
            // 任意阶段（P1/P2）的骤降都应被检测，而非仅限打穿到 ≤5 的近死场景。
            // 单 tick 内多段合法伤害（如 soul_sever 爆裂、高倍率 SA）也可能打穿血量，
            // 只有实际掉血量明显超过已结算的合法伤害总量时才判定为血量篡改。
            // 容差为 max(10, 期望血量 5%)：高血量阶段允许更大的单 tick 合法落差，
            // 减少拔刀剑 SA/爆裂连击被误判为篡改而触发反作弊惩罚。
            float legitDrop = this.expectedHealth - current;
            float allowance = Math.max(10.0f, this.expectedHealth * 0.05f);
            if (legitDrop > this.damageSinceExpectedHealthRefresh + allowance) {
                tampered = true;
            }
        }
        if (!tampered) {
            return;
        }
        boss.setHealth(restoreTo);
        this.attributeTamperCount++;
        if (this.attributeTamperCount > 9999) this.attributeTamperCount = 9999;
        // 警报广播限频：每 30 秒最多响应一次，防刷屏
        if (this.tamperBroadcastCooldownTicks <= 0) {
            MutableComponent msg = Component.translatable("message.silent_sun.redios.anticheat.tamper").withStyle(ChatFormatting.RED);
            boss.broadcastToParticipants(boss.rediosSigned(msg));
            this.tamperBroadcastCooldownTicks = 600;
        }
        boss.restoreExpectedMaxHealth();
        // 重响应动作（音效/清效果/重挂 Buff）纳入全局 30s 惩罚门：
        // 连续篡改（如每 tick 写血量）时仅恢复与计数每 tick 执行，其余每 30s 一次。
        respondToTamper("HEALTH_TAMPER");
        // 全盛状态暂时无途径触发：血量篡改仅警告，不再解锁全盛
    }

    // ── max health integrity guard（防锁死兜底） ──

    /**
     * 篡改重响应（限频）：音效 + 清外部效果 + 重挂自身 Buff，
     * 全部纳入全局 30s 惩罚门（与篡改广播限频 600 tick 双重防刷屏）。
     * <p>
     * 高频篡改（如客户端每 tick 写血量/上限）时，恢复与计数每 tick 执行，
     * 本方法最多每 30s 触发一次，避免反复重复极高频率触发导致的表现抖动与刷屏。
     */
    private void respondToTamper(String tamperKind) {
        // 2026-09-12（战斗流程报告）：篡改响应留痕——统一出口记一条即可覆盖血量/血量上限两类
        // （kind 由调用方带入）；gatedBy30s=true 表示本条被全局 30s 惩罚门压制、实际未执行惩罚。
        this.reportAntiCheat(tamperKind, null,
            this.antiCheatPunishGlobalCooldownTicks > 0 ? "none" : "wardenSound+clearExternalEffects+reapplySelfBuffs",
            this.antiCheatPunishGlobalCooldownTicks > 0, "血量 / 血量上限被外部改写");
        if (this.antiCheatPunishGlobalCooldownTicks > 0) {
            return;
        }
        this.antiCheatPunishGlobalCooldownTicks = 600;
        this.attributeTamperFlagTicks = 60;
        // 惩罚窗口：反作弊生效后 5s 内全攻击重置目标自定义无敌帧（用户裁决）
        // TODO(审计清理 G08 #4)：5s（100 tick）生效窗口只在此血量篡改路径打开，另三条惩罚路径仅占 30s 门 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
        this.punishUntilTick = boss.tickCount + 100;
        boss.level().playSound(null, boss.blockPosition(),
            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.0f, 1.0f);
        boss.clearAllExternalEffects();
        boss.reapplySelfBuffs();
    }

    /**
     * 惩罚窗口是否生效：反作弊响应后 5s（100 tick）内返回 true，
     * Boss 全攻击应重置目标自定义无敌帧（用户裁决：异常离场返场也算）。
     */
    boolean isPunishWindowActive(int nowTick) {
        return nowTick < this.punishUntilTick;
    }

    /**
     * 尝试占用全局 30s 惩罚门（供 DamagePipeline 等外部同步限频）。
     * 成功返回 true（此前窗口内未响应过），失败表示窗口内已响应过，调用方应静默。
     */
    boolean tryAcquirePunishGate() {
        if (this.antiCheatPunishGlobalCooldownTicks > 0) {
            return false;
        }
        this.antiCheatPunishGlobalCooldownTicks = 600;
        return true;
    }

    /**
     * 每 tick 校验真实属性上限与期望值一致。
     * <p>
     * 无论篡改来自何种路径（外部模组 / NBT 编辑器 / 其他 Boss 机制），
     * 只要属性被压离期望值即立即恢复，保证"血量上限永不锁死"。
     * 反作弊过渡窗口（attributeTamperFlagTicks &gt; 0）内跳过，
     * 由窗口结束时的 restoreExpectedMaxHealth() 统一恢复。
     */
    private void tickMaxHealthIntegrity(ServerLevel serverLevel) {
        if (!boss.phaseMaxHealthApplied || this.expectedMaxHealth <= 0.0) {
            return;
        }
        if (this.attributeTamperFlagTicks > 0) {
            return; // 过渡窗口内由窗口结束逻辑恢复
        }
        double real = boss.getMaxHealth();
        if (!Double.isFinite(real) || real <= 0.0 || Math.abs(real - this.expectedMaxHealth) > 0.5) {
            // NaN/负值/0 或偏离期望 → 立即恢复期望上限（NaN/0 无法用差值判断，需单独兜底）。
            boss.restoreExpectedMaxHealth();
            this.lastObservedMaxHealth = this.expectedMaxHealth;
        }
    }

    // ── anti hard stun ──

    private void tickAntiHardStun(ServerLevel serverLevel) {
        if (boss.bossState.isVoteOrTransition()) {
            return;
        }
        if (this.antiHardStunCooldownTicks > 0) {
            --this.antiHardStunCooldownTicks;
            return;
        }
        boolean recovered = false;
        if (boss.isNoAi()) {
            boss.setNoAi(false);
            recovered = true;
        }
        boss.goalSelector.enableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
        boss.goalSelector.enableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK);
        boss.goalSelector.enableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP);
        boss.targetSelector.enableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET);
        if (recovered) {
            this.antiHardStunCooldownTicks = 4;
            LivingEntity target = boss.getTarget();
            if (target != null && target.isAlive()) {
                boss.getNavigation().moveTo(target, 1.0);
            }
        }
    }

    // ── creative → survival check ──

    void tickCreativeToSurvivalCheck(ServerLevel serverLevel) {
        for (UUID id : new HashSet<>(this.creativeStrikers)) {
            ServerPlayer player = boss.getServerPlayer(id);
            if (player == null || !player.isAlive()) continue;
            if (!player.isCreative() && !player.isSpectator()) {
                this.creativeStrikers.remove(id);
                boss.expelledPlayers.add(id);
                MutableComponent msg = Component.translatable("message.silent_sun.redios.anticheat.returned_items").withStyle(ChatFormatting.GOLD);
                player.sendSystemMessage(boss.rediosSigned(msg));
                Map<StackKey, Integer> gained = this.creativeGainedItems.remove(id);
                this.creativePrevInventory.remove(id);
                List<ItemStack> items = new ArrayList<>();
                // G4（2026-09-11 修复 C03）：按检测期记录的**实际净增量**归还，而不是"每种给满一组"。
                // gained 的 value 是 tickCreativeTracking 单调累计的净拾取数量，原实现用
                // getMaxStackSize() 丢弃了该数量 → 拿 1 个钻石也返 64 个（数量越多越离谱）。
                if (gained != null) {
                    for (Map.Entry<StackKey, Integer> e : gained.entrySet()) {
                        int remaining = Math.max(0, e.getValue());
                        int maxStack = Math.max(1, new ItemStack(e.getKey().item).getMaxStackSize());
                        while (remaining > 0) {
                            // 2026-09-11（代码审计 G08 #2 修复）：总栈数封顶。不可堆叠物品
                            // （maxStack == 1）每个计数单位都要构造一个 ItemStack，存档被改过时
                            // 这里就是 OOM / 单 tick 冻结的入口。
                            if (items.size() >= CREATIVE_RETURN_MAX_STACKS) {
                                CURIOS_LOG.warn("创造归还物品栈数达到上限 {}，本次截断：player={}",
                                    CREATIVE_RETURN_MAX_STACKS, id);
                                break;
                            }
                            ItemStack stack = new ItemStack(e.getKey().item, Math.min(remaining, maxStack));
                            if (e.getKey().tag != null) {
                                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(e.getKey().tag));
                            }
                            items.add(stack);
                            remaining -= stack.getCount();
                        }
                        if (items.size() >= CREATIVE_RETURN_MAX_STACKS) break;
                    }
                }
                // 2026-08-12：期间没拿任何东西 → 不给予任何物品（删除原 D2 fallback
                // 一阶段战利品，避免零拾取白拿奖励；items 为空则下方循环自然跳过）
                for (ItemStack stack : items) {
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
            }
        }
    }

    // ── G4: creative gained-item tracking ──
    // StackKey 已提升为顶层类（2026-09-01）：Connector 转换 jar 丢失嵌套类 nest 信息，
    // 原内部类 AntiCheatLayer$StackKey 会 ClassNotFoundException（创造玩家攻击 Boss 崩溃）。

    private static void addStackToAggregate(Map<StackKey, Integer> agg, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        agg.merge(new StackKey(stack), stack.getCount(), Integer::sum);
    }

    private static Map<StackKey, Integer> aggregateInventory(ServerPlayer player) {
        Map<StackKey, Integer> agg = new HashMap<>();
        for (ItemStack stack : player.getInventory().items) {
            addStackToAggregate(agg, stack);
        }
        for (ItemStack stack : player.getInventory().armor) {
            addStackToAggregate(agg, stack);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            addStackToAggregate(agg, stack);
        }
        return agg;
    }

    /** G4: 首次攻击进入检测时初始化追踪（记录物品栏基线）。 */
    void beginCreativeTracking(UUID id, ServerPlayer player) {
        this.creativeGainedItems.put(id, new HashMap<>());
        this.creativePrevInventory.put(id, aggregateInventory(player));
    }

    /** G4: 每 tick 对比物品栏净增量，单调累计到"获得清单"。 */
    private void tickCreativeTracking(UUID id, ServerPlayer player) {
        Map<StackKey, Integer> gained = this.creativeGainedItems.get(id);
        Map<StackKey, Integer> prev = this.creativePrevInventory.get(id);
        if (gained == null) return;
        if (prev == null) {
            // 区块卸载/重载后仅恢复了 gained，缺失物品栏基线：以当前物品栏重新建立基线。
            // 卸载期间的增量无法追溯，但已累计的 gained 仍可在切回生存时归还。
            this.creativePrevInventory.put(id, aggregateInventory(player));
            return;
        }
        Map<StackKey, Integer> cur = aggregateInventory(player);
        for (Map.Entry<StackKey, Integer> e : cur.entrySet()) {
            int delta = e.getValue() - prev.getOrDefault(e.getKey(), 0);
            if (delta > 0) {
                gained.merge(e.getKey(), delta, Integer::sum);
            }
        }
        prev.clear();
        prev.putAll(cur);
    }

    // ── counter-attack helpers ──

    // Curios 饰品栏冷却反射缓存（可选联动，不硬依赖 Curios；反作弊触发低频，无需 TTL）
    private static final Logger CURIOS_LOG = LoggerFactory.getLogger("SilentSun:AntiCheat");
    private static volatile boolean curiosReflectionTried = false;
    private static volatile boolean curiosReflectionOk = false;
    private static volatile Method curiosGetInventoryMethod;
    private static volatile Method curiosHandlerGetCuriosMethod;
    private static volatile Method stacksGetStacksMethod;
    private static volatile Method dynamicGetSlotsMethod;
    private static volatile Method dynamicGetStackInSlotMethod;

    private static void ensureCuriosReflection() {
        if (curiosReflectionTried) return;
        curiosReflectionTried = true;
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            curiosGetInventoryMethod = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Class<?> handlerClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            curiosHandlerGetCuriosMethod = handlerClass.getMethod("getCurios");
            Class<?> stacksClass = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            stacksGetStacksMethod = stacksClass.getMethod("getStacks");
            Class<?> dynamicClass = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            dynamicGetSlotsMethod = dynamicClass.getMethod("getSlots");
            dynamicGetStackInSlotMethod = dynamicClass.getMethod("getStackInSlot", int.class);
            curiosReflectionOk = true;
        } catch (Exception e) {
            // Curios 未安装 / API 不匹配 → 静默降级（可选联动），仅记一条 WARN 便于排查
            CURIOS_LOG.warn("Curios API unavailable, skipping curios cooldown extension: {}", e.toString());
        }
    }

    /**
     * 将物品冷却延伸到 Curios 饰品栏（挂坠/戒指/腰带等）。
     * <p>
     * 反射调用链：CuriosApi.getCuriosInventory(player) → Optional<ICuriosItemHandler>
     * → getCurios() → Map<slotId, ICurioStacksHandler> → getStacks()
     * → IDynamicStackHandler（getSlots / getStackInSlot），对每个非空饰品槽施加同款冷却。
     * 失败不影响主惩罚流程。
     */
    private static void applyCuriosCooldowns(Player player, int cooldownTicks) {
        ensureCuriosReflection();
        if (!curiosReflectionOk) return;
        try {
            Object invOpt = curiosGetInventoryMethod.invoke(null, player);
            if (!(invOpt instanceof Optional<?> opt) || opt.isEmpty()) return;
            Object handler = opt.get();
            Object curiosMap = curiosHandlerGetCuriosMethod.invoke(handler);
            if (!(curiosMap instanceof Map<?, ?> map)) return;
            for (Object stacksHandler : map.values()) {
                Object stacks = stacksGetStacksMethod.invoke(stacksHandler);
                int slots = (Integer) dynamicGetSlotsMethod.invoke(stacks);
                for (int i = 0; i < slots; i++) {
                    Object stackObj = dynamicGetStackInSlotMethod.invoke(stacks, i);
                    if (stackObj instanceof ItemStack stack && !stack.isEmpty()) {
                        player.getCooldowns().addCooldown(stack.getItem(), cooldownTicks);
                    }
                }
            }
        } catch (Exception e) {
            CURIOS_LOG.warn("Failed to apply curios cooldowns: {}", e.toString());
        }
    }

    /**
     * 对单个玩家施加"本人 2s 物品栏 + Curios 饰品栏强制冷却"。
     * 抽成公共方法供单点惩罚与反流放全体惩罚复用。
     */
    private void applyInventoryAndCuriosCooldowns(Player player, int cooldownTicks) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                player.getCooldowns().addCooldown(stack.getItem(), cooldownTicks);
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                player.getCooldowns().addCooldown(stack.getItem(), cooldownTicks);
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty()) {
                player.getCooldowns().addCooldown(stack.getItem(), cooldownTicks);
            }
        }
        // Curios 饰品栏同款冷却（可选联动：未装 Curios 时反射失败自动跳过）
        applyCuriosCooldowns(player, cooldownTicks);
    }

    /**
     * Punish a single cheater entity.
     * <p>
     * 仅对明确的单个作弊者（如创造模式攻击者）施加"本人 2s 物品冷却 + 警告"。
     * 受全局 30s 惩罚门约束：窗口内后续触发一律静默，避免战斗被反复打断。
     */
    void counterCheatAttacker(Entity attacker) {
        if (BossTargeting.isCheatImmune(attacker)) {
            return; // 数据包黑名单：此类实体不触发反作弊惩罚
        }
        // 2026-09-12（战斗流程报告）：惩罚落地留痕（gatedBy30s=true ⇒ 被全局门压制、实际未处罚）。
        boolean gated = this.antiCheatPunishGlobalCooldownTicks > 0;
        this.reportAntiCheat("TAMPER_PUNISH", attacker,
            gated ? "none" : (attacker instanceof Player ? "itemCooldown(40)+warn" : "none"), gated,
            "单点作弊者反制（创造模式攻击者 / 死亡作弊）");
        if (this.antiCheatPunishGlobalCooldownTicks > 0) {
            return; // 全局 30s 惩罚门：窗口内不再重复惩罚
        }
        this.antiCheatPunishGlobalCooldownTicks = 600;
        if (attacker instanceof Player player) {
            // 物品强制冷却 2 秒（40 tick）
            this.applyInventoryAndCuriosCooldowns(player, 40);
            MutableComponent tip = Component.translatable("message.silent_sun.redios.anticheat.punish").withStyle(ChatFormatting.DARK_RED);
            player.sendSystemMessage(boss.rediosSigned(tip));
        }
    }

    /**
     * 全体连坐降级：向参战者广播一次警告（受全局 30s 惩罚门约束）。
     * <p>
     * 默认不对全体参战者施加物品冷却，避免 Boss 机制触发时连坐全队影响战斗体验；
     * 反流放（离场重归）等明确作弊场景通过 {@code applyCooldowns=true} 追加 2s 物品栏
     * 与 Curios 饰品栏强制冷却。
     */
    void counterAllCheatAttackers(ServerLevel serverLevel) {
        this.counterAllCheatAttackers(serverLevel, false);
    }

    void counterAllCheatAttackers(ServerLevel serverLevel, boolean applyCooldowns) {
        // 2026-09-12（战斗流程报告）：全体连坐降级留痕（单参版本转调本方法，故只在此处记一条；
        // gatedBy30s=true ⇒ 被全局门压制、实际未警告/未冷却）。
        boolean gated = this.antiCheatPunishGlobalCooldownTicks > 0;
        this.reportAntiCheat("TAMPER_PUNISH", null,
            gated ? "none" : (applyCooldowns ? "broadcast+全员itemCooldown(40)" : "broadcast"), gated,
            "全体连坐降级（applyCooldowns=" + applyCooldowns + "）");
        if (this.antiCheatPunishGlobalCooldownTicks > 0) {
            return; // 全局 30s 惩罚门：窗口内不再重复警告
        }
        this.antiCheatPunishGlobalCooldownTicks = 600;
        if (applyCooldowns) {
            int cooldownTicks = 40;
            for (UUID id : new HashSet<>(boss.battleParticipants)) {
                ServerPlayer player = boss.getServerPlayer(id);
                if (player != null) {
                    this.applyInventoryAndCuriosCooldowns(player, cooldownTicks);
                }
            }
            for (UUID id : new HashSet<>(boss.expelledPlayers)) {
                ServerPlayer player = boss.getServerPlayer(id);
                if (player != null) {
                    this.applyInventoryAndCuriosCooldowns(player, cooldownTicks);
                }
            }
        }
        MutableComponent tip = Component.translatable("message.silent_sun.redios.anticheat.punish").withStyle(ChatFormatting.DARK_RED);
        boss.broadcastToParticipants(boss.rediosSigned(tip));
    }

    // ── NBT persistence ──

    /** 序列化创造模式追踪期间累计获得的物品清单（G4），跨区块卸载/重载保留。 */
    private void writeCreativeGainedItems(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<StackKey, Integer>> playerEntry : this.creativeGainedItems.entrySet()) {
            CompoundTag pe = new CompoundTag();
            pe.putUUID("Id", playerEntry.getKey());
            ListTag items = new ListTag();
            for (Map.Entry<StackKey, Integer> e : playerEntry.getValue().entrySet()) {
                CompoundTag ie = new CompoundTag();
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(e.getKey().item);
                ie.putString("Item", key == null ? "minecraft:air" : key.toString());
                ie.putInt("Count", e.getValue());
                if (e.getKey().tag != null) {
                    ie.put("Tag", e.getKey().tag);
                }
                items.add(ie);
            }
            pe.put("Items", items);
            list.add(pe);
        }
        tag.put("SilentSunCreativeGainedItems", list);
    }

    private void readCreativeGainedItems(CompoundTag tag) {
        this.creativeGainedItems.clear();
        ListTag list = tag.getList("SilentSunCreativeGainedItems", 10);
        for (int pi = 0; pi < list.size(); pi++) {
            CompoundTag pe = list.getCompound(pi);
            if (!pe.hasUUID("Id")) continue;
            UUID id = pe.getUUID("Id");
            Map<StackKey, Integer> gained = new HashMap<>();
            ListTag items = pe.getList("Items", 10);
            for (int ii = 0; ii < items.size(); ii++) {
                CompoundTag ie = items.getCompound(ii);
                ResourceLocation rl = ResourceLocation.tryParse(ie.getString("Item"));
                // TODO(审计清理 G08 #5)：其一——不可达死分支（DefaultedRegistry 契约保证不返回 null），删除安全但掩盖「卸载物品被记为 minecraft:air」的语义缺口，建议改 Items.AIR 判据 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
                if (rl == null) continue;
                Item item = BuiltInRegistries.ITEM.get(rl);
                // TODO(审计清理 G08 #5)：其二——不可达死分支（DefaultedRegistry 契约保证不返回 null），删除安全但掩盖「卸载物品被记为 minecraft:air」的语义缺口，建议改 Items.AIR 判据 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
                if (item == null) continue;
                CompoundTag customTag = ie.contains("Tag", 10) ? ie.getCompound("Tag") : null;
                // 2026-09-11（代码审计 G08 #2 修复）：单条目计数封顶，防止存档里的巨大 Count
                // 在归还那一 tick 被逐组展开成海量 ItemStack。
                int rawCount = ie.getInt("Count");
                int count = Math.max(1, Math.min(CREATIVE_RETURN_MAX_COUNT_PER_ENTRY, rawCount));
                if (rawCount > CREATIVE_RETURN_MAX_COUNT_PER_ENTRY) {
                    CURIOS_LOG.warn("创造归还清单条目 Count 超上限，已截断：item={} raw={} cap={}",
                        ie.getString("Item"), rawCount, CREATIVE_RETURN_MAX_COUNT_PER_ENTRY);
                }
                gained.put(new StackKey(item, customTag), count);
            }
            // 即使清单为空也保留条目，使 tickCreativeTracking 能据此重建物品栏基线继续追踪。
            this.creativeGainedItems.put(id, gained);
        }
    }

    void readAdditionalSaveData(CompoundTag tag) {
        this.deathCheatStrikeCount = tag.getInt("SilentSunDeathCheatStrikes");
        this.attributeTamperCount = tag.getInt("SilentSunAttributeTamperCount");
        this.antiCheatNoLoot = tag.getBoolean("SilentSunAntiCheatNoLoot");
        this.removalAttemptCount = tag.getInt("SilentSunRemovalAttempts");
        this.creativeLeaveTimerTicks = tag.getInt("SilentSunCreativeLeaveTimer");

        this.creativeStrikers.clear();
        ListTag creativeList = tag.getList("SilentSunCreativeStrikers", 10);
        for (int ci = 0; ci < creativeList.size(); ci++) {
            CompoundTag entry = creativeList.getCompound(ci);
            if (entry.hasUUID("Id")) {
                this.creativeStrikers.add(entry.getUUID("Id"));
            }
        }

        this.readCreativeGainedItems(tag);
    }

    void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("SilentSunDeathCheatStrikes", this.deathCheatStrikeCount);
        tag.putInt("SilentSunAttributeTamperCount", this.attributeTamperCount);
        tag.putBoolean("SilentSunAntiCheatNoLoot", this.antiCheatNoLoot);
        tag.putInt("SilentSunRemovalAttempts", this.removalAttemptCount);
        tag.putInt("SilentSunCreativeLeaveTimer", this.creativeLeaveTimerTicks);

        ListTag creativeSaveList = new ListTag();
        for (UUID id : this.creativeStrikers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            creativeSaveList.add(entry);
        }
        tag.put("SilentSunCreativeStrikers", creativeSaveList);

        this.writeCreativeGainedItems(tag);
    }
}
