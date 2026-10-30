# AI 依赖第四阶段复跑

第四阶段仍为 PARTIAL。此入口采集固定版本的局部自然地图运行与 GL 截图，不能把它
作为全部 AI 原版一致性、五幕整链或跨电脑视觉验收的 PASS。

从仓库根目录在 PowerShell 运行，Output 必须是尚不存在的目录：

```powershell
./tools/run-ai-dependency-acceptance.ps1 `
  -D2Home 'G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  -Output 'F:\ai-evidence\machine-a' `
  -Revision '<待验证提交完整SHA>' -Seed 1
```

脚本从指定 Git 提交导出 source.zip，在独立 source 目录编译运行，不会把共享工作区
未提交改动混入基线。需要 Git、可运行 Gradle 的 JDK、1.10F MPQ 及可创建 LWJGL2
上下文的显卡驱动；不包含或复制游戏资源。日志、临时角色存档和编译输出均在 Output 下。

- acceptance.json：提交、源码包 SHA256、MPQ 名称/大小/SHA256、机器/JDK、参数与各阶段状态。
- natural-console.log、natural/map-tour.log、natural/ai-summary.txt：seed=1、25Hz、
  地图1/2及非城镇入口200 tick的自然AI观察。审计拒绝观察窗口不足、无行为证据、
  fallback、未审查告警、死亡恢复或未完成巡图。观察成功仍为 OBSERVED_RUNTIME_ONLY。
  如果入口窗口尚无实际怪物行为，使用正常TCP移动走近最多8个已可见的同地图活体敌人，
  从生产共享碰撞网格规划至约6子格外，路线长度限制128子格。不传送、不生成怪物、
  不改AI状态、不发送攻击或调用战斗清障；动态阻挡则停止并尝试下一候选。到达后再
  观察200 tick。入口与接敌窗口都执行相同的无攻击、完整tick与唯一配对审计，
  未遇到可达敌人或仍无实际动作时继续失败，不将“思考过”放宽为通过。
- gl-console.log、gl/rogue-encampment-smoke.png、gl/rogue-encampment-manifest.txt：
  生产 GameScreen 在地图2的854×480截图，以及 GL vendor/renderer/version。
  GL使用专用CampSmoke角色的固定地图种子0x110FCA4D，与TCP观察的Seed参数分开记录。
  完整截图等待角色有可绘制GPU图层、无待加载资源且连续三帧稳定；清单记录实际地图
  种子、角色可绘制图层数和稳定帧数。
  CAPTURED_REQUIRES_VISUAL_REVIEW 只表示已采集；静态截图不能证明整段动作、音效或战斗正确。

自然地图检查失败后仍尝试 GL，全部证据保留；任一检查失败时清单result=FAILED且
脚本最终抛错，两项都完成也只记PARTIAL。原有1×1冒烟默认
尺寸保持不变，可单独用 :desktop:offscreenCamp 的 offscreenWidth/offscreenHeight 属性
调整截图尺寸。

在另一台电脑检出相同提交、安装同版本资源后复跑，并传入第一台的 acceptance.json：

```powershell
./tools/run-ai-dependency-acceptance.ps1 `
  -D2Home 'D:\Games\Diablo II 1.10F' `
  -Output 'D:\ai-evidence\machine-b' `
  -Revision '<相同提交完整SHA>' -Seed 1 `
  -CompareWith 'D:\ai-evidence\machine-a-acceptance.json'
```

比较只检查源码/资源/参数指纹及不同主机名，不会把相同输入认作相同结果，更不会自动
宣布跨电脑通过。需要同时复核两台日志和截图；主机名也不是硬件身份的证明。
第二、三阶段遗留、全部自然路线、门/接缝/特殊技能、死亡复活、规模性能以及与原版的
动态画面对照仍需补验。
# 多种子自然路线自动观察

可使用 `tools/run-ai-natural-matrix.ps1 -D2Home <1.10f目录> -Output <新目录>`
依次运行 seed 1、2、3；用 `-Seeds` 指定唯一的种子列表。所有运行固定到同一提交，
每个种子分别保存源码快照、资源哈希、TCP 日志和验收清单，根目录 `matrix.json`
汇总状态、实际路线 SUMMARY 和失败原因。任一种子失败时，继续收集其他种子证据，
最后以失败结束。矩阵跳过 GL，单次完整入口仍默认运行 GL。

当前矩阵只覆盖 level 1/2 的自然路线变化。成功状态为 `OBSERVED_RUNTIME_ONLY`，
不能推导门、窄道、全部技能、全部怪物资源、性能或原版一致性已验收。

2026-10-30 使用新矩阵脚本对源码提交 `c157ccf6a` 运行 seed 2，证据为
`build/ai-matrix-seed2/matrix.json`。结果 `visited=2/2`、`walks=4`、
`transitions=1`、`detachedJumps=0`、`failures=0`，状态 `OBSERVED_RUNTIME_ONLY`。
这是新增种子的单行实测。

随后在提交 `d2ee7df1c` 上完成默认 seed 1/2/3 全矩阵，证据目录为
`build/ai-matrix-three-seeds`。seed 1、2 状态为 `OBSERVED_RUNTIME_ONLY`；
seed 3 路线同样完成（visited=2/2、walks=4、transitions=1、failures=0），
但两条 `AnimDataResolver` 的 `AMBL2HT` 到 `AMGH2HT` COF 回退警告被审计拒绝，
因此总矩阵为 `FAILED`。未放宽资源回退审计；该资源问题仍待修复。

后续定位到生成近战存档的手部物品替换问题：`unequipItem` 仅移除装备槽映射，
旧初始物品仍以 EQUIPPED 写入 D2S；重读后旧盾牌恢复，造成双手矛与持盾上下文并存。
提交 `c4c222063` 改为移除被替换的初始手部物品。新快照 seed 3 在
`build/ai-seed3-starter-fix` 复跑通过，路线 SUMMARY failures=0，资源审计无问题，
状态 `OBSERVED_RUNTIME_ONLY`。原三种子失败证据保留；修复后全三种子尚未重跑。
另有 `b5f7c058b` 修正未知装备上下文不应默认持盾，并通过 CombatSystemTest。

2026-10-30 在 `81e7506b1` 完成修复后的 seed 1/2/3 验证：seed 1 证据位于
`build/ai-matrix-equipment-roundtrip/seed-1`；磁盘空间耗尽后保留该成功结果，释放空间后
在 `build/ai-equipment-resume-23` 重跑 seed 2、3。三份 manifest 的提交和源码哈希一致，
每项均包含唯一的 `melee_save_roundtrip weapon=spr leftArm=empty result=PASS` 记录，
即真实生成 D2S 后重读校验右手武器和空左手。三项均为 `OBSERVED_RUNTIME_ONLY`，
visited=2/2、walks=4、transitions=1、detachedJumps=0、failures=0，严格资源审计通过。
seed 1 combatKills=1，seed 2/3 为 0。合并索引为
`build/ai-equipment-resume-23/combined-three-seeds.json`。磁盘故障时的失败文件未改写。
这完成了 level 1/2 的三种子观察与装备存档读写回归，仍不代表门、窄道、完整技能、
全部资源、性能或原版一致性已验收；人工视觉/听感和跨电脑为外部验证项。


2026-10-30 验收入口与自然矩阵新增 `-Levels '10,14'` 参数（默认仍为 `1,2`）。
楼层必须是 1..132 内的唯一 ID；清单、跨主机输入比较和 Gradle 调用均记录同一楼层列表，
验收还核对实际 LOAD 的完整顺序及对应 planned/visited 数量。

地下通道 seed 1 首轮使用源码 `81e7506b1`，证据位于 `build/ai-underground-10-14`。
该轮达到 600 秒阶段上限，矩阵为 FAILED；仅实际加载 level 10，未加载 level 14，
没有最终 SUMMARY 或 DOOR_PASS，不能计为地下路线、门或窄道验收通过。
日志保留了真实遇怪观察、清障与路线重规划过程。超时前仍在处理出口方向的怪物阻挡，
本轮不足以判断扩大时间预算后能否完成。

该失败同时暴露阶段超时发生在状态捕获之外，导致单次 acceptance.json 中 natural
仍为 NOT_RUN。现将自然路线及 GL 的阶段调用纳入各自失败捕获，超时会记录 FAILED，
自然路线失败后仍按配置尝试 GL。旧失败证据保持原样；使用脚本中的实际 try/catch
注入超时异常验证，两阶段均正确记录 FAILED。两个脚本语法检查、非法楼层拒绝检查
及 diff 检查通过。下一步应给地下长路线安排明确的更长时间预算后重跑，仍保留严格
资源审计；门交互需另选有实际门事件的路线。

长路线可通过矩阵入口传入 `-StageTimeoutSeconds 1800`，逐项转交给单次验收入口；
默认仍为 600 秒。两入口拒绝 1..86400 秒之外的时间，矩阵和单次清单均保存
`stageTimeoutSeconds`，便于识别每轮的运行预算。该值是每个 Gradle 阶段的超时上限，
不包含源码导出和资源哈希准备时间，也不替代路线、观察窗口及资源审计。

2026-10-30 使用 1800 秒阶段预算完成地下通道 seed 1 重跑，源码提交
`de1b2169f`，证据位于 `build/ai-underground-10-14-long`。矩阵状态为
`OBSERVED_RUNTIME_ONLY`；单次总验收仍为 PARTIAL（本轮跳过 GL，且没有原版判定器）。
实际 LOAD 顺序为 10、14，`TRANSITION_PASS from=10 to=14 mode=WARP_WALK`
记录 `warpRequests=1 movement=PRODUCTION_TCP`。SUMMARY 为 visited=2/2、walks=3、
transitions=1、detachedJumps=0、combatKills=19、failures=0。walks 是最终汇总口径，
不等同于逐段 WALK_PASS 的数量。

两层入口均完成 200 tick 无玩家攻击观察窗口；重新执行严格证据及控制台审计通过，
warnings=0、errors=0、fallbackRecords=0、issues=0。CorruptRogue、Fallen、
FallenShaman 和 Bighead 有运行行为记录，其余仅初始化记录不计作行为覆盖。
本轮自然阶段约 449.5 秒完成，早于旧的 600 秒上限；不能把本次成功单独归因于
扩大时间预算，也不能据此认定之前超时的根因已解决。原失败证据保留。
本轮 DOOR_PASS=0，只补充该地下路线的单种子观察，不代表门、所有窄道或全部 AI
已验收。下一步应选择有实际门交互的路线补验。

2026-10-30 补门交互时，首轮误将 20/21 当成监牢；实际 LOAD 为 Forgotten Tower /
Tower Cellar Level 1。证据目录 `build/ai-doors-jail-20-21` 的目录名不代表实际地图，
只计作 20→21 普通路线观察：visited=2/2、transitions=1、failures=0、DOOR_PASS=0。
后续依据 `D2LevelIds` 常量改为监牢 29→30。

监牢首轮使用 `15cd68d28`，证据位于 `build/ai-doors-jail-29-30`。虽然路线 SUMMARY
为 PASS，且 level 29 的真实原生门 entity 137/149/148（class 14/13/14）均记录
`clientMode=ON animationComplete=true`，最终转换到 level 30，但控制台两条
`Unsupported srvdofunc(93)` 使严格验收及矩阵为 FAILED；未加入警告豁免。

对照 D2MOO `SKILLS_SrvDo093_GargoyleTrap`，提交 `09e9cfa56` 补齐石像鬼陷阱的
原生服务端发射：必须有目标单位，次要瞄准轴最多四格，起点使用原生整数除法偏移，
从 srvMissileA 创建一个有归属的弹体并初始化技能伤害；Actioneer 明确委托该处理，
避免在实际服务端处理前误报 unsupported。发射集成测试 2 项覆盖六组正负方向/主轴/
相等轴/不足六格的几何，以及缺失目标拒绝，第一幕原生 AI 集成测试 29 项通过。

修复后的同种子监牢重跑证据位于 `build/ai-doors-jail-29-30-fixed`，源码固定为
`09e9cfa56`，自然阶段约 147.7 秒。实际 LOAD 顺序 29、30；三个 DOOR_PASS 仍对应
entity 137/149/148，后续生产 TCP 路线完成 `TRANSITION_PASS from=29 to=30`。
SUMMARY 为 visited=2/2、walks=3、transitions=1、detachedJumps=0、combatKills=0、
failures=0。控制台两条 GARGOYLE_TRAP 记录证明修复逻辑实际触发：skill=172、
row=24、level=1，分别记录实际发射起点与瞄准向量。独立重跑观察窗口和控制台审计，
warnings=0、errors=0、fallbackRecords=0、issues=0，矩阵为 OBSERVED_RUNTIME_ONLY。
单次总验收仍为 PARTIAL；旧失败未改写，未增加任何警告豁免。

该样本证明监牢原生门的网络开门、动画完成与后续路线推进；DOOR_PASS 本身不是
门平面穿越几何判定，也不证明全部门类型、关门/重连持久化、所有窄道或原版一致性。
下一步可补窄道的明确几何/移动证据，或扩展其他门类型；人工视觉、听感和跨电脑
验证继续留作外部验证项。

2026-10-30 新增窄道只读采样及 `tools/audit-ai-narrow-movement.ps1`。在自然地形上，
以生产 PLAYER 路径碰撞掩码及实际角色脚印检查采样中心，沿 X/Y/两条对角轴寻找
正负方向三格内的首个阻挡，记录其间可用脚印中心数量（1..5）。这是截面格点计数，
不是墙间物理宽度；对角采样一步为对角格距，不与轴向物理距离等同。越出楼层的边界
不算墙，动态单位不代替地形/物体阻挡。两个连续权威样本必须同层、同脚印、未阻挡、
tick 增量 1..10、沿截面的垂直方向至少移动 1 格、横移不超过 .75，且位移不超速度
允许范围，才能记录 NARROW_MOVE。日志只表示采样点附近的局部进展，不证明采样间
整段轨迹或整条走廊几何。

独立审计要求自然路线及严格控制台审计已通过，校验 NARROW_MOVE 的格式、tick、
投影及截面计数，并只保留同 sequence/level 后续出现 WALK_PASS 的样本。调用：
`./tools/audit-ai-narrow-movement.ps1 -Run <某次 seed 目录>`，成功时输出 JSON，
没有合格样本则抛错。审计 JSON 标记 SAMPLED_LOCAL_NARROW_MOVEMENT、NOT_ASSERTED。
旧日志缺少采样不会补推为通过。合成正例和过期 tick/错误截面方向/未配对行走反例
检查通过；这些审计检查本身不是地图实测证据。MapTourProbeTest 共 13 项通过。

仅轴向版本 `c89adf50b` 的 `build/ai-narrow-jail-29-30`，以及加入对角投影版本
`87a7c8fb4` 的 `build/ai-narrow-jail-diagonal` 均完成普通监牢路线观察，但
NARROW_MOVE=0，窄道审计正确拒绝，不计为窄道覆盖。未扩大阈值追求阳性结果。

同一提交 `87a7c8fb4`、seed 1 改跑蛆虫巢穴 62→63，证据目录
`build/ai-narrow-maggot-62-63`。普通路线 visited=2/2、walks=3、transitions=1、
detachedJumps=0、combatKills=23、failures=0，记录生产 TCP 的 62→63 WARP_WALK。
自然阶段约 524.2 秒，warnings=0、errors=0、fallbackRecords=0，严格控制台审计通过，
矩阵为 OBSERVED_RUNTIME_ONLY，单次总验收仍为 PARTIAL。

窄道日志记录 95 条 NARROW_MOVE，独立审计将其中 90 条与后续成功行走配对，涉及
level 62 的 30 个不同 sequence；5 条未与成功行走配对而未计入。截面方向为 X 54、
Y 32、DPLUS 1、DMINUS 3；这是采样记录数，不是独立通道数量或覆盖率。level 63
没有合格窄道样本，不扩推为两层均覆盖。审计结果保存于该目录 `narrow-evidence.json`。
例如 sequence 6、tick 369→374，footprint=2，两端沿 Y 截面的可用脚印中心跨度
均为 2，角色由 (12669.449,21.989368) 移至 (12666.637,21.99609)，随后同请求
WALK_PASS。此项补齐了有明确截面及实际行走关联的单种子局部窄道证据，仍不代表
连续全轨迹、所有窄道、怪物通过相同窄道或原版一致性已验证。

2026-10-30 按建议补充怪物窄道追击/阻挡恢复回归。提交 `f3a2af823` 在
Pathfinder 的动态阻挡恢复中增加有限的“队列前沿”接近：仅对真实 Monster 的
实体目标生效，先用无动态占用的地形路径确认源点到目标仍连通，再在动态碰撞快照
下生成部分接近路径；保留目标实体和 NativePathIdentity，技能施法路径不走此恢复。
这避免单文件窄道中队首占据接近环时，队尾永远停在原地，同时不穿越地形或动态单位。

新增回归覆盖：size=3 怪物沿三格通道转弯、目标移动到转弯臂后持续追击、两只追击者
在动态堵塞处停下并在移除阻挡后恢复。第一次测试暴露队尾没有恢复，日志中的最终
位置为 follower=(6,10)；修复后 PathfinderTerrainPursuitTest 及相关路径回归通过，
真实 Skeleton AI 决策场景也通过，并验证恢复后仍携带目标实体身份。测试场景是受控
地形/ECS，不等价于自然地图所有怪物和所有窄道的运行覆盖。

2026-10-30 按建议执行窄道特殊技能链回归。聚焦测试通过（总构建 23 项任务成功）：
Act1MonsterEcsScenario、MonsterSkillStart、TrapMissileAi、SpecialMissileController、
RemainingMonsterSkill，以及 MonsterNarrowPursuit/MonsterNarrowDecision 场景均通过。
覆盖 Fallen Shaman 的 ShamanFire 服务端弹体、Gargoyle Trap 原生发射、技能启动、
特殊弹体控制器、窄道追击与阻挡恢复；测试继续检查弹体归属、技能 ID、目标身份、
生产碰撞和路径恢复。没有加入音效听感判定。

同一批次若额外包含 MonsterMissileSourceIntegrationTest，已有 13 个 source-channel
断言失败（共 71 项），失败集中在物理/元素/毒伤固定点值和吸收快照；这些测试与本次
窄道特殊技能改动无交集，当前工作区没有修改其实现，故未把这批次标成整体通过。
聚焦重跑排除了该既有失败组并通过。需要另开独立任务定位 source-channel 基线变化，
不能以本次技能链结果掩盖它。

2026-10-30 继续定位并关闭上述 source-channel 13 个失败。根因为测试夹具仍替换
MathUtils.random 来固定掷值，而 CombatSystem 默认已使用 combat.compatibility
独立随机流。共享 MonsterMissilePhysicalIntegrationTest.Fixture 现显式绑定测试
CombatSystem 随机源，保留原先上界前一值的算术断言及 miss 用例的后续覆盖。
没有修改生产随机流或伤害结算。扩展回归还发现物理、毒伤各一个格挡场景未声明
持盾上下文，现用 setCombatBlockContext(true, 0) 明确补齐测试前提。

Source 36、Physical 19、Elemental 37、CreationCritical 5，以及 Act1MonsterEcsScenario
16、RemainingMonsterSkill 5、SpecialMissileController 2、MonsterNarrowPursuit 3、
MonsterNarrowDecision 1、PathfinderTerrainPursuit 7 均通过（合计 131）。同次扩展
运行共 165 项，仅毒伤格挡失败；修复后 Poison 单独重跑 34 项全部通过。
另补跑 ai 子包的 MonsterSkillStart 1、TrapMissileAi 7，全部通过，总计 173 项。
证据日志：build/ai-source-skills-narrow-regression.log（保留首次毒伤失败）、
build/ai-poison-block-regression.log、build/ai-skill-start-trap-regression.log。
此前关于“与窄道改动无交集”的记录不是实现正确性的证明；本轮通过定位和重跑
关闭该失败组。人工视觉/听感、跨电脑验证仍未执行，不据此扩大验收范围。

2026-10-30 补充生产随机流弹体结算回归 MonsterMissileProductionRandomIntegrationTest。
测试使用真实 ServerEntityFactory、arrow 表项、ServerGameplayContext、碰撞系统及
StateUpdater，不替换战斗解析器或随机源。seed 1、1234、-98765 各执行基线、同种子
重放和无关怪物随机扰动三个独立世界，逐项比较伤害及最终随机状态。
独立 BigInteger 原生种子参考计算验证物理 2560..2816、火焰 512..768、毒伤
256..512 的掷值、即时生命损失、四帧毒伤和状态到期；碰撞事件捕获弹体种子高低
两字，验证伤害随机流推进。真实 arrow 的 AR 判定消费 owner 随机流，单独检查
owner 高低两字恰好推进一次；无关单位在创建前、命中前及毒伤期间反复取随机值，
不改变结算结果。此场景无持盾、无抗性、无暴击，不能外推这些分支已新增覆盖。
生产随机流新测试 3 项、CreationCritical 5 项、Source 36 项共 44 项通过，无跳过。
日志 build/ai-production-missile-rng-regression.log；模块边界检查同时通过。
本轮没有生产实现修改，补齐的是工厂创建到碰撞结算的受控 ECS 自动验证。

2026-10-30 扩展蛆虫巢穴 seed 2 的 62→63→64 自然路线。首次固定提交 e6b694216
运行目录 build/ai-narrow-maggot-seed2-62-64：路线 visited=3/3、transitions=2、
combatKills=42、failures=0，但严格审计发现 6 条 SEGHHTH 查找告警，故总结果 FAILED，
不计为合格窄道证据。实体为 maggotegg2，MonStats2 未声明 GH，通用受击流程却仍
强制切入。D2MOO SUnitDmg.cpp::sub_6FCC1870 对无 GH 的怪物保留软受击；BL 分支
也检查模式能力。修复 a652b0ac6 在创建反应序列或删除当前动作之前检查已声明的
MonStats2 模式能力，不支持 GH/BL 时发出软受击，保留动作。没有增加动画资源回退、
屏蔽日志或放宽审计。新增三种蛆虫卵的 melee/missile、GH/BL 反例，以及普通骷髅
仍进入 GH 的正例；HitReactionResolver 8 项及弹体相关 134 项共 142 项全部通过。

同 seed/路线在修复提交重跑，目录 build/ai-narrow-maggot-seed2-62-64-fixed，
自然阶段约 909.2 秒，visited=3/3、walks=5、transitions=2、detachedJumps=0、
combatKills=40、failures=0，warnings=0、errors=0、fallbackRecords=0，严格审计通过。
独立窄道审计从 273 条候选记录接受 259 条成功行走配对样本：level 62 为 57 条/
18 个 sequence，level 63 为 190 条/60 个 sequence，level 64 为 12 条/3 个 sequence；
方向 X=89、Y=119、DPLUS=22、DMINUS=29。补齐 seed 2 和先前缺失的 level 63 局部
样本，同时获得 level 64 样本；旧 seed 1 证据属于旧提交，不能称为同版三种子矩阵。
可追溯摘要与原始文件 SHA256 保存在 docs/ai-narrow-maggot-seed2-20261030.json。
单次自然结果为 OBSERVED_RUNTIME_ONLY、总验收 PARTIAL；不外推为连续走廊几何、
怪物自然窄道穿越或原版一致性覆盖。人工视觉/听感及跨电脑验证仍未执行。

2026-10-30 继续在同一修复提交 a652b0ac6 执行 seed 3、62→63→64，目录
build/ai-narrow-maggot-seed3-62-64。已逐项核对与 seed 2 的 revision、源码包 SHA256、
MPQ 资源清单/大小/SHA256、楼层路线一致，只有地图 seed 不同。自然阶段约 831.2 秒，
visited=3/3、walks=5、transitions=2、detachedJumps=0、combatKills=42、failures=0，
warnings=0、errors=0、fallbackRecords=0，严格日志审计通过。

独立窄道审计从 136 条候选接受 134 条成功行走配对记录：level 62 为 25 条/8 个
sequence、level 63 为 98 条/32 个 sequence、level 64 为 11 条/3 个 sequence；
X=54、Y=63、DPLUS=11、DMINUS=6。摘要与原始证据文件 SHA256 保存于
docs/ai-narrow-maggot-seed3-20261030.json。当前 seed 2/3 均有同版三层局部窄道证据，
仍缺同版 seed 1，不能标成三种子矩阵完成。本轮未修改生产代码，未重复已通过的
代码测试；运行入口自行完成编译及模块边界检查。自然结果仍为 OBSERVED_RUNTIME_ONLY，
总验收 PARTIAL；沿用局部采样、玩家行走、非原版判定器的范围限制，未做人工听感/
视觉或跨电脑验证。
