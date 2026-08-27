/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.network;

import com.lydiath.silent_sun.SilentSunMod;
import java.util.Set;
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

    public static void handleServer(BlackSunRespawnPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
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
