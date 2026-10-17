# AI 移动精度第三批：大型单位接敌

基线：`e707b141d`。

## 实测定位

新增只读决策快照（决策次数、上次目标、上次 approach 返回值、当前/截止帧、
动作锁），竞技场同时记录路径组件、房间拓扑与目标周围可用落点。
快照不重新索敌、不调用 think、不消耗 RNG；单测检查重复读取不触发路径请求。
最终快照的 componentActionLock 仅读取 Casting/Sequence，不调用可能修改
恢复计时的 actionBlocksDecision 子类钩子；这不等于所有特殊 held-mode 的决策锁。

修复前 Normal、seed 1、100 ticks：Duriel 和 DeathMauler 远场均持续决策，
target=玩家，actionLocked=false，approach=REJECTED。不存在原生房间拓扑，
因此不是休眠、索敌半径或动作锁造成的这次停滞。
两种怪 size=3，目标处 terrainBlocked=false、dynamicBlocked=true，
目标周围八个相邻落点全部不可用。

生产 Map → A* 仅在目标精确中心忽略目标占地；大型移动者的相邻节点仍与玩家
占地相撞，无法连到这个孤立终点。失败发生在首次请求，而旧接近点搜索只用于
已有路径的动态堵路恢复，因此怪物反复等待后再次失败。

## 修复边界与依据

首次普通怪物追踪单位中心失败，且动态占地阻挡目标时，复用既有半径 4 的
接近点搜索。接近点、首段路径均保留动态碰撞验证；保存原目标身份与位置快照，
不是把追踪改成一次性坐标行走。坐标命令、技能持有移动不使用此回退。
原目标城镇/房间准入及生成路径的房间后检仍保留；找不到合法路径仍失败。

D2MOO `D2Common/src/Path/Path.cpp` 的类型 13 指向
`PATH_Toward_6FDAA9F0`；`PathMisc.cpp` 该函数按碰撞和距离限制生成可用路径点，
并非要求普通追踪必须走到单位中心。这里是现有 A* 生产链的接近点修复，
**不是**该原生算法的逐指令移植或完整 flags-7/fp16 接入。

## 验证与结果

修复后真实 1.10F MPQ，Normal、seed 1、500 ticks：

- Duriel far：42 个移动样本、68 次动作；10 格场景：16 个移动样本、69 次动作。
- DeathMauler far：98 个移动样本、10 次动作；技能循环场景仍观察通过。
- DoomKnight 对照 far：76 个移动样本、25 次动作。
- 八场均 ACTION_OBSERVED，其余 1187 项 NOT_RUN；correctnessPass=NOT_EVALUATED。

补充 Hell、seed 2、500 ticks：两种怪六场均 ACTION_OBSERVED，
DeathMauler far 移动 84 个样本/8 次动作，Duriel far 移动 42 个样本/64 次动作。
两轮均无场内 warning/error 或 AI handler fallback；这不等于没有使用接近点寻路回退。
最后诊断纯读取防护改动后的定向 5 项复跑通过。

这些是合成开放地图的局部证据，不是自然地图全场景或完整客户端动作验收。
原始输出保存在本机：
`build/ai-movement-diagnostic-20261017/`、
`build/ai-movement-geometry-20261017/`、
`build/ai-movement-approach-final-20261017/`。
Hell 输出：`build/ai-movement-approach-hell-seed2-20261017/`。

定向测试包括首次失败回退、目标身份延续、完全不可达、坐标命令不绕目标、
无动态阻挡不回退、技能移动不回退、既有房间/动态碰撞/等待帧回归。
另有真实 A* + 动态占地、空模拟地形层测试，直接对照孤立中心失败与合法接近点成功。
核心定向回归 64 项、竞技场测试 14 项通过，architectureRegression 通过。
初版未限制动态目标阻挡时，既有房间拒绝测试发现多余接近点搜索；收紧准入后通过，
没有将房间限制删掉来通过测试。
真实客户端营地三帧 PASS、关闭 PASS；输出在
`desktop/build/ai-movement-approach-20261017-camp/`。此烟测不验证目标怪物表现。

复验（先设置合法 1.10F 安装路径为 D2_HOME；输出目录须为新目录）：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.server.PathfinderLargeTargetApproachTest --tests com.riiablo.engine.server.PathfinderRecoveryTargetTest --tests com.riiablo.engine.server.PathfinderRoomBoundaryTest --tests com.riiablo.engine.server.PathfinderTest --tests com.riiablo.engine.server.DynamicUnitCollisionSystemTest --tests com.riiablo.engine.server.ai.DurielMovementIntegrationTest --tests com.riiablo.engine.server.ai.NativeDecisionTimingIntegrationTest --no-daemon --max-workers=1
.\gradlew.bat :server:d2gs:monsterAiArena '-ParenaMonsters=duriel,deathmauler1,doomknight1' '-ParenaTicks=500' '-ParenaOutput=build/ai-approach-new-run' --no-daemon --max-workers=1
```

下一步优先在自然地形验证窄门、转角、第三方动态堵路和目标移动后的路径续接，
随后再推进 raw fp16/live path 与 Act/Zone 桥。
