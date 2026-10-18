# 食腐鸟地面接近失败恢复

日志中的 1390 已降落并持续锁定玩家，但停在近战范围外。按日志坐标计算的
半径接近落点 `(79,-47)` 与另一只食腐鸟的占地重叠；日志没有记录实际拒绝原因，
因此同伴阻挡是待实机确认的触发条件。

对照 D2MOO `AiThink.cpp::AITHINK_Fn023_Vulture`、
`AiTactics.cpp::AITACTICS_MoveInRadiusToTarget` 和
`MonsterMode.cpp::sub_6FC65680` 和 `PathWF.cpp`，旧主机 A* 的精确落点失败不应丢弃全部
可达的局部前进机会。食腐鸟现在在半径坐标移动失败后，用已移植的 `D2WallFollow`
生成局部转向/部分路径，再提交其末端可达坐标。该兼容路径不等同于完整的原版
MonsterMode/path tick 实现，也不替代正在开发的原生路径运行时。

局部计算平移到 uint16 坐标空间，碰撞查询仍使用原始有符号地图坐标。
保留房间邻接、地形和单位碰撞限制；飞行状态不启用该恢复。
其他 AI 的默认恢复为空，成功的原有半径请求不会触发此分支。

回归覆盖日志坐标下的占地冲突、平移后等价结果和完全被围住时拒绝移动。
跨电脑仍需复验三只食腐鸟接近/攻击，并确认没有穿过其他单位。

验证：独立输出目录下运行 `VultureApproachBoundaryTest` 和 `VultureFlightTest`
通过；包含连续接近后进入玩家近战距离的回归。主代码编译通过。
公共输出目录的首次测试编译受并发写入冲突阻断，随后通过本地
`build/vulture-recovery.init.gradle` 隔离构建输出完成测试。
未执行原报告场景的真实 MPQ/画面验收。

## 攻击结束后的 AI 调度

确认原版 `MonsterMode.cpp::sub_6FC63680` 在攻击结束时返回 NU，
NU 入口 `sub_6FC64310` 在没有未来 AI 事件时按 MonStats.AIdel 安排下次决策，
非正值回退为 15 帧。此前 Java 动作锁移除后可立即再决策，遗漏了此阶段。

SequenceHandler 现在在怪物动作正常结束并进入 NU 时通知 AI；NativeDecisionAI
保留未来的决策时间，仅在到期/无等待事件时，从 NU 入口安排难度对应 AIdel。
其他 AI 默认忽略通知；多段技能提前返回、玩家动作与非 NU 结束不触发该通知。
本次不调整攻击概率或动画速度，也不实现冻结/眩晕的完整原版事件分支。

新增精确 15 帧攻击边界、未来事件保留、零延迟回退与 SequenceHandler 通知接线回归。
验证：独立输出目录下运行 VultureAttackCadenceTest、SequenceHandlerTest、
VultureFlightTest 和 VultureApproachBoundaryTest，20 项全部通过，无失败或跳过。
编译与模块边界检查通过；原报告场景的实际攻击节奏仍待跨电脑验证。
