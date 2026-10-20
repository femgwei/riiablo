# 玩家／佣兵普通武器攻击 8.8 闭环（2026-10-20）

## 范围和原版依据

本批覆盖普通 Attack、Left Hand Swing、普通弓／弩 Attack、Throw／Left Hand Throw。
不将特殊技能的预计算整数伤害包自动迁入新策略。D2MOO revision：
`0c0bbd1e6bef9181a3da1c046523af622a925416`；数据使用本地 1.10f MPQ。

- `D2Game/src/UNIT/SUnitDmg.cpp::SUNITDMG_ApplyDamageBonuses`（147 起）：
  选择当前武器的一手／双手单位端点，转为 8.8，加入 normaldamage，先修正下界和退化区间，
  再分别应用公共 ED 与最小／最大 ED，最后排他抽样。武器采用 Weapons.txt 的
  StrBonus／DexBonus，两项分别截断；无武器才使用完整力量。
- `SUNITDMG_FillDamageValues`（260 起）：钝器对亡灵 +50%；近战暴击依次尝试
  武器精通、被动暴击、致命一击，成功后短路，不将概率直接相加。
- `D2Common/src/Units/Missile.cpp::MISSILE_CalculateDamageData`（467 起）：
  发射时保存端点及 ED，投掷使用 throw 端点，SrcDamage 在 8.8 端点上除以 128；
  较大的最小／最大 ED 作为一个共同百分比，命中抽样后才应用，不套近战端点策略。
- `MISSILE_HasBonusStats`（797 起）：发射时先抽被动暴击，即使概率是零；失败后尝试
  致命一击、武器精通，均不得在飞行中换装后重算。
- `D2Common/src/D2Inventory.cpp::INVENTORY_GetWieldType`（2390 起）及
  `Units/Units.cpp::UNITS_MergeDualWieldWeaponStatLists`（3121 起）：具体手的伤害来源
  不等于两手物理端点之和；双手剑旁的槽位即使放着损坏装备，也仍影响单手模式。
- `D2Game/src/MONSTER/MonsterAI.cpp::MONSTERAI_UpdateMercStatsAndSkills`（185 起）
  将佣兵固有伤害保存在 secondary 端点；`MonsterUnique.cpp::sub_6FC6CF10`（1263 起）
  将非罗格的固有伤害减一后加入模式伤害，`sub_6FC6CF90`（1279 起）则在罗格导弹
  已创建后加入同类增量。无武器佣兵导弹的属性加成是敏捷，不是近战力量。

以上是逆向源码与原始表的核验，不是原版进程逐击捕获；没有引用未读取的网页作为依据。

## 实现

`WeaponAttackSource` 从当前装备槽、活动武器组及佣兵所有者的数据选择具体武器，
读取物品更新后已经包含本地 ED 的端点，避免再次应用武器本地 ED。非武器装备和背包
护符的附加 min/max、全局 ED 单独收集；不将盾牌基础盾击伤害导入普通攻击，也不继承
旧属性更新器在护甲零端点上提前乘入的 ED。镶嵌与活动套装列表保留各自来源。
非武器的每等级附加伤害从原始活动列表读取一次，避免旧更新器的 aggregate／remaining
残留重复；已聚合的物品属性也不再与同名 remaining 残留相加。

`Actioneer` 只对普通近战显式调用新入口；`ServerEntityFactory` 和
`ServerSkillSystem` 对普通远程生成独立 launch packet，并防止重复初始化消耗暴击随机数。
`MissileCollisionSystem` 消费该原生包：命中使用 owner RNG，伤害使用 projectile RNG，
防御使用 defender RNG；不把命中时所有者的当前状态／精通再加到发射快照。

近战端点加成、远程发射端点、暴击、减伤和实际 HP 访问保持 8.8；元素使用原有
原生 source-stat 包，毒素为独立速率与时长。具体手的吸取百分比经伤害事件进入唯一
`DamageSettlement`，继续执行封顶、吸收回血、扣血、死亡和 Vitals 序列化。
旧 `totalDamage` 整数摘要仍仅供兼容，不能用它执行定点伤害扣血。

## 回归入口

```powershell
./gradlew.bat :core:playerCombatRegression :core:monsterCombatAcceptance :desktop:compileJava :server:d2gs:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --console=plain
```

新增独立数值测试 `NativeOrdinaryWeaponTest`，宿主装备／定点／吸取／死亡／客户端
round-trip 测试 `OrdinaryWeaponDamageTest`，实际 Actioneer 普通双持关键帧测试，
弓／弩／投掷实际发射与碰撞测试，以及原始 1.10f 武器系数／普通导弹 SrcDamage 审计。
`:core:playerCombatRegression` 已复验通过：208 类、891 项，失败／错误／跳过均为 0，
包含新增宿主专项 13 项、普通发射与碰撞 3 项、双持关键帧 1 项及原始表审计 1 项。

- `:core:monsterCombatAcceptance` 全部分层通过，包括普通攻击、行为、结算、数据、
  元素、导弹、模式元素、毒素、追击、特殊能力、产怪及架构门禁。
- `:server-domain:monsterCombatMathTest`：25 类、278 项通过（新增独立普通武器数值 7 项）；
  `:server-domain:monsterCombatParityTest`：11 类、106 项通过。
- 统一命令产生的 core／server-domain 及依赖模块报告按 classname + testcase name 去重：
  **362 类、2,484 个不同用例，失败／错误／跳过均为 0**。不同入口有重叠，不能相加。
- 模块边界门禁、`:desktop:compileJava`、`:server:d2gs:compileJava` 均通过；
  最终统一命令返回 `BUILD SUCCESSFUL`，耗时 17 分 36 秒。

本机使用忽略的本地 init 脚本隔离项目缓存和 buildDir、限制单 worker／小堆，并启用
Mockito 当前 JVM 自附加；未修改测试框架或跳过失败用例。报告位于忽略目录
`.gradle/combat-settlement-verified/<module>/test-results/<task>/`，统计仅使用本轮新报告。
修复过程中新增测试暴露的护甲 ED 预乘与更新器残留重复均已修正，最后代码与测试冻结后
完整复验；之后只补充文档结果。本轮没有运行图形客户端、双机联机或原版 EXE 逐击采样。

扩大回归同时修正两项旧断言：普通短弓改用实际装备的双手端点，而非人工设置的角色
一手伤害；德鲁伊 ownership 测试移除已在上批表格和真实命中测试中完成的震波 #243，
补齐清单同步遗漏，其余已确认缺口和缺少测试的技能清单保持不变。

## 另一台电脑的实机验收

拉取本批提交后，建议优先验证以下生产场景；本机无头回归不能替代这些检查：

- 野蛮人两手装备伤害差异明显的武器，分别普通 Attack／Left Hand Swing，确认不再混加两手。
- 双手剑单独装备，再加入副手及损坏副手，检查一手／双手模式；切换武器组后再次攻击。
- 弓、弩和投掷各命中一次；导弹飞行期间换装，确认已发射的伤害仍采用发射时数据。
- 装备吸取、附加元素及毒素，观察实际扣血、吸取、状态和死亡；联机客户端生命应与服务端一致。
- 罗格及近战佣兵更换武器后攻击，确认装备伤害生效；特殊元素箭／法术不属于本批普通入口。

## 保留边界

- 特殊技能预计算包、SrcMissDmg、特殊导弹回调、Half2HSrc 及技能专属转换不自动接入；
  既有保护继续保留。普通路径通过不等于七职业全部技能完成定点迁移。
- 佣兵特殊元素箭、法术和召唤／复活倍率不在本批普通武器入口内。
- 未改写原版 stat-list／事件队列的完整调度模型；压碎性打击等其他独立修正器仍有
  后续原版专项审计空间，不能据此宣布战斗系统完全等价。
- 大生命 float 表示、初始单位种子的完全还原、图形客户端、双机联机和原版 EXE 采样
  仍需单独验证。本批不启动客户端，不改安装目录中的 exe／dll。
