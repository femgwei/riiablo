# 原版冰封球后台客户端采集（2026-11-19）

## 结果与范围

原版 1.10f 可以在未显示的独立 Windows 桌面自动启动、选人、进入鲜血荒地、施放冰封球并采集客户端状态。本轮不需要前台窗口、不移动系统鼠标、不再次申请 UAC。复用原有管理员 broker；它只执行仓库固定任务。

最终自动复测批次：`20261119-105700-8968a7d5`。从 broker 接受任务到完成约 46 秒。先前试验的等待时间包含人工实现采集器的时间，不是每次采集必需时间。

- 服务端：1 个 `frozenorb`、30 个 `frozenorbbolt`、16 个 `frozenorbnova`，共 1026 条状态记录。
- 客户端：观察到 1 个球、29 个普通冰箭、16 个末圈冰箭，共 925 条状态记录。
- 客户端普通冰箭记录 542 条，末圈冰箭记录 354 条；两组动画游标都持续变化。
- 连续观察 101 个服务端帧（458～558），使用原始调度器，没有 burst，也没有直接修改帧计数。
- 输入桌面前后均为 `Default`；游戏在 `riiablo_orb_755af770353e`。89 次检查中，测试桌面均未成为输入桌面。
- 屏幕前后均为 1920×1080。前台 HWND 从 2622132 变为 1050482；本轮不能声称用户前台窗口始终不变。游戏所在独立桌面始终未成为输入桌面，因此不能把这次 HWND 变化归因于原版窗口抢占前台。
- 调试器最终 `detached=true`、`cleanup_confirmed=true`。本次创建的游戏已关闭，独立桌面句柄已释放。

29/30 是客户端/服务端**观测到的不同单位数**。不能据此直接判定原版丢失了一支冰箭；客户端与服务端分别执行发射回调，短生命周期对象也可能落在采样间隙。本轮未证明双方每个单位一一对应或全量覆盖。

## 已取得的信息

每条 `missiles.jsonl` 记录区分 `server` 与 `client`，包含服务端帧、采样步数、单位 ID/地址、导弹 class、16.16 位置、方向向量、速度向量、64 向路径方向、Unit 的动画游标/长度原始字段、图形对象原始字段和完整 Unit DWORD 预览。

普通冰箭的路径方向在生命周期内保持不变。末圈冰箭在初始阶段改变路径方向和速度向量，然后稳定；这与 Riiablo 此前发现的“速度已转向、动画朝向仍停留在旧方向”有直接的验证关联。

客户端普通冰箭和末圈冰箭的 `Unit+0x44` 在 0、256、512、768、1024、1280 间循环，`Unit+0x48` 为 1536。服务端对应动画字段不推进。因此不能使用服务端 Unit 的动画字段来代表客户端播放。

**边界：** 本轮没有截取像素，也没有截获最终绘制函数参数。`Unit+0x54` 图形指针可读，但其开头数据不能直接套用 D2MOO 的 `D2GfxDataStrc` 解释为贴图方向、资源名和帧号；当前只保留为原始候选数据。路径方向是实际客户端字段，仍不等于最终提交的 DCC 方向。不能宣布已经验证箭头素材朝向、原版与 Riiablo 的完整画面一致、伤害一致或精确回调调用栈。

## 本轮解决的两个采集陷阱

1. Python `subprocess.STARTUPINFO` 不会传递自行添加的 `lpDesktop` 属性。首个 worker 自检发现仍处于 `Default`，在启动游戏前拒绝运行。现改用完整 Win32 `STARTUPINFO` 和 `CreateProcessW`，子进程启动时即绑定独立桌面；没有调用 `SwitchDesktop`。
2. 单独调用服务端 `PLRMODE_ChangeModeToCoords` 能生成完整服务端冰封球/冰箭，但本轮没有得到客户端冰箭。仅遍历客户端房间单位列表也不够。最终方案通过独立桌面游戏 HWND 的定向鼠标消息触发真实客户端施法，并遍历经原版指令验证的客户端导弹哈希表；这才得到客户端普通/末圈冰箭。

最终 collector 不直接调用游戏施法函数。它通过临时更新入口/返回断点读取一致快照，恢复原字节和寄存器并脱离调试；这仍然属于短暂修改进程执行状态的调试实验，不是纯只读外部轮询。

## 入口与复现

- `tools/original-observer/frozen_orb_background.py`：独立桌面父/子进程、独立 `CXOrb` 角色、进图、采集、关闭。
- `tools/original-observer/native-harness/d2game_frozen_orb.cpp`：固定原版/模块哈希、真实客户端输入、服务端/客户端分开采样、断点恢复和脱离。
- `tools/original-observer/jobs/frozen-orb-private-desktop.json`：固定后台任务，由 orchestrator 在普通入口前分派。
- `tools/original-observer/summarize_frozen_orb.py`：从原始 JSONL 输出分组数量、转向和动画游标统计；不会把图形原始字段误报为最终绘制方向。

已构建 x86 helper、broker 存活时，从仓库根目录运行：

```powershell
python tools/original-observer/broker_client.py submit --op run_job --job frozen-orb-private-desktop
```

全流程自动执行，不需要额外写入触发文件。已有原版游戏时拒绝运行；不会复用、关闭用户当前游戏。原 `CXSorc` 只作为创建独立 `CXOrb` 的来源；不会覆盖原角色。`CXOrb` 为裸装、普通模式存活法师、1 级冰封球、无协同/冰冷支配；复跑时校验并使用游戏自己保存的该独立角色。

批次保存实际执行的 EXE、collector/controller/runner 源码及哈希。执行冻结的 EXE 副本，避免后续构建改变当次运行程序。调试清理不确定时不关闭游戏，并阻止后续批次。

## 证据位置

最终根目录：

```text
tools/original-observer/broker/20261116-014209-3a0c898c/runs/20261119-105700-8968a7d5/orchestrator/
```

- `orchestrator.json`：输入桌面/分辨率/前台 HWND 前后状态、隔离检查数和 worker 结果。
- `worker/entry/summary.json`：自动选人/进图/窗口局部输入记录。
- `worker/capture/summary.json`：原版施法、双方数量、连续帧及调试清理结果。
- `worker/capture/missiles.jsonl`：1951 条原始服务端/客户端单位记录。
- `worker/capture/frames.csv`：双方玩家模式、服务端帧和客户端导弹总数。
- `worker/capture/animation-state-summary.json`：可读分组统计。
- `worker/provenance/`：运行时冻结的 EXE 和源码。

首次客户端成功批次 `20261119-105251-2bf6fa55` 观察到 937 条客户端记录；最终批为 925 条。不同的记录总数反映正常调度与单位可见生命周期，不能直接把采样条数当成动画总帧数或导弹总发射数。

## 下一步

优先把原版末圈冰箭的方向变化与 Riiablo 的 `Velocity → Angle → DirectionResolver` 语义记录对齐；若要验证资源本身是否尾部朝前，再在同一独立桌面中定位最终 DCC 方向/帧选择函数。无需回到完整窗口像素对齐流程。

## 与当前 Riiablo 的核对（同日补充）

结论为部分一致，尚未达到整体一致。Riiablo 的 `frozen-orb-facing.json` 是对生产运动/朝向系统的无窗口测试，DCC 使用方向数桩；其中的零方向不匹配只证明 Riiablo 内部动画方向编号跟随自身速度，不是原版差分通过。

已匹配的部分：原版服务端与移植端均生成 30 支普通冰箭和 16 支末圈冰箭；普通冰箭保持固定运动方向，末圈具有初期转向阶段。逐帧转向时序、初始相位和最终贴图方向仍未完成对齐。

发现的具体差异是移动速度。原版记录的 16.16 速度换算为地图子格/秒（向量长度 / 65536 × 25），普通冰箭约 21.08824～21.09375，末圈约 28.11784～28.125。服务端连续帧位置差也支持这两个区间；客户端偶尔在两个服务端采样点之间执行两次更新，因此不以客户端位置跳变直接推断瞬时速度。

当前 Riiablo 诊断的普通冰箭速度为 18，末圈为 24。生产 `ServerEntityFactory` 同样直接将 `Missiles.Vel` 设为速度长度，`MissileCollisionSystem` 使用 `velocity × delta` 推进，这两类导弹没有额外的原版速度单位换算。按这一实现，移植端约为原版速度的 64/75，即慢约 14.7%。原版少量方向上的微小速度差还涉及整数方向向量量化，不能把所有方向都当作精确相同的浮点速度。

动画方面，原版两类冰箭都观测到 6 帧循环、原始动画速度 256。Riiablo 的 Loader/Stepper 已有按数据表速率推进的路径，但之前的方向诊断没有安装完整动画步进和真实 DCC 加载链，不能把它当作动画帧节奏已通过原版比较。本轮只记录差异，没有修改速度或扩大修复范围。

## 后续修复与无窗口回归（2026-11-19）

上节是修复前结论。本次继续使用已保存的原版数据，没有启动原版或前台游戏窗口。

### 已修复的差异

1. `ServerEntityFactory` 对 `frozenorb / frozenorbbolt / frozenorbnova` 使用原版速度单位：表速度先左移 8 位，应用 Slow Missiles 后乘 75/100，再用整数方向向量计算 16.16 每帧位移。普通冰箭不再以 18、末圈不再以 24 子格/秒移动。其他技能保持原来的速度路径。
2. 末圈原先在创建时先投影旋转了 45°，第一次 SrvDo16 又执行相同旋转。现在按原版先沿原始环向创建，由回调执行第一次转向。转向重新指向“当前整数格 + 偏移”的格中心，保留当前精确位置带来的方向差，替代简单的浮点向量旋转。相关导弹在格中心创建，并保留 16.16 位置推进。
3. 真实 DCC 步进回归发现，浮点时间累加在第 6 次 25Hz 更新时仍可能停留在第 5 帧。新增 `Animation.updateNativeFrames`，仅在本次三类冰封球导弹的 Stepper 分支使用原版 8.8 动画游标推进；Loader 对这些导弹的延迟加载也恢复整数帧相位。原有帧事件和完成事件仍正常分派。

末圈每次转向仍沿用此前的 `Velocity → Angle → DirectionResolver` 即时同步修复。`frozenOrbVelocity` 保存生成时的原版速度标量，避免每次转向重新按量化后的向量长度推算而累积变化。

### 独立原版数据回归

固定测试数据：`core/src/test/resources/original/frozen-orb-motion-1.10f.json`。包含源 JSONL 的 SHA-256，以及原版 1 个球、30 支普通冰箭、16 支末圈冰箭各自最初至多 9 条服务端状态，共 **47 个单位、414 条状态**。目标输入使用原版环坐标及相位规则；期望速度和位置直接来自原版记录，不由移植算法生成。

`FrozenOrbOriginalMotionTest` 为每个案例通过真实 `ServerEntityFactory` 创建导弹，再用 `MissileCollisionSystem` 更新，逐项断言两个速度分量和两个精确位置字段。全部案例通过，包括全部 16 个末圈初始方向及初期转向。这里验证的是独立导弹运动；不等于整次施法的客户端/服务端单位已一一对应。

`MissileAnimationCatchupTest` 从本地 MPQ 读取真实 DCC，确认球为 16 帧、两类冰箭为 6 帧、速率均为 256，并通过生产 `AnimationStepper` 验证连续三轮循环和延迟加载后的继续播放。没有 GPU 绘制。

方向诊断仍生成 `core/build/diagnostics/frozen-orb-facing.json`：普通冰箭 850、末圈 400 条运动/朝向样本，动画方向编号与本地速度方向编号不匹配数均为 0。该测试使用 DCC 方向数桩，不取代真实最终绘制方向采集。

新增速度状态进入 `MissileRuntimeCheckpoint` v4，兼容读取 v1～v3；旧记录没有速度标量时，从保存的运动速度作近似恢复。检查点字段审计同时暴露出既有 `fireWallNextFrame / recyclerRecovered` 未保存的问题，本次把这两个字段一并加入 v4 尾部，避免新增状态时继续保留不完整的序列化。

### 验证命令与剩余边界

本地 Java 17、`D2_HOME` 指向原版资源目录：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.server.FrozenOrbOriginalMotionTest --tests com.riiablo.engine.server.SorceressFrozenOrbIntegrationTest --tests com.riiablo.engine.server.SpecialMissileLifecycleTest --tests com.riiablo.presentation.MissileAnimationCatchupTest --tests com.riiablo.presentation.MissileLoaderTest --tests com.riiablo.engine.replay.MissileRuntimeCheckpointTest --offline --console=plain --no-daemon
```

结果：**31 项测试通过**，模块边界检查通过。没有运行整个仓库测试集。

仍未验证：原版最终 DCC 提交方向、箭头素材视觉朝向、完整客户端回调/绘制调用顺序、伤害与命中差分。当前修复有运动数值和真实资源播放节奏证据，但不能据此宣布用户看到的末圈“尾部朝前”已经完成最终视觉验收。下一步可在独立桌面采集最终贴图方向/帧选择参数，核对末圈速度对应的 DCC 方向，继续无需前台截图。

## 最终资源方向与绘制提交后台核对（2026-11-19 后续）

本节更新上节的“最终 DCC 提交方向未验证”边界。原版后台确实执行精灵绘制，不需要切到用户前台；当前 Riiablo 的资源方向和帧选择通过了以下独立原版数据回归。仍未进行像素或完整画面验收。

### 采集位置与字段含义

固定版本 `D2Client.dll` 的 RVA `0xBAF28` 调用 D2CMP ordinal 10055 准备资源；`0xBAF2D` 是该调用返回边界，`0xBAF3E` 是成功准备后的入口。对压缩 DCC 分支，在 `D2CMP.dll` RVA `0xBFE7` 读取方向映射返回的 EAX、缓存记录中的方向数和帧数。随后在 `D2Client.dll` RVA `0xBB066`、`0xBB06B` 观察 D2Gfx ordinal 10072 调用前与返回后，记录同一单位的实际绘制数据。

最终 buffer 的 `+0x08` 是帧，`+0x0C` 仍为 64 向输入，不能直接当成 DCC 的 16 向编号。`+0x10` 在这些提交中可以是 0；它不是实际资源方向数。DCC 选择结果来自前述真实执行点的 EAX，资源名来自 buffer `+0x44`。冰箭使用 `IceBolt`，16 向、每向 6 帧；球使用 `IceOrb`，1 向、16 帧。记录中的 `decoded_direction_offset` 是早期命名，实际只是缓存 `+0x2C + direction*4` 的原始值，未证明为文件偏移；后续 collector 已改名为 `cache_direction_entry_raw`，回归不依赖此字段。

观察器同时等待资源调用返回与方向选择事件。部分缓存路径不经过该选择点，必须标记 `selection_observed=false`，不能沿用上一条选择结果。准备失败也必须在返回边界重启观察，避免把下一个单位的选择误配给当前单位。每次原版断点命中时先恢复 EIP 和原字节，再继续执行；没有主动调用图形函数或强制 StartDraw 返回成功。

静态辅助证据：`tools/original-observer/frozen-orb-dcc-direction.rea.json`，REA/Ghidra 12.1.4 对 D2CMP ordinal 10018 / VA `0x6FDF11F0` 的反编译，证据 ID `ev_262cc39b8eaf6c2c7bea91feba054362ec96eb8105885c19799160c2ef4a7ddb`。D2CMP SHA-256 为 `e725036e1118d34b98d2b739525ff75a225e6964b425469e4773c93f0c9cf882`，已加入 collector 版本校验。`frozen-orb-dcc-selection-static.json` 保存受限范围的 PE/Capstone 指令及原始字节，仅为静态辅助，不能替代运行证据。

### 最终有效批次

路径：`tools/original-observer/broker/20261116-014209-3a0c898c/runs/20261119-140541-d48e2491/orchestrator/`。

- 观察连续 101 个服务端帧（459～559），未加速 tick；服务器状态 1042 条，客户端状态 937 条。
- 实际精灵提交 **802 次**，对应绘制函数返回 **802 次**。
- 其中 **769 次**同时取得有效 DCC 方向选择：球 26、普通冰箭 482、末圈冰箭 261。其余 **33 次**有提交/返回，但未观察到该方向选择分支，不纳入方向差分。
- 已选记录涵盖球 1 个、普通冰箭 29 个、末圈冰箭 **16 个**不同客户端单位；末圈覆盖全部 16 个 DCC 方向。普通箭的客户端数量为观测覆盖，不能声称采到服务器全部 30 支的每一帧。
- 输入桌面前后 `Default`，独立桌面为 `riiablo_orb_fd955e7e3e52`（精确值以 `orchestrator.json` 为准）；79 次检查中未成为输入桌面，程序无 SwitchDesktop 调用。屏幕前后 1920×1080，前台 HWND 前后均为 1050482。
- `detached=true`、`cleanup_confirmed=true`、`owned_game_closed=true`，无清理阻塞。

此前 `20261119-140005-bafdb91a` 已取得 923 次提交/返回，但没有直接采 DCC 选择，只能作为后台绘制存在的证据。`20261119-140248-2c2b9149` 没有覆盖资源准备返回分支，部分方向关联可能跨调用，**不能作为方向回归输入**。更早 `20261119-135333-c3e53007` 因错误字段边界断言中止，已安全清理。保留失败批次，不将其覆盖成成功。

### 自动回归和结论

`tools/original-observer/export_frozen_orb_sprites.py` 校验逐条提交/返回配对、客户端动画游标与提交帧、路径与提交方向输入、DCC 数量边界，排除未直接观察到方向选择的记录。导出 `core/src/test/resources/original/frozen-orb-sprites-1.10f.json`，保留原始 JSONL 路径和 SHA-256，期望方向/帧直接取自原版运行记录。

`FrozenOrbOriginalSpriteTest` 使用生产 `DirectionResolver` 和 `MissileLoader.initializePlayback`，从原版瞬时速度输入构造朝向，读取 Riiablo 当前数据表选择的真实 MPQ/DCC，核对资源名、方向数、帧数、每条期望方向及帧。只禁用 DCC 的 GPU 上传；资源解码、动画对象与方向系统保持真实。末圈每条记录在第一次系统处理后即匹配，不允许等待普通导弹的两次稳定处理来掩盖转向延迟。帧部分是给定原版动画游标的播放相位核对；连续步进节奏由已有 `MissileAnimationCatchupTest` 独立验证。

**769 条方向与帧选择均匹配**，包含全部末圈方向。使用同一资源，当前修复后的选择路径没有再现此前“末圈箭头朝后”的方向错选；这不是对历史修复前版本的回归结论，也不证明用户所运行构建已更新。该结论只适用于本次技能、版本和输入；不等于截图已验收、完整施法客户端/服务端时序一致、原版所有回调调用顺序已还原，或命中伤害一致。

验证：`FrozenOrbOriginalSpriteTest` 和 `MissileAnimationCatchupTest` 共 **7 项通过**、无失败/跳过，模块边界检查通过。日志 `tools/original-observer/frozen-orb-sprite-validation.log`。本轮没有修改 Java 生产代码，也没有再次运行此前全部 31 项回归。

复现导出和测试：

```powershell
python tools/original-observer/export_frozen_orb_sprites.py <capture目录> core/src/test/resources/original/frozen-orb-sprites-1.10f.json
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.presentation.FrozenOrbOriginalSpriteTest --tests com.riiablo.presentation.MissileAnimationCatchupTest --offline --console=plain --no-daemon
```

后续优先根据仍能复现的具体异常定位客户端状态发布、渲染顺序或实际命中链；无需为了这次方向选择核对重新走前台像素对齐。


## Blaze 后台客户端动画比较（2026-11-19）

本轮使用相同的独立桌面方案，新增 `CXBlaze` 裸装 1 级 Blaze 法师角色。角色进入 Blood Moor 后，由窗口定向消息激活 Blaze，等待施法完成，再实际行走 13 个 subtile。没有系统鼠标、前台切换、截图或主动调用绘图函数。调试器已脱离，测试实例已关闭；本轮没有修改 Blaze 生产代码。

有效完整批次：`tools/original-observer/broker/20261116-014209-3a0c898c/runs/20261119-151216-db768db2/orchestrator/`。原始证据位于 `worker/capture/`，当次执行源码与程序位于 `worker/provenance/`。

- 输入桌面前后均为 `Default`，屏幕前后均为 1920×1080，前台 HWND 前后相同。82 次检查确认独立桌面没有成为输入桌面。
- 连续采样 221 个服务端帧，取得 1,495 条服务端和 1,510 条客户端 missile 状态；D2Gfx 提交和返回各 1,510 次，其中 1,506 次匹配到 D2CMP 实际 DCC 选择。
- 服务端 13 个 class 67 地面段，客户端 14 个对象：初始 class 67 一个、class 69 四个、class 104 两个、class 105 七个。13 个行走段均能按出生帧和位置匹配客户端对象；客户端还存在一个施法初始对象。
- 原版实际选择 `groundFireBig`（475 次）、`groundFireMedium`（805 次）、`groundFireSmall`（230 次）。Riiablo 的 `blaze / firemedium / firesmall` 是 missile 表项名，分别对应这些 DCC，不能把表项名和资源名不同认作素材差异。
- 三种 DCC 均为 1 方向、37 帧、速率 256（25Hz）。14 个对象全程符合：出生 0～11 帧增长 → 带独立起始相位的 12～35 帧循环 → 最后 11 tick 为 `10,9,8,7,6,5,4,3,2,1,1` 收缩。最后未观察到提交第 0 帧，不能将收尾写成 10→0。
- class 69 的可见寿命为 90 帧，初始 class 67 及 class 104/105 为 115 帧；所有服务端段为 115 帧。这是本次 1 级场景观测，不能直接推广全部技能等级。

与当前 `ReferenceElementalPresentationSystem` 比较，已定位四处实现差异：

1. `configure` 设置 `loopStart=12, loopEnd=36`，`frameAt` 按闭区间循环，实际 25 帧；原版本次是 24 帧循环，循环中未提交第 36 帧。
2. `frameAt` 到结束仍循环，`applyAnimation` 仅在最后 0.4 秒降低透明度。它缺少原版倒放增长帧的收缩阶段；是否还需要保留某种原版透明度变化，本次没有像素证据。
3. `blazeDurationSeconds` 将服务端寿命统一传给三种展示。原版 class 69 大火焰视觉先于 115 帧服务端段消失，当前统一寿命不能表达该差异。
4. `updateBlazeTrail` 额外要求与前段相隔至少 0.1 秒、至少 14 像素。本次原版行走每 2～3 tick 产生一段、相邻位置差 1 subtile。将 13 个原版出生样本回放进此规则仅保留 9 个。这是对生产规则的离线回放，尚非运行中 Riiablo 的同路径采集。

`summarize_blaze.py` 不启动游戏，校验原始调用/返回、连续帧和清理结果，导出 `core/src/test/resources/original/blaze-client-animation-1.10f.json`，保留全部 1,510 个帧值、14 段寿命、13 个服务端出生位置及原始文件 SHA-256。`BlazeOriginalAnimationTest` 从 MPQ 解码三种真实 DCC，检查捕获的帧可用性、增长/循环/收尾及两端出生对应。测试通过仅证明原版基准完整，不表示 Riiablo 已一致。随机相位由实测首次循环帧拟合，尚未重建原版 RNG 消耗。

早期批次 `145808-00c0df1d`、`150155-6d490e1c`、`150503-1c364fc3` 的移动消息发在施法结束前，实际没有走动，只能观察初始火焰。它们均安全清理；不能据此声称 Blaze 没有服务端地面段或只用大火焰。完整行走采集已替换早期不完整 fixture。原始成功批次的 `summary.json.scope` 留有早期“未暴露服务端段”文字，与其 `server_unique` 冲突；保留原始文件以供审计，本文和导出器以实际 JSONL 为准，采集器源码已修正文字并增强实际移动和两端轨迹断言。

验证：`BlazeOriginalAnimationTest` 三项和 `BlazeGoldenDamageTest` 一项全部通过，日志为 `tools/original-observer/blaze-animation-validation.log`；更新后的 C++ 采集器也已编译通过，没有为此再次启动原版。早期包含更广服务端测试的运行还发现两个持续时间断言失败：`NativeSorceressFireAreaDataTest` 与 `SorceressFireAreaIntegrationTest` 期望 120，而当前 `dm12` 公式返回 117。本轮未修改该公式或期望值，也未采集技能状态自然结束帧，因此该项仍待核对，不能混淆技能状态持续时间和单个火焰段 115 帧寿命。

建议下一轮先修正 Blaze 专属的增长→24 帧循环→收缩状态机、按视觉分支设置寿命，再用原版出生点回放修正轨迹筛选。最后补采实际目标生命变化；本轮没有证明伤害、所有等级、透明混合或像素一致。
