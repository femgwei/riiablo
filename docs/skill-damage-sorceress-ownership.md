# Sorceress 技能伤害所有者审计

更新时间：2026-10-09
审计基线：Diablo II 1.10f  
范围：DMG-03B 的 Sorceress 子项，技能 ID 36–65

机器可读明细见 `skill-damage-sorceress-ownership.tsv`。本阶段只确认伤害由谁拥有、原版如何进入该路径、riiablo 从哪里执行；尚未把 1–20 级数值写入黄金矩阵。

## 结论

- 30/30 个技能已逐项分类，ID 连续且无重复。
- 20 个技能产生直接固定/随机伤害，1 个 Static Field 使用当前生命百分比，1 个 Hydra 通过召唤物间接造成伤害，4 个技能只修改其他伤害，4 个技能没有对外伤害。
- 普通法术导弹的基准伤害通常由 `Skills.txt` 拥有。`Missiles.txt` 主要拥有飞行、碰撞、范围、父子导弹、周期与命中回调。D2MOO 的 `MISSILE_CalculateDamageData` 根据 missile 的 Skill/MissileSkill 选择技能表曲线，不能仅看导弹名判断数值所有者。
- Inferno、Blaze、Fire Wall、Meteor 的火焰流/持续火焰必须保留 8.8 rate 与
  `DamageRate` 语义：
  `SrvDo05` 每个游戏帧执行碰撞，`DamageRate` 只按 `value / 1024` 缩放平面 DR/MDR，
  不是攻击间隔；Meteor 的撞击和地面火焰要在后续黄金值中拆成不同场景。
- Static Field 不属于普通逐等级 min/max 曲线。它以 `calc1` 计算当前生命百分比，以 `calc2` 提供最小固定伤害，并受难度生命下限约束。
- Enchant、Fire/Lightning/Cold Mastery 没有独立命中包。它们通过状态或被动属性改变后续伤害，因此后续黄金矩阵不能把它们伪装成 0–0 伤害技能。

## 已确认的 riiablo 缺口

- Energy Shield：存在 `DefenseCalculator.applyEnergyShield`，但没有找到 SrvDo023 把技能状态安装到角色的路径。
- Lightning Mastery：导弹伤害解析器会读取 `passive_ltng_mastery`，但 `StateUpdater.synchronizeSorceressPassives` 目前只同步 Warmth 和 Fire Mastery。
- Cold Mastery：伤害结算会读取 `passive_cold_pierce`，现有测试也能人工注入该值，但没有从角色技能等级同步被动状态。

这些缺口不在本子项中直接修复。它们会进入 DMG-04/DMG-05 前的实现修复队列，否则无法用生产路径生成可信的 `riiablo_actual_*`。

Telekinesis 缺口已在 DMG-04 审核中关闭：`ServerSkillSystem.applyTelekinesis` 现在按
`SrvSt12/SrvDo021` 验证玩家施法者、单位目标、敌对关系、城镇和距离，直接结算
Skills.txt 物理/元素伤害并应用 Lightning Mastery、抗性、免疫、吸收及 PvP 缩放，且
不会创建导弹实体。物品/对象交互与击退概率仍属于行为审计，不冒充本次伤害黄金值。

## 已实现但测试仍不足的路径

- Inferno：`SrvDo019` 已改为每次只创建一个 `SrvMissileA`，按 `calc1` 设置射程并保留
  8.8 定点脉冲伤害；等级 1–20 和单次实际命中已有测试。`SrvSt11` 状态刷新、耗蓝、
  重复脉冲及障碍截断仍属于 DMG-07 行为缺口。
- Lightning：通用技能导弹路径已存在，但缺少法师 Lightning 的生产伤害集成测试。
- Hydra：三只召唤物的创建、归属和偏移已有测试；仍缺少 Hydra 单枚火球继承技能等级与伤害的断言。

## 后续黄金值约束

1. 普通技能导弹从 Skills.txt 曲线进入 `MISSILE_CalculateDamageData`，再应用协同、HitShift 和 mastery；不得直接拿 Missiles.txt 的同名行数值替代。
2. Shiver Armor 和 Telekinesis 是直接回调伤害，不经过普通导弹创建路径。
3. Chilling Armor、Thunder Storm、Blizzard、Frozen Orb、Meteor 和 Hydra 必须验证父子/状态/召唤链是否保留源技能 ID 与等级。
4. Static Field 需要生命值、难度和抗性场景矩阵；不存在一组脱离目标生命的固定 min/max 黄金值。
5. 被动和无伤害技能用明确 N/A 理由收口，不填数值 0。

Shiver Armor 的 DMG-04 等级 1–20 基础范围已经批准：等级 1 为 `6–8`，等级 20 为
`60–71`，冷长度为 100–500 帧。矩阵保存规范 getter 范围；原版有限随机数不包含最大
端点，且 `EventFunc03` 写入普通 cold 长度而不是 freeze 长度；riiablo 的反击随机和
状态路径均已同步修正。

Chilling Armor 的 DMG-04 等级 1–20 基础范围已经批准：等级 1 为 `4–6`，等级 20 为
`39–50`，cold 长度为 100–400 帧。`EventFunc01` 只响应带 `ReturnFire` 标志的入射导弹，
返回弹继承技能 60 和护甲等级，并从 Skills.txt 获取伤害；返回弹本身不带 `ReturnFire`。
原版导弹随机不包含最大 getter 端点，riiablo 的通用导弹结算已同步修正。

Warmth 的 DMG-04 等级 1–20 已按明确 N/A 批准。它不创建导弹或战斗伤害包；
1.10f `PassiveCalc=ln12`、`Param1=30`、`Param2=12`，在永久 `warmth` 状态上写入
`manarecoverybonus=30%..258%`。D2MOO `EVENTS_ManaRegen` 将该百分比与基础恢复相乘，
不是对目标生命或法力造成伤害。riiablo 现已按表公式安装状态并由
`ManaRecoverySystem` 消费；伤害 expected/actual/delta 字段保持空白。

Frozen Armor 的 DMG-04 等级 1–20 也按明确 N/A 批准。`SrvDo018` 安装互斥的
`frozenarmor` 状态：零 Shiver Armor/Chilling Armor 硬点协同时，防御加成为
30%–125%，持续 3000–8700 帧。`EventFunc02` 只在近战物理受伤后写入
30–87 帧 `dwFrzLen`；它不写物理/元素伤害，也不创建导弹。riiablo 的状态安装、
护甲互斥和受击冻结路径已由逐级黄金测试与集成测试锁定。

Enchant 的 DMG-04 等级 1–20 已按目标状态的一份火焰伤害贡献批准。零 Warmth
硬点、零 Fire Mastery 时，`enma/exma` 产生 `8–10` 到 `68–89` 火焰伤害，
`ln12` 产生 3600–15000 帧持续时间，`toht` 产生 20%–191% 命中率。
`SrvDo025` 不立即结算攻击，而是把这些统计安装到友方目标；后续武器命中消费该状态。
riiablo 已修正为在协同和精通计算完成前保留原生 8.8 定点精度。

Teleport 的 DMG-04 等级 1–20 已按明确 N/A 批准。1.10f `Skills.txt#54` 的物理、
元素和 server missile 字段均为空或 0；`SrvDo027` 只读取目标坐标，检查当前关卡的
`Levels.Teleport` 与飞行碰撞，再调用 `sub_6FCBDFE0` 搜索安全落点并移动单位。
riiablo 的 `Actioneer.resolveTeleport` 对应执行同一位移链，专项测试确认成功位移不会创建
导弹或发出 `DamageEvent`；矩阵伤害 expected/actual/delta 字段保持空白。

## 可复现验证

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.engine.server.SorceressDamageOwnershipTest --no-daemon
```

该测试同时核对 TSV 的 30 行完整性、Skills.txt 的 ID/名称/回调一致性、所有伤害路径的 D2MOO 与 riiablo 引用，以及四个已确认缺口不会被误标为已实现。
