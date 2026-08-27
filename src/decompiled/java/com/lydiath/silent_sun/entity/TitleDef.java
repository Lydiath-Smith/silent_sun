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
    public final int phase;
    public final int titleIndex;
    public final int minDurationSeconds;
    public final BossFlag[] flagsGranted;

    TitleDef(int phase, int titleIndex, int minDurationSeconds, BossFlag... flagsGranted) {
        this.phase = phase;
        this.titleIndex = titleIndex;
        this.minDurationSeconds = minDurationSeconds;
        this.flagsGranted = flagsGranted;
    }

    /** Create a Phase 1 title definition. */
    public static TitleDef p1(int index, int minSec, BossFlag... flags) {
        return new TitleDef(1, index, minSec, flags);
    }

    /** Create a Phase 2 title definition. */
    public static TitleDef p2(int index, int minSec, BossFlag... flags) {
        return new TitleDef(2, index, minSec, flags);
    }
}
