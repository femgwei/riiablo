# M1-A 合并收口批次（2026-10-21）

本批完成 M1-A 的新增依赖硬门禁，不再按单个客户端系统拆分：

- `check-client-presentation-boundary.ps1` 现在支持基线比较和 `-FailOnDrift`。
- 固定当前 `engine/client` 的 83 条既有表现依赖为债务基线，比较键忽略行号变化，只检测新增文件/类别/代码引用。
- 新增 `:core:clientPresentationBoundaryCheck`，并接入 `:core:architectureRegression`；Gradle 任务固定在仓库根目录运行，避免相对路径漂移。

验证：

```text
./gradlew.bat :core:clientPresentationBoundaryCheck :core:architectureRegression --no-daemon
```

结果：通过；报告 `M1-A findings: 83`、`M1-A boundary drift: none`，架构回归及拆分模块测试全部通过。

M1-A 当前状态：既有违规已登记并具备新增违规硬门禁；剩余表现迁移仍作为债务收口，不再阻塞后续 M1-B 的启动。
下一步：进入 M1-B 地图/实体边界合并批次；M1-C 的旧网络实现保留在已登记 owner 内，后续再做实际收敛。
