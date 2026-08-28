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
     * 无战斗安全窗口：投票 / 转阶段 / 一阶段濒死等待。
     * 这些状态下 Boss 不参与任何战斗逻辑，failsafe 清场与区块卸载结算必须豁免，
     * 防止服务器波动或短暂无人加载导致 Boss 无奖励消失。
     */
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
