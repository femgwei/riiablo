# Druid 技能伤害所有者审计

本文件完成 DMG-03B 的 Druid 部分：技能 ID 221–250，共 30 项。结论以 Diablo II
1.10f 的 `Skills.txt` / `Missiles.txt` 和 D2MOO 调用路径为准；本阶段只确认伤害所有者、
结算职责和 riiablo 覆盖状态，不写入逐等级 `expected_*` 黄金值。

机器可检查的逐项证据见 `skill-damage-druid-ownership.tsv`。

## 结论

- 30/30 项均已确认原版所有者和 D2MOO 路径。
- 24 项会直接产生或修改伤害；Shape Shifting、Oak Sage、Cycle of Life、Cyclone Armor、
  Vines 等 5 项不产生独立伤害包，Wearwolf 只修改命中/攻速，另有召唤辅助语义。
- 7 项为确认的 riiablo 实现缺口：Molten Boulder、Arctic Blast、Cyclone Armor、Twister、
  Shock Wave、Tornado、Spirit of Barbs。
- 7 项为聚焦测试缺口：Raven、Plague Poppy、Summon Spirit Wolf、Heart of Wolverine、
  Summon Fenris、Hunger、Summon Grizzly。它们已有主要生产路径，但尚未通过最终伤害消费
  用例锁定攻击包、毒素、增伤或吸取只结算一次。

## 关键原版语义

### 召唤技能不能用 Skills.txt 的 min/max 直接代替宠物攻击

`SrvDo114/115/119` 先创建真实宠物，再由 `D2GAME_SetSummonPassiveStats` 安装召唤等级、
`SumSkill/SumSkCalc` 和被动属性。Raven、Spirit Wolf、Fenris、Grizzly 的伤害所有者是后续
宠物攻击；Plague Poppy 则由 Vine AI 使用 `Vine Attack`，最终由 plague-vines 导弹结算毒素。
`SrvHit50` 只控制重复命中的时间窗口，本身不应再造一份毒素伤害。

### Twister 与 Tornado 不是普通单枚直线导弹

`SKILLS_SrvDo118_Twister_Tornado` 先计算 `calc1`，再为每枚导弹写入独立初始化参数。
Twister 的 `SrvDmg09` 还追加眩晕；Tornado 的 `SrvDo27` 负责原版游走路径。当前 riiablo
既没有 SrvDo118 专用分支，local authoritative 门禁也未放行 118，因此普通 generic missile
回退不能视为等价实现。

### Arctic Blast 与 Inferno 共用持续施法状态机

`SrvSt11` 通过 `SKILLS_StartInferno` 创建或刷新 `STATE_INFERNO`，`SrvDo019` 的每个持续
施法回调再调用 `SKILLS_DoInferno`。后者遵循首 tick 规则，并把 `calc1` 写成流导弹范围。
当前起手 case 11 是空操作，generic missile 只能创建一次普通投射物，缺失持续状态、刷新和
原版范围所有权。

### Spirit of Barbs 当前是确认的消费断链

光环投射能算出正确 Barbs 数值，但 `DruidSkills.applySummonAuraModifiers` 把
`thorns_percent` 只写入 `UnitState.runtimeValue`；`StateUpdater.applyThorns` 实际读取
`Stat.thorns_percent` 聚合贡献。两端没有连接，因此并非“缺一个测试”，而是最终反伤为零的
生产缺口。

### Cyclone Armor 是防御侧缺口，仍需登记

Cyclone Armor 不产生对外伤害，所以逐等级输出应标 N/A；但它与其他 `SrvDo018` 技能一样，
应安装可消耗的元素吸收状态。当前分派只覆盖 Sorceress armor、Venom、Bone Armor 和
Holy Shield，Cyclone Armor 会落空。伤害审计必须保留该缺口，避免把“无输出”误写成“完整”。

## riiablo 缺口明细

1. **Molten Boulder**：玩家 local path 的 SrvDo000 被提前过滤；即使进入 generic path，
   当前还把 `pSrvDoFunc == 7` 统一当作 guided missile，未复现滚动、火径、击退和爆裂链。
2. **Arctic Blast**：没有 SrvSt11 的持续状态和 `SKILLS_DoInferno` tick/range 语义。
3. **Cyclone Armor**：没有状态安装、剩余吸收量和 incoming elemental 消耗路径。
4. **Twister / Tornado**：没有 SrvDo118 多枚初始化、Twister stun 和 Tornado SrvDo27 路径。
5. **Shock Wave**：5 枚导弹创建时传入 `null sharedHitTargets`；现有专项测试稳定失败，
   因而一次施法缺少原版的单目标共享命中门禁。
6. **Spirit of Barbs**：光环值没有进入 `Stat.thorns_percent`，反伤消费者读不到它。

这些缺口本轮只登记，不在尚未建立逐等级黄金值前直接修改生产技能逻辑。

## 下一步

DMG-03B 累计完成 Sorceress、Paladin、Necromancer、Barbarian、Druid 共 150/210 项；
本项完成比例 62.5%，加权贡献 7.5%，总加权进度 25.5%。下一职业为 Assassin
（技能 ID 251–280）。
