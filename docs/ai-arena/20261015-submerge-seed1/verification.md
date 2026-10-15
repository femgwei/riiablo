# 本机复测证据：Submerge

- 2026-10-15，Windows，1.10f MPQ来自 `G:\BaiduNetdiskDownload\Diablo II 1.10F`。
- 原版依据：D2MOO `8322494ed1f715ad51552f169df76cf600fabc71`，
  `SkillMonst.cpp` 的SrvSt51/SrvDo094/SrvSt52、`Units.cpp` 的源帧指标/序列推进、
  `AiThink.cpp` Fn056/Fn057，以及本机MonSeq原表。不是只凭动画经验推测。
- 发布源运行目录：`server/d2gs/build/monster-ai-arena/20261015-submerge-final3`。
  早期probe、cycle-probe、final2均保留，不覆盖旧运行；final2尚未保留未接入序列的专门告警，
  不作为发布快照。最终新增回归验证未知Submerge序列仍报WARN，蛙魔far告警数量与旧表相同。
- Normal / seed=1 / 500个25Hz tick每场，557行、1142场全部执行：
  ACTION_OBSERVED=477、AI_FALLBACK=376、WARNING=106、NO_ACTION=172、
  MOVEMENT_ONLY=7、TERMINAL_OBSERVED=4；RUNTIME_ERROR/ERROR_LOG/NOT_RUN/
  SPECIAL_NOT_TRIGGERED均0。fallback仍对应188行、47个AI名称。
- 新增3行Tentacle及3行TentacleHead的submerge-cycle均观察到主体保持、恢复0x0E、
  浮出动画结束和后续真实A1动作，均0告警/错误。日志 `SubmergeCycle phase=1/2/3/4`
  依次对应四阶段；tentacle1分别发生在tick17/417/434/451。
- 原8场萨满和10场GreaterMummy真实复活仍成功。6行单独执行的21场与全目录对应记录
  逐字段一致；辅助怪的动作/伤害不能满足主体的专属断言。
- 与 `../20261015-native-fixes-seed1/results.jsonl` 按monster/scenario比较全部字段：
  仅12个Tentacle/TentacleHead near/far既有场次变化，其余1124场逐字段一致；
  无既有场次丢失，新增6个submerge-cycle单独计入。WARNING从118降至106，未改白名单。
- 155项定向回归通过（core128、d2gs27），失败/错误/跳过均0。时钟测试覆盖
  12/20源帧、半/常/双速、原表动作点、无重复动作/无动画结束、冻结、猎物消失、
  未移植序列保留告警、主人概率/SQ、客户端反向播放/保持/恢复；生产场景不使用替身。

完整复跑（输出目录必须未存在）：

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
  --tests '*SubmergeSequenceIntegrationTest' --tests '*NativeSubmergeDataTest' `
  --tests '*AnimationStepperTest' `
  :server:d2gs:test --tests '*MonsterAiArenaTest' `
  --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' `
  :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-submerge-recheck `
  --console=plain
```

只复跑六行时，在arena命令增加：

```powershell
'-ParenaMonsters=tentacle1,tentacle2,tentacle3,tentaclehead1,tentaclehead2,tentaclehead3'
```

其余场次会明确登记NOT_RUN，不是全目录通过。`logs.zip` 包含1142个逐场日志，
解压到本目录后可使用report.html的日志链接。

这仍是诊断复测，不是全AI正确性PASS。FrogDemon未接入其独立潜入序列/完整决策，
不能因共享94回调就算完成；Inferno、其他技能/MonSeq和资源问题仍保留。
未运行全项目测试、三难度/多seed完整矩阵，也未代替另一台电脑的真实地图/TCP/GL验证。
