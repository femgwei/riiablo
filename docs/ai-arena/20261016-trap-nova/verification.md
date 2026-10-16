# Trap-Nova 验证

原版依据：D2MOO `source/D2Game/src/AI/AiThink.cpp`，
`AITHINK_Fn080_092_TrapPoison_TrapNova`，0x6FCDE8B0。本次仅接入 Trap-Nova，Trap-Poison 尚未计入完成量。

实现目标距离上界（含边界）、施放次数、Skill1 与等待交替，
无目标/超距/耗尽进入死亡模式并设置 UNITFLAG_NOTC。
最后一次施放结束后直接死亡；缺少 Skill1 只等待，不消耗次数；
调用技能失败仍消耗次数，与原版在 UseSkill 后无条件更新计数一致。
技能施放、死亡动作和禁止掉落复用已有生产机制，仅新增专用 AI 与表名映射。

固定 `source-base-sha.txt` 对应 git archive 到 `build/trap-nova-source`。
覆盖本项两个新 Java 文件；AI.java 只在 archive 版本追加 Trap-Nova 映射，
不包含共享工作区中其他任务的 alignmentOwner/CombatOwnership 修改。
`source-sha256.txt` 记录实际隔离验证源码的 SHA256。

运行（PowerShell，仓库根目录）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat -p build/trap-nova-source --project-cache-dir build/trap-nova-cache -I "$PWD/docs/ai-arena/20261016-trap-nova/isolated.init.gradle" :core:test --tests '*TrapNovaAiIntegrationTest' --tests '*TrapMissileAiIntegrationTest' --tests '*TrapMeleeAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --tests '*SorceressNovaIntegrationTest' --console=plain --max-workers=1
```

新增测试使用真实 1.10f 表、共享 AI Fixture 和模拟寻路器，验证决策与动作生命周期。
不代表图形客户端或自然地图新星命中验收。

结果：65 项通过，0 失败、0 错误（TrapNova 7、TrapMissile 6、TrapMelee 5、Act1Native 26、SorceressNova 21）。
实表 Skill1 为 Trap Nova、SrvDo022、S1；复用现有新星处理，无新增大依赖。
累计完成 3/37，剩余 34 类。
