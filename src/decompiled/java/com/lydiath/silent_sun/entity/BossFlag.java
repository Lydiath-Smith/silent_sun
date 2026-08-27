package com.lydiath.silent_sun.entity;

/**
 * Permanent unlock flags for RediosEntity.
 * <p>
 * Replaces the 13+ scattered boolean fields ({@code guardUnlocked},
 * {@code seaSkySoulSeverUnlocked}, {@code uncontrolledSprintUnlocked}, etc.)
 * with a single {@code EnumSet<BossFlag>}.
 * <p>
 * Once a flag is granted (typically by entering a title for the first time),
 * it stays active for the remainder of the fight.
 */
public enum BossFlag {

    // ────────── Permanent unlocks ──────────

    /** 1.9 有所不为 — adaptive guard block */
    GUARD_BLOCK,

    /** 2.0 海天之隙 — Sea-Sky Gap soul sever mechanics */
    SEA_SKY_SOUL_SEVER,

    /** 1.5 镜影之面 — 断魂每击挂载与结算提前解锁（原设计仅 2.0 海天之隙解锁） */
    SOUL_SEVER_HARVEST,

    /** 2.1 失控疾驰 — cancel attack cooldown */
    UNCONTROLLED_SPRINT,

    /** 2.2 错位干涉 exit — dodge chance floor 15% */
    DODGE_MIN_15,

    /** 2.3 混沌破败 exit — absolute damage attacks */
    CHAOS_RUIN_ABSOLUTE,

    /** 2.4 灰烬曙光 — ash dawn unlocked */
    ASH_DAWN,

    /** 2.6 暗色天星 — dark star blast fired (one-shot) */
    DARK_STAR_FIRED,

    /** 2.6 暗色天星 — dark star flight mode */
    DARK_STAR_FLIGHT,

    /** 2.6 暗色天星 — bedrock repaired flag */
    BEDROCK_REPAIRED,

    /** 2.7 黑日天光 — black sun mechanics */
    BLACK_SUN,

    /** 2.8 无光失色 — colorless mode */
    COLORLESS,

    /** 1.8 砺锋尝胆 — attack damage type randomized */
    ATTACK_RANDOMIZED,

    /** Unlocked via P1T2 or P2T8 — enrage stacking level 2+ */
    ENRAGE_STACKING,

    /** P1T2 坚信之意 — weakness curse on attackers */
    WEAKNESS_CURSE,
}
