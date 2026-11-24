# 极地风暴：转向、松手和重施法复核

已修复一个实际转向问题：动作已经排队、尚未消费时，如果当前施法目标改变，旧实现仍使用上一轮保存的坐标，可能多发一条旧方向冰流。现在保留待执行动作的身份，在消费时从同一 Casting 读取当前目标；服务端回调、冰流数量和首次更新规则不变。

## 原版依据与后台样本

固定版本的 CltDo24 帮助函数在 `D2Client+2A255` 调用 `1A950` 取得目标，之后才选择资源和分配冰流。`1A950` 从当前目标单位取坐标；无目标单位时读取角色当前路径目标。`CABAE/CABA8` 分别导入 D2Common ordinal10175/10176，读取路径偏移`0x10/0x12`。这里不存在上一轮 Java 事件坐标快照这一层。

这部分来自 hash 固定的本地 PE/Capstone 检查，不是原版运行时调用堆栈。后台运行值另由 run `20261121-233623-a5ced4c9` 验证：

- 未显示的私有桌面、CXArctic裸装技能1级、自动进入地图；在3条冰流已出现且角色动作字节为1时，对私有 HWND 发送持续右键状态下的鼠标移动，转到垂直的通畅方向；第15条后松手。
- 转向消息约609ms发出，但路径目标约在后续一次更新才改变。第4条约649ms出现时路径目标仍旧；第5条约728ms出现时已变新目标。
- 共18条：前4条速度`(36864,0)`，后14条`(0,36864)`，单位为每原生tick的16.16位移。改变目标后，每条新冰流的方向与该次实际路径目标一致。
- 对18条冰流保留的稳定端点样本，速度始终不变，位置满足“出生点+已过年龄×原速度”；先前发出的冰流没有随转向弯折。
- 转向至松手阶段身体源帧保持9，只有一个 ColdWindOverlay 实例；松手后3条收尾，最后无冰流残留。

不能把 PostMessage 的发出时刻当作原版已经改变目标。特别是第4条仍向旧方向，是当时路径目标尚未变化，不能拿它来证明应保留旧事件坐标。比较按实际目标变化的边界进行。

本地本批回放相应在4条已发布、下一动作待消费时改变实际输入目标；第5条起改向。这是本批样本边界，不是对所有机器输入延迟或所有转向的条数常量。本次采集仍为非原子只读轮询；没有前台切换、全局输入、调试器、暂停、进程内存写入或远程执行。所属原版游戏已关闭。

## 本地复现与修复

新增实际管线回归先在第一个动作后、消费前改变 `Actioneer.updateArcticBlastInput` 的瞄准点。修复前测试要求北向，却得到东向速度14.0625，明确复现了旧坐标问题；基线7项中只有这一项失败。

`InfernoPresentationSystem` 不再将原事件目标保存为下一轮必须使用的目标。消费时先核对 Casting 的 actionId/技能，再复制当前 targetId/targetVec 发射，因此新的输入在消费前到达时能正确转向。已经发布的冰流不重新绑定目标。

松手入口仍由 Actioneer 保留最后一次持键目标，不会因为松手时鼠标坐标改变就把已排队的尾流改向。取消与同轮重新施法仍由 actionId 隔离；旧待执行动作不会混入新施法，新施法只创建自己的起手层。

修改仅在客户端展示系统读取待消费目标的位置，没有修改服务端技能、伤害、耗蓝、状态时长或导弹数量。

## 验证结果与范围

35项限定回归全部通过，0失败、0错误、0跳过，包括8项阶段/转向检查、原有持键/松手、6/18计数边界、400个单冰流状态、冷资源、起手层，以及法师地狱之火/强化和声音生命周期。

实际输入与客户端系统顺序回放产生18条，方向序列与本批原版逐条一致：4条旧方向、14条新方向，松手后3条，一个起手层，最终回到NU且无残留。

原版样本覆盖通畅地形、对地瞄准的一次直角转向。所有角度、目标怪物死亡/删除、FCR/减速、联网输入传输、身体与冰流DCC实际朝向、音画精确同步和GPU绘制仍需分别验证。取消/重施法及松手时改变指针坐标本轮是本地回归，没有相应新增原版操作样本；不能扩大为这些场景已与原版全面一致。

## 复现入口与证据

- 后台任务：`tools/original-observer/jobs/arctic-blast-turn-private-desktop.json`，复用常驻接收器，不新增授权入口。
- 原始run：`tools/original-observer/broker/20261121-165133-4490d598/runs/20261121-233623-a5ced4c9/`。
- 静态入口/输出：`inspect_arctic_target_resolution.py`、`arctic-blast-target-resolution-static-1.10f.txt`。
- 原版导出：`export_arctic_turn.py RUN/orchestrator FIXTURE`，fixture为 `core/src/test/resources/original/arctic-blast-turn-1.10f.json`。
- 本地导出：`:core:arcticBlastTurnOriginalAnalysis`，文件 `tools/original-observer/arctic-blast-turn-after-fix-1.10f.json`。
- 对照：`compare_arctic_turn.py FIXTURE REPLAY OUTPUT`，结果 `class-skill-batch-2026-11-21/druid-arctic-blast-turn-comparison-1.10f.json`。
- 测试：`arctic-blast-retarget-before-fix-validation.log` 保留失败基线，`arctic-blast-retarget-final-validation.log` 和 `arctic-blast-retarget-test-results/` 保存最终结果。
- 最终来源/清理/哈希清单：`tools/original-observer/arctic-blast-retarget-validation-2026-11-21.json`。

后续[目标移除与待动作生命周期复核](arctic-blast-target-lifecycle-analysis-2026-11-22.md)定位到删除目标在本轮仍残留 Position 的窗口，已改为校验有效实体订阅。新增5项加既有35项回归通过。它是本地边界修复和原版静态解析链核对，没有新增原版死亡或GPU采集。
