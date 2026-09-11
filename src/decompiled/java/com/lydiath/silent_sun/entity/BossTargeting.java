package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.config.SilentSunConfig;

import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * 战斗模式与目标判定的统一入口。
 * <p>
 * 解决文字稿核心问题："无论什么模式，对非玩家实体是如何攻击的，受伤又该如何判断"。
 * <p>
 * 两种战斗模式（由配置 PLAYER_BATTLE_ONLY_ENABLED 控制）：
 * <ul>
 *   <li><b>Mode 1（仅玩家战斗）</b>：Boss 只攻击玩家，非玩家实体的伤害仅接受
 *       有主人的宠物（OwnableEntity），且伤害受 FRIENDLY_MOB_DAMAGE_CAP 上限。</li>
 *   <li><b>Mode 2（斗蛐蛐）</b>：Boss 只攻击非玩家实体。可攻击的实体池由
 *       datapack 标签 {@code silent_sun:boss_primary_targets} / {@code silent_sun:boss_mob_targets}
 *       白名单定义；数据包未加载时回退为任意非玩家实体（原行为）。</li>
 * </ul>
 * <p>
 * 反作弊免疫：实体类型命中 {@code silent_sun:boss_cheat_immune} 标签时，
 * 其伤害一律无效且不触发反作弊惩罚。
 */
public final class BossTargeting {

    // ── Datapack tags (must match silent_sun_datapack/data/silent_sun/tags/entity_types/) ──

    private static final ResourceLocation PRIMARY_TARGETS_ID =
        ResourceLocation.fromNamespaceAndPath("silent_sun", "boss_primary_targets");
    private static final ResourceLocation MOB_TARGETS_ID =
        ResourceLocation.fromNamespaceAndPath("silent_sun", "boss_mob_targets");
    private static final ResourceLocation CHEAT_IMMUNE_ID =
        ResourceLocation.fromNamespaceAndPath("silent_sun", "boss_cheat_immune");

    private static final TagKey<EntityType<?>> PRIMARY_TARGETS =
        TagKey.create(Registries.ENTITY_TYPE, PRIMARY_TARGETS_ID);
    private static final TagKey<EntityType<?>> MOB_TARGETS =
        TagKey.create(Registries.ENTITY_TYPE, MOB_TARGETS_ID);
    private static final TagKey<EntityType<?>> CHEAT_IMMUNE =
        TagKey.create(Registries.ENTITY_TYPE, CHEAT_IMMUNE_ID);

    private BossTargeting() {}

    // ── Mode ──

    /** @return true 表示 Mode 1（仅玩家战斗）；false 表示 Mode 2（斗蛐蛐）。 */
    public static boolean playerOnlyMode() {
        return SilentSunConfig.PLAYER_BATTLE_ONLY_ENABLED.get().booleanValue();
    }

    // ── 攻击目标判定（索敌 + doHurtTarget 共用） ──

    /**
     * 判定 Boss 是否应该攻击该目标。
     * 索敌 Goal 的 predicate 与 doHurtTarget 的入口守卫共用此判定，
     * 保证"能索敌到的目标一定能被攻击"的一致性。
     */
    public static boolean isValidAttackTarget(ITargetableHost boss, LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (target instanceof ITargetableHost) return false;

        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (boss.expelledPlayers().contains(player.getUUID())) return false;
            return playerOnlyMode();
        }

        // 非玩家实体：Mode 1 不攻击；Mode 2 按数据包白名单（或回退任意）
        if (playerOnlyMode()) return false;
        return isMobTargetEligible(target);
    }

    // ── 受伤判定（DamagePipeline 攻击者解析共用） ──

    /**
     * 判定来自该攻击者的伤害是否有效。
     * 统一了"谁打 Boss 算数"的规则：
     * <ul>
     *   <li>反作弊免疫实体：一律无效</li>
     *   <li>Mode 1：玩家有效（排除创造/旁观/被驱逐）；非玩家仅当有主人的宠物有效</li>
     *   <li>Mode 2：玩家无效；非玩家按数据包白名单（或回退任意）</li>
     * </ul>
     */
    public static boolean isValidDamageAttacker(ITargetableHost boss, LivingEntity attacker) {
        if (attacker == null) return false;
        if (isCheatImmune(attacker)) return false;

        if (attacker instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            if (boss.expelledPlayers().contains(player.getUUID())) return false;
            return playerOnlyMode();
        }

        if (playerOnlyMode()) {
            return attacker instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
        }
        return isMobTargetEligible(attacker);
    }

    // ── 反作弊免疫 ──

    /** 命中 {@code silent_sun:boss_cheat_immune} 标签的实体类型。标签未定义时返回 false。 */
    public static boolean isCheatImmune(Entity entity) {
        return entity != null && entity.getType().is(CHEAT_IMMUNE);
    }

    // ── Internal ──

    private static boolean isMobTargetEligible(LivingEntity target) {
        EntityType<?> type = target.getType();
        if (type.is(PRIMARY_TARGETS) || type.is(MOB_TARGETS)) {
            return true;
        }
        // 两个白名单标签均未定义（数据包未安装）→ 回退安全过滤，
        // P2-1: 避免无差别屠杀村民/被动生物/宠物
        return tagsUndefined(target.level()) && isSafeFallbackTarget(target);
    }

    /** P2-1: 内置安全过滤——数据包未安装时排除被动/中立/村民/有主宠物。 */
    private static boolean isSafeFallbackTarget(LivingEntity target) {
        if (target instanceof NeutralMob) return false; // 中立：铁傀儡/蜜蜂/狼/猪灵等
        if (target instanceof Npc) return false; // 村民/流浪商人
        if (target instanceof Animal) return false; // 被动动物：猪牛羊鸡兔等
        if (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) return false; // 有主宠物
        return true; // 剩余任意敌对类实体（含末影龙等 Boss 类）
    }

    /**
     * 数据包白名单标签是否「未定义」（两个标签都为空 → 视为未安装数据包，回退安全过滤）。
     * <p>
     * 2026-09-11（代码审计 G06 修复）：**移除原 tagsUndefinedCache**。该缓存只有
     * 「命中标签 → 置 false」一个方向的写入点，**没有任何失效路径** —— 数据包装着时首次判定
     * 就把缓存写成 false，此后管理员移除/改名数据包并 {@code /reload}，标签已不存在却仍读到
     * false → 斗蛐蛐模式（Mode 2）下任何非玩家实体都进不了回退分支，
     * <b>Boss 永久无法攻击任何生物，直到重启服务器</b>。
     * <p>
     * 去掉缓存后每次走 {@code registry.getTag}（HashMap 查表，O(1)）——Mode 2 下每 tick
     * 即使数百次调用也可忽略，换来的是与标签真值永不脱钩（含 /reload 与数据包增删）。
     */
    private static boolean tagsUndefined(Level level) {
        Registry<EntityType<?>> registry =
            level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE);
        Optional<? extends HolderSet.Named<EntityType<?>>> primary = registry.getTag(PRIMARY_TARGETS);
        Optional<? extends HolderSet.Named<EntityType<?>>> mob = registry.getTag(MOB_TARGETS);
        boolean primaryEmpty = primary.isEmpty() || primary.get().size() == 0;
        boolean mobEmpty = mob.isEmpty() || mob.get().size() == 0;
        return primaryEmpty && mobEmpty;
    }
}
