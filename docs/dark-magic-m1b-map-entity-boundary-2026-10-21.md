# M1-B 地图/实体边界合并批次（2026-10-21）

本批一次完成地图/实体反向依赖的 owner 登记、可复算基线和新增漂移硬门禁。

## 当前 owner 与债务

- `core/src/main/java/com/riiablo/map/Map.java`、`MapManager.java`：地图运行时仍持有 `EntityFactory`，负责 warp、monster、object population；owner `core/map-runtime`，删除条件是由 population/assembly port 接管实体创建。
- `Act*MapBuilder*.java`、`BaseMapBuilderD2MOD.java`：部分 builder 直接使用 `EntityFactory` 和 `NativeDataTables`；owner `core/map-builder`，删除条件是 builder 只输出布局、碰撞、warp 和对象描述。
- `DrlgLevel.java`、D2MOD builders：直接读取 `NativeDataTables` 尺寸/分组数据；owner `core/map-data-adapter`，删除条件是规则数据通过显式 map-data port 注入。
- `Box2DPhysics.java` 和地图相关组件引用：属于现有 ECS/物理适配债务，本批登记，不迁移行为。

扫描当前发现 124 条直接依赖，固定在
`tools/architecture/m1b-map-entity-boundary-baseline.tsv`；报告键忽略行号，只检测新增文件/类别/代码引用。

## 门禁

新增 `tools/architecture/check-map-entity-boundary.ps1` 和 Gradle 任务
`:core:mapEntityBoundaryCheck`，已接入 `:core:architectureRegression`。新增地图到
EntityFactory、NativeDataTables 或服务端组件的直接引用会使门禁失败。

## 验证

```text
./gradlew.bat :core:mapEntityBoundaryCheck :core:architectureRegression --no-daemon
```

结果：`M1-B findings: 124`、`M1-B boundary drift: none`；架构回归、拆分模块测试和 core 编译通过。

M1-B 状态：边界审计和新增依赖硬门禁已完成；地图生成脱离 ECS 的实际迁移尚未完成，因此不宣称 M1-B 的最终领域隔离出口已满足。下一步应在一个更大的地图 population 迁移批次中引入显式 `MapPopulationPort`，再删除 `Map`/builder 对 `EntityFactory` 的直接持有。
