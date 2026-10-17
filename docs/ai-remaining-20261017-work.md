# 剩余 29 类怪物 AI 批处理记录

## 实现范围

前批 8/37 已提交；本批连续补齐剩余 29 类，专用处理器缺失数为 0。
清单和依赖结果见 [ai-dependencies-missing.md](ai-dependencies-missing.md)。
Baboon、Scarab 已有实现，本批补充实表注册检查。

## 生产链修复

- 原生路径路点、目标追踪/分配身份、步数预算和受阻停止；不回退普通 A*。
- MonsterData 模式记忆、minion 命令队列与目标/所有者身份、过期帧。
- 区域生成类、房间随机流、Nest/Spawner 出生上下文和 minion 归属。
- Imp 骑乘、位置跟随、碰撞卸载、下乘恢复、宿主释放与 mounted skill 表选择。
- Inferno126、Whip131、FireMissile132、Pregnant133、Stomp134、Spawner135 回调。
- SuicideMinion 死亡延迟爆炸、Regurgitator 吞尸、ReanimatedHorde 复生修饰符。
- DarkWanderer InitFn43/59、正常模拟更新和退场计时；修复 PassiveSystem 不执行更新的问题。
- Baal 波次技能 285/286，真实 SrvHit54 控制器、波次/owner 身份、迟到回调隔离和失败恢复；原地变类与传送门重试。
- 本批 12 个 MonSeq 定义的真实数据执行、混合模式切换与冲锋移动点过滤；Leap Attack 起跳/落地/单次命中。
- CatapultSpotter 的活动与非活动房间死亡 catapult 查询。
- 测试夹具补原生 orbit/path/chase 返回值、TownPortalRegistry、复活标志和真实分配的生成实体 ID。

## 验证环境

- 安装数据：`G:\BaiduNetdiskDownload\Diablo II 1.10F`，通过 `D2_HOME` 指定。
- 隔离源码：`build/ai-remaining-20261017-source`。
- 隔离基线：`0ff3a770ed2d1330ef666b801b18dfeed387b55f`，位于 `build/ai-remaining-20261017-baseline`。
- Gradle init：[ai-remaining-20261017.init.gradle](ai-remaining-20261017.init.gradle)，各项目构建输出留在隔离目录。
- 只同步本批生产代码及测试；不回退共享工作区 HEAD，不混入其他人的 client/文档改动。

## 验证结果

初次广域命令：`:core:test --tests com.riiablo.engine.server.* --max-workers=1`。
共 2,375 项，36 项失败；其中本批两个夹具失败已经定位并修复，其余已与干净基线核对：20 类共 211 项，33 项失败复现。Amazon 的 immolation 范围命中用例基线通过，已加入最新回归，不直接归为基线失败。
原始日志：`build/ai-remaining-20261017-server.log`。

整批聚焦回归覆盖所有 AI、NativeMonInferno、Leap、MonSeq、Act1 ECS、Meteor/Baal 控制器、A3 Wanderer 和 A5 Baal 任务；首轮 671 项中 670 项通过。
剩余 Stomp 测试随后修正为：原始 1.10f 空 srvmissilea 按原版拒绝，临时配置有效 missile 字段后验证共享直接伤害。安装数据未改动。
最终统一回归 725 项全部通过（65 个测试类），日志 `build/ai-remaining-20261017-final.log`。
随后补充冲锋等待和 Nihlathak 的喷火初始化回归，相关 10 项全部通过，日志 `build/ai-remaining-20261017-callbacks.log`；按测试类去重后累计 727 项，0 失败。
后续补充没有改动 AI 决策；验证的是 MonSeq 结束只发一次、冲锋移动点不造成伤害、进入近战后仅执行攻击点，以及技能开始事件在安装序列指针前初始化喷火通道。
Amazon 的 immolation 用例在最终整类回归中通过，首次广域失败未稳定复现，未修改该技能的生产代码或测试。

因此，本批及关联回归为通过；整个服务端广域回归仍有已在干净基线复现的 33 个失败，不能声称整个工程测试全绿。基线日志 `build/ai-remaining-20261017-baseline.log`。

测试说明：29 类 × 3 难度独立加载/运行/死亡；29 类 × 2 概率边界 × 3 距离重复决策；普通近战分支与动作锁、FingerMage 诅咒追击、Bat 挂顶、Suicide 丢失目标、Imp 宿主释放；所有 37 类及 Baboon/Scarab 实表专用类解析。
生产回调测试另覆盖真实 MonSeq 点、Wanderer 时钟、Baal 控制器坐标、Stomp 一次掷点、Korlic 落地/重复动作点、房间退出后的死亡 catapult 查询。

## 验收边界

本批实现与自动测试不能当作原版全分支逐帧一致证明。自然地图和图形客户端场景尚未全量验收；固定点/碰撞/表现限制详见依赖文档。整个服务端测试未全部通过时会保留失败清单和基线复现证据。

## 基线复现失败（33）

- AssassinSkillSpecializationTest：1 项。
- BlessedHammerIntegrationTest：1 项。
- CombatPipelineIntegrationTest：1 项。
- DiabPrisonIntegrationTest：8 项。
- DruidShapeShiftTest：1 项。
- DruidShockWaveTest：1 项。
- FrogPresenceIntegrationTest：4 项。
- AuthoritativeItemMoveServiceTest：1 项。
- MonsterMeleeAnimationRegressionTest：2 项。
- MonsterRoomActivationTest：1 项。
- NativeBarbarianPassiveDataTest：3 项。
- NativeDruidShapeDataTest：1 项。
- NativeNecromancerPoisonNovaDataTest：1 项。
- NativePaladinBlessedHammerDataTest：1 项。
- NecromancerPoisonNovaIntegrationTest：1 项。
- PotionRecoverySystemTest：1 项。
- ServerEntityFactorySummonQuotaTest：2 项。
- StatusEffectEcsScenarioTest：1 项。
- SummonedPetSystemTest：1 项。
