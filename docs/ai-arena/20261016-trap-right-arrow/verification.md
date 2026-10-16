# Trap-RightArrow 验证

原版依据：D2MOO `AiThink.cpp`，`AITHINK_Fn078_TrapRightArrow`，0x6FCDE570。

距离区间两端均包含，超区间或无目标等待 40 帧；整数 X 坐标差大于 2 或尚未越过冷却截止帧时等待 30 帧。
截止帧使用 param2；相对帧时钟在动作期间继续推进，整数帧比较保留原版严格大于条件。
原版另写 aiParam[2] 为相同技能截止帧，但本函数没有读取它，不另加不存在的等待分支。

随 AI 完成的小依赖：Map 按关卡保存原版 monster-region unk0x2D4；
关卡小于 40 固定类型 0，不消耗随机数；其余关卡第一次有效攻击消耗陷阱原版种子流的 uint32 % 3。
同地图同关卡后续陷阱复用类型，地图释放时清除；类型 1 施放本行 Skill1，其余类型普通 A1。
缺少 Skill1 时保留原版 30 帧等待，不替换为普通攻击。

固定 `source-base-sha.txt` 对应 git archive 到 `build/trap-right-arrow-source`。
只覆盖新增 AI、测试及 Map.java；AI.java 在 archive 版本追加 `ai-mapping.patch` 的本项映射，
避免混入共享工作区其他 AI 目标系统改动。实际隔离源码 SHA256 见 `source-sha256.txt`。

运行（PowerShell，仓库根目录）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat -p build/trap-right-arrow-source --project-cache-dir build/trap-right-arrow-cache -I "$PWD/docs/ai-arena/20261016-trap-right-arrow/isolated.init.gradle" :core:test --tests '*TrapRightArrowAiIntegrationTest' --tests '*TrapPoisonAiIntegrationTest' --tests '*TrapNovaAiIntegrationTest' --tests '*TrapMissileAiIntegrationTest' --tests '*TrapMeleeAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --console=plain --max-workers=1
```

测试使用真实 1.10f 表、共享 AI Fixture、模拟寻路和合成区域，验证决策与动作生命周期。
尚未进行自然地图箭矢命中或图形客户端验收。Trap-LeftArrow 不计入本项完成量。

结果：56 项通过，0 失败、0 错误（TrapRightArrow 7、TrapPoison 5、TrapNova 7、
TrapMissile 6、TrapMelee 5、Act1Native 26）。
实表为 trap-horzmissile，普通箭 MissA1=trap spike right，Skill1=PoisonBallTrap、S1、
SrvDo0、SrvMissile=trap poison ball left；后者走已有通用服务端飞弹路径，无额外技能依赖。
本项小依赖已补齐，无新增大依赖；累计完成 5/37，剩余 32 类。
