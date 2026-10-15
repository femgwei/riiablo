# 怪物 AI 移植聊天接续（2026-10-15）

> 更新：本交接所指Do104已接续实现并完成本机验证。最新证据改读
> `docs/ai-arena/20261015-diab-prison-seed1/verification.md`：469项定向通过，
> 557行/1163场、49专属成功，107告警/376回退仍存，两次JSONL一致。
> 下文保留迁移时历史，不要重做Do104；下一项可审计Horror/Sentry MonSeq。

本文件保存 AI 聊天的最新关键上下文。用户要求保存后开启新聊天继续，下一项是
DiabPrison（SrvDo104 骨牢），不是重新统计或重做已完成的 DiabRun。

## 用户约定

- 工作目录 `F:\3rd_src\riiablo`，PowerShell，分支 master；完成代码修改后必须 commit 并
  push `origin/master`，用户在另一台电脑验证。极小修改不必编译；实质 AI/技能改动按风险测试。
- 暗黑原版逻辑优先查 `F:\3rd_src\D2MOO\source`；仍不明确可搜索 The Phrozen Keep。
  不只凭经验回答。难点可分析原版 EXE/DLL：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
  真实测试显式设置 `$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'`。
- 不隐藏/降级 WARN，不放宽 oracle，不修改原表概率来通过测试；不注入 SkillDo、不强制技能、
  不关闭碰撞或传送以制造成功。保留初失败、修后及复跑的独立证据，不覆盖旧输出目录。
- 进度和测试结论要诚实：观察到动作不等于完整 AI 正确性 PASS，定向测试不是全项目通过，
  headless 不是另一台电脑的真实 TCP/GL 视觉验收。历史章节的“下一步”按最新记录解释。
- 用 apply_patch 编辑。保留其他任务的脏文件/提交，不 git reset/checkout 覆盖、不混入提交。
  当前无用户要求派子代理，不主动启动子代理。

## 最新已完成并推送

提交 `7b5bf0bbebb756616a77ef6c0e8fa14ce7fcab50`：
`fix(ai): port DiabRun sequence movement and direct hit`，已核对 origin/master 同 hash。
前一 AI 提交 `df8eda01f` 为 DiabWall；更早包含 DiabCold 和 Diablo 电光链。

DiabRun Skills198 / St54 / Do103：

- 对照 D2MOO HEAD `8322494ed1f715ad51552f169df76cf600fabc71` 及本机1.10f原表。
  St54保存目标身份与起手整格坐标，清单位追踪；真实 seq_diablorun/RN，不走XX→A1。
- 22点序列：5启动坐标A*，13回卷6；路径结束进入14..21收尾；18动作点仅对原目标当前
  近战距离施加一次技能纯物理53..69（上界排除），不取武器、不掷AR/block、不生成飞弹。
- opt-in最近可达A*保留动态/静态碰撞及房间约束，普通exact搜索不变。原版无targetUnit时
  不做target-room预检，目标不可达时flush best节点。项目heuristic/tie/预算/平滑仍非逐位移植。
- 新 native fixed physical 入口仅Do103使用：先PDR后抗性、玩家物抗50cap、怪100免疫、
  负抗最低-100、允许伤害0。保留旧共有入口行为。正常序列256时钟，基准23.4375 subtile/s。
- 覆盖目标移走/移除、静态屏障、冻结、死亡/取消清路径、池复用，本地RN动画跟随源帧。
  共有速度状态、整数伤害、ECS id及近战距离投影等边界见验证文档。

## 最终证据与当前进度

优先读：

1. `docs/ai-arena/20261015-diab-run-seed1/verification.md`（原版依据、完整测试范围/命令、边界）。
2. `docs/monster-ai-arena.md` 顶部、`docs/d2moo-progress-roadmap.md` 最新 AI 章节。
3. `server/d2gs/src/main/java/com/riiablo/server/d2gs/MonsterAiArena.java` 和相应测试。

最新快照 `docs/ai-arena/20261015-diab-run-seed1/`：report.html、results.jsonl、inventory/summary、
COMPLETED、logs.zip（1161场）、regression-final.zip（core/d2gs分目录57个XML）、
repeat-results.zip、initial-diagnostic.zip（初地火未触发证据）。

- 最终460项定向测试通过：core426/54suite，d2gs34/3suite，失败/错误/跳过0。
- 全目录557行/1161场，Normal/seed1/每场500个25Hz tick：490 ACTION、376 AI_FALLBACK、
  109 WARNING、175 NO_ACTION、7 MOVEMENT_ONLY、4 TERMINAL；异常/ERROR/NOT_RUN/专属未触发0。
- 47场专属全部成功：18复活、6触手、3蛙魔、10持续火焰/电光、2冰击、2地火、2冲刺、4无主人。
- 对旧1159场：1155全字段相同，仅diablo/diabloclone的far与diab-wall共4场变化，新增2冲刺。
- 两次最终全目录JSONL SHA256一致：
  `CDD0149DDEE66C6BF6C71B73B5FB2BF467AE7EFF94937A910DA0DED621E95C52`。
  build输出 `server/d2gs/build/monster-ai-arena/20261015-diab-run-full-final` 和 `20261015-diab-run-repeat`。
- 冲刺两场均tick1/7/29/33完成四阶段，实际56物理扣血。地火初静止角色场因冲刺接近不再施放；
  已记录失败，只在diab-wall fixture加正常角色Pathfinder后撤16恢复远距机会，不强制怪物技能，
  不放宽旧四阶段oracle。地火两场仍真实Do104告警，不能说整个Diablo技能链完成。
- 47个AI名称/188行fallback尚未解决；完整Fn051、Do104、Horror/Sentry MonSeq、完整Fn065、
  Megademon专用AI、全局精度/事件、三难度多seed、真实TCP/GL仍待。
- 旧失败 `BaalCrabSummonTargetLifecycleIntegrationTest.
  deadWallAndRetainedCursesNeedNewNodeInsertionBeforeNode9Fallback` line317 expected2 actual0
  已在未改18a5cc7b复现，证据 `docs/ai-arena/20261015-inferno-seed1/baseline-known-failure.xml`；
  本轮未纳入/未修复，不宣称全项目通过。

## 共享工作区：必须重新检查

本轮AI代码已完整提交；交接创建时仍有并行地图/其他技能任务未提交修改，不属于本AI聊天，
包括 D2MOO_JAVA drlg六文件、Actioneer、ServerSkillSystem、StateUpdater、Missile、
AssassinMartialArtsTest、MissileNativePolicyTest、NecromancerGolemSideEffectTest、
ShamanFireballAuditTest、DrlgUpstreamParityTest，以及 current-chat-ownership/roadmap/
dark-magic-port-plan/d2moo-upstream-port 等文档。列表可能随并行任务变化，以git状态为准。

ServerSkillSystem本轮曾按hunk仅提交DiabRun分支和applyDiabRun，保留了其他任务的Do085
resolveMonsterChainMissile修改。后续Actioneer/ServerSkillSystem可能重叠，先看diff，只改所需范围。
测试在共享工作区执行，包含当时可见的并行修改，不能称提交后的独立worktree验收。
不要为了清理状态stage所有文件；必要时按hunk暂存，确认cached diff。旧大索引文档包含其他
模块的历史状态，不要据此夺取并行任务或重做其工作。

## 新聊天继续执行

1. 先读本文件和最新verification，检查git status/log/diff，确认没有正在运行且禁止覆盖的测试JAR。
2. 继续下一个失败项 DiabPrison Do104：先读 D2MOO SkillMonst.cpp::SKILLS_SrvDo104_DiabPrison
   （0x6FD09890）及其召唤/骨牢生命周期、原表消费者与MonStats槽位，不凭名称猜行为。
   本轮尚未进行Do104实现或完整源逻辑审计，不假设既有玩家Bone Prison可以直接复用。
3. 基于真实源逻辑补生产链、正式数据/集成测试和自然AI专属场，覆盖其辅助怪/生命周期；
   继续严格oracle与保留警告，不以“有实体/有动作”代替伤害/碰撞/结束等实际行为验证。
4. 跑相邻定向回归及全目录，按monster/scenario逐字段比本轮1161场，新增场单独统计；
   复跑验证确定性。用新输出目录归档报告、完整日志、最终XML和verification。
5. 更新AI进度文档，只提交本轮范围并push origin/master，核对远程hash，中文汇报实际结果。

仅要重跑全目录时（目录必须不存在）：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
 -ParenaOutput=server/d2gs/build/monster-ai-arena/my-next-full --max-workers=1 --console=plain
```

仅定向两行可加 `'-ParenaMonsters=diablo,diabloclone'`，其余NOT_RUN，不是全目录验收。
本聊天在保存/迁移时不再修改生产代码或启动长测试；后续工作由新聊天接续。
