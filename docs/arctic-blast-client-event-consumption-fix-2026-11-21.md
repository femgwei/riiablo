# 极地风暴客户端动作消费与首次更新修复

已修复两处客户端时序差异：实际施法的冰流改为在下一次客户端更新消费待执行动作，出生轮保留年龄0；属于本地玩家的移动冰流在首次更新执行原版的一次额外推进，使位置、寿命和动画共同从年龄0到2，随后逐帧推进。没有增加冰流数量，也没有移动服务端伤害、耗蓝或施法状态的回调时机。

## 这次找到的原版分支

以下是对固定 SHA256 的原版 DLL 做本地 PE/Capstone 静态检查，并由后台运行值支持的结论，不是捕获到的原版调用栈：

- `D2Client+A8C9D..A8CCF` 读取角色动作字节，符合条件时先调用 `1CA30` 消费已有动作；`A8CF8` 随后调用 D2Common ordinal10373，推进并重新设置本轮序列动作。不是“本轮跨过动作点，立即同步执行全部客户端效果”。
- `1CA30 → 1C6B0 → 1C8F7/1C909` 按 Skills 行的 `cltdofunc`（偏移F4）从 `D75A8` 表调用客户端 Do；技能230的槽24指向 `2A3B0 → 2A1D0`。
- `880F0` 单位循环先更新导弹，包括本地导弹表`11BC00`，之后才更新玩家表`11AA00`。因此，玩家更新中新建的冰流会保留出生状态，等下一轮处理导弹。
- `9D8D3..9D90E` 为指定游戏类型及本地玩家所有的导弹设置首轮重复资格。本轮实际 game_type=0，符合该分支；没有推定其他枚举值的产品含义。
- `9DAE4..9DB19` 在一次 CltDo1 更新后，检查已过帧数是否等于1、路径速度是否非零；符合时清除重复资格，回跳`9D90E`，再执行一次移动、寿命递减和后续处理。
- `CB4D8` 导入 D2Common ordinal11125，确实返回 `TotalFrames-CurrentFrame`；`CADE2` 导入 ordinal10147，是路径速度 getter。因此年龄0→2有明确的首轮重复分支，不再只是轮询漏看年龄1的猜测。

已有 CltSt15 和 CltDo24 都能回退到点8的结论仍成立。动作消费、回退光标和新冰流发布应分别记录，不能把点8直接当成某个函数已经执行的证据。

## 后台复核与采集时钟

最终 run `20261121-231927-f587a20d`，两组按第3/15条冰流出生松手，总数仍为6/18，共24条。所有记录中的导弹 owner_type=0、owner_id=1，与自动入场保存的 CXArctic 玩家 unit_id=1 相同；game_type=0，满足本轮静态首轮重复分支的本地玩家条件。

24条都捕获年龄0出生，随后稳定观察到年龄2，并验证位置推进两次速度步长。该观察与静态循环共同支持首轮双更新；不声称捕获了函数栈，也不声称内部不存在短暂年龄1或已验证 GPU 提交。

这套 Python 3.9 的 Windows monotonic 实测约15.6ms分辨率。最初两次提高采样频率的尝试仍受其影响，已保留原始结果。最终采集使用 QueryPerformanceCounter，名义分辨率100ns，在前2.1秒最多1800次扫描，以1ms最小间隔分段采集，之后回到普通轮询；没有修改系统计时器分辨率。每次仍比较扫描前后角色标记、种子和分配计数，排除检测到的跨更新扫描，不能因此称为原子快照或100ns游戏观测精度。

最终3470次扫描中3428次端点标记相等。原版全程在未显示的私有桌面运行，无前台切换、全局输入、调试器、暂停、内存写入或远程调用；所属游戏已关闭，常驻接收器保留。

## Riiablo 修改

`InfernoPresentationSystem` 对真实 Arctic SQ 施法产生的 SkillDo 保存客户端待执行动作，到下一次展示更新消费。服务端继续在原来的动作点收到事件。消费时验证 Casting 的 actionId 和技能，取消施法、角色删除或切换施法后不会补出过期冰流；目标向量做独立拷贝，不保留可变事件参数的引用。

该系统标记本轮消费中新生的冰流，`MissileImpactPresentationSystem` 不在其出生轮再次移动它。后续首次更新只为本地玩家所有、速度非零的技能230展示冰流执行额外一步；位置、nativeFrame 和动画时间一起推进，而不是仅把 DCC 帧号加一。远端所有者和零速度不进入此分支。没有新增持久导弹字段，也没有改变服务端检查点格式。

直接构造 SkillDo 的既有单冰流数值诊断保持独立，不把它冒充真实施法时序。回归曾发现新出生保护误作用于该旧入口，已将保护限定到实际消费的待执行动作，冷资源/60Hz验证恢复通过。

实际系统顺序回放现在记录：角色动作 tick11，冰流出生 tick12，出生更新结束为序列点9/源帧9/年龄0；tick13为年龄2，位移两步、DCC帧1；之后年龄3。首次分配栈来自 `InfernoPresentationSystem.begin → emit → factory`，不再从 AnimStepper 的即时事件栈直接分配。

## 验证和范围

30项相关回归通过，覆盖实际持键/松手、尾流数量、取消、资源延迟、原有400个冰流状态、起手附着层、法师地狱之火/强化展示及声音生命周期。随后补充速度条件，用4项阶段测试检查本地首轮、远端、取消及零速度；合计31个不同测试用例，重复执行3项，最终结果另见校验清单。

阶段对照检查24条原版冰流的出生/首更新状态，以及本地11次真实客户端发射的动作消费、0→2运动和动画阶段；原有按出生数松手的6/18计数回归仍通过。此处未验证伤害、耗蓝数值是否与原版相同，只确认本次没有改动其执行路径。

已解决上一轮的“光标差一轮”及“下一稳定年龄2”这两个具体问题。其他等级、所有输入转向/FCR/减速组合、碰撞边界、网络游戏模式、绝对出生位置的格中心偏移、A/B全局随机流、音画精确同步和GPU绘制仍未完整验收。

## 复现文件

- 静态入口/输出：`tools/original-observer/inspect_arctic_action_consumption.py`、`arctic-blast-action-consumption-static-1.10f.txt`。
- 原版导出：`export_arctic_event_phase.py RUN/orchestrator OUTPUT`，fixture为 `core/src/test/resources/original/arctic-blast-event-phase-1.10f.json`。
- 实际回放：`:core:arcticBlastPublicationOriginalAnalysis`，输出 `tools/original-observer/arctic-blast-event-consumption-after-fix-1.10f.json`。
- 阶段比较：`compare_arctic_event_phase.py FIXTURE REPLAY OUTPUT`，结果 `class-skill-batch-2026-11-21/druid-arctic-blast-event-phase-comparison-1.10f.json`。
- 回归：`arctic-blast-event-consumption-final-validation.log`、`arctic-blast-event-consumption-velocity-validation.log`；XML保存在 `arctic-blast-event-phase-test-results/`。
- 最终证据清单：`tools/original-observer/arctic-blast-event-consumption-validation-2026-11-21.json`。

历史原版记录、失败验证日志和修复前回放均保留，旧的未决结论以本篇后续证据为准。

后续转向复核发现并修复了待执行动作保存旧目标的问题，见[转向/松手与重施法记录](arctic-blast-retarget-release-analysis-2026-11-21.md)。本篇描述的事件时序及首轮0→2规则保留；目标坐标现在在消费时读取当前 Casting，而不是沿用排队时的快照。新后台样本与本地18条发射方向序列一致，35项限定回归通过。
