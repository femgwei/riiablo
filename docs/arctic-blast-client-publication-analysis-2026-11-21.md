# 极地风暴：动作标记、冰流分配与首次更新

本轮把之前“相同源帧9、逻辑光标差一帧”的问题缩小到动作消费/客户端冰流分配阶段，并修正测试入口的系统顺序偏差。没有修改游戏施法、导弹数量、伤害或动画实现，也没有为了对齐快照加入延迟或跳帧。原版具体函数的运行时调用顺序仍未完整捕获。

## 后台补采

run `20261121-223507-1d2b1d4b` 复用常驻 broker，在未显示的私有桌面启动原版，自动进入地图并执行第3/15条冰流后松手。两组仍分别6/18条，所属游戏已关闭。没有切换桌面、前台输入、调试器、暂停、内存写入、远程执行或 GPU 回读。

每次单位表遍历前后读取角色序列、动作字节、随机种子和客户端分配计数。765次扫描中745次端点标记相等，20次发现变化而排除在阶段比较之外。端点相等并不构成原子快照，也不能排除 ABA 变化；结论仅限此次固定99级裸装、技能1级、固定输入的样本。

第一组首条冰流的稳定观察依次为：

1. 角色序列点8、源帧9、剩余5、动作字节1，客户端分配计数仍为214，两个所查导弹哈希表均无215号单位。
2. 随后角色序列点9、源帧9、剩余4、动作字节0，计数变为215，215号冰流首次可见，年龄0、仍在出生位置。
3. 后续首次观察到年龄变化时为2，位置相对出生点推进两次原生速度步长。

24条冰流都取得了动作标记已置位但本条尚未分配的前置样本、年龄0的出生样本和年龄2的后续样本。持续阶段出生在序列点9，两组最终收尾的最后一条出生在点11。不能将“首次观察到的更新为年龄2”说成“原版内部或画面绝对不经过年龄1”。

## 两处回退，不能只凭光标认定回调

对 SHA256 `94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282` 的本地 D2Client.dll 做离线 Capstone 检查：

- CltSt15，RVA `2A050`：已有 INFERNO 状态列表时也会设置序列点8、剩余`0x500`，见 `2A145..2A157`。
- CltDo24，共享帮助函数：先调用导弹创建器`9E070`，随后按 INFERNO 状态回退到点8、剩余`0x500`，见 `2A2FE`、`2A358..2A375`。
- 客户端分配器`87D50`在`87D57..87D6E`更新全局计数`F08FC`，经初始化调用链将本地单位同步链接到相应哈希表。

因此，“看见点8”不能唯一定位为 CltDo24 已完成。计数器观察也不支持本轮初期的简单猜测——仅仅是单位表晚发布。已确认的是原版动作标记样本与新冰流分配样本分处不同更新阶段。具体由起手刷新、动作消费、主循环顺序或首次更新的哪条路径形成，仍需入口级证据；本轮没有原版运行时调用栈。

## 本地实际调用与测试入口修正

新诊断通过测试工厂的只读回调记录创建栈，通过真实 Artemis 订阅记录实体插入，再记录每轮 world.process 完成后的状态。没有伪造 SkillDo：

`AnimStepper.processArcticBlast → Actioneer.onAnimDataKeyframe → InfernoPresentationSystem.onSkillDo → ServerEntityFactory.createMissile`

工厂返回时角色逻辑点10，之后回退到点8；客户端冰流在同一轮更新进入实体订阅，插入年龄0。这与原版稳定观察中的动作标记/分配分阶段现象仍有区别。

旧 `ArcticBlastSequenceIntegrationTest.Fixture` 将 AnimStepper 放在 MissileImpactPresentationSystem 前，导致新冰流创建当轮又被移动一次，本轮结束年龄1。正式 `ClientSimulationAssembly` 则把展示运动放在序列动画前，创建当轮结束时冰流年龄仍为0。

现已让 Fixture 默认采用正式客户端中相关系统的相对顺序，并提供显式旧顺序模式供历史复现。诊断同时导出两种顺序：

- 旧入口：出生轮结束年龄1，下一轮年龄2。
- 与客户端相对顺序一致的入口：出生轮结束年龄0，下一轮年龄1。

这项修改发生在测试入口，不能描述为修复正式客户端“出生就移动”的问题。回放使用实际施法/状态/序列/导弹/动画系统与 DCC 头，但仍是精简 ECS 世界，不包含完整 GameScreen、GPU 或网络传输。

## 结果与边界

“少一条收尾冰流”的判断仍被同出生边界实验排除。新的证据解释了为什么不能把历史本地插入快照与原版单位表快照直接逐帧等同，并消除一处测试入口引入的提前移动。

尚未解决的是原版具体何时消费动作、何时执行 CltSt15/CltDo24，以及新冰流首次更新为何在此次稳定观察中直接到年龄2。正式顺序回放的下一轮年龄1与之仍不同。暂不对游戏增加固定延迟、补发冰流或跳过年龄1。

下一步应只核对客户端技能事件消费和导弹首次更新入口，建立可区分这两个阶段的证据后再调整展示调度。无需前台截图或像素对齐，也不应为此改变伤害/耗蓝路径。

## 验证与文件

默认 Fixture 顺序修正后，15项限定回归通过，0失败、0错误、0跳过，覆盖出生边界2项、角色序列6项和起手附着层7项。两种顺序诊断和24条原版阶段导出/比较校验通过；Python编译及最终证据哈希另记在校验清单。

- 原始后台 run：`tools/original-observer/broker/20261121-165133-4490d598/runs/20261121-223507-1d2b1d4b/`，每组新增 `publication.json`，保留扫描读数和排除信息。
- 原版 fixture：`core/src/test/resources/original/arctic-blast-publication-1.10f.json`。
- 导出/比较：`export_arctic_publication.py`、`compare_arctic_publication.py`；静态入口 `inspect_arctic_publication.py`。
- 两种本地顺序：`tools/original-observer/arctic-blast-publication-orders-1.10f.json`，JavaExec `:core:arcticBlastPublicationOriginalAnalysis`。
- 比较结果：`tools/original-observer/class-skill-batch-2026-11-21/druid-arctic-blast-publication-comparison-1.10f.json`。
- 初次本地诊断 `arctic-blast-publication-production-1.10f.json` 只代表旧测试入口顺序，保留用于审计，不把文件名中的 production 当作完整生产客户端验证。
- 验证：`arctic-blast-publication-order-validation.log`、`arctic-blast-publication-test-results/`，校验清单 `arctic-blast-publication-validation-2026-11-21.json`。

先前原版/本地 JSON 和比较结果均保留。默认 Fixture 顺序调整不改变6/18、校准7/19的出生数量结论；历史回放的出生轮运动相位不能扩大为正式客户端验证。

后续已定位并修复这两个具体时序差异，见[客户端动作消费修复](arctic-blast-client-event-consumption-fix-2026-11-21.md)：原版先消费上一轮动作再推进序列，且本地玩家移动导弹在首轮已过帧数为1时会重复更新一次。新的后台所有者/游戏类型检查与高精度采样支持这些分支，本地实际施法回放的发布点及0→2更新已对齐。这里保留的是修复前研究记录，不再把其两个未决项作为最新结论。
