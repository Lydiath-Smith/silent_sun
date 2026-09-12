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

    /** SA 池快照：只在 {@code IntegrationContract.tryInvokeRandomSA} 真正重建池缓存（60s TTL 到期）时抓一次。 */
    private record SaPoolSnapshot(long t, List<String> inPool, List<String> excluded, List<String> namespaces,
                                  List<String> saIds) {
    }

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

    public void saPool(long nowGameTime, List<String> inPool, List<String> excluded, List<String> namespaces,
                       List<String> saIds) {
        try {
            this.saPool = new SaPoolSnapshot(this.rel(nowGameTime), copy(inPool), copy(excluded),
                copy(namespaces), copy(saIds));
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
            LOG.info("[SilentSun] 战斗流程报告已导出：{}（头衔 {} / 收尾 {} / 反作弊 {} / SA 施放 {} 条）",
                file, this.titleFlow.size(), this.phaseSettle.size(), this.antiCheat.size(), this.saCasts.size());
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
