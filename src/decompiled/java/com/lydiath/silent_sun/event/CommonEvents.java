/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.event;

import com.lydiath.silent_sun.data.RediosBattleData;
import com.lydiath.silent_sun.data.RediosCooldownData;
import com.lydiath.silent_sun.data.RediosCooldownData;
import com.lydiath.silent_sun.entity.IntegrationContract;
import com.lydiath.silent_sun.entity.RediosEntity;
import com.lydiath.silent_sun.loot.RediosLocalConfigReloadListener;
import com.lydiath.silent_sun.loot.RediosLootConfig;
import com.lydiath.silent_sun.loot.RediosRewardOverrideConfig;
import com.lydiath.silent_sun.config.SilentSunConfig;
import com.lydiath.silent_sun.registry.ModDamageTypes;
import com.lydiath.silent_sun.registry.ModEffects;
import com.lydiath.silent_sun.rules.RediosRules;
import com.lydiath.silent_sun.rules.RediosRulesReloadListener;
import com.lydiath.silent_sun.util.AbsoluteDamageUtil;
import com.lydiath.silent_sun.util.BookTextReloadListener;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid="silent_sun")
public final class CommonEvents {
    public static final String SOUL_SEVER_BONUS_KEY = "silent_sun:soul_sever_bonus";
    public static final String SOUL_SEVER_APPLYING_KEY = "silent_sun:soul_sever_applying";
    private static final String PHASE2_CHOICE_BOSS_KEY = "silent_sun:phase2_choice_boss";
    private static final String SHARPEN_SOUL_SEVER_BOSS_KEY = "silent_sun:sharpen_soul_sever_boss";
    private static final String SHARPEN_SOUL_SEVER_AMP_KEY = "silent_sun:sharpen_soul_sever_amp";
    private static final String ROOTLESS_BUFF_BLOCK_BOSS_KEY = "silent_sun:rootless_buff_block_boss";
    private static final String MIRROR_FACE_ATTACK_BOSS_KEY = "silent_sun:mirror_face_attack_boss";
    private static final String MIRROR_FACE_ATTACK_VALUE_KEY = "silent_sun:mirror_face_attack_value";
    /** 全伤害类型随机调用（拔刀剑路径）防重入标记：重施加的随机伤害不再二次随机 */
    private static final String BLADE_DAMAGE_RANDOMIZED_KEY = "silent_sun:blade_damage_randomized";
    private static final ResourceLocation MIRROR_FACE_ATTACK_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath("silent_sun", "mirror_face_attack_damage");
    /** 拔刀剑全局兜底清扫的 tick 计数器（每 20 tick 跨维度清扫一次危险暴击剑气） */
    private static int bladeGlobalSweepTick = 0;

    // ── K1: 断魂不可被牛奶清除（1.21.1 替代实现） ──
    // 1.21.1 移除了 MobEffect.isCurativeItem，牛奶改为 finishUsingItem 内
    // removeAllEffects 全量清除。替代方案：开始喝牛奶时快照断魂状态，
    // 饮用完成（断魂已被牛奶清除）后原样重新施加，其他效果照常被清除。
    private static final Map<UUID, MobEffectInstance> MILK_SOUL_SEVER_SNAPSHOT = new HashMap<>();

    /**
     * 反破解兜底（禁生成 / 防多 Boss 并存）：世界内已存在其他 Redios 时拒绝新实体加入。
     * 覆盖 addFreshEntity / 命令直接塞入等绕过 finalizeSpawn 的生成途径。
     * 仅对 RediosEntity 做检查，普通实体零开销。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        // 拔刀剑剑气（EntityDrive）入世即清扫：负攻击力 owner（无色虚弱诅咒减攻为负）的暴击剑气
        // 强制关暴击，从源头杜绝 EntityDrive.onHitEntity 负伤害暴击 nextInt(负) 崩服。
        // 与 Boss 是否在场/战斗态解耦，覆盖跨维度、脱离战斗后残留剑气的崩溃窗口。
        IntegrationContract.sanitizeBladeDriveOnJoin(event.getEntity());
        // IShootable 投射物（刀光/次元斩/幻影剑）入世即补 shooter：孤儿投射物（owner==null）被玩家
        // 扫描 ArrowReflector 时 getShooter()==null 会 NPE 崩端，此处关掉「入世→被扫描」的 1 tick 空窗。
        IntegrationContract.sanitizeShooterOnJoin(event.getEntity());
        if (!(event.getEntity() instanceof RediosEntity redios)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        // M16：旧版本残留 Boss（未加载区块里老 jar 生成的 Boss 后续加载）→ 拒绝加入
        if (redios.isLegacyData()) {
            event.setCanceled(true);
            return;
        }
        if (RediosEntity.isAnotherRediosPresent(serverLevel, redios)) {
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getItem().is(Items.MILK_BUCKET)) return;
        MobEffectInstance soulSever = player.getEffect(ModEffects.SOUL_SEVER);
        if (soulSever != null) {
            MILK_SOUL_SEVER_SNAPSHOT.put(player.getUUID(), new MobEffectInstance(soulSever));
        }
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        MobEffectInstance snapshot = MILK_SOUL_SEVER_SNAPSHOT.remove(id);
        if (snapshot == null) return;
        if (!player.isAlive()) return;
        // Finish 事件在 finishUsingItem（牛奶清除效果）之后触发，此处重新施加断魂
        player.addEffect(snapshot);
    }

    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getItem().is(Items.MILK_BUCKET)) return;
        // M15：饮用中断（未 Finish）丢弃快照，避免陈旧断魂快照残留、下次喝奶误回灌旧断魂
        MILK_SOUL_SEVER_SNAPSHOT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        RediosLootConfig.loadOrCreate();
        RediosRewardOverrideConfig.loadOrCreate();
        // 2026-08-30 用户裁决：每次进入游戏清理全版本遗留莱德厄斯（实体 + 账本 + 冷却），
        // 防止老版本 jar 生成的 Boss（旧 NBT 数据）带着旧逻辑直接加载进世界。
        // 2026-09-09：清理入口收窄到 RediosEntity.purgeAllResidualBosses（防打穿，不再暴露公开静默剔除）。
        MinecraftServer server = event.getServer();
        RediosEntity.purgeAllResidualBosses(server);
        ServerLevel overworld = server.overworld();
        RediosBattleData.get(overworld).clearAllRecords();
        RediosCooldownData.get(overworld).resetCooldown();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new RediosRulesReloadListener());
        event.addListener(new RediosLocalConfigReloadListener());
        event.addListener(new BookTextReloadListener());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("silent_sun").requires(source -> source.hasPermission(2))).then(Commands.literal("redios").then(Commands.literal("reload_configs").executes(CommonEvents::runRediosReloadConfigs)).then(Commands.literal("reset_summon_cd").executes(CommonEvents::runRediosResetSummonCd)))).then(Commands.literal("reload_redios_configs").executes(CommonEvents::runRediosReloadConfigs))).then(Commands.literal("reload_all").executes(CommonEvents::runReloadAll)));
    }

    private static int runRediosReloadConfigs(CommandContext<CommandSourceStack> ctx) {
        boolean okLoot = RediosLootConfig.reload();
        boolean okOverrides = RediosRewardOverrideConfig.reload();
        (ctx.getSource()).sendSuccess(() -> Component.translatable((String)(okLoot && okOverrides ? "command.silent_sun.redios.reload_configs.success" : "command.silent_sun.redios.reload_configs.fail")), true);
        String lootPath = String.valueOf(RediosLootConfig.getConfigPath());
        String overridesPath = String.valueOf(RediosRewardOverrideConfig.getConfigPath());
        String lootError = String.valueOf(RediosLootConfig.lastError());
        String overridesError = String.valueOf(RediosRewardOverrideConfig.lastError());
        (ctx.getSource()).sendSuccess(() -> Component.translatable("command.silent_sun.redios.reload_configs.loot_detail", (Object[])new Object[]{lootPath, okLoot, RediosLootConfig.entryCount(), lootError}), true);
        (ctx.getSource()).sendSuccess(() -> Component.translatable("command.silent_sun.redios.reload_configs.overrides_detail", (Object[])new Object[]{overridesPath, okOverrides, RediosRewardOverrideConfig.overrideCount(), overridesError}), true);
        return okLoot && okOverrides ? 1 : 0;
    }

    private static int runRediosResetSummonCd(CommandContext<CommandSourceStack> ctx) {
        ServerLevel overworld = ctx.getSource().getServer().overworld();
        // 管理员清理扩展：标记全维度存活 Boss 待离场（任意状态；区块静止的 Boss 解冻恢复 tick 后自动无掉落退场）
        RediosEntity.requestCommandLeaveAll(ctx.getSource().getServer());
        RediosCooldownData.get(overworld).resetCooldown();
        RediosBattleData.get(overworld).clearAllRecords();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.silent_sun.redios.reset_summon_cd.success"), true);
        return 1;
    }

    private static int runReloadAll(CommandContext<CommandSourceStack> ctx) {
        try {
            (ctx.getSource()).getServer().getCommands().performPrefixedCommand(ctx.getSource(), "reload");
            return 1;
        }
        catch (Exception e) {
            (ctx.getSource()).sendFailure(Component.translatable("command.silent_sun.reload_all.dispatch_failed"));
            return 0;
        }
    }

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) return;
        // 振刀判定：玩家在 Boss 攻击动作未完成时出手命中 → Boss 5s 攻击迟缓（15s CD）
        if (event.getTarget() instanceof RediosEntity boss
            && event.getEntity() instanceof Player player) {
            boss.triggerWeaponWeakpoint(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // G7: 极限模式玩家处于 Boss 战锁血保护中时，任何伤害最低保留 1 点生命，
        // 直到玩家真正死亡（死亡界面选择回到出生点）或脱离战斗。
        // 非致命伤害直接短路：既省一次 2048 格类过滤查询，也避免把钳制逻辑用在非致命伤害上。
        if (event.getNewDamage() < player.getHealth()) return;
        if (hasHardcoreProtector(player)) {
            event.setNewDamage(Math.max(0.0f, player.getHealth() - 1.0f));
        }
    }

    /**
     * M14 硬核 1 血锁**最终兜底**（2026-09-11 用户裁决 C2 → 选项 A）。
     * <p>
     * 前面四条 {@code clampHardcoreProtected} 都挂在 {@code LivingDamageEvent.Pre} / 兜底事件上，
     * 但**真正致命的断魂通道不经过这些事件**：灭却之日的 {@code SoulSeverMobEffect.applyTrueDamage}
     * 是 {@code hurt()} + **{@code setHealth} 差额补扣**，扣到 0 时直接走死亡流程。因此这里加一道
     * 死亡事件拦截：受保护硬核玩家死亡 → 取消死亡并钳回 1 血。任何通道（含第三方直写血量）都拦得住。
     * <p>
     * 半径放宽到 2048 格：硬核锁的意义是"玩家不该被本模组的伤害打死"，不该因为离得远就失效。
     * <p>
     * 2026-09-11（代码审计 G12 #1 修复）：半径收敛为唯一常量 {@link #HARDCORE_PROTECT_RADIUS}，
     * 判据收敛为唯一方法 {@link #hasHardcoreProtector}，不再各处自写一套阈值。
     */
    @SubscribeEvent
    public static void onLivingDeathHardcoreProtected(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (hasHardcoreProtector(player)) {
            event.setCanceled(true);
            player.setHealth(1.0f);
        }
    }

    /**
     * 绝对真实伤害核心锁定（始终启用）。
     * <p>
     * {@link AbsoluteDamageUtil#damage} 以 hurt() 走完整链路，并在目标上标记最终应锁定的
     * 真伤值。此处以最低优先级（最后运行）把 {@code LivingDamageEvent.Pre} 的最终伤害
     * 锁回标记值，从而绕过护甲/附魔/减伤模组在 Pre 之前的减免。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAbsoluteDamageLockPre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (!AbsoluteDamageUtil.isAbsoluteDamageMarked(target)) {
            return;
        }
        float damage = AbsoluteDamageUtil.getAbsoluteDamageValue(target);
        damage = clampHardcoreProtected(target, damage);
        event.setNewDamage(damage);
    }

    /**
     * 硬核 1 血锁的保护半径（格）。
     * <p>
     * 2026-09-11（代码审计 G12 #1 修复）：原先三处判据半径各不相同——{@code onLivingDamagePre} 用
     * 全维度 {@code getEntities().getAll()}、死亡拦截用 2048 格、{@code clampHardcoreProtected} 用 128 格，
     * 且 128 格那份的注释还自称「语义等价」。实际后果：玩家距 Boss 超过 128 格时，默认优先级先把伤害钳到
     * {@code health-1}，随后 LOWEST 的 {@code onSoulSeverDamageLockPre} 再次调用钳制方法，因 128 格过滤
     * 返回**未钳制的原值**并覆盖前一次钳制 → 致命伤害照常落地，只能靠死亡事件拦截兜底；即 128~2048 格
     * 区间走的是「先进 {@code die()} 再取消」的降级路径（第三方统计/墓碑类模组会真实收到一次死亡事件）。
     * <p>
     * 现三处统一用本半径，取 2048：硬核锁的语义是「玩家不该被本模组的伤害打死」，不该因距离失效。
     * 性能上无损——本判据只在**致命伤害**时被调用（各调用点均有 {@code damage >= health} 短路），
     * 且原先 {@code onLivingDamagePre} 走的是全维度实体遍历，比 2048 格类过滤更慢。
     */
    private static final double HARDCORE_PROTECT_RADIUS = 2048.0;

    /**
     * 本维度内是否存在正在保护该玩家的莱德厄斯（唯一硬核保护判据，三处调用点共用）。
     */
    private static boolean hasHardcoreProtector(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !level.getLevelData().isHardcore()) {
            return false;
        }
        for (RediosEntity redios : level.getEntitiesOfClass(RediosEntity.class, player.getBoundingBox().inflate(HARDCORE_PROTECT_RADIUS))) {
            if (redios.isAlive() && !redios.isRemoved() && redios.isHardcoreProtected(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * M14 硬核 1 血锁（2026-09-10 用户裁决：**两条伤害链共用同一判据**）。
     * <p>
     * 受莱德厄斯 {@code isHardcoreProtected} 标记的硬核玩家，无论被哪条通道命中（绝对真伤
     * {@code AbsoluteDamageUtil} 标记，或断魂 {@code soul_sever} 标记），伤害一律钳到
     * {@code health - 1}，保 1 点生命。
     * <p>
     * 原实现只写在绝对真伤那条链里 → 断魂走另一套标记（{@code isSoulSeverDamageMarked}）可以
     * **越过 1 血锁直接击杀**硬核保护玩家，与 M14 本意矛盾（W5）。
     */
    private static float clampHardcoreProtected(LivingEntity target, float damage) {
        if (!(target instanceof ServerPlayer player)) {
            return damage;
        }
        if (hasHardcoreProtector(player)) {
            return Math.min(damage, Math.max(0.0f, player.getHealth() - 1.0f));
        }
        return damage;
    }

    /**
     * 绝对真实伤害兜底（兼容性补丁，默认关闭）。
     * <p>
     * 若第三方模组在 {@code LivingIncomingDamageEvent} 阶段取消或改小真伤（发生在 Pre 之前），
     * 会连带使真伤失效。开启 {@code redios.absoluteDamageFallback} 后，此处以最低优先级恢复
     * 标记值并解除取消，确保真伤一定命中。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAbsoluteDamageFallback(LivingIncomingDamageEvent event) {
        if (!SilentSunConfig.ABSOLUTE_DAMAGE_FALLBACK.get()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (!AbsoluteDamageUtil.isAbsoluteDamageMarked(target)) {
            return;
        }
        event.setCanceled(false);
        event.setAmount(clampHardcoreProtected(target, AbsoluteDamageUtil.getAbsoluteDamageValue(target)));
    }

    /**
     * 断魂伤害核心锁定（始终启用）。
     * <p>
     * 断魂伤害走 {@link AbsoluteDamageUtil#soulSeverDamage}，以 hurt() 完整链路并在目标上标记
     * 最终应锁定的断魂值（baseX + soulSeverY）。此处以最低优先级把 {@code LivingDamageEvent.Pre}
     * 的最终伤害锁回该值，确保断魂无视护甲 / 附魔 / 减伤模组的减免。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSoulSeverDamageLockPre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (!AbsoluteDamageUtil.isSoulSeverDamageMarked(target)) {
            return;
        }
        // 2026-09-10（W5）：断魂同样受 M14 硬核 1 血锁约束——原实现只锁断魂值、没有硬核判定，
        // 于是断魂成了唯一能越过 1 血锁击杀硬核保护玩家的通道（与本意矛盾）。
        float locked = AbsoluteDamageUtil.getSoulSeverDamageValue(target);
        event.setNewDamage(clampHardcoreProtected(target, locked));
    }

    /**
     * 断魂伤害补刀（始终启用）。
     * <p>
     * 若第三方模组在 {@code LivingIncomingDamageEvent} 阶段取消 / 改小断魂伤害
     * （发生在 Pre 之前，会连带截断 hurt 流程），此处以最低优先级恢复标记值并解除取消，
     * 确保断魂伤害一定落地（图腾仍在 hurt 死亡链内正常触发）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSoulSeverDamageFallback(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (!AbsoluteDamageUtil.isSoulSeverDamageMarked(target)) {
            return;
        }
        event.setCanceled(false);
        event.setAmount(clampHardcoreProtected(target, AbsoluteDamageUtil.getSoulSeverDamageValue(target)));
    }

    /**
     * 全伤害类型随机调用（拔刀剑路径）。
     * <p>
     * Boss 的拔刀剑连击走 SlashBlade {@code AttackHelper.attack → target.hurt}，不经过
     * {@code doHurtTarget}（那里已有 attackRandomized 逻辑）。此处拦截 Boss 直接造成的
     * 伤害（source.getDirectEntity()==Boss 且主手持拔刀剑），取消原伤害后以随机伤害类型
     * 重新施加（与 doHurtTarget 同款过滤）。
     * <p>
     * 重入保护：重新施加的伤害再次触发本事件时直接放行，避免二次随机/无限递归。
     * 只拦截直接攻击；SA 飞行道具（directEntity=道具实体）不受影响。
     */
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        CompoundTag data = target.getPersistentData();
        if (data.getBoolean(BLADE_DAMAGE_RANDOMIZED_KEY)) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof RediosEntity redios)) {
            return;
        }
        if (!redios.isAttackRandomized()) {
            return;
        }
        if (!IntegrationContract.isSlashBladeItem(redios.getMainHandItem().getItem())) {
            return;
        }
        DamageSource newSource = redios.randomAttackSource();
        if (newSource == null) {
            return;
        }
        float amount = event.getAmount();
        event.setCanceled(true);
        data.putBoolean(BLADE_DAMAGE_RANDOMIZED_KEY, true);
        boolean dealt;
        try {
            dealt = target.hurt(newSource, amount);
        } finally {
            data.remove(BLADE_DAMAGE_RANDOMIZED_KEY);
        }
        // 原伤害被取消后 AttackHelper 走了 miss 分支（无击退），此处按 SlashBlade 同款 0.5 倍率补齐
        if (dealt && target.isAlive()) {
            float kb = (float) redios.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
            if (kb > 0.0f) {
                target.knockback(kb * 0.5f, Mth.sin(redios.getYRot() * 0.017453292f), -Mth.cos(redios.getYRot() * 0.017453292f));
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        // 断魂结算已统一到灭却之日（2026-09-01 用户裁决）：Boss 侧只负责挂账本
        //（recordSoulSeverLedgerBoss，层值 = 头衔依赖值），结算由灭却之日 SoulSeverMobEffect
        // 的受击 Post 事件统一触发（玩家账本 + Boss 账本双轨求和 + 9 bypass + 每 tick 去重）。
        // 本侧原 bonus 结算（trySettleSoulSever）与常量已移除，不再参与断魂伤害计算。
        // EG2: FragileEffect damage amplification (continues even without Soul Sever)
        applyFragileDamage(event, target);
    }

    // EG2: FragileEffect damage amplification — each level adds 5% extra damage
    private static void applyFragileDamage(LivingDamageEvent.Post event, LivingEntity target) {
        MobEffectInstance fragile = target.getEffect(ModEffects.FRAGILE);
        if (fragile == null) return;
        int amp = fragile.getAmplifier() + 1; // level = amp + 1
        float extraDamage = event.getNewDamage() * amp * 0.05f;
        if (extraDamage <= 0.0f) return;
        AbsoluteDamageUtil.damage(target, target.damageSources().magic(), extraDamage);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        UUID bossId;
        Player player2 = event.getEntity();
        if (!(player2 instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)player2;
        CompoundTag data = player.getPersistentData();
        if (data.hasUUID(SHARPEN_SOUL_SEVER_BOSS_KEY)) {
            RediosEntity redios;
            bossId = data.getUUID(SHARPEN_SOUL_SEVER_BOSS_KEY);
            int amp = data.getInt(SHARPEN_SOUL_SEVER_AMP_KEY);
            Entity entity = player.serverLevel().getEntity(bossId);
            if (!(entity instanceof RediosEntity) || !(redios = (RediosEntity)entity).isAlive()
                    || redios.isSoulSeverRetiredInPhase1()) {
                CommonEvents.clearSharpenSoulSever((ServerPlayer)player);
            } else {
                MobEffectInstance current = player.getEffect(ModEffects.SOUL_SEVER);
                if (current == null || current.getAmplifier() < amp) {
                    player.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, 40, amp, true, true));
                } else if (current.getDuration() < 20) {
                    player.addEffect(new MobEffectInstance(ModEffects.SOUL_SEVER, 40, current.getAmplifier(), true, true));
                }
            }
        }
        if (data.hasUUID(ROOTLESS_BUFF_BLOCK_BOSS_KEY)) {
            RediosEntity redios;
            bossId = data.getUUID(ROOTLESS_BUFF_BLOCK_BOSS_KEY);
            Entity entity = player.serverLevel().getEntity(bossId);
            if (!(entity instanceof RediosEntity && (redios = (RediosEntity)entity).isAlive() && redios.isRootlessPureActiveNow())) {
                CommonEvents.clearRootlessBuffBlock((ServerPlayer)player);
            } else {
                for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
                    if (!((MobEffect)effect.getEffect().value()).isBeneficial()) continue;
                    player.removeEffect(effect.getEffect());
                }
            }
        }
        if (data.hasUUID(MIRROR_FACE_ATTACK_BOSS_KEY)) {
            RediosEntity redios;
            bossId = data.getUUID(MIRROR_FACE_ATTACK_BOSS_KEY);
            long value = data.getLong(MIRROR_FACE_ATTACK_VALUE_KEY);
            Entity entity = player.serverLevel().getEntity(bossId);
            if (!(entity instanceof RediosEntity && (redios = (RediosEntity)entity).isAlive() && redios.isMirrorFaceActiveNow())) {
                CommonEvents.clearMirrorFaceAttackBoost((ServerPlayer)player);
            } else {
                CommonEvents.applyMirrorFaceAttackBoost((ServerPlayer)player, value);
            }
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(PHASE2_CHOICE_BOSS_KEY)) {
            return;
        }
        String text = event.getRawText().trim();
        Boolean choice = CommonEvents.parsePhase2Choice(text);
        if (choice == null) {
            return;
        }
        UUID bossId = data.getUUID(PHASE2_CHOICE_BOSS_KEY);
        Entity entity = player.serverLevel().getEntity(bossId);
        if (entity instanceof RediosEntity) {
            RediosEntity redios = (RediosEntity)entity;
            redios.handlePhase2Choice(player, choice);
        }
        CommonEvents.clearPhase2ChoicePending(player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }
        RediosBattleData.get(server.overworld()).tickServer(server);
        // 拔刀剑全局兜底清扫：每 20 tick（1 秒）跨维度清扫一次危险暴击剑气（2026-09-10 实测回归修复：
        // 本批把它从 20 改成 100 = 最长 5 秒空窗，而本仓库 RediosEntity:669-672 的注释正是反证——
        // 实测崩服 EntityDrive.onHitEntity:303 nextInt(负) 就落在这个空窗里。危险剑气是崩服护栏，
        // 不该为了省一次实体扫描把窗口放大 5 倍）。
        if (++bladeGlobalSweepTick >= 20) {
            bladeGlobalSweepTick = 0;
            IntegrationContract.globalSanitizeBladeDrives(server);
        }
    }

    public static void markPhase2ChoicePending(ServerPlayer player, UUID bossId) {
        player.getPersistentData().putUUID(PHASE2_CHOICE_BOSS_KEY, bossId);
    }

    public static void clearPhase2ChoicePending(ServerPlayer player) {
        player.getPersistentData().remove(PHASE2_CHOICE_BOSS_KEY);
    }

    public static void markSharpenSoulSever(ServerPlayer player, UUID bossId, int amplifier) {
        CompoundTag data = player.getPersistentData();
        data.putUUID(SHARPEN_SOUL_SEVER_BOSS_KEY, bossId);
        data.putInt(SHARPEN_SOUL_SEVER_AMP_KEY, amplifier);
    }

    public static boolean isSharpenSoulSeverMarked(ServerPlayer player, UUID bossId) {
        CompoundTag data = player.getPersistentData();
        return data.hasUUID(SHARPEN_SOUL_SEVER_BOSS_KEY) && bossId.equals(data.getUUID(SHARPEN_SOUL_SEVER_BOSS_KEY));
    }

    public static void clearSharpenSoulSever(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(SHARPEN_SOUL_SEVER_BOSS_KEY);
        data.remove(SHARPEN_SOUL_SEVER_AMP_KEY);
        player.removeEffect(ModEffects.SOUL_SEVER);
        data.remove(SOUL_SEVER_BONUS_KEY);
        data.remove(SOUL_SEVER_APPLYING_KEY);
    }

    public static void markRootlessBuffBlock(ServerPlayer player, UUID bossId) {
        player.getPersistentData().putUUID(ROOTLESS_BUFF_BLOCK_BOSS_KEY, bossId);
    }

    public static void clearRootlessBuffBlock(ServerPlayer player) {
        player.getPersistentData().remove(ROOTLESS_BUFF_BLOCK_BOSS_KEY);
    }

    public static void markMirrorFaceAttackBoost(ServerPlayer player, UUID bossId, long value) {
        CompoundTag data = player.getPersistentData();
        data.putUUID(MIRROR_FACE_ATTACK_BOSS_KEY, bossId);
        data.putLong(MIRROR_FACE_ATTACK_VALUE_KEY, value);
        CommonEvents.applyMirrorFaceAttackBoost(player, value);
    }

    public static void clearMirrorFaceAttackBoost(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(MIRROR_FACE_ATTACK_BOSS_KEY);
        data.remove(MIRROR_FACE_ATTACK_VALUE_KEY);
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.removeModifier(MIRROR_FACE_ATTACK_DAMAGE_ID);
        }
    }

    private static void applyMirrorFaceAttackBoost(ServerPlayer player, long value) {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(MIRROR_FACE_ATTACK_DAMAGE_ID);
        double amount = CommonEvents.clampMirrorFaceValue(value);
        if (amount == 0.0) {
            return;
        }
        attribute.addTransientModifier(new AttributeModifier(MIRROR_FACE_ATTACK_DAMAGE_ID, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static double clampMirrorFaceValue(long value) {
        long cap = 1000000000L;
        if (value > cap) {
            return cap;
        }
        if (value < -cap) {
            return -cap;
        }
        return value;
    }

    private static Boolean parsePhase2Choice(String raw) {
        String s = raw.strip();
        String lower = s.toLowerCase(Locale.ROOT);
        if (RediosRules.phase2VoteYesTokens().contains(lower)) {
            return true;
        }
        if (RediosRules.phase2VoteNoTokens().contains(lower)) {
            return false;
        }
        return null;
    }

    private static RediosEntity findActiveRedios(ServerLevel level) {
        for (Entity e : level.getEntities().getAll()) {
            if (e instanceof RediosEntity redios && redios.isAlive() && !redios.isRemoved()) {
                return redios;
            }
        }
        return null;
    }

    // PH1: Track player healing to increment Soul Sever Y
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float amount = event.getAmount();
        if (amount <= 0.0f) return;
        RediosEntity redios = CommonEvents.findActiveRedios(player.serverLevel());
        if (redios != null) {
            redios.addSoulSeverY(Math.max(0L, (long) Math.ceil(amount)));
        }
    }

    // PH1: Track anvil repair to increment Soul Sever Y (repaired / 10)
    // (NeoForge 1.21 has no mending event; anvil repair is the only hook)
    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int repaired = event.getLeft().getDamageValue() - event.getOutput().getDamageValue();
        if (repaired <= 0) return;
        RediosEntity redios = CommonEvents.findActiveRedios(player.serverLevel());
        if (redios != null) {
            redios.addSoulSeverY(Math.max(0L, repaired / 10L));
        }
    }

    private CommonEvents() {
    }
}
