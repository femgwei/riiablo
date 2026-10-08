# Amazon 技能伤害所有者审计

本文件完成 DMG-03B 的 Amazon 部分：技能 ID 6–35，共 30 项。结论以 Diablo II
1.10f 的 `Skills.txt` / `Missiles.txt` 和 D2MOO 调用路径为准；本阶段只确认伤害所有者、
结算职责和 riiablo 覆盖状态。后续 DMG-04 已开始按本清单逐项写入有独立证据的
逐等级 `expected_*` 黄金值。

机器可检查的逐项证据见 `skill-damage-amazon-ownership.tsv`。本轮显式使用
`G:\BaiduNetdiskDownload\Diablo II 1.10F` 取证；1.14 输出只用于识别版本污染，没有写入结论。

## 结论

- 30/30 项均已确认原版所有者和 D2MOO 路径。
- 25 项已有生产实现和聚焦测试证据。
- 5 项存在明确的 riiablo 差异：Inner Sight、Jab、Guided Arrow、Dopplezon、Valkyrie。
- 没有用 dark-magic 或 1.14 数值覆盖用户已经验证的 Amazon 生产逻辑；本轮只登记有
  1.10f 数据和 D2MOO 调用链支持的差异。
- Magic Arrow 与 Fire Arrow 已完成等级 1–20 基础伤害审计；40/600 个 Amazon
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

1. **Inner Sight（8）**：1.10f `AuraStatCalc` 使用分段曲线，等级 2 为 `-65`；当前
   `calculateInnerSightDefenseReduce` 线性计算为 `-60`，等级越高偏差越大。
2. **Jab（10）**：原生 `Calc1=ln34` 在等级 6 合法得到 `0%`；当前
   `getPhysicalDamagePercent` 把零当成“公式缺失”，fallback 成 `+48%`。
3. **Guided Arrow（22）**：原生 `Calc1=ln34` 在等级 1 为 `0%`；当前
   `spawnGuidedArrow` 对非正值启用手写 fallback，错误变成 `+5%`。
4. **Dopplezon（28）**：已创建实体、等级、owner-relative HP 和时限，但缺原版被动属性、
   额外 `Calc1` 最大生命、召唤技能/装备、UMod 和 Overlay 初始化。
5. **Valkyrie（32）**：已创建实体和 Valkyrie 状态，但缺被动属性、SumSkill 和按 `Calc2`
   物品等级生成的装备；这些缺失会直接改变女武神攻击伤害。

这些差异本轮只登记并用完整性测试锁定，没有在逐等级黄金值建立前改写 Amazon 生产逻辑。

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
DMG-08，不能从本批 `delta=0` 推断分通道最终结算已经完全等价。下一项优先审核
Inner Sight（8）。
