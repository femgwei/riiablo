# dark-magic 吸取借鉴：玩家与佣兵百分比结算

## 依据与选择

参考 dark-magic main 的 5bb71212、0ed24e8a（2026-10-10，PR #428），
不是 10 月 14–15 日的 1.13c 草案规范。本项目行为以本地 D2MOO 1.10f 为准：

- D2Game/UNIT/SUnitDmg.cpp，受击提交分支约 1149–1255：先以受击前生命
  限制物理伤害；玩家百分比左移六位，除 DifficultyLevels 的独立生命/法力
  吸取除数，再依次计算 physical * percent / 100、MonStats.Drain / 100、/64。
  佣兵使用百分比分支，不应用玩家难度除数。每步使用整数截断。
- 同文件 SUNITDMG_AddLeechedLife / SUNITDMG_AddLeechedMana：资源封顶；
  死亡延迟阻止生命恢复。百分比通道可在资源已满时产生吸取 overlay，双吸
  用攻击者随机种子二选一，而非客户端重新随机。
- D2Common/Units/Missile.cpp，MISSILE_AddStatsToDamage、
  MISSILE_CalculateFinalDamage、MISSILE_SetDamageStats：创建投射物时复制吸取
  属性，并按来源伤害比例 /128 截断，保存在投射物自身。
- D2Game/MISSILES/MissMode.cpp，MISSMODE_FillDamageParams：命中读取投射物
  自身的吸取属性。不能照搬 dark-magic 命中时读取 owner 当前属性的简化。
- SkillDruid.cpp，SKILLS_SrvDo122_Hunger：技能生命/法力吸取加入已有百分比。
  SKILLS_SrvDo120_FeralRage_Maul 则先结算当前攻击，再提升聚气状态；本次将
  野性狂暴状态更新移至当前命中结算之后，避免新一层吸取提前作用于当前击。

## 本次实现

- NativeLeechMath 纯计算保留 raw 8.8 精度；不复制上游整点生命丢失小数的限制。
- LeechSettlement 在公共 DamageSettlement 的防御回调/吸收之后、目标扣血之前
  执行，读取世界 serverContext 数据（旧测试保留 legacy binding）。同一个请求
  重入或重复 commit 不会重复吸取。
- 玩家近战、玩家武器投射物及佣兵生命吸取接入；佣兵不恢复虚构的法力池。
- 近战装备与状态吸取，加上饥饿/眼镜蛇攻击额外百分比，统一处理难度、Drain
  和过量伤害；删除野性狂暴、饥饿、眼镜蛇攻击旧的独立回血路径，避免重复。
- 普通及技能投射物创建快照按 SrcDamage / Skills.SrcDam 缩放吸取属性；命中
  从 snapshot 复制百分比到结算请求。在途装备变化不改写已发射投射物。
  零来源物理法术快照为零吸取，不能因为物理伤害而继承装备吸血。
- 权威结算选择 steallife / stealmana，DamageAppliedEvent 携带不可变表现事实，
  ElementalHitPresentationSystem 在攻击者身上播放。此效果独立于原生导弹
  suppressElementalPresentation，不因该开关丢失。

## 边界与未移植部分

更新：普通怪物固定吸取与耐力联动已在后续同批完成，见
[剩余候选一次性移植](dark-magic-batch-2026-10-19.md)。以下保留本次百分比吸取提交的原始范围。

- 普通怪物的固定量生命/法力/耐力吸取未移植：SUnitDmg 不仅恢复怪物生命，
  还在伤害生成阶段掷固定伤害并加入总伤害，需连同生成、随机顺序、目标资源
  扣除一起实现。不能在公共百分比结算中对所有怪物套玩家百分比。
- 玩家百分比分支后续对防守方法力的特殊扣除未加入；与 dark-magic 一样暂缓。
- 来源导弹继承（SrcMissDmg）、特殊服务器伤害回调修改吸取通道、全部技能覆盖
  及完整远程客户端特效同步不作为本批已完成内容。
- NativeDamagePacket 的旧 lifeStolen/manaStolen 估算字段保留兼容，但不再作为
  公共权威资源写入依据。Blood Golem 事件共享生命仍走独立的原生技能逻辑。
- 测试覆盖公共结算/快照/表现调用，不等同于原版 exe 对照、实际像素渲染或
  完整联网验收。另一个任务的怪物外观修改不纳入本次提交。

## 验证

使用 D2_HOME=G:\BaiduNetdiskDownload\Diablo II 1.10F 的真实 MPQ，定向运行
LeechSettlementTest、DamageSettlementIntegrationTest、DamagePresentationBoundaryTest、
DruidFeralMaulTest、AssassinMartialArtsTest、MonsterMissileSourceIntegrationTest、
NativeDruidHungerDataTest。使用独立构建目录避开并行任务的输出覆盖。
最终重跑 BUILD SUCCESSFUL：7 个测试类、101 项，零失败、零错误、零跳过。
