/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.registry;

import com.lydiath.silent_sun.entity.RediosEntity;
import com.lydiath.silent_sun.entity.StarfallCurtainEntity;
import com.lydiath.silent_sun.entity.StarfallSalvoEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "silent_sun");
    public static final DeferredHolder<EntityType<?>, EntityType<RediosEntity>> REDIOS = ENTITY_TYPES.register("redios", () -> EntityType.Builder.of(RediosEntity::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10).build(ResourceLocation.fromNamespaceAndPath("silent_sun", "redios").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<StarfallSalvoEntity>> STARFALL_SALVO = ENTITY_TYPES.register("starfall_salvo", () -> EntityType.Builder.<StarfallSalvoEntity>of(StarfallSalvoEntity::new, MobCategory.MISC).sized(3.0f, 3.0f).clientTrackingRange(64).updateInterval(1).build(ResourceLocation.fromNamespaceAndPath("silent_sun", "starfall_salvo").toString()));
    public static final DeferredHolder<EntityType<?>, EntityType<StarfallCurtainEntity>> STARFALL_CURTAIN = ENTITY_TYPES.register("starfall_curtain", () -> EntityType.Builder.<StarfallCurtainEntity>of(StarfallCurtainEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).clientTrackingRange(64).updateInterval(1).build(ResourceLocation.fromNamespaceAndPath("silent_sun", "starfall_curtain").toString()));

    private ModEntities() {
    }
}
