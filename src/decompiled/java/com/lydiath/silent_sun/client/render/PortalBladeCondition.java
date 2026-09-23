package com.lydiath.silent_sun.client.render;

import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.entity.RediosEntity;
import net.minecraft.world.entity.LivingEntity;

public class PortalBladeCondition {

    /**
     * 判断是否应该激活武器的末地传送门残影特效。
     * <p>激活条件：实体是 Boss，且**断魂值**达到
     * {@link SilentSunConfig#SOUL_SEVER_PORTAL_TRAIL_THRESHOLD}（默认 3000）。
     *
     * <p><b>2026-09-14 判据修正（重要）</b>：原实现读 {@code MobEffectInstance.getAmplifier() >= 3000}，
     * 而前置模组 {@code SoulSeverMobEffect.MAX_AMPLIFIER = 4}（经 javap 核实）⇒ **该条件恒为 false，
     * 残影一次都不会出现**。3000 这个数值本身是合理的（与 {@code SOUL_SEVER_Y_WARNING_THRESHOLD}
     * 默认 5000 同量纲），错的是**取值来源**：应读断魂值，而不是 effect 等级。
     *
     * <p><b>为什么必须走 {@link RediosEntity#getClientSoulSeverValue()}</b>：本方法在**客户端渲染**时
     * 被调用，而 {@code soulSeverY} 是服务端内存字段（仅 NBT / 账本落盘，**从不同步**）⇒ 客户端直接读
     * 服务端值只会拿到 0。故走同步镜像 —— 该同步位（{@code CLIENT_SOUL_SEVER_Y}）于 2026-09-11 被
     * 当作「零引用」删除，2026-09-14 因本功能恢复（详见该字段注释）。
     *
     * <p><b>为何不再检查 effect 是否存在</b>：断魂值 = {@code SOUL_SEVER_BASE_X(30) + soulSeverY}，
     * 而 {@code soulSeverY} 只在断魂相关路径被累加 ⇒ 达标本身就蕴含「处于断魂状态」。
     * 同时省掉每**帧**一次 {@code ResourceLocation.parse} + registry 查询（这里是渲染热路径）。
     */
    public static boolean isActive(LivingEntity entity) {
        if (!(entity instanceof RediosEntity redios)) {
            return false;
        }
        return redios.getClientSoulSeverValue() >= SilentSunConfig.SOUL_SEVER_PORTAL_TRAIL_THRESHOLD.get();
    }
}
