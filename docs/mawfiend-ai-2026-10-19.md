# 深渊恶魔原地不动：追赶路径被误按吐弹距离取消

## 定位与原版依据

- 用户 `C:/Users/DELL/Desktop/game.log` 中，`regurgitator3 / Regurgitator`
  多次输出 `MONSTER_MOVE`，但没有近战/技能决策记录。该日志仅证明移动请求被接受，
  不证明坐标发生变化；用户实机观察为完全不动。
- 原版 1.10f 中文 MPQ：`MawFiend` 为“深淵惡魔”，对应 `regurgitator3/4`；
  `AbyssKnight / doomknight2` 是“地獄騎士”。此前骑士部件修复不是这个问题的修复。
- 本机 `F:/3rd_src/D2MOO/source/D2Game/src/AI/AiThink.cpp`，
  `AITHINK_Fn071_Regurgitator`（D2Game `0x6FCDD060`）：普通状态追到近战用 A1；
  吞尸技能后进入 phase 4，非近战时直接 A2，近战时先逃离；phase 5 逃离并回普通状态。
- 原版 `MonStats.txt` 四种 `regurgitator1..4` 都是空 `MissA1`、
  `MissA2=regurgitatorcorpse`。这个条件性吐弹不能当作常驻的远程追赶停步距离。

## 修复与回归边界

- `Pathfinder.process` 不再对 `Regurgitator` 的单位目标追赶应用通用飞弹距离截停；
  保留现有原生体型近战判定、地形和动态碰撞，也保留其他远程怪的截停。
- 不修改 Fn071 吞尸/吐弹状态机，不把所有配置 A2 飞弹的怪物都改成近战追赶。
- `RegurgitatorPursuitIntegrationTest` 使用真实 1.10f 表、真实 A*、
  动态占位和速度推进。四种行、三难度、距离 10/16 共 24 个场景，
  要求首帧坐标实际前进，随后到近战并创建针对玩家的 A1。
  改动前四种行都在“首帧必须移动”断言失败，复现原地不动，而不是仅检查移动请求。
  另有普通远程怪的距离截停对照；地形层为可控夹具，不是生成地图实机场景。
- `RegurgitatorCorpseAiIntegrationTest` 使用真实表和原有 ECS 决策夹具，
  覆盖四种行/三难度的选尸、吞尸、A2、逃离后回普通追赶；这里的寻路是桩，
  不宣称实际吞尸动作点、尸体移除或飞弹伤害已由这个测试验证。

## 验证

- 最终编译产物、依赖模块和测试类路径均位于
  `.gradle/regurgitator-validation-v2`。初轮输出仍落到共享 `core/build`，
  扩测期间出现 class 缺失，那轮作废；不能把缺类失败当作 AI 行为结论。
- 独立定向复验：2 类 9 项通过，失败/错误/跳过均为 0。
  包含上述 24 个实际移动场景、12 个吞尸状态机场景及 1 个普通远程对照。
- 扩测 `RegurgitatorPursuitIntegrationTest`、`Pathfinder*Test` 和 `ai.*`：
  80 类 850 项，848 通过、2 失败、错误/跳过为 0；模块边界检查通过。
  两项失败是 `PathfinderRecoveryTargetTest.skillOwnedMovementDoesNotUseOrdinaryApproachFallback`
  的无资源夹具空指针，以及 `PathfinderTerrainPursuitTest.pathAndSweptMovementGoAroundWallEndInsteadOfCuttingTheCorner`
  的绕墙高度断言。没有改动这两个非本次问题。
- 用 `b009b61d7` 的原始 `Pathfinder.java` 单独编译、以前置类路径替换运行对照：
  两项通用失败均复现，四种 regurgitator 均在首帧位移断言失败，普通远程对照通过
  （7 项中 6 失败）。该对照不改工作区生产源码，也不覆盖修复后的 class。
- 可用以下命令复现定向检查；去掉两个精确过滤、改用 `Pathfinder*Test` 与 `ai.*`
  可扩测。隔离初始化脚本同时设置输出与类路径，避免其他任务清理共享 class。

```powershell
.\gradlew.bat -I docs/mawfiend-ai-validation.init.gradle :core:test `
  --tests com.riiablo.engine.server.RegurgitatorPursuitIntegrationTest `
  --tests com.riiablo.engine.server.ai.RegurgitatorCorpseAiIntegrationTest `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --console=plain --max-workers=1
```

未运行全 core 或完整桌面构建；尚未在另一台电脑验收实际 Act IV 遭遇。
