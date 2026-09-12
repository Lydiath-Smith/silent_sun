/*
 * Decompiled with CFR 0.152.
 */
package com.lydiath.silent_sun.rules;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lydiath.silent_sun.rules.RediosRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RediosRulesReloadListener
extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation RULES_ID = ResourceLocation.fromNamespaceAndPath("silent_sun", "redios_rules");
    private static final Logger LOG = LoggerFactory.getLogger("SilentSun:Rules");

    /**
     * 代码真正读取的配置键全集（2026-09-11 代码审计 G03 #2 修复）。
     * <p>
     * 背景：本文件的 60 余处读取都是「取不到 / 类型不符 → 静默回落默认值」，管理员把键名
     * 拼错时现象是「改了配置完全无效、日志无任何提示」。逐处打日志会刷屏，故改为 reload
     * 结束时<b>一次性汇总</b>：把 json 里出现、但代码从不读取的键报出来（几乎必然是拼写错误）。
     * <p>
     * 维护约定：新增 / 改名配置键后必须同步本清单，否则会误报「未知键」。
     * 可用下面这条命令重新提取（PowerShell，仓库根目录执行）：
     * <pre>
     * $t = [IO.File]::ReadAllText('src\decompiled\java\com\lydiath\silent_sun\rules\RediosRulesReloadListener.java')
     * [regex]::Matches($t, 'root\.(?:get|has|getAsJsonArray|getAsJsonObject)\(\s*"([a-z0-9_]+)"|tryParseString\(\s*root\s*,\s*"([a-z0-9_]+)"') |
     *     ForEach-Object { if ($_.Groups[1].Success) { $_.Groups[1].Value } else { $_.Groups[2].Value } } |
     *     Sort-Object -Unique
     * </pre>
     * 注：{@code vote_timeout_seconds} 等键在当前 json 中未出现，但代码仍读取以兼容旧配置，故一并列入。
     */
    private static final Set<String> KNOWN_KEYS = Set.of(
        "adaptive_block_cooldown_ticks", "adaptive_block_damage_reduction", "adaptive_block_duration_ticks", "adaptive_block_trigger_hits_per_second",
        "battle_expel_timeout_seconds", "battle_radius_blocks", "black_sun_defeat_ratio", "boss_missing_vision_action",
        "boss_missing_vision_dot_threshold", "boss_missing_vision_enabled", "boss_missing_vision_ticks", "boss_sa_excluded_namespaces",
        "boss_sa_excluded_sa_ids", "boss_sa_whitelist_namespaces", "chaos_ruin_incoming_absolute_enabled", "colorless_reflect_ratio",
        "colorless_weakness_amplifier", "colorless_weakness_duration_ticks", "damage_source_debug",
        "damage_source_debug_cooldown_ticks", "damage_source_debug_only_phase2", "damage_source_debug_only_when_expelled", "height_flight_diff_blocks",
        "height_flight_enabled", "height_flight_vertical_speed", "lag_protection_enabled", "latency_threshold_ms",
        "locate_boss_distance_blocks", "locate_boss_enabled", "locate_boss_notify_interval_ticks", "phase2_vote_no_tokens",
        "phase2_vote_yes_tokens", "push_away_distance", "push_away_range", "push_away_strength",
        "redios_battle_music_enabled", "redios_battle_music_outro_enabled", "redios_battle_music_phase1_intro_ticks", "redios_battle_music_phase1_loop_ticks",
        "redios_battle_music_phase2_intro_ticks", "redios_battle_music_phase2_loop_ticks", "redios_battle_music_volume", "redios_book_author",
        "redios_note_phase1_win_phase2_lose", "redios_outcome_text_phase1_win_only_file", "redios_outcome_text_phase1_win_phase2_lose_file",
        "redios_outcome_text_phase2_win_file", "restore_nbt", "restored_blocks_whitelist",
        "skip_vote", "twilight_moment_apply_effect", "twilight_moment_debug_messages", "twilight_moment_expel_enabled",
        "twilight_moment_mode", "twilight_moment_notify_cooldown_ticks", "twilight_moment_punishment", "twilight_moment_satisfy_effects",
        "twilight_moment_timed_grace_ticks", "uncontrolled_sprint_aoe_dodge_chance", "uncontrolled_sprint_extra_cooldown_ticks", "uncontrolled_sprint_extra_damage_ratio",
        "uncontrolled_sprint_extra_hits", "void_all_things_darkness_duration_ticks", "void_all_things_teleport_cooldown_ticks", "vote_tie_as_yes",
        "vote_timeout_seconds", "wall_attack_notify_cooldown_ticks", "wall_attack_trace_particles", "weapon_weakpoint_armor_pierce",
        "weapon_weakpoint_cooldown_ticks", "weapon_weakpoint_damage_multiplier", "weapon_weakpoint_enabled", "weapon_weakpoint_fixed_cooldown",
        "weapon_weakpoint_slow_ticks"
    );

    /**
     * reload 结束时汇总「json 里有、但代码从不读取」的键，只打一条日志。
     * <p>
     * 2026-09-11（代码审计 G03 #2）：用于让「键名拼错 → 配置静默无效」变得可观测。
     */
    private static void warnUnknownKeys(JsonObject root) {
        Set<String> unknown = new TreeSet<>(root.keySet());
        unknown.removeAll(KNOWN_KEYS);
        if (!unknown.isEmpty()) {
            LOG.warn("silent_sun/redios_rules.json 含 {} 个代码不认识的键（已忽略；多半是拼写错误，正确键名见 RediosRulesReloadListener.KNOWN_KEYS）：{}",
                    unknown.size(), unknown);
        }
    }

    public RediosRulesReloadListener() {
        super(GSON, "silent_sun");
    }

    protected void apply(Map<ResourceLocation, JsonElement> json, ResourceManager resourceManager, ProfilerFiller profiler) {
        ResourceLocation parsed;
        JsonElement element = json.get(RULES_ID);
        if (element == null || !element.isJsonObject()) {
            // N3: 配置缺失或格式非对象时回退默认值——显式告警，避免管理员无从得知
            LOG.warn("silent_sun/redios_rules.json 缺失或不是 JSON 对象，已回退全部默认配置。当前加载的 rules 键: {}", json.keySet());
            RediosRules.setTwilightMomentMode(RediosRules.TwilightMomentMode.REFRESH);
            RediosRules.setTwilightMomentPunishment(RediosRules.TwilightMomentPunishmentMode.VISUAL);
            RediosRules.setTwilightMomentDebugMessages(false);
            RediosRules.setTwilightMomentNotifyCooldownTicks(100);
            RediosRules.setTwilightMomentTimedGraceTicks(10);
            RediosRules.setTwilightMomentApplyEffectId(ResourceLocation.fromNamespaceAndPath("minecraft", "darkness"));
            RediosRules.setTwilightMomentSatisfyEffectIds(List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "darkness")));
            RediosRules.setDamageSourceDebug(false);
            RediosRules.setDamageSourceDebugCooldownTicks(40);
            RediosRules.setDamageSourceDebugOnlyPhase2(true);
            RediosRules.setDamageSourceDebugOnlyWhenExpelled(true);
            RediosRules.setBlackSunDefeatRatio(0.5);
            RediosRules.setColorlessReflectRatio(0.5);
            RediosRules.setColorlessWeaknessDurationTicks(200);
            RediosRules.setColorlessWeaknessAmplifier(0);
            RediosRules.setVoidAllThingsDarknessDurationTicks(40);
            RediosRules.setVoidAllThingsTeleportCooldownTicks(40);
            RediosRules.setUncontrolledSprintExtraHits(1);
            RediosRules.setUncontrolledSprintExtraDamageRatio(0.35);
            RediosRules.setUncontrolledSprintExtraCooldownTicks(4);
            RediosRules.setUncontrolledSprintAoEDodgeChance(0.25);
            RediosRules.setChaosRuinIncomingAbsoluteEnabled(true);
            // N3: 缺失分支需与 RediosRules 静态默认保持一致（此前遗漏视野缺失一组）
            RediosRules.setBossMissingVisionEnabled(true);
            RediosRules.setBossMissingVisionTicks(100);
            RediosRules.setBossMissingVisionDotThreshold(0.45);
            RediosRules.setBossMissingVisionAction(RediosRules.BossMissingVisionAction.WAYPOINT);
            RediosRules.setWallAttackTraceParticles(true);
            RediosRules.setWallAttackNotifyCooldownTicks(60);
            RediosRules.setLocateBossEnabled(true);
            RediosRules.setLocateBossDistanceBlocks(35);
            RediosRules.setLocateBossNotifyIntervalTicks(200);
            RediosRules.setHeightFlightEnabled(true);
            RediosRules.setHeightFlightDiffBlocks(6);
            RediosRules.setHeightFlightVerticalSpeed(0.5);
            RediosRules.setPhase2VoteRequired(true);
            RediosRules.setPhase2VoteYesTokens(null);
            RediosRules.setPhase2VoteNoTokens(null);
            RediosRules.setRediosBookAuthor(null);
            // 2026-09-12（审计清理 G17 #5）：原 setRediosDefeatBookTitle(null) / setRediosVictoryBookTitle(null)
            // 两行已删除（配置键 redios_defeat_book_title / redios_victory_book_title 已废弃并移出 KNOWN_KEYS）。
            RediosRules.setRediosNotePhase1WinPhase2Lose(null);
            RediosRules.setRediosOutcomeTextPhase1WinOnlyFile(null);
            RediosRules.setRediosOutcomeTextPhase1WinPhase2LoseFile(null);
            RediosRules.setRediosOutcomeTextPhase2WinFile(null);
            RediosRules.setRediosBattleMusicEnabled(true);
            RediosRules.setRediosBattleMusicVolume(1.0f);
            RediosRules.setRediosBattleMusicPhase1IntroTicks(873);
            RediosRules.setRediosBattleMusicPhase1LoopTicks(3245);
            RediosRules.setRediosBattleMusicPhase2IntroTicks(482);
            RediosRules.setRediosBattleMusicPhase2LoopTicks(3171);
            RediosRules.setRediosBattleMusicOutroEnabled(true);
            RediosRules.setVoteTimeoutSeconds(30);
            RediosRules.setVoteTieAsYes(false);
            // TODO(审计清理 G02 #3)：本套默认值在字段初值 / setter null 回退 / reload 重置块三处各写一遍 —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
            RediosRules.setAdaptiveBlockTriggerHitsPerSecond(6);
            RediosRules.setAdaptiveBlockDurationTicks(20);
            RediosRules.setAdaptiveBlockDamageReduction(1.0);
            RediosRules.setAdaptiveBlockCooldownTicks(40);
            RediosRules.setBattleRadiusBlocks(72);
            RediosRules.setBattleExpelTimeoutSeconds(60);
            RediosRules.setPushAwayDistance(17.0);
            RediosRules.setPushAwayStrength(2.0);
            RediosRules.setPushAwayRange(10.0);
            RediosRules.setRestoredBlocksWhitelist(null);
            // 2026-09-12（SA 名单热配置化）：**传 null ⇒ 交给静态配置回退**，不写死代码默认值。
            // 理由：本设计里「静态配置是回退层」，整份 json 缺失时更应回退到作者的 toml 自定义值
            //（toml 是独立文件，不因 json 丢失而失效）。若这里写死 DEFAULT_BOSS_SA_*，
            // 「作者改过 toml + json 丢失」这个组合会静默丢弃他的配置。
            // 键级缺失同样传 null —— 两条路径统一为同一语义，三态表随之简化为「非 null 用它，null 回退」。
            RediosRules.setBossSaWhitelistNamespaces(null);
            RediosRules.setBossSaExcludedNamespaces(null);
            RediosRules.setBossSaExcludedSaIds(null);
            RediosRules.setRestoreNbt(true);
            RediosRules.setLatencyThresholdMs(150);
            RediosRules.setLagProtectionEnabled(true);
            RediosRules.setWeaponWeakpointEnabled(true);
            RediosRules.setWeaponWeakpointSlowTicks(100);
            RediosRules.setWeaponWeakpointCooldownTicks(300);
            RediosRules.setWeaponWeakpointFixedCooldown(20);
            RediosRules.setWeaponWeakpointDamageMultiplier(1.5);
            RediosRules.setWeaponWeakpointArmorPierce(0.5);
            return;
        }
        JsonObject root = element.getAsJsonObject();
        // 2026-09-11（G03）：改用 tryParseString —— 原裸调 getAsString()，键值非字符串即异常逃出 apply()
        // TODO(审计清理 G03 #5)：71 个默认值在三处各抄一遍（注释自证已漂移过一次） —— 详见 docs\审计剩余交接清单-2026-09-11.md §三
        String rawMode = tryParseString(root, "twilight_moment_mode");
        RediosRules.TwilightMomentPunishmentMode punishmentMode = RediosRules.TwilightMomentPunishmentMode.VISUAL;
        if (root.has("twilight_moment_punishment")) {
            // E1: 显式二选一配置："expel"=逐出 / "visual"=视觉改变（默认）
            try {
                String raw = root.get("twilight_moment_punishment").getAsString();
                punishmentMode = "expel".equalsIgnoreCase(raw) ? RediosRules.TwilightMomentPunishmentMode.EXPEL : RediosRules.TwilightMomentPunishmentMode.VISUAL;
            }
            catch (RuntimeException e) {
                punishmentMode = RediosRules.TwilightMomentPunishmentMode.VISUAL;
            }
        } else if (root.has("twilight_moment_expel_enabled")) {
            // E1: 兼容旧布尔键（true=逐出 / false=视觉改变）
            try {
                punishmentMode = root.get("twilight_moment_expel_enabled").getAsBoolean() ? RediosRules.TwilightMomentPunishmentMode.EXPEL : RediosRules.TwilightMomentPunishmentMode.VISUAL;
            }
            catch (RuntimeException e) {
                punishmentMode = RediosRules.TwilightMomentPunishmentMode.VISUAL;
            }
        }
        boolean twilightDebug = false;
        if (root.has("twilight_moment_debug_messages")) {
            twilightDebug = root.get("twilight_moment_debug_messages").getAsBoolean();
        }
        int notifyCooldownTicks = 100;
        if (root.has("twilight_moment_notify_cooldown_ticks")) {
            try {
                notifyCooldownTicks = root.get("twilight_moment_notify_cooldown_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                notifyCooldownTicks = 100;
            }
        }
        int timedGraceTicks = 10;
        if (root.has("twilight_moment_timed_grace_ticks")) {
            try {
                timedGraceTicks = root.get("twilight_moment_timed_grace_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                timedGraceTicks = 10;
            }
        }
        boolean damageDebug = false;
        if (root.has("damage_source_debug")) {
            try {
                damageDebug = root.get("damage_source_debug").getAsBoolean();
            }
            catch (RuntimeException e) {
                damageDebug = false;
            }
        }
        int damageDebugCooldownTicks = 40;
        if (root.has("damage_source_debug_cooldown_ticks")) {
            try {
                damageDebugCooldownTicks = root.get("damage_source_debug_cooldown_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                damageDebugCooldownTicks = 40;
            }
        }
        boolean damageDebugOnlyPhase2 = true;
        if (root.has("damage_source_debug_only_phase2")) {
            try {
                damageDebugOnlyPhase2 = root.get("damage_source_debug_only_phase2").getAsBoolean();
            }
            catch (RuntimeException e) {
                damageDebugOnlyPhase2 = true;
            }
        }
        boolean damageDebugOnlyWhenExpelled = true;
        if (root.has("damage_source_debug_only_when_expelled")) {
            try {
                damageDebugOnlyWhenExpelled = root.get("damage_source_debug_only_when_expelled").getAsBoolean();
            }
            catch (RuntimeException e) {
                damageDebugOnlyWhenExpelled = true;
            }
        }
        double blackSunDefeatRatio = 0.5;
        if (root.has("black_sun_defeat_ratio")) {
            try {
                blackSunDefeatRatio = root.get("black_sun_defeat_ratio").getAsDouble();
            }
            catch (RuntimeException e) {
                blackSunDefeatRatio = 0.5;
            }
        }
        double colorlessReflectRatio = 0.5;
        if (root.has("colorless_reflect_ratio")) {
            try {
                colorlessReflectRatio = root.get("colorless_reflect_ratio").getAsDouble();
            }
            catch (RuntimeException e) {
                colorlessReflectRatio = 0.5;
            }
        }
        int colorlessWeaknessDurationTicks = 200;
        if (root.has("colorless_weakness_duration_ticks")) {
            try {
                colorlessWeaknessDurationTicks = root.get("colorless_weakness_duration_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                colorlessWeaknessDurationTicks = 200;
            }
        }
        int colorlessWeaknessAmplifier = 0;
        if (root.has("colorless_weakness_amplifier")) {
            try {
                colorlessWeaknessAmplifier = root.get("colorless_weakness_amplifier").getAsInt();
            }
            catch (RuntimeException e) {
                colorlessWeaknessAmplifier = 0;
            }
        }
        int voidAllThingsDarknessDurationTicks = 40;
        if (root.has("void_all_things_darkness_duration_ticks")) {
            try {
                voidAllThingsDarknessDurationTicks = root.get("void_all_things_darkness_duration_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                voidAllThingsDarknessDurationTicks = 40;
            }
        }
        int voidAllThingsTeleportCooldownTicks = 40;
        if (root.has("void_all_things_teleport_cooldown_ticks")) {
            try {
                voidAllThingsTeleportCooldownTicks = root.get("void_all_things_teleport_cooldown_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                voidAllThingsTeleportCooldownTicks = 40;
            }
        }
        int uncontrolledSprintExtraHits = 1;
        if (root.has("uncontrolled_sprint_extra_hits")) {
            try {
                uncontrolledSprintExtraHits = root.get("uncontrolled_sprint_extra_hits").getAsInt();
            }
            catch (RuntimeException e) {
                uncontrolledSprintExtraHits = 1;
            }
        }
        double uncontrolledSprintExtraDamageRatio = 0.35;
        if (root.has("uncontrolled_sprint_extra_damage_ratio")) {
            try {
                uncontrolledSprintExtraDamageRatio = root.get("uncontrolled_sprint_extra_damage_ratio").getAsDouble();
            }
            catch (RuntimeException e) {
                uncontrolledSprintExtraDamageRatio = 0.35;
            }
        }
        int uncontrolledSprintExtraCooldownTicks = 4;
        if (root.has("uncontrolled_sprint_extra_cooldown_ticks")) {
            try {
                uncontrolledSprintExtraCooldownTicks = root.get("uncontrolled_sprint_extra_cooldown_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                uncontrolledSprintExtraCooldownTicks = 4;
            }
        }
        double uncontrolledSprintAoEDodgeChance = 0.25;
        if (root.has("uncontrolled_sprint_aoe_dodge_chance")) {
            try {
                uncontrolledSprintAoEDodgeChance = root.get("uncontrolled_sprint_aoe_dodge_chance").getAsDouble();
            }
            catch (RuntimeException e) {
                uncontrolledSprintAoEDodgeChance = 0.25;
            }
        }
        boolean chaosRuinIncomingAbsoluteEnabled = true;
        if (root.has("chaos_ruin_incoming_absolute_enabled")) {
            try {
                chaosRuinIncomingAbsoluteEnabled = root.get("chaos_ruin_incoming_absolute_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                chaosRuinIncomingAbsoluteEnabled = true;
            }
        }
        boolean wallAttackTraceParticles = true;
        if (root.has("wall_attack_trace_particles")) {
            try {
                wallAttackTraceParticles = root.get("wall_attack_trace_particles").getAsBoolean();
            }
            catch (RuntimeException e) {
                wallAttackTraceParticles = true;
            }
        }
        int wallAttackNotifyCooldownTicks = 60;
        if (root.has("wall_attack_notify_cooldown_ticks")) {
            try {
                wallAttackNotifyCooldownTicks = root.get("wall_attack_notify_cooldown_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                wallAttackNotifyCooldownTicks = 60;
            }
        }
        boolean locateBossEnabled = true;
        if (root.has("locate_boss_enabled")) {
            try {
                locateBossEnabled = root.get("locate_boss_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                locateBossEnabled = true;
            }
        }
        int locateBossDistanceBlocks = 35;
        if (root.has("locate_boss_distance_blocks")) {
            try {
                locateBossDistanceBlocks = root.get("locate_boss_distance_blocks").getAsInt();
            }
            catch (RuntimeException e) {
                locateBossDistanceBlocks = 35;
            }
        }
        int locateBossNotifyIntervalTicks = 200;
        if (root.has("locate_boss_notify_interval_ticks")) {
            try {
                locateBossNotifyIntervalTicks = root.get("locate_boss_notify_interval_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                locateBossNotifyIntervalTicks = 200;
            }
        }
        boolean heightFlightEnabled = true;
        if (root.has("height_flight_enabled")) {
            try {
                heightFlightEnabled = root.get("height_flight_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                heightFlightEnabled = true;
            }
        }
        int heightFlightDiffBlocks = 6;
        if (root.has("height_flight_diff_blocks")) {
            try {
                heightFlightDiffBlocks = root.get("height_flight_diff_blocks").getAsInt();
            }
            catch (RuntimeException e) {
                heightFlightDiffBlocks = 6;
            }
        }
        double heightFlightVerticalSpeed = 0.5;
        if (root.has("height_flight_vertical_speed")) {
            try {
                heightFlightVerticalSpeed = root.get("height_flight_vertical_speed").getAsDouble();
            }
            catch (RuntimeException e) {
                heightFlightVerticalSpeed = 0.5;
            }
        }
        ResourceLocation twilightApplyId = ResourceLocation.fromNamespaceAndPath("minecraft", "darkness");
        String twilightApplyRaw = tryParseString(root, "twilight_moment_apply_effect");
        if (twilightApplyRaw != null && (parsed = RediosRulesReloadListener.tryParseId(twilightApplyRaw)) != null) {
            twilightApplyId = parsed;
        }
        ArrayList<ResourceLocation> satisfyIds = null;
        if (root.has("twilight_moment_satisfy_effects") && root.get("twilight_moment_satisfy_effects").isJsonArray()) {
            ArrayList<ResourceLocation> list = new ArrayList<ResourceLocation>();
            for (JsonElement e : root.getAsJsonArray("twilight_moment_satisfy_effects")) {
                ResourceLocation parsed2;
                if (!e.isJsonPrimitive() || (parsed2 = RediosRulesReloadListener.tryParseId(e.getAsString())) == null) continue;
                list.add(parsed2);
            }
            if (!list.isEmpty()) {
                satisfyIds = list;
            }
        }
        boolean skipVote = false;
        if (root.has("skip_vote")) {
            try {
                skipVote = root.get("skip_vote").getAsBoolean();
            }
            catch (RuntimeException e) {
                skipVote = false;
            }
        }
        boolean phase2VoteRequired = !skipVote;
        boolean bossMissingVisionEnabled = true;
        if (root.has("boss_missing_vision_enabled")) {
            try {
                bossMissingVisionEnabled = root.get("boss_missing_vision_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                bossMissingVisionEnabled = true;
            }
        }
        int bossMissingVisionTicks = 100;
        if (root.has("boss_missing_vision_ticks")) {
            try {
                bossMissingVisionTicks = root.get("boss_missing_vision_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                bossMissingVisionTicks = 100;
            }
        }
        double bossMissingVisionDotThreshold = 0.45;
        if (root.has("boss_missing_vision_dot_threshold")) {
            try {
                bossMissingVisionDotThreshold = root.get("boss_missing_vision_dot_threshold").getAsDouble();
            }
            catch (RuntimeException e) {
                bossMissingVisionDotThreshold = 0.45;
            }
        }
        String bossMissingVisionActionRaw = null;
        if (root.has("boss_missing_vision_action")) {
            try {
                bossMissingVisionActionRaw = root.get("boss_missing_vision_action").getAsString();
            }
            catch (RuntimeException e) {
                bossMissingVisionActionRaw = null;
            }
        }
        ArrayList<String> phase2VoteYesTokens = null;
        if (root.has("phase2_vote_yes_tokens") && root.get("phase2_vote_yes_tokens").isJsonArray()) {
            ArrayList<String> list = new ArrayList<String>();
            for (JsonElement e : root.getAsJsonArray("phase2_vote_yes_tokens")) {
                Object s;
                if (!e.isJsonPrimitive() || (s = e.getAsString()) == null || ((String)s).isBlank()) continue;
                list.add((String)s);
            }
            if (!list.isEmpty()) {
                phase2VoteYesTokens = list;
            }
        }
        ArrayList<String> phase2VoteNoTokens = null;
        if (root.has("phase2_vote_no_tokens") && root.get("phase2_vote_no_tokens").isJsonArray()) {
            ArrayList<String> list = new ArrayList<String>();
            for (JsonElement e : root.getAsJsonArray("phase2_vote_no_tokens")) {
                String s;
                if (!e.isJsonPrimitive() || (s = e.getAsString()) == null || s.isBlank()) continue;
                list.add(s);
            }
            if (!list.isEmpty()) {
                phase2VoteNoTokens = list;
            }
        }
        String rediosBookAuthor = null;
        if (root.has("redios_book_author")) {
            try {
                rediosBookAuthor = root.get("redios_book_author").getAsString();
            }
            catch (RuntimeException e) {
                rediosBookAuthor = null;
            }
        }
        // 2026-09-12（审计清理 G17 #5）：原解析块 redios_defeat_book_title / redios_victory_book_title 已删除
        //（对应 RediosRules 字段与访问器已移除；两个死方法 createDefeatBookAndQuill / createVictoryBook 是
        //  它们唯一的消费者）。json 里若仍留有这两个键，会被 KNOWN_KEYS 校验判为未知键并汇总告警（不再生效）。
        String rediosNotePhase1WinPhase2Lose = null;
        if (root.has("redios_note_phase1_win_phase2_lose")) {
            try {
                rediosNotePhase1WinPhase2Lose = root.get("redios_note_phase1_win_phase2_lose").getAsString();
            }
            catch (RuntimeException e) {
                rediosNotePhase1WinPhase2Lose = null;
            }
        }
        ResourceLocation rediosOutcomeTextPhase1WinOnlyFile = null;
        String outcomePhase1WinOnlyRaw = tryParseString(root, "redios_outcome_text_phase1_win_only_file");
        if (outcomePhase1WinOnlyRaw != null && (parsed = RediosRulesReloadListener.tryParseId(outcomePhase1WinOnlyRaw)) != null) {
            rediosOutcomeTextPhase1WinOnlyFile = parsed;
        }
        ResourceLocation rediosOutcomeTextPhase1WinPhase2LoseFile = null;
        String outcomeP1WinP2LoseRaw = tryParseString(root, "redios_outcome_text_phase1_win_phase2_lose_file");
        if (outcomeP1WinP2LoseRaw != null && (parsed = RediosRulesReloadListener.tryParseId(outcomeP1WinP2LoseRaw)) != null) {
            rediosOutcomeTextPhase1WinPhase2LoseFile = parsed;
        }
        ResourceLocation rediosOutcomeTextPhase2WinFile = null;
        String outcomePhase2WinRaw = tryParseString(root, "redios_outcome_text_phase2_win_file");
        if (outcomePhase2WinRaw != null && (parsed = RediosRulesReloadListener.tryParseId(outcomePhase2WinRaw)) != null) {
            rediosOutcomeTextPhase2WinFile = parsed;
        }
        boolean rediosBattleMusicEnabled = true;
        if (root.has("redios_battle_music_enabled")) {
            try {
                rediosBattleMusicEnabled = root.get("redios_battle_music_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                rediosBattleMusicEnabled = true;
            }
        }
        float rediosBattleMusicVolume = 1.0f;
        if (root.has("redios_battle_music_volume")) {
            try {
                rediosBattleMusicVolume = root.get("redios_battle_music_volume").getAsFloat();
            }
            catch (RuntimeException e) {
                rediosBattleMusicVolume = 1.0f;
            }
        }
        // A-1（2026-09-11）：战斗音乐三段。*_ticks 表示各段音频长度——服务端据此切换
        // （intro→loop）与重发（流式 ogg 无法自动循环）。**按阶段分组**：P1/P2 的 intro
        // 素材长度差异很大（实测 873 vs 482 tick），单组配置会让短的那段留下静音空档。
        int rediosBattleMusicPhase1IntroTicks = 873;
        if (root.has("redios_battle_music_phase1_intro_ticks")) {
            try {
                rediosBattleMusicPhase1IntroTicks = root.get("redios_battle_music_phase1_intro_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                rediosBattleMusicPhase1IntroTicks = 873;
            }
        }
        int rediosBattleMusicPhase1LoopTicks = 3245;
        if (root.has("redios_battle_music_phase1_loop_ticks")) {
            try {
                rediosBattleMusicPhase1LoopTicks = root.get("redios_battle_music_phase1_loop_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                rediosBattleMusicPhase1LoopTicks = 3245;
            }
        }
        int rediosBattleMusicPhase2IntroTicks = 482;
        if (root.has("redios_battle_music_phase2_intro_ticks")) {
            try {
                rediosBattleMusicPhase2IntroTicks = root.get("redios_battle_music_phase2_intro_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                rediosBattleMusicPhase2IntroTicks = 482;
            }
        }
        int rediosBattleMusicPhase2LoopTicks = 3171;
        if (root.has("redios_battle_music_phase2_loop_ticks")) {
            try {
                rediosBattleMusicPhase2LoopTicks = root.get("redios_battle_music_phase2_loop_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                rediosBattleMusicPhase2LoopTicks = 3171;
            }
        }
        boolean rediosBattleMusicOutroEnabled = true;
        if (root.has("redios_battle_music_outro_enabled")) {
            try {
                rediosBattleMusicOutroEnabled = root.get("redios_battle_music_outro_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                rediosBattleMusicOutroEnabled = true;
            }
        }
        // 死配置（M5）：vote_timeout_seconds / vote_tie_as_yes 无任何消费方——
        // 投票超时/平局已硬编码 30s/否决（设计裁决）。保留解析仅为兼容旧 json 里仍有这两个键，
        // 值被读入 RediosRules 但无调用点。新 json 已移除这两个键。
        int voteTimeoutSeconds = 30;
        if (root.has("vote_timeout_seconds")) {
            try {
                voteTimeoutSeconds = root.get("vote_timeout_seconds").getAsInt();
            }
            catch (RuntimeException e) {
                voteTimeoutSeconds = 30;
            }
        }
        boolean voteTieAsYes = false;
        if (root.has("vote_tie_as_yes")) {
            try {
                voteTieAsYes = root.get("vote_tie_as_yes").getAsBoolean();
            }
            catch (RuntimeException e) {
                voteTieAsYes = false;
            }
        }
        // 2026-09-11（代码审计 G03 #4）：改用 int + getAsInt()。
        // 下游 RediosRules.setAdaptiveBlockTriggerHitsPerSecond(int) 本就是 int 字段，而键名与
        // 随包 JSON 写作 6.0（浮点字面量）暗示可填小数——原 (int) 截断下填 6.5 会静默变成 6，无日志。
        // 改为 int 后类型与语义一致（消费方 WeaponManager:254 按「整数次/秒」算 20/hits）。
        // 兼容性：Gson 的 getAsInt() 对 6.0 同样得 6，行为与原先完全一致。
        int adaptiveBlockTriggerHitsPerSecond = 6;
        if (root.has("adaptive_block_trigger_hits_per_second")) {
            try {
                adaptiveBlockTriggerHitsPerSecond = root.get("adaptive_block_trigger_hits_per_second").getAsInt();
            }
            catch (RuntimeException e) {
                adaptiveBlockTriggerHitsPerSecond = 6;
            }
        }
        int adaptiveBlockDurationTicks = 20;
        if (root.has("adaptive_block_duration_ticks")) {
            try {
                adaptiveBlockDurationTicks = root.get("adaptive_block_duration_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                adaptiveBlockDurationTicks = 20;
            }
        }
        double adaptiveBlockDamageReduction = 1.0;
        if (root.has("adaptive_block_damage_reduction")) {
            try {
                adaptiveBlockDamageReduction = root.get("adaptive_block_damage_reduction").getAsDouble();
            }
            catch (RuntimeException e) {
                adaptiveBlockDamageReduction = 1.0;
            }
        }
        int adaptiveBlockCooldownTicks = 40;
        if (root.has("adaptive_block_cooldown_ticks")) {
            try {
                adaptiveBlockCooldownTicks = root.get("adaptive_block_cooldown_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                adaptiveBlockCooldownTicks = 40;
            }
        }
        int battleRadiusBlocks = 72;
        if (root.has("battle_radius_blocks")) {
            try {
                battleRadiusBlocks = root.get("battle_radius_blocks").getAsInt();
            }
            catch (RuntimeException e) {
                battleRadiusBlocks = 72;
            }
        }
        int battleExpelTimeoutSeconds = 60;
        if (root.has("battle_expel_timeout_seconds")) {
            try {
                battleExpelTimeoutSeconds = root.get("battle_expel_timeout_seconds").getAsInt();
            }
            catch (RuntimeException e) {
                battleExpelTimeoutSeconds = 60;
            }
        }
        double pushAwayDistance = 17.0;
        if (root.has("push_away_distance")) {
            try {
                pushAwayDistance = root.get("push_away_distance").getAsDouble();
            }
            catch (RuntimeException e) {
                pushAwayDistance = 17.0;
            }
        }
        double pushAwayStrength = 2.0;
        if (root.has("push_away_strength")) {
            try {
                pushAwayStrength = root.get("push_away_strength").getAsDouble();
            }
            catch (RuntimeException e) {
                pushAwayStrength = 2.0;
            }
        }
        double pushAwayRange = 10.0;
        if (root.has("push_away_range")) {
            try {
                pushAwayRange = root.get("push_away_range").getAsDouble();
            }
            catch (RuntimeException e) {
                pushAwayRange = 10.0;
            }
        }
        ArrayList<String> restoredBlocksWhitelist = null;
        if (root.has("restored_blocks_whitelist") && root.get("restored_blocks_whitelist").isJsonArray()) {
            ArrayList<String> list = new ArrayList<String>();
            for (JsonElement e : root.getAsJsonArray("restored_blocks_whitelist")) {
                ResourceLocation parsedWhitelistId;
                if (!e.isJsonPrimitive() || (parsedWhitelistId = RediosRulesReloadListener.tryParseId(e.getAsString())) == null) {
                    LOG.warn("silent_sun/redios_rules.json 的 restored_blocks_whitelist 条目非法（需 namespace:id 形式，如 minecraft:bedrock），已跳过：{}", e);
                    continue;
                }
                // 2026-09-11（代码审计 G03 #3 修复）：原实现原样入库（list.add(s)）。消费端
                // RediosEntity.isDarkStarSpecialBlock / restoreDarkStarSpecialBlocks 用
                // BuiltInRegistries.BLOCK.getKey(...).toString() 比较，该值**恒为小写且带命名空间**
                // → 配置里写大写（minecraft:Bedrock）或缺命名空间（bedrock）的条目**永不匹配**，
                // 白名单静默失效，被 2.6 暗星爆破摧毁的方块永不恢复（存档内永久摧毁）。
                // 现统一归一化为 ResourceLocation.toString()。
                list.add(parsedWhitelistId.toString());
            }
            if (!list.isEmpty()) {
                restoredBlocksWhitelist = list;
            }
        }
        // 2026-09-12（SA 名单热配置化）：三个 SA 池名单键 —— 数组范式同 restored_blocks_whitelist
        // （has + isJsonArray → 逐元素校验 → 非法项 warn 跳过），见 tryParseStringList。
        // 三态：**键缺失 ⇒ null ⇒ 取值端回退静态配置**；键存在且为 [] ⇒ 空表 ⇒ 显式全禁；有内容 ⇒ 该内容。
        // 注意：键级缺失**不得**在这里填默认值，否则「键存在但为空」与「键缺失」又混成一个语义。
        ArrayList<String> bossSaWhitelistNamespaces = RediosRulesReloadListener.tryParseStringList(
                root, "boss_sa_whitelist_namespaces", "需 namespace，如 slashblade");
        ArrayList<String> bossSaExcludedNamespaces = RediosRulesReloadListener.tryParseStringList(
                root, "boss_sa_excluded_namespaces", "需 namespace，如 tianshaxing");
        ArrayList<String> bossSaExcludedSaIds = RediosRulesReloadListener.tryParseStringList(
                root, "boss_sa_excluded_sa_ids", "需完整 SA id，如 foxextra:thrust");
        boolean restoreNbt = true;
        if (root.has("restore_nbt")) {
            try {
                restoreNbt = root.get("restore_nbt").getAsBoolean();
            }
            catch (RuntimeException e) {
                restoreNbt = true;
            }
        }
        // N2: 低帧率判定用的是延迟阈值(毫秒)，键名更名 latency_threshold_ms；
        // M1: 默认 150ms 与字段一致，避免误判普通网络玩家。
        // 2026-09-11（代码审计 G03 #4）：同上改为 int + getAsInt()——下游 setLatencyThresholdMs(int)
        // 本就是 int，随包 JSON 也写作整数 150，用 double 解析纯属多余的类型假象。
        int latencyThresholdMs = 150;
        if (root.has("latency_threshold_ms")) {
            try {
                latencyThresholdMs = root.get("latency_threshold_ms").getAsInt();
            }
            catch (RuntimeException e) {
                latencyThresholdMs = 150;
            }
        }
        boolean lagProtectionEnabled = true;
        if (root.has("lag_protection_enabled")) {
            try {
                lagProtectionEnabled = root.get("lag_protection_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                lagProtectionEnabled = true;
            }
        }
        boolean weaponWeakpointEnabled = true;
        if (root.has("weapon_weakpoint_enabled")) {
            try {
                weaponWeakpointEnabled = root.get("weapon_weakpoint_enabled").getAsBoolean();
            }
            catch (RuntimeException e) {
                weaponWeakpointEnabled = true;
            }
        }
        int weaponWeakpointSlowTicks = 100;
        if (root.has("weapon_weakpoint_slow_ticks")) {
            try {
                weaponWeakpointSlowTicks = root.get("weapon_weakpoint_slow_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                weaponWeakpointSlowTicks = 100;
            }
        }
        int weaponWeakpointCooldownTicks = 300;
        if (root.has("weapon_weakpoint_cooldown_ticks")) {
            try {
                weaponWeakpointCooldownTicks = root.get("weapon_weakpoint_cooldown_ticks").getAsInt();
            }
            catch (RuntimeException e) {
                weaponWeakpointCooldownTicks = 300;
            }
        }
        int weaponWeakpointFixedCooldown = 20;
        if (root.has("weapon_weakpoint_fixed_cooldown")) {
            try {
                weaponWeakpointFixedCooldown = root.get("weapon_weakpoint_fixed_cooldown").getAsInt();
            }
            catch (RuntimeException e) {
                weaponWeakpointFixedCooldown = 20;
            }
        }
        double weaponWeakpointDamageMultiplier = 1.5;
        if (root.has("weapon_weakpoint_damage_multiplier")) {
            try {
                weaponWeakpointDamageMultiplier = root.get("weapon_weakpoint_damage_multiplier").getAsDouble();
            }
            catch (RuntimeException e) {
                weaponWeakpointDamageMultiplier = 1.5;
            }
        }
        double weaponWeakpointArmorPierce = 0.5;
        if (root.has("weapon_weakpoint_armor_pierce")) {
            try {
                weaponWeakpointArmorPierce = root.get("weapon_weakpoint_armor_pierce").getAsDouble();
            }
            catch (RuntimeException e) {
                weaponWeakpointArmorPierce = 0.5;
            }
        }
        RediosRules.TwilightMomentMode mode = rawMode == null ? RediosRules.TwilightMomentMode.REFRESH : ("timed".equalsIgnoreCase(rawMode) ? RediosRules.TwilightMomentMode.TIMED : RediosRules.TwilightMomentMode.REFRESH);
        RediosRules.setTwilightMomentMode(mode);
        RediosRules.setTwilightMomentPunishment(punishmentMode);
        RediosRules.setTwilightMomentDebugMessages(twilightDebug);
        RediosRules.setTwilightMomentNotifyCooldownTicks(notifyCooldownTicks);
        RediosRules.setTwilightMomentTimedGraceTicks(timedGraceTicks);
        RediosRules.setTwilightMomentApplyEffectId(twilightApplyId);
        RediosRules.setTwilightMomentSatisfyEffectIds(satisfyIds == null ? List.of(twilightApplyId) : satisfyIds);
        RediosRules.setDamageSourceDebug(damageDebug);
        RediosRules.setDamageSourceDebugCooldownTicks(damageDebugCooldownTicks);
        RediosRules.setDamageSourceDebugOnlyPhase2(damageDebugOnlyPhase2);
        RediosRules.setDamageSourceDebugOnlyWhenExpelled(damageDebugOnlyWhenExpelled);
        RediosRules.setBlackSunDefeatRatio(blackSunDefeatRatio);
        RediosRules.setColorlessReflectRatio(colorlessReflectRatio);
        RediosRules.setColorlessWeaknessDurationTicks(colorlessWeaknessDurationTicks);
        RediosRules.setColorlessWeaknessAmplifier(colorlessWeaknessAmplifier);
        RediosRules.setVoidAllThingsDarknessDurationTicks(voidAllThingsDarknessDurationTicks);
        RediosRules.setVoidAllThingsTeleportCooldownTicks(voidAllThingsTeleportCooldownTicks);
        RediosRules.setUncontrolledSprintExtraHits(uncontrolledSprintExtraHits);
        RediosRules.setUncontrolledSprintExtraDamageRatio(uncontrolledSprintExtraDamageRatio);
        RediosRules.setUncontrolledSprintExtraCooldownTicks(uncontrolledSprintExtraCooldownTicks);
        RediosRules.setUncontrolledSprintAoEDodgeChance(uncontrolledSprintAoEDodgeChance);
        RediosRules.setChaosRuinIncomingAbsoluteEnabled(chaosRuinIncomingAbsoluteEnabled);
        RediosRules.setWallAttackTraceParticles(wallAttackTraceParticles);
        RediosRules.setWallAttackNotifyCooldownTicks(wallAttackNotifyCooldownTicks);
        RediosRules.setLocateBossEnabled(locateBossEnabled);
        RediosRules.setLocateBossDistanceBlocks(locateBossDistanceBlocks);
        RediosRules.setLocateBossNotifyIntervalTicks(locateBossNotifyIntervalTicks);
        RediosRules.setHeightFlightEnabled(heightFlightEnabled);
        RediosRules.setHeightFlightDiffBlocks(heightFlightDiffBlocks);
        RediosRules.setHeightFlightVerticalSpeed(heightFlightVerticalSpeed);
        RediosRules.setPhase2VoteRequired(phase2VoteRequired);
        RediosRules.setBossMissingVisionEnabled(bossMissingVisionEnabled);
        RediosRules.setBossMissingVisionTicks(bossMissingVisionTicks);
        RediosRules.setBossMissingVisionDotThreshold(bossMissingVisionDotThreshold);
        RediosRules.BossMissingVisionAction bossAction = bossMissingVisionActionRaw != null && bossMissingVisionActionRaw.equalsIgnoreCase("teleport") ? RediosRules.BossMissingVisionAction.TELEPORT : RediosRules.BossMissingVisionAction.WAYPOINT;
        RediosRules.setBossMissingVisionAction(bossAction);
        RediosRules.setPhase2VoteYesTokens(phase2VoteYesTokens);
        RediosRules.setPhase2VoteNoTokens(phase2VoteNoTokens);
        RediosRules.setRediosBookAuthor(rediosBookAuthor);
        // 2026-09-12（审计清理 G17 #5）：原 setRediosDefeatBookTitle(...) / setRediosVictoryBookTitle(...) 已删除。
        RediosRules.setRediosNotePhase1WinPhase2Lose(rediosNotePhase1WinPhase2Lose);
        RediosRules.setRediosOutcomeTextPhase1WinOnlyFile(rediosOutcomeTextPhase1WinOnlyFile);
        RediosRules.setRediosOutcomeTextPhase1WinPhase2LoseFile(rediosOutcomeTextPhase1WinPhase2LoseFile);
        RediosRules.setRediosOutcomeTextPhase2WinFile(rediosOutcomeTextPhase2WinFile);
        RediosRules.setRediosBattleMusicEnabled(rediosBattleMusicEnabled);
        RediosRules.setRediosBattleMusicVolume(rediosBattleMusicVolume);
        RediosRules.setRediosBattleMusicPhase1IntroTicks(rediosBattleMusicPhase1IntroTicks);
        RediosRules.setRediosBattleMusicPhase1LoopTicks(rediosBattleMusicPhase1LoopTicks);
        RediosRules.setRediosBattleMusicPhase2IntroTicks(rediosBattleMusicPhase2IntroTicks);
        RediosRules.setRediosBattleMusicPhase2LoopTicks(rediosBattleMusicPhase2LoopTicks);
        RediosRules.setRediosBattleMusicOutroEnabled(rediosBattleMusicOutroEnabled);
        RediosRules.setVoteTimeoutSeconds(voteTimeoutSeconds);
        RediosRules.setVoteTieAsYes(voteTieAsYes);
        RediosRules.setAdaptiveBlockTriggerHitsPerSecond(adaptiveBlockTriggerHitsPerSecond);
        RediosRules.setAdaptiveBlockDurationTicks(adaptiveBlockDurationTicks);
        RediosRules.setAdaptiveBlockDamageReduction(adaptiveBlockDamageReduction);
        RediosRules.setAdaptiveBlockCooldownTicks(adaptiveBlockCooldownTicks);
        RediosRules.setBattleRadiusBlocks(battleRadiusBlocks);
        RediosRules.setBattleExpelTimeoutSeconds(battleExpelTimeoutSeconds);
        RediosRules.setPushAwayDistance(pushAwayDistance);
        RediosRules.setPushAwayStrength(pushAwayStrength);
        RediosRules.setPushAwayRange(pushAwayRange);
        RediosRules.setRestoredBlocksWhitelist(restoredBlocksWhitelist);
        // 2026-09-12（SA 名单热配置化）：三键**原样写入** —— null（键缺失）与空表（显式全禁）必须保持可区分，
        // 故这里既不填默认值、也不能走任何把空表转成默认值的中转。
        RediosRules.setBossSaWhitelistNamespaces(bossSaWhitelistNamespaces);
        RediosRules.setBossSaExcludedNamespaces(bossSaExcludedNamespaces);
        RediosRules.setBossSaExcludedSaIds(bossSaExcludedSaIds);
        RediosRules.setRestoreNbt(restoreNbt);
        RediosRules.setLatencyThresholdMs(latencyThresholdMs);
        RediosRules.setLagProtectionEnabled(lagProtectionEnabled);
        RediosRules.setWeaponWeakpointEnabled(weaponWeakpointEnabled);
        RediosRules.setWeaponWeakpointSlowTicks(weaponWeakpointSlowTicks);
        RediosRules.setWeaponWeakpointCooldownTicks(weaponWeakpointCooldownTicks);
        RediosRules.setWeaponWeakpointFixedCooldown(weaponWeakpointFixedCooldown);
        RediosRules.setWeaponWeakpointDamageMultiplier(weaponWeakpointDamageMultiplier);
        RediosRules.setWeaponWeakpointArmorPierce(weaponWeakpointArmorPierce);
        // 2026-09-11（G03 #2）：未知键汇总告警——见 KNOWN_KEYS 的 javadoc
        warnUnknownKeys(root);
    }

    private static ResourceLocation tryParseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ResourceLocation.parse((String)raw);
        }
        catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 2026-09-11（代码审计 G03 修复）：安全取字符串键。
     * <p>
     * Gson 的 {@code JsonElement.getAsString()} 只被 {@code JsonPrimitive} 覆写——键值写成
     * 数组 / 对象 / null 时会抛 {@code UnsupportedOperationException}。本文件原先有 5 处裸调
     * （twilight_moment_mode、twilight_moment_apply_effect、三个 redios_outcome_text_*_file），
     * 异常会逃出 {@code apply()} → <b>整次数据包 reload 失败</b>，且文件顶部那条「配置缺失」
     * 告警不会触发，现象是「改了配置完全无效、日志也无提示」。失败时返回 null 并告警，
     * 由调用方回退默认值。
     */
    private static String tryParseString(JsonObject root, String key) {
        if (root == null || !root.has(key)) {
            return null;
        }
        JsonElement e = root.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            LOG.warn("silent_sun/redios_rules.json 的键 {} 不是字符串（类型不符），已忽略并使用默认值", key);
            return null;
        }
        return e.getAsString();
    }

    /**
     * 2026-09-12（SA 名单热配置化）：安全取「字符串数组」键（三个 SA 池名单键共用）。
     * <p>
     * 解析范式照抄 {@code restored_blocks_whitelist}：{@code has + isJsonArray} 判类型 →
     * 逐元素 {@code isJsonPrimitive} 校验 → 非法项 {@code LOG.warn} 后跳过。
     * 与之的两个区别：
     * <ol>
     *   <li>**不做 ResourceLocation 归一化** —— SA 名单里既有 namespace（如 {@code slashblade}，没有冒号）
     *       也有完整 SA id（如 {@code foxextra:thrust}），{@code ResourceLocation.parse} 会把前者
     *       误判成 {@code minecraft:slashblade}。条目仅 strip() 去空白，大小写原样保留
     *       （消费端用 {@code ResourceLocation.toString()} 恒小写比较）。</li>
     *   <li>**空数组原样返回空表**（不返回 null）—— 三个 SA 名单键是三态语义：
     *       键缺失 ⇒ null ⇒ 调用方回退静态配置；键存在且为 {@code []} ⇒ 空表 ⇒ 作者显式全禁；
     *       键存在且有内容 ⇒ 该内容。把 {@code []} 吃成 null/默认值正是「改了没生效且无提示」的根源。</li>
     * </ol>
     *
     * @return 键存在且是数组时返回解析结果（**可能是空表**）；键缺失 / 类型不是数组时返回 {@code null}
     *         （＝未配置，调用方据此回退静态配置）
     */
    private static ArrayList<String> tryParseStringList(JsonObject root, String key, String hint) {
        if (root == null || !root.has(key)) {
            return null;
        }
        JsonElement raw = root.get(key);
        if (raw == null || !raw.isJsonArray()) {
            // 2026-09-12（SA 名单热配置化）：键存在但类型不符 ⇒ 按「未配置」处理（回退静态配置），并明确告警，
            // 否则写成字符串/对象时现象同样是「改了没生效且无提示」。
            LOG.warn("silent_sun/redios_rules.json 的键 {} 不是数组（类型不符），已按未配置处理并回退静态配置；期望格式：{}",
                    key, hint);
            return null;
        }
        ArrayList<String> list = new ArrayList<String>();
        for (JsonElement e : raw.getAsJsonArray()) {
            if (!e.isJsonPrimitive() || e.getAsString().isBlank()) {
                LOG.warn("silent_sun/redios_rules.json 的 {} 条目非法（{}），已跳过：{}", key, hint, e);
                continue;
            }
            list.add(e.getAsString().strip());
        }
        return list;
    }
}
