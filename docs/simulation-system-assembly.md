# 单机 / D2GS 公共系统装配

日期：2026-10-16。行为基线：本批修改前的 `d24537104`。

## 第七批：入口清理与综合回归（2026-10-16）

基线为第六批提交 `4f63640fa`。新增 `ClientSimulationAssembly` 与
D2GS 包内 `D2GSSimulationAssembly`，分别保存两端已有的完整有序装配配方。
公共组继续即时插入，不改成延迟插件，不统一客户端和服务端的任务、AI、动画或
移动顺序。还原两个平台布尔参数和 AI tour 参数后，两端 builder 主体与基线
去掉注释、空白完全一致；没有新增游戏规则、协议或存档变更。

GameScreen 只传入原会话、音频绑定、地图/物品管理器、工厂、渲染器与平台条件；
保留 protected builder 扩展点，NetworkedGameScreen 仍在 build 前以 HIGH 优先级
加入插值/网络接收，并在原位置追加同步、Ping 与网络统计。平台判断仍在屏幕宿主，
装配类不读取 Gdx.app。D2GS 显式传入商人会话、权威物品服务和 AI tour 开关；
地图、资源、网络、World 创建、命名服务注册及交易回调仍由 D2GS 拥有。
两配方拒绝错误运行模式和缺失的必要服务；客户端另外拒绝音频与模拟时钟错配。
这些检查仅提前发现无效接线，不改变有效生产路径。

新增完整装配合同测试，检查实际 builder 输出，而不是只检查公共组：
客户端覆盖 LOCAL / REMOTE_CLIENT 和 Desktop / Android / iOS 条件；直接调用
生产 GameScreen 与 NetworkedGameScreen 方法，锁定完整顺序、权威分支、输入分支、
物理差异和 Profiler 插件位置。服务端检查自己的完整顺序、权威服务对象身份、
模式参数和显式 AI tour 开关。两端检查新会话不共享系统实例。
客户端测试绕过重资产屏幕构造，使用无 GL 的字体/贴图替身；仅构建配置、不启动
World，不将其当作画面或联网行为验收。真实资源专项与行为回归另行运行。

装配专项新增 14/14 项通过。扩大回归 core 为 244/246，D2GS 全部 42/42，
合计 286/288；两项失败均在第六批基线复现：投掷专精期望 9 / 实际 6，
房间 AI 激活测试期望 0 次更新 / 实际 1 次。基线复核将唯一改动的既有 core
生产文件 GameScreen 恢复至原版并校验 Git blob 一致，独立重跑原两类共 20 项，
同样仅两项失败；本轮不调用新增装配类。随后恢复本批代码，不改玩法来消除断言。
综合回归原始 XML 另存 `build/simulation-assembly-regression-xml/`，基线 XML 为
`build/simulation-assembly-baseline-xml/`，日志见路线图最新章节。

另一台电脑可先执行装配专项（core 9 项及 D2GS 5 项）：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.screen.GameScreenAssemblyTest `
  :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --no-daemon
```

恢复本批后另跑客户端装配与完整 D2GS 测试共 51/51，并执行真实 1.10f 营地
三帧、25Hz Tick、Throw 导弹创建/伤害及双客户端死亡幂等，全部通过。
日志 `build/simulation-assembly-final-resource.log`。保留一次 78.86ms 慢 Tick、
基础资源诊断及重复事件拒绝警告；不声称性能、人工画面/听感、完整反复退出重进
或全部资源无泄漏已验收。完整结果及剩余边界见路线图最新章节。

## 第六批：屏幕外部订阅生命周期（2026-10-16）

基线为第五批提交 `bf5f1a36a`。审计确认 GameScreen.hide 原先对
KeepControlPanelGrouped 使用 clearStateListeners，会删除其他订阅者；窗口监听和
输入处理器没有重复挂接保护；玩家/佣兵物品的 cursor location listener 只有添加，
CharData.clearListeners 也不清理这类监听。本批针对这些外部订阅收口，不尝试一次
修复地图/DT1 的已知所有权问题或重做 D2GS 停机线程。

新增屏幕内部 `ScreenSubscriptionScope`，在原 show 位置按原顺序挂接，逆序解除。
激活/解除幂等；隐藏后可重新激活；最终释放后拒绝激活并清除注册闭包。若注册过程的
初始化回调失败，包含当前注册一起回滚；解除时继续处理其他注册并保留原始异常及
suppressed 错误，不把失败吞掉。它只管理订阅，不管理 World、地图、资产或线程。

- visibleSubscriptions：17 个按键、3 个输入处理器、窗口边界、桌面显示设置监听。
  hide 只解除当前屏幕自己的监听，保留其他订阅者。输入/窗口宿主使用首次配置时的
  实例，不在释放时重新读取可能已被替换的 Riiablo.client/input。每次重新激活窗口
  监听仍重置 prevY 并立即执行原初始化回调；Cvar 的 onLoaded 时序保留。
- sessionSubscriptions：构造时按原位置挂接玩家和佣兵背包的同一个 cursor，hide
  不解除；最终释放时配对移除。ItemData 新增按身份移除一个 location listener 的 API，
  不清空其他监听或另一条相同 cursor 注册，不修改物品位置逻辑。
- dispose 即使没有先 hide 也解除两组订阅；正常重复 dispose 不再重复保存/释放。
  释放结束仅在 Riiablo.game/engine 仍指向自身时清除旧别名，不覆盖新会话；已释放
  屏幕 resume 不再重新发布自身，show 明确拒绝。隐藏时不清全局别名，保留加载流程。

GameScreen 的 World 装配方法保持不变，25Hz、模式、玩法、协议、存档未改。保留原
保存角色/Automap、CharData.clearListeners、World、演员/Stage、预载资产的释放路径；
map.dispose 的历史 FIXME、scaledStage、其他 UI/表资源与服务端停机资源边界没有在
本批解决。show 的地图实体/玩家重建语义也不改变，幂等保护仅针对外部订阅，不声称
任意连续 show 都能完整恢复游戏，更不声称 GL 资源全面无泄漏。

测试分三层：Scope 的顺序、重复调用、失败回滚和异常传播；实际 GameScreen 配置/
hide/dispose 方法在 mock 宿主上验证监听归属、宿主替换、直接销毁、旧/新屏幕隔离、
光标绑定和释放；真实 ItemData 位置变化验证只移除一条监听。GameScreen 测试绕过
资产密集构造，不等同真实 GL 屏幕退出重进。自动测试和真实资源证据见进度路线图。

## 第五批：客户端环境音依赖（2026-10-16）

基线为第四批提交 `c466a087b`。客户端新增 `ClientAudioSession`，仅绑定宿主的
环境时钟、实时玩家编号提供器、Audio 服务、SoundEnviron / Sounds 表。GameScreen
在创建 World 之前装配；玩家编号通过 `() -> player` 实时读取，不把创建 World 时
尚未就绪的玩家编号固定下来，也不再通过其他屏幕的 `Riiablo.game` 找监听者。
时钟与 `SimulationSession.environment()` 是同一个对象；音效表和服务只绑定引用，
不复制资源、不创建第二个音频服务、不接管宿主资源释放，D2GS 不安装这个客户端绑定。
静音宿主可显式传 null Audio，其余必要依赖在构造时拒绝缺失。

`SoundEnvironmentSystem`、`ObjectAmbientSoundSystem`、`SoundEmitterHandler` 显式接收
同一个绑定，三者不再直接读取 Riiablo.game/audio/files；环境音也不再注入 environment
字符串别名。原三处安装位置、模式分支、25Hz、声音编号、昼夜选择、衰减/渐变、后台
暂停与 dispose 调用顺序保留。随机事件仍使用原 MathUtils.random，不在本批改变 RNG。
还原这三处构造参数后，GameScreen 装配方法与基线除空白外一致。

`ClientAudioSessionTest` 和 `ClientAudioSessionIntegrationTest` 共新增 11 项，
包括 LOCAL / REMOTE_CLIENT 双世界复用实体 ID 的交替 Tick、不同表/时钟/播放端点、
冲突环境别名、延迟玩家赋值与更换、切区、后台暂停、循环事件时长、火炬熄灭/离开范围、
不清理非本系统对象音源、原生河流地形循环、缺失记录和静音宿主，以及销毁后重建。
集成测试实际运行 Artemis World，音频播放端点使用 mock，不代表设备听感验收。
测试刻意断开旧全局游戏/表入口，并放入不相关的全局 Audio，证明本组接线不再依赖它们。

边界仅为首组客户端环境音依赖：WeatherRenderSystem、FootstepEmitter、MonsterSoundEmitter、
输入、渲染、UI 及其他音效仍有全局引用；Audio 内部的 CVar/资源路径和共享随机数也没有
全面隔离。因此不能声称整个客户端或两个完整游戏会话已独立运行，完整退出重进、设备
听感和长时间泄漏仍待后续验证。自动验证结果和日志见进度路线图最新章节。

## 第四批：队伍与死亡奖励依赖（2026-10-16）

基线为第三批提交 `ce1e7d6a5`。上下文在 mode/map/environment 之外增加宿主已有的
`PartyManager`，仍不自行创建或释放资源。两端从 `session.partyManager()` 给
`PartyMemberSyncSystem`、`ExperienceManager`、`DeathRewardSystem` 显式传参。
三个系统不再从 `partyManager` 字符串别名注入这项依赖；绑定引用不可替换，
空参数构造时拒绝。ExperienceManager 移除仓库内无调用的运行时 setter，防止奖励
解析器初始化后管理器与其引用不一致。单人测试夹具改为显式传入同一个空队伍管理器，
保留原无队伍分支，不把空依赖当作静默降级路径。

上下文仍注册原 `partyManager` 别名，其他任务、AI、交易、掉落消费者继续使用同一
宿主对象，不在本批全面迁移。联网客户端原来保留的 ExperienceManager 也只改参数，
不额外安装权威掉落或队伍快照系统。组内/两端 Tick 顺序、经验/掉落公式、RNG、
协议、存档和 dispose 流程不变；本批不删除 Riiablo.gameSeed/表资源等全局依赖。

`DeathRewardIntegrationTest` 新增本地/服务端双世界奖励隔离、实时退队和重复事件
断言，以及冲突别名不能覆盖三个系统显式依赖的断言。两个世界实体 ID 故意相同；
实际 DeathEvent 驱动经验、奖励快照和掉落实体元数据，不用仅反射字段作为行为证据。
100 基础经验、两名同级成员得到 67/67 的固定期望，核对 D2MOO
`SUnitDmg.cpp::SUNITDMG_DistributeExperience` 的 `89/256` 组队加成；未修改公式。
测试掉落使用原有 boss 合成夹具和真实物品表，不代表新增原版 TC 对齐或自然概率验收。

隔离范围仅为这组队伍绑定和 ECS 奖励数据。GroundDropOwnership 的静态拾取缓存、
ItemGenerator/表资源等全局对象仍存在；测试不据此宣称完整双会话并行拾取或游戏已隔离。
完整人工操作、画面和长时间泄漏验收仍待。

本批最终 89/89 项、三端编译通过；首次 11 项失败为克隆夹具缺少门户注册表，
仅补其真实依赖后重跑通过，未改断言。真实营地三帧、25Hz Tick、Throw 和双 TCP
客户端死亡幂等专项通过；幂等场景 groundItems=0，不等同于网络掉落创建验收。
日志保留基础诊断、出生重定位及重复事件拒绝警告，未发现 ERROR/Exception。
全部证据和其他既有问题见 `d2moo-progress-roadmap.md` 最新章节。

## 第三批：最小会话上下文（2026-10-16）

基线为第二批提交 `996f77c8a`。`SimulationSession` 仅保存三个不可替换的引用：
运行模式、地图、环境时钟。资源仍由 GameScreen / D2GS 创建和拥有；上下文不持有
World、网络、渲染或全局单例，不自行 dispose 地图/时钟，也不复制运行态。
新游戏由宿主提供新对象；同一会话切 Act 则继续读取原地图的实时 Act。

两端先构造上下文，再在原位置装配系统。迁移的第一个依赖边界是环境组：

- `EnvironmentSystem(Map, EnvironmentCycle)` 必须显式传入地图和时钟。
- `EnvironmentObjectSystem(EnvironmentCycle)` 必须显式传入时钟。
- 两个系统不再通过字符串注入这组依赖；缺失依赖在构造时直接失败，
  不把未绑定地图静默当作 Act I。正常生产入口始终有地图，此项不是玩法规则变更。
- `registerServices` 将同一个 context、map、environment 分别注册为
  `simulationSession`、`map`、`environment`，让尚未迁移的渲染、音频、
  网络接收、任务和控制台消费者继续获得原对象，不建立第二套环境状态。

环境系统在联网客户端仍注册并推进时钟，保留原有表现时序，不借这次迁移更改网络
校时策略。篝火系统仍仅用于单机/D2GS 权威世界。系统顺序、25Hz、协议、存档和
资源释放流程均不重排；尚未清理 `Riiablo.engine`，也未声称支持完整双会话并行游戏。

`SimulationSessionTest` 验证所有模式的绑定身份、旧别名真实注入和缺失参数拒绝。
`SimulationSessionIntegrationTest` 验证真实 ECS 双世界交替 Tick、实时 Act、
销毁/重建后新时钟隔离、宿主地图未被隐式释放、构造依赖不被其他别名覆盖，
以及昼夜过界在同一 Tick 到达篝火并在白天清理序列。
这些集成测试使用无资源地图替身，不等同于完整 GameScreen/D2GS 退出重进或泄漏验收。
生产接线另跑真实资源营地/Tick/导弹测试，所有结果仅为自动验证，不冒充人工实测。

本批结果：新增 18 项，加现有模式/装配/环境回归共 67/67；三端编译通过。
真实 1.10f 营地三帧、D2GS 25Hz Tick、Throw 导弹生命周期和 1000→992 伤害通过。
日志保留 level0/DrlgType0 跳过和 Java object 分配回退警告，未发现 ERROR/Exception。
证据路径及其他批次遗留问题见 `d2moo-progress-roadmap.md` 最新章节。

## 第二批：显式运行模式（2026-10-16）

基线为第一批提交 `342aad7a3`。新增无会话状态的 `SimulationMode`，区分
`LOCAL`、`DEDICATED_SERVER` 和 `REMOTE_CLIENT`。GameScreen 仅在装配入口
根据 socket 选择模式，D2GS 显式选择服务端模式，不把 socket 推入引擎配置。

- 单机：保留权威注册分支、动态单位碰撞、本地跑走输入、Networked 模式更新和
  `ServerSkillSystem(true)` 旧适配分工。
- D2GS：保留动态单位碰撞和 Networked 模式更新，不读取本地跑走输入，
  技能仍等价于 `ServerSkillSystem(false)`。
- 联网客户端：不进入权威注册分支，动态碰撞系统仍注册但不建立权威足迹，
  保留本地跑走输入，不重新推导 Networked 单位的 COF 模式；创建权威技能系统直接拒绝。

三个模式均即时创建新的系统实例。模式只集中现有构造参数，不持有库存服务、map、
socket、World 或其他可变资源。库存修订服务、AI tour 开关仍由宿主显式传入原注册组。
GameScreen 中非装配用途的 socket 判断未改动，Box2D/VelocityAdder 差异和所有 Tick
插入位置保留。本批没有新增或修改 1.10f 游戏规则，不是新的原版行为移植。

`SimulationModeTest` 锁定三种模式的参数、远端技能拒绝、实例隔离及 builder 即时插入。
`SimulationModeIntegrationTest` 使用真实 Artemis World 验证动态足迹阻挡与
Networked 移动模式归属，并确认客户端未联网单位的动画更新没有一起被关闭。
测试不依赖 MPQ/GL；生产入口另跑真实营地和 D2GS 资源专项。

第二批结果：新增 18 项与既有装配/移动/碰撞回归合计 62/62，三端编译通过。
真实 1.10f 营地三帧、D2GS 25Hz Tick 和 Throw 导弹伤害通过；日志保留
level0/DrlgType0 跳过与 Java object 分配回退警告，另一台电脑画面/操作仍待验收。
完整证据和未解决的第一批既有问题见 `d2moo-progress-roadmap.md` 最新章节。

## 第一批：公共注册组

## 范围

`core/.../engine/SimulationSystemGroups.java` 集中创建两端已有的 38 个系统，
分成 9 个有序注册组。`GameScreen.getWorldConfigurationBuilder()` 和
`D2GS.create()` 在原位置通过 `.with(SimulationSystemGroups.xxx())` 注册。
每次调用返回新的数组和系统实例，不共享游戏会话的运行态。

这批只提取相同的注册片段。两端原有的整体顺序、网络客户端条件、构造参数和
单机 Box2D / 服务端 VelocityAdder 差异保留；没有统一为一套新的 Tick 顺序。
没有改变 1.10f 规则、网络协议或存档格式，也没有引入新的 Gradle 模块。

## 注册组与边界

- `stateAndActions`：状态更新、体力/魔力/药水恢复、佣兵药水、导弹碰撞、Actioneer。
- `unitLifecycle`：怪物尸体、单位生命周期、原生玩家目标槽。
- `quests`：Act I–V 任务及 Act II 特殊任务、佣兵任务奖励。
- `ownedUnits`：佣兵跟随、召唤物、刺客陷阱、佣兵技能；避免只注册跟随而漏掉技能。
- `questItemRewards`：Countess 和 Charsi 奖励。
- `worldObjectEffects`：对象/法杖掉落、神殿、陷阱；D2GS 显式传入物品修订服务，
  单机传 null 延续原有库存路径。
- `roomPopulation`：RoomEntityTracking → RoomActivation → AI；AI tour 诊断开关由宿主传入。
- `sequenceAnimation`：SequenceHandler → AnimStepper，先选取动作再推进关键帧。
- `objectCollision`：对象碰撞 → Bone Wall/Prison 单位足迹。

任务、所属单位、任务物品奖励、对象效果、房间激活这五组仅用于权威世界。
GameScreen 的联网客户端仍不进入权威注册分支（第一批为 `socket == null`，
第二批等价改为 `mode.isAuthoritative()`）。
其余四组保留原有客户端副本注册，不因名称包含 server 而擅自移除。

`ServerSkillSystem(true)`（单机旧表现分工）、`ServerSkillSystem()`（D2GS）、
`DynamicUnitCollisionSystem` 和 `VelocityModeChanger` 的模式参数第一批仍在入口显式选择，
第二批由 `SimulationMode` 集中创建，入口继续决定安装位置。
UI、音频、渲染、传输、会话资源注入及宿主对象生命周期仍由各入口负责。
后续可以继续提取这些边界，但不在本批改动中顺带更换职责。

使用即时数组而非 ArtemisPlugin：插件延迟到 build 阶段安装，会改变它与宿主
前后系统之间的位置。这里直接调用 builder.with，保持原注册位置。

## 验证入口

`SimulationSystemGroupsTest` 不启动 World/GL 或读取 MPQ，锁定九组的类型和顺序，
验证 Artemis builder 实际安装位置、系统实例隔离、跨组无重复、库存服务及 AI 诊断参数。

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.SimulationSystemGroupsTest `
  :server:d2gs:compileJava :desktop:compileJava --no-daemon
```

行为回归运行现有 CombatPipelineIntegrationTest、SequenceHandlerTest、
UnitLifecycleSystemTest、MercenaryFollowSystemTest、DeathRewardIntegrationTest 和
Act1Quest*Test。真实资源通过 D2_HOME 或任务的 d2Home 参数注入，不写入代码。

实际生产接线还需运行 `:desktop:offscreenCamp`，以及真实 D2GS 的固定 Tick、
导弹、佣兵和任务专项。自动测试结果不能替代另一台电脑的画面与操作验收。
本批最终结果记录在 `d2moo-progress-roadmap.md`。

本批结果：新增装配 21/21；行为回归 68/69，追加佣兵回归 30/30。
投掷专精固定期望 9 / 实际 6 的失败已在改造前版本复现。
真实营地三帧、D2GS Tick 与 Throw 通过；Den 多人/重连断言通过，但日志中存在
改造前同样出现的 victim=175 缺等级 ERROR。
佣兵网络测试因 Blood Moor 测试房间缺失而失败，改造前同样复现，尚不能验收施法。
完整原始日志保留在 build 下，不隐藏错误，也不将整个回归声明为全绿。

## 下一步

七批范围为公共注册组、显式运行模式、最小会话上下文、首组权威奖励依赖、首组
客户端环境音依赖、屏幕外部订阅生命周期、入口配方清理与综合回归。它们不等于
全面会话依赖隔离或全局状态拆分，不以批数代替完成度。
后续优先独立梳理地图/GL 所有权与会话退出重进，再分别迁移其余客户端依赖和
服务端停机资源；开始修改释放逻辑前必须确认共享资源与宿主所有权，不能只因
存在 dispose 方法就扩大释放范围。
该后续阶段的首批 UI / 调试 GL 归属、测试入口与地图限制记录在
[session-resource-lifecycle.md](session-resource-lifecycle.md)。
任务、佣兵、AI 在两端的位置不同，需要独立行为证据才能调整，不在提取注册时重排。
不以复刻 dark-magic 的模块数量为目标，也不将架构整理计作 D2MOO 行为对齐完成度。
