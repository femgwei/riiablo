# D2MOO 1.10f 技能伤害通用公式证据

更新时间：2026-10-08

证据版本：The Phrozen Keep D2MOO `8322494ed1f715ad51552f169df76cf600fabc71`

适用范围：七职业技能伤害审计 DMG-03A。本文只固定 `Skills.txt` 通用曲线、
`HitShift`、协同百分比和元素持续时间的计算顺序。武器包、导弹所有权、毒素总量、
召唤继承、抗性和 PvP 结算仍由后续任务核对。

## 五段等级增量

D2MOO `source/D2Common/src/D2Skills.cpp:2459`
`SKILLS_CalculateDamageBonusByLevel` 定义了五段增量：

- 等级 1：增量为 0。
- 等级 2–8：使用第 1 段，共 7 次。
- 等级 9–16：在前 7 次基础上使用第 2 段，共 8 次。
- 等级 17–22：继续使用第 3 段，共 6 次。
- 等级 23–28：继续使用第 4 段，共 6 次。
- 等级 29 起：继续使用第 5 段。

因此边界必须按 `8/16/22/28` 切换，不能把五个字段理解为每五级一段，也不能在
等级 1 预加一次增量。`SkillDamageFormulaParityTest` 使用差异明显的五个增量值锁定
等级 1、8、9、16、17、22、23、28、29 的边界。

## 物理伤害顺序

D2MOO `D2Skills.cpp:2490` 的 `SKILLS_GetMinPhysDamage` 和 `D2Skills.cpp:2556` 的
`SKILLS_GetMaxPhysDamage` 使用以下顺序：

1. 仅当调用参数允许武器包且 `SrcDam` 非零时，读取武器最小/最大伤害。
2. 武器伤害乘 `SrcDam` 后除以 128，整数除法在这里截断。
3. 加上 `MinDam/MaxDam` 和五段等级增量。
4. 若存在 `DmgSymPerCalc`，执行 `damage += damage * bonus / 100`，再次按整数截断。
5. 最后执行 `damage << HitShift`，返回原生定点数伤害。

踢击是独立分支，不能套用普通武器公式；DMG-06 将单独审核 `SrcDam`、双持和踢击。

## 元素伤害顺序

D2MOO `D2Skills.cpp:2623` 的 `SKILLS_GetMinElemDamage` 和 `D2Skills.cpp:2685` 的
`SKILLS_GetMaxElemDamage` 使用以下顺序：

1. 计算 `EMin/EMax + 五段等级增量`。
2. 立即左移 `HitShift`，得到原生定点数。
3. 在定点数上执行 `damage += damage * EDmgSymPerCalc / 100`。
4. 调用参数允许时，再用同一份已含协同的伤害计算元素主修加成。
5. UI 或需要整数伤害的调用者随后右移 8 位；右移造成向下截断。

最小值存在原版特有门槛：只有定点数伤害大于 256，或 `EMinLev1` 非零时才应用
`EDmgSymPerCalc`。最大值只要公式存在就应用。该不对称行为已由
`SkillDamageFormulaParityTest` 固定，审计不能为了“看起来一致”而合并两条路径。

`DATATBLS_ApplyRatio` 位于
`source/D2Common/src/DataTbls/MonsterTbls.cpp:931`。常规伤害范围内等价于有符号整数
乘法后除以分母；除法发生位置决定取整结果，不能提前把伤害转成显示整数。

## 元素持续时间

D2MOO `D2Skills.cpp:2719` 的 `SKILLS_GetElementalLength` 只使用三个持续时间增量段：

- 等级 2–8 使用 `ELevLen1`。
- 等级 9–16 使用 `ELevLen2`。
- 等级 17 起使用 `ELevLen3`。

加上 `ELen` 后，再应用 `ELenSymPerCalc` 百分比并做整数截断。返回值仍是游戏帧，
不能未经 25 FPS 换算直接标为秒。

## 对黄金矩阵的约束

- `source_curve_*` 仍只是表格基值和分段增量，不是最终黄金值。
- `expected_*` 必须说明保存的是定点数、显示整数、每帧 rate 还是完整持续期 total。
- 每个技能还必须在 DMG-03B 确认真正的伤害所有者和调用参数，不能看到
  `Skills.txt` 有伤害列就默认使用技能表路径。
- 协同、主修和 `SrcDam` 的整数除法位置必须与上述原生顺序一致。

对应 riiablo 实现入口：

- `MissileDamageResolver.damageBonusByLevel`
- `MissileDamageResolver.skillElementalDamage`
- `MissileDamageResolver.skillElementalDamageFixed`
- `SkillFormula.evaluate`

对应回归测试：

- `SkillDamageFormulaParityTest`
- `FireBoltGoldenDamageTest`（DMG-04 首批：技能 36，等级 1–20）
- `IceBoltGoldenDamageTest`（DMG-04 第二批：技能 39，等级 1–20）
- `FireBallGoldenDamageTest`（DMG-04 第三批：技能 47，等级 1–20）
- `IceBlastGoldenDamageTest`（DMG-04 第四批：技能 45，等级 1–20）
- `GlacialSpikeGoldenDamageTest`（DMG-04 第五批：技能 55，等级 1–20）
- `LightningGoldenDamageTest`（DMG-04 第六批：技能 49，等级 1–20）
- `NovaGoldenDamageTest`（DMG-04 第七批：技能 48，等级 1–20）
- `FrostNovaGoldenDamageTest`（DMG-04 第八批：技能 44，等级 1–20）
- `ChargedBoltGoldenDamageTest`（DMG-04 第九批：技能 38，等级 1–20）
- `ChainLightningGoldenDamageTest`（DMG-04 第十批：技能 53，等级 1–20）

## DMG-04 首个逐级实例：Fire Bolt

Fire Bolt 由 `Skills.txt` 拥有伤害，`SrcDam=0`、`HitShift=7`。基础场景中 Fire Ball、
Meteor 硬点和 Fire Mastery 均为 0，因此等级曲线左移 7 位后没有额外百分比修正。
矩阵 `expected_min/max` 保存原生定点值 `>> 8` 后的单枚单目标整数伤害；测试另行断言
未截断的 8.8 定点中间值。等级 1 的定点范围为 768–1536、整数范围为 3–6；
等级 20 的定点范围为 11648–15488、整数范围为 45–60。

riiablo 实际值通过 `MissileDamageResolver.initializeSkill` 建立 `firemindam/firemaxdam`
生产快照后读取，未使用期望值公式回填。等级 1–20 的最小值和最大值差异均为 0。

## DMG-04 第二个逐级实例：Ice Bolt

Ice Bolt 同样由 `Skills.txt` 拥有伤害，`SrcDam=0`、`HitShift=7`。无协同、无 Cold
Mastery 场景中，等级 1 的定点范围为 768–1280、整数范围为 3–5；等级 20 的定点范围
为 9728–12672、整数范围为 38–49。riiablo 实际值从
`MissileDamageResolver.initializeSkill` 建立的 `coldmindam/coldmaxdam` 快照读取，等级
1–20 的差异均为 0。`cold length` 保留为独立语义，延后到 DMG-07 审核。

## DMG-04 第三个逐级实例：Fire Ball

Fire Ball 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=7`。无协同、无 Fire
Mastery 场景中，等级 1 的定点范围为 1536–3584、整数范围为 6–14；等级 20 的定点
范围为 51072–57984、整数范围为 199–226。riiablo 实际值从父导弹 `fireball` 的
`MissileDamageResolver.initializeSkill` 快照读取，等级 1–20 的差异均为 0。

`MISSMODE_SrvHit01_Fireball_ExplodingArrow_FreezingArrowExplosion` 负责命中后的范围扇出；
展示子导弹继承父伤害包，中心目标通过共享命中门禁只结算一次。因此矩阵保存“每个目标
一次命中”的范围，不为整次施法填写固定总伤害；多目标数量与累计值延后到 DMG-07。

## DMG-04 第四个逐级实例：Ice Blast

Ice Blast 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=7`。无 Ice Bolt、Blizzard、
Frozen Orb 协同且无 Cold Mastery 的基础场景中，等级 1 的定点范围为 2048–3072、整数
范围为 8–12；等级 20 的定点范围为 64768–68224、整数范围为 253–266。riiablo 实际值
从 `iceblast` 导弹的 `MissileDamageResolver.initializeSkill` 快照读取，等级 1–20 的
`coldmindam/coldmaxdam` 差异均为 0。

`MISSMODE_SrvDmg04_IceBlast` 会把 cold length 转为 freeze length，但不改变本批核对的
一次命中伤害范围。因此矩阵的 `expected_total` 保持空白，冻结状态、持续时间和最终抗性
结算延后到 DMG-07。

## DMG-04 第五个逐级实例：Glacial Spike

Glacial Spike 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=7`。无 Ice Bolt、Ice
Blast、Frozen Orb 协同且无 Cold Mastery 的基础场景中，等级 1 的定点范围为
4096–6144、整数范围为 16–24；等级 20 的定点范围为 57600–62080、整数范围为
225–242。riiablo 实际值从 `glacialspike` 导弹的
`MissileDamageResolver.initializeSkill` 快照读取，等级 1–20 的
`coldmindam/coldmaxdam` 差异均为 0。

`MISSMODE_SrvHit13_GlacialSpike_HellMeteorDown` 负责命中点范围投递和冻结语义，不改变
每个目标收到的一次伤害包。因此矩阵只批准单目标一次命中范围，`expected_total` 保持空白；
范围目标数量、冻结长度和整次施法累计延后到 DMG-07。

## DMG-04 第六个逐级实例：Lightning

Lightning 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=8`。无 Charged Bolt、Chain
Lightning、Nova 协同且无 Lightning Mastery 的基础场景中，最小值五段增量全为 0，
最大值五段增量为 `8/12/20/28/36`。等级 1 的定点范围为 256–10240、整数范围为
1–40；等级 20 的定点范围为 256–69632、整数范围为 1–272。

riiablo 实际值从 `lightningbolt` 导弹的 `MissileDamageResolver.initializeSkill` 快照
读取，等级 1–20 的 `lightmindam/lightmaxdam` 差异均为 0。D2MOO
`MISSILE_CalculateDamageData` 负责把技能伤害安装到导弹；本批只批准一个伤害包对一个
目标的一次结算。穿透、多目标或重复接触和整次施法累计延后到 DMG-06/07，抗性与免疫
结算延后到 DMG-08，因此 `expected_total` 保持空白。

## DMG-04 第七个逐级实例：Nova

Nova 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=8`，且没有伤害协同公式。基础
场景中最小值五段增量为 `6/7/8/9/10`，最大值五段增量为 `8/9/10/11/12`。等级 1
的定点范围为 256–5120、整数范围为 1–20；等级 20 的定点范围为 33536–48128、
整数范围为 131–188。

D2MOO `SKILLS_SrvDo022_NovaAttack` 调用 `sub_6FD14170` 固定创建 64 路技能导弹，
`MISSILE_CalculateDamageData` 为每路安装 Skills.txt 伤害。riiablo 实际值从 `nova`
导弹的 `MissileDamageResolver.initializeSkill` 快照读取，等级 1–20 的
`lightmindam/lightmaxdam` 差异均为 0。`SorceressNovaIntegrationTest` 另行锁定同一次
施法共享目标命中门禁；矩阵仍只批准每个目标一次收到的伤害包，64 路投递、多目标和
cast-wide total 延后到 DMG-07，抗性与免疫结算延后到 DMG-08。

## DMG-04 第八个逐级实例：Frost Nova

Frost Nova 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=7`。无 Blizzard、Frozen
Orb 协同且无 Cold Mastery 的基础场景中，最小值五段增量为 `4/6/8/10/12`，最大值
五段增量为 `5/7/9/11/13`。等级 1 的定点范围为 512–1024、整数范围为 2–4；
等级 20 的定点范围为 14336–17280、整数范围为 56–67。

D2MOO `SKILLS_SrvDo022_NovaAttack` 和 `sub_6FD14170` 固定创建 64 路技能导弹，
`MISSILE_CalculateDamageData` 为每路安装 Skills.txt 冷伤害。riiablo 实际值从
`frostnova` 导弹的 `MissileDamageResolver.initializeSkill` 快照读取，等级 1–20 的
`coldmindam/coldmaxdam` 差异均为 0。`ELen=200`、三级长度增量均为 25 的冷缓持续
时间保留在源矩阵，但不计入本批基础伤害；冷缓长度、64 路投递、多目标和 cast-wide
total 延后到 DMG-07，抗性、免疫与 Cold Mastery 穿透结算延后到 DMG-08。

## DMG-04 第九个逐级实例：Charged Bolt

Charged Bolt 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=7`。无 Lightning 协同且
无 Lightning Mastery 的基础场景中，最小值和最大值的五段增量均为 `1/1/2/3/4`。
等级 1 的定点范围为 512–1024、整数范围为 2–4；等级 20 的定点范围为
3456–3968、整数范围为 13–15。

D2MOO `SKILLS_SrvDo017_ChargedBolt_BoltSentry` 读取 `calc1` 并为每枚弹丸调用原生
Charged Bolt 路径初始化；`MISSILE_CalculateDamageData` 再从技能 38 安装 Skills.txt
闪电伤害。riiablo 实际值从 `chargedbolt` 导弹的
`MissileDamageResolver.initializeSkill` 快照读取，等级 1–20 的
`lightmindam/lightmaxdam` 差异均为 0。矩阵只批准单枚导弹对单个目标的一次伤害包；
弹丸数量、确定性种子、77 帧路径、重复/多目标接触和 cast-wide total 延后到 DMG-07，
抗性、免疫及 Lightning Mastery 结算延后到 DMG-08。

## DMG-04 第十个逐级实例：Chain Lightning

Chain Lightning 由 `Skills.txt` 持有伤害，`SrcDam=0`、`HitShift=8`。无 Charged Bolt、
Lightning、Nova 协同且无 Lightning Mastery 的基础场景中，最小值五段增量全为 0，
最大值五段增量为 `11/13/15/15/15`。等级 1 的定点范围为 256–10240、整数范围为
1–40；等级 20 的定点范围为 256–71936、整数范围为 1–281。

D2MOO `SKILLS_SrvDo026_ChainLightning` 只创建一枚根导弹，并把 `calc1` 结果作为链预算；
`MISSMODE_SrvHit12_ChainLightning_LightningStrike` 每次命中创建下一链段，继承相同技能
ID 和技能等级并递减预算。`MISSILE_CalculateDamageData` 为每个链段安装同一 Skills.txt
伤害包。审计发现 riiablo 的根导弹和续链导弹此前只走通用 Missiles.txt 初始化，技能
拥有的 `chainlightning` 因而没有权威伤害快照；现已在根段和技能 53 续链段调用
`MissileDamageResolver.initializeSkill`，等级 1–20 的 `lightmindam/lightmaxdam` 与独立
黄金数组一致；`continuationSegmentKeepsTheAuthoritativeSkillSnapshot` 另行让根段实际命中，
并断言 SrvHit12 创建的每个后续链段继续持有 1–40 的一级伤害快照。矩阵只批准每个链段
对单个目标的一次伤害；跳跃目标选择、多目标累计和 cast-wide total 延后到 DMG-07，
抗性、免疫及 Lightning Mastery 结算延后到 DMG-08。
