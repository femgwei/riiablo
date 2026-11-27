# 白骨装甲：私有桌面原版绘制对照与阶段转换帧修正

后续受击/耗尽采集与删除匹配修复见
`necromancer-bone-armor-hit-cleanup-analysis-2026-11-26.md`。
下文“尚未采到受击/耗尽”和按高组号清理是本轮历史状态，已由后续记录更新。

本轮补上前一轮白骨装甲（68）缺失的原版运行时绘制证据。
前一轮 setfunc6/remfunc3、三组出现链与快照值的修复见
`necromancer-bone-armor-animation-chain-analysis-2026-11-26.md`。
本轮通过实际绘制发现并修复了阶段转换时停留第0帧导致的相位差，
不是把前一轮离线分析或单测改标为原版通过。

## 后台采集结果

Run：`20261126-171517-98b556fb`，任务 `necromancer-bone-armor-private-desktop`。
原版目录明确固定到 `G:/BaiduNetdiskDownload/Diablo II 1.10F`，
CXNecro、技能68/20级，普通定向 HWND 右键施法。

复用已用于骨牢的私有桌面父进程，把既有菜单/最小化采集流程放入
`riiablo_bonearmor_be2f43763c46`。菜单的可见策略仅用于未显示的私有桌面；
实际 D2Gfx 采集要求 minimized。未切换输入桌面，未调用前台激活，未移动系统光标，
未发送全局输入。父进程113次检查私有桌面均不是输入桌面：

- 输入桌面前后均为 Default。
- 前台 HWND 前后均为43780228，屏幕尺寸均为1920x1080。
- 原生采集器 passed，5255个 export events，detached=true、cleanup_confirmed=true。
- process-session completed、内部 orchestrator passed、外层私有桌面任务 passed。
- 拥有的原版进程正常关闭（未强制结束），私有桌面句柄已关闭；broker回到ready。

原始记录：

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261126-171517-98b556fb/orchestrator/worker/capture-run/process-session/001-bone-armor-d2gfx-minimized/d2gfx-call-trace.jsonl
```

归一化：`tools/original-observer/necromancer-bone-armor-original-draw-2026-11-26.json`。
脚本：`normalize_d2gfx_bone_armor_trace.py`。
测试夹具：`core/src/test/resources/original/necromancer-bone-armor-draw-1.10f.json`。

## 实际出现、计时器与三组前后层

筛出382次白骨装甲覆盖层绘制，全部 window_state=minimized：

- 140 / bonearmor_cast：31次，采到帧1..11；第0帧未采到，不推断原版不播放。
- 141 / bonearmor_front：163次，覆盖全部0..23帧。
- 142 / bonearmor_back：166次，覆盖全部0..23帧。
- 143 / bonearmor_null1：7次，帧0、1。
- 144 / bonearmor_null2：15次，帧0、1。

两个 null 资源也经过绘制调用，不能把它们的存在理解为可见像素。
NE/SC采到帧1、3、5、7、9、11、13；旁路候选模式为[1,10]，
`client_animation_skill_mode_observed=true`。

按每次 StartDraw 划分绘制批次，保留73个包含护盾且由下一次 StartDraw 闭合的区间。
其中47个区间同时有三次 front 和三次 back，同侧三次使用不同动画帧，
前后两侧的三个帧值均一一相同。例如首个完整三组区间为：

```text
back:  1, 9, 16
body:  NE components
front: 1, 9, 16
```

47个完整三组区间都符合 back -> NE body -> front 的调用顺序。
这里 body 只选择 ORD10072/RVA42A0 的 NE 组件绘制；较早的 ORD10075/RVA4360
是独立的角色阴影调用，不混入主体区间的前后排序检查。
Riiablo 现有 RenderSystem 按 PreDraw 在角色前后绘制覆盖层，配置策略与此相容；
本轮没有执行 Riiablo GPU 渲染，不能把源码分支检查称为 GPU 对照通过。

StartDraw 区间是绘制区间，不是模拟帧；采集使用软件断点，毫秒耗时包含调试停顿，
不能用于精确测量施法帧速。相同ID的绘制次数和相位支持三组展示，
不独立证明效果 GUID、所有完整帧或每次资源创建/删除事件。

## 对照发现的相位问题与修复

前一轮重建的出现链在阶段完成后，下一阶段先发布第0帧，下一模拟帧才推进。
它的三组循环相位间隔是8、8、8，无法产生原版采到的三元组1、9、16。
本次47个完整三组区间均保持模24的相对相位0、8、15（环形间隔8、7、9）。

这与固定DLL的更新循环相符：RVA B7EBE 每次回到链表头扫描，
RVA B7ECF 利用节点的更新标记跳过本轮已处理节点；mode4/mode7完成时创建的新节点
没有已处理标记，因此会在同一次更新中继续推进。mode4转循环、mode7转出现层都需要
在当前模拟帧推进一次，不能额外停留第0帧。

OverlayManager 新增专门的阶段接续路径，为接续动画播入一个25Hz帧的游标，
热缓存时立即应用，避免由 AssetManager 的下次处理再引入一帧。
初次创建仍保留原有出生帧；冷资源仍等待资产就绪，该加载策略的原版等价性未实测。

以本地初始出生游标0为基准，热缓存三组分别在年龄12、19、27进入循环，
进入时游标为1。年龄27的三组循环帧为16、9、1，覆盖原版首个完整三元组。
这些年龄是 Riiablo 的受控生产时钟，原版本轮没有采集可直接对齐的施法起始模拟tick。

新增回归 `everySampledOriginalThreePairBatchIsReproducedByTheProductionClock`：
运行真实 World/状态/OverlayManager 链路，保留同名层的重复帧值，检查原版47个完整批次的
前后帧多重集合均由本地生产时钟产生。另检查实际MPQ资源能表示所有采到的可见层和计时器帧。
这是逐绘制批次相位对照，不是只验证“资源存在”或把单个帧号断言当作整段一致。

## 验证、记录和剩余项

JDK17，显式 D2_HOME 与 -Pd2Home 固定1.10F，core与server:d2gs分别执行。
准确结果、逐项XML、命令、来源SHA256与清理状态见
`tools/original-observer/necromancer-bone-armor-original-draw-validation-2026-11-26/validation.json`。

本轮 core50项（含9项白骨装甲用例）、server:d2gs7项、Python22项，共79项通过，
无失败、错误或跳过。`git diff --check`通过，收尾未发现1.10F原版进程。

前一轮隐藏进入失败的三个run、初轮测试与扩大检查失败记录均保留；
本次成功运行单独记录，未改写历史失败为成功。
individual_runtime_status 更新为 observed_private_desktop_minimized_d2gfx_partial；
职业批次 runtime_status 仍保持 not_run_in_class_batch。

仍未采到原版受击减少、耗尽、重新施法或中断清理。前一轮按组取消出现阶段高组链的
策略仍是合理重建，缺少原版同ID链表删除顺序的运行时对照。
未完成原版与 Riiablo 最终 GPU 像素、颜色、光照、阴影和所有方向对照。
本轮范围内的原版出现层、两类延迟计时层、三组前后循环及其相位已有实际绘制证据。
未提交或推送，保留工作区其他修改、.tools、.gradle-user及heartbeat。
