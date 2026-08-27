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
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public final class StarfallSalvoEntity extends Entity {
    /** 每 tick 下落高度（约 70 格/秒，28 格落差约 0.4 秒落地）。 */
    private static final double FALL_SPEED_PER_TICK = 3.5;
    /** 硬上限：超出此 tick 仍未引爆则自行消失（兜底，防止 Boss 转阶段时残留）。 */
    private static final int MAX_LIFETIME_TICKS = 240;

    private UUID ownerUuid = null;
    private double hoverY = 0.0;
    private int delayTicks = 0;
    private boolean falling = false;
    private boolean settled = false;
    private int ageTicks = 0;
    /** 合法移除标记：生命周期到期或 Boss 引爆前置 true，防止被误判为作弊清除。 */
    private boolean legitRemoval = false;
    /** 最近一次 hurt 的攻击者（用于作弊清除时定位作弊者）。 */
    private Entity lastAttacker = null;
    /** 显示豁免：入场演出星星置 true，客户端渲染时绕过雾效（失明遮蔽下仍全程可见）。 */
    private boolean displayExempt = false;

    public StarfallSalvoEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void initSalvo(UUID ownerUuid, double hoverY, int delayTicks) {
        this.ownerUuid = ownerUuid;
        this.hoverY = hoverY;
        this.delayTicks = delayTicks;
    }

    /** Boss 引爆时调用：标记为合法移除，防止被误判为作弊清除而误伤。 */
    public void markLegitRemoval() {
        this.legitRemoval = true;
    }

    /** 入场演出星星标记为显示豁免：客户端渲染绕过雾效，失明遮蔽下仍全程可见。 */
    public void markDisplayExempt() {
        this.displayExempt = true;
    }

    public boolean isDisplayExempt() {
        return this.displayExempt;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
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

    public boolean isSettled() {
        return this.settled;
    }

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

    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            this.lastAttacker = attacker;
        }
        return false;
    }

    public boolean isAttackable() {
        return false;
    }

    public boolean isNoGravity() {
        return true;
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
        this.hoverY = tag.getDouble("SalvoHoverY");
        this.delayTicks = tag.getInt("SalvoDelay");
        this.falling = tag.getBoolean("SalvoFalling");
        this.settled = tag.getBoolean("SalvoSettled");
        this.ageTicks = tag.getInt("SalvoAge");
        this.displayExempt = tag.getBoolean("SalvoDisplayExempt");
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUuid != null) {
            tag.putUUID("SalvoOwner", this.ownerUuid);
        }
        tag.putDouble("SalvoHoverY", this.hoverY);
        tag.putInt("SalvoDelay", this.delayTicks);
        tag.putBoolean("SalvoFalling", this.falling);
        tag.putBoolean("SalvoSettled", this.settled);
        tag.putInt("SalvoAge", this.ageTicks);
        tag.putBoolean("SalvoDisplayExempt", this.displayExempt);
    }
}
