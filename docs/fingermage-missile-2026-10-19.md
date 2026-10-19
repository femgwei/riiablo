# 扼杀者 FingerMageSpider 导弹

## 命中亮斑与近战补修

- 原版 D2Client 1.10f CltHit10 回调位于 0x6FB366D0，读取 Missiles
  行 +0x36 的 ProgOverlay，调用 0x6FB5BA70 附着于被命中单位；
  fingermagespider 对应 fingermagespiderexplode，而不是通用 fire_explode。
  现补此分派，并禁止叠加通用元素火焰；墙面命中不附着亮斑。
- D2MOO SrvHit19 按技能 auratargetstate/auralencalc/aurastat 应用状态。
  技能 196 为 fingermagecurse，持续 30 + 10*(lvl-1) 帧，
  manarecovery 为 -24*lvl；重复命中替换到期时间及属性，不取较大持续时间。
- D2MOO AITHINK_Fn070 的参数 0/1 是近战/施法概率，2/3 是恢复/逃跑
  血量门槛。旧实现将这四项错位；现恢复原始索引。
- P3/P4 的拖尾密度差异尚未完成画面验收。原版每 3 帧生成、20 帧存活
  可以形成约 6～7 格，不是一次发射 6 枚权威导弹；本轮不添加数量上限。
- 使用本机 1.10f MPQ 定向验证命中亮斑分派（含墙面不附着）、诅咒属性和
  覆盖刷新、追踪发射以及 AI 近战分支；未运行全量构建或营地烟测。

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
客户端回调按主体剩余帧模 3 创建同资源表现子导弹，并设置其 TargetX=0；子导弹
使用 TargetX 作为私有倒计时，到 -20 后删除。它不再追踪或继续生成拖尾。
导入序号核对：11140/11141 是 SetTargetX/GetTargetX，11124 才是 GetCurrentFrame。

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
