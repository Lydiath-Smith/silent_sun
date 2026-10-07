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
    // 默认 600 tick（30 秒）：隔墙攻击/投掷约每 3-4 秒命中一次，旧值 60 tick 会导致几乎每次命中都刷同一句台词。
    private static volatile int wallAttackNotifyCooldownTicks = 600;
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
    // 2026-09-18：voteTimeoutSeconds / voteTieAsYes 两字段删除——行为早已硬编码
    // （投票时长固定 600t、平局按否决，见 RediosEntity 的投票流程），配置键零消费。
    private static volatile List<String> phase2VoteYesTokens = List.of("yes", "y", "1", "继续", "是");
    private static volatile List<String> phase2VoteNoTokens = List.of("no", "n", "2", "下次", "否");

    // ========== 自适应格挡 ==========
    // 2026-09-14（体检 P1-2 核实，**结论更正**）：本套默认值确实在「字段初值 / setter 回退 / reload 缺失块」
    // 三处各写一遍（实测 70 / 19 / 62 项），但**对账结果为 0 漂移** —— 即"漂移风险"当前不成立
    //（历史漂移过一次，证据是 RediosRulesReloadListener 的「N3」注释，已修）。
    // ⇒ 作者裁决**不做** 230 处常量抽取（纯机械改动，收益只是"防未来漂移"），改为**机械化对账**兜底：
    //     powershell -ExecutionPolicy Bypass -File _规则\rules_defaults_check.ps1
    //   改配置键后跑一次，三处不一致即报（退出码 1）。详见 docs\实现计划-P1-2默认值同源-2026-09-14.md
    // 原 TODO(审计清理 G02 #3) 已由本次核实闭环 —— **不要再重做一遍核实**，直接跑上面这条命令。
    private static volatile int adaptiveBlockTriggerHitsPerSecond = 6;
    private static volatile int adaptiveBlockDurationTicks = 20;
    // 2026-09-18：adaptiveBlockDamageReduction 字段删除——2026-09-10 裁决「格挡就全免」后
    // 该键再无消费者（DamagePipeline.stageAdaptiveGuardBlock 直接 cancel），旧配置兼容读取也一并移除。
    private static volatile int adaptiveBlockCooldownTicks = 40;

    // ========== 战斗区域 ==========
    /** 通用脱战半径（格）：超出后开始计时，持续 {@link #battleExpelTimeoutSeconds} 未返回即判定脱战。
     *  2026-09-10（用户裁决）：默认 32 → **72**，以作者攻略「玩家以脱战方式离场，判定 72 格」为准。
     *  <p>2026-09-12（用户裁决「**以不误踢为主**」）：**取消 2.9 的专属即时逐出档**
     *  （原 {@code RediosEntity.VOID_BATTLE_RANGE_BLOCKS} = 64，超出即逐出、无宽限）——
     *  它是 {@code tickBattleAreaCheck} 的重复实现，而后者在 {@code tick()} 里无条件执行、本已覆盖 2.9；
     *  该档的存在前提（用 Boss 实时坐标判定 ⇒ 因传送误判 ⇒ 需更近半径补偿）在原点改为战斗锚点后消失。
     *  取消依据还包括原始设计（也许.txt §5 对 2.10）原文「脱战判定**同第三章 3.6**」。
     *  <p>现行**两档**（刻意不同值 —— 同值会让即时档抢先执行、把本档宽限变成死代码）：
     *   · 本值（默认 72）+ {@link #battleExpelTimeoutSeconds} 宽限：**所有阶段含 2.9**
     *     （tickBattleAreaCheck 单人逐出 / checkAllParticipantsDisengaged 全员脱战）；
     *   · {@code RediosEntity.HARD_FLEE_RADIUS_BLOCKS}（84）：极端逃离，超出即无奖励退场、无宽限
     *     （tickChunkRetention / tickAntiExile）。
     *  <p>2026-09-12 教训：把 84 那条即时档误并到本值（72）后，即时退场因先执行而抢先，本值的
     *  60 秒宽限、tickBattleAreaCheck 的逐出超时、VOTE 期 600 tick 投票倒计时全部沦为死代码。 */
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

    // ========== SA 池名单（2026-09-12 热配置化） ==========
    // 2026-09-12（SA 名单热配置化）：三个名单键由 SilentSunConfig（静态、改完需重启）迁到热配置
    // redios_rules.json（boss_sa_* 三键），改完重载即生效。
    //
    // 【本处原有三个 DEFAULT_BOSS_SA_* 常量，已删除】—— 删除经过与理由：
    //   初版实现让「整份 json 缺失」分支引用它们写死默认值，于是它们有了唯一引用点；
    //   但随后确认为更一致的做法是**该分支也传 null、交给静态配置回退**（toml 是独立文件，
    //   不因 json 丢失而失效；写死默认值会让「作者改过 toml + json 丢失」这个组合静默丢弃他的配置）。
    //   改成 null 后三个常量即成零引用死代码 —— 默认值本身没有丢失，它们已完整存在于
    //   SilentSunConfig 的 BOSS_SA_WHITELIST_NAMESPACES / BOSS_SA_EXCLUDED_NAMESPACES /
    //   BOSS_SA_EXCLUDED_SA_IDS（那才是回退层，也是唯一真源）。
    //   ⇒ 默认值只有一个来源：静态配置键。热配置只表达「覆盖」或「显式全禁」，不再持有第三份副本。
    // 2026-09-12（SA 名单热配置化）：**三态语义** —— null = 键从未配置（调用方回退 SilentSunConfig 静态键）；
    // 空列表 = 作者显式全禁（白名单全禁 / 排除项为空 = 不排除）；非空 = 生效值。
    // 初值特意为 null：「尚未 reload 过」等价于「未配置」⇒ 走静态回退，与迁移前行为一致；
    // **不能**用任何默认清单作初值——否则「写 [] 想禁掉全部 SA」会被默认值悄悄吃掉。
    private static volatile List<String> bossSaWhitelistNamespaces;
    private static volatile List<String> bossSaExcludedNamespaces;
    private static volatile List<String> bossSaExcludedSaIds;

    // ========== 卡顿保护 ==========
    // N2: 原名 fpsThreshold 实为"低帧率判定用延迟阈值(毫秒)"，更名 latencyThresholdMs
    // M1: 默认 150ms——避免把普通网络玩家（30-80ms）误判为低帧率
    private static volatile int latencyThresholdMs = 150;
    private static volatile boolean lagProtectionEnabled = true;

    // ========== 成书与文本 ==========
    private static volatile String rediosBookAuthor = "Redios";
    // 2026-09-12（审计清理 G17 #5）：删除死配置 rediosDefeatBookTitle / rediosVictoryBookTitle
    //（及 getter/setter、json 键 redios_defeat_book_title / redios_victory_book_title）——
    // 它们唯一的读取点是 RediosEntity 的两个无调用者方法 createDefeatBookAndQuill / createVictoryBook，
    // 两者已删除；成品书标题改用常量 OUTCOME_BOOK_TITLE。依据：docs\_审计-2026-09-11\G17.md §5。
    // 2026-09-18（多语言接线）：原 rediosNotePhase1WinPhase2Lose / 三个 rediosOutcomeText*File
    // 字段已删除——结局书正文改走 lang 键（book.silent_sun.*）随客户端语言解析，不再读数据包 txt。

    // ========== 战斗音乐 ==========
    private static volatile boolean rediosBattleMusicEnabled = true;
    private static volatile float rediosBattleMusicVolume = 1.0f;
    // A-1（2026-09-11）：三段结构。*_ticks 表示各段音频长度——服务端据此切换（intro→loop）
    // 与重发（流式 ogg 无法自动循环，loop 每 loop_ticks 重发一次）。
    // **按阶段分组**：实测 P1/P2 的 intro 素材长度差异很大（873 vs 482 tick），单组配置无法
    // 同时正确（短的那段会留下静音空档），故每阶段各一组。默认值 = 各 ogg 实测时长。
    private static volatile int rediosBattleMusicPhase1IntroTicks = 873;
    private static volatile int rediosBattleMusicPhase1LoopTicks = 3245;
    private static volatile int rediosBattleMusicPhase2IntroTicks = 482;
    private static volatile int rediosBattleMusicPhase2LoopTicks = 3171;
    private static volatile boolean rediosBattleMusicOutroEnabled = true;

    // ========== 武器弱点 ==========
    private static volatile boolean weaponWeakpointEnabled = true;
    private static volatile int weaponWeakpointSlowTicks = 100;
    private static volatile int weaponWeakpointCooldownTicks = 300;
    private static volatile int weaponWeakpointFixedCooldown = 20;
    // 振刀弱点窗口期间的额外伤害倍率（>1 表示玩家在该窗口内对 Boss 造成更多伤害）。
    private static volatile double weaponWeakpointDamageMultiplier = 1.5;
    // 振刀弱点窗口期间的护甲穿透比例（0~1）：1.0 表示完全无视 Boss 护甲。
    private static volatile double weaponWeakpointArmorPierce = 0.5;

    // ========== 真伤光环参数（2026-09-14 · 步骤 3 配置化） ==========
    // 两个真伤光环原先 10 个数值全部**硬编码**在 RediosEntity 的 tickSorrowToilAura / tickChaosRuinAura
    // 内部（伤害、伤害判定半径、伤害间隔、粒子环半径、粒子间隔），现抽为热配置键。
    // 默认值与原硬编码**逐位相同** ⇒ 不改配置时行为零变化。
    // ⚠️ 粒子参数（particle_*）是**纯视觉**，与伤害参数同组仅为便于集中维护。
    // 悲愿辛劳光环（RediosEntity.tickSorrowToilAura）
    private static volatile double sorrowToilAuraDamage = 1.0;
    private static volatile double sorrowToilAuraRadius = 5.0;
    private static volatile int sorrowToilAuraIntervalTicks = 4;
    private static volatile double sorrowToilAuraParticleRadius = 5.0;
    private static volatile int sorrowToilAuraParticleIntervalTicks = 2;
    // 混沌废墟光环（RediosEntity.tickChaosRuinAura）
    private static volatile double chaosRuinAuraDamage = 3.0;
    private static volatile double chaosRuinAuraRadius = 5.0;
    private static volatile int chaosRuinAuraIntervalTicks = 20;
    private static volatile double chaosRuinAuraParticleRadius = 5.0;
    private static volatile int chaosRuinAuraParticleIntervalTicks = 5;

    // ================================================================
    // Twilight Moment
    // ================================================================
    public static TwilightMomentMode twilightMomentMode() { return twilightMomentMode; }
    public static void setTwilightMomentMode(TwilightMomentMode mode) { twilightMomentMode = mode == null ? TwilightMomentMode.REFRESH : mode; }
    // 2026-09-11（代码审计 G02 #4 修复）：原 twilightMomentPunishment() getter 全库零调用者 ——
    // 消费全部走 twilightMomentExpelMode()；字段与 setter 保留（后者仍被 reload 使用）。
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
    // SA Pool Names (2026-09-12 热配置化：JSON 为准，SilentSunConfig 静态键仅作回退)
    // ================================================================
    public static List<String> bossSaWhitelistNamespaces() { return bossSaWhitelistNamespaces; }
    public static void setBossSaWhitelistNamespaces(List<String> v) {
        // 2026-09-12（SA 名单热配置化）：本 setter **刻意不照抄**文件内其它 setter 的
        // `v == null || v.isEmpty() ? DEFAULT : ...` 空值兜底 —— 那会把「写 [] 显式全禁」
        // 静默吃成默认名单，现象是「改了 json + reload 看起来正常、白名单其实没变」。
        // 这里只区分三态：null（键缺失/整份配置缺失之外未配置）原样保留，其余一律 List.copyOf。
        bossSaWhitelistNamespaces = v == null ? null : List.copyOf(v);
    }
    public static List<String> bossSaExcludedNamespaces() { return bossSaExcludedNamespaces; }
    public static void setBossSaExcludedNamespaces(List<String> v) {
        // 2026-09-12（SA 名单热配置化）：同上 —— 不做空表兜底，null 与空表必须可区分
        // （null=未配置⇒回退静态键；空表=显式「不排除任何 namespace」）。
        bossSaExcludedNamespaces = v == null ? null : List.copyOf(v);
    }
    public static List<String> bossSaExcludedSaIds() { return bossSaExcludedSaIds; }
    public static void setBossSaExcludedSaIds(List<String> v) {
        // 2026-09-12（SA 名单热配置化）：同上 —— 不做空表兜底（null=未配置⇒回退静态键；空表=显式「不排除任何 SA id」）。
        bossSaExcludedSaIds = v == null ? null : List.copyOf(v);
    }

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
    // 2026-09-12（审计清理 G17 #5）：原 rediosDefeatBookTitle() / setRediosDefeatBookTitle() /
    // rediosVictoryBookTitle() / setRediosVictoryBookTitle() 四个访问器已删除（零消费者，见上方字段处说明）。

    // ================================================================
    // Battle Music
    // ================================================================
    public static boolean rediosBattleMusicEnabled() { return rediosBattleMusicEnabled; }
    public static void setRediosBattleMusicEnabled(boolean v) { rediosBattleMusicEnabled = v; }
    public static float rediosBattleMusicVolume() { return rediosBattleMusicVolume; }
    public static void setRediosBattleMusicVolume(float v) { if (!Float.isFinite(v)) { rediosBattleMusicVolume = 1.0f; return; } rediosBattleMusicVolume = Math.max(0.0f, Math.min(4.0f, v)); }
    public static int rediosBattleMusicPhase1IntroTicks() { return rediosBattleMusicPhase1IntroTicks; }
    public static void setRediosBattleMusicPhase1IntroTicks(int v) { rediosBattleMusicPhase1IntroTicks = Math.max(1, v); }
    public static int rediosBattleMusicPhase1LoopTicks() { return rediosBattleMusicPhase1LoopTicks; }
    public static void setRediosBattleMusicPhase1LoopTicks(int v) { rediosBattleMusicPhase1LoopTicks = Math.max(1, v); }
    public static int rediosBattleMusicPhase2IntroTicks() { return rediosBattleMusicPhase2IntroTicks; }
    public static void setRediosBattleMusicPhase2IntroTicks(int v) { rediosBattleMusicPhase2IntroTicks = Math.max(1, v); }
    public static int rediosBattleMusicPhase2LoopTicks() { return rediosBattleMusicPhase2LoopTicks; }
    public static void setRediosBattleMusicPhase2LoopTicks(int v) { rediosBattleMusicPhase2LoopTicks = Math.max(1, v); }
    public static boolean rediosBattleMusicOutroEnabled() { return rediosBattleMusicOutroEnabled; }
    public static void setRediosBattleMusicOutroEnabled(boolean v) { rediosBattleMusicOutroEnabled = v; }

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

    // ================================================================
    // 真伤光环参数（2026-09-14 · 步骤 3 配置化）
    // ================================================================
    // ⚠️ 回退默认值与字段初值**各写一遍** —— 这是本项目既定模式（见 P1-2 结论：保持现状 + 脚本对账）。
    //    改动后请跑 `_规则\rules_defaults_check.ps1`，**不要手工逐项核对**。
    // ⚠️ 间隔类一律 `Math.max(1, v)`：它们在 `tickCount % N` 里当除数，N=0 会抛除零。
    public static double sorrowToilAuraDamage() { return sorrowToilAuraDamage; }
    public static void setSorrowToilAuraDamage(double v) { sorrowToilAuraDamage = Double.isFinite(v) ? Math.max(0.0, v) : 1.0; }
    public static double sorrowToilAuraRadius() { return sorrowToilAuraRadius; }
    public static void setSorrowToilAuraRadius(double v) { sorrowToilAuraRadius = Double.isFinite(v) ? Math.max(0.0, v) : 5.0; }
    public static int sorrowToilAuraIntervalTicks() { return sorrowToilAuraIntervalTicks; }
    public static void setSorrowToilAuraIntervalTicks(int v) { sorrowToilAuraIntervalTicks = Math.max(1, v); }
    public static double sorrowToilAuraParticleRadius() { return sorrowToilAuraParticleRadius; }
    public static void setSorrowToilAuraParticleRadius(double v) { sorrowToilAuraParticleRadius = Double.isFinite(v) ? Math.max(0.0, v) : 5.0; }
    public static int sorrowToilAuraParticleIntervalTicks() { return sorrowToilAuraParticleIntervalTicks; }
    public static void setSorrowToilAuraParticleIntervalTicks(int v) { sorrowToilAuraParticleIntervalTicks = Math.max(1, v); }
    public static double chaosRuinAuraDamage() { return chaosRuinAuraDamage; }
    public static void setChaosRuinAuraDamage(double v) { chaosRuinAuraDamage = Double.isFinite(v) ? Math.max(0.0, v) : 3.0; }
    public static double chaosRuinAuraRadius() { return chaosRuinAuraRadius; }
    public static void setChaosRuinAuraRadius(double v) { chaosRuinAuraRadius = Double.isFinite(v) ? Math.max(0.0, v) : 5.0; }
    public static int chaosRuinAuraIntervalTicks() { return chaosRuinAuraIntervalTicks; }
    public static void setChaosRuinAuraIntervalTicks(int v) { chaosRuinAuraIntervalTicks = Math.max(1, v); }
    public static double chaosRuinAuraParticleRadius() { return chaosRuinAuraParticleRadius; }
    public static void setChaosRuinAuraParticleRadius(double v) { chaosRuinAuraParticleRadius = Double.isFinite(v) ? Math.max(0.0, v) : 5.0; }
    public static int chaosRuinAuraParticleIntervalTicks() { return chaosRuinAuraParticleIntervalTicks; }
    public static void setChaosRuinAuraParticleIntervalTicks(int v) { chaosRuinAuraParticleIntervalTicks = Math.max(1, v); }

    private RediosRules() {}

    // ================================================================
    // Enums
    // ================================================================
    public enum BossMissingVisionAction { TELEPORT, WAYPOINT }
    public enum TwilightMomentMode { REFRESH, TIMED }
    /** E1: 2.5 断光之刻惩罚方式——EXPEL 逐出战斗 / VISUAL 仅视觉改变（默认）。 */
    public enum TwilightMomentPunishmentMode { EXPEL, VISUAL }
}
