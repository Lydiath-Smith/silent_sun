/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.data;

import com.lydiath.silent_sun.SilentSunMod;
import com.lydiath.silent_sun.entity.BossState;
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
    /** 2026-09-10（用户裁决 A8 + 本轮实测裁决）：实体缺失但「该位置正在实体 tick」= 被外部暴力删除
     *  → **立刻回场**（原为 400 tick 宽限）。区块已加载且在实体 tick 距离却找不到 Boss，账本里又有
     *  未结算记录（不变量：记录存在 ⟺ 未结算，合法离场一律先清记录），不存在误判空间；宽限期只会
     *  让"Boss 被删 → 回场"之间白空 20 秒。保留 1 tick 仅用于避开同 tick 的移除/重建竞态。
     *  真正未加载/未到实体 tick 距离的情形走下方"走远/卸载"分支（仍等 100 tick）。 */
    private static final int REBUILD_GRACE_TICKS_LOADED = 1;
    /**
     * 2026-09-10：**真正被使用**的长兜底窗口（10 分钟 = 12000 tick）。
     * <p>
     * 历史提示：同名常量在 0.0.17 ~ 2026-09-08 期间是**死常量**（全程只有声明、代码一律写字面量 12000L），
     * 因此本批次早期已将它删除。本次重新引入是因为它终于有了真实用途：
     * 为「**本次启动从未见过实体 tick**」的账本记录（崩服/重启后 Boss 区块始终未加载）提供最终清理，
     * 避免记录永久残留成幽灵账本。
     */
    private static final int UNLOADED_SETTLE_TICKS = 12000;
    /**
     * 2026-09-10（实测崩坏修复）：两次重建尝试之间的最小间隔（5 秒）。
     * 防止"重建出的 Boss 没能站住"时每 tick 重建一次（实测出现过聊天栏被刷爆的风暴）。
     */
    private static final int REBUILD_RETRY_COOLDOWN_TICKS = 100;
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
                // 2026-09-10：6 项重建必需状态（旧档无键 → 走各字段默认，向后兼容）
                r.battleStartGameTime = e.contains("BattleStart") ? e.getLong("BattleStart") : -1L;
                ListTag initList = e.getList("InitialParticipants", 10);
                for (int m = 0; m < initList.size(); ++m) {
                    CompoundTag p = initList.getCompound(m);
                    if (p.hasUUID("Id")) r.initialParticipants.add(p.getUUID("Id"));
                }
                ListTag twilightList = e.getList("TwilightExpelled", 10);
                for (int m = 0; m < twilightList.size(); ++m) {
                    CompoundTag p = twilightList.getCompound(m);
                    if (p.hasUUID("Id")) r.twilightExpelled.add(p.getUUID("Id"));
                }
                ListTag netList = e.getList("PlayerNetDamage", 10);
                for (int m = 0; m < netList.size(); ++m) {
                    CompoundTag p = netList.getCompound(m);
                    if (p.hasUUID("Id")) r.playerNetDamage.put(p.getUUID("Id"), p.getDouble("Total"));
                }
                ListTag dmgList = e.getList("DamageTypeTotals", 10);
                for (int m = 0; m < dmgList.size(); ++m) {
                    CompoundTag p = dmgList.getCompound(m);
                    ResourceLocation key = ResourceLocation.tryParse((String)p.getString("Key"));
                    if (key != null) r.damageTypeTotals.put(key, p.getDouble("Total"));
                }
                ListTag restoreList = e.getList("DarkStarRestore", 10);
                for (int m = 0; m < restoreList.size(); ++m) {
                    CompoundTag entry = restoreList.getCompound(m);
                    r.darkStarRestore.put(new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")), entry);
                }
                // 2026-09-10（L1 修复）：解锁旗标快照（旧档无键 → 空复合，读端走各自默认）
                if (e.contains("UnlockFlags")) {
                    r.unlockFlags = e.getCompound("UnlockFlags").copy();
                }
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
            // 2026-09-10（L1 修复）：解锁旗标快照（回场重建时写回实体 NBT）
            e.put("UnlockFlags", r.unlockFlags.copy());
            // 2026-09-10：6 项重建必需状态
            e.putLong("BattleStart", r.battleStartGameTime);
            ListTag initList = new ListTag();
            for (UUID id : r.initialParticipants) {
                CompoundTag p = new CompoundTag();
                p.putUUID("Id", id);
                initList.add(p);
            }
            e.put("InitialParticipants", initList);
            ListTag twilightList = new ListTag();
            for (UUID id : r.twilightExpelled) {
                CompoundTag p = new CompoundTag();
                p.putUUID("Id", id);
                twilightList.add(p);
            }
            e.put("TwilightExpelled", twilightList);
            ListTag netList = new ListTag();
            for (Map.Entry<UUID, Double> entry : r.playerNetDamage.entrySet()) {
                CompoundTag p = new CompoundTag();
                p.putUUID("Id", entry.getKey());
                p.putDouble("Total", entry.getValue().doubleValue());
                netList.add(p);
            }
            e.put("PlayerNetDamage", netList);
            ListTag dmgList = new ListTag();
            for (Map.Entry<ResourceLocation, Double> entry : r.damageTypeTotals.entrySet()) {
                CompoundTag p = new CompoundTag();
                p.putString("Key", entry.getKey().toString());
                p.putDouble("Total", entry.getValue().doubleValue());
                dmgList.add(p);
            }
            e.put("DamageTypeTotals", dmgList);
            ListTag restoreList = new ListTag();
            for (CompoundTag entry : r.darkStarRestore.values()) {
                restoreList.add(entry.copy());
            }
            e.put("DarkStarRestore", restoreList);
            list.add(e);
        }
        tag.put("Battles", list);
        return tag;
    }

    public void upsert(UUID bossId, ResourceLocation dimension, BlockPos pos, int phase, int titleIndex, long gameTime, float health, long soulSeverY, int bossStateOrd, int titleLockTicks, int colorlessChallengeTicks, Set<UUID> participants, Set<UUID> expelled,
                       long battleStartGameTime, Set<UUID> initialParticipants, Set<UUID> twilightExpelled,
                       Map<UUID, Double> playerNetDamage, Map<ResourceLocation, Double> damageTypeTotals,
                       Map<BlockPos, CompoundTag> darkStarRestore, CompoundTag unlockFlags) {
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
        // 2026-09-10：心跳即「Boss 正在实体 tick」，允许后续走 5s 走远/卸载离场判定。
        r.seenTickingSinceLoad = true;
        r.participants.clear();
        r.participants.addAll(participants);
        r.expelled.clear();
        r.expelled.addAll(expelled);
        // 2026-09-10：6 项重建必需状态（心跳同步）。darkStarRestore 用"尺寸变化才拷贝"的启发式，
        // 避免 2.6 破坏期该表可能含上百条记录、每 tick 全量拷贝造成的开销。
        r.battleStartGameTime = battleStartGameTime;
        r.initialParticipants.clear();
        r.initialParticipants.addAll(initialParticipants);
        r.twilightExpelled.clear();
        r.twilightExpelled.addAll(twilightExpelled);
        r.playerNetDamage.clear();
        r.playerNetDamage.putAll(playerNetDamage);
        r.damageTypeTotals.clear();
        r.damageTypeTotals.putAll(damageTypeTotals);
        if (r.darkStarRestore.size() != darkStarRestore.size()) {
            r.darkStarRestore.clear();
            r.darkStarRestore.putAll(darkStarRestore);
        }
        // 2026-09-10 实测修复（L1）：解锁旗标快照。原先重建只带 15 个键，10 个 *Unlocked 布尔 +
        // AttackSpecialized + PendingLockReleased 全部回落 false → 回场后 Boss 被剥光（断魂收割/
        // 无色挑战/格挡/虚空传送/全属性/激怒叠加全失效，2.9 还会被推导成"已解除"可一击必杀）。
        // 键名清单由 RediosEntity.snapshotUnlockFlags() 与读端 restoreStateFromNbt 共用，不在此重复。
        r.unlockFlags = unlockFlags == null ? new CompoundTag() : unlockFlags.copy();
        this.setDirty();
    }

    public void remove(UUID bossId) {
        if (this.records.remove(bossId) != null) {
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

    /** M21：是否存在任意战斗账本记录（Boss 存活但可能区块未加载）。 */
    public boolean hasAnyRecord() {
        return !this.records.isEmpty();
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
        for (BattleRecord r : new HashSet<BattleRecord>(this.records.values())) {
            if (r.dimension == null) {
                this.remove(r.bossId);
                continue;
            }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, r.dimension));
            if (level == null) {
                // 2026-09-11（代码审计 G05 #2 修复）：记录的维度已不存在（数据包/维度被移除）时，
                // 原实现与「实体仍在」共用一条 continue → 该记录**永不清理**：每 tick 空转，
                // 且召唤侧的「已有 Boss 记录」检查会被这条幽灵记录永久阻断（Boss 再也召唤不出来）。
                SilentSunMod.LOGGER.warn("[SilentSun] 账本记录指向的维度已不存在，清理该记录：bossId={} dim={}", r.bossId, r.dimension);
                this.remove(r.bossId);
                continue;
            }
            if (level.getEntity(r.bossId) != null) {
                continue;
            }
            long since = now - r.lastSeenGameTime;
            // ── 判据（2026-09-10 用户裁决 A8：恢复「暴力清除 → 重建」）──────────────
            // 用「该位置是否正在实体 tick」把两种情况彻底分开：
            //   ① 正在实体 tick 却找不到 Boss → 实体被外部删除（他模组/存档编辑/暴力清除）→ 重建；
            //   ② 未加载 / 未到实体 tick 距离 → 玩家走远或区块卸载 → 维持 2026-09-01 裁定（5s 离场）。
            // 不能只用 isLoaded：区块可以在视野内加载但不在实体 tick 距离，此时实体不在
            // entityManager 里、getEntity 必然返回 null，误判会重建出重复 Boss。
            if (level.isLoaded(r.pos) && level.isPositionEntityTicking(r.pos)) {
                r.seenTickingSinceLoad = true;
                if (since >= (long)REBUILD_GRACE_TICKS_LOADED) {
                    // 加载且在实体 tick 却找不到实体 = 被外部删除 → 立刻回场（2026-09-10 实测裁决）。
                    // 留痕日志移到 rebuildOrDrop 的冷却门之后（2026-09-10 修复日志风暴：原先写在
                    // 这里，重建没站住时会每 tick 打印 = 20 行/秒）。
                    this.rebuildOrDrop(level, r, now);
                }
                continue;
            }
            // ② 未加载 / 未到实体 tick 距离。
            //    ⚠️ 2026-09-10 实测修复：**必须先确认本次启动后见过实体 tick**，否则不能按"走远"判离场。
            //    服务器崩服/重启后，Boss 所在区块通常还没加载；若直接走下面的 5s 离场，
            //    就会出现「刚重启，Boss 无奖励消失，玩家不知道为什么离场」——这正是实测遇到的现象。
            //    本字段**不写档**：每次从存档载入 RediosBattleData 都重置为 false，
            //    因此只有"本次启动后确实见过 Boss 在 tick"（= 战斗正常进行过）才允许走 5s 离场。
            //    兜底：万一区块永远不再加载（玩家再也不会回来），10 分钟（UNLOADED_SETTLE_TICKS）
            //    后清掉记录，避免账本永久残留。
            // TODO(审计清理 G05 #6)：!seenTickingSinceLoad 分支不可达（启动即清账本） —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
            if (!r.seenTickingSinceLoad) {
                if (since >= (long)UNLOADED_SETTLE_TICKS) {
                    SilentSunMod.LOGGER.warn(
                        "[Redios] 账本残留清理（本次启动从未见过实体 tick，超过 {} tick）：boss={} pos={} since={}",
                        UNLOADED_SETTLE_TICKS, r.bossId, r.pos, since);
                    this.remove(r.bossId);
                }
                continue;
            }
            // 走远 / 区块卸载：5s（100 tick）内区块未重新加载（实体未恢复）即合法离场结算。
            if (since >= 100L) {
                SilentSunMod.LOGGER.warn(
                    "[Redios] 走远/卸载离场结算：boss={} pos={} since={} tick(加载={} 实体tick={}) 参战={}",
                    r.bossId, r.pos, since, level.isLoaded(r.pos), level.isPositionEntityTicking(r.pos),
                    r.participants.size());
                level.getChunkAt(r.pos);
                Entity entity = level.getEntity(r.bossId);
                if (entity instanceof RediosEntity) {
                    RediosEntity redios = (RediosEntity)entity;
                    redios.settleByUnloadTimeout(level);
                }
                this.remove(r.bossId);
            }
        }
    }

    /**
     * 终态闸门 + 重建（批次 2.2）。
     * <p>
     * <b>不变量</b>：所有合法离场路径（{@code settleBattle} / {@code leaveBattle} / {@code die}）都按
     * 2026-08-30「退场秩序化」先 {@code clearBattleRecord} 再执行后续清理，因此
     * <b>账本里存在记录 ⟺ 该场战斗尚未结算</b>。已结算的场次绝无记录可被重建。
     * 此处再按 {@code bossStateOrd} 做一次范围校验：越界序号属脏记录，直接清除而不重建。
     * <p>
     * 2026-09-10（实测崩坏修复）：加**重建重试冷却**。起因是实测出现「每 tick 一次的重建风暴」——
     * 重建出的 Boss 被入世守卫拒绝（数据版本键缺失，见 {@code rebuildFromRecord}），实体从未真正落地，
     * 于是 tickServer 每 tick 都再重建一次，聊天栏被广播刷爆。冷却保证**任何**"重建没站住"的失败模式
     * 最多每 {@link #REBUILD_RETRY_COOLDOWN_TICKS} 重试一次。
     * （作者裁定"重建次数无上限"针对的是**次数**，不是**频率**，故本冷却不与裁定冲突。）
     *
     * @return true 表示本次已完成重建
     */
    private boolean rebuildOrDrop(ServerLevel level, BattleRecord r, long now) {
        if (now - r.lastRebuildAttemptTick < (long)REBUILD_RETRY_COOLDOWN_TICKS) {
            return false;
        }
        r.lastRebuildAttemptTick = now;
        // 留痕放在冷却门之后：真实重建尝试最多每 REBUILD_RETRY_COOLDOWN_TICKS 一次
        //（2026-09-10 修复日志风暴——原先在冷却门之前打印 → 重建没站住时 20 行/秒）。
        SilentSunMod.LOGGER.warn("[Redios] 外部删除 → 回场：boss={} pos={} 参战={} 连续重建计数={}",
            r.bossId, r.pos, r.participants.size(), r.rebuildCount);
        int ord = r.bossStateOrd;
        if (ord < 0 || ord >= BossState.values().length) {
            this.remove(r.bossId);
            return false;
        }
        if (!RediosEntity.rebuildFromRecord(level, r)) {
            // 已有另一个 Redios 实体 / 实体创建失败 → 冷却后重试（重建无次数上限）。
            // 2026-09-10：留痕——若这条反复出现，说明"重建没站住"（实测那次是入世守卫拒收），
            // 冷却把它的频率压到 5 秒一次，不会刷屏。
            SilentSunMod.LOGGER.warn(
                "[Redios] 重建失败（{} tick 后重试）：boss={} pos={} 连续重建计数={} 参战={}",
                REBUILD_RETRY_COOLDOWN_TICKS, r.bossId, r.pos, r.rebuildCount, r.participants.size());
            return false;
        }
        ++r.rebuildCount;
        this.setDirty();
        return true;
    }

    public static final class BattleRecord {
        public UUID bossId;
        public ResourceLocation dimension;
        public BlockPos pos;
        public int phase;
        public int titleIndex;
        public long lastSeenGameTime;
        /** 被外部删除后的重建次数（**无次数上限**，2026-09-10 用户裁决 A8；仅作诊断计数：
         *  每次成功重建 +1，实体恢复心跳（{@link #upsert}）后清零，故其值 = "连续重建次数"。） */
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
        public final Set<UUID> participants = new HashSet<UUID>();
        public final Set<UUID> expelled = new HashSet<UUID>();
        /**
         * 2026-09-10：本次启动后**是否见过该 Boss 正在实体 tick**（心跳 upsert 或 tickServer 观测到即置位）。
         * <p>
         * **刻意不写入存档**——每次从存档载入本 SavedData 时都重置为 false。
         * 用途：只有"本次启动确实跑过战斗"才允许走 5 秒「走远/卸载离场」判定；
         * 否则崩服/重启后 Boss 所在区块未加载，会被误判成走远而无奖励消失。
         */
        public boolean seenTickingSinceLoad = false;
        /** 2026-09-10：上次重建尝试的 gameTime（**不写档**，仅用于 5 秒重试冷却，防重建风暴）。 */
        public long lastRebuildAttemptTick = 0L;
        /** 2026-09-10（L1 修复）：解锁旗标快照——回场重建时写回实体 NBT，键名清单由
         *  {@code RediosEntity.snapshotUnlockFlags()} 与读端共用（10 个 *Unlocked + 2.7 特化 + 2.9 锁血解除）。 */
        public CompoundTag unlockFlags = new CompoundTag();
        // ── 2026-09-10 新增：重建必须保留的 6 项状态（原先只有 9 个键重建，导致这些回落默认值）──
        /** 战斗开始 gameTime（灾变式动态减伤的时间基准）。缺失 → 重建后减伤从 80% 重新开始（Boss 突然变硬）。 */
        public long battleStartGameTime = -1L;
        /** 初始参战者（2.7 分母 + 成书 {participants} 占位符）。缺失 → 分母清零。 */
        public final Set<UUID> initialParticipants = new HashSet<UUID>();
        /** 2.5「断光之刻」被传送者名单。缺失 → 逐出名单清零。 */
        public final Set<UUID> twilightExpelled = new HashSet<UUID>();
        /** 每玩家累计净伤害（威胁值 / 繁星分配）。缺失 → 仇恨清零、索敌退回就近。 */
        public final Map<UUID, Double> playerNetDamage = new HashMap<UUID, Double>();
        /** 伤害类型累计（2.7 弱点特化判定）。缺失 → 特化属性丢失。 */
        public final Map<ResourceLocation, Double> damageTypeTotals = new HashMap<ResourceLocation, Double>();
        /** 2.6 破坏方块的恢复表。**缺失 → 那些方块永久不恢复**（基岩/屏障/命令方块等）。 */
        public final Map<BlockPos, CompoundTag> darkStarRestore = new HashMap<BlockPos, CompoundTag>();
    }
}
