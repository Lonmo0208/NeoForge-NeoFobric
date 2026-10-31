# NeoFobric

**中文（默认）** | [English](#english)

> **一个拿来自娱自乐的加载器：是一坨"史" —— 但我们就是要它比 Forbric 那坨史好。**
> A loaders experiment built purely for fun: a pile of garbage — but it has to be a *better* pile of garbage than Forbric's.

对照物 / For comparison: <https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader>

---

## 这是什么

**在 NeoForge 加载器里搭了一座桥，让 Fabric 能一起跑。** 底座是 NeoForge（本仓库是 **NeoForge 26.1.2** 的 fork，当前版本 **`26.1.2.126-26.1.2-NeoFobric`**）：FML 依旧是宿主 —— 它启动、它拥有游戏本体、它管模块层和它自己的模组。桥负责把 **fabric-loader 本体**（由本仓库的 `fabric-loader` 子模块现场编译）拉进同一个进程，Fabric 模组按 Fabric 自己那套规矩加载：`fabric.mod.json`、入口点、嵌套 jar、access widener / class tweaker、Fabric mixin 配置，一样不少。这里**没有 Forge** —— 已经"似"掉的那个旧 Forge 不参与任何环节。**不装任何 Fabric 模组时，它就是完完全全的 NeoForge。** 装模组时**优先选 NeoForge 版**，只有 NeoForge 版不存在时再用 Fabric 版：桥接的兼容性是"能跑"级别的，不是"保证"级别的。

`26.1.2-NeoFobric` 分支是把 26.2 的桥**回移植**到 26.1.2 上的一条线：Fabric API 在 26.1.2 上的四个 mixin 落点、内容注册表钩子、game test 启动路径、以及 26.1.2 自己的形状差异（`validate()` 一次性、资源重载的 required 包处理等）都单独对过。

## 现状：请当作「能玩但别当真」的测试版

bug 可能还有很多，兼容性也可能还不够好，**甚至无法和 FFAPI（Forgified Fabric API）一起用**（见下）。**本版本验证过的范围**：Fabric API 自己的两套测试（服务端 100/100 全绿、客户端 `BUILD SUCCESSFUL`）；**432 个 26.1.2 模组**（NeoForge + Fabric 混装；EarthMobs 已按已知问题移出，现为 431）实机进世界，集成服务器加载 3.9 秒，无黑屏、无断线，樱花林世界与 HUD 正常渲染（截图确认）。**生产安装链**（安装器装出来的独立专服 / 客户端）本轮没有单独实测，请自行验证。**其他设备理论都能装**：凡是能装 NeoForge 26.1.2 + Fabric 的环境（Windows / Linux / macOS，各种启动器）理论上都可以 —— **但我们只在开发机上验证过，没有做多设备验证**。**能进游戏 ≠ 每个功能都对；NeoForge 模组也可能被连锁效应波及**（桥改的是两边共用的那一层）。

### 26.1.2（`26.1.2.126`，2026-10-30）

- **26.1.2 线的第一个发布版本**：桥从 26.2 回移植（Minecraft / NeoForge 26.1.2、FML 11.0.13、Fabric Loader 0.20.1.1、Fabric API 0.155.3+26.1.2、Mixin 0.17.4 + MixinExtras 0.5.5）。`fabric-loader` 由本仓库子模块现场编译。
- **模组菜单区分加载器**：Fabric 模组有自己的行，带 Loader 标签（Fabric / NeoForge 两种配色），点开能看到 `fabric.mod.json` 里的元数据。
- **主菜单左下角的模组计数拆成两部分**：`共 N 个模组：NeoForge X + Fabric Y`。
- **432 件 26.1.2 模组集实机进世界**：集成服务器加载 3.9 秒，无黑屏、无断线（EarthMobs 之后按已知问题一节移出，现模组集 431 件）。
- **回归**：Fabric API 服务端套件 **`All 100 required tests passed`** ✓；客户端套件 **`BUILD SUCCESSFUL`**（截图对比、动态注册表同步、生命周期 / 命令等用例全过）。

## 反馈

**欢迎报 bug —— 趁 token 还没烧完，收到就会修**（在本仓库开 Issue，或者直接找作者）。修得快慢另说，但会修。反正不管最后修成什么样，**它都还是比 Forbric 那坨史好**。报的时候如果方便，带上：游戏版本 + 加载器版本（安装器自报的那个号）+ 模组清单 + `logs/latest.log`（或者崩档报告），能省很多来回。

### 我们跑了什么测试

**26.1.2（`26.1.2.126`）**：Fabric API 服务端套件 **`All 100 required tests passed`** ✓；客户端套件跑到收尾 **`BUILD SUCCESSFUL`**（截图对比、动态注册表同步、生命周期/命令等用例全过）；432 件 26.1.2 模组集实机进世界（`Dev logged in with entity id` 之后持续运行，无崩溃）。桥内置自检在 432 模组集里的读数：**`events 11/11 wired`**、**`fabric payload round trip ok`**、**网络 canary ok**（发出探针 180 tick 后仍在线）。

## 26.1.2 这一轮修掉的典型问题

- **Fabric API 的四个 mixin 落点**：26.1.2 把原版调用点搬过位置，Fabric API 各模块的注入锚点得跟着改 —— 不改的话 Fabric API 自己都起不来。
- **内容注册表钩子**：桥把 Fabric 的内容注册表合并进 26.1.2 的加载列表（否则表现为 `Missing registry` / 存档进不去）。
- **game test 启动路径**：Fabric 的测试模组要能把 game test 专服拉起来，`-Dfabric-api.gametest` 才跑得完 100 条必测项。
- **`validate()` 只允许一次**：26.1.2 的 `validate()` 是一次性的，同一进程起第二个服务器会炸 —— 改成可重复调用。
- **资源重载**：① 显式移除的 required 资源包（Fabric 的 tag 测试包）不再被重新加回，客户端 tag 测试因此恢复全绿；② 重载监听器列表保持可变，Fabric 模组可以在重载开始后注册监听器。
- **着色器（黑屏根因之一）**：模组命名空间的静态管线编译失败只记 WARN 并跳过，不再让整个资源重载抛 `Failed to load required shader programs`；原版管线仍然硬失败。
- **配方同步**：`neoforge:recipe_content` 发送前逐个预编码，编码失败的配方记 WARN 后剔除 —— 某些模组用两个不同实例构造 `StreamCodec.unit`（Enderite），以前会在 netty 线程抛异常，把刚进服的玩家踢下线。

## 已知不兼容

**FFAPI（Forgified Fabric API）不能同装** —— 它是 NeoForge 模组，和桥自带的那份真 Fabric API 携带**同一批包**（`net.fabricmc.fabric.*`）⇒ 同一个模块层里两个模块导出同名包 ⇒ JDK 直接拒绝启动，只能二选一。**Sinytra Connector 不能同装** —— 它和桥抢同一件差事（把 Fabric 模组带进 NeoForge）。另外：**"一起跑" ≠ "互相看得见"** —— 两边模组并排运行没问题，但"Fabric 模组直接依赖某个 NeoForge 模组的 API"（反之亦然）**不保证**。

**不要合并上游 `upstream/26.1.x`** —— 26.1.x 的流体子系统与 26.1.2 不兼容，合并会丢桥的代码并让整棵树编译失败（本分支已经回退过一次这样的合并）。要拿上游修复请逐条 cherry-pick。

**Enderite 的两条 special 配方客户端收不到**（`enderitemod:enderite_elytra`、`enderitemod:enderite_shield_decoration`）：该模组用两个不同实例构造 `MapCodec.unit` / `StreamCodec.unit`，网络层无法编码。服务端合成不受影响，只是客户端配方书里没有。

**SereneSeasons 26.1.2.0.5 及以上在 26.1.2 客户端会 `StackOverflowError`**：26.1.2 新增的 `AtmosphericFogEnvironment` 会直接调 `Biome.hasPrecipitation()`，而 0.5+ 注入的 `onHasPrecipitation` 又回调同一方法、没有重入保护。请使用 **26.1.2.0.4**（本仓库验证用的模组集就是 0.4）。

**模组集里已知要避开的模组**（都是模组侧的问题，不是桥的）：

- `earthmobsmod 26.1.2-29.6.0` —— 它的 `PigMixin` 调 `Entity.isInFluidType(FluidType)`，而 **26.1.2 这一版 NeoForge 根本没有这套实体-流体 API**（上游 `IEntityExtension` 里整段是注释掉的 `// TODO: Reimplement with Entity/Fluid interaction patches`），该方法要到上游 `26.1.x` 分支（同样是 MC 26.1.2，但流体子系统重写过）才有。表现是猪一 tick 就 `NoSuchMethodError` 崩服。它的依赖声明只写了 `versionRange=[26.1.2.95,)`，所以我们 126 也被判为满足、能装进来 —— 已移出模组集；
- `cinderscapes` —— 盔甲纹饰的 item model 生成对缺 `cases` 的 JSON 调 `JsonArray.get`，NPE 让 `itemStackModels` future 失败 → 资源重载失败 → 黑屏；
- Xaero 小地图 / 世界地图 —— `GuiRenderer` 的 Pre 推送了矩阵、Post 没触发，16 帧后 `max stack size of 16 reached`；
- `shouldersurfing` —— 配置未加载时读 NeoForge 配置，`Cannot get config value before config is loaded`。

## 怎么装 / 怎么构建

**从源码构建**：

```bash
git clone --recurse-submodules <this repo>
cd NeoFobric
./gradlew :neoforge:installerJar      # 产物在 projects/neoforge/build/libs/
```

普通克隆也行：`settings.gradle` 会在缺失时自动把 `fabric-loader` 子模块拉下来（钉在一个固定提交上）。**安装**：① 先按 NeoForge 官方方式装好 **NeoForge 26.1.2**；② 用上面构建出来的安装器安装；③ 把 **Fabric 模组和 NeoForge 模组一起**丢进 `mods/`。发布资产里的五个 jar：主 jar、`-client`、`-joined`、`-universal`、`-installer`。**卸载 / 回退**：把本加载器从安装配置里去掉，或用 NeoForge 自己的安装覆盖一遍即可 —— 它没有往游戏本体写不可逆的东西。

### 第一次启动会慢，这是正常的

**第一次进游戏会明显慢。** 原因是桥要先**处理一遍 Fabric 侧的模组**：扫描它们的 jar，把需要调整的部分（**尤其是 Fabric API 各个模块的 mixin 锚点** —— NeoForge 把原版的调用点搬过位置，这些锚点得跟着改）**生成一份修改后的副本放进缓存**（`.neofobric/` 目录里），之后就都从缓存加载。所以：

- **第一次启动**：慢（本模组集 102 个 Fabric jar 需要 realign，还要叠上游戏自身的首次资源加载）；
- **之后的启动**：和普通 NeoForge 差不多；
- **★ 只要动过模组集（加、删、换任意一个模组），这份缓存整体失效，下次启动会再处理一遍** —— "刚换完模组第一次慢、之后快"就是它的正常节奏，不是卡死；
- 想强制跳缓存：加 JVM 参数 `-Dneofobric.noPatchCache=true`（调试用，会更慢）。

## 许可与出处

见 [README-LICENSE.md](README-LICENSE.md)：**我们自己写的部分**沿用 NeoForge 的 `LGPL-2.1-only`；内嵌的 Fabric API 模块与 fabric-loader 是 `Apache-2.0`（各自保留声明）；我们**没有复制** FFAPI（Apache-2.0）或 Sinytra Connector（MIT）的代码，只在注释里引用它们公开的做法，并已在许可文件里致谢。

---

## 关于这个项目

**这是个 AI 项目**（人定方向、验结果；代码、文档、排查过程大量由 AI 完成），所以它是一坨"史"，我们自己也这么叫它。但我们对这一坨有要求：**它得比 Forbric 那坨好** —— 好在它用的是**真正的 fabric-loader**（不是重新实现一遍 Fabric API 的语义）、**不丢掉 NeoForge**（底座还在、NeoForge 模组照常跑、不装 Fabric 模组时它就是普通 NeoForge），并且把上游 **Fabric API 自己的测试流程**跑完了（26.1.2 上是 100/100），还把结果原样写在这里。

**再强调一遍：这是一坨 AI 写的史。** 它**肯定不如信雅互联（Sinytra Connector）+ FFAPI** —— 人家是认真做的工程，我们是从正经路子旁边岔出去的一坨。但写它，就是想让 Forbric 看一眼同一道题的两个答案：**同样是方向盘全交给 AI，凭什么你写出来是一坨臭的，我这坨好歹是巧克力味的？** 想要"正经"的 NeoForge ↔ Fabric 互通方案，请去看 **Sinytra Connector + FFAPI**；我们这条纯属自娱自乐的一条岔路。

---

## English

**NeoFobric** is a bridge that lets fabric-loader run inside a NeoForge instance. This repository is a **NeoForge 26.1.2** fork, version **`26.1.2.126-26.1.2-NeoFobric`**; the `26.1.2-NeoFobric` branch backports the 26.2 bridge onto 26.1.2.

FML stays the host: it starts, it owns the game, and it keeps loading its own mods. The bridge pulls the real **fabric-loader** (compiled from this repository's `fabric-loader` submodule) into the same process, so Fabric mods are loaded the Fabric way: `fabric.mod.json`, entrypoints, nested jars, access wideners / class tweakers and Fabric mixin configs. There is no Forge anywhere, and with no Fabric mod installed this is plain NeoForge.

**State: a playable-but-don't-trust-it beta.** What was verified on 26.1.2: Fabric API's own suites — server **100/100 required tests passed**, client **BUILD SUCCESSFUL**; a **432-mod** 26.1.2 mod set (NeoForge + Fabric mixed; EarthMobs has since been removed, 431 now) reached an in-world session with a 3.9 s integrated-server load, no black screen and no disconnect. The bridge's own self-check read `events 11/11 wired`, `fabric payload round trip ok` and a healthy network canary on that mod set. The production installer chain (standalone server / client installed from the installer) was not separately tested in this round. Prefer NeoForge builds of mods when they exist; the bridge is "it runs" level, not a guarantee.

**What changed on 26.1.2**

- Backported the bridge: the four Fabric API mixin landing points, the content-registry hooks, the game-test boot path, and 26.1.2's own shape differences.
- The mods menu now shows Fabric mods as their own rows with a Loader chip, and the title screen splits the mod count into NeoForge and Fabric.
- `validate()` is repeatable, so a second in-process server can start.
- Resource reloads honour explicitly removed required packs, and the reload-listener list stays mutable so Fabric mods can register late.
- A failed static shader pipeline in a mod namespace is logged and skipped instead of failing the whole reload (one of the black-screen causes).
- `neoforge:recipe_content` pre-encodes every recipe and drops the ones its serializer cannot encode (with a warning) instead of disconnecting the joining player.

**Known incompatibilities**: FFAPI (Forgified Fabric API) and Sinytra Connector cannot be installed alongside this bridge (same packages / same job). Do not merge `upstream/26.1.x` — its fluid subsystem is incompatible with 26.1.2 and the merge drops the bridge code. Enderite's two special recipes are never sent to the client (the mod builds `StreamCodec.unit` with a different instance), so they are absent from the client recipe book while still craftable server-side. SereneSeasons 26.1.2.0.5+ stack-overflows on the 26.1.2 client; use 0.4. Cinderscapes, Xaero's maps and ShoulderSurfing have mod-side crashes and are not part of the verified mod set. EarthMobsMod 26.1.2-29.6.0 calls `Entity.isInFluidType`, which 26.1.2's NeoForge does not have (the fluid-entity API only exists on upstream `26.1.x`), so it crashes the server as soon as a pig ticks; it has been removed from the set.

**Install & build**: build with `./gradlew :neoforge:installerJar`, install NeoForge 26.1.2 first, then the installer, then drop both Fabric and NeoForge mods into `mods/`. The first launch is slow: the bridge rewrites the Fabric jars it needs (mixin anchor realignment) into `.neofobric/` and reuses that cache afterwards; any change to the mod set invalidates the cache. `-Dneofobric.noPatchCache=true` forces a rebuild for debugging.

**Licensing & attribution**: see [README-LICENSE.md](README-LICENSE.md). Our own code is `LGPL-2.1-only` (as NeoForge); embedded Fabric API modules and fabric-loader stay `Apache-2.0`. No code was copied from FFAPI or Sinytra Connector.

**About**: this is an AI-assisted project built for fun — a pile of garbage that has to be a better pile of garbage than Forbric's. For a serious NeoForge ↔ Fabric interoperability solution, use **Sinytra Connector + FFAPI**.
