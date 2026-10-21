# M1-C 第三批：生产入口引用检查（2026-10-21）

新增 `tools/architecture/check-network-entrypoints.ps1`，扫描生产源码对
`d2gs_test`、`d2gs_netty` 以及旧 `nnet`/`onet` transport 的引用，并输出
`build/reports/m1c-network-entrypoints.tsv`。

当前结果：`d2gs_test_refs=0`、`d2gs_netty_refs=0`；测试 harness 没有被其他生产源码显式引用。
旧 `nnet`/`onet` 的引用仍存在于 `server/netty` 的 D2GS transport 适配器中，这是已登记的
M8-D 收敛债务。脚本只检查引用，不把 `d2gs_test` 当前位于 `src/main` 的事实隐瞒为已解决。

验证命令：

```powershell
powershell -ExecutionPolicy Bypass -File tools/architecture/check-network-entrypoints.ps1
```

本批完成度：100%。下一批可进入 M1-C 收口记录，或转入 M1-A 后续迁移；两者可并行，分别限定在 `server/` 与 `core/engine/client`。
