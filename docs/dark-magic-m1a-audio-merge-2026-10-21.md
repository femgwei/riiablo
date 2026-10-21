# M1-A 合并表现依赖迁移第 7 批（2026-10-21）

本合并批次同时收口两个音频系统：

- `ObjectAmbientSoundSystem` 依赖 `ClientEnvironmentAudioServices`，不再绑定具体 `ClientAudioSession`。
- `SoundEventSystem` 依赖 `ClientSpatialAudioServices`，只消费监听实体和音频播放入口。

`ClientAudioSession` 同时实现两个窄接口，环境音、空间衰减、频道、循环和淡入淡出行为保持不变。`MonsterSoundEmitter` 留到下一组合并批次，因为它还需要 `Files.MonSounds` 数据服务。

验证：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientAudioSessionTest --tests com.riiablo.engine.client.SoundEventNetworkTest :core:architectureRegression --no-daemon
```

结果：BUILD SUCCESSFUL。下一批继续合并处理 `MonsterSoundEmitter` 与 `ClientEntityFactory` 的音频依赖。
