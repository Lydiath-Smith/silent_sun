package com.lydiath.silent_sun.registry;

import com.lydiath.silent_sun.block.CleavingPainBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** silent_sun 方块实体注册表（2026-09-04 裂解之痛召唤祭坛）。 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "silent_sun");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CleavingPainBlockEntity>> CLEAVING_PAIN =
        BLOCK_ENTITIES.register("cleaving_pain",
            () -> BlockEntityType.Builder.of(CleavingPainBlockEntity::new, ModBlocks.CLEAVING_PAIN.get()).build(null));

    private ModBlockEntities() {
    }
}
