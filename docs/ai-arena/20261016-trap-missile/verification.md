# Trap-Missile 验证

原版依据：D2MOO `source/D2Game/src/AI/AiThink.cpp`，`AITHINK_Fn077_TrapMissile`，0x6FCDE4D0。

实现距离上界（含边界）、总发射次数、攻击与等待交替、无目标/超距/次数耗尽进入死亡模式。
耗尽检查优先于等待分支，最后一发结束后直接死亡，不额外等待。
复用 A1 普通攻击的 MonStats.MissA1 飞弹路径和 NativeUnitFlags.NO_TREASURE_CLASS 禁止掉落标志。
补齐非法 Java 表名 `Trap-Missile` 到专用类的映射，无通用战斗系统改动。

固定 `source-base-sha.txt` 对应的 git archive，展开到 `build/trap-missile-source`，
仅覆盖 `source-sha256.txt` 列出的三个文件。使用本目录 init 脚本隔离全部 Java 编译和测试输出。

运行（PowerShell，仓库根目录）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat -p build/trap-missile-source --project-cache-dir build/trap-missile-cache -I "$PWD/docs/ai-arena/20261016-trap-missile/isolated.init.gradle" :core:test --tests '*TrapMissileAiIntegrationTest' --tests '*TrapMeleeAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
```

测试使用真实 1.10f 表与共享 AI Fixture（模拟寻路），覆盖两条表记录、三难度攻击/等待/耗尽时序、
动作阻塞、距离边界、无目标、零次数、发射后超距优先于等待、禁止掉落及死亡后不再决策。
本次验证 AI 决策与共享动作生命周期，未进行图形客户端或自然地图飞弹命中验收。

结果：37 项测试通过（TrapMissile 6、TrapMelee 5、Act1Native 26），0 失败、0 错误。
三个覆盖文件与隔离源码 SHA256 一致，原始日志与 XML 保存在本目录。
累计完成 2/37，剩余 35 类；本项无大依赖。
