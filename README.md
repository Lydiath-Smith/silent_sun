# 喑哑之日 · Silent Sun

NeoForge **1.21.1** 的 Boss 战模组，围绕「碎镜之影 · 莱德厄斯（Redios）」的一场长线战斗与其周边系统。

> **状态：alpha（未正式发布）**。当前版本号见 `gradle.properties` 的 `mod_version`。
> 仓库：<https://gitee.com/lydiath/silent_sun>

## 这是什么

- **两阶段 Boss 战，共 20 段「头衔」**（每阶段 10 段）：血量按头衔分段、段内锁血，打穿段底才推进下一段。
- **阶段衔接**：一阶段收尾有**投票**（可通过配置跳过）、濒死锁血与转场演出。
- **拔刀剑（SlashBlade）联动**：Boss 会切换刀并释放 SA；**SA 池由命名空间白名单控制**（默认只开 3 个，其余交给整合包作者按强度取舍）。
- **边界收口**：反作弊（创造模式 / 篡改血量 / 死亡作弊）、脱战与出圈逐出、区块卸载与跨维度离场、极限模式保命。
- **产出**：分阶段奖励与**结局书**（多语言）、战斗音乐、成就。

设计与实现的权威文档：

| 文档 | 内容 |
|---|---|
| `docs/设计文稿-重制版.md` | 设计稿（**与代码冲突时以代码为准**） |
| `_知识文库/施工台账.md` | 逐条施工记录与作者裁决留痕（**动手前先读，避免重做已裁决的事**） |
| `_知识文库/危险面与共享判据.md` | 已知的六类事故面与共享判据 |
| `docs/实现计划-*.md` | 各轮改动的计划书 |

## 构建

**前置：JDK 21。**

1. 把两个前置模组的 jar 放进 `libs/`（**仓库不包含它们**，原因见下）：
   - `libs/SlashBladeResharped-2.0.7-1.21.1.jar` —— 拔刀剑：重锋
   - `libs/extinction_day_mod_1784441698-1.16.0.jar` —— 灭却之日
2. 出包：

   ```bat
   gradlew.bat exportJar
   ```

   - ⚠️ **必须用 `exportJar`，不要用 `build`**：交付包由 `exportJar` 生成（它会带上 `bin/main` 里的手写资源真源）。
3. 产物：`build/libs/silent_sun-<版本>.jar`
4. 如需源码包：`gradlew sourcesJar`（全量源码）/ `gradlew rageSourcesJar`（精选若干类）。

> **为什么前置 jar 不入库**：它们是第三方模组的二进制，本仓库不代为分发。二者在 `build.gradle` 里是 `compileOnly` + `localRuntime`，**只影响编译与本地调试**；运行时由玩家的 `mods/` 目录提供（`neoforge.mods.toml` 已把前置声明为 `required`）。

## 目录说明

| 路径 | 用途 |
|---|---|
| `src/decompiled/java` | **主源码**（反编译产物 + 持续手改） |
| `bin/main/assets` · `bin/main/data` · `bin/main/pack.mcmeta` · `bin/main/silent_sun.mixins.json` | **手写资源真源**：`build.gradle` 用 `srcDir('bin/main')` 打进 jar，改 lang / 配置 JSON 请改这里 |
| `src/generated/resources` | 数据生成产物（`runData`） |
| `src/main/templates/META-INF/neoforge.mods.toml` | 模组元数据模板（`${...}` 由 `gradle.properties` 展开） |
| `src/main/resources/META-INF/LICENSE.txt` | 随包协议全文（与根 `LICENSE.txt` 同步） |
| `docs/` · `_知识文库/` | 设计稿、计划书、验收清单 / 台账、经验记录 |
| `_规则/rules_defaults_check.ps1` | 热配置默认值「三处同源」对账脚本（只读；改配置键后跑一次） |

## 版本号（两条线）

| 线 | 形式 | 用途 |
|---|---|---|
| **发布线** | `0.0.1-alphaN` | 只在对外上传 / 发布时使用 |
| **修改线** | `0.0.1-mod.<yyyyMMdd>` | 本地改动构建 |

两条线**错开、互不占用**，看包名即可分辨「正式发布版 / 本地修改版」。
⚠️ 历史上的 `0.0.17` / `0.0.24` **从未对外发布**，勿按"需要接续历史号"处理。

## 协议

采用**分区授权** —— 代码与美术 / 音频资源适用不同协议。全文见 [`LICENSE.txt`](LICENSE.txt)（同一份随包于 `META-INF/LICENSE.txt`）：

- **代码 → Apache License 2.0**
- **美术 / 音频资源 → CC BY-NC-SA 4.0**
- **整合包与服务器明确许可**：可随任意整合包分发、可在任意服务器使用，**含获赞助或捐赠的整合包与服务器**；单独提取或转售美术资源不在许可范围。
- 早期版本（`0.0.1-alpha2` 之前）适用其发布时的旧自定义协议（保留所有权利）——**新旧版本各自适用其发布时的协议**。

## 当前停用的功能

- **「传送门刀身残影」的客户端 mixin 未注册**（作者裁决：刀身与残影不做）。代码与 shader 资源保留在仓库中，`bin/main/silent_sun.mixins.json` 的 `client` 数组为空即可复现「停用」状态；恢复方式见该文件与两个 mixin 类的注释。

## 说明

- 本模组为独立自制内容，**不隶属于任何官方模组**。
- AI 协助制作：DeepSeek。
