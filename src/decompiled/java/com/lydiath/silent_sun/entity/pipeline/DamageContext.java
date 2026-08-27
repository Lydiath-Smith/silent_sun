package com.lydiath.silent_sun.entity.pipeline;

import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/**
 * Mutable context passed through the damage pipeline.
 * <p>
 * {@code amount} is modified in-place by stages that apply damage reduction.
 * {@code attacker} is set by {@code AttackerResolutionStage} and consumed by
 * subsequent stages that need the resolved {@link LivingEntity}.
 * {@code cancelled} is set to {@code true} when a stage fully handles or
 * rejects the damage, stopping further pipeline processing.
 */
public class DamageContext {

    public final RediosEntity boss;
    public final DamageSource source;
    public float            amount;
    public boolean          cancelled;

    /**
     * Resolved by {@code AttackerResolutionStage}.
     * {@code null} until that stage runs.
     */
    @Nullable
    public LivingEntity attacker;

    /**
     * Set to {@code true} by {@code AttackerResolutionStage} once
     * {@link RediosEntity#markBattleParticipant(LivingEntity)} has been called,
     * to prevent duplicate calls in later stages.
     */
    public boolean participantMarked;

    public DamageContext(RediosEntity boss, DamageSource source, float amount) {
        this.boss   = boss;
        this.source = source;
        this.amount = amount;
    }
}
