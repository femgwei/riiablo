# 技能移植对照表：riiablo ↔ dark-magic

更新时间：2026-09-28

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

现有职业专项测试数量（按文件名统计）为：Amazon 4、Assassin 2、Barbarian 7、Druid 18、Necromancer 16、Paladin 10、Sorceress 11。这个数量只能表示已有测试入口，不能表示技能已经通过原生行为验收。

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
  这仍只完成 ECS/纯逻辑层的一轮门槛，真实 MPQ 动画和重连仍待验收。
- 首批门槛：30 个 Amazon 行逐行检查 `charclass/reqskill/reqlevel/mana/InTown/SrvStFunc/SrvDoFunc/武器限制/弹药/quantity/ToHit/SrcDam/EType/Calc1..4`，再做固定种子、多目标、失手、墙碰撞、死亡和重连测试。

### Sorceress

- riiablo 入口：`SorceressSkills.java`、`MissileDamageResolver`、`SkillExecutor`。
- 当前测试：防御状态、火焰区域、投射物、Blizzard、Frozen Orb、Meteor、Static Field、Thunder Storm、Frost Nova 等专项测试。
- dark-magic exact-ID 对照：Fire Bolt(36) `missile.straight`；Frozen Armor(40)、Shiver Armor(50)、Chilling Armor(60) `state.self-timed`；Ice Blast(45) `missile.straight-freeze`；Fire Ball(47) `missile.straight-impact-area`；Nova(48) `missile.radial`；Enchant(52) `state.targeted-timed`；Teleport(54) `movement.point-relocate`；Glacial Spike(55) `missile.straight-impact-area-freeze`。
- 重点借鉴：把“投射物移动/接触/伤害”和“爆炸表现实体”分开；把冰甲的防御、持续时间、触发条件、冻结/减速和状态替换放入同一来源生命周期。
- 版本风险：dark-magic 的 Fire Ball/Nova/Ice Blast 数值来自 1.14d；riiablo 必须用 D2MOO 1.10f 的函数和数据重新确认。

### Necromancer

- riiablo 入口：`NecromancerSkills.java`、`SummonedPetSystem`、`CorpseConsumption`。
- 当前测试：诅咒、Bone Armor/Bone Wall、Poison Dagger/Nova、Corpse/Poison Explosion、Skeleton/Golem/Revive 及 AI 集成测试。
- dark-magic exact-ID 对照：Amplify Damage(66)、Weaken(72) `state.point-area-curse`；Raise Skeleton(70)、Raise Skeletal Mage(80)、Revive(95) `summon.targeted-corpse`；Clay(75)、Blood(85)、Iron(90)、Fire Golem(94) `summon.golem`。
- 重点借鉴：尸体消费和召唤创建必须是一次权威事务；PetType 上限、owner/source、Iron Golem 的物品来源、召唤物替换、断线/换图/死亡清理都要进入测试。
- riiablo 已有较多对应实现，但应逐项对照 dark-magic 的“创建前验证 → effect tick 再验证 → 成功后消费/替换”顺序。

### Paladin

- riiablo 入口：`PaladinSkills.java`、`AuraManager`、`AuraEcsSystem`。
- 当前测试：Aura 数据、Resistance/Support/Resource/Special Aura 集成、Blessed Hammer、Fist of the Heavens、Paladin melee。
- dark-magic exact-ID 对照：Might(98)、Resist Fire(100)、Thorns(103)、Defiance(104)、Resist Cold(105)、Blessed Aim(108)、Resist Lightning(110)、Vigor(115)、Salvation(125) `aura.selected-party-stat`；Prayer(99)、Cleansing(109)、Meditation(120) `aura.selected-party-periodic`；Redemption(124) `aura.selected-corpse-periodic`。
- 重点借鉴：aura source 身份、右键选择、同级目标稳定排序、半径筛选、pulse 计划、资源不足不改变目标、取消/替换/重连清理。
- 这是 riiablo 下一批最值得迁移 dark-magic 测试结构的职业之一，尤其是 aura 优先级和多人快照。

### Assassin

- riiablo 入口：`AssassinSkills.java`、`AssassinTrapSystem`、`NativeTrapSystem`。
- 当前测试：`AssassinMartialArtsTest`、`AssassinSkillSpecializationTest`，以及 Native Trap/Object/Fire 测试。
- dark-magic exact-ID 对照：Fire Blast(251)、Shock Web(256)、Blade Sentinel(257)、Charged Bolt Sentry(261)、Wake of Fire Sentry(262)、Blade Fury(266)、Lightning Sentry(271)、Wake of Inferno(272)、Death Sentry(276)、Blade Shield(277)，统一为 `trap.assassin-family`。
- 重点借鉴：精确 ID 白名单、helper missile 解码、陷阱落地/替换/数量上限、Blade Sentinel 巡逻、Death Sentry 尸体事务、Blade Shield 周期武器效果。

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
