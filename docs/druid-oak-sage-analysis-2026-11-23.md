# Oak Sage（橡木智者）阶段结论

后续共享表现核对发现了源控制状态与受益状态混用的问题，现已修正；本文件原先的验证范围不包含这项差异。补充证据和限制见 [狼獾之心共享光环核对](druid-wolverine-presentation-analysis-2026-11-23.md)。

Oak Sage 使用 `SrvDoFunc=119`，召唤上限为 1，等级 8 时基础召唤等级由 `Calc2=ulvl` 解析。其生命光环不是直接读取 Oak Sage 技能行，而是通过关联的 Oak Sage Aura 行投影到召唤者、队伍成员、佣兵和其他召唤物。

已有 Riiablo 实现覆盖：

- source-owned `StateId.OAKSAGE` 光环层；
- 关联 Aura 行的等级和 `ln34` 生命加成；
- 召唤者、队伍成员、佣兵和召唤物的范围/区域过滤；
- 橡木贤者死亡、删除、跨区域或超出范围后的撤销；
- 网络同步和客户端状态 overlay 生命周期。

验证结果：`NativeDruidSummonDataTest` 通过；Oak Sage 相关的 `DruidSummonIntegrationTest` 断言均通过。组合测试中剩余失败来自 Java 25 与旧 Byte Buddy/Mockito 的测试框架兼容性，不是 Oak Sage 断言失败。

本轮没有重复启动原版客户端：Oak Sage 与 Oak/Wolverine/Barbs 共用同一套 spirit aura 投影链，独立原版采集不会增加新的信息。后续只有在需要核对 Oak Sage 专属客户端召唤动画或死亡动画时，才建立单独采集。
