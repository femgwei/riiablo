# 极地风暴：松手边界与“少一次”计数复核

本轮没有发现缺少一条收尾冰流。以实际客户端冰流出生数触发松手时，原版和 Riiablo 都会再生成三条，总数分别为 6/18；修正旧回放的计时零点后，原来的 640/1600 ms 两组总数也分别对上 7/19。修复的是验证驱动的时间基准，**没有向游戏额外添加 SkillDo 或补发导弹**。逻辑光标和客户端发布相位仍存在差异，不能把数量对上扩大为全部调用顺序一致。

## 新的后台实验

run `20261121-220407-c5eb938e`，固定任务 `arctic-blast-birth-release-private-desktop`。仍使用独立裸装 CXArctic，角色99级、技能1级，在未显示的私有桌面自动进入 Blood Moor；无前台切换、调试器、暂停、内存写入、远程调用、全局输入或 GPU 回读。常驻 broker 复用，所属游戏已关闭。

两组分别在客户端哈希表里观察到第3条、第15条新冰流时，立即向该游戏私有 HWND 发送右键释放。它们是实际导弹单位的出生观察，不把毫秒持续时间当作精确游戏帧，也不把它们叫作已捕获的 CltDo24 调用堆栈。

原版结果：

- 第3条后松手：之前3条，之后3条，总6条；后三条首次可见于松手后约93、171、250 ms。
- 第15条后松手：之前15条，之后3条，总18条；后三条首次可见于松手后约79、157、235 ms。

全部24条单位均直接捕获到 TotalFrames=CurrentFrame=17 的出生状态，没有用倒推寿命补算不存在的出生。两次释放边界均为角色逻辑序列点9、源帧9、剩余4帧；最终本技能无残留。轮询是非原子的，发送释放消息也不等于证明原版在哪个逻辑 tick 消费消息。

## Riiablo 实际出生观察

`ArcticBlastBirthBoundaryOriginalAnalysis` 使用真实 Actioneer、序列、状态和客户端展示管线，以 Artemis 的 Missile 实体插入通知记录实际 presentationOnly/skill230 导弹。实体 id 被复用时仍按新的插入计数；没有把原有工厂 id 集合或假 SkillDo 当作出生总量。

它同样在第3/15条客户端导弹发布完成后松手。两组分别总计6/18条，松手后的三条都在本地第2、4、6 tick 生成，最后回到 NU 且导弹残留为零。以25 Hz换算后的观察时间与原版本批次快照最大偏差分别13 ms、5 ms，仅表示此次轮询时钟上的观察差值，不是已证实的原生 tick 同步精度。

原版释放时逻辑点9，本地点8，但二者都显示源帧9。这一相位差仍保留为未知项。现在可以排除“从相同出生计数边界松手时，本地少执行一次收尾发射”的判断；不能据此宣称客户端内部调用栈、网络包顺序、伤害或耗蓝一致。

## 旧回放的时间零点错误

此前 `ArcticBlastSequenceOriginalAnalysis` 在调用 cast 后，直接把第一次 world.process 记为持键第1个已过区间。该次更新实际上才把 S1 的出生姿态（源帧0）初始化并发布。于是标为16/40帧的旧实验，把零时刻样本也算成了一个已经流逝的区间，从初始姿态起只推进15/39个区间，产生6/18次回调。

新的回放先通过正常输入/世界更新建立源帧0姿态，明确记录 `time_reference=initialized_S1_source_frame_zero`，再推进16/40个完整区间。没有跳过起手帧、修改序列表、强制状态寿命或人工添加发射。两组自然产生7/19条，与旧原版两组观察数一致；该计时方法也有独立出生观察回归。

历史 `arctic-blast-sequence-production-1.10f.json` 和旧比较结果保留，不能再把其6/18与原版7/19的差值直接当作游戏漏发证据。新的校准结果保存在不同文件中。原版墙钟时间、消息消费时间与本地固定步的绝对对齐仍未建立；这次只完成计数基准和收尾发射数量复核。

## 验证与保留文件

本轮没有修改生产施法、伤害、动画或导弹生成代码。只增加后台实验、实际出生观察、比较和时间基准修正。

限定验证 `arctic-blast-birth-boundary-validation.log`：15项通过，0失败、0错误、0跳过，包含新增出生边界2项、既有角色序列6项、起手附着层7项。另通过真实校准序列导出与比较校验，无需因诊断文件变动重跑全部职业战斗测试。

- fixture：`core/src/test/resources/original/arctic-blast-birth-boundary-1.10f.json`
- 原始run：`tools/original-observer/broker/20261121-165133-4490d598/runs/20261121-220407-c5eb938e/`
- 边界生产回放：`tools/original-observer/arctic-blast-birth-boundary-production-1.10f.json`
- 校准序列回放：`tools/original-observer/arctic-blast-sequence-calibrated-production-1.10f.json`
- 新比较：`class-skill-batch-2026-11-21/druid-arctic-blast-birth-boundary-comparison-1.10f.json` 和 `druid-arctic-blast-caster-calibrated-comparison-1.10f.json`
- XML：`tools/original-observer/arctic-blast-birth-boundary-test-results/`

导出入口为 `export_arctic_birth_boundary.py RUN FIXTURE`；生产任务为 `:core:arcticBlastBirthBoundaryOriginalAnalysis`，常规序列任务仍为 `:core:arcticBlastSequenceOriginalAnalysis`；比较入口为 `compare_arctic_birth_boundary.py FIXTURE PRODUCTION ELAPSED_REFERENCE OUTPUT`。原始记录中的旧 worker 进度字段 hold_ms 曾存放3/15这一边界参数，本轮导出依据经过校验的 identity.release_after_births 与 release_boundary，后续 worker 已明确区分两个参数，未重写冻结的原始文件。

下一步若继续提高相位精度，应比较首次导弹发布及第一次更新的时机：在相同源帧9下，原版逻辑点9与本地点8的区别尚未解释。它不再被概括为“少一次回调”。其他等级、所有输入相位/FCR、原版服务端伤害、实际方向和 GPU 仍需分别验收。

后续见[动作消费与首次更新复核](arctic-blast-client-publication-analysis-2026-11-21.md)。补采确认原版动作标记和分配分阶段，且 CltSt15 也能回退到点8；回退光标不能直接当作 CltDo24 调用时间。旧 Fixture 还把展示运动放在序列动画之后，造成新冰流出生轮提前移动；测试入口已按正式客户端相关系统相对顺序修正。此处6/18数量结论不变，历史回放的出生轮运动相位不能扩大为正式客户端验证。
