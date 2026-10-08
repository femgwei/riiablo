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
- `ThunderStormGoldenDamageTest`（DMG-04 第十一批：技能 57，等级 1–20）
- `StaticFieldGoldenDamageTest`（DMG-04 第十二批：技能 42，等级 1–20）
- `TelekinesisGoldenDamageTest`（DMG-04 第十三批：技能 43，等级 1–20）
- `BlazeGoldenDamageTest`（DMG-04 第十四批：技能 46，等级 1–20）
- `FireWallGoldenDamageTest`（DMG-04 第十五批：技能 51，等级 1–20）
- `InfernoGoldenDamageTest`（DMG-04 第十六批：技能 41，等级 1–20）
- `ShiverArmorGoldenDamageTest`（DMG-04 第十七批：技能 50，等级 1–20）

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

## DMG-04 第十一个逐级实例：Thunder Storm

Thunder Storm 由 `Skills.txt` 持有每次雷击伤害，`SrcDam=0`、`HitShift=8`，没有伤害
协同公式。基础场景中最小值和最大值的五段增量均为 `10/10/11/11/11`。等级 1 的
定点范围为 256–25600、整数范围为 1–100；等级 20 的定点范围为 49920–75264、
整数范围为 195–294。

D2MOO `SKILLS_SrvSt13_ThunderStorm` 初始化上次目标和首次执行标志；
`SKILLS_SrvDo029_ThunderStorm` 首次建立带技能 ID/等级的状态，后续周期选择目标并创建
`thunderstorm1`，随后通过 `MISSMODE_SrvDmgHitHandler` 立即结算一次命中。
`MISSILE_CalculateDamageData` 从技能 57 安装 Skills.txt 闪电伤害。riiablo 的
`StateUpdater.processThunderStorm` 同样为每次周期雷击调用
`MissileDamageResolver.initializeSkill`；等级 1–20 的 `lightmindam/lightmaxdam` 与独立
黄金数组一致，集成测试另行确认实际周期导弹携带技能 57、等级 1 和 1–100 权威快照。
矩阵只批准每次雷击对单目标的一次伤害包；状态持续时间、雷击间隔、目标选择、雷击次数
和 full-aura total 延后到 DMG-07，抗性、免疫、吸收、PvP 与 Lightning Mastery 结算
延后到 DMG-08。

## DMG-04 第十二个逐级实例：Static Field

Static Field 不使用普通 Skills.txt 最小/最大伤害曲线。D2MOO
`SKILLS_SrvDo020_StaticField` 以 `calc1=par4` 取得当前生命伤害百分比，以 `calc2=par3`
取得最小 signed 8.8 定点伤害，并在 Expansion 游戏中读取
`DifficultyLevels.txt.StaticFieldMin`。1.10f 数据在等级 1–20 均为 `calc1=25`、`calc2=0`；
等级只通过 `AuraRangeCalc=ln12` 改变半径。

`SKILLS_AuraCallback_StaticField` 先把目标当前生命右移 8 位为整数生命。若 Expansion
难度生命下限启用且当前生命不高于 `maxLife * StaticFieldMin / 100`，本次目标直接跳过；
这个判断是命中资格门禁，不是命中后生命夹紧。随后原版按
`currentIntegerLife * damagePct / 100` 截断，并把结果限制为最多 `currentLife - 1`，左移
8 位后再与 `calc2` 最小定点伤害取最大值。负抗性在普通元素结算前被反向补偿，因此不会
放大 Static Field，正抗、免疫、吸收和 PvP 缩放仍由后续结算路径处理。

DMG-04 为这一非固定伤害技能锁定可复现输入：单个 100/100 生命怪物、Normal Expansion、
0 闪电抗性、无装备。独立 D2MOO 转录在等级 1–20 都得到 `25 << 8`，riiablo
`SorceressSkills.calculateStaticFieldRawDamageFixed` 同样得到 25 点，故 20 行
`expected_min/max` 与 `riiablo_actual_min/max` 均为 25，`delta_min/max=0`。
`StaticFieldGoldenDamageTest` 还锁定 1/100、34/100@33%、33/100@33%、51/100@50% 和
50/100@50% 五个边界；完整 ECS 测试继续覆盖难度门槛、负抗补偿、正抗、免疫、吸收、
PvP、目标过滤和无导弹语义。多目标及重复施法总量延后到 DMG-07，最终结算场景延后到
DMG-08。

## DMG-04 第十三个逐级实例：Telekinesis

Telekinesis 由 `Skills.txt` 持有直接命中伤害。1.10f 行为为 `SrvStFunc=12`、
`SrvDoFunc=21`、`HitShift=8`、`EType=ltng`，物理基值和五段增量均为 0；元素基值为
1–2，最小值与最大值的五段增量均为 `1/1/1/1/1`，且没有伤害协同公式。无装备、无
协同、无 Lightning Mastery 的基础场景中，等级 1 的定点范围为 256–512、整数范围为
1–2；等级 20 的定点范围为 5120–5376、整数范围为 20–21。

D2MOO `SKILLS_SrvSt12_Telekinesis_DragonFlight` 先要求目标存在、距离不超过
`AuraRangeCalc`，并对玩家/怪物目标检查敌对关系和双方城镇限制。
`SKILLS_SrvDo021_Telekinesis` 仅允许玩家施法者；对玩家或怪物目标先调用
`D2GAME_RollPhysicalDamage_6FD14EC0`，再调用 `D2GAME_RollElementalDamage_6FD14DD0`，
随后合并 Skills.txt 的 `ResultFlags`、`HitFlags`、`HitClass` 与 `Param[1]` 击退概率，最后
走普通 missile-style 命中结算。元素滚动调用 `SKILLS_GetMin/MaxElemDamage(..., a4=1)`，
因此 Lightning Mastery 属于生产伤害包；基础黄金场景明确把它固定为 0。

`D2GAME_RollElementalDamage_6FD14DD0` 把 `max-min` 传给 limited RNG，因此原生运行时的
上端点遵循排他约定；黄金矩阵仍记录两个 D2Common getter 返回的规范最小/最大范围。
riiablo `ServerSkillSystem.applyTelekinesis` 现在复现单位目标门禁、物理/元素范围、
Lightning Mastery、抗性/免疫/吸收、PvP、生命扣减与死亡事件，并发出带闪电通道的
missile-style `DamageEvent`，但不创建导弹实体。等级 1–20 的生产范围与独立黄金数组完全
一致。物品拾取/移动、对象操作以及击退位移属于行为审计，不计入本次 DMG-04 数值批准。

## DMG-04 第十四个逐级实例：Blaze

Blaze 的黄金单位是“单个 `blaze` 地面导弹对单个目标在单个游戏帧内的 8.8 定点火焰
伤害率”。1.10f `Skills.txt` 为 `HitShift=4`、`EMin=4`、`EMax=8`，最小值和最大值的
五段增量均为 `2/3/4/6/9`，没有伤害协同。等级 1 的定点范围为 `64–128`，即
0.25–0.5 生命/帧；等级 20 为 `928–992`，即 3.625–3.875 生命/帧。矩阵直接保存 8.8
定点值，避免把小于 1 点的每帧伤害截断为 0。

D2MOO `SKILLS_SrvDo023_Blaze_EnergyShield_SpiderLay` 安装 `STATE_BLAZE` 并保存技能 ID、
等级和持续时间；`SKILLS_CreateBlazeMissile` 只在角色实际移动且不在城镇时创建地面导弹，
并把技能 ID/等级传给导弹伤害计算。`MISSILE_CalculateDamageData` 对技能拥有的伤害调用
`SKILLS_GetMin/MaxElemDamage(..., a4=1)`，因此 Fire Mastery 属于生产快照，但本批基础
场景固定为 0，留到 DMG-05 单独审核。

`MISSMODE_SrvDo05_FireWall_ImmolationFire_MeteorFire` 每个游戏帧执行碰撞，不读取
`DamageRate` 作为等待帧数。`Missiles.cpp` 把该字段存入 `STAT_DAMAGE_FRAMERATE`，
`MissMode.cpp` 再复制到伤害包的 `dwPiercePct`，最后 `SUnitDmg.cpp` 以
`DamageRate / 1024` 缩放平面 DR/MDR。Blaze 的 1.10f `DamageRate=0`。`SrvDmg03` 的
`dParam1 / 128` 只控制受击反应概率，也不改变碰撞频率。

riiablo `StateUpdater.processBlazeTrail` 已按实际移动生成 `tickInterval=1` 的地面导弹，
`MissileDamageResolver.initializeSorceressFireArea` 保存逐级 8.8 火焰率，
`MissileCollisionSystem.resolveFixedElementalRate` 在首个游戏帧即可对重叠目标结算。
`BlazeGoldenDamageTest` 比较 20 级独立常量与生产快照；专项数据和 ECS 测试同时锁定
`DamageRate=0`、逐帧碰撞和首帧实际扣血。多个 trail 重叠、导弹生命周期、整段状态
总伤害归入 DMG-07，抗性、吸收和 PvP 归入 DMG-08。

## DMG-04 第十五个逐级实例：Fire Wall

Fire Wall 的黄金单位是“单个 `firewall` 段对单个目标在单个游戏帧内的 8.8 定点火焰
伤害率”。1.10f `Skills.txt` 为 `HitShift=4`、`EMin=15`、`EMax=20`，最小值和最大值
五段增量均为 `9/14/21/21/21`。基础场景把所有协同和 Fire Mastery 固定为 0。等级 1
定点范围为 `240–320`，即 0.9375–1.25 生命/帧；等级 20 为 `4384–4464`，即
17.125–17.4375 生命/帧。矩阵保存未截断的 8.8 定点值。

D2MOO `SKILLS_SrvDo024_FireWall` 在非城镇目标点创建两条相反方向的 `firewallmaker`，
并创建一个中心 `firewall` 段。`MISSMODE_SrvDo06_MoltenBoulder_FireWallMaker` 沿 maker
移动路径创建 `SubMissile1`，把 maker 的技能 ID 和技能等级原样写入子段。
`MISSILE_CalculateDamageData` 因此继续从 Fire Wall 的 Skills.txt 曲线取得伤害并应用
Fire Mastery，而不是从 maker 或子段的 Missiles.txt 伤害列替代技能曲线。

中心段和子段均由 `MISSMODE_SrvDo05_FireWall_ImmolationFire_MeteorFire` 每个游戏帧执行
碰撞。Fire Wall 的 `DamageRate=41` 通过 `STAT_DAMAGE_FRAMERATE` 进入伤害包，最终只把
平面 DR/MDR 按 `41/1024` 缩放；它不是 41 帧攻击间隔。`SrvDmg03` 的 `dParam1/128`
仍只控制受击反应概率。

riiablo `ServerSkillSystem.spawnFireWall` 已建立两条 maker 和中心段，
`MissileCollisionSystem.processFireWallMaker` 创建继承技能 51/等级的子段；所有实际火段
均保存 `tickInterval=1`、逐级 8.8 火焰率与 `DamageRate=41`，并在第一个模拟帧即可扣血。
`FireWallGoldenDamageTest` 比较 20 级独立常量与生产快照。多段重叠、墙体长度、生命周期
和整次施法总伤害归入 DMG-07，协同/Fire Mastery 归入 DMG-05，抗性、吸收和 PvP
归入 DMG-08。

## DMG-04 第十六个逐级实例：Inferno

Inferno 的基础黄金单位是“单个 `SrvDo019` 创建的一个 `infernoflame1` 流导弹对单个
目标的一次 8.8 定点火焰命中”。1.10f `Skills.txt` 为 `HitShift=2`、`EMin=32`、
`EMax=64`，最小值五段增量为 `24/26/28/32/36`，最大值为
`24/27/29/33/37`。基础场景把 Warmth 协同和 Fire Mastery 固定为 0。等级 1 的定点
范围为 `128–256`，即 0.5–1.0 生命/脉冲；等级 20 为 `2080–2256`，即
8.125–8.8125 生命/脉冲。矩阵保存未截断的 8.8 定点值。

D2MOO `SKILLS_SrvSt11_Inferno_ArcticBlast` 校验起始法力并调用 `SKILLS_StartInferno`。
第一次启动只建立 20 帧 `STATE_INFERNO`，把技能参数清零，不创建流导弹；已有状态会刷新
为 6 帧并调用 `SKILLS_DoInferno`。后续 `SKILLS_SrvDo019_Inferno_ArcticBlast` 调用
`SKILLS_DoInferno`，每次只读取 `SrvMissileA`，把 `calc1` 结果作为射程，并把技能 ID 与
技能等级写入新导弹。`MISSILE_CalculateDamageData` 再从技能 41 取得 8.8 火焰范围并应用
Fire Mastery。这里的定点范围属于每枚流导弹的一次伤害包；通道的事件频率决定每秒总量。

审计发现 riiablo 原通用服务器导弹分支会把 Inferno 表中相同的 A/B/C 三列全部创建，
并通过普通整数 `initializeSkill` 把低等级的亚整数伤害截断。现在
`ServerSkillSystem.spawnSorceressInferno` 每个 `SrvDo019` 只创建一枚 `SrvMissileA`，按
`calc1` 设置射程，并用 `MissileDamageResolver.initializeSorceressFireArea` 保留 8.8
伤害；一级实际命中测试确认一次扣除 0.5 至小于 1.0 生命。`InfernoGoldenDamageTest`
比较等级 1–20 的独立常量与生产快照，全部差异为 0。

`SrvSt11` 状态刷新、耗蓝、动画事件频率、重复脉冲、障碍截断和整次通道总伤害归入
DMG-07；Warmth 协同与 Fire Mastery 归入 DMG-05，抗性、吸收和 PvP 归入 DMG-08。

## DMG-04 第十七个逐级实例：Shiver Armor

Shiver Armor 的基础黄金单位是“`UNITEVENT_ATTACKEDINMELEE` 触发的一次 `EventFunc03`
直接冷伤害反击”。它不创建导弹，也不要求原近战攻击命中。1.10f `Skills.txt` 为
`HitShift=7`、`EMin=12`、`EMax=16`，最小值五段增量为 `4/6/8/10/12`，最大值为
`5/7/9/11/13`。基础场景把 Frozen Armor 与 Chilling Armor 硬点协同固定为 0。

D2MOO `SKILLS_SrvDo018_DefensiveBuff` 把技能 ID/等级保存在状态表，并注册
`EventFunc03`。回调用状态中的等级调用 `D2GAME_RollElementalDamage_6FD14DD0`；后者先按
`SKILLS_GetMinElemDamage/SKILLS_GetMaxElemDamage` 取得 8.8 值，再以 `max-min` 调用
`ITEMS_RollLimitedRandomNumber`，所以规范 getter 最大值是运行时排除端点。矩阵保留 getter
范围：等级 1 为 `6–8` 生命，等级 20 为 `60–71` 生命；实际随机结果分别是 `[6,8)` 与
`[60,71)`。`SKILLS_GetElementalLength` 给出的冷长度从等级 1 的 100 帧增长到等级 20 的
500 帧。

riiablo 的 `SorceressSkills.getArmorColdDamage/getArmorColdLength` 与 20 级独立常量一致。
审计同时发现 `StateUpdater.applyShiverArmor` 原先把最大值包含在随机区间中，并把
`dwColdLen` 误施加为冻结状态。现在它使用与 D2MOO 相同的排除上界和普通 cold 减速，并用
聚焦随机边界测试及实际近战事件集成测试保护。Frozen Armor/Chilling Armor 协同归入
DMG-05；抗性、Cold Mastery 穿透、吸收和 PvP 归入 DMG-08。

## DMG-04 第十八个逐级实例：Chilling Armor

Chilling Armor 的基础黄金单位是“`UNITEVENT_HITBYMISSILE` 触发的一枚
`chillingarmorbolt` 返回弹对入射攻击者的一次冷伤害命中”。1.10f `Skills.txt` 为
`HitShift=7`、`EMin=8`、`EMax=12`，最小值五段增量为 `2/4/6/8/10`，最大值为
`3/5/7/9/11`。基础场景把 Frozen Armor 与 Shiver Armor 硬点协同固定为 0。

D2MOO `SKILLS_SrvDo018_DefensiveBuff` 把技能 ID/等级保存在状态表并注册 `EventFunc01`。
回调先验证攻击者、护甲持有者与敌对关系，再检查入射导弹的 `ReturnFire` 标志；只有满足
条件时才创建 `SrvMissileA`，并把护甲技能 ID/等级传给导弹。返回弹自身没有
`ReturnFire`，所以不会递归触发。`MISSILE_CalculateDamageData` 识别技能所属导弹后，从
Skills.txt#60 调用 `SKILLS_GetMin/MaxElemDamage` 和 `SKILLS_GetElementalLength`，不使用
Missiles.txt 的基础元素曲线。

矩阵保存规范 getter 范围：等级 1 为 `4–6` 生命，等级 20 为 `39–50` 生命；cold 长度
从 100 帧增长到 400 帧。实际命中由 `MISSMODE_RollDamageValue` 以 `max-min` 调用有限
随机数，所以运行时区间分别是 `[4,6)` 与 `[39,50)`。

riiablo 的 `StateUpdater.launchChillingArmorBolt` 已按 `ReturnFire` 门禁创建以护甲持有者
为 owner、入射攻击者为 target 的返回弹，并用 `MissileDamageResolver.initializeSkill`
保留技能 ID、等级、Skills.txt 冷伤害与 cold 长度。审计发现通用导弹结算原先包含最大
端点；`CombatSystem` 现仅对导弹物理和元素包采用原版排除上界，近战随机保持原语义。
Frozen Armor/Shiver Armor 协同归入 DMG-05；返回弹最终抗性、Cold Mastery 穿透、吸收
和 PvP 归入 DMG-08。

## DMG-04 第十九个逐级实例：Magic Arrow

Magic Arrow 的基础黄金单位是“一枚 `magicarrow` 对单个目标在抗性前造成的总整数伤害”，
场景固定为无装备、owner 武器最小/最大伤害均为 0、无协同。1.10f `Skills.txt#6` 为
`SrcDam=128`、`HitShift=8`、`MinDam=MaxDam=1`，物理最小/最大五段增量均为
`1/1/1/1/1`，所以 `SKILLS_GetMinPhysDamage` 和 `SKILLS_GetMaxPhysDamage` 在等级 1–20
返回完全相同的 8.8 定点曲线 `256, 512, ..., 5120`，即 1–20 点整数总伤害。

`MISSILE_CalculateDamageData` 先把技能物理曲线与 `SrcDam` 武器包组合。基础场景的武器包
为 0，因此只留下上述技能曲线。命中时 `magicarrow` 的 `SrvDmgFunc=1` 进入
`MISSMODE_SrvDmg01_FireArrow_MagicArrow_ColdArrow`；`DmgCalc1=dl12` 以
`dParam1 + (level - 1) * dParam2` 求转换百分比，Magic Arrow 的参数为 `1/1`，即等级
1–20 分别转换 1%–20%。回调从 `dwPhysDamage` 减去同一份定点数并加到导弹的 magic
元素通道，因此只重新分配通道，不改变总定点伤害。

riiablo `MissileDamageResolver.initializeSkill` 同样组合技能曲线与武器源包，并用
`damageConversionPercent` 执行 `dl12` 转换。当前运行时伤害通道以整数保存：等级 1–20
的物理通道为 `1,2,3,4,5,6,7,8,9,9,10,11,12,13,13,14,15,15,16,16`，魔法通道为
`0,0,0,0,0,0,0,0,0,1,1,1,1,1,2,2,2,3,3,4`，两者之和仍严格为 1–20。
`MagicArrowGoldenDamageTest` 同时锁定原版表字段、20 级定点总量守恒和生产通道之和。

本批只批准抗性前总整数范围，不能把 `delta=0` 解释为分通道小数完全一致。D2MOO 在
物理转魔法时仍保留 8.8 小数，而 riiablo 的整数通道会延后不足 1 点的转换；固定武器包
及该精度差异归入 DMG-06，物理/魔法抗性、穿透、吸收和 PvP 对分通道小数的影响归入
DMG-08。

## DMG-04 第二十个逐级实例：Fire Arrow

Fire Arrow 的基础黄金单位是“一枚 `firearrow` 对单个目标的一次抗性前火焰命中”，场景
固定为无装备、owner 武器最小/最大伤害均为 0，并且 Exploding Arrow 硬点为 0。1.10f
`Skills.txt#7` 为 `SrcDam=128`、`HitShift=8`、`EType=fire`、`EMin=1`、`EMax=4`；
最小值五段增量为 `2/3/6/12/24`，最大值为 `2/3/7/14/27`。协同公式为
`(skill('Exploding Arrow'.blvl)) * par8`，本场景的协同输入为 0。按
`SKILLS_GetMinElemDamage` 与 `SKILLS_GetMaxElemDamage` 的 8.8 定点曲线右移后，等级
1–20 最小值为 `1,3,5,7,9,11,13,15,18,21,24,27,30,33,36,39,45,51,57,63`，
最大值为 `4,6,8,10,12,14,16,18,21,24,27,30,33,36,39,42,49,56,63,70`。

`MISSILE_CalculateDamageData` 先把技能火焰曲线与 `SrcDam=128` 的武器源包组合。命中时，
`firearrow` 的 `SrvDmgFunc=1` 进入
`MISSMODE_SrvDmg01_FireArrow_MagicArrow_ColdArrow`；其 `DmgCalc1=dl12`，参数为
`dParam1=3`、`dParam2=2`，所以等级 1–20 会把 `3%+(level-1)*2%` 的物理伤害转入
火焰通道。当前基础场景的武器物理包为 0，转换输入也为 0，因此不会改变技能自身的
火焰范围。固定武器包下的转换值和定点精度属于 DMG-06。

riiablo `MissileDamageResolver.initializeSkill` 从技能 7 的元素曲线建立同一份火焰快照；
`FireArrowGoldenDamageTest` 锁定 1.10f 字段、`SrvDmgFunc=1`、`dl12` 参数、20 级独立
常量、零物理通道和生产快照。等级 1 为 1–4，等级 20 为 63–70，全部 20 个等级的
`delta_min/max` 均为 0。抗性、穿透、吸收和 PvP 最终结算不进入本批，保留在 DMG-08。

## DMG-04 第二十一个逐级实例：Inner Sight

Inner Sight 不拥有直接伤害。1.10f `Skills.txt#8` 的 `SrcDam=0`，没有 server missile，
物理伤害列均为 0，`SrvDoFunc=6`。表中的 `EMin=40` 与五段 `EMinLev=25/45/60/80/100`
不是元素伤害包，而是 `AuraStatCalc=-edmn` 借用的分段输入；因此本技能等级 1–20 的
`expected_*`、`riiablo_actual_*` 和 `delta_*` 全部明确为 N/A 并保持空白，不能填写 0。

D2MOO `SKILLS_SrvDo006_InnerSight_SlowMissiles` 计算 `AuraLenCalc`、`AuraRangeCalc` 与
`AuraStatCalc`，然后按原生 aura filter 扫描目标。`SKILLS_AuraCallback_InnerSight_SlowMissiles`
只建立 curse/stat list，把计算值写入 `armorclass` 并安装 Inner Sight 目标状态；该路径
不分配 combat damage record，也不创建导弹。`-edmn` 的等级 1–20 结果为
`-40,-65,-90,-115,-140,-165,-190,-215,-260,-305,-350,-395,-440,-485,-530,-575,`
`-635,-695,-755,-815`。

审计复现了 riiablo 已登记的差异：原 `calculateInnerSightDefenseReduce` 使用
`40+(level-1)*20`，等级 2 错为 `-60`。`SkillFormula` 现在支持 SkillCalc 未移位的
`edmn/edmx` token，生产 `applyInnerSight` 使用原生 `-edmn` 分段曲线；
`InnerSightGoldenDamageTest` 以独立常量锁定全部 20 级，集成测试确认等级 2 状态值为
`-65` 且仍是平面防御修正。持续时间、范围、目标过滤和最终命中率影响不属于 DMG-04
伤害黄金值，留给后续行为与结算审计。
