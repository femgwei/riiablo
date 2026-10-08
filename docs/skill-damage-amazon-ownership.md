# Amazon 技能伤害所有者审计

本文件完成 DMG-03B 的 Amazon 部分：技能 ID 6–35，共 30 项。结论以 Diablo II
1.10f 的 `Skills.txt` / `Missiles.txt` 和 D2MOO 调用路径为准；本阶段只确认伤害所有者、
结算职责和 riiablo 覆盖状态。后续 DMG-04 已开始按本清单逐项写入有独立证据的
逐等级 `expected_*` 黄金值。

机器可检查的逐项证据见 `skill-damage-amazon-ownership.tsv`。本轮显式使用
`G:\BaiduNetdiskDownload\Diablo II 1.10F` 取证；1.14 输出只用于识别版本污染，没有写入结论。

## 结论

- 30/30 项均已确认原版所有者和 D2MOO 路径。
- 28 项已有生产实现和聚焦测试证据。
- 2 项仍存在明确的 riiablo 差异：Dopplezon、Valkyrie。
- Inner Sight 原有的线性防御削减差异已在 DMG-04 中按 1.10f 数据与 D2MOO 调用链修复；
  没有用 dark-magic 或 1.14 数值覆盖用户已经验证的 Amazon 生产逻辑。
- Magic Arrow、Fire Arrow、Inner Sight、Critical Strike、Jab、Cold Arrow、Multiple Shot、
  Dodge、Power Strike、Poison Javelin、Exploding Arrow、Slow Missiles、Avoid、Impale、
  Lightning Bolt、Ice Arrow、Guided Arrow 与 Penetrate 已完成等级 1–20 基础审计；380/600 个 Amazon
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

1. **Dopplezon（28）**：已创建实体、等级、owner-relative HP 和时限，但缺原版被动属性、
   额外 `Calc1` 最大生命、召唤技能/装备、UMod 和 Overlay 初始化。
2. **Valkyrie（32）**：已创建实体和 Valkyrie 状态，但缺被动属性、SumSkill 和按 `Calc2`
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
DMG-06，最终元素结算留给 DMG-08。

Poison Javelin（15）等级 1–20 已按“单个 `poisonjavcloud` 对单个目标的完整持续期毒伤、
零装备、零 Plague Javelin 硬点、抗性前”批准。1.10f 的 `HitShift=0` 使 `Skills.txt`
元素 getter 直接成为 8.8 每帧 fixed rate：等级 1 为 `32–48`，等级 20 为 `592–656`；
持续时间从 200 增长到 1150 帧，完整持续期整数总量从 `25–37` 增长到 `2659–2946`，
20 个等级的 `delta_min/max` 均为 0。`SrvDo02` 发出移动 `poisonjavcloud`，`SrvDo03`
每原生帧碰撞；根标枪武器命中、云数量/覆盖/重叠/刷新留给 DMG-06/07，毒抗和最终结算
留给 DMG-08。

Exploding Arrow（16）等级 1–20 已按“单个 `explodingarrowexp2` 对单个目标的一次技能
火焰命中、零装备、零 Fire Arrow 硬点、抗性前”批准。等级 1 为 `2–6`，等级 20 为
`129–149`，20 个等级的 `delta_min/max` 均为 0。D2MOO `SrvHit04` 从根箭创建子导弹，
`SrvHit01` 用子导弹快照向半径内目标分发火焰包；根箭只保留 `SrcDam=128` 武器命中。
武器值留给 DMG-06，范围目标数和施法总量留给 DMG-07，最终元素结算留给 DMG-08。

Slow Missiles（17）等级 1–20 已按“定时投射物速度状态，不拥有独立输出伤害包”批准；
三组伤害 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确 N/A 并保持空白。1.10f
`AuraTargetState=slowmissiles`、`AuraStat=skill_handofathena`、`AuraStatCalc=ln12`，
`Param1=33`、`Param2=0` 使全部等级都把可减速投射物速度设为正常值的 33%。持续时间
`ln34` 从 300 增长到 3150 帧，范围 `ln56` 固定为 20。

D2MOO `SrvDo006` 把这些字段原样交给通用诅咒状态回调；`Missiles.cpp` 只在新建、声明
`CanSlow` 的怪物投射物时读取状态并乘速度，不创建 combat damage record。riiablo 的状态
安装与新投射物速度消费者均已覆盖。目标过滤、状态刷新/覆盖和实际轨迹属于 DMG-07，
最终来袭伤害结果属于 DMG-08。

Avoid（18）等级 1–20 已按“永久被动远程规避概率，不拥有独立输出伤害包”批准；三组
伤害 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确 N/A 并保持空白。1.10f
`PassiveState=avoid`、`PassiveStat=passive_avoid`、`PassiveCalc=dm12`，`Param1=15`、
`Param2=75` 产生 24%–65% 的等级曲线。

D2MOO 被动刷新把结果写入永久状态。导弹命中路径以 `bAvoid=1` 调用
`SUNITDMG_ApplyBlockOrDodge`；移动单位只检查 Evade，静止单位依次处理适用的盾牌格挡、
Weapon Block 和 Avoid。Avoid 成功后清除 successful-hit，阻止后续伤害填充。riiablo
`StateUpdater`、`DefenseCalculator` 与 `CombatSystem` 使用同一状态和攻击上下文。随机边界、
防御优先级及最终来袭伤害结算属于 DMG-08。

Impale（19）等级 1–20 已按“单个增强武器记录，不拥有固定技能伤害包”批准；三组伤害
`expected_*`、`riiablo_actual_*` 和 `delta_*` 明确 N/A 并保持空白。1.10f
`Calc1=ln12`、`Param1=300`、`Param2=25` 产生 300%–775% 的武器伤害百分比；
`Calc2=par6-dm34` 产生 46%–25% 的额外资源损耗概率，`Calc3=par5` 固定为 1。

D2MOO `SrvSt07` 只在成功命中后调用 `SUNITDMG_ApplyDamageBonuses` 构造一次武器物理包，
并立即按 Calc2 随机决定堆叠武器扣一数量，或非堆叠武器扣 Calc3 耐久。随后
`SrvDo002` 消费已分配的记录。riiablo `prepareImpale`、`resolveImpale` 与
`drainImpaleDurability` 使用同一公式和生命周期。固定武器值、SrcDam、实际命中与普通
武器耐久消耗留在 DMG-06，最终抗性和生命结算留在 DMG-08。

Lightning Bolt（20）等级 1–20 已按“单枚 `lightningjavelin` 的技能自带闪电分量、
零武器包、零硬点协同、抗性前 canonical getter 范围”批准。1.10f `EMin=1`、
`EMax=40`，最大值五段增量为 `12/18/28/48/88`，产生等级 1 的 `1–40` 到等级 20 的
`1–380`；生产 `MissileDamageResolver.initializeSkill` 在 owner 武器伤害为 0 时逐级输出
相同范围，并保留物理通道为 0。

D2MOO 通用技能处理器创建 `lightningjavelin`，导弹伤害初始化把 `Skills.txt` 闪电曲线与
`SrcDam=96` 武器包放入同一快照；`MISSMODE_SrvDmg12_LightningJavelin` 在命中时求值
`DmgCalc=dl12`，最多把物理包的 100% 转入导弹元素通道。原版
`MISSMODE_RollDamageValue` 对 `max-min` 调用有限随机数，因此运行时不会取到 canonical
最大值；矩阵仍记录 getter 范围。固定武器包、转换比例和整份命中记录留给 DMG-06，
抗性、免疫、吸收、PvP 与最终生命扣减留给 DMG-08。

Ice Arrow（21）等级 1–20 已按“单枚 `icearrow` 的技能自带冷伤分量、零武器包、零
Cold Arrow 硬点协同、抗性前 canonical getter 范围”批准。1.10f `EMin=6`、
`EMax=10`，五段等级增量产生等级 1 的 `6–10` 到等级 20 的 `216–232`；生产
`MissileDamageResolver.initializeSkill` 在 owner 武器伤害为 0 时逐级输出相同范围，物理
通道保持 0。

D2MOO `MISSMODE_SrvDmg02_IceArrow_RoyalStrikeChaos` 把导弹 `coldlength` 的
`dParam1=100%` 写入命中记录的 freeze length，并清空 cold length；因此等级 1–20 的
50–145 帧长度数值不变，但状态语义从 chill 转为 freeze。riiablo 用 `freezesTarget` 和
`FREEZE` 元数据保存同一转换。固定武器包、命中率与整份命中记录留给 DMG-06，冻结状态、
Boss/Unique/Hireling 回退为 chill、抗性缩时和碎冰死亡留给 DMG-07/08。下一项优先审核
Guided Arrow（22）。

Guided Arrow（22）等级 1–20 已按“单枚锁定目标的 `guidedarrow` 武器记录、无装备、
owner 武器伤害为 0”批准为明确 N/A：Skills.txt 的物理和元素固定伤害字段全部为 0，
技能不拥有可填入 `expected_min/max/total` 的独立伤害包。`Calc1=ln34` 只向
`SrcDam=128` 武器快照附加等级 1–20 的 `0%–95%` `damagepercent`。

D2MOO `SKILLS_SrvDo010_GuidedArrow_BoneSpirit` 只有在 `Calc1` 结果非零时才安装
`SKILLS_AddDamagePercentBonus`，所以等级 1 必须保留合法的 `0%`。riiablo 已移除把该零值
替换为 `+5%` 的 fallback，等级 1 导弹倍率现为 1.00；逐级公式和生产创建路径都有回归。
固定武器值、`SrcDam` 缩放、箭袋消耗与完整命中总量留给 DMG-06，抗性和最终结算留给
DMG-08。

Penetrate（23）等级 1–20 已按“永久攻击命中率状态，不拥有独立输出伤害包”批准；三组
伤害 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确 N/A 并保持空白。1.10f
`PassiveState=penetrate`、`PassiveStat=item_tohit_percent`、`PassiveCalc=ln12`，
`Param1=35`、`Param2=10` 产生等级 1–20 的 `35%–225%` 命中率加成，每级增加 10%。

D2MOO `SKILLS_RefreshSkill`（`D2Common/src/D2Skills.cpp:603`）读取 `nPassiveStat` 和
`dwPassiveCalc`，把 `ln12` 结果写入永久被动状态的 `STAT_ITEM_TOHIT_PERCENT`；
`SUNITDMG` 命中路径（`D2Game/src/UNIT/SUnitDmg.cpp:2439–2511`）读取该属性并加入攻击
命中率计算。Penetrate 不创建伤害记录、导弹或新伤害值；最终命中率消费留给 DMG-06，
抗性和最终生命结算留给 DMG-08。下一项优先审核 Charged Strike（24）。
