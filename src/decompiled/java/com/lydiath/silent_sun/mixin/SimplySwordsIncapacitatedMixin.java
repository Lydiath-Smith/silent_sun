package com.lydiath.silent_sun.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 简易刀剑跨线程访问修补（2026-10-03，实战崩溃取证）：
 *
 * <p>{@code IncapacitatingStatusEffectRegistry.isIncapacitated(LivingEntity)} 会对
 * {@code entity.getActiveEffects()}（服务端实体内部 HashMap 的 values 视图）开 stream 遍历。
 * 该方法被其数据包处理器挂在 {@code ServerboundPlayerActionPacket} 的处理链上，而该数据包
 * 在 <b>Netty 网络线程</b>执行；同一时刻主线程正在给玩家施加 / 移除效果（高频战斗中每 tick
 * 数十次）⇒ {@code java.util.ConcurrentModificationException}（HashMap$ValueSpliterator），
 * 网络线程报错后连接被直接关闭（2026-10-03 23:18:33 实证）。
 *
 * <p>本 Mixin 在 HEAD 拦截：非服务端主线程调用时直接返回 {@code false}（视为「未失能」），
 * 完全不触碰实体的可变效果集合；主线程调用时原逻辑照常执行，语义零变化。损失仅限于
 * 并发窗口内网络线程对失能玩家动作包的一次拦截——该检查本就不该在网络线程进行。
 *
 * <p>目标模组为<b>可选依赖</b>：silent_sun.compat.mixins.json required=false，
 * 不引用目标类任何类型，仅字符串定位。
 */
@Mixin(targets = "net.sweenus.simplyswords.api.IncapacitatingStatusEffectRegistry")
public abstract class SimplySwordsIncapacitatedMixin {

    @Inject(method = "isIncapacitated(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void silentSun$skipOffThreadCheck(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) {
            return;
        }
        Level level = entity.level();
        if (level.isClientSide()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server != null && !server.isSameThread()) {
            cir.setReturnValue(false);
        }
    }

    private SimplySwordsIncapacitatedMixin() {}
}
