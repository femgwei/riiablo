# D2MOO / dark-magic 更新参考记录

更新时间：2026-11-02

本记录对应：

- D2MOO `1a30fbe4`（从 `0c0bbd1e` 快进更新）
- dark-magic `12f586cc`（从 `deafc12a` 快进更新）

## 可直接作为 1.10f 行为参考的 D2MOO 更新

D2MOO 的目标仍是 Diablo II 1.10f，因此以下内容优先进入 riiablo 的原生对齐审计：

- `D2Game UNIT / PLAYER: party gold share per member and corpse items on the corpse`
  （`f96f392c`）：核对金币分配的成员资格、每成员份额、尸体物品归属，补充多人拾取和
  归属回归测试。
- `D2Game AI: try all eight follow directions in the pet and hireling movers`
  （`7870057b`）：佣兵/宠物跟随失败时应遍历八个方向，而不是只尝试固定方向。对应
  riiablo 的佣兵跟随和召唤物 regroup 路径。
- `D2Game ITEMS: compact the removed slot's belt column`
  （`2160948e`）：腰带移除物品后压缩列，需与 riiablo 当前腰带容量和药水移动校验一起核对。
- `D2Game MISSILES: make HitSubMissile and HitPar signed`
  （`10a66a00`）：导弹表字段是有符号值，不能在 Java 读取时无条件当作非负整数；应检查
  `Missiles.Entry`、子导弹和碰撞分支的符号扩展。
- `DRLG_OUTDOORS_GenerateDirtPath: memmove for overlapping shift`
  （`705f1da0`）：泥土路径的重叠数组移动必须按 memmove 语义处理，适合作为 Act 1 路径
  生成和固定种子测试的边界样本。
- `MONSTERUNIQUE_CanAssignUMod: a monster of type 0 can be excluded`
  （`b3198b23`）及 `UMod` 排除列表修正：核对空列表、类型 0 和 signed roll 的边界。
- `D2Game SKILLS: aim Lightning Strike chain missiles at target X`
  （`8981a37e`）：链式导弹的目标坐标读取应单独验证，不能沿用初始投射方向。
- `D2Game OBJECTS: match native trapped soul spawn loop`
  （`c350e9fe`）：对象生成循环、数量和失败路径可用于校对任务对象/陷阱对象。

D2MOO 同时新增了大量 `D2Common` 和 `D2Game` 单元测试，尤其覆盖碰撞、DRLG、物品、
怪物、技能、状态、导弹和单位房间。riiablo 可采用相同的测试切分：先做纯数据边界，再做
ECS/地图集成，不把一个通过的 UI 或 headless 场景当成整个模块完成。

## dark-magic 可借鉴的规格和测试方法

dark-magic 的最新规格以 1.13c 为证据版本。它们不能直接替换 riiablo 的 1.10f 常量，
但可以作为行为边界和证据格式：

- `docs/specs/core-numeric-and-rng-foundations.md`：固定点格式、32 位溢出、符号扩展、
  截断方向和 RNG 消耗必须写入每个公式测试。
- `combat-damage-pipeline-order.md` 与 `combat-damage-mitigation.md`：把命中、伤害构造、
  难度/PvP 缩放、抗性、吸收、生命扣除、吸血和状态应用拆成有序阶段。对应 riiablo
  当前仍标记为 CUSTOM 的完整 `D2DamageStrc` 链，应优先采用这种阶段化审计方式。
- `combat-status-application.md`：毒素伤害与持续时间分离，毒抗、持续时间抗性和每帧
  tick 分开验证。对应 riiablo 的 `StatusEffectApplier`、`DamageApplier` 和毒素状态测试。
- `monster-scaling-by-players-and-difficulty.md`：难度、区域等级、玩家数量、怪物等级、
  生命/经验和抗性初始化应作为同一 spawn 上下文验证，避免只测最终生命值。
- `player-party-experience-sharing.md` 与 `player-party-gold-sharing.md`：成员资格、
  活跃/存活状态、距离、同区域和归属应分别测试。对应 riiablo 当前 party XP/gold 仍为
  PARTIAL 的审计项。
- `missile-creation-and-seeding.md`、`missile-movement-and-collision.md`、
  `missile-hit-and-damage-calculation.md`：导弹创建、种子、移动、碰撞、命中和伤害要分开
  记录 owner、target、create/hit/delete tick，适合补强 riiablo 当前 headless missile
  夹具。
- `skill-assassin-death-sentry-corpse-selection.md` 和 `...corpse-damage.md`：死亡哨兵的
  尸体选择、独立爆炸次数和伤害随机范围应拆开；可用于核对 riiablo 最近的 Death Sentry
  和陷阱实现。
- `player-mana-regeneration.md`、`player-stamina-drain-while-running.md`：资源更新应记录
  固定帧、定点算术、城镇条件、负值和当前值/最大值更新顺序。
- `docs/specs/README.md` 的证据规则：每条规则标注 `verified`、`runtime-confirmed` 或
  `unresolved`，并明确哪些内容只是推断。这一点应保留到 riiablo 的原生对齐文档中。

## 建议的 riiablo 后续顺序

1. 先核对 D2MOO 的导弹字段符号扩展、佣兵/宠物八方向跟随、腰带列压缩和 party gold。
2. 为毒素、怪物 spawn 缩放、party XP/gold、导弹生命周期补充“纯数据 + ECS 集成”双层测试。
3. 以 dark-magic 的阶段化格式拆分 `CombatSystem` 的命中、伤害构造、抗性/吸收和状态应用，
   但所有 1.10f 数值仍以 D2MOO 与本地 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F` 实测为准。
4. 地图方面优先复核泥土路径重叠移动和 Act 1 入口/房间人口；dark-magic 的 Cold Plains、
   Stony Field、Den of Evil 方案可借鉴其 seam、population 和真实资源回归组织方式。

