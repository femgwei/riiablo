# 本机复测证据：Diablo / Diabloclone DiabWall

2026-10-15，Windows，本机1.10f MPQ：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
AI基线 `492585ed9d9ac99c4596d18630a8835b4b07783e`；保留并行地图提交
`da163a11`、`9539a2dc`。旧快照不覆盖，本轮仅补Skills197 / SrvDo102及独立地火验证场。

## 原版依据与实现

D2MOO HEAD `8322494ed1f715ad51552f169df76cf600fabc71`：

- `SkillMonst.cpp::SKILLS_SrvDo102_DiabWall`（0x6FD092B0）：calc1决定控制飞弹数，
  flags0x21、出生在施法者整格坐标；每个控制飞弹使用同一目标，ordinal传给initializer。
  这不是扇形散射，也不是玩家FireWall。创建失败不重试，继续其余ordinal。
  参数未设置nSkill（0），仅设置nSkillLevel；目标从当前单位或保存的路径坐标取得。
- `Skills.cpp::D2GAME_GetXAndYFromTargetUnit_6FD14020`：X和Y都非0才有效。
  `Missiles.cpp`创建规则：有速度且目标与出生点相同则目标各+1；任一轴距离>=100拒绝。
- `SkillMonst.cpp::SKILLS_MissileInit_DiabWall`（0x6FD09410）：TotalFrames>=78时封顶77，
  CurrentFrame也设77。seedLow为ordinal加PathGetFirstPointX；`Path.cpp`证实此函数实际
  返回tTargetCoord.X，不是施法者X。high seed初始666，以high+0x6AC690C5*unsigned(low)
  roll；低32位unsigned%100>=20才选择ChargedBolt，否则直行。ChargedBolt首方向另消费一次roll。
  `PathMisc.cpp::PATH_ComputePathChargedBolt`按目标主方向、每2格一段及原32项方向序列处理；
  本轮复用已有ChargedBolt运行路径，未声称完整预生成整格路径逐位相同。
- `MissMode.cpp::MISSMODE_SrvDo12_DiabWallMaker`（0x6FC57DA0）：只有
  D2Common_10234(path)才在控制飞弹当前整格位置生成SubMissile1，继承owner/skill/level，
  随后通用HandleMissileCollision。`Step.cpp`证实该条件为pathflag0x8，即前次移动跨整格，
  不是速度非0；SrvDo在本次移动之前执行。本轮保留原地图碰撞和房间处理。
- `D2Common/Units/Missile.cpp`：child无Skill/MissileSkill时使用Missiles.txt伤害，
  不能被Skills197空伤害覆盖；ApplyMastery只在表允许时用。复用已有fixed火焰区resolver，
  保存8.8每帧伤害；控制飞弹不执行普通单位伤害/LastCollide近战伤害fallback。

本机原表/动画有正式断言：197 St0/Do102、calc1=lvl、SrvMissileA=diabwallmaker，
唯一Do102消费者。Skills.monanim=S1，但MonStats的Skill4槽位覆盖为S3，
diablo等级3、diabloclone等级2；测试加载真实DIS3HTH的唯一动作marker，不注入SkillDo。

maker179：Do12/Hit0/Dmg0、Vel20、Range80、Size1、InitSteps2、CollideType3，
Collision=false、CollideKill=false、LastCollide=true、SubMissile1=diabwall，无伤害。
child180：Do1/Hit0/Dmg0、Vel0、Range36、Size2、DamageRate41、无Skill、MissileSkill=false、
ApplyMastery=false；fire、EMin12/EMax25、各档每级+5、HitShift3。
等级3原始8.8每帧火伤176..280，等级2为136..240（均上界排除）。

## 最终本机结果

发布源目录：`server/d2gs/build/monster-ai-arena/20261015-diab-wall-full`。
Normal、seed1、每场500个25Hz tick：557行/1159场。
ACTION_OBSERVED486、AI_FALLBACK376、WARNING111、NO_ACTION175、MOVEMENT_ONLY7、
TERMINAL_OBSERVED4。异常/ERROR_LOG/NOT_RUN/SPECIAL_NOT_TRIGGERED均0。
47个AI名称/188行fallback仍未解决；动作观察不等于完整AI正确性PASS。

新增两场diab-wall均DIAB_WALL_OBSERVED。自然AI原表决策，场景距离18；
不改概率、不强制技能、不注入SkillDo、不关闭地图碰撞。独立oracle分四阶段：

1. 主体真实S3/197/102动作点，实际出现3/2个maker、77帧/skill0/原等级正确。
2. 实际地火parent/owner/cast序号正确，8.8火伤、36帧及DamageRate41与原表一致。
3. DamageEvent携带真实missileId，独立检查属于该cast的纯火包以及目标实际HP下降。
4. 真实S3结束、首cast所有控制/地火实体全部清理。cast序号防止后续施法或实体ID复用混淆。

两行首cast均tick261生成maker、263铺火，diablo tick282/clone tick281实际扣血，
tick374均达到阶段4。旧18复活、6触手、3蛙魔、10持续火焰/电光、2冰击和4无主人场
继续成功，共45场专属成功。没有用其他辅助怪的动作或伤害替代主体通过。

与上一批1157场按monster/scenario逐字段比较：1155场相同、2场变化、新增2场、无丢失。
变化仅diablo/far、diabloclone/far：damageEvents75→361/558，warnings均19→15，
damage分别2047→2161.6953、2263→1827.1367，状态仍WARNING。
移植地火改变原有伤害/冷却叠加后的总量，不要求clone伤害单调增加。
两场新增地火也各有15条St54/Do103/104及DIXXHTH资源回退告警，总WARNING109→111；
不能把地火链成功当作整个Diablo AI验收，其他告警不隐藏。

最终定向回归408项（core375/d2gs33），49个suite，失败/错误/跳过0。
本轮新增DiabWall数据2项、集成7项及arena顺序oracle1项；额外包含既有FireWall/ChargedBolt/
ChargedStrike golden回归，其余为上一批相邻AI/伤害/生命周期回归，不重复累加初轮或复跑。
9项DiabWall测试覆盖真实S3单发、原始种子/unsigned分支/额外roll/短寿命不延长、
归属和独立地火伤害、真实HP变化/动画结束/到期清理、静止/慢移跨格门控、移除目标坐标fallback、
零轴/远轴拒绝、武器伤害不混入/75火抗/怪物100火免、首次创建失败不重试、
真实合成地图屏障阻挡和池组件复用。首次fixture失败发现共用死亡目标guard阻断坐标fallback，
已仅对DiabWall放行并复测；另修正测试对首child必来自首maker的错误假定，没有放宽生产oracle。

发布JSONL SHA256：`6535B5525530A5EDB95CCFF1737D3CA09DD48CA16C24F0E43377A8922BB89A3D`。
最终代码再次完整运行`20261015-diab-wall-repeat`，1159场JSONL具有相同SHA256。
第二次结果/汇总/完成标记见`repeat-results.zip`。

## 复现

输出目录必须不存在。全目录或两行定向复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-diab-wall-full --max-workers=1 --console=plain
# 两行定向追加 '-ParenaMonsters=diablo,diabloclone'；其余1149场NOT_RUN，不是全目录验收。
```

最终定向回归命令（同次运行还追加上述全目录任务）：

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
 --tests '*DiabCold*Test' --tests '*DiabWall*Test' `
 --tests '*CombatSystemTest' --tests '*DamageApplierTest' `
 --tests '*SorceressTelekinesisIntegrationTest' --tests '*TelekinesisGoldenDamageTest' `
 --tests '*ColdArrowGoldenDamageTest' --tests '*StateListTest' `
 --tests '*FireWallGoldenDamageTest' --tests '*ChargedBoltGoldenDamageTest' `
 --tests '*ChargedStrikeGoldenDamageTest' `
 :server:d2gs:test --tests '*MonsterAiArenaTest' `
 --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' --max-workers=1 --console=plain
```

## 明确保留的边界

- ChargedBolt复用项目连续运行路径和共有速度单位，没有扩改全局75%速度换算或16.16精度。
  原版完整预生成整格路径/房间路径遍历/InitSteps2尚非逐位复刻，本轮只确认种子与分支规则、
  首方向额外roll、跨整格发射门控及实际控制/地火生命周期。
- 地火8.8伤害复用共有fixed元素resolver；本轮独立测试玩家、player-owned普通怪、
  火抗/免疫和武器伤害隔离，不称所有宠物/佣兵/难度伤害矩阵已验收。
  原版完整伤害事件、特定source死亡链和全局单位随机流仍待对齐。
- arena仍只是Normal seed1；三难度多seed、另一电脑真实TCP/GL表现未验收。
  DiabRun St54/Do103、DiabPrison Do104、完整Fn051、Horror/Sentry MonSeq、完整Fn065、
  Megademon专用AI及47AI/188行fallback仍待处理。下一项可补DiabRun。
- 这不是全项目测试。Baal目标链line317旧失败已在18a5cc7b基线复现，未纳入/未修复，
  证据保留在`../20261015-inferno-seed1/baseline-known-failure.xml`。

`logs.zip`含1159场完整日志，解压到本目录后report.html日志链接可用；
`regression-final.zip`含core/d2gs分目录49个最终XML，`repeat-results.zip`含第二次全目录结果。
