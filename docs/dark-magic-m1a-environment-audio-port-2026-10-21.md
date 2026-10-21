# M1-A 表现依赖迁移第 6 批（2026-10-21）

新增 `ClientEnvironmentAudioServices` 窄接口，并让 `ClientAudioSession` 实现它。
`SoundEnvironmentSystem` 现在依赖该接口，不再绑定具体会话类；环境表、监听实体、音频通道、淡入淡出和事件时序保持不变。

验证：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientAudioSessionTest :core:architectureRegression --no-daemon
```

结果：BUILD SUCCESSFUL。下一批继续处理 `ObjectAmbientSoundSystem`，复用同一环境音频边界。
