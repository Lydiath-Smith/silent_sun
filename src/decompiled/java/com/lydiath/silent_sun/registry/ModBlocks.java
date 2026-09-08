package com.lydiath.silent_sun.registry;

import com.lydiath.silent_sun.block.CleavingPainBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** silent_sun 方块注册表（2026-09-04 裂解之痛召唤祭坛）。 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("silent_sun");

    /** 裂解之痛：莱德厄斯召唤器基底（岩浆/水状态 → 雷击掉落召唤器 / 召唤 Boss）。 */
    public static final DeferredHolder<Block, Block> CLEAVING_PAIN = BLOCKS.register("cleaving_pain",
        () -> new CleavingPainBlock(BlockBehaviour.Properties.of().strength(2.0f, 6.0f).noOcclusion()));

    private ModBlocks() {
    }
}
