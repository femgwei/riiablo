# 亚马逊瘟疫标枪：原版后台采集与实现差异

对象：Plague Javelin，skill 25。目标是定位并修正客户端展示中缺少或错误的步骤。下文原始差异是修复前快照；修复状态、验证范围及复现命令见文末“修复落地”。

## 原版采集

- 原版 1.10f：`G:\BaiduNetdiskDownload\Diablo II 1.10F`，固定 loader `-w -res800`。
- 批次 `20261120-104511-b36f705e`，沿用已授权常驻 broker 24896，无新增 UAC。
- 私人桌面 `riiablo_poison_2bfea5d89c3b`，112 次隔离检查；前后输入桌面均为 Default、前台窗口句柄均为 3480222、分辨率均为 1920×1080，没有切换桌面或全局输入。
- 独立测试角色 `CXPlague`，只学习一级瘟疫标枪，无协同；使用已校验的非魔法标枪。不覆盖用户角色。新存档通过原版正常加载。
- 用定向客户端消息施放一次，连续观察 240 个服务端更新。没有生成目标怪物，本轮观测的是**自然射程到期触发爆发**，不能声称已验证击中怪物的流程。
- 调试器已脱离、断点已恢复、本批次游戏已关闭，常驻 broker 保持可用。

原始证据位于 `tools/original-observer/broker/20261116-014209-3a0c898c/runs/20261120-104511-b36f705e/orchestrator/`，worker/provenance 保留当批原生程序与控制脚本。可重放导出为 `core/src/test/resources/original/plague-javelin-1.10f.json`，另有 MPQ 表行/资源头 `tools/original-observer/plague-javelin-assets-1.10f.json`。

## 原版实际执行的展示链路

投掷标枪 → 飞行中按整数格位移留下静止毒云 → 到期/命中回调产生扩散云 → 各云随机生成烟雾 → 毒云退出循环并消失，尚未到期的烟雾继续飘动。

本次实际单位：

- 服务端：1 标枪、23 扩散云。
- 客户端：1 标枪、13 静止拖尾云、23 扩散云、107 烟雾。
- 2,734 次精灵调用/返回配对；2,721 条实际 DCC 选择，其中标枪 12、拖尾云 408、扩散云 959、烟雾 1,342。缓存未命中的 13 次调用留在原始数据中，未计入选帧对照。

这些是一次施法的观测数，不是所有方向、等级或随机种子的固定数量。客户端与服务端的单位分配和更新异步，不能按同名 unit id 直接配对。

## 已定位的差异

### 1. 标枪速度偏低，爆发中心随之偏近

原版服务端初速度 `(73728,0)` fp16/tick，即 1.125 子格/tick、每秒 28.125；标枪寿命 15 tick。当前生产工厂走通用 `Vel=24` 每秒速度，得到 0.96 子格/tick，低约 14.67%。

从同一出生点 `(4522,5642)` 运行当前生产工厂/碰撞系统到自然到期：

- 原版扩散云出生中心 `(4539,5642)`。
- 当前 Riiablo 扩散云出生中心约 `(4536.3994,5642)`，x 近了 2.6006 子格。

这是飞行位移加原版整数格出生规则的累积差异，不应仅在特效上加位置补偿。

定位：`NativePoisonJavelinMotion.handles` 目前只识别 poisonjav，`ServerEntityFactory` 中 plaguejavelin 仍走通用速度和浮点位置分支。

### 2. 拖尾云位置错误，并缺少 PoisonSparks 层

原版 13 个拖尾云速度为零、寿命 60 tick，实际绘制 PoisonSparks，DCC 方向均为 4。当前 `processPoisonFlight` 在创建 plaguejavcloud 后将子云速度设成标枪速度；较早生成的云不断前移，原本应留在身后的拖尾趋向挤在标枪附近。

`MissileLoader` 还跳过所有 presentationOnly 的 plaguejavcloud 基础 DCC，只保留吐烟回调。因此这里同时存在“父云在错误的位置吐烟”和“少了一层实际存在的精灵”。这一规则是历史用户反馈后的兼容回退，不是原版机制；旧研究文件已经明确注明该权衡，本轮原版运行证据进一步证明了差异。

不能只删隐藏分支：需要同时改回静止、整数格出生、正确方向及独立时钟，再恢复精灵，否则可能再次出现先前反馈的重叠或短暂闪现。

### 3. 毒云动画没有复用已验证的原生循环

原版拖尾云和扩散云均在年龄 19 回到帧 10，按 `[10,19)` 循环，临近寿命结束时进入退出段。拖尾云寿命为 60 tick；扩散云则为 `60 + 3×9 = 87 tick`，不是在 60 tick 时一同消失。

当前 `NativePoisonJavelinPresentation` / `MissileLoader.initializePlayback` 的原生循环只覆盖 poisonjavcloud，plaguejavcloud 仍走通用 Animation.setSubLoop。通用播放先走完整套资源，然后进入中段，且使用浮点累计时钟；这与原版本批的循环位置和后段退出均不同。

离线逐 tick 驱动当前真实 DirectionResolver/AnimationStepper，并按原版实际资源选择逐条比较：拖尾云 408 条中 338 条帧号不同，扩散云 959 条中 897 条帧号不同。该诊断使用出生即加载的 DCC；为分析被隐藏的拖尾，强制挂上动画组件。它不是当前生产路径实际会画出拖尾的证明，也不是 GPU 像素比较。

### 4. 扩散方向没有传给精灵朝向

`createPoisonCloudChild` 以 Vector2.X 调用工厂，随后按环偏移重算 Velocity，却未更新 Angle。当前本地云朝向仍为 X；原版扩散云根据各自路径选方向。

原版实际选择中，959 条扩散云有 876 条与当前生产初始 Angle 对应的方向不一致。静止拖尾的原版方向是 4，当前遗留 X 则映射为 3。修复时应使用原生路径方向与 DCC 映射，不能简单把所有云统一转为方向 4。

烟雾的 1,342 条方向重建对照仍有 38 条未匹配；这部分使用截断后的速度反推朝向，可能落在原生方向量化边界，尚未定位到具体原因，不归咎于随机算法，也不将其计为已修复问题。烟雾帧号本次重放均匹配。

## 已一致的部分

当前服务端自然到期生成 23 个爆发云，与本次原版一致；23 个云的横纵 fp16 初速度多重集合全部一致，寿命均为 87 tick。

- 慢组轴向速度每 tick 0.046875 格。
- 快组轴向速度每 tick 0.09375 格。
- 其他方向保留原生分量截断，不能直接用归一化浮点圆替换。

因此无需重写已对上的环数量和两组初速度。上面的出生中心、朝向、动画和客户端独立调度仍需修复，初速度一致不能推导出完整画面或伤害一致。

本轮未修改 SCREEN 混合兼容策略。没有最终 GPU 证据，不依据旧 D2Glide 模式数字直接改当前索引 shader。

## 诊断和复现

新增 `PlagueJavelinOriginalAnalysis` 是诊断入口，不是把差异当作通过的测试。它运行当前生产工厂/碰撞/表现系统，输出模拟值及选帧差异。测试场景给出有效拥有者实体，不提供怪物或装备伤害属性；此次关注位置、速度和动画。

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat -I tools/original-observer/asset-probe.init.gradle :core:plagueJavelinOriginalAnalysis --offline --console=plain --no-daemon
& 'F:\3rd_src\emsdk\python\3.9.2-nuget_64bit\python.exe' tools/original-observer/analyze_plague_javelin.py
```

机器结果为 `tools/original-observer/plague-javelin-comparison-1.10f.json` 和 `plague-javelin-analysis-2026-11-20.json`，保存输入/诊断源码的 SHA-256。后续修复须先保存此基线，不能把修复后结果覆盖成本轮旧行为的证据。

后台再采集只提交固定 job：

```powershell
& 'F:\3rd_src\emsdk\python\3.9.2-nuget_64bit\python.exe' tools/original-observer/broker_client.py submit --op run_job --job plague-javelin-private-desktop
```

本轮扩展了既有毒标采集器，增加固定 skill 25 / CXPlague 角色选项；保留毒标原有参数和隔离方式，没有开放任意命令或前台启动模式。

## 建议修复顺序与边界

1. 标枪原生速度与 fp16 移动、爆发整数格出生中心。
2. 静止独立拖尾、原生毒云时钟、正确朝向，同时恢复 PoisonSparks 层。
3. 扩散云 Angle 与路径方向同步，再做客户端/服务端独立爆发时序核对。
4. 检查怪物命中、不同方向/等级、烟雾方向量化边界。

本轮没有验证命中伤害、毒抗、穿透、所有等级/方向或 1.14；一级无协同空投不能代表这些情形。初始随机分配顺序仍是现有移植策略，107 个烟雾不能作为每次施法的固定目标。

本轮 **51 项相关回归全部通过**（0 失败、0 错误、0 跳过），包含瘟疫标枪爆发环、伤害表、毒标枪客户端生命周期及原版选帧、烟雾随机回放、检查点兼容性和骨矛相邻回归。生产代码/测试代码编译、模块边界检查、Python 语法及 `git diff --check` 通过。结果独立保存为 `tools/original-observer/plague-javelin-validation-2026-11-20.json`。这些测试说明既有实现的回归状态，不否定本轮发现的原版差异。

## 修复落地（2026-11-20）

已将本节列出的修复应用到生产代码。本次完全离线使用前述原版数据，没有启动新的原版进程、切换窗口或申请 UAC。

- `NativePoisonJavelinMotion` 扩展到 plaguejavelin/plaguejavcloud，统一使用原生 fp16 路径及整数格出生。标枪速度恢复为 1.125 格/tick；15 tick 后爆发云出生在 `(4539,5642)`。
- `MissileImpactPresentationSystem` 让瘟疫标枪复用独立的静止拖尾链路：第一帧初始化路径，后续整数格变化产生云；本批数据重放产生 13 个、每个一级寿命 60 tick。恢复 PoisonSparks 基础层，拖尾不再随标枪前移。保留高等级原有的每级额外两次循环规则，高等级尚无动态原版证据。
- `NativePoisonJavelinPresentation` / `MissileLoader` 将 plaguejavcloud 纳入原生动画时钟，按年龄恢复引入段、中段 `[10,19)` 循环及退出段，处理冷加载和重复表现更新。移动爆发云保持 87 tick 寿命。
- `MissileCollisionSystem` 用爆发环的原始目标偏移设置 Angle，再由原生 64 方向映射选择 PoisonSparks 的 8 方向。避免用截断后的速度重新选择路径方向。网络副本不会携带 `poisonCloudTraveling`，因此以本地 presentationOnly 拖尾身份区分静止云与复制的移动云，防止网络副本全部被锁为方向 4。
- 保留现有 SCREEN 混合策略、23 云的环参数与伤害公式。此次没有增加检查点字段或修改网络包。

新增 `core/src/test/java/com/riiablo/presentation/PlagueJavelinOriginalTest.java`，直接核对保存的原版运行数据：

- 服务端 2,016 个状态（15 个标枪状态和 23×87 个扩散云状态），逐帧比较出生/消失、fp16 位置、速度与剩余寿命。
- 客户端 13 条静止拖尾的出生格、年龄和到期，经过真实工厂、碰撞和表现回调；并通过真实 MissileLoader 确认静止云能够发布 DCC。
- 2,721 条实际 DCC 选择，分别验证从出生连续播放、延迟加载、缺少服务端移动标记的网络副本；比较真实 MPQ 资源的帧号与方向。爆发云的 Angle 从生产工厂实际生成结果取得，不把原版预期方向传给实现。

### 更正修复前诊断中的烟雾差异

此前的 38 条烟雾方向差异来自诊断程序：出生年龄为 0 时尚未运行 DirectionResolver，就读取了动画默认方向 0。原版数据中有 50 条年龄 0 的烟雾，其中 12 条本来就是方向 0，其余恰为 38 条。修复诊断时在出生帧执行表现更新；新增回归对所有烟雾样本也直接核对方向，不再把这 38 条归为未定位的生产量化问题。原始基线 JSON 保留，不重写历史计数。

### 重放与证据

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat -I tools/original-observer/asset-probe.init.gradle :core:test --tests '*PlagueJavelinOriginalTest' :core:plagueJavelinOriginalAnalysis --offline --console=plain --no-daemon
```

诊断现在使用实际生产环朝向，并执行出生帧方向解析；默认输出为 `tools/original-observer/plague-javelin-comparison-after-fix-1.10f.json`，保留修复前 comparison/analysis 文件。旧的 `analyze_plague_javelin.py` 用于历史基线汇总，不用于证明当前修复。

完整相关回归为 **64 项通过，0 失败、0 错误、0 跳过**，包含毒标枪、瘟疫标枪伤害表、陷阱毒云、检查点兼容、网络生命周期和骨矛。修复验证文件 `tools/original-observer/plague-javelin-fix-validation-2026-11-20.json` 记录各批次 XML 摘要、SHA-256 及原版 fixture/生产源码指纹。

这些结果验证了本批一级自然到期施法的路径、生命周期、DCC 选帧与朝向，以及模拟网络副本的展示行为。没有 GPU 截图验收、真实网络时延/丢包测试、怪物命中原版采集或高等级/所有投掷方向的原版覆盖；也没有把一次施法的烟雾随机分配顺序认定为所有客户端必须相同。
