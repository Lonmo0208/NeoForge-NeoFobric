# NeoFobric

**中文（默认）** | [English](#english)

> **一个拿来自娱自乐的加载器：是一坨"史" —— 但我们就是要它比 Forbric 那坨史好。**
> A loaders experiment built purely for fun: a pile of garbage — but it has to be a *better* pile of garbage than Forbric's.

对照物 / For comparison: <https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader>

---

## 这是什么

**在 NeoForge 加载器里搭了一座桥，让 Fabric 能一起跑。** 这个分支的底座是 **Minecraft 26w14a**（今年的愚人节快照，由官方 26.1.2 派生）叠上本仓库的 **`26w14a-Cp26.1`** 兼容线 —— 后者在代码上做加法，让 **26.1.2 的 NeoForge 模组也能装进 26w14a**。`26w14aCp26-Neofobric` 再在它上面接上桥：**fabric-loader 本体**（由本仓库的 `fabric-loader` 子模块现场编译）被拉进同一个进程，Fabric 模组按 Fabric 自己那套规矩加载：`fabric.mod.json`、入口点、嵌套 jar、access widener / class tweaker、Fabric mixin 配置，一样不少。

所以这个分支上同时存在三套东西：

1. **26w14a 原版的调用点与愚人节玩法** —— 原版代码的位置、数据、行为都留在原地；
2. **26.1.2 的"伪原版"调用点** —— Cp26.1 为 26.1.2 模组的 mixin 补出的落点；NeoForge 的平台语义照旧；
3. **Fabric 模组** —— 桥按 Fabric 的规矩加载，和 NeoForge 模组并排跑。

**不装任何 Fabric 模组时，它就是带 Cp26.1 兼容的 NeoForge。** 装模组时**优先选 NeoForge 版**，只有 NeoForge 版不存在时再用 Fabric 版：桥接的兼容性是"能跑"级别的，不是"保证"级别的。版本号：**`26.1.26w14a.126-26w14a-NeoFobric`**。

### 为什么用 26.1.2 的 Fabric API？

26w14a 自带的 Fabric API 是 **0.145.2** —— 版本低、模块少，26.1.2 时代的模组声明的 API 它没有。所以桥把 Fabric 依赖检查看到的游戏版本报成 **26.1.2**（`neofobric.fabricGameVersion`），主线测试也按 **Fabric API 0.155.3+26.1.2** 跑。0.145.2 的测试集还留在 `projects/neoforge/run/{client,server}/mods-145-*`，需要时可以复跑对照。

## 现状：请当作「能玩但别当真」的测试版

bug 可能还有很多，兼容性也可能还不够好，**甚至无法和 FFAPI（Forgified Fabric API）一起用**（见下）。**本分支实际验证过的范围**：

- **客户端**：标题界面 / 模组菜单实机确认 —— 双端计数（实测读数 `共 46 个模组：NeoForge 2 + Fabric 44`）与 Fabric 模组的折叠行、Loader 标签都正常；主菜单的版本行显示 `Minecraft 26.1.2 Comp 26w14a` / `NeoForge 26.1.26w14a-cp26.1-herdcraft.5`；
- **Fabric API 服务端套件**：**`All 100 required tests passed`** ✓（0.155.3+26.1.2，2026-11-02）；
- **Fabric API 客户端套件**：客户端 tag、tag 移除、命令、动态注册表同步、单机客户端 tag、"remove 条目默认 optional" 等用例都过；**唯一没过的是 `clientTagDedicatedServerTests`** —— 专服移除内置资源包并重载后，客户端仍看到旧的 tag 内容（`minecraft:dirt` 还在 `minecraft:sword_efficient` 里）。26.1.2 线上这一套是绿的（那边的包移除 / 重载处理修过），26w14a 上还剩这一处差异。

**没做**（别当成已验证）：26.1.2 那套 **432 件模组集**没有在本分支复跑；生产安装器装出来的独立专服 / 客户端没有单独实测；多设备没有验证。**能进游戏 ≠ 每个功能都对；NeoForge 模组也可能被连锁效应波及**（桥改的是两边共用的那一层）。本轮按**存档**推送：分支可构建、可玩，暂时没有单独的 release 资产。

## 这个分支上补了什么

- **桥的移植**：fabric-loader 子模块、Fabric jar 的 mixin 锚点 realign、class tweaker / access widener、嵌套 jar 与 entrypoint 分发、Fabric API 的四个 mixin 落点、内容注册表钩子、game test 启动路径；
- **双端 UI**：标题界面的 NeoForge / Fabric 双计数；模组菜单里 Fabric 模组有自己的行和 Loader 标签，同包内的子模组折叠在所属模组下（`▸`/`▾`、搜索时自动展开）；
- **26.1.2 形状差异的移植**：`validate()` 一次性、注册表冻结幂等、`RegistryDataLoader` 的 4 参 shim、`SimpleJsonResourceReloadListener` 的 `Optional` 标记、`ReloadableServerRegistries` 的 `ConditionalOps`、`AttributeSupplier.Builder` 回退、`CustomPacketPayload` 的 2 参 codec、`handleCustomPayloadCommon` 拆分、`ServerModLoader` 的"模组已加载"守卫、`GuiRenderer` 的原版 picture-in-picture map（NeoForge 的 PIP 重构换掉了原版调用点，Fabric 的注册会丢）；
- **资源 / 着色器 / 配方**：重载监听器列表保持可变、显式移除的 required 包处理、模组命名空间的静态管线失败只记 WARN、`neoforge:recipe_content` 预编码并剔除无法编码的配方；
- **Fabric 侧数据语义**：tag 的 `fabric:remove` 合并、燃料事件、堆肥 / 斧头 / 流体等 Fabric 模组行为翻译成平台语义。

## 怎么装 / 怎么构建

**从源码构建**：

```bash
git clone --recurse-submodules <this repo>
cd NeoFobric
git checkout 26w14aCp26-Neofobric
./gradlew :neoforge:installerJar      # 产物在 projects/neoforge/build/libs/
```

普通克隆也行：`settings.gradle` 会在缺失时自动把 `fabric-loader` 子模块拉下来（钉在一个固定提交上）。**安装**：用上面构建出来的安装器装出 `26.1.26w14a.126-26w14a-NeoFobric` 这一版；然后把 **26.1.2 构建的 NeoForge 模组** 和 **Fabric 模组**一起丢进 `mods/`。**卸载 / 回退**：把本加载器从安装配置里去掉，或用 NeoForge 自己的安装覆盖一遍即可 —— 它没有往游戏本体写不可逆的东西。

### 第一次启动会慢，这是正常的

**第一次进游戏会明显慢。** 原因是桥要先**处理一遍 Fabric 侧的模组**：扫描它们的 jar，把需要调整的部分（**尤其是 Fabric API 各个模块的 mixin 锚点** —— NeoForge 把原版的调用点搬过位置，这些锚点得跟着改）**生成一份修改后的副本放进缓存**（`.neofobric/` 目录里），之后就都从缓存加载。

- **第一次启动**：慢（还要叠上游戏自身的首次资源加载）；
- **之后的启动**：和普通 NeoForge 差不多；
- **★ 只要动过模组集（加、删、换任意一个模组），这份缓存整体失效，下次启动会再处理一遍** —— "刚换完模组第一次慢、之后快"就是它的正常节奏，不是卡死；
- 想强制跳缓存：加 JVM 参数 `-Dneofobric.noPatchCache=true`（调试用，会更慢）。

## 已知不兼容 / 已知缺口

**FFAPI（Forgified Fabric API）不能同装** —— 它是 NeoForge 模组，和桥自带的那份真 Fabric API 携带**同一批包**（`net.fabricmc.fabric.*`）⇒ 同一个模块层里两个模块导出同名包 ⇒ JDK 直接拒绝启动，只能二选一。**Sinytra Connector 不能同装** —— 它和桥抢同一件差事（把 Fabric 模组带进 NeoForge）。另外：**"一起跑" ≠ "互相看得见"** —— 两边模组并排运行没问题，但"Fabric 模组直接依赖某个 NeoForge 模组的 API"（反之亦然）**不保证**。

**自定义 `FabricRenderPipeline` 不可用**：26w14a 的 `RenderPipeline.Builder` 签名与 26.1.2 不同，`fabric-rendering-v1` 的 `RenderPipelineBuilderMixin` 注入器对不上，桥把它从配置里剔除了；常规渲染和 GUI 的 picture-in-picture 注册不受影响。

**Fabric 侧的自定义 blockstate 模型类型还没接上**：26.1.2 线在 `BlockStateModelHooks` 里加了 `fabric:type` 分派和 `registerFabricModelType`，26w14a 上还是平台原版形状 —— Fabric API 的 `fabric-model-loading-api-v1` / `fabric-renderer-api-v1-testmod` 客户端入口点会 `NoSuchMethodError` 被跳过（游戏照常启动，用到该功能的模组模型会缺失）。

**模组版本要按 26.1.2 系来**：不要把 26.2 / 26.3 构建的模组丢进来 —— 这个分支的底座是 26w14a + 26.1.2 兼容层，不是 26.2。26w14a 本身没有模组生态。

## 反馈

**欢迎报 bug —— 趁 token 还没烧完，收到就会修**（在本仓库开 Issue，或者直接找作者）。修得快慢另说，但会修。报的时候如果方便，带上：游戏版本 + 加载器版本 + 模组清单 + `logs/latest.log`（或者崩档报告），能省很多来回。

## 许可与出处

见 [README-LICENSE.md](README-LICENSE.md)：**我们自己写的部分**沿用 NeoForge 的 `LGPL-2.1-only`；内嵌的 Fabric API 模块与 fabric-loader 是 `Apache-2.0`（各自保留声明）；我们**没有复制** FFAPI（Apache-2.0）或 Sinytra Connector（MIT）的代码，只在注释里引用它们公开的做法，并已在许可文件里致谢。

---

## 关于这个项目

**这是个 AI 项目**（人定方向、验结果；代码、文档、排查过程大量由 AI 完成），所以它是一坨"史"，我们自己也这么叫它。但我们对这一坨有要求：**它得比 Forbric 那坨好** —— 好在它用的是**真正的 fabric-loader**（不是重新实现一遍 Fabric API 的语义）、**不丢掉 NeoForge**（底座还在、NeoForge 模组照常跑、不装 Fabric 模组时它就是普通 NeoForge），并且把上游 **Fabric API 自己的测试流程**跑完了（本分支服务端 100/100），还把结果原样写在这里。

**再强调一遍：这是一坨 AI 写的史。** 它**肯定不如信雅互联（Sinytra Connector）+ FFAPI** —— 人家是认真做的工程，我们是从正经路子旁边岔出去的一坨。想要"正经"的 NeoForge ↔ Fabric 互通方案，请去看 **Sinytra Connector + FFAPI**；我们这条纯属自娱自乐的一条岔路。

---

## English

**NeoFobric** is a bridge that lets fabric-loader run inside a NeoForge instance. This branch (`26w14aCp26-Neofobric`) sits on **Minecraft 26w14a** (this year's April-Fools snapshot, derived from 26.1.2) plus this repository's **`26w14a-Cp26.1`** compatibility line, which adds the call sites that let **26.1.2 NeoForge mods** install on 26w14a. The bridge then pulls the real **fabric-loader** (compiled from this repository's `fabric-loader` submodule) into the same process, so Fabric mods are loaded the Fabric way: `fabric.mod.json`, entrypoints, nested jars, access wideners / class tweakers and Fabric mixin configs.

Three shapes therefore coexist here: 26w14a's own vanilla call sites and April-Fools behaviour; 26.1.2's "pseudo-vanilla" call sites added by Cp26.1 for 26.1.2 mod mixins; and the NeoForge platform semantics. With no Fabric mod installed, this is plain NeoForge with the Cp26.1 compatibility layer. Version: **`26.1.26w14a.126-26w14a-NeoFobric`**.

**Fabric API**: 26w14a ships 0.145.2, which is too old and too thin for 26.1.2-era mods. The bridge therefore reports the game version as **26.1.2** (`neofobric.fabricGameVersion`) to Fabric's dependency checks, and the test line targets **Fabric API 0.155.3+26.1.2**. The 0.145.2 test set is kept under `projects/neoforge/run/{client,server}/mods-145-*` for comparison runs.

**State: a playable-but-don't-trust-it beta.** Verified here: the title screen and mods menu (split mod count, measured `46 mods: NeoForge 2 + Fabric 44`; Fabric rows with Loader chips, folded child mods); Fabric API's server suite **`All 100 required tests passed`**; the client suite runs through the client tag, tag-removal, command, dynamic-registry-sync, singleplayer client-tag and remove-entry-defaulting cases, failing only `clientTagDedicatedServerTests` (after the dedicated server removes a built-in pack and reloads, the client still sees the old tag contents — `minecraft:dirt` remains in `minecraft:sword_efficient`). That case is green on the 26.1.2 line; on 26w14a it is the one remaining difference. Not done: the 432-mod set was not re-run on this branch; the production installer chain was not separately tested; no multi-device testing.

**Known gaps**: custom `FabricRenderPipeline` construction is unavailable (`RenderPipelineBuilderMixin` is dropped because 26w14a's `RenderPipeline.Builder` signature differs); Fabric's custom blockstate model types are not wired yet (26.1.2's `BlockStateModelHooks.registerFabricModelType` / `fabric:type` dispatch has not been ported, so `fabric-model-loading-api-v1`'s client entrypoint is skipped with `NoSuchMethodError`). FFAPI (Forgified Fabric API) and Sinytra Connector cannot be installed alongside this bridge (same packages / same job). Use 26.1.2-built mods; do not drop 26.2/26.3 mods into it.

**Install & build**: build with `./gradlew :neoforge:installerJar`, install the resulting `26.1.26w14a.126-26w14a-NeoFobric` version, then drop both Fabric and 26.1.2 NeoForge mods into `mods/`. The first launch is slow: the bridge rewrites the Fabric jars it needs (mixin anchor realignment) into `.neofobric/` and reuses that cache afterwards; any change to the mod set invalidates the cache. `-Dneofobric.noPatchCache=true` forces a rebuild for debugging.

**Licensing**: see [README-LICENSE.md](README-LICENSE.md). Our own code is `LGPL-2.1-only`; embedded Fabric API modules and fabric-loader stay `Apache-2.0`. No code was copied from FFAPI or Sinytra Connector.

**About**: an AI-assisted project built for fun — a pile of garbage that has to be a better pile of garbage than Forbric's. For a serious NeoForge ↔ Fabric interoperability solution, use **Sinytra Connector + FFAPI**.
