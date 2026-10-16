# 会话资源生命周期

本阶段接在七批子系统装配之后，独立处理资源归属与会话退出；不改变装配顺序、
25Hz、玩法、协议或存档格式，不以释放方法存在与否判断资源归属。

## 第一批：UI / 调试 GL 资源归属与释放规则

- `GameScreen` 创建并拥有主 `stage` 与 `scaledStage`，二者构造时都借用
  `Riiablo.batch`。退出时先释放各自顶层 `Disposable` Actor，再释放 Stage；
  只取 Actor 快照，不递归销毁任意子控件或 style 共享资产。缩放 Stage 上的
  `NpcDialogBox.dispose()` 会停止其语音句柄。`hide()` 只解除可见订阅，不释放 Stage。
- libGDX `Stage(viewport, batch)` 不拥有传入的 batch；其 `dispose()` 清理 Stage
  状态但不释放共享 batch。两个真实 Stage 共用 mock Batch 的测试锁定这一约定。
  Client 仍负责共享 batch；本批不提前销毁它。
- `Box2DDebugger` 拥有自己创建的 `Box2DDebugRenderer`，但借用注入的
  `RenderSystem`、`Box2DPhysics` 与相机服务；只释放自己的 debug renderer。
- `ProfilerSystem` 拥有 `new Stage()`、`ShapeRenderer` 与从
  `profiler/uiskin.json` 创建的 Skin。这里 Stage 自己创建并拥有 batch，与
  GameScreen 两个 Stage 不同；按 Stage、ShapeRenderer、Skin 顺序释放。
  不释放 Client 的 batch、shapes 或字体。
- 系统清理前先清空私有资源引用；GameScreen 保留既有 disposed 防护。
  重复调用不重复释放，释放失败后也不自动重试已经尝试过的资源。
- `Disposal` 是一次短生命周期清理过程，不是资源注册表：按调用顺序尝试所有操作，
  最后抛出第一个 RuntimeException / Error，后续失败作为 suppressed 保留。
  不推断所有权、不去重、不重试、不静默吞错。
- 角色保存、CharData 监听清理、Automap 保存、World、UI、预载资产保持正常路径的
  原有相对顺序。某项失败仍继续尝试剩余项；全局 game/engine 别名仍按身份清理。
  预载资产按加载次数逐条 unload，不因文件名相同而去重，避免破坏引用计数。

## 第一批地图 / DT1 审计记录

`Map.dispose()` 会释放 Zone 并清空 DT1s 索引；`DT1s.clear()` 本身不销毁贴图。
Zone 的依赖由 `Zone.load()` 通过共享 AssetManager 加载，Zone 释放时又经全局
AssetManager 卸载。`DT1Loader.loadSync()` 准备贴图并把 DT1 交给 AssetManager。
`DT1.dispose()` 仍受静态 `loadData` 控制；贴图/Pixmap 与失败路径尚需逐项核实。

因此本批保留 GameScreen 中原有 `map.dispose()` FIXME：不能直接手工销毁 DT1，
也不能未经实际 load/unload 配对验证就启用整图卸载。后续先明确原 AssetManager、
实际加载次数、未加载/部分加载 Zone 与换 Act 的边界，再验证共享资产的引用计数。
以下第二批继续落实此审计；GameScreen 的退出卸载开关仍等待完整会话重进验证。

## 测试范围与明确限制

`DisposalTest` 检查顺序、异常保留、Error、重复 close 和不去重。
`GameScreenSubscriptionsTest` 调用生产 dispose，覆盖两 Stage、Actor 自移除、异常后
继续清理、资产重复引用及重复销毁；使用真实 Stage / native 矩阵、mock GL 与 Batch，
不创建真实 GL 上下文。
屏幕重资产构造被绕过，不能据此声称完整 UI 创建或重复游戏会话已经验收。
`Box2DDebuggerDisposalTest` 与 `ProfilerSystemDisposalTest` 用 mock GL 资源检查生产
清理方法；另通过真实 Artemis World 验证禁用系统也收到 dispose，initialize 用替身。

真实资源冒烟使用现有 `:desktop:offscreenCamp` 与 D2GS 固定 Tick / Throw 专项；
它们不等于人工画面/听感、完整同进程退出重进、GL 句柄计数或长期泄漏验收。
运行结果和日志见 [进度路线](d2moo-progress-roadmap.md) 本阶段章节。

另一台电脑可独立运行本批专项：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.util.DisposalTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.engine.client.debug.Box2DDebuggerDisposalTest `
  --tests com.riiablo.profiler.ProfilerSystemDisposalTest `
  :desktop:compileJava :server:d2gs:compileJava --no-daemon
```

## 第二批：地图依赖引用与 DT1 生命周期

- Zone 的 `dependencies` 只描述配方，不再作为卸载凭据。`load()` 捕获当时的
  AssetManager，并逐条记录成功提交的加载。相同 Zone 重复 load 不再叠加引用；
  配方中的同名条目仍分别提交、分别释放，不去重破坏既有引用计数。
- 仅计算依赖/生成地图但未加载，不卸载任何资产。提交中途失败时回滚已提交部分，
  保留原错误与清理异常；整张地图先前已成功加载的其他 Zone 仍由 Map.dispose 管理。
- finishLoading、Preset 的 DS1 读取及最终卸载均使用父 Zone 捕获的原管理器，
  不因 Riiablo.assets 后续改变而读取或卸载另一宿主的资产。其他地图代码的全局依赖
  未因此全面隔离；不能据此支持多 World 任意并行。
- libGDX 1.10 `AssetManager.unload` 对活动任务按文件名取消；对同名多消费者直接
  反复 unload 不能保证每次扣减一个引用。Zone 清理先完成在途的已提交加载，
  再逐条卸载；已因异步失败而被管理器移除的条目无需再卸载。
  这是有意的同步等待，加载中退出可能变慢；必须在原资源宿主仍有效的线程执行，
  有 GL 数据时必须在 GL 线程执行。它不是通用异步取消协议，DT1Loader 的取消
  回调/跨线程回滚未在本批重写。
- Zone 卸载失败仍尝试其余条目；Map 继续处理其余 Zone 并清空 DT1s/图索引。
  清理记录先移走，重复调用不重试失败资源。池化 Zone 清空 map/level/type，
  池化 tile 数组在归还时清空，避免静态池保留旧会话与已释放贴图引用。
- DT1 按实例真实持有资源释放 Texture 与 Pixmap，不再受释放时静态 loadData
  开关影响。TextureData 使用 disposePixmap=false，CPU 像素属于 DT1；释放时
  清空 tile 的 pixmap/texture 引用，重复 dispose 幂等，释放失败继续其余资源。
  dispose 后拒绝重新 prepareTextures；再次加载必须由 AssetManager 创建新 DT1。
- 解码中途失败释放已创建的 Pixmap；贴图准备中途失败释放已经成功创建的 Texture
  和全部 CPU 像素。本批不保证第三方 Texture 构造函数内部尚未返回时的 GL 失败
  可全部回滚，也不处理任意外部直接操作 AssetManager 的所有失败/取消路径。

新增 `MapAssetLifecycleTest` 使用真实 AssetManager/同步及异步加载器检查引用计数、
在途共享引用、未加载、重复加载、失败回滚、换 Act、原宿主及池引用；地图生成器
为小型夹具，不是完整 Act 生成。`DT1LifecycleTest` 使用真实 native Pixmap、
mock GL/Texture 验证幂等/开关改变/失败清理，另有截断解码清理用例。

新增 `:desktop:offscreenMapAssets` 用原版 MPQ、真实 DT1Loader 和隐藏 1x1 GL
上下文，在同一进程、同一 AssetManager 上跑三轮双 Map 加外部引用；检查
4→2→1→0、DT1 实例更新、Pixmap.isDisposed、glIsTexture 和 glGetError。
此入口不构造完整 GameScreen、不做地图生成或屏幕切换，因此只是地图资产重载验证。

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.map.MapAssetLifecycleTest `
  --tests com.riiablo.map.DT1LifecycleTest `
  :desktop:offscreenMapAssets `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

## 后续边界（第二批完成时的历史记录）

下一批开展完整同进程多次会话退出重进验证，再启用 GameScreen 的 map.dispose。
目前仍保留该历史 FIXME，正常退出的整图卸载尚未接通，不能宣称地图退出泄漏已解决。
网络屏幕自身的 socket / HostedD2GS 停机幂等与失败后继续清理、Client 的完整共享
资源及屏幕栈清理、构造/初始化失败后的自动回滚均尚未完成。
本批没有消除 UI 内部的 Riiablo 全局依赖，预载资产卸载也尚未绑定原 AssetManager，
不能据此宣称任意宿主替换安全、全部资源无泄漏或全面会话隔离。

## 第三批：GameScreen 整图退出与加载屏生命周期

`GameScreen.dispose()` 现在在保存角色/Automap、销毁 World 和两个 UI Stage 后，
调用 `Map.dispose()`，然后清理加载屏和会话预载。异常后继续尝试其它资源的规则不变。
地图资源在消费者退出之后、Client 的 AssetManager 与 GL 上下文销毁之前释放，
不再保留普通会话退出的历史 map.dispose FIXME。

新增包内 `ScreenAssetScope`：捕获创建时的 AssetManager，只记录成功提交的 load，
每次提交对应一次 unload，不按文件名去重。未显示的屏幕不会卸载尚未提交的预载配方。
预载的加载、Warriv 备用模式的预热和最终卸载归入同一会话 scope；原始配方仍由
`getDependencies()` 提供。清理先完成仍在途的同名资产，再逐引用卸载，沿用上一批
libGDX 1.10 的处理约束：可能同步等待，必须在资源宿主存活时、GL 线程上调用。

`GameLoadingScreen` 的构造不再获取贴图或创建 Stage；每次 show 创建临时 Stage，
持有一份加载图引用，hide/dispose 释放二者。Client 在 pop 后可能调用 dispose，
但同一 GameScreen 后续换 Act 仍会复用这个 loader，因此这里的 dispose 仅结束
当前显示周期，不永久封闭 loader。会话预载仅提交一次，最终由 GameScreen 释放。
show 中途失败会清理已创建的临时资源；不等于整个 GameScreen 构造/初始化自动回滚。

### 同进程生产验证入口

新增 `:desktop:offscreenSessionLifecycle`。同一 Client、AssetManager 和真实 1.10f
MPQ/GL 上下文中三轮构造生产 GameScreen，经加载屏、Act I DRLG、营地实体和真实
渲染后，通过 Client.setScreen 退出，再进入下一轮。退出目标是无资源占位屏，不是
实际菜单/角色选择屏；每轮输出独立营地 manifest 和生命周期报告。

入口检查旧地图/Stage 清空、按键与输入处理器解除、全局 game/engine 别名清空、
加载图、windowopen 和 MaxiMap 三种屏幕专属预载的引用数平衡、DT1 从管理器移除、
原生 Pixmap 释放及 GL 句柄删除；
后续会话必须获得新的 DT1 实例。重复 dispose 不重复释放。它不宣称全部 UI 内部
引用归零，不替代人工画面/听感、长时间运行或网络会话验证。

```powershell
.\gradlew.bat :desktop:offscreenSessionLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/session-reentry-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/session-reentry-camp' `
  '-Pd2Version=1.10f' --no-daemon
```

### 新暴露的独立 UI 引用问题

首次真实验证发现构造 UI 后，退出仍残留 `data/global/sfx/cursor/button.wav`
引用。`Button` 构造各自 load，而 GameScreen 只清理顶层 Disposable Actor，
嵌套按钮的 dispose 未被统一配对。验证必须区分 UI 构造自身的引用和稍后 show
提交的会话预载，不能把前者算作后者；顶层控件又会在退出时正确释放其中一部分，
因此也不能要求整个管理器的共享音效引用等于构造后的计数。真实入口只对上述三种
专属预载做基线断言，逐提交/重复文件名的 scope 配对另有专项测试；不把共享音效
包装为通过。生命周期报告单独记录构造前、构造后与退出后的按钮引用数。
此问题本批不扩展处理，后续需要明确控件/面板资源所有权，不能简单
递归 dispose 所有子控件（会与已有面板的子资源释放重叠）。

网络屏幕 socket / HostedD2GS、Client 完整屏幕栈及共享资源、GameEntryLoadingScreen
自身资源、完整构造回滚、真实跨 Act/加载中退出、长时间多轮压力测试仍未验收。

### 第三批验证记录

- 新增 8 项专项，扩大定向回归 309/309、45 类通过：core 267/267（38 类）、
  D2GS 全部 42/42（7 类），失败/错误/跳过均 0。范围沿用第二批 301 项并新增
  scope、loader 和整图清理顺序覆盖；未纳入两个既有玩法/AI 基线失败。
- 同进程完整生产会话三轮全部通过上述限定检查：每轮 58 个 DT1 资产、2626 张
  地图贴图及其 Pixmap 退出释放，下一轮新实例；输入处理器回到基线 2。
  专属预载回到显示前引用基线，重复 dispose 安全。报告
  `build/session-reentry-camp/cycle-1-lifecycle.txt` 至 `cycle-3-lifecycle.txt`；
  每轮营地渲染三帧，独立 manifest 在相应 cycle-N 目录。
- 按钮引用实际为构造前/构造后/退出后：第一轮 0/129/105，第二轮
  105/234/210，第三轮 210/339/315，明确是未解决的独立问题。
- 独立地图资产 GL 三轮继续 PASS：Cavedr.dt1 44 tiles，4→2→1→0。
  日志 `build/session-reentry-regression.log`。初轮编译的包私有字段访问、
  Animation 测试夹具 Colors 初始化、路径规范化及共享音效基线归因错误均已
  修正；保留初轮失败日志，不把这些失败运行视为成功。
- 上述 309 项、两种三轮真实 GL 检查、原单轮营地/Client 退出、D2GS 25Hz
  （step=0.04）和 Throw 导弹顺序/伤害 1000→992 在同一命令完成，最终
  `BUILD SUCCESSFUL`，core/desktop/D2GS 编译通过。日志仍为
  `build/session-reentry-regression.log`，未出现 ERROR/Exception。
  保留 level0/DrlgType0 跳过、Java 分配回退、表 Key 推断等既有诊断；四次
  SIM_SLOW 为 107.33 / 58.70 / 57.31 / 112.39ms（预算 40ms），不作性能验收。

## 第四批：嵌套按钮音效与动态快捷栏清理

`Button` 每次构造获得的一份 button.wav 引用现在绑定原 AssetManager；播放和
卸载均使用该宿主，重复释放不重复扣减，释放后不再播放缓存的 Sound。清理在途加载
沿用前述先完成、再卸载的约束，可能同步等待，要求原宿主仍有效的主线程/GL 线程。

新增 `Button.disposeClickSounds(root)`，先快照整棵子树中的 Button，再仅清理
Button 基类自己的音效引用。包含隐藏控件，但不调用任意 Actor 或 Button 子类的
dispose，不释放共享 style/贴图/batch。GameScreen 在原有顶层 Actor 清理之前调用，
面板既有的按钮 dispose 仍安全，其他子资源仍由原面板负责，避免通用递归销毁重叠。
单纯 detach、排序时 clear/readd 不代表终止生命周期，不自动释放。

`SpellsQuickPanel` 在技能变化重建和最终 dispose 时，先按每行实际控件树回收旧
按钮音效，再清空旧 Actor/Cell 和索引；不能仅遍历按 skill id 索引的 map，因为
充能技能和普通技能可能同 id 覆盖，导致漏掉仍存在的按钮。排序重排不释放按钮。
某行音效清理失败仍清空该行并处理余行，最终 dispose 仍尝试全部按键监听和图标，
保留首异常及后续 suppressed，不对失败引用重试。

新增 ButtonLifecycleTest 11 项、SpellsQuickPanelLifecycleTest 2 项，并加强现有
GameScreen Stage 测试的隐藏嵌套按钮覆盖。真实会话入口每轮额外触发三次技能更新，
要求运行中音效引用不累积，并要求退出回到 UI 构造前的基线；上一批的单独诊断现在
升级为硬断言。仍不宣称所有 UI 资产或 Client 全部资源都已归零。

三轮生命周期入口共用同一进程，不应共用单轮 camp 的 120 秒总看门狗；该入口
现在按三轮给出 360 秒进程上限，普通单轮仍为 120 秒。资源断言、轮数和帧数不变，
这只是失败进程的外部终止预算，不是放宽 40ms Tick 预算或性能验收门槛。

本批最小复跑（扩大回归另含前述装配/音频/地图测试）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.screen.panel.SpellsQuickPanelLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  :desktop:offscreenSessionLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/ui-buttons-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/ui-buttons-camp' `
  '-Pd2Version=1.10f' --no-daemon
```

### 第四批验证记录

- 新增 13 项专项通过，扩大定向回归共 322/322、47 类：core 280/280（40 类）、
  D2GS 全部 42/42（7 类），失败/错误/跳过均 0；三端编译检查通过。范围为上一批
  309 项加本批专项，未纳入既有玩法/AI 基线失败，不宣称全仓全绿。
- 最终真实资源三轮，每轮按钮引用构造前/构造后/运行中/退出后为 0/129/138/0；
  三次技能更新重建保持运行中计数稳定。旧 105/轮残留消除。每轮 58 个 DT1、
  2626 张地图贴图及其 CPU 像素退出释放，新会话使用新 DT1，输入处理器回到 2。
  数字是当前夹具观测值，生产入口按宿主基线断言，不硬编码 129 或 138。
- 独立地图资产三轮 44 tiles、引用 4→2→1→0，单轮营地三帧及 Client 退出、
  D2GS 25Hz step=0.04、Throw 导弹顺序及伤害 1000→992 全部通过；与上述 322 项
  在同一命令完成，BUILD SUCCESSFUL。日志 build/ui-buttons-regression-02.log，
  报告 build/ui-buttons-camp/cycle-N-lifecycle.txt，营地 manifest 在 cycle-N/。
- 无 ERROR/Exception/OFFSCREEN_FATAL；仍保留既有地图/分配/表 Key 诊断。
  SIM_SLOW=70.36/85.18/88.17/78.22ms（40ms 预算），本次不作性能验收。
- 初轮夹具缺少 Gdx.files，导致 Cell 默认值初始化递归，已补齐并完整重跑；失败
  日志 build/ui-buttons-focused.log 保留。首个成功三轮见 ui-buttons-focused-02.log。
  首次扩大回归在第三轮营地三帧 PASS 后达到旧 120 秒看门狗，未完成退出断言，
  ui-buttons-regression.log 保留为失败记录；按三轮修正有界总预算后完整重跑通过，
  不将失败运行记为成功，也不放宽资源断言。

### 后续边界

本轮审计另外确认：InventoryPanel 的 invchar6Tab.DC6 引用和自建 fill Texture
尚未配对释放；ControlPanel 的七份职业技能图标引用，以及拼接背景的 CPU Pixmap
尚需清理。这些独立的面板资产留给下一批，不通过递归 dispose 所有控件处理。
网络停机、Client 完整屏幕栈/共享资源、GameEntryLoadingScreen、完整构造回滚、
跨 Act/加载中退出及长时压力/人工画面听感验收仍未完成。

## 第五批：背包与控制面板资产、私有贴图

InventoryPanel 补齐 invchar6Tab.DC6 的一份 unload，以及自建黑色 fill Texture
的释放。ControlPanel 补齐七份职业技能图标引用；两个面板自身的资产获取和卸载均
使用构造时的 AssetManager，控制条/迷你栏的相关引用仍由各自既有层级负责。
不是对所有 UI 的全局宿主依赖迁移，GoldAmountDialog 等其他控件仍有自己的旧实现。

两面板的 dispose 增加幂等边界：先标记终止，按所有权释放按钮音效、自建子资源及
逐份资产引用；某项失败仍尝试后续，保留首异常和 suppressed，重复调用不重试。
ControlWidget 清理也采用失败后继续，避免按钮异常跳过背景纹理或其它图标引用。
不递归调用任意子 Actor 的 dispose，也不直接销毁 AssetManager 管理的共享纹理。

包内 PanelTextures 统一创建临时纯色像素与拼接背景：try/finally 在上传成功或
失败后释放自己分配的 Pixmap；DC6 提供的源帧像素仍归 DC6。拼接顺序、底部对齐、
尺寸及 unmanaged TextureData 设定保持原样。背包 fill 和经验条白点也走此路径。
若 ControlWidget 构造失败，调用方回收已经返回的背景 Texture；这只是局部回滚，
不保证整个 ControlPanel/GameScreen 构造失败时的全资源自动回滚，也不能保证第三方
Texture 构造函数尚未返回时其内部 GL 句柄一定已回滚。

新增 PanelResourceLifecycleTest 7 项（真实释放方法、模拟资源及真实 AssetManager），覆盖面板重复退出、
全部父级引用、原宿主、嵌套异常后继续、非桌面无控制条分支。PanelTexturesTest 5 项
用 native Pixmap / 模拟 Texture 构造，覆盖上传/拼接失败清理、源帧归属、像素与布局。
真实三轮入口另外对 invchar6Tab 和七种职业图标做构造前基线断言，检查背包 fill、
控制条背景和经验条白点三张自建纹理的活跃/退出 GL 句柄，并验证上传像素提前释放。

后续优先处理 GameEntryLoadingScreen 的 Stage/加载图生命周期，再推进网络停机、
Client 完整屏幕栈/共享资源等边界；跨 Act/加载中退出、长时压力与人工显示/听感仍待。

本批最小复跑（全量定向回归还沿用第四批的装配/音频/地图选择）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.PanelResourceLifecycleTest `
  --tests com.riiablo.screen.panel.PanelTexturesTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  :desktop:offscreenSessionLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/ui-panels-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/ui-panels-camp' `
  '-Pd2Version=1.10f' --no-daemon
```

### 第五批验证记录

- 新增 12 项通过，最终扩大定向回归 334/334、49 类：core 292/292（42 类）、
  D2GS 全部 42/42（7 类），失败/错误/跳过均 0；core/desktop/D2GS 编译检查通过。
  沿用第四批 322 项并新增本批用例，不含既有玩法/AI 基线失败，不宣称全仓全绿。
- 真实资源三轮，每轮 8 种面板资产回到构造前基线、3 张自建纹理 GL 句柄删除，
  上传像素在运行期间已释放。按钮 0/129/138/0、三次技能重建稳定；每轮地图
  58 个 DT1 / 2626 张贴图及 CPU 像素回收、新会话新实例、输入基线 2 仍通过。
  退出 loaded asset 种类由上一批 57 降至 49，不等同于全部内存或 GL 资源验收。
- 独立地图真实 GL 三轮保持 44 tiles、引用 4→2→1→0；单轮营地三帧及 Client
  退出、D2GS 25Hz step=0.04、Throw 导弹顺序与伤害 1000→992 全部通过，
  与上述测试同一命令 BUILD SUCCESSFUL。日志 build/ui-panels-regression.log；
  报告 build/ui-panels-camp/cycle-N-lifecycle.txt，营地 manifest 在 cycle-N/。
- 初次专项的 11 项新增测试与既有 23 项均通过，真实三轮也通过；随后加入第 12 项
  真实 AssetManager 外部共享引用用例，纳入最终扩大回归。初次日志
  build/ui-panels-focused.log。没有先失败再修改断言或延长看门狗的情况。
- 最终无 ERROR/Exception/OFFSCREEN_FATAL；仍保留既有地图/分配/表 Key 诊断。
  四次 SIM_SLOW=100.47/68.01/51.20/85.57ms（预算 40ms），不作性能验收。

## 第六批：入场加载屏的临时资源与交接

GameEntryLoadingScreen 的构造不再创建 Stage 或加载图片；每次 show 才取得一份
加载图引用并创建借用 Client batch 的 Stage。hide 先移走资源引用，再释放 Stage
和 ScreenAssetScope；重复 hide/dispose 不重复卸载，尚未完成的隐藏屏幕可重新 show，
最终 dispose 或成功交接后禁止重用。某项清理失败仍尝试后续，保留首异常和 suppressed。

必须在 hide 完成清理，而不是只补 dispose：Client.setScreen 调用新 GameScreen.show
时，后者会 push 游戏加载屏，导致外层返回后屏幕栈非空，旧入场屏未必收到 dispose。
本批不改 Client 的栈判定或切换事务；生产交接中即使旧屏只收到 hide，也能回收临时
Stage 和加载图，不误卸随后游戏加载屏/会话持有的同名图片引用。

入场屏捕获原 AssetManager、Fonts 和 Client，用于自己的加载、字体准备和交接；
绘制仍借用全局 viewport/batch/palettes，后继 GameScreen 仍有全局宿主依赖，不能
据此宣称任意宿主替换安全。字体队列及共享字体属于 Client/Fonts，不由入场屏取消或
卸载；socket/localHost 参数原样传递给网络屏幕，入场视图不关闭 socket 或托管服务。
初始化、工厂或切换失败会终止旧入场屏并清理其临时资源，不自动重试。Client 可能已
安装新屏后才抛错，所以不能在 catch 中无条件 dispose 新屏；完整 GameScreen 构造
回滚、切换事务以及失败入场的网络所有权收口仍待后续处理。

新增 GameEntryLoadingScreenLifecycleTest 15 项，覆盖未使用即销毁、重复显示/隐藏、
隐藏恢复、隐藏后禁止入场、字体探测/资产未就绪、缓存时黑帧、一次性交接与回调重入、
原宿主、队列/提交/探测/工厂/切换失败、清理异常后继续、动画进度边界和本地/网络
参数传递。测试替换重型新屏构造，不能单靠它证明真实游戏创建成功。

offscreenSessionLifecycle 现在每轮先使用生产 GameEntryLoadingScreen，字体准备
完成后由包内工厂创建原 OffscreenCampScreen，再由真实 Client 交接至游戏加载屏。
新增断言：构造不 load、显示时独占一份图片引用、交接后 Stage Actor 及字段清空、
重复销毁旧入场屏不扣游戏的共享图片引用。预载基线排除构造游戏时仍活跃的入场屏
临时引用，不能把它算入退出后的宿主基线；前五批资源硬断言全部保留。

当前真实资源入口为中文环境，首次入场初始化 9 种此前入口绕过的延迟字体，退出
loaded asset 种类从旧入口的 49 变为 58，三轮稳定。这些是 Client/Fonts 常驻资源，
不是本批临时视图的释放目标；这里只核对种类和生命周期路径，不宣称字体引用计数
或 Client 关闭时的全部共享资源已验收。没有删除字体缓存，本批不验收冷缓存 CJK
图集重建，也不代替真实菜单/角色选择、人工画面/听感或网络会话检查。

本批最小复跑（扩大回归另含前五批装配/音频/地图选择）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.GameEntryLoadingScreenLifecycleTest `
  --tests com.riiablo.screen.GameLoadingScreenLifecycleTest `
  --tests com.riiablo.screen.ScreenAssetScopeTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  :desktop:offscreenSessionLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/entry-loading-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/entry-loading-camp' `
  '-Pd2Version=1.10f' --no-daemon
```

下一批优先审计网络 socket / HostedD2GS 的幂等停机与异常后继续，再推进 Client
完整屏幕栈/共享资源；跨 Act/加载中退出、完整构造回滚、长时压力、人工与性能验收仍待。

### 第六批验证记录

- 首轮 15 项新增及既有专项通过，真实入场链三轮通过，日志
  build/entry-loading-focused.log；未发生先失败再放松断言或延长看门狗的处理。
- 最终扩大定向回归 349/349、50 类通过：core 307/307（43 类）、D2GS
  全部 42/42（7 类），失败/错误/跳过均 0。范围为上批 334 项加本批 15 项；
  不含既有玩法/AI 基线失败，不宣称全仓全绿。core/desktop/D2GS 编译检查通过。
- 同一命令的生产入场链三轮全部通过：每轮 entryStageReleased 和
  entryLoadingReferencesBalanced 均 true；按钮引用 0/129/138/0，三次技能重建
  稳定、8 种面板资产回基线、3 张私有纹理删除，58 个 DT1 / 2626 张地图贴图及
  CPU 像素释放、新会话新实例、输入处理器基线 2 继续通过。常驻资产种类三轮均 58。
- 独立地图 GL 三轮 44 tiles、引用 4→2→1→0，单轮营地三帧/Client 退出、D2GS
  25Hz step=0.04 和 Throw 顺序及伤害 1000→992 全部通过，整体 BUILD SUCCESSFUL。
  日志 build/entry-loading-regression.log；报告
  build/entry-loading-regression-camp/cycle-N-lifecycle.txt，营地 manifest 在 cycle-N/。
- 最终无 ERROR/Exception/OFFSCREEN_FATAL；保留既有地图/分配/表 Key 诊断。
  SIM_SLOW=80.82/67.55/47.30/90.42ms，预算 40ms，不作性能验收。

## 第七批：网络屏幕退出与托管进程停机

NetworkedGameScreen 在进入 dispose 时先标记网络生命周期终止，按原顺序清理
GameScreen/World/UI/地图，再关闭其拥有的 socket，最后尝试停止本地托管服务。
某项 RuntimeException/Error 不跳过后续项，保留首异常及 suppressed；重复调用和
清理回调重入不再重复关闭 socket/停止服务。删除子类重复的 socket 字段，关闭后
清空父类连接引用；World 系统注入的连接在其自身 dispose 期间仍有效。hide 只暂停
可见订阅，不关闭网络，保留游戏加载屏/跨 Act 切换的现有行为。

只有 localHost 网络屏幕才捕获 HostedD2GS.stopAction；该回调绑定当时的 Process
身份，而非退出时的任意当前进程。旧回调遇到已替换的服务或捕获时没有进程时不做
处理，远程加入者不停止本机托管服务。全局 start/stop 入口仍供原菜单/启动失败及
JVM shutdown hook 使用；本批不是整个菜单到游戏的连接所有权协议重写。

HostedD2GS.stop 先请求 destroy，最多等待 1000ms；仍活跃则 destroyForcibly，
再最多等待 1000ms，并以 isAlive 确认退出后才清空句柄。终止动作抛错仍尝试剩余
步骤并最终传播，不把失败吞掉。两次等待后仍存活则报错并保留句柄，后续全局 stop
可以再次尝试，start 不会另起重叠实例。网络屏幕本身仍是一次性清理，不因重复
dispose 自动重试失败资源。等待被中断仍尝试强制结束/确认，最后恢复线程中断标志。
以上等待在调用线程执行，可能增加退出延迟；两个限时约束的是 waitFor，不保证 OS
终止调用自身的实时上限，不是服务器优雅保存/协议断连确认或 Tick 性能验收。

新增两类专项覆盖真实生产释放方法，但绕过 GameScreen 重型构造：

- NetworkedGameScreenLifecycleTest 9 项：远程/托管清理、hide 保留连接、父级或
  socket 失败后继续、Error/suppressed、回调重入、父级已释放、缺少连接、旧屏不清
  新别名。另含同一测试内三轮真实子 JVM + libGDX NetJavaSocketImpl：真实握手、
  客户端流关闭、进程确认退出、监听端口有界关闭、旧停机回调不影响新进程。
- HostedD2GSLifecycleTest 9 项：空/已退出进程、普通退出确认、超时强制结束、仍存活
  报错/保留/防重启、终止动作失败后继续、异常链、中断恢复与旧/空回调隔离。

真实子进程只是标准 Java TCP 夹具，通过反射安装到 HostedD2GS 的当前进程槽；
使用生产 stopAction/stop 和网络屏幕 dispose，不启动 D2GS.main，也不验收真实
服务器启动命令、联机入场/运行/重连或保存协议。初轮立即探测 TCP 拒绝出现竞态，
保留 build/network-lifecycle-focused.log 为失败记录；现在独立要求进程返回时已退出，
并要求端口在 2 秒内明确拒绝连接，同时检查客户端已关闭的流读取抛出 IOException。
不删除端口检查、不扩大生产等待预算，不将失败运行记为成功。

本批最小复跑：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.NetworkedGameScreenLifecycleTest `
  --tests com.riiablo.screen.HostedD2GSLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests com.riiablo.screen.GameEntryLoadingScreenLifecycleTest `
  :desktop:compileJava :server:d2gs:compileJava --no-daemon
```

后续优先处理 Client 完整屏幕栈/共享资源。菜单持有 socket 的退出/构造失败路径、
TCPIPScreen 后台启动与离屏竞争、GameEntryLoadingScreen 入场失败的所有权交接、
整个 GameScreen 构造回滚、跨 Act/加载中退出、长时压力和人工/性能验收仍未收口。

### 第七批验证记录

- 修正夹具后首个成功专项共 54/54，含 18 项新增和既有 GameScreen 订阅/装配、
  入场屏测试；三端编译通过，日志 build/network-lifecycle-focused-02.log。
- 最终扩大定向回归 367/367、52 类通过：core 325/325（45 类），D2GS 全部
  42/42（7 类），失败/错误/跳过均 0。沿用第六批 349 项加本批 18 项，不含既有
  玩法/AI 基线失败，不宣称全仓全绿。真实子进程/TCP 三轮也在此命令内重新通过，
  专项 XML 位于 core/build/test-results/test/；core/desktop/D2GS 编译检查通过。
- 原版 1.10f 资源检查保持通过：独立地图 GL 三轮 44 tiles、引用 4→2→1→0；
  生产入场链/营地退出三轮，每轮 58 个 DT1 / 2626 张地图贴图和 CPU 像素释放、
  输入处理器回到 2；按钮 0/129/138/0、三次技能重建稳定、8 种面板资产回基线、
  3 张私有纹理删除、临时上传像素已释放。入场屏 Stage/图片均释放，常驻资产种类
  三轮均 58。此入口仍是本地 GameScreen，不将其作为 NetworkedGameScreen 实机验收。
- 单轮营地三帧/Client 退出、D2GS 25Hz step=0.04、Throw 顺序及伤害
  1000→992 同一命令通过，BUILD SUCCESSFUL；日志 build/network-lifecycle-regression.log。
  每轮报告 build/network-lifecycle-camp/cycle-N-lifecycle.txt，营地 manifest 在 cycle-N/。
- 最终无 ERROR/Exception/OFFSCREEN_FATAL；保留既有地图/分配/表 Key 诊断。
  SIM_SLOW=80.13/57.19/91.21/104.12ms，预算 40ms，不作性能验收。

## 第八批：Client 最终退出的整栈与共享宿主清理

本批只收口应用最终 dispose，不改运行中的 setScreen/pushScreen/popScreen/
clearAndSet 切换事务。旧实现只 hide/dispose 前台屏幕；游戏加载屏在前台时，真正
拥有 World/UI/地图的 GameScreen 留在栈中，可能随进程直接丢弃而没有保存/释放。
任何一项抛错还会跳过所有后续宿主资源。Client 现先标记终止，快照并移走前台和
完整栈，前台优先、栈由上至下按对象身份去重，前台 hide 后逐一 dispose。已经隐藏
的栈内屏幕不再 hide。去重仅限本次 Client 直接清理，不替代各屏幕内部幂等规则，
也不建立阻止 GameLoadingScreen 后续正常复用的全局“已销毁对象”注册表。

所有屏幕在原保存路径、字体、音频、AssetManager 与 GL 宿主仍有效时退出；每项
RuntimeException/Error 之后继续其余屏幕和宿主清理，首异常与 suppressed 最终传播。
退出回调不能 set/push/pop/clearAndSet 或重新 create，重复 dispose/回调重入不会
重复清理；结束后的 render/resize/pause/resume 不再触碰释放资源或重新写入全局别名。
未初始化的空 Client 可退出，但不保证任意子组件的半构造内部回滚。

屏幕后解绑 Client 自己的四个 Cvar 监听（FPS/Gamma/VSync/Realm）、清空屏幕边界
监听与本机输入；只在平台当前仍使用旧 multiplexer 时解除平台输入，不影响替换者。
InputProcessor 现在移除自己持有的 Vibration 监听并清空处理器。之后停音乐，保存
日志/Cvars/按键，释放 console、此前遗漏的 ShapeRenderer、batch、外借给 batch 的
shader、调色/色变/纯色纹理及 AssetManager。共享资产不由屏幕提前销毁。某个组件
内部失败是否还能清理其内部全部资源，仍受该组件实现约束，不作全面零泄漏保证。

stdout/stderr 在重定向前保存；console 包装借用的原 stdout 时使用关闭屏障，
关闭自己的 PrintStream 不关闭原输出。退出只恢复仍指向本 console 的标准流，不
覆盖其他宿主后来设置的流；各流恢复和 flush/close 都在失败后继续清理范围内。
Riiablo 中由该 Client 发布的宿主别名逐字段按身份清理，不能清掉新 Client 的别名。
这不是允许两个完整客户端任意并行运行的保证，组件自身的旧全局依赖仍存在。

新增 ClientShutdownLifecycleTest 17 项：整栈顺序/身份去重、重复退出、hide/屏幕/
宿主异常/Error、退出重入与拒绝装屏、释放后生命周期、空 Client、输入解除与新宿主
隔离、Client Cvar/输入监听解绑、标准流恢复/不误关借用流、保存失败、别名清理，以及
前台 loader + 栈中真实生产 GameScreen.dispose 的 World/地图先于共享资产释放。
重型 Client.create/GameScreen 构造使用替身，不以单元测试代替真实 GL 验收。

desktop OffscreenRenderClient 的 camp 模式现在包裹生产 Client.dispose，在 LWJGL
销毁上下文之前验证：两份共享 Shader 程序、两张纯色纹理已删除、AssetManager
loaded=0、宿主输入清空/平台输入解除、Client/关键 GL 别名清空、标准流不再指向旧
console，重复 dispose 安全。GL 删除程序的语义允许仍绑定的程序延迟回收，验证先
glUseProgram(0) 再 glIsProgram；这是验证探针，不改变游戏渲染或 Client 停机逻辑。
此探针覆盖正常完成后的生产会话退出，真实加载中终止仍未作为端到端场景验收。

本批最小复跑（真实资源入口保持三轮和既有资源断言）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.ClientShutdownLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameEntryLoadingScreenLifecycleTest `
  --tests com.riiablo.screen.NetworkedGameScreenLifecycleTest `
  --tests com.riiablo.screen.HostedD2GSLifecycleTest `
  :desktop:offscreenSessionLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/client-shutdown-focused-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/client-shutdown-focused-camp' `
  '-Pd2Version=1.10f' --no-daemon
```

后续优先处理运行中屏幕替换/清栈的重入、异常和输入归属；旧菜单 hide/dispose 的
多份引用配对必须逐项审计，不能直接统一为任意递归释放。RenderedConsole 的内部
Cvar/Timer、AsyncOutputStreamAppender 线程、完整共享子组件释放与静态绑定清理、
菜单/入场连接交接及后台启动竞争、完整构造回滚、跨 Act/真实加载中退出、长期压力、
人工与性能验收仍待。本批没有修改玩法/RNG/Tick/协议/存档格式。

### 第八批专项验证记录

- 首轮编译误将 libGDX Screen 作为 Disposable 传入，改为显式调用其 dispose；
  build/client-shutdown-focused.log 保留为失败记录。后两轮各有 1 项夹具失败：Cvar
  第一次不同值赋值会发 onLoaded，不是 onChanged；设置相同值不触发初始化，因此
  不能靠同值赋值修复夹具。现按 isLoaded 明确验证对应事件，继续要求第三方监听
  保留、旧 Client batch 不收到回调，未删除或减弱解绑检查。失败日志
  build/client-shutdown-focused-02.log、client-shutdown-focused-03.log 保留。
- 修正后 62/62 专项通过，含本批 17 项与既有订阅/入场/网络专项；原版资源三轮
  及真实 Client 最终 GL 检查通过，BUILD SUCCESSFUL。日志
  build/client-shutdown-focused-04.log；三轮报告和 manifest 在
  build/client-shutdown-focused-camp/。未降低资源断言，也未改动轮数/帧数或进程看门狗预算。

### 第八批最终回归

- 扩大定向回归 384/384、53 类：core 342/342（46 类）、D2GS 全部 42/42
  （7 类），失败/错误/跳过均 0。沿用第七批 367 项加本批 17 项，不含既有玩法/AI
  基线失败，不宣称全仓全绿；core/desktop/D2GS 编译检查通过。
- 同一命令的生产入场链三轮、独立地图 GL 三轮、单轮营地三帧、D2GS 25Hz
  step=0.04 和 Throw 顺序/伤害 1000→992 全部通过，BUILD SUCCESSFUL。日志
  build/client-shutdown-regression.log；三轮报告
  build/client-shutdown-camp/cycle-N-lifecycle.txt，营地 manifest 在 cycle-N/。
- 前七批资源硬断言保持通过：每轮 58 个 DT1 / 2626 张贴图及 CPU 像素释放；
  按钮 0/129/138/0、三次技能重建稳定、8 种面板资产回基线、3 张私有纹理删除、
  上传像素已释放；入场 Stage/图片清理，输入基线 2，常驻资产种类三轮均 58。
  独立地图仍为 44 tiles、引用 4→2→1→0；子进程/TCP 三轮专项继续通过。
- 两次生产 Client 最终退出（完整三轮结束、独立单轮结束）均有 CLIENT_SHUTDOWN
  PASS：两份共享程序及两张纯色纹理删除、AssetManager loaded=0、输入清空、
  关键别名解除、标准流恢复；GL 上下文仍有效时检查，重复退出安全。
- 最终无 ERROR/Exception/OFFSCREEN_FATAL；保留既有地图/分配/表 Key 诊断。
  SIM_SLOW=109.08/65.80/64.39/110.71ms，预算 40ms，不作性能验收。

## 第九批：运行中屏幕切换、弹栈与清栈

Client 的 setScreen/pushScreen/popScreen/clearAndSet 现在统一处理明确的归属：
普通替换和弹栈释放被替换的前台，不再根据 show 返回后栈是否为空猜测；push 保留
旧前台。clearAndSet 先移走完整旧栈，前台 hide 后 dispose，再按栈顶至栈底释放，
按对象身份去重；目标若是旧栈中的对象则保留并恢复，目标若已经是前台则只清旧栈，
不重复 hide/show/resize 或注册输入。普通 set/push 当前对象为空操作，直接指向旧栈
对象则报错并要求明确使用 pop/clear，避免同一对象同时归前台和栈所有。

切换先移除旧前台及其 Client 管理的输入，再 hide/释放旧归属，最后 show 新屏幕。
每项 RuntimeException/Error 不阻断余项释放或后续安装，最后传播首异常及 suppressed。
因此调用抛错不意味着目标没安装，也不意味着可以无条件 dispose 目标。show/resize
失败的目标仍归当前 Client 所有，供后续替换/最终退出清理；失败回调后不继续 resize
或自动注册屏幕自身的 InputProcessor。各屏幕自己注册的 Stage/订阅仍由其 hide/dispose
负责，本批不把任意回调失败恢复成完全可运行 UI，也不做自动回滚到旧屏幕。

保留 show/resize 的同步嵌套导航：GameScreen.show 仍可 push 可复用加载屏。
每次实际换前台递增版本，外层只在版本未变且未请求退出时继续 resize/输入注册，
避免嵌套切换后把已隐藏甚至已释放的对象重新注册。嵌套 push/pop 即使回到同一对象
也不能误执行外层旧回调。hide/dispose 期间明确拒绝导航，避免重入重新认领已移走
资源；审计现有生产屏幕的这两类回调没有导航需求，不采用后台排队或隐式延后导航。
空栈 pop 明确抛 IllegalStateException，不依赖 JVM assert 是否开启。该协议仍限定
应用生命周期线程，不是多线程导航锁或异步任务取消协议。

切换回调中请求 Client.dispose 会立即禁止后续导航/应用生命周期回调，并等最外层
切换完成屏幕清理后才释放共享宿主。若尚未 show 目标，只 dispose 其构造期归属，不
调用 hide；若目标已经进入 show，则最终整栈退出仍会 hide/dispose。嵌套切换也不能
提前销毁还被外层清理使用的 AssetManager/GL 资源。render 在屏幕回调返回后再次
检查终止状态，不在回调已关闭 Client 后继续绘制共享 cursor/batch。

旧菜单配对审计：MenuScreen、MultiplayerScreen、LoginScreen、TCPIPScreen 与
SelectCharacterScreen/2/3 均为构造 load、show 再 load、hide 调 dispose，最终
Client.dispose 还要释放其构造期份额；不能统一删除第二次 dispose 或把 hide 改成
永久终止。SplashScreen 构造加载、hide 仅解绑；CreateCharacterScreen/LobbyScreen
hide 解绑（后者还关闭连接），其构造期资源由最终 dispose 处理。这些屏幕的 Stage、
按钮/角色预览内部释放、背景任务/socket 归属仍有待单独收口，本批不声称菜单零泄漏
或完整菜单/联机验收，也未改变这些旧屏幕自身的加载/释放次数。

新增 ClientScreenTransitionTest 26 项，使用真实 Client 和 InputProcessor，替身屏幕
绕过 MPQ/GL 构造，覆盖替换/保留/弹栈、嵌套 show/resize、回到同一身份、同屏空操作、
重复入栈拒绝、空栈/null、清栈顺序/保留目标、身份去重、hide/多项释放异常和 Error、
show/resize 失败归属、嵌套后外层失败、清理重入拒绝、回调中退出及宿主清理顺序，
同一 loader 连续三次 push/pop，确认没有全局“一次释放后永不复用”的注册表。
真实三轮入口额外要求入场屏已被 Client 标记 dispose，游戏退出地图在测试显式重复
dispose 之前就已清空；报告增加 entryRetiredByClient/gameRetiredByClient。

最小专项复跑：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.ClientScreenTransitionTest `
  --tests com.riiablo.screen.ClientShutdownLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameLoadingScreenLifecycleTest `
  --tests com.riiablo.screen.GameEntryLoadingScreenLifecycleTest `
  :desktop:compileJava :server:d2gs:compileJava --no-daemon
```

首轮专项 73/73、5 类及三端编译通过，日志 build/screen-transition-focused.log。
其后补充退出请求期间的应用回调防护、深度 finally 复位和真实三轮探针，由最终扩大
回归重新验证。本批未改玩法/RNG/Tick/协议/存档格式。下一批优先收口旧菜单的
显式资源归属与 show/hide 配对；console 内部监听/Timer/异步日志线程、共享子组件、
菜单/入场连接交接、后台启动竞争、构造回滚、跨 Act/真实加载中退出、长时压力、
人工与性能验收仍待，资源阶段没有全部结束。

### 第九批最终回归

- 扩大定向回归 410/410、54 类：core 368/368（47 类）、D2GS 全部 42/42
  （7 类），失败/错误/跳过均 0；是上批 384 项加本批 26 项，不包含既有玩法/AI
  基线失败，不宣称全仓全绿。core/desktop/D2GS 编译通过。
- 同一命令的真实本地生产入场链/营地退出重进三轮全部通过，新增
  entryRetiredByClient/gameRetiredByClient 每轮均为 true；每轮 58 个 DT1、2626 张
  地图贴图与 CPU 像素释放，输入回基线 2，按钮引用 0/129/138/0，三次技能重建稳定，
  8 种面板资产回基线、3 张私有纹理删除、上传像素释放、入场 Stage/图片清理，常驻
  资产种类保持 58。没有降低资源断言，也没有改变三轮/三帧和看门狗预算。
- 独立地图 GL 三轮仍为 44 tiles、引用 4→2→1→0；真实子 JVM/TCP 三轮专项
  继续通过。单轮营地三帧、两次生产 Client 最终 GL 检查（两共享程序/两纹理删除、
  assets=0、输入/别名/流恢复）通过；D2GS 25Hz step=0.04、Throw 顺序和伤害
  1000→992 同一命令通过，BUILD SUCCESSFUL，耗时 3m33s。
- 日志 build/screen-transition-regression.log；三轮报告
  build/screen-transition-camp/cycle-N-lifecycle.txt，营地 manifest 在 cycle-N/。
  最终无 ERROR/Exception/OFFSCREEN_FATAL；保留既有地图/分配/表 Key 诊断。
  SIM_SLOW=70.92/50.39/53.02/70.42ms，预算 40ms，不作性能验收。

## 第十批：启动页、主菜单与多人菜单的显式资源归属

本批限定 SplashScreen、MenuScreen、MultiplayerScreen 三页，不把带角色预览、
后台连接和 socket 的 SelectCharacterScreen/2/3、LoginScreen、TCPIPScreen、
CreateCharacterScreen、LobbyScreen 混入同一批。后续继续菜单第二组，再进入控制台/
异步日志及其余共享宿主边界，不以本批三页通过宣称整个前端零泄漏。

三页使用包内 MenuScreenResources：构造期每个描述符只 load 一次，由原始
AssetManager 的 ScreenAssetScope 配对释放；show 不再重复 load，hide 只解除本页
输入/控制器订阅，保持 Stage、按钮和图片以便从栈恢复；最终 dispose 一次性关闭
订阅、释放每个显式登记按钮自身的点击音效、Stage 和该页的资产引用。Stage 仍借用
Client.batch，字体、音乐控制器、动画对象及其他宿主资源不由页面销毁。按钮只回收
其声音，不递归调用任意 Actor.dispose，也不销毁 style 中的共享图片或字体。

页面持有原输入 multiplexer，旧屏不去新 Riiablo.input/assets 解除订阅或卸载资产。
同页重复 show/hide 不重复注册，disposed 后拒绝 show，隐藏/释放后 render 返回。
控制器订阅仍经 libGDX Controllers（以当前 Gdx.app 定位平台 manager），不保证任意
替换 Gdx.app 后仍能解除旧 manager；本批不是双完整 Client 并行运行协议。
Splash 保持原有控制器/继续输入/Stage 的注册顺序；隐藏后的旧继续回调不能再导航。

构造路径包裹失败清理，所有已经登记的 asset、Stage 和成功构造的按钮均会尝试释放，
包括尚未挂入 Stage 的按钮。show 订阅失败按 ScreenSubscriptionScope 回滚，最终
清理每项 RuntimeException/Error 后继续，传播首异常及 suppressed。失败清理不自动
重试，也不保证某个子控件自己尚未完成构造时的内部回滚、资产加载器内部异常回收、
音乐播放队列恢复或整个 GameScreen 构造图回滚。

Splash→Menu 和 Menu→Multiplayer 仍共享原 Logo Animation，不重新创建或复制动画。
每个页面独立拥有底层 Logo DC6 引用，即使上页隐藏/退出，当前消费者的纹理仍有效。
Menu 拥有 9 种资源，其中 button.wav 是父页 1 份预载 + 4 个按钮；Multiplayer 为
8 种资源，button.wav 是 1+3。两页同时保留时 button 引用为 9，返回 Menu 为 5，
最终退出为 0（均相对外部消费者基线）；不删除显式预载来改变既有播放就绪行为。

新增 MenuScreenLifecycleTest 19 项，使用真实页面构造/按钮/AssetManager 与同步
替身资源，GL/Stage/字体/动画/控制器为夹具。覆盖三页三次可见周期、原宿主绑定、
未 show 即销毁、外部共享引用、未挂树按钮的构造失败、Stage 释放失败、部分控制器
注册回滚/重试、控制器解除 Error 后继续、隐藏渲染和旧输入防护，以及不释放任意
外借 Actor/字体/Batch。前两轮失败日志保留：menu-resources-focused.log 因夹具
缺少 Label 静态初始化所需的 Textures.white 引发级联失败；focused-02.log 剩 1 项
因重新 stub 控制器静态方法触发旧 Answer 并登记 null。现用显式故障开关注入错误，
不在重新 stub 时执行登记动作，保留“回滚后全部解绑”的断言。

新增 :desktop:offscreenMenuLifecycle（--mode menus），使用真实生产 Client 和原版
MPQ/GL。三轮各走真实 Splash 继续输入→Menu 多人按钮→Multiplayer 取消按钮→原
Menu→clear；每个可见阶段直接调用生产 render 三次，检查九种资源精确回基线、
按钮声音 0/5/9/5/0、三 Stage 已由 Client 主动清空、输入/控制器回基线。采样完整
DC6 pages 的 GL 纹理句柄，要求 pop 后共享纹理仍有效、clear 后全部删除，并在
GL 上下文销毁前复用生产 Client 最终共享资源检查。此入口通过事件夹具触发按钮，
不证明真实鼠标/手柄操作、菜单外观/音效听感或登录/TCP/IP 连接已经人工验收。

平台退出还包含独立边界：LWJGL 先调用 LifecycleListener.dispose，之后才调用
Client.dispose；Controllers 的平台生命周期回调会从其 registry 移除旧 manager，
此后再调用静态 removeListener 会按 API 实现重新初始化 manager。本批每个可见的
控制器页面同时注册本机 Application 的终止观察器；普通 hide/dispose 仍解除自己的
控制器监听并移除观察器，平台已经终止时则不再调用会初始化 manager 的静态入口。
页面输入、Stage、按钮和图片清理仍照常进行。19 项中含平台先终止的专项；GL 探针
三轮结束后再停留于一个新主菜单直接退出，在真实 backend 顺序下验证 manager 没有
复活，并复用共享 GL/资产最终清理断言。生产逻辑不通过反射修改控制器 registry。

控制器探针按实际库结构验证：gdx-controllers-desktop 2.2.1 的 getListeners 返回
包含单一 CompositeControllerListener 的 Array，而非页面注册清单；验收代码只读
展开该组合对象的 listeners，检查数量与页面 listener 的对象身份。平台内部的
ManageControllers 监听保留在基线，不调用 clearListeners，也不更换为测试 manager。
此只读检查及最终 registry 检查属于版本相关的测试探针，不是生产生命周期协议。

专项验证记录：focused-03.log 中 82 项全部通过，但 GL 因把组合包装层当作页面数量
而失败；修正为读取实际注册列表后 focused-04.log 的 82/82、真实菜单三轮和共享
退出检查通过（56s）。再加入平台先销毁/前台主菜单直接关闭边界后，最终
build/menu-resources-focused-05.log 的 83/83、6 类与三端编译/真实 GL 验收通过
（58s）：三轮均为 9 种资产回基线、126 张纹理删除、3 Stage 由 Client 清空、
button 引用 0/5/9/5/0、输入 2、控制器内部常驻监听 1，MENU_SHUTDOWN 明确
controllerManagerResurrected=false，CLIENT_SHUTDOWN PASS。未降低断言或延长预算。

最小复跑（日志和报告输出应使用各自独立的目录）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.MenuScreenLifecycleTest `
  --tests com.riiablo.screen.ClientScreenTransitionTest `
  --tests com.riiablo.screen.ClientShutdownLifecycleTest `
  --tests com.riiablo.screen.ScreenAssetScopeTest `
  --tests com.riiablo.screen.ScreenSubscriptionScopeTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  :desktop:offscreenMenuLifecycle :server:d2gs:compileJava `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/menu-resources-focused-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/menu-resources-focused' `
  '-Pd2Version=1.10f' --no-daemon
```

### 第十批最终回归

- 扩大定向回归 429/429、55 类：core 387/387（48 类）、D2GS 全部 42/42
  （7 类），失败/错误/跳过均 0；是上批 410 项加本批 19 项，不包含既有玩法/AI
  基线失败，不宣称全仓全绿。core/desktop/D2GS 编译通过。
- 同一命令的菜单三轮均通过：九种资产精确回基线、126 张纹理删除、三个 Stage
  主动清空、按钮声音引用 0/5/9/5/0、输入基线 2、控制器常驻基线 1。最终停留于
  主菜单直接关闭，MENU_SHUTDOWN 和共享资源 CLIENT_SHUTDOWN 均 PASS。
- 独立地图 GL 三轮仍为 44 tiles、引用 4→2→1→0；本地生产入场链/营地退出重进
  三轮的原有资源硬断言均通过：每轮 58 个 DT1、2626 张地图贴图、按钮引用
  0/129/138/0、8 种面板资产回基线、3 张私有纹理删除、入场/游戏由 Client 主动
  释放。退出时剩余资产种类为 58/59/59，不声称本次三轮稳定为 58。
- 单轮营地三帧、三次生产 Client 最终 GL 检查（两共享程序/两纹理删除、assets=0、
  输入/别名/流恢复）、D2GS 25Hz step=0.04、Throw 顺序及伤害 1000→992 同一
  命令通过，BUILD SUCCESSFUL，耗时 3m18s。
- 日志 build/menu-resources-regression.log；菜单报告
  build/menu-resources-camp/menu-cycle-N-lifecycle.txt，会话报告 cycle-N-lifecycle.txt。
  最终无 ERROR/Exception/OFFSCREEN_FATAL；保留既有地图/分配/表 Key 诊断和
  不可用音乐跳过记录。SIM_SLOW=92.90/50.23/40.68/91.37ms，预算 40ms，
  不作性能验收。资源阶段尚未结束，下一批为角色选择/预览等菜单第二组。

为核对总资产数变化，OffscreenSessionLifecycleScreen 另输出
cycle-N-remaining-assets.txt，逐项保存退出后的路径、类型及引用数；不删除原有资源
硬断言，也不把总种类相等作为已验证事实。首次复跑 build/menu-resources-assets.log
在第一轮加载后停止渲染，线程检查显示 LWJGL Application 正常后台循环而无加载任务；
LWJGL 的 pauseWhenMinimized 会让离屏 1x1 窗口在被判为不可见时停止 render。
主动结束该测试进程并保留失败日志，离屏入口明确设置 pauseWhenMinimized=false /
pauseWhenBackground=false；普通游戏入口不变，三轮/三帧和看门狗预算不变。

补充复跑 build/menu-resources-assets-02.log：真实会话三轮和菜单三轮、两次最终
Client GL 检查均 PASS，BUILD SUCCESSFUL，耗时 2m9s，无 ERROR/Exception/
OFFSCREEN_FATAL；SIM_SLOW=84.61/48.57/41.49ms，仍不作性能验收。三轮退出
种类为 59/59/59，三个资产清单路径相同；声音项包含 MedDirt3.wav、wilderness day
2.wav、fire4.wav，Audio.play 当前会缓存已加载的声音库。原回归没有逐项快照，
因此不能事后断定 58→59 究竟新增哪一项，不把缓存推测写成已证实结论。

快照同时暴露下一资源批需要处理的引用递增，不能以资产种类相同宣称会话零泄漏：
Gleam.dcc、NPCSpeechBalloon.dcc、ShrineManaRegen.dcc 和区域标题
data/local/ui/chi/act1/A1L1.dc6 均为 refs=1→2→3；MENU/boxpieces.DC6 为
12→24→36。它们不在本批三页菜单的九种描述符内，属于覆盖动画/区域标题及
共享框组件的单独生命周期边界，本批只新增证据，不扩散修改这些实现。现有会话
PASS 只代表已列明的资源硬断言通过；最终 Client.dispose 后 assets=0 仍通过，
不代表宿主存活时上述引用已经归位。清单位于 build/menu-resources-assets-02/
cycle-N-remaining-assets.txt，后续修复应以逐项引用为依据补充断言。

## 第十一批：角色选择页与角色预览资源

本批限定 SelectCharacterScreen / 2 / 3、CharacterSelectButton 和旧 CharacterPreview
的图形资源，不改变三种选择页的单机/大厅/TCP/IP 导航方式、存档读写格式、角色
装备组合或动画规则。CreateCharacterScreen / CharacterCreateButton、登录及 socket /
托管进程交接另批处理；上一批发现的五种共享资产引用递增也未在此批顺带修改。

三页复用 MenuScreenResources：构造只加载三种页面图片一次，显式登记六个按钮及
每个角色条目；show/hide 只注册/解除原输入宿主的 Stage，隐藏不再调用 dispose。
最终释放条目、按钮声音、借用 batch 的 Stage 和页面图片，重复退出安全，隐藏或
销毁后的 render 和旧操作回调不再导航。成功创建但尚未挂树的条目也被登记；列表
刷新先移走旧集合，再逐项移除 Actor、注销归属并释放，某项失败继续其余旧条目。
刷新开始即禁用需要选中角色的操作，不把刷新失败的部分列表当作完整恢复事务。
本批不增加“每次 show 都重读存档”的行为，仍由原有构造/刷新路径更新列表。

CharacterSelectButton 不再共享静态 charselectbox 纹理：每个条目拥有一份选中框
资产引用，选择时从原 AssetManager 获取当前对象；空列表不加载选中框。删除旧的
loadBox/unloadBox 全局配对，最后一个条目退出才允许实际释放，外部消费者的引用
不受影响。条目拥有其 CharacterPreview；set 更换预览时释放旧实例，标签准备失败
保留旧预览并释放新实例。dispose 先断开 EntityWrapper，再释放预览及框引用。
EntityWrapper 仍为借用容器，不泛化销毁任意传入 Entity。

旧 Entity 的加载/读取路径增加两个保护扩展点，默认仍使用原全局实现；仅
CharacterPreview 绑定创建时的 AssetManager 并记录成功请求的路径，同一预览中
重复无效化/重画同一图层不重复 load，访问过的不同图层保留到该预览退出。释放时
丢弃动画/图层引用，配对归还全部请求；已释放的预览不再 act/draw。该策略不是
运行时装备切换的逐帧缓存淘汰，也没有把所有旧 Entity 或游戏 ECS 改为新的归属协议。

ScreenAssetScope 原有实现移入可由屏幕/控件/预览共用的 AssetReferenceScope，
屏幕包内保留原入口作为委托。成功 load 才登记，每次成功请求对应一次 unload；
按 libGDX 1.10 共享排队加载语义，先完成仍在加载的请求再卸载，不能取消其他
消费者的同名请求。清理幂等，异常/Error 后继续并保留首异常与 suppressed；仍需
在宿主 AssetManager 退出前的 GL 线程执行，不承诺加载器内部失败自动回滚。

专项验证扩展 MenuScreenLifecycleTest，在真实页面/控件/AssetManager 上使用替身
Stage、DC6/DCC、字体、动画和控制器；列表使用校验和有效的 1.10 头部夹具（不是
完整可玩存档）。首轮 96/96、5 类和三端编译通过，日志
build/character-resources-focused.log；后续加入空列表、旧回调、共享排队预览等边界
及真实 GL 探针后再完成最终验证。预览/条目构造之前成功登记的资源可回滚，不宣称
任意子控件尚未构造完成的内部资源或完整连接图均已自动回滚。

新增 :desktop:offscreenCharacterLifecycle，在前台菜单验收入口上追加三轮、每轮三种
选择页、每页七职业预览。独立生成 build 输出目录内的新存档夹具，不读取/覆盖/
删除用户存档；通过生产 Client push/pop 验证隐藏保留、原页恢复，再调用生产列表
刷新检查旧 DCC 纹理/像素释放，最终清栈要求全部 AssetManager 引用精确回基线、
输入归位、Stage 已被 Client 清空。只读反射读取私有状态，并调用原刷新方法；
不模拟网络连接，也不点击进入游戏或删除角色，不代替人工外观/操作与联机验收。

第二轮 build/character-resources-focused-02.log 的 109/109、6 类及三端编译通过：
MenuScreenLifecycleTest 共 45 项（原 19 项 + 本批新增 26 项）。真实 GL 的第一个
七职业组通过，61 个 DCC、14176 张纹理在刷新及最终退出后均释放，全部引用回基线；
连续九组则触发既有 120 秒看门狗，进程退出码 2，保留失败日志。线程检查显示正在
执行真实 DCC/GL 纹理删除而非死锁，不降低资源断言或增大超时掩盖耗时。

因此聚合任务拆为 C1V1 至 C3V3 九个串行 JavaExec 子任务，每组独立 JVM 保持
120 秒预算，七职业/三帧渲染、隐藏恢复、列表刷新、完整纹理/像素与引用断言不变。
这是三轮独立运行验证，不冒充九组在同一长期存活宿主的压力测试；每组内部仍由同一
真实 Client 经过隐藏恢复及列表销毁/重建，长时同宿主压力及性能验收继续保留。
可单独运行 :desktop:offscreenCharacterLifecycleC1V1 复查一组；默认聚合执行全部九组。
聚合任务的总时间可以超过 120 秒，120 秒是每个子 JVM 的上限而非整个聚合任务的
上限。第二/三种选择页使用空 Account/socket，仅执行图形资源路径，不触发连接或
进入大厅/游戏；UI 验证通过不能据此宣称连接所有权已经验收。

本批最小复跑（45 项菜单/角色生命周期单元测试 + 一组七职业真实 GL）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.screen.MenuScreenLifecycleTest `
  :desktop:offscreenCharacterLifecycleC1V1 `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/character-resources-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/character-resources-recheck' `
  '-Pd2Version=1.10f' --no-daemon
```

将单组任务替换为 :desktop:offscreenCharacterLifecycle 可复跑全部九个独立组。

### 第十一批最终回归

- 扩大定向 JUnit 413/413、52 类：core 371/371（45 类），D2GS 全部 42/42
  （7 类），失败/错误/跳过均 0；三端编译通过。本次筛选范围与第十批 429 项不同，
  不能当作“上批 429 + 本批 26”或全仓全绿，既有玩法/AI 基线失败未纳入。
- 九个独立 JVM 角色组全部通过：三种选择页各重复三次，每组七职业、61 个 DCC，
  刷新与最终退出各确认 14176 张纹理删除及 Pixmap 释放；隐藏/恢复不增减引用，
  列表重建后引用相同，最终全部 AssetManager 引用精确回基线，输入归位，Stage
  由生产 Client 主动清空。每组前置菜单三轮也通过，共 27 轮菜单、9 次
  MENU_SHUTDOWN 和 9 次角色组 Client 共享退出检查。
- 独立地图三轮均为 44 tiles、引用 4→2→1→0；生产会话三轮资源硬断言均通过：
  每轮 58 个 DT1、2626 张地图贴图、按钮音效引用 0/129/138/0、8 种面板资产
  回基线、3 张私有纹理删除、入场及游戏屏幕由 Client 主动释放。单轮营地三帧、
  总计 11 次 Client 最终 GL 检查（两共享程序/两纹理删除、assets=0、输入/别名
  清空）、D2GS 25Hz step=0.04、Throw 顺序及伤害 1000→992 同命令通过。
- 会话剩余种类为 58/58/59。逐项清单确认第一/二轮路径相同，第三轮新增
  data/global/sfx/ambient/footstep/MedDirt1.wav（Sound，refs=1），与声音库缓存
  行为一致；不把音效缓存或最终 assets=0 当成会话期间零泄漏的证据。
  Gleam / NPCSpeechBalloon / ShrineManaRegen 和 A1L1 仍 refs=1→2→3，
  MENU/boxpieces 仍 12→24→36，明确留到后续批次，本批未修改这些资源边界。
- 日志 build/character-resources-regression.log，报告目录
  build/character-resources-regression/，包含九份 character-cycle-N-variant-V.txt、
  三份 cycle-N-lifecycle.txt 及三份 cycle-N-remaining-assets.txt。BUILD SUCCESSFUL，
  退出码 0，耗时 14m36s，无 ERROR/Exception/OFFSCREEN_FATAL。保留已有地图/
  分配/表 Key 诊断、不可用音乐记录，以及 focused-02.log 的超时失败，不覆盖失败史。
  SIM_SLOW=66.51/78.75/50.84/94.16ms，预算 40ms，不作性能验收。

总体七批子系统装配完成，资源第十一批完成但资源阶段未结束。下一批优先收口
CreateCharacterScreen / CharacterCreateButton，继续按明确归属补测试；共享组件/
覆盖动画/区域标题引用递增、连接交接、控制台与异步日志、完整构造失败回滚、
跨 Act/加载中退出、长期同宿主压力及人工画面/操作/联机/性能验收仍待。

## 第十二批：创建角色页与职业控件

本批只收口 CreateCharacterScreen / CharacterCreateButton 的页面、动画、声音与
可见订阅；不改 initializeNewCharacter 的暗黑属性/装备/技能规则、D2S 写入过程，
也不改变本地 GameEntryLoadingScreen 或在线 onCreated 回调的导航与连接交接。
不访问/修改原版 exe/dll，本批没有需要重新推断的暗黑机制。

创建页复用 MenuScreenResources，构造中只创建一次页面、TextField、七职业控件、
两个按钮及火焰动画。已成功构造但尚未挂树的职业控件立刻登记，后续任意创建失败
会释放此前已登记的资源；每次 show 不再重复创建 Actor，hide 保留职业/名称和全部
图形引用。最终释放七职业、按钮声音、Stage（借用宿主 batch）和五种页面资产，
包括旧实现漏掉的 textbox.dc6。删除未执行的旧后台线程/场景绘制注释，不恢复这些
未实现功能，也不泛化递归 dispose 任意 Actor。

输入与布局监听分别绑定构造时的 input / Client；重复 show/hide 幂等，显示订阅失败
回滚已有订阅，某项解绑异常/Error 仍继续其他清理。隐藏和最终销毁先禁止旧页面
回调，解除输入/布局监听，禁用各职业交互并只停止其自身播放；姓名框失焦并通过
libGDX 默认 keyUp 取消按键重复，失焦失败仍尝试取消。隐藏/销毁后 render、旧按键、
旧确认按钮与旧布局回调不再导航、写存档或绘制。没有重写通用 TextField/全局 Timer；
libGDX 的光标闪烁任务在 Actor 退出 Stage 后由其原回调自行取消，不声称同步回收
全部 UI Timer，隐藏保留 Stage 时也不声称闪烁任务已同步停止。

职业控件使用原 AssetManager + AssetReferenceScope，每个成功提交的不同描述符
只登记一次，loadRemainingAssets 可重复调用，也支持只构造初始 NU1 就销毁。
公共 select/deselect 与悬停按需保证其剩余资源已提交，不依赖页面调用加载的时序；
不改变职业特殊图层、动画混合、NU1/FW/NU3/BW 状态顺序。声音保存每次 play 返回
的 ID，切换、隐藏及退出只 stop(id)，不对共享 Sound 调用无参数 stop。退出幂等，
先禁止交互/动画回调并断开动画，再停止各自身播放与配对归还资产；某项 stop 抛出
异常/Error 后仍继续其余清理，旧动画完成回调不会重新加载已释放资产。

专项在 MenuScreenLifecycleTest 中新增 28 项（原 45 + 28 = 73）：七职业初始加载
退出、七职业重复加载与选中/退回、共享音效播放 ID 和原 manager 保护、声音 stop
Error、初始动画构造失败、旧事件/旧完成回调，以及创建页重复 show/hide 与名称/
Actor 保留、未 show 退出、可见退出保护原宿主、第二职业构造失败、布局 attach
回滚/重试、detach Error、Stage Error、隐藏旧页面回调，以及部分提交失败重试不
重复已成功请求、外部消费者的 textbox 引用保护。使用真实页面/控件和
AssetManager，Stage/动画/字体/GL/声音为替身，不替代人工操作/听感。
首轮 build/create-resources-focused.log：135/135、6 类和三端编译通过，47s。

新增 :desktop:offscreenCreateCharacterLifecycle，沿用前置菜单三轮及 120 秒子进程
看门狗，在同一真实 Client 上反复三轮构造/隐藏恢复/最终清栈创建页。加载原版 MPQ，
通过生产的职业/页面 ClickListener，配合真实 Animation.act 的确定性完成步推进七
职业悬停、FW→NU3、BW→NU1；不调用确认按钮，不写存档、不发网络请求，也不
代替真实鼠标命中/逐帧时序、外观或声音人工验收。只读反射观察资产/布局清单，
清栈后核对全部引用、输入、布局监听、Stage 及本批 DC6 GL 纹理和原始/上传 Pixmap。
首轮 build/create-resources-gl.log 因 getDeclaredField 只查离屏 Client 子类而找不到
父类的 screenBoundsListeners，退出码 1，38s；保留失败日志，探针改为沿继承链查找，
不删除或放宽布局监听及任何资源断言。

第二轮 build/create-resources-focused-02.log：135/135（新增前 26 项时）、6 类与
创建页 GL 三轮全部通过，BUILD SUCCESSFUL，1m3s；每轮七职业的 14 次完成状态
转换、47 个 DC6，1545 张纹理删除、1557 个原始/上传 Pixmap 释放，全部引用与输入/
布局监听回基线；选中职业、名称、Actor 数量在隐藏恢复后保持，Stage 由 Client 主动
清空。前置菜单三轮、MENU_SHUTDOWN、Client 最终共享 GL 释放也通过。随后再补
两项边界测试进入最终扩大回归，不把尚未运行的测试计入该次 135 项结果。

本批最小复跑（73 项菜单/选择/创建生命周期 + 三轮创建页真实 GL）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test --tests com.riiablo.screen.MenuScreenLifecycleTest `
  :desktop:offscreenCreateCharacterLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/create-resources-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/create-resources-recheck' `
  '-Pd2Version=1.10f' --no-daemon
```

### 第十二批最终回归

- 扩大定向 JUnit 441/441、52 类：core 399/399（45 类），D2GS 42/42（7 类），
  失败/错误/跳过均 0；三端编译通过。本次使用第十一批的筛选范围，加本批 28 项；
  仍排除独立环境的 MercenaryHudAssetProbeTest 及既有玩法/AI 基线失败，不宣称
  全仓测试全绿，也未降低任何既有资源断言。
- 创建页同一 Client 三轮全部通过，每轮七职业、47 个 DC6、1545 张纹理删除、
  1557 个原始/上传 Pixmap 释放；全部资产引用、输入和布局监听回基线，隐藏/恢复
  保留职业/名称且不新增 Actor，Stage 由生产 Client 主动清空。上一批选择页
  C1V1 七职业组也通过：61 个 DCC、刷新/最终退出各 14176 张纹理释放与像素
  释放、全部引用回基线。本次只复查选择页一组，不把上批九组冒充本次再次全跑。
- 两个菜单进程的前置三轮均通过，共 6 轮菜单、2 次 MENU_SHUTDOWN；独立地图
  三轮仍为 44 tiles、引用 4→2→1→0。生产会话三轮仍每轮 58 个 DT1 / 2626 张
  地图纹理、按钮引用 0/129/138/0、8 种面板资产回基线、3 张私有纹理释放，
  entry/game 已由 Client 主动释放；单轮营地三帧、总计 4 次 Client 共享最终 GL
  检查（两程序/两纹理删除、assets=0、输入/别名/流恢复）、D2GS 25Hz
  step=0.04、Throw 顺序及伤害 1000→992 均同一命令通过。
- 逐项会话清单种类 58/58/58，三轮路径相同；Gleam / NPCSpeechBalloon /
  ShrineManaRegen / A1L1 仍 refs=1→2→3，MENU/boxpieces 仍 12→24→36。
  本批未修改这些边界，种类稳定和最终 assets=0 都不能代替宿主存活时的逐项引用
  归位。没有把前批不同运行中的声音缓存变化当成本批结果。
- 日志 build/create-resources-regression.log；报告目录
  build/create-resources-regression/ 包含 creation-cycle-N.txt、
  character-cycle-1-variant-1.txt、cycle-N-lifecycle.txt 与
  cycle-N-remaining-assets.txt。BUILD SUCCESSFUL，退出码 0，4m39s，
  无 ERROR/Exception/OFFSCREEN_FATAL。保留首轮探针失败及原有地图/分配/表 Key
  诊断、不可用音乐跳过记录。SIM_SLOW=62.31/54.69/96.62/65.18ms，预算 40ms，
  仍不作性能验收；自动探针也不替代人工画面、真实输入、声音、联机或存档验收。

总体七批子系统装配完成，资源第十二批完成但资源阶段未结束。下一批优先修复
已确认的覆盖动画/区域标题/共享框引用累积，再继续控制台与异步日志、连接交接其余
边界、完整构造失败回滚、跨 Act/加载中退出及长期压力；人工与性能验收继续保留。

## 第十三批：覆盖动画与区域标题

本批收口 Gleam、NPCSpeechBalloon、实体覆盖动画（营地探针中的 ShrineManaRegen）
及区域标题（当前中文环境的 A1L1）。共享框 MENU/boxpieces 涉及 NPC 菜单树、
对话和面板的不同归属，留到第十四批；不以 AssetManager 最终整体清空代替宿主
存活时逐项引用归位，不声称资源阶段已结束。本批不改暗黑规则或原版文件。

GroundItemGleamSystem / Act1QuestIndicatorSystem 捕获原 AssetManager，成功请求
登记到 AssetReferenceScope；重复 begin 不重复请求，退出清空动画状态并幂等归还
自身引用。初始化时宿主尚不可用则绑定首次有效宿主，之后不跟随全局别名切换。
不改变闪光周期、任务标记条件、动画混合、光照、偏移或帧率。

OverlayManager 为每个实体保存组件身份、描述符身份及独立引用作用域。替换、
自然结束、匹配状态清除、组件移除、实体删除和 World 退出均归还引用；首次
subscription 插入前就移除的资源，由 begin 的账本核对补收。核对采用复用的
IntArray，避免遍历账本时删除条目，也不每帧创建实体快照。同帧移除/重新安装
依据当前 subscription 与描述符身份判别，旧通知不释放新所有者，组件池复用后
不重置新动画。World 退出遍历账本而不是仅订阅内实体，覆盖未 process 的请求。
清理先移出账本，动画 reset 异常/Error 仍尝试归还引用及清理其他实体。无效
overlay id 先解析再替换，保留原效果，修复旧实现先卸载却仍保留旧组件的问题。
保留 transient 的首次可见帧及 minimumDisplayFrames、persistent 状态生命周期。

ZoneEntryDisplayer 为每次区域标题单独登记请求，切换前先清动作、隐藏并断开旧
Drawable，再卸载旧引用；最终退出同时移除 Stage Actor。保留语言、ACT 路径及
Id=8 使用 A1Q1 的规则；队列未完成、重复退出、外部共享消费者和原宿主均纳入
生命周期测试，不将标题 Actor 遗留在 Stage 上等待宿主兜底。

OffscreenSessionLifecycleScreen 在进入加载页/构造 GameScreen **之前**采样四项
基线；真实加载结束后要求四项均有新增引用、实际 GL 纹理及原始/上传 Pixmap。
生产 Client 退出游戏后逐项断言引用回基线，零基线资产另断言纹理删除及像素释放。
保留原有地图、按钮、面板、输入、加载页和最终宿主检查，以及全部剩余资产快照；
不会跳过未覆盖的本批资源，也不把共享框累积隐藏在总种类数中。

### 第十三批验证与复跑

首轮 build/overlay-resources-focused.log 为 31 项中 15 项失败（Label 静态初始化
缺少宿主 white 纹理、Mockito 在未完成设桩时构造另一设桩）。修正夹具后第二轮
build/overlay-resources-focused-02.log 剩 3 项失败，确认为生产删除判断缺陷：
Artemis EntityManager.isActive 在帧末 purge 前仍返回 true；改查当前订阅活跃位，
保留原有断言而非延后等待或放宽“删除即归还”的标准。

第三轮 build/overlay-resources-focused-03.log：31/31、3 类及三端编译通过，其中
新 PresentationAssetLifecycleTest 为 19 项；另 5 项任务标记、7 项状态覆盖动画。
同一真实 Client 三轮会话通过，每轮四项引用回基线、42 张覆盖动画/标题纹理
删除、42 个原始/上传 Pixmap 释放；原地图/按钮/面板/输入/加载页及最终 Client
GL 检查全部保留并通过。剩余资产种类由原来的 58/58/58 降为 54/54/54，四项
已不在退出快照中，boxpieces 仍 refs=12/24/36，不声称整体零泄漏。
BUILD SUCCESSFUL，退出码 0，2m41s。之后另补“请求提交 Error”和“宿主延后可用”
两项进入最终扩大回归，不计入该次 31 项结果。

最小复跑（专项 + 原版 MPQ 同宿主三轮）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests com.riiablo.engine.client.PresentationAssetLifecycleTest `
  --tests com.riiablo.engine.client.Act1QuestIndicatorSystemTest `
  --tests com.riiablo.engine.client.StateOverlaySystemTest `
  :desktop:offscreenSessionLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/overlay-resources-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/overlay-resources-recheck' `
  '-Pd2Version=1.10f' --no-daemon
```

### 第十三批最终扩大回归

- build/overlay-resources-regression.log：资源定向 JUnit 497/497、58 类，core
  455/455（51 类）、D2GS 42/42（7 类），失败/错误/跳过均 0；包含新增 21 项
  PresentationAssetLifecycleTest、任务标记、状态覆盖动画、Amazon 箭矢、死灵爆炸
  和怪物技能表现测试。三端编译通过。独立地图三轮 44 tiles、引用 4→2→1→0；
  生产会话三轮每轮 58 个 DT1 / 2626 张地图纹理、四项表现引用回基线、42 张
  表现纹理删除和 42 个原始/上传 Pixmap 释放。按钮 0/129/138/0、8 项面板基线、
  3 张面板私有纹理、输入、加载页以及两个 Client 最终共享 GL 检查均通过。
- 同一命令的独立营地三帧、25Hz step=0.04（ticks 6→37）、Throw 顺序及伤害
  1000→992 通过，BUILD SUCCESSFUL，退出码 0，3m54s。三轮退出剩余资产
  54/54/54，四项目标均不在快照中；boxpieces 仍 refs=12/24/36。慢帧
  146.67/57.04/43.86/81.76ms，仍超过 40ms，不作性能验收。
- 首次扩大筛选误把两个圣骑士测试写在 client 包，实际未执行；随后使用正确的
  server 包补跑全部 core 筛选，build/overlay-resources-regression-core-final.log
  为 471 项、53 类，470 通过、1 失败，1m7s。FistOfHeavens 9/9、BlessedHammer
  6/7；失败为 srvDmg05StacksUndeadAndDemonBonuses，预期 100 / 实际 50，与
  docs/dark-magic-port-plan-2026-10-14.md 的既有失败记录一致。该方法只调用
  未修改的伤害加成逻辑，不经过本批系统；保留失败日志及原断言，不借本批改变
  暗黑伤害规则。合并 D2GS 后扩展范围共 513 项、60 类，512 通过 / 1 既有失败，
  不得宣称 513/513、全仓全绿或整个资源阶段零泄漏。
- 本批未重跑创建/选择页全部真实 GL 组合，已有页面生命周期单元回归纳入上述
  筛选；人工画面、输入、声音、跨 Act、联机、存档、加载中退出及长时压力仍待。

总体七批子系统装配完成，资源第十三批完成。第十四批处理共享框的 NPC 菜单树、
对话、角色/商店面板所有权；之后继续连接交接其余边界、控制台/Timer/异步日志、
完整构造失败回滚、跨 Act/加载中退出与长期压力，人工与性能验收继续保留。

## 第十四批：共享框与 NPC 菜单树归属

本批收口 MENU/boxpieces.DC6，覆盖 BorderedPaletteIndexedDrawable、NpcMenu、
NpcDialogBox、CharacterPanel 与 VendorPanel 的全部生产创建点。不合并每个控件
独立持有的引用，也不改变 NPC 选项、任务/交易规则、文本滚动、边框布局或原版文件。

BorderedPaletteIndexedDrawable 捕获原 AssetManager，以 AssetReferenceScope 配对
每次成功请求；幂等退出先断开缓存 DC6、regions 和借用的白纹理引用，再归还资产。
两种 draw 在退出后均无动作，不销毁宿主 white 或其他消费者使用中的共享贴图。

NPC 根菜单由 MenuManager 工厂创建并登记实体账本，子菜单在填充选项之前登记到
父菜单；不依赖 Stage 递归销毁，因为未显示的子菜单从未进入 Stage。普通导航、
隐藏与关闭保留整棵树；根替换、MenuWrapper 移除、实体删除及 World 退出显式
释放整棵树。首次 process 前删除也会收回，同帧移除/重建不误释放新菜单；工厂
安装组件失败立即回滚。菜单清理解除按钮监听、Stage 和父子归属，异常/Error 不
跳过兄弟或其他根；销毁不是玩法 cancel，不触发已删除实体的交互计数修改。

DialogManager 接管对话的替换、显式关闭和 World 最终退出；同实例不重复安装，
销毁后传入的新对话立即释放。NpcDialogBox 清理自己的 Audio.Instance，而非停止
整个共享声音；停止声音失败仍归还边框，旧完成回调不再复活对话。构造/挂载失败
清理已持有的资源，恢复菜单失败作为附加异常，不覆盖最初的挂载错误；旧对话
清理失败仍尝试安装新对话，并由管理器保留后续退出所需的归属。

角色/商店面板捕获原资产宿主，为三种各自页面资产逐项配对，并显式释放按钮与
共享框；部分构造失败按已持有字段清理。商店先请求自己的按钮贴图，再创建按钮
样式，不再借角色面板先加载。退出隐藏购买提示、解除 Stage capture listener、
清除待处理购买/交易状态；面板退出后不会重新显示或绘制已释放的背景。

OffscreenSessionLifecycleScreen 保留原四类表现资源和地图/按钮/面板/输入/加载页
断言，增加第五类共享框的构造前基线、实际 GL/Pixmap 及退出检查；清点全部
MenuWrapper 的整棵菜单树，每个节点和两个面板各持一份引用。执行三次对话安装/
替换、显式关闭，再留下活动对话交给生产 World 退出；逐节点检查菜单已释放、
父归属已断开，DialogManager 已清空。不是靠最终 AssetManager.clear 掩盖累积。

### 第十四批专项验证与复跑

生产代码初步回归 build/box-resources-initial.log：原面板资源、角色属性及屏幕
装配测试与三端编译通过，1m9s。新增专项首轮 build/box-resources-focused.log
42 项中 20 项失败，均在夹具初始化给 Fonts 的 final 字段赋值时被拒绝，尚未进入
资源断言；修正夹具只设置非 final 字段，保留原断言与失败日志。

第二轮 build/box-resources-focused-02.log：42/42、4 类及三端编译、真实会话三轮
通过，2m24s。每轮共享框 0→12→0、10 个 NPC 菜单节点释放，对话替换与 World
最终回收通过；五类表现资源共 64 张纹理删除、64 个原始/上传 Pixmap 释放。
随后新增三项工厂回滚/多根错误清理/对话替换错误测试，纳入最终扩大回归，不计入
这次 42 项结果。

最小复跑（共享框专项与原版 MPQ 同一 Client 三轮）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  :desktop:offscreenSessionLifecycle `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/box-resources-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/box-resources-recheck' `
  '-Pd2Version=1.10f' --no-daemon
```

本批范围限制：ItemGrid 构造的私有 fill Texture 尚无显式释放，VendorGrid 虽然
showFill=false 仍会分配；本批没有修改该边界，现有三张面板私有纹理断言不覆盖
这些网格纹理。下一批优先检查所有网格消费者及归属，不把 AssetManager 剩余
种类稳定或最终 assets=0 当作私有 GL 零泄漏。完整构造失败回滚、连接交接其余
边界、控制台/Timer/异步日志、跨 Act/加载中退出、长时压力及人工/性能仍待。

### 第十四批扩大回归

- build/box-resources-regression.log 的定向 JUnit 525/525、61 类：core 483/483
  （54 类）、D2GS 42/42（7 类），失败/错误/跳过均 0；含新增共享框 23 项，以及
  原面板、菜单/选择/创建页、表现资源、Act1QuestDialogController 与 NpcHealer
  回归，三端编译通过。该筛选仍不包含独立环境的佣兵资源探针和已记录的玩法/
  AI 基线失败，不宣称全仓测试全绿，也没有修改原断言或伤害规则。
- 独立地图三轮均 44 tiles、引用 4→2→1→0；同一 Client 的三轮真实会话每轮
  58 个 DT1 / 2626 张地图纹理、共享框 0→12→0、10 个菜单节点退休、五类表现
  引用回基线、64 张表现纹理删除及 64 个原始/上传 Pixmap 释放。原按钮
  0/129/138/0、8 项面板基线、3 张面板私有纹理、输入及加载页检查均保留。
- 三轮退出的剩余资产种类为 54/55/55；第二轮仅新增宿主声音缓存
  data/global/sfx/ambient/footstep/MedDirt3.wav，refs=1，第三轮逐项清单与第二轮
  一致。共享框及此前四类表现资源均不在退出快照中；声音缓存和私有网格纹理
  是不同边界，不以总种类数代替逐项引用或私有 GL 检查。
- 该命令在已通过地图/三轮会话/独立营地及两个 Client 最终退出之后，
  headlessSimulationTick 报本地 127.0.0.1:6114 Connection refused；因此整条命令
  BUILD FAILED，3m57s，headlessMissileCombat 尚未执行。保留日志，不把前段
  525 项通过等同于整条回归成功，也暂不把连接被拒绝归因于本批 UI 修改。
- 最后审查发现探针原字段 dialogReplacements=3 实际将首次安装也算作替换；
  补充独立的首次安装及引用断言，再真正执行三次替换。原日志的资源释放结果
  仍有效，但其中替换计数不作三次替换的证据；使用修正后的探针另行重跑。
- 修正探针后 build/box-resources-final-gl.log：同一 Client 三轮会话、独立营地、
  25Hz sim_tick（ticks 6→36）与 Throw 战斗（life 1000→992）均通过，三轮共享框
  仍为 0→12→0，三轮最终 ClientShutdown 共享 GL 释放通过；BUILD SUCCESSFUL，
  2m18s。日志仍报告 47.64–89.03ms 慢帧，超出 40ms 预算，不作性能验收。

## 第十五批：ItemGrid/VendorGrid/BeltGrid 私有纹理

本批收口上一批留下的私有 `fill` Texture。ItemGrid 现在实现幂等 Disposable，使用
统一的 PanelTextures 上传路径，退出时禁用输入、解除网格及 StoredItem 监听、清理
子项、脱离 Stage，再释放自己的填充纹理；不销毁 ItemData、物品 wrapper 或借用的
颜色/贴图。退出后的 draw、点击、populate 和 syncItems 均不再访问已释放状态。

InventoryPanel、StashPanel、CubePanel 显式持有并释放各自网格；VendorPanel 逐个
释放四个 VendorGrid。VendorGrid 退出时先清除购买回调，避免旧网格继续触发交易。
BeltGrid 同样纳入归属：ControlPanel 释放它的腰带网格，并解除 Keys.Belt 的按键
分配监听；监听回调在退出后不会更新旧标签。腰带背景仍借用 ControlPanel 资产，
只清引用，不重复释放借用纹理。

真实会话探针改为递归清点 Inventory/Stash/Cube/Vendor/Control 下的全部网格，
本批每轮验证 11 张私有 GL 纹理（3 张既有面板纹理、8 个 ItemGrid/VendorGrid/
BeltGrid 填充纹理）存活并在 Client 退出后删除；保留原地图、按钮、共享框、表现
资源、输入、加载页和最终 GL 检查。

### 第十五批验证

- 首次专项 `build/grid-resources-focused.log` 暴露的失败只是测试夹具对未初始化
  Actor listener 数组调用真实 dispose 的 NPE，不是生产路径；移除该不完整 mock
  断言并保留面板实际所有权验证。
- `build/grid-resources-focused-03.log`：30 项专项、三端编译通过；面板资源及
  共享框回归无失败。
- `build/grid-resources-regression.log`：13 类 154 项，失败/错误 0，跳过 2 项；
  包含全部 panel 测试、共享框生命周期、GameScreen 装配与菜单页回归。
- `build/grid-resources-offscreen-02.log`：同一 Client 三轮真实会话通过，每轮
  `ownedPanelTexturesDeleted=11`、共享框 `0→12→0`、NPC 菜单 10 节点退休，
  最终 ClientShutdown 通过；BUILD SUCCESSFUL，1m59s。该探针仍记录慢帧，未作
  性能或人工画面验收。

本批仍不覆盖网格之外的长期压力、连接交接、控制台/Timer/异步日志、跨 Act/加载中
退出、人工输入声音联机存档验收；资源阶段还需继续检查其他私有 GL Actor 和完整
构造失败回滚。

## 第十六批：雇佣兵面板与头像 HUD 私有纹理

本批收口 `HirelingPanel` 与 `MercenaryHud` 的私有白色填充纹理。两个组件捕获构造
时的原 AssetManager；HirelingPanel 改用 PanelTextures 的安全上传路径，退出时清理
自身监听/子 Actor、按钮、填充纹理及五项页面资产；MercenaryHud 退出时清理头像
标签和点击监听、填充纹理及全部雇佣兵/召唤物图标引用。两者退出幂等，旧 act/draw
不会继续访问已释放状态，替换全局 AssetManager 也不会把引用卸载到错误宿主。

真实会话探针在上一批 11 张网格/面板纹理之外增加 HirelingPanel 与 MercenaryHud
两张私有纹理，要求每轮共 13 张纹理存活、退出后全部删除；保留共享框、菜单树、
地图、表现资源、输入、加载页和 ClientShutdown 检查。

### 第十六批验证

- `build/mercenary-resources-focused.log`：雇佣兵 HUD 探针、面板资源、共享框专项
  与三端编译通过，BUILD SUCCESSFUL，1m3s。
- `build/mercenary-resources-offscreen.log`：同一 Client 三轮真实会话和独立营地
  通过；每轮 `ownedPanelTexturesDeleted=13`、共享框 `0→12→0`、10 个 NPC 菜单
  节点退休，最终 ClientShutdown 通过；BUILD SUCCESSFUL，1m58s。慢帧仍不作
  性能验收，人工画面/输入/声音/联机/存档仍待。

本批仍未覆盖其他私有 GL Actor 的完整构造失败回滚、连接交接、控制台/Timer/异步
日志、跨 Act/加载中退出和长期压力；资源阶段继续按宿主归属逐项收口。

## 第十七批：招募、帮助与传送小面板资源宿主

本批收口 `HireMercenaryPanel`、`HelpPanel` 和 `WaygatePanel`。三个组件现在在构造时
捕获原始 `AssetManager`，后续加载、完成加载和卸载均不再读取可变的
`Riiablo.assets` 全局别名；按钮自身的声音引用也沿用其创建时捕获的宿主。三个面板
的 `dispose` 均幂等，退出后禁用输入、清除监听和子 Actor，并阻止旧 `act`/`draw`、
打开或网络回调继续访问已释放资源。

`HireMercenaryPanel` 的 `textslid.dc6` 仍保持延迟加载语义：首次展示招募列表时请求，
退出时即使加载尚未完成也先完成自己的请求再归还引用，避免取消同名资源的其他使用者。
`HelpPanel` 和 `WaygatePanel` 的四项资源在构造时取得，退出逐项归还；传送点按钮、
帮助关闭按钮和菜单子项不再留在旧 Stage 上。

### 第十七批验证

- `:core:compileJava :core:compileTestJava` 通过。
- `PanelResourceLifecycleTest`、`HelpPanelLayoutTest`、`NativeHirelingOfferIntegrationTest`
  定向回归通过；随后 `ButtonLifecycleTest` 也通过。
- 使用原版 1.10f MPQ 的 `:desktop:offscreenHireMercenaries` 通过，生成实际
  招募窗口 PNG 与 `roster.tsv`，日志为 `build/hire17-preview.log`。
- 同一 Client 三轮 `:desktop:offscreenSessionLifecycle` 通过；每轮仍删除 13 张私有
  网格/面板/HUD 纹理，共享框 `0→12→0`，NPC 菜单 10 个节点退休，最终
  `ClientShutdown result=PASS`，日志为 `build/hire17-session.log`。

本批没有宣称移动端 `MobileControls`/`MobilePanel` 已覆盖；它们的构造路径和专用
生命周期测试留到下一批。慢帧、人工画面/输入/声音、联机/存档、跨 Act、加载中退出、
完整构造失败回滚及长期压力仍未完成验收。

## 第十八批：移动端菜单与操作栏归属

`MobilePanel` 与 `MobileControls` 捕获构造时的 AssetManager，用 AssetReferenceScope
逐项配对自己的 DC6 请求；构造中途失败释放已取得的页面引用及已返回的按钮实例。
退出幂等，异常/Error 不跳过其余资源，清除注入的借用引用但不销毁 World、Socket、
Actioneer 或其他面板。旧 act/draw、点击、短按和长按在退出后不再访问当前会话。

MobilePanel 显式持有并释放全部九个按钮，而非只释放角色和背包按钮：包括未挂入
表格的地图按钮，以及首帧被 GameScreen 移到 Stage 顶层的退出按钮。逐按钮清除
监听、从父节点移除并归还按钮声音；再清理自身及页面资产。MobileControls 对
交互按钮和五个技能按钮做同样清理，取消 GestureDetector 长按任务。快捷选择器
只借用移动技能槽；退出时仅当它仍指向本操作栏的槽位才解除 observer 并隐藏，
不清除已替换的新 observer，也不释放共享选择器、字体或贴图。

离屏会话探针新增可选 `-PmobileUiLifecycle`：在桌面隐藏 GL 客户端显式创建这两个
生产移动控件，仍执行真实 GameScreen.show 注入、首帧退出按钮重挂载与 Client
退出。每轮检查 15 个按钮退休、三项移动 UI 资产回构造前基线及对应 GL 删除，
并留下活动快捷选择器交给生产退出。此路径不是 Android 平台模拟，也不是触屏/
软键盘/真机性能或视觉验收；默认桌面探针行为不变。

### 第十八批验证

- 最初单项夹具因 libGDX app 未初始化触发 Cell 递归；补齐应用后又暴露路径标准化
  断言差异，修正为 AssetDescriptor 使用的 `/`。扩展专项首轮
  `build/mobile-resources-focused.log` 的 11 项中 6 项因缺少借用 white 纹理夹具失败；
  补齐夹具，没有修改生产断言或技能规则。
- `build/mobile-resources-focused-02.log`：11 项全部通过，失败/错误/跳过 0；
  core、desktop、D2GS 编译通过，43s。专项使用真实 AssetManager/控件，覆盖全部
  按钮、未挂载/重挂载按钮、共享资产和排队声音、全局宿主替换、手势取消、迟到
  回调、observer 身份、异常清理与部分构造回滚，无需 MPQ、真实 GL 或 Android。
- `build/mobile-resources-regression-gl.log`：扩大定向 14 类 106 项，失败/错误 0、
  跳过 2；同一 Client 三轮每轮 `mobileButtonsRetired=15`、
  `mobileAssetBaselinesBalanced=3`、`mobileTexturesDeleted=102`，构造前/退出后按钮
  声音引用均 0；保留原 13 张私有纹理、共享框 `0→12→0`、10 个 NPC 菜单节点、
  地图、表现资源和 ClientShutdown 检查，BUILD SUCCESSFUL，2m36s。首轮装配测试
  筛选误用不存在的类名，未计入装配覆盖，最终复跑使用 GameScreenAssemblyTest。
- `build/mobile-resources-final.log`：正确装配筛选后共 15 类 115 项，失败/错误 0、
  跳过 2（佣兵独立环境资源探针）；新增断言先确认两个移动宿主已由 Client 释放，
  再测试重复 dispose，避免探针手动清理掩盖归属缺失。原版 1.10f MPQ 三轮仍分别
  退休 15 个按钮、归还三项移动 UI 资源、删除 102 张相关共享纹理，私有纹理 13 张、
  共享框 `0→12→0`、菜单 10 节点及 ClientShutdown 通过，BUILD SUCCESSFUL，2m51s。
  退出剩余资产种类为 55/55/55，不据此声称整个资源阶段零泄漏。仍报告
  42.69–89.03ms 慢帧，超过 40ms 预算，不作性能或人工验收。

最小移动 UI 复跑（桌面宿主，原版 MPQ，同一 Client 三轮）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.MobilePanelLifecycleTest `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/mobile-resources-recheck-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/mobile-resources-recheck-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

完整控件内部构造失败回滚、其余技能/任务等面板的宿主、连接交接、控制台/Timer/
异步日志、跨 Act/加载中退出、长期压力及人工/性能验收仍待；资源阶段尚未结束。

## 第十九批：技能书与快捷技能选择器

`SpellsPanel` 与左右两个 `SpellsQuickPanel` 捕获原 AssetManager 和 CharData，
用 AssetReferenceScope 配对各自的 DC6 请求。面板退出幂等，逐项清理按钮声音、
监听、Stage 归属及页面引用；构造失败回滚已提交的请求与已返回的按钮实例。
技能书的字体样式改为实例字段，不让静态样式跨客户端持有上一宿主的字体。
共享字体、Socket、CharData、ItemData 只是借用，不由面板销毁。

CharData / ItemData 增加按对象身份移除 skill / alternate listener 的接口，
面板独立销毁时解绑自己注册的监听，不清空其他消费者。快捷选择器保存原 ItemData
与 alternate listener；退出同时解除全体技能热键和 observer。旧点击、热键、武器
切换、技能通知及 act/draw 均受生命周期保护；外来角色的通知不触发重建。
技能书的关闭、页签和加点回调也受同一保护。

快捷技能重建用代次标记让已保存的旧按钮点击失效；仍按原有技能规则、行及技能书
顺序布局。返回的按钮先交给行表格持有，再执行可能失败的后续初始化，失败时清掉
本次已返回按钮和映射。清理遍历全部行子 Actor 而非可能覆盖相同技能 ID 的 IntMap，
同时存在的充能/已学技能不会漏释放。单个声音或子控件清理异常仍继续其余行及资产。
observer 为 null（移动端）时仍可正常选择技能，不再直接解引用。

真实会话探针在原有面板基线上增加 generic 技能图标、技能书小写扩展名图标、背景
和关闭按钮，合计检查 12 项资产；逐次采样重建前的技能按钮，并要求生产 Client
已销毁三个技能宿主后才验证重复 dispose。保留可选移动组件路径及前批所有检查。

### 第十九批验证

- `build/spells-resources-compile.log`：core 主代码与测试源码编译通过。
- 首轮 `build/spells-resources-focused.log` 共 12 项，新增的 10 项均通过；旧的
  CALLS_REAL_METHODS 夹具绕过 Actor 构造，新增清理访问了不存在的 listeners，
  导致其中 1 项失败。补齐该夹具的生命周期字段与 Actor 桩，保留原异常传播及
  全行/热键/图标释放断言；后续真实控件测试也单独覆盖这些清理路径。
- 中间版本 `build/spells-resources-regression-gl.log` 共 16 类 126 项，失败/错误 0、
  跳过 2；三轮会话与 ClientShutdown 通过，2m31s。
- 最终 `build/spells-resources-final.log`：新增专项扩至 14 项且全部通过；扩大定向
  16 类共 129 项，127 通过、2 跳过（佣兵独立环境资源探针），失败/错误 0。
  core、desktop、D2GS 编译链通过，BUILD SUCCESSFUL，2m50s。
- 最终原版 1.10f MPQ 三轮均通过：每轮 3 次重建累计退休 21 个快捷技能按钮，
  退出退休 40 个技能/关闭按钮，三个技能宿主均由 Client 销毁，12 项面板资产
  回基线，退出按钮声音引用 0。移动组件仍退休 15 个按钮、归还 3 项资源并删除
  102 张相关共享纹理；原 13 张私有纹理、共享框 `0→12→0`、10 个 NPC 菜单节点
  和最终 ClientShutdown 通过。
- 最终会话退出剩余资产种类 55/55/55，不据此宣称全部会话资源零泄漏；中间版本
  的 54/54/54 与上批清单差异仅为懒加载的 MedDirt3 脚步声缓存，不算本批修复成果。
  最终仍有 49.94–77.75ms 慢帧，超过 40ms 预算，不作性能、人工画面/输入/声音、
  联机或存档验收。

最小专项复跑（不需 MPQ/GL）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.SpellsPanelResourceLifecycleTest `
  --tests com.riiablo.screen.panel.SpellsQuickPanelLifecycleTest --no-daemon
```

完整定向及真实 1.10f 三轮复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/spells-resources-recheck-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/spells-resources-recheck-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

仍未覆盖 Button/HotkeyButton 内部构造尚未返回实例即失败的完整回滚；这不等于整个
控件树所有构造路径安全。任务面板等其余宿主、连接交接、控制台/Timer/异步日志、
跨 Act/加载中退出、长期压力、Android 真机与人工/性能验收继续留待后续批次。

## 第二十批：任务面板、任务语音与滚动控件

`QuestsPanel` 捕获构造时的 AssetManager / CharData，用 AssetReferenceScope 配对
六项公共 DC6 与全部可见任务图标的请求；即使文件名重复也保留每次提交的引用。
退出幂等，停止全部五个页签（包括隐藏页签）的滚动语音，解除完成回调，清除控件树
的普通/捕获监听与动作，移除 Stage 归属，释放七个按钮声音及全部已取得的页面引用。
部分构造失败同样清理已返回的按钮和页签，不依赖 GameScreen 递归释放声音。
旧角色的点击、页签、回放、完成通知与 act/draw 不再访问新会话。

`DialogScroller` 原来的 dispose 仅停止语音，任务页签一直把它用作可复用的“停止”，
最终退出反而没有销毁这五个控件。现在区分可复用的 stop 与最终、幂等的 dispose：
设置普通文本、重复回放、切换任务/页签和隐藏整个任务面板会先停止旧语音；重新打开
仍能回放。最终销毁清空音频实例与完成监听，拆除子控件，并忽略迟到的播放/文字/
act/draw 调用。音频停止前先清除自己持有的实例，异常不会导致重复停止旧实例。

滚动控件捕获原 Audio 与实例字体，不再用静态字段持有上一客户端字体；字体和音频
宿主仅借用，不由控件销毁。静态任务文本不触发完成，单次回放只完成一次；缺失语音
映射和缺少时长前缀的文本也不会丢失旧音频归属。任务状态判定、任务排序和图标帧
仍沿用原有规则，本批没有扩展 Act I 以外的任务实现。

任务图标动画退出时 reset；同时修复 Animation.Layer 归还池时仍保留 DC / regions /
transform 的引用，归还时只清空借用引用，不 dispose 共享贴图。重新取用仍由原 set
完整初始化渲染状态，两项池生命周期测试与既有动画回归共同约束此变更。这不代表
其他池、全部控件内部构造失败或所有异步入口均已完成审计。

真实会话探针现在实际选择 Act I 第一个任务，执行三次生产回放、一次关闭后重开，
让最后一次语音留给生产退出流程清理；采样全部任务控件和已上传任务贴图，检查
五个 scroller 已由面板销毁、回放实例停止、资产回构造前基线及 GL 贴图删除。
保留前批技能、移动 UI、地图、私有纹理、共享框和最终 ClientShutdown 检查。

### 第二十批验证

- `build/quests-resources-compile.log`：core 主代码及已有测试源码编译通过。
- `build/quests-resources-focused.log` 首轮因 mock 辅助方法导入缺失而编译失败。
  `build/quests-resources-focused-02.log` 的 13 项中有 2 项夹具失败：模拟切回第一页
  时没有执行页签点击，字体替身也缺少 BitmapFont 内部数据；修正为真实页签回调和
  完整字体夹具，没有放宽生产断言。随后 `build/quests-resources-regression-gl.log`
  176 项中 1 项因 Mockito 嵌套求值导致未完成桩配置而失败、2 项跳过，尚未运行 GL；
  将被引用的字体区域先保存到局部变量后修正，保留上述失败日志。
- 最终 `build/quests-resources-final.log`：新增 13 项任务/滚动控件专项与 2 项动画池
  生命周期专项全部通过；扩大到 25 类 176 项，174 通过、2 跳过（佣兵独立环境
  资源探针），失败/错误 0。包含既有动画、音频和 Act I 任务呈现回归，
  core、desktop、D2GS 编译链通过，BUILD SUCCESSFUL，2m34s。
- 同一 Client 三轮真实 MPQ/GL 会话均通过：每轮三次任务语音回放、一次关闭后重开，
  退出时 146 个任务控件解除父节点、监听与动作，五个滚动控件销毁，三个回放实例
  均停止，33 项任务资产回基线、794 张相关纹理删除。前批每轮技能重建退休 21 个
  旧按钮、退出退休 40 个技能按钮、移动组件 15 按钮/102 张纹理、13 张私有纹理、
  共享框 `0→12→0` 和 10 个 NPC 菜单节点检查继续通过，最终 ClientShutdown 通过。
- 每轮退出后资产种类为 55/55/55，不等价于进程零滞留。仍记录 52.82–89.51ms
  慢帧，超过 40ms 预算，不作性能验收；语音实例和引用检查也不替代人工听感、
  画面/输入、Android 真机、联机和存档验收。

专项复跑（无需 MPQ/GL）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.QuestsPanelLifecycleTest `
  --tests com.riiablo.codec.AnimationLayerLifecycleTest --no-daemon
```

扩大定向及原版 1.10f 三轮复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/quests-resources-recheck-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/quests-resources-recheck-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

资源阶段继续：EscapePanel 设置/按键捕获与其他尚使用可变资源宿主的面板、完整控件
内部构造回滚、连接交接、控制台/Timer/异步日志、跨 Act/加载中退出、长期压力、
Android 真机及人工/性能验收仍待，不能据本批宣称完整资源阶段完成或进程零滞留。

## 第二十一批：Escape 菜单、设置页与按键捕获

`EscapePanel` 捕获构造时的 AssetManager / CharData / GdxKeyMapper / Audio，
用 AssetReferenceScope 配对本地化的 options、exit、returntogame 以及 pentspin
四项 DC6 请求。保留本地化按钮的独立帧加载方式，不启用 COMBINE；设置规则、
键位保存格式、玩法和存档不变。字体、角色、按键及 Audio 宿主仅借用。

主菜单、选项、声音、视频、自动地图和按键六个页面在创建时登记，尚未挂到根面板
的页面也能在构造失败后清理。最终退出幂等，清除整树普通/捕获监听、动作和父节点，
清空按键行与页面索引，reset 两个旋转动画后归还资产。焦点或某项卸载抛出 Error
时仍尝试其余清理，不重复归还已经尝试的引用；这不代表全部控件内部构造失败路径
或所有 Stage 错误都已验收。

原来按键捕获只有全局 boolean，隐藏菜单、离开按键页或退出没有统一解除；旧菜单
还可能把后来菜单的捕获关闭。GdxKeyMapper 现在提供按对象身份归属的 begin / end，
只允许当前 owner 解除捕获；兼容的 setCaptureMode 会使旧 token 失效，不让旧 owner
解除后续未署名捕获。捕获期间 gameplay 输入仍沿用原来的跳过逻辑。

菜单在关闭、隐藏、返回父页、取消、保存、清空、恢复默认和最终销毁时释放自己的
捕获；保存原 Stage，只在其 keyboardFocus 仍是本菜单时清焦点，不清后来 Actor 的
焦点。重新挂载后再次捕获会先解除原 Stage 的焦点。焦点获取被拒绝或抛出异常时
回滚捕获，避免留下屏蔽 gameplay 的全局开关。旧会话/已销毁菜单的点击、悬停、
按键及 act/draw 均不再访问新角色/按键宿主或触发退出；销毁后不能重新打开。

真实会话探针实际通过菜单按钮进入按键页，分别验证返回父页、隐藏和最终退出三次
捕获的释放。最后一次捕获及焦点留给生产 Client teardown，不提前手工 dispose。
退出后检查六页全树、迟到按键、四项资产构造前基线和已采样 GL 贴图删除；保留
任务语音、技能重建、移动 UI、地图、私有纹理、共享框及 ClientShutdown 检查。

### 第二十一批验证

- `build/escape-resources-compile.log`：core 主代码与原有测试源码编译通过。
- 首轮 `build/escape-resources-targeted.log` 的 19 项中 2 项失败：清除主键的断言
  忽略了现有 MappedKey 将副键前移的规则；另一个断言将 Stage 清子控件的正常调用
  误计为越权修改焦点。修正为保留原有键位语义、只禁止修改新焦点，随后
  `build/escape-resources-targeted-02.log` 的 19 项全部通过。
- 扩大前补充三项专项，并将夹具导航改为生产按钮回调。
  `build/escape-resources-regression-gl.log` 因测试误调用 Audio 不存在的 dispose
  方法而编译失败；`build/escape-resources-regression-gl-02.log` 的 198 项中 2 项
  因迟到回调断言未扣除导航阶段两次正常选择音而失败、2 项跳过，未运行 GL。
  现分别验证并清除导航阶段调用记录，再检查迟到回调零调用，保留失败日志。
- 最终 `build/escape-resources-final.log`：18 项真实 Escape 控件/资产专项及 4 项
  GdxKeyMapper 捕获归属/生产输入专项全部通过；扩大定向为 27 类 198 项，196 通过、
  2 跳过（佣兵独立环境资源探针），失败/错误 0。core、desktop、D2GS 编译链通过，
  BUILD SUCCESSFUL，2m37s。
- 同一 Client 的三轮原版 1.10f MPQ/GL 会话均通过：每轮三次真实菜单按键捕获，
  返回父页、隐藏和最终退出均释放捕获/焦点，208 个菜单控件清除父节点、普通/
  捕获监听与动作，四项 Escape 资产回基线、12 张相关纹理删除，迟到按键无效。
  保留每轮任务 146 控件/5 个 scroller/33 项资产/794 张纹理、技能重建 21 个旧
  按钮/退出 40 个按钮、移动 UI 15 按钮/102 张纹理、私有纹理 13 张、共享框
  `0→12→0`、10 个 NPC 菜单及地图资源检查，最终 ClientShutdown 通过。
- 每轮退出后资产种类为 56/56/56，剩余资产名称/类型/引用一致；对比前批多出的
  一项为实际菜单导航首次触发、由 Audio 宿主缓存的 `data/global/sfx/cursor/select.wav`
  （refs=1），不是 Escape 四项 DC6 的引用累积。最终宿主退出 AssetManager 资产为 0，
  但不能据此宣称整个进程零滞留。仍有 46.31–95.04ms 慢帧，超过 40ms 预算，
  不作人工画面/听感、Android 真机、联机、完整存档或性能验收。

专项复跑（无需 MPQ/GL，不写真实偏好或角色键位文件）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.EscapePanelLifecycleTest `
  --tests com.riiablo.GdxKeyMapperCaptureTest --no-daemon
```

扩大定向及原版 1.10f 三轮复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.GdxKeyMapperCaptureTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/escape-resources-recheck-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/escape-resources-recheck-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

下一批优先 CubePanel / StashPanel 等仍使用可变全局 AssetManager 卸载的剩余面板。
完整控件内部构造回滚、连接交接、控制台/Timer/异步日志、跨 Act/加载中退出、长期
压力、Android 真机和人工/性能验收仍待，资源阶段尚未结束。

## 第二十二批：方块、仓库与共享金币对话框

`CubePanel` / `StashPanel` 捕获原 AssetManager / CharData / ItemData，页面 DC6
通过 AssetReferenceScope 配对；退出不再向后来的全局资源宿主卸载。独立释放方块
两个按钮、仓库两个按钮、网格私有 fill 和仓库金币对话框，不再依赖 GameScreen
递归释放按钮声音。构造中途失败清理已经返回的按钮/网格/对话框和已提交引用；
某项清理抛 RuntimeException / Error 时仍尝试其余项，幂等退出不重复归还。

`ActorTree.clear` 只清理明确拥有的控件树的普通/捕获监听、动作和父节点，不调用
任意子控件的 dispose，不销毁共享 style、字体或物品贴图。面板自己按字段释放资源，
包括部分构造时尚未挂载的控件。两面板的网格使用 ItemGrid 的 isActive 扩展点，
使旧角色/旧资源宿主的保存点击、物品按钮和绘制不能再操作新会话；其他网格默认
仍只使用原来的 disposed 判定。拖入、拾取和交换仍路由原 CUBE / STASH 操作。

仓库拥有的 `GoldAmountDialog` 也存在全局卸载、非幂等退出和回调保留问题，因此
本批一起收口这个共享控件，并回归背包对话框的生产退出路径。它捕获原资源宿主/
角色，配对三项 DC6，独立清理四个按钮声音、输入过滤/监听、提交/上限回调和控件
树。构造失败也释放已取得资源；最终销毁后不能重开，迟到提交、取消、箭头和输入
不再触及会话。面板和对话框按钮在所属会话过期/销毁后视为 disabled，使保存的
按下回调也不会访问后来的输入宿主以播放声音或震动。

对话框记录获得焦点时的原 Stage，只清仍属于自己的输入框焦点；隐藏仓库/关闭
弹窗释放焦点但保留可重开状态，重新挂载后打开先解除旧 Stage 焦点。焦点获取
被拒绝/抛异常时隐藏弹窗，焦点释放失败仍继续销毁按钮和资产。关闭会通过 libGDX
默认输入监听的 keyUp 取消连按，销毁后 disable 输入框；Blink/KeyRepeat 的已排队
任务依赖 libGDX 在发现输入框离开 Stage 后自行取消，不等于全局 Timer 审计完成。

新测试发现原取消按钮的 ClickListener 内部未限定的 cancel() 实际调用了继承的
ClickListener.cancel()，未关闭弹窗或通知取消；现在明确调用对话框的 cancel。
保留直接 touchUp 取消路径、数字过滤、上限钳制、负数选项、箭头不低于零、单次
提交和正常重开语义。没有新增方块合成、改金币规则、协议或存档格式。

真实会话探针增加两面板与背包/仓库两个金币对话框的控件采样，实际通过仓库按钮
打开、隐藏关闭、重新打开，再通过背包按钮打开另一弹窗；两者均留给生产退出清理。
检查回调、输入和 Stage 归属已退休、八项（保留文件名大小写的）资产回构造前基线、
已采样纹理删除，并保留前批 Escape、任务、技能、移动 UI、私有纹理、地图、共享框
与最终 ClientShutdown 检查。这不等于金币交易或方块玩法的人工验收。

### 第二十二批验证

- `build/storage-resources-compile.log`：core 主代码与原有测试源码编译通过。
- `build/storage-resources-targeted.log` 首轮 19 项中 2 项失败：一项暴露取消按钮
  误调继承方法的生产缺陷，已限定为对话框取消；一项为字体 mock 的已有 Answer
  在重新设桩时调用内部 mock，导致 BitmapFontCache 被错误地配置到布尔方法上。
  `build/storage-resources-regression-gl.log` 扩大到 217 项后仍有这项夹具失败、
  2 项跳过，未运行 GL；最终改用 doReturn / doThrow，保留原有故障断言。
  随后另补一项保存的按下回调不得震动新宿主的专项，共 20 项。
- `build/storage-resources-final.log`：20 项新增专项全部通过；扩大到 28 类 218 项，
  216 通过、2 跳过（既有佣兵独立环境资源探针），失败/错误 0。core、desktop、
  D2GS 编译链通过，三轮 MPQ/GL 及最终 ClientShutdown 通过，2m55s。
  每轮退休 23 个相关控件、两个金币对话框，八项资产回基线、57 张纹理删除；
  保留 Escape 三次捕获、任务、技能、移动 UI 等全部既有检查。
  随后仅将 close 中取消连按和释放焦点拆为独立清理尝试，最终代码另行复跑。
- 最终代码 `build/storage-resources-verified.log`：同一 28 类 218 项再次通过
  （216 通过、2 跳过、失败/错误 0），core / desktop / D2GS 编译链通过，
  三轮真实 MPQ/GL、全部前批检查及 ClientShutdown 再次通过，BUILD SUCCESSFUL，
  2m57s。每轮仍为 23 个相关控件、两个金币弹窗、八项资产基线与 57 张纹理；
  面板/弹窗按钮声音由各自明确的宿主配对，不依赖顶层声音遍历兜底。
- 最终三轮退出后资产种类为 56/57/57：对比剩余资产的名称/类型/引用，第二轮
  比首轮及前批 Escape 导航基线多出 `data/global/sfx/ambient/footstep/MedDirt3.wav`
  （Sound，refs=1），第三轮不再增加。这是 Audio 按 Sounds 条目首次加载并保留的
  脚步声宿主缓存，不是八项存储面板/DC6 的引用累积；不报告总数或全体资产一致。
  最终 ClientShutdown 资产为 0，不代表进程零滞留。
  最终复跑仍记录 44.96–73.53ms 慢帧，超过 40ms 预算，不作性能、人工输入/
  听感、金币交易、方块合成、联机或 Android 真机验收。

无需 MPQ/GL 的专项复跑（仅拦截私有 Texture 上传，真实面板/网格/弹窗/引用计数）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.StoragePanelLifecycleTest --no-daemon
```

扩大定向及原版 1.10f 三轮复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.GdxKeyMapperCaptureTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/storage-resources-recheck-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/storage-resources-recheck-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

下一批优先 InventoryPanel 剩余会话回调、匿名 AlternateListener 的独立解绑和构造
回滚边界。借用全局角色的其他控件、完整控件内部构造失败、连接交接、控制台/
Timer/异步日志、跨 Act/加载中退出、长期压力、Android 真机及人工/性能验收仍待，
不能据本批宣称资源阶段结束。

## 第二十三批：背包资源、武器切换监听与所属会话

`InventoryPanel` 捕获原 AssetManager、CharData / ItemData、Audio 与 Cursor，
以 `AssetReferenceScope` 记录十项成功提交的页面资产。部分构造失败回滚已经取得
的引用、已返回的按钮/网格/金币对话框和两张私有 fill；某项清理失败仍继续其余项，
保留原错误与 suppressed 异常，不销毁借用的音频、光标、字体、装备图像或物品数据。
控件内部尚未返回实例就抛错的完整审计仍留后批，不宣称所有构造边界已覆盖。

武器切换 `AlternateListener` 保存身份，注册前先记录，背包退出独立从原 ItemData
移除，不依赖最终 CharData 批量清理。保留读档后初始化当前武器组，当前监听按原
左右手映射更新；来自其他 ItemData、旧角色/物品数据或已销毁面板的通知直接失效。
退出清理装备缓存与背景引用、监听/动作/父节点、控制器和金币宿主回调，重复退出
不重复释放，也不自动重试已经失败的清理。

生产 `GameScreen` 显式把自身传给背包。构造期间允许填充初始网格，构造完成后
只有原 GameScreen 是当前全局会话时才允许输入/绘制/更新；这样即使重用同一个
CharData / AssetManager，旧面板也不能向新游戏路由物品或商店操作。退出清空 owner，
避免保存旧控件时额外牵住整个 GameScreen。无参入口保留，若有当前游戏则必须是
其 inventoryPanel。原资源、角色、物品数据、音频或光标被替换后同样停止派发。

装备点击、武器组按钮、金币提交/上限、网格操作、act/draw 和装备目标查询均受
会话门禁保护；按钮过期后视为 disabled，保存的按下回调也不能播放声音或震动
后来的输入宿主。目标查询只接受本面板的 BodyPart，不接受另一背包的装备 Actor。
维持原库存/腰带/装备/商店/金币路由与限制，不改玩法、25Hz、协议或存档格式。

真实同 Client 三轮探针增加整个背包控件树、具名监听注册与独立移除、保存点击
回调失效、十项资产回构造前基线及采样 GL 纹理删除检查，继续保留第二十二批
仓库/方块/金币弹窗和此前所有检查；不能替代人工装备操作、交易或听感验收。

### 第二十三批验证

- `build/inventory-resources-targeted.log` 首次因新测试把 Java List 的 get(0)
  写成 first() 而未通过测试源码编译，已修正。
- `build/inventory-resources-targeted-recheck.log`：42 项中 4 项失败，均为旧
  `PanelResourceLifecycleTest` 的模拟按钮缺少 getChildren 场景树桩，不能用于
  新增的 ActorTree 清理。补齐按钮空树及原夹具的引用作用域；保留原有资产独立
  消费者、重复释放和异常继续断言。首轮新增背包专项 15 项通过，随后另补两项
  真实声音卸载失败继续清理、同角色内替换 ItemData 的隔离测试，共新增 17 项。
- `build/inventory-resources-regression-gl.log`：最终代码新增 17 项全部通过，
  扩大到 28 类 235 项，233 通过、2 跳过（既有佣兵独立环境资源探针），失败/
  错误 0；core / desktop / D2GS 编译链通过。三轮真实 MPQ/GL 和最终
  ClientShutdown 通过，BUILD SUCCESSFUL，3m。每轮退休背包 26 个控件、
  36 个保存点击回调失效、独立监听移除、十项资产回基线、37 张采样纹理删除。
  保留仓库/方块 23 个控件、两个金币弹窗、八项资产/57 张纹理，Escape 208
  控件/三次捕获/四项资产/12 张纹理，任务 146 控件/五个滚动控件/三次重播/
  33 项资产/794 张纹理，技能重建退休 21 个旧按钮/退出 40 个按钮、移动 UI
  15 个按钮/102 张纹理，以及 13 张私有纹理、共享框、地图、输入等既有检查。
- 三轮退出后资产种类均为 56，最终 ClientShutdown assets=0。这仅说明本次
  正常进出探针的资产计数与采样通过，不代表长压、全进程零滞留或所有缓存均已
  验收。仍记录 42.13–67.29ms 慢帧，超过 40ms 预算；不作性能、人工画面/
  输入/听感、装备交易玩法、联机或 Android 真机验收。

无需 MPQ/GL 的背包与存储专项（拦截私有 Texture 上传，运行真实生产构造与
AssetManager 引用计数）：

```powershell
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.StoragePanelLifecycleTest `
  --tests com.riiablo.screen.panel.PanelResourceLifecycleTest --no-daemon
```

扩大定向及原版 1.10f 三轮复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.GdxKeyMapperCaptureTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/inventory-resources-recheck-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/inventory-resources-recheck-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

下一批优先 VendorPanel / 雇佣兵相关剩余控件的旧会话回调归属，不重做已配对的
正常资产释放；再继续控件内部构造失败、连接交接、控制台/Timer/异步日志、
跨 Act/加载中退出及长期压力验证。Android 真机、人工与性能验收单列，资源阶段
尚未结束；不以本批三轮正常进出替代这些边界。

## 第二十四批：商店会话回调与购买确认框捕获

保留 VendorPanel 已有的三项页面资产引用作用域与共享 boxpieces 边框归属，
不重做正常加载/卸载。本批捕获原 CharData / ItemData、Audio、Cursor，并由
生产 GameScreen 显式传入 owner；构造时允许创建网格，构造完成后必须仍是原
资源/角色/物品数据/音频/光标和当前 GameScreen 才能派发。无参入口保留，但有
当前游戏时须是该游戏的 vendorPanel；最终退出清空 owner、网络同步器/借用
socket、库存/定价/生成器引用与待处理操作，不销毁借用的物品、库存数组或宿主。

买/卖/修理/退出、页签、确认/取消、网格交易、配置/网络结果、act/draw 与报价
入口均受会话门禁保护。九个 Button 在旧会话视为 disabled，保存的按下回调不会
访问后来的输入宿主；VendorGrid 的交易入口、drain 和容量查询使用 isActive
扩展点，过期网格不再触及角色或调用购买监听。最终退出递归清理普通/捕获监听、
动作、父节点与页签链接，独立释放九个按钮声音、四张网格私有 fill、共享边框
引用和三项页面引用；已经返回但尚未挂载的网格/确认框也纳入清理。清理幂等、
部分构造失败回滚、异常继续，失败的操作只尝试一次，错误不静默吞掉。

购买确认框记录实际添加捕获监听的原 Stage，重开先释放旧捕获；隐藏、取消、
外部点击、退出和先脱离/换挂载后关闭均清理原 Stage，不误清新 Stage 的其他
监听。添加捕获或打开中途失败时回滚，移除捕获失败仍隐藏确认框、清空提交物品，
隐藏面板时仍清理待处理请求和交易模式，最终退出仍继续资源清理。只有当前会话
才能重置其交易光标，旧面板退出不重置后来会话的光标。

保留左键确认购买至光标、右键直接购买与取消交易模式、现有卖出/修理反馈、
定价函数、库存及协议语义；同一会话隐藏面板后到达的钱包快照仍按既有逻辑处理。
本批仅隔离送达旧面板实例的结果，不重写 ClientNetworkReceiver 的全局路由、
连接交接或同会话不同请求/服务的迟到响应关联；完整网络交接边界仍是后续工作。
也不宣称雇佣兵相关控件、库存重建的全部迟到 Actor 回调、控件内部尚未返回实例
的构造失败或物品图像自身生命周期已全部收口。不改玩法、25Hz、协议或存档格式。

真实探针在退出前使用一件未加载私有物品图像的合成药水记录，仅触发生产购买
确认 UI（不成交、不生成 NPC 库存），检查隐藏后重开和最终捕获清理；采样整个
商店控件树、保存点击/购买回调失效、四项资产基线和采样 GL 纹理删除，保留前批
所有检查。该探针不是交易玩法、联机、人工视觉/听感、长压或 Android 真机验收。

### 第二十四批验证

- `build/vendor-resources-targeted.log`：52 项中 4 项失败。新测试取到框架 Button
  内部点击监听而非最后添加的业务监听；修正动作选择后确认/取消/光标路径恢复。
- `build/vendor-resources-targeted-recheck.log`：52 项中仍有 2 项购买断言失败，
  测试把钱包扣款写死为 50，而原版表及既有交易定价入口计算为 30。移除人为
  注入的缓存报价，按现有交易定价函数断言钱包扣款，保留单次确认、光标目的地
  与直接购买的断言，不改生产定价算法。随后补隐藏捕获失败、仅替换 ItemData/
  AssetManager、借用库存不销毁三项，共新增 23 项。
- `build/vendor-resources-regression-gl.log`：最终代码 23 项新增专项全部通过，
  扩大定向包括既有 VendorPricingTest，共 30 类 280 项，278 通过、2 跳过
  （既有佣兵独立环境资源探针），失败/错误 0；core / desktop / D2GS 编译链
  通过。三轮真实 MPQ/GL、全部前批检查及 ClientShutdown 通过，BUILD SUCCESSFUL，
  3m12s。每轮商店 32 个控件退休、42 个保存点击回调失效，确认框隐藏/重开与
  最终原 Stage 捕获清理、四项资产基线和 54 张采样纹理删除通过。
  保留背包 26 控件/36 回调/十项资产/37 纹理、仓库/方块 23 控件/两个金币弹窗/
  八项资产/57 纹理、Escape 208 控件/三次捕获/四项资产/12 纹理、任务 146 控件/
  五个滚动控件/三次重播/33 项资产/794 纹理、技能重建退休 21 个旧按钮/退出
  40 个按钮、移动 UI 15 个按钮/102 纹理，以及 13 张私有纹理、共享框、地图、
  输入等既有检查。
- 三轮退出后资产种类 56/56/56，最终 ClientShutdown assets=0；不表示全进程
  零滞留、长期压力或完整网络交接通过。仍记录 47.71–93.53ms 慢帧，超过 40ms
  预算；人工画面/输入/听感、真实商店交易/联机及 Android 真机与性能验收均未完成。

需要原版资源表、但无需真实 GL 的专项（只拦截私有 Texture 上传）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.VendorPanelLifecycleTest `
  --tests com.riiablo.screen.panel.VendorRepairFeedbackTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest --no-daemon
```

扩大定向及原版 1.10f 三轮复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.GdxKeyMapperCaptureTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  --tests com.riiablo.item.VendorPricingTest `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/vendor-resources-recheck-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/vendor-resources-recheck-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

下一批优先 HirelingPanel / HireMercenaryPanel 等雇佣兵控件的旧会话归属；随后
继续完整控件内部构造失败、连接交接/网络接收路由、控制台/Timer/异步日志、
跨 Act/加载中退出及长期压力验证。人工、Android 真机与性能验收仍单列，
不以商店面板收口宣称资源阶段完成。

## 2026-10-16 第二十五批：雇佣兵装备与招募页的会话归属

HirelingPanel 捕获原 AssetManager / CharData / MercData / ItemData / Audio / Cursor
及显式 GameScreen owner，以 AssetReferenceScope 配对五项页面引用。GameScreen
在创建 World 后显式绑定两个面板，保留装备控制器的既有注入；世界只能绑定一次，
不能把缓存的 EntitySubscription 换到另一世界上继续使用。初始化可以在游戏发布
前进行，之后装备、按钮、统计刷新与绘制均限定原角色、宿主、世界和游戏。
退出独立释放一个按钮声音及私有 fill，退休完整控件树，清空装备缓存、页面、
控制器、订阅、World 和 owner；不销毁借用物品、字体、角色或世界级订阅。
部分构造失败回滚已经取得的资源，清理错误不阻止其他资源清理；失败操作不重试。

HireMercenaryPanel 捕获原资源、角色、MercData、MPQ 解析宿主和 GameScreen，使用
同样的一次性世界绑定；输入、选择/确认/取消、结果及 act/draw 不跨会话。textslid
懒加载和描述解析失败时归还本次引用并清空部分描述，重开可重试，不重复计入成功
引用。每次清状态/重建列表都递归退休旧 Actor；候选的第一行和第二行保存相同的
列表代次，清监听之外仍保证已保存的旧回调失效，不选择已退休候选。

招募页记录取得焦点的原 Stage 和原键盘/滚动焦点。重开先关闭旧归属，隐藏/关闭/
最终销毁在原 Stage 清理；只有焦点仍指向本页时才恢复仍在该 Stage 的原 Actor，
不抢走其他控件的新焦点、不恢复已脱离 Actor。获取拒绝/抛错时关闭回滚，释放
抛错仍继续清理另一焦点和资源；无 Stage 的 open 安全返回。最终销毁幂等并清空
请求、选择、候选、描述和宿主引用，普通关闭后仍可重新打开。

保留装备槽检查和 ItemController 的 true（佣兵）路由、原数值/字体基线、原版
雇佣报价、索引/seed/previousSeed 请求参数、替换确认、本地收费和超时不重付语义；
不改玩法、25Hz、协议或存档。只隔离送达旧面板实例的结果，不扩展为全局网络
接收器的连接交接或同会话迟到请求关联改造。完整控件内部尚未返回实例的构造
失败、MercenaryHud/其他 HUD 的全部旧回调，以及装备缓存实时同步并未在本批
宣称完成；装备缓存仍沿用原先初始化/本面板操作更新逻辑。

### 第二十五批验证

- `build/mercenary-resources-targeted.log`：测试夹具首次编译误用了不存在的
  BodyLoc.value，改用枚举顺序后复跑。
- `build/mercenary-resources-targeted-recheck.log`：29 项中 28 项失败，主因为新
  夹具未配置 PaletteIndexedColorDrawable 所需共享 white 纹理，补齐宿主夹具。
- `build/mercenary-resources-targeted-fixture.log`：29 项中剩 4 项失败；揭示当前
  Artemis 配置不能以 @Wire World 自动注入，生产装配改为显式一次性 bindWorld。
  另修正故障注入位置（Label.i18n 读取 StringTBL 而非 bundle）、抛错 mock 的
  重置方式及共享资源待队列处理后的引用数断言，不改游戏规则。
- `build/mercenary-resources-targeted-binding.log`：32 项专项全部通过，涵盖五项
  装备资产/按钮声音/私有 fill 的独立归还，兼容/不兼容装备路由、原世界绑定、
  构造失败与卸载异常、旧会话/退出后回调、懒加载/描述失败重试、旧行第一/第二
  行选择、两种焦点恢复/换挂载/脱离/拒绝/异常、隐藏/销毁和网络/本地雇佣参数、
  单次提交、15 秒超时不重付。
- `build/mercenary-resources-regression-gl.log`：最终代码扩大定向 32 类 317 项，
  315 通过、2 跳过（既有佣兵独立环境资源探针），失败/错误 0；包含既有
  NativeHirelingOfferIntegrationTest 五项原版表/收费/替换/存档回归和定价测试。
  core / desktop / D2GS 编译链通过，三轮真实 MPQ/GL 与最终 ClientShutdown
  通过，BUILD SUCCESSFUL，2m47s。
- 每轮两页面 44 控件退休、16 保存点击回调失效、重建退休 3 列表控件，隐藏
  恢复双焦点、退出释放原焦点、旧输入失效，六项资产回基线和 45 张采样纹理
  删除；检查报告为 `build/mercenary-resources-gl/cycle-{1,2,3}-lifecycle.txt`。
  探针用无效 NPC 类型打开生产模态路径（不会生成候选），再展示合成候选验证
  重建，只操作 UI，不实际雇佣或收费；生产收费逻辑由既有原版表集成测试覆盖。
  保留前批商店/背包/仓库/金币弹窗/Escape/任务/技能/移动 UI、13 私有纹理、
  共享框、地图和输入等所有检查。
- 三轮退出资产种类 56/56/57；逐名比较第三轮比第一轮多出
  `data/global/sfx/ambient/footstep/MedDirt3.wav`（Sound，refs=1），符合 Audio.play
  按 Sounds.Entry 保留 descriptor 的宿主懒缓存路径，并非本批六项资源残留；
  最终 ClientShutdown assets=0。不将短跑计数视为进程零滞留或长压通过。
  仍有 47.89–71.32ms 慢帧超过 40ms 预算，人工画面/输入/听感、真实联机、
  Android 真机和性能验收均未完成。

专项复跑（原版表、无真实 GL；仅拦截私有 Texture 上传）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.MercenaryPanelsLifecycleTest `
  --tests com.riiablo.engine.server.quest.NativeHirelingOfferIntegrationTest --no-daemon
```

扩大定向与三轮真实资源复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.GdxKeyMapperCaptureTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  --tests com.riiablo.item.VendorPricingTest `
  --tests com.riiablo.engine.server.quest.NativeHirelingOfferIntegrationTest `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/mercenary-resources-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/mercenary-resources-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

下一批优先 MercenaryHud 等剩余 HUD/控件的会话归属；随后继续完整控件内部构造
失败、连接交接/全局网络接收路由、控制台/Timer/异步日志、跨 Act/加载中退出及
长期压力验证。资源阶段尚未结束，人工/Android/性能验收继续单列。

## 2026-10-16 第二十六批：佣兵与召唤物头像 HUD

MercenaryHud 捕获原 AssetManager / CharData / Cursor / MPQ 宿主及显式 GameScreen
owner。GameScreen 在 World 创建后一次性绑定 HUD 的世界，禁止改绑；所有实体
收集和生命读取使用所属世界，缓存的佣兵/召唤物 EntitySubscription 不混入新
世界。当前会话身份覆盖点击/悬停、命中测试、药水入口、临时反馈、act 和两种
绘制入口，隐藏 HUD 不再接收头像交互，已保存的旧监听及退出后回调无效。

以 AssetReferenceScope 配对四种佣兵头像与十三种召唤物头像引用，支持仍在队列
中的独立退出，不卸载外部 owner 的共享引用。私有血条 fill 改用 PanelTextures
的 finally 上传像素清理路径；构造中途失败回滚已取得资源，原失败保留且附加
清理错误。最终退出先隐藏/禁用，递归退休完整控件树及尚未挂载的已返回 Label，
独立归还十七项引用和私有纹理，清空槽位、名称/计数 Label 缓存、反馈、控制器、
世界/订阅与 owner。清理幂等，某项抛错仍继续清理其他项，失败操作只尝试一次；
不销毁借用角色、物品、字体、控制器或世界级订阅。

PetType 名称静态缓存改按 MPQFileHandleResolver 身份匹配，换宿主后重新解析；
未提供解析宿主/缺少表的空结果不会污染后来新宿主。缓存只保存不可变字符串
映射，并通过 WeakReference 识别来源，不强持有旧 MPQ 宿主。保持同一 resolver
内的既有懒缓存/缺表回退，不声称同一 resolver 内动态改挂 MPQ 后自动热更新。

不改头像文件、佣兵优先与召唤物分组/排列、原有存活过滤/熊狼展示、名称解析
规则、血条位置/颜色阈值、药水类型/消耗/治疗逻辑或右键打开装备页语义；只是
限定所属且可见的头像交互。仍沿用现有变种头像回退，不扩展头像玩法或重写
CursorMovementSystem/网络接收器的全局路由。不改 25Hz、协议或存档。

### 第二十六批验证

- `build/mercenary-hud-targeted.log`：新测试初次编译因 component 通配导入中的
  Object / Class 与 java.lang 同名而失败，改用明确的组件导入。
- `build/mercenary-hud-targeted-recheck.log`：当时新增 23 项专项全部通过，连同
  既有头像资源探针共 27 项、2 跳过；随后增加一项当前头像和半血条绘制回归。
- `build/mercenary-hud-regression-gl.log`：341 项中 1 项失败、2 跳过；新增绘制
  测试的 DC6 夹具只模拟无参 getTexture，没有模拟生产使用的 getTexture(0)，
  导致空帧。补齐帧索引取图的夹具，不改生产绘制逻辑。
- `build/mercenary-hud-regression-gl-recheck.log`：最终代码新增 24 项专项全过，
  扩大定向 33 类 341 项，339 通过、2 跳过（既有佣兵独立环境资源探针），
  失败/错误 0；core / desktop / D2GS 编译链通过，三轮真实 MPQ/GL 与最终
  ClientShutdown 通过，BUILD SUCCESSFUL，2m51s。
- 专项覆盖十七项共享引用/排队引用和私有 fill 的独立归还、早期 load/后期 Label
  构造失败与附加清理错误、卸载异常继续、可见头像药水/右键/悬停/命中/反馈、
  原角色/游戏/光标/资源/MPQ/世界门禁、一次绑定和退出后全部回调失效。
  使用真实 Artemis World / component / subscription 验证原 owner 和存活过滤、
  分组计数、熊狼展示及旧订阅不查询新世界；验证当前图像/半血条绘制与 PetType
  缓存换宿主、缺表回退和弱来源/不可变映射。不销毁借用订阅或字体。
- 三轮报告 `build/mercenary-hud-gl/cycle-{1,2,3}-lifecycle.txt` 每轮 HUD 31 控件
  退休、两个保存输入监听的点击/进入/离开/移动回调失效、反馈/控制器/订阅清空、
  十七项头像资产回基线及 17 张采样纹理删除；私有 fill 包含在保留的 13 张私有
  纹理检查中。保留前批两雇佣兵页 44 控件/16 点击/六项资产/45 纹理，及商店、
  背包、金币弹窗、仓库、Escape、任务/语音、技能、移动 UI、共享框、地图、
  加载交接和输入检查。真实资源探针不创建佣兵/召唤物、不消费药水、不做联机。
- 三轮退出资产种类 56/56/56，最终 ClientShutdown assets=0；仅表明本次采样
  引用/纹理和宿主退出检查通过，不代表全进程零滞留或长期压力通过。仍记录
  41.27–72.79ms 慢帧，超过 40ms 预算；人工观感/输入/听感、实际药水玩法与
  联机、Android 真机及性能验收均未完成。

定向复跑（需要原版资源表、无真实 GL，仅拦截私有 Texture 上传）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.MercenaryHudLifecycleTest `
  --tests com.riiablo.screen.panel.MercenaryHudAssetProbeTest --no-daemon
```

扩大定向与三轮真实资源复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.GdxKeyMapperCaptureTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  --tests com.riiablo.item.VendorPricingTest `
  --tests com.riiablo.engine.server.quest.NativeHirelingOfferIntegrationTest `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/mercenary-hud-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/mercenary-hud-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

下一批优先 ControlPanel 剩余会话回调和动态子控件；完整控件内部尚未返回实例
的构造失败、连接交接/全局网络接收路由、控制台/Timer/异步日志、跨 Act/加载中
退出及长期压力验证仍待，资源阶段未结束。人工/Android/性能验收继续单列。

## 2026-10-17 第二十七批：ControlPanel 与嵌套 HUD 控件

ControlPanel 由 GameScreen 显式传入 owner，捕获原 AssetManager / CharData /
ItemData / Cursor / Audio / 输入宿主，并在世界创建后显式一次绑定 World。
允许未发布 owner 的构造填充，完成构造后要求当前游戏仍是该 owner；同一世界
重复绑定无副作用，拒绝换绑，销毁后不重新绑定。保留无参构造，捕获其初始世界，
不使用无法在当前 Artemis 配置下注入的 @Wire World。

提示按钮、任务日志确认/提示更新、运行切换、迷你菜单全部操作、腰带控制器与
网格、左右技能的点击/刷新/复制/绘制、面板 act/draw/命中和重新显示均受原
会话身份限制。保存的 Button 声音按下回调通过 disabled 门禁，不访问新输入
宿主；体力神殿从绑定世界查询。维持任务确认位、升级提示音、生命/法力/经验/
体力显示、布局、普通攻击技能 0、快捷键映射、技能禁用规则、菜单路由、腰带
槽位/用药参数及显示语义，不改 25Hz、协议、存档或暗黑玩法。

父页十五项资源、ControlWidget 三项、MinipanelWidget 一项，各由原 manager 的
AssetReferenceScope 配对；十九项不包含 Button 既有的独立点击声音引用。经验
fill、组合背景和腰带 fill 三张私有纹理独立释放，不销毁借用的 DC6、字体、
物品/属性、控制器或 World。父页及两层嵌套 owner 均支持部分构造回滚，特别
处理已经返回但尚未挂载的按钮，以及经验纹理已生成后 TextureRegion 构造失败。
整个退出先禁止输入/渲染，再归还资源并退休控件树，额外清理不在树内的生命/
法力标签、技能详情和腰带热键标签，清空 owner、世界与控制器等借用引用。
重复退出无副作用，单项失败仍尝试其余清理，原始构造错误保留、清理错误附加；
失败清理动作不自动重试。

HotkeyButton 提供 isActive 扩展点，ControlPanel 的两个 observer 使用原会话门禁；
已销毁按钮不再刷新/映射/复制/绘制，并释放自身声音和控件/详情树，构造内部
失败也归还声音引用。BeltGrid 的键位回调和绘制改遵循 ItemGrid.isActive，
ControlPanel 将其限定原 owner；独立退出解绑四个 AssignmentListener，并退休
不属于 Scene2D 子节点的四个标签。这里不声称其他动态网格/快捷技能 owner 的
所有移除或重建路径已完成退休，那些边界继续下一批。

### 第二十七批验证

- `build/control-panel-compile.log` 与 `build/control-panel-targeted.log` 保留早期
  编译诊断：输入宿主类型不匹配，以及新测试的通配导入/StatListRef API/World
  非 AutoCloseable 夹具问题，均已修正。
- `build/control-panel-targeted-recheck.log`：当时 23 项专项中 22 项通过，一项
  因重新 stubbing 字体方法时触发已有 Answer，造成测试夹具递归；改用 doAnswer。
  随后增加腰带部分标签构造失败和移动端分支退出，共 25 项。
- `build/control-panel-resources-regression-gl.log` 与 `...-recheck.log`：新增
  25 项已通过，扩大定向的三个旧部分 mock 未提供新资源作用域/完整 ActorTree
  清理所需的组和 Actor 状态。更新旧夹具，保留原共享引用、异常继续、重复退出
  断言；不通过改变生产清理逻辑迎合不完整 mock。
- `build/control-panel-resources-regression-gl-final.log`：最终代码 34 类 366 项，
  364 通过、2 跳过（既有佣兵独立环境资源探针），失败/错误 0；core / desktop /
  D2GS 编译链、三轮真实 MPQ/GL 与最终 ClientShutdown 全部通过，BUILD
  SUCCESSFUL，3m5s。
- 新增 25 项使用真实生产 ControlPanel / 嵌套 owner、AssetManager 引用计数与
  原版数据表，仅拦截私有 Texture 上传。覆盖共享/排队引用、十五个按钮的声音、
  三张私有纹理、完整树/键位监听清理，早期资产、HotkeyButton 内部、组合背景
  所属子控件、迷你菜单后期未挂载按钮、腰带部分标签的构造失败，原错误附加
  清理错误与异常后继续。覆盖角色/游戏/资源/光标/音频/输入/世界替换和销毁后
  保存点击/声音按下/键位回调及两种 Batch 绘制入口，不触及替代宿主；保留当前
  菜单/技能/腰带参数、任务确认/升级声音、攻击 0/热键与移动端分支回归。
- `build/control-panel-resources-gl/cycle-{1,2,3}-lifecycle.txt` 每轮新增验证：
  ControlPanel 36 控件退休、54 保存点击失效、4 腰带键位监听解绑且保存回调
  失效、左右技能停止刷新、owner/世界/控制器清空、十九项资产回基线、607 张
  实际上传的 DC6 采样纹理删除；三张私有纹理仍纳入原 13 张私有纹理检查。
  保留头像 HUD、雇佣兵页、商店/背包/仓库/金币、Escape、任务/语音、技能、
  移动 UI、共享框、地图、加载交接与输入的所有前批检查。探针不消费药水或
  创建真实交互玩法，不代表联机或 Android 平台模拟。
- 三轮退出资产种类 56/56/57；对比 cycle-2/3-remaining-assets.txt，唯一新增为
  `data/global/sfx/ambient/footstep/MedDirt3.wav`，Sound refs=1，符合既有 Audio
  宿主懒缓存路径。最终 ClientShutdown assets=0；只证明本轮采样引用/纹理和
  退出检查通过，不代表全进程零滞留或长期压力通过。仍有
  48.23–79.70ms 慢帧超过 40ms 预算，人工观感/听感/输入、联机、Android
  真机及性能验收均未完成。

定向复跑（需要原版数据表，无真实 GL，拦截私有上传）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests com.riiablo.screen.panel.ControlPanelLifecycleTest `
  --tests com.riiablo.screen.panel.PanelResourceLifecycleTest --no-daemon
```

扩大定向与三轮真实资源复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :core:test `
  --tests 'com.riiablo.screen.panel.*' `
  --tests com.riiablo.GdxKeyMapperCaptureTest `
  --tests com.riiablo.widget.ButtonLifecycleTest `
  --tests com.riiablo.engine.client.SharedBoxLifecycleTest `
  --tests com.riiablo.screen.GameScreenSubscriptionsTest `
  --tests com.riiablo.screen.GameScreenAssemblyTest `
  --tests 'com.riiablo.codec.Animation*Test' `
  --tests com.riiablo.engine.client.AnimationStepperTest `
  --tests com.riiablo.engine.client.Act1QuestPresentationTest `
  --tests 'com.riiablo.audio.*Test' `
  --tests com.riiablo.item.VendorPricingTest `
  --tests com.riiablo.engine.server.quest.NativeHirelingOfferIntegrationTest `
  :desktop:offscreenSessionLifecycle '-PmobileUiLifecycle' `
  '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' `
  '-PsavesDir=F:/3rd_src/riiablo/build/control-panel-resources-saves' `
  '-PvisualOutput=F:/3rd_src/riiablo/build/control-panel-resources-gl' `
  '-Pd2Version=1.10f' --no-daemon
```

总体七批子系统装配整理完成，资源阶段推进至第二十七批但未结束。下一批优先
动态 ItemGrid / 快捷技能条目移除或重建时的退休边界；其他完整控件内部尚未
返回实例的构造失败、连接交接/全局网络路由、控制台/Timer/异步日志、跨 Act/
加载中退出及长压仍待。未扩大或重写全局网络接收路由，不能宣称完整网络隔离。
