# 德鲁伊熔岩巨球：原版 1.10F 后台核对

日期：2026-11-20。技能 ID 229，一级、无协同，CXMolten 独立德鲁伊存档，地图种子 127897359，鲜血荒地，正 X 方向施法。

## 结论与范围

当前 Riiablo 的出土→滚动结构和 5/100 tick 寿命已存在，但与这份原版的移动速度、滚动尾迹、到期爆炸火场和碎石飞行实现有明确差异。前半部分保留修复前的采集与差异基线；本轮已按下述证据修改生产实现，修复与回归结果见文末。

原版全部运行在未显示的私人 Windows desktop；没有 SwitchDesktop、SetForegroundWindow、全局输入或强制启动绘图。每次记录输入桌面、分辨率与前台 HWND，并确认本轮创建的游戏退出、调试器脱离、补丁恢复。复用管理员 broker 24896，没有再次 UAC 授权。

证据来自自有 Win32 调试器、MPQ/DCC 解析和 Capstone 静态反汇编。当前会话没有连接 REA MCP；本文不把这些本地记录称为 REA Evidence。

## 可复用证据

- 第一轮 `20261120-145507-55401485`：仅 452–456 导弹，667 对精灵调用/返回，659 次 DCC 选择。确认需要扩大采集到滚动火焰的其他资源。
- 主采集 `20261120-145902-b4b8d5b0`：1714 对精灵调用/返回，1703 次选择，保留 240 个连续服务端更新；fixture 为 `core/src/test/resources/original/molten-boulder-1.10f.json`。11 次没有命中 DCC 选择断点的冷缓存绘图保留原始日志，不纳入方向/帧 oracle。
- 到期入口探针 `20261120-151130-d744dcda`：独立观测 D2Game SrvHit47 入口/返回，确认 NULL 目标、CurrentFrame=0，返回 1；客户端/服务端单位数量与主采集一致。
- 原始日志和当时的 collector exe/source、controller、runner、fixture 生成器均保存在 `tools/original-observer/broker/20261116-014209-3a0c898c/runs/<run>/orchestrator/worker/`，`provenance/` 是冻结副本。
- 原版资源：`tools/original-observer/molten-boulder-assets-1.10f.json`。
- 实际生产回放：`tools/original-observer/molten-boulder-comparison-1.10f.json`，由 `MoltenBoulderOriginalAnalysis` 调用生产 ServerSkillSystem、ServerEntityFactory、MissileCollisionSystem、ReferenceElementalPresentationSystem、MissileLoader、AnimationStepper 生成；读取实际 DCC，省略 GPU 上传。
- 汇总：`tools/original-observer/molten-boulder-analysis-2026-11-20.json`，包含精灵帧不一致的原版采样索引、到期入口/返回记录。
- 静态依据：`molten-boulder-static-client-1.10f.txt`、`molten-boulder-static-server-1.10f.txt`，可用相应 `inspect_molten_boulder_*.py` 重新生成。

主采集和到期探针均由常驻 broker 自动完成进入地图、施法、采样与退出。第三种 job 的首次尝试 `20261120-151007-22666661` 因入口签名字节写成 83EC 而在施法前拒绝运行，已确认 detach/cleanup；核对实际 81EC 字节后重试通过。没有把失败轮次当成功证据。

## 1. 出土与滚动：寿命对，速度和生成位置不对

原版资源：453 `moltenboulderemerge`，8 方向×6 帧，Range=5、Vel=3；452 `moltenboulder`，8 方向×10 帧，Range=100、Vel=4。

实际原版出土存在 5 个服务端 tick，随后创建一颗滚动巨球，滚动连续存在 100 tick。Riiablo 也是 5→100，基本阶段顺序一致。

但 Vel 不能直接解释成“世界格/秒”。原版正 X 方向滚动每 tick 移动 12288/65536=0.1875 subtile，即 4.6875 subtile/s；Riiablo 的工厂直接使用 Vel=4，实际速度只有 4 subtile/s，低约 **14.67%**。出土原版为 9216/65536/tick=3.515625 subtile/s，Riiablo 为 3，存在相同换算问题。

原版 rolling 的初始精确位置为 (4523.5,5642.5)，Riiablo 为 (4522.6006,5642)。这里同时涉及坐标中心表示和 SrvHit48 创建子弹时的整数格重定位，不能简单对所有技能统一加 0.5。修复时应按同一坐标约定比较相对位移，并保留原版的子弹出生格规则。

定位：`ServerEntityFactory.createMissile` 默认分支直接 `setLength(missile.Vel)`；`MissileCollisionSystem.spawnMoltenBoulderChild` 原样传递浮点终点。现有 Frozen Orb/Bone Spear/Firestorm 的专用速度路径不能自动覆盖此技能。

## 2. 滚动尾迹：资源、间距、寿命均不同

原版客户端沿滚动路径生成 19 段火焰，本轮组合是 firewall=8、firesmall=7、firemedium=4，每段 TotalFrames=90，即 **3.6 秒**。主采集与到期入口探针重复得到相同组合，未声称所有随机流/方向都必定是这些数量。

服务端另有 19 段不可混淆的 `moltenboulderfirepath`，寿命 37 tick。服务端伤害单位与客户端火焰资源不是同一类实体。

原版 D2Client CltDo47（RVA 94C30）处理后跳到 CltDo6（90820）。共享函数在三种 `CltSubMissile` 中使用原生随机流选一种。资源表对应 firewall/firesmall/firemedium；这与运行时的 69/104/105 单位一致。

Riiablo 当前 `ReferenceElementalPresentationSystem` 使用 APK 参考策略：每 `30/230` 秒生成单一 `moltenboulderfirepath`，2 秒寿命。本次实际生产回放产生 **31 段滚动尾迹**，到期另加 1 段中心火焰。结果是火焰更密、消失更早，尺寸/外形组合也不对。

动画还有独立差异：原版 CltDo5 火焰循环采到 12..35；当前参考效果使用包含 36 的循环，并加入独立随机相位。下一轮应为此技能的客户端尾迹使用原生增长/循环时钟，不能全局改动所有 CltDo5，也不能把客户端 90 tick 写回服务端伤害寿命。

定位：`ReferenceElementalPresentationSystem.process` 的 moltenboulder 分支、`groundFirePhase`、`configure/frameAt`。

## 3. 到期爆炸：缺少一整片火场，也涉及服务端逻辑

原版自然到期时：

1. 创建一个 454 `moltenboulderexplode` 载体（TotalFrames=13；其 CelFile 为 null，不能把它当作可见的“大爆炸 DCC”）。
2. 尝试生成碎石。
3. 按 18 组固定偏移创建客户端 `moltenboulderfirepath`，每个 37 tick（1.48 秒）。
4. 服务端另生成相同 18 组偏移的伤害单位，寿命 **7 tick**，由技能爆炸半径作为 lifetime override 传入。

Riiablo 到期客户端只是一个载体、8 个径向碎片和 **1 个中心火焰（2 秒）**；已有的服务端 18 格扇出只在碰撞大型怪物时调用，自然到期没有进入这条服务端分支。本次生产回放只有沿路的 15 个伤害火焰，没有任何 7 tick 的到期火场。

这不是仅凭观察画面推测：到期探针记录 game_frame=590，target=0，current_frame=0，missile_id=2，SrvHit47 返回 1；同一轮随后保留 18 个服务端短寿命火焰。

**D2MOO 源码在此处与 1.10F 二进制不同。** 源码 `MISSMODE_SrvHit47_MoltenBoulder` 把 `!pUnit` 和非怪物一起返回 2；当前原版 DLL 的 RVA 2DB08 则在 target==NULL 时直接跳到 2DB88，进入区域结算/子弹扇出。不能仅按该源码取消原版到期爆炸。

定位：服务端 `MissileCollisionSystem.triggerNativeNullHit` 没有 rolling SrvHit47 分支，`burstMoltenBoulder` 仅在 LARGE 碰撞路径调用；客户端 `ReferenceElementalPresentationSystem.impact` 只生成单个中心火焰。

未采集受控怪物的逐次扣血，因此尚不能报告区域伤害数值、命中、抗性或击退已经和原版一致。已证实的是缺少“自然到期调用与 18 个伤害子单位”这一步。

## 4. 碎石：不是固定八向，也不能改成固定九个

原版 CltHit52 的共享函数 RVA 989A0 使用 cHitPar1=10 次候选落点，随机选择目标并检查距离，过近时不生成。本轮实际生成 9 个，寿命分别为 14、19、22、24、28、32、36、38、39 tick。飞行方向和到达时间有变化。

当前 Riiablo 固定 8 个、均匀八向、每个 0.5 秒，最后偏移为固定 70×45 像素椭圆。因此爆炸显得过分规整，碎石没有原版的散落节奏。下一轮应复现候选采样/距离过滤和原版飞行时钟，不应把本批观测数 9 写成新的常量。

## 5. 滚动动画：帧时钟发生浮点漂移

原版实际 DCC 选择的 rolling frame 等于 native_age mod 10。按同一 native_age 与生产世界回放对齐，共 80 个有效原版样本，64 个与当前帧不一致，常见为落后 1 帧。例如 age6：原版6、Riiablo5；age10：原版0、Riiablo9。

资源都是 10 帧，并非少了 DCC 帧。问题在普通 `Animation.update(float)` 的累计浮点时间；该技能尚未走已有的整数 native tick 方案。这里应只给熔岩巨球阶段设定按 age 求帧/整数光标，避免改动此前已经核对过的其他技能。

## 验证与后续修复顺序

本轮 x86 采集器编译成功，Python 语法校验通过，实际生产分析任务成功。JUnit 共 24 项通过：MoltenBoulderLifecycleTest 3 项、OriginalMoltenBoulderFixtureTest 1 项、ReferenceElementalPresentationTest 20 项。旧测试通过说明内部契约仍成立，**不代表已具备原版一致性**。

建议接下来仅修改该技能，顺序为：

1. 修复出土/滚动速度换算、子弹出生格和按 native tick 播放的滚动动画。
2. 分开服务端火焰与客户端三种尾迹，恢复跨格发射、90 tick 视觉寿命及原生火焰时钟。
3. 补齐 NULL 到期的服务端爆炸分支、18 格客户端火场，再实现碎石候选落点/过滤与飞行寿命。
4. 重跑本次固定场景；随后新增斜向施法、小/中/大型怪物、墙体碰撞与多等级/协同的受控伤害核对。

这些步骤不以 GPU 像素对齐为门槛。当前没有前台截图验证，也没有验证所有方向、全技能等级、光照/混色/最终像素。


## 后续修复：生产实现

本轮使用以上冻结的 1.10F 证据完成修改，不再启动原版，不涉及前台窗口或 UAC。

- 出土和滚动接入原生方向表、75% 速度换算和 16.16 步进；出土结束按原版整数格重新创建滚动单位。没有全局增加 0.5 坐标偏移。滚动每 tick 为 0.1875 格，出土为 0.140625 格。
- 出土/滚动动画以 native age 求帧，冷加载也直接定位到当前帧；网络副本使用独立展示时钟。60 Hz 展示更新不会变成 60 Hz 技能动画。
- 滚动尾迹由跨格触发，从 firewall/firesmall/firemedium 三行选择，视觉寿命为 90 tick。服务端沿路伤害单位仍为 moltenboulderfirepath、37 tick。
- 到期事件发生在常规展示更新之前，因此先补发最后一个尚未处理的跨格回调，再生成爆炸效果。这修复了第 99 tick 最后一格火焰遗漏及其后随机流错位。
- 原版 NULL 到期分支现在也会执行一次区域伤害与 18 格火场；服务端子单位为 7 tick，客户端对应 18 格火焰为 37 tick。碰撞大型怪物的原有单次共享伤害包策略保留；NULL 目标不会访问负实体 ID。
- 爆炸载体按资源 Range=13 tick 存在，继续作为不可见载体。碎石按 cHitPar1=10 次候选采样，坐标范围 ±20，距离不大于 3 则丢弃；使用 cHitPar2=15 速度及原生距离公式计算飞行时间。没有固定八向、固定九个或固定 0.5 秒。
- 碎石沿固定精度方向速度飞行，位置参与正常排序与裁剪；动画在较长飞行的前段保持首帧，到最后 16 tick 再播放。短于 16 tick 的飞行也从首帧开始。碎石的创建标志 `0x524` 包含 `0x100`：D2Client RVA `9E89D..9E8D7` 会将展示朝向设为 `(targetX + targetY) & 63`，再归约到 DCC 方向；物理速度不随之旋转。实现已恢复这条规则，不能用速度角度代替碎石朝向。新静态记录为 `molten-boulder-static-create-1.10f.txt`，含模块 SHA-256；这是本地 Capstone 证据。
- 火焰以 12..35 为中段循环，按原生 CltDo5 生长、随机进入循环与退场，不再叠加旧 APK 的随机相位或额外透明度衰减。新行为只作用于本技能的视觉效果。

### 验证入口与产物

- 新增 `MoltenBoulderOriginalParityTest`：生产施法回放的移动、滚动 DCC 帧、尾迹/火场数量与寿命；原版全部火焰和碎石的动画光标；DCC 方向与碎石位移。
- `MoltenBoulderLifecycleTest` 新增无直接碰撞的自然到期区域伤害验证，并检查 18 个短寿命子单位不会递归爆炸。
- `ReferenceElementalPresentationTest` 将原 APK 熔岩巨球断言替换为原生跨格尾迹、有限火场、变时长碎石、冷 DCC 发布与副本时钟验证。
- 生产回放保存为 `tools/original-observer/molten-boulder-comparison-after-fix-1.10f.json`；修复前的 `molten-boulder-comparison-1.10f.json` 保留。
- 汇总入口：`analyze_molten_boulder.py <fixture> <after-fix-replay> <output>`；分析器按输入实际生成检查结果，已删除写死的旧差异结论。

最终回归：**74 项 JUnit 全部通过，0 失败、0 错误、0 跳过**，覆盖熔岩巨球、ReferenceElementalPresentation、火焰风暴、毒标和瘟疫标枪。日志为 `tools/original-observer/molten-fix-final-tests.log`。此前的运行遇到 JVM 原生内存不足；限制 heap/编译器并通过 class include 限定测试输入后，完整回归成功。未把失败或被中断的运行计入通过数量。

## 后台多方向采集准备与接收器修复

新增固定任务 `molten-boulder-directions-private-desktop`，复用未显示的独立桌面和现有采集器，依次请求八个方向。仅在采集器明确报告没有 12 格通路且确认调试清理完成时，记录该方向未采集；其他失败仍终止批次。导出器可用 `--capture capture-dir-N` 选择单个方向。此任务尚未运行，不代表已经完成八方向原版验证。

原常驻接收器在替换心跳状态文件时因 Windows 拒绝访问而退出。修复后，状态写入遇到临时权限错误会保留接收器并在下一轮重试，失败的临时文件会清理；任务结果文件写入失败仍明确报错。两项回归测试覆盖状态锁定后的恢复和任务结果错误不被吞掉。

恢复接收器后未启动新的原版采集。随后检查发现 Windows PcaSvc 进程约占用 1.35 GB，而接收器工作集约 18 MB；当时没有原版或 Java 测试进程。兼容性日志包含暗黑启动器以及其他应用，不能仅据这些记录认定内存增长由本次调试造成。

固定场景修复后：速度 4.6875 格/秒；80 对滚动精灵帧差异从 64 降为 **0**；三种沿路视觉火焰共 19 段、各 90 tick；服务端沿路 19×37 tick、到期 18×7 tick；客户端到期 18×37 tick；碎石本轮为 9 个，寿命分别为 14、19、22、24、28、32、36、38、39 tick，候选落点产生的位移和展示方向也匹配。

回调回归另外核对 **2347 个火焰动画光标、242 个碎石动画光标、200 个巨球/碎石 DCC 方向样本**。修复后 JSON 汇总为 `tools/original-observer/molten-boulder-fix-validation-2026-11-20.json`，包含 10 项场景检查、JUnit 分类及源码 SHA-256。

可复用运行环境为 JDK 17、Gradle 7.3、workspace 中的 `.gradle/molten-boulder-runtime` 缓存，`D2_HOME`/`D2_TEST` 指向原版资源目录。使用 `asset-probe.init.gradle`，设置 `-PprobeTestHeap=512m -PprobeForkEvery=0`，并用 `-PprobeIncludes=**/*MoltenBoulder*Test.class,**/ReferenceElementalPresentationTest.class,**/*Firestorm*Test.class,**/*PoisonJavelin*Test.class,**/*PlagueJavelin*Test.class` 限定测试文件；`:core:test` 验证，`:core:moltenBoulderOriginalAnalysis` 重新生成生产回放。全部为 headless Java/MPQ 分析。

### 仍需区分的范围

这次证明的是记录场景中的步骤、运动、资源和时钟，并非所有场景或最终 GPU 像素一致。回放用原版 rolling 的初始客户端 seed 对齐尾迹选择和碎石候选；实际游戏的客户端单位分配 seed 尚未与原版全局分配器同步。每个火焰的 CltDo5 规则使用第 11 tick（随机进入循环前）的完整 low/high seed 单独验证。运行记录表明部分火焰的 seed 也会被其他客户端逻辑推进，不能只从出生 seed 推演其实际相位。这里未重建这些额外 RNG 使用者，因此不能把回调规则测试解释为实际运行中全部火焰随机相位一模一样。

新增到期伤害测试验证 Riiablo 不再漏结算及其一次性边界；尚未用原版受控怪物逐次扣血证明抗性、命中、击退、所有等级/协同数值一致。斜向、墙体和多种体型的原版新采集，以及最终 GPU 视觉观感，仍属于后续核对范围。
