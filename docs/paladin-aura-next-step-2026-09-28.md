# Paladin Aura 对照核验下一步

更新时间：2026-09-28

## 目的

本阶段继续执行 `docs/skill-porting-matrix.md` 的 Paladin Aura 对照工作。目标不是
覆盖或重写已由用户验证过的 Amazon 实现，而是把 dark-magic 已完成的 Aura 行为族和
测试结构，与 riiablo 当前实现逐技能、逐生命周期比较；只有在 dark-magic 或 D2MOO
证据显示行为更完整时，才移植行为或测试。

版本边界仍然是：riiablo 以 Diablo II 1.10f/D2MOO 为行为和数值基线，dark-magic 的
1.14d 数值不能直接复制。dark-magic 可优先借鉴 owner/source 身份、状态生命周期、
固定 pulse 事务、checkpoint/replay 和测试组织方式。

## 当前待办

### 1. Aura pulse 资源扣除顺序

- 为 Might、Resist Fire 等没有合法范围目标的场景补充回归测试。
- 验证有合法目标但没有产生有效 stat/state 时的扣蓝行为。
- 单独验证 Prayer、Cleansing、Meditation 的周期 pulse 必须有完整 mana 才执行。
- 验证 Holy Fire、Holy Freeze 等伤害 Aura 不因目标为空而错误扣除一次 pulse mana。
- 验证 Redemption 只消费合法尸体，并且一次成功 pulse 只消费一次尸体。
- 当前风险点：`AuraManager.pulse()` 在扫描目标之前调用 `consumeMana()`，且 native
  Aura 默认按技能 mana cost 扣除。D2MOO `SKILLS_SrvDo065_BasicAura`/
  `SKILLS_SrvDo066` 的调用链显示，目标回调实际成功应用效果后才应决定带目标 Aura 的
  pulse 资源消耗；需要先由测试固定 riiablo 的预期，再决定是否调整实现。

### 2. Aura 来源和优先级

- 两个同状态 Aura 重叠时，高技能等级胜出。
- 同等级来源按稳定 caster/source ID 排序，结果不得随实体遍历顺序变化。
- 不同状态 Aura 可以同时存在。
- Aura 取消、换选、离开范围、死亡、失去资格和重连后清理旧 source。
- 资源不足时不应改变当前选中的 Aura 目标关系。

### 3. 两层测试

- 纯数据层：验证 native skill ID、`srvstfunc/srvdofunc`、技能等级、mana、半径、
  `perDelayFrames`、目标过滤和状态 ID。
- ECS/集成层：验证 owner state、target state、pulse、伤害/治疗/净化、尸体消费、
  多来源优先级和清理。
- 如涉及客户端接线，再运行真实 MPQ/客户端门槛；不能用合成 UI 场景替代。

## d2client.dll 静态检查是否纳入

应纳入，但作为第三类证据，而不是取代 D2MOO 和 ECS 回归测试。

### 适合由 d2client.dll 静态检查确认的内容

- 客户端技能表/技能 ID 到客户端函数或行为入口的映射。
- `srvstfunc`、`srvdofunc`、动画关键帧、客户端预测和表现事件是否接线一致。
- Aura 选择、取消、图标/范围表现、导弹或特效 helper 的客户端生命周期。
- 客户端是否会重复发送 keyframe、重复播放 impact，或在状态失效后继续表现。
- 版本差异：1.10f 客户端与 1.14d dark-magic 参考之间的函数号和数据布局变化。

### 不能单独由 d2client.dll 静态检查决定的内容

- 服务端目标合法性、敌我过滤、墙/范围判定。
- mana 是否在“有有效效果”后扣除，以及资源不足时的原子性。
- 状态来源优先级、同等级稳定排序、死亡/离开范围后的 ECS 清理。
- Redemption 尸体合法性和一次性消费。
- D2MOO 中由服务端 `SKILLS_SrvDo*`、`SKILLS_AuraCallback_*` 和 Excel 数据共同决定
  的 1.10f 数值与公式。

### 推荐的证据链

1. 先用 D2MOO/1.10f 数据和反编译函数确认规则与数值。
2. 用 dark-magic manifest、Aura 系统和 Lua 测试提取可复用的行为族/事务断言。
3. 在 riiablo 纯 Java 数据测试中锁定边界和版本假设。
4. 在 riiablo ECS 集成测试中验证可观察状态、资源和清理。
5. 用 d2client.dll 静态检查补充客户端接线、预测和表现侧证据。
6. 对无法由静态分析证明的内容，保留为真实 1.10f MPQ/双客户端待验收项。

## 完成判定

Paladin Aura 只有在上述关键场景的纯数据层和 ECS 层测试均通过，并且客户端静态检查
没有发现 ID/函数号/关键帧接线冲突后，才能把对应技能标记为“已对照”。如果 riiablo
当前行为已经符合 D2MOO 且比 dark-magic 更贴近 1.10f，则只移植 dark-magic 的测试
结构，不覆盖 riiablo 行为。

本文件只记录核对计划和证据边界；任何实现修改仍须单独提交、运行相关测试并推送。

## 第一项实证结果（2026-09-28）

已完成 Prayer 的第一轮 D2MOO ↔ riiablo ECS 对照。D2MOO 的
`SKILLS_SrvDo065_BasicAura` 先按当前 mana 决定本次 stat/effect 是否可用，
但不会因为 mana 不足取消已选 Aura；扫描回调只有在确实产生有效效果时才允许
`D2GAME_SKILLMANA_AuraConsume_6FD10C90` 扣除 pulse cost。riiablo 已按这一顺序
修正并加入：

- 满生命时 Prayer 不扣 mana；
- mana 不足时不治疗、不取消 Aura，并在下个 pulse 重试；
- mana 足够且实际恢复生命时只扣一次 native cost。

对应回归位于 `AuraEcsScenarioTest` 的
`prayerSpendsManaOnlyWhenItsPulseActuallyRestoresLife` 和
`unfundedPrayerKeepsSelectionAndRetriesOnTheNextPulse`。已通过：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.server.AuraEcsScenarioTest
./gradlew.bat :core:test --tests "com.riiablo.engine.server.*Aura*Test" --tests com.riiablo.engine.server.AuraEcsScenarioTest
```

这项修正只覆盖已由 D2MOO 证实的资源/生命周期差异；Holy Fire、Holy Freeze、
Redemption 和多来源优先级仍按待办中的独立场景继续核对。

## Redemption 对照结果（2026-09-28）

D2MOO `SKILLS_SrvDo082_Redemption` 通过 `args2.nCounter` 判断本次范围扫描是否
至少成功消费一具合法尸体；没有成功消费时只清除 mana-regeneration suppression，
不扣 pulse mana，也不因一次失败而取消选中的 Aura。riiablo 现在让
`applyRedemptionEffect` 返回“是否成功消费尸体”，并将该结果纳入统一的
`pulseUseful` 结算。

新增 `AuraManagerPulseTest.redemptionConsumesManaOnlyAfterACorpseIsActuallyConsumed`，
覆盖空范围失败 pulse 与下一次成功 pulse 的扣蓝边界。Holy Freeze 的冷免疫过滤和
Shatter 生命周期已有 `PaladinSpecialAuraIntegrationTest` 覆盖，后续将继续核对
不同来源/不同状态的胜者关系。

## 不同状态叠加结果（2026-09-28）

已将 dark-magic 的 `different_selected_aura_states_stack_on_each_party_member`
映射为 riiablo 的 `AuraEcsScenarioTest.differentAuraStatesStackOnTheSamePartyMember`。
测试确认 Might 与 Prayer 可以同时存在于同一队友：Might 的伤害加成保留，Prayer
仍可执行治疗；两者不会因为均为 party Aura 而相互替换。
