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

## 总体迁移表

| 编号 | 迁移名称 | 范围 | 目标 | 主要产物 | 出口条件 | 依赖 | 状态 |
|---|---|---|---|---|---|---|---|
| M0 | 约束与基线 | 全仓库架构盘点、模块依赖、现有测试、D2MOO 证据和 dark-magic 对照矩阵 | 固定边界和迁移基线，不改变行为 | ownership 清单、依赖图、反向依赖清单、基线测试报告 | 所有目标目录都有唯一 owner；当前行为、已知失败和架构缺口可复现 | 无 | 已完成 |
| M1 | 领域边界 | `core`、`server`、`quests`、`server-systems`、`shared` 的依赖整理 | 建立 `mechanism / data / policy / adapter / presentation` 五类边界，并登记旧违规 | 包级依赖规则、架构检查、跨层调用清单 | 新代码不能违反依赖方向；旧违规点有 owner、迁移行和删除条件 | M0 | 已完成 |
| M2 | 权威状态 | 玩家、怪物、物品、任务、佣兵、地图运行时状态和客户端预测 | 明确唯一权威状态来源，UI 和客户端只能提交意图，不能提交结果 | 状态模型、状态写入接口、快照字段清单、客户端命令边界 | 任一状态变更都能追踪到权威系统；无 UI 直接写状态；预测回滚不改变权威数据 | M1 | 进行中 |
| M3 | Tick、命令与事件 | 固定 40ms Tick、输入意图、技能、移动、死亡、实体删除、阶段调度和结构屏障 | 统一执行顺序和副作用边界，令渲染帧率不影响仿真 Tick | Command envelope、Event、结构变更队列、固定步进运行时、Tick/phase 调度图 | 固定步进可脱离渲染运行；非法意图被拒绝；阶段末统一冲刷结构变更；普通移动、拾取、一个交互流程通过纯逻辑和 ECS 测试 | M2 | 进行中 |
| M4 | 数据层 | TXT、MPQ、D2 数据表、固定点数值、RNG/Seed、版本和可选内容包 | 使用不可变记录和显式数据来源，规则层不直接操作解析器；为每个系统/实体建立可复现 RNG 流 | typed record、数据版本、命名 RNG stream、数据审计工具、可选内容包边界 | 缺失数据、版本差异、RNG 消耗都有明确测试；合法数据不走隐式常量回退；同一输入可重放得到相同随机序列 | M1 | 未开始 |
| M5 | 战斗垂直切片 | 普通攻击或 Fire Bolt 的完整链路 | 验证新边界能够承载真实 D2 行为，并能被快照/回放复核 | 命令→准入→结算→导弹/命中→伤害→快照→表现→回放 | 纯数据、ECS、D2GS、真实 1.10f 资源测试全部通过；旧入口不再结算；关键 Tick 有检查点和校验值 | M2、M3、M4 | 未开始 |
| M6 | 物品与成长 | 物品生成、装备、背包、库存事务、经验、升级、佣兵成长 | 将交易和属性聚合变成可回滚、可验证的领域事务 | Item domain、Inventory transaction、Progression service、存档字段映射 | 拾取、装备、交易、升级和重连状态无重复提交或双重结算 | M2、M4、M5 | 未开始 |
| M7 | 世界规则 | 地图生成、碰撞、寻路、怪物 AI、任务、NPC、Warp、对象 | 将 D2 规则从通用地图/ECS 机制中分离 | World policy、AI policy、Quest/NPC command handlers | Act 1 关键链路通过 headless、双客户端和真实 MPQ 验收 | M2、M3、M4、M5、M6 | 未开始 |
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
| M8-A 回放与检查点 | `simulation/replay.go`、`ecs/snapshot.go`、校验和/恢复 | 记录命令、权威 Tick、随机 stream 状态和快照摘要；支持断线恢复与离线回放 | 未开始 | 录制→恢复→继续运行结果一致；检查点校验失败可定位到 Tick/实体/字段 |
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

因此完整计划预计为 **8 次主要迁移、约 25～35 个完整批次**，不将这一数量全部消耗在 M1。当前 M1 三项交付已验收，下一正式迁移为 M2；已有 M3/M8 局部实现不代表后续迁移已完成。



