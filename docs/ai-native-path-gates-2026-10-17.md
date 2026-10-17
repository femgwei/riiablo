# AI 移动精度第六批：ECS 投影路径的目的地限制

基线：`d3d6f179a`。

## 缺口与修正

ECS Pathfinder.findNativePath 为 Toward、Orbit 和 type 13 chase 安装投影路径，
绕过普通 A* 入口，也遗漏了该入口已有的城镇和导出房间策略。
现在在生成前复用目的地检查，在安装前复用路径节点的房间检查；拒绝时清除旧路径、
停止速度，避免继续执行上一条移动命令。身份未分配等原有早退语义不变。

城镇依据是本地 D2MOO `source/D2Common/src/Path/Path.cpp`：
PATH_IsTargetDestinationAllowed（约 256 行）检查怪物能否进入城镇；
D2Common_10142 包装流程（约 410 行）在分派路径类型前执行该检查。
本批沿用宿主已有 npc / inTown / revive 判定，没有新增忽略城镇限制的标志。

房间部分是对齐当前 ECS 普通路径的防越界策略，而非新移植原版 active-room 包装器。
导出拓扑存在时限制到源房间及其直接相邻房间；无拓扑仍保持旧几何回退。
路径后检检查生成的节点，不等于逐帧、逐子格的完整房间碰撞证明。

## 回归

修改生产代码前，30 项恢复测试中两项新增测试失败：禁止城镇追踪、非相邻房间追踪。
允许进城的怪物测试通过。修复后另补合法目标但绕行节点越出导出房间的回归。
测试使用真实 ECS/Pathfinder 和投影算法、脚本地形/动态碰撞，不是自然地图或原版对照。

验证命令：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.server.PathfinderRecoveryTargetTest --tests com.riiablo.engine.server.PathfinderRoomBoundaryTest --tests com.riiablo.engine.server.PathfinderTerrainPursuitTest --tests com.riiablo.engine.server.PathfinderLargeTargetApproachTest --tests com.riiablo.engine.server.PathfinderTest :core:architectureRegression --no-daemon --max-workers=1 --console=plain
```

结果：54 项定向测试通过（恢复 31、房间 2、地形追踪 6、大型接近 1、Pathfinder 14），
无失败、错误或跳过；architectureRegression 通过。
日志：`build/ai-native-gates-tests.log`。本批未重跑完整 mapRegression。

## 尚未覆盖

不改变浮点转整数、动态目标占地忽略规则、原生路径标志或速度语义。
不补造 raw fp16、运行时 active-room / Act / Zone 或共享 owner seed。
普通路径仍是 Map → AStarPathFinder；Toward/Orbit 仍是有限投影，并非完整原版寻路。
本批未新增自然地图、客户端或 Boss 行为验收。
