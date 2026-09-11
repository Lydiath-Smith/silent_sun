/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.network;

import com.lydiath.silent_sun.SilentSunMod;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：玩家在黑日天光（2.7）完败界面上点击"返回出生点"。
 * <p>
 * 服务端据此将玩家传回其出生点（床/重生锚，否则世界出生点），并恢复状态，
 * 但不经过原版死亡重生流程，因此不增加死亡计数。
 */
public record BlackSunRespawnPayload() implements CustomPacketPayload {
    public static final Type<BlackSunRespawnPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(SilentSunMod.MODID, "black_sun_respawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlackSunRespawnPayload> STREAM_CODEC =
        StreamCodec.unit(new BlackSunRespawnPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 2026-09-11（代码审计 G05 修复）：败北界面的「待回包」登记表 —— UUID → 过期 gameTime。
     * <p>
     * 原实现的 {@code handleServer} 只做一次 {@code instanceof} 判断就执行「传送 + 满血 + 清 debuff」，
     * 而服务端不记录任何「该玩家刚结束败北界面」的状态 → <b>任意客户端（含改包玩家）在任意时刻
     * 发送这个包，都能拿到满血 + 回出生点 + 清负面</b>，完全绕过 leaveBattle / 结算 / 冷却。
     * 现在只有服务端亲自发过败北界面、且仍在有效期内的玩家，回包才被受理（一次性消费）。
     * <p>
     * 用 ConcurrentHashMap 是因为 payload handler 与主线程调用点不保证同线程。
     */
    private static final Map<UUID, Long> DEFEAT_SCREEN_PENDING = new ConcurrentHashMap<UUID, Long>();
    private static final long PENDING_VALID_TICKS = 1200L;

    /** 服务端发出败北界面时调用；顺手清理已过期条目，使表规模只与「近期收到界面的玩家数」有关。 */
    public static void markDefeatScreenShown(ServerPlayer player) {
        if (player == null) {
            return;
        }
        long now = player.level().getGameTime();
        DEFEAT_SCREEN_PENDING.entrySet().removeIf(e -> e.getValue() < now);
        DEFEAT_SCREEN_PENDING.put(player.getUUID(), now + PENDING_VALID_TICKS);
    }

    public static void handleServer(BlackSunRespawnPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Long expire = DEFEAT_SCREEN_PENDING.remove(player.getUUID());
            if (expire == null || player.level().getGameTime() > expire) {
                SilentSunMod.LOGGER.warn("忽略未经授权的 black_sun_respawn 回包：player={}（未登记或已过期）",
                    player.getName().getString());
                return;
            }
            respawnToSpawn(player);
        }
    }

    private static void respawnToSpawn(ServerPlayer player) {
        ServerLevel current = player.serverLevel();
        MinecraftServer server = current.getServer();
        BlockPos respawnPos = player.getRespawnPosition();
        ResourceKey<Level> respawnDim = player.getRespawnDimension();

        ServerLevel target = null;
        if (respawnPos != null) {
            target = server.getLevel(respawnDim);
        }
        if (target == null) {
            target = server.overworld();
        }
        if (target == null) {
            target = current;
        }

        Vec3 pos;
        if (respawnPos != null) {
            pos = Vec3.atBottomCenterOf(respawnPos);
        } else {
            BlockPos spawnPos = target.getSharedSpawnPos();
            pos = new Vec3(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        }

        player.teleportTo(target, pos.x, pos.y, pos.z, Set.of(), player.getYRot(), player.getXRot());
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.clearFire();
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.WEAKNESS);
    }
}
