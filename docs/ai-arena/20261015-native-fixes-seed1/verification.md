# 本机复测证据

- 2026-10-15，Windows，1.10f MPQ来自 `G:\BaiduNetdiskDownload\Diablo II 1.10F`。
- Normal / seed=1 / 500个25Hz tick每场，557行、1136场全部执行。
- 源运行目录：`server/d2gs/build/monster-ai-arena/20261015-native-fixes-final`。
- 前一完整运行目录：`server/d2gs/build/monster-ai-arena/20261015-native-fixes-seed1`；
  最终JSONL与其逐行相同。两份运行均保留，不覆盖首轮和诊断失败输出。
- 对比提交内旧表 `../20261015-normal-seed1/results.jsonl`：只有GreaterMummy30场、
  BloodLord14场改变，其余1092场逐字段相同。木乃伊10场、萨满8场真实复活成功。
- 141项定向测试通过，失败/错误/跳过均0（core115、d2gs26）。
- 这是诊断复测，不是全AI正确性PASS；剩余47种AI名称回退及其他告警保留。

完整复跑（新目录名必须未存在）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests '*GreaterMummyAiIntegrationTest' --tests '*BloodLordAiIntegrationTest' `
  --tests '*NativeMummySequenceTest' --tests '*BestowIntegrationTest' `
  --tests '*ServerMonsterCorpseSystemTest' --tests '*Act1NativeAiIntegrationTest' `
  --tests '*Act1MonsterEcsScenarioTest' --tests '*UnitLifecycle*Test' `
  --tests '*TentacleAiIntegrationTest' --tests '*BaalCrabAiIntegrationTest' `
  --tests '*BaalCrabCloneAiIntegrationTest' --tests '*NativeFrenzyDataTest' `
  --tests '*BarbarianFrenzyTest' --tests '*SequenceHandler*Test' `
  :server:d2gs:test --tests '*MonsterAiArenaTest' `
  --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' `
  :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-native-fixes-recheck `
  --console=plain
```

`logs.zip` 包含1136个逐场日志，解压到本目录可使用report.html的日志链接。
详细根因、D2MOO依据、修复范围和剩余问题见 `../../monster-ai-arena.md` 最新批次。
未执行全项目测试套件、三难度/多seed全矩阵，也未代替另一台电脑的真实地图/TCP验证。
