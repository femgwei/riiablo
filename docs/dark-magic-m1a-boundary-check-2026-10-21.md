# M1-A 第二批：可复算边界检查（2026-10-21）

新增 `tools/architecture/check-client-presentation-boundary.ps1`，扫描
`core/src/main/java/com/riiablo/engine/client` 对 `screen`、`widget`、`audio` 和
`graphics` 的直接引用，并输出 TSV 快照。当前快照位于
`build/reports/m1a-client-presentation-boundary.tsv`，不纳入提交。

运行方式：

```powershell
powershell -ExecutionPolicy Bypass -File tools/architecture/check-client-presentation-boundary.ps1
```

脚本当前用于登记既有债务；`-FailOnFindings` 可用于未来迁移完成后的硬门禁。当前目录仍有既有表现依赖，因此不以零结果作为本批出口条件。

验证结果：脚本成功运行并生成快照；`SoundP.java` 尾部空行已规范化，未产生额外差异。

本批完成度：100%。下一批：M1-C 第一批，盘点 `server/` 网络多代实现；与 M1-A 后续迁移可并行，文件边界分别为脚本/报告与 `server/`。
