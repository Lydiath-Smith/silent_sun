/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.data;

import com.lydiath.silent_sun.entity.RediosEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public final class RediosBattleData
extends SavedData {
    private static final String KEY = "silent_sun_redios_battles";
    private static final SavedData.Factory<RediosBattleData> FACTORY = new SavedData.Factory<>(RediosBattleData::new, RediosBattleData::load, DataFixTypes.LEVEL);
    private static final int UNLOADED_SETTLE_TICKS = 12000;
    private final Map<UUID, BattleRecord> records = new HashMap<UUID, BattleRecord>();

    public static RediosBattleData get(ServerLevel level) {
        return (RediosBattleData)level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    private static RediosBattleData load(CompoundTag tag, HolderLookup.Provider registries) {
        RediosBattleData data = new RediosBattleData();
        ListTag list = tag.getList("Battles", 10);
        int i = 0;
        while (i < list.size()) {
            CompoundTag e = list.getCompound(i);
            if (e.hasUUID("BossId")) {
                BattleRecord r = new BattleRecord();
                r.bossId = e.getUUID("BossId");
                r.dimension = ResourceLocation.tryParse((String)e.getString("Dim"));
                r.pos = new BlockPos(e.getInt("X"), e.getInt("Y"), e.getInt("Z"));
                r.phase = e.getInt("Phase");
                r.titleIndex = e.getInt("TitleIndex");
                r.lastSeenGameTime = e.getLong("LastSeen");
                r.rebuildCount = e.getInt("Rebuilds");
                r.health = e.getFloat("Health");
                r.soulSeverY = e.getLong("SoulSeverY");
                r.bossStateOrd = e.getInt("BossState");
                r.titleLockTicks = e.getInt("TitleLock");
                r.colorlessChallengeTicks = e.contains("ColorlessChallenge") ? e.getInt("ColorlessChallenge") : -1;
                // 判定秩序化（A3）：已合法离场标记（旧存档无此键 → 默认 false）
                r.settled = e.contains("Settled") && e.getBoolean("Settled");
                ListTag participants = e.getList("Participants", 10);
                int j = 0;
                while (j < participants.size()) {
                    CompoundTag p = participants.getCompound(j);
                    if (p.hasUUID("Id")) {
                        r.participants.add(p.getUUID("Id"));
                    }
                    ++j;
                }
                ListTag expelled = e.getList("Expelled", 10);
                int j2 = 0;
                while (j2 < expelled.size()) {
                    CompoundTag p = expelled.getCompound(j2);
                    if (p.hasUUID("Id")) {
                        r.expelled.add(p.getUUID("Id"));
                    }
                    ++j2;
                }
                data.records.put(r.bossId, r);
            }
            ++i;
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BattleRecord r : this.records.values()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("BossId", r.bossId);
            e.putString("Dim", r.dimension == null ? "" : r.dimension.toString());
            e.putInt("X", r.pos.getX());
            e.putInt("Y", r.pos.getY());
            e.putInt("Z", r.pos.getZ());
            e.putInt("Phase", r.phase);
            e.putInt("TitleIndex", r.titleIndex);
            e.putLong("LastSeen", r.lastSeenGameTime);
            e.putInt("Rebuilds", r.rebuildCount);
            e.putFloat("Health", r.health);
            e.putLong("SoulSeverY", r.soulSeverY);
            e.putInt("BossState", r.bossStateOrd);
            e.putInt("TitleLock", r.titleLockTicks);
            e.putInt("ColorlessChallenge", r.colorlessChallengeTicks);
            e.putBoolean("Settled", r.settled);
            ListTag participants = new ListTag();
            for (UUID id : r.participants) {
                CompoundTag p = new CompoundTag();
                p.putUUID("Id", id);
                participants.add(p);
            }
            e.put("Participants", participants);
            ListTag expelled = new ListTag();
            for (UUID id : r.expelled) {
                CompoundTag p = new CompoundTag();
                p.putUUID("Id", id);
                expelled.add(p);
            }
            e.put("Expelled", expelled);
            list.add(e);
        }
        tag.put("Battles", list);
        return tag;
    }

    public void upsert(UUID bossId, ResourceLocation dimension, BlockPos pos, int phase, int titleIndex, long gameTime, float health, long soulSeverY, int bossStateOrd, int titleLockTicks, int colorlessChallengeTicks, Set<UUID> participants, Set<UUID> expelled) {
        BattleRecord r = this.records.get(bossId);
        if (r == null) {
            r = new BattleRecord();
            r.bossId = bossId;
            this.records.put(bossId, r);
        }
        r.dimension = dimension;
        r.pos = pos;
        r.phase = phase;
        r.titleIndex = titleIndex;
        r.lastSeenGameTime = gameTime;
        r.health = health;
        // 断魂数值单调递增，取账本与实体两者较大值，防止外部清空/回退 NBT 导致数值丢失。
        r.soulSeverY = Math.max(r.soulSeverY, soulSeverY);
        r.bossStateOrd = bossStateOrd;
        r.titleLockTicks = titleLockTicks;
        r.colorlessChallengeTicks = colorlessChallengeTicks;
        // 存活心跳：Boss 仍在正常 tick 并上报记录，说明上次重建已成功落地。
        // 复位重建计数，避免"累计 3 次重建后永久失效"，同时保留对"重建失败(实体始终未出现)"
        // 的上限保护——重建失败时 upsert 不会被调用，rebuildCount 仍会累加直至 3 次停用。
        r.rebuildCount = 0;
        // 判定秩序化（A3）：心跳即「战斗进行中」，复位已结算标记（新战斗重新上报）。
        r.settled = false;
        r.participants.clear();
        r.participants.addAll(participants);
        r.expelled.clear();
        r.expelled.addAll(expelled);
        this.setDirty();
    }

    public void remove(UUID bossId) {
        if (this.records.remove(bossId) != null) {
            this.setDirty();
        }
    }

    /**
     * 标记记录为「已合法结算/离场」（判定秩序化 A3）。
     * <p>
     * 在 {@link #remove} 之前调用：先置 settled=true 再移除。即使移除后因竞态
     * 又有心跳写入（理论上已被 updateBattleRecord 守卫堵住，此处双保险），
     * tickServer 也会按 settled 清理而非重建——合法退场永不被判为实体异常。
     * 若记录已不存在（正常移除成功）则 no-op。
     */
    public void markSettled(UUID bossId) {
        BattleRecord r = this.records.get(bossId);
        if (r != null) {
            r.settled = true;
            this.setDirty();
        }
    }

    /** 清空全部战斗账本记录（召唤前暴力清除残留 / reset_summon_cd 时调用）。
     *  Redios 同一时刻只允许一个 Boss，清空整本账本不会误伤其他战斗。 */
    public void clearAllRecords() {
        if (!this.records.isEmpty()) {
            this.records.clear();
            this.setDirty();
        }
    }

    public long getSoulSeverY(UUID bossId) {
        BattleRecord r = this.records.get(bossId);
        return r == null ? 0L : r.soulSeverY;
    }

    public void tickServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        // 暴力清除反制：每 200 tick 扫描一次"实体缺失但未到卸载超时"的记录，检测存档级删除。
        boolean scanNow = now % 200L == 0L;
        for (BattleRecord r : new HashSet<BattleRecord>(this.records.values())) {
            if (r.dimension == null) {
                this.remove(r.bossId);
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, r.dimension));
            if (level == null || level.getEntity(r.bossId) != null) {
                continue;
            }
            // 判定秩序化（A3）：记录标记「已合法离场」→ 清理残留，不重建。
            // 合法退场（击败/卸载/计时胜利/无奖励）后若记录残留，不当作实体异常（作弊删除）。
            if (r.settled) {
                this.remove(r.bossId);
                continue;
            }
            long since = now - r.lastSeenGameTime;
            if (since >= 12000L) {
                // 原逻辑：卸载超时兜底结算（未加载区块中的 Boss 被卸载，实体缺失属正常）
                level.getChunkAt(r.pos);
                Entity entity = level.getEntity(r.bossId);
                if (entity instanceof RediosEntity) {
                    RediosEntity redios = (RediosEntity)entity;
                    redios.settleByUnloadTimeout(level);
                }
                this.remove(r.bossId);
                continue;
            }
            // 暴力清除反制：未到卸载超时但实体缺失。若所在 chunk 已加载（战斗区域仍活跃）→
            // 判定为被外部删除（在线清实体 / 停服删 entities 存档后重启加载）→ 用记录重建 Boss。
            // chunk 未加载 → 正常卸载，不强制加载，留待超时结算。
            if (!scanNow || since < 400L || r.rebuildCount >= 3) {
                continue;
            }
            ChunkPos cp = new ChunkPos(r.pos);
            if (!level.getChunkSource().hasChunk(cp.x, cp.z)) {
                continue;
            }
            level.getChunkAt(r.pos);
            if (level.getEntity(r.bossId) != null) {
                continue; // 卸载后强制加载恢复，无需重建
            }
            if (RediosEntity.rebuildFromRecord(level, r)) {
                r.rebuildCount++;
                this.setDirty();
            }
        }
    }

    public static final class BattleRecord {
        public UUID bossId;
        public ResourceLocation dimension;
        public BlockPos pos;
        public int phase;
        public int titleIndex;
        public long lastSeenGameTime;
        /** 被外部删除后的重建次数（上限 3 次，超出交给超时结算兜底）。 */
        public int rebuildCount;
        /** 最近一次记录时的真实血量（用于外部删除后重建时恢复，而非回满）。 */
        public float health;
        /** 断魂数值（单调递增），用于外部清空/删除 NBT 后恢复。 */
        public long soulSeverY;
        /** Boss 状态机序号（BossState.ordinal()），用于外部删除后恢复到正确的投票/转阶段/濒死状态。 */
        public int bossStateOrd;
        /** 头衔锁血剩余 tick，用于重建后保留濒死等待进度。 */
        public int titleLockTicks;
        /** 无色挑战剩余 tick（-1 表示未激活），用于重建后保留无色挑战进度。 */
        public int colorlessChallengeTicks = -1;
        /**
         * 已合法结算/离场标记（判定秩序化 A3）。
         * <p>
         * 合法退场（击败/卸载/计时胜利/无奖励）后若记录残留，tickServer 靠此标记
         * 识别为「合法离场」→ 清理而非重建；防止把正常结算误判为实体异常（作弊删除）。
         * 心跳 upsert 时复位为 false（新战斗重新开始上报）。
         */
        public boolean settled = false;
        public final Set<UUID> participants = new HashSet<UUID>();
        public final Set<UUID> expelled = new HashSet<UUID>();
    }
}
