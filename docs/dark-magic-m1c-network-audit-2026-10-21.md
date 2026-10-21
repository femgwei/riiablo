# M1-C 第一批：server 网络实现盘点（2026-10-21）

本批只登记 `server/` 网络多代实现，不改变协议或会话行为。扫描确认网络生产代码主要集中在 `server/netty`，并由 `server/bnls`、`server/bncs`、`server/mcp`、`server/d2gs` 提供入口。

## Owner 与边界

| 实现族 | 路径/包 | 当前角色 | owner | 删除/收敛条件 |
|---|---|---|---|---|
| FlatBuffers/TCP | `server/netty` `com.riiablo.net`, `com.riiablo.net.tcp` | 协议适配与 TCP transport | `server/netty` | 每类消息有唯一 protocol owner，规则系统不直接操作 socket |
| 旧 nnet | `server/netty` `com.riiablo.nnet`, `com.riiablo.nnet.tcp` | 并存 packet/endpoint API | `server/netty`（过渡） | 无生产调用者后删除或冻结为测试适配器 |
| 旧 onet | `server/netty` `com.riiablo.onet`, `com.riiablo.onet.reliable`, `com.riiablo.onet.tcp` | 可靠/不可靠通道与实验 TCP | `server/netty`（过渡） | 可靠性语义迁移到唯一 transport 后删除；`TestClient/TestServer` 仅测试 |
| D2GS Netty | `server/netty` `com.riiablo.server.d2gs_netty` | D2GS message processor、同步和服务端启动 | `server/netty` | authoritative session 与 transport 分层后收敛为唯一入口 |
| D2GS test | `server/netty` `com.riiablo.server.d2gs_test` | headless/test client-server harness | `server/netty`（测试） | 不得进入生产依赖；保留为测试适配器 |
| D2GS domain/session | `server/d2gs` `com.riiablo.server.d2gs` | 权威模拟、命令处理、会话状态 | `server/d2gs` | 只接受 intent，网络 transport 通过 adapter 接入 |
| BNLS/BNCS/MCP | `server/bnls`, `server/bncs`, `server/mcp` | 登录、战网和 MCP 协议入口 | 各自模块 | 协议 owner 明确后禁止跨模块复制会话实现 |

## 事实结论

- `net`、`nnet`、`onet` 三套底层 API 在同一 `server/netty` 模块并存。
- `d2gs_netty` 与 `d2gs_test` 与 `d2gs` 权威域并存；其中 test 包必须保持测试边界。
- 本批没有把并存实现误判为已完成分层；M8-D 仍负责最终网络三层收敛。

## 验证

静态扫描命令：

```powershell
Get-ChildItem server -Recurse -Filter '*.java'
rg -n 'package com\.riiablo\.(net|nnet|onet|server\.d2gs_netty|server\.d2gs_test)' server
```

扫描成功；未修改生产代码，未运行网络实机测试。

本批完成度：100%。下一批：M1-C 第二批，为上述实现生成可复算包/文件清单，并检查 `d2gs_test` 不被生产 source set 引用。M1-A 后续表现迁移可与之并行，边界分别为 `core/engine/client` 与 `server/`。
