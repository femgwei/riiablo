# Trap-Melee 验证

原版依据：D2MOO `source/D2Game/src/AI/AiThink.cpp`，`AITHINK_Fn087_TrapMelee`，0x6FCDE960。

新增专用 AI 和 `Trap-Melee` 表名映射，不改变其他 AI 的加载路径。
不在近战或无目标等待 40 帧；近战按 param0 判断普通 A1 攻击，失败按 param1 等待。

固定 `source-base-sha.txt` 对应的 git archive，展开到 `build/trap-melee-source`，只覆盖 `source-sha256.txt` 中三个文件。
验证前后这些工作区文件与隔离源码 SHA256 相同；隔离构建使用本目录 `isolated.init.gradle`。

运行（PowerShell，仓库根目录）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat -p build/trap-melee-source --project-cache-dir build/trap-melee-cache -I "$PWD/docs/ai-arena/20261016-trap-melee/isolated.init.gradle" :core:test --tests '*TrapMeleeAiIntegrationTest' --tests '*VileDogAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
```

结果：36 项通过，0 失败，0 错误；本目录保留日志和 XML。
新增 5 项覆盖真实表名解析、三难度近战攻击及动作阻塞、配置等待、远处等待、无目标等待。
Fixture 使用真实 1.10f 表和模拟寻路器；本次无图形客户端或自然地图验收。

本轮进度：完成 1/37，剩余 36。未发现本项需要登记的大依赖。
