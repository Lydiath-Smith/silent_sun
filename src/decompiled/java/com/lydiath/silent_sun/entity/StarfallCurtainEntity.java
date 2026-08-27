/*
 * 繁星爆闪 (StarfallSalvo) 白色幕布实体。
 * 统一引爆瞬间生成：一块始终朝向玩家（公告板）的白色半透明幕布，放置在
 * 引爆点（Boss 位置）作为「远处白幕布」的世界内白闪演出，随生命周期线性淡出。
 * 无碰撞箱、无重力、免疫伤害；替代原先的全屏 HUD 白闪（StarfallFlashEvents）。
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

public final class StarfallCurtainEntity extends Entity {
    /** 幕布总生命周期（tick）。爆炸瞬间最亮，随剩余时间线性淡出至消失。 */
    public static final int MAX_LIFETIME_TICKS = 30;

    private int lifetimeTicks = MAX_LIFETIME_TICKS;
    private UUID ownerUuid = null;
    /** 合法移除标记：生命周期到期前置 true，防止被误判为作弊清除。 */
    private boolean legitRemoval = false;
    /** 最近一次 hurt 的攻击者（用于作弊清除时定位作弊者）。 */
    private Entity lastAttacker = null;

    public StarfallCurtainEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public void initCurtain(int lifetimeTicks) {
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
    }

    /** 生成时由 Boss 设置，用于作弊清除时回调 Boss 补刀。 */
    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
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
        this.lifetimeTicks--;
        if (this.lifetimeTicks <= 0) {
            this.legitRemoval = true;
            this.discard();
        }
    }

    public int getLifetimeTicks() {
        return this.lifetimeTicks;
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
     * 作弊清除检测：幕布只在「生命周期到期」时合法移除（届时 {@code legitRemoval} 已置
     * true）。其余 KILLED/DISCARDED 均视为作弊清除，静默回调 Boss 补刀（不刷屏）。
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
        if (!(this.level() instanceof ServerLevel serverLevel) || this.ownerUuid == null) return;
        Entity owner = serverLevel.getEntity(this.ownerUuid);
        if (!(owner instanceof RediosEntity boss)) return;
        if (this.lastAttacker instanceof ServerPlayer cheater && cheater.isAlive()) {
            boss.silentRetaliate(cheater);
        }
    }

    protected void readAdditionalSaveData(CompoundTag tag) {
        this.lifetimeTicks = tag.getInt("CurtainLifetime");
    }

    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("CurtainLifetime", this.lifetimeTicks);
    }
}
