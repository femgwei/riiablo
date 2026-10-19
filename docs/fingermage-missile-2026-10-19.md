# 扼杀者 FingerMageSpider 导弹

## 发射目标补修

实机反馈主体没有转向后，确认通用 ServerSkillSystem 发射分支只用 SkillDoEvent
目标计算初始方向，没有把 targetId 保存到导弹。FingerMage AI 使用 Casting，
并不保证拥有 Target 组件，因此 SrvDo11 的 owner Target 回退取不到目标。
现在发射 pSrvDoFunc=11 导弹时保留 event.targetId，施法结束后仍跟踪该单位。
新增 FingerMageLaunchIntegrationTest，通过真实 ServerSkillSystem/ServerEntityFactory
发射技能 196，覆盖没有 owner Target、清除施法状态、目标连续改变位置后两次转向。
此前仅手动构造 owner Target 的测试未覆盖这个生产接线缺口。
使用本机 1.10f 资源运行 FingerMageLaunchIntegrationTest 和 FingerMageFlightTest，
两项通过（零跳过、零失败）。本轮只补发射目标，不重复运行营地烟测。

依据：本机 Diablo II 1.10f MPQ、D2MOO SkillMonst.cpp SrvDo101、
MissMode.cpp SrvDo11，以及原版 D2Client.dll 客户端回调 0x6FB31DD0。
DLL SHA256：94466B84C0593AB682F1D3D8DC65EF4F8170F33E430865D802BC752458B2B282。

MonStats 的 Strangler 为 fingermage2，技能 196、导弹 177。
Missiles 行：pSrvDoFunc=11、Param=5/20/2，pCltDoFunc=15、
CltParam=3/20/5/20/2，CltSubMissile1=fingermagespider。
DCC 头确认 16 方向、每方向 5 帧；保留原始资源动画，不人为施加旋转。

原版服务端按剩余帧模 5 重算路径，距离门槛 20；新路径目标为当前位置加上
各轴目标方向符号乘 2，不是围绕目标旋转。距离使用原生整数、单位尺寸算法。
客户端回调按主体剩余帧模 3 创建同资源表现子导弹，并设置其 CurrentFrame=0；子导弹
计时到 -20 后删除。它不再追踪或继续生成拖尾。

实现增加 SrvDo11 的方向修正，以及本地/网络主体共有的客户端拖尾；拖尾保持静止，
20 帧清理，禁止伤害、追踪和递归生成。原版技能创建 flags=3，calc1 的 nLoops
没有 flags&8，因此不增加主体寿命或额外权威投射物。

验证：新增 FingerMageFlightTest 覆盖倒计时间隔、owner 目标回退、符号方向、
速度保持和距离外不转向；FingerMageTrailTest 覆盖子弹表现权限及寿命边界。
普通 core:test 编译被已有 AssassinSkillSpecializationTest 缺少 assertSame/
assertNotSame 导入阻断，使用本地临时 init script 限定这两个测试源码验证。
两个定向测试均为 1 项、零失败。使用本机 1.10f MPQ 的 desktop:offscreenCamp
通过，desktop/build/visual-tests/fingermage-20261019/rogue-encampment-manifest.txt
记录 result=PASS，客户端退出清理也通过。
实机扼杀者追踪、拖尾连贯性仍需另一台电脑复验；营地烟测不替代该画面验收。
