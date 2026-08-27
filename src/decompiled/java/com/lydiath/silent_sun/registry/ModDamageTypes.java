package com.lydiath.silent_sun.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * 模组自定义伤害类型。
 * <p>
 * 伤害类型是数据包注册表（datapack registry），不在代码里注册，
 * 而是通过 {@code data/silent_sun/damage_type/*.json} 自动注册；
 * 其"穿透"属性通过 {@code data/minecraft/tags/damage_type/bypasses_*.json} 标签追加。
 * 代码侧仅保留 ResourceKey 引用，并在结算时按需构造 DamageSource。
 */
public final class ModDamageTypes {

    public static final ResourceKey<DamageType> SOUL_SEVER = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("silent_sun", "soul_sever"));

    /**
     * 莱德厄斯主攻击 / 技能的统一伤害类型。
     * <p>
     * {@code message_id} 为 {@code redios_attack}，死亡文案走 {@code death.attack.redios_attack}
     * （纯台词、不带玩家名）。除死亡文案外，其余减伤行为与原版 {@code mob_attack} 一致，
     * 未加入任何 {@code bypasses_*} 标签。
     */
    public static final ResourceKey<DamageType> REDIOS_ATTACK = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_attack"));

    private ModDamageTypes() {
    }

    /**
     * 构造断魂伤害源（无来源实体）。
     * <p>
     * 保持与原来 {@code target.damageSources().magic()} 相同的"无 directEntity"语义，
     * 这样不会命中 {@code CommonEvents.onLivingIncomingDamage} 的
     * {@code source.getDirectEntity() instanceof RediosEntity} 分支，
     * 从而避免被拔刀剑伤害类型随机化 / 弱点属性攻击改写。
     */
    public static DamageSource soulSever(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SOUL_SEVER));
    }

    /**
     * 构造莱德厄斯主攻击 / 技能伤害源，来源实体（causingEntity）与直接来源（directEntity）
     * 均为 {@code attacker}，与原版 {@code damageSources().mobAttack(mob)} 语义一致。
     */
    public static DamageSource rediosAttack(Level level, Entity attacker) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(REDIOS_ATTACK),
                attacker,
                attacker);
    }
}
