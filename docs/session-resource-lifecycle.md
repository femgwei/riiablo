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
