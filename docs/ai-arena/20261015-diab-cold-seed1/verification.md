# 本机复测证据：Diablo / Diabloclone DiabCold

2026-10-15，Windows，本机1.10f MPQ：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
AI基线 `7f8f318923a3739b3b87e4805b74d36bc66e5444`；保留并行地图提交
`ba07b423`。旧结果不覆盖，本轮仅补Skills194 / SrvDo100及独立验证场。

## 原版依据

D2MOO HEAD `8322494ed1f715ad51552f169df76cf600fabc71`：

- `SkillMonst.cpp::SKILLS_SrvDo100_DiabCold`（0x6FD08EB0）：取得当前单位目标，
  设置ResultFlags|1和FrzLen，再RollElementalDamage、ExecuteEvents(bMissile=1)、
  ExecuteMissileDamage。不走攻击命中率、近战距离重判、武器伤害或飞弹实体创建；
  无单位目标不以坐标替代。原表ResultFlags/HitFlags/HitClass均0，SrvOverlay为空。
- `Skills.cpp::D2GAME_RollElementalDamage_6FD14DD0`与`sub_6FD11E40`：按EType路由伤害。
  本机原表EType是**mag，不是cold**；FrzLen独立保留。伤害随机上界排除。
- `D2Common/D2Skills.cpp::SKILLS_GetElementalLength`：长度使用1–8/9–16/17+三档，
  ELenSymPerCalc按百分比加成，不是平加，也不在这里除难度。
- `SUnitDmg.cpp::SUNITDMG_CalculateTotalDamage/ApplyResistancesAndAbsorb`：
  Magc走magicresist和魔法吸收，难度惩罚不作用于magicresist；MDR先于抗性，
  百分比吸收上限40%。怪物不套玩家75..95抗性上限，>=100免疫不被穿透打破。
  Flen走coldresist/maxcoldresist/passivecoldpierce，不扣MDR或吸收；
  CannotBeFrozen清零、HalfFreeze在抗性之前减半。非普通怪物的抗性路径按现有佣兵标记分类。
- `SUNITDMG_ApplyFreezeState/ApplyColdState`：玩家降为COLD；Boss/unique/champion/佣兵
  降为COLD；普通怪coldeffect>=0不冻结，否则除MonsterFreezeDiv。
  COLD独立使用MonsterColdDiv。复用StateUpdater，不预先重复缩放；UNINTERRUPTABLE拒绝。
  目标城镇、死亡、阵营、可攻击标志和当前zone使用现有服务端合法性校验。

本机真实数据/动画均有正式断言：Skills194 St0/Do100、S2、mag、HitShift8，
EMin15/EMax25，前档每级+9/+12，ELen100、三档均+50。
diablo Skill2等级4：魔法42..61（上界排除）、长度250；diabloclone等级2：24..37、150。
DIS2HTH为16帧/speed256，源帧6唯一ATK。首发来自真实动作点，不注入SkillDo。

实现保留旧通用伤害调用的历史MDR/抗性上限行为，新直接包入口仅供Do100使用；
共享固定元素包另修正了魔法抗性不受难度惩罚。旧resolveColdDuration未改。

## 最终本机结果

发布源目录：`server/d2gs/build/monster-ai-arena/20261015-diab-cold-final`。
Normal、seed1、每场500个25Hz tick：557行/1157场。

ACTION_OBSERVED486、AI_FALLBACK376、WARNING109、NO_ACTION175、MOVEMENT_ONLY7、
TERMINAL_OBSERVED4。异常/ERROR_LOG/NOT_RUN/SPECIAL_NOT_TRIGGERED均0。
仍有47个AI名称/188行fallback，动作观察不是完整AI正确性PASS。

新增两场diab-cold均DIAB_COLD_OBSERVED。独立oracle要求真实S2/194施法、恰一个
MISSILE魔法包、实际HP变化与包相符、COLD来源和250/150初始时长正确、没有FREEZE、
没有新增飞弹，并观察随后真实S2结束。不能仅凭施法/伤害日志通过。
两行首轮均tick7命中、tick17结束；diablo首发44魔法、clone首发30魔法。
旧18复活、6触手、3蛙魔、10持续火焰/电光和4无主人死亡场继续成功，共43场专属。

与上一批1155场按monster/scenario逐字段比较：1151场相同，4场变化，新增2场，无丢失。
变化仅diablo/diabloclone的near和inferno-cycle：damageEvents68→70、warnings2→0，
damage分别2000→2048和3997→4090，状态WARNING→ACTION_OBSERVED。
两行far逐字段未变，Do102/103/104、St54及资源回退告警继续保留。
因此总WARNING113→109，新增两场和原四场使ACTION480→486；不把未施放的其他技能算通过。

最终定向回归395项（core363/d2gs32），44个suite，失败/错误/跳过均0。
其中本轮DiabCold数据2项、集成11项，覆盖真实动作点和单发结束、伤害/长度两类抗性、
MDR/百分比与固定吸收、CBF/半时长、怪物免疫/90%抗性、玩家三难度惩罚/95%上限、
三难度怪物FreezeDiv/ColdDiv只除一次、UNINTERRUPTABLE、非法/移除/远离目标及死亡/到期。
邻接CombatSystem/DamageApplier/Telekinesis/ColdArrow/StateList和上一批AI回归继续成功。
XML在`regression-final.zip`；不是全项目测试，也不重复计入初轮或复跑。

发布JSONL SHA256：`1DF491438ED77241A6C61FD78285DF93BF1CF5A9974FFAC4E1AF8DD37A61CC06`。
最终代码再次完整运行`20261015-diab-cold-repeat`，1157场JSONL具有相同SHA256，逐字段一致。
第二次结果/汇总/完成标记保存于`repeat-results.zip`；初轮诊断不是发布证据的替代。

## 复现

输出目录必须不存在。全目录或两行定向复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-diab-cold-full --max-workers=1 --console=plain
# 两行定向：追加 '-ParenaMonsters=diablo,diabloclone'；其余1149场会NOT_RUN，不是全目录通过。
```

最终定向回归命令（另在同次运行追加上述全目录任务）：

```powershell
.\gradlew.bat :core:test `
 --tests '*GreaterMummyAiIntegrationTest' --tests '*BloodLordAiIntegrationTest' `
 --tests '*NativeMummySequenceTest' --tests '*BestowIntegrationTest' `
 --tests '*ServerMonsterCorpseSystemTest' --tests '*Act1NativeAiIntegrationTest' `
 --tests '*Act1MonsterEcsScenarioTest' --tests '*UnitLifecycle*Test' `
 --tests '*TentacleAiIntegrationTest' --tests '*BaalCrabAiIntegrationTest' `
 --tests '*BaalCrabCloneAiIntegrationTest' --tests '*NativeFrenzyDataTest' `
 --tests '*BarbarianFrenzyTest' --tests '*SequenceHandler*Test' `
 --tests '*SubmergeSequenceIntegrationTest' --tests '*NativeSubmergeDataTest' `
 --tests '*AnimationStepperTest' --tests '*FrogDemonAiIntegrationTest' `
 --tests '*FrogPresenceIntegrationTest' --tests '*DynamicUnitCollisionSystemTest' `
 --tests '*MonInferno*Test' --tests '*FetishShamanInfernoAiTest' `
 --tests '*InfernoGoldenDamageTest' --tests '*DiabloAiIntegrationTest' `
 --tests '*MonsterAiParamParityTest' --tests '*AssassinSkillSpecializationTest' `
 --tests '*SorceressFireAreaIntegrationTest' --tests '*NativeAssassinSkillMatrixTest' `
 --tests '*NativeSorceressFireAreaDataTest' --tests '*MissileNativePolicyTest' `
 --tests '*DiabCold*Test' --tests '*CombatSystemTest' --tests '*DamageApplierTest' `
 --tests '*SorceressTelekinesisIntegrationTest' --tests '*TelekinesisGoldenDamageTest' `
 --tests '*ColdArrowGoldenDamageTest' --tests '*StateListTest' `
 :server:d2gs:test --tests '*MonsterAiArenaTest' `
 --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' --max-workers=1 --console=plain
```

## 明确保留的边界

- 伤害沿用项目整数HP/伤害和MathUtils随机投影，上界排除，但不是原版8.8精度与单位种子
  随机流的全链逐位复刻。原生伤害事件全套物品触发/宠物伤害倍率/DEATH_DELAY仍未对齐；
  UNINTERRUPTABLE本轮采用无伤害/控制投影，不声称完整原生延迟死亡实现。
- StateUpdater共有状态刷新/冷冻时钟继续复用，没有借本轮改写全局状态系统。
  本机测试覆盖玩家、普通怪、unique，其他原表Boss/champion/佣兵分档复用共有逻辑，
  不称每个怪物分档都已新增独立验收。
- 原表SrvOverlay为空，不增加自定义overlay同步设施；真实TCP客户端状态/受击动画和GL地图
  仍需另一电脑验证。三难度长度/魔法伤害有定向测试，arena仍只是Normal seed1。
- 其他Diablo技能/完整Fn051、Horror/Sentry MonSeq、完整Fn065、Megademon专用AI、
  47AI回退以及全局时钟/路径/难度技能级待处理；下一项可接DiabWall Do102。
- 这不是全项目测试。已在18a5cc7b基线复现的Baal目标链line317旧失败未纳入/未修复，
  证据保留在`../20261015-inferno-seed1/baseline-known-failure.xml`。

`logs.zip`含1157场完整日志，解压到本目录后report.html日志链接可用；
`regression-final.zip`保存最终XML，`repeat-results.zip`保存第二轮完整结果，不重复累加测试数。
