# 七职业逐等级伤害审计任务清单

更新时间：2026-10-09

审计基线：Diablo II 1.10f

最近完成技能提交：`90cf269fd07288e29710b3eac504f34b112e6ec5`
当前加权完成度：**33.6%**

> 本清单独立于 dark-magic 技能移植清单。此前的 dark-magic 非视觉任务完成率不代表
> 七职业逐等级伤害已经核对。本审计共有 210 个职业技能，第一阶段覆盖硬点等级
> 1–20，共 4,200 个技能—等级组合。

## 当前结论

- 已完成审计口径和验收规则（DMG-01，5%）。
- 已从真实 1.10f `Skills.txt` 自动导出七职业 210 个技能、每技能 1–20 级的完整清册，
  并生成 4,200 行源数据矩阵（DMG-02，10%）。
- 生成入口现以 Immolation Arrow 和 `immolationfire` 的 1.10f 特征值作版本哨兵；若
  `D2_HOME` 指向 1.14 数据，测试会直接失败，禁止把跨版本数据标成 1.10f 黄金基线。
- 1.14 截图只作为跨版本辅助证据。技能公式结构可能相近，但 1.14 的数据表数值不能直接
  写入 1.10f 黄金矩阵；本次基线纠正不增加完成百分比。
- 已完成 D2MOO 通用伤害公式证据和边界回归（DMG-03A，3%）：五段等级曲线、
  `HitShift`、物理/元素协同取整顺序、最小元素伤害的原版协同门槛以及元素持续时间。
- DMG-03B 已完成七职业各 30/30 个技能的伤害所有者和原版调用路径
  （累计 210/210，10.5%）：明细见各职业的
  `skill-damage-*-ownership.tsv` 与 `skill-damage-*-ownership.md`。
  审计确认 Energy Shield、Lightning Mastery、Cold Mastery 仍存在 riiablo 执行/被动
  同步缺口；Telekinesis 的单位目标伤害路径和 Inferno 的单脉冲路径已补齐，Lightning、
  Hydra 仍缺少聚焦伤害测试，Inferno 完整通道生命周期保留在 DMG-07。
- Paladin 审计确认 Sacrifice、Smite、Zeal、Charge、Holy Shield 共 5 个伤害语义缺口；
  Conversion 和 Conviction 生产路径存在但仍缺聚焦测试。缺口只登记，尚未计入黄金值。
- Necromancer 审计确认 30 项所有者；Teeth、Skeleton Mastery、Raise Skeleton、Weaken、
  Raise Skeletal Mage、Bone Spear、Decrepify、Bone Spirit、Revive 缺聚焦伤害测试。
  Summon Resist 另有防御侧实现缺口：生产读取 `passive_summon_resist`，但当前没有
  Necromancer 被动同步路径把该值安装到施法者。
- Barbarian 审计确认 30 项所有者；Bash、Leap、Double Swing、Stun、Double Throw、
  Leap Attack、Concentrate 共 7 个实现缺口，Taunt 和 Battle Cry 另缺最终伤害消费的
  聚焦测试。Frenzy、Whirlwind、Berserk、War Cry、六项武器专精和尸体技能已有生产
  路径与专项测试证据。
- Druid 审计确认 30 项所有者；Molten Boulder、Arctic Blast、Cyclone Armor、Twister、
  Shock Wave、Tornado、Spirit of Barbs 共 7 个实现缺口；Raven、Plague Poppy、Spirit Wolf、
  Heart of Wolverine、Fenris、Hunger、Grizzly 另缺最终伤害消费的聚焦测试。
- Assassin 审计确认 30 项所有者；Claw Mastery、Psychic Hammer、Quickness、Weapon Block、
  Cloak of Shadows、Fade、Shadow Warrior、Mind Blast、Shadow Master 共 9 个实现缺口；
  Shock Field、Blade Sentinel、Charged Bolt Sentry、Blade Fury 另缺最终伤害消费的聚焦测试。
- Amazon 审计确认 30 项所有者；Dopplezon、Valkyrie 仍有 2 个明确实现差异，均缺原版
  召唤被动属性、技能、装备等初始化链。Inner Sight 的分段公式，以及 Jab、Guided Arrow
  的合法零值差异均已在 DMG-04 中修复。
- 跨职业完整性复核已通过：七份清单共 210 个唯一技能、统一 12 列且无空证据；状态分布为
  `IMPLEMENTED_TESTED` 120 项、`IMPLEMENTED_TEST_GAP` 27 项、
  `OUT_OF_SCOPE_NO_DAMAGE` 29 项、`RIIABLO_GAP` 34 项。新增 Guided Arrow 测试引用已纳入
  自动解析门禁。
- 4,200 行中已有 760 行 `GOLDEN_APPROVED`，其余 3,440 行仍为
  `PENDING_D2MOO_REFERENCE`；未批准行的 `expected_*`、`riiablo_actual_*` 和
  `delta_*` 必须保持空白。
- 当前 33.6% 包含审计基础设施、源清册、通用公式证据、七职业 210/210 项所有者语义
  对齐，以及 760 个逐级黄金行；不表示技能伤害正确率为 33.6%。

## 加权任务

- [x] **DMG-01（5%）审计口径、版本、等级域和场景定义**
  - 基线锁定为 1.10f。
  - 第一场景锁定为：硬点等级 1–20、无装备、无协同、无主修、无抗性结算。
  - 等级 21+、协同、武器包、召唤继承和最终结算均作为独立维度，不混入基础曲线。

- [x] **DMG-02（10%）七职业完整清册和初步伤害路径提示**
  - 七职业各 30 个技能，共 210 个；每职业 600 行，共 4,200 行。
  - 路径提示只依据 `Skills.txt` 的技能伤害列、`SrcDam`、server missile、周期、光环、
    被动、召唤和 `SrvDoFunc` 自动生成；它不是最终路径结论。

- [x] **DMG-03A（3%）D2MOO 通用伤害公式、定点数和取整顺序**
  - 证据见 `skill-damage-d2moo-formula-evidence.md`。
  - 边界测试覆盖等级 1/8/9/16/17/22/23/28/29、`HitShift` 显示截断，以及
    `SKILLS_GetMinElemDamage` 与最大值路径不同的协同门槛。

- [x] **DMG-03B（12%）Skills.txt / Missiles.txt / D2MOO 逐技能解释语义对齐**
  - 为每个技能确认实际伤害所有者：技能表、导弹表、武器包、召唤物、状态周期或专用回调。
  - 核对五段每级增量、`HitShift`、`SrcDam`、协同公式、元素长度和取整时机。
  - 每个结论必须填写具体 D2MOO 函数或源码位置；不清楚时再查 The Phrozen Keep。
  - 七职业各 30/30 和跨职业完整性复核均已完成（累计 12.0% / 本项 12%，本项完成比例
    100.0%）；总门禁见 `SkillDamageOwnershipAuditTest`，复核报告见
    `skill-damage-ownership-cross-class-review.md`。

- [ ] **DMG-04（20%）1–20 级无装备、无协同基础黄金值**
  - 为 4,200 行填写有证据的 `expected_*`，非伤害技能必须标记明确的 N/A 原因，
    不能用零伪装成“不适用”。
  - 毒素必须同时记录 rate、duration 和 total；多段技能必须区分单段和整次施法总量。
  - 已批准 Fire Bolt、Ice Bolt、Fire Ball、Ice Blast、Glacial Spike、Lightning、Nova、
    Frost Nova、Charged Bolt、Chain Lightning、Thunder Storm、Static Field、Telekinesis、
    Blaze、Fire Wall、Inferno、Shiver Armor、Chilling Armor、Magic Arrow、Fire Arrow、
    Inner Sight、Critical Strike、Jab、Cold Arrow、Multiple Shot、Dodge、Power Strike、
    Poison Javelin、Exploding Arrow、Slow Missiles、Avoid、Impale、Lightning Bolt、Ice Arrow、
    Guided Arrow、Penetrate、Charged Strike、Plague Javelin 等级 1–20 共 760/4,200 行（本项 18.0952%，加权贡献 3.6190 个百分点）；
    DMG-04 状态为进行中。

- [ ] **DMG-05（15%）全部硬点协同组合**
  - 逐技能列出有效协同、硬点读取规则、上限、取整顺序及组合用例。

- [ ] **DMG-06（10%）武器伤害、SrcDam、ToHit 与多段攻击**
  - 覆盖近战、弓弩、投掷、踢击、charge/release 和多次命中。

- [ ] **DMG-07（10%）毒素、周期、区域、父子导弹和召唤继承**
  - 分离每帧/每脉冲/每枚导弹/每目标/总持续期，禁止只比较 UI 显示整数。

- [ ] **DMG-08（5%）抗性、免疫、难度与 PvP 最终结算**
  - 基础黄金值和最终生命扣减分列保存，避免把结算修正误写进技能基础伤害。

- [ ] **DMG-09（7%）七职业逐等级回归测试**
  - 黄金值与 riiablo 实际输出逐行比较；所有非零差异必须有结论和回归用例。

- [ ] **DMG-10（3%）完整性审计、报告和交接**
  - 校验 210 技能、所有已定义等级/场景、证据链接、测试引用和未决项均可追溯。

完成度按各项中已达到准入规则的最小可核查单元累计；未达到准入规则的草稿不计入。
当前总加权完成度为 33.6190%（展示时为 33.6%）。

## 黄金值准入规则

一行只有同时满足以下条件，才能从 `PENDING_D2MOO_REFERENCE` 改为
`GOLDEN_APPROVED`：

1. 已确认该伤害由哪个表或专用处理函数拥有，不能只根据技能名称推断。
2. `d2moo_reference` 指向具体函数/源码位置，并能解释等级增量、定点数和取整顺序。
3. `expected_*` 使用明确单位；毒素、多段、周期和召唤不得只填一个含义不清的总数。
4. `riiablo_actual_*` 来自生产计算路径，不得用同一套期望值公式回填冒充独立实际值。
5. `delta_*` 已计算；差异为零，或差异已被判定为 riiablo 缺陷并绑定回归测试/修复任务。
6. `test_reference` 指向自动化测试；Amazon 已由用户验证的实现不因清册导入而被覆盖。

## 矩阵文件

- `skill-damage-audit-handoff-2026-10-08.md`：Fire Arrow 审计前的接手快照，记录当时 Git
  基线、测试命令、XLSX 生成注意事项和已知既有失败；当前进度以本清单为准。
- `skill-damage-golden-matrix.tsv`：版本控制友好的机器可读真源，共 60 列、4,200 行数据。
- `skill-damage-golden-matrix.xlsx`：浏览、筛选和交接用工作簿；包含 Summary、Task List、
  Golden Matrix 和 Field Dictionary。
- `SkillDamageAuditMatrixTest`：从真实 1.10f MPQ 重建源清册，并硬性检查 7×30×20 的完整性。
- `SkillDamageOwnershipAuditTest`：复核七份所有者清单的 210 个唯一技能、统一状态分类、
  D2MOO 位置和测试引用可追溯性。

`source_curve_*` 只是 `Skills.txt` 基值加五段等级增量后的未移位源数值。它们没有应用
`HitShift`、导弹归属、协同、主修、武器包、周期或最终结算，因此不能当作黄金值使用。

## DMG-04 已批准结果

- Fire Bolt（技能 36）等级 1–20 已通过黄金准入：D2MOO 原生 8.8 定点中间值、
  右移 8 位后的单枚单目标整数范围，以及 riiablo `MissileDamageResolver.initializeSkill`
  生产快照逐级一致。
- 基础场景明确排除装备、协同、Fire Mastery、抗性和最终生命结算；等级 1 为 3–6，
  等级 20 为 45–60，20 个等级的 `delta_min/max` 均为 0。
- Ice Bolt（技能 39）沿用相同的 D2MOO 技能导弹路径；基础场景排除装备、协同、
  Cold Mastery、抗性和最终生命结算。等级 1 为 3–5，等级 20 为 38–49，20 个等级的
  `delta_min/max` 均为 0。
- Ice Bolt 的 `cold length` 不属于本批伤害范围，矩阵只保留源字段并明确标为待 DMG-07，
  不用持续时间扩大或替代单次命中伤害。
- Fire Ball（技能 47）等级 1–20 已按“每个目标一次命中”批准。等级 1 为 6–14，等级
  20 为 199–226，20 个等级的 `delta_min/max` 均为 0。`SrvHit01` 的中心目标去重和附近
  目标扇出由独立集成测试锁定。
- Fire Ball 的 `expected_total` 不代表整次施法对所有目标的累计值；范围内目标数量和
  cast-wide total 留给 DMG-07，避免把单目标黄金值误乘为固定总量。
- Ice Blast（技能 45）等级 1–20 已按单目标一次命中批准。等级 1 为 8–12，等级 20
  为 253–266，20 个等级的 `delta_min/max` 均为 0；riiablo 实际值来自
  `MissileDamageResolver.initializeSkill` 的 `coldmindam/coldmaxdam` 生产快照。
- `MISSMODE_SrvDmg04_IceBlast` 只用于确认该导弹会把 cold length 转为 freeze length；
  冻结状态、持续时间及抗性结算不进入本批黄金伤害，明确延后到 DMG-07。
- Glacial Spike（技能 55）等级 1–20 已按“每个目标一次命中”批准。等级 1 为 16–24，
  等级 20 为 225–242，20 个等级的 `delta_min/max` 均为 0；riiablo 实际值来自
  `MissileDamageResolver.initializeSkill` 的 `coldmindam/coldmaxdam` 生产快照。
- `MISSMODE_SrvHit13_GlacialSpike_HellMeteorDown` 的范围扇出、冻结长度和整次施法多目标
  累计不进入本批固定总伤害，明确延后到 DMG-07。
- Lightning（技能 49）等级 1–20 已按“每个目标一次命中”批准。等级 1 为 1–40，
  等级 20 为 1–272，20 个等级的 `delta_min/max` 均为 0；riiablo 实际值来自
  `lightningbolt` 的 `MissileDamageResolver.initializeSkill` 生产快照。
- Lightning 的最小值五段增量全为 0，最大值五段增量为 `8/12/20/28/36`，且
  `HitShift=8`；穿透、多目标接触、重复接触与整次施法累计不进入本批固定总伤害，
  明确延后到 DMG-06/07，抗性结算延后到 DMG-08。
- Nova（技能 48）等级 1–20 已按“每个目标一次命中”批准。等级 1 为 1–20，等级 20
  为 131–188，20 个等级的 `delta_min/max` 均为 0；riiablo 实际值来自每条 `nova`
  导弹的 `MissileDamageResolver.initializeSkill` 生产快照。
- D2MOO `SKILLS_SrvDo022_NovaAttack` 经 `sub_6FD14170` 固定创建 64 路导弹；riiablo
  集成测试锁定同次施法共享命中门禁，使每个目标只结算一次。64 路投递、多目标累计和
  整次施法总伤害延后到 DMG-07，抗性结算延后到 DMG-08。
- Frost Nova（技能 44）等级 1–20 已按“每个目标一次命中”批准。等级 1 为 2–4，
  等级 20 为 56–67，20 个等级的 `delta_min/max` 均为 0；riiablo 实际值来自每条
  `frostnova` 导弹的 `MissileDamageResolver.initializeSkill` 生产快照。
- Frost Nova 与 Nova 共用 D2MOO `SrvDo022` 的 64 路投递，但使用 `HitShift=7` 的冷伤害
  曲线。基础场景不含 Blizzard/Frozen Orb 协同和 Cold Mastery；冷缓持续时间、64 路
  多目标累计及整次施法总伤害延后到 DMG-07，抗性结算延后到 DMG-08。
- Charged Bolt（技能 38）等级 1–20 已按“单枚导弹对单个目标一次命中”批准。等级 1
  为 2–4，等级 20 为 13–15，20 个等级的 `delta_min/max` 均为 0；riiablo 实际值来自
  `chargedbolt` 导弹的 `MissileDamageResolver.initializeSkill` 生产快照。
- D2MOO `SKILLS_SrvDo017_ChargedBolt_BoltSentry` 按 `calc1` 创建多枚技能导弹，但不改变
  每枚导弹的 Skills.txt 伤害曲线。基础场景不含 Lightning 协同和 Lightning Mastery；
  弹丸数量、确定性种子、77 帧路径、重复/多目标接触和整次施法累计延后到 DMG-07，
  抗性与免疫结算延后到 DMG-08。
- Chain Lightning（技能 53）等级 1–20 已按“一条链段对单个目标一次命中”批准。等级 1
  为 1–40，等级 20 为 1–281，20 个等级的 `delta_min/max` 均为 0；riiablo 实际值来自
  `chainlightning` 根导弹和续链导弹的 `MissileDamageResolver.initializeSkill` 生产快照。
- D2MOO `SKILLS_SrvDo026_ChainLightning` 只创建根导弹并保存跳跃预算，后续链段由
  `MISSMODE_SrvHit12_ChainLightning_LightningStrike` 创建且继承相同技能 ID 和技能等级。
  本次修复了根导弹及技能 53 续链导弹缺失 Skills.txt 伤害快照的问题；跳跃目标选择、
  多目标累计和整次施法总伤害延后到 DMG-07，抗性与免疫结算延后到 DMG-08。
- Thunder Storm（技能 57）等级 1–20 已按“每次周期雷击对单个目标的一次命中”批准。
  等级 1 为 1–100，等级 20 为 195–294，20 个等级的 `delta_min/max` 均为 0；riiablo
  实际值来自周期创建的 `thunderstorm1` 导弹 `MissileDamageResolver.initializeSkill`
  生产快照。
- D2MOO `SKILLS_SrvSt13_ThunderStorm` 初始化技能参数，`SKILLS_SrvDo029_ThunderStorm`
  建立状态并在周期回调中选择目标、创建技能导弹、立即结算一次命中。状态持续时间、
  雷击间隔、目标选择、总雷击次数和整段状态总伤害延后到 DMG-07，抗性与免疫结算延后
  到 DMG-08。
- Static Field（技能 42）等级 1–20 已按固定输入场景批准：单个 100/100 生命怪物、
  Normal Expansion、0 闪电抗性、无装备。`calc1=par4=25`，`calc2=par3=0`，因此每一级
  的原始及最终单目标伤害均为 25，20 个等级的 `delta_min/max` 均为 0；技能等级只改变
  `AuraRangeCalc=ln12` 的作用半径，不改变伤害百分比。
- D2MOO `SKILLS_SrvDo020_StaticField` 读取当前生命百分比、最小 8.8 定点伤害和难度生命
  下限；`SKILLS_AuraCallback_StaticField` 先用整数当前生命计算百分比，再保留最后 1 点
  生命并应用最小伤害。Expansion 的 `StaticFieldMin` 是施法资格门禁，不是命中后的生命
  夹紧。Nightmare/Hell 下限、抗性、免疫、吸收和 PvP 结算由专项集成测试锁定，归入
  DMG-08；多目标及重复施法总伤害归入 DMG-07。
- Telekinesis（技能 43）等级 1–20 已按“单个合法单位目标的一次直接命中”批准。无装备、
  无协同、无 Lightning Mastery 时，等级 1 为 1–2，等级 20 为 20–21；20 个等级的
  `delta_min/max` 均为 0。D2MOO `D2GAME_RollElementalDamage` 使用
  `SKILLS_GetMin/MaxElemDamage(..., a4=1)`，因此生产路径会应用 Lightning Mastery。
- D2MOO 的 limited RNG 接收 `max-min`，运行时上端点按原生约定不参与抽取；黄金矩阵仍
  记录 `SKILLS_GetMin/MaxElemDamage` 的规范范围。riiablo 新增 `SrvDo021` 单位目标路径，
  覆盖敌对、距离、城镇、抗性、免疫、吸收、PvP 和无导弹语义。击退概率与物品/对象
  交互是行为边界，不计入本批 DMG-04 黄金伤害。
- Blaze（技能 46）等级 1–20 已按“单个地面导弹、单个目标、单个游戏帧的 8.8 火焰
  伤害率”批准。等级 1 为 `64–128` fixed（0.25–0.5 生命/帧），等级 20 为
  `928–992` fixed（3.625–3.875 生命/帧）；20 个等级的 `delta_min/max` 均为 0。
- D2MOO `SrvDo023` 安装 Blaze 状态，实际移动时创建继承技能 ID/等级的 `blaze`；
  `SrvDo05` 每个游戏帧执行碰撞。`DamageRate` 写入 `STAT_DAMAGE_FRAMERATE` 并最终按
  `DamageRate / 1024` 缩放平面 DR/MDR，不控制伤害间隔。Fire Mastery、抗性、吸收、
  PvP、多个 trail 重叠、生命周期和整段总伤害分别延后到 DMG-05/07/08。
- Fire Wall（技能 51）等级 1–20 已按“单个 `firewall` 段、单个目标、单个游戏帧的
  8.8 火焰伤害率”批准。等级 1 为 `240–320` fixed（0.9375–1.25 生命/帧），等级 20
  为 `4384–4464` fixed（17.125–17.4375 生命/帧）；20 个等级的 `delta_min/max` 均为 0。
- D2MOO `SrvDo024` 创建两条方向相反的 `firewallmaker` 和一个中心 `firewall` 段；
  `SrvDo06` 创建的子段继承技能 ID/等级，`SrvDo05` 每帧碰撞。`DamageRate=41` 只把平面
  DR/MDR 按 `41/1024` 缩放，不是 41 帧伤害间隔。多段重叠、墙体长度、生命周期和整次
  施法总伤害延后到 DMG-07；协同/Fire Mastery 与最终抗性结算分别延后到 DMG-05/08。
- Inferno（技能 41）等级 1–20 已按“单个 `SrvDo019` 创建的一个 `infernoflame1`
  流导弹对单个目标的一次 8.8 定点命中”批准。等级 1 为 `128–256` fixed
  （0.5–1.0 生命/脉冲），等级 20 为 `2080–2256` fixed（8.125–8.8125 生命/脉冲）；
  20 个等级的 `delta_min/max` 均为 0。
- D2MOO `SKILLS_DoInferno` 只消费 `SrvMissileA`，写入技能 ID/等级，并以 `calc1` 结果
  覆盖流导弹射程。审计发现 riiablo 原通用分支会把相同的 A/B/C 三列都创建出来，且
  普通整数快照会截断低等级的小数脉冲；现已用专用 `spawnSorceressInferno` 改为一枚
  导弹并保存 8.8 定点伤害。`SrvSt11` 状态刷新、耗蓝、重复脉冲、障碍截断和整次施法
  总伤害延后到 DMG-07，协同/Fire Mastery 与最终抗性结算分别延后到 DMG-05/08。
- Shiver Armor（技能 50）等级 1–20 已按“`UNITEVENT_ATTACKEDINMELEE` 触发的一次
  `EventFunc03` 直接冷伤害反击”批准。无装备、Frozen Armor/Chilling Armor 协同为 0；
  等级 1 的规范 getter 范围为 `6–8` 生命，等级 20 为 `60–71` 生命，20 个等级的
  `delta_min/max` 均为 0。对应冷长度从 100 帧增长到 500 帧。
- D2MOO `D2GAME_RollElementalDamage_6FD14DD0` 以 `max-min` 调用有限随机数，因此运行时
  最大端点不包含在随机结果中；矩阵仍像 Telekinesis 一样保存规范 getter 的 min/max。
  审计发现 riiablo Shiver Armor 原实现使用了包含最大值的随机区间，并把 `dwColdLen`
  误施加为冻结；现已改为原版上界排除和普通减速状态。协同组合归入 DMG-05，抗性、
  Cold Mastery 穿透、吸收和 PvP 归入 DMG-08。
- Chilling Armor（技能 60）等级 1–20 已按“合法 `HITBYMISSILE` 事件触发的一枚
  `chillingarmorbolt` 返回弹命中”批准。无装备、Frozen Armor/Shiver Armor 协同为 0；
  等级 1 的规范 getter 范围为 `4–6` 生命，等级 20 为 `39–50` 生命，20 个等级的
  `delta_min/max` 均为 0。对应 cold 长度从 100 帧增长到 400 帧。
- D2MOO `EventFunc01` 只在入射导弹具有 `ReturnFire` 标志时创建回击弹，并把护甲技能 ID
  和等级写入导弹；`MISSILE_CalculateDamageData` 因此从 Skills.txt#60 而非导弹自身曲线
  取得冷伤害。`chillingarmorbolt` 本身不含 `ReturnFire`，不会递归触发。审计还发现
  riiablo 通用导弹随机包含最大端点，现已把导弹物理和元素随机同步为 D2MOO
  `MISSMODE_RollDamageValue` 的排除上界；近战随机语义保持不变。协同组合归入 DMG-05，
  抗性、Cold Mastery 穿透、吸收和 PvP 归入 DMG-08。
- Magic Arrow（技能 6）等级 1–20 已按“一枚 `magicarrow` 对单个目标的抗性前总整数
  伤害”批准。无装备且 owner 武器伤害为 0 时，等级 1 为 1，等级 20 为 20；20 个等级的
  `delta_min/max` 均为 0。`SrvDmg01` 的 `dl12=1%+(level-1)*1%` 只把物理通道的一部分
  转为魔法，不改变本场景的总伤害。固定武器包与分通道定点精度归入 DMG-06，最终结算
  归入 DMG-08。
- Fire Arrow（技能 7）等级 1–20 已按“一枚 `firearrow` 对单个目标的一次抗性前火焰
  命中”批准。无装备、owner 武器伤害为 0、Exploding Arrow 硬点为 0 时，等级 1 为
  1–4，等级 20 为 63–70；20 个等级的 `delta_min/max` 均为 0。`SrvDmg01` 的
  `dl12=3%+(level-1)*2%` 在本场景只会转换零物理包，因此不改变技能自身火焰曲线。
  固定武器包的物理转火焰归入 DMG-06，抗性、穿透、吸收和 PvP 归入 DMG-08。
- Inner Sight（技能 8）等级 1–20 已按“无直接伤害”批准；三组伤害 expected/actual/delta
  字段全部保持空白，并在 `candidate_unit` 明确标记 N/A，禁止用 0 伪装伤害。D2MOO
  `SrvDo006` 只计算 `AuraStatCalc=-edmn` 并由回调安装定时 `armorclass` 负值，不创建导弹
  或伤害记录。防御削减从等级 1 的 `-40` 增长到等级 20 的 `-815`。
- 审计修复了 riiablo 原手写线性 fallback：`SkillFormula` 现在支持未移位的 `edmn/edmx`
  token，`applyInnerSight` 使用原生分段曲线，等级 2 从错误的 `-60` 修正为 `-65`。持续
  时间、范围、过滤和最终命中率影响留给后续行为与结算审计。
- Critical Strike（技能 9）等级 1–20 已按“永久被动暴击概率，不拥有独立伤害包”批准；
  三组伤害 expected/actual/delta 字段全部保持空白，并在 `candidate_unit` 明确标记 N/A。
  D2MOO 被动刷新计算 `PassiveCalc=dm12`，把 16%–68% 写入永久
  `passive_critical_strike` 状态；近战和导弹消费者只在已有物理伤害记录上读取该概率。
  随机边界、物理翻倍、与武器专精/Deadly Strike 的优先级及最终结算留给 DMG-06/08。
- Jab（技能 10）等级 1–20 已按“技能本身不拥有固定伤害包，三个 SQ keyframe 各创建
  一份独立武器记录”批准；三组伤害 expected/actual/delta 字段全部保持空白，并在
  `candidate_unit` 明确标记 N/A。原生 `Calc1=ln34` 的武器物理加成为 `-15%`–`42%`，
  等级 6 的 `0%` 是合法公式结果。生产现仅在公式字段缺失时使用兼容 fallback，不再把
  合法零值替换为 `+48%`。固定武器包、每段实际值和三段施法总量留给 DMG-06。
- Cold Arrow（技能 11）等级 1–20 已按“无装备、owner 武器伤害为 0、Ice Arrow 硬点为
  0、单箭单目标、抗性前冷伤”批准。`HitShift=7` 把源表等级 1 的 `6–8` 转为 `3–4`，
  等级 20 的 `106–112` 转为 `53–56`；20 个等级的 `delta_min/max` 均为 0。
  `SrvDmg01` 的 `dl12=3%+(level-1)*2%` 在本场景只转换零物理包。固定武器转换和分通道
  定点精度留给 DMG-06；100–670 帧 chill length 留给 DMG-07，最终结算留给 DMG-08。
- Multiple Shot（技能 12）等级 1–20 已按“技能本身不拥有固定伤害包，每条 lane 创建一枚
  独立 `SrcDam=96` 武器导弹”批准；三组伤害 expected/actual/delta 字段全部保持空白，并在
  `candidate_unit` 明确标记 N/A。1.10f `Calc1="min(24,ln12)"` 产生 2–21 枚导弹，
  `Calc2=par3` 固定为 1 帧激活值，`Calc3=2` 固定中央两条 lane。固定武器包、每枚命中值、
  中央/外侧 lane 标志的消费语义及整次施法总量留给 DMG-06。
- Dodge（技能 13）等级 1–20 已按“永久被动近战规避概率，不拥有独立输出伤害包”批准；
  三组伤害 expected/actual/delta 字段全部保持空白，并在 `candidate_unit` 明确标记 N/A。
  D2MOO 被动刷新计算 `PassiveCalc=dm12`，把 18%–56% 写入永久 `passive_dodge` 状态；
  近战命中路径在建立和结算伤害记录前读取该概率，成功时清除 successful-hit 标志。
  随机边界、盾牌/武器格挡优先级、动作状态和最终来袭伤害结算留给 DMG-08。
- Power Strike（技能 14）等级 1–20 已按“单次成功近战记录中的技能闪电分量、零武器包、
  零协同、抗性前 canonical getter 范围”批准。闪电范围从等级 1 的 `1–16` 增长到等级
  20 的 `1–646`，20 个等级的 `delta_min/max` 均为 0。D2MOO `SrvSt06` 先判定命中，
  再把闪电随机值写入同一份 combat record；`SrvDo002` 在关键帧消费该记录并扣耐久。
  固定武器物理包、`Calc1` 增强物理、实际随机值和整份近战记录总量留给 DMG-06，抗性、
  吸收、PvP 与最终生命扣减留给 DMG-08。
- `expected_total` 对单次命中的范围型伤害不适用，保持空白并在 `candidate_unit` 中标明
  N/A；禁止把最小值、最大值或二者之和伪装成“总伤害”。
- 自动化证据：
  `FireBoltGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `IceBoltGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `FireBallGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `SorceressFireBallIntegrationTest#impactFansOutToNearbyTargetsButDoesNotRedamageTheCenter`、
  `IceBlastGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `SorceressIceBlastIntegrationTest#iceBlastCreatesFreezeWithoutASecondColdStateAndHitsOnce`、
  `GlacialSpikeGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `SorceressIceBlastIntegrationTest#glacialSpikeAppliesOneFreezePacketToEachTargetInImpactRadius`、
  `LightningGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `NovaGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `SorceressNovaIntegrationTest#oneCastDamagesEachCrossedTargetOnlyOnce`、
  `FrostNovaGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `SorceressFrostNovaIntegrationTest#createsExactNativeRingWithSkillDamageAndColdMasterySnapshot`、
  `SorceressFrostNovaIntegrationTest#collisionAppliesResistedColdDurationAndNativeImmunityItemGates`、
  `ChargedBoltGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `NativeSorceressProjectileDataTest#chargedBoltUsesSrvDo17AndCalc1Burst`、
  `NativeSorceressProjectileDataTest#chargedBoltInitialDirectionRemainsTargetAligned`、
  `ChainLightningGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `ChainLightningGoldenDamageTest#continuationSegmentKeepsTheAuthoritativeSkillSnapshot`、
  `SpecialSkillEcsScenarioTest#chainLightningCreatesOneAuthoritativeSegmentPerHostileJump`、
  `ThunderStormGoldenDamageTest#levelOneToTwentyMatchesD2mooFixedPointFormula`、
  `SorceressThunderStormIntegrationTest#castInstallsAuraAndEmitsOneAuthoritativeStrikePerPeriod`、
  `StaticFieldGoldenDamageTest#levelOneToTwentyMatchesD2mooCurrentLifeFormula`、
  `StaticFieldGoldenDamageTest#nativeFloorAndLastLifeBoundariesMatchD2mooOrder`、
  `SorceressStaticFieldIntegrationTest#normalHitsEveryValidTargetAndUsesResistanceAbsorbAndNoMissiles`、
  `SorceressStaticFieldIntegrationTest#expansionDifficultyThresholdIsAnEligibilityGate`、
  `SorceressStaticFieldIntegrationTest#hostilePlayerReceivesNativePvpScalarWhileNeutralPlayerIsIgnored`、
  `TelekinesisGoldenDamageTest#levelOneToTwentyMatchesD2mooElementalFormula`、
  `TelekinesisGoldenDamageTest#nativeLimitedRandomRollTreatsMaximumAsExclusive`、
  `SorceressTelekinesisIntegrationTest#monsterDamageUsesLightningMasteryAndCreatesNoMissile`、
  `SorceressTelekinesisIntegrationTest#resistanceImmunityAndAbsorbUseTheNativeElementalPipeline`、
  `SorceressTelekinesisIntegrationTest#hostilePlayerUsesPvpScalarWhileInvalidTargetsAreSkipped`、
  `BlazeGoldenDamageTest#levelOneToTwentyMatchesD2mooPerFrameFixedPointFormula`、
  `NativeSorceressFireAreaDataTest#blazeUsesTimedStateAndFractionalGroundMissile`、
  `SorceressFireAreaIntegrationTest#blazeStateEmitsOnlyAfterActualMovementAndKeepsFractionalDamage`、
  `SorceressFireAreaIntegrationTest#blazeTrailDamagesAnOverlappingTargetOnItsFirstGameFrame`、
  `FireWallGoldenDamageTest#levelOneToTwentyMatchesD2mooPerFrameFixedPointFormula`、
  `NativeSorceressFireAreaDataTest#fireWallUsesTwoMakersAndOnePersistentCentreSegment`、
  `SorceressFireAreaIntegrationTest#fireWallCreatesOpposedMakersCentreAndDamagingChildSegments`、
  `CombatSystemTest#fixedFireRatePreservesFractionAndScalesMdrByDamageRate`、
  `InfernoGoldenDamageTest#levelOneToTwentyMatchesD2mooPerStreamMissileFixedPointFormula`、
  `NativeSorceressFireAreaDataTest#infernoUsesOneFractionalSkillOwnedStreamMissile`、
  `SorceressFireAreaIntegrationTest#infernoSrvDoEmitsOneFractionalSkillOwnedStreamMissile`、
  `ShiverArmorGoldenDamageTest#levelOneToTwentyMatchesD2mooDirectEventFormula`、
  `ShiverArmorGoldenDamageTest#nativeLimitedRandomRollTreatsGetterMaximumAsExclusive`、
  `ChillingArmorGoldenDamageTest#levelOneToTwentyMatchesD2mooReturnMissileFormula`、
  `ChillingArmorGoldenDamageTest#nativeMissileRollTreatsGetterMaximumAsExclusive`、
  `NativeSorceressDefenseDataTest#shiverAndChillingArmorRetainDistinctNativeReactions`、
  `SorceressDefenseIntegrationTest#armorFamilyIsExclusiveAndDispatchesItsThreeNativeEvents`、
  `MagicArrowGoldenDamageTest#levelOneToTwentyPreservesD2mooTotalAcrossMagicConversion`、
  `FireArrowGoldenDamageTest#levelOneToTwentyMatchesD2mooFireCurveWithoutWeaponOrSynergy`、
  `InnerSightGoldenDamageTest#levelOneToTwentyAreNondamagingNativeDefenseDebuffs`、
  `CriticalStrikeGoldenDamageTest#levelOneToTwentyAreNondamagingNativeCriticalChances`、
  `JabGoldenDamageTest#levelOneToTwentyUseNativeWeaponPercentWithoutOwningFixedDamage`、
  `AmazonMeleeSkillLifecycleTest#jabConsumesExactlyThreeAttackKeyframesAndStopsAfterTheThird`、
  `AmazonMeleeSkillLifecycleTest#jabCompletesRemainingThrustsAfterTheFirstStrikeKillsTarget`、
  `NativeJabSequenceTest#oneHandThrustUsesTheNativeEighteenPointSequence`、
  `NativeJabSequenceTest#twoHandThrustUsesTheNativeTwentyOnePointSequence`、
  `ColdArrowGoldenDamageTest#levelOneToTwentyMatchesD2mooColdCurveWithoutWeaponOrSynergy`、
  `AmazonSkillSpecializationTest#elementalArrowsCaptureNativeSkillDamageAndFreezeSemantics`、
  `MultipleShotGoldenDamageTest#levelOneToTwentyUseNativeLaneCountsWithoutOwningFixedDamage`、
  `ServerSkillSystemTest#srvDo008UsesNativeCountAndCentreFormulas`、
  `ServerSkillSystemTest#srvDo008UsesNativePerpendicularLaneSpacing`、
  `AmazonSkillSpecializationTest#multipleShotStopsAtNativeMapBarrierBeforeTarget`、
  `DodgeGoldenDamageTest#levelOneToTwentyAreNondamagingNativeMeleeAvoidanceChances`、
  `NativeAmazonPassiveDataTest#nativePassiveFormulasMatchAtRepresentativeLevels`、
  `NativeAmazonPassiveDataTest#passiveStateBridgeUsesNativeStatsAndStateIds`、
  `AmazonSkillSpecializationTest#passiveDodgeAvoidEvadeUseNativeAttackContext`、
  `PowerStrikeGoldenDamageTest#levelOneToTwentyMatchD2mooLightningCurveWithoutWeaponOrSynergy`、
  `AmazonMeleeSkillLifecycleTest#powerStrikeConsumesOnePrecomputedElementalCombatRecord`、
  `AmazonMeleeSkillLifecycleTest#powerAndChargedStrikeDrainWeaponDurabilityOnlyAfterAConfirmedHit`、
  `NativeBarbarianPassiveDataTest#selectedMasteryChangesAuthoritativeDamageHitChanceAndCriticalRoll`。

## 可复现命令

在仓库根目录设置输出路径后运行：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:SKILL_DAMAGE_AUDIT_TSV = 'F:\3rd_src\riiablo\docs\skill-damage-golden-matrix.tsv'
$env:SKILL_DAMAGE_AUDIT_COMMIT = (git rev-parse --short HEAD)
.\gradlew.bat :core:test --tests com.riiablo.engine.server.SkillDamageAuditMatrixTest --no-daemon
```

`D2_HOME` 必须显式指向未经修改的 1.10f 安装。测试中的版本哨兵要求 Immolation Arrow
为 `EMin=10`、`EMax=20`、五段增量 `10|20|30|32|34`，并要求
`immolationfire.Range=75`；1.14 的对应值不同，不能通过该门禁。

随后运行 `tools/skill-damage-audit/build-golden-matrix.mjs` 重新生成 XLSX。构建器只把 TSV
和本清单的当前状态整理成工作簿，不会自行批准任何黄金值。

## Poison Javelin（技能 15）

Poison Javelin 等级 1–20 已按“单个 `poisonjavcloud` 对单个目标的完整持续期毒伤，抗性前、
零装备、零 Plague Javelin 硬点”批准。原生 8.8 速率从 `32–48` fixed/frame 增长到
`592–656` fixed/frame，持续时间从 200 增长到 1150 帧；完整持续期整数总量从 `25–37`
增长到 `2659–2946`，20 个等级的 `delta_min/max` 均为 0。`expected_total`、
`riiablo_actual_total` 和 `delta_total` 保持空白，避免把一个范围压成单一总数。

D2MOO `SrvDo02` 只负责根标枪发出 `poisonjavcloud`，`SrvDo03` 每原生帧处理毒云碰撞；
根标枪武器命中、毒云数量/覆盖/重叠/刷新留给 DMG-06/07，抗性和最终结算留给 DMG-08。

## Plague Javelin（技能 25）

Plague Javelin 等级 1–20 已按“单个 `plaguejavcloud` 对单个目标的完整持续期毒伤，抗性前、
零装备、零 Poison Javelin 硬点”批准。`HitShift=3` 使 D2MOO 元素 getter 产生原生 8.8
fixed/frame 速率：等级 1 为 `80–128`，等级 20 为 `1824–1872`；持续时间从 75 增长到
265 游戏帧；完整持续期整数总量从 `23–37` 增长到 `1888–1937`，20 个等级的
`delta_min/max` 均为 0。`expected_total`、`riiablo_actual_total` 和 `delta_total` 保持空白，
因为这里记录的是一个范围，而不是把最小/最大压成单一总数。

D2MOO `MISSMODE_SrvHit02_PlagueJavelin_PoisonPotion` 从根标枪命中扇出
`plaguejavcloud`，`HitPar[0]=1`、`HitPar[1]=2`、`HitPar[2]=3` 经过
`MISSMODE_CreatePoisonCloudHitSubmissiles` 产生 8 个主环和 15 个交错子云，共 23 个移动毒云；
每个子云随后由 `MISSMODE_SrvDo03_PoisonCloud_Blizzard_ThunderStorm_HandOfGod` 按原生帧进入
通用碰撞路径。根标枪武器命中、23 云的轨迹/覆盖/重叠/刷新留给 DMG-06/07，毒抗、毒长减免、
Pierce、PvP 和最终结算留给 DMG-08。

## 下一执行项

继续 DMG-04，下一项优先审核 Strafe（技能 26）等级 1–20。
