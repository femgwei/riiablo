# 技能移植对照表：riiablo ↔ dark-magic

更新时间：2026-10-02（交接快照：Assassin trap owner lifecycle / projectile completion）

## 2026-10-02 Assassin Fire Trauma（ID 251）行为增量

- 按 D2MOO `MISSMODE_SrvHit36_MissileInAir` 对齐：`bomb in air`（本地
  `pSrvHitFunc=36`）碰到单位时 fail-closed，不在飞行中直接造成伤害；只有墙体/地图的
  null-hit 才通过 `HitSubMissile=bomb on ground` 转成地面炸弹。
- 按 D2MOO `MISSMODE_SrvHit03_ExplosivePotion_BombOnGround` 与
  `MISSMODE_SrvHit44_ExplodingJavelin` 对齐：`bomb on ground`（`pSrvHitFunc=3`、
  `Vel=0`、`Range=5`）在原生寿命到期前忽略单位碰撞，到期时按技能
  `aurarangecalc=par1`、本地 `Param1=5` 做一次中心范围火焰伤害，并生成一次
  `ExplosionMissile=bomb explosion` 视觉子弹；source 随后删除，避免重复爆炸。
- generic Fire Trauma 仍由 `ServerSkillSystem` 读取 `srvmissile=bomb in air`，由
  `MissileDamageResolver.initializeSkill` 保留 `EType=fire`、`EMin/EMax`、
  `EDmgSymPerCalc`（Shock Field/Death Sentry/Charged Bolt Sentry/Lightning Sentry/
  Wake of Fire Sentry/Inferno Sentry）和施法时火焰快照；Fire Trauma 本身没有 `SrcDam`。
- 新增 `AssassinSkillSpecializationTest` 两项回归：精确锁定 air/ground/explosion 行、
  协同快照，以及地面炸弹单位接触不直伤、半径外目标不受伤、到期只爆炸一次。
  `./gradlew.bat :core:test --tests com.riiablo.engine.server.AssassinSkillSpecializationTest`
  已通过（24 tests）。
- 仍未标记为四层完成：真实 1.10f MPQ 墙体 null-hit、跨房间/重连、双客户端动画与伤害
  观感尚未验收；本次只完成 D2MOO 对照和权威 ECS 行为层。

## 2026-10-02 Assassin trap shot synergy snapshot

- 对照 dark-magic `owned-target-records-and-localized-*-synergies-partial`，修正
  `AssassinTrapSystem` 的普通陷阱射击、Charged Bolt Sentry、Inferno Sentry 和 Blade
  Sentinel 初始化：现在把施法者硬点技能等级 resolver 传给
  `MissileDamageResolver.initializeSkill`，不会在陷阱攻击阶段把 `EDmgSymPerCalc`
  静默当成零。
- Wake of Fire Sentry 的 `SrvDo125` maker 生成的两枚 `wake of destruction` 子导弹也
  继承同一份 owner synergy snapshot；owner 仍由 maker 的 `damageOwnerId` 保留。
- `AssassinSkillSpecializationTest.chargedBoltSentrySrvDo017EmitsNativeBoltBurst`
  新增参考快照比对，锁定 Charged Bolt 的 `lightmaxdam` 与带硬点协同的原生公式一致；
  Assassin 专项测试目前 24 项全部通过。
- 该增量仍属于纯逻辑/ECS 层；真实 MPQ 双客户端的陷阱伤害、重连和动画观感仍需单独 gate。

## 2026-10-02 Assassin trap owner lifecycle / sentry retirement

- 对照 dark-magic `trap_skill_real_test.go` 与 `trap_skill_test.lua` 的 sentry shot-budget
  语义：陷阱达到 `maxShots` 后只删除 trap/controller；已经发出的 projectile 必须继续完成
  移动、碰撞和伤害，不能因为 controller 被回收而变成无主导弹。
- 对照 D2MOO `AITHINK_Fn101_AssassinSentry`、`AITHINK_Fn104_DeathSentry` 以及
  `MISSILES` 命中路径，保留 trap entity 作为 `Missile.ownerId`（原生 ownership/表现），
  同时记录施法者到 `Missile.damageOwnerId`。碰撞时若 trap 已 inactive，则回退到
  `damageOwnerId` 进行敌我判断、属性/协同快照、难度和战斗结算；trap 仍存活时不改变原有
  owner 行为。
- `AssassinTrapSystem` 的普通陷阱、Charged Bolt、Inferno、Blade Sentinel 导弹均记录
  `damageOwnerId`；`MissileCollisionSystem` 集中使用有效 combat owner，避免 sentry
  删除后飞行中的导弹丢失伤害关系。
- 新增回归 `AssassinSkillSpecializationTest.sentryProjectileCompletesAfterTrapShotBudgetRetiresController`，
  覆盖“一发达到上限 → controller 删除 → 飞行导弹仍命中目标”；定向用例和完整
  `AssassinSkillSpecializationTest` 均通过（24 tests）。
- 新增回归 `AssassinSkillSpecializationTest.playerDepartureRemovesOwnedAssassinSentry`，
  移植 dark-magic `player_departure_removes_owned_sentries`：玩家删除采用 Artemis 延迟
  entity flush，下一 ECS tick 由 `SummonedPetSystem` 观察 owner 缺失并清理陷阱；该语义
  与“飞行导弹保留 damageOwnerId”相互独立。
- 当前仍未完成真实 1.10f MPQ 墙体/null-hit、跨房间/重连、双客户端动画与伤害观感 gate；
  该项是 ECS/Native 行为层对照完成，不等于四层验收完成。

## 2026-10-02 Assassin Blade Fury (`SrvSt26`/`SrvDo048`) 行为增量

- `ServerSkillSystem` 已接入原生 Blade Fury 发刃路径：只读取本地 1.10f
  `srvmissilea=bladefragment1`，每次 `SkillDoEvent` 最多创建一枚权威导弹，按
  `Param4=5` 保留发刃间隔，并在发刃时扣除一次技能 mana。
- `MissileDamageResolver.initializeSkill` 继续负责 `SrcDam=96` 的武器伤害快照；导弹保留
  owner、技能 ID、技能等级和 `damageSnapshot`，不会落入 generic `SrvMissileA/B` 双发路径。
- `Actioneer` 已实现 `SrvSt26` 的 `StateId.INFERNO` 生命周期：首次启动约 21 帧，持续输入刷新
  到 7 帧；cast 入口使用本地 1.10f `startmana` 门槛，起始 cast 不提前扣除每刃 mana。
- 新增 `AssassinSkillSpecializationTest` 的 Blade Fury 回归，覆盖单枚发刃、`Param4` 节流、
  SrcDam 快照、逐刃资源消耗及 start-mana 门槛；定向
  `AssassinSkillSpecializationTest` 已通过。
- 仍未标记为四层完成：真实 1.10f MPQ 双客户端时序、SQ held-input 重入、墙体/碰撞、重连恢复、
  Blade Fury 两个 helper missile 的完整视觉/伤害差异仍需后续 gate。

## 2026-10-02 Assassin trap exact-ID Native gate

- 新增 `AssassinSkillSpecializationTest.auditDarkMagicTrapExactIdsAgainstLegacyRows`，
  按 dark-magic `trap.assassin-family` 的 10 个 exact-ID（251/256/257/261/262/266/
  271/272/276/277）锁定本地 1.10f Skills.txt 名称、`SrvDoFunc`、陷阱 `pettype`，以及
  Blade Fury/Blade Shield 的服务端导弹行。
- 1.10f 与 dark-magic 1.14d 的显示名不同（Fire Trauma/Fire Blast、Shock Field/Shock Web），
  测试以 exact-ID 和原生函数为权威，不复制 1.14d 数值或名称。
- 本次先增加 Native 对照门槛；Shock Field (`SrvDo043`)、Blade Fury (`SrvDo048`) 和
  Fire Trauma (`251`) 已完成纯 ECS 行为层，但 10 条技能仍未标记为四层完成。下一步是
  把陷阱射击协同、owner 生命周期和真实 1.10f MPQ gate 接到同一组验收。
- 提交：`2f0634af test: lock Assassin dark-magic trap exact IDs`，已推送 `origin/master`。

### 2026-10-02 Shock Field (`SrvDo043`) 行为增量

- `ServerSkillSystem` 现在将 `SrvDo043` 从 local `monstersOnly` 过滤中放行，并按本地
  1.10f `prgcalc1` 计算 progressive 导弹数量、按 `aurarangecalc` 计算散布半径；每枚
  导弹只取 `srvmissilea=shock field in air`，保留 owner、技能等级和 lightning damage
  snapshot。
- 新增 `shockFieldSrvDo043UsesProgressiveCountAndScattersAuthoritativeMissiles`，验证
  level 1 的 6 枚导弹、独立落点方向和伤害快照；完整
  `AssassinSkillSpecializationTest` 已通过。
- 这仍是纯 ECS/Native 行为层完成，不代表真实 MPQ 双客户端、墙体、重复命中和重连层已完成。
- 提交：`d544b8b9 feat: port Assassin Shock Field scatter`，已推送 `origin/master`。

## 2026-10-01 Amazon Decoy / Valkyrie 真实 MPQ 召唤 gate

- [x] 新增 `headlessAmazonSummon`，按 `Skills.txt` 的 `summon/pettype/petmax`
  验证 Decoy/Dopplezon(28) 与 Valkyrie(32) 的权威创建、owner、技能等级、被动标志、
  持续时间及 `Valkyrie` 状态；owner/observer 初始快照和 observer 重连均保持同一
  summon entityId。
- [x] Decoy 使用真实 1.10f MPQ 双客户端 gate 通过：`durationFrames=750`、
  `passive=true`；Valkyrie 使用 level 7 gate 通过：`durationFrames=0`、
  `passive=false`、`valkyrieState=true`。
- [x] 修复 headless COF fallback 与真实 CastSkillDoEvent 竞态：Decoy 的无 keyframe
  回放等待窗口从 200ms 调整为 1s，避免同一次施法创建两个 Dopplezon，导致旧实体在
  重连前被 PetMax 替换。网络实体快照同时改为每个 recipient 独立 ByteBuffer，避免
  共享广播缓冲区在同帧重定位时造成观察者丢包；断线删除切回 D2GS 应用线程。
- [x] 验证命令：
  `./gradlew.bat :server:d2gs:headlessAmazonSummon -PamazonSummonSkill=28 -PamazonSummonSkillLevel=5 -PamazonSummonTimeout=20 --no-daemon`
  与 `... -PamazonSummonSkill=32 -PamazonSummonSkillLevel=7 ...`，两次均为
  `BUILD SUCCESSFUL`；核心 Amazon 三项定向回归同样通过。
- [ ] 仍需把相同的真实 MPQ 双客户端门槛扩展到 Amazon 其余尚未覆盖的 exact-ID 行；
  不以本 gate 覆盖用户已验证的 Amazon 伤害/公式。

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

### 当前移植口径（交接必读）

当前 43/43 exact-ID 已建立 riiablo 行为对照或测试映射，但不是 43/43 完成四层验收。
最高等级证据集中在 Sorceress Fire Ball/Nova、Necromancer Poison Nova、Paladin 20 Aura
和 Druid vine 的部分双客户端链路；Amazon 30 行仍是下一优先级，且 dark-magic 没有
Amazon exact-ID 配置。其他 agent 的未提交 Amazon/Assassin/Item 修改必须保留，接手者
只能提交自己明确修改的文件。最新交接、工作区边界和测试命令见
`docs/codex-handoff-2026-09-30.md` 与 `docs/current-chat-ownership.md`。

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
  `CombatResult`。
  Power Strike/Charged Strike 已按 D2MOO 的 `SrvDo002/SrvDo011` 接入成功命中扣武器耐久，
  并由 Amazon 技能矩阵锁定该耐久行为族。
  两者的 `SrvSt06` 也已改为一次性预计算物理+闪电包，keyframe 不再重新掷骰或丢失元素伤害。
  `AmazonMeleeSkillLifecycleTest` 现在还锁定原生 4% 耐久概率（成功命中才扣一点）以及
  关键帧前目标死亡时保留记录到动画结束、跳过伤害/耐久并清理 `Casting` 的边界。
- 已建立真实 MPQ 双客户端入口 `headlessAmazonMelee`，当前允许 Jab(10)、Power
  Strike(14)、Impale(19)、Charged Strike(24)、Fend(30)、Lightning Strike(34)。六项均已
  逐个通过真实伤害、owner/observer 一致性与 observer 重连生命值门槛；Lightning Strike
  还确认链式导弹在客户端观察到。这不等于其余 Amazon exact-ID 行已经完成。
- 真实 Jab death gate 已通过 `-PamazonMeleeTargetDeath`：首个关键帧前目标死亡后，双方
  客户端保持死亡状态，后续 Jab 记录不产生额外伤害，observer 重连不恢复生命。miss、
  quantity/durability 仍以 ECS/D2MOO 测试为主；新增的 `-PamazonMeleeExpectMiss` 已通过
  真实高防/被动闪避 gate，双方生命和重连快照保持不变。
- 真实 MPQ Impale quantity gate 已通过：D2MOO `Calc2=par6-dm34` 在 level 20 的
  `25%` 路径命中后将装备 `jav` 从 `quantity=16` 降为 `15`，`durability/maxdurability`
  保持 `20/20`；owner/observer 目标生命一致，observer 重连后服务端资源仍为
  `15/20/20`。夹具读取权威装备的 quantity、durability、maxdurability 及 native base
  标志，避免把客户端快照误当成资源真值。随后新增真实 MPQ 非堆叠 `spr` fixture，
  确认 `stackable=0,nodurability=0` 时 quantity 缺失保持 `-1`，Calc3 durability
  从 `20` 降为 `19`，且 owner/observer 与重连资源一致；因此 Calc2/Calc3 两条资源
  路径均已有真实双客户端 gate。
- 首批门槛：30 个 Amazon 行逐行检查 `charclass/reqskill/reqlevel/mana/InTown/SrvStFunc/SrvDoFunc/武器限制/弹药/quantity/ToHit/SrcDam/EType/Calc1..4`，再做固定种子、多目标、失手、墙碰撞、死亡和重连测试；当前下一项是弹药耗尽/补充和剩余 Amazon 导弹/区域行为。

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
- 2026-09-30 已完成 Aura 来源/资源/快照首轮 ECS 回归：不同 skill 共享同一 state
  只保留一个 winner；高等级来源取消、死亡或离开范围后按 native `perdelay + 1`
  短层过期并在下一 pulse 重新选主；同一 caster 换 skill 不残留旧 layer；
  `StateSerializer` 重连快照会替换 stale source/skill layer。对应测试包括
  `AuraManagerPulseTest` 的 shared-state replacement、unfunded winner、damage Aura
  self-layer 和有效目标扣蓝场景，以及 `AuraEcsScenarioTest` 的范围恢复/重连快照场景。
- 2026-09-30 按 D2MOO `SrvDo065/SrvDo066/SrvDo082` 固定 pulse 资源事务：无合法目标、
  无尸体或 mana 不足时不扣 mana、不取消已选 Aura；Damage Aura 的 self/passive layer
  不单独触发扣蓝，只有非 self 的有效伤害目标才使 pulse 变为 useful。当前剩余门槛是
  真实 1.10f 双客户端对 Aura 图标、动画、旧客户端 StateP 兼容和多玩家表现的验收。
- 2026-09-30 已增加独立真实 MPQ 双客户端入口 `headlessPaladinAura`，覆盖右键 Aura
  selection、同房间 party ally 的 owner/target `StateP`、source/skill 元数据、observer
  断线重连以及重新入队后的快照恢复。当前回归覆盖 Might、Prayer、Resist Fire、
  Cleansing、Fanaticism、Meditation、Redemption、Salvation；Fanaticism 与 Redemption
  的 self-only 分支以及 Redemption 的 targetless self-state 也纳入门槛；
  不能用区域技能入口代替该流程。为支持 native 快照语义，Aura 对重新创建的 ECS
  entity 会立即重发布当前 winner，不必等待下一次 `perdelay`。
- 随后补充跨区域撤销断言：owner 从 Act I 进入 Blood Moor、observer 保留在原区域时，
  Might、Prayer、Salvation 的 target state 都在 `perdelay + 1` 生命周期内消失，日志记录
  `paladin_aura_cross_area_pass`，未发现跨 `Map.Zone` 泄漏。旧客户端 Aura 图标、动画
  和范围表现仍需单独的客户端观测。
- `headlessPaladinAuraRegression` 已扩展为 20 个已注册 Paladin Aura 技能的完整清单，
  并按 native `affectsParty`/`affectsEnemy` 区分 party、hostile target-state 与
  self-only/targetless 分支；全清单运行通过。Holy Freeze、Conviction 已使用 deterministic
  hostile monster fixture 验证 target-state、source/skill、重连和跨区域撤销；怪物免疫、
  `noAura`、不可攻击目标等组合仍需独立过滤矩阵。
- 2026-09-30 已补充 `AuraEcsScenarioTest.hostileAuraFilterMatrixMatchesD2MooBossPrimeNoAuraAndAttackabilityRules`。
  该矩阵按 D2MOO `sub_6FD0FA00/sub_6FD0FE80` 锁定：Conviction（SrvDo066，
  `bCheckMonAuraFlag=0`）命中 Boss、Prime Evil 与 `noAura` 怪物，但拒绝
  `MonStats2.isAtt=false` 或 `noSel=true`；Holy Freeze（SrvDo081）同样不把
  `noAura` 当作过滤条件，再额外按 `MonStats.coldeffect` 拒绝冷免疫目标。后续只需把
  真实 MPQ 双客户端的过滤结果和 Aura 图标/动画观测接到同一清单，不应把 `noAura`
  规则泛化到 SrvDo066/081。
- 同日将过滤 fixture 接入 `headlessPaladinAura`：Conviction=123 的真实双客户端门槛
  现在同时验证 Boss、Prime Evil、`noAura` 命中以及不可攻击/不可选中拒绝；
  Holy Freeze=114 额外验证 `noAura` 命中和 `coldeffect` 冷免疫拒绝。fixture 使用每个
  实体独立的 `MonStats/MonStats2` 副本，避免共享 Excel 行造成串扰；SrvDo081 的短
  target-state 生命周期也已纳入重连/跨区等待节奏。
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
- 2026-10-02 又锁定了 10 个 dark-magic exact-ID 到 1.10f Native 行的名称、函数和关键字段；
  这只是数据层门槛，不代表 `trap.assassin-family` 已完成四层验收。陷阱射击协同与
  owner 生命周期已有 ECS 回归；下一步仍是把真实 1.10f MPQ gate 接到同一组验收。

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
- 真实 1.10f 双客户端专项已完成三种 vine 的共享实体、`VINE_BEAST` 和 reconnect；
  `Plague Poppy`（222）另已通过 `Vine Attack` 根导弹 runtime index 474 与
  `plague vines trail` runtime index 475。`Cycle of Life`/`Vines`（231/241）按 D2MOO
  `SrvSt63_Corpse_VineCycler` 标记为非 projectile 分支；当前已在 Actioneer 技能开始阶段
  选择合法尸体、同步 `CORPSE_NOSELECT`，并以 vine 的玩家 owner 在尸体位置创建
  `VineCycler` 的 `SrvMissileA`（`vine recycler delay`），`DruidVineCorpseCyclerTest`
  锁定 owner/坐标/skillId/skillLevel 和重复施放防重；`D2GSHeadlessClient
  --require-vine --vine-skill 231/241` 已通过真实双客户端的尸体目标、导弹共享和
  observer reconnect 门槛。
  D2MOO 核对确认该 delay missile 只按 `Range=47` 帧结束，不消费尸体；客户端
  `pCltDoFunc=51` 的第 20/45 帧 `recycler vine`/`recycler explosion` 已按原生参数补齐。
  由于 D2MOO trail 行没有直接毒素字段，也不把 poison state 作为已验证结果。
- `plague vines trail` 的 `SrvHit50` 已按 D2MOO 的 `Hit delay=15` 窗口和
  `Range + level*LevRange` 生命周期注册；其 `pSrvDmgFunc=0`，不擅自推断毒素伤害。

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

### 2026-10-01 Amazon 弹药边界增量

- `BOWQ/XBOQ` 的 `item_replenish_quantity` 零数量保留与恢复已完成纯逻辑/ECS 门槛，
  对应测试为 `NativeAmazonAmmoReplenishTest`。
- 普通箭袋耗尽移除行为未改变；真实 MPQ 双客户端 bow/Fire Arrow gate、目标命中和
  observer reconnect 已通过，装备切换与 D2S `item_replenish_quantity` 持久化仍是
  独立限制项。

### 2026-10-01 Amazon Fire Arrow 真实资源增量

- `headlessAmazonBow` 已完成真实 MPQ 的 Fire Arrow 导弹共享、箭袋 `1->0`、可恢复
  物品保留、恢复和 observer reconnect；测试桥接注入的 `item_replenish_quantity` 不
  代表 D2S 序列化已完成。
- Fire Arrow 目标命中/伤害已通过：门槛等待实际模拟碰撞而非只看到导弹创建，并在
  owner/observer 两端观察到目标生命下降；测试 fixture 显式提供弓的 ToHit/物理伤害，
  不改变生产技能公式。D2S `item_replenish_quantity` 持久化限制仍单独保留。

### 2026-10-01 Amazon Cold Arrow 真实 gate

- `headlessAmazonBow` 现支持 `Cold Arrow(11)`，并通过真实 MPQ 双客户端导弹共享、
  命中伤害、箭袋消耗/恢复与 observer reconnect。
- owner/observer 两端均观察到目标 `COLD(state=11)`，没有误判为 `FREEZE`；该断言对应
  D2MOO 的 `SrvDmgFunc=1` 物理转换和独立冷状态应用链。
- 这一步只扩展测试入口和断言，没有用 dark-magic 或测试逻辑覆盖用户已验证的 Amazon
  生产技能公式；D2S 恢复属性持久化仍需另行处理。

### 2026-10-01 Amazon Ice Arrow Boss gate

- `Ice Arrow(21)` 已通过真实 MPQ 双客户端导弹/命中/箭袋/reconnect gate。
- D2MOO 的 `SrvDmgFunc=2` 先把冷长度转成冻结长度，但 Boss 在
  `SUNITDMG_ApplyFreezeState` 中只能被冷却；riiablo 现在对 Boss 目标应用 `COLD(state=11)`
  且明确禁止 `FREEZE(state=1)`，普通目标冻结语义不变。
- 为避免动态单位网格把贴近玩家的目标搬走，bow gate fixture 移除目标动态 footprint；
  这是测试稳定化，不是生产规则覆盖。

### 2026-10-01 Amazon Exploding Arrow 真实范围子导弹 gate

- `Exploding Arrow(16)` 已按 D2MOO `MISSMODE_SrvHit04_ExplodingArrow_FreezingArrow_RoyalStrikeMeteorCenter`
  核对：父箭命中后从 `HitSubMissile[]` 创建 `explodingarrowexp2`，子导弹以原生半径走
  `SrvDmgHitHandler` 范围伤害；父箭不重复携带爆炸火伤害。
- `headlessAmazonBow -PamazonBowSkill=16` 通过真实 1.10f MPQ 双客户端 gate：父箭和
  `explodingarrowexp2` 在 owner/observer 共享、目标生命从 `1,000,000` 降至 `999,850`，
  箭袋耗尽/恢复和 observer reconnect 均通过。
- 断言不是只看父箭创建：gate 同时要求 HitSubMissile 子实体在两端出现且目标实际掉血；
  日志确认 `srv=explodingarrow`、`pSrvHitFunc=4`、`HitSubMissile=[explodingarrowexp2]`、
  火焰技能字段 `fire:2..6`。
- 该步只扩展验证入口并复用已有 `spawnAmazonExplosion`，没有用 dark-magic 行为覆盖
  用户已验证的 Amazon 生产公式；D2S 可恢复属性持久化限制仍独立保留。

### 2026-10-01 Amazon Freezing Arrow Boss 范围子导弹 gate

- `Freezing Arrow(31)` 按 D2MOO `SrvHit04` 和 `HitSubMissile[0]=freezingarrowexp3`
  核对；爆炸子导弹携带冷伤害/冻结包，父箭与范围子导弹分别参与命中处理。
- 真实 MPQ 双客户端 gate 已通过：`freezingarrow` 父箭和 `freezingarrowexp3` 子导弹在
  owner/observer 共享，目标生命 `1,000,000 -> 999,644`，两端观察到 Boss 例外的
  `COLD(state=11)+SHATTER(state=107)`，没有错误建立 `FREEZE(state=1)`；箭袋耗尽、恢复
  和 observer reconnect 也通过。
- 这是 Boss 目标门槛，符合 D2MOO `SUNITDMG_ApplyFreezeState` 对 Boss/Unique/Hireling
  的冻结转冷规则。随后修正了 `resolveColdShatterDeath` 的错误二次判定：SHATTER 只由
  `ApplyColdState` 在冷包应用时决定，冻结包不能在 DeathEvent 边界强制生成 SHATTER。
  普通怪物的 `FreezingArrowDeathOrderTest` 现已通过。

### 2026-10-01 Amazon Immolation/Guided Arrow 真实导弹 gate

- `Immolation Arrow(27)` 已按 D2MOO `MISSMODE_SrvHit09_ImmolationArrow` 验证父箭、
  `immolationfire` 圆形持续区域、两端共享、后续 tick 伤害和生命周期删除一致；
  箭袋消耗/恢复与 observer 重连也已通过。
- `Guided Arrow(22)` 已按 D2MOO `MISSMODE_SrvHit10_GuidedArrow_BoneSpirit` 扩展
  `headlessAmazonBow`，确认 `guidedarrow` 在 owner/observer 共享、锁定目标实际掉血、
  箭袋 `1->0`、可恢复箭袋和 observer 重连保持。
- `AmazonSkillSpecializationTest` 定向回归通过。Guided Arrow gate 仍会记录
  `Actioneer` 对 `SrvDoFunc=10` 的兼容日志，但权威 `ServerSkillSystem` 已完成导弹创建；
  该日志和旧客户端动画委派仍列为后续收尾项。
- 当前下一项：补齐 Multiple Shot/Strafe 的多目标穿透、墙碰撞和旧客户端动画门槛，
  随后继续其他职业的 exact-ID 对照。
- 本轮先补齐 `ServerSkillSystemTest.srvDo008UsesIntegerDeltaForDiagonalLaneTargets`：
  用 D2MOO 的整数 caster→target delta 和垂直 lane halving 锁定斜向中心/外侧箭方向；
  `MissileNativePolicyTest` 同时确认跨导弹共享命中集合不会重复命中同一目标。真实动态
  多目标 gate 仍待稳定夹具，不将单位重定位实验计入完成度。
- `MissileNativePolicyTest` 另锁定 1.10f Amazon 箭行的 `Collision=false + SrvDoFunc=1`
  仍必须进入 swept unit collision，以及 `CollideType=3` 使用 missile-barrier mask；
  Lightning Fury 墙体 gate 复跑通过，墙后目标未被选中且未掉血。

### 2026-10-01 Amazon Multiple Shot / Strafe 对照完成

- `Multiple Shot(12)`：D2MOO `SrvDo008` 的 `Calc1/Calc3`、整数垂直 lane、
  `SrvMissileA/B` 选择和一次施法共享命中集合已与 `ServerSkillSystem` 对齐；真实
  MPQ gate 在 level 20 观察到 21 枚权威导弹，owner/observer 实体一致，目标掉血，
  箭袋只扣一次并可重连恢复。夹具使用 caster-target 4f 的开放直线距离，避免 2f
  近距离扇形起点落入目标碰撞包络，也避免 8f 在 gate 窗口内尚未到达；测试现已加入
  “无真实目标掉血不得通过”的硬断言。
- `Strafe(26)`：D2MOO `SrvSt08` 的初始目标/箭数与 `SrvDo012` 的 Param1/2/3
  续发语义已核对；riiablo 维持每个 keyframe 一箭、目标流按范围排序、首箭单次
  弹药扣除。真实双客户端 gate 已观察多箭共享、伤害和 reconnect 一致。
- 这两项没有覆盖用户已验证 Amazon 数值公式；dark-magic 仅作为 exact-ID、事务和
  测试组织参考，数值/回调仍以 D2MOO 1.10f 和当前 MPQ 为准。
- `Actioneer` 现已显式接收 `srvstfunc=8` 与 `srvdofunc=12`，将 Multiple Shot/Strafe
  动画阶段委派给权威 `SkillDoEvent`，本轮真实 gate 不再出现 unsupported warning；
  headless fallback 仍只用于补齐缺失 COF 关键帧，不代表画面动画已完全收尾。多目标
  穿透和旧客户端观感仍列为后续 gate。

### 2026-10-01 Amazon Lightning Fury 真实 gate

- `Lightning Fury(35)`：D2MOO `MISSMODE_SrvHit20` 的 `HitPar[0]/HitPar[1]`、
  `AuraFilter=0xA783` 和 `HitSubMissile=furylightning` 已与当前 ECS 分裂路径对照。
- 真实 `headlessAmazonMelee` 现在接受 35 号技能，使用 `jav` fixture 验证根 javelin、
  同 RoomEx 第二/第三目标的 `furylightning` 子导弹 owner/observer 共享、三目标掉血和
  observer reconnect；没有覆盖或改写用户已验证的 Amazon 元素伤害公式。
- `MissileCollisionSystem` 让子导弹继承根导弹共享命中集合，阻止从根命中点出生时对
  根目标的第一帧重复命中；纯逻辑已有范围、分裂数量、静态阻挡、NoAura 和目标合法性
  测试。真实入口支持 `-PamazonMeleeSkillLevel`，按原生公式动态创建分裂目标；等级 1
  精确验证 2 枚、等级 10/20 精确验证 11 枚共享子导弹，所有目标均通过双客户端掉血与
  逐目标重连检查。生产排序已抽为最近距离优先 helper，并由纯 ECS 测试锁定；真实 MPQ
  gate 现在通过 D2GS 应用线程快照读取生产路径实际选中的目标 ID 和同 tick 距离，验证
  11 个目标唯一且按距离非递减排列。动态单位碰撞可能在后续 tick 重定位目标，因此不再
  用事后客户端坐标推断生产顺序。真实墙体 gate 也已通过：在 MPQ 地图中扫描可行走且被
  `FLAG_BLOCK_JUMP` raycast 阻挡的点，放置第 11 个候选后重新验证实际实体 ray；生产快照只
  选择前 10 个无遮挡目标，墙后目标不掉血，observer reconnect 保持初始生命。gate 硬断言
  blocked ID 不在快照中，不使用假墙或“超过上限”冒充墙体证据。当前 MPQ 观测确认两行导弹均为
  `CollideType=3`、`Collision=false`、`LastCollide=true`。

### 2026-10-01 Amazon Poison Javelin / Plague Javelin 真实毒云 gate

- [x] D2MOO `MISSMODE_SrvHit02_PoisonJavelin_PlagueJavelin` 已与现有 ECS 对照：Poison
  Javelin 命中创建 `poisonjavcloud`，Plague Javelin 创建 `plaguejavcloud`；两者均通过
  持久区域导弹应用 `POISON(state=2)`，并保留各自原生 cast delay。
- [x] `headlessAmazonMelee` 现接受技能 15/25。真实 1.10f MPQ 双客户端 gate 已分别通过：
  两端观察到对应 native 云团、目标实际掉血、目标两端出现 POISON 状态，observer reconnect
  后毒状态仍恢复；Plague 的多子云团由现有 `SrvHit02` fan-out 逻辑创建，未把 Poison 的
  单云团行误当作 Plague 行。
- [x] 本轮仅扩展验证入口和技能特定断言，没有覆盖用户已验证的 Amazon 伤害/持续时间公式；
  公式、云团生命周期和 cast delay 仍以 D2MOO 1.10f / 当前 MPQ 数据为准。
- 2026-10-01 复跑确认：Poison Javelin(15) level 20 的双端目标生命降至
  `999998.75`，Plague Javelin(25) 降至 `999994.0`；两项均通过云团、POISON 状态及
  observer reconnect 恢复门槛。Multiple Shot/Strafe 多目标夹具仍未纳入完成项。

## 参考入口

- riiablo 技能 ID：`core/src/main/java/com/riiablo/engine/server/skill/SkillId.java`
- riiablo 职业技能范围：`core/src/main/java/com/riiablo/CharacterClass.java`
- riiablo 共享校验：`core/src/main/java/com/riiablo/engine/server/skill/NativeSkillResolver.java`
- dark-magic 行为说明：`F:/3rd_src/dark-magic/internal/content/d2legacy/lua/d2legacy/README.md`
- dark-magic 研究和证据：`F:/3rd_src/dark-magic/docs/research/SKILLS_STATES_AND_MISSILES.md`
- D2MOO 基线：`F:/3rd_src/D2MOO/source/D2Game/src/SKILLS`
