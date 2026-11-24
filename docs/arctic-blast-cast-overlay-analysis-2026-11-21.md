# 极地风暴：ColdWindOverlay 起手层（2026-11-21）

本轮确认并补上起手时挂在施法者身上的 ColdWindOverlay，以及一次起手声音请求。它是一次播放的 21 帧附着层，不是持续喷射期间无限循环的冰流。后台原版时钟与本地生产实现的限定对照通过，GPU 画面、实际原版音频播放和前轮冰流回调数量差异仍未验收。

## 原版后台证据

run `20261121-211726-7197b5ae`，常驻 broker 会话 `20261121-165133-4490d598`。同一独立裸装 `CXArctic`，角色 99 级、Arctic Blast(230) 一点，其余硬技能零点；两次按住约 640 ms、1600 ms，各观察六秒。原版位于未显示的私有桌面，无切前台、调试器、暂停、进程内写入、远程调用或 GPU 回读。所属游戏已关闭，broker 保持 ready。

本次采集复用强化技能已验证的图形数据布局：单位 +54 指向图形数据，图形数据 +2C 为附着层链头，节点 +A4 为下一项。直接读取当前进程生效的 Skills 编译行，prgoverlay 得到索引 **196**；Overlay 编译行对应 `Expansion\ColdWindOverlay`，Frames=21、AnimRate=16、Trans=3。施法前目标层不存在。

两组各只观察到一个该类型实例，共 111 条原始附着层快照，按指针、光标、计时参数等去重后为 44 个状态。每组含 21 个有效光标 `0,256,...,5120`，以及一个终点 `5376` 状态；终点处节点 +08 从 1 变为 0，随后移除，不把它算作不存在的第 22 张 DCC 帧。两组均无拒绝读取，仍保留非原子轮询可能遗漏或混合更新的限制。

- 类型始终为 2（ONCE），步进 256，总游标 5376，即 21 个 25 Hz tick，约 0.84 秒。
- 短按组松手后该层仍完成剩余动画；长按组仍在喷射时，该层已经结束，没有再次生成。
- 附着于施法者图形链表，不是新的独立场景单位，也不是每条冰流附带一份。

真实 DCC 头为 1 方向、21 帧，资源哈希来自保留的静态资源清单。未拦截本轮绘制函数，不能把这些内存光标叫作实际 DCC 选帧、屏幕或像素证据；节点中名为 draw_mode 的旧观测字段也不作为 GPU 混合模式的验证结论。

## 专用回调与实现

固定哈希 D2Client 的客户端起手表 RVA `0xD7488`，槽 15 指向 `0x2A050`。离线反汇编确认：原版在 INFERNO 尚未激活时，从技能 +10C 读取 prgoverlay，以类型 2 调用 `0xBBA70` 创建层；再从 +10E 读取 prgsound，调用声音调度器。共享构造器使用 `AnimRate<<4`。这与本次进程中一个 ONCE 实例、256 步进的观察相符。静态调用关系不是运行时堆栈，也没有捕获原版音频样本。本地 Win32/Capstone 输出不标记为 REA Evidence。

Riiablo 的 SkillCastHandler 原先对 CltSt15 直接跳过，legacy Skills.Entry 没有读取 prgoverlay/prgsound。本轮使用已有的无损 `NativeSkills` 表读取这两个字段，避免修改 legacy BIN 布局，也不把空的 castoverlay 当成该层来源。

`InfernoPresentationSystem` 在真实 SkillStartEvent 的 skill230/CltSt15 路径请求附着层与 `druid_windcast` 起手声音，按 Casting.actionId 去重。同一施法的持续输入或重复起手通知不重播；新施法获得新的出生时钟。实体移除会清除本地记录。网络无 Casting 身份时仍使用起手事件，其全部重复包/状态交错情况未验收。

`NativeArcticCastOverlay` 只处理此名字的非持久层，由 OverlayManager 管理独立 25 Hz 时钟；OverlayStepper 不再对它重复推进。第一阶段保留出生帧 0，随后即使 DCC 未加载也推进年龄并按 21 帧寿命删除；延迟可用的资源直接定位到当前帧，完全错过寿命的资源不再补放晚到闪光。该层接入已有原生附着层位置、帧率和混合策略，但本轮未做 GPU 混合画面对照。

当前状态/施法持续时间不决定这个 ONCE 层的寿命。它结束时不会删除同一角色的其他持久附着层。持续冰流、伤害载体和前轮 S1/SQ 调度没有新增修改。

## 生产验证

`ArcticBlastOverlayOriginalAnalysis` 使用真实 Actioneer、SkillStart、OverlayManager、OverlayStepper 及实际 DCC 头，分别运行热资源、延迟 12 次更新、完全错过寿命后三种情况。诊断没有直接创建假起手事件来替代真实施法，也没有执行 GPU 绘制。

全部 **42 个原版有效状态**按年龄对照生产帧号和步进，零缺失、零差异；两个终点状态单独登记，不用钳位末帧冒充新的有效帧。热资源产生 0..20 帧，延迟资源首次加载时在年龄 11/帧 11，完全未加载的层按期清除，之后资源可用也不会重新出现。

最终 `arctic-blast-overlay-final-validation.log`：**54 项通过，0 失败、0 错误、0 跳过**，九个套件。包括新增起手附着层 7 项、角色序列 6 项、冰流/Inferno/权威展示，以及状态附着层共存与资源生命周期回归。新增验证覆盖一次起手声音请求、持续施放不重播、新施法重置年龄、60 Hz 更新保持 25 Hz 特效时钟、冷资源到期和其他持久层共存。声音请求次数通过不等于原版实际音频样本一致。

初期诊断日志保留：通过精确类名调用 world.getSystem(OverlayManager.class) 未解析到测试中的派生记录器，导致起手层没有创建；改为可选依赖注入后，真实起手测试通过。最初未加载资源的用例也新增了“起手确实创建了一次”的断言，防止把没有生成任何层误算为到期清理成功。

复现和保留文件：

- 原始 run：`tools/original-observer/broker/20261121-165133-4490d598/runs/20261121-211726-7197b5ae/`
- fixture：`core/src/test/resources/original/arctic-blast-overlay-1.10f.json`
- 生产导出：`tools/original-observer/arctic-blast-overlay-production-1.10f.json`
- 比较：`tools/original-observer/class-skill-batch-2026-11-21/druid-arctic-blast-overlay-comparison-1.10f.json`
- 测试 XML：`tools/original-observer/arctic-blast-overlay-test-results/`

`export_arctic_overlay.py RUN FIXTURE` 冻结源快照与哈希；`inspect_arctic_overlay.py HOME OUTPUT` 生成限定离线记录；`:core:arcticBlastOverlayOriginalAnalysis` 导出三种生产生命周期；`compare_arctic_overlay.py FIXTURE PRODUCTION OUTPUT` 重建对照。

本轮不重复增加职业技能覆盖数量。下一步仍需缩小前轮两组各少一次冰流回调的起止相位差，且不能因此反向调整已经采到的附着层寿命。原版服务端伤害/耗蓝、实际方向和完整 GPU 效果保持未验收状态。

后续已完成[松手边界计数复核](arctic-blast-release-boundary-analysis-2026-11-21.md)：相同实际出生边界下，两侧收尾均3条；校正旧诊断初始化区间后，总数量也对上。没有修改附着层时钟或额外补发导弹。首次发布/逻辑光标相位仍单独保留。
