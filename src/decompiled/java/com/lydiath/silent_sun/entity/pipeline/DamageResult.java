package com.lydiath.silent_sun.entity.pipeline;

/**
 * Result of a single pipeline stage execution.
 * Immutable — use {@link #proceed()} or {@link #cancel()} factory methods.
 */
public record DamageResult(boolean cancelled) {

    private static final DamageResult PROCEED = new DamageResult(false);
    private static final DamageResult CANCEL   = new DamageResult(true);

    public static DamageResult proceed() {
        return PROCEED;
    }

    public static DamageResult cancel() {
        return CANCEL;
    }
}
