# NeoFobric

**中文（默认）** | [English](#english)

> **一个拿来自娱自乐的加载器：是一坨"史" —— 但我们就是要它比 Forbric 那坨史好。**
> A loaders experiment built purely for fun: a pile of garbage — but it has to be a *better* pile of garbage than Forbric's.

对照物 / For comparison: <https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader>

---

## 这是什么

**在 NeoForge 加载器里搭了一座桥，让 Fabric 能一起跑。** 底座是 NeoForge（本仓库是 **NeoForge 26.3** 的 fork，当前版本 **`26.3.0.100`**）：FML 依旧是宿主 —— 它启动、它拥有游戏本体、它管模块层和它自己的模组。桥负责把 **fabric-loader 本体**（由本仓库的 `fabric-loader` 子模块现场编译）拉进同一个进程，Fabric 模组按 Fabric 自己那套规矩加载：`fabric.mod.json`、入口点、嵌套 jar、access widener / class tweaker、Fabric mixin 配置，一样不少。这里**没有 Forge** —— 已经"似"掉的那个旧 Forge 不参与任何环节。**不装任何 Fabric 模组时，它就是完完全全的 NeoForge。** 装模组时**优先选 NeoForge 版**，只有 NeoForge 版不存在时再用 Fabric 版：桥接的兼容性是"能跑"级别的，不是"保证"级别的。

## 现状：请当作「能玩但别当真」的测试版

bug 可能还有很多，兼容性也可能还不够好，**甚至无法和 FFAPI（Forgified Fabric API）一起用**（见下）。**服务端可以用** ✓ —— 进程内专服与生产安装出来的专服都实测过：能起到 `Done`，模组正常加载；**带上 bclib/wover 全系的专服**也起来了（这曾撞上一个注册表冻结窗口问题，已修）。**其他设备理论都能装**：凡是能装 NeoForge 26.3 + Fabric 的环境（Windows / Linux / macOS，各种启动器）理论上都可以 —— **但我们只在开发机上验证过，没有做多设备验证**，请你自己试。**机械动力·飞跃版（Create Fly）的部分特性可以正常使用**，但肯定还有我们不知道的 bug；**voxy（含 voxy-extra）可玩**，**「Fabric 钠 + NeoForge Iris」与「NeoForge 钠 + Fabric Iris」两种混搭也都实测可进世界**；**其他 Fabric 模组同理**：能进游戏 ≠ 每个功能都对；**NeoForge 模组也可能被连锁效应波及**（桥改的是两边共用的那一层）。

### 26.3（`26.3.0.100`，2026-10-28）

- **底座换成 Minecraft / NeoForge 26.3**（FML 12.0.12、Fabric Loader 0.20.1、Fabric API 0.165.0+26.3）；`fabric-loader` 仍由本仓库子模块现场编译。
- **416 件 26.3 模组集**实机进世界：暮色森林 Re26、Sodium/Iris、Voxy 全家、FrozenLib、FancyMenu、EMF/ETF/ESF、ViaFabricPlus、JER、Create Fly、Alex's Mobs、Twilight Forest 等；没有 26.3 版本的模组已剔除（例如部分内容模组）。
- **回归**：Fabric API 服务端套件 **112/112** 通过；客户端全套 gametest **BUILD SUCCESSFUL**（含截图对比、动态注册表同步）。
- **26.3 这一轮修掉的典型问题**：
  - FML 12 把 `ModConfig.Type.COMMON/SERVER` 改成 `LOCAL/SYNCED`，旧模组入口点会 `NoSuchFieldError`（暮色森林、Carry On、Particular、UntitledDuck、YDM 等）—— 桥在 jar 与 class 两层改写常量引用。
  - `RegistryDataLoader` 在静态初始化时就捕获了三份注册表列表，`NewDatapackRegistryEvent` 之后注册的注册表没进加载列表（表现为 "Missing registry" / 存档进不去）—— 加载点合并实时列表后恢复（日志：`Appended 55 datapack registries`）。
  - `BiomeModifier.modify` 新增 `RegistryAccess` 参数，旧三参实现会 `AbstractMethodError` —— 默认方法转发。
  - FancyMenu 在早期窗口回调（`EarlyWindowHandoff`）里访问尚不存在的 `gui` 导致启动 NPE —— `framebufferSizeChanged` 在 `gui == null` 时跳过 `resizeGui`。
  - FrozenLib 把 duck 接口注入到 `StructurePlacement` **接口**上，暮色森林直接实现该接口的 `LandmarkGridPlacement` 继承了三个抛 `AssertionError` 的默认方法，动态注册表事件把建世界打死 —— 把默认方法改成"无条件"语义。
  - Alex's Mobs 的多部件实体在结束追踪时，平台读 NeoForge 的 `PartEntity[]` 默认值（null）导致客户端断线 / 服务端区块卸载 NPE —— 结束追踪与开始追踪一样走桥接取实体自己声明的 `getParts()`。
  - 同一个模组装了**两个版本**时（例如 Sodium 0.9.2 + 0.9.3），旧副本的类会被隐藏，但它的 **mixin 配置以前仍会先注册**：0.9.2 的 `sodium-common.mixins.json` 配 0.9.3 的类，资源重载时死于 `Illegal classload request for accessor mixin ... missing from sodium-common.mixins.json`。现在旧副本的配置也一起跳过。
  - **ShoulderSurfing** 自建的两条半透明盔甲管线不在 Iris 的原版管线映射里，光影下会报 `Missing program shouldersurfing:pipeline/armor_translucent_no_cull...`；桥现在给这两条补上 `ENTITIES_TRANSLUCENT` / `ENTITIES_TRANSLUCENT_GLINT` 映射，警告消失、盔甲按实体半透明程序渲染。
- **26.3 光影提示**（不是桥的问题，但会看到"玩家头顶灰色碎片"）：
  - 26.3 换了透明/粒子渲染管线，**旧光影包**会被 Iris 标成 `Outdated`，缺少 `minecraft:pipeline/opaque_particle` / `translucent_particle` 的覆盖程序，粒子会渲染成巨大的灰色四边形。请使用 **26.3 版本的光影包**（例如 Complementary **r5.9.3**）。
  - Iris **1.11.x** 在 26.3 上仍有上述粒子管线 bug（日志 `Missing program ... likely an Iris bug`）；**Iris 1.12 快照已修**。
  - **Voxy 0.2.20-beta 声明与 Iris 1.12+ 不兼容**（`breaks iris >=1.12-`），目前 **Voxy 与 Iris 1.12 只能二选一**：要光影正常就用 Iris 1.12（不带 Voxy），要 Voxy 就退回 Iris 1.11.x（接受粒子碎片）。即使把 jar 里那条 `breaks` 删掉强行启动，**Iris 1.12 下 Voxy 的 LOD 会渲染成纯白色**（它的光影集成只适配 1.11.x）。26.2 时代本仓库的 voxy fork `0.2.21-beta` 修过 Sodium/Iris 兼容，但 **26.3 的对应构建暂未发布**。

## 反馈

**欢迎报 bug —— 趁 token 还没烧完，收到就会修**（在本仓库开 Issue，或者直接找作者）。修得快慢另说，但会修。反正不管最后修成什么样，**它都还是比 Forbric 那坨史好**。报的时候如果方便，带上：游戏版本 + 加载器版本（安装器自报的那个号）+ 模组清单 + `logs/latest.log`（或者崩档报告），能省很多来回。

### 我们跑了什么测试

**26.3（`26.3.0.100`）**：Fabric API 服务端套件 **`All 112 required tests passed`** ✓；客户端套件跑到收尾 **`BUILD SUCCESSFUL`**（截图对比、动态注册表同步、生命周期/命令等用例全过）；416 件 26.3 模组集实机进世界（`Dev logged in with entity id`）。以下 ①–⑩ 是 26.2 时期的完整记录，保留作历史。

**Fabric API 仓库自带的测试流程**跑完了：服务端套件 **`All 114 required tests passed`** ✓ —— 上游那套 gametest 全量 114 条必测项全绿，这是本仓库最硬的一条证据。客户端套件同样跑到收尾，**唯一没过的是上游测试用例自己内部互斥**（`fabric-tag-api-v1-testmod`：同一个数据包既要给 `sword_efficient` 加 `dirt`、又要移除另一个标签的内容，两条断言要求同一个包"在/不在"，本环境无解）—— 记档的已知差异，不算桥的缺口。桥内置自检：事件清单在客户端 **11/11 wired** ✓，专服没有玩家也能打出报表 ✓。生产安装链：用本仓库构建的安装器装出来的**专服**能起到 `Done`、**客户端**能进游戏。

之后又补跑了几轮把上面这条坐实：① **Fabric API 官方测试包**在客户端重跑 —— 世界正常加载、`LifecycleEventsTest` 跑到 3400+ tick、入口点失败 / 编码失败 / 崩档全部 0；② **专服**带 bclib/wover 全系起服（修复前会崩在注册表冻结窗口，见上）；③ 桥自检新增两条硬证据 —— **Fabric 通道协商读数**（单机 74/41 对 41/74、联机同样对镜；任何"这边有接收器、对面没协商过"的通道都会被点名警告）和**真·payload 往返探针**（现场生成的 payload 走完客户端 → 服务端 → 客户端，判词 `fabric payload round trip ok`）；④ **NeoForge 自带模组列表**现在也给 Fabric 模组显示更新（跑 Mod Menu 的检查器 + 直连 Modrinth 的哈希回退）。装了 c2me 的模组集里事件自检读数是 **10/11**，静默的那条是该模组重写区块系统后的已知行为（文档有档，与桥无关）；⑤ **2026-10-14**：当时列表里的**完整 90 件套**实机进世界并游玩（世界生成全家桶、Create、AE2、TechReborn 等），同日修掉两批"进世界 / 掉线"问题 —— **世界加载注册表链**（"存档进不去 / 数据包错误"）与 **polymer 握手通道 + 26.2 实体常量迁移**（"连接丢失"）；另外 **Architectury 的 Fabric 版现在也能启动并进世界**（按 Fabric 编译、靠 `LocalCapture` 抓局部变量的 mixin，此前会被平台补丁的形状差异打死）。这几项已随 **26.2.0.128** 发布。**2026-10-15/16**：把这套**完整 98 件套**（列表已更新）实机跑通 —— 进世界、挖方块、**掉落物拾取**、**创造飞行切生存正常摔落**全部通过；Fabric API 官方测试包在客户端再次全绿（`LifecycleEventsTest` 跑到 6000+ tick，断言 / 编码 / 崩档全 0）；同时修掉三个深坑：**桥把 NeoForge 模组自己的自定义包误判成 Fabric 通道**（modernfix 的加载挂起哨兵包被丢弃，服务端整局冻结 —— 表现为掉落物捡不起来、创造切生存在空中不落）、**分包器测量绕过了编码器的包上下文**（polymer 类模组在 `level_event` 上掉线）、**创造模式物品栏在未注册 tab 上崩溃**（polymer 模组集）。已并入 **26.2.0.129**。⑥ **2026-10-15/17**：**ViaFabricPlus 5.0.2（连接任意版本服务器的客户端模组）全套实机跑通并纳入列表**（现 100 件套）——修掉的层：Fabric jar 内置 MixinExtras 与平台模块的层冲突（启动即死）、其版本切换的重载任务撞冻结窗口、其工具提示喂原始版本对象撞翻译参数校验（dev）、其 1.12.2 放方块 lambda 覆写的可见性；另修 polymer 页面字段（VFP GUI 后的重载卡死）与分包器/资源重写的栈尺寸。已并入 **26.2.0.130**。⑦ **2026-10-19**：**两个 Sodium 构建现在都能和 Continuity 配合** —— Fabric 版继续走原生 FRAPI emitter；NeoForge 版走默认 emitter 的 level-aware 5 参 `collectParts`（helper 注入 Continuity 自己的 jar，Fabric `WrapperBlockStateModel` 把该调用透传下去，Iris 不再吞上下文）。全模组 101 件 + Iris 光影开/关、最小 Neo/Fabric 集均实测玻璃整面连接、书架连续。同期把点名兼容归档到 `compbridlnk/`，并修复 Jade×花盆 NPE。已并入 **26.2.0.131**。⑧ **2026-10-24/25**：模组集扩到 **463 件**（NeoForge + Fabric 混装；c2me/OpenCL、Sodium、Iris 带光影、Voxy LOD、Create、TFC 兼容层等都在内）实机进世界游玩 —— 本轮修掉：**平台模组暴露数据补全**（camerapture 内嵌 tapestry 的注册回滚与适配器风味、LambDynamicLights 经 Yumi 读 jar 内默认配置）、**fabric-registry-sync 的急切方块状态缓存重建**（Occultism 等注册中途炸）；定位"进世界后一片空白"的元凶为 **Noxesium 卡客户端区块加载**（模组侧，已停放），并把原版 Fabric 版 **visuality 经桥替换回归**（NeoForge 移植版因注入点过时崩）。已并入 **26.2.0.132**。⑨ **2026-10-25**：模组集扩到 **502 件** 并再次进世界 —— 本轮修掉：**放宽副本把 `[[accessTransformers]]` 的 `file =` 行也一起删了**（FancyMenu 的 AT 声明因此丢失；现在只清理 `[[jars]]` 段）、以及 **Jade 缺翻译断言的真凶**（inventory_particles 的 `AtlasLoaderMixin` 把 `forEach` 注入点挂在 26.2 的双参 `SpriteSourceList.list` 上，而该调用只留在单参重载里，注入 0/1 打死整次资源重载，所有语言文件遂未加载；日志里的 `zip file closed` 只是重载中途流产后的连带现象）。停放的都是模组自身问题：inventory_particles（注入点选错重载）、enderscape（启动时遍历全注册表构造方块实体，撞上 EasyAnvils / VisualWorkbench 的共享 BE 类型不覆盖生成的变体方块）、status-effect-bars、xaerozoomout、do_a_barrel_roll。实机读数：686 个 Fabric 侧模组加载、加载失败 0、客户端已加载区块 861，进世界截图确认近处方块、树、结构、手持物品全部正常渲染。已并入 **26.2.0.133**。⑩ **2026-10-26**：继续补数据层与类调整的缺口（已并入 **26.2.0.134**）—— **`fabric:load_conditions` 现在对 NeoForge 的配方路径生效**（Fabric 的 resource-conditions 模块只包住 `scanDirectory`，NeoForge 的 `scanDirectoryWithModifier` 拷贝以前照常解析条件不满足的文件；接入后 502 件套的唯一解析失败 209 → 147）、**Fabric 模组的 `loot_modifiers` 与 `global_loot_modifiers` 索引不再被 NeoForge 的加载器误解析**、**`PotionBrewing.bootstrap` 只在拿到真实 registry access 时发事件**（Create 的早期 EMPTY 调用配上无守卫监听器，以前会把世界的数据加载打死）、**dev 客户端重复注册 game test 条目时容错**（只影响 dev，正式包不发该事件）、**`transitive-inject-interface` 的向下传播补全**（Create/FrozenLib/mru 等六件模组依赖它，158 条注入），以及 realign 注解遍历与类层级遍历的 6 条新单测（`fabricbridge` 现在 **11 条全绿**）。

### 实测能跑的模组（502 个，26.2 记录）

> 26.3 的 416 件套不逐条列出（太长）；代表模组见上面的「26.3」一节。下面这份完整列表是 26.2 时期的记录，仍可作为"这些模组类型在桥上能跑"的参考。

```
AccurateBlockPlacement-Reborn-1.4.8+mc-26.1-26.2
Adorn-9.0.0+26.2-neoforge
AdvancedCoreInfo-neoforge-26.2-1.5.0
AdvancedLootInfo-neoforge-26.2-2.5.0
AdvancementPlaques-26.2-neoforge-1.7.2
AgeingSpawners-26.2-12.0.0
AmbientEnvironment-neoforge-26.2-26.2.1
AmbientSounds_NEOFORGE_v6.3.6_mc26.2
ArmorStatues-v26.2.0-mc26.2.x-NeoForge
AsyncParticles-26.2.2.10+26.2
AttributeFix-neoforge-MC26.2-26.2.0.1
BarteringStation-v26.2.0-mc26.2.x-NeoForge
BetterAdvancements-NeoForge-26.2-0.6.0.72
BetterAnimationsCollection-v26.2.0-mc26.2.x-NeoForge
BetterF3-19.0.1-NeoForge-26.2
BetterThanMending-2.3.0-merged
BetterTridents-v26.2.0-mc26.2.x-NeoForge
BiomesOPlenty-neoforge-26.2-26.2.0.0.28
BridgingMod-2.7.0+26.2.neoforge-release
Chunky-NeoForge-1.5.4
Clumps-neoforge-26.2-26.2.1
ConfigManager-neoforge-26.1_26.2-1.1.3
ConfiguredDefaults-v26.2.0-mc26.2.x-NeoForge
Controlling-neoforge-26.2-26.2.4
CreativeCore_NEOFORGE_v2.14.16_mc26.2
CreeperFirework-neoforge-3.0.0
CustomPlayerModels-26.2-0.6.27c
CustomSkinLoader_Universal-15.1
CutThrough-v26.2.0-mc26.2.x-NeoForge
DarkPaintings-neoforge-MC26.2-26.2.0.1
DeleteWorldsToTrash-v26.2.0-mc26.2.x-NeoForge
DiagonalFences-v26.2.0-mc26.2.x-NeoForge
DiagonalWalls-v26.2.0-mc26.2.x-NeoForge
DiagonalWindows-v26.2.0-mc26.2.x-NeoForge
Drip Sounds-0.6.3+26.2-NeoForge
EasyAnvils-v26.2.3-mc26.2.x-NeoForge
EasyMagic-v26.2.0-mc26.2.x-NeoForge
EasyShulkerBoxes-v26.2.3-mc26.2.x-NeoForge
EchoChest-v26.2.0-mc26.2.x-NeoForge
Ecologics-NeoFab-26.2-2.6.3
EnchantingInfuser-v26.2.0-mc26.2.x-NeoForge
EnchantmentDescriptions-neoforge-MC26.2-26.2.0.2
EternalNether-v26.2.0-mc26.2.x-NeoForge
EuphoriaPatcher-1.10.5-r5.9.3-neoforge
ExplorersCompass-26.2-3.3.0-neoforge
ExtremeSoundMuffler-4.04_NeoForge-26.2
FallingTree-26.2-25
FarmersDelight-26.2-3.6.28+refabricated
Female-Gender-Mod-5.0.0+mc26.2-neoforge
ForgeConfigAPIPort-v26.2.1-mc26.2.x-Fabric
FriendlyFire-neoforge-MC26.2-26.2.0.1
FrozenLib-3.0.3-mc26.2-fabric
GlitchCore-neoforge-26.2-26.2.0.0.0
Highlighter-26.2-neoforge-1.2.2
HopoBetterUnderwaterRuins-_26.2_-1.2.8
HorseExpert-v26.2.1-mc26.2.x-NeoForge
HypixelModAPI-1.0.2+build.1+mc26.1
IAS-9.0.8+26.2-neoforge
IMBlocker-7.3.2-neoforge-26.1+
Iceberg-26.2-neoforge-1.4.2.2
IllagerInvasion-v26.2.0-mc26.2.x-NeoForge
ImmediatelyFast-NeoForge-1.16.5+26.2
InvMove-0.9.6+26.2-NeoForge
InventoryProfilesNext-neoforge-26.2-2.3.8
ItemBorders-26.2-neoforge-1.3.2
ItemLocks-Fabric-26.2-1.3.14
ItemPhysicLite_NEOFORGE_v1.6.12_mc26.2
Ixeris-4.6.8+26.2-neoforge
Jade-mc26.2-Fabric-26.2.11
JustEnoughProfessions-neoforge-26.2-12.0.0
JustEnoughResources-NeoForge-26.2-1.11.0.44
KotlinLangForge-2.16.0-k2.4.21-3.1+neoforge
LeaveMyBarsAlone-v26.2.0-mc26.2.x-NeoForge
LegendaryTooltips-26.2-neoforge-1.6.2
LongerChatHistory-neoforge-1.8
MagnumTorch-v26.2.0-mc26.2.x-NeoForge
MindfulDarkness-v26.2.0-mc26.2.x-NeoForge
MobLassos-v26.2.1-mc26.2.x-NeoForge
MoogsEndStructures-universal-1.21-2.1.1
MoogsNetherStructures-universal-1.21-3.1.1
MoogsSoaringStructures-universal-1.21-2.2.0
MoogsStructureLib-neoforge-26.2-3.4.2
MoogsVoyagerStructures-universal-1.21-5.1.3
MoreSpearEnchantments-fabric-1.1.2+26.2
MossyLib-1.6.0+26.2+neoforge
MouseTweaks-neoforge-mc26.2-2.31
MutantMonsters-v26.2.2-mc26.2.x-NeoForge
NaturesCompass-26.2-3.3.0-neoforge
Neat-26.2-50-NEOFORGE
NetherChested-v26.2.0-mc26.2.x-NeoForge
NoChatReports-NEOFORGE-26.2-v2.20.2
NoChatRestrictions-NeoForge-MC26.2-v1.2.3
Nullscape_26.2_v1.2.20
OverflowingBars-v26.2.0-mc26.2.x-NeoForge
PacketFixer-fabric-3.3.6
Paraglider-neoforge-26.2.1
ParticleEffects-1.6.0+26.2+neoforge
PatPat-1.3.2+26.2+neoforge
PickUpNotifier-v26.2.0-mc26.2.x-NeoForge
Ping-Wheel-1.12.1-neoforge-26.2
PlayerAnimationLibMerged-1.2.8+mc.26.2
PlayerRevive_NEOFORGE_v2.1.2_mc26.2
PresenceFootsteps-1.13.3+26.2
PrickleMC-neoforge-MC26.2-26.2.0.3
Prism-26.2-neoforge-1.1.2
ProPlacer-v26.2.0-mc26.2.x-NeoForge
RebornCore-6.1.1
ResourcefulConfig-5.0.1
ResourcefulLib-5.0.4
ScalableLux-neoforge-0.3.0-alpha.0.16-all
Searchables-neoforge-26.2-1.0.1
SereneSeasons-neoforge-26.2-26.1.2.0.6
ShoulderSurfing-NeoForge-26.2-5.2.0
SimpleDiscordRichPresence-neoforge-26.2.0.1
SkeletonAIFix-v26.2.0-mc26.2.x-NeoForge
SmoothScrollingRefurbished+26.2-1.9.0
SnowUnderTrees-2.7.6+26.1
SpiderOverhaul-0.0.6-NeoForge-v26.2
Stellarity-6.0.1
Stoneworks-v26.2.0-mc26.2.x-NeoForge
StorageDrawers-neoforge-26.2.0.1
TechReborn-6.1.1
TerraBlender-neoforge-26.2-26.2.0.0.2
Terralith_26.2_v2.6.4_Neoforge
TinySkeletons-v26.2.1-mc26.2.x-NeoForge
ToolStats-neoforge-MC26.2-26.2.0.1
TradingPost-v26.2.0-mc26.2.x-NeoForge
Trim Patcher-2.1-mc26.2-neoforge
UniversalBoneMeal-v26.2.0-mc26.2.x-NeoForge
VehicleUpgrade-v26.2.0-mc26.2.x-NeoForge
ViaFabricPlus-5.0.2
VoxyServer-1.2.4-26.2
WilderWild-4.3.3-mc26.2-fabric
XaeroPlus-2.37.1+neoforge-26.2-WM1.47.1-MM26.6.1
[26.2+]MYEC-v1.10.1-NeoForge
[Neoforge]ctov-26-2-4.1.0
_26.2_SecurityCraft_v1.10.2.1
abridged-2.0.2-neoforge-26.2
adorabuild-structures-2.11.0-neoforge-26.2
advancementdisable-neoforge-1.1.2+26.1.2
ae2wtlib-26.2.0-beta
alexsmobs-2.2.4-fabric+26.2
amcdelight-1.0.1-fabric+26.2
amecs-bundle-1.6.5+mc26.2-pre-2
animal_feeding_trough-1.2.1+26.2-neoforge
animatica-0.6.3+26.2
appleskin-neoforge-mc26.2-3.0.10
appliedenergistics2-26.2.1-alpha
archers-neoforge-3.1.3.003+26.2
architectury-neoforge-21.1.12
armor-stand-arms-v2.7.1
armor_model_api-neoforge-1.1.2+26.2
armored-elytra-1.15.1
authme-neoforge-9.3.0+26.2
baguettelib-26.2-NeoForge-2.0.7
bagus_lib-26.2-27.7.0
balm-neoforge-26.2-26.2.0.9
bbe-neoforge-1.3.8-beta.1+mc26.2
bclib-26.201.3
beautify-fabric-3.0.0+26.2
better-compatability-checker-neoforge-26.2.0.1
better-compatibility-checker
better-end-26.201.3
better-nether-26.201.3
better-selection-mc-1.9.1
better_climbing-neoforge-6
better_end_sky-0.3.1+26.2
betterarcheology-neoforge-26.2-1.3.9
bettercombat-neoforge-3.2.2+26.2
betterf1-neoforge-26.2-1.3
betterhurtcam-neoforge-1.14.0+mc26.2
bettermounthud-1.3.1
betterstats-5.5.6+fn-26.2
bettervillage-neoforge-26.2-4.0.1
biolith-neoforge-3.7.0-beta.1
biomereplacer-3.1-newt-neo
blur-neoforge-6.3.1+26.2
boatiview-neoforge-0.0.9-26.2
bobby-5.2.15+mc26.2
boids-2.0.0+26.3
bookshelfinspector-neoforge-2.4+26.2
bundle-api-neoforge-4.0.0+26.2
byepregen-neoforge-26.2-1.1.2.5
c2me-fabric-mc26.2-0.4.2-alpha.0.60
c2me-fabric-opts-accel-opencl-mc26.2-0.4.2-alpha.0.60
calcmod-1.5.2+neoforge.26.2
camerapture-1.10.15+mc26.2
carryon-neoforge-26.2-2.11.2
chat_heads-1.3.2-neoforge-26.1
chatanimation-neoforge-1.3.5+mc26.2
chattools-v2.4.5+26.2-neoforge
chefsdelight-1.0.6-fabric-26.2
cherishedworlds-neoforge-17.0.0+26.2
chunkloaders-1.2.9a-neoforge-mc26.2
cicada-lib-0.15.3+26.2
cinderscapes-6.1.0-alpha.2
clickthrough-plus-3.6.5+26.1.2-neoforge
clienttweaks-neoforge-26.2-26.2.0.6
cloth-config-26.2.155-neoforge
cobweb-neoforge-26.2-1.4.0
codxlib-1.6.1-fabric+26.2
collective-26.2.0-8.62
colorfulhearts-neoforge-26.2-26.2.0.0
comforts-neoforge-16.0.0+26.2
commandkeys-neoforge-3.103.2+26.2
configmanager
connectedglass-1.1.14a-neoforge-mc26.2
connectiblechains-2.4.4+26.2-neoforge
constructionwand-2.1.3+26.2
constructionwandpolymer-1.0.1
continuity-3.0.1+26.2
controlify-3.5.3+mc26.2-universal
cookingforblockheads-neoforge-26.2-26.2.0.5
coolrain-1.4.0-26.2
corpse-neoforge-1.1.21+26.2
craftingtweaks-neoforge-26.2-26.2.0.3
cratedelight-26.07.01-26.2-fabric
crawl-mc26.2-neoforge-0.15.0
create-fly-26.2-6.0.9-2
create_ultimate_factory-2.2.4-fabric-26.2
cristellib-neoforge-26.2-3.1.13
crosshairindicator-1.0.3
curios-neoforge-16.0.0+26.2
custom-crops-1.1.1+26.2
cwb-4.1.0+26.2
dark-loading-screen-1.6.19
decorative-lamps_NeoForge-26.2-1.8.1
default-options
delightlib-26.06.23-26.2-fabric
difficultylock-26.2.0-5.2
dismountentity-26.2.0-3.8
display-delight-1.8.4-mc26.2
doubledoors-26.2.0-7.5
durabilitytooltip-1.2.0a-neoforge-mc26.2
dynamiccrosshair-9.14+26.2-fabric
e4mc-neoforge-6.2.3
earthmobsmod-26.2-30.5.0
easyelytratakeoff-26.2.0-4.6
effecttimerplus-fabric-3.103.1+26.2
elevatorid-neoforge-26.2-1.16.4
elytra-chestplate-swapper-2.1.0-MC26.2-neoforge
elytratrails-1.6.7-26.2
elytratrims-neoforge-4.9.1+26.2
emoji-type-3.1.0-26.2-neoforge
emotecraft-for-MC26.2-3.4.0-b.build.170
enchanted-vertical-slabs-2.7.0-neoforge-mc26.2
enchantment-effects-1.1.0+26.2
enderitemod-neoforge-26.2-1.9.1
enderscape-fabric-3.0.3+mc26.2
endrem-neoforge-26.2-6.2.1
entity_collision_optimizer-1.0.0-mc26.2-alpha.10
entity_model_features-3.3.12-26.2-neoforge
entity_sound_features-0.8.2-26.1-neoforge
entity_texture_features-7.2.6-26.2-neoforge
entityculling-neoforge-1.11.3-mc26.2
exdeorum-5.1
explorations-neoforge-26.2-1.6.4
explosive-enhancement-1.4.2-26.2
fabric-api-0.161.0+26.2
fadeless-2.0.8-26.2
fallingleaves-26.2-3.1.0-beta
fancymenu_neoforge_3.9.14_MC_26.2
farm-3x3-2.4.1
farm-3x3-farmers-delight-compatibility-2.4.X
fast-ip-ping-v1.0.12-mc26.2
ferritecore-9.0.0-neoforge
firstperson-neoforge-2.7.3-mc26.2
fish_of_thieves-mc26.2-v26.2.1.2-neoforge
forgivingvoid-neoforge-26.2-26.2.0.2
formations-1.0.4a-neoforge-mc26.2
formationsnether-1.0.5b-mc1.21+
formationsoverworld-1.0.5c-mc1.21+
fpsdisplay-5.1.0+26.2
framework-neoforge-26.2-0.13.26
freelook-1.4.3
friendsandfoes-neoforge-5.0.3+mc26.2
fullbrightnesstoggle-26.2.0-4.9
fusion-1.3.16-neoforge-mc26.2
fzzy_config-0.7.7+26.2+neoforge
gazebo-neoforge-2.2.0+26.2
geckolib-neoforge-26.2-5.5.6
globalpacks-neoforge-26.2-26.2.1
glowing-torchflower-neoforge-mc26.2-1.4.1
goldenhopper-neoforge-26.2-1.7.0
gravestone-neoforge-1.0.40+26.2
grind-enchantments-4.2.2+26.1.2
guardvillagers-4.0.4-26.2.0
guideme-26.2.2-alpha
hardcorerevival-neoforge-26.2-26.2.0.6
harvest-with-ease-neoforge-26.2-9.4.0
healingcampfire-26.2.0-6.4
held-item-info-1.9.2
ijm-tweaks-1.8.0+26.2
immersive_aircraft-neoforge-1.7.0+26.2
immersive_armors-1.8.2+26.2-neoforge
immersive_melodies-neoforge-0.8.2+26.2
immersive_paintings-neoforge-26.2-0.7.9
immersiveui-neoforge-26.2-0.3.6+26.2
improved-village-placement-1.2.0-neoforge-26.2
incontrol-26.2-11.0.0
inventoryessentials-neoforge-26.2-26.2.0.6
inventoryhud.neoforge.26.2-3.4.34
inventorytotem-26.2.0-3.6
invisibleframes-neoforge-2.0.1+26.2
iris-fabric-1.11.4+mc26.2
ironchest-26.2-neoforge-16.8.3
itemscroller-fabric-26.2-0.32.2
jamlib-neoforge-2.3.1+26.2.x
jecharacters-26.1.2-neoforge-4.6.9
jei-26.2-neoforge-30.42.0.236
jewelry-neoforge-2.5.1+26.2
jumpoverfences-neoforge-26.2-1.8.0
just-hammers-neoforge-26.2.0.1
justenoughbreeding-neoforge-26.2-3.4.1
justzoom_fabric_3.0.1_MC_26.2
kitchenprojectiles-1.0.13+mc26.2
kleeslabs-neoforge-26.2-26.2.0.1
konkrete_fabric_1.11.1_MC_26.2
kreno-neoforge-26.2-26.3.2-26.2
lambdynamiclights-4.12.4+26.2
language-reload-1.7.8+26.2
leawind_third_person-3.0.3-beta+neoforge-26.2
libIPN-neoforge-26.2-6.9.0
libjf-26.2.2+forge
libraryferret-neoforge-26.2-5.0.1
lighty-neoforge-4.0.1+26.2
litematica-fabric-26.2-0.28.8
lithium-neoforge-0.25.3+mc26.2
lithostitched-1.8.0-neoforge-26.2
locator-heads-2.4.2
lootr-neoforge-26.2-1.24.41.124
magic_vibe_decorations-3.0.1-neoforge-26.1.2-26.3
make_bubbles_pop-0.4.0-beta.3-neoforge-mc26.2+
malilib-fabric-26.2-0.29.6
man_of_many_planes-0.2.3+26.2-neoforge
marlows-crystal-optimizer-2.0.0+mc26.1-26.3-neoforge
mca-neoforge-8.1.11+26.2
mcw-bridges-3.1.2-mc26.2neoforge
mcw-doors-1.1.5-mc26.2neoforge
mcw-fences-1.2.1-mc26.2neoforge
mcw-furniture-3.4.2-mc26.2neoforge
mcw-holidays-1.1.2-mc26.2neoforge
mcw-lights-1.1.5-mc26.2neoforge
mcw-paintings-1.1.0-mc26.2neoforge
mcw-paths-1.1.1-mc26.2neoforge
mcw-roofs-2.3.3-mc26.2neoforge
mcw-stairs-1.0.2-mc26.2neoforge
mcw-trapdoors-1.1.5-mc26.2neoforge
mcw-windows-2.4.2-mc26.2neoforge
mcwifipnp-2.1.3-26.2-neoforge
melody_neoforge_1.0.17_MC_26.2
mezz_config-26.2-neoforge-0.6.8-standalone
midnightlib-neoforge-1.10.0+26.2
minihud-fabric-26.2-0.40.7
modernfix-neoforge-5.27.21+mc26.2
modmenu-20.0.3
modonomicon-26.2-neoforge-2.5.0
more-armor-trims-1.5.4-26.2
more-chest-variants-1.5.14+26.1.1-neoforge
morechathistory-2.0.0
moredelight-26.06.23-26.2-fabric
moretotems-neoforge-26.2-2.25.0
movesubtitles-1.3+26.1-fabric
mru-1.0.44+26.2-fabric
musicnotification-neoforge-3.4.0-alpha.1+mc26.2
mythicupgrades-neoforge-26.2-5.1.1
nanite-library-neoforge-26.2.0.2
naturalist-2.0.6-neoforge-26.2
nethermap-4.3.0+26.1
netherportalfix-neoforge-26.2-26.2.0.1
nicer-skies-1.5.0+26.2
no-report-button-1.8.0-neoforge
no-resource-pack-warnings-1.5.0
no-telemetry-1.10.0
notenoughanimations-neoforge-1.12.5-mc26.2
notenoughcrashes-neoforge-4.4.9+26.2
nuit-neoforge-1.0.0-beta.6+mc26.2
occultism-26.2-neoforge-1.256.0
oceansdelight-expanded-1.0.4+fabric.26.2
ok_zoomer-18.0.0-beta.3
owo-lib-0.13.1+26.2
packed_packs-neoforge-2.3.1+26.2
paladins-neoforge-3.1.3.003+26.2
particlerain-4.0.2+26.2-fabric
particular-26.2-NeoForge-1.5.7
perspective_api-1.5.0-beta+neoforge-26.2
plasmovoice-neoforge-26.2-2.2.0-beta.2
polytone-26.2-6.9.1-neoforge
prefab-neoforge-2.1.0
promenade-5.6.0
puffish_skills-0.19.2-26.2-neoforge
punchy-2.8g-neoforge-26.2
puzzleslib-v26.2.5-mc26.2.x+neoforge
raised-neoforge-26.2-6.0.2
ranged_weapon_api-neoforge-4.0.0+26.2
reacharound+26.2-1.4.2
realisticbees-26.2.0-4.4
reglass-26.2-2.1.1
replaymod-26.2-2.6.28
repurposed_structures-7.7.6+26.2-neoforge
rightclickharvest-neoforge-4.6.2+26.2.x
rogues-neoforge-3.1.3.003+26.2
runes-neoforge-1.3.2+26.2
rusticdelight-fabric-26.2-1.7.0
scholar-neoforge-26.2-1.2.10
sclp-neoforge-5.6.7-snapshot+mc26.1-local
screenshot_viewer-1.3.6-fabric-mc26.2
searchonmcmod-neoforge-26.2-0.1.1
seasonhud-neoforge-26.2-2.0.11
shatterlib-neoforge-0.7.0-beta.1+26.2
shogi-neoforge-26.2-26.2.0.8
show-me-your-skin-2.0.3+26.2
shulkerboxtooltip-neoforge-5.4.1+26.2
sit-neoforge-26.1.2-1.5.2
skinlayers3d-neoforge-1.11.4-mc26.2
skinshuffle-2.13.0+26.2-fabric
skyboxify-3.4+26.2-fabric
smooth-scrolling-refurbished
smoothgui-neoforge-2.1.0+mc26.2
smoothskies-2.12.2+26.2-fabric
smoothswapping-0.9.10-26.2-neoforge
sodium-extra-fabric-0.9.4+mc26.2
sodium-neoforge-0.9.2+mc26.2
sophisticatedbackpacks-26.2-3.26.10.2199
sophisticatedcore-26.2-1.5.11.2405
sophisticatedstorage-26.2-1.5.122.2186
sound-physics-remastered-neoforge-1.5.1+26.2
soundsbegone-neoforge-1.6.0+mc26.2
spanishdelight-26.2-1.0.11
spark-1.10.187-neoforge
spell_engine-neoforge-1.10.9+26.2
spell_power-neoforge-1.6.2+26.2
springlotus-neoforge-1.2.2+26.2
spyglass-astronomy
spyglass_improvements-1.6+mc26.2+neoforge
stackedblocks-26.06.24-26.2-fabric
stackedblocksfarmersdelight-26.06.24-26.2-fabric
stackrefill-26.2.0-6.2
starterkit-26.2.0-9.0
statuseffecttimer-2.0.0+26.2
stendhal-1.4.9-26.2
storagedelight-26.07.01-26.2-fabric
structure_layout_optimizer-1.1.4+26.1-neoforge
structure_pool_api-neoforge-1.3.0+26.2
structurify-neoforge-2.0.43+mc26.2
superflatworldnoslimes-26.2.0-3.7
supermartijn642configlib-1.1.8a-neoforge-mc26.2
supermartijn642corelib-1.1.25-neoforge-mc26.2
t_and_t-fabric-neoforge-1.13.11
tcdcommons-5.5.6+fn-26.2
tectonic-3.0.28-neoforge-26.2
terrestria-8.1.0-beta.2
tfc2mecompat-1.1.1+mc26.x-neoforge
the-graveyard-1.5.1
theurgy-26.2-neoforge-1.118.1
tiertagger-neoforge-2.6.0+mc26.2
titlefixer-1.1+26.2
toms_storage_fabric-26.2-2.11.4
toms_trading_network-26.2-0.3.16
tooltip-scroll-1.5.1+26.2
totemcounter-neoforge-1.13.0+mc26.2
totemtweaks-26.2-1.2.0
trade-cycling-neoforge-1.0.23+26.2
trashcans-1.1.1a-neoforge-mc26.2
trashslot-neoforge-26.2-26.2.0.2
treeharvester-26.2.0-10.3
trek-B0.6.2
tslatentitystatus-neoforge-26.2-1.10.2
tweakeroo-fabric-26.2-0.29.5
twilightforest.re26-26.2-4.8.4208-fabric
ubesdelight-fabric-26.2-0.4.15
ukulib-neoforge-2.1.1+26.2
ukus-armor-hud-neoforge-0.12.0+mc26.2
undergroundworlds-neoforge-3.2.1-26.2
untitledduckmod-neoforge-26.2-1.6.0
variantsandventures-neoforge-1.0.28+mc26.2
veinminer-client-neoforge-2.12.1
veinminer-neoforge-2.12.1
villagernames-26.2.0-8.8
visuality-fabric
voicechat-fabric-2.6.24+26.2
voxy-0.2.21-beta-f473097
voxy-extra-0.2.6
voxy-seam-fix-0.2.0
voxy-server-side-fabric
wakes-0.7.1+26.2
waveycapes-neoforge-1.11.3-mc26.2
waystones-neoforge-26.2-26.2.0.12
weaponmaster_ydm-neoforge-26.2-4.4.4
wits-1.4.2+26.1-neoforge
wizards-neoforge-3.1.3.003+26.2
worldweaver-26.201.3
xaerominimap-neoforge-26.2-26.6.1
xaeroworldmap-neoforge-26.2-1.47.1
xercapaint-26.2-1.0.0
xptome-neoforge-26.1.2-2.5.1
yeetusexperimentus-neoforge-102.0.0
yet_another_config_lib_v3-3.9.7+26.2-neoforge
yumi-commons-collections-2.0.0
yumi-commons-core-2.0.0
yumi-commons-event-2.0.0
yumi-mc-foundation-1.1.1+26.2
```

> 这一批能"进游戏并正常玩一阵" —— 不代表每个功能都对。

### 目前相对稳一点的方向

**更好的末地 / 更好的下界（bclib / Better End / Better Nether / WorldWeaver 这一系）**目前理论上是兼容更好的：末地**只验证到"能进末地、地形（可能）生成正常"** —— 功能方块、结构、生物等**没有测试**；**更好的下界现在能正常加载、它的结构（地狱城市）也能正常生成**（这正是 2026-10-11 那次崩溃修掉的 —— 之前是 `this.structure` 为 null）。越跨界的内容越可能出问题（跨加载器互相依赖、双方都要接管同一段原版逻辑的地方）。**另外实测跑通的组合**：**voxy 全系（voxy / voxy-extra / lss + 自带服务端）都能带起来**。

### 钠与 Iris 可以跨加载器混搭（不必同款）

**Sodium 和 Iris 不需要来自同一个加载器** —— 不必为了配套去换另一边，四种搭配全都实测走通：同加载器的两种（都用 NeoForge 版 / 都用 Fabric 版）是常规组合；**跨加载器的两种混搭也已验证可用**：

- **NeoForge 版 Sodium + Fabric 版 Iris** ✓ —— 实测（`sodium-neoforge-0.9.1+mc26.2` + Fabric 版 Iris 1.11.2，搭配 voxy / continuity / lss / sodium-extra 全系）：进世界、水面渲染正常、持续 2 分钟以上无崩溃，光影包（Complementary Unbound）正常加载。
- **Fabric 版 Sodium + NeoForge 版 Iris** ✓ —— 实测（`sodium-fabric-0.9.1+mc26.2` + `iris-neoforge-1.11.2+mc26.2`，同样带全部 Fabric 扩展）：进世界 40 秒以上稳定、水面渲染正常，光影包正常加载。

桥为此做了两件事：把钠两个构建的"加载器专属类"按实测类清单**双向改名对齐**（`sodium.fabric.*` ↔ `sodium.neoforge.*`），让 Iris 针对一边布局编译的 mixin 落到实际加载的那个构建上（这正是"一渲染水面就崩"的根因修法）；再把 FML 的"必须有某个钠"放宽成可由 Fabric 版钠满足。顺带：**钠视频设置里的模组页面也双向补齐** —— NeoForge 钠在跑会收下 Fabric 侧的页（iris / voxy / continuity / lss / sodium-extra），Fabric 钠在跑会收下 NeoForge 侧的页（iris / c2me 等）。

**注意这是"能跑通"级别的保证**（覆盖：启动 → 进世界 → 水面渲染 → 设置页链接）；非常规版本搭配、重度光影包请自行测试，出问题照常带日志报 bug。

**CTM 补充（2026-10-19 更新）**：Continuity 的连接纹理在 **Fabric 版 Sodium** 上走原生 FRAPI emitter（`emitQuads`），在 **NeoForge 版 Sodium** 上走 Neo 的默认 emitter（level-aware 5 参 `collectParts`）。桥为此在 Continuity 的 jar 里注入了一个自包含 helper（`ContinuityCompat` + `NeoFobricCtmHelper`），并把 Fabric 的 `WrapperBlockStateModel` 的 5 参调用透传给被包裹的模型——Iris 的 wrapper 之前正是把世界上下文吞掉、让 NeoForge 回退到 2 参路径的原因。**两个 Sodium 构建现在都能和 Continuity 正常配合**；Neo 版自己的 `sodium-frapi` 配置仍然按它自己的 `requiredMods` 被跳过（不需要打开，也不建议造别名壳）。全模组 + Iris 光影开/关、最小 Neo 集、最小 Fabric 集均已实测玻璃整面连接、书架连续。

PS：这是个趣味实验性测试，出问题不负责~ 关于 voxy：请使用**本仓库作者构建的 voxy 0.2.21-beta**（分支 [`Up-dev`](https://github.com/Lonmo0208/voxy/tree/Up-dev)；下面列表里的就是这个）—— 它修复了与最新版 Sodium / Iris 的兼容（开光影时 LOD 正常加载），也**修复了与 Ixeris 同开时的帧缓冲崩溃**（两者现在可以同时启用，本仓库已实测进世界无崩溃）。Sodium / Iris 依旧建议用 Fabric 版。**注意：这个 0.2.21-beta 是 26.2 构建；26.3 还没有对应版本，26.3 上目前只能用 Modrinth 的 0.2.20-beta（见上面的 26.3 光影提示）。**

### 点名兼容单独归档（`compbridlnk`）

桥的主体只做通用规则；凡是**必须直接点某个模组的类、包或字段名**才能修的问题，全部集中在 `projects/fabricbridge/src/main/java/net/neoforged/fabricbridge/compbridlnk/`，一个模组一个类，注释写明为什么非它不可：

- `ViaFabricPlusCompat` —— 版本选择 GUI 的 wrap 重载对齐 + 服务器列表翻译参数转字符串 + 1.10 花盆注入点对齐
- `CreateCompat` —— 方块实体 behaviour 的提前初始化、partial model 的模型发现注入
- `ForgeConfigAPIPortCompat` —— `ConfigTracker#registerConfig(modid)` 调用重定向
- `PolymerCompat` —— 创造模式物品栏的静态页游标
- `SodiumCompat` / `SodiumConfigApiLink` / `SodiumLoaderLayoutLink` —— 入口点顺序与设置页链接
- `ContinuityCompat` —— Neo Sodium 默认 emitter 的 level-aware 5 参 `collectParts` 适配（helper 注入 Continuity 自己的 jar；Fabric `WrapperBlockStateModel` 的 5 参透传）
- `ArchitecturyCompat` —— 含 Architectury 注入的 jar 识别（外壳 FML 模组）
- `ModernfixMixinGuard` —— 关掉与平台字段形状冲突的 mixin
- `ModMenuConfigLink` / `ModMenuUpdateLink` —— 模组列表的配置页与更新检查

Fabric API 自身的适配不在此列 —— 那是宿主 API 的一部分，走通用处理器。

## 已知不兼容

**FFAPI（Forgified Fabric API）不能同装** —— 它是 NeoForge 模组，和桥自带的那份真 Fabric API 携带**同一批包**（`net.fabricmc.fabric.*`）⇒ 同一个模块层里两个模块导出同名包 ⇒ JDK 直接拒绝启动，只能二选一。**Sinytra Connector 不能同装** —— 它和桥抢同一件差事（把 Fabric 模组带进 NeoForge）。另外：**"一起跑" ≠ "互相看得见"** —— 两边模组并排运行没问题，但"Fabric 模组直接依赖某个 NeoForge 模组的 API"（反之亦然）**不保证**。还有上游测试里那条**内部互斥**的 tag 断言（见上）。

**26.3 光影 / Iris / Voxy**：26.3 改了透明与粒子管线，**旧光影包**会被 Iris 标记为 `Outdated`，缺少 `opaque_particle` / `translucent_particle` 程序时粒子会渲染成灰色大碎片 —— 请用 26.3 版本的光影包（例如 Complementary **r5.9.3**）。Iris **1.11.x** 在 26.3 上仍有这个粒子管线 bug，**Iris 1.12 快照已修**；但 **Voxy 0.2.20-beta 声明与 Iris 1.12+ 不兼容**（`breaks iris >=1.12-`），两者目前只能二选一。

## 怎么装 / 怎么构建

**从源码构建**（安装器不再预构建进仓库）：

```bash
git clone --recurse-submodules <this repo>
cd NeoFobric
./gradlew :neoforge:installerJar      # 产物在 projects/neoforge/build/libs/
```

普通克隆也行：`settings.gradle` 会在缺失时自动把 `fabric-loader` 子模块拉下来（钉在一个固定提交上）。**安装**：① 先按 NeoForge 官方方式装好 **NeoForge 26.3**；② 用上面构建出来的安装器安装；③ 把 **Fabric 模组和 NeoForge 模组一起**丢进 `mods/`。**卸载 / 回退**：把本加载器从安装配置里去掉，或用 NeoForge 自己的安装覆盖一遍即可 —— 它没有往游戏本体写不可逆的东西。

### 第一次启动会慢，这是正常的

**第一次进游戏会明显慢。** 原因是桥要先**处理一遍 Fabric 侧的模组**：扫描它们的 jar，把需要调整的部分（**尤其是 Fabric API 各个模块的 mixin 锚点** —— NeoForge 把原版的调用点搬过位置，这些锚点得跟着改）**生成一份修改后的副本放进缓存**（`.neofobric/` 目录里），之后就都从缓存加载。所以：

- **第一次启动**：慢（本机 94 个 Fabric jar 约 10 秒，冷盘 / 慢机器上更久，还要叠上游戏自身的首次资源加载）；
- **之后的启动**：和普通 NeoForge 差不多；
- **★ 只要动过模组集（加、删、换任意一个模组），这份缓存整体失效，下次启动会再处理一遍** —— "刚换完模组第一次慢、之后快"就是它的正常节奏，不是卡死；
- 想强制跳缓存：加 JVM 参数 `-Dneofobric.noPatchCache=true`（调试用，会更慢）。

## 许可与出处

见 [README-LICENSE.md](README-LICENSE.md)：**我们自己写的部分**沿用 NeoForge 的 `LGPL-2.1-only`；内嵌的 Fabric API 模块与 fabric-loader 是 `Apache-2.0`（各自保留声明）；我们**没有复制** FFAPI（Apache-2.0）或 Sinytra Connector（MIT）的代码，只在注释里引用它们公开的做法，并已在许可文件里致谢。

---

## 关于这个项目

**这是个 AI 项目**（人定方向、验结果；代码、文档、排查过程大量由 AI 完成），所以它是一坨"史"，我们自己也这么叫它。但我们对这一坨有要求：**它得比 Forbric 那坨好** —— 好在它用的是**真正的 fabric-loader**（不是重新实现一遍 Fabric API 的语义）、**不丢掉 NeoForge**（底座还在、NeoForge 模组照常跑、不装 Fabric 模组时它就是普通 NeoForge），并且把上游 **Fabric API 自己的测试流程**跑完了（114/114），还把结果原样写在这里。

**再强调一遍：这是一坨 AI 写的史。** 它**肯定不如信雅互联（Sinytra Connector）+ FFAPI** —— 人家是认真做的工程，我们是从正经路子旁边岔出去的一坨。但写它，就是想让 Forbric 看一眼同一道题的两个答案：**同样是方向盘全交给 AI，凭什么你写出来是一坨臭的，我这坨好歹是巧克力味的？** 想要"正经"的 NeoForge ↔ Fabric 互通方案，请去看 **Sinytra Connector + FFAPI**；我们这条纯属自娱自乐的一条岔路。

---

## English

[中文](#neofobric) | **English**

## What this is

**A bridge built inside the NeoForge loader so Fabric can run alongside it.** The base is NeoForge (**this repository is a fork of NeoForge 26.3**, current version **`26.3.0.100`**): FML stays the host — it boots, it owns the game, the module layer and its own mods. The bridge pulls the **real fabric-loader** (compiled from this repository's `fabric-loader` submodule) into the same process, and Fabric mods load the Fabric way: `fabric.mod.json`, entrypoints, nested jars, access wideners / class tweakers, Fabric mixin configs. **There is no Forge here** — the dead old Forge is not part of any step. **With no Fabric mods installed it is a plain NeoForge loader.** When picking mods, **prefer the NeoForge build**; use a Fabric build only when there is no NeoForge one. Compatibility here means "it runs", not "it is guaranteed".

## Current state: a playable-but-don't-trust-it beta

There are probably still plenty of bugs, compatibility may still be rough, and it **cannot be installed together with FFAPI (Forgified Fabric API)** (see below). **The server side works** ✓ — both an in-process dedicated server and a production-installed one reach `Done` with mods loaded; a **dedicated server carrying the whole bclib/wover family** boots too (it used to hit a registry freeze window — fixed). **Other machines should work in theory** — anything that can run NeoForge 26.3 + Fabric (Windows / Linux / macOS, any launcher) ought to be fine, **but we only ever verified on the development machine; there is no multi-device testing**, so please try it yourself. **Parts of Create Fly work correctly**, but there are certainly bugs we do not know about; **voxy (including voxy-extra) is playable**, and **both mixed pairs — Fabric Sodium + NeoForge Iris and NeoForge Sodium + Fabric Iris — reach the world**; **the same goes for other Fabric mods**: launching is not the same as every feature working; **NeoForge mods can be hit by knock-on effects too** (the bridge touches the layer both loaders share).

### 26.3 (`26.3.0.100`, 2026-10-28)

- **Base moved to Minecraft / NeoForge 26.3** (FML 12.0.12, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3); `fabric-loader` is still compiled from this repository's submodule.
- **A 416-mod 26.3 set reaches the world in practice**: Twilight Forest Re26, Sodium/Iris, the Voxy family, FrozenLib, FancyMenu, EMF/ETF/ESF, ViaFabricPlus, JER, Create Fly, Alex's Mobs and more; mods without a 26.3 build were dropped.
- **Regression**: Fabric API server suite **112/112**, client suite **BUILD SUCCESSFUL** (screenshot comparison, dynamic-registry sync and the rest included).
- **Representative 26.3 fixes**: `ModConfig.Type.COMMON/SERVER` → `LOCAL/SYNCED` (old entrypoints died with `NoSuchFieldError`); the datapack registry lists captured during `RegistryDataLoader`'s static init missed `NewDatapackRegistryEvent` additions ("Missing registry" / worlds refusing to load); the `BiomeModifier.modify` signature gained a `RegistryAccess` parameter (old three-argument implementations threw `AbstractMethodError`); FancyMenu dereferenced the not-yet-created `gui` from the early-window callback (startup NPE); FrozenLib injected its duck interface into the `StructurePlacement` interface, so Twilight Forest's direct implementor inherited three throwing defaults and killed world creation; Alex's Mobs' multipart entities read NeoForge's null `PartEntity[]` default when tracking ended (client disconnect / server chunk-unload NPE); two versions of one mod (e.g. Sodium 0.9.2 + 0.9.3) used to register the older copy's mixin config first while the newer copy owned the classes, which failed the resource reload with `Illegal classload request for accessor mixin ... missing from sodium-common.mixins.json` (older copies' configs are skipped now); and ShoulderSurfing's two self-built translucent armor pipelines, which Iris has no mapping for, now get `ENTITIES_TRANSLUCENT` / `ENTITIES_TRANSLUCENT_GLINT` instead of the `Missing program shouldersurfing:...` warning.
- **26.3 shader note** (not the bridge, but you will see it): 26.3 changed the transparency/particle pipelines, so **older shader packs** are marked `Outdated` by Iris and lack the `opaque_particle` / `translucent_particle` programs — particles then render as huge grey quads. Use a **26.3 shader pack** (e.g. Complementary **r5.9.3**). Iris **1.11.x** still has this particle bug on 26.3; **the Iris 1.12 snapshot fixes it**, but **Voxy 0.2.20-beta declares itself incompatible with Iris 1.12+** (`breaks iris >=1.12-`), so Voxy and Iris 1.12 are currently either/or. Even removing that line from the jar only gets it to start: **under Iris 1.12 Voxy's LODs render pure white** (its shader integration only understands 1.11.x). The 26.2-era `0.2.21-beta` build from this project's own voxy fork fixed Sodium/Iris compatibility, but **no 26.3 build of it has been published yet**.

## Feedback

**Bug reports are welcome — while we still have tokens to burn, we will fix what comes in** (open an issue here, or ping the author). Fast or slow, it gets fixed. And whatever it looks like in the end, **it is still a better pile of garbage than Forbric's**. If you can, include: game version + loader version (the number the installer reports) + your mod list + `logs/latest.log` (or the crash report) — it saves a lot of back-and-forth.

### What we tested

**26.3 (`26.3.0.100`)**: the Fabric API server suite reports **`All 112 required tests passed`** ✓, the client suite ends in **`BUILD SUCCESSFUL`** (screenshot comparison, dynamic-registry sync, lifecycle and command tests included), and a 416-mod 26.3 set reaches the world (`Dev logged in with entity id`). Items (1)–(11) below are the full 26.2 record, kept as history.

We ran **Fabric API's own test flow** end to end: the server suite reports **`All 114 required tests passed`** ✓ — every one of upstream's required gametests is green, and that is the strongest evidence this repository has. The client suite also runs to completion; **the only failure is an upstream test case that contradicts itself** (`fabric-tag-api-v1-testmod`: one data pack both adds `dirt` to `sword_efficient` and removes content from another tag, so two assertions demand the same pack be present and absent at once — unsolvable here). That is a documented known difference, not a gap in the bridge. The bridge's built-in self-check reports its event inventory as **11/11 wired** on the client ✓, and a dedicated server prints the report even with no player ✓. Production install chain: a dedicated server installed with the installer built from this repository reaches `Done`, and the client reaches the game.

Several follow-up rounds hardened exactly that claim: (1) the **official Fabric API test mods** ran again on the client — the world loads, `LifecycleEventsTest` reaches 3400+ ticks, and entrypoint failures / encoding failures / crashes are all zero; (2) a **dedicated server** boots with the whole bclib/wover family (it used to crash on the registry freeze window mentioned above); (3) the bridge's self-check grew two hard evidences: the **Fabric channel negotiation readout** (74/41 versus 41/74 in singleplayer, mirrored on a server; any channel whose receiver the other side never negotiated is named as a warning) and a **real payload round-trip probe** (a payload generated on the spot travels client → server → client; the verdict reads `fabric payload round trip ok`); (4) the **NeoForge mod list now shows updates for Fabric mods too** (Mod Menu's own checkers plus a direct Modrinth hash lookup). On a pack that includes c2me the event self-check reads **10/11** — the silent row is that mod's own documented behaviour as it rewrites the chunk system, not the bridge. (5) 2026-10-14: the full 90-mod set (as then listed) launches into the world and plays (worldgen family, Create, AE2, TechReborn and more), and two more batches of world-load / disconnect problems were fixed the same day — the **world-load registry chain** ("the save will not load / datapack errors") and **polymer's handshake channel plus 26.2's moved entity constants** ("connection lost"); additionally **architectury-fabric can now boot and open a world** (Fabric-compiled `LocalCapture` mixins used to be killed by the platform patches' shapes). All of this shipped in **26.2.0.128**. (6) 2026-10-15/16: the now **98-mod set** (list updated below) ran end to end — world entry, mining, item pickup and the creative-flight→survival fall all pass; the official Fabric API test mods are green again on the client (`LifecycleEventsTest` past 6000 ticks; assertions / encoding / crashes all zero). Three deep fixes landed too: **the bridge misclassified NeoForge mods' own payloads as Fabric channels** (modernfix's load-suspension sentinel was dropped and the server stayed frozen all session — items would not be picked up and creative flight never fell), **the packet splitter measured around the encoder's packet context** (polymer-style mods got kicked on `level_event`), and **the creative inventory crashed on unregistered tabs** (polymer packs). All folded into **26.2.0.129**. (7) 2026-10-15/17: **ViaFabricPlus 5.0.2 (the join-every-server-version client mod) now runs end to end and joins the list** (100 mods now) - fixed layers: the layer conflict between a Fabric jar's bundled MixinExtras and the platform module (start-up death), its version-switch reload tasks hitting the freeze window, its tooltip feeding a raw version object into the translatable-argument validation (dev), and the visibility of its 1.12.2 block-place lambda overwrite; plus polymer's page field (the reload stall behind VFP's GUI) and stack sizing in the splitter/resource rewrites. All folded into **26.2.0.130**. (8) 2026-10-19: **both Sodium builds now work with Continuity** — Fabric keeps its native FRAPI emitter; NeoForge's default emitter is served by the level-aware five-argument `collectParts` (a helper written into Continuity's own jar, with Fabric's `WrapperBlockStateModel` passing the call through so Iris keeps the world context). The full 101-mod set with Iris shaders on/off, plus the minimal Neo and Fabric sets, all reached the world with connected glass and bookshelf. The same round archived named-mod compatibility into `compbridlnk` and fixed the Jade × flower-pot NPE. All folded into **26.2.0.131**. (9) 2026-10-24/25: the set grew to **463 mods** (NeoForge + Fabric mixed; c2me/OpenCL, Sodium, Iris with shader packs, Voxy LOD, Create, the TFC compat layer and more) and reached the world again. Fixed this round: **platform-mod exposure data completeness** (camerapture's nested tapestry rolled back registrations and mismatched adapter flavours; LambDynamicLights reads its in-jar defaults through Yumi) and **fabric-registry-sync's eager block-state cache rebuild** (Occultism et al. blew up mid-registration). The "empty world after join" culprit turned out to be **Noxesium stalling client chunk loading** (mod-side, parked), and the original Fabric build of **visuality now runs through the bridge** in place of the NeoForge port whose injection point predates 26.2. All folded into **26.2.0.132**. (10) 2026-10-25: the set grew to **502 mods** and reached the world again. Fixed this round: **the relaxed copy was deleting the `[[accessTransformers]]` declaration along with the nested-jar blocks** (every `file =` line went, so FancyMenu lost its access transformers; only `[[jars]]` sections are stripped now), and **the real cause of Jade's missing-translation assertion** — inventory_particles' `AtlasLoaderMixin` anchors its `forEach` injection on 26.2's two-argument `SpriteSourceList.list`, but that call now lives only in the one-argument overload, so 0/1 injection failed the whole resource reload and every language file went missing (the `zip file closed` noise was a consequence of the aborted reload). Everything parked is a plain mod problem: inventory_particles (wrong overload), enderscape (its start-up sweep constructs a block entity for every registered block, which trips the shared EasyAnvils/VisualWorkbench block-entity types that do not cover their generated variant blocks), status-effect-bars, xaerozoomout and do_a_barrel_roll. Live readout: 686 Fabric-side mods loaded, 0 load failures, 861 client chunks, and the world screenshot shows nearby blocks, trees, structures and the held item rendering normally. All folded into **26.2.0.133**. (11) 2026-10-26: the data and class-tweak gaps behind the mod set were closed. **`fabric:load_conditions` now applies on NeoForge's recipe path** — Fabric's resource-conditions module only wraps `scanDirectory`, while NeoForge's `scanDirectoryWithModifier` copy parsed conditioned files anyway; unique parse failures on the 502-mod set dropped from 209 to 147. **Fabric mods' `loot_modifiers` files and the `global_loot_modifiers` index are no longer parsed by NeoForge's loader**, **`PotionBrewing.bootstrap` only fires the brewing event when it was handed a real registry access** (Create calls the deprecated EMPTY overload from recipe preparation, and one unguarded listener used to take the whole world's data load down), **the dev client tolerates duplicate game-test registrations** (dev-only; release builds never fire that event), and **`transitive-inject-interface` now propagates down the hierarchy** (six mods rely on it; 158 interfaces injected). Six new unit tests pin the realign annotation walk and the class-hierarchy walk (`fabricbridge` now has 11, all green). All folded into **26.2.0.134**.

### The 502 mods that have actually been run

> The 26.3 set has 416 jars and is not listed line by line; representative mods are in the 26.3 section above. The full list below is the 26.2 record and still shows the kinds of mods the bridge can carry.

```
AccurateBlockPlacement-Reborn-1.4.8+mc-26.1-26.2
Adorn-9.0.0+26.2-neoforge
AdvancedCoreInfo-neoforge-26.2-1.5.0
AdvancedLootInfo-neoforge-26.2-2.5.0
AdvancementPlaques-26.2-neoforge-1.7.2
AgeingSpawners-26.2-12.0.0
AmbientEnvironment-neoforge-26.2-26.2.1
AmbientSounds_NEOFORGE_v6.3.6_mc26.2
ArmorStatues-v26.2.0-mc26.2.x-NeoForge
AsyncParticles-26.2.2.10+26.2
AttributeFix-neoforge-MC26.2-26.2.0.1
BarteringStation-v26.2.0-mc26.2.x-NeoForge
BetterAdvancements-NeoForge-26.2-0.6.0.72
BetterAnimationsCollection-v26.2.0-mc26.2.x-NeoForge
BetterF3-19.0.1-NeoForge-26.2
BetterThanMending-2.3.0-merged
BetterTridents-v26.2.0-mc26.2.x-NeoForge
BiomesOPlenty-neoforge-26.2-26.2.0.0.28
BridgingMod-2.7.0+26.2.neoforge-release
Chunky-NeoForge-1.5.4
Clumps-neoforge-26.2-26.2.1
ConfigManager-neoforge-26.1_26.2-1.1.3
ConfiguredDefaults-v26.2.0-mc26.2.x-NeoForge
Controlling-neoforge-26.2-26.2.4
CreativeCore_NEOFORGE_v2.14.16_mc26.2
CreeperFirework-neoforge-3.0.0
CustomPlayerModels-26.2-0.6.27c
CustomSkinLoader_Universal-15.1
CutThrough-v26.2.0-mc26.2.x-NeoForge
DarkPaintings-neoforge-MC26.2-26.2.0.1
DeleteWorldsToTrash-v26.2.0-mc26.2.x-NeoForge
DiagonalFences-v26.2.0-mc26.2.x-NeoForge
DiagonalWalls-v26.2.0-mc26.2.x-NeoForge
DiagonalWindows-v26.2.0-mc26.2.x-NeoForge
Drip Sounds-0.6.3+26.2-NeoForge
EasyAnvils-v26.2.3-mc26.2.x-NeoForge
EasyMagic-v26.2.0-mc26.2.x-NeoForge
EasyShulkerBoxes-v26.2.3-mc26.2.x-NeoForge
EchoChest-v26.2.0-mc26.2.x-NeoForge
Ecologics-NeoFab-26.2-2.6.3
EnchantingInfuser-v26.2.0-mc26.2.x-NeoForge
EnchantmentDescriptions-neoforge-MC26.2-26.2.0.2
EternalNether-v26.2.0-mc26.2.x-NeoForge
EuphoriaPatcher-1.10.5-r5.9.3-neoforge
ExplorersCompass-26.2-3.3.0-neoforge
ExtremeSoundMuffler-4.04_NeoForge-26.2
FallingTree-26.2-25
FarmersDelight-26.2-3.6.28+refabricated
Female-Gender-Mod-5.0.0+mc26.2-neoforge
ForgeConfigAPIPort-v26.2.1-mc26.2.x-Fabric
FriendlyFire-neoforge-MC26.2-26.2.0.1
FrozenLib-3.0.3-mc26.2-fabric
GlitchCore-neoforge-26.2-26.2.0.0.0
Highlighter-26.2-neoforge-1.2.2
HopoBetterUnderwaterRuins-_26.2_-1.2.8
HorseExpert-v26.2.1-mc26.2.x-NeoForge
HypixelModAPI-1.0.2+build.1+mc26.1
IAS-9.0.8+26.2-neoforge
IMBlocker-7.3.2-neoforge-26.1+
Iceberg-26.2-neoforge-1.4.2.2
IllagerInvasion-v26.2.0-mc26.2.x-NeoForge
ImmediatelyFast-NeoForge-1.16.5+26.2
InvMove-0.9.6+26.2-NeoForge
InventoryProfilesNext-neoforge-26.2-2.3.8
ItemBorders-26.2-neoforge-1.3.2
ItemLocks-Fabric-26.2-1.3.14
ItemPhysicLite_NEOFORGE_v1.6.12_mc26.2
Ixeris-4.6.8+26.2-neoforge
Jade-mc26.2-Fabric-26.2.11
JustEnoughProfessions-neoforge-26.2-12.0.0
JustEnoughResources-NeoForge-26.2-1.11.0.44
KotlinLangForge-2.16.0-k2.4.21-3.1+neoforge
LeaveMyBarsAlone-v26.2.0-mc26.2.x-NeoForge
LegendaryTooltips-26.2-neoforge-1.6.2
LongerChatHistory-neoforge-1.8
MagnumTorch-v26.2.0-mc26.2.x-NeoForge
MindfulDarkness-v26.2.0-mc26.2.x-NeoForge
MobLassos-v26.2.1-mc26.2.x-NeoForge
MoogsEndStructures-universal-1.21-2.1.1
MoogsNetherStructures-universal-1.21-3.1.1
MoogsSoaringStructures-universal-1.21-2.2.0
MoogsStructureLib-neoforge-26.2-3.4.2
MoogsVoyagerStructures-universal-1.21-5.1.3
MoreSpearEnchantments-fabric-1.1.2+26.2
MossyLib-1.6.0+26.2+neoforge
MouseTweaks-neoforge-mc26.2-2.31
MutantMonsters-v26.2.2-mc26.2.x-NeoForge
NaturesCompass-26.2-3.3.0-neoforge
Neat-26.2-50-NEOFORGE
NetherChested-v26.2.0-mc26.2.x-NeoForge
NoChatReports-NEOFORGE-26.2-v2.20.2
NoChatRestrictions-NeoForge-MC26.2-v1.2.3
Nullscape_26.2_v1.2.20
OverflowingBars-v26.2.0-mc26.2.x-NeoForge
PacketFixer-fabric-3.3.6
Paraglider-neoforge-26.2.1
ParticleEffects-1.6.0+26.2+neoforge
PatPat-1.3.2+26.2+neoforge
PickUpNotifier-v26.2.0-mc26.2.x-NeoForge
Ping-Wheel-1.12.1-neoforge-26.2
PlayerAnimationLibMerged-1.2.8+mc.26.2
PlayerRevive_NEOFORGE_v2.1.2_mc26.2
PresenceFootsteps-1.13.3+26.2
PrickleMC-neoforge-MC26.2-26.2.0.3
Prism-26.2-neoforge-1.1.2
ProPlacer-v26.2.0-mc26.2.x-NeoForge
RebornCore-6.1.1
ResourcefulConfig-5.0.1
ResourcefulLib-5.0.4
ScalableLux-neoforge-0.3.0-alpha.0.16-all
Searchables-neoforge-26.2-1.0.1
SereneSeasons-neoforge-26.2-26.1.2.0.6
ShoulderSurfing-NeoForge-26.2-5.2.0
SimpleDiscordRichPresence-neoforge-26.2.0.1
SkeletonAIFix-v26.2.0-mc26.2.x-NeoForge
SmoothScrollingRefurbished+26.2-1.9.0
SnowUnderTrees-2.7.6+26.1
SpiderOverhaul-0.0.6-NeoForge-v26.2
Stellarity-6.0.1
Stoneworks-v26.2.0-mc26.2.x-NeoForge
StorageDrawers-neoforge-26.2.0.1
TechReborn-6.1.1
TerraBlender-neoforge-26.2-26.2.0.0.2
Terralith_26.2_v2.6.4_Neoforge
TinySkeletons-v26.2.1-mc26.2.x-NeoForge
ToolStats-neoforge-MC26.2-26.2.0.1
TradingPost-v26.2.0-mc26.2.x-NeoForge
Trim Patcher-2.1-mc26.2-neoforge
UniversalBoneMeal-v26.2.0-mc26.2.x-NeoForge
VehicleUpgrade-v26.2.0-mc26.2.x-NeoForge
ViaFabricPlus-5.0.2
VoxyServer-1.2.4-26.2
WilderWild-4.3.3-mc26.2-fabric
XaeroPlus-2.37.1+neoforge-26.2-WM1.47.1-MM26.6.1
[26.2+]MYEC-v1.10.1-NeoForge
[Neoforge]ctov-26-2-4.1.0
_26.2_SecurityCraft_v1.10.2.1
abridged-2.0.2-neoforge-26.2
adorabuild-structures-2.11.0-neoforge-26.2
advancementdisable-neoforge-1.1.2+26.1.2
ae2wtlib-26.2.0-beta
alexsmobs-2.2.4-fabric+26.2
amcdelight-1.0.1-fabric+26.2
amecs-bundle-1.6.5+mc26.2-pre-2
animal_feeding_trough-1.2.1+26.2-neoforge
animatica-0.6.3+26.2
appleskin-neoforge-mc26.2-3.0.10
appliedenergistics2-26.2.1-alpha
archers-neoforge-3.1.3.003+26.2
architectury-neoforge-21.1.12
armor-stand-arms-v2.7.1
armor_model_api-neoforge-1.1.2+26.2
armored-elytra-1.15.1
authme-neoforge-9.3.0+26.2
baguettelib-26.2-NeoForge-2.0.7
bagus_lib-26.2-27.7.0
balm-neoforge-26.2-26.2.0.9
bbe-neoforge-1.3.8-beta.1+mc26.2
bclib-26.201.3
beautify-fabric-3.0.0+26.2
better-compatability-checker-neoforge-26.2.0.1
better-compatibility-checker
better-end-26.201.3
better-nether-26.201.3
better-selection-mc-1.9.1
better_climbing-neoforge-6
better_end_sky-0.3.1+26.2
betterarcheology-neoforge-26.2-1.3.9
bettercombat-neoforge-3.2.2+26.2
betterf1-neoforge-26.2-1.3
betterhurtcam-neoforge-1.14.0+mc26.2
bettermounthud-1.3.1
betterstats-5.5.6+fn-26.2
bettervillage-neoforge-26.2-4.0.1
biolith-neoforge-3.7.0-beta.1
biomereplacer-3.1-newt-neo
blur-neoforge-6.3.1+26.2
boatiview-neoforge-0.0.9-26.2
bobby-5.2.15+mc26.2
boids-2.0.0+26.3
bookshelfinspector-neoforge-2.4+26.2
bundle-api-neoforge-4.0.0+26.2
byepregen-neoforge-26.2-1.1.2.5
c2me-fabric-mc26.2-0.4.2-alpha.0.60
c2me-fabric-opts-accel-opencl-mc26.2-0.4.2-alpha.0.60
calcmod-1.5.2+neoforge.26.2
camerapture-1.10.15+mc26.2
carryon-neoforge-26.2-2.11.2
chat_heads-1.3.2-neoforge-26.1
chatanimation-neoforge-1.3.5+mc26.2
chattools-v2.4.5+26.2-neoforge
chefsdelight-1.0.6-fabric-26.2
cherishedworlds-neoforge-17.0.0+26.2
chunkloaders-1.2.9a-neoforge-mc26.2
cicada-lib-0.15.3+26.2
cinderscapes-6.1.0-alpha.2
clickthrough-plus-3.6.5+26.1.2-neoforge
clienttweaks-neoforge-26.2-26.2.0.6
cloth-config-26.2.155-neoforge
cobweb-neoforge-26.2-1.4.0
codxlib-1.6.1-fabric+26.2
collective-26.2.0-8.62
colorfulhearts-neoforge-26.2-26.2.0.0
comforts-neoforge-16.0.0+26.2
commandkeys-neoforge-3.103.2+26.2
configmanager
connectedglass-1.1.14a-neoforge-mc26.2
connectiblechains-2.4.4+26.2-neoforge
constructionwand-2.1.3+26.2
constructionwandpolymer-1.0.1
continuity-3.0.1+26.2
controlify-3.5.3+mc26.2-universal
cookingforblockheads-neoforge-26.2-26.2.0.5
coolrain-1.4.0-26.2
corpse-neoforge-1.1.21+26.2
craftingtweaks-neoforge-26.2-26.2.0.3
cratedelight-26.07.01-26.2-fabric
crawl-mc26.2-neoforge-0.15.0
create-fly-26.2-6.0.9-2
create_ultimate_factory-2.2.4-fabric-26.2
cristellib-neoforge-26.2-3.1.13
crosshairindicator-1.0.3
curios-neoforge-16.0.0+26.2
custom-crops-1.1.1+26.2
cwb-4.1.0+26.2
dark-loading-screen-1.6.19
decorative-lamps_NeoForge-26.2-1.8.1
default-options
delightlib-26.06.23-26.2-fabric
difficultylock-26.2.0-5.2
dismountentity-26.2.0-3.8
display-delight-1.8.4-mc26.2
doubledoors-26.2.0-7.5
durabilitytooltip-1.2.0a-neoforge-mc26.2
dynamiccrosshair-9.14+26.2-fabric
e4mc-neoforge-6.2.3
earthmobsmod-26.2-30.5.0
easyelytratakeoff-26.2.0-4.6
effecttimerplus-fabric-3.103.1+26.2
elevatorid-neoforge-26.2-1.16.4
elytra-chestplate-swapper-2.1.0-MC26.2-neoforge
elytratrails-1.6.7-26.2
elytratrims-neoforge-4.9.1+26.2
emoji-type-3.1.0-26.2-neoforge
emotecraft-for-MC26.2-3.4.0-b.build.170
enchanted-vertical-slabs-2.7.0-neoforge-mc26.2
enchantment-effects-1.1.0+26.2
enderitemod-neoforge-26.2-1.9.1
enderscape-fabric-3.0.3+mc26.2
endrem-neoforge-26.2-6.2.1
entity_collision_optimizer-1.0.0-mc26.2-alpha.10
entity_model_features-3.3.12-26.2-neoforge
entity_sound_features-0.8.2-26.1-neoforge
entity_texture_features-7.2.6-26.2-neoforge
entityculling-neoforge-1.11.3-mc26.2
exdeorum-5.1
explorations-neoforge-26.2-1.6.4
explosive-enhancement-1.4.2-26.2
fabric-api-0.161.0+26.2
fadeless-2.0.8-26.2
fallingleaves-26.2-3.1.0-beta
fancymenu_neoforge_3.9.14_MC_26.2
farm-3x3-2.4.1
farm-3x3-farmers-delight-compatibility-2.4.X
fast-ip-ping-v1.0.12-mc26.2
ferritecore-9.0.0-neoforge
firstperson-neoforge-2.7.3-mc26.2
fish_of_thieves-mc26.2-v26.2.1.2-neoforge
forgivingvoid-neoforge-26.2-26.2.0.2
formations-1.0.4a-neoforge-mc26.2
formationsnether-1.0.5b-mc1.21+
formationsoverworld-1.0.5c-mc1.21+
fpsdisplay-5.1.0+26.2
framework-neoforge-26.2-0.13.26
freelook-1.4.3
friendsandfoes-neoforge-5.0.3+mc26.2
fullbrightnesstoggle-26.2.0-4.9
fusion-1.3.16-neoforge-mc26.2
fzzy_config-0.7.7+26.2+neoforge
gazebo-neoforge-2.2.0+26.2
geckolib-neoforge-26.2-5.5.6
globalpacks-neoforge-26.2-26.2.1
glowing-torchflower-neoforge-mc26.2-1.4.1
goldenhopper-neoforge-26.2-1.7.0
gravestone-neoforge-1.0.40+26.2
grind-enchantments-4.2.2+26.1.2
guardvillagers-4.0.4-26.2.0
guideme-26.2.2-alpha
hardcorerevival-neoforge-26.2-26.2.0.6
harvest-with-ease-neoforge-26.2-9.4.0
healingcampfire-26.2.0-6.4
held-item-info-1.9.2
ijm-tweaks-1.8.0+26.2
immersive_aircraft-neoforge-1.7.0+26.2
immersive_armors-1.8.2+26.2-neoforge
immersive_melodies-neoforge-0.8.2+26.2
immersive_paintings-neoforge-26.2-0.7.9
immersiveui-neoforge-26.2-0.3.6+26.2
improved-village-placement-1.2.0-neoforge-26.2
incontrol-26.2-11.0.0
inventoryessentials-neoforge-26.2-26.2.0.6
inventoryhud.neoforge.26.2-3.4.34
inventorytotem-26.2.0-3.6
invisibleframes-neoforge-2.0.1+26.2
iris-fabric-1.11.4+mc26.2
ironchest-26.2-neoforge-16.8.3
itemscroller-fabric-26.2-0.32.2
jamlib-neoforge-2.3.1+26.2.x
jecharacters-26.1.2-neoforge-4.6.9
jei-26.2-neoforge-30.42.0.236
jewelry-neoforge-2.5.1+26.2
jumpoverfences-neoforge-26.2-1.8.0
just-hammers-neoforge-26.2.0.1
justenoughbreeding-neoforge-26.2-3.4.1
justzoom_fabric_3.0.1_MC_26.2
kitchenprojectiles-1.0.13+mc26.2
kleeslabs-neoforge-26.2-26.2.0.1
konkrete_fabric_1.11.1_MC_26.2
kreno-neoforge-26.2-26.3.2-26.2
lambdynamiclights-4.12.4+26.2
language-reload-1.7.8+26.2
leawind_third_person-3.0.3-beta+neoforge-26.2
libIPN-neoforge-26.2-6.9.0
libjf-26.2.2+forge
libraryferret-neoforge-26.2-5.0.1
lighty-neoforge-4.0.1+26.2
litematica-fabric-26.2-0.28.8
lithium-neoforge-0.25.3+mc26.2
lithostitched-1.8.0-neoforge-26.2
locator-heads-2.4.2
lootr-neoforge-26.2-1.24.41.124
magic_vibe_decorations-3.0.1-neoforge-26.1.2-26.3
make_bubbles_pop-0.4.0-beta.3-neoforge-mc26.2+
malilib-fabric-26.2-0.29.6
man_of_many_planes-0.2.3+26.2-neoforge
marlows-crystal-optimizer-2.0.0+mc26.1-26.3-neoforge
mca-neoforge-8.1.11+26.2
mcw-bridges-3.1.2-mc26.2neoforge
mcw-doors-1.1.5-mc26.2neoforge
mcw-fences-1.2.1-mc26.2neoforge
mcw-furniture-3.4.2-mc26.2neoforge
mcw-holidays-1.1.2-mc26.2neoforge
mcw-lights-1.1.5-mc26.2neoforge
mcw-paintings-1.1.0-mc26.2neoforge
mcw-paths-1.1.1-mc26.2neoforge
mcw-roofs-2.3.3-mc26.2neoforge
mcw-stairs-1.0.2-mc26.2neoforge
mcw-trapdoors-1.1.5-mc26.2neoforge
mcw-windows-2.4.2-mc26.2neoforge
mcwifipnp-2.1.3-26.2-neoforge
melody_neoforge_1.0.17_MC_26.2
mezz_config-26.2-neoforge-0.6.8-standalone
midnightlib-neoforge-1.10.0+26.2
minihud-fabric-26.2-0.40.7
modernfix-neoforge-5.27.21+mc26.2
modmenu-20.0.3
modonomicon-26.2-neoforge-2.5.0
more-armor-trims-1.5.4-26.2
more-chest-variants-1.5.14+26.1.1-neoforge
morechathistory-2.0.0
moredelight-26.06.23-26.2-fabric
moretotems-neoforge-26.2-2.25.0
movesubtitles-1.3+26.1-fabric
mru-1.0.44+26.2-fabric
musicnotification-neoforge-3.4.0-alpha.1+mc26.2
mythicupgrades-neoforge-26.2-5.1.1
nanite-library-neoforge-26.2.0.2
naturalist-2.0.6-neoforge-26.2
nethermap-4.3.0+26.1
netherportalfix-neoforge-26.2-26.2.0.1
nicer-skies-1.5.0+26.2
no-report-button-1.8.0-neoforge
no-resource-pack-warnings-1.5.0
no-telemetry-1.10.0
notenoughanimations-neoforge-1.12.5-mc26.2
notenoughcrashes-neoforge-4.4.9+26.2
nuit-neoforge-1.0.0-beta.6+mc26.2
occultism-26.2-neoforge-1.256.0
oceansdelight-expanded-1.0.4+fabric.26.2
ok_zoomer-18.0.0-beta.3
owo-lib-0.13.1+26.2
packed_packs-neoforge-2.3.1+26.2
paladins-neoforge-3.1.3.003+26.2
particlerain-4.0.2+26.2-fabric
particular-26.2-NeoForge-1.5.7
perspective_api-1.5.0-beta+neoforge-26.2
plasmovoice-neoforge-26.2-2.2.0-beta.2
polytone-26.2-6.9.1-neoforge
prefab-neoforge-2.1.0
promenade-5.6.0
puffish_skills-0.19.2-26.2-neoforge
punchy-2.8g-neoforge-26.2
puzzleslib-v26.2.5-mc26.2.x+neoforge
raised-neoforge-26.2-6.0.2
ranged_weapon_api-neoforge-4.0.0+26.2
reacharound+26.2-1.4.2
realisticbees-26.2.0-4.4
reglass-26.2-2.1.1
replaymod-26.2-2.6.28
repurposed_structures-7.7.6+26.2-neoforge
rightclickharvest-neoforge-4.6.2+26.2.x
rogues-neoforge-3.1.3.003+26.2
runes-neoforge-1.3.2+26.2
rusticdelight-fabric-26.2-1.7.0
scholar-neoforge-26.2-1.2.10
sclp-neoforge-5.6.7-snapshot+mc26.1-local
screenshot_viewer-1.3.6-fabric-mc26.2
searchonmcmod-neoforge-26.2-0.1.1
seasonhud-neoforge-26.2-2.0.11
shatterlib-neoforge-0.7.0-beta.1+26.2
shogi-neoforge-26.2-26.2.0.8
show-me-your-skin-2.0.3+26.2
shulkerboxtooltip-neoforge-5.4.1+26.2
sit-neoforge-26.1.2-1.5.2
skinlayers3d-neoforge-1.11.4-mc26.2
skinshuffle-2.13.0+26.2-fabric
skyboxify-3.4+26.2-fabric
smooth-scrolling-refurbished
smoothgui-neoforge-2.1.0+mc26.2
smoothskies-2.12.2+26.2-fabric
smoothswapping-0.9.10-26.2-neoforge
sodium-extra-fabric-0.9.4+mc26.2
sodium-neoforge-0.9.2+mc26.2
sophisticatedbackpacks-26.2-3.26.10.2199
sophisticatedcore-26.2-1.5.11.2405
sophisticatedstorage-26.2-1.5.122.2186
sound-physics-remastered-neoforge-1.5.1+26.2
soundsbegone-neoforge-1.6.0+mc26.2
spanishdelight-26.2-1.0.11
spark-1.10.187-neoforge
spell_engine-neoforge-1.10.9+26.2
spell_power-neoforge-1.6.2+26.2
springlotus-neoforge-1.2.2+26.2
spyglass-astronomy
spyglass_improvements-1.6+mc26.2+neoforge
stackedblocks-26.06.24-26.2-fabric
stackedblocksfarmersdelight-26.06.24-26.2-fabric
stackrefill-26.2.0-6.2
starterkit-26.2.0-9.0
statuseffecttimer-2.0.0+26.2
stendhal-1.4.9-26.2
storagedelight-26.07.01-26.2-fabric
structure_layout_optimizer-1.1.4+26.1-neoforge
structure_pool_api-neoforge-1.3.0+26.2
structurify-neoforge-2.0.43+mc26.2
superflatworldnoslimes-26.2.0-3.7
supermartijn642configlib-1.1.8a-neoforge-mc26.2
supermartijn642corelib-1.1.25-neoforge-mc26.2
t_and_t-fabric-neoforge-1.13.11
tcdcommons-5.5.6+fn-26.2
tectonic-3.0.28-neoforge-26.2
terrestria-8.1.0-beta.2
tfc2mecompat-1.1.1+mc26.x-neoforge
the-graveyard-1.5.1
theurgy-26.2-neoforge-1.118.1
tiertagger-neoforge-2.6.0+mc26.2
titlefixer-1.1+26.2
toms_storage_fabric-26.2-2.11.4
toms_trading_network-26.2-0.3.16
tooltip-scroll-1.5.1+26.2
totemcounter-neoforge-1.13.0+mc26.2
totemtweaks-26.2-1.2.0
trade-cycling-neoforge-1.0.23+26.2
trashcans-1.1.1a-neoforge-mc26.2
trashslot-neoforge-26.2-26.2.0.2
treeharvester-26.2.0-10.3
trek-B0.6.2
tslatentitystatus-neoforge-26.2-1.10.2
tweakeroo-fabric-26.2-0.29.5
twilightforest.re26-26.2-4.8.4208-fabric
ubesdelight-fabric-26.2-0.4.15
ukulib-neoforge-2.1.1+26.2
ukus-armor-hud-neoforge-0.12.0+mc26.2
undergroundworlds-neoforge-3.2.1-26.2
untitledduckmod-neoforge-26.2-1.6.0
variantsandventures-neoforge-1.0.28+mc26.2
veinminer-client-neoforge-2.12.1
veinminer-neoforge-2.12.1
villagernames-26.2.0-8.8
visuality-fabric
voicechat-fabric-2.6.24+26.2
voxy-0.2.21-beta-f473097
voxy-extra-0.2.6
voxy-seam-fix-0.2.0
voxy-server-side-fabric
wakes-0.7.1+26.2
waveycapes-neoforge-1.11.3-mc26.2
waystones-neoforge-26.2-26.2.0.12
weaponmaster_ydm-neoforge-26.2-4.4.4
wits-1.4.2+26.1-neoforge
wizards-neoforge-3.1.3.003+26.2
worldweaver-26.201.3
xaerominimap-neoforge-26.2-26.6.1
xaeroworldmap-neoforge-26.2-1.47.1
xercapaint-26.2-1.0.0
xptome-neoforge-26.1.2-2.5.1
yeetusexperimentus-neoforge-102.0.0
yet_another_config_lib_v3-3.9.7+26.2-neoforge
yumi-commons-collections-2.0.0
yumi-commons-core-2.0.0
yumi-commons-event-2.0.0
yumi-mc-foundation-1.1.1+26.2
```

> This set launches the game and plays for a while — it does **not** mean every feature is correct.

### The relatively stable corner

**Better End / Better Nether (the bclib / Better End / Better Nether / WorldWeaver family)** is currently the better bet in theory: for the End, **we only verified that you can enter and that terrain (probably) generates** — functional blocks, structures and mobs there are **untested**; **Better Nether now loads and its structures (the nether cities) generate correctly** (that is the crash fixed on 2026-10-11 — `this.structure` used to be null). The more a feature crosses loaders (mods depending on each other across the border, or both sides wanting to own the same piece of vanilla logic), the more likely it breaks. **Combinations that have been run through**: **the whole voxy family (voxy / voxy-extra / lss with its server side) comes up**.

### Sodium and Iris may come from different loaders

**Sodium and Iris do not have to come from the same loader** — you do not need to swap the other side for a matching build; all four pairings have been run through. Both same-loader pairs (both builds from NeoForge, or both from Fabric) are the ordinary cases; **the two cross-loader mixes are verified too**:

- **NeoForge Sodium + Fabric Iris** ✓ — tested with `sodium-neoforge-0.9.1+mc26.2` + Fabric Iris 1.11.2 alongside the whole Fabric extension family (voxy / continuity / lss / sodium-extra): reaches the world, water renders fine, stable for 2+ minutes, and a shader pack (Complementary Unbound) loads.
- **Fabric Sodium + NeoForge Iris** ✓ — tested with `sodium-fabric-0.9.1+mc26.2` + `iris-neoforge-1.11.2+mc26.2` and the same Fabric extensions: 40+ seconds in-world and stable, water renders fine, shader pack loads.

Under the hood the bridge does two things: it **renames Sodium's loader-specific classes across the two builds, in both directions** (a measured `sodium.fabric.*` ↔ `sodium.neoforge.*` name mapping) so that Iris's mixins compiled against one build's layout land on the build that actually loads (that rewrite is exactly what fixes the crash on the first rendered water surface); and it relaxes FML's hard "requires some Sodium" so the Fabric build of Sodium can satisfy it. As a bonus, **the mod pages inside Sodium's video settings are filled in from both sides too**: with NeoForge Sodium running, the Fabric mods' pages appear (iris / voxy / continuity / lss / sodium-extra); with Fabric Sodium running, the NeoForge mods' pages appear (iris / c2me and others).

**This is an "it runs" guarantee** — the coverage is launch → world → water rendering → settings-page links. Unusual version pairings and heavy shader packs are on you to test; if something breaks, report it with logs as usual.

**CTM note (updated 2026-10-19)**: Continuity's connected textures run through Sodium's native FRAPI emitter (`emitQuads`) on the **Fabric Sodium** build, and through Sodium's default level-aware emitter (the five-argument `collectParts`) on the **NeoForge Sodium** build. The bridge adds a self-contained helper inside Continuity's jar (`ContinuityCompat` + `NeoFobricCtmHelper`) and makes Fabric's `WrapperBlockStateModel` pass the five-argument call through to the wrapped model — Iris's wrapper was swallowing the world context and making NeoForge fall back to the two-argument path. **Both Sodium builds now work with Continuity**; the Neo build's own `sodium-frapi` config stays skipped by its `requiredMods` (no need to enable it, and a fake mod-id shell remains a bad idea). Verified with the full set + Iris (shaders on and off), the minimal Neo set, and the minimal Fabric set: connected glass walls and connected bookshelves.

PS: this is a just-for-fun experimental test — if things break, we take no responsibility~ About voxy: use **voxy 0.2.21-beta, the build made in this project's own fork** (branch [`Up-dev`](https://github.com/Lonmo0208/voxy/tree/Up-dev); it is the one in the list below) — it fixes compatibility with the latest Sodium / Iris (LODs load normally with shaders enabled) and **fixes the frame-buffer crash when Ixeris is enabled** (the two can now run together; verified in this repo, world entry with no crash). Fabric builds of Sodium and Iris are still the suggestion. **Note: that 0.2.21-beta is a 26.2 build; there is no 26.3 build yet, so on 26.3 you currently only have Modrinth's 0.2.20-beta (see the 26.3 shader note above).**


## Known incompatibilities

**FFAPI (Forgified Fabric API) cannot be installed alongside this** — it is a NeoForge mod carrying the **same packages** (`net.fabricmc.fabric.*`) as the real Fabric API the bridge bundles, so two modules in one layer export the same package and the JDK refuses to boot. Pick one. **Sinytra Connector cannot be installed alongside this either** — it fights for the same job (bringing Fabric mods into NeoForge). Also note: **running side by side is not the same as seeing each other** — the two mod sets run fine in parallel, but a Fabric mod depending directly on a NeoForge mod's API (or the reverse) is **not guaranteed**. And there is that **self-contradicting** upstream tag assertion mentioned above.

**26.3 shaders / Iris / Voxy**: 26.3 changed the transparency and particle pipelines, so **older shader packs** are marked `Outdated` by Iris; without an `opaque_particle` / `translucent_particle` program, particles render as huge grey quads. Use a **26.3 shader pack** (e.g. Complementary **r5.9.3**). Iris **1.11.x** still has this particle-pipeline bug on 26.3, **the Iris 1.12 snapshot fixes it**, but **Voxy 0.2.20-beta declares itself incompatible with Iris 1.12+** (`breaks iris >=1.12-`) — currently either/or. Stripping that line from the jar only gets it to launch: **under Iris 1.12 Voxy's LODs render pure white**, because its shader integration only understands Iris 1.11.x.

## Install & Build

**Build from source** (no prebuilt installer is kept in the repository):

```bash
git clone --recurse-submodules <this repo>
cd NeoFobric
./gradlew :neoforge:installerJar      # artifact lands in projects/neoforge/build/libs/
```

A plain clone works too: `settings.gradle` fetches the `fabric-loader` submodule on demand (pinned to a fixed commit). **Install**: (1) install **NeoForge 26.3** the official way; (2) run the installer you just built; (3) drop **Fabric and NeoForge mods together** into `mods/`. **Uninstall / roll back**: remove this loader from the install, or just reinstall NeoForge over it — nothing irreversible is written into the game.

### The first launch is slow, and that is normal

**The first launch takes noticeably longer.** The bridge has to **process every Fabric-side mod once**: it scans their jars and **writes patched copies into a cache** (under `.neofobric/`) for the parts that need adjusting — **above all the Fabric API modules' mixin anchors**, which have to follow the vanilla call sites NeoForge moved. After that, everything loads from the cache:

- **First launch**: slow (about 10 seconds for 94 Fabric jars on our machine; longer on a cold disk or a slow one, on top of the game's own first-time resource loading);
- **Later launches**: about the same as ordinary NeoForge;
- **★ Touch the mod set at all (add, remove or swap one mod) and the whole cache is invalidated** — the next launch pays that cost again. "Slow once after changing mods, fast afterwards" is the normal rhythm, not a hang;
- To bypass the cache: add the JVM argument `-Dneofobric.noPatchCache=true` (debugging only — it gets slower).

## Licensing & attribution

See [README-LICENSE.md](README-LICENSE.md): **our own code** follows NeoForge's `LGPL-2.1-only`; the bundled Fabric API modules and fabric-loader are `Apache-2.0` (each keeps its own notices); we did **not copy** any code from FFAPI (Apache-2.0) or Sinytra Connector (MIT) — only their publicly documented approaches are referenced in comments — and both are credited in the licence file.

---

## About this project

**This is an AI project** (humans set the direction and check results; much of the code, documentation and debugging is done by AI), which is why we call it a pile of garbage ourselves. But this pile has standards: **it has to be better than Forbric's** — it uses the **real fabric-loader** (not a re-implementation of Fabric API semantics), it **does not throw NeoForge away** (the base stays, NeoForge mods keep working, and with no Fabric mods it is an ordinary NeoForge), and it ran **Fabric API's own test flow** to 114/114 and wrote the result down instead of hand-waving.

**One more time: this is a pile of AI-written garbage.** It is **certainly not as good as Sinytra Connector + FFAPI** — those are serious engineering; this is a pile that wandered off the proper path. But it was written to show Forbric two answers to the same question: **if the steering wheel is handed to an AI either way, why is yours a stinky pile while mine at least smells like chocolate?** If you want a "serious" NeoForge ↔ Fabric interop, go look at **Sinytra Connector + FFAPI**; this one is a for-fun detour.

---

[↑ 回到顶部（中文版）/ Back to top](#neofobric)
