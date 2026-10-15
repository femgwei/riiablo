# A1–A5 AI 运行巡游（显式观察模式）

在既有 `headlessMapTour` 上增加 `-PtourAiObserve`，不改变默认地图巡游。
使用真实 1.10f MPQ、服务端 ECS、25Hz 固定帧和 TCP 协议；不调用原版 DLL，
也不把日志正常或地图 PASS 当作“与原版 AI 完全一致”。

## 流程与边界

- 沿用地图加载、本图移动、门/Warp、原生 seam 及真实战斗清障流程。
- 每张非城镇地图进入后，先观察默认 8000ms（200 个权威 tick），不发攻击请求。
  遇到每个 level/entity-ID 的首个堵路怪时，在接近后、清障前另观察一次；
  观察后重新采样，避免使用已经移动/消失的目标。该去重不是跨 ID 复用的单位身份保证。
- 观察期间持续接收 TCP；模拟不推进、玩家死亡或不可用均失败。不冻结怪物、
  不关闭碰撞，不注入怪物技能/任务，不通过传送绕过失败。
- 清障阶段仍使用原有百万血量、命中 100000、伤害 5000 的玩家夹具，避免把
  路线验收变成平衡测试。观察阶段无攻击请求，不代表后续清障也不会秒杀。
- 只覆盖巡游实际遇到并产生记录的单位。进入 132 图不等于遍历所有房间、怪物、
  Boss/任务触发、随机技能分支、低血量/尸体/召唤物/佣兵条件。未出现者未测试。
  真机动画、显示与原版逐帧对照仍需另验；已有定向 AI 回归仍需保留。
- 模式只允许 25Hz、难度 0/1/2，观察窗口 40..30000ms；禁止混用 object-only、
  seam-only、town-only 模式。可用 `-PtourLevels=2,3` 缩短诊断，不能当作全 Act 验收。

## 日志

- `map-tour.log`：保留地图通行证据，coverage 明确为 `AI_RUNTIME_OBSERVATION`。
  `AI_OBSERVE_BEGIN/END` 给出 level、观察原因、首末权威 tick；`playerAttacks=0`
  表示此观察窗口没有发送玩家攻击请求，不是统计怪物攻击次数。
- `ai-events.log`：直接捕获生产日志，含时间、来源类及权威 tick（非模拟线程为 -1）。
  显式打开 AI/Actioneer/ServerSkillSystem 的 INFO 及 AIStepper 的 DEBUG；
  原有日志输出仍保留，结束时恢复日志级别和 sink。`--verbose` 控制另一套
  LibGDX 日志，单独使用它不足以打开这些 AI 日志。另显式打开 MissileCollisionSystem
  INFO，沿 `MONSTER_MISSILE`/技能生成 → `MISSILE_HIT phase=collision/result/impact`
  检查飞弹实际命中与 `appliedDamage`；飞弹生成或 cast 记录不等于命中。
- `ai-summary.txt`：按 monster/实际 AI 汇总初始化、移动、决策、技能 cast 请求和
  近战 damage 路径的**日志记录数**。不当作精确动作次数、命中次数、击杀或覆盖率。
  `thinkSamples` 是 AI 更新后每 25 个 tick 的低频采样，不是全部 think 次数；
  `[MONSTER_AI_TICK]` 同时给出 level、状态、位置、寻路/施法组件，区分待机与未运行。
  `movementSamples` 统计相邻采样间大于 0.01 格的实际位移，补充直接调用 Pathfinder、
  不经过 `[MONSTER_MOVE]` 的旧 AI。仅有 APPROACH/WANDER 状态或 Pathfind 组件不算位移，
  新初始化会清掉旧 ID 的位置基线；它不是总移动距离、动作数或寻路正确性证明。
  `AI_TICK_ONLY_NO_ACTION` 表示采样到了更新但没有动作证据，不能满足动作最低门槛。
  `INIT_ONLY_NO_BEHAVIOR_EVIDENCE` 不代表 AI 正常；`BEHAVIOR_OBSERVED` 也不代表
  行为正确。`nativeCombatant` 仅来自 MonStats Align/killable/npc，不是实时阵营判定。
  新的初始化会覆盖旧 entity-ID 的归属，避免后续记录归入旧怪物。
- 原有 runner 的 `console.log` 保留 System.out/资源/异常等完整输出，
  `log-issues.json` 仍严格收集未审查告警。不能只看 `ai-events.log`。
- AI fallback、ERROR 或没有任何原生战斗怪物动作证据使 AI 模式进程失败，
  即使之前打印的地图通行 SUMMARY 是 PASS。
  正常关闭时写 summary；硬杀进程只保证之前已刷新的事件日志，不保证有 summary。
  `run=COMPLETED` 仅表示本次流程结束、未记录 fallback/ERROR，
  且至少一种原生战斗怪物有动作/位移记录；`correctness=NOT_ASSERTED` 明确没有原版
  行为正确性 oracle。告警还必须通过独立审计，不得忽略。

可优先检索 `[AI_FALLBACK]`、`[MONSTER_AI_INIT]`、`[MONSTER_MOVE]`、
`[MONSTER_MELEE]`、`[MONSTER_SKILL]`、`[MONSTER_PATH_STALLED]`、
`[MONSTER_ROOM_ACTIVE]`、`[MONSTER_ROOM_DORMANT]`，再按 entity、monster、tick
串联 decision → keyframe/技能分派 → damage/飞弹等证据。CAST/decision 本身不保证效果落地。

## 运行

先做小范围真实运行，输出使用未用过的新目录，不能覆盖旧证据：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:headlessMapTour -PtourAiObserve `
  -PtourLevels=8 -PtourSeed=1 -PtourDifficulty=0 -PtourAiObserveMillis=8000 `
  -PtourOutput=server/d2gs/build/ai-tour/your-new-smoke-directory `
  --no-daemon --max-workers=1 --console=plain
```

全 Act 单种子/多种子正式批次沿用现有脚本，自动保存完整控制台、源码版本与 MPQ hash。
脚本拒绝未提交源码和重复目录；默认普通难度，先普通再分别跑噩梦、地狱。
参数 `-AiObserveMillis` 缩短窗口也会减少技能分支被观察到的机会。

```powershell
.\tools\run-map-tour-seeds.ps1 -AiObserve -Seeds @(1) -Rate 25 `
  -Difficulty 0 -AiObserveMillis 8000 -Output server/d2gs/build/ai-tour/your-new-batch
```

不传 `-Seeds` 时沿用当前六种子矩阵。AI 模式额外增加每图及清障目标的等待时间，
完整矩阵可能很久，不自动将一次短巡游升级成长期六种子运行。

runner 除原地图验收外，调用 `Assert-AiTourEvidence`，独立检查非城镇观察窗口的
tick 数、禁止玩家攻击请求、正常结束且无 fallback/ERROR，以及至少一种原生战斗怪物的
实际行为记录；初始化或 NPC 移动不能满足这个最低门槛。它仍不是全怪物/全行为验收。
运行 `tools/test-ai-tour-audit.ps1` 验证审计会拒绝缺窗口、伪 tick、NPC-only、
init-only、错误/fallback 及中断等反例；原地图审计反例继续由
`tools/test-map-acceptance-audit.ps1` 验证。

## 2026-10-15 本轮验证

- 专服编译通过；MapTourAiLog/MapTourProbe/MapTourWalking 共 21 项、
  AIStepper/Act1NativeAiIntegration/BaalCrabAiIntegration 共 43 项，合计 64 项全过。
  覆盖默认不开启、低频采样不改变 update 次数、日志 sink/继承级别恢复、
  ID 复用、初始化/待机/位移证据分离、fallback/error 门槛和无效参数。
- 原地图独立审计反例通过；AI 审计正例及 11 个故障反例通过。
- 真实 1.10f、seed=1、25Hz、level=8、8000ms 的最终短巡游完成，入口观察
  200 个权威 tick，实际记录 zombie1 的 9 个位移采样；其余 Brute/Fallen/
  FallenShaman 仅有 AI 更新采样。该短路线没有攻击/技能/伤害记录，不能宣称战斗通过。
  输出 `server/d2gs/build/ai-tour/20261015-smoke-8-final`（本机忽略目录，不提交日志）。
  `Assert-AiTourEvidence` 通过，但完整控制台仍有 4 条 Attributes empty-base 告警；
  已保存在 `log-issues.json`，严格无告警验收**未通过**，不放宽审计规则。
- 早期 level=2 短路线只产生初始化，独立门槛应拒绝；随后增加进程级动作门槛。
  level=8 初版只看动作日志而漏掉旧 AI 直接寻路的位移，曾被正确标记为未完成；
  最终版本加入真实位置采样和反例后另建目录复验，没有覆盖早期失败证据。
- 未启动完整 132 图、六种子或三难度长矩阵；这轮交付的是可复用观察模式，
  不是 A1–A5 全怪物 AI 行为验收结论。

## 2026-10-15 本机复现与修复（续）

- 原版 1.10f MPQ、普通难度、seed=1、25Hz、8000ms 窗口，先跑连续
  level=2,3,4；输出 `server/d2gs/build/ai-tour/20261015-diagnostic-234`。
  路线完成，观察到 CorruptRogue/Fallen 近战实际伤害、萨满火球及复活链，
  不是只有入口初始化。完整诊断发现19条 empty-base 告警，严格验收失败。
- 临时调用栈定位（已移除诊断代码，保留原告警）发现两个实际清理错误：
  `CharData.softReset` 对玩家/佣兵只清 base 再 reset，留下旧 serialized property
  lists；`spawnNativeMapExplosion` 对 shamanexp 清 base 再 reset，每次碰撞告警。
  两处改用 `Attributes.clear()` 完整清理。火球父弹伤害不变，爆炸子弹仍保持
  空的权威伤害快照，不能回退到主人属性或重复施加火球伤害。
- 新开飞弹命中日志后，第二轮七图样本
  `server/d2gs/build/ai-tour/20261015-final-7levels` 发现 shamanexp 仍造成额外1点物理伤害：
  例如 tick=1422 shafire1 对玩家造成3点火伤，tick=1423 shamanexp 又扣1点。
  原版数据 `CollideType=0/Collision=false` 且三个服务端回调为0；D2MOO
  `MissMode.cpp::MISSMODE_HandleMissileCollision` 的0模式不做单位碰撞。
  空快照仍会落入通用伤害默认值，因此明确将这个已确认的纯表现子弹标记
  presentationOnly；不改变父弹、其他碰撞、怪物AI或测试玩家的生命值。
  扩展原回归后修复前实际测得玩家100→99；同时断言特效存活、正常到期及零伤害事件，
  防止通过直接删除特效或只检查空属性制造通过。
- 新增角色清理两项、萨满特效一项回归，修复前均实际失败；修复后通过。
  ShamanFireballAudit 原有四个动画链夹具缺少 AnimStepper 必需的 CofManager，
  已补注册真实系统，不跳过动画/伤害断言。角色新建/存档、资源刷新、火球、爆炸
  生命周期、AIStepper、Act1/Baal AI、MapTour 首轮104项定向回归全过；修复额外1点
  伤害后加入 MissileNativePolicy，最终122项全过（0失败/错误/跳过）。旧AI火球链
  原先要求至少两次伤害，改为等待完整64帧后严格断言一次父弹伤害，不能把重复伤害
  当作测试预期。
  AI审计正例/11个反例、原地图审计反例继续通过。
- 扩至 level=2,3,4,49,83,108,131，覆盖 A1–A5 的7张抽样地图；
  输出 `server/d2gs/build/ai-tour/20261015-fixed-234-49-83-108-131`。
  地图通行7/7完成，但 **AI验收失败**：11次 fallback（FingerMage 5、Megademon 4、
  BloodLord 2），另有 srvdofunc(101)、srvstfunc(53) 未支持、两次动态生成位置调整
  和 Goatman `GMDT2HS` 死亡COF查找告警；不能用地图 SUMMARY PASS 覆盖进程失败。
  两处 empty-base 告警在这个真实战斗样本中已消失。
- 以上三类缺失AI及技能需继续按 D2MOO 移植，不以 GenericMonster 替代品冒充完成；
  对应 `AiThink.cpp::AITHINK_Fn070_FingerMage/Fn089_Megademon/Fn125_BloodLord`，
  `SkillMonst.cpp::SKILLS_SrvDo101_FingerMageSpider/SKILLS_SrvSt53_MonInferno`。
  出生碰撞调整与死亡COF另需定向复现。未执行完整132图、六种子/三难度矩阵，
  也未证明抽样图所有怪物/技能条件分支正确。
- 最终修复后复测 level=2,3,4：
  `server/d2gs/build/ai-tour/20261015-verified-234`，3/3路线完成，入口均200个权威tick，
  2次真实地图过渡，0次分段跳转；AI summary 为 COMPLETED、fallback=0、ERROR=0。
  独立 `Assert-AiTourEvidence` 通过；记录15次 shafire1 接触和2条近战 damage 记录，
  shamanexp 单位接触为0，empty-base 告警为0。完整控制台审计仍有1条
  Goatman `GMDT2HS` 死亡COF告警，故严格无告警验收 **仍未通过**，没有改白名单。
  122项定向回归和所有审计反例也在最终源码上复验通过。

本次真实最终复测命令（输出需换全新目录）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:headlessMapTour -PtourAiObserve `
  '-PtourLevels=2,3,4' -PtourSeed=1 -PtourAiObserveMillis=8000 `
  -PtourOutput=server/d2gs/build/ai-tour/your-new-a1-retest `
  --no-daemon --max-workers=1 --console=plain
```

跨 Act 缺口复现将 levels 改成 `'2,3,4,49,83,108,131'`（PowerShell 下列表参数须加引号）。
同种子保证地图基线，不保证现有旧AI全局随机分支/实际动作记录数逐次相同；不能以
某次未触发告警或未遇到某怪代替确定性回归。
