# D2MOO 1.10f 技能伤害通用公式证据

更新时间：2026-10-09

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

## DMG-04 第二十二个逐级实例：Critical Strike

Critical Strike 不拥有直接伤害。1.10f `Skills.txt#9` 的 `SrcDam=0`、`SrvDoFunc=0`，没有
server missile，所有物理和元素伤害字段均为 0。该行声明
`PassiveState=criticalstrike`、`PassiveStat=passive_critical_strike`、
`PassiveCalc=dm12`，并使用 `Param1=5`、`Param2=80`。因此等级 1–20 的
`expected_*`、`riiablo_actual_*` 和 `delta_*` 全部明确为 N/A 并保持空白，不能把 0
写成技能伤害。

`dm12` 按 `a + 110 * level * (b - a) / (100 * (level + 6))` 计算整数概率。代入 5 和 80
后，等级 1–20 为
`16,25,32,38,42,46,49,52,54,56,58,60,61,62,63,65,65,66,67,68`。
D2MOO `SKILLS_RefreshPassiveSkills` 在技能等级变化时调用 `SKILLS_EvaluateSkillFormula`，
随后把结果写入技能声明的 PassiveStat 状态表；该阶段不会建立伤害记录。

D2MOO `SUnitDmg.cpp` 的物理伤害路径读取 `STAT_PASSIVE_CRITICAL_STRIKE`，随机成功时设置
critical flag 并把既有 `dwPhysDamage` 乘 2；失败后才检查 Deadly Strike。导弹武器包的
`MISSILE_HasBonusStats` 同样先读取 Critical Strike，再检查 Deadly Strike 和武器专精。
这些消费者证明技能只拥有概率状态，不拥有独立伤害包；随机边界、翻倍物理值和三类暴击
来源的优先级属于 DMG-06/08，而不是本批 N/A 黄金值。

riiablo `AmazonSkills.getCriticalStrikeChance` 和 `applyPassiveState` 已从同一条 `dm12`
公式建立永久状态，`StateUpdater` 负责刷新，`CombatSystem` 与导弹快照路径消费
`passive_critical_strike`。`CriticalStrikeGoldenDamageTest` 用独立常量锁定全部 20 级、
原版表字段、无伤害列和每级状态贡献；现有战斗测试另锁定成功暴击只翻倍物理伤害。

## DMG-04 第二十三个逐级实例：Jab

Jab 的技能行不拥有固定伤害包。1.10f `Skills.txt#10` 使用 `SrvStFunc=5`、
`SrvDoFunc=7`、`SrcDam=128`、`HitShift=8`、`ToHit=10`、`LevToHit=9` 和
`Calc1=ln34`；`Param3=-15`、`Param4=3`。该行没有 server missile，物理和元素技能伤害
字段均为 0，`EType` 为空。因此 DMG-04 的等级 1–20 行把三组
`expected_*`、`riiablo_actual_*` 和 `delta_*` 明确标记为 N/A 并保持空白；固定武器包、
每段实际值和三段施法总量属于 DMG-06。

`ln34` 产生等级 1–20 的武器物理加成：
`-15,-12,-9,-6,-3,0,3,6,9,12,15,18,21,24,27,30,33,36,39,42`。
等级 6 的 `0%` 是有效公式值，不表示公式缺失。

D2MOO `SKILLS_SrvSt05_Jab`（`SkillAma.cpp:69–76`）只验证目标仍然有效。
`SKILLS_SrvDo007_Jab`（`SkillAma.cpp:427–472`）在每个 SQ keyframe 独立计算 ToHit 和
命中判定；成功后直接把 `Calc1` 结果写入本次武器包，再附加技能元素包。`SrcDam=0` 时
才 fallback 到 128，而 Jab 已显式声明 128。函数随后为这一个 keyframe 分配一份 combat
record 并执行一次耐久损耗。三次动画关键帧因此拥有三份独立记录，不能把总量预先折叠成
一个技能伤害值。

riiablo 原 `getPhysicalDamagePercent` 把公式求值为 0 当作“公式缺失”，使等级 6 错误进入
旧手写 fallback 并得到 `+48%`。生产现按公式字段是否为空决定 fallback，保留负值和合法
零值。`JabGoldenDamageTest` 用独立常量锁定 20 级曲线及无固定伤害字段；
`AmazonMeleeSkillLifecycleTest` 与 `NativeJabSequenceTest` 分别锁定三次关键帧消费和原生
动画序列。

## DMG-04 第二十四个逐级实例：Cold Arrow

1.10f `Skills.txt#11` 使用 `SrvStFunc=4`、`SrvDoFunc=0`、`SrvMissile=coldarrow`、
`SrcDam=128`、`HitShift=7`、`ToHit=10` 和 `LevToHit=9`。冷伤基值为 `6–8`，五段每级
增量分别为最小值 `4/5/8/16/42`、最大值 `4/5/9/17/44`；
`EDmgSymPerCalc=(skill('Ice Arrow'.blvl))*par8`。本批把 Ice Arrow 硬点固定为 0。

D2MOO `D2GAME_SKILLS_Handler_6FD12BA0` 对 `SrvDoFunc=0` 的技能创建 `coldarrow`，
`MISSILE_CalculateDamageData` 读取 `SKILLS_GetMinElemDamage`、`SKILLS_GetMaxElemDamage` 和
`SKILLS_GetElementalLength`，同时按 `SrcDam=128` 继承完整武器包。元素 getter 先计算
五段源表曲线，再左移 `HitShift=7` 形成 8.8 定点值，因此等级 1 的 `6–8` 对应整数
`3–4`，等级 20 的 `106–112` 对应整数 `53–56`。等级 1–20 的抗性前单目标冷伤范围为：

`3–4,5–6,7–8,9–10,11–12,13–14,15–16,17–18,19–20,22–23,`
`24–25,27–28,29–30,32–33,34–35,37–38,41–42,45–47,49–51,53–56`。

`coldarrow` 的 `SrvDmgFunc=1` 进入
`MISSMODE_SrvDmg01_FireArrow_MagicArrow_ColdArrow`。其 `DmgCalc1=dl12`、
`dParam1=3`、`dParam2=2`，把等级 1–20 的 3%–41% 物理通道移入冷通道，总 8.8 伤害
不变。本批 owner 武器伤害为 0，因此该转换不改变上面的技能冷伤；固定武器包与分通道
精度留给 DMG-06。

原生 chill length 由 `ELen=100`、`ELevLen=30/30/30` 产生 100–670 帧。Cold Arrow
使用 cold 而非 freeze 路径；持续时间、减速状态、抗性缩短及死亡碎裂语义属于 DMG-07/08。
`ColdArrowGoldenDamageTest` 用独立常量锁定全部 20 级冷伤与长度，并逐级对比
`MissileDamageResolver.initializeSkill` 的生产快照；当前无需生产修复。

## DMG-04 第二十五个逐级实例：Multiple Shot

Multiple Shot 的技能行不拥有固定技能伤害包。1.10f `Skills.txt#12` 使用
`SrvStFunc=4`、`SrvDoFunc=8`、`SrcDam=96`、`HitShift=8`，`ToHit/LevToHit` 均为 0；
`SrvMissileA=multipleshotarrow`、`SrvMissileB=multipleshotbolt`。物理和元素技能伤害字段
均为 0，`EType` 为空。因此 DMG-04 的等级 1–20 行把三组 `expected_*`、
`riiablo_actual_*` 和 `delta_*` 明确标记为 N/A 并保持空白；固定武器包、每枚实际命中值
和整次施法总量属于 DMG-06。

三个公式各自拥有不同语义：`Calc1="min(24,ln12)"`，`Param1=2`、`Param2=1`，产生
等级 1–20 的 lane 总数 `2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21`；
`Calc2=par3` 且 `Param3=1`，设置导弹激活帧；`Calc3=2` 固定中央组为两条 lane。现有任务
清单曾把 `Calc2` 误写成物理加成，本次按真实 1.10f 数据和 D2MOO 调用位置纠正。

D2MOO `SKILLS_SrvDo008_MultipleShot_Teeth_ShockWave`（`SkillAma.cpp:476–567`）先求值
`Calc1`，根据武器类别选择 A/B 导弹，再把 `Calc2` 写入 `nActivateFrame`。随后按
`Calc3` 把 fan 分成左侧、中央和右侧三组，对每条 lane 独立调用
`MISSILES_CreateMissileFromParams`；外侧组和中央组的创建标志不同，具体命中/触发消费语义
留给 DMG-06。通用创建链为每枚导弹调用 `MISSILE_CalculateDamageData`，因此 `SrcDam=96`
表示每枚导弹各自继承 96/128 武器包，不是把一次施法的武器伤害先乘以箭数。

riiablo `ServerSkillSystem.spawnMultipleShotTeethShockWave` 按同一 `Calc1/Calc3` 拆分 lane，
每次 `createMissile` 后都调用 `initializeSkillDamage`，由 `MissileDamageResolver` 使用
`SrcDam=96` 建立独立武器快照。`MultipleShotGoldenDamageTest` 用独立常量锁定全部 20 级
lane 数、1 帧激活值、两条中央 lane 及无固定伤害字段；现有 SrvDo008 几何和墙体碰撞测试
覆盖生产 fan 路径。本轮无需生产修复。

## DMG-04 第二十六个逐级实例：Dodge

Dodge 不拥有输出伤害包。1.10f `Skills.txt#13` 使用 `SrvDoFunc=0`、`SrcDam=0`，没有
server missile，物理和元素伤害字段均为 0，`EType` 为空。该行声明
`PassiveState=dodge`、`PassiveStat=passive_dodge`、`PassiveCalc=dm12`，并使用
`Param1=10`、`Param2=65`。因此 DMG-04 的等级 1–20 行把三组 `expected_*`、
`riiablo_actual_*` 和 `delta_*` 明确标记为 N/A 并保持空白；概率不是输出伤害值。

`dm12` 按 `a + 110 * level * (b - a) / (100 * (level + 6))` 计算整数概率。代入 10 和 65
后，等级 1–20 为
`18,25,30,34,37,40,42,44,46,47,49,50,51,52,53,54,54,55,55,56`。
D2MOO `D2Common/src/D2Skills.cpp` 的被动技能刷新先调用 `SKILLS_EvaluateSkillFormula`，再把
结果写入技能声明的 PassiveStat，记录技能 ID/等级并启用永久状态；该阶段不创建 damage
record 或 missile。

D2MOO `SUnitDmg.cpp` 的 `SUNITDMG_GetResultFlags` 在近战命中成功后调用
`SUNITDMG_ApplyBlockOrDodge(..., bAvoid=0, bBlock=1)`。盾牌格挡失败后，
`SUNITDMG_ApplyDodge` 在非移动分支读取 `STAT_PASSIVE_DODGE`，以 `% 100` 的随机值判定；
成功时返回 `BLOCKFLAG_DODGE` 并清除 successful-hit。随后 `SUNITDMG_AllocCombat` 只有在
没有 Dodge/Avoid/Evade/Weapon Block 且仍是成功命中时，才填充并汇总伤害。因此 Dodge
拥有“近战伤害预防概率”，不拥有一份独立的输出伤害。

riiablo `AmazonSkills.getDodgeChance` 与 `applyPassiveState` 使用同一 `dm12` 数据行建立永久
状态，`StateUpdater` 负责刷新，`CombatSystem` 在伤害结算前通过
`DefenseCalculator.checkPassiveDefense` 消费 `passive_dodge`。
`DodgeGoldenDamageTest` 用独立常量锁定全部 20 级、原版表字段、无伤害列和每级状态贡献；
`AmazonSkillSpecializationTest.passiveDodgeAvoidEvadeUseNativeAttackContext` 另锁定 Dodge
只处理站立近战上下文。随机边界、格挡优先级和最终来袭伤害结算属于 DMG-08。

## DMG-04 第二十七个逐级实例：Power Strike

1.10f `Skills.txt#14` 使用 `SrvStFunc=6`、`SrvDoFunc=2`、`SrcDam=128`、`HitShift=8`、
`ToHit=20` 和 `LevToHit=12`。技能没有 server missile；固定物理伤害字段为 0，元素类型为
`ltng`。闪电最小值为 1 且五段增量全为 0；最大值基值为 16，五段增量为
`18/36/54/72/90`。`EDmgSymPerCalc` 读取 Lightning Strike、Lightning Bolt、
Charged Strike 和 Lightning Fury 的硬点并乘 `par8`，本批把四项硬点固定为 0。

D2MOO `SKILLS_SrvSt06_PowerStrike_ChargedStrike`（`SkillAma.cpp:80–114`）先调用
`SUNITDMG_GetResultFlags`。只有成功命中才求值 `Calc1` 写入 enhanced-damage percentage，
再调用 `D2GAME_RollElementalDamage_6FD14DD0` 把技能闪电值写入 damage 结构；随后
`SUNITDMG_AllocCombat` 按 `SrcDam=128` 保存这一份近战记录。`SKILLS_SrvDo002` 在动画关键帧
消费同一记录并处理武器耐久，不会重新掷一次技能闪电或创建第二份伤害包。

通用元素 getter 先按五段曲线和 `HitShift=8` 建立 8.8 定点值，再应用硬点协同。本批零协同
下，等级 1–20 的 canonical 闪电范围为：

`1–16,1–34,1–52,1–70,1–88,1–106,1–124,1–142,1–178,1–214,`
`1–250,1–286,1–322,1–358,1–394,1–430,1–484,1–538,1–592,1–646`。

`D2GAME_RollElementalDamage_6FD14DD0` 把 `max-min` 传给 limited RNG，因此运行时随机上界
排除 canonical getter 最大值；矩阵沿用其他直接元素技能的口径保存 getter 范围。固定武器
物理包、`Calc1` 增强物理、随机样本和整份近战记录总量属于 DMG-06；抗性、吸收、PvP 与
最终生命扣减属于 DMG-08。

riiablo `Actioneer.prepareAmazonElementalStrike` 使用
`MissileDamageResolver.skillElementalDamage` 逐级建立同一闪电范围，再把它交给预计算近战
记录；关键帧只消费一次。`PowerStrikeGoldenDamageTest` 用独立常量锁定全部 20 级、原版
表字段、零协同和生产 resolver 输出，`AmazonMeleeSkillLifecycleTest` 锁定单记录消费与成功
命中后才扣耐久。本轮无需生产修复。

## DMG-04 第二十八个逐级实例：Poison Javelin

1.10f `Skills.txt#15` 使用 `SrvStFunc=4`、`SrvDoFunc=0`、`SrvMissile=poisonjav`、
`SrcDam=128`、`HitShift=0`，元素类型为 `pois`。毒素基值为 `32–48`，五段等级增量为
`16/32/48/64/96` 和 `16/36/52/68/84`；长度基值为 200 帧，`ELevLen=50|50|50`。
`EDmgSymPerCalc` 只读取 Plague Javelin 硬点，本批固定为 0。

D2MOO `MISSMODE_SrvDo02_PlagueJavelin_PoisonJavelin_PoisonTrap` 在根标枪移动期间按
`SrvCalc` 创建 `poisonjavcloud` 子导弹；`MISSMODE_SrvDo03_PoisonCloud_Blizzard_ThunderStorm_HandOfGod`
再把子导弹交给通用碰撞路径。子导弹自身 `pSrvDoFunc=3`，因此每个原生游戏帧执行碰撞，
而不是把 `DamageRate=0` 当作 tick 间隔。毒云的根标枪武器命中不并入本批总量。

由于 `HitShift=0`，D2Common 的元素 getter 结果就是原生 8.8 每帧固定速率。等级 1–20
的最小/最大速率固定值分别为 `32..592` 与 `48..656`；持续时间为 `200..1150` 帧。
单个 `poisonjavcloud` 对单个目标完整持续期的整数总伤害按
`floor(rateFixed * durationFrames / 256)`，范围从等级 1 的 `25–37` 到等级 20 的
`2659–2946`。`PoisonJavelinGoldenDamageTest` 同时锁定 Skills/Missiles 字段、速率、
持续时间、生产 `fixedPoisonRate` 快照和独立总量常量。

毒云数量、轨迹覆盖、重叠目标、重复施法刷新/覆盖和根标枪武器包属于 DMG-06/07；毒抗、
毒抗穿透、毒长减免、PvP 与最终生命结算属于 DMG-08。本批矩阵的
`expected_min/max` 与 `riiablo_actual_min/max` 记录完整持续期整数范围，三个 `*_total`
字段保持空白，因为这里仍是一个范围而非单一总数。

## DMG-04 第二十九个逐级实例：Exploding Arrow

1.10f `Skills.txt#16` 使用 `SrvStFunc=4`、`SrvDoFunc=0`、`SrvMissile=explodingarrow`、
`SrcDam=128`、`HitShift=8`，元素类型为 `fire`。火焰基值为 `2–6`，五段等级增量为
`5/7/9/12/20` 和 `5/8/11/14/23`；`EDmgSymPerCalc` 读取 Fire Arrow 硬点并乘
`par8`，本批固定协同为 0。

D2MOO `MISSMODE_SrvHit04_ExplodingArrow_FreezingArrow_RoyalStrikeMeteorCenter` 在根箭命中时
创建 `HitSubMissile[0]=explodingarrowexp2`，并把原技能 ID、等级和 owner 传给子导弹。
`explodingarrowexp2` 使用
`MISSMODE_SrvHit01_Fireball_ExplodingArrow_FreezingArrowExplosion`，从子导弹快照取得技能
火焰包，再对半径内目标分发。根 `explodingarrow` 的 `SrcDam=128` 武器命中与子导弹技能
火焰包是两个所有者，不能合并成一个 DMG-04 数值。

零装备、零 Fire Arrow 硬点时，单个爆炸子导弹对单个目标的一次抗性前火焰范围从等级 1
的 `2–6` 增长到等级 20 的 `129–149`。riiablo 根导弹初始化会因 `pSrvHitFunc=4` 排除技能
元素包，`MissileDamageResolver.initializeSkillArea` 再为 `explodingarrowexp2` 建立纯火焰
快照；`ExplodingArrowGoldenDamageTest` 用独立常量逐级锁定两段所有权和生产输出。

固定武器命中留给 DMG-06；爆炸半径、目标数、多目标累计和整次施法总量留给 DMG-07；
Fire Mastery、抗性、穿透、吸收、PvP 和最终生命扣减留给 DMG-08。范围型单目标伤害的
三个 `*_total` 字段保持空白。

## DMG-04 第三十个逐级实例：Slow Missiles

Slow Missiles 不拥有输出伤害包。1.10f `Skills.txt#17` 使用 `SrvStFunc=0`、
`SrvDoFunc=6`、`SrcDam=0`，没有 server missile，物理和元素伤害字段均为 0，`EType`
为空。因此 DMG-04 的等级 1–20 行把三组 `expected_*`、`riiablo_actual_*` 和
`delta_*` 明确标记为 N/A 并保持空白；投射物速度百分比不是伤害值。

原生行声明 `AuraTargetState=slowmissiles`、`AuraStat1=skill_handofathena` 和
`AuraStatCalc1=ln12`。`Param1=33`、`Param2=0` 使等级 1–20 的状态属性均为 33；该值
表示投射物保留正常速度的 33%，不是只减少 33%。`AuraLenCalc=ln34` 配合
`Param3=300`、`Param4=150`，产生 300、450、…、3150 帧的持续时间；
`AuraRangeCalc=ln56` 配合 `Param5=20`、`Param6=0`，使范围在全部等级固定为 20。

D2MOO `SKILLS_SrvDo006_InnerSight_SlowMissiles`（`SkillAma.cpp:384–403`）直接求值上述
持续时间、范围和状态属性，再由
`SKILLS_AuraCallback_InnerSight_SlowMissiles`（`SkillAma.cpp:406–425`）调用通用诅咒
状态安装函数；该链不分配 combat damage record，也不创建 missile。D2MOO
`Missiles.cpp:134–143` 在怪物新建、且 Missiles.txt 声明 `CanSlow` 的投射物时检查
`STATE_SLOWMISSILES`，读取 `STAT_SKILL_HANDOFATHENA` 并把初始速度乘以该百分比。

riiablo `ServerSkillSystem.applySlowMissiles` 求值同一组原生公式并安装
`SLOWMISSILES/skill_handofathena` 状态；`ServerEntityFactory.slowMissileVelocityPercent`
在新建可减速怪物投射物时消费该状态。`SlowMissilesGoldenDamageTest` 用独立常量锁定
全部 20 级速度百分比、持续时间、固定范围和无伤害字段，现有
`AmazonSkillSpecializationTest.slowMissilesAppliesNativeStateAndVelocityStatInsteadOfInnerSight`
锁定实际状态安装。目标过滤、状态刷新/覆盖及飞行轨迹属于 DMG-07，最终来袭伤害结算
属于 DMG-08。

## DMG-04 第三十一个逐级实例：Avoid

Avoid 不拥有输出伤害包。1.10f `Skills.txt#18` 使用 `SrvDoFunc=0`、`SrcDam=0`，没有
server missile，物理和元素伤害字段均为 0，`EType` 为空。因此 DMG-04 的等级 1–20 行
把三组 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确标记为 N/A 并保持空白；
规避概率不是输出伤害值。

原生行声明 `PassiveState=avoid`、`PassiveStat1=passive_avoid`、
`PassiveCalc1=dm12`，并使用 `Param1=15`、`Param2=75`。`dm12` 按
`a + 110 * level * (b - a) / (100 * (level + 6))` 进行整数计算，等级 1–20 的概率为：

`24,31,37,41,45,48,50,52,54,56,57,59,60,61,62,63,63,64,65,65`。

D2MOO `SKILLS_RefreshSkill`（`D2Skills.cpp:512–600`）求值 PassiveCalc，并把结果写入
技能声明的 PassiveStat 永久状态。导弹碰撞路径在 `MissMode.cpp:4707` 以 `bAvoid=1`
调用 `SUNITDMG_ApplyBlockOrDodge`；物理导弹允许盾牌格挡先判定，失败后进入通用被动防御。
`SUNITDMG_ApplyDodge`（`SUnitDmg.cpp:2719–2772`）对移动单位只读取
`STAT_PASSIVE_EVADE`，对静止单位先检查 Weapon Block，再读取 `STAT_PASSIVE_AVOID`，以
`random % 100 < chance` 判定。成功时返回 `BLOCKFLAG_AVOID`；导弹路径设置 Avoid 标志并
清除 successful-hit，因此不会继续执行该命中伤害。

riiablo `AmazonSkills.getAvoidChance` 和 `applyPassiveState` 使用同一 Skills.txt 行，
`StateUpdater` 安装永久 `avoid/passive_avoid` 状态，`CombatSystem` 把导弹标记为 ranged，
并由 `DefenseCalculator.checkPassiveDefense` 按移动、Weapon Block、Avoid 的原生上下文顺序
消费概率。`AvoidGoldenDamageTest` 用独立常量锁定全部 20 级、原版表字段、无伤害列和
每级状态贡献；现有 `AmazonSkillSpecializationTest.passiveDodgeAvoidEvadeUseNativeAttackContext`
锁定静止远程攻击上下文。随机样本、盾牌/Weapon Block/Evade 优先级、动画与最终来袭伤害
结算属于 DMG-08。

## DMG-04 第三十二个逐级实例：Impale

Impale 不拥有脱离武器的固定伤害包。1.10f `Skills.txt#19` 使用 `SrvStFunc=7`、
`SrvDoFunc=2`、`SrcDam=128`、`HitShift=8`，没有 server missile，物理和元素伤害曲线均为
0，`EType` 为空。因此 DMG-04 的等级 1–20 行把三组 `expected_*`、
`riiablo_actual_*` 和 `delta_*` 明确标记为 N/A 并保持空白；武器基础值和最终范围属于
DMG-06。

原生行声明 `Calc1=ln12`、`Param1=300`、`Param2=25`，等级 1–20 的武器伤害百分比为：

`300,325,350,375,400,425,450,475,500,525,550,575,600,625,650,675,700,725,750,775`。

额外物品损耗使用 `Calc2=par6-dm34`，其中 `Param3=0`、`Param4=30`、`Param6=50`。
`dm34` 按 `a + 110 * level * (b - a) / (100 * (level + 6))` 进行整数计算，再从 Param6
减去结果，等级 1–20 的概率为：

`46,42,39,37,35,34,33,32,31,30,29,28,28,27,27,26,26,26,25,25`。

`Calc3=par5`、`Param5=1`，所以非堆叠武器每次触发固定损失 1 点耐久；堆叠武器触发时
改为扣除 1 个数量。

D2MOO `SKILLS_SrvSt07_Impale`（`SkillAma.cpp:117–189`）先用 `ToHit=100`、
`LevToHit=25` 求一次命中。成功后，它把 Calc1 交给
`SUNITDMG_ApplyDamageBonuses`（`SUnitDmg.cpp:147–230`），读取当前武器、属性与
`SrcDam` 构造一次物理伤害记录并设置预填充标志，避免通用路径重复填充。相同成功命中
分支随后求值 Calc2/Calc3：堆叠武器扣数量，非堆叠耐久武器扣 Calc3。记录由
`SKILLS_SrvDo002_Kick_PowerStrike_MonIceSpear_Impale_Bash_Stun_Concentrate_BearSmite_Vengeance_Berserk_FireClaws`
（`Skills.cpp:2094–2204`）在关键帧消费；该通用消费者另有普通武器耐久路径。

riiablo `AmazonSkills.getPhysicalDamagePercent` 直接求值 Calc1，`calculateWeaponDamage`
把结果合入武器包；`Actioneer.prepareImpale` 预计算一次记录，`resolveImpale` 幂等消费，
`drainImpaleDurability` 按 Calc2/Calc3 选择数量或耐久路径。
`ImpaleGoldenDamageTest` 用独立常量锁定全部 20 级百分比、资源概率、固定损耗量、原版表
字段和无固定伤害字段；现有 `AmazonMeleeSkillLifecycleTest` 锁定一次性记录、命中门槛及
两种资源路径。固定武器数值、SrcDam、实际命中、普通耐久和最终伤害范围属于 DMG-06；
抗性、格挡、PvP 与生命结算属于 DMG-08。

## DMG-04 第三十三个逐级实例：Lightning Bolt

1.10f `Skills.txt#20` 使用 `SrvStFunc=4`、`SrvDoFunc=0`、
`SrvMissile=lightningjavelin`、`SrcDam=96`、`HitShift=8`，元素类型为 `ltng`。闪电基值为
`1–40`，最小值五段等级增量全为 0，最大值五段等级增量为 `12/18/28/48/88`；
`EDmgSymPerCalc` 读取 Lightning Strike、Power Strike、Charged Strike、Lightning Fury
硬点并乘 `par8`，本批固定协同为 0。

D2MOO `D2GAME_SKILLS_Handler_6FD12BA0`（`Skills.cpp:2445`）创建技能声明的
`lightningjavelin`。导弹初始化把技能闪电曲线与 96/128 武器包写入同一快照；
`MISSMODE_SrvDmg12_LightningJavelin`（`MissMode.cpp:4342`）在命中时求值 Missiles.txt
`DmgCalc=dl12`，把结果限制到 100%，从当前物理包取对应份额并与已有元素伤害相加，随后
按导弹元素类型重写命中记录。该转换不改变技能自带闪电曲线的所有权。

零装备、零四项硬点协同时，单枚标枪对单个目标的一次抗性前技能闪电 getter 范围从等级 1
的 `1–40` 增长到等级 20 的 `1–380`。riiablo
`MissileDamageResolver.initializeSkill` 使用同一五段曲线；owner 武器伤害为 0 时，
`SrcDam=96` 和 `SrvDmg12` 转换均不产生额外值，物理通道保持 0，20 个等级的
`delta_min/max` 均为 0。`LightningBoltGoldenDamageTest` 用独立常量逐级锁定表字段、
导弹行和生产快照。

D2MOO `MISSMODE_RollDamageValue`（`MissMode.cpp:281`）向有限随机数传入 `max-min`，所以
原生运行时上限排除 canonical getter 最大值；矩阵与其他技能一致保存 getter 范围。
固定武器包、`dl12` 转换和完整命中记录留给 DMG-06；抗性、免疫、吸收、PvP 与最终结算
留给 DMG-08。范围型单目标伤害的三个 `*_total` 字段保持空白。

## DMG-04 第三十四个逐级实例：Ice Arrow

1.10f `Skills.txt#21` 使用 `SrvStFunc=4`、`SrvDoFunc=0`、`SrvMissile=icearrow`、
`SrcDam=128`、`HitShift=8`、`ToHit=20`、`LevToHit=9`，元素类型为 `cold`。冷伤基值为
`6–10`，五段等级增量为 `6/12/18/26/36` 和 `6/13/19/27/38`；
`EDmgSymPerCalc=(skill('Cold Arrow'.blvl))*par8`，本批把 Cold Arrow 硬点固定为 0。

D2MOO `SKILLS_SrvSt04_Arrow_Bolt`（`SkillAma.cpp:56`）完成弓弩起手检查，随后
`D2GAME_SKILLS_Handler_6FD12BA0`（`Skills.cpp:2445`）创建 `icearrow`。通用导弹初始化把
技能冷伤、50 帧基础 cold length、每级 5 帧增量和完整 `SrcDam=128` 武器包写入同一快照。
命中时 `MISSMODE_SrvDmg02_IceArrow_RoyalStrikeChaos`（`MissMode.cpp:4370`）读取
Missiles.txt `dParam1=100`，把 `coldlength * 100 / 100` 写入 freeze length，并把 cold
length 清零；它不修改物理或冷伤数值。

零装备、零 Cold Arrow 硬点协同时，单枚箭对单个目标的一次抗性前技能冷伤 getter 范围
从等级 1 的 `6–10` 增长到等级 20 的 `216–232`。riiablo
`MissileDamageResolver.initializeSkill` 逐级输出相同范围，物理通道保持 0；50–145 帧的
预转换 cold length 以 `freezesTarget` 和 `FREEZE` 元数据进入碰撞状态链。20 个等级的
`delta_min/max` 均为 0。`IceArrowGoldenDamageTest` 用独立常量锁定表字段、导弹行、伤害、
长度和状态元数据。

D2MOO `MISSMODE_RollDamageValue`（`MissMode.cpp:281`）向有限随机数传入 `max-min`，所以
原生运行时上限排除 canonical getter 最大值；矩阵仍保存 getter 范围。固定武器包、
ToHit 和整份命中记录留给 DMG-06；冻结状态、Boss/Unique/Hireling 回退、抗性缩时、
碎冰死亡和最终结算留给 DMG-07/08。范围型单目标伤害的三个 `*_total` 字段保持空白。

## DMG-04 第三十五个逐级实例：Guided Arrow

1.10f `Skills.txt#22` 使用 `SrvStFunc=4`、`SrvDoFunc=10`、三个 server missile 字段均为
`guidedarrow`、`SrcDam=128`、`HitShift=8`，`ToHit` 和 `LevToHit` 均为 0。技能物理与
元素伤害基值和五段增量全部为 0；`Calc1=ln34` 配合 `Param3=0`、`Param4=5`，产生
等级 1–20 的 `0/5/10/.../95%` 武器伤害增强。

D2MOO `SKILLS_SrvDo010_GuidedArrow_BoneSpirit`（`SkillAma.cpp:570`）选择弓弩导弹并创建
一枚带目标的 `guidedarrow`。它只在 `Calc1` 结果非零时安装
`SKILLS_AddDamagePercentBonus`（`SkillAma.cpp:653`），后者把该值加到导弹
`STAT_DAMAGEPERCENT`；因此等级 1 的合法 0 不得触发替代公式。`MISSMODE_SrvDo07`
（`MissMode.cpp:882`）负责追踪转向，`MISSMODE_SrvHit10`（`MissMode.cpp:2431`）限制锁定
目标命中，这两条路径不创建额外固定伤害包。

零装备、owner 武器伤害为 0 时，等级 1–20 均没有可记录的固定技能伤害；矩阵把
`expected_min/max/total`、`riiablo_actual_*` 和 `delta_*` 保持空白，并用明确 N/A 口径
批准。`GuidedArrowGoldenDamageTest` 锁定 1.10f 表字段、`0%–95%` 曲线以及零武器源
不会生成伤害快照；生产创建回归另确认等级 1 倍率为 1.00，修复了旧的 `+5%` fallback。
固定武器值、`SrcDam` 缩放、箭袋和完整命中总量留给 DMG-06；抗性与最终结算留给
DMG-08。

## DMG-04 第三十六个逐级实例：Penetrate

1.10f `Skills.txt#23` 是纯被动技能：`SrvStFunc=0`、`SrvDoFunc=0`、物理和元素伤害
字段均为 0，`PassiveState=penetrate`、`PassiveStat=item_tohit_percent`、
`PassiveCalc=ln12`，`Param1=35`、`Param2=10`。因此等级 1–20 的命中率状态加成是
`35,45,55,65,75,85,95,105,115,125,135,145,155,165,175,185,195,205,215,225`；
它不拥有可填入 `expected_min/max/total` 的独立伤害包，矩阵对应字段明确保持 N/A 空白。

D2MOO `SKILLS_RefreshSkill`（`D2Common/src/D2Skills.cpp:603`）读取技能的
`nPassiveStat` 和 `dwPassiveCalc`，并在 `D2Common/src/D2Skills.cpp:617–638` 将公式结果
写入永久被动状态 stat-list。`D2Game/src/UNIT/SUnitDmg.cpp:2439–2511` 的攻击命中率
路径读取 `STAT_ITEM_TOHIT_PERCENT` 并将其加入攻击命中率计算。该状态刷新不创建伤害
记录、导弹或新的伤害值；最终命中率消费留给 DMG-06，抗性和生命结算留给 DMG-08。

`PenetrateGoldenDamageTest` 独立锁定 `Skills.txt` 字段、`ln12` 曲线、`penetrate` 状态和
`item_tohit_percent` 属性，并由 `SkillDamageAuditMatrixTest` 校验 20 行全部为
`GOLDEN_APPROVED`、伤害列保持 N/A。

## DMG-04 第三十七个逐级实例：Charged Strike

1.10f `Skills.txt#24` 使用 `SrvStFunc=6`、`SrvDoFunc=11`、`SrcDam=0`、`HitShift=8`，
元素类型为 `ltng`，技能闪电基值为 `1–30`，五段最大值增量为 `12/16/20/24/28`。
`EDmgSymPerCalc` 读取 Lightning Strike、Lightning Bolt、Power Strike、Lightning Fury
硬点并乘 `par8`；本批四项硬点固定为 0。`Calc1=par1+lvl/par2`，`Param1=3`、`Param2=5`，
产生等级 1–20 每次施法 `3,3,3,3,4,4,4,4,4,5,5,5,5,5,6,6,6,6,6,7` 枚闪电弹。

D2MOO `SKILLS_SrvSt06_PowerStrike_ChargedStrike`（`SkillAma.cpp:80`）先对近战目标
分配一次 `D2DamageStrc`，成功命中时调用 `D2GAME_RollElementalDamage_6FD14DD0`；
`SKILLS_SrvDo011_ChargedStrike`（`SkillAma.cpp:657`）随后消耗耐久，并从命中目标创建
每枚独立的 `chargedstrikebolt`。这些导弹由 `MISSILE_CalculateDamageData`
（`D2Common/src/Units/Missile.cpp:467`）走技能导弹分支，调用
`SKILLS_GetMinElemDamage`/`SKILLS_GetMaxElemDamage`（`D2Skills.cpp:2623/2685`）读取
Charged Strike 的技能闪电曲线；导弹表自身不拥有伤害字段。

因此 DMG-04 矩阵把候选单位固定为“一枚闪电弹对单个目标的抗性前闪电分量”：等级 1–20
从 `1–30` 增长到 `1–322`，`expected_min/max` 与独立生产快照逐级一致，三组 `*_total`
留空以避免把碰撞数量压成一个值。`ChargedStrikeGoldenDamageTest` 锁定 Skills.txt
字段、Calc1 弹数、零协同曲线和 `chargedstrikebolt` 生产快照；近战记录、弹数/碰撞数、
抗性和最终结算留给 DMG-06/07/08。

## DMG-04 第三十八个逐级实例：Plague Javelin

1.10f `Skills.txt#25` 使用 `SrvStFunc=4`、`SrvDoFunc=0`、`SrvMissile=plaguejavelin`、
`SrcDam=128`、`HitShift=3`、`ToHit=30`、`LevToHit=9`，元素类型为 `pois`。毒素基值为
`10–16`，五段等级增量均为 `6/12/20/40/60`；长度基值为 75 帧，`ELevLen=10|10|10`；
`EDmgSymPerCalc=(skill('Poison Javelin'.blvl))*par8`，本批把 Poison Javelin 硬点固定为 0。

D2MOO `D2GAME_SKILLS_Handler_6FD12BA0` 创建根 `plaguejavelin`。根导弹的
`MISSMODE_SrvHit02_PlagueJavelin_PoisonPotion` 读取 `HitSubMissile[0]=plaguejavcloud`
和 `HitPar[0..2]=1,2,3`，调用 `MISSMODE_CreatePoisonCloudHitSubmissiles`：`HitPar[1]=2`
按 16 项原生环偏移发出 8 个主云，`HitPar[0]=1` 再发出 15 个交错云，共 23 个移动子云。
每个子云随后由 `MISSMODE_SrvDo03_PoisonCloud_Blizzard_ThunderStorm_HandOfGod` 每原生帧
进入通用碰撞路径；云的 `DamageRate=0` 不是 tick 间隔。

D2Common `SKILLS_GetMinElemDamage` / `SKILLS_GetMaxElemDamage` 先计算等级曲线，再按
`HitShift=3` 左移得到原生 8.8 fixed/frame 速率。等级 1–20 的最小/最大速率为
`80..1824` 与 `128..1872`，长度为 `75..265` 游戏帧。单个 `plaguejavcloud` 对单个目标
完整持续期的整数总量按 `floor(rateFixed * durationFrames / 256)`，范围从 `23–37` 到
`1888–1937`。`MISSILE_CalculateDamageData` 负责技能导弹分支的快照组合，但根标枪的
`SrcDam=128` 武器命中不属于本批云总量。

`PlagueJavelinGoldenDamageTest` 锁定 Skills/Missiles 字段、23 云的原生 fan-out、8.8
rate、游戏帧持续时间、完整持续期整数范围和零协同生产快照。根武器包、云数量/轨迹/覆盖/
重叠/刷新留给 DMG-06/07；毒抗、毒长减免、Pierce、PvP 和最终生命结算留给 DMG-08。

## DMG-04 第三十九个逐级实例：Strafe

1.10f `Skills.txt#26` 使用 `SrvStFunc=8`、`SrvDoFunc=12`、`SrvMissileA=strafearrow`、
`SrvMissileB=strafebolt`、`SrcDam=96`、`HitShift=8`。`Calc1="min(par3 + lvl - 1, par4)"`
决定一次施法的箭数上限，`Calc3=2+lvl/4` 是最小目标数；两者属于释放/目标调度，
不是伤害乘数。`Calc2=ln12` 使用 `Param1=5`、`Param2=5`，所以等级 1–20 每枚箭分别
携带 `5,10,...,100%` 的技能物理加成。

D2MOO `SKILLS_SrvDo012_Strafe`（`SkillAma.cpp:711`）每个动画关键帧创建一枚
`strafearrow` 或 `strafebolt`，并在导弹初始化前调用
`SKILLS_AddDamagePercentBonus`（`SkillAma.cpp:648`）。随后
`MISSILE_CalculateDamageData`（`D2Common/src/Units/Missile.cpp:467`）读取该导弹的
`STAT_DAMAGEPERCENT`，把 `SrcDam=96` 的独立武器包和 Calc2 加成保留在每枚箭上；
不会把箭数合并成一次施法的单一伤害记录。

因此 DMG-04 矩阵把候选单位固定为“单枚 Strafe 箭对单个目标的技能拥有加成”，
`expected_min/max/total` 明确保持 N/A；武器基础值、SrcDam、ToHit、箭数/目标选择、
Pierce、碰撞和整次施法总量分别留给 DMG-06/07，抗性和最终生命结算留给 DMG-08。
`StrafeGoldenDamageTest` 逐级锁定 Skills/Missiles 字段、Calc2 5%→100% 曲线以及
riiablo 每箭快照中的 `STAT_DAMAGEPERCENT`。

## DMG-04 第四十个逐级实例：Immolation Arrow

1.10f `Skills.txt#27` 使用 `SrvStFunc=4`、`SrvDoFunc=0`、`SrvMissile=immolationarrow`、
`SrcDam=128`、`HitShift=8`，元素类型为 `fire`，技能火焰基值为 `10–20`，五段等级增量为
`10/20/30/32/34`。`EDmgSymPerCalc=(skill('Exploding Arrow'.blvl)) * par8`；本批
Exploding Arrow 硬点固定为 0，因此等级 1–20 的即时技能包为 `10–20` 至 `360–370`。

D2MOO 通用技能处理器先创建 `immolationarrow` 父导弹。其
`MISSMODE_SrvHit09_ImmolationArrow`（`D2Game/src/MISSILES/MissMode.cpp:2377`）在命中时
读取 `HitPar[0]`/`Calc1` 的火场半径和 `HitCalc`/`Calc2` 的持续参数，创建
`immolationfire` 子导弹，然后用 `sub_6FD10200` 在即时范围内分发父箭技能/武器命中记录。
父箭的 `SrcDam=128` 武器包和子导弹的即时技能包是不同所有者；DMG-04 矩阵只批准单个
目标的即时技能火焰包，武器命中、半径内目标集合和整次施法总量留给 DMG-06/07。

`immolationfire` 的 `pSrvDoFunc=5` 由
`MISSMODE_SrvDo05_FireWall_ImmolationFire_MeteorFire_MoltenBoulderFirePath` 每游戏帧进入
碰撞，`pSrvDmgFunc=3` 的
`MISSMODE_SrvDmg03_Blaze_FireWall_ImmolationFire_MeteorFire`（`MissMode.cpp:4387`）只
处理 soft-hit 结果标志。1.10f 导弹行固定 `HitShift=2`、`EMin/EMax=7–9`、每级增量
`5/5/5/5/5`、`DamageRate=41`、`Range=75` 帧，另有
`EDmgSymPerCalc=skill('Fire Arrow'.blvl) * 5`；本批 Fire Arrow 硬点固定为 0。生产路径
`MissileDamageResolver.initializeImmolationFireArea` 直接保存 8.8 fixed-point 速率，专项
测试同时锁定即时父包和周期子包；周期 cadence、覆盖、重叠和完整持续期结算仍属于 DMG-07。

因此矩阵第 422–441 行将候选单位固定为“单个即时范围目标的抗性前技能火焰包”，
`expected_min/max` 与生产快照逐级一致，`expected_total` 留空以避免把一次施法的范围目标
集合压成单一总值。

## DMG-04 第四十二个逐级实例：Evade

Evade 不拥有输出伤害包。1.10f `Skills.txt#29` 使用 `SrvDoFunc=0`、`SrcDam=0`，没有
物理/元素固定伤害、导弹或元素长度；`PassiveState=evade`、`PassiveStat=passive_evade`、
`PassiveCalc=dm12`，`Param1=10`、`Param2=65`，所以等级 1–20 的原生曲线为
`18,25,30,34,37,40,42,44,46,47,49,50,51,52,53,54,54,55,55,56`%。矩阵行 462–481
把三组 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确留空为 N/A。

D2MOO 的被动刷新通过 `SKILLS_RefreshSkill` 读取 `dm12` 并把结果写入永久 `evade` 状态。
`SUNITDMG_ApplyBlockOrDodge` 在移动单位的 incoming hit 上优先检查 Evade，成功后清除
successful-hit，后续伤害不会填充；静止单位则继续适用盾牌格挡、Weapon Block 和 Avoid
分支。Evade 不是攻击者的伤害修正，而是防御者的移动规避状态。

riiablo 的 `AmazonSkills.getEvadeChance`、`applyPassiveState`、`DefenseCalculator` 和
`CombatSystem` 复用同一 Skills.txt 状态/公式及移动攻击上下文。`EvadeGoldenDamageTest`
锁定全部 20 级、原版字段、无伤害列和每级 `passive_evade` 状态贡献；随机边界、动画、
静止上下文优先级和最终来袭伤害结算留给 DMG-08。

## DMG-04 第四十三个逐级实例：Fend

Fend 不拥有固定技能伤害包。1.10f `Skills.txt#30` 使用 `SrvStFunc=9`、`SrvDoFunc=13`、
`SrcDam=128`、`HitShift=8`、`ToHit=40`、`LevToHit=10`，物理/元素固定伤害字段均为 0，
`Calc1=12`、`Calc2=ln34`、`Param1=70`、`Param2=10`。因此等级 1–20 的 `Calc2` 曲线为
`70,80,90,100,110,120,130,140,150,160,170,180,190,200,210,220,230,240,250,260`%，
矩阵行 482–501 把三组 `expected_*`、`riiablo_actual_*` 和 `delta_*` 明确留空为 N/A。

D2MOO `SKILLS_SrvSt09_Fend`（`SkillAma.cpp:258`）在技能开始时以 `Calc1` 限制近战范围内
的目标流，并把首目标类型/ID写入技能参数。`SKILLS_SrvDo013_Fend_Zeal_Fury`
（`SkillAma.cpp:795`）每个攻击关键帧只处理一个当前目标：成功命中时把 `Calc2` 写入
`dwEnDmgPct`，再用 `SrcDam=128` 分配一条武器战斗记录并扣除耐久，随后选择下一个目标。
目标数量和一条施法的总伤害不是单个技能伤害包，不能压入 DMG-04 的固定 `expected_*`。

riiablo 的 `AmazonSkills.getFendHitCount`、`getPhysicalDamagePercent` 与
`Actioneer.prepareFend/resolveFend` 复用原生 12 目标上限、70%–260% 增强曲线和独立目标流。
`FendGoldenDamageTest` 锁定全部 20 级、原版字段、无固定伤害列和每级增强百分比；目标
选择、命中随机、武器值、耐久和整次施法总量留给 DMG-06/07/08。

## DMG-04 第四十四个逐级实例：Dopplezon

1.10f `Skills.txt#28` 使用 `SrvStFunc=0`、`SrvDoFunc=15`、`Summon=dopplezon`、
`PetType=dopplezon`、`PetMax=1` 和 `Summode=NU`。`SrcDam`、物理/元素伤害字段、
`SrvMissile` 和 `ELen` 均为空或 0；因此 Dopplezon 技能本身不拥有可填入 DMG-04
`expected_min/max/total` 的独立伤害记录，矩阵 442–461 行明确写 N/A 空白。

D2MOO `SKILLS_SrvDo015_Dopplezon`（`D2Game/src/SKILLS/SkillAma.cpp:922–974`）创建
召唤后以 `Calc3=par3` 将生命替换为 owner 最大生命的百分比，再调用
`D2GAME_SKILLS_SetSummonBaseStats_6FD0CB10` 和
`D2GAME_SetSummonPassiveStats_6FD0C530`。后者读取 `AuraStat` 抗性，应用
`Calc1=lvl*par4` 的额外最大生命，安装 `SumSkill`、UMod、Overlay 和召唤装备；到期事件
与 AI 更新同样由该原生链完成。Decoy 不执行攻击，召唤物的 MonStats 近战值不能倒灌为
Dopplezon 技能伤害。

`DopplezonGoldenDamageTest` 锁定 `ln12` 时限 `250 + 125/级`、`par3=50` 的 owner
生命百分比、无伤害字段和 `summon.decoy` 行为注册。riiablo 当前只覆盖创建实体、等级、
owner-relative HP 和时限，缺失的召唤被动/技能/装备、Calc1、UMod、Overlay 和完整
生命周期继续归入 DMG-07，不在 DMG-04 伪造零伤害黄金值。

## DMG-04 第四十五个逐级实例：Freezing Arrow

Freezing Arrow 的固定技能冷伤由爆炸子导弹拥有。1.10f `Skills.txt#31` 使用
`SrvStFunc=4`、`SrvMissile=freezingarrow`、`SrcDam=128`、`HitShift=8`，元素类型为
`cold`，`EMin/EMax=40–50`，五段增量为 `10/15/20/22/24`，`EDmgSymPerCalc` 读取
Cold Arrow 硬点并乘 `par8`；本批协同固定为 0。`freezingarrow` 的 `SrvHitFunc=4`
不在根箭快照中保留技能冷伤，`HitSubMissile[0]=freezingarrowexp3` 子导弹的
`SrvHitFunc=1` 才拥有冷伤包。等级 1–20 的单目标抗性前冷伤为 `40–50` 至 `310–320`，
`coldlength` 固定为 50 帧；矩阵行 502–521 的 expected/actual 冷伤与 delta=0 已明确记录。

D2MOO `MISSMODE_SrvHit04_ExplodingArrow_FreezingArrow_RoyalStrikeMeteorCenter`
（`MissMode.cpp:2673`）创建子导弹并继承原技能、等级和 owner；
`MISSMODE_SrvHit01_Fireball_ExplodingArrow_FreezingArrowExplosion`
（`MissMode.cpp:2087`）读取子导弹技能快照，把冷伤包应用到半径内每个目标。根箭的武器
命中、爆炸范围目标集合、Boss/Unique/Hireling 冻结回退和整次施法总量不属于本批单个
子导弹黄金值。

riiablo `MissileDamageResolver.initializeSkill` 保留根箭武器快照，
`initializeSkillArea` 为 `freezingarrowexp3` 写入冷伤和 50 帧冻结元数据；
`FreezingArrowGoldenDamageTest` 锁定全部 20 级、父子所有权、生产冷伤快照和冻结长度。
范围覆盖、死亡顺序和最终抗性/结算留给 DMG-06/07/08。

## DMG-04 第四十六个逐级实例：Valkyrie

Valkyrie（技能 32）本身只负责创建召唤物，不拥有可独立填入 DMG-04 的伤害包。
1.10f `Skills.txt#32` 使用 `SrvDoFunc=16`、`Summon=valkyrie`、`PetType=valkyrie`、
`PetMax=1` 和 `Summode=NU`；`SrcDam=0`，物理/元素伤害字段、元素长度及五段增量均为
0，且没有 `SrvMissile`。`Calc1=par1 * (lvl - 1) + skill('Dopplezon'.blvl) * par8`
用于原生召唤初始化的等级/属性链，`Calc2=ln56` 用于女武神的生成装备等级，均不是技能
自身的 outgoing damage。矩阵行 522–541 因此把 `expected_min/max/total`、
`riiablo_actual_*` 和 `delta_*` 明确留空为 N/A。

D2MOO `SKILLS_SrvDo016_Valkyrie`（`D2Game/src/SKILLS/SkillAma.cpp:979–1022`）创建
`valkyrie` 召唤物，调用 `D2GAME_SKILLS_SetSummonBaseStats_6FD0CB10` 和
`D2GAME_SetSummonPassiveStats_6FD0C530`，再安装 AI 事件与 `STATE_VALKYRIE`。后续
女武神攻击记录由召唤物的 `MonStats.txt`、`SumSkill`、被动属性和生成装备共同拥有，
不能倒灌为 Valkyrie 技能的固定伤害值；这些继承、装备、命中和整次召唤生命周期留给
DMG-07/08。

riiablo 的 `NativeSkillBehaviorRegistry` 注册 `summon.valkyrie` 且不生成 server
missile；`ValkyrieGoldenDamageTest` 锁定全部 20 级的召唤字段、零伤害字段和
`Calc2=ln56` 原生初始化公式。当前生产路径已覆盖实体、owner、等级和 Valkyrie 状态，
但被动属性、SumSkill 与生成装备差异仍按所有权清单保留为 DMG-07 缺口。

## DMG-04 第四十七个逐级实例：Pierce

Pierce 不拥有输出伤害包。1.10f `Skills.txt#33` 使用 `SrvStFunc=0`、`SrvDoFunc=0`、
`SrcDam=0`，没有物理/元素固定伤害、server missile 或元素长度；`PassiveState=pierce`、
`PassiveStat=skill_pierce`、`PassiveCalc=dm12`，`Param1=10`、`Param2=100`。原生递减公式
`a + 110 * level * (b-a) / (100 * (level+6))` 在等级 1–20 产生
`24,34,43,49,55,59,63,66,69,71,74,76,77,79,80,82,83,84,85,86`%。矩阵行
542–561 的 `expected_*`、`riiablo_actual_*` 和 `delta_*` 全部明确留空为 N/A。

D2MOO `SKILLS_RefreshPassiveSkills`（`D2Common/src/D2Skills.cpp:603`）刷新永久 Pierce
状态。创建带 `Missiles.txt.Pierce` 标记的投射物时，`Missiles.cpp:321–337` 合并
`STAT_ITEM_PIERCE` 与 `STAT_SKILL_PIERCE`，按概率预掷最多 4 次继续机会并写入
`STAT_PIERCE_IDX`；`MissMode.cpp:5037–5045` 在成功碰撞后消耗该计数，使同一导弹及其
原始伤害快照继续飞向后续目标。Pierce 不复制或修改单目标伤害值。

riiablo 的 `AmazonSkills.getPierceChance` 与永久状态桥接复用同一 `dm12` 公式，
`ServerSkillSystem.configurePierce` 仅为原生可穿透导弹预掷继续次数，
`MissileCollisionSystem` 维护同一导弹的已命中目标集合并逐次消耗计数。
`PierceGoldenDamageTest` 锁定全部 20 级概率、零伤害字段和永久 `skill_pierce` 状态；
命中随机、导弹原始伤害、后续目标数量和整次施法总量留给 DMG-06/07/08。

## DMG-04 第四十八个逐级实例：Lightning Strike

Lightning Strike 的首个近战目标与后续链段目标各拥有一份相同技能曲线的闪电包。
1.10f `Skills.txt#34` 使用 `SrvStFunc=10`、`SrvDoFunc=14`、`HitShift=8`，元素类型为
`ltng`，`EMin/EMax=1–25`，最大值五段增量为 `10/15/20/25/30`；
`EDmgSymPerCalc` 读取 Charged Strike、Lightning Bolt、Power Strike、Lightning Fury
硬点并乘 `par8`，本批协同固定为 0。等级 1–20 的单目标规范 getter 范围为 `1–25`
至 `1–295`，矩阵行 562–581 的 expected/actual 与 delta=0 已明确记录；total 保持 N/A。

D2MOO `SKILLS_SrvSt10_LightningStrike`（`SkillAma.cpp:339`）在成功近战命中时把技能
闪电范围写入战斗记录，并以 `Calc1=20` 设置增强物理百分比；`SrcDam=0` 在该回调中按
原版回退为 128，因此完整近战记录还包含武器包。`SKILLS_SrvDo014_LightningStrike`
（`SkillAma.cpp:870`）从首个受击者附近选择另一目标，创建继承技能 ID/等级的
`lightningstrike` 导弹，并以 `Calc2=ln34` 写入 2–21 的链跳预算。
`MISSMODE_SrvHit12_ChainLightning_LightningStrike`（`MissMode.cpp:2522`）每次命中再创建
一个同技能、同等级的子链段并递减预算。因此链段数量不能乘进一个目标的黄金伤害。

riiablo 的近战路径通过 `MissileDamageResolver.skillElementalDamage` 取得相同曲线并写入
`calculateLightningStrikeAttack`，链式路径则由 `spawnLightningStrike` 与
`MissileCollisionSystem` 为每个链段保存相同技能快照。`LightningStrikeGoldenDamageTest`
锁定全部 20 级规范范围、零协同、20 格范围及 2–21 跳预算。完整武器包、20% 增强物理、
命中随机、链路目标集合与施法总量留给 DMG-06/07；抗性和最终结算留给 DMG-08。
