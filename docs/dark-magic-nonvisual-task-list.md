# dark-magic 技能对照：非视觉任务清单

更新时间：2026-10-04

## 进度口径

- 本清单只统计不需要用户人眼对比原版画面的工作。
- 清单完成率按固定权重计算：`已完成权重 / 100`。
- 当前清单完成率：**80%**。
- 当前执行项：**DM-NV-12 d2client.dll 静态证据并入技能矩阵**。
- 原技能移植严格总进度仍记为约 **97%**；视觉验收单独保留，不会因本清单完成而自动记为 100%。
- 每项只有在源码依据、自动测试、真实 MPQ gate（适用时）、提交和推送全部完成后才计入百分比。

## 任务列表

- [x] **DM-NV-01（8%）Amazon Lightning Bolt(20) 真实命中/伤害链**
  - 定位 `lightningjavelin` 从 swept collision、ToHit 到 `SrvDmgFunc=12` 的中断点。
  - owner/observer 均观察同一目标实际掉血，并验证抗性、免疫、弹药和 reconnect。
- [x] **DM-NV-02（10%）Death Sentry 完整非视觉行为**
  - difficulty=1/2、多个地图 seed、多尸体/多发预算、爆炸范围、物理/火焰拆分。
  - Skill2 fallback、null-target 重选、多 trap/PetMax、重连期间不重复消费或复活。
  - 2026-10-04 完成：修正 headless MPQ 夹具在不同 seed 下将陷阱落入非活动 RoomEx 的问题，并固定目标/尸体的 native RoomEx 上下文；difficulty=0 的 seed=1/2/3、difficulty=1/2 的 seed=1 均通过双客户端权威导弹、尸体消费和 reconnect gate。
  - 相邻 RoomEx 双尸体顺序 gate 通过（第一候选消费、第二候选保留）；墙体 gate 通过（可见目标掉血、墙后目标保持 `100000`）；ECS `AssassinSkillSpecializationTest` 通过，覆盖物理/火焰拆分、爆炸内外半径、Skill2 fallback、null-target、非法/城镇/墙后目标过滤、尸体插入顺序、shot budget 与 controller 回收。
  - Death Sentry reconnect 对短生命周期/RoomEx 可见性允许空替换快照，但额外校验同一 trap 的同一尸体事务标记，拒绝重复消费或复活。
- [x] **DM-NV-03（8%）Wake of Fire Sentry 行为收口**
  - maker→wave 数量、两波方向/间隔、逐目标伤害、墙体/null-hit、预算和控制器到期。
  - 2026-10-04 完成：真实 MPQ 双客户端 maker(517)→双波(518)/reconnect gate 通过；ECS 覆盖逐路径伤害快照、正交波方向、off-axis 不命中、null-target 不消耗 shot budget、控制器预算回收，以及方向性静态墙体触发 null-hit 和双波回收。
- [x] **DM-NV-04（8%）Inferno Sentry 行为收口**
  - 通道 duration/pulse、方向追踪、逐目标伤害、抗性/免疫、墙体和 reconnect 生命周期。
- [x] **DM-NV-05（5%）Fire Trauma 一次性链收口**
  - air→ground→explosion 的一次命中、范围、共享命中集合和 reconnect 不复活父实体。
- [x] **DM-NV-06（5%）Blade Shield 非视觉边界**
  - 多目标、最后 pulse、到期静默、owner 死亡/离区、武器切换、PvP/Party/召唤过滤。
- [x] **DM-NV-07（10%）Sorceress 完整技能树行为审计**
  - 剩余 exact-ID、公式、状态、父子导弹、Hydra/区域技能及双客户端/reconnect 门槛。
- [x] **DM-NV-08（8%）Druid Vine/召唤/持续区域收口**
  - Vine Hit50 窗口、跨区跟随、召唤所有权/PetMax、持续区域命中去重和重连。
- [x] **DM-NV-09（8%）Necromancer 行为边界收口**
  - 尸体原子预留、召唤失败回滚、Golem/Revive、毒素、Bone Armor、Curse 来源优先级。
- [x] **DM-NV-10（5%）Paladin Aura/Conversion 边界**
  - 多来源稳定选择、目标范围、离区撤销、付费 pulse、Conversion 到期恢复。
- [x] **DM-NV-11（5%）Barbarian 战吼/尸体/精通边界**
  - 战吼覆盖刷新、尸体竞争、武器 exact-ID、多段攻击和目标失效处理。
- [ ] **DM-NV-12（8%）d2client.dll 静态证据并入技能矩阵**
  - 为每项技能记录 keyframe、SrvSt/SrvDo/SrvHit/SrvDmg、目标过滤和客户端函数证据。
- [ ] **DM-NV-13（5%）dark-magic 行为测试模式迁移**
  - effect-time revalidation、原子回滚、aura priority、所有权、尸体事务、stale/reconnect。
- [ ] **DM-NV-14（4%）扩展 exact-ID 行为注册框架**
  - 将非 Amazon 的目标、资源、状态、导弹链和生命周期逐步注册；未知行为 fail-closed。
- [ ] **DM-NV-15（3%）七职业自动覆盖审计工具**
  - 输出每个技能的 handler、ECS 测试、真实 gate、双客户端/reconnect 和静态证据状态。

## 完成记录

### 2026-10-04：DM-NV-01 完成

- D2MOO/MPQ 数据依据：`Lightning Bolt -> lightningjavelin`，`LastCollide=true`、`Vel=30`、`Range=25`、`SrvDmgFunc=12`；`DmgCalc1=dl12` 的物理转闪电快照由 `AmazonSkillSpecializationTest` 锁定。
- ECS 证据：`lightningBoltUsesNativeLightningJavelinDamageSnapshot`、`lightningBoltSnapshotHonorsMonsterResistanceAndImmunity`、`lightningBoltSweptCollisionAppliesSnapshotDamage` 均通过；新增测试确认 50% 闪电抗性降低伤害、100% 怪物闪电抗性保持免疫。
- MPQ 双客户端证据：
  `./gradlew.bat :server:d2gs:headlessAmazonMelee -PamazonMeleeSkill=20 -PamazonMeleeTimeout=25 --no-daemon`
  通过；owner/observer 目标生命 `1000000 -> 999799`，权威导弹 `entity=133 / missile=205`，标枪数量 `16 -> 15`，observer 重连后数量仍为 `15`、目标生命仍为 `999799`。
- 相关矩阵证据：`NativeAmazonSkillMatrixTest` 的 Lightning Bolt `decquant=true` 与 `NativeAmazonAmmoPolicyTest` 的 javelin ammo policy 均通过。
- 代码只扩展 gate 的标枪数量快照/重连断言和 Lightning Bolt 抗性单测，没有覆盖或改写用户已验证的 Amazon 伤害公式。
- 本项完成后累计完成率：**8%**。下一项切换为 **DM-NV-02 Death Sentry 完整非视觉行为**。

### 2026-10-04：DM-NV-02 完成

- 夹具修复：预先退役竞争 preset monster；将 Death Sentry 目标、尸体和落点固定到施法者/相邻 RoomEx 的 native 拓扑，避免 map seed 造成未激活陷阱或不一致尸体候选。
- MPQ 双客户端 gate：
  `areaSkill=276` 在 difficulty=0 的 seed=1/2/3、difficulty=1 的 seed=1、difficulty=2 的 seed=1 均通过；每次均观察到 owner/observer 相同的权威 corpse-explosion missile，并通过 reconnect 无重复消费/复活断言。
- 专项 gate：相邻 RoomEx 双尸体顺序 gate 通过；wall gate 通过，墙前目标生命降为 `0`，墙后目标保持 `100000`。
- ECS `AssassinSkillSpecializationTest` 全类通过（包括 `SrvDo055` 的尸体事务、物理/火焰半径拆分、Skill2 fallback、null-target、目标过滤、插入顺序、shot budget 和控制器回收）。
- 本项完成后累计完成率：**18%**。下一项切换为 **DM-NV-03 Wake of Fire Sentry 行为收口**。

### 2026-10-04：DM-NV-03 完成

- 真实 MPQ gate：`areaSkill=262` 通过，owner/observer 共享 `assassintrap`、maker `517` 和两枚 `518` 波导弹；reconnect 不复活已过期导弹。
- ECS 新增并通过 `wakeOfFireWavesCarryNativeDamageAndHitOnlyAlongTheirPaths`：两枚波导弹均携带原生伤害快照，正向路径目标掉血，偏轴目标不受伤。
- ECS 新增并通过 `wakeOfFireDoesNotConsumeShotBudgetWithoutAHostileTarget`：null-target 不创建 maker、不消耗 shot budget，控制器保留。
- ECS 新增并通过 `wakeOfFireWavesRespectAStaticBarrierOnTheirTravelDirection`：maker 水平移动仍能到达端点，波导弹在原生碰撞方向遇墙后触发 null-hit，墙后目标不掉血且波导弹被权威删除。
- `AssassinSkillSpecializationTest` 全类回归通过；本项完成后累计完成率：**26%**。下一项切换为 **DM-NV-04 Inferno Sentry 行为收口**。

### 2026-10-04：DM-NV-04 完成

- 真实 MPQ gate：`areaSkill=272` 通过；owner/observer 共享 Inferno 陷阱控制器和 `523` 通道导弹，重连期间短生命周期导弹正常过期，`stale=false`，未出现 `AssassinSentry` AI fallback 警告。
- ECS 新增并通过 `infernoSentryChannelCarriesFireDamageToTrackedTarget`：SrvDo095 通道导弹保存原生火焰伤害快照，目标在追踪射线上实际掉血。
- ECS 新增并通过 `infernoSentryChannelHonorsFullFireImmunity`：100% 火焰抗性目标保持满血，确认伤害快照经过原生抗性/免疫链。
- ECS 新增并通过 `infernoSentryDoesNotConsumeShotBudgetWithoutAHostileTarget`：null-target 不发射通道、不消耗 trap shot budget，控制器保留。
- ECS 新增并通过 `infernoSentryStreamStopsAtAStaticBarrierBeforeDamagingBehindTarget`：静态障碍在通道导弹首段之后触发 null-hit，墙后目标不掉血，导弹权威删除。
- `AssassinSkillSpecializationTest` 全类回归通过；本项完成后累计完成率：**34%**。下一项切换为 **DM-NV-05 Fire Trauma 一次性链收口**。

### 2026-10-04：DM-NV-05 完成

- 真实 MPQ gate：`areaSkill=251` 通过；owner/observer 共享 `385` air、`386` ground、`387` explosion 三段链，目标生命在爆炸链中归零；reconnect 后 `active=[]`、`ownerActive` 中旧实体仅正常过期，`targetWasDead=true`、`stale=false`。
- ECS 新增并通过 `fireTraumaAirNullHitCreatesOneGroundChildAndOneExplosion`：静态障碍触发 air 导弹 null-hit，严格生成一个 authoritative ground child，再由 ground 到期生成一个 explosion presentation child。
- `fireTraumaGroundIgnoresUnitContactAndExplodesOnceAtSkillRadius` 扩展为双目标半径断言：ground 在到期前不因单位接触提前爆炸，到期时同一伤害包对半径内多个敌对目标各命中一次，半径外目标不受伤，且不会重复生成 explosion。
- 原有 `fireTraumaUsesNativeAirGroundChainAndSkillSynergy`、`AssassinSkillSpecializationTest` 全类回归通过；本项完成后累计完成率：**39%**。下一项切换为 **DM-NV-06 Blade Shield 非视觉边界**。

### 2026-10-04：DM-NV-06 完成

- 真实 MPQ gate：`areaSkill=277` 通过；owner/observer 同步 `StateId.BLADESHIELD=158` 的持续状态和周期计数，reconnect 后状态仍为 `[158]`、`stale=false`。
- 现有 ECS 覆盖并通过：多目标与 25 帧 cadence、AuraRangeCalc 范围、town 静默、当前/相邻 RoomEx 扫描、非相邻/跨 zone 过滤、NativeUnitFlags 无效目标、状态到期/技能丢失、owner 死亡、武器耐久和目标护甲耐久。
- 新增并通过 `bladeShieldSkipsPlayersAndSummonedPetsAsFriendlyTargets`：无 PvP hostility 的其他玩家和友方召唤宠物均保持满血，确认目标过滤不会被普通坐标范围绕过。
- `AssassinSkillSpecializationTest` 全类回归通过；本项完成后累计完成率：**44%**。下一项切换为 **DM-NV-07 Sorceress 完整技能树行为审计**。

### 2026-10-04：DM-NV-07 Sorceress 完整技能树行为审计完成

- 对照 D2MOO/1.10f `Skills.txt` 和 `SkillSor` dispatch，将 Sorceress 技能 ID 36–65 全部纳入 `NativeSkillBehaviorRegistry` 的 exact-ID 声明；每行同时锁定 `SrvStFunc/SrvDoFunc`，未知或回调不一致时返回 `null`，不会按显示名误判。
- 审计中确认的关键原生回调包括：Inferno `SrvSt11/SrvDo19`、Telekinesis `SrvSt12/SrvDo21`、Chain Lightning `SrvDo26`、Energy Shield `SrvDo23`、Thunder Storm `SrvSt13/SrvDo29`、Hydra `SrvSt14/SrvDo144`；Blizzard/Meteor 共用 `SrvDo28`，Nova/Frost Nova 共用 `SrvDo22`，三种冰甲共用 `SrvDo18` 但按 exact ID 保持不同状态族。
- 登记了直线/多弹/径向/冻结/范围/持续区域/状态/被动/传送/Hydra 等行为族及其 1.10f 导弹名证据；没有改写用户已验证的 Amazon 技能公式或实现。
- 新增 `NativeSorceressSkillMatrixTest`：遍历 30 个 Sorceress 行，验证表中每个 ID 都有精确声明并与实际 `SrvSt/SrvDo` 一致；另验证篡改 Hydra 回调后 fail-closed。
- 验证：`./gradlew.bat :core:test --tests com.riiablo.engine.server.NativeSorceressSkillMatrixTest --no-daemon` 通过；既有 Sorceress 投射物、区域、状态、Blizzard/Frozen Orb/Meteor/Nova/Thunder Storm/Static Field 专项用例作为本轮对照证据保持通过。
- 本项完成后累计完成率：**54%**。下一项切换为 **DM-NV-08 Druid Vine/召唤/持续区域收口**。

### 2026-10-04：DM-NV-08 Druid Vine/召唤/持续区域收口完成

- 对照 D2MOO `SrvDo114/115/119`、`SrvSt63_Corpse_VineCycler`、`SrvDo130_VineAttack` 和 `MISSMODE_SrvHit50_PlagueVinesTrail`，复核三种 Vine 的 SumSkill 等级传递、`VINE_BEAST` 状态、所有权/PetMax、尸体预留、防重复施放和 trail 生命周期。
- `DruidVineCorpseCyclerTest` 已锁定 `CORPSE_NOSELECT`、owner missile、尸体坐标/技能等级、47 帧 recycler delay，以及 `HitDelay=15` 的 `SrvHit50` 窗口；该回调只负责时间门控，不直接造成毒伤，也不复用 `SrvHit16` 减速状态。
- 新增 `SummonedPetSystemTest.druidVineFollowsOwnerAcrossZoneBoundaryWithSkillSourceMetadata`，验证 Vine 跨 zone 跟随 owner 后仍保留 owner、PetType、来源 skillId 和 skillLevel 元数据；与既有 spirit aura 的跨区撤销规则分离。
- 真实 1.10f MPQ 双客户端门槛复跑通过：`./gradlew.bat :server:d2gs:headlessVine -PvineSkill=222 -PvineTimeout=90 --no-daemon`；结果包含 `vine_dual_pass` 和 `vine_reconnect_pass`，owner/observer 共享 Vine、`VINE_BEAST`、Vine Attack 根导弹和 trail，重连 `stale=false`。
- 定向回归通过：`DruidVineCorpseCyclerTest`、`DruidSummonIntegrationTest`、`SummonedPetSystemTest`、`NativeDruidSummonDataTest`、Druid Firestorm/Fissure/Storm 专项。
- 本项完成后累计完成率：**62%**。下一项切换为 **DM-NV-09 Necromancer 行为边界收口**。

### 2026-10-04：DM-NV-09 Necromancer 行为边界收口完成

- 定向回归覆盖并通过 `NecromancerSummonIntegrationTest`、`NecromancerExplosionIntegrationTest`、`NecromancerGolemReviveIntegrationTest`、`NecromancerGolemSideEffectTest`、Bone Armor/Wall、Curse、Poison Nova/Dagger 以及全部 `NativeNecromancer*DataTest` 契约。
- 复核尸体事务：Raise Skeleton、Corpse/Poison Explosion 和 Iron Golem 均在副作用前原子预留，召唤失败会释放 `usable`、`CORPSE_NOSELECT` 和 `CORPSE_NODRAW`；重复 keyframe 与二次消费保持拒绝。
- 补齐 Revive 的原生尸体可选边界：`SrvDo058` 现在统一经过 `CorpseConsumption.selectable`，不可选（`CorpseSel=0`）、fading、shattered、城镇或已预留尸体均在恢复前拒绝；新增回归确保尸体不被修改。
- 保持 Golem/Revive 所有权、PetMax、Iron Golem 物品来源属性、Fire Golem Holy Fire、Bone Armor、Curse group/source 优先级和 Poison owner/source、抗性、持续时间及重连契约不变。
- 证据来源：D2MOO `SrvDo031/055/056/057/058/063`、`SKILLS_CanUnitCorpseBeSelected` 与 1.10f `MonStats2.CorpseSel/Revive` 字段；本轮没有覆盖或重写 Amazon 已验证实现。
- 本项完成后累计完成率：**70%**。下一项切换为 **DM-NV-10 Paladin Aura/Conversion 边界**。

### 2026-10-04：DM-NV-10 Paladin Aura/Conversion 边界完成

- Paladin 定向回归 77 项通过，覆盖 `AuraManagerPulseTest`、`AuraEcsScenarioTest`、Blessed Hammer、Fist of the Heavens、支持/抗性/特殊/资源 Aura、Holy Shield、Conversion 以及全部 `NativePaladin*DataTest` 契约。
- 修正 FoH 延迟导弹的原生命中链：`SrvHit22` 主目标命中时不再落入通用 `ExplosionMissile` 分支，避免额外生成一枚 Holy Bolt；分裂数量重新严格服从 `HitPar2/Calc4` 上限，主目标、亡灵过滤和 6 枚 level-1 上限回归通过。
- Blessed Hammer 回归夹具改为按真实 77 点螺旋路径布置目标并使用 `Size.MEDIUM` 原生 footprint，验证多个目标各命中一次且后续帧不重复伤害；没有改变用户已验证的 Amazon 代码。
- 既有 Aura 证据保持通过：同技能强度选择及稳定 caster tie-break、不同 Aura 叠加、范围/相邻 RoomEx、NoAura/hostile filter、town/离区撤销、付费 pulse 延迟扣蓝、重连 source layer，以及 Conversion 的成功/失败、目标过滤、所有权、到期恢复原始等级/生命比例和 owner/zone 失效收口。
- 证据来源：D2MOO `SrvDo066/073/079/080/081`、`SrvHit22`、`AuraFilter`/`AuraRangeCalc` 与 1.10f Skills/Missiles/States 数据；本轮未覆盖或重写 Amazon 实现。
- 本项完成后累计完成率：**75%**。下一项切换为 **DM-NV-11 Barbarian 战吼/尸体/精通边界**。

### 2026-10-04：DM-NV-11 Barbarian 战吼/尸体/精通边界完成

- 对照 D2MOO `SkillBar.cpp` 的 `SrvSt33_FindPotion_GrimWard`、`SrvDo069_FindPotion`、`SrvSt34_FindItem`、`SrvDo072_FindItem` 和 `SrvDo075_GrimWard`，确认 Find Potion/Find Item 在成功率判定前先设置 `CORPSE_NOSELECT`，失败也不会允许同一尸体再次执行；Grim Ward 按 `MonStats2` small/large 选择 `SrvMissileB/C`，并同步 `CORPSE_NOSELECT/NODRAW`。
- 新增 `BarbarianCorpseSkillTest` ECS 关键帧覆盖：失败率前置预留、重复 keyframe 拒绝、`CorpseSel=0`/shattered/fading 尸体拒绝、Grim Ward 单次 size-specific missile 和尸体坐标事务。
- 修复 `ServerMonsterCorpseSystemTest` 的测试夹具：注册缺失的 `CofManager`，使 hireling、普通死亡和 Holy Freeze shatter 三条原生 DT/DD 路径可完整回归；三项不再因 `CofManager not registered with world` 失败。
- 回归通过：`BarbarianCorpseSkillTest`、`BarbarianFrenzyTest`、`BarbarianWarCryTest`、`BarbarianWhirlwindTest`、`BarbarianBerserkTest`、`NativeBarbarianWarCryDataTest`、`NativeBarbarianPassiveDataTest`、`NativeBerserkDataTest`、`NativeFrenzyDataTest`、`NativeWhirlwindDataTest`、`AIWarCryControlTest` 和 `ServerMonsterCorpseSystemTest`。
- 本项完成后累计完成率：**80%**。下一项切换为 **DM-NV-12 d2client.dll 静态证据并入技能矩阵**。

### 2026-10-04：DM-NV-12 d2client.dll 静态证据并入技能矩阵（第一层）

- 新增 `NativeClientSkillEvidenceTest`，以 dark-magic `skill-behavior-coverage.v1.json` 的 43 个 exact-ID 为输入，逐项核对 1.10f 无损 `Skills.txt` 与解码后的 `Skills.Entry`：`cltstfunc`、`cltdofunc`、`cltmissile`、`cltmissilea/b/c/d` 均必须完全一致，并要求保留原始行号。
- 这层只证明数据字段没有在导入/转换时丢失，不能把客户端 callback 数字直接解释为 `d2client.dll` 的函数语义；测试注释已明确禁止用 Skills.txt 行替代二进制反汇编证据。
- 已建立 [d2client-static-skill-evidence.tsv](d2client-static-skill-evidence.tsv) 作为 43 行回填入口；每行必须同时有匹配的 1.10f DLL hash、函数地址、caller/xref 和 keyframe 证据，状态才可从 `pending-binary` 改为完成态。
- 新增 `tools/verify-d2client-evidence.ps1`：默认检查 43 个 ID、版本和重复项；另一 agent 回填后使用 `-RequireComplete`，会强制验证每行的 hash、函数地址、caller/xref 与 keyframe 字段均非空。
- 当前 `D2_HOME` 未提供可读取的 `d2client.dll`，因此静态地址、调用者/xref、keyframe 消费点仍待另一 agent 的二进制检查结果回填；DM-NV-12 尚未计入完成率，当前仍为 **80%**。
