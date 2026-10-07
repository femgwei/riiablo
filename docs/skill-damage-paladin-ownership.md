# Paladin 技能伤害所有者审计

更新时间：2026-10-07  
审计基线：Diablo II 1.10f  
范围：DMG-03B 的 Paladin 子项，技能 ID 96–125

机器可读明细见 `skill-damage-paladin-ownership.tsv`。本阶段只确认伤害所有者、D2MOO 调用路径和 riiablo 生产路径，不向黄金矩阵写入未经独立验证的逐级 `expected_*`。

## 结论

- 30/30 个 Paladin 技能已逐项分类，ID 连续且无重复。
- 14 个技能会直接、周期、反射或通过武器造成伤害；11 个技能只修改其他伤害；5 个技能没有对外伤害。
- 普通 Paladin 光环并非一种统一的“加属性”路径：`SrvDo065` 只投射基础状态，`SrvDo066` 同时拥有自身被动列表、敌方状态和周期伤害，Holy Freeze 使用独立的 `SrvDo081`，Redemption 使用尸体回调 `SrvDo082`。
- Holy Fire、Holy Freeze、Holy Shock 同时拥有两种伤害结果：周期脉冲使用 Skills.txt 元素曲线，拥有者身上的被动 stat list 则把同一曲线投射到武器命中。黄金值必须分场景记录。
- Fist of the Heavens 是分裂所有者：中心雷电来自 Skills.txt，延迟控制和 Holy Bolt 子弹数量来自 delay missile，而子弹魔法伤害来自 Missiles.txt。
- Blessed Hammer 的 Skills.txt 魔法曲线先应用 Vigor/Blessed Aim 硬点协同，再把施法瞬间的 Concentration 特例加成快照到导弹；不能把 Concentration 当成通用魔法主修。

## 已确认的 riiablo 伤害缺口

- Sacrifice（96）：原版 `SrvSt29` 先把 `calc1` 增强伤害写进物理战斗记录，`SrvDo064` 再以该物理记录（并按目标当前生命封顶）计算 `calc2` 自伤。riiablo 的目标伤害后乘逻辑会连元素总伤害一起放大，而自伤反而使用未乘技能增强、且已经过目标物理减免的 `combat.physicalDamage`。
- Smite（97）：原版玩家分支在盾牌基础伤害和 Holy Shield 伤害后应用 Smite `calc1`。riiablo 玩家分支没有应用该技能增强伤害；现有 `SmiteIntegrationTest` 只覆盖怪物 A2 分支。
- Zeal（106）：原版把 `calc2` 写入 `dwEnDmgPct`，它是物理武器增强伤害。riiablo 对 `combat.totalDamage` 和元素展示通道整体乘该比例，会把装备元素伤害一并放大。
- Charge（107）：与 Zeal 相同，原版 `calc1` 是 `dwEnDmgPct` 物理增强项；riiablo 玩家路径把总伤害和元素通道一起放大。
- Holy Shield（117）：原版 Smite 通过 `SKILLS_GetMinPhysDamage` / `SKILLS_GetMaxPhysDamage` 读取 Holy Shield 的物理 MinDam/MaxDam 曲线。riiablo 却把 Holy Shield `calc1`（防御公式）当作同一个平坦伤害同时加到最小和最大值。

本子项不直接修复上述代码。它们必须在 DMG-04 生成 Paladin 黄金值之前进入实现修复和回归队列，否则 `riiablo_actual_*` 会系统性偏离原版。

## 已实现但测试仍不足的路径

- Conversion（116）：公式、状态到期恢复和生产入口存在，但缺少一次完整的“武器命中—成功/失败转化—状态期满”生产链测试。
- Conviction（123）：目标筛选和状态公式已有测试，但缺少生产伤害测试证明物理防御与三种元素抗性惩罚在最终结算中只应用一次。

## 后续黄金值约束

1. Sacrifice、Zeal、Charge、Might、Concentration 和 Fanaticism 的增强伤害属于物理武器分量，不能无条件乘整个物理+元素总包。
2. Smite 的基础是盾牌 Items.txt MinDam/MaxDam；Holy Shield 追加的是技能物理 MinDam/MaxDam；Smite 自身 `calc1` 再作为增强伤害应用。三层必须分开验证。
3. Thorns 没有脱离来袭命中的固定 min/max；场景必须提供来袭物理伤害和攻击者类型。
4. Holy Fire、Holy Freeze、Holy Shock 要分别保存周期脉冲与武器附加元素伤害，不能把两者相加伪装成“一次施法伤害”。
5. Fist of the Heavens 至少拆成中心雷电、单枚 Holy Bolt 和整次分裂三个结果；子弹数量与合法亡灵目标数相关。
6. 无伤害技能用明确 N/A 原因收口，不能填 0–0 冒充已验证黄金值。

## 可复现验证

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.engine.server.PaladinDamageOwnershipTest --no-daemon
```

该门禁核对 30 行完整性、Skills.txt 的 ID/名称、每项 D2MOO/riiablo/测试证据，以及 5 个已确认实现缺口不会被静默标记为已实现。
