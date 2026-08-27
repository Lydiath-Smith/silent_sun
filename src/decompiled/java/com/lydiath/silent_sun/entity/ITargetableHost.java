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
}
