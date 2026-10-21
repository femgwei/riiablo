# M1-A 表现依赖迁移第 5 批（2026-10-21）

将 `ObjectBreakSoundEmitter` 的实体声音播放从静态 `MonsterAudio.play` 收口到
`ClientPresentationServices.playEntitySound`。`ClientGameplayContext` 提供兼容实现，
对象模式与声音表选择保持不变；系统现在通过可替换的表现端口播放声音。

验证：

```text
./gradlew.bat :core:compileJava :core:architectureRegression --no-daemon
```

结果：BUILD SUCCESSFUL。下一批继续处理 `SoundEnvironmentSystem` 或 `ObjectAmbientSoundSystem` 的音频会话依赖。
