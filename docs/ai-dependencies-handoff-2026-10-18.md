# AI 执行层接续交接（2026-10-18）

## 接续任务

继续完善 AI 依赖/执行层，优先推进生产共享碰撞网格。逐种怪物的思考逻辑由另一个
chat 审计，本任务不重复接管。先阅读本文件、`ai-three-dependencies-2026-10-18.md`
和 `d2moo-progress-roadmap.md`，检查当前 git 状态，再推进有原版证据的实现。

## 已完成与验证

- 功能提交 `35d952615afbee35449fd2b7ab1f3ea2dc7ba6d3` 已推送，核对 origin/master 一致。
- 普通怪物在服务端及本地权威客户端接入 25 Hz raw fp16 步进，需要可信原生位置、
  MonStats 整数速度和 GraphPath 整数节点来源。特殊技能/未知来源走兼容路线。
- 工厂出生/复活修正 MonStats.Velocity 到世界速度的 25/16 换算；显式
  setMonster(float) 保留旧世界速度契约。表速度 6、75%、25 帧移动 7.03125 subtile。
- Box2D 对 native-owned 位置只跟随，不二次积分；冻结保留 raw 低位。
- MonSeq 跨源点仅采用最后非零事件，SOUND 也覆盖之前攻击事件；序列起始帧立即
  映射源点零；新 COF 未就绪时客户端不向旧动画写权威帧。
- 最终 54 项定向测试 failures/errors/skipped 均为 0，architectureRegression 通过。
  日志 `build/ai-dependency-closeout-verified.log`（ignored，本机可用）。未跑全 core。
- 动态阻挡测试验证期间有阻挡且全程不穿透，不要求最后一帧必定处于阻挡状态；
  原生步进可能退回安全格心再重试。人工 fixture/无渲染资源测试不是自然地图/视觉验收。

## 剩余工作及注意点

接续增量已建立 Zone 持久 16 位共享网格，并将生产对象/门事件与出生查询的
地形/对象部分接入。详见三项依赖记录的“接续增量”。下述第一项的活体单位迁移、
完整事件顺序和接缝 footprint 仍未完成；不要把对象层接入误读为全部共享碰撞完成。
该增量最终 35 项定向测试及 3 项架构回归通过，无失败/错误/跳过；日志
`build/ai-shared-collision-zone-verified.log`。未跑全 core、自然地图或 GL。

1. 生产共享碰撞：目前 DynamicUnitCollisionSystem 仍为实体投影/owner 计数。
   需要持久 room/zone 16 位共享网格、事件顺序和移动/传送/死亡/复活/删除/门/对象/
   房间接缝更新。不能用每帧重建投影冒充原版重叠位互清。
   已有 D2CollisionGrid、D2CommonCollision、D2PathStep.stepUnit/tryTeleportUnit 可复用。
   大型蛙魔初始 MONSTER 3×3、NO_PATH cross；隐藏只清中心并 field-only 改 pattern，
   角格和四臂可残留，必须参考原版更新顺序，不能简单按当前实体重绘。
2. 完整原生路径：当前沿用宿主规划器及现有地形/动态碰撞回调。path flags、动态
   游标、房间缓存、距离计数、特殊技能及恢复分支未全部原生化。
3. 动作全覆盖：全部 MonSeq 模式/动作帧、源点零事件调度、特殊序列、COF/DCC、
   方向和音效仍需核对；没有完成 GL 目视验收。
4. 自然地图整链：真实 native 地图而非 synthetic/fallback，固定 seed 验证出生→
   接敌→高速移动/狭窄通道/门/房间接缝→动作→死亡复活；另验证产卵孵化、潜伏出土。
   可参考 NativeRoomMonsterPopulationResourceTest，Map builder 布局不等于 map.generate
   后真实 tile 碰撞；必须断言 native topology，不能把合成 arena 当自然地图。

## 原版证据与用户约定

- 项目 `F:\3rd_src\riiablo`；D2MOO `F:\3rd_src\D2MOO\source`。
- 原版 1.10f `G:\BaiduNetdiskDownload\Diablo II 1.10F`，难点可分析 exe/dll。
- 暗黑逻辑优先查 D2MOO；仍不明再查 The Phrozen Keep，不凭经验给结论。
- 参考 Monster.cpp、Units.cpp、D2PathResult、Path/Step.cpp、SequenceTbls.cpp；
  具体行为与边界见三项依赖记录。
- 改代码后提交并推送 origin/master，核对远端 SHA；不要输出 remote URL（可能含凭据）。
  push 输出重定向 ignored build 日志。极小改动不编译；功能修改做相应回归。
- 多 chat 共享目录；先看 git status，保留别人改动，明确文件提交，不使用全量 git add。
  不主动创建额外子代理或工作树。不要将共享网格/自然地图/视觉验收说成已经完成。

## 最近验证命令

PowerShell 设置 D2_HOME 为上述原版目录后，使用现有隔离 init：

```powershell
./gradlew.bat -I build/ai-position-contract.init.gradle --project-cache-dir .gradle/ai-position-contract :core:test --tests '*NativeMonsterMovementTest' --tests '*MonsterSpeedCompositionTest' --tests '*DiabPrisonIntegrationTest' --tests '*NativeMonsterSequenceIntegrationTest' --tests '*AnimationStepperTest' --tests '*PathfinderTerrainPursuitTest' --tests '*VelocityAdderTest' --tests '*Box2DSynchronizerPostTest' :core:architectureRegression --no-daemon --max-workers=1 --console=plain
```

隔离 init 与测试日志是本地 ignored 文件；若缺失应先检查构建配置再重建隔离流程。
测试 XML 在 `build/ai-position-contract-isolated/core/test-results/test/`，不要把旧 XML
误认为新一轮结果。正常完整构建方式以项目文档为准。

## 四阶段收敛：第一阶段生产共享碰撞（2026-10-18）

第一阶段代码接入完成，验证结果见下方记录；第二阶段完整原生路径调度、第三阶段动作全覆盖、
第四阶段自然地图/GL/跨电脑验收仍分别待办。本节更新此前“动态单位仍为 owner 投影”的状态。

- 有有效碰撞数据的 Zone 使用持久 16 位共享网格（short 存储）。玩家、怪物、PET、尸体、
  飞弹、地面物品与对象/门在状态变化时写位或清位；重叠删除/移动允许互清，不按 owner 计数
  或每帧重绘恢复。无有效 Zone 数据的 reduced world 保留兼容路径。
- 工厂分配、移动失败恢复、传送、死亡/复活、组件移除及 ECS pending deletion 发布碰撞变化。
  蛙魔隐藏遵循中心清位和 field-only pattern 切换，大 footprint 残留保留。
- footprint 跨真实原生房间接缝更新，不混入 detached overlapping level；保存网格 generation，
  旧 footprint 不清新网格。对象 mask 按 Units.cpp 区分门、视线、飞弹及 TownPortal。
- 生产路径/出生/对象落点查询读取共享位；materialized object 不再通过 owner 投影补回。
  未实体化对象仍作为分配 staging footprint。飞弹接入占位发布，命中算法未在本阶段完整移植。
- 查询前逐实体比较变化，避免重绘，但仍有 O(N) 扫描；尚未做自然地图规模性能验收。
  Whirlwind 仍保留宿主穿单位兼容路线，NO_PATH 特殊调度随第二阶段处理。
- 原版依据：D2Common Path.cpp 的分配、pattern 更新及 deadCol；Units.cpp 对象 mask；
  AiThink.cpp FrogDemon；D2Collision.cpp 共享 set/reset/invalid 0x27；Missiles.cpp Collision/Size。

未跑整个 core 测试集；人工 ECS/Zone fixture 和无渲染资源测试不能替代自然地图整链或 GL 验收。
验证收尾（2026-10-19）：扩展轮 142 项中 141 通过，新加网格替换测试因误用 8 位
terrain.or 写 0x804 失败；改为 16 位 setter 并加入删除前断言后，最终单独复跑
SharedCollisionRuntimeTest 共 14 项，failures/errors/skipped 全为 0。夹具自行设置并恢复
Gdx.app，避免测试顺序依赖。日志：build/ai-shared-collision-phase1-verified.log、
build/ai-shared-collision-phase1-closeout.log。此前扩展轮 architectureRegression 和
服务端/桌面编译通过；最新工作区 architectureRegression 为 3 项中 1 项失败，原因是
其他聊天未纳入本轮提交的 Pathfinder、SequenceHandler、VelocityAdder 全局访问增长，
不能将最新工作区架构回归报告为通过。未扩大基线或修改这些并行文件。
最终服务端/桌面编译通过：build/ai-shared-collision-phase1-compile.log（2026-10-19）。
