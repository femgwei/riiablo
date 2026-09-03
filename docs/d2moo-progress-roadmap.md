# riiablo / D2MOO 对齐进度与实施路线

更新时间：2026-09-04
基线：`F:/3rd_src/D2MOO`（Diablo II 1.10f）与仓库内 `D2MOO_JAVA`

## 说明

这里的百分比按“可运行行为 + 原生分支 + 数据表/RNG + 状态副作用 + 测试覆盖”估算，
不按文件数量或类数量计算。D2MOO 自身也包含 Stub，因此目标是对齐可观察的游戏行为，
不是复制 DLL 的内部实现。

当前分支：`master`  
当前基线提交：以本文件所在提交的 `HEAD` 为准（每次模块提交后更新报告）。

## 总体进度

- **全项目 D2MOO 行为对齐：约 64%**
- **全项目剩余工作：约 36%**
- **第一章最小可玩闭环：约 78%**
- **第一章剩余工作：约 22%**
- **D2MOO_JAVA DRLG 整体：约 70%**
- **D2MOO_JAVA 第一章实际使用链：约 85%**

第一章已经接近收尾；全项目剩余量主要来自完整战斗分支、物品属性、多人边界、
数据层统一，以及 Act 2–5 地图和任务。

## 模块完成度与剩余量

“项目权重”表示该模块占整个 D2MOO 对齐目标的估计比重；“剩余贡献”表示该模块
当前缺口折算到全项目的百分点，所有模块剩余贡献合计约 36%。

| 优先级 | 模块 | 项目权重 | 当前完成 | 模块剩余 | 剩余贡献 | 当前结论 |
|---|---|---:|---:|---:|---:|---|
| P0 | 数据表、固定点、RNG、Unit 所属关系 | 10% | 55% | 45% | 4.5% | 部分运行路径仍有默认值和 fallback |
| P0 | 第一章地图、Warp、碰撞 | 10% | 85% | 15% | 1.5% | 可玩链基本稳定，需完成细节和回归 |
| P0 | Act 2–5/完整 DRLG | 5% | 25% | 75% | 3.8% | 尚未按第一章标准逐幕审计 |
| P0 | 怪物生成、等级和区域人口 | 7% | 70% | 30% | 2.1% | 还需完整区域池、群组和难度分支 |
| P0 | 怪物 AI 与特殊行为 | 8% | 55% | 45% | 3.6% | 通用 fallback、召唤和特殊 AI 分支不全 |
| P1 | 战斗、伤害、状态、技能、导弹 | 12% | 55% | 45% | 5.4% | 命中结果、抗性、持续伤害和技能分支仍简化 |
| P1 | 经验、升级、属性点、技能点、佣兵经验 | 7% | 75% | 25% | 1.8% | 所有权链、存档恢复和少量事件待补 |
| P1 | 装备、背包、物品移动和派生属性 | 10% | 60% | 40% | 4.0% | 原生属性聚合、腰带/尸体/插槽仍不完整 |
| P1 | TreasureClassEx、品质和地面掉落 | 7% | 70% | 30% | 2.1% | 唯一/套装属性和完整构造仍有 fallback |
| P2 | 箱子、门、陷阱、神殿、水井等对象 | 5% | 80% | 20% | 1.0% | 第一章主要对象已接通，快照广播待补 |
| P2 | 第一章任务、奖励和过渡 | 5% | 75% | 25% | 1.3% | 多人资格、对话分支和持久化待补 |
| P2 | NPC 买卖、修理、赌博、雇佣 | 4% | 70% | 30% | 1.2% | 原生库存刷新、雇佣/复活和重连待补 |
| P2 | Party、敌对、玩家交易和多人同步 | 5% | 55% | 45% | 2.3% | 玩家交易、复杂可见性和重连待补 |
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
| 野蛮人 Barbarian | 50% | 50% | Frenzy/Whirlwind/Berserk 状态、双持和命中序列 |
| 德鲁伊 Druid | 40% | 60% | 变形、召唤物、持续区域技能和协同公式 |
| 死灵法师 Necromancer | 50% | 50% | 尸体技能、召唤物所有权、诅咒和复活数量限制 |
| 圣骑士 Paladin | 50% | 50% | 光环叠加、Blessed Hammer/FoH、元素伤害与抗性 |
| 法师 Sorceress | 55% | 45% | Teleport、冰冻/燃烧持续时间、掌握技能和导弹分裂 |

职业技能专项整体按 **约 63% 完成、约 37% 剩余** 计入战斗模块；刺客专项已完成，
其余职业仍按各自行所列缺口继续推进。

## 实施顺序

每完成一项，就在本文件将对应 `[ ]` 改为 `[x]` 并使用删除线标记，同时补充测试、
提交和远程推送信息。未通过验收的模块不得标记完成。

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

## 当前下一项

刺客专项已经完成：陷阱、暗影召唤与控制、三层聚气、四套元素释放、四项龙系完成技，
以及 Blade Shield / Venom 的服务端状态、伤害和多人表现快照均已接通。

下一项建议进入 **野蛮人职业技能专项**，优先顺序为 Frenzy 双持连续命中与速度状态、
Whirlwind 路径/周期攻击、Berserk 物理转魔法及防御归零，再处理战吼范围状态与多人表现。

## 记录规则

- 每个模块独立提交，不把地图、战斗、物品和网络无关改动混在一起。
- 修改共享文件时必须在提交说明和最终报告中列出。
- 每次完成后记录：测试命令、通过/失败数量、commit hash、远程分支状态。
- 测试失败时保持模块为 `[ ]`，只记录失败原因和下一步修复项。
