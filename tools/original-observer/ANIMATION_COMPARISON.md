# 职业技能动画差分流程

> 当前研究总记录：[`docs/original-client-animation-research-2026-11-15.md`](../../docs/original-client-animation-research-2026-11-15.md)。
> 当前目标是功能性客户端动画重构；像素级视觉哈希保留为诊断证据，不是主验收门禁。

## 2026-11-15 当前状态

最新成功后台批次为 `20261115-045525-1123182a`。原版 D2Client 已采集 300 帧，确认 `NU → SQ → TN`，并成功完成 native helper attach/detach。Riiablo 读取原版 `mode_id` 时间轴后，`mode_id`、模式、token、武器类别、COF 帧数和速度均达到 300/300 一致；剩余差异集中在 SQ 游标推进、攻击事件时序和未统一的视觉缓冲区。当前跨实现报告仍保持 `failed`，其中 `animation_frame_raw` 为 291/300 不一致，`visual_sha256` 为 300/300 不同。

最新成功原版 trace：

```text
tools/original-observer/broker/20261114-095236-2dc08884/runs/
20261115-045525-1123182a/orchestrator/process-session/
001-client-visible-whirlwind-animation-client-animation/animation-trace.json
```

跨实现差分：

```text
tools/original-observer/runs/riiablo-whirlwind-client-visual-replay-20261115/
diff-after-mode-schedule.json
```

这套流程把动画一致性拆为三个独立门禁：

1. **服务器时序**：技能开始、mode、移动、攻击/导弹/声音事件和结束帧。
2. **客户端动画**：COF token、武器类、动画帧、总帧数、速度和关键帧数组。
3. **画面**：同一游戏 tick 的渲染帧摘要或 PNG 哈希。

缺少第 2 或第 3 层数据时，比较器返回 `incomplete`，不会返回通过。

## 统一 trace 格式

JSONL 第一行是 header，后续每行一个 frame：

```json
{"type":"header","schema":1,"metadata":{"skill":151,"level":1,"capabilities":["mode","animation_frame","keyframes"]}}
{"type":"frame","tick":0,"game_frame":100,"mode_id":18,"mode":"SQ","token":"BA","wclass":"1HS","animation_frame_raw":0,"animation_frame_count":16,"animation_speed":256,"keyframes":[],"x_fp16":100,"y_fp16":200,"combat_events":[],"visual_sha256":null}
```

Riiablo 侧使用 `com.riiablo.engine.server.animation.AnimationTraceWriter` 输出。它只负责稳定格式，不改变技能逻辑；应由测试 world 或专用 trace system 在每个 authoritative tick 调用一次。核心模块已提供可选的 `AnimationTraceSystem`：

```java
AnimationTraceWriter writer = new AnimationTraceWriter(path, metadata);
AnimationTraceSystem trace = new AnimationTraceSystem(writer)
    .focus(barbarianEntity).gameFrame(authoritativeFrame);
world.setSystem(trace); // 或在 world builder 中加入 trace
```

它会从 `AnimData.frame/numFrames/speed/keyframes`、`CofReference` 和 `Position` 生成标准 frame；关闭 world 前调用 `trace.close()`。截图摘要仍由渲染侧另外写入 `visual_sha256`。

## 原版当前可转换的证据

现有 `d2game_*` collector 可提供 mode、游戏帧、FP16 坐标和战斗事件，但它运行在 D2Game 更新线程，不能看到 D2Client 的 COF 光标。转换命令：

```powershell
python tools/original-observer/animation_compare.py from-native `
  tools/original-observer/broker/20261114-095236-2dc08884/runs/<run>/orchestrator/process-session/001-whirlwind-single-axe-level1 `
  --output tools/original-observer/runs/whirlwind-animation-original.json
```

这个文件用于服务器时序对齐，明确标记 `animation_frame`、`keyframes` 和 `visual` 缺失。

已有的只读 `live-observer.py` 还可以读取经过版本签名校验的 D2Client 玩家单元字段 `mode`、`animation_frame_raw` 和坐标。转换客户端采样（文件名是 `samples.jsonl`）：

```powershell
python tools/original-observer/animation_compare.py from-client `
  tools/original-observer/runs/<client-observer>/samples.jsonl `
  --output tools/original-observer/runs/original-client-animation.json
```

这一步能建立原版客户端动画帧基线，但采样是非原子、非游戏 tick 锁定的；它仍缺少原版 COF 关键帧数组，因此只能作为帧游标证据，不能作为完整动画通过。

如果需要同时记录客户端窗口的渲染摘要，可在只读 observer 上增加：

```powershell
python tools/original-observer/live-observer.py `
  --pid <pid> --home 'G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  --output tools/original-observer/runs/<client-observer> `
  --animation-candidates --visual
```

`--visual` 不激活窗口，也不读取桌面；它通过目标窗口的 `PrintWindow` 输出
32 位客户区位图并记录 SHA-256。捕获失败会保留结构化错误，不会把空图或桌面
内容提升为 `visual` 能力。只有每一个 player sample 都成功捕获时，
`animation_compare.py from-client` 才会在 metadata 中声明 `visual`。

本次后台验证任务 `20261114-212650-1ac5621e` 已成功采集 300/300 个窗口哈希，
并将原版客户端 trace 提升为完整动画字段：

```text
tools/original-observer/broker/20261114-095236-2dc08884/runs/
20261114-212650-1ac5621e/orchestrator/process-session/
001-client-visible-whirlwind-animation-client-animation/animation-trace-promoted.json
```

该 trace 的 `animation_ready=true`、`client_fields_ready=true`、
`visual_ready=true`；对自身重放比较为 `passed`。它证明后台窗口采集链路可用，
但仍需用同一场景生成 Riiablo 客户端渲染 trace，才能进行跨实现的视觉差分。

### 只读 UnitAny/COF 候选扫描

动画任务会同时传入 `--animation-candidates`。在 D2Client、D2Common、D2Game
哈希通过后，observer 只读取玩家 `UnitAny` 的 0xec 字节和四个已知指针，保留
候选字段及 64 字节预览，不写内存、不调用目标进程函数：

| 偏移 | 1.10f 结构候选 | 目前证据 |
| --- | --- | --- |
| `0x30` | sequence mode 指针 | 可读，序列数据首字节随技能变化 |
| `0x34/0x38` | sequence 总帧/当前帧（256 定点） | `SQ` 阶段出现 `256..1792` |
| `0x3c/0x4c` | 动画速度（32/16 位） | `SQ` 阶段稳定为 `256` |
| `0x40` | sequence mode | `SQ` 阶段为 `7` |
| `0x44/0x48` | 当前帧/帧计数（256 定点） | `SQ` 阶段观测到 `0..1536` 与 `256..1792` |
| `0x4E` | `nActionFrame` / 序列事件码 | `SQ` 阶段出现 `1`，可映射为 `ATK` |
| `0x50` | 当前 COF/AnimData 指针 | 预览以 ASCII token 开头，例如 `BANU1HS`、`BAGH1HS`、`BATN1HS` |
| `0x54` | GfxData 指针 | 可读，稳定指针 |

最新成功任务为 `20261114-165858-40b9b0d5`，原始候选摘要在：

```text
tools/original-observer/broker/20261114-095236-2dc08884/runs/
20261114-165858-40b9b0d5/orchestrator/process-session/
001-client-visible-whirlwind-animation-client-animation/summary.json
```

这次实际采样得到 `BAGH1HS: frames=5, speed=256`、
`BANU1HS: frames=8, speed=96`、`BATN1HS: frames=16, speed=80`，并且每个
AnimData 记录的 144 个帧标记均已完整读出（本批次均为零）。这些字段已进入
canonical trace 的 `animation_candidates`；仍保留 candidate 标记，因为采样
尚未做到原子游戏 tick 对齐，也没有把原始帧标记和渲染关键帧建立一一映射。
比较器的完整动画门禁保持 `incomplete`。

如果要显式尝试把普通模式的 `pFrameFlags` 转成标准关键帧，可使用：

```powershell
python tools/original-observer/animation_compare.py promote-client `
  tools/original-observer/broker/<session>/runs/<run>/orchestrator/process-session/<sample>/samples.jsonl `
  --promote-keyframes --output tools/original-observer/runs/original-client-animation-keyframes.json
```

映射遵循 1.10f 的事件码：`1/2/3/4 = ATK/MIS/SND/SKL`。普通模式读取
`pFrameFlags`，`SQ` 模式读取原版已经计算好的 `nActionFrame` 字段；如果
任一来源缺失，提升会跳过并保留 `incomplete`。

当前旋风斩样本已经成功得到 300 帧中的 8 个 `ATK` 事件，结构化动画自比较
结果为 `passed`；仍然缺少同一 authoritative tick 的画面缓冲区摘要，因此
视觉门禁仍为 `incomplete`。

生成结果：

```text
tools/original-observer/runs/original-client-animation-promoted-keyframes.json
tools/original-observer/runs/original-client-animation-self-diff-keyframes.json
```

## 比较命令

### Riiablo 后台 trace 导出

现在也可以直接启动生产客户端的隐藏离屏旋风斩采集器。它不启动地图、不显示
窗口、不需要 UAC，使用真实 `CofResolver/CofLoader/CofLayerLoader` 和 FBO，
一次导出 `NU -> SQ -> TN` 的 300 帧客户端 trace，并保留若干 PNG 关键帧：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :desktop:offscreenWhirlwindAnimation --no-daemon `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-PvisualOutput=F:\3rd_src\riiablo\tools\original-observer\runs\riiablo-whirlwind-client-visual'
```

输出目录中的 `riiablo-whirlwind-client-visual.jsonl` 可直接交给比较器；
`cof.txt` 已确认本地资源记录为 `BANU1HS=8/96`、`BAGH1HS=5/256`、
`BATN1HS=16/80`。当前 FBO 是 320x240 的角色合成缓冲区，原版 `PrintWindow`
是完整客户区，所以两边的 `visual_sha256` 不能直接判定相等；它们用于确认
帧被真实渲染，并作为后续统一裁剪区域/相机后的视觉门禁输入。

本轮与原版 300 帧样本的静态客户端字段已经逐帧一致：`mode_id`、`mode`、
`token`、`wclass`、`animation_frame_count`、`animation_speed` 和 `keyframes`
均为 300/300 相同。剩余差异集中在动画游标相位/推进速率和画面缓冲区：原版
采样是窗口异步约 60--100ms，离屏采集当前按固定段落驱动；因此比较报告会保留
`failed`，不能把它误读为 COF 资源或模式不一致。

比较器现在还支持按原版 trace 的 `sample_elapsed_ms` 回放采样间隔。该模式只
改变离屏采集器的推进时钟，不改变 COF、技能或渲染逻辑：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :desktop:offscreenWhirlwindAnimation --no-daemon `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  '-Pschedule=F:\3rd_src\riiablo\tools\original-observer\runs\original-client-whirlwind-visual.json' `
  '-PvisualOutput=F:\3rd_src\riiablo\tools\original-observer\runs\riiablo-whirlwind-client-visual-replay'
```

本次回放后，相位诊断从 52/300（17.3%）提高到 111/300（37.0%）；其中
`NU` 为 11/15、`TN` 为 95/267。`SQ` 仍只有 5/18，说明其差异主要来自
原版的 native sequence 游标映射，而不是固定采样间隔。严格门禁仍保留
`failed`，因为 `animation_frame_raw` 仍有 280/300 差异，且两边视觉摘要的
分辨率和裁剪范围不同，不能用哈希直接宣称画面一致。

Whirlwind 的 SQ 源帧表已按 D2MOO `D2Common/DataTbls/SequenceTbls.cpp` 固化为
`NativeWhirlwindSequence`：8 个序列点的源帧为 `0,1,2,3,3,4,5,6`，攻击事件
位于点 3 和点 7。离屏采集器现在使用这张表生成 `animation_frame_raw`，不再
把 SQ 直接按 GH 的 5 帧 COF 取模。原版样本仍显示额外的回退/重播行为，后续
需要把 `SrvDo076` 的 `RewindSkillEx` 影响接入同一序列时钟。

当前采集器还输出 `native_sequence_frame_raw`、`native_sequence_point` 和
`native_rewind_frames` 诊断字段。`RewindSkillEx(3)` 模型可以用
`--whirlwind-rewind` 开启，但它需要与原版 `gameFrame` 对齐后才适合作为严格
门禁；默认关闭时使用已经校准过的原生序列推进。按本轮 300 帧原版窗口样本，
默认推进的 SQ 精确匹配为 9/18（50%），整体相位匹配为 121/300（40.3%）。
这证明序列表和采样时钟已经接通，但还不能把窗口采样的 `animation_frame_raw`
当成技能动画完全一致的结论。

比较报告现在附带 `field_mismatch_counts`、`field_compared_counts` 和
`exact_match_fields`，可以把资源/模式问题与采样相位问题分开：本批次报告中
只有 `animation_frame_raw`（298/300）和 `visual_sha256`（300/300）不一致，
其余客户端字段全部精确匹配。

可以在不启动窗口的情况下运行一个固定的旋风斩 headless 场景并导出
Riiablo 的 canonical JSONL：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:RIIABLO_TRACE_OUTPUT = 'F:\3rd_src\riiablo\tools\original-observer\runs\riiablo-whirlwind-trace.jsonl'
.\gradlew.bat :core:test --tests com.riiablo.engine.server.WhirlwindAnimationTraceExportTest --no-daemon
```

随后可直接与原版服务器 trace 运行比较器：

```powershell
python tools/original-observer/animation_compare.py compare `
  tools/original-observer/runs/whirlwind-animation-original.json `
  tools/original-observer/runs/riiablo-whirlwind-trace.jsonl `
  --output tools/original-observer/runs/riiablo-whirlwind-vs-original.json
```

该导出器用于验证后台采集链路和字段格式；它不伪造原版客户端画面，
当前仍会保留 SQ 序列和地图坐标对齐差异，比较结果为 `failed` 或
`incomplete` 时不能解释为视觉不一致的最终结论。

```powershell
python tools/original-observer/animation_compare.py compare `
  tools/original-observer/runs/original-animation.json `
  tools/original-observer/runs/riiablo-animation.json `
  --output tools/original-observer/runs/animation-diff.json
```

退出状态：

- `0`：请求的动画字段都存在且一致；
- `1`：发现差异；
- `2`：证据不完整，例如仍只有 D2Game mode/位置，没有 D2Client 动画帧。

### 2026-11-14 旋风斩服务器对齐结果

当前固定样本已经完成 61 个 authoritative tick 的逐帧对齐：

```text
tools/original-observer/runs/riiablo-whirlwind-vs-original.json
status: incomplete
mismatch_count: 0
```

这次修正包含 SQ 启动前三个游戏帧的无位移前导、相对帧
`3,7,15,23,31,39,47,55` 的攻击窗口、每 tick 位移以及结束时的
`SQ -> NU` 回站时序。报告仍标记为 `incomplete`，因为原版 D2Game
collector 没有提供 D2Client 动画游标、COF 关键帧、战斗事件和渲染缓冲区摘要；
因此它证明的是服务器模式/位置时序一致，不能单独证明视觉一致。

比较器默认允许 `1/256` subtile 的位置误差；mode、动画帧、关键帧和视觉摘要必须精确一致。视觉摘要相同只表示输入帧摘要相同，最终画面门禁仍应保留原始 PNG 和采集方式。

## 旋风斩第一批目标

先固定右手手斧、左手盾牌、1 级旋风斩：

- 原版已知服务器窗口：相对帧 `3,7,15,23,31,39,47,55`；
- 原版位移起始：第 `3` 帧；结束位移：第 `56` 帧；回站：第 `60` 帧；
- `0x50` 的 COF token、帧数、速度和关键帧事件已经可转换为标准 trace；
- 下一步需把原版 trace 与 Riiablo 在相同技能场景下的 authoritative tick 对齐；
- 再让 Riiablo 用 `AnimData.frame/numFrames/speed/keyframes` 输出同一 tick trace；
- 最后接入 D2GFX 后缓冲截图，才可以宣称画面动画一致。

## 后台联合采样任务

`jobs/barbarian-whirlwind-animation.json` 会在一次后台任务中重启原版、进入
CXBarb 的 Blood Moor，并在 `d2game_whirlwind` 触发窗口前启动只读
`live-observer.py`。原生 collector 的服务器帧和客户端 `samples.jsonl` 会
分别保留；客户端样本会自动转换为同目录的 `animation-trace.json`。

这条任务当前用于验证采集链路。因为 native collector 直接调用 D2Game 的
技能入口，未经过 D2Client 的输入/技能选择消息，实测客户端样本可能全部保持
`mode=NU`。任务会写出 `client_animation_skill_mode_observed=false`，这种
结果只能判定为 `incomplete`。要取得真正的 `SQ`/`BA` 客户端动画，下一步
需要给 CXBarb 提供客户端可见的旋风斩技能记录，再通过后台窗口消息发送一次
技能目标输入；不能把 D2Game 的直接调用当成客户端动画触发。

现在 `jobs/barbarian-whirlwind-client-animation-input.json` 已完成这一步：
`CXBarbWW` 的 F1 左右技能字段均设为 151，后台向游戏窗口发送 F1 和右键
地面输入。成功 trace 已观察到 `NU → SQ → WALK`，其中 `SQ` 持续 33 个
40ms 采样，帧游标范围为 `0..1536`。这证明客户端技能模式可以在无前台操作
下采集。COF token、动画总帧数、关键帧和渲染摘要仍缺失，故比较状态继续为
`incomplete`。

## 门禁原则

- 不把伤害事件当作客户端 `ATK` 关键帧；两者分别记录。
- 不把整数坐标相同当作没有移动；优先比较 FP16 坐标。
- 不用单个截图或感知相似度替代完整帧序列。
- 原版 collector 未读取 D2Client 动画帧前，所有报告都必须保留 `incomplete` 状态。

## 2026-11-19 旋风斩可见调用链验证

在 `CXBarbWW`、Blood Moor、800x600 窗口可见但不抢前台的条件下，原版
D2Client 断点采样得到两次真实调用：

- `overlay_create`（RVA `0xBBA70`，约 1187 ms）；
- `skill_callback_dispatch`（RVA `0x1C6B0`，约 1312 ms）。

同批只读客户端采样得到 300 帧，观察到技能模式 `SQ`（模式号 18），因此
技能输入和客户端模式切换已经证实。候选的 `unit_animation_update`、
`unit_render_dispatch`、`render_frame_dispatch`、`overlay_draw` 在 15 秒窗口内
没有命中。这个结果说明当前 D2Client 断点集合还不足以覆盖实际的 DirectDraw
提交路径，不能直接解释为原版没有绘制。

Riiablo 同一旋风斩语义样本有 300 帧、2424 个事件：`SEQUENCE_CURSOR` 300 次、
`LAYER_DRAW` 2100 次、`LAYER_LOAD` 14 次、`COF_RESOLVE` 2 次、
`ANIMATION_MODE_CHANGE` 5 次、`KEYFRAME_EVENT` 3 次。每帧的层顺序和组件号
已保留，可用于定位少画或顺序错误。

机器可读报告：
`runs/semantic-whirlwind-20261119b/client-callchain-diff.json`。
报告状态为 `partial`：已确认技能回调和 Overlay 创建，但仍需补充 D2Gfx
帧提交入口后，才能把原版未命中的四个候选与 Riiablo 的图层提交做严格对应。
## 2026-11-19 语义调用链差分

Riiablo 的离屏 JSONL 现在额外写出有序事件：`ANIMATION_MODE_CHANGE`、
`COF_RESOLVE`、`LAYER_LOAD`、`SEQUENCE_CURSOR`、`KEYFRAME_EVENT` 和
`LAYER_DRAW`。`LAYER_DRAW.component` 是实际提交到 PaletteIndexedBatch 的
COF 组件，事件顺序就是本帧的绘制顺序，可以直接定位少画的层或错误顺序，
不依赖像素哈希。

原版侧新增 `native-harness/d2client_animation_trace.exe`。它对固定 SHA-256
的 1.10f D2Client.dll 在静态核实的技能分派 `0x6FABC6B0`、通用单位动画更新
`0x6FB3D880`、Overlay 创建 `0x6FB5BA70` 和绘制 `0x6FB57E2A` 设置一次性
断点，只读取 EIP、返回地址和栈，命中后恢复断点并继续。它不调用目标函数，
不写目标内存；入口未命中时保持 `incomplete`，不会把静态地址当作运行时证据。

```powershell
$pid = (Get-CimInstance Win32_Process |
  ? { $_.Name -eq 'Game.exe' } | Select-Object -First 1 -Expand ProcessId)
.\native-harness\build\d2client_animation_trace.exe $pid `
  'G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  'F:\3rd_src\riiablo\tools\original-observer\runs\original-client-callchain' 5000
```
