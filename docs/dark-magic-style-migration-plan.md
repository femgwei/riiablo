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

### M8-C 第 1 批：网络副本表现事件出口（2026-10-24）

本批把网络副本接收与表现处理进一步隔离。`ClientNetworkReceiver` 在验证并提交对象、
生命和物品结果后，只发布不可变的 `ClientObjectStateEvent`、`ClientPlayerHitEvent`、
`ClientItemPickupEvent` 和 `ClientItemDropReplayEvent`；不再直接调用 `ClientEntityFactory`、
`PlayerHitSound` 或 `ItemEffectManager`。实体工厂、伤害表现和物品表现系统分别订阅这些事件，
仍沿用既有 EventSystem/固定 Tick 边界，未修改 FlatBuffers、D2 结算或权威组件写入。

本批同时把网络对象的基础 mode/stateFlags 先保留在 ECS 权威副本，再由表现 owner 投影
Interactable/Selectable，兼容无表现工厂的 headless 测试装配。物品拾取音效和失败后的地面
物品 bounce 也改为在事件阶段执行，避免网络接收线程直接触碰表现资源。

验证：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava --no-daemon --max-workers=1 --console=plain
./gradlew.bat :core:test --tests com.riiablo.architecture.ClientUiBoundaryTest `
  --tests com.riiablo.engine.client.ClientNetworkReceiverTickBoundaryTest `
  --tests com.riiablo.engine.client.ClientObjectSnapshotStateTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：core 生产/测试编译通过，架构、网络事件 Tick 边界、对象状态和客户端装配定向回归
通过。表现扫描中的 `ClientNetworkReceiver` 直接 handler 依赖已清除；`ClientGameplayContext`、
`Label` 以及 host 装配兼容债务仍待后续 M8-C 批次处理，不能据此宣称 M8-C 或 M8 整体完成。

### M8-C 第 2 批：网络副本标签生命周期事件（2026-10-24）

本批继续收口网络副本生命周期的表现写入。召唤物和佣兵快照在需要清理旧世界标签时，
`ClientNetworkReceiver` 只发布不可变的 `ClientLabelRemovalEvent`，不再直接引用
`presentation.component.Label` 或修改表现组件；`ClientEntityFactory` 在事件阶段负责移除
标签。权威实体、佣兵所有权和网络包字段保持不变，事件仍遵守固定 Tick 的 EventSystem 边界。

验证：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava --no-daemon --max-workers=1 --console=plain
./gradlew.bat :core:test --tests com.riiablo.architecture.ClientUiBoundaryTest `
  --tests com.riiablo.engine.client.ClientNetworkReceiverTickBoundaryTest `
  --tests com.riiablo.engine.client.ClientObjectSnapshotStateTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：网络副本标签事件边界和客户端定向回归通过；`ClientNetworkReceiver` 的直接表现组件
依赖已清零。`ClientGameplayContext`、物品适配器和 host 装配兼容债务仍待后续 M8-C 批次，
M8-C/M8 整体继续标记为进行中。

### M8-C 第 3 批：客户端表现装配 owner 迁移（2026-10-24）

提交：`40fddea17`、`e63ee2bc7`。

本批将客户端表现系统的装配配方迁移到 `com.riiablo.presentation.ClientSimulationAssembly`，
由 presentation 侧拥有表现系统的注册与相对顺序；`GameScreen` 和装配回归测试改为引用新的
presentation owner。引擎客户端仍只提供仿真系统、输入系统和生命周期适配器，表现系统不再通过
`engine/client/ClientSimulationAssembly` 作为 host 组合入口反向挂载。

为保持 `ClientVendorSystem` 的现有行为，`ClientItemManager` 新增只读的 `clientContext()` 访问器，
装配只读取 `charData`/`playerId` 端口，不改变物品事务、网络协议或权威 Tick 顺序。
本批同时将本地背包满时的地面物品 bounce 从 `ClientItemManager` 对
`ItemEffectManager` 的直接查找改为 `ClientItemDropReplayEvent`，并移除
`ZoneChangeTracker` 中未使用的 `Stage` 注入；Box2D/Shape 调试器也迁入
`presentation.debug`，由表现装配统一注册。

验证：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：core 生产/测试编译通过，模块边界、M1-A 表现边界和客户端装配回归通过；装配债务从
`engine/client/ClientSimulationAssembly` 清除，物品 bounce、ZoneChangeTracker 和调试渲染器的直接表现依赖也已清除。输入、物品管理器和测试 harness 的兼容
`ClientGameplayContext` 依赖仍按后续 M8-C 批次处理，M8-C/M8 整体继续标记为进行中。

本批收尾前置的两个窄端口已落地：`ActTransitionSystem` 通过 `ClientActTransitionPort` 接收
已接受的换 Act 意图，`AutoInteracter` 通过 `ClientPlayerPort` 读取玩家身份；真实 screen 和
测试 harness 均显式注册端口，旧 `ClientGameplayContext` 仍仅保留给尚未迁移的物品/输入/网络兼容路径。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：M8-C 第 4 批，收口 `ClientItemManager`/`NetworkedClientItemManager` 的物品表现事件
出口和窄接口，再处理输入/交互及 debug 适配器残余依赖。

### M8-C 第 4 批：尸体生命周期与测试 host 窄边界（2026-10-24）

提交：`c341f9d29`。

`CorpseManager` 不再读取表现侧 `AnimationWrapper`；尸体开始淡出和完成删除时发布不可变的
`ClientCorpseLifecycleEvent`，后续表现适配器可在固定 Tick 事件阶段订阅。网络尸体和玩家尸体
仍由原有权威生命周期保护，普通尸体的计时、淡出和删除语义不变。

`ClientSystemHarness` 改为显式接收 `ClientActTransitionPort` 与 `ClientPlayerPort`，不再把
完整 `ClientGameplayContext` 注册进测试世界；对应换 Act 回归使用窄端口验证，生产 screen 的
端口注册保持不变。

验证：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava `
  :core:test --tests com.riiablo.engine.client.CorpseManagerTest `
  --tests com.riiablo.engine.client.ClientGameplayContextTest --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：定向尸体/测试 host 回归、core 编译、模块边界和 M1-A 表现边界通过；M8-C 债务由 19 条
降至 17 条。物品、网络接收、鼠标/键盘输入和悬停仍需等待其对应 M2/M3/M6/M8-D 端口完成，
M8-C/M8 整体继续标记为进行中。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：M8-C 第 5 批，处理 `ClientItemManager` 与 `NetworkedClientItemManager` 的音效、
cursor 和物品表现事件出口。

### M8-C 第 5 批：物品音效事件出口（2026-10-24）

提交：`30844b140`。

本批将本地和联网物品管理器中的拾取、药水、城镇传送门、鉴定等音效播放改为发布既有
`SoundEvent`，由 `SoundEventSystem` 在表现阶段按 listener、区域和距离策略播放。物品系统
仍直接读取角色物品状态并提交本地/网络事务；cursor 操作、物品快照和协议字段不变。

验证：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava `
  :core:test --tests com.riiablo.engine.client.ClientItemManagerGoldPickupTest `
  --tests com.riiablo.engine.client.NetworkedClientItemManagerTest `
  --tests com.riiablo.engine.client.ClientItemIntentSystemTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：物品本地/联网定向回归、core 编译、模块边界和 M1-A 表现边界通过。物品管理器仍保留
`ClientGameplayContext` 用于角色物品、文件表和 cursor 的权威兼容读取，属于 M2/M6 后续窄
接口工作；M8-C/M8 整体继续标记为进行中。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：继续处理物品 cursor/状态端口，或在 M2/M6 物品事务端口完成后删除剩余 context 依赖。

### M8-C 第 6 批：鼠标输入与 UI 命中适配器（2026-10-24）

提交：`ff4e3c87e`。

本批新增 `ClientPointerUiPort` 与 presentation-owned `ClientPointerUiAdapter`。`CursorMovementSystem`
不再直接依赖 Scene2D `Stage`/`Actor`、`UIUtils`、`DialogManager` 或 `MenuManager`，改由适配器提供
UI 命中来源、修饰键、佣兵肖像、cursor 状态和面板阻塞/关闭意图；GameScreen 显式注册适配器，
保留固定 Tick 点击队列、Alt 物品标签、佣兵药水和菜单/对话点击语义。

验证：

```powershell
./gradlew.bat :core:compileJava :core:compileTestJava `
  :core:test --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests com.riiablo.engine.client.HoveredManagerTest --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：core 编译、客户端装配/悬停定向回归、模块边界和 M1-A 表现边界通过；M8-C 债务由 17 条
降至 12 条。`CursorMovementSystem` 仍保留角色技能/物品数据读取用的 `ClientGameplayContext`，
悬停系统的表现组件依赖和网络/物品状态依赖留待后续端口批次。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：收口 `HoveredManager` 的表现组件与上下文依赖，或在 M2/M3 输入意图端口完成后统一处理。

### M8-C 第 7 批：悬停命中表现适配器（2026-10-24）

提交：`b7c217fca`。

本批新增 engine-side 的 `ClientHoverPresentationPort`/`ClientHoverBounds`，并由
`ClientHoverPresentationAdapter` 统一持有 `BBoxWrapper`、动画/overlay 高亮、标签边界和
Unsummon 状态。`HoveredManager` 现在只依赖 `Selectable`、`Position` 与窄端口，保留实体筛选、
可交互目标优先级、距离排序、Alt 下地面物品标签命中、召唤物 Unsummon 命中和 DT 模式清理语义。
客户端装配显式注册适配器；标签边界纯数学回归迁移到 presentation 测试。

验证：

```powershell
./gradlew :core:compileJava :core:compileTestJava :core:test `
  --tests com.riiablo.engine.client.HoveredManagerTest `
  --tests com.riiablo.presentation.ClientHoverPresentationAdapterTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：core 编译、悬停/适配器/客户端装配定向回归、模块边界和 M1-A 表现边界通过；M8-C 债务由
12 条降至 6 条。剩余 6 条均为 `ClientGameplayContext` 的物品、网络副本、鼠标/键盘输入兼容读取，
留待后续状态/输入端口批次处理。M8-C/M8 整体继续标记为进行中。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：继续收口 `ClientItemManager`/`NetworkedClientItemManager`/`ClientNetworkReceiver` 的
状态出口，或在输入意图端口批次中处理 `CursorMovementSystem` 与 `KeyboardMovementSystem` 的剩余 context 依赖。

### M8-C 第 8 批：客户端输入状态端口（2026-10-24）

提交：`aa7322f14`。

本批新增 engine-side 的 `ClientActionInputPort` 与 `ClientKeyboardInputPort`，由 presentation 侧
`ClientGameplayInputAdapter` 统一提供当前技能、技能表、投掷/远程武器和移动阻塞事实。`CursorMovementSystem`
与 `KeyboardMovementSystem` 不再直接依赖 `ClientGameplayContext`/`ClientUiServices`；GameScreen 为真实
客户端世界注册同一个适配器的两个窄端口，保留固定 Tick 输入队列、城镇技能限制、普通攻击/投掷/弓箭判定和
雇佣兵面板阻塞语义。缺少适配器的离线构造保持安全降级，不修改服务端规则或协议。

验证：

```powershell
./gradlew :core:compileJava :core:compileTestJava :core:test `
  --tests com.riiablo.presentation.ClientGameplayInputAdapterTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests com.riiablo.architecture.ClientUiBoundaryTest --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：core 编译、输入适配器/客户端装配/表现边界回归通过；M8-C 债务由 6 条降至 3 条。剩余债务为
`ClientItemManager`、`NetworkedClientItemManager` 和 `ClientNetworkReceiver` 的
`ClientGameplayContext` 兼容读取，后续按物品事务与网络副本状态出口收口。M8-C/M8 整体继续标记为进行中。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：处理物品事务/网络副本的状态适配器，统一提交已确认的物品快照和表现事件。

### M8-C 第 9 批：网络副本表现端口（2026-10-25）

提交：`ffa569880`。

本批新增 `ClientNetworkPresentationPort` 与 `ClientNetworkPresentationAdapter`。`ClientNetworkReceiver`
不再直接依赖 `ClientGameplayContext`，网络副本所需的字符串格式化、表数据、角色/游标事实以及 NPC/交易
结果展示均通过窄端口提供；GameScreen 为真实客户端注册适配器。实体同步、物品快照提交、网络事件顺序和
协议格式保持不变，缺少表现端口时安全降级为无 UI 输出。

验证：

```powershell
./gradlew :core:compileJava :core:compileTestJava :core:test `
  --tests com.riiablo.presentation.ClientNetworkPresentationAdapterTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests com.riiablo.architecture.ClientUiBoundaryTest --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：core 编译、网络表现适配器/客户端装配/表现边界回归通过；M8-C 债务由 3 条降至 2 条。剩余两条
均为 `ClientItemManager` 与 `NetworkedClientItemManager` 的 `ClientGameplayContext` 物品事务兼容读取，
留待下一批统一迁移；M8-C/M8 整体继续标记为进行中。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：收口本地/联网物品管理器的角色物品端口，并保持 `ClientItemIntentSystem` 的固定 Tick 准入与
联网 `ItemMoveResult` 快照顺序不变。

### M8-C 第 10 批：物品事务上下文端口（2026-10-25）

提交：`9944e000a`。

本批新增 engine-owned 的 `ClientItemContextPort` 与 presentation 侧 `ClientItemContextAdapter`。
`ClientItemManager`、`NetworkedClientItemManager` 及其 `ClientItemIntentSystem` 现在通过窄端口读取角色、
物品、游标、表数据和客户端状态；GameScreen 为真实客户端注入适配器，旧无参测试构造保留全局兼容源。
本地拾取/金币、联网物品请求、固定 Tick 意图重新校验和 `ItemMoveResult` 快照顺序均保持不变。

验证：

```powershell
./gradlew :core:compileJava :core:compileTestJava :core:test `
  --tests com.riiablo.engine.client.ClientItemManagerGoldPickupTest `
  --tests com.riiablo.engine.client.ClientItemIntentSystemTest `
  --tests com.riiablo.engine.client.NetworkedClientItemManagerTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests com.riiablo.architecture.ClientUiBoundaryTest --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：物品事务/联网意图定向回归、core 编译、模块边界和 M1-A 表现边界通过；M8-C 清单中的直接
`ClientGameplayContext` 债务已清零。M8-C/M8 整体仍不宣称完成，事件/快照出口、真实网络重连和完整
表现替换验收继续按 M8-A/M8-D 及后续门禁推进。

真实资源：未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；本批不修改暗黑规则。

下一迁移：转入 M8-C 收尾审计，检查剩余表现兼容调用和事件/快照出口；若仍有可独立收口的边界，再按
完整验收批次处理，不新增微批次。

### M8-C 收尾审计：engine/client 表现端口边界（2026-10-25）

提交：`f0c54b759`。

本次审计把 `ClientUiBoundaryTest` 扩展为扫描整个 `engine/client` source set，禁止重新引入
`ClientGameplayContext`、`ClientUiServices` 或任意 `com.riiablo.presentation` 直接边；客户端输入、悬停、
网络副本和物品事务只能通过 engine-owned port 接收表现事实。审计没有发现新的独立 M8-C 迁移点，
`m1-presentation-debt.tsv` 当前为零条旧边。

验证：

```powershell
./gradlew :core:compileTestJava :core:test `
  --tests com.riiablo.architecture.ClientUiBoundaryTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  :core:m8ReplayRegression :server:d2gs:compileJava --no-daemon --max-workers=1 --console=plain
python tools/architecture/migration_boundaries.py --mode check
& tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
git diff --check
```

结果：边界审计、客户端装配、M8 replay 回归和 D2GS 编译全部通过。M8-C 的直接 context/表现包依赖
已完成收口，但 M8-C 总目标仍与 M8-A/M8-D 的事件/快照、真实恢复、网络唯一入口和重连验收相关，
因此不把本次审计写成 M8-C 或 M8 整体完成声明。

真实资源：本次未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`；未修改暗黑规则。

下一迁移：进入 M8-A/M8-D 的真实 ECS checkpoint/replay 与 D2GS handler 唯一入口批次；优先选择能
同时增加真实网络验收和可恢复状态门禁的一组 handler，不再继续拆分 M8-C 端口微批次。

### M8-D 第 1 批：Movement/CastSkill 统一 D2GS ingress（2026-10-25）

本批把 D2GS 中最成熟的 Movement/CastSkill 固定 Tick 路径接入统一
`D2gsCommandAdapter`，不改 FlatBuffer 字段、移动/战斗规则或原有 scheduler 语义。

实现与边界：

- D2GS 连接建立/断开现在绑定 `AuthoritativeSessionRegistry`；断开会取消该 actor 尚未
  消费的 ingress 命令，避免迟到网络包进入权威 ECS。
- Movement/CastSkill 入站先经过 `D2gsCommandAdapter` 的连接身份、网络 authority、请求
  元数据和固定 Tick queue，再转交既有 `MovementIntentScheduler`/
  `CombatIntentScheduler`；原有序号去重、目标/技能校验、ACK、replay command 记录保持在
  原权威路径中。
- 已知实体目标经过 adapter target gate；未知目标仍交给领域 scheduler 产生原协议拒绝，
  保留客户端可观察的错误/ACK 行为。其他 D2GS handler 尚未迁移，不能宣称 M8-D 完成。

验证：

```powershell
./gradlew :server:d2gs:compileJava :core:test --tests com.riiablo.engine.D2gsCommandAdapterTest --no-daemon --console=plain
./gradlew :core:m8ReplayRegression :server:d2gs:compileJava --no-daemon --console=plain
git diff --check
```

结果：上述编译、adapter 会话/命令准入测试和 M8 replay 回归全部通过；本批未使用
`G:\\BaiduNetdiskDownload\\Diablo II 1.10F`，未修改暗黑规则。真实双客户端 Movement/CastSkill
重连与可恢复 checkpoint 仍需后续批次验收。

下一迁移：扩展同一 ingress 到其余 D2GS authoritative handler，并把真实 ECS checkpoint
restore、RNG/D2S 和跨进程重连验收接入同一网络门禁；不把本批写成 M8-A/M8-D 完成。

### M8-D 第 2 批：技能点、属性点与 Party/Trade handler ingress（2026-10-25）

本批继续沿用第 1 批的 D2GS fixed-tick ingress，把五类已有请求缓存/权限边界的
authoritative handler 接到同一入口；不改协议字段、领域规则或原有幂等缓存。

实现与边界：

- `SelectSkillRequest`、`SpendSkillPointRequest`、`SpendStatPointRequest`、`PartyRequest`
  和 `TradeRequest` 不再从 FlatBuffer `process` 分支直接执行，而是先进入
  `D2gsCommandAdapter`，在当前 simulation Tick 统一消费。
- adapter 使用内部 ingress 序列避免吞掉协议层重复 request；技能点、Party、Trade 的既有
  request cache 继续负责响应重放和 `REQUEST_ID_REUSED` 判定，成功命令仍由原
  `recordAuthoritativeCommand` 记录。
- Party/Trade 的已知实体目标经过 adapter target gate；非法/未知目标保留给原领域服务，
  继续生成原协议错误结果。NPC、任务、物品、金币和生命周期 handler 尚未迁移，M8-D 仍未完成。

验证：

```powershell
./gradlew :core:test `
  --tests com.riiablo.engine.server.party.PartyServiceProtocolTest `
  --tests com.riiablo.engine.server.party.PartyRequestCacheTest `
  --tests com.riiablo.engine.server.player.SkillPointRequestCacheTest `
  --tests com.riiablo.engine.server.player.PlayerSkillPointAuthorityTest `
  --tests com.riiablo.engine.server.player.PlayerStatPointAuthorityTest `
  --tests com.riiablo.engine.server.trade.TradeRequestCacheTest `
  --tests com.riiablo.engine.server.trade.TradeManagerTest `
  :core:m8ReplayRegression `
  :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  --tests com.riiablo.server.d2gs.NativeSeamNetworkVisibilityTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew :server:d2gs:compileJava --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：技能点、属性点、Party、Trade 领域回归，M8 replay、D2GS 装配/网络可见性测试和
D2GS 编译全部通过；本批未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`，未修改暗黑规则。

下一迁移：把 NPC/任务/物品/金币/生命周期请求按事务复杂度分组接入同一 ingress，并在其后
接入真实 ECS checkpoint restore、RNG/D2S 和跨进程重连网络门禁；不把本批写成 M8-D 完成。

### M8-D 第 3 批：NPC、Quest 与 PlayerLifecycle handler ingress（2026-10-25）

本批把 NPC 服务、任务交互和玩家死亡/复活请求接入同一 D2GS fixed-tick ingress；不改
quest/NPC 规则、重生条件或现有协议响应。

实现与边界：

- `NpcServiceRequest`、`QuestRequest` 和 `PlayerLifecycleRequest` 从 FlatBuffer `process`
  分支直接执行改为先进入 `D2gsCommandAdapter`，再在当前 simulation Tick 消费。
- NPC/Quest 的 request cache 仍由原 handler 负责响应重放、库存/任务快照发布和
  `REQUEST_ID_REUSED`；PlayerLifecycle 的死亡、重生、位置和 ACK 逻辑保持原路径。
- NPC/Quest 已知目标经过 adapter target gate；非法/未知目标仍交给原领域校验，保留
  原协议错误结果。物品/金币及旧 cursor/item handler 仍未迁移，M8-D 继续进行中。

验证：

```powershell
./gradlew :core:test `
  --tests com.riiablo.engine.server.npc.NpcServiceRequestCacheTest `
  --tests com.riiablo.engine.server.npc.NpcServiceProtocolTest `
  --tests com.riiablo.engine.server.quest.QuestRequestCacheTest `
  --tests com.riiablo.engine.server.PlayerLifecycleProtocolTest `
  --tests com.riiablo.engine.client.ClientPlayerRespawnResultTest `
  --tests com.riiablo.engine.client.ClientLifecycleReceiverTest `
  :core:m8ReplayRegression `
  :server:d2gs:test --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew :server:d2gs:compileJava --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：NPC/Quest/Lifecycle 定向回归、M8 replay、D2GS 实体生命周期测试和生产编译全部通过；
本批未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`，未修改暗黑规则。

下一迁移：收口 ItemMove/Gold 事务 ingress，再进行全量 D2GS handler 唯一入口审计，并把
真实 ECS checkpoint restore、RNG/D2S 和跨进程重连接入网络验收；不把本批写成 M8-D 完成。

### M8-D 第 4 批：ItemMove/Gold 事务 handler ingress（2026-10-25）

本批把统一物品事务和金币请求接入 `D2gsCommandAdapter` fixed-tick ingress；保留库存
revision、地面物品所有权、原子事务、金币掉落/拾取以及既有响应行为。

实现与边界：

- `ItemMoveRequest` 和 `GoldRequest` 从 `process` 分支直接执行改为进入统一 adapter，
  再在当前 simulation Tick 调用原有 `AuthoritativeItemMoveService`/金币逻辑。
- ItemMove 的协议 request cache 仍在原 handler 中处理精确重放和 `REQUEST_ID_REUSED`；
  adapter 使用内部 ingress 序列，避免在入口层吞掉重复协议请求。
- 地面拾取的已知实体目标经过 adapter target gate；距离、revision、party gold share、
  ground ownership 和事务结果仍由原领域服务校验。旧 cursor/item 包仍是兼容路径，尚未纳入
  M8-D 唯一 ingress。

验证：

```powershell
./gradlew :core:test `
  --tests com.riiablo.engine.server.item.ItemMoveRequestCacheTest `
  --tests com.riiablo.engine.server.item.AuthoritativeItemMoveServiceTest `
  --tests com.riiablo.engine.server.item.GoldPickupServiceTest `
  --tests com.riiablo.engine.server.item.GroundDropPositionTest `
  --tests com.riiablo.engine.server.item.GroundDropOwnershipMetadataTest `
  --tests com.riiablo.engine.client.ClientItemManagerGoldPickupTest `
  --tests com.riiablo.engine.client.AuthoritativeInventoryStateTest `
  :core:m8ReplayRegression `
  :server:d2gs:test --tests com.riiablo.server.d2gs.InventorySnapshotTransportTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew :server:d2gs:compileJava --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：物品事务/金币/库存快照定向回归、M8 replay、D2GS 库存传输测试和生产编译全部通过；
本批未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`，未修改暗黑规则。

下一迁移：进行 D2GS authoritative handler 全量唯一入口审计，处理 SnapshotResync/EntitySync
及旧 cursor/item 兼容边界，再接入真实 ECS checkpoint restore、RNG/D2S 和跨进程重连验收；
不把本批写成 M8-D 完成。

### M8-D 第 5 批：SnapshotResync/EntitySync ingress 与全量 handler 审计（2026-10-25）

本批完成当前 D2GS `process(Packet)` 的 authoritative 入站审计，并把快照重同步与兼容
EntitySync 输入纳入统一 fixed-tick ingress；出站 EntitySync 仍由 `NetworkSynchronizer`
和 `SerializationManager` 负责，不改变网络副本生成链。

实现与边界：

- `SnapshotResyncRequest`、`EntitySync` 进入 `D2gsCommandAdapter` 后，在固定 Tick 调用
  原有 baseline/legacy movement validator；EntitySync 目标必须与认证 actor 一致，生产环境
  仍由 `ALLOW_LEGACY_ENTITY_SYNC` 明确控制绝对位置兼容桥。
- `process` 中除 Connection/Ping、固定 Tick adapter ingress 和显式 legacy cursor/item
  拒绝外，不再直接调用 authoritative mutation handler。旧 cursor/item 包继续记录并拒绝，
  不形成第二套权威库存路径。
- Snapshot baseline 的重复 request、双客户端隔离、EntitySync tick/ACK 和生命周期语义
  保持不变；本批没有修改 FlatBuffer 字段或客户端快照格式。

验证：

```powershell
./gradlew :core:test `
  --tests com.riiablo.engine.client.SnapshotResyncProtocolTest `
  --tests com.riiablo.net.packet.d2gs.EntitySyncClockTest `
  --tests com.riiablo.engine.client.ClientNetworkReceiverTickBoundaryTest `
  --tests com.riiablo.engine.client.ClientLifecycleReceiverTest `
  :core:m8ReplayRegression `
  :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  --tests com.riiablo.server.d2gs.InventorySnapshotTransportTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew :server:d2gs:compileJava --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：SnapshotResync/EntitySync/客户端生命周期定向回归、M8 replay、D2GS 装配/库存/实体
生命周期测试和生产编译全部通过；本批未使用 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F`，
未修改暗黑规则。

下一迁移：进入真实 ECS checkpoint restore、RNG/D2S 与跨进程重连网络验收；若发现旧 transport
仍有生产入口，再按完整 handler 组收敛，不把本批写成 M8-A/M8-D 完成。

### M8-A 第 9 批：真实 ECS checkpoint continuation gate（2026-10-25）

本批把“恢复后继续运行”从摘要声明落实为固定 Tick 门禁。`AuthoritativeSimulation` 保留默认
单调时钟保护，同时提供带显式 `allowTimeRewind` 的 replay restore 入口；只有 checkpoint
适配器在同一 owner thread 完成 ECS payload 恢复后，才能用该入口把时钟回到归档 Tick。
D2GS 的 `restoreReplayCheckpoint` 现在在 ECS 恢复成功后使用这一显式 replay 语义，随后下一
个固定 Tick 从检查点状态继续执行。

新增定向测试覆盖：默认恢复拒绝倒退、显式 replay restore 允许倒退、真实序列化 ECS 状态回滚，
以及恢复后的 N+1 Tick 状态/摘要继续推进。该批没有把 RNG stream registry、D2S 角色状态、跨
进程 native factory 重建或 session rebind 误称为完成。

验证：

```powershell
./gradlew.bat :core:test `
  --tests com.riiablo.engine.server.AuthoritativeSimulationTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：默认单调恢复、显式 replay 回滚和 ECS checkpoint N+1 Tick continuation 定向门禁通过；
M8-A 仍未完成完整跨进程恢复，M8-D 仍未完成所有旧 transport owner 收敛。

下一迁移：接入 checkpoint 的显式 RNG registry restore，并把 D2S/角色状态与 ECS continuation
绑定到同一恢复事务；之后再做真实跨进程 session rebind/reconnect gate。

### M8-A 第 10 批：NativeRng checkpoint registry restore（2026-10-25）

本批新增 `CheckpointRngRegistry`，把 checkpoint 中的 `NativeRng` 列表从诊断摘要提升为可
恢复的权威 registry：按 `(stream, contextId)` 稳定排序和去重，恢复前先完整校验，缺失 stream
可直接由捕获的 state/drawCount 在新进程重建，检查点之外的注册 stream 会被清除。恢复前的
异常不会改变现有 registry；`SimulationCheckpoint.RngState` 同时补齐值对象语义，便于跨进程
恢复验收比较。

D2GS 增加可选 `replayRngRegistry`。安装后，checkpoint capture 自动带出 registry 状态，
restore 会在 ECS 变更前校验 RNG payload，并在 ECS 恢复成功后应用 registry；没有安装 registry
或 checkpoint 没有 RNG 状态时保持旧行为。该批明确只覆盖一字 `NativeRng` 兼容流，未把
`NativeUnitRandom` 的 D2Common 双字 `pSeed`、D2S/角色状态、跨进程 session rebind 伪装成完成。

验证：

```powershell
./gradlew.bat :core:test `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:compileJava :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：registry 捕获/原地恢复、缺失 stream 重建、重复身份拒绝和空 checkpoint 清理测试通过；
D2GS checkpoint 编译与既有 ECS 恢复回归通过。M8-A 仍待双字 native-unit RNG、D2S/角色事务和
真实跨进程重连 gate。

下一迁移：为 `NativeUnitRandom` 建立带 low/high seed 的 native-unit registry，并将其与 D2S
角色状态纳入同一 checkpoint transaction；完成后再验收 session authority rebind。

### M8-A 第 11 批：NativeUnitRandom 双字 seed registry（2026-10-25）

本批新增 `NativeUnitRandomRegistry` 与 `SimulationCheckpoint.NativeUnitRngState`，按
`(stream, contextId)` 保存 D2Common `pSeed` 的完整 low/high 两字；恢复时不会把 ECS 组件上的
低字 `rngState` 当作完整随机状态。由于 native unit stream 必须绑定实际 Monster/Missile/
UnitRandomSeed 与 `World`，跨进程恢复要求调用方提供显式 factory，不能凭 game seed 盲目重建。

D2GS checkpoint 摘要现在可同时携带兼容 `NativeRng` 和双字 native-unit RNG；安装可选
`replayNativeUnitRngRegistry` 后，capture 会记录双字状态，restore 会在 ECS 变更前校验并在
ECS 成功后恢复 registry。`SimulationCheckpoint.diff` 对 native stream 变化报告
`native-rng:<stream>#<context>`，便于继续运行门禁定位。

验证：

```powershell
./gradlew.bat :core:test `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:compileJava :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：双字 low/high 捕获、原地恢复、显式 factory 缺失 stream 重建、无 factory 拒绝和摘要
差异定位测试通过；D2GS checkpoint 编译与 ECS 回归通过。D2S/角色状态尚未进入同一恢复事务，
session authority rebind 和真实跨进程重连仍未完成。

下一迁移：建立角色 D2S checkpoint adapter（序列化字节、校验和、恢复回滚）并与 ECS/RNG
恢复组成单一事务；随后接入 session rebind/reconnect gate。

### M8-A 第 12 批：D2S 角色 checkpoint adapter 与回滚边界（2026-10-25）

本批新增 `D2SCheckpoint` / `D2SCheckpointRegistry`。角色状态以严格读取器验证过的 1.10f
D2S 完整字节保存，记录实体 id、difficulty、原生 checksum 和 SHA-256；恢复前先解码全部
角色，确认缺失实体有显式 factory，再一次性替换 `Player.data` 绑定。任一绑定回调失败时，
已替换角色会回滚，registry 不提交半成品。

D2GS checkpoint 现在可选携带 D2S 角色快照；安装 `replayD2sRegistry` 后，capture 自动收集，
restore 在 ECS/RNG 变更前验证 D2S，并在 ECS 成功后应用角色绑定。D2S 重编码可能产生新的
保存时间/校验和，因此恢复门禁以原始快照字节的校验和/摘要和恢复后的角色语义为准，不把
“重新写出字节完全相同”误作 1.10f 要求。

验证：

```powershell
./gradlew.bat :core:test `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:compileJava :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：D2S 字节校验、缺失角色 factory、角色恢复和绑定失败回滚测试通过；D2GS checkpoint
编译与 ECS/RNG 回归通过。session authority rebind、真实跨进程 D2S + ECS 同时恢复仍待下一批。

下一迁移：把 session identity/authority rebind 纳入 checkpoint transaction，并运行真实双进程
断线恢复 gate；完成后再评估 M8-A 收尾条件。

### M8-A 第 13 批：session authority checkpoint 与 generation rebind（2026-10-25）

本批把 session identity 纳入 D2GS checkpoint 边界，仍不宣称 M8-A/M8 整体完成。

- `AuthoritativeSessionRegistry` 新增不可变 authority `Snapshot`、原子 restore、稳定角色
  `rebind` 和单调 generation；旧 active connection 在接管后转为 `DISCONNECTED`，迟到命令
  不能继续使用旧 connection，带旧 generation 的命令显式拒绝。
- `D2gsCommandAdapter` 增加 generation-aware submit/rebind；保留现有无 generation 调用以兼容
  旧 transport，新的 reconnect transport 可用 generation 做 epoch 隔离。
- `D2gsCheckpointSnapshot` 可携带 session authority canonical 摘要；D2GS capture/restore
  将 ECS、RNG、D2S 和 authority snapshot 纳入同一恢复门禁。恢复后的 active stable key 会进入
  pending rebind 集合，新的连接可在 generation 校验后接管旧 actor 身份。
- 增加 registry、旧 connection 拒绝、新 connection 接管、generation 迟到包拒绝和 checkpoint
  authority round-trip 测试。真实双进程 socket gate 仍待下一批运行环境验收，D2S/ECS/RNG 的
  全量跨系统回滚事务也仍未完成。

验证：

```powershell
./gradlew.bat :core:test --tests com.riiablo.engine.D2gsCommandAdapterTest `
  :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：定向 core/D2GS 测试和编译通过；M8-A 仍待真实双进程 reconnect gate，以及 ECS/RNG/D2S/
session authority 失败时的完整跨系统回滚验证。

下一迁移：在可用的 headless 双进程环境运行跨进程 checkpoint restore/reconnect gate；随后
补齐失败回滚事务并复核旧 transport 的 generation 注入，之后再评估 M8-A 收尾条件。

### M8-A 第 14 批：checkpoint 事务回滚与旧 transport 代际隔离（2026-10-25）

本批继续收口恢复安全边界，仍不宣称 M8-A/M8 整体完成。

- D2GS checkpoint restore 在 ECS 成功后才应用 RNG、D2S、session authority；任一后续阶段
  抛错会用恢复前的 ECS/RNG/native-unit RNG/D2S/session 快照回滚，并恢复 fixed-tick 时钟和
  上一次 replay checkpoint 引用。
- `Packet`/D2GS `Client` 增加 socket transport token。入站包和断开回调都校验当前连接代际，
  旧 socket 的迟到包不能操作新复用的 connection slot，也不能误删新连接。
- 保留上一批 generation-aware command admission；transport token 解决连接槽位复用，session
  generation 解决 stable identity 接管，两层都不改变现有 D2GS 协议字段。

验证：

```powershell
./gradlew.bat :core:test --tests com.riiablo.engine.D2gsCommandAdapterTest `
  :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：core/D2GS 定向测试与编译通过。真实双进程 checkpoint 恢复、D2S factory 失败注入和
旧 transport 延迟包的 socket 级门禁仍需在 headless 运行环境中验收；因此 M8-A 继续进行中。

下一迁移：运行真实双进程 reconnect gate，确认旧 socket、旧 generation、旧 actor 三者均被
拒绝且新连接可继续发命令；通过后再评估 M8-A 的最终收尾与剩余回滚故障注入。

### M8-A 第 15 批：真实双客户端 reconnect gate 复跑（2026-10-25）

本批运行现有 1.10f 资源下的真实 TCP 双客户端重连门禁，结果分开记录，不把局部通过扩大
为 M8-A 完成声明。

- `:server:d2gs:headlessReconnectVisibility` 通过：旧玩家/普通召唤物删除，掉落和对象保持，
  同一角色新连接完成 baseline，输出 `reconnect_visibility_disconnect_pass` 与
  `reconnect_visibility_pass`。
- `:server:d2gs:headlessReconnectGroundLoot` 连续两次在第一次部分金币拾取响应处超时
  （`partial gold pickup mismatch: result=timeout`），因此当前不能确认重连后旧 actor/new
  actor 的掉落命令继续执行；这不是通过证据，需单独收口 item-move/reconnect gate。
- 本批没有把失败门禁改成宽松超时，也没有宣称真实跨进程 checkpoint restore 已完成；当前
  已验证的是同一 D2GS 进程内的真实 TCP disconnect/reconnect 生命周期。

验证命令：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II'
./gradlew.bat :server:d2gs:headlessReconnectVisibility --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:headlessReconnectGroundLoot --no-daemon --max-workers=1 --console=plain
```

下一迁移：定位 `ItemMoveRequest` 在真实 reconnect fixture 中未返回结果的路径，先完成
部分金币拾取/旧 owner window 的命令 gate，再继续旧 socket/generation 延迟包和跨进程
checkpoint 恢复验收。

### M8-A 第 16 批：固定 Tick handler 入站收口与物品重连门禁（2026-10-25）

本批修复真实门禁中 `ItemMoveRequest` 无响应的根因，并完成统一物品协议与延迟删除的
回归收口；仍不把 M8-A/M8 整体标记为完成。

- 修正 `D2GS.drainD2gsIngress` 的 payload 分支：此前先要求所有 payload 是
  `IngressCommand`，实际 `HandlerIngress` 会在固定 Tick 排空时直接返回，导致
  `item_move` 等 D2GS handler 被静默丢弃。现在 movement/combat 与 handler 两类命令都在
  同一固定 Tick 队列中正确排空。
- `:server:d2gs:headlessReconnectGroundLoot` 通过：部分金币 `20 -> 5`、玩家入账 `15`，
  竞争客户端收到完整 ground correction，断线后旧 owner window 和新连接 baseline 保持。
- `:server:d2gs:headlessItemFailureCorrections` 通过：成功拾取快照、重复请求重放、距离、
  背包占满、死亡三类失败均返回完整 ground item 数据/owner/坐标，且成功物品完成 D2S
  round-trip。
- `:server:d2gs:headlessDelayedDeleteFrames` 通过：延迟三 Tick 的旧删除帧被忽略，随后
  重连金币门禁仍通过。
- headless correction fixture 保留生产自动装备规则；为避免 `cap` 触发原版自动装备而
  误测“背包存放”断言，测试夹具只清除该夹具物品的可选装备类型元数据，不改变生产逻辑。

验证命令：

```powershell
./gradlew.bat :server:d2gs:headlessReconnectGroundLoot --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:headlessItemFailureCorrections --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:headlessDelayedDeleteFrames --no-daemon --max-workers=1 --console=plain
git diff --check
```

下一迁移：继续真实旧 socket/generation 延迟命令拒绝与跨进程 checkpoint restore 验收，
并在这些边界通过后再评估 M8-A 收尾条件。

### M8-A 第 17 批：旧 socket transport token 延迟帧门禁（2026-10-25）

本批完成旧 socket 连接槽位复用下的真实延迟入站帧验收，仍不把 M8-A/M8 整体标记为完成。

- 新增 headless-only 入站延迟故障注入：`EntitySync` 帧可延迟固定 Tick 后再回到统一
  `packets` 队列；默认属性关闭，不改变生产传输路径。
- 新增 `:server:d2gs:headlessStaleTransport`，旧连接发送绝对位置帧后立即断开，同一角色
  重连复用连接槽位；延迟帧释放时必须因旧 `transportToken` 被丢弃，新 actor 的 baseline
  位置保持不变，输出 `stale_transport_pass ... delayedFrameDropped=true slotReuse=true`。
- 该 gate 覆盖 socket transport token 层；session generation/跨进程 checkpoint 的真实
  重绑定与恢复仍需单独验收。

验证命令：

```powershell
./gradlew.bat :server:d2gs:headlessStaleTransport --no-daemon --max-workers=1 --console=plain
git diff --check
```

下一迁移：把同一延迟帧 gate 扩展到带 generation 的命令 admission，并继续跨进程 ECS/RNG/D2S
checkpoint restore 验收。

### M8-A 第 18 批：真实 generation-aware command admission（2026-10-25）

本批把旧 socket 的 transport token 门禁继续收口到固定 Tick 命令边界，仍不把 M8-A/M8
整体标记为完成。

- D2GS 的移动、技能和通用 handler 入站提交统一读取当前
  `AuthoritativeSessionRegistry.activeConnection(connectionId).generation`，并通过
  `D2gsCommandAdapter.submit(connectionId, generation, ...)` 做提交时 epoch 校验。
- `IngressCommand`/`HandlerIngress` 保留提交时 generation；固定 Tick 消费前再次校验
  connection、actor 和 generation，覆盖提交与消费之间发生断线重连的竞态，旧代命令不会
  进入 ECS handler 或 movement/combat scheduler。
- 扩展 `headlessStaleTransport`：旧连接的延迟帧先验证不会移动新 actor，再由新连接发送
  `SnapshotResyncRequest` 并要求 BEGIN/END 基线成功；日志输出
  `delayedFrameDropped=true slotReuse=true generationAwareCommand=true`。

验证命令：

```powershell
./gradlew.bat :core:test --tests com.riiablo.engine.D2gsCommandAdapterTest `
  :server:d2gs:compileJava --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:headlessStaleTransport --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：core generation/rebind 测试与 D2GS 编译通过；真实 stale transport gate 通过，旧
transport 帧被丢弃，重连后的当前 generation 命令完成 snapshot baseline。M8-A 仍待跨进程
ECS checkpoint restore，以及 RNG、D2S 和 session authority 的组合恢复验收。

### M8-A 第 19 批：ECS checkpoint process-independent envelope（2026-10-25）

本批补齐跨进程 ECS payload 的可传输格式，但只覆盖 ECS 子集，不把可选 RNG、D2S 或
session authority 元数据伪装成已完成。

- `D2gsCheckpointSnapshot` 新增版本化 `toEcsBytes/fromEcsBytes` envelope，包含 tick、
  按 entity id 排序的完整 `EntitySync` payload、长度边界和 world digest；解码拒绝错误
  magic/version、乱序/重复实体、截断、尾随数据和 digest 篡改。
- ECS-only 编解码若发现 checkpoint 携带 RNG、D2S 或 session metadata 会显式失败，避免
  跨进程传输时静默丢失后续恢复所需状态。
- `D2gsCheckpointSnapshotTest` 增加独立 Artemis World 恢复、摘要一致性和篡改/尾随字节
  拒绝门禁，验证恢复不依赖原进程中的 snapshot 对象引用。

验证命令：

```powershell
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：D2GS checkpoint 测试通过。当前 envelope 已能跨进程传输 ECS 世界状态；下一批继续
把 RNG、D2S 和 session authority 纳入同一版本化 envelope，并做真实进程重启后的组合恢复。

### M8-A 第 20 批：完整 checkpoint metadata envelope（2026-10-25）

本批把 ECS-only envelope 扩展为可携带完整恢复元数据的版本化 checkpoint，仍不把真实
进程重启网络 gate 宣称为完成。

- `D2gsCheckpointSnapshot.toBytes/fromBytes` 现在同时编码 ECS payload、兼容
  `NativeRng`、双字 native-unit RNG、D2S 角色条目和 session authority；D2S 条目通过
  严格 `D2SReader` 重新验证，session records 通过 registry snapshot 校验。
- envelope 保存完整 summary canonical 与 D2S canonical 作为完整性见证；任何 metadata
  丢失、篡改、乱序、截断、错误版本或尾随字节都会拒绝恢复。
- 新增跨 World 的完整 metadata round-trip 测试，确认 RNG 列表和 session generation
  在解码后保持一致；ECS-only API 对携带可选 metadata 的 checkpoint 显式失败，避免
  静默降级。

验证命令：

```powershell
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：D2GS checkpoint 测试通过。下一批运行真实进程重启/接管 gate，验证完整 envelope
进入新 D2GS 实例后，ECS、RNG、D2S 和 session generation 能共同恢复并继续处理命令。

### M8-A 第 21 批：落盘交接、跨 JVM 继续仿真与接管安全边界（2026-10-25）

本批把完整 checkpoint envelope 接入可落盘的进程交接边界，仍不把“新进程已接管并
继续处理真实 socket 命令”提前宣称完成。

- 新增 `D2gsCheckpointStore`，将完整 `D2gsCheckpointSnapshot.toBytes()` 写入同目录临时
  文件，刷新文件内容后再原子替换目标；写入失败会清理临时文件，避免留下半个 checkpoint。
  不支持原子替换的文件系统直接失败，不降级为可能破坏旧 checkpoint 的非原子覆盖；该承诺
  是文件内容刷新和进程间原子发布，不等同于断电情况下的目录项持久性保证。
- `D2GS` 增加 `writeReplayCheckpoint(Path)` 与 `restoreReplayCheckpoint(Path)`，生产恢复
  入口不再需要调用方自行拼装 envelope；读取仍统一经过版本、长度、摘要、D2S 和 session
  canonical 校验。
- 完整 envelope 升到 v2，以 `-1` 表示缺省、`0` 表示存在但为空的 D2S/session registry，
  修复空 registry 编解码失败；保留 v1 读取兼容性。恢复时缺少非空 RNG/native RNG 或显式
  D2S payload 的适配器会在 ECS 变更前拒绝，空 RNG 列表则清除归档中不存在的 stream。
- 接管支持同 stable key 复用原 connection slot，D2GS 登录回退同时处理归档连接槽占用；
  authority snapshot 允许旧的 disconnected generation 历史，并始终选择最高 generation，
  但拒绝多 active owner、重复 generation 或旧 generation 仍 active 的无效记录。
- 新增独立 JVM 组合门禁：真实 `SerializationManager`、最小 ECS 与 `D2GS` 恢复事务在另一
  Java 进程重建；恢复后摘要一致、旧 generation 命令拒绝，新 generation 命令在 N+1 Tick
  消耗恢复的两类 RNG 并改变 Position，最终摘要与本 JVM 同路径结果一致。
- 同批覆盖损坏文件拒绝、目标替换/失败临时文件清理、缺少适配器的前置拒绝、空 stream 清理、
  接管后再次 snapshot/restore，以及 native factory 晚期失败后的 ECS/RNG/session/clock 回滚。

验证：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsCheckpointStoreTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  :core:test `
  --tests com.riiablo.engine.D2gsCommandAdapterTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：服务端 26 项、core 17 项，共 43 项测试通过，0 失败、0 跳过；生产编译、模块边界与
`git diff --check` 通过。已验证真实独立 JVM 边界上的恢复/继续仿真摘要一致性及损坏拒绝。
新增跨 JVM fixture 不依赖 MPQ；同轮真实 factory/D2S 回归使用上述 1.10f 安装资源。本批
不改暗黑规则算法，RNG 仅验证既有实现的状态恢复和继续抽样。最小 ECS fixture 不等同于
生产地图/完整 native factory 重建；D2S 在该 JVM 组合门禁中是空 registry，非空角色恢复
仍由独立 D2S 回归验证。因此 M8-A、M8-D 和 M8 总体继续标记为进行中。

剩余问题/下一迁移：生产 D2GS 仍需自动注册真实 RNG 调用方与玩家 D2S bindings；恢复登录
必须复用归档 actor/角色状态，而不能由客户端旧 D2S 新建另一个玩家。补齐这些接管适配后，
在 1.10f headless 环境启动旧实例写出 checkpoint、关闭并启动新实例，经真实 socket 完成
generation rebind 和后续固定 Tick 命令；再核对完整地图/实体、非空 D2S、RNG 继续执行证据。

### M8-A 第 22 批：生产绑定、归档 actor 接管与真实角色跨 JVM 恢复（2026-10-25）

本批集中补齐上一批的生产接管适配：真实玩家 D2S/native unit RNG 自动绑定、归档 actor
复用和含非空角色存档的独立 JVM 继续运行门禁。M8-A、M8-D 和 M8 总体仍保持进行中。

- 新增 `D2gsWorldCheckpointBindings` 并在生产 D2GS World 装配后安装。捕获时自动扫描
  networked 玩家，注销已删除角色的 D2S binding；恢复时同时更新 `Player.data` 与
  `AttributesWrapper.attrs`，保留 ECS 中的 progression/equipment revision 和实时 Vitals。
  显式安装的 host registry 仍可覆盖自动 binding。
- 自动捕获 Monster/Missile 与已初始化的其他 UnitRandomSeed 的 D2Common 双字 seed；
  每次捕获重建 live wrapper，避免组件/id 回收后读到旧 seed。恢复在实体创建后绑定新组件，
  清理归档之外的 seed，并拒绝非 unit stream 或不属于归档实体的 RNG 引用。
- checkpoint 待接管角色的 Connection 复用归档 actor 和已恢复 CharData，不由客户端旧 D2S
  创建另一角色，不重复生成佣兵；向观察者发布归档角色的 COF 外观。重复登录、错误角色
  class/identity 和重复接管拒绝。同槽接管仍使用服务端归档 generation，不信任客户端版本。
- 修复 pinned Artemis 2.3 `EntityManager.isActive(id)` 对从未分配 ID 返回真的问题；
  checkpoint/lifecycle guard 同时检查实际 Entity 是否存在，使真空 World 能创建归档实体。
  晚期 RNG/D2S 失败回滚还显式删除尚未进入 subscription 的新实体，避免下一 Tick 出现幽灵角色。
- 独立 JVM 门禁加载真实 1.10f MPQ，使用真实 ServerEntityFactory、SerializationManager 和
  D2GS 恢复事务，在空 World 恢复完整 Amazon D2S（含初始装备、金币和传送点），核对存档
  字节/ECS 摘要、属性指针和 runtime revision；复用 actor 接管后核对 native RNG 下一次抽样、
  N+1 Tick 位移以及与本 JVM 同路径的继续运行摘要。
- 同批覆盖同 World 再次恢复、角色删除/id 回收、Monster/Missile 双字 RNG 继续抽样、
  真实 D2S identity 晚期失败在非空/空 World 的完整回滚，以及从未分配 ID 的无资源回归。

验证：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointStoreTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  :core:test `
  --tests com.riiablo.engine.D2gsCommandAdapterTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:headlessSimulationTick `
  :server:d2gs:headlessReconnectGroundLoot :server:d2gs:headlessStaleTransport `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：服务端 32 项、core 18 项，共 50 项测试通过，0 失败、0 跳过；生产编译、模块边界和
`git diff --check` 通过。真实 TCP gate 观察到固定 40ms 步进（tick 6→37）、20 金币堆
部分拾取 5 后剩余 15 在重连 baseline 保持一致且 owner window 不丢失，以及同槽重用后的旧 socket 帧被拒绝、
当前 generation 命令正常执行。独立 JVM 角色恢复是上述定向测试的一部分，与这三个同进程
TCP gate 分别验收，不合并声称已通过真实 socket 的跨进程重启。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。本批不修改暗黑公式，native RNG
沿用现有 D2MOO D2Common Seed 实现，只绑定/恢复原有双字状态；角色夹具通过正式 D2S
读写入口，保留既有初始装备和传送点校验，不放宽字节或摘要断言。

剩余问题/下一迁移：新 JVM 门禁使用最小 Map，不含真实 socket 或完整生产 World 系统，
不能替代“旧实例落盘退出→新实例恢复→真实 TCP 接管/后续命令”的最终 gate。兼容 NativeRng/
全局随机调用方、角色药剂等未进 D2S 的隐藏运行时状态、完整地图/系统级状态仍需审计和迁移。
多玩家归档连接槽交换当前按 authority 冲突拒绝，不承诺任意登录顺序；本批沿用原有
Connection name/class 身份模型，不是新增密码认证系统。后续将完整地图/native factory、
系统状态和真实 socket 重启接管纳入同一组合验收，再决定 M8-A 收口。

### M8-A 第 23 批：世界兼容锁、药剂运行时与跨 JVM 连续恢复（2026-10-25）

完成日期：2026-10-25。本批合并处理真实重启前的两个状态缺口：World 的 seed/难度身份，
以及 D2S 不保存的角色药剂 RNG、生命/法力持续恢复速率和剩余帧；不改原有药剂规则公式。

- `CharData.PotionCheckpoint` 是不可变、校验后的运行时快照，捕获/恢复不抽取随机数、
  不推进任何帧。`D2SCheckpoint.Entry` 单独携带该元数据，不向原版 D2S 添加字段或字节。
  D2S registry 在 factory/Binding.set 之前将运行时状态装入解码后的 CharData，晚期失败
  回滚同时恢复旧角色的剩余恢复进度和随机状态。
- 完整 checkpoint envelope 升到 v3，增加每个 D2S 条目的可选 PotionCheckpoint 以及可选
  WorldIdentity；保留 v1/v2（包括非空真实 D2S）的读取。旧归档的缺省元数据保持缺省，
  不能把不存在的药剂恢复进度当作已保存。新增字段的 presence、数值范围与 canonical
  witness 均受校验，篡改或截断拒绝。
- 生产 D2GS 自动记录 seed/难度，并在刷新 binding、创建实体或变更时钟之前拒绝不匹配的
  归档。WorldIdentity 和药剂运行时进入 replay summary 的 world digest，即使 ECS/D2S
  字节未改变，也能发现药剂随机状态或剩余恢复帧差异；ECS-only 导出不允许丢弃世界身份。
- 真实 1.10f 门禁让原 World 饮用生命/法力药剂后运行 31 个固定 Tick，再落盘交接；以原
  World 不间断运行作为基准，比较新 World 与独立 JVM 恢复后 400 Tick 内多个时点的定点
  生命/法力、恢复进度和 RNG 状态，中途再次饮药并验证最终 ECS/D2S 摘要一致。
  新 World 不额外调用 `World.process()` 激活 subscription，避免在恢复时钟之外消耗一帧。
- 同批覆盖错误 seed/难度的前置拒绝、仅运行时改变时的摘要变化、非法恢复速率/帧数、
  元数据篡改，以及带活跃药剂进度的非空/空 World D2S identity 失败回滚。

验证：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointStoreTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  :core:test `
  --tests com.riiablo.engine.D2gsCommandAdapterTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --tests com.riiablo.engine.server.PotionRecoverySystemTest `
  --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:headlessSimulationTick `
  :server:d2gs:headlessReconnectGroundLoot :server:d2gs:headlessStaleTransport `
  :core:test --tests com.riiablo.save.D2SWriter96HeaderTest `
  --tests com.riiablo.save.D2SWriter96StatTest `
  --tests com.riiablo.save.ItemDataResourceRefreshTest `
  --tests com.riiablo.save.CharDataResetTest `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

第一组结果：服务端 38 项、core 25 项，共 63 项通过，0 失败、0 跳过。
第二组额外 D2S header/stat、资源刷新与 CharData reset 回归 18 项通过；合计 81 项，
0 失败、0 跳过。生产编译、模块边界与 `git diff --check` 通过。真实 TCP 三个 gate 均通过：
固定步进 tick 5→36、部分拾取后的 15 金币/owner window 在重连后保持一致、同槽旧 socket
延迟帧被丢弃且当前 generation 命令成功。它们仍是同进程网络回归，不等于跨进程 socket 接管。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。D2MOO/原版依据：沿用既有
HEALTHPOT/MANAPOT、PotionRecoverySystem 和 CharData 药剂计算实现；本批只保存并恢复
其现有字段，不修改持续恢复、叠加或随机加倍公式，也不将兼容药剂 RNG 冒称为原版双字 pSeed。

剩余问题/下一迁移：WorldIdentity 只是 seed/难度兼容锁，不是 MPQ/TXT 内容 digest，不能
证明完整地图或系统运行时已恢复。旧 v1/v2 归档本来就没有这些新字段，仍只保留历史子集恢复
能力。完整地图/native factory（包括稀疏实体 ID）、Map/AI/任务/交易等系统级隐藏状态和
其他随机调用方仍待审计；真实 socket 跨进程重启接管尚未通过组合 gate。
下一批集中处理完整地图/native factory 的实体集和系统恢复前置条件，再接真实 TCP 重启。
M8-A、M8-D 与 M8 总体继续保持进行中。

### M8-A 第 24 批：稀疏 native entity ID、恢复冲突保护与跨 JVM 实体集（2026-10-25）

完成日期：2026-10-25。本批合并处理 native factory 无法命中稀疏 ID 的前置缺口、
无关本地实体/待删除实体的占用冲突，以及真实工厂创建失败与晚期 D2S 失败后的清理。

- 新增 pinned Artemis 2.3 的同包 `CheckpointEntityAllocation`，只调用原 allocator 的
  `create/clean`，不反射或改写私有 free-list，也不调用 `World.process()`。恢复批次内暂存
  跳过的裸 ID，不给它们附加组件或提交 subscription changed；结束时回收未使用的预留。
  一批只扫描一次稀疏间隙，也能从乱序 limbo 中保留并随后使用更高的目标 ID。
  生产分配范围明确限制为 `0..999999`，超界、活实体占用、待删除和已关闭 scope 均拒绝。
- `EntityFactory` 的默认创建仍走 `world.create()`；仅 `ServerEntityFactory` checkpoint
  单实体初始化 scope 使用指定分配器。native 初始化在分配后抛错时，显式删除本次实体，
  不把不可见于 subscription 的半成品留到下一 Tick。普通创建与客户端创建路径不变。
- `EntityLifecycle.beginRestore/endRestore` 覆盖创建、反序列化、删除和内部回滚；
  finally 关闭分配 scope。`D2gsCheckpointSnapshot` 在任何创建/反序列化之前，拒绝
  checkpoint ID 已被非 Networked 本地实体占用、或尚在 Artemis 删除批次内的情况。
  不覆盖本地实体，不通过额外 Tick 清空删除队列，不静默重映射 owner/entity 引用。
- 真实 1.10f 角色与两件带 owner/party 窗口的地面药剂形成 `{0,5,9}` 实体集，落盘后
  分别在新 World 和独立 JVM 恢复，严格比较 ECS/world/D2S canonical，再与原 World
  连续运行三个固定 Tick 的摘要比较。另测乱序回收队列优先返回 9、5 时，仍能先恢复
  角色 0，并保留间隙内活跃本地实体 1 的位置与身份。
- 注入 native player 已初始化后的异常，验证下一正常 flush 后无 ghost、玩家 target
  slot 释放、所有预留/失败 ID 可复用、同一 lifecycle 可重试恢复；晚期错误 D2S identity
  回滚也扩展到稀疏角色/物品实体集，检查完整摘要不变且空 World 不留占位实体。

验证：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointStoreTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  :core:test --tests com.artemis.CheckpointEntityAllocationTest `
  --tests com.riiablo.engine.D2gsCommandAdapterTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --tests com.riiablo.engine.server.PotionRecoverySystemTest `
  --tests com.riiablo.engine.server.NativePlayerTargetSlotsTest `
  --tests com.riiablo.engine.server.NativePathIdentityTest `
  --tests 'com.riiablo.engine.server.ServerEntityFactory*Test' `
  --tests com.riiablo.presentation.ClientEntityFactoryWaypointTest `
  :server:d2gs:headlessSimulationTick :server:d2gs:headlessReconnectGroundLoot `
  :server:d2gs:headlessStaleTransport :desktop:compileJava `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：服务端 42 项、core 59 项，共 101 项通过，0 失败、0 跳过；模块边界、服务端/
core/桌面端编译通过。三个真实 TCP gate 通过：固定 Tick `7→38`（31 步）、部分拾取后
剩余 15 金币和 owner window 在重连后保持、同槽旧 socket 延迟帧丢弃且当前 generation
命令成功。它们仍是同进程网络回归，不等于跨进程 socket 接管。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。本批不改暗黑规则或伤害/RNG
公式；分配适配依据 pinned `artemis-odb:2.3.0` 的 `EntityManager.obtain/free/clean`、
`World.create`、`BatchChangeProcessor` 和 `ComponentManager.synchronize` 源码，
工厂沿用既有 D2MOO 规则实现。

剩余问题/下一迁移：精确恢复已有实体 ID **不代表**恢复原 allocator 的 nextId/limbo 顺序，
本批可能改变下一次普通分配的 ID；原分配器状态仍需归档后才能验收“创建新实体”的完全
确定性续跑。资源门禁使用最小 Map 和真实角色/物品工厂，并非完整地图/所有单位类型覆盖。
审查确认 `MissileP` 只含 identity/owner/range/skill/level/lifetime，未保存施放时的完整
伤害包、附着/其他瞬态字段；按 ID 顺序重建时 owner 也可能尚未恢复，不能把相同摘要当作
投射物结算等价的证明。下一批优先集中补齐投射物运行时与重建依赖、原分配器状态，再推进
Map/AI/任务/交易等系统隐藏状态与真实 socket 跨进程重启的组合验收。
M8-A、M8-D 与 M8 总体继续保持进行中。

### M8-A 第 25 批：投射物运行时、无重掷重建与跨 JVM 命中续跑（2026-10-25）

完成日期：2026-10-25。本批集中补齐上一批发现的 MissileP 子集不足，以及按 ID 重建时
owner 尚未恢复导致的伤害/RNG 依赖；不把这项交付视为完整 World checkpoint 已完成。

- 新增显式版本化 `MissileRuntimeCheckpoint`，保存 Missile 的全部标量、四个路径向量、
  五步加速相位、native pSeed 双字及兼容镜像、施放时 physical/elemental packet、
  damage 的 base/aggregate/remaining 编码三元组与 aggregate revision、next-hit 表、
  已命中集合、跨 projectile 共享 cast 集合和指向另一 projectile.hitTargets 的别名。
  Baal owner lifetime 区分 null/current/stale，恢复后不把旧引用绑定到复用 ID 的新 incarnation。
  行记录和资产句柄由 native factory 重建，不归档宿主对象图，不使用生产反射或 Java 序列化。
- runtime schema 测试逐字段对照 Missile：未来增加字段而未显式决定归档策略将失败。
  解析不依赖 Riiablo.files；拒绝非法 bool/presence、非有限浮点数、重复/乱序集合与 stat、
  超界长度、截断、尾随字节、共享组冲突、缺失 current owner identity 和 ECS/runtime
  identity 不符。damage 若带附加属性列表或 shield/running/difficulty 上下文，capture
  显式失败，不静默丢弃这些未支持的属性上下文。
- 完整 `D2GC` envelope 升为 v4：在 v3 worldIdentity 后、canonical witness 前追加
  `runtimeCount` 和逐条 `entityId/length/runtimeBytes`；每条最多 1 MiB。
  runtime bytes 纳入 world digest，纯网络 EntitySync 字节保持不变；有 runtime 时禁止
  降为仅 ECS 的 envelope。保留 v1/v2/v3 读取；旧归档没有 runtime 时仍只具历史子集能力。
- `ServerEntityFactory.createCheckpointMissile` 仅在 checkpoint allocation scope 内使用，
  只组装结构、行/资产、碰撞与位置组件，跳过 owner 属性/施法模式读取、伤害计算、创建时
  暴击抽取和 slow-missile 重算。全部 ECS payload 应用后统一恢复 runtime 与共享引用；
  投射物 ID 低于 owner ID 也能重建。内部失败与晚期 D2S 失败的事务回滚同样恢复 runtime。
- 首次命中逐帧测试暴露技能/特殊投射物的兼容 `CombatSystem` 流未接 checkpoint，现将其
  接入 D2GS 自动 World RNG binding，保存 state/drawCount，恢复原系统持有的 RNG 对象。
  只为已有序列添加稳定身份，不改 seed 推导、LCG 算法或抽取顺序，不冒称原版 pSeed。
  旧归档省略该流后发生晚期失败，也必须回滚到实际战斗消费者持有的对象，不能恢复脱离
  消费者的 registry 副本。显式 host registry override 仍支持。
- 真实 1.10f 最小 Map + Player 0、Monster owner 5、Fire Bolt 1/2，预跑后落盘，在新
  World 和独立 JVM 恢复；兼容战斗流先消耗七次，确保不是碰巧从相同初始 seed 开始。
  逐帧严格比较后续 35 Tick canonical、全部 native RNG、实际碰撞后的生命值及最终 D2S，
  且必须观察到真实玩家受伤。另用真实工厂创建 ordinary arrow，之后改变 owner 属性，
  验证创建时 critical/elemental packet 原样恢复，下一 Tick 与原 World 摘要一致。

验证命令：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointStoreTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  :core:test --tests com.riiablo.engine.replay.MissileRuntimeCheckpointTest `
  --tests com.artemis.CheckpointEntityAllocationTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --tests com.riiablo.engine.server.NativePathIdentityTest `
  --tests com.riiablo.engine.server.PotionRecoverySystemTest `
  --tests com.riiablo.engine.server.FireBoltGoldenDamageTest `
  --tests com.riiablo.engine.server.FireBallGoldenDamageTest `
  --tests com.riiablo.engine.server.MonsterMissilePhysicalIntegrationTest `
  --tests com.riiablo.engine.server.MonsterMissileElementalIntegrationTest `
  --tests com.riiablo.engine.server.MonsterMissileCreationCriticalIntegrationTest `
  --tests com.riiablo.engine.server.combat.OrdinaryWeaponDamageTest `
  --tests com.d2moo.testutil.NativeMissileAccelerationTest `
  :server:d2gs:headlessSimulationTick :server:d2gs:headlessReconnectGroundLoot `
  :server:d2gs:headlessStaleTransport :desktop:compileJava `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：服务端 46 项、core 112 项，共 158 项通过，0 失败、0 跳过；其中新增 runtime
codec 6 项、真实 World 投射物集成 4 项。模块边界、服务端/core/桌面编译通过；三个
真实 TCP gate 通过：固定 Tick `6→37`（31 步）、重连后剩余 15 金币与 owner window
保持、同槽旧 socket 延迟帧丢弃且当前 generation 命令成功。追加普通 arrow 用例后重跑
上述六组服务端测试；强化逐字段断言后重跑上述十四组 core 测试，计数不重复累加。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。本批不改暗黑规则或伤害公式；
沿用已审计的 MissileDamageResolver / NativeMissilePhysicalDamage / NativeMissileElementalDamage
（D2MOO MISSILE_Get*Damage、MISSMODE_RollDamageValue、SUNITDMG 相关实现）与
NativeMissileAcceleration（PathMisc.cpp sub_6FD5CEB0），只保存既有计算结果和相位。

剩余问题/下一迁移：原 allocator 的 nextId/limbo 顺序仍未归档，本批不保证恢复后新创建
实体得到与连续运行相同的 ID；需要结合删除批次及回滚单独处理。路径控制字段覆盖不等于
完整地图/房间碰撞状态已恢复；共享集合仅覆盖归档网络投射物之间的关系，外部系统持有的
集合引用仍依赖系统级恢复。Map/AI/任务/交易等隐藏运行时和其他随机消费者仍需逐项收口。
真实 socket 的跨进程重启接管仍缺组合 gate；现有网络 gate 仅是同进程回归。
M8-A、M8-D 与 M8 总体继续保持进行中。

### M8-A 第 26 批：原 allocator 队列、高水位与创建实体确定性续跑（2026-10-25）

完成日期：2026-10-25。本批集中处理上一批保留的 `nextId/limbo` 缺口，并将 native
创建失败、删除失败和晚期 D2S 失败后的 allocator 回滚放在同一交付中。

- 新增针对 pinned `artemis-odb:2.3.0` 的 `EntityAllocatorCheckpoint`：显式归档
  nextId 和逻辑 FIFO 顺序的 free-id 数组，active/recycled 集由二者的补集确定。
  最大 nextId 为 1000000；拒绝重复、负数、越界 ID、异常长度、版本、截断及尾随字节。
  capture 校验实际 entity store、recycled 标记、free-list、free-id 组件清理的一致性。
- Artemis 没有公开这三个字段，因此窄适配器只反射访问 EntityManager 的 `nextId`、
  `limbo`、`recycled`，检查声明类型、可见性、final/static 布局，并拒绝替代实现。
  不使用 Unsafe，不替换第三方 EntityManager 类，不遍历或序列化组件对象图。
  扩容仍走原 manager.create，保证已注册的组件/bit-vector store 正常扩容；回退高水位时
  只裁去已清理且没有活实体的尾部缓存。升级 Artemis 时必须重新审计此版本适配器。
  上批 `CheckpointEntityAllocation` 本身仍只走普通分配 API，本批没有改写其行为。
- 完整 `D2GC` envelope 升为 v5：在 v4 missile runtime 段后、canonical witness 前追加
  allocator presence，以及可选的 length/bytes；内部 codec v1 为 version/nextId/count/
  orderedFreeIds。allocator bytes 纳入 world digest；禁止带此元数据的纯 ECS 降级。
  v1/v2/v3/v4 继续可读，不为旧归档虚构原分配顺序；`.withAllocatorState(null)` 明确选择
  历史子集恢复，不提供 allocator 确定性保证。其他 with 方法必须保留 allocator 元数据。
- D2GS 自动 capture allocator，要求完成删除清理且实际 Networked 集与已发布 payload 集
  一致：拒绝 pending deletion 或尚未进入 subscription 的网络实体，不能将它们静默当作
  不需恢复的本地实体。归档应位于正常 Tick/结构提交边界，不通过额外仿真 Tick 补救。
- 精确恢复前要求非 Networked 本地实体的 ID 集已经与归档一致：缺失的地图/宿主实体需由
  对应 owner 先重建，外来本地实体占用 archived free-id 时必须拒绝，不能覆盖或重映射。
  该检查只保证本地 ID 集相同，不证明本地组件/地图内容相同；旧子集恢复仍可保留额外本地实体。
- 带 allocator 的恢复事务先执行 native set reconciliation，关闭 sparse allocation scope，
  再完成 subscription 与 native component purge，最后恢复原 FIFO/recycled/nextId。
  结构 flush 沿用 BatchChangeProcessor.update 和 World.process 尾部的 clean 顺序，只通知
  结构订阅者，不调用任何 system.process，不推进仿真时钟。失败时清除半成品、重建原实体集，
  再恢复原 allocator；D2GS 外层晚期 RNG/D2S/session 回滚同样恢复此状态，包括旧归档失败。
- 真实 1.10f Player 0 + owned ground items 5/9，制造 nextId=18、15 个乱序回收 ID 和
  没有活实体的高位空洞；目标 World 先分配/回收到 30，恢复后与原 World 连续执行 22 次
  真实物品工厂创建，穿插删除并逐步比较 ID、完整 checkpoint canonical 和最终 D2S。
  新 World 和独立 JVM 均严格比对，越过原 free-list 后达到 nextId=24；不只检查恢复瞬间。
  另覆盖队列 head 偏移、全空高位区间、回退到 nextId=0 后再次扩容、匹配/冲突本地实体、
  native 工厂初始化后抛错、删除已生效却返回失败，以及晚期错误 D2S 的完整回滚。

验证命令：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointStoreTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  :core:test --tests com.artemis.EntityAllocatorCheckpointTest `
  --tests com.artemis.CheckpointEntityAllocationTest `
  --tests com.riiablo.engine.replay.MissileRuntimeCheckpointTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --tests com.riiablo.engine.server.NativePathIdentityTest `
  --tests com.riiablo.engine.server.NativePlayerTargetSlotsTest `
  --tests com.riiablo.engine.server.PotionRecoverySystemTest `
  --tests 'com.riiablo.engine.server.ServerEntityFactory*Test' `
  --tests com.riiablo.presentation.ClientEntityFactoryWaypointTest `
  :server:d2gs:headlessSimulationTick :server:d2gs:headlessReconnectGroundLoot `
  :server:d2gs:headlessStaleTransport :desktop:compileJava `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：服务端 51 项、core 63 项，共 114 项通过，0 失败、0 跳过；包含新增 allocator
codec/恢复 5 项、真实 World 3 项、envelope/删除失败回滚 2 项，并强化已有 native 工厂
失败与晚期 D2S 回滚用例。模块边界、服务端/core/桌面编译通过。三个真实 TCP gate
通过：固定 Tick `6→37`（31 步）、部分拾取后剩余 15 金币与 owner window 在重连后保持、
旧 socket 延迟帧丢弃且同槽当前 generation 命令成功；没有将其当作跨进程 socket gate。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。本批不改暗黑规则、伤害/RNG
公式；依据 pinned Artemis 源码的 EntityManager.obtain/free/clean、IntDeque.popFirst、
World.process 尾部、BatchChangeProcessor.update/getPendingPurge 与 ComponentManager.clean。

剩余问题/下一迁移：allocator 和已覆盖角色/物品/投射物的续跑不等于完整 World 恢复。
本地实体的组件内容、Map/RoomEx 碰撞/房间状态、怪物 AI/状态持续时间、任务/交易等系统隐藏
运行时、其他 RNG 消费者和资源内容 digest 仍需逐项收口。结构订阅者的所有系统级副作用也
不能仅靠 allocator 元数据回滚。真实 socket 的跨进程重启接管仍缺组合 gate，现有三个网络
gate 仅为同进程回归。M8-A、M8-D 与 M8 总体继续保持进行中。

### M8-A 第 27 批：单位状态、DOT 与资源折叠上下文恢复（2026-10-25）

完成日期：2026-10-25。本批将普通单位状态的服务端运行时、属性依赖、
新 World/独立 JVM 续跑和失败回滚合并交付；不宣称完整怪物 AI 或完整状态生命周期已迁移。

- 新增显式版本化 `UnitStateRuntimeCheckpoint`，与客户端 `StateP` 分离。
  每个 Networked 实体都有一条记录，包括 UnitStates 不存在和 stateList 为 null 的情况；
  恢复会清除目标中不应存在的状态组件，不保留目标的旧状态，也不将服务端状态变成 snapshotOnly。
  单位 runtime 保存同步/药水标志、已应用的最大资源百分比、原始/上次结算上限和 aggregate revision。
- UnitState 显式保存剩余/初始持续时间、来源/技能、curse 仲裁元数据、永久/stat-list 类别、
  所有修正值、精确 DOT rate、毒伤载荷、周期 delay/countdown、Thunder Storm 前目标、
  地面拖尾坐标、运行值、同步/到期标志、Conversion save-list 标量、mastery 类型，
  以及每条 native stat/layer/operation/encodedValue 和私有 legacy projection baseline。
  保留列表顺序、多来源同状态、多个匿名永久 stat-list，以及独立的 state/GFX flags；
  不使用 addState 刷新合并，不由技能等级重算已施放的效果，不重新抽取 RNG。
- 携带 UnitStates 的单位同时保存 Attributes 的 base/aggregate/remaining 三层 encoded stat、
  type、aggregate revision、盾牌/跑动上下文的 presence/value 和难度抗性标志。
  StaminaMaximum 显式保存原始上限、上次输出、装备/状态的两类百分比、bonus、
  rebuild 中间值及 revision，防止恢复后把已加成的上限当成原始输入再次放大。
  这不是任意 Attributes 对象图序列化；非空额外 source lists 明确拒绝，等待其 owner 适配。
- 完整 D2GC envelope 升为 v6：在 v5 allocator 段后增加可选的 unit-state runtime count
  和逐实体 length/bytes。count=-1 表示历史/明确未覆盖的子集，count=0 表示已覆盖的空实体集。
  覆盖集必须与 ECS payload 完全相同；元数据与覆盖标志纳入 canonical digest，
  禁止纯 ECS 导出静默丢弃。`withUnitStateRuntime(null)` 是显式选择旧子集能力，
  其他 with 方法保留此元数据；v1–v5 按原有能力继续读取，不虚构权威状态运行时。
- runtime 恢复先验证所有 host/字段，再无回调地替换 state-list 和属性。
  ECS 应用/内部回滚均包含此段；D2GS 在 D2S 更换 CharData/Attributes 指针后再次应用，
  晚期 RNG/D2S/session 失败也在 D2S 回滚之后恢复原 runtime。整个过程不运行
  StateUpdater、不触发状态移除/插入效果、不补跑 World.process 或额外仿真 Tick。
- 非 null RemovalCallback、CONVERSION、PREGNANT，以及活动 curse/conversion target
  membership 均 fail closed；不能将闭包静默丢弃或只凭 stateId 假造原生命周期。
  已分离的普通层/无此类依赖的状态可以归档；TARGETS registry/order、
  Conversion/Pregnant 回调重绑定仍需后续与怪物/目标系统共同迁移。
- 新增 UnitState 全字段 schema 审计（反射只用于测试）、私有 projection baseline/holder
  标量/flags/匿名重复层/组件缺失/null-list round-trip、所有截断边界、非法 boolean、
  非有限 rate、未知 hook/活动 target membership 拒绝、后一个 host 无效时前一个不变。
  属性用例验证三层 encoded 值、64-bit revision、combat presence 和 stamina 操作数，
  并覆盖装备百分比与 vitality/状态刷新续算；不通过重建属性来弱化比较。
- 真实 1.10f Player 同时携带精确 poison、Battle Orders 类资源增益、两个来源的 cold 层、
  两个匿名永久 stat-list 和进行中的蓝药。运行 3 Tick 后归档，新 World 与独立 JVM
  再续跑 32 Tick，逐 Tick 严格比较完整 canonical/药水运行时并比较最终 D2S。
  显式断言剩余 5 次毒伤每帧 64 encoded、duration=1 的末次仍结算，
  第 10 Tick 增益到期后最大生命/法力/体力回到原值。晚期错误 D2S 在 live/空 World
  均完整回滚，之后再与未失败基准连续比较 14 Tick。

验证命令：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat :server:d2gs:test `
  --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointStoreTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest `
  --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest `
  --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest `
  --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest `
  :core:test --tests com.riiablo.engine.replay.UnitStateRuntimeCheckpointTest `
  --tests com.riiablo.engine.replay.UnitStateAttributeCheckpointTest `
  --tests com.riiablo.attributes.StaminaMaximumTest `
  --tests com.riiablo.engine.server.state.StateListTest `
  --tests com.riiablo.engine.server.PotionRecoverySystemTest `
  --tests com.riiablo.engine.replay.MissileRuntimeCheckpointTest `
  --tests com.artemis.EntityAllocatorCheckpointTest `
  --tests com.artemis.CheckpointEntityAllocationTest `
  --tests com.riiablo.engine.replay.CheckpointRngRegistryTest `
  --tests com.riiablo.engine.replay.NativeUnitRandomRegistryTest `
  --tests com.riiablo.save.D2SCheckpointRegistryTest `
  --tests com.riiablo.engine.server.NativePlayerTargetSlotsTest `
  --tests 'com.riiablo.engine.server.ServerEntityFactory*Test' `
  :server:d2gs:headlessSimulationTick :server:d2gs:headlessReconnectGroundLoot `
  :server:d2gs:headlessStaleTransport :desktop:compileJava `
  --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：服务端 54 项、core 89 项，共 143 项通过，0 失败、0 跳过；模块边界与桌面编译通过。
三个真实 TCP gate 通过：固定 Tick `7→37`（30 步）、部分拾取后剩余 15 金币和 owner
window 保持、旧 socket 延迟帧丢弃且当前 generation 命令成功。三个网络 gate 仍为同进程，
上述独立 JVM 状态验收不等于 socket 跨进程接管。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。本批不改暗黑公式、状态到期顺序、
DOT 或 RNG 算法；只保存并恢复现有 StateUpdater/UnitState/Attributes/StaminaMaximum 的输入与相位。
仍待迁移：完整怪物 AI、TARGETS/回调生命周期、周期技能及其他未绑定 RNG 消费者、
本地实体组件、Map/RoomEx、任务/交易等隐藏运行时、资源内容 digest、
结构订阅者系统级副作用的完整回滚，以及真实 socket 跨进程重启接管组合验收。
M8-A、M8-D 和 M8 总体继续保持进行中。

### M8-A 第 28 批：TARGETS、状态移除生命周期与派生怪物创建恢复（2026-10-25）

本批承接第 27 批的明确缺口，集中保存目标注册表、单位共享节点、Conversion/Pregnant
回调及其创建依赖；不将两个小型 AI 控制组件的覆盖等同于完整怪物 AI checkpoint。

实现与边界：

- `NativePlayerTargetSlots` 持有 World-local 插入时钟。玩家 head 与绑定的
  `NativeTargetNode` 共用事件域，恢复不会回拨其他 World 的顺序。
  保存八个 head/注册事件和完整 allocation-attempt 集合，包括 slot 满员和初始死人失败的尝试；
  恢复直接写回，不重新注册、不把失败者变成等待晋升队列。
- 新增显式 `TargetRuntimeCheckpoint` v1：覆盖全部网络 payload ID，保存节点 presence、
  raw kind/owner/order，以及 `NativeAiState.previousMode`、`NativeAiTargetOverride` 的全部六字段。
  capture 不调用会惰性摘链的 getter，保留尚未观察到的失效 owner；恢复后原有代际校验仍生效。
  StateList、SummonedPet、Mercenary 重绑到同一个实际节点，不创建各自独立的顺序副本。
- 有 TARGETS 系统时，未覆盖的本地目标宿主、不同事件域节点、detached pet/merc/state
  顺序继续 fail closed。额外 Attributes source lists 和未知移除回调仍不支持。
- `UnitStateRuntimeCheckpoint` 升为 v2，继续读取 v1。明确编码 Conversion/Pregnant 的
  typed callback，验证原回调绑定身份，再重绑到目标 World 的 Monster、AttributesWrapper、
  factory、files、skill row；不序列化闭包，也不凭 stateId 猜测一个不存在的回调。
  Pregnant 区分 null/current/stale lifetime token，旧实体令牌不会绑定到重用 ID 的新生命期。
  携带状态的 Monster 另保存 row identity、converted/conversionOwnerId；这不是完整 Monster image。
- 完整 D2GC envelope 升为 v7，在 v6 unit-state 段后追加可选 TARGETS 段并纳入 canonical witness；
  保留 v1–v6 读取。活动 state membership 不得脱离 TARGETS owner，旧 ECS envelope 不得静默丢弃元数据。
  即使未携带 allocator，TARGETS 也走外层事务：先结束 lifecycle scope/冲刷结构订阅，再应用节点和时钟，
  最后应用可选 allocator。失败回滚遵循同一顺序，不让玩家释放通知清掉刚恢复的子节点。
- 真实 Pregnant 续跑暴露了怪物出生 HP/外观仍使用全局 `MathUtils.random` 的缺口。
  `ServerEntityFactory` 的这两个入口改为已纳入 registry 的单位 `NativeUnitRandom`：
  components → Doom Knight 特例 → inclusive base HP，固定 HP 范围也消费对应抽样。
  HP 数值公式不变，但出生随机结果/后续种子相位有意改正，不能宣称与旧全局随机轨迹兼容。
  当前仍是 host 的直接组件选择路径，不覆盖原版 MonRegData 的预生成区域变体池、原版完整出生种子分配。

原版依据：D2MOO `GAME/Targets.cpp` 的 TARGETS_First/UpdateSummonAI/FreeUnitNode/TARGETS_Last，
`SKILLS/SkillPal.cpp` 的 Conversion 移除回调、`SKILLS/SkillDruid.cpp` 的 SrvDo133/sub_6FD01B00，
以及 `MONSTER/Monster.cpp` 的 MONSTER_InitializeStatsAndSkills/MONSTER_SetComponents。
本批不改 Conversion 的既有生命比例公式、不改变存活 Pregnant 移除不生成怪物的规则。

验证覆盖：新 World 与独立 JVM 的真实 ServerEntityFactory 恢复、逐阶段严格 canonical/native RNG/D2S
对照，Pregnant current/null/stale 三态、存活移除不生成、死亡移除只生成一次、Conversion 到期和死亡清理；
晚期 D2S 失败后的 live/empty World 回滚；玩家删除/重建及删除额外玩家的订阅时序；
World-local 时钟、满员/死人失败尝试、失效节点 raw capture、共享节点与刷新不重入、
codec 截断/非法布尔/重复 ID/事件/未知版本、外层 witness、旧 v6 envelope/v1 state codec 兼容。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
沿用第 27 批验收命令，增加 `TargetRuntimeCheckpointTest`、`NativeTargetNodeTest`、
`NativeTargetOrderTest`、`NativeConversionLifecycleTest`、`NativeConversionDataTest`、
`MonsterComponentMissileIntegrationTest`、`MonsterStats*Test` 和 `PutridDefilerAiIntegrationTest`。
最终结果：服务端 61 项、core 144 项，共 205 项通过，0 失败、0 跳过；模块边界与桌面编译通过。
三个真实 TCP gate 通过：固定 Tick `6→37`（31 步）、部分拾取后剩余 15 金币且 owner window 保持、
旧 socket 延迟帧丢弃且当前 generation 命令成功。TCP gate 仍为同进程；独立 JVM 的状态/回调
验收不等于真实 socket 跨进程接管。`git diff --check` 通过。

剩余与下一批：完整 Monster 运行时（包括可变 component ordinals、模式/召唤所属与死亡相位）、
AI 决策对象/命令队列/路径缓存、pet/merc 其他可变运行时、周期技能及其他未绑定 RNG 消费者、
本地实体组件、Map/RoomEx、
任务/交易隐藏运行时、资源内容 digest、其他结构订阅者副作用，以及真实 socket 跨进程重启接管组合验收。
建议下一批集中处理怪物/AI 的明确标量与队列相位，继续对无法完整重建的对象 fail closed。
M8-A、M8-D、M8 总体继续保持进行中。

### M8-A 第 29 批：AI 命令栈与原生路径观察相位恢复（2026-10-25）

本批集中补齐 `NativeAiCommand` 和 `NativeAiPathCache`；不把宿主命令栈、部分原生路径缓存
等同于完整 Monster、AI 决策对象或 ECS 移动路径快照。

实现与边界：

- 新增显式 `AiControlRuntimeCheckpoint` v1。覆盖每个网络 payload ID 的组件 presence，
  区分无命令组件和 present/NONE，区分无缓存、known empty、known nonempty 和 unknown。
  原样保存 targetX/targetY、三个观察位；失效时保留的 raw 坐标/hasPoints 不被清零或推断成新路径。
- `NativeAiCommand` 提供不可变值镜像：current 在前，displaced commands 按实际 pop 顺序排列，
  保存 owner/target、任意现有 command 值、绝对 expiresAtFrame，不重新调用 copy/set 构造历史。
  上限 65536 项；完整栈先校验，再一次性替换，不修改现有命令执行规则、不消耗 RNG。
  owner/target 分别保存 null/current/stale lifetime；current 重绑目标 World 的真实 token，
  stale 永远失效，null 保留原有不加代际保护的语义。当前身份必须属于本 checkpoint 且已由实际
  factory 建立，缺失时拒绝恢复，不凭引用补造实体身份；失效引用允许指向已不存在的实体。
- `TargetRuntimeCheckpoint` 升为 v2，在原有 v1 数据之后追加完整 AI-control 段，严格校验 host 集合一致。
  保留 v1 读取并暴露 `hasAiControlRuntime=false`；旧格式的未覆盖不等于新格式的显式 absence，
  不删除旧格式未声明的命令/缓存。D2GC 外层布局仍为 v7，原有 TARGETS digest 自动覆盖新版内嵌字节；
  旧版 reader 会拒绝未知的 TARGETS 子版本，不会无声丢弃新增运行时。
- 沿用完整事务：结构订阅和 native lifecycle 结束后恢复 TARGETS/AI-control，最后恢复 allocator；
  在变更任何目标节点/时钟前预检所有命令 current lifetime。晚期 D2S 失败也恢复原命令栈、
  缓存及后续 pop 行为；未覆盖本地命令/缓存宿主、越界 current 引用、非法长度/布尔/身份、
  非升序/重复 host、截断和尾部字节继续 fail closed。

原版参考：D2MOO `AI/AiGeneral.cpp` 的 AIGENERAL_CopyAiCommand/AllocAiCommand 和命令控制结构。
本批保存的是已实现的宿主 current/displaced 栈，并不宣称完整复刻原版五参数循环链表。
缓存延续既有 `BaalCrab.noTowardPoints` 的明确 known/unknown 投影，不新增原版路径算法猜测。

验收包括：逐字段 schema guard、逐字节截断与恶意输入、全部 known/unknown 组合、
current/stale/null 栈逐层弹出、恢复后实体身份重用、缺失 later-host 身份时零部分写入、
v1/v2 兼容与 presence 清除；真实 Scarab 恢复命令后执行 Jab、保留 displaced MOVE 且无额外概率抽样，
Baal/Clone 恢复三类缓存后真实 node9 选择；真实 factory 的新 World/独立 JVM 严格 canonical/native RNG/D2S
续跑和 live/empty World 晚期失败回滚。命令/缓存独立变化必须改变 canonical，且不改变 EntitySync 或 D2S。

扩大 AI 回归时，修正一项既有 Baal owner-recreation 测试夹具：原测试把 detached StateList/pet 的
全局顺序与 World-local player head 比较，第二个 World 的结果依赖前面的全局插入次数。
改为在插入前绑定实际共享节点，释放 wall 时显式 detach；保留玩家组释放、刷新不重入、
重新插入才恢复、独立 node9 存活等原有断言，不改生产规则，也不放宽期望值。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
沿用第 28 批验收组合，增加 `AiControlRuntimeCheckpointTest`、`NativeAiPathCacheTest`、
`NativeAiCommandTest`、`ScarabCommandTest`、`ScarabLeaderCommandTest`、`BaalCrabNode9IntegrationTest`。
最终结果：服务端 63 项、core 205 项，共 268 项通过，0 失败、0 跳过；模块边界、桌面编译、
`git diff --check` 通过。三个真实 TCP gate 通过：固定 Tick `7→37`（30 步）、
部分拾取后剩余 15 金币且 owner window 保持、旧 socket 延迟帧丢弃且当前 generation 命令成功。
这三个 TCP gate 仍是同进程；独立 JVM 的命令/缓存/状态续跑不等于真实 socket 跨进程接管。

剩余与下一批：完整 Monster image（component ordinals、模式元素冻结选择、召唤所属/出生锚点、
死亡与复活相位）、AI 对象私有 timer/状态机、Casting/Sequence 与 ECS 移动路径、pet/merc 其他运行时，
以及前批列出的 Map/RoomEx、本地实体、任务/交易、资源 digest 和真实 socket 跨进程接管组合验收。
M8-A、M8-D、M8 总体仍进行中。

### M8-A 第 30 批：完整 Monster 组件镜像与真实生命周期恢复（2026-10-25）

本批集中补齐 `Monster` 组件自身的全部实例字段；不把完整组件值等同于完整怪物实体、
AI 决策对象、ECS 移动路径或碰撞系统缓存。继续使用真实 `ServerEntityFactory` 生命周期，
不通过反射复制生产组件、不调用 `Monster.set/setRank` 重建历史，不重新进入模式或抽取 RNG。

实现与边界：

- 新增显式 `MonsterRuntimeCheckpoint` v1，保存 16 个原生 component variant 序号、
  rank/affixes、Baal wave/pack/所属、召唤和复活来源、死亡调度与碰撞标志、frog/vulture 相位、
  出生锚点、conversion overlay、冷却及双精度帧值。MonStats/MonStats2 仅存 row identity，
  恢复时校验 factory 已创建的真实表句柄，不复制内容表，也不把渲染 CompCode 当原生序号。
- 冻结保存 `modeElements` 的 presence、全部 damage/drain 数组、cold/poison length 与解析标志；
  保留已选中的临时伤害结果，不凭永久属性重算。原生种子保存 null/present、low/high 两字及
  `rngState` 镜像；恢复已有 seed 时原位更新，避免已绑定的 RNG wrapper 脱离。
  真实 world-native archive 的 Monster image 与 unit RNG registry 必须一致，冲突在变更 ECS 前拒绝。
- owner/mount 分别保存 null/current/stale lifetime：current 必须在 checkpoint 内且目标 factory
  已建立实际身份，stale 恢复为永远失效 token，null 保留原语义，实体 ID 重用不能激活旧引用。
  其他无 token 的既有 owner ID 字段保留 raw 值，不擅自增加宿主尚未实现的代际规则。
- spawnZone 必须属于本 World 注册的 Map，按 levelId/levelSeed/矩形/synthetic 标志在目标预建
  Map 中唯一精确重绑；缺失、重复、foreign map/zone 拒绝。不按 spawnX/Y 猜测 zone，
  不生成或导入 Map/RoomEx 运行时；相同 zone key 也不是资源内容 digest 的替代品。
- `UnitStateRuntimeCheckpoint` 升为 v3，在每条既有记录尾部追加 Monster image，包含显式 absence，
  即使没有 UnitStates holder 也覆盖 Monster。v1/v2 继续可读且明确返回未覆盖完整 Monster；
  v2 conversion overlay 与新完整 image 必须一致。D2GC 外层仍为 v7，已有 unit-state digest
  自动覆盖内嵌字节。全记录先检查 row/token/zone 等依赖再赋值，未覆盖本地 Monster、非法宿主、
  重复 ID、非法布尔/浮点/长度/版本、截断与尾部字节继续 fail closed。
- 沿用既有 ECS→RNG→D2S 事务和最终 runtime 重应用；真实 factory 的新 World、独立 JVM
  严格 canonical/native RNG/D2S 续跑，以及缺失 zone、live/empty World 晚期 D2S 失败回滚均纳入验收。
  续跑检查覆盖原生 RNG 与明确冷却/帧字段，不宣称已经验证完整 AI、死亡动作或碰撞缓存续跑。

原版参考：D2MOO `MONSTER/MonsterMode.cpp::sub_6FC627B0` 的模式临时元素选择，
以及死亡路径的 `PATH_SetUnitDeadCollision` 边界；本批只保留宿主已实现值，不新增模式/死亡/复活规则。
测试包含组件和 mode-elements 字段/数组尺寸 schema guard、全字段 round-trip、9 类 owner/mount
lifetime 组合、v1/v2/v3 兼容、冻结结果恢复后的真实 projectile/GH/NU 行为，以及 Monster-only
变化影响 canonical 但不改变 EntitySync/D2S/RNG、嵌套镜像损坏必须被校验和拒绝。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。沿用第 29 批验收组合，增加
`MonsterRuntimeCheckpointTest`、`MonsterModeElementLifecycleTest`、`MonsterModeElementProfileTest`、
`MonsterModeMeleeIntegrationTest`、`CorpseSpawnCollisionIntegrationTest`。
最终扩大回归：服务端 67 项、core 250 项，共 317 项通过，0 失败、0 跳过。
模块边界、桌面编译和 `git diff --check` 通过。三个真实 TCP gate 通过：固定 Tick `8→39`
（31 步）、部分拾取后剩余 15 金币且 owner window 保持、旧 socket 延迟帧丢弃且当前 generation
命令成功。TCP gate 仍为同进程；独立 JVM 的组件/RNG/D2S 续跑不等于真实 socket 跨进程接管。

剩余与下一批：AI 对象私有 timer/状态机、Casting/Sequence 与 ECS 移动路径、pet/merc 其他运行时，
以及前批列出的 Map/RoomEx、本地实体、任务/交易、资源 digest、其他结构订阅者副作用和真实 socket
跨进程接管组合验收。M8-A、M8-D、M8 总体仍进行中，不能将本批标记为 M8 整体收尾。

### M8-A 第 31 批：共享 AI 私有运行时与首组状态机恢复（2026-10-25）

本批集中处理 AI 基类、绝对帧调度和首组实体 AI 的私有状态；不宣称全部 AI 类型或整只怪物
已经可完整重启续跑，也不把对象状态机恢复等同于 Casting/Sequence、动作事件和移动路径恢复。

实现与边界：

- 新增显式 `AiRuntimeState` v1 和 `AiDecisionRuntimeCheckpoint` v1。按精确实现类型及宿主 ID
  校验，不反射复制私有字段、不 Java serialization、不动态加载 checkpoint 中的类名。
  字节解析只使用白名单构造器作 schema 校验，不注入、initialize、update 或抽取 RNG；
  实际恢复原位覆盖目标 factory 已建立的 AI 对象，不替换其服务、mapper、订阅或交互器引用。
- 基类保存 SLEEP、8 槽参数的实际长度/值、monsound、movementActive/running/velocity 缓存、
  nextWarCryThink；私有兼容控制 RNG 保存 stream/context/state/drawCount，fallback 原生 seed
  保存 presence 与 low/high 两字。未存在的流保持 null，不通过重新初始化或抽样“对齐”。
  SLEEP/旧式 time 等允许原有正无穷 sentinel，NaN/负无穷及非法尺寸继续拒绝。
- `NativeDecisionAI` 保存绝对 nextThinkFrame、双精度 elapsedFrames、dead、state、
  decisionCount、lastDecisionTarget、lastApproach；不将绝对事件改成恢复时起算的相对延迟。
  ReanimatedHorde 额外保存 resurrectionDelay/deathScheduled，VileDog 保存一次性 initialized，
  GenericMonster 保存 nextThink/state 并校验不可变 nativeAiName 配置。
- 本批精确白名单共 14 种实体 AI：QuillRat、Scarab、Fallen、Brute、Zombie、Skeleton、
  GenericMonster、Andariel、DoomKnight、FlyingScimitar、BaalMinion、ReanimatedHorde、
  PutridDefiler、VileDog；另支持共享 `AI.IDLE`，但不写回其跨实体共享字段。
  6 种旧式 FSM 保存 current/previous/global、nextAction/time 与各自 aiParam/target 字段。
  `CheckpointStateMachine` 保持原 DefaultStateMachine 的游戏行为，仅增加无回调值恢复入口，
  不调用 changeState/enter/exit，历史 revert 行为保留；FSM owner 必须与宿主一致。
  工作向量每次使用前重写，World 订阅由正常初始化重绑，二者不作为历史逻辑状态复制。
- `UnitStateRuntimeCheckpoint` 升为 v4，既有 v3 尾部再追加逐实体 AI image，明确区分 wrapper
  absent、present/null、共享 IDLE 与具体对象。presence、实现类型或 Generic 配置不符合真实宿主时
  拒绝恢复；不凭镜像猜测构造/替换玩法 AI。v1/v2/v3 继续可读且不声称覆盖 AI 对象，不删除旧格式
  未声明的对象状态。D2GC 外层仍 v7，原有 unit-state digest 自动覆盖新增字节；host 集合及 ID
  必须一致，未覆盖本地 AIWrapper、未知实现/继承类和非法输入 fail closed。
- 全部记录先预检，再应用；沿用 ECS/RNG/D2S 事务及最后一次 runtime 重应用。真实 factory
  的 DoomKnight 新 World/独立 JVM 进行 35 步受控 action-completion 续跑，比较每步诊断、严格
  canonical、原生 RNG 与 D2S；live/empty World 晚期 D2S 失败回滚也包含私有 AI 相位，
  live 恢复保留实际 AI 对象身份。该用例显式在快照前清理动作/路径，不替代完整动作系统验收。

原版参考：D2MOO `AI/AiTactics.cpp::AITACTICS_Idle` 使用 `dwGameFrame + nFrames` 调度
EVENTTYPE_AITHINK，`AI/AiThink.cpp` 的 Fn014 QuillRat / Fn114 ReanimatedHorde。
本批保留宿主已实现字段，不改概率、目标筛选、死亡/复活或模式规则。旧式 AI 中仍有
`MathUtils.random` 分支，本批未把全局随机源改造成已绑定流，不宣称这些分支已获完整决定性续跑。

验收覆盖：逐类字段 schema guard、全部支持类型值镜像、FSM 历史/无回调、正无穷 sentinel、
私有 RNG 的零状态/计数/两字种子与后续序列、逐字节截断、未知枚举/版本/类型/宿主、
多宿主预检零部分写入、旧版本 uncovered；真实 DoomKnight 的剩余 16 帧等待及首次攻击 RNG，
AI-only 私有相位变化改变 canonical 而不改变 EntitySync/D2S/RNG，嵌套 AI 镜像损坏必须被校验和拒绝。

真实资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。沿用第 30 批 checkpoint/factory/状态/目标/
RNG/D2S 组合，扩大到本批 `AiRuntimeStateTest`、DoomKnight、NativeAiDecision、Act1NativeAi、
VileDog、Scarab command/leader、Remaining20261017、WarCry、AIStepper、PutridDefiler、
BaalCrabNode9、NativeAiPathCache/Command 及真实 factory 等回归。
最终源码扩大回归：服务端 69 项、core 584 项，共 653 项通过，0 失败、0 跳过；
其中新增 AI schema 测试 10 项。模块边界、桌面编译和 `git diff --check` 通过。
三个真实 TCP gate 通过：固定 Tick `7→37`（30 步）、部分拾取后剩余 15 金币且 owner window
保持、旧 socket 延迟帧丢弃且当前 generation 命令成功。TCP gate 仍为同进程；独立 JVM 的
AI/RNG/D2S 续跑不等于真实 socket 跨进程接管。

剩余与下一批：扩展其余 AI 类型的明确私有字段适配，并集中处理 Casting/Sequence/动作与
ECS 移动路径；其他 pet/merc 运行时、未绑定 RNG 消费者、Map/RoomEx、本地实体、任务/交易、
资源 digest、其他结构订阅者副作用及真实 socket 跨进程接管仍保留。M8-A、M8-D、M8 总体继续进行中。

### M8-A 第 32 批：Boss、施法者与机关陷阱 AI 私有状态恢复（2026-10-25）

本批集中扩展 19 类原生 AI 的显式镜像，沿用第 31 批的真实 factory 宿主、全记录预检和
ECS/RNG/D2S 回滚事务。它是 AI 对象层覆盖扩展，不是 Casting/Sequence、伤害事件或路径镜像。

- 新支持 BaalCrab、Diablo、Mephisto、Duriel、Summoner、OblivionKnight、AbyssKnight、
  ArcaneTower、TrapMissile、TrapNova、TrapPoison、TrapMelee、TrapRightArrow、TrapLeftArrow、
  TrappedSoul、ThornHulk、VileMother、ZakarumPriest、ZakarumZealot。精确白名单现为 33 类
  实体 AI，另有共享 IDLE；未适配类和未声明继承类继续拒绝，不通过反射或通用字段拷贝兜底。
- Boss 保留 Baal 初始 anchor/anchored 及目标计数/评分、Diablo retained action/actionCount、
  Mephisto phase/burstRemaining/spellCounter、Duriel 一次性 Holy Freeze 选择标记。
  anchor 是历史状态，即使恢复时位置已改变也不能重算；原位写入目标对象的既有向量。
- 施法者保留 Summoner 双冷却、OblivionKnight curseDeadline/previousTarget、AbyssKnight recovery、
  ZakarumPriest 本地帧钟和三个法术 deadline、ZakarumZealot attackArmed；另外保留 ThornHulk
  burst/recovery/waitAfterFrenzy 与 VileMother 已生成数量，不重抽 RNG 或重置技能预算。
- 陷阱保留 ArcaneTower phase/remaining/nextAction、TrapMissile shots/needsDelay、
  TrapNova（含 TrapPoison）casts/needsDelay、左右箭陷阱的双精度 elapsedFrames/nextAttackFrame、
  TrappedSoul awakened/双精度 frame/nextAttackFrame。新增局部 double 帧钟只接受有限值，
  float 冷却使用已有有限值 codec，保持宿主实际精度和计时表示，不借 checkpoint 修改玩法调度。
- Mephisto/Baal 的 destination/ray/collision、Summoner escape、Priest landing/ray/collision
  在使用前覆盖，仍属 scratch，不复制旧 World/Map 或探测结果。TrapPoison/TrapLeftArrow 精确
  类型单独声明，私有数据由其已适配父类读写；不会放开任意派生类。
- 既有 AI v1、wrapper v1、unit-state v4、D2GC v7 均不变；原有 14 类字节布局不变。
  新类型加入白名单，私有尾部仍自动纳入原 unit-state canonical witness。

原版依据：D2MOO `AiThink.cpp::AITHINK_Fn077_TrapMissile` 的已用次数/交替延迟、
Fn078/079 的绝对 deadline、Fn093_ArcaneTower 的 phase/budget/deadline、
Fn051_Diablo 的 retained action，以及 Fn099_TrappedSoul 的 awakened/deadline。
本批保留宿主已实现状态，不修改这些 AI 的概率、目标规则、原版差异或路径近似。

验收增加/强化：33 类逐字段标量比对（不只比较重编码镜像）、预检零写入、全部 schema
逐字节截断、非有限 double 拒绝、Baal 锚点值/向量身份；真实 TrapMissile 在第一发结束后恢复，
保持 17 帧间隔且第二发后按已耗预算死亡。真实 D2GS factory 的 trap 在新 World/独立 JVM
续跑 100 步，逐步比较状态/诊断、严格 canonical、原生 RNG 和 D2S；AI-only 变化及 live/empty
World 晚期 D2S 失败回滚同时覆盖 DoomKnight 和 TrapMissile。用例仍显式完成动作并清理
Casting/Sequence/Pathfind，不是完整战斗续跑或真实 socket 跨进程接管。

扩大到全部 AI 测试时，BaalCrabTargeting 的 3 项生命周期断言暴露了夹具的事件域混用：
子单位通过 detached 兼容计数器 set，而 Player head 已使用 World 私有序列。
将该夹具的子单位统一对齐 factory 的 `NativeTargetNodes.get`/bind-before-set，
归属切换、移除/重建和 bonewall 改类显式执行 detach/insert，原全部期望值不变。
这也消除 isolated class 执行依赖其他测试先抬高静态兼容计数器的问题，不修改生产目标选择算法。

最终扩大回归使用真实 1.10f 资源 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，
core 范围在第 31 批基础上扩大为 `com.riiablo.engine.server.ai.*Test` 全部 AI 测试，
保留 checkpoint/factory/状态/目标/RNG/D2S 回归。服务端 70 项、core 1008 项，共 1078 项通过，
0 失败、0 跳过；BaalCrabTargeting 29 项及 AI schema 12 项均通过。
模块边界、桌面编译和 `git diff --check` 通过。三个真实 TCP gate 通过：固定 Tick `7→38`
（31 步）、部分拾取后剩余 15 金币且 owner window 保持、旧 socket 延迟帧丢弃且当前
generation 命令成功。TCP gate 仍为同进程，未扩展成真实 socket 跨进程接管验收。

剩余：其余 AI 类型（含 BaalCrabClone）、Casting/Sequence 与冻结战斗记录、ECS 路径、其他 pet/merc 运行时，
以及前批 Map/RoomEx、本地实体、未绑定 RNG、任务/交易、资源 digest、结构订阅副作用和
真实 socket 跨进程接管组合验收。M8-A、M8-D、M8 总体继续进行中。

### M8-A 第 33 批：分身、潜伏计时与一次性 AI 阶段恢复（2026-10-25）

本批将可共用字段策略和验收的 17 类 AI 集中适配，沿用真实 factory 宿主原位恢复、
全记录预检与 ECS/RNG/D2S 事务，不修改 AI 决策概率、技能、目标选择或既有调度语义。

- 新支持 BaalCrabClone、BaalTaunt、BaalTentacle、BaalThrone、Tentacle、TentacleHead、
  SuicideMinion、WillOWisp、SevenTIllusion、Spirit、SandMaggotQueen、Sarcophagus、
  Ancient、SiegeTower、SiegeBeast、Succubus、SuccubusWitch。精确白名单累计为 50 类实体 AI，
  另有共享 IDLE；未知类与未声明派生类仍拒绝，不采用反射式生产序列化或动态类加载。
- BaalTaunt 保留 neutralFrames；BaalTentacle 保留 ownerId、本地 frame 和 expireFrame。
  expireFrame 的 NaN 是宿主尚未初始化寿命的合法 sentinel，单独用严格 boolean presence 编码，
  已初始化时使用有限 float codec。不会把 NaN 扩散到其他帧钟，也不会恢复时重抽寿命。
- Tentacle/TentacleHead 通过显式父类 codec 保留 phase、双精度 frame/changeAfter；
  Tentacle 另外保留 ownerId。BaalCrabClone 复用已审计的 BaalCrab 历史 anchor/anchored、
  目标计数/评分和 NativeDecisionAI 状态，不因没有自有字段而跳过父类数据。
- SuicideMinion 保留 deadline、explosionFrame（含 -1 sentinel）、detonated、hitRequested；
  Spirit/SevenTIllusion 保留 attacked；SandMaggotQueen 保留 spawned/spawnPhase，
  Sarcophagus 保留 spawned/nextSpawn；WillOWisp 保留 phase/count/indexOrDeadline。
- BaalThrone 保留 preparedWave/transformed/castWave，但不把这些局部标记当作任务系统的
  权威波次状态。Ancient、SiegeTower/SiegeBeast、Succubus/SuccubusWitch 没有额外实例字段，
  仍逐个声明具体类型，继承状态走同一显式 codec，不放开任意子类。
- AI v1、wrapper v1、unit-state v4、D2GC v7 版本保持不变，原有 33 类布局不变；
  新类型的私有尾部自动进入 unit-state canonical witness。构造原型时不初始化宿主、不抽 RNG。

原版依据：D2MOO `AiThink.cpp::AITHINK_Fn097_Spirit` 的先提交一次性标记、后请求攻击、
用尽后等待 50 帧；Fn139_BaalTentacle 的仅首次设置随机寿命及严格超过 deadline 才死亡；
Fn056_Tentacle/Fn057_TentacleHead 的潜伏阶段及秒数转换的绝对 deadline。
本批仅保存宿主已经实现的表示，不借 checkpoint 调整它们与原版的既有差异。

验收扩展为 50 类逐字段标量比对、继承链完整字段策略、预检零写入、逐字节截断、
非有限局部帧钟拒绝；专门验证寿命 unset/finite 两种表示、非法 presence、带 NaN 的有限分支
被拒绝，及 BaalCrab/Clone 锚点值与目标向量对象身份。真实触手验证剩余寿命到期不重抽，
Tentacle/Head 验证跨新 World 保留潜伏阶段和剩余延迟，Spirit 验证目标丢失/重新出现不重置攻击。

真实 D2GS factory 的 Spirit 在第一发动作完成后保存，在新 World 与独立 JVM 各续跑 120 步，
逐步比对状态/诊断、严格 canonical（含原生 RNG）和 D2S，且每步断言不再创建攻击动作。
AI-only canonical 变化和晚期 D2S 失败回滚扩大为 DoomKnight、TrapMissile、Spirit 三类，
覆盖现有/空 World 与 live AI 身份保持。所有行为续跑用例仍采用受控动作完成边界，
不宣称恢复 Casting/Sequence、真实技能伤害或跨进程 socket。

最终扩大回归使用真实 1.10f 资源 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，
范围沿用第 32 批的全部 AI、checkpoint/factory/状态/目标/RNG/D2S 回归。
core 1012 项、服务端 71 项，共 1083 项通过，0 失败、0 跳过；模块边界、桌面编译及
`git diff --check` 通过。三个真实 TCP gate 通过：固定 Tick `7→38`（31 步）、
部分拾取后剩余 15 金币且 owner window 保持、旧 socket 延迟帧丢弃且当前 generation 命令成功。
TCP gate 仍为同进程，不代表真实 socket 跨进程接管验收完成。

剩余：其他未适配 AI、Casting/Sequence 与冻结战斗记录、ECS 路径、其他 pet/merc 运行时；
Act5 等任务权威状态、Map/RoomEx、本地实体、未绑定 RNG、交易、资源 digest、结构订阅副作用
及真实 socket 跨进程接管仍待后续组合验收。仅恢复 BaalThrone/Ancient 局部字段不等于恢复任务，
仅恢复 WillOWisp 局部编队字段不等于完整多人编队/动作续跑。M8-A、M8-D、M8 总体继续进行中。

### M8-A 第 34 批：召唤预算、炮塔冷却与循环/支援 AI 恢复（2026-10-25）

本批集中扩展 28 类可共用标量状态策略的原生 AI，沿用精确具体类型白名单、纯构造原型、
全记录预检、真实 factory 宿主原位覆盖与 ECS/RNG/D2S 回滚事务；不修改宿主玩法、概率或调度。

- 新支持 BatDemon、CatapultSpotter、ClawViperEx、DesertTurret、ElementalBeast、FetishBlowgun、
  FingerMage、GenericSpawner、GreaterMummy、HighPriest、InvisoSpawner、Izual、MaggotEgg、
  Megademon、MinionSpawner、Mosquito、Overseer、BaalToStairs、PantherJavelin、QuillMother、
  BloodLord、Catapult、DeathMauler、FrozenHorror、Griswold、HellMeteor、Minion、Smith。
  白名单累计为 78 类实体 AI，另有共享 IDLE；未知类型及未声明派生类仍明确拒绝。
- BatDemon 保存 phase/loops/regenBonus：回血增益既是属性值，也是后续撤销所需的 AI 账本，
  只恢复其中一边会重复叠加或无法扣除。FetishBlowgun/Mosquito 保存 phase/loops，
  FingerMage 保存 fleeing/tries，ElementalBeast 保存 phase，避免恢复时重启循环或技能阶段。
- GenericSpawner 保存 lastSpawn/count，InvisoSpawner 保存 deadline/remaining，
  MinionSpawner 保存 deadline/count，MaggotEgg 保存 hatched；已消耗的生成次数与孵化标记不重置。
  GenericSpawner 的 initialize 会设置 lastSpawn，但镜像解析与应用都不调用 initialize。
- CatapultSpotter 保存 lastShot/shotType/shotsRemaining；ClawViperEx 保存 nextShot/charged；
  DesertTurret 保存 shotCount/nextAction/initialized；Megademon 保存 float frame/infernoAfter。
  保持各自 long/float 和绝对帧/剩余秒数表示，不借本批把旧本地时钟改成共享时钟。
- HighPriest 保存 healHydraDeadline/engaged；GreaterMummy 保存 healTarget/reviveTarget/candidates，
  同时保留检查点时可查询的扫描结果；Overseer 保存 rallyDeadline/lastAttacker；
  Izual 保存 activated/doldrums/swings，BaalToStairs 保存 retired，不在恢复时重放激活或退场逻辑。
- DesertTurret aim 和 PantherJavelin escape 使用前会被覆盖，是 scratch 向量，不复制旧对象。
  无自有可变字段的类型仍逐个列入白名单，继承 NativeDecisionAI/AI 的已审计状态，
  不复制 mapper、服务、订阅、World/Map，也不通过反射式生产序列化或任意继承兜底。
- AI v1、wrapper v1、unit-state v4、D2GC v7 保持不变，原有 50 类的字节布局不变，
  新类型尾部仍由 unit-state canonical witness 覆盖。

原版依据：D2MOO `AiThink.cpp::AITHINK_Fn029_BatDemon` 将栖息增益存入 dwAiParam[2]，
并在离开栖息阶段时扣除；Fn040_MaggotEgg 的孵化后死亡标记；Fn094_DesertTurret 的初始化、
短/长间隔和计数；Fn089_Megademon 在请求 Inferno 前消耗冷却，严格超过 deadline 才再施放；
Fn121_MinionSpawner 的 deadline/已用次数。本批保存宿主现有表示，不顺带调整已有原版差异。

验收扩展为 78 类逐字段往返、继承链字段策略、预检零写入、逐字节截断及 float 时钟非有限值
拒绝。真实表/ECS 用例验证已提交孵化的蛋不重复请求孵化、用尽预算的 MinionSpawner 不再请求生成技能、DesertTurret
跨新 World 连续 80 步保留方向射击与冷却（不重放初始化施法），以及被拒绝 Inferno 的剩余
冷却和严格 deadline 等值边界。

真实 D2GS factory 的 BatDemon 在已有 40 点栖息增益（base/aggregate 80→120）时保存，
新 World 与独立 JVM 各续跑 45 步，逐步比较状态/诊断、canonical（含原生 RNG）和 D2S，
并检查原 frame 34 离巢时仅扣除 40 点、恢复为 80。另验死亡时撤销一次、重复 kill 不重复扣除。
AI-only witness 变化及现有/空 World 晚期 D2S 失败回滚增加有实际属性增益的 BatDemon，
与 DoomKnight、TrapMissile、Spirit 一并验证，保持 live AI 对象身份。
参数控制仅位于测试夹具，不改全局 MonStats。续跑仍显式清理动作组件，不宣称完整动作恢复。

最终扩大回归使用真实 1.10f 资源 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，
沿用第 33 批的全部 AI、checkpoint/factory/状态/目标/RNG/D2S 范围。
core 1017 项、服务端 73 项，共 1090 项通过，0 失败、0 跳过；模块边界、桌面编译及
`git diff --check` 通过。三个真实 TCP gate 通过：固定 Tick `6→37`（31 步）、
部分拾取后剩余 15 金币且 owner window 保持、旧 socket 延迟帧丢弃且当前 generation 命令成功。
TCP gate 仍为同进程，不代表真实 socket 跨进程接管验收完成。

剩余：其他 AI（包括带历史坐标或跨实体身份 token 的类型及旧 FSM）、Casting/Sequence 与
冻结战斗记录、ECS 路径、其他 pet/merc 运行时，以及前批列出的任务、Map/RoomEx、交易、
本地实体、未绑定 RNG、资源 digest、结构订阅副作用和真实 socket 跨进程接管组合验收。
Izual/BaalToStairs 的局部标记不等于任务权威状态恢复，召唤器私有预算恢复也不等于
RoomEx RNG、实际技能生成和完整战斗续跑全部覆盖。M8-A、M8-D、M8 总体继续进行中。

### M8-A 第 35 批：飞行/骑乘记忆与独立计时型 AI 恢复（2026-10-25）

本批集中补齐 10 类 AI 的私有状态，继续采用精确类型白名单、纯构造解析、全记录预检、
factory 宿主原位恢复和既有 ECS/RNG/D2S 事务；不改玩法、概率、技能执行或时钟表示。

- 新支持 DarkWanderer、Imp、Vulture、Nihlathak、Arach、AssassinSentry、BonePrison、
  FoulCrowNest、GargoyleTrap、Vines。累计 88 类实体 AI，另有共享 IDLE；未知具体类型、
  未声明派生类继续拒绝。AI v1、wrapper v1、unit-state v4、D2GC v7 不变，原有 78 类布局不变。
- Vulture 保存飞行阶段 aiParam0、历史航点 aiParam1/2、groundIdle/Walk/Run 三个 byte、
  savedModes、landingStatus 和 lastTraceFrame。航点不是临时向量，不能恢复时重抽；
  地面模式账本也不能从当前飞行动画推导。恢复只覆盖 AI 字段，不调用 airborne/land/kill。
- Imp 保存 previousTarget/mountedSkill，避免丢失目标连续性或重复选择骑乘技能。
  nativeOwner/nativeRequestedMount 的身份仍由既有 Monster 镜像负责；
  Nihlathak 无新增实例字段，明确继承 Overseer 的 rallyDeadline/lastAttacker 和基类状态。
- DarkWanderer 保存 phase/attempts。destination 在每次成功 beforeDecision 时由
  Act3QuestSystem.getDarkWandererCoordinates 完整覆盖，失败时不进入 think，是 scratch；
  不把该临时坐标当作任务权威状态，不重放任务初始化或离场事件。
- Arach 保存 nextThink、三个 aiParam 和 state，其逃跑向量使用前由当前位置覆盖。
  GargoyleTrap/Vines 保存 nextThink/state；这三类 nextThink 可由旧 SLEEP 产生正无穷
  sleep sentinel，沿用 timer codec，拒绝 NaN/负无穷。
- FoulCrowNest 保存 elapsedFrames/lastSpawnFrame/nextThink/spawned/state；三个时钟必须有限，
  不把固定冷却误作 sleep sentinel。AssassinSentry 保存 state，BonePrison 保存 dead。
  Vines 的 corpseEntities 是目标 World 的实时查询订阅，保留 factory 注入/初始化后的对象，
  不复制源订阅、mapper、World、Map 或服务。

原版核对：D2MOO AiThink.cpp 的 Fn023_Vulture 明确保留 dwAiParam[1/2] 航点并递减飞行计数；
Fn043_FoulCrowNest 保留上次生成帧/已生成数；Fn063_GargoyleTrap 保留射击恢复；
Fn091_DarkWanderer 每轮向 ACT3Q7 获取坐标并保留阶段/重试数。
本批忠实保存宿主现有表示，不将 checkpoint 工作扩大成原版行为差异修正。

验收扩展到 88 类逐字段往返、继承链字段策略、预检零写入、逐字节截断、非默认有符号 byte、
严格 boolean 和 timer/有限 float 非法值拒绝。真实表/ECS 用例组合恢复 AI 与 Monster（含共享
原生种子），验证秃鹰沿用未到达航点、死亡恢复原地面模式，魔童在预建同 ID owner 上保留
已选技能与后续决策/RNG；另验鸟巢耗尽预算不重置、石像陷阱保留剩余冷却，以及藤蔓保留
目标 World 的订阅对象；已死亡 BonePrison 不重新执行 kill 动作。
移动模式、地图/碰撞和 owner 依赖在这些定向夹具中显式预建，
不宣称完整飞行、骑乘物理或 owner 生命周期恢复。

真实 D2GS factory 的 GargoyleTrap 在射击后剩余 0.75 秒冷却时保存，新 World 与独立 JVM
各续跑 60 步，逐步比较动作请求、状态、canonical（含原生 RNG）和 D2S，检查不会提前射击
且到期后重新射击。AI-only witness 变化及现有/空 World 晚期 D2S 失败回滚新增该类型，
保持目标 live AI 对象身份。续跑仍使用受控动作完成边界，显式清理 Casting/Sequence/Pathfind，
不是完整动作或网络接管验收。

定向验证命令（真实资源由 D2_HOME 指向本地 1.10f 目录）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.ai.AiRuntimeStateTest --tests com.riiablo.engine.server.ai.FlightAndIndependentCheckpointIntegrationTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --continue --no-daemon --max-workers=1 --console=plain
```

最终扩大回归沿用第 34 批全部 AI、checkpoint/factory/状态/目标/RNG/D2S 测试范围，
附带 :server:d2gs:headlessSimulationTick、:server:d2gs:headlessReconnectGroundLoot、
:server:d2gs:headlessStaleTransport、:desktop:compileJava 与自动 verifyModuleBoundaries。
最终结果：core 1024 项、server/d2gs 74 项，共 1098 项通过，0 失败、0 跳过；
桌面编译、模块边界和 git diff --check 通过。三个真实 TCP gate 通过：
固定 Tick 6→36（30 步、40ms 步长）、部分拾取后剩余 15 金币且 ownerWindowPreserved=true、
旧 socket 延迟帧 delayedFrameDropped=true 且当前 generationAwareCommand=true。
TCP gate 仍为同进程，不代表真实 socket 跨进程接管完成。

剩余：Regurgitator 的 foodIdentity 必须另接 null/current/stale 引用编码、覆盖范围和目标
World 身份绑定，不能把旧 token 对象复制过去或一律重绑当前实体；该类型继续明确拒绝。
其他旧 FSM/宠物/佣兵 AI、完整 Casting/Sequence/冻结战斗记录和 Item 引用、ECS 路径/
NativePathRuntime 游标、MovementModes/飞行碰撞/骑乘物理、DiabPrison 关系与 AssassinTrapSystem
控制器等仍需各自适配。任务、Map/RoomEx、本地实体、交易、未绑定 RNG、资源 digest、
结构订阅副作用及真实 socket 跨进程接管仍按前批保留。局部 AI 字段支持不等于这些系统
整体可恢复；M8-A、M8-D、M8 总体继续进行中。

### M8-A 第 36 批：尸体寿命引用、血乌锚点及隐藏阶段恢复（2026-10-25）

本批集中补齐 Regurgitator、BloodRaven、FrogDemon、CycleOfLife、DeathSentry，
累计 93 类实体 AI，另有共享 IDLE。沿用精确类型白名单、纯构造解析、全记录预检、
factory 宿主原位恢复和既有 ECS/RNG/D2S 事务；不改玩法、概率或时钟表示。
AI v1、wrapper v1、unit-state v4、D2GC v7 不变，原有 88 类字节布局不变。

- Regurgitator 保存 phase/attempts，并用显式 ID + lifetime 编码 food/foodIdentity。
  null token 保留为 null；current token 绑定目标 World 中该 ID 的当前真实身份；
  retired token 恢复为目标 World 的独立失效 token，绝不绑定同 ID 的新实体。
  非 null token 捕获和恢复均要求显式 World，不借用 Riiablo.engine，不复制源 World/token。
- null/current 引用仍可能解引用实体，必须属于 checkpoint 覆盖集；retired 引用可指向
  集合外 ID。unit-state 捕获、全记录恢复预检及 D2GC 外层重建都检查覆盖关系。
  current 引用还必须指向目标 World 已分配、ECS active 且具有当前 NativePathIdentity 的实体；
  预检不创建身份组件、不分配退休 token、不写 AI，非法/未分配/删除 ID 在写入前拒绝。
  退休 token 显式标记为永久失效，且 Regurgitator 先判身份再查询尸体组件，保证集合外及
  极大未分配 ID 不触发 Artemis 越界查询；普通分配 token 的有效期判断不变。
- BloodRaven 保存历史 anchor、nextThink、quickStrikeRollChance、returningToAnchor、
  specialUses/state；原位更新目标 anchor 向量，不重新取出生点或补充特殊技能次数。
  anchor 坐标必须有限，nextThink 保留正无穷 sleep sentinel，拒绝 NaN/负无穷。
- FrogDemon 保存 phase/hiddenDecisions。CycleOfLife 明确继承 Vines codec；
  DeathSentry 明确继承 AssassinSentry codec，二者无新增实例字段，不把宿主控制器
  或实时查询订阅当作可序列化私有状态。

原版核对：D2MOO AiThink.cpp 的 Fn071_Regurgitator 保留阶段、尸体 ID、接近尝试数及
8 帧吃尸等待；Fn059_BloodRaven 的 AI command 10 保存历史坐标，dwAiParam[2] 保留
返回锚点状态，并限制特殊技能累计次数。本批只保存现有宿主表示；NativePathIdentity
是防 ECS ID 复用的宿主保护，不宣称原版具有相同 token 序列化格式。

验收扩展到 93 类逐字段往返、继承链字段策略、预检零写入及逐字节截断。真实表/ECS
验证三种 food 引用、新 World 重绑、实际删除/ID 复用、恢复后身份再次变更、缺少覆盖/
身份/显式 World、极大未分配 ID 和非法 wire kind；血乌恢复后返回历史锚点、耗尽预算
不重置；蛙魔在预建碰撞和动作完成边界上保留隐藏决策预算并继续 Emerge。

真实 D2GS factory 的 Regurgitator 在 phase 3 吃尸等待时保存，三种引用语义分别在
新 World 和独立 JVM 续跑 60 步，逐步比较阶段、动作请求、canonical（含原生 RNG）
和 D2S。current/null 首次到期继续吃尸请求，retired 首次到期清除旧引用，不误吃
同 ID 新身份；后续允许正常重新搜索尸体。AI-only witness 变化和现有/空 World 的
晚期 D2S 失败回滚新增该类型，保留 live AI 对象身份。
这些续跑仍显式清理 Casting/Sequence/Pathfind，并不执行完整吃尸技能效果或尸体消耗。

定向命令（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.ai.AiRuntimeStateTest --tests com.riiablo.engine.server.ai.EntityReferenceCheckpointIntegrationTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --continue --no-daemon --max-workers=1 --console=plain
```

最终扩大回归沿用第 35 批全部 AI、checkpoint/factory/状态/目标/RNG/D2S 测试范围，
增加 NativePathIdentityTest，附带三个真实 TCP gate、桌面编译及模块边界检查；
最终结果：core 1042 项、server/d2gs 76 项，共 1118 项通过，0 失败、0 跳过；
桌面编译、verifyModuleBoundaries 与 git diff --check 通过。三个真实 TCP gate 通过：
固定 Tick 7→38（31 步、40ms 步长）、部分拾取后剩余 15 金币且 ownerWindowPreserved=true、
旧 socket 延迟帧 delayedFrameDropped=true 且 generationAwareCommand=true。
TCP gate 仍为同进程，不代表真实 socket 跨进程接管完成。

剩余：旧 FSM、Npc/Navi/NecroPet/Hireable 等 AI，完整 Casting/Sequence/冻结战斗记录和
Item 引用、ECS 路径/NativePathRuntime 游标、MovementModes/飞行碰撞/骑乘物理、
DiabPrison 关系、AssassinTrapSystem 控制器等仍需独立适配。隐藏阶段字段支持不代表
蛙魔碰撞/held S1 动作已整体恢复，DeathSentry 的字段支持也不代表控制器已恢复。
任务、Map/RoomEx、本地实体、交易、未绑定 RNG、资源 digest、结构订阅副作用及
真实 socket 跨进程接管继续保留；M8-A、M8-D、M8 总体仍进行中。

### M8-A 第 37 批：旧 FSM 近战、冲锋及再生记忆恢复（2026-10-26）

本批集中补齐 Baboon、Bighead、BloodHawk、ClawViper、CorruptLancer、CorruptRogue、
Goatman 七类，累计 100 类实体 AI，另有共享 IDLE。它们统一沿用既有
CheckpointStateMachine，运行时继承 DefaultStateMachine 的原有行为；恢复直接赋值
current/previous/global，不调用 changeState、enter/exit 或 initialize，不重放出生/死亡。
AI v1、wrapper v1、unit-state v4、D2GC v7 不变，原有 93 类字节布局不变。

- 七类均显式保存 nextAction/time 以及三段 FSM 历史；校验机器精确类型和 owner，
  未声明派生类、未知状态名和非法数值继续拒绝。nextAction 必须有限，time 保留
  正无穷 sleep sentinel，拒绝 NaN/负无穷；负计时余量保持原值，不做夹取或重排时钟。
- Baboon 保存 aiParam0/1/2，即再生剩余决策数、攻击记忆、待移除的再生增量。
  增量字段和既有 unit-state 属性镜像组合恢复，不在恢复时重新增加 hpregen；
  后续原有决策到期才扣减保存的增量，不能把其他来源的再生一起清零。
- BloodHawk/ClawViper 保存 aiParam0 冲锋记忆；CorruptLancer 保存 ranToTarget，
  不丢失跑近后下一次近战决策的连续性。Bighead/CorruptRogue/Goatman 没有额外私有
  持久标量。tmpVec2 均在使用前完整覆盖，是 scratch，不复制源向量。
- enemyEntities 是目标 World 初始化后的实时订阅，恢复保留原对象，不复制源 World、
  mapper 或订阅；这些宿主仍须按目标服务器上下文正常初始化。

原版核对：D2MOO AiThink.cpp Fn011_Baboon 明确保留 dwAiParam[0] 再生计数、[1] 攻击
记忆及 [2] 增量，到期或健康恢复时扣减保存的增量；Fn005_BloodHawk 保留冲锋后近战
优先状态；Fn036_CorruptLancer 跑近后保存 dwAiParam[0]，下一近战决策消耗该记忆。
FSM 名称、time/nextAction 是宿主现有表示，本批不将 checkpoint 扩大成原版行为修正。

验收扩展到 100 类逐字段往返、继承字段策略、预检零写入、逐字节截断和 FSM 历史。
七类真实表/ECS 验证剩余等待、目标 AI/机器/临时向量/实时订阅对象身份、历史回退到
DEAD 后 update/kill 不重复触发死亡动作；另验证狒狒的待移除再生增量与属性镜像组合，
以及 Lancer 跑近记忆保留至下一次近战。codec 解析/预检/恢复不读取或替换全局随机源。

真实 D2GS factory 的 Baboon 在待移除再生加成且 time 尚余 0.35 秒时保存，新 World
与独立 JVM 各续跑 60 步，逐步比较动作请求、FSM、canonical（含原生 RNG）和 D2S，
验证等待到期后 hpregen 从 51 降至 40 且不重复扣除，随后仍能继续近战决策。
AI-only witness 变化、现有/空 World 晚期 D2S 失败回滚加入该类型，保留 live AI 身份。

边界：若干旧 AI 分支仍调用 MathUtils 全局随机源，该源未由本批接入 checkpoint。
Baboon 的 D2GS 续跑选用有目标的原生 unit-seed 分支；Lancer 定向用例显式预建外部
随机源，并不声称恢复了它。其他六类的字段恢复不等于所有随机分支已跨进程确定性闭合。
Casting/Sequence/Pathfind 仍在受控动作完成边界清理，不代表完整技能、路径或碰撞恢复。

定向命令（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.ai.AiRuntimeStateTest --tests com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --continue --no-daemon --max-workers=1 --console=plain
```

最终扩大回归沿用第 36 批全部 AI、checkpoint/factory/状态/目标/RNG/D2S 和身份生命周期
测试范围，附带三个真实 TCP gate、桌面编译及模块边界检查。
最终结果：core 1053 项、server/d2gs 77 项，共 1130 项通过，0 失败、0 跳过；
桌面编译、verifyModuleBoundaries 和 git diff --check 通过。三个真实 TCP gate 通过：
固定 Tick 5→36（31 步、40ms 步长）、重连后部分拾取剩余 15 金币且 ownerWindowPreserved=true、
旧 socket 延迟帧 delayedFrameDropped=true 且 generationAwareCommand=true。
TCP gate 仍为同进程，不代表真实 socket 跨进程接管完成。

剩余旧 FSM（含 CorruptArcher 的 missile 表引用）、Npc/Navi/NecroPet/Hireable 等 AI、
全局/未绑定 RNG、完整 Casting/Sequence/冻结战斗记录与 Item 引用、路径游标、
MovementModes/飞行碰撞/骑乘物理、DiabPrison/AssassinTrapSystem 控制器、任务、
Map/RoomEx、本地实体、交易、资源 digest、结构订阅副作用及真实 socket 跨进程接管
仍需后续适配；M8-A、M8-D 和 M8 总体继续进行中。

### M8-A 第 38 批：旧 FSM 恢复、循环及沙虫阶段记忆（2026-10-26）

本批集中补齐 Mummy、MaggotLarva、PinHead、PantherWoman、Wraith、Fetish、
SandMaggot 七类，累计 107 类实体 AI，另有共享 IDLE。继续使用精确类白名单和
CheckpointStateMachine，恢复直接赋值 current/previous/global，不触发状态回调、
initialize、出生或死亡动作。AI v1、wrapper v1、unit-state v4、D2GC v7 不变，
原有 100 类编码布局不变；不改变当前宿主玩法、参数或 RNG 调用顺序。

- 七类显式保存 nextAction/time 和三段 FSM 历史，验证 machine 类型/owner、枚举和
  有限 nextAction；time 保留正无穷 sentinel，拒绝 NaN/负无穷，不夹取负计时余量。
- MaggotLarva 保存 aiParam0 恢复标记，恢复后近战先消耗该标记进入等待，不凭空
  发起攻击或额外抽全局随机数。Fetish 保存 aiParam0/1 阶段与循环次数，保留循环
  边界后的逃跑决策，不从正常阶段或循环零点重新开始。
- SandMaggot 保存 aiParam0/1/2 阶段、时限记忆和已产卵数量；ready-to-lay 到期
  才请求既有 Skill3，成功后只消费一次额度；潜地状态保持等待及配置的出土技能。
  不在恢复时执行技能、孵蛋、回血或重新计算时限。
- Mummy、PinHead、PantherWoman、Wraith 没有额外私有持久标量。scratch tmpVec2、
  mapper、World 和实时订阅不进入镜像；保留目标 AI/机器/向量/订阅对象身份，
  PantherWoman 的敌方及怪物群订阅均保留目标 World 的初始化结果。

原版依据：D2MOO source/D2Game/src/AI/AiThink.cpp 的 Fn009_Wraith、
Fn015_SandMaggot、Fn018_PantherWoman、Fn021_Mummy、Fn030_Fetish、
Fn038_MaggotLarva 和 Fn039_PinHead。Fn038 的非零 dwAiParam[0] 会短路概率判定
并清标记进入恢复；Fn030 保存阶段/循环次数；Fn015 保存阶段、绝对帧时限及产卵数。
本批只保存当前宿主表示：沙虫现有 aiParam1/出入土时限 TODO 不因此等于原版绝对帧；
PinHead 当前未实现原版 dwAiParam[0] 攻击记忆，Mummy 的唤醒距离分支、
PantherWoman 的原版 base-id 群体过滤、Fetish 原版目标生命比例/逃跑分支仍有
既有差异，另属行为对齐工作，不能由 checkpoint 字段往返推导为已完成。

验收覆盖 107 类逐标量、字段策略、预检零写入、逐字节截断及 FSM 历史。
七类真实表/ECS 验证延迟续跑、目标宿主对象身份和回退 DEAD 不重复死亡。
新增幼虫恢复先于 RNG、Fetish 循环到期逃跑、沙虫待产卵额度只消费一次/封顶、
潜地等待与配置出土技能、PantherWoman 双订阅及三个简单近战 AI 到期恢复用例。
codec 构造/解析/预检/恢复不读取或替换 MathUtils 全局 RNG。

真实 D2GS factory 的 sandmaggot1 在 BURROW、aiParam0/1/2=3/47/2、
time=0.35 秒时写盘，新 World 和独立 JVM 各续跑 60 步；近处目标使其在第 9 步
请求出土，随后显式把玩家移远，使下一阶段请求潜地，逐步比较动作、FSM、
canonical（含原生 RNG）和 D2S，并验证时限记忆/产卵数未被重置。
将其加入 AI-only witness 变化、私有状态篡改校验、现有/空 World 晚期 D2S
失败回滚验收；失败后 canonical 不变且现有 live AI 对象保留。

边界：MathUtils 全局随机源仍未纳入 checkpoint；沙虫跨进程轨迹主动选择不经过
该源的潜地/出土分支，其他概率用例显式提供测试外部 RNG，不声称随机源被恢复。
Casting/Sequence/Pathfind 仍在受控动作完成边界清理，本批不包含真实技能关键帧、
产卵实体创建、潜地治疗或完整路径/碰撞续跑，也不覆盖所有旧 AI 概率分支。

定向命令（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.ai.AiRuntimeStateTest --tests com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest --tests com.riiablo.engine.server.ai.LegacyPhaseCheckpointIntegrationTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --continue --no-daemon --max-workers=1 --console=plain
```

扩大回归沿用第 37 批范围，并加入 Act1MonsterEcsScenarioTest 沙虫等既有技能关键帧
回归，附带桌面编译、模块边界和三个真实 TCP gate。最终结果：core 1084 项、
server/d2gs 78 项，共 1162 项通过，0 失败、0 跳过；桌面编译、模块边界和
git diff --check 通过。TCP 门禁验证固定 Tick 5→36（31 步、40ms）、
重连后部分拾取剩余 15 金币且 ownerWindowPreserved=true、旧 socket 延迟帧
delayedFrameDropped=true 且 generationAwareCommand=true；仍是同进程门禁，
不代表真实 socket 跨进程接管完成。

剩余旧 FSM 为 CorruptArcher、FallenShaman、FetishShaman、SandLeaper、
SandRaider、SkeletonBow、SkeletonMage、Vampire；还包括 Npc/Navi/NecroPet/Hireable
等 AI、全局/未绑定 RNG、完整 Casting/Sequence/冻结战斗记录与 Item 引用、
路径游标、MovementModes/飞行碰撞/骑乘物理、控制器、任务、Map/RoomEx、
本地实体、交易、资源 digest、结构订阅副作用和真实 socket 跨进程接管。
M8-A、M8-D 和 M8 总体继续进行中。

### M8-A 第 39 批：剩余旧 FSM、远程表绑定及 World 订阅收口（2026-10-26）

本批合并 CorruptArcher、FallenShaman、FetishShaman、SandLeaper、SandRaider、
SkeletonBow、SkeletonMage、Vampire 八类，累计 115 类实体 AI，另有共享 IDLE。
AI 包不再直接构造 DefaultStateMachine，统一使用 CheckpointStateMachine；
这是旧 FSM 私有字段检查点适配收口，不是全部 AI/原版行为/完整 ECS 恢复完成。
AI v1、wrapper v1、unit-state v4、D2GC v7 不变，原有 107 类布局不变。

- 八类保存 nextAction/time 及 current/previous/global 历史；恢复不调用 initialize、
  状态回调或动作事件。owner、未知枚举、非有限 nextAction 和 NaN/负无穷 time
  继续拒绝；正无穷 sleep sentinel、负计时余量保留，不重排决策。
- SandRaider 保存 aiParam0/1/2 蓄力计数、充能标记及受伤逃跑尝试数；
  Vampire 保存 aiParam0/1/2 阶段、距离记忆和升级技能冷却决策数。SkeletonMage
  保存 targetId（-1 或非负），沿用下一决策的既有有效性重查，不新增硬引用或
  lifetime 保证。其余五类没有额外私有持久标量。
- CorruptArcher/SkeletonBow/SkeletonMage 的 missile 是初始化派生的表绑定，
  镜像记录 presence、Id、Missile key、Range。纯解码只校验结构，不访问资源表；
  恢复预检要求目标已初始化绑定与记录一致，保留目标 row 对象，不构造/覆盖源表行。
  缺失/多出绑定或 ID/key/Range 不符均在写入 AI 字段前拒绝。此局部校验不是完整
  内容 digest，不能保证未记录列或其他资源一致，资源版本锁仍待后续。
- SandRaider 的 enemyEntities 原为静态缓存，改为实例持有，通过注入的 World
  初始化，避免第二个 World 使用第一个 World 的订阅。两 World 并存和目标
  Class 移除用例验证隔离；恢复不覆盖订阅或 World。其他实时订阅和 scratch
  向量不进入镜像；FallenShaman.targetDistance 每次使用前重置，亦属 scratch。

原版依据：D2MOO source/D2Game/src/AI/AiThink.cpp 的 Fn008_SandRaider、
Fn013_FallenShaman、Fn017_SandLeaper、Fn028_Vampire、Fn035_CorruptArcher、
Fn037_SkeletonBow、Fn064_SkeletonMage、Fn065_FetishShaman。
Fn008 按决策累加蓄力并保存充能/逃跑次数，Fn028 按决策递减升级技能冷却，
Fn013/Fn065 通过技能请求复活，实际复活仍由关键帧执行；Fn017 近战使用 A2。
本批不修既有玩法差异：SandRaider 的充能技能/友军逃跑搜索和颜色状态仍有 TODO，
Vampire special-state/逃跑分支、弓手即时反应和其余宿主与原版的差异仍需行为审计。

验收扩展到 115 类字段往返、字段策略、纯解码/预检零写入、逐字节截断、
FSM 历史及 owner/枚举/计时拒绝。八类真实表/ECS 验证等待、目标 AI/机器/向量/
实时订阅身份和 DEAD 回退不重复死亡。新增导弹表 distinct-object 绑定保留及
presence/ID/key/Range 不符零写入、严格 presence/负 ID/截断拒绝；蓄力到期、
冷却到期、萨满到期才请求目标 World 尸体复活且未提前消费尸体、保留 live Leap
时不重发跳跃。首次扩大参数化夹具将目标移出 aidist，触发现有 stopMovement；
改为保留有效目标并给弓手设置待恢复 ATTACK 阶段，继续严格检查等待期间无路径调用，
未修改生产决策或放宽断言。

真实 D2GS factory 验证三类远程 AI 的非空导弹表绑定在空/已有 World 恢复后保持；
SandLeaper 在 ATTACK、time=0.35 秒时写盘，新 World 和独立 JVM 各续跑 60 步，
逐步比较动作、FSM、canonical（含原生 RNG）和 D2S，并加入 AI-only witness、
私有计时篡改校验、现有/空 World 晚期 D2S 失败回滚。受控参数使其只走原生 unit-seed
近战分支，其他概率用例使用显式外部 MathUtils RNG；codec 不消费/替换该全局源。

边界：未恢复完整 Casting/Sequence/Leap/Pathfind、技能关键帧和冻结战斗记录；
live Leap 用例仅验证不覆盖既有空中组件，不证明跨 World 空中轨迹恢复。
萨满仅验证复活请求/延迟，不证明中途复活事务恢复。D2GS 仍在受控动作完成边界
清除动作/路径，不声称所有分支或全局 RNG 跨进程确定性闭合。

定向命令（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.ai.AiRuntimeStateTest --tests com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest --tests com.riiablo.engine.server.ai.LegacyRangedCheckpointIntegrationTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --continue --no-daemon --max-workers=1 --console=plain
```

扩大回归沿用第 38 批范围，附带桌面编译、模块边界和三个真实 TCP gate。
最终结果：core 1100 项、server/d2gs 80 项，共 1180 项通过，0 失败、0 跳过；
桌面编译、模块边界扫描及其 9 项工具自测、git diff --check 通过。
TCP 门禁验证固定 Tick 6→37（31 步、40ms）、重连后部分拾取剩余 15 金币且
ownerWindowPreserved=true、旧 socket 延迟帧 delayedFrameDropped=true 且
slotReuse=true、generationAwareCommand=true；仍是同进程 TCP 门禁，
不代表真实 socket 跨进程接管完成。

后续：Npc/Navi/NecroPet/Hireable 等非旧 FSM AI、全局/未绑定 RNG、
完整动作/跳跃/路径游标/冻结战斗记录与 Item 引用、MovementModes/飞行碰撞/骑乘物理、
控制器、任务、Map/RoomEx、本地实体、交易、内容 digest、结构订阅副作用及真实
socket 跨进程接管仍待适配。M8-A、M8-D 和 M8 总体继续进行中。

### M8-A 第 40 批：NPC / Navi / NecroPet / Hireable 私有状态与绑定边界（2026-10-26）

本批合并四类非旧 FSM AI，累计 119 类实体 AI，另有共享 IDLE。
继续使用精确类白名单，不接受任意子类；AI v1、wrapper v1、unit-state v4、
D2GC v7 不变，原有 115 类布局不变，不改变服务端工厂的 NPC 选择或战斗决策。

- Npc 保存 targetId（DS1 巡逻点索引，不是实体 ID）、actionTimer、actionPerformed
  和诊断 state。actionTimer 允许交互结束后遗留的正无穷等待及负余量，拒绝 NaN/
  负无穷；游标要求至少 -1，绑定非空路径时必须小于 numPoints。
- Npc.name/monstats 是目标初始化派生绑定；镜像见证 name、表行 presence/hcIdx/Id，
  预检一致后保留目标对象。DS1.Path 仅见证 presence、点数、x/y 及每点 x/y/action，
  不构造或覆盖目标路径。点数限 1024，拒绝负数、缺点或空点；目标路径缺失、多出、
  坐标、顺序或动作不符均在 AI 写入前拒绝。此绑定不是完整资源内容 digest，亦不
  包含 Pathfind 动态轨迹、寻路游标、碰撞或 NPC 任务 MapAI 命令的完整恢复。
- Npc/Navi 的 Interactable.count 非零时，采集和恢复预检拒绝，避免把活动对话、
  交易或服务会话当作私有计时恢复。World、mercenaries 订阅、派生表行和 scratch
  向量均保留目标身份；恢复不调用 initialize、interact、路径搜索或动作回调。
  此防护仅适用于这两类 AI，不是共享 IDLE 城镇 NPC 或全部服务会话的覆盖声明。
- Navi 保存 nextThink、burstPause、burstRemaining 及独立 attackRng 的 presence、
  stream/context/state/drawCount。保留零 RNG 状态、消费次数和 null/lazy 语义，
  不重新播种或消费随机数；已存在同身份 RNG 对象原地恢复。严格拒绝非法布尔、
  非 legacy/-1 身份、负消费次数、负/非有限 pause 和不在 0..2 的剩余射击数。
  这仍是宿主已有的兼容 LCG，并未改为原版 pSeed 算法。
- NecroPet 保存 skillBranch/skillBranchConfigured、有限 nextThink、软目标 targetId
  及非空诊断 state。恢复不重新推导技能分支；延迟到期后沿用目标/主人有效性重查，
  不增加跨 lifetime 引用保证。Hireable 没有新增私有字段，仍是被动标记；雇佣兵
  的跟随/战斗由专用系统驱动，不因本批 codec 接入而宣称它们的计时器已全部恢复。

原版依据：D2MOO source/D2Game/src/AI/AiThink.cpp 的 Fn032_Npc、
AITHINK_ExecuteMapAiAction、Fn058_Navi、Fn061_Hireable、Fn067_NecroPet。
原版 MapAI 依赖地图节点/命令和任务特例；Fn058 在交互时等待，检测友方玩家并维护
喊话计数，再选择 25 范围内目标攻击；Fn067 用 AI-control param0 选择普通/技能分支，
并依赖主人与目标。宿主的巡逻计时、Navi 连射/暂停/伤害补偿和独立 LCG、宠物分支
仍有既有行为差异，本批保存实际宿主状态，不宣称与原版行为完全等价。

验收覆盖 119 类字段策略、逐标量往返、预检零写入和截断拒绝；新增两类 NPC 的
真实 World 表/路径绑定保持与不匹配拒绝、活动交互拒绝、等待到期后巡逻点选择续跑，
Navi 的零状态/已有随机流对象/空随机流及 100 步跨连射暂停续跑、不访问 MathUtils；
NecroPet 的两类分支/显式配置标记、延迟、主人失效、warp 重置与死亡不重复触发，
并用真实技能表行验证恢复后的普通近战/技能分支决策。Hireable 恢复后保持无动作。

真实 D2GS 工厂验证 Navi/NecroPet/Hireable 在空/已有 World 恢复且 live AI 对象
不替换；普通城镇 Npc 仍按现有工厂策略使用共享 IDLE，本批明确验证保留此策略。
Npc 巡逻镜像只在目标已初始化且绑定一致的 World 验证，不宣称工厂已创建巡逻 NPC
或已支持从空 World 重建 DS1 地图路径。
Navi 在待续 burstPause=0.3、nextThink=0.15、burstRemaining=1 时写盘，新 World
和独立 JVM 各续跑 100 步；逐步比较动作、canonical（含 AI 私有 LCG、原生 unit RNG）
和 D2S，核对真实箭实体增量等于射击次数。其私有暂停变化进入 AI-only witness、
有效 nested 计时篡改拒绝及现有/空 World 晚期 D2S 失败回滚。受控夹具显式清理
Casting/Sequence/Pathfind，不代表箭命中、全动作关键帧或真实 socket 跨进程接管。

定向命令（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.ai.AiRuntimeStateTest --tests com.riiablo.engine.server.ai.NpcAndPetCheckpointIntegrationTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --continue --no-daemon --max-workers=1 --console=plain
```

扩大回归沿用第 39 批范围，附带桌面编译、模块边界和三个真实 TCP gate。
最终结果：core 1111 项、server/d2gs 82 项，共 1193 项通过，0 失败、0 跳过；
桌面编译、模块边界扫描及其 9 项工具自测、git diff --check 通过。
TCP 门禁验证固定 Tick 6→37（31 步、40ms）、重连部分拾取剩余 15 金币且
ownerWindowPreserved=true、旧 socket 延迟帧 delayedFrameDropped=true 且
slotReuse=true、generationAwareCommand=true；仍是同进程 TCP 门禁，
不代表真实 socket 跨进程接管完成。

后续重点转向全局/未绑定 RNG、完整 Casting/Sequence/Leap/动态路径游标、冻结战斗
记录与 Item 引用、召唤/雇佣兵控制器与系统计时、MovementModes/飞行碰撞/骑乘物理、
任务、Map/RoomEx、NPC 服务/交易、本地实体、完整内容 digest、结构订阅副作用及
真实 socket 跨进程接管。M8-A、M8-D 和 M8 总体继续进行中。

### M8-A 第 41 批：雇佣兵组件与伴随单位系统重试时钟（2026-10-26）

本批合并雇佣兵组件、跟随/技能重试、召唤物重聚重试的恢复适配。
TARGETS 升至 v3，内嵌 companion-runtime v1；AI v1、wrapper v1、unit-state v4、
D2GC v7 不变。旧 TARGETS v1/v2 仍可读取，companionRuntime=null 明确表示未覆盖，
不会用缺失镜像清空目标雇佣兵或系统时钟。

- 保存 Mercenary 的 presence、ownerId、mercType、原始 hirelingId（保留 -1 回退）、
  level/seed/nameId、experience/flags、progression/equipment revision、六槽 skills/
  skillLevels，以及 aiRngState、aiChanceParam、targetId。零 RNG 状态与原始机会参数
  不被重新播种或重算；现有组件及 final 数组原地写回。缺失组件按镜像创建/移除，
  不调用 set、技能初始化、雇佣/复活服务、目标插入或战斗动作。
- nativeTargetNode/private fallback 不作为独立对象图复制，仍由 TARGETS 恢复共享
  节点和所属 World 的事件域；采集拒绝 detached membership。owner 必须在镜像内，
  当前活跃 target 不能漏出镜像；targetId 仍是会在下一次技能决策重查的软 ID，
  不新增跨 lifetime 引用保证，已失效的软目标可以保留。
- MercenaryFollowSystem.repathCooldown、MercenarySkillSystem.cooldown、
  SummonedPetSystem.regroupRetry 全量保存，包括空 map、未存条目与显式零值的区别。
  浮点保留原始有限位型（包括负零），拒绝负余量、NaN/Infinity、重复/负 ID、
  未覆盖时钟宿主。完整替换目标时钟，不 tick、不消费 RNG、不改现有重试算法。
- 镜像见证三个系统的 presence，恢复要求装配一致；所有实体和计时宿主先预检，
  再允许 TARGETS 回退 sequence 或修改节点/组件。跟随时钟要求具备 Position/
  MapWrapper 的雇佣兵或有效 Valkyrie；技能时钟要求 Mercenary/Monster/Position；
  召唤物重聚时钟要求目标已有 SummonedPet 控制器。本批不凭时钟构造召唤物。
- 修复 MercenarySkillSystem 缺少 removed 清理：移除/删除宿主后清空技能冷却及
  日志节流条目，避免回收 ID 继承旧冷却。日志节流、计数器与 scratch 向量不是
  游戏权威时钟，不进入镜像，也不以它们宣称诊断输出可逐字回放。
  三个系统额外监听全部实体的删除：工厂实体已发布、checkpoint 新增控制器尚未
  进入各组件 aspect 时发生回滚删除，也必须清除刚恢复的时钟，不能只依赖
  IteratingSystem.removed。新增此边角的定向测试及空 World 晚期失败回滚回归。
- D2GS 将 MercenaryP 作为网络投影，避免反序列化落入尚未实现的 getData；
  TARGETS v3 的完整 Mercenary overlay 负责恢复。在构造/读盘 checkpoint 时，
  对网络投影与运行态的 presence、身份、经验、flags 和 revision 做交叉校验，
  拒绝冲突/重复投影。运行态与时钟进入现有 TARGETS canonical 摘要和文件见证。
  legacy 无 companion overlay 的包仍仅是子集，不获得完整雇佣兵恢复保证。
- 扩大回归发现并修复既有 legacy 地图跨 Zone 重聚落点缺口：预留落点必须按主人
  的目标 Zone 过滤阻挡者，不能按宠物旧 Zone 忽略已抵达的前一只宠物。沿用现有
  多狼回归验证同/跨 Zone 的分离、阻挡和刷新后占位；原生 RoomEx/shared-bit 分支
  保持不变，此项不宣称完整原生跨区碰撞恢复。

原版依据：D2MOO source/D2Game/src/AI/AiThink.cpp 的 Fn061_Hireable、
D2GAME_PETAI_PetMove_6FCE3EE0、sub_6FCE4610、sub_6FCE4830。
原版依赖主人、AI-control param0、unit pSeed、Hireling.txt 和调度事件；本批保留
宿主已有独立兼容 RNG 与浮点重试计时，不改用原生 pSeed，也不宣称调度/战斗行为
与原版完全等价。

验收：Mercenary 全字段策略守卫、逐标量/数组/共享节点往返、组件 presence/absence、
零 RNG 与 -1 hirelingId、计时负零/空 map、World 隔离、完整截断与非法镜像拒绝、
系统装配/晚宿主缺失的预检零写入、旧 v1/v2 未覆盖语义、删除清理和 ID 回收；
真实 ECS 验证跟随冷却同 Tick 到期、技能重试/RNG 100 步续跑，召唤重聚重试在
目标已有控制器时保持到期语义，缺控制器时拒绝。

真实 D2GS 工厂验收包括雇佣兵在新/已有 World 恢复、组件及 final 数组身份保持、
新 World/独立 JVM 各 100 步继续真实 MercenarySkillSystem/Actioneer 决策，逐步
比较 canonical、原生 unit RNG 和 D2S；私有机会参数改变摘要但不改变网络 payload，
有效 nested 目标 ID 篡改被文件见证拒绝，晚期 D2S 失败回滚现有/空 World 的
实体、组件、时钟、TARGETS、RNG、D2S 与分配器。夹具主动结束 Casting/Sequence/
Pathfind，不代表完整动作关键帧、命中/伤害或寻路游标验收。

边界：完整 SummonedPet（spawnOrder 分配器、时长/死亡、陷阱/Inferno/Blade 控制、
Iron Golem sourceItem、骨墙 controller）、MercenaryManager 的 active playerMercs/
装备 Item 引用、NPC 候选名单及刷新时钟、NativeMercenaryRewardSystem 服务绑定、
主人 CombatPositionHistory 路径环尚未覆盖；不能把完整 Mercenary 组件等同于
完整雇佣服务/装备/控制器恢复。NPC 交互/交易与真实 socket 跨进程接管仍不在本批。

定向命令（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.replay.CompanionRuntimeCheckpointTest --tests com.riiablo.engine.replay.TargetRuntimeCheckpointTest --tests com.riiablo.engine.server.SummonedPetSystemTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --continue --no-daemon --max-workers=1 --console=plain
```

扩大回归沿用第 40 批范围，增加 companion runtime、雇佣/名单/原生经验表、
Mercenary/SummonedPet serializer、跟随/重聚及 DynamicUnitCollisionSystem/
UnitCollisionGrid 回归，并附带桌面编译、模块边界和三个真实 TCP gate。
最终结果：core 1207 项、server/d2gs 85 项，共 1292 项通过，0 失败、0 错误、
0 跳过；桌面编译、模块边界扫描及其 9 项工具自测、git diff --check 通过。
TCP 门禁验证固定 Tick 5→35（30 步、40ms）、重连部分拾取剩余 15 金币且
ownerWindowPreserved=true、旧 socket 延迟帧 delayedFrameDropped=true 且
slotReuse=true、generationAwareCommand=true；仍是同进程 TCP 门禁，
不代表真实 socket 跨进程接管完成。M8-A、M8-D 和 M8 总体继续进行中。

### M8-A 第 42 批：召唤物运行态、World 配额与 Inferno 续跑（2026-10-26）

本批集中补齐 SummonedPet 的标量控制器镜像、组件 presence/absence、配额高水位和
已有重聚时钟的联合恢复。companion-runtime 升至 v2，内嵌 summon-runtime v1；
TARGETS v3、AI v1、wrapper v1、unit-state v4、D2GC v7 不变。
companion v1 仍可读取，summonRuntime=null 表示未覆盖，不清空现有召唤物或配额。

- 显式保存 owner/type/skill、被动标记、duration/elapsed、spawnOrder、unsummon
  标记/原因、networkReplica、死亡标记/deadFrames、陷阱预算/已发射数/攻击余量、
  Blade 方向/原点/目标坐标/关联 missile ID、Inferno 的 frame/sequence point/
  end/remaining/pulse/target、Death Sentry 尸体记忆及骨墙 controller ID。
  所有浮点保留有限原始位型（包括负零）；攻击、Inferno remaining 和 pulse cooldown
  是可合法为负的 due counter，不截断归零。共享 NativeTargetNode 仍由 TARGETS
  恢复和重绑，不保存 detached membership，不调用 set、插入目标节点或重新触发技能。
- 生产 set 在绑定节点后使用所属 NativePlayerTargetSlots 的独立 petSpawnSequence，
  不再消费进程级静态配额计数。quota 与 TARGETS 插入序列分开，保存已退休插入的
  高水位，不能从现存宠物最大 spawnOrder 重建；其他 World 和客户端不能干扰配额。
  目标 World 若还存在镜像外活召唤物，直接恢复也必须在零写入预检拒绝，不能围绕
  未覆盖宠物回退配额；D2GS 正常事务先收敛网络实体集合，再应用此完整 World 镜像。
  detached/custom-world 兼容调用保留旧序列，但不能冒充可恢复的生产 World 镜像。
  零值/组件缺失保持原义；正 spawnOrder 必须唯一且不超过保存的高水位，溢出拒绝。
- 先预检所有宿主、覆盖范围、共享节点、配额、有限数值和 Item 图边界，再写组件、
  配额及系统时钟；已有 SummonedPet 对象原地恢复，缺失时创建，镜像缺失时移除。
  重聚/女武神跟随时钟依据待恢复控制器预检，允许空 World 的正常工厂怪物在
  TARGETS 阶段获得控制器，不再要求目标 World 预先拥有召唤物组件。
- SummonedPetP 仅为网络投影，保持协议不变；D2GS 对 presence、owner/type、实际
  ushort skill 投影及派生 unsummonable 标记交叉验证，拒绝重复/冲突。运行态和
  quota 进入既有 canonical 摘要与文件见证，合法篡改高水位也会被外层见证拒绝。
- Iron Golem 的非空 sourceItem 暂不复制：采集明确拒绝，恢复到携带该未覆盖图的
  活对象也在预检拒绝，避免悄悄丢弃 Item 身份/属性/装备关系。其余字段有反射策略
  守卫，新增字段不能无声漏出 codec。关联目标、尸体、Blade missile、骨墙控制器
  保留原有软 ID 语义，活引用须被镜像覆盖；不新增跨 lifetime 引用安全保证。

原版核对：D2MOO PlayerPets.cpp:260 的移除、:468 的配额满时移除链表头再尾插，
AiThink.cpp:13880 的 Fn104 尸体记忆，SkillMonst.cpp:972 的 SrvDo095，以及
SkillNec.cpp:1693/1811 的 MONUMOD_KILLMINIONSDEATH。本批恢复宿主现有表示，
不改原有配额、死亡宽限、技能伤害公式，不将兼容浮点计时宣称为原版调度器复刻。

验收：全字段往返、对象/共享节点身份、组件增删、空配额/退休高水位/World 隔离/
溢出、负 due counter/负零、全截断/非法长度/布尔/数值/重复顺序拒绝、旧 companion
v1 未覆盖语义、晚宿主缺失和 sourceItem 预检零写入；真实 SummonedPetSystem 验证
到期、死亡宽限的同 Tick 退出和骨墙控制器联动。

真实 1.10f D2GS factory 验收在 Inferno 序列第 4 帧写盘，新 World 与独立 JVM 各续跑
60 Tick，逐 Tick 比较 canonical（含 unit RNG、状态、导弹、控制器、配额）与 D2S，
实际生成脉冲并在预算耗尽后移除陷阱；同时验证召唤物死亡、骨墙控制器/从属段移除，
恢复后下一 quota 分配保持一致。已有 World 保留组件对象身份；晚期 D2S 身份失败
回滚现有/空 World 的实体、控制器、TARGETS、重聚时钟、quota、RNG、D2S 和分配器。

定向命令（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.replay.SummonRuntimeCheckpointTest --tests com.riiablo.engine.replay.CompanionRuntimeCheckpointTest --tests com.riiablo.engine.replay.TargetRuntimeCheckpointTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --no-daemon --max-workers=1 --console=plain
```

扩大回归覆盖 AI、checkpoint、状态、目标、召唤/陷阱/骨墙/佣兵、碰撞、真实 factory、
RNG/D2S 及生命周期：core 1214 项、server/d2gs 88 项，共 1302 项通过，0 失败、
0 错误、0 跳过；最终 quota/companion 39 项及真实 factory 53 项定向复测也全部
通过（不重复计入总数），包括镜像外召唤物的零写入预检。
桌面编译、模块边界扫描及 9 项工具自测、git diff --check 通过。

```text
./gradlew.bat :server:d2gs:headlessSimulationTick :server:d2gs:headlessReconnectGroundLoot :server:d2gs:headlessStaleTransport :server:d2gs:headlessSummonReconnect --continue --no-daemon --max-workers=1 --console=plain
```

四个真实 TCP gate 全部通过：固定 Tick 5→36（31 步、40ms）；部分拾取重连剩余
15 金币且 ownerWindowPreserved=true；旧 socket delayedFrameDropped=true、
slotReuse=true、generationAwareCommand=true；骷髅双客户端/观察端重连验证
ownerSummonPreserved=true、observerSnapshotReplay=true。它们仍为同进程 TCP
门禁，不代表真实 socket 跨进程接管完成。

边界：sourceItem/装备 Item 图、MercenaryManager 服务/名单/刷新时钟/奖励绑定、
主人 CombatPositionHistory、完整 Casting/Sequence/Leap/动态路径游标/冻结战斗记录、
MovementModes/飞行碰撞/骑乘物理、任务、Map/RoomEx/NPC 服务/交易、本地实体、
全局/未绑定 RNG、完整内容 digest、结构订阅副作用及真实 socket 跨进程接管仍未闭环。
Blade 控制字段恢复不等于完整路径/命中验收；Inferno 门禁不是全技能动作关键帧验收。
M8-A、M8-D 和 M8 总体继续进行中。

### M8-A 第 43 批：战斗位置历史、主人路径环与采样时钟恢复（2026-10-26）

本批集中补齐 CombatPositionHistory 的完整历史环和独立采样系统时钟，
不改历史近战距离/碰撞或佣兵跟随规则。companion-runtime 升至 v3，内嵌
position-history v1；summon-runtime v1、TARGETS v3、unit-state v4、AI v1、
wrapper v1、D2GC v7 不变。companion v1/v2 仍可读取，历史为“未覆盖”，
不能清空目标 World 的历史或回退其采样时钟；v3 的显式 absence 与未覆盖分开。

- 保存 capacity、latestTick 高水位、每个物理槽的 Tick 和排序后的历史实体记录，
  包括整数 x/y、size、roomId 和可空 zone；保留未初始化槽、已采样空帧、稀疏/
  非单调采样、已删除实体的旧记录。latestTick 的槽被更早 Tick 覆盖也不重新推算
  高水位；不以当前活实体集合重建历史，不补齐空洞，不修改原有软 entity ID 语义。
- Zone 以 level ID、seed、边界、synthetic/town 标记精确匹配目标 Map 中的唯一
  实例；不导入源 World 的 Zone 引用，不以 level ID 单独弱匹配。Map presence、
  环容量、named owner 绑定及采样系统拓扑必须一致，缺失/外来/歧义区域在写入前拒绝。
  这是预建地图上的历史适配，不是 Map/RoomEx、动态碰撞或全世界拓扑恢复。
- 恢复先完整解析和预检，再原地替换既有 ticks 数组/IntMap 槽内容；保留同一个
  History 实例，Actioneer/MercenaryFollowSystem 继续使用其已有注入对象。
  不采样当前坐标、不 Tick、不执行动作或重算距离。采样与恢复对重复 Zone 缓存
  身份解析，避免历史实体数乘以地图区域数的重复扫描。
- 同时覆盖 CombatPositionCaptureSystem 自有 Tick；D2GS 的外部采样沿用已恢复
  AuthoritativeSimulation 时钟，两种装配不混为一个计数器。独立采样时钟耗尽时
  在递增前明确拒绝，不能溢出污染计数器。时钟和历史跟随已有 TARGETS 事务回滚。
- 新格式进入既有 canonical 摘要和文件见证；仅改变历史、网络 payload/D2S/RNG
  不变时摘要仍会变化。严格拒绝截断、尾随数据、非法布尔/长度/容量/槽 Tick/
  ID 顺序/重复 ID/负 size/负采样时钟；镜像持有字节防御性复制，历史上限 16MiB，
  不放宽既有外层容量预算。

原版依据：D2MOO `source/D2Game/src/AI/AiThink.cpp:9518` 的
`D2GAME_PETAI_PetMove_6FCE3EE0`，尤其 :9585 起遍历主人 20 个路径坐标，
并以距离 >5 筛选落点。本批仅恢复现有宿主的兼容历史表示，不将 64 槽
CombatPositionHistory 宣称为原版玩家路径结构复刻，也不改变 PetMove 策略。

验收覆盖：历史全环/空帧/稀疏与乱序/退休实体/整数舍入/roomId/零 size/
World 隔离/容器身份/Zone 重绑；真实历史近战读取；真实 MercenaryFollowSystem
轨迹选择消费旧帧并跳过距离过近的新帧；采样系统恢复后连续推进、旧格式未覆盖、
拓扑不匹配零写入、字段策略守卫及畸形镜像拒绝。

真实 1.10f ServerEntityFactory/D2GS 在预建 synthetic arena 中保存历史并写盘，
新 World 和独立 JVM 各续跑 72 Tick，逐 Tick 比较 canonical（含 unit RNG）与
D2S。真实 Actioneer 在恢复后从旧帧判定近战范围，而不是读取已移远的当前位置；
历史环覆盖整圈后回到原有 missing-snapshot fallback。晚期 D2S 身份失败回滚
已有和空 World 的历史、实体、TARGETS、RNG、分配器、D2S 和时钟，并保留 History
对象身份。此门禁的佣兵跟随/施法系统为受控禁用，不能冒充完整佣兵动作续跑。

定向复现（D2_HOME 指向本地 1.10f 资源）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.combat.CombatPositionCheckpointTest --tests com.riiablo.engine.replay.CompanionRuntimeCheckpointTest --tests com.riiablo.engine.replay.SummonRuntimeCheckpointTest :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --no-daemon --max-workers=1 --console=plain
```

扩大回归复用第 42 批的 AI/checkpoint/目标/召唤/陷阱/佣兵/碰撞/生命周期/RNG/
D2S/真实 factory 范围，并将历史测试过滤扩为 `CombatPosition*Test`：core 1226 项、
server/d2gs 91 项，共 1317 项通过，0 失败、0 错误、0 跳过；其中新增历史单测
12 项，真实 factory 新增 3 项。桌面编译、模块边界检查及 9 项架构工具自测通过。

```text
./gradlew.bat :server:d2gs:headlessSimulationTick :server:d2gs:headlessReconnectGroundLoot :server:d2gs:headlessStaleTransport :server:d2gs:headlessSummonReconnect --continue --no-daemon --max-workers=1 --console=plain
```

四个真实 TCP gate 全部通过：固定 Tick 8→39（31 步、40ms）、部分金币拾取重连、
旧 transport/slot-generation 隔离、召唤物双客户端/观察端重连。它们仍是同进程
TCP 门禁；独立 JVM 验收为上述 checkpoint 文件恢复，不是 socket 跨进程接管。
最终 `git diff --check` 通过；生成 SoundP 的 EOF 空行噪声不提交。

边界：本批不覆盖 sourceItem/装备 Item 图、MercenaryManager 服务/名单/刷新/
奖励绑定、完整 Casting/Sequence/Leap/动态路径游标/冻结战斗记录、MovementModes/
飞行碰撞/骑乘、任务、Map/RoomEx/NPC 交易、本地实体、全局/未绑定 RNG、完整内容
digest、结构订阅副作用及真实 socket 跨进程接管。M8-A/M8-D 和 M8 总体继续进行中。

### M8-A 第 44 批：佣兵服务、NPC 名单与刷新续跑（2026-10-26）

完成日期：2026-10-26；代码提交：`56bc9212e5ab4553e81b6d466fad3289661558e5`。

本批集中覆盖 NativeMercenaryRewardSystem / MercenaryManager 的服务运行态，
将 companion-runtime 升至 v4，内嵌 manager/service v1；TARGETS v3、unit-state v4、
D2GC v7 不变。companion v1/v2/v3 仍可读取，佣兵服务为“未覆盖”，不清空既有服务；
v4 的显式 absence 则要求目标 World 服务拓扑一致。M8 总体仍为进行中。

- 保存管理器逻辑毫秒、难度、名单专用 RNG 的原始 state/drawCount、全局/逐玩家
  NPC 名单和空域、候选顺序/seed/name/hired/level、lastRefreshTime，以及活跃记录的
  owner/entity、定义、生命、等级/经验、雇佣时间；允许死亡记录 entity=-1 且没有尸体。
  定义标量、可空字符串/数组与共享 definition 对象图显式编码，不重新生成候选。
- 真实服务初始化时安装 World 独立的 `hireling-roster` 兼容 LCG，用仿真 delta
  推进逻辑毫秒，保存后跨 600000ms 严格 `>` 刷新边界继续生成相同名单；standalone
  管理器保留既有墙钟/随机入口，但不能当作受管 checkpoint。没有将兼容 LCG
  宣称为原版双字 pSeed，也没有把宿主十分钟刷新策略宣称为原版 NpcRecord 复刻。
- manager restore 复用目标 manager、回调、同 key 活跃记录/名单容器、同 index 候选、
  可复用定义和同形数组；不调用雇佣/扣费/奖励/刷新。回调必须仍属于目标 World 的
  服务，工厂/EventSystem/native table 不复制源对象。native Hireling 表逐字段 SHA256
  和 legacy 经验阈值是恢复依赖身份，不是全游戏内容 digest。
- 实体侧恢复补齐真实佣兵服务对怪物工厂的适配：移除敌对 AI/点击选择宿主，先绑定
  目标 MercData 大型属性容器，再应用 unit-state。D2S 发布后再次重绑共享 stats，
  然后重放属性镜像；不执行 createMercenaryEntity、onMercenaryHired 或免费奖励。
  保存空装备容器的 equipmentRevision；无尸体记录额外保存完整 base/aggregate/
  remaining、aggregateRevision、战斗上下文和 staminaMaximum，不能靠 D2S 头重算。
  带实体的记录必须有 LARGE unit-state 属性镜像；完整捕获原子组装 TARGETS 与
  unit-state，禁止混入未覆盖属性的旧镜像。
- 晚期失败的外层事务直接使用已验证 before-image 恢复实体/分配器/TARGETS，再恢复
  D2S、共享属性和时钟，不给“玩家已删除但服务尚未回滚”的中间态拍新快照。
  不改变正常结构相位，也不触发 Tick、奖励、复活扣费或名单 RNG 消耗。
- manager 上限 8MiB、service 上限 16MiB；字节防御性复制，完整解析、依赖/宿主/覆盖
  预检后再写入。拒绝截断、尾随、非法版本/布尔/count/引用/顺序/时间/难度/修订号，
  以及未覆盖玩家/实体、外来回调和服务拓扑差异。RNG/时钟耗尽不能溢出或重新播种。
  ActiveMercenary.equipment 当前仅支持全零；MercData 的非空 Item 图（含被覆盖旧记录）
  明确拒绝，不丢弃装备或把装备实体引用伪装成已恢复。

原版依据：D2MOO `source/D2Game/src/UNIT/SUnitNpc.cpp:940–1008` 的雇佣流程，
按 NPC record 与名字 slot 取候选、检查 name/bHired，并用原记录 dwSeed 调用
MONSTERS_HirelingInit 后检查/扣除金币。本批保存这些既有候选身份和消耗状态，
不凭经验修改雇佣等级、费用、技能或复活规则。

验收覆盖：manager 全字段/共享定义图/容器身份、候选 seed/hired CAS、刷新边界、
World RNG 隔离、无尸体死亡记录、数据依赖差异、畸形字节和字段策略守卫；服务侧
覆盖只改变服务时钟时 canonical 变化而 payload/D2S 不变、嵌套文件见证篡改拒绝、
旧 v3 未覆盖、Item/实体覆盖/回调归属拒绝及已有/空 World 的晚期失败回滚。

真实 1.10f ServerEntityFactory/D2GS 在预建 synthetic arena 中真实雇佣并写盘，
新 World 与独立 JVM 分别续跑 6 个 40ms Tick，逐 Tick 比较 canonical（含 RNG）
和 D2S，打开既有及新 NPC 菜单跨刷新边界；无尸体分支随后真实付费复活，并验证
共享属性、只扣一次费用、不能重复复活/免费再授予。跟随/施法系统受控禁用，
不能冒充完整佣兵动作/路径续跑；文件跨 JVM 恢复也不是 socket 跨进程接管。

验证（D2_HOME 指向 `G:\BaiduNetdiskDownload\Diablo II 1.10F`）：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.pet.MercenaryManagerCheckpointTest --tests com.riiablo.engine.server.pet.MercenaryManagerTest --tests com.riiablo.engine.server.quest.NativeMercenaryRewardSystemTest --tests com.riiablo.engine.replay.CompanionRuntimeCheckpointTest --tests com.riiablo.engine.replay.SummonRuntimeCheckpointTest --tests com.riiablo.engine.server.combat.CombatPositionCheckpointTest --no-daemon --max-workers=1 --console=plain
./gradlew.bat :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsCheckpoint* --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest :desktop:compileJava --continue --no-daemon --max-workers=1 --console=plain
```

扩大回归复用第 43 批 AI/checkpoint/目标/召唤/陷阱/佣兵/历史/碰撞/生命周期/RNG/
D2S/真实 factory 范围，并加入 `MercenaryManager*Test`、`NativeMercenaryRewardSystemTest`、
`NativeHirelingOfferIntegrationTest`、`NativeHirelingExperienceTableTest`：core 1253 项、
server/d2gs 96 项，共 1349 项通过，0 失败、0 错误、0 跳过；其中新增 manager 单测
10 项、真实 factory 联合测试 5 项。最后的属性覆盖预检/原子捕获调整后，6 项 core
unit-state 测试及完整 96 项 server 回归重新通过，桌面编译再次通过。模块边界检查、
架构扫描及 9 项扫描工具自测通过，`git diff --check` 通过，SoundP EOF 生成噪声不提交。

```text
./gradlew.bat :server:d2gs:headlessSimulationTick :server:d2gs:headlessReconnectGroundLoot :server:d2gs:headlessStaleTransport :server:d2gs:headlessSummonReconnect --continue --no-daemon --max-workers=1 --console=plain
```

四个既有真实 TCP gate 在最终代码上复跑通过：固定 Tick 8→39（31 步、40ms）、部分金币拾取重连、
旧 transport/slot-generation 隔离、召唤物双客户端/观察端重连。额外运行的
`headlessMercenaryLifecycle` / `headlessMercenaryRestore` 暂不计为通过；前者报告
Blood Moor Fallen/Shaman 测试房间不可用，后者等待死亡佣兵登录快照超时。
已在上一提交 `5fac614609b9958c7dbe89bba0b7f8f29edf0279` 的隔离 worktree 中，
使用同一 D2_HOME 重跑这两个任务，分别复现完全相同的错误；两项均属于本次发现的
既有 gate/资源装配限制，不作为本批新增回归，也没有放宽断言或伪造通过。

剩余依赖：sourceItem/装备 Item 图、完整 Casting/Sequence/Leap/动态路径游标/
冻结战斗记录、MovementModes/飞行碰撞/骑乘、任务与 NPC pending 事务、Map/RoomEx、
本地实体、全局/未绑定 RNG、完整内容 digest、结构订阅副作用及真实 socket 跨进程
接管继续保留。下一批优先集中处理装备 Item/sourceItem 的所有权与恢复依赖。

### M8-A 第 45 批：Iron Golem sourceItem 运行态树与所有权恢复（2026-10-26）

完成日期：2026-10-26；代码提交：`a6a42e73f`。

本批将 `SummonRuntimeCheckpoint` 升至 v2，集中完成已实现 Iron Golem `sourceItem` 的恢复依赖：

- 新增 `ItemRuntimeCheckpoint` 与 `AttributesCheckpoint`，保存物品元数据、quality/随机字段、
  属性 base/aggregate/remaining 三列表、属性列表顺序、dirty/frozen 标记、战斗上下文、耐力上限及
  一层 socket 子树；恢复时通过目标 World 的 `ServerGameplayContext.Files` 重新绑定表行和属性表，
  不复制源 World 的表对象、UI wrapper 或过期 `Item.data()` 缓存。
- `SummonRuntimeCheckpoint` 对每个非空 `sourceItem` 保存完整运行态 Item 树，恢复前完成全部
  Item/属性解析；拒绝循环、共享、外来表绑定及与地面物品、玩家/佣兵/尸体/石魔容器重复拥有的引用。
  完整源 Item 无法安全编码时仍在写入前失败，不清除原对象；未覆盖的旧 v1 仍明确拒绝覆盖非空图。
- 依据 D2MOO `SKILLS_SrvDo057_IronGolem`：地面金属物品被转交给石魔，之后不再由地面实体拥有；
  本批只恢复既有 sourceItem 生命周期和属性，不扩展金属资格、伤害公式或死亡销毁规则。

验证：`:core:compileJava`、`SummonRuntimeCheckpointTest`、`CompanionRuntimeCheckpointTest` 通过；
此前 D2GS `D2gsWorldCheckpointBindingsTest` 佣兵/召唤绑定回归通过。首次回归发现旧版本损坏字节断言
使用 v2 值，已修正为非法版本；生成的 `SoundP.java` EOF 噪声不纳入提交。

剩余依赖：佣兵 `MercData` 装备槽/D2S 发布后的真实装备重绑、sourceItem 与完整 D2S 角色物品统一所有权、
完整 Casting/Sequence/Leap/路径游标、地图/事务/未绑定 RNG、真实 socket 跨进程接管仍未完成；M8 总体继续进行中。

下一迁移：M8-A 第 46 批，处理佣兵装备 Item 树与 D2S 装备槽重绑，随后再做 sourceItem/装备跨实体删除和重连验收。

### M8-A 第 46 批：佣兵 MercData 装备树与 D2S 发布后重绑（2026-10-26）

完成日期：2026-10-26；代码提交：`0e94b47e1`。

- `MercenaryServiceCheckpoint` 升级为 v2，保存每个活跃佣兵的完整 `MercData` Item 树及
  `equipmentRevision`；Item 使用与 Iron Golem 相同的运行态 codec，禁止共享、循环和外来表绑定。
- D2S 发布后先通过当前 World 数据表物化装备，再原子替换目标 `MercData.ItemData`，随后恢复
  equipmentRevision、共享 MercData stats 和无尸体属性镜像；不调用雇佣、装备事件、扣费或刷新。
- 旧 service v1 继续可读，但遇到目标已有非空装备图时拒绝覆盖；v2 会在写入/恢复前校验目标
  现有装备图可安全解析，避免晚失败时静默丢失装备。

原版依据：D2MOO `source/D2Game/src/UNIT/SUnitNpc.cpp` 的雇佣实体与 D2S `MercData` 物品段；
本批不改佣兵装备资格、属性公式或 D2S 二进制布局，只恢复已有 Item 所有权和槽位投影。

验证：core 佣兵/companion 相关回归通过；`D2gsWorldCheckpointBindingsTest` 61 项通过、0 失败、
0 跳过；`core:compileJava` 通过；`D2_HOME=G:\\BaiduNetdiskDownload\\Diablo II 1.10F`。

剩余依赖：真实带装备佣兵 fixture 的跨 JVM D2S round-trip、sourceItem/佣兵/玩家/尸体删除顺序、
完整 Casting/Sequence/Leap/动态路径游标、地图/事务/未绑定 RNG、真实 socket 跨进程接管仍未完成。

下一迁移：M8-A 第 47 批，加入真实带装备佣兵 fixture，验收 D2S/独立 JVM 重连及实体删除回滚。

### M8-A 第 47 批：真实带装备佣兵 round-trip 与装备状态保持（2026-10-26）

完成日期：2026-10-26；代码提交：`edd5ca03a`。

- 将真实 D2GS checkpoint fixture 扩展为佣兵雇佣后创建并装备 `cap`，固定 body slot、Item id、
  属性和 equipmentRevision；跨新 World 恢复后校验装备树、`Location.EQUIPPED`、`BodyLoc.HEAD`、
  D2S canonical 与服务摘要完全一致。
- `ItemData.replaceFromAuthoritativeSnapshot(snapshot, false)` 为 checkpoint 专用替换路径：重建
  ownership/equipped 索引但不重新计算 aggregate 或发送装备事件，避免已验证的 Item/owner 属性
  revision 在恢复时被二次重算；普通运行时入口仍保留原有重算和通知语义。
- 真实 fixture 继续使用独立 JVM/D2S 文件恢复路径，验证活跃佣兵装备在恢复后可继续服务；旧 v1
  未覆盖装备拒绝、共享/非法 Item 图预检和晚失败原子回滚测试保持通过。

原版依据：D2MOO 雇佣实体使用 `MercData` 的装备/属性状态，D2SReader96/D2SWriter96 的 merc
  item 段；本批不改物品资格、属性公式或 D2S 布局。

验证：`D2gsWorldCheckpointBindingsTest.hirelingServiceRostersAndDeadRecordsResumeAcrossWorldAndJvmRestart`
通过；完整该类 61 项通过，0 失败、0 跳过；`core:compileJava` 通过；资源路径为
`G:\\BaiduNetdiskDownload\\Diablo II 1.10F`。

剩余依赖：佣兵/玩家/sourceItem 跨容器删除顺序、带 socket 装备的真实跨 JVM fixture、完整
Casting/Sequence/Leap/动态路径游标、地图/事务/未绑定 RNG、真实 socket 跨进程接管仍未完成。

下一迁移：M8-A 第 48 批，集中处理装备/socket 删除生命周期与晚失败回滚，再进行带 socket 的
独立 JVM D2S 验收。

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

### M8-A 第 48 批：佣兵/召唤物装备树生命周期与 socket checkpoint 验收（2026-10-26）

完成日期：2026-10-26；代码提交：`c682e5223`。

- 统一主动解雇佣兵与登出卸载的所有权语义：主动解雇会清除 `MercData` 的完整装备树、socket
  子树、属性镜像和身份字段；登出卸载仍只删除运行时实体并保留 D2S `MercData`，避免 socket
  装备在重连时变成无主引用或被错误丢弃。
- 扩展真实 D2GS checkpoint fixture，使用带 `r01` socket 的 `cap` 佣兵装备和带 socket 的
  Iron Golem `sourceItem`，验证 native Item tree、`Location.SOCKET`、装备槽位和跨 World/
  独立 JVM 恢复后的身份保持。
- 复跑实体删除、佣兵装备覆盖预检和 D2S 晚失败事务回滚；失败路径要求保留原装备树引用、
  equipmentRevision、共享 MercData 属性和运行时服务状态，不允许半恢复。

原版依据：D2MOO `SUnitNpc.cpp` 的 MercData/雇佣实体生命周期、`SkillNec.cpp` 的
`SKILLS_SrvDo057_IronGolem` 消耗 source Item 语义；D2S 物品段沿用 `D2SReader96`/
`D2SWriter96` 的原生 `MERC`、`GOLEM` 和嵌套 socket 记录。本批不改变 D2S 二进制布局或装备
资格公式。

验证：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.engine.server.quest.NativeMercenaryRewardSystemTest --tests com.riiablo.engine.server.SummonedPetSystemTest --no-daemon --max-workers=1 --console=plain
.\gradlew.bat :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：核心定向回归通过；`D2gsWorldCheckpointBindingsTest` 62 项通过、0 失败、0 跳过，
包含带 socket 佣兵/召唤 source Item 的跨 World 与独立 JVM 恢复、主动解雇树清理和晚失败
回滚。生成的 `SoundP.java` EOF 空行噪声已清理。M8-A 仍不宣称整体完成，完整 Casting/
Sequence/Leap/动态路径游标、地图/事务/未绑定 RNG、真实 socket transport 接管和最终 M8-D
网络收口仍保留。

下一迁移：M8-A 第 49 批，继续处理未覆盖的实体创建/删除恢复边界与真实 socket transport
接管；若无新增生命周期缺口，再进入 M8-A/M8-D 联合收尾验收。

### M8-A 第 49 批：恢复删除边界与 socket transport 生命周期收口（2026-10-26）

完成日期：2026-10-26；代码提交：`c682e5223`。

- 收口 `ServerEntityLifecycle` 的恢复删除边界：Artemis 删除是延迟生效的，事务内以集合记录
  已提交删除，重复删除在同一 restore 事务中明确失败，避免佣兵/召唤物/sourceItem 等所有权
  清理被重复执行；每次 begin/end restore 都重置该门禁。
- 复核 `D2gsCheckpointSnapshot` 的创建失败、unexpected 删除和 rollback 路径，确认 socket
  transport token 的 stale frame/disconnect 门禁与实体删除事务共用同一连接 epoch。

验证：

```powershell
$env:D2_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
.\\gradlew.bat :server:d2gs:test --tests com.riiablo.server.d2gs.ServerEntityLifecycleTest --tests com.riiablo.server.d2gs.D2gsCheckpointSnapshotTest --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：D2GS 定向生命周期/checkpoint 回归 26 项通过、0 失败；核心与 FlatBuffers 生成通过，
未产生 `SoundP.java` 噪声。真实 1.10f 资源门禁按环境变量执行；本批不改变 D2S 布局或 socket
字段，仅收口 transport/restore 生命周期边界。

剩余问题：M8-A 的完整 Casting/Sequence/Leap/动态路径游标、地图/事务/未绑定 RNG，以及
M8-D 最终网络事件统一出口仍待后续批次。

下一迁移：M8-A/M8-D 联合收尾验收，覆盖真实双 socket transport 与最终旧入口清理。

### M8-A/M8-D 第 50 批：真实 socket 接管与跨区域删除门禁（2026-10-26）

完成日期：2026-10-26；代码提交：`d829c98b6`。

- 将真实 1.10f headless transport 验收合并为同一批：旧 socket 延迟帧、连接槽复用、
  generation-aware resync、延迟删除、实体 ID 复用，以及跨区域 tombstone 与重连 baseline。
- 修正跨区域 reconnect 夹具的生命周期竞态：关闭 socket 后等待 D2GS 应用线程完成权威实体
  删除，再建立替代连接，确保测试验证的是生产 delete→rebind 顺序，而不是依赖线程调度窗口。
- 保持 transport token、generation 和实体 incarnation 三重门禁；旧区域 tombstone 不得污染
  新区域复用的实体 ID，替代连接必须收到完整 baseline 并继续执行 resync。

验证：

```powershell
$env:D2_HOME='G:\\BaiduNetdiskDownload\\Diablo II 1.10F'
.\\gradlew.bat :server:d2gs:headlessStaleTransport :server:d2gs:headlessDelayedDeleteFrames :server:d2gs:headlessEntityIdReuse :server:d2gs:headlessCrossAreaBaseline --no-daemon --max-workers=1 --console=plain
.\\gradlew.bat :server:d2gs:test --tests com.riiablo.server.d2gs.D2gsCheckpointRecoveryTest --tests com.riiablo.server.d2gs.D2gsWorldCheckpointBindingsTest --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：真实 1.10f 四项 socket/lifecycle headless gate 全部通过；跨区域 gate 覆盖 source
level→destination level 的实体 ID 复用、incarnation 递增、旧 tombstone 丢弃和重连 baseline。
checkpoint recovery 与 World bindings 回归通过；本批不改变 FlatBuffer/D2S 字段或 D2 行为公式。

剩余问题：M8-A 仍有完整 Casting/Sequence/Leap、动态路径游标、地图/事务/未绑定 RNG 的
完整恢复范围；M8-C 表现依赖和 M8-D 最终旧 handler/transport owner 清理仍待联合收尾。

下一迁移：M8-A/M8-C/M8-D 最终联合收口，完成全量 replay/save/reconnect 与旧直连入口审计。

### M8-C/M8-D 第 51 批：旧物品 cursor 入口删除与统一 ingress 收口（2026-10-26）

完成日期：2026-10-26；代码提交：待本批提交后回填。

- 删除 D2GS 中已由 `isLegacyItemRequest` 统一拒绝、且没有生产调用者的旧
  `GroundToCursor`、`CursorToGround`、`StoreToCursor`、`CursorToStore`、body/belt 和
  swap handler 及对应 switch 分支，避免旧 cursor 协议继续伪装成生产 owner。
- 保留 `ItemMoveRequest` → `submitD2gsHandler` → 固定 Tick ingress 的唯一物品事务入口；旧
  协议仍在统一入口处记录拒绝，不再触发 `ItemManager` 直接写入或回显原始 packet。
- 与 M8-A 的 socket/generation 门禁组合验证，确认旧 transport、checkpoint 和物品入口删除
  不改变实体恢复、重连 baseline 或 D2S/FlatBuffer 字段。

验证：

```powershell
.\gradlew.bat :server:d2gs:compileJava :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.InventorySnapshotTransportTest --no-daemon --max-workers=1 --console=plain
git diff --check
```

结果：D2GS 编译与定向 ingress/物品传输回归通过；旧 cursor/body/belt handler 在生产源码中
无剩余调用者。生成的 `SoundP.java` EOF 空行噪声已清理。本批不改变物品规则、D2S 布局或
协议字段。

剩余问题：M8-A 的完整 Casting/Sequence/Leap、动态路径游标、地图/事务/未绑定 RNG，及
M8-C 的 `ClientGameplayContext` 表现兼容构造仍待最终联合收口；M8-D 还需完成全量 handler
唯一入口审计和旧 transport owner 删除。

下一迁移：M8-A/M8-C/M8-D 最终 replay/save/reconnect 验收与全量旧入口审计。

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



