# dark-magic 技能对照：非视觉任务清单

更新时间：2026-10-04

## 进度口径

- 本清单只统计不需要用户人眼对比原版画面的工作。
- 清单完成率按固定权重计算：`已完成权重 / 100`。
- 当前清单完成率：**18%**。
- 当前执行项：**DM-NV-03 Wake of Fire Sentry 行为收口**。
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
- [ ] **DM-NV-03（8%）Wake of Fire Sentry 行为收口**
  - maker→wave 数量、两波方向/间隔、逐目标伤害、墙体/null-hit、预算和控制器到期。
- [ ] **DM-NV-04（8%）Inferno Sentry 行为收口**
  - 通道 duration/pulse、方向追踪、逐目标伤害、抗性/免疫、墙体和 reconnect 生命周期。
- [ ] **DM-NV-05（5%）Fire Trauma 一次性链收口**
  - air→ground→explosion 的一次命中、范围、共享命中集合和 reconnect 不复活父实体。
- [ ] **DM-NV-06（5%）Blade Shield 非视觉边界**
  - 多目标、最后 pulse、到期静默、owner 死亡/离区、武器切换、PvP/Party/召唤过滤。
- [ ] **DM-NV-07（10%）Sorceress 完整技能树行为审计**
  - 剩余 exact-ID、公式、状态、父子导弹、Hydra/区域技能及双客户端/reconnect 门槛。
- [ ] **DM-NV-08（8%）Druid Vine/召唤/持续区域收口**
  - Vine Hit50 窗口、跨区跟随、召唤所有权/PetMax、持续区域命中去重和重连。
- [ ] **DM-NV-09（8%）Necromancer 行为边界收口**
  - 尸体原子预留、召唤失败回滚、Golem/Revive、毒素、Bone Armor、Curse 来源优先级。
- [ ] **DM-NV-10（5%）Paladin Aura/Conversion 边界**
  - 多来源稳定选择、目标范围、离区撤销、付费 pulse、Conversion 到期恢复。
- [ ] **DM-NV-11（5%）Barbarian 战吼/尸体/精通边界**
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
