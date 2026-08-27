# Soul Sever (断魂) — 独立导出模组

## 来源
提取自《灭却之日》(Silent Sun) Mod 的 Redios Boss 战机制。

## 机制说明
- **断魂附加值**：外部系统（如 Boss）通过 `SoulSeverEvents.addSoulSeverBonus(target, amount)` 为目标叠加断魂附加值
- **受击返还**：目标下次受击时，断魂附加值以**绝对伤害**（绕过护甲/附魔/抗性）形式追加
- **砺锋尝胆**：通过 `markSharpenSoulSever()` 标记目标，每 tick 自动刷新断魂效果等级

## 文件清单
| 文件 | 说明 |
|------|------|
| `SoulSeverMod.java` | 模组入口 |
| `SoulSeverEffect.java` | 断魂效果类（纯标记） |
| `SoulSeverEvents.java` | 事件处理 + 公开 API |
| `AbsoluteDamageUtil.java` | 绝对伤害工具 |

## 集成方式
将 `com.lydiath.soulsever` 包直接复制到目标工程的 `src/main/java` 下，
在目标模组的 `@Mod` 构造器中注册 `SoulSeverEvents.MOB_EFFECTS`：

```java
@Mod("your_mod_id")
public class YourMod {
    public YourMod(IEventBus modEventBus) {
        SoulSeverEvents.MOB_EFFECTS.register(modEventBus);
    }
}
```

然后通过 API 调用：
```java
// 为目标叠加断魂附加值
SoulSeverEvents.addSoulSeverBonus(target, 50L);

// 标记砺锋尝胆（自动刷新效果等级）
SoulSeverEvents.markSharpenSoulSever(target, boss, 2);
```
