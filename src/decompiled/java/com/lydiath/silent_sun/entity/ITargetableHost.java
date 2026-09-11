package com.lydiath.silent_sun.entity;

import java.util.Set;
import java.util.UUID;

/**
 * 可被索敌规则复用的 Boss 宿主契约。
 * <p>
 * 抽出 {@link BossTargeting} 对宿主的唯一依赖——「被驱逐玩家的 UUID 集合」，
 * 使索敌 / 受伤判定规则不再强绑具体实体类型，未来新增 Boss 实现本接口即可复用。
 */
public interface ITargetableHost {

    /** 被驱逐出战斗的玩家 UUID 集合（这些玩家既不能被 Boss 攻击，其对 Boss 的伤害也无效）。 */
    Set<UUID> expelledPlayers();

    /**
     * 本场参战玩家 UUID 集合（**只读用途**，调用方不得修改返回的集合）。
     * <p>
     * 2026-09-11（代码审计 G06 #2）：Mode 1 下「有主宠物是否算合法攻击者」的判据需要
     * 「主人是否参战」，原先该集合只在 {@code RediosEntity} 内部可见，导致 {@link BossTargeting}
     * 只能退化为「有主人即合法」，与索敌侧口径分叉。补入接口以统一判据。
     */
    Set<UUID> battleParticipants();
}
