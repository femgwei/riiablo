# Barbarian 技能伤害所有者审计

本文件完成 DMG-03B 的 Barbarian 部分：技能 ID 126–155，共 30 项。结论以 Diablo II
1.10f 的 `Skills.txt` / `Missiles.txt` 和 D2MOO 调用路径为准；本阶段只确认伤害所有者、
结算职责和 riiablo 覆盖状态，不写入逐等级 `expected_*` 黄金值。

机器可检查的逐项证据见 `skill-damage-barbarian-ownership.tsv`。

## 结论

- 30/30 项均已确认原版所有者和 D2MOO 路径。
- 18 项会直接产生或修改伤害：7 个武器/投掷主动技能、6 个武器专精、Taunt、Shout、
  Battle Cry、Frenzy、Whirlwind、Berserk 和 War Cry（部分技能同时属于这些类别）。
- 12 项没有独立伤害包；其中 Leap 虽无伤害值，仍有玩家落地击退的实现缺口。
- 7 项为确认的 riiablo 实现缺口：Bash、Leap、Double Swing、Stun、Double Throw、
  Leap Attack、Concentrate。
- 2 项为聚焦测试缺口：Taunt、Battle Cry 的负 `damagepercent` 状态已正确构建，尚无
  生产战斗用例证明最终怪物物理伤害只降低一次。

## 关键原版语义

### SrvSt32 不是一个普通攻击占位符

`SKILLS_SrvSt32_Conversion_Bash_Stun_Concentrate_BearSmite` 在命中成功时先建立
`D2DamageStrc`：`calc1` 写入增强物理伤害，`calc4` 可控制元素转换，`SrcDam` 决定
武器包比例；随后 `calc2 << 8` 直接加到物理伤害，并安装技能自己的状态列表。
`SrvDo002` 只负责消费这份已准备记录和命中后状态。当前 `Actioneer` 的 start case 32
无条件调用 `prepareConversion`，后者拒绝 `SrvDoFunc != 79` 的技能，因此 Bash、Stun、
Concentrate 不能用后续的“普通攻击回退”来视为已实现。

### 双持技能按“每个手、每个关键帧”审计

- Double Swing：`SrvDo070` 在交替帧重新选目标，再调用 SrvSt32 产生一份当前手武器记录。
- Double Throw：`SrvDo074` 从当前手投掷物取得 missile，向该 missile 追加技能 ToHit 和
  `calc1` damage-percent。
- Frenzy：`SrvDo009` 每个序列事件只产生一份手部记录；上一次成功命中才在下一事件开始
  时增加速度层数，层数上限等于当前技能等级。
- Whirlwind：`SrvDo076` 按武器速度断点周期触发；双持时一次脉冲可以产生两份记录。
  因而黄金矩阵必须记录“单手单脉冲”，不能把整段动画写成一个模糊总伤害。

### Leap 与 Leap Attack 不是同一路伤害

Leap 的玩家落地回调使用 `calc1` 作为击退半径，没有独立伤害数值。Leap Attack 则在
落地后重新取得近战目标，以 `calc1` 增强的 `SrcDam` 武器记录造成一次命中。当前 riiablo
只有怪物 Leap 的镜像落点移动路径，不能作为玩家 Leap 或 Leap Attack 的完成证据。

### War Cry 的伤害属于技能关联导弹

`SrvDo068` 先建立战吼 wave。War Cry 的导弹由通用 skill-linked missile 计算读取
`Skills.txt` 物理曲线，`MISSMODE_SrvDmg07_Warcry_ShockWave` 只追加 stun length 和
hit class。Howl、Shout、Battle Orders、Battle Command 的同类 wave 不因此获得普通伤害。

### 武器专精是按物品类型分层的修改器

六个 Mastery 的被动列表分别保存 ToHit、damage-percent 和 critical。D2Common 在具体
武器包建立时按物品类型选择最高匹配值；Throwing Mastery 还要求“可投掷物 + 远程投掷
技能”上下文。它们没有自己的 min/max，但必须在 DMG-06 的武器场景中作为独立输入维度。

## riiablo 缺口明细

1. **Bash / Stun / Concentrate**：SrvSt32 起手被 Conversion 专用门禁拒绝；技能增强、
   固定物理量、击退/眩晕或自身防御状态均未进入权威战斗记录。
2. **Double Swing**：没有 SrvDo070 双手序列、交替手和二次目标选择。
3. **Double Throw**：没有 SrvDo074 的每手投掷 missile、ToHit 与 damage-percent 附加。
4. **Leap**：现有实现是怪物镜像落点；缺玩家 `AuraRangeCalc` 范围限制和落地击退波。
5. **Leap Attack**：没有 SrvSt41/SrvDo078 状态机与落地后的增强武器记录。

这些缺口本轮只登记，不在尚未建立逐等级黄金值前直接修改生产技能逻辑。

## 下一步

DMG-03B 累计完成 Sorceress、Paladin、Necromancer、Barbarian 共 120/210 项；本项完成
比例 50.0%，加权贡献 6.0%，总加权进度 24.0%。下一职业为 Druid（技能 ID 221–250）。
