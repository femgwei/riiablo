# Amazon 技能伤害所有者审计

本文件完成 DMG-03B 的 Amazon 部分：技能 ID 6–35，共 30 项。结论以 Diablo II
1.10f 的 `Skills.txt` / `Missiles.txt` 和 D2MOO 调用路径为准；本阶段只确认伤害所有者、
结算职责和 riiablo 覆盖状态。后续 DMG-04 已开始按本清单逐项写入有独立证据的
逐等级 `expected_*` 黄金值。

机器可检查的逐项证据见 `skill-damage-amazon-ownership.tsv`。本轮显式使用
`G:\BaiduNetdiskDownload\Diablo II 1.10F` 取证；1.14 输出只用于识别版本污染，没有写入结论。

## 结论

- 30/30 项均已确认原版所有者和 D2MOO 路径。
- 27 项已有生产实现和聚焦测试证据。
- 3 项仍存在明确的 riiablo 差异：Guided Arrow、Dopplezon、Valkyrie。
- Inner Sight 原有的线性防御削减差异已在 DMG-04 中按 1.10f 数据与 D2MOO 调用链修复；
  没有用 dark-magic 或 1.14 数值覆盖用户已经验证的 Amazon 生产逻辑。
- Magic Arrow、Fire Arrow、Inner Sight、Critical Strike、Jab、Cold Arrow、Multiple Shot、
  Dodge 与 Power Strike 已完成等级 1–20 基础审计；180/600 个 Amazon
  技能—等级行获批。

## 关键原版语义

### 箭和标枪通常同时有武器所有者与技能所有者

`D2GAME_SKILLS_Handler_6FD12BA0` 为 `SrvDoFunc=0` 的技能创建 `SrvMissile`，
`MISSILE_CalculateDamageData` 再组合 `SrcDam` 武器包、技能物理/元素曲线和导弹专用
`SrvDmgFunc`。因此 Fire/Cold/Magic Arrow、Lightning Bolt 等不能只比较技能表元素值；
Exploding/Freezing/Immolation Arrow 还必须把父箭武器命中与子导弹范围包分开。

Multiple Shot 和 Strafe 的 `SrcDam=96` 表示每枚箭继承 96/128 武器包，不表示一次施法
把武器伤害乘以箭数后写成一个包。Poison/Plague Javelin 的根标枪和持续毒云也必须分别
记录；毒云最终黄金值要拆成 rate、duration 和 total。

### 近战多段技能按“每次记录”结算

Jab 在三个 SQ keyframe 各拥有一个武器记录；Fend 对每个已选目标各创建一个武器记录；
Power/Charged Strike 和 Impale 在起手阶段预先分配记录，动画关键帧只消费一次。
Charged Strike 的闪电弹、Lightning Strike 的链式导弹又是独立技能包，不能合并到首个
近战目标的伤害中。

### 被动技能拥有状态，消费者拥有最终记录

Critical Strike、Dodge、Avoid、Penetrate、Evade、Pierce 由原版被动刷新安装永久状态。
它们分别在暴击、闪避、命中率或导弹穿透消费者中生效。Inner Sight 和 Slow Missiles
共用 `SrvDo006`，但写入不同的目标状态和属性，不能用同一个手写值替代数据公式。

### Amazon 召唤依赖完整的原生初始化链

`SrvDo015/016` 创建 Decoy/Valkyrie 后，还调用
`D2GAME_SKILLS_SetSummonBaseStats_6FD0CB10` 和
`D2GAME_SetSummonPassiveStats_6FD0C530`。后者会安装 PassiveStat、AuraStat、SumSkill、
生命加成、UMod/Overlay，并生成装备。Valkyrie 后续攻击伤害因此不只是 MonStats 的裸值；
Decoy 虽然不攻击，其生命/防御语义也不能只靠一个 owner-HP 百分比代替。

## 已确认的 riiablo 差异

1. **Guided Arrow（22）**：原生 `Calc1=ln34` 在等级 1 为 `0%`；当前
   `spawnGuidedArrow` 对非正值启用手写 fallback，错误变成 `+5%`。
2. **Dopplezon（28）**：已创建实体、等级、owner-relative HP 和时限，但缺原版被动属性、
   额外 `Calc1` 最大生命、召唤技能/装备、UMod 和 Overlay 初始化。
3. **Valkyrie（32）**：已创建实体和 Valkyrie 状态，但缺被动属性、SumSkill 和按 `Calc2`
   物品等级生成的装备；这些缺失会直接改变女武神攻击伤害。

这些剩余差异继续由完整性测试锁定，不在不相关技能审计中改写。

## DMG-04 当前进度

Magic Arrow（6）等级 1–20 已按“无装备、owner 武器伤害为 0、单箭单目标、抗性前总整数
伤害”批准。技能自身物理曲线在等级 1–20 分别产生 1–20 点总伤害；`magicarrow` 的
`SrvDmgFunc=1` 调用 `MISSMODE_SrvDmg01_FireArrow_MagicArrow_ColdArrow`，并按
`DmgCalc1=dl12`、`dParam1=1`、`dParam2=1` 把 1%–20% 的物理伤害重新分配为魔法伤害，
不会改变 8.8 定点总量。

Fire Arrow（7）等级 1–20 已按“无装备、owner 武器伤害为 0、Exploding Arrow 硬点为
0、单箭单目标、抗性前火焰伤害”批准。1.10f 的火焰曲线从等级 1 的 1–4 增长到等级
20 的 63–70，20 个等级均与 `MissileDamageResolver.initializeSkill` 的生产快照一致。
`firearrow` 同样使用 `SrvDmgFunc=1`；其 `DmgCalc1=dl12`、`dParam1=3`、`dParam2=2`
会把 3%–41% 的物理包转换为火焰，但本批武器物理包为 0，所以不会改变上述结果。

riiablo 生产快照的物理与魔法通道当前按整数保存，因此 Magic Arrow 基础矩阵只批准与
D2MOO 一致的抗性前总整数范围；原版 8.8 小数通道分配、固定武器包，以及 Fire Arrow
固定武器包的物理转火焰分别留给 DMG-06。物理/魔法/火焰抗性、穿透、吸收和 PvP 留给
DMG-08，不能从本批 `delta=0` 推断分通道最终结算已经完全等价。

Inner Sight（8）等级 1–20 已按“无直接伤害，`expected_*`/`riiablo_actual_*`/`delta_*`
明确 N/A”批准。D2MOO `SrvDo006` 计算 `AuraStatCalc=-edmn`，回调只向敌对目标安装
`armorclass` 定时负值，不创建导弹或伤害记录。分段防御削减从等级 1 的 `-40` 增长到
等级 20 的 `-815`。审计补齐 `SkillFormula` 的未移位 `edmn/edmx` token，并把生产路径从
手写线性值改为原生分段曲线；等级 2 现为 `-65`。持续时间、范围、目标过滤和最终命中率
影响不属于 DMG-04 伤害值，留给后续行为与结算审计。

Critical Strike（9）等级 1–20 已按“永久被动暴击概率，不拥有独立伤害包”批准；三组伤害
`expected_*`、`riiablo_actual_*` 和 `delta_*` 明确 N/A 并保持空白。D2MOO
`SKILLS_RefreshPassiveSkills` 计算 `PassiveCalc=dm12`，把结果写入
`passive_critical_strike` 状态属性；`Param1=5`、`Param2=80` 产生 16%–68% 的等级 1–20
概率曲线。近战伤害路径读取该属性，成功时只把既有物理伤害翻倍；导弹武器包也复制并消费
该属性。随机边界、与武器专精/Deadly Strike 的优先级、双倍物理包和最终结算留给
DMG-06/08。

Jab（10）等级 1–20 已按“技能本身不拥有固定伤害包，三个 SQ keyframe 各创建一份独立
武器记录”批准；三组伤害 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确 N/A 并保持
空白。1.10f `Calc1=ln34` 产生 `-15%`–`42%` 的武器物理加成，等级 6 的 `0%` 是合法
公式结果。审计修复了生产路径把零误判为公式缺失并 fallback 到 `+48%` 的差异：现在只有
公式字段为空时才使用兼容 fallback。固定武器包、每段实际值和三段施法总量留给 DMG-06。

Cold Arrow（11）等级 1–20 已按“无装备、owner 武器伤害为 0、Ice Arrow 硬点为 0、
单箭单目标、抗性前冷伤”批准。`HitShift=7` 使等级 1 的源表 `6–8` 对应整数 `3–4`，
等级 20 的 `106–112` 对应 `53–56`；生产快照逐级一致。`coldarrow` 的
`SrvDmgFunc=1`、`DmgCalc1=dl12` 和 `dParam=3/2` 会把物理包的 3%–41% 转为冷伤，
但本批武器物理包为 0。固定武器转换留给 DMG-06；100–670 帧 chill length 与最终
状态/结算留给 DMG-07/08。

Multiple Shot（12）等级 1–20 已按“技能本身不拥有固定伤害包，每条 lane 各自持有一枚
`SrcDam=96` 武器导弹”批准；三组伤害 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确
N/A 并保持空白。1.10f `Calc1="min(24,ln12)"` 产生 2–21 条 lane，`Calc2=par3` 固定为
1 帧激活值，`Calc3=2` 固定中央两条 lane。D2MOO `SrvDo008` 为每条 lane 独立调用
`MISSILES_CreateMissileFromParams`，其伤害快照由通用导弹链按 96/128 武器份额建立。
固定武器值、中央/外侧 lane 标志的消费语义、碰撞目标集合和整次施法总量留给 DMG-06。

Dodge（13）等级 1–20 已按“永久被动近战规避概率，不拥有独立输出伤害包”批准；三组伤害
`expected_*`、`riiablo_actual_*` 和 `delta_*` 明确 N/A 并保持空白。1.10f
`PassiveCalc=dm12` 使用 `Param1=10`、`Param2=65`，产生 18%–56% 的概率曲线。
D2MOO 被动刷新把结果写入永久 `dodge` 状态的 `passive_dodge` 属性；近战命中路径在填充、
汇总和执行伤害前读取该值，成功时将命中改为 Dodge。随机边界、盾牌/武器格挡优先级、
动作状态与最终来袭伤害结算留给 DMG-08。

Power Strike（14）等级 1–20 已按“单次成功近战记录中的技能闪电分量、零武器包、零协同、
抗性前 canonical getter 范围”批准。1.10f `EMin=1`、`EMax=16`，最大值五段增量为
`18/36/54/72/90`，产生等级 1 的 `1–16` 到等级 20 的 `1–646`；生产元素 resolver
逐级一致。D2MOO `SrvSt06` 只在命中成功后调用元素伤害 roll，并把结果保存在按
`SrcDam=128` 建立的同一 combat record；`SrvDo002` 在关键帧消费该记录并扣耐久。
武器物理包、`Calc1` 增强物理、运行时排除 canonical 最大值的随机结果和整份记录总量留给
DMG-06，最终元素结算留给 DMG-08。下一项优先审核 Poison Javelin（15）。
