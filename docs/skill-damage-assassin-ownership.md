# Assassin 技能伤害所有者审计

本文件完成 DMG-03B 的 Assassin 部分：技能 ID 251–280，共 30 项。结论以 Diablo II
1.10f 的 `Skills.txt` / `Missiles.txt` 和 D2MOO 调用路径为准；本阶段只确认伤害所有者、
结算职责和 riiablo 覆盖状态，不写入逐等级 `expected_*` 黄金值。

机器可检查的逐项证据见 `skill-damage-assassin-ownership.tsv`。1.10f 名称保持数据原样，
包括 Fire Trauma、Shock Field、Quickness 和 Royal Strike。

## 结论

- 30/30 项均已确认原版所有者和 D2MOO 路径。
- 26 项会直接产生或修改伤害；Quickness、Weapon Block、Cloak of Shadows、Fade 不产生
  独立对外伤害包，但其状态、防御和命中语义仍属于完整性审计范围。
- 9 项为确认的 riiablo 实现缺口：Claw Mastery、Psychic Hammer、Quickness、Weapon Block、
  Cloak of Shadows、Fade、Shadow Warrior、Mind Blast、Shadow Master。
- 4 项为聚焦测试缺口：Shock Field、Blade Sentinel、Charged Bolt Sentry、Blade Fury。
  它们已有主要生产路径和伤害快照证据，但尚未用专项用例把最终目标生命变化锁定。

## 关键原版语义

### 陷阱放置技能不是最终伤害所有者

`SrvDo045` 只创建 Sentry 并通过召唤继承安装 `SumSkill/SumSkCalc`。真正的伤害由陷阱
MonStats 的攻击技能及其导弹拥有：Charged Bolt Sentry 调用 `SrvDo017`，Wake of Fire
调用 `SrvDo125`，Inferno Sentry 调用怪物 `SrvDo095`，Lightning Sentry 使用
`sentry lightning`。Death Sentry 还必须区分 Skill2 雷电和 `SrvDo055` 尸爆，不能把放置
技能表上的伤害提示直接当成每次攻击包。

### 武学蓄力的伤害在终结技消费时结算

`SrvDo034/035` 首先建立正常武器命中并把成功命中的蓄力层数保存到状态。Tiger Strike、
Cobra Strike、Fists of Fire、Claws of Thunder、Blades of Ice、Royal Strike 的附加效果由
下一次成功终结技消费。Royal Strike 的三层是选择三个阶段，不是把三种元素叠加；
Fists/Claws/Blades 则按各自 `PrgStack` 和专用回调组合阶段。

### 三类刀刃技能的 SrcDam 和生命周期不同

- Blade Sentinel 由移动的召唤控制器携带一枚往返附着导弹，使用 `SrcDam=48`。
- Blade Fury 是 `SrvSt26/SrvDo048` 持续施法节拍创建的独立直线导弹，使用 `SrcDam=96`。
- Blade Shield 是有时限状态的周期范围回调，经 `SrvDo054/SrvDo142` 对每个目标建立记录，
  使用 `SrcDam=32`。

三者都不能退化成同一个“普通武器导弹”实现。

### Shadow 召唤不只是创建一个普通怪物

原版 `SrvDo049` 在召唤之后还同步主人等级、按技能等级扩展生命和被动属性、生成装备，
并安排 Shadow 的技能 AI。当前 riiablo 只完成了普通召唤实体、所有权和状态标记，缺少会
直接影响宠物攻击/施法伤害的初始化，因此 Shadow Warrior/Master 记为实现缺口，而不是
仅记为缺一个测试。

### 无独立输出不等于已实现

Quickness 和 Fade 共用 `SrvDo018`，Weapon Block 依赖永久被动刷新，Cloak of Shadows
同时安装施法者状态和目标 Dim Vision/防御属性。这些技能的基础逐级伤害应标 N/A，但若
状态没有安装或没有进入消费者，仍必须保留生产缺口。

## riiablo 缺口明细

1. **Claw Mastery**：没有 Assassin 被动同步，`PassiveStat/PassiveCalc/PassiveIType` 未进入
   真实 claw 武器记录；现存辅助公式也没有生产调用者。
2. **Psychic Hammer**：没有 `SrvSt22/SrvDo033` 分支，物理/魔法直接伤害及按目标类型选择的
   击退概率没有结算。
3. **Quickness / Fade**：共享 `SrvDo018` 分派只覆盖 Sorceress armor、Venom、Bone Armor
   和 Holy Shield，两种 Assassin 状态均落空。
4. **Weapon Block**：战斗层已有 `passive_weaponblock` 消费者，但没有被动同步把技能值安装
   到角色，所以消费者始终读不到该技能贡献。
5. **Cloak of Shadows**：当前只向目标写入手工计算的防御降低。原版的施法者状态、活动期
   禁止重施，以及全部 `PassiveStat/AuraStat` 数据公式没有完整复现。
6. **Shadow Warrior / Shadow Master**：只创建普通宠物并标记所有权，缺少原版等级、生命、
   被动属性、装备和技能 AI 初始化。
7. **Mind Blast**：没有 `SrvDo051` 范围伤害回调，也没有眩晕/转化结算。

这些缺口本轮只登记，不在尚未建立逐等级黄金值前直接修改生产技能逻辑。

## 下一步

DMG-03B 累计完成 Sorceress、Paladin、Necromancer、Barbarian、Druid、Assassin 共
180/210 项；本项完成比例 75.0%，加权贡献 9.0%，总加权进度 27.0%。下一职业为 Amazon
（技能 ID 6–35）。Amazon 是用户已验证实现，只做 D2MOO 证据核对和回归保护；未经明确
差异证据不改写其生产逻辑。
