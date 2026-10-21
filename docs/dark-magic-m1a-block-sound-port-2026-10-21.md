# M1-A 表现依赖迁移第 4 批（2026-10-21）

将 `BlockSoundEmitter.clientContext` 从完整 `ClientGameplayContext` 收窄为
`ClientPresentationServices`。该系统只需要音频播放入口；实体分类、模式判断、距离衰减和声音键选择保持原样。

验证：

```text
./gradlew.bat :core:compileJava :core:architectureRegression --no-daemon
```

结果：BUILD SUCCESSFUL。下一批继续处理只依赖音频服务的 `ObjectBreakSoundEmitter` 或 `SoundEnvironmentSystem`，每批保持单系统边界。
