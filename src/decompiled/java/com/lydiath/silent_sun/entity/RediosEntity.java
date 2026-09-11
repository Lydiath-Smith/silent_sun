/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.entity;

import com.lydiath.silent_sun.SilentSunMod;
import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.data.RediosBattleData;
import com.lydiath.silent_sun.data.RediosCooldownData;
import com.lydiath.silent_sun.effect.EnrageEffect;
import com.lydiath.silent_sun.entity.AntiCheatLayer;
import com.lydiath.silent_sun.entity.BossFlag;
import com.lydiath.silent_sun.entity.BossState;
import com.lydiath.silent_sun.entity.BossTargeting;
import com.lydiath.silent_sun.entity.DamagePipeline;
import com.lydiath.silent_sun.entity.IntegrationContract;
import com.lydiath.silent_sun.entity.StarfallCurtainEntity;
import com.lydiath.silent_sun.entity.StarfallSalvoEntity;
import com.lydiath.silent_sun.entity.TitleDef;
import com.lydiath.silent_sun.entity.WeaponManager;
import com.lydiath.silent_sun.entity.pipeline.DamageContext;
import com.lydiath.silent_sun.event.CommonEvents;
import com.lydiath.silent_sun.integration.BladeAttackGoal;
import com.lydiath.silent_sun.loot.RediosLootConfig;
import com.lydiath.silent_sun.loot.RediosRewardOverrideConfig;
import com.lydiath.silent_sun.network.BlackSunDefeatPayload;
import com.lydiath.silent_sun.network.BlackSunRespawnPayload;
import com.lydiath.silent_sun.registry.ModDamageTypes;
import com.lydiath.silent_sun.registry.ModEffects;
import com.lydiath.silent_sun.registry.ModEntities;
import com.lydiath.silent_sun.registry.ModItems;
import com.lydiath.silent_sun.registry.ModSounds;
import com.lydiath.silent_sun.rules.RediosRules;
import com.lydiath.silent_sun.security.RuntimeInjectionGuard;
import com.lydiath.silent_sun.util.AbsoluteDamageUtil;
import com.lydiath.silent_sun.util.IAbsoluteDamageImmune;
import com.lydiath.silent_sun.util.BookTextCache;
import com.lydiath.silent_sun.util.ShulkerBoxUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DefaultAnimations;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class RediosEntity
extends Monster
implements GeoEntity, ITargetableHost, IAbsoluteDamageImmune {
    // 2026-09-11（代码审计 G13 #7 修复）：原 EXPEL_REPEL_RADIUS / EXPEL_REPEL_RADIUS_SQR /
    // INITIAL_PARTICIPANT_CAPTURE_TICKS / DARK_STAR_RADIUS 四个常量全库**零消费**
    //（消费点写的是同义字面量；且排斥半径口径现由 RediosRules.pushAwayRange() 承载）—— 已删除。
    private static final String OUTCOME_BOOK_TITLE = "\u7559\u8a00\u4e00\u5219";
    private static final String OUTCOME_BOOK_LEGACY_TITLE = "\u6210\u4e66";
    private static final String DROP_LIST_BOOK_TITLE = "\u5217\u8868";
    private static final String DROP_STATS_BOOK_TITLE = "\u7edf\u8ba1\u7269\u54c1";
    // 战斗区域统一 64 格（2026-09-08 用户裁决：整合包优化状况下够大）
    // 2.9「空无万象」专属的**即时逐出**半径（无 60 秒宽限，见 checkVoidBattleRange，只被 tickVoidAllThings 调用）。
    // 与通用脱战口径的关系（2026-09-10 明确，见 RediosRules.battleRadiusBlocks 注释）：
    //   · 通用脱战 = RediosRules.battleRadiusBlocks（默认 72）+ 60 秒宽限（tickBattleAreaCheck）；
    //   · 2.9 专属 = 本常量 64，超出即逐出、不给宽限 → 2.9 期间更严，两者刻意不同值。
    private static final int VOID_BATTLE_RANGE_BLOCKS = 64;
    private static final int VOID_BATTLE_RANGE_BLOCKS_SQR = 4096;
    /** Boss 数据版本：NBT 结构变更时 +1，用于 EntityJoinLevelEvent 剔除旧版本残留 Boss。 */
    private static final int BOSS_DATA_VERSION = 1;
    private static final long FAILSAFE_TICK_SPIKE_NANOS = 2000000000L;
    private static final int FAILSAFE_TICK_SPIKES_TO_TRIGGER = 2;
    private static final double FAILSAFE_HIGH_MEMORY_RATIO = 0.95;
    private static final int FAILSAFE_HIGH_MEMORY_TICKS = 40;
    private static final int FAILSAFE_DISCARD_DELAY_TICKS = 100;
    private static final int RIDE_PUNISH_COOLDOWN_TICKS = 40;
    private static final int WISH_REPAIR_INTERVAL_TICKS = 5;
    private static final int PHASE1_RESISTANCE_AMPLIFIER = 2;
    private static final int COLORLESS_RESISTANCE_AMPLIFIER = 3;
    private static final double BASE_MAX_HEALTH_FOR_REGEN_SCALE = 1000.0;
    private static final int DUSTLESS_GOOD_BUFF_COUNT = 4;
    private static final int DUSTLESS_GOOD_BUFF_AMPLIFIER = 2;
    private static final int DUSTLESS_GOOD_REFRESH_TICKS = 40;
    private static final int DUSTLESS_GOOD_HEAL_BOOST_TICKS = 600;
    private static final int FIRM_FAITH_PLAYER_RESIST_AMP = 2;
    private static final int FIRM_FAITH_PLAYER_RESIST_REFRESH_TICKS = 40;
    private static final int ENRAGE_INFINITE_DURATION_TICKS = 1000000000;
    private static final int UNITY_POWER_FRIENDLY_RADIUS = 20;
    private static final int UNITY_POWER_FRIENDLY_RADIUS_SQR = 400;
    private static final float UNITY_POWER_IMMUNE_CHANCE = 0.2f;
    private static final int UNITY_POWER_STRENGTH_REFRESH_TICKS = 40;
    private static final int UNITY_POWER_STRENGTH_MAX_LEVEL = 10;
    // 2026-09-11（代码审计 G13 #7 修复）：原 STAGE_DIG_RAY_STEPS / STAGE_DIG_INTERVAL_TICKS 在本类
    // 零消费，且与 WeaponManager 的同名常量重复定义（后者才是真实消费方）—— 已删除本类副本。
    // 2026-09-11（B-4）：原 STAGE_BLOCK_BOMB_RANGE = 24 死常量已删除（全库仅声明、0 消费）——
    // 投掷门限实际由 WeaponManager.tryThrowBlockBomb 的 getCurrentAttackReach() * 4 决定
    // （约 20~32 格，随激怒成长；变更出处见 docs/审计优化计划.md）。
    private static final int STAGE_BLOCK_BOMB_COOLDOWN_TICKS = 35;
    private static final float STAGE_BLOCK_BOMB_EXPLOSION_POWER = 2.5f;
    private static final List<Holder<MobEffect>> DUSTLESS_GOOD_BUFF_POOL = List.of(MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.JUMP, MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING, MobEffects.NIGHT_VISION, MobEffects.HEALTH_BOOST);
    static final List<Component> PHASE1_TITLES = List.of(Component.translatable("title.silent_sun.redios.phase1.0"), Component.translatable("title.silent_sun.redios.phase1.1"), Component.translatable("title.silent_sun.redios.phase1.2"), Component.translatable("title.silent_sun.redios.phase1.3"), Component.translatable("title.silent_sun.redios.phase1.4"), Component.translatable("title.silent_sun.redios.phase1.5"), Component.translatable("title.silent_sun.redios.phase1.6"), Component.translatable("title.silent_sun.redios.phase1.7"), Component.translatable("title.silent_sun.redios.phase1.8"), Component.translatable("title.silent_sun.redios.phase1.9"));
    static final List<Component> PHASE2_TITLES = List.of(Component.translatable("title.silent_sun.redios.phase2.0"), Component.translatable("title.silent_sun.redios.phase2.1"), Component.translatable("title.silent_sun.redios.phase2.2"), Component.translatable("title.silent_sun.redios.phase2.3"), Component.translatable("title.silent_sun.redios.phase2.4"), Component.translatable("title.silent_sun.redios.phase2.5"), Component.translatable("title.silent_sun.redios.phase2.6"), Component.translatable("title.silent_sun.redios.phase2.7"), Component.translatable("title.silent_sun.redios.phase2.8"), Component.translatable("title.silent_sun.redios.phase2.9"));
    static final TitleDef[] PHASE1_TITLE_DEFS = new TitleDef[]{TitleDef.of(new BossFlag[0]), TitleDef.of(new BossFlag[0]), TitleDef.of(BossFlag.WEAKNESS_CURSE, BossFlag.ENRAGE_STACKING), TitleDef.of(new BossFlag[0]), TitleDef.of(new BossFlag[0]), TitleDef.of(BossFlag.SOUL_SEVER_HARVEST), TitleDef.of(new BossFlag[0]), TitleDef.of(new BossFlag[0]), TitleDef.of(new BossFlag[0]), TitleDef.of(BossFlag.GUARD_BLOCK)};
    static final TitleDef[] PHASE2_TITLE_DEFS = new TitleDef[]{TitleDef.of(BossFlag.SEA_SKY_SOUL_SEVER), TitleDef.of(BossFlag.UNCONTROLLED_SPRINT), TitleDef.of(new BossFlag[0]), TitleDef.of(new BossFlag[0]), TitleDef.of(BossFlag.ASH_DAWN), TitleDef.of(new BossFlag[0]), TitleDef.of(new BossFlag[0]), TitleDef.of(BossFlag.BLACK_SUN), TitleDef.of(BossFlag.COLORLESS, BossFlag.ENRAGE_STACKING), TitleDef.of(new BossFlag[0])};
    private static final EntityDataAccessor<Integer> CLIENT_TRANSITION_TICKS = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_BOSS_STATE = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_TWILIGHT_ACTIVE = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_INTRO_ACTIVE = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_SUMMON_INTRO_TICKS = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final int INTRO_TOTAL_TICKS = 80;
    private static final int INTRO_STAR_COUNT = 6;
    private static final RawAnimation TRANSITION_ANIM = RawAnimation.begin().thenPlay("transition");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    int phase = 1;
    int titleIndex = 0;
    /**
     * 战斗开始游戏时间（灾变式动态减伤的时间基准；{@code -1} = 未开战）。
     * <p>
     * 2026-09-11（代码审计 G14 #3 修复）：原实现为 {@code int battleStartTick = tickCount}，而
     * {@code Entity.tickCount} **不落盘** → 区块回场/账本重建后新实例 {@code tickCount=0}、起点也归 0
     * → {@code elapsedSec=0} → 动态减伤回到初始 {@code dynamicReductionInitial}（默认 80%）并重新衰减，
     * 等于「每次重载白送最长 40 秒高额减伤」。
     * <p>
     * 注意：单纯给旧字段补一个 NBT 键**无效**——旧值是大数、新实例 {@code tickCount} 是 0，
     * 差值被 {@code Math.max(0.0f, …)} 钳到 0，结果与不落盘完全相同。故改用
     * {@code ServerLevel.getGameTime()} 作基准：同场次内两者每 tick 同步 +1，**差值逐位相同**，
     * 在线手感零变化；只有跨重载才体现为「精确还原」而非「计时归零」。
     */
    private long dynamicReductionStartGameTime = -1L;
    /** 头衔锁血剩余 tick；包可见供 DamagePipeline 判断「锁血中不推进非 x.9 头衔」（2026-09-01）。 */
    int titleLockTicks = 0;
    /** P2 濒死锁血已到期解除：到期后回到 PHASE2_COMBAT 允许击杀，不再锁血。 */
    boolean pendingLockReleased = false;
    /**
     * 头衔锁血结束后的「5 秒禁回血」缓冲（故意的削弱措施，防锁血刚结束就回血越过段顶跳段）。
     * <p>
     * 2026-09-11（代码审计 G13 #10 / G14 #7 修复）：原实现不落盘 → 回场/重建后归零，
     * 该缓冲静默失效，恰好放进它要防的场景（{@code checkHealTitleRegression} 头衔回退）。
     * 现持久化至 {@code SilentSunTitleLockGrace}。本字段与 {@code titleLockTicks} 同批搬运进回场快照
     * （{@code snapshotUnlockFlags} 走 {@code addAdditionalSaveData} 全量，无需额外接线）。
     */
    private int titleLockGraceTicks = 0;
    /** 投票/转阶段/濒死期间所有活跃参战玩家远离 Boss 的连续 tick 数（64 格外），用于区分「主动逃离脱战」与「区块短暂卸载」。 */
    private int disengageTicks = 0;
    private int aiWatchdogNoTargetTicks = 0;
    int transitionTicks = 0;
    /**
     * 转场总时长（tick，由 {@code startTransition} 按配置写入）。
     * <p>
     * 2026-09-11（代码审计 G13 #2 修复）：原实现不落盘，回场（{@code SilentSunTransition} 已持久化
     * 而本字段没有）后为 0，消费点却写 {@code Math.max(1, transitionTotalTicks)} → 总时长被抬成 1、
     * {@code elapsed = 1 - transitionTicks} 变负数 → 转场 Boss 栏被 {@code setVisible(false)} 隐藏、
     * 进度条钉满格、冲击特效门限（= -5）永不命中。现持久化至 {@code SilentSunTransitionTotal}，
     * 并由 {@link #transitionTotal()} 在缺键时回落配置值。
     */
    private int transitionTotalTicks = 0;
    private long soulSeverY = 0L;
    // 2026-09-10（批次 2.7）：原 wrongInterferenceActive / chaosRuinActive / ashDawnActive 三个字段已删除，
    // 改为由 phase/titleIndex 派生的方法 isWrongInterferenceActive() / isChaosRuinActive() / isAshDawnActive()。
    private boolean chaosRuinAbsoluteAttacks = false;
    private boolean ashDawnUnlocked = false;
    private boolean darkStarFired = false;
    private boolean darkStarFlightUnlocked = false;
    private boolean darkStarBedrockRepaired = false;
    private final Map<BlockPos, CompoundTag> darkStarRestoreBlocks = new HashMap<BlockPos, CompoundTag>();
    private BlockPos darkStarBlastOrigin = null;
    private int darkStarBlastNextIndex = 0;
    // G13#7（2026-09-11）：原 DARK_STAR_BLAST_PER_TICK = 600 零消费已删除（消费点写死同值字面量）。
    private boolean blackSunTriggered = false;
    private boolean colorlessUnlocked = false;
    private int colorlessChallengeTicks = -1;
    /** 2.9 空无万象永久解锁（2026-09-02）：进入 2.9 后传送攻击能力正推/逆推均保持，直至 Boss 死亡。 */
    private boolean voidAllThingsUnlocked = false;
    BossState bossState = BossState.PHASE1_COMBAT;
    final EnumSet<BossFlag> unlockedFlags = EnumSet.noneOf(BossFlag.class);
    boolean noResurrection = false;
    private boolean awaitingNoResurrectionPhase2 = false;
    private boolean blackSunUnlocked = false;
    private boolean weaknessCurseActive = false;
    private boolean enrageStackingUnlocked = false;
    private boolean attackRandomized = false;
    private boolean attackSpecialized = false;
    private Holder<DamageType> specializedDamageType = null;
    private final Map<ResourceLocation, Double> damageTypeTotals = new HashMap<ResourceLocation, Double>();
    /** 每玩家累计净伤害（仇恨值），用于繁星爆闪按仇恨分配星星。 */
    private final Map<UUID, Double> playerNetDamageTotals = new HashMap<UUID, Double>();
    // ── 威胁值索敌（设计 §582-583；设计注明「硬编码」，故不设配置键）──
    /** 目标切换阈值：新目标威胁 ≥ 当前目标 × 1.3 才切换。 */
    private static final double TARGET_SWITCH_THRESHOLD = 1.3;
    /** 目标切换冷却：切换后 40 tick（2 秒）内不再切换，防抖。 */
    private static final int TARGET_SWITCH_COOLDOWN_TICKS = 40;
    /** 威胁值衰减：每 20 tick（1 秒）全表 ×0.95（即 -5%）。 */
    private static final int THREAT_DECAY_INTERVAL_TICKS = 20;
    private static final double THREAT_DECAY_FACTOR = 0.95;
    /** 目标切换冷却剩余 tick（仅内存态，不入档）。 */
    private int targetSwitchCooldownTicks = 0;
    /** 每玩家最近一次范围性伤害的 tick，用于判定「持续范围轰炸」。仅内存态，不落地。 */
    private final Map<UUID, Integer> playerAreaDamageTick = new HashMap<UUID, Integer>();
    /** 范围轰炸窗口：该窗口内受过范围性伤害即视为「正在持续范围轰炸」。 */
    private static final int AREA_BOMBARDMENT_WINDOW_TICKS = 40;
    int weaponWeakpointSlowTicks = 0;
    private int weaponWeakpointCooldownTicks = 0;
    private int attackRecoveryTicks = 0;
    private boolean bossOutlineEnabled = false;
    private boolean seaSkySoulSeverUnlocked = false;
    private boolean soulSeverHarvestUnlocked = false;
    boolean uncontrolledSprintUnlocked = false;
    boolean guardUnlocked = false;
    private int multiPartTargetId = -1;
    private int multiPartAttackIndex = 0;
    private final Map<Integer, Float> multiPartLastDamage = new HashMap<>();
    private final Map<Integer, Integer> multiPartDropStreak = new HashMap<>();
    private static final int MULTI_PART_DROP_SWITCH_STREAK = 2;
    private static final float MULTI_PART_DAMAGE_THRESHOLD = 0.5f;
    private final Map<UUID, Integer> twilightFailureLastNotifyTick = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> twilightTimedMissingTicks = new HashMap<UUID, Integer>();
    private final Set<UUID> twilightTimedMissingFromApply = new HashSet<UUID>();
    private final Map<UUID, Integer> wallAttackLastNotifyTick = new HashMap<UUID, Integer>();
    private final Map<Class<?>, Map<String, Method>> reflectNoArgMethods = new HashMap();
    private final Map<Class<?>, Set<String>> reflectNoArgMissing = new HashMap();
    private final Map<Class<?>, Map<String, Field>> reflectFields = new HashMap();
    private final Map<Class<?>, Set<String>> reflectFieldMissing = new HashMap();
    private final Map<String, Integer> damageSourceDebugLastTick = new HashMap<String, Integer>();
    private final Map<String, Integer> damageZeroLogLastTick = new HashMap<String, Integer>();
    double dodgeChance = 0.0;
    double reflectRatio = 0.0;
    boolean reflectApplying = false;
    private int voidTeleportCooldown = 0;
    /** phase2.9 反应式避让传送标志：轨道 B 命中时置 true，tickVoidAllThings 优先消费。 */
    boolean voidDodgeTeleportPending = false;
    /** 反向激流冲刺（脱离玩家立体锁定球体）：剩余冲刺 tick 与冷却 tick。 */
    private int reverseDashTicks = 0;
    private int reverseDashCooldownTicks = 0;
    private double reverseDashDirX = 0.0;
    private double reverseDashDirZ = 0.0;
    private int enrageStackCooldownTicks = 0;
    private boolean allowSelfTeleport = false;
    private long lastServerTickNanos = -1L;
    private int tickSpikeCount = 0;
    private int highMemoryTicks = 0;
    private boolean failsafeActive = false;
    private int failsafeCountdownTicks = 0;
    final AntiCheatLayer anticheat = new AntiCheatLayer(this);
    final WeaponManager weapons = new WeaponManager(this);
    final CombatStatModulator stats = new CombatStatModulator(this);
    private int wishRepairTicker = 0;
    private int wishAbsorptionTicker = 0;
    private int healBoostTicks = 0;
    private final Map<UUID, List<Holder<MobEffect>>> dustlessGoodBuffs = new HashMap<UUID, List<Holder<MobEffect>>>();
    private int rootlessPureTickCounter = 0;
    private long mirrorFaceLockedSoulSever = 0L;
    private int whoseWishTicker = 0;
    private int allParticipantsDeadTicks = 0;
    private int battleAreaUnloadedTicks = 0;
    private final Map<UUID, Integer> locateBossFarTicks = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> bossNotInViewTicks = new HashMap<UUID, Integer>();
    // G13#7（2026-09-11）：原 MISSING_VIEW_NOTIFY_COOLDOWN_TICKS = 600 零消费已删除
    //（「不在视野」提醒的限频实际写死 600，与 RediosRules.locateBossNotifyIntervalTicks 是两条口径 —— 见 G14 #9 登记项）。
    private final Map<UUID, Integer> lastMissingViewNotifyTick = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> outOfAreaTicks = new HashMap<UUID, Integer>();
    private boolean heightFlightMode = false;
    boolean phaseMaxHealthApplied = false;
    private int battleMusicPhase = 0;
    /** A-1：当前段（0=none 1=intro 2=loop 3=outro）。 */
    private int battleMusicSegment = MUSIC_SEG_NONE;
    /**
     * A-1：下发世代号——每次需要让玩家**重新收到**音效时递增（换相位 / 换段 / loop 重发）。
     * 不能用「相位×10+段」当标识：loop 段重发时段标识不变，会被"未变则跳过"吃掉。
     */
    private int battleMusicStamp = 0;
    /** A-1：当前段结束时刻（tickCount 基准）；intro→loop 切换与 loop 重发均以此判定。 */
    private long battleMusicSegmentEndTick = 0L;
    /** A-1：本场是否已下发 outro —— 结算时据此决定是否仍 stop 掉 MUSIC 源（让 outro 播完）。 */
    private boolean battleMusicOutroSent = false;
    private static final int MUSIC_SEG_NONE = 0;
    private static final int MUSIC_SEG_INTRO = 1;
    private static final int MUSIC_SEG_LOOP = 2;
    private static final int MUSIC_SEG_OUTRO = 3;
    private final Map<UUID, Integer> battleMusicPlaying = new HashMap<UUID, Integer>();
    // 2026-09-11（A-1）：音乐可听半径不再硬编码（原 MUSIC_FADE_OUT_DIST_SQR = 4096.0 即 64 格，
    // 与设计稿 §十 A3 的 72 格参战区倒挂 → 64~72 格仍在战斗却听不到音乐），改读
    // RediosRules.battleRadiusBlocks()。注意 sounds.json 的 attenuation_distance 是静态字段，
    // 无法读配置，须手工同步为同一值（当前 72）。
    // G13#7（2026-09-11）：原 STARFALL_SALVO_SETTLE_TIMEOUT_TICKS = 160 零消费已删除
    //（该超时已被 maxDelay + 20 取代，见 tickStarfallSalvoDetonation 处的注释）。
    // 2026-09-11（代码审计 G13 #7 修复）：原 STARFALL_SALVO_FALL_FROM_BLOCKS(30) /
    // STARFALL_SALVO_HOVER_BLOCKS(2) 零消费 —— 星星生成循环各处写死 `getY() + 30` / `getY() + 2`
    //（即 G13 #9 登记的「三份重复实现」）—— 已删除。
    /** 集中轰炸目标的星星散布半径（设计稿 §7.3：集中轰炸保持 5.0，非集中才用配置的散射半径）。 */
    private static final double STARFALL_SALVO_CONCENTRATED_RADIUS = 5.0;
    /**
     * 星爆齐射冷却（tick）。初值 {@code -1} = 未初始化，由 {@code finalizeSpawn} 按配置
     * {@code redios.starfallSalvoIntervalTicks} 赋值（2026-09-11 代码审计 G13 #11 修复：
     * 原初值写死 900，整合包把该配置调小后，**新召唤 Boss 的首发大招仍固定等 45 秒**）。
     * 读档路径见 {@code readAdditionalSaveData} 里同配置的兜底。
     */
    private int starfallSalvoCooldownTicks = -1;
    private boolean starfallSalvoPending = false;
    private final Set<UUID> starfallSalvoStars = new HashSet<UUID>();
    private final Set<UUID> introStarfallStars = new HashSet<UUID>();
    private boolean introStarfallDetonated = false;
    private int starfallSalvoSettleTimeoutTicks = 0;
    private boolean starfallSalvoReadyToDetonate = false;
    private int starfallSalvoDetonateDelayTicks = 0;
    private int phase2ChoiceTimeoutTicks = 0;
    private final Map<UUID, Boolean> phase2Choices = new HashMap<UUID, Boolean>();
    final Set<UUID> battleParticipants = new HashSet<UUID>();
    final Set<UUID> expelledPlayers = new HashSet<UUID>();
    private boolean allExpelledLeavePending = false;
    /** 管理员清理命令标记：tick 时若为 true 立即无掉落退场（区块静止的 Boss 解冻恢复 tick 后自动生效）。 */
    private boolean pendingCommandLeave = false;
    private int bossDataVersion = BOSS_DATA_VERSION;
    private boolean settlementDone = false;
    final Set<UUID> hardcoreProtectedPlayers = new HashSet<UUID>();
    final Set<UUID> twilightExpelled = new HashSet<UUID>();
    private long battleStartGameTime = -1L;
    private final Set<UUID> initialParticipants = new HashSet<UUID>();
    final Set<UUID> mobParticipants = new HashSet<UUID>();
    private boolean mobBattleEngaged = false;
    // 2026-09-11（代码审计 G13 #7 修复）：原 TITLE_WHOSE_WISH(7) / TITLE_SHARPEN_TRIAL(8) /
    // TITLE_DIVIDE_LIGHT(5) / TITLE_BLACK_SUN(7) 四个头衔索引常量全库**零消费** ——
    // 实际到处用裸索引比较（如 `phase == 1 && titleIndex == 7`），段位调整时最易漏改；
    // 头衔语义真值现在 PHASE1_TITLE_DEFS / PHASE2_TITLE_DEFS —— 已删除。
    private boolean legitRemoval = false;
    private boolean inHurtProcessing = false;
    /** 入场演出剩余 tick（0 表示无演出）：演出期 Boss 冻结、无敌、不索敌。 */
    private int introTicks = 0;
    /** 召唤演出剩余 tick（2026-09-04）：裂解之痛召唤时播放切阶段立方体动画，烟圈收缩帧散射繁星爆闪；0=无。 */
    private int summonIntroTicks = 0;
    /** 召唤演出已释放散射爆闪标记（防收缩帧多 tick 重复触发）。 */
    private boolean summonScatterFired = false;
    /** 重建自战斗账本记录的标记：仅作语义区分，不影响结算 CD（照常设 CD）。 */
    private boolean rebuiltAsSettled = false;
    /** 2026-09-10（用户裁决 D5）：强制本次结算发放「一阶段奖励」。
     *  用于 2.5 断光之刻全体被传送导致战斗终止的场景——设计 §2.5 要求发一阶段奖励，
     *  而该路径处于 phase==2，settleBattle 默认会走 dropPhase2Reward。 */
    private boolean forcePhase1Reward = false;
    /** 2026-09-10（用户裁决 C3 / Q13）：一阶段断魂已被 1.7 清除 → 一阶段剩余头衔（1.8/1.9）不再重挂。
     *  <p>
     *  1.7「愿予必成」每秒 {@code restorePlayerToFull} 会清掉玩家全部负面效果（含断魂），作者裁定
     *  该清除**保持到一阶段结束**，因此 1.8「磨锐试炼」的每 20 tick 重挂与入场挂都必须让路。
     *  进入二阶段时复位（二阶段断魂由 2.0「海天之隙」独立授予）。 */
    private boolean soulSeverRetiredInPhase1 = false;
    private LeaveReason leaveReason = LeaveReason.NONE;
    private BlockPos battleAnchorPos = null;
    private ResourceLocation battleAnchorDim = null;
    // 2026-09-11（代码审计 G13 #7 修复）：原 ANTI_EXILE_RANGE(256.0) / ANTI_EXILE_VOID_MARGIN(8.0)
    // 零消费 —— tickAntiExile 里写死 65536.0（= 256²）与 8.0 —— 已删除。
    private int deathViaHurtTick = -1;
    private int removalPunishCooldownTicks = 0;
    private static final SoundEvent[] DARKNESS_AMBIENT_SOUNDS = new SoundEvent[]{SoundEvents.WARDEN_HEARTBEAT, SoundEvents.WARDEN_LISTENING, SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_ANGRY};
    private int darknessSoundCooldown = 0;
    private static final ResourceLocation MAX_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_max_health_override");
    // G13#7（2026-09-11）：原 ATTRIBUTE_MAX_HEALTH_CAP = 1024.0 零消费已删除
    //（属性上限的实际处理点写死 1024.0，见 applyPhaseMaxHealth 的钳制告警）。
    private static boolean warnedMaxHealthClamped = false;

    public RediosEntity(EntityType<? extends RediosEntity> type, Level level) {
        super(type, level);
        this.bossEvent.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 2000.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 30.0).add(Attributes.ATTACK_SPEED, 4.0).add(Attributes.ARMOR, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        // 2026-09-11（代码审计 G13 #8 修复）：CLIENT_PHASE / CLIENT_TITLE_INDEX /
        // CLIENT_SOUL_SEVER_Y / CLIENT_TITLE_LOCK_TICKS 四个同步位原先**只有写入、没有任何读取方**
        // （唯一读取者是它们各自的 getClient*，而那 4 个 getter 全库零调用）⇒ 每 tick 白付同步开销。
        // 已连同 accessor 定义、getter 与 syncClientRenderData 里的 set 一并删除。
        builder.define(CLIENT_TRANSITION_TICKS, 0);
        builder.define(CLIENT_BOSS_STATE, BossState.PHASE1_COMBAT.ordinal());
        builder.define(CLIENT_TWILIGHT_ACTIVE, 0);
        builder.define(CLIENT_INTRO_ACTIVE, 0);
        builder.define(CLIENT_SUMMON_INTRO_TICKS, 0);
    }

    public int getClientSummonIntroTicks() {
        return this.entityData.get(CLIENT_SUMMON_INTRO_TICKS);
    }

    public int getClientTransitionTicks() {
        return this.entityData.get(CLIENT_TRANSITION_TICKS);
    }

    public BossState getClientBossState() {
        int ord = this.entityData.get(CLIENT_BOSS_STATE);
        BossState[] values = BossState.values();
        return ord >= 0 && ord < values.length ? values[ord] : BossState.PHASE1_COMBAT;
    }

    public boolean isClientTwilightActive() {
        return this.entityData.get(CLIENT_TWILIGHT_ACTIVE) != 0;
    }

    public boolean isClientIntroActive() {
        return this.entityData.get(CLIENT_INTRO_ACTIVE) != 0;
    }

    private void syncClientRenderData() {
        if (this.level().isClientSide) {
            return;
        }
        this.entityData.set(CLIENT_TRANSITION_TICKS, this.transitionTicks);
        this.entityData.set(CLIENT_BOSS_STATE, this.bossState.ordinal());
        this.entityData.set(CLIENT_TWILIGHT_ACTIVE, this.isTwilightMomentActive() ? 1 : 0);
        this.entityData.set(CLIENT_INTRO_ACTIVE, this.introTicks > 0 ? 1 : 0);
        this.entityData.set(CLIENT_SUMMON_INTRO_TICKS, this.summonIntroTicks);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RediosRiptideDashGoal(this));
        this.goalSelector.addGoal(2, new RediosMeleeAttackGoal(this));
        if (IntegrationContract.isSlashBladeIntegrationAvailable()) {
            this.goalSelector.addGoal(3, new BladeAttackGoal(this));
        }
        this.goalSelector.addGoal(4, new RediosWallAttackGoal(this));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]){

            public boolean canUse() {
                OwnableEntity ownable;
                UUID ownerId;
                if (!super.canUse()) {
                    return false;
                }
                LivingEntity attacker = RediosEntity.this.getLastHurtByMob();
                if (attacker == null) {
                    return false;
                }
                if (!BossTargeting.isValidDamageAttacker(RediosEntity.this, attacker)) {
                    return false;
                }
                return !BossTargeting.playerOnlyMode() || !(attacker instanceof OwnableEntity) || (ownerId = (ownable = (OwnableEntity)attacker).getOwnerUUID()) != null && RediosEntity.this.battleParticipants.contains(ownerId);
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, e -> {
            ServerPlayer sp;
            return e instanceof ServerPlayer && !(sp = (ServerPlayer)e).isCreative() && !sp.isSpectator() && !this.expelledPlayers.contains(sp.getUUID());
        }){

            public boolean canUse() {
                return RediosEntity.this.isTargetingAllowed() && BossTargeting.playerOnlyMode() && super.canUse();
            }

            public boolean canContinueToUse() {
                return RediosEntity.this.isTargetingAllowed() && BossTargeting.playerOnlyMode() && super.canContinueToUse();
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, LivingEntity.class, 10, true, false, e -> BossTargeting.isValidAttackTarget(this, (LivingEntity)e)){

            public boolean canUse() {
                return RediosEntity.this.isTargetingAllowed() && !BossTargeting.playerOnlyMode() && super.canUse();
            }

            public boolean canContinueToUse() {
                return RediosEntity.this.isTargetingAllowed() && !BossTargeting.playerOnlyMode() && super.canContinueToUse();
            }
        });
    }

    /**
     * 主动索敌是否允许（2026-09-09 用户裁决：与 titleLock 解耦——titleLock 只承担"锁血/防跳段"，
     * 不再连带关闭就近索敌，消除"更换头衔后 Boss 站桩 15~30s"的索敌停顿）：
     * 仅投票/转阶段等冻结窗口关闭；HurtByTarget 复仇仇恨本就不受此门控。
     */
    private boolean isTargetingAllowed() {
        return !this.bossState.isVoteOrTransition();
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)this.level();
        // 结算保底（2026-09-02，最后的补救手段，希望尽量用不到）：结算已标记
        //（settlementDone=true，掉落实已发放）但实体仍未移除（结算链路异常中断/竞态残留）→
        // 立即合法离场。正常结算 safeDiscard 与 settlementDone 同 tick 完成，本分支不触发；
        // 触发即代表"结算信息已发但 Boss 赖着"——最后手段兜底移除（legitRemoval + discard）。
        if (this.settlementDone && !this.isRemoved()) {
            this.safeDiscard();
            return;
        }
        if (this.tickFailsafe(serverLevel)) {
            return;
        }
        // 首次进入战斗记录战斗开始游戏时间（灾变式动态减伤的时间基准，见 dynamicReductionStartGameTime 注释）
        if (this.dynamicReductionStartGameTime < 0L && this.bossState.isCombat()) {
            this.dynamicReductionStartGameTime = serverLevel.getGameTime();
        }
        if (this.allExpelledLeavePending) {
            this.allExpelledLeavePending = false;
            this.bossLeaveNoLoot();
            return;
        }
        // 管理员清理命令（/silent_sun redios reset_summon_cd 扩展）：标记后下一 tick 无掉落退场
        if (this.pendingCommandLeave) {
            this.pendingCommandLeave = false;
            this.bossLeaveNoLoot();
            return;
        }
        if (this.anticheat.tickCreativeRelated(serverLevel)) {
            MutableComponent leaveMsg = Component.translatable("message.silent_sun.redios.creative_leave").withStyle(ChatFormatting.GOLD);
            for (UUID id2 : new HashSet<UUID>(this.anticheat.creativeStrikers)) {
                ServerPlayer sp;
                ServerPlayer cp = this.getServerPlayer(id2);
                if (cp == null || (sp = cp).isCreative() || !sp.isAlive()) continue;
                this.anticheat.creativeStrikers.remove(id2);
            }
            // 2026-09-11 用户裁决（§2.4 口径，选项 A）：创造离场**不发惩罚**——
            //   已清一阶段 → 正常发奖励 + 冷却 0；未清一阶段 → 无奖励（没打到就是没打到）+ 冷却 0。
            // 原实现走 bossLeaveFriendly（= leaveBattle(..., setCooldown=true)）→ 无掉落 + 3 天冷却，
            // 与设计 §2.4（0 冷却 + 已清 P1 发奖励）和 §3.5（一小时）三处矛盾。
            if (this.hasClearedPhase1ForLoot()) {
                this.settleBattle(serverLevel, 0L, true, this.phase == 2);
            } else {
                this.leaveBattle(serverLevel, leaveMsg, false);
            }
            return;
        }
        if (this.colorlessChallengeTicks > 0) {
            // 2026-09-10（用户裁决）：计时只在「真正能打」的时间累加——**冻结态不倒计时**。
            //   ・冻结态 = bossState.isFrozen()：PHASE1_VOTE / PHASE1_TRANSITION / PHASE1_PENDING / PHASE2_PENDING
            //     （投票 / 转场 / 两阶段濒死，玩家无法推进战斗进度）；
            //   ・锁血期 = titleLockTicks > 0：段底不可越，玩家打不穿 → 同样不计入可打时间。
            // 起算点仍为「进入 2.8 那一刻」（设计稿 §2.8 原义），回退后重进 2.8 **不重置**（保持现状）。
            if (!this.bossState.isFrozen() && this.titleLockTicks <= 0) {
                --this.colorlessChallengeTicks;
                if (this.colorlessChallengeTicks <= 0) {
                    this.resolveColorlessChallengeSuccess(serverLevel);
                    return;
                }
            }
        }
        if (!this.phaseMaxHealthApplied) {
            this.applyPhaseMaxHealth(serverLevel);
        }
        if (this.removalPunishCooldownTicks > 0) {
            --this.removalPunishCooldownTicks;
        }
        if (this.reverseDashTicks > 0) {
            --this.reverseDashTicks;
            this.getNavigation().stop();
            this.setDeltaMovement(this.reverseDashDirX * 1.9, this.getDeltaMovement().y, this.reverseDashDirZ * 1.9);
        } else if (this.reverseDashCooldownTicks > 0) {
            --this.reverseDashCooldownTicks;
        }
        IntegrationContract.registerDoSlashListener();
        if (this.tickCount % 200 == 0) {
            RuntimeInjectionGuard.scanIfNeeded(RediosEntity.class);
        }
        this.syncClientRenderData();
        // 召唤演出（2026-09-04）：裂解之痛召唤时播放切阶段立方体动画，烟圈收缩帧释放散射繁星爆闪。
        // 递减与散射触发独立于 intro（intro 冻结 Boss 响指演出并行）；引爆交给战斗 tick 的 tickStarfallSalvo。
        if (this.summonIntroTicks > 0) {
            this.tickSummonCinematic(serverLevel);
        }
        if (this.introTicks > 0) {
            this.tickIntro(serverLevel);
            return;
        }
        this.weapons.tick();
        if (this.titleLockTicks <= 0 && this.tickCount == 1) {
            this.titleLockTicks = this.titleLockDurationTicks();
        }
        this.repelExpelledPlayers();
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && !BossTargeting.isValidAttackTarget(this, currentTarget)) {
            this.setTarget(null);
            currentTarget = null;
        }
        if (!BossTargeting.playerOnlyMode() && currentTarget != null && !(currentTarget instanceof Player)) {
            this.registerMobParticipant(currentTarget);
        }
        this.cleanupMobParticipants();
        this.tickAiWatchdog(serverLevel);
        this.tickLowFpsTargetAdjustment(serverLevel);
        this.anticheat.tickRidePunish(serverLevel);
        this.updateBattleRecord(serverLevel);
        this.tickAntiExile(serverLevel);
        this.tickBattleMusic(serverLevel);
        this.tickChunkRetention(serverLevel);
        if (this.isRemoved()) {
            return;
        }
        this.tickDarknessAmbientSounds(serverLevel);
        this.tickLocateBoss(serverLevel);
        this.tickBossMissingInView(serverLevel);
        this.tickBattleAreaCheck(serverLevel);
        this.tickHeightFlight(serverLevel);
        // 2026-09-10（实测崩服修复）：拔刀剑崩溃防护**不随刀窗口开关**——它是防崩服护栏，不是攻击机制。
        // 原先它挂在 `if (isBladeAttackAllowed())` 内，而该方法在 1.7「谁人之愿」/ PHASE1_VOTE / PHASE1_TRANSITION
        // 期间为 false → 这些窗口里只剩 CommonEvents 的 100 tick 全局兜底（最长 5 秒空窗）。
        // 实测崩溃（EntityDrive.onHitEntity:303 nextInt(负)）即落在该空窗内：Boss 与崩溃剑气相距仅约 20 格，
        // 本应被每 tick 的 64 格扫描覆盖。移动到此处的代价只是一次实体 AABB 查询，远低于崩服代价。
        IntegrationContract.sanitizeBossBladeEntities(this);
        if (this.isBladeAttackAllowed() && this.isBladeModeActive()) {
            IntegrationContract.tryTickBossBladePlayerHits(this);
            IntegrationContract.tryFireBossPhantomSwords(this);
        }
        this.hardcoreProtectedPlayers.removeIf(id -> {
            ServerPlayer p = this.getServerPlayer((UUID)id);
            return p == null || !p.isAlive() || p.level() != this.level();
        });
        // 非法坐标兜底（2026-09-10 实测：寰宇支配之剑把 Boss 甩到 999999,999999,999999）：
        // 外部把位置写成世界边界之外的荒谬值时直接拉回战斗锚点，避免 Boss 被"流放"后触发
        // 脱战/区域卸载等误判，随后还会被判 externally removed（回场 + 流程空窗）。
        if (Math.abs(this.getX()) > 3.0E7 || Math.abs(this.getZ()) > 3.0E7 || Math.abs(this.getY()) > 2.0E4) {
            // 2026-09-11（代码审计 P0 修复）：battleAnchorPos 可能为 null——:415 初始化为 null，
            // :4289-4292 在存档缺 SilentSunAnchorX 时又会置回 null。原实现直接解引用会在实体
            // tick 抛 NPE 崩服（同文件 :5416/:5478 的同类用法都有判空，唯独这里漏了）。
            BlockPos recover = this.battleAnchorPos != null ? this.battleAnchorPos : serverLevel.getSharedSpawnPos();
            SilentSunMod.LOGGER.warn("[Redios] 非法坐标拦截：位置=({}, {}, {}) → 拉回{} ({}, {}, {})",
                (int)this.getX(), (int)this.getY(), (int)this.getZ(),
                this.battleAnchorPos != null ? "战斗锚点" : "世界出生点（锚点为空）",
                recover.getX(), recover.getY(), recover.getZ());
            this.moveTo((double)recover.getX() + 0.5, (double)recover.getY(),
                (double)recover.getZ() + 0.5, this.getYRot(), this.getXRot());
            this.setDeltaMovement(0.0, 0.0, 0.0);
        }
        // 防死兜底（2026-09-10 实测修复）：防死窗口内若有任何路径把血量写到 <1（前置模组断魂
        // 在 LivingDamageEvent.Post 里直写血量数据、绕过 setHealth），每 tick 钳回 1 血并同步
        // 反作弊基线——既保住 isDeadOrDying() 拦不住的"血量本身合法性"，也避免把玩家自己模组的
        // 合法机制（断魂 DoT）误判成作弊惩罚。放在 anticheat.tick 之前，保证基线一致。
        if (this.isProtectedFromDeath() && this.getHealth() < 1.0f) {
            this.forceSetHealth(1.0f);
            this.anticheat.markLegalHealthChange(1.0f);
        }
        this.anticheat.tick(serverLevel);
        if (this.deathTime > 0 && !this.legitRemoval && !this.isLegitDeathFlow()) {
            this.deathTime = 0;
            this.forceSetHealth(Math.max(1.0f, this.getHealth()));
            if (this.tickCount % 200 == 0) {
                MutableComponent funny = Component.translatable("message.silent_sun.redios.anticheat.death_animation_interrupted").withStyle(ChatFormatting.DARK_RED);
                this.broadcastToParticipants(this.rediosSigned(funny));
            }
        }
        if (this.bossState == BossState.PHASE1_VOTE) {
            if (this.checkAllParticipantsDisengaged(serverLevel)) {
                return;
            }
            if (this.checkBattleAreaUnloaded(serverLevel)) {
                return;
            }
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            this.setPose(Pose.SITTING);
            --this.phase2ChoiceTimeoutTicks;
            if (this.phase2ChoiceTimeoutTicks <= 0) {
                this.finishPhase2Choice();
            }
            return;
        }
        if (this.bossState == BossState.PHASE1_PENDING || this.bossState == BossState.PHASE2_PENDING) {
            // 判定秩序化（A4）：三守卫统一顺序 = 全灭 → 脱战 → 卸载（与 COMBAT 主流程一致）。
            // 全灭判定对 VOTE/转场自行豁免（checkDefeatByAllDead 的 isVoteOrTransition 守卫）。
            if (this.checkDefeatByAllDead(serverLevel)) {
                return;
            }
            if (this.checkAllParticipantsDisengaged(serverLevel)) {
                return;
            }
            if (this.checkBattleAreaUnloaded(serverLevel)) {
                return;
            }
            // 2026-09-04：pending 不代表 Boss 被眩晕（设计遗留问题修复）——移除
            // navigation.stop / setDeltaMovement(0) / SITTING 冻结：Boss 正常站立、移动、
            // 切武器（weapons.tick 在前已跑）、近战攻击（goalSelector 驱动）。仅保留锁血计时 +
            // 自我恢复 + 到期收口。
            this.setPose(Pose.STANDING);
            if (this.titleLockTicks > 0) {
                --this.titleLockTicks;
            }
            this.tickNaturalRegen();
            if (this.titleLockTicks <= 0) {
                this.onPendingLockExpired();
            }
            return;
        }
        if (this.weaponWeakpointSlowTicks > 0) {
            --this.weaponWeakpointSlowTicks;
        }
        if (this.weaponWeakpointCooldownTicks > 0) {
            --this.weaponWeakpointCooldownTicks;
        }
        if (this.attackRecoveryTicks > 0) {
            --this.attackRecoveryTicks;
        }
        if (this.getPose() == Pose.SITTING) {
            this.setPose(Pose.STANDING);
        }
        // 威胁值衰减 / 目标切换冷却（独立于头衔逻辑，冻结态也照常计时）
        this.tickThreatSystem();
        if (this.bossState == BossState.PHASE1_TRANSITION) {
            --this.transitionTicks;
            // 2026-09-11 实测修复（S1）：判等改 `<= 0`。原为 `== 0` 精确判等，而回场重建时
            // SilentSunTransition 不在账本键清单里 → 读回 0 → 自减成 -1 → 永不命中 →
            // **Boss 永久停在转场态**（无敌 + die 被防死拦截 + failsafe 安全窗口豁免），无奖励卡死。
            // 配置 phaseTransitionSeconds = 0 时会踩同一条。
            if (this.transitionTicks <= 0) {
                this.enterPhase2Combat();
            } else if (this.transitionTicks == this.transitionTotal() - 6) {
                this.spawnTransitionImpact(serverLevel);
            }
        } else {
            this.updateTitle();
            this.weapons.tickStageAbilities(serverLevel);
            this.tickSeaSkyGap(serverLevel);
            this.tickUncontrolledSprint(serverLevel);
            this.tickWishGrant(serverLevel);
            this.tickDustlessGood(serverLevel);
            this.tickFirmFaith(serverLevel);
            this.tickUnityPower(serverLevel);
            this.tickSorrowToilAura(serverLevel);
            this.tickChaosRuinAura(serverLevel);
            this.tickRootlessPure(serverLevel);
            this.tickMirrorFace(serverLevel);
            this.tickWhoseWish(serverLevel);
            this.tickSharpenTrial();
            this.tickTwilightMoment();
            this.tickAshDawn();
            this.tickDarkStar();
            this.tickBlackSun();
            this.tickVoidAllThings();
            this.tickColorlessSuper(serverLevel);
            this.tickEnrage();
            this.tickEnrageStacking();
            this.tickWeaknessCurse();
            this.tickFragileBinding();
            this.tickStarfallSalvo(serverLevel);
        }
        if (this.tickCount % 4 == 0) {
            double armor = this.bossState.isPhase2() ? SilentSunConfig.PHASE2_ARMOR_VALUE.get() : SilentSunConfig.PHASE1_ARMOR_VALUE.get();
            if (this.isWeaponWeakpointWindowActive()) {
                armor *= 1.0 - RediosRules.weaponWeakpointArmorPierce();
            }
            this.getAttribute(Attributes.ARMOR).setBaseValue(armor);
        }
        if (this.titleLockGraceTicks > 0) {
            --this.titleLockGraceTicks;
        }
        this.tickNaturalRegen();
        if (this.checkDefeatByAllDead(serverLevel)) {
            return;
        }
        if (this.checkAllParticipantsDisengaged(serverLevel)) {
            return;
        }
        if (this.checkBattleAreaUnloaded(serverLevel)) {
            return;
        }
        this.updateBossEvent();
        this.anticheat.markLegalHealthChange(this.getHealth());
    }

    /** 入场演出：首拍播响指 + 生成无伤害星星，随后冻结 Boss，倒计时结束后进入正式战斗。 */
    private void tickIntro(ServerLevel serverLevel) {
        if (this.introTicks == INTRO_TOTAL_TICKS) {
            this.startIntro(serverLevel);
        }
        this.getNavigation().stop();
        this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
        this.setPose(Pose.SITTING);
        --this.introTicks;
        this.tickIntroStarfallDetonation(serverLevel);
        if (this.introTicks <= 0) {
            this.setPose(Pose.STANDING);
        }
    }

    /** 召唤演出开始（2026-09-04）：播放切阶段立方体动画 + 烟圈收缩帧散射繁星爆闪。不冻结 Boss。 */
    public void beginSummonCinematic() {
        this.summonIntroTicks = Math.max(1, SilentSunConfig.PHASE_TRANSITION_SECONDS.get() * 20);
        this.summonScatterFired = false;
    }

    /** 召唤演出每 tick：递减；烟圈收缩帧释放一次纯视觉散射繁星爆闪；引爆纯视觉星星（幂等）。 */
    private void tickSummonCinematic(ServerLevel serverLevel) {
        int total = Math.max(1, SilentSunConfig.PHASE_TRANSITION_SECONDS.get() * 20);
        int elapsed = total - this.summonIntroTicks;
        // 烟圈收缩帧：立方体 fieldT=0.5 → elapsed = impactTick(6) + (total-6)/2
        int contractTick = 6 + (total - 6) / 2;
        if (!this.summonScatterFired && elapsed >= contractTick) {
            this.summonScatterFired = true;
            this.spawnSummonScatterStars(serverLevel);
        }
        this.tickIntroStarfallDetonation(serverLevel);
        --this.summonIntroTicks;
    }

    /** 召唤散射繁星爆闪（纯视觉、无伤害、不破坏方块）：散射 20 格，复用 intro 星星引爆链（explode NONE）。 */
    private void spawnSummonScatterStars(ServerLevel serverLevel) {
        this.introStarfallStars.clear();
        this.introStarfallDetonated = false;
        for (int i = 0; i < INTRO_STAR_COUNT; ++i) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = 20.0 * (0.25 + 0.75 * Math.sqrt(this.random.nextDouble()));
            double x = this.getX() + Math.cos(angle) * dist;
            double z = this.getZ() + Math.sin(angle) * dist;
            double hoverY = this.getY() + 2.0;
            double spawnY = this.getY() + 30.0;
            int delay = this.random.nextInt(20);
            StarfallSalvoEntity star = ModEntities.STARFALL_SALVO.get().create(serverLevel);
            if (star == null) continue;
            star.initSalvo(this.getUUID(), hoverY, delay, null, 0.0, 0.0);
            star.markDisplayExempt();
            star.setPos(x, spawnY, z);
            serverLevel.addFreshEntity(star);
            this.introStarfallStars.add(star.getUUID());
        }
    }

    /** 入场演出首拍：响指音效 + 纯演出繁星爆闪（下落悬停、不引爆、无伤害）。 */
    private void startIntro(ServerLevel serverLevel) {
        serverLevel.playSound(null, this.blockPosition(), (SoundEvent)ModSounds.STARFALL_SALVO_PRE_EXPLOSION.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
        this.spawnIntroStars(serverLevel);
    }

    /** 生成入场版无伤害星星：只做下落→悬停演出，不加入引爆集合，寿命到期自行消失。 */
    private void spawnIntroStars(ServerLevel serverLevel) {
        for (int i = 0; i < INTRO_STAR_COUNT; ++i) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * 5.0;
            double x = this.getX() + Math.cos(angle) * dist;
            double z = this.getZ() + Math.sin(angle) * dist;
            double hoverY = this.getY() + 2.0;
            double spawnY = this.getY() + 30.0;
            int delay = this.random.nextInt(20);
            StarfallSalvoEntity star = ModEntities.STARFALL_SALVO.get().create(serverLevel);
            if (star == null) continue;
            star.initSalvo(this.getUUID(), hoverY, delay, null, 0.0, 0.0);
            star.markDisplayExempt();
            star.setPos(x, spawnY, z);
            serverLevel.addFreshEntity(star);
            this.introStarfallStars.add(star.getUUID());
        }
    }

    /** 入场演出：待入场星全部悬停后引爆（无伤害、不破坏方块，仅爆炸特效）。 */
    private void tickIntroStarfallDetonation(ServerLevel serverLevel) {
        if (this.introStarfallStars.isEmpty() || this.introStarfallDetonated) {
            return;
        }
        for (UUID uuid : this.introStarfallStars) {
            Entity star = serverLevel.getEntity(uuid);
            if (!(star instanceof StarfallSalvoEntity salvo) || !salvo.isSettled()) {
                return;
            }
        }
        this.introStarfallDetonated = true;
        // 2026-09-11（代码审计 G13 #3 修复）：演出路径必须用 **0.0f** 威力。
        // 本方法 javadoc 自称「无伤害、不破坏方块，仅爆炸特效」，但原实现传的是
        // STARFALL_SALVO_EXPLOSION_POWER（默认 6.0）—— 而 Level.explode **任何** interaction
        // 都会对实体造成爆炸伤害与击退（同文件 spawnTransitionImpact 的注释已明确这一点，
        // 并因此用 0.0f 做「真·无害爆炸」）。演出星星就悬在 Boss 身边（hoverY = getY() + 2），
        // 即玩家召唤时站立处 ⇒ 残血玩家可能在「无害演出」里被炸死。
        for (UUID uuid : this.introStarfallStars) {
            Entity star = serverLevel.getEntity(uuid);
            if (star == null || star.isRemoved()) continue;
            serverLevel.explode(this, star.getX(), star.getY(), star.getZ(), 0.0f, Level.ExplosionInteraction.NONE);
            if (star instanceof StarfallSalvoEntity salvo) salvo.markLegitRemoval();
            star.discard();
        }
        this.introStarfallStars.clear();
    }

    private void tickStarfallSalvo(ServerLevel serverLevel) {
        if (!this.bossState.isCombat()) {
            return;
        }
        if (this.starfallSalvoPending) {
            if (this.tickStarfallSalvoDetonation(serverLevel)) {
                this.starfallSalvoPending = false;
            }
            return;
        }
        if (this.starfallSalvoCooldownTicks > 0) {
            --this.starfallSalvoCooldownTicks;
            if (this.starfallSalvoCooldownTicks <= 0) {
                this.spawnStarfallSalvo(serverLevel);
            }
        }
    }

    private void spawnStarfallSalvo(ServerLevel serverLevel) {
        // 2026-09-10（B1 配置接线）：原先是硬编码 5.0 / 20.0，导致 SilentSunConfig.STARFALL_SALVO_RADIUS
        // 成了死配置（整合包把 starfallSalvoRadius 改成 7.0 也毫无效果）。
        // 设计稿 §7.3：非集中轰炸散布半径 = 配置值（默认 20）；集中轰炸目标保持 5.0。
        double concentratedRadius = STARFALL_SALVO_CONCENTRATED_RADIUS;
        double dispersedRadius = SilentSunConfig.STARFALL_SALVO_RADIUS.get();
        int minCount = SilentSunConfig.STARFALL_SALVO_MIN_COUNT.get();
        int maxCount = SilentSunConfig.STARFALL_SALVO_MAX_COUNT.get();
        int maxDelay = SilentSunConfig.STARFALL_SALVO_MAX_DELAY_TICKS.get();
        int count = minCount >= maxCount ? minCount : minCount + this.random.nextInt(maxCount - minCount + 1);
        this.starfallSalvoStars.clear();
        this.starfallSalvoPending = true;
        // 2026-09-01 用户裁决「释放紧凑」：settleTimeout 收紧到 maxDelay+20（正常演出 ≈
        // maxDelay + 下落 6 tick，兜底 1 秒；原 160+maxDelay 太宽松，星星悬停期间僵等）
        this.starfallSalvoSettleTimeoutTicks = maxDelay + 20;
        this.starfallSalvoReadyToDetonate = false;
        this.starfallSalvoDetonateDelayTicks = 0;
        this.starfallSalvoCooldownTicks = SilentSunConfig.STARFALL_SALVO_INTERVAL_TICKS.get();
        List<LivingEntity> targets = this.collectStarfallTargets(serverLevel);
        if (targets.isEmpty()) {
            this.spawnStarfallStars(serverLevel, null, count, concentratedRadius, maxDelay);
            return;
        }
        // 持续范围轰炸者优先，其余按累计净伤害（仇恨值）降序。
        targets.sort((a, b) -> {
            boolean ab = this.isActiveAreaBombardment(a);
            boolean bb = this.isActiveAreaBombardment(b);
            if (ab != bb) return ab ? -1 : 1;
            return Double.compare(this.hatredOf(b), this.hatredOf(a));
        });
        LivingEntity highestHatred = null;
        double maxHatred = 0.0;
        for (LivingEntity t : targets) {
            double h = this.hatredOf(t);
            if (h > maxHatred) {
                maxHatred = h;
                highestHatred = t;
            }
        }
        double totalHatred = 0.0;
        for (LivingEntity t : targets) {
            totalHatred += Math.max(0.0, this.hatredOf(t));
        }
        if (totalHatred <= 0.0) {
            LivingEntity primary = targets.get(0);
            double r = this.isConcentratedStarfallTarget(primary, highestHatred) ? concentratedRadius : dispersedRadius;
            this.spawnStarfallStars(serverLevel, primary, count, r, maxDelay);
            return;
        }
        int assigned = 0;
        for (int i = 0; i < targets.size(); ++i) {
            LivingEntity t = targets.get(i);
            double h = Math.max(0.0, this.hatredOf(t));
            if (h <= 0.0) continue;
            int share = i == targets.size() - 1 ? count - assigned : (int)Math.floor((double)count * (h / totalHatred));
            if (share > 0) {
                double r = this.isConcentratedStarfallTarget(t, highestHatred) ? concentratedRadius : dispersedRadius;
                this.spawnStarfallStars(serverLevel, t, share, r, maxDelay);
                assigned += share;
            }
        }
        if (assigned < count) {
            LivingEntity primary = targets.get(0);
            double r = this.isConcentratedStarfallTarget(primary, highestHatred) ? concentratedRadius : dispersedRadius;
            this.spawnStarfallStars(serverLevel, primary, count - assigned, r, maxDelay);
        }
    }

    private boolean isConcentratedStarfallTarget(LivingEntity t, LivingEntity highestHatred) {
        return this.isActiveAreaBombardment(t) || highestHatred != null && t == highestHatred;
    }

    private List<LivingEntity> collectStarfallTargets(ServerLevel serverLevel) {
        // 2026-09-01 用户裁决：大招直接对视距内（reach×4）全部合法目标索敌，
        // 不再「8 格内有目标就只打 8 格内」的两级回退（避免忽略 8~32 格目标）。
        // 2026-09-10（B2）：半径接入配置 starfallSalvoAttackRadius（设计稿 §8.2 = 24），
        // 取 max(配置值, 攻击距离×4) 作下限——落实设计值的同时不削弱上述裁定
        //（攻击距离随阶段/激怒上升时，收集半径同步放大）。
        double viewDist = Math.max(SilentSunConfig.STARFALL_SALVO_ATTACK_RADIUS.get(),
            this.getCurrentAttackReach() * 4.0);
        return serverLevel.getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(viewDist), e -> BossTargeting.isValidAttackTarget(this, e));
    }

    private void spawnStarfallStars(ServerLevel serverLevel, LivingEntity target, int count, double radius, int maxDelay) {
        double centerX = target != null ? target.getX() : this.getX();
        double centerY = target != null ? target.getY() : this.getY();
        double centerZ = target != null ? target.getZ() : this.getZ();
        for (int i = 0; i < count; ++i) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            // 2026-09-01 用户实测「没有分散感」：原 sqrt(random) 平方根分布中心密（视觉一坨）。
            // 改中心留空的外围均匀分布（25%~100% 半径带），满天星雨散布感保留。
            double dist = radius * (0.25 + 0.75 * Math.sqrt(this.random.nextDouble()));
            double x = centerX + Math.cos(angle) * dist;
            double z = centerZ + Math.sin(angle) * dist;
            double hoverY = centerY + 2.0;
            double spawnY = centerY + 30.0;
            int delay = maxDelay > 0 ? this.random.nextInt(maxDelay + 1) : 0;
            StarfallSalvoEntity star = ModEntities.STARFALL_SALVO.get().create(serverLevel);
            if (star == null) continue;
            // 记录相对目标中心的水平偏移：跟踪时保持（星星群整体平移，间距不变 → 分散感）
            star.initSalvo(this.getUUID(), hoverY, delay,
                target != null ? target.getUUID() : null, x - centerX, z - centerZ);
            star.setPos(x, spawnY, z);
            serverLevel.addFreshEntity(star);
            this.starfallSalvoStars.add(star.getUUID());
        }
    }

    private boolean tickStarfallSalvoDetonation(ServerLevel serverLevel) {
        boolean timeout;
        timeout = --this.starfallSalvoSettleTimeoutTicks <= 0;
        if (!this.starfallSalvoReadyToDetonate) {
            boolean allSettled = true;
            if (!timeout) {
                for (UUID uuid : this.starfallSalvoStars) {
                    StarfallSalvoEntity salvo;
                    Entity star = serverLevel.getEntity(uuid);
                    if (!(star instanceof StarfallSalvoEntity) || (salvo = (StarfallSalvoEntity)star).isSettled()) continue;
                    allSettled = false;
                    break;
                }
            }
            if (!timeout && !allSettled) {
                return false;
            }
            this.starfallSalvoReadyToDetonate = true;
            // 2026-09-01 用户实测「瞬爆」：引爆准备 10 → 20 tick（1 秒），给玩家预警反应时间
            this.starfallSalvoDetonateDelayTicks = 20;
            serverLevel.playSound(null, this.blockPosition(), (SoundEvent)ModSounds.STARFALL_SALVO_PRE_EXPLOSION.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
            return false;
        }
        if (this.starfallSalvoDetonateDelayTicks > 0) {
            --this.starfallSalvoDetonateDelayTicks;
            return false;
        }
        this.detonateStarfallSalvo(serverLevel);
        return true;
    }

    private void detonateStarfallSalvo(ServerLevel serverLevel) {
        StarfallCurtainEntity curtain = ModEntities.STARFALL_CURTAIN.get().create(serverLevel);
        if (curtain != null) {
            curtain.initCurtain(30);
            curtain.setOwnerUuid(this.getUUID());
            curtain.setPos(this.getX(), this.getY() + 1.0, this.getZ());
            serverLevel.addFreshEntity(curtain);
        }
        float power = (float)(SilentSunConfig.STARFALL_SALVO_EXPLOSION_POWER.get()).doubleValue();
        HashSet<LivingEntity> hitVictims = new HashSet<>();
        // 战斗爆闪可破坏方块（入场演示星星走别的路径不破坏）。尊重 mobGriefing。
        boolean griefing = serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        for (UUID uuid : this.starfallSalvoStars) {
            Entity star = serverLevel.getEntity(uuid);
            if (star == null || star.isRemoved()) continue;
            double sx = star.getX();
            double sy = star.getY();
            double sz = star.getZ();
            // 破坏方块：手动球形破坏，不用 explode——explode 任何 interaction 都会对实体
            // 造成爆炸伤害（与断魂属性伤害叠加），且 NONE 模式实测仍有击退/伤害（「瞬爆炸飞」）。
            // 这里只破坏方块，实体伤害与击退由下方手动控制。
            if (griefing) {
                this.destroyStarfallBlocks(serverLevel, sx, sy, sz, power);
            }
            // 视觉粒子
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, sx, sy, sz, 1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.FLASH, sx, sy, sz, 1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.sendParticles(ParticleTypes.CLOUD, sx, sy, sz, 10, 0.8, 0.8, 0.8, 0.05);
            AABB blast = new AABB(sx - (double)power, sy - (double)power, sz - (double)power, sx + (double)power, sy + (double)power, sz + (double)power);
            List<LivingEntity> inBlast = serverLevel.getEntitiesOfClass(LivingEntity.class, blast, e -> e != this && BossTargeting.isValidAttackTarget(this, e));
            hitVictims.addAll(inBlast);
            // 弱击退（2026-09-01 用户实测认可「有击退但没炸上天」的力度）：朝远离爆点水平推开，
            // 力度 0.5，远弱于 explode 强击退，避免炸飞
            for (LivingEntity v : inBlast) {
                double dx = v.getX() - sx;
                double dz = v.getZ() - sz;
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 1.0E-6) {
                    dx = 0.0;
                    dz = 0.0;
                } else {
                    dx /= len;
                    dz /= len;
                }
                v.knockback(0.5, dx, dz);
            }
            if (star instanceof StarfallSalvoEntity salvo) {
                salvo.markLegitRemoval();
            }
            star.discard();
        }
        float starDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity victim : hitVictims) {
            // 反作弊惩罚窗口：强制命中（无视目标自定义无敌帧）
            this.resetTargetInvulnIfPunishWindow(victim);
            // 断魂属性伤害（9 bypass 真伤，穿透护甲/无敌帧/保护）——与断魂结算共用伤害类型
            victim.hurt(ModDamageTypes.soulSever(victim.level()), starDamage);
            // 挂断魂账本（Boss 账本，头衔依赖值）后立即结算一次（2026-09-01 用户裁决：
            // 爆闪命中立即结算断魂，而不是等到下次受击）
            this.applySoulSeverToTarget(victim);
            cn.autoforged.extinction_day_mod_1784441698.effect.SoulSeverMobEffect.applySoulSeverExplicit(victim, this);
        }
        this.starfallSalvoStars.clear();
    }

    /** 手动球形破坏方块（战斗繁星爆闪用；mobGriefing 由调用方检查）。
     *  半径 = 爆炸强度，跳过空气/基岩/不可破坏方块。不用 explode 以避免其对实体
     *  的爆炸伤害/击退与断魂属性伤害叠加。 */
    private void destroyStarfallBlocks(ServerLevel level, double cx, double cy, double cz, double radius) {
        net.minecraft.core.BlockPos center = net.minecraft.core.BlockPos.containing(cx, cy, cz);
        int r = Math.max(1, (int) Math.floor(radius));
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    net.minecraft.core.BlockPos p = center.offset(dx, dy, dz);
                    if (center.distSqr(p) > radius * radius) continue;
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
                    if (st.isAir() || st.getBlock() == net.minecraft.world.level.block.Blocks.BEDROCK) continue;
                    if (st.getDestroySpeed(level, p) < 0.0f) continue;
                    level.destroyBlock(p, false);
                }
            }
        }
    }

    private void updateBossEvent() {
        if (this.bossState == BossState.PHASE1_VOTE) {
            return;
        }
        if (this.bossState == BossState.PHASE1_TRANSITION) {
            int total = this.transitionTotal();
            int elapsed = total - this.transitionTicks;
            float p = (float)elapsed / (float)total;
            boolean visible = elapsed >= 6;
            this.bossEvent.setVisible(visible);
            float progress = (float)this.transitionTicks / (float)total;
            this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
            if (p < 0.33f) {
                this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);
            } else if (p < 0.66f) {
                this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_12);
            } else {
                this.bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
            }
            this.bossEvent.setProgress(Mth.clamp(progress, 0.0f, 1.0f));
            this.bossEvent.setName(this.getTransitionBossBarName());
            return;
        }
        this.bossEvent.setVisible(true);
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        this.bossEvent.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        // 2026-09-11（代码审计 G14 #4 修复）：原此处调 setBossBarMax(...)，那是个**永久 no-op** ——
        // 它反射查找 BossEvent 的 "maxProgress" 字段，而 1.21.1 的 BossEvent 只有
        // name / progress / color / overlay / darkenScreen / playBossMusic / createWorldFog
        // （setProgress 直接存归一化值）⇒ getDeclaredField 必抛，被空 catch 吞掉。
        // 血条本就由下面这行归一化比值正确驱动，故删除方法与其调用。
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.bossEvent.setName(this.getBossBarName());
    }

    private Component getTransitionBossBarName() {
        int tenths = Mth.clamp((int)((this.transitionTicks * 10 + 19) / 20), 0, 9999);
        int sec = tenths / 10;
        int dec = tenths % 10;
        return this.getBossBarName().copy().append(Component.literal(" ")).append(Component.translatable("bossbar.silent_sun.redios.transition").withStyle(ChatFormatting.DARK_PURPLE)).append(Component.literal(" ")).append(Component.translatable("bossbar.silent_sun.redios.transition_time", new Object[]{sec, dec}).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    Component rediosSigned(Component msg) {
        return Component.translatable("message.silent_sun.redios.signature", new Object[]{this.getDisplayName()}).append(msg);
    }

    private void tickDarknessAmbientSounds(ServerLevel serverLevel) {
        if (this.battleParticipants.isEmpty()) {
            return;
        }
        if (--this.darknessSoundCooldown > 0) {
            return;
        }
        this.darknessSoundCooldown = 60 + this.random.nextInt(60);
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player;
            if (this.expelledPlayers.contains(id) || (player = this.getServerPlayer(id)) == null || !player.isAlive() || !player.hasEffect(MobEffects.DARKNESS)) continue;
            SoundEvent sound = DARKNESS_AMBIENT_SOUNDS[this.random.nextInt(DARKNESS_AMBIENT_SOUNDS.length)];
            serverLevel.playSound(null, player.blockPosition(), sound, SoundSource.HOSTILE, 0.5f, 0.8f + this.random.nextFloat() * 0.4f);
        }
    }

    private void applyPhaseMaxHealth(ServerLevel serverLevel) {
        AttributeInstance kbInst;
        AttributeInstance ekbInst;
        AttributeInstance attackInst;
        // 2026-09-11（代码审计 G13 #4 修复）：原此处先调 ensureMaxHealthUncapped()，但那是个
        // **空操作** —— maxHealthUncapped 字段只被它自己读写（全库零读取），整段等价于「什么都不做」；
        // 而紧邻的 1024 上限告警却在实打实处理「被原版上限钳住」的后果 ⇒ 两者互为误导，
        // 维护者会以为本模组已自行绕过 1024 上限。已删除该字段、方法与本次调用。
        // （真正绕过原版 1024 上限仍需外部 attributefix，见下方的钳制告警。）
        double desired = (SilentSunConfig.PHASE_MAX_HEALTH.get()).intValue();
        if (!Double.isFinite(desired) || desired <= 0.0) {
            return;
        }
        AttributeInstance inst = this.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) {
            return;
        }
        double oldMax = this.getMaxHealth();
        double oldHealth = this.getHealth();
        this.rebuildMaxHealthAttribute(desired);
        this.phaseMaxHealthApplied = true;
        double newMax = this.getMaxHealth();
        if (!(Double.isFinite(oldMax) && oldMax > 0.0 && Double.isFinite(newMax) && newMax > 0.0)) {
            return;
        }
        if (newMax < desired - 1.0 && !warnedMaxHealthClamped) {
            warnedMaxHealthClamped = true;
            SilentSunMod.LOGGER.warn("Redios MAX_HEALTH clamped to {} by vanilla attribute cap (1024); install \u5c5e\u6027\u4fee\u590d (attributefix) to reach configured phaseMaxHealth={}.", newMax, desired);
            MutableComponent clampMsg = Component.translatable("message.silent_sun.redios.maxhealth_clamped", new Object[]{(int)desired}).withStyle(ChatFormatting.RED);
            for (ServerPlayer p : serverLevel.players()) {
                p.displayClientMessage(this.rediosSigned(clampMsg), false);
            }
        }
        double ratio = oldHealth / oldMax;
        double scaled = newMax * (ratio = Mth.clamp(ratio, 0.0, 1.0));
        float clamped = (float)Mth.clamp(scaled, 1.0, newMax);
        if (clamped != this.getHealth()) {
            this.setHealth(clamped);
        }
        if ((attackInst = this.getAttribute(Attributes.ATTACK_DAMAGE)) != null) {
            attackInst.setBaseValue((SilentSunConfig.BASE_ATTACK_DAMAGE.get()).doubleValue());
        }
        if ((kbInst = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)) != null) {
            kbInst.setBaseValue((SilentSunConfig.KNOCKBACK_RESISTANCE.get()).doubleValue());
        }
        // 2026-09-10（用户裁决 D7）：爆炸击退不走 push（Explosion 自己算完直接 setDeltaMovement），
        // 只能靠该属性挡。与 KNOCKBACK_RESISTANCE 共用同一个配置键，保证"免疫击退"是一条口径。
        if ((ekbInst = this.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)) != null) {
            ekbInst.setBaseValue((SilentSunConfig.KNOCKBACK_RESISTANCE.get()).doubleValue());
        }
        this.anticheat.lastObservedMaxHealth = this.anticheat.expectedMaxHealth = (double)this.getMaxHealth();
    }

    private void rebuildMaxHealthAttribute(double desired) {
        AttributeInstance inst = this.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) {
            return;
        }
        inst.setBaseValue(1024.0);
        // 清除所有作用于 MAX_HEALTH 的外部 modifier（/attribute modifier add 或其它模组
        // 注入的任意 UUID），只保留本模组自己的 override，杜绝外部把上限拉高/压低后无法恢复。
        for (AttributeModifier modifier : new ArrayList<>(inst.getModifiers())) {
            if (!MAX_HEALTH_MODIFIER_ID.equals(modifier.id())) {
                inst.removeModifier(modifier);
            }
        }
        inst.removeModifier(MAX_HEALTH_MODIFIER_ID);
        double delta = desired - 1024.0;
        if (delta > 0.0) {
            AttributeModifier mod = new AttributeModifier(MAX_HEALTH_MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE);
            inst.addPermanentModifier(mod);
        }
    }

    private void tickLocateBoss(ServerLevel serverLevel) {
        if (!RediosRules.locateBossEnabled() || this.battleParticipants.isEmpty() || this.bossState.isVoteOrTransition()) {
            this.locateBossFarTicks.clear();
            return;
        }
        int blocks = Math.max(0, RediosRules.locateBossDistanceBlocks());
        if (blocks <= 0) {
            this.locateBossFarTicks.clear();
            return;
        }
        int interval = Math.max(1, RediosRules.locateBossNotifyIntervalTicks());
        double distSqr = (double)blocks * (double)blocks;
        BlockPos pos = this.blockPosition();
        for (UUID id2 : new HashSet<UUID>(this.battleParticipants)) {
            if (this.expelledPlayers.contains(id2)) {
                this.locateBossFarTicks.remove(id2);
                continue;
            }
            ServerPlayer player = this.getServerPlayer(id2);
            if (player == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) {
                this.locateBossFarTicks.remove(id2);
                continue;
            }
            if (player.distanceToSqr(this) <= distSqr) {
                this.locateBossFarTicks.remove(id2);
                continue;
            }
            int t = this.locateBossFarTicks.getOrDefault(id2, 0) + 1;
            if (t >= interval) {
                t = 0;
                player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.locate_boss", new Object[]{pos.getX(), pos.getY(), pos.getZ()}).withStyle(ChatFormatting.GRAY)));
            }
            this.locateBossFarTicks.put(id2, t);
        }
        this.locateBossFarTicks.keySet().removeIf(id -> !this.battleParticipants.contains(id));
    }

    private void tickBattleAreaCheck(ServerLevel serverLevel) {
        if (this.battleParticipants.isEmpty()) {
            this.outOfAreaTicks.clear();
            return;
        }
        int radius = Math.max(0, RediosRules.battleRadiusBlocks());
        int timeoutTicks = Math.max(1, RediosRules.battleExpelTimeoutSeconds()) * 20;
        double radiusSqr = (double)radius * (double)radius;
        for (UUID id2 : new HashSet<UUID>(this.battleParticipants)) {
            double dz;
            if (this.expelledPlayers.contains(id2)) {
                this.outOfAreaTicks.remove(id2);
                continue;
            }
            ServerPlayer player = this.getServerPlayer(id2);
            if (player == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) {
                this.outOfAreaTicks.remove(id2);
                continue;
            }
            double dx = player.getX() - this.getX();
            double distSqr = dx * dx + (dz = player.getZ() - this.getZ()) * dz;
            if (distSqr > radiusSqr) {
                int t = this.outOfAreaTicks.getOrDefault(id2, 0) + 1;
                if (t > timeoutTicks) {
                    this.expelFromBattle(player);
                    this.outOfAreaTicks.remove(id2);
                    continue;
                }
                this.outOfAreaTicks.put(id2, t);
                continue;
            }
            this.outOfAreaTicks.remove(id2);
        }
        this.outOfAreaTicks.keySet().removeIf(id -> !this.battleParticipants.contains(id));
    }

    void clearAllExternalEffects() {
        // 无条件清除所有非自身效果：仅保留莱德厄斯自身的"激怒"状态，
        // 其余无论是有益/有害药水效果，还是被 removeEffect 覆写锁定的效果，一律移除。
        MobEffectInstance enrage = this.getEffect((Holder<MobEffect>)ModEffects.ENRAGE);
        // 2026-09-02：2.8/2.9 永久效果豁免（直至 Boss 死亡）——colorlessUnlocked 解锁后，
        // 力量V/迅捷II/恢复V（无限时长）在清除时保留并重挂，onTitleChanged/反作弊不再移除
        //（正推/逆推/状态切换效果保持；重复效果以永久版本为主）。
        MobEffectInstance permanentBoost = this.colorlessUnlocked ? this.getEffect(MobEffects.DAMAGE_BOOST) : null;
        MobEffectInstance permanentSpeed = this.colorlessUnlocked ? this.getEffect(MobEffects.MOVEMENT_SPEED) : null;
        MobEffectInstance permanentRegen = this.colorlessUnlocked ? this.getEffect(MobEffects.REGENERATION) : null;
        this.removeAllEffects();
        if (enrage != null) {
            super.addEffect(enrage, this);
        }
        if (permanentBoost != null) {
            super.addEffect(permanentBoost, this);
        }
        if (permanentSpeed != null) {
            super.addEffect(permanentSpeed, this);
        }
        if (permanentRegen != null) {
            super.addEffect(permanentRegen, this);
        }
    }

    void reapplySelfBuffs() {
        if (this.phase == 2) {
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 4, true, false), this);
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false), this);
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, true, false), this);
        }
        if (this.phase == 1) {
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2, true, false), this);
        }
    }

    private void tickBossMissingInView(ServerLevel serverLevel) {
        if (!RediosRules.bossMissingVisionEnabled() || this.battleParticipants.isEmpty() || this.bossState.isVoteOrTransition()) {
            this.bossNotInViewTicks.clear();
            this.lastMissingViewNotifyTick.clear();
            return;
        }
        int threshold = Math.max(1, RediosRules.bossMissingVisionTicks());
        double dotThreshold = RediosRules.bossMissingVisionDotThreshold();
        if (!Double.isFinite(dotThreshold)) {
            dotThreshold = 0.45;
        }
        for (UUID id2 : new HashSet<UUID>(this.battleParticipants)) {
            if (this.expelledPlayers.contains(id2)) {
                this.bossNotInViewTicks.remove(id2);
                this.lastMissingViewNotifyTick.remove(id2);
                continue;
            }
            ServerPlayer player = this.getServerPlayer(id2);
            if (player == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) {
                this.bossNotInViewTicks.remove(id2);
                this.lastMissingViewNotifyTick.remove(id2);
                continue;
            }
            boolean visible = player.hasLineOfSight(this);
            if (visible) {
                Vec3 look = player.getLookAngle();
                Vec3 toBoss = this.position().subtract(player.position());
                Vec3 toNorm = toBoss.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : toBoss.normalize();
                double dot = look.dot(toNorm);
                visible = dot >= dotThreshold;
                            }
            if (visible) {
                this.bossNotInViewTicks.remove(id2);
                continue;
            }
            int t = this.bossNotInViewTicks.getOrDefault(id2, 0) + 1;
            if (t >= threshold) {
                t = 0;
                RediosRules.BossMissingVisionAction action = RediosRules.bossMissingVisionAction();
                if (action == RediosRules.BossMissingVisionAction.TELEPORT) {
                    this.teleportIntoView(serverLevel, player);
                } else {
                    int now = this.tickCount;
                    int last = this.lastMissingViewNotifyTick.getOrDefault(id2, -600);
                    if (now - last >= 600) {
                        this.lastMissingViewNotifyTick.put(id2, now);
                        BlockPos pos = this.blockPosition();
                        player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.locate_boss", new Object[]{pos.getX(), pos.getY(), pos.getZ()}).withStyle(ChatFormatting.GRAY)));
                        this.trySendWaypoint(serverLevel, player, pos);
                    }
                }
            }
            this.bossNotInViewTicks.put(id2, t);
        }
        this.bossNotInViewTicks.keySet().removeIf(id -> !this.battleParticipants.contains(id));
        this.lastMissingViewNotifyTick.keySet().removeIf(id -> !this.battleParticipants.contains(id));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void teleportIntoView(ServerLevel serverLevel, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0E-6) {
            look = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 raw = player.position().add(look.normalize().scale(8.0));
        BlockPos base = BlockPos.containing((double)raw.x, (double)raw.y, (double)raw.z);
        BlockPos surface = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
        this.allowSelfTeleport = true;
        try {
            this.teleportTo((double)surface.getX() + 0.5, surface.getY(), (double)surface.getZ() + 0.5);
        }
        finally {
            this.allowSelfTeleport = false;
        }
        this.setNoAi(false);
        this.forceSetTarget((LivingEntity)player);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private void trySendWaypoint(ServerLevel serverLevel, ServerPlayer player, BlockPos pos) {
        String dim = String.valueOf(serverLevel.dimension().location());
        String name = "Redios";
        String cmd = "/jm waypoint temp create \"" + name + "\" " + dim + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " aqua " + player.getGameProfile().getName() + " true";
        serverLevel.getServer().getCommands().performPrefixedCommand(serverLevel.getServer().createCommandSourceStack().withPermission(4).withSuppressedOutput(), cmd);
        String xaero = "/setwaypoint " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        MutableComponent xaeroMsg = Component.literal("[Xaero] ").withStyle(ChatFormatting.GOLD).append(Component.literal((String)xaero).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, xaero))));
        player.sendSystemMessage(this.rediosSigned(xaeroMsg));
    }

    /**
     * A-1（2026-09-11）：战斗音乐由「整曲单次播放」改为 intro → loop → outro 段状态机。
     * <p>
     * 流程（依用户裁决）：开战/换相位 → intro；满 introTicks → loop（每 loopTicks 重发一次，
     * 因流式 ogg 无法自动循环）；**P1 进入投票（PHASE1_VOTE）→ P1 outro**；跳过投票直入 P2 则
     * 直接播 P2 intro；战斗结束 → P2 outro（见 {@link #playBattleMusicOutroForParticipants()}）。
     * <p>
     * 每个玩家独立记「已下发的段」（{@code phase*10 + segment}），进出范围时自动补发/停播。
     */
    private void tickBattleMusic(ServerLevel serverLevel) {
        if (!RediosRules.rediosBattleMusicEnabled() || this.battleParticipants.isEmpty()) {
            this.stopAllBattleMusic();
            this.battleMusicPhase = 0;
            this.battleMusicSegment = MUSIC_SEG_NONE;
            this.battleMusicSegmentEndTick = 0L;
            return;
        }
        int desiredPhase = this.phase == 2 ? 2 : 1;
        // ① 相位切换（含开战）：清掉上一相位的音效，从 intro 重新起步。
        //    投票必然属于 P1；若正在投票而相位记录尚未跟上，按 P1 处理（不切段）。
        if (desiredPhase != this.battleMusicPhase) {
            this.stopAllBattleMusic();
            this.battleMusicPhase = desiredPhase;
            this.battleMusicSegment = MUSIC_SEG_INTRO;
            this.battleMusicSegmentEndTick = (long)this.tickCount + this.battleMusicIntroTicksFor(desiredPhase);
            this.battleMusicOutroSent = false;
            ++this.battleMusicStamp;
        }
        // ② 段推进
        if (this.bossState == BossState.PHASE1_VOTE) {
            // 用户裁决「P1 进投票放 P1 结束（outro）」：outro 不重发、不计时。
            if (this.battleMusicSegment != MUSIC_SEG_OUTRO) {
                this.stopAllBattleMusic();
                this.battleMusicSegment = MUSIC_SEG_OUTRO;
                this.battleMusicSegmentEndTick = 0L;
                ++this.battleMusicStamp;
            }
            this.battleMusicOutroSent = true;
        } else if (this.battleMusicSegment == MUSIC_SEG_INTRO) {
            if ((long)this.tickCount >= this.battleMusicSegmentEndTick) {
                this.battleMusicSegment = MUSIC_SEG_LOOP;
                this.battleMusicSegmentEndTick = (long)this.tickCount + this.battleMusicLoopTicksFor(this.battleMusicPhase);
                ++this.battleMusicStamp;
            }
        } else if (this.battleMusicSegment == MUSIC_SEG_LOOP) {
            if ((long)this.tickCount >= this.battleMusicSegmentEndTick) {
                this.battleMusicSegmentEndTick = (long)this.tickCount + this.battleMusicLoopTicksFor(this.battleMusicPhase);
                // 2026-09-11 修复（代码审计 G14）：原本只续期 endTick、未改段标识，而下发判定是
                // 「stamp 未变则跳过」→ 重发被整个吃掉，loop 播完一次后整场无声。
                ++this.battleMusicStamp;
            }
        }
        int segment = this.battleMusicSegment;
        if (segment == MUSIC_SEG_NONE) {
            return;
        }
        DeferredHolder<SoundEvent, SoundEvent> music = this.battleMusicSoundFor(this.battleMusicPhase, segment);
        if (music == null) {
            return;
        }
        // ③ 逐玩家下发：段标识变化（含换段、重发周期到点后 loop 段不变）时重新发送。
        float volume = RediosRules.rediosBattleMusicVolume();
        double rangeSqr = this.battleMusicRangeSqr();
        int stamp = this.battleMusicStamp;
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || !player.isAlive() || player.level() != this.level()) {
                if (player != null) {
                    this.stopBattleMusicFor(player);
                }
                this.battleMusicPlaying.remove(id);
                continue;
            }
            if (this.distanceToSqr(player) > rangeSqr) {
                this.stopBattleMusicFor(player);
                this.battleMusicPlaying.remove(id);
                continue;
            }
            Integer playing = this.battleMusicPlaying.get(id);
            if (playing != null && playing == stamp) continue;
            this.stopBattleMusicFor(player);
            player.connection.send(new ClientboundSoundPacket(music, SoundSource.MUSIC, this.getX(), this.getY(), this.getZ(), volume, 1.0f, this.random.nextLong()));
            this.battleMusicPlaying.put(id, stamp);
        }
        this.battleMusicPlaying.keySet().removeIf(uid -> !this.battleParticipants.contains(uid));
    }

    /** A-1：该阶段的 intro 段时长（tick）＝ 对应 ogg 实测长度。 */
    private int battleMusicIntroTicksFor(int phase) {
        return phase == 2 ? RediosRules.rediosBattleMusicPhase2IntroTicks() : RediosRules.rediosBattleMusicPhase1IntroTicks();
    }

    /** A-1：该阶段的 loop 段时长（tick）＝ 重发周期（＝对应 ogg 实测长度）。 */
    private int battleMusicLoopTicksFor(int phase) {
        return phase == 2 ? RediosRules.rediosBattleMusicPhase2LoopTicks() : RediosRules.rediosBattleMusicPhase1LoopTicks();
    }

    /** A-1：相位 + 段 → 对应的音效事件（无匹配返回 null）。 */
    private DeferredHolder<SoundEvent, SoundEvent> battleMusicSoundFor(int phase, int segment) {
        boolean p2 = phase == 2;
        if (segment == MUSIC_SEG_INTRO) {
            return p2 ? ModSounds.REDIOS_BATTLE_MUSIC_PHASE2_INTRO : ModSounds.REDIOS_BATTLE_MUSIC_PHASE1_INTRO;
        }
        if (segment == MUSIC_SEG_LOOP) {
            return p2 ? ModSounds.REDIOS_BATTLE_MUSIC_PHASE2_LOOP : ModSounds.REDIOS_BATTLE_MUSIC_PHASE1_LOOP;
        }
        if (segment == MUSIC_SEG_OUTRO) {
            return p2 ? ModSounds.REDIOS_BATTLE_MUSIC_PHASE2_OUTRO : ModSounds.REDIOS_BATTLE_MUSIC_PHASE1_OUTRO;
        }
        return null;
    }

    /** A-1：音乐可听半径的平方——跟随参战半径配置；无效值回落 72（与 sounds.json 保持一致）。 */
    private double battleMusicRangeSqr() {
        int r = RediosRules.battleRadiusBlocks();
        if (r <= 0) {
            r = 72;
        }
        return (double)r * (double)r;
    }

    /**
     * A-1（2026-09-11）：战斗结束时下发本阶段 outro（用户裁决：P1 进投票播 P1 结束曲，
     * P2 收尾按惯性 = 战斗结束）。已下发过（投票期已播 P1 outro）则不重复。
     * <p>
     * 下发后置 {@code battleMusicOutroSent}，使随后的 {@code cleanupPlayerAfterBattle}
     * 不再 stop MUSIC 源 —— 让 outro 自然播完，而不是被清理打断。
     */
    private void playBattleMusicOutroForParticipants() {
        if (this.battleMusicOutroSent) {
            return;
        }
        this.battleMusicOutroSent = true;
        if (!RediosRules.rediosBattleMusicEnabled() || !RediosRules.rediosBattleMusicOutroEnabled()) {
            return;
        }
        DeferredHolder<SoundEvent, SoundEvent> outro = this.battleMusicSoundFor(this.phase == 2 ? 2 : 1, MUSIC_SEG_OUTRO);
        if (outro == null) {
            return;
        }
        float volume = RediosRules.rediosBattleMusicVolume();
        HashSet<UUID> ids = new HashSet<UUID>(this.battleParticipants);
        ids.addAll(this.expelledPlayers);
        for (UUID id : ids) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || player.connection == null) {
                continue;
            }
            this.stopBattleMusicFor(player);
            player.connection.send(new ClientboundSoundPacket(outro, SoundSource.MUSIC, this.getX(), this.getY(), this.getZ(), volume, 1.0f, this.random.nextLong()));
        }
    }

    private void stopBattleMusicFor(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
    }

    private void stopAllBattleMusic() {
        ServerPlayer player;
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            this.stopBattleMusicFor(player);
        }
        for (UUID id : new HashSet<UUID>(this.expelledPlayers)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            this.stopBattleMusicFor(player);
        }
        this.battleMusicPlaying.clear();
    }

    private void tickHeightFlight(ServerLevel serverLevel) {
        if (this.darkStarFlightUnlocked) {
            this.setNoGravity(true);
            return;
        }
        if (!RediosRules.heightFlightEnabled()) {
            return;
        }
        if (!this.heightFlightMode) {
            int diff = Math.max(0, RediosRules.heightFlightDiffBlocks());
            if (diff <= 0) {
                return;
            }
            if (this.level().getBlockState(this.blockPosition().below()).is(Blocks.BEDROCK)) {
                this.heightFlightMode = true;
            }
            for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
                ServerPlayer player;
                if (this.expelledPlayers.contains(id) || (player = this.getServerPlayer(id)) == null || player.isSpectator() || !player.isAlive() || player.level() != this.level() || !(Math.abs(player.getY() - this.getY()) >= (double)diff)) continue;
                this.heightFlightMode = true;
                break;
            }
            if (!this.heightFlightMode) {
                for (UUID id : new HashSet<UUID>(this.mobParticipants)) {
                    LivingEntity target = this.getMobParticipant(id);
                    if (target == null || !target.isAlive() || !(Math.abs(target.getY() - this.getY()) >= (double)diff)) continue;
                    this.heightFlightMode = true;
                    break;
                }
            }
        }
        if (!this.heightFlightMode) {
            return;
        }
        this.setNoGravity(true);
        if (this.bossState.isVoteOrTransition() || this.isDarkStarBlastOngoing()) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive() || target.level() != this.level()) {
            target = this.pickNearestActiveParticipant(serverLevel);
        }
        if (target == null) {
            return;
        }
        double speed = RediosRules.heightFlightVerticalSpeed();
        if (!Double.isFinite(speed) || speed <= 0.0) {
            return;
        }
        double dy = target.getY() - this.getY();
        if (Math.abs(dy) < 0.75) {
            Vec3 dm = this.getDeltaMovement();
            this.setDeltaMovement(dm.x, 0.0, dm.z);
            return;
        }
        double yVel = Mth.clamp((double)(dy * 0.08), -speed, speed);
        Vec3 dm = this.getDeltaMovement();
        this.setDeltaMovement(dm.x, yVel, dm.z);
    }

    private LivingEntity pickNearestActiveParticipant(ServerLevel serverLevel) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            double d;
            ServerPlayer player;
            if (this.expelledPlayers.contains(id) || (player = this.getServerPlayer(id)) == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level() || !((d = player.distanceToSqr(this)) < bestDist)) continue;
            bestDist = d;
            best = player;
        }
        if (best == null) {
            for (UUID id : new HashSet<UUID>(this.mobParticipants)) {
                double d;
                LivingEntity target = this.getMobParticipant(id);
                if (target == null || !target.isAlive() || !((d = target.distanceToSqr(this)) < bestDist)) continue;
                bestDist = d;
                best = target;
            }
        }
        return best;
    }

    private void tickAiWatchdog(ServerLevel serverLevel) {
        int interval = Math.max(1, SilentSunConfig.AI_WATCHDOG_CHECK_INTERVAL_TICKS.get());
        if (this.tickCount % interval != 0) {
            return;
        }
        if (!this.isTargetingAllowed()) {
            this.aiWatchdogNoTargetTicks = 0;
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && BossTargeting.isValidAttackTarget(this, target)) {
            this.aiWatchdogNoTargetTicks = 0;
            return;
        }
        int threshold = Math.max(1, SilentSunConfig.AI_WATCHDOG_NO_TARGET_TICKS.get());
        this.aiWatchdogNoTargetTicks += interval;
        if (this.aiWatchdogNoTargetTicks < threshold) {
            return;
        }
        this.aiWatchdogNoTargetTicks = 0;
        // 设计 §583：当前目标失效 → 立即重新索敌；重新索敌以威胁值优先，
        // 无人有威胁值时（例如全员零输出）才退回就近索敌。
        LivingEntity next = this.pickHighestThreatTarget();
        if (next == null) {
            next = this.pickNearestActiveParticipant(serverLevel);
        }
        if (next != null) {
            this.setTarget(next);
        }
    }

    private boolean isSeaSkyGapActive() {
        return this.phase == 2 && this.titleIndex == 0;
    }

    /**
     * 2.2「错位干涉」是否生效（20% 命中率 + Boss 攻击 50% 落空）。
     * <p>
     * 2026-09-10（用户裁决 Q1「重建补闪避」）：改为**由 phase/titleIndex 派生**，不再用字段缓存。
     * 原实现只在 {@code onTitleChanged} 里赋值，而 {@code restoreStateFromNbt}（存档重载 / 外部删除后
     * 重建）直接写 phase/titleIndex、不经过 onTitleChanged → 字段恒为 false，2.2 的两条效果静默失效。
     * 派生写法天然随 NBT 持久化（phase/titleIndex 均已入档）。
     * <p>
     * 同源的另两项一并派生：{@link #isChaosRuinActive()}（2.3 混沌破败）与 {@link #isAshDawnActive()}（2.4 灰烬曙光）
     * ——它们与 2.2 是同一种「只由 phase/titleIndex 决定却存成字段」的写法，重载后同样失效。
     */
    boolean isWrongInterferenceActive() {
        return this.phase == 2 && this.titleIndex == 2;
    }

    /** 2.3「混沌破败」是否生效（见 {@link #isWrongInterferenceActive()} 的持久化说明）。 */
    boolean isChaosRuinActive() {
        return this.phase == 2 && this.titleIndex == 3;
    }

    /** 2.4「灰烬曙光」是否生效（见 {@link #isWrongInterferenceActive()} 的持久化说明）。 */
    private boolean isAshDawnActive() {
        return this.phase == 2 && this.titleIndex == 4;
    }

    private void applyOpponentEffect(Supplier<MobEffectInstance> effectFactory) {
        this.forEachFightingOpponent(target -> target.addEffect((MobEffectInstance)effectFactory.get()));
    }

    private void tickSeaSkyGap(ServerLevel serverLevel) {
        if (!this.seaSkySoulSeverUnlocked) {
            return;
        }
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 4, true, false), this);
        this.applyOpponentEffect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 4, true, true));
    }

    boolean hasFlag(BossFlag flag) {
        return this.unlockedFlags.contains(flag);
    }

    void grantFlag(BossFlag flag) {
        this.unlockedFlags.add(flag);
    }

    private void syncFlagsFromBooleans() {
        if (this.seaSkySoulSeverUnlocked) {
            this.grantFlag(BossFlag.SEA_SKY_SOUL_SEVER);
        }
        if (this.soulSeverHarvestUnlocked) {
            this.grantFlag(BossFlag.SOUL_SEVER_HARVEST);
        }
        if (this.uncontrolledSprintUnlocked) {
            this.grantFlag(BossFlag.UNCONTROLLED_SPRINT);
        }
        if (this.guardUnlocked) {
            this.grantFlag(BossFlag.GUARD_BLOCK);
        }
        if (this.colorlessUnlocked) {
            this.grantFlag(BossFlag.COLORLESS);
        }
        if (this.ashDawnUnlocked) {
            this.grantFlag(BossFlag.ASH_DAWN);
        }
        if (this.chaosRuinAbsoluteAttacks) {
            this.grantFlag(BossFlag.CHAOS_RUIN_ABSOLUTE);
        }
        if (this.enrageStackingUnlocked) {
            this.grantFlag(BossFlag.ENRAGE_STACKING);
        }
        if (this.blackSunUnlocked) {
            this.grantFlag(BossFlag.BLACK_SUN);
        }
        if (this.weaknessCurseActive) {
            this.grantFlag(BossFlag.WEAKNESS_CURSE);
        }
        // 2026-09-11（代码审计 G06 #3 修复）：原此处 grant 的 DARK_STAR_FIRED / DARK_STAR_FLIGHT /
        // BEDROCK_REPAIRED 三个旗标**从不被 hasFlag 查询**（真值就是上面三个同名布尔字段），
        // 属只写不读的死旗标 —— 已随 BossFlag 里的常量一并删除。
    }

    boolean isUncontrolledSprintActive() {
        return this.phase == 2 && this.titleIndex == 1;
    }

    private void tickUncontrolledSprint(ServerLevel serverLevel) {
        if (!this.uncontrolledSprintUnlocked) {
            return;
        }
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false), this);
        this.applyOpponentEffect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false));
    }

    public boolean isInvulnerableTo(DamageSource source) {
        if (this.introTicks > 0) {
            return true;
        }
        if (this.bossState.isFrozen()) {
            return true;
        }
        if (this.phase == 1 && this.titleIndex == 6 && this.titleLockTicks > 0
            && (source == null || !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))) {
            // 2026-09-10（用户裁决）：1.6「竭力之悲」只免疫普通伤害，9bypass 真伤/断魂照常打血
            //（与原版 Entity.isInvulnerableTo 的 bypass 语义一致）。isFrozen() 分支保持全免——
            // 冻结期全免是「9pass 不得跳阶段」的防线，不可放行。
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean hurt(DamageSource source, float amount) {
        // 无敌帧短路（2026-09-01 修复）：管线先于原版无敌帧判定执行，导致无敌帧期
        // （一次命中后 20 tick）管线副作用（反射反伤 / 回血 / 断魂累计 / 灵魂 Y 累计）
        // 在"实际未掉血"的幻影攻击上照常触发。这里按原版语义短路：
        // 无敌帧内非穿甲伤害直接拒绝（管线不跑、副作用不触发）；
        // BYPASSES_INVULNERABILITY（9pass 断魂等穿甲）不受影响，照常走管线与结算。
        if (this.invulnerableTime > 0
            && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.logDamageZero(source, amount, amount, "invulnerable_frame");
            return false;
        }
        boolean dealt;
        DamageContext ctx = DamagePipeline.run(this, source, amount);
        if (ctx.cancelled) {
            this.logDamageZero(source, amount, ctx.amount, "pipeline_cancelled");
            return false;
        }
        this.inHurtProcessing = true;
        try {
            dealt = super.hurt(source, ctx.amount);
        }
        finally {
            this.inHurtProcessing = false;
        }
        if (!dealt) {
            this.logDamageZero(source, amount, ctx.amount, "not_dealt");
        }
        if (dealt) {
            this.anticheat.recordLegalDamage(ctx.amount);
            LivingEntity attacker = this.tryResolveDamageAttacker(source);
            if (attacker instanceof Player) {
                this.markBattleParticipant(attacker);
                this.recordPlayerDamageType(source, ctx.amount);
                this.recordPlayerNetDamage((Player)attacker, source, ctx.amount);
            }
            if (this.isDeadOrDying()) {
                this.deathViaHurtTick = this.tickCount;
            }
        }
        return dealt;
    }

    void debugLogUnknownDamageSource(DamageSource source, float amount) {
        String dCls;
        String eCls;
        String sourceCls;
        if (source == null) {
            return;
        }
        if (RediosRules.damageSourceDebugOnlyPhase2() && this.phase != 2) {
            return;
        }
        if (RediosRules.damageSourceDebugOnlyWhenExpelled() && this.expelledPlayers.isEmpty()) {
            return;
        }
        Entity e = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (e instanceof Player) {
            return;
        }
        if (direct instanceof Player) {
            return;
        }
        UUID owner = this.tryResolveOwnerUuid(e);
        if (owner == null) {
            owner = this.tryResolveOwnerUuid(direct);
        }
        if (owner != null) {
            return;
        }
        owner = this.tryResolveOwnerUuidFromDamageSource(source);
        if (owner != null) {
            return;
        }
        int now = this.tickCount;
        int cooldown = Math.max(0, RediosRules.damageSourceDebugCooldownTicks());
        String msgId = source.getMsgId();
        String key = msgId + "|" + (sourceCls = source.getClass().getName()) + "|" + (eCls = e == null ? "null" : e.getClass().getName()) + "|" + (dCls = direct == null ? "null" : direct.getClass().getName());
        // 2026-09-11（代码审计 G14 #2 修复）：哨兵整数溢出。新键取到 Integer.MIN_VALUE 后做
        // **int** 减法会回绕成负数（now=5000 → -2147478648；now=MAX → -1），恒 < cooldown
        // ⇒ 直接 return、连 put 都到不了，键永远是「新键」⇒ 下方 LOGGER.warn 永不执行。
        // 与本文件 wallAttackLastNotifyTick 的既有正确写法对齐，把比较放宽成 long。
        long last = this.damageSourceDebugLastTick.getOrDefault(key, Integer.MIN_VALUE).intValue();
        if ((long)now - last < (long)cooldown) {
            return;
        }
        this.damageSourceDebugLastTick.put(key, now);
        Object typeObj = this.tryInvokeNoArgMethod(source, "type");
        if (typeObj == null) {
            typeObj = this.tryInvokeNoArgMethod(source, "getType");
        }
        String typeStr = typeObj == null ? "null" : typeObj.getClass().getName() + ":" + String.valueOf(typeObj);
        SilentSunMod.LOGGER.warn("Redios unknown DamageSource: amount={} msgId={} source={} type={} entity={} direct={} pos={} phase={} title={} expelled={} participants={}", new Object[]{Float.valueOf(amount), msgId, sourceCls, typeStr, eCls, dCls, this.blockPosition(), this.phase, this.titleIndex, this.expelledPlayers.size(), this.battleParticipants.size()});
    }

    private void logDamageZero(DamageSource source, float originalAmount, float finalAmount, String reason) {
        if (!(SilentSunConfig.DAMAGE_ZERO_LOG_ENABLED.get()).booleanValue()) {
            return;
        }
        if (source == null) {
            return;
        }
        int cooldown = Math.max(0, SilentSunConfig.DAMAGE_ZERO_LOG_COOLDOWN_TICKS.get());
        int now = this.tickCount;
        String msgId = source.getMsgId();
        String key = reason + "|" + msgId + "|" + source.getClass().getName();
        // 2026-09-11（代码审计 G14 #2 修复）：同 debugLogUnknownDamageSource —— 哨兵 Integer.MIN_VALUE
        // 参与 int 减法会回绕，导致限频判据恒真、日志分支永不执行（DAMAGE_ZERO_LOG_ENABLED
        // 打开后也拿不到任何输出，等于排障手段失效）。
        long last = this.damageZeroLogLastTick.getOrDefault(key, Integer.MIN_VALUE).intValue();
        if ((long)now - last < (long)cooldown) {
            return;
        }
        this.damageZeroLogLastTick.put(key, now);
        Entity e = source.getEntity();
        Entity direct = source.getDirectEntity();
        SilentSunMod.LOGGER.warn("Redios damageZero: reason={} original={} final={} msgId={} source={} entity={} direct={} pos={} phase={} title={} state={}", new Object[]{reason, Float.valueOf(originalAmount), Float.valueOf(finalAmount), msgId, source.getClass().getName(), e == null ? "null" : e.getClass().getName(), direct == null ? "null" : direct.getClass().getName(), this.blockPosition(), this.phase, this.titleIndex, this.bossState});
    }

    private void clearDamageDebugCaches() {
        this.reflectNoArgMethods.clear();
        this.reflectNoArgMissing.clear();
        this.reflectFields.clear();
        this.reflectFieldMissing.clear();
        this.damageSourceDebugLastTick.clear();
        this.damageZeroLogLastTick.clear();
    }

    boolean isDamageFromExpelledPlayer(DamageSource source) {
        if (source == null || this.expelledPlayers.isEmpty()) {
            return false;
        }
        Entity e = source.getEntity();
        if (this.isExpelledPlayerEntity(e)) {
            return true;
        }
        Entity direct = source.getDirectEntity();
        if (this.isExpelledPlayerEntity(direct)) {
            return true;
        }
        UUID owner = this.tryResolveOwnerUuid(e);
        if (owner != null && this.expelledPlayers.contains(owner)) {
            return true;
        }
        owner = this.tryResolveOwnerUuid(direct);
        if (owner != null && this.expelledPlayers.contains(owner)) {
            return true;
        }
        owner = this.tryResolveOwnerUuidFromDamageSource(source);
        return owner != null && this.expelledPlayers.contains(owner);
    }

    private boolean isExpelledPlayerEntity(Entity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }
        Player player = (Player)entity;
        return this.expelledPlayers.contains(player.getUUID());
    }

    LivingEntity tryResolveDamageAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity e = source.getEntity();
        if (e instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)e;
            return living;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)direct;
            return living;
        }
        Entity owner = this.tryResolveOwnerEntity(direct);
        if (owner instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)owner;
            return living;
        }
        owner = this.tryResolveOwnerEntity(e);
        if (owner instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)owner;
            return living;
        }
        String[] names = new String[]{"getAttacker", "getOwner", "getCaster", "getShooter", "getThrower", "getSummoner", "getTrueOwner", "getSource"};
        for (String name : names) {
            Entity candidate = this.tryReadEntityFromInvokeResult(this.tryInvokeNoArgMethod(source, name));
            if (!(candidate instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity)candidate;
            return living;
        }
        return null;
    }

    private Entity tryResolveOwnerEntity(Entity entity) {
        OwnableEntity ownable;
        LivingEntity owner;
        if (entity == null) {
            return null;
        }
        if (entity instanceof Projectile) {
            Projectile proj = (Projectile)entity;
            return proj.getOwner();
        }
        if (entity instanceof OwnableEntity && (owner = (ownable = (OwnableEntity)entity).getOwner()) != null) {
            return owner;
        }
        String[] names = new String[]{"getOwner", "getCaster", "getShooter", "getThrower", "getSummoner", "getTrueOwner", "getPlayerOwner", "getSource"};
        for (String name : names) {
            Entity candidate = this.tryReadEntityFromInvokeResult(this.tryInvokeNoArgMethod(entity, name));
            if (candidate == null) continue;
            return candidate;
        }
        return null;
    }

    private Entity tryReadEntityFromInvokeResult(Object o) {
        Optional opt;
        Object inner;
        if (o == null) {
            return null;
        }
        if (o instanceof Entity) {
            Entity e = (Entity)o;
            return e;
        }
        if (o instanceof Optional && (inner = (opt = (Optional)o).orElse(null)) instanceof Entity) {
            Entity e = (Entity)inner;
            return e;
        }
        return null;
    }

    private Object tryInvokeNoArgMethod(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return null;
        }
        Class<?> clazz = target.getClass();
        Map<String, Method> methods = this.reflectNoArgMethods.get(clazz);
        if (methods != null && methods.containsKey(name)) {
            Method m = methods.get(name);
            try {
                return m.invoke(target, new Object[0]);
            }
            catch (ReflectiveOperationException ignored) {
                methods.remove(name);
                this.reflectNoArgMissing.computeIfAbsent(clazz, k -> new HashSet()).add(name);
                return null;
            }
        }
        Set<String> missing = this.reflectNoArgMissing.get(clazz);
        if (missing != null && missing.contains(name)) {
            return null;
        }
        try {
            Method m = clazz.getMethod(name, new Class[0]);
            this.reflectNoArgMethods.computeIfAbsent(clazz, k -> new HashMap()).put(name, m);
            return m.invoke(target, new Object[0]);
        }
        catch (ReflectiveOperationException ignored) {
            this.reflectNoArgMissing.computeIfAbsent(clazz, k -> new HashSet()).add(name);
            return null;
        }
    }

    private UUID tryReadUuidFromInvokeResult(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof UUID) {
            UUID u = (UUID)o;
            return u;
        }
        if (o instanceof Entity) {
            Entity owner = (Entity)o;
            return owner.getUUID();
        }
        if (o instanceof Optional) {
            Optional opt = (Optional)o;
            Object inner = opt.orElse(null);
            if (inner instanceof UUID) {
                UUID u = (UUID)inner;
                return u;
            }
            if (inner instanceof Entity) {
                Entity owner = (Entity)inner;
                return owner.getUUID();
            }
        }
        return null;
    }

    private Object tryReadField(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return null;
        }
        Class<?> clazz = target.getClass();
        Map<String, Field> fields = this.reflectFields.get(clazz);
        if (fields != null && fields.containsKey(name)) {
            Field f = fields.get(name);
            try {
                return f.get(target);
            }
            catch (IllegalAccessException ignored) {
                fields.remove(name);
                this.reflectFieldMissing.computeIfAbsent(clazz, k -> new HashSet()).add(name);
                return null;
            }
        }
        Set<String> missing = this.reflectFieldMissing.get(clazz);
        if (missing != null && missing.contains(name)) {
            return null;
        }
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            this.reflectFields.computeIfAbsent(clazz, k -> new HashMap()).put(name, f);
            return f.get(target);
        }
        catch (ReflectiveOperationException ignored) {
            this.reflectFieldMissing.computeIfAbsent(clazz, k -> new HashSet()).add(name);
            return null;
        }
    }

    private UUID tryResolveOwnerUuidFromDamageSource(DamageSource source) {
        if (source == null) {
            return null;
        }
        String[] methodNames = new String[]{"getOwnerUUID", "getOwner", "getCasterUUID", "getCaster", "getShooterUUID", "getShooter", "getThrowerUUID", "getThrower", "getSummonerUUID", "getSummoner", "getTrueOwnerUUID", "getTrueOwner", "getPlayerOwnerUUID", "getPlayerOwner", "getAttackerUUID", "getAttacker", "getSourceUUID", "getSource"};
        for (String name : methodNames) {
            UUID u = this.tryReadUuidFromInvokeResult(this.tryInvokeNoArgMethod(source, name));
            if (u == null) continue;
            return u;
        }
        String[] fieldNames = new String[]{"ownerUUID", "ownerUuid", "owner", "casterUUID", "casterUuid", "caster", "shooterUUID", "shooterUuid", "shooter", "throwerUUID", "throwerUuid", "thrower", "summonerUUID", "summonerUuid", "summoner", "attackerUUID", "attackerUuid", "attacker", "sourceUUID", "sourceUuid", "source"};
        for (String name : fieldNames) {
            UUID u = this.tryReadUuidFromInvokeResult(this.tryReadField(source, name));
            if (u == null) continue;
            return u;
        }
        return null;
    }

    private UUID tryResolveOwnerUuid(Entity entity) {
        Projectile proj;
        Entity owner;
        if (entity == null) {
            return null;
        }
        if (entity instanceof Projectile && (owner = (proj = (Projectile)entity).getOwner()) != null) {
            return owner.getUUID();
        }
        if (entity instanceof OwnableEntity) {
            OwnableEntity ownable = (OwnableEntity)entity;
            UUID u = ownable.getOwnerUUID();
            if (u != null) {
                return u;
            }
            LivingEntity owner2 = ownable.getOwner();
            if (owner2 != null) {
                return owner2.getUUID();
            }
        }
        String[] names = new String[]{"getOwnerUUID", "getOwner", "getCasterUUID", "getCaster", "getShooterUUID", "getShooter", "getThrowerUUID", "getThrower", "getSummonerUUID", "getSummoner", "getTrueOwnerUUID", "getTrueOwner", "getPlayerOwnerUUID", "getPlayerOwner"};
        for (String name : names) {
            UUID u = this.tryReadUuidFromInvokeResult(this.tryInvokeNoArgMethod(entity, name));
            if (u == null) continue;
            return u;
        }
        return null;
    }

    /**
     * 真伤判据（2026-09-10 用户裁决 A10「收窄判据」）。
     * <p>
     * 真伤 = 带 {@code #minecraft:bypasses_invulnerability} 标签的伤害（断魂 soul_sever、
     * 2.7 全属性 redios_spectrum、以及其它模组的 9bypass 真伤通道），**不再按 msgId 白名单判定**。
     * <p>
     * 原实现把 {@code magic / indirectMagic / sonic_boom / wither / dragonBreath} 也算真伤，造成两处偏差：
     * <ol>
     *   <li><b>P0</b>：拔刀剑刀光 / 幻影剑等普通魔法命中被 {@code DamagePipeline.stageHealImmunity}
     *       判为「免疫并记录」——伤害被取消并转成 <b>Boss 回血 + 断魂 x</b>，玩家越打 Boss 越硬；</li>
     *   <li>{@code WeaponManager.tryGuardBlock} 对普通魔法免于格挡，与设计稿 L636
     *       「格挡不属于防御判定，弹射物穿透失效，覆盖全身」冲突。</li>
     * </ol>
     * 设计侧的判据是「免疫并记录<b>真实 / 虚空 / 窒息</b>伤害」（设计稿 L396 / L419 / L488），
     * 普通魔法不属于这三类。
     * <p>
     * 注：本服标签文件（`data/minecraft/tags/damage_type/bypasses_invulnerability.json`）为<b>追加</b>式
     * （无 `replace`），故原版的 {@code out_of_world} / {@code generic_kill} 仍在标签内——
     * 这两者已由 {@code stageDirectKillGuard}（generic_kill 无实体直接取消）与
     * {@link #isVoidDamage}（虚空走 1.6 记账）分别接管，不会误入本判据。
     */
    boolean isTrueDamage(DamageSource source) {
        return source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * 伤害 cap 与灾变式动态减伤（2026-09-01 用户裁决：参考灾变模组——随时间递减的高额减伤，
     * 9bypass 打穿）：
     * <ol>
     *   <li><b>9bypass 打穿</b>：断魂/穿甲（BYPASSES_INVULNERABILITY）伤害不受动态减伤，
     *       只受硬上限约束——玩家刀断魂、无妄之终 7% 真伤等仍能压制前期高额减伤；</li>
     *   <li><b>随时间递减</b>：战斗开始（首次进入战斗记录 dynamicReductionStartGameTime）后每秒按
     *       {@code dynamicReductionDecayPerSec} 衰减，初始 {@code dynamicReductionInitial}（80%）
     *       高额减伤 → 归零；控制受伤节奏（前期玩家输出被压制，战斗拖久减伤消失）；</li>
     *   <li><b>超额比例削减</b>：阈值以上部分按比例削减（保留原机制）；</li>
     *   <li><b>硬上限</b>：最终不超过 {@code damageHardCap}。</li>
     * </ol>
     */
    /** 当前服务端游戏时间；非服务端回落 {@code tickCount}（时间基准方法只在服务端链路调用）。 */
    private long gameTimeNow() {
        return this.level() instanceof ServerLevel sl ? sl.getGameTime() : (long)this.tickCount;
    }

    /**
     * 转场总时长（tick）：优先用 {@link #startTransition} 记录的实值，该字段缺失/为 0 时回落配置值。
     * 见字段注释（G13 #2：原 {@code Math.max(1, transitionTotalTicks)} 把 0 抬成 1 是坏兜底）。
     */
    private int transitionTotal() {
        if (this.transitionTotalTicks > 0) {
            return this.transitionTotalTicks;
        }
        return Math.max(1, SilentSunConfig.PHASE_TRANSITION_SECONDS.get() * 20);
    }

    float applyDamageCap(float amount, DamageSource source) {
        float hardCap = (SilentSunConfig.DAMAGE_HARD_CAP.get()).floatValue();
        // ⚠️ 裁决沿革（2026-09-11 用户裁决 N02 = **以现状为准**）：
        //   代码现状 = 2026-09-01 裁决「9bypass 打穿：断魂/穿甲伤害不受动态减伤，只受硬上限约束」。
        //   历史 A1 最终口径（`设计文稿合集.md:2391-2393`「玩家对 Boss 的伤害仍受 200 上限与动态减伤约束」）
        //   本次**不采纳**（曾按"历史版本为准"试改并已回退），保留 2026-09-01 口径。
        // 9bypass 打穿：断魂/穿甲伤害不受动态减伤（用户裁决）
        if (source != null && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return Math.min(amount, hardCap);
        }
        // 灾变式：随时间递减的高额减伤（基准 = 开战时记录的 getGameTime()，跨重载可精确还原）
        float elapsedSec = this.dynamicReductionStartGameTime < 0L ? 0.0f
            : Math.max(0.0f, (float)(this.gameTimeNow() - this.dynamicReductionStartGameTime) / 20.0f);
        float initial = (SilentSunConfig.DYNAMIC_REDUCTION_INITIAL.get()).floatValue();
        float decay = (SilentSunConfig.DYNAMIC_REDUCTION_DECAY_PER_SEC.get()).floatValue();
        float reduction = Math.max(0.0f, initial - elapsedSec * decay);
        float reduced = amount * (1.0f - reduction);
        // 超额比例削减（保留原机制）
        float threshold = (SilentSunConfig.DYNAMIC_REDUCTION_THRESHOLD.get()).floatValue();
        float ratio = (SilentSunConfig.DYNAMIC_REDUCTION_RATIO.get()).floatValue();
        if (reduced > threshold) {
            reduced = threshold + (reduced - threshold) * ratio;
        }
        return Math.min(reduced, hardCap);
    }

    /**
     * 「免疫并记录」判据（设计稿 L396 / L419 / L488）：**虚空 / 窒息**伤害被 Boss 免疫并等额记入断魂 x。
     * <p>
     * {@code stageHealImmunity} 的 {@code setHeal(ctx.amount)} 就是"免疫"的实现——取消本次伤害后
     * 等额回血，净效果为零（1.6 光环自损即净零 + 记账）；这不是额外奖励，故保留。
     * <p>
     * <b>2026-09-10 修正（实测回归，必须记住）</b>：**不得**把 9bypass 真伤
     * （`#minecraft:bypasses_invulnerability`）计入本判据。2.5 收窄 {@link #isTrueDamage} 时
     * 改成标签判定，而灭却之日的 `soul_sever`（断魂 / calamity 打击）**也带该标签** →
     * 被误判为"免疫并记录" → **伤害被取消、Boss 反而回血 + 记 y**，等于把断魂这条主伤害通道
     * 吃成了治疗。实测证据（`latest.log`）：
     * {@code [calamityRound] strike: target=碎镜之影·莱德厄斯 hpBefore=859.999 hpAfter=959.999}。
     * <p>
     * 设计里"免疫并记录**真实**伤害"指的是 **1.6 光环自身的 1 点真伤**，而它的实现走
     * {@code fellOutOfWorld}（虚空）→ 已被虚空分支覆盖，所以删掉真伤分支**不损失设计原意**。
     * 真伤判据 {@link #isTrueDamage} 本身仍保留（供 `WeaponManager.tryGuardBlock` 的"真伤不可格挡"用）。
     */
    boolean isHealImmunityDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        // 虚空（含 1.6 光环自损的 fellOutOfWorld）/ 淹没 / 窒息：免疫 + 记账。
        // 断魂、2.7 全属性、以及其它 9bypass 真伤一律**不**走本判据——它们必须照常打血。
        return this.isVoidDamage(source) || this.isDrowningDamage(source) || this.isSuffocationDamage(source);
    }

    private boolean isDrowningDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        String msgId = source.getMsgId();
        return "drown".equals(msgId) || "drowning".equals(msgId);
    }

    private boolean isSuffocationDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        String msgId = source.getMsgId();
        return "inWall".equals(msgId) || "in_wall".equals(msgId) || "suffocation".equals(msgId);
    }

    void setHeal(float amount) {
        float maxHealth;
        if (amount <= 0.0f || this.level().isClientSide) {
            return;
        }
        // 头衔锁血结束后的 5 秒禁回血缓冲（titleLockGraceTicks）是**故意的削弱措施**
        // （2026-08-30 用户确认保留）：防止锁血刚结束的瞬间回血越过段顶跳段/回血过猛。
        // 锁血进行中（titleLockTicks > 0）不禁回血——自我恢复在锁血生效期间照常有用，
        // 由下方 clamp/回归决定是否越段（锁血中默认只 clamp 拦段顶、非锁血才回退）。
        if (this.titleLockGraceTicks > 0) {
            return;
        }
        if (this.healBoostTicks > 0) {
            amount *= 2.0f;
        }
        if ((double)(maxHealth = this.getMaxHealth()) > 1000.0) {
            amount *= (float)(1000.0 / (double)maxHealth);
        }
        float newHealth = this.getHealth() + amount;
        float max = this.getMaxHealth();
        if (newHealth > max) {
            newHealth = max;
        }
        if (newHealth != this.getHealth()) {
            this.setHealth(newHealth);
            boolean pending = this.bossState == BossState.PHASE1_PENDING || this.bossState == BossState.PHASE2_PENDING;
            if (!pending) {
                // 回血越段顶处理（2026-09-01 回归修复，恢复 d2fb33e 之前的节奏）：
                //   锁血进行中（titleLockTicks > 0）默认只 clamp 拦越段顶、不回退头衔
                //   （ALLOW_TITLE_LOCK_HEAL_REGRESSION 默认 false）——d2fb33e 无条件调
                //   checkHealTitleRegression 导致秒回血反复「回退→重锁→越段→回退」递归链；
                //   非锁血期回血越过段顶才回退头衔并重锁（回退理想场景 8836c43）。
                if (this.titleLockTicks > 0) {
                    if ((SilentSunConfig.ALLOW_TITLE_LOCK_HEAL_REGRESSION.get()).booleanValue()) {
                        this.checkHealTitleRegression();
                    } else {
                        this.clampHealthToCurrentTitle();
                    }
                } else {
                    this.checkHealTitleRegression();
                }
            }
            this.anticheat.markLegalHealthChange(this.getHealth());
        }
    }

    private void checkHealTitleRegression() {
        if (this.bossState.isVoteOrTransition()) {
            return;
        }
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        int newIndex = RediosEntity.computeTitleIndex(this.getMaxHealth(), this.getHealth(), titles.size());
        if (newIndex < this.titleIndex) {
            int oldPhase = this.phase;
            int oldTitleIndex = this.titleIndex;
            this.titleIndex = newIndex;
            this.titleLockTicks = this.titleLockDurationTicks();
            this.onTitleChanged(oldPhase, oldTitleIndex, this.phase, this.titleIndex);
        }
    }

    private void tickNaturalRegen() {
        if (this.tickCount % 20 != 0) {
            return;
        }
        double regenAmount = this.bossState.isPhase2()
            ? (SilentSunConfig.PHASE2_HEALTH_REGEN.get()).doubleValue()
            : (SilentSunConfig.PHASE1_HEALTH_REGEN.get()).doubleValue();
        if (regenAmount > 0.0) {
            this.setHeal((float)regenAmount);
        }
    }

    /**
     * 濒死锁血到期收口。见设计稿《docs/设计文稿-重制版.md》§2.2 + `docs/实现计划-2026-08-27-P2濒死锁血修复.md`。
     * 衔接：配置允许回退 → 回 COMBAT 重打；P2 → pendingLockReleased=true + 切回 COMBAT 允许击杀
     * （不自杀，CD 由 die() 设）；P1 → beginPhase2Choice（投票）。
     */
    private void onPendingLockExpired() {
        boolean phase2 = this.bossState == BossState.PHASE2_PENDING;
        List<Component> titles = phase2 ? PHASE2_TITLES : PHASE1_TITLES;
        int currentIndex = RediosEntity.computeTitleIndex(this.getMaxHealth(), this.getHealth(), titles.size());
        if ((SilentSunConfig.ALLOW_PENDING_LOCK_HEAL_REGRESSION.get()).booleanValue() && currentIndex < titles.size() - 1) {
            // 锁血结束回血越段（配置允许时）：退出 PENDING，回退 COMBAT 重打上一头衔（同步头衔 + 重新锁血 + 重授 flag）
            this.transitionTo(phase2 ? BossState.PHASE2_COMBAT : BossState.PHASE1_COMBAT);
            int oldPhase = this.phase;
            int oldTitleIndex = this.titleIndex;
            this.titleIndex = currentIndex;
            this.titleLockTicks = this.titleLockDurationTicks();
            this.setPose(Pose.STANDING);
            this.bossEvent.setVisible(true);
            this.onTitleChanged(oldPhase, oldTitleIndex, this.phase, this.titleIndex);
            return;
        }
        if (phase2) {
            // P2 濒死锁血到期：解除锁血/无敌 → Boss 变为【可击杀】。
            // pending 唯一目的 = 防止击杀误判（锁 1 血防伤害打到 ≤0 被误判死亡/提前结算），
            // 到期后 pendingLockReleased=true 回 COMBAT，Boss 恢复可被正常击杀（die 设 CD），
            // 无其他设计目的。2026-09-01：不再压回 1 血——PENDING 无敌期自我恢复（只回不扣）
            // 的成果保留，击杀需打掉回血后的当前血量；不自杀、不在此设 CD（CD 由 die() 在
            // 玩家真正击杀时设置）。反作弊基线保持 PENDING 期最后一次 setHeal 的 mark 值。
            this.pendingLockReleased = true;
            this.transitionTo(BossState.PHASE2_COMBAT);
            this.setPose(Pose.STANDING);
        } else {
            this.beginPhase2Choice();
        }
    }

    void enterPendingState() {
        this.pendingLockReleased = false;
        this.titleLockTicks = this.titleLockDurationTicks();
    }

    /**
     * 统一运行时状态转移入口：校验合法性、告警非法转移但不阻断、落盘赋值。
     * <p>
     * 反序列化（readAdditionalSaveData / rebuildFromRecord）直接赋值，
     * 不经过本方法，避免历史存档状态组合被误判为非法。
     */
    void transitionTo(BossState next) {
        if (this.bossState == next) {
            return;
        }
        if (!this.bossState.canTransitionTo(next)) {
            SilentSunMod.LOGGER.warn("[Redios] 非法状态转移: {} -> {} at {}",
                this.bossState, next, this.blockPosition());
        }
        this.bossState = next;
    }

    /**
     * 状态恢复入口：反序列化 / 重建直接赋值，绕过转移表。
     * 历史存档状态组合可能被转移表判定为非法，这里不做告警、直接落盘，
     * 非法 stateOrd 回退到「按阶段 + transitionTicks」的默认状态。
     */
    private void restoreBossState(int stateOrd, int phase, int transitionTicks) {
        this.bossState = stateOrd >= 0 && stateOrd < BossState.values().length
            ? BossState.values()[stateOrd]
            : (transitionTicks > 0 ? BossState.PHASE1_TRANSITION : (phase == 2 ? BossState.PHASE2_COMBAT : BossState.PHASE1_COMBAT));
    }

    public void heal(float amount) {
        this.setHeal(amount);
    }

    void restoreExpectedMaxHealth() {
        if (this.anticheat.expectedMaxHealth <= 0.0) {
            return;
        }
        this.rebuildMaxHealthAttribute(this.anticheat.expectedMaxHealth);
    }

    void bossLeaveNoLoot() {
        ServerLevel serverLevel = (ServerLevel)this.level();
        this.leaveBattle(serverLevel, Component.translatable("message.silent_sun.redios.no_loot_farewell").withStyle(ChatFormatting.GOLD), false);
    }

    // 2026-09-11（代码审计 G14 #6 修复）：原 bossLeaveFriendly(...)（= leaveBattle(..., setCooldown=true)）
    // 自 2026-09-11 创造离场改造后已无任何调用者，且其「设冷却」与现行 §2.4 的 0 冷却口径相反 —— 已删除。
    private void resolveColorlessChallengeSuccess(ServerLevel serverLevel) {
        this.setTarget(null);
        this.setNoAi(true);
        this.grantAdvancementToParticipants(serverLevel, "phase2_countdown");
        // 计时胜利广播（2026-09-02 补上 lang 已有文案 message.silent_sun.redios.challenge_success：
        // 「打爽了。可能让你觉得头疼也抱歉了。」——此前结算全程无玩家可见文本）。
        this.broadcastToParticipants(this.rediosSigned(
            Component.translatable("message.silent_sun.redios.challenge_success").withStyle(ChatFormatting.DARK_PURPLE)));
        // 计时胜利（无色挑战成功）也掉二阶段奖励（2026-09-01 用户裁决：phase2.8 数值难办，
        // 计时胜利给掉落，而非原 antiCheatNoLoot 无掉落）——走 settleBattle 结算
        //（settlementDone 幂等 + 账本先标 settled + phase==2 时 dropPhase2Reward + 召唤冷却）。
        long cooldownTicks = (long) SilentSunConfig.COOLDOWN_DAYS.get().intValue() * 24000L;
        this.settleBattle(serverLevel, cooldownTicks, true, false);
    }

    private void leaveBattle(ServerLevel serverLevel, Component farewellMsg, boolean setCooldown) {
        if (this.settlementDone) {
            // 退场秩序化（2026-08-30）：同 settleBattle——已结算但实体未移除 → 兜底移除。
            if (!this.isRemoved()) {
                this.safeDiscard();
            }
            return;
        }
        this.settlementDone = true;
        // 2026-09-10：无掉落离场诊断日志（`拜拜了您嘞` 这类静默退场原先不留任何痕迹）。
        SilentSunMod.LOGGER.warn("[Redios] 无掉落离场：原因={} 阶段={} 头衔={} 设冷却={} 参战={} 位置={}",
            this.leaveReason, this.phase, this.titleIndex, setCooldown, this.battleParticipants.size(), this.blockPosition());
        // 退场秩序化（M4）：先清账本再执行可能抛异常的清理（含外部模组直调），防幽灵重建
        this.clearBattleRecord(serverLevel);
        this.anticheat.antiCheatNoLoot = true;
        if (setCooldown && BossTargeting.playerOnlyMode()) {
            this.applySummonCooldown(serverLevel, (long)(SilentSunConfig.COOLDOWN_DAYS.get()).intValue() * 24000L);
        }
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 1.0f);
        this.disableBossOutline(serverLevel);
        this.cleanupNearbyLivingAfterBattle(serverLevel);
        this.broadcastToParticipants(this.rediosSigned(farewellMsg));
        this.cleanupPlayersAfterBattle(serverLevel);
        this.bossEvent.setVisible(false);
        this.safeDiscard();
    }

    /** 召唤冷却统一结算入口：所有 CD 设置都经此方法，避免散落遗漏或口径不一致。 */
    private void applySummonCooldown(ServerLevel serverLevel, long cooldownTicks) {
        RediosCooldownData.get(serverLevel).setCooldown(serverLevel, cooldownTicks);
    }

    protected void dropFromLootTable(DamageSource damageSource, boolean causedByPlayer) {
    }

    protected float getEquipmentDropChance(EquipmentSlot slot) {
        return 0.0f;
    }

    /**
     * 防死窗口判定（2026-09-10 实测修复）：落在这些状态下时 Boss 绝不允许死亡。
     * <p>
     * 背景（23:03:48 实测现场）：前置模组断魂（soul_sever）在 {@code LivingDamageEvent.Post}
     * 里把「差额」直接写进血量数据，**绕过 {@link #setHealth}**——单 tick 内血量即被写到 ≤0，
     * 而 vanilla {@code LivingEntity.hurt} 收尾判定（hurt 尾部 isDeadOrDying → die）随即以该
     * 伤害源结算击杀。1.9 的锁血钳制（setHealth 钳 1 + stagePhase1Lock）因此被整条绕过：
     * 一阶段未清却已死亡 → 无掉落、不进投票。
     * <p>
     * vanilla 全部死亡判定都经 {@link #isDeadOrDying()}（hurt:1147/1259、handleEntityEvent:3
     * 等），那是唯一可靠拦截点：本方法同时作为 isDeadOrDying / setHealth 钳制 / 每 tick 回血
     * 兜底 / die 拦截的共同判据。pendingLockReleased（P2 锁血解除）后不再保护，玩家可正常击杀。
     */
    private boolean isProtectedFromDeath() {
        if (this.bossState == BossState.PHASE1_PENDING
            || this.bossState == BossState.PHASE1_VOTE
            || this.bossState == BossState.PHASE1_TRANSITION
            || this.bossState == BossState.PHASE2_PENDING) {
            return true;
        }
        if (this.bossState == BossState.PHASE1_COMBAT) {
            // 一阶段只看头衔：pendingLockReleased 是二阶段字段，绝不可影响一阶段判定
            // （跨阶段泄漏会让 1.9 防死窗口悄悄失效）。
            return this.titleIndex == PHASE1_TITLES.size() - 1;
        }
        if (this.bossState == BossState.PHASE2_COMBAT) {
            // 解除锁血（pendingLockReleased）后允许玩家正常击杀 → 不再保护。
            return !this.pendingLockReleased && this.titleIndex == PHASE2_TITLES.size() - 1;
        }
        return false;
    }

    private boolean isLegitDeathFlow() {
        // P2 濒死锁血是「防击杀窗口」：处于 P2_PENDING 时恒不合法。pendingLockReleased 只在
        // onPendingLockExpired 离开 PENDING（transitionTo PHASE2_COMBAT）时才置 true，所以本分支
        // 在真正处于 P2_PENDING 时恒为 false，等价于 return false；允许击杀发生在切回 COMBAT 之后，
        // 走下方 inHurtProcessing 判定。锁血未到期直调 setHealth(≤0)/kill() 一律视为篡改拦截。
        // 2026-09-10：全部防死窗口（含 1.9 COMBAT/PENDING/VOTE/TRANSITION、2.9 未解除锁血）统一走
        // isProtectedFromDeath —— 即使伤害绕过管线直写血量到 ≤0，也不得判为合法死亡。
        if (this.isProtectedFromDeath()) {
            return false;
        }
        if (this.inHurtProcessing) {
            return true;
        }
        return this.deathViaHurtTick >= 0 && this.tickCount - this.deathViaHurtTick <= 1;
    }

    private void forceSetHealth(float health) {
        super.setHealth(health);
    }

    public void setHealth(float health) {
        // 濒死锁血钳 1（M11：先于 ≤0 篡改拦截）——x.9（1.9/2.9）头衔锁血 + PENDING 期间
        // 最低血量 1、不允许 ≤0：即使前置模组断魂 9pass 直接改血（非 hurt 链路）到 ≤0 也钳 1；
        // 回血/改血到 >1 允许（保底不封顶）。先钳再拦，避免 9pass 直扣 ≤0 被判篡改而非钳 1。
        //   非 x.9 头衔（1.0~1.8/2.0~2.8）：**不钳 1 血**——大伤害交 updateTitle 逐格推进。
        // 2026-09-10：判据统一为 isProtectedFromDeath —— 原名单漏了 PHASE1_VOTE /
        // PHASE1_TRANSITION（1.9 转场/投票窗口），实测死亡正是发生在这些窗口。
        boolean protectedFromDeath = this.isProtectedFromDeath();
        if (protectedFromDeath && health < 1.0f) {
            health = 1.0f;
        }
        if (health <= 0.0f && !this.isLegitDeathFlow()) {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
                this.onTamperAttempt(serverLevel, false);
            }
            return;
        }
        // 非 x.9 段底钳制（2026-09-01）：前置模组 9pass 断魂（soul_sever 无视无敌帧，每 tick
        // 结算）的「差额 setHealth 直扣」绕过 DamagePipeline——这里兜底：COMBAT 非最后头衔且
        // 血量下降时，若低于当前头衔段底则钳回段底（推进由管线/updateTitle 按锁血节奏负责，
        // setHealth 只保底不推进）。回血（setHeal → setHealth 上调）与系统推进（tryForceAdvanceOnLockEnd
        // 压血至新段内）不受影响；P2 解除锁血后（pendingLockReleased）不再钳，允许击杀。
        if (health < this.getHealth() && !protectedFromDeath
            && (this.bossState == BossState.PHASE1_COMBAT || this.bossState == BossState.PHASE2_COMBAT)) {
            List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
            float maxHealth = this.getMaxHealth();
            float segment = maxHealth / (float) titles.size();
            float low = maxHealth - (float) (this.titleIndex + 1) * segment;
            if (low < 0.0f) {
                low = 0.0f;
            }
            if (health < low) {
                health = low;
                // 钳制同步反作弊基线（2026-09-01 修复）：9pass 断魂「差额 setHealth 直扣」
                // 被钳到段底是合法行为，若不记 markLegalHealthChange，单 tick 落差超过
                // max(10, 5%期望) 会被 tickHealthCheatCheck 判为篡改 → 恢复血量 + 误报
                // 「搞么子。」并撤销 9pass 伤害（子代理审查发现）。
                this.anticheat.markLegalHealthChange(health);
            }
        }
        super.setHealth(health);
    }

    public boolean isDeadOrDying() {
        if (super.isDeadOrDying() && !this.legitRemoval && !this.isLegitDeathFlow()) {
            return false;
        }
        return super.isDeadOrDying();
    }

    /**
     * 说明：原版 {@code Entity.setRemoved(RemovalReason)} 在 1.21.1 是 **final**，无法覆写——
     * 第三方模组若绕过 {@link #remove} 直调它（区块卸载/换维度也走这里），本模组只能靠账本
     * 的 externally-removed 回场重建兜底（见 RediosBattleData 的重建分支）。因此这里的
     * remove() 收紧是"尽量在源头拦"，直调 setRemoved 的情形由"回场后不被立刻踢走"（
     * AntiCheatLayer#rearmCreativeLeaveAfterRestore）保证战斗能继续。
     */
    public void remove(Entity.RemovalReason reason) {
        // 退场秩序化（2026-08-30）；2026-09-10 收紧（实测：寰宇支配之剑的"清除实体"直接删掉 Boss）：
        // 原判定只拦「非 KILLED/DISCARDED」，于是第三方模组走 remove(KILLED) / discard() 就能删掉
        // 战斗中的 Boss → 账本判为 externally removed → 回场重建 + 一段流程空窗。
        // 现在一律以 legitRemoval 为准：本模组自己的合法离场（die/settle/leave/purge）都会先置位，
        // 其余任何来源的删除（含 KILLED/DISCARDED）一律拦截并记篡改。
        // 区块卸载与换维度不走本方法（它们直调 final 的 setRemoved，按原版生命周期放行）。
        if (!this.legitRemoval && !this.level().isClientSide) {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
                SilentSunMod.LOGGER.warn("[Redios] 外部删除拦截(remove)：reason={} 位置={}", reason, this.blockPosition());
                this.onTamperAttempt(serverLevel, false);
            }
            return;
        }
        super.remove(reason);
    }

    @SuppressWarnings("deprecation")
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnData) {
        if (spawnType != MobSpawnType.SPAWN_EGG) {
            SilentSunMod.LOGGER.warn("Redios spawn blocked: spawnType={} at {}", spawnType, this.blockPosition());
            this.legitRemoval = true;
            super.remove(Entity.RemovalReason.DISCARDED);
            return spawnData;
        }
        if (isAnotherRediosPresent(level.getLevel(), this)) {
            SilentSunMod.LOGGER.warn("Redios spawn blocked: another Redios already exists");
            this.legitRemoval = true;
            super.remove(Entity.RemovalReason.DISCARDED);
            return spawnData;
        }
        this.battleAnchorPos = this.blockPosition();
        this.battleAnchorDim = this.level().dimension().location();
        this.introTicks = INTRO_TOTAL_TICKS;
        // 2026-09-11（代码审计 G13 #11 修复）：首发齐射冷却按配置初始化（详见字段注释）。
        this.starfallSalvoCooldownTicks = SilentSunConfig.STARFALL_SALVO_INTERVAL_TICKS.get();
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData);
    }

    private void safeDiscard() {
        this.legitRemoval = true;
        if (this.leaveReason != LeaveReason.NONE) {
            SilentSunMod.LOGGER.warn("Redios leaving (leaveReason={}) at {}", this.leaveReason, this.blockPosition());
        }
        // 2026-09-11（代码审计 G15 #4 修复·收口）：在此统一还原 2.6 暗星爆破摧毁/替换掉的
        // 白名单方块（命令方块 / 结构方块 / 屏障 / 末地传送门框架等）。
        // 原实现只在 die() 里还原（且 antiCheatNoLoot 分支还提前 return 跳过它），
        // 而 leaveBattle 覆盖的「无掉落退场 / 创造离场 / 管理员清理 / 全灭 / 脱战 / 区块卸载 /
        // 极限模式区块保留」等出口**全都不还原** ⇒ 2.6 期间走这些出口即不可逆的世界改动。
        // safeDiscard 是所有这些出口的共同终点，在此收口可一次覆盖全部路径；
        // restoreDarkStarSpecialBlocks 自身幂等（末尾 clear 映射、空表直接返回），重复调用安全。
        if (this.level() instanceof ServerLevel sl) {
            this.restoreDarkStarSpecialBlocks(sl);
        }
        this.discard();
    }

    /** 召唤前暴力清除残留 Boss 的静默剔除：绕过反作弊拦截，不结算、不设 CD、不广播。
     *  2026-09-09 防打穿收紧：改为 private——不再暴露为公开「任意静默移除」API，
     *  仅经 {@link #purgeAllResidualBosses}（服务端启动清理入口）调用。 */
    private void forceDiscardSilently() {
        if (this.isRemoved()) {
            return;
        }
        this.legitRemoval = true;
        this.discard();
    }

    /** 服务端启动清理全版本遗留莱德厄斯（2026-08-30 裁决；2026-09-09 收紧为唯一合法入口）：
     *  逐维度剔除未移除的 RediosEntity。走合法标记 + discard，不结算掉落、不设 CD、不广播。 */
    public static void purgeAllResidualBosses(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel sl : server.getAllLevels()) {
            for (Entity e : sl.getEntities().getAll()) {
                if (e instanceof RediosEntity redios && !redios.isRemoved()) {
                    redios.forceDiscardSilently();
                }
            }
        }
    }

    private void onTamperAttempt(ServerLevel serverLevel, boolean applyCooldowns) {
        ++this.anticheat.removalAttemptCount;
        if (this.anticheat.removalAttemptCount > 9999) {
            this.anticheat.removalAttemptCount = 9999;
        }
        if (this.removalPunishCooldownTicks > 0) {
            return;
        }
        this.removalPunishCooldownTicks = 1200;
        SilentSunMod.LOGGER.warn("Redios tamper attempt #{} blocked at {}", this.anticheat.removalAttemptCount, this.blockPosition());
        // 作弊警告：无条件清除所有非自身效果，并重挂自身 Buff
        this.clearAllExternalEffects();
        this.reapplySelfBuffs();
        this.anticheat.counterAllCheatAttackers(serverLevel, applyCooldowns);
    }

    /**
     * 静默补刀：衍生实体（星星/幕布）被作弊清除时，不广播、直接对作弊者造成绝对真伤。
     * <p>
     * 与 {@link AntiCheatLayer#counterCheatAttacker}（物品冷却 + 刷屏警告）不同，这里复用
     * 骑乘惩罚的绝对伤害链路，只补刀、不打扰其它参战者。
     * <p>
     * 2026-09-10 恢复（W2 回归）：本批曾把这条链连同 StarfallSalvo/Curtain 的 remove() 检测
     * 一起删除，导致"星星/幕布被外部清除"再也不会补刀（`silentRetaliate` 一度全项目无定义）。
     */
    void silentRetaliate(ServerPlayer cheater) {
        if (cheater == null || !cheater.isAlive() || cheater.isSpectator()) return;
        if (BossTargeting.isCheatImmune(cheater)) return;
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        cheater.hurtTime = 0;
        cheater.hurtDuration = 0;
        try {
            cheater.invulnerableTime = 0;
        } catch (Throwable ignored) {
        }
        AbsoluteDamageUtil.damage(cheater, this.damageSources().mobAttack(this), damage);
        this.applySoulSeverToTarget(cheater);
    }

    public void die(DamageSource damageSource) {
        boolean includeDefeatBook;
        if (!this.anticheat.antiCheatNoLoot && !this.isLegitDeathFlow()) {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel sl = (ServerLevel)level;
                if (this.isProtectedFromDeath()) {
                    // 防死拦截（2026-09-10 实测修复）：防死窗口内的击杀一律无效——钳回 1 血继续
                    // 流程（1.9 → 锁血到期 → 投票 → 转场 → 二阶段），且**不做作弊惩罚**：走这条路
                    // 的常见原因是前置模组断魂等直写血量的合法机制，牵连玩家会误伤。
                    this.forceSetHealth(1.0f);
                    this.anticheat.markLegalHealthChange(1.0f);
                    SilentSunMod.LOGGER.warn("[Redios] 防死拦截：状态={} 头衔={} 伤害源={} —— 已钳回 1 血继续流程",
                        this.bossState, this.titleIndex,
                        damageSource == null ? "null" : damageSource.getMsgId());
                    return;
                }
                this.onTamperAttempt(sl, false);
                this.forceSetHealth(Math.max(1.0f, this.getHealth()));
            }
            return;
        }
        this.legitRemoval = true;
        if (this.settlementDone) {
            return;
        }
        this.settlementDone = true;
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            super.die(damageSource);
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        // 2026-09-10：击杀路径的诊断日志（实测补漏——击杀走 die() 而非 settleBattle/leaveBattle，
        // 原先这条**最常见的结束方式反倒一条日志都没有**，导致"这场怎么结束的"无从判断）。
        SilentSunMod.LOGGER.warn("[Redios] 被击杀结算：阶段={} 头衔={} 一阶段已清={} 无掉落标记={} 伤害源={} 参战={} 位置={}",
            this.phase, this.titleIndex, this.hasClearedPhase1ForLoot(), this.anticheat.antiCheatNoLoot,
            damageSource == null ? "null" : damageSource.getMsgId(), this.battleParticipants.size(),
            this.blockPosition());
        // 退场秩序化（M5）：先清账本再执行可能抛异常的清理/掉落，防幽灵重建
        this.clearBattleRecord(serverLevel);
        // 2026-09-11（代码审计 G15 #4 修复）：暗星方块还原**提到无掉落分支之前**。
        // 原实现把它放在下方（紧随玩家效果清理之后），而 antiCheatNoLoot 分支在它之前就 return
        // ⇒ 那是 die() 里唯一不还原的路径：2.6 暗星爆破摧毁/替换掉的白名单方块
        // （命令方块 / 结构方块 / 屏障 / 末地传送门框架等）既不还原也无处恢复，
        // 属**不可逆的世界改动**。现三条退出路径共用同一次还原。
        this.restoreDarkStarSpecialBlocks(serverLevel);
        if (this.anticheat.antiCheatNoLoot) {
            this.disableBossOutline(serverLevel);
            this.cleanupNearbyLivingAfterBattle(serverLevel);
            this.cleanupPlayersAfterBattle(serverLevel);
            this.bossEvent.setVisible(false);
            this.clearBattleRecord(serverLevel);
            this.discard();
            return;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            Player player = serverLevel.getPlayerByUUID(id);
            if (player == null) continue;
            player.removeEffect(ModEffects.SOUL_SEVER);
        }
        boolean clearedPhase1 = this.hasClearedPhase1ForLoot();
        if (!clearedPhase1) {
            super.die(damageSource);
            this.disableBossOutline(serverLevel);
            this.cleanupNearbyLivingAfterBattle(serverLevel);
            this.cleanupPlayersAfterBattle(serverLevel);
            this.clearBattleRecord(serverLevel);
            // 退场秩序化（2026-08-30）：P1 未通关就被打死时，settlementDone 已在上方置 true，
            // 若不 discard 会留下「已结算幽灵」——实体还在但所有后续 settleBattle/leaveBattle
            // 被 settlementDone 短路，导致投票 no 离场失效、Boss 赖着不走、反复重启投票。
            // 必须真正移除实体，让退场闭环。
            this.discard();
            return;
        }
        if (this.phase == 2) {
            this.grantAdvancementToParticipants(serverLevel, "phase2_win");
        }
        if (this.isVoidAllThingsActive()) {
            if (this.isFinalKillerPlayer(damageSource)) {
                this.applySummonCooldown(serverLevel, (long)(SilentSunConfig.COOLDOWN_DAYS.get()).intValue() * 24000L);
            }
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 1.0f);
            // 退场秩序化（2026-08-30）：先标记账本「已合法离场」再掉落，防掉落异常导致被误判重建。
            this.clearBattleRecord(serverLevel);
            ArrayList<ItemStack> loot = new ArrayList<ItemStack>();
            loot.addAll(this.createPhase1Loot(serverLevel, false));
            loot.addAll(this.createPhase2Loot(serverLevel, true));
            this.ensureMandatoryLoot(loot, RediosBookOutcome.PHASE2_WIN);
            BlockPos placePos = this.findNearbyRewardPlacement(serverLevel);
            boolean placed = false;
            if (placePos != null) {
                placed = ShulkerBoxUtil.placeShulkerBox(serverLevel, placePos, Blocks.WHITE_SHULKER_BOX.defaultBlockState(), loot, Component.translatable("container.silent_sun.redios_loot"));
            }
            if (!placed) {
                ItemStack box = ShulkerBoxUtil.createShulkerBox(Items.WHITE_SHULKER_BOX, loot, Component.translatable("container.silent_sun.redios_loot"));
                this.spawnAtLocation(box);
                this.notifyRewardCoordinates(serverLevel, this.blockPosition());
            } else {
                this.notifyRewardCoordinates(serverLevel, placePos);
            }
            this.disableBossOutline(serverLevel);
            this.cleanupNearbyLivingAfterBattle(serverLevel);
            this.cleanupPlayersAfterBattle(serverLevel);
            this.bossEvent.setVisible(false);
            this.discard();
            return;
        }
        // 退场秩序化（2026-08-30）：先标记账本「已合法离场」再掉落——即使掉落/清理抛异常，
        // 账本已是 settled，tickServer 只清理不重建（杜绝账本位置与击杀地相距很远时的误判复活）。
        // super.die 放在 clearBattleRecord 之前是为了 vanilla 死亡动画正常触发；
        // 账本标记先行保证退场顺序：先确认合法离场 → 再做后续。
        this.clearBattleRecord(serverLevel);
        super.die(damageSource);
        includeDefeatBook = this.phase == 2;
        if (includeDefeatBook && this.isFinalKillerPlayer(damageSource)) {
            this.applySummonCooldown(serverLevel, (long)(SilentSunConfig.COOLDOWN_DAYS.get()).intValue() * 24000L);
        }
        // 掉落潜影盒规范化（2026-08-30）：按 phase 分派——P1（一阶段停手/中途结算）→ P1 箱；
        // P2（二阶段击杀）→ P2 箱。原实现 P2 也调 dropPhase1Reward（内部回退 P1 配置），
        // 导致二阶段击杀掉成 P1 的箱子（箱子调用脱节）。
        if (this.phase == 2) {
            this.dropPhase2Reward(serverLevel, includeDefeatBook);
        } else {
            this.dropPhase1Reward(serverLevel, includeDefeatBook);
        }
        this.disableBossOutline(serverLevel);
        this.cleanupNearbyLivingAfterBattle(serverLevel);
        this.cleanupPlayersAfterBattle(serverLevel);
        // 正常路径无显式 discard：super.die 走 vanilla 死亡流程自动移除；账本已 settled，不会被重建。
    }

    private boolean isFinalKillerPlayer(DamageSource damageSource) {
        Entity entity = damageSource.getEntity();
        if (!(entity instanceof Player)) {
            return false;
        }
        Player player = (Player)entity;
        return !player.isSpectator() && !player.isCreative();
    }

    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    /*
     * Unable to fully structure code
     */
    public boolean doHurtTarget(Entity target) {
        // 反作弊惩罚窗口：强制命中（无视目标自定义无敌帧）
        if (target instanceof LivingEntity lt) {
            this.resetTargetInvulnIfPunishWindow(lt);
        }
        LivingEntity livingTarget = null;
        double reach = 0.0;
        Level var8_4 = null;
        ServerLevel sl = null;
        double dx = 0.0;
        double dz = 0.0;
        double dist = 0.0;
        double px = 0.0;
        double py = 0.0;
        double pz = 0.0;
        float damage = 0.0f;
        DamageSource src = null;
        boolean result = false;
        Player p = null;
        Player p2 = null;
        ServerPlayer sp = null;
        ServerPlayer sp2 = null;
        Player playerTarget = null;
        float totalDamage = 0.0f;
        float pierceRatio = 0.0f;
        float bypass = 0.0f;
        float normal = 0.0f;
        boolean hit = false;
        Vec3 dir = null;
        Vec3 push = null;
        block28: {
            block30: {
                block29: {
                    block27: {
                        if (!(target instanceof LivingEntity)) {
                            return false;
                        }
                        livingTarget = (LivingEntity)target;
                        this.attackRecoveryTicks = Math.max(this.attackRecoveryTicks, this.getAttackCooldownTicks());
                        if (!BossTargeting.isValidAttackTarget(this, livingTarget)) {
                            return false;
                        }
                        this.markBattleParticipant(livingTarget);
                        if (!BossTargeting.playerOnlyMode() && !(livingTarget instanceof Player)) {
                            this.registerMobParticipant(livingTarget);
                        }
                        reach = this.getCurrentAttackReach();
                        if (this.distanceToSqr(livingTarget) > reach * reach + 9.0) {
                            return false;
                        }
                        if (this.isWrongInterferenceActive() && this.random.nextFloat() > 0.5f) {
                            var8_4 = this.level();
                            if (var8_4 instanceof ServerLevel) {
                                sl = (ServerLevel)var8_4;
                                dx = livingTarget.getX() - this.getX();
                                dz = livingTarget.getZ() - this.getZ();
                                dist = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
                                px = livingTarget.getX() + dx / dist * 1.5;
                                py = livingTarget.getY() + (double)livingTarget.getBbHeight() * 0.5;
                                pz = livingTarget.getZ() + dz / dist * 1.5;
                                sl.sendParticles(ParticleTypes.END_ROD, px, py, pz, 5, 0.2, 0.2, 0.2, 0.05);
                                sl.sendParticles(ParticleTypes.LARGE_SMOKE, px, py, pz, 3, 0.15, 0.15, 0.15, 0.01);
                                sl.playSound(null, px, py, pz, SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 0.5f, 0.9f + this.random.nextFloat() * 0.2f);
                            }
                            return false;
                        }
                        if (!(livingTarget instanceof Player)) {
                            List<Entity> multiCandidates = this.collectMultiEntityCandidates(livingTarget);
                            if (multiCandidates.size() > 1) {
                                if (this.isWhoseWishActive()) {
                                    return false;
                                }
                                return this.doHurtMultiPartTarget(livingTarget, multiCandidates);
                            }
                        }
                        if (livingTarget instanceof Player && ((p2 = (Player)livingTarget).isSpectator() || p2.isCreative() && !(SilentSunConfig.BOSS_DAMAGE_CREATIVE.get()).booleanValue())) {
                            return false;
                        }
                        if (livingTarget instanceof Player && this.expelledPlayers.contains((p = (Player)livingTarget).getUUID())) {
                            return false;
                        }
                        if (this.isWhoseWishActive()) {
                            return false;
                        }
                        if (!this.isChaosRuinActive() && !this.chaosRuinAbsoluteAttacks && !this.isSharpenTrialActiveNow()) break block27;
                        damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        src = this.buildAttackSource();
                        if (livingTarget instanceof ServerPlayer && (sp = (ServerPlayer)livingTarget).level().getLevelData().isHardcore()) {
                            // 2026-09-11（代码审计 G15 #2 修复）：三份重复的「保 1 血 + 登记标记 + 假死亡演出」
                            // 收敛为 clampHardcoreSpare / notifyHardcoreSpare（纯重构，行为不变）。
                            damage = this.clampHardcoreSpare(sp, damage);
                        }
                        result = AbsoluteDamageUtil.damage(livingTarget, src, damage, SilentSunConfig.BOSS_DAMAGE_CREATIVE.get());
                        break block28;
                    }
                    if (!this.attackRandomized && !this.attackSpecialized) break block29;
                    damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    src = this.buildAttackSource();
                    result = livingTarget.hurt(src, damage);
                    break block30;
                }
                if (livingTarget instanceof Player && SilentSunConfig.BOSS_ARMOR_PIERCE.get() > 0.0) {
                    playerTarget = (Player)livingTarget;
                    totalDamage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    pierceRatio = (SilentSunConfig.BOSS_ARMOR_PIERCE.get()).floatValue();
                    bypass = totalDamage * pierceRatio;
                    normal = totalDamage - bypass;
                    hit = false;
                    if (normal > 0.0f) {
                        hit = playerTarget.hurt(ModDamageTypes.rediosAttack(this.level(), this), normal) != false || hit != false;
                    }
                    if (bypass > 0.0f) {
                        hit = AbsoluteDamageUtil.damage((LivingEntity)playerTarget, ModDamageTypes.rediosAttack(this.level(), this), bypass, SilentSunConfig.BOSS_DAMAGE_CREATIVE.get()) != false || hit != false;
                    }
                    result = hit;
                } else {
                    result = super.doHurtTarget(target);
                }
            }
            if (result && livingTarget.getHealth() <= 0.0f && livingTarget instanceof ServerPlayer && (sp = (ServerPlayer)livingTarget).level().getLevelData().isHardcore()) {
                // **仅兜底**：本条挂在 super.doHurtTarget / 护甲穿透链的汇合点——伤害已结算完、
                // hurt() 内部可能已触发 die()，事后补血无法事前注入伤害值。正常路径已由上文
                // clampHardcoreSpare 事前钳伤，不应走到这里；真要「绝不触发 LivingDeathEvent」
                // 须自行重写 Mob.doHurtTarget 的全部副作用（击退/火焰附加/setLastHurtMob），风险不抵收益。
                sp.setHealth(1.0f);
                this.notifyHardcoreSpare(sp);
            }
            if (!result && this.uncontrolledSprintUnlocked && this.isUncontrolledSprintActive()) {
                if (this.distanceToSqr(livingTarget) > 9.0) {
                    return false;
                }
                damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                if (livingTarget instanceof ServerPlayer && (sp2 = (ServerPlayer)livingTarget).level().getLevelData().isHardcore()) {
                    damage = this.clampHardcoreSpare(sp2, damage);
                }
                AbsoluteDamageUtil.damage(livingTarget, ModDamageTypes.rediosAttack(this.level(), this), damage, SilentSunConfig.BOSS_DAMAGE_CREATIVE.get());
                result = true;
            }
            if (!result) {
                return false;
            }
        }
        if (livingTarget.isAlive() && this.isBladeAttackAllowed() && this.isBladeModeActive()) {
            IntegrationContract.tryApplyBossTripleWhammy((LivingEntity)this, livingTarget);
        }
        if (this.voidAllThingsUnlocked) {
            // 2026-09-02：2.9 命中推离能力永久化（逆推/状态切换保持，直至 Boss 死亡）
            dir = livingTarget.position().subtract(this.position());
            if (dir.lengthSqr() < 1.0E-6) {
                dir = new Vec3(1.0, 0.0, 0.0);
            }
            push = dir.normalize().scale(1.8);
            livingTarget.push(push.x, 0.35, push.z);
        }
        if (this.seaSkySoulSeverUnlocked || this.soulSeverHarvestUnlocked || this.isSeaSkyGapActive()) {
            this.applySoulSeverToTarget(livingTarget);
        }
        this.weapons.tryApplyUncontrolledSprintExtraHits(livingTarget);
        return true;
    }

    private Entity[] getSubEntitiesOf(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof EnderDragon) {
            return ((EnderDragon)entity).getSubEntities();
        }
        // 通用多部件：优先 getSubEntities()，再尝试 getParts()/getAllParts()，
        // 覆盖暮色森林九头蛇等多判定框 Boss 的部件 API。
        Entity[] result = this.toEntityArray(this.tryInvokeNoArgMethod(entity, "getSubEntities"));
        if (result != null) {
            return result;
        }
        for (String name : new String[]{"getParts", "getAllParts"}) {
            result = this.toEntityArray(this.tryInvokeNoArgMethod(entity, name));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Entity[] toEntityArray(Object result) {
        if (result instanceof Entity[]) {
            return (Entity[])result;
        }
        if (result instanceof Iterable) {
            List<Entity> out = new ArrayList<>();
            for (Object o : (Iterable<?>)result) {
                if (o instanceof Entity) {
                    out.add((Entity)o);
                }
            }
            if (!out.isEmpty()) {
                return out.toArray(new Entity[0]);
            }
        }
        return null;
    }

    /**
     * B6「受击框穷举」：枚举主目标本体、其多部位部件，以及主目标附近
     * 所有合法召唤物/独立攻击实体，统一作为「判定框」候选池。
     */
    private List<Entity> collectMultiEntityCandidates(LivingEntity primaryTarget) {
        List<Entity> candidates = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        if (primaryTarget != null && primaryTarget.isAlive()) {
            candidates.add(primaryTarget);
            seen.add(primaryTarget.getId());
        }
        this.collectPartsOf(primaryTarget, candidates, seen);
        double radius = this.getCurrentAttackReach() + 2.0;
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(radius), e -> e != this && e.isAlive() && e != primaryTarget
                && BossTargeting.isValidAttackTarget(this, e));
        for (LivingEntity other : nearby) {
            if (seen.add(other.getId())) {
                candidates.add(other);
            }
            this.collectPartsOf(other, candidates, seen);
        }
        return candidates;
    }

    private void collectPartsOf(Entity entity, List<Entity> out, Set<Integer> seen) {
        Entity[] parts = this.getSubEntitiesOf(entity);
        if (parts == null) {
            return;
        }
        for (Entity part : parts) {
            if (part != null && part.isAlive() && seen.add(part.getId())) {
                out.add(part);
            }
        }
    }

    private boolean doHurtMultiPartTarget(LivingEntity mainTarget, List<Entity> candidates) {
        int attempts = candidates.size();
        if (attempts == 0) {
            return false;
        }
        if (this.multiPartTargetId != mainTarget.getId()) {
            this.multiPartTargetId = mainTarget.getId();
            this.multiPartAttackIndex = this.random.nextInt(attempts);
            this.multiPartLastDamage.clear();
            this.multiPartDropStreak.clear();
        }
        this.multiPartAttackIndex = Math.floorMod(this.multiPartAttackIndex, attempts);
        Entity part = candidates.get(this.multiPartAttackIndex);
        float damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource src = ModDamageTypes.rediosAttack(this.level(), this);
        Holder<DamageType> dmgHolder = this.resolveAttackDamageHolder();
        if (dmgHolder != null) {
            // 2026-09-11（代码审计 G15 #3 修复）：单参构造 `new DamageSource(holder)` 的
            // directEntity / causingEntity **都是 null**，会把上一行刚带上的 this（Boss）丢掉。
            // 补回后实际会改变行为的是两处：
            //   · 受击目标的仇恨归因 —— 原版 `hurt` 对带实体的来源会 `setLastHurtByMob(攻击者)`；
            //   · `DamagePipeline` 中以 `ctx.source.getEntity() instanceof LivingEntity` 为判据的
            //     闪避分支（2.1 范围闪避 / 2.2 落空）—— 原先在随机化 / 特化攻击源上恒为 false，
            //     即这两段闪避**从未生效**；补回后开始按设计稿 §7.1 A4 生效。
            // 注：`DamageSource(Holder, Entity directEntity, Entity causingEntity)` 的参数顺序是
            // (holder, direct, causing)；此处两处都传 this，故顺序无差别。
            // 与本文件 randomAttackSource() 的口径统一。
            src = new DamageSource(dmgHolder, this, this);
        }
        float mainHealthBefore = mainTarget.getHealth();
        float partHealthBefore = part instanceof LivingEntity ? ((LivingEntity)part).getHealth() : 0.0f;
        this.swing(InteractionHand.MAIN_HAND);
        boolean dealt = this.damageMultiPart(part, src, damage);
        float damageDealt = part instanceof LivingEntity
            ? partHealthBefore - ((LivingEntity)part).getHealth()
            : mainHealthBefore - mainTarget.getHealth();
        Float previous = this.multiPartLastDamage.get(part.getId());
        boolean dropped = previous != null && damageDealt < previous - MULTI_PART_DAMAGE_THRESHOLD;
        boolean meaningful = dealt && damageDealt > MULTI_PART_DAMAGE_THRESHOLD;
        int streak = 0;
        if (meaningful && dropped) {
            streak = this.multiPartDropStreak.getOrDefault(part.getId(), 0) + 1;
        }
        this.multiPartDropStreak.put(part.getId(), streak);
        this.multiPartLastDamage.put(part.getId(), damageDealt);
        // B6：优先粘住实际伤害最高的判定框；无敌/连续伤害下降则切换探测其它判定框。
        if (!meaningful) {
            this.multiPartAttackIndex = (this.multiPartAttackIndex + 1) % attempts;
        } else if (streak >= MULTI_PART_DROP_SWITCH_STREAK) {
            this.multiPartAttackIndex = (this.multiPartAttackIndex + 1) % attempts;
            this.multiPartDropStreak.put(part.getId(), 0);
        }
        if (this.voidAllThingsUnlocked) {
            // 2026-09-02：2.9 命中推离能力永久化（逆推/状态切换保持，直至 Boss 死亡）
            Vec3 dir = mainTarget.position().subtract(this.position());
            if (dir.lengthSqr() < 1.0E-6) {
                dir = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 push = dir.normalize().scale(1.8);
            mainTarget.push(push.x, 0.35, push.z);
        }
        if (this.seaSkySoulSeverUnlocked || this.soulSeverHarvestUnlocked || this.isSeaSkyGapActive()) {
            this.applySoulSeverToTarget(mainTarget);
        }
        this.weapons.tryApplyUncontrolledSprintExtraHits(mainTarget);
        // 2026-09-11（代码审计 G15 #6 修复）：原实现**恒返回 true** —— 目标处于无敌帧 /
        // 伤害被完全吸收（dealt == false）时也被上层当成「命中」，攻击动画与冷却照常走；
        // 而 doHurtTarget 里「无拘冲刺补击」分支（要求 !result）在这种情形下不会触发，
        // 与「打不动就换判定框 / 补击」的既有意图相左。现与内部判定对齐。
        return meaningful;
    }

    private boolean damageMultiPart(Entity part, DamageSource src, float damage) {
        if (this.isChaosRuinActive() || this.chaosRuinAbsoluteAttacks || this.isSharpenTrialActiveNow()) {
            if (part instanceof LivingEntity) {
                return AbsoluteDamageUtil.damage((LivingEntity)part, src, damage);
            }
            return part.hurt(src, damage);
        }
        boolean hit = part.hurt(src, damage);
        if (!hit && this.uncontrolledSprintUnlocked && this.isUncontrolledSprintActive()
            && this.distanceToSqr(part) <= 9.0) {
            return part.hurt(ModDamageTypes.rediosAttack(this.level(), this), damage);
        }
        return hit;
    }

    /** 反作弊惩罚窗口内：无视目标自定义无敌帧（2026-09-01 用户裁决：反作弊生效 5s 内
     *  Boss 全攻击强制命中，无论是谁——近战/剑气/幻影剑/爆闪/光环统一走此方法）。 */
    private void resetTargetInvulnIfPunishWindow(LivingEntity target) {
        if (target != null && this.anticheat.isPunishWindowActive(this.tickCount)) {
            target.invulnerableTime = 0;
        }
    }

    /**
     * 真伤光环统一结算（2026-09-01 用户裁决：走 9bypass 改血）：
     * 先走完整 hurt（断魂属性 9 bypass，保证受伤演出与死亡流程），
     * 再比较实际扣血与期望伤害，差额直接用 setHealth 补扣——
     * 与灭却之日 {@code SoulSeverMobEffect.applyTrueDamage} 同款，
     * 防第三方「限伤/次数盾」把光环伤害削掉（9 bypass 穿甲但不免疫 set 减伤）。
     */
    private void applyTrueDamageAura(LivingEntity living, float amount) {
        if (living == null || !living.isAlive() || living.level().isClientSide) {
            return;
        }
        this.resetTargetInvulnIfPunishWindow(living);
        float before = living.getHealth();
        living.hurt(ModDamageTypes.soulSever(living.level()), amount);
        if (living.isAlive() && !living.isDeadOrDying() && living.getHealth() > 0.0f) {
            float actual = before - living.getHealth();
            float diff = amount - actual;
            if (diff > 0.5f) {
                living.setHealth(Math.max(0.0f, living.getHealth() - diff));
            }
        }
    }

    void applySoulSeverToTarget(LivingEntity livingTarget) {
        int durationTicks = SilentSunConfig.SOUL_SEVER_DURATION_SECONDS.get() * 20;
        int maxAmplifier = SilentSunConfig.SOUL_SEVER_MAX_AMPLIFIER.get();
        int newAmplifier = 0;
        MobEffectInstance current = livingTarget.getEffect(ModEffects.SOUL_SEVER);
        if (current != null) {
            // 叠加限频（2026-09-01 修复，子代理审查发现）：手动碰撞路径每投射物每 tick +1
            // （5 剑齐射一波 +5），叠加 doHurtTarget/aura 各路径且每次应用刷新满时长，
            // 数秒拉满 maxAmplifier 并战斗中永不衰减——违背「低频率防秒满」设计
            // （BladeAttackGoal 的连击守卫只约束连击路径，护不住齐射/光环）。
            // 每目标每 tick 至多 +1：本 tick 已叠过则保持当前 amplifier（不增不减）。
            CompoundTag stackData = livingTarget.getPersistentData();
            if (stackData.getInt("silent_sun:soul_sever_last_stack_tick") == livingTarget.tickCount) {
                newAmplifier = Math.min(maxAmplifier, current.getAmplifier());
            } else {
                stackData.putInt("silent_sun:soul_sever_last_stack_tick", livingTarget.tickCount);
                newAmplifier = Math.min(maxAmplifier, current.getAmplifier() + 1);
            }
        }
        if (livingTarget instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)livingTarget;
            if (this.isSharpenTrialActive() || CommonEvents.isSharpenSoulSeverMarked(player, this.getUUID())) {
                CommonEvents.markSharpenSoulSever(player, this.getUUID(), newAmplifier);
            }
        }
        livingTarget.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, durationTicks, newAmplifier, true, true));
        // 断魂结算统一到灭却之日账本（2026-09-01 用户裁决）：
        // 层值 = 当次 Boss 断魂值（baseX + soulSeverY，头衔推进提升）写入 Boss 账本
        //（recordSoulSeverLedgerBoss，与玩家账本独立挤最小层、各自时长，互不挤占）；
        // 结算由灭却之日 SoulSeverMobEffect 的受击 Post 统一触发（账本各层之和 + 9 bypass），
        // 不再走本侧的 soul_sever_bonus 单值结算（原 bonus NBT 写入移除）。
        long now = livingTarget.level().getGameTime();
        cn.autoforged.extinction_day_mod_1784441698.effect.SoulSeverMobEffect.recordSoulSeverLedgerBoss(
                livingTarget, this.getSoulSeverValue(), now, durationTicks);
    }

    public void markSoulSeverIfUnlocked(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        if (!this.seaSkySoulSeverUnlocked && !this.soulSeverHarvestUnlocked && !this.isSeaSkyGapActive()) {
            return;
        }
        this.applySoulSeverToTarget(target);
    }

    /**
     * 断魂结算已统一到灭却之日（2026-09-01 用户裁决）：Boss 挂账本（recordSoulSeverLedgerBoss）
     * 后，结算由灭却之日 SoulSeverMobEffect 的受击 Post 事件统一触发（双账本求和 + 9 bypass），
     * 本侧不再手动结算（原 settleSoulSeverPostDamage 已删除，调用点同步移除）。
     */

    private void tickSharpenTrial() {
        if (!this.isSharpenTrialActive()) {
            return;
        }
        // 2026-09-10（用户裁决 C3 / Q13）：断魂已在 1.7 被清除，一阶段剩余时间不再重挂。
        if (this.soulSeverRetiredInPhase1) {
            return;
        }
        if (this.tickCount % 20 == 0 && this.level() instanceof ServerLevel) {
            for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
                ServerPlayer player = this.getServerPlayer(id);
                if (player == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
                CommonEvents.markSharpenSoulSever(player, this.getUUID(), 0);
                player.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, 40, 0, true, true));
            }
            this.forEachMobOpponent(target -> target.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, 40, 0, true, true)));
        }
    }

    private void forceAdvanceToNextTitle() {
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        List<Component> list = titles;
        if (this.titleIndex >= titles.size() - 1) {
            return;
        }
        int oldTitleIndex = this.titleIndex;
        int oldPhase = this.phase;
        float maxHealth = this.getMaxHealth();
        float segment = this.titleSegment();
        float trigger = maxHealth - (float)(this.titleIndex + 1) * segment;
        float epsilon = 0.001f;
        ++this.titleIndex;
        this.titleLockTicks = this.titleLockDurationTicks();
        this.setHealth(trigger - epsilon);
        this.broadcastForceAdvanceCountdown();
        this.onTitleChanged(oldPhase, oldTitleIndex, this.phase, this.titleIndex);
    }

    private void broadcastForceAdvanceCountdown() {
        List<Component> titles;
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        List<Component> list = titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        if (this.titleIndex < titles.size()) {
            Component nextTitle = titles.get(this.titleIndex);
            SilentSunMod.LOGGER.info("Redios force-advance to next title phase={} titleIndex={} title={}", new Object[]{Integer.valueOf(this.phase), Integer.valueOf(this.titleIndex), nextTitle.getString()});
        }
    }

    private boolean isRootlessPureActive() {
        return this.phase == 1 && this.titleIndex == 4;
    }

    private void clearEffectsForRootlessPure() {
        MobEffectInstance enrage = this.getEffect(ModEffects.ENRAGE);
        this.removeAllEffects();
        if (enrage != null) {
            super.addEffect(enrage, this);
        }
    }

    private boolean isMirrorFaceActive() {
        return this.phase == 1 && this.titleIndex == 5;
    }

    private boolean isWhoseWishActive() {
        return this.phase == 1 && this.titleIndex == 7;
    }

    private void tickRootlessPure(ServerLevel serverLevel) {
        if (!this.isRootlessPureActive()) {
            this.rootlessPureTickCounter = 0;
            return;
        }
        if (++this.rootlessPureTickCounter >= 20) {
            this.rootlessPureTickCounter = 0;
            this.addSoulSeverY(1L);
        }
    }

    private void tickMirrorFace(ServerLevel serverLevel) {
        if (!this.isMirrorFaceActive()) {
            return;
        }
        long locked = this.mirrorFaceLockedSoulSever;
        if (locked <= 0L) {
            // 2026-09-11（代码审计 G17 #3 修复）：旧档无 SilentSunMirrorFaceLockedSoulSever 键时兜底，
            // 按当前断魂总量重新锁定（与进入 1.5 时的口径一致）。否则 locked=0 会每 tick 以 0 重挂，
            // 把参战玩家身上已累计的镜像面攻击力加成抹掉 → 1.5 的核心增益静默失效。
            this.mirrorFaceLockedSoulSever = locked = this.getSoulSeverValue();
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            CommonEvents.markMirrorFaceAttackBoost(player, this.getUUID(), locked);
        }
    }

    private void tickWhoseWish(ServerLevel serverLevel) {
        if (!this.isWhoseWishActive()) {
            this.whoseWishTicker = 0;
            return;
        }
        ++this.whoseWishTicker;
        if (this.whoseWishTicker % 20 != 0) {
            return;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            this.restorePlayerToFull(player);
        }
        this.forEachMobOpponent(target -> {
            target.setHealth(target.getMaxHealth());
            for (MobEffectInstance effect : new ArrayList<>(target.getActiveEffects())) {
                if (effect.getEffect().value().isBeneficial()) continue;
                target.removeEffect(effect.getEffect());
            }
        });
    }

    private void restorePlayerToFull(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);
        player.getFoodData().setExhaustion(0.0f);
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            if (effect.getEffect().value().isBeneficial()) continue;
            player.removeEffect(effect.getEffect());
        }
        // 2026-09-10（用户裁决 C3 / Q13）：这里连带清掉了玩家的断魂，裁定"该清除保持到一阶段结束"
        // → 置退场标记，并同时退掉 `CommonEvents.onPlayerTick` 每 tick 补挂所用的 NBT 标记
        //（不置标记的话，1.8/1.9 会把断魂原样补回来，裁定落空）。
        this.soulSeverRetiredInPhase1 = true;
        CommonEvents.clearSharpenSoulSever(player);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) continue;
            player.getCooldowns().removeCooldown(stack.getItem());
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.isEmpty()) continue;
            player.getCooldowns().removeCooldown(stack.getItem());
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.isEmpty()) continue;
            player.getCooldowns().removeCooldown(stack.getItem());
        }
    }

    void notifyWallAttack(ServerPlayer player) {
        int cooldown = Math.max(0, RediosRules.wallAttackNotifyCooldownTicks());
        int now = this.tickCount;
        long last = this.wallAttackLastNotifyTick.getOrDefault(player.getUUID(), Integer.MIN_VALUE).intValue();
        if ((long)now - last < (long)cooldown) {
            return;
        }
        this.wallAttackLastNotifyTick.put(player.getUUID(), now);
        player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.wall_attack").withStyle(ChatFormatting.DARK_PURPLE)));
    }

    public boolean isRootlessPureActiveNow() {
        return this.isRootlessPureActive() && this.bossState.isCombat();
    }

    public boolean isMirrorFaceActiveNow() {
        return this.isMirrorFaceActive() && this.bossState.isCombat();
    }

    private boolean isWishGrantActive() {
        return this.phase == 1 && this.titleIndex == 0;
    }

    private boolean isDustlessGoodActive() {
        return this.phase == 1 && this.titleIndex == 1;
    }

    private boolean isFirmFaithActive() {
        return this.phase == 1 && this.titleIndex == 2;
    }

    boolean isUnityPowerActive() {
        return this.phase == 1 && this.titleIndex == 3;
    }

    private void tickWishGrant(ServerLevel serverLevel) {
        if (this.isWishGrantActive()) {
            ++this.wishAbsorptionTicker;
            if (this.wishAbsorptionTicker % 100 == 0) {
                this.tickPhase1Resistance();
            }
        }
        if (this.colorlessUnlocked) {
            // 2026-09-02：1.2 护甲提升（抗性）作为 2.8 调用效果永久化——colorlessUnlocked 后持续至 Boss 死亡
            this.tickResistanceBoost();
        }
        if (!this.isWishGrantActive()) {
            this.wishRepairTicker = 0;
            this.wishAbsorptionTicker = 0;
            return;
        }
        ++this.wishRepairTicker;
        if (this.wishRepairTicker % 5 != 0) {
            return;
        }
        double percentPerSecond = SilentSunConfig.WISH_REPAIR_PERCENT_PER_SECOND.get();
        if (percentPerSecond <= 0.0) {
            return;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            this.repairAllCarriedItems(player, percentPerSecond, 5);
        }
    }

    private void tickFirmFaith(ServerLevel serverLevel) {
        if (!this.isFirmFaithActive()) {
            return;
        }
        this.applyOpponentEffect(() -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2, true, false));
    }

    private void tickUnityPower(ServerLevel serverLevel) {
        if (!this.isUnityPowerActive()) {
            return;
        }
        if (this.tickCount % 40 != 0) {
            return;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            int friendly;
            int level;
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level() || (level = Math.min(10, friendly = this.countNearbyFriendly(player, serverLevel))) <= 0) continue;
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, level - 1, true, false));
        }
        this.forEachMobOpponent(target -> {
            int friendly = serverLevel.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(20.0), e -> e.isAlive() && !(e instanceof Player) && !(e instanceof Monster) && target.distanceToSqr(e) <= 400.0).size();
            int level = Math.min(10, friendly);
            if (level > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, level - 1, true, false));
            }
        });
    }

    private int countNearbyFriendly(ServerPlayer player, ServerLevel serverLevel) {
        List entities = serverLevel.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(20.0), e -> e.isAlive() && !(e instanceof Player) && !(e instanceof Monster) && player.distanceToSqr(e) <= 400.0);
        int count = entities.size();
        for (UUID id : this.battleParticipants) {
            ServerPlayer other;
            if (id.equals(player.getUUID()) || this.expelledPlayers.contains(id) || (other = this.getServerPlayer(id)) == null || !other.isAlive() || !(player.distanceToSqr(other) <= 400.0)) continue;
            ++count;
        }
        return count;
    }

    private void tickDustlessGood(ServerLevel serverLevel) {
        if (this.isDustlessGoodActive()) {
            this.healBoostTicks = 600;
        } else if (this.healBoostTicks > 0) {
            --this.healBoostTicks;
        }
        if (!this.isDustlessGoodActive()) {
            return;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            List<Holder<MobEffect>> buffs = this.dustlessGoodBuffs.computeIfAbsent(id, ignored -> this.rollDustlessGoodBuffs());
            for (Holder<MobEffect> buff : buffs) {
                player.addEffect(new MobEffectInstance(buff, 40, 2, true, false));
            }
        }
        this.forEachMobOpponent(target -> {
            List<Holder<MobEffect>> buffs = this.dustlessGoodBuffs.computeIfAbsent(target.getUUID(), ignored -> this.rollDustlessGoodBuffs());
            for (Holder<MobEffect> buff : buffs) {
                target.addEffect(new MobEffectInstance(buff, 40, 2, true, false));
            }
        });
    }

    private List<Holder<MobEffect>> rollDustlessGoodBuffs() {
        ArrayList<Holder<MobEffect>> pool = new ArrayList<Holder<MobEffect>>(DUSTLESS_GOOD_BUFF_POOL);
        ArrayList<Holder<MobEffect>> selected = new ArrayList<Holder<MobEffect>>(4);
        for (int i = 0; i < 4 && !pool.isEmpty(); ++i) {
            int idx = this.random.nextInt(pool.size());
            selected.add(pool.remove(idx));
        }
        return selected;
    }

    private void repairAllCarriedItems(ServerPlayer player, double percentPerSecond, int intervalTicks) {
        Inventory inv = player.getInventory();
        for (ItemStack stack : inv.items) {
            this.repairItem(stack, percentPerSecond, intervalTicks);
        }
        for (ItemStack stack : inv.armor) {
            this.repairItem(stack, percentPerSecond, intervalTicks);
        }
        for (ItemStack stack : inv.offhand) {
            this.repairItem(stack, percentPerSecond, intervalTicks);
        }
    }

    private void repairItem(ItemStack stack, double percentPerSecond, int intervalTicks) {
        if (stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) {
            return;
        }
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) {
            return;
        }
        double repairDouble = (double)maxDamage * percentPerSecond * ((double)intervalTicks / 20.0);
        int repair = Math.max(1, (int)Math.round(repairDouble));
        int currentDamage = stack.getDamageValue();
        int nextDamage = Math.max(0, currentDamage - repair);
        if (nextDamage != currentDamage) {
            stack.setDamageValue(nextDamage);
        }
    }

    private void tickPhase1Resistance() {
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 2, true, false), this);
    }

    private void tickResistanceBoost() {
        int desired = 3;
        MobEffectInstance current = this.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (current != null) {
            desired = Math.max(desired, current.getAmplifier());
        }
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, desired, true, false), this);
    }

    boolean isSorrowToilActive() {
        // 2026-09-02：1.6 虚空光环作为 2.8 调用效果永久化——colorlessUnlocked（2.8 解锁，
        // 永久标志）后持续至 Boss 死亡（正推 2.9/逆推均生效）；原 P1 1.6 自身分支不动。
        return this.phase == 1 && this.titleIndex == 6 || this.colorlessUnlocked;
    }

    private void tickSorrowToilAura(ServerLevel serverLevel) {
        if (!this.isSorrowToilActive()) {
            return;
        }
        if (this.tickCount % 5 == 0) {
            double radius = 5.0;
            int particles = 14;
            double spin = (double)this.tickCount * 0.05;
            double midY = this.getY() + (double)this.getBbHeight() * 0.5;
            for (int i = 0; i < particles; ++i) {
                double angle = Math.PI * 2 * (double)i / (double)particles + spin;
                double x = this.getX() + Math.cos(angle) * radius;
                double z = this.getZ() + Math.sin(angle) * radius;
                double y = midY + (this.random.nextDouble() - 0.5) * 0.8;
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.0, 0.0, 0.0, 0.2);
            }
        }
        if (this.tickCount % 4 != 0) {
            return;
        }
        double r = 5.0;
        AABB box = this.getBoundingBox().inflate(r);
        List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e.distanceToSqr(this) <= r * r);
        for (LivingEntity living : entities) {
            // 2026-09-11 用户裁决（A08 = 以历史为准）：按历史 A10 裁定（`设计文稿合集.md:2432-2439`）
            // 过滤目标——Mode1：参战玩家 + 被参战玩家驯服的友好生物；Mode2：数据包白名单。
            if (!this.isAuraTarget(living)) continue;
            // 真伤光环：走 9bypass 改血（2026-09-01 用户裁决——完整 hurt + 差额 setHealth，
            // 与灭却之日 applyTrueDamage 同款，防第三方限伤/次数盾吞伤）
            this.applyTrueDamageAura(living, 1.0f);
            this.markSoulSeverIfUnlocked(living);
        }
        // 2026-09-11（A08 → 历史 A10 第二口径落地）：Mode1 下把半径内的**无主生物**按「脱战玩家同款
        // 推离」清场（§6.5：水平 <17 格推开、前方有墙改垂直上推）。此前这条**完全没有实现**。
        this.clearOwnerlessMobsFromAura(serverLevel);
        // 2026-09-10（用户裁决 C5）：光环自损改为走「虚空伤害免疫 + 记账」管线——
        // 由 DamagePipeline.stageSorrowToil 记录断魂 x 后取消伤害（Boss 不再被自己的光环真实扣血），
        // 与设计 §1.6 / §5.3「Boss 自身受虚空伤害免疫并记录断魂 x」一致。
        // 临时清零无敌帧：避免刚被玩家命中（invulnerableTime > 0）时自损被 hurt 入口的无敌帧短路吞掉。
        int savedAuraInvuln = this.invulnerableTime;
        this.invulnerableTime = 0;
        try {
            this.hurt(this.damageSources().fellOutOfWorld(), 1.0f);
        } finally {
            this.invulnerableTime = savedAuraInvuln;
        }
    }

    /**
     * A10 光环目标过滤（2026-09-11 依历史 A10 裁定落地，`设计文稿合集.md:2432-2439`）：
     * <ul>
     *   <li><b>Mode1</b>：参战玩家（合法、非创造/旁观/被逐出）+ **被参战玩家驯服的友好生物**
     *       （§3.5：它们同为合法伤害来源，故光环也伤它们）；其余（村民、无主生物等）不受伤，
     *       改由 {@link #clearOwnerlessMobsFromAura} 推离清场。</li>
     *   <li><b>Mode2</b>：走数据包白名单（{@code isValidAttackTarget}），不伤玩家与友好生物。</li>
     * </ul>
     * 取代原先的 `!playerOnlyMode() && isFriendlyEntity(...)` 短路（那套在 Mode1 下形同虚设，
     * 会连村民/宠物一起打，且不区分参战与否）。
     */
    private boolean isAuraTarget(LivingEntity living) {
        if (living == null || living == this || !living.isAlive()) {
            return false;
        }
        if (BossTargeting.playerOnlyMode()) {
            if (living instanceof Player player) {
                return BossTargeting.isValidAttackTarget(this, player);
            }
            if (living instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
                return this.battleParticipants.contains(ownable.getOwnerUUID());
            }
            return false;
        }
        return BossTargeting.isValidAttackTarget(this, living);
    }

    /**
     * A10 第二口径（历史原文：「无主生物较多时：按『脱战玩家同款推离』清场（见 §6.5）」）：
     * Mode1 下，1.6 竭力之悲光环半径内**不属于光环目标**的生物（村民、无主怪等）按同一套推离规则
     * 推开——水平距离 &lt; {@code push_away_distance}（默认 17）时推一次，前方 1 格有不可穿过方块则改
     * 垂直上推，避免卡墙/窒息。每 10 tick 执行一次，避免每 tick 重复推。
     */
    private void clearOwnerlessMobsFromAura(ServerLevel serverLevel) {
        if (!BossTargeting.playerOnlyMode() || this.tickCount % 10 != 0) {
            return;
        }
        double pushDist = RediosRules.pushAwayDistance();
        double pushDistSqr = pushDist * pushDist;
        double strength = RediosRules.pushAwayStrength();
        for (LivingEntity mob : serverLevel.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(pushDist),
                e -> e != this && e.isAlive() && !(e instanceof Player) && !this.isAuraTarget(e))) {
            double dx = mob.getX() - this.getX();
            double dz = mob.getZ() - this.getZ();
            double distSqr = dx * dx + dz * dz;
            if (distSqr >= pushDistSqr) {
                continue;
            }
            double len = Math.sqrt(distSqr);
            if (len < 1.0E-6) {
                dx = 1.0;
                dz = 0.0;
                len = 1.0;
            }
            double nx = dx / len;
            double nz = dz / len;
            if (this.isPushDirectionBlocked(serverLevel, mob, nx, nz)) {
                mob.push(0.0, 0.6, 0.0);
            } else {
                mob.push(nx * strength, 0.15, nz * strength);
            }
        }
    }

    // 2026-09-11（代码审计 G15 #5 修复）：原 isFriendlyEntity(...) 自 A10 重构后已无任何调用者
    // （替代者是 isAuraTarget），保留它只会诱使后续改动回到「连村民 / 宠物一起打」的旧行为 —— 已删除。
    private boolean isSharpenTrialActive() {
        return this.phase == 1 && this.titleIndex == 8;
    }

    boolean isVoidDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        if (source == this.damageSources().fellOutOfWorld()) {
            return true;
        }
        String msgId = source.getMsgId();
        return "outOfWorld".equals(msgId) || "out_of_world".equals(msgId) || "fell_out_of_world".equals(msgId);
    }

    boolean isAreaDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return true;
        }
        Entity directEntity = source.getDirectEntity();
        if (directEntity == null) {
            String msgId = source.getMsgId();
            return msgId.contains("explosion") || msgId.contains("magic") || msgId.contains("indirectMagic") || msgId.contains("dragonBreath") || msgId.contains("area_effect_cloud");
        }
        return false;
    }

    public boolean isSharpenTrialActiveNow() {
        return this.isSharpenTrialActive() && this.bossState.isCombat();
    }

    /** 2026-09-10（用户裁决 C3 / Q13）：一阶段断魂是否已退场（1.7 清除后 1.8/1.9 不再重挂）。 */
    public boolean isSoulSeverRetiredInPhase1() {
        return this.soulSeverRetiredInPhase1;
    }

    private void tickChaosRuinAura(ServerLevel serverLevel) {
        boolean showVisuals;
        showVisuals = this.isChaosRuinActive() || this.chaosRuinAbsoluteAttacks;
        if (!showVisuals) {
            return;
        }
        double radius = 5.0;
        if (this.tickCount % 5 == 0) {
            double angle;
            int i;
            int particles = 12;
            for (i = 0; i < particles; ++i) {
                angle = Math.PI * 2 * (double)i / (double)particles + (double)this.tickCount * 0.06;
                double x = this.getX() + Math.cos(angle) * radius;
                double z = this.getZ() + Math.sin(angle) * radius;
                double y = this.getY() + 0.3 + this.random.nextDouble() * 2.5;
                serverLevel.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 1, 0.0, 0.1, 0.0, 0.02);
            }
            for (i = 0; i < 6; ++i) {
                angle = this.random.nextDouble() * 2.0 * Math.PI;
                double r = this.random.nextDouble() * radius;
                double x = this.getX() + Math.cos(angle) * r;
                double z = this.getZ() + Math.sin(angle) * r;
                double y = this.getY() + 0.5 + this.random.nextDouble() * 2.0;
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (!this.isChaosRuinActive()) {
            return;
        }
        if (this.tickCount % 20 == 0) {
            AABB box = this.getBoundingBox().inflate(radius);
            List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != this && e.distanceToSqr(this) <= radius * radius);
            for (LivingEntity living : entities) {
                // 2026-09-11 用户裁决（A08 = 以历史为准）：同 1.6 光环，2.3 混沌之墟也用 A10 目标过滤。
                if (!this.isAuraTarget(living)) continue;
                float auraDamage = 3.0f;
                // 真伤光环：走 9bypass 改血（2026-09-01 用户裁决——完整 hurt + 差额 setHealth）
                this.applyTrueDamageAura(living, auraDamage);
                this.markSoulSeverIfUnlocked(living);
            }
        }
    }

    private void tickLowFpsTargetAdjustment(ServerLevel serverLevel) {
        if (this.battleParticipants.size() <= 1) {
            return;
        }        LivingEntity target = this.getTarget();
        if (!(target instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer currentTarget = (ServerPlayer)target;
        if (!this.isPlayerLowFps(currentTarget)) {
            return;
        }
        for (UUID id : this.battleParticipants) {
            ServerPlayer other;
            if (this.expelledPlayers.contains(id) || (other = this.getServerPlayer(id)) == null || other == currentTarget || !other.isAlive() || this.isPlayerLowFps(other)) continue;
            this.forceSetTarget((LivingEntity)other);
            return;
        }
    }

    private boolean isPlayerLowFps(ServerPlayer player) {
        if (player.connection == null) {
            return false;
        }
        int latency = player.connection.latency();
        int threshold = RediosRules.latencyThresholdMs();
        return threshold > 0 && latency > threshold;
    }

    private boolean areAllParticipantsLowFps(ServerLevel serverLevel) {
        if (this.battleParticipants.isEmpty()) {
            return false;
        }
        // 2026-09-11（代码审计 G15 #7 修复）：原实现把「玩家取不到（离线 / 已死）」与
        // 「玩家确实低帧」走同一条 continue ⇒ 参战者**全部离线**时循环走空并返回 true，
        // 被当成「全员低帧」触发延迟保护退场（无掉落）—— 即最后一名玩家掉线就等于被踢掉战斗。
        // 现分开处理：离线/已死者不计入，且至少要有 1 名在线参战者才可能返回 true。
        int lowFpsOnline = 0;
        for (UUID id : this.battleParticipants) {
            if (this.expelledPlayers.contains(id)) continue;
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || !player.isAlive()) continue;
            if (!this.isPlayerLowFps(player)) return false;
            ++lowFpsOnline;
        }
        return lowFpsOnline > 0;
    }

    private boolean tickFailsafe(ServerLevel serverLevel) {
        boolean allLowFps;
        if (this.bossState.isSafeWindow()) {
            if (this.failsafeActive) {
                this.failsafeActive = false;
                this.failsafeCountdownTicks = 0;
                this.bossEvent.setVisible(true);
                this.setNoAi(false);
            }
            return false;
        }
        long now = System.nanoTime();
        if (this.lastServerTickNanos > 0L) {
            long delta = now - this.lastServerTickNanos;
            if (delta >= 2000000000L) {
                ++this.tickSpikeCount;
            } else if (this.tickSpikeCount > 0) {
                --this.tickSpikeCount;
            }
        }
        this.lastServerTickNanos = now;
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        if (max > 0L) {
            long used = rt.totalMemory() - rt.freeMemory();
            double ratio = (double)used / (double)max;
            if (ratio >= 0.95) {
                ++this.highMemoryTicks;
            } else if (this.highMemoryTicks > 0) {
                --this.highMemoryTicks;
            }
        }
        boolean serverLag = this.tickSpikeCount >= 2 || this.highMemoryTicks >= 40;
        allLowFps = this.tickSpikeCount < 2 && this.highMemoryTicks < 40 && RediosRules.lagProtectionEnabled() && this.areAllParticipantsLowFps(serverLevel);
        if (!this.failsafeActive && (serverLag || allLowFps)) {
            this.failsafeActive = true;
            this.failsafeCountdownTicks = 100;
            this.bossEvent.setVisible(false);
            this.setTarget(null);
            this.getNavigation().stop();
            this.setNoAi(true);
            MutableComponent msg = Component.translatable("message.silent_sun.redios.failsafe").withStyle(ChatFormatting.GRAY);
            this.broadcastToParticipants(this.rediosSigned(msg));
        }
        if (this.failsafeActive) {
            this.bossEvent.setVisible(false);
            this.setTarget(null);
            this.getNavigation().stop();
            this.setNoAi(true);
            --this.failsafeCountdownTicks;
            if (this.failsafeCountdownTicks <= 0) {
                this.restoreDarkStarSpecialBlocks(serverLevel);
                this.bossLeaveNoLoot();
            }
            return true;
        }
        return false;
    }

    public boolean addEffect(MobEffectInstance effect, Entity source) {
        MobEffectInstance current;
        if (effect != null && effect.getEffect().value() == ModEffects.ENRAGE.get() && (current = this.getEffect(ModEffects.ENRAGE)) != null && current.getAmplifier() >= 9) {
            if (this.colorlessUnlocked) {
                this.triggerWeaknessCurse();
            }
            MobEffectInstance clamped = new MobEffectInstance(ModEffects.ENRAGE, Math.max(current.getDuration(), effect.getDuration()), 9, effect.isAmbient(), effect.isVisible());
            return super.addEffect(clamped, source);
        }
        if (this.isDebuffImmune() && effect != null && !effect.getEffect().value().isBeneficial()) {
            return false;
        }
        return super.addEffect(effect, source);
    }

    public boolean removeEffect(Holder<MobEffect> effect) {
        if ((this.ashDawnUnlocked || this.colorlessUnlocked) && effect != null && (effect.value()).isBeneficial()) {
            return false;
        }
        return super.removeEffect(effect);
    }

    /**
     * 防打穿（2026-09-09）：外部 removeAllEffects() 清不掉 Boss 常驻 Buff——
     * 激怒（Enrage）与 2.8/2.9 colorless 永久的力量/迅捷/恢复在清除后立即重挂
     * （与 clearAllExternalEffects 同一保护集）。合法离场/结算走各自既有流程，不受影响。
     */
    @Override
    public boolean removeAllEffects() {
        MobEffectInstance enrage = this.getEffect((Holder<MobEffect>)ModEffects.ENRAGE);
        MobEffectInstance permanentBoost = this.colorlessUnlocked ? this.getEffect(MobEffects.DAMAGE_BOOST) : null;
        MobEffectInstance permanentSpeed = this.colorlessUnlocked ? this.getEffect(MobEffects.MOVEMENT_SPEED) : null;
        MobEffectInstance permanentRegen = this.colorlessUnlocked ? this.getEffect(MobEffects.REGENERATION) : null;
        boolean cleared = super.removeAllEffects();
        if (enrage != null) {
            super.addEffect(enrage, this);
        }
        if (permanentBoost != null) {
            super.addEffect(permanentBoost, this);
        }
        if (permanentSpeed != null) {
            super.addEffect(permanentSpeed, this);
        }
        if (permanentRegen != null) {
            super.addEffect(permanentRegen, this);
        }
        return cleared;
    }

    protected void customServerAiStep() {
        if (this.bossState.isVoteOrTransition()) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            return;
        }
        super.customServerAiStep();
    }

    public boolean canFreeze() {
        return false;
    }

    public float getBlockSpeedFactor() {
        return 1.0f;
    }

    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
    }

    public void readAdditionalSaveData(CompoundTag tag) {
        if (!(tag.getFloat("Health") > 0.0f)) {
            tag.putFloat("Health", 1.0f);
        }
        super.readAdditionalSaveData(tag);
        this.restoreStateFromNbt(tag);
    }

    private void restoreStateFromNbt(CompoundTag tag) {
        this.phase = tag.getInt("SilentSunPhase");
        this.settlementDone = tag.getBoolean("SilentSunSettlementDone");
        this.titleIndex = tag.getInt("SilentSunTitleIndex");
        this.titleLockTicks = tag.getInt("SilentSunTitleLock");
        this.transitionTicks = tag.getInt("SilentSunTransition");
        this.restoreBossState(tag.getInt("SilentSunBossState"), this.phase, this.transitionTicks);
        this.soulSeverY = tag.getLong("SilentSunSoulSeverY");
        // 断魂数值防篡改：NBT 可能被 /data remove 或外部清空，账本里保留单调递增备份，
        // 读取时取两者较大值，防止数值被回退清零。
        if (this.level() instanceof ServerLevel serverLevel) {
            long ledgerSoulSeverY = RediosBattleData.get(serverLevel).getSoulSeverY(this.getUUID());
            if (ledgerSoulSeverY > this.soulSeverY) {
                this.soulSeverY = ledgerSoulSeverY;
            }
        }
        this.ashDawnUnlocked = tag.getBoolean("SilentSunAshDawnUnlocked");
        this.darkStarFired = tag.getBoolean("SilentSunDarkStarFired");
        this.darkStarFlightUnlocked = tag.getBoolean("SilentSunDarkStarFlight");
        this.darkStarBedrockRepaired = tag.getBoolean("SilentSunDarkStarBedrock");
        this.darkStarBlastOrigin = tag.contains("SilentSunDarkStarBlastX") ? new BlockPos(tag.getInt("SilentSunDarkStarBlastX"), tag.getInt("SilentSunDarkStarBlastY"), tag.getInt("SilentSunDarkStarBlastZ")) : null;
        this.darkStarBlastNextIndex = tag.getInt("SilentSunDarkStarBlastNext");
        this.darkStarRestoreBlocks.clear();
        ListTag darkStarRestoreList = tag.getList("SilentSunDarkStarRestore", 10);
        for (int i = 0; i < darkStarRestoreList.size(); ++i) {
            CompoundTag entry = darkStarRestoreList.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"));
            this.darkStarRestoreBlocks.put(pos, entry);
        }
        this.blackSunTriggered = tag.getBoolean("SilentSunBlackSunTriggered");
        this.colorlessUnlocked = tag.getBoolean("SilentSunColorlessUnlocked");
        this.voidAllThingsUnlocked = tag.getBoolean("SilentSunVoidAllThingsUnlocked");
        this.colorlessChallengeTicks = tag.getInt("SilentSunColorlessChallengeTicks");
        this.noResurrection = tag.getBoolean("SilentSunNoResurrection");
        this.awaitingNoResurrectionPhase2 = tag.getBoolean("SilentSunAwaitingNoResurrectionPhase2");
        // 2026-09-10 作者裁决「保持现状」（docs/项目彻查报告-2026-09-10.md:417「A1 noResurrection 读后覆写」）
        // —— 下面两行清零是既定口径，不是笔误。考古附证：enableNoResurrection() 在
        // silent_sun-0.0.17-historical.jar 与 0.0.24-historical.jar 中**均无任何 invoke 调用点**（javap -c 全类扫描）
        // ⇒ 该功能属「①从未接线」而非回归；设计稿 §2（L94/L147）的「无复活直入 P2」尚未定义触发条件，
        // 接线须另立批次（见 docs/实现计划-2026-09-11-C-1审计中危批次.md §4.1 D-3）。
        this.noResurrection = false;
        this.awaitingNoResurrectionPhase2 = false;
        this.pendingCommandLeave = tag.getBoolean("SilentSunPendingCommandLeave");
        this.bossDataVersion = tag.contains("SilentSunDataVersion") ? tag.getInt("SilentSunDataVersion") : 0;
        this.blackSunUnlocked = tag.getBoolean("SilentSunBlackSunUnlocked");
        this.weaknessCurseActive = tag.getBoolean("SilentSunWeaknessCurseActive");
        this.enrageStackingUnlocked = tag.getBoolean("SilentSunEnrageStackingUnlocked");
        this.attackRandomized = tag.contains("SilentSunAttackRandomized") ? tag.getBoolean("SilentSunAttackRandomized") : this.phase == 1 && this.titleIndex == 8;
        this.attackSpecialized = tag.getBoolean("SilentSunAttackSpecialized");
        // P2 锁血解除状态持久化（2026-09-01 修复）：锁血解除后重启若丢失该位，
        // setHealth 的 phase2Last 保底复活 → Boss 无法被击杀、下次命中被拉回 PENDING
        // 再走一整轮锁血。旧存档无此键 → 按「非 PENDING 且 COMBAT 最后头衔」推导为已解除。
        this.pendingLockReleased = tag.contains("SilentSunPendingLockReleased")
            ? tag.getBoolean("SilentSunPendingLockReleased")
            : this.bossState == BossState.PHASE2_COMBAT && this.titleIndex == RediosEntity.PHASE2_TITLES.size() - 1;
        // 2026-09-11（S3）：投票状态落盘/读回（写端见 addAdditionalSaveData 的同名键）。
        this.phase2ChoiceTimeoutTicks = tag.getInt("SilentSunVoteTimeout");
        this.phase2Choices.clear();
        ListTag voteList = tag.getList("SilentSunVoteChoices", 10);
        for (int vi = 0; vi < voteList.size(); ++vi) {
            CompoundTag voteEntry = voteList.getCompound(vi);
            if (!voteEntry.hasUUID("Id")) continue;
            this.phase2Choices.put(voteEntry.getUUID("Id"),
                voteEntry.contains("Yes") ? Boolean.valueOf(voteEntry.getBoolean("Yes")) : null);
        }
        // 兜底：投票态但计时为 0（旧档无键 / 被第三方清空）→ 补一整个 30 秒窗口，
        // 避免"投票被整段跳过、全员的下次被吞"（P2 投票默认窗口 = 600 tick，与 beginPhase2Choice 一致）。
        if (this.bossState == BossState.PHASE1_VOTE && this.phase2ChoiceTimeoutTicks <= 0) {
            this.phase2ChoiceTimeoutTicks = 600;
        }
        this.damageTypeTotals.clear();
        ListTag dtTotals = tag.getList("SilentSunDamageTypeTotals", 10);
        for (int di = 0; di < dtTotals.size(); ++di) {
            CompoundTag entry = dtTotals.getCompound(di);
            ResourceLocation key = ResourceLocation.tryParse((String)entry.getString("Key"));
            if (key == null) continue;
            this.damageTypeTotals.put(key, entry.getDouble("Total"));
        }
        this.playerNetDamageTotals.clear();
        ListTag netTotals = tag.getList("SilentSunPlayerNetDamage", 10);
        for (int ni = 0; ni < netTotals.size(); ++ni) {
            CompoundTag entry = netTotals.getCompound(ni);
            if (!entry.hasUUID("Id")) continue;
            this.playerNetDamageTotals.put(entry.getUUID("Id"), entry.getDouble("Total"));
        }
        if (this.attackSpecialized) {
            ResourceLocation specKey;
            String specId = tag.getString("SilentSunSpecializedDamageType");
            ResourceLocation resourceLocation = specKey = specId.isEmpty() ? null : ResourceLocation.tryParse((String)specId);
            if (specKey != null && this.level() != null) {
                Registry<DamageType> registry = this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
                this.specializedDamageType = registry.getHolder(specKey).orElse(null);
            }
            if (this.specializedDamageType == null) {
                this.specializedDamageType = this.findSpecializedDamageType();
            }
        }
        this.weaponWeakpointSlowTicks = tag.getInt("SilentSunWeakpointSlow");
        this.weaponWeakpointCooldownTicks = tag.getInt("SilentSunWeakpointCooldown");
        this.seaSkySoulSeverUnlocked = tag.getBoolean("SilentSunSeaSkySoulSeverUnlocked");
        this.soulSeverHarvestUnlocked = tag.getBoolean("SilentSunSoulSeverHarvestUnlocked");
        this.uncontrolledSprintUnlocked = tag.getBoolean("SilentSunUncontrolledSprintUnlocked");
        this.guardUnlocked = tag.getBoolean("SilentSunGuardUnlocked");
        this.chaosRuinAbsoluteAttacks = tag.getBoolean("SilentSunChaosRuinAbsoluteAttacks");
        this.soulSeverRetiredInPhase1 = tag.getBoolean("SilentSunSoulSeverRetiredInPhase1");
        this.dodgeChance = tag.contains("SilentSunDodgeChance") ? (double)tag.getFloat("SilentSunDodgeChance") : 0.0;
        this.battleStartGameTime = tag.contains("SilentSunBattleStartTime") ? tag.getLong("SilentSunBattleStartTime") : -1L;
        // 2026-09-11 审计修复（G14 #3）：动态减伤时间基准改用 gameTime；缺键回落 -1（未开战）
        this.dynamicReductionStartGameTime = tag.contains("SilentSunDynamicReductionStart") ? tag.getLong("SilentSunDynamicReductionStart") : -1L;
        // 2026-09-11 审计修复（G13 #10 / G14 #7）：锁血结束后 5 秒禁回血缓冲
        this.titleLockGraceTicks = tag.getInt("SilentSunTitleLockGrace");
        // 2026-09-11 审计修复（G13 #2）：转场总时长（缺键读回 0 → transitionTotal() 用配置值兜底）
        this.transitionTotalTicks = tag.getInt("SilentSunTransitionTotal");
        // 2026-09-11 审计修复（G17 #3）：1.5「镜像面」锁定的断魂总量
        this.mirrorFaceLockedSoulSever = tag.getLong("SilentSunMirrorFaceLockedSoulSever");
        this.initialParticipants.clear();
        ListTag initialList = tag.getList("SilentSunInitialParticipants", 10);
        for (int i2 = 0; i2 < initialList.size(); ++i2) {
            CompoundTag entry = initialList.getCompound(i2);
            if (!entry.hasUUID("Id")) continue;
            this.initialParticipants.add(entry.getUUID("Id"));
        }
        this.expelledPlayers.clear();
        ListTag expelledList = tag.getList("SilentSunExpelledPlayers", 10);
        for (int i3 = 0; i3 < expelledList.size(); ++i3) {
            CompoundTag entry = expelledList.getCompound(i3);
            if (!entry.hasUUID("Id")) continue;
            this.expelledPlayers.add(entry.getUUID("Id"));
        }
        this.twilightExpelled.clear();
        ListTag twilightExpelledList = tag.getList("SilentSunTwilightExpelled", 10);
        for (int i4 = 0; i4 < twilightExpelledList.size(); ++i4) {
            CompoundTag entry = twilightExpelledList.getCompound(i4);
            if (entry.hasUUID("Id")) {
                this.twilightExpelled.add(entry.getUUID("Id"));
            }
        }
        this.bossOutlineEnabled = tag.getBoolean("SilentSunBossOutlineEnabled");
        this.weapons.readAdditionalSaveData(tag);
        this.voidTeleportCooldown = tag.getInt("SilentSunVoidTeleportCooldown");
        this.heightFlightMode = tag.getBoolean("SilentSunHeightFlightMode");
        this.anticheat.readAdditionalSaveData(tag);
        // 回场/读档复核创造离场窗口（2026-09-10 实测修复）：不继承残留倒计时，否则被第三方删除
        // 的 Boss 一旦重建回场，会因冻在删除期的计时器立刻"创造模式离场"（无掉落 + 设冷却）。
        this.anticheat.rearmCreativeLeaveAfterRestore();
        this.starfallSalvoCooldownTicks = tag.contains("SilentSunStarfallCooldown") ? tag.getInt("SilentSunStarfallCooldown") : (SilentSunConfig.STARFALL_SALVO_INTERVAL_TICKS.get()).intValue();
        this.battleParticipants.clear();
        ListTag battleList = tag.getList("SilentSunBattleParticipants", 10);
        for (int bi = 0; bi < battleList.size(); ++bi) {
            CompoundTag entry = battleList.getCompound(bi);
            if (!entry.hasUUID("Id")) continue;
            this.battleParticipants.add(entry.getUUID("Id"));
        }
        // 2026-09-10（批次 2.14 / B5）：与写入侧对称——恢复斗蛐蛐模式的参战生物（旧档无键 → 空集/false）。
        this.mobParticipants.clear();
        ListTag mobList = tag.getList("SilentSunMobParticipants", 10);
        for (int mi = 0; mi < mobList.size(); ++mi) {
            CompoundTag mobEntry = mobList.getCompound(mi);
            if (!mobEntry.hasUUID("Id")) continue;
            this.mobParticipants.add(mobEntry.getUUID("Id"));
        }
        this.mobBattleEngaged = tag.getBoolean("SilentSunMobBattleEngaged");
        if (tag.contains("SilentSunAnchorX")) {
            this.battleAnchorPos = new BlockPos(tag.getInt("SilentSunAnchorX"), tag.getInt("SilentSunAnchorY"), tag.getInt("SilentSunAnchorZ"));
            this.battleAnchorDim = tag.contains("SilentSunAnchorDim") ? ResourceLocation.tryParse((String)tag.getString("SilentSunAnchorDim")) : null;
        } else {
            this.battleAnchorPos = null;
            this.battleAnchorDim = null;
        }
        this.syncFlagsFromBooleans();
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SilentSunSettlementDone", this.settlementDone);
        tag.putInt("SilentSunPhase", this.phase);
        tag.putInt("SilentSunTitleIndex", this.titleIndex);
        tag.putInt("SilentSunTitleLock", this.titleLockTicks);
        tag.putInt("SilentSunTransition", this.transitionTicks);
        // 2026-09-11 实测修复（S3）：投票（1.9 继续/下次）此前**完全不落盘**——
        // 投票中区块卸载重载、或被第三方删除后回场，`phase2ChoiceTimeoutTicks` 读回 0 且
        // `phase2Choices` 为空 → VOTE tick 的首个 `--` 就 `<= 0` → `finishPhase2Choice()`
        // total=0 → 直接 startTransition，**投票被整段跳过、全员的「下次」被吞掉**。
        // 写入实体 NBT 后，回场快照（snapshotUnlockFlags 整体搬运）也会一并带上。
        tag.putInt("SilentSunVoteTimeout", this.phase2ChoiceTimeoutTicks);
        ListTag voteList = new ListTag();
        for (Map.Entry<UUID, Boolean> e : this.phase2Choices.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", e.getKey());
            if (e.getValue() != null) {
                entry.putBoolean("Yes", e.getValue().booleanValue());
            }
            voteList.add(entry);
        }
        tag.put("SilentSunVoteChoices", voteList);
        tag.putInt("SilentSunBossState", this.bossState.ordinal());
        tag.putLong("SilentSunSoulSeverY", this.soulSeverY);
        tag.putBoolean("SilentSunAshDawnUnlocked", this.ashDawnUnlocked);
        tag.putBoolean("SilentSunDarkStarFired", this.darkStarFired);
        tag.putBoolean("SilentSunDarkStarFlight", this.darkStarFlightUnlocked);
        tag.putBoolean("SilentSunDarkStarBedrock", this.darkStarBedrockRepaired);
        if (this.darkStarBlastOrigin != null) {
            tag.putInt("SilentSunDarkStarBlastX", this.darkStarBlastOrigin.getX());
            tag.putInt("SilentSunDarkStarBlastY", this.darkStarBlastOrigin.getY());
            tag.putInt("SilentSunDarkStarBlastZ", this.darkStarBlastOrigin.getZ());
            tag.putInt("SilentSunDarkStarBlastNext", this.darkStarBlastNextIndex);
        }
        ListTag darkStarRestoreList = new ListTag();
        for (CompoundTag compoundTag : this.darkStarRestoreBlocks.values()) {
            darkStarRestoreList.add(compoundTag.copy());
        }
        tag.put("SilentSunDarkStarRestore", (Tag)darkStarRestoreList);
        tag.putBoolean("SilentSunBlackSunTriggered", this.blackSunTriggered);
        tag.putBoolean("SilentSunColorlessUnlocked", this.colorlessUnlocked);
        tag.putBoolean("SilentSunVoidAllThingsUnlocked", this.voidAllThingsUnlocked);
        tag.putInt("SilentSunColorlessChallengeTicks", this.colorlessChallengeTicks);
        tag.putBoolean("SilentSunNoResurrection", this.noResurrection);
        tag.putBoolean("SilentSunPendingLockReleased", this.pendingLockReleased);
        tag.putBoolean("SilentSunAwaitingNoResurrectionPhase2", this.awaitingNoResurrectionPhase2);
        tag.putBoolean("SilentSunPendingCommandLeave", this.pendingCommandLeave);
        tag.putInt("SilentSunDataVersion", this.bossDataVersion);
        tag.putBoolean("SilentSunBlackSunUnlocked", this.blackSunUnlocked);
        tag.putBoolean("SilentSunWeaknessCurseActive", this.weaknessCurseActive);
        tag.putBoolean("SilentSunEnrageStackingUnlocked", this.enrageStackingUnlocked);
        tag.putBoolean("SilentSunAttackRandomized", this.attackRandomized);
        tag.putBoolean("SilentSunAttackSpecialized", this.attackSpecialized);
        if (this.specializedDamageType != null && this.specializedDamageType.getKey() != null) {
            tag.putString("SilentSunSpecializedDamageType", this.specializedDamageType.getKey().toString());
        }
        ListTag dtTotals = new ListTag();
        for (Map.Entry<ResourceLocation, Double> entry : this.damageTypeTotals.entrySet()) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("Key", entry.getKey().toString());
            compoundTag.putDouble("Total", entry.getValue().doubleValue());
            dtTotals.add(compoundTag);
        }
        tag.put("SilentSunDamageTypeTotals", (Tag)dtTotals);
        ListTag netTotals = new ListTag();
        for (Map.Entry<UUID, Double> entry : this.playerNetDamageTotals.entrySet()) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putUUID("Id", entry.getKey());
            compoundTag.putDouble("Total", entry.getValue().doubleValue());
            netTotals.add(compoundTag);
        }
        tag.put("SilentSunPlayerNetDamage", (Tag)netTotals);
        tag.putInt("SilentSunWeakpointSlow", this.weaponWeakpointSlowTicks);
        tag.putInt("SilentSunWeakpointCooldown", this.weaponWeakpointCooldownTicks);
        tag.putBoolean("SilentSunSeaSkySoulSeverUnlocked", this.seaSkySoulSeverUnlocked);
        tag.putBoolean("SilentSunSoulSeverHarvestUnlocked", this.soulSeverHarvestUnlocked);
        tag.putBoolean("SilentSunUncontrolledSprintUnlocked", this.uncontrolledSprintUnlocked);
        tag.putBoolean("SilentSunGuardUnlocked", this.guardUnlocked);
        tag.putBoolean("SilentSunChaosRuinAbsoluteAttacks", this.chaosRuinAbsoluteAttacks);
        tag.putBoolean("SilentSunSoulSeverRetiredInPhase1", this.soulSeverRetiredInPhase1);
        tag.putFloat("SilentSunDodgeChance", (float)this.dodgeChance);
        // 2026-09-11 审计修复：4 个此前未落盘的状态字段（见各自字段注释）。
        // 它们会随 snapshotUnlockFlags（addAdditionalSaveData 全量 - LEDGER_CARRIED_KEYS）自动进回场快照。
        tag.putLong("SilentSunDynamicReductionStart", this.dynamicReductionStartGameTime);
        tag.putInt("SilentSunTitleLockGrace", this.titleLockGraceTicks);
        tag.putInt("SilentSunTransitionTotal", this.transitionTotalTicks);
        tag.putLong("SilentSunMirrorFaceLockedSoulSever", this.mirrorFaceLockedSoulSever);
        if (this.battleStartGameTime >= 0L) {
            tag.putLong("SilentSunBattleStartTime", this.battleStartGameTime);
        }
        ListTag listTag = new ListTag();
        for (UUID uUID : this.initialParticipants) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putUUID("Id", uUID);
            listTag.add(compoundTag);
        }
        tag.put("SilentSunInitialParticipants", (Tag)listTag);
        ListTag listTag2 = new ListTag();
        for (UUID uUID : this.expelledPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", uUID);
            listTag2.add(entry);
        }
        tag.put("SilentSunExpelledPlayers", (Tag)listTag2);
        ListTag listTag3 = new ListTag();
        for (UUID id : this.twilightExpelled) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            listTag3.add(entry);
        }
        tag.put("SilentSunTwilightExpelled", (Tag)listTag3);
        tag.putBoolean("SilentSunBossOutlineEnabled", this.bossOutlineEnabled);
        this.weapons.addAdditionalSaveData(tag);
        tag.putInt("SilentSunVoidTeleportCooldown", this.voidTeleportCooldown);
        tag.putBoolean("SilentSunHeightFlightMode", this.heightFlightMode);
        this.anticheat.addAdditionalSaveData(tag);
        tag.putInt("SilentSunStarfallCooldown", this.starfallSalvoCooldownTicks);
        if (this.battleAnchorPos != null) {
            tag.putInt("SilentSunAnchorX", this.battleAnchorPos.getX());
            tag.putInt("SilentSunAnchorY", this.battleAnchorPos.getY());
            tag.putInt("SilentSunAnchorZ", this.battleAnchorPos.getZ());
            if (this.battleAnchorDim != null) {
                tag.putString("SilentSunAnchorDim", this.battleAnchorDim.toString());
            }
        }
        ListTag listTag4 = new ListTag();
        for (UUID id : this.battleParticipants) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            listTag4.add(entry);
        }
        tag.put("SilentSunBattleParticipants", (Tag)listTag4);
        // 2026-09-10（批次 2.14 / B5）：斗蛐蛐模式的参战生物一并落盘。
        // 原先只存 battleParticipants，区块重载/存档重载后 mobParticipants 为空
        // → getActiveMobParticipantCount()/退场判定与 2.0 的 mob 分支都会误判。
        ListTag mobList = new ListTag();
        for (UUID id : this.mobParticipants) {
            CompoundTag mobTag = new CompoundTag();
            mobTag.putUUID("Id", id);
            mobList.add(mobTag);
        }
        tag.put("SilentSunMobParticipants", (Tag)mobList);
        tag.putBoolean("SilentSunMobBattleEngaged", this.mobBattleEngaged);
    }

    private long getSoulSeverValue() {
        return (long)(SilentSunConfig.SOUL_SEVER_BASE_X.get()).intValue() + this.soulSeverY;
    }

    public void addSoulSeverY(long delta) {
        long next;
        try {
            next = Math.addExact(this.soulSeverY, delta);
        }
        catch (ArithmeticException e) {
            next = delta >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        long oldY = this.soulSeverY;
        this.soulSeverY = next;
        long threshold = (SilentSunConfig.SOUL_SEVER_Y_WARNING_THRESHOLD.get()).intValue();
        if (oldY <= threshold && next > threshold && this.level() instanceof ServerLevel) {
            MutableComponent warning = Component.translatable("message.silent_sun.redios.soul_sever_overflow").withStyle(ChatFormatting.DARK_RED);
            for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
                ServerPlayer player = this.getServerPlayer(id);
                if (player == null || this.expelledPlayers.contains(id) || !player.isAlive()) continue;
                player.sendSystemMessage(this.rediosSigned(warning));
            }
        }
    }

    public void enableNoResurrection() {
        this.noResurrection = true;
    }

    public boolean isBladeAttackAllowed() {
        if (this.isWhoseWishActive()) {
            return false;
        }
        // 2026-09-04：pending 期间 Boss 照常战斗（拔刀剑攻击不中断）——pending 唯一目的只是
        // 锁 1 血防击杀误判，故 COMBAT 与两个 PENDING 状态均允许拔刀剑攻击。
        return this.bossState.isCombat()
            || this.bossState == BossState.PHASE1_PENDING
            || this.bossState == BossState.PHASE2_PENDING;
    }

    public boolean isBladeModeActive() {
        return this.weapons.isBladeModeActive();
    }

    public DamageSource randomAttackSource() {
        Holder<DamageType> holder = this.resolveAttackDamageHolder();
        if (holder == null) {
            return null;
        }
        return new DamageSource(holder, this, this);
    }

    private Holder<DamageType> resolveAttackDamageHolder() {
        if (this.attackSpecialized && this.specializedDamageType != null) {
            return this.specializedDamageType;
        }
        if (!this.attackRandomized) {
            return null;
        }
        Registry<DamageType> registry = this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        List<Holder<DamageType>> holders = registry.holders().filter(h -> !h.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !h.is(DamageTypeTags.BYPASSES_EFFECTS)).filter(h -> !h.is(DamageTypeTags.WITCH_RESISTANT_TO)).collect(Collectors.toList());
        if (holders.isEmpty()) {
            return null;
        }
        return holders.get(this.random.nextInt(holders.size()));
    }

    /**
     * 构建攻击 DamageSource（合并冗余，M2）：默认 rediosAttack，特化/随机类型时用解析出的 holder。
     * 衔接：doHurtTarget 分支 A/B 共用；见设计稿 §3.1（1.8 砺锋随机化 / 2.7 弱点特化）。
     * 2026-09-08 用户裁决：2.7 弱点特化改为「单次攻击视为包内全部已注册攻击属性」——
     * 一次全额命中且带全穿透标签（redios_spectrum，同断魂 9bypass 语义），优先于 1.8 随机化，永久生效。
     */
    private DamageSource buildAttackSource() {
        if (this.attackSpecialized) {
            return ModDamageTypes.rediosSpectrum(this.level(), this);
        }
        DamageSource src = ModDamageTypes.rediosAttack(this.level(), this);
        Holder<DamageType> dmgHolder = this.resolveAttackDamageHolder();
        if (dmgHolder != null) {
            // 2026-09-11（代码审计 G15 #3 修复）：同上 —— 补回攻击者实体
            // （影响仇恨归因与以 source.getEntity() 为判据的闪避分支，详见 doHurtMultiPartTarget 处）。
            src = new DamageSource(dmgHolder, this, this);
        }
        return src;
    }

    private void recordPlayerDamageType(DamageSource source, float amount) {
        if (source == null || amount <= 0.0f) {
            return;
        }
        Holder type = source.typeHolder();
        if (type == null || type.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || type.is(DamageTypeTags.BYPASSES_EFFECTS) || type.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return;
        }
        ResourceKey typeKey = type.getKey();
        if (typeKey == null) {
            return;
        }
        ResourceLocation key = typeKey.location();
        this.damageTypeTotals.merge(key, Double.valueOf(amount), Double::sum);
    }

    private void recordPlayerNetDamage(Player player, DamageSource source, float amount) {
        if (player == null || amount <= 0.0f) {
            return;
        }
        UUID id = player.getUUID();
        this.playerNetDamageTotals.merge(id, Double.valueOf(amount), Double::sum);
        if (this.isAreaDamage(source)) {
            this.playerAreaDamageTick.put(id, Integer.valueOf(this.tickCount));
        }
    }

    private double hatredOf(LivingEntity target) {
        if (target instanceof Player player) {
            // 设计 §582：离线 / 死亡 / 被逐出「立即清零」——读取侧即时归零
            //（写入表每 20 tick 物理清理一次，语义上等效于立即生效）。
            if (!this.isThreatTrackable(player.getUUID())) {
                return 0.0;
            }
            Double d = this.playerNetDamageTotals.get(player.getUUID());
            return d == null ? 0.0 : d.doubleValue();
        }
        return 0.0;
    }

    /** 该玩家是否仍在威胁值记账范围内（离线 / 死亡 / 被逐出 → 不再计）。 */
    private boolean isThreatTrackable(UUID id) {
        if (this.expelledPlayers.contains(id)) {
            return false;
        }
        ServerPlayer player = this.getServerPlayer(id);
        return player != null && player.isAlive();
    }

    /** 威胁值衰减与清理（设计 §582：停止造成伤害后每秒 -5%，每 20 tick 一次）。 */
    private void tickThreatSystem() {
        if (this.targetSwitchCooldownTicks > 0) {
            --this.targetSwitchCooldownTicks;
        }
        if (this.tickCount % THREAT_DECAY_INTERVAL_TICKS != 0 || this.playerNetDamageTotals.isEmpty()) {
            return;
        }
        this.playerNetDamageTotals.entrySet().removeIf(entry -> !this.isThreatTrackable(entry.getKey()));
        this.playerNetDamageTotals.replaceAll((id, total) -> total * THREAT_DECAY_FACTOR);
    }

    /** 当前威胁值最高且仍可攻击的参战玩家；无人有威胁值时返回 null（回退到就近索敌）。 */
    private LivingEntity pickHighestThreatTarget() {
        LivingEntity best = null;
        double bestHatred = 0.0;
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator()
                    || player.isCreative() || !player.isAlive() || player.level() != this.level()
                    || !BossTargeting.isValidAttackTarget(this, player)) continue;
            double hatred = this.hatredOf(player);
            if (hatred > bestHatred) {
                bestHatred = hatred;
                best = player;
            }
        }
        return best;
    }

    /**
     * 威胁值索敌闸门（设计 §583）：新目标威胁 ≥ 当前目标 × 1.3 才切换，切换后 40 tick 冷却；
     * 当前目标失效（死亡 / 脱战 / 被逐出）或威胁被反超时不设限；首次锁敌也取威胁最高者。
     * <p>
     * 原版 {@code NearestAttackableTargetGoal}（就近索敌）退化为"提案方"，本闸门决定是否接受——
     * 因此「就近」只在无人有威胁值时（例如开战瞬间）才生效。内部机制需强制锁敌时走
     * {@link #forceSetTarget(LivingEntity)} 绕过本闸门。
     */
    @Override
    public void setTarget(LivingEntity target) {
        LivingEntity current = this.getTarget();
        if (target != null && target != current) {
            if (current == null) {
                LivingEntity highest = this.pickHighestThreatTarget();
                if (highest != null) {
                    target = highest;
                }
            } else if (!this.shouldSwitchTarget(current, target)) {
                return;
            }
            if (target != current) {
                this.targetSwitchCooldownTicks = TARGET_SWITCH_COOLDOWN_TICKS;
            }
        }
        super.setTarget(target);
    }

    /** 是否允许从 {@code current} 切到 {@code next}（设计 §583 的阈值 + 冷却两条规则）。 */
    private boolean shouldSwitchTarget(LivingEntity current, LivingEntity next) {
        if (!BossTargeting.isValidAttackTarget(this, current)) {
            return true;
        }
        if (this.targetSwitchCooldownTicks > 0) {
            return false;
        }
        return this.hatredOf(next) >= this.hatredOf(current) * TARGET_SWITCH_THRESHOLD;
    }

    /** 绕过威胁值闸门的强制锁敌：仅限内部机制（传送贴身 / 低帧率规避）。 */
    private void forceSetTarget(LivingEntity target) {
        super.setTarget(target);
    }

    /**
     * 免疫外部击退（2026-09-10 用户裁决 D7：「给 Boss 加免疫击退」，防被打飞后无法追击）。
     * <p>
     * {@code Entity.push(double,double,double)} 是原版**所有直接推力**的唯一汇聚点：
     * <ul>
     *   <li>实体互相推挤 —— {@code Entity.push(Entity)} 内部就是转调本方法（`Entity.java:1529`）；</li>
     *   <li>液体流动推动；</li>
     *   <li>第三方模组（含拔刀剑 SA 的 KnockBacks）直接调用的 {@code push} / {@code push(Vec3)}。</li>
     * </ul>
     * 此处一律忽略。攻击击退（{@code LivingEntity.knockback}）已由 {@code KNOCKBACK_RESISTANCE = 1.0}
     * 挡掉；**爆炸击退不走 push**（`Explosion.java:294/304` 用 {@code EXPLOSION_KNOCKBACK_RESISTANCE}
     * 算完直接 {@code setDeltaMovement}），故另设该属性为 1.0（与 KNOCKBACK_RESISTANCE 同一配置键）。
     * <p>
     * 自身机制（冲刺 / 高度飞行 / 传送 / 2.9 脱离）本来就不走 push，而是直接 {@code setDeltaMovement}，
     * 因此本覆写不影响 Boss 的主动位移。
     */
    @Override
    public void push(double x, double y, double z) {
        // 刻意忽略：外部推力一律无效
    }

    private boolean isActiveAreaBombardment(LivingEntity target) {
        if (!(target instanceof Player player)) {
            return false;
        }
        Integer last = this.playerAreaDamageTick.get(player.getUUID());
        return last != null && this.tickCount - last.intValue() <= AREA_BOMBARDMENT_WINDOW_TICKS;
    }

    private Holder<DamageType> findSpecializedDamageType() {
        if (this.damageTypeTotals.isEmpty()) {
            return null;
        }
        ResourceLocation bestKey = null;
        double bestTotal = 0.0;
        for (Map.Entry<ResourceLocation, Double> entry : this.damageTypeTotals.entrySet()) {
            if (!(entry.getValue() > bestTotal)) continue;
            bestTotal = entry.getValue();
            bestKey = entry.getKey();
        }
        if (bestKey == null) {
            return null;
        }
        Registry<DamageType> registry = this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return registry.getHolder(bestKey).orElse(null);
    }

    public boolean isAttackRandomized() {
        return this.attackRandomized;
    }

    public boolean isAttackSpecialized() {
        return this.attackSpecialized;
    }

    private void startTransition() {
        int ticks;
        this.transitionTo(BossState.PHASE1_TRANSITION);
        this.transitionTicks = ticks = SilentSunConfig.PHASE_TRANSITION_SECONDS.get() * 20;
        this.transitionTotalTicks = ticks;
        this.bossEvent.setVisible(true);
        this.triggerAnim("main", "transition");
        Level level = this.level();
        if (level instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)level;
            this.grantAdvancementToParticipants(sl, "phase1_clear");
        }
    }

    private void spawnTransitionImpact(ServerLevel serverLevel) {
        double y = this.getY() + 2.0;
        serverLevel.explode(this, this.getX(), y, this.getZ(), 0.0f, Level.ExplosionInteraction.NONE);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), y, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 16; ++i) {
            double angle = Math.PI * 2 * (double)i / 16.0;
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX() + Math.cos(angle) * 5.0, y - 0.5, this.getZ() + Math.sin(angle) * 5.0, 1, 0.0, 0.0, 0.0, 0.12);
        }
    }

    private void enterPhase2Combat() {
        int oldPhase = this.phase;
        int oldTitleIndex = this.titleIndex;
        this.phase = 2;
        this.transitionTo(BossState.PHASE2_COMBAT);
        this.titleIndex = 0;
        this.titleLockTicks = this.titleLockDurationTicks();
        this.setHealth(this.getMaxHealth());
        this.anticheat.markLegalHealthChange(this.getHealth());
        this.setTarget(null);
        this.bossEvent.setVisible(true);
        this.onTitleChanged(oldPhase, oldTitleIndex, this.phase, this.titleIndex);
    }

    // 2026-09-11（代码审计 G16 #8 修复）：原 enterPhase2() 只是 enterPhase2Combat() 的同义包装，
    // 唯一调用点已改为直调后者 —— 已删除。

    void enterNoResurrectionPhase2() {
        this.enterPhase2Combat();
        this.chaosRuinAbsoluteAttacks = true;
        this.blackSunUnlocked = true;
        this.colorlessUnlocked = true;
        this.ashDawnUnlocked = true;
        this.dodgeChance = Math.max(this.dodgeChance, (double)0.15f);
        this.enrageStackingUnlocked = true;
        this.healBoostTicks = 600;
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 4, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, true, false), this);
        this.level().playSound(null, this.blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 1.0f, 1.0f);
        this.awaitingNoResurrectionPhase2 = false;
        MutableComponent msg = Component.translatable("message.silent_sun.redios.no_resurrection_start").withStyle(new ChatFormatting[]{ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD});
        if (!BossTargeting.playerOnlyMode()) {
            for (ServerPlayer sp : ((ServerLevel)this.level()).players()) {
                sp.sendSystemMessage(this.rediosSigned(msg));
            }
        } else {
            this.broadcastToParticipants(this.rediosSigned(msg));
        }
    }

    /**
     * 大伤害打穿头衔段底时强制逐格推进（2026-08-30 用户裁决「钳在段底，逐格推进」）。
     * 与 updateTitle 的「锁血结束才推进」不同：伤害已打穿当前段底即视为进入下一段，
     * 立即 titleIndex+1 + 重设锁血 + onTitleChanged（BossFlag 逐个授予），不等待锁血计时。
     * 非 x.9 头衔专用（x.9 走濒死锁血，不调此方法）。
     */
    void advanceTitleFromDamage() {
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        if (this.titleIndex >= titles.size() - 1) {
            return; // 已到最后头衔（x.9），由濒死锁血处理
        }
        int oldPhase = this.phase;
        int oldTitleIndex = this.titleIndex;
        this.titleIndex = this.titleIndex + 1;
        this.titleLockTicks = this.titleLockDurationTicks();
        this.onTitleChanged(oldPhase, oldTitleIndex, this.phase, this.titleIndex);
    }

    /**
     * 头衔推进（每 tick）。见设计稿《docs/设计文稿-重制版.md》§2.2。
     * 衔接：computeTitleIndex → titleIndex+1（只步进 1，保证中间 BossFlag 逐个授予）
     * → 重设锁血 → onTitleChanged（唯一头衔切换入口）。锁血中递减 + clampHealthToCurrentTitle。
     */
    private void updateTitle() {
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        int minTicks = this.titleLockDurationTicks();
        int oldTitleIndex = this.titleIndex;
        int oldPhase = this.phase;
        int desired = RediosEntity.computeTitleIndex(this.getMaxHealth(), this.getHealth(), titles.size());
        if (this.titleLockTicks <= 0 && desired > this.titleIndex) {
            // 只 +1 步进：高频高额伤害一次打穿多段时，不再跳过头衔，保证中间 BossFlag 逐个授予
            this.titleIndex = this.titleIndex + 1;
            this.titleLockTicks = minTicks;
            this.onTitleChanged(oldPhase, oldTitleIndex, this.phase, this.titleIndex);
        } else if (this.titleLockTicks > 0) {
            --this.titleLockTicks;
            this.clampHealthToCurrentTitle();
            if (this.titleLockTicks == 0) {
                this.titleLockGraceTicks = 100;
                this.onTitleLockEnded();
                this.clampHealthToCurrentTitle();
            }
        }
    }

    private void onTitleLockEnded() {
        if (this.awaitingNoResurrectionPhase2) {
            this.enterNoResurrectionPhase2();
            return;
        }
        if (this.phase == 1 && (this.titleIndex == 7 || this.titleIndex == 8)) {
            this.forceAdvanceToNextTitle();
            return;
        }
        if (this.phase == 2 && this.titleIndex == 5) {
            this.forceAdvanceToNextTitle();
            return;
        }
        this.tryForceAdvanceOnLockEnd();
    }

    private void clampHealthToCurrentTitle() {
        if (this.awaitingNoResurrectionPhase2) {
            return;
        }
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        float maxHealth = this.getMaxHealth();
        float segment = this.titleSegment();
        float high = maxHealth - (float)this.titleIndex * segment;
        float low = maxHealth - (float)(this.titleIndex + 1) * segment;
        if (this.titleIndex == titles.size() - 1) {
            low = 0.0f;
        }
        float epsilon = 0.001f;
        // 头衔回退理想场景（2026-08-30 用户规范）：
        //   1.9/2.9 濒死锁血到期 → 自我恢复回血到 201（1.8/2.8 段）→ 回退头衔重新锁血，
        //   血量应保持 201（不重置、不抬到段底）。
        // clamp 职责仅「回血不越过头衔段顶」（防跳阶段回血）；**下限不抬段内血量**——
        // 段内（如 201 在 [1600,1800) 段内？否——201 < 1600 实为「段底下方」，见下）：
        // 血量低于当前段底（如回退后 201 低于 1.8 段底 1600）说明回退判定与段区间不一致，
        // 此时保持原值，不强行抬到段底（伤害锁底由 stagePhase1Lock/stagePhase2Pending 与
        // setHealth 钳底负责，clamp 只管回血上限）。
        float clampHigh = high - epsilon;
        float clamped = Math.min(this.getHealth(), clampHigh);
        if (clamped != this.getHealth()) {
            this.setHealth(clamped);
        }
    }

    private void tryForceAdvanceOnLockEnd() {
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        List<Component> list = titles;
        if (this.titleIndex >= titles.size() - 1) {
            return;
        }
        float maxHealth = this.getMaxHealth();
        float segment = this.titleSegment();
        float trigger = maxHealth - (float)(this.titleIndex + 1) * segment;
        float lockPoint = trigger + 1.0f;
        if (!this.isTwilightMomentActive() && this.getHealth() > lockPoint + 0.01f) {
            return;
        }
        int oldTitleIndex = this.titleIndex++;
        int oldPhase = this.phase;
        this.titleLockTicks = this.titleLockDurationTicks();
        float epsilon = 0.001f;
        this.setHealth(trigger - epsilon);
        this.broadcastForceAdvanceCountdown();
        this.onTitleChanged(oldPhase, oldTitleIndex, this.phase, this.titleIndex);
    }

    private boolean isTwilightMomentActive() {
        return this.phase == 2 && this.titleIndex == 5;
    }

    private Holder<MobEffect> resolveTwilightMomentApplyEffect() {
        Optional holder;
        ResourceLocation id = RediosRules.twilightMomentApplyEffectId();
        if (id != null && (holder = BuiltInRegistries.MOB_EFFECT.getHolder(id)).isPresent()) {
            return (Holder)holder.get();
        }
        return MobEffects.DARKNESS;
    }

    private boolean hasTwilightMomentSatisfyEffect(ServerPlayer player) {
        for (ResourceLocation id : RediosRules.twilightMomentSatisfyEffectIds()) {
            Optional holder;
            if (id == null || (holder = BuiltInRegistries.MOB_EFFECT.getHolder(id)).isEmpty() || player.getEffect((Holder)holder.get()) == null) continue;
            return true;
        }
        return false;
    }

    private void notifyTwilightMomentFailure(ServerPlayer player, boolean onApply) {
        int cooldown;
        int now = this.tickCount;
        int last = this.twilightFailureLastNotifyTick.getOrDefault(player.getUUID(), Integer.MIN_VALUE);
        if (now - last < (cooldown = Math.max(0, RediosRules.twilightMomentNotifyCooldownTicks()))) {
            return;
        }
        this.twilightFailureLastNotifyTick.put(player.getUUID(), now);
        if (!RediosRules.twilightMomentDebugMessages()) {
            player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.twilight_moment_expel").withStyle(ChatFormatting.BLUE)));
            return;
        }
        String mode = RediosRules.twilightMomentMode().name().toLowerCase();
        String apply = String.valueOf(RediosRules.twilightMomentApplyEffectId());
        String satisfy = RediosRules.twilightMomentSatisfyEffectIds().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String reason = Component.translatable((onApply ? "message.silent_sun.redios.twilight_moment_debug_reason.apply_failed" : "message.silent_sun.redios.twilight_moment_debug_reason.effect_missing")).getString();
        String resolvedApply = String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(this.resolveTwilightMomentApplyEffect().value()));
        List<String> active = new ArrayList();
        for (Holder holder : player.getActiveEffectsMap().keySet()) {
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey((MobEffect)holder.value());
            if (key == null) continue;
            active.add(key.toString());
        }
        active.sort(String::compareTo);
        if (active.size() > 12) {
            active = active.subList(0, 12);
        }
        String activeStr = String.join((CharSequence)",", active);
        player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.twilight_moment_expel").withStyle(ChatFormatting.BLUE)));
        player.sendSystemMessage(this.rediosSigned(Component.literal((String)("mode=" + mode + " reason=" + reason)).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(this.rediosSigned(Component.literal((String)("apply=" + apply + " satisfy=[" + satisfy + "]")).withStyle(ChatFormatting.GRAY)));
        player.sendSystemMessage(this.rediosSigned(Component.literal((String)("resolved=" + resolvedApply + " active=[" + activeStr + "]")).withStyle(ChatFormatting.GRAY)));
    }

    private void applyTwilightMomentOnEnter() {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        boolean expelEnabled = RediosRules.twilightMomentExpelMode();
        RediosRules.TwilightMomentMode mode = RediosRules.twilightMomentMode();
        int duration = mode == RediosRules.TwilightMomentMode.TIMED ? this.titleLockTicks + 5 : 40;
        Holder<MobEffect> applyEffect = this.resolveTwilightMomentApplyEffect();
        for (UUID id : this.battleParticipants) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            player.addEffect(new MobEffectInstance(applyEffect, duration, 0, true, false));
            if (!player.hasEffect(applyEffect)) {
                if (!expelEnabled) continue;
                this.expelForDarknessFailure(player, true);
                continue;
            }
            if (expelEnabled) {
                this.twilightTimedMissingFromApply.add(id);
            }
            if (!expelEnabled || this.hasTwilightMomentSatisfyEffect(player)) continue;
            if (mode == RediosRules.TwilightMomentMode.TIMED) {
                this.twilightTimedMissingTicks.put(id, Math.max(1, this.twilightTimedMissingTicks.getOrDefault(id, 0)));
                continue;
            }
            this.expelForDarknessFailure(player, true);
        }
    }

    private void tickTwilightMoment() {
        if (!this.isTwilightMomentActive() || this.titleLockTicks <= 0 || !(this.level() instanceof ServerLevel)) {
            this.twilightTimedMissingTicks.clear();
            this.twilightTimedMissingFromApply.clear();
            return;
        }
        boolean expelEnabled = RediosRules.twilightMomentExpelMode();
        RediosRules.TwilightMomentMode mode = RediosRules.twilightMomentMode();
        Holder<MobEffect> applyEffect = this.resolveTwilightMomentApplyEffect();
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null) {
                this.twilightTimedMissingTicks.remove(id);
                this.twilightTimedMissingFromApply.remove(id);
                continue;
            }
            if (this.expelledPlayers.contains(id)) {
                this.twilightTimedMissingTicks.remove(id);
                this.twilightTimedMissingFromApply.remove(id);
                continue;
            }
            if (player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            if (!this.hasTwilightMomentSatisfyEffect(player)) {
                if (mode == RediosRules.TwilightMomentMode.TIMED) {
                    int grace = Math.max(0, RediosRules.twilightMomentTimedGraceTicks());
                    int missing = this.twilightTimedMissingTicks.getOrDefault(id, 0) + 1;
                    this.twilightTimedMissingTicks.put(id, missing);
                    if (expelEnabled && missing > grace) {
                        boolean onApply = this.twilightTimedMissingFromApply.contains(id);
                        this.twilightTimedMissingTicks.remove(id);
                        this.twilightTimedMissingFromApply.remove(id);
                        this.expelForDarknessFailure(player, onApply);
                        continue;
                    }
                } else if (expelEnabled) {
                    boolean onApply = this.twilightTimedMissingFromApply.contains(id);
                    this.twilightTimedMissingTicks.remove(id);
                    this.twilightTimedMissingFromApply.remove(id);
                    this.expelForDarknessFailure(player, onApply);
                    continue;
                }
            } else if (mode == RediosRules.TwilightMomentMode.TIMED) {
                this.twilightTimedMissingTicks.remove(id);
                this.twilightTimedMissingFromApply.remove(id);
            }
            if (mode != RediosRules.TwilightMomentMode.REFRESH) continue;
            player.addEffect(new MobEffectInstance(applyEffect, 40, 0, true, false));
        }
    }

    private void expelForDarknessFailure(ServerPlayer player, boolean onApply) {
        BlockPos respawn;
        this.notifyTwilightMomentFailure(player, onApply);
        this.twilightExpelled.add(player.getUUID());
        Level level = this.level();
        if (level instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)level;
            this.grantAdvancement(sl, player, "teleport_expel");
        }
        this.expelFromBattle(player);
        ServerLevel targetLevel = player.server.getLevel(player.getRespawnDimension());
        if (targetLevel == null) {
            targetLevel = player.server.overworld();
        }
        if ((respawn = player.getRespawnPosition()) == null) {
            respawn = targetLevel.getSharedSpawnPos();
        }
        double tx = (double)respawn.getX() + 0.5;
        double ty = (double)respawn.getY() + 0.1;
        double tz = (double)respawn.getZ() + 0.5;
        try {
            player.teleportTo(targetLevel, tx, ty, tz, player.getYRot(), player.getXRot());
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (player.level() == this.level() && player.distanceToSqr(this) <= RediosRules.pushAwayDistance() * RediosRules.pushAwayDistance()) {
            this.repelExpelledPlayers();
        }
        if (this.isTwilightMomentActive() && this.battleParticipants.isEmpty()) {
            this.endBattleHalfDayDefeatCooldown();
        }
    }

    private void tickAshDawn() {
        if (!this.ashDawnUnlocked) {
            return;
        }
        this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, true, false), this);
        if (this.tickCount % 3 == 0) {
            this.setHeal(1.0f);
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player;
            if (this.expelledPlayers.contains(id) || (player = this.getServerPlayer(id)) == null || !player.isAlive()) continue;
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, true, true));
        }
        this.forEachMobOpponent(target -> target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, true, false)));
    }

    private void tickColorlessSuper(ServerLevel serverLevel) {
        // 2026-09-02：2.8/2.9 效果永久化——colorlessUnlocked 解锁后（正推/逆推/状态切换）
        // 均保持：buff 已无限时长（applyColorlessPermanentBuffs），此处不再依赖 isCombat
        // 限制（VOTE/PENDING 也保持）；仅保留 2.8 新效果（每 100 tick 激怒 +1）。
        if (!this.colorlessUnlocked) {
            return;
        }
        this.applyColorlessPermanentBuffs();
        if (this.tickCount % 100 == 0) {
            this.grantEnrageLevels(1);
        }
    }

    /** 2.8 无光失色永久效果（2026-09-02 起无限时长 = 直至 Boss 死亡；healBoostTicks 每 tick 重置永续）。
     *  调用效果（力量V/迅捷II/恢复V）+ 永久机制标志。重复效果以本永久版本为主
     * （vanilla addEffect 对同效果保留更长 duration，无限不被 40 tick 覆盖）。 */
    private void applyColorlessPermanentBuffs() {
        this.healBoostTicks = 600;
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 4, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 1, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION, 4, true, false), this);
        this.ashDawnUnlocked = true;
        this.dodgeChance = Math.max(this.dodgeChance, 0.15);
        this.chaosRuinAbsoluteAttacks = true;
        this.enrageStackingUnlocked = true;
    }

    private void endBattleHalfDayCooldown() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            // 判定秩序化（A5）：客户端分支只 safeDiscard，不（也无法）清服务端账本——
            // 本方法只在服务端结算路径调用（level 必为 ServerLevel），此分支实际不可达；
            // 账本清理由 settleBattle 完成。勿在此补 clearBattleRecord（无 ServerLevel 参数）。
            this.safeDiscard();
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        this.settleBattle(serverLevel, (long)(SilentSunConfig.COOLDOWN_HALF_DAYS.get() * 24000.0), true, false);
    }

    private void endBattleHalfDayDefeatCooldown() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            // 判定秩序化（A5）：同 endBattleHalfDayCooldown——客户端分支实际不可达，账本由 settleBattle 清理。
            this.safeDiscard();
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        // 2026-09-10（用户裁决 D5）：2.5 全体被传送 → 战斗终止，按设计发「一阶段奖励」（非二阶段）。
        this.forcePhase1Reward = true;
        this.settleBattle(serverLevel, (long)(SilentSunConfig.COOLDOWN_HALF_DAYS.get() * 24000.0), this.phase == 2, true);
    }

    private boolean isDarkStarActive() {
        return this.phase == 2 && this.titleIndex == 6;
    }

    /** 暗色天星「破方块窗口」（2026-09-09 用户裁决）：2.6 头衔内仅在 27³ 破坏进行中
     * （darkStarBlastOrigin 非空）才站桩+推开；破方块完成后恢复正常索敌/追击/近战，不再全程推人。 */
    private boolean isDarkStarBlastOngoing() {
        return this.isDarkStarActive() && this.darkStarBlastOrigin != null;
    }

    private void performDarkStarBlast() {
        if (this.darkStarFired) {
            return;
        }
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        this.darkStarFired = true;
        this.darkStarBlastOrigin = this.blockPosition();
        this.darkStarBlastNextIndex = 0;
    }

    private void tickDarkStarBlast(ServerLevel serverLevel) {
        BlockPos origin = this.darkStarBlastOrigin;
        if (origin == null) {
            return;
        }
        int maxIndex = 19683;
        for (int processed = 0; this.darkStarBlastNextIndex < maxIndex && processed < 600; ++processed) {
            BlockState state;
            // B-05（2026-09-11 依设计 §五 2.6「破坏约 27³」）：原为 ++this.darkStarBlastNextIndex，
            // 索引自 1 起 → 漏掉 idx=0（相对角点 (-13,-13,-13)），且最后一次 idx=19683 映射到越界的 (14,-13,-13)。
            // 改为后置自增，索引取 0..19682，恰好覆盖完整 27³ = 19683 格；完成判定（Next >= maxIndex）语义不变。
            int idx = this.darkStarBlastNextIndex++;
            int dx = idx / 729 - 13;
            int dy = idx / 27 % 27 - 13;
            int dz = idx % 27 - 13;
            BlockPos pos = origin.offset(dx, dy, dz);
            if (serverLevel.isOutsideBuildHeight(pos) || !serverLevel.getWorldBorder().isWithinBounds(pos) || (state = serverLevel.getBlockState(pos)).isAir()) continue;
            this.weapons.recordMinedBlock(state);
            if (this.isDarkStarSpecialBlock(state) && !this.darkStarRestoreBlocks.containsKey(pos)) {
                BlockEntity blockEntity;
                CompoundTag entry = new CompoundTag();
                entry.putInt("X", pos.getX());
                entry.putInt("Y", pos.getY());
                entry.putInt("Z", pos.getZ());
                entry.putInt("StateId", Block.getId((BlockState)state));
                // 2026-09-10（用户裁决 Q3）：NBT 回填从「只覆盖命令方块」扩到白名单内**任意带方块实体**的方块
                //（structure_block 的 mode/name、jigsaw 的 pool/target 等）。
                // 无方块实体的方块（如 end_portal_frame 的 eye 位）无需额外处理——BlockState 的全部属性
                // 都已由 StateId（Block.getId 的全局状态调色板 ID）承载，天然保留。
                if (RediosRules.restoreNbt() && (blockEntity = serverLevel.getBlockEntity(pos)) != null) {
                    entry.put("BlockEntity", blockEntity.saveWithoutMetadata(serverLevel.registryAccess()));
                }
                this.darkStarRestoreBlocks.put(pos, entry);
            }
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 35);
            serverLevel.levelEvent(2001, pos, Block.getId((BlockState)state));
        }
        if (this.darkStarBlastNextIndex >= maxIndex) {
            this.darkStarBlastOrigin = null;
        }
    }

    private boolean isDarkStarSpecialBlock(BlockState state) {
        // 2026-09-10（用户裁决 D6）：记录端与恢复端统一读同一份白名单（RediosRules.restoredBlocksWhitelist）。
        // 原实现记录集合（bedrock/barrier/end_portal_frame/三种命令方块）与配置白名单不一致，导致
        // barrier / end_portal_frame「记录了却不恢复」（永久摧毁）、structure_block / jigsaw「白名单空转」。
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        List<String> whitelist = RediosRules.restoredBlocksWhitelist();
        return !whitelist.isEmpty() && whitelist.contains(id.toString());
    }

    private void restoreDarkStarSpecialBlocks(ServerLevel serverLevel) {
        if (this.darkStarRestoreBlocks.isEmpty()) {
            return;
        }
        List<String> whitelist = RediosRules.restoredBlocksWhitelist();
        for (Map.Entry<BlockPos, CompoundTag> e : new HashMap<BlockPos, CompoundTag>(this.darkStarRestoreBlocks).entrySet()) {
            BlockEntity blockEntity;
            BlockPos pos = e.getKey();
            CompoundTag tag = e.getValue();
            if (!serverLevel.getBlockState(pos).isAir()) continue;
            BlockState state = Block.stateById((int)tag.getInt("StateId"));
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!whitelist.isEmpty() && !whitelist.contains(blockId.toString())) continue;
            serverLevel.setBlock(pos, state, 3);
            // 2026-09-10（用户裁决 Q3）：恢复端同样放开到任意方块实体（原为只认 CommandBlockEntity）。
            if (!RediosRules.restoreNbt() || (blockEntity = serverLevel.getBlockEntity(pos)) == null) continue;
            if (tag.contains("BlockEntity", 10)) {
                blockEntity.loadWithComponents(tag.getCompound("BlockEntity"), (HolderLookup.Provider)serverLevel.registryAccess());
                blockEntity.setChanged();
                continue;
            }
            if (blockEntity instanceof CommandBlockEntity) {
                CommandBlockEntity cbe = (CommandBlockEntity)blockEntity;
                // 旧档兼容：2026-09-10 之前的记录只存命令字符串（没有方块实体整体 NBT）
                if (!tag.contains("Cmd")) continue;
                cbe.getCommandBlock().setCommand(tag.getString("Cmd"));
                cbe.setChanged();
            }
        }
        this.darkStarRestoreBlocks.clear();
    }

    private void tickDarkStar() {
        ServerLevel serverLevel;
        Level level;
        if (this.darkStarFlightUnlocked) {
            this.setNoGravity(true);
            if (!this.darkStarBedrockRepaired && (level = this.level()) instanceof ServerLevel) {
                serverLevel = (ServerLevel)level;
                this.repairBedrockLayer(serverLevel);
            }
        }
        if (!this.isDarkStarActive()) {
            return;
        }
        level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        serverLevel = (ServerLevel)level;
        // 2026-09-10 实测修复（DR-01）：坠落兜底必须放在下方早退**之前**。原实现把它放在
        // `darkStarBlastOrigin == null` 早退之后，而该字段在破方块（约 33 tick）结束后即清空 →
        // 2.6 剩余期间这段永不执行（HEAD 里它在方法末尾、每 tick 都跑），Boss 掉到 y<0 也不会
        // 解锁飞行/补基岩。
        if (!this.darkStarFlightUnlocked && this.getY() < 0.0) {
            this.darkStarFlightUnlocked = true;
            this.setNoGravity(true);
            this.repairBedrockLayer(serverLevel);
        }
        // 2026-09-09：站桩+推开只在破方块进行中；破完后立即恢复正常战斗（不再每 tick 停导航/推人）
        if (this.darkStarBlastOrigin == null) {
            return;
        }
        this.tickDarkStarBlast(serverLevel);
        this.getNavigation().stop();
        this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
    }

    private void repairBedrockLayer(ServerLevel serverLevel) {
        BlockPos center = this.blockPosition();
        int y = serverLevel.getMinBuildHeight();
        for (int dx = -13; dx <= 13; ++dx) {
            for (int dz = -13; dz <= 13; ++dz) {
                BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                if (!serverLevel.getWorldBorder().isWithinBounds(pos) || serverLevel.getBlockState(pos).is(Blocks.BEDROCK)) continue;
                serverLevel.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
            }
        }
        boolean firstRepair = !this.darkStarBedrockRepaired;
        this.darkStarBedrockRepaired = true;
        if (firstRepair) {
            this.broadcastToParticipants(this.rediosSigned(Component.translatable("message.silent_sun.redios.dark_star_bedrock_repaired")));
        }
    }

    private boolean isBlackSunActive() {
        return this.phase == 2 && this.titleIndex == 7;
    }

    private int getActiveParticipantCount() {
        if (!(this.level() instanceof ServerLevel)) {
            return 0;
        }
        int count = 0;
        for (UUID id : this.battleParticipants) {
            ServerPlayer player;
            if (this.expelledPlayers.contains(id) || (player = this.getServerPlayer(id)) == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            ++count;
        }
        return count;
    }

    private int getCrossDimensionAliveCount() {
        if (!(this.level() instanceof ServerLevel)) {
            return 0;
        }
        int count = 0;
        for (UUID id : this.battleParticipants) {
            ServerPlayer player;
            if (this.expelledPlayers.contains(id) || (player = this.getServerPlayer(id)) == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() == this.level()) continue;
            ++count;
        }
        return count;
    }

    private void tickBlackSun() {
        double ratio;
        int threshold;
        if (!this.isBlackSunActive() || this.blackSunTriggered) {
            return;
        }
        int initial = this.initialParticipants.size();
        if (initial <= 0) {
            return;
        }
        int active = this.getActiveParticipantCount();
        // 2026-09-10（用户裁决 D8）：2.5「断光之刻」被传送者（twilightExpelled）计入「已减少」，
        // 不再加回留存；与设计 §7.1 A7「被传送（2.5）计入已减少」一致。
        int retained = active;
        if (retained > (threshold = (int)Math.floor((double)initial * (ratio = Mth.clamp((double)RediosRules.blackSunDefeatRatio(), 0.0, 1.0))))) {
            return;
        }
        this.blackSunTriggered = true;
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null) continue;
            MutableComponent msg = Component.translatable("message.silent_sun.redios.black_sun_farewell", new Object[]{player.getName().getString()}).withStyle(ChatFormatting.BLUE);
            player.sendSystemMessage(this.rediosSigned(msg));
            if (this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            // 2026-09-11（代码审计 G05 修复）：先登记再发界面——回包只有登记过且未过期才被受理，
            // 否则任意客户端可伪造包随时满血传送（原 handleServer 零校验）。
            BlackSunRespawnPayload.markDefeatScreenShown(player);
            PacketDistributor.sendToPlayer(player, new BlackSunDefeatPayload(), (CustomPacketPayload[])new CustomPacketPayload[0]);
        }
        this.endBattleThreeDayCooldown();
    }

    private void endBattleThreeDayCooldown() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            // 判定秩序化（A5）：同 endBattleHalfDayCooldown——客户端分支实际不可达，账本由 settleBattle 清理。
            this.safeDiscard();
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        boolean dropReward = this.phase == 2;
        this.settleBattle(serverLevel, (long)(SilentSunConfig.COOLDOWN_DAYS.get()).intValue() * 24000L, dropReward, true);
    }

    private void dropPhase1Reward(ServerLevel serverLevel, boolean includeDefeatBook) {
        int rewardPhase = this.phase == 2 ? 1 : this.phase;
        int rewardTitleIndex = this.phase == 2 ? PHASE1_TITLES.size() - 1 : this.titleIndex;
        List<ItemStack> loot = RediosRewardOverrideConfig.getOverrideStacks(rewardPhase, rewardTitleIndex);
        if ((loot = new ArrayList<ItemStack>(loot)).isEmpty()) {
            loot = RediosLootConfig.roll(serverLevel.random);
        }
        this.ensureMandatoryLoot(loot, includeDefeatBook ? RediosBookOutcome.PHASE1_WIN_PHASE2_LOSE : RediosBookOutcome.PHASE1_WIN_ONLY);
        loot.add(new ItemStack(ModItems.REDIOS_DISC_PHASE1.get()));
        // 灭却之日（required 前置）提供的「长梦彼端的灾厄之影」：数量由配置决定（B6 数量配置化）。
        this.addCalamityShadow(loot, serverLevel,
            SilentSunConfig.CALAMITY_SHADOW_PHASE1_MIN.get(), SilentSunConfig.CALAMITY_SHADOW_PHASE1_MAX.get());
        if (loot.isEmpty()) {
            return;
        }
        // 卸载退场（对应维度无玩家）：掉落直接发给参战玩家，不做世界放置（掉落地不可加载）
        if (this.leaveReason == LeaveReason.CHUNK_UNLOAD) {
            ItemStack box = ShulkerBoxUtil.createShulkerBox(Items.BROWN_SHULKER_BOX, loot, Component.translatable("container.silent_sun.phase1_reward"));
            if (!this.deliverRewardToPlayer(serverLevel, box)) {
                this.spawnAtLocation(box);
            }
            return;
        }
        BlockPos placePos = this.findNearbyRewardPlacement(serverLevel);
        boolean placed = false;
        if (placePos != null) {
            placed = ShulkerBoxUtil.placeShulkerBox(serverLevel, placePos, Blocks.BROWN_SHULKER_BOX.defaultBlockState(), loot, Component.translatable("container.silent_sun.phase1_reward"));
        }
        if (!placed) {
            ItemStack box = ShulkerBoxUtil.createShulkerBox(Items.BROWN_SHULKER_BOX, loot, Component.translatable("container.silent_sun.phase1_reward"));
            if (this.deliverRewardToPlayer(serverLevel, box)) {
                return; // 已发玩家，无世界箱，跳过坐标播报
            }
            this.spawnAtLocation(box);
            placePos = this.blockPosition();
        }
        this.notifyRewardCoordinates(serverLevel, placePos);
    }

    private void notifyRewardCoordinates(ServerLevel serverLevel, BlockPos rewardPos) {
        ServerPlayer player;
        String dim = serverLevel.dimension().location().toString();
        BlockPos bossPos = this.blockPosition();
        MutableComponent msg = Component.translatable("message.silent_sun.reward_coordinates.box").append(Component.literal((String)(dim + " " + rewardPos.getX() + " " + rewardPos.getY() + " " + rewardPos.getZ())).withStyle(ChatFormatting.GOLD)).append(Component.translatable("message.silent_sun.reward_coordinates.boss")).append(Component.literal((String)(dim + " " + bossPos.getX() + " " + bossPos.getY() + " " + bossPos.getZ())).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            player.sendSystemMessage(this.rediosSigned(msg));
        }
        for (UUID id : new HashSet<UUID>(this.expelledPlayers)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            player.sendSystemMessage(this.rediosSigned(msg));
        }
    }

    /**
     * 战斗账本心跳（每 tick upsert）。见 `docs/实现计划-2026-08-27-判定秩序化.md`。
     * 衔接：RediosBattleData.upsert → tickServer 扫描（实体缺失 + 位置正在实体 tick + 宽限窗 → 重建）。
     * <p>
     * 守卫已在（2026-09-10 复核）：{@code settlementDone || isRemoved()} 时不再写账本，
     * 因此结算后的残余 tick 不会残留记录、也不会被 tickServer 误判为实体异常而重建。
     */
    private void updateBattleRecord(ServerLevel serverLevel) {
        double dz;
        double dy;
        double dx;
        if (this.settlementDone || this.isRemoved()) {
            return;
        }
        if (this.battleParticipants.isEmpty() && this.mobParticipants.isEmpty()) {
            return;
        }
        if (this.battleAnchorPos == null || this.battleAnchorDim == null) {
            this.battleAnchorPos = this.blockPosition();
            this.battleAnchorDim = serverLevel.dimension().location();
        } else if (serverLevel.dimension().location().equals(this.battleAnchorDim) && (dx = this.getX() - ((double)this.battleAnchorPos.getX() + 0.5)) * dx + (dy = this.getY() - (double)this.battleAnchorPos.getY()) * dy + (dz = this.getZ() - ((double)this.battleAnchorPos.getZ() + 0.5)) * dz <= 16384.0) {
            this.battleAnchorPos = this.blockPosition();
        }
        ResourceLocation dim = serverLevel.dimension().location();
        RediosBattleData.get(serverLevel).upsert(this.getUUID(), dim, this.blockPosition(), this.phase, this.titleIndex, serverLevel.getGameTime(), this.getHealth(), this.soulSeverY, this.bossState.ordinal(), this.titleLockTicks, this.colorlessChallengeTicks, this.battleParticipants, this.expelledPlayers,
            this.battleStartGameTime, this.initialParticipants, this.twilightExpelled,
            this.playerNetDamageTotals, this.damageTypeTotals, this.darkStarRestoreBlocks,
            this.snapshotUnlockFlags());
    }

    /**
     * 解锁旗标快照（2026-09-10 实测修复 L1）：把"回场重建必须原样恢复"的解锁旗标打成复合标签，
     * 由账本随心跳一起存，`rebuildFromRecord` 写回实体 NBT —— 读端仍是
     * {@link #restoreStateFromNbt}（键名与 {@code addAdditionalSaveData} 完全一致，此处不另造键名）。
     * <p>
     * 背景：原重建只带 15 个键，这些旗标全部回落 false → Boss 一旦被外部删除（寰宇支配之剑之类）
     * 回场后就变成"残废版"：断魂收割 / 无色挑战 / 格挡 / 虚空传送 / 2.7 全属性 / 激怒叠加全失效，
     * 且 2.9 锁血解除位丢失会被推导成"已解除"→ 可被一击必杀。
     * <p>
     * 维护约定：**新增解锁类旗标时，只在这里与 {@code addAdditionalSaveData}/{@code restoreStateFromNbt}
     * 三处同步即可**，账本侧无需改动（它整包搬运）。
     */
    private CompoundTag snapshotUnlockFlags() {
        // 2026-09-11 重做（"回场漏键"根治）：改为「整体搬运实体自身 NBT，再剔除账本已单独携带 /
        // 必须重置的键」。键名与类型由 addAdditionalSaveData ↔ restoreStateFromNbt 天然对齐，**不可能
        // 再出现漏键或类型错配**；以后新增持久化字段自动覆盖。
        // 旧实现逐键手写 13 个旗标，接连漏掉 25 个键，后果实测可见：
        //   · SilentSunTransition → 回场后永久卡在转场态（无敌 + 无奖励，S1）
        //   · SilentSunMobParticipants / MobBattleEngaged → Mode 2 回场后全灭/卸载/区块保留守卫全 early-return
        //   · SilentSunDodgeChance(2.2) / SilentSunChaosRuinAbsoluteAttacks(2.3) → 保底闪避与永久绝对伤害丢失
        //   · weapons/anticheat 子标签 → 刀窗口与反作弊状态重置
        CompoundTag snapshot = new CompoundTag();
        this.addAdditionalSaveData(snapshot);
        for (String key : LEDGER_CARRIED_KEYS) {
            snapshot.remove(key);
        }
        // 必须重置：重建出的 Boss 还要能重新结算；管理员的离场意图与一次性命令标记不继承。
        snapshot.remove("SilentSunSettlementDone");
        snapshot.remove("SilentSunPendingCommandLeave");
        return snapshot;
    }

    /**
     * 账本已单独携带的键（rebuildFromRecord 会显式写它们，快照里剔除以免重复存储与覆盖冲突）。
     * <p>
     * 2026-09-11（代码审计 G05 #3 修复）：**移除 4 个锚点键**（{@code SilentSunAnchorX/Y/Z/Dim}）。
     * 它们原先列在这里的理由是「账本已单独携带」，但 {@code BattleRecord} **并没有锚点字段**，
     * {@code rebuildFromRecord} 只是用 {@code record.pos}（Boss 的**位置**）顶替锚点 ——
     * 于是锚点在每次回场后丢失：防逐客拉回的目标点变成「被甩飞后所在的位置」，机制形同虚设。
     * 移除后快照会原样携带锚点，读端仍是 {@code restoreStateFromNbt}（键名无需改动）。
     */
    private static final String[] LEDGER_CARRIED_KEYS = new String[]{
        "SilentSunDataVersion", "SilentSunPhase", "SilentSunTitleIndex", "SilentSunSoulSeverY",
        "SilentSunBossState", "SilentSunTitleLock", "SilentSunColorlessChallengeTicks",
        "SilentSunBattleParticipants", "SilentSunExpelledPlayers", "SilentSunBattleStartTime",
        "SilentSunInitialParticipants", "SilentSunTwilightExpelled", "SilentSunPlayerNetDamage",
        "SilentSunDamageTypeTotals", "SilentSunDarkStarRestore"
    };

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void tickAntiExile(ServerLevel serverLevel) {
        if (this.legitRemoval || this.isRemoved()) {
            return;
        }
        if (this.battleAnchorPos == null || this.battleAnchorDim == null) {
            return;
        }
        if (this.bossState.isVoteOrTransition()) {
            return;
        }
        ResourceLocation hereDim = serverLevel.dimension().location();
        boolean crossDim = !hereDim.equals(this.battleAnchorDim);
        boolean voided = !crossDim && this.getY() < (double)serverLevel.getMinBuildHeight() - 8.0;
        boolean exiled = false;
        if (!crossDim && !voided) {
            double dz;
            double dy;
            double dx = this.getX() - ((double)this.battleAnchorPos.getX() + 0.5);
            if (dx * dx + (dy = this.getY() - (double)this.battleAnchorPos.getY()) * dy + (dz = this.getZ() - ((double)this.battleAnchorPos.getZ() + 0.5)) * dz > 65536.0) {
                boolean anyNear = false;
                LivingEntity target = this.getTarget();
                if (target != null && target.isAlive() && target.level() == serverLevel && target.distanceToSqr(this) <= 16384.0) {
                    anyNear = true;
                }
                for (UUID id : this.battleParticipants) {
                    ServerPlayer p = this.getServerPlayer(id);
                    if (p == null || !p.isAlive() || p.level() != serverLevel || !(p.distanceToSqr(this) <= 16384.0)) continue;
                    anyNear = true;
                    break;
                }
                if (!anyNear) {
                    for (UUID id : this.mobParticipants) {
                        LivingEntity mob;
                        Entity e = serverLevel.getEntity(id);
                        if (!(e instanceof LivingEntity) || !(mob = (LivingEntity)e).isAlive() || !(mob.distanceToSqr(this) <= 16384.0)) continue;
                        anyNear = true;
                        break;
                    }
                }
                if (!anyNear) {
                    exiled = true;
                }
            }
        }
        if (!(crossDim || voided || exiled)) {
            return;
        }
        boolean punishFired = this.removalPunishCooldownTicks <= 0;
        this.onTamperAttempt(serverLevel, true);
        this.allowSelfTeleport = true;
        try {
            if (crossDim) {
                ServerLevel anchorLevel = serverLevel.getServer().getLevel(ResourceKey.create((ResourceKey)Registries.DIMENSION, (ResourceLocation)this.battleAnchorDim));
                if (anchorLevel == null) {
                    return;
                }
                this.teleportTo(anchorLevel, (double)this.battleAnchorPos.getX() + 0.5, this.battleAnchorPos.getY(), (double)this.battleAnchorPos.getZ() + 0.5, Set.of(), this.getYRot(), this.getXRot());
            } else {
                this.teleportTo((double)this.battleAnchorPos.getX() + 0.5, this.battleAnchorPos.getY(), (double)this.battleAnchorPos.getZ() + 0.5);
            }
        }
        finally {
            this.allowSelfTeleport = false;
        }
        if (punishFired) {
            MutableComponent msg = Component.translatable("message.silent_sun.redios.anti_exile").withStyle(ChatFormatting.GOLD);
            this.broadcastToParticipants(this.rediosSigned(msg));
        }
    }

    private void clearBattleRecord(ServerLevel serverLevel) {
        // 合法离场防线：settlementDone 守卫已阻止结算后再心跳上报；此处直接移除账本。
        // 2026-09-10（用户裁决 A8）：记录被移除即代表该场战斗已结算——
        // RediosBattleData.tickServer 的终态闸门只认「账本里还有记录」，记录已删 → 永不重建。
        RediosBattleData data = RediosBattleData.get(serverLevel);
        data.remove(this.getUUID());
        this.clearDamageDebugCaches();
    }

    private void settleBattle(ServerLevel serverLevel, long cooldownTicks, boolean dropPhase1Reward, boolean includeDefeatBook) {
        if (this.settlementDone) {
            // 退场秩序化（2026-08-30）：settlementDone 已 true 说明本 Boss 已走过结算。
            // 若实体仍未移除（历史幽灵 / 竞态残留），补一次兜底移除，避免「已结算但赖着不走」。
            // 正常结算路径 settlementDone 与 safeDiscard 同 tick 完成，此分支仅防御。
            if (!this.isRemoved()) {
                this.safeDiscard();
            }
            return;
        }
        this.settlementDone = true;
        // 2026-09-10：离场诊断日志（实测「Boss 不知道为什么就不见了」时全靠这条定位）。
        SilentSunMod.LOGGER.warn("[Redios] 结算离场：原因={} 阶段={} 头衔={} 发一阶段奖励={} 冷却tick={} 结局书={} 参战={} 位置={}",
            this.leaveReason, this.phase, this.titleIndex, dropPhase1Reward, cooldownTicks, includeDefeatBook,
            this.battleParticipants.size(), this.blockPosition());
        // 退场秩序化（2026-08-30）：先标记账本「已合法离场」再执行掉落等可能抛异常的步骤。
        // 顺序颠倒（先 clearBattleRecord 再掉落）能保证：即使掉落/音效/清理中抛异常中断，
        // 账本记录也已移除——这是「终态闸门」的实现基础：记录存在 ⟺ 未结算，已结算场次
        // 不可能再被 RediosBattleData.tickServer 重建（「先确认是合法离场再做复活」，
        // 杜绝账本位置与击杀地相距很远时的误判复活）。
        this.clearBattleRecord(serverLevel);
        this.restoreDarkStarSpecialBlocks(serverLevel);
        if (dropPhase1Reward) {
            // 掉落潜影盒规范化（2026-08-30）：按当前 phase 分派箱子——
            // P1（一阶段停手）→ dropPhase1Reward（BROWN 箱 + P1 唱片）；
            // P2（二阶段击杀/计时）→ dropPhase2Reward（WHITE 箱 + P2 唱片 + 二阶段战利品）。
            // 原实现统一调 dropPhase1Reward（其内部 phase==2 时回退 P1 配置），
            // 导致二阶段击杀/计时掉落成 P1 的箱子——「箱子调用脱节」。
            // 2026-09-10（用户裁决 D5）：forcePhase1Reward 时（2.5 全体被传送致战斗终止）
            // 按设计 §2.5 发一阶段奖励，即使当前 phase==2。
            if (this.phase == 2 && !this.forcePhase1Reward) {
                this.dropPhase2Reward(serverLevel, includeDefeatBook);
            } else {
                this.dropPhase1Reward(serverLevel, includeDefeatBook);
            }
        }
        if (includeDefeatBook) {
            MutableComponent msg = Component.translatable("message.silent_sun.redios.defeat_book_farewell").withStyle(ChatFormatting.DARK_PURPLE);
            this.broadcastToParticipants(this.rediosSigned(msg));
        }
        if (cooldownTicks > 0L && BossTargeting.playerOnlyMode()) {
            this.applySummonCooldown(serverLevel, cooldownTicks);
            if (this.rebuiltAsSettled) {
                SilentSunMod.LOGGER.info("Redios rebuilt from record settled normally; summon cooldown applied as usual ({} ticks)", cooldownTicks);
            }
        }
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 1.0f);
        this.cleanupNearbyLivingAfterBattle(serverLevel);
        this.cleanupPlayersAfterBattle(serverLevel);
        this.bossEvent.setVisible(false);
        this.safeDiscard();
    }

    /** 二阶段掉落潜影盒（2026-08-30 规范化）：WHITE 箱 + P2 唱片 + 二阶段战利品（含灭却之日固定掉落）。
     *  与 isVoidAllThingsActive 特例分支共用 createPhase2Loot 语义，但由本方法统一放箱与播报坐标。
     *  outcome 固定 PHASE2_WIN：二阶段击杀/计时都是「二阶段胜利」，ensureMandatoryLoot 据此
     *  补信标 + 钻石块 + 结局之书（includeDefeatBook 只影响书内容文案，不影响 outcome）。 */
    private void dropPhase2Reward(ServerLevel serverLevel, boolean includeDefeatBook) {
        ArrayList<ItemStack> loot = new ArrayList<ItemStack>();
        loot.addAll(this.createPhase1Loot(serverLevel, false));
        loot.addAll(this.createPhase2Loot(serverLevel, true));
        this.ensureMandatoryLoot(loot, RediosBookOutcome.PHASE2_WIN);
        if (loot.isEmpty()) {
            return;
        }
        // 卸载退场（对应维度无玩家）：掉落直接发给参战玩家，不做世界放置（掉落地不可加载）
        if (this.leaveReason == LeaveReason.CHUNK_UNLOAD) {
            ItemStack box = ShulkerBoxUtil.createShulkerBox(Items.WHITE_SHULKER_BOX, loot, Component.translatable("container.silent_sun.redios_loot"));
            if (!this.deliverRewardToPlayer(serverLevel, box)) {
                this.spawnAtLocation(box);
            }
            return;
        }
        BlockPos placePos = this.findNearbyRewardPlacement(serverLevel);
        boolean placed = false;
        if (placePos != null) {
            placed = ShulkerBoxUtil.placeShulkerBox(serverLevel, placePos, Blocks.WHITE_SHULKER_BOX.defaultBlockState(), loot, Component.translatable("container.silent_sun.redios_loot"));
        }
        if (!placed) {
            ItemStack box = ShulkerBoxUtil.createShulkerBox(Items.WHITE_SHULKER_BOX, loot, Component.translatable("container.silent_sun.redios_loot"));
            if (this.deliverRewardToPlayer(serverLevel, box)) {
                return; // 已发玩家，无世界箱，跳过坐标播报
            }
            this.spawnAtLocation(box);
            placePos = this.blockPosition();
        }
        this.notifyRewardCoordinates(serverLevel, placePos);
    }

    private void cleanupPlayersAfterBattle(ServerLevel serverLevel) {
        // A-1（2026-09-11）：战斗结束时下发本阶段 outro（用户裁决：P1 进投票播 P1 结束曲，
        // P2 收尾按惯性 = 战斗结束）。已下发则随后的 cleanupPlayerAfterBattle 不再 stop
        // MUSIC 源，让 outro 自然播完而不是被清理打断。
        this.playBattleMusicOutroForParticipants();
        ServerPlayer player;
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            this.cleanupPlayerAfterBattle(player);
        }
        for (UUID id : new HashSet<UUID>(this.expelledPlayers)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            this.cleanupPlayerAfterBattle(player);
        }
    }

    private void cleanupPlayerAfterBattle(ServerPlayer player) {
        if (!this.battleMusicOutroSent) {
            this.stopBattleMusicFor(player);
        }
        CommonEvents.clearPhase2ChoicePending(player);
        CommonEvents.clearSharpenSoulSever(player);
        CommonEvents.clearRootlessBuffBlock(player);
        CommonEvents.clearMirrorFaceAttackBoost(player);
        // 断魂账本清理（2026-09-01 用户裁决：离场/脱战/禁止再战 → 立即清除对应玩家账本）：
        // 统一清 effect + 玩家账本 + Boss 账本，防 NBT 残留（结算以账本为主，残留会误结算）。
        cn.autoforged.extinction_day_mod_1784441698.effect.SoulSeverMobEffect.clearSoulSeverLedgers(player);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.WEAKNESS);
        // 2026-09-11 实测修复（R-2.8）：脆弱（FRAGILE）是 INFINITE_DURATION 挂上去的，而全项目
        // 原先**没有任何一处移除它** → 战斗结束后玩家永久带 +50% 受伤（最高 10 级）。设计 §3.9
        // 要求「持续时间与激怒绑定（激怒结束则脆弱结束）」，战后清理必须清掉。
        player.removeEffect(ModEffects.FRAGILE);
    }

    // 2026-09-11（代码审计 G08 #3 修复）：原 removeAntiCheatCooldowns() 已随
    // AntiCheatLayer.antiCheatCooldownPlayers（只写不读的死集合）一并删除。

    private void cleanupNearbyLivingAfterBattle(ServerLevel serverLevel) {
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(64.0))) {
            if (entity == this) continue;
            entity.removeEffect(ModEffects.SOUL_SEVER);
            entity.removeEffect(MobEffects.DARKNESS);
            entity.removeEffect(MobEffects.WEAKNESS);
            // 2026-09-11（R-2.8 同批）：附近生物同样清脆弱——Mode 1 下这些生物会被光环挂上脆弱，
            // 不清则战后永久 +50% 受伤。
            entity.removeEffect(ModEffects.FRAGILE);
        }
    }

    void broadcastToParticipants(Component msg) {
        ServerPlayer player;
        Object object;
        if (!BossTargeting.playerOnlyMode() && (object = this.level()) instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)object;
            for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
            }
            return;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            player.sendSystemMessage(msg);
        }
        for (UUID id : new HashSet<UUID>(this.expelledPlayers)) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            player.sendSystemMessage(msg);
        }
    }

    private void grantAdvancement(ServerLevel serverLevel, ServerPlayer player, String name) {
        if (player == null) {
            return;
        }
        AdvancementHolder advancement = serverLevel.getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath("silent_sun", (String)name));
        if (advancement != null) {
            // 2026-09-11（代码审计 P0 修复）：criterion 名必须是 json 里定义的那个。
            // datagen 侧统一用 .addCriterion("trigger", impossible())（见 ModAdvancementProvider），
            // 生成的 advancement json 里 criteria 键就是 "trigger"。原实现传的是成就 id
            // （"silent_sun:" + name），grantProgress 查不到该名会**静默返回 false** →
            // phase1_clear / phase2_countdown / phase2_win / teleport_expel 与 root 全部拿不到。
            player.getAdvancements().award(advancement, "trigger");
        }
    }

    private void grantAdvancementToParticipants(ServerLevel serverLevel, String name) {
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            this.grantAdvancement(serverLevel, this.getServerPlayer(id), name);
        }
    }

    /**
     * 唯一性守卫：任意维度已存在 self 以外的 RediosEntity 时返回 true。
     * self 传 null 表示「检测是否存在任何 RediosEntity」（重建前调用）。
     */
    public static boolean isAnotherRediosPresent(ServerLevel level, Entity self) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }
        for (ServerLevel sl : server.getAllLevels()) {
            for (Entity e : sl.getEntities().getAll()) {
                if (!(e instanceof RediosEntity)) continue;
                if (e == self) continue;
                return true;
            }
        }
        return false;
    }

    public static boolean rebuildFromRecord(ServerLevel level, RediosBattleData.BattleRecord record) {
        RediosEntity boss;
        if (level == null || record == null || record.pos == null || record.dimension == null) {
            return false;
        }
        if (isAnotherRediosPresent(level, null)) {
            return false;
        }
        if ((boss = ModEntities.REDIOS.get().create(level)) == null) {
            return false;
        }
        boss.setUUID(record.bossId);
        CompoundTag recordTag = new CompoundTag();
        // 2026-09-10（**实测崩坏修复：重建风暴**）：必须写入数据版本键，否则重建出的 Boss 会被
        // onEntityJoinLevel 的 M16 守卫判为「旧版本残留 Boss」而**拒绝入世**：
        //   restoreStateFromNbt 把缺失的 "SilentSunDataVersion" 读成 0（`:3877`）
        //   → isLegacyData() == true（`bossDataVersion < BOSS_DATA_VERSION`）
        //   → CommonEvents.onEntityJoinLevel: `event.setCanceled(true)`
        //   → 实体从未真正加入 → tickServer 下一 tick 又判定"实体缺失 + 正在实体 tick" → 又重建
        //   → **每 tick 一次的重建风暴 + 聊天栏被「来！不打到痛快不罢休！」刷爆**（实测日志 22:46:11 起）。
        recordTag.putInt("SilentSunDataVersion", BOSS_DATA_VERSION);
        recordTag.putInt("SilentSunPhase", record.phase);
        recordTag.putInt("SilentSunTitleIndex", record.titleIndex);
        recordTag.putLong("SilentSunSoulSeverY", record.soulSeverY);
        recordTag.putInt("SilentSunBossState", record.bossStateOrd);
        recordTag.putInt("SilentSunTitleLock", record.titleLockTicks);
        recordTag.putInt("SilentSunColorlessChallengeTicks", record.colorlessChallengeTicks);
        ListTag participants = new ListTag();
        for (UUID id : record.participants) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            participants.add(entry);
        }
        recordTag.put("SilentSunBattleParticipants", participants);
        ListTag expelled = new ListTag();
        for (UUID id : record.expelled) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            expelled.add(entry);
        }
        recordTag.put("SilentSunExpelledPlayers", expelled);
        // 2026-09-10：**补齐重建必需状态** —— 原先只写 9 个键，导致重建后这些回落默认值，
        // 其中有实际影响的：2.6 破坏方块的恢复表（丢了 = 那些方块永久不恢复）、战斗开始时间
        // （丢了 = 动态减伤从 80% 重新计时，Boss 突然变硬）、初始参战者（2.7 分母清零）、
        // 2.5 被传送名单、仇恨统计（威胁值清零 → 索敌退回就近）、伤害类型统计（2.7 特化丢失）。
        // 键名与结构**与 addAdditionalSaveData 完全一致**，否则读端(getList/getLong)取不到。
        recordTag.putLong("SilentSunBattleStartTime", record.battleStartGameTime);
        ListTag initialList = new ListTag();
        for (UUID id : record.initialParticipants) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            initialList.add(entry);
        }
        recordTag.put("SilentSunInitialParticipants", initialList);
        ListTag twilightList = new ListTag();
        for (UUID id : record.twilightExpelled) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            twilightList.add(entry);
        }
        recordTag.put("SilentSunTwilightExpelled", twilightList);
        ListTag netTotals = new ListTag();
        for (Map.Entry<UUID, Double> netEntry : record.playerNetDamage.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", netEntry.getKey());
            entry.putDouble("Total", netEntry.getValue().doubleValue());
            netTotals.add(entry);
        }
        recordTag.put("SilentSunPlayerNetDamage", netTotals);
        ListTag dmgTotals = new ListTag();
        for (Map.Entry<ResourceLocation, Double> dmgEntry : record.damageTypeTotals.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", dmgEntry.getKey().toString());
            entry.putDouble("Total", dmgEntry.getValue().doubleValue());
            dmgTotals.add(entry);
        }
        recordTag.put("SilentSunDamageTypeTotals", dmgTotals);
        ListTag restoreList = new ListTag();
        for (CompoundTag entry : record.darkStarRestore.values()) {
            restoreList.add(entry.copy());
        }
        recordTag.put("SilentSunDarkStarRestore", restoreList);
        // 2026-09-10 实测修复（L1）：写回解锁旗标快照（键名与 addAdditionalSaveData 一致，
        // restoreStateFromNbt 直接消费）——否则回场后 Boss 的断魂收割/无色挑战/格挡/虚空传送/
        // 2.7 全属性/激怒叠加全部回落 false，2.9 锁血解除位还会被推导成"已解除"→ 可被一击必杀。
        if (!record.unlockFlags.isEmpty()) {
            recordTag.merge(record.unlockFlags.copy());
        }
        boss.restoreStateFromNbt(recordTag);
        // 2026-09-11（代码审计 G05 #3 修复）：锚点优先用快照携带的值（锚点键已不再被
        // LEDGER_CARRIED_KEYS 剔除，restoreStateFromNbt 会读回真实锚点）；仅在旧档/缺键时
        // 才退化为「Boss 当前位置」。原实现**无条件**用 record.pos 顶替 —— 而账本并不携带锚点，
        // 于是每次回场都把开战锚点覆盖成 Boss 当时所在的位置。
        if (boss.battleAnchorPos == null || boss.battleAnchorDim == null) {
            boss.battleAnchorPos = record.pos;
            boss.battleAnchorDim = record.dimension;
        }
        boss.moveTo((double)record.pos.getX() + 0.5, record.pos.getY(), (double)record.pos.getZ() + 0.5, 0.0f, 0.0f);
        boss.applyPhaseMaxHealth(level);
        float maxHealth = boss.getMaxHealth();
        List<Component> titles = boss.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        int titleIdx = Mth.clamp(boss.titleIndex, 0, titles.size() - 1);
        float restored;
        if (titleIdx >= titles.size() - 1) {
            // phase1.9 / phase2.9 濒死段：恢复至该头衔区间满值，避免 1 血快照重建后立即再死
            float segment = maxHealth / (float)titles.size();
            restored = maxHealth - (float)titleIdx * segment;
        } else {
            restored = record.health > 0.0f ? Mth.clamp(record.health, 1.0f, maxHealth) : maxHealth;
        }
        boss.forceSetHealth(restored);
        boss.anticheat.markLegalHealthChange(boss.getHealth());
        boss.rebuiltAsSettled = true;
        // 2026-09-10 实测修复（L4）：必须检查落地结果——原实现丢弃 addFreshEntity 返回值后无条件
        // return true，于是"重建没站住"也被当成成功（A8 已取消重建次数上限）→ 每 5 秒重试一次并
        // 每次广播「来！不打到痛快不罢休！」，无限循环。失败时交给 rebuildOrDrop 的冷却门重试。
        if (!level.addFreshEntity(boss)) {
            SilentSunMod.LOGGER.warn("[Redios] 回场失败（addFreshEntity 拒绝）：pos={} phase={} 头衔={}",
                record.pos, record.phase, record.titleIndex);
            return false;
        }
        boss.leaveReason = LeaveReason.ANOMALY;
        boss.broadcastToParticipants(boss.rediosSigned(Component.translatable("message.silent_sun.redios.rebuilt_after_purge").withStyle(ChatFormatting.RED)));
        // 2026-09-11 用户裁决（C5 落地）：**重建回场本身就是"明确的作弊场景"** —— 外部模组/存档编辑
        // 把 Boss 清除掉（例：寰宇支配之剑的"清除实体"）。按设计触发反作弊惩罚：全员警告 +
        // applyCooldowns=true = 物品栏 **与 Curios 饰品栏** 每件 2 秒（40 tick）强制冷却
        //（实现见 AntiCheatLayer.counterAllCheatAttackers:653-677；全局 30 秒惩罚门自带防刷屏）。
        // 因此 stageDeathCheat 的惩罚分支无需恢复可达——它的判据已被"六态全放行"覆盖成不可达。
        boss.anticheat.counterAllCheatAttackers(level, true);
        SilentSunMod.LOGGER.warn("Redios rebuilt from battle record at {} (externally removed, phase={}, leaveReason={})", new Object[]{record.pos, record.phase, boss.leaveReason});
        boss.leaveReason = LeaveReason.NONE;
        return true;
    }

    public void settleByUnloadTimeout(ServerLevel serverLevel) {
        if (this.isRemoved()) {
            return;
        }
        this.leaveReason = LeaveReason.CHUNK_UNLOAD;
        // 2026-09-10：这条是「Boss 莫名其妙不见了」的头号嫌疑路径，必须留痕。
        SilentSunMod.LOGGER.warn("[Redios] 区块卸载超时离场（走远/卸载判定）：位置={} 阶段={} 头衔={} 参战={} 纯玩家模式={}",
            this.blockPosition(), this.phase, this.titleIndex, this.battleParticipants.size(),
            BossTargeting.playerOnlyMode());
        if (!BossTargeting.playerOnlyMode()) {
            this.bossLeaveNoLoot();
            return;
        }
        boolean includeDefeatBook = this.phase == 2;
        long cooldown = this.phase == 2 ? (long)(SilentSunConfig.COOLDOWN_DAYS.get()).intValue() * 24000L : 0L;
        this.settleBattle(serverLevel, cooldown, this.hasClearedPhase1ForLoot(), includeDefeatBook);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void tickChunkRetention(ServerLevel serverLevel) {
        if (this.battleParticipants.isEmpty()) {
            return;
        }
        // 2026-09-01 用户裁决：最近活跃参战玩家 > 128 格直接退场，无视 AI 停止
        //（不再因 VOTE/TRANSITION 提前返回）。外部性能模组（Adaptive Performance Tweaks 等）
        // 强保区块加载会让 checkBattleAreaUnloaded 永不触发，这里改用「最近玩家距离」兜底退场。
        int active = 0;
        boolean anyClose = false;
        for (UUID id : this.battleParticipants) {
            ServerPlayer player = this.getServerPlayer(id);
            if (this.expelledPlayers.contains(id) || player == null || player.isSpectator()
                || player.isCreative() || !player.isAlive() || player.level() != this.level()) {
                continue;
            }
            ++active;
            // 只按水平（XZ）距离判定：不把高度轴算进退场距离，
            // 否则 Boss 高度飞行（飞上去追人）时地面玩家会被垂直差误判 >128 格。
            double dx = player.getX() - this.getX();
            double dz = player.getZ() - this.getZ();
            if (dx * dx + dz * dz <= 128.0 * 128.0) {
                anyClose = true;
                break;
            }
        }
        if (active == 0 || anyClose) {
            return;
        }
        if (serverLevel.getLevelData().isHardcore()) {
            BlockPos pos = this.blockPosition();
            BlockPos surface = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
            this.allowSelfTeleport = true;
            try {
                this.teleportTo((double)surface.getX() + 0.5, surface.getY(), (double)surface.getZ() + 0.5);
            }
            finally {
                this.allowSelfTeleport = false;
            }
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 1.0f);
            this.bossLeaveNoLoot();
            return;
        }
        // 非硬核：最近玩家 > 128 格直接无奖励退场（逃离优先）。
        this.bossLeaveNoLoot();
    }

    private boolean checkDefeatByAllDead(ServerLevel serverLevel) {
        if (this.bossState.isVoteOrTransition()) {
            this.allParticipantsDeadTicks = 0;
            return false;
        }
        boolean playerOnly = BossTargeting.playerOnlyMode();
        if (playerOnly) {
            if (this.battleParticipants.isEmpty()) {
                this.allParticipantsDeadTicks = 0;
                return false;
            }
            if (this.getActiveParticipantCount() > 0 || this.getCrossDimensionAliveCount() > 0) {
                this.allParticipantsDeadTicks = 0;
                return false;
            }
        } else {
            if (!this.mobBattleEngaged) {
                this.allParticipantsDeadTicks = 0;
                return false;
            }
            if (this.getActiveMobParticipantCount() > 0) {
                this.allParticipantsDeadTicks = 0;
                return false;
            }
        }
        ++this.allParticipantsDeadTicks;
        if (this.allParticipantsDeadTicks == 1) {
            // 2026-09-10（用户裁决）：把「Boss 无故消失」变成「有预告、可取消」。
            // 参战者全部非活跃（死亡 / 创造 / 旁观 / 跨维度 / 被逐出）起算的第一 tick 立刻播报，
            // 玩家在这 200 tick（10 秒）内切回生存并回到战场就会自动取消（计数被重置）。
            MutableComponent inactiveWarn = Component.translatable("message.silent_sun.redios.all_inactive_warning")
                .withStyle(ChatFormatting.RED);
            for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
                ServerPlayer participant = this.getServerPlayer(id);
                if (participant == null) continue;
                participant.sendSystemMessage(this.rediosSigned(inactiveWarn));
            }
        }
        if (this.allParticipantsDeadTicks < 200) {
            return false;
        }
        this.allParticipantsDeadTicks = 0;
        this.bossLeaveNoLoot();
        return true;
    }

    private boolean checkAllParticipantsDisengaged(ServerLevel serverLevel) {
        // 2026-09-11 用户裁决（S4 选项 A）：脱战判定**统一读配置** battleRadiusBlocks（默认 72）+ 60 秒宽限。
        // 原实现写死 64 格 / 5 秒，与配置(72)、设计稿 §3.6(72 格 + 60 秒) 倒挂 → 65~71 格是"死带"：
        // 玩家被击退到 68 格站 5 秒就被判全员脱战 → Boss 无奖励退场（明明还在战斗半径内）。
        double radius = RediosRules.battleRadiusBlocks();
        double radiusSqr = radius * radius;
        int graceTicks = 1200; // 60 秒
        if (this.battleParticipants.isEmpty()) {
            this.disengageTicks = 0;
            return false;
        }
        int active = 0;
        boolean anyClose = false;
        for (UUID id : this.battleParticipants) {
            ServerPlayer player = this.getServerPlayer(id);
            if (this.expelledPlayers.contains(id) || player == null || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) {
                continue;
            }
            ++active;
            // 2026-09-11（代码审计 P2 修复）：与 tickChunkRetention 的口径统一——只按水平（XZ）距离。
            // 原用 player.distanceToSqr(this)（含 Y 轴），Boss 高度飞行去追人时，地面玩家会被垂直差
            // 误判为「超出战斗半径」→ 60 秒后判全员脱战 → Boss 无奖励退场（明明水平还在圈内）。
            double dx = player.getX() - this.getX();
            double dz = player.getZ() - this.getZ();
            if (dx * dx + dz * dz <= radiusSqr) {
                anyClose = true;
                break;
            }
        }
        if (active == 0 || anyClose) {
            this.disengageTicks = 0;
            return false;
        }
        ++this.disengageTicks;
        if (this.disengageTicks < graceTicks) {
            return false;
        }
        this.disengageTicks = 0;
        this.bossLeaveNoLoot();
        return true;
    }

    private boolean checkBattleAreaUnloaded(ServerLevel serverLevel) {
        // 2026-09-01 用户裁决「区块都卸载了就让人走吧」：不再对安全窗口（投票/转阶段/濒死）
        // 豁免区块卸载结算——玩家走远导致区块卸载（连续 200 tick）就退场，冻结态也退。
        boolean playerOnly = BossTargeting.playerOnlyMode();
        if (playerOnly) {
            if (this.battleParticipants.isEmpty()) {
                this.battleAreaUnloadedTicks = 0;
                return false;
            }
            if (this.getActiveParticipantCount() == 0 && this.getCrossDimensionAliveCount() == 0) {
                this.battleAreaUnloadedTicks = 0;
                return false;
            }
        } else if (!this.mobBattleEngaged || this.getActiveMobParticipantCount() == 0) {
            this.battleAreaUnloadedTicks = 0;
            return false;
        }
        boolean anyTicking = false;
        for (UUID id : this.battleParticipants) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || !player.isAlive() || player.level() != this.level() || !serverLevel.isPositionEntityTicking(player.blockPosition()) || player.isCreative() && !(player.distanceToSqr(this) <= 4096.0)) continue;
            anyTicking = true;
            break;
        }
        if (!anyTicking && !playerOnly) {
            for (UUID id : new HashSet<UUID>(this.mobParticipants)) {
                LivingEntity mob = this.getMobParticipant(id);
                if (mob == null || !serverLevel.isPositionEntityTicking(mob.blockPosition())) continue;
                anyTicking = true;
                break;
            }
        }
        if (anyTicking) {
            this.battleAreaUnloadedTicks = 0;
            return false;
        }
        ++this.battleAreaUnloadedTicks;
        if (this.battleAreaUnloadedTicks < 200) {
            return false;
        }
        this.battleAreaUnloadedTicks = 0;
        this.leaveReason = LeaveReason.CHUNK_UNLOAD;
        if (this.isColorlessActive() || this.isVoidAllThingsActive()) {
            this.resolveColorlessChallengeSuccess(serverLevel);
            return true;
        }
        this.bossLeaveNoLoot();
        return true;
    }

    private ItemStack createDefeatBookAndQuill() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(Filterable.passThrough(Component.translatable("book.silent_sun.redios.defeat.page0")));
        WrittenBookContent content = new WrittenBookContent(Filterable.passThrough(RediosRules.rediosDefeatBookTitle()), RediosRules.rediosBookAuthor(), 0, pages, true);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private int getEnrageLevel() {
        return this.stats.enrageLevel();
    }

    private boolean isEnrageMax() {
        return this.getEnrageLevel() >= 10;
    }

    private boolean isDebuffImmune() {
        return this.ashDawnUnlocked || this.isEnrageMax();
    }

    private void ensureEnrageForTwilightMoment() {
        if (this.getEffect(ModEffects.ENRAGE) != null) {
            return;
        }
        this.grantEnrageLevels(10);
    }

    private void tickEnrage() {
        MobEffectInstance effect = this.getEffect(ModEffects.ENRAGE);
        if (effect == null) {
            this.enrageStackCooldownTicks = 0;
            return;
        }
        if (effect.getAmplifier() > 9) {
            this.addEffect(new MobEffectInstance(ModEffects.ENRAGE, effect.getDuration(), 9, effect.isAmbient(), effect.isVisible()), this);
        }
        this.tickEnrageAntiStun();
    }

    private void tickEnrageAntiStun() {
        if (!this.isEnrageMax()) {
            return;
        }
        if (this.isNoAi()) {
            this.setNoAi(false);
        }
        this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
        this.goalSelector.enableControlFlag(Goal.Flag.LOOK);
        this.goalSelector.enableControlFlag(Goal.Flag.JUMP);
        this.targetSelector.enableControlFlag(Goal.Flag.TARGET);
    }

    private void tickEnrageStacking() {
        MobEffectInstance current;
        boolean active;
        if (this.bossState.isVoteOrTransition()) {
            this.enrageStackCooldownTicks = 0;
            return;
        }
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        boolean firmFaith = this.isFirmFaithActive();
        active = this.phase == 1 ? firmFaith : this.enrageStackingUnlocked;
        if (!active) {
            this.enrageStackCooldownTicks = 0;
            return;
        }
        if (this.enrageStackCooldownTicks > 0) {
            --this.enrageStackCooldownTicks;
            return;
        }
        this.enrageStackCooldownTicks = 60;
        this.grantEnrageLevels(1);
        if (firmFaith && this.isEnrageMax() && (current = this.getEffect(ModEffects.ENRAGE)) != null && current.getDuration() < 1000000000) {
            this.addEffect(new MobEffectInstance(ModEffects.ENRAGE, 1000000000, current.getAmplifier(), true, true), this);
        }
    }

    private void grantEnrageLevels(int levels) {
        if (levels <= 0) {
            return;
        }
        MobEffectInstance current = this.getEffect(ModEffects.ENRAGE);
        int currentAmp = current == null ? -1 : current.getAmplifier();
        int desiredAmp = Math.min(9, currentAmp + levels);
        if (current == null) {
            desiredAmp = Math.min(9, levels - 1);
        }
        int duration = current == null ? 1000000000 : Math.max(current.getDuration(), 1000000000);
        this.addEffect(new MobEffectInstance(ModEffects.ENRAGE, duration, desiredAmp, true, true), this);
        // 2026-09-10（用户裁决 D1）：脆弱施加给「参战玩家」而不是 Boss 自身——
        // 原写法把 FRAGILE 挂在自己身上且被自身负面免疫拒绝，整条 2.8 脆弱链是死代码。
        // 追伤通道：CommonEvents.applyFragileDamage（受击 Post，按 (amp+1)×5% 追加魔法真伤）。
        // B-03（2026-09-11 依设计 §3.9「满层激怒时**再次**获得激怒才触发脆弱」）：判据用**本次授予前**
        // 的等级 currentAmp，而不是授予后的 desiredAmp——否则"刚好到满层的那一次"就立刻叠脆弱，比设计早一拍。
        if (currentAmp >= EnrageEffect.FRAGILE_TRIGGER_LEVEL) {
            for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
                ServerPlayer participant = this.getServerPlayer(id);
                if (participant == null || this.expelledPlayers.contains(id) || participant.isSpectator()
                        || participant.isCreative() || !participant.isAlive()
                        || participant.level() != this.level()) continue;
                EnrageEffect.applyFragile(participant, desiredAmp);
            }
        }
        // 2026-09-11（代码审计 G17 #9 修复）：原 else 分支在此重复清理参战者的脆弱，
        // 但 tickFragileBinding（每 tick 调用、过滤条件更完整：判 expelled / 旁观 / 创造 /
        // 存活 / 维度）做的是同一件事且判据一致，同一 tick 内会覆盖此处 —— 属冗余路径。已删除。
    }

    /**
     * B-02（2026-09-11 依设计 §3.9「脆弱持续时间与激怒绑定——激怒结束则脆弱结束」落地）：
     * 每 tick 复核——Boss 当前激怒等级低于触发阈值（含激怒被清除、掉回、战斗结束）时，
     * 把参战者身上的「脆弱」撤掉。原先脆弱是 `INFINITE_DURATION` 且只有战后退场清理，
     * 会出现"激怒早没了、脆弱还在且无限"的残留。
     */
    private void tickFragileBinding() {
        MobEffectInstance enrage = this.getEffect(ModEffects.ENRAGE);
        int amp = enrage == null ? -1 : enrage.getAmplifier();
        if (amp >= EnrageEffect.FRAGILE_TRIGGER_LEVEL) {
            return;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer participant = this.getServerPlayer(id);
            if (participant != null && participant.hasEffect(ModEffects.FRAGILE)) {
                participant.removeEffect(ModEffects.FRAGILE);
            }
        }
    }

    private void triggerWeaknessCurse() {
        if (this.weaknessCurseActive) {
            return;
        }
        this.weaknessCurseActive = true;
        MutableComponent msg = Component.translatable("message.silent_sun.redios.colorless_curse").withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : this.bossEvent.getPlayers()) {
            player.sendSystemMessage(this.rediosSigned(msg));
        }
        this.tickWeaknessCurse();
    }

    private void tickWeaknessCurse() {
        if (!this.weaknessCurseActive) {
            return;
        }
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }
        int duration = Math.max(0, RediosRules.colorlessWeaknessDurationTicks());
        if (duration <= 0) {
            return;
        }
        int amp = Math.max(0, RediosRules.colorlessWeaknessAmplifier());
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amp, true, false));
        }
        this.forEachMobOpponent(target -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, amp, true, false)));
    }

    boolean isColorlessActive() {
        return this.phase == 2 && this.titleIndex == 8;
    }

    boolean isVoidAllThingsActive() {
        return this.phase == 2 && this.titleIndex == 9;
    }

    private void tickVoidAllThings() {
        // 2026-09-02：2.9 传送攻击能力永久化——进入 2.9 解锁 voidAllThingsUnlocked 后，
        // 逆推回 2.8/更早头衔（COMBAT 期间）也保持传送/黑暗/反应式避让，直至 Boss 死亡。
        if (!this.voidAllThingsUnlocked && !this.isVoidAllThingsActive()) {
            return;
        }
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel2 = (ServerLevel)level;
        this.checkVoidBattleRange(serverLevel2);
        if (this.voidTeleportCooldown > 0) {
            --this.voidTeleportCooldown;
        }
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            int duration = RediosRules.voidAllThingsDarknessDurationTicks();
            if (duration <= 0) {
                duration = 40;
            }
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, true, false));
        }
        int duration = RediosRules.voidAllThingsDarknessDurationTicks();
        if (duration <= 0) {
            duration = 40;
        }
        int darknessDuration = duration;
        this.forEachMobOpponent(target -> target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, darknessDuration, 0, true, false)));
        // 反应式避让传送：轨道 B 命中立体锁定后置的标志，独立于 40 tick 常规冷却，
        // 立即执行一次脱离传送（复用轨道 A 的球体抬升落点），执行后清标志。
        if (this.voidDodgeTeleportPending) {
            this.voidDodgeTeleportPending = false;
            LivingEntity dodgeTarget = this.pickVoidTeleportTarget(serverLevel2);
            if (dodgeTarget != null) {
                this.teleportToAttackEdge(serverLevel2, dodgeTarget);
            }
            return;
        }
        if (this.voidTeleportCooldown > 0) {
            return;
        }
        LivingEntity target2 = this.pickVoidTeleportTarget(serverLevel2);
        if (target2 == null) {
            return;
        }
        this.voidTeleportCooldown = Math.max(this.getAttackCooldownTicks(), RediosRules.voidAllThingsTeleportCooldownTicks());
        this.teleportToAttackEdge(serverLevel2, target2);
        this.forceSetTarget(target2);
    }

    private LivingEntity pickVoidTeleportTarget(ServerLevel serverLevel) {
        if (!BossTargeting.playerOnlyMode()) {
            LivingEntity t = this.getTarget();
            // M13：非玩家模式也纳入玩家目标（纯玩家时传送不再停摆）
            if (t != null && t.isAlive() && t.level() == this.level()) {
                return t;
            }
            return null;
        }
        ArrayList<ServerPlayer> candidates = new ArrayList<ServerPlayer>();
        for (UUID uUID : this.battleParticipants) {
            ServerPlayer player;
            if (this.expelledPlayers.contains(uUID) || (player = this.getServerPlayer(uUID)) == null || player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
            candidates.add(player);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        ArrayList<ServerPlayer> noCover = new ArrayList<ServerPlayer>();
        for (ServerPlayer p : candidates) {
            if (!this.getSensing().hasLineOfSight(p)) continue;
            noCover.add(p);
        }
        ArrayList<ServerPlayer> arrayList = noCover.isEmpty() ? candidates : noCover;
        return (ServerPlayer)arrayList.get(this.random.nextInt(arrayList.size()));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void teleportToAttackEdge(ServerLevel serverLevel, LivingEntity player) {
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        double distance = Math.max(reach + 0.5, 5.5);
        BlockPos base = player.blockPosition();
        for (int i = 0; i < 16; ++i) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dx = Math.cos(angle) * distance;
            double dz = Math.sin(angle) * distance;
            BlockPos pos = new BlockPos((int)((double)base.getX() + dx), base.getY(), (int)((double)base.getZ() + dz));
            BlockPos feet = serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
            BlockPos head = feet.above();
            // M12：清落点方块遵循 mobGriefing（与 detonateStarfallSalvo 口径一致）
            boolean canGrief = serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
            if (!serverLevel.getBlockState(feet).isAir() && canGrief) {
                serverLevel.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            }
            if (!serverLevel.getBlockState(head).isAir() && canGrief) {
                serverLevel.setBlock(head, Blocks.AIR.defaultBlockState(), 3);
            }
            Vec3 dest = Vec3.atBottomCenterOf((Vec3i)feet);
            // 轨道 A：2.9 主动避让玩家立体锁定球体（无妄之终）——2026-09-02 永久化
            //（voidAllThingsUnlocked，逆推保持），相交则抬升到球顶+1。
            if (this.voidAllThingsUnlocked) {
                Vec3 avoided = this.avoidVoidSphere(dest);
                if (avoided != null) {
                    if (avoided.y - (double)feet.getY() <= 32.0) {
                        dest = avoided;
                    } else if (i < 12) {
                        continue;
                    }
                    // else：多次重选仍避不开（球体过大），接受贴地落点。
                }
            }
            if (!serverLevel.noCollision(this, this.getBoundingBox().move(dest.subtract(this.position())))) continue;
            this.allowSelfTeleport = true;
            try {
                this.teleportTo(dest.x, dest.y, dest.z);
            }
            finally {
                this.allowSelfTeleport = false;
            }
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.0f, 1.0f);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0f, 1.0f);
            return;
        }
    }

    /**
     * phase2.9 轨道 A：判断 Boss 落点 AABB 是否与任一战斗参与者玩家的立体锁定球体
     * （以玩家为中心、半径 = 交互距离 × 2）相交。相交返回抬升到球顶+1 的落点，
     * 不相交返回 null（保持贴地）。
     */
    private Vec3 avoidVoidSphere(Vec3 dest) {
        AABB bossBox = this.getBoundingBox().move(dest.subtract(this.position()));
        boolean intersects = false;
        double liftY = dest.y;
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer p = this.getServerPlayer(id);
            if (p == null || !p.isAlive() || p.isSpectator() || p.isCreative() || p.level() != this.level()) continue;
            double radius = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) * 2.0;
            Vec3 c = p.position();
            AABB sphere = new AABB(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
            if (bossBox.intersects(sphere)) {
                intersects = true;
                liftY = Math.max(liftY, c.y + radius + 1.0);
            }
        }
        return intersects ? new Vec3(dest.x, liftY, dest.z) : null;
    }

    /**
     * T-v3-7：玩家挥刀（DoSlashEvent）回调。仅当 Boss 在战斗中且与玩家距离 > 玩家交互
     * 距离（远程立体锁定）时触发反向冲刺，脱离无妄之终球体；冷却防抖。
     */
    void onPlayerRemoteSlash(Player player) {
        if (this.reverseDashCooldownTicks > 0) return;
        if (this.bossState.isVoteOrTransition() || this.isDarkStarBlastOngoing()) return;
        if (player == null || !player.isAlive()) return;
        if (!this.battleParticipants.contains(player.getUUID())) return;
        if (this.distanceTo(player) <= player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)) return;
        this.requestReverseDash(player);
    }

    /** 反向激流冲刺：方向 = Boss 水平位置 − 玩家水平位置，速度同 RediosRiptideDashGoal 的 1.9 格/tick。 */
    private void requestReverseDash(LivingEntity awayFrom) {
        if (this.reverseDashCooldownTicks > 0) return;
        Vec3 dir = this.position().subtract(awayFrom.position());
        double hLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        this.reverseDashDirX = hLen > 1.0E-6 ? dir.x / hLen : 0.0;
        this.reverseDashDirZ = hLen > 1.0E-6 ? dir.z / hLen : 0.0;
        this.reverseDashTicks = 10;
        this.reverseDashCooldownTicks = 50;
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void checkVoidBattleRange(ServerLevel serverLevel) {
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            // 2026-09-11（代码审计 P2 修复）：统一为水平（XZ）距离，同 tickChunkRetention / 脱战判定。
            // 原用 this.distanceToSqr(player)（含 Y 轴），2.9 期间 Boss 高飞时地面玩家会被垂直差
            // 误判为「超出 64 格」而即时逐出。
            double vdx = player.getX() - this.getX();
            double vdz = player.getZ() - this.getZ();
            if (vdx * vdx + vdz * vdz <= VOID_BATTLE_RANGE_BLOCKS_SQR) continue;
            ChunkPos cp = player.chunkPosition();
            if (!serverLevel.getChunkSource().hasChunk(cp.x, cp.z)) continue;
            player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.expelled").withStyle(ChatFormatting.DARK_RED)));
            // 2026-09-11（代码审计 G17 修复）：改为复用统一入口 expelFromBattle。
            // 原先的内联实现只做「remove 参战者 + 清格挡统计 + cleanupPlayerAfterBattle」，
            // 漏掉该入口的 4 项状态更新：expelledPlayers 登记（被逐出者不算已逐出，可能再次入战）、
            // twilightTimedMissingTicks / twilightTimedMissingFromApply 两个计时表、
            // hardcoreProtectedPlayers（硬核 1 血保护残留）；且 allExpelledLeavePending 的判据
            // 少了 playerOnlyMode()，Mode 2（斗蛐蛐）下会误判全员离场而让 Boss 无奖励退场。
            this.expelFromBattle(player);
            SilentSunMod.LOGGER.warn("[Redios] 2.9 超距逐出：{}", player.getName().getString());
        }
    }

    /**
     * 「阻断外部传送」的唯一判据（2026-09-11 代码审计 G17 #2 修复）。
     * <p>
     * 口径依 2.9 传送能力永久化的既有注释：进入 2.9 解锁 {@code voidAllThingsUnlocked} 后**永久化**，
     * 逆推回 2.8/更早头衔（COMBAT 期间）也保持，直至 Boss 死亡。
     * <p>
     * 原先六参重载用 {@code voidAllThingsUnlocked}（永久位），三参重载却用
     * {@code isVoidAllThingsActive()}（= {@code phase == 2 && titleIndex == 9}，逆推后即为 false）
     * → Boss 进过 2.9 再退回 2.8 时，任何走三参重载的第三方/原版传送都能把它挪走，
     * 2.8 阶段的反传送形同虚设。现两处共用本判据，避免第三次发散。
     */
    private boolean isTeleportBlocked() {
        return !this.allowSelfTeleport && this.voidAllThingsUnlocked;
    }

    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> movements, float yRot, float xRot) {
        if (this.isTeleportBlocked()) {
            // 2026-09-02：2.9 阻止外部传送能力永久化（逆推保持，直至 Boss 死亡）
            return false;
        }
        boolean ok = super.teleportTo(level, x, y, z, movements, yRot, xRot);
        if (this.allowSelfTeleport) {
            this.ensureSelfTeleportApplied(x, y, z);
        }
        return ok;
    }

    public void teleportTo(double x, double y, double z) {
        if (this.isTeleportBlocked()) {
            return;
        }
        super.teleportTo(x, y, z);
        if (this.allowSelfTeleport) {
            this.ensureSelfTeleportApplied(x, y, z);
        }
    }

    /**
     * A-01（2026-09-11 依设计 L453「2.9 传送无视一切传送禁止/拦截（含其他模组）」补齐）：
     * **传送结果校验 + 直写坐标兜底**。
     * <p>
     * 说明：NeoForge 的 {@code EntityTeleportEvent} 只覆盖指令 / 末影珍珠 / 紫颂果等少数来源，
     * 拦不住 mixin 型或自定义的传送拦截；而 2.9「空无万象」的传送是 Boss 自身行为，必须一定生效。
     * 因此不依赖任何事件，改为**校验结果**：若 {@code super.teleportTo} 没能把实体真正挪过去
     * （被第三方取消 / 改道 / 夹回原处），直接 {@code setPos} 写坐标并标记位置同步。
     * 仅在 {@code allowSelfTeleport}（本模组自己的传送）时启用，不影响外部对本模组的传送限制。
     */
    private void ensureSelfTeleportApplied(double x, double y, double z) {
        double dx = this.getX() - x;
        double dy = this.getY() - y;
        double dz = this.getZ() - z;
        if (dx * dx + dy * dy + dz * dz <= 1.0) {
            return; // 已在目标点（±1 格容差）
        }
        SilentSunMod.LOGGER.warn("[Redios] 2.9 传送被外部拦截，已直写坐标兜底：目标=({}, {}, {}) 实际=({}, {}, {})",
            (int)x, (int)y, (int)z, (int)this.getX(), (int)this.getY(), (int)this.getZ());
        this.setPos(x, y, z);
        this.setDeltaMovement(0.0, 0.0, 0.0);
        this.hurtMarked = true; // 触发客户端位置/速度同步
    }

    private ItemStack createVictoryBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(Filterable.passThrough(Component.translatable("book.silent_sun.redios.victory.page0")));
        WrittenBookContent content = new WrittenBookContent(Filterable.passThrough(RediosRules.rediosVictoryBookTitle()), RediosRules.rediosBookAuthor(), 0, pages, true);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
        return book;
    }

    private void enableBossOutline(ServerLevel serverLevel) {
        String teamName;
        if (this.bossOutlineEnabled) {
            return;
        }
        ServerScoreboard sb = serverLevel.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(teamName = "silent_sun_boss");
        if (team == null) {
            team = sb.addPlayerTeam(teamName);
            team.setColor(ChatFormatting.AQUA);
        }
        team.getPlayers().add(this.getStringUUID());
        this.setGlowingTag(true);
        this.bossOutlineEnabled = true;
    }

    private void disableBossOutline(ServerLevel serverLevel) {
        if (!this.bossOutlineEnabled) {
            return;
        }
        this.setGlowingTag(false);
        ServerScoreboard sb = serverLevel.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam("silent_sun_boss");
        if (team != null) {
            team.getPlayers().remove(this.getStringUUID());
        }
        this.bossOutlineEnabled = false;
    }

    private void ensureMandatoryLoot(List<ItemStack> loot, RediosBookOutcome outcome) {
        if (loot == null) {
            return;
        }
        boolean hasBook = false;
        boolean hasBeacon = false;
        boolean hasDiamondBlocks = false;
        Iterator<ItemStack> it = loot.iterator();
        while (it.hasNext()) {
            String title;
            String hover;
            ItemStack stack = it.next();
            if (stack == null || stack.isEmpty()) continue;
            if ((stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK)) && (DROP_LIST_BOOK_TITLE.equals(hover = stack.getHoverName().getString()) || DROP_STATS_BOOK_TITLE.equals(hover))) {
                it.remove();
                continue;
            }
            if (stack.is(Items.WRITTEN_BOOK) && (OUTCOME_BOOK_LEGACY_TITLE.equals(title = stack.getHoverName().getString()) || OUTCOME_BOOK_TITLE.equals(title))) {
                if (!hasBook) {
                    this.applyOutcomeBookContent(stack, outcome);
                    hasBook = true;
                    continue;
                }
                it.remove();
                continue;
            }
            if ("\u8bb0\u8f7d\u7269\u54c1".equals(stack.getHoverName().getString())) {
                it.remove();
                continue;
            }
            if (!hasBeacon && stack.is(Items.BEACON)) {
                hasBeacon = true;
            }
            if (hasDiamondBlocks || !stack.is(Items.DIAMOND_BLOCK) || stack.getCount() < 64) continue;
            hasDiamondBlocks = true;
        }
        if (!hasBook) {
            loot.add(this.createOutcomeBook(outcome));
        }
        // 信标一组（64）：两阶段都掉（2026-09-04 用户裁决「两个阶段信标数量都改成一组」——
        // 原实现仅 P2 补 1 个、P1 无）。缺则补 64，已有（override 含）不重复。
        if (!hasBeacon) {
            loot.add(new ItemStack(Items.BEACON, 64));
        }
        if (outcome == RediosBookOutcome.PHASE2_WIN) {
            if (!hasDiamondBlocks) {
                loot.add(new ItemStack(Items.DIAMOND_BLOCK, 64));
            }
        }
    }

    private ItemStack createOutcomeBook(RediosBookOutcome outcome) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        this.applyOutcomeBookContent(book, outcome);
        return book;
    }

    private void applyOutcomeBookContent(ItemStack book, RediosBookOutcome outcome) {
        String text = this.resolveOutcomeBookText(outcome);
        List<Filterable<Component>> pages = List.of(Filterable.passThrough(Component.literal((String)text)));
        WrittenBookContent content = new WrittenBookContent(Filterable.passThrough(OUTCOME_BOOK_TITLE), RediosRules.rediosBookAuthor(), 0, pages, true);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);
    }

    private String resolveOutcomeBookText(RediosBookOutcome outcome) {
        MinecraftServer server = null;
        Level level = this.level();
        if (level instanceof ServerLevel) {
            server = ((ServerLevel)level).getServer();
        }
        return switch (outcome.ordinal()) {
            case 0 -> this.applyOutcomePlaceholders(BookTextCache.getOrDefault(server, RediosRules.rediosOutcomeTextPhase1WinOnlyFile(), "\u505a\u7684\u4e0d\u9519\uff0c\u652f\u6301\u4e0b\u6b21\u518d\u6765"));
            case 1 -> this.applyOutcomePlaceholders(BookTextCache.getOrDefault(server, RediosRules.rediosOutcomeTextPhase1WinPhase2LoseFile(), RediosRules.rediosNotePhase1WinPhase2Lose()));
            case 2 -> this.applyOutcomePlaceholders(BookTextCache.getOrDefault(server, RediosRules.rediosOutcomeTextPhase2WinFile(), "\u6211\u5e94\u6025\u63aa\u65bd\u53d1\u52a8\u4e86\uff0c\u6253\u5230\u8fd9\u5c31\u884c\u4e86\uff0c\u4f60\u5e94\u8be5\u6253\u723d\u4e86\u5427\uff1f\u6211\u80af\u5b9a\u662f\u6253\u723d\u4e86"));
            default -> throw new MatchException(null, null);
        };
    }

    private boolean hasClearedPhase1ForLoot() {
        return this.bossState.isPhase2() || this.bossState == BossState.PHASE1_VOTE;
    }

    List<ItemStack> createPhase1Loot(ServerLevel serverLevel, boolean includeDefeatBook) {
        int rewardTitleIndex;
        int rewardPhase = this.phase == 2 ? 1 : this.phase;
        ArrayList<ItemStack> loot = new ArrayList<ItemStack>(RediosRewardOverrideConfig.getOverrideStacks(rewardPhase, rewardTitleIndex = this.phase == 2 ? PHASE1_TITLES.size() - 1 : this.titleIndex));
        if (loot.isEmpty()) {
            loot.addAll(RediosLootConfig.roll(serverLevel.random));
        }
        this.ensureMandatoryLoot(loot, includeDefeatBook ? RediosBookOutcome.PHASE1_WIN_PHASE2_LOSE : RediosBookOutcome.PHASE1_WIN_ONLY);
        loot.add(new ItemStack(ModItems.REDIOS_DISC_PHASE1.get()));
        // 灭却之日（required 前置）提供的「长梦彼端的灾厄之影」：数量由配置决定（B6 数量配置化）。
        this.addCalamityShadow(loot, serverLevel,
            SilentSunConfig.CALAMITY_SHADOW_PHASE1_MIN.get(), SilentSunConfig.CALAMITY_SHADOW_PHASE1_MAX.get());
        return loot;
    }

    private List<ItemStack> createPhase2Loot(ServerLevel serverLevel, boolean doubleRollOnEmpty) {
        ArrayList<ItemStack> loot = new ArrayList<ItemStack>(RediosRewardOverrideConfig.getOverrideStacks(2, this.titleIndex));
        boolean hasOverride = !loot.isEmpty();
        loot.add(new ItemStack(ModItems.REDIOS_DISC_PHASE2.get()));
        // 二阶段额外掉落（2026-09-04 用户裁决）：龙蛋 ×2 + 耀魂方块（slashblade）一组 64。
        loot.add(new ItemStack(Items.DRAGON_EGG, 2));
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("slashblade", "proudsoul_trapezohedron"))
            .ifPresent(trapezohedron -> loot.add(new ItemStack(trapezohedron, 64)));
        if (!hasOverride) {
            loot.addAll(RediosLootConfig.roll(serverLevel.random));
            if (doubleRollOnEmpty) {
                loot.addAll(RediosLootConfig.roll(serverLevel.random));
            }
        }
        // 灭却之日（required 前置）提供的二阶段「长梦彼端的灾厄之影」：数量由配置决定
        //（2026-09-10 用户裁决 B6：数量配置化）。默认 8~13 = 原「固定 1 + 随机 7~12」的合计。
        this.addCalamityShadow(loot, serverLevel,
            SilentSunConfig.CALAMITY_SHADOW_PHASE2_MIN.get(), SilentSunConfig.CALAMITY_SHADOW_PHASE2_MAX.get());
        return loot;
    }

    /**
     * 添加灭却之日「长梦彼端的灾厄之影」掉落（三处掉落点共用的唯一入口）。
     * <p>
     * 走注册表查找而非反射，避免编译期硬依赖；灭却之日未提供该物品时静默跳过；
     * 数量区间由 {@code redios.calamityShadow*} 配置决定（2026-09-10 用户裁决 B6）。
     */
    private void addCalamityShadow(List<ItemStack> loot, ServerLevel serverLevel, int min, int max) {
        if (max < min) {
            max = min;
        }
        int count = min + (max > min ? serverLevel.random.nextInt(max - min + 1) : 0);
        if (count <= 0) {
            return;
        }
        BuiltInRegistries.ITEM
            .getOptional(ResourceLocation.fromNamespaceAndPath("extinction_day_mod_1784441698", "calamity_shadow"))
            .ifPresent(item -> loot.add(new ItemStack(item, count)));
    }

    private String applyOutcomePlaceholders(String template) {
        if (template == null) {
            return "";
        }
        String dimension = this.level().dimension().location().toString();
        BlockPos pos = this.blockPosition();
        int durationSeconds = 0;
        Level level = this.level();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            if (this.battleStartGameTime >= 0L) {
                durationSeconds = (int)Math.max(0L, (serverLevel.getGameTime() - this.battleStartGameTime) / 20L);
            }
        }
        String participants = this.formatOutcomeParticipants();
        return template.replace("{dimension}", dimension).replace("{x}", Integer.toString(pos.getX())).replace("{y}", Integer.toString(pos.getY())).replace("{z}", Integer.toString(pos.getZ())).replace("{participants}", participants).replace("{duration_seconds}", Integer.toString(durationSeconds));
    }

    private String formatOutcomeParticipants() {
        Set<UUID> ids = this.initialParticipants.isEmpty() ? this.battleParticipants : this.initialParticipants;
        Set<UUID> set = ids;
        if (ids.isEmpty()) {
            return "0";
        }
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return Integer.toString(ids.size());
        }
        ServerLevel serverLevel = (ServerLevel)level;
        ArrayList<String> names = new ArrayList<String>();
        for (UUID id : ids) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
            if (player == null) continue;
            names.add(player.getName().getString());
        }
        if (!names.isEmpty()) {
            return String.join((CharSequence)", ", names);
        }
        return Integer.toString(ids.size());
    }

    private Component getBossBarName() {
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        Component title = titles.get(Mth.clamp((int)this.titleIndex, 0, titles.size() - 1));
        MutableComponent base = this.getType().getDescription().copy().append(Component.literal(" \u00b7 ")).append(title);
        if (this.bossState.isVoteOrTransition()) {
            return base;
        }
        // §7 攻略 A2/D2：锁血倒计时对**每个头衔**可见（归零瞬间 = 5s 真输出窗口开启）。
        // 原实现在「锁血结束即强制推进、不进入 5s 窗口」的 3 个特例头衔（P1 7/8、P2 5）上才显示，语义倒置。
        if (this.titleLockTicks <= 0) {
            return base;
        }
        int tenths = Mth.clamp((int)((this.titleLockTicks * 10 + 19) / 20), 0, 9999);
        int sec = tenths / 10;
        int dec = tenths % 10;
        return base.copy().append(Component.literal(" ")).append(Component.translatable("bossbar.silent_sun.redios.transition_time", new Object[]{sec, dec}).withStyle(ChatFormatting.GOLD));
    }

    private int getMinTitleSeconds() {
        if (this.phase == 1) {
            return SilentSunConfig.PHASE1_TITLE_MIN_SECONDS.get();
        }
        return Math.max(30, SilentSunConfig.PHASE2_TITLE_MIN_SECONDS.get());
    }

    private int titleLockDurationTicks() {
        return this.getMinTitleSeconds() * 20;
    }

    private int colorlessChallengeDurationTicks() {
        int configured = Math.max(0, SilentSunConfig.COLORLESS_CHALLENGE_SECONDS.get()) * 20;
        int minimum = this.getMinTitleSeconds() * 2 * 20;
        return Math.max(configured, minimum);
    }

    private float titleSegment() {
        List<Component> titles = this.phase == 1 ? PHASE1_TITLES : PHASE2_TITLES;
        return this.getMaxHealth() / (float)titles.size();
    }

    private void onTitleChanged(int oldPhase, int oldTitleIndex, int newPhase, int newTitleIndex) {
        // B-7（2026-09-11 依设计 T-v3-9「一阶段仅以 log 记录阶段流程」补齐）：此前 onTitleChanged 内
        // 一条日志都没有，头衔推进出问题时无法回溯；一阶段只记录、不打扰玩家（二阶段有 BossBar/广播）。
        if (newPhase == 1) {
            SilentSunMod.LOGGER.info("[Redios] 头衔推进（一阶段）：phase={} title={} → phase={} title={}",
                oldPhase, oldTitleIndex, newPhase, newTitleIndex);
        }
        // 2026-09-10（用户裁决 C3 / Q13）：一阶段结束 → 断魂退场标记复位。
        // 二阶段断魂由 2.0「海天之隙」独立授予（seaSkySoulSeverUnlocked），不受此标记约束。
        if (newPhase == 2 && oldPhase == 1) {
            this.soulSeverRetiredInPhase1 = false;
        }
        ServerPlayer player;
        TitleDef[] defs;
        Level level;
        this.clearAllExternalEffects();
        this.reapplySelfBuffs();
        if (oldPhase == 2 && oldTitleIndex == 6 && (newPhase != 2 || newTitleIndex != 6) && (level = this.level()) instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            this.restoreDarkStarSpecialBlocks(serverLevel);
        }
        // 2026-09-10（批次 2.7）：此处原有的三行字段赋值
        //   wrongInterferenceActive / chaosRuinActive / ashDawnActive = (newPhase==2 && newTitleIndex==N)
        // 已删除——三者改为由 phase/titleIndex 派生（isWrongInterferenceActive / isChaosRuinActive /
        // isAshDawnActive），不再需要在此同步，也就不会因 restoreStateFromNbt 绕过本方法而失效。
        if (this.isAshDawnActive()) {
            this.ashDawnUnlocked = true;
        }
        TitleDef[] titleDefArray = defs = newPhase == 1 ? PHASE1_TITLE_DEFS : PHASE2_TITLE_DEFS;
        if (newTitleIndex >= 0 && newTitleIndex < defs.length) {
            TitleDef def = defs[newTitleIndex];
            for (BossFlag flag : def.flagsGranted) {
                this.grantFlag(flag);
            }
        }
        this.seaSkySoulSeverUnlocked = this.hasFlag(BossFlag.SEA_SKY_SOUL_SEVER);
        this.soulSeverHarvestUnlocked = this.hasFlag(BossFlag.SOUL_SEVER_HARVEST);
        this.uncontrolledSprintUnlocked = this.hasFlag(BossFlag.UNCONTROLLED_SPRINT);
        this.guardUnlocked = this.hasFlag(BossFlag.GUARD_BLOCK);
        this.colorlessUnlocked = this.hasFlag(BossFlag.COLORLESS);
        this.ashDawnUnlocked = this.hasFlag(BossFlag.ASH_DAWN);
        this.chaosRuinAbsoluteAttacks = this.hasFlag(BossFlag.CHAOS_RUIN_ABSOLUTE);
        this.enrageStackingUnlocked = this.hasFlag(BossFlag.ENRAGE_STACKING);
        if (!this.attackRandomized && newPhase == 1 && newTitleIndex == 8) {
            this.attackRandomized = true;
        }
        if (!this.attackSpecialized && newPhase == 2 && newTitleIndex == 7) {
            this.attackSpecialized = true;
            this.specializedDamageType = this.findSpecializedDamageType();
        }
        this.blackSunUnlocked = this.hasFlag(BossFlag.BLACK_SUN);
        this.weaknessCurseActive = this.hasFlag(BossFlag.WEAKNESS_CURSE);
        if (this.colorlessUnlocked) {
            this.applyColorlessPermanentBuffs();
        }
        if (oldPhase == 2 && oldTitleIndex == 2 && (newPhase != 2 || newTitleIndex != 2)) {
            // 2026-09-11（代码审计 G06 #3 修复）：真值是 dodgeChance（已随回场快照持久化），
            // 原先额外 grant 的 DODGE_MIN_15 旗标**从不被 hasFlag 查询** → 已随常量一并删除。
            this.dodgeChance = Math.max(this.dodgeChance, 0.15);
        }
        if (oldPhase == 2 && oldTitleIndex == 3 && (newPhase != 2 || newTitleIndex != 3)) {
            this.grantFlag(BossFlag.CHAOS_RUIN_ABSOLUTE);
            this.chaosRuinAbsoluteAttacks = true;
        }
        // 2026-09-10 修复（对比表 C4）：原写法 `double d = isWhoseWishActive() ? 1.0 : (reflectRatio = …)`
        // 把 1.0 赋给了之后再未被引用的局部变量 d，导致 1.7「谁人之愿」期间 reflectRatio 从未被设为 1.0
        // ——「完全反伤」实际为 0%，玩家可白打（攻略 B17 的反制机制形同虚设）。
        // 1.7 属一阶段、2.8/2.9 属二阶段，二者互斥，故 1.7 直接给 1.0，其余按 colorlessReflectRatio。
        this.reflectRatio = this.isWhoseWishActive()
            ? 1.0
            : (this.colorlessUnlocked ? RediosRules.colorlessReflectRatio() : 0.0);
        if (newPhase == 1 && newTitleIndex == 2) {
            this.enrageStackCooldownTicks = 0;
        }
        if (newPhase == 2 && newTitleIndex == 8) {
            this.enrageStackCooldownTicks = 0;
            if (this.colorlessChallengeTicks < 0) {
                this.colorlessChallengeTicks = this.colorlessChallengeDurationTicks();
            }
        }
        if (newPhase == 1 && newTitleIndex == 4) {
            this.clearEffectsForRootlessPure();
            this.rootlessPureTickCounter = 0;
            for (UUID uUID : new HashSet<UUID>(this.battleParticipants)) {
                if (!(this.level() instanceof ServerLevel)) break;
                player = this.getServerPlayer(uUID);
                if (player == null || this.expelledPlayers.contains(uUID) || player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
                player.removeAllEffects();
                CommonEvents.markRootlessBuffBlock(player, this.getUUID());
            }
        }
        if (oldPhase == 1 && oldTitleIndex == 4 && (newPhase != 1 || newTitleIndex != 4)) {
            this.rootlessPureTickCounter = 0;
            if (this.level() instanceof ServerLevel) {
                for (UUID uUID : new HashSet<UUID>(this.battleParticipants)) {
                    player = this.getServerPlayer(uUID);
                    if (player == null) continue;
                    CommonEvents.clearRootlessBuffBlock(player);
                }
            }
        }
        if (newPhase == 1 && newTitleIndex == 5) {
            this.mirrorFaceLockedSoulSever = this.getSoulSeverValue();
            if (this.level() instanceof ServerLevel) {
                MutableComponent notice = Component.translatable("message.silent_sun.redios.mirror_face_soul_sever_total_prefix").append(Component.literal((String)Long.toString(this.mirrorFaceLockedSoulSever)).withStyle(ChatFormatting.GOLD));
                for (UUID uUID : new HashSet<UUID>(this.battleParticipants)) {
                    ServerPlayer player2 = this.getServerPlayer(uUID);
                    if (player2 == null || this.expelledPlayers.contains(uUID) || player2.isSpectator() || player2.isCreative() || !player2.isAlive()) continue;
                    player2.sendSystemMessage(this.rediosSigned(notice));
                    CommonEvents.markMirrorFaceAttackBoost(player2, this.getUUID(), this.mirrorFaceLockedSoulSever);
                }
            }
        }
        if (oldPhase == 1 && oldTitleIndex == 5 && (newPhase != 1 || newTitleIndex != 5)) {
            if (this.level() instanceof ServerLevel) {
                for (UUID uUID : new HashSet<UUID>(this.battleParticipants)) {
                    player = this.getServerPlayer(uUID);
                    if (player == null) continue;
                    CommonEvents.clearMirrorFaceAttackBoost(player);
                }
            }
            this.mirrorFaceLockedSoulSever = 0L;
        }
        if (newPhase == 1 && newTitleIndex == 7) {
            this.whoseWishTicker = 0;
            if (this.level() instanceof ServerLevel) {
                for (UUID uUID : new HashSet<UUID>(this.battleParticipants)) {
                    player = this.getServerPlayer(uUID);
                    if (player == null || this.expelledPlayers.contains(uUID) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
                    this.restorePlayerToFull(player);
                }
            }
        }
        if (oldPhase == 1 && oldTitleIndex == 1 && (newPhase != 1 || newTitleIndex != 1)) {
            this.healBoostTicks = 600;
            this.dustlessGoodBuffs.clear();
        }
        if (newPhase == 1 && newTitleIndex == 1) {
            this.healBoostTicks = 600;
        }
        if (newPhase == 1 && newTitleIndex == 8 && !this.soulSeverRetiredInPhase1 && this.level() instanceof ServerLevel) {
            for (UUID uUID : new HashSet<UUID>(this.battleParticipants)) {
                player = this.getServerPlayer(uUID);
                if (player == null || this.expelledPlayers.contains(uUID) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
                CommonEvents.markSharpenSoulSever(player, this.getUUID(), 0);
                player.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, 40, 0, true, true));
            }
        }
        MutableComponent msg = null;
        if (newPhase == 2) {
            msg = Component.translatable(("quote.silent_sun.redios.phase2." + Mth.clamp((int)newTitleIndex, (int)0, (int)(PHASE2_TITLES.size() - 1))));
        }
        if (msg != null) {
            for (ServerPlayer serverPlayer : this.bossEvent.getPlayers()) {
                serverPlayer.sendSystemMessage(this.rediosSigned(msg));
            }
        }
        if (newPhase == 2 && newTitleIndex == 5) {
            this.applyTwilightMomentOnEnter();
            this.ensureEnrageForTwilightMoment();
        }
        if (newPhase == 2 && newTitleIndex == 6) {
            this.performDarkStarBlast();
            if (RediosRules.lagProtectionEnabled() && this.level() instanceof ServerLevel) {
                this.tickFailsafe((ServerLevel)this.level());
            }
        }
        if (newPhase == 2 && newTitleIndex == 7) {
            this.blackSunTriggered = false;
        }
        if (newPhase == 2 && newTitleIndex == 9) {
            // 2.9 空无万象：永久解锁（2026-09-02）——2.8 效果 + 传送攻击能力，正推/逆推/
            // 状态切换直至 Boss 死亡均保持（逆推不削效果）。buff 由 applyColorlessPermanentBuffs
            // 无限时长统一挂（40 tick 版本会与永久版重复且可能干扰，不再单独挂）。
            this.voidAllThingsUnlocked = true;
            this.ashDawnUnlocked = true;
            this.dodgeChance = Math.max(this.dodgeChance, 0.15);
            this.chaosRuinAbsoluteAttacks = true;
            this.enrageStackingUnlocked = true;
            this.colorlessUnlocked = true;
            this.applyColorlessPermanentBuffs();
        }
    }

    private boolean isWallAttackEnabled() {
        return this.phase == 2 || this.phase == 1 && this.titleIndex >= 1;
    }

    /** 隔墙时「墙体厚度」达到该阈值即改走投掷突破，否则穿墙近战（单位：方块层数）。 */
    static final int WALL_THICKNESS_BREAK_THRESHOLD = 3;

    @Override
    public Set<UUID> expelledPlayers() {
        return this.expelledPlayers;
    }

    /**
     * 本场参战玩家 UUID 集合（{@link ITargetableHost} 契约）。
     * <p>
     * 2026-09-11（代码审计 G06 #2）：补入接口，供 {@code BossTargeting} 统一
     * 「有主宠物是否算合法攻击者」的判据（要求主人 ∈ 本集合），
     * 消除与索敌侧「主人必须参战」的口径分叉。
     */
    @Override
    public Set<UUID> battleParticipants() {
        return this.battleParticipants;
    }

    double getCurrentAttackReach() {
        return this.stats.attackReach();
    }

    /**
     * 统计 Boss 视线到目标之间第一段连续非空气方块的层数（墙体厚度）。
     * 用于隔墙时抉择「穿墙近战（薄墙）」vs「投掷突破（厚墙）」。有视线时返回 0。
     */
    int countWallThickness(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel level)) return 0;
        Vec3 from = this.getEyePosition();
        Vec3 to = target.getEyePosition();
        Vec3 dir = to.subtract(from);
        double dist = dir.length();
        if (dist < 1.0E-6) return 0;
        dir = dir.normalize();
        int thickness = 0;
        int steps = Math.min((int)Math.ceil(dist), 64);
        for (int i = 1; i <= steps; i++) {
            BlockPos pos = BlockPos.containing(from.add(dir.scale(i)));
            if (!level.getBlockState(pos).isAir()) {
                ++thickness;
            } else if (thickness > 0) {
                break;
            }
        }
        return thickness;
    }

    public int getAttackCooldownTicks() {
        return this.stats.attackCooldownTicks();
    }

    boolean isWeaponWeakpointWindowActive() {
        return this.weaponWeakpointSlowTicks > 0;
    }

    public void triggerWeaponWeakpoint(Player attacker) {
        if (!RediosRules.weaponWeakpointEnabled()) {
            return;
        }
        if (this.attackRecoveryTicks <= 0) {
            return;
        }
        if (this.weaponWeakpointCooldownTicks > 0 || this.weaponWeakpointSlowTicks > 0) {
            return;
        }
        this.weaponWeakpointCooldownTicks = RediosRules.weaponWeakpointCooldownTicks();
        this.weaponWeakpointSlowTicks = RediosRules.weaponWeakpointSlowTicks();
        Level level = this.level();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            Vec3 c = this.position().add(0.0, (double)this.getBbHeight() * 0.5, 0.0);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.7f);
            serverLevel.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.35, 0.35, 0.35, 0.12);
        }
        if (attacker instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer)attacker;
            serverPlayer.displayClientMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.weakpoint_hit").withStyle(ChatFormatting.RED)), true);
        }
    }

    private static int computeTitleIndex(float maxHealth, float currentHealth, int titleCount) {
        if (titleCount <= 1) {
            return 0;
        }
        float segment = maxHealth / (float)titleCount;
        float missing = maxHealth - Mth.clamp(currentHealth, 0.0f, maxHealth);
        return Mth.clamp((int)((int)(missing / segment)), 0, titleCount - 1);
    }

    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController(this, "main", 5, state -> state.setAndContinue(DefaultAnimations.IDLE)));
        controllers.add(new AnimationController(this, "transition", 0, state -> state.setAndContinue(TRANSITION_ANIM)));
    }

    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public void handlePhase2Choice(ServerPlayer player, boolean continueFight) {
        if (this.bossState != BossState.PHASE1_VOTE) {
            return;
        }
        UUID id = player.getUUID();
        if (!this.phase2Choices.containsKey(id)) {
            return;
        }
        if (this.phase2Choices.get(id) != null) {
            return;
        }
        this.phase2Choices.put(id, continueFight);
        player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.phase2_choice.selected").append(Component.translatable((String)(continueFight ? "message.silent_sun.redios.phase2_choice.option_yes" : "message.silent_sun.redios.phase2_choice.option_no")).withStyle(continueFight ? ChatFormatting.GREEN : ChatFormatting.RED))));
        if (this.phase2Choices.values().stream().allMatch(v -> v != null)) {
            this.finishPhase2Choice();
        }
    }

    void beginPhase2Choice() {
        if (!BossTargeting.playerOnlyMode()) {
            this.startTransition();
            return;
        }
        if (!RediosRules.phase2VoteRequired()) {
            if (this.level() instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)this.level();
                MutableComponent msg = Component.translatable("message.silent_sun.redios.phase2_choice.start").withStyle(ChatFormatting.DARK_RED);
                for (ServerPlayer player : this.bossEvent.getPlayers()) {
                    player.displayClientMessage(this.rediosSigned(msg), true);
                }
            }
            this.startTransition();
            return;
        }
        this.phase2Choices.clear();
        int timeoutTicks = 600;
        for (UUID id : this.battleParticipants) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            this.phase2Choices.put(id, null);
        }
        if (this.phase2Choices.isEmpty()) {
            for (ServerPlayer player : this.bossEvent.getPlayers()) {
                UUID id;
                if (player == null || this.expelledPlayers.contains(id = player.getUUID()) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
                this.phase2Choices.put(id, null);
            }
        }
        if (this.phase2Choices.isEmpty()) {
            this.bossEvent.setVisible(true);
            this.setNoAi(false);
            this.startTransition();
            return;
        }
        this.transitionTo(BossState.PHASE1_VOTE);
        this.phase2ChoiceTimeoutTicks = timeoutTicks;
        this.bossEvent.setVisible(false);
        this.setTarget(null);
        this.getNavigation().stop();
        this.setNoAi(true);
        MutableComponent prompt = Component.translatable("message.silent_sun.redios.phase2_choice.prompt").withStyle(ChatFormatting.LIGHT_PURPLE);
        MutableComponent yes = Component.translatable("message.silent_sun.redios.phase2_choice.button_yes").withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "yes")));
        MutableComponent no = Component.translatable("message.silent_sun.redios.phase2_choice.button_no").withStyle(style -> style.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "no")));
        MutableComponent tokenYes = Component.literal("yes").withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "yes")));
        MutableComponent tokenNo = Component.literal("no").withStyle(style -> style.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "no")));
        MutableComponent hint = Component.translatable("message.silent_sun.redios.phase2_choice.hint", new Object[]{yes, no, tokenYes, tokenNo});
        for (UUID id : this.phase2Choices.keySet()) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null) continue;
            CommonEvents.markPhase2ChoicePending(player, this.getUUID());
            player.sendSystemMessage(this.rediosSigned(prompt));
            player.sendSystemMessage(this.rediosSigned(hint));
        }
    }

    private void finishPhase2Choice() {
        ServerPlayer player;
        if (this.bossState != BossState.PHASE1_VOTE) {
            return;
        }
        int total = this.phase2Choices.size();
        long yesVotes = this.phase2Choices.values().stream().filter(Boolean.TRUE::equals).count();
        for (UUID id : this.phase2Choices.keySet()) {
            player = this.getServerPlayer(id);
            if (player == null) continue;
            CommonEvents.clearPhase2ChoicePending(player);
        }
        this.transitionTo(BossState.PHASE1_COMBAT);
        this.phase2ChoiceTimeoutTicks = 0;
        if (total <= 0) {
            this.bossEvent.setVisible(true);
            this.setNoAi(false);
            this.startTransition();
            return;
        }
        if (yesVotes > (long)(total / 2)) {
            this.bossEvent.setVisible(true);
            this.setNoAi(false);
            for (UUID id : this.phase2Choices.keySet()) {
                player = this.getServerPlayer(id);
                if (player == null) continue;
                player.displayClientMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.phase2_choice.start").withStyle(ChatFormatting.DARK_RED)), true);
            }
            this.startTransition();
            return;
        }
        this.endBattleHalfDayCooldown();
    }

    private BlockPos findNearbyRewardPlacement(ServerLevel serverLevel) {
        BlockPos base = this.blockPosition();
        for (int dy = 0; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    BlockPos below;
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (!serverLevel.getBlockState(pos).isAir() || serverLevel.getBlockState(below = pos.below()).isAir()) continue;
                    return pos;
                }
            }
        }
        return null;
    }

    ServerPlayer getServerPlayer(UUID id) {
        Level level = this.level();
        if (level instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)level;
            return sl.getServer().getPlayerList().getPlayer(id);
        }
        return null;
    }

    /** 把奖励潜影盒直接发给参战玩家（物品栏满则掉落在其脚下）。返回是否成功交给某在线玩家。 */
    private boolean deliverRewardToPlayer(ServerLevel serverLevel, ItemStack box) {
        for (UUID id : this.battleParticipants) {
            ServerPlayer p = this.getServerPlayer(id);
            if (p == null || p.isSpectator() || !p.isAlive()) continue;
            if (!p.getInventory().add(box)) {
                p.drop(box, false);
            }
            return true;
        }
        for (UUID id : this.expelledPlayers) {
            ServerPlayer p = this.getServerPlayer(id);
            if (p == null || p.isSpectator() || !p.isAlive()) continue;
            if (!p.getInventory().add(box)) {
                p.drop(box, false);
            }
            return true;
        }
        return false;
    }

    /** 管理员清理命令：把全维度存活的 Boss 标记为待离场（任意状态；区块静止的 Boss 解冻恢复 tick 后自动退场）。 */
    public static void requestCommandLeaveAll(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getEntities().getAll()) {
                if (e instanceof RediosEntity redios && redios.isAlive() && !redios.isRemoved()) {
                    redios.pendingCommandLeave = true;
                }
            }
        }
    }

    /** M16：旧版本残留 Boss 检测（数据版本低于当前版本）。 */
    public boolean isLegacyData() {
        return this.bossDataVersion < BOSS_DATA_VERSION;
    }

    void registerMobParticipant(LivingEntity entity) {
        if (entity == null || BossTargeting.playerOnlyMode() || entity instanceof Player || entity.isSpectator() || !entity.isAlive() || entity.level() != this.level()) {
            return;
        }
        if (this.mobParticipants.size() >= 16) {
            this.cleanupMobParticipants();
            if (this.mobParticipants.size() >= 16) {
                return;
            }
        }
        if (this.mobParticipants.add(entity.getUUID())) {
            this.mobBattleEngaged = true;
        }
    }

    void cleanupMobParticipants() {
        if (this.mobParticipants.isEmpty()) {
            return;
        }
        // 2026-09-11（代码审计 G09 #1 修复）：清理失效参战生物时，同步回收 WeaponManager 的两本
        // 格挡记账（guardLastHitTick / guardAvgInterval）。原先只有玩家路径会回收
        // （expelFromBattle → removeGuardStats），非玩家攻击者（Mode 1 有主宠物 / Mode 2 白名单生物）
        // 的条目整场无人清理 → 两本 Map 只增不减。
        this.mobParticipants.removeIf(id -> {
            if (this.getMobParticipant((UUID)id) == null) {
                this.weapons.removeGuardStats((UUID)id);
                return true;
            }
            return false;
        });
    }

    LivingEntity getMobParticipant(UUID id) {
        LivingEntity le;
        ServerLevel sl;
        Entity e;
        Level level = this.level();
        if (level instanceof ServerLevel && (e = (sl = (ServerLevel)level).getEntity(id)) instanceof LivingEntity && (le = (LivingEntity)e).isAlive() && le.level() == level && !le.isSpectator()) {
            return le;
        }
        return null;
    }

    private int getActiveMobParticipantCount() {
        int count = 0;
        for (UUID id : this.mobParticipants) {
            if (this.getMobParticipant(id) == null) continue;
            ++count;
        }
        return count;
    }

    void forEachFightingOpponent(Consumer<LivingEntity> action) {
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level()) continue;
            action.accept((LivingEntity)player);
        }
        this.forEachMobOpponent(action);
    }

    void forEachMobOpponent(Consumer<LivingEntity> action) {
        for (UUID id : new HashSet<UUID>(this.mobParticipants)) {
            LivingEntity target = this.getMobParticipant(id);
            if (target == null) continue;
            action.accept(target);
        }
    }

    void markBattleParticipant(LivingEntity entity) {
        Level level;
        boolean added;
        Level level2;
        ServerPlayer sp;
        boolean playerOnly = BossTargeting.playerOnlyMode();
        ServerPlayer serverPlayer = sp = entity instanceof ServerPlayer ? (ServerPlayer)entity : null;
        if (playerOnly && sp == null) {
            return;
        }
        if (!playerOnly) {
            return;
        }
        if (sp != null && (sp.isSpectator() || sp.isCreative())) {
            return;
        }
        UUID id = entity.getUUID();
        if (this.expelledPlayers.contains(id)) {
            return;
        }
        // 2026-09-11（代码审计 G15 #2 第二阶段 / 作者裁定）：极限模式玩家**预标记**硬核保 1 血。
        // <p>
        // 根因：hardcoreProtectedPlayers 原先只在玩家**第一次被打到 0 血之后**才由 notifyHardcoreSpare
        // 写入 → 首次致命一击时 CommonEvents.hasHardcoreProtector 为 false →
        // onLivingDeathHardcoreProtected 不取消死亡 → 玩家走完整 ServerPlayer.die()：
        // 死亡界面包（L693）、死亡消息广播（L709）、dropAllDeathLoot 掉光物品（L725）、
        // 死亡计数/统计递增（L728/L737）—— 之后汇合点的 setHealth(1f) 已无法撤回这些副作用。
        // <p>
        // 现改为「参战登记时即写入标记」：doHurtTarget 开头（L3118）就会对本方法的攻击目标调用本方法，
        // 即**同 tick 内、伤害结算之前**玩家已在集合里，首次致命一击立即被
        // CommonEvents.onLivingDamagePre 钳到 health-1。该判据用的是 LivingDamageEvent 的 newDamage
        // （= 已过护甲/吸收的**实际**伤害），正合设计稿 §3.5 L354「若伤害导致玩家生命值降至 0 以下」
        // 的实际伤害语义；也避免了「钳名义伤害 → 削弱护甲作用」的副作用。
        if (sp != null && sp.level().getLevelData().isHardcore()) {
            this.hardcoreProtectedPlayers.add(id);
        }
        if (this.battleStartGameTime < 0L && (level2 = this.level()) instanceof ServerLevel) {
            ServerLevel serverLevel2 = (ServerLevel)level2;
            this.battleStartGameTime = serverLevel2.getGameTime();
            this.anticheat.deathCheatStrikeCount = 0;
        }
        if ((added = this.battleParticipants.add(id)) && !this.bossOutlineEnabled && this.battleParticipants.size() == 1 && (level = this.level()) instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)level;
            this.enableBossOutline(sl);
        }
        // 2026-09-10（用户裁决 Q10 = A「参战过即计入」）：去掉原先「首次参战起 200 tick（10 秒）内」的时间窗口。
        // 分母 = 本场累计参战人数；成书的 {participants} 占位符语义随之变为累计参战人数（与 2.7 分母同源）。
        if (sp != null && this.battleStartGameTime >= 0L && this.level() instanceof ServerLevel) {
            this.initialParticipants.add(id);
        }
        if (added && this.isTwilightMomentActive() && this.titleLockTicks > 0 && sp != null && this.level() instanceof ServerLevel) {
            boolean expelEnabled = RediosRules.twilightMomentExpelMode();
            RediosRules.TwilightMomentMode mode = RediosRules.twilightMomentMode();
            int duration = mode == RediosRules.TwilightMomentMode.TIMED ? this.titleLockTicks + 5 : 40;
            Holder<MobEffect> applyEffect = this.resolveTwilightMomentApplyEffect();
            if (!sp.isSpectator() && !sp.isCreative() && sp.isAlive()) {
                sp.addEffect(new MobEffectInstance(applyEffect, duration, 0, true, false));
                if (!sp.hasEffect(applyEffect)) {
                    if (expelEnabled) {
                        this.expelForDarknessFailure(sp, true);
                    }
                    return;
                }
            }
            if (expelEnabled && !this.hasTwilightMomentSatisfyEffect(sp)) {
                if (mode == RediosRules.TwilightMomentMode.TIMED) {
                    this.twilightTimedMissingTicks.put(id, Math.max(1, this.twilightTimedMissingTicks.getOrDefault(id, 0)));
                    this.twilightTimedMissingFromApply.add(id);
                } else {
                    this.expelForDarknessFailure(sp, true);
                }
            }
        }
    }

    public void expelFromBattle(ServerPlayer player) {
        UUID id = player.getUUID();
        this.expelledPlayers.add(id);
        this.battleParticipants.remove(id);
        this.weapons.removeGuardStats(id);
        this.twilightTimedMissingTicks.remove(id);
        this.twilightTimedMissingFromApply.remove(id);
        this.hardcoreProtectedPlayers.remove(id);
        if (this.getTarget() != null && this.getTarget().getUUID().equals(id)) {
            this.setTarget(null);
        }
        this.cleanupPlayerAfterBattle(player);
        if (BossTargeting.playerOnlyMode() && this.battleParticipants.isEmpty()) {
            this.allExpelledLeavePending = true;
        }
    }

    /**
     * 极限模式「保 1 血」：把致命伤害钳到 {@code health - 1} 并登记保护标记 + 演出。返回钳后伤害。
     * <p>
     * 2026-09-11（代码审计 G15 #2 修复）：原实现在三处各写一遍（混沌之墟/砺锋·无拘冲刺两条事前钳伤
     * 与汇合点的事后补血），机制与过滤条件已开始发散。现收敛为本方法 + {@link #notifyHardcoreSpare}。
     * <p>
     * 设计依据：设计稿 §3.5 L354「极限模式保护：Boss 攻击玩家时，若伤害导致玩家生命值降至 0 以下，
     * 玩家**不死亡**但播放死亡动画与音效，生命值重置为 1」。事前钳伤使血量从不 ≤0，才真正满足
     * 「玩家不死亡」（不会外发 {@code LivingDeathEvent}）。
     */
    private float clampHardcoreSpare(ServerPlayer sp, float damage) {
        if (damage < sp.getHealth()) {
            return damage;
        }
        this.notifyHardcoreSpare(sp);
        return Math.max(0.0f, sp.getHealth() - 1.0f);
    }

    /** 保 1 血登记 + 假死亡演出（标记 / 死亡动画 / 音效 / 提示）。 */
    private void notifyHardcoreSpare(ServerPlayer sp) {
        this.hardcoreProtectedPlayers.add(sp.getUUID());
        sp.level().broadcastEntityEvent(sp, (byte)3);
        if (sp.level() instanceof ServerLevel sl) {
            sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        Component warn = Component.translatable("message.silent_sun.redios.hardcore_spare").withStyle(ChatFormatting.DARK_RED);
        sp.sendSystemMessage(this.rediosSigned(warn));
    }

    public boolean isHardcoreProtected(UUID id) {
        return this.hardcoreProtectedPlayers.contains(id);
    }

    private void repelExpelledPlayers() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        if (this.expelledPlayers.isEmpty()) {
            return;
        }
        double range = RediosRules.pushAwayRange();
        AABB box = this.getBoundingBox().inflate(range);
        List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class, box, p -> !p.isSpectator() && !p.isCreative());
        double pushDist = RediosRules.pushAwayDistance();
        double pushDistSqr = pushDist * pushDist;
        double strength = RediosRules.pushAwayStrength();
        for (ServerPlayer player : players) {
            double nz;
            double nx;
            double dz;
            double dx;
            double distSqr;
            if (!this.expelledPlayers.contains(player.getUUID()) || (distSqr = (dx = player.getX() - this.getX()) * dx + (dz = player.getZ() - this.getZ()) * dz) >= pushDistSqr) continue;
            double len = Math.sqrt(distSqr);
            if (len < 1.0E-6) {
                dx = 1.0;
                dz = 0.0;
                len = 1.0;
            }
            if (this.isPushDirectionBlocked(serverLevel, player, nx = dx / len, nz = dz / len)) {
                player.push(0.0, 0.6, 0.0);
                continue;
            }
            player.push(nx * strength, 0.15, nz * strength);
        }
    }

    private boolean isPushDirectionBlocked(ServerLevel serverLevel, Entity entity, double nx, double nz) {
        BlockPos feet = entity.blockPosition();
        BlockPos ahead = new BlockPos(feet.getX() + (int)Math.round(nx), feet.getY(), feet.getZ() + (int)Math.round(nz));
        return this.isImpassableBlock(serverLevel, ahead) || this.isImpassableBlock(serverLevel, ahead.above());
    }

    private boolean isImpassableBlock(ServerLevel serverLevel, BlockPos pos) {
        BlockState state = serverLevel.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock((BlockGetter)serverLevel, pos);
    }

    static enum LeaveReason {
        NONE,
        CHUNK_UNLOAD,
        ANOMALY;

    }

    private static final class RediosRiptideDashGoal
    extends Goal {
        private final RediosEntity redios;
        private int cooldownTicks = 0;
        private int dashTicks = 0;

        private RediosRiptideDashGoal(RediosEntity redios) {
            this.redios = redios;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        public boolean canUse() {
            if (this.redios.bossState.isVoteOrTransition() || this.redios.isDarkStarBlastOngoing()) {
                return false;
            }
            if (this.cooldownTicks > 0) {
                --this.cooldownTicks;
                return false;
            }
            LivingEntity target = this.redios.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            double d = this.redios.distanceTo(target);
            double reach = this.redios.getCurrentAttackReach();
            return d >= 4.0 && d <= reach * 4.0;
        }

        public boolean canContinueToUse() {
            return this.dashTicks > 0;
        }

        public void start() {
            this.dashTicks = 10;
            this.cooldownTicks = 50;
            LivingEntity target = this.redios.getTarget();
            if (target != null) {
                double dz;
                double dx;
                double hLen;
                this.redios.getLookControl().setLookAt(target, 30.0f, 30.0f);
                Vec3 dir = target.position().subtract(this.redios.position());
                if (dir.lengthSqr() < 1.0E-6) {
                    dir = new Vec3(1.0, 0.0, 0.0);
                }
                double nx = (hLen = Math.sqrt((dx = dir.x) * dx + (dz = dir.z) * dz)) > 1.0E-6 ? dx / hLen : 0.0;
                double nz = hLen > 1.0E-6 ? dz / hLen : 0.0;
                double yVel = Mth.clamp((double)(dir.y * 0.15), -3.0, 3.0);
                if (yVel >= 0.0 && yVel < 0.25) {
                    yVel = 0.25;
                }
                this.redios.setDeltaMovement(nx * 1.9, yVel, nz * 1.9);
                Level level = this.redios.level();
                if (level instanceof ServerLevel) {
                    ServerLevel serverLevel = (ServerLevel)level;
                    serverLevel.playSound(null, this.redios.blockPosition(), (SoundEvent)SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            }
        }

        public void tick() {
            LivingEntity target = this.redios.getTarget();
            if (target == null || !target.isAlive()) {
                this.dashTicks = 0;
                return;
            }
            --this.dashTicks;
            this.redios.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (this.redios.distanceToSqr(target) <= 9.0) {
                this.redios.swing(InteractionHand.MAIN_HAND);
                this.redios.doHurtTarget(target);
                this.dashTicks = 0;
            }
        }
    }

    private static final class RediosMeleeAttackGoal
    extends Goal {
        /** §7.2 近身判定距离：设计稿 L611「进入近身（< 4 格）后绕目标圆周移动」。 */
        private static final double ORBIT_ENTER_DIST = 4.0;
        private static final double ORBIT_ENTER_SQR = ORBIT_ENTER_DIST * ORBIT_ENTER_DIST;
        /** 绕圈半径（格）：落在近身圈内，且 < 攻击距离，故绕圈期间攻击判定照常成立。 */
        private static final double ORBIT_RADIUS = 3.0;
        /** 每次路径重算推进的切向角度（弧度），0.6 ≈ 34°。 */
        private static final double ORBIT_STEP_RAD = 0.6;
        /** 绕圈速度 = 追击速度 × 该系数。 */
        private static final double ORBIT_SPEED_SCALE = 0.85;
        /** 换向周期（tick，3~6s 随机）：避免长圈单向的机械感。 */
        private static final int ORBIT_FLIP_MIN_TICKS = 60;
        private static final int ORBIT_FLIP_MAX_TICKS = 120;

        private final RediosEntity redios;
        private final double speed;
        private int attackCooldownTicks = 0;
        private int pathRecalcTicks = 0;
        private boolean orbiting = false;
        private int orbitDir = 1;
        private int orbitFlipTicks = 0;

        private RediosMeleeAttackGoal(RediosEntity redios) {
            this.redios = redios;
            this.speed = 1.2;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canUse() {
            LivingEntity target = this.redios.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (this.redios.bossState.isVoteOrTransition() || this.redios.isDarkStarBlastOngoing()) {
                return false;
            }
            if (this.redios.isBladeModeActive()) {
                return false;
            }
            return !this.redios.isWallAttackEnabled() || this.redios.getSensing().hasLineOfSight(target);
        }

        public boolean canContinueToUse() {
            return this.canUse();
        }

        public void start() {
            this.pathRecalcTicks = 0;
        }

        public void stop() {
            this.orbiting = false;
            this.redios.getNavigation().stop();
        }

        public void tick() {
            double reachSqr;
            double reach;
            LivingEntity target = this.redios.getTarget();
            if (target == null) {
                return;
            }
            this.redios.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (this.pathRecalcTicks-- <= 0) {
                this.pathRecalcTicks = 6;
                double moveSpeed = this.redios.heightFlightMode ? this.speed * 1.35 : this.speed;
                if (this.redios.distanceToSqr(target) > ORBIT_ENTER_SQR) {
                    // 非近身：直线追击当前目标（§7.2 L611 前半）。
                    this.orbiting = false;
                    this.redios.getNavigation().moveTo(target, moveSpeed);
                } else {
                    // 近身（< 4 格）：绕目标圆周移动（§7.2 L611 后半）。
                    // 切向角取「当前实际方位角 + 一步」，Boss 被击退或导航滞后时不会与圆周脱节；
                    // 圆心取目标当前位置，目标移动时圈跟着走。
                    if (!this.orbiting) {
                        this.orbiting = true;
                        this.orbitDir = this.redios.getRandom().nextBoolean() ? 1 : -1;
                        this.orbitFlipTicks = this.rollOrbitFlipTicks();
                    }
                    if (--this.orbitFlipTicks <= 0) {
                        this.orbitDir = -this.orbitDir;
                        this.orbitFlipTicks = this.rollOrbitFlipTicks();
                    }
                    double angle = Math.atan2(this.redios.getZ() - target.getZ(), this.redios.getX() - target.getX());
                    if (!Double.isFinite(angle)) {
                        angle = this.redios.getRandom().nextDouble() * Math.PI * 2.0;
                    }
                    double next = angle + (double)this.orbitDir * ORBIT_STEP_RAD;
                    this.redios.getNavigation().moveTo(target.getX() + Math.cos(next) * ORBIT_RADIUS, target.getY(), target.getZ() + Math.sin(next) * ORBIT_RADIUS, moveSpeed * ORBIT_SPEED_SCALE);
                }
            }
            if (this.attackCooldownTicks > 0) {
                --this.attackCooldownTicks;
            }
            if (this.redios.isWallAttackEnabled() && !this.redios.getSensing().hasLineOfSight(target)) {
                return;
            }
            reach = this.redios.getCurrentAttackReach();
            reachSqr = reach * reach;
            if (this.redios.distanceToSqr(target) <= reachSqr && this.attackCooldownTicks <= 0) {
                this.attackCooldownTicks = this.redios.getAttackCooldownTicks();
                this.redios.swing(InteractionHand.MAIN_HAND);
                this.redios.doHurtTarget(target);
            }
        }

        private int rollOrbitFlipTicks() {
            return ORBIT_FLIP_MIN_TICKS + this.redios.getRandom().nextInt(ORBIT_FLIP_MAX_TICKS - ORBIT_FLIP_MIN_TICKS + 1);
        }
    }

    private static final class RediosWallAttackGoal
    extends Goal {
        private final RediosEntity redios;
        private int attackCooldownTicks = 0;

        private RediosWallAttackGoal(RediosEntity redios) {
            this.redios = redios;
            this.setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        public boolean canUse() {
            LivingEntity target = this.redios.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            if (!this.redios.isWallAttackEnabled()) {
                return false;
            }
            if (this.redios.isDarkStarBlastOngoing()) {
                return false;
            }
            if (this.redios.bossState.isVoteOrTransition()) {
                return false;
            }
            if (this.redios.getSensing().hasLineOfSight(target)) {
                return false;
            }
            double reach = this.redios.getCurrentAttackReach();
            if (this.redios.distanceToSqr(target) > reach * reach) {
                return false;
            }
            // 厚墙（厚度≥阈值）交给投掷突破，穿墙近战只处理薄墙
            return this.redios.countWallThickness(target) < WALL_THICKNESS_BREAK_THRESHOLD;
        }

        public boolean canContinueToUse() {
            return this.canUse();
        }

        public void tick() {
            LivingEntity target = this.redios.getTarget();
            if (target == null) {
                return;
            }
            this.redios.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (this.attackCooldownTicks > 0) {
                --this.attackCooldownTicks;
                return;
            }
            this.attackCooldownTicks = this.redios.getAttackCooldownTicks();
            this.redios.swing(InteractionHand.MAIN_HAND);
            this.redios.doHurtTarget(target);
            if (target instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer)target;
                this.redios.notifyWallAttack(player);
            }
        }
    }

    private static enum RediosBookOutcome {
        PHASE1_WIN_ONLY,
        PHASE1_WIN_PHASE2_LOSE,
        PHASE2_WIN;

    }
}
