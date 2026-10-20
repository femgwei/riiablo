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

## 总体迁移表

| 编号 | 迁移名称 | 范围 | 目标 | 主要产物 | 出口条件 | 依赖 | 状态 |
|---|---|---|---|---|---|---|---|
| M0 | 约束与基线 | 全仓库架构盘点、模块依赖、现有测试、D2MOO 证据和 dark-magic 对照矩阵 | 固定边界和迁移基线，不改变行为 | ownership 清单、依赖图、反向依赖清单、基线测试报告 | 所有目标目录都有唯一 owner；当前行为、已知失败和架构缺口可复现 | 无 | 进行中 |
| M1 | 领域边界 | `core`、`server`、`quests`、`server-systems`、`shared` 的依赖整理 | 建立 `mechanism / data / policy / adapter / presentation` 五类边界，并登记旧违规 | 包级依赖规则、架构检查、跨层调用清单 | 新代码不能违反依赖方向；旧违规点有 owner、迁移行和删除条件 | M0 | 进行中 |
| M2 | 权威状态 | 玩家、怪物、物品、任务、佣兵、地图运行时状态和客户端预测 | 明确唯一权威状态来源，UI 和客户端只能提交意图，不能提交结果 | 状态模型、状态写入接口、快照字段清单、客户端命令边界 | 任一状态变更都能追踪到权威系统；无 UI 直接写状态；预测回滚不改变权威数据 | M1 | 未开始 |
| M3 | Tick、命令与事件 | 固定 40ms Tick、输入意图、技能、移动、死亡、实体删除、阶段调度和结构屏障 | 统一执行顺序和副作用边界，令渲染帧率不影响仿真 Tick | Command envelope、Event、结构变更队列、固定步进运行时、Tick/phase 调度图 | 固定步进可脱离渲染运行；非法意图被拒绝；阶段末统一冲刷结构变更；普通移动、拾取、一个交互流程通过纯逻辑和 ECS 测试 | M2 | 进行中（已有固定步进与系统组，尚无完整准入/屏障） |
| M4 | 数据层 | TXT、MPQ、D2 数据表、固定点数值、RNG/Seed、版本和可选内容包 | 使用不可变记录和显式数据来源，规则层不直接操作解析器；为每个系统/实体建立可复现 RNG 流 | typed record、数据版本、命名 RNG stream、数据审计工具、可选内容包边界 | 缺失数据、版本差异、RNG 消耗都有明确测试；合法数据不走隐式常量回退；同一输入可重放得到相同随机序列 | M1 | 未开始 |
| M5 | 战斗垂直切片 | 普通攻击或 Fire Bolt 的完整链路 | 验证新边界能够承载真实 D2 行为，并能被快照/回放复核 | 命令→准入→结算→导弹/命中→伤害→快照→表现→回放 | 纯数据、ECS、D2GS、真实 1.10f 资源测试全部通过；旧入口不再结算；关键 Tick 有检查点和校验值 | M2、M3、M4 | 未开始 |
| M6 | 物品与成长 | 物品生成、装备、背包、库存事务、经验、升级、佣兵成长 | 将交易和属性聚合变成可回滚、可验证的领域事务 | Item domain、Inventory transaction、Progression service、存档字段映射 | 拾取、装备、交易、升级和重连状态无重复提交或双重结算 | M2、M4、M5 | 未开始 |
| M7 | 世界规则 | 地图生成、碰撞、寻路、怪物 AI、任务、NPC、Warp、对象 | 将 D2 规则从通用地图/ECS 机制中分离 | World policy、AI policy、Quest/NPC command handlers | Act 1 关键链路通过 headless、双客户端和真实 MPQ 验收 | M2、M3、M4、M5、M6 | 未开始 |
| M8 | 网络、存档与表现收口 | FlatBuffers/Netty、快照、重连、D2S、声音/视觉表现和 LibGDX UI/渲染 | 让客户端成为纯表现层，统一协议、持久化和表现适配边界 | semantic protocol、snapshot/replay/checkpoint、versioned save、presentation adapter、SoundEvent | 重连、回放、存档往返和客户端显示通过；服务端不直接访问音频/渲染；旧直连入口已删除 | M3、M5、M6、M7 | 进行中（声音事件链路已完成） |

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
| M8-C 表现层剥离 | `internal/presentation` 与仿真分离 | 继续收口 `ClientGameplayContext`、NPC/UI、标签、渲染和音频资源依赖；表现只能订阅事件/快照 | 未开始 | engine/server 不依赖 UI/音频设备；客户端表现替换不改变权威 Tick/结果 |
| M8-D 网络三层收敛 | protocol / transport / authoritative session | 收敛 FlatBuffers、Netty、D2GS session 和旧网络实现；禁止规则系统直接操作 socket | 未开始 | 协议、传输、会话各有唯一 owner；重连、快照和命令路径不再存在多套权威实现 |
| M7-A 机制/策略分离 | Go ECS 保持通用机制，Lua/D2 规则作为策略 | Java 中先把可参数化的 AI、技能、掉落和任务公式移入 policy/data，不进行一次性脚本化 | 未开始 | 规则策略可由纯数据/策略测试替换；通用 ECS 机制不读取具体 D2 表行 |

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

大迁移不直接对应大提交。每一行建议拆成 2～5 个小批次，每个小批次都提交并推送：

- M0：2 个批次，分别建立目录清单和测试基线。
- M1：3 个批次，分别整理依赖、架构检查和旧违规登记。
- M2：3～4 个批次，按玩家/单位、物品/任务、佣兵/地图状态迁移。
- M3：3～4 个批次，按命令队列、事件顺序、死亡/删除边界迁移。
- M4：3～5 个批次，按表数据、固定点、RNG 和缺失数据策略迁移。
- M5：5 个以上批次，先完成一个技能族，再扩展普通攻击和其他技能。
- M6～M8：每行 3～5 个批次，按领域和验收门槛拆分。

因此完整计划预计为 **8 次主要迁移、约 25～35 个小批次**。当前不启动 M5 之前的大规模重写；先从 M0 建立范围记录，确保以后每轮都能知道正在移动什么、为什么移动以及何时算完成。
