# M1-B 地图实体端口迁移批次（2026-10-21）

本批完成地图到实体创建入口的第一阶段实际迁移：

- 新增 `MapPopulationPort`，声明地图所需的 monster、warp、object 创建能力。
- `EntityFactory` 实现该端口，保留现有 ECS 工厂作为唯一适配器。
- `Map`、`MapManager`、Act builder 和 D2MOD builder 的字段/参数改为 `MapPopulationPort`；地图层不再直接持有 `EntityFactory` 类型。
- `Act1MapBuilderD2MOD` 的 Blood Raven fallback 也改为通过端口解析。

行为保持不变，客户端工厂和服务端工厂仍通过同一端口提供实现。M1-B 扫描从 124 条降至 103 条，减少项来自地图层对 `EntityFactory` 的直接类型依赖；剩余 `NativeDataTables`、服务端组件和客户端工厂判定仍登记为后续债务。

验证：

```text
./gradlew.bat :core:compileJava :core:mapEntityBoundaryCheck :core:architectureRegression --no-daemon
```

结果：BUILD SUCCESSFUL；`M1-B findings: 103`、`M1-B boundary drift: none`，M1-A 门禁亦通过。

下一步：继续在同一 M1-B 方向收口 `NativeDataTables` 读取，建立显式地图数据端口；不再回到单文件拆分。
