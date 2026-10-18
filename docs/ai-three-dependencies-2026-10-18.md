# AI 三项依赖联合推进（2026-10-18，未整体完成）

## 接续增量：Zone 共享位网格与对象/门

- Zone 新增持久 unsigned 16-bit `D2CollisionGrid`，首次访问拷入当前地形，后续
  DT1 `or` 同步置位；存储使用本地坐标，Zone 重定位不丢位，重新分配尺寸和释放时清理。
- 生产 `ObjectCollisionUpdater` 在创建、模式变化、移动、换 Zone、删除时更新共享位；
  未变化的对象不重写。重叠对象之一清位会清掉同位，后续帧不会按剩余 owner 自动恢复。
- 对象掩码依据 D2MOO `Units.cpp::UNITS_GetCollisionMask`：门按 BlocksVis /
  BlockMissile 选择 0x806 / 0x804 / 0x400，普通对象 0x404 / 0x400，
  TownPortal SubClass=4 使用 0x8000。低位也共享，因此清门可清掉重叠地形的可见性位。
- `isFreeForMonsterSpawnMask` 的地形/对象部分读取生产共享网格；不再把对象/门
  reference count 投影为高位。旧行走规划器继续使用独立 reference 层。
- **边界**：活体 PLAYER/MONSTER、NO_PATH/PET、飞弹、尸体和物品仍沿用原投影查询；
  `DynamicUnitCollisionSystem` 的行走索引仍每帧重建。跨 Zone 的整对象迁移已覆盖，
  跨接缝 footprint 裁切/相邻房间解析尚未迁移。不能据此宣布生产碰撞全链已经原生化。

最终验证：`build/ai-shared-collision-zone-verified.log` 构建成功，35 项定向测试
（对象/门 12、出生搜索 10、原生移动 8、底层网格 5）及 3 项架构回归全部通过，
failures/errors/skipped 均为 0。未跑全 core，部分模块架构依赖任务为 UP-TO-DATE。
前次 final 日志因另一聊天并发新增战斗测试、其对应主类尚未重新编译而失败；
方法源码就绪后完整重跑上述入口通过，没有修改该聊天文件或放宽断言。
以上为无渲染人工夹具，不替代自然地图或 GL 验收。

## 本轮收尾：生产移动和动作衔接

- 普通怪物现在可在服务端及本地权威客户端走 raw fp16 步进；输入必须同时具备
  原生位置、MonStats 整数速度、GraphPath 整数节点来源和 25 Hz tick。未知来源、
  非固定 tick、Leap/Whirlwind/DiabRun 等特殊技能仍回退原路线。
- 工厂出生/复活的 MonStats.Velocity 按 25/16 换算世界速度，修正旧的单位缺失；
  显式 `setMonster(float)` 仍保留世界速度契约。原生步进按整数百分比生成速度，
  AI 与状态加速不重复应用；冻结保留坐标低位。Box2D 只同步已提交位置。
- 原版依据：Monster.cpp 的 `PATH_SetVelocity(nVelocity << 8)`、Units.cpp 的
  `UNITS_GetBaseVelocity` 和 `UNITS_UpdateRunWalkAnimRateAndVelocity`，以及
  D2PathResult 的 direction4096 × velocity8 归一化。表速度 6、75%、25 帧移动
  7.03125 subtile，不是 4.5 subtile。
- MonSeq 跨源点时仅保留最后一个非零事件，SOUND 也会覆盖之前攻击事件；依据
  SequenceTbls.cpp::DATATBLS_ComputeSequenceAnimation。序列开始立即映射源点零
  对应的 COF 帧；客户端在新 COF 未就绪时暂停 seek，就绪后追上权威帧。

## 剩余工作（按执行依赖排序）

1. **生产共享碰撞网格**：持久化房间/zone 的 16 位共享位网格，按原生事件顺序
   更新移动、传送、死亡、复活、删除、对象/门及跨房间操作；验证重叠单位互清。
   当前实体投影/owner 计数不能等同该语义。大型蛙魔潜伏的角格/NO_PATH 残留也
   依赖这项，不能用每帧重建投影冒充完成。
2. **完整原生路径调度**：原生 path flags、寻路入口、动态游标、房间重缓存、
   距离计数和特殊恢复/技能路线。当前普通移动虽然已接生产，仍沿用宿主规划器，
   碰撞回调也仍查询现有地形与动态投影层，不是完整 D2Common 路径系统。
3. **动作全覆盖**：继续核对全部 MonSeq 的模式/动作帧、源点零事件调度和特殊
   序列，检查 COF/DCC 资源切换、方向及音效；本轮未进行 GL 目视验收。
4. **自然地图与跨电脑验收**：在真实 native 地图（非 synthetic/fallback）验证
   出生→接敌→快速移动/狭窄通道/门/房间接缝→动作→死亡/复活；固定 seed 复现。
   客户端重点检查高速怪物是否穿墙、抖动、重复位移，潜伏/出土是否倒播或跳帧，
   沙虫产卵/孵化与复活后的占位是否正确。人工 fixture 不能替代此项。

这次“收尾”指本轮可验证增量的测试、记录与提交，不代表以上四项已整体完成。

### 本轮验证

- 最终日志：`build/ai-dependency-closeout-verified.log`，54 项定向用例全部通过，
  failures/errors/skipped 均为 0；`architectureRegression` 通过。
- 覆盖：普通怪物生产步进 8、速度合成 3、DiabPrison 12、MonSeq 4、
  客户端动画 12、人工地形路径 6、兼容移动 2、Box2D Post 7。
  新用例包含真实 Box2D 跟随且不二次积分，以及动态怪物占位阻挡。
- 首轮 52 项通过；补充用例曾因把“最后一帧必须报告阻挡”作为断言而失败。
  退回安全格心后可再次尝试，最终改为验证期间发生阻挡、全程不穿透及保留 raw。
  最终复跑已通过。未运行整个 core 测试集，架构回归中的部分模块为 UP-TO-DATE。
- 以上为无渲染/人工 fixture 验证，不替代自然地图或客户端目视验收。

## 本批实现

- 固定点：移植 `D2Common/src/Path/Step.cpp::sub_6FDACEC0` 的普通非导弹、
  碰撞检测分支为 `D2PathStep.stepUnit`，并提供 `Position.stepNativeUnit`。
  输入必须是可信 raw fp16 位置、原生每帧速度向量和路径点；不从 float 反推来源。
  覆盖同格跳过碰撞迁移、零向量格心归位、按最大速度分量截取路径点、快速移动
  中间格探测、碰撞回到最后安全格心、十个 SavedSteps 上限。
  回调可直接连接 `D2CollisionGrid.tryTeleportUnit`，以保留失败后的占位恢复。
- 碰撞：玩家死亡产生的独立遗留尸体写入 CORPSE 3×3 查询层；服务端与本地
  客户端创建时均立即登记并复制死亡地图空间。玩家身上的死亡信息不是第二具
  尸体；复活/回城不带走旧尸体占位。删除或移除 Corpse 组件后清理。
  原版依据：`D2Game/src/PLAYER/PlrModes.cpp` 的 `PATH_SetUnitDeadCollision(..., 1)`，
  以及 `D2Common/src/Path/Path.cpp` 的死亡碰撞实现。
- 表现：`AnimationStepper` 补上 `nativeMummy` 的权威源帧跟随；持续喷火的跟随
  不再依赖仍持有 Sequence 组件。现有 `AnimStepper.processNativeMummy` 是服务端
  源帧依据；不是重新设计怪物思考或伤害逻辑。

## 不能宣称完成的边界

- 初批新步进 API 当时尚未接管生产调度（本轮普通怪物接入见上文）。路径 flags、动态路径点游标、房间重缓存、距离
  计数、特殊恢复分支及权威原生速度向量来源仍需接通；Box2D/float 不自动升级。
  不能把仅有 raw 坐标或浮点 Velocity 当作完整原生路径输入。
- 生产碰撞仍为实体投影层。这里的玩家尸体修复没有将生产系统替换成共享
  16 位网格；重叠所有者之间的位互清差异仍在。测试分别验证原生共享网格规则
  和现有实体投影规则，不把两者等同。
- 木乃伊/喷火的资源级无渲染回归不等于所有 MonSeq、COF、DCC 或 GL 视觉验收。
- 未完成自然地图“出生→接敌→移动碰撞→动作→死亡/复活”整链验收。

## 验证

定向测试及架构回归日志：`build/ai-three-dependencies-validation.log`；
最终代码复跑日志：`build/ai-three-dependencies-final.log`。
最终 48 项定向用例全部通过，失败/错误/跳过均为 0；architectureRegression 通过。
覆盖 raw 步进 7、既有方向/精度 5、客户端动画 11、怪物尸体 6、通用 MonSeq 2、
人工地形追踪 6、玩家尸体 4、活体玩家标记 3、旧移动 2、房间接缝 2。
玩家尸体用例包含真实服务端 DeathEvent 的当帧登记；旧客户端创建路径做代码接入，
尚无该路径完整死亡/回城/拾尸的图形验收。
首轮曾因把 Class.PLR 等同于活 Player 而漏掉可渲染尸体，已改为实际 Player
组件判定并在最终复跑覆盖。地形/接缝用例为人工 fixture，不是自然地图验收。
未运行整个 core/AI 测试集；架构任务中的部分模块测试为 UP-TO-DATE。
本文件记录的是三项的可复核增量，不是三项一次性全部完成的声明。

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
