# 单机 / D2GS 公共系统装配（第一批）

日期：2026-10-16。行为基线：本批修改前的 `d24537104`。

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
GameScreen 的联网客户端仍不进入 `socket == null` 权威注册分支。
其余四组保留原有客户端副本注册，不因名称包含 server 而擅自移除。

`ServerSkillSystem(true)`（单机旧表现分工）、`ServerSkillSystem()`（D2GS）、
`DynamicUnitCollisionSystem` 和 `VelocityModeChanger` 的模式参数仍在入口显式选择。
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

根据两台电脑的回归结果，再决定是否提取显式的模式配置和会话上下文。
任务、佣兵、AI 在两端的位置不同，需要独立行为证据才能调整，不在提取注册时重排。
