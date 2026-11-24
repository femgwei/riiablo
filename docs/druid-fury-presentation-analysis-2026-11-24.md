# 狂怒（Fury，248）：攻击关键帧回卷与连续攻击节奏

本轮接在震波之后，目标是完善狼人连续攻击的客户端展示链路。全程离线读取有效 MPQ 数据、公开重建源码并运行无窗口测试；没有启动原版、切到前台或发送全局输入。

## 定位到的缺失步骤

有效 Skills 行：SrvSt37、SrvDo13、CltSt53、CltDo21、A1/seqtrans A1，只有 wolf 可用。calc1 为 `min(Param5+lvl−1,Param6)`，即 2..5 次攻击；calc2=ln34 为伤害增幅。**Param2=100 的说明是 `% frame rollback`，不是攻击速度加成。**

D2MOO `SkillAma.cpp:SKILLS_SrvSt37_Zeal_Fury_BloodLordFrenzy` 保存攻击次数和目标；`SKILLS_SrvDo013_Fend_Zeal_Fury` 在一次攻击后扣减次数，寻找后续目标，随后立即调用 `SKILLS_RewindUnitAnimation(unit, Param2)`。

`Skills.cpp:SKILLS_RewindUnitAnimation` 调用 `SUnit.cpp:SUNIT_RewindAnimationByPercentage`，后者取消旧 MODECHANGE/ENDANIM 事件并根据回卷后的时间位置重排事件。原表 100% 对应重新从起点推进，不必先播放当前攻击的全部收招段。最后一击或没有后续目标时不执行这个回卷，保留最终尾段。

Riiablo 原先仅在 `SequenceHandler.onAnimDataFinished` 重启整段 A1：每一击都先播完整 13 帧动作，再开始下一击。攻击次数虽正确，但中间多播了尾段，形成狂怒连续攻击偏慢的展示差异。`getFuryRepeatAttackRate` 还把回卷百分比误命名为攻击速度。

上述依据是本机数据与公开重建源码，不是官方原始源码或本轮原版运行时动画轨迹。

## 修复

1. Actioneer 在 SrvDo13 的本次攻击结算、剩余次数和下一目标处理后，立即调用 SequenceHandler 的 Fury 回卷入口。
2. 回卷保留当前动作序列，不重新执行 SrvSt37、不重置连击次数；按原表 100% 将 AnimData.frame 回到零，并重置关键帧扫描位置，保证下一击能再次触发。
3. 强制发布相同 A1 模式的重播事件，使 CofReference.animationRestartId 递增。已有 CofReferenceP 序列化携带这个 ID；客户端已有 applyNetworkMode 会把同模式的新 ID 当作强制重播，而不是忽略“模式没变”。没有扩展协议。
4. 不将 Param2 加入 IAS，保留已有动作速度覆盖值；Sequence.started 保持 true，避免下一 tick 作为全新动作重复初始化。
5. 最后一击及没有后续目标时不回卷，让现有结束路径恢复 NU。保留结束事件处的旧兼容分支，未对其他共享 SrvDo13 技能进行同样改造。
6. 新增名称准确的 getFuryRollbackPercent；旧 getFuryRepeatAttackRate 作为 deprecated 兼容接口保留，日志和表契约测试改用 rollback 名称。

原版对非 100% 回卷按已过游戏时间计算；当前辅助入口对自定义表的非 100% 值使用本地固定帧位置近似。**本轮只验证零售数据的 100%，不宣称支持所有自定义回卷比例。**

## 无窗口验证

FuryAnimationIntegrationTest 载入真实 eanimdata.d2，在生产 CofManager、DruidShapeShiftResolver、AnimDataResolver、SequenceHandler、Actioneer 和 AnimStepper 中推进 25 Hz 逻辑，未手动注入每一击关键帧：

- 技能 1 级两击和 4 级五击按实际狼人 A1 关键帧触发，首轮输出分别为 hit_ticks=[7,14] / [7,14,21,28,35]，finish_tick=20 / 41。
- 每次中间命中后，立即检查固定帧位置为零、Sequence 仍开始状态、尚未发出 ENDANIM，以及序列化的相同 A1 带有递增重播 ID。
- 最后一击结束后只出现一次 ENDANIM，Casting/Sequence 移除并回到 NU，不无限循环。
- 首个目标死亡后，后续攻击转到另一个存活目标；全部目标死亡后，不制造新的近战命中，正常结束动作。死亡条件由夹具在首击后设置，未宣称采集了原版致死行为。
- 未强制 hit：攻击尝试即消耗本轮次数，真实伤害继续由已有命中/格挡与结算路径决定。

这里的 tick 时间是本轮指定属性、无装备加速的 Riiablo 夹具结果，不是原版计时或所有 IAS 配置的固定答案。使用的 `40A1HTH` 仍为原有 13 帧资源，修复改变重播时机，没有删帧或增加虚构效果。

既有 DruidFuryTest/NativeDruidFuryDataTest 回归狼形限制、2..5 次攻击、calc2 伤害和目标接续；共享 SequenceHandler 及焰爪、饥饿、震波动作资源另行回归。测试日志、逐项 XML 和源码 SHA256 保存于 `tools/original-observer/fury-presentation-validation-2026-11-24.json`，最终通过数量以该文件为准。

首轮 10 项通过；最终 JDK 17 定向回归 **32 项通过，0 失败、0 错误、0 跳过**，其中包含 3 项共享 SequenceHandler 检查。两轮构建均成功。

## 仍未覆盖

- 没有原版实时采样或 GPU 绘制；CltSt53/CltDo21 的精确视觉相位、全方向精灵展示与音效尚未逐帧确认。
- 已验证服务器回卷和重播 ID 序列化，并检查现有客户端处理路径；未启动真实远端双客户端会话，也没有把包中存在重播 ID 当成 GPU 播放已验收。
- 未验证所有武器速度、装备 IAS、变身加速与其他状态叠加；现有通用攻击速度公式不是本轮重建成果。
- 原版按全局事件队列回卷，Riiablo 按当前固定 tick 调度；精确到原版游戏帧的事件边界仍需后台运行时对照。
- 全部目标死亡的测试验证退出路径，不覆盖所有地图碰撞、动态离开范围和实体 GUID 生命周期重用情况。

职业清单保留 runtime_status=not_run_in_class_batch，只更新此项有界研究进度。狂怒之后德鲁伊所有 30 个技能均已有至少一轮研究或采集记录，后续应据清单剩余差异安排下一职业，而不是把有记录误记为全部与原版一致。
