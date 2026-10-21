# M1-A 表现依赖迁移第 1 批（2026-10-21）

将 `ClientGameplayContext` 从 `engine/client` 移入 `com.riiablo.presentation`，明确它是客户端主机到表现服务的适配绑定，而不是仿真 engine 类型。现有构造参数和 `fromGlobals()` 兼容桥保持不变，调用方仅更新 import/限定名。

验证：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientGameplayContextTest :core:architectureRegression :desktop:compileJava :server:d2gs:compileJava --no-daemon
```

结果：BUILD SUCCESSFUL；上下文测试、架构回归、desktop 与 D2GS 编译通过。

边界：本批未移除 Audio、GameScreen、PaletteIndexedBatch 等具体表现类型；下一批继续把这些依赖收敛到 presentation port，再逐步禁止 engine 新代码直接引用设备/渲染对象。
