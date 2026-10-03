# dark-magic 技能对照：非视觉任务清单

更新时间：2026-10-04

## 进度口径

- 本清单只统计不需要用户人眼对比原版画面的工作。
- 清单完成率按固定权重计算：`已完成权重 / 100`。
- 当前清单完成率：**0%**。
- 当前执行项：**DM-NV-01 Amazon Lightning Bolt(20) 真实命中/伤害链**。
- 原技能移植严格总进度仍记为约 **97%**；视觉验收单独保留，不会因本清单完成而自动记为 100%。
- 每项只有在源码依据、自动测试、真实 MPQ gate（适用时）、提交和推送全部完成后才计入百分比。

## 任务列表

- [ ] **DM-NV-01（8%）Amazon Lightning Bolt(20) 真实命中/伤害链**
  - 定位 `lightningjavelin` 从 swept collision、ToHit 到 `SrvDmgFunc=12` 的中断点。
  - owner/observer 均观察同一目标实际掉血，并验证抗性、免疫、弹药和 reconnect。
- [ ] **DM-NV-02（10%）Death Sentry 完整非视觉行为**
  - difficulty=1/2、多个地图 seed、多尸体/多发预算、爆炸范围、物理/火焰拆分。
  - Skill2 fallback、null-target 重选、多 trap/PetMax、重连期间不重复消费或复活。
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

暂无。每完成一项，在此记录日期、证据命令、提交 hash 和新的累计完成率。
