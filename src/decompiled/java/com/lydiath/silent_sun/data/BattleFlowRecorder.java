package com.lydiath.silent_sun.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Boss 战斗流程日志报告记录器（2026-09-12 新增，见 {@code docs/实现计划-2026-09-12-战斗流程日志报告.md} §2）。
 * <p>
 * <b>定位：旁路观测器</b>。它不参与任何战斗逻辑——不改条件、不改返回值、不改时序；所有记录与写文件
 * 失败只 {@code warn}，异常绝不允许冒泡到战斗路径。**默认开启**（{@link #isEnabled()} == true，
 * 2026-09-13 作者要求「战斗记录默认开启」）；关闭时 {@code RediosEntity} 不创建本对象（字段为 null），
 * 所有记录点先判 null ⇒ 零开销（命令 {@code /silent_sun battle_report off} 可临时关）。
 * <p>
 * 纯内存记录 + 退场时一次性序列化；不持有实体强引用（只用 UUID / 字符串 / 基本类型）。
 * 记录面只含四类事件（{@code TITLE} / {@code SA_CAST} / {@code PHASE_SETTLE} / {@code ANTICHEAT}）
 * 加一份 SA 池快照。
 * <p>
 * 开关由命令侧控制（{@link #setEnabled(boolean)}）：记录器**在实体构造时**按开关状态创建
 * （已存在的 Boss 不补挂）。⇒ 若无默认开启，{@code on} 只对**之后召唤**的 Boss 生效；
 * 现在默认即为 true，所以每场战斗都会被采集，不必再手动开。
 * <p>
 * <b>开销（2026-09-13 复核）</b>：产出观测**不是每 tick 扫描**，而是在**每次 SA 施放时**扫一次
 * 48 格半径（{@code IntegrationContract.PRODUCTION_SCAN_RADIUS}，调用点在 {@code tryInvokeRandomSA} 内）
 * ⇒ SA 间隔以秒计，代价很小。唯一持续成本是**每场结束写一个几百 KB 的 JSON**。
 */
public final class BattleFlowRecorder {

    private static final Logger LOG = LoggerFactory.getLogger("silent_sun-battle-report");
    /** serializeNulls：让 {@code saCasts[].error} 在「未抛异常」时显式为 null（与设计 §2.2 的样例一致）。 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
        .serializeNulls().create();
    private static final int SCHEMA = 1;
    private static final double TICKS_PER_SECOND = 20.0;
    /**
     * 单个 {@code ANTICHEAT} kind 的条数上限。目的：外部模组「每 tick 直写血量」这类高频篡改
     * 会让 {@code respondToTamper} 每 tick 触发一次，若不设上限，单场报告可膨胀到数万条。
     * 超上限只丢该 kind 的后续条目并累计计数（导出时写进 {@code antiCheatDropped}），
     * 前 500 条已足够看出「何时开始篡改、被什么惩罚、是否被 30s 门压制」。
     */
    private static final int ANTI_CHEAT_MAX_PER_KIND = 500;

    // ── 开关（命令侧接口） ──

    /**
     * 全局开关默认值：**true**（2026-09-13 作者要求「战斗记录默认开启」）。
     * <p>
     * 原为 {@code false}（须手动 {@code /silent_sun battle_report on}）。改为默认开后：
     * ① 每场战斗都会被采集，不必再手动开；② 顺带消除了「{@code on} 对已存在的 Boss 无效」这个坑
     * —— 记录器在实体构造时读本值，默认即为 true ⇒ 任何时候召唤的 Boss 都会建记录器。
     * <p>
     * ⚠️ 记录器是按**实体构造时刻**的状态创建的（{@code RediosEntity} 字段初始化器）⇒ 用命令改成
     * {@code false} 之后，**已在场的 Boss 仍会继续采集并导出**，只是**之后新召唤**的 Boss 不再建记录器。
     */
    private static volatile boolean enabled = true;

    /** 命令侧开关：{@code off} 之后**新召唤**的 Boss 不再建记录器（已在场的照常采完并导出）。 */
    public static void setEnabled(boolean value) {
        enabled = value;
        LOG.info("[SilentSun] 战斗流程报告 {}", value
            ? "已开启（之后召唤的 Boss 开始采集，退场时导出 JSON）"
            : "已关闭（已有记录器的场次照常导出，之后召唤的 Boss 不再采集）");
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ── 事件载体（纯数据，不含实体引用） ──

    /** 头衔推进。{@code phase}/{@code fromPhase} 分开记：头衔推进里也包含 1→2 的换阶段那一跳。 */
    private record TitleEvent(long t, int phase, int fromPhase, int from, int to, boolean forced) {
    }

    /** 收尾事件：kind ∈ vote / transition / defeat / pending / noLootLeave / timedVictory。 */
    private record SettleEvent(long t, int phase, int titleIndex, String kind, String note) {
    }

    /** 反作弊事件：kind ∈ MAX_HEALTH_TAMPER / HEALTH_TAMPER / DEATH_CHEAT / RIDE_PUNISH / TAMPER_PUNISH / REBUILD_COUNTER。 */
    private record AntiCheatEvent(long t, String kind, int phase, int titleIndex, String offender, String punish,
                                  boolean gatedBy30s, String note) {
    }

    /** 单次 SA 施放。{@code ok=false} 时若 {@code error} 为 null，说明是「未能施放」而非抛异常（原因见 note）。 */
    private record SaCastEvent(long t, String saId, int phase, int titleIndex, boolean ok, String error, String note) {
    }

    /**
     * SA 池快照：只在 {@code IntegrationContract.tryInvokeRandomSA} 真正重建池缓存（60s TTL 到期）时抓一次。
     * <p>
     * 2026-09-12（白名单化）：新增 {@code whitelist} —— 池规则由「黑名单」改为「白名单 + 两层二次排除」后，
     * 白名单是**主规则**；不记它，报告就无法回答「按什么规则排的」（验收第 5 条）。
     */
    private record SaPoolSnapshot(long t, List<String> inPool, List<String> excluded, List<String> whitelist,
                                  List<String> namespaces, List<String> saIds) {
    }

    /**
     * combo / tickAction 探针采样点（2026-09-12 新增，见 {@link #comboProbe}）。
     * <p>
     * 只记「变化点」：{@code kind=comboSeqChange}（comboSeq 变了）或
     * {@code kind=fingerprintFirstNonZero}（tickAction 指纹首次出现非零）。
     * {@code lastProcessedTick} 是实体 {@code persistentData} 的 lastProcessedTick 键值 —— 它在整个
     * slashblade 里只有 {@code ComboState$TimeLineTickAction.accept} 一个写入者，故它是否 &gt; 0
     * 直接回答「{@code ComboState.tickAction} 在 Boss 上到底有没有被调用过」。
     */
    private record ComboProbeSample(long t, String kind, String from, String to, boolean selfDriven,
                                    long lastProcessedTick, long elapsed, int timelineFrames, String lastSaId,
                                    String tickActionClass, String clickActionClass) {
    }

    /**
     * 实体产出事件（2026-09-12 新增，见 {@link #entityProduction}）。
     * <p>
     * 背景：作者的实机观察是「有一部分 SA 的对应实体产出成功」，这与「Mob 上 100% 空放」的静态结论冲突。
     * 要定死这件事必须观测**实体**：本条目记录 Boss 周围新出现的模组实体，并带上出现时刻的
     * comboSeq / 指纹 / 是否我方驱动窗口 / 距最近一次 SA 施放的 tick 偏移 —— 这四样合起来就能回答
     * 「产出走的是时间线、clickAction，还是第三条路径」。
     */
    private record ProductionEvent(long t, String type, String category, String owner, boolean selfDriven,
                                   String comboSeq, long fingerprint, long sinceLastSaCastTicks, String lastSaId,
                                   long sinceLastPlayerSlashTicks, String lastPlayerSlashBy,
                                   long sinceLastPlayerChargeTicks, String lastPlayerChargeBy) {
    }

    /** 单次 SA 施放**同调用内**新增的实体（空列表 = 这次 SA 空放）。 */
    private record SaProductionEvent(long t, String saId, List<String> added) {
    }

    /**
     * 玩家受击事件（2026-09-13 新增）。
     * <p>
     * {@code amount} = {@code LivingDamageEvent.Pre} 的 amount（**本会受到的伤害** —— 保命道具把伤害
     * 拦下时这个值仍然有，而 {@code Post} 不会触发，故必须在 Pre 记）；
     * {@code healthBefore} = 受击前血量 ⇒ 两者对比即可看出「这一下是否致命、保命是否被消耗」；
     * {@code sinceLastSaCastTicks} / {@code lastSaId} 与产出观测同源 ⇒ 能把受击对齐到最近一次 SA。
     */
    private record PlayerHitEvent(long t, String player, String damageType, float amount, float healthBefore,
                                  long sinceLastSaCastTicks, String lastSaId) {
    }

    /** 实体产出事件累计（按类型聚合，导出成 {@code entityProduction.byType}）。 */
    private static final class ProdAgg {
        private int count;
        private long firstT = -1L;
        private long lastT = -1L;
        private final java.util.Set<String> owners = new java.util.LinkedHashSet<>();
        private final java.util.Set<String> combos = new java.util.LinkedHashSet<>();
    }

    /** 按 SA id 聚合的施放产出（导出成 {@code entityProduction.bySaId}）：直接回答「哪一部分 SA 真的出了实体」。 */
    private static final class SaProdAgg {
        private int casts;
        private int emptyCasts;
        private int addedTotal;
        private long firstT = -1L;
        private long lastT = -1L;
        private final java.util.Set<String> types = new java.util.LinkedHashSet<>();
    }

    /**
     * 探针判定「combo 已回到待机」的两个 id（只做字符串比较，不引 slashblade 反射 —— 报告器保持零外部依赖）。
     * 与 {@code IntegrationContract} 的 {@code SLASH_ARTS_NONE_ID} / {@code SLASH_BLADE_STANDBY_ID} 同值。
     */
    private static final String COMBO_NONE = "slashblade:none";
    private static final String COMBO_STANDBY = "slashblade:standby";
    /**
     * verdict 判定「活动 combo 时长足够」的阈值（tick）。
     * <p>
     * 60 tick = 3 秒：slashblade 自带 combo_a1..a5 与任何 SA 时间线 combo 都会在这段时间内产生至少一次
     * 时间线帧命中（TimeLineTickAction 一进 combo 就按 elapsed 逐帧推进）。低于此值不下结论，只报
     * {@code inconclusive} —— 避免「采样窗口太短」被误读成结论。
     */
    private static final long COMBO_ACTIVE_TICKS_THRESHOLD = 60L;
    /** comboSeqTrace 条数上限：combo 每段一两条，正常一场战斗远低于此；超限只丢条目（防报告膨胀）。 */
    private static final int COMBO_TRACE_MAX = 2000;
    /** 实体产出 trace 条数上限（与 comboSeqTrace 同理：正常战斗几十~几百条）。 */
    private static final int PRODUCTION_TRACE_MAX = 2000;

    /** 产出观测的扫描参数（由 RediosEntity 告知一次，只用于写进报告便于复现）。 */
    private double productionScanRadius = -1.0;
    private int productionScanIntervalTicks = -1;

    // ── 内存态 ──

    /** 战斗开始 gameTime（相对秒基准）；{@code -1} = 未知，退化为「首个事件时刻」。 */
    private long startGameTime = -1L;
    /** 已导出标记：导出幂等（safeDiscard 与 remove 两条导出路径只写一次文件）。 */
    private boolean flushed = false;
    // 2026-09-13（N2 方案 b）：本场是否由「外部清除 Boss 实体 ⇒ 按账本重建回场」产生。
    // 取代原先 `RediosEntity.rebuildFromRecord` 里 `leaveReason = LeaveReason.ANOMALY` 的
    // 「设完立刻复位」做法 —— 那个标记只服务一行日志，导出时 session.leaveReason 永远是 NONE
    // ⇒「这场是否发生过重建」在战斗记录里**不可检索**（实测三场非法死亡记录全为 leaveReason:NONE
    // 就是同一成因模式）。改为显式布尔字段，随 session 落盘。
    private boolean rebuiltFromRecord = false;

    private final List<TitleEvent> titleFlow = new ArrayList<>();
    private final List<SettleEvent> phaseSettle = new ArrayList<>();
    private final List<AntiCheatEvent> antiCheat = new ArrayList<>();
    private final List<SaCastEvent> saCasts = new ArrayList<>();
    /**
     * 玩家受击事件（2026-09-13 新增，作者要求）。
     * <p>
     * <b>用途</b>：把「Boss 抽到哪个 SA」与「玩家什么时候挨了多重的打」对齐，用于回答
     * 「哪几次 SA 打穿了玩家的保命手段」。原先记录面只有四类事件（TITLE / SA_CAST / PHASE_SETTLE /
     * ANTICHEAT）+ 产出观测，**看不到玩家受伤**。
     * <p>
     * <b>挂钩</b>：{@code CommonEvents} 的 {@code LivingDamageEvent.Pre}，只记**参战玩家**。
     * 用 {@code Pre} 而非 {@code Post}：{@code Pre} 能看到「**本会受到的伤害**」——保命类道具把伤害
     * 拦下时 {@code Post} 不会触发，那样恰恰看不到"保护被消耗"的那一刻。
     * <p>
     * <b>与 SA 对齐</b>：每条带 {@code sinceLastSaCastTicks} 与 {@code lastSaId}，与产出观测同源，
     * 因此能直接判断「这次挨打发生在哪次 SA 之后多少 tick」。
     */
    private final List<PlayerHitEvent> playerHits = new ArrayList<>();
    /** 受击条目上限：玩家受击频率可高于其他事件，故放宽到 2000；超出丢后续并计入 {@code playerHitsDropped}。 */
    private static final int PLAYER_HIT_MAX = 2000;
    private int playerHitsDropped;
    private final Map<String, Integer> antiCheatDropped = new LinkedHashMap<>();
    /** 各 kind 已记录条数（配合 {@link #ANTI_CHEAT_MAX_PER_KIND} 截断，避免每次都遍历列表计数）。 */
    private final Map<String, Integer> antiCheatCountByKind = new LinkedHashMap<>();
    private SaPoolSnapshot saPool = null;

    // ── 2026-09-12（tickAction 探针）内存态：全部由 RediosEntity 每若干 tick 采一次样喂进来 ──

    private final List<ComboProbeSample> comboSeqTrace = new ArrayList<>();
    private int comboProbeSamples;
    /** 采样覆盖的 tick 总数（= 采样次数 × 采样间隔），用于换算「活动 combo 占比」。 */
    private long comboProbeObservedTicks;
    /** comboSeq 非 none/standby 的累计 tick 数：判据的「观测窗口足够长」分母。 */
    private long comboProbeActiveTicks;
    /** tickAction 指纹的历史最大值（> 0 = 铁证 tickAction 跑过）。 */
    private long comboFingerprintMax;
    /** 指纹首次出现非零的相对 tick（未出现为 null）。 */
    private Long comboFingerprintFirstNonZeroT;
    /** 指纹首次非零时的 comboSeq / 是否我方驱动 / 最近一次成功 SA —— 实测里这三样是定位驱动者的关键。 */
    private String comboFingerprintFirstNonZeroCombo;
    private boolean comboFingerprintFirstNonZeroSelfDriven;
    private String comboFingerprintFirstNonZeroSaId;
    /** 指纹首次非零时，该 combo 的 tickAction / clickAction **实际运行时类名**。
     *  用途：指纹只能证明「某个 TimeLineTickAction 被执行过」，配合这两个类名才能判断
     *  是 tickAction 被外部驱动，还是我方 clickAction 恰好是个时间线对象（见 IntegrationContract#comboActionTypes）。 */
    private String comboFingerprintFirstNonZeroTickActionClass;
    private String comboFingerprintFirstNonZeroClickActionClass;
    /** 指纹首次非零时，两个动作对象是否**就是** slashblade 的 TimeLineTickAction 实例
     *  （决定「指纹是不是这两个字段这条链写的」——都要 false 时说明还有第三方直接持有并调用时间线的路径）。 */
    private boolean comboFingerprintFirstNonZeroTickActionIsTimeline;
    private boolean comboFingerprintFirstNonZeroClickActionIsTimeline;
    /** elapsed 最大值（对照指纹用：elapsed 在涨而指纹不动 = 时间线一帧都没跑）。 */
    private long comboElapsedMax;
    /** 观测到的「非空时间线帧数」最大值（> 0 证明该 combo 真的有一份非空时间线）。 */
    private int comboMaxTimelineFrames = -2;
    private int comboSeqChangeCount;
    private int comboSelfDrivenChangeCount;
    private int comboTraceDropped;
    private String comboLastSeq;
    /** 最近一次采样到的指纹值（产出事件要记「出现那一刻的指纹」，不能用历史最大值）。 */
    private long comboLastFingerprint;
    private String comboProbeUnavailable;
    private String comboFingerprintKey;
    private boolean comboFingerprintKeyFromReflection;
    private int comboSampleIntervalTicks;
    /** 对照：持刀玩家身上的同一指纹最大值 + 玩家名（证明指纹机制在当前 jar 上有效）。 */
    private long comboControlFingerprintMax = -1L;
    private String comboControlName;
    private int comboControlSamples;

    // ── 2026-09-12（产出观测）内存态 ──

    private final List<ProductionEvent> productionTrace = new ArrayList<>();
    private final Map<String, ProdAgg> productionByType = new LinkedHashMap<>();
    /** 粗分类（刀光/剑气/次元斩/剑雨/其它）汇总：口径与既有诊断 diagnoseSlashBladeEntityFlood 一致，便于两处对照。 */
    private final Map<String, Integer> productionByCategory = new LinkedHashMap<>();
    private int productionTotal;
    /** 在我方驱动窗口**之外**生成的实体数（= 指纹恒 0 时的「第三条路径」证据）。 */
    private int productionOutsideSelfWindow;
    private int productionTraceDropped;
    private final List<SaProductionEvent> saProductions = new ArrayList<>();
    /** 按 SA id 聚合：{@code casts} / {@code emptyCasts} / {@code addedTotal} 直接回答「哪一部分 SA 真的出了实体」。 */
    private final Map<String, SaProdAgg> saProductionBySaId = new LinkedHashMap<>();
    /**
     * SA「**窗口产出**」归因（2026-09-13 新增）。
     * <p>
     * 为什么需要它：{@code saCastAddedEntities} 量的是「SA 施放**同一次调用内**新增的实体」，而
     * SlashBlade 的 SA 产出是**延迟**的 ⇒ 同拍差恒为 0 ⇒ 会被读成"7 次 SA 全部空放"（实测
     * `battle-a7e79189-446031.json` 就是如此，而同一份记录里 {@code byType} 却有 animated_slash 150 /
     * drive 103 等实体）。**那是统计窗口太窄，不是 SA 无效。**
     * <p>
     * 本聚合改用「最近一次成功 SA 施放后 {@link #SA_PRODUCTION_WINDOW_TICKS} tick 内出现的实体」
     * 归给那次 SA ⇒ 能回答"这次 SA 到底产出了什么"。数据来源与 {@code trace} 里的
     * {@code sinceLastSaCastTicks} / {@code lastSaId} 同源，**没有新采数据**。
     * <p>
     * 局限：按「距最近一次 SA」归因 ⇒ 窗口内若另有一次 SA 施放，产出会算给**更近**的那次；
     * 故本记录同时给出 {@code windowTicks} 与 {@code outsideSelfDriven} 便于判读。
     */
    private final Map<String, Integer> saWindowProductionBySaId = new LinkedHashMap<>();
    /** 窗口产出的 {@code selfDriven=false} 部分（用于区分"我方驱动窗口内"与"完全外部"）。 */
    private final Map<String, Integer> saWindowProductionOutsideBySaId = new LinkedHashMap<>();
    /**
     * SA 产出的归因窗口（tick）。取 40（= 2 秒）：实测 SA 产出延迟通常在 1 秒内，
     * 而本场 SA 施放间隔为 6~50 秒 ⇒ 40 tick 足够吞下延迟、又不会跨到下一次 SA。
     */
    private static final int SA_PRODUCTION_WINDOW_TICKS = 40;
    private int saProductionTotal;
    /** SA 施放新增实体的总数（所有 SaProductionEvent 的 added 之和）。 */
    private int saProductionAddedTotal;
    private int emptySaProductions;
    /** 最近一次 SA_CAST 事件的相对 tick（用于算产出距 SA 施放的偏移；-1 = 本场还没施放过）。 */
    private long lastSaCastRelTick = -1L;
    /**
     * 最近一次**玩家挥刀**（SlashBlade {@code DoSlashEvent}，user 是 Player）的相对 tick 与玩家名。
     * <p>
     * 为什么要这个锚点：第三方模组的 SA 常挂在挥刀/蓄力事件上（例如 recasting 的
     * {@code TimeBeyondSlashArts.onCharge} 就是 {@code @SubscribeEvent}）。当产出落在我方驱动窗口之外
     * 时，靠「距最近一次玩家挥刀多少 tick」就能判断这条第三条路径是不是**玩家侧**触发的。
     * 注意：Boss 自己的 clickAction 也会 post 同一个事件，但 user 是 Mob（非 Player），已被过滤 ⇒
     * 这里只记玩家挥刀，不会有 Boss 侧污染。
     */
    private long lastPlayerSlashRelTick = -1L;
    private String lastPlayerSlashPlayer;
    private int playerSlashCount;
    private long playerSlashFirstT = -1L;
    private long playerSlashLastT = -1L;
    private final java.util.Set<String> playerSlashPlayers = new java.util.LinkedHashSet<>();
    /**
     * 玩家**蓄力**锚点（SlashBlade {@code ChargeActionEvent}，见 {@link #playerCharge}）。
     * 与挥刀锚点互补：第三方 SA 也可能挂在蓄力事件上（如 recasting 的
     * {@code TimeBeyondSlashArts.onCharge}），产出落在我方窗口之外时靠这两条锚点判断是否玩家侧触发。
     */
    private long lastPlayerChargeRelTick = -1L;
    private String lastPlayerChargePlayer;
    private int playerChargeCount;
    /** 蓄力者**就是 Boss 自己**的次数（&gt; 0 ⇒ Boss 在走 onUseTick / holdAction 链 = 第三条路径候选）。 */
    private int bossSelfChargeCount;
    private long playerChargeFirstT = -1L;
    private long playerChargeLastT = -1L;
    private final java.util.Set<String> playerChargePlayers = new java.util.LinkedHashSet<>();

    /** 战斗起点同步（由 {@code RediosEntity} 在已知 {@code battleStartGameTime} 时调用）；已设过则不再覆盖。 */
    public void setStartGameTime(long gameTime) {
        try {
            if (gameTime >= 0L && this.startGameTime < 0L) {
                this.startGameTime = gameTime;
            }
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：起点同步失败：{}", t.toString());
        }
    }

    /** 相对 tick：{@code 当前 gameTime - 战斗开始 gameTime}；起点未知时以首个事件为基准（并落进 session）。 */
    private long rel(long nowGameTime) {
        if (this.startGameTime < 0L) {
            this.startGameTime = nowGameTime;
        }
        return Math.max(0L, nowGameTime - this.startGameTime);
    }

    // ── 记录入口（全部自带 try/catch：记录失败只 warn，绝不影响战斗） ──

    public void title(long nowGameTime, int fromPhase, int fromIndex, int toPhase, int toIndex, boolean forced) {
        try {
            this.titleFlow.add(new TitleEvent(this.rel(nowGameTime), toPhase, fromPhase, fromIndex, toIndex, forced));
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：TITLE 记录失败：{}", t.toString());
        }
    }

    public void saCast(long nowGameTime, String saId, int phase, int titleIndex, boolean ok, String error, String note) {
        try {
            this.saCasts.add(new SaCastEvent(this.rel(nowGameTime), saId, phase, titleIndex, ok, error, note));
            // 2026-09-12（产出观测）：记录施放时刻，供产出事件算「距最近一次 SA 施放的 tick 偏移」。
            this.lastSaCastRelTick = this.rel(nowGameTime);
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：SA_CAST 记录失败：{}", t.toString());
        }
    }

    public void saPool(long nowGameTime, List<String> inPool, List<String> excluded, List<String> whitelist,
                       List<String> namespaces, List<String> saIds) {
        try {
            this.saPool = new SaPoolSnapshot(this.rel(nowGameTime), copy(inPool), copy(excluded),
                copy(whitelist), copy(namespaces), copy(saIds));
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：SA 池快照记录失败：{}", t.toString());
        }
    }

    public void phaseSettle(long nowGameTime, int phase, int titleIndex, String kind, String note) {
        try {
            this.phaseSettle.add(new SettleEvent(this.rel(nowGameTime), phase, titleIndex, kind, note));
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：PHASE_SETTLE 记录失败：{}", t.toString());
        }
    }

    public void antiCheat(long nowGameTime, String kind, int phase, int titleIndex, String offender, String punish,
                          boolean gatedBy30s, String note) {
        try {
            String key = kind == null ? "UNKNOWN" : kind;
            int already = this.antiCheatCountByKind.getOrDefault(key, 0);
            if (already >= ANTI_CHEAT_MAX_PER_KIND) {
                this.antiCheatDropped.merge(key, 1, Integer::sum);
                return;
            }
            this.antiCheatCountByKind.put(key, already + 1);
            this.antiCheat.add(new AntiCheatEvent(this.rel(nowGameTime), kind, phase, titleIndex, offender, punish,
                gatedBy30s, note));
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：ANTICHEAT 记录失败：{}", t.toString());
        }
    }

    // ── 2026-09-12（tickAction 探针）：旁路采样入口 ──

    /**
     * COMBO_PROBE：一次 combo / tickAction 只读采样。
     * <p>
     * 由 {@code RediosEntity} 每 {@code tickSpan} tick 调一次，喂进 {@code IntegrationContract.probeCombo}
     * 的只读快照。**只在变化点记 trace**（comboSeq 变了 / 指纹首次非零），其余只累加统计 ——
     * 一场 20 分钟战斗约 12000 次采样，逐条记录只会让报告失去可读性。
     * <p>
     * 本方法不参与任何战斗判定，失败只 warn。
     *
     * @param tickSpan                    采样间隔（tick），用于把采样数换算成覆盖时长
     * @param comboSeq                    Boss 主手刀当前 combo（读不到为 null）
     * @param elapsed                     {@code ComboState.getElapsed(entity)}（读不到为 -1）
     * @param lastProcessedTick           tickAction 指纹（{@code persistentData} 的 lastProcessedTick）
     * @param timelineFrames              当前 combo 的 tickAction 时间线帧数（-1 不适用 / -2 读不到）
     * @param selfDriven                  本窗口内 slashblade 的 comboSeq 是否由 silent_sun 自己写的
     * @param fingerprintKey              指纹键名（写进报告，便于核对 slashblade 版本）
     * @param fingerprintKeyFromReflection 键名是否来自反射（false = 字面量兜底）
     */
    public void comboProbe(long nowGameTime, int tickSpan, String comboSeq, long elapsed, long lastProcessedTick,
                           int timelineFrames, boolean selfDriven, String fingerprintKey,
                           boolean fingerprintKeyFromReflection, String tickActionClass, String clickActionClass,
                           boolean tickActionIsTimeline, boolean clickActionIsTimeline) {
        try {
            long span = Math.max(1L, tickSpan);
            this.comboProbeSamples++;
            this.comboProbeObservedTicks += span;
            if (this.comboSampleIntervalTicks <= 0) {
                this.comboSampleIntervalTicks = (int) span;
            }
            if (fingerprintKey != null) {
                this.comboFingerprintKey = fingerprintKey;
                this.comboFingerprintKeyFromReflection = fingerprintKeyFromReflection;
            }
            boolean active = comboSeq != null && !COMBO_NONE.equals(comboSeq) && !COMBO_STANDBY.equals(comboSeq);
            if (active) {
                this.comboProbeActiveTicks += span;
            }
            if (timelineFrames > this.comboMaxTimelineFrames) {
                this.comboMaxTimelineFrames = timelineFrames;
            }
            if (elapsed > this.comboElapsedMax) {
                this.comboElapsedMax = elapsed;
            }
            if (lastProcessedTick > this.comboFingerprintMax) {
                this.comboFingerprintMax = lastProcessedTick;
            }
            String lastSaId = this.lastSuccessfulSaId();
            // 指纹首次非零 = 铁证（该键的写入者全部位于 tickAction 执行链上，见 buildTickActionProbe 的说明）。
            // 单独记一条并保存「当时的 comboSeq / 是否我方驱动 / SA id」—— 实测证明这三样才是定位驱动者的关键
            // （2026-09-12 第一场实测：首次非零落在第三方 SA 的 combo 段且 selfDriven=false）。
            if (lastProcessedTick > 0L && this.comboFingerprintFirstNonZeroT == null) {
                long t = this.rel(nowGameTime);
                this.comboFingerprintFirstNonZeroT = t;
                this.comboFingerprintFirstNonZeroCombo = comboSeq;
                this.comboFingerprintFirstNonZeroSelfDriven = selfDriven;
                this.comboFingerprintFirstNonZeroSaId = lastSaId;
                this.comboFingerprintFirstNonZeroTickActionClass = tickActionClass;
                this.comboFingerprintFirstNonZeroClickActionClass = clickActionClass;
                this.comboFingerprintFirstNonZeroTickActionIsTimeline = tickActionIsTimeline;
                this.comboFingerprintFirstNonZeroClickActionIsTimeline = clickActionIsTimeline;
                this.addComboSample(new ComboProbeSample(t, "fingerprintFirstNonZero", comboSeq, comboSeq,
                    selfDriven, lastProcessedTick, elapsed, timelineFrames, lastSaId,
                    tickActionClass, clickActionClass));
            }
            if (!java.util.Objects.equals(this.comboLastSeq, comboSeq)) {
                this.comboSeqChangeCount++;
                if (selfDriven) {
                    this.comboSelfDrivenChangeCount++;
                }
                this.addComboSample(new ComboProbeSample(this.rel(nowGameTime), "comboSeqChange",
                    this.comboLastSeq, comboSeq, selfDriven, lastProcessedTick, elapsed, timelineFrames, lastSaId,
                    tickActionClass, clickActionClass));
            }
            this.comboLastSeq = comboSeq;
            this.comboLastFingerprint = lastProcessedTick;
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：COMBO_PROBE 记录失败：{}", t.toString());
        }
    }

    /**
     * COMBO_PROBE 对照：持刀玩家身上的同一指纹（{@code Inventory} 每 tick 调 {@code inventoryTick}，
     * 故玩家的指纹应 &gt; 0）。用途：证明「指纹机制在当前 jar 上确实可读」——若对照也恒 0，
     * Boss 的 0 就不能当作「tickAction 没被调用」的证据。
     */
    public void comboProbeControl(String playerName, long lastProcessedTick) {
        try {
            this.comboControlSamples++;
            if (lastProcessedTick > this.comboControlFingerprintMax) {
                this.comboControlFingerprintMax = lastProcessedTick;
                this.comboControlName = playerName;
            }
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：COMBO_PROBE 对照记录失败：{}", t.toString());
        }
    }

    /** COMBO_PROBE 不可用（理由只记第一条，避免每采样一次刷一条）。 */
    public void comboProbeUnavailable(String reason) {
        try {
            if (this.comboProbeUnavailable == null) {
                this.comboProbeUnavailable = reason;
            }
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：COMBO_PROBE 不可用标记失败：{}", t.toString());
        }
    }

    // ── 2026-09-12（产出观测）：实体增量入口 ──

    /**
     * ENTITY_PRODUCTION：Boss 周围**新出现**的模组实体（增量观测）。
     * <p>
     * 这是回答「(A) 时间线在跑 / (B) 只走 clickAction / (C) 第三条路径」的关键证据 ——
     * 光看 comboSeq 分不清 (A) 与 (C)；把「实体出现的时刻 + 当时的指纹 + 是否我方驱动窗口 +
     * 距最近一次 SA 施放的偏移」四样对齐，才能分清。
     * <p>
     * 为什么用注册表 id 而不是类名：第三方模组自己的实体类型（非 {@code mods.flammpfeil.slashblade}
     * 包）同样要能标出来 —— 既有诊断 {@code CommonEvents.diagnoseSlashBladeEntityFlood} 只看类名前缀，
     * 会漏掉这一类，而它恰恰可能是「某部分 SA 产出成功」的真实来源。
     *
     * @param type       实体类型注册表 id（如 {@code slashblade:drive}、{@code foxextra:xxx}）
     * @param category   粗分类：刀光 / 剑气 / 次元斩 / 剑雨 / 其它
     * @param owner      归属者名字（读不到为 null；用于剔除「玩家自己挥刀产生的实体」这类污染）
     * @param selfDriven 出现时刻是否落在我方驱动窗口内（我方写过 comboSeq / 推进过 combo 的 ±1 tick）
     */
    public void entityProduction(long nowGameTime, String type, String category, String owner, boolean selfDriven) {
        try {
            long t = this.rel(nowGameTime);
            String typeKey = type == null ? "<unknown>" : type;
            this.productionTotal++;
            if (!selfDriven) {
                this.productionOutsideSelfWindow++;
            }
            ProdAgg agg = this.productionByType.computeIfAbsent(typeKey, k -> new ProdAgg());
            agg.count++;
            this.productionByCategory.merge(category == null ? "其它" : category, 1, Integer::sum);
            if (agg.firstT < 0L) {
                agg.firstT = t;
            }
            agg.lastT = t;
            if (owner != null) {
                agg.owners.add(owner);
            }
            if (this.comboLastSeq != null) {
                agg.combos.add(this.comboLastSeq);
            }
            if (this.productionTrace.size() >= PRODUCTION_TRACE_MAX) {
                this.productionTraceDropped++;
                return;
            }
            long sinceLastSaCast = this.lastSaCastRelTick < 0L ? -1L : t - this.lastSaCastRelTick;
            // 2026-09-13 新增：窗口产出归因 —— 把"距最近一次 SA 施放 ≤ 40 tick 内出现的实体"
            // 归给那次 SA。这是对 saCastAddedEntities（同调用差值）的必要补充：同调用差恒为 0
            // 只说明"产出不在施放那一拍"，不代表 SA 无效。
            if (this.lastSaCastRelTick >= 0L && sinceLastSaCast >= 0L && sinceLastSaCast <= SA_PRODUCTION_WINDOW_TICKS) {
                String saId = this.lastSuccessfulSaId();
                if (saId != null && !saId.isEmpty()) {
                    this.saWindowProductionBySaId.merge(saId, 1, Integer::sum);
                    if (!selfDriven) {
                        this.saWindowProductionOutsideBySaId.merge(saId, 1, Integer::sum);
                    }
                }
            }
            long sincePlayerSlash = this.lastPlayerSlashRelTick < 0L ? -1L : t - this.lastPlayerSlashRelTick;
            long sincePlayerCharge = this.lastPlayerChargeRelTick < 0L ? -1L : t - this.lastPlayerChargeRelTick;
            this.productionTrace.add(new ProductionEvent(t, typeKey, category, owner, selfDriven,
                this.comboLastSeq, this.comboLastFingerprint, sinceLastSaCast, this.lastSuccessfulSaId(),
                sincePlayerSlash, this.lastPlayerSlashPlayer, sincePlayerCharge, this.lastPlayerChargePlayer));
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：ENTITY_PRODUCTION 记录失败：{}", t.toString());
        }
    }

    /**
     * 玩家受击留痕（2026-09-13 新增）。由 {@code CommonEvents} 的 {@code LivingDamageEvent.Pre}
     * 对**参战玩家**调用；记录「本会受到的伤害」与「受击前血量」，并带上距最近一次 SA 的 tick 偏移。
     */
    public void playerHit(long nowGameTime, String player, String damageType, float amount, float healthBefore) {
        try {
            if (this.playerHits.size() >= PLAYER_HIT_MAX) {
                this.playerHitsDropped++;
                return;
            }
            long t = this.rel(nowGameTime);
            long sinceLastSaCast = this.lastSaCastRelTick < 0L ? -1L : t - this.lastSaCastRelTick;
            this.playerHits.add(new PlayerHitEvent(t, player, damageType, amount, healthBefore,
                sinceLastSaCast, this.lastSuccessfulSaId()));
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：PLAYER_HIT 记录失败：{}", t.toString());
        }
    }

    /** 产出观测的扫描参数留痕（由 RediosEntity 在首次扫描时告知一次）。 */
    public void productionScanInfo(double radius, int intervalTicks) {
        try {
            this.productionScanRadius = radius;
            this.productionScanIntervalTicks = intervalTicks;
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：产出观测参数留痕失败：{}", t.toString());
        }
    }

    /**
     * 玩家挥刀锚点（2026-09-12）：SlashBlade {@code DoSlashEvent} 且 user 是 Player 时调用一次。
     * <p>
     * 作用：产出事件带上「距最近一次玩家挥刀多少 tick」后，才能判断 (C) 那条第三条路径是否**玩家侧**触发
     * （第三方 SA 常挂在挥刀/蓄力事件上）。Boss 自己的 clickAction 也 post 同一事件，但 user 是 Mob
     * 已被上游过滤 ⇒ 本锚点不含 Boss 侧污染。
     */
    public void playerSlash(long nowGameTime, String playerName) {
        try {
            long t = this.rel(nowGameTime);
            this.playerSlashCount++;
            this.lastPlayerSlashRelTick = t;
            this.lastPlayerSlashPlayer = playerName;
            if (this.playerSlashFirstT < 0L) {
                this.playerSlashFirstT = t;
            }
            this.playerSlashLastT = t;
            if (playerName != null) {
                this.playerSlashPlayers.add(playerName);
            }
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：玩家挥刀锚点记录失败：{}", t.toString());
        }
    }

    /**
     * 蓄力锚点（2026-09-12）：SlashBlade {@code ChargeActionEvent} 状态变化时调用。
     * <p>
     * {@code ChargeActionEvent} 在蓄力期间**每 tick** 都会 post，故调用方只在 gameTime 跳变 &gt; 2 tick
     * 时传 {@code newCharge=true}（＝新一次蓄力），其余只刷新「最近一次蓄力时刻」——否则报告会膨胀
     * 成每 tick 一条。
     * <p>
     * <b>蓄力者可能是 Boss 自己</b>：该事件的持有者是任意 {@code LivingEntity}，第三方处理（recasting 的
     * {@code onCharge}）**不检查是否玩家**。若 {@code isBoss=true}，说明 Boss 正在走
     * {@code ItemSlashBlade.onUseTick} 链（{@code holdAction} 出口）—— 这是「第三条路径」的关键候选，
     * 因为 silent_sun 自己从不 {@code startUsingItem}（全库零调用）。
     */
    public void playerCharge(long nowGameTime, String entityName, boolean isBoss, boolean newCharge) {
        try {
            long t = this.rel(nowGameTime);
            this.lastPlayerChargeRelTick = t;
            this.lastPlayerChargePlayer = entityName;
            this.playerChargeLastT = t;
            if (newCharge) {
                this.playerChargeCount++;
                if (isBoss) {
                    this.bossSelfChargeCount++;
                }
                if (this.playerChargeFirstT < 0L) {
                    this.playerChargeFirstT = t;
                }
                if (entityName != null) {
                    this.playerChargePlayers.add(entityName);
                }
            }
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：玩家蓄力锚点记录失败：{}", t.toString());
        }
    }

    /**
     * SA_PRODUCTION：某次 SA 施放**同一次调用内**新增的实体（空列表 = 这次 SA 空放）。
     * <p>
     * 时间线产物出现在后续 tick，不可能落进「同一次调用的前后快照差」里 ⇒ 本条目非空即证明
     * {@code doArts → updateComboSeq → clickAction / releaseAction} 这条链确实产出了实体。
     */
    public void saProduction(long nowGameTime, String saId, List<String> added) {
        try {
            long t = this.rel(nowGameTime);
            List<String> snapshot = added == null ? List.of() : new ArrayList<>(added);
            this.saProductionTotal++;
            this.saProductionAddedTotal += snapshot.size();
            if (snapshot.isEmpty()) {
                this.emptySaProductions++;
            }
            SaProdAgg agg = this.saProductionBySaId.computeIfAbsent(saId == null ? "<unknown>" : saId,
                k -> new SaProdAgg());
            agg.casts++;
            agg.addedTotal += snapshot.size();
            if (snapshot.isEmpty()) {
                agg.emptyCasts++;
            }
            if (agg.firstT < 0L) {
                agg.firstT = t;
            }
            agg.lastT = t;
            for (String s : snapshot) {
                int at = s.indexOf('@');
                agg.types.add(at < 0 ? s : s.substring(0, at));
            }
            if (this.saProductions.size() < PRODUCTION_TRACE_MAX) {
                this.saProductions.add(new SaProductionEvent(t, saId, snapshot));
            }
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告：SA_PRODUCTION 记录失败：{}", t.toString());
        }
    }

    /** 最近一次**成功施放**的 SA id：让 trace 的每条 combo 变化都能对上「哪次 SA 下发的」。 */
    private String lastSuccessfulSaId() {
        for (int i = this.saCasts.size() - 1; i >= 0; i--) {
            SaCastEvent e = this.saCasts.get(i);
            if (e.ok()) {
                return e.saId();
            }
        }
        return null;
    }

    private void addComboSample(ComboProbeSample sample) {
        if (this.comboSeqTrace.size() >= COMBO_TRACE_MAX) {
            this.comboTraceDropped++;
            return;
        }
        this.comboSeqTrace.add(sample);
    }

    // ── 导出 ──

    /**
     * 序列化落盘并清空记录器。<b>幂等</b>——{@code safeDiscard()}（全部结算/离场路径）与
     * {@code remove(RemovalReason)}（die() 的 vanilla 死亡移除、discard 兜底）都会调它，只写一次。
     * 任何失败只 warn。
     */
    /**
     * 标记本场由账本重建回场（外部清除 Boss 实体）。2026-09-13（N2 方案 b）。
     * <p>
     * 取代原「{@code leaveReason = LeaveReason.ANOMALY} 设完即复位」的做法 —— 那个标记只服务一行日志，
     * 导出时 {@code session.leaveReason} 恒为 {@code NONE} ⇒「这场是否发生过重建」**不可检索**。
     */
    public void markRebuiltFromRecord() {
        this.rebuiltFromRecord = true;
    }

    /** {@return 本场是否由账本重建回场} */
    public boolean isRebuiltFromRecord() {
        return this.rebuiltFromRecord;
    }

    public void export(UUID bossId, long endGameTime, String leaveReason, List<String> participants,
                       boolean playerOnlyMode) {
        if (this.flushed) {
            return;
        }
        this.flushed = true;
        try {
            if (bossId == null) {
                return;
            }
            JsonObject root = this.buildRoot(bossId, endGameTime, leaveReason, participants, playerOnlyMode);
            Path file = this.resolveOutputFile(bossId);
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            LOG.info("[SilentSun] 战斗流程报告已导出：{}（头衔 {} / 收尾 {} / 反作弊 {} / SA 施放 {} / combo 采样 {} 条）",
                file, this.titleFlow.size(), this.phaseSettle.size(), this.antiCheat.size(), this.saCasts.size(),
                this.comboProbeSamples);
            // 2026-09-12（tickAction 探针）：结论也打一行到日志，作者不必打开 JSON 就能看到判据。
            JsonObject probe = this.buildTickActionProbe();
            LOG.info("[SilentSun] tickAction 探针结论：{} —— {}", probe.get("verdict").getAsString(),
                probe.get("verdictBasis").getAsString());
            LOG.info("[SilentSun] 产出路径判定：{} —— {}", probe.get("pathVerdict").getAsString(),
                probe.get("pathBasis").getAsString());
            if (!this.antiCheatDropped.isEmpty()) {
                LOG.warn("[SilentSun] 战斗流程报告：部分反作弊条目因超过单 kind 上限（{}）被丢弃：{}",
                    ANTI_CHEAT_MAX_PER_KIND, this.antiCheatDropped);
            }
        } catch (Throwable t) {
            LOG.warn("[SilentSun] 战斗流程报告导出失败（不影响战斗）：{}", t.toString());
        } finally {
            this.clear();
        }
    }

    private void clear() {
        this.titleFlow.clear();
        this.phaseSettle.clear();
        this.antiCheat.clear();
        this.saCasts.clear();
        this.antiCheatDropped.clear();
        this.antiCheatCountByKind.clear();
        this.saPool = null;
        this.comboSeqTrace.clear();
        this.comboFingerprintFirstNonZeroTickActionClass = null;
        this.comboFingerprintFirstNonZeroClickActionClass = null;
        this.comboFingerprintFirstNonZeroTickActionIsTimeline = false;
        this.comboFingerprintFirstNonZeroClickActionIsTimeline = false;
        this.productionTrace.clear();
        this.productionByType.clear();
        this.productionByCategory.clear();
        this.saProductions.clear();
        this.saProductionBySaId.clear();
        this.playerSlashCount = 0;
        this.playerSlashFirstT = -1L;
        this.playerSlashLastT = -1L;
        this.lastPlayerSlashRelTick = -1L;
        this.lastPlayerSlashPlayer = null;
        this.playerSlashPlayers.clear();
        this.playerChargeCount = 0;
        this.bossSelfChargeCount = 0;
        this.playerChargeFirstT = -1L;
        this.playerChargeLastT = -1L;
        this.lastPlayerChargeRelTick = -1L;
        this.lastPlayerChargePlayer = null;
        this.playerChargePlayers.clear();
    }

    /** 输出目录：游戏实例根目录下 {@code logs/silent_sun/}；取不到则退化到系统临时目录并 warn。 */
    private Path reportDir() {
        try {
            return FMLPaths.GAMEDIR.get().resolve("logs").resolve("silent_sun");
        } catch (Throwable t) {
            Path fallback = Paths.get(System.getProperty("java.io.tmpdir", ".")).resolve("silent_sun")
                .resolve("logs");
            LOG.warn("[SilentSun] 战斗流程报告：无法定位游戏实例根目录（FMLPaths.GAMEDIR 异常：{}），"
                + "改写到临时目录 {}", t.toString(), fallback);
            return fallback;
        }
    }

    /**
     * 玩家受击事件数组（2026-09-13 新增）。判读：
     * ① 与 {@code saCasts} 按 {@code sinceLastSaCastTicks} 对齐 ⇒「这次挨打在哪次 SA 之后多少 tick」；
     * ② {@code amount >= healthBefore} ⇒ 这一下**本该致命**（若玩家没死，说明保命手段被消耗了一次）；
     * ③ {@code lastSaId} 为空 / {@code sinceLastSaCastTicks} 为 -1 ⇒ 挨打与任何 SA 无关（普通攻击 / 光环 / 第三方）。
     */
    private JsonArray buildPlayerHits() {
        JsonArray arr = new JsonArray();
        for (PlayerHitEvent e : this.playerHits) {
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("player", e.player());
            o.addProperty("damageType", e.damageType());
            o.addProperty("amount", e.amount());
            o.addProperty("healthBefore", e.healthBefore());
            o.addProperty("lethal", e.amount() >= e.healthBefore());
            o.addProperty("sinceLastSaCastTicks", e.sinceLastSaCastTicks());
            o.addProperty("lastSaId", e.lastSaId());
            arr.add(o);
        }
        return arr;
    }

    private Path resolveOutputFile(UUID bossId) {
        String id = bossId.toString();
        String shortId = id.length() >= 8 ? id.substring(0, 8) : id;
        long start = Math.max(0L, this.startGameTime);
        return this.reportDir().resolve("battle-" + shortId + "-" + start + ".json");
    }

    private JsonObject buildRoot(UUID bossId, long endGameTime, String leaveReason, List<String> participants,
                                 boolean playerOnlyMode) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.add("session", this.buildSession(bossId, endGameTime, leaveReason, participants, playerOnlyMode));
        root.add("saPool", this.buildSaPool());
        root.add("titleFlow", this.buildTitleFlow());
        root.add("phaseSettle", this.buildPhaseSettle(leaveReason, participants));
        root.add("antiCheat", this.buildAntiCheat());
        // 2026-09-13 新增：玩家受击留痕 —— 用于把「哪次 SA」与「什么时候挨了多重的打」对齐。
        root.add("playerHits", this.buildPlayerHits());
        root.addProperty("playerHitsDropped", this.playerHitsDropped);
        root.add("saCasts", this.buildSaCasts());
        root.add("saStats", this.buildSaStats());
        root.add("tickActionProbe", this.buildTickActionProbe());
        root.add("comboSeqTrace", this.buildComboSeqTrace());
        root.add("entityProduction", this.buildEntityProduction());
        root.add("saProductions", this.buildSaProductions());
        if (!this.antiCheatDropped.isEmpty()) {
            JsonObject dropped = new JsonObject();
            for (Map.Entry<String, Integer> e : this.antiCheatDropped.entrySet()) {
                dropped.addProperty(e.getKey(), e.getValue());
            }
            root.add("antiCheatDropped", dropped);
        }
        return root;
    }

    private JsonObject buildSession(UUID bossId, long endGameTime, String leaveReason, List<String> participants,
                                    boolean playerOnlyMode) {
        JsonObject session = new JsonObject();
        session.addProperty("bossId", bossId.toString());
        session.addProperty("startGameTime", this.startGameTime);
        session.addProperty("endGameTime", endGameTime);
        session.addProperty("durationTicks", this.startGameTime < 0L ? 0L : Math.max(0L, endGameTime - this.startGameTime));
        session.addProperty("result", deriveResult(leaveReason));
        session.addProperty("leaveReason", leaveReason == null ? "NONE" : leaveReason);
        // 2026-09-13（N2 方案 b）：显式落盘「本场是否发生过重建回场」。
        // 原实现靠 leaveReason=ANOMALY 传递该信息，但那个值在同一次调用内就被复位成 NONE ⇒ 永远读不到。
        session.addProperty("rebuiltFromRecord", this.rebuiltFromRecord);
        JsonArray parts = new JsonArray();
        if (participants != null) {
            for (String p : participants) {
                parts.add(p);
            }
        }
        session.add("participants", parts);
        session.addProperty("participantCount", participants == null ? 0 : participants.size());
        session.addProperty("playerOnlyMode", playerOnlyMode);
        return session;
    }

    /**
     * 离场结果归纳（设计 §2.2 的 result 枚举）。
     * <p>
     * 判据：① {@code leaveReason=CHUNK_UNLOAD} 优先 → {@code chunkUnload}；
     * ② 出现 {@code timedVictory} → {@code victory}（**须先于 defeat 判**，理由见下）；
     * ③ 走过结算/击杀（{@code phaseSettle} 里出现 {@code defeat}）→ {@code defeated}；
     * ④ 只走了无掉落离场（{@code noLootLeave}）→ {@code noLootLeave}；
     * ⑤ 什么都没记到（例如区块卸载直接冻结实体，未走任何退场路径）→ {@code walkAway}。
     * <p>
     * <b>2026-09-12（用户裁决）新增 {@code victory} 档</b>：2.8「无色挑战」计时到点 = <b>二阶段胜利</b>，
     * 但它同样走 {@code settleBattle}，而后者对所有结算路径统一记 {@code kind="defeat"} ⇒
     * 不特判就会把胜利归成「被击败」，与玩家击杀 / 投票否决 / 区块超时同档。
     * 解法是不动 {@code settleBattle}（避免影响其余路径），改由
     * {@code RediosEntity.resolveColorlessChallengeSuccess} 在调它**之前**先记一条
     * {@code timedVictory}，本方法据此优先返回 {@code victory}。
     * <p>
     * 注意：投票否决离场也走 {@code settleBattle}（发一阶段奖励 + 设冷却），因此同样归入
     * {@code defeated}——要区分具体收尾方式请看 {@code phaseSettle} 的 kind/note 与 leaveReason。
     * {@code CHUNK_UNLOAD} 保持最高优先：2.8/2.9 期间走远导致区块卸载的场次仍记 {@code chunkUnload}
     * （其 {@code phaseSettle} 里照样能看到 {@code timedVictory} 条目，成就 phase2_countdown 与
     * 二阶段奖励照发），这样不改变既有「走远优先」的判定。
     */
    private String deriveResult(String leaveReason) {
        if ("CHUNK_UNLOAD".equals(leaveReason)) {
            return "chunkUnload";
        }
        boolean victory = false;
        boolean defeated = false;
        boolean noLoot = false;
        for (SettleEvent e : this.phaseSettle) {
            if ("timedVictory".equals(e.kind())) {
                victory = true;
            } else if ("defeat".equals(e.kind())) {
                defeated = true;
            } else if ("noLootLeave".equals(e.kind())) {
                noLoot = true;
            }
        }
        if (victory) {
            return "victory";
        }
        if (defeated) {
            return "defeated";
        }
        if (noLoot) {
            return "noLootLeave";
        }
        return "walkAway";
    }

    private JsonObject buildSaPool() {
        JsonObject out = new JsonObject();
        SaPoolSnapshot snap = this.saPool;
        if (snap == null) {
            out.addProperty("capturedAt", -1L);
            out.add("rule", new JsonObject());
            out.add("inPool", new JsonArray());
            out.add("excluded", new JsonArray());
            return out;
        }
        out.addProperty("capturedAt", ticksToSeconds(snap.t()));
        JsonObject rule = new JsonObject();
        // 2026-09-12（白名单化）：whitelist 是主规则，必须记 —— 报告靠它回答「池为什么是这些」。
        rule.add("whitelistNamespaces", toArray(snap.whitelist()));
        rule.add("excludedNamespaces", toArray(snap.namespaces()));
        rule.add("excludedSaIds", toArray(snap.saIds()));
        out.add("rule", rule);
        out.add("inPool", toArray(snap.inPool()));
        out.add("excluded", toArray(snap.excluded()));
        return out;
    }

    private JsonArray buildTitleFlow() {
        JsonArray arr = new JsonArray();
        for (TitleEvent e : this.titleFlow) {
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("phase", e.phase());
            o.addProperty("fromPhase", e.fromPhase());
            o.addProperty("from", e.from());
            o.addProperty("to", e.to());
            o.addProperty("forced", e.forced());
            arr.add(o);
        }
        return arr;
    }

    private JsonArray buildPhaseSettle(String leaveReason, List<String> participants) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < this.phaseSettle.size(); i++) {
            SettleEvent e = this.phaseSettle.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("phase", e.phase());
            o.addProperty("titleIndex", e.titleIndex());
            o.addProperty("kind", e.kind());
            if (e.note() != null) {
                o.addProperty("note", e.note());
            }
            // 最后一条收尾 = 本场最终离场方式：补上设计 §2.2 要求的 leaveReason 与参战人数。
            if (i == this.phaseSettle.size() - 1) {
                o.addProperty("final", true);
                o.addProperty("leaveReason", leaveReason == null ? "NONE" : leaveReason);
                o.addProperty("participants", participants == null ? 0 : participants.size());
            }
            arr.add(o);
        }
        return arr;
    }

    private JsonArray buildAntiCheat() {
        JsonArray arr = new JsonArray();
        for (AntiCheatEvent e : this.antiCheat) {
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("kind", e.kind());
            o.addProperty("phase", e.phase());
            o.addProperty("titleIndex", e.titleIndex());
            if (e.offender() != null) {
                o.addProperty("offender", e.offender());
            }
            o.addProperty("punish", e.punish());
            o.addProperty("gatedBy30s", e.gatedBy30s());
            if (e.note() != null) {
                o.addProperty("note", e.note());
            }
            arr.add(o);
        }
        return arr;
    }

    private JsonArray buildSaCasts() {
        JsonArray arr = new JsonArray();
        for (SaCastEvent e : this.saCasts) {
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("saId", e.saId());
            o.addProperty("phase", e.phase());
            o.addProperty("titleIndex", e.titleIndex());
            o.addProperty("ok", e.ok());
            if (e.error() == null) {
                o.add("error", com.google.gson.JsonNull.INSTANCE);
            } else {
                o.addProperty("error", e.error());
            }
            if (e.note() != null) {
                o.addProperty("note", e.note());
            }
            arr.add(o);
        }
        return arr;
    }

    /** 聚合段：由 {@code saCasts} 累加得出（冗余换取易读，与设计 §2.2 一致）。 */
    private JsonArray buildSaStats() {
        Map<String, SaAgg> agg = new LinkedHashMap<>();
        for (SaCastEvent e : this.saCasts) {
            String id = e.saId() == null ? "<unknown>" : e.saId();
            SaAgg a = agg.computeIfAbsent(id, k -> new SaAgg());
            a.count++;
            if (a.firstT < 0L) {
                a.firstT = e.t();
            }
            a.lastT = e.t();
            a.phases.add(e.phase());
            if (!e.ok()) {
                a.failures++;
            }
        }
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, SaAgg> entry : agg.entrySet()) {
            SaAgg a = entry.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("saId", entry.getKey());
            o.addProperty("count", a.count);
            o.addProperty("firstT", ticksToSeconds(a.firstT < 0L ? 0L : a.firstT));
            o.addProperty("lastT", ticksToSeconds(a.lastT < 0L ? 0L : a.lastT));
            JsonArray phases = new JsonArray();
            for (Integer p : new TreeSet<>(a.phases)) {
                phases.add(p);
            }
            o.add("phases", phases);
            o.addProperty("failures", a.failures);
            arr.add(o);
        }
        return arr;
    }

    /**
     * tickActionProbe：**结论段**（原始序列在 {@code comboSeqTrace}）。
     * <p>
     * 回答的问题：持刀 Mob（本模组 Boss）身上，SlashBlade 的 {@code ComboState.tickAction} 到底会不会被调用？
     * <p>
     * 判据（按优先级）：
     * <ol>
     *   <li>{@code fingerprintMax > 0} ⇒ {@code tickActionDriven}（铁证）。
     *       指纹键 {@code slashblade.lastProcessedTick} 在整个 slashblade 里只有一个写入者：
     *       {@code ComboState$TimeLineTickAction.accept}，而它只在 {@code ComboState.tickAction(entity)}
     *       被调用时才执行；写入发生在**推进一帧之后**。</li>
     *   <li>{@code fingerprintMax == 0} 且 {@code maxTimelineFrames > 0} 且活动 combo 时长 ≥ 阈值
     *       ⇒ {@code tickActionNotDriven}：有非空时间线、有活动 combo，但指纹从未被写过
     *       ⇒ 没有任何驱动者调用 tickAction。</li>
     *   <li>其余情况一律 {@code inconclusive}，并在 {@code verdictBasis} 里说明差在哪（窗口太短 /
     *       combo 时间线为空 / 时间线类型不适用 / 反射读不到），避免假阴性被当成结论。</li>
     * </ol>
     */
    private JsonObject buildTickActionProbe() {
        JsonObject out = new JsonObject();
        boolean mechanismVerified = this.comboControlFingerprintMax > 0L;
        String verdict;
        StringBuilder basis = new StringBuilder();
        if (this.comboProbeSamples == 0) {
            verdict = "noData";
            basis.append("本场未采到任何 combo 样本（记录器随 Boss 创建 ⇒ 只有开关打开后新开战的那一场才采集）。");
        } else if (this.comboFingerprintMax > 0L) {
            verdict = "tickActionDriven";
            basis.append("指纹 ").append(this.comboFingerprintKey).append(" 最大值为 ")
                .append(this.comboFingerprintMax).append("（> 0）⇒ ComboState.tickAction 在 Boss 上确实被执行过，")
                .append("时间线确实推进过（第三方 SA 在 Boss 上会真的产生实体）。");
            if (this.comboFingerprintFirstNonZeroT != null) {
                basis.append(" 首次非零：t=").append(ticksToSeconds(this.comboFingerprintFirstNonZeroT))
                    .append("s，当时 comboSeq=").append(this.comboFingerprintFirstNonZeroCombo)
                    .append("，selfDriven=").append(this.comboFingerprintFirstNonZeroSelfDriven)
                    .append(this.comboFingerprintFirstNonZeroSaId == null ? ""
                        : "，最近一次成功 SA=" + this.comboFingerprintFirstNonZeroSaId)
                    .append(this.comboFingerprintFirstNonZeroSelfDriven
                        ? "（我方下发那一拍——注意 selfDriven 是 ±1 tick 窗口标记，不能据此排除 slashblade 自驱）"
                        : "（**不是我方下发的那一拍** ⇒ 该段的时间线推进由 slashblade/第三方自驱完成）")
                    .append("。");
            }
            // 关键：该键在每次 applyComboSeq（＝切 combo）时被 slashblade 主动 remove ⇒ 指纹 > 0 是
            // 「当前/最近 combo 段内」的证据，不是远古残留 —— 这条直接封掉「会不会是几小时前写的」的质疑。
            basis.append(" 写入者核查（javap 全量）：该键只有两处**写**，且都在 tickAction 执行链上 —— ")
                .append("ComboState$TimeLineTickAction.accept（推进一帧后 putInt）与 ")
                .append("ComboState$TickAction.lambda$andThen$0（保存/延迟写回）；")
                .append("另有**三处清零**（remove）：ISlashBladeState 的 applyComboSeq（切 combo）、")
                .append("BladeRuntimeSyncer.lambda$onBladeMotion$0（BladeMotionEvent 同步）。")
                .append("故指纹 > 0 是「当前 combo 段内时间线推进过」的**近期**证据，不是历史残留。");
        } else if (this.comboMaxTimelineFrames > 0) {
            if (this.comboProbeActiveTicks >= COMBO_ACTIVE_TICKS_THRESHOLD) {
                verdict = "tickActionNotDriven";
                basis.append("观测到活动 combo（非 none/standby）累计 ").append(this.comboProbeActiveTicks)
                    .append(" tick，其中曾出现带 ").append(this.comboMaxTimelineFrames)
                    .append(" 帧非空时间线的 combo（其 tickAction 就是 TimeLineTickAction），但指纹 ")
                    .append(this.comboFingerprintKey).append(" 始终为 0（从未被写过）⇒ 没有任何驱动者调用 ")
                    .append("ComboState.tickAction：第三方 SA 在 Boss 上是**空放**（只跑 clickAction，时间线一帧不跑）。");
            } else {
                verdict = "inconclusive";
                basis.append("时间线存在（最大 ").append(this.comboMaxTimelineFrames).append(" 帧）但活动 combo 仅累计 ")
                    .append(this.comboProbeActiveTicks).append(" tick（< ").append(COMBO_ACTIVE_TICKS_THRESHOLD)
                    .append("）⇒ 窗口太短，不下结论。请打一场更长的战斗（多甩几个 SA）后复看。");
            }
        } else if (this.comboMaxTimelineFrames == 0) {
            verdict = "inconclusive";
            basis.append("Boss 只进过空时间线的 combo（帧数 0，即 ComboState.EMPTY_TICK_ACTION），"
                + "而空时间线本来就不写指纹 ⇒ 无法区分。请让 Boss 施放一个带时间线的 SA 后再看。");
        } else if (this.comboMaxTimelineFrames == -1) {
            verdict = "inconclusive";
            basis.append("观测到的 combo **找不到时间线**（帧数 -1：连 tickAction 的捕获字段里也没有 "
                + "TimeLineTickAction，或组合体拆不开）⇒ 指纹机制对它不适用，无法判定。");
        } else {
            verdict = "inconclusive";
            basis.append("读不到活动 combo 的帧数信息（-2：探针反射未解析 / comboSeq 不在 combo_state 注册表），"
                + "且指纹恒 0 ⇒ 无法区分「tickAction 未被调用」与「探针读不到」。请核对日志中的探针降级告警。");
        }
        if (mechanismVerified) {
            basis.append(" 对照：持刀玩家 ")
                .append(this.comboControlName == null ? "<未知>" : this.comboControlName)
                .append(" 的同一指纹为 ").append(this.comboControlFingerprintMax)
                .append("（> 0）⇒ 指纹机制在当前 jar 上确实可读，Boss 的 0 是**真 0**。");
        } else if (this.comboControlSamples > 0) {
            basis.append(" 对照：本场持刀玩家的同一指纹也恒为 0（采样 ").append(this.comboControlSamples)
                .append(" 次）⇒ 指纹机制**未获验证**，Boss 的 0 须存疑（先让玩家持拔刀剑挥几刀后复测）。");
        } else {
            basis.append(" 对照：本场无持刀玩家样本 ⇒ 指纹机制未获验证（判据仍成立，但少一层交叉验证）。");
        }
        out.addProperty("verdict", verdict);
        out.addProperty("verdictBasis", basis.toString());

        // ── (A)/(B)/(C) 路径判定（2026-09-12，作者新情报后新增）：把「时间线是否在跑」与
        //    「实体有没有产出来」两个事实交叉。只看 comboSeq 分不清 (A) 与 (C)，必须带实体证据。──
        String pathVerdict;
        StringBuilder pathBasis = new StringBuilder();
        boolean timelineRunning = this.comboFingerprintMax > 0L;
        if (timelineRunning) {
            pathVerdict = "A_timelineExecuting";
            pathBasis.append("(A) 时间线在执行：指纹 ").append(this.comboFingerprintKey).append(" 最大 ")
                .append(this.comboFingerprintMax).append("（> 0）⇒ ComboState.tickAction 被调用过、时间线帧在推进。")
                .append("本场实体产出 ").append(this.productionTotal)
                .append(" 个（既有时间线逐帧产出，也可能含 clickAction 产出 —— 两者可并存）。");
        } else if (this.productionTotal == 0) {
            pathVerdict = "B_noProduction";
            pathBasis.append("(B) 只走 clickAction，且**本场无实体产出**：指纹恒 0，Boss 周围也没出现任何模组实体。");
            if (this.comboProbeActiveTicks < COMBO_ACTIVE_TICKS_THRESHOLD) {
                pathBasis.append(" 但活动 combo 仅累计 ").append(this.comboProbeActiveTicks).append(" tick（< ")
                    .append(COMBO_ACTIVE_TICKS_THRESHOLD).append("）⇒ 窗口偏短，建议打长一点复测。");
            }
        } else if (this.productionOutsideSelfWindow > 0) {
            pathVerdict = "C_thirdPathProduction";
            pathBasis.append("(C) 产出走**第三条路径**：指纹恒 0（时间线没跑），但有 ")
                .append(this.productionOutsideSelfWindow).append(" 个实体在我方驱动窗口**之外**生成（总产出 ")
                .append(this.productionTotal).append(" 个）。")
                .append("定位方式：对照 entityProduction.byType（哪一类实体、何时首次出现）与 saProductions"
                    + "（那次 SA 同调用内产出了什么）—— 若某类实体的首现时刻与某次 SA 施放严格对齐，"
                    + "则该 SA 走的是 clickAction/releaseAction 之外的出口；若与任何 SA 施放都对不上，"
                    + "则驱动者不在 silent_sun 侧（第三方模组自己的事件监听器 / 调度器）。"
                    + "（selfDriven 是窗口级标记 ⇒ 本判定偏保守：会漏判同窗口内第三方与我方并存的情况，"
                    + "但不会把 clickAction 产物误报成第三条路径。）");
        } else {
            pathVerdict = "B_clickActionOnly";
            pathBasis.append("(B) 只走 clickAction：指纹恒 0（时间线没跑），全部 ").append(this.productionTotal)
                .append(" 个实体都出现在我方驱动窗口内（updateComboSeq / progressCombo 那一拍的动作产物）。")
                .append(" comboSeq 变化 ").append(this.comboSeqChangeCount).append(" 次，其中我方写入 ")
                .append(this.comboSelfDrivenChangeCount).append(" 次。");
        }
        if (!timelineRunning && this.comboSeqChangeCount > this.comboSelfDrivenChangeCount) {
            pathBasis.append(" 注：comboSeq 有 ").append(this.comboSeqChangeCount - this.comboSelfDrivenChangeCount)
                .append(" 次变化落在我方窗口外 —— 但 comboSeq 变化**不等于**时间线在跑"
                    + "（resolvCurrentComboState 的超时迁移同样会改它），判定仍以指纹为准。");
        }
        if (this.bossSelfChargeCount > 0) {
            pathBasis.append(" ★关键线索：本场观测到 **Boss 自己**触发了 ").append(this.bossSelfChargeCount)
                .append(" 次蓄力事件（ChargeActionEvent 的持有者就是 Boss）⇒ Boss 在走 ItemSlashBlade.onUseTick ")
                .append("链，而该链的动作出口是 holdAction（不是 clickAction、也不是 tickAction）—— ")
                .append("这很可能就是那条第三条路径的直接来源。");
        }
        out.addProperty("pathVerdict", pathVerdict);
        out.addProperty("pathBasis", pathBasis.toString());

        // 读法指南（写给作者）：字段多且易误读，故把「看哪三个字段」直接写进报告。
        out.addProperty("readGuide",
            "判定只看三处：① fingerprintMax > 0 ⇒ 时间线在跑（tickAction 被调用过）；"
                + "② fingerprintMax = 0 且 maxTimelineFrames > 0 且 activeComboTicks >= activeComboThresholdTicks "
                + "⇒ tickAction 从未被调用（第三方 SA 在 Boss 上是空放，只跑 clickAction）；"
                + "③ control.fingerprintMechanismVerified = true 才代表指纹机制在当前 jar 上有效"
                + "（对照 = 持刀玩家的同一指纹 > 0，玩家物品栏每 tick 调 inventoryTick）。"
                + " 注意 elapsedMax 是 combo 已过帧数（= gameTime - lastActionTime，随时间自然增长），"
                + "**不能**用来判断时间线是否在跑；maxTimelineFrames 现已**能拆 andThen 组合体**读到帧数"
                + "（2026-09-12 修复「时间轴查找」：递归拆 tickAction 的捕获字段找 TimeLineTickAction）—— "
                + "故 >=0 = 找到时间线并读到帧数、**-1 = 确实找不到时间线**（组合体拆不开 / 第三方自定义实现 / 真无时间线）、"
                + "-2 = 读不到；注意 tickActionIsTimeline=false 而 frames>=0 属**合法组合**（时间线被 andThen 组合在 tickAction 里）；"
                + "判定一律以 fingerprintMax 为准；comboSeqTrace 里 selfDriven = silent_sun 在本 tick（或前一 tick）"
                + "刚主动写过 comboSeq / 推进过 combo —— 因此**变化本身不能证明时间线在跑**"
                + "（silent_sun 的 updateComboSeq/progressCombo 与 resolvCurrentComboState 的超时迁移都会改 comboSeq），"
                + "结论只以 fingerprintMax 为准。");
        out.addProperty("fingerprintKey", this.comboFingerprintKey);
        out.addProperty("fingerprintKeyFromReflection", this.comboFingerprintKeyFromReflection);
        out.addProperty("fingerprintMax", this.comboFingerprintMax);
        out.addProperty("fingerprintFirstNonZeroT", this.comboFingerprintFirstNonZeroT);
        // 首次非零的现场（实测证明这三样才是定位驱动者的关键：哪一段 combo、是不是我方下发、哪次 SA 之后）
        out.addProperty("fingerprintFirstNonZeroCombo", this.comboFingerprintFirstNonZeroCombo);
        out.addProperty("fingerprintFirstNonZeroSelfDriven", this.comboFingerprintFirstNonZeroSelfDriven);
        out.addProperty("fingerprintFirstNonZeroLastSaId", this.comboFingerprintFirstNonZeroSaId);
        // 指纹首次非零时该 combo 的两个动作对象的**实际运行时类名**（"跟着变"：不假设类型，如实记录）
        out.addProperty("fingerprintFirstNonZeroTickActionClass", this.comboFingerprintFirstNonZeroTickActionClass);
        out.addProperty("fingerprintFirstNonZeroClickActionClass", this.comboFingerprintFirstNonZeroClickActionClass);
        out.addProperty("fingerprintFirstNonZeroTickActionIsTimeline", this.comboFingerprintFirstNonZeroTickActionIsTimeline);
        out.addProperty("fingerprintFirstNonZeroClickActionIsTimeline", this.comboFingerprintFirstNonZeroClickActionIsTimeline);
        out.addProperty("samples", this.comboProbeSamples);
        out.addProperty("sampleIntervalTicks", this.comboSampleIntervalTicks);
        out.addProperty("observedTicks", this.comboProbeObservedTicks);
        out.addProperty("activeComboTicks", this.comboProbeActiveTicks);
        out.addProperty("activeComboThresholdTicks", COMBO_ACTIVE_TICKS_THRESHOLD);
        out.addProperty("maxTimelineFrames", this.comboMaxTimelineFrames);
        out.addProperty("elapsedMax", this.comboElapsedMax);
        out.addProperty("comboSeqChanges", this.comboSeqChangeCount);
        out.addProperty("selfDrivenChanges", this.comboSelfDrivenChangeCount);
        if (this.comboTraceDropped > 0) {
            out.addProperty("comboSeqTraceDropped", this.comboTraceDropped);
        }
        if (this.comboProbeUnavailable != null) {
            out.addProperty("unavailable", this.comboProbeUnavailable);
        }
        JsonObject control = new JsonObject();
        control.addProperty("samples", this.comboControlSamples);
        control.addProperty("player", this.comboControlName);
        control.addProperty("fingerprintMax", this.comboControlFingerprintMax);
        control.addProperty("fingerprintMechanismVerified", mechanismVerified);
        out.add("control", control);
        return out;
    }

    /** comboSeqTrace：comboSeq 变化序列 + 指纹首次非零（探针的原始证据，结论见 {@code tickActionProbe}）。 */
    private JsonArray buildComboSeqTrace() {
        JsonArray arr = new JsonArray();
        for (ComboProbeSample e : this.comboSeqTrace) {
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("event", e.kind());
            o.addProperty("from", e.from());
            o.addProperty("to", e.to());
            o.addProperty("selfDriven", e.selfDriven());
            o.addProperty("lastProcessedTick", e.lastProcessedTick());
            o.addProperty("elapsed", e.elapsed());
            o.addProperty("timelineFrames", e.timelineFrames());
            o.addProperty("tickActionClass", e.tickActionClass());
            o.addProperty("clickActionClass", e.clickActionClass());
            if (e.lastSaId() != null) {
                o.addProperty("lastSaId", e.lastSaId());
            }
            arr.add(o);
        }
        return arr;
    }

    /**
     * entityProduction：Boss 周围模组实体的**增量**观测（2026-09-12，作者新情报后新增）。
     * <p>
     * 读法：
     * <ul>
     *   <li>{@code byType} / {@code byCategory} 回答「**哪一类**实体产出来了、多少次、首次与末次何时、归属谁」
     *       —— 这是「有一部分 SA 产出成功」到底对应哪类实体的直接答案；</li>
     *   <li>{@code trace} 给出每个实体出现的时刻与当时的 comboSeq / 指纹 / 是否我方驱动窗口 /
     *       距最近一次 SA 施放的 tick 偏移 —— 与 {@code saCasts}、{@code comboSeqTrace} 对齐即可定位产出链；</li>
     *   <li>{@code saCastAddedEntities} / {@code saCastEmptyProductions} 是**施放同调用内**的差值统计：
     *       added &gt; 0 的 SA 就是「真的产出成功」的那部分。</li>
     * </ul>
     * 注意：本观测跳过 {@code minecraft:} 命名空间实体，只统计模组实体；且只报**新出现**的 id（增量），
     * 首轮扫描只建基线不记录（否则会把战前已存在的实体误算成产出）。
     */
    private JsonObject buildEntityProduction() {
        JsonObject out = new JsonObject();
        out.addProperty("scanRadius", this.productionScanRadius);
        out.addProperty("scanIntervalTicks", this.productionScanIntervalTicks);
        out.addProperty("total", this.productionTotal);
        out.addProperty("outsideSelfDrivenWindow", this.productionOutsideSelfWindow);
        out.addProperty("saCastProductions", this.saProductionTotal);
        out.addProperty("saCastAddedEntities", this.saProductionAddedTotal);
        out.addProperty("saCastEmptyProductions", this.emptySaProductions);
        // 2026-09-13 新增：SA「窗口产出」—— 与上面的同调用差值互补。判读：
        //   windowCount > 0 ⇒ 该 SA 确实产出了实体（只是不在施放那一拍）；
        //   windowCount == 0 且同调用也是 0 ⇒ 这个 SA 是真的没产出任何东西。
        JsonArray saWindow = new JsonArray();
        for (Map.Entry<String, Integer> entry : this.saWindowProductionBySaId.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("saId", entry.getKey());
            o.addProperty("windowCount", entry.getValue());
            o.addProperty("outsideSelfDriven", this.saWindowProductionOutsideBySaId.getOrDefault(entry.getKey(), 0));
            o.addProperty("windowTicks", SA_PRODUCTION_WINDOW_TICKS);
            saWindow.add(o);
        }
        out.add("saWindowProductions", saWindow);
        JsonArray byType = new JsonArray();
        for (Map.Entry<String, ProdAgg> entry : this.productionByType.entrySet()) {
            ProdAgg a = entry.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("type", entry.getKey());
            o.addProperty("count", a.count);
            o.addProperty("firstT", ticksToSeconds(a.firstT < 0L ? 0L : a.firstT));
            o.addProperty("lastT", ticksToSeconds(a.lastT < 0L ? 0L : a.lastT));
            o.add("owners", toArray(new ArrayList<>(a.owners)));
            o.add("combos", toArray(new ArrayList<>(a.combos)));
            byType.add(o);
        }
        out.add("byType", byType);
        // 按 SA 聚合：哪一部分 SA 真的出了实体、出了什么 —— 直接对「有一部分 SA 产出成功」这条观察作答。
        JsonArray bySaId = new JsonArray();
        for (Map.Entry<String, SaProdAgg> entry : this.saProductionBySaId.entrySet()) {
            SaProdAgg a = entry.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("saId", entry.getKey());
            o.addProperty("casts", a.casts);
            o.addProperty("emptyCasts", a.emptyCasts);
            o.addProperty("addedTotal", a.addedTotal);
            o.addProperty("firstT", ticksToSeconds(a.firstT < 0L ? 0L : a.firstT));
            o.addProperty("lastT", ticksToSeconds(a.lastT < 0L ? 0L : a.lastT));
            o.add("producedTypes", toArray(new ArrayList<>(a.types)));
            bySaId.add(o);
        }
        out.add("bySaId", bySaId);
        JsonObject byCategory = new JsonObject();
        for (Map.Entry<String, Integer> entry : this.productionByCategory.entrySet()) {
            byCategory.addProperty(entry.getKey(), entry.getValue());
        }
        out.add("byCategory", byCategory);
        // 玩家挥刀锚点汇总：用于判断「产出是否是玩家侧触发」的第三条路径。
        JsonObject playerSlashes = new JsonObject();
        playerSlashes.addProperty("count", this.playerSlashCount);
        playerSlashes.addProperty("firstT", this.playerSlashFirstT < 0L ? -1.0 : ticksToSeconds(this.playerSlashFirstT));
        playerSlashes.addProperty("lastT", this.playerSlashLastT < 0L ? -1.0 : ticksToSeconds(this.playerSlashLastT));
        playerSlashes.add("players", toArray(new ArrayList<>(this.playerSlashPlayers)));
        out.add("playerSlashes", playerSlashes);
        // 玩家蓄力锚点汇总：第三方 SA 也可能挂在蓄力事件上（如 recasting 的 onCharge）。
        JsonObject playerCharges = new JsonObject();
        playerCharges.addProperty("count", this.playerChargeCount);
        // 蓄力者是 Boss 自己的次数：> 0 就是实锤「Boss 在走 ItemSlashBlade.onUseTick 链」，
        // 而 holdAction 是那条链的动作出口 —— 第三条路径的直接候选。
        playerCharges.addProperty("bossSelfCount", this.bossSelfChargeCount);
        playerCharges.addProperty("firstT", this.playerChargeFirstT < 0L ? -1.0 : ticksToSeconds(this.playerChargeFirstT));
        playerCharges.addProperty("lastT", this.playerChargeLastT < 0L ? -1.0 : ticksToSeconds(this.playerChargeLastT));
        playerCharges.add("players", toArray(new ArrayList<>(this.playerChargePlayers)));
        out.add("playerCharges", playerCharges);
        JsonArray trace = new JsonArray();
        for (ProductionEvent e : this.productionTrace) {
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("type", e.type());
            o.addProperty("category", e.category());
            if (e.owner() != null) {
                o.addProperty("owner", e.owner());
            }
            o.addProperty("selfDriven", e.selfDriven());
            o.addProperty("comboSeq", e.comboSeq());
            o.addProperty("fingerprint", e.fingerprint());
            o.addProperty("sinceLastSaCastTicks", e.sinceLastSaCastTicks());
            if (e.lastSaId() != null) {
                o.addProperty("lastSaId", e.lastSaId());
            }
            o.addProperty("sinceLastPlayerSlashTicks", e.sinceLastPlayerSlashTicks());
            if (e.lastPlayerSlashBy() != null) {
                o.addProperty("lastPlayerSlashBy", e.lastPlayerSlashBy());
            }
            o.addProperty("sinceLastPlayerChargeTicks", e.sinceLastPlayerChargeTicks());
            if (e.lastPlayerChargeBy() != null) {
                o.addProperty("lastPlayerChargeBy", e.lastPlayerChargeBy());
            }
            trace.add(o);
        }
        out.add("trace", trace);
        if (this.productionTraceDropped > 0) {
            out.addProperty("traceDropped", this.productionTraceDropped);
        }
        out.addProperty("readGuide",
            "看四处：① entityProduction.byType — 产出的实体类型与归属（含非 slashblade 命名空间 = 第三方模组实体，"
                + "既有诊断 diagnoseSlashBladeEntityFlood 看不到这一类）；② trace 里每条实体的 selfDriven 与 "
                + "sinceLastSaCastTicks —— selfDriven=false 且指纹恒 0 ⇒ 产出不是 silent_sun 驱动的；"
                + "sinceLastSaCastTicks 在 0~2 内 ⇒ 很可能是 clickAction/releaseAction 同拍产物，"
                + "远大于 2 且按数十 tick 间隔反复出现 ⇒ 疑似时间线逐帧产出；"
                + "③ saProductions[].added — 那次 SA 同调用内到底产出了什么（空表 = 这次 SA 空放）；"
                + "④ trace 的双锚点 sinceLastPlayerSlashTicks / sinceLastPlayerChargeTicks —— "
                + "若产出的实体几乎都紧跟某次玩家**挥刀**或**蓄力**之后（且 selfDriven=false、fingerprint=0），"
                + "则这条第三条路径是**玩家侧**触发的（第三方 SA 常挂在 DoSlashEvent / ChargeActionEvent 上，"
                + "如 recasting 的 TimeBeyondSlashArts.onCharge）；若两个锚点都对不上，"
                + "则驱动者既不在 silent_sun 侧、也不在玩家挥刀/蓄力事件上，需要继续查第三方模组的自调度器。"
                + " ★另外务必看 playerCharges.bossSelfCount：> 0 表示**蓄力者就是 Boss 自己**"
                + "（ChargeActionEvent 的持有者是任意 LivingEntity，第三方 onCharge 不检查是否玩家）"
                + "⇒ Boss 在走 ItemSlashBlade.onUseTick 链，其动作出口是 holdAction —— "
                + "这是第三条路径最直接的候选（silent_sun 自己从不 startUsingItem）。"
                + " 归因局限（务必知悉）：selfDriven 是**窗口级**标记 —— 只要该扫描窗口"
                + "（2 tick）内 silent_sun 驱动过 combo，该窗口内的产出就一律标 true，因此它**不排除**"
                + "「我方与第三方同窗口各产一部分」，(C) 的判定方向是保守的（宁可漏判 C，不假报 C）；"
                + "要更细只能看 saProductions 的**同调用差值**（那是严格同一次调用内的因果差）；"
                + "反向的真（selfDriven=true）也不等于产出必源于我方 —— 它只说明该窗口我方动过 combo。");
        return out;
    }

    /** saProductions：每次 SA 施放同调用内新增的实体（原始序列，汇总见 {@code entityProduction}）。 */
    private JsonArray buildSaProductions() {
        JsonArray arr = new JsonArray();
        for (SaProductionEvent e : this.saProductions) {
            JsonObject o = new JsonObject();
            o.addProperty("t", ticksToSeconds(e.t()));
            o.addProperty("saId", e.saId());
            o.addProperty("addedCount", e.added().size());
            o.add("added", toArray(e.added()));
            arr.add(o);
        }
        return arr;
    }

    private static final class SaAgg {
        private int count;
        private long firstT = -1L;
        private long lastT = -1L;
        private int failures;
        private final List<Integer> phases = new ArrayList<>();
    }

    private static double ticksToSeconds(long ticks) {
        return Math.round(ticks / TICKS_PER_SECOND * 1000.0) / 1000.0;
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray arr = new JsonArray();
        if (values != null) {
            for (String v : values) {
                arr.add(v);
            }
        }
        return arr;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
