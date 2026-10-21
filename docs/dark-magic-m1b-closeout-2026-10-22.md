# M1-B 地图生成边界验收（2026-10-22）

M1-B 已完成。实现提交：`2a6416ef0`。

本记录接续 2026-10-21 的边界审计和 population port 迁移记录；旧记录中的 124/103 条依赖及“尚未完成”是当时状态，当前验收以本记录为准。

## 生成与装配

- `Map`、Act builders、`drlg` 只产生布局、碰撞、warp、preset/object 和怪物放置描述，不持有 ECS world、实体工厂或 socket，不创建实体。
- `MapPopulationPlan` 保存兼容生成器和 Blood Raven 的怪物 ID、坐标与区域；native room 的怪物/object 队列继续按原房间激活流程消费。
- `MapManager` 是地图实体装配的唯一 owner，通过 `MapPopulationPort` 调用现有工厂。计划按顺序消费，重复调用不重放；工厂抛出异常时重试从未成功的项继续；释放/重建地图后切换到新计划。
- 远程客户端由 host 显式配置，不生成本地怪物。`MapRuntimeBindings` 在运行时绑定 ECS warp 索引查询，地图只使用数据查询函数。
- `MapDataPort` 委托共享的 `codec/excel/NativeTableValues`。服务端 `NativeDataTables` 复用相同访问器，保留难度回退和尺寸/怪物分组边界。
- 自动地图名称分类移至 `MapLevelNames`，客户端保留委托门面，地图生成不再反向依赖客户端分类器。

## 门禁与验证

生成层依赖预算为 0。`MapManager`、`Box2DPhysics`、`RenderSystem` 作为明确的运行时适配器，58 条既有依赖独立登记在 `tools/architecture/m1b-map-entity-boundary-baseline.tsv`；任何生成层违规都会失败，不能通过更新适配器基线放行。

```text
./gradlew.bat :core:mapBoundaryRegression :core:architectureRegression :server:d2gs:compileJava :server:netty:compileJava :desktop:compileJava "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F" --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File tools/architecture/check-map-entity-boundary.ps1 -FailOnDrift
```

M1-B 回归：95 项，失败 0、错误 0、跳过 0。覆盖 A1–A5 真实 1.10F MPQ/headless 布局、怪物放置与装配、异常后重试、会话隔离、warp 查询、town NPC 描述、waypoint 和数据访问边界。架构回归和所有上述 host 编译通过。

边界报告：`M1-B findings: 58 (owned runtime adapters)`、`M1-B generation findings: 0`、`M1-B boundary drift: none`。生成层的 `EntityFactory`、`NativeDataTables`、服务端 component、Artemis、socket 和工厂创建调用均在禁止范围。

## D2MOO 依据及剩余范围

保持 `DrlgDrlg.allocDrlg`、`DrlgOutPlace.createLevelConnections` 和 `DrlgPresetUnitExporter` 的 native 布局/描述链。`NativeTableValues` 提取已有 DATATBLS 访问语义，不增加数值回退或改变怪物选择规则；本项不声称新增 D2 全行为等价性。

`Box2DPhysics` 的 ECS/物理适配和 `RenderSystem` 的 ECS/表现适配仍是已有债务，分别由物理适配 owner 和 M8-C 负责。生成器对已有 D2 纯规则策略的依赖属于 M7-A，`map/map2/map5/map/d2moo/D2MOO_JAVA` 的地图代际收敛属于 M7-B，均尚未完成。M1-B 完成不代表 M1 整体完成。
