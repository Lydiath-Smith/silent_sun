package com.lydiath.silent_sun.util;

/**
 * 标记接口：实现该接口的实体免疫「绝对真实伤害 / 断魂伤害」。
 * <p>
 * 语义源自莱德厄斯 Boss——绝对伤害与断魂伤害对 Boss 一律无效。
 * 抽成接口后，未来新增同类 Boss 只需实现本接口即可复用该免疫语义，
 * {@link AbsoluteDamageUtil} 不再依赖具体实体类型。
 */
public interface IAbsoluteDamageImmune {
}
