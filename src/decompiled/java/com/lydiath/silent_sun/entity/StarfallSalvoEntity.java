/*
 * 繁星爆闪 (StarfallSalvo) 星星实体。
 * 无碰撞箱、无重力、免疫伤害；由 Boss 在周围 5 格召唤后，
 * 各自随机延迟，再垂直下落至离地 2 格悬停，等待 Boss 统一引爆。
 * 本实体只负责「下落→悬停」演出；爆炸统一由 RediosEntity 触发。
 */
package com.lydiath.silent_sun.entity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class StarfallSalvoEntity extends Entity {
    /** 每 tick 下落高度（2026-09-01 用户实测「瞬爆」：5.0 太快看不见下落过程 → 回退 3.5，
     *  约 70 格/秒，30 格落差约 0.43 秒，配合随机延迟有层次感）。 */
    private static final double FALL_SPEED_PER_TICK = 3.5;
    /** 硬上限：超出此 tick 仍未引爆则自行消失（兜底，防止 Boss 转阶段时残留）。 */
    private static final int MAX_LIFETIME_TICKS = 240;

    private UUID ownerUuid = null;
    /** 跟踪目标（2026-09-01 用户裁决：星星下落/悬停期间跟随目标当前位置，
     *  解决「目标移动 → 引爆落空」；目标消失则回落跟随 Boss）。
     *  2026-09-01 修订：保持生成时的水平偏移（星星群整体平移跟随，间距不变）→
     *  分散感保留（否则星星全聚向目标中心，视觉上一坨）。 */
    private UUID targetUuid = null;
    /** 生成时相对目标中心的水平偏移（跟踪时保持，星星群随目标平移而间距不变）。 */
    private double offsetX = 0.0;
    private double offsetZ = 0.0;
    private double hoverY = 0.0;
    private int delayTicks = 0;
    private boolean falling = false;
    private boolean settled = false;
    private int ageTicks = 0;
    /** 合法移除标记：生命周期到期或 Boss 引爆前置 true，防止被误判为作弊清除。 */
    private boolean legitRemoval = false;
    /** 最近一次 hurt 的攻击者（用于作弊清除时定位作弊者）。2026-09-10 恢复：本批曾连同
     *  remove() 检测一起被删，导致"星星被外部清除"再也不会静默补刀（W2 回归）。 */
    private Entity lastAttacker = null;
    /**
     * 显示豁免：入场演出星星置 true，客户端渲染时绕过雾效（失明遮蔽下仍全程可见）。
     * <p>
     * 2026-09-11（代码审计 G10 修复）：原为普通 boolean 字段，**只在服务端由
     * {@code RediosEntity} 置位、没有任何同步通道** —— 客户端渲染器
     * {@code StarfallSalvoRenderer} 读到的恒为 false，「穿透黑雾」豁免从来没有生效过。
     * 改为 SynchedEntityData 同步（本类原先就覆写了空的 defineSynchedData）。
     */
    private static final EntityDataAccessor<Boolean> DATA_DISPLAY_EXEMPT =
        SynchedEntityData.defineId(StarfallSalvoEntity.class, EntityDataSerializers.BOOLEAN);

    public StarfallSalvoEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void initSalvo(UUID ownerUuid, double hoverY, int delayTicks, UUID targetUuid,
                          double offsetX, double offsetZ) {
        this.ownerUuid = ownerUuid;
        this.hoverY = hoverY;
        this.delayTicks = delayTicks;
        this.targetUuid = targetUuid;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    /** Boss 引爆时调用：标记为合法移除，防止被误判为作弊清除而误伤。 */
    public void markLegitRemoval() {
        this.legitRemoval = true;
    }

    /** 入场演出星星标记为显示豁免：客户端渲染绕过雾效，失明遮蔽下仍全程可见。 */
    public void markDisplayExempt() {
        this.entityData.set(DATA_DISPLAY_EXEMPT, Boolean.TRUE);
    }

    public boolean isDisplayExempt() {
        return this.entityData.get(DATA_DISPLAY_EXEMPT);
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DISPLAY_EXEMPT, Boolean.FALSE);
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        this.ageTicks++;
        if (this.ageTicks > MAX_LIFETIME_TICKS) {
            this.legitRemoval = true;
            this.discard();
            return;
        }
        // 水平跟随目标（2026-09-01 用户裁决）：delay/下落/悬停全程朝目标当前位置缓动，
        // 目标消失则跟随 Boss——解决「目标移动 → 引爆落空」。
        this.trackTargetHorizontally();
        if (!this.falling) {
            if (this.delayTicks > 0) {
                this.delayTicks--;
            } else {
                this.falling = true;
            }
            return;
        }
        if (!this.settled) {
            double nextY = this.getY() - FALL_SPEED_PER_TICK;
            if (nextY <= this.hoverY) {
                this.setPos(this.getX(), this.hoverY, this.getZ());
                this.settled = true;
            } else {
                this.setPos(this.getX(), nextY, this.getZ());
            }
        }
    }

    /** 水平跟随目标（保持生成时的水平偏移：目标存活 → 目标当前位置+偏移；目标消失 → Boss；
     *  均不可用 → 原地）。偏移保持使星星群整体平移跟随、互相间距不变 → 分散感保留。 */
    private void trackTargetHorizontally() {
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            return;
        }
        Entity track = null;
        if (this.targetUuid != null) {
            Entity t = sl.getEntity(this.targetUuid);
            if (t instanceof LivingEntity le && le.isAlive()) {
                track = le;
            }
        }
        if (track == null && this.ownerUuid != null) {
            Entity o = sl.getEntity(this.ownerUuid);
            if (o instanceof LivingEntity le && le.isAlive()) {
                track = le;
            }
        }
        if (track == null) {
            return;
        }
        double dx = (track.getX() + this.offsetX) - this.getX();
        double dz = (track.getZ() + this.offsetZ) - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1) {
            // 每 tick 移动 0.8 格（不超过剩余距离），平滑追踪不瞬移
            double step = Math.min(dist, 0.8);
            this.setPos(this.getX() + dx / dist * step, this.getY(), this.getZ() + dz / dist * step);
        }
    }

    public boolean isSettled() {
        return this.settled;
    }

    // TODO(审计清理 G10 #6)：本文件 isFalling() / getOwnerUuid() 与 item/RediosSigilItem.java 的 getUseAnimation() 是三个零调用公开方法，可删；注意 StarfallCurtainEntity.getLifetimeTicks() 已于 D-5 裁定后「由死变活」，**不要删** —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
    public boolean isFalling() {
        return this.falling;
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public boolean canCollideWith(Entity other) {
        return false;
    }

    public boolean isAttackable() {
        return false;
    }

    public boolean isNoGravity() {
        return true;
    }

    /**
     * 记录攻击者（2026-09-10 恢复，W2 回归）：星星被外部清除时用它定位作弊者，静默补刀。
     * 仍然不吃伤害（return false），只是留痕。
     */
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            this.lastAttacker = attacker;
        }
        return false;
    }

    /**
     * 作弊清除检测：星星只会在「生命周期到期」或「Boss 引爆」时合法移除（届时
     * {@code legitRemoval} 已置 true）。其余 KILLED/DISCARDED 均视为作弊清除，静默回调
     * Boss 补刀（不刷屏）。移除本身不阻止——星星是短命演出实体，被清就被清。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide && !this.legitRemoval
            && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            this.notifyCheatRemoval();
        }
        super.remove(reason);
    }

    private void notifyCheatRemoval() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        Entity owner = serverLevel.getEntity(this.ownerUuid);
        if (!(owner instanceof RediosEntity boss)) return;
        if (this.lastAttacker instanceof ServerPlayer cheater && cheater.isAlive()) {
            boss.silentRetaliate(cheater);
        }
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SalvoOwner")) {
            this.ownerUuid = tag.getUUID("SalvoOwner");
        }
        // M23：跟踪目标 + 水平偏移持久化（此前缺失，重载后星星丢失跟踪目标）
        if (tag.contains("SalvoTarget")) {
            this.targetUuid = tag.getUUID("SalvoTarget");
        }
        this.offsetX = tag.getDouble("SalvoOffsetX");
        this.offsetZ = tag.getDouble("SalvoOffsetZ");
        this.hoverY = tag.getDouble("SalvoHoverY");
        this.delayTicks = tag.getInt("SalvoDelay");
        this.falling = tag.getBoolean("SalvoFalling");
        this.settled = tag.getBoolean("SalvoSettled");
        this.ageTicks = tag.getInt("SalvoAge");
        this.entityData.set(DATA_DISPLAY_EXEMPT, tag.getBoolean("SalvoDisplayExempt"));
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUuid != null) {
            tag.putUUID("SalvoOwner", this.ownerUuid);
        }
        // M23：跟踪目标 + 水平偏移持久化
        if (this.targetUuid != null) {
            tag.putUUID("SalvoTarget", this.targetUuid);
        }
        tag.putDouble("SalvoOffsetX", this.offsetX);
        tag.putDouble("SalvoOffsetZ", this.offsetZ);
        tag.putDouble("SalvoHoverY", this.hoverY);
        tag.putInt("SalvoDelay", this.delayTicks);
        tag.putBoolean("SalvoFalling", this.falling);
        tag.putBoolean("SalvoSettled", this.settled);
        tag.putInt("SalvoAge", this.ageTicks);
        tag.putBoolean("SalvoDisplayExempt", this.entityData.get(DATA_DISPLAY_EXEMPT));
    }
}
