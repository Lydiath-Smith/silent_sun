package com.lydiath.silent_sun.rules;

import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public final class RediosRules {

    // ========== 断光之刻 ==========
    private static volatile TwilightMomentMode twilightMomentMode = TwilightMomentMode.REFRESH;
    // E1: 2.5 断光之刻降难度采用"逐出/视觉改变"二选一，默认视觉改变（不逐出，仅施加环境效果）
    private static volatile TwilightMomentPunishmentMode twilightMomentPunishment = TwilightMomentPunishmentMode.VISUAL;
    private static volatile boolean twilightMomentDebugMessages = false;
    private static volatile int twilightMomentNotifyCooldownTicks = 100;
    private static volatile int twilightMomentTimedGraceTicks = 10;
    private static volatile ResourceLocation twilightMomentApplyEffectId = ResourceLocation.fromNamespaceAndPath("minecraft", "darkness");
    private static volatile List<ResourceLocation> twilightMomentSatisfyEffectIds = List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "darkness"));

    // ========== 伤害调试 ==========
    private static volatile boolean damageSourceDebug = false;
    private static volatile int damageSourceDebugCooldownTicks = 40;
    private static volatile boolean damageSourceDebugOnlyPhase2 = true;
    private static volatile boolean damageSourceDebugOnlyWhenExpelled = true;

    // ========== 伤害与战斗 ==========
    private static volatile double blackSunDefeatRatio = 0.5;
    private static volatile double colorlessReflectRatio = 0.5;
    private static volatile int colorlessWeaknessDurationTicks = 200;
    private static volatile int colorlessWeaknessAmplifier = 0;
    private static volatile int voidAllThingsDarknessDurationTicks = 40;
    private static volatile int voidAllThingsTeleportCooldownTicks = 40;
    private static volatile int uncontrolledSprintExtraHits = 1;
    private static volatile double uncontrolledSprintExtraDamageRatio = 0.35;
    private static volatile int uncontrolledSprintExtraCooldownTicks = 4;
    private static volatile double uncontrolledSprintAoEDodgeChance = 0.25;
    private static volatile boolean chaosRuinIncomingAbsoluteEnabled = true;

    // ========== 环境与辅助 ==========
    private static volatile boolean wallAttackTraceParticles = true;
    private static volatile int wallAttackNotifyCooldownTicks = 60;
    private static volatile boolean locateBossEnabled = true;
    private static volatile int locateBossDistanceBlocks = 35;
    private static volatile int locateBossNotifyIntervalTicks = 200;
    private static volatile boolean heightFlightEnabled = true;
    private static volatile int heightFlightDiffBlocks = 6;
    private static volatile double heightFlightVerticalSpeed = 0.5;

    // ========== 视野缺失 ==========
    private static volatile boolean bossMissingVisionEnabled = true;
    private static volatile int bossMissingVisionTicks = 100;
    private static volatile double bossMissingVisionDotThreshold = 0.45;
    private static volatile BossMissingVisionAction bossMissingVisionAction = BossMissingVisionAction.WAYPOINT;

    // ========== 投票系统 ==========
    private static volatile boolean phase2VoteRequired = true;
    private static volatile int voteTimeoutSeconds = 30;
    private static volatile boolean voteTieAsYes = false;
    private static volatile List<String> phase2VoteYesTokens = List.of("yes", "y", "1", "继续", "是");
    private static volatile List<String> phase2VoteNoTokens = List.of("no", "n", "2", "下次", "否");

    // ========== 自适应格挡 ==========
    private static volatile int adaptiveBlockTriggerHitsPerSecond = 6;
    private static volatile int adaptiveBlockDurationTicks = 20;
    /** 2026-09-10（用户裁决）：「格挡就全免」——该键**已不再被 DamagePipeline 消费**，
     *  保留只是让旧配置文件仍能读入而不报错（1.0 = 旧语义下的"全额免除"）。 */
    private static volatile double adaptiveBlockDamageReduction = 1.0;
    private static volatile int adaptiveBlockCooldownTicks = 40;

    // ========== 战斗区域 ==========
    /** 通用脱战半径（格）：超出后开始计时，持续 {@link #battleExpelTimeoutSeconds} 未返回即判定脱战。
     *  2026-09-10（用户裁决）：默认 32 → **72**，以作者攻略「玩家以脱战方式离场，判定 72 格」为准。
     *  注意与 2.9 的即时逐出半径 {@code RediosEntity.VOID_BATTLE_RANGE_BLOCKS}（64，无宽限）是两个口径：
     *  2.9 期间更严，超出 64 格立即逐出；本值是通用口徑（带 60 秒宽限）。 */
    private static volatile int battleRadiusBlocks = 72;
    private static volatile int battleExpelTimeoutSeconds = 60;

    // ========== 强力推离 ==========
    private static volatile double pushAwayDistance = 17.0;
    private static volatile double pushAwayStrength = 2.0;
    private static volatile double pushAwayRange = 10.0;

    // ========== 方块恢复 ==========
    // 2026-09-10（用户裁决 D6）：白名单是「记录 + 恢复」的单一真源；
    // 补齐 barrier / end_portal_frame（原先被记录却因不在白名单而永久摧毁）。
    private static final List<String> DEFAULT_RESTORED_BLOCKS = List.of(
        "minecraft:bedrock", "minecraft:command_block", "minecraft:chain_command_block",
        "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:jigsaw",
        "minecraft:barrier", "minecraft:end_portal_frame");
    private static volatile List<String> restoredBlocksWhitelist = DEFAULT_RESTORED_BLOCKS;
    private static volatile boolean restoreNbt = true;

    // ========== 卡顿保护 ==========
    // N2: 原名 fpsThreshold 实为"低帧率判定用延迟阈值(毫秒)"，更名 latencyThresholdMs
    // M1: 默认 150ms——避免把普通网络玩家（30-80ms）误判为低帧率
    private static volatile int latencyThresholdMs = 150;
    private static volatile boolean lagProtectionEnabled = true;

    // ========== 成书与文本 ==========
    private static volatile String rediosBookAuthor = "Redios";
    private static volatile String rediosDefeatBookTitle = "谢谢惠顾，下次再来。";
    private static volatile String rediosVictoryBookTitle = "干得漂亮！欢迎再来！";
    private static volatile String rediosNotePhase1WinPhase2Lose = "干的很好了，想与整个世界为敌，光是让世界看你是不行的。\n\n[战斗记录]\n维度: {dimension}\n坐标: {x} {y} {z}\n参战者: {participants}\n用时: {duration_seconds}s";
    private static volatile ResourceLocation rediosOutcomeTextPhase1WinOnlyFile = ResourceLocation.fromNamespaceAndPath("silent_sun", "books/redios/outcome_phase1_win_only.txt");
    private static volatile ResourceLocation rediosOutcomeTextPhase1WinPhase2LoseFile = ResourceLocation.fromNamespaceAndPath("silent_sun", "books/redios/outcome_phase1_win_phase2_lose.txt");
    private static volatile ResourceLocation rediosOutcomeTextPhase2WinFile = ResourceLocation.fromNamespaceAndPath("silent_sun", "books/redios/outcome_phase2_win.txt");

    // ========== 战斗音乐 ==========
    private static volatile boolean rediosBattleMusicEnabled = true;
    private static volatile float rediosBattleMusicVolume = 1.0f;

    // ========== 武器弱点 ==========
    private static volatile boolean weaponWeakpointEnabled = true;
    private static volatile int weaponWeakpointSlowTicks = 100;
    private static volatile int weaponWeakpointCooldownTicks = 300;
    private static volatile int weaponWeakpointFixedCooldown = 20;
    // 振刀弱点窗口期间的额外伤害倍率（>1 表示玩家在该窗口内对 Boss 造成更多伤害）。
    private static volatile double weaponWeakpointDamageMultiplier = 1.5;
    // 振刀弱点窗口期间的护甲穿透比例（0~1）：1.0 表示完全无视 Boss 护甲。
    private static volatile double weaponWeakpointArmorPierce = 0.5;

    // ================================================================
    // Twilight Moment
    // ================================================================
    public static TwilightMomentMode twilightMomentMode() { return twilightMomentMode; }
    public static void setTwilightMomentMode(TwilightMomentMode mode) { twilightMomentMode = mode == null ? TwilightMomentMode.REFRESH : mode; }
    public static TwilightMomentPunishmentMode twilightMomentPunishment() { return twilightMomentPunishment; }
    public static void setTwilightMomentPunishment(TwilightMomentPunishmentMode v) { twilightMomentPunishment = v == null ? TwilightMomentPunishmentMode.VISUAL : v; }
    /** E1: 是否启用逐出惩罚（EXPEL 模式）。 */
    public static boolean twilightMomentExpelMode() { return twilightMomentPunishment == TwilightMomentPunishmentMode.EXPEL; }
    public static boolean twilightMomentDebugMessages() { return twilightMomentDebugMessages; }
    public static void setTwilightMomentDebugMessages(boolean v) { twilightMomentDebugMessages = v; }
    public static int twilightMomentNotifyCooldownTicks() { return twilightMomentNotifyCooldownTicks; }
    public static void setTwilightMomentNotifyCooldownTicks(int v) { twilightMomentNotifyCooldownTicks = Math.max(0, v); }
    public static int twilightMomentTimedGraceTicks() { return twilightMomentTimedGraceTicks; }
    public static void setTwilightMomentTimedGraceTicks(int v) { twilightMomentTimedGraceTicks = Math.max(0, v); }
    public static ResourceLocation twilightMomentApplyEffectId() { return twilightMomentApplyEffectId; }
    public static void setTwilightMomentApplyEffectId(ResourceLocation id) { twilightMomentApplyEffectId = id == null ? ResourceLocation.fromNamespaceAndPath("minecraft", "darkness") : id; }
    public static List<ResourceLocation> twilightMomentSatisfyEffectIds() { return twilightMomentSatisfyEffectIds; }
    public static void setTwilightMomentSatisfyEffectIds(List<ResourceLocation> ids) { twilightMomentSatisfyEffectIds = ids == null || ids.isEmpty() ? List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "darkness")) : List.copyOf(ids); }

    // ================================================================
    // Damage & Combat
    // ================================================================
    public static boolean damageSourceDebug() { return damageSourceDebug; }
    public static void setDamageSourceDebug(boolean v) { damageSourceDebug = v; }
    public static int damageSourceDebugCooldownTicks() { return damageSourceDebugCooldownTicks; }
    public static void setDamageSourceDebugCooldownTicks(int v) { damageSourceDebugCooldownTicks = Math.max(0, v); }
    public static boolean damageSourceDebugOnlyPhase2() { return damageSourceDebugOnlyPhase2; }
    public static void setDamageSourceDebugOnlyPhase2(boolean v) { damageSourceDebugOnlyPhase2 = v; }
    public static boolean damageSourceDebugOnlyWhenExpelled() { return damageSourceDebugOnlyWhenExpelled; }
    public static void setDamageSourceDebugOnlyWhenExpelled(boolean v) { damageSourceDebugOnlyWhenExpelled = v; }
    public static double blackSunDefeatRatio() { return blackSunDefeatRatio; }
    public static void setBlackSunDefeatRatio(double v) { blackSunDefeatRatio = Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.5; }
    public static double colorlessReflectRatio() { return colorlessReflectRatio; }
    public static void setColorlessReflectRatio(double v) { colorlessReflectRatio = Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.5; }
    public static int colorlessWeaknessDurationTicks() { return colorlessWeaknessDurationTicks; }
    public static void setColorlessWeaknessDurationTicks(int v) { colorlessWeaknessDurationTicks = Math.max(0, v); }
    public static int colorlessWeaknessAmplifier() { return colorlessWeaknessAmplifier; }
    public static void setColorlessWeaknessAmplifier(int v) { colorlessWeaknessAmplifier = Math.max(0, v); }
    public static int voidAllThingsDarknessDurationTicks() { return voidAllThingsDarknessDurationTicks; }
    public static void setVoidAllThingsDarknessDurationTicks(int v) { voidAllThingsDarknessDurationTicks = Math.max(0, v); }
    public static int voidAllThingsTeleportCooldownTicks() { return voidAllThingsTeleportCooldownTicks; }
    public static void setVoidAllThingsTeleportCooldownTicks(int v) { voidAllThingsTeleportCooldownTicks = Math.max(1, v); }
    public static int uncontrolledSprintExtraHits() { return uncontrolledSprintExtraHits; }
    public static void setUncontrolledSprintExtraHits(int v) { uncontrolledSprintExtraHits = Math.max(0, v); }
    public static double uncontrolledSprintExtraDamageRatio() { return uncontrolledSprintExtraDamageRatio; }
    public static void setUncontrolledSprintExtraDamageRatio(double v) { uncontrolledSprintExtraDamageRatio = Double.isFinite(v) ? Math.max(0.0, Math.min(5.0, v)) : 0.35; }
    public static int uncontrolledSprintExtraCooldownTicks() { return uncontrolledSprintExtraCooldownTicks; }
    public static void setUncontrolledSprintExtraCooldownTicks(int v) { uncontrolledSprintExtraCooldownTicks = Math.max(0, v); }
    public static double uncontrolledSprintAoEDodgeChance() { return uncontrolledSprintAoEDodgeChance; }
    public static void setUncontrolledSprintAoEDodgeChance(double v) { uncontrolledSprintAoEDodgeChance = Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.25; }
    public static boolean chaosRuinIncomingAbsoluteEnabled() { return chaosRuinIncomingAbsoluteEnabled; }
    public static void setChaosRuinIncomingAbsoluteEnabled(boolean v) { chaosRuinIncomingAbsoluteEnabled = v; }

    // ================================================================
    // Environment & Assist
    // ================================================================
    public static boolean wallAttackTraceParticles() { return wallAttackTraceParticles; }
    public static void setWallAttackTraceParticles(boolean v) { wallAttackTraceParticles = v; }
    public static int wallAttackNotifyCooldownTicks() { return wallAttackNotifyCooldownTicks; }
    public static void setWallAttackNotifyCooldownTicks(int v) { wallAttackNotifyCooldownTicks = Math.max(0, v); }
    public static boolean locateBossEnabled() { return locateBossEnabled; }
    public static void setLocateBossEnabled(boolean v) { locateBossEnabled = v; }
    public static int locateBossDistanceBlocks() { return locateBossDistanceBlocks; }
    public static void setLocateBossDistanceBlocks(int v) { locateBossDistanceBlocks = Math.max(0, v); }
    public static int locateBossNotifyIntervalTicks() { return locateBossNotifyIntervalTicks; }
    public static void setLocateBossNotifyIntervalTicks(int v) { locateBossNotifyIntervalTicks = Math.max(1, v); }
    public static boolean heightFlightEnabled() { return heightFlightEnabled; }
    public static void setHeightFlightEnabled(boolean v) { heightFlightEnabled = v; }
    public static int heightFlightDiffBlocks() { return heightFlightDiffBlocks; }
    public static void setHeightFlightDiffBlocks(int v) { heightFlightDiffBlocks = Math.max(0, v); }
    public static double heightFlightVerticalSpeed() { return heightFlightVerticalSpeed; }
    public static void setHeightFlightVerticalSpeed(double v) { heightFlightVerticalSpeed = Double.isFinite(v) ? Math.max(0.0, Math.min(2.0, v)) : 0.5; }

    // ================================================================
    // Boss Missing Vision
    // ================================================================
    public static boolean bossMissingVisionEnabled() { return bossMissingVisionEnabled; }
    public static void setBossMissingVisionEnabled(boolean v) { bossMissingVisionEnabled = v; }
    public static int bossMissingVisionTicks() { return bossMissingVisionTicks; }
    public static void setBossMissingVisionTicks(int v) { bossMissingVisionTicks = Math.max(1, v); }
    public static double bossMissingVisionDotThreshold() { return bossMissingVisionDotThreshold; }
    public static void setBossMissingVisionDotThreshold(double v) { bossMissingVisionDotThreshold = Double.isFinite(v) ? Math.max(-1.0, Math.min(1.0, v)) : 0.45; }
    public static BossMissingVisionAction bossMissingVisionAction() { return bossMissingVisionAction; }
    public static void setBossMissingVisionAction(BossMissingVisionAction v) { bossMissingVisionAction = v == null ? BossMissingVisionAction.WAYPOINT : v; }

    // ================================================================
    // Vote System
    // ================================================================
    public static boolean phase2VoteRequired() { return phase2VoteRequired; }
    public static void setPhase2VoteRequired(boolean v) { phase2VoteRequired = v; }
    public static int voteTimeoutSeconds() { return voteTimeoutSeconds; }
    public static void setVoteTimeoutSeconds(int v) { voteTimeoutSeconds = Math.max(1, v); }
    public static boolean voteTieAsYes() { return voteTieAsYes; }
    public static void setVoteTieAsYes(boolean v) { voteTieAsYes = v; }
    public static List<String> phase2VoteYesTokens() { return phase2VoteYesTokens; }
    public static void setPhase2VoteYesTokens(List<String> tokens) {
        // N3: null 回退值与静态默认保持一致（多语言 token 不丢失）
        if (tokens == null || tokens.isEmpty()) { phase2VoteYesTokens = List.of("yes", "y", "1", "继续", "是"); return; }
        phase2VoteYesTokens = tokens.stream().filter(s -> s != null && !s.isBlank()).map(s -> s.strip().toLowerCase(Locale.ROOT)).toList();
    }
    public static List<String> phase2VoteNoTokens() { return phase2VoteNoTokens; }
    public static void setPhase2VoteNoTokens(List<String> tokens) {
        // N3: null 回退值与静态默认保持一致
        if (tokens == null || tokens.isEmpty()) { phase2VoteNoTokens = List.of("no", "n", "2", "下次", "否"); return; }
        phase2VoteNoTokens = tokens.stream().filter(s -> s != null && !s.isBlank()).map(s -> s.strip().toLowerCase(Locale.ROOT)).toList();
    }

    // ================================================================
    // Adaptive Block
    // ================================================================
    public static int adaptiveBlockTriggerHitsPerSecond() { return adaptiveBlockTriggerHitsPerSecond; }
    public static void setAdaptiveBlockTriggerHitsPerSecond(int v) { adaptiveBlockTriggerHitsPerSecond = Math.max(0, v); }
    public static int adaptiveBlockDurationTicks() { return adaptiveBlockDurationTicks; }
    public static void setAdaptiveBlockDurationTicks(int v) { adaptiveBlockDurationTicks = Math.max(0, v); }
    public static double adaptiveBlockDamageReduction() { return adaptiveBlockDamageReduction; }
    public static void setAdaptiveBlockDamageReduction(double v) { adaptiveBlockDamageReduction = Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 1.0; }
    public static int adaptiveBlockCooldownTicks() { return adaptiveBlockCooldownTicks; }
    public static void setAdaptiveBlockCooldownTicks(int v) { adaptiveBlockCooldownTicks = Math.max(0, v); }

    // ================================================================
    // Battle Area
    // ================================================================
    public static int battleRadiusBlocks() { return battleRadiusBlocks; }
    public static void setBattleRadiusBlocks(int v) { battleRadiusBlocks = Math.max(0, v); }
    public static int battleExpelTimeoutSeconds() { return battleExpelTimeoutSeconds; }
    public static void setBattleExpelTimeoutSeconds(int v) { battleExpelTimeoutSeconds = Math.max(1, v); }

    // ================================================================
    // Push Away
    // ================================================================
    public static double pushAwayDistance() { return pushAwayDistance; }
    public static void setPushAwayDistance(double v) { pushAwayDistance = Double.isFinite(v) ? Math.max(0.0, v) : 17.0; }
    public static double pushAwayStrength() { return pushAwayStrength; }
    public static void setPushAwayStrength(double v) { pushAwayStrength = Double.isFinite(v) ? Math.max(0.0, v) : 2.0; }
    public static double pushAwayRange() { return pushAwayRange; }
    public static void setPushAwayRange(double v) { pushAwayRange = Double.isFinite(v) ? Math.max(0.0, v) : 10.0; }

    // ================================================================
    // Block Restore
    // ================================================================
    public static List<String> restoredBlocksWhitelist() { return restoredBlocksWhitelist; }
    public static void setRestoredBlocksWhitelist(List<String> v) {
        // N3: null 回退值与静态默认保持一致（2026-09-10：统一引用 DEFAULT_RESTORED_BLOCKS，避免第三处副本漂移）
        restoredBlocksWhitelist = v == null || v.isEmpty() ? DEFAULT_RESTORED_BLOCKS : List.copyOf(v);
    }
    public static boolean restoreNbt() { return restoreNbt; }
    public static void setRestoreNbt(boolean v) { restoreNbt = v; }

    // ================================================================
    // Lag Protection
    // ================================================================
    public static int latencyThresholdMs() { return latencyThresholdMs; }
    public static void setLatencyThresholdMs(int v) { latencyThresholdMs = Math.max(1, v); }
    public static boolean lagProtectionEnabled() { return lagProtectionEnabled; }
    public static void setLagProtectionEnabled(boolean v) { lagProtectionEnabled = v; }

    // ================================================================
    // Book & Text
    // ================================================================
    public static String rediosBookAuthor() { return rediosBookAuthor; }
    public static void setRediosBookAuthor(String v) { rediosBookAuthor = v == null || v.isBlank() ? "Redios" : v.strip(); }
    public static String rediosDefeatBookTitle() { return rediosDefeatBookTitle; }
    public static void setRediosDefeatBookTitle(String v) { rediosDefeatBookTitle = v == null || v.isBlank() ? "谢谢惠顾，下次再来。" : v.strip(); }
    public static String rediosVictoryBookTitle() { return rediosVictoryBookTitle; }
    public static void setRediosVictoryBookTitle(String v) { rediosVictoryBookTitle = v == null || v.isBlank() ? "干得漂亮！欢迎再来！" : v.strip(); }
    public static String rediosNotePhase1WinPhase2Lose() { return rediosNotePhase1WinPhase2Lose; }
    public static void setRediosNotePhase1WinPhase2Lose(String v) {
        // N3: null 回退值与静态默认保持一致（完整版含战斗记录占位符）
        rediosNotePhase1WinPhase2Lose = v == null || v.isBlank()
            ? "干的很好了，想与整个世界为敌，光是让世界看你是不行的。\n\n[战斗记录]\n维度: {dimension}\n坐标: {x} {y} {z}\n参战者: {participants}\n用时: {duration_seconds}s"
            : v;
    }
    public static ResourceLocation rediosOutcomeTextPhase1WinOnlyFile() { return rediosOutcomeTextPhase1WinOnlyFile; }
    public static void setRediosOutcomeTextPhase1WinOnlyFile(ResourceLocation id) { rediosOutcomeTextPhase1WinOnlyFile = id == null ? ResourceLocation.fromNamespaceAndPath("silent_sun", "books/redios/outcome_phase1_win_only.txt") : id; }
    public static ResourceLocation rediosOutcomeTextPhase1WinPhase2LoseFile() { return rediosOutcomeTextPhase1WinPhase2LoseFile; }
    public static void setRediosOutcomeTextPhase1WinPhase2LoseFile(ResourceLocation id) { rediosOutcomeTextPhase1WinPhase2LoseFile = id == null ? ResourceLocation.fromNamespaceAndPath("silent_sun", "books/redios/outcome_phase1_win_phase2_lose.txt") : id; }
    public static ResourceLocation rediosOutcomeTextPhase2WinFile() { return rediosOutcomeTextPhase2WinFile; }
    public static void setRediosOutcomeTextPhase2WinFile(ResourceLocation id) { rediosOutcomeTextPhase2WinFile = id == null ? ResourceLocation.fromNamespaceAndPath("silent_sun", "books/redios/outcome_phase2_win.txt") : id; }

    // ================================================================
    // Battle Music
    // ================================================================
    public static boolean rediosBattleMusicEnabled() { return rediosBattleMusicEnabled; }
    public static void setRediosBattleMusicEnabled(boolean v) { rediosBattleMusicEnabled = v; }
    public static float rediosBattleMusicVolume() { return rediosBattleMusicVolume; }
    public static void setRediosBattleMusicVolume(float v) { if (!Float.isFinite(v)) { rediosBattleMusicVolume = 1.0f; return; } rediosBattleMusicVolume = Math.max(0.0f, Math.min(4.0f, v)); }

    // ================================================================
    // Weapon Weakpoint
    // ================================================================
    public static boolean weaponWeakpointEnabled() { return weaponWeakpointEnabled; }
    public static void setWeaponWeakpointEnabled(boolean v) { weaponWeakpointEnabled = v; }
    public static int weaponWeakpointSlowTicks() { return weaponWeakpointSlowTicks; }
    public static void setWeaponWeakpointSlowTicks(int v) { weaponWeakpointSlowTicks = Math.max(1, v); }
    public static int weaponWeakpointCooldownTicks() { return weaponWeakpointCooldownTicks; }
    public static void setWeaponWeakpointCooldownTicks(int v) { weaponWeakpointCooldownTicks = Math.max(1, v); }
    public static int weaponWeakpointFixedCooldown() { return weaponWeakpointFixedCooldown; }
    public static void setWeaponWeakpointFixedCooldown(int v) { weaponWeakpointFixedCooldown = Math.max(1, v); }
    public static double weaponWeakpointDamageMultiplier() { return weaponWeakpointDamageMultiplier; }
    public static void setWeaponWeakpointDamageMultiplier(double v) { weaponWeakpointDamageMultiplier = Double.isFinite(v) ? Math.max(1.0, Math.min(10.0, v)) : 1.5; }
    public static double weaponWeakpointArmorPierce() { return weaponWeakpointArmorPierce; }
    public static void setWeaponWeakpointArmorPierce(double v) { weaponWeakpointArmorPierce = Double.isFinite(v) ? Math.max(0.0, Math.min(1.0, v)) : 0.5; }

    private RediosRules() {}

    // ================================================================
    // Enums
    // ================================================================
    public enum BossMissingVisionAction { TELEPORT, WAYPOINT }
    public enum TwilightMomentMode { REFRESH, TIMED }
    /** E1: 2.5 断光之刻惩罚方式——EXPEL 逐出战斗 / VISUAL 仅视觉改变（默认）。 */
    public enum TwilightMomentPunishmentMode { EXPEL, VISUAL }
}
