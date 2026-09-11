package com.lydiath.silent_sun.entity;

/**
 * Explicit finite state machine for RediosEntity lifecycle.
 * <p>
 * 见设计稿《docs/设计文稿-重制版.md》§2.0（权威状态机）：本枚举 + RediosEntity.transitionTo
 * 为运行时唯一入口；反序列化（readAdditionalSaveData / rebuildFromRecord）经
 * restoreBossState 直接赋值绕过转移表（历史存档容错）。
 * <p>
 * Replaces the 6+ implicit boolean flags ({@code phase}, {@code transitionTicks},
 * {@code awaitingPhase2Choice}, {@code noResurrection}, {@code pendingDeath},
 * {@code titleLockTicks}) with a single authoritative state.
 * <p>
 * Illegal state combinations (e.g. pendingDeath + titleLockTicks coexisting,
 * or noResurrection skipping transitionTicks check) are eliminated by design
 * — the state machine explicitly rejects invalid transitions.
 *
 * <pre>
 *   PHASE1_COMBAT ──(1.9 vote start)──► PHASE1_VOTE
 *        │                                    │
 *        │                                    ├──(timeout/no-voters)──► PHASE1_TRANSITION
 *        │                                    ├──(yes majority)───────► PHASE1_TRANSITION
 *        │                                    └──(no majority)────────► discard()
 *        │
 *        ├──(noResurrection, HP≤1)──► PHASE2_COMBAT (direct, no vote)
 *        │
 *        ├──(last title, HP≤1)──────► PHASE1_PENDING (wait lock expire)
 *        │
 *   PHASE1_PENDING ──(titleLockTicks=0)──► PHASE1_VOTE (beginPhase2Choice)
 *
 *   PHASE1_PENDING 与 PHASE2_PENDING 同理：一阶段濒死（最后头衔被打到 1 血）后
 *   不再立即投票，而是等待当前头衔锁血倒计时归零（titleLockTicks=0）才进入投票，
 *   保证"一阶段濒死与二阶段濒死同理"——濒死即进入冻结等待，而非立刻推进。
 *
 *   PHASE1_TRANSITION ──(ticks=0)──► PHASE2_COMBAT
 *
 *   PHASE2_COMBAT ──(HP≤1, !voidAllThings)──► PHASE2_PENDING
 *
 *   PHASE2_PENDING ──(titleLockTicks=0)──► die() → DEFEATED
 * </pre>
 * <p>
 * <b>序号契约（2026-09-11 代码审计 G06 #9）：只能在末尾追加新状态，禁止中间插入或调序。</b>
 * 本枚举以 {@code ordinal()} 落盘，共三个持久化面：① 实体 NBT {@code SilentSunBossState}；
 * ② 战场账本 {@code RediosBattleData} 的 {@code "BossState"} int 键；③ 反序列化 / 回场重建的写回。
 * 读取侧只有<b>越界保护</b>（{@code RediosEntity.restoreBossState}、
 * {@code RediosBattleData.rebuildOrDrop}），挡不住「插入」—— 序号仍落在 [0,6) 内时会被
 * {@code values()[n]} 静默映射成相邻状态：不越界、不报错、行为静默错档。
 * ⇒ 新状态一律追加在末尾；若确需插入，必须同时提供旧存档迁移（例如把状态一并写成 name 键）。
 * {@link #ORDINAL_CONTRACT} 是该契约的可执行形式，装载期逐项比对，不一致即抛异常。
 */
public enum BossState {

    /** Normal Phase 1 combat — title locks active, all abilities tick. */
    PHASE1_COMBAT,

    /** Phase 1.9 vote in progress — AI frozen, boss sitting. */
    PHASE1_VOTE,

    /** Phase 1 last title HP clamped at 1, waiting for lock to expire before vote. */
    PHASE1_PENDING,

    /** Transition animation from P1 to P2 — invulnerable, no abilities. */
    PHASE1_TRANSITION,

    /** Normal Phase 2 combat — title locks active, all abilities tick. */
    PHASE2_COMBAT,

    /** Phase 2 HP clamped at 1, waiting for current title lock to expire before releasing to player kill. */
    PHASE2_PENDING;

    /**
     * 序号契约的可执行形式：常量名必须与 ordinal 逐位对应。
     * <p>
     * 2026-09-11（代码审计 G06 #9）：本表的唯一用途是把「序号即存档格式」变成<b>装载期硬约束</b>——
     * 有人往枚举中间插入 / 调整顺序时，这份代码在任何环境都会立刻抛 {@link IllegalStateException}，
     * 而不是等旧存档被静默读成相邻状态。改枚举就必须改本表，而改本表就会看到上面那条契约。
     * <p>
     * 注：这里故意抛异常而非仅告警 —— 表与 {@code values()} 的一致性只由源码决定，与运行环境无关；
     * 一旦不一致，说明「改了枚举没改表」，那份代码在任何环境都是错的，崩掉比静默错档安全。
     */
    private static final String[] ORDINAL_CONTRACT = {
        "PHASE1_COMBAT", "PHASE1_VOTE", "PHASE1_PENDING", "PHASE1_TRANSITION", "PHASE2_COMBAT", "PHASE2_PENDING"
    };

    static {
        BossState[] all = values();
        if (all.length != ORDINAL_CONTRACT.length) {
            throw new IllegalStateException("BossState 常量数已变（" + all.length + " != " + ORDINAL_CONTRACT.length
                + "）。本枚举以 ordinal() 落盘（NBT SilentSunBossState / 账本 BossState 键），"
                + "只能在末尾追加；若必须插入，须同时提供旧存档迁移。");
        }
        for (int i = 0; i < all.length; i++) {
            if (!all[i].name().equals(ORDINAL_CONTRACT[i])) {
                throw new IllegalStateException("BossState 序号契约被破坏：ordinal " + i + " 期望 "
                    + ORDINAL_CONTRACT[i] + "，实际 " + all[i].name()
                    + "。中间插入 / 调序会让旧存档静默错档，禁止；只能在末尾追加。");
            }
        }
    }

    // ────────── Phase helpers (replaces int phase field queries) ──────────

    public boolean isPhase1() {
        return this == PHASE1_COMBAT || this == PHASE1_VOTE || this == PHASE1_TRANSITION || this == PHASE1_PENDING;
    }

    public boolean isPhase2() {
        return this == PHASE2_COMBAT || this == PHASE2_PENDING;
    }

    public int getPhase() {
        return isPhase1() ? 1 : 2;
    }

    // ────────── Combat / ability helpers ──────────

    /** Boss is in active combat: can be hurt, can attack, abilities tick. */
    public boolean isCombat() {
        return this == PHASE1_COMBAT || this == PHASE2_COMBAT;
    }

    /** Boss is frozen (vote, transition, pending death, or defeated). */
    public boolean isFrozen() {
        return !isCombat();
    }

    /** Boss is in vote or transition (used for player-side checks). */
    public boolean isVoteOrTransition() {
        return this == PHASE1_VOTE || this == PHASE1_TRANSITION;
    }

    /** Boss is in Phase 1 combat specifically. */
    public boolean isPhase1Combat() {
        return this == PHASE1_COMBAT;
    }

    /** Boss is in Phase 2 combat specifically. */
    public boolean isPhase2Combat() {
        return this == PHASE2_COMBAT;
    }

    /** Boss is awaiting Phase 2 vote. */
    public boolean isVoting() {
        return this == PHASE1_VOTE;
    }

    /** Boss is in transition animation. */
    public boolean isTransitioning() {
        return this == PHASE1_TRANSITION;
    }

    /** Boss is in pending-death state (Phase 2, HP=1). */
    public boolean isPendingDeath() {
        return this == PHASE2_PENDING;
    }

    /** Boss is in pending-death state (Phase 1 last title, HP=1, waiting to vote). */
    public boolean isPhase1PendingDeath() {
        return this == PHASE1_PENDING;
    }

    /**
     * 无战斗安全窗口：投票 / 转阶段 / 一阶段濒死等待 / 二阶段濒死等待。
     * <p>
     * <b>当前唯一消费者是 {@code RediosEntity.tickFailsafe}</b>（这些状态下不触发 failsafe 清场，
     * 防止服务器波动或短暂低帧导致 Boss 无奖励消失）。
     * <p>
     * 历史沿革（2026-09-10 核对）：0.0.24 及以前它还被 {@code checkBattleAreaUnloaded} 使用，
     * 用于豁免区块卸载结算；该豁免已按 2026-09-01 用户裁决「区块都卸载了就让人走吧」**有意删除**——
     * 即冻结态也会因区块卸载退场。此处保留原名仅为兼容既有调用与历史语义，
     * <b>不要再把它当成"卸载结算豁免"的判据</b>。
     */
    // TODO(审计清理 G06 #6)：isFrozen() 与 isSafeWindow() 是同一集合的两份实现 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
    public boolean isSafeWindow() {
        return this == PHASE1_VOTE || this == PHASE1_TRANSITION || this == PHASE1_PENDING || this == PHASE2_PENDING;
    }

    // ────────── Transition table ──────────

    /**
     * 显式转移表：声明本状态可合法转移到的目标状态。
     * <p>
     * 仅覆盖运行时转移；反序列化（readAdditionalSaveData / rebuildFromRecord）不经过此校验。
     * 非法转移由 {@code RediosEntity.transitionTo} 告警但不阻断，避免遗漏导致 Boss 卡死。
     */
    public boolean canTransitionTo(BossState next) {
        if (this == next) {
            return false;
        }
        return switch (this) {
            case PHASE1_COMBAT -> next == PHASE1_PENDING || next == PHASE1_TRANSITION || next == PHASE2_COMBAT;
            case PHASE1_VOTE -> next == PHASE1_COMBAT;
            case PHASE1_PENDING -> next == PHASE1_VOTE || next == PHASE1_COMBAT || next == PHASE1_TRANSITION;
            case PHASE1_TRANSITION -> next == PHASE2_COMBAT;
            case PHASE2_COMBAT -> next == PHASE2_PENDING;
            case PHASE2_PENDING -> next == PHASE2_COMBAT;
        };
    }
}
