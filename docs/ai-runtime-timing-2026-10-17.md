# AI 运行时精度第一批：思考截止帧

基线：`799f337bd`。本批从共享调度开始；固定点移动、生产寻路切换和完整客户端动作帧仍待后续。

## 原版依据与修复

本地 D2MOO `source/D2Game/src/AI/AiTactics.cpp`：
`AITACTICS_Idle`（D2Game.0x6FCD0110）删除旧 AITHINK 事件，按
`dwGameFrame + nFrames` 安排新事件；零等待转换为一帧。
本批没有新增 DLL 动态执行证据。

`NativeDecisionAI` 原先反复从 float 秒数减去 delta。新增回归在原实现上复现：

- 等待七帧，在第七次 40ms 更新后仍未到期。
- 权威帧从 1 推进到 11，中间不调用 AI，十帧等待仍未到期。

现在保存整数截止帧，权威模拟期间读取其 tick；旧直接调用夹具继续使用既有
`nativeFrame()` 累计帧回退。此回退不能代表脱离权威模拟时的全局事件调度。
到期仍遵守 Casting/Sequence 动作锁，不在动作中重启施法；攻击、技能、warp 的
原重试/解除等待行为保留。没有改动休眠期间其他专用计时器，也没有实现完整原版事件队列。

## 回归范围

新增 `NativeDecisionTimingIntegrationTest`：1/5/7/10/15/35/50/100 帧的精确边界、
跳过 AI 更新后的权威截止帧、到期时动作锁，以及三难度 DoomKnight 普通近战、
DeathMauler 远程技能、Duriel 冲锋拒绝后重试。三种动作测试使用真实安装表，
显式控制概率边界；沿用 stub Pathfinder，不把它们当作真实移动、碰撞或命中验收。

首次整目录 `:core:test --tests 'com.riiablo.engine.server.ai.*'`：630 项，627 通过，
三项 Andariel 动画模式断言失败（期望 14、实际 7）。随后将唯一生产改动临时恢复为
HEAD 原版，独立运行这三项，三项同样失败；已恢复本批实现。失败方法：

- `andarielMeleeSelectsSprayOrAttackAndDoesNotOverwriteActiveCast`
- `failedSkillDoesNotSubstituteBasicAttackAndWaitsTenFrames`
- `andarielRangedBranchesUseBothConfiguredSkills`

该轮整目录结果包含最初三项新增时序测试；随后增加的三类实际 AI 动作测试另行定向复验。

最终定向复验：时序 4、Spirit 6、DoomKnight 5、DeathMauler 7，共 22 项全过；
`:core:architectureRegression` 3 项全过，模块边界门禁、D2GS/desktop 编译通过。

真实 1.10f MPQ，普通难度、seed=1、每场 500 tick，筛选三行执行七场：

- DoomKnight near：30 次动作记录、27 次伤害事件；far：76 次移动记录、22 次伤害事件。
- DeathMauler near：29 次伤害事件；deathmaul-cycle 专用断言通过，32 次伤害事件。
- Duriel near：75 次伤害事件。
- DeathMauler/Duriel 普通 far 均为 NO_ACTION，原因未在本批确定，不算远程/冲锋验收通过。
- 七场均无 fallback/场内 warning/error；其余 1187 条为 NOT_RUN，不能计执行覆盖。
  此 arena 使用合成地图，仍为局部生命周期/动作证据，非自然地形或全行为正确性证明。

输出：`build/ai-timing-20261017-arena/`，results.jsonl 与逐场日志保留在本机。
随后真实 `:desktop:offscreenCamp` 三帧 PASS，正常关闭 PASS，Gradle 退出码 0；
manifest 在 `build/ai-timing-20261017-camp/rogue-encampment-manifest.txt`。
该营地烟测没有验证目标怪物的客户端动作或真实地形冲锋。

复验命令（先设置 `D2_HOME` 为本机合法 1.10f 安装目录）：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.server.ai.NativeDecisionTimingIntegrationTest --tests com.riiablo.engine.server.ai.SpiritAiIntegrationTest --tests com.riiablo.engine.server.ai.DoomKnightAiIntegrationTest --tests com.riiablo.engine.server.ai.DeathMaulerAiIntegrationTest :core:architectureRegression --no-daemon --max-workers=1
.\gradlew.bat :server:d2gs:monsterAiArena '-ParenaMonsters=doomknight1,deathmauler1,duriel' '-ParenaTicks=500' '-ParenaOutput=build/ai-timing-new-run' --no-daemon --max-workers=1
```

## 后续入口

1. 处理运行期 raw fp16 坐标、live path 字段和 Act/Zone 房间桥，先以只读逐 tick 轨迹找首次差异。
2. 真实地形覆盖直线/斜线、窄门、拐角和动态堵路；普通近战、远程绕位、冲锋分别验收。
3. 核对动作点与 COF/DCC，再扩大伤害和特殊状态矩阵。

现有路径交接明确通用生产搜索仍是 Map → AStarPathFinder；不能将独立原生路径
626 项测试或本批调度修复当作原生路径已接入。
