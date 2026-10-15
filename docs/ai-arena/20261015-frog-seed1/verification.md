# 本机复测证据：FrogDemon

2026-10-15，Windows，1.10f MPQ来自 `G:\BaiduNetdiskDownload\Diablo II 1.10F`。
原版依据为D2MOO `8322494ed1f715ad51552f169df76cf600fabc71`：

- `AiThink.cpp::AITHINK_Fn052_FrogDemon`、`sub_6FCD55D0`，
  `SUnit.cpp::sub_6FCBDFE0` 的落点恢复；不是按怪物外观推测。
- `MonsterMode.cpp::sub_6FC64790`、`Skills.cpp::sub_6FD13410`：usedSkill的模式更新
  可以调用SrvDo，无需MonSeq动作点。`SkillMonst.cpp::SrvDo094` 在源帧0停止序列。
- 直接读取本机MonSeq原表：`seq_froghidden`仅S1源帧0/1/2，没有ATK/MIS事件。
  不能套触手的反向序列，也不能伪造一个动作点。新增独立模式更新事件调用真实94，
  保持源帧0，不发SkillDo/动画结束来冒充一次攻击；客户端跟随该权威源帧。
- `D2Collision.cpp::COLLISION_GetFreeCoordinatesImpl` 与 `D2Dungeon.h::DUNGEON_GetRoomAtPosition`：
  `bAllowNeighborRooms=FALSE`控制搜索失败的回退，不禁止搜索成功进入合法相邻房间。
  恢复测试覆盖原生邻房间可用/未链接房间不可用；没有把FALSE直译成禁止邻房间。

## 修复和边界

Fn052的初始/潜伏/战斗三态、12/24帧延迟、严格距离条件、>64次决策超时、
近战A2→A1→绕行和远程射击/绕行优先级均接入原生概率流；0%也消费随机数。
战斗后不重置初始态，不再反复下潜。SMALL_NO_PRESENCE独立于不可选标志；
普通SrvSt51不会让触手等其他怪物失去动态占位。恢复helper先设置0x0E，再检查
当前pattern及静态/对象/门/NO_PLAYER/动态NO_PATH，宠物PET不在0x1C09中；失败不伪造浮出。
恢复后采用SMALL_UNIT_PRESENCE而不修改单位战斗尺寸。

共用circle/approach仍是项目路径投影，不声称逐步路径截断、等距落点遍历顺序完全匹配。
Fn052特殊状态回调 `D2GAME_AI_Unk052_6FCDA910` 尚未单独接入：
Terror/DimVision/Taunt/其他特殊控制继续使用项目共有桥接，未宣称特殊状态完全对齐。
此次不是通用MonSeq实现；未知Submerge序列仍告警。

## 实测

- 发布源运行：`server/d2gs/build/monster-ai-arena/20261015-frog-final2`。
  probe、final、long等早期目录均保留，不覆盖。Normal、seed1、每场500个25Hz tick。
- 557行/1145场全部执行：ACTION_OBSERVED480、AI_FALLBACK376、WARNING103、
  NO_ACTION175、MOVEMENT_ONLY7、TERMINAL_OBSERVED4；RUNTIME_ERROR/ERROR_LOG/
  NOT_RUN/SPECIAL_NOT_TRIGGERED均0。回退仍为188行、47个AI名称。
- 原18场复活、6场触手潜入循环、4场无主人死亡仍成功。
- 新3场 `frog-emerge` 全部 `FROG_EMERGENCE_OBSERVED`，无告警/错误。
  距离18自然下潜，停帧且无占位后角色沿真实Pathfinder/VelocityAdder路径靠近到距离6；
  必须依次恢复占位/标志、完成真实浮出动画、主体发出A1或A2攻击。
  日志 `FrogCycle phase=1/2/3/4` 对应这四阶段；辅助单位事件不能满足断言。
  frogdemon1四阶段发生在tick2/38/54/198。
- 与 `../20261015-submerge-seed1/results.jsonl` 比较每个字段：仅蛙魔6个旧near/far场变化，
  其余1136场完全一致；没有旧场丢失。新增3场另计。WARNING106→103，未更改白名单。
  far没有SkillDo是正常潜伏，不再把94的维持更新计成攻击。
- 定向回归181项（core153、d2gs28），失败/错误/跳过均0。包含原表、无动作点的真实
  模式回调、冻结/恢复、序列重建、客户端、决策优先级/超时/失败恢复、真实工厂静态及
  动态碰撞/PET/房间边界、专属oracle反例和前两批相关回归。

长时发布源运行 `server/d2gs/build/monster-ai-arena/20261015-frog-long2`，
每场2200tick，三种蛙魔9场。远距未移动角色，三场均先维持潜伏，
超过64次决策后恢复存在并真实施放Emerge；不因无攻击把浮出算成攻击。
三场均在tick1真实施放Submerge、tick1574真实施放一次Emerge；tick1551仍停0且无占位，
tick1601已恢复占位和0x0E，日志断言核验通过。final和final2完整JSONL也完全相同。
对应9场日志和结果发布于 `long-run/`。该目录仍有其他行NOT_RUN，不当全目录长时通过。
长时分支以日志时序和单测的严格65次决策边界共同核验，不改原表概率、不注入SkillDo。

## 重跑

输出目录必须不存在：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
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
  :server:d2gs:test --tests '*MonsterAiArenaTest' `
  --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' `
  :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-frog-recheck --console=plain

.\gradlew.bat :server:d2gs:monsterAiArena `
  '-ParenaMonsters=frogdemon1,frogdemon2,frogdemon3' -ParenaTicks=2200 `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-frog-long --console=plain
```

`logs.zip`含1145个逐场日志，解压到本目录后HTML链接可用。仍有Inferno、其他技能/
MonSeq、资源/回退AI等缺口；未跑全项目测试、三难度多seed完整矩阵及真实地图TCP/GL。
BUILD SUCCESSFUL / COMPLETED / ACTION_OBSERVED均不等于全AI正确性PASS。
