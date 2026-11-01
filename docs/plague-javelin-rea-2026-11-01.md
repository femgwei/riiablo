# 瘟疫标枪客户端效果对齐（2026-11-01）

## 最新状态：a9a3f4d19 的实机回退修正

用户实测该提交导致毒标缺少扩散、瘟疫标枪烟雾只闪现少量帧。此前 68 项无 GL 测试通过，
只能说明所测的模拟逻辑自洽，不能证明画面正确。以下当前策略替代本文后面旧批次的相关结论：

- 恢复上一版已由用户验证较接近原版的毒类 Screen 混合策略，以及瘟疫标枪本地拖尾载体的运动。
  根标枪仍显示，先前隐藏的重复载体基础 DCC 仍隐藏。原版 flags=0x8005 不代表速度继承的
  静态结论不变；恢复运动是当前 ECS 表现层的兼容回退，不伪称原版创建逻辑就是如此。
  D2Glide 的 draw-mode 证据也不足以证明现代索引 shader 可以直接替换混合模式而视觉等价。
- 表现时钟重新在本地以 25 Hz 持续推进；网络 nativeFrame 仅用于追帧，不再是唯一驱动源。
  重复/暂不更新的服务器时钟不能冻结烟雾。重复包不回退 RNG，但新的本地模拟 tick 会正常抽样。
  历史运动载体按“先吐烟再移动”的相位追帧，保留稀疏快照补段和烟雾年龄处理。
- 修复旧地图坐标转 uint32 的错误：无原生精度的负坐标保留有符号世界位置，以量化步长推进；
  仅显式持有原生 raw precision 的 Position 走 unsigned 原生适配。否则烟雾跨过零点可跳到
  65535 附近而消失。服务端毒云共用路径也采用相同区分，不改毒伤、抗性及碰撞回调。
- 出生表现 seed 不再等于 gameSeed/实体 ID 的固定派生值。工厂有独立 D2Seed 分配流，
  每次出生抽取新的 low seed，并同步给观察端；实体槽回收后不会重放同一套随机烟雾。
  它不消耗或修改 damage RNG。检查点恢复已有弹体不抽取新出生 seed，使用原来保存的值。
  这仍非完整原版分配调度；跨新工厂的检查点恢复不承诺未来新生烟雾与旧工厂逐粒一致。

本轮 JDK 17 + 真实 1.10f MPQ 定向回归：16 类 / 71 项，失败、错误、跳过均为 0，
编译和模块边界检查通过。新增跨负坐标、两种云在零/停滞网络帧号下继续吐烟、128 次出生
种子不重复的覆盖；更新移动载体和 Screen 策略回归，100 帧历史与连续更新的一致性仍通过。
没有 GPU/原版运行时逐帧验收，不能据此宣称用户反馈已在实机完全消失。

## 本批修改

按 D2MOO 服务端源码、原版 1.10f MPQ 和 REA 的 D2Client.dll 静态证据修正：

- SrvHit2 的 8 个主环 + 15 个交错环子云统一出生在命中中心，以原偏移为目标向外移动，
  不再直接出生在偏移点。**根据实机反馈纠正上一批判断：偏移只决定初始方向，不是停止终点。**
  删除到点钳制和停车，子云持续沿原方向移动至寿命耗尽。
- 创建时设置 `Range + loops * (SubStop - SubStart)` 时钟；原表为 `60 + 3 * 9 = 87` 帧。
  从出生就配置移动毒伤区域，移动消耗同一寿命。普通 Poison Javelin / 尸体毒云保留共享生命周期。
- 主/交错环的原生速度是 `(Param << 7) * 75 / 100`，再按路径方向长度 4096、16.16 坐标
  转换到 ECS 每秒速度。原表 Param=2/4，对应每帧 0.046875/0.09375 子格，
  每秒 1.171875/2.34375 子格；不再用 `Param * 0.75` 冒充每秒速度。
  第 86 帧两组轴向半径为 4.03125/8.0625 子格，第 87 次更新删除；旧逻辑在约 2～2.8 格处停住。
  此处是云团中心距离，不含云团碰撞体积或烟雾图片边缘。
- CltDo4 的烟雾出生点继承父云位置，随机的是绝对目标坐标，不是出生点像素抖动。
  每轴 `d = rollLimited(2*r) - r; target = parentIntegerCoordinate + d + sign(d)*r`，
  其中零走正分支。原表 r=6，偏移为 `[-12,-7]` 或 `[6,11]` 世界子格，
  不再使用 `[-6,6]/16`。速度方向取出生点到目标的原生路径 LUT，静止父云不再只向 +X 吐烟。
- CltDo4 使用两字种子：`next = uint32(low) * 0x6AC690C5 + uint32(high)`；
  有界取值用低 32 位取模 / 二次幂掩码。正 interval（包括 1）消耗一次机会抽样；
  **剩余帧**为零或 interval<=0 时才跳过机会抽样。elapsed=0 不是“必定首帧生成”。
- `poisonpuff` 保留 Vel=1 / Range=25 的帧寿命，不再清零寿命后依赖动画结束；
  删除无原版依据的实体/种子哈希起始动画相位，遵循 RandStart=0。
- CltDo3 拖尾改为整数路径格变化时生成；CltDo3/4 与烟雾的时钟不再等待 DCC。
  其他短命中动画仍等待自己的资源加载，不改变此前 FingerMage 的第 21 次更新到期规则。

没有改毒伤公式、抗性和减伤。本轮删除毒类强制 Screen 的规则，依据见文末。
新扩散路径及统一寿命会改变云团在何处、何时覆盖目标，这是本批修复的预期影响。

## 当前架构的明确选择

原客户端 CltHit2 也会生成同样的环，但 Riiablo 已由服务端生成并同步真实子云
（原表 `plaguejavcloud.ClientSend=1`）。当 CltHit2/SrvHit2 的第一子云名称一致时，
客户端命中事件只消费音效，不再额外创建一团中央云；烟雾继续由每个真实子云的 CltDo4 生成。
这是适配当前权威同步架构的去重，不是声称恢复了原客户端预测时序。

客户端烟雾另存 `clientSeed`，优先从同步的不可变出生 `presentationSeed/666` 初始化；
旧包没有出生种子时才复制现有完整 nativeSeed，或回退到 `rngState/666`。
重复相同快照不回退随机流，不修改服务端 nativeSeed / rngState。FlatBuffers 尾部追加 seed / known，
known 区分旧包和合法零种子。权威检查点升级为版本 2 保存出生种子，并兼容读取版本 1。
clientSeed、clientPathX/Y、clientSnapshotKnown、clientSampleFrame/Position 均为本地表现缓存，
实体回收及权威快照恢复时清空，不进入服务端检查点。
纠正上版文档：既有 clientFrame / clientFlightInitialized 实际已在版本 1 中序列化，不能统称瞬态。
服务端快照不保证精确恢复烟雾随机流或拖尾观察格。
同一出生种子和相同路径样本可在不同本地实体 ID 下复现烟雾；不保证与原版的初始种子一致。
当前服务器仍用 NativeRng.forUnit 分配出生种子，而非完整移植 SUNIT_InitSeed 的全局分配调度。
D2MOO `source/D2Game/src/UNIT/SUnit.cpp:406-423` 的 SUNIT_InitSeed 先初始化 seed，
再从父/游戏种子抽取 dwInitSeed，以 low=dwInitSeed / high=666 初始化；本轮只同步现有出生 seed。
CltDo3 本地子云用父出生种子和生成帧稳定派生，属于当前同步架构的分配策略，不是原版调度证据。
未审计的 CltDo8/49 继续使用旧 fallback，没有借此批顺带改写。

CltDo3 的原版门禁是动态路径 flag 0x8。当前浮点 ECS / 网络副本没有暴露该标记，
本批以当前整数格与上次观察格（首次为 start 的格）比较实现对应门禁。
网络副本按已接收的 nativeFrame 补齐 25 Hz 回调，每帧最多生成一团载体，而非每穿过一格生成一团。
常速段用前后位置线性插值，首包按当前速度倒推，并落到 16.16 网格以避免插值尾差改变门禁。
历史子云/烟雾按年龄推进，过期烟雾删除，冷 DCC 装载也使用已过年龄；同一快照不重复抽样。
这解决稀疏快照断段，不是恢复网络未传输的真实路径低位，也不覆盖碰撞/变速前的未知历史。

## 本轮纠正：CltDo3 载体不继承标枪速度

此前将 flags=0x8005 解释为速度继承不成立。复核相同 SHA-256 的 DLL：
`0x6FB302FB..0x6FB30306` 清零整个创建结构，`0x6FB3030C` 设置 flags=5；
Range override 存在时改为 0x8005。D2MissileStrc 的 nVelocity 位于 +0x28，helper 没有写它。
通用创建 `0x6FB3E2EE` 的 flags&4 分支直接读取这个零值，0x8000 则控制 Range override。
D2MOO 的同名 CreatePlagueJavelin_PoisonJavelin_PoisonTrapHitSubmissiles 同样没有赋值 nVelocity。
因此拖尾载体改回静止，移动的是随后生成的 poisonpuff；根 plaguejavelin DCC 保持显示，
已针对用户确认的多余载体基础 DCC 的隐藏规则本轮保留，未声称该隐藏规则已获逐像素原版证据。

命中环和 smoke 的方向复用 D2PathStep 127 比值量化 / 128 项方向 LUT，
逐分量 `(direction * nativeVelocity) >> 8` 后保存 16.16 步长，负方向同样使用算术右移。
毒云运动累加 raw precision，不再每帧 float 积分。PrimePoisonNova 的既有速度适配没有改写。

## 原版静态证据与可追溯位置

目标：`G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Client.dll`，x86 PE32，
ImageBase `0x6FAA0000`，SHA-256：
`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。

- CltDo 表 `0x6FB91260`：slot 3 → `0x6FB303E0`，slot 4 → `0x6FB30690`。
- CltHit 表 `0x6FB913D0`：slot 2 → `0x6FB36350`。
- `0x6FB302E0`：飞行子云 helper，调用 D2Common #10234 检查路径 flag 0x8。
- `0x6FB30470`：烟雾 helper，读取 #11124 剩余帧、两字 RNG、flags=0x20 和随机目标。
- `0x6FB35FD0`：命中环 helper；`0x6FB36350` 读取 CltHitSubMissile 和 cHitPar1/2/3。
- `0x6FB3E070`：通用客户端创建；`0x6FB3E0BD` 分支支持继承出生点，
  `0x6FB3E2B8` 处理 flags=0x20 的绝对目标。巨大函数的反编译局部变量不可直接当结构定义，
  此处以汇编和创建结构字段偏移互证。
- D2MOO `source/D2Game/src/MISSILES/MissMode.cpp`：
  `MISSMODE_CreatePoisonCloudHitSubmissiles`（D2Game `0x6FC568F0`）。
  `Missiles.cpp` 的创建分支在 flags&8 且 SubLoop 时延长 Range；精确速度 flags&0x10
  避免再次左移 8 位，但仍经过统一的 `75 * velocity / 100`。
- 扩散范围的决定性证据：`source/D2Common/src/Path/Step.cpp` 的
  `sub_6FDACEC0`（1.10f `0x6FDACEC0`）对 `PATHTYPE_MISSILE` 设置 `v40=0`，
  跳过普通单位的目标点步长钳制及到点路径索引递增。`Path.cpp` 创建 UNIT_MISSILE 时
  选择该路径类型；`PathMisc.cpp` 的 `D2Common_10226` 也不为该类型逐帧重新朝目标转向。
  上版只核对子云创建参数，没有核对该路径类型的推进语义，因而误加了停车逻辑。
- `PATH_GetDirectionVector`（Step.cpp，1.10f `0x6FDAC5E0`）方向 LUT 长度约 4096；
  `PATH_ComputeVelocityAndDirectionVectorsToNextPoint`（PathMisc.cpp，`0x6FDAC790`）
  使用 `(direction * velocity) >> 8` 写入 16.16 步长，因此轴向每帧距离为 velocity/4096。
  Step.cpp 也给出 flag 0x8 的整数坐标变化语义。
- 原版 MPQ 的原始 `missiles.txt` 与加载后的表互证：
  `plaguejavelin(43)` → `plaguejavcloud(221, PoisonSparks)` →
  `poisonpuff(141, PoisonSmokePuff)`；CltParam=24/1/6，命中参数=1/2/3。

REA 6.8.0 / Ghidra 12.1.4。CltDo3 / CltHit2 自动函数边界不完整，上一轮使用 REA
read_bytes 与已安装 objdump 的精确地址范围解码，没有把反编译失败当成原版没有该逻辑。
本批复用已经导出的证据，不重复冷启动或运行原版 DLL。

关键 Evidence：

- 回调表：`ev_8594bfdfb3888e6ec9971a0364cdf198ec4f5184ecc8a156bf18ed226e3ca3d3`
- CltDo3：`ev_fc9bf9b926092b52a9339ae5981dc9da3f27df37b97a4ea850749786db96548b`
- 烟雾字节：`ev_b46691d6eb7e085f0f9a895f93368116ca68af590ac9fbe29e2e423358a93be3`
- 烟雾伪代码：`ev_a23b1e43f912a20d75d3c044ba883f07d899ce583572478534792a9a4bb2f2f4`
- 命中环：`ev_8359f16009163462fc9b61d96753eeefff3a33bb42fe26c80ae039e5747c1844`
- 出生点：`ev_d1c4b709587005006c73f4080e86b5317ad59b298ca52f20f7593b1989c4ade7`
- 绝对目标：`ev_efb3f0893f2a8feece18633f4391eec782923df7c38e0b6246e27abf04625938`

完整本机导出：`.gradle/rea-plaguejavelin-20261101-evidence.json`，19 条记录，
是忽略的生成文件，不随 Git 推送。以上均为静态观察；没有原版运行时逐帧画面证据。

## 验证与另一台电脑复验

JDK 17，真实 1.10f MPQ：

```powershell
.\gradlew.bat :core:plagueJavelinRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --console=plain
```

专用 source set 编译本批与相邻回归，默认选择毒云相关的 3 个 Amazon ECS 测试、
两项毒枪逐级伤害 golden、全部选入的表现测试及 FingerMage 服务端回归，
并加入 PrimePoisonNova 共享路径和 MissileRuntimeCheckpoint 字段策略回归。
此前定向回归 15 类 / 55 项全部通过；本轮新增回归结果见文末。
不会修改默认 `:core:test` 的测试范围。报告位于
`core/build/test-results/plagueJavelinRegression`；本机用独立输出目录
`.gradle/plaguejavelin-rea-validation` 避免与其他聊天的地图构建相互覆盖。

扩大到完整 AmazonSkillSpecializationTest 的入口是在同一命令追加
`-PplagueExtendedRegression`。本次扩大回归 15 类 / 93 项：92 通过，1 失败，零错误 / 跳过。
唯一失败是既有 `explodingArrowCreatesAuthoritativeAreaSubMissile` 的爆炸半径伤害断言。
将提交 `e874b59c0` 的原 MissileCollisionSystem 独立编译，并在测试 classpath 中覆盖本批版本，
同一断言依旧失败；该测试不运行本批表现系统。修改前服务端源码覆盖实验保存在本机忽略的
`.gradle/plague-server-baseline*`，本批不修改或排除默认套件中的该测试。
默认全 core 测试仍有此前 Client/GameScreen 迁移遗留的 UI 测试编译阻碍，不宣称全套通过。

新增生命周期测试运行生产系统和真实 ECS 子实体，独立 uint64 oracle 验证机会抽样、
两轴目标和种子隔离，并覆盖冷/热 DCC、25 帧寿命、整数格拖尾门禁、中央云去重和对象池清理。
服务端测试验证 23 云同源出生、两组原生速度、86 次连续移动及统一 87 帧到期，
独立常量逐帧检查两组轴向位置，第 60 帧外环达到 5.625 格，并验证出生点 5.5 格外的目标中毒。
共享路径的 PrimePoisonNova 验证持续移动和自身生命周期，本次没有改它既有的速度换算。
不使用 GPU，也不是真实网络收包测试。

另一台电脑应对照原版观察：低/高技能等级、飞行拖尾、命中怪物及空投到期、
毒云从中心向外铺开、烟雾多方向飘动和消退、首次冷加载与再次施放、联机观察端是否重复云团。
DCC 像素混合、软件后端调色板量化和真实网络预测/丢包时序仍需实机验收。
原生方向量化、逐分量截断及模拟稀疏快照现有定向覆盖，但不声称全部路径基础设施已移植。
以上无 GL 回归不能证明实机画面已与原版完全一致。

## 后续修正：毒烟的混合模式

本轮修复定向验收：JDK 17，真实 1.10f MPQ，`:core:plagueJavelinRegression` 共 16 类 / 68 项，
失败、错误、跳过均为 0；生产代码编译及模块边界检查通过。新增覆盖斜向逐分量截断、86 帧 raw
precision 累加、连续/稀疏网络时钟、重复快照、不同 ECS id 的种子一致性、100 帧历史中已到期
载体及其末尾烟雾、冷加载动画年龄、FlatBuffers 零 seed/旧包，以及版本 1/2 检查点兼容。
这些结果替代前述“尚未覆盖 LUT / 稀疏快照”的旧状态，不替代另一台电脑上的实际画面验收。

之前按毒元素/名称选用 `SCREEN` 没有原版证据，本轮删除该例外。
`MissileLoader` 的毒标、瘟疫标枪、子云和 `poisonpuff` 现遵循自己的 `Trans`：
`Trans=1` 使用既有 `ADDITIVE`（源色 + 目标色），不再以 Screen 的
`source + destination * (1-source)` 压低烟雾重叠区域的颜色。
`Trans=0` 根标枪仍用正常绘制；不增加额外透明度/亮度或动画相位。
隐藏重复拖尾层的独立规则与地面火焰分支没有改变。

依据为上文同 SHA-256 的本机 1.10f `D2Client.dll`，本轮通过已安装的 objdump
只读复核 `0x6FB5AFD2..0x6FB5B07A`。`UNIT_MISSILE=3` 分支按导弹编号取得行后：

- `0x6FB5B011` 读取 `Missiles.Trans`（行偏移 `0x18D`）；
- `0x6FB5B017..0x6FB5B025`：默认模式 5，`Trans=1` 改为模式 3；
- 此后没有毒元素/技能名称判定；`Trans=2` 改为模式 4，hover 高亮可覆盖为 7；
- `0x6FB5B059..0x6FB5B066` 把所选模式传给绘制接口。

这也与 `docs/d2moo-progress-roadmap.md` 的“2026-10-18 闪电导弹加法混合”
已核验链路一致：D2Gfx #10072 → D2Glide `0x6F815AE0` → 模式 3 选混合状态 2 →
`0x6F812AD2` 调用 `grAlphaBlendFunction(ONE, ONE, ZERO, ZERO)`。
本轮同样只读复核了该 D2Glide 调用附近的原始指令，没有执行原版 DLL。
D2MOO `source/D2Gfx/include/DrawMode.h` 的模式 3 为 `DRAWMODE_MODULATE`；
具体方程以该版本 D2Glide 指令为准，不能从枚举名称猜测。

`PoisonCloudPresentationTest` 新增五条真实表行与合成毒元素/技能名的选择器断言，
锁定毒类不能覆盖 Trans=1 的原生选择。此处仅修复已证明的硬件后端混合方程，
不声称还原 D2DDraw 软件后端的 256×256 调色板查表量化，亦未进行 GPU 逐像素验收。
上文 CltDo3/CltDo4 等 REA Evidence ID 继续保留；本段 objdump 复核不伪造为新的 REA 记录。
