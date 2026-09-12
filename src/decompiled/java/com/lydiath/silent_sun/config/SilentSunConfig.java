package com.lydiath.silent_sun.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class SilentSunConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Redios 基础数值
    public static final ModConfigSpec.IntValue PHASE_MAX_HEALTH = BUILDER.defineInRange("redios.phaseMaxHealth", 2000, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue BASE_ATTACK_DAMAGE = BUILDER.defineInRange("redios.baseAttackDamage", 30.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue BASE_ATTACK_REACH = BUILDER.defineInRange("redios.baseAttackReach", 5.0, 0.0, 64.0);

    // 参战机制
    // true（默认）= Mode 1 仅玩家参战：Boss 只攻击玩家，非玩家仅有主宠物可造成有限伤害。
    // false = Mode 2 斗蛐蛐：Boss 视玩家为友好、只攻击敌对生物（按数据包白名单或安全回退），
    // 玩家无法直接对 Boss 造成伤害，仅能通过敌对生物斗蛐蛐。
    public static final ModConfigSpec.BooleanValue PLAYER_BATTLE_ONLY_ENABLED = BUILDER
        .comment("是否仅玩家参战（战斗模式开关）。",
            "true（默认）= 模式一：Boss 只攻击玩家，非玩家仅有主宠物可造成有限伤害。",
            "false = 模式二（斗蛐蛐）：Boss 视玩家为友好、只攻击敌对生物",
            "（按 silent_sun:boss_primary_targets / boss_mob_targets 数据包白名单，",
            "数据包未安装时回退为任意敌对生物），玩家无法直接对 Boss 造成伤害。")
        .define("redios.playerBattleOnlyEnabled", true);

    // 阶段时间
    public static final ModConfigSpec.IntValue PHASE1_TITLE_MIN_SECONDS = BUILDER
        .comment("P1 每个头衔锁血的最短时长（秒）。锁血期间 Boss 血量被钳制在当前头衔段",
            "（下限段低+1、上限段顶-ε），锁血结束后若血量已打穿段低则推进下一头衔，",
            "否则重新锁血。调低可加快 P1 推进节奏。")
        .defineInRange("redios.phase1TitleMinSeconds", 15, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue PHASE2_TITLE_MIN_SECONDS = BUILDER
        .comment("P2 每个头衔锁血的最短时长（秒）。注意：代码强制下限为 30 秒",
            "（getMinTitleSeconds 中 Math.max(30, ...)），此处填写的值低于 30 时按 30 生效，",
            "避免 P2 头衔切换过快导致混沌之墟/暮光时刻等阶段技能难以完整演出。")
        .defineInRange("redios.phase2TitleMinSeconds", 30, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue PHASE_TRANSITION_SECONDS = BUILDER.defineInRange("redios.phaseTransitionSeconds", 6, 0, Integer.MAX_VALUE);

    // Phase 2 防御
    public static final ModConfigSpec.DoubleValue PHASE2_ARMOR_VALUE = BUILDER.defineInRange("redios.phase2ArmorValue", 25.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue PHASE2_DAMAGE_REDUCTION = BUILDER.defineInRange("redios.phase2DamageReduction", 0.45, 0.0, 1.0);
    // 2026-08-12：Boss 普攻对玩家的护甲穿透比例（0~1）。玩家毕业护甲下 Boss 普攻打不出伤害时调高；
    // 穿透部分走真伤路径（保底 5% 护甲减免、上限 200），与 AbsoluteDamageUtil 口径一致。
    public static final ModConfigSpec.DoubleValue BOSS_ARMOR_PIERCE = BUILDER.defineInRange("redios.bossArmorPierce", 0.4, 0.0, 1.0);

    // 2026-08-12：Boss 能否对创造模式玩家造成伤害（含混沌/砺锋等真伤路径）。
    // 默认开启（true）：Boss 可对创造玩家造成伤害，便于作者在创造模式下测试战斗数值；
    // 创造玩家攻击 Boss 会触发反作弊"仅打一次以示警示"（counterCheatAttacker + 提示），
    // 随后进入友好离场/物品追踪流程。旁观者始终免疫；需纯防作弊时关闭为 false。
    public static final ModConfigSpec.BooleanValue BOSS_DAMAGE_CREATIVE = BUILDER
        .comment("Boss 能否对创造模式玩家造成伤害（含混沌/砺锋真伤路径）。",
            "默认可伤（true）：创造玩家攻击会触发一次反作弊警示，随后友好离场；旁观者仍免疫。",
            "设为 false 恢复纯防作弊语义（创造玩家完全免疫真伤）。")
        .define("redios.bossDamageCreative", true);

    // Phase 1 防御（等效满保护附魔下界合金套：20护甲+20%最终减伤）
    public static final ModConfigSpec.DoubleValue PHASE1_ARMOR_VALUE = BUILDER.defineInRange("redios.phase1ArmorValue", 20.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue PHASE1_DAMAGE_REDUCTION = BUILDER.defineInRange("redios.phase1DamageReduction", 0.20, 0.0, 1.0);

    // 通用限伤与动态减伤
    public static final ModConfigSpec.DoubleValue DAMAGE_HARD_CAP = BUILDER.defineInRange("redios.damageHardCap", 200.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue DYNAMIC_REDUCTION_THRESHOLD = BUILDER.defineInRange("redios.dynamicReductionThreshold", 100.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue DYNAMIC_REDUCTION_RATIO = BUILDER.defineInRange("redios.dynamicReductionRatio", 0.5, 0.0, 1.0);
    // 灾变式动态减伤（2026-09-01 用户裁决：参考灾变模组——随时间递减的高额减伤，9bypass 打穿）：
    // 初始减伤率（战斗开始时的最终减伤比例），随战斗进行按每秒衰减率下降，归零后不再减伤。
    public static final ModConfigSpec.DoubleValue DYNAMIC_REDUCTION_INITIAL = BUILDER.defineInRange("redios.dynamicReductionInitial", 0.8, 0.0, 0.95);
    public static final ModConfigSpec.DoubleValue DYNAMIC_REDUCTION_DECAY_PER_SEC = BUILDER.defineInRange("redios.dynamicReductionDecayPerSec", 0.02, 0.0, 1.0);

    // 友好生物限伤（模式一：仅玩家参战时，友好生物对Boss伤害上限）
    public static final ModConfigSpec.DoubleValue FRIENDLY_MOB_DAMAGE_CAP = BUILDER.defineInRange("redios.friendlyMobDamageCap", 25.0, 0.0, Double.MAX_VALUE);

    // 击退抗性
    public static final ModConfigSpec.DoubleValue KNOCKBACK_RESISTANCE = BUILDER.defineInRange("redios.knockbackResistance", 1.0, 0.0, 1.0);

    // 冷却（上限封到 3 天：除一阶段半天外，其余统一 3 天，不允许配置超过 3 天）
    public static final ModConfigSpec.IntValue COOLDOWN_DAYS = BUILDER.defineInRange("redios.cooldownDays", 3, 0, 3);
    public static final ModConfigSpec.DoubleValue COOLDOWN_HALF_DAYS = BUILDER.defineInRange("redios.cooldownHalfDays", 0.5, 0.0, 3.0);

    // 修复与方块
    public static final ModConfigSpec.DoubleValue WISH_REPAIR_PERCENT_PER_SECOND = BUILDER.defineInRange("redios.wishRepairPercentPerSecond", 0.01, 0.0, 1.0);
    public static final ModConfigSpec.IntValue STAGE_MINED_BLOCK_QUEUE_MAX = BUILDER.defineInRange("redios.stageMinedBlockQueueMax", 64, 0, 2048);

    // 断魂
    public static final ModConfigSpec.IntValue SOUL_SEVER_BASE_X = BUILDER.defineInRange("soulSever.baseX", 30, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SOUL_SEVER_DURATION_SECONDS = BUILDER.defineInRange("soulSever.durationSeconds", 15, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SOUL_SEVER_MAX_AMPLIFIER = BUILDER.defineInRange("soulSever.maxAmplifier", 4, 0, 10);
    public static final ModConfigSpec.IntValue SOUL_SEVER_Y_WARNING_THRESHOLD = BUILDER.defineInRange("soulSever.yWarningThreshold", 5000, 0, Integer.MAX_VALUE);

    // 回血速率（每 20 tick 即每秒生效一次）
    // 每次 30 点 = 每秒 30 点（P1）；每次 60 点 = 每秒 60 点（P2）
    public static final ModConfigSpec.DoubleValue PHASE1_HEALTH_REGEN = BUILDER.defineInRange("redios.phase1HealthRegen", 30.0, 0.0, Double.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue PHASE2_HEALTH_REGEN = BUILDER.defineInRange("redios.phase2HealthRegen", 60.0, 0.0, Double.MAX_VALUE);

    // 头衔锁血期间回血是否允许退回上一个头衔（配置开关，默认 false）
    // 使用 .comment() 而非 // 注释，确保完整描述写入生成的 config/silent_sun/xxx.toml
    public static final ModConfigSpec.BooleanValue ALLOW_TITLE_LOCK_HEAL_REGRESSION = BUILDER
        .comment("头衔锁血期间，自动回血是否允许把血量抬回上一个头衔段。",
            "默认 false（推荐）：锁血期间回血被钳制在当前头衔段顶（段顶-ε），",
            "无论回复多少都不会越过头衔段边界，头衔不会因回血倒退——",
            "玩家把 Boss 打进新头衔段后，Boss 的回血无法把进度拖回去。",
            "true：锁血期间回血不设段顶限制，可自然回升；血量越过当前头衔段顶时",
            "头衔按退回规则回退到上一个头衔段，并重新进入锁血（回退后仍受锁血保护，",
            "且锁血失效后 5 秒禁回血缓冲依然生效）。",
            "开启后玩家需在锁血期间保持输出压制，否则 Boss 可能靠回血退回上一头衔。")
        .define("redios.allowTitleLockHealRegression", false);

    // 濒死锁血到期后回血越段是否回退上一头衔重打（配置开关，默认 false）
    public static final ModConfigSpec.BooleanValue ALLOW_PENDING_LOCK_HEAL_REGRESSION = BUILDER
        .comment("濒死锁血（PENDING）到期后，若期间回血使血量越回上一头衔段，",
            "是否回退到上一头衔重打（DPS 检测）。",
            "默认 false（推荐）：锁血到期不回退重打，直接按原逻辑结算——",
            "P1 濒死到期进二阶段投票，P2 濒死到期直接结算死亡。",
            "true：锁血到期先判血量，回到上一头衔段则回退到上一头衔并重新锁血，",
            "要求玩家在濒死锁血期间持续输出压制回血，否则 Boss 靠回血回退上一头衔、",
            "拉高战斗强度（回退重打）。")
        .define("redios.allowPendingLockHealRegression", false);

    // 召唤广播（2026-08-12 新增：召唤成功时召唤者全服广播的文案，留空则关闭）
    public static final ModConfigSpec.ConfigValue<String> SUMMON_BROADCAST_MESSAGE = BUILDER
        .comment("玩家使用莱德厄斯召唤器成功召唤 Boss 时，在全服聊天栏广播的文案。",
            "以\"<召唤者名> 文案\"形式发送；留空字符串则不广播。")
        .define("redios.summonBroadcastMessage", "你好，交友。");

    // 繁星爆闪（StarfallSalvo）：整场战斗（P1+P2）每 45 秒（900 tick）固定发动一次，
    // 在 Boss 周围 radius 格召唤 count 颗无碰撞箱星星，随机延迟后垂直下落至离地 2 格悬停，
    // 全部就位（或超时兜底）后统一爆炸；破坏方块遵循 mobGriefing 开关。
    public static final ModConfigSpec.IntValue STARFALL_SALVO_INTERVAL_TICKS = BUILDER
        .comment("繁星爆闪的发动间隔（tick）。默认 900 = 45 秒。")
        .defineInRange("redios.starfallSalvoIntervalTicks", 900, 20, Integer.MAX_VALUE);
    public static final ModConfigSpec.DoubleValue STARFALL_SALVO_RADIUS = BUILDER
        .comment("繁星爆闪的召唤范围半径（格）：星星（爆炸实体）在 Boss 周围随机生成/分布的半径，",
            "此 20 格就是召唤范围半径。注意：它只决定星星落点范围，不是爆炸/伤害半径——",
            "实际爆炸/伤害范围由下方 redios.starfallSalvoExplosionPower 逐星决定。默认 20。")
        .defineInRange("redios.starfallSalvoRadius", 20.0, 1.0, 64.0);
    public static final ModConfigSpec.DoubleValue STARFALL_SALVO_ATTACK_RADIUS = BUILDER
        .comment("繁星爆闪的攻击/断魂收集半径（格）：每次发动时在该半径内收集全部合法目标，",
            "再按各自威胁值分配星星数量。设计稿 §8.2 记为 24。",
            "实取 max(本值, 当前攻击距离 × 4)——保证 2026-09-01 裁定「大招对视距内全部合法目标索敌」",
            "不被削弱（攻击距离随阶段/激怒变化时半径同步放大）。默认 24。")
        .defineInRange("redios.starfallSalvoAttackRadius", 24.0, 1.0, 128.0);
    public static final ModConfigSpec.IntValue STARFALL_SALVO_MIN_COUNT = BUILDER
        .comment("繁星爆闪每次召唤星星的最少数量。")
        .defineInRange("redios.starfallSalvoMinCount", 12, 1, 64);
    public static final ModConfigSpec.IntValue STARFALL_SALVO_MAX_COUNT = BUILDER
        .comment("繁星爆闪每次召唤星星的最多数量。")
        .defineInRange("redios.starfallSalvoMaxCount", 16, 1, 64);
    public static final ModConfigSpec.IntValue STARFALL_SALVO_MAX_DELAY_TICKS = BUILDER
        .comment("繁星爆闪每颗星星开始下落的随机最大延迟（tick）。默认 60 = 0~3 秒。",
            "2026-09-11 依设计更正默认 10 → 60：设计口径为「0~3 秒内随机下落」，原 0.5 秒使星星几乎同时落下、",
            "失去逐颗落下的层次感。逐星取 [0, 本值] 均匀随机。")
        .defineInRange("redios.starfallSalvoMaxDelayTicks", 60, 0, 200);
    public static final ModConfigSpec.DoubleValue STARFALL_SALVO_EXPLOSION_POWER = BUILDER
        .comment("繁星爆闪每颗星星爆炸的威力（大范围随机爆破半径，沿用 Level.explode 口径，",
            "破坏方块遵循 mobGriefing）。默认 6.0，约 6 格爆炸半径，逐星独立覆盖召唤半径内的大范围区域。")
        .defineInRange("redios.starfallSalvoExplosionPower", 6.0, 0.0, 64.0);

    // 灭却之日「长梦彼端的灾厄之影」掉落数量（2026-09-10 用户裁决 B6：数量配置化）。
    // 原先三处掉落点都是硬编码（P1 为 1 + rand(4)；P2 为固定 1 + rand(7~12)），整合包无法调整。
    public static final ModConfigSpec.IntValue CALAMITY_SHADOW_PHASE1_MIN = BUILDER
        .comment("一阶段奖励里「灾祸之影」的最小数量。默认 1（原硬编码 1~4 的下界）。",
            "灭却之日未安装/未提供该物品时静默跳过。")
        .defineInRange("redios.calamityShadowPhase1Min", 1, 0, 64);
    public static final ModConfigSpec.IntValue CALAMITY_SHADOW_PHASE1_MAX = BUILDER
        .comment("一阶段奖励里「灾祸之影」的最大数量。默认 4（原硬编码 1~4 的上界）。")
        .defineInRange("redios.calamityShadowPhase1Max", 4, 0, 64);
    public static final ModConfigSpec.IntValue CALAMITY_SHADOW_PHASE2_MIN = BUILDER
        .comment("二阶段奖励里「灾祸之影」的最小数量。默认 8（= 原「固定 1 + 随机 7」）。")
        .defineInRange("redios.calamityShadowPhase2Min", 8, 0, 64);
    public static final ModConfigSpec.IntValue CALAMITY_SHADOW_PHASE2_MAX = BUILDER
        .comment("二阶段奖励里「灾祸之影」的最大数量。默认 13（= 原「固定 1 + 随机 12」）。")
        .defineInRange("redios.calamityShadowPhase2Max", 13, 0, 64);

    // 2.8 无光失色挑战成功时限：进入 2.8 起算；若未在该时限内攻克 Boss，则视为挑战成功
    // （Boss 转为友好生物并走创造离场路径，进入 3 天召唤冷却）。
    // 代码强制下限 = 最后两个二阶段头衔持续时间（2 × P2 头衔锁血时长，默认 60 秒）。
    // 2026-09-10 用户裁决：**冻结态不倒计时**（投票 / 转场 / 两阶段濒死 / 锁血期均暂停），
    // 即"实际可打时长"恒为该值；回退后重进 2.8 不重置。
    public static final ModConfigSpec.IntValue COLORLESS_CHALLENGE_SECONDS = BUILDER
        .comment("2.8 无光失色激活后的挑战成功时限（秒）。",
            "从「进入 2.8」起算；**冻结态暂停计时**（投票 / 转场 / 两阶段濒死 / 锁血期不倒计时），",
            "因此该值等于玩家实际可打时长。若未在该时限内攻克 Boss，则视为挑战成功：",
            "Boss 转为友好生物并离场，进入 3 天召唤冷却。代码强制下限为最后两个二阶段头衔的持续时间",
            "（2 × P2 头衔锁血时长，默认 60 秒），填写的值低于该下限时按该下限生效。")
        .defineInRange("redios.colorlessChallengeSeconds", 300, 0, Integer.MAX_VALUE);

    // ── 兼容性补丁（§7.6：免伤顺序 / 伤害为 0 排查） ──

    // Phase1.9 完全防御（可选）：phase=1 且 titleIndex=9（有所不为）时强制免伤。
    // 用于排查整合包中该头衔被外部模组异常破防/秒杀的问题；默认关闭，不影响正常流程。
    public static final ModConfigSpec.BooleanValue PHASE1_ABSOLUTE_DEFENSE = BUILDER
        .comment("Phase1.9 完全防御（兼容性补丁，可选）。",
            "phase=1 且 titleIndex=9（有所不为）时，对 Boss 的一切伤害强制免伤。",
            "用于排查整合包冲突（该头衔被外部模组异常破防/秒杀）。",
            "默认关闭；开启后 Boss 在此头衔期间无法受伤，正常投票推进也会被暂停，",
            "仅在诊断/隔离冲突时使用，正式游玩请保持关闭。")
        .define("redios.phase1AbsoluteDefense", false);

    // 绝对真实伤害兜底（兼容性补丁，可选，默认关闭）
    public static final ModConfigSpec.BooleanValue ABSOLUTE_DAMAGE_FALLBACK = BUILDER
        .comment("绝对真实伤害兜底（兼容性补丁，可选）。",
            "true 时，若绝对真实伤害在 LivingIncomingDamageEvent 阶段被第三方模组取消或改小，",
            "则强制按真伤标记值恢复，确保真伤一定命中。",
            "默认关闭；仅在遇到第三方减伤/免伤模组导致真伤失效时开启。")
        .define("redios.absoluteDamageFallback", false);

    // AI 看门狗：长时间无目标时强制重锁最近参战者。
    public static final ModConfigSpec.IntValue AI_WATCHDOG_CHECK_INTERVAL_TICKS = BUILDER
        .comment("AI 看门狗：每隔多少 tick 检查一次 Boss 是否有索敌目标。默认 20（每秒一次）。")
        .defineInRange("redios.aiWatchdogCheckIntervalTicks", 20, 1, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue AI_WATCHDOG_NO_TARGET_TICKS = BUILDER
        .comment("AI 看门狗：Boss 连续无有效目标多少 tick 后强制重锁最近参战者。默认 100（5 秒）。")
        .defineInRange("redios.aiWatchdogNoTargetTicks", 100, 1, Integer.MAX_VALUE);

    // damageZeroLog：最终伤害归零/被管线取消时的服务端日志记录。
    public static final ModConfigSpec.BooleanValue DAMAGE_ZERO_LOG_ENABLED = BUILDER
        .comment("damageZeroLog：最终伤害归零/被管线取消时是否记录服务端日志。默认关闭。",
            "用于定位\"伤害为 0 / 无法造成伤害\"的整合包冲突。")
        .define("redios.damageZeroLogEnabled", false);
    public static final ModConfigSpec.IntValue DAMAGE_ZERO_LOG_COOLDOWN_TICKS = BUILDER
        .comment("damageZeroLog：同源伤害日志限频冷却（tick）。默认 40。")
        .defineInRange("redios.damageZeroLogCooldownTicks", 40, 0, Integer.MAX_VALUE);

    // 2026-09-02：自制血条 UI（RediosBossBarRenderer + CUSTOM_BOSS_BAR_ENABLED）已删除，
    // Boss 血条只用原版默认渲染（服务端 ServerBossEvent 由原版 GUI 显示）。

    // 拔刀剑 Boss 随机 SA 池排除列表（2026-09-01）：默认排除狐月刀（foxextra）与天杀星刀
    // （tianshaxing）——两者 SA 有 SE 前提（Boss 刀 miedao_duan 无对应 SE 会放不出/异常），
    // 且 foxextra 的 VoidSlashPlus 时间线每帧调 Drive.doSlash + AttackManager.doSlash 生成多条
    // 剑气+刀光（刀光洪峰源）。其余 namespace（slashblade 内置、灭却之日、amazingshine、
    // shinkubloodkatana 等）全部进池，保持全随机精神。
    // 2026-09-12（字节码级复核后订正归因）：本条原先把「排除 foxextra 整包」的理由写成
    // 「其 SA 有 SE 前提 + 时间线每帧多实体 = 洪峰源」，两条均**不成立**：
    //   ① SE 方向是反的——foxextra 的 SummonSword 在每次 DoSlashEvent 上
    //      generateFivePointSwordRain(...,5)，是**放大器**（+5 实体/次），不是「缺 SE 就放不出」；
    //      AbstractSpecialEffect.isEffective 偏移 1 为 instanceof Player → 非 Player 取
    //      getRequestLevel()=60，60<=60 为真 ⇒ 对 Mob **恒生效**。
    //   ② Boss(Mob) 上 combo 时间线**不执行**——ItemStack.inventoryTick 的调用点全版本只有
    //      Inventory.java（玩家物品栏），LivingEntity 零命中；SlashBlade 里 tickAction 唯一调用点
    //      是 ItemSlashBlade.lambda$inventoryTick$12 ⇒ Mob 无驱动者，只有 clickAction 生效。
    // 「刀光洪峰」的真实驱动源仍未知，已另立运行时排查项，**不要再归因到 foxextra 时间线**。
    // 默认值由 ["foxextra","tianshaxing"] 改为 ["tianshaxing"]：foxextra 改用下面更精确的 SA id 列表，
    // 其 SA 中 void_slash_plus 当前为空放、sakura_endex 是本环境唯一真有输出者，均无 Player/SE 硬前提。
    // 2026-09-12（SA 名单热配置化）：本键已降级为**回退**——优先读热配置 silent_sun/redios_rules.json
    // 的 boss_sa_whitelist_namespaces（改完重载即生效）；仅当热配置未提供或为空时才回退读本静态键（改它需重启）。
    /**
     * Boss 随机施放 SA 的 **namespace 白名单**（2026-09-12 用户裁决：由黑名单改为白名单）。
     * <p>
     * 动因：黑名单是「默认信任、事后拉黑」—— 新装模组的 SA 会自动进池，要等出问题才发现
     * （{@code foxextra:thrust} 就是典型：它挂的 {@code checkcast Player} 仅因 Mob 上 combo 时间线
     * 不跑才暂时没炸）。白名单是 fail-safe：**新模组默认不进池**，必须针对性测过才放行。
     * <p>
     * 放行条件（三重判定，见 {@code IntegrationContract.isSaAllowed}）：
     * <pre>
     *   namespace ∈ 本白名单 ∧ namespace ∉ BOSS_SA_EXCLUDED_NAMESPACES ∧ id ∉ BOSS_SA_EXCLUDED_SA_IDS
     * </pre>
     * <p>
     * 默认值 = 本实例实装且已分析过的 7 个 namespace（各模组的 SA 注册表实测得出；SlashBlade 的
     * SA id 恒为 {@code <modid>:<sa_name>}，所以 namespace 就是 mod id）：
     * <ul>
     *   <li>{@code slashblade} —— 重锋本体（judgement_cut / sakura_end / piercing / circle_slash / drive_* / void_slash / wave_edge）；</li>
     *   <li>{@code slashblade_addon} —— SJAP 日系附属包（fire_spiral / gale_swords / lighting_swords / rapid_blistering_swords / spiral_edge / water_drive）；</li>
     *   <li>{@code extinction_day_mod_1784441698} —— 灭却之日（Boss 刀本体，含 life_severing_slash 等）；</li>
     *   <li>{@code foxextra} —— 狐月刀改·重生（其 thrust 已由 SA id 黑名单单独排除）；</li>
     *   <li>{@code slashbladeamazingshine} —— 荧光惊异（gold_shine）；</li>
     *   <li>{@code shinkubloodkatana} —— 炼狱真红之刃（heart_slash / heart_slashc）；</li>
     *   <li>{@code feibiblade} —— 飞比刀（jiubi 等；静态分析未覆盖，按用户裁决先放行，待实机针对性测试）。</li>
     * </ul>
     * <b>新模组接入流程</b>：装上后其 namespace 默认不在名单 ⇒ Boss 不会放它的 SA（零风险跑着）；
     * 想看能不能用，就临时加进本名单 + {@code /silent_sun battle_report on}，打一场后看报告里的
     * {@code saCasts}（ok / error / note）决定去留。
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_SA_WHITELIST_NAMESPACES = BUILDER
        .comment("Boss 随机施放 SA 的 **namespace 白名单**：不在名单里的 namespace 一律不进池。",
            "默认 7 个：slashblade / slashblade_addon / extinction_day_mod_1784441698 / foxextra /",
            "           slashbladeamazingshine / shinkubloodkatana / feibiblade",
            "新装模组的 SA 默认**不**进池（fail-safe）；要启用需针对性测试后把其 namespace 加进本列表。",
            "白名单之下还有两层二次排除：bossSaExcludedNamespaces 与 bossSaExcludedSaIds。",
            "2026-09-12（SA 名单热配置化）：本键已降级为**回退**，当前生效值优先取热配置",
            "silent_sun/redios_rules.json 的 boss_sa_whitelist_namespaces（改 json + 重载即生效）；",
            "仅当该热配置键未提供或为空时才使用本值，此时修改 TOML 仍需重启服务器。")
        .defineList("redios.bossSaWhitelistNamespaces",
            List.of("slashblade", "slashblade_addon", "extinction_day_mod_1784441698",
                    "foxextra", "slashbladeamazingshine", "shinkubloodkatana", "feibiblade"),
            o -> o instanceof String);

    // 2026-09-12（SA 名单热配置化）：本键已降级为**回退**——优先读热配置 silent_sun/redios_rules.json
    // 的 boss_sa_excluded_namespaces；仅当热配置未提供或为空时才回退读本静态键（改它需重启）。
    /**
     * 白名单**内部**的 namespace 二次排除（2026-09-12 语义变更：原先它是全局黑名单，现在只在白名单内生效）。
     * <p>
     * 默认四项及理由：
     * <ul>
     *   <li>{@code tianshaxing} / {@code tiansha_extinction} —— 天杀星刀：其 SA 以 **SE 为硬性前提**，
     *       Boss 刀无对应 SE，根本放不出来。（{@code tianshaxing} 是 mod id，佐证：实例
     *       {@code config/tianshaxing-common.toml} 存在，NeoForge 配置文件名规则是 {@code <modid>-<type>.toml}；
     *       {@code tiansha_extinction} 是该模组注册的**第二个** namespace，2026-09-12 补入。）</li>
     *   <li>{@code annihilationblade} —— 湮灭之刃（Arcsea/AnnihilationBlade）：**清除系作弊 SA**，
     *       其 {@code AbsoluteRemovalService} / {@code NuclearRemovalService} 会强制移除/终止实体。
     *       作者备注：「放出来出事我管不了」。详见 {@code docs/项目彻查报告-2026-09-10.md} §4.7。</li>
     *   <li>{@code annihilationbladeex} —— 湮灭之刃 EX（RLlufee）：同上；当前因缺 {@code jupiter} 前置装不上，
     *       但装上即会向 {@code slashblade:slash_arts} 注册 SA，故预先排除。</li>
     * </ul>
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_SA_EXCLUDED_NAMESPACES = BUILDER
        .comment("白名单内部的 namespace 二次排除（不在白名单里的 namespace 本来就不进池）。",
            "默认 [tianshaxing, tiansha_extinction, annihilationblade, annihilationbladeex]：",
            "  tianshaxing / tiansha_extinction —— 天杀星刀（后者是该模组注册的第二个 namespace）：",
            "      SA 以 SE 为硬性前提，Boss 刀无对应 SE，放不出来；",
            "  annihilationblade / annihilationbladeex —— 湮灭之刃(含EX)：清除系作弊 SA，",
            "      其 AbsoluteRemovalService / NuclearRemovalService 会强制移除实体（作者备注：放出来出事我管不了）。",
            "2026-09-12（SA 名单热配置化）：本键已降级为**回退**，当前生效值优先取热配置",
            "silent_sun/redios_rules.json 的 boss_sa_excluded_namespaces（改 json + 重载即生效）；",
            "仅当该热配置键未提供或为空时才使用本值，此时修改 TOML 仍需重启服务器。")
        .defineList("redios.bossSaExcludedNamespaces",
            List.of("tianshaxing", "tiansha_extinction", "annihilationblade", "annihilationbladeex"),
            o -> o instanceof String);

    // 2026-09-12（SA 名单热配置化）：本键已降级为**回退**——优先读热配置 silent_sun/redios_rules.json
    // 的 boss_sa_excluded_sa_ids；仅当热配置未提供或为空时才回退读本静态键（改它需重启）。
    /**
     * Boss 随机施放 SA 时的 **SA 级**排除列表（完整 id，形如 {@code foxextra:thrust}）。
     * <p>
     * 2026-09-12（用户裁决 + 字节码级复核）：与 {@link #BOSS_SA_EXCLUDED_NAMESPACES} **并行生效**
     * —— namespace 列表用于整包排除，本列表用于「只排掉某个 namespace 里真正不可用的单个 SA」。
     * <p>
     * 默认 {@code ["foxextra:thrust"]}，理由（已用 javap 逐段确认）：
     * <ul>
     *   <li>该 SA 的 combo {@code foxextra:thrust_ex} 在 {@code TimeLineTickAction.put(2, …)} 上挂了
     *       {@code FEXcomboRegsitry.lambda$static$7} → {@code com.dinzeer.foxextra.sa.Thrust.doSlash}，
     *       其字节码第一句就是 {@code checkcast net/minecraft/world/entity/player/Player}
     *       → {@code SMoveUtil.sendDashMessage(Player, …)}；</li>
     *   <li>施放者是 Boss（Mob）时必抛 {@code ClassCastException}，且抛出点在 {@code Item.inventoryTick}
     *       内，宿主的 {@code try/catch (Exception)} 抓不到，会落到 vanilla {@code guardEntityTick}
     *       → 每 tick 刷栈；</li>
     *   <li>当前该时间线**因 Mob 无驱动者而不执行**，所以这是「有驱动就炸」的地雷而非在线故障
     *       —— 但 {@code checkcast} 的确定性存在，故仍排除（一旦将来恢复 combo 驱动，不排就必酿事故）。</li>
     * </ul>
     * 修改后最迟 60 秒生效（SA 注册表键集有 60s TTL 缓存）。
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_SA_EXCLUDED_SA_IDS = BUILDER
        .comment("Boss 随机施放 SA 时排除的**完整 SA id** 列表（形如 foxextra:thrust）。",
            "与 redios.bossSaExcludedNamespaces 并行生效：namespace 用于整包排除，本列表用于精确排除单个 SA。",
            "默认 [foxextra:thrust]：其 combo 时间线的第 2 tick 会执行 checkcast Player，Boss 是 Mob，必抛 ClassCastException。",
            "同一 namespace 内其余 SA（foxextra 的 void_slash_plus / sakura_endex）经字节码复核无 Player/SE 硬性前提，保留在池中。",
            "提示：Boss(Mob) 上 combo 时间线不执行（ItemStack.inventoryTick 只对玩家物品栏调用），当前只有 clickAction 生效。",
            "2026-09-12（SA 名单热配置化）：本键已降级为**回退**，当前生效值优先取热配置",
            "silent_sun/redios_rules.json 的 boss_sa_excluded_sa_ids（改 json + 重载即生效）；",
            "仅当该热配置键未提供或为空时才使用本值，此时修改 TOML 仍需重启服务器。")
        .defineList("redios.bossSaExcludedSaIds",
            List.of("foxextra:thrust"),
            o -> o instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SilentSunConfig() {
    }
}
