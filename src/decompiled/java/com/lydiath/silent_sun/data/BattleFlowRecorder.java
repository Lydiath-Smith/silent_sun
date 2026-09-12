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
 * 失败只 {@code warn}，异常绝不允许冒泡到战斗路径。默认关闭（{@link #isEnabled()} == false），
 * 关闭时 {@code RediosEntity} 不创建本对象（字段为 null），所有记录点先判 null ⇒ 零开销。
 * <p>
 * 纯内存记录 + 退场时一次性序列化；不持有实体强引用（只用 UUID / 字符串 / 基本类型）。
 * 记录面只含四类事件（{@code TITLE} / {@code SA_CAST} / {@code PHASE_SETTLE} / {@code ANTICHEAT}）
 * 加一份 SA 池快照。
 * <p>
 * 开关由命令侧控制（{@link #setEnabled(boolean)}）：{@code on} 时**当场开战的那一场**开始采集
 * （记录器在实体构造时按开关状态创建，已存在的 Boss 不补挂——与设计 §2.3 一致）。
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

    private static volatile boolean enabled = false;

    /** 命令侧开关：{@code on} 之后**新开战**的场次开始采集；{@code off} 之后不再新建记录器。 */
    public static void setEnabled(boolean value) {
        enabled = value;
        LOG.info("[SilentSun] 战斗流程报告 {}", value
            ? "已开启（当场开战的那一场开始采集，退场时导出 JSON）"
            : "已关闭（已有记录器的场次照常导出，之后不再采集）");
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // ── 事件载体（纯数据，不含实体引用） ──

    /** 头衔推进。{@code phase}/{@code fromPhase} 分开记：头衔推进里也包含 1→2 的换阶段那一跳。 */
    private record TitleEvent(long t, int phase, int fromPhase, int from, int to, boolean forced) {
    }

    /** 收尾事件：kind ∈ vote / transition / defeat / pending / noLootLeave。 */
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
                                    long lastProcessedTick, long elapsed, int timelineFrames, String lastSaId) {
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

    // ── 内存态 ──

    /** 战斗开始 gameTime（相对秒基准）；{@code -1} = 未知，退化为「首个事件时刻」。 */
    private long startGameTime = -1L;
    /** 已导出标记：导出幂等（safeDiscard 与 remove 两条导出路径只写一次文件）。 */
    private boolean flushed = false;

    private final List<TitleEvent> titleFlow = new ArrayList<>();
    private final List<SettleEvent> phaseSettle = new ArrayList<>();
    private final List<AntiCheatEvent> antiCheat = new ArrayList<>();
    private final List<SaCastEvent> saCasts = new ArrayList<>();
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
    /** elapsed 最大值（对照指纹用：elapsed 在涨而指纹不动 = 时间线一帧都没跑）。 */
    private long comboElapsedMax;
    /** 观测到的「非空时间线帧数」最大值（> 0 证明该 combo 真的有一份非空时间线）。 */
    private int comboMaxTimelineFrames = -2;
    private int comboSeqChangeCount;
    private int comboSelfDrivenChangeCount;
    private int comboTraceDropped;
    private String comboLastSeq;
    private String comboProbeUnavailable;
    private String comboFingerprintKey;
    private boolean comboFingerprintKeyFromReflection;
    private int comboSampleIntervalTicks;
    /** 对照：持刀玩家身上的同一指纹最大值 + 玩家名（证明指纹机制在当前 jar 上有效）。 */
    private long comboControlFingerprintMax = -1L;
    private String comboControlName;
    private int comboControlSamples;

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
                           boolean fingerprintKeyFromReflection) {
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
            // 指纹首次非零 = 铁证（该键唯一写入者是 TimeLineTickAction）—— 单独记一条，便于一眼定位时刻。
            if (lastProcessedTick > 0L && this.comboFingerprintFirstNonZeroT == null) {
                long t = this.rel(nowGameTime);
                this.comboFingerprintFirstNonZeroT = t;
                this.addComboSample(new ComboProbeSample(t, "fingerprintFirstNonZero", comboSeq, comboSeq,
                    selfDriven, lastProcessedTick, elapsed, timelineFrames, lastSaId));
            }
            if (!java.util.Objects.equals(this.comboLastSeq, comboSeq)) {
                this.comboSeqChangeCount++;
                if (selfDriven) {
                    this.comboSelfDrivenChangeCount++;
                }
                this.addComboSample(new ComboProbeSample(this.rel(nowGameTime), "comboSeqChange",
                    this.comboLastSeq, comboSeq, selfDriven, lastProcessedTick, elapsed, timelineFrames, lastSaId));
            }
            this.comboLastSeq = comboSeq;
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
        root.add("saCasts", this.buildSaCasts());
        root.add("saStats", this.buildSaStats());
        root.add("tickActionProbe", this.buildTickActionProbe());
        root.add("comboSeqTrace", this.buildComboSeqTrace());
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
     * ② 走过结算/击杀（{@code phaseSettle} 里出现 {@code defeat}）→ {@code defeated}；
     * ③ 只走了无掉落离场（{@code noLootLeave}）→ {@code noLootLeave}；
     * ④ 什么都没记到（例如区块卸载直接冻结实体，未走任何退场路径）→ {@code walkAway}。
     * 注意：投票否决离场也走 {@code settleBattle}（发一阶段奖励 + 设冷却），因此同样归入
     * {@code defeated}——要区分具体收尾方式请看 {@code phaseSettle} 的 kind/note 与 leaveReason。
     */
    private String deriveResult(String leaveReason) {
        if ("CHUNK_UNLOAD".equals(leaveReason)) {
            return "chunkUnload";
        }
        boolean defeated = false;
        boolean noLoot = false;
        for (SettleEvent e : this.phaseSettle) {
            if ("defeat".equals(e.kind())) {
                defeated = true;
            } else if ("noLootLeave".equals(e.kind())) {
                noLoot = true;
            }
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
            basis.append("观测到的 combo 其 tickAction 不是 TimeLineTickAction（帧数 -1）⇒ 指纹机制对它不适用，无法判定。");
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
        // 读法指南（写给作者）：字段多且易误读，故把「看哪三个字段」直接写进报告。
        out.addProperty("readGuide",
            "判定只看三处：① fingerprintMax > 0 ⇒ 时间线在跑（tickAction 被调用过）；"
                + "② fingerprintMax = 0 且 maxTimelineFrames > 0 且 activeComboTicks >= activeComboThresholdTicks "
                + "⇒ tickAction 从未被调用（第三方 SA 在 Boss 上是空放，只跑 clickAction）；"
                + "③ control.fingerprintMechanismVerified = true 才代表指纹机制在当前 jar 上有效"
                + "（对照 = 持刀玩家的同一指纹 > 0，玩家物品栏每 tick 调 inventoryTick）。"
                + " 注意 elapsedMax 是 combo 已过帧数（= gameTime - lastActionTime，随时间自然增长），"
                + "**不能**用来判断时间线是否在跑；maxTimelineFrames 的 -1 = 该 combo 的 tickAction 不是 "
                + "TimeLineTickAction，-2 = 读不到；comboSeqTrace 里 selfDriven = silent_sun 在本 tick（或前一 tick）"
                + "刚主动写过 comboSeq / 推进过 combo —— 因此**变化本身不能证明时间线在跑**"
                + "（silent_sun 的 updateComboSeq/progressCombo 与 resolvCurrentComboState 的超时迁移都会改 comboSeq），"
                + "结论只以 fingerprintMax 为准。");
        out.addProperty("fingerprintKey", this.comboFingerprintKey);
        out.addProperty("fingerprintKeyFromReflection", this.comboFingerprintKeyFromReflection);
        out.addProperty("fingerprintMax", this.comboFingerprintMax);
        out.addProperty("fingerprintFirstNonZeroT", this.comboFingerprintFirstNonZeroT);
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
            if (e.lastSaId() != null) {
                o.addProperty("lastSaId", e.lastSaId());
            }
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
