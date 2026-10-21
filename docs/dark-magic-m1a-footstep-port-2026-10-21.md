# M1-A 表现依赖迁移第 3 批（2026-10-21）

将 `FootstepEmitter.clientContext` 从 `ClientGameplayContext` 收窄为
`ClientPresentationServices`。该系统只需要动态玩家实体和音频播放，不再依赖主机上下文的其他绑定。

`ClientGameplayContext` 继续作为兼容实现，`fromGlobals()` 可直接注入；未改变脚步触发、材质选择或声音播放行为。

验证：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientGameplayContextTest :core:architectureRegression --no-daemon
```

结果：BUILD SUCCESSFUL。下一批继续选择只使用音频/资源服务的客户端系统，逐步减少完整上下文类型传播。
