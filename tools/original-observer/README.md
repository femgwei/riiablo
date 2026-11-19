# 原版差分测试与后台观测

冰封球后台客户端采集已通过独立桌面验证，记录见 [`docs/frozen-orb-background-capture-2026-11-19.md`](../../docs/frozen-orb-background-capture-2026-11-19.md)。可用 `broker_client.py submit --op run_job --job frozen-orb-private-desktop` 复跑；采集真实客户端路径方向、动画游标及图形原始字段，不切换输入桌面。最终贴图方向/资源字段仍需进一步定位。

当前研究与交接入口：[`docs/original-client-animation-research-2026-11-15.md`](../../docs/original-client-animation-research-2026-11-15.md)。该记录汇总原版 1.10f 后台观测、broker、native harness、Riiablo 客户端动画 trace 和旋风斩当前结论。当前主目标是功能性技能客户端动画重构；不要把视觉哈希差异直接解释为资源或绘制错误。

在仓库根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\original-observer\run-headless.ps1 -D2Home 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
```

这条命令依次运行原版 DLL 差分和现有 riiablo headless 回归，不启动游戏、图形后端或旧版 `Diablo II.exe` 启动器。需要 Python、JDK 和 VS2019 Community 的 x86 编译工具。

- `seed-differential.py`：在独立 x86 子进程里执行用户原版 `D2Common.dll` 的 6 个种子状态导出，与直接编译的生产 Java `D2Seed`、`Seed` 类比较 448 组输入。覆盖零、符号位、最大 uint32 和固定随机样本；仅验证初始化、设置和读取，不验证随机抽样序列。每次运行固定校验 DLL SHA-256 和导出 RVA，并保留输入、双方输出、退出码、源码哈希及差异 JSON。原版子进程超时 10 秒，不匹配版本在加载前拒绝。
- `rng-differential.py`：直接执行同一 DLL 中经指令核对的原始随机数函数（RVA `0x38E30`）和有界随机数函数（RVA `0x3D3E0`）。1408 组条件各连续抽样 64 次，共 90,112 次，逐次比较返回值及种子两字；覆盖负数/零/一边界、2 的幂、普通模数和最大正 int。加载前固定 DLL 哈希，调用前固定完整函数机器码；同样使用无控制台子进程和 10 秒超时。不会把自写 C++ 算法当作原版执行结果。
- `:core:monsterAiTest`：怪物 AI 决策与检查点恢复回归。这是移植端测试，并非原版实时 AI 比较。
- `:core:playerCombatRegression`：七职业技能、伤害结算和同步回归，同样属于移植端测试。
- `:core:m4CoreDataSourceRegression`：显式使用所选原版 MPQ，核对技能、怪物、导弹、状态和属性表的固定 1.10f 数据与投影。安装目录通过 `-Pd2Home` 传递；避免环境变量漏传造成原版资源测试跳过。
- 最后单独运行世界隔离、模块依赖方向、渲染顺序和元素效果测试；覆盖全局资源为空时的弹道创建，以及同一弹道复用到不同世界时的属性编码。
- `server:d2gs:headlessMapTour` 可对指定种子实际进入 Act I 的 Rogue Encampment/Blood Moor，并写出 `scene-level-<level>.txt`。快照包含房间拓扑、原生预设怪物/物件、运行时怪物类型/精英属性、地面物品代码/品质，以及场景计数；`scene-diff.py` 可在取得原版快照后按身份和数量比较，忽略动态 ECS entity id 和位置字段。

`run-headless.ps1` 临时设置 Byte Buddy 对 Java 25 的兼容选项，退出后恢复原值，不修改系统环境或项目依赖版本。
同样临时将 `D2_110F_HOME` 指向所选安装目录，让声音映射探针也能读取原版资源，退出后恢复原值。
Gradle 使用 `--continue` 收集独立套件的结果；任何套件失败，整个命令仍返回失败。
脚本同时检查原版 MPQ 集成测试的 JUnit 报告；报告缺失、用例跳过或断言失败都会返回失败，不能用“Gradle 成功但原版测试未执行”作为通过依据。

原版执行证据保存在 `runs/<时间>-seed/summary.json` 和 `runs/<时间>-rng/summary.json`，原始 CSV 在各自目录的 `native.stdout`、`java.stdout`。Gradle XML 和 HTML 报告在 `core/build/test-results/`、`core/build/reports/tests/`。原版游戏文件、编译结果和本地测试输出均不纳入 Git。

仅运行原版 DLL 差分：

```powershell
python tools\original-observer\seed-differential.py --home 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
python tools\original-observer\rng-differential.py --home 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
```

早期副本启动探针的结果如下；已被下文原路径 loader 成功进图与控制实验补充。准备副本会复制 DLL、MPQ、安装级配置、`save` 目录和原版资源哈希；普通探针只有明确指定 `--debug` 才启用调试创建，`--exact-args` 可按原版 Game.exe 的无参数方式启动。当时副本中的 Game.exe 退出码 2，且只加载到 D2Win/D2Sound，未加载 D2Client/D2Game；那次实验没有取得场景快照。模块已加载、探针成功清理进程，都不能证明已经进入游戏。

使用 Onlyer 免 CD loader 时，目标进程是 loader 自身，而不是另一个 `Game.exe` 子进程。`prepare` 会把 `D2v110各版本免CD全功能启动.exe` 复制到隔离副本；可用下面的命令按“直接双击 loader、无参数”的方式观察同一 PID 的模块和异常：

```powershell
python tools\\original-observer\\observer.py probe `
  --runtime tools\\original-observer\\runtime `
  --executable 'D2v110各版本免CD全功能启动.exe' `
  --exact-args --current-desktop --debug --seconds 15 `
  --output tools\\original-observer\\runs\\loader-debug
```

探针会记录 loader 主模块、`D2Client.dll`/`D2Game.dll` 加载、窗口、原生异常和退出码；它不会切换前台、发送输入或写入原版安装目录。当前这台机器的后台复现已确认核心 DLL 加载成功，但在 `Fog.dll` 初始化阶段收到 `0xC00000FD` 栈溢出，尚未进入菜单或地图。

### 原路径窗口启动与管理员只读观测

上述栈溢出来自副本启动实验，不能据此认定最小化或独立桌面本身导致崩溃。原路径具有 XP SP2 等兼容性设置，且实际游戏进程已提权；普通权限探针不能枚举其模块。已验证的路径是：管理员助手从原目录以 `-w -res800` 正常启动 loader，窗口出现后立即最小化，再用同权限只读采样。游戏会正常写自身日志等运行文件；助手不修改注册表、兼容性设置或安装文件。

`observe-live.ps1` 会复用原目录已有的唯一 loader 实例；没有实例时才启动。它需要 Windows UAC 确认，可用 `Start-Process -Verb RunAs -WindowStyle Hidden` 运行，参数为 `-Python <64位python绝对路径> -RunDirectory <新的输出目录>`。助手完成 12 秒采样后退出，保留游戏运行。它不安装服务或计划任务，也不关闭 UAC。

`live-observer.py` 也可在管理员终端直接调用，且只附着读取指定 PID，不启动或终止游戏：

```powershell
python tools\original-observer\live-observer.py --pid <PID> `
  --home 'G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  --output tools\original-observer\runs\live-sample --seconds 12
```

读取前校验可执行路径和三份核心 DLL 的固定哈希。角色全局地址已从旧头文件的错误候选 `0x11C1E0` 更正为此 DLL 实际 getter（RVA `0x883D0`）引用的 `0x11C200`，每次读取还会验证重定位后的 `MOV EAX,[global]; RET` 机器码。角色 Unit 结构仍须在实际进入地图后验证。指针为空不能单独确定具体菜单页面；模块加载、最小化和 CPU 活跃也不能证明地图逻辑推进。

### 后台菜单自动化与隐藏窗口运行

2026-11-13 已实测后台定向消息可操作该版本的菜单：读取 `D2Win` 控件链表中的真实按钮文字、启用状态和矩形，再用 `PostMessageW` 发送鼠标按下/松开。已完成“單一玩者”→现有角色 `aaa`→“好”，没有全局输入或前台切换。`menu-driver.py` 将每次动作和前后控件状态保存在输出目录，入口只允许单人游戏及确认按钮；不能用它删除角色或进入 Battle.net。

单纯最小化时，提交进入后 120 秒内仍无角色对象。此 DLL 的 `D2Gfx.WINDOW_GetState` 会在恢复时检查 `IsIconic`。后续隐藏实验成功进入地图：先隐藏窗口，在空窗口区域保护下以 `SW_SHOWNOACTIVATE` 清除最小化状态，再隐藏并恢复原区域；向本进程窗口投递 `WM_ACTIVATEAPP`，让游戏自身处理恢复。过渡中窗口没有可绘制区域，未调用前台/焦点切换 API；采样确认游戏 HWND 从未成为前台。仅修改 `WS_MINIMIZE` 样式不足以清除 Windows 的 iconic 状态，这次失败也保留在日志中。

这是有图形后端的隐藏窗口运行。游戏在该模式下保留运行；后续探针可复用隐藏的同一进程。不要用“窗口隐藏”推断模拟正确，更不能把客户端动画变化当作服务端 AI/伤害结算正确的证据。

管理员终端中的组合入口如下（普通终端需先通过 `Start-Process -Verb RunAs -WindowStyle Hidden` 启动该助手；UAC 仍须人工确认）：

```powershell
& .\tools\original-observer\observe-live.ps1 `
  -Python (Get-Command python).Source `
  -RunDirectory 'F:\3rd_src\riiablo\tools\original-observer\runs\entry-unique-name' `
  -AutomateEntry -HiddenRun -Character 'aaa'
```

助手复用唯一匹配实例或按 `-w -res800` 启动原路径 loader，然后运行有界 45 秒菜单状态机。`-Character` 必须与当前已选角色文字精确一致；本版不会猜选其他角色，也不自动选择新的难度。遇到未知页面、禁用按钮、版本签名不符、窗口变成前台或超时会停止；不会关闭游戏。`menu/summary.json` 的 `scene_observed` 表示读到并校验了地图链路，`player_observed` 仅表示角色对象存在，失败/超时返回非零退出码。

存档路径可能与 exe 所在目录不同：本机 HKCU 的 `Save Path` 实际指向 `G:\BaiduNetdiskDownload\Diablo II\Save`，所以菜单显示 `aaa`，而不是 1.10F 目录中 `wp-a.d2s` 的角色。新版本驱动会在任何菜单输入前同时备份安装目录存档和注册表指定存档，并记录文件哈希、大小、修改时间。首次进入实验仅事先备份了安装目录；实际 `aaa` 存档在进入后才补做备份，其记录修改时间仍为 2026-10-01。正常游戏仍可能自行保存，不会把原版存档当作只读资源。

`scene_reader.py` 只读遍历角色→路径→活动房间→DRLG 房间→关卡，并核对原版 getter 机器码、路径回指、角色是否落在房间内及 Act 归属。已验证 `aaa` 位于 Rogue Encampment（level 1），坐标 `(4393,5628)`，地图种子 `127897359`。当时客户端加载 35 个房间、238 个单位（1 个玩家、30 个 type 1 怪物/NPC、207 个物件），这些数字覆盖客户端当前加载范围，不是整张地图生成总数，不能直接与 riiablo 全图计数作差分结论。

成功隐藏进入和连续状态证据：`runs/20261113-164003-menu-hidden/`；带地图链路与加载单位的只读采样、实际 Save Path 备份：`runs/20261113-164432-scene-background/`。该轮采样没有发送新的游戏输入。

### 后台行走和原版场景差分（2026-11-13）

`menu-driver.py --hidden-run --walk-to-level 2 --capture-layouts --pause-after`
可在已经校验的 Normal Act I 营地东出口执行有界自动行走。驱动从原版活动房间读取碰撞网格，按玩家路径掩码规划路线，用此版本实际相机 getter 将世界坐标投影为目标窗口的客户区坐标，然后仅向该窗口投递消息。不移动系统光标；有时间、点击次数、无进展和投影合理性限制。角色到达 level 2 即停止。请求行走但超时/提前停止时返回失败，不能以仍读得到角色来判定完成。

`runs/20261113-170512-original-walk/` 记录了 13 次定向点击、约 17.5 秒真实走出营地；首次轴解释错误的投影被检查拒绝，记录在 `runs/20261113-170337-original-walk/`，没有发送行走输入。全过程屏幕仍为 1920×1080，目标窗口未成为前台。`--pause-after` 最后向游戏投递失活并隐藏窗口；本次看到图形 pause/state 均为 1，这还不是权威服务端 tick 停止证明。

原版正确布局数据为 `runs/20261113-170834-original-scene-final/layout-0.json` 至 `layout-5.json`（schema 2）。早期 schema 1 的 picked-file 字段读自 presetRoom+4，该字段在此版本未填充，**不能用其文件索引差异判断移植错误**。schema 2 根据实际 getter RVA `0x481B0` 从 presetRoom→pMap→nPickedFile 读取；Java 诊断也使用 pMap 的同一字段。

最新 headless 输出、逐样本差分和汇总在 `runs/20261113-preset-stage-verified/`，Gradle 日志为同名 `.log`。地图种子 127897359、Normal 鲜血荒地的结果：

- 地图尺寸 280×480 subtiles 一致；79/79 房间位置、尺寸和预设定义一致。
- 50/50 已选择的预设场景文件索引一致。
- 已观测的 16 组房间邻接一致；其余 63 组没有原版观测值。
- 16 个原版已初始化房间内的 4/4 预设物件，DRLG 生成前 class、初始 mode 和关卡局部位置一致。排除 `source=ObjGroup/...` 的后续运行时 population。6 份原版采样结论相同；它们属于同一实例、同一种子，不是 6 个独立地图覆盖。
- Town 尺寸一致，Java 走 DS1 兼容路径、未导出 RoomEx，因此营地房间几何/邻接不宣称通过。

预设与运行时编号必须分开。Cott1.ds1 的 raw 38 在原版 D2Common 导出 #11223 映射为 **580**，这是特殊箱子生成选择器；riiablo 的 `NATIVE_OBJECT class=243` 是解析后的实际物件。新增 `presetClass=580 resolver=SPECIAL_CHEST` 后，生成前数据匹配，不再把 580→243 报成错误。普通 PE/Capstone 静态证据（DLL 哈希、getter、分发表和特殊箱子函数）保存在 `preset-selector-static-evidence.json`；这份证据不表示执行了该 DLL 函数，也不是 REA Evidence。

已确认的剩余实现差距：`NativePresetObjectResolver.resolvePresetChest` 当前用地图种子及坐标哈希选择箱子，而原版使用物件区域共享 RNG，并受激活顺序影响。因此预设数据一致不保证实际箱子 class、神殿属性或开箱掉落一致。运行时怪物、物品也尚未对齐房间激活集合、时刻和动作；不以全图 materialization 数量直接对比原版当前加载范围。下一步应采集服务端房间激活顺序、物件控制 RNG 和具体生成单位，再建立运行时差分。

复现比较与失败门禁（不需要启动游戏）：

```powershell
python tools/original-observer/layout-diff.py `
  tools/original-observer/runs/20261113-170834-original-scene-final/layout-0.json `
  tools/original-observer/runs/20261113-preset-stage-verified/scene-level-2.txt `
  --output tools/original-observer/runs/20261113-preset-stage-verified/layout-diff-level-2.json
python -m unittest discover -s tools/original-observer -p test_layout_diff.py -v
```

11 项门禁测试验证种子/难度不符、空原版列表、重复几何、同数量不同位置、缺失/错误物件等不能误报通过；旧版本缺少生成前 class 或可靠文件索引时显式未测。总状态 `partial` 表示目前覆盖范围有缺口，并非完整场景通过。只读采样没有成功样本时返回失败，不再输出成功的 observed 状态。

### 原版线程控制和首个技能 getter 实测（2026-11-13）

`control-live.py` 和 `native-harness/d2game_controller.cpp` 是新的一层，**不是只读观察器**。它们临时附加 Windows 调试器、写入软件断点、改变游戏线程寄存器和一小段空闲栈。没有安装 DLL、修改磁盘中的游戏代码、使用全局输入或通过自建注入线程执行游戏函数。现有 `observer.py`、`live-observer.py` 的只读契约不变。

原版 `D2Game!GAME_UpdateProgress` RVA `0x86D0` 的入口/返回点用于精确观测执行边界。入口的 ECX 提供实际 `D2GameStrc*`；验证 Normal、单个客户端、唯一服务端玩家、unit→game 回指、Act→DRLG→game 回指及地图种子。读写前固定模块路径和哈希，原生助手也校验 D2Common/D2Game SHA-256、入口机器码和 native caller 的 CALL 目标。当前仅支持明确选定原路径 loader PID，不猜测进程。

`runs/20261113-controller-pilot/` 的实际结果：

- 定位服务端游戏对象 `35651708`、服务端玩家 `37494784`、原生更新线程 `24180`。这些地址只属于当时那个实例，重启后必须重新解析。
- 在原生更新入口保持 debug event 200ms，权威帧计数不变；这是调试暂停的证明。与图形 pause flag 是两个不同层次。
- 正常调度截获 12 次完整更新：`42801 → 42813`，逐次增量均为 1。
- 在同一游戏线程和原生锁持有区间连续重入完整更新 12 次：`42815 → 42827`，逐次增量也均为 1。没有直接改写帧计数。
- burst 的旧版 `GetTickCount64` 记录为 0ms，表示低于此次计时器分辨率，**不能据此计算加速倍率**。此次仅证明连续推进帧可行，没有验证加速前后完整状态等价；客户端输入、网络处理和画面可能来不及在两帧之间运行。
- 在更新线程调用 D2Common #11004/#11005（RVA `0x72810`/`0x72B00`），传入真实服务端玩家、Fire Bolt ID 36、等级 1–20、关闭 mastery。当前 Amazon 玩家没有火弹协同技能，此处是基础 getter 夹具，不表示该角色学习或实际释放了火弹。40 次返回保留完整定点数，并检查函数返回后的 stdcall 栈平衡、恢复原寄存器、且游戏帧没有推进。正常和 burst 两批 getter 结果一致。
- 断点恢复、两次调试 detach 均成功。前台 HWND 和桌面 1920×1080 未变；最后图形 pause/state 为 1、窗口 hidden/minimized。最后这项仍不单独证明服务端帧停止。

`MapTourProbe` 通过显式世界资源导出 riiablo 生产 `MissileDamageResolver` 的相同基础范围，包括定点数和整数截断。原版等级 1–20 的最小/最大返回值（40 次 getter、80 个定点/整数结果字段）全部匹配；报告为 `runs/20261113-controller-pilot/firebolt-diff.json`，Java 输出在 `runs/20261113-firebolt-port-oracle/`。这是首个原版实际执行的技能范围差分，不是最终命中掉血或像素验证。

复现需先编译 x86 助手，在管理员终端执行如下有界批次；普通终端可通过 `Start-Process -Verb RunAs -WindowStyle Hidden` 启动，仍需一次 UAC。`--output` 必须新建，原版先最小化或沿用已隐藏实例：

```powershell
python tools/original-observer/control-live.py --pid <匹配原版PID> `
  --home 'G:\BaiduNetdiskDownload\Diablo II 1.10F' --seed 127897359 `
  --output '<新的绝对输出目录>'
python tools/original-observer/skill-getter-diff.py `
  tools/original-observer/runs/20261113-controller-pilot `
  tools/original-observer/runs/20261113-firebolt-port-oracle/scene-level-1.txt `
  --output tools/original-observer/runs/20261113-controller-pilot/firebolt-diff.json
python -m unittest discover -s tools/original-observer -p 'test_*diff.py' -v
```

每次批次在输入前通过菜单助手备份实际 Save Path。不要结束清理状态不明的调试助手：清理无法完成时，新版会保持助手存活并写出 `cleanup_attention_required`，父进程也不会超时杀死它。试验通过后补充的清理恢复、原生哈希门禁和源文件/可执行文件留档已编译检查；本次成功执行证据的精确源码/可执行文件哈希以该 run 的 summary 为准，未人为触发原版异常测试恢复分支。

比较器新增 8 项失败门禁，和地图比较器共 19 项通过：缺失/重复等级、错误伤害、跳帧、未确认调试清理、协同条件不符等均不能报通过。后续范围：独立测试角色/目标、实际技能施放、命中与 HP 差额、完整 RNG/状态对齐、原版渲染帧捕获；其他 209 个职业技能还未建立原版执行差分。

### 可见窗口验证的进程入口（2026-11-13）

用户允许普通窗口后，可使用 `process-session.py --pid <PID> --home '<原版目录>' --output '<新的目录>' --seconds 600` 复用现有实例。原版提权时此助手也需要提权。它校验 loader 和五份模块的路径/哈希、备份安装目录与实际 Save Path，然后等待输出目录 `commands/` 中的 JSON 文件，仅支持 `{"op":"snapshot"}`、`{"op":"native_cast"}`、`{"op":"stop"}`。文件名须唯一，写完整后原子重命名；每次检查 `summary.json` 的对应 action 结果后再提交下一条。

此入口不创建或覆盖 CXSorc，不发送窗口输入，不恢复/隐藏/最小化窗口，也不改变分辨率。窗口操作由 computer-use 完成。`native_cast` 仍会临时附加调试器、写断点/寄存器/空闲栈，仅允许既有 CXSorc 位于 Blood Moor；保存并执行该次留档 EXE，记录完整 helper 源码与 EXE 哈希。未确认调试清理则停止接受后续命令，不终止调试器。action 失败会使会话最终返回非零；`ready` 只代表会话就绪，不能当作游戏或视觉通过。

此入口目前仅通过 Python 编译检查；其新增运行时路径以本轮新目录的结果为准。已有隐藏窗口没有被恢复前，不能把它用于证明客户端画面有效。

### 一次 UAC 后的受限调度器（2026-11-13）

`orchestrator.py` 将原版 loader、可见窗口菜单入口、进程会话和 allow-listed native collector 组织在一个提权父进程下。它不是任意 shell：当前只接受 `native_cast` 和 `stop` 这两个已审查操作，拒绝未知 collector、未知角色和重复批次名。父进程通过 `CreateProcess` 启动原版，因此子进程继承同一管理员完整性级别，不会为每个批次再次弹 UAC。

首次启动由用户确认一次 UAC：

```powershell
& .\tools\original-observer\start-orchestrator.ps1 `
  -Job 'F:\3rd_src\riiablo\tools\original-observer\jobs\firebolt-smoke.json' `
  -Output 'F:\3rd_src\riiablo\tools\original-observer\runs\orchestrated-firebolt'
```

调度器使用 `-w -res800` 启动精确 loader，以 `--visible-run` 通过定向窗口消息自动完成 CXSorc→Blood Moor 入口；它不移动系统光标，也不要求窗口成为前台。每个批次使用唯一 JSON 文件名写入进程会话的 `commands/`，等待完整 action 结果后才提交下一批。失败时不强杀可能仍附着调试器的进程；报告会标记需要清理，保留 loader 供检查。

`jobs/firebolt-smoke.json` 是当前可执行的最小批次。它的 `scenario` 已进入调度报告，但现有 `d2game_cast.exe` 仍固定为经过验证的 CXSorc 1 级 Fire Bolt 夹具；扩展到 210 个技能前，需要按近战、投射物、范围、光环、陷阱、召唤、变身和被动技能增加经过 ABI 审查的 collector。调度器框架本身不宣称这些技能已通过。

首次端到端 smoke 成功记录在 `runs/orchestrated-firebolt-manual4/`：自动入口到达 Blood Moor，随后 native collector 完整施法并确认调试清理。该批次原版伤害为 `1461/256`，目标 HP 从 `256000` 降至 `254539`，拥有 1 条 Fire Bolt 弹道和 1 个伤害事件；`entry/summary.json`、`process-session/001-firebolt-level1/frames.csv`、`damage.csv`、`identity.json` 和 `orchestrator.json` 保存了证据。此结果是新的随机/位置样本，仍只代表固定 1 级 Fire Bolt，不能推广到其余技能，也没有视觉差分结论。

### 原版完整施法和首个实际命中差分（2026-11-13）

`test-character.py` 从已校验版本/校验和的存档创建另一个名字 `CXSorc`：法师、裸装、无佣兵、只学 1 级 Fire Bolt，HP/MP 各 1000。保留来源角色的等级 99、经验、任务和地图种子。原角色文件没有改写；`skill-session.py` 先备份安装目录和注册表实际 Save Path，再以 `xb` 安装新文件。原版加载、角色名/职业检查及后台走入鲜血荒地均已成功。实际 Save Path 是 `G:\BaiduNetdiskDownload\Diablo II\Save`。

`native-harness/d2game_cast.cpp` 在原版更新线程调用固定版本的怪物生成/属性/玩家模式函数，生成 class 0（skeleton1）独立目标，设 HP 1000、火抗/MDR/回复为 0，仅删除该目标的 AI/回复事件以保持静止。调用 `PLRMODE_ChangeModeToCoords` 的 CAST 模式（RVA `0x517D0`），由原版动画事件触发技能执行，并正常调度 80 帧；没有直接调用 Do 阶段代替完整动作，没有直接改生命扣血，也没有使用 burst。输入位置先通过已加载房间碰撞格检查。和旧控制器一样会临时修改断点/寄存器/空闲栈，运行后恢复并 detach。普通 PE/Capstone 核对了固定 DLL 的入口、导出和 ABI；本轮没有 REA MCP 连接，不把这些记录称为 REA Evidence。

实测证据：`runs/20261113-cast-session/009-cast/` 和 `010-cast/`。前一轮 `008-cast` 因选点过严在任何生成/施法之前停止；其清理先失败、fallback detach 成功，但旧汇总误保留了 false。控制器现已修正 fallback 成功时的返回值，后两次均确认清理。

- 两次原版完整施法都在相对第 7 帧生成唯一火弹，第 13 帧结算，第 14 帧回到站立。每批 81 个权威帧采样连续，无跳帧。
- 第一发火伤 1056/256 = 4.125 HP，目标从 256000 降至 254944（8.8）。第二发火伤 1389/256 = 5.42578125 HP，目标降至 254611。原版 `SUNITDMG_ExecuteMissileDamage` 入口的目标、施法者及伤害包与 HP 差额一致。
- 第二发命中前弹道 seed 两字 `(564996791,666)`、伤害端点 `(768,1536)`；命中后 seed `(567464301,235655850)`。只推进一次原生随机数即可解释 1389 点定点伤害。
- 观测最低 MP 为 255445；**这不是单独的耗蓝值**，同帧回复 85 抵消了部分 640（2.5 MP）的消耗。Java 接受施法时的 2.5 MP 成本另有断言；当前没有直接 hook 原版耗蓝 setter，不能把净变化当作耗蓝比较通过。

`FireBoltVerticalSliceTest.levelOneImpactReplaysOriginalMissileSeedAndExactHpLoss` 将原版记录的两字 seed 作为输入，运行 riiablo 的 ServerSkillSystem、伤害快照和实际 MissileCollisionSystem/HP 结算，不替换战斗计算器或随机返回值。初测得到 **1024** 而非原版 **1389**，暴露火弹仍使用共享兼容 RNG 和整数元素结算。当前只将 Fire Bolt 接入弹道自己的 RNG 和 8.8 元素结算。修复后该样本的伤害、目标 HP 和两个 seed 结束值精确匹配；再消耗 37 次共享兼容 RNG 应不影响结果。此 Java 夹具显式派发 Cast/Do，因此**尚未比较 riiablo 的动画事件时刻、弹道移动时间、目标实际模型碰撞，以及画面**。其他等级的完整命中、协同、精通、抗性和装备仍需独立样本，不能从这个 1 级裸装样本推广为全技能一致。

隐藏窗口 `PrintWindow(hwnd,...,3)` 返回 true，但图像全黑（只有一种字节值、非零字节为 0），已目视确认不可用。不能用该图做视觉通过证据。原版测试会话已停止、调试助手已退出，游戏仍存活且隐藏，桌面 1920×1080。原 summary 的 `window_hidden_after=false` 是异步隐藏后的即时检查竞争；退出后的独立 Win32 枚举确认实际隐藏。后续会话脚本已加入等待确认。

复验原版记录与失败门禁：

```powershell
python tools/original-observer/cast-trace-check.py `
  tools/original-observer/runs/20261113-cast-session/010-cast `
  --output tools/original-observer/runs/20261113-cast-session/010-trace-check.json
python -m unittest discover -s tools/original-observer -p test_cast_trace.py -v
```

判定器拒绝未清理调试器、错误角色/目标、跳帧、HP 与伤害不符、RNG 两字不符、目标移动和重复伤害事件。精确原生源码和 EXE 留在各次 `*-cast-artifacts`；Java 首次失败/修复通过日志是 `runs/20261113-firebolt-replay-before.log` / `20261113-firebolt-replay-after.log`。`playerCombatRegression` 已纳入 `*FireBolt*`，防止新基准只在手动选测时运行。

后续视觉路径已有静态候选：此 D2Win 的导出 #10168 / RVA `0xDCD0` 与 `D2Win_10168_WINMAIN_CreateScreenshot` 入口一致；D2MOO 对应实现经 `D2GFX_GetBackBuffer`（#10046）读取实际图形后缓冲，并写 JPEG。`VK_SNAPSHOT` 也映射到同一截图入口。静态记录在 `native-screenshot-candidate.json`，**尚未调用、尚未证明隐藏时可获取有效帧**；优先经窗口所属线程的截图消息路径验证，不把渲染函数放到服务端更新线程执行。

最终源码在 Java 17 下串行复验：火弹专项 5 项通过（其中 2 项是同一原版 seed 的基准/共享 RNG 干扰重放）；完整 `playerCombatRegression` 为 **1007 项、235 个套件，失败/错误/跳过均为 0**，已包含这 5 项。另有 19 项既有差分门禁和 9 项实际施法记录门禁通过。完整日志为 `runs/20261113-firebolt-full-regression-jdk17.log`，汇总、原始 trace 哈希、源码哈希和关键 JUnit XML 留在 `runs/20261113-cast-session/validation-summary.json` 及 `validation-artifacts/`。早先 Java 25 配置解析失败和并行编译导致 ClassNotFound 的批次均保留日志，后一批已中止，不纳入本次验证结论。不要并行执行共享编译输出的 Gradle 构建；当前 Gradle 7.3 改动构建脚本后应使用本机 `C:\Program Files\Java\jdk-17`。

种子函数 ABI 参考 `D2MOO/source/D2Common/include/D2Seed.h`，本机 DLL 导出由 REA `inspect-native-exports` 核对。两个 RNG 入口另经 REA/Ghidra API 分析及 Capstone 完整指令核对；原始函数使用 ECX 输入种子、EDX:EAX 返回两字，有界函数另使用 EDX 输入上界，两者均无外部调用。静态分析不能代替执行证据，实际调用结果以本地差分输出为准。

初次基线（2026-11-13）：战斗回归 1002 项中有 29 项失败、2 项声音探针跳过；原始证据保存在 `runs/20261113-headless-baseline/summary.json`，保留以便比较修复结果。

最终完整复测：怪物 AI 830 项、技能战斗 1003 项、原版资源 14 项，以及补充隔离/架构/渲染 32 项全部通过，失败、错误和跳过均为零。原先失败的 29 个用例逐项确认仍在报告中且通过。原版 DLL 种子状态 448 组、随机序列 90,112 次输出与两字状态均一致。各套件包含重复覆盖，数量不代表不重复用例总数。完整日志为 `runs/headless-final-full.log`；汇总与 XML 证据保存在 `runs/20261113-headless-fixed/`。

失败修复涉及以下几类原因：

- 旧夹具只控制 libGDX 随机数，而生产路径已使用原生随机流；现在显式提供伤害端点抽样，并为耐久消耗选取真实随机流的对应分支。
- 迁移对照中的物理减伤顺序、盾牌归属和 Weapon Block 状态未同步原生规则；保留其余迁移比较，另用独立规则测试验证。吸收治疗场景修正致死前置条件；Smite 补充盾击与 Holy Shield 的玩家生命周期证明。
- 爆炸箭缺失技能标识时未恢复火伤快照；兼容弹道抽样每次重建随机流；这两项修正生产实现。
- 光环、陷阱和渲染依赖违反已有模块边界。补充隔离测试又发现弹道伤害依赖全局属性表；现在显式使用所属世界的属性元数据，并验证复用时切换编码。

### 自然运行时调用栈追踪（2026-11-14）

`native-harness/d2game_runtime_trace.cpp` 是一个被版本哈希锁定的被动 helper。它不调用游戏函数、不注入 DLL，只对 `D2Game+0x86D0`（更新）、`+0x51A00`（目标技能模式）和 `+0x91260`（伤害入口）各设置一次软件断点，记录 EIP/ECX/EDX/ESP/EBP、返回地址、栈前 16 个 DWORD 和可识别参数，然后恢复原字节并 detach。`summary.json` 只有在实际写出 `trace.jsonl` 且 cleanup 确认后才会通过。

当前一次无人输入的验证已成功记录真实 update 入口 1 次，`detached=true`、`cleanup_confirmed=true`，原版进程继续存活。由于这次没有向游戏发送技能目标输入，目标模式和伤害入口没有命中；因此该证据证明了自然 tick 的运行时观测链路，不代表任何技能伤害或视觉一致性。下一步应增加经过坐标/窗口消息校验的后台技能输入，再复用同一 trace helper 捕获完整自然施法链。

### Bash 1–40 级运行采集（2026-11-14）

常驻管理员 broker 存活时，用下列命令提交整批任务，无需再次 UAC：

```powershell
python tools/original-observer/broker_client.py submit --op run_job --job barbarian-bash-target-levels1to40
```

helper 在原版更新线程上临时设置所选 Bash 等级，生成一个固定靶怪并通过目标 GUID 发起 A1，逐帧采集伤害事件、HP、MP、模式和击退。成功结束前，通过哈希与指令签名锁定的 `SUNIT_RemoveUnit`（D2Game RVA `0x8AEE0`）删除本次生成的靶怪，确认它已从服务端怪物哈希表移除，并在暂停的更新线程上恢复技能等级。没有这一步，连续生成的靶怪会占满周边碰撞格；已观测到第 8 或第 10 次因无法选点而停止，分批重开只能暂时缓解。

汇总时用 broker 返回的任务 ID 限定输入，防止混入旧样本或失败试验的其他等级：

```powershell
python tools/original-observer/summarize_barbarian_bash.py `
  --runs-root tools/original-observer/broker/<broker-session>/runs `
  --run-id <task-id> --require-levels 40 `
  --output tools/original-observer/runs/barbarian-bash-levels1to40.json
```

`--run-id` 可重复，用于明确合并断点续采的不同任务；也支持 broker `run_jobs` 的嵌套输出。未确认 debugger cleanup/detach 或采集失败的样本不进入基线；缺级时退出码为 1。单级基线另外使用 `--level 1`，不把高等级伤害混进 1 级分布。

早期报告把 `last_frame-first_frame=80` 命名为攻击周期，这是采集窗口长度。现改为从 `frames.csv` 的 A1→非 A1 模式变化计算攻击持续帧，从 `damage.csv` 计算相对伤害结算帧，并单列 `observation_frames`。本夹具 1 级实测为持续 16 帧、第 7 帧伤害结算、窗口 80 帧。

伤害最小值/最大值始终只是观测值；每级一次攻击不建立随机伤害分布或理论边界，合法 miss 单独记录。不能用少量样本的 min/max 区间作为随机伤害一致性的通过门禁。`compare_barbarian_bash.py` 的旧区间检查不作为精确伤害差分证据。

原版 `calc2` 是 HP，SrvSt32 在 AllocCombat 之后执行 `calc2 << 8`，但不重算已缓存的总伤害。本批 40/40 样本（包括 miss）满足 `physical_fixed-total_fixed=level*256`，HP 差额只等于 `total_fixed`。例如 40 级 physical=14628、total=4388；38 级 miss 的 physical=9728、total=0。Riiablo 现将这个延后字段保留在物理包中，从用于扣血的总量中排除；两条原版记录另有边界回归。它们显式供应已解析的基础伤害，不能冒充原 RNG 全流程重放。

Riiablo 的 `BarbarianBashLifecycleTest` 已覆盖 1–40 级真实 Actioneer 的开始/攻击关键帧路径，但其关键帧由测试主动派发；这不证明 Riiablo 自然动画时序与原版一致。最终伤害精确对比还需要相同武器、属性、减伤和 RNG 状态；画面、自然怪物 AI、协同与其他职业不由这批 Bash 数据覆盖。

### 旋风斩：首次原版连续采集与攻击时序修正（2026-11-14）

`barbarian-whirlwind` job 使用既有管理员 broker，自动重启测试 loader、进入 CXBarb 场景，然后运行独立的 `d2game_whirlwind` collector。它在原版更新线程添加真实 1 级技能节点，经坐标模式入口执行完整 SQ；临时生成 skeleton1，关闭其 AI/回复事件，设 1000 HP 和零相关抗性。装备检查固定为右手 hand axe（class 0）、左手 buckler（class 328）。该夹具不代表所有装备或等级。

连续断点覆盖 SrvSt38（D2Game RVA `0xCBFE0`）、SrvDo076（`0xCC4C0`）和伤害入口（`0x91260`），每次记录寄存器和栈前 16 个 DWORD。同帧多次回调也会保留。成功时移除临时目标和技能节点、恢复原技能选择、恢复断点并 detach；失败记录不作为基准。失败时不保证游戏夹具已恢复，下一任务须按 restart 生命周期重新进入。DLL 哈希和入口签名检查仍由 collector 执行。

成功原版任务为 `20261114-144735-46386fb4` 和补充 FP16 坐标的 `20261114-145618-0d46a215`。中间一次任务 `20261114-145344-600d657c` 因旧 loader 退出超过等待时间，在采集前失败；确认旧 PID 已退出后重试成功。结果见 `runs/whirlwind-single-axe-level1.json` 与 `runs/whirlwind-single-axe-level1-fp16.json`，原始 CSV、调用栈和身份记录留在报告的 `source` 路径。

两次均观察到完整施放 60 帧、Start 1 次、Do 55 次。攻击检查窗口为相对帧 `3,7,15,23,31,39,47,55`；前两次间隔 4 帧，后续手斧间隔 8 帧。实际目标伤害事件在 `3,7,15` 帧，之后目标离开攻击范围。第一批三次命中、合计 `2728/256` HP；第二批两次命中一次 miss、合计 `1534/256` HP。每帧 HP 与伤害包总量一致，最低法力相对施放前下降 `6400/256=25` MP，临时夹具恢复和调试清理均确认。

FP16 补采确认前两帧坐标完全未变，第 3 帧开始移动，首步 `24576/65536=0.375` subtile；path velocity 为 1536。第 56 帧最后一次位移，第 60 帧才回到站立。Riiablo 当前移动开始和到达后的状态收尾尚未按这条记录对齐；不能仅凭攻击窗口一致宣称路径、施放总时长或客户端动画一致。

新增 `BarbarianWhirlwindTest.originalSingleAxeAttackWindowsStartAtFrames3And7ThenUseWeaponInterval` 在旧实现上实际失败，记录为 `1,5,13,21,29,37,45,53`。现将首检查延后至第 3 帧，保留初次检查后 4 帧和后续武器断点调度。该时序测试显式固定测得的 8 帧间隔，属于调度隔离回归；装备/动画算出的间隔另行验证。没有对齐原生 RNG 或逐击伤害，亦未验证双持、其他等级、其他武器、完整自然动画或截图。

`check_whirlwind.py` 校验帧连续、技能/装备身份、伤害目标、每帧 HP、回调地址/参数/栈长度、清理状态，以及可选 FP16 坐标的一致性。`test_whirlwind.py` 包含正例和错误目标、错误装备、跳帧、HP 不符、未恢复夹具、FP16 不符/缺列等负例。

用户指出耗蓝可能不一致后，新增真正经过 `ServerSkillSystem` 的 SkillCast 验证，实际复现了地面旋风斩耗蓝为 0、且 `25-1/256` MP 仍接受施放。原因是通用 `TargetableOnly` 空挥免耗蓝分支错误地包含了 SrvSt38；旋风斩的原版行带该标记，但坐标目标仍执行完整技能。现为 SrvSt38 保留正常耗蓝。此前只派发 SkillStart 的旋风斩测试绕过了这层，不能用来证明耗蓝一致。

资源补采任务 `20261114-150656-a84e2745` 再次通过，`resources.json` 记录施放前/最大 MP 均为 256000，manarecovery/manarecoverybonus 均为 0。施放立即扣 6400（25 MP），之后 60 帧每帧回复 85（`85/256` MP），结束时为 254700（994.921875 MP），因此结束时的净下降 5.078125 MP 不等于技能成本。新报告 `runs/whirlwind-single-axe-level1-mana.json` 分开列出 immediate cost、final net drop 和逐帧回复增量，避免混用。新增 Java 测试分别验证真实校验扣一次 25 MP、后续攻击窗口不再扣、25 MP 边界，以及同资源条件下的 60 帧回蓝序列；其余等级仍需原版独立采样。

### 职业技能动画差分工具链（2026-11-14）

动画差分流程、JSONL schema、原版转换、Riiablo exporter 和判定边界见 `ANIMATION_COMPARISON.md`。`animation_compare.py` 支持把 D2Game 样本转换成服务器时序 trace，也支持现有只读 live-observer 的 D2Client `mode/animation_frame_raw` 样本。缺少 D2Client COF 帧、关键帧或渲染摘要时，比较器返回 `incomplete`，不会把服务器时序通过冒充完整动画通过。

联合采样任务 `jobs/barbarian-whirlwind-animation.json` 已实际运行两次：原版
旋风斩 collector 均成功完成（60 个服务器帧、清理和 detach 均确认），并行的
D2Client observer 每次写出 300 个只读样本和 `animation-trace.json`。由于该
collector 直接调用 D2Game 技能入口，客户端玩家模式两次都只观察到 `NU (1)`，
没有观察到 `SQ (18)`；报告中的 `client_animation_skill_mode_observed` 为
`false`，因此该批次只能作为采集链路验证，不能作为技能动画一致性通过。

### 客户端可见旋风斩输入已打通（2026-11-14）

新增独立存档 `CXBarbWW`，技能区含 1 级旋风斩，F1 槽使用 1.10f 的两个
16 位字段格式保存技能；整个 DWORD 直接写入会使角色无法加载。任务
`jobs/barbarian-whirlwind-client-animation-input.json` 在后台发送 F1 和窗口
定向右键地面输入，同时运行只读 D2Client observer 与 passive runtime trace。
成功运行 `20261114-163023-794c0495` 的客户端 trace 观察到：

- 模式序列 `NU (1)` → `SQ (18)` → `WALK (5)`；
- `SQ` 连续 33 个采样（约 1.69 秒，40ms 间隔）；
- `animation_frame_raw` 在 `SQ` 区间出现 6 个取值，范围 `0..1536`；
- native trace detach/cleanup 均确认，前台窗口未被激活。

这已经是原版客户端技能模式和帧游标的有效证据，但仍缺 COF token、总帧数、
关键帧数组和同步渲染截图，所以比较器仍应保持 `incomplete`，不能宣称完整
画面一致。
