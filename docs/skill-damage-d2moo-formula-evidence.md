# D2MOO 1.10f 技能伤害通用公式证据

更新时间：2026-10-07

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
