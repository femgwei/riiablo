# Trap-Poison 验证

原版依据：D2MOO `source/D2Game/src/AI/AiThink.cpp` 的 AI 表，Fn080、Fn092
均指向 `AITHINK_Fn080_092_TrapPoison_TrapNova`（0x6FCDE8B0）。
因此 TrapPoison 显式继承已移植的共用决策 TrapNova，以本行 MonStats.Skill1/Sk1mode
施放毒云技能；不是继承 GenericMonster 或用普通攻击代替技能。

覆盖目标距离上界、技能次数、施放与等待交替、无目标/超距/耗尽死亡及禁止掉落。
共用决策的缺技能不消耗次数、施放失败消耗次数由 TrapNova 测试验证；
本项额外验证真实毒云行解析、不同于新星的技能、三难度生命周期、距离边界、无目标/零次数和施放失败。

固定 `source-base-sha.txt` 的 git archive 到 `build/trap-poison-source`，覆盖本项两个新 AI Java 文件及新增技能集成测试。
AI.java 在 archive 版本追加本项映射，排除共享工作区其他任务的战斗归属改动；
独立映射补丁保存在 `ai-mapping.patch`，隔离源码 SHA256 记录在 `source-sha256.txt`。
ServerSkillSystem.java 仅应用 `skill-dependency.patch` 中的本项技能回调。

运行（PowerShell，仓库根目录）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./gradlew.bat -p build/trap-poison-source --project-cache-dir build/trap-poison-cache -I "$PWD/docs/ai-arena/20261016-trap-poison/isolated.init.gradle" :core:cleanTest :core:test --tests '*TrapPoisonAiIntegrationTest' --tests '*TrapNovaAiIntegrationTest' --tests '*TrapMissileAiIntegrationTest' --tests '*TrapMeleeAiIntegrationTest' --tests '*Act1NativeAiIntegrationTest' --tests '*PrimePoisonNovaIntegrationTest' --tests '*SorceressNovaIntegrationTest' --console=plain --max-workers=1
```

Fixture 使用真实 1.10f 表、模拟寻路器，验证 AI 决策和动作生命周期。
本次无图形客户端或自然地图毒云命中验收。

## 随 AI 补齐的小依赖

实表 Skill1 为 PrimePoisonNova，SrvDo099、A1、SrvMissileA=primepoisoncloud。
原通用路径只会创建一枚飞弹；原版 `SkillMonst.cpp:1371` 的 SrvDo099 创建八个偶数方向，
Calc2>1 时再以 Calc2 步长选择奇数方向，两组分别使用 Missiles.Param1/Param2 的精确速度。
本次新增专用回调并复用现有毒云移动/停靠机制，保留 Calc1 循环数、技能等级和来源。
速度按服务端现有表单位约定，将原版精确值 Param<<6 换算为 Param/4（默认原版为 Vel<<8）。
新增生产路径测试核对两组数量、顺序、目标坐标、速度、来源、等级和循环数。

`decision-tests.log` 是补技能依赖前的 49 项 AI 决策通过记录。
`skill-first.log/xml` 保留首轮技能测试的等级预期错误：测试多加了难度奖励；
当前服务器从本行 Sk1lvl 读取等级，修正预期后重跑联合套件。该轮未作生产行为修补。

最终结果：71 项通过，0 失败、0 错误（TrapPoison 5、PrimePoisonNova 1、TrapNova 7、
TrapMissile 6、TrapMelee 5、Act1Native 26、SorceressNova 21）。
本项小依赖已补齐，无新增大依赖；累计完成 4/37，剩余 33 类。
