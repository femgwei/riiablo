# 狂犬病（Rabies，238）：感染光效和传播截止

本轮从召唤恐狼继续到狂犬病。重点是感染表现、传播结束边界和状态清理；没有启动原版进程、切换前台或发送全局输入。

## 有效数据与源码依据

本机 1.10F 有效 Skills 表指定：Rabies 使用 `SrvSt57`、`SrvDo121`、狼形限制、S3 动作、`AuraTargetState=rabies`、`SrvMissileA=rabiesplague`。

状态 112（rabies）指向 Overlay `rabiesplague`，其 DCC 为 `Expansion/rabies.dcc`：30 帧、单方向，AnimRate=16，Trans=3，PreDraw=false。Height1/2/3/4 分别为 14、0、-14、-60。该状态不是普通 POISON 状态，感染覆盖层与中毒染色/伤害的生命周期可以不同。

两个传播弹体的 CelFile 均为字面值 `null`：

- `rabiesplague`（515）使用 SrvDo30，Param1=4、Param2=7、子弹体 rabiescontagion。
- `rabiescontagion`（516）使用 SrvDo1、SrvHit53、SrvDmg11，速度 5。

本轮只按表中 null 资源描述其表现角色，没有把 `null` 字符串改成 Java null，也没有为传播过程虚构可见弹道。

公开重建源码 D2MOO `SkillDruid.cpp:SKILLS_SrvDo121_Rabies/SKILLS_ApplyTargetAuraAndMissile` 创建目标感染状态与附着控制器，并避免给已有感染再增加同一状态。`MissMode.cpp:MISSMODE_SrvDo30_RabiesPlague` 在宿主死亡时结束控制器；`MISSMODE_SrvHit53_RabiesContagion` 要求传播时剩余原感染时间至少为 10 帧，且不超过技能原持续时间。

以上是本机有效 MPQ 数据与公开重建源码证据，不是官方原始源码、原版运行时调用记录或 GPU 对比。

## 修正

感染覆盖层之前走通用 Overlay 策略，把 AnimRate=16 当作 16 FPS，将 Trans=3 当作 luminosity。现在持久 `StateId.RABIES` 使用项目已有的原版 Overlay 策略：25 FPS、additive，以及按角色高度类别计算偏移。状态仍由权威快照恢复和移除，不增加客户端自己的过期计时器。

传播控制器之前将短于 10 帧的剩余时间用 `max(10, remaining)` 补长，这会让新感染与光效超过原感染截止。现在剩余不足 10 帧时停止创建新感染，符合边界时直接传递剩余值。

控制器增加对附着宿主生命的检查：即使死亡后的 rabies 状态还未移除，也不再继续传播，并删除该控制器。这里检查的是被感染宿主，不把原施法者死亡当成同一个条件。

## 无窗口验证

`RabiesPresentationTest` 在真实 Artemis、StateOverlaySystem、OverlayManager、OverlayStepper、StateUpdater 和 StateSerializer 中检查：

1. 受感染目标的远端快照创建一层光效，读取真实 DCC 头确认 30 帧/单方向，使用 25 FPS、additive 与原版高度偏移。
2. 客户端 snapshotOnly 不自行扣减持续时间；状态刷新不重新加载资源或重启动画。
3. POISON 与 RABIES 分别移除：去除普通中毒不误删感染层；去除感染不误删仍存在的中毒状态。
4. 待加载阶段取消感染时释放资源，组件移除时不清掉无关状态图层。
5. 施放事件不会在施法者处再创建可见弹体或感染覆盖层；传播弹体保留原表 null CelFile。

上述绘制对象与 AssetManager 使用 mock，DCC 头单独从真实 MPQ 字节读取。没有执行 GPU 绘制，也没有宣称完整调色/遮挡效果已经验证。

普通 POISON 还配置独立的 `castoverlay=poisonhit`。其瞬时激活层可能在状态移除后仍正常播完，因此测试按状态归属检查狂犬病层已清理，并明确保留 poisonhit；不以“整个 Overlay 组件为空”作为感染移除的条件。

`RabiesControllerLifecycleTest` 在生产 MissileCollisionSystem 中验证剩余时间的 9/10/50 帧边界、子控制器继承截止时间、死亡宿主停止传播、已有感染不被重复脉冲续期，以及移除一个宿主状态不误删其他目标感染。该夹具不制造毒伤害数值，只验证感染标记与控制器；已有技能毒伤害公式、近战命中处理与快照测试另外回归。

日志、逐项 XML、数据行和源码哈希见 `tools/original-observer/rabies-presentation-validation-2026-11-24.json`。职业清单保留原版运行时未采集状态。

初轮 44 项中 6 项失败：传播夹具的 EntityFactory 未注册 Map 导致 5 项初始化失败；另 1 项错误假设 POISON 没有独立激活图层。已补齐地图依赖，并依据实际表将瞬时 poisonhit 与持久感染层分别断言。初轮 XML 和日志保留，不计为最终通过。

最终 JDK 17 组合回归：8 个测试类共 **44 项通过，0 失败、0 错误、0 跳过**。

## 尚未完成

- Riiablo 仍以周期范围扫描直接感染邻近目标；原版 SrvDo30 会随机发出不可见传播弹体，SrvHit53 在碰撞时接力。传播随机方向、飞行延迟、命中/碰撞和位置遍历顺序尚未完整重建。
- 剩余时间测试按现有回调扣减后的 remainingFrames 检查，未证明脉冲相位与原版全局 gameFrame 完全相同。
- 本轮检查最短剩余时间和宿主死亡，未证明原技能时长上界、跨区域传播、施法者离开游戏后的清理完全一致。
- 未新增原版后台运行时采样；S3 咬击动画、目标感染覆盖层的精确出生帧、全方向动画、GPU 画面及受控多目标毒伤害仍待核对。
