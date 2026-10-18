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


## 第二阶段：原生路径执行主干增量（2026-10-19）

本轮接入了生产原生请求、续路和移动 tick，第二阶段尚未整体验收完成，不能把本节
理解为“完整原生路径及全部特殊路线已完成”。第一阶段的共享碰撞基础继续使用。

- 新增 D2PathTick，按 PathMisc.cpp #10226 / Step.cpp 消费 ACTIVE、原生点游标、
  nDist/nDistance、第五次加速度更新、阻挡后的游标及 0x8/0x4 标志和中心复位。
- NativePathRuntime 在可信 raw fp16、MonStats/NativeUnitFlags、有效 Zone 网格及完整
  RoomEx 邻接来源下接管普通怪物路径、Toward、CW/CCW circle 和 type13 chase。
  普通模式请求按 MonsterMode.cpp 使用 type13、默认搜索距离5、续路预算20；初次
  失败按 sub_6FC65680 重试 type15。续路使用 D2PathContinuation，停止依赖宿主0.5秒
  追踪计时器和动态阻挡 A* 恢复。原生拒绝不自动改走宿主 A*。
- 请求走真实共享位 remove owner / optional target / search / restore target / owner，
  同一 tick 的多个子步不重新同步旧 Position，最终提交 raw 位置后再发布占位。
  房间缓存使用原生有序邻接，跨 level 接缝更新 MapWrapper；网格替换时重抓房间图。
  外部传送/精度失效及目标 allocation token 失效会撤销旧游标，避免回写旧坐标。
- 旋风构造 mask 保持0xC01，移动改为完整0x1025，读取 NO_PATH 并排除自身 footprint；
  不再把高位全部截掉。缺少生产碰撞服务的旧工具世界仍可使用地形射线。
- 跳跃在可信 raw 位置、25Hz 与 MonStats.Run 安全整数域内，使用 type9、Run<<8
  和原生 tick 推进；飞行清除 footprint、落地恢复，冻结不丢低位。原有技能调用者
  仍负责起跳动画、目标/落点选择。没有可信输入时保留旧兼容实现。
- Pathfinder、VelocityAdder、SequenceHandler 的技能表读取接入现有 ServerGameplayContext，
  不扩大架构回归的全局访问基线。

仍需收口：玩家/所有特殊技能的生产调度尚未全部迁移；type0/1/7/8/11/12/16 虽有独立
移植模块，不能据此声称全部接入宿主调用链。显式 lead speed/目标 raw facing、技能速度/
加速度写入、各 AI 的额外模式参数和完整 native allocation lifetime 还需逐入口核对。
Charge/击退的现有宿主调用、旋风的浮点构造射线、跳跃落点搜索及起落技能阶段仍有兼容
实现。原生主干运行时只信任其已经接管的字段；未知输入不提升为已原生化。
自然地图整链、GL、跨电脑与规模性能验收仍属第四阶段，未在本轮完成。

本轮验证：build/ai-native-path-phase2-third.log 的扩展功能回归573项全部通过，
其后增加跳跃/传送保护与 World 上下文修正。最终源码定向复跑66项（含生产路径12项、
原生tick7项、共享碰撞14项及旋风/跳跃/Charge/MonSeq等既有回归），失败/错误/跳过均0；
architectureRegression 3项通过，服务端与桌面端编译通过。
最终日志 build/ai-native-path-phase2-verified.log。未跑整个 core 测试集，没有自然地图或GL验收。
## 第三阶段：动作切换保护与 MonSeq 全表回归（2026-10-19）

第三阶段本轮完成以下增量，尚不能标记“动作表现全覆盖”整体验收完成。

- AnimationStepper 将 COF/图层就绪保护从特殊序列扩展至所有动画：存在 dirty、
  pending DCC loading 或所选 COF 与 resident COF 不一致时，不推进旧动画，也不把
  新动作速率写给旧图层。资源发布后恢复普通播放或权威 source-frame 映射。
- 新增普通动作切换回归，检查 COF 不一致、dirty、loading 三个等待阶段与加载完成
  后的恢复。保留对象速率、冻结、特殊序列倒放/保持等已有行为。
- NativeMonsterSequenceIntegrationTest 从固定14个名称扩展到当前1.10F资源
  monseq.txt 内全部 seq_ 条目，在128/256/512三种COF速率下逐点检查源帧、模式、
  非音效事件和结束时机。该测试不含 Casting，验证通用解释器，不能替代每个技能的
  生产调用链、Charge/跳跃特殊调度或每种怪物的DCC资源验收。
- 原版依据复核：SequenceTbls.cpp::DATATBLS_ComputeSequenceAnimation 返回源帧、
  模式和最后非零事件；Units.cpp::UNITS_InitializeSequence / UNITS_StopSequence
  读取返回的方向局部变量，但没有在这两处写回朝向。本轮没有凭表字段猜测转向规则。

最终验证日志 build/ai-action-phase3-verified.log：动作相关32项测试全部通过，
失败/错误/跳过均0；architectureRegression 3项通过；服务端与桌面编译通过。
其中音效3项仅验证距离/区域衰减，不能据此声称MonSeq SOUND已全链播放验收。
未运行整个core测试集，未完成全部怪物COF/DCC资源与方向、音效生产链核对，
也未进行自然地图、GL目视或跨电脑验收。第二阶段的剩余事项继续保留。

## 第四阶段：固定基线、自然地图观察与 GL 采集（2026-10-19）

本轮完成验收入口与本机取证，第四阶段仍未整体验收完成。

- 新增 tools/run-ai-dependency-acceptance.ps1，从指定已提交版本导出独立源码快照，
  记录源码包与 MPQ 的 SHA256、机器/JDK、TCP 和 GL 各自种子。即使自然地图失败，
  仍尝试 GL 并保存诊断；任一阶段失败时最终清单记 FAILED，成功采集也仅为 PARTIAL。
- 支持另一电脑使用 CompareWith 比较输入指纹；同主机、资源或参数不符不会被认作
  跨电脑验证。输入一致仍需复核运行结果，不能自动宣布跨电脑通过。
- offscreenCamp 增加可选854×480截图，默认1×1兼容。记录GL驱动、实际地图种子，
  完整截图等待角色有GPU图层、资源队列清空并连续三帧稳定，避免只截到地形就退出。

真实结果（不能合并为总PASS）：

1. 提交60c73f6d9的完整脚本运行：TCP seed=1、25Hz、地图1/2，4次局部步行、
   1次真实接缝过渡、无detached jump，200 tick入口观察完成。巡图报告visited=2，
   但AI严格检查失败：fallen1仅有24条thinkSamples，没有移动/施法/攻击记录，
   因No native combatant behavior observed退出1。warnings/errors/fallbackRecords均0，
   不表示怪物行为已通过。证据 build/ai-phase4-acceptance-60c73f6d9/natural/。
2. 同一脚本的初始GL截图虽生成854×480图片，但第6帧角色尚不可见。未把该图作为
   视觉通过；据此补齐截图就绪门槛。
3. 最终OffscreenCampScreen在上述源码快照中单独复验：第89帧、稳定3帧、角色9个
   可绘制图层、地图种子286247501（0x110FCA4D），Intel HD Graphics 630 / OpenGL4.6。
   人工查看截图已见角色、自然怪物与HUD；这是单一时刻的本机场景，不是全部动作或
   与原版对照验收。最终截图 build/ai-phase4-gl-ready-final/rogue-encampment-smoke.png，
   清单同目录 rogue-encampment-manifest.txt，日志 build/ai-phase4-gl-ready-final.log。
   最终GL运行BUILD SUCCESSFUL，核心/服务端/桌面编译完成；快照中的截图源码与提交
   文件SHA256一致：75658A5C5C8DF63694D9FDF9015E127A158AF75592BC3BD90EF4365BA69BC14D。
4. 最终脚本语法检查通过；既有AI审计有效样本及11项拒绝反例、地图/城镇往返/全部
   原生接缝证据审计脚本均通过。完整入口运行基线为60c73f6d9，最终就绪门槛另外
   做了真实GL复验；没有声称最终脚本所有路径已端到端重跑。

复跑说明：docs/ai-dependency-acceptance.md。没有第二台电脑的运行证据，没有完整自然
地图出生→接敌→门/通道/接缝→全部动作→死亡/复活覆盖，也未做规模性能或原版逐帧
对照。第二、三阶段未完成项继续保留，不能因为已进行四次处理就宣称四项全部关闭。
