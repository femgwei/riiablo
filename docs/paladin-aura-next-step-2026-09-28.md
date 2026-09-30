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

已补齐一项 dark-magic 对照缺口：Cleansing 的 self 目标不能只挂上 AuraState，
还必须在同一周期 pulse 中执行净化缩短。`AuraManagerPulseTest` 已锁定施法者和
盟友都会收到 `applyCleansingEffect`；其余 poison/curable 状态判定仍由
`StateUpdater` 按 1.10f `States.txt` 的 `curable` 位执行。

本轮又补充 `AuraManagerPulseTest.paidAuraWithNoValidRangeTargetKeepsSelectionAndDefersManaUntilUsefulPulse`：
付费 Aura 在范围扫描没有合法目标时保持已选状态、不扣 mana、不发布 stat layer；
下一次出现合法目标并真正提交效果后，才扣除一次 native pulse cost。该测试固定了
D2MOO `SrvDo065/SrvDo066` 的“有效效果后结算资源”顺序，避免空 pulse 改写来源关系。

随后新增 `damageAuraSelfLayerDoesNotCountAsUsefulWithoutAValidDamageTarget`，暴露并
修正了一个实现差异：`AURA_TYPE_DAMAGE` 的施法者被动/self layer 不能单独把
`pulseUseful` 置真。D2MOO `SKILLS_SrvDo066_HolyFire_HolyShock_Sanctuary_Conviction`
先建立 self layer，再由 hostile damage scan 的 `field_40` 决定扣 mana；现在
`AuraManager` 只在伤害 Aura 的非 self 目标产生有效 stat/damage 时结算 pulse cost。
同一回归随后切换目标为合法状态，确认下一次 pulse 恰好扣除一次 mana，确保修正
没有把正常的伤害 Aura 资源结算一并屏蔽。

另外新增 `AuraManagerPulseTest.unfundedStrongerSameStateAuraKeepsItsWinnerRelation`：
高等级同状态来源在本次 pulse 无法支付 mana 时，仍保留 native winner/source
关系并发布零值短层，不会错误回退到较弱来源；下一次资金恢复后仍由原来源继续
刷新。这与 D2MOO 的“资源失败跳过本次有效值，但不取消已选 Aura”语义一致。

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

## 周期光环链接治疗结果（2026-09-30）

对照 dark-magic `periodic_aura_skills_test.lua` 与 1.10f `Skills.txt` 后发现，
Cleansing 和 Meditation 的第二个 AuraStat 不是常量，而是
`skill('Prayer'.edns)`。原 `SkillFormula` 只支持 `.blvl/.lnXY/.dmXY`，因此该
引用被静默解析为 0，两个光环只发布了状态/净化/法力恢复，却漏掉了 Prayer-linked
治疗。现在解析器支持引用技能的 `.edns/.enms/.edxs/.exms`，保留原生 8.8 fixed
精度；AuraManager 同时传入真实技能行解析器。新增回归覆盖：

- `Cleansing` 在同一个 pulse 为施法者和队友缩短毒素并按 Prayer 等级治疗；
- `Meditation` 在发布 `manarecoverybonus` 的同时为施法者和队友按 Prayer 等级治疗。

这项修正没有改动 Amazon，也没有把 dark-magic 的 1.14d 数值带入 riiablo；只补上
D2MOO/1.10f 已存在的链接公式语义。

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

## 同状态来源结果（2026-09-28）

D2MOO `sub_6FD10EC0` 按目标 state 查找已有 stat-list；如果来源 skill 不同，
旧 stat-list 会被移除后再建立，而不是让两个 skill 层并存。riiablo 原先把
`skillId` 放进胜者 key，可能造成错误叠加；现在已改为 `target + state`，并以
`AuraManagerPulseTest.differentSkillsUsingOneStateShareOneNativeWinnerSlot`
锁定该行为。原有“同技能高等级胜出、同等级按 caster ID 稳定排序”测试继续通过。

## 同状态来源生命周期回归（2026-09-30）

在上述同状态 winner 规则基础上，新增
`AuraManagerPulseTest.differentSkillsUsingOneStateReplaceAndRestoreWithoutLeavingHiddenEffects`，
覆盖 dark-magic 测试中 source replacement/removal 的生命周期边界：

- 不同 skill 共享同一 state 时，高等级来源只发布一个公开 AuraEffect；
- 胜者取消后，旧 skill 的公开来源立即移除，较弱来源在下一个 native pulse 恢复；
- 同一 caster 切换选中 skill 时，旧 skill layer 不会残留或与新 skill 叠加；
- 最终取消后，目标没有隐藏的同状态公开效果。

本轮仅补充回归证据，没有覆盖用户已验证的 Amazon 修改，也未改变 Paladin Aura
运行时代码。`AuraManagerPulseTest` 与 `AuraEcsScenarioTest` 专项测试均通过；
下一项继续核对来源失去资格（死亡/离开范围/重连）时 ECS 状态层的短暂
`perdelay + 1` 过期和重新选主语义。

## 失去资格与重连快照回归（2026-09-30）

继续补充 `AuraEcsScenarioTest` 的 ECS 证据：

- `fanaticismExpiresAfterRangePulseAndUsesItsNativeAttackRateStat` 现在同时验证
  目标离开范围后状态过期，以及重新进入范围后同一来源在下一次 native pulse
  重新发布状态；
- 新增 `paladinAuraSnapshotReplacesStaleSourceLayerAcrossReconnect`，用真实
  `StateSerializer`/`StateP` 序列化路径验证重连快照保留 Paladin Aura 的
  `sourceEntityId`、`skillId`、等级和持续时间，并清除 replica 中旧的来源层。

来源死亡后的 winner 恢复已由
`strongestSameSkillAuraWinsAndWeakerReturnsOnItsNextPulse` 覆盖；本轮没有修改
运行时代码。这样 Paladin Aura 的 server-side 失去资格、重新选主和 client
快照替换均有独立回归证据，仍需后续补充双客户端真实 MPQ 门槛验证。

## 当前真实 MPQ 门槛状态（2026-09-30）

本轮运行完整 Paladin 服务端专项集合并通过：

```text
./gradlew.bat :core:test \
  --tests "com.riiablo.engine.server.*Paladin*Test" \
  --tests com.riiablo.engine.server.AuraManagerPulseTest \
  --tests com.riiablo.engine.server.AuraEcsScenarioTest \
  --no-daemon
```

本轮已新增独立 `headlessPaladinAura` fixture，未复用 `headlessAreaSkill`：
生成 Paladin D2S、建立 owner/party observer、在 D2GS simulation thread 调用原生
Aura selection、验证双方 `StateP.sourceEntityId/skillId`，再断开 observer、重新入队、
重连并验证目标状态快照恢复。当前回归覆盖 Might、Prayer、Resist Fire、Cleansing、
Fanaticism、Meditation、Redemption、Salvation；其中 Fanaticism 和 Redemption 按
native 定义作为 self-only Aura 跳过 party target-state 检查，Redemption 仍单独验证
self state 和重连流程：

```text
./gradlew.bat :server:d2gs:headlessPaladinAura -PpaladinAuraSkill=98 -PpaladinAuraTimeout=90 --no-daemon
./gradlew.bat :server:d2gs:headlessPaladinAuraRegression --no-daemon
```

实现中补充了 Aura 对新 ECS 实体的即时重发布：实体重连后即使仍处于原 Aura 的
`affectedEntities` 集合，也会按当前胜者重新写入状态层，不必等待下一次 `perdelay`。
重连门槛同时显式恢复 party 关系；否则 native ally filter 会正确拒绝已重建的
observer。

同一门槛随后补充跨区域撤销：owner 从 Act I 区域进入 Blood Moor 后，observer 仍在
原区域，Might、Prayer、Salvation 的 target state 均在 `perdelay + 1` 短生命周期内
消失，未发生跨 `Map.Zone` 泄漏。对应日志为 `paladin_aura_cross_area_pass`。后续剩余
工作集中在真实旧客户端的 Aura 图标、动画和范围表现断言。

## 完整 Paladin Aura 清单首轮门槛（2026-09-30）

将 `headlessPaladinAuraRegression` 从代表性四/八项扩展为全部 20 个已注册原生 Aura：
Might、Prayer、Resist Fire、Holy Fire、Thorns、Defiance、Resist Cold、Blessed Aim、
Cleansing、Resist Lightning、Concentration、Holy Freeze、Vigor、Holy Shock、
Sanctuary、Fanaticism、Meditation、Conviction、Redemption、Salvation（其中技能表
实际通过的清单为 20 行，含所有已注册的 Paladin Aura 技能行）。

本轮完整清单运行通过。门槛根据 native `affectsParty` 自动区分：party Aura 验证
owner/ally target state、重连和跨区域撤销；self-only/targetless Aura（Holy Fire、
Holy Shock、Sanctuary、Fanaticism、Redemption）只验证 self state、重连和来源元数据。
Holy Freeze/Conviction 已建立 deterministic hostile monster fixture，并验证 monster
target state 的 source/skill 元数据、observer 重连恢复及 owner 跨区域后的撤销。当前仍
未覆盖所有怪物免疫/不可攻击/noAura 组合；这些属于下一层目标过滤专项，而不是本轮 Aura
生命周期门槛的缺失。

## Hostile Aura 目标过滤矩阵（2026-09-30）

已将 D2MOO 的目标筛选拆成可执行 ECS 断言。`sub_6FD0FA00` 的 `FINDISATT`、
`FINDISSEL` 对应 `MonStats2.isAtt/noSel`，因此 Conviction 对不可攻击或不可选中的
目标不发布 `StateId.CONVICTION`。D2MOO 的 `sub_6FD0FE80` 只有在
`bCheckMonAuraFlag=1` 时检查 `MonStats.noAura`；Conviction（SrvDo066）和 Holy
Freeze（SrvDo081）传 0，所以 Boss、Prime Evil 及 `noAura` 怪物仍进入 hostile scan。
Holy Freeze 随后在 `SKILLS_AuraCallback_HolyFreeze` 依据当前难度的 `coldeffect`
拒绝冷免疫目标。对应测试为
`AuraEcsScenarioTest.hostileAuraFilterMatrixMatchesD2MooBossPrimeNoAuraAndAttackabilityRules`。

真实 MPQ 双客户端入口随后扩展为同一过滤 fixture：Conviction=123 的门槛覆盖 Boss、
Prime Evil、`noAura`、不可攻击和不可选中目标；Holy Freeze=114 覆盖 `noAura` 和
冷免疫目标。每个 fixture 使用独立的 `MonStats/MonStats2` 副本，避免修改共享 Excel
单例导致后创建目标继承前一目标标志。实测两项均通过，且 Holy Freeze 的短 target
state 在 observer 重连及 owner 跨区撤销阶段仍能稳定收敛。
