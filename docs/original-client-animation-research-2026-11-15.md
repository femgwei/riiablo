# 原版 1.10f 客户端动画与 Riiablo 差分研究记录

最后更新：2026-11-15

## 当前目标

当前目标是重构并完善 Riiablo 的技能客户端动画行为，优先保证模式切换、动画资源、序列游标、关键帧事件、技能结束和图层生命周期正确。暂不把原版完整窗口与 Riiablo FBO 做像素级一致作为主验收条件。

像素截图和视觉哈希仍保留为诊断证据，但由于原版是完整 640×480 客户区，Riiablo 当前是 320×240 角色 FBO，两者在统一裁剪、相机、缩放、背景和调色板之前不能直接作为视觉通过门禁。

## 环境与边界

- 原版目录：`G:\\BaiduNetdiskDownload\\Diablo II 1.10F`
- 原版启动文件：`D2v110各版本免CD全功能启动.exe`
- 原版版本：1.10f，核心 DLL 使用固定 SHA-256 校验。
- 采集方式：常驻管理员 broker、隐藏/最小化窗口、定向 `PostMessageW` 输入、版本锁定的 x86 native helper。
- observer 默认只读，不向原版进程写内存、不注入 DLL、不调用目标函数。
- native collector 是单独的、明确 allow-list 的测试夹具，可以在原版更新线程调用经过 ABI 和指令签名核对的函数；每次必须确认断点恢复、detach 和目标仍存活。
- broker 当前已支持一次 UAC 后串行调度后续任务；最新状态为 `ready`，`cleanup_blocked=false`。

## 已建立的工具链

### 原版进程与客户端观测

- `tools/original-observer/resident_broker.py`：常驻管理员任务接收器。
- `tools/original-observer/orchestrator.py`：重启唯一 loader、自动进入单人游戏和 Blood Moor、启动进程会话。
- `tools/original-observer/menu-driver.py`：通过真实 D2Win 控件和窗口消息完成菜单及行走，不切前台、不移动系统鼠标。
- `tools/original-observer/live-observer.py`：只读读取 D2Client 玩家 Unit 候选字段、模式、动画帧、坐标和窗口采样。
- `tools/original-observer/animation_compare.py`：统一 trace、字段差分、阶段对齐和 SQ 诊断。
- `tools/original-observer/native-harness/d2game_runtime_trace.cpp`：版本锁定的被动断点观测器，可观测 update、target mode、damage 入口和调用现场。
- 所有 x86 native helper 已显式启用 `SeDebugPrivilege`，避免管理员 token 在 `DebugActiveProcess` 处返回 Win32 5。

### Riiablo 侧动画观测

- `core/src/main/java/com/riiablo/screen/OffscreenWhirlwindAnimationScreen.java`：隐藏 1×1 LWJGL 窗口、真实 COF/DCC 资源链、角色 FBO、300 帧 JSONL 和 PNG 关键帧。
- `core/src/main/java/com/riiablo/engine/server/AnimationTraceSystem.java`：从生产 `AnimData`、`CofReference`、位置和关键帧生成标准 trace。
- `server-domain/src/main/java/com/riiablo/engine/server/NativeWhirlwindSequence.java`：根据 D2MOO `SequenceTbls.cpp` 固化 Whirlwind 的原生序列帧表。
- `server-domain/src/main/java/com/riiablo/engine/server/animation/`：动画 trace 写入和相关数据结构。

## 原版底层差分已完成的结果

- D2Common seed 状态：448 组输入与 Java 一致。
- 原版 RNG：90,112 次输出及每次两字状态与 Java 一致。
- Fire Bolt：已完成一次完整原版施法、弹道、命中、伤害和 HP 结算样本，并修正 Riiablo 的独立弹道 RNG 与元素结算。
- Bash：已采集 1–40 级固定靶怪样本，包含模式、耗蓝、目标 HP、伤害和击退边界。
- Whirlwind 服务器夹具：右手单手斧、左手盾牌、1 级技能、Blood Moor 固定种子；已观测攻击窗口 `3,7,15,23,31,39,47,55`、位移开始/结束、耗蓝和目标 HP。
- 临时生成怪物可用于原版技能测试：使用原版 spawn、加入当前房间、设置 HP/抗性/回复、删除 AI 事件，完成后用原版 remove 清理。

## 旋风斩客户端动画当前证据

### 成功的后台客户端采样

成功任务：`20261115-045525-1123182a`

原版客户端 trace：

```text
tools/original-observer/broker/20261114-095236-2dc08884/runs/
20261115-045525-1123182a/orchestrator/process-session/
001-client-visible-whirlwind-animation-client-animation/animation-trace.json
```

该批次确认：

- 300 个客户端样本；
- 原版模式序列为 `NU → SQ → TN`；
- SQ 段有 18 个采样；
- D2Client 候选字段可读，包含 sequence frame、action frame、COF token、帧数和速度；
- native helper 成功 attach，`detached=true`、`cleanup_confirmed=true`；
- 由于后台右键最后被判定为 `approach`，native runtime trace 本次只捕获到 1 个 update，没有 target_mode/damage 入口。

### Riiablo 回放与差分

Riiablo 使用原版 trace 的 `mode_id` 和 `sample_elapsed_ms` 回放后，报告位于：

```text
tools/original-observer/runs/riiablo-whirlwind-client-visual-replay-20261115/
diff-after-mode-schedule.json
```

当前结果：

```text
status: failed              # 仍保留严格差分状态
mode_id/mode/token/wclass: 300/300 一致
animation_frame_count: 300/300 一致
animation_speed: 300/300 一致
animation_frame_raw: 291/300 不一致
keyframes: 5 帧不一致
visual_sha256: 300/300 不同
整体 phase alignment: 118/300 = 39.3%
SQ phase alignment: 6/18
```

这说明资源选择和模式切换已基本正确，主要剩余问题是 SQ 游标推进、回退/重播时机和采样时钟，而不是已证明的 COF 资源缺失。

### 已确认的 SQ 资源和时序事实

原生序列表来自 D2MOO：

```text
source frames = 0,1,2,3,3,4,5,6
attack events = sequence points 3 and 7
logical mode  = SQ (18)
presentation  = Barbarian GH composite / 1HS
```

原版客户端候选数据还显示稳定的 presentation 映射：

```text
sequence_frame_raw -> animation_frame_raw
512  -> 512
1024 -> 768
1280 -> 1024
1536 -> 1280
1792 -> 1536
```

Riiablo 已保留 `native_sequence_frame_raw`、`native_sequence_point` 和 `native_rewind_frames` 诊断字段；粗略 `RewindSkillEx(3)` 模型仍未作为默认行为启用，因为只按窗口采样时间回退会降低匹配率。下一版应将游标绑定 authoritative game frame 和语义攻击事件，而不是固定采样 tick。

## 生成怪物与客户端可见性

原版服务端生成的测试怪物并非天然不可见。Bash 和 Whirlwind 的服务器 collector 都使用原版 spawn 函数生成当前房间内的 skeleton1，并在继续运行若干游戏帧后进行测试。

此前旋风斩客户端动画批次没有把“生成靶怪”和“客户端输入”组合起来：

- 被动 runtime trace 不生成怪物；
- 客户端输入批次使用地图中的自然怪物或地面点；
- 直接调用 D2Game 旋风斩入口会绕过 D2Client 的技能选择消息。

因此该批次不能证明生成怪物不可见。合并测试应先 spawn、等待客户端 Unit 同步、投影目标坐标，再发送 F1 和右键，同时采集 SQ、target_mode、damage 和 HP。

## 当前明确的问题

1. Whirlwind SQ 的 authoritative cursor、攻击事件和 `RewindSkillEx` 时序还没有统一。
2. 原版 D2Client 的 `gameFrame` 与窗口采样时间还没有在同一条 trace 中原子对齐。
3. 最新成功 native runtime trace 只有 update，尚缺一次客户端真实攻击对应的 target_mode/damage 调用链。
4. 原版完整 backbuffer 与 Riiablo FBO 尚未统一裁剪；视觉哈希不作为当前功能性验收条件。
5. 旋风斩之外的复杂技能（跳跃、变身、召唤、陷阱、光环和其他 Sequence 技能）还需要按动画族建立相同的事件契约。

## 旋风斩下一轮实现计划

### 第 1 轮：实现

- 用 `WHIRLWIND_START`、`WHIRLWIND_ATTACK`、`WHIRLWIND_REWIND`、`WHIRLWIND_END` 语义事件替代固定 tick 脉冲；
- 让 `NativeWhirlwindSequence` 以 authoritative game frame 推进；
- 在 SQ 期间固定 GH/1HS presentation，结束后恢复 NU；
- 保证主手、盾牌、身体和阴影图层的 COF 生命周期不泄漏。

### 第 2 轮：验证与修复

- 测试起始前导、攻击事件、连续施放、中断、耗蓝只扣一次、位移和结束帧；
- 输出不依赖像素的标准动画 trace；
- 对照原版模式、token、帧数、速度、关键帧和事件顺序。

### 第 3 轮：原版端到端复核

- 生成客户端可见的固定靶怪；
- 后台发送真实 F1/右键；
- 同时记录 D2Client SQ、服务端 target_mode/damage、HP 和结束状态；
- 将成功证据加入长期基线。

## 验收边界

旋风斩功能性动画通过条件：

- 正确进入 SQ；
- 使用正确的 GH/1HS presentation；
- 序列游标连续推进，不冻结、不跳回错误帧；
- 攻击事件在正确的序列点触发；
- 位移期间动画持续；
- 结束后回到 NU；
- 重复施放和中断不会残留 SQ 状态；
- 资源图层没有异常释放或重复创建。

截图和视觉哈希只作为后续诊断项，不作为当前主门禁。

## 主要证据索引

- 动画流程说明：`tools/original-observer/ANIMATION_COMPARISON.md`
- 原版 observer 与 broker：`tools/original-observer/README.md`
- native harness 说明：`tools/original-observer/native-harness/README.md`
- 最新原版调度报告：`tools/original-observer/broker/20261114-095236-2dc08884/runs/20261115-045525-1123182a/orchestrator/orchestrator.json`
- 最新跨实现差分：`tools/original-observer/runs/riiablo-whirlwind-client-visual-replay-20261115/diff-after-mode-schedule.json`
- SQ 实现：`server-domain/src/main/java/com/riiablo/engine/server/NativeWhirlwindSequence.java`
- 旋风斩客户端离屏采集：`core/src/main/java/com/riiablo/screen/OffscreenWhirlwindAnimationScreen.java`
