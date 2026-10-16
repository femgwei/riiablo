# 单机 / D2GS 公共系统装配

日期：2026-10-16。行为基线：本批修改前的 `d24537104`。

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

公共注册组、显式运行模式、最小会话上下文、首组权威奖励依赖已分四批接入，
尚未完成全面会话依赖隔离或全局状态拆分。按约七批的风险边界，后续暂定三批：
客户端依赖隔离、生命周期收口、入口清理与综合回归；复杂批次仍可继续细拆。
下一批先选择一组客户端依赖，不一次替换所有 Riiablo 全局引用；
完整会话退出/重建和资源释放问题仍需后续独立处理。
任务、佣兵、AI 在两端的位置不同，需要独立行为证据才能调整，不在提取注册时重排。
不以复刻 dark-magic 的模块数量为目标，也不将架构整理计作 D2MOO 行为对齐完成度。
