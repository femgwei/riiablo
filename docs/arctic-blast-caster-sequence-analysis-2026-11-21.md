# 极地风暴：真实施法路径、S1/SQ 与持键收尾

本轮修复的是此前没有进入原版 S1/SQ 动作路径的问题，并打通本地真实输入、持续施放、转向、松手和取消。动作源帧阶段顺序已与后台原版样本对上，不能扩大为逻辑光标、全部冰流数量、原版服务端伤害或 GPU 一致。

## 原版补采与角色配置

最终证据 run 为 `20261121-185938-70b0693c`，角色仍为独立裸装 `CXArctic`，仅 Arctic Blast(230) 一点。此次读取了输入存档的角色等级：**角色是 99 级，技能是 1 级**。此前记录中“一级冰流/level1”表示技能等级，不表示原版角色等级；单冰流诊断的角色属性等级 1 不影响它已验证的移动与帧率，但不能直接拿来通过完整玩家施法校验。本次真实施法回放使用与原版相同的角色 99 级、技能 1 级，没有放宽等级校验。

两次按住约 640 ms / 1600 ms 的原版运行使用只读轮询和私有 HWND 输入，额外记录 pAnimSeq、逻辑序列光标/速度/剩余时间、序列展示模式、源帧光标和 INFERNO 状态位。原版无需前台、无调试器附加、无暂停或写内存。原始报告验证私有桌面一直不是输入桌面，屏幕尺寸未变，所属游戏已关闭；常驻 broker 可继续使用，无新增 UAC。

前一补采 run `20261121-185707-8f119e92` 已确认序列表和光标；最终 run 再加入 INFERNO 状态位。两次均成功且关闭所属进程。本次新增轮询不计为 DCC 绘制或堆栈证据。

`arctic-blast-sequence-1.10f.json` 保留最终两组原始文件哈希、采集器快照哈希与 78 条去重施法阶段变化；也保留这次捕获的 26 条冰流、416 个去重状态，不覆盖上一轮的 25 条/400 状态 fixture。

## 原版动作与实际缺失

实际进程中的 pAnimSeq 指向 D2Common RVA `0x9FE10`。它的 15 条六字节记录与固定哈希 DLL 内的数据完全相同，表 SHA-256 为 `1d24c8e224165a173bca4fff23d19fd6be625c8b7e56e494437bebe38a806e65`。

序列对应的资源动作是 S1(13)，源帧依次为 `0,1,2,3,4,5,6,7,8,9,9,10,11,12,13`，仅序列点 10 有 MELEE_ATTACK 事件。原版逻辑单位模式 SQ(18) 与所选 S1 资源模式不是同一个字段。点 10 触发冰流时仍显示 S1 源帧 9，不应把逻辑点直接当作资源帧。

固定版本 D2Client 的 CltDo24 在 INFERNO 条件下调用序列定位函数到点 8，并将剩余时钟设置为 `0x500`。已观察到持键时逻辑点 8/9 循环、源帧持续保持 9；松手后仍有结束阶段。两组源帧阶段去除重复后都为 `0..11`，随后转入 NU。该观察来自内存状态和 DLL 表，不是实际身体 DCC 选帧调用。

Riiablo 原先的 PLR 资源模式表不包含 SQ，Arctic Blast 的 anim 和 seqtrans 又都是 SQ，因而实际落入 Actioneer 的 SC 回退。普通 COF 标记无法替代这套序列；按住期间还被“已有 Casting/Sequence”的输入门挡住。因此单冰流移动正确，并不意味着角色动作和持续喷射已正确。

## 实现

- `NativeArcticBlastSequence` 保存经实际 DLL/进程验证的 15 点源帧映射。仅匹配原版 skill230、seqnum18、SrvSt11/SrvDo19 的玩家施法路径启用，其他技能不套用这套动作。
- `Actioneer` 为该技能选择 S1，建立独立逻辑光标和剩余时钟；同一次施法的输入续传只更新目标和朝向，不重新播放起手或创建新的 actionId。
- `SequenceHandler` 在启动时设置 S1；`AnimStepper` 首次保留源帧零，随后按 25 Hz 推进，在动作点运行真实动画关键帧事件，由现有服务端技能系统和客户端展示系统分别处理冰流。诊断程序没有直接注入 SkillDoEvent。
- 本地连续施放沿现有 INFERNO 初始化/刷新规则工作。新 SQ 分支将刷新与导弹回调分开，避免刷新和同一动画动作点重复生成导弹，也避免旧的 AnimData 回退与逻辑光标双重回退。
- `CursorMovementSystem` 在普通忙碌门之前传递持续输入与松手。短租期用于输入/UI 中断后的有界退出；同技能的积压右键按下不再于松手后变成一次新施法。新的服务器重复请求入口也保留同一 actionId，但网络完整输入/释放链路尚未验收。
- 已有 CofReference.sequenceFrame 向客户端发布源帧，因此本地和网络快照展示都可用实际 S1 帧，不依赖普通动画自行循环。取消施法清除停帧、Sequence/Casting 和对应 INFERNO 状态；已创建的冰流按自己的寿命退出。

Sequence 新增状态均为可移植的原始字段，已有 ActionRuntimeCheckpoint 反射字段镜像覆盖它们；既有 checkpoint 回归已通过。

## 验证与边界

最终日志 `tools/original-observer/arctic-blast-sequence-final-validation.log`：**40 项通过，0 失败、0 错误、0 跳过**。XML 保留于 `arctic-blast-sequence-test-results/`。包括真实施法序列 6 项、真实 Cursor 输入 1 项、动作 checkpoint 4 项、Leap 展示 5 项，以及上一轮冰流/Inferno/音频/权威展示等 24 项。

真实施法测试覆盖起手不跳过零帧、持续喷射、两 tick 回调间隔、转向不重启、松手后 NU/清理、断输入、耗尽法力和显式取消。实际 Cursor 测试验证普通 Casting/Sequence 忙碌时仍传入按住与释放，以及积压点击不会在结束后重新开喷。身体展示验证使用真实 S1 COF 和 AnimationStepper 源帧，导弹使用实际 A/B DCC 头和生产生命周期，不进行 GPU 绘制。

`ArcticBlastSequenceOriginalAnalysis` 经真实 Actioneer → SequenceHandler/AnimStepper → ServerSkillSystem/InfernoPresentationSystem 回放两组 16/40 tick 持键，导出 `arctic-blast-sequence-production-1.10f.json`。`compare_arctic_sequence.py` 比较结果：

- 两组身体源帧阶段均为 `0..11`，与原版样本顺序相同。
- 本地两个相邻冰流技能回调相隔 2 tick，松手后均在 8 tick 内回到 NU；观察结束时客户端和伤害载体均为零。
- 原版两组观察到 7 / 19 条客户端冰流，本地为 6 / 18 次客户端回调，**仍各少一次，不登记为数量匹配**。原版轮询输入时间未与本地固定帧对齐，当前还不能区分全部差异来自输入相位还是结束回调处理。
- 原版松手到 NU 分别约 359 / 312 ms；本地自 16/40 tick 输入边界起均为 8 tick。保留这些原始差值，不将其伪装为绝对 tick 对齐。

原版松手后的逻辑光标可在 9/10 之间变化，本地适配器在状态到期前保留 8/9 循环；二者都显示源帧 9。当前修复了展示阶段和持续输入缺失，没有重建原版客户端/服务器包更新的全部交错顺序，也没有凭客户端样本宣布伤害、耗蓝或服务端导弹时序一致。高 FCR、其他等级/装备和所有中断分支仍需另验。

前期失败日志保留：一次缺少 StateId 导入；完整世界测试最初没有载入 eanimdata，随后又沿用了等级 1 属性导致合法等级校验拒绝；Cursor 模拟系统的依赖注入也在测试装配中修正。最终按真实等级加载动画数据和输入依赖通过，没有删除原断言以制造通过。

## 复现入口与下一项

原始 run 位于 `tools/original-observer/broker/20261121-165133-4490d598/runs/20261121-185938-70b0693c/`。`export_arctic_sequence.py RUN FIXTURE` 校验并冻结序列证据，`inspect_arctic_sequence.py HOME OUTPUT` 校验离线表和回退分支，`:core:arcticBlastSequenceOriginalAnalysis` 导出真实生产回放，`compare_arctic_sequence.py FIXTURE PRODUCTION OUTPUT` 生成限定阶段比较。

下一项是 `arctic_blast_cast` / `ColdWindOverlay` 的挂载、循环和结束。当前 Skills 展示入口没有读取 prgoverlay/prgsound，这提示可能还少一层，但本轮没有采集原版附着层调用，不能只凭资源头就决定播放时机或长度。继续使用后台可用的观测方式；随后再缩小目前每组少一次回调的输入相位差。

后续已完成这一附着层的后台轮询：确认 CltSt15 一次生成、21 tick 后结束，并补上起手层及声音请求。42 个有效原版光标对照通过，54 项相关回归通过。详见[起手附着层记录](arctic-blast-cast-overlay-analysis-2026-11-21.md)。此前的冰流回调数和绝对相位差仍保留，未宣称解决。

后续[出生边界复核](arctic-blast-release-boundary-analysis-2026-11-21.md)确认：第3/15条冰流后松手，两侧均再生成3条，总数6/18一致。旧回放将首次发布源帧0也算入已过持键区间；修正诊断时间零点后，16/40区间得到7/19，与原版旧样本相同。历史计数文件保留，但不再把它作为漏发收尾的证据。生产生成代码未增加发射，逻辑光标/绝对相位仍未完全对齐。
