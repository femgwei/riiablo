# M0 测试基线与限制（2026-10-21）

## 基线范围

本基线只验证迁移边界和可复算的模块编译，不宣称全仓库行为测试全绿。命令在
`F:\\3rd_src\\riiablo`、`master`、提交 `f949f8917` 上执行。

## 通过项

| 命令 | 结果 |
|---|---|
| `./gradlew.bat :verifyModuleBoundaries :shared:test :game-data:test :server-domain:test :server-systems:test --no-daemon` | 通过；架构门禁通过；game-data、server-domain、server-systems 测试通过；shared 无测试源 |
| `./gradlew.bat :core:architectureRegression :core:compileJava :server:d2gs:compileJava :desktop:compileJava --no-daemon` | 通过；core 架构回归、core 编译、D2GS 编译、desktop 编译通过 |

此次没有使用真实 `G:\\BaiduNetdiskDownload\\Diablo II 1.10F` 资源，也没有启动客户端或网络实机验收。

## 已知限制

- 本基线未运行 `:core:test` 全量、完整 server/desktop 集成测试、真实 MPQ/headless 地图验收或双客户端验收。
- `core` 仍包含主机入口、属性运行时、表现适配、地图和大部分 ECS 战斗/AI；架构快照显示 31 个源码循环群，最大循环群 502 个。
- `server` 下 `net`/`nnet`/`onet` 与 `d2gs`/`d2gs_netty`/`d2gs_test` 多代实现仍并存；这属于 M1-C/M8-D 的后续范围。
- `map`、`map2`、`map5`、`map/d2moo` 与 `D2MOO_JAVA` 并存；这属于 M7-B 的后续范围。
- 迁移计划中 M3-B、M4-A、M8-A、M8-C、M8-D 等子项仍未完成，不能由本基线提前标记完成。

## M0 出口

- [x] 目录 owner、依赖方向和反向依赖有可复算记录。
- [x] 当前边界门禁和拆分模块编译基线可复现。
- [x] 已知未覆盖范围、结构缺口和资源限制已记录。
- [x] 不改变生产行为。

M0 完成后进入 M1。建议下一批为 M1-A：先对 `engine/client` 到 `screen/widget`、音频和渲染对象的反向依赖建立逐文件清单；M1-C 的网络多代实现盘点可并行，边界分别限定在 `core/src/main/java/com/riiablo/engine/client` 与 `server/`。
