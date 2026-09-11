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
            RediosRules.setRediosDefeatBookTitle(null);
            RediosRules.setRediosVictoryBookTitle(null);
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
        String rediosDefeatBookTitle = null;
        if (root.has("redios_defeat_book_title")) {
            try {
                rediosDefeatBookTitle = root.get("redios_defeat_book_title").getAsString();
            }
            catch (RuntimeException e) {
                rediosDefeatBookTitle = null;
            }
        }
        String rediosVictoryBookTitle = null;
        if (root.has("redios_victory_book_title")) {
            try {
                rediosVictoryBookTitle = root.get("redios_victory_book_title").getAsString();
            }
            catch (RuntimeException e) {
                rediosVictoryBookTitle = null;
            }
        }
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
        double adaptiveBlockTriggerHitsPerSecond = 6.0;
        if (root.has("adaptive_block_trigger_hits_per_second")) {
            try {
                adaptiveBlockTriggerHitsPerSecond = root.get("adaptive_block_trigger_hits_per_second").getAsDouble();
            }
            catch (RuntimeException e) {
                adaptiveBlockTriggerHitsPerSecond = 6.0;
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
        double latencyThresholdMs = 150.0;
        if (root.has("latency_threshold_ms")) {
            try {
                latencyThresholdMs = root.get("latency_threshold_ms").getAsDouble();
            }
            catch (RuntimeException e) {
                latencyThresholdMs = 150.0;
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
        RediosRules.setRediosDefeatBookTitle(rediosDefeatBookTitle);
        RediosRules.setRediosVictoryBookTitle(rediosVictoryBookTitle);
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
        RediosRules.setAdaptiveBlockTriggerHitsPerSecond((int) adaptiveBlockTriggerHitsPerSecond);
        RediosRules.setAdaptiveBlockDurationTicks(adaptiveBlockDurationTicks);
        RediosRules.setAdaptiveBlockDamageReduction(adaptiveBlockDamageReduction);
        RediosRules.setAdaptiveBlockCooldownTicks(adaptiveBlockCooldownTicks);
        RediosRules.setBattleRadiusBlocks(battleRadiusBlocks);
        RediosRules.setBattleExpelTimeoutSeconds(battleExpelTimeoutSeconds);
        RediosRules.setPushAwayDistance(pushAwayDistance);
        RediosRules.setPushAwayStrength(pushAwayStrength);
        RediosRules.setPushAwayRange(pushAwayRange);
        RediosRules.setRestoredBlocksWhitelist(restoredBlocksWhitelist);
        RediosRules.setRestoreNbt(restoreNbt);
        RediosRules.setLatencyThresholdMs((int) latencyThresholdMs);
        RediosRules.setLagProtectionEnabled(lagProtectionEnabled);
        RediosRules.setWeaponWeakpointEnabled(weaponWeakpointEnabled);
        RediosRules.setWeaponWeakpointSlowTicks(weaponWeakpointSlowTicks);
        RediosRules.setWeaponWeakpointCooldownTicks(weaponWeakpointCooldownTicks);
        RediosRules.setWeaponWeakpointFixedCooldown(weaponWeakpointFixedCooldown);
        RediosRules.setWeaponWeakpointDamageMultiplier(weaponWeakpointDamageMultiplier);
        RediosRules.setWeaponWeakpointArmorPierce(weaponWeakpointArmorPierce);
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
}
