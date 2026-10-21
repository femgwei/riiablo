# M1-C 收口记录（2026-10-21）

M1-C 的“网络实现盘点”阶段已收口，迁移实现仍归 M8-D。三批审计建立了唯一 owner、生产 source-set 事实和入口引用基线。

## 已完成

- `server/netty` 的 `net`、`nnet`、`onet`、`d2gs_netty`、`d2gs_test` 已按 protocol、transport、测试适配器分类。
- `server/d2gs` 已登记为 authoritative session/domain owner；BNLS、BNCS、MCP 各自保留协议入口 owner。
- 可复算清单：75 个网络 Java 文件；`d2gs_test` 有 2 个文件仍位于 `src/main`，属于已知 source-set 债务。
- 生产引用检查：`d2gs_test_refs=0`、`d2gs_netty_refs=0`；旧 `nnet`/`onet` 仍由 D2GS transport 适配器使用。

## 出口判断

盘点出口已满足：owner、边界、反向引用和删除条件均有记录，且静态检查可复算。最终“每个消息/会话路径只有一套生产入口”尚未满足，因此 M8-D 不能标记完成；M1-C 后续只在网络迁移发生时重新打开。

验证脚本：

```powershell
powershell -ExecutionPolicy Bypass -File tools/architecture/check-network-implementation-inventory.ps1
powershell -ExecutionPolicy Bypass -File tools/architecture/check-network-entrypoints.ps1
```

下一步：转入 M1-A 后续表现迁移；M8-D 保留为后续网络三层收敛任务。可并行边界为 `core/src/main/java/com/riiablo/engine/client` 与 `server/`。
