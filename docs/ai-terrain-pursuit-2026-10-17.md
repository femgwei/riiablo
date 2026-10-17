# AI 移动精度第四批：门宽与移动射线占地

基线：`52f1887c1`。

## 定位与原版依据

新增真实 A*、动态占地、VelocityAdder 逐帧移动回归，使用有边界的人工地形。
场景覆盖三格门、单格窄门、绕墙端点、目标移动与临时单位堵门。
每个 tick 检查移动者没有进入地形、没有重叠其他单位，路径存在时仍指向原目标身份。
这不是生成地图，也没有替代自然地图验证。

可开门怪物场景通过后，普通怪物三格门场景在原实现上失败：路径创建成功，
但 240 tick 后没有穿过门。射线旧分支对普通怪物使用 Point2.clearance，
BIG 会被当成更大的半径；可开门怪物/玩家则已使用 Map.isBlockedForPath。
终点旧分支还只检查中心格，遗漏周围占地。

本地 D2MOO `source/D2Common/src/D2Collision.cpp` 的
`COLLISION_CheckMaskWithSize`（约 775 行）明确区分 POINT、SMALL 相邻十字、
BIG 的 3×3 bounding box。开门能力不改变这个单位尺寸定义。

`RaycastCollisionDetector.findCollision` 现在对所有移动射线采样及最终落点
统一调用 Map.isBlockedForPath，沿用当前 mask、size、canOpenDoors。
不改变五次/格的浮点采样密度，不宣称原版 fp16 逐帧精度；
未修改 PathSmoother 使用的另一条 collides 接口。

## 测试边界

人工地形夹具只模拟地形位图和 Map 查找，A*、占地尺寸检查、动态单位索引、
路径恢复、射线和逐帧移动使用生产实现。可开门与普通怪物分开验证，
没有放宽碰撞、强制技能或传送移动者。夹具不启用 AI 思考；堵路解除后重新
请求路径代表后续 AI 请求，不宣称测试了 AI 的重试调度。

首次整组运行因 Mockito 保留高频调用历史造成资源压力，已中止，不计通过。
地图夹具改为 stubOnly，不保留调用历史；后续按小批次重新执行。

## 自然地图观察

修复前运行真实 1.10F MPQ、生产服务端 ECS、25Hz 和 TCP：

- Normal、seed 1、level 8：路线完成；入口及首个清障前观察均为 200 tick，
  观察期间 playerAttacks=0。Brute、Fallen、FallenShaman 有行为证据；
  Zombie 仅有更新采样，不算动作通过。
- Hell、seed 2、level 2：路线完成；QuillRat 有行为证据，Fallen/Zombie
  仅有更新采样。两轮 ai-summary 均 warnings=0、errors=0、fallbackRecords=0，
  独立 Assert-AiTourEvidence 审计通过。

输出：`server/d2gs/build/ai-tour/20261017-approach-natural-8/`、
`server/d2gs/build/ai-tour/20261017-approach-natural-2-hell/`。
这些是观察，不是 AI 正确性 oracle，也未遇到 Duriel/DeathMauler，
不能宣称所有大体型怪物已经完成自然地形验收。

## 定向验证

修正后 66 项定向测试通过：地形追踪 6、射线占地 4、大型接近点 1、
恢复与目标生命周期 27、房间边界 2、Pathfinder 14、动态碰撞 12。
两轮修复前自然巡游的完整控制台也经 Get-MapLogIssues 审计，无未审查问题。
修正后 `:core:mapRegression` 203 项通过，无失败/跳过；architectureRegression 通过。
首次修正后自然巡游在 TCP 连接阶段 Connection refused，未进入观察窗口，
对应 `20261017-terrain-final-8/` 记录为 FAILED_OR_INTERRUPTED，不计通过。
使用新目录重试修正后的 Normal/seed 1/level 8：路线完成，独立 AI 证据审计通过，
warnings/errors/fallbackRecords 均为 0，完整控制台无未审查问题。
入口及清障前均观察 200 tick、playerAttacks=0；Brute/Fallen/FallenShaman
有行为记录，Zombie 仍仅有更新采样。输出：
`server/d2gs/build/ai-tour/20261017-terrain-final-8-retry/`。

复验（先设置 D2_HOME；输出目录应为新目录）：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.server.PathfinderTerrainPursuitTest --tests com.riiablo.map.pfa.RaycastFootprintTest --tests com.riiablo.engine.server.PathfinderLargeTargetApproachTest --tests com.riiablo.engine.server.PathfinderRecoveryTargetTest --tests com.riiablo.engine.server.PathfinderRoomBoundaryTest --tests com.riiablo.engine.server.PathfinderTest --tests com.riiablo.engine.server.DynamicUnitCollisionSystemTest --no-daemon --max-workers=1
.\gradlew.bat :server:d2gs:headlessMapTour -PtourAiObserve '-PtourLevels=8' -PtourSeed=1 -PtourDifficulty=0 -PtourAiObserveMillis=8000 '-PtourOutput=server/d2gs/build/ai-tour/terrain-new-run' --no-daemon --max-workers=1
```

后续仍需有自然地图位置可复现的 Boss/大型怪定向场景，以及 raw fp16/live path
与 Act/Zone 桥。当前门宽修复不是完整原生寻路切换。
