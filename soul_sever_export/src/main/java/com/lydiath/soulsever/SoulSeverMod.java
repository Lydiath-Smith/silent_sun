package com.lydiath.soulsever;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * 断魂效果 — 独立导出模组。
 * <p>
 * 断魂（Soul Sever）是来自《灭却之日》Boss 战的机制：
 * Boss 每次受击时叠加"断魂附加值"到玩家身上，
 * 玩家再次受击时，断魂附加值以绝对伤害形式追加返还。
 * <p>
 * 本模组提取了该效果的核心逻辑，可独立集成到其他 NeoForge 1.21.1 项目中。
 */
@Mod(SoulSeverMod.MOD_ID)
public final class SoulSeverMod {
    public static final String MOD_ID = "soul_sever";

    public SoulSeverMod(IEventBus modEventBus) {
        SoulSeverEvents.MOB_EFFECTS.register(modEventBus);
    }
}
