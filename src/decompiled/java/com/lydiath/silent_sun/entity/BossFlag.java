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
 * <p>
 * 2026-09-11（代码审计 G06 #3 / G14 #5）：删除 5 个**只写不读**的死旗标 ——
 * {@code ATTACK_RANDOMIZED}（连授予都没有）、{@code DODGE_MIN_15}、{@code DARK_STAR_FIRED}、
 * {@code DARK_STAR_FLIGHT}、{@code BEDROCK_REPAIRED}。它们的真值分别是
 * {@code attackRandomized} / {@code dodgeChance} / {@code darkStarFired} /
 * {@code darkStarFlightUnlocked} / {@code darkStarBedrockRepaired} 布尔字段，
 * 旗标本身从无 {@code hasFlag} 查询方，留着会误导「旗标即真值」。
 * <p>
 * 安全性：{@code RediosEntity.unlockedFlags} 这个 EnumSet **不落盘**，由
 * {@code syncFlagsFromBooleans} 从各布尔字段重建 ⇒ 删除常量不影响存档（无 ordinal 序列化）。
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

    /** 2.3 混沌破败 exit — absolute damage attacks */
    CHAOS_RUIN_ABSOLUTE,

    /** 2.4 灰烬曙光 — ash dawn unlocked */
    ASH_DAWN,

    /** 2.7 黑日天光 — black sun mechanics */
    BLACK_SUN,

    /** 2.8 无光失色 — colorless mode */
    COLORLESS,

    /** Unlocked via P1T2 or P2T8 — enrage stacking level 2+ */
    ENRAGE_STACKING,

    /** P1T2 坚信之意 — weakness curse on attackers */
    WEAKNESS_CURSE,
}
