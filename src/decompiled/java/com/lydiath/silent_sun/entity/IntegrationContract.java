package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.config.SilentSunConfig;
// 2026-09-12（SA 名单热配置化）：SA 池三个名单键的热配置载体（redios_rules.json → RediosRules）
import com.lydiath.silent_sun.rules.RediosRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-mod integration contract for Silent Sun.
 * <p>
 * Centralizes all reflection-based cross-mod calls with:
 * <ul>
 *   <li>Explicit logging on reflection failures (no silent swallowing)</li>
 *   <li>TTL-based availability cache (addresses stale Class.forName results)</li>
 *   <li>Weapon compatibility checks (prevent ghost items from uninstalled mods)</li>
 * </ul>
 * <p>
 * Used by {@link BladeAttackGoal} and {@link WeaponManager#tickEquipment()}.
 */
public final class IntegrationContract {

    private static final Logger LOG = LoggerFactory.getLogger("SilentSun:Integration");

    /**
     * 热路径「熔断式 warn」（2026-10-06 日志刷屏加固）：同一 key 全进程生命周期只输出第一条。
     * <p>
     * 适用场景：每 tick / 每次命中 / 每实体循环内的反射调用，在拔刀剑版本漂移导致反射持续失败时，
     * 普通 warn 会按「实体数 × 每 tick」刷成日志洪水。首个 warn 保证故障可见，后续静默。
     * 仅抑制日志、不改变任何控制流（既有"失败后继续重试"的行为保持不变）。
     * 与 {@link #shooterProbeBroken} 的区别：那处连探测一起禁用（失败结果本就无意义），
     * 本方法用于失败后仍需保留重试的路径。
     */
    private static final java.util.Set<String> WARNED_ONCE_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void warnOnce(String key, String format, Object... args) {
        if (WARNED_ONCE_KEYS.add(key)) LOG.warn(format, args);
    }

    // ── Mod identity strings (centralized, single source of truth) ──

    static final String EXTINCTION_DAY_MOD_CLASS = "cn.autoforged.extinction_day_mod_1784441698.ModMain";
    static final String SLASH_BLADE_ITEM_CLASS = "mods.flammpfeil.slashblade.item.ItemSlashBlade";
    static final String SLASH_BLADE_DEFINITION_CLASS = "mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition";
    static final String MIEDAO_DUAN_FIELD = "MIEDAO_DUAN_PROTOTYPE";
    static final String GET_BLADE_METHOD = "getBlade";
    static final String REGISTRY_KEY_FIELD = "REGISTRY_KEY";
    /** Boss 命名刀注册表内的 id，指向前置灭却之日实际注册的命名刀（含灭刀·断·试做模型/刀技/特效） */
    static final String MIEDAO_DUAN_NAMED_BLADE_ID = "extinction_day_mod_1784441698:miedao_duan_prototype";
    /** 随机 SA 反射目标（SlashBlade 自带 slash_arts 注册表，游戏内已注册的 SA 全都在里面） */
    static final String SLASH_ARTS_REGISTRY_CLASS = "mods.flammpfeil.slashblade.registry.SlashArtsRegistry";
    static final String SLASH_ARTS_REGISTRY_FIELD = "REGISTRY";
    static final String SLASH_ARTS_CLASS = "mods.flammpfeil.slashblade.slasharts.SlashArts";
    static final String SLASH_ARTS_ARTSTYPE_CLASS = "mods.flammpfeil.slashblade.slasharts.SlashArts$ArtsType";
    static final String BLADE_STATE_ACCESS_CLASS = "mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess";
    static final String ISLASH_BLADE_STATE_CLASS = "mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState";
    static final String SLASH_ARTS_DO_ARTS_METHOD = "doArts";
    static final String SLASH_ARTS_REGISTRY_KEYSET_METHOD = "keySet";
    static final String SLASH_ARTS_REGISTRY_GET_METHOD = "get";
    static final String BLADE_STATE_ACCESS_OF_METHOD = "of";
    static final String ISLASH_BLADE_STATE_UPDATE_COMBO_METHOD = "updateComboSeq";
    static final String ISLASH_BLADE_STATE_PROGRESS_COMBO_METHOD = "progressCombo";
    /** Boss 每 tick 驱动 combo 生命周期：resolvCurrentComboState 推进超时迁移。
     *  <p>2026-09-11（代码审计 G18 #1 修复）：原注释后半句「ComboState.tickAction 执行
     *  TimeLineTickAction」所指的整条反射链（{@code COMBO_STATE_REGISTRY_CLASS} / {@code _FIELD} /
     *  {@code _GET_METHOD} / {@code COMBO_STATE_CLASS} / {@code COMBO_STATE_TICK_ACTION_METHOD}
     *  五个常量 + 三个静态字段）**零消费方**，且与上述活反射项同处一个 try —— 任一解析失败即把
     *  {@code cachedAvailable} 置 false，**连带整段拔刀剑集成失效**。已删除，见下方静态字段区。 */
    static final String ISLASH_BLADE_STATE_RESOLV_COMBO_METHOD = "resolvCurrentComboState";
    /** slashblade 模组 id（ModList 版本探测用）：重锋 2.0.3 / Refix 2.0.3-0.2.3 */
    static final String SLASHBLADE_MOD_ID = "slashblade";
    /** 重锋版 combo 卡死重置阈值：combo 距上次回到 NONE/standby 超过该 tick 数视为卡死。
     *  400 tick = 20s：合法长 SA（蓄力系多段 TimeoutNext 演出）均 < 20s，不会被误杀；
     *  真卡死（getNext 环/永久停留不回 NONE）20s 后仍会被强制重置。 */
    static final int COMBO_STUCK_RESET_TICKS = 400;
    /** Boss 刀刃强化 setter 名（底层写入 NBT 键 killCount / proudSoul / RepairCounter） */
    static final String ISLASH_BLADE_STATE_SET_KILL_COUNT_METHOD = "setKillCount";
    static final String ISLASH_BLADE_STATE_SET_PROUD_SOUL_COUNT_METHOD = "setProudSoulCount";
    static final String ISLASH_BLADE_STATE_SET_REFINE_METHOD = "setRefine";
    /** Boss 命名刀强化数值（与玩家手中的灭却之日刀完全隔离）：
     *  triple_whammy 需 refine≥30、super_burst_drive 需 killCount≥50（soul_sever 恒定激活），
     *  5000/3000000/300 全量满足，使 3 个 SE 均处于激活状态。 */
    static final int BOSS_BLADE_KILL_COUNT = 5000;
    static final int BOSS_BLADE_PROUD_SOUL_COUNT = 3000000;
    static final int BOSS_BLADE_REFINE = 300;
    /** 注册表里占位的 NONE（slashblade:none），施放时跳过 */
    static final ResourceLocation SLASH_ARTS_NONE_ID = ResourceLocation.fromNamespaceAndPath("slashblade", "none");
    /**
     * Boss 普攻起手段（slashblade:combo_a1）：玩家左键从地面 standby 起手的第一段。
     * Mob 无 IInputState（CapabilityInputState），initStandByCommand 匹配不到 L_CLICK
     * 输入命令而返回 NONE，连击永远停在 NONE 起不了手；需手动强制跳入 A1 段。
     */
    static final ResourceLocation COMBO_A1_ID = ResourceLocation.fromNamespaceAndPath("slashblade", "combo_a1");
    static final ResourceLocation SLASH_BLADE_STANDBY_ID = ResourceLocation.fromNamespaceAndPath("slashblade", "standby");
    /** EntityDrive（剑气）实体：slashblade:drive，反射构造不硬依赖 slashblade */
    static final String ENTITY_DRIVE_CLASS = "mods.flammpfeil.slashblade.entity.EntityDrive";
    static final String SLASHBLADE_DRIVE_ENTITY_ID = "slashblade:drive";
    static final String ENTITY_DRIVE_SET_DAMAGE_METHOD = "setDamage";
    static final String ENTITY_DRIVE_SET_SPEED_METHOD = "setSpeed";
    static final String ENTITY_DRIVE_SET_COLOR_METHOD = "setColor";
    static final String ENTITY_DRIVE_SET_LIFETIME_METHOD = "setLifetime";
    /** 剑气参数：速度 / 存活 tick / 颜色（super_burst_drive 同款蓝紫） */
    static final float DRIVE_SPEED = 2.5f;
    static final float DRIVE_LIFETIME = 30.0f;
    static final int DRIVE_COLOR = 0x3333FF;
    /** 刀光（EntitySlashEffect）反射缓存：普攻斩击轨迹 + triple_whammy 三连特效 */
    /** EntityAbstractSummonedSword（剑气/幻影剑基类）：手动碰撞 doForceHitEntity 绕过 pvp_enable 拦玩家 */
    static final String ENTITY_ABSTRACT_SUMMONED_SWORD_CLASS = "mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword";
    static final String ENTITY_ABSTRACT_SUMMONED_SWORD_DO_FORCE_HIT_METHOD = "doForceHitEntity";
    /** EntityAbstractSummonedSword 暴击开关：强制关暴击以规避 EntityDrive.onHitEntity
     *  负伤害 + 暴击 分支 random.nextInt(负) 抛 "Bound must be positive" 崩服。 */
    static final String ENTITY_ABSTRACT_SUMMONED_SWORD_GET_IS_CRITICAL_METHOD = "getIsCritical";
    static final String ENTITY_ABSTRACT_SUMMONED_SWORD_SET_IS_CRITICAL_METHOD = "setIsCritical";
    /** 手动碰撞已命中目标去重 NBT 键（存 int id 列表，随实体销毁自动清理） */
    static final String BOSS_BLADE_HIT_TARGETS_TAG = "SilentSunForceHitTargets";
    /** AttackManager.doAttackWith(DamageSource,float,Entity,boolean,boolean)：刀光/次元斩无 doForceHitEntity，
     *  唯一可反射的「对单目标强制结算」入口（内部即 target.hurt(src, amount) + invulnerableTime 处理）。 */
    static final String ATTACK_MANAGER_CLASS = "mods.flammpfeil.slashblade.util.AttackManager";
    static final String ATTACK_MANAGER_DO_ATTACK_WITH_METHOD = "doAttackWith";
    /** IShootable.getDamage()：刀光/次元斩的基础伤害值（幻影剑用 setDamage 写入的等价基准） */
    static final String ISHOOTABLE_GET_DAMAGE_METHOD = "getDamage";
    /** 幻影剑发射方法名：doFire() 定义在 SpiralSwords/StormSwords/BlisteringSwords/HeavyRainSwords
     *  各自子类里（基类 EntityAbstractSummonedSword 没有），故反射需按实体具体 Class 动态解析。 */
    static final String SUMMONED_SWORD_DO_FIRE_METHOD = "doFire";
    /** 幻影剑骑乘环绕 → 发射 的停留 tick 数（复刻"召唤一圈后射出"的剑技节奏） */
    static final int PHANTOM_SWORD_FIRE_DELAY_TICKS = 15;
    /** 幻影剑发射延迟 NBT 键（存 int 目标 tick，随实体销毁自动清理） */
    static final String PHANTOM_SWORD_FIRE_AT_TAG = "SilentSunPhantomFireAt";
    /** 幻影剑「已改射向目标」NBT 标记（避免每 tick 重复改射、打断已发射的飞行） */
    static final String PHANTOM_SWORD_RETARGET_TAG = "SilentSunPhantomRetargeted";
    /** EntitySlashEffect（刀光）实体：slashblade:slash_effect，普攻斩击轨迹 / triple_whammy 三连特效 */
    static final String ENTITY_SLASH_EFFECT_CLASS = "mods.flammpfeil.slashblade.entity.EntitySlashEffect";
    /** SlashBladeEvent$DoSlashEvent：玩家挥刀事件（T-v3-7 反射监听，不硬依赖 slashblade）。 */
    static final String SLASH_BLADE_EVENT_DO_SLASH_CLASS = "mods.flammpfeil.slashblade.event.SlashBladeEvent$DoSlashEvent";
    static final String DO_SLASH_EVENT_GET_USER_METHOD = "getUser";
    /**
     * SlashBladeEvent$ChargeActionEvent：玩家**蓄力**事件（{@code ItemSlashBlade.onUseTick} 里 post）。
     * <p>
     * 2026-09-12（产出观测）：第三方 SA 常挂在蓄力/挥刀事件上（例如 recasting 的
     * {@code TimeBeyondSlashArts.onCharge(ChargeActionEvent)} 就是 {@code @SubscribeEvent}）。当产出落在
     * 我方驱动窗口之外时，本锚点用于判断它是否**玩家蓄力**触发 —— 与 {@code DoSlashEvent} 锚点互补。
     */
    static final String SLASH_BLADE_EVENT_CHARGE_CLASS =
        "mods.flammpfeil.slashblade.event.SlashBladeEvent$ChargeActionEvent";
    /**
     * ChargeActionEvent 的持有者读取方法名。
     * <p>
     * <b>javap 确证（两次独立取证）</b>：它是 {@code getEntityLiving()LivingEntity}，**不叫 {@code getUser}**
     * （那是 DoSlashEvent 的 API）—— {@code SlashBladeEventHandler.onChargeBlade} 与 recasting 的
     * {@code TimeBeyondSlashArts.onCharge} 的字节码都调 {@code getEntityLiving()}。该类另有
     * {@code getSlashBladeState()} / {@code getChargeTicks()}。
     */
    static final String SLASH_BLADE_EVENT_CHARGE_GET_ENTITY_METHOD = "getEntityLiving";
    static final String REGISTRY_EVENTS_CLASS = "mods.flammpfeil.slashblade.RegistryEvents";
    static final String REGISTRY_EVENTS_SLASH_FIELD = "SlashEffect";
    /** 幻影剑基类实体（slashblade:summoned_sword）：玩家 onInputChange 直发的基础幻影剑，生成即 shoot() */
    static final String REGISTRY_EVENTS_SUMMONED_SWORD_FIELD = "SummonedSword";
    static final String ENTITY_SLASH_EFFECT_SET_KNOCKBACK_ORDINAL_METHOD = "setKnockBackOrdinal";
    static final String ENTITY_SLASH_EFFECT_SET_ROTATION_ROLL_METHOD = "setRotationRoll";
    static final String ENTITY_SLASH_EFFECT_SET_COLOR_METHOD = "setColor";
    static final String ENTITY_SLASH_EFFECT_SET_MUTE_METHOD = "setMute";
    static final String ENTITY_SLASH_EFFECT_SET_IS_CRITICAL_METHOD = "setIsCritical";
    /** IShootable 接口：EntityAbstractSummonedSword / EntitySlashEffect / EntityJudgementCut 均实现，
     *  getShooter()/setShooter() 三者都委托 getOwner()/setOwner()。用接口反射可统一给无 owner 的
     *  拔刀剑投射物补 shooter，覆盖全部实现类（不只 EntityAbstractSummonedSword 一族）。 */
    static final String ISHOOTABLE_CLASS = "mods.flammpfeil.slashblade.entity.IShootable";
    static final String ISLASH_BLADE_STATE_GET_COLOR_CODE_METHOD = "getColorCode";
    static final String ISLASH_BLADE_STATE_GET_REFINE_METHOD = "getRefine";
    static final String ISLASH_BLADE_STATE_GET_SPECIAL_EFFECTS_METHOD = "getSpecialEffects";
    /** 灭却之日 triple_whammy SE 的注册 id path（hasSpecialEffect 精确匹配的退化遍历判断） */
    static final String TRIPLE_WHAMMY_SE_PATH = "triple_whammy";

    // ── 2026-09-12（tickAction 运行时探针）：钉死「持刀 Mob 上 ComboState.tickAction 是否被调用」──
    //
    // 问题：两份独立字节码分析都指向「不会」—— ComboState.tickAction 的**全局唯一调用点**是
    // ItemSlashBlade.lambda$inventoryTick$12，而 ItemStack.inventoryTick 在 NeoForge 21.1.235 / MC 1.21.1
    // 的唯一调用点是玩家物品栏 Inventory（Mob/LivingEntity/Entity 全 0 处）。但本仓库内有互相矛盾的
    // 旧注释（BladeAttackGoal:103-107 与 tryTickBladeComboStuckGuard 的 javadoc 均称「持刀 Mob 每 tick
    // 被 slashblade 驱动」），且作者有「第三方 SA 可以释放」的实战观察。本探针用**实测**裁决。
    //
    // 做法（**只读 + 记录**，不改 slashblade 源码、不加 Mixin、不改任何行为）：
    //   ① 指纹（主判据）：{@code ComboState$TimeLineTickAction.accept} 每推进一帧就写
    //      {@code entity.getPersistentData()} 的 {@link #COMBO_LAST_PROCESSED_TICK_KEY_LITERAL}
    //      （javap 确证：ldc "slashblade.lastProcessedTick" + CompoundTag.putInt(elapsed+1)）。
    //      该键**在整个 slashblade 里只有这一个写入者**，故「值 > 0」⇒ tickAction 确实在 Boss 上执行过；
    //      「活动 combo 持续数十 tick 而键恒 0」⇒ 无任何驱动者调用 tickAction。
    //   ② 帧数（排除假阴性）：反射读当前 ComboState.tickAction 的运行时类型与其 timeLine Map 的 size，
    //      证明「这个 combo 真的有一份非空时间线」——排除「时间线本来就是空的，所以不写键」。
    //   ③ 对照（证明指纹机制本身有效）：持刀玩家（inventoryTick 有驱动者）身上的同一指纹。
    static final String COMBO_STATE_CLASS = "mods.flammpfeil.slashblade.registry.combo.ComboState";
    static final String COMBO_STATE_REGISTRY_CLASS = "mods.flammpfeil.slashblade.registry.ComboStateRegistry";
    static final String COMBO_STATE_REGISTRY_FIELD = "REGISTRY";
    static final String COMBO_STATE_LAST_PROCESSED_TICK_KEY_FIELD = "LAST_PROCESSED_TICK_KEY";
    static final String COMBO_STATE_GET_ELAPSED_METHOD = "getElapsed";
    static final String COMBO_STATE_TICK_ACTION_FIELD = "tickAction";
    /** {@code ComboState.clickAction} 字段名：必须与 tickAction 一起读运行时类型，否则无法区分
     *  「时间线被外部驱动」与「我方 clickAction 恰好是个时间线对象」（见 {@link #comboActionTypes}）。 */
    static final String COMBO_STATE_CLICK_ACTION_FIELD = "clickAction";
    static final String COMBO_STATE_TIME_LINE_FIELD = "timeLine";
    static final String COMBO_STATE_TIME_LINE_TICK_ACTION_CLASS =
        "mods.flammpfeil.slashblade.registry.combo.ComboState$TimeLineTickAction";
    static final String ISLASH_BLADE_STATE_GET_COMBO_SEQ_METHOD = "getComboSeq";
    /** 指纹键名兜底字面量（javap 确证 {@code ComboState.LAST_PROCESSED_TICK_KEY} 的值即此字符串）。 */
    static final String COMBO_LAST_PROCESSED_TICK_KEY_LITERAL = "slashblade.lastProcessedTick";

    // ── Availability cache ──

    private static volatile Boolean cachedAvailable;
    private static volatile long lastAvailabilityCheck;
    private static final long AVAILABILITY_CACHE_TTL_MS = 30_000L; // 30s TTL
    /** Boss 刀刃强化日志只打印一次，避免每次重装配（刀窗口逐 tick 触发）刷屏。 */
    private static volatile boolean bossBladeStatsLogged = false;

    // ── slashblade 版本探测 + combo 卡死监控（重锋/Refix 运行时差异适配，2026-09-01）──

    /** null=未探测；true=Refix 版（2.0.3-0.2.3，历史 jar 验证无卡死，不做卡死检测）；
     *  false=重锋版/未知（默认带卡死检测，防御性）。 */
    private static volatile Boolean refixRuntime;

    /** combo 卡死监控（重锋版）：key=caster，value=combo 活跃跟踪。WeakHashMap 防实体泄漏。
     *  2026-09-01 改版：原「连续停留」检测有两大缺陷——① TimeoutNext 未超时 getNext 返回
     *  「自己」，长 SA（>120 tick）被误杀中断结算；② getNext 环（A1→…→A5→A1）combo id 每
     *  tick 变化，停留计数恒 0 检测失效。现改为「距上次回到 NONE/standby 的 tick」：正常 combo
     *  总会回 standby 刷新计时，环/永久停留不回则超阈值被重置。 */
    private static final java.util.Map<LivingEntity, ComboStuckState> COMBO_STUCK_TRACKERS = new java.util.WeakHashMap<>();

    /** 单个 caster 的 combo 活跃跟踪：lastStandbyTick = 最近一次 combo 处于 NONE/standby 的 tickCount */
    private static final class ComboStuckState {
        int lastStandbyTick = -1;
    }

    /**
     * 探测 slashblade 运行时版本：Refix（2.0.3-0.2.3）返回 true，重锋（2.0.3）/未知返回 false。
     * <p>
     * 字节码对比结论（2026-09-01）：两版 progressCombo/getNext 逐指令一致，唯一实质差异是
     * ComboState$TimeLineTickAction 的 lastProcessedTick 存储——Refix 存实体数据（实体独立），
     * 重锋是实例字段（ComboState 注册表单例全实体共享），且重锋 combo 注册内容重写（238+ lambdas
     * vs Refix 122+）——combo 对 Mob 可能卡活跃段回不到 NONE/standby，tickAction 每 tick 刷刀光。
     * Refix 版历史 jar 验证无此问题，不做卡死检测。
     */
    private static boolean isRefixRuntime() {
        Boolean cached = refixRuntime;
        if (cached != null) return cached;
        boolean refix = false;
        try {
            var container = net.neoforged.fml.ModList.get().getModContainerById(SLASHBLADE_MOD_ID);
            if (container.isPresent()) {
                String version = container.get().getModInfo().getVersion().toString();
                // Refix 版本号形如 "2.0.3-0.2.3"（带补丁后缀），重锋形如 "2.0.3"
                refix = version != null && version.contains("0.2.3");
            }
        } catch (Throwable t) {
            // 探测失败默认重锋模式（带卡死检测，防御性）；Refix 正常流程永不触发检测
            LOG.warn("Failed to detect slashblade version, default to 重锋 mode (combo stuck guard active): {}", t.toString());
        }
        refixRuntime = refix;
        return refix;
    }

    // ── Reflection cache ──

    private static volatile Object miedaoPrototype;
    private static volatile Method miedaoGetMethod;
    private static volatile Class<?> slashBladeItemClass;
    private static volatile ResourceKey<?> slashBladeRegistryKey;
    private static volatile Method slashBladeGetBladeMethod;
    private static volatile Object slashArtsRegistry;
    private static volatile Method slashArtsRegistryKeySetMethod;
    private static volatile Method slashArtsRegistryGetMethod;
    private static volatile Object artsTypeSuccess;
    private static volatile Method slashArtsDoArtsMethod;
    private static volatile Method bladeStateAccessOfMethod;
    private static volatile Method updateComboSeqMethod;
    private static volatile Method progressComboMethod;
    private static volatile Method setKillCountMethod;
    private static volatile Method setProudSoulCountMethod;
    private static volatile Method setRefineMethod;
    private static volatile Method resolvCurrentComboStateMethod;
    // EntityDrive（剑气）反射缓存
    private static volatile Class<?> entityDriveClass;
    private static volatile java.lang.reflect.Constructor<?> entityDriveCtor;
    private static volatile Method driveSetDamageMethod;
    private static volatile Method driveSetSpeedMethod;
    private static volatile Method driveSetColorMethod;
    private static volatile Method driveSetLifetimeMethod;
    private static volatile Method driveShootMethod;
    private static volatile Method driveSetOwnerMethod;
    // EntitySlashEffect（刀光）反射缓存：普攻斩击轨迹 + triple_whammy 三连特效
    private static volatile Class<?> entitySlashEffectClass;
    private static volatile java.lang.reflect.Constructor<?> entitySlashEffectCtor;
    private static volatile Object slashEffectEntityType;
    private static volatile Method slashEffectSetDamageMethod;
    private static volatile Method slashEffectSetKnockBackOrdinalMethod;
    private static volatile Method slashEffectSetRotationRollMethod;
    private static volatile Method slashEffectSetColorMethod;
    private static volatile Method slashEffectSetMuteMethod;
    private static volatile Method slashEffectSetIsCriticalMethod;
    private static volatile Method slashEffectSetPosMethod;
    private static volatile Method slashEffectSetOwnerMethod;
    private static volatile Method slashEffectSetYRotMethod;
    private static volatile Method slashEffectSetXRotMethod;
    // EntityAbstractSummonedSword 手动碰撞命中（doForceHitEntity）：绕过 pvp_enable=false 拦玩家
    private static volatile Class<?> entityAbstractSummonedSwordClass;
    private static volatile Method summonedSwordDoForceHitEntityMethod;
    // EntityAbstractSummonedSword.shoot(double,double,double,float,float)：改射 truepower 幻影剑朝目标飞行
    private static volatile Method summonedSwordShootMethod;
    // EntityAbstractSummonedSword 暴击开关（getIsCritical/setIsCritical）：强制关暴击规避负伤害暴击崩溃
    private static volatile Method summonedSwordGetIsCriticalMethod;
    private static volatile Method summonedSwordSetIsCriticalMethod;
    // Boss 专用幻影剑（EntityAbstractSummonedSword 基类，slashblade:summoned_sword）反射直发缓存
    private static volatile Object summonedSwordEntityType;
    private static volatile java.lang.reflect.Constructor<?> summonedSwordCtor;
    private static volatile Method summonedSwordSetColorMethod;
    private static volatile Method summonedSwordSetDamageMethod;
    private static volatile Method summonedSwordSetRollMethod;
    private static volatile Method summonedSwordSetOwnerMethod;
    // IShootable 接口 + setShooter(Entity)：给无 owner 的拔刀剑投射物（幻影剑/刀光/次元斩）补 shooter，
    // 规避玩家 ArrowReflector 扫描到 getShooter()==null 的 IShootable 时 NPE。
    private static volatile Class<?> iShootableClass;
    private static volatile Method iShootableSetShooterMethod;
    private static volatile Method iShootableGetShooterMethod;
    // DoSlashEvent（玩家挥刀）反射缓存：T-v3-7 全阶段移动避让（远程挥刀反向冲刺）。
    private static volatile Class<?> doSlashEventClass;
    private static volatile Method doSlashEventGetUserMethod;
    private static volatile boolean doSlashListenerRegistered = false;
    // ChargeActionEvent（玩家蓄力）锚点反射缓存（2026-09-12 产出观测）：失败只禁用该锚点，不影响主链路。
    private static volatile Class<?> chargeEventClass;
    private static volatile Method chargeEventGetUserMethod;
    private static volatile boolean chargeListenerRegistered = false;
    // IShootable.getDamage()：刀光/次元斩基础伤害（AttackManager.doAttackWith 需要显式传伤害量）
    private static volatile Method iShootableGetDamageMethod;
    // AttackManager.doAttackWith(DamageSource,float,Entity,boolean,boolean)：刀光/次元斩无
    // doForceHitEntity，这是对单目标强制结算（target.hurt + invulnerableTime 处理）的唯一反射入口。
    private static volatile Method attackManagerDoAttackWithMethod;
    // 幻影剑 doFire()：定义在 SpiralSwords/StormSwords/BlisteringSwords/HeavyRainSwords 子类，
    // 基类无此方法，故按实体具体 Class 缓存（key=className）。
    private static final java.util.Map<String, Method> summonedSwordDoFireMethodCache = new java.util.concurrent.ConcurrentHashMap<>();
    // 无 doFire() 的幻影剑子类缓存（key=className）。ConcurrentHashMap 不允许 null 值，
    // 故用独立 Set 记录「无此方法」的类，避免缓存 null 触发 NPE。
    private static final Set<String> summonedSwordNoDoFireClasses = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // ISlashBladeState 读取（SE 判断 / 刀色）
    private static volatile Method getColorCodeMethod;
    private static volatile Method getRefineMethod;
    private static volatile Method getSpecialEffectsMethod;
    private static volatile boolean reflectionInitialized;
    /** SA 注册表键集缓存：slash_arts 启动注册完成后基本不变，避免每次施放随机 SA
     *  都反射 keySet + 拷贝 HashSet/ArrayList（中距离 SA 每 80~120 tick 一次）。
     *  2026-09-01：加 TTL 定期刷新——运行时其他 mod（KubeJS 等）新注册的 SA 能收录进随机池。 */
    private static volatile List<Object> cachedSlashArtsKeys;
    private static volatile long cachedSlashArtsKeysAt;
    /** SA 键集缓存有效期：60s 后重新 keySet 收录新增 SA */
    private static final long SLASH_ARTS_KEYS_TTL_MS = 60_000L;
    // 2026-09-12（SA 名单热配置化）：空白名单告警「只打一次」的标记 —— isSaAllowed 在池重建时对每个
    // 候选 SA 各调一次，不能在它里面打日志（会刷屏）；标记由取值方法 warnEmptySaWhitelistOnce 维护，
    // 名单恢复非空时复位。
    private static volatile boolean emptySaWhitelistWarned;
    // 2026-09-12（SA 名单热配置化）：池为空时的诊断快照（每次真正重建池缓存时刷新），
    // 用于区分「slash_arts 注册表真的空」与「注册表非空但被三份名单滤空」（后者才是常见情况，
    // 也是作者唯一能发现「namespace 拼错 / 模组没装 / 白名单写空」的地方）。
    private static volatile int cachedSlashArtsRawCount = -1;
    private static volatile List<String> cachedSlashArtsExcludedSample = List.of();
    // 2026-09-12（SA 名单热配置化）：**池为空告警的节流标记**（池恢复非空时复位）。
    // 池被滤空是一个**持续状态**，而候选池为空的分支在每次施放尝试时都会走到（约每 80~120 tick），
    // 不节流会一直刷同一条诊断。体检上「池为什么空」不会每 tick 变化，故只报一次。
    private static volatile boolean saPoolEmptyWarned;
    // 2026-09-29（ALL 黑名单模式）：白名单**内容**的上次播报快照 —— 模式/条目变化时才播一次，
    // 支持 reload 后「白名单 ⇄ 黑名单」反复翻转。必须用 List.equals 比较（不可 ==）。
    private static volatile List<String> lastAnnouncedSaWhitelist;

    // ── 2026-09-12（tickAction 运行时探针）反射缓存：全部只读，失败只降级该探针 ──

    private static volatile Method getComboSeqMethod;
    private static volatile Method comboGetElapsedMethod;
    private static volatile Object comboStateRegistry;
    private static volatile Method comboStateRegistryGetMethod;
    private static volatile Field comboStateTickActionField;
    /** {@code ComboState.clickAction} 字段（与 tickAction 一起读，用于区分是哪条链在跑时间线）。 */
    private static volatile Field comboStateClickActionField;
    private static volatile Field comboStateTimeLineField;
    private static volatile Class<?> timeLineTickActionClass;
    /** 探针反射解析状态：{@code null} = 尚未解析（每次进入都会重试）。 */
    private static volatile Boolean comboProbeResolved;
    /** 探针反射不可用的原因（非 null 时报告的 tickActionProbe 会带上，便于定位是探针坏了还是结论如此）。 */
    private static volatile String comboProbeUnavailableReason;
    /** 指纹键名：反射读 {@code ComboState.LAST_PROCESSED_TICK_KEY} 优先，失败回退字面量。 */
    private static volatile String comboLastProcessedTickKeyInstant;
    private static volatile boolean comboFingerprintKeyFromReflection;
    /** 「本 comboSeq 的 tickAction/clickAction 运行时类型 + 时间线帧数」缓存（注册表启动后不变，可无限期缓存）。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, ComboActionTypes> COMBO_ACTION_TYPES =
        new java.util.concurrent.ConcurrentHashMap<>();
    /** silent_sun 自己写 comboSeq 的最近一次归属（实体 id + 该实体 tickCount），供探针归因「这次变化是谁造成的」。 */
    private static volatile int selfComboWriteEntityId = Integer.MIN_VALUE;
    private static volatile int selfComboWriteTick = Integer.MIN_VALUE;
    /**
     * 我方主动写 comboSeq 的**单调计数**（每次 {@link #markSelfComboWrite} 自增）。
     * <p>
     * 产出观测用它做**窗口级**归因：扫描是每 N tick 一次，若用「距上次写入不超过 N tick」判断，
     * 会漏掉窗口前半段的 clickAction 产物（实体在写入后 3 tick 才被扫到）→ 那些产物会被误判成
     * 「我方窗口之外」的第三条路径。用计数差才能准确回答「本扫描窗口内我方到底写过没有」。
     */
    private static volatile long selfComboWriteCount;

    // ── Public API ──

    /**
     * Check if both required mods (extinction_day_mod + SlashBlade) are present.
     * Cached for 30s before re-checking to avoid per-tick Class.forName overhead.
     */
    public static boolean isSlashBladeIntegrationAvailable() {
        long now = System.currentTimeMillis();
        if (cachedAvailable != null && (now - lastAvailabilityCheck) < AVAILABILITY_CACHE_TTL_MS) {
            return cachedAvailable;
        }
        synchronized (IntegrationContract.class) {
            if (cachedAvailable != null && (now - lastAvailabilityCheck) < AVAILABILITY_CACHE_TTL_MS) {
                return cachedAvailable;
            }
            try {
                Class.forName(EXTINCTION_DAY_MOD_CLASS);
                Class.forName(SLASH_BLADE_ITEM_CLASS);
                cachedAvailable = true;
                // 版本断言（改进项 2）：类存在但关键 API 签名不匹配 → 明确告警而非静默降级。
                // slashblade / sbr_core 升级后若 getBlade 方法签名变化，此处能快速定位。
                // 2026-08-30 修正：getBlade 实际定义在 SlashBladeDefinition（命名刀注册表 value 类），
                // 而非 ItemSlashBlade（其上只有 getBladeId）——原断言查错类导致两版 jar 均误报 WARN。
                // 实测重锋版 2.0.3 与 Refix 版该签名一致，断言通过即代表装备链路可用。
                // 2026-09-11（代码审计 G18 #2 修复）：内层原先只 catch NoSuchMethodException，而
                // Class.forName(SLASH_BLADE_DEFINITION_CLASS) 抛的是 ClassNotFoundException ——
                // 它会穿透内层、被**外层**的 catch 捕获，把 cachedAvailable 置 false，于是
                // 「slashblade 已加载、只是该内部类名/位置与预期不同」被误判为「前置缺失」，
                // **整段拔刀剑集成判死**。版本断言只用于告警，不应参与可用性判定 ⇒ 改捕公共父类。
                try {
                    Class<?> definitionClass = Class.forName(SLASH_BLADE_DEFINITION_CLASS);
                    definitionClass.getMethod(GET_BLADE_METHOD, Item.class, HolderLookup.Provider.class);
                } catch (ReflectiveOperationException apiErr) {
                    LOG.warn("[版本断言] SlashBlade 已加载，但 {} 或其 getBlade({}, HolderLookup.Provider) 不可用 —— " +
                        "slashblade 版本可能升级过 API，Boss 拔刀剑装备/SA 可能异常。请核对 slashblade 版本。",
                        SLASH_BLADE_DEFINITION_CLASS, Item.class.getSimpleName());
                }
            } catch (ClassNotFoundException e) {
                cachedAvailable = false;
            }
            lastAvailabilityCheck = now;
            return cachedAvailable;
        }
    }

    // 2026-09-11（代码审计 G18 #3 修复）：原 invalidateAvailabilityCache() 全库零调用者，
    // 且它只重置 cachedAvailable 与 lastAvailabilityCheck 两个字段，达不到「刷新可用性」的语义
    // （reflectionInitialized 与 SA 池缓存都不清）—— 已删除。

    /**
     * Attempt to equip the boss with 灭刀·断·试做 via reflection.
     * <p>
     * 与灭却之日一致：从 SlashBlade 命名刀注册表（slashblade:named_blades）取出
     * {@code miedao_duan_prototype} 定义，并调用 {@code getBlade(Item, Provider)} 生成
     * 带全套 NBT（模型 / 贴图 / 刀技 / 特效）的刀刃，而非裸 ItemStack（否则显示默认刀）。
     * Logs on failure instead of swallowing.
     *
     * @return the equipped ItemStack on success, ItemStack.EMPTY on failure
     */
    public static ItemStack tryEquipBlade(Entity boss) {
        if (!ensureReflectionReady()) return ItemStack.EMPTY;
        try {
            Object item = miedaoGetMethod.invoke(miedaoPrototype);
            if (!(item instanceof Item bladeItem)) {
                LOG.warn("MIEDAO_DUAN_PROTOTYPE.get() returned non-Item: {}", item);
                return ItemStack.EMPTY;
            }
            // 命名刀链路（照抄灭却之日 recipe → SlashBladeDefinition.getBlade()）
            if (slashBladeRegistryKey != null && slashBladeGetBladeMethod != null) {
                try {
                    HolderLookup.Provider registries = boss.registryAccess();
                    // raw type 编译期调用：RegistryLookup#get(ResourceKey) 在接口上编译期绑定，
                    // 避免对运行时类型（MappedRegistry$1 匿名类）反射 getMethod——旧实现反射到
                    // MappedRegistry$1 时模块系统拒绝访问抛 IllegalAccessException，导致每次装配
                    // 失败回退裸刀（显示默认阎魔刀模型）。返回的 Holder.Reference 需 value() 解包
                    // 才是真正的 SlashBladeDefinition，再走 getBlade 生成命名刀。
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    HolderLookup.RegistryLookup lookup = registries.lookupOrThrow((ResourceKey) slashBladeRegistryKey);
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    ResourceKey elementKey = ResourceKey.create((ResourceKey) slashBladeRegistryKey,
                        ResourceLocation.parse(MIEDAO_DUAN_NAMED_BLADE_ID));
                    Optional<?> refOpt = lookup.get(elementKey);
                    Object definition = null;
                    if (refOpt.isPresent()) {
                        Object value = ((Holder<?>) refOpt.get()).value();
                        if (value != null) definition = value;
                    }
                    if (definition != null) {
                        ItemStack stack = (ItemStack) slashBladeGetBladeMethod.invoke(definition, bladeItem, registries);
                        if (stack != null && !stack.isEmpty()) {
                            applyBossBladeStats(stack);
                            return stack;
                        }
                        LOG.warn("getBlade returned empty stack for {}, falling back to plain item.",
                            MIEDAO_DUAN_NAMED_BLADE_ID);
                    } else {
                        LOG.warn("Named blade {} not found in registry, falling back to plain item.",
                            MIEDAO_DUAN_NAMED_BLADE_ID);
                    }
                } catch (Exception e) {
                    LOG.warn("Named blade path failed ({}), falling back to plain item.", e.toString());
                }
            }
            // fallback：裸 ItemStack（至少保证武器能装上）
            ItemStack plain = new ItemStack(bladeItem);
            applyBossBladeStats(plain);
            return plain;
        } catch (Exception e) {
            LOG.warn("Failed to create 灭刀·断 item via reflection: {}", e.toString());
        }
        return ItemStack.EMPTY;
    }

    /**
     * 将 Boss 命名刀强化到全 SE 激活水平（与玩家手中的灭却之日刀完全隔离）：
     * 反射调用 ISlashBladeState.setKillCount / setProudSoulCount / setRefine
     * （底层写入 NBT 键 killCount / proudSoul / RepairCounter）。
     * <p>
     * 旧版灭却之日 SE 激活条件：triple_whammy 需 refine≥30、super_burst_drive 需
     * killCount≥50，soul_sever 恒定激活——Boss 数值（5000/3000000/300）全部满足，
     * 因此 3 个 SE 均处于激活状态。失败仅记日志，不阻断装配。
     */
    private static void applyBossBladeStats(ItemStack stack) {
        try {
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, stack);
            if (stateOpt instanceof Optional<?> opt && opt.isPresent()) {
                Object state = opt.get();
                setKillCountMethod.invoke(state, BOSS_BLADE_KILL_COUNT);
                setProudSoulCountMethod.invoke(state, BOSS_BLADE_PROUD_SOUL_COUNT);
                setRefineMethod.invoke(state, BOSS_BLADE_REFINE);
                if (!bossBladeStatsLogged) {
                    bossBladeStatsLogged = true;
                    LOG.info("Boss blade (灭刀·断) empowered: killCount={}, proudSoul={}, refine={}",
                        BOSS_BLADE_KILL_COUNT, BOSS_BLADE_PROUD_SOUL_COUNT, BOSS_BLADE_REFINE);
                }
            } else {
                LOG.warn("BladeStateAccess.of returned empty for boss blade, SE stats not applied.");
            }
        } catch (Exception e) {
            LOG.warn("Failed to empower boss blade via reflection: {}", e.toString());
        }
    }

    /**
     * 从 SlashBlade 的 slash_arts 注册表（游戏内已注册的全部 SA）随机挑选一个施放。
     * <p>
     * 链路：doArts(ArtsType.Success, caster) 解析出 combo 状态 id，
     * 再通过 {@code BladeStateAccess.of(mainHand).updateComboSeq(caster, combo)}
     * 触发 —— 与玩家右键施放 SA 完全一致：applyComboSeq 播 BladeMotionEvent（客户端动作用）+
     * ComboState.clickAction（实际攻击动作）。
     * 跳过 NONE 占位条目。
     */
    public static void tryInvokeRandomSA(LivingEntity caster) {
        if (!ensureReflectionReady()) return;
        // 2026-08-15：攻击力 ≤ 0 时跳过随机 SA——部分 SA 的 clickAction/tickAction
        // 会生成 isCritical=true 的 EntityDrive，负伤害命中触发与剑气相同的
        // "Bound must be positive" 崩服。
        if (caster.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0.0) return;
        // 2026-09-12（战斗流程报告）：SA_CAST 采集上下文（旁路，不参与任何判定）。
        // 注：反射未就绪 / 攻击力 ≤0 的提前返回**没有已选定的 SA**，故不记事件；
        // 选定 SA 之后的每一条 return 都会记一条（ok=false = 未能施放），抛异常记 error 类名。
        final RediosEntity flowBoss = caster instanceof RediosEntity r ? r : null;
        // 2026-09-12（产出观测）：施放前模组实体快照 —— 只在战斗报告开启时采集（关闭时零开销）。
        final java.util.Map<Integer, ProbeEntityInfo> flowBefore =
            flowBoss != null && flowBoss.flowReportActive()
                ? scanModEntities(caster, PRODUCTION_SCAN_RADIUS) : null;
        String flowSaId = null;
        try {
            // 缓存 SA 注册表键集：slash_arts 启动注册完成后基本不变，避免每次施放
            // 随机 SA 都反射 keySet + 拷贝 HashSet/ArrayList；TTL 过期重新 keySet，
            // 收录运行时新注册的 SA（KubeJS 等）。
            List<Object> keyList = cachedSlashArtsKeys;
            long now = System.currentTimeMillis();
            if (keyList == null || (now - cachedSlashArtsKeysAt) > SLASH_ARTS_KEYS_TTL_MS) {
                Object registry = slashArtsRegistry;
                @SuppressWarnings("unchecked")
                Set<Object> keys = new java.util.HashSet<>((Set<Object>) slashArtsRegistryKeySetMethod.invoke(registry));
                // 2026-09-12（战斗流程报告）：过滤前留一份全量 id（供池快照算「排掉了哪些」）。
                List<String> flowAllIds = new ArrayList<>();
                for (Object flowKey : keys) {
                    if (flowKey instanceof ResourceLocation flowRl) {
                        flowAllIds.add(flowRl.toString());
                    }
                }
                // NeoForge 1.21.1：Registry.keySet() 返回 ResourceLocation（非 ResourceKey）。
                // 2026-09-01：随机池黑名单过滤，2026-09-12 扩展为 namespace + SA id 双粒度。
                // 注：原注释把排除 foxextra 的理由写成「其 SA 有 SE 前提 + 时间线每帧多实体是洪峰源」，
                // 该归因已于 2026-09-12 经源码+javap 双重验证推翻（详见 isSaAllowed 的 javadoc），
                // 现保留 foxextra 整包排除**仅因**其中的 foxextra:thrust 会让 Mob 抛 CCE，
                // 且已改为只排那一个 id（config redios.bossSaExcludedSaIds）。
                keys.removeIf(k -> k instanceof ResourceLocation rl
                    && (!isSaAllowed(rl) || SLASH_ARTS_NONE_ID.equals(rl)));
                keyList = new ArrayList<>(keys);
                cachedSlashArtsKeys = keyList;
                cachedSlashArtsKeysAt = now;
                // 2026-09-12（战斗流程报告）：SA 池快照——只在本分支（60s TTL 到期、真正重建池缓存）
                // 采集，不额外开销。全量 id 需在 removeIf 之前留一份，才能算出「排掉了哪些」。
                List<String> flowInPool = new ArrayList<>();
                for (Object flowKey : keyList) {
                    flowInPool.add(String.valueOf(flowKey));
                }
                List<String> flowExcluded = new ArrayList<>(flowAllIds);
                flowExcluded.removeAll(flowInPool);
                reportSaPool(flowBoss, flowInPool, flowExcluded);
                // 2026-09-12（SA 名单热配置化）：留下池为空时的诊断数据（注册表原始条目数 + 被滤掉的 id 采样）。
                // 只在真正重建缓存时算一次，池为空的告警分支直接读这两个值，零额外反射开销。
                cachedSlashArtsRawCount = flowAllIds.size();
                cachedSlashArtsExcludedSample = flowExcluded.size() > 5
                    ? new ArrayList<>(flowExcluded.subList(0, 5)) : new ArrayList<>(flowExcluded);
            }
            if (keyList.isEmpty()) {
                // 2026-09-12（SA 名单热配置化）：原实现只有一句 "slash_arts registry is empty"，把
                // 「注册表真的空」与「注册表非空但被三份名单滤空」混为一谈 —— 后者才是常见情况，且是
                // 作者唯一能发现「白名单配错（namespace 拼错 / 模组装错 / 写成 []）」的地方。
                // 故分成两种措辞：滤空时打出原始条目数、滤后池大小、当前生效的三条规则与被滤掉的 id 采样。
                // 2026-09-12（节流）：本分支在**每次施放尝试**时都会走到，而池为空是持续状态 ⇒
                // 用 saPoolEmptyWarned 只报一次；池恢复非空时在下方复位，保证「再次被滤空」仍能告警。
                if (!saPoolEmptyWarned) {
                    saPoolEmptyWarned = true;
                    if (cachedSlashArtsRawCount > 0) {
                        LOG.warn("[SilentSun] SA 候选池被名单滤空：slash_arts 注册表共 {} 条，滤后 0 条。"
                                + "当前生效规则 —— 模式={}，白名单 namespace={}，排除 namespace={}，排除 SA id={}；"
                                + "被滤掉的 id 采样（最多 5 个）：{}。"
                                + "若这不是本意，请检查 silent_sun/redios_rules.json 的 boss_sa_whitelist_namespaces"
                                + "（首位写 \"ALL\" = 黑名单模式；写 [] = 显式全禁；namespace 拼错或模组未装都会导致池为空）。"
                                + "本告警每次「滤空 → 恢复」只报一次。",
                            cachedSlashArtsRawCount, saWhitelistModeLabel(), saWhitelistNamespaces(),
                            saExcludedNamespaces(), saExcludedSaIds(), cachedSlashArtsExcludedSample);
                    } else {
                        LOG.warn("slash_arts registry is empty, cannot invoke random SA.");
                    }
                }
                // 2026-09-12（战斗流程报告）：候选池为空 = 一次「未能施放」。
                reportSaCast(flowBoss, null, false, null, "候选池为空（slash_arts 注册表无可用条目）");
                return;
            }
            // 2026-09-12（SA 名单热配置化）：池非空 ⇒ 复位告警标记，使「再次被滤空」能再次告警。
            saPoolEmptyWarned = false;
            Object key = keyList.get(caster.getRandom().nextInt(keyList.size()));
            // 2026-09-12（战斗流程报告）：选定即记下 id，供后续失败/异常留痕使用。
            flowSaId = String.valueOf(key);
            Object raw = slashArtsRegistryGetMethod.invoke(slashArtsRegistry, key);
            Object slashArts;
            if (raw instanceof Optional<?> opt && opt.isPresent()) {
                Object ref = opt.get();
                // 防御性解包：本版本 get(ResourceLocation) 直接返回 SlashArts，
                // 仅当未来签名变回 Optional<Holder.Reference> 时才会走到这里。
                slashArts = ref instanceof Holder<?> h ? h.value() : ref;
            } else {
                slashArts = raw;
            }
            if (slashArts == null) {
                // 2026-09-12（战斗流程报告）：未能施放（注册表返回的 SA 为 null）。
                reportSaCast(flowBoss, flowSaId, false, null, "注册表返回的 SA 为 null");
                return;
            }
            // 2026-09-12（作者需求：完善 SA 黑名单）：施放留痕。
            // 黑名单按 **namespace** 排除（config redios.bossSaExcludedNamespaces，默认
            // foxextra / tianshaxing），但此前成功施放**完全静默** —— 整个方法只有「注册表为空」
            // 一条 warn，于是「该往黑名单里再加什么」无据可依：log 里搜到的全是注册表/mixin 元数据，
            // 没有任何一次实际施放的 SA id。
            // 这里记录**完整 id**（形如 slashblade:judgement_cut），便于按 namespace 归类统计。
            // 频率 = BladeAttackGoal 的中距离分支 80~120 tick 一次（整场战斗数十条）。
            // 2026-10-03（高频日志检修）：降为 DEBUG——每次施放已由战斗流程报告 JSON 记录，不再刷 INFO。
            LOG.debug("[SilentSun] Boss 随机施放 SA：{}（候选池 {} 个）", key, keyList.size());
            Object combo = slashArtsDoArtsMethod.invoke(slashArts, artsTypeSuccess, caster);
            if (!(combo instanceof ResourceLocation comboLoc)) {
                // 2026-09-12（战斗流程报告）：未能施放（doArts 没解析出 combo id）。
                reportSaCast(flowBoss, flowSaId, false, null, "doArts 未返回 combo id（该 SA 对 Mob 无有效 clickAction）");
                return;
            }
            ItemStack blade = caster.getMainHandItem();
            if (blade.isEmpty()) {
                // 2026-09-12（战斗流程报告）：未能施放（主手非刀）。
                reportSaCast(flowBoss, flowSaId, false, null, "主手非刀，BladeStateAccess 取不到状态");
                return;
            }
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
            if (stateOpt instanceof Optional<?> opt && opt.isPresent()) {
                updateComboSeqMethod.invoke(opt.get(), caster, comboLoc);
                // 2026-09-12（tickAction 探针）：归因标记（旁路，只影响报告里 selfDriven 一列）
                markSelfComboWrite(caster);
                // 2026-09-12（产出观测）：本次施放**同一次调用内**新增的实体（空 = 这次 SA 空放）。
                reportSaProduction(flowBoss, flowSaId, caster, flowBefore);
                // 2026-09-12（战斗流程报告）：施放成功（combo 已下发给刀状态机）。
                reportSaCast(flowBoss, flowSaId, true, null, null);
            } else {
                // 2026-09-12（战斗流程报告）：未能施放（BladeStateAccess.of 返回空）。
                reportSaCast(flowBoss, flowSaId, false, null, "BladeStateAccess.of 返回空");
            }
        } catch (Exception e) {
            LOG.warn("Failed to invoke random slash art via reflection: {}", e.toString());
            // 2026-09-12（战斗流程报告）：抛异常 = 黑名单首要判据（saStats.failures > 0 的 error 来源）。
            reportSaCast(flowBoss, flowSaId, false, e.getClass().getName(),
                flowSaId == null ? "异常发生在选定 SA 之前" : "反射调用抛异常");
        }
    }

    // ── 2026-09-12（战斗流程报告）：SA 相关留痕（旁路；开关关闭时零开销） ──

    /** SA_CAST 留痕：施放者不是 Boss（或开关关闭）时无操作；失败不影响施放链路。 */
    private static void reportSaCast(RediosEntity boss, String saId, boolean ok, String error, String note) {
        if (boss == null) {
            return;
        }
        boss.flowSaCast(saId, ok, error, note);
    }

    /**
     * SA 池快照留痕：入池 / 被排除 id 列表 + 当时的**全部规则**。
     * <p>
     * 2026-09-12（白名单化）：规则由「黑名单」改为「白名单 + 两层二次排除」，故快照也一并记录
     * 白名单 —— 不记的话报告答不了「池为什么是这些」（验收第 5 条）。
     */
    private static void reportSaPool(RediosEntity boss, List<String> inPool, List<String> excluded) {
        if (boss == null) {
            return;
        }
        try {
            // 2026-09-12（SA 名单热配置化）：快照改走取值方法 —— 否则报告的名单仍印静态键的旧值，
            // 答不了「池为什么是这些」（键已迁到 json，热配置才是当前生效来源）；读取失败记空表。
            List<String> whitelist = saWhitelistNamespaces();
            List<String> excludedNamespaces = saExcludedNamespaces();
            List<String> excludedSaIds = saExcludedSaIds();
            boss.flowSaPool(inPool, excluded,
                whitelist == null ? List.of() : whitelist,
                excludedNamespaces == null ? List.of() : excludedNamespaces,
                excludedSaIds == null ? List.of() : excludedSaIds);
        } catch (Throwable t) {
            LOG.warn("Failed to report SA pool snapshot: {}", t.toString());
        }
    }

    /**
     * SA 池名单取值（**热配置优先 / 静态配置兜底**，均按三态语义）——三个取值方法的统一契约，实现见下三处。
     * <p>
     * 2026-09-12（SA 名单热配置化）：白名单 / 二次排除 namespace / 二次排除 SA id 三个名单键已迁到热配置
     * {@code silent_sun/redios_rules.json}（{@code boss_sa_whitelist_namespaces} 等三键，由
     * {@code RediosRulesReloadListener} 解析进 {@link RediosRules}），改完重载即生效、无需重启服务器。
     * {@code SilentSunConfig} 里原来的三个静态键**保留**作兼容回退。
     * <p>
     * <b>三态语义</b>（区分「没配」与「显式配空」是本次迁移的硬要求，否则「写 [] 想禁掉全部 SA」会被
     * 静默回退成默认名单，现象是「改了 json + reload 看起来正常、白名单其实没变」）：
     * <ul>
     *   <li>热配置值 {@code != null} ⇒ <b>原样返回</b>（**空表也直接返回**，那是作者的显式意图）；</li>
     *   <li>热配置值 {@code == null}（键从未配置）⇒ 回退读 {@code SilentSunConfig} 静态键；</li>
     *   <li>整个过程 try/catch：静态键 {@code get()} 在 FML 未初始化时会抛，读取失败按现有语义
     *       「不放行 + warn」⇒ 返回 {@code null}，由 {@link #isSaAllowed(ResourceLocation)} 判为拒绝。</li>
     * </ul>
     * <b>为何失败返回 {@code null} 而不是空表</b>：空表在②③两条排除判定里恰好是「不排除 ＝ 放行」，
     * 会把配置损坏变成放行；{@code null} 才能同时满足「三条判定通用」与「失败即拒绝」。
     *
     * @return 生效名单；热配置与静态回退都读不到时返回 {@code null}（＝读取失败，调用方拒绝放行）
     */
    private static List<String> saWhitelistNamespaces() {
        try {
            // 2026-09-12（SA 名单热配置化）：三态 —— null=键从未配置 ⇒ 回退静态；空表=显式全禁 ⇒ 原样返回
            List<String> hot = RediosRules.bossSaWhitelistNamespaces();
            if (hot != null) {
                warnEmptySaWhitelistOnce(hot, "silent_sun/redios_rules.json 的 boss_sa_whitelist_namespaces");
                return hot;
            }
            // 2026-09-12（SA 名单热配置化）：热配置为 null（未配置）⇒ 回退静态键（改静态 toml 仍需重启）
            List<String> fallback = new ArrayList<>(SilentSunConfig.BOSS_SA_WHITELIST_NAMESPACES.get());
            warnEmptySaWhitelistOnce(fallback, "config 的 redios.bossSaWhitelistNamespaces（静态回退）");
            return fallback;
        } catch (Throwable t) {
            // 2026-09-12（SA 名单热配置化）：两个来源都读不到 ⇒ 降级为 null（调用方判为拒绝放行），
            // 并留 warn —— 否则「SA 池莫名变空」无从定位（与改动前的兜底方向一致）。
            LOG.warn("[SilentSun] SA 白名单（热配置 + 静态回退）读取失败，本次不放行任何 SA：{}", t.toString());
            return null;
        }
    }

    /**
     * 2026-09-12（SA 名单热配置化）：空白名单的**一次性**告警。
     * <p>
     * 白名单为空时 {@link #isSaAllowed(ResourceLocation)} 每次调用都返回 false，而它在池重建（60s TTL）
     * 时会对每个候选 SA 各调一次 —— 在那里打日志会刷屏，故把告警放在这里并用静态标记只打一次。
     * 名单恢复非空时复位标记，于是「修好又写空」能再次告警。
     */
    private static void warnEmptySaWhitelistOnce(List<String> whitelist, String source) {
        if (whitelist != null && !whitelist.isEmpty()) {
            // 2026-09-12（SA 名单热配置化）：恢复非空 ⇒ 复位标记，下次再被清空时能重新告警
            emptySaWhitelistWarned = false;
            return;
        }
        if (emptySaWhitelistWarned) {
            return;
        }
        emptySaWhitelistWarned = true;
        // 2026-09-12（SA 名单热配置化）：点明后果 + 指向具体键名，避免「改了没生效且无提示」
        LOG.warn("[SilentSun] SA 白名单为空（来源：{}）⇒ Boss 不会施放任何 SA（候选池必为空）。"
                + "若这不是你的本意，请检查 boss_sa_whitelist_namespaces：写 [] 表示显式全禁，"
                + "键缺失/写成非数组才会回退 config 的 redios.bossSaWhitelistNamespaces。", source);
    }

    /** 二次排除 namespace 名单；热配置优先（空表=显式不排除）、静态兜底，读取失败返回 {@code null}（= 拒绝放行）。 */
    private static List<String> saExcludedNamespaces() {
        try {
            // 2026-09-12（SA 名单热配置化）：三态 —— 只有 null（未配置）才回退静态键；空表的含义是「不排除任何 namespace」
            List<String> hot = RediosRules.bossSaExcludedNamespaces();
            if (hot != null) {
                return hot;
            }
            return new ArrayList<>(SilentSunConfig.BOSS_SA_EXCLUDED_NAMESPACES.get());
        } catch (Throwable t) {
            LOG.warn("[SilentSun] SA 二次排除 namespace 名单（热配置 + 静态回退）读取失败，本次不放行任何 SA：{}", t.toString());
            return null;
        }
    }

    /** 二次排除 SA id 名单；热配置优先（空表=显式不排除）、静态兜底，读取失败返回 {@code null}（= 拒绝放行）。 */
    private static List<String> saExcludedSaIds() {
        try {
            // 2026-09-12（SA 名单热配置化）：三态 —— 只有 null（未配置）才回退静态键；空表的含义是「不排除任何 SA id」
            List<String> hot = RediosRules.bossSaExcludedSaIds();
            if (hot != null) {
                return hot;
            }
            return new ArrayList<>(SilentSunConfig.BOSS_SA_EXCLUDED_SA_IDS.get());
        } catch (Throwable t) {
            LOG.warn("[SilentSun] SA 二次排除 id 名单（热配置 + 静态回退）读取失败，本次不放行任何 SA：{}", t.toString());
            return null;
        }
    }

    /**
     * 2026-09-29（ALL 黑名单模式）：判断一个白名单条目是否为 {@code ALL} 哨兵。
     * 容忍首尾空格与大小写（{@code trim().toUpperCase(ROOT)}）；只在白名单<b>第一个位置</b>调用本方法。
     */
    private static boolean isSaAllModeEntry(String s) {
        return s != null && "ALL".equals(s.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /** 当前生效白名单的模式标签（滤空诊断日志用）：首位 ALL ⇒ {@code "黑名单(ALL)"}，否则 {@code "白名单"}。 */
    private static String saWhitelistModeLabel() {
        List<String> whitelist = saWhitelistNamespaces();
        return whitelist != null && !whitelist.isEmpty() && isSaAllModeEntry(whitelist.get(0))
            ? "黑名单(ALL)" : "白名单";
    }

    /**
     * 2026-09-29（ALL 黑名单模式）：白名单内容变化时<b>一次性</b>播报当前模式。
     * <p>
     * isSaAllowed 在池重建时对每个候选 SA 各调一次，故用上次播报内容（{@link List#equals}）去重；
     * 热配置 reload 后内容变化（白名单 ⇄ 黑名单、ALL 后增删条目）会再次播报：
     * <ul>
     *   <li>进入 ALL：INFO 说明「除两个排除名单外全部放行」并列出当前排除名单；</li>
     *   <li>ALL 后还有条目：WARN 说明这些条目已被忽略（黑名单模式下不生效）；</li>
     *   <li>非 ALL（含首次启动）：INFO 说明白名单模式及当前名单。</li>
     * </ul>
     */
    private static void announceSaWhitelistModeOnce(List<String> whitelist) {
        List<String> last = lastAnnouncedSaWhitelist;
        if (last != null && last.equals(whitelist)) {
            return;
        }
        lastAnnouncedSaWhitelist = whitelist == null ? null : List.copyOf(whitelist);
        boolean allMode = whitelist != null && !whitelist.isEmpty() && isSaAllModeEntry(whitelist.get(0));
        if (allMode) {
            LOG.info("[SilentSun] SA 池以黑名单模式（白名单首位 ALL）运作：除两个排除名单外全部 SA 放行。"
                    + "排除 namespace={}，排除 SA id={}",
                saExcludedNamespaces(), saExcludedSaIds());
            if (whitelist.size() > 1) {
                LOG.warn("[SilentSun] ALL 之后的 {} 个条目已被忽略（黑名单模式下它们不生效）：{}",
                    whitelist.size() - 1, whitelist.subList(1, whitelist.size()));
            }
        } else {
            LOG.info("[SilentSun] SA 池以白名单模式运作，当前白名单 namespace={}", whitelist);
        }
    }

    /**
     * SA 随机池过滤（**白名单模式** + 两层二次排除；2026-09-01 引入黑名单，2026-09-12 用户裁决改为白名单）。
     * <p>
     * 2026-09-12（SA 名单热配置化）：下面三个名单键已迁到热配置 {@code silent_sun/redios_rules.json}
     * （{@code boss_sa_whitelist_namespaces} / {@code boss_sa_excluded_namespaces} /
     * {@code boss_sa_excluded_sa_ids}），取值统一经 {@link #saWhitelistNamespaces()} /
     * {@link #saExcludedNamespaces()} / {@link #saExcludedSaIds()}（热配置优先、键缺失才回退静态配置），
     * 本方法不再直接读配置。三重判定与顺序保持不变；白名单被显式配成空表时本方法恒返回 false
     * （全禁），告警在取值方法里做一次性输出，此处不打日志以免刷屏。
     * <p>
     * 放行条件（三者**全部**满足）：
     * <ol>
     *   <li>namespace ∈ {@code redios.bossSaWhitelistNamespaces}（默认 7 个已分析过的模组）；</li>
     *   <li>namespace ∉ {@code redios.bossSaExcludedNamespaces}
     *       （默认 {@code tianshaxing} 天杀星刀 —— SA 以 SE 为硬性前提，Boss 刀无对应 SE；
     *        以及 {@code annihilationblade} / {@code annihilationbladeex} 湮灭之刃 —— 清除系作弊 SA）；</li>
     *   <li>完整 id ∉ {@code redios.bossSaExcludedSaIds}
     *       （默认 {@code foxextra:thrust} —— 其 combo {@code foxextra:thrust_ex} 的时间线
     *        {@code put(2, …)} 调 {@code Thrust.doSlash}，源码即 {@code (Player) playerIn} 硬转，
     *        施放者为 Mob 时必抛 {@code ClassCastException}）。</li>
     * </ol>
     * <p>
     * <b>为什么从黑名单改成白名单</b>：黑名单是「默认信任、事后拉黑」—— 新装模组的 SA 会自动进池，
     * 要等它出问题才发现（{@code foxextra:thrust} 就是典型：它的 {@code checkcast Player} 只是因为
     * Mob 上 combo 时间线不跑才暂时没炸，一旦 combo 驱动恢复就是当场崩）。白名单是 fail-safe：
     * 新模组默认不进池，必须针对性测过才放行。配合 {@code /silent_sun battle_report on} 的战斗报告，
     * 「针对性测试」有现成手段（看 {@code saCasts} 的 ok / error / note 决定去留）。
     * <p>
     * <b>2026-09-12 归因订正</b>（本条原先把「整包排除 foxextra」的理由写为「其 SA 有 SE 前提 +
     * 时间线每帧多实体 = 刀光洪峰源」，经 <b>foxextra 源码 + javap 字节码双重验证，两条均不成立</b>）：
     * ① SE 方向是反的 —— {@code SummonSword} 在每次 {@code DoSlashEvent} 上额外生成 5 个剑雨实体，
     * 是**放大器**而非「缺 SE 就放不出」；且 Boss 刀 {@code miedao_duan_prototype} 的
     * {@code special_effects} 只有 soul_sever / triple_whammy / super_burst_drive，本就不含它；
     * ② Boss(Mob) 上 combo 时间线**根本不执行** —— {@code ItemStack.inventoryTick} 的调用点只有
     * {@code Inventory}（玩家物品栏），Mob 无驱动者 ⇒ 只有 {@code clickAction} 生效，
     * 故 {@code void_slash_plus} 当前是空放、{@code sakura_endex} 才是 foxextra 里唯一真有输出的。
     * 「刀光洪峰」的真实驱动源仍未知，已另立运行时排查项，<b>勿再归因到 foxextra 时间线</b>。
     * <p>
     * <b>2026-09-29 新增 ALL 黑名单模式</b>：白名单首位（trim + 忽略大小写）为 {@code "ALL"} 时，
     * 放行公式变为 {@code namespace ∉ 排除namespace ∧ id ∉ 排除id} —— 第①重白名单跳过、
     * ②③两重排除照常生效；ALL 之后的其余条目忽略并 warn；排除名单读取失败（null）仍拒绝放行。
     * 模式切换经 announceSaWhitelistModeOnce 在白名单内容变化时一次性播报。
     */
    static boolean isSaAllowed(ResourceLocation rl) {
        try {
            // 2026-09-12（SA 名单热配置化）：三处取值改为调本类取值方法（热配置优先 / 静态兜底）；
            // 取值失败返回 null ⇒ 拒绝放行（fail-closed，两种模式一致）。
            List<String> whitelist = saWhitelistNamespaces();
            if (whitelist == null) {
                return false;
            }
            // 2026-09-29（ALL 黑名单模式）：白名单首位 trim+忽略大小写等于 ALL ⇒ allMode=true，
            // 跳过第①重 namespace 白名单（全部 namespace 默认放行），仅由②③两重排除决定去留。
            boolean allMode = !whitelist.isEmpty() && isSaAllModeEntry(whitelist.get(0));
            announceSaWhitelistModeOnce(whitelist);
            // ① 白名单：白名单模式下，不在名单里的 namespace 一律不进池（fail-safe）；ALL 模式跳过本重
            if (!allMode && !whitelist.contains(rl.getNamespace())) {
                return false;
            }
            // ② namespace 二次排除（ALL 模式下与③同为仅有的排除手段）
            List<String> excludedNamespaces = saExcludedNamespaces();
            if (excludedNamespaces == null || excludedNamespaces.contains(rl.getNamespace())) {
                return false;
            }
            // ③ SA id 二次排除
            List<String> excludedSaIds = saExcludedSaIds();
            return excludedSaIds != null && !excludedSaIds.contains(rl.toString());
        } catch (Exception e) {
            // 2026-09-12（白名单化）：兜底方向**翻转** —— 配置读取失败时**拒绝**放行，而非全放行。
            // 白名单模式里「放行」才是危险方向：若此处仍 return true，配置一损坏就退化成全放行，
            // 恰好把白名单要防的事（未测过的第三方 SA 进池）重新引进来。
            // 2026-09-29（ALL 模式）：黑名单模式下 fail-closed 同样必要 —— 否则配置一损坏，
            // ALL 模式就退化成无边界全放行，风险方向与白名单模式一致。
            // 补一条 warn：否则「SA 池莫名变空」将无从定位。
            LOG.warn("[SilentSun] SA 名单配置读取失败，本次不放行任何 SA：{}", rl, e);
            return false;
        }
    }

    /**
     * 推进拔刀剑普攻连击（等价玩家左键连段）。
     * <p>
     * 反射调用 {@code ISlashBladeState.progressCombo(LivingEntity)}：基于当前 combo 的
     * getNext() 推演下一段 → updateComboSeq → clickAction（实际攻击动作）。连击链走到尽头
     * 自动回到 NONE，下次调用从第一段重新开始——与玩家连打左键完全一致。
     * A3/A4/A5 的 TimeLineTickAction 由 slashblade ItemSlashBlade.inventoryTick 对持刀 Mob
     * 每 tick 驱动（2026-09-01 确认，详见 tryTickBladeComboStuckGuard 注释）。
     */
    public static void tryProgressCombo(LivingEntity caster) {
        if (!ensureReflectionReady()) return;
        try {
            ItemStack blade = caster.getMainHandItem();
            if (blade.isEmpty()) return;
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
            if (stateOpt instanceof Optional<?> opt && opt.isPresent()) {
                Object state = opt.get();
                // Mob 无 IInputState（CapabilityInputState）：玩家左键会先向输入状态注入
                // L_CLICK 命令再调 progressCombo，initStandByCommand 据此匹配 combo_a1 起手；
                // Mob 拿到的命令集恒为空 → standby.getNext() 返回 slashblade:none，连击永远
                // 停在 NONE，A1 段（斩击 doSlash / 剑气 DoSlashEvent）永远无法起手。
                // 当前处于 NONE/standby 时强制 updateComboSeq 跳入 combo_a1（普通攻击第一段），
                // 绕过输入命令判定；A1→A2→…→A5 由各段 getNext（TimeoutNext 帧推进）自动衔接。
                Object curLoc = resolvCurrentComboStateMethod.invoke(state, caster);
                if (!(curLoc instanceof ResourceLocation rl)) return;
                // TODO(审计清理 G18 #5)：同一「当前处于 NONE/standby 待机」判定在本文件两处各写一遍（此处起手段 + combo 卡死检测处），未抽公共谓词 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
                if (SLASH_ARTS_NONE_ID.equals(rl) || SLASH_BLADE_STANDBY_ID.equals(rl)) {
                    updateComboSeqMethod.invoke(state, caster, COMBO_A1_ID);
                } else {
                    progressComboMethod.invoke(state, caster);
                }
                // 2026-09-12（tickAction 探针）：归因标记（旁路，只影响报告里 selfDriven 一列）
                markSelfComboWrite(caster);
            }
        } catch (Exception e) {
            // 近战中每 3~5 tick 调用：反射持续失败时按熔断式 warn 只记首条（2026-10-06）
            warnOnce("combo_progress", "Failed to progress slash blade combo via reflection: {}", e.toString());
        }
    }

    /**
     * Boss 剑气（EntityDrive）反射直发，等效 super_burst_drive 每次挥刀发射的剑气。
     * <p>
     * 灭却之日 {@code SuperBurstDriveEffect.onDoingSlash} 带 {@code instanceof Player} 检查，
     * Boss（Mob）挥刀触发 DoSlashEvent 时剑气被跳过（doBurstDrive 参数是 Player）。
     * 这里按同一链路反射构造 {@code slashblade:drive} 实体：伤害取 Boss 攻击力、
     * 方向取当前朝向（配合 lookAt 已同步 yRot），颜色/存活与 super_burst_drive 同款，
     * 但不设暴击（见方法内注释，防负伤害崩溃）。失败仅记日志，不影响其他攻击。
     */
    public static void trySpawnBurstDrive(LivingEntity caster) {
        if (!ensureReflectionReady()) return;
        try {
            Level level = caster.level();
            if (level.isClientSide()) return;
            // EntityDrive.onHitEntity 会把伤害再乘 owner.ATTACK_DAMAGE 且无 clamp：
            // 若施法者被 WEAKNESS 诅咒减攻为负，剑气伤害为负 → 暴击加值
            // random.nextInt(ceil(负)/2+2) 抛 "Bound must be positive" 崩溃。
            // 攻击力 ≤ 0 时不发射（虚弱期本就该削弱输出），同时避免负伤害给目标回血。
            if (caster.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0.0) return;
            @SuppressWarnings({"unchecked", "rawtypes"})
            Holder<EntityType<?>> holder = (Holder) level.registryAccess()
                .lookupOrThrow(Registries.ENTITY_TYPE)
                .getOrThrow((ResourceKey) ResourceKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.parse(SLASHBLADE_DRIVE_ENTITY_ID)));
            EntityType<?> type = holder.value();
            Object drive = entityDriveCtor.newInstance(type, level);
            Entity entity = (Entity) drive;
            // 出生点：眼睛高度的 3/4 + 朝向 0.3 格偏移（super_burst_drive 同款）
            Vec3 pos = caster.position()
                .add(0.0, caster.getEyeHeight() * 0.75, 0.0)
                .add(caster.getLookAngle().scale(0.3));
            entity.setPos(pos);
            // EntityDrive.onHitEntity 命中时会将伤害再乘 owner.ATTACK_DAMAGE（×30），
            // 若此处直接 setDamage(攻击力) 会得到 30×30=900 的秒杀级伤害。
            // 固定 base=1.0 → 命中伤害 ≈ 攻击力(30) × scale(1.0) ≈ 普攻同级，随配置缩放。
            driveSetDamageMethod.invoke(drive, 1.0);
            driveSetSpeedMethod.invoke(drive, DRIVE_SPEED);
            driveSetColorMethod.invoke(drive, DRIVE_COLOR);
            // 不设暴击（保持默认 false）：onHitEntity 的暴击加值
            // random.nextInt(ceil(伤害)/2+2) 在伤害为负时 bound≤0 抛 IllegalArgumentException
            //（2026-08-12 崩溃根因，Boss 被 WEAKNESS 减攻为负时命中触发）。
            // 不暴击则跳过该分支，负伤害会被 vanilla actuallyHurt 的 if (damage > 0) 安全忽略。
            driveSetLifetimeMethod.invoke(drive, DRIVE_LIFETIME);
            driveSetOwnerMethod.invoke(drive, caster);
            Vec3 dir = caster.getLookAngle();
            driveShootMethod.invoke(drive, dir.x, dir.y, dir.z, DRIVE_SPEED, 0.0f);
            level.addFreshEntity(entity);
        } catch (Exception e) {
            LOG.warn("Failed to spawn burst drive (剑气) via reflection: {}", e.toString());
        }
    }

    /**
     * Boss 单次命中后触发灭却之日 triple_whammy SE（三连击）：近战 / 剑气 / 幻影剑每一把
     * 命中独立触发一次（2026-10-04 用户裁决）。
     * <p>
     * 灭却之日 {@code TripleWhammyEffect.onSlashBladeHit} 监听 SlashBladeEvent.HitEvent
     * 且带 {@code instanceof Player} 检查——Boss（Mob）挥刀命中永远进不来。这里按同款
     * 逻辑反射复刻（cfr 反编译 TripleWhammyEffect.checkAndApply）：
     * 主手刀 SE 列表含 triple_whammy 且 refine≥30 → 目标中心生成 2 个 damage=0 刀光
     * （旋转随机、颜色取刀刃）+ invulnerableTime 清零后 2 次 mobAttack 伤害（等同 Boss
     * 普攻攻击力，与玩家版取 ATTACK_DAMAGE 一致）。全部失败仅记日志，不影响原伤害。
     */
    public static void tryApplyBossTripleWhammy(LivingEntity boss, LivingEntity target) {
        if (!ensureReflectionReady()) return;
        try {
            // 触发口径（2026-10-04 用户裁决）：每次命中独立判定——剑气 / 幻影剑 / 斩击每一把命中
            // 都触发一次三连，不做「每目标每 tick 一次」限频（旧限频会把同 tick 内多把剑的触发静默吞掉）。
            // 「额外斩击类 SE 单次连锁各只能触发一次」由调用结构天然保证：本方法追加的两道刀光
            // damage=0（被碰撞扫描扫到也在「命中落地」判定前 return，不回流），两次追加伤害直接走
            // target.hurt，不经过 doHurtTarget / forceHitBladeTarget 这两个唯二触发点。
            Level level = boss.level();
            if (level.isClientSide()) return;
            ItemStack blade = boss.getMainHandItem();
            if (blade.isEmpty() || !isSlashBladeItem(blade.getItem())) return;
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
            if (!(stateOpt instanceof Optional<?> opt && opt.isPresent())) return;
            Object state = opt.get();
            if (!hasTripleWhammySE(state)) return;
            int refine = ((Number) getRefineMethod.invoke(state)).intValue();
            if (refine < 30) return;
            float damage = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (damage <= 0.0f) return;
            int color = bladeColorCode(blade);
            Vec3 targetPos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
            for (int i = 0; i < 2; ++i) {
                spawnSlashEffect(boss, targetPos, boss.getRandom().nextFloat() * 360.0f, color, false, false, 0.0);
            }
            int savedInvuln = target.invulnerableTime;
            target.invulnerableTime = 0;
            target.hurt(boss.damageSources().mobAttack(boss), damage);
            target.invulnerableTime = 0;
            target.hurt(boss.damageSources().mobAttack(boss), damage);
            target.invulnerableTime = savedInvuln;
        } catch (Exception e) {
            // 每把剑/剑气/斩击命中都调用本方法：反射持续失败时按熔断式 warn 只记首条（2026-10-06）
            warnOnce("boss_triple_whammy", "Failed to apply boss triple whammy (三连) via reflection: {}", e.toString());
        }
    }

    /**
     * Boss 拔刀剑 combo 卡死守卫（每 tick 调用，2026-09-01 改版）。
     * <p>
     * 原 {@code tryTickBladeCombo} 每 tick 手动驱动 {@code resolvCurrentComboState + tickAction}；
     * 反编译确认 slashblade（重锋 2.0.3/2.0.7、Refix 三版一致）的 {@code ItemSlashBlade.inventoryTick}
     * 对持刀 Mob 每 tick 自己驱动同一条链（resolvCurrentComboState 超时迁移 + isInMainhand 时
     * tickAction 执行 TimeLineTickAction）——我们重复驱动 = 刀光翻倍（"刚切刀就有刀光"）。
     * <p>
     * <b>2026-09-12 实测裁决：本条「每 tick 被驱动」成立</b>（首场战斗报告 battle-8db3ba56-240360.json）——
     * tickAction 指纹 {@code slashblade.lastProcessedTick} 达到 29（&gt; 0），对照组持刀玩家同指纹 10（&gt; 0）；
     * 首次非零落在 {@code extinction_day_mod:spatial_slash} 的 combo 段且 selfDriven=false（非我方下发那一拍）
     * ⇒ 时间线由 slashblade 侧驱动。两份「{@code ComboState.tickAction} 唯一调用点是
     * {@code ItemSlashBlade.lambda$inventoryTick$12}、而 {@code ItemStack.inventoryTick} 只被玩家
     * {@code Inventory} 调用 ⇒ Mob 上永不执行」的静态分析结论**被实测推翻**（真正的驱动者尚未定位）。
     * 复现/复看手段见 {@link #probeCombo}：战斗报告 {@code tickActionProbe} 给出实测结论。
     * 故 tickAction 驱动交给 slashblade，这里只保留 combo 卡死守卫：
     * combo 距上次回 NONE/standby 超阈值（400 tick = 20s）视为卡死（重锋版 combo 注册内容重写，
     * 对 Mob 可能卡活跃段回不到 NONE → tickAction 每 tick 刷刀光），强制 updateComboSeq(none)
     * 重置回 standby。Refix 版（历史 jar 验证正常）不做检测。
     */
    public static void tryTickBladeComboStuckGuard(LivingEntity caster) {
        if (!ensureReflectionReady()) return;
        try {
            ItemStack blade = caster.getMainHandItem();
            if (blade.isEmpty() || !isSlashBladeItem(blade.getItem())) return;
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
            if (stateOpt instanceof Optional<?> opt && opt.isPresent()) {
                Object state = opt.get();
                Object loc = resolvCurrentComboStateMethod.invoke(state, caster);
                if (!(loc instanceof ResourceLocation r)) {
                    return;
                }
                ResourceLocation rl = r;
                // 重锋版适配（2026-09-01）：combo 卡死检测。「距上次回 NONE/standby 超阈值」判定：
                // 正常 combo（普攻连击 A1→…→A5、SA 时间线）总会回到 standby 刷新计时；
                // getNext 环 / 永久停留不回 NONE 则超阈值被强制 updateComboSeq(none) 重置。
                // 阈值 400 tick 覆盖合法长 SA（TimeoutNext 未超时 getNext 返回自己，见研究文档 §9）。
                if (!isRefixRuntime()) {
                    ComboStuckState st = COMBO_STUCK_TRACKERS.computeIfAbsent(caster, k -> new ComboStuckState());
                    if (SLASH_ARTS_NONE_ID.equals(rl) || SLASH_BLADE_STANDBY_ID.equals(rl)) {
                        st.lastStandbyTick = caster.tickCount;
                    } else {
                        if (st.lastStandbyTick < 0) {
                            st.lastStandbyTick = caster.tickCount;
                        } else if (caster.tickCount - st.lastStandbyTick > COMBO_STUCK_RESET_TICKS) {
                            LOG.warn("Slash blade combo not returning to NONE for {} ticks (boss={}, combo={}) — forcing reset",
                                caster.tickCount - st.lastStandbyTick, caster.getName().getString(), rl);
                            updateComboSeqMethod.invoke(state, caster, SLASH_ARTS_NONE_ID);
                            // 2026-09-12（tickAction 探针）：归因标记（旁路，只影响报告里 selfDriven 一列）
                            markSelfComboWrite(caster);
                            st.lastStandbyTick = caster.tickCount;
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 战斗中每 goal tick 调用：反射持续失败时按熔断式 warn 只记首条（2026-10-06）
            warnOnce("combo_stuck_check", "Failed to check slash blade combo stuck via reflection: {}", e.toString());
        }
    }

    // ── 2026-09-12（tickAction 运行时探针）：只读 API，零行为变更 ──

    /**
     * combo 探针只读快照。
     *
     * @param comboSeq        Boss 主手刀当前的 combo id（读不到为 {@code null}）
     * @param elapsed         {@code ComboState.getElapsed(entity)}（读不到为 {@code -1}）
     * @param lastProcessedTick tickAction 指纹（{@code persistentData} 的 lastProcessedTick，读不到为 {@code -1}）
     * @param timelineFrames  当前 combo 的 tickAction 时间线帧数：{@code >=0} 帧数（0 = 空时间线）、
     *                        {@code -1} **确实找不到时间线**（已递归拆过 andThen 组合体的捕获字段）、
     *                        {@code -2} 读不到
     * @param unavailableReason 非 null = 探针本身不可用（而非「结论如此」）
     */
    public record ComboProbeSnapshot(String comboSeq, long elapsed, long lastProcessedTick,
                                     int timelineFrames, ComboActionTypes actionTypes, String unavailableReason) {
        public boolean ok() {
            return this.unavailableReason == null;
        }
    }

    /**
     * 解析 combo 探针所需的只读反射项。
     * <p>
     * <b>必须独立于主反射 try</b>（代码审计 G18 #1 的教训）：探针解析失败只允许降级探针，
     * 绝不能把 {@code cachedAvailable} 置 false 而连带整段拔刀剑集成失效。故本方法整个包在
     * 自己的 try 里，且每一项失败都单独记录、不向上抛。
     */
    private static void resolveComboProbeReflection() {
        if (comboProbeResolved != null) {
            return;
        }
        synchronized (IntegrationContract.class) {
            if (comboProbeResolved != null) {
                return;
            }
            StringBuilder problems = new StringBuilder();
            String keyName = COMBO_LAST_PROCESSED_TICK_KEY_LITERAL;
            boolean keyFromReflection = false;
            // ① 指纹键名：反射读常量（javap 确证其值就是字面量），失败退字面量 —— 主判据永不失效。
            try {
                Object key = Class.forName(COMBO_STATE_CLASS)
                    .getField(COMBO_STATE_LAST_PROCESSED_TICK_KEY_FIELD).get(null);
                if (key instanceof String s && !s.isEmpty()) {
                    keyName = s;
                    keyFromReflection = true;
                }
            } catch (Throwable t) {
                problems.append("ComboState.LAST_PROCESSED_TICK_KEY 反射失败（改用字面量 ").append(keyName)
                    .append("）：").append(t).append("；");
            }
            comboLastProcessedTickKeyInstant = keyName;
            comboFingerprintKeyFromReflection = keyFromReflection;
            // ② comboSeq / elapsed：读不到只让报告少两列，不影响指纹主判据。
            try {
                getComboSeqMethod = Class.forName(ISLASH_BLADE_STATE_CLASS)
                    .getMethod(ISLASH_BLADE_STATE_GET_COMBO_SEQ_METHOD);
            } catch (Throwable t) {
                getComboSeqMethod = null;
                problems.append("ISlashBladeState.getComboSeq 反射失败：").append(t).append("；");
            }
            try {
                comboGetElapsedMethod = Class.forName(COMBO_STATE_CLASS)
                    .getMethod(COMBO_STATE_GET_ELAPSED_METHOD, LivingEntity.class);
            } catch (Throwable t) {
                comboGetElapsedMethod = null;
                problems.append("ComboState.getElapsed 反射失败：").append(t).append("；");
            }
            // ③ 时间线帧数（增强信号）：Registry 查询 + 两个 private final 字段的读取权限。
            try {
                comboStateRegistry = Class.forName(COMBO_STATE_REGISTRY_CLASS)
                    .getField(COMBO_STATE_REGISTRY_FIELD).get(null);
                try {
                    comboStateRegistryGetMethod = comboStateRegistry.getClass()
                        .getMethod(SLASH_ARTS_REGISTRY_GET_METHOD, ResourceLocation.class);
                } catch (Throwable inner) {
                    // 运行时实现类的可见性因映射而异，回退按 Registry 接口反射（与 SlashArts 同源的坑）。
                    comboStateRegistryGetMethod = Class.forName("net.minecraft.core.Registry")
                        .getMethod(SLASH_ARTS_REGISTRY_GET_METHOD, ResourceLocation.class);
                }
            } catch (Throwable t) {
                comboStateRegistryGetMethod = null;
                problems.append("ComboStateRegistry.REGISTRY 反射失败（帧数信号禁用）：").append(t).append("；");
            }
            try {
                Field tickField = Class.forName(COMBO_STATE_CLASS).getDeclaredField(COMBO_STATE_TICK_ACTION_FIELD);
                tickField.setAccessible(true);
                comboStateTickActionField = tickField;
                // clickAction 也必须读：时间线对象被设成 clickAction 时，指纹同样会被写（见 comboActionTypes 注释）。
                Field clickField = Class.forName(COMBO_STATE_CLASS).getDeclaredField(COMBO_STATE_CLICK_ACTION_FIELD);
                clickField.setAccessible(true);
                comboStateClickActionField = clickField;
                Class<?> tla = Class.forName(COMBO_STATE_TIME_LINE_TICK_ACTION_CLASS);
                Field lineField = tla.getDeclaredField(COMBO_STATE_TIME_LINE_FIELD);
                lineField.setAccessible(true);
                comboStateTimeLineField = lineField;
                timeLineTickActionClass = tla;
            } catch (Throwable t) {
                comboStateTickActionField = null;
                comboStateClickActionField = null;
                comboStateTimeLineField = null;
                timeLineTickActionClass = null;
                problems.append("ComboState.tickAction / clickAction / timeLine 字段读取失败（类型信号禁用，指纹主判据不受影响）：")
                    .append(t).append("；");
            }
            // ④ 蓄力锚点（ChargeActionEvent）：解析失败只禁用该锚点，不影响探针主判据。
            try {
                chargeEventClass = Class.forName(SLASH_BLADE_EVENT_CHARGE_CLASS);
                try {
                    chargeEventGetUserMethod = chargeEventClass
                        .getMethod(SLASH_BLADE_EVENT_CHARGE_GET_ENTITY_METHOD);
                } catch (NoSuchMethodException incompatible) {
                    // 版本分支兜底：若某分支沿用 DoSlashEvent 的 getUser 命名，此处仍可命中。
                    chargeEventGetUserMethod = chargeEventClass.getMethod(DO_SLASH_EVENT_GET_USER_METHOD);
                }
            } catch (Throwable t) {
                chargeEventClass = null;
                chargeEventGetUserMethod = null;
                problems.append("ChargeActionEvent 反射失败（玩家蓄力锚点禁用）：").append(t).append("；");
            }
            comboProbeUnavailableReason = problems.length() == 0 ? null : problems.toString();
            comboProbeResolved = Boolean.TRUE;
            if (comboProbeUnavailableReason != null) {
                LOG.warn("[SilentSun] combo/tickAction 探针部分反射项不可用（探针降级，不影响战斗）：{}",
                    comboProbeUnavailableReason);
            }
        }
    }

    /** 指纹键名：反射取 {@code ComboState.LAST_PROCESSED_TICK_KEY} 优先，失败回退字面量，永不返回 null。 */
    public static String comboFingerprintKey() {
        resolveComboProbeReflection();
        return comboLastProcessedTickKeyInstant;
    }

    /** 指纹键名是否来自反射（false = 用了字面量兜底，报告里标注以便察觉 slashblade 版本差异）。 */
    public static boolean isComboFingerprintKeyFromReflection() {
        resolveComboProbeReflection();
        return comboFingerprintKeyFromReflection;
    }

    /**
     * 只读读一次 tickAction 指纹（实体 {@code persistentData} 的 lastProcessedTick 键）。{@code <0} = 读不到。
     * <p>
     * 语义：该键由 {@code ComboState$TimeLineTickAction.accept} 在**推进一帧时间线后**写入
     * （{@code putInt(key, elapsed + 1)}）。{@code CompoundTag.getInt} 对缺失键返回 0 且**不写入**，
     * 故「读到 0」严格等于「时间线从未推进过」；该键只增不减（写入后持久保留）。
     */
    public static long readComboFingerprint(LivingEntity entity) {
        try {
            if (entity == null) {
                return -1L;
            }
            String key = comboFingerprintKey();
            CompoundTag data = entity.getPersistentData();
            return data == null || key == null ? -1L : data.getInt(key);
        } catch (Throwable t) {
            return -1L;
        }
    }

    /**
     * 对照指纹：仅当该实体**主手持拔刀剑**时返回其 tickAction 指纹，否则 {@code -1}（不适用）。
     * <p>
     * 为什么需要对照：持刀玩家的 {@code inventoryTick} 有驱动者（{@code Inventory} 每 tick 调），
     * 其指纹应 &gt; 0。若对照也恒为 0，说明「指纹机制在当前 jar 上读不出来」，此时 Boss 的 0 不能作为
     * 「tickAction 未被调用」的证据 —— 报告用 {@code control.fingerprintMechanismVerified} 标注这一点。
     */
    public static long readControlComboFingerprint(LivingEntity entity) {
        try {
            if (entity == null || entity.level() == null || entity.level().isClientSide()) {
                return -1L;
            }
            ItemStack blade = entity.getMainHandItem();
            if (blade.isEmpty() || !ensureReflectionReady() || bladeStateAccessOfMethod == null) {
                return -1L;
            }
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
            if (!(stateOpt instanceof Optional<?> opt) || opt.isEmpty()) {
                return -1L;
            }
            return readComboFingerprint(entity);
        } catch (Throwable t) {
            return -1L;
        }
    }

    /**
     * combo 探针主入口：一次只读采样，任何失败都降级为「字段读不到」而绝不抛异常。
     * 全程只调用只读 getter / 只读字段读取，不写任何 slashblade 状态。
     */
    public static ComboProbeSnapshot probeCombo(LivingEntity entity) {
        String unavailable = null;
        String comboSeq = null;
        long elapsed = -1L;
        int frames = -2;
        ComboActionTypes actionTypes = ComboActionTypes.UNKNOWN;
        try {
            if (entity == null) {
                return new ComboProbeSnapshot(null, -1L, -1L, -2, ComboActionTypes.UNKNOWN, "实体为 null");
            }
            resolveComboProbeReflection();
            if (!isSlashBladeIntegrationAvailable()) {
                unavailable = "slashblade 集成不可用（前置缺失），comboSeq 无法读取";
            } else if (ensureReflectionReady()) {
                ItemStack blade = entity.getMainHandItem();
                if (!blade.isEmpty() && bladeStateAccessOfMethod != null) {
                    Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
                    if (stateOpt instanceof Optional<?> opt && opt.isPresent() && getComboSeqMethod != null) {
                        Object loc = getComboSeqMethod.invoke(opt.get());
                        comboSeq = loc == null ? null : loc.toString();
                    }
                }
                if (comboGetElapsedMethod != null) {
                    Object value = comboGetElapsedMethod.invoke(null, entity);
                    if (value instanceof Number n) {
                        elapsed = n.longValue();
                    }
                }
                actionTypes = comboActionTypes(comboSeq);
                frames = actionTypes.tickActionFrames();
            } else {
                unavailable = "slashblade 反射缓存未就绪，comboSeq 无法读取";
            }
        } catch (Throwable t) {
            unavailable = "探针采样异常：" + t;
        }
        long fingerprint = readComboFingerprint(entity);
        if (fingerprint < 0L && unavailable == null) {
            unavailable = "tickAction 指纹读取失败（persistentData 不可用）";
        }
        return new ComboProbeSnapshot(comboSeq, elapsed, fingerprint, frames, actionTypes, unavailable);
    }

    /**
     * 当前 combo 的 tickAction 时间线帧数（只读）。
     * <p>
     * <b>2026-09-12 首场实测 → 同日修复（「时间轴查找」）</b>：该场 {@code maxTimelineFrames} 全程 -1，
     * 而指纹达到 29 —— 原因是 {@code ComboState.tickAction} 字段里放的是
     * {@code TickAction.andThen(...)} 生成的**合成 lambda**（javap 确证 {@code andThen} 是 interface 的
     * default 方法、编译为捕获式 lambda，且该字段声明类型是 {@code Consumer<LivingEntity>}，
     * 可放任意组合体），此时 {@code isInstance(TimeLineTickAction)} 恒 false。
     * <p>
     * <b>现已按捕获字段递归拆解组合体</b>（{@link #findTimeLineAction}）⇒ 帧数可正常读出。解读方式：
     * <ul>
     *   <li>{@code >=0} ⇒ 找到时间线并读到帧数（0 = 空时间线，即 {@code ComboState.EMPTY_TICK_ACTION}）；</li>
     *   <li>{@code -1} ⇒ **确实找不到时间线**（组合体拆不开 / 第三方自定义实现 / 该 combo 真无时间线）；</li>
     *   <li>{@code -2} ⇒ 读不到（反射不可用 / comboSeq 不在 combo_state 注册表里）。</li>
     * </ul>
     * 注：{@code tickActionIsTimeline=false} 且本值 {@code >=0} 是**合法组合** —— 表示时间线被
     * {@code andThen} 组合在 tickAction 里，而非字段值本身。
     *
     * @return {@code >=0} 帧数；{@code -1} 找不到时间线；{@code -2} 反射不可用 / comboSeq 未注册
     */
    /**
     * 当前 combo 的 {@code tickAction} / {@code clickAction} 字段的**实际运行时类型**（只读）。
     * <p>
     * <b>为什么必须记实际类型（2026-09-12 修正一处推理漏洞）</b>：{@code lastProcessedTick} 指纹只能证明
     * 「某个 {@code TimeLineTickAction.accept} 被执行过」，它**不能**单独证明是 tickAction 被执行 ——
     * 因为 {@code TimeLineTickAction} 只是一个 {@code Consumer} 对象，若某个 combo 把它设成
     * **clickAction**（走我方 {@code updateComboSeq → clickAction} 这条链），同样会写这个指纹。
     * 只有把两个字段的运行时类型一起记下来，才能区分：
     * <ul>
     *   <li>指纹在涨 且 {@code tickAction} 是时间线（或含时间线的组合体）⇒ 时间线被**外部驱动**；</li>
     *   <li>指纹在涨 而 {@code clickAction} 才是时间线类型 ⇒ 是**我方 updateComboSeq 触发的 clickAction** 在跑时间线。</li>
     * </ul>
     * 另外「组合体」情况（slashblade 的 {@code TickAction.andThen}、第三方如 True_POWER 的
     * {@code wrapOperationUpperSlashTickAction} 包装）会表现为类名是合成的 lambda 类，而非
     * {@code ComboState$TimeLineTickAction} —— 这正是过去 {@code maxTimelineFrames=-1} 的成因；
     * <b>2026-09-12 已修</b>：{@link #findTimeLineAction} 会递归拆捕获字段把时间线找出来
     * （见 {@link #timelineFramesOf}），故本类型的 {@code *IsTimeline=false} 不再等于「没有时间线」，
     * 要配合对应的 {@code *Frames >= 0} 一起读。
     */
    public record ComboActionTypes(String tickActionClass, String clickActionClass,
                                   boolean tickActionIsTimeline, boolean clickActionIsTimeline,
                                   int tickActionFrames, int clickActionFrames) {
        public static final ComboActionTypes UNKNOWN =
            new ComboActionTypes(null, null, false, false, -2, -2);
    }

    /**
     * 读当前 combo 的 tickAction / clickAction 字段运行时类型（只读；读不到返回 {@link ComboActionTypes#UNKNOWN}）。
     * 结果按 comboSeq 缓存（注册表内容启动后不变）。
     * <p>
     * 类名用**完整名**（{@code getName()}）而不是简名：lambda 的完整名形如
     * {@code net.mrqx.truepower.util.TruePowerComboHelper$$Lambda/0x...}，能**直接指出该动作属于哪个模组** ——
     * 这正是「哪个模组把什么动作塞进了这个 combo」的唯一线索（详见 {@link ComboActionTypes} 的说明）。
     */
    private static ComboActionTypes comboActionTypes(String comboSeq) {
        if (comboSeq == null || comboStateRegistry == null || comboStateRegistryGetMethod == null
            || comboStateTickActionField == null) {
            return ComboActionTypes.UNKNOWN;
        }
        ComboActionTypes cached = COMBO_ACTION_TYPES.get(comboSeq);
        if (cached != null) {
            return cached;
        }
        ComboActionTypes types = ComboActionTypes.UNKNOWN;
        try {
            Object state = comboStateRegistryGetMethod.invoke(comboStateRegistry, ResourceLocation.parse(comboSeq));
            if (state != null) {
                Object tickAction = comboStateTickActionField.get(state);
                Object clickAction = comboStateClickActionField == null ? null : comboStateClickActionField.get(state);
                types = new ComboActionTypes(
                    tickAction == null ? null : tickAction.getClass().getName(),
                    clickAction == null ? null : clickAction.getClass().getName(),
                    isTimelineAction(tickAction), isTimelineAction(clickAction),
                    timelineFramesOf(tickAction), timelineFramesOf(clickAction));
            }
        } catch (Throwable t) {
            types = ComboActionTypes.UNKNOWN;
        }
        COMBO_ACTION_TYPES.put(comboSeq, types);
        return types;
    }

    /** 该动作对象是否是 TimeLineTickAction 实例（时间线）。 */
    private static boolean isTimelineAction(Object action) {
        return action != null && timeLineTickActionClass != null && timeLineTickActionClass.isInstance(action);
    }

    /**
     * 从 {@code tickAction} / {@code clickAction} 字段值里**递归查找** TimeLineTickAction
     * （只读；找不到返回 {@code null}）。
     * <p>
     * <b>2026-09-12（实战「时间轴查找」）</b>：该字段实测往往**不是**直接的 {@code TimeLineTickAction}，
     * 而是 {@code ComboState$TickAction.andThen(...)} 生成的**合成 lambda**。javap 确证：
     * <ul>
     *   <li>{@code ComboState$TickAction} 是 interface，{@code andThen} 是它的 default 方法，
     *       编译为捕获式 lambda（{@code ComboState$TickAction$$Lambda}）；</li>
     *   <li>{@code ComboState.tickAction} 的**声明类型是 {@code Consumer<LivingEntity>}**，
     *       因此可以放任意组合体，不受 {@code TickAction} 名义类型约束。</li>
     * </ul>
     * ⇒ 直接 {@code isInstance} 判断恒 false、帧数恒 {@code -1}，这就是过去 {@code maxTimelineFrames}
     * 全程 -1 的成因（也让实体产出无法归因到具体时间轴）。组合体的**捕获字段**里存着原 TickAction，
     * 故按字段递归拆解即可定位真正的时间轴对象。
     * <p>
     * 深度上限 4：{@code andThen} 串联「时间线→附加动作」通常只 1 层，上限用于挡住第三方深层包装
     * 导致的无界递归。全程只读并吞异常 —— 任一环读不到只返回 {@code null}，不影响战斗。
     */
    private static Object findTimeLineAction(Object action, int depth) {
        if (action == null || depth > 4) {
            return null;
        }
        if (isTimelineAction(action)) {
            return action;
        }
        try {
            for (Field f : action.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!f.trySetAccessible()) {
                    continue;
                }
                Object captured = f.get(action);
                if (captured == null || captured == action) {
                    continue;
                }
                Object found = findTimeLineAction(captured, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } catch (Throwable ignored) {
            // 组合体不可拆（模块访问限制 / 非捕获式实现）⇒ 视为「找不到时间轴」，探针降级不影响战斗
        }
        return null;
    }

    /**
     * 动作对象的 timeLine 帧数。
     * <p>
     * {@code -1} = **找不到时间线**（含组合体拆不开、字段读不到）；{@code >=0} = 帧数（0 = 空时间线）。
     * 与 {@code tickActionIsTimeline} 配合解读：{@code isTimeline=false} 但本值 {@code >=0} ⇒
     * 说明时间线被 andThen **组合**在 tickAction 里（时间线确实存在，只是不直接是字段值）。
     */
    private static int timelineFramesOf(Object action) {
        Object timeline = findTimeLineAction(action, 0);
        if (timeline == null) {
            return -1;
        }
        try {
            Object line = comboStateTimeLineField == null ? null : comboStateTimeLineField.get(timeline);
            return line instanceof java.util.Map<?, ?> map ? map.size() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static int timelineFrameCount(String comboSeq) {
        return comboActionTypes(comboSeq).tickActionFrames();
    }

    /**
     * 标记「silent_sun 刚主动写了 comboSeq」（探针归因用）：
     * 由 {@link #tryProgressCombo} / {@link #tryInvokeRandomSA} / 卡死守卫在 updateComboSeq 成功后调用。
     * 仅写两个 volatile 字段，失败只影响报告里 selfDriven 一列，不参与任何战斗判定。
     */
    static void markSelfComboWrite(LivingEntity entity) {
        try {
            if (entity != null) {
                selfComboWriteEntityId = entity.getId();
                selfComboWriteTick = entity.tickCount;
                selfComboWriteCount++;
            }
        } catch (Throwable ignored) {
            // 归因标记失败无害
        }
    }

    /** 我方主动写 comboSeq 的单调计数：调用方用它做「窗口内是否发生过我方写入」的差值判断。 */
    public static long selfComboWriteCount() {
        return selfComboWriteCount;
    }

    /** 最近一次「我方主动写 comboSeq」是否落在窗口内（同实体 + tickCount 差 ≤ windowTicks）。 */
    public static boolean isSelfComboWriteRecent(LivingEntity entity, int windowTicks) {
        try {
            if (entity == null || entity.getId() != selfComboWriteEntityId) {
                return false;
            }
            int delta = entity.tickCount - selfComboWriteTick;
            return delta >= 0 && delta <= windowTicks;
        } catch (Throwable t) {
            return false;
        }
    }

    // ── 2026-09-12（产出观测）：回答「到底是哪一类实体产出来了」──
    //
    // 为什么需要它：作者的实机观察是「有一部分 SA 的对应实体产出成功」——若成立，则必有静态分析
    // 没走通的产出链（时间线之外的出口 / 第三方模组自驱动 / 别的事件监听器）。要定死这件事，必须
    // 观测**实体**而不只是 comboSeq。既有诊断 CommonEvents.diagnoseSlashBladeEntityFlood 有三个
    // 不适用点：① 只在总数超阈值时打日志（少量产出全盲）；② 只统计 mods.flammpfeil.slashblade 包
    // 的类（第三方模组自己的实体类型不计）；③ 快照式、不归因到 Boss、无法与 combo/指纹时间线对齐。
    // 故这里另建一套：**增量式 + 全模组命名空间 + 归属到 Boss 周围**，只在战斗报告开启时运转。

    /** 产出观测半径（格）：刀光/剑气/幻影剑都生成在 Boss 身前，48 格覆盖飞行中的剑气。 */
    static final double PRODUCTION_SCAN_RADIUS = 48.0;

    /**
     * 一次扫描到的模组实体只读快照。
     *
     * @param type    实体类型注册表 id（如 {@code slashblade:drive}、{@code foxextra:xxx}）——
     *                用**注册表 id** 而不是类名，第三方模组自己的实体类型同样能标出
     * @param category 粗分类：刀光 / 剑气 / 次元斩 / 剑雨 / 其它（口径与
     *                {@code CommonEvents.diagnoseSlashBladeEntityFlood} 一致，便于两处对照）
     * @param owner   归属者名字（读不到为 null）
     */
    public record ProbeEntityInfo(int id, String type, String category, String owner) {
    }

    /**
     * 扫描中心实体周围半径内的**非 vanilla 实体**（模组实体），返回 id → 快照。只读，不改任何状态。
     * <p>
     * 跳过 {@code minecraft:} 命名空间的实体（玩家 / 箭 / 掉落物等），只留模组实体 ——
     * 这样增量 diff 出来的就是「模组产出的实体」，噪音极低。
     * <p>
     * <b>为什么主键用注册表 id 而不是类名（2026-09-12 作者口径："跟着变就行"）</b>：
     * 第三方会**替换/新增**拔刀剑的斩击实体（例如 recasting2 这一类整合包常客会整体接管斩击表现），
     * 类名与包路径都可能与 slashblade 原版不同。注册表 id 如实反映实际生成的对象，因此无论谁替换、
     * 换成什么类型，报告都会**自动跟随**，不需要为任何具体模组写特判。粗分类
     * （{@link #classifyProbeEntityClass}）只是给人看的归并桶，落到「其它」不代表漏观测 ——
     * 类型明细在 {@code type} 字段里始终是精确的。
     */
    public static java.util.Map<Integer, ProbeEntityInfo> scanModEntities(LivingEntity center, double radius) {
        java.util.Map<Integer, ProbeEntityInfo> out = new java.util.HashMap<>();
        try {
            if (center == null || center.level() == null || center.level().isClientSide()) {
                return out;
            }
            AABB box = center.getBoundingBox().inflate(radius);
            for (Entity e : center.level().getEntitiesOfClass(Entity.class, box, candidate -> candidate != center)) {
                if (e == null) {
                    continue;
                }
                ResourceLocation key;
                try {
                    key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
                } catch (Throwable ignored) {
                    continue;
                }
                if (key == null || "minecraft".equals(key.getNamespace())) {
                    continue;
                }
                out.put(e.getId(), new ProbeEntityInfo(e.getId(), key.toString(),
                    classifyProbeEntityClass(e.getClass().getSimpleName()), probeOwnerName(e)));
            }
        } catch (Throwable t) {
            // 按扫描间隔周期调用：扫描持续失败时按熔断式 warn 只记首条（2026-10-06）
            warnOnce("production_scan", "产出观测扫描失败（不影响战斗）：{}", t.toString());
        }
        return out;
    }

    /**
     * 粗分类口径，与 {@code CommonEvents.diagnoseSlashBladeEntityFlood} 完全一致
     * （用类简名而非直接引用类型：SlashBlade 是反射软依赖，编译期不可引用）。
     * <p>
     * <b>刻意不做第三方特判（2026-09-12 作者口径："跟着变就行"）</b>：recasting2 这类模组会**替换**
     * 拔刀剑的斩击实体，替换后类名不再匹配下面的前缀，本方法会把它归入「其它」——这是**可接受**的：
     * 报告的实体明细以注册表 id（{@link ProbeEntityInfo#type()}）为准，那是精确值；本方法只提供
     * 「刀光/剑气/次元斩/剑雨」四个人类可读的归并桶，不承担完整性。**不要**为了让 refactor 后的类名
     * 落进原桶而给某个具体模组加前缀匹配 —— 那会把观测绑死在某一版实现上。
     */
    public static String classifyProbeEntityClass(String simpleName) {
        if (simpleName == null) {
            return "其它";
        }
        if (simpleName.startsWith("EntitySlashEffect")) {
            return "刀光";
        }
        if (simpleName.startsWith("EntityDrive")) {
            return "剑气";
        }
        if (simpleName.startsWith("EntityJudgementCut")) {
            return "次元斩";
        }
        if (simpleName.contains("Sword")) {
            return "剑雨";
        }
        return "其它";
    }

    /** 实体归属者名字（读不到为 null）：{@code Projectile.getOwner()} / {@code OwnableEntity.getOwner()}。 */
    private static String probeOwnerName(Entity e) {
        try {
            Entity owner = null;
            if (e instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                owner = projectile.getOwner();
            } else if (e instanceof net.minecraft.world.entity.OwnableEntity ownable) {
                owner = ownable.getOwner();
            }
            return owner == null ? null : owner.getName().getString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * SA 施放产出留痕：对比施放前后的模组实体集合，把**本次施放新增**的实体报给战斗报告。
     * <p>
     * 这是直接回答「这个 SA 到底产出了什么」的最硬证据：新增实体全部出现在**同一次调用内**，
     * 因此它们必然来自 {@code doArts → updateComboSeq → clickAction / releaseAction} 这条链，
     * 不可能是时间线逐帧产出（时间线产物出现在后续 tick）。反之 {@code added} 为空 = 这次 SA 空放。
     *
     * @param before 施放前的实体快照（调用方只在报告开启时采集；为 null 表示不记录）
     */
    private static void reportSaProduction(RediosEntity boss, String saId, LivingEntity caster,
                                           java.util.Map<Integer, ProbeEntityInfo> before) {
        if (boss == null || before == null) {
            return;
        }
        try {
            java.util.Map<Integer, ProbeEntityInfo> after = scanModEntities(caster, PRODUCTION_SCAN_RADIUS);
            List<String> added = new ArrayList<>();
            for (java.util.Map.Entry<Integer, ProbeEntityInfo> entry : after.entrySet()) {
                if (before.containsKey(entry.getKey())) {
                    continue;
                }
                ProbeEntityInfo info = entry.getValue();
                added.add(info.owner() == null ? info.type() : info.type() + "@" + info.owner());
            }
            boss.flowSaProduction(saId, added);
        } catch (Throwable t) {
            LOG.warn("SA 产出留痕失败（不影响战斗）：{}", t.toString());
        }
    }

    /**
     * 强制关闭 Boss 剑气（EntityDrive）的暴击，规避负伤害暴击崩溃。
     * <p>
     * SlashBlade {@code EntityDrive.onHitEntity} 在暴击分支执行
     * {@code random.nextInt(Mth.ceil(damageValue) / 2 + 2)}；当 Boss 攻击力被减为负
     * （或第三方 SA/combo 把剑气 base 伤害设为负）时该 bound ≤ 0，抛
     * {@code IllegalArgumentException: Bound must be positive} 直接崩服。
     * 这里每 tick 扫描 owner == Boss 的剑气并强制 {@code setIsCritical(false)}：
     * 非暴击会跳过该分支，负伤害随后被 vanilla hurt 的 amount&gt;0 守卫安全忽略。
     */
    public static void sanitizeBossBladeDrives(RediosEntity boss) {
        if (!ensureReflectionReady()) return;
        if (entityDriveClass == null || entityAbstractSummonedSwordClass == null) return;
        if (summonedSwordGetIsCriticalMethod == null || summonedSwordSetIsCriticalMethod == null) return;
        Level level = boss.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel)) return;

        // 2026-08-15：崩溃剑气不一定 owner==Boss。参战玩家被“无色虚弱诅咒”减攻为负后，
        // 玩家自己拔刀剑生成的暴击剑气（owner=玩家）命中同样会触发
        // EntityDrive.onHitEntity 暴击分支 random.nextInt(ceil(负伤害)/2+2) 抛
        // "Bound must be positive" 直接崩服。这里改为扫描 Boss 战斗半径内全部剑气，
        // 凡 owner 为 LivingEntity 且攻击力 ≤ 0（或 owner 为空）者强制关闭暴击，
        // 从源头杜绝负伤害暴击崩溃，而不再局限于 owner==Boss。
        //
        // 2026-09-10（实测补强，**高危漏项**）：扫描判据由 EntityDrive **上移到父类
        // EntityAbstractSummonedSword** —— 该父类的 onHitEntity **有一模一样的崩服结构**
        // （字节码：Mth.ceil → getIsCritical → iconst_2/idiv/iconst_2 → nextInt，位于
        // EntityAbstractSummonedSword.onHitEntity 偏移 29/94/108-112），即**幻影剑一族**同样会崩。
        // EntityDrive extends EntityAbstractSummonedSword，故判据上移后**同时覆盖两者**，不会漏剑气。
        // （注：EntityJudgementCut 是独立类，其 nextInt 在**构造器**里做随机种子、不是伤害公式，无此崩服路径。）
        List<Projectile> drives = level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(64.0),
            e -> entityAbstractSummonedSwordClass.isInstance(e) && e.isAlive());
        if (drives.isEmpty()) return;

        for (Projectile drive : drives) {
            try {
                if (isDangerousBladeDrive(drive)) {
                    if (Boolean.TRUE.equals(summonedSwordGetIsCriticalMethod.invoke(drive))) {
                        summonedSwordSetIsCriticalMethod.invoke(drive, false);
                    }
                }
            } catch (Exception e) {
                // 每战斗 tick × 64 格内每把剑：反射持续失败时按熔断式 warn 只记首条（2026-10-06）
                warnOnce("drive_critical_sanitize", "Failed to sanitize boss drive critical flag: {}", e.toString());
            }
        }
    }

    /**
     * 给 Boss 近身无 owner 的拔刀剑投射物（幻影剑 / 刀光 / 次元斩）补上 shooter。
     * <p>
     * SlashBlade 的 SA 时间轴（TimeLineTickAction）在 Boss（Mob）上生成的投射物可能未设置
     * owner；玩家手持拔刀剑施放含 ArrowReflector 的 SA 时，其
     * {@code TargetSelector.getReflectableEntitiesWithinAABB} 会扫描附近 IShootable 并执行
     * {@code getShooter().equals(attacker)}，getShooter()==null 直接 NPE 崩端。
     * 三个 IShootable 实现（EntityAbstractSummonedSword / EntitySlashEffect / EntityJudgementCut）
     * 的 getShooter()/setShooter() 均委托 getOwner()/setOwner()，因此这里统一按 IShootable 接口
     * 扫描无 owner 者并补 shooter=Boss，保证其永远非空。
     * <p>
     * 半径收敛到 16 格（2026-09-01 收紧，子代理审查发现）：Boss 生成的孤儿投射物都在
     * Boss 本体位置产生，且 BladeAttackGoal.tick 同 tick 收尾即调用本方法，16 格足以在
     * 「生成→被玩家扫描」的空窗内补齐 owner。更大的半径会把远距离、可能属于玩家/第三方的
     * 无 owner 投射物（如玩家 SA 时间线暂缺 owner 的刀光/次元斩）一并劫持到 Boss，
     * 造成错误归属（用户所指「幻影剑做给玩家」的怀疑点之一）。
     */
    public static void sanitizeBossSummonedSwordShooters(RediosEntity boss) {
        if (!ensureReflectionReady()) return;
        if (iShootableClass == null || iShootableSetShooterMethod == null) return;
        Level level = boss.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel)) return;

        List<Entity> swords = level.getEntitiesOfClass(Entity.class, boss.getBoundingBox().inflate(16.0),
            e -> iShootableClass.isInstance(e) && e.isAlive() && hasNullShooter(e));
        if (swords.isEmpty()) return;

        for (Entity sword : swords) {
            try {
                iShootableSetShooterMethod.invoke(sword, boss);
            } catch (Exception e) {
                // 每战斗 tick × 每个孤儿投射物：反射持续失败时按熔断式 warn 只记首条（2026-10-06）
                warnOnce("shooter_assign", "Failed to assign boss shooter to slashblade projectile: {}", e.toString());
            }
        }
    }

    /** IShootable.getShooter() 反射判空：返回 true 表示无 shooter（孤儿投射物，需补 Boss）。 */
    /**
     * 2026-09-14（体检 P0-3）：反射判定一旦抛异常即置位 —— 此后直接降级返回，不再重复反射、
     * 不再重复记日志。理由见 {@link #hasNullShooter} 的 catch 注释。
     * <p>{@code volatile}：本方法可能在多个维度/多个线程路径上被读到，置位后要求立即可见。
     */
    private static volatile boolean shooterProbeBroken = false;

    private static boolean hasNullShooter(Entity e) {
        if (iShootableGetShooterMethod == null || shooterProbeBroken) {
            return false;
        }
        try {
            return iShootableGetShooterMethod.invoke(e) == null;
        } catch (Exception ex) {
            // 2026-09-14（体检 P0-3 修复，**保留 G19 #5「不得静默」的意图**）：
            // G19 #5 当初选 `debug` 而非静默，是为了「反射失效不能无痕」；但本方法被
            // `globalSanitizeBladeDrives` 的「全维度全实体」循环与 `sanitizeBossSummonedSwordShooters`
            // 的 16 格实体谓词**逐实体调用** ⇒ 反射一旦失效就是「实体数 × 每 tick」的日志洪水。
            // 现改为：**首次 `warn`（作者可见，保住可见性）+ 置位禁用探测**（此后直接返回 false，
            // 明确降级、不再抛异常循环、不再刷屏）。即「可见性」与「不刷屏」两者兼得。
            // 上面 `iShootableGetShooterMethod == null` 的早退是「没装拔刀剑」的正常路径
            //（返回值本就是正确语义），故不记日志。
            if (!shooterProbeBroken) {
                shooterProbeBroken = true;
                LOG.warn("hasNullShooter 反射判定失败，已**禁用该探测**（此后一律按「无 null shooter」处理，"
                    + "可能漏清理孤儿投射物，请检查拔刀剑版本是否匹配）：", ex);
            }
            return false;
        }
    }

    /**
     * 拔刀剑崩溃防护总入口：每战斗 tick 调用一次，覆盖刀窗口之外的残留剑气/幻影剑。
     * <ul>
     *   <li>{@link #sanitizeBossBladeDrives(RediosEntity)}：关闭负攻击力 owner（被无色虚弱诅咒
     *       减攻为负的参战玩家等）暴击剑气的暴击，杜绝 EntityDrive.onHitEntity 负伤害暴击
     *       random.nextInt(负) 抛 "Bound must be positive" 崩服。</li>
     *   <li>{@link #sanitizeBossSummonedSwordShooters(RediosEntity)}：给无 owner 的幻影剑/剑气补
     *       shooter，杜绝玩家 ArrowReflector 扫描到 getShooter()==null 的 IShootable 时崩端。</li>
     * </ul>
     * 必须在刀窗口之外也持续运行：剑气/幻影剑有存活期，刀窗口结束后仍可能在玩家附近飞行，
     * 而玩家随时可能挥刀触发 ArrowReflector 扫描。
     */
    public static void sanitizeBossBladeEntities(RediosEntity boss) {
        sanitizeBossBladeDrives(boss);
        sanitizeBossSummonedSwordShooters(boss);
    }

    /**
     * 全局（跨维度、与 Boss 是否在场/战斗态解耦）清扫危险的暴击剑气。
     * <p>
     * 与 {@link #sanitizeBossBladeDrives(RediosEntity)} 不同，这里不依赖 Boss 位置/战斗态：
     * 直接遍历服务端所有维度里存活的 EntityDrive 剑气，凡「暴击」且 owner 危险（负攻击力 /
     * owner 为空 / owner 为 Redios）者一律强制关闭暴击，从源头杜绝
     * {@code EntityDrive.onHitEntity} 负伤害暴击 {@code random.nextInt(负)} 抛
     * "Bound must be positive" 崩服。作为全局 tick 兜底，覆盖 Boss 不在场/非战斗态/跨维度
     * 残留剑气的崩溃窗口。
     */
    public static void globalSanitizeBladeDrives(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        if (!ensureReflectionReady()) return;
        if (entityDriveClass == null || entityAbstractSummonedSwordClass == null) return;
        if (summonedSwordGetIsCriticalMethod == null || summonedSwordSetIsCriticalMethod == null) return;

        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            boolean anyPlayer = !level.players().isEmpty();
            for (Entity entity : level.getEntities().getAll()) {
                // 2026-09-10：判据上移到父类，覆盖幻影剑一族（见 sanitizeBossBladeDrives 的说明）。
                if (entityAbstractSummonedSwordClass.isInstance(entity)) {
                    if (!(entity instanceof Projectile drive) || !drive.isAlive()) continue;
                    sanitizeBladeDriveCritical(drive);
                }
                // 2026-09-11 崩服护栏补漏（审计 G 组）：原先全局兜底**只关暴击、不补 owner**，
                // 于是"远离 Boss 的孤儿 IShootable"（玩家侧 ArrowReflector 会扫描
                // TargetSelector.getReflectableEntitiesWithinAABB）仍可能因 getShooter()==null 崩端。
                // 这里补一道外科手术式兜底：**玩家附近**且存活 ≥20 tick 仍无 shooter 的孤儿直接移除。
                // 玩家自己的拔刀剑投射物都带 owner，孤儿只可能来自 Mob/异常生成 → 移除无副作用；
                // 限定"玩家附近"既避开昂贵的全维度实体查询，也正好覆盖真正会崩的那一段。
                if (anyPlayer && iShootableClass != null && iShootableSetShooterMethod != null
                    && iShootableClass.isInstance(entity)
                    && entity instanceof Projectile projectile && projectile.isAlive()
                    && projectile.tickCount >= 20 && hasNullShooter(projectile)
                    && isNearAnyPlayer(level, projectile)) {
                    projectile.discard();
                }
            }
        }
    }

    /** 目标是否在任一玩家 64 格内（用于孤儿 IShootable 的崩服兜底判定，避免全维度查询）。 */
    private static boolean isNearAnyPlayer(net.minecraft.server.level.ServerLevel level, Entity entity) {
        double limit = 64.0 * 64.0;
        for (net.minecraft.world.entity.player.Player player : level.players()) {
            if (player.distanceToSqr(entity) <= limit) {
                return true;
            }
        }
        return false;
    }

    /**
     * 剑气入世即清扫（供 EntityJoinLevelEvent 调用）：暴击剑气若 owner 危险则立刻关暴击，
     * 无需扫描、零额外开销，杜绝跨维度/脱离战斗后残留剑气的崩溃窗口。
     */
    public static void sanitizeBladeDriveOnJoin(Entity entity) {
        if (entity == null) return;
        if (!ensureReflectionReady()) return;
        if (entityDriveClass == null || entityAbstractSummonedSwordClass == null) return;
        if (summonedSwordGetIsCriticalMethod == null || summonedSwordSetIsCriticalMethod == null) return;
        // 2026-09-10：判据上移到父类，覆盖幻影剑一族（见 sanitizeBossBladeDrives 的说明）。
        if (!entityAbstractSummonedSwordClass.isInstance(entity)) return;
        if (!(entity instanceof Projectile drive)) return;
        sanitizeBladeDriveCritical(drive);
    }

    /**
     * IShootable 投射物入世即补 shooter（供 EntityJoinLevelEvent 调用）。
     * <p>
     * 孤儿投射物（getOwner()==null）大概率由 Boss 拔刀剑 SA 时间轴生成；玩家持刀扫描
     * ArrowReflector 时 getShooter()==null 会 NPE 崩端。此处若所在 level 有在场 Redios，
     * 立即补 shooter=Boss，关掉「入世→被扫描」的 1 tick 空窗；无 Boss 则跳过（玩家侧自设 owner）。
     */
    public static void sanitizeShooterOnJoin(Entity entity) {
        if (entity == null) return;
        if (!ensureReflectionReady()) return;
        if (iShootableClass == null || iShootableSetShooterMethod == null) return;
        Level level = entity.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        if (!iShootableClass.isInstance(entity)) return;
        if (!(entity instanceof Projectile projectile) || !projectile.isAlive()) return;
        if (projectile.getOwner() != null) return;
        RediosEntity boss = null;
        // 仅当实体在 Boss 附近（16 格）时才补归属（2026-09-01 收紧，子代理审查发现）：
        // 原实现全维度找第一个在场 Redios，把远处玩家 SA 暂缺 owner 的刀光/次元斩也劫持
        // 到 Boss，造成错误归属（getOwner()==boss 过滤反向结算到玩家身上）。
        for (Entity e : serverLevel.getEntities().getAll()) {
            if (e instanceof RediosEntity r && r.isAlive()
                && r.distanceToSqr(projectile) <= 256.0) {
                boss = r;
                break;
            }
        }
        if (boss == null) return;
        try {
            iShootableSetShooterMethod.invoke(projectile, boss);
        } catch (Exception ex) {
            LOG.warn("Failed to assign boss shooter on join: {}", ex.toString());
        }
    }

    /** 单发剑气就地清扫暴击：判据见 {@link #isDangerousBladeDrive(Projectile)}。 */
    private static void sanitizeBladeDriveCritical(Projectile drive) {
        try {
            if (!Boolean.TRUE.equals(summonedSwordGetIsCriticalMethod.invoke(drive))) return;
            if (isDangerousBladeDrive(drive)) {
                summonedSwordSetIsCriticalMethod.invoke(drive, false);
            }
        } catch (Exception e) {
            // 单发失败忽略，避免刷屏
        }
    }

    /** 判断剑气 owner 是否危险（会触发 EntityDrive.onHitEntity 负伤害暴击 nextInt(负) 崩服）。 */
    private static boolean isDangerousBladeDriveOwner(Entity owner) {
        if (owner == null) return true;
        if (owner instanceof RediosEntity) return true; // Boss 剑气一律关暴击（防配置/诅咒把 Boss 攻击力改负）
        if (owner instanceof LivingEntity living) {
            return living.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 0.0;
        }
        return false;
    }

    /**
     * 判断**剑气整体**是否危险（2026-09-10 补强，实测崩服后加）。
     * <p>
     * 原判据 {@link #isDangerousBladeDriveOwner(Entity)} **只看 owner**（空 / Redios / 攻击力 ≤ 0），
     * 漏掉「owner 健康、但第三方 SA 用 {@code setDamage(负)} 传进来的剑气」——实测崩服即这条路径
     * （{@code EntityDrive.onHitEntity:303} → {@code nextInt(Mth.ceil(damageValue)/2+2)}，bound ≤ 0）。
     * <p>
     * 命中时的 {@code damageValue = getDamage() × owner.ATTACK_DAMAGE × scale × 全局倍率}
     * （两处乘法**都没有 clamp**，见 `EntityDrive.onHitEntity` 偏移 137-148 / 325-347），
     * 所以「自身 {@code getDamage() ≤ 0}」与「owner 攻击力 ≤ 0」是两个必须同时覆盖的入口。
     */
    private static boolean isDangerousBladeDrive(Projectile drive) {
        if (isDangerousBladeDriveOwner(drive.getOwner())) return true;
        return bladeDriveBaseDamage(drive) <= 0.0;
    }

    /**
     * 读 IShootable.getDamage()（剑气 base 伤害，命中时还要再乘 owner 攻击力）。
     * 读不到时返回 {@code NaN}——比较恒为 false，即"不危险"，避免误关全部剑气的暴击。
     */
    private static double bladeDriveBaseDamage(Projectile drive) {
        if (iShootableGetDamageMethod == null || iShootableClass == null || !iShootableClass.isInstance(drive)) {
            return Double.NaN;
        }
        try {
            Object value = iShootableGetDamageMethod.invoke(drive);
            return value instanceof Number number ? number.doubleValue() : Double.NaN;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * 手动碰撞检测：Boss 拔刀剑投射物（剑气 EntityDrive / 幻影剑 SummonedSword /
     * 刀光 EntitySlashEffect / 次元斩 EntityJudgementCut）命中参战玩家。
     * <p>
     * SlashBlade 默认 {@code pvp_enable=false}：{@code TargetSelector.test}（AttackablePredicate）
     * 对 {@code Player} 直接返回 false，导致 Boss（Mob）射出的拔刀剑投射物 ray-trace 命中
     * 玩家时被丢弃、直接穿过（幻影剑 tick 与刀光/次元斩的 AttackManager.areaAttack 均受其拦截）。
     * 这里每 tick 在服务端扫描 owner == Boss 的全部 IShootable 拔刀剑投射物，与在场参战玩家做
     * AABB 碰撞，命中时按类型强制结算（幻影剑/剑气走 {@code doForceHitEntity}；刀光/次元斩走
     * {@code AttackManager.doAttackWith}），并用 NBT 去重防止同一投射物对同一目标重复结算。
     * SA 随机逻辑不变。
     */
    public static void tryTickBossBladePlayerHits(RediosEntity boss) {
        if (!ensureReflectionReady()) return;
        if (iShootableClass == null || attackManagerDoAttackWithMethod == null
            || iShootableGetDamageMethod == null) return;
        Level level = boss.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel)) return;
        // 崩溃防护（关暴击 / 补 owner）已由 RediosEntity.tick 每战斗 tick 统一调用
        // sanitizeBossBladeEntities，此处只负责刀窗口内的手动碰撞命中。
        if (boss.battleParticipants.isEmpty()) return;

        List<ServerPlayer> targets = new ArrayList<>();
        for (UUID id : boss.battleParticipants) {
            ServerPlayer p = boss.getServerPlayer(id);
            if (p != null && p.isAlive() && !p.isSpectator() && p.level() == level) {
                targets.add(p);
            }
        }
        if (targets.isEmpty()) return;
        // 2026-09-11（代码审计 G19 #3 修复）：battleParticipants 是 HashSet，迭代序不稳定 ——
        // 而下方「飞行追踪」按 targets 的下标取目标，顺序一变目标就漂移。
        // 按实体 id 排序得到会话内稳定的顺序（实体 id 在同一会话内不变）。
        targets.sort(java.util.Comparator.comparingInt(Entity::getId));

        // 覆盖全部 IShootable 拔刀剑投射物：剑气 EntityDrive / 幻影剑 SummonedSword
        // （均 extends EntityAbstractSummonedSword）/ 刀光 EntitySlashEffect / 次元斩
        // EntityJudgementCut（后两者 extends Projectile，无 doForceHitEntity）。
        List<Projectile> blades = level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(64.0),
            e -> iShootableClass.isInstance(e) && e.isAlive()
                && e.getOwner() == boss);
        if (blades.isEmpty()) return;

        for (Projectile blade : blades) {
            // 幻影剑追踪（2026-09-01 用户裁决「SA幻影剑没索敌」）：
            // 直线飞行（shoot 一次性方向）被走位轻易躲掉。已发射（脱离 Boss 8 格）的剑
            // 每 tick 朝目标当前位置改向（EntityAbstractSummonedSword.tick 尊重 deltaMovement，
            // 2.0.7 源码 L325 确认）→ 具备基础索敌；环绕/汇聚阶段（贴近 Boss）不追踪，保留演出。
            if (blade.distanceToSqr(boss) > 64.0 && !targets.isEmpty()) {
                // 2026-09-11（代码审计 G19 #3 修复）：原先**所有**剑统一拉向 targets.get(0)，
                // 而发射方向是按 index % targets.size() 分散的 —— 分散每 tick 被抹掉，全部剑汇聚到同一目标。
                // 现改为按「剑自身实体 id」取模选目标：既保持分散，又保证同一把剑在整个飞行过程中
                // 目标稳定（不随循环顺序或 HashSet 迭代序漂移）。
                LivingEntity track = targets.get(Math.floorMod(blade.getId(), targets.size()));
                Vec3 toTarget = track.getEyePosition().subtract(blade.position());
                if (toTarget.lengthSqr() > 1.0E-6) {
                    Vec3 dir = toTarget.normalize();
                    double speed = blade.getDeltaMovement().length();
                    if (speed < 0.5) {
                        speed = 3.0; // 未发射/静止的剑给默认飞行速度
                    }
                    blade.setDeltaMovement(dir.scale(speed));
                    // 同步朝向（部分渲染与命中判定读取 yRot/xRot）
                    blade.setYRot((float) (net.minecraft.util.Mth.atan2(dir.x, dir.z) * 180.0 / Math.PI));
                    blade.setXRot((float) (net.minecraft.util.Mth.atan2(dir.y,
                        Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * 180.0 / Math.PI));
                }
            }
            AABB bladeBB = blade.getBoundingBox().expandTowards(blade.getDeltaMovement()).inflate(0.5);
            for (ServerPlayer target : targets) {
                if (!target.getBoundingBox().intersects(bladeBB)) continue;
                forceHitBladeTarget(boss, blade, target);
            }
        }
    }

    private static void forceHitBladeTarget(RediosEntity boss, Entity blade, ServerPlayer target) {
        CompoundTag data = blade.getPersistentData();
        ListTag hitList = data.getList(BOSS_BLADE_HIT_TARGETS_TAG, Tag.TAG_INT);
        int targetId = target.getId();
        for (int i = 0; i < hitList.size(); i++) {
            if (hitList.getInt(i) == targetId) {
                return; // 已命中过，跳过防重复结算
            }
        }
        // 命中结算（2026-09-01 修复，子代理审查发现）：
        //  原实现「先写去重条目 + 先挂断魂，后结算命中」——目标 invulnerableTime 无敌帧内
        //  doForceHitEntity 的 hurt 被吞（不掉血），但去重条目已消费、断魂/三连照常触发：
        //  ① 该剑对该玩家永久漏结算（条目已消费、剑穿身无法再命中）；
        //  ② 玩家在 20 tick 无敌窗口内被 5 剑齐射仍吃满 5×2 次三连斩 + 断魂 amplifier 秒满。
        //  现改为：先结算命中，命中落地（血量下降 / 死亡）后才写去重条目并执行断魂/三连；
        //  未命中（无敌帧吞 / 免疫 / 护甲全挡）不消费去重（剑可再尝试）、不断魂、不三连——
        //  保持「玩家可用无敌帧躲避剑气/幻影剑」的 vanilla 语义。
        float hpBefore = target.getHealth();
        boolean deadBefore = target.isDeadOrDying();
        // 反作弊惩罚窗口：强制命中（无视目标自定义无敌帧，2026-09-01 用户裁决）
        if (boss.anticheat.isPunishWindowActive(boss.tickCount)) {
            target.invulnerableTime = 0;
        }
        try {
            if (entityAbstractSummonedSwordClass != null
                && summonedSwordDoForceHitEntityMethod != null
                && entityAbstractSummonedSwordClass.isInstance(blade)) {
                // 幻影剑/剑气：EntityAbstractSummonedSword.doForceHitEntity（原版命中路径）。
                summonedSwordDoForceHitEntityMethod.invoke(blade, target);
            } else {
                // 刀光/次元斩：无 doForceHitEntity，走 AttackManager.doAttackWith 强制结算
                // （内部 target.hurt(src, amount) + invulnerableTime 处理），绕过 pvp_enable=false。
                double damage = ((Number) iShootableGetDamageMethod.invoke(blade)).doubleValue();
                DamageSource src = blade.damageSources().indirectMagic(blade, boss);
                attackManagerDoAttackWithMethod.invoke(null, src, (float) damage, target, true, true);
            }
        } catch (Exception e) {
            // 每把剑每 tick 碰撞判定：反射持续失败（且不写去重⇒同剑每 tick 重试）时按熔断式 warn 只记首条（2026-10-06）
            warnOnce("force_blade_hit", "Failed to force blade hit on player via reflection: {}", e.toString());
        }
        boolean hit = !target.isDeadOrDying() ? target.getHealth() < hpBefore - 0.001f : !deadBefore;
        if (!hit) {
            return; // 未造成伤害（无敌帧吞 / 免疫）：不写去重、不断魂、不三连
        }
        hitList.add(IntTag.valueOf(targetId));
        data.put(BOSS_BLADE_HIT_TARGETS_TAG, hitList);
        // 拔刀剑投射物（幻影剑/剑气）命中统一结算（对齐近战 doHurtTarget 的 SE 链路）：
        // 1) 断魂：海天断魂解锁后叠加统一断魂（两个模组断魂一个设计，走 silent_sun 同一套）；
        // 2) 两道斩击：triple_whammy SE 复刻（灭却之日监听 SlashBladeEvent.HitEvent 但带
        //    instanceof Player 检查，Boss 进不来，这里手动补两道额外斩击，内部按目标/tick 限频）。
        boss.markSoulSeverIfUnlocked(target);
        if (target.isAlive()) {
            tryApplyBossTripleWhammy(boss, target);
        }
    }

    /**
     * Boss 专用幻影剑生成（齐射）——修复「Boss 从没真正生成过幻影剑」的链路缺陷。
     * <p>
     * SlashBlade 的幻影剑入口 {@code SummonedSwordArts} 四个 perform*（SpiralSwords /
     * StormSwords / BlisteringSwords / HeavyRains）开头均带 {@code instanceof ServerPlayer}
     * 检查，Boss（Mob）永远进不去；{@link #tryFireBossPhantomSwords(RediosEntity)} 只是
     * 「有剑才发射」，此前却没有任何代码为 Boss 造剑，导致幻影剑攻击是死代码。
     * <p>
     * 这里按玩家 {@code onInputChange} 同款链路反射直发 {@code slashblade:summoned_sword}
     * （幻影剑基类实体）：生成即 {@code shoot()}，无需 doFire/rideTick 环绕。伤害取 Boss
     * 攻击力（幻影剑 onHitEntity 不像 EntityDrive 那样再乘 owner.ATTACK_DAMAGE，只乘
     * SLASHBLADE_DAMAGE scale，Boss 无该属性恒为 1.0，故 setDamage(攻击力) 即等效普攻）。
     * 命中玩家由 {@link #tryTickBossBladePlayerHits(RediosEntity)} 的 doForceHitEntity
     * 绕过 pvp_enable=false 拦截。
     */
    public static void trySpawnBossPhantomSwords(LivingEntity boss, LivingEntity target) {
        if (!ensureReflectionReady()) return;
        if (entityAbstractSummonedSwordClass == null || summonedSwordCtor == null
            || summonedSwordEntityType == null || summonedSwordSetOwnerMethod == null
            || summonedSwordSetDamageMethod == null || summonedSwordSetColorMethod == null
            || summonedSwordShootMethod == null) return;
        Level level = boss.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel)) return;

        float attack = (float) boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (attack <= 0.0f) return; // 虚弱减攻为负/零时跳过，避免负伤害与零输出

        ItemStack blade = boss.getMainHandItem();
        // TODO(审计清理 G19 #7)：幻影剑默认色裸写 0x3333FF，与本文件常量 DRIVE_COLOR 重复定义（同值两处，改色会漏改） —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
        int color = isSlashBladeItem(blade.getItem()) ? bladeColorCode(blade) : 0x3333FF;

        Vec3 eye = boss.getEyePosition();
        Vec3 toTarget = (target != null && target.isAlive())
            ? target.getEyePosition().subtract(eye)
            : boss.getLookAngle();
        if (toTarget.lengthSqr() < 1.0E-6) {
            toTarget = boss.getLookAngle();
        } else {
            toTarget = toTarget.normalize();
        }

        // 扇面齐射：中间直射 + 左右各两把，共 5 把，观感为「一排幻影剑扫向目标」
        int count = 5;
        double spreadDeg = 12.0;
        for (int i = 0; i < count; ++i) {
            double angle = (i - (count - 1) / 2.0) * spreadDeg;
            Vec3 dir = toTarget.yRot((float) Math.toRadians(angle));
            try {
                Object sword = summonedSwordCtor.newInstance(summonedSwordEntityType, level);
                Entity entity = (Entity) sword;
                entity.setPos(eye.x, eye.y, eye.z);
                summonedSwordSetOwnerMethod.invoke(sword, boss);
                summonedSwordSetDamageMethod.invoke(sword, (double) attack);
                summonedSwordSetColorMethod.invoke(sword, color);
                if (summonedSwordSetRollMethod != null) {
                    summonedSwordSetRollMethod.invoke(sword, boss.getRandom().nextFloat() * 360.0f);
                }
                summonedSwordShootMethod.invoke(sword, dir.x, dir.y, dir.z, 3.0f, 0.0f);
                level.addFreshEntity(entity);
            } catch (Exception e) {
                LOG.warn("Failed to spawn boss phantom sword via reflection: {}", e.toString());
            }
        }
    }

    /**
     * 修复「Boss 召唤了幻影剑但未发射」：
     * <ul>
     *   <li>原生 slashblade 幻影剑（SpiralSwords / StormSwords / BlisteringSwords / HeavyRainSwords）
     *       生成后默认只 startRiding(owner) 环绕站立，必须调用 {@code doFire()} 置位 IT_FIRED 才会
     *       rideTick() → stopRiding()+shoot()。Boss 是 Mob 无输入，无人代打，故服务端扫描后反射 doFire()。</li>
     *   <li>「真正的力量 True POWER」的 EntityBlastSummonedSword 无 doFire，其 setPreBlastSwordList
     *       生成的幻影剑默认朝 owner 内收（向 Boss 汇聚）而非朝玩家射出；这里反射 shoot() 改射向
     *       Boss 当前目标，配合 tryTickBossBladePlayerHits 的 doForceHitEntity 命中玩家并 burst()。</li>
     * </ul>
     */
    public static void tryFireBossPhantomSwords(RediosEntity boss) {
        if (!ensureReflectionReady()) return;
        if (entityAbstractSummonedSwordClass == null) return;
        Level level = boss.level();
        if (level.isClientSide() || !(level instanceof net.minecraft.server.level.ServerLevel)) return;

        // 幻影剑环绕/汇聚阶段紧跟 Boss 本体（半径很小）；已发射的会高速脱离，天然不再命中此范围。
        List<Projectile> blades = level.getEntitiesOfClass(Projectile.class, boss.getBoundingBox().inflate(16.0),
            e -> entityAbstractSummonedSwordClass.isInstance(e) && e.isAlive()
                && e.getOwner() == boss);
        if (blades.isEmpty()) return;

        // 索敌目标池：优先当前 AI 目标，其余为在场参战玩家。目标池为空（脱战）时仍发射，
        // 方向退回 Boss 面向，保证幻影剑始终朝 Boss 攻击方向射出。
        List<LivingEntity> targets = new ArrayList<>();
        LivingEntity primary = boss.getTarget();
        if (primary != null && primary.isAlive()) {
            targets.add(primary);
        }
        for (UUID id : boss.battleParticipants) {
            ServerPlayer p = boss.getServerPlayer(id);
            if (p != null && p.isAlive() && !p.isSpectator() && p.level() == level && p != primary) {
                targets.add(p);
            }
        }

        int bladeIndex = 0;
        for (Projectile blade : blades) {
            Method doFire = resolveSummonedSwordDoFire(blade);
            if (doFire != null) {
                fireSummonedSword(blade, doFire, boss, targets, bladeIndex++);
                continue;
            }
            // 无 doFire：基类 EntityAbstractSummonedSword（生成即 shoot）与 truepower 内收型剑
            // 默认方向均非 Boss 目标，轮询改射向不同目标（分散索敌）
            retargetSummonedSword(blade, boss, targets, bladeIndex++);
        }
    }

    private static void fireSummonedSword(Entity blade, Method doFire, RediosEntity boss, List<LivingEntity> targets, int index) {
        CompoundTag data = blade.getPersistentData();
        int fireAt = data.getInt(PHANTOM_SWORD_FIRE_AT_TAG);
        if (fireAt == 0) {
            fireAt = blade.tickCount + PHANTOM_SWORD_FIRE_DELAY_TICKS;
            data.putInt(PHANTOM_SWORD_FIRE_AT_TAG, fireAt);
        }
        if (blade.tickCount < fireAt) return;
        try {
            // 先脱离骑乘，防止原生 rideTick() 用「owner→剑」的径向方向覆盖发射向量
            blade.stopRiding();
            // 置 IT_FIRED，激活幻影剑自身命中检测（doFire 只负责该标志位，不负责方向）
            doFire.invoke(blade);
            // 显式朝目标方向发射：目标池为空时退回 Boss 面向
            Vec3 dir = pickPhantomSwordDirection(blade, boss, targets, index);
            if (summonedSwordShootMethod != null) {
                summonedSwordShootMethod.invoke(blade, dir.x, dir.y, dir.z, 3.0f, 0.0f);
            }
            // 2026-09-11（代码审计 G19 #1 修复）：本模块已按扇面索引（index % targets.size()）定好
            // 方向并 shoot，此处补打「已定向」标记，让同 tick 的 retargetSummonedSword 跳过 ——
            // 否则它会重算方向并再次 shoot，把刚摆好的扇面覆盖成多把同向重叠的剑
            // （即「扇面齐射」从未真正生效）。
            data.putBoolean(PHANTOM_SWORD_RETARGET_TAG, true);
        } catch (Exception e) {
            LOG.warn("Failed to fire boss phantom sword via reflection: {}", e.toString());
        }
    }

    private static Vec3 pickPhantomSwordDirection(Entity blade, RediosEntity boss, List<LivingEntity> targets, int index) {
        if (!targets.isEmpty()) {
            LivingEntity target = targets.get(index % targets.size());
            Vec3 toTarget = target.getEyePosition().subtract(blade.position());
            if (toTarget.lengthSqr() > 1.0E-6) {
                return toTarget.normalize();
            }
        }
        return boss.getLookAngle();
    }

    private static void retargetSummonedSword(Entity blade, RediosEntity boss, List<LivingEntity> targets, int index) {
        if (summonedSwordShootMethod == null) return;
        // 无 doFire 的幻影剑（基类 EntityAbstractSummonedSword 生成即 shoot、truepower 内收型剑）
        // 默认方向取 owner 朝向/侧面而非 Boss 当前目标，统一改射向索敌目标。
        CompoundTag data = blade.getPersistentData();
        if (data.getBoolean(PHANTOM_SWORD_RETARGET_TAG)) return;

        Vec3 dir = pickPhantomSwordDirection(blade, boss, targets, index);
        // 2026-09-11（代码审计 G19 #4 修复）：标记改到 shoot **成功之后**再打。
        // 原实现先写标记、后 invoke —— 一旦 invoke 抛异常，该剑就被永久标记为「已定向」，
        // 再也不会被重试改向（而方向其实从未下发）。
        try {
            summonedSwordShootMethod.invoke(blade, dir.x, dir.y, dir.z, 3.0f, 0.0f);
            data.putBoolean(PHANTOM_SWORD_RETARGET_TAG, true);
        } catch (Exception e) {
            LOG.warn("Failed to retarget phantom sword: {}", e.toString());
        }
    }

    private static Method resolveSummonedSwordDoFire(Entity blade) {
        String key = blade.getClass().getName();
        Method cached = summonedSwordDoFireMethodCache.get(key);
        if (cached != null) return cached;
        if (summonedSwordNoDoFireClasses.contains(key)) return null;
        try {
            Method m = blade.getClass().getMethod(SUMMONED_SWORD_DO_FIRE_METHOD);
            summonedSwordDoFireMethodCache.put(key, m);
            return m;
        } catch (NoSuchMethodException e) {
            // 该子类没有 doFire()，缓存类名避免每次重试反射（ConcurrentHashMap 不允许 null 值）
            summonedSwordNoDoFireClasses.add(key);
            return null;
        }
    }

    /**
     * Check if a given Item is a SlashBlade (拔刀剑) item.
     * Safe to call when the mod is not installed — returns false.
     */
    public static boolean isSlashBladeItem(Item item) {
        if (!ensureReflectionReady()) return false;
        return slashBladeItemClass != null && slashBladeItemClass.isInstance(item);
    }

    /**
     * Check if an ItemStack is a potential ghost item (references an uninstalled mod).
     * Ghost items occur when a player picks up a boss drop weapon whose mod is absent.
     */
    static boolean isGhostWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        String className = item.getClass().getName();
        // If the item's class references extinction_day or slashblade namespaces
        // but the mods are not actually loaded, it's a ghost
        if (className.contains("extinction_day") && !isSlashBladeIntegrationAvailable()) {
            return true;
        }
        if (className.contains("slashblade") && slashBladeItemClass == null) {
            return true;
        }
        return false;
    }

    /**
     * Replace ghost weapon items in a player's inventory with safe alternatives.
     * <p>
     * ⚠️ 2026-09-11（代码审计 G19 #2）：本方法**全库无调用者**（未接线能力）。本次只补安全守卫、
     * <b>不接线</b>（是否接线需作者裁决，已在审计登记）。原实现两个隐患：
     * <ol>
     *   <li>{@link #isGhostWeapon} 的判据之一依赖 {@code slashBladeItemClass}，而该字段只在
     *       {@link #ensureReflectionReady()} 成功后才非 null —— 若在反射未就绪时执行本方法，
     *       <b>所有</b>拔刀剑物品都会被判为 ghost → 不可逆清空玩家背包里的刀；</li>
     *   <li>清除方式为直接置空且无掉落，一旦误判即永久损失物品。</li>
     * </ol>
     * 现改为：反射未就绪时直接跳过（判据不可信）；清除时把物品掉落到玩家脚下，误判仍可拾回。
     */
    static void sanitizeGhostWeapons(Player player) {
        if (!ensureReflectionReady()) {
            return; // 判据不可信，宁可不清理也不能误删玩家物品
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isGhostWeapon(stack)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                player.drop(stack, false);
                LOG.warn("Removed ghost weapon from {} slot {}: {}", player.getName().getString(), i, stack);
            }
        }
    }

    // ── Internal ──

    /** 反射读刀 SE 列表，判断是否含灭却之日 triple_whammy（id path 精确匹配） */
    private static boolean hasTripleWhammySE(Object state) throws Exception {
        Object se = getSpecialEffectsMethod.invoke(state);
        if (se instanceof Collection<?> c) {
            for (Object o : c) {
                if (o instanceof ResourceLocation rl && TRIPLE_WHAMMY_SE_PATH.equals(rl.getPath())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 反射读刀刃颜色（ISlashBladeState.getColorCode），失败回退白色 */
    private static int bladeColorCode(ItemStack blade) {
        try {
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
            if (stateOpt instanceof Optional<?> opt && opt.isPresent()) {
                return ((Number) getColorCodeMethod.invoke(opt.get())).intValue();
            }
        } catch (Exception ignored) {
            // 2026-09-12（审计清理 G19 #5）：原先空 catch 静默回退白色，刀色偏差在日志里毫无痕迹。
            // 调用点：tryApplyBossTripleWhammy（10-04 后每次命中）与 trySpawnBossPhantomSwords
            // （60~90 tick 齐射）。失败必属「已装拔刀剑但反射异常」的真实故障，需要可见；
            // 但每次命中频率高，2026-10-06 改为熔断式只记首条（形参仍名 ignored，控制改动面）。
            warnOnce("blade_color", "bladeColorCode 反射读取刀刃颜色失败，回退白色 0xFFFFFF（幻影剑/三连刀光会偏色）", ignored);
        }
        return 0xFFFFFF;
    }

    /**
     * 反射生成一个 slashblade 刀光实体（EntitySlashEffect），参数与玩家 doSlash / TripleWhammy 一致。
     * damage=0 纯视觉（KnockBacks.cancel ordinal=0 无击退）。
     * <p>
     * 2026-08-30：刀光速率的源头限速在 BladeAttackGoal 的 combo 驱动间隔（≥7 tick），
     * 本方法不额外丢弃。
     */
    private static void spawnSlashEffect(LivingEntity owner, Vec3 pos, float roll, int color,
                                         boolean mute, boolean critical, double damage) {
        try {
            Object slash = entitySlashEffectCtor.newInstance(slashEffectEntityType, owner.level());
            Entity entity = (Entity) slash;
            slashEffectSetPosMethod.invoke(slash, pos.x, pos.y, pos.z);
            slashEffectSetOwnerMethod.invoke(slash, owner);
            slashEffectSetRotationRollMethod.invoke(slash, roll);
            slashEffectSetYRotMethod.invoke(slash, owner.getYRot());
            slashEffectSetXRotMethod.invoke(slash, 0.0f);
            slashEffectSetColorMethod.invoke(slash, color);
            slashEffectSetMuteMethod.invoke(slash, mute);
            slashEffectSetIsCriticalMethod.invoke(slash, critical);
            slashEffectSetDamageMethod.invoke(slash, damage);
            slashEffectSetKnockBackOrdinalMethod.invoke(slash, 0);
            owner.level().addFreshEntity(entity);
        } catch (Exception e) {
            // 燕返每次命中调 2 次：反射持续失败时按熔断式 warn 只记首条（2026-10-06）
            warnOnce("spawn_slash_effect", "Failed to spawn slash blade effect entity (刀光): {}", e.toString());
        }
    }

    /** 判断实体是否为 slashblade 刀光（EntitySlashEffect）；反射缓存未就绪时返回 false。 */
    public static boolean isSlashEffectEntity(net.minecraft.world.entity.Entity e) {
        return entitySlashEffectClass != null && entitySlashEffectClass.isInstance(e);
    }

    private static boolean ensureReflectionReady() {
        if (!isSlashBladeIntegrationAvailable()) return false;
        if (reflectionInitialized) return true;
        synchronized (IntegrationContract.class) {
            if (reflectionInitialized) return true;
            try {
                Class<?> modMainClass = Class.forName(EXTINCTION_DAY_MOD_CLASS);
                Field field = modMainClass.getField(MIEDAO_DUAN_FIELD);
                miedaoPrototype = field.get(null);
                miedaoGetMethod = miedaoPrototype.getClass().getMethod("get");
                slashBladeItemClass = Class.forName(SLASH_BLADE_ITEM_CLASS);
                Class<?> definitionClass = Class.forName(SLASH_BLADE_DEFINITION_CLASS);
                slashBladeRegistryKey = (ResourceKey<?>) definitionClass.getField(REGISTRY_KEY_FIELD).get(null);
                slashBladeGetBladeMethod = definitionClass.getMethod(GET_BLADE_METHOD, Item.class, HolderLookup.Provider.class);
                // 随机 SA：SlashArtsRegistry.REGISTRY + SlashArts.doArts + BladeStateAccess/ISlashBladeState
                Class<?> slashArtsRegistryClass = Class.forName(SLASH_ARTS_REGISTRY_CLASS);
                slashArtsRegistry = slashArtsRegistryClass.getField(SLASH_ARTS_REGISTRY_FIELD).get(null);
                slashArtsRegistryKeySetMethod = slashArtsRegistry.getClass().getMethod(SLASH_ARTS_REGISTRY_KEYSET_METHOD);
                // NeoForge 1.21.1 的 Registry 接口与 vanilla 不同：
                //   keySet() 返回 Set<ResourceLocation>（不是 ResourceKey）；
                //   get(ResourceLocation) 直接返回 T（SlashArts 本体，无 Optional/Holder 包装）。
                // 旧代码按 ResourceKey 取 get → 每次以 ResourceLocation 的 key 调用必抛
                // IllegalArgumentException: argument type mismatch（SA 全程放不出）。
                slashArtsRegistryGetMethod = slashArtsRegistry.getClass().getMethod(SLASH_ARTS_REGISTRY_GET_METHOD, ResourceLocation.class);
                Class<?> artsTypeClass = Class.forName(SLASH_ARTS_ARTSTYPE_CLASS);
                artsTypeSuccess = artsTypeClass.getField("Success").get(null);
                slashArtsDoArtsMethod = Class.forName(SLASH_ARTS_CLASS)
                    .getMethod(SLASH_ARTS_DO_ARTS_METHOD, artsTypeClass, LivingEntity.class);
                bladeStateAccessOfMethod = Class.forName(BLADE_STATE_ACCESS_CLASS)
                    .getMethod(BLADE_STATE_ACCESS_OF_METHOD, ItemStack.class);
                updateComboSeqMethod = Class.forName(ISLASH_BLADE_STATE_CLASS)
                    .getMethod(ISLASH_BLADE_STATE_UPDATE_COMBO_METHOD, LivingEntity.class, ResourceLocation.class);
                // 普攻连击推进（玩家左键链路）：ISlashBladeState.progressCombo(LivingEntity)
                progressComboMethod = Class.forName(ISLASH_BLADE_STATE_CLASS)
                    .getMethod(ISLASH_BLADE_STATE_PROGRESS_COMBO_METHOD, LivingEntity.class);
                // Boss 刀刃强化 setter：ISlashBladeState.setKillCount/setProudSoulCount/setRefine(int)
                setKillCountMethod = Class.forName(ISLASH_BLADE_STATE_CLASS)
                    .getMethod(ISLASH_BLADE_STATE_SET_KILL_COUNT_METHOD, int.class);
                setProudSoulCountMethod = Class.forName(ISLASH_BLADE_STATE_CLASS)
                    .getMethod(ISLASH_BLADE_STATE_SET_PROUD_SOUL_COUNT_METHOD, int.class);
                setRefineMethod = Class.forName(ISLASH_BLADE_STATE_CLASS)
                    .getMethod(ISLASH_BLADE_STATE_SET_REFINE_METHOD, int.class);
                // Boss 每 tick 驱动 combo 生命周期（Mob 无 inventoryTick，需手动推进）
                resolvCurrentComboStateMethod = Class.forName(ISLASH_BLADE_STATE_CLASS)
                    .getMethod(ISLASH_BLADE_STATE_RESOLV_COMBO_METHOD, LivingEntity.class);
                // 2026-09-11（代码审计 G18 #1 修复）：此处原解析 ComboStateRegistry / ComboState.tickAction
                // 共三个字段（comboStateRegistry / comboStateRegistryGetMethod / comboStateTickActionMethod），
                // 但全库**零消费**；且它们与上面的活反射项同处一个 try —— 任一 Class.forName / getMethod
                // 抛异常都会走到 catch 把 cachedAvailable 置 false，**整段拔刀剑集成连带失效**。已删除。
                // EntityDrive（剑气）反射：super_burst_drive 的剑气链路（DoSlashEvent →
                // SuperBurstDriveEffect.onDoingSlash → doBurstDrive(Player)）带 instanceof Player
                // 检查，Boss（Mob）挥刀触发 DoSlashEvent 时剑气被跳过。这里反射直发同款剑气实体。
                entityDriveClass = Class.forName(ENTITY_DRIVE_CLASS);
                entityDriveCtor = entityDriveClass.getConstructor(net.minecraft.world.entity.EntityType.class, net.minecraft.world.level.Level.class);
                driveSetDamageMethod = entityDriveClass.getMethod(ENTITY_DRIVE_SET_DAMAGE_METHOD, double.class);
                driveSetSpeedMethod = entityDriveClass.getMethod(ENTITY_DRIVE_SET_SPEED_METHOD, float.class);
                driveSetColorMethod = entityDriveClass.getMethod(ENTITY_DRIVE_SET_COLOR_METHOD, int.class);
                driveSetLifetimeMethod = entityDriveClass.getMethod(ENTITY_DRIVE_SET_LIFETIME_METHOD, float.class);
                // Projectile.shoot / setOwner 为继承的 public 方法，同样走 getMethod
                driveShootMethod = entityDriveClass.getMethod("shoot", double.class, double.class, double.class, float.class, float.class);
                driveSetOwnerMethod = entityDriveClass.getMethod("setOwner", net.minecraft.world.entity.Entity.class);
                // EntityAbstractSummonedSword.doForceHitEntity(Entity)：手动碰撞强制命中，
                // 绕过 EntityAbstractSummonedSword.tick 里 TargetSelector.test 的 pvp_enable=false
                // 拦截（默认 pvp 关闭时 Boss 剑气/部分幻影剑 ray-trace 命中玩家被丢弃、直接穿过）。
                entityAbstractSummonedSwordClass = Class.forName(ENTITY_ABSTRACT_SUMMONED_SWORD_CLASS);
                summonedSwordDoForceHitEntityMethod = entityAbstractSummonedSwordClass
                    .getMethod(ENTITY_ABSTRACT_SUMMONED_SWORD_DO_FORCE_HIT_METHOD, net.minecraft.world.entity.Entity.class);
                // shoot 为 EntityAbstractSummonedSword 继承自 Projectile 的 public 方法（不依赖具体子类）
                summonedSwordShootMethod = entityAbstractSummonedSwordClass
                    .getMethod("shoot", double.class, double.class, double.class, float.class, float.class);
                // 暴击开关：强制关暴击规避 EntityDrive.onHitEntity 负伤害暴击分支崩服
                summonedSwordSetIsCriticalMethod = entityAbstractSummonedSwordClass
                    .getMethod(ENTITY_ABSTRACT_SUMMONED_SWORD_SET_IS_CRITICAL_METHOD, boolean.class);
                summonedSwordGetIsCriticalMethod = entityAbstractSummonedSwordClass
                    .getMethod(ENTITY_ABSTRACT_SUMMONED_SWORD_GET_IS_CRITICAL_METHOD);
                // Boss 专用幻影剑：玩家 onInputChange 用 EntityAbstractSummonedSword 基类实体
                // （RegistryEvents.SummonedSword）生成即 shoot()，无需 doFire/rideTick 环绕。
                // Boss（Mob）进不了 SummonedSwordArts（perform* 均要求 ServerPlayer），
                // 故由 silent_sun 反射同款实体直发，配合 tryTickBossBladePlayerHits 命中玩家。
                summonedSwordEntityType = Class.forName(REGISTRY_EVENTS_CLASS)
                    .getField(REGISTRY_EVENTS_SUMMONED_SWORD_FIELD).get(null);
                summonedSwordCtor = entityAbstractSummonedSwordClass
                    .getConstructor(net.minecraft.world.entity.EntityType.class, net.minecraft.world.level.Level.class);
                summonedSwordSetColorMethod = entityAbstractSummonedSwordClass
                    .getMethod("setColor", int.class);
                summonedSwordSetDamageMethod = entityAbstractSummonedSwordClass
                    .getMethod("setDamage", double.class);
                summonedSwordSetRollMethod = entityAbstractSummonedSwordClass
                    .getMethod("setRoll", float.class);
                summonedSwordSetOwnerMethod = entityAbstractSummonedSwordClass
                    .getMethod("setOwner", net.minecraft.world.entity.Entity.class);
                // IShootable 接口方法 setShooter(Entity)（三个实现类均委托 setOwner）：
                // 给无 owner 的拔刀剑投射物补 shooter，规避 ArrowReflector 扫描 IShootable 时
                // getShooter()==null 崩溃。用接口反射而非 EntityAbstractSummonedSword 类反射，
                // 才能同时覆盖 EntitySlashEffect / EntityJudgementCut 等非幻影剑的 IShootable 实现。
                iShootableClass = Class.forName(ISHOOTABLE_CLASS);
                iShootableSetShooterMethod = iShootableClass
                    .getMethod("setShooter", net.minecraft.world.entity.Entity.class);
                iShootableGetShooterMethod = iShootableClass.getMethod("getShooter");
                iShootableGetDamageMethod = iShootableClass.getMethod(ISHOOTABLE_GET_DAMAGE_METHOD);
                // AttackManager.doAttackWith(DamageSource,float,Entity,boolean,boolean)：静态方法
                // （内部忽略 EntityAbstractSummonedSword、其余走 target.hurt + invulnerableTime 处理）。
                attackManagerDoAttackWithMethod = Class.forName(ATTACK_MANAGER_CLASS).getMethod(
                    ATTACK_MANAGER_DO_ATTACK_WITH_METHOD,
                    DamageSource.class, float.class, Entity.class, boolean.class, boolean.class);
                // EntitySlashEffect（刀光）：普攻斩击轨迹 + triple_whammy 三连特效。
                // Mob 无 IInputState 触不到玩家左键链路（clickAction），且 slashblade 的
                // HitEvent/DoSlashEvent 对非 Player 实体常被拦截，这里按 doSlash/TripleWhammy
                // 同款参数反射直发实体：构造(EntityType, Level) + 继承自 Entity 的 setPos/setOwner/
                // setYRot/setXRot + 实体自有 setRotationRoll/setColor/setMute/setIsCritical/
                // setDamage/setKnockBackOrdinal。
                entitySlashEffectClass = Class.forName(ENTITY_SLASH_EFFECT_CLASS);
                entitySlashEffectCtor = entitySlashEffectClass.getConstructor(net.minecraft.world.entity.EntityType.class, net.minecraft.world.level.Level.class);
                slashEffectEntityType = Class.forName(REGISTRY_EVENTS_CLASS)
                    .getField(REGISTRY_EVENTS_SLASH_FIELD).get(null);
                slashEffectSetDamageMethod = entitySlashEffectClass
                    .getMethod(ENTITY_DRIVE_SET_DAMAGE_METHOD, double.class);
                slashEffectSetKnockBackOrdinalMethod = entitySlashEffectClass
                    .getMethod(ENTITY_SLASH_EFFECT_SET_KNOCKBACK_ORDINAL_METHOD, int.class);
                slashEffectSetRotationRollMethod = entitySlashEffectClass
                    .getMethod(ENTITY_SLASH_EFFECT_SET_ROTATION_ROLL_METHOD, float.class);
                slashEffectSetColorMethod = entitySlashEffectClass
                    .getMethod(ENTITY_SLASH_EFFECT_SET_COLOR_METHOD, int.class);
                slashEffectSetMuteMethod = entitySlashEffectClass
                    .getMethod(ENTITY_SLASH_EFFECT_SET_MUTE_METHOD, boolean.class);
                slashEffectSetIsCriticalMethod = entitySlashEffectClass
                    .getMethod(ENTITY_SLASH_EFFECT_SET_IS_CRITICAL_METHOD, boolean.class);
                slashEffectSetPosMethod = entitySlashEffectClass
                    .getMethod("setPos", double.class, double.class, double.class);
                slashEffectSetOwnerMethod = entitySlashEffectClass
                    .getMethod("setOwner", net.minecraft.world.entity.Entity.class);
                slashEffectSetYRotMethod = entitySlashEffectClass.getMethod("setYRot", float.class);
                slashEffectSetXRotMethod = entitySlashEffectClass.getMethod("setXRot", float.class);
                // ISlashBladeState：SE 判断（getSpecialEffects / getRefine）与刀色（getColorCode）
                Class<?> iStateClass = Class.forName(ISLASH_BLADE_STATE_CLASS);
                getColorCodeMethod = iStateClass.getMethod(ISLASH_BLADE_STATE_GET_COLOR_CODE_METHOD);
                getRefineMethod = iStateClass.getMethod(ISLASH_BLADE_STATE_GET_REFINE_METHOD);
                getSpecialEffectsMethod = iStateClass.getMethod(ISLASH_BLADE_STATE_GET_SPECIAL_EFFECTS_METHOD);
                // DoSlashEvent（玩家挥刀）反射：T-v3-7 全阶段移动避让。非致命——失败仅禁用该监听，
                // 不影响其它拔刀剑集成（不入主 catch，避免 cachedAvailable 被误置 false）。
                try {
                    doSlashEventClass = Class.forName(SLASH_BLADE_EVENT_DO_SLASH_CLASS);
                    doSlashEventGetUserMethod = doSlashEventClass.getMethod(DO_SLASH_EVENT_GET_USER_METHOD);
                } catch (Exception ignored) {
                    doSlashEventClass = null;
                    doSlashEventGetUserMethod = null;
                    LOG.warn("DoSlashEvent reflection unavailable (T-v3-7 disabled): {}", ignored.toString());
                }
                reflectionInitialized = true;
                LOG.info("BladeAttackGoal reflection cache initialized successfully.");
                return true;
            } catch (Exception e) {
                LOG.error("Failed to initialize BladeAttackGoal reflection: {}", e.toString());
                cachedAvailable = false;
                lastAvailabilityCheck = System.currentTimeMillis();
                return false;
            }
        }
    }

    /**
     * 惰性注册 SlashBlade 玩家挥刀（DoSlashEvent）监听器到 NeoForge 主事件总线。
     * <p>
     * T-v3-7：Boss 需在玩家「远程挥刀」（立体范围锁定，如无妄之终）瞬间反向冲刺脱离球体。
     * DoSlashEvent 是 slashblade 的可选依赖事件，无法直接 {@code @SubscribeEvent} 编译引用，
     * 故反射获取事件类后按事件类型注册 Consumer；仅在 slashblade 可用且 Boss 进入过战斗后触发。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerDoSlashListener() {
        if (doSlashListenerRegistered) return;
        if (!ensureReflectionReady()) return;
        if (doSlashEventClass == null || doSlashEventGetUserMethod == null) return;
        doSlashListenerRegistered = true;
        try {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.NORMAL,
                false,
                (Class) doSlashEventClass,
                (java.util.function.Consumer) IntegrationContract::onPlayerDoSlash);
        } catch (Throwable t) {
            doSlashListenerRegistered = false;
            LOG.warn("Failed to register DoSlashEvent listener: {}", t.toString());
        }
    }

    /**
     * 惰性注册 SlashBlade 玩家**蓄力**（{@code ChargeActionEvent}）监听器（2026-09-12 产出观测）。
     * <p>
     * 只做记录：给战斗报告提供「玩家蓄力」时间锚点。第三方 SA 常挂在蓄力/挥刀事件上（例如 recasting 的
     * {@code TimeBeyondSlashArts.onCharge(ChargeActionEvent)} 就是 {@code @SubscribeEvent}）——
     * 产出落在 silent_sun 驱动窗口之外时，靠这个锚点才能判断那条「第三条路径」是否玩家蓄力触发。
     * <p>
     * <b>与 DoSlashEvent 监听器的实现差别（性能）</b>：{@code ChargeActionEvent} 由
     * {@code ItemSlashBlade.onUseTick} 触发，蓄力期间**每 tick 每个玩家**都会 post，因此回调里
     * **不能**像 {@code onPlayerDoSlash} 那样遍历全服实体找 Boss —— 回调只写两个 volatile 字段
     * （最近一次蓄力的 gameTime + 玩家名），由 Boss 自己的采样去读。注册失败只 warn 并禁用该锚点。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerChargeListener() {
        if (chargeListenerRegistered) {
            return;
        }
        resolveComboProbeReflection();
        if (chargeEventClass == null || chargeEventGetUserMethod == null) {
            return;
        }
        chargeListenerRegistered = true;
        try {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.NORMAL,
                false,
                (Class) chargeEventClass,
                (java.util.function.Consumer) IntegrationContract::onPlayerCharge);
        } catch (Throwable t) {
            chargeListenerRegistered = false;
            LOG.warn("Failed to register ChargeActionEvent listener（产出观测的蓄力锚点已禁用）: {}", t.toString());
        }
    }

    /** 最近一次蓄力事件的 gameTime（{@code <0} = 本进程从未观测到）。 */
    private static volatile long lastChargeEventGameTime = -1L;
    /** 最近一次蓄力者名字（可能是玩家，也可能**就是 Boss 自己** —— 见下）。 */
    private static volatile String lastChargeEventPlayer;
    /** 最近一次蓄力者是否就是本模组 Boss。 */
    private static volatile boolean lastChargeEventEntityIsBoss;
    /**
     * 蓄力事件回调：只写三个 volatile（不遍历实体、不干预逻辑），供 Boss 采样时取用。
     * <p>
     * <b>不过滤成 Player</b>：{@code ChargeActionEvent} 的持有者是任意 {@code LivingEntity}，且第三方处理
     * （如 recasting 的 {@code onCharge}）**不检查是不是玩家**。若实测发现蓄力者就是 Boss 本身，那就说明
     * Boss 正在走 {@code ItemSlashBlade.onUseTick} 链（{@code holdAction} 出口）—— 这正是「第三条路径」
     * 最重要的候选之一（silent_sun 自己从不 {@code startUsingItem}，全库零调用）。
     */
    static void onPlayerCharge(Object event) {
        try {
            Object holder = chargeEventGetUserMethod.invoke(event);
            if (!(holder instanceof LivingEntity living)) {
                return;
            }
            Level level = living.level();
            if (level == null || level.isClientSide()) {
                return;
            }
            lastChargeEventGameTime = level.getGameTime();
            lastChargeEventPlayer = living.getName().getString();
            lastChargeEventEntityIsBoss = living instanceof RediosEntity;
        } catch (Throwable t) {
            // 单次事件异常忽略，避免监听器抛异常影响 slashblade 事件链。
        }
    }

    /** 最近一次蓄力的 gameTime（{@code <0} = 从未观测到）；只读，供 Boss 采样侧消费。 */
    public static long lastChargeEventGameTime() {
        return lastChargeEventGameTime;
    }

    /** 最近一次蓄力者名字（可能是玩家，也可能就是 Boss 自己）。 */
    public static String lastChargeEventPlayer() {
        return lastChargeEventPlayer;
    }

    /** 最近一次蓄力者是否就是本模组 Boss（true ⇒ Boss 在走 {@code onUseTick} / {@code holdAction} 链）。 */
    public static boolean lastChargeEventEntityIsBoss() {
        return lastChargeEventEntityIsBoss;
    }

    /** DoSlashEvent 事件回调：反射取 getUser()（挥刀玩家），通知在场 Boss 判断远程挥刀避让。 */
    static void onPlayerDoSlash(Object event) {
        try {
            Object user = doSlashEventGetUserMethod.invoke(event);
            if (!(user instanceof Player player)) return;
            if (player.level().isClientSide()) return;
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
            for (Entity e : serverLevel.getEntities().getAll()) {
                if (e instanceof RediosEntity boss && boss.isAlive()) {
                    // 2026-09-12（产出观测）：玩家挥刀锚点（旁路，零行为变更；内部自带 try/catch）。
                    // 放在 onPlayerRemoteSlash 之前，保证锚点不因主逻辑异常而丢失。
                    boss.flowPlayerSlash(player.getName().getString());
                    boss.onPlayerRemoteSlash(player);
                }
            }
        } catch (Throwable t) {
            // 单次事件异常忽略，避免监听器抛异常影响 slashblade 事件链。
        }
    }
}
