package com.lydiath.silent_sun.entity;

/**
 * Immutable data definition for a single title stage.
 * <p>
 * Replaces the ad-hoc {@code PHASE1_TITLES} / {@code PHASE2_TITLES} String arrays
 * and the scattered hardcoded duration/flag logic in {@code onTitleChanged}.
 * <p>
 * Each title self-describes:
 * <ul>
 *   <li>Phase and index (e.g. Phase 1, title 0 = 愿予必成)</li>
 *   <li>Minimum lock duration (seconds)</li>
 *   <li>Permanent flags granted when first entered</li>
 *   <li>Title display name key and phase 2 quote key</li>
 * </ul>
 */
public final class TitleDef {
    // 2026-09-11（代码审计 G06 #4 修复）：原 phase / titleIndex / minDurationSeconds 三个字段
    // **全库零读取**（只写）—— 头衔锁血时长的真值在配置项（SilentSunConfig），这三个字段从未被消费，
    // 留着会让维护者以为「minDurationSeconds 生效」。已删除字段，并把 p1 / p2 收窄为单一工厂
    // （收窄后两者实现完全相同，保留两个同义工厂等于再造一处「同义包装」死代码）；
    // 相位语义现由 PHASE1_TITLE_DEFS / PHASE2_TITLE_DEFS 两个数组本身承载。
    public final BossFlag[] flagsGranted;

    TitleDef(BossFlag... flagsGranted) {
        this.flagsGranted = flagsGranted;
    }

    /** 单个头衔定义（相位由调用方的数组决定）。 */
    public static TitleDef of(BossFlag... flags) {
        return new TitleDef(flags);
    }
}
