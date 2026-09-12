/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun;

import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.entity.RediosEntity;
import com.lydiath.silent_sun.network.BlackSunDefeatPayload;
import com.lydiath.silent_sun.network.BlackSunRespawnPayload;
import com.lydiath.silent_sun.registry.ModBlockEntities;
import com.lydiath.silent_sun.registry.ModBlocks;
import com.lydiath.silent_sun.registry.ModEffects;
import com.lydiath.silent_sun.registry.ModEntities;
import com.lydiath.silent_sun.registry.ModItems;
import com.lydiath.silent_sun.registry.ModSounds;
import com.lydiath.silent_sun.registry.ModTabs;
import com.lydiath.silent_sun.util.TranslationCompletenessChecker;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(value="silent_sun")
public final class SilentSunMod {
    public static final String MODID = "silent_sun";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SilentSunMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModEffects.MOB_EFFECTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModTabs.CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::onEntityAttributes);
        modEventBus.addListener(this::onEntityAttributesModified);
        modEventBus.addListener(this::onRegisterPayloadHandlers);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                Class<?> clazz = Class.forName("com.lydiath.silent_sun.client.ClientModEvents");
                modEventBus.register(clazz);
            }
            catch (Exception e) {
                // 2026-09-12（审计清理 G01 #4）：原先只有一行 warn + e.toString()，日志里既没有栈、也没说清
                // 「后果」——三个实体渲染器（Redios / StarfallSalvo / StarfallCurtain）缺失后，服务端照常启动，
                // 直到 Boss 或星落演出登场才在客户端崩。这里带上异常对象（保留栈）与后果说明以便直接定位。
                // 级别维持 warn（启动期一次性事件，不会刷屏；且属「同类注册失败」的功能性故障）。（补日志增强，无行为变更）
                LOGGER.warn("客户端事件类 ClientModEvents 注册失败：三个实体渲染器（Redios / StarfallSalvo / "
                    + "StarfallCurtain）均未注册，服务端可正常启动，但 Boss 或星落实体出现时会崩客户端。", e);
            }
        }
        modContainer.registerConfig(ModConfig.Type.COMMON, (IConfigSpec)SilentSunConfig.SPEC);
        if (!FMLLoader.isProduction()) {
            // 开发/测试环境：启动时检查 zh_cn / en_us 翻译键完整性，缺失键打印 warn。
            TranslationCompletenessChecker.check(LOGGER);
        }
    }

    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put((EntityType)ModEntities.REDIOS.get(), RediosEntity.createAttributes().build());
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BlackSunDefeatPayload.TYPE, BlackSunDefeatPayload.STREAM_CODEC, BlackSunDefeatPayload::handleClient);
        registrar.playToServer(BlackSunRespawnPayload.TYPE, BlackSunRespawnPayload.STREAM_CODEC, BlackSunRespawnPayload::handleServer);
    }

    /**
     * SlashBlade（拔刀剑·重铸）兼容修复：
     * <p>
     * SlashBlade 的 {@code AttackHelper#getSweepingBonus} 会无条件读取攻击者的
     * {@code minecraft:player.sweeping_damage_ratio}，但该属性原版仅注册在 Player 上。
     * 当非玩家生物（例如手持 灭刀·断 释放 浮生万仞 SA 的 Redios）作为攻击者时，
     * {@code getAttributeValue} 会抛出 {@link IllegalArgumentException}
     * （Can't find attribute minecraft:player.sweeping_damage_ratio）导致崩溃。
     * <p>
     * 这里把该属性补注册到所有缺少它的生物类型上，使任意非玩家持刀者的伤害计算都能正常进行。
     * 对原版无副作用：普通生物不会读取该属性，仅 SlashBlade 攻击结算时会读取。
     */
    private void onEntityAttributesModified(EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            if (!event.has(type, Attributes.SWEEPING_DAMAGE_RATIO)) {
                event.add(type, Attributes.SWEEPING_DAMAGE_RATIO);
            }
        }
    }
}

