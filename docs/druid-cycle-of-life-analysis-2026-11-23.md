# Cycle of Life / Carrion Vine / Solar Creeper（Fn111）

本轮把 Carrion Vine（技能 231）和 Solar Creeper（技能 241）从 Plague Poppy 的 Fn110 逻辑中分离出来，按 D2MOO `AITHINK_Fn111_CycleOfLife` 重建。

已完成：

- 使用 `AuraRangeCalc` 计算可用尸体搜索半径，并按原版限制到 5..50；
- 主人距离使用原版单位距离，默认跟随距离为 6；主人 leash 优先于尸体处理；
- 尸体选择检查可用性、`CORPSE_NOSELECT`/`CORPSE_NODRAW`、地图/房间和敌对阵营；
- 尸体超过 `aip2` 视野阈值时不重复换尸体；
- Carrion Vine 只有主人生命未满时才启动 `CorpseCycler`；Solar Creeper 只有主人法力未满时才启动 `VineCycler`；
- Fn111 使用独立的 25% 受威胁逃离分支，不读取毒状态/毒抗，也不会把尸体误判成毒藤目标；
- 施法使用 `SrvSt63`、尸体原点和 `CORPSE_NOSELECT` 预留，复用现有服务端弹体创建；
- `SrvDo29`/`SrvDo33` 分别恢复生命/法力；本轮纠正整数取整、满资源门禁，并阻止死亡主人被回收弹体恢复；
- 补齐恢复成功时发送给主人（不是尸体）的 `Missiles.txt ProgOverlay`，沿用可同步到客户端的 target-overlay 事件；这层表现此前缺失；
- 客户端 `pCltDoFunc=51` 的 recycler vine / recycler explosion 子特效和 20/45 帧边界已有独立测试；
- checkpoint 保存 `nextThink`、`skillCooldown` 和状态，严格保留等于冷却边界时不施法的原版语义。

验证：`CycleOfLifeAiIntegrationTest` 6 项、`DruidVineCorpseCyclerTest` 参数化后的两种尸体循环、`DruidVineRecyclerPresentationTest`、`NativeDruidSummonDataTest` 和 `AiRuntimeStateTest` 均通过；运行日志见 `tools/original-observer/cycle-of-life-validation-2026-11-23.log`。

新增 `DruidRecyclerRecoveryTest` 在真实 `MissileCollisionSystem` 中推进两类回收弹体，检查触发前资源不变、触发后整数值和主人覆盖层、只触发一次、满资源与死亡门禁。修复前 100.75/200.75 的生命样本错误变成 116.80859，原版整数规则应为 116；50.75/100.75 的法力样本错误变成 55.785156，原版应为 55。回收表现测试初轮还暴露了测试中嵌套 `Attributes.get` 共用临时 StatRef 的问题，已改为先读取最大值再设置当前值；最终回归不沿用该测试桩错误。

组合回归在 Java 25 下完成 91 项，其中 89 项通过、`MissileTargetOverlayTest` 两项因旧 Byte Buddy 不支持 Java 25 而中断。JDK 17 复核揭示这两项旧夹具没有绑定 Artemis World，现已修正夹具，并增加 `CorpseCycler`/`VineCycler` 两项客户端消费测试：覆盖层加到主人，且不额外生成 impact 子弹体。初始化 mock 工厂的生命周期调用不计作游戏事件调用，测试在世界绑定后清空初始化调用记录。

测试记录分开保留：主组合日志 `cycle-of-life-final-validation-2026-11-23.log`、Java 17 夹具复核日志，以及 `cycle-of-life-client-final-java17-2026-11-23.log`。最终汇总 JSON 根据保存的主组合 XML 和客户端复核 XML 合并结果，不把初次失败日志伪装成成功构建。

源码依据：`AiThink.cpp:AITHINK_Fn111_CycleOfLife`、`SkillDruid.cpp:SKILLS_FindUseableCorpse/SKILLS_SrvSt63_Corpse_VineCycler`、`UnitFinds.cpp:UNITFINDS_TestUnit`、`Units.cpp:UNITS_GetDistanceToOtherUnit`、`MissMode.cpp:MISSMODE_SrvDo29_RecyclerDelay/MISSMODE_SrvDo33_VineRecyclerDelay`。本轮使用公开重建源码与用户本机有效 1.10F MPQ 表作为依据，没有新增原版运行时采集证据。

范围限制：原版 `UNITFINDS` 按邻接房间及房间链表遍历，本轮使用 ECS 尸体集合顺序进行首个可用对象选择，不能保证同布局下选中同一个尸体。PetMove mode 3 的 leash 暂用现有跟随投影，没有补齐 RegionId、坐标偏移或精确落点；PvP 宠物尸体不在本轮敌对尸体筛选范围。首个冷却基于 AI 本地时钟，尚未证明与原版全局 gameFrame 起始相位一致。威胁逃离概率分支已实现，但 6 项 AI 测试未单独验证其随机流。客户端 20/45 帧边界当前是表格/回调测试，未做两种藤的生产客户端完整逐帧回放或 GPU 验证。

后续如需更细对齐，再采集原版私有桌面中的自然尸体顺序、回收覆盖层和客户端动画游标。本轮没有启动原版游戏、切换前台或发送全局输入。

最终结果：14 个测试类合并后 93 项通过，0 失败、0 错误、0 跳过。主组合已有 89 项通过；客户端覆盖层 4 项在 JDK 17 下通过，替换主组合中受阻的 2 项，因此总计 93 项。完整清单、原始失败口径和源码哈希见 `tools/original-observer/cycle-of-life-validation-2026-11-23.json`。
