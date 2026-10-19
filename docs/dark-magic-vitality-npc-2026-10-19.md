# dark-magic 后续借鉴：耐力加点及 NPC 快照

## 范围与依据

工作区检查时，其他任务的代码已提交，仅剩 composite-smoke.log。
本批改动 PlayerStatsManager，新增两份测试；不修改受击结算、怪物命中或表现层。

参考上游 PR #445（f51cd620）与 #458（deafc12a）。dark-magic 的 1.14d
样本用于提出测试问题；行为依据另行核对本地 D2MOO 1.10f：

- PlayerStats.cpp，PLAYERSTATS_SpendStatPoint（0x6FC7EDF0）：增加最大耐力，
  再增加 StaminaPerVitality << 6，最后封顶。
- ItemMode.cpp，D2GAME_ITEMMODE_ServerStatlistCallback_6FC41910：最大耐力
  变化时，当前值大于零且旧上限大于零，按 current / max(oldMaximum,256)
  * newMaximum 截断，并限制为 [1,newMaximum]；零不作比例提升。

## 已实现

- 耐力加点使用实时 aggregate 当前值，避免保存 base 的陈旧耐力覆盖实时值。
- 保持 8.8 原始整数，按最大值变化回调→职业固定收益→封顶处理，当前值同时写回
  保存列表和实时列表。无加点资源时仍走已有拒绝流程。
- 覆盖部分耐力区分向量 18168/23808→18619/24064、零、满、连续两次加点，
  正式入口按实际 CharStats 遍历七职业并验证扣点。
- NPC 测试检查真实 MPQ 中 Akara 的 npc/interact/PS，以及 MonsterSerializer
  身份、PositionSerializer 位置、CofComponentsSerializer 外观值与后续实体修改隔离。
  配合现有 A2–A5 NPC 地图投影测试运行。

## 尚未覆盖

- 本次保留既有最大耐力增量模型；装备/技能百分比对加点导致的最大值增量、
  UnitStates 临时上限缓存与下一次刷新之间的协调，仍需独立处理。
  不把无百分比加点向量推广为全部 buff/装备组合已对齐。
- 生命和精力加点未修改。尤其 Energy 原生先加当前法力再加最大法力，不能套用
  耐力的顺序。
- NPC 测试不是完整联网连接、DS1→客户端创建链或实际像素渲染证明。
  本批没有重写已存在的 NPC 创建/选择/交互实现。

## 验证

通过本机 D2_HOME 注入 1.10f MPQ，定向运行 VitalityStaminaOrderingTest、
PlayerStatPointAuthorityTest、TownNpcSnapshotContractTest 和既有 A2–A5
TownNpcEntityProjectionTest。结果 BUILD SUCCESSFUL，6 个测试类、10 项测试，
零失败、零跳过。未运行原版客户端画面对比、完整联机/重连或全量核心测试。
