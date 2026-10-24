# riiablo 结构化迁移计划

> 目标：借鉴 dark-magic 的边界、权威状态和验证方式，改善 riiablo 的可维护性与模块验证能力。
> 保留 Java、LibGDX、Artemis、FlatBuffers 和现有 1.10f 行为实现；不引入 Go/Lua，不把游戏规则独立成 Mod。
> 下表是 riiablo 的落地计划，不是把 dark-magic 的实现直接搬过来。dark-magic 的命名 RNG、命令准入、相位屏障、回放检查点和内容分层均拆成可在 Java/Artemis 中逐步验证的子项。

## 使用规则

- 本文件是迁移范围的唯一记录。每次迁移只允许修改自己行中的范围。
- `状态` 只使用 `未开始`、`进行中`、`已完成`、`暂停`；没有通过出口条件时不得标记为完成。
- 每个迁移完成后记录提交 hash、验证命令和剩余问题，再开始下一次迁移。
- 发现跨行依赖时先更新“依赖迁移”，不得把新职责偷偷放入当前迁移。
- 迁移期间保持一个权威入口；旧入口迁移完成后删除，避免双重结算。
- 子项必须注明是“已落地、局部存在还是尚未开始”；已有 `SimulationSystemGroups`、预测缓冲或快照字段不能当作完整 dark-magic 等价实现。
- M1 按原建议的三项交付验收：依赖整理、架构检查、旧违规登记。M1-A/B/C 是审计对象，不是另起批次编号；历史单类提交不再累计成新批次。完整权威状态、事件/只读快照与网络实现收敛仍按 M2/M3/M8 的依赖推进，不能因提前完成迁包而宣称这些迁移完成。
- 2026-10-22 M1 第 3/3 批范围：统一核验已有依赖与 owner；补查 engine 的 LibGDX 设备/控件依赖和通过 presentation 适配器形成的旧反向依赖；以逐文件、逐类型和次数预算登记 owner、后续迁移及删除条件并接入架构门禁；修正网络清单/外部引用扫描的 source-set、自产包引用及计数盲点。此批不进行 M2/M3 权威状态或 M8-C 完整快照重写，不修改战斗、地图规则或协议。
- 本批 M1-C source-set 隔离补缺：将没有外部生产调用者的 `server/netty/.../d2gs_test/Main` 与 `TestClient` 原样移入 `src/test/java`，禁止生产源新增测试 harness 或引用它；既有 `net/nnet/onet/d2gs_netty` 实现的网络语义和启动入口不在本批改写，仍由 M8-D 收敛。
- M2 第 1/4 批范围：玩家/单位移动状态的权威来源、不可变接收快照、预测校正与 GPU 只读变换视图；同步修改 renderer、automap、标签/天气/调试读取与 host 输入入口，并验证渲染及回滚不修改已接收权威快照或原生精度。后续三批分别处理玩家/单位生命周期写入、物品/任务、佣兵/地图状态；总批次保持原定 3～4 批范围。战斗/移动公式和协议不变；M3 命令 envelope、完整结构屏障与 M8 回放不在本批实现。

- M2 第 2/4 批范围：玩家/单位死亡、尸体、复活与状态/生命快照的唯一接收写入接口；ESC 只捕获复活意图，在仿真 Tick 内准入；联网生命周期仅应用已提交快照，复活确认包不计算位置或生命结果。本地/服务端规则沿用既有 owner，物品/任务事务在第 3 批收口；不修改协议、战斗公式或 M3 调度/结构屏障。

- M2 第 3/4 批范围：库存、地面物品、钱包/弹药及当前难度任务记录的不可变接收状态和写入接口；拾取、装备、存储、药剂、商店和任务对话由 UI 捕获意图，在仿真 Tick 内调用既有本地/服务端规则；联网副本仅应用完整库存和带 Tick 的玩家/任务快照，确认包不重算交易或奖励。允许同步完善 D2GS baseline、NPC/任务/交易完成后的既有快照发布及拾取成功事件；佣兵/地图权威状态留第 4 批。不修改协议或 D2 公式，不提前实现 M3 envelope/完整结构屏障、M6 完整领域事务或 M8 网络三层/回放。

## 总体迁移表

| 编号 | 迁移名称 | 范围 | 目标 | 主要产物 | 出口条件 | 依赖 | 状态 |
|---|---|---|---|---|---|---|---|
| M0 | 约束与基线 | 全仓库架构盘点、模块依赖、现有测试、D2MOO 证据和 dark-magic 对照矩阵 | 固定边界和迁移基线，不改变行为 | ownership 清单、依赖图、反向依赖清单、基线测试报告 | 所有目标目录都有唯一 owner；当前行为、已知失败和架构缺口可复现 | 无 | 已完成 |
| M1 | 领域边界 | `core`、`server`、`quests`、`server-systems`、`shared` 的依赖整理 | 建立 `mechanism / data / policy / adapter / presentation` 五类边界，并登记旧违规 | 包级依赖规则、架构检查、跨层调用清单 | 新代码不能违反依赖方向；旧违规点有 owner、迁移行和删除条件 | M0 | 已完成 |
| M2 | 权威状态 | 玩家、怪物、物品、任务、佣兵、地图运行时状态和客户端预测 | 明确唯一权威状态来源，UI 和客户端只能提交意图，不能提交结果 | 状态模型、状态写入接口、快照字段清单、客户端命令边界 | 任一状态变更都能追踪到权威系统；无 UI 直接写状态；预测回滚不改变权威数据 | M1 | 已完成 |
| M3 | Tick、命令与事件 | 固定 40ms Tick、输入意图、技能、移动、死亡、实体删除、阶段调度和结构屏障 | 统一执行顺序和副作用边界，令渲染帧率不影响仿真 Tick | Command envelope、Event、结构变更队列、固定步进运行时、Tick/phase 调度图 | 固定步进可脱离渲染运行；非法意图被拒绝；阶段末统一冲刷结构变更；普通移动、拾取、一个交互流程通过纯逻辑和 ECS 测试 | M2 | 进行中 |
| M4 | 数据层 | TXT、MPQ、D2 数据表、固定点数值、RNG/Seed、版本和可选内容包 | 使用不可变记录和显式数据来源，规则层不直接操作解析器；为每个系统/实体建立可复现 RNG 流 | typed record、数据版本、命名 RNG stream、数据审计工具、可选内容包边界 | 缺失数据、版本差异、RNG 消耗都有明确测试；合法数据不走隐式常量回退；同一输入可重放得到相同随机序列 | M1 | 进行中 |
| M5 | 战斗垂直切片 | 普通攻击或 Fire Bolt 的完整链路 | 验证新边界能够承载真实 D2 行为，并能被快照/回放复核 | 命令→准入→结算→导弹/命中→伤害→快照→表现→回放 | 纯数据、ECS、D2GS、真实 1.10f 资源测试全部通过；旧入口不再结算；关键 Tick 有检查点和校验值 | M2、M3、M4 | 进行中 |
| M6 | 物品与成长 | 物品生成、装备、背包、库存事务、经验、升级、佣兵成长 | 将交易和属性聚合变成可回滚、可验证的领域事务 | Item domain、Inventory transaction、Progression service、存档字段映射 | 拾取、装备、交易、升级和重连状态无重复提交或双重结算 | M2、M4、M5 | 已完成 |
| M7 | 世界规则 | 地图生成、碰撞、寻路、怪物 AI、任务、NPC、Warp、对象 | 将 D2 规则从通用地图/ECS 机制中分离 | World policy、AI policy、Quest/NPC command handlers | Act 1 关键链路通过 headless、双客户端和真实 MPQ 验收 | M2、M3、M4、M5、M6 | 进行中 |
| M8 | 网络、存档与表现收口 | FlatBuffers/Netty、快照、重连、D2S、声音/视觉表现和 LibGDX UI/渲染 | 让客户端成为纯表现层，统一协议、持久化和表现适配边界 | semantic protocol、snapshot/replay/checkpoint、versioned save、presentation adapter、SoundEvent | 重连、回放、存档往返和客户端显示通过；服务端不直接访问音频/渲染；旧直连入口已删除 | M3、M5、M6、M7 | 进行中 |

## dark-magic 对应子项与补充范围

以下子项把前期对 dark-magic 的分析落到本计划中。它们不是新增技术栈，而是 M3/M4/M7/M8 的明确出口条件；状态仍遵守本文规定的四种值。

| 子项 | 对应 dark-magic 设计 | riiablo 落地范围 | 当前状态 | 完成判定 |
|---|---|---|---|---|
| M3-A 固定步进运行时 | `internal/game/session` 固定 25Hz，与渲染解耦 | 将 `SimulationClock` 从常量/调用约定提升为可独立推进的 accumulator；支持慢帧、暂停和插值边界 | 未开始 | headless 不启动渲染也能按 40ms 推进；同一输入序列在不同渲染帧率下 Tick/结果一致 |
| M3-B 命令准入 | `session/intent_source.go` 的 actor、序号、目标 Tick、权威等级校验 | 统一移动/技能/交互命令 envelope；D2GS 只接受意图，服务端重算目标、距离、消耗和结果 | 进行中 | 非法 actor、过期 Tick、重复序号、越权目标均拒绝且不改变权威状态；合法命令幂等 |
| M3-C 相位与结构屏障 | `ecs/schedule.go` 的 phase 拓扑和 deferred structural changes | 在现有 `SimulationSystemGroups` 外增加 phase contract、读写集登记和实体/组件变更 barrier | 未开始 | phase 顺序可检查；系统内不直接破坏当前遍历；屏障后一次性应用创建/删除/组件变更 |
| M4-A 命名 RNG | `simulation/random.go` 按名称/上下文隔离 RNG stream | 为 AI、战斗、掉落、地图和表现随机分别提供显式 stream；禁止规则层共享全局 RNG | 未开始 | stream 名称、种子和消费次数进入测试/诊断；新增系统不会改变其他系统随机序列 |
| M4-B 内容/Mod 分层 | dark-magic 的内容包、digest、版本锁和命名空间 | 当前不引入 Lua/Go Mod；保留可选的 data-pack/version/digest 接口，避免未来内容污染核心规则 | 暂停 | 只有在 Mod 成为目标后启动；启动前先定义包签名、版本兼容和多人会话锁定 |
| M8-A 回放与检查点 | `simulation/replay.go`、`ecs/snapshot.go`、校验和/恢复 | 记录命令、权威 Tick、随机 stream 状态和快照摘要；支持断线恢复与离线回放 | 进行中 | 录制→恢复→继续运行结果一致；检查点校验失败可定位到 Tick/实体/字段 |
| M8-B 声音语义事件 | 服务端只发语义，客户端负责资源和设备表现 | `SoundEvent`、客户端 `SoundEventSystem`、D2GS `SoundP`，含可见性和发声坐标 | 已完成 | 服务端无 `MonsterAudio` 调用；本地和 D2GS 远程声音测试通过；实体同 Tick 删除仍可按坐标播放 |
| M8-C 表现层剥离 | `internal/presentation` 与仿真分离 | 继续收口 `ClientGameplayContext`、NPC/UI、标签、渲染和音频资源依赖；表现只能订阅事件/快照 | 进行中 | engine/server 不依赖 UI/音频设备；客户端表现替换不改变权威 Tick/结果 |
| M8-D 网络三层收敛 | protocol / transport / authoritative session | 收敛 FlatBuffers、Netty、D2GS session 和旧网络实现；禁止规则系统直接操作 socket | 未开始 | 协议、传输、会话各有唯一 owner；重连、快照和命令路径不再存在多套权威实现 |
| M7-A 机制/策略分离 | Go ECS 保持通用机制，Lua/D2 规则作为策略 | Java 中先把可参数化的 AI、技能、掉落和任务公式移入 policy/data，不进行一次性脚本化 | 未开始 | 规则策略可由纯数据/策略测试替换；通用 ECS 机制不读取具体 D2 表行 |

### M1/M7/M8 结构债务子项

M1-B 本批依赖迁移：允许同步修改 `EntityFactory` 的地图端口能力、客户端/服务端 host 的地图装配绑定，以及 `NativeDataTables` 对共享纯数据访问器的委托；不改变战斗、任务数值或协议。`MapManager`（实体装配）、`Box2DPhysics`（物理适配）、`RenderSystem`（表现适配）保留唯一 owner，不能被地图 builder 反向调用。D2 规则策略与地图代际的整体搬迁分别属于 M7-A/M7-B。

M1-A/M8-C 本批依赖迁移：允许将输入、物品、网络结果和世界标签的具体 screen/widget 访问委托给 `presentation/ClientGameplayContext`，新增 UI 查询/意图端口，并同步修改 `GameScreen` 的消息输出绑定及 dedicated server/竞技场的装配配方，删除无调用者的 UI 系统注册。保留现有 UI 行为、权威结果路由优先级、系统相对顺序和 D2MOO 规则；不修改战斗结算或协议。

M1-A/M8-C NPC/世界 UI 迁移：将客户端 NPC、菜单/对话、任务对话和指示器、世界/怪物标签及区域提示归入 `presentation`，将 `MenuWrapper` 移出 shared；同步更新客户端装配、widget、screen、viewer 工具和相应测试的类型引用。NPC 使用所属 world/context 获取服务，保留原有交互、任务提交、资源释放和标签布局行为；不更改服务端 NPC、任务规则或战斗代码。

本批 viewer 依赖补充：扩展编译发现 Map Viewer 的 `Map.injectBuilders` 和 Skill Viewer 的 `Map.setEntityFactory` 旧入口已在 M1-B 删除，Skill Viewer 还引用旧包的 `CofDirtyComponents`；允许改为既有 `MapRuntimeBindings` host 绑定、当前 shared 组件类型及工具所属的 factory/context 注册，不改地图生成器、战斗或数据规则。

M1-A/M8-C 音频与图形收口：将声音会话、声音事件/环境/脚步/怪物/格挡/物件播放及衰减、只观察已提交伤害的客户端处理器、天气、overlay/item/missile 资源加载、地面物品光效、automap 绘制及其辅助类型、路径调试绘制归入 `presentation`；将仅被表现类使用、持有播放句柄的 `SoundEmitter` 从 server/component 迁至 presentation/component。客户端实体工厂保留实体职责，飞行音委托表现适配器。允许同步调整 host 装配、viewer、命令、日志配置和测试/门禁引用；空间播放使用所属 world 和 listener，保留原声音键、时序、增益、暂停及资源生命周期。不迁移或修改服务端伤害、AI、任务、数值或协议。

M1-A/M8-C 技能/死亡/状态归属迁移：集中将 `SkillCastHandler`、`DeathHandler`、`ElementalHitPresentationSystem`、`NativeShrinePresentationSystem`、`StateOverlaySystem` 迁入 `presentation`，同步更新客户端装配、输入、网络接收、screen、viewer、日志和回归引用。神殿图标/名称映射从实体工厂提取到 `ShrinePresentation`，`MonsterTransform` 随状态调色板恢复归入表现层，现有 COF 适配器委托同一实现。保留本地/联网权威导弹复用、尸体/复活/装备与 Box2D 生命周期、神殿快照及状态视觉恢复的全部原有行为；不修改服务端结算或协议。`DeathHandler` 仍是混合生命周期适配器，包迁移不代表死亡机制已完成事件/快照解耦。

M1-A/M8-C 实体与 COF/动画归属迁移：集中迁移客户端实体工厂、COF 解析/加载/缓存/卸载、透明度/调色板更新、动画/overlay 步进、方向显示和物品落地表现适配器至 `presentation`，对应仅客户端使用的动画、包围盒、COF 资源、标签与 overlay 组件迁至 `presentation/component`。同步修改 host、输入/网络、viewer、测试及门禁引用；`RenderSystem` 仅将原有 3 条组件依赖更换为新归属，允许项限定到这些具体类型，不放宽整个包。保持资源等待、引用计数、缺失 COF 回退、实体重用及系统相对顺序；工厂继承服务端创建逻辑和客户端适配写入仍作为 M8-C 兼容债务登记，不改权威战斗、地图生成或协议。

这组子项把前期结构审计中发现的反向依赖和多代实现单独登记，避免它们被“表现层剥离”或“网络收敛”一句话掩盖。这里的状态表示改造状态，不表示问题已经解决。

依赖与验收口径：M1 主行的三项交付已验收；M1-A 的完整事件/快照目标依赖 M2/M3/M8-C，M1-C 的唯一生产消息/会话入口依赖 M3/M8-D。两个子项保留原完成判定并记为进行中，不能用盘点完成或迁包完成替代完整改造出口。M1 主行已完成只表示依赖门禁和旧违规登记具备进入 M2 的条件。M3 已有固定步进调用约定和系统组，仍无独立 accumulator 与完整准入/屏障；M8 只有 M8-B 声音事件链路完成。

| 子项 | 审计对象 | 目标边界 | 当前状态 | 完成判定 |
|---|---|---|---|---|
| M1-A 机制/表现依赖审计 | `engine/client` 对 `screen/widget` 的反向 import；客户端 NPC、对话、标签和区域提示 | `engine` 只发布快照/语义事件，`presentation` 负责 widget、音频和渲染资源 | 进行中 | 架构检查禁止 engine/server 引入 UI、音频设备或渲染对象；现有调用均有 owner 和删除条件 |
| M1-B 地图/实体边界 | `map`、`MapBuilder` 直接引用 `EntityFactory`、服务端组件和 `NativeDataTables` | 地图只产布局、碰撞、warp 和对象描述；实体创建由 population/assembly 系统完成 | 已完成 | A1–A5 真实 MPQ/headless 布局验收通过；生成层 ECS/工厂/socket 违规为 0，实体创建统一由装配适配器负责 |
| M1-C 网络实现盘点 | `server/netty` 中 `net`、`nnet`、`onet`，以及 `d2gs`、`d2gs_netty`、`d2gs_test` 并存 | 明确 protocol、transport、authoritative session 的唯一 owner，测试实现与生产实现隔离 | 进行中 | 每个消息/会话路径只有一套生产入口；旧实现删除或标记为仅测试适配器 |
| M7-B 地图代际收敛 | `map`、`map2`、`map5`、`map/d2moo`、`D2MOO_JAVA` 并存 | 保留主地图链和 D2MOO bridge，过渡实现不再被新规则依赖 | 未开始 | Act 1 关键地图在主链上通过真实 MPQ/headless 验收；过渡目录无生产调用者 |

### 前期 dark-magic 对比核对（2026-10-21）

原结构分析的结论已逐项复核并映射到上面的迁移项。标记为“部分属实”的项目表示 riiablo 已有局部机制，但还没有 dark-magic 意义上的完整边界；不能因此提前标记完成。

| 原分析结论 | riiablo 当前证据 | 判定 | 计划落点 |
|---|---|---|---|
| 固定 25Hz 与渲染解耦 | `SimulationClock` 目前提供 25Hz 常量，`AuthoritativeSimulation`/D2GS 已按 25Hz 配置；仍缺可独立推进的 accumulator | 部分属实 | M3-A |
| 命名确定性 RNG stream | `map2.random.Random` 和多处 `RandomXS128`/`MathUtils.random` 已存在，但没有按系统/实体命名、隔离和消费计数 | 属实（缺口存在） | M4-A |
| 回放、检查点、校验和恢复 | 客户端预测与若干 snapshot 类型存在，但未形成跨系统的 replay/checkpoint/checksum 协议 | 部分属实 | M8-A |
| 意图命令与权威准入 | 客户端已有 `ClientPredictionBuffer`，D2GS 也有命令处理；尚无统一 actor/序号/目标 Tick/权限 envelope | 属实（缺口存在） | M3-B |
| ECS 相位、读写集和结构屏障 | `SimulationSystemGroups` 已表达系统组顺序；没有统一 phase contract、读写集登记和 deferred structural barrier | 部分属实 | M3-C |
| 机制/策略分离 | 数据表和若干 policy 已存在，但大量 D2 公式仍直接位于 engine/server system | 部分属实 | M7-A |
| Mod 内容包与运行时锁定 | 当前没有完整 Mod/package digest/namespace/session lock；计划明确不引入 Lua/Go | 属实（暂不迁移） | M4-B（暂停） |
| 服务端直接播放音频 | 历史审计记录 83 处；声音事件迁移后服务端不再直接播放，当前仅剩 3 处客户端表现层 `MonsterAudio` 兼容调用 | 已完成主项，仍有表现收口 | M8-B、M8-C |
| engine/client ↔ screen/widget 反向依赖 | 原 riiablo screen/widget/audio/graphics 扫描为 0；补扫 LibGDX 控件/图形类型与 presentation 后，15 个文件仍有 76 条文件/类型边、77 次引用，均有 owner 和删除条件 | 原扫描零值不代表设备/控件清零；事件/快照表现边界未完成 | M1-A、M2、M3、M6、M8-C |
| map ↔ engine/server 反向依赖 | M1-B 已删除生成层的 ECS/工厂/socket 依赖；`MapManager`、`Box2DPhysics`、`RenderSystem` 的 58 条运行时适配依赖有独立基线，规则策略位置和地图代际仍待收敛 | 生成边界已完成，适配/策略债务仍有登记 | M1-B（已完成）、M7-A、M7-B、M8-C |
| 网络和地图多代并存 | `server/netty` 与 `server/d2gs`、`net/nnet/onet`、`d2gs_netty` 仍在生产 source set；`d2gs_test` 两类已移入测试 source set；`map/map2/map5` 仍存在 | 多代实现未收敛；测试 harness 隔离已完成 | M1-C、M7-B、M8-D |

### 明确不迁移的部分

以下是对比分析中“dark-magic 不占优或移植收益不足”的结论，不再作为 riiablo 的迁移任务：

- **平台与渲染后端**：保留 LibGDX 的桌面/Android 能力；只吸收 headless 可测和表现订阅边界，不替换渲染栈。
- **D2 数据/记录管线**：继续使用现有 TXT/MPQ、table、codec、serializer 和 D2MOO 证据链，不照搬 dark-magic 的数据实现。
- **测试工具与真实资源验收**：保留地图回归、AI 竞技场、headless 和真实 1.10f/MPQ 验收；只补回放、检查点和确定性校验。
- **QUIC/TLS 传输本身**：不为迁移而替换 TCP/Netty+FlatBuffers；只迁移意图准入、协议/传输/会话分层和重连语义。

### M8-B 声音语义事件已完成记录

完成日期：2026-10-21

提交：`e24a9697e`（本地/单机声音事件桥接）、`e0c9185ca`（D2GS `SoundP` 网络传输）

验证：

```text
./gradlew.bat :server-domain:compileJava :core:compileJava --no-daemon
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientAudioSessionTest --no-daemon
./gradlew.bat :core:test --tests com.riiablo.engine.client.SoundEventNetworkTest --no-daemon
./gradlew.bat :server:d2gs:test --tests com.riiablo.server.d2gs.SoundEventTransportTest --no-daemon
```

真实资源：否；本项是协议/表现边界迁移，声音键和触发时机沿用现有 1.10f 表项。

D2MOO 依据：本项不改变暗黑数值规则；保留已有 AI 触发时机、声音字符串、可见性和距离衰减策略。

剩余问题：非 AI 的其他表现音效仍有旧的全局/直接依赖；M8-A 回放检查点、M8-C 表现包收口和 M8-D 网络层收敛尚未完成。

下一迁移：M3-B / M4-A，先建立命令准入和命名 RNG 的可验证边界。


### M1-B 完成记录（2026-10-22）

完成日期：2026-10-22

提交：`2a6416ef0`（地图生成边界、host 装配绑定和回归门禁）

验证：

```text
./gradlew.bat :core:mapBoundaryRegression :core:architectureRegression :server:d2gs:compileJava :server:netty:compileJava :desktop:compileJava "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F" --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File tools/architecture/check-map-entity-boundary.ps1 -FailOnDrift
```

结果：M1-B 回归 95 项通过，失败/错误/跳过均为 0；生成层违规为 0，运行时适配器基线 58 条，无新增漂移；架构回归和客户端/服务端编译通过。

真实资源：是，显式指定 `G:\BaiduNetdiskDownload\Diablo II 1.10F`；A1–A5 的布局与 native room 描述在无 ECS world、无实体工厂的环境中生成。

D2MOO 依据：沿用 `D2MOO_JAVA/.../drlg/DrlgDrlg.java::allocDrlg`、`DrlgOutPlace.java::createLevelConnections` 及 `DrlgPresetUnitExporter.java` 的布局/描述链；共享数据访问器保留原 `NativeDataTables` 的 DATATBLS 难度列回退、尺寸和怪物组上下界语义。本项改变生成与装配的边界，不改变 D2 公式或协议。

剩余问题：`MapManager` 是地图实体装配的唯一 owner；`Box2DPhysics`/`RenderSystem` 的运行时依赖继续由物理/表现适配器承担并受基线约束。D2 策略包位置由 M7-A 收敛，地图多代实现由 M7-B 收敛；两者均不属于本项完成声明。M1-A/M8-C 的表现依赖收口仍按原计划跟踪，M1 整体尚未完成。

下一迁移：M1-A/M8-C 的剩余表现依赖收口；M1-C 盘点已完成，网络实现收敛仍属于 M8-D。

详细记录：`docs/dark-magic-m1b-closeout-2026-10-22.md`。

### M1-A/M8-C UI host 端口迁移记录（2026-10-22）

本批完成日期：2026-10-22；M1-A/M8-C 整体仍为进行中。

提交：`f7961320d`（UI host 端口、客户端消费者迁移、dedicated 装配及回归门禁）。

实现：`ClientUiServices` 提供输入和物品所需的 UI 查询/意图；具体 panel、世界标签创建/更新和旧网络结果回调统一留在表现适配器。网络消息输出改为 host 注入的 `Consumer<String>`。D2GS 和怪物竞技场不再安装 `MenuManager`/`DialogManager`。

验证命令：

```text
./gradlew.bat :core:clientUiBoundaryRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest :desktop:compileJava :server:netty:compileJava "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F" --no-daemon
```

结果：UI 回归 111 项、架构回归 6 项、服务端装配/竞技场测试 19 项全部通过，失败/错误/跳过均为 0；core、desktop、D2GS、Netty 编译通过。表现依赖由 83 条降至 72 条，基线只删除消失的 11 条；新增违规为 0。修正既有装配断言遗漏的 `SoundEventSystem`，以及标签测试与既有紧凑纵向间距不一致的问题，未改变生产布局。

真实资源：是；更正原记录，任务指示器与共享控件生命周期测试继承 `RiiabloTest`，会加载真实 MPQ 数据表和字符串；控件/设备仍使用测试替身，不启动真实 GL 客户端或完整竞技场。此前 M1-B 地图资源验收仍以对应记录为准。

D2MOO 依据：本批保留既有 1.10f 实现，只移动 host/UI 调用边界；物品拾取、佣兵药剂、NPC/交易结果和权威 Tick/数值规则均未改写，不宣称新增 D2 全行为等价性。

剩余问题：NPC/menu/dialog、标签显示与区域提示、渲染/音频适配仍待迁移；当前端口仍经由兼容 context 访问，尚未达到表现仅订阅事件/快照的出口条件。M1/M8-C 整体尚未完成。

下一迁移：M1-A/M8-C 的 NPC/menu/dialog 与标签/区域提示表现依赖，沿现有 session 和端口继续收口。

详细记录：`docs/dark-magic-m8c-ui-host-ports-2026-10-22.md`。

### M1-A/M8-C NPC 与世界 UI 迁移记录（2026-10-22）

本批完成日期：2026-10-22；M1-A/M8-C 整体仍为进行中。

提交：`7722a22a8`（NPC/世界 UI 表现归属、会话绑定、viewer 适配与回归门禁）。

实现：NPC、菜单/对话、任务指示器、标签/区域提示及名称解析等 10 个类归入 `presentation`，`MenuWrapper` 迁至 `presentation/component`；删除旧位置，不保留双入口。客户端装配、screen/widget 和 viewer 改用新类型，装配相对顺序不变。NPC 改用所属 world/context 的玩家、角色、音频和数据绑定；怪物标签在注入后创建控件。两个 viewer 改用 M1-B 的现有 host 绑定入口。

验证命令：

```text
./gradlew.bat :core:clientUiBoundaryRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest :desktop:compileJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F" --no-daemon
```

结果：UI 回归 115 项、架构回归 8 项、服务端装配/竞技场 19 项，共 142 项通过，失败/错误/跳过均为 0；core、desktop、D2GS、Netty、Map Viewer、Skill Viewer 编译通过。表现扫描由 72 条降至 50 条（音频 40、图形 10），直接 screen/widget 违规为 0；只从基线删除消失的 22 条，未增加允许项。M1-B 生成层仍为 0，运行时适配器 58 条，无漂移。

真实资源：是；headless 用例通过 `RiiabloTest` 加载 1.10F MPQ 数据表/字符串，保留任务标记、Corpsefire 名称解析与控件生命周期验收；没有运行实际 GL/音频设备或完整竞技场。

D2MOO 依据：沿用原 NPC healer 的 1.10f 声音事件 10/声音 2673（`shrine_refill`）、问候回退及商店/复活语义；任务对话仍在打开时提交原消息，标签保留原间距、数量上限和有限避让。服务端 NPC/任务规则和战斗代码未改动。

剩余问题：音频 40 条、图形 10 条仍由 M1-A/M8-C 收敛；现有客户端系统还可经由适配器依赖表现，widget 内部仍有兼容全局资源读取，尚未达到仅订阅事件/快照的目标。

下一迁移：集中收口 M1-A/M8-C 的剩余音频播放、环境/天气、渲染资源依赖。

详细记录：`docs/dark-magic-m8c-npc-world-ui-2026-10-22.md`。

### M1-A/M8-C 音频与图形收口记录（2026-10-22）

本批完成日期：2026-10-22；M1-A/M8-C 整体仍为进行中。

提交：`13ec540f3`（音频/图形归属、会话空间播放、SoundEmitter 组件迁移及回归门禁）；`d8ae4a045` 为首份验收记录。

验证：`:core:clientUiBoundaryRegression`、`:server:d2gs:test` 的 `D2GSSimulationAssemblyTest`/`MonsterAiArenaTest`/`SoundEventTransportTest`，以及 desktop、Netty、Map Viewer、Skill Viewer 编译通过，均显式使用 `-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F`。表现回归 331 项通过、1 项可选原版 automap 存档样本跳过，架构 10 项和 D2GS 20 项通过，共 361 项通过、1 项跳过，失败/错误均为 0。直接表现扫描 50 → 0；M1-B 生成层 0，运行时适配器 58 条无漂移。

真实资源：是；相关 headless 用例读取 1.10F MPQ 数据表/字符串，没有运行真实音频设备或完整 GL 客户端。

D2MOO 依据：沿用现有 1.10f Sounds/MonSounds、Missiles、Overlay 和 automap 规则，仅改变归属及会话绑定；声音键、时序、半径/增益、暂停、资源生命周期和客户端装配相对顺序保留，服务端数值/任务/协议未改动。

剩余问题：直接依赖清零不等于仅订阅事件/快照。客户端实体工厂、技能/死亡/状态及 COF/动画适配器、context/widget 兼容调用仍由 M8-C 负责；具体 owner、删除条件和可选验收限制已登记在详细记录中。

下一迁移：集中收敛 M1-A/M8-C 的客户端实体、技能/死亡/状态表现事件/快照出口，不再按单个表现类拆批。

详细记录：`docs/dark-magic-m8c-audio-graphics-2026-10-22.md`。

### M1-A/M8-C 技能、死亡与状态归属记录（2026-10-22）

本批完成日期：2026-10-22；M1-A/M8-C 整体仍为进行中。

提交：`75d749a18`（5 个客户端入口、神殿映射和调色板辅助逻辑迁至 presentation，更新装配、网络/输入/screen/viewer、日志及回归门禁）。

验证：`:core:compileTestJava`、完整 `:core:clientUiBoundaryRegression`、D2GS 的 `D2GSSimulationAssemblyTest`/`MonsterAiArenaTest`/`SoundEventTransportTest`，以及 desktop、D2GS 生产/测试、Netty、Map Viewer、Skill Viewer 编译通过。完整客户端回归 368 项通过、1 项可选原版 automap 存档样本跳过；架构 11 项、D2GS 20 项通过，合计 399 项通过、1 项跳过，失败/错误均为 0。表现扫描仍为 0；M1-B 生成层 0、运行时适配器 58 条无漂移。两份历史尸体夹具补齐当前复活守卫要求的城镇出生点与 zone，保留原有装备、HP、复活及成长/掉落断言。

真实资源：是；`-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F`，相关 headless 测试读取 MPQ 数据表/字符串/动画资源，没有启动实际 GL/音频设备或完整竞技场。

D2MOO 依据：沿用 1.10f Skills/Missiles 客户端回调及权威导弹复用、States/Overlay 视觉恢复、Shrines Code/ShrId、Clients.cpp 城镇玩家出生点。服务端结算、AI、任务、数值、协议和客户端装配相对顺序未改动。

剩余问题：包归属已迁移，事件/只读快照出口未完成。客户端实体工厂与 COF/动画资源仍有直接适配调用；技能仍探测本地/联网系统；神殿仍有种类补齐及 COF 写入；死亡处理器仍混合尸体/装备/HP/位置/Box2D 生命周期；状态调色板与 context/widget 仍有全局/host 兼容访问。具体 owner 和删除条件见详细记录，M1 整体尚未完成。

下一迁移：M1-A/M8-C 客户端实体与 COF/动画表现适配，集中处理装配、资源等待/释放和会话依赖；死亡权威职责与事件/快照出口按 M2/M3 依赖收敛。

详细记录：`docs/dark-magic-m8c-skill-death-state-2026-10-22.md`。

### M1-A/M8-C 客户端实体与 COF/动画归属记录（2026-10-22）

本批完成日期：2026-10-22；M1-A/M8-C 整体仍为进行中。

提交：`7b5dea102`（客户端实体、COF/动画适配器归属迁移及回归门禁）。13 个实体/COF/动画生产适配器和 8 个客户端组件迁入 `presentation`，相关 host、viewer、测试与门禁引用已统一；1.10f COF 回归测试同步迁入新包。

验证：`:core:compileTestJava`、完整 `:core:clientUiBoundaryRegression`、desktop、D2GS 生产/测试、Netty、Map Viewer、Skill Viewer 编译全部通过。客户端共 383 项，382 项通过、1 项可选 automap 样本跳过；架构 12 项、D2GS 定向 20 项通过，合计 414 项通过、1 项跳过，失败/错误均为 0。M1-A 扫描 0、M1-B 生成层 0、运行时适配器 58 且无漂移。

真实资源：是；使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，无 GL/音频设备启动。

剩余问题：包归属已迁移，实体/COF/动画仍有兼容性写入和 context 依赖，事件/只读快照出口尚未完成；死亡、技能、神殿和状态的兼容路径仍按 M2/M3/M8-C owner 跟踪。M1 整体尚未完成。

下一迁移：集中收敛客户端实体与表现适配器的事件/只读快照出口，不再按单个表现类拆批。

详细记录：`docs/dark-magic-m8c-entity-cof-animation-2026-10-22.md`。

### M1 原定三项交付统一验收（2026-10-22）

阶段：M1/P1；本批为原定第 3/3 项“旧违规登记”，本批完成度 100%。第一项依赖整理、第二项架构检查与第三项旧违规登记已合并核验，不再累计历史单类提交为新批次。前面的逐次记录保留为历史，本节是当前进度口径。

提交：`e82529343`（统一边界门禁、旧债登记、网络扫描修正和 harness 测试源隔离）；本节验收记录随同批次提交。

范围与结果：

- 依赖整理：沿用已有五类 owner、模块 DAG 和 M1-B host/population 端口；关闭 extracted module 仍可导入 `com.riiablo.presentation` 的门禁缺口。
- 架构检查：新增统一生产 source-root 扫描，补查 LibGDX 类型、`Gdx` 音频/图形设备以及 presentation 反向依赖；逐文件、逐类型、逐次数拒绝新增和增长，减少旧债时要求同步收缩预算。headless 代码不允许通过登记获得表现依赖豁免。扫描器是保守源码检查，Java 编译和现有模块/地图门禁继续作为语义依据。
- 旧违规登记：`tools/architecture/m1-presentation-debt.tsv` 共 15 个文件、76 条边、77 次引用，逐项有具体 owner、M2/M3/M6/M8 后续归属和可核验删除条件。旧 M1-A 扫描为 0 只覆盖 riiablo 原表现包，不能覆盖 LibGDX 与新 presentation 包；此前“直接设备/控件均清零”的描述不准确。
- 网络审计与隔离：两份 `d2gs_test` harness 保持内容和包名不变，移入 `server/netty/src/test/java`；生产源码条目 75 → 73，生产 harness 条目和外部生产引用均为 0。两个 PowerShell 脚本修复了表头假制表符、source-set 混扫、整棵旧实现目录跳过与错误计数，并委托统一 Python 扫描。剩余 7 条跨族引用全部指向 `nnet`，仍由 M8-D 收敛，不能把零 harness 引用视为生产入口唯一。

验证命令（两次 Gradle 执行均成功）：

```powershell
./gradlew.bat :core:architectureRegression :core:clientUiBoundaryRegression :core:mapBoundaryRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest :desktop:compileJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
./gradlew.bat :core:architectureRegression :server:netty:compileTestJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
powershell -NoProfile -ExecutionPolicy Bypass -File tools/architecture/check-network-entrypoints.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/architecture/check-network-implementation-inventory.ps1
```

结果：架构回归 12 项、客户端回归 383 项（382 通过、1 项可选 automap 样本跳过）、地图 95 项、D2GS 定向 20 项；抽出模块 game-data 2 项、server-domain 295 项、server-systems 2 项、quests 17 项。当前 XML 共 826 项，825 通过、1 跳过，失败/错误为 0；模块任务在最后补验中使用有效的 UP-TO-DATE 结果，统计不表示本批重跑全部仓库测试。另有扫描器拒绝用例 9 项通过。M1-A 原扫描 0；补充门禁 76 条边/77 次引用无漂移；M1-B 生成层 0、运行时适配器 58 条无漂移。Netty 主/测试源均编译通过，生产输出没有 `d2gs_test` class，测试输出包含两份 harness。

真实资源：是，使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F` MPQ/headless 回归；未运行真实 GL/audio 客户端或完整双客户端网络实机。D2MOO 依据：本批只改变依赖门禁、记录和测试源码归属，不修改 D2 规则、战斗、地图或协议；既有 1.10f 行为回归保持通过。

出口判断：M1 主行原定的三项交付已验收；M1-A 完整事件/只读快照目标仍未达到，M1-C 的单一生产消息/会话路径也未达到，两个子项原判定保留并继续记为进行中。它们分别依赖 M2/M3/M8-C 和 M3/M8-D，不作为新增 M1 微批次。M1-B、M8-B 已完成；M2/M4/M5/M6/M7 尚未正式启动，M3 和 M8-C 只有局部实现，M8-A/M8-D 未完成（M4-B 暂停）。

剩余 owner：表现旧债见逐边 TSV；地图 58 条运行时依赖仍由 `MapManager`（装配）、`Box2DPhysics`（物理）、`RenderSystem`（表现）承担，生成层不再反向依赖；网络族 owner/删除条件沿用 `docs/dark-magic-m1c-network-audit-2026-10-21.md`，本次仅消除 harness 生产 source-set 债务。

下一正式迁移：M2，按原计划集中处理玩家/单位权威状态、写入接口与预测/表现边界；物品/任务和佣兵/地图按 M2 原定后续批次处理。M8-C/M8-D 完整改造继续保留其依赖与出口，不在 M1 中提前重写。

### M2 第 1/4 批：玩家/单位移动权威与渲染视图（2026-10-22）

完成日期：2026-10-22；本批完成度 100%，M2 整体仍为进行中，已完成 1/4 批。实现与本节验收记录属于同一完整批次。

提交：`8880ebc8f`（不可变移动快照、复制式渲染变换、触摸移动意图和专项门禁）。

状态来源与写入边界：

- 本地/服务端移动状态继续由现有 `Pathfinder`、移动积分和物理同步系统负责；本批不改写移动/战斗公式。远程客户端在 `ClientNetworkReceiver` 接收 PLR/MON/MIS 变换时，先把服务器字段提交到独立的 `AuthoritativeTransformState`，再更新 ECS 副本或重放本地预测。查询仅返回不可变标量快照，不返回 FlatBuffers 表、接收缓冲或 ECS 向量。
- 快照字段清单：`tick`、`serverTimeMillis`；Position 的 `x/y`；Velocity 的 `velocityX/velocityY`；Angle 的 `angleX/angleY`；三个 `hasPosition/hasVelocity/hasAngle` 存在标记。部分更新保留未携带字段；带时间戳的乱序更新被拒绝，同 Tick 更新和旧版零 Tick 兼容保留，零 Tick 不清除已有单调水位。删除、跨 level 清理和新 baseline 开始时同步清除对应快照/插值历史。
- `AuthoritativeInterpolationSystem` 仅计算渲染副本，不再临时覆盖或恢复 ECS Position/Angle；本地校正只淡出视觉偏移。`ClientTransformView` 和 renderer 的复制接口供精灵/阴影、相机、光照、automap 标记、世界标签、任务指示器、天气与路径调试读取，动画方向只在绘制时临时应用并恢复。地图探索记录继续读取仿真位置，MonsterLabel 顶部固定标题没有世界坐标读取，无需改动。
- `GameScreen` 触摸入口只捕获方向；新增 `TouchpadMovementSystem` 在仿真 Tick 内调用现有 `Actioneer.moveTo`，松开时提交一次停止意图，遵守可打断守卫。删除 host 对 Velocity/Angle 的直接移动写入；本地及远程客户端配方均安装该入口，移动协议沿用现有链路。
- 插值系统不再依赖 `AnimationWrapper`，从 M1 登记中删除这条已消除的旧债。补充表现门禁 76 条边/77 次引用降至 75 条边/76 次引用（14 个文件），没有新增允许项；M1-A 原扫描仍为 0，M1-B 生成层仍为 0、运行时适配器 58 条无漂移。

验证命令：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava --no-daemon
./gradlew.bat :core:clientTransformRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest :desktop:compileJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
```

结果：M2 专项 70 项、客户端表现 383 项（382 通过、1 项可选原版 automap 存档样本跳过）、架构 12 项、D2GS 定向 20 项，共 485 次测试执行，484 通过、1 跳过，失败/错误为 0；按四个任务 XML 统计，包含门禁共享的用例，不表示全仓库测试全部重跑。扫描器拒绝用例另有 9 项通过。core 生产/测试、desktop、D2GS 生产/测试、Netty、Map Viewer、Skill Viewer 编译均通过。专项覆盖渲染期间 ECS 与原生精度不变、复制值不泄漏、预测重放不改已接收快照、缓冲复用、部分更新、时间倒退、删除/清空及触摸输入的 Tick/停止/可打断边界；既有原生移动、位置序列化、预测和 baseline 测试纳入同一门禁。

真实资源：是；明确使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，相关 headless 用例加载真实 MPQ 表/字符串/资源。未启动真实 GL/audio 客户端、物理触屏或双客户端网络实机。

D2MOO 依据：沿用已有 1.10f `UNITS_UpdateRunWalkAnimRateAndVelocity` 对应的 `NativePlayerMovement` 与 `D2PathStep::stepUnit` 原生精度移动链；本批只隔离已提交变换、预测和 GPU 读取。网络 PositionP 仍为现有浮点投影，接收时照旧使副本原生精度失效；本批保证渲染不会额外破坏本地原生精度，不宣称浮点协议可以恢复原生定点坐标。

剩余问题：玩家/单位死亡、复活、技能/神殿/状态适配器等生命周期写入仍未统一；物品/任务、佣兵/地图状态的权威来源与写入接口尚未迁移。表现副本仍兼容读取 ECS，本批没有完成全量事件/快照出口；M3 envelope/结构屏障及 M8 回放/网络三层收敛继续按原行跟踪。

下一迁移：M2 第 2/4 批，集中收口玩家/单位生命周期状态及写入接口；随后第 3/4 批物品/任务、第 4/4 批佣兵/地图。不得将类级修补、验收记录或补记 hash 另计批次。

### M2 第 2/4 批：玩家/单位生命周期状态及写入接口（2026-10-22）

完成日期：2026-10-22；本批完成度 100%，M2 整体仍为进行中，已完成 2/4 批。实现与本节验收记录属于同一完整批次。

提交：`ce96a7935`（生命周期快照、写入端口、复活意图和死亡/尸体权威边界）；本节验收记录随同批次提交。

状态来源与写入边界：

- 新增不可变 `AuthoritativeUnitLifecycleState`，按服务器 `tick/serverTimeMillis` 接受 PLR/MON 的 `CofReferenceP`、`VitalsP`、`StateP`；状态层与 FlatBuffers buffer 解耦，部分更新保留未携带字段，显式空 `StateP` 清空列表，旧 Tick 的死亡数据不能覆盖新复活状态。
- `ClientUnitSnapshotWriter` 是客户端 State/Vitals 的统一快照写入端口：Vitals 只写 aggregate，不改 base；StateP 保留 source/skill 层和周期字段，并标记 `snapshotOnly`。联网客户端的资源恢复、DOT、死亡结算、尸体回收和本地复活规则均不会因快照副作用运行。
- `ClientUnitLifecycleSystem` 将 ESC 转为复活意图，在仿真 Tick 内重新校验；联网复活只发送一个去重请求，结果包不直接应用坐标、HP/MP 或本地 `DeathHandler` 规则，只接受匹配请求并触发带 Tick 的 snapshot resync。
- `ClientNetworkReceiver` 在 EntitySync 中先提交生命周期快照，再投影 ECS/表现；删除、关闭、重连和 baseline begin 清理快照。`DeathHandler` 仅消费已提交快照创建表现尸体、恢复碰撞/动画适配器，不卸装联网副本装备，也不创建权威尸体。
- 服务端/本地 owner 继续负责生命周期、死亡、尸体回收和资源恢复规则；相关系统增加 `snapshotOnly` 守卫，联网尸体由服务端删除。修复本地/服务端复活后 `UnitLifecycle` 未恢复的问题，使下一次死亡可重新进入权威清理。M1 表现债务登记同步删除 `ClientNetworkReceiver`/`CursorMovementSystem` 对 `DeathHandler` 的两条旧边，当前剩余 73 条边、74 次引用，没有新增允许项。

验证命令：

```powershell
./gradlew.bat :core:clientLifecycleRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
./gradlew.bat :desktop:compileJava :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
git diff --check
```

结果：`clientLifecycleRegression` 111 项通过；其依赖门禁 `clientTransformRegression` 76 项通过、`clientUiBoundaryRegression` 385 项中 384 通过/1 项可选跳过、`architectureRegression` 12 项通过；D2GS 定向测试 20 项通过（D2GSSimulationAssembly 5、MonsterAiArena 14、SoundEventTransport 1）。合计 604 次测试执行，603 通过、1 跳过，失败/错误为 0；任务间包含共享用例，不表示全仓库测试全部重跑。架构边界 unittest 9 项通过，`migrationBoundaryCheck` 通过；M1-A 原扫描 0，M1-B 生成层 0/运行时适配器 58 条无漂移。core 生产/测试、desktop、D2GS 生产/测试、Netty、Map Viewer、Skill Viewer 编译均通过；`SoundP.java` 规范化为单个结尾换行，最终差异检查无 EOF 空行问题。

真实资源：是；明确使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，相关 headless 用例加载真实 MPQ 表/字符串/资源。未启动真实 GL/audio 客户端或双客户端网络实机。

D2MOO 依据：`F:\3rd_src\D2MOO\source\D2Game\src\PLAYER\PlrMsg.cpp::D2GAME_PACKETCALLBACK_Rcv0x41_Resurrect_6FC87480`。该函数表明服务端在复活准入后恢复最大生命、Mana、Stamina，设置 targetable，切换 Act/出生点并重新分配技能；本批只把这些结果作为带 Tick 的权威快照消费，不在客户端重算。

剩余问题：物品/任务事务、佣兵/地图状态尚未迁移；技能/神殿等表现适配器的全量事件出口仍按 M3/M8-C 跟踪。M3 command envelope、完整 phase barrier 和 M8 replay/network 三层收敛仍未完成。联网快照仍兼容写入现有 ECS 副本，完整事件出口和双客户端实机验收留到后续批次。

下一迁移：M2 第 3/4 批，集中处理物品/任务权威状态与事务写入接口；随后第 4/4 批佣兵/地图状态。不得将类级修补、验收记录或补记 hash 另计批次。

### M2 第 3/4 批：物品/任务权威状态与事务写入接口（2026-10-22）

完成日期：2026-10-22；本批完成度 100%，M2 整体仍为进行中，已完成 3/4 批。本批将库存/地面物品、钱包/弹药、任务记录和客户端意图统一收口；实现、回归和本节记录属于同一完整批次。

提交：`6611ca2c4`（物品/任务权威快照、Tick 意图入口、D2GS 完整库存发布和拾取事件收口）。

状态来源与写入边界：

- 新增 `AuthoritativeInventoryState`、`AuthoritativeGroundItemState`、`AuthoritativeQuestState` 和 `AuthoritativePlayerItemState`。接收端先复制 FlatBuffers 字节、校验完整库存/地面物品/40 条任务记录和时间顺序，再由 `Client*SnapshotWriter` 一次性投影到兼容的 `CharData`/ECS 副本；坏包、重复槽位、重复 ID、非法枚举、非有限坐标和佣兵越权条目不会部分提交或推进 revision。任务 revision 保持 D2 现有 FNV 内容哈希语义，允许哈希下降，不被误当作递增计数器。
- `ClientItemIntentSystem`、`ClientQuestIntentSystem` 和 `ClientVendorSystem` 把背包、拾取、装备、药剂、武器切换、NPC 对话、日志确认、购买/出售/修理等 UI 调用排入仿真 Tick；索引操作在 Tick 重新按稳定 item id 解析，地面操作保存 `NativePathIdentity.Token`，死亡、会话变化、实体复用和联网副本都会拒绝本地结算。现有协议没有武器切换和联网 imbue 操作，远端明确拒绝本地规则并保留到 M6/M8。
- `ClientNetworkReceiver` 删除旧的 11 条 inventory echo replay 路径，仅接受完整 `ItemMoveResult`；baseline BEGIN 清理权威 stores，END 同时要求实体计数、库存 revision 和任务快照完整后才推进完成水位。未带 Tick 的 Quest/Gold/NPC 结果只做反馈或触发 resync，不能覆盖带 Tick 的钱包、弹药和任务记录。
- D2GS 在登录/resync、拾取/物品事务、任务/NPC 成功、交易完成和金币成功后发布完整库存并同步带时间戳的 `PlayerP`；拾取只有在本地 Tick 或服务端权威结算成功、且地面实体确实被消费后才派发 `QuestItemPickedUpEvent`。地面 ItemP 更新保持服务器实体 ID、拾取归属窗口和 party gold 元数据，元数据更新不丢失已提交物品字节。
- `VendorPanel`、`InventoryPanel`、`GameScreen`、任务对话和日志面板只捕获意图/消费反馈；网络结果不直接写钱包、所有权、耐久或奖励。D2MOO 对照保留 `ITEMS_IsInPlayersInventory`、`PLRTRADE_AddGold`、`QUESTS_NPCMessage`、`QUESTS_SendCurrentFlags`、`QUESTS_UpdatePlayerFlags` 和 `ACT1Q1_Callback11_ScrollMessage` 的既有规则依据；本批不把 riiablo 既有 bit63 兼容标记宣称为 D2MOO 新规则。

验证命令与结果：

```powershell
./gradlew.bat :core:clientInventoryQuestRegression :desktop:compileJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
./gradlew.bat :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest --tests com.riiablo.server.d2gs.InventorySnapshotTransportTest --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
git diff --check
```

结果：`clientInventoryQuestRegression` 210 项通过；其依赖的 `clientLifecycleRegression` 111 项、`clientTransformRegression` 81 项、`clientUiBoundaryRegression` 403 项中 402 通过/1 项可选跳过、`architectureRegression` 12 项通过；D2GS 定向测试 22 项通过（含新增完整库存传输 2 项）。架构边界 unittest 9 项通过，M1-A 0、M1-B 生成层 0/运行时适配器 58 无漂移，表现债务 73 edges/74 references 无新增。core、D2GS、desktop、Netty、Map Viewer、Skill Viewer 编译均通过。`SoundP.java` 结尾已规范为单个换行。

真实资源：是；客户端/headless 回归使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F` 的 MPQ 数据、字符串和相关资源。没有启动真实 GL/audio 设备或双客户端实机验收。

剩余问题：库存/任务仍投影到现有可变 `CharData`/ECS 兼容副本；完整领域事务回滚、所有外部消耗/奖励统一事件出口属于 M6。协议尚未提供联网 imbue/weapon-set 操作；M3 command envelope、完整 phase barrier，M8 replay 与 network 三层收敛仍未完成。

下一迁移：M3，集中处理 Tick、命令与事件边界；不得把类级修补、验收记录或补记 hash 另计批次。

### M2 第 4/4 批：佣兵/地图权威状态与写入接口（2026-10-23）

完成日期：2026-10-23；本批完成度 100%，M2 四个计划批次全部完成。本批把佣兵实体快照和 EntitySync 的 level context 从 FlatBuffer/ECS 直写路径收口到不可变权威状态，再通过兼容 writer 投影到现有运行时对象；没有新增协议字段，也没有修改 D2 公式。

实现与边界：

- 新增 `AuthoritativeMercenaryState` 和 `AuthoritativeMapState`。佣兵快照复制 owner、merc type、Hireling.txt id、等级、seed、name、经验和 flags；地图快照复制实体 level、tick 和 server time。两者均按实体保存，并拒绝 tick 或 server time 回退，避免旧包覆盖当前状态。
- `ClientMercenarySnapshotWriter` 负责将已提交快照写入 `Mercenary` ECS 兼容副本；seed 更换时重置原生 target node、技能和 AI 初始化字段。owner 尚未映射时仍保留快照和 pending owner，映射出现后只绑定一次。实体删除、level prune、baseline BEGIN 和 receiver dispose 都清理对应快照。
- `ClientMapSnapshotWriter` 负责把 level context 解析到 `MapWrapper`。Map/Zone 仍是可变运行时对象，不作为权威来源；重复 level 投影不会重置已存在的 room/pinned context。roomId 没有加入协议，地图生成和 RoomEx 归属继续由现有 MapManager/MapWrapper owner 管理。
- `NpcServiceResult` 中无 Tick 的 `CharData.MercData` header 继续作为表现/存档兼容路径，不能称作完整实体权威事务；统一 header 事务留给 M6/M8。M3 command envelope、完整结构屏障以及 M8 网络三层/回放均未提前实现。

提交：`b28a4ae89`（佣兵/地图权威快照、写入接口、回归门禁和 SoundP.java 结尾清理）。

验证命令与结果：

```powershell
./gradlew.bat :core:test --tests com.riiablo.engine.client.AuthoritativeMercenaryStateTest --tests com.riiablo.engine.client.AuthoritativeMapStateTest --tests com.riiablo.engine.client.ClientMercenarySnapshotTest --no-daemon
./gradlew.bat :core:compileJava --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
git diff --check
```

结果：佣兵/地图定向测试 7 项通过；core 编译通过。完整 `clientMercenaryMapRegression` 的依赖门禁复跑时，既有 `BlessedHammerIntegrationTest.nativeHammerDoesNotInventADemonBonusAbsentFromTheTable` 失败（期望 0、实际 50），与本批改动无关；本批定向测试和编译未受影响。真实资源路径为 `G:\BaiduNetdiskDownload\Diablo II 1.10F`；未启动真实 GL/audio 客户端或双客户端实机验收。D2MOO 依据继续采用 `ITEMS_IsInPlayersInventory`、`PLRTRADE_AddGold`、`QUESTS_NPCMessage`、`QUESTS_SendCurrentFlags`、`QUESTS_UpdatePlayerFlags` 和 `ACT1Q1_Callback11_ScrollMessage` 的既有规则对照，本批不声称完整 D2 等价。

剩余问题：佣兵 header、CharData/ECS 兼容投影、MapManager/RoomEx 所有权仍未统一为 M6 领域事务；M3 command envelope/结构屏障、M8 网络三层和回放仍未完成。下一正式迁移为 M3，按原计划进入 Tick、命令与事件边界；不得把后续修补拆成新的 M2 微批次。

### M3 第 1/4 批：固定 Tick、命令 envelope 与阶段屏障（2026-10-23）

完成日期：2026-10-23；本批完成度 100%，M3 计划四个批次中的第 1 批。本批建立固定 25Hz 仿真的统一准入边界，不改变既有 D2 规则、网络协议或系统内部结算顺序。

实现与边界：

- 新增不可变 `SimulationCommandEnvelope`，统一记录目标 Tick、提交序号、来源、命令类型和 payload；`SimulationCommandQueue` 按 Tick/sequence 稳定排序，并拒绝已经排空 Tick 的迟到命令。
- 新增 `SimulationEventQueue`，把仿真事件按 Tick/sequence 排序后再交给表现/兼容事件系统；新增 `StructuralChangeQueue`，阶段末以稳定批次冲刷实体/组件结构变更，冲刷期间产生的新变更留到下一 Tick。
- 新增 `SimulationTickCoordinator`，明确 `COMMANDS → SIMULATION → EVENTS → STRUCTURAL_COMMIT` 阶段，并接入 `GameScreen` 的既有 `FixedStepAccumulator`。暂停时清空未提交命令、事件和结构变更，避免恢复后跨会话执行旧输入。
- 现有 Artemis 系统注册顺序和 `SimulationClock` 25Hz 规则保持不变；本批没有把 D2GS 改造成新 envelope，也没有提前实现完整 M8 replay/network 三层。

提交：`880758c69`（固定 Tick coordinator、command/event 队列、结构变更屏障和 GameScreen 接入）。

验证命令与结果：

```powershell
./gradlew.bat :core:m3TickCommandRegression :core:compileJava --no-daemon
git diff --check
```

结果：M3 定向门禁 11 项通过（FixedStepAccumulator 6、SimulationClock 2、SimulationTickCoordinator 3）；模块边界检查通过；core 编译通过。真实资源路径未用于纯调度门禁；未启动真实客户端。后续 M3 批次仍需把更多输入/网络意图适配到 envelope、统一事件出口并扩大结构屏障覆盖面。

剩余问题：当前旧 UI/网络入口仍有兼容队列，完整命令准入、事件生命周期、所有结构变更迁移以及 M8 回放/网络三层仍未完成。下一迁移为 M3 第 2/4 批，集中处理输入意图和网络结果的 Tick 准入；不得把类级修补、验收记录或补记 hash 另计批次。

### M3 第 2/4 批：客户端意图 Tick 准入适配（2026-10-23）

完成日期：2026-10-23；本批完成度 100%，M3 计划四个批次中的第 2 批。既有物品、任务和商店 UI 意图在存在固定 Tick coordinator 时提交 `SimulationCommandEnvelope`，由 `COMMANDS` 阶段执行；离线测试 harness 保留兼容队列路径。没有增加 D2GS 字段或改变本地/服务端规则。

实现与边界：

- `ClientItemIntentSystem`、`ClientQuestIntentSystem`、`ClientVendorSystem` 增加 coordinator 适配。意图继续捕获稳定 item/entity identity，并在 Tick 执行时重新验证角色、死亡、实体复用和网络副本条件。
- `GameScreen` 将 coordinator 的命令处理器绑定到 envelope payload，保证 UI 意图先于既有 ECS 系统处理；旧 `processSystem` 队列只在没有 coordinator 的测试/兼容装配中运行。
- 网络 cast、技能点、属性点和 skill selection 的 FlatBuffer 发送格式保持不变；本批只统一客户端 Tick 准入，不提前实现 M8 网络三层或 M3 完整 envelope 传输。

提交：`30635cb1c`（客户端 item/quest/vendor 意图接入 Tick command envelope）。

验证命令与结果：

```powershell
./gradlew.bat :core:m3TickCommandRegression --no-daemon
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientItemIntentSystemTest --tests com.riiablo.engine.client.ClientQuestIntentSystemTest --tests com.riiablo.engine.client.ClientVendorSystemTest --tests com.riiablo.engine.SimulationTickCoordinatorTest --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
git diff --check
```

结果：M3 门禁 14 项通过（固定步进/时钟 8、coordinator 3、item/quest/vendor 意图 3）；模块边界 unittest 9 项通过；core 编译通过。真实资源未用于纯意图准入门禁，未启动真实客户端。剩余完整命令准入、网络结果事件统一出口、结构变更全量迁移和 M8 replay/network 三层仍留后续批次。

剩余问题：部分移动/技能输入仍由旧入口直接捕获，网络反馈仍走现有 D2GS 结果路由；下一迁移为 M3 第 3/4 批，集中处理移动/技能命令和网络结果事件的统一 Tick 出口；不得把后续修补拆成新的微批次。

### M3 第 3/4 批：移动/技能命令与生命周期意图 Tick 准入（2026-10-23）

完成日期：2026-10-23；本批完成度 100%，M3 计划四个批次中的第 3 批。键盘、触控板、鼠标移动目标、技能施放和复活请求在固定 Tick coordinator 存在时统一提交下一 Tick 的 `SimulationCommandEnvelope`，由 `COMMANDS` 阶段执行；无 coordinator 的离线测试/兼容装配继续保留原路径。

实现与边界：

- `KeyboardMovementSystem`、`TouchpadMovementSystem` 和 `CursorMovementSystem` 捕获稳定的目标副本后提交移动命令，执行阶段仍调用既有 `Actioneer`，没有搬动移动公式、寻路或结算规则。
- `CursorMovementSystem` 的技能 cast 命令在执行阶段重新解析网络目标、观测 Tick 和目标 Tick；`ClientUnitLifecycleSystem` 的复活请求也延迟到命令阶段恢复请求玩家，再走原有生命周期准入。
- `GameScreen` 继续使用既有 `COMMANDS → SIMULATION → EVENTS → STRUCTURAL_COMMIT` 顺序；没有增加 D2GS 字段，没有改变 D2 公式或 Actioneer 结算，也没有提前实现 M6 完整事务和 M8 replay/network 三层。

提交：`029b5005f`（移动、技能和生命周期意图 Tick 准入及 M3 回归门禁扩展）。

验证命令与结果：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava --no-daemon
./gradlew.bat :core:m3TickCommandRegression --no-daemon
./gradlew.bat :core:test --tests com.riiablo.engine.client.CursorMovementSystemTest --tests com.riiablo.engine.client.TouchpadMovementSystemTest --tests com.riiablo.engine.client.ClientUnitLifecycleSystemTest --tests com.riiablo.engine.client.ClientPlayerRespawnResultTest --tests com.riiablo.engine.client.ClientItemIntentSystemTest --tests com.riiablo.engine.client.ClientQuestIntentSystemTest --tests com.riiablo.engine.client.ClientVendorSystemTest --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
git diff --check
```

结果：M3 Tick command regression 门禁通过（固定步进/时钟、coordinator、移动、生命周期和 item/quest/vendor 意图共 46 个测试方法）；移动/生命周期及既有意图定向测试通过；模块边界 unittest 9 项通过；core 生产与测试编译通过。生成的 `SoundP.java` 末尾空行已规范化。真实资源路径未用于纯调度门禁，未启动真实 GL/audio 客户端或双客户端实机验收。

剩余问题：网络反馈仍走现有 D2GS 结果路由，事件出口和结构变更仍有兼容路径；M3 第 4 批需继续收口网络结果事件与结构提交边界，M6 完整事务及 M8 replay/network 三层仍未完成。不得把后续修补拆成新的微批次。

### M3 第 4/4 批：网络结果事件与结构提交边界（2026-10-23）

完成日期：2026-10-23；本批完成度 100%，M3 计划四个批次全部完成。网络接收器在固定 Tick 中处理 D2GS 结果时，表现事件和 ECS 结构删除分别经过事件队列与结构提交屏障，保持现有权威状态更新顺序和协议格式。

实现与边界：

- `ClientNetworkReceiver` 的 `MissileImpactP`、`SoundP` 结果在 coordinator 存在时发布为下一 Tick 的 `SimulationEventQueue` 事件，由 `GameScreen` 的 `EVENTS` 阶段派发到既有 `EventSystem`；无 coordinator 的测试/兼容装配继续直接派发。
- 断线、EntitySync 删除、切图清理和地面物品权威移除统一经过 `StructuralChangeQueue`，在 `STRUCTURAL_COMMIT` 阶段执行 `world.delete`；权威快照、删除水位和跨地图过滤逻辑保持不变。
- 没有增加 D2GS 字段，没有修改 D2 公式或网络结果含义；事件队列只负责阶段准入，M6 完整事务和 M8 replay/network 三层仍未提前实现。

提交：`3b7c47db2`（网络结果事件与结构提交 Tick 边界及回归门禁）。

验证命令与结果：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava --no-daemon
./gradlew.bat :core:m3TickCommandRegression --no-daemon
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientInventoryQuestReceiverTest --tests com.riiablo.engine.client.ClientLifecycleReceiverTest --tests com.riiablo.engine.client.ClientPlayerRespawnResultTest --tests com.riiablo.engine.client.ClientMercenarySnapshotTest --tests com.riiablo.engine.client.ClientObjectSnapshotStateTest --no-daemon
python -m unittest discover -s tools/architecture -p test_migration_boundaries.py
git diff --check
```

结果：M3 Tick command regression 门禁 48 项通过，包含网络事件延迟派发和结构提交屏障边界测试；网络接收器定向回归通过；模块边界 unittest 9 项通过；core 生产与测试编译通过。真实资源路径未用于纯调度门禁，未启动真实 GL/audio 客户端或双客户端实机验收。

剩余问题：M3 基础阶段边界已完成，但网络反馈仍保留现有 D2GS 结果路由，完整事件溯源、可重放网络层和领域事务属于 M6/M8；下一正式迁移按计划进入后续 M4，不得把 M3 收口补丁拆成新的微批次。

### M3 独立补强：命令身份准入与 phase contract（2026-10-23）

完成日期：2026-10-23；本批补齐 M3 基础四批之外、无需等待 M6/M8 的独立边界能力。M3 仍不宣称整体完成：全量网络结果唯一入口、所有 ECS 结构写入、回放/网络三层和表现快照债务继续按依赖项保留。

实现与边界：

- `SimulationCommandEnvelope` 增加 actor、request、target 和 authority 元数据；`SimulationCommandAdmission.network(...)` 要求网络命令具备身份、正 request id，并通过调用方提供的目标授权检查。
- `SimulationCommandQueue` 对 `(source, actor, request)` 做会话内幂等去重，并保留可诊断的拒绝原因；默认本地 UI 仍使用兼容的 local admission，不强迫现有离线输入伪造网络身份。
- 新增独立 `SimulationPhaseContract`，记录 system 所属 phase、读集、写集和结构写集；同一 system 不得重复注册，结构资源只能在 `STRUCTURAL_COMMIT` 写入。`SimulationTickCoordinator` 暴露该契约供 headless 组装和检查使用。

提交：`a589f61f0`（命令身份准入、request 幂等和 phase contract）。

验证：

```powershell
./gradlew.bat :core:test --tests com.riiablo.engine.SimulationTickCoordinatorTest --no-daemon
./gradlew.bat :core:compileJava --no-daemon
git diff --check
```

结果：命令身份、目标授权、重复 request、phase 读写集和结构写入边界定向测试通过；未启动真实客户端，也未修改 D2GS 协议。M3 尚余完整结构迁移、事件溯源和 M8 网络/回放依赖。

下一迁移：M4；M3 的剩余项随 M6/M8 依赖完成后继续收口。

### M4 第 1/4 批：命名确定性 RNG stream（2026-10-23）

完成日期：2026-10-23；本批完成度 100%，M4 计划四个批次中的第 1 批。新增显式命名 RNG stream 工厂，为后续数据/规则迁移提供可复现的随机来源边界；现有 D2Common 两词 `pSeed` 和兼容 RNG 调用保持不变。

实现与边界：

- `NamedRngStreams` 按 `gameSeed + streamName + contextId` 派生稳定种子，返回独立 `NativeRng` 实例；名称和上下文都进入种子，某个 stream 增加消费不会改变其他 stream 的序列。
- 工厂拒绝空名称，并提供 `seedFor` 诊断入口，便于后续 replay/检查点记录 stream identity；没有引入全局 RNG、线程共享状态或隐式常量回退。
- 本批未改 `NativeUnitRandom`、D2Common 两词种子、战斗/掉落/AI 公式，也未将现有调用方强行切换到新 stream；调用方迁移和消费计数属于后续 M4 批次。

提交：`45052ed5c`（命名确定性 RNG stream 工厂与回归门禁）。

验证命令与结果：

```powershell
./gradlew.bat :core:m4NamedRngRegression --no-daemon
git diff --check
```

结果：M4 命名 RNG 门禁 3 项通过，覆盖同身份序列重放、名称/上下文隔离和参数拒绝；模块边界检查通过。真实资源路径未用于纯数据流门禁，未启动真实客户端。

剩余问题：现有规则调用方仍有兼容 `NativeRng` 和 D2Common `pSeed` 入口，尚未为 AI、战斗、掉落、地图和表现建立完整消费审计；数据版本、MPQ/TXT 来源和缺失数据策略留在后续 M4 批次。不得把后续适配拆成新的微批次。

### M4 第 2/4 批：TXT/MPQ 数据来源与版本身份（2026-10-23）

完成日期：2026-10-23；本批完成度 100%，M4 计划四个批次中的第 2 批。五类原生表 projection 现在保留不可变的表来源、版本标记和内容摘要，规则层可审计实际加载的数据身份。

实现与边界：

- 新增 `TxtTableSource`，以路径、`DIABLO_II_110F`/未声明版本和 `TxtTableManifest` 保存 TXT/MPQ 来源；manifest 同时记录原始 SHA-256、表头摘要、语义摘要、行数和列数。
- `NativeSkills`、`NativeMissiles`、`NativeMonStats`、`NativeItemStatCost` 和 `States` 的 MPQ 加载路径统一标记 1.10f 来源；内存 fixture 继续标记未声明版本，便于测试不得伪装为真实资源。
- 表值、字段转换、缺失列和 projection schema 行为保持不变；没有修改 D2 公式、协议或资源内容，也没有把解析器调用搬入规则层。

提交：`6c47323ba`（TXT/MPQ 来源身份与 projection manifest）。

验证命令与结果：

```powershell
./gradlew.bat :core:m4DataSourceRegression :core:m4CoreDataSourceRegression --no-daemon
$env:D2_110F_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
./gradlew.bat :core:test --tests com.riiablo.codec.excel.Native110FTxtTablesIntegrationTest --no-daemon
git diff --check
```

结果：M4 数据来源门禁通过；game-data ownership/manifest 回归与 core projection/schema/真实 1.10f MPQ 表摘要门禁分开通过。资源路径为 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`，未启动真实 GL/audio 客户端。

剩余问题：数据版本目前以加载入口声明为准，尚未建立跨表 data-pack digest、缺失数据拒绝策略和完整固定点字段审计；这些留在后续 M4 批次。不得把后续适配拆成新的微批次。

### M4 第 3/4 批：data-pack 身份、严格缺失策略与固定点门禁（2026-10-24）

完成日期：2026-10-24；本批补齐 M4 数据身份和固定点验收中不依赖 M5/M6 的部分。

实现与边界：

- 新增 `TxtDataPackManifest`，按逻辑表名、声明版本、行列计数和 raw/header/semantic digest 生成与路径无关的跨表 SHA-256 身份；表插入顺序变化不会改变身份。
- 新增 `MissingTxtDataException` 和 `NativeTxtProjectionReport.createAllStrict(...)`。严格规则装配在缺表、版本不匹配或 schema issue 时失败，不再把缺失数据静默降级为空 projection；旧兼容 `parse` 路径保持可用。
- `NativeRng` 增加 draw count、stream name 和 context 诊断字段；`NamedRngStreams.openLegacy(...)` 为旧 seed 提供不改变序列的命名迁移入口，掉落 fallback/treasure-class 已接入该入口。
- 新增独立 `m4CoreDataSourceRegression` 与 `m4FixedPointRegression`，覆盖 core 表 projection、真实 1.10f manifest、8.8/24.8 伤害/状态/位置/技能转换门禁；game-data ownership 门禁保持独立 classpath，避免测试隔离被污染。

提交：`d4ba1c400`（data-pack 身份、严格缺失策略、RNG 消费诊断与固定点门禁）。

验证：

```powershell
./gradlew.bat :server-domain:test --tests com.riiablo.engine.server.NamedRngStreamsTest --no-daemon
./gradlew.bat :game-data:test --tests com.riiablo.codec.excel.TxtDataPackManifestTest --no-daemon
./gradlew.bat :core:m4DataSourceRegression :core:m4CoreDataSourceRegression :core:m4FixedPointRegression --no-daemon
$env:D2_110F_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
./gradlew.bat :core:m4CoreDataSourceRegression --no-daemon
git diff --check
```

结果：data-pack/严格缺失策略、RNG 消费诊断和固定点门禁通过；真实 1.10f core 数据门禁 14 项全部通过。尚未把所有 server AI/技能/地图/表现的 `MathUtils.random` 和兼容 `NativeRng` 调用迁移到命名 stream；这些仍是 M4-A 的剩余迁移债务，不能据此宣称 M4 总体完成。

下一迁移：继续 M4-A 调用方审计与命名 stream 迁移；M4-B 内容包按计划暂停。

### M6 第 1/4 批：库存领域事务边界（2026-10-24）

本批与 M5 并行启动。M4 已提供不可变数据来源、严格缺失策略和固定点门禁，
但 M4-A 的全部规则调用方迁移仍在继续；因此本批只建立不依赖具体战斗结算的物品事务边界，
不修改 M5 的技能、命中、伤害或导弹入口。

范围：

- 将拾取、装备、存储、药剂和交易所需的物品变更表示为可验证的领域操作；操作提交前只读取
  稳定物品 ID、当前位置、尺寸、所有权和钱包快照。
- 预检失败时不得修改 `ItemData`、金币、装备属性或任务记录；提交阶段保留现有
  `ItemData`/D2GS 规则作为唯一实际变更入口，避免并行期间出现第二套结算逻辑。
- 为事务预检记录来源 Tick、请求序号和操作摘要，供后续 M3 事件屏障、M5 战斗快照和 M8
  回放使用；本批不新增 FlatBuffer 字段。
- 现有 `ItemDataTradeAuthority`、`AuthoritativeItemMoveService` 和客户端意图入口只作为
  适配层接入，生产调用切换和完整回滚留在本批后半段验收，不与 M5 同时改写。

出口：

- 纯数据测试覆盖重复物品 ID、非法位置/尺寸、所有权失效、容量不足、金币不足和重复提交；
- ECS/D2GS 测试证明预检失败不改变库存和钱包，成功提交只产生一次变更；
- 与 M5 的并行边界固定为：M5 负责战斗结算及其快照，M6 负责物品/钱包事务及属性聚合输入，
  双方通过稳定 item ID 和已提交 Tick 交接，不直接互相调用内部实现。

当前状态：已启动，尚未宣称 M6 完成。下一步为实现事务预检模型并接入现有交易/物品移动测试；
M4-A 调用方审计、M5 战斗垂直切片和本批 M6 事务可以并行推进。

### M6 第 2/4 批：经验与升级领域服务（2026-10-24）

本批与 M5 战斗垂直切片并行推进。新增纯规则 `ProgressionService`，以现有原生
`Experience.txt` 投影作为阈值来源，计算经验奖励、等级跨越、等级上限和饱和结果；服务只返回
不可变 `Result`，不直接写入 `CharData`、ECS、战斗实体或网络快照。

已覆盖：负经验奖励归零、非法等级归一化、跨多个等级的确定性升级、最大等级边界和 long 溢出保护。
M5 后续只需消费 `Result` 中的已应用经验和新等级，不需要调用 M6 内部实现。当前尚未把结果
接入现有 `ExperienceManager` 生产写入路径，避免在 M5 仍调整战斗奖励时形成双重结算入口。

验证：`ProgressionServiceTest` 已加入，覆盖上述纯规则边界。定向 Gradle 测试在核心编译阶段被
工作区中既有的构建/生成文件改动阻塞，未能完成测试执行；该阻塞与本批新增类无关，待外部构建改动
收口后复跑。

下一步：接入 `ExperienceManager` 的单一提交边界，增加升级结果与玩家/佣兵快照的幂等测试；继续
保持 M5 战斗结算和 M6 经验提交之间只通过已提交 Tick、实体 ID 和不可变结果交接。

### M6 第 3/4 批：装备属性只读投影（2026-10-24）

本批与 M5 并行推进。新增 `ItemStatProjection`，将一个或多个物品的 aggregate stat
转换为按 stat ID 排序的不可变投影；投影支持饱和加法、缺省值查询和稳定索引访问，供后续战斗、
角色面板和快照层消费。它不持有 `Attributes`、`Item`、ECS 或 UI 引用，也不会反向写入装备或
战斗实体。

本批验证了无序 stat 输入的稳定排序、缺省 stat、空/空白来源以及只读投影边界。真实装备聚合
接入仍需等 M6 后续批次统一处理套装计数、装备位置和 `ItemData.updateStats()` 的单一提交入口；
M5 只接收投影结果，不读取 M6 内部的可变属性列表。

验证：`./gradlew.bat :core:test --tests com.riiablo.attributes.ItemStatProjectionTest --no-daemon`
通过。由于当前工作区仍有外部 D2GS 构建改动，本批只运行定向核心测试，没有扩大到全量测试。

当前状态：M6 第 3/4 批的纯投影部分完成，M6 整体仍进行中。下一步是将投影与装备事务的
提交 revision、套装激活计数和玩家/佣兵快照连接起来，同时保持与 M5 的战斗读取边界稳定。

### M6 第 4 批：经验单一提交边界（2026-10-24）

本批将 `ExperienceManager` 的经验写入收敛到 `ProgressionService.Result` →
`commitExperience` 单一提交边界。经验计算仍由纯规则服务完成，CharData 的 base/aggregate
统计、等级变更和资源不变量只在提交边界写入，避免调用方重复计算或重复结算。现有击杀奖励
调用保持兼容，但不再直接维护经验/等级公式。

定向 `ExperienceManagerNativeTest`、`ProgressionServiceTest` 通过。玩家经验 revision、装备
revision 和佣兵生命周期快照在后续收口批统一接入；本段记录的是单一提交边界的历史状态。

### M6 最终收口批：事务、成长、装备与重连 revision（2026-10-24）

本批完成 M6 在当前代码范围内可独立完成的剩余工作，并把玩家/佣兵的 progression 与
equipment revision 接到服务端快照、客户端接收门禁和 ECS 表现副本。库存事务继续由
`InventoryTransaction`/`AuthoritativeItemMoveService` 作为唯一预检与提交边界；经验升级由
`ProgressionService.Result` → `ExperienceManager.commitExperience` 提交，提交成功后只推进一次
玩家 progression revision；佣兵经验、升级、恢复和装备聚合使用同一生命周期 owner，避免重复推进。

本批还修正了角色池化时 `ItemData.equipmentRevision` 的清零，并补齐 FlatBuffers 生成接口、
玩家/佣兵序列化字段、客户端 revision gate 及 snapshot writer。`core:m6FinalRegression` 覆盖
库存事务、属性投影、经验/升级、玩家/佣兵快照、物品移动/鉴定/金币和客户端 tick 边界；
最终回归共 85 项通过，`git diff --check` 通过，未修改 D2 数值公式或 M8 的 replay/save 协议。

出口判断：M6 的库存、装备聚合、经验升级、佣兵成长和重连快照在本仓库范围内已完成，
可满足“无重复提交或双重结算”的验收要求。完整 D2S 存档往返、可恢复 replay、网络三层
统一出口及最终真实双客户端验收仍属于 M8，不作为 M6 未完成项回填。

### M7 第 1 批：世界生成策略边界与 Act I 原生地图门禁（2026-10-24）

本批开始 M7 的世界规则收敛。当前 Act I 的 D2MOO DRLG、道路拓扑、碰撞层、Warp 和
人口装配已经存在，但 `Map` 的生产入口仍在多个阶段直接读取兼容 cvar，容易让 legacy
builder 与原生 post-generation 处理产生分叉。新增 `MapGenerationPolicy` 作为单一 session
策略：默认使用 native D2MOO，只有显式关闭旧 cvar 时才进入 legacy compatibility；后续
世界规则不再自行判断 builder 或读取 cvar。

新增 `:core:m7WorldRegression`，覆盖策略默认值、地图生成边界、Act I D2MOO layout/道路/楼层
导出、碰撞层、Waypoint、RoomEx 和原生怪物人口装配，并继续依赖模块边界与 M1-B map/entity
门禁。该批不修改 D2 数值公式、AI 行为公式或协议，不把现有 native bridge 误报为完整 M7。

验证：`./gradlew.bat :core:m7WorldRegression --no-daemon` 已通过，共 74 项；同时通过
`verifyModuleBoundaries`。M7 后续批继续处理 native path/collision 的全量策略出口、
怪物 AI policy、Quest/NPC handlers 和 Act I 双客户端/真实 MPQ 验收；M7-B 的 map/map2/map5
代际删除条件仍未达到。

### M7 第 2 批：原生道路/碰撞所有权边界（2026-10-24）

本批收口 Act I 原生 D2MOO outdoor export 与旧兼容直线道路之间的策略边界。此前
`generatePathsOnTileGrid` 只有在 Blood Moor、Cold Plains、Stony Field 三个关卡都检测到
原生 dirt path 时才跳过兼容路径；当 native export 只覆盖部分 Act I 关卡，旧路径仍可能写入
已由 D2MOO RoomEx floor/collision 接管的 TileGrid。现在 `MapGenerationPolicy` 提供统一的
`usesCompatibilityPath` 判断，builder 按当前 map 的已接受 native outdoor export 判定所有权：
native export 一旦存在就禁止兼容路径覆盖；没有 native export 时仍允许 fallback，显式 legacy
模式仍可用于历史兼容和对照测试。没有检测到原生 dirt-path floor flag 时只记录诊断，不回退覆盖
原生地图。

验证：`./gradlew.bat :core:m7WorldRegression --no-daemon` 通过，共 77 项；`verifyModuleBoundaries`
和 `git diff --check` 通过。该批未修改 D2MOO 道路生成公式、碰撞标志或协议；仍需后续批次完成
怪物 AI policy、Quest/NPC handlers 以及 Act I 双客户端/真实 MPQ 验收。

### M7 第 3 批：MonStats AI 策略入口收口（2026-10-24）

本批将怪物 AI token 的世界规则解析从通用 `AI` 机制中分离。新增
`MonsterAiPolicy`，集中维护 `MonStats.AI` 到 Java 实现名的 D2 特殊别名（包括
`7TIllusion` 和 `Trap-*`），并统一处理 Idle 单例、专用 AI 构造和缺失实现的
`GenericMonster` 权威 fallback。原有 AI 行为、随机流、动作时序和 fallback 语义不变；
`AI.findAI` 仅保留兼容入口，实际解析由 policy 负责，避免新规则继续散落在反射机制中。

`m7WorldRegression` 扩展纳入 `MonsterAiPolicyTest`、`NativeAiDecisionTest` 和
`Act1NativeAiIntegrationTest`，覆盖 token 别名、专用 AI、Idle/fallback、D2 参数及
Act I 原生 AI 行为。验证共 118 项通过，失败/错误/跳过均为 0；同时通过
`verifyModuleBoundaries` 与 `git diff --check`。本批未修改 D2 AI 公式或技能数值，
Quest/NPC handlers、真实双客户端和 MPQ 行为验收仍待后续批次。

### M7 第 4 批：Quest/NPC canonical identity 与消息边界（2026-10-24）

本批新增统一 `QuestNpcPolicy`，将 `MonStats` NPC 行解析为任务系统使用的 canonical
NPC 类型。Act I--IV 的命令准入和 Act I--V 的 Quest handler 消息路由统一经过该入口；
Act V 的消息 validator 继续接收原始兼容 ordinal，再由既有 validator 做表行重映射，
以兼容扩展表的额外零行和项目自定义 ordinal。移除了 QuestPlayerView 与 Act V handler
中重复的 NPC 名称/ordinal 判断，保留 D2 原有任务状态、消息编号、数值和协议语义不变。

新增 `QuestNpcPolicyTest`，并将其与 `QuestModuleIntegrationTest` 纳入
`m7WorldRegression`。验证：`./gradlew.bat :core:test --tests
com.riiablo.engine.server.quest.QuestNpcPolicyTest --tests
com.riiablo.engine.server.quest.QuestModuleIntegrationTest --no-daemon` 通过；
`./gradlew.bat :core:m7WorldRegression --no-daemon` 共 126 项通过，失败/错误/跳过均为 0；
同时通过 `verifyModuleBoundaries` 与 `git diff --check`。本批不宣称真实双客户端或 MPQ
验收完成，也未修改 D2 任务规则和数值公式。

### M7 第 5 批：地图过渡实现调用者门禁（2026-10-24）

本批先对 M7-B 的生产调用者做安全收口。审计确认 `map2` 在核心生产源码中已经没有
外部调用者；`map5` 仍由 `Ds1Loader`、`Dt1Loader` 及其参数类作为 MPQ 资源解码桥使用，
不能在未迁移资产管线前删除。新增 `MapGenerationBoundaryTest` 门禁：禁止世界生成规则
重新依赖 `map2` 或 `map5`，并将上述四个资产桥调用者列为唯一允许的 `map5` 兼容边界。
这不会改变资产读取行为，也不把资源解码桥误报为地图生成 owner。

`m7WorldRegression` 纳入该架构门禁后共 128 项通过，失败/错误/跳过均为 0；同时通过
`verifyModuleBoundaries` 与 `git diff --check`。M7-B 的旧地图实现删除仍需下一批在真实
MPQ/headless 地图验收后决定，当前不删除 `map5` 资产解码器或其工具依赖。

### M7 第 6 批：全量路径与碰撞策略出口（2026-10-24）

本批将 native path/collision 的所有权纳入 `MapGenerationPolicy`。新增
`usesNativeCollision` 与 `pathCollisionFlags`，通用路径阻塞、Zone flags、飞行碰撞、
Whirlwind 和动态单位碰撞现在只有在当前会话处于 native D2MOO 模式且对应 Zone 已接受
原生碰撞导出时才读取 16 位 native grid；显式 legacy 模式或缺失导出统一使用静态
DT1/DS1 兼容层，不会把未完成的 native grid 当作权威结果。动态碰撞的 spawn-mask fallback
也经过同一策略入口。

新增策略回归覆盖 native/legacy/缺失导出的三种组合；`m7WorldRegression` 共 129 项通过，
失败/错误/跳过均为 0，同时通过 `verifyModuleBoundaries` 与 `git diff --check`。本批未改
D2MOO path/collision 公式、mask 数值或单位行为，只统一读取边界；真实 MPQ/headless 和
双客户端验收仍留在 M7 第 7 批。

### M7 第 7 批：真实资源与双客户端收尾（2026-10-24）

本批完成当前 M7 世界规则链可以独立收口的真实验收。真实 1.10f MPQ 下的
`:core:m7WorldRegression -Pd2Home="G:\BaiduNetdiskDownload\Diablo II 1.10F"` 共 130 项通过，
失败、错误和跳过均为 0。Act I 城镇↔血腥荒地的真实 TCP seam 门禁通过：`planned=2`、
`visited=2`、`transitions=1`、`failures=0`；native terrain、`staticPath` 和 `dynamicPath`
均在生产 D2GS 线程中验证。

真实三客户端 `headlessDenQuestDual` 通过，覆盖双人死亡/复活、第三客户端隔离、Den
任务目标/奖励待结算和重连快照；此前暴露的 LibGDX `Array` 嵌套迭代器崩溃已修复，
`Map.getZone(int,int)` 的兼容查询改为索引遍历，保持原有区域和 boundary-wall 优先级。
新增 `MapZoneLookupTest` 直接覆盖查询重入，防止共享 iterator 回归。
真实双客户端 `headlessA4IzualDual` 也通过，确认 A4 Plains of Despair/Outer Steppes
任务入口和双客户端区域同步仍可用。

收尾时也复测了 `headlessAreaTransitionDual` 和较长的 `headlessMapTour`，两者不作
全绿声明：前者在原生连续 seam `104→105` 处仍要求不存在的静态 Warp entity，属于
验收夹具与 native seam 契约不一致；后者在 Blood Moor/Cold Plains 动态怪物密集位置
出现 runner 清障超时，诊断显示 `nativeCollisionMismatches=0`，属于夹具清障/超时边界。
这些结果不能反推地图规则失败，也不能写成完整巡游通过。`map5` 仍只是 `Ds1Loader`/
`Dt1Loader` 的 MPQ 解码桥，未删除；M7-B 的多代地图最终删除和 M7-A 的全部规则策略
搬迁仍待后续批次，M7 总状态保持“进行中”。

### M8 第 1 批：确定性回放日志与检查点基础（2026-10-24）

本批正式启动 M8，先处理不依赖具体传输实现的 M8-A 基础。新增不可变
`SimulationReplayLog` 与 `SimulationCheckpoint`：回放日志记录游戏 seed、已准入命令的
权威 Tick/sequence、actor/request/target、authority、命令类型和调用方提供的 canonical
payload；检查点记录 Tick、世界摘要、按实体排序的摘要以及命名 RNG stream 的
`context/state/drawCount`。所有字段都有稳定 canonical 编码和 SHA-256 摘要，避免 Java
对象身份或 Map 遍历顺序污染跨进程比较。

检查点比较不会只返回一个失败 hash，而是逐项报告 `tick/entity/field/expected/actual`，
可以定位世界摘要、实体摘要或 RNG stream 的第一处差异。新增回归覆盖相同命令/检查点的
跨实例 digest 稳定性、payload 变化、实体/RNG/Tick 差异和重复检查点拒绝。

验证：

```powershell
./gradlew.bat :core:test --tests com.riiablo.engine.SimulationReplayLogTest --no-daemon
git diff --check
```

结果：M8-A 第一批定向测试通过；本批只建立录制与校验数据边界，不伪装成完整 ECS
世界恢复、断线继续运行或离线重放。下一批接入 `SimulationTickCoordinator`/D2GS 的
命令录制和权威 checkpoint 发布，再验证录制→恢复→继续运行的一致性。

### M8 第 2 批：固定 Tick 回放录制接入（2026-10-24）

本批完成 M8-A 的第二个实现批次，提交 `m8: connect replay capture to fixed tick execution`。
`SimulationTickCoordinator` 现在在命令队列实际 drain、且已通过 admission 后记录命令，
并提供只接受当前已完成 Tick 的 checkpoint 发布接口；提交时不会把尚未执行的 pending
命令写入日志。新增 `SimulationReplayRecorder` 与 `SimulationReplayCapture`，用调用方提供
的显式 payload encoder 生成稳定字符串；日志命令可重建为新的 coordinator 入队命令，保留
Tick、authority、actor/request/target 等准入元数据。

新增确定性回归：录制两个固定 Tick 命令和两个 checkpoint，在新的 coordinator 中按日志
重放，逐 checkpoint 的 `diff` 为空且最终状态一致；同时覆盖 pending 命令不提前录制。

验证：

```powershell
./gradlew.bat :core:m8ReplayRegression --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：M8 回放门禁 10 项通过（日志/检查点 5 项、固定 Tick 协调器 5 项）；没有修改
D2GS/FlatBuffer 协议，也没有宣称 ECS 世界恢复、D2GS 实际录制、断线继续运行或完整离线
回放已经完成。下一批再处理 D2GS 命令适配与权威网络会话边界。

### M8 第 3 批：D2GS 权威会话与命令入口边界（2026-10-24）

本批建立可由 D2GS 接入的协议中立 ingress，不直接把 socket 线程变成仿真写入者。新增
`AuthoritativeSessionRegistry`，统一连接槽、稳定角色标识、actor 身份和 ACTIVE/
DISCONNECTED 生命周期；同一角色的并发连接被拒绝，断线后可用新的连接槽重连，旧连接的
待执行命令会被清除。新增 `D2gsCommandAdapter`，所有网络命令必须先通过活动会话身份、
正数 request id、网络 authority 和目标授权，再进入 `SimulationCommandQueue`，只能由
固定 Tick drain 执行。

回归覆盖并发角色连接拒绝、断线重连、重复 request、未授权目标、未认证连接，以及断线
命令不能继续 drain。该批提供了 D2GS handler 迁移所需的唯一入口契约；现有 D2GS 多类
FlatBuffer handler 尚未全部改接，不能宣称 M8-D 网络三层收敛或完整 D2GS replay 已完成。

验证：

```powershell
./gradlew.bat :core:m8ReplayRegression --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：M8 回放/权威入口门禁 12 项通过；下一批可将 Movement、CastSkill 及物品/任务等
D2GS handler 分组接入该 adapter，并为每组补充真实 FlatBuffer ingress 验收。

### M8 第 4 批：D2GS 移动/技能 drain 回放接入（2026-10-24）

本批把 replay recorder 接入现有 D2GS 的权威 scheduler drain 点：MovementIntent 和
CombatIntent 只有在目标 Tick 到达、实体仍属于已认证玩家并即将进入权威 apply 前才记录；
网络线程收到包或 scheduler submit 时不会提前写入 replay。命令使用显式 canonical payload，
保留 actor、request/sequence、target、authority 和实际 apply Tick；没有修改 Movement/CastSkill
的 D2 结算、目标重算、ACK 或 FlatBuffer 字段。

`SimulationCommandEnvelope.Builder.build(sequence)` 现在向协议适配器公开稳定 sequence
构造能力，D2GS 可将已有 scheduler 的序号直接转换为 replay 元数据。D2GS 仍默认使用
`SimulationReplayRecorder.NONE`，只有宿主显式安装 recorder 才产生日志，因此生产行为和
资源开销保持兼容。

验证：

```powershell
./gradlew.bat :server:d2gs:compileJava :core:m8ReplayRegression --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：D2GS 生产编译和 M8 回放/权威入口 12 项回归通过。物品、任务、NPC、交易等其他
FlatBuffer handler 以及实际 checkpoint 世界摘要仍待后续批次接入；M8-A/M8-D 继续进行中。

### M8 第 5 批：D2GS 权威请求回放命令（2026-10-24）

本批把 D2GS 中已经完成权威变更的请求接入 replay recorder。成功的物品移动、金币、NPC
服务、任务、交易、组队、技能/属性加点和玩家生命周期请求，现在在响应发送前以稳定的
canonical payload 记录；失败、重复、无效 request id（`<= 0`）以及未认证 actor 不会写入
回放。记录点位于实际权威 mutation 之后，避免把网络接收或失败尝试误记成游戏状态。

命令统一携带 `d2gs` 来源、当前仿真 Tick、actor、request、target 和网络 authority 元数据；
没有修改 FlatBuffer 协议、D2 结算、ACK 或响应格式。Movement/CastSkill 仍由前一批的
scheduler drain 记录，避免重复提交。新增覆盖范围不代表所有旧 handler 已迁移到统一 ingress，
也不代表 checkpoint 已包含真实 ECS 世界摘要或可恢复断线状态。

验证：

```powershell
./gradlew.bat :server:d2gs:compileJava :core:m8ReplayRegression --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：生产 D2GS 编译和 M8 回放/权威入口回归通过。M8-A 的真实 checkpoint 世界摘要、ECS
恢复/断线继续运行，以及 M8-D 网络三层唯一 owner 和其余旧 handler 的统一迁移仍待后续批次。

### M8 第 6 批：回放继续运行与检查点校验器（2026-10-24）

本批新增协议中立的 `SimulationReplayRunner`。它从协调器当前 Tick 开始，只提交尚未被
恢复检查点覆盖的命令，按固定 Tick 推进仿真，并在每个记录检查点调用外部摘要源进行逐项
差异校验；结果同时报告起止 Tick、提交命令数、已校验检查点数和 `tick/entity/field`
级 mismatch。已有的 replay log 因此可以用于断线后的继续运行门禁，而不依赖 Java 对象
身份或 Map 遍历顺序。

本批没有把摘要校验器误称为 ECS 存档恢复：真实 D2GS 世界摘要、组件反序列化、RNG stream
恢复和断线 session 重绑定仍需后续 server/ECS adapter；没有修改 FlatBuffer 协议或 D2
规则。回归同时覆盖从 Tick 0 播放、从已有 Tick 继续播放，以及摘要不一致时的可定位失败。

验证：

```powershell
./gradlew.bat :core:m8ReplayRegression --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：M8 replay 回归门禁通过；M8-A 仍待真实 ECS checkpoint/restore，M8-D 仍待网络三层
唯一 owner 和剩余旧 handler 的统一迁移。

### M8 第 7 批：真实 D2GS/ECS checkpoint 摘要与恢复边界（2026-10-24）

本批把 checkpoint 从抽象摘要接到 D2GS 的实际 ECS/网络状态：`D2gsCheckpointSnapshot`
枚举当前 `Networked` 实体，复用 `SerializationManager` 生成与 EntitySync baseline 相同的
权威 FlatBuffer 状态，并按实体生成稳定 SHA-256 digest，再组合成带 Tick 的 world digest。
EntitySync payload 的时钟字段固定为零，避免不同进程启动时间污染跨进程比较；Tick 仍由
checkpoint envelope 单独记录。

D2GS 在安装 replay recorder 时于每个 authoritative Tick 的 outgoing 阶段发布真实摘要，
并保留最近 checkpoint。新增恢复边界可把归档 payload 应用到仍存在的 ECS 实体，全部成功后
才恢复 `AuthoritativeSimulation` 时钟；缺失实体或应用失败会拒绝恢复，不会半恢复后继续运行。
实体创建/删除、跨进程 native factory 重建和完整 RNG stream 反序列化尚未伪装完成，仍需
后续原生工厂与 RNG adapter。

验证：

```powershell
./gradlew.bat :server:d2gs:compileJava :core:test --tests com.riiablo.engine.server.AuthoritativeSimulationTest --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：D2GS 生产编译、authoritative simulation 时钟恢复和 replay 回归通过；当前恢复契约
明确限定为“已有实体的网络状态恢复”，完整断线重建与持久化存档往返仍待后续批次。

### M8 第 8 批：RNG、D2S 与重连验收（2026-10-24）

本批收口 checkpoint 边界之外的三个验收缺口。`NativeRng` 现在提供不可变
snapshot、同身份 restore 和从 snapshot 重建，保留 stream/context、state 与 draw count；
`NativeUnitRandom` 同时捕获/恢复 D2Common 两字 `pSeed`，不会只恢复低字导致后续原生
序列漂移。`D2gsCheckpointSnapshot` 新增显式 RNG 状态入口，摘要 diff 会报告
`rng:<stream>#<context>` 的继续运行偏差。

`ItemWriter` 对可恢复堆叠物品允许原生 `quantity=0`，并把服务端 base 中的
`item_replenish_quantity` materialize 到 D2S 属性列表；普通堆叠物品仍保持最小数量 1。
真实 D2S round-trip 已验证 Amazon `aqv` 保留物品、零数量和可保存的 1.10f rate（该 stat
在 `ItemStatCost.txt` 中为 6 位保存值，运行时测试仍可使用 100）。

真实 1.10f MPQ 的 `headlessAmazonBow` 双客户端 gate 已重新通过，覆盖导弹/命中、弹药
状态和 observer reconnect；重连日志为 `amazon_bow_reconnect_pass`，活动实体未复活为
陈旧状态。该 gate 仍是 headless 角色 D2S 入口生成的真实网络验收，完整跨进程 session
重绑和全部 RNG stream 的统一注册表不在本批虚构完成。

验证：

```powershell
./gradlew.bat :server-domain:test --tests com.riiablo.engine.server.NativeRngTest
./gradlew.bat :core:test --tests com.riiablo.save.NewCharacterStartItemsTest --tests com.riiablo.engine.server.NativeUnitRandomTest
./gradlew.bat :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest
./gradlew.bat :core:m8ReplayRegression :core:m6FinalRegression
./gradlew.bat :server:d2gs:headlessAmazonBow -Pd2Home="G:\\BaiduNetdiskDownload\\Diablo II 1.10F"
git diff --check
```

结果：上述定向测试、M8 replay、M6 final 和真实 MPQ Amazon bow/reconnect gate 通过；完整
`ItemWriterTest` 的旧 `.d2i` 样例在当前工作区缺失，因此未把缺少 fixture 误报为实现失败。

### M5 第 1 批：Fire Bolt 战斗垂直切片（2026-10-24）

完成日期：2026-10-24；本批完成 M5 的第一批受控技能族切片，不宣称 M5 整体完成。

实现与边界：

- 新增 `FireBoltVerticalSliceTest`，从服务端 `SkillCastEvent` 准入开始，验证 Sorceress 的 Fire Bolt 技能身份、技能等级和 Mana 消耗；无效 Mana 不创建导弹且不改变权威状态。
- 通过真实 `SkillDoEvent` 和 Skills/Missiles 表创建 Fire Bolt 导弹，检查技能伤害在发射时固定到导弹快照，并由 `MissileCollisionSystem` 完成固定点火焰伤害和生命变化。
- 新增 `m5FireBoltRegression` 门禁，组合 Fire Bolt 1--20 级 D2MOO 黄金值、Fire Bolt 垂直切片和元素导弹/生命快照回归；不修改 D2GS 协议，不提前实现 M8 replay/checkpoint，也保留旧兼容测试装配。

提交：`049d20e7f`。

验证：

```powershell
./gradlew.bat :core:m5FireBoltRegression --no-daemon
git diff --check
```

结果：M5 Fire Bolt 门禁 40 个测试通过；包含 cast 准入/耗蓝、真实导弹创建、固定点命中、DamageEvent/DamageAppliedEvent 和 VitalsP 同步，以及现有 D2MOO Fire Bolt 等级黄金值。使用仓库内测试资源，未使用外部 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F` 安装，未启动真实客户端或 D2GS 双客户端。

D2MOO 依据：Fire Bolt 等级伤害沿用 `D2Skills.cpp:2459`、`2623`、`2685` 与 `Units/Missile.cpp:467` 的既有黄金测试注释；本批没有凭经验改写公式。

剩余问题：M5 仍未完成普通攻击、其他技能族、D2GS/真实 1.10f 端到端验收和关键 Tick 检查点/完整回放；M3/M4 的全量网络事件及 RNG 调用方迁移债务仍按原记录保留。当前垂直切片仍允许旧兼容事件装配用于离线测试，不能视为“旧入口已全部删除”。

下一迁移：M5 第 2 批，继续扩展 Fire Bolt 相邻技能/普通攻击前先补 D2GS headless 命令入口和可复核的最小检查点摘要。

### M5 第 2 批：Fire Bolt D2GS headless 与最小检查点摘要（2026-10-24）

完成日期：2026-10-24；本批把第 1 批的 Fire Bolt 链路接到真实 D2GS TCP headless 入口，并增加可复核的命中摘要；仍不宣称 M5 整体完成。

实现与边界：

- `D2GSHeadlessClient` 新增 `--generated-fire-bolt`，生成 1.10f Sorceress 等级 30、Fire Bolt 等级 20 的确定性 D2S；通过真实 `CastSkillRequest`，不直接调用服务端 `SkillDoEvent`。
- 新增 `:server:d2gs:headlessFireBolt`，在真实 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F` 资源下验证 TCP 准入、Blood Moor 目标、Fire Bolt 导弹同步、命中生命变化和导弹生命周期顺序。
- 新增不可变 `CombatCheckpoint`，以 Tick、caster/target、skill、8.8 生命前后值和导弹数量生成 canonical 摘要及 SHA-256；headless 命中后输出摘要。它只用于诊断/比对，不是 M8 完整世界快照或恢复格式。

提交：`67a1a7d3e`。

验证：

```powershell
./gradlew.bat :server-domain:test --tests com.riiablo.engine.server.combat.CombatCheckpointTest --no-daemon
$env:D2_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
./gradlew.bat :server:d2gs:headlessFireBolt --no-daemon
git diff --check
```

结果：`CombatCheckpointTest` 3 项通过；真实 1.10f headless 门禁通过，观察到 Fire Bolt skill=36、2 个 owner missile、目标生命 `1000.00→888.10`，命中 Tick 与导弹创建顺序一致，并输出 canonical `25|117|119|36|256000|227353|2` 及稳定 SHA-256 摘要。未修改 D2GS FlatBuffer 字段，未启动图形客户端。

D2MOO/原版依据：Fire Bolt 伤害仍由第 1 批 D2MOO `D2Skills.cpp:2459/2623/2685` 与 `Units/Missile.cpp:467` 黄金值约束；本批只扩展真实网络入口和摘要，不改规则公式。

剩余问题：M5 仍需普通攻击/其他技能族、双客户端观察、真实命中事件与表现快照收口；checkpoint 尚不能恢复/继续仿真，M8 replay/network 三层仍未完成。旧兼容事件装配仍服务离线测试，不能视为旧入口已删除。

下一迁移：M5 第 3 批，扩展 Fire Bolt 相邻火系技能或普通攻击，并把命中结果摘要接入双客户端观察门禁。

### M5 第 3 批：Fire Ball 相邻技能族与双客户端检查点（2026-10-24）

完成日期：2026-10-24；本批扩展 Fire Bolt 相邻的 Fire Ball 技能族，把单客户端命中、原生爆炸子弹和双客户端权威观察放入同一个验收门禁；不拆成单类补丁。

实现与边界：

- 新增 `m5FireSkillRegression`，集中覆盖 Fire Bolt/Fire Ball 的等级黄金值、ECS 命中、Fire Ball `SrvHit01` 爆炸生命周期与投射物数据，共 70 个测试。
- 新增 `:server:d2gs:headlessFireBall`，复用真实双 TCP 客户端 area-skill 入口，使用真实 1.10f 资源验证 Fire Ball parent/`explodingarrowexp` child 的共享观察、目标生命变化和观察端一致性。
- Fire Ball 双客户端命中后生成 `CombatCheckpoint`；owner/peer canonical 摘要必须一致，并输出稳定 digest。没有修改 FlatBuffer 协议，也没有把该摘要当作 M8 完整回放/恢复格式。

提交：`514e9d2ba`。

验证：

```powershell
./gradlew.bat :core:m5FireSkillRegression --no-daemon
$env:D2_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
./gradlew.bat :server:d2gs:headlessFireBall --no-daemon
git diff --check
```

结果：`m5FireSkillRegression` 70 项通过；真实 1.10f 双客户端 Fire Ball 门禁通过，观察到 skill=47、root/child missile 类型 `[62, 42]`、两个观察端共享 5 个 missile entity，目标生命 `6.00→0.00`，并输出 canonical `23|118|138|47|1536|0|5` 与一致 digest。该低生命目标是现有确定性 Blood Moor fixture 的实际目标，不代表所有怪物均一击致死。

D2MOO/原版依据：Fire Ball 的 `MISSMODE_SrvHit01` 爆炸 fan-out 与 child 生命周期沿用现有 `SorceressFireBallIntegrationTest`、`NativeExplosionLifecycleTest` 的 D2MOO 注释和 1.10f 表数据；本批只收紧双观察与检查点一致性。

剩余问题：M5 仍需普通攻击/物理伤害、表现快照事件出口、旧兼容入口清理和最终全量出口复核；checkpoint 仍是摘要而非可恢复 replay。M3/M4 的全量网络事件与 RNG 调用方债务不在本批伪装解决。

下一迁移：M5 第 4 批，完成普通攻击/物理伤害链路并纳入同一数据、ECS、D2GS 和双客户端门禁，再评估 M5 最终收口批次。

### M5 第 4 批：物理投射物与普通武器伤害结算（2026-10-24）

完成日期：2026-10-24；本批完成物理投射物/Throw 与普通武器投射物的 ECS 结算、真实 D2GS headless 门禁和固定检查点回归；不宣称近战普通攻击、表现快照或 M5 整体已完成。

实现与边界：

- 新增 `m5PhysicalCombatRegression`，覆盖物理投射物碰撞、攻击评级/伤害所有权、普通武器伤害快照、Amazon Throw/普通弓箭生命周期以及 CombatSystem/native fixed physical damage 回归；只纳入与本批直接相关且稳定的 Amazon 普通武器测试方法，不把未稳定的 Immolation Arrow 专项资源测试扩大为本批门禁。
- `D2GSHeadlessClient` 增加 `--require-physical` 校验，`:server:d2gs:headlessPhysicalThrow` 使用生成 Amazon 的真实 Throw skill=2，通过 1.10f TCP/D2GS 导弹链路验证 owner missile 与物理生命结算。
- 该 gate 输出可复核的 canonical `CombatCheckpoint`；没有修改 FlatBuffer 协议，也没有把摘要当作 M8 可恢复 replay/完整表现快照。

验证：

```powershell
./gradlew.bat :core:m5PhysicalCombatRegression --no-daemon
$env:D2_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
./gradlew.bat :server:d2gs:headlessPhysicalThrow --no-daemon
git diff --check
```

结果：`m5PhysicalCombatRegression` 100 项测试通过（0 失败、0 跳过）；真实 1.10f `headlessPhysicalThrow` gate 通过，观察到 Amazon Throw skill=2、1 个 owner missile、目标生命 `1000.00→991.10`，并输出 canonical `26|117|119|2|256000|253722|1` 与 digest `2e537ca6ce7286cc4e9da7956f8940d5f7e45fc2c05b0ad28f6c299c4a41bff3`。使用资源路径 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；未启动图形客户端，未修改 D2GS FlatBuffer 字段。

剩余问题：近战普通攻击的完整 ECS/D2GS 链路、表现快照事件出口、旧兼容入口清理和 M5 最终全量出口复核仍未完成；checkpoint 仍是摘要而非可恢复 replay。M3/M4 的全量网络事件与 RNG 调用方债务不在本批伪装解决。

下一迁移：M5 第 5 批或最终收口批，集中完成近战普通攻击/表现快照与旧入口复核；只有相关门禁和真实资源验证完成后才更新为完成。

### M5 第 5 批：近战普通 Attack 与观察端 checkpoint（2026-10-24）

完成日期：2026-10-24；本批把 Amazon 普通 Attack（skill=0）接入既有权威近战链路，补齐 ECS keyframe/物理结算、双客户端观察、重连恢复和 checkpoint 一致性门禁；不宣称 M5 的表现事件出口、旧兼容入口清理或完整 replay 已完成。

提交：`9df82404f`。

实现与边界：

- `D2GSHeadlessClient` 的 Amazon melee fixture 允许原生 Attack(0)，生成存档保留 CharData 默认普通攻击，不把 Attack 伪造为技能等级；既有 Jab/Impale 等专用技能路径保持不变。
- 新增 `:server:d2gs:headlessAmazonAttack`，复用生产 D2GS TCP 双客户端/重连 gate，使用真实 1.10f `spr` 近战武器验证普通攻击命中、两端生命一致、观察端重连不恢复旧生命，并输出共享 `CombatCheckpoint`。
- 新增 `m5MeleeCombatRegression`，集中覆盖普通 Attack keyframe、武器耐久、弓/标枪普通攻击距离策略、Amazon 近战生命周期、普通武器伤害快照与服务端生命/事件线；过滤掉与本批无关的 Throwing Mastery 专项资源测试。

验证：

```powershell
./gradlew.bat :core:m5MeleeCombatRegression --no-daemon
$env:D2_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
./gradlew.bat :server:d2gs:headlessAmazonAttack --no-daemon
git diff --check
```

结果：`m5MeleeCombatRegression` 53 项测试通过（0 失败、0 跳过）；真实 1.10f gate 通过，观察到 skill=0、双客户端目标生命 `1000000.0→999996.4`，本次运行输出共享 checkpoint canonical `23|117|142|0|256000000|255999072|0`，digest `c00dafe642a7cf374f83f0fb8ec1510a08f49cb04686b715191e24bbffe0976c`，并验证重连观察端恢复生命 `999996.94` 且无陈旧命中前状态。tick/entity 字段随独立 server run 分配，比较时以 canonical 字段和 digest 为本次运行摘要；未修改 FlatBuffer 字段，未启动图形客户端。

D2MOO/原版依据：普通 Attack 使用 `SkillCodes.attack=0` 的原生 `SrvDo002`/普通武器分派；物理伤害、武器耐久和 keyframe 顺序沿用现有 D2MOO 注释与 `CombatPipelineIntegrationTest`/`OrdinaryWeaponDamageTest` 的固定点断言，没有凭经验改写公式。

剩余问题：M5 仍需最终复核表现快照事件出口、旧兼容入口删除范围以及摘要 checkpoint 到 M8 可恢复 replay 的转换；本批只完成普通近战权威结算和网络观察，不把这些依赖提前宣称完成。

下一迁移：M5 最终收口批，集中清理/登记战斗旧入口并把表现快照与 replay 依赖交给 M8；若旧入口仍有明确生产结算职责，继续保留为未完成项。

### M5 最终收口批：生产结算/快照/表现出口复核（2026-10-24）

完成日期：2026-10-24；本批完成 M5 战斗垂直切片在当前代码范围内可独立完成的最终复核，
不把 M8 的可恢复 replay、完整网络事件统一出口提前计入。

实现与门禁：

- 新增 `M5CombatBoundaryAuditTest`，审计专用 D2GS 是否在构造 `World` 前注册
  `ServerGameplayContext`；Fire Bolt、Fire Ball、物理投射物和普通攻击涉及的生产结算类均必须
  经过 `DamageSettlement.commit`，不能直接写入生命损失。
- 审计 `NetworkSynchronizer` 只发送 `SerializationManager` 生成的权威实体快照，并保留
  `MissileImpactEvent`/`SoundEvent` 作为表现事件出口；它不得订阅或传输可变的
  `DamageEvent`/`DamageAppliedEvent`。`VitalsSerializer` 仍是生命、法力和耐力的唯一快照投影。
- 审计客户端表现系统只订阅已提交的 `DamageAppliedEvent`；旧兼容常量仅用于解释已提交事实，
  不作为新的事件入口。`ServerGameplayContext.legacyCombatContext` 保留给无上下文的离线
  测试/工具，真实 D2GS 生产装配始终注入世界上下文。
- 新增 `:core:m5FinalCombatRegression`，集中运行前五批的技能、物理、近战和表现回归及本批审计。

验证：

```powershell
./gradlew.bat :core:m5FinalCombatRegression --no-daemon
git diff --check
```

结果：最终 M5 回归 214 项通过、0 失败；包含生产出口审计、`DamageAppliedEvent` 表现边界、
Fire Bolt/Fire Ball、物理投射物、普通武器和普通 Attack 门禁。未修改 FlatBuffer 字段，未启动
图形客户端；真实 1.10f 双客户端证据沿用第 2～5 批已通过的 gate。

D2MOO/原版依据：本批不改伤害公式或 1.10f 行为，只锁定既有 `SUNITDMG` 对应的单一结算边界、
`VitalsP` 快照和已提交命中事实出口；规则证据仍以各批已有 D2MOO 行号与原版资源回归为准。

剩余问题：M5 的战斗链路和当前可独立完成的快照/表现出口已完成；摘要 checkpoint 转换为可恢复
replay、完整网络事件统一出口和回放校验仍属于 M8，不能在 M5 中宣称完成。M5 计划行因此继续
标记为“进行中”，直到 M8 依赖收口。

下一迁移：继续 M6/M7 领域批次；M8 再接管可恢复 checkpoint/replay、统一网络表现事件和最终旧
直连入口删除。

### M0 完成记录（2026-10-21）

完成日期：2026-10-21
提交：`3baaf2497`（M0 基线完成记录）
验证：`:verifyModuleBoundaries`、`:shared:test`、`:game-data:test`、`:server-domain:test`、`:server-systems:test`、`:core:architectureRegression`、`:core:compileJava`、`:server:d2gs:compileJava`、`:desktop:compileJava` 全部通过；详细限制见 `docs/dark-magic-m0-baseline-2026-10-21.md`
真实资源：否
D2MOO 依据：本批不改变暗黑规则；D2MOO 作为既有兼容证据保留在依赖/范围记录中
剩余问题：全量行为测试、真实 MPQ/headless 验收、M1-A/M1-C 反向依赖迁移和 M3/M4/M8 未完成子项
下一迁移：M1（历史记录；当前进度以上面的统一验收为准）

## 每次迁移的固定交付格式

每个迁移完成后，在本文件对应行补充以下信息：

```text
完成日期：YYYY-MM-DD
提交：<git hash>
验证：<命令和通过数量>
真实资源：<是否使用 G:\\BaiduNetdiskDownload\\Diablo II 1.10F>
D2MOO 依据：<源码文件/函数>
剩余问题：<明确到模块或行为>
下一迁移：<M1-M8 编号>
```

## 迁移批次建议

按原计划的完整交付范围组织批次，每个批次可以包含实现与验收记录提交；单类修补、编译适配和补记 hash 不另计批次。每个完整批次提交并推送：

- M0：2 个批次，分别建立目录清单和测试基线。
- M1：3 个批次，分别整理依赖、架构检查和旧违规登记。
- M2：3～4 个批次，按玩家/单位、物品/任务、佣兵/地图状态迁移。
- M3：3～4 个批次，按命令队列、事件顺序、死亡/删除边界迁移。
- M4：3～5 个批次，按表数据、固定点、RNG 和缺失数据策略迁移。
- M5：5 个以上批次，先完成一个技能族，再扩展普通攻击和其他技能。
- M6～M8：每行 3～5 个批次，按领域和验收门槛拆分。

因此完整计划预计为 **8 次主要迁移、约 25～35 个完整批次**，不将这一数量全部消耗在 M1。当前 M1 三项交付已验收，M2 四个计划批次已完成，下一项为 M3；已有 M3/M8 局部实现不代表后续迁移已完成。



