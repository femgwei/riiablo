# 逐怪 AI 隔离测试场

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
- 复活夹具参考 D2MOO `AITHINK_Fn013_FallenShaman` / `Fn022_GreaterMummy` 及其 callback。
  没有为全部特殊 AI 补齐任务、召唤主从、受伤盟友、环境/地形触发等专属场景。

状态优先记录异常：RUNTIME_ERROR、ERROR_LOG、AI_FALLBACK、WARNING、
SPECIAL_NOT_TRIGGERED；否则分别记 ACTION_OBSERVED、MOVEMENT_ONLY、NO_ACTION，
无主人正常死亡记 TERMINAL_OBSERVED。专属场景没有实现时 `special=NOT_IMPLEMENTED`，
不是 PASS。辅助怪施法/伤害不计入主体行为；警告可来自主体或辅助怪，均保留。
伤害事件数/合计是事件证据，不是逐包伤害公式的独立验算。

## 2026-10-15 本机记录与修复

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
仍存在 FingerMage/Megademon/BloodLord 等缺失 AI、触手 srvdofunc(94) 未支持、
COF 回退等问题。表内动作观察数**不能换算为“AI移植完成率”**。

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
