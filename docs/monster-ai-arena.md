# 逐怪 AI 隔离测试场

## 最新修复批次：2026-10-15 submerge

本批次针对 Tentacle / TentacleHead 的潜入、停帧、浮出链路，不以新增空的技能 case 消警。

- 对照 D2MOO `SkillMonst.cpp::SKILLS_SrvSt51_Submerge` / `SrvDo094_Submerge` /
  `SrvSt52_Emerge` 和 `Units.cpp::UNITS_GetShiftedFrameMetrics`：94检查的是展示源帧，
  不是逻辑序列游标；源帧到0时停止SQ。猎物死亡或被移除不能阻断这个自身动作回调。
- 本机1.10f `MonSeq.txt` 的 `seq_tentaclesubmerge` 为 S1源帧12→0，
  `seq_tentheadsubmerge` 为20→0；两者分别有8/7个事件1。新增专用反向序列时钟，
  使用真实MonSeq动作点，不再将长序列名当XX查COF，也不使用S1浮出COF的正放标记冒充潜入。
  最后保持源帧0，不发动画结束/不回NU；停帧后AI仍按原表时间思考，浮出重建S1清掉旧保持状态。
  客户端本地序列表现跟随权威反向源帧，避免将潜入显示成浮出；仍需另一台电脑做TCP/GL验证。
- 按 `AiThink.cpp::AITHINK_Fn056_Tentacle` / `Fn057_TentacleHead` 修正主人SQ判定：
  原生序列模式为14，不是SC；显示S1的主人潜入仍是SQ。主人带Corpse不能直接让触手必死，
  只有主人在DD时才做原生40%判定，无主人仍真实走死亡链。
- 为3行Tentacle和3行TentacleHead增加 `submerge-cycle` 场景，中心距3、使用真实表参数。
  顺序断言主体保持源帧0且0x0E全清、浮出时恢复0x0E、真实浮出动画结束、随后主体A1动作。
  日志的 `SubmergeCycle phase=1/2/3/4` 对应上述四阶段；辅助怪事件不计入。
  仅施法、只有保持、缺少浮出结束或标志错误均不能满足专属断言。

本次不是通用MonSeq移植。FrogDemon仍未接入其潜入序列/完整决策链；没有因同用94就
宣称蛙魔正确，也没有更改缺资源/其他技能告警、关闭碰撞、强制施法或改原表概率。
未移植调用会报 `Unported Submerge sequence`，3个蛙魔far场的告警数量仍与旧表一致，
不因支持94回调而静默跳过未接入的序列；新增回归验证该边界。

最终本机Normal、seed=1、500tick：**557行、1142场全部执行**（原1136场+6个专属场）。

- ACTION_OBSERVED 477（原459），WARNING 106（原118）。
- AI_FALLBACK 376、NO_ACTION 172、MOVEMENT_ONLY 7、TERMINAL_OBSERVED 4，均不变。
- RUNTIME_ERROR / ERROR_LOG / NOT_RUN / SPECIAL_NOT_TRIGGERED 均为0。
- 6场 `SUBMERGE_CYCLE_OBSERVED`，主体及辅助单位均无告警；原18场真实复活仍成功。
  例如tentacle1在tick17保持、417开始浮出、434浮出结束、451恢复A1动作。
- 与上一份 `20261015-native-fixes-seed1/results.jsonl` 按monster/scenario完整字段比较：
  仅12个触手/触手头的near/far既有场次变化，其余1124场完全一致，无既有场次丢失。
  六行单独运行的21场与完整运行对应记录也全部逐字段相同，辅助怪行为不算主体覆盖。
- 定向回归 **155项通过**（core128、d2gs27），失败/错误/跳过均0，包含本批次原表/反向
  时钟/停帧/冻结/猎物消失/主人判定/客户端帧控制与专属断言反例，以及上一批相关回归。

新表：[`20261015-submerge-seed1/report.html`](ai-arena/20261015-submerge-seed1/report.html)，
同目录 `logs.zip` 含1142个逐场日志，解压后HTML日志链接可用；复现命令和证据见
[`verification.md`](ai-arena/20261015-submerge-seed1/verification.md)。旧快照保持不变。

仍有47个AI名称/188行回退，以及Inferno、其他技能/MonSeq、骷髅复起COF等缺口。
以上是全目录单难度单seed诊断，不是全AI正确性PASS，未执行全项目测试、三难度多seed矩阵
或真实TCP/GL地图验证。下一批可继续处理蛙魔的独立序列/决策链或MonInferno持续施法链。

## 前一修复批次：2026-10-15 native-fixes

本轮从上一份1136场结果定位了三项相互叠加的复活缺陷，并补齐一个缺失AI。
下面是本机实际修复/复测记录，不代表全部错误已清零；旧快照保持不变。

- GreaterMummy 找尸体仍是 TODO，复活/射击都把真正技能替换成了普通 attack；
  重写为 D2MOO `AiThink.cpp::AITHINK_Fn022_GreaterMummy` 的决策优先级，使用
  Skill1/2/3、原生概率流，保留施法/动画忙碌保护，去掉近距离无概率强制A2和随机1–2秒停顿。
  盟友扫描区分普通木乃伊的 lUndead、Radament 的 l/hUndead、Normal暗金例外及额外10范围，
  检查阵营、TARGETABLE、udead/hide、终态尸体和相邻房间；按回调保留最后合格目标而非最近目标。
- 死亡系统此前在DD清掉全部 MONSTER_TARGET，导致原版复活回调永远拒绝普通尸体。
  根据 `MonsterMode.cpp::sub_6FC63E80` 的 `flags &= 0xFFFF7FF3`，在死亡边界清可攻击/
  有效攻击目标等位，保留原有 TARGETABLE，仅 hide mask 清该位；不会主动把不可选目标变可选。
  反复DD不恢复XP/掉落，尸体仍不可被普通攻击。旧断言随原版证据纠正，并新增 hide 边界测试。
- 本机1.10f `MonSeq.txt::seq_mummyres` 为 SC 的0..15点，13点事件2（MIS）。
  部分变体SC COF的标记不同，单纯改SC模式仍不能正确触发；新增专用序列游标，
  由真实 AnimStepper 在13点触发一次技能、16点结束，不直接注入 SkillDo/复活结果。
  不修改共享COF数据；测试核对原表全部16点及全部GreaterMummy行，并覆盖半速/常速/双速跨帧。
- BloodLord 原本缺类、退回 GenericMonster。按 `AiThink.cpp::AITHINK_Fn125_BloodLord`
  补齐近战概率、A2 Skill1狂乱/A1普通攻击、非近战接近概率和表内停顿；使用已有srvDo109。
  不再以通用普通攻击冒充它的狂乱决策。

最终Normal、seed=1、500tick，全目录仍为557行/1136场：

- ACTION_OBSERVED 459（原443），MOVEMENT_ONLY 7（不变）。
- AI_FALLBACK 376（原390），对应188行、47个AI名称（原195行、48个名称）。
- WARNING 118（原117），NO_ACTION 172（原165）。
- SPECIAL_NOT_TRIGGERED 0（原10），TERMINAL_OBSERVED 4（不变）。
- RUNTIME_ERROR / ERROR_LOG / NOT_RUN 均为0；10场木乃伊复活全部
  `resurrected && resurrectedBy==subject`，8场萨满复活仍成功。
- BloodLord 的14场不再fallback，其中7场近距有动作、7场远距仍无动作，后者继续登记
  NO_ACTION，不以“已有专用类”判定通过。WARNING增加的一场是unraveler1复活后骷髅
  `SKXX1HS -> SKA11HS` 复起动画回退，未隐藏或豁免。

新表及可解压日志：[`20261015-native-fixes-seed1/report.html`](ai-arena/20261015-native-fixes-seed1/report.html)。
完整JSONL、inventory、summary、COMPLETED、logs.zip与表同目录；解压日志包后HTML日志链接可用。
与旧表逐字段比较，仅BloodLord14场和GreaterMummy30场改变，其余1092场完全相同。

定向回归141项全部通过，0失败/错误/跳过，覆盖 GreaterMummy/BloodLord决策、序列时钟、尸体标志、Bestow、原生Act1、
死亡/生命周期、触手、BaalCrab/分身、Frenzy、SequenceHandler及测试场/巡图日志。
没有把本次定向测试称为项目全部测试，也没有完成三难度、多seed或另一台电脑的TCP验证。

### 剩余错误如何处理

376场fallback是47种表内AI缺少专用类，不是一个反射加载异常；需逐类移植，不能删除
fallback日志或改成Idle。较多的有Minion、VileDog、MinionSpawner、SuicideMinion、
SuccubusWitch、FetishBlowgun、Imp等，FingerMage/Megademon也仍缺失。

技能回调告警是独立的移植缺口：本轮日志中srvstfunc(53)796条、srvdofunc(26)462条、
(136)396条、(101)170条、(108)/(148)各136条、(94)27条。这些是**日志条数而非场次数**，
也可能出现在fallback场内，不能只看118个WARNING场就认定只有118场有告警。
Inferno持续施法、Submerge动画保持/浮出和其他技能须连同生命周期修复，不能加空case消警。

XX/缺COF告警包含尚未接入的MonSeq和具体资源/模式问题；本次只实现seq_mummyres，
没有声称实现通用MonSeq，包括骷髅复起序列仍需跟进。NO_ACTION只说明20秒固定场景内
无主体动作/位移，需按aiDist、环境/主人/受伤/任务条件补专属场景后再区分实现缺陷和条件不足；
Idle/控制器不能被强制攻击来凑覆盖率。木乃伊仍复用共享路径/绕行近似，不声称与原版三步路径
截断、房间单位遍历顺序、Radament任务激活回调逐项一致。

## 与 skill-viewer 的复用边界

复用 `Map.createSyntheticArena`：不进入 GameScreen、不加载 DS1/DT1，建立可容纳真实
单位、寻路、目标选择和飞弹的独立 Zone。skill-viewer 已有角色重建、怪物/尸体加载的
流程，但其下拉框目前只有 Fallen，`Enable AI` 未接入 AIStepper，不能直接当 AI 验收器。
本次没有更改原来的技能查看器行为；新增其**无窗口批量测试配套入口**，不是 GUI 自动巡播。

`MonsterAiArena` 使用生产 `ServerEntityFactory`、`AIStepper`、动画关键帧、Actioneer、
ServerSkillSystem、飞弹碰撞、状态、尸体和生命周期系统；不使用 RecordingFactory 或
WalkableMap 替身。每个场次销毁旧 World/Map，重新创建角色、怪物及辅助单位。
MPQ/数据表/动画索引只加载一次，不在测试中改 MonStats 原表。每场重置 gameSeed 与
MathUtils 随机源；不宣称所有旧系统共享了完整原版单位随机流。

## 统计口径

目录取本机 1.10f MPQ：MonStats 中 `enabled && Align==0 && !npc` 的 **557 行**，
对应 **144 个 BaseId 家族、110 个表内 AI 名称**。包括不可杀控制器/陷阱、召唤物以及
未定位 Act 的数据，不等于 557 种主线地图怪物；同模型不同变体保留为不同被测行。

Act 来源合并 Levels 1..132 的 mon/nmon/umon/cmon、MonPreset 直接怪物/超级暗金引用，
以及生产工厂已经支持的单怪 placement directive；再沿 spawn/minion1/minion2 传播 Act。
来源逐行保留，未解析的 placement 也保留在 inventory.txt，绝不按猜测分配 Act。

- A1：66 个数据变体，30 个家族，27 个 AI 名称。
- A2：77 个数据变体，41 个家族，36 个 AI 名称。
- A3：71 个数据变体，41 个家族，39 个 AI 名称。
- A4：36 个数据变体，26 个家族，24 个 AI 名称。
- A5：187 个数据变体，74 个家族，64 个 AI 名称。
- 去重后有 Act 引用的为 423 行；134 行暂未归属 Act，仍全部参加测试。

各 Act 有交集，不应相加作为总数。MonPreset 候选并未逐个检查实际 DS1 是否使用，
且没有把超级暗金品质、任务阶段和难度组合当作新的 MonStats 种类。
预设缺少精确 Level 时使用显式 Act 本地空地夹具（2/41/76/104/110），不是声称找到了
其原版出生地图；未归属 Act 时用 Level 2。测试难度为当前运行指定的单一难度，
目录来源则合并所有难度引用。

## 运行

输出目录必须不存在；中断/旧日志不能被新运行覆盖。

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-new-run `
  --console=plain
```

默认所有目录行、seed=1、Normal、每场500个固定25Hz权威模拟 tick，即20秒游戏时间。
循环加速执行，不等20秒墙钟时间。通过 AuthoritativeSimulation 维护战斗时钟，非渲染帧
驱动。可选参数：

- `-ParenaSeed=2`、`-ParenaDifficulty=1`（0/1/2 普通/噩梦/地狱）。
- `-ParenaTicks=1500`：每场最多15000 tick；延长观察不等于增加正确性断言。
- `'-ParenaMonsters=fallenshaman1,unraveler1,tentacle1,baalclone'`：只执行选定行，
  其余仍在报告中显示 NOT_RUN；名字错误直接报错，避免意外空跑。
- `-ParenaCatalogOnly`：只导出目录、所有场次均 NOT_RUN。

输出 `report.html`（可搜索表格）、`results.jsonl`（逐场实时落盘）、`inventory.txt`、
`summary.txt`、逐场 `.log` 和 `COMPLETED`。
开始先写全目录 NOT_RUN 报告；若进程中断，初始表不代表最新进展，应结合 jsonl
中已经落盘的记录；只有 COMPLETED 代表目录遍历结束。尚不支持原目录断点续跑。
单场运行时异常/断言/LinkageError 保留堆栈后继续下一场；致命 VM 错误不吞掉。
命令默认是**诊断导出**，存在 fallback/告警等也会结束并打印 DIAGNOSTIC_COMPLETE，
Gradle BUILD SUCCESSFUL 和 COMPLETED **都不等于 AI 验收通过**。

## 场景和结果含义

基线角色为30级 Amazon、HP=1000000、不主动攻击，不用于数值平衡或伤害正确性验收。
玩家放在(128,128)，保证单位尺寸在场内，避开旧寻路把(0,0)当终点哨兵的问题。
不关闭动态单位碰撞，不跳过真实技能/动画关键帧，不强制注入 SkillDo 来伪造 AI 行为。

- 每行 `near`（中心距3）和 `far`（中心距18）；是固定几何距离，不保证每种体型下
  near 恰好在原版近战范围。表中记录真实位移、主体 SkillDo、主体伤害事件和告警。
- minion1/minion2 各加载一只并赋予 minionOwner；这是最小辅助上下文，不是完整原版
  PartyMin/PartyMax 数量/队形验证。spawn 字段关联子怪由真实 AI 自行生成，不预先伪造召唤成功。
- FallenShaman、GreaterMummy 增加 `corpse` 场：供给 Fallen/低阶骷髅终态尸体，沿真实
  死亡/尸体组件建立夹具；复活必须由主体 AI 发起并到达动画关键帧，断言
  `resurrected && resurrectedBy==subject`，施法记录本身不算复活成功。
- Tentacle 提供对应 TentacleHead；BaalCrabClone 提供 BaalCrab 并建立双向主人链接。
  依据 D2MOO `AiThink.cpp::AITHINK_Fn056_Tentacle`、
  `AiBaal.cpp::AITHINK_Fn140_BaalCrabClone`；另加 `orphan` 验证无主人自行死亡不崩溃。
- Tentacle / TentacleHead 另有 `submerge-cycle`（中心距3），真实完成潜入保持、浮出结束、
  恢复A1动作才记录 `SUBMERGE_CYCLE_OBSERVED`；仅其他怪施法、只开始浮出不能通过。
- 复活夹具参考 D2MOO `AITHINK_Fn013_FallenShaman` / `Fn022_GreaterMummy` 及其 callback。
  没有为全部特殊 AI 补齐任务、召唤主从、受伤盟友、环境/地形触发等专属场景。

状态优先记录异常：RUNTIME_ERROR、ERROR_LOG、AI_FALLBACK、WARNING、
SPECIAL_NOT_TRIGGERED；否则分别记 ACTION_OBSERVED、MOVEMENT_ONLY、NO_ACTION，
无主人正常死亡记 TERMINAL_OBSERVED。专属场景没有实现时 `special=NOT_IMPLEMENTED`，
不是 PASS。辅助怪施法/伤害不计入主体行为；警告可来自主体或辅助怪，均保留。
伤害事件数/合计是事件证据，不是逐包伤害公式的独立验算。

## 2026-10-15 首轮本机记录与修复（历史快照）

首轮完整目录 557 行、1132 场发现8个运行时异常：3种触手和巴尔分身在无主人时自行
死亡，DeathEvent.killer=-1，Actioneer.onDeath 对负数调用 ComponentMapper.has 导致
ArrayIndexOutOfBoundsException。修复只在 killer>=0 时清理击杀者目标；死亡单位自身
的技能/路径/状态清理保持原样。新增无击杀者回归与正常击杀目标清理回归。
同时修正测试场辅助主人关系，单独保留4场 orphan，不通过补主人把真实边界缺陷隐藏掉。

修复后全目录 **557 行、1136 场均已执行**，Normal、seed=1、500 tick/场：

- ACTION_OBSERVED：443；MOVEMENT_ONLY：7。
- AI_FALLBACK：390；WARNING：117；NO_ACTION：165。
- SPECIAL_NOT_TRIGGERED：10；TERMINAL_OBSERVED：4。
- RUNTIME_ERROR / ERROR_LOG / NOT_RUN：均为0。
- 8场萨满复活已观察到；10场 GreaterMummy（含 Radament）复活未触发，代码中找尸体/
  复活分支仍有 TODO。尚未在本次改写整个 GreaterMummy 决策函数。

390是 **场次数**，对应195个数据行、48个表内AI名称，不是390种怪物；同一怪物
近/远两场会重复计数。
该快照时仍存在 FingerMage/Megademon/BloodLord 等缺失 AI、触手 srvdofunc(94) 未支持、
COF 回退等问题。表内动作观察数**不能换算为“AI移植完成率”**。
BloodLord与GreaterMummy的后续修复及当前计数见本页开头，不覆盖首轮结果。

最终完整场次表和可解压的逐场日志随仓库提交到
`docs/ai-arena/20261015-normal-seed1/`。`report.html` 可直接打开；日志链接需要先把
该目录 `logs.zip` 解压到同一个目录。JSONL 保留完整默认字段，便于后续差分和排序。
构建目录保留初始失败日志和各次复跑，不覆盖旧证据。

定向回归79项全部通过、无失败/错误/跳过：服务端目录/归因/日志26项；核心死亡事件、
生命周期、触手、BaalCrab及其分身53项。不是项目全部测试套件，也不把它们计为557种
怪物各自的完整行为验收。
另外仅选 fallenshaman1、tentacle1、baalclone 重跑9场，逐字段与完整运行对应记录
一致，验证改变执行子集后主体结果没有串场；其余1127场正确登记为 NOT_RUN。

这解决了“按数据行全目录跑基线，不再仅抽几张图遇到什么测什么”的问题，
**没有解决全AI分支正确性验收**；仍需按失败表逐类补齐原生 AI、专属场景和独立断言。
空地测试也不能代替真实 TCP/地图巡游的地形、门、房间唤醒、任务/Boss阶段和网络验证，
更未执行三难度/多seed完整矩阵。
