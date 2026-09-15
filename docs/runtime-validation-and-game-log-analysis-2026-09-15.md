# 自动验证方案与 game.log 错误分析（2026-09-15）

日志来源：`C:/Users/DELL/Desktop/game.log`（仅作为运行数据分析，未复制进仓库）。

## 后续自动验证方案

采用四层门槛，后续功能不得只依赖人工画面判断：

1. 纯 Java/数据测试：公式、状态机、分类、投影、边界、幂等和固定 RNG。
2. Headless ECS 集成：实体组件生命周期、导弹碰撞/伤害/删除、RoomEx、任务对象。
3. 真实 D2GS 协议客户端：双端快照、奖励、Warp、断线重连、删除水位。
4. 真实 1.10f 离屏渲染：DC6/DCC 资源、Automap、UI、目标区域像素和包围盒。

当前优先落地项：

- Throw/标枪：固定不移动目标和固定 RNG，断言服务端生命前后值、每枚导弹 ID、
  create/hit/delete tick 及客户端生命快照。
- Automap：固定生成尸体、物品、导弹、Champion/Unique/Minion/Boss、任务对象和入口，
  断言 marker 类型/数量/颜色/投影；三模式只比较目标 ROI 和包围盒，不比较整张截图。
- A5Q6/任务链：等待明确服务端状态并设置固定超时，禁止固定 sleep；双客户端断言
  对象、奖励 revision、重建和重连状态。
- CI 分层：普通提交运行 core/ECS；合并前运行 D2GS 聚合门槛；带合法 MPQ 的自托管
  机器运行真实资源和像素门槛。

## game.log 结构

- 文件约 3.83 MB、33058 行，包含至少三次追加运行，不是单次启动日志。
- 前两次运行均在 Splash 阶段失败，第三次进入 Blood Moor 后因 DCC 资源释放异常退出。
- 三次异常后 Gradle 都打印 `BUILD SUCCESSFUL`，因此该日志中的 Gradle 状态不能作为
  客户端成功证据。

## P0：最后一次退出的直接原因

```text
Asset not loaded: data/global/MONSTERS/SI/TR/SITRLITNUhth.dcc
  at AssetManager.unload
  at CofLayerLoader.unload(CofLayerLoader.java:144)
  at CofLayerLoader.loadDcs(CofLayerLoader.java:121)
```

判断：这是 `CofLayerLoader` 与 `AssetManager` 生命周期状态不一致，不是 MPQ 中缺少该
DCC。`loadDcs` 只有在 MPQ 查询命中 DCC/DC6 后才会保存 descriptor；崩溃发生于后续
`unload`，说明实体仍保存旧 descriptor，但 AssetManager 已经完成过释放、取消加载或
引用计数归零。日志中异常前刚生成/更新两个 `quillrat1`，路径中的 `SI/TR` 对应该怪物
的躯干层，符合怪物 COF 组件刷新触发释放的调用链。

后续修复/验证方向：

- 将 `CofLayerLoader.unload` 改为幂等 release：descriptor 为空或资产不在 manager 中时
  只清槽，不调用会抛异常的 `AssetManager.unload`。
- 核对异步 queued/loading/loaded 三态以及同一路径多实体共享引用计数，不能只覆盖
  `isLoaded=true` 的正常路径。
- 增加两个相同怪物快速进入/离开 RoomEx、NU/WL 模式切换和实体删除的 headless/离屏
  回归；断言无双重 unload、无负引用计数且最终引用归零。

处理状态（本轮）：`CofLayerLoader.unload` 已改为幂等释放；调用前检查
`AssetManager.contains`，并捕获异步完成/失败与释放之间的竞态。新增
`CofLayerLoaderReleaseTest`（含 queued asset 和重复释放场景）验证 stale descriptor 连续释放不会抛异常。仍需在真实客户端
重复怪物/RoomEx 切换场景中确认资源引用最终归零。

## P0：自动验证假通过

`desktop:run` 在 LWJGL Application 线程抛未捕获异常后仍输出 `BUILD SUCCESSFUL`。
当前 `desktop/build.gradle` 的普通 `run` 设置了 `ignoreExitValue=true`，且后台应用线程
异常没有可靠转换为进程非零退出码。后续自动门槛不得使用 `desktop:run` 的 Gradle 成功
作为结果；应使用已有 `offscreen*` 任务，并要求：

- 全局未捕获异常处理器记录 fatal 后以非零退出；
- 必须出现明确 `result=PASS` manifest；
- 缺少 PASS、超时或出现 `Exception in thread` 均判失败。

## P1：前两次 Splash 音乐失败

```text
Couldn't load dependencies of asset: data/global/music/Act4/diablo.wav
Caused by: IllegalArgumentException: file cannot be null
```

1.10f 资源集合中该音乐无法由声音 resolver 提供有效 FileHandle，OpenAL 因 null file
终止启动。当前仓库 `MusicController.next()` 已包含“可选音乐失败后跳过”的保护提交，
而第三段运行已越过 Splash，因此这两段更像追加日志中的旧运行。仍需用当前 HEAD 做一次
缺少该 WAV 的启动专项，确认只记录 skip、不会退出且 AssetManager 不残留半加载任务。

## P2/P3：非直接退出项

- `WindowsPreferences/WinRegistry`：Java 模块禁止反射私有注册表方法，同时注册表根键
  创建返回权限错误。显式传入 `--d2/--saves` 后不阻断启动；后续可在参数已提供时跳过
  InstallationFinder，避免无意义警告。
- `Memory pool not provided`：共约 564 条，当前走 Java 对象/数组分配回退，不是本次
  崩溃原因，但严重污染日志，建议限频或降为 DEBUG。
- `Warp tile not found`：仅少量 DRLG 警告，需要由现有 Warp graph/collision 门槛核对，
  不能仅凭本日志认定为致命地图错误。
- `Trees.ds1 zero-padding`：单次兼容填零提示，应在地图连续性测试中观察，不是退出点。

## 下一步顺序

1. 在真实客户端重复怪物/RoomEx 切换场景复测 COF 资源引用归零。
2. 修正普通桌面运行的 fatal 退出传播或增加日志 PASS 门槛，消除假成功。
3. 用当前 HEAD 重跑缺失音乐启动，确认已有保护真实生效。
4. 再推进 Throw、Automap marker、A5Q6 的自动验证夹具。
