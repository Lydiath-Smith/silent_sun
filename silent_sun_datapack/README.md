# Silent Sun — 灭却之日 Datapack

独立数据包，非覆盖工程文件。放置于 `world/datapacks/` 即可生效。

## 文件结构

```
silent_sun_datapack/
├── pack.mcmeta                          # pack_format=48 (1.21.1)
└── data/silent_sun/
    ├── damage_type/                     # 自定义伤害类型
    │   ├── absolute.json               #  绝对伤害（断魂、混沌毁灭）
    │   ├── reflect.json                #  反射伤害
    │   ├── void.json                   #  虚空伤害（竭力之悲）
    │   └── heal_immunity.json          #  治疗免疫伤害
    ├── tags/
    │   ├── damage_type/
    │   │   ├── boss_true_damage.json   #  真实伤害标签（穿透护甲+格挡）
    │   │   ├── boss_void_damage.json   #  虚空伤害标签（被吸收）
    │   │   └── bypasses_guard.json     #  绕过格挡的伤害类型
    │   └── entity_types/
    │       ├── boss_primary_targets.json # 主目标（默认仅 player）
    │       ├── boss_mob_targets.json     # 非玩家可攻击实体
    │       └── boss_cheat_immune.json    # 反作弊免疫黑名单
    └── loot_table/
        ├── redios_phase1.json          # 一阶段掉落
        └── redios_phase2.json          # 二阶段掉落
```

## 使用方式

### Mode 1（仅玩家战斗）
默认配置。`boss_primary_targets` 仅包含 `minecraft:player`。

### Mode 2（斗蛐蛐模式）
将目标实体类型添加到 `boss_primary_targets.json` 和 `boss_mob_targets.json`：

```json
{
  "replace": false,
  "values": [
    "minecraft:player",
    "minecraft:warden",
    "minecraft:wither",
    "minecraft:ender_dragon"
  ]
}
```

### 反作弊
将已知会作弊的模组实体添加到 `boss_cheat_immune.json`，Boss 会永久免疫该类型实体的伤害。

### 自定义掉落
修改 `redios_phase1.json` / `redios_phase2.json` 中的战利品表。

## 与模组源码的关系
- 此数据包完全独立，不修改、不覆盖 `src/` 下任何 Java 源码
- 伤害类型标签在 `DamagePipeline` 中通过 `source.is(TagKey)` 引用
- 实体类型标签在 `RediosEntity` 索敌逻辑中通过 `entity.getType().is(TagKey)` 引用
- 战利品表在结算时通过 `ResourceLocation` 引用
