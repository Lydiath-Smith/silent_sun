/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class RediosCooldownData
extends SavedData {
    private static final String KEY = "silent_sun_redios_cooldown";
    private static final SavedData.Factory<RediosCooldownData> FACTORY = new SavedData.Factory<>(RediosCooldownData::new, RediosCooldownData::load, DataFixTypes.LEVEL);
    private long nextAllowedGameTime = 0L;
    private int summonAttemptCount = 0;

    public static RediosCooldownData get(ServerLevel level) {
        // 冷却全局共享：固定挂载到主世界 SavedData，避免下界/末地各自维护一份导致跨维度可重复召唤。
        return (RediosCooldownData)overworld(level).getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    private static ServerLevel overworld(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server == null ? level : server.overworld();
    }

    private static RediosCooldownData load(CompoundTag tag, HolderLookup.Provider registries) {
        RediosCooldownData data = new RediosCooldownData();
        data.nextAllowedGameTime = tag.getLong("NextAllowedGameTime");
        data.summonAttemptCount = tag.getInt("SummonAttemptCount");
        return data;
    }

    public long getNextAllowedGameTime() {
        return this.nextAllowedGameTime;
    }

    public void setCooldown(ServerLevel level, long durationTicks) {
        // J1: 冷却以游戏内时间（DayTime）为基准，受睡觉跳过夜晚与 /time 加速影响。
        // 统一以主世界 DayTime 为基准：末地/下界的 DayTime 固定不变（无昼夜循环），
        // 若用传入维度的时间会导致冷却在下界/末地召唤后永远无法结束（"冷却进入很长时间"）。
        long now = overworld(level).getDayTime();
        this.nextAllowedGameTime = Math.max(this.nextAllowedGameTime, now + Math.max(0L, durationTicks));
        this.setDirty();
    }

    /** 显式重置召唤冷却（仅供 /silent_sun redios reset_summon_cd 命令调用）。
     *  不回改 setCooldown 的 Math.max 单调递增语义——正常结算仍防缩短，
     *  仅在此把回退能力封在明确 API 里，供管理员可信场景归零。 */
    public void resetCooldown() {
        this.nextAllowedGameTime = 0L;
        this.summonAttemptCount = 0;
        this.setDirty();
    }

    public boolean isOnCooldown(ServerLevel level) {
        return overworld(level).getDayTime() < this.nextAllowedGameTime;
    }

    public long remainingTicks(ServerLevel level) {
        return Math.max(0L, this.nextAllowedGameTime - overworld(level).getDayTime());
    }

    /** 记录一次冷却期内的召唤尝试（仅冷却中调用）；计数在冷却到期成功召唤处显式清零。 */
    public int recordSummonAttempt(ServerLevel level) {
        this.summonAttemptCount++;
        this.setDirty();
        return this.summonAttemptCount;
    }

    public int getSummonAttemptCount() {
        return this.summonAttemptCount;
    }

    /** 冷却到期时清零连点计数（否则计数跨冷却周期累积，下一周期首次连点即触发额外提示）。 */
    public void resetSummonAttempt() {
        if (this.summonAttemptCount != 0) {
            this.summonAttemptCount = 0;
            this.setDirty();
        }
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextAllowedGameTime", this.nextAllowedGameTime);
        tag.putInt("SummonAttemptCount", this.summonAttemptCount);
        return tag;
    }
}
