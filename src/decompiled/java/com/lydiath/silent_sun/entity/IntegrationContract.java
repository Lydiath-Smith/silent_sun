package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.config.SilentSunConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
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
    /** 三连斩（triple_whammy 复刻）每目标每 tick 限频 NBT 键（2026-09-01 修复）：5 剑齐射
     *  每把剑独立触发一次双斩三连 = 一波 5×2 次全额攻击，按（目标,tick）合并后每目标
     *  每 tick 至多一次三连（与灭却之日同款「每目标每游戏 tick 至多结算一次」模式）。 */
    static final String BOSS_TRIPLE_WHAMMY_TICK = "SilentSunLastTripleWhammyTick";
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
                // NeoForge 1.21.1：Registry.keySet() 返回 ResourceLocation（非 ResourceKey）。
                // 2026-09-01：随机池 namespace 过滤（黑名单）——狐月刀(foxextra)/天杀星刀
                // (tianshaxing) 的 SA 有 SE 前提且 foxextra 时间线每帧多实体是刀光洪峰源，
                // 默认排除；其余全进池（config redios.bossSaExcludedNamespaces 可调）。
                keys.removeIf(k -> k instanceof ResourceLocation rl
                    && (!isSaAllowed(rl) || SLASH_ARTS_NONE_ID.equals(rl)));
                keyList = new ArrayList<>(keys);
                cachedSlashArtsKeys = keyList;
                cachedSlashArtsKeysAt = now;
            }
            if (keyList.isEmpty()) {
                LOG.warn("slash_arts registry is empty, cannot invoke random SA.");
                return;
            }
            Object key = keyList.get(caster.getRandom().nextInt(keyList.size()));
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
            if (slashArts == null) return;
            Object combo = slashArtsDoArtsMethod.invoke(slashArts, artsTypeSuccess, caster);
            if (!(combo instanceof ResourceLocation comboLoc)) return;
            ItemStack blade = caster.getMainHandItem();
            if (blade.isEmpty()) return;
            Object stateOpt = bladeStateAccessOfMethod.invoke(null, blade);
            if (stateOpt instanceof Optional<?> opt && opt.isPresent()) {
                updateComboSeqMethod.invoke(opt.get(), caster, comboLoc);
            }
        } catch (Exception e) {
            LOG.warn("Failed to invoke random slash art via reflection: {}", e.toString());
        }
    }

    /**
     * SA 随机池 namespace 过滤（2026-09-01，黑名单模式）：默认全放行，仅排除配置列出的
     * namespace（config redios.bossSaExcludedNamespaces，默认 foxextra/tianshaxing）。
     * <p>
     * 狐月刀（foxextra）与天杀星刀（tianshaxing）的 SA 有 SE 前提（Boss 刀 miedao_duan 无对应
     * SE 会放不出/异常）；且 foxextra 的 VoidSlashPlus 时间线（TimeLineTickAction）每帧调
     * Drive.doSlash + AttackManager.doSlash 生成多条剑气+刀光，Boss 驱动时 combo 卡活跃段
     * 回不到 NONE → tickAction 每 tick 刷实体 → 刀光洪峰（实测成千/秒）。其余第三方
     * （amazingshine/shinkubloodkatana 等）无 SE 前提，保留进池，维持全随机。
     */
    static boolean isSaAllowed(ResourceLocation rl) {
        try {
            return !SilentSunConfig.BOSS_SA_EXCLUDED_NAMESPACES.get().contains(rl.getNamespace());
        } catch (Exception e) {
            // 配置读取失败兜底：全放行
            return true;
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
                if (SLASH_ARTS_NONE_ID.equals(rl) || SLASH_BLADE_STANDBY_ID.equals(rl)) {
                    updateComboSeqMethod.invoke(state, caster, COMBO_A1_ID);
                } else {
                    progressComboMethod.invoke(state, caster);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to progress slash blade combo via reflection: {}", e.toString());
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
     * Boss 普攻命中后触发灭却之日 triple_whammy SE（三连击）。
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
            // 每目标每 tick 至多一次三连（2026-09-01 修复）：5 剑齐射每把剑独立触发
            // 双斩三连 = 一波 5×2 次全额攻击；限频后每目标每 tick 至多一次。
            CompoundTag targetData = target.getPersistentData();
            if (targetData.getInt(BOSS_TRIPLE_WHAMMY_TICK) == target.tickCount) return;
            targetData.putInt(BOSS_TRIPLE_WHAMMY_TICK, target.tickCount);
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
            LOG.warn("Failed to apply boss triple whammy (三连) via reflection: {}", e.toString());
        }
    }

    /**
     * Boss 拔刀剑 combo 卡死守卫（每 tick 调用，2026-09-01 改版）。
     * <p>
     * 原 {@code tryTickBladeCombo} 每 tick 手动驱动 {@code resolvCurrentComboState + tickAction}；
     * 反编译确认 slashblade（重锋 2.0.3/2.0.7、Refix 三版一致）的 {@code ItemSlashBlade.inventoryTick}
     * 对持刀 Mob 每 tick 自己驱动同一条链（resolvCurrentComboState 超时迁移 + isInMainhand 时
     * tickAction 执行 TimeLineTickAction）——我们重复驱动 = 刀光翻倍（"刚切刀就有刀光"）。
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
                            st.lastStandbyTick = caster.tickCount;
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to check slash blade combo stuck via reflection: {}", e.toString());
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
                LOG.warn("Failed to sanitize boss drive critical flag: {}", e.toString());
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
                LOG.warn("Failed to assign boss shooter to slashblade projectile: {}", e.toString());
            }
        }
    }

    /** IShootable.getShooter() 反射判空：返回 true 表示无 shooter（孤儿投射物，需补 Boss）。 */
    private static boolean hasNullShooter(Entity e) {
        if (iShootableGetShooterMethod == null) {
            return false;
        }
        try {
            return iShootableGetShooterMethod.invoke(e) == null;
        } catch (Exception ex) {
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
            LOG.warn("Failed to force blade hit on player via reflection: {}", e.toString());
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
            LOG.warn("Failed to spawn slash blade effect entity (刀光): {}", e.toString());
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

    /** DoSlashEvent 事件回调：反射取 getUser()（挥刀玩家），通知在场 Boss 判断远程挥刀避让。 */
    static void onPlayerDoSlash(Object event) {
        try {
            Object user = doSlashEventGetUserMethod.invoke(event);
            if (!(user instanceof Player player)) return;
            if (player.level().isClientSide()) return;
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
            for (Entity e : serverLevel.getEntities().getAll()) {
                if (e instanceof RediosEntity boss && boss.isAlive()) {
                    boss.onPlayerRemoteSlash(player);
                }
            }
        } catch (Throwable t) {
            // 单次事件异常忽略，避免监听器抛异常影响 slashblade 事件链。
        }
    }
}
