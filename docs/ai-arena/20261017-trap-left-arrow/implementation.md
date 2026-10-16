# Trap-LeftArrow 实现记录

原版：D2MOO `source/D2Game/src/AI/AiThink.cpp`，
`AITHINK_Fn079_TrapLeftArrow`（0x6FCDE710）。

Fn079 与 Fn078 的决策相同，区别为左箭检查整数 Y 坐标差，右箭检查整数 X 坐标差。
提取 `alignmentDifference` 扩展点，左箭继承共用决策并覆盖 Y 坐标差计算，右箭保留 X 计算。
`AI.findAI` 显式将表名 Trap-LeftArrow 映射至 TrapLeftArrow。

保留距离区间含两端、超距等待 40 帧、坐标差大于 2 或冷却未严格过期等待 30 帧、
冷却按 param2 更新、地图关卡共享攻击类型、类型 1 使用自身 Skill1、缺技能等待 30 帧。
复用已移植的区域状态、技能与普通攻击生命周期，无新增依赖。

按用户要求未运行测试，也未编译；仅核对原版分支与代码差异。
原右箭项的测试结果不作为本次左箭的运行验证，尚未进行自然地图或图形客户端验收。

累计完成实现 6/37，剩余 31 类。
