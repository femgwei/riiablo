# M1-A 合并表现依赖迁移第 8 批（2026-10-21）

本批收口 `MonsterSoundEmitter` 的声音服务边界：

- 新增 `ClientMonsterAudioServices`，组合空间音频与 `Files` 数据访问。
- `ClientGameplayContext` 实现该接口。
- `MonsterSoundEmitter` 改为依赖窄接口，保留 MonSounds 表解析、距离衰减、脚步/中立/死亡声音时序。

`ClientEntityFactory` 仍保留完整上下文，因为同一类还负责 shrine、装备和实体创建资源；其音频调用留到独立合并批次，避免混入数据依赖迁移。

验证：

```text
./gradlew.bat :core:test --tests com.riiablo.engine.client.ClientGameplayContextTest :core:architectureRegression --no-daemon
```

结果：BUILD SUCCESSFUL。下一批处理 `ClientEntityFactory`、`MissileImpactPresentationSystem` 等仍持有完整上下文的声音调用。
