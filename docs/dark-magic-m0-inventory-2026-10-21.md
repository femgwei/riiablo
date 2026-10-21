# M0 第一批：目录 owner 与依赖边界清单（2026-10-21）

本批固定迁移范围，不改变运行时行为。清单由 `settings.gradle`、源码目录和
`tools/architecture/dependencies.py` 生成；生成快照位于 `build/reports/m0-inventory/`，
不纳入提交。

## 唯一 owner

| 目录/模块 | Java 源文件数 | owner | 迁移归属 |
|---|---:|---|---|
| `core` | 2141 | core/legacy + client presentation adapter | M1-A、M2、M3、M5、M8-C |
| `server` | 85 | protocol/transport adapters（按子模块） | M1-C、M3、M8-D |
| `quests` | 51 | quest policy/domain | M1、M7 |
| `server-systems` | 5 | server mechanism adapters | M1、M3 |
| `shared` | 30 | shared mechanism primitives | M1 |
| `game-data` | 81 | immutable table/data loading | M4 |
| `D2MOO_JAVA` | 135 | native-rule evidence/compatibility adapter | M4、M5、M7 |
| `desktop` | 3 | desktop presentation host | M8-C |
| `android` | 1 | Android presentation host | M8-C |

`server` 的生产子模块 owner 进一步固定为 `bnls`、`bncs`、`mcp`、`d2gs`、`netty`；
`tools/*`、`excel/*`、`table/*` 和 `tester` 属于工具/测试 owner，不进入本轮领域迁移。

## 允许的生产依赖方向

```text
presentation hosts (desktop/android) -> core
core -> server-systems -> server-domain -> game-data -> shared
server-domain -> D2MOO_JAVA
quests -> server-domain
server/* -> core or domain according to existing Gradle boundary
```

已有物理拆分门禁验证了新模块的白名单方向；当前仍有 502 类型的最大 core 内部循环群和
31 个源码循环群，不能把这份清单解释成完整分层已完成。

## 反向依赖登记（M0 事实记录）

- `core/engine/client` 仍直接引用 `screen`、`widget`、音频和渲染对象：owner `core`，迁移行 M1-A/M8-C，删除条件是表现订阅适配器覆盖后移除直接 import。
- `map`、`Act*MapBuilder*` 仍直接创建 `EntityFactory`、读取服务端组件或 `NativeDataTables`：owner `core`，迁移行 M1-B/M7-B，删除条件是地图只输出布局/碰撞/对象描述。
- `server/netty` 与 `server/d2gs` 并存，且 `net`/`nnet`/`onet`、`d2gs_netty`/`d2gs_test` 多代实现并存：owner `server`，迁移行 M1-C/M8-D，删除条件是每条消息和会话路径只有一个生产入口。
- `map`、`map2`、`map5`、`map/d2moo`、`D2MOO_JAVA` 并存：owner `core`/`D2MOO_JAVA`，迁移行 M7-B，删除条件是主地图链通过真实 MPQ/headless 验收且过渡目录无生产调用者。
- `core` 仍保留 `Riiablo.files`、属性运行时、全局随机和大规模 ECS 战斗/AI：owner `core`，迁移行 M2/M3/M4/M5；本批只登记，不迁移。

## 出口核对

- [x] 目标目录均有唯一 owner。
- [x] 生成了可复算的源码依赖快照和模块图。
- [x] 反向依赖均登记 owner、迁移行和删除条件。
- [ ] M0 尚未完成：测试基线报告仍待下一批建立；当前文档不宣称行为基线可复现。

下一批：M0 第二批，固定测试基线、已知失败和资源/环境限制；完成后再决定是否进入 M1。
