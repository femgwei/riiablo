# Wearbear（熊人变身，228）阶段结论

本轮纠正了前次总结中的表值混用。显式指定
`-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F` 后，原始 MPQ
`Skills.txt`、类型化表格和 SrvDo116 运行状态使用以下数值：

- 伤害：`Param1=50`、`Param2=7`，即 `50 + (level-1)*7`；
- 防御：`Param3=25`、`Param4=5`，即 `25 + (level-1)*5`；
- 形态生命基值：`Param5=50`，加上 Lycanthropy 的 `ln34`；
- 3 级熊人、2 级 Lycanthropy：伤害加成 64%、防御加成 35%、生命加成 75%。

旧辅助函数错误使用 55/8、25/6，旧测试和文档也据此写成 71/37/100。
生产 `applyShapeShiftState` 已经按 `AuraStatCalc` 读取表格，因此本轮修正的是
辅助函数及测试/研究记录，没有把已经正确的表驱动状态改成硬编码。

`NativePlayerCombat110fAuditTest.rawWerebearRowAndProductionStateUse110fCoefficients`
所在测试类从原始 MPQ 文本独立核对数值，并覆盖 1、2、3、20 级结果；
`NativeDruidShapeDataTest` 对辅助函数与表格求值的一致性提供第二层检查。
形态状态、互斥切换、Lycanthropy、Feral Rage/Maul 和客户端形态资源继续沿用已有链路。

熊人原版专属的变身/攻击逐帧采集本轮未执行，不能用狼人样本宣称熊人时序已完成。
本轮全程为离线原版数据读取和无窗口 headless 测试。

注意：`AITHINK_Fn112_DruidBear` 对应召唤熊 Grizzly（247），其进展单独记录于
`docs/druid-grizzly-analysis-2026-11-23.md`，不能标记为本技能的 AI 实现。
