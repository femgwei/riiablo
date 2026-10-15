# 本机复测证据：Diablo / Diabloclone DiabRun

2026-10-15，Windows，本机1.10f MPQ：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
基线`df8eda01f94eb92f0400668fd4d1f8f227f74994`，旧快照不覆盖；本轮补Skills198 / St54 / Do103。
D2MOO HEAD `8322494ed1f715ad51552f169df76cf600fabc71`，没有凭经验替代原版证据。

## 原版依据与实现

- `SkillMonst.cpp::SKILLS_SrvSt54_DiabRun`（0x6FD094B0）：必须usedSkill和单位目标，
  flags清0，保存目标GUID/type，清路径targetUnit，保存起手目标整数X/Y；不追踪移动目标。
- `SKILLS_SrvDo103_DiabRun`（0x6FD09570）：路径结束flags2改count8/cursor14；
  源帧5按calc1及STAT_VELOCITYPERCENT启动PATHTYPE_ASTAR，flags1；13改count16/cursor6。
  action1查保存身份，当前近战距离才消费动作并结算技能包，ExecuteEvents(bMissile=1)
  /ExecuteMissileDamage；不是普通近战伤害fallback，不生成飞弹实体。
- `MonsterMode.cpp::sub_6FC64E90`：moving推进路径，结束置flags2并回调技能；按MonSeq事件
  回调技能，随后StopSequence。`Units.cpp::UNITS_InitializeSequence`初始dwSeqSpeed256；
  `UNITS_SetAnimationFrame`只seek序列cursor且保留低8位，`UNITS_StopSequence`独立递减remaining。
  本轮按正常256时钟处理，FREEZE暂停；本地RN动画跟随权威源帧回卷，不用XX→A1/COF伪造事件。
- `Path/AStar.cpp::PATH_AStar_TargetLocationHasEnoughRoom`无targetUnit时直接允许搜索；
  ComputePath保存best heuristic节点，目标不可达也flush最近可达路径，邻节点仍检查碰撞。
  本轮新增opt-in partial入口，普通exact入口不变；不忽略玩家占位、不穿屏障。
  8.8速度换算至项目单位为calc1*25/16，再用怪物75%基准，calc1=20时23.4375 subtile/s。
- `Skills.cpp::sub_6FD155E0`已有SUCCESSFULHIT则不再掷AR；ResultFlags5为SUCCESSFULHIT/GETHIT，
  不是knockback8。`D2Skills.cpp::SKILLS_GetMinPhysDamage`调用a4=0，不取SrcDam武器包。
  `SUnitDmg.cpp::SUNITDMG_ApplyResistancesAndAbsorb`：PDR先于抗性、玩家物抗50cap、怪物100免疫，
  物抗不受难度惩罚、负抗最低-100、无最低1点。新增仅Do103使用的native物理入口，保留旧共有行为。
  佣兵是怪物类型，不套玩家50cap；完整佣兵/宠物矩阵未宣称验收。

本机原表正式断言：Skills198 St54/Do103唯一消费者，monanim=xx、calc1=20，
Param=[8,14,5,13,16,6,0,0]、ResultFlags5/HitFlags0/SrcDam0、EType空、HitShift8，
Min17/Max33、各档每级+9。两行MonStats Skill5=DiabRun、Sk5mode=seq_diablorun、等级5。
技能物理53..69（上界排除），无元素包。seq_diablorun共22点，全RN/源帧0..21，
5..13事件2、18事件1，其余无事件；直接读取MPQ的MonSeq，不以resident COF marker代替。

## 最终本机结果

发布源：`server/d2gs/build/monster-ai-arena/20261015-diab-run-full-final`。
Normal、seed1、每场500个25Hz tick，557行/1161场：ACTION_OBSERVED490、AI_FALLBACK376、
WARNING109、NO_ACTION175、MOVEMENT_ONLY7、TERMINAL_OBSERVED4。
异常/ERROR_LOG/NOT_RUN/SPECIAL_NOT_TRIGGERED均0。47AI名称/188行fallback仍未解决。

新增两场diab-run均DIAB_RUN_OBSERVED，自然AI原表选择，距离18，不强制技能、不注入SkillDo、
不改概率、不关闭碰撞。独立oracle四阶段：真实198/St54/RN起手；坐标路径无tracking且实际
位移>0.5；同cast源帧18纯物理53..69且HP下降；该cast结束且路径清理。保存runtime引用防后续cast混淆。
两场均tick1阶段1、7阶段2、29实际56物理扣血/阶段3、33阶段4；146→130.0625，未走入玩家128。
旧18复活/6触手/3蛙魔/10持续火焰或电光/2冰击/2地火/4无主人场全部成功，共47专属。

与旧1159场按monster/scenario全字段对比：1155相同，4变化，新增2，无丢失：
两行far由WARNING变ACTION_OBSERVED，warnings15→0、actions83→65、damageEvents361/558→63、
moves28→17；伤害2161.6953→1887和1827.1367→3985。冲刺接近目标改变后续自然选择，
far未出现其他技能告警不等于所有技能都验收。
两行diab-wall仍WARNING，warnings15→1（真实Do104），actions83→67，moves28→75/73，
damageEvents361→273/558→168，总伤害2000.5664/2721.1602。near/inferno-cycle/diab-cold保持逐字段相同。

地火fixture变化明确保留：冲刺真实接近后，初静止角色场500tick地火不再触发，
`initial-diagnostic.zip`保存两场NOT_TRIGGERED日志和当时定向JSONL/汇总（其余1149 NOT_RUN，非全目录）。
仅diab-wall场在观察到run结束、距离<10且角色无路径时，用普通Pathfinder后撤16，
不传送、不强制施法、不改怪物概率、不放宽旧地火四阶段oracle。
两行均tick34后撤到112，diablo tick235/clone234再后撤到96；
地火四阶段分别262/264/284/375及261/263/283/374，Do104 tick196仍如实告警。

最终定向回归460项：core426/54suite、d2gs34/3suite，失败/错误/跳过0；不累加初轮/复跑。
11项DiabRun测试覆盖原表、序列低位/count、真实寻路/loop/单击/收尾、移走/移除目标、
0AR/高防/100block/9999武器隔离、玩家50/100物抗同cap、player-owned普通怪90/100抗性、
平减先于抗性/足量平减归零/负抗下限、静态屏障、FREEZE/死亡/取消清理、无目标拒绝和池复用。
另有本地真实DIRNHTH动画源帧seek及arena阶段顺序/假击/过早清理拒绝测试。
定向fixture真实MonSeq、Actioneer、AnimStepper、Pathfinder、动态碰撞和位移积分，不注入SkillDo。

发布JSONL SHA256：`CDD0149DDEE66C6BF6C71B73B5FB2BF467AE7EFF94937A910DA0DED621E95C52`。
同代码再次完整运行`20261015-diab-run-repeat`，1161场JSONL哈希完全一致，结果见`repeat-results.zip`。
并行地图/其他技能任务的未提交修改保留、不混入本轮提交；复跑结果一致不等于验收这些并行改动。
测试在共享工作区执行，包含执行时可见的并行修改，不是提交后的独立worktree全项目验收。

## 复现

输出目录必须不存在：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
 -ParenaOutput=server/d2gs/build/monster-ai-arena/my-diab-run-full --max-workers=1 --console=plain
# 定向两行可加 '-ParenaMonsters=diablo,diabloclone'，其余NOT_RUN不是验收。
```

最终定向回归使用前批`../20261015-diab-wall-seed1/verification.md`列出的完整命令，
在core追加以下筛选，并在同次运行追加上述full-final全目录任务：

```powershell
--tests '*DiabRun*Test' --tests '*PathfinderTest' --tests '*PathfinderRoomBoundaryTest' `
--tests '*PlayerGroundPathTest' --tests '*VelocityAdderTest' --tests '*VelocityModeChangerTest' `
--tests '*PlayerVelocityUnitsTest'
```

## 明确保留边界

- 最近可达A*复用项目Euclidean启发式、约300次搜索预算/平滑/房间约束，并非D2MOO
  原完整节点预算、tie-break、邻节点顺序和整格路径逐位复刻。阻塞后通过共有stalledTime结束路径，
  不是全局Step状态机移植。普通怪速度全系统未扩修。
- 正常序列256时钟，未覆盖原版所有状态/动画速率修正矩阵；速度percent/state继续用共有投影。
  技能伤害为整数投影，不宣称原生8.8逐位随机流/全部事件/命中反应/吸血反伤矩阵完成。
  目标保留ECS id，不是原GUID/type/generation全系统实现；当前近战距离复用共有round/size投影。
- arena只验Normal seed1，不是实际全Act地图刷怪覆盖。TCP独立客户端/GL、三难度多seed待验收。
  Do104/完整Fn051、Horror/Sentry MonSeq、完整Fn065、Megademon及47AI/188行fallback仍待处理。
  下一项可补DiabPrison Do104。
- 非全项目测试：Baal目标链line317旧失败未纳入/未修，在18a5cc7b已复现，证据
  `../20261015-inferno-seed1/baseline-known-failure.xml`。

`logs.zip`含1161场完整日志，解压到本目录后report.html日志链接可用；
`regression-final.zip`含core/d2gs分目录57个最终XML。旧快照与初失败证据保留。
