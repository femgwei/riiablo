# Sorceress 技能伤害所有者审计

更新时间：2026-10-07  
审计基线：Diablo II 1.10f  
范围：DMG-03B 的 Sorceress 子项，技能 ID 36–65

机器可读明细见 `skill-damage-sorceress-ownership.tsv`。本阶段只确认伤害由谁拥有、原版如何进入该路径、riiablo 从哪里执行；尚未把 1–20 级数值写入黄金矩阵。

## 结论

- 30/30 个技能已逐项分类，ID 连续且无重复。
- 20 个技能产生直接固定/随机伤害，1 个 Static Field 使用当前生命百分比，1 个 Hydra 通过召唤物间接造成伤害，4 个技能只修改其他伤害，4 个技能没有对外伤害。
- 普通法术导弹的基准伤害通常由 `Skills.txt` 拥有。`Missiles.txt` 主要拥有飞行、碰撞、范围、父子导弹、周期与命中回调。D2MOO 的 `MISSILE_CalculateDamageData` 根据 missile 的 Skill/MissileSkill 选择技能表曲线，不能仅看导弹名判断数值所有者。
- Blaze、Fire Wall、Meteor 的持续火焰必须保留 8.8 rate 与 `DamageRate` 语义：
  `SrvDo05` 每个游戏帧执行碰撞，`DamageRate` 只按 `value / 1024` 缩放平面 DR/MDR，
  不是攻击间隔；Meteor 的撞击和地面火焰要在后续黄金值中拆成不同场景。
- Static Field 不属于普通逐等级 min/max 曲线。它以 `calc1` 计算当前生命百分比，以 `calc2` 提供最小固定伤害，并受难度生命下限约束。
- Enchant、Fire/Lightning/Cold Mastery 没有独立命中包。它们通过状态或被动属性改变后续伤害，因此后续黄金矩阵不能把它们伪装成 0–0 伤害技能。

## 已确认的 riiablo 缺口

- Energy Shield：存在 `DefenseCalculator.applyEnergyShield`，但没有找到 SrvDo023 把技能状态安装到角色的路径。
- Lightning Mastery：导弹伤害解析器会读取 `passive_ltng_mastery`，但 `StateUpdater.synchronizeSorceressPassives` 只同步 Fire Mastery。
- Cold Mastery：伤害结算会读取 `passive_cold_pierce`，现有测试也能人工注入该值，但没有从角色技能等级同步被动状态。

这些缺口不在本子项中直接修复。它们会进入 DMG-04/DMG-05 前的实现修复队列，否则无法用生产路径生成可信的 `riiablo_actual_*`。

Telekinesis 缺口已在 DMG-04 审核中关闭：`ServerSkillSystem.applyTelekinesis` 现在按
`SrvSt12/SrvDo021` 验证玩家施法者、单位目标、敌对关系、城镇和距离，直接结算
Skills.txt 物理/元素伤害并应用 Lightning Mastery、抗性、免疫、吸收及 PvP 缩放，且
不会创建导弹实体。物品/对象交互与击退概率仍属于行为审计，不冒充本次伤害黄金值。

## 已实现但测试仍不足的路径

- Inferno：生产路径能建立 Inferno 状态并创建技能导弹，但缺少法师专用的流持续时间、脉冲伤害和障碍截断集成测试。
- Lightning：通用技能导弹路径已存在，但缺少法师 Lightning 的生产伤害集成测试。
- Hydra：三只召唤物的创建、归属和偏移已有测试；仍缺少 Hydra 单枚火球继承技能等级与伤害的断言。

## 后续黄金值约束

1. 普通技能导弹从 Skills.txt 曲线进入 `MISSILE_CalculateDamageData`，再应用协同、HitShift 和 mastery；不得直接拿 Missiles.txt 的同名行数值替代。
2. Shiver Armor 和 Telekinesis 是直接回调伤害，不经过普通导弹创建路径。
3. Chilling Armor、Thunder Storm、Blizzard、Frozen Orb、Meteor 和 Hydra 必须验证父子/状态/召唤链是否保留源技能 ID 与等级。
4. Static Field 需要生命值、难度和抗性场景矩阵；不存在一组脱离目标生命的固定 min/max 黄金值。
5. 被动和无伤害技能用明确 N/A 理由收口，不填数值 0。

## 可复现验证

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.engine.server.SorceressDamageOwnershipTest --no-daemon
```

该测试同时核对 TSV 的 30 行完整性、Skills.txt 的 ID/名称/回调一致性、所有伤害路径的 D2MOO 与 riiablo 引用，以及四个已确认缺口不会被误标为已实现。
