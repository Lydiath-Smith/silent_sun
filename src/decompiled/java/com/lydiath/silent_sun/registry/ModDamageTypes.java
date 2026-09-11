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

    /**
     * 断魂伤害类型统一到灭却之日（2026-09-01 用户裁决：效果与伤害类型应为同一个，走我们 9 bypass）：
     * 使用 {@code extinction_day_mod_1784441698:soul_sever}——带 9 个 bypass tag
     * （bypasses_armor/cooldown/effects/enchantments/invulnerability/resistance/shield/wolf_armor
     * + no_knockback），无视护甲/护盾/无敌帧/创造/保护魔咒，与灭却之日 SoulSeverMobEffect
     * 结算共用同一伤害类型（真伤通道）。
     */
    public static final ResourceKey<DamageType> SOUL_SEVER = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("extinction_day_mod_1784441698", "soul_sever"));

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

    /**
     * 2.7 弱点特化「全属性」伤害类型（2026-09-08 用户裁决）：
     * MC 单次伤害只能挂一个 DamageType，故以「专用类型 + 全穿透标签」等价实现
     * 「单次攻击视为包内全部已注册攻击属性」——本类型加入 bypasses_armor/enchantments/
     * effects/resistance/shield/invulnerability 标签（与断魂 soul_sever 同款 9bypass 语义），
     * 该一击护甲/护盾/无敌帧/附魔减伤皆无法豁免，一次全额命中。
     */
    public static final ResourceKey<DamageType> REDIOS_SPECTRUM = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_spectrum"));

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

    /**
     * 构造 2.7 弱点特化「全属性」一击伤害源（来源与直接来源均为 {@code attacker}，
     * 语义同 {@link #rediosAttack}，但类型为带全穿透标签的 redios_spectrum）。
     */
    public static DamageSource rediosSpectrum(Level level, Entity attacker) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(REDIOS_SPECTRUM),
                attacker,
                attacker);
    }
}
