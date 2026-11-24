# 德鲁伊小旋风/龙卷风：客户端基线与后台采集准备

## 后续完成：路径与外观方向

后续已修复目标X种子、原生两格节点/定点速度、独立客户端风团及固定外观方向，并让本地伤害载体同步使用原生路径。221个客户端路径状态和221个本地权威路径状态均匹配；42项不同回归分批通过。完整范围、随机外观和原版服务端伤害限制见 [路径/方向研究](druid-wind-path-direction-analysis-2026-11-21.md)。以下动画阶段的“轨迹尚未修复”等表述保留为历史记录。

## 最新更新：客户端速率和循环修复

复用后台run `20261121-165203-4158d94f`，本阶段没有启动新的原版实例。两个技能的221个去重客户端状态已导出到 `core/src/test/resources/original/druid-wind-animation-1.10f.json`；这是非原子内存读取到的动画游标，不是DCC提交或GPU像素。

原版小旋风速度为256（每tick一帧），年龄36回到18；龙卷风速度为512（每tick两帧），年龄18就回到12，之后每12tick完成一遍[12,36)循环。原版技能回调表 `0xD75A8` 的68/69项分别指向RVA0x22B30/0x22BE0；它们向同一创建函数0x22900分别传入0x100/0x200，与运行状态完全对应。这是技能创建时的显式速率覆盖，不能只读取Missiles表默认animrate1024后统一转换为256。

两个资源DCC均41帧，而表AnimLen40。旧版实际生产回放在年龄36..40播36..40、年龄41才进入循环，还存在浮点/初始化重复推进的年龄偏差。匹配同一角色技能点、出生格和目标格后，旧版小旋风144个可配对状态中93个帧不同，龙卷风73个中70个不同；原始合成坐标基线不参与这些统计。

新 `NativeDruidWindPresentation` 限定Twister240/twister与Tornado245/tornado组合，使用技能回调速率、表AnimLen和排他SubStop，在原生整数年龄上直接选帧。循环在SubStop边界立即回到SubStart，不先播完整贴图。倒计时不大于 `AnimLen-SubStop+1` 时从**当前游标**继续普通推进，不臆造跳到36的完整收尾。原版龙卷风年龄70/71/74分别为20/22/28，末段仍可能是循环中的资源帧。

MissileLoader负责首次/晚到资源发布；AnimationStepper对这两项只恢复当前原生年龄的帧，避免加载同tick再推进一次。远程副本用transient `DruidWindAnimationClock`在没有DCC时照常积累本地时间，使用现有clientFrame发布，不推进收到的nativeFrame，不改移动、战斗、寿命或网络格式。60Hz渲染累计为25Hz年龄，旧快照不会把本地动画倒回；本地权威单位直接跟随服务端nativeFrame。

验证分两层：全部221个采集游标/rate与生产时钟策略匹配；实际生产系统回放可配对的217个已加载年龄均匹配，4个年龄零的出生状态尚未经过Loader发布，单独列为unloaded_birth_age_samples，未伪装成渲染通过。晚加载/始终未加载的导弹均在原有50/75tick结束，晚加载恢复当前帧；其他技能保留原逻辑。机器对照见 `druid-{twister,tornado}-comparison-1.10f.json`，旧生产输出保留 `*-production-before-native-wind-1.10f.json`。

最终 **29项相关检查分批全部通过**：保留 `druid-wind-animation-final2-validation.log` 中六个未变化套件的24项通过结果（XML冻结于 `druid-wind-passing-regression-results/`）；修正副本测试环境后，`druid-wind-replica-final-validation.log` 的5项wind套件和正式生产回放通过，合计29个不同测试。副本测试环境先后遗漏map、OverlayManager注册及authoritative=false，三个失败日志均保留，没有当作通过结果。最终机器汇总为 `druid-wind-animation-validation-2026-11-21.json`。模块边界、Python编译、git diff --check均通过；不是全仓库测试或像素验收。

正式机器对照：小旋风144个已加载样本差异93→0；龙卷风73个差异70→0，数量3/1、寿命50/75匹配。四个资源发布前出生年龄已单列。职业覆盖更新为4项停止状态/DCC采集、2项只读客户端轮询，限定对照6/30；两项wind均标 `compared_client_clock_with_remaining_differences`，未把只读轮询扩大为实际绘制调用采集。

本轮只修复动画时钟。客户端初始path方向在每个采集单位内保持不变，而velocity会折转；当前主机方向解析仍可能随速度转动，**没有采到真实DCC方向，暂不宣称方向一致**。原版速度是fp16轴向30720/24576每tick，主机使用浮点移动及不同转向间隔，完整漂移路径仍需另行对齐。未改写服务器伤害或把主机空地图计数当原版服务端证据。后续优先重建这两项的本地客户端移动/方向，再扩展德鲁伊其他技能。

## 后续更新：接收器恢复、只读后台采集成功

用户返回后授权由助手启动接收器。普通沙箱内启动仍返回Win32 5；在沙箱外执行同一已授权启动命令后成功请求UAC，新接收器PID25512、管理员权限、ready和心跳均确认，session为 `20261121-165133-4490d598`，恢复命令已加载。因此此前的自动启动错误不能直接归因于Windows配置；沙箱外启动已解决。此前游戏调试附加失败原因仍未单独证明。

run `20261121-165203-4158d94f`：后台只读轮询批次通过，同一原版进程4832、同一客户端人物地址，私有桌面 `riiablo_molten_52610cdeb175` 未显示，输入桌面与屏幕尺寸检查通过。小旋风3个、原始378条/去重147个状态，寿命50tick；龙卷风1个、原始189条/去重74个状态，寿命75tick。两个技能结束时客户端残留均为零，所属游戏已自动关闭，接收器返回ready、active=null、cleanup_blocked=false。

已由 `export_druid_wind_polling.py`导出 `runtime-wind-polling-summary.json`和两项replay-input JSON。状态为 `captured_client_polling`，实际DCC选帧/绘制提交证据数为零；下方“尚无有效样本”描述保留为本轮早先阶段记录，现在已有客户端状态样本，正式生产对照尚待执行。暂不把旧覆盖统计中的4项完整采集与新增2项只读客户端采集合并成相同口径。

本轮推进Twister240/Tornado245，目标仍是补齐客户端动画步骤。已完成工具扩展、固定双技能角色、静态代码定位和真实Riiablo生产基线；**尚无本轮有效原版技能运行样本，尚未改写这两个技能的生产动画**。职业运行覆盖维持4/30，不能因增加工具或静态数据而计为6/30。

## 已确认的本地实现和静态差异

裸装双技能配置 `CXDruWind`：Twister、Tornado各一点，其他硬点为零，固定地图种子127897359，F1/F2分别选两项。只创建独立测试存档，已有同名存档必须通过身份、技能、地图、空装备和校验和验证，拒绝覆盖不匹配存档。

真实生产管线从SkillDo启动：Twister生成3个、寿命50tick，Tornado生成1个、寿命75tick；使用已有seeded charged-bolt路径。基线不是原版复现：出生(100,100)、目标(108,100)均为明确合成输入，空地图无目标，不证明轨迹或伤害一致。两个JSON均标 `source_kind=synthetic_production_baseline_no_original_capture`，`source_run_id=null`。

客户端资源实际均41帧；Twister4方向，Tornado1方向。Missiles表AnimLen=40，与实际DCC41帧不同。Twister循环区间[18,36)，Tornado[12,36)，animrate=1024。

当前 `Animation.getFrameByIndex` 在完整DCC帧数结束后才进入子循环。基线中两项在年龄36..40继续选择36..40；年龄41才回到18或12。另有加载后首次选帧相位现象，年龄1时为第2帧，需要原版年龄/更新顺序验证，不能直接按画面猜改。

哈希固定的D2Client CltDo1（RVA0x9D880）在启用SubLoop时，以当前倒计时和 `AnimLen-SubStop+1` 决定继续子循环还是普通推进；循环调用D2Common#10375。该函数在下一游标到达SubStop时立刻减去SubStop-SubStart，不先播完整个DCC。普通推进#10374按unit+0x48的表AnimLen回绕。因此“先播完41帧再循环”与原版静态路径不符，不能仅按DCC帧数循环，也不能在寿命末尾无条件把游标跳到36。

静态证据还表明循环退出依赖剩余寿命；退出时的确切游标、绘制相位和实际提前移除仍需运行采样。小旋风方向、charged-bolt漂移路径、速度比例、冷加载恢复与绝对出生延迟也尚未完成原版核对。

`inspect_druid_wind.py`验证D2Client/D2Common SHA256、导弹回调表slot1与Common导出地址后，从对齐入口导出 `druid-wind-static-1.10f.txt`。其中CltDo1、动画更新和绘制函数是静态观察；不是执行轨迹、不是REA Evidence，也不是绘制已发生的证明。两个技能的skill cltdofunc分别68和69，与导弹pCltDo1是不同回调表。

## 后台尝试、失败与清理

全部原版尝试均在未显示的 `riiablo_molten_*` 私有桌面，输入桌面保持Default、分辨率保持不变。没有前台唤醒、桌面切换、全局鼠标键盘、强制渲染或GPU截图。

- `20261121-160225-314acbe0`：新任务通过background_probe白名单，但独立角色白名单遗漏CXDruWind，在游戏启动前拒绝。已补齐角色白名单，失败未计为采集。
- `20261121-160334-6c47f292`：自动进入Blood Moor成功；新采集器DebugActiveProcess返回Win32 5，未附加、未施法、无样本，游戏已关闭。
- `20261121-160659-416c2046`：增加附加前检查后重试。helper_elevated=true，CheckRemoteDebuggerPresent成功且target_already_debugged=false；启用SeDebugPrivilege仍获Win32 5。无技能样本，游戏已关闭。不能直接判定是杀毒软件，也没有关闭任何系统保护。
- `20261121-161131-36cbbaa7`：改用不附加调试器的只读Python轮询，冻结副本导入orchestrator失败，采集器尚未建立输出。主worker因无summary进入保守清理锁，保留了所属游戏。确认helper已退出后关闭本任务记录的loader PID16192；没有对其他进程操作。根因已改为collector自包含常量及桌面名称读取，增加启动前 `--help`导入预检；只读collector没有summary时不应误判为“仍持有调试器”。
- `20261121-161347-c52c6924`：旧broker内存中的cleanup_blocked继续拒绝任务；写状态文件不能重置其内存锁，未视为已恢复。

旧broker2616通过已有shutdown命令正常退出；接收器新版补了 `recover_cleanup`。该命令仅在没有原版loader且未清理worker所记录的game/helper PID均已退出后解锁，不能证明退出、PID仍在运行或访问被拒绝时均保持锁，不杀进程、不解除调试器。

自动启动新broker返回 `UAC startup failed/cancelled: 5`，没有成功弹出UAC，当前没有响应中的broker或原版实例。已向用户请求管理员PowerShell重启接收器。新发现路径 `broker/20261121-161625-91e1b395`只是启动尝试目录，没有成功运行状态，不能标为ready。

## 就绪工具与后续顺序

`d2game_druid_twister.cpp`、`d2game_druid_tornado.cpp`已构建并加入x86清单，限定角色/技能/版本/初始走廊，原版输入施法与被动绘制探针沿用现有机制；本机当前附加被拒绝，不继续无变化重试。

备用 `druid_wind_passive.py`仅OpenProcess查询/读取权限，在私有桌面通过指定HWND施法，10秒有界轮询客户端hash中的477/478单位。记录原生坐标、速度、方向、寿命、动画游标和随机种子，拒绝部分明确跨更新读取；不挂调试器、不暂停、不写内存、不调用内部函数、不截图。**它仍是非原子采样，可能混合更新或漏tick，游标不能冒充DCC提交选择。**

重启接收器后运行 `druid-wind-observe-private-desktop`。同一进程依次两技能，结束时检查客户端无本技能残留，正常关闭所属游戏。

成功结果由 `export_druid_wind_polling.py`验证桌面、分辨率、进程、技能、角色、清理和源哈希，导出不同于DCC采集的 `runtime-wind-polling-summary.json`；实际绘制选择数明确为0。不得用原DCC exporter强行补帧。若调试器路径以后恢复，可用 `export_class_batch.py`和 `prepare_class_batch_replay.py --wind`。

`DruidWindOriginalAnalysis`复用实际ServerSkillSystem、MissileCollisionSystem、MissileLoader、AnimationStepper、DirectionResolver，按记录技能点回放；GPU方向加载mock。当前仅生成 `druid-{twister,tornado}-production-static-baseline.json`；原版采集成功后再生成正式production文件并按年龄核对，保留静态基线，不伪造run_id。

本轮限定回归 **17项通过，0失败、0错误、0跳过**；包括DruidWindStreamIntegrationTest、DruidWindCollisionTest、MissileAnimationCatchupTest、DruidSurfaceOriginalParityTest。模块边界通过、Python编译及collector --help导入预检通过、git diff --check通过。日志 `tools/original-observer/druid-wind-baseline-validation.log`；不是两项技能已与原版一致的证明。

后续先验证循环边界、倒计时退出、动画相位及方向，再单独核对移动。服务端Tornado区域伤害频率在本级寿命75与间隔15下无法区分elapsed/countdown相位，不借此次静态分析直接改伤害调度。未提交或推送本轮修改。
