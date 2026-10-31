# 瘟疫标枪客户端效果对齐（2026-11-01）

## 本批修改

按 D2MOO 服务端源码、原版 1.10f MPQ 和 REA 的 D2Client.dll 静态证据修正：

- SrvHit2 的 8 个主环 + 15 个交错环子云统一出生在命中中心，以原偏移为目标向外移动，
  不再直接出生在终点后立即停止。到达前钳制最后一步，避免提前一帧吸附或越过目标。
- 创建时设置 `Range + loops * (SubStop - SubStart)` 时钟；原表为 `60 + 3 * 9 = 87` 帧。
  移动消耗同一寿命，到达后不重启 87 帧。普通 Poison Javelin / 尸体毒云保留共享生命周期。
- CltDo4 的烟雾出生点继承父云位置，随机的是绝对目标坐标，不是出生点像素抖动。
  每轴 `d = rollLimited(2*r) - r; target = parentIntegerCoordinate + d + sign(d)*r`，
  其中零走正分支。原表 r=6，偏移为 `[-12,-7]` 或 `[6,11]` 世界子格，
  不再使用 `[-6,6]/16`。速度方向取出生点到目标的单位向量，静止父云不再只向 +X 吐烟。
- CltDo4 使用两字种子：`next = uint32(low) * 0x6AC690C5 + uint32(high)`；
  有界取值用低 32 位取模 / 二次幂掩码。正 interval（包括 1）消耗一次机会抽样；
  **剩余帧**为零或 interval<=0 时才跳过机会抽样。elapsed=0 不是“必定首帧生成”。
- `poisonpuff` 保留 Vel=1 / Range=25 的帧寿命，不再清零寿命后依赖动画结束；
  删除无原版依据的实体/种子哈希起始动画相位，遵循 RandStart=0。
- CltDo3 拖尾改为整数路径格变化时生成；CltDo3/4 与烟雾的时钟不再等待 DCC。
  其他短命中动画仍等待自己的资源加载，不改变此前 FingerMage 的第 21 次更新到期规则。

没有改毒伤公式、抗性和减伤，也没有重做 PL2 Screen / 透明度规则。
新扩散路径及统一寿命会改变云团在何处、何时覆盖目标，这是本批修复的预期影响。

## 当前架构的明确选择

原客户端 CltHit2 也会生成同样的环，但 Riiablo 已由服务端生成并同步真实子云
（原表 `plaguejavcloud.ClientSend=1`）。当 CltHit2/SrvHit2 的第一子云名称一致时，
客户端命中事件只消费音效，不再额外创建一团中央云；烟雾继续由每个真实子云的 CltDo4 生成。
这是适配当前权威同步架构的去重，不是声称恢复了原客户端预测时序。

客户端烟雾另存 `clientSeed`，初次从现有完整 nativeSeed 复制；无完整种子时用
`rngState/666` 初始化。之后不修改服务端 nativeSeed / rngState。该流与 clientFrame 一样
是瞬态表现状态，实体回收时清空，不加入服务端存档、回放或网络权威数据。
因此不保证两台客户端具有逐烟雾相同的随机轨迹，也不保证与原版的初始种子一致。
未审计的 CltDo8/49 继续使用旧 fallback，没有借此批顺带改写。

CltDo3 的原版门禁是动态路径 flag 0x8。当前浮点 ECS / 网络副本没有暴露该标记，
本批以当前整数格与上次观察格（首次为 start 的格）比较实现对应门禁。
网络稀疏快照跨过多个格时仍只生成当前观察点的一团云，不插值补齐未观察的原版路径。

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
  `Missiles.cpp` 的创建分支在 flags&8 且 SubLoop 时延长 Range。
  `source/D2Common/src/Path/Step.cpp` 给出 flag 0x8 的整数坐标变化语义。
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
两项毒枪逐级伤害 golden、全部选入的表现测试及 FingerMage 服务端回归。
最终定向回归 13 类 / 47 项全部通过，失败、错误、跳过均为 0；生产代码编译和模块边界检查通过。
不会修改默认 `:core:test` 的测试范围。报告位于
`core/build/test-results/plagueJavelinRegression`；本机用独立输出目录
`.gradle/plaguejavelin-rea-validation` 避免与其他聊天的地图构建相互覆盖。

扩大到完整 AmazonSkillSpecializationTest 的入口是在同一命令追加
`-PplagueExtendedRegression`。本批扩大回归 13 类 / 85 项：84 通过，1 失败，零错误 / 跳过。
唯一失败是既有 `explodingArrowCreatesAuthoritativeAreaSubMissile` 的爆炸半径伤害断言。
将提交 `e874b59c0` 的原 MissileCollisionSystem 独立编译，并在测试 classpath 中覆盖本批版本，
同一断言依旧失败；该测试不运行本批表现系统。修改前服务端源码覆盖实验保存在本机忽略的
`.gradle/plague-server-baseline*`，本批不修改或排除默认套件中的该测试。
默认全 core 测试仍有此前 Client/GameScreen 迁移遗留的 UI 测试编译阻碍，不宣称全套通过。

新增生命周期测试运行生产系统和真实 ECS 子实体，独立 uint64 oracle 验证机会抽样、
两轴目标和种子隔离，并覆盖冷/热 DCC、25 帧寿命、整数格拖尾门禁、中央云去重和对象池清理。
服务端测试验证 23 云同源出生、实际向目标移动、终点停留和统一 87 帧到期。
不使用 GPU，也不是真实网络收包测试。

另一台电脑应对照原版观察：低/高技能等级、飞行拖尾、命中怪物及空投到期、
毒云从中心向外铺开、烟雾多方向飘动和消退、首次冷加载与再次施放、联机观察端是否重复云团。
DCC 像素混合、路径固定点精度、网络预测/丢包时序仍不在已验收范围。
