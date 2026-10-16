# 怪物 AI 移植依赖与进度

范围：1.10f 目录中原先缺少专用实现的 37 类 AI（136 个 MonStats 行）。
小依赖随对应 AI 一起实现；只有确认的大依赖在此登记。新增类或消除 fallback 本身不代表完成。
每完成一类，核对原版分支并向用户汇报剩余量。2026-10-17 起按用户要求不跑测试；之后的完成项明确注明未跑测试，不沿用先前项的测试结论。

## 本轮实现

累计完成实现 8/37，剩余 29 类；本批 7TIllusion、Catapult 已统一验证，Trap-LeftArrow 按用户要求纳入本批回归。

- Trap-Melee：已完成 Fn087 的攻击概率、失败等待、非近战及无目标固定 40 帧等待；补齐表名到 Java 类的显式映射。隔离源码测试通过，证据见 `ai-arena/20261016-trap-melee/`。本轮完成 1 类，剩余 36 类。测试覆盖 AI 决策与共享动作生命周期，未进行图形客户端场景验收。
- Trap-Missile：已完成 Fn077 的距离/次数限制、交替等待、无目标/超距/耗尽死亡及禁止掉落；补齐表名映射。6 项专用测试及相关回归共 37 项通过，证据见 `ai-arena/20261016-trap-missile/`。复用现有飞弹攻击和单位标志，无新增大依赖；未进行自然地图飞弹命中验收。完成后剩余 35 类。
- Trap-Nova：已完成 Fn080/092 中的新星陷阱决策，含距离/次数限制、交替等待、缺技能不计数、施放失败仍计数、死亡及禁止掉落；补齐表名映射。真实表 Skill1 为 Trap Nova（SrvDo022，S1），复用现有新星处理。7 项专用测试及相关回归共 65 项通过，证据见 `ai-arena/20261016-trap-nova/`。无新增大依赖，未进行自然地图新星命中验收。完成后剩余 34 类；Trap-Poison 未计入本项。

- Trap-Poison：已接入原版 Fn080/092 共用决策，以自身 PrimePoisonNova（SrvDo099，A1）施放。随 AI 补齐 SrvDo099 两组方向/速度、Calc1 循环数和 Calc2 步长的小依赖。5 项毒云 AI 测试、1 项三难度技能生产路径测试及相关回归共 71 项通过，证据见 `ai-arena/20261016-trap-poison/`。无新增大依赖，未进行自然地图毒云命中验收。完成后剩余 33 类。

- Trap-RightArrow：已完成 Fn078 的距离上下界、整数 X 坐标差、严格冷却截止帧、30/40 帧等待及普通箭/Skill1 分支。随 AI 补齐地图关卡级共享攻击类型与释放清理；小于 40 的关卡不消耗随机数，其余首次有效攻击 uint32 % 3。实表 PoisonBallTrap（SrvDo0）复用现有通用飞弹路径。7 项专用测试及相关回归共 56 项通过，证据见 `ai-arena/20261016-trap-right-arrow/`。无新增大依赖，尚未进行自然地图命中验收。完成后剩余 32 类。

- Trap-LeftArrow：完成 Fn079；与 Fn078 共用距离上下界、严格冷却、区域攻击类型及普通攻击/Skill1 分支，仅将对齐条件改为整数 Y 坐标差不大于 2。新增坐标差扩展点，右箭仍使用 X；补齐表名映射。复用已实现的区域状态和本行技能读取，无新增依赖。按用户要求未跑测试、未编译，仅核对原版与代码差异，记录见 `ai-arena/20261017-trap-left-arrow/implementation.md`。完成后剩余 31 类。

## 已确认的大依赖

### D01：原版动态路径动作桥

原版：`D2Game/src/AI/AiTactics.cpp` 的 `sub_6FCD0E80`、`AITACTICS_MoveToTarget`；`D2Game/src/MONSTER/MonsterMode.cpp` 的 `sub_6FC65680`；`D2Common/src/Path/Path.cpp` 的路径函数表。

现有 `NativeDecisionAI.circle` 是目标周围旋转 90 度后交给 A* 的近似；`AI.moveTo` 仅传坐标、速度和目标。原版类型 5/6 调用 PATH_Toward，并带方向偏移、距离预算、动态路径标志和动作结束条件；类型 13 也调用 PATH_Toward。`NativeTowardPathProbe` 只是只读可达性投影，不安装原版路径。不能将一次 A* 成功视为这套生产能力已完成。

直接受影响：AbyssKnight Fn073、FingerMage Fn070、ThornHulk Fn027、SuccubusWitch Fn119、OblivionKnight Fn074、HighPriest Fn085、FetishBlowgun Fn096、VileMother Fn068（绕行分支）；Mosquito Fn024（类型 13 接近、逃跑后游走）；ReanimatedHorde Fn114、SiegeBeast Fn115（WalkInRadiusToTarget）；Regurgitator Fn071（带步数的尸体接近）。

行为影响：绕行方向/终点、被障碍截断后的重试、接近距离与动作完成时机不等价。下一步应先为 Pathfinder 建立可安装并执行的原版动态路径动作接口，再接各 AI。逃跑的符号坐标计算、WalkCloseToUnit 四次种子抽样属于小依赖，可随对应 AI 补齐，不单独列为大依赖。

### D02：AI 特殊状态与命令队列生产桥

原版：`AiUtil.cpp:sub_6FCF2E70` 判断 **3 或 19**；`AI/AiStates.h` 将 3 标为 unused、19 标为 invalid。它们不是 Terror(11) 或 Taunt(12)，禁止用恐惧/嘲讽替代。`AITHINK_Fn075_QuillMother` 在此分支调用 `AIGENERAL_AllocCommandsForMinions`；Fn096 处理命令 1/14 后释放当前命令。

现有 `NativeAiCommand` 只投影单个 ATTACK=1 命令，没有原版队列、14、类型/编号双参数与广播生命周期；现有恐惧/嘲讽桥不能证明原版 3/19 分支已接通。

受影响：QuillMother；FetishBlowgun（另依赖 D01）；FingerMage、BatDemon、Overseer、Nihlathak 的特殊状态分支。下一步核对原版状态生产者及命令分配/消费，建立实体身份有效性和所有者释放机制，再接各分支。普通攻击分支本身不是大依赖。

### D03：怪物生成的区域选择与出生动作上下文

原版：Fn082 InvisoSpawner 调用 `D2Common_11063` 选择区域 mummy 变体，再 `sub_6FC66260` 在房间内找位置并生成；Fn129 GenericSpawner 从 `pMonReg[level].pMonData` 的本局已选怪物列表中选择 GENERICSPAWN 类，使用 Nest；Fn121 MinionSpawner 先通过 AI 邻近列表统计 minion/suicideminion；Fn068 VileMother 选择对应子怪变体并检查方向出生位置。

现有静态 `NativeMonsterRegion` 表辅助函数不能替代本局区域实例；通用工厂 spawn 也不能替代上述选类、原版空位规则、技能出生动作上下文和 minion 关联。受影响：InvisoSpawner、GenericSpawner、MinionSpawner、VileMother，以及 Nihlathak 的区域 minion 召唤分支。下一步补运行时区域怪物列表及出生上下文，并把技能服务端回调接入实际工厂；不能仅在 AI 中调用 cast 后增加计数就算完成。

### D04：Imp 与载具附着生命周期

原版：Fn113 SiegeTower、Fn115 SiegeBeast、Fn122 Imp，以及 `D2GAME_AI_SpecialState16_6FCE1D30`。塔/兽为无主邪恶 Imp 写入候选载具；Imp 接近、施放附着技能并进入 mounting 特殊状态，宿主死亡/已有 owner 时要重新选择。

现有普通 owner/minion 字段没有这套相互选择、附着模式和解离生产路径。受影响：Imp、SiegeTower、SiegeBeast。下一步建立共享附着状态和技能回调，同时实现三类 AI，避免各自仅写一个 entityId；SiegeBeast 另依赖 D01。

### D05：剧情 AI 与已有任务系统的实体级衔接

- DarkWanderer Fn091：原版 `ACT3Q7_GetWandererCoordinates` 和 `ACT3Q7_CreateVileDogSpawnTimer`。现有 Act3QuestSystem 未提供游荡者坐标/延迟狗生成接口；缺失会使其无法按剧情坐标离场并生成狗。需接入 A3Q7 生命周期，不伪造随机游走后死亡。
- BaalThrone Fn134 / BaalToStairs Fn138：现有 Act5QuestSystem/Act5BaalWaveState **已经有五波与传送门逻辑**。缺的是 AI 施放 BaalMonsterSpawn/BaalCorpseExplode、同一实体重初始化为 BaalCrabStairs、寻找门户并移除原单位的原版衔接。下一步重用任务状态并明确唯一的五波推进者，防止重复刷波；不是重做整个 A5Q6。
- Ancient Fn133：需三个 AncientBarbSkillHandler，原版共同调用 ACT5Q5_IsNotActivatable、同关卡 boss 选敌；Talic 还以 mode-change path type 100 和 step=1 执行旋风，Madawc 按步数接近，Korlic 用 sequence leap。现有三雕像激活/召唤任务能力不等于这套战斗动作桥已实现。下一步暴露任务可激活状态并接通三个 handler 的动作和选敌。
- CatapultSpotter Fn126：首次 3% 检查依赖房间朝向、跨三段房间的 SUNITINACTIVE_IsUnitInsideRoom；弹着点使用 COLLISION_GetFreeCoordinatesEx。现有普通活跃实体扫描无法替代休眠房间单位登记。需接入休眠单位查询和原版弹着点空位搜索，再执行五种投石技能选择。

### D06：死亡动作与尸体技能回调

SuicideMinion Fn117 的 AI 只延迟切入 DEATH；爆炸属于死亡动作回调，不是给 AI 添加独立伤害结算。现有 `NativeDecisionAI.kill` 仅清动作、播放 DT/DD 和音频，不能据此证明自爆生产链已完成。需核对原版怪物死亡事件并接技能/飞弹回调，再实现严格大于截止帧的触发。不得把普通伤害/反伤重构塞入本项。

Regurgitator Fn071 还需要真实 DEAD 模式尸体的选择、吞食技能、后续 A2 吐尸动作上下文和尸体竞争失效处理；现有玩家尸体技能不能自动证明怪物吞食/吐尸链已接通。下一步核对技能服务端索引并复用已存在的尸体状态与飞弹能力，只补缺失桥；另依赖 D01。

### D07：受击 AI 回调与技能移动预检

BatDemon：Fn029 外还有 `D2GAME_AI_Unk029_6FCD76F0` 回调：S4 时恢复状态 1；逃跑状态 4 受击时切回状态 1 并触发 S3；其他状态重新调度原 AI。当前 AI 基类没有受击入口，AIStepper 只有普通 update/special-control 调度；仅实现 Fn029 会漏掉这条生产分支。下一步把对应原版受击 AI 回调接到怪物受击事件，再完成状态机。hpregen 增减和挂顶动作等待属于小依赖，不单独记为大依赖。

ClawViperEx：Fn142 使用 `MonsterSpawn.cpp:sub_6FC68630`，对 SrvDo067 检查目标房间非城镇与 `D2Common_11025(...,0xC01)`；现有 Actioneer 的 Monster Charge 起手只请求 Pathfinder 的普通路径，AI 技能启动也没有这个原版预检接口。与 D01 的技能移动路径衔接一起补齐，再实现远程冷却、A1/A2 分流；颜色状态清除、冷却计时和概率选择属于小依赖。不能以普通 A* 成功代替原版技能允许施放条件。

### 版本核实项（不冒充已确认的大依赖）

WillOWisp Fn025：当前参考源码的普通移动/攻击之外还包含四单位仪式、五单位祝福、共享队形槽位与 1800 帧冷却。项目没有对应生产状态；但在把整套协调机制列为 **1.10f** 的大依赖之前，必须先确认该参考分支适用于目标版本。此类仍待版本核实和移植，不计为完成，也不声称已经确认必须移植后续版本的彩蛋。

## 待完成（31）

7TIllusion、AbyssKnight、Ancient、BaalThrone、BaalToStairs、BatDemon、Catapult、CatapultSpotter、ClawViperEx、DarkWanderer、FetishBlowgun、FingerMage、GenericSpawner、HighPriest、Imp、InvisoSpawner、MinionSpawner、Mosquito、Nihlathak、OblivionKnight、Overseer、QuillMother、ReanimatedHorde、Regurgitator、SiegeBeast、SiegeTower、SuccubusWitch、SuicideMinion、ThornHulk、VileMother、WillOWisp。

Baboon、Scarab 已有专用类，不在本轮 37 类中；此处不将其视为完整原版验收。
