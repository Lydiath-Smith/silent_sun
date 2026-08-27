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
    private static final double EXPEL_REPEL_RADIUS = 24.0;
    private static final double EXPEL_REPEL_RADIUS_SQR = 576.0;
    private static final int INITIAL_PARTICIPANT_CAPTURE_TICKS = 200;
    private static final int DARK_STAR_RADIUS = 13;
    private static final String OUTCOME_BOOK_TITLE = "\u7559\u8a00\u4e00\u5219";
    private static final String OUTCOME_BOOK_LEGACY_TITLE = "\u6210\u4e66";
    private static final String DROP_LIST_BOOK_TITLE = "\u5217\u8868";
    private static final String DROP_STATS_BOOK_TITLE = "\u7edf\u8ba1\u7269\u54c1";
    private static final int VOID_BATTLE_RANGE_BLOCKS = 512;
    private static final int VOID_BATTLE_RANGE_BLOCKS_SQR = 262144;
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
    private static final int STAGE_DIG_RAY_STEPS = 6;
    private static final int STAGE_DIG_INTERVAL_TICKS = 2;
    private static final int STAGE_BLOCK_BOMB_RANGE = 24;
    private static final int STAGE_BLOCK_BOMB_COOLDOWN_TICKS = 35;
    private static final float STAGE_BLOCK_BOMB_EXPLOSION_POWER = 2.5f;
    private static final List<Holder<MobEffect>> DUSTLESS_GOOD_BUFF_POOL = List.of(MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED, MobEffects.DIG_SPEED, MobEffects.JUMP, MobEffects.REGENERATION, MobEffects.ABSORPTION, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING, MobEffects.NIGHT_VISION, MobEffects.HEALTH_BOOST);
    static final List<Component> PHASE1_TITLES = List.of(Component.translatable("title.silent_sun.redios.phase1.0"), Component.translatable("title.silent_sun.redios.phase1.1"), Component.translatable("title.silent_sun.redios.phase1.2"), Component.translatable("title.silent_sun.redios.phase1.3"), Component.translatable("title.silent_sun.redios.phase1.4"), Component.translatable("title.silent_sun.redios.phase1.5"), Component.translatable("title.silent_sun.redios.phase1.6"), Component.translatable("title.silent_sun.redios.phase1.7"), Component.translatable("title.silent_sun.redios.phase1.8"), Component.translatable("title.silent_sun.redios.phase1.9"));
    private static final List<Component> PHASE2_TITLES = List.of(Component.translatable("title.silent_sun.redios.phase2.0"), Component.translatable("title.silent_sun.redios.phase2.1"), Component.translatable("title.silent_sun.redios.phase2.2"), Component.translatable("title.silent_sun.redios.phase2.3"), Component.translatable("title.silent_sun.redios.phase2.4"), Component.translatable("title.silent_sun.redios.phase2.5"), Component.translatable("title.silent_sun.redios.phase2.6"), Component.translatable("title.silent_sun.redios.phase2.7"), Component.translatable("title.silent_sun.redios.phase2.8"), Component.translatable("title.silent_sun.redios.phase2.9"));
    static final TitleDef[] PHASE1_TITLE_DEFS = new TitleDef[]{TitleDef.p1(0, 15, new BossFlag[0]), TitleDef.p1(1, 15, new BossFlag[0]), TitleDef.p1(2, 15, BossFlag.WEAKNESS_CURSE, BossFlag.ENRAGE_STACKING), TitleDef.p1(3, 15, new BossFlag[0]), TitleDef.p1(4, 15, new BossFlag[0]), TitleDef.p1(5, 15, BossFlag.SOUL_SEVER_HARVEST), TitleDef.p1(6, 15, new BossFlag[0]), TitleDef.p1(7, 15, new BossFlag[0]), TitleDef.p1(8, 15, new BossFlag[0]), TitleDef.p1(9, 15, BossFlag.GUARD_BLOCK)};
    static final TitleDef[] PHASE2_TITLE_DEFS = new TitleDef[]{TitleDef.p2(0, 30, BossFlag.SEA_SKY_SOUL_SEVER), TitleDef.p2(1, 30, BossFlag.UNCONTROLLED_SPRINT), TitleDef.p2(2, 30, new BossFlag[0]), TitleDef.p2(3, 30, new BossFlag[0]), TitleDef.p2(4, 30, BossFlag.ASH_DAWN), TitleDef.p2(5, 30, new BossFlag[0]), TitleDef.p2(6, 30, new BossFlag[0]), TitleDef.p2(7, 30, BossFlag.BLACK_SUN), TitleDef.p2(8, 30, BossFlag.COLORLESS, BossFlag.ENRAGE_STACKING), TitleDef.p2(9, 30, new BossFlag[0])};
    private static final EntityDataAccessor<Integer> CLIENT_PHASE = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_TITLE_INDEX = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_TRANSITION_TICKS = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_BOSS_STATE = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> CLIENT_SOUL_SEVER_Y = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> CLIENT_TWILIGHT_ACTIVE = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_TITLE_LOCK_TICKS = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CLIENT_INTRO_ACTIVE = SynchedEntityData.defineId(RediosEntity.class, EntityDataSerializers.INT);
    private static final int INTRO_TOTAL_TICKS = 80;
    private static final int INTRO_STAR_COUNT = 6;
    private static final RawAnimation TRANSITION_ANIM = RawAnimation.begin().thenPlay("transition");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    int phase = 1;
    int titleIndex = 0;
    private int titleLockTicks = 0;
    /** P2 濒死锁血已到期解除：到期后回到 PHASE2_COMBAT 等待玩家补刀，不再锁血。 */
    boolean pendingLockReleased = false;
    private int titleLockGraceTicks = 0;
    /** 投票/转阶段/濒死期间所有活跃参战玩家远离 Boss 的连续 tick 数（64 格外），用于区分「主动逃离脱战」与「区块短暂卸载」。 */
    private int disengageTicks = 0;
    private int aiWatchdogNoTargetTicks = 0;
    int transitionTicks = 0;
    private int transitionTotalTicks = 0;
    private long soulSeverY = 0L;
    boolean wrongInterferenceActive = false;
    boolean chaosRuinActive = false;
    private boolean chaosRuinAbsoluteAttacks = false;
    private boolean ashDawnActive = false;
    private boolean ashDawnUnlocked = false;
    private boolean darkStarFired = false;
    private boolean darkStarFlightUnlocked = false;
    private boolean darkStarBedrockRepaired = false;
    private final Map<BlockPos, CompoundTag> darkStarRestoreBlocks = new HashMap<BlockPos, CompoundTag>();
    private BlockPos darkStarBlastOrigin = null;
    private int darkStarBlastNextIndex = 0;
    private static final int DARK_STAR_BLAST_PER_TICK = 600;
    private boolean blackSunTriggered = false;
    private boolean colorlessUnlocked = false;
    private int colorlessChallengeTicks = -1;
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
    private static final int MISSING_VIEW_NOTIFY_COOLDOWN_TICKS = 600;
    private final Map<UUID, Integer> lastMissingViewNotifyTick = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> outOfAreaTicks = new HashMap<UUID, Integer>();
    private boolean heightFlightMode = false;
    boolean phaseMaxHealthApplied = false;
    private int battleMusicPhase = 0;
    private final Map<UUID, Integer> battleMusicPlaying = new HashMap<UUID, Integer>();
    private static final double MUSIC_FADE_OUT_DIST_SQR = 16384.0;
    private static final int STARFALL_SALVO_SETTLE_TIMEOUT_TICKS = 160;
    private static final int STARFALL_SALVO_FALL_FROM_BLOCKS = 30;
    private static final int STARFALL_SALVO_HOVER_BLOCKS = 2;
    private int starfallSalvoCooldownTicks = 900;
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
    private boolean settlementDone = false;
    final Set<UUID> hardcoreProtectedPlayers = new HashSet<UUID>();
    final Set<UUID> twilightExpelled = new HashSet<UUID>();
    private long battleStartGameTime = -1L;
    private final Set<UUID> initialParticipants = new HashSet<UUID>();
    final Set<UUID> mobParticipants = new HashSet<UUID>();
    private boolean mobBattleEngaged = false;
    private static final int TITLE_WHOSE_WISH = 7;
    private static final int TITLE_SHARPEN_TRIAL = 8;
    private static final int TITLE_DIVIDE_LIGHT = 5;
    private static final int TITLE_BLACK_SUN = 7;
    private boolean legitRemoval = false;
    private boolean inHurtProcessing = false;
    /** 入场演出剩余 tick（0 表示无演出）：演出期 Boss 冻结、无敌、不索敌。 */
    private int introTicks = 0;
    /** 重建自战斗账本记录的标记：仅作语义区分，不影响结算 CD（照常设 CD）。 */
    private boolean rebuiltAsSettled = false;
    private LeaveReason leaveReason = LeaveReason.NONE;
    private BlockPos battleAnchorPos = null;
    private ResourceLocation battleAnchorDim = null;
    private static final double ANTI_EXILE_RANGE = 256.0;
    private static final double ANTI_EXILE_VOID_MARGIN = 8.0;
    private int deathViaHurtTick = -1;
    private int removalPunishCooldownTicks = 0;
    private static final SoundEvent[] DARKNESS_AMBIENT_SOUNDS = new SoundEvent[]{SoundEvents.WARDEN_HEARTBEAT, SoundEvents.WARDEN_LISTENING, SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_ANGRY};
    private int darknessSoundCooldown = 0;
    private static final ResourceLocation MAX_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_max_health_override");
    private static final double ATTRIBUTE_MAX_HEALTH_CAP = 1024.0;
    private static boolean maxHealthUncapped = false;
    private static boolean warnedMaxHealthClamped = false;

    public RediosEntity(EntityType<? extends RediosEntity> type, Level level) {
        super(type, level);
        this.bossEvent.setVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 2000.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 30.0).add(Attributes.ATTACK_SPEED, 4.0).add(Attributes.ARMOR, 20.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CLIENT_PHASE, 1);
        builder.define(CLIENT_TITLE_INDEX, 0);
        builder.define(CLIENT_TRANSITION_TICKS, 0);
        builder.define(CLIENT_BOSS_STATE, BossState.PHASE1_COMBAT.ordinal());
        builder.define(CLIENT_SOUL_SEVER_Y, 0L);
        builder.define(CLIENT_TWILIGHT_ACTIVE, 0);
        builder.define(CLIENT_TITLE_LOCK_TICKS, 0);
        builder.define(CLIENT_INTRO_ACTIVE, 0);
    }

    public int getClientPhase() {
        return this.entityData.get(CLIENT_PHASE);
    }

    public int getClientTitleIndex() {
        return this.entityData.get(CLIENT_TITLE_INDEX);
    }

    public int getClientTransitionTicks() {
        return this.entityData.get(CLIENT_TRANSITION_TICKS);
    }

    public BossState getClientBossState() {
        int ord = this.entityData.get(CLIENT_BOSS_STATE);
        BossState[] values = BossState.values();
        return ord >= 0 && ord < values.length ? values[ord] : BossState.PHASE1_COMBAT;
    }

    public long getClientSoulSeverY() {
        return (Long)this.entityData.get(CLIENT_SOUL_SEVER_Y);
    }

    public boolean isClientTwilightActive() {
        return this.entityData.get(CLIENT_TWILIGHT_ACTIVE) != 0;
    }

    public boolean isClientIntroActive() {
        return this.entityData.get(CLIENT_INTRO_ACTIVE) != 0;
    }

    public int getClientTitleLockTicks() {
        return this.entityData.get(CLIENT_TITLE_LOCK_TICKS);
    }

    private void syncClientRenderData() {
        if (this.level().isClientSide) {
            return;
        }
        this.entityData.set(CLIENT_PHASE, this.phase);
        this.entityData.set(CLIENT_TITLE_INDEX, this.titleIndex);
        this.entityData.set(CLIENT_TRANSITION_TICKS, this.transitionTicks);
        this.entityData.set(CLIENT_BOSS_STATE, this.bossState.ordinal());
        this.entityData.set(CLIENT_SOUL_SEVER_Y, this.soulSeverY);
        this.entityData.set(CLIENT_TWILIGHT_ACTIVE, this.isTwilightMomentActive() ? 1 : 0);
        this.entityData.set(CLIENT_TITLE_LOCK_TICKS, this.titleLockTicks);
        this.entityData.set(CLIENT_INTRO_ACTIVE, this.introTicks > 0 ? 1 : 0);
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

    private boolean isTargetingAllowed() {
        return !this.bossState.isVoteOrTransition() && this.titleLockTicks <= 0;
    }

    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel)this.level();
        if (this.tickFailsafe(serverLevel)) {
            return;
        }
        if (this.allExpelledLeavePending) {
            this.allExpelledLeavePending = false;
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
            this.bossLeaveFriendly(serverLevel, leaveMsg);
            return;
        }
        if (this.colorlessChallengeTicks > 0) {
            --this.colorlessChallengeTicks;
            if (this.colorlessChallengeTicks <= 0) {
                this.resolveColorlessChallengeSuccess(serverLevel);
                return;
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
        if (this.isBladeAttackAllowed()) {
            IntegrationContract.sanitizeBossBladeEntities(this);
            if (this.isBladeModeActive()) {
                IntegrationContract.tryTickBossBladePlayerHits(this);
                IntegrationContract.tryFireBossPhantomSwords(this);
            }
        }
        this.hardcoreProtectedPlayers.removeIf(id -> {
            ServerPlayer p = this.getServerPlayer((UUID)id);
            return p == null || !p.isAlive() || p.level() != this.level();
        });
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
            if (this.checkAllParticipantsDisengaged(serverLevel)) {
                return;
            }
            if (this.checkDefeatByAllDead(serverLevel)) {
                return;
            }
            if (this.checkBattleAreaUnloaded(serverLevel)) {
                return;
            }
            this.getNavigation().stop();
            this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
            this.setPose(Pose.SITTING);
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
        if (this.bossState == BossState.PHASE1_TRANSITION) {
            --this.transitionTicks;
            if (this.transitionTicks == 0) {
                this.enterPhase2();
            } else if (this.transitionTicks == Math.max(1, this.transitionTotalTicks) - 6) {
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
            star.initSalvo(this.getUUID(), hoverY, delay);
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
        float power = (float) ((Double) SilentSunConfig.STARFALL_SALVO_EXPLOSION_POWER.get()).doubleValue();
        for (UUID uuid : this.introStarfallStars) {
            Entity star = serverLevel.getEntity(uuid);
            if (star == null || star.isRemoved()) continue;
            serverLevel.explode(this, star.getX(), star.getY(), star.getZ(), power, Level.ExplosionInteraction.NONE);
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
        double concentratedRadius = 5.0;
        double dispersedRadius = 20.0;
        int minCount = SilentSunConfig.STARFALL_SALVO_MIN_COUNT.get();
        int maxCount = SilentSunConfig.STARFALL_SALVO_MAX_COUNT.get();
        int maxDelay = SilentSunConfig.STARFALL_SALVO_MAX_DELAY_TICKS.get();
        int count = minCount >= maxCount ? minCount : minCount + this.random.nextInt(maxCount - minCount + 1);
        this.starfallSalvoStars.clear();
        this.starfallSalvoPending = true;
        this.starfallSalvoSettleTimeoutTicks = 160 + maxDelay;
        this.starfallSalvoReadyToDetonate = false;
        this.starfallSalvoDetonateDelayTicks = 0;
        this.starfallSalvoCooldownTicks = SilentSunConfig.STARFALL_SALVO_INTERVAL_TICKS.get();
        List<LivingEntity> targets = this.collectStarfallTargets(serverLevel);
        if (targets.isEmpty()) {
            this.spawnStarfallStars(serverLevel, this.getX(), this.getY(), this.getZ(), count, concentratedRadius, maxDelay);
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
            this.spawnStarfallStars(serverLevel, primary.getX(), primary.getY(), primary.getZ(), count, r, maxDelay);
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
                this.spawnStarfallStars(serverLevel, t.getX(), t.getY(), t.getZ(), share, r, maxDelay);
                assigned += share;
            }
        }
        if (assigned < count) {
            LivingEntity primary = targets.get(0);
            double r = this.isConcentratedStarfallTarget(primary, highestHatred) ? concentratedRadius : dispersedRadius;
            this.spawnStarfallStars(serverLevel, primary.getX(), primary.getY(), primary.getZ(), count - assigned, r, maxDelay);
        }
    }

    private boolean isConcentratedStarfallTarget(LivingEntity t, LivingEntity highestHatred) {
        return this.isActiveAreaBombardment(t) || highestHatred != null && t == highestHatred;
    }

    private List<LivingEntity> collectStarfallTargets(ServerLevel serverLevel) {
        double reach = this.getCurrentAttackReach();
        List<LivingEntity> inReach = serverLevel.getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(reach), e -> BossTargeting.isValidAttackTarget(this, e));
        if (!inReach.isEmpty()) {
            return inReach;
        }
        double viewDist = reach * 4.0;
        return serverLevel.getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(viewDist), e -> BossTargeting.isValidAttackTarget(this, e));
    }

    private void spawnStarfallStars(ServerLevel serverLevel, double centerX, double centerY, double centerZ, int count, double radius, int maxDelay) {
        for (int i = 0; i < count; ++i) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * radius;
            double x = centerX + Math.cos(angle) * dist;
            double z = centerZ + Math.sin(angle) * dist;
            double hoverY = centerY + 2.0;
            double spawnY = centerY + 30.0;
            int delay = maxDelay > 0 ? this.random.nextInt(maxDelay + 1) : 0;
            StarfallSalvoEntity star = ModEntities.STARFALL_SALVO.get().create(serverLevel);
            if (star == null) continue;
            star.initSalvo(this.getUUID(), hoverY, delay);
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
        for (UUID uuid : this.starfallSalvoStars) {
            Entity star = serverLevel.getEntity(uuid);
            if (star == null || star.isRemoved()) continue;
            double sx = star.getX();
            double sy = star.getY();
            double sz = star.getZ();
            serverLevel.explode(this, sx, sy, sz, power, Level.ExplosionInteraction.MOB);
            AABB blast = new AABB(sx - (double)power, sy - (double)power, sz - (double)power, sx + (double)power, sy + (double)power, sz + (double)power);
            hitVictims.addAll(serverLevel.getEntitiesOfClass(LivingEntity.class, blast, e -> e != this && BossTargeting.isValidAttackTarget(this, e)));
            if (star instanceof StarfallSalvoEntity salvo) {
                salvo.markLegitRemoval();
            }
            star.discard();
        }
        for (LivingEntity victim : hitVictims) {
            this.applySoulSeverToTarget(victim);
            this.settleSoulSeverPostDamage(victim);
        }
        this.starfallSalvoStars.clear();
    }

    private void updateBossEvent() {
        if (this.bossState == BossState.PHASE1_VOTE) {
            return;
        }
        if (this.bossState == BossState.PHASE1_TRANSITION) {
            int total = Math.max(1, this.transitionTotalTicks);
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
        this.setBossBarMax(this.getMaxHealth());
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

    private static void ensureMaxHealthUncapped() {
        if (maxHealthUncapped) {
            return;
        }
        maxHealthUncapped = true;
    }

    private void applyPhaseMaxHealth(ServerLevel serverLevel) {
        AttributeInstance kbInst;
        AttributeInstance attackInst;
        RediosEntity.ensureMaxHealthUncapped();
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
        this.removeAllEffects();
        if (enrage != null) {
            super.addEffect(enrage, this);
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
        this.setTarget((LivingEntity)player);
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

    private void tickBattleMusic(ServerLevel serverLevel) {
        int desiredPhase;
        if (!RediosRules.rediosBattleMusicEnabled()) {
            this.stopAllBattleMusic();
            this.battleMusicPhase = 0;
            return;
        }
        if (this.battleParticipants.isEmpty()) {
            this.stopAllBattleMusic();
            this.battleMusicPhase = 0;
            return;
        }
        int n = desiredPhase = this.phase == 2 ? 2 : 1;
        if (desiredPhase != this.battleMusicPhase) {
            this.stopAllBattleMusic();
            this.battleMusicPhase = desiredPhase;
        }
        DeferredHolder<SoundEvent, SoundEvent> music = desiredPhase == 2 ? ModSounds.REDIOS_BATTLE_MUSIC_PHASE2 : ModSounds.REDIOS_BATTLE_MUSIC_PHASE1;
        float volume = RediosRules.rediosBattleMusicVolume();
        for (UUID id : new HashSet<UUID>(this.battleParticipants)) {
            ServerPlayer player = this.getServerPlayer(id);
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || !player.isAlive() || player.level() != this.level()) {
                if (player != null) {
                    this.stopBattleMusicFor(player);
                }
                this.battleMusicPlaying.remove(id);
                continue;
            }
            if (this.distanceToSqr(player) > 16384.0) {
                this.stopBattleMusicFor(player);
                this.battleMusicPlaying.remove(id);
                continue;
            }
            Integer playingPhase = this.battleMusicPlaying.get(id);
            if (playingPhase != null && playingPhase == desiredPhase) continue;
            this.stopBattleMusicFor(player);
            player.connection.send(new ClientboundSoundPacket(music, SoundSource.MUSIC, this.getX(), this.getY(), this.getZ(), volume, 1.0f, this.random.nextLong()));
            this.battleMusicPlaying.put(id, desiredPhase);
        }
        this.battleMusicPlaying.keySet().removeIf(uid -> !this.battleParticipants.contains(uid));
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
            int diff = Math.max(0, Math.max(RediosRules.heightFlightDiffBlocks(), SilentSunConfig.HEIGHT_FLIGHT_DIFF_BLOCKS.get()));
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
        if (this.bossState.isVoteOrTransition() || this.isDarkStarActive()) {
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
        LivingEntity nearest = this.pickNearestActiveParticipant(serverLevel);
        if (nearest != null) {
            this.setTarget(nearest);
        }
    }

    private boolean isSeaSkyGapActive() {
        return this.phase == 2 && this.titleIndex == 0;
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
        if (this.darkStarFired) {
            this.grantFlag(BossFlag.DARK_STAR_FIRED);
        }
        if (this.darkStarFlightUnlocked) {
            this.grantFlag(BossFlag.DARK_STAR_FLIGHT);
        }
        if (this.darkStarBedrockRepaired) {
            this.grantFlag(BossFlag.BEDROCK_REPAIRED);
        }
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
        if (this.phase == 1 && this.titleIndex == 6 && this.titleLockTicks > 0) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean hurt(DamageSource source, float amount) {
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
        int last = this.damageSourceDebugLastTick.getOrDefault(key, Integer.MIN_VALUE);
        if (now - last < cooldown) {
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
        int last = this.damageZeroLogLastTick.getOrDefault(key, Integer.MIN_VALUE);
        if (now - last < cooldown) {
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

    private void setBossBarMax(float maxHealth) {
        try {
            Field maxField = BossEvent.class.getDeclaredField("maxProgress");
            maxField.setAccessible(true);
            maxField.set(this.bossEvent, Float.valueOf(maxHealth));
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
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

    boolean isTrueDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        String msgId = source.getMsgId();
        return "magic".equals(msgId) || "indirectMagic".equals(msgId) || "sonic_boom".equals(msgId) || "wither".equals(msgId) || "dragonBreath".equals(msgId);
    }

    float applyDamageCap(float amount) {
        float threshold = (SilentSunConfig.DYNAMIC_REDUCTION_THRESHOLD.get()).floatValue();
        float ratio = (SilentSunConfig.DYNAMIC_REDUCTION_RATIO.get()).floatValue();
        float hardCap = (SilentSunConfig.DAMAGE_HARD_CAP.get()).floatValue();
        if (amount > threshold) {
            float excess = amount - threshold;
            amount = threshold + excess * ratio;
        }
        return Math.min(amount, hardCap);
    }

    boolean isHealImmunityDamage(DamageSource source) {
        if (source == null) {
            return false;
        }
        int flags = 0;
        if (this.isVoidDamage(source)) {
            ++flags;
        }
        if (this.isTrueDamage(source)) {
            ++flags;
        }
        if (this.isDrowningDamage(source)) {
            ++flags;
        }
        if (this.isSuffocationDamage(source)) {
            ++flags;
        }
        return flags >= 1;
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
            // P2 濒死锁血到期：解除锁血/无敌，回到 P2 战斗等待玩家补刀自然击杀。
            // 不自杀、不在此设 CD——CD 统一在玩家真正击杀时由 die() 设置。
            this.pendingLockReleased = true;
            this.transitionTo(BossState.PHASE2_COMBAT);
            this.setPose(Pose.STANDING);
            this.setHealth(1.0f);
            this.anticheat.markLegalHealthChange(1.0f);
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

    void bossLeaveFriendly(ServerLevel serverLevel, Component farewellMsg) {
        this.leaveBattle(serverLevel, farewellMsg, true);
    }

    private void resolveColorlessChallengeSuccess(ServerLevel serverLevel) {
        this.setTarget(null);
        this.setNoAi(true);
        this.grantAdvancementToParticipants(serverLevel, "phase2_countdown");
        this.bossLeaveFriendly(serverLevel, Component.translatable("message.silent_sun.redios.challenge_success").withStyle(ChatFormatting.GOLD));
    }

    private void leaveBattle(ServerLevel serverLevel, Component farewellMsg, boolean setCooldown) {
        if (this.settlementDone) {
            return;
        }
        this.settlementDone = true;
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
        this.clearBattleRecord(serverLevel);
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

    private boolean isLegitDeathFlow() {
        // P2 濒死锁血是「防击杀窗口」：仅当锁血到期（pendingLockReleased=true，onPendingLockExpired
        // 已解除锁血）后才算合法死亡流，允许玩家补刀自然击杀；锁血未到期时直调 setHealth(≤0)/
        // kill() 等绕过伤害管线的击杀一律视为篡改拦截（与 P1 PENDING 的 inHurtProcessing 判定对称）。
        if (this.bossState == BossState.PHASE2_PENDING) {
            return this.pendingLockReleased;
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
        if (health <= 0.0f && !this.isLegitDeathFlow()) {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
                this.onTamperAttempt(serverLevel, false);
            }
            return;
        }
        super.setHealth(health);
    }

    public boolean isDeadOrDying() {
        if (super.isDeadOrDying() && !this.legitRemoval && !this.isLegitDeathFlow()) {
            return false;
        }
        return super.isDeadOrDying();
    }

    public void remove(Entity.RemovalReason reason) {
        if (!(reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED || this.legitRemoval || this.level().isClientSide)) {
            Level level = this.level();
            if (level instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel)level;
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
        return super.finalizeSpawn(level, difficulty, spawnType, spawnData);
    }

    private void safeDiscard() {
        this.legitRemoval = true;
        if (this.leaveReason != LeaveReason.NONE) {
            SilentSunMod.LOGGER.warn("Redios leaving (leaveReason={}) at {}", this.leaveReason, this.blockPosition());
        }
        this.discard();
    }

    /** 召唤前暴力清除残留 Boss 的静默剔除：绕过反作弊拦截，不结算、不设 CD、不广播。 */
    public void forceDiscardSilently() {
        if (this.isRemoved()) {
            return;
        }
        this.legitRemoval = true;
        this.discard();
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
        this.restoreDarkStarSpecialBlocks(serverLevel);
        boolean clearedPhase1 = this.hasClearedPhase1ForLoot();
        if (!clearedPhase1) {
            super.die(damageSource);
            this.disableBossOutline(serverLevel);
            this.cleanupNearbyLivingAfterBattle(serverLevel);
            this.cleanupPlayersAfterBattle(serverLevel);
            this.clearBattleRecord(serverLevel);
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
            this.clearBattleRecord(serverLevel);
            this.discard();
            return;
        }
        super.die(damageSource);
        includeDefeatBook = this.phase == 2;
        if (includeDefeatBook && this.isFinalKillerPlayer(damageSource)) {
            this.applySummonCooldown(serverLevel, (long)(SilentSunConfig.COOLDOWN_DAYS.get()).intValue() * 24000L);
        }
        this.dropPhase1Reward(serverLevel, includeDefeatBook);
        this.disableBossOutline(serverLevel);
        this.cleanupNearbyLivingAfterBattle(serverLevel);
        this.cleanupPlayersAfterBattle(serverLevel);
        this.clearBattleRecord(serverLevel);
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
        Holder<DamageType> dmgHolder = null;
        boolean result = false;
        Player p = null;
        Player p2 = null;
        ServerPlayer sp = null;
        ServerPlayer sp2 = null;
        Component warn = null;
        Player playerTarget = null;
        float totalDamage = 0.0f;
        float pierceRatio = 0.0f;
        float bypass = 0.0f;
        float normal = 0.0f;
        boolean hit = false;
        boolean v0 = false;
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
                        if (this.wrongInterferenceActive && this.random.nextFloat() > 0.5f) {
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
                        if (!this.chaosRuinActive && !this.chaosRuinAbsoluteAttacks && !this.isSharpenTrialActiveNow()) break block27;
                        damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        src = ModDamageTypes.rediosAttack(this.level(), this);
                        dmgHolder = this.resolveAttackDamageHolder();
                        if (dmgHolder != null) {
                            src = new DamageSource(dmgHolder);
                        }
                        if (livingTarget instanceof ServerPlayer && (sp = (ServerPlayer)livingTarget).level().getLevelData().isHardcore() && damage >= sp.getHealth()) {
                            damage = Math.max(0.0f, sp.getHealth() - 1.0f);
                            this.hardcoreProtectedPlayers.add(sp.getUUID());
                            sp.level().broadcastEntityEvent(sp, (byte)3);
                            sl = (ServerLevel)sp.level();
                            sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
                            warn = Component.translatable("message.silent_sun.redios.hardcore_spare").withStyle(ChatFormatting.DARK_RED);
                            sp.sendSystemMessage(this.rediosSigned(warn));
                        }
                        AbsoluteDamageUtil.damage(livingTarget, src, damage, SilentSunConfig.BOSS_DAMAGE_CREATIVE.get());
                        this.settleSoulSeverPostDamage(livingTarget);
                        break block28;
                    }
                    if (!this.attackRandomized && !this.attackSpecialized) break block29;
                    damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    src = ModDamageTypes.rediosAttack(this.level(), this);
                    dmgHolder = this.resolveAttackDamageHolder();
                    if (dmgHolder != null) {
                        src = new DamageSource(dmgHolder);
                    }
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
                        v0 = hit = playerTarget.hurt(ModDamageTypes.rediosAttack(this.level(), this), normal) != false || hit != false;
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
                sp.setHealth(1.0f);
                this.hardcoreProtectedPlayers.add(sp.getUUID());
                sp.level().broadcastEntityEvent(sp, (byte)3);
                sl = (ServerLevel)sp.level();
                sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
                warn = Component.translatable("message.silent_sun.redios.hardcore_spare").withStyle(ChatFormatting.DARK_RED);
                sp.sendSystemMessage(this.rediosSigned(warn));
            }
            if (!result && this.uncontrolledSprintUnlocked && this.isUncontrolledSprintActive()) {
                if (this.distanceToSqr(livingTarget) > 9.0) {
                    return false;
                }
                damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
                if (livingTarget instanceof ServerPlayer && (sp2 = (ServerPlayer)livingTarget).level().getLevelData().isHardcore() && damage >= sp2.getHealth()) {
                    damage = Math.max(0.0f, sp2.getHealth() - 1.0f);
                    this.hardcoreProtectedPlayers.add(sp2.getUUID());
                    sp2.level().broadcastEntityEvent(sp2, (byte)3);
                    sl = (ServerLevel)sp2.level();
                    sl.playSound(null, sp2.blockPosition(), SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
                    warn = Component.translatable("message.silent_sun.redios.hardcore_spare").withStyle(ChatFormatting.DARK_RED);
                    sp2.sendSystemMessage(this.rediosSigned(warn));
                }
                AbsoluteDamageUtil.damage(livingTarget, ModDamageTypes.rediosAttack(this.level(), this), damage, SilentSunConfig.BOSS_DAMAGE_CREATIVE.get());
                this.settleSoulSeverPostDamage(livingTarget);
                result = true;
            }
            if (!result) {
                return false;
            }
        }
        if (livingTarget.isAlive() && this.isBladeAttackAllowed() && this.isBladeModeActive()) {
            IntegrationContract.tryApplyBossTripleWhammy((LivingEntity)this, livingTarget);
        }
        if (this.isVoidAllThingsActive()) {
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
            src = new DamageSource(dmgHolder);
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
        if (this.isVoidAllThingsActive()) {
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
        return true;
    }

    private boolean damageMultiPart(Entity part, DamageSource src, float damage) {
        if (this.chaosRuinActive || this.chaosRuinAbsoluteAttacks || this.isSharpenTrialActiveNow()) {
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

    void applySoulSeverToTarget(LivingEntity livingTarget) {
        int durationTicks = SilentSunConfig.SOUL_SEVER_DURATION_SECONDS.get() * 20;
        int maxAmplifier = SilentSunConfig.SOUL_SEVER_MAX_AMPLIFIER.get();
        int newAmplifier = 0;
        MobEffectInstance current = livingTarget.getEffect(ModEffects.SOUL_SEVER);
        if (current != null) {
            newAmplifier = Math.min(maxAmplifier, current.getAmplifier() + 1);
        }
        if (livingTarget instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer)livingTarget;
            if (this.isSharpenTrialActive() || CommonEvents.isSharpenSoulSeverMarked(player, this.getUUID())) {
                CommonEvents.markSharpenSoulSever(player, this.getUUID(), newAmplifier);
            }
        }
        livingTarget.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, durationTicks, newAmplifier, true, true));
        long bonus = this.getSoulSeverValue();
        CompoundTag data = livingTarget.getPersistentData();
        long existing = data.getLong("silent_sun:soul_sever_bonus");
        if (bonus > existing) {
            data.putLong("silent_sun:soul_sever_bonus", bonus);
        }
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void settleSoulSeverPostDamage(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        if (target.getEffect(ModEffects.SOUL_SEVER) == null) {
            data.remove("silent_sun:soul_sever_bonus");
            data.remove("silent_sun:soul_sever_applying");
            return;
        }
        if (data.getBoolean("silent_sun:soul_sever_applying")) {
            return;
        }
        long bonus = data.getLong("silent_sun:soul_sever_bonus");
        if (bonus <= 0L) {
            return;
        }
        float appliedBonus = bonus >= 1000000000L ? 1.0E9f : (float)bonus;
        data.putBoolean("silent_sun:soul_sever_applying", true);
        try {
            AbsoluteDamageUtil.soulSeverDamage(target, ModDamageTypes.soulSever(target.level()), appliedBonus);
        }
        finally {
            data.putBoolean("silent_sun:soul_sever_applying", false);
        }
    }

    private void tickSharpenTrial() {
        if (!this.isSharpenTrialActive()) {
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
        if (this.isColorlessActive()) {
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
        return this.phase == 1 && this.titleIndex == 6 || this.isColorlessActive();
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
            if (living == this || !BossTargeting.playerOnlyMode() && this.isFriendlyEntity(living)) continue;
            AbsoluteDamageUtil.damage(living, this.damageSources().fellOutOfWorld(), 1.0f, SilentSunConfig.BOSS_DAMAGE_CREATIVE.get());
            this.settleSoulSeverPostDamage(living);
            this.markSoulSeverIfUnlocked(living);
        }
        if (this.getHealth() > 1.0f) {
            this.setHealth(this.getHealth() - 1.0f);
            this.anticheat.markLegalHealthChange(this.getHealth());
        }
    }

    private boolean isFriendlyEntity(LivingEntity living) {
        OwnableEntity ownable;
        if (living instanceof Player) {
            return true;
        }
        if (!(living instanceof Mob)) {
            return true;
        }
        return living instanceof NeutralMob || living instanceof Animal || living instanceof Npc || living instanceof OwnableEntity && (ownable = (OwnableEntity)living).getOwnerUUID() != null;
    }

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

    private void tickChaosRuinAura(ServerLevel serverLevel) {
        boolean showVisuals;
        showVisuals = this.chaosRuinActive || this.chaosRuinAbsoluteAttacks;
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
        if (!this.chaosRuinActive) {
            return;
        }
        if (this.tickCount % 20 == 0) {
            AABB box = this.getBoundingBox().inflate(radius);
            List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != this && e.distanceToSqr(this) <= radius * radius);
            for (LivingEntity living : entities) {
                if (BossTargeting.playerOnlyMode() ? !(living instanceof ServerPlayer) : this.isFriendlyEntity(living)) continue;
                float auraDamage = 3.0f;
                AbsoluteDamageUtil.damage(living, ModDamageTypes.rediosAttack(this.level(), this), auraDamage);
                this.settleSoulSeverPostDamage(living);
                this.markSoulSeverIfUnlocked(living);
            }
        }
    }

    private void tickLowFpsTargetAdjustment(ServerLevel serverLevel) {
        if (this.battleParticipants.size() <= 1) {
            return;
        }
        LivingEntity target = this.getTarget();
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
            this.setTarget((LivingEntity)other);
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
        for (UUID id : this.battleParticipants) {
            ServerPlayer player;
            if (this.expelledPlayers.contains(id) || (player = this.getServerPlayer(id)) == null || !player.isAlive() || this.isPlayerLowFps(player)) continue;
            return false;
        }
        return true;
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
        this.colorlessChallengeTicks = tag.getInt("SilentSunColorlessChallengeTicks");
        this.noResurrection = tag.getBoolean("SilentSunNoResurrection");
        this.awaitingNoResurrectionPhase2 = tag.getBoolean("SilentSunAwaitingNoResurrectionPhase2");
        this.noResurrection = false;
        this.awaitingNoResurrectionPhase2 = false;
        this.blackSunUnlocked = tag.getBoolean("SilentSunBlackSunUnlocked");
        this.weaknessCurseActive = tag.getBoolean("SilentSunWeaknessCurseActive");
        this.enrageStackingUnlocked = tag.getBoolean("SilentSunEnrageStackingUnlocked");
        this.attackRandomized = tag.contains("SilentSunAttackRandomized") ? tag.getBoolean("SilentSunAttackRandomized") : this.phase == 1 && this.titleIndex == 8;
        this.attackSpecialized = tag.getBoolean("SilentSunAttackSpecialized");
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
        this.dodgeChance = tag.contains("SilentSunDodgeChance") ? (double)tag.getFloat("SilentSunDodgeChance") : 0.0;
        this.battleStartGameTime = tag.contains("SilentSunBattleStartTime") ? tag.getLong("SilentSunBattleStartTime") : -1L;
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
        this.starfallSalvoCooldownTicks = tag.contains("SilentSunStarfallCooldown") ? tag.getInt("SilentSunStarfallCooldown") : (SilentSunConfig.STARFALL_SALVO_INTERVAL_TICKS.get()).intValue();
        this.battleParticipants.clear();
        ListTag battleList = tag.getList("SilentSunBattleParticipants", 10);
        for (int bi = 0; bi < battleList.size(); ++bi) {
            CompoundTag entry = battleList.getCompound(bi);
            if (!entry.hasUUID("Id")) continue;
            this.battleParticipants.add(entry.getUUID("Id"));
        }
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
        tag.putInt("SilentSunColorlessChallengeTicks", this.colorlessChallengeTicks);
        tag.putBoolean("SilentSunNoResurrection", this.noResurrection);
        tag.putBoolean("SilentSunAwaitingNoResurrectionPhase2", this.awaitingNoResurrectionPhase2);
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
        tag.putFloat("SilentSunDodgeChance", (float)this.dodgeChance);
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
        return this.bossState.isCombat();
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
            Double d = this.playerNetDamageTotals.get(player.getUUID());
            return d == null ? 0.0 : d.doubleValue();
        }
        return 0.0;
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

    private void enterPhase2() {
        this.enterPhase2Combat();
    }

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
        float lockPoint = low + 1.0f;
        float min = this.titleIndex == titles.size() - 1 ? low + epsilon : lockPoint;
        float clampHigh = SilentSunConfig.ALLOW_TITLE_LOCK_HEAL_REGRESSION.get() != false ? maxHealth : high - epsilon;
        float clamped = Mth.clamp(this.getHealth(), min, clampHigh);
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
        if (!this.colorlessUnlocked || !this.bossState.isCombat()) {
            return;
        }
        this.applyColorlessPermanentBuffs();
        if (this.tickCount % 100 == 0) {
            this.grantEnrageLevels(1);
        }
    }

    private void applyColorlessPermanentBuffs() {
        this.healBoostTicks = 600;
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 4, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false), this);
        this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, true, false), this);
        this.ashDawnUnlocked = true;
        this.dodgeChance = Math.max(this.dodgeChance, 0.15);
        this.chaosRuinAbsoluteAttacks = true;
        this.enrageStackingUnlocked = true;
    }

    private void endBattleHalfDayCooldown() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            this.safeDiscard();
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        this.settleBattle(serverLevel, (long)(SilentSunConfig.COOLDOWN_HALF_DAYS.get() * 24000.0), true, false);
    }

    private void endBattleHalfDayDefeatCooldown() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
            this.safeDiscard();
            return;
        }
        ServerLevel serverLevel = (ServerLevel)level;
        this.settleBattle(serverLevel, (long)(SilentSunConfig.COOLDOWN_HALF_DAYS.get() * 24000.0), this.phase == 2, true);
    }

    private boolean isDarkStarActive() {
        return this.phase == 2 && this.titleIndex == 6;
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
            int idx = ++this.darkStarBlastNextIndex;
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
                if (RediosRules.restoreNbt() && (blockEntity = serverLevel.getBlockEntity(pos)) instanceof CommandBlockEntity) {
                    CommandBlockEntity cbe = (CommandBlockEntity)blockEntity;
                    BaseCommandBlock cmd = cbe.getCommandBlock();
                    entry.putString("Cmd", cmd.getCommand());
                    entry.put("BlockEntity", (Tag)cbe.saveWithoutMetadata((HolderLookup.Provider)serverLevel.registryAccess()));
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
        return state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK);
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
            if (!RediosRules.restoreNbt() || !((blockEntity = serverLevel.getBlockEntity(pos)) instanceof CommandBlockEntity)) continue;
            CommandBlockEntity cbe = (CommandBlockEntity)blockEntity;
            if (tag.contains("BlockEntity", 10)) {
                cbe.loadWithComponents(tag.getCompound("BlockEntity"), (HolderLookup.Provider)serverLevel.registryAccess());
                cbe.setChanged();
                continue;
            }
            if (!tag.contains("Cmd")) continue;
            cbe.getCommandBlock().setCommand(tag.getString("Cmd"));
            cbe.setChanged();
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
        this.tickDarkStarBlast(serverLevel);
        this.getNavigation().stop();
        this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
        double r = 13.5;
        AABB box = new AABB(this.getX() - r, this.getY() - r, this.getZ() - r, this.getX() + r, this.getY() + r, this.getZ() + r);
        List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class, box, p -> !p.isSpectator() && !p.isCreative());
        for (ServerPlayer player : players) {
            Vec3 diff = player.position().subtract(this.position());
            if (diff.lengthSqr() < 1.0E-6) {
                diff = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 push = diff.normalize().scale(1.5);
            player.push(push.x, 0.2, push.z);
        }
        this.forEachMobOpponent(target -> {
            if (!target.getBoundingBox().intersects(box)) {
                return;
            }
            Vec3 diff = target.position().subtract(this.position());
            if (diff.lengthSqr() < 1.0E-6) {
                diff = new Vec3(1.0, 0.0, 0.0);
            }
            Vec3 push = diff.normalize().scale(1.5);
            target.push(push.x, 0.2, push.z);
        });
        if (!this.darkStarFlightUnlocked && this.getY() < 0.0) {
            this.darkStarFlightUnlocked = true;
            this.setNoGravity(true);
            this.repairBedrockLayer(serverLevel);
        }
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
        int retained = active + this.twilightExpelled.size();
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
            PacketDistributor.sendToPlayer(player, new BlackSunDefeatPayload(), (CustomPacketPayload[])new CustomPacketPayload[0]);
        }
        this.endBattleThreeDayCooldown();
    }

    private void endBattleThreeDayCooldown() {
        Level level = this.level();
        if (!(level instanceof ServerLevel)) {
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
        if (loot.isEmpty()) {
            return;
        }
        BlockPos placePos = this.findNearbyRewardPlacement(serverLevel);
        boolean placed = false;
        if (placePos != null) {
            placed = ShulkerBoxUtil.placeShulkerBox(serverLevel, placePos, Blocks.BROWN_SHULKER_BOX.defaultBlockState(), loot, Component.translatable("container.silent_sun.phase1_reward"));
        }
        if (!placed) {
            ItemStack box = ShulkerBoxUtil.createShulkerBox(Items.BROWN_SHULKER_BOX, loot, Component.translatable("container.silent_sun.phase1_reward"));
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

    private void updateBattleRecord(ServerLevel serverLevel) {
        double dz;
        double dy;
        double dx;
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
        RediosBattleData.get(serverLevel).upsert(this.getUUID(), dim, this.blockPosition(), this.phase, this.titleIndex, serverLevel.getGameTime(), this.getHealth(), this.soulSeverY, this.bossState.ordinal(), this.titleLockTicks, this.colorlessChallengeTicks, this.battleParticipants, this.expelledPlayers);
    }

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
        RediosBattleData.get(serverLevel).remove(this.getUUID());
        this.clearDamageDebugCaches();
    }

    private void settleBattle(ServerLevel serverLevel, long cooldownTicks, boolean dropPhase1Reward, boolean includeDefeatBook) {
        if (this.settlementDone) {
            return;
        }
        this.settlementDone = true;
        this.restoreDarkStarSpecialBlocks(serverLevel);
        if (dropPhase1Reward) {
            this.dropPhase1Reward(serverLevel, includeDefeatBook);
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
        this.clearBattleRecord(serverLevel);
        this.safeDiscard();
    }

    private void cleanupPlayersAfterBattle(ServerLevel serverLevel) {
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
        this.removeAntiCheatCooldowns();
    }

    private void cleanupPlayerAfterBattle(ServerPlayer player) {
        this.stopBattleMusicFor(player);
        CommonEvents.clearPhase2ChoicePending(player);
        CommonEvents.clearSharpenSoulSever(player);
        CommonEvents.clearRootlessBuffBlock(player);
        CommonEvents.clearMirrorFaceAttackBoost(player);
        player.removeEffect(ModEffects.SOUL_SEVER);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.WEAKNESS);
    }

    private void removeAntiCheatCooldowns() {
        this.anticheat.antiCheatCooldownPlayers.clear();
    }

    private void cleanupNearbyLivingAfterBattle(ServerLevel serverLevel) {
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(64.0))) {
            if (entity == this) continue;
            entity.removeEffect(ModEffects.SOUL_SEVER);
            entity.removeEffect(MobEffects.DARKNESS);
            entity.removeEffect(MobEffects.WEAKNESS);
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
            player.getAdvancements().award(advancement, "silent_sun:" + name);
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
        boss.restoreStateFromNbt(recordTag);
        boss.battleAnchorPos = record.pos;
        boss.battleAnchorDim = record.dimension;
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
        level.addFreshEntity(boss);
        boss.leaveReason = LeaveReason.ANOMALY;
        boss.broadcastToParticipants(boss.rediosSigned(Component.translatable("message.silent_sun.redios.rebuilt_after_purge").withStyle(ChatFormatting.RED)));
        SilentSunMod.LOGGER.warn("Redios rebuilt from battle record at {} (externally removed, phase={}, leaveReason={})", new Object[]{record.pos, record.phase, boss.leaveReason});
        boss.leaveReason = LeaveReason.NONE;
        return true;
    }

    public void settleByUnloadTimeout(ServerLevel serverLevel) {
        if (this.isRemoved()) {
            return;
        }
        this.leaveReason = LeaveReason.CHUNK_UNLOAD;
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
        if (this.bossState.isVoteOrTransition()) {
            return;
        }
        if (serverLevel.getNearestPlayer(this, 128.0) != null) {
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
        // 非硬核不再追人（用户裁决：逃离优先）。玩家 >128 格时交 checkAllParticipantsDisengaged
        // 的 64 格 + 100 tick 判定触发逃离脱战。
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
        if (this.allParticipantsDeadTicks < 200) {
            return false;
        }
        this.allParticipantsDeadTicks = 0;
        this.bossLeaveNoLoot();
        return true;
    }

    private boolean checkAllParticipantsDisengaged(ServerLevel serverLevel) {
        // 投票/转阶段/濒死（安全窗口）期间，区块卸载判定被豁免；此处独立按「玩家距离」判定主动逃离脱战，不受 isSafeWindow 豁免。
        // 所有活跃参战玩家都远离 Boss >64 格持续 5 秒 → 无奖励退场；区块短暂卸载（玩家仍在 64 格内）→ 不退场。
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
            if (player.distanceToSqr(this) <= 64.0 * 64.0) {
                anyClose = true;
                break;
            }
        }
        if (active == 0 || anyClose) {
            this.disengageTicks = 0;
            return false;
        }
        ++this.disengageTicks;
        if (this.disengageTicks < 100) {
            return false;
        }
        this.disengageTicks = 0;
        this.bossLeaveNoLoot();
        return true;
    }

    private boolean checkBattleAreaUnloaded(ServerLevel serverLevel) {
        // 安全窗口（投票/转阶段/一阶段濒死等待）豁免区块卸载结算，防止雪地等地形下玩家短暂未加载导致投票无法结束、Boss 无奖励消失
        if (this.bossState.isSafeWindow()) {
            this.battleAreaUnloadedTicks = 0;
            return false;
        }
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
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || !player.isAlive() || player.level() != this.level() || !serverLevel.isPositionEntityTicking(player.blockPosition()) || player.isCreative() && !(player.distanceToSqr(this) <= 16384.0)) continue;
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
        EnrageEffect.applyFragileIfEnraged((LivingEntity)this, desiredAmp);
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
        if (!this.isVoidAllThingsActive()) {
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
        this.setTarget(target2);
    }

    private LivingEntity pickVoidTeleportTarget(ServerLevel serverLevel) {
        if (!BossTargeting.playerOnlyMode()) {
            LivingEntity t = this.getTarget();
            if (t != null && t.isAlive() && t.level() == this.level() && !(t instanceof Player)) {
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
            if (!serverLevel.getBlockState(feet).isAir()) {
                serverLevel.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            }
            if (!serverLevel.getBlockState(head).isAir()) {
                serverLevel.setBlock(head, Blocks.AIR.defaultBlockState(), 3);
            }
            Vec3 dest = Vec3.atBottomCenterOf((Vec3i)feet);
            // 轨道 A：phase2.9 主动避让玩家立体锁定球体（无妄之终），相交则抬升到球顶+1。
            if (this.isVoidAllThingsActive()) {
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
        if (this.bossState.isVoteOrTransition() || this.isDarkStarActive()) return;
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
            if (player == null || this.expelledPlayers.contains(id) || player.isSpectator() || player.isCreative() || !player.isAlive() || player.level() != this.level() || this.distanceToSqr(player) <= 262144.0) continue;
            ChunkPos cp = player.chunkPosition();
            if (!serverLevel.getChunkSource().hasChunk(cp.x, cp.z)) continue;
            this.battleParticipants.remove(id);
            this.weapons.removeGuardStats(id);
            player.sendSystemMessage(this.rediosSigned(Component.translatable("message.silent_sun.redios.expelled").withStyle(ChatFormatting.DARK_RED)));
            this.cleanupPlayerAfterBattle(player);
        }
    }

    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> movements, float yRot, float xRot) {
        if (!this.allowSelfTeleport && this.isVoidAllThingsActive()) {
            return false;
        }
        return super.teleportTo(level, x, y, z, movements, yRot, xRot);
    }

    public void teleportTo(double x, double y, double z) {
        if (!this.allowSelfTeleport && this.isVoidAllThingsActive()) {
            return;
        }
        super.teleportTo(x, y, z);
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
        if (outcome == RediosBookOutcome.PHASE2_WIN) {
            if (!hasBeacon) {
                loot.add(new ItemStack(Items.BEACON));
            }
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
        return loot;
    }

    private List<ItemStack> createPhase2Loot(ServerLevel serverLevel, boolean doubleRollOnEmpty) {
        ArrayList<ItemStack> loot = new ArrayList<ItemStack>(RediosRewardOverrideConfig.getOverrideStacks(2, this.titleIndex));
        boolean hasOverride = !loot.isEmpty();
        loot.add(new ItemStack(ModItems.REDIOS_DISC_PHASE2.get()));
        if (!hasOverride) {
            loot.addAll(RediosLootConfig.roll(serverLevel.random));
            if (doubleRollOnEmpty) {
                loot.addAll(RediosLootConfig.roll(serverLevel.random));
            }
        }
        // 灭却之日（required 前置）提供的二阶段固定掉落「长梦彼端的灾厄之影」。
        // 走注册表查找而非反射，避免编译期硬依赖；灭却之日未提供该物品时静默跳过。
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("extinction_day_mod_1784441698", "calamity_shadow"))
            .ifPresent(calamityShadow -> loot.add(new ItemStack(calamityShadow)));
        return loot;
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
        boolean countdown = this.phase == 1 && (this.titleIndex == 7 || this.titleIndex == 8) || this.phase == 2 && this.titleIndex == 5;
                if (!countdown || this.titleLockTicks <= 0) {
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
        ServerPlayer player;
        TitleDef[] defs;
        Level level;
        this.clearAllExternalEffects();
        this.reapplySelfBuffs();
        if (oldPhase == 2 && oldTitleIndex == 6 && (newPhase != 2 || newTitleIndex != 6) && (level = this.level()) instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            this.restoreDarkStarSpecialBlocks(serverLevel);
        }
        this.wrongInterferenceActive = newPhase == 2 && newTitleIndex == 2;
        this.chaosRuinActive = newPhase == 2 && newTitleIndex == 3;
        this.ashDawnActive = newPhase == 2 && newTitleIndex == 4;
                if (this.ashDawnActive) {
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
            this.dodgeChance = Math.max(this.dodgeChance, 0.15);
            this.grantFlag(BossFlag.DODGE_MIN_15);
        }
        if (oldPhase == 2 && oldTitleIndex == 3 && (newPhase != 2 || newTitleIndex != 3)) {
            this.grantFlag(BossFlag.CHAOS_RUIN_ABSOLUTE);
            this.chaosRuinAbsoluteAttacks = true;
        }
        double d = this.isWhoseWishActive() ? 1.0 : (this.reflectRatio = this.colorlessUnlocked ? RediosRules.colorlessReflectRatio() : 0.0);
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
        if (newPhase == 1 && newTitleIndex == 8 && this.level() instanceof ServerLevel) {
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
            this.healBoostTicks = 600;
            this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 4, true, false), this);
            this.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false), this);
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4, true, false), this);
            this.ashDawnUnlocked = true;
            this.dodgeChance = Math.max(this.dodgeChance, 0.15);
            this.chaosRuinAbsoluteAttacks = true;
            this.enrageStackingUnlocked = true;
            this.colorlessUnlocked = true;
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
        this.mobParticipants.removeIf(id -> this.getMobParticipant((UUID)id) == null);
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
        ServerLevel serverLevel;
        long elapsed;
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
        if (this.battleStartGameTime < 0L && (level2 = this.level()) instanceof ServerLevel) {
            ServerLevel serverLevel2 = (ServerLevel)level2;
            this.battleStartGameTime = serverLevel2.getGameTime();
            this.anticheat.deathCheatStrikeCount = 0;
        }
        if ((added = this.battleParticipants.add(id)) && !this.bossOutlineEnabled && this.battleParticipants.size() == 1 && (level = this.level()) instanceof ServerLevel) {
            ServerLevel sl = (ServerLevel)level;
            this.enableBossOutline(sl);
        }
        if (sp != null && this.battleStartGameTime >= 0L && (level = this.level()) instanceof ServerLevel && (elapsed = (serverLevel = (ServerLevel)level).getGameTime() - this.battleStartGameTime) <= 200L) {
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
            if (this.redios.bossState.isVoteOrTransition() || this.redios.isDarkStarActive()) {
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
        private final RediosEntity redios;
        private final double speed;
        private int attackCooldownTicks = 0;
        private int pathRecalcTicks = 0;

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
            if (this.redios.bossState.isVoteOrTransition() || this.redios.isDarkStarActive()) {
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
                reach = this.redios.getCurrentAttackReach();
                reachSqr = reach * reach;
                if (this.redios.distanceToSqr(target) > reachSqr) {
                    double moveSpeed = this.redios.heightFlightMode ? this.speed * 1.35 : this.speed;
                    this.redios.getNavigation().moveTo(target, moveSpeed);
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
            if (this.redios.isDarkStarActive()) {
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
