# M1-C 第二批：网络实现可复算清单（2026-10-21）

新增 `tools/architecture/check-network-implementation-inventory.ps1`，按包族扫描
`server/netty`、`server/d2gs`、`server/bnls`、`server/bncs` 和 `server/mcp`，输出
`build/reports/m1c-network-inventory.tsv`。当前清单共 75 个 Java 文件。

检查结果：`com.riiablo.server.d2gs_test` 有 2 个文件，且位于
`server/netty/src/main/java`，所以当前确实属于生产 source set，而不是测试 source set。
这是 M1-C/M8-D 已登记的结构债务；本批不移动文件，避免改变现有启动与测试行为。收敛条件是
将 harness 移入 `src/test` 或独立测试适配器后，再启用零生产引用门禁。

验证命令：

```powershell
powershell -ExecutionPolicy Bypass -File tools/architecture/check-network-implementation-inventory.ps1
```

结果：脚本成功运行，输出 `entries=75 d2gs_test_main_entries=2`。
本批完成度：100%。下一批可开始 M1-C 第 3 批，建立生产入口引用检查；M1-A 后续迁移可并行。

