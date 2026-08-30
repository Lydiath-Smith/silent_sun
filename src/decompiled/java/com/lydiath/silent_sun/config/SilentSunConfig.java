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
    public static final ModConfigSpec.IntValue SOUL_SEVER_MAX_AMPLIFIER = BUILDER.defineInRange("soulSever.maxAmplifier", 2, 0, 10);
    public static final ModConfigSpec.IntValue SOUL_SEVER_Y_WARNING_THRESHOLD = BUILDER.defineInRange("soulSever.yWarningThreshold", 5000, 0, Integer.MAX_VALUE);

    // 高度差飞行
    public static final ModConfigSpec.IntValue HEIGHT_FLIGHT_DIFF_BLOCKS = BUILDER.defineInRange("redios.heightFlightDiffBlocks", 6, 0, 256);

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
    public static final ModConfigSpec.IntValue STARFALL_SALVO_MIN_COUNT = BUILDER
        .comment("繁星爆闪每次召唤星星的最少数量。")
        .defineInRange("redios.starfallSalvoMinCount", 12, 1, 64);
    public static final ModConfigSpec.IntValue STARFALL_SALVO_MAX_COUNT = BUILDER
        .comment("繁星爆闪每次召唤星星的最多数量。")
        .defineInRange("redios.starfallSalvoMaxCount", 16, 1, 64);
    public static final ModConfigSpec.IntValue STARFALL_SALVO_MAX_DELAY_TICKS = BUILDER
        .comment("繁星爆闪每颗星星开始下落的随机最大延迟（tick）。默认 20 = 1 秒。")
        .defineInRange("redios.starfallSalvoMaxDelayTicks", 20, 0, 200);
    public static final ModConfigSpec.DoubleValue STARFALL_SALVO_EXPLOSION_POWER = BUILDER
        .comment("繁星爆闪每颗星星爆炸的威力（大范围随机爆破半径，沿用 Level.explode 口径，",
            "破坏方块遵循 mobGriefing）。默认 6.0，约 6 格爆炸半径，逐星独立覆盖召唤半径内的大范围区域。")
        .defineInRange("redios.starfallSalvoExplosionPower", 6.0, 0.0, 64.0);

    // 2.8 无光失色挑战成功时限：激活后若未在该时限内攻克 Boss，则视为挑战成功
    // （Boss 转为友好生物并走创造离场路径，进入 3 天召唤冷却）。
    // 代码强制下限 = 最后两个二阶段头衔持续时间（2 × P2 头衔锁血时长，默认 60 秒）。
    public static final ModConfigSpec.IntValue COLORLESS_CHALLENGE_SECONDS = BUILDER
        .comment("2.8 无光失色激活后的挑战成功时限（秒）。",
            "激活后若未在该时限内攻克 Boss，则视为挑战成功：Boss 转为友好生物并离场，",
            "进入 3 天召唤冷却。代码强制下限为最后两个二阶段头衔的持续时间",
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

    // 莱德厄斯血条显示方式（客户端渲染）
    // true（默认）= 使用自制分段紫黑血条（对齐原版槽位，不遮挡其它 Boss）；
    // false = 使用原版默认 Boss 血条。
    public static final ModConfigSpec.BooleanValue CUSTOM_BOSS_BAR_ENABLED = BUILDER
        .comment("莱德厄斯血条显示方式。",
            "true（默认）= 使用自制分段紫黑血条（对齐原版槽位，不遮挡其它 Boss）。",
            "false = 使用原版默认 Boss 血条。")
        .define("redios.customBossBarEnabled", true);

    // 拔刀剑 Boss 随机 SA 池排除列表（2026-09-01）：默认排除狐月刀（foxextra）与天杀星刀
    // （tianshaxing）——两者 SA 有 SE 前提（Boss 刀 miedao_duan 无对应 SE 会放不出/异常），
    // 且 foxextra 的 VoidSlashPlus 时间线每帧调 Drive.doSlash + AttackManager.doSlash 生成多条
    // 剑气+刀光（刀光洪峰源）。其余 namespace（slashblade 内置、灭却之日、amazingshine、
    // shinkubloodkatana 等）全部进池，保持全随机精神。
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_SA_EXCLUDED_NAMESPACES = BUILDER
        .comment("Boss 随机施放 SA 时排除的注册表 namespace 列表（黑名单模式，其余全进池）。",
            "默认 [foxextra, tianshaxing]：狐月刀/天杀星刀的 SA 有 SE 前提（Boss 刀无对应 SE 会异常），",
            "且 foxextra 的 VoidSlashPlus 时间线每帧生成多条剑气/刀光（刀光洪峰源）。",
            "其余（slashblade 内置、灭却之日、amazingshine、shinkubloodkatana 等）全部进池。",
            "如需排除更多，把 namespace 加入此列表。修改后最迟 60 秒生效。")
        .defineList("redios.bossSaExcludedNamespaces",
            List.of("foxextra", "tianshaxing"),
            o -> o instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SilentSunConfig() {
    }
}
