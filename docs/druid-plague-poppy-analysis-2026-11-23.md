# Plague Poppy（毒藤）阶段结论

## 已完成

按原版 `MonStats.txt`、`Skills.txt` 和 D2MOO `AITHINK_Fn110_Vines` 核对了毒藤的运行契约：

- `plaguepoppy` 使用 `Vines` AI，`aidel=15`。
- AI 参数为 `[100, 20, 25, 10, 35]`：Vine Attack 间隔、目标视野、停顿、逃离距离和主人牵引距离。
- 首个技能是 `Vine Attack`，`SrvDoFunc=130`，服务端子弹为 `plague vines`。
- 原版对中毒或 100% 毒抗目标会逃离；技能冷却按 AI 控制器的上次施法帧计算，不能每个 AI tick 都重放。

Riiablo 的 `Vines` AI 已补齐：

- 使用原版 owner leash 和目标视野参数；
- 使用原版单位近战范围判断；
- 对中毒/毒免目标走逃离分支；
- 保存并递减独立的 `skillCooldown`，按 `aip1 * 25Hz` 限制 Vine Attack 重复施放。

## 验证

- `NativeDruidSummonDataTest`：通过，包含 Plague Poppy AI 参数和 Vine Attack 行校验。
- `DruidVineCorpseCyclerTest`：通过。
- `VinesResourceAuditTest`：通过；原版 MPQ 中毒藤有 5 个 DCC 层，WL 资源缺失由资源守卫处理。
- 组合 AI 回归仍受 Java 25 与旧 Byte Buddy/Mockito 兼容性阻断，失败来自测试框架加载，不是毒藤断言。

## 未完成

还没有为 Plague Poppy 建立像 Raven 那样的原版后台 native helper，尚未采集毒藤生成、首次攻击、逃离、毒藤轨迹和客户端 `S1` 动画时序。因此当前是数据/AI 重构完成，运行时差分证据仍待补齐。
