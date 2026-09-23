/*
 * 转阶段震屏：P1→P2 过渡动画（PHASE1_TRANSITION）期间，
 * 附近参战玩家的相机视角叠加正弦抖动，强度随过渡进度增强
 * （临近 P2 登场瞬间达到峰值），距离 96 格以外不生效。
 */
package com.lydiath.silent_sun.client;

import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.List;

@EventBusSubscriber(modid = "silent_sun", value = Dist.CLIENT)
public final class CameraShakeEvents {

    // 2026-09-14（体检 P2-B / G20 #3 **已闭环**）：原先此处把配置驱动的转场总时长**硬编码成 120.0**
    //（= 默认 6s 的值）⇒ 配置改成非 6s 时**进度曲线被压平**：分母固定 120，而剩余 tick > 120 时
    //`Math.min(ticks, 120) / 120` 恒为 1 ⇒ progress 恒 0、前半段一直卡在开场 50% 强度，
    //要等到剩余 < 120 才开始上升（即"配置改时长本处不跟随"）。
    //现改为调用**唯一来源** `RediosEntity.configuredTransitionTicks()`（客户端可安全调用：它是 static，
    //且 `SilentSunConfig` 双端可读 —— 同包客户端 `RediosRenderer` 早已这么用）。
    //默认配置（6s）下取值仍为 **120**，与旧硬编码**逐位相同** ⇒ 默认行为零变化。
    //原 TODO(审计清理 G20 #3) 已闭环，勿再本地硬编码转场时长。
    /** 震屏生效半径（格） */
    private static final double SHAKE_RADIUS = 96.0;
    private static final double MAX_ROLL_DEG = 3.0;
    private static final double MAX_YAW_DEG = 1.2;
    private static final double MAX_PITCH_DEG = 1.0;

    /**
     * 2026-08-12：Boss 攻击玩家不震屏。
     * 原版玩家受击镜头晃动由 ClientboundDamageEventPacket 同步的 hurtTime/hurtDuration 驱动
     * （GameRenderer.bobHurt），网络包在客户端 tick 的 gameMode.tick() 阶段处理，
     * 早于 ClientTickEvent.Post → 在此清除可确保下一渲染帧不再出现受击倾斜。
     * 仅当本次受击来源为 Redios（近战/拔刀/SA/光环真伤）或其投掷炸弹时清除，转阶段震屏不受影响。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.hurtTime <= 0) {
            return;
        }
        DamageSource src = player.getLastDamageSource();
        if (src == null) {
            return;
        }
        Entity direct = src.getDirectEntity();
        if (direct instanceof RediosEntity || src.getEntity() instanceof RediosEntity) {
            player.hurtTime = 0;
            player.hurtDuration = 0;
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        RediosEntity boss = findTransitioningBoss(mc);
        if (boss == null) {
            return;
        }
        int ticks = boss.getClientTransitionTicks();
        if (ticks <= 0) {
            return;
        }
        double distSq = boss.distanceToSqr(mc.player);
        if (distSq > SHAKE_RADIUS * SHAKE_RADIUS) {
            return;
        }
        // 强度：过渡开场 50%，随进度线性升至 100%（临近 P2 登场最强）
        double ref = RediosEntity.configuredTransitionTicks();
        double progress = 1.0 - Math.min(ticks, ref) / ref;
        double intensity = 0.5 + 0.5 * Math.max(progress, 0.0);
        // 距离衰减：96 格外无震，50 格内满强度（线性）
        double distFactor = Math.max(0.0, 1.0 - Math.sqrt(distSq) / SHAKE_RADIUS);
        double amp = distFactor * intensity;
        // 多频正弦叠加：roll 为主震（左右晃），yaw/pitch 为辅（轻微视野晃动）
        double phase = (double) mc.player.tickCount * 0.55;
        float roll = (float) (Math.sin(phase) * MAX_ROLL_DEG * amp);
        float yaw = (float) (Math.cos(phase * 1.3) * MAX_YAW_DEG * amp);
        float pitch = (float) (Math.sin(phase * 0.7 + 1.7) * MAX_PITCH_DEG * amp);
        event.setRoll(event.getRoll() + roll);
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
    }

    private static RediosEntity findTransitioningBoss(Minecraft mc) {
        AABB box = AABB.ofSize(mc.player.position(), SHAKE_RADIUS * 2, SHAKE_RADIUS * 2, SHAKE_RADIUS * 2);
        List<RediosEntity> list = mc.level.getEntitiesOfClass(RediosEntity.class, box,
            e -> e.isAlive() && e.getClientTransitionTicks() > 0);
        return list.isEmpty() ? null : list.get(0);
    }

    private CameraShakeEvents() {
    }
}
