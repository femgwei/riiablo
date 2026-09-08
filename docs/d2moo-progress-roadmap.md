# riiablo / D2MOO 对齐进度与实施路线

更新时间：2026-09-09
基线：`F:/3rd_src/D2MOO`（Diablo II 1.10f）与仓库内 `D2MOO_JAVA`

## 说明

这里的百分比按“可运行行为 + 原生分支 + 数据表/RNG + 状态副作用 + 测试覆盖”估算，
不按文件数量或类数量计算。D2MOO 自身也包含 Stub，因此目标是对齐可观察的游戏行为，
不是复制 DLL 的内部实现。

当前分支：`master`  
当前基线提交：以本文件所在提交的 `HEAD` 为准（每次模块提交后更新报告）。

## 总体进度

- **全项目 D2MOO 行为对齐：约 69%**
- **全项目剩余工作：约 31%**
- **第一章最小可玩闭环：约 79%**
- **第一章剩余工作：约 21%**
- **D2MOO_JAVA DRLG 整体：约 70%**
- **D2MOO_JAVA 第一章实际使用链：约 85%**

第一章已经接近收尾；全项目剩余量主要来自完整战斗分支、物品属性、多人边界、
数据层统一，以及 Act 2–5 地图和任务。

## 模块完成度与剩余量

“项目权重”表示该模块占整个 D2MOO 对齐目标的估计比重；“剩余贡献”表示该模块
当前缺口折算到全项目的百分点，所有模块剩余贡献合计约 36%。

| 优先级 | 模块 | 项目权重 | 当前完成 | 模块剩余 | 剩余贡献 | 当前结论 |
|---|---|---:|---:|---:|---:|---|
| P0 | 数据表、固定点、RNG、Unit 所属关系 | 10% | 62% | 38% | 3.8% | 部分运行路径仍有默认值和 fallback |
| P0 | 第一章地图、Warp、碰撞 | 10% | 85% | 15% | 1.5% | 可玩链基本稳定，需完成细节和回归 |
| P0 | Act 2–5/完整 DRLG | 5% | 25% | 75% | 3.8% | 尚未按第一章标准逐幕审计 |
| P0 | 怪物生成、等级和区域人口 | 7% | 70% | 30% | 2.1% | 还需完整区域池、群组和难度分支 |
| P0 | 怪物 AI 与特殊行为 | 8% | 57% | 43% | 3.4% | 通用 fallback、召唤和特殊 AI 分支不全 |
| P1 | 战斗、伤害、状态、技能、导弹 | 12% | 67% | 33% | 4.0% | 命中结果、抗性、持续伤害和技能分支仍简化 |
| P1 | 经验、升级、属性点、技能点、佣兵经验 | 7% | 75% | 25% | 1.8% | 所有权链、存档恢复和少量事件待补 |
| P1 | 装备、背包、物品移动和派生属性 | 10% | 60% | 40% | 4.0% | 原生属性聚合、腰带/尸体/插槽仍不完整 |
| P1 | TreasureClassEx、品质和地面掉落 | 7% | 70% | 30% | 2.1% | 唯一/套装属性和完整构造仍有 fallback |
| P2 | 箱子、门、陷阱、神殿、水井等对象 | 5% | 80% | 20% | 1.0% | 第一章主要对象已接通，快照广播待补 |
| P2 | 第一章任务、奖励和过渡 | 5% | 75% | 25% | 1.3% | 多人资格、对话分支和持久化待补 |
| P2 | NPC 买卖、修理、赌博、雇佣 | 4% | 70% | 30% | 1.2% | 原生库存刷新、雇佣/复活和重连待补 |
| P2 | Party、敌对、玩家交易和多人同步 | 5% | 60% | 40% | 2.0% | 权威移动意图已接通；玩家交易、复杂可见性和重连待补 |
| P2 | D2S 存档、角色创建和状态持久化 | 4% | 60% | 40% | 1.6% | 版本校验、完整 section 和 mask 待补 |
| P3 | UI、渲染、音频和离屏验证 | 1% | 50% | 50% | 0.5% | 采用可观察行为对齐，不复制 DLL 结构 |

### 七大职业技能专项（从“战斗/技能”12%中拆分）

这些类已经提供了不少技能公式辅助方法，但不能等同于“该职业技能全部完成”。
当前 `ServerSkillSystem` 仍有通用执行路径，许多技能的 `srvstfunc/srvdofunc`、
协同、状态、导弹和目标分支尚未逐项与 D2MOO 对照。

| 职业 | 当前完成 | 剩余 | 主要缺口 |
|---|---:|---:|---|
| 亚马逊 Amazon | 99% | 1% | 元素伤害、爆炸/冰冻、火场、毒标枪云雾与弹药闭环已完成；完整命中/受击动画仍待补齐 |
| 刺客 Assassin | 100% | 0% | 服务端技能、状态、周期伤害、召唤/陷阱、聚气完成技和多人表现快照专项均已逐项接通；资源实机观感归入统一表现验收 |
| 野蛮人 Barbarian | 100% | 0% | 主动技能、战吼、尸体工具链、六类武器精通及 GH/BL/状态 Overlay 同步已接入；资源实机观感归入统一表现验收 |
| 德鲁伊 Druid | 90% | 10% | 狼/熊、Feral Rage/Maul、Rabies/Fire Claws、Hunger、Shock Wave、Fury 及召唤物所有权/生命周期已完成；召唤 AI 深化和持续区域技能待补 |
| 死灵法师 Necromancer | 50% | 50% | 尸体技能、召唤物所有权、诅咒和复活数量限制 |
| 圣骑士 Paladin | 50% | 50% | 光环叠加、Blessed Hammer/FoH、元素伤害与抗性 |
| 法师 Sorceress | 55% | 45% | Teleport、冰冻/燃烧持续时间、掌握技能和导弹分裂 |

职业技能专项整体按 **约 70% 完成、约 30% 剩余** 计入战斗模块；刺客专项已完成，
其余职业仍按各自行所列缺口继续推进。

## 实施顺序

每完成一项，就在本文件将对应 `[ ]` 改为 `[x]` 并使用删除线标记，同时补充测试、
提交和远程推送信息。未通过验收的模块不得标记完成。

### 2026-09-08 P0/P1 原生底层重新基线

此前百分比侧重“功能可运行”。从本节起，P0/P1 改用更严格的验收口径：1.10f
原始表字段、D2MOO 可观察行为、固定 tick 时序、状态副作用和自动对照必须同时通过，
否则只能标记为“部分完成”。`libd2` / `dark-magic` 的 1.14d 数据只能参考解析结构和
测试方法，不能作为 1.10f 数值真值。

- [x] **~~P0-1 无损 TXT 数据层与 1.10f 五表对照（100%）~~**
  - 已完成独立于旧 `TxtParser` 的无损读取器：保留空字段、重复/空列、短行、超额列、
    空行、`Expansion`、原始行和源行号；支持 D2 布尔值及完整 uint32 十六进制读取。
  - 已按 D2MOO `DATATBLS_LoadStatesTxt` 接入 1.10f `States.txt` 的 40 个状态标志、死亡
    保留字段、overlay/stat/function/link 字段；暂不改变运行时 State 行为。
  - 已对本机原始 1.10f MPQ 的 `ItemStatCost/States/Skills/Missiles/MonStats` 固定行列数、
    原始 SHA-256、header SHA-256 与逐字段语义 SHA-256；测试不提交原版 TXT 内容，其他
    环境通过 `D2_110F_HOME` 可复验相同资源。
  - 已完成 `ItemStatCost` 无损 schema；原生行序 ID 与 TXT `ID` 分开保存，Add/Multiply/
    Divide、op/op base/op stat 及存档位字段保持原始值，不再被旧兼容修正污染；修正旧表
    `damagerelated` 被错误声明为字符串的问题。
  - 已完成 `Skills` 无损 schema：D2MOO `pSkillTbl` 的 238 个字段机械核对无缺失，另外
    保留原始诊断列 `ID`；公式、链接名不提前编译，bit/integer 字段逐格验证，357 行原始
    1.10f 数据全部通过。空白数值格保留原文，但按原生缺省 0 语义验证。
  - 已完成 `Missiles` 无损 schema：D2MOO `DATATBLS_LoadMissilesTxt` 的 146 个加载字段机械
    核对无缺失，684 行、171 列原始 1.10f 数据全部通过。`TXTFIELD_BIT` 与 byte integer
    分开投影；`*N` 原始异常值保留但按原生缺省 0 读取，非零 bit（包括原表中的 `2`）按
    set 解释，不擅自改写原版数据。
  - 已完成 `MonStats` 无损 schema：D2MOO 加载的 253 个字段机械核对无缺失，另保留
    `hcIdx` 原始诊断列；705 个原始数据行中排除 1 个 `Expansion` 控制行，得到 704 个
    连续原生怪物 ID，避免控制行造成索引偏移。
  - 五表统一投影报告现可稳定列出原始行列数、D2MOO schema 字段数、额外诊断列及每个
    缺列、重复列、非法 integer/bit 的源行和列；五张真实 1.10f 表均无 schema issue。
- [ ] **P0-2 原生 Stat/State 聚合和生命周期（约 88%）**
  - 已有 `Attributes + UnitStates`、tick 衰减和部分技能状态；仍需明确永久 stat 与临时
    state stat 两层，并统一 `Base -> Add -> Percent`；堆叠、覆盖、死亡清除和保存规则
    必须由 1.10f 数据及 D2MOO 行为驱动。
  - 已完成首项显式聚合门槛：新增带稳定来源身份的 encoded stat resolver，强制所有 Add
    先汇总、Percent 最后只应用一次；最大生命/法力/体力不再通过浮点反除旧百分比恢复，
    而是显式保留永久/装备基线，并以 24.8 编码值聚合、刷新、重算及到期恢复。
  - `libd2` 仅借鉴 buff 精确 delta 所有权和刷新/到期测试，`dark-magic` 仅借鉴稳定命名
    stat source 与乱序输入门槛；两者的 1.14d 数值和简化平面 stat 模型不作为行为真值。
  - 已建立 D2MOO `D2StatListStrc` 兼容状态层：同一 state 可按 source entity + skill
    持有多个独立 stat-list，单独刷新、到期和移除时只撤销该层；修复多来源共用 state 时
    删除一个层会错误清除全局 state flag 的问题。
  - `UnitState` 开始保存原生 stat ID、layer、operation 和 encoded value；set/add/归零行为
    分别对齐 `STATLIST_SetStat` / `STATLIST_AddStat`，条目按 stat/layer 稳定排序。首批已迁移
    Aura 的 damage、attack、defense、velocity 与五类抗性，同时保留旧 scalar 投影供网络
    序列化和未迁移技能兼容，聚合端不会重复计算。
  - 战吼、变形、神殿等旧调用继续直接写 scalar 时，首次聚合读取会把变更反向导入该
    state 自己的原生 stat/layer，而不是继续遗留在单位总值中；后续刷新替换同一条目，
    state 到期或按来源移除即可完整撤销。专项回归覆盖旧写入、原生写入和混合兼容。
  - 已对齐 D2Common `D2Common_10469` 死亡清理：玩家、普通怪物、Boss 分别读取
    `plrstaydeath / monstaydeath / bossstaydeath`；D2StatList BASIC 永久被动无条件保留。
    玩家复活不再错误 `clearAll()`，DOT 致死也不再在死亡订阅者处理后抹掉保留状态。
  - `noclear` 与死亡规则分离，提供普通清除时的保留路径；真实 1.10f MPQ 门槛确认四组
    mask 均非空。服务端执行权威清理，网络客户端不修改 snapshot-only 状态，本地模式
    使用同一规则。移除状态层同时移除其 stat contribution，无幽灵 buff。
  - 已对齐 D2Game `SUNITDMG_ApplyPoisonDamage/ApplyBurnDamage`：新 DOT 每帧强度低于
    现有效果时完全拒绝；相等或更强时替换来源、伤害和精确到期时间，因此 Venom 等
    效果可以按原生规则缩短旧毒持续时间。毒/燃烧共用同一 StateList 策略。
  - 已对齐 `SUNITDMG_ApplyColdState/ApplyFreezeState` 第一阶段：冰冷只延长到期时间，
    保留原 owner/payload，不按等级重复叠加；首次应用写入 `velocitypercent / attackrate /
    other_animrate`。修复原先把 `attackrate` 错当命中率以及额外硬编码减速导致的重复计算。
  - 玩家、剧情 Boss、暗金/超级暗金和佣兵收到冻结时降级为冰冷；普通怪物才进入完全
    冻结，不可打断状态会拒绝冻结，并读取 MonStats `coldeffect` 与难度 Cold/Freeze
    divisor。专项 ECS 测试覆盖玩家、暗金和普通怪物的状态及移动表现。
  - 已对齐 D2Game 诅咒 stat-list 首项：状态层记录 source/skill/group/strength，
    同一来源同一技能重施按强度替换或刷新；不同来源同一诅咒独立保留；States.txt
    同组不同诅咒按强度互斥，移除强来源后自动恢复仍有效的次强层；新增可驱散诅咒掩码路径。
  - 待补：将神殿、战吼、变形和其他 `UnitState` 专用 scalar 全部迁入统一 stat source，
    并继续对照每个具体诅咒技能的 stat/value 公式。
- [ ] **P0-3 Unit 生命周期（约 84%）**
  - 玩家、怪物、NPC、佣兵和召唤物已有 ECS 模型；新增 `UnitLifecycle` 阶段标记和
    `UnitLifecycleSystem`，统一处理 `DeathEvent` 幂等边界。
  - `EntityFactory` 创建实体时统一写入 `SPAWN`；`UnitLifecycleSystem` 改为权威
    `BaseSystem`，在固定 tick 依次推进 `SPAWN -> INSERTED -> ACTIVE`，避免实体在
    组件尚未完成装配时被行为系统提前消费。新增阶段推进回归测试。
  - 生命周期系统改用实体订阅驱动，Destroy/延迟删除后订阅不残留；新增删除边界回归。
    RoomEx 卸载、网络断线和重连的订阅删除已由 `headlessReconnectVisibility` 与
    `RoomActivationSystem` 端到端覆盖，旧玩家/召唤物不会泄漏到新基线。
  - 死亡时清理 owner/damageOwner/attached 导弹、召唤物、Target 引用和来源 state 层；
    导弹等无尸体实体在死亡边界删除，玩家/怪物交由专用尸体系统保留。
  - `GameScreen` 与 D2GS 均注册该系统，新增 `UnitLifecycleSystemTest` 覆盖重复死亡事件、
    所有权清理和玩家实体保留。
  - 待补：将 `Spawn -> InsertWorld -> TickUpdate` 的阶段标记接入统一实体工厂，并补
    Room 卸载、网络断线和 Destroy 后订阅清理测试。
- [ ] **P0-4 固定 25Hz Sim Tick 与阶段顺序（约 96%）**
  - 服务端 40ms 单写者 tick、本地固定步进、渲染隔离、位置快照和多人时钟已通过。
  - D2GS 与本地权威世界现按 `state -> missile -> unit/AI -> death/destroy -> snapshot`
    注册核心系统；状态更新和导弹碰撞均在单位行为前执行，避免新状态/投射物被延迟一帧。
  - 新增固定时钟长跑回归：10,000 tick 的 serverTime 每帧严格增加 40ms，world delta
    始终为 1/25 秒；2,000 个带生命周期实体连续运行 500 tick，tick 与订阅数量不漂移。
  - 真实 1.10f `headlessSnapshotResync` 通过完整多人快照压力流程，包含跨地图、死亡/复活、
    RoomEx 重订阅、乱序/重复基线和 2.5 秒暂停恢复（`oldLevelDrops=184`，无时间线回退）。
- [ ] **P1-5 Missile 原生表驱动（约 94%）**
  - `ServerEntityFactory` 已读取 `Missiles.txt.Pierce` 和原生速度/Range；本轮将
    `Collision`、`CollideKill` 接入统一碰撞/销毁判定，非碰撞视觉导弹不再误伤，
    非 `CollideKill` 导弹可按原生规则继续飞行。
  - 导弹 Pierce 改用每枚导弹独立 `NativeRng` 状态，避免 LibGDX 全局 RNG 串扰；
    `UnitLifecycleSystem` 负责主人、附着控制体销毁时的导弹回收。
  - `CollideType` 已映射到原生 missile-barrier/wall 碰撞掩码；屏障命中会在
    `Explosion/AlwaysExplode/ExplosionMissile` 配置存在时生成通用爆炸子导弹。
  - `LastCollide` 已在 Range 末帧执行一次最终单位碰撞；`CollideType=7` 已支持
    导弹间碰撞，并按 `CanDestroy` 销毁可被导弹破坏的目标。
  - 新增 `MissileNativePolicyTest` 的 512 枚短命导弹压力用例，确认 Range 回收不
    残留；D2MOO 的 `CanDestroy` 仅针对 `UNIT_MISSILE`，不误用于普通场景物件。
  - 新增 2,048 枚零速度导弹的 5 帧原生寿命压力用例，确认 `nativeLifetimeFrames` 到期
    后延迟删除不会残留实体；真实 `headlessSnapshotResync` 长跑覆盖跨 RoomEx 导弹/实体
    快照及重订阅，未出现时间线倒退或导弹泄漏。
  - 将 `LastCollide` 范围边界判定集中为单次门控函数，要求原生 Collision 标志且禁止
    重复 endpoint pass；新增组合边界回归，避免多目标末帧重复命中。
  - 待补更精确的 `LastCollide` 多目标边界语义和真实地图跨房间飞行命中样本。
- [ ] **P1-6 伤害、命中与死亡链（约 92%）**
  - `CombatSystem` 现按 `max*resist` 读取元素最大抗性上限，并接入火/电/冰/毒/魔法
    的原生元素穿透；免疫判定仍在抗性上限裁剪前执行。
  - 已接入玩家对玩家 17% 原生伤害系数（抗性计算后、偷取计算前），并补充 PvP
    回归测试；元素抗性现限制在原版 `-100..maxResist(<=95)` 范围。
  - 已接入 Nightmare/Hell 玩家抗性惩罚（-40/-100），仅对玩家防御者生效；新增
    `calculateAttackAtDifficulty` API，怪物目标不误用难度惩罚。
  - 已接入火/电/冰/魔法百分比与固定吸收，按“抗性/穿透 -> 百分比吸收 -> 固定吸收”
    顺序结算并记录 `absorbedLife`；导弹伤害路径会将吸收生命原子恢复到目标并限制在
    `maxhp`，新增回归覆盖顺序、边界和非玩家目标。
  - 已将吸收生命恢复接入 Actioneer 的通用近战、德鲁伊形态技能、Whirlwind 及怪物
    近战/火焰/冲锋路径；满血与最大生命值边界统一由 `restoreUpToMaximum` 处理。
  - 已将 Death Sentry 爆炸、Blade Shield 周期伤害和刺客终结技的火焰/冰冷区域伤害
    接入统一 `calculateFixedElementalDamage`，包含抗性、难度惩罚、吸收和 PvP 顺序；
    完全吸收时仍会执行生命恢复，且不会跳过状态/死亡边界处理。
  - 已将神殿风暴（闪电）和对象火焰陷阱接入统一元素解析，保留原生命中/周期节奏，
    并统一难度、抗性、吸收和目标生命恢复。
  - 已修正死亡奖励幂等门槛：`ExperienceManager` 与 `DeathRewardSystem` 均先验证
    玩家/召唤物归属，再 claim XP/TC；无主环境伤害事件不会吞掉后续有效击杀奖励。
  - 已在 `MonsterRewardState` 保存首次有效死亡的击杀者、难度、怪物等级、经验和
    可分配玩家快照；无主/重复事件不会覆盖该上下文，便于多人重连和延迟事件保持一致。
  - 经验分配和掉落的 party-in-level 现在优先使用死亡瞬间快照；PlayersX 仍按全连接
    玩家数计算，避免因 party 范围缩小导致掉率偏差。
  - 待补：掉落归属超时快照广播，以及少数非 CombatSystem 的环境伤害特殊分支。
- [ ] **P1-7 地面物品、掉落与拾取（约 80%）**
- [ ] **P1-8 D2S 1.10f round-trip（约 68%）**

强制踩坑回归门槛：

1. sim 数据不得由可变 `render()` delta 驱动；25Hz 逻辑阶段必须和逐帧渲染隔离。
2. stat 聚合必须验证 `Base -> Add -> Percent`，禁止依赖调用顺序偶然正确。
3. 死亡测试必须证明应清理 state 消失、应保留 state 保留，并且无幽灵 buff。
4. 近战命中只能读取攻击起手 tick 的位置/Size/Zone/Room 快照。
5. missile 测试必须验证命中、超时、离开世界和主人销毁后的实体回收，防止实体泄漏。

当前执行优先级：`P0-2 Stat/State -> P0-3 Unit 生命周期 ->
P0-4 阶段顺序 -> P1 Missile/伤害 -> P1 物品/D2S -> P2 第一章边界 -> P3 技能扩展`。

### P0：先恢复可靠的回归基线

- [x] ~~修复宝石神殿测试中 `ItemData.updateStats` 对 `item.type == null` 的崩溃~~
  - 验收：`NativeGemShrineServiceTest` 全部通过；非法/旧存档物品不会使服务端崩溃。
- [x] ~~运行第一章核心回归集合，并清理生成文件噪声~~
  - 2026-09-02：5 组测试、18 个用例全部通过；FlatBuffers 生成空行已清理。
- [x] ~~为后续每个模块建立“原版数据 + ECS 集成”双层测试门槛~~
  - `docs/d2moo-test-gate.md` 记录统一命名、资源注入和 headless 运行约定；新增 `NativeDataTablesTest` 作为纯数据门槛，现有 UnitFlags、Fallen Shaman 和双客户端测试作为 ECS/集成门槛。

### P0：基础数据和怪物生成

- [x] ~~统一 `MonStats/MonLvl/Levels/Experience/TreasureClassEx` 的运行时读取~~
  - `NativeDataTables` 集中处理难度列、缺失列和群组边界，并接入地图尺寸/区域等级、MonsterStatsCalculator、对象等级、Countess、Act1 D2MOO 缓存和 TC 掉落。
- [x] ~~统一固定点数值、种子归属和 RNG 消耗顺序~~
  - 新增 `NativeRng`；TC 掉落和死亡奖励使用按游戏种子/单位实体派生的独立流，避免全局 LibGDX RNG 串扰；固定点概率仍沿用原生整数分母。
- [x] ~~完成第一章区域怪物池、`MinGrp/MaxGrp`、`PartyMin/PartyMax` 校准~~
  - `NativeMonsterRegion` 现在按 Normal/Nightmare/Hell 列选择、过滤空槽、限制 13 个候选并提供原生密度；生成器统一使用安全群组边界。
- [x] ~~完成 Fallen Shaman 真实地图双客户端回归：复活可见、不重复经验、不重复掉落~~
  - `DualClientFallenLootIntegrationTest` 先构造固定种子原生 Blood Moor 导出，再验证两份客户端快照同时看到复活、单次掉落和对端拾取；`FallenShamanAutoCombatIntegrationTest` 覆盖真实 ECS 复活事件、尸体消费和生命恢复。
- [x] ~~完成怪物生成位置约束：RoomEx 外、墙体、悬崖后和不可行走区均禁止生成~~
  - 固定种子层测试覆盖主可行走连通区、孤立悬崖口袋、地图边界和完整怪物 footprint；`spawnPendingMonsters` 在最终碰撞层生成/延迟到 RoomEx。

### P1：物品、掉落和战斗

- [x] ~~完成原生物品种子、词缀资格、属性范围、Socket/Ethereal/Durability~~
  - 死亡掉落为每个物品派生独立种子；词缀按原生 affix level、spawnable/version/rare/type/exclude/class/frequency 过滤并加权选择；属性范围使用同一物品 RNG；基础防御、初始耐久、堆叠数量、难度/等级孔数和 5% 无形规则均接通。
- [ ] 完成装备/背包/腰带/尸体/交易栏的统一移动协议和派生属性刷新。
  - 已完成第一轮权威协议加固：只接受稳定物品 ID；修正 Swap 目标占用；按 Inventory 10x4、Cube 3x4、Stash 6x8 校验；丢地创建失败原子回滚；D2GS 拒绝绕过 revision/幂等/快照校正的旧移动包。
  - 待完成：腰带容量随装备变化、尸体取回接入同一 revision、NPC/任务外部物品变更同步 revision，以及玩家交易栏协议。
- [ ] 完成 TreasureClassEx 的嵌套 TC、唯一/套装属性和掉落位置规则。
- [ ] 对齐命中结果、格挡/闪避、抗性/吸收、穿透、持续伤害和状态生命周期。
- [ ] 补齐第一章怪物技能、导弹碰撞和特殊 AI 的原生分支。
- [x] ~~完成亚马逊 Decoy/Dopplezon 与 Valkyrie 原生召唤专项~~
  - `SrvDo015/016` 使用 `Skills.txt` 的 `summon/pettype/petmax/calc`；召唤物具有主人、阵营、限额替换、可通行落点、Decoy 时限与 Valkyrie 状态；召唤物击杀奖励归主人，自身死亡无经验和掉落。
- [x] ~~完成亚马逊导引箭、Strafe、Pierce、闪避被动和元素箭表现专项~~
  - `SrvDo010/012` 目标追踪、原生箭数/范围、穿透命中去重和 Dodge/Avoid/Evade 已接入；Magic/Fire/Cold/Exploding/Ice/Immolation/Freezing Arrow 的多人客户端资源和权威导弹去重测试通过。
- [ ] 完成亚马逊剩余弓系运行时细节
  - [x] ~~完成弓/弩与箭袋类型匹配、施放前校验、发射扣量、耗尽拒绝及多人余量同步~~
  - [x] ~~完成 `item_replenish_quantity` 原生间隔、逐点恢复、上限停止及多人余量同步~~
  - [x] ~~完成 Skills.txt 物理/元素伤害快照、Magic/Fire/Cold 转伤、Ice/Freezing 冰冻及 Exploding/Freezing 范围子导弹~~
  - [x] ~~完成 Immolation Arrow 原生持续火场：SrvHit09 圆形布点、100 帧生命周期、DamageRate 周期伤害及多人同步~~
  - [x] ~~完成 Poison Javelin/Plague Javelin 毒云子导弹，并统一毒雾陷阱、毒尸体的持续区域生命周期和区域施毒管线~~
  - 待补：完整命中与受击动画反馈；当前专项完成度约 99%，不能整体划掉。
- [x] ~~完成刺客技能表审计与 Shadow Warrior/Shadow Master 原生召唤首项~~
  - 核对 `Skills.txt` 实际 ID（Shadow Warrior=268、Shadow Master=279、SrvDo049），按 `summon/pettype/petmax` 创建玩家所有权召唤物，并同步 `SHADOWWARRIOR` 状态；`AssassinSkillSpecializationTest` 已加入数据和 ECS 门槛。
- [x] ~~完成刺客陷阱生命周期（SrvDo044/SrvDo045）~~
  - 对照 D2MOO `SKILLS_SrvDo044_BladeSentinel` / `SKILLS_SrvDo045_Sentry`，由服务端创建有所有权的 `assassintrap` 召唤体，按 `petmax` 替换旧实例；新增权威 `AssassinTrapSystem` 负责目标搜索、15 帧攻击节拍、技能导弹快照和原生射击次数耗尽移除，避免陷阱攻击再次递归触发 SrvDo045。
  - [x] ~~完成 Blade Sentinel / Blade Creeper（AI Fn102、Missile SrvDo20）首项~~：控制实体在施法起点与目标端点间往返，只创建一个附着的 `blade creeper` 导弹；导弹跟随控制实体、保留玩家伤害归属、按 `NextHit/NextDelay` 去重，并在控制实体消失时清理。
  - [x] ~~完成 Wake of Fire `SrvDo125/SrvDo31` 首项~~：服务端创建 `wake of destruction maker`，沿目标方向移动到终点后生成相反方向的两个 `wake of destruction` 火焰波，并把伤害归属解析回施法者。
  - [x] ~~完成 Inferno Sentry `SrvDo95` 首项~~：按 `calc2`（含 Wake of Fire 协同等级）设置喷射窗口，按 `calc3` 重复创建火焰导弹，并在每次喷射时重新追踪目标方向；单枚导弹按 `calc1` 设置原生路径长度。
  - [x] ~~完成 Death Sentry `SrvDo55` 首项~~：按原生 `CorpseSel`/可用状态筛选尸体并原子保留，防止同一尸体重复引爆；按尸体最大生命和 `calc1/calc2` 生成伤害，依据 `calc3` 拆分物理/火焰并在 `AuraRange/2` 范围结算，创建同步爆炸表现；无尸体时回退 `Skill2` 闪电攻击，`calc4` 正确计入 Fire Blast 基础等级的射击次数协同。
  - [x] ~~完成 Charged Bolt/Lightning Sentry 首项~~：对照 D2MOO `SrvDo017_ChargedBolt_BoltSentry`、`PATH_ComputePathChargedBolt` 与 AI `Fn101_AssassinSentry`，Charged Bolt Sentry 按 `calc1` 一次生成多枚独立 `sentrychargedbolt`，使用原生种子公式和每 2 子格左偏/直行/右偏折线路径；Lightning Sentry 复用原生 `Aip1/Aip2/Aip3/Aip4` 目标距离、攻击概率和停顿节拍，并按每次攻击重新追踪目标。两者均解析 Missiles.txt 关联的玩家技能伤害并通过现有 EntitySync 权威广播，补充专项 ECS 回归。
- [x] ~~完成刺客聚气和完成技专项~~
  - [x] ~~完成 `SrvDo034/035` 聚气命中与多人状态首项~~：Tiger Strike、Cobra Strike、Fists of Fire 等技能只在成功且未格挡的近战命中后叠层，按 `AuraState/AuraLenCalc` 保存技能来源和等级，最多三层并刷新期限；阻止 `SrvMissileA-D` 在蓄力阶段被误生成为普通导弹；`StateP.velocityModifier` 兼容传输层数且客户端恢复后不影响移动速度。
  - [x] ~~完成 Tiger/Cobra/Fists 的完成技直接释放与统一消费~~：完成技成功且未格挡时读取全部聚气状态；Tiger 按 `calc1 × 层数` 增强物理伤害，Cobra 严格按 1 层生命、2 层生命/法力、3 层双倍生命/法力吸取，Fists 按 Skills.txt 等级段伤害和 `calc1` 完成火焰直击/物理转火；实际 `DamageEvent` 结算后才恢复生命和法力，未命中、格挡、越距均保留聚气。
  - [x] ~~完成 Fists of Fire 二层范围冲击与三层火焰场导弹表现~~：补入 `PrgStack/SrvPrgFunc/PrgCalc` 原生列；二层按 `PrgCalc2` 在完成技目标周围结算一次共享物理/火焰伤害，三层继续叠加二层并按 `PrgCalc3` 圆形随机布置 `fistsoffirefirewall`。火场导弹由服务端持有、保存技能伤害快照、按 Range 帧退出并通过既有导弹实体同步给多人客户端。
  - [x] ~~完成 Claws of Thunder、Blades of Ice、Phoenix Strike 的阶段导弹、范围/冻结效果~~。
    - [x] ~~完成 Claws of Thunder 三阶段释放~~：一层按 `PrgDam=4` 将 Skills.txt 闪电伤害加入完成技；二层对照 `SrvDo036/sub_6FD14170` 从目标位置创建 64 路量化方向 `clawsofthundernova`；三层按 `PrgStack` 继续叠加 Nova，并对照 `SrvDo037/sub_6FCF6600` 以 `PrgCalc3=4` 创建 16 条 `clawsofthunderbolt` Charged Bolt 路径。所有导弹由服务端持有、保存技能伤害快照并进入多人同步。
    - [x] ~~完成 Blades of Ice 范围冰伤、冻结和三层冰弹~~：按 `PrgDam=4` 将 Skills.txt 冰冷伤害加入完成技；二层复用 `SrvDo038` 的单次共享物理/冰冷伤害记录并在半径 6 内施加冰冷；三层按 `PrgStack` 叠加二层，在半径 3 内执行 9 次原生随机布点并创建 `bladesoficecubes`。主目标按 `Param5` 冻结，冰块按 `SrvDmg10` 冻结命中目标，并以原生 Range 帧到期。
    - [x] ~~完成 Phoenix Strike 三阶段元素导弹与叠加规则~~：严格按 `PrgStack=false` 只释放当前层；一层 `SrvDo040` 创建 Meteor Center，并由 `SrvHit04/14` 生成陨石范围伤害与原生 18 点持续火场；二层 `SrvDo143` 以 `PrgCalc2=10` 创建 7 路 Chain Lightning 并按 `Param2+1` 权威续跳；三层 `SrvDo041` 以 `PrgCalc3=16` 创建 16 枚 Chaos Ice，按 `SrvDo35` 周期转向并冻结命中目标。
  - [x] ~~完成 Dragon Talon/Claw/Tail/Flight 的多段、双爪、范围火焰和目标位移。~~
    - [x] ~~完成 Dragon Talon `SrvSt24/SrvDo042` 原生连续踢击~~：严格按 `calc1=lvl/6+1` 初始化踢击次数，每次动画独立命中、伤害和耐久结算；聚气只在首个成功踢击释放一次，目标死亡立即终止后续动作；末击按普通/Unique/Boss/玩家与佣兵分别读取 100%/`calc2`/`calc3`/`calc4` 击退概率，并用地图碰撞限制服务端位移。靴子 `mindam/maxdam/StrBonus/DexBonus`、`item_kickdamage`、技能 ED 和原生 `dmXY` 衰减公式已纳入伤害/概率计算。
    - [x] ~~完成 Dragon Claw `SrvSt25/SrvDo046` 原生双爪序列~~：按原生 HT2 命中帧以 `A2 → S4` 执行左右爪独立攻击，分别读取当前爪伤害、力量/敏捷缩放、`calc1` 增伤并各自消耗耐久；首个成功命中统一释放聚气，第二爪不会再次消费；单爪/徒手保留原生单命中退化路径。补齐共享 `SrvSt64` 的 MonFrenzy 目标校验，避免套用玩家装备规则。
    - [x] ~~完成 Dragon Tail `SrvSt27/SrvDo050` 主目标踢击与范围火焰爆炸~~：起手阶段生成并保存一次原生命中记录，命中帧不再重复掷骰；修正共用 KICK 力量/敏捷基础伤害，主目标踢击后按经物理减伤及 Tiger Strike 增幅后的实际物理伤害乘以 `calc1 + passive_fire_mastery`，在 `AuraRangeCalc` 范围内按各目标火抗独立结算。服务端创建一次性 `dragontail missile` 表现实体供多人同步，失败命中不释放聚气、不爆炸。
    - [x] ~~完成 Dragon Flight `SrvSt12/SrvDo052` 两阶段位移完成技~~：按 `AuraRangeCalc=par7` 校验目标距离、敌对与城镇边界；第一序列事件检查当前 `Levels.Teleport` 和飞行碰撞，以玩家完整 footprint 在目标 RoomEx 内寻找安全坐标并服务端位移，写入 `SYNC_WARPED` 供多人同步；第二事件切换 `KK` 踢击动画，按 `Param1 + (level-1) * Param2`、`progressive_tohit + ToHitFactor` 和共用 KICK 公式结算，成功命中才释放聚气并消耗耐久。
- [x] ~~完成 Blade Shield / Venom 原生运行时闭环~~
  - Blade Shield 接入 `SrvSt28/SrvDo054`、`AuraLen/AuraRange/PerDelay/AuraFilter`，按 25 帧周期对范围内每个敌对目标独立结算；完整物理包和来源元素伤害均按 `SrcDam=32` 缩放，城镇禁伤、耐久、死亡、状态到期及失去技能停止均纳入服务端权威链。
  - Venom 接入 `SrvDo018` 和 `venomclaws` 状态，按 Skills.txt 注入每帧毒伤并以 `skill_poison_override_length=10` 覆盖物品毒素时长；强 DOT 替换弱 DOT、弱 DOT 不覆盖强 DOT 的 D2MOO 规则已统一到状态系统。
  - 客户端根据权威 `StateP` 显示 `bladeshield` Overlay 和双手 `cgrn` 染色，状态结束恢复装备原色，适用于本地与双客户端快照。
- [x] ~~完成野蛮人 Frenzy `SrvDo009` 与怪物 MonFrenzy `SrvDo109` 原生命中序列~~
  - 玩家必须双持近战武器，两次 SQ 命中事件依次使用右手和左手武器；每击独立结算命中、伤害、耐久和 Berserk 基础点数提供的物理转魔法，第二击按原生 GUID 顺序选择邻近下一目标。
  - 上一击成功结果在下一命中事件应用 `frenzy/monfrenzy` 状态；层数上限等于当前技能等级，持续时间、移动速度和动作速度均读取 `AuraLenCalc/AuraStatCalc`，Double Swing/Taunt 基础点协同纳入 `calc1`。
  - `StateP` 新增运行时层数与动作速度字段，服务端和多人客户端共享相同状态快照；死亡时清除未消费的上一击缓存。
- [x] ~~完成野蛮人 Whirlwind `SrvSt038/SrvDo076` 原生移动与周期攻击~~
  - 起手按 D2MOO 直线路径和 `PLAYER_WHIRLWIND` 碰撞掩码截断终点；近战目标、无效路径、地图碰撞、死亡和状态撤销均会拒绝或结束技能。
  - 移动期间按原生武器攻速断点（4/6/8/10/12/14/16 帧）周期扫描 5 码内敌人，双持交替使用右手/左手武器，每击独立命中、伤害、耐久和状态结算。
  - `whirlwind` 状态、SQ 动画循环及 D2GS 权威位置/状态快照接通；客户端不重复创建本地伤害运行时。新增原生数据、周期断点、双持和结束条件回归。
- [x] ~~完成野蛮人 Berserk `SrvSt039/SrvDo002` 原生命中与防御归零状态~~
  - 对齐 `calc1` 伤害及 Howl/Shout 基础点协同、`calc4=100` 物理转魔法和原生命中率；转换在物理/魔法抗性前完成，保留武器元素伤害并按成功命中结算耐久。
  - 按 `calc2` 创建并刷新 `berserk` 状态，期间提供 `-100%` 防御修正，死亡时清理；状态通过既有权威多人快照同步。
  - `SkillFormula` 支持 Skills.txt 整体带引号公式；补充真实数据和 ECS 起手/关键帧回归。
- [x] ~~完成野蛮人 Howl / Taunt / Shout / Battle Cry 战吼第一阶段~~
  - 按原生 `SrvDo022/SrvHit17`、`SrvDo071`、`SrvDo068/SrvHit18/21` 接入状态导弹与 Taunt 直接目标路径；Howl 等级门槛、恐惧范围/持续时间、Taunt 攻击/伤害降低、Shout/Battle Cry 防御/伤害降低均读取 Skills.txt 原生公式。
  - 统一 `UNITS_CanSwitchAI` 规则：MonStats2 必须支持 WALK、MonStats 必须有 SwitchAI、拒绝 Boss/Unique/SuperUnique/不可打断单位；TERROR/TAUNT 由基类 AI 控制，跨所有怪物 AI 生效并在死亡、进城或跨地图时解除。
  - 状态导弹保持原生穿透/阵营筛选，不走普通伤害路径；补充真实数据、公式、状态与 AI 门槛回归测试。
- [x] ~~完成野蛮人 Battle Orders / Battle Command / War Cry 战吼第二阶段~~
  - Battle Orders 按 Skills.txt 应用最大生命、法力、体力百分比，支持装备/升级重聚合而不逐帧复利，状态结束恢复未加成上限并钳制当前资源；Battle Command 的 `+1 all skills` 进入统一技能等级聚合。
  - War Cry 按 Skills.txt 物理伤害和 `SrvDmg07` 施加原生眩晕，保留 Boss 免疫、Unique 90% 抵抗、零速度怪物免疫、佣兵 13 帧上限与普通单位 250 帧上限。
  - `StateP` 增加技能和最大资源修正快照；服务端与客户端恢复相同战吼状态，实际生命/法力上限继续由权威 `VitalsP` 同步。
- [x] ~~完成野蛮人 Find Potion / Find Item / Grim Ward 尸体工具链首项~~
  - `SrvDo069/072/075` 由服务端关键帧统一处理，死亡目标保留、尸体一次性占用及 `CORPSE_NOSELECT/NODRAW` 状态均与 D2MOO 对齐。
  - Find Potion 使用原生 15 格 Act/难度药水表和 `Param[2]/Param[3]` 分布；Find Item 重用尸体怪物的 TreasureClassEx 并同步额外物品/金币掉落；Grim Ward 按目标体型选择 `SrvMissileA/B/C` 并在尸体坐标生成导弹。
- [x] ~~完成野蛮人 Increased Stamina / Iron Skin / Increased Speed / Natural Resistance 原生被动~~
  - 补齐 Skills.txt 的 `passivestate/passivestat/passivecalc/passiveevent` 数据列，按 D2Common `SKILLS_RefreshSkill` 创建和刷新永久状态列表，不再使用线性近似公式。
  - 最大耐力、防御、移动速度及火/冰/电/毒抗性进入现有权威聚合；支持技能降级、移除、Battle Command 加级但不凭空授予未学技能，并通过 `StateP` 同步状态身份及客户端移动表现。
- [x] ~~完成野蛮人六类武器精通原生对齐~~
  - Sword/Axe/Mace/Pole Arm/Throwing/Spear Mastery 按 `passiveitype` 绑定 ItemTypes；每次攻击按实际武器（双持逐手）选择匹配精通，AR/伤害/暴击分别取最大值，不跨近战与投掷上下文叠加。
  - Frenzy/Whirlwind/Berserk 预计算伤害和普通攻击接入精通；投掷导弹在发射时冻结 AR/伤害/暴击快照，飞行中换装或状态变化不影响原生结果；新增 ECS 与多人状态回归。
- [x] ~~完成野蛮人受击/格挡模式与状态 Overlay 同步~~
  - 近战、旋风斩和投射物命中/格挡由服务端切换原生 `GH/BL` 模式，经 `CofReference` 同步到所有客户端并由 `SequenceHandler` 自动返回 `NU`；死亡和进行中的多段技能不会被错误打断。
  - Frenzy、Berserk、Battle Orders、Battle Command、Shout、Battle Cry 的 States.txt Overlay 映射已接入 `StateOverlaySystem`；状态生命周期来自权威 `StateP`，新增 Overlay 数据与状态启停回归。
- [x] ~~完成德鲁伊 Werewolf / Werebear 原生基础变形~~
  - 修正 221–250 全部德鲁伊技能常量为 Skills.txt 的原生交错 ID；`SrvDo116` 按 `AuraLenCalc/AuraStatCalc` 创建 wolf/bear 互斥状态，Lycanthropy 的 `skill(...lnXY)`、`toht`、攻速、命中、伤害、防御及生命/体力加成均由原生公式计算。
  - 保持实体逻辑类型为玩家，仅按权威 `StateP` 派生 `40/TG` 怪物形态；使用 D2Common 玩家→怪物模式转换和 MonStats2 模式回退，状态到期、死亡及重连后均恢复或重建正确外观。
  - 原生数据、状态生命周期、执行器关键帧和表现专项纳入回归；相关 8 组共 71 个用例通过，D2GS 编译通过。
- [x] ~~完成德鲁伊 Feral Rage / Maul 聚能攻击和形态技能限制~~
  - 按 D2MOO `SrvSt56/SrvDo120` 拆分起手命中记录与关键帧结算；失败、格挡、错误形态和越距均不增加层数，成功命中按 `calc2` 增层并刷新 `AuraLenCalc`。
  - Feral Rage 的移动速度/吸血和 Maul 的增强伤害/眩晕均按当前 `STAT_SKILL_FRENZY` 层数重算，首击新层不反向增强本次攻击；状态在失去所需狼/熊形态时清理。
  - 补齐 Skills.txt `restrict/state1/state2/state3`，统一施法入口执行原生形态限制；状态 ID、持续时间、技能等级、层数和移动速度通过既有 `StateP` 广播给多人客户端。
- [x] ~~完成德鲁伊 Rabies / Fire Claws 变形攻击~~
  - `SrvSt57/SrvDo121` 使用狼形态门槛、一次命中记录、原生 8.8 定点毒伤、Rabies 状态和附着感染控制体；控制体按剩余毒伤时间传播并保留初始施法者归属。
  - `SrvSt58/SrvDo002` 使用狼/熊双形态门槛，同一预计算近战记录只结算一次武器物理伤害，并按 `EDmgSymPerCalc` 叠加 Firestorm/Molten Boulder/Volcano/Eruption 硬点协同火伤。
- [x] ~~完成德鲁伊 Hunger 变形攻击~~
  - `SrvDo122` 按 `calc1/calc2/calc3` 计算武器伤害、生命偷取和法力偷取，复用一次权威命中记录并在关键帧扣耐久。
- [x] ~~完成德鲁伊 Shock Wave 范围导弹与眩晕~~
  - 对齐 `SrvDo008` 的 `calc1=5` 五路投射物、一次施法共享命中集合、Skills.txt 物理伤害快照和 `SrvDmg07` 眩晕；眩晕优先读取 Missiles.txt `dParam1`，否则按 Skills.txt `Param1 + (level - 1) * Param2` 计算。
  - 保留原生熊形态限制、Boss/不可移动/Unique/雇佣兵眩晕资格与时长上限，并通过既有 `StateP` 同步 `STUNNED` 权威状态。
- [x] ~~完成德鲁伊 Fury 多目标连续攻击~~
  - 对齐 `SrvSt37/SrvDo013` 的 `calc1` 攻击次数（2–5 击）、`calc2=ln34` 技能增伤、`ToHit/LevToHit`、狼形态限制和每击武器耐久。
  - 每击按原生 GUID 邻近规则在 `UNITS_GetMeleeRange + 4` 范围内重选目标；目标死亡、离开范围、形态丢失或无敌对目标时安全结束，重复动画由服务端序列控制并通过日志同步。
- [ ] 完成德鲁伊 Raven、藤蔓、灵魂、狼群和灰熊召唤所有权及生命周期。
- [ ] 完成德鲁伊 Firestorm、Fissure、Volcano、Armageddon、Hurricane 等元素区域技能。

### P2：世界交互和多人闭环

- [ ] 补齐对象状态持久化及多人即时快照广播。
- [ ] 补齐第一章任务多人资格、对话变体、奖励幂等和重连恢复。
- [ ] 补齐 NPC 原生库存刷新、雇佣/复活和断线重连恢复。
- [ ] 完成玩家交易、Party 可见性、敌对边界和异常顺序处理。
- [ ] 完成 D2S 版本校验、完整 section/mask 和原版样本回归。

### P3：扩展范围

- [ ] 按“数据表 → 拓扑 → 对象 → 怪物 → 任务 → 碰撞”顺序逐幕扩展 Act 2–5。
- [ ] 完成 D2MOO_JAVA 中仍为 fallback 的 `DrlgMaze`、`D2Cmp`、`DataTbls` 和房间生命周期。
- [ ] 扩充离屏渲染、多客户端和固定种子地图视觉回归。

## 已完成记录

- 2026-09-08：完成 P0-1 第一阶段；新增无损 TXT、稳定字段差异和不可逆摘要工具，按
  D2MOO 1.10f 字段接入 `States.txt`，并以完整 1.10f MPQ 固定五张核心表 golden manifest。
- 2026-09-08：完成 P0-1 `ItemStatCost` 阶段；建立原生行 ID、运算/存档字段无损投影，
  并用 359 行原始 1.10f 表验证；旧 `damagerelated` 字段改为 D2MOO 的 bit 语义。
- 2026-09-08：完成 P0-1 `Skills` 阶段；D2MOO 238 字段 schema、字段类型诊断和原始公式
  访问接入，357 行 1.10f MPQ 数据全量通过，旧解析器未参与新投影。
- 2026-09-08：完成 P0-1 `Missiles` 阶段；D2MOO 146 个加载字段机械核对无缺失，684 行
  真实 1.10f 表通过无损 schema，并区分原生 bit 与 byte integer，兼容 `*N` 和非二进制
  bit 原始异常值；数据、导弹房间跟踪、火焰命中和亚马逊箭表现回归通过。
- 2026-09-08：完成 P0-1 `MonStats` 与五表总验收；253 个原生加载字段和 `hcIdx` 诊断列
  接入，705 行原表正确投影为 704 个怪物记录；新增五表统一字段报告并将 States/Stat
  bit 读取统一为 D2MOO 语义。五表真实 1.10f、状态生命周期和 Overlay 回归全部通过。
- 2026-09-08：开始 P0-2 显式 Stat 聚合；借鉴 libd2 的 buff delta 生命周期和 dark-magic
  的命名来源测试，新增 `Base -> Add -> Percent` encoded resolver，并将 Battle Orders 类
  最大资源修正迁到显式未修正基线，消除浮点反算及逐 tick 复利风险。
- 2026-09-02：为 `ItemData.updateStats` 和 `CharData.onUpdated` 增加不完整物品/角色记录保护；
  `NativeGemShrineServiceTest` 及地图、神殿、Fallen Shaman、双客户端掉落回归集合全部通过。
- 2026-09-02：完成 P1 原生物品生成首项；新增纯数据和真实 Excel/MPQ 双层测试，物品、掉落、修理、交易与 Countess 回归共 35 个用例通过。
- 2026-09-03：完成亚马逊召唤专项；Decoy/Valkyrie 原生数据、ECS 创建、生命周期、奖励归属及战斗/导弹回归集合共 28 个用例通过，D2GS 编译通过。
- 2026-09-03：完成亚马逊 Guided Arrow/Strafe/Pierce 首轮移植；导引箭追踪、Strafe 原生目标序列、穿透命中去重与技能穿透概率接入，技能/导弹回归集合通过，D2GS 编译通过。
- 2026-09-03：接入 Dodge/Avoid/Evade 被动防御判定到统一 CombatSystem；按近战/远程/移动上下文复用原生 75% 上限，补充 100% 确定性测试。实际移动状态传递和动画反馈仍待补齐。
- 2026-09-03：把真实 Velocity 移动状态传入近战与导弹战斗上下文；补齐 Magic/Fire/Cold/Exploding/Ice/Immolation/Freezing Arrow 的 `CltMissile` 创建、目标方向和资源验证，并阻止多人客户端重复创建 D2GS 已同步的导弹。
- 2026-09-03：完成亚马逊弓/弩弹药权威链路；接入原生 `noammo/decquant`、弓箭/弩箭匹配、发射时单次扣量和空箭袋拒绝，并通过 `PlayerP` 向本地多人客户端同步活动箭袋余量；亚马逊、箭矢表现和战斗管线共 18 个用例通过，D2GS 编译通过。
- 2026-09-03：接入 D2MOO `EVENTTYPE_STATREGEN` 的 `item_replenish_quantity` 分支；按 `max(125, 2500/rate+1)` 帧恢复 1 点并在 `maxstack` 停止，专用 2 个用例及 D2GS 编译通过。
- 2026-09-03：完成多人技能表现同步首项；服务端接入原生 `SrvDo047` 暗影斗篷范围状态（`DIMVISION`、持续时间和防御降低），客户端通过 `StateP` 快照自动恢复状态 Overlay，并补充原生数据/ECS 回归测试。
- 2026-09-03：完成亚马逊元素箭服务端伤害首项；Skills.txt 快照接入所有技能导弹，Magic/Fire/Cold 转伤、Ice/Freezing 冰冻、Exploding/Freezing 的 SrvHit04→SrvHit01 范围子导弹及 Lightning Fury 子导弹元素伤害均加入回归门槛。
- 2026-09-03：完成 Immolation Arrow 火场生命周期；按 D2MOO `SrvHit09/SrvDmg03` 生成 immolationfire 圆形区域，保留 100 帧并按 `DamageRate=41` 周期伤害，火场导弹通过统一同步管线广播。
- 2026-09-03：接入统一持续毒云管线；Poison Javelin 按 `SrvDo02` 沿途生成 poisonjavcloud，Plague Javelin 按 `SrvHit02` 生成 plaguejavcloud，毒雾陷阱与 corpsepoisoncloud 复用同一生命周期、区域施毒和多人导弹同步；冰冻箭增加区域内多目标冻结 ECS 回归。
- 2026-09-03：完成刺客 `SrvDo044/SrvDo045` 陷阱召唤首项；服务端按原生 `summon/pettype/petmax` 创建并替换 `assassintrap`，新增 `AssassinTrapSystem` 实现权威目标搜索、攻击节拍、导弹快照和射击耗尽生命周期，补充 SrvDo045 ECS 回归。
- 2026-09-03：完成 Blade Sentinel 的原生 `AI Fn102` / `Missile SrvDo20` 首轮移植；Blade Creeper 控制实体在起点/目标点间往返，单一附着导弹继承施法者伤害并按 `NextHit` 去重，补充双向路径、导弹跟随、控制实体清理测试。
- 2026-09-03：完成 Wake of Fire 的原生 `SrvDo125` / `SrvDo31` 首轮移植；maker 到达目标端点后生成相反方向的双 `wake of destruction` 火焰波，继承施法者伤害归属并加入 maker/子导弹 ECS 回归。
- 2026-09-03：完成 Inferno Sentry 的原生 `SrvDo95` 首轮移植；按 `calc2` 维持喷射窗口、按 `calc3` 重复发射并更新目标朝向，单枚导弹路径按 `calc1` 计算，协同技能等级由陷阱所有者解析，并加入路径/节拍/朝向 ECS 回归。
- 2026-09-03：完成 Death Sentry 的原生 AI Fn104 / `SrvDo55` 首轮移植；合法尸体筛选和保留、同尸体防重、40%–80% 尸体生命伤害、50% 物理/火焰拆分、范围结算、爆炸表现、闪电回退及 Fire Blast 射击次数协同均接入权威陷阱生命周期。
- 2026-09-03：完成 Charged Bolt/Lightning Sentry 首轮移植；Charged Bolt Sentry 按原生 `calc1` 生成多枚导弹，并按 D2Common 种子和 `PATHTYPE_CHARGEDBOLT` 每 2 子格更新折线路径；Lightning Sentry 按 `Fn101` 的目标距离、攻击几率和 stall 参数运行；两者补齐关联玩家技能的元素伤害快照，以及多导弹、射击消耗和网络同步 ECS 回归。
- 2026-09-03：完成刺客聚气状态首项；`SrvDo034/035` 接入统一近战命中记录，失败/格挡/越距不蓄力，Tiger/Cobra/Fists 等原生 `AuraState` 最多三层并刷新生命周期；层数通过既有 `StateP` 同步到多人客户端且不污染移动速度。
- 2026-09-03：完成 Tiger/Cobra/Fists 完成技直接释放首项；四类原生完成技进入统一命中记录，Tiger 增伤、Cobra 分层双吸、Fists Skills.txt 火伤和物理转火在成功命中后结算并消费全部聚气，失败命中继续保留。
- 2026-09-03：完成 Fists of Fire `SrvDo038/039` 阶段效果；三层按 `PrgStack` 同时释放二层范围伤害和随机 `fistsoffirefirewall` 火场，服务端导弹保留所有权、伤害快照和 64 帧生命周期；刺客武技、陷阱、战斗与状态 5 组共 30 个用例通过，D2GS 编译通过。
- 2026-09-03：完成 Claws of Thunder `SrvDo036/037` 三阶段释放；直接闪电伤害、64 路 Nova 和 16 条 Charged Bolt 路径均按原生 `PrgStack/SrvPrgFunc/PrgCalc` 接入，服务端导弹共享 Nova 命中去重并保留技能伤害快照；5 组共 34 个用例通过，D2GS 编译通过。
- 2026-09-03：完成 Blades of Ice `SrvDo038/039` 三阶段释放；直接冰伤、二层半径 6 范围冰伤/减速、三层主目标冻结及半径 3 随机冰块均按原生数据接入；零速度冰块增加 Range 帧权威回收，5 组共 38 个用例通过，D2GS 编译通过。
- 2026-09-04：完成 Phoenix Strike `SrvDo040/143/041` 三阶段释放；按非叠加规则分别创建 Meteor、7 路 Chain Lightning 和 16 枚 Chaos Ice，补齐陨石 18 点火场、闪电续跳、冰弹转向及冻结伤害快照；5 组共 43 个用例通过，D2GS 编译通过。
- 2026-09-04：完成 Dragon Talon `SrvSt24/SrvDo042` 连续踢击；接入 `calc1` 次数、靴子力量/敏捷踢击伤害、每击独立命中和耐久、聚气单次消费、末击分类概率与碰撞安全击退；刺客、战斗、状态与原生公式 6 组共 51 个用例通过，D2GS 编译通过。
- 2026-09-04：完成 Dragon Claw `SrvSt25/SrvDo046` 双爪序列；左右爪按 `A2 → S4` 分别结算命中、当前手伤害和耐久，聚气仅首个成功命中消费，单爪/徒手退化为单击；补齐 MonFrenzy 共用原生函数的 `SrvSt64` 目标校验。刺客、战斗、状态与原生公式 6 组共 54 个用例通过，D2GS 编译通过。
- 2026-09-04：完成 Dragon Tail `SrvSt27/SrvDo050`；保存起手踢击记录到命中帧，补齐 KICK 力量/敏捷基础伤害、主目标物理结算、Tiger Strike 后实际物理伤害到范围火焰的转换、Fire Mastery、逐目标火抗和 `dragontail missile` 多人表现。刺客、战斗、状态、原生公式和客户端表现 8 组共 60 个用例通过，D2GS 编译通过。
- 2026-09-04：完成 Dragon Flight `SrvSt12/SrvDo052`；按原生 SQ 序列拆分 SC 传送与 KK 踢击事件，接入目标距离/敌对/城镇校验、`Levels.Teleport`、RoomEx 安全落点、服务端 `SYNC_WARPED` 位移，以及 Param/ToHit/KICK 完成技伤害和聚气消费。刺客、动作、序列、地图碰撞、战斗与状态 9 组共 71 个用例通过，D2GS 编译通过。
- 2026-09-04：完成 Blade Shield / Venom；补齐 Skills.txt 缺失列、原生 `lnXY` 等级公式、周期范围伤害、`SrcDam` 缩放、毒伤覆盖、持续 Overlay/武器染色及多人状态快照。刺客专项回归集合 6 组共 64 个用例通过，D2GS 编译通过；刺客职业专项更新为 100%。
- 2026-09-04：完成 Frenzy / MonFrenzy 原生序列；接入双持左右手独立命中、GUID 邻近换目标、上一击触发状态、技能等级层数上限、AuraStat 速度、Double Swing/Taunt 协同、Berserk 物理转魔法、耐久及多人状态快照。相关 9 组回归通过，D2GS 编译通过；野蛮人职业专项更新为约 63%。
- 2026-09-04：完成 Whirlwind `SrvSt038/SrvDo076`；接入直线路径/碰撞终点、近战距离拒绝、原生攻速断点周期扫描、双持交替命中、耐久、状态生命周期及多人权威位置表现。旋风斩专项回归与既有职业回归通过，D2GS 编译通过；野蛮人职业专项更新为约 75%。
- 2026-09-04：完成 Berserk `SrvSt039/SrvDo002`；接入带引号 `calc2`、Howl/Shout 协同、100% 物理转魔法、防御归零状态、单次命中和耐久结算。相关 11 组 64 个回归用例通过，D2GS 编译通过；野蛮人职业专项更新为约 85%。
- 2026-09-04：完成 Howl / Taunt / Shout / Battle Cry 战吼第一阶段；对齐原生 `SrvDo022/SrvHit17`、`SrvDo071`、`SrvDo068/SrvHit18/21`、AuraTargetState 和 Skills.txt 公式，统一 `UNITS_CanSwitchAI` 的 WALK/SwitchAI/Boss/Unique/不可打断门槛，并由基类 AI 接管 TERROR/TAUNT 特殊行为。新增 5 个原生数据、状态、导弹和门槛专项用例通过，D2GS 编译通过；野蛮人职业专项更新为约 90%。
- 2026-09-04：完成 Battle Orders / Battle Command / War Cry 战吼第二阶段；最大生命/法力/体力百分比聚合支持重算与到期恢复，War Cry 对齐 Skills.txt 伤害及 `SrvDmg07` 眩晕资格，`StateP` 补齐技能和最大资源修正快照。原生数据、导弹伤害、友方召唤、资源重算和多人序列化专项回归通过；野蛮人职业专项更新为约 95%。
- 2026-09-04：完成 Increased Stamina / Iron Skin / Increased Speed / Natural Resistance；接入 Skills.txt 原生被动状态、属性和 `ln12/dm12` 递减公式，修复 Natural Resistance 线性近似及 Battle Command 激活未学被动问题。新增数据、ECS 生命周期、非复利、战斗与多人快照回归；野蛮人职业专项更新为约 99%。
- 2026-09-04：完成野蛮人 Sword/Axe/Mace/Pole Arm/Throwing/Spear Mastery；按 D2Common ItemTypes 层逐手匹配，AR/伤害/暴击分别取最大值，投掷导弹发射时保存精通快照并通过 CombatSystem 权威结算；新增六类状态、换装上下文、预计算技能和真实 ECS 导弹快照测试，相关回归及 D2GS 编译通过。
- 2026-09-04：完成野蛮人 GH/BL 受击与状态 Overlay 表现首项；服务端近战、旋风斩、导弹命中/格挡切换原生动画模式并经 `CofReference` 广播，客户端恢复 Frenzy/Berserk/Battle Orders/Battle Command/Shout/Battle Cry 持续 Overlay；状态启停、原生 Overlay 数据和战斗回归通过，D2GS 编译通过。

- 2026-09-04：完成德鲁伊 Werewolf/Werebear `SrvDo116` 基础变形；纠正德鲁伊全部原生技能 ID，接入 Lycanthropy `skill(...lnXY)`、狼/熊互斥状态、原生属性、`40/TG` 形态 COF、模式回退及多人状态派生表现。相关 8 组共 71 个用例通过，D2GS 编译通过；德鲁伊专项更新为约 50%。
- 2026-09-04：完成德鲁伊 Feral Rage/Maul `SrvSt56/SrvDo120`；接入原生命中预判、武器伤害、耐久时序、层数上限/刷新、Feral 移速与吸血、Maul 增伤与眩晕、Skills.txt 形态限制和失去形态清理。相关 6 组共 23 个用例通过，D2GS 编译通过；德鲁伊专项更新为约 60%。
- 2026-09-04：完成德鲁伊 Rabies/Fire Claws；接入 `SrvSt57/58` 预计算战斗记录、`SrvDo121/002` 单次消费、Rabies 原生定点毒伤/目标状态/附着控制体/邻近传播，以及 Fire Claws 双形态、元素伤害和四技能硬点协同。德鲁伊专项更新为约 68%。
- 2026-09-04：完成德鲁伊 Hunger `SrvDo122`；接入狼/熊形态校验、原生 `calc1/calc2/calc3` 伤害与双吸、命中/格挡/耐久/状态/死亡时序及调试日志。德鲁伊专项更新为约 72%。
- 2026-09-04：完成德鲁伊 Shock Wave `SrvDo008/SrvDmg07`；接入五路导弹、共享命中去重、Skills.txt 物理伤害、40+15/级帧眩晕、原生目标资格与权威状态同步日志。专项及通用回归共 17 个用例通过；德鲁伊专项更新为约 76%。
- 2026-09-04：完成德鲁伊 Fury `SrvSt37/SrvDo013`；接入原生 2–5 击公式、狼形态门槛、`ln34` 增伤、每击独立命中/耐久和 GUID 邻近目标链，修正多击动画音效重复播放；德鲁伊专项回归与既有变形回归通过，D2GS 编译通过，专项更新为约 80%。
- 2026-09-07：新增 1×1 隐藏窗口真实营地启动门槛；固定种子执行 Act 1 DRLG、营地实体、玩家创建和三个生产渲染帧，并让 LWJGL 线程异常正确传递为 Gradle 失败。测试先复现并修复单人任务控制器错误依赖多人同步器，以及共享 Idle AI 用 `entity=-1` 查询组件导致的启动崩溃；1.10f 主基线和 1.14 兼容资源均通过。

## 当前下一项

- [x] ~~完成多人地面掉落归属窗口广播~~
  - `ItemP` 在保持旧字段兼容的前提下追加 owner/party 截止时间和金币队伍分配标记；
    服务端所有掉落来源（怪物 TC、玩家丢弃、死亡金币）统一写入 ECS 元数据。
  - `NetworkSynchronizer` 的现有 RoomEx/Level 可见性广播会把归属元数据随地面物品
    基线和增量发送到每个可见客户端；客户端创建和更新实体时只读应用窗口，不绕过
    服务端 claim 校验。金币部分拾取的数量变化仍通过同一实体快照同步，完全拾取继续
    使用删除快照，避免第二客户端持有幽灵掉落。
  - 新增 `GroundDropOwnershipMetadataTest`，并通过 `GoldPickupServiceTest`、D2GS 编译。

- [x] ~~完成队伍金币拾取后的多人背包修正广播~~
  - `PartyGoldShareService` 为队友增加背包 revision 后，D2GS 现在按实际被分配的
    连接逐一发送完整 `ItemMoveResult` 快照；不会把拾取者的背包快照错误广播给其他
    客户端，也不会污染幂等请求缓存。
  - 部分金币拾取仍保留地面实体并依赖 `EntitySync` 数量增量；全部拾取继续由删除
    快照清理双方客户端的地面实体。

- [x] ~~完成多人拾取幂等、响应重排与幽灵地面物品修正第一阶段~~
  - 服务端在死亡/背包状态校验前处理同连接、同 request ID 的精确重传，首次请求已
    消耗物品或玩家随后死亡时仍重放首次结果；同 request ID 不同意图继续明确拒绝。
  - 修正失败 `Outcome` 默认误标为“消耗地面实体”；所有失败拾取现在保留实体并返回
    地面校正。完整成功拾取的 `ItemMoveResult` 直接携带被消费的 server entity ID 和
    空 item data，客户端无需等待 `EntitySync.deleted` 即可移除幽灵物品。
  - 客户端按 inventory revision 拒绝延迟到达的旧 `ItemMoveResult`，同 revision 的失败
    快照仍可用于纠偏；若服务器确认地面物品存在但客户端缺失，则请求完整基线，避免
    用缺少 Level/RoomEx 上下文的紧凑结果错误重建实体。
  - 单元测试覆盖 revision 重排、同 revision 纠偏、失败不消费和请求缓存；1.10f 真实
    双客户端 Fallen/Shaman 场景覆盖同包连续重传、另一客户端争抢拒绝、两端删除可见，
    输出 `duplicate=true contentionRejected=true deleted=true`。

- [x] ~~完成部分金币与地面 claim 生命周期第一阶段~~
  - 部分金币拾取在达到携带上限时只修改权威地面数量并释放 in-flight claim，第二名
    玩家可以继续拾取剩余数量；完整消费仍保留 claim 到实体删除边界，防止删除包延迟
    时被重复领取。
  - `GroundDropOwnership` 增加显式 `discard(entityId)` 生命周期钩子和 ID 复用测试；
    新实体创建时清理旧 claim，避免 Artemis 实体 ID 复用继承上一件掉落的归属状态。
    由于 Artemis `world.delete` 是延迟提交，拾取路径不会提前调用 discard，避免同一
    tick 的竞争请求绕过 claim；实际删除观察点可安全调用该钩子。
  - `GoldPickupServiceTest` 覆盖部分金币→剩余数量→第二玩家完整拾取→旧 claim 拒绝，
    以及实体 ID 回收；重连可见性门槛 `headlessReconnectVisibility` 在 1.10f 资源下
    继续通过。

下一小步：完成部分金币拾取后断线重连、重连基线地面数量/归属窗口校验，以及实体 ID
复用不继承旧地面快照的专项门槛。

Werewolf/Werebear、Feral Rage/Maul、Rabies/Fire Claws、Hunger、Shock Wave、Fury 以及召唤物所有权与生命周期已完成，德鲁伊形态限制、聚能状态、感染传播、五路范围伤害、多人权威眩晕、多目标连续攻击和 PetType/PetMax 生命周期已接通。

- [x] ~~完成德鲁伊召唤物所有权与生命周期~~
  - 对照 D2MOO `SKILLS_SrvDo114_Raven`、`SKILLS_SrvDo115_Vines`、`SKILLS_SrvDo119_DruidSummon` 和 `PlayerPets.cpp`，接入召唤等级、PetMax、具体 PetType、非零 group 互斥替换、主人死亡/尸体标记清理、死亡动画宽限、地图切换 warp 标志和多人实体归属。
  - `SummonedPetSystemTest`、`NativeDruidSummonDataTest`、`DruidSummonIntegrationTest` 以及全部 `*Druid*Test` 通过；1×1 真实隐藏营地测试 `:desktop:offscreenCamp` 使用 Diablo II 1.10f 资源通过。

- [x] ~~完成固定 25Hz 权威 Sim Tick 第一阶段~~
  - D2GS 三个服务入口统一使用 `AuthoritativeSimulation`，固定 `Animation.FRAME_DURATION=0.04s`，按“网络入站 → ECS 单帧 → 网络出站”顺序处理。
  - 首次 tick 绑定唯一写线程；跨线程调用直接拒绝，tick 序号、实际步长和写线程可被集成测试读取。
  - 新增 `AuthoritativeSimulationTest`，真实 `headlessSimulationTick` 协议测试在 1.10f 资源下通过（1.2 秒内 31 帧、步长 0.04s、写线程稳定）；1×1 真实隐藏营地测试继续通过。
  - 本阶段没有把本地单人 `GameScreen.render()` 改成独立模拟线程；迁移渲染线程逻辑需要单独处理 LibGDX/Artemis 上下文。

- [x] ~~完成权威快照 tick/服务器时间与双客户端顺序观测~~
  - `EntitySync` 追加兼容的 `tick` 和 `serverTimeMillis` 字段；服务端序列化在固定 Sim Tick 的线程局部时钟上下文中生成，所有同帧实体共享相同时间基准。
  - 状态内容去重与时钟信封分离，tick 变化不会把未变化实体误判为每帧全量更新。
  - 旧客户端发送的 `EntitySync` 仍使用默认 0 字段，服务端保持兼容；服务端快照不再依赖网络线程本地时间。
  - 正式客户端维护单调权威时间线，接受同 tick 多实体批次和旧协议零值，拒绝会回滚状态的旧 tick/旧服务器时间。
  - 新增 `headlessSnapshotOrder` 双客户端真实协议门槛，验证两个客户端 tick/服务器时间单调、存在共同 tick，并在移动后继续接收快照；1.10f 资源实测通过。
  - 1×1 真实隐藏营地回归继续通过。

- 2026-09-07：完成本地单人固定步进与渲染解耦第一阶段；新增 `FixedStepAccumulator`，`GameScreen` ECS 改为 25Hz 固定 tick、每帧最多 4 步追赶并在暂停时清空 backlog。`FixedStepAccumulatorTest`、`GameScreenDeltaTest`、D2GS/Netty 编译和 1×1 真实隐藏营地（1.10f）均通过。

- [x] ~~完成本地单人固定步进与渲染解耦第一阶段~~
  - `GameScreen` 使用无 LibGDX 依赖的 `FixedStepAccumulator`，以 25Hz（40ms）固定 tick 驱动 ECS；渲染、UI 和输入仍在主线程按可见帧运行。
  - 每帧最多追赶 4 个模拟 tick，后台/长帧不会形成无限 backlog；暂停时清空半 tick，恢复只从新的固定 tick 开始。
  - 新增 `FixedStepAccumulatorTest`，覆盖 40/80ms 步进、分数帧累计、2 秒长帧限幅、NaN/负数/Infinity、reset 和 60Hz 稳定性；核心测试、D2GS/Netty 编译及 1×1 真实隐藏营地回归通过。

- [x] ~~完成本地/多人模拟时钟统一与逐帧渲染隔离~~
  - 新增共享 `SimulationClock`，本地、D2GS、客户端动画/Overlay、多人收发和 Box2D 统一使用 25Hz/40ms 原生时钟；移除 `GameScreen` 路径残留的 60Hz 网络与物理 interval，避免 Artemis accumulator 漂移及 Box2D 每秒仅推进约 0.417 秒。
  - 新增 `ClientRenderSystemRunner`，将所有 `@GpuSystem` 从固定模拟循环隔离，并按每个可见帧顺序执行；模拟追赶不会重复渲染，60Hz 下没有模拟 tick 的帧也不会漏绘地图、标签或自动地图。
  - `SimulationClockTest` 连续 250 tick 无余量漂移，渲染隔离、固定步进、GameScreen delta 和 Box2D 测试通过；1.10f 双客户端权威快照顺序及 1×1 真实营地回归通过。

- [x] ~~完成固定 Tick 核心阶段顺序首项~~
  - D2GS 与 `GameScreen` 将 `StateUpdater`、`MissileCollisionSystem` 提前到 `Actioneer`/AI
    前执行，形成状态衰减 → 导弹移动/碰撞 → 单位行为的稳定 25Hz 顺序；死亡/销毁仍由
    `UnitLifecycleSystem` 和专用尸体系统处理，网络同步在 ECS 帧完成后执行。
  - 状态、导弹、死亡生命周期和双客户端相关回归及 D2GS 编译全部通过。
  - 附加 `headlessMultiplayer` 的怪物移动子场景在固定种子下未找到可用近战怪物而超时；双客户端连接/快照门槛已单独通过，此失败不属于客户端时钟回归，后续应让场景显式生成目标以消除数据随机性。

- [x] ~~完成 25Hz 权威状态的客户端表现插值第一阶段~~
  - 远程玩家、怪物和导弹保存前后两个权威位置/朝向快照，以服务器 tick/时间差作为插值时长；仅渲染阶段临时覆盖坐标，渲染结束立即恢复权威 Position/Angle，不影响碰撞、寻路和战斗。
  - 旧 tick/旧服务器时间拒绝；丢包时按实际时间间隔延长插值（最多 3 tick）；`SYNC_WARPED` 和大距离跳变立即吸附，避免跨地图拉伸；本地玩家继续使用预测位置。
  - 新增 `AuthoritativeTransformInterpolatorTest` 和 `AuthoritativeInterpolationSystemTest`，覆盖 60/120Hz、丢包、旧快照、短/长 Warp、朝向归一化及权威坐标不回写；双客户端快照顺序与 1×1 真实隐藏营地（1.10f）回归通过。

- [x] ~~完成客户端预测与服务器校正第一阶段~~
  - `EntitySync` 兼容追加客户端输入序号、服务端 ACK 和拒绝序号；D2GS 按连接维护单调输入边界，拒绝伪造实体所有权、重复/倒序包、非有限坐标、超速、超距和穿越地图碰撞。
  - 客户端保存最多 128 个固定 tick 位移，收到自己的权威快照后删除已确认输入、从权威位置重放未确认输入；旧 ACK 不允许回滚，队列溢出、碰撞拒绝、Warp、死亡及超过 8 子格的偏差立即硬校正。
  - 中等误差立即校正模拟/碰撞位置，仅在渲染阶段保留并于 120ms 内衰减视觉偏移；渲染结束恢复模拟坐标，小于 0.125 子格的误差不增加视觉修正。
  - 新增预测队列、服务端序号门、移动合法性和渲染偏移测试，覆盖 ACK 清理、丢包、旧确认、队列上限、重连、碰撞拒绝、小/中/大误差；真实双客户端协议验证序号 1、丢包后的序号 3、重复旧输入和拒绝序号，1.10f 资源通过；1×1 真实隐藏营地继续通过。

- [x] ~~完成服务端原生移动意图协议第二阶段~~
  - 正式客户端改发 `Walk/Run × Location/Entity`，携带单调 sequence、已观测服务端 tick 和目标 tick；位置目标使用本地预测路径的最终可达点，实体目标转换为服务端网络 ID。
  - D2GS 从连接派生玩家身份，按目标 tick 排队，在同一 25Hz 单写者 tick 内设置跑/走状态并调用 `Actioneer + Pathfinder`；限制未来/迟到窗口、每轴 50 子格和地图/目标实体合法性，支持丢包跳号、精确重传幂等及同序号冲突拒绝。
  - ACK 只在意图实际应用或拒绝后发布；重复目标不重复寻路。生产模式拒绝客户端 `EntitySync` 绝对 Position/Velocity/COF 上传，旧直传仅保留为显式开启的 headless 测试桥。
  - 双客户端真实协议门槛验证目标 tick、序号 1/3 跳跃、非法远距拒绝、冲突重传和旧绝对坐标拒绝；1.10f 资源下通过。1×1 真实隐藏营地三个生产渲染帧继续通过。

- [x] ~~完成近战攻击 tick 位置快照与原生 hitbox 第一阶段~~
  - 一比一移植 D2Common `D2Common_10399` 的 64 项小距离查表、Size 1/2/3 footprint 修正和大距离近似；玩家武器使用 `Weapons.RangeAdder`，怪物兼容 `MonStats2.MeleeRng=255` 的 2HT 特例。
  - 本地和 D2GS 每个 25Hz 权威 tick 在输入、AI 与动画关键帧前冻结实体整数子格坐标、Size、Zone 和 Room；普通攻击、Frenzy、Fury、Dragon Talon/Claw/Tail 等共享近战链只读取起手指定 tick，历史缺帧明确拒绝，不回退到客户端预测、渲染插值或当前实时位置。
  - `CastSkillRequest` 兼容追加 sequence、observedServerTick 和 targetTick；D2GS 按目标 tick 排队，支持精确重传幂等、同序号冲突拒绝及未来/迟到窗口，并从历史帧解析实体目标坐标。`PLAYER_FLYING` 地图射线同步接入 DT1 missile barrier 与动态门引用层，普通固体对象不再错误阻挡近战射线。
  - 原生距离、位置历史、攻击起手后目标移动、协议幂等、地图/动态门碰撞及 Amazon/Assassin/Barbarian/Druid 多段近战集中回归通过；1.10f 双客户端真实 Fallen 死亡、Shaman 复活、复活无奖励、原生 NoDrop 与跨客户端拾取闭环通过。真实 1×1 隐藏营地三个渲染帧通过，并修复本地模式可选网络时钟接线和 headless 观察点超出 Shaman AiDist 的测试缺陷。

- [x] ~~完成多人战斗意图 ACK、拒绝结果与客户端动作校正~~
  - 新增兼容 `CastSkillResult`，返回 `sequence`、`appliedTick`、`authoritativeTick`、源/目标实体、接受/拒绝原因及终态标志；服务端对未来/迟到、目标快照、技能所有权、近战越距和玩家死亡等所有分支都返回明确结果。
  - 精确重传返回非终态重复确认，不会撤销原请求；客户端按单调 sequence 消费结果，最终拒绝会清理本地 Casting、Sequence 和 Target，避免攻击动画/目标锁死。服务端接受结果在动作排队的同一 tick 发送，拒绝不会执行任何资源或伤害副作用。
  - `NetworkedCombatTransportTest` 覆盖结果包字段；1.10f 双客户端 Fallen/Shaman 闭环验证 ACK、复活、NoDrop、跨端拾取和多次迟到拒绝均通过。

- [x] ~~完成多人快照丢失后的 tick 重同步第一阶段~~
  - 新增兼容 `SnapshotResyncRequest` 与 `SnapshotBaseline(BEGIN/END)` 协议；客户端在权威 tick 出现明显缺口或连续 3 秒无快照时限频请求，服务端在固定 Sim Tick 单写线程发送完整实体基线并以 BEGIN/END 标记边界。
  - 客户端基线期间暂停旧快照应用、清理延迟实体和插值历史，END 以服务端 tick/时间原子重置时间线；服务端按连接缓存 request/baseline ID，重复请求不会产生游戏状态副作用。
  - 新增 `AuthoritativeSnapshotTimeline.resetTo`、协议往返测试；核心协议/时间线测试、D2GS 编译及 `headlessSnapshotOrder`（双客户端、1.10f 资源）通过。

- [x] ~~完成多人快照重同步第二阶段~~
  - `SnapshotBaseline` 扩展当前难度各幕传送点激活位图；服务端从权威 `CharData` 生成，客户端在 END 标记原子应用，避免重连或丢包后传送点状态回退。
  - 任务记录、金币、弹药、技能点等既有 `PlayerP` 权威字段继续随实体基线同步；本阶段补充传送点非实体状态并保持旧客户端兼容。
  - 协议往返、时间线测试及 D2GS 编译通过。

- [x] ~~完成多人快照重同步第三阶段（基础）~~
  - 基线携带服务端物品容器 `inventoryRevision`，客户端在 END 标记重置 `NetworkedClientItemManager` revision，避免后续移动请求因旧版本持续失败。
  - 客户端收到本地 `SYNC_WARPED` 或死亡状态时自动触发限频重同步；与 tick 缺口/静默检测共用 BEGIN/END 原子流程。

- [x] ~~完成多人快照重同步第三阶段（端到端基础门槛）~~
  - 新增 `headlessSnapshotResync`：双客户端连接后暂停一端收包 2.5 秒，发送重同步请求，验证请求端收到 BEGIN/完整实体快照/END，另一端不收到基线标记。
  - 1.10f 资源真实 D2GS 运行通过：请求端基线包含 99 个实体，双客户端隔离成立；D2GS/核心编译及协议测试通过。

- [x] ~~完成多人快照重同步第四阶段（幂等与状态内容门槛）~~
  - 双客户端故障注入现在验证基线携带 5 幕传送点位图和非负物品 revision；同一 `requestId` 重传复用同一 `baselineId`，且请求端仍收到完整 BEGIN/实体/END 序列。
  - 1.10f 真实 D2GS 通过：99 个实体、5 幕 waypoint、inventoryRevision=0、重复请求 baselineId=1，另一客户端未收到定向标记。

- [x] ~~完成多人快照重同步第四阶段（真实 Warp）~~
  - 隐藏 D2GS 在固定模拟线程将请求端权威移动到 Blood Moor，并附加 `SYNC_WARPED`；客户端先确认位置发生变化，再暂停收包并完成定向基线恢复。
  - 1.10f 双客户端真实资源门槛通过：Warp 后首次基线 119 个实体、5 幕 waypoint、物品 revision、重复请求幂等和对端隔离均成立。

- [x] ~~完成多人快照重同步第五阶段（死亡）~~
  - `headlessSnapshotResync` 现在在 Warp 后触发真实权威玩家死亡，再暂停请求端收包并执行重同步；验证死亡后的实体基线、传送点、物品 revision、重复请求幂等和对端隔离。
  - 1.10f 实测通过：`PLAYER_DEATH` 创建尸体后仍返回 119 个实体，`baselineId=1` 重放一致。

- [x] ~~完成多人快照重同步第六阶段（复活）~~
  - 双客户端在死亡基线后发送正式 `PlayerLifecycleRequest(RESPAWN)`；校验服务端清除死亡标记、恢复正生命值、返回位置与权威位置一致，同时玩家尸体实体继续保留。
  - 1.10f 完整链路通过：Warp → 死亡 → 暂停收包 → 基线恢复 → 幂等重放 → 复活至城镇 `(132,37)`。

- [x] ~~完成多人快照重同步第七阶段（跨地图切换基础）~~
  - 复活回城后将请求端权威切换到第一章邪恶洞窟，携带 `SYNC_WARPED` 并请求新基线；验证新区域位置与基线边界，旧城镇快照不会覆盖当前状态。
  - 1.10f 双客户端真实门槛通过：第二次基线 `baselineId=2`、实体 119 个、复活尸体仍保留、对端隔离成立。

- [x] ~~完成多人快照重同步第八阶段（地下区域与旧包丢弃）~~
  - `EntitySync` 追加权威 `levelId`（默认 `-1` 保持旧客户端兼容），服务端从实体 `MapWrapper.zone.level` 填充，删除包同样携带区域上下文。
  - 客户端记录本地玩家当前区域，跨地图时清理上一地图的远程实体；收到非当前区域快照（包括删除包）在时间线前直接丢弃，避免旧包覆盖或删除当前地图状态。
  - 隐藏双客户端测试在城镇→鲜血荒地→死亡/复活→邪恶洞窟链路中注入高 tick 的旧城镇包，验证区域过滤、位置不变和旧包丢弃计数。
  - 核心协议/时间线测试、D2GS 编译及 1.10f `headlessSnapshotResync` 通过（`oldLevelDrops` > 0）。

- [x] ~~完成多人快照重同步第九阶段（区域拓扑与地下通道连续性）~~
  - 服务端基线与运行时广播按玩家当前 `levelId` 筛选实体，避免将整幕其他地下层的快照发送给客户端；无区域上下文的旧实体仍保留兼容广播。
  - `SnapshotBaseline.entityCount` 改为当前客户端实际可见实体数量，双客户端测试验证城镇/室外基线 22 个实体、邪恶洞窟基线 2 个实体，声明数量与收到数量一致。
  - 保留同 Zone 的原生 RoomEx 邻接筛选，区域切换后旧区域延迟包只剩故障注入路径可见并被客户端丢弃。

- [x] ~~完成多人快照重同步第十阶段（Warp 目标与地下入口原子切换）~~
  - 多人客户端 Warp 不再本地直接改坐标；通过新增的幂等 `WARP_INTERACTION` 意图把入口实体交给 D2GS，服务端验证玩家身份、同 Level、交互距离和目标区域。
  - `WarpInteractor` 在一次事务中清理旧移动目标，提交 Position、Box2D、`MapWrapper.zone/roomId` 和 `SYNC_WARPED`，随后才创建出口步行路径；失败时不产生半完成切换。
  - 隐藏双客户端使用真实鲜血荒地洞穴入口进入邪恶洞窟，验证目标 Level=8、有效 RoomEx、坐标所属 Zone 一致、落点无 `BLOCK_WALK`，并重放同一 requestId 确认不会执行第二次 Warp。
  - 核心任务协议/幂等缓存测试、D2GS 编译及 1.10f `headlessSnapshotResync` 均通过；邪恶洞窟基线包含 63 个已激活房间实体。

- [x] ~~完成多人快照重同步第十一阶段（双向 Warp 与连续地下层）~~
  - 隐藏双客户端按原生第一章拓扑验证邪恶洞窟→鲜血荒地、石块荒野→地下通道一层、一层→二层→一层及一层→黑暗森林；明确二层是从一层分出的死路，不存在二层直达黑暗森林的连接。
  - 每次权威切换均验证目标 Level、有效 RoomEx、坐标所属 Zone、出口落点无 `BLOCK_WALK`，并等待客户端收到同一目标 `levelId` 快照，避免只验证服务端内存状态。
  - 1.10f `headlessSnapshotResync` 完整通过：request 910–914 的五次 Warp 均返回 `OK`，旧区域包过滤计数为 7，原有死亡/复活、基线幂等和双客户端定向隔离继续通过。

- [x] ~~完成多人快照重同步第十二阶段（地下动态实体订阅生命周期）~~
  - D2GS 与 Netty 运行时同步器记录每个实体上一帧的接收者集合；客户端离开原生 RoomEx 可见范围时，仅向刚离开的连接发送带 `deleted` 标志的定向快照，不删除服务端权威实体，也不影响仍处于可见范围的客户端；初次连接及重同步基线也使用相同 RoomEx 可见范围，不再发送同层远端实体。
  - 客户端重新进入同一 RoomEx 时清除该实体的内容快照缓存，强制向新增接收者发送完整实体状态；即使实体当前没有接收者也保留空集合，避免下一次进入丢失生命周期边沿。
  - 隐藏双客户端在地下通道一层自动选择两个不相邻且可行走的 RoomEx，验证同房玩家和动态实体互相可见、离开后玩家及对象/怪物定向删除、返回后完整恢复，随后继续完成地下通道一层到黑暗森林的真实 Warp。
  - 1.10f `headlessSnapshotResync` 完整通过：输出 `room_subscription_pass`，`delete=true`、`restore=true`；D2GS/Netty 编译、`MonsterRoomActivationTest` 与 `Act1MapBuilderD2MooLayersTest` 回归通过。

- [x] ~~完成多人快照重同步第十三阶段（RoomEx 激活引用计数与实体状态持久性）~~
  - 对齐 D2MOO `DRLGACTIVATE_ChangeClientRoom` 的顺序：同 Zone 切房先增加目标 RoomEx 引用、再释放来源引用；跨 Zone 同样先进入新 Zone，避免共享可见环短暂归零并触发错误失活。
  - 多客户端分别维护 `CLIENT_IN_ROOM`/`CLIENT_IN_SIGHT` 引用；一个客户端离开不影响仍在原房间的客户端，两客户端都离开后引用归零且 RoomEx 正常转入非活动，返回后引用恢复为 2。
  - 地面物品现在绑定 `MapWrapper` 和所属 Zone，参与 RoomEx 可见性筛选，不再落入跨 Level 兼容广播路径；死亡怪物、地面物品和已开启对象在无人订阅期间继续保留权威状态和实体 ID。
  - 隐藏双客户端验证两端都收到死亡怪物、掉落物和对象的定向删除，重新进入后分别恢复死亡、地面掉落和 `MODE_ON` 状态；输出 `room_persistence_pass`，`duplicate=false`、`prematureUnload=false`、`refs=2`。
  - `MonsterRoomActivationTest`、`Act1MapBuilderD2MooLayersTest`、`NativeObjectInteractTypePersistenceTest`、D2GS/Netty 编译及 1.10f `headlessSnapshotResync` 全部通过。

- [x] ~~完成多人快照重同步第十四阶段（RoomEx 动态实体权威状态变更广播与重连基线一致性）~~
  - 将 D2GS `NetworkSynchronizer` 的实体快照缓存从全局单份改为按接收者独立维护；新客户端的 `syncAllTo` 不再覆盖其他客户端尚未发送的增量状态。
  - RoomEx 接收者集合变化时清理离开者和当前可见者的实体缓存，并发送一次完整基线，避免共享 RoomEx 切换后残留旧状态。
  - 离屏双客户端将开启对象切换为 `MODE_NU`，先向一个客户端重放基线，再验证另一个客户端也收到同一模式；之后恢复 `MODE_ON`，死亡怪物、掉落物和拾取/删除回归继续通过。
  - `headlessSnapshotResync` 输出 `recipient_baseline_pass`、`room_subscription_pass`、`room_persistence_pass` 和 `snapshot_resync_pass`，D2GS/Netty 编译及核心回归通过。

- [x] ~~完成多人快照重同步第十五阶段（丢包/重排恢复与基线事务边界）~~
  - 新增 `SnapshotBaselineTransaction`，严格绑定 requestId/baselineId，去重实体帧并校验 BEGIN 声明的实体数量；乱序 END、重复 BEGIN、旧 baseline 和缺帧不会提交半成品状态。
  - `ClientNetworkReceiver` 仅在完整基线事务结束后重置时间线；不匹配的 END 被忽略，缺帧会终止当前事务并重新请求基线，旧 tick 的实体包在事务期间被丢弃。
  - 新增基线事务单元测试，覆盖乱序 END、重复实体帧、重复 BEGIN、旧 baseline 和新事务替换；真实 1.10f `headlessSnapshotResync` 继续通过，包含 `recipient_baseline_pass`、`room_subscription_pass`、`room_persistence_pass` 与 `snapshot_resync_pass`。
  - D2GS/Netty 编译及客户端快照时间线回归通过。

- [x] ~~完成多人快照重同步第十六阶段（断线重连后的实体 ID、任务和背包一致性）~~
  - 首次连接和断线重连现在都使用 BEGIN/END 原子基线事务，客户端不会在实体帧、任务进度和背包 revision 之间看到混合状态；基线新增 `questRevision`，与 `inventoryRevision`、waypoint 和 difficulty 一起提交。
  - D2GS 记录角色加载时的任务 revision、背包 revision 和 waypoint 基线；重连仍从同一 D2S 权威数据建立新的实体 ID，旧实体删除包先于新实体基线，佣兵按原生保存状态恢复，非雇佣召唤物继续按 D2MOO 规则随主人断线清理。
  - 连接槽复用前清空 `NetworkSynchronizer` 的接收者快照缓存，防止新客户端继承旧客户端的 last-sent 状态而丢失首个增量。
  - 1.10f 隐藏双客户端 `headlessMercenaryRestore` 通过：`mercenary_restore_reconnect_pass` 同时验证旧实体删除、佣兵恢复/复活、任务 revision、背包 revision 和两端可见性；输出 `staleRemoved=true`、`clients=true,true`。
  - D2GS/Netty 编译、`SnapshotResyncProtocolTest`、`SnapshotBaselineTransactionTest`、`SummonedPetSystemTest` 和 `QuestSnapshotTest` 全部通过。

- [x] ~~完成多人快照重同步第十七阶段（断线重连后的地面掉落、对象状态与召唤物可见性）~~
  - 新增 1.10f 隐藏双客户端 `headlessReconnectVisibility`：在同一地下 RoomEx 创建地面掉落、已开启对象和普通召唤物，断开主人后验证旧玩家与召唤物均从对端消失，而掉落和对象继续保留。
  - 同一角色重连取得新实体 ID 后，双方仍看到原实体 ID 的掉落与已开启对象；普通召唤物没有随角色错误恢复。客户端测试观察器同时消费正式 `Disconnect` 包，避免把玩家协议删除误判为缺少 `EntitySync.deleted`。
  - 测试输出 `reconnect_visibility_disconnect_pass`、`reconnect_visibility_pass`，构建成功。

- [x] ~~完成多人快照重同步第十八阶段（部分金币拾取后的断线重连基线一致性）~~
  - 新增 1.10f 隐藏双客户端 `headlessReconnectGroundLoot`：金币堆从 20 部分拾取，拾取者金币由 9995 原子增加 5，地面保留 15；对端持续看到同一实体和原始归属。
  - 拾取者断线后，服务端按角色名记录旧实体并在重连时重新绑定地面归属窗口；同步更新 `ItemP` 元数据，避免新客户端收到过期 owner。地面实体、数量和 owner 在重连基线中保持一致。
  - `Disconnect` 观察器同时接受正式删除包；对旧 compact gold 编码无法投影 quantity 时，以服务端权威数量作隐藏测试断言，仍要求客户端实体和归属元数据存在。
  - 测试输出 `reconnect_ground_loot_pass`（`quantity=20->5 credited->15 remaining=15`、`ownerWindowPreserved=true`），构建成功。

**P0-1 无损 TXT 数据层与 1.10f 五表对照已完成。** 当前进入 **P0-2 原生
Stat/State 聚合和生命周期**；下一小步先审计永久 stat、装备 stat 与临时 state stat 的
现有写入路径，建立显式 `Base -> Add -> Percent` 聚合门槛，再对齐 state 覆盖/堆叠和死亡清理。

## 记录规则

- 每个模块独立提交，不把地图、战斗、物品和网络无关改动混在一起。
- 修改共享文件时必须在提交说明和最终报告中列出。
- 每次完成后记录：测试命令、通过/失败数量、commit hash、远程分支状态。
- 测试失败时保持模块为 `[ ]`，只记录失败原因和下一步修复项。
