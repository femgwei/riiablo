# D2MOO增量同步续聊交接（2026-10-16）

## 用户目标与范围纠正（最高优先级）

用户只要求同步D2MOO最新更新，不要求完整替换riiablo生产寻路。
此前50轮把上游增量修正扩展成完整原生寻路迁移，范围过大；已向用户说明并纠正。
暂停完整寻路替换、额外快照加固、全套fp16/Act/seed接线，不再把生产仍A*视为增量同步未完成的理由。
旧docs/path-port-handoff-2026-10-16.md及寻路文档保留为历史，不据其“下一轮”指令自动重启扩展。

## 上游真实范围

- 官方F:/3rd_src/D2MOO，origin=https://github.com/ThePhrozenKeep/D2MOO.git。
- 已实际fetch --no-recurse-submodules，master仍0c0bbd1e6bef9181a3da1c046523af622a925416（10月13日）。
- 历史比较起点8322494ed1f715ad51552f169df76cf600fabc71，158提交含merge，101个非merge。
  Path目录只有8个非merge更新，含注释/版本条件，不是几十套新算法。
- 用户截图指出10月16日分支更新，不能只看master：
  origin/test_utilities=8390bfe02c63ed2d8a5c2719e79eb3764f0ae217，主线外8提交；
  origin/visitor_framework=14110b37faa289f9f25011c9794cbfe10791f815，主线外17提交，包含前8再加9。
- 这17项是C++测试工具/对象访问与差异比较，无游戏寻路算法更新；纳入同步适用性审查。
- D2MOO本地HEAD现为0c0bbd1，external/D2.Detours有既有修改，不覆盖、不reset。
  读取上游用git show origin/<branch>:path，避免混入本地内容。

## 当前进度与证据

权威剩余清单：docs/d2moo-upstream-remaining-2026-10-16.md。
含主线101项索引、待核销分组及新分支适用性；尚无可信总完成率。
状态必须区分已移植、现有实现等价、不适用、待修复，并关联代码/版本/测试，不能把无证据当代码缺失。

最新本聊天代码提交d63a1d2fa35ac16dab5c1adf0bd21a113fa4286d，已推送并核对远端一致。
本批同步：
- aec1395 bin读取：core/src/test/java/com/d2moo/testutil/UpstreamTestUtilities.java。
  工作目录/data/global/excel/filename，int32小端count，显式recordSize，返回只读小端记录buffer，允许尾随数据。
  缺文件/头截断/负count/乘积超出实际数据拒绝，是Java宿主安全扩展，不冒充上游未检查stream状态行为。
- da15a17 uint32随机fixture：long表示闭区间，拒绝采样去除模偏差，调用者提供Random以便复现。
  不是D2Seed，不保证上游mt19937相同序列，不用于游戏逻辑。
- 定向4项/1类通过，失败/错误/跳过0；core/build/upstream-tools-01.log，
  XML .gradle/path-port/upstream-tools-01/core/test-results/pathPortTest。
- docs/path-port-regression.init.gradle隔离编译清单已加入com/d2moo/testutil/**。
- 01c64d8/8bc96f2/0fadad7原生函数地址配对/inline hook/DLL基址工具：当前Java测试后端不能直接移植，
  不创建假DLL地址调用；如要实际DLL差分另立明确原生桥工作。
- 017e8ce空NoOpFixture为C++ mixin终止类型，JUnit无需同构空类；2dd609b/8390bfe为CMake/header装配。

## VisitorFramework第二批已完成适用性审查

已读取下述九项全部最终源码，Java测试适配为
`core/src/test/java/com/d2moo/testutil/ObjectDifferences.java`，新增五项测试。
与第一批合计九项/两类通过；日志 `core/build/upstream-visitor-01.log`，
XML `.gradle/path-port/upstream-visitor-01/core/test-results/pathPortTest`。
逐提交归类和Java差异见权威剩余清单第二批章节。
身份对去重是安全改进，不复制单侧指针去重；无原DLL调用，无生产逻辑变更。

主线数据组 `185ae45` / `4d45674` / `606dce4` 已完成本批适用性审查：
MonStats String空值及Accel有符号int与增量修正等价，原生固定4096位图越界不适用当前结构。
新增 `UpstreamDataBoundaryTest` 四项通过（失败/错误/跳过0），包括实际1.10f MPQ与运行时逐行加载比对；
日志 `core/build/upstream-data-01.log`，XML `.gradle/path-port/upstream-data-01/core/test-results/pathPortTest`。
生产没有读取Accel，也没有game级唯一掉落去重/nolimit检查，这是保留的能力缺口，不能误报行为等价。
代码/调用依据详见剩余清单第1组。没有为字段符号修正新建完整加速度或掉落系统。
后续已核销 `615b204` / `d68bf6a` / `f553315`：原版报文选择/字段offset/包长修正
不适用当前FlatBuffers EntitySync协议；已核对两个NetworkSynchronizer、SerializationManager、
Position/Velocity/Vitals Serializer、Sync.fbs、StatRequest.fbs及ClientNetworkReceiver收发链，
并搜索core/src/main、server、D2MOO_JAVA/src无对应原版报文结构。详见清单第2/6组。
本批只改审查记录，没有生产代码变更，故未重复运行上一批测试。
当前主线已核销8/101；剩余93项待逐项核销；分支17项适用性审查已完成；
另保留2项能力缺口（加速度执行/game级唯一掉落去重）。用户要求每完成一项汇报剩余工作量。
`5be5503`已修复：生产`party/CombatOwnership`保留源方一层（含missile）、
目标monster owner链迭代，读取minionOwnerId/merc/summon owner，缺owner/自指终止，畸形环拒绝。
已接线Actioneer、MissileCollisionSystem、ServerSkillSystem、StateUpdater、AuraEcsSystem、AI目标资格；
现有Conversion专用规则保留。普通无owner目标不分配visited集合。
新增`CombatOwnershipTest`五项，反射调用真实生产关系入口/AI资格，实体与mapper来自真实ECS，
另跑PvP八项/诅咒十八项，共31项通过，失败/错误/跳过0；非完整战斗tick/非原DLL差分。
日志`core/build/upstream-owner-01.log`，XML `.gradle/path-port/upstream-owner-01/core/test-results/pathPortTest`。
其他聊天的Trap-Melee/Missile/Nova工厂注册及相关文件已由其独立提交，勿覆盖；本轮仅修改AI目标资格相关hunk。
`b634ef7`已移植：NativeTargetNode.ALLIED表示独立slot8，普通AI扫描纳入显式成员，
同距player优先/slot8头插入优先、严格aiDist边界、同Act检查，CombatOwnership承认该成员的友军阵营。
ServerEntityFactory对真实NpcBarb行登记；未把所有友军/NPC一概加入，未扩展Wussie救援AI或rogue2生成器。
Slot8FactoryTest验证实际1.10f行经生产工厂分配；Slot8TargetTest验证扫描/顺序/脱离/尸体/距离边界。
加已有owner/PvP/player slots，共25项/五类通过，失败/错误/跳过0；
日志`core/build/upstream-slot8-01.log`，XML `.gradle/path-port/upstream-slot8-01/core/test-results/pathPortTest`。
本批目标扫描增量不改Baal专用评分，不声称完整原生视觉/寻路/NPC AI迁移。
下一步核销 `cce700b`（diff已读），再进入动画速度四项。
追踪Java生产owner链和target slots、summoner状态消息。只修确认差异，不扩展完整寻路迁移。

## VisitorFramework九项源码索引（已读）

已审查上游源码并移植必要Java语义，没有机械翻译模板和宏：
6288354 VisitorFramework module；586fd95 DifferenceType；5baef9d DifferenceDescriptor；
2a21c28 DiffContext；a53f818 VisitorTraits；1842788 FieldVisitor；9ca6c7f DiffVisitor；
ae3c0f1 TestComparator；14110b3 Macros。
上游source/VisitorFramework/include及src，git show origin/visitor_framework:...读取。
特别核对对象图重复引用/环、数组/指针差异、浮点严格小于0.0001、char数组strcmp等语义；
不要自动使用宽容比较替换原本需要bit精确的游戏回归，不宣称生成对比器等于执行原DLL。
之后回主线剩余清单逐项核销数据signed、目标/动画、技能事件、任务/交易、AI、网络/CRC等。
1.11+/Ubers/C++工具链按1.10f/Java适用性分类；含混标题/混合提交必须读diff。

## 共享工作区与交付约束

- 工作目录F:/3rd_src/riiablo，PowerShell，master；不创建新worktree，除非任务确需。
- 交接时其他聊天未提交：core/src/main/java/com/riiablo/engine/server/StateUpdater.java，
  core/src/test/java/com/riiablo/engine/server/NecromancerCurseIntegrationTest.java，
  docs/ai-arena/20261016-iron-maiden-overkill/。这些会继续变化，先查git status，不动它们。
- 严禁git add .或reset他人修改；只暂存自己文件/补丁，核对cached stat/check后commit/push并查ls-remote。
- 代码用apply_patch修改；不主动派子代理。无需每轮扩大全项目测试，按本轮真实变更范围验证。
- 不把626项旧寻路回归等同主线101/分支17更新完成率。
- 用户希望连续推进、简洁中文报告，约60秒进度更新；无需重复询问已经授权的同步动作。

## 可复用测试命令

PowerShell设置D2_HOME及D2_TEST为G:/BaiduNetdiskDownload/Diablo II 1.10F。
gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=<独立标签>
  -PpathTestScope=path --tests 'com.d2moo.testutil.*' --no-build-cache --no-daemon --max-workers=1 --console=plain
输出重定向到core/build/<标签>.log；每轮不同标签隔离编译/报告，避免其他聊天并发干扰。
新增测试包时更新隔离include，不要因包被排除误报0测试通过。
