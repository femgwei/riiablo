# D2MOO 双层测试门槛

每个原生对齐模块必须同时提供两层回归：

1. **纯数据/边界层**：只构造 Excel 行或原生值，验证难度索引、缺失列、概率边界、固定点溢出和 RNG 消耗等确定性规则。
2. **ECS/地图集成层**：启动 headless Artemis world 或地图构造器，验证数据进入实体、碰撞、事件和网络快照后的可观察结果。

测试命名约定：纯数据测试使用 `Native*Test`，集成测试使用 `*IntegrationTest`；两者都必须运行在 `:core:test` 的 headless 配置下。需要真实 D2 资源的测试通过 `D2_TEST`/`D2_HOME` 注入，不得把本机绝对路径写入测试。

当前门槛样例：

- `NativeDataTablesTest`：难度列和群组边界的纯数据测试。
- `NativeUnitFlagsTest`：UnitFlags 原生位和 ECS 组件生命周期测试。
- `FallenShamanAutoCombatIntegrationTest`、`DualClientFallenLootIntegrationTest`：怪物复活、掉落和双客户端集成测试。

提交前至少运行对应模块的两层测试；任一层失败时，路线图中的模块保持未完成状态。
