# 2026-10-15 Normal / seed=1 逐怪测试记录

打开 `report.html` 查看可搜索的逐场表；完整字段在 `results.jsonl`。
本目录是生成结果快照，不应手工改行状态。新运行使用新的输出目录。

运行：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-verification `
  --console=plain
```

557个敌对数据行，1136场，500个权威25Hz tick/场（加速模拟）。
443场观察到动作、7场仅移动、390场AI fallback、117场告警、165场无动作、
10场专项未触发、4场无主人正常死亡；运行时异常/ERROR/未执行均0。
COMPLETED 仅表示遍历完成，绝不表示原版AI逻辑全部正确。
fallback覆盖195个数据行、48个AI名称，不能把场次数当怪物数。

`logs.zip` 有1136个原始逐场日志。拉取后在本目录运行：

```powershell
Expand-Archive -LiteralPath .\logs.zip -DestinationPath .
```

解压后 report.html 的逐场 log 链接可用。压缩包不包含 MPQ、原版 exe/dll 或其他
游戏资源。完整初始失败/复跑日志仍在执行机器的 server/d2gs/build/monster-ai-arena。

定向 JUnit 回归79项：核心53、服务端26，0失败/错误/跳过；另选3个怪物9场复跑，
所有JSON字段与完整运行一致，验证子集执行隔离。
完整设计、统计来源及未覆盖边界见 [测试场说明](../../monster-ai-arena.md)。
