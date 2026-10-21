# M1-A 表现依赖迁移第 2 批（2026-10-21）

新增 `com.riiablo.presentation.ClientPresentationServices`，声明客户端系统需要的最小表现服务：游戏屏幕供应、音频和调色批处理器。`ClientGameplayContext` 实现该接口，保留原有具体类型 getter 作为兼容层；本批没有改变构造方式或运行时行为。

验证：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientGameplayContextTest :core:architectureRegression --no-daemon
```

结果：通过。下一批将把单个客户端系统的字段类型改为 `ClientPresentationServices`，逐步减少 engine/client 对主机上下文的直接依赖。
