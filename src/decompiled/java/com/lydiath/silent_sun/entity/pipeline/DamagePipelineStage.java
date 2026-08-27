package com.lydiath.silent_sun.entity.pipeline;

/**
 * A single stage in the damage pipeline.
 * <p>
 * Each stage receives a mutable {@link DamageContext} and returns a
 * {@link DamageResult}.  Return {@link DamageResult#cancel()} to stop
 * the pipeline (the damage is fully handled or rejected).  Return
 * {@link DamageResult#proceed()} to continue to the next stage.
 * <p>
 * Stages may modify {@link DamageContext#amount} and set
 * {@link DamageContext#attacker} for downstream stages.
 */
@FunctionalInterface
public interface DamagePipelineStage {

    DamageResult process(DamageContext ctx);
}
