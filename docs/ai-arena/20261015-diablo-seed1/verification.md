# 本机复测证据：Diablo / Diabloclone 电光持续链

2026-10-15，Windows，本机1.10f MPQ：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
代码基线 `224def8f93912ff8ab262a53ce1c3372023b71f5`；旧快照不覆盖。

## 原版依据和修复范围

D2MOO HEAD `8322494ed1f715ad51552f169df76cf600fabc71`，本轮直接核对：

- `SkillMonst.cpp::SKILLS_SrvSt53_MonInferno`（0x6FD07CD0）：Param1为当前帧加
  max(1,calc2)，打开布尔INFERNO，不按TTL自动关闭。
- `SKILLS_SrvDo152_DiabLight`（0x6FD082D0）：与Do95相同，先创建SrvMissileA，
  再检查deadline和INFERNO；成功且继续时max(1,calc3)后重排MODECHANGE。
  最后一次已排程回调仍创建飞弹，然后改排InfernoLen后的ENDANIM。
  目标坐标或创建失败不伪造成功，不走普通技能的泛用飞弹重试。
- `SKILLS_UpdateInfernoAnimationParameters`（0x6FD081E0）：calc1非正时使用
  Missiles.Param2+level-1，clamp1..255；动画速度(AnimLen<<8)/寿命。
- `SKILLS_SetInfernoFrame`（0x6FD07D80）：非SQ只seek至InfernoAnim，
  不缩短原COF帧数。本轮193/SC接入上一批独立周期/尾时钟，311/323 MonSeq仍不接入。
- `AiThink.cpp::AITHINK_Fn051_Diablo`（0x6FCE8950）动作5：判断的是真实INFERNO，
  不是先前攻击次数；有状态则关闭、idle2并清动作，没有则使用Skill1并保留动作5。
  保留动作的分派在无目标分支之前，因此丢失目标也需要消费已有开关。
- `AiTactics.cpp::AITACTICS_UseSkill`不含“施法后两帧强制关闭”；
  `MonsterMode.cpp::sub_6FC63680`/`sub_6FC64310`负责结束模式/中立后的AI调度。
  本轮移除Java动作5伪造的actionCount开关及两帧delay，保留项目现有忙碌保护，
  不允许新决策覆盖仍在执行的SC/尾动画。全局原生AI事件调度仍属未完成项。

本机原表/动画数据均有正式断言：

- Skills193 DiabLight：St53/Do152，monanim=SC，SrvMissileA=diablight，
  calc2=par1得到40，calc3=2。
- diablo/diabloclone：AI=Diablo，Skill1=193/SC，Sk1lvl分别3/2；
  MonStats2 InfernoAnim=9、InfernoLen=6。
- DISCHTH：17帧、speed256，源帧9唯一MIS动作点；首发仍来自真实动画数据，
  不注入SkillDo或强制技能选择来通过arena。
- diablight：Param2=30、Vel=20、VelLev=0、CanSlow=true、AnimLen=15；
  对应两行技能级的帧寿命32/31，动画速度随寿命计算。

## 本机结果

发布源目录：`server/d2gs/build/monster-ai-arena/20261015-diablo-full`。
Normal、seed1、每场500个25Hz tick：557行/1155场。

ACTION_OBSERVED480、AI_FALLBACK376、WARNING113、NO_ACTION175、MOVEMENT_ONLY7、
TERMINAL_OBSERVED4。运行时异常/ERROR_LOG/NOT_RUN/SPECIAL_NOT_TRIGGERED均0。
仍有47个AI名称/188行fallback，不将动作观察算成完整AI正确性PASS。

新增diablo/diabloclone各一个inferno-cycle，共10场Inferno循环通过；
旧18复活、6触手、3蛙魔和4无主人死亡场继续成功。
Diablo专属oracle还要求尾动画结束后实际观察到INFERNO关闭，才能接受下轮施法。
两行均：tick181首轮start，190首发（动作帧9），随后每2tick一发，
222最后一发（动作帧41、deadline40、共17发），228尾结束/状态关闭，338新一轮start。
日志包含真实分配的飞弹ID、owner/skill/寿命有效性、间隔/deadline/end和InfernoReset。

专属通过没有覆盖其他告警：DiabCold Do100及其他技能St54/Do102/103/104、
XX→A1资源回退仍可见，因此Diablo六场仍标WARNING。新增两场使总WARNING111→113。

与上一批1153场按monster/scenario逐字段比较：1149场完全一致，4个既有场变化，
新增2场，无旧场丢失。变化仅diablo/diabloclone near/far，来自真实持续发射和
动作5不再被先前攻击计数抑制：

- diablo near：actions27→71、damageEvents21→68、warnings3→2、damage948→2000。
- diablo far：actions22→83、damageEvents12→75、moves73→28、warnings31→19、damage477→2047。
- diabloclone near：actions27→71、damageEvents21→68、warnings3→2、damage3955→3997。
- diabloclone far：actions24→83、damageEvents15→75、moves54→28、damage2648→2263。

定向回归301项（core270/d2gs31），36个suite，失败/错误/跳过均0。
包含本轮真实SC生命周期、关状态末发/创建失败、真实开关/攻击计数隔离/目标丢失、
本地飞弹动画，以及上一批95、复活、Submerge、FrogDemon、Frenzy、Baal和邻接玩家技能。
XML在`regression-final.zip`，不是全项目测试。上轮已隔离的Baal目标链line317旧失败
未在本轮修改或宣称通过，证据仍在`../20261015-inferno-seed1/baseline-known-failure.xml`。

完整JSONL SHA256：`C60C3CE74A377C0D3BC9E19971FD1D5AEDB86B970222E68C4B0757BBA6C8B5D8`。
第二次完整运行`20261015-diablo-repeat`的JSONL具有相同SHA256，1155场逐字段一致；
第二次结果/汇总/完成标记另存`repeat-results.zip`，测试项数不重复累加。

## 复现

输出目录必须不存在。完整目录复跑：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-diablo-full --console=plain
```

只跑两行（其他行会NOT_RUN，不能当全目录通过）：

```powershell
.\gradlew.bat :server:d2gs:monsterAiArena '-ParenaMonsters=diablo,diabloclone' `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-diablo-targeted --console=plain
```

本轮完整301项回归命令：

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
 :server:d2gs:test --tests '*MonsterAiArenaTest' `
 --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' --console=plain
```

## 明确保留的边界

- 本轮只补DiabLight持续链和动作5开关，不宣称完整Fn051目标权重/传送门/光环/其他技能移植。
  下一项可继续处理DiabCold SrvDo100，仍以D2MOO和原表为依据。
- 沿用项目动作时钟与冻结暂停、现有速度单位和伤害/路径投影；不宣称原版全局事件队列、
  完整固定点飞弹路径/75%速度换算和整数伤害、三难度技能级加成已对齐。
- 311/323 MonSeq、完整Fn065、Megademon专用AI、47AI回退仍待处理。
- TCP独立客户端的持续施法/飞弹元数据、真实GL地图和三难度多seed尚未验收，
  需要另一台电脑继续验证；未改变碰撞/原表概率，没有关闭告警或注入假技能。

`logs.zip`含1155场完整日志，解压到本目录后report.html日志链接可用。
