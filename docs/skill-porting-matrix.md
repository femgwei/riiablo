# 技能移植对照表：riiablo ↔ dark-magic

更新时间：2026-09-30

## 目标与版本边界

本表用于审计 riiablo 七个职业技能实现，并把 dark-magic 中已经验证的行为族和测试用例映射到 riiablo。

- riiablo 的目标行为基线是 Diablo II 1.10f，优先以本地 `F:/3rd_src/D2MOO`、1.10f MPQ 和必要时 The Phrozen Keep 为准。
- dark-magic 当前目标是 LoD 1.14d Expansion；它的数值、技能表行和某些事件顺序不能直接复制到 riiablo。
- 可以直接借鉴的是：精确技能 ID 白名单、数据解码、固定 tick 事务、状态/所有权生命周期、checkpoint/replay 以及测试组织方式。
- 本表中的“dark-magic 对照”表示存在可复用的行为族或测试思路，不表示两个版本的公式已经相等。

## 当前基线

### riiablo

`CharacterClass` 已覆盖七个职业技能区间：Amazon `6..35`、Sorceress `36..65`、Necromancer `66..95`、Paladin `96..125`、Barbarian `126..155`、Druid `221..250`、Assassin `251..280`。

共享入口是 `NativeSkillResolver`、`SkillExecutor`、`ServerSkillSystem`；职业专用逻辑位于 `AmazonSkills`、`AssassinSkills`、`BarbarianSkills`、`DruidSkills`、`NecromancerSkills`、`PaladinSkills`、`SorceressSkills`。

现有职业专项测试数量（按文件名统计）为：Amazon 4、Assassin 2、Barbarian 7、Druid 18、Necromancer 19、Paladin 10、Sorceress 11。这个数量只能表示已有测试入口，不能表示技能已经通过原生行为验收。

### dark-magic

当前 manifest 位于 `F:/3rd_src/dark-magic/internal/content/d2legacy/manifests/skill-behavior-coverage.v1.json`，共有 43 个 exact-ID 配置，归入 16 个可复用行为族。所有声明仍带有 `partial` evidence 状态。

研究报告曾在 2026-08-18 记录 357 条 Skills.txt、172 种行为签名、33 个明确接入配置和 324 个缺失配置；该统计早于当前 manifest，不能用来替代当前清单。

## 职业级对照

### Amazon（当前最高优先级）

- riiablo 入口：`AmazonSkills.java`、`Actioneer` 的 Amazon 分支、`NativeSkillResolver.isAmazonBowSkill/isAmazonJavelinSkill`。
- 当前测试：`AmazonSkillSpecializationTest`、`NativeAmazonPassiveDataTest`、
  `NativeAmazonPoisonJavelinDataTest`、`NativeAmazonSkillMatrixTest`、
  `AmazonMeleeSkillLifecycleTest`、`NativeAmazonCombatFormulaTest`、
  `NativeAmazonAmmoPolicyTest`。
- 本轮新增 `NativeAmazonSkillMatrixTest` 与 `NativeSkillBehaviorRegistry`：30 行 Amazon
  技能以 exact ID + `srvstfunc/srvdofunc` 注册行为族；`NativeSkillResolver` 只在回调号
  与注册声明完全一致时把行为族写入 `SkillExecutor.SkillData`，未知/篡改行保持未注册
  （fail-closed）。这是 dark-magic `skill-behavior-coverage` manifest 的 Java 对应层，
  目前只声明 Amazon，不能据此声称其他职业已覆盖。
- dark-magic 对照：没有 Amazon exact-ID 配置；Amazon 必须直接按 D2MOO 1.10f 和真实 1.10f 数据审计，不能把 dark-magic 的其他职业行为族当作 Amazon 结论。
- Jab/Impale/Fend 已补齐一轮 D2MOO SrvSt/SrvDo 生命周期回归：Jab 固定消费三次
  SrvDo007 keyframe 并拒绝过期第四次；Impale 在 SrvSt07 预计算一次 CombatResult，
  由 SrvDo002 幂等消费；Fend 按 SrvSt09 的 calc1 上限建立目标流，由 SrvDo013 每次
  keyframe 前进到不同附近目标。测试保留真实命中 RNG，miss 不被误判为生命周期失败。
  本轮又补充了 SrvSt05 无目标 fail-closed、Impale 近战范围拒绝和 Fend 首目标死亡
  重定向，并用可控 RNG 覆盖 Impale `Calc2` stack quantity 与 `Calc3` weapon durability。
  本轮还按 D2MOO 固定点路径覆盖 ToHit/LevToHit、SrcDam、空武器 1..2 最小包和 -90%
  伤害百分比下限；确认 1.10f Jab 的实际 Calc1 为 `-15`，不再使用经验性 +8% 兜底。
  Impale 的 miss 与 keyframe 前目标死亡也已验证为不消耗武器资源，并在动画完成时清理
  遗留记录。
  弹药策略按 `isAmazonBowSkill && !noammo` 对齐：Magic Arrow 虽是弓技能但原生
  `noammo=1`，标枪技能不会错误消耗弓箭袋。
  `Casting` 组件池复用也已覆盖 Impale 预计算记录清理，避免实体重绑定后沿用旧的
  `CombatResult`；真实网络断线重连仍待验收。
  Power Strike/Charged Strike 已按 D2MOO 的 `SrvDo002/SrvDo011` 接入成功命中扣武器耐久，
  并由 Amazon 技能矩阵锁定该耐久行为族。
  两者的 `SrvSt06` 也已改为一次性预计算物理+闪电包，keyframe 不再重新掷骰或丢失元素伤害。
  这仍只完成 ECS/纯逻辑层的一轮门槛，真实 MPQ 动画和重连仍待验收。
- 首批门槛：30 个 Amazon 行逐行检查 `charclass/reqskill/reqlevel/mana/InTown/SrvStFunc/SrvDoFunc/武器限制/弹药/quantity/ToHit/SrcDam/EType/Calc1..4`，再做固定种子、多目标、失手、墙碰撞、死亡和重连测试。

### Sorceress

- riiablo 入口：`SorceressSkills.java`、`MissileDamageResolver`、`SkillExecutor`。
- 当前测试：防御状态、火焰区域、投射物、Blizzard、Frozen Orb、Meteor、Static Field、Thunder Storm、Frost Nova 等专项测试。
- dark-magic exact-ID 对照：Fire Bolt(36) `missile.straight`；Frozen Armor(40)、Shiver Armor(50)、Chilling Armor(60) `state.self-timed`；Ice Blast(45) `missile.straight-freeze`；Fire Ball(47) `missile.straight-impact-area`；Nova(48) `missile.radial`；Enchant(52) `state.targeted-timed`；Teleport(54) `movement.point-relocate`；Glacial Spike(55) `missile.straight-impact-area-freeze`。
- 重点借鉴：把“投射物移动/接触/伤害”和“爆炸表现实体”分开；把冰甲的防御、持续时间、触发条件、冻结/减速和状态替换放入同一来源生命周期。
- 本轮先完成 Teleport(54) 的数据对照：D2MOO `SKILLS_SrvDo027_Teleport` 与
  dark-magic `movement.point-relocate` 均要求按 Levels.Teleport 策略验证落点；
  Skills.txt mana 行为为 `24 - (level-1)`，最低 1。riiablo 已补充原生行测试，
  并修正 `SorceressSkills.calculateTeleportManaCost` 的旧最低值 6；实际地图落点、
  flying collision 和旧移动意图清理已有 `SpecialSkillEcsScenarioTest` 覆盖。
- 本轮继续完成 Ice Blast(45) 的首轮对照：D2MOO `MISSMODE_SrvDmg04_IceBlast`
  会把 `dwColdLen` 转移到 `dwFrzLen` 并清零普通冷却。riiablo 现已按
  `Missiles.txt.pSrvDmgFunc=4` 识别冻结包，避免同一次命中同时建立 COLD 和 FREEZE；
  同时补上 `ELenSymPerCalc` 的百分比持续时间协同（Ice Blast 的 Glacial Spike
  硬点），并以真实 1.10f Skills/Missiles 行锁定 `EMin/EMax/HitShift/ELen`、
  `srvDmgFunc` 和 82 帧一级持续时间断言。测试入口为
  `NativeSorceressProjectileDataTest.iceBlastUsesNativeSrvDmg04FreezeConversionAndLengthSynergy`。
  `SorceressIceBlastIntegrationTest` 已覆盖 ECS 层的冷免疫、只建立 FREEZE、以及
  单目标重复命中门闩；致死命中时的冻结/SHATTER 顺序仍沿用现有 D2MOO 冻结包回归，
  后续再单独核对 Ice Blast 与普通冻结箭的差异。
- Glacial Spike(55) 已按 dark-magic `missile.straight-impact-area-freeze` 与 D2MOO
  `MISSMODE_SrvHit13_GlacialSpike_HellMeteorDown` 对照：命中点按
  `AuraRangeCalc` 对其他敌对目标扇出同一伤害包，冻结时长来自 `AuraLenCalc`，而不是
  普通 `ELen`。riiablo 已补 `pSrvHitFunc=13` 的扇出路径、`frze` 包只建立 FREEZE、
  冷免疫过滤和目标一次性命中；`NativeSorceressProjectileDataTest` 锁定真实行的
  `ln12/ln34`、参数、HitFlags 和 helper 表现导弹，`SorceressIceBlastIntegrationTest`
  覆盖中心/范围内/范围外/冷免疫目标及冻结时长。
- Fire Ball(47) 已开始按 dark-magic `missile.straight-impact-area` 对照：D2MOO
  `MISSMODE_SrvHit01_Fireball_ExplodingArrow_FreezingArrowExplosion` 的父导弹负责
  命中包，`ExplosionMissile` 只承载同一伤害包的范围扇出。riiablo 现在为爆炸子导弹
  增加运行时 impact radius 快照，并共享父导弹命中集合，确保中心目标不被父/子导弹
  重复结算；真实 1.10f `pSrvHitFunc=1`、`sHitPar[0]=4` 和 16 帧表现生命周期
  已锁定在 `NativeSorceressProjectileDataTest`。新增
  `SorceressFireBallIntegrationTest` 覆盖 ECS 多目标范围扇出、中心目标不重复伤害、
  墙碰撞停止导弹并生成 impact 表现；同时为父导弹增加独立的 cast-lifetime shared
  gate，避免父实体池化重置后清空子导弹去重集合；另覆盖中心目标致死前的 impact
  创建、范围扇出及表现导弹 16 帧生命周期。真实 1.10f MPQ 双客户端门槛现已通过：
  目标锁定后两端共同观察到 `fireball` 父导弹和 `explodingarrowexp` 子导弹，且短生命周期
  效果不错误进入重连保活门槛。
- Nova(48) 已按 D2MOO `SKILLS_SrvDo022_NovaAttack` / `sub_6FD14170` 完成首轮
  对照。1.10f 原生行为是固定 64 个偏移点、使用 `SrvMissileA` 发射 `nova`，速度为
  `Missiles.txt.Vel + Skills.txt.Calc1`；riiablo 保留原生坐标表、24 速度、13 格
  寿命和 1..20 闪电伤害快照。一次施法的 64 枚导弹共享命中集合，因此同一目标只会
  结算一次，mana 也只在 `SkillCastEvent` 验证成功时扣除一次。新增
  `NativeSorceressProjectileDataTest.novaUsesNativeSrvDo22SixtyFourPathRow` 与
  `SorceressNovaIntegrationTest`，覆盖 64 路创建/方向速度、跨路径去重、后续 tick
  不重复伤害、前置技能/单次 mana 扣除和 50%/100% 闪电抗性结算。墙碰撞、致死后其他路径
  继续命中、13 格范围到期和不重建已由 ECS 测试覆盖；真实 1.10f MPQ 双客户端门槛现已
  通过，两端共享同一权威 `nova` 导弹实体和伤害等级。dark-magic 的 `12 + 4/level` 数量只作
  行为族测试参考，不能替代 D2MOO 固定 64 路规则。
- 版本风险：dark-magic 的 Fire Ball/Nova/Ice Blast 数值来自 1.14d；riiablo 必须用 D2MOO 1.10f 的函数和数据重新确认。

### Necromancer

- riiablo 入口：`NecromancerSkills.java`、`SummonedPetSystem`、`CorpseConsumption`。
- 当前测试：诅咒、Bone Armor/Bone Wall、Poison Dagger/Nova、Corpse/Poison Explosion、Skeleton/Golem/Revive 及 AI 集成测试。
- dark-magic exact-ID 对照：Amplify Damage(66)、Weaken(72) `state.point-area-curse`；Raise Skeleton(70)、Raise Skeletal Mage(80)、Revive(95) `summon.targeted-corpse`；Clay(75)、Blood(85)、Iron(90)、Fire Golem(94) `summon.golem`。
- 重点借鉴：尸体消费和召唤创建必须是一次权威事务；PetType 上限、owner/source、Iron Golem 的物品来源、召唤物替换、断线/换图/死亡清理都要进入测试。
- riiablo 已有较多对应实现，但应逐项对照 dark-magic 的“创建前验证 → effect tick 再验证 → 成功后消费/替换”顺序。
- Poison Nova(92) 已补入真实 1.10f D2GS 双客户端门槛：生成 Necromancer 存档并发送真实施法包，
  两端共同观察同一权威 `poisonnova` 导弹实体、技能 ID 和伤害等级。新增
  `NecromancerPoisonNovaIntegrationTest`，覆盖 64 路共享命中去重、8.8 毒伤/持续帧快照、
  毒抗免疫、毒穿透对伤害与持续时间的影响，以及施法后修改施法者属性不污染飞行中导弹。
  当前 Poison Nova 技能链已完成首轮核对；Raise Skeleton/Revive/Golem 已补齐无效尸体、
  创建失败回滚和 Iron Golem 物品预留回滚门槛；`SummonedPetSystemTest` 已覆盖 Necromancer
  PetType 归一化、owner 离开清理以及 Golem/Revive 跨区跟随；`ServerEntityFactorySummonQuotaTest`
  已覆盖真实 PetMax 最旧实体替换和 Golem 共享配额，`SummonedPetSerializerTest` 已覆盖
  owner/PetType/skillId/unsummonable 重连字段。
- `headlessSummonReconnect` 已通过真实 1.10f MPQ 双客户端门槛：死灵法师 Skeleton 在
  `SummonedPetP` 中保持 ownerId/PetType/skillId/unsummonable；观察客户端断线不会清理
  召唤物，重连后按原 entityId 重放快照，旧 owner 的召唤物仍保持活动；同一实体帧
  同时带标准 `MonsterP` 且 type=1，为忽略未知 `SummonedPetP` 的旧客户端保留普通怪物
  显示路径；`ServerClientCombatSyncEcsScenarioTest` 进一步锁定同一 EntitySync 同时
  携带两个组件及 Skeleton 的 owner/type/skill 字段。
- 当前剩余门槛集中在真实旧客户端 UI/动画观感，以及完整技能树审计。

### Paladin

- riiablo 入口：`PaladinSkills.java`、`AuraManager`、`AuraEcsSystem`。
- 当前测试：Aura 数据、Resistance/Support/Resource/Special Aura 集成、Blessed Hammer、Fist of the Heavens、Paladin melee。
- dark-magic exact-ID 对照：Might(98)、Resist Fire(100)、Thorns(103)、Defiance(104)、Resist Cold(105)、Blessed Aim(108)、Resist Lightning(110)、Vigor(115)、Salvation(125) `aura.selected-party-stat`；Prayer(99)、Cleansing(109)、Meditation(120) `aura.selected-party-periodic`；Redemption(124) `aura.selected-corpse-periodic`。
- 重点借鉴：aura source 身份、右键选择、同级目标稳定排序、半径筛选、pulse 计划、资源不足不改变目标、取消/替换/重连清理。
- 新增 `AuraEcsScenarioTest.equalLevelAurasUseStableLowestCasterTieBreakRegardlessOfActivationOrder`：
  对照 dark-magic 的 selected-party aura 稳定排序要求，锁定同等级 Might 不受激活或
  `IntMap` 遍历顺序影响，最低 caster id 获胜；原 winner 失效后下一次 native pulse
  才切换到备用 aura，保留短时 state layer 语义。
- 对照 dark-magic `periodic_aura_skills_test.lua` 的 Cleansing pulse 目标语义，修正
  `AuraManager`：施法者自身和范围内盟友都执行 poison/可净化 curse 缩短；新增
  `AuraManagerPulseTest.cleansingPulseAlsoProcessesTheCaster`，避免 self 分支只保留
  AuraState 却漏掉周期净化效果。
- 又补齐 dark-magic 周期光环的链接技能公式：Cleansing/Meditation 的
  `skill('Prayer'.edns)` 现在通过 `SkillFormula` 解析为原生 8.8 fixed healing，
  并由 AuraManager 传入真实技能行解析器；`AuraEcsScenarioTest` 覆盖施法者与队友
  同 pulse 治疗、Cleansing 净化缩短和 Meditation 法力恢复状态。此前该引用因只支持
  `.blvl/.lnXY/.dmXY` 而静默得到 0，导致两项技能遗漏 Prayer 治疗。
- 这是 riiablo 下一批最值得迁移 dark-magic 测试结构的职业之一，尤其是 aura 优先级和多人快照。

### Assassin

- riiablo 入口：`AssassinSkills.java`、`AssassinTrapSystem`、`NativeTrapSystem`。
- 当前测试：`AssassinMartialArtsTest`、`AssassinSkillSpecializationTest`，以及 Native Trap/Object/Fire 测试。
- dark-magic exact-ID 对照：Fire Blast(251)、Shock Web(256)、Blade Sentinel(257)、Charged Bolt Sentry(261)、Wake of Fire Sentry(262)、Blade Fury(266)、Lightning Sentry(271)、Wake of Inferno(272)、Death Sentry(276)、Blade Shield(277)，统一为 `trap.assassin-family`。
- 重点借鉴：精确 ID 白名单、helper missile 解码、陷阱落地/替换/数量上限、Blade Sentinel 巡逻、Death Sentry 尸体事务、Blade Shield 周期武器效果。
- 已补齐 helper missile 的 fail-closed 门槛：`AssassinTrapSystem` 只接受 `srvmissile*`
  服务端字段，不再把 `cltmissile*` 视觉字段当成权威伤害导弹；对应回归位于
  `AssassinSkillSpecializationTest.trapMissileResolutionRejectsClientOnlyHelperRows`。
- 已按 D2MOO `AITHINK_Fn101_AssassinSentry` / `AITHINK_Fn104_DeathSentry` 收紧攻击技能
  解析：召唤体缺少 `MonStats.Skill1/Skill2` 时进入 fail-closed，不再回退执行放置技能，
  防止 `SrvDo045` 递归或错误 helper missile；对应回归位于
  `AssassinSkillSpecializationTest.trapAttackResolutionFailsClosedWhenSummonHasNoAttackSkill`。
- 攻击技能等级已完成核对：D2MOO `Monster.cpp` 先按召唤体 `MonStats.Sk#lvl` 建立初始技能，
  但 `sub_6FCF8610` 随后调用 `D2GAME_SetSummonPassiveStats`；该函数按放置技能的
  `SumSkill/SumSkCalc` 再次 `D2GAME_SetSkills`。原生陷阱行的主攻击均为 `SumSkCalc=lvl`
  （Death Sentry 的 `Skill1`/`Skill2` 也都是 `lvl`），所以运行时应保存放置技能等级快照，
  不能直接采用静态 `Sk1lvl/Sk2lvl=1`。`AssassinSkillSpecializationTest.auditTrapSummonSkillInheritanceRows`
  现在锁定六类陷阱的完整继承映射和协同来源；现有 Death Sentry 等级 4 回归与该结论一致。

### Barbarian

- riiablo 入口：`BarbarianSkills.java`、共享 `Actioneer`/`CombatSystem`。
- 当前测试：Berserk、Frenzy、War Cry、Whirlwind、尸体技能和被动数据测试。
- dark-magic exact-ID 对照：当前没有 Barbarian 配置。
- 审计依据：D2MOO 1.10f 的 `SkillBarb.cpp`、Skills.txt/MonStats2/States.txt 和真实技能动作；不要因为 dark-magic 没有实现就降低验证要求。
- 重点门槛：Frenzy 状态叠加、Whirlwind 目标流、Berserk 物理/魔法转换、War Cry 范围/眩晕、战斗专精被动聚合。

### Druid

- riiablo 入口：`DruidSkills.java`、`DruidShapeShiftResolver`、共享 `Actioneer`/导弹系统。
- 当前测试：Feral Maul、Fury、Rabies/Fire Claws、形态、Shock Wave、Firestorm、Fissure、Volcano、Storm Aura、Summon。
- dark-magic exact-ID 对照：当前没有 Druid 配置。
- 审计依据：D2MOO 1.10f 的 Druid skill/形态/召唤链和真实 MPQ；dark-magic 的通用状态、导弹、持续区域和 summon 测试结构仍可移植。
- 重点门槛：形态状态与武器/动画、持续区域导弹生命周期、召唤物所有权、毒素/火焰快照和重连。
- 本轮补齐持续区域子导弹的协同快照：Fissure/Volcano 控制器生成的火焰子导弹现在
  读取施法者硬点 `baseSkillLevel`，不再因子导弹阶段使用空 resolver 而丢失
  `EDmgSymPerCalc`；`DruidVolcanoIntegrationTest` 已覆盖 Volcano 的 Eruption 协同。
- Firestorm 的 `SrvDo117` 多流创建现在由 `DruidFirestormIntegrationTest` 锁定施法时
  硬点协同快照：每条权威流都必须保留 Molten Boulder/Eruption 协同后的火焰包；真实
  MPQ 双客户端动画/命中观感仍待验收。
- Armageddon/Hurricane 的 `SrvDo124` 周期状态现在由
  `DruidStormAuraIntegrationTest.periodicStormStrikeCapturesNativeSkillDamageAtPulse`
  锁定：服务端脉冲必须创建带来源技能、等级和元素包的权威导弹，snapshot-only 客户端
  不得自行推进周期时钟。
- Druid `SrvDo114/115/119` 召唤链已按 D2MOO 使用 `Skills.txt Calc2` 计算召唤基础等级，
  `DruidSummonIntegrationTest.summonBaseLevelUsesNativeCalc2InsteadOfOwnerLevelHeuristic`
  防止角色等级启发式回归；召唤物跨区/重连和旧客户端动画仍待真实资源验收。
- `SummonedPetSystemTest.druidWolfFollowsOwnerAcrossZoneBoundaryWithSkillSourceMetadata`
  现在锁定 Dire Wolf 的 `fenris` PetType 跨区跟随，以及 owner/skillId/skillLevel 元数据
  不丢失。
- spirit aura 已按 D2MOO `SumSkill/SumSkCalc` 解析关联的 `Oak Sage Aura`、`Wolverine Aura`
  和 `Barbs Aura` 行；`DruidSummonIntegrationTest` 锁定 Oak Sage max-life、Wolverine
  attack/damage、Spirit of Barbs thorns 的原生 `ln34/ln56` 数值，以及 owner layer 的
  pet source identity、超出范围撤销和宠物删除撤销。
- Druid vine 已按 D2MOO `SrvDo115_Vines` 核对：Poison Creeper、Carrion Vine、Solar
  Creeper 均安装 `Vine Attack`（`SumSk1Calc=lvl`）并持有 `VINE_BEAST` 状态；
  `DruidSkills.getSummonGrantedSkillLevel` 和 `DruidSummonIntegrationTest` 锁定父技能等级
  传递，避免回退到 MonStats 静态技能等级。vine 不参与 party aura 投影。
- 真实 1.10f 双客户端专项已完成 `Plague Poppy`（当前表行 id 222）：`Vine Attack` 根导弹
  runtime index 474、`plague vines trail` runtime index 475、共享实体/状态和 reconnect
  均已通过。当前 `Cycle of Life`/`Vines`（231/241）仍有施法入队但无召唤快照的问题，
  暂不移植为“已完成”。由于 D2MOO trail 行没有直接毒素字段，也不把 poison state 作为
  已验证结果。

## dark-magic 测试用例移植映射

以下不是简单复制 Lua，而是把相同的行为断言落到 riiablo 的纯 Java、ECS、D2GS 和真实 MPQ 四层门槛：

- `data/skill_behavior_coverage_test.lua` → 新增 `SkillBehaviorCoverageMatrixTest`：精确 skill ID、行为族、未知函数拒绝、不得因函数形状相似而自动放行。
- `data/skill_test.lua` → 扩展 `CharacterSkillMatrixTest` 和 `NativeSkillResolverTest`：职业归属、起始技能、前置技能、reqlevel、mana、InTown、oskill。
- `data/skill_modifiers_test.lua`、`systems/skill_synergy_test.lua` → 扩展 `SkillFormulaTest` 和各职业 Native 数据测试：`.blvl/.lvl/.edns/.edmn` 的引用解析、硬等级和有效等级分离、舍入顺序。
- `data/missile_skills_test.lua`、`systems/missile_skill_test.lua` → `MissileNativePolicyTest`、`CombatPipelineIntegrationTest`：创建 tick、owner/source、移动、扫掠碰撞、命中、删除、checkpoint。
- `systems/impact_missile_skill_test.lua`、`systems/area_freeze_missile_skill_test.lua` → Sorceress/Amazon 导弹专项：一次 impact 点、范围内目标稳定排序、每目标独立 RNG、冻结/区域表现不重复结算。
- `data/state_skills_test.lua`、`systems/state_skill_test.lua` → `SorceressDefenseIntegrationTest` 和状态专项：同源刷新、States group 替换、过期、抗性/免疫、受击触发。
- `data/targeted_state_skills_test.lua`、`systems/targeted_state_skill_test.lua` → Enchant/诅咒测试：目标合法性、友军/PvP、来源身份、持续时间快照和重连恢复。
- `data/aura_skills_test.lua`、`data/periodic_aura_skills_test.lua`、`systems/aura_skill_test.lua` → `PaladinSupportAuraIntegrationTest`、`PaladinResourceAuraIntegrationTest`、`AuraEcsScenarioTest`：选中 aura、目标关系、pulse、资源支付、撤销和多玩家排序。
- `data/corpse_summon_skills_test.lua`、`data/golem_summon_skills_test.lua`、`systems/corpse_summon_skill_test.lua` → Necromancer summon/golem/revive 集成测试：尸体/金属物品原子消费、PetType 上限、owner 清理和实体重建。
- `data/trap_skills_test.lua`、`systems/trap_skill_test.lua` → `NativeTrapSystemTest`、`AssassinSkillSpecializationTest`：陷阱 helper、落地、数量、周期射击、尸体爆炸、Blade Sentinel 巡逻和 checkpoint。
- `data/point_movement_skills_test.lua`、`systems/point_movement_skill_test.lua` → Teleport/Charge/Leap 的点位、碰撞、RoomEx、边界和重连测试。
- dark-magic 的 `*_real_test.go` → riiablo 的 `Native*DataTest` 与 `offscreenCamp/headlessD2GS`：只在合法 1.10f MPQ 下验证真实表行和表现，不能把 1.14d fixture 当 golden。

## 四层验收标准

每个技能或行为族在本表中标记完成前，必须具备：

1. **Native 数据层**：Skills/Missiles/States/SkillDesc/ItemStatCost 行、函数号、参数和版本来源。
2. **纯逻辑层**：等级、mana、前置、ToHit、伤害、元素、状态、RNG 和边界值。
3. **ECS/D2GS 层**：实体所有权、tick 顺序、碰撞、死亡、删除、快照、断线重连和幂等。
4. **真实资源层**：1.10f MPQ 的离屏营地/专项技能验证；涉及多人时增加双客户端。

未通过任一层时，状态只能是“部分实现/待审计”，不能标记为完成。

## 当前执行顺序

1. Amazon 30 个技能逐行建立 Native 数据断言；exact-ID 行为注册表及 Jab/Impale/Fend
   的首轮动作/目标流、Impale 耐久/数量及 ToHit/SrcDam/Calc 边界已完成，下一步补齐
   失手/死亡、重连和真实 1.10f MPQ 验收。
2. 把 dark-magic 的 exact-ID/fail-closed、导弹生命周期、状态来源、checkpoint 断言移植成 riiablo Java 测试模板。
3. 对照 D2MOO 1.10f 复核 Amazon 的 ToHit、SrcDam、Calc、武器/弹药、穿透、元素和多目标规则。
4. 依次审计 Paladin Aura、Necromancer summon/golem、Assassin trap、Sorceress missile/state。
5. 最后审计 Barbarian/Druid 的技能树全量分支，并运行七职业聚合矩阵。

## 参考入口

- riiablo 技能 ID：`core/src/main/java/com/riiablo/engine/server/skill/SkillId.java`
- riiablo 职业技能范围：`core/src/main/java/com/riiablo/CharacterClass.java`
- riiablo 共享校验：`core/src/main/java/com/riiablo/engine/server/skill/NativeSkillResolver.java`
- dark-magic 行为说明：`F:/3rd_src/dark-magic/internal/content/d2legacy/lua/d2legacy/README.md`
- dark-magic 研究和证据：`F:/3rd_src/dark-magic/docs/research/SKILLS_STATES_AND_MISSILES.md`
- D2MOO 基线：`F:/3rd_src/D2MOO/source/D2Game/src/SKILLS`
