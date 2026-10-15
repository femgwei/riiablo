# 本机复测证据：MonInferno 的非 MonSeq 分支

2026-10-15，Windows，本机1.10f MPQ：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。
修改前代码基线 `18a5cc7b`；测试期间新增的地图测试提交 `af737dc5` 保留，
仅涉及地图资源测试及地图文档，不属于本次 AI 修改。

## 原版依据

D2MOO `8322494ed1f715ad51552f169df76cf600fabc71`，直接核对源码和本机原表：

- `SkillMonst.cpp::SKILLS_SrvSt53_MonInferno`：Param1为当前帧加max(1,calc2)，
  INFERNO是布尔开关，不是把calc2当作StateList自动到期时间。
- `SKILLS_SrvDo095_MonInferno`：创建SrvMissileA成功之后，才判断deadline及INFERNO；
  因而已排程的末次回调仍创建一发。继续时max(1,calc3)后MODECHANGE；
  到期/状态关闭或目标坐标/创建失败时取消后续回调，改为InfernoLen后的ENDANIM。
- `SKILLS_SetInfernoFrame`：本轮非SQ仅seek源帧至InfernoAnim，不改COF帧数；
  普通COF事件被持续链接管后不重复触发，结束时钟独立于动画循环。
- `SKILLS_UpdateInfernoAnimationParameters`：calc1非正时使用Missiles.Param2+level-1，
  clamp1..255，作为真实帧寿命；飞弹动画速率为(AnimLen<<8)/寿命。
- `MonsterMode.cpp::sub_6FC64790`、`SUnit.cpp::sub_6FCBCE70`：首个模式更新来源于
  真实COF动作点，后续95自己重排MODECHANGE，不靠注入MonSeq/SkillDo伪造首发。
- `Skills.cpp::D2GAME_GetXAndYFromTargetUnit_6FD14020`：优先目标单位坐标，
  否则取路径首点；X/Y任一为0失败。坐标查询本身不检查目标HP。
- `Missiles.cpp::MISSILES_CreateMissileFromParams`：显式Vel+level*VelLev/8仍受CanSlow/
  SLOWMISSILES影响。本轮保留项目现有速度单位；原版后续75%及路径单位转换尚未全局对齐。
- `AiThink.cpp::AITHINK_Fn065_FetishShaman`：距离严格小于技能等级才能开始Inferno，
  下一次决策遇INFERNO先关闭。此次只修严格边界、开关消费和跨World订阅残留；
  不宣称完整Fn065复活筛选/指令/决策时钟已移植。
- `AiBaal.cpp`动作12明确使用S1施放Baal Inferno，不从其他技能槽猜模式。

原表六个St53消费者：FetishInferno178、MegademonInferno319、mon inferno sentry311、
DiabLight193、Horror Arctic Blast323、Baal Inferno317。
本轮仅178/A1、319/S1、317/S1接入95时钟；193的152及311/323的MonSeq仍明确告警，
不增加空case消警，也不改玩家St11/Do19或独立AssassinTrapSystem持续链。

## 实测与复现

发布源目录：`server/d2gs/build/monster-ai-arena/20261015-inferno-final3`。
Normal、seed1、每场500个25Hz tick；输出目录必须不存在：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
.\gradlew.bat :server:d2gs:monsterAiArena `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-inferno-recheck --console=plain
```

仅复跑本轮主体（其余行会NOT_RUN，不能当全目录通过）：

```powershell
.\gradlew.bat :server:d2gs:monsterAiArena `
  '-ParenaMonsters=fetishshaman1,fetishshaman2,fetishshaman3,fetishshaman4,fetishshaman5,fetishshaman6,fetishshaman7,fetishshaman8,baalcrab,baalclone,megademon1,megademon2,megademon3,megademon4,megademon5,venomlord' `
  -ParenaOutput=server/d2gs/build/monster-ai-arena/my-inferno-targeted --console=plain
```

完整557行/1153场：ACTION_OBSERVED480、AI_FALLBACK376、WARNING111、NO_ACTION175、
MOVEMENT_ONLY7、TERMINAL_OBSERVED4；运行时异常/ERROR_LOG/NOT_RUN/专属未触发均0。
8场新增inferno-cycle全部INFERNO_CYCLE_OBSERVED，18场复活、6场触手循环、3场蛙魔浮出
和4场无主人死亡仍成功。WARNING较旧103增加8，来自新增场的辅助FetishBlowgun回退，
没有将专属成功强行覆盖WARNING。fallback仍是47个AI名称/188行，Megademon也仍fallback。

与`20261015-frog-seed1/results.jsonl`按monster/scenario比较全部字段：31个既有场变化，
均属FetishShaman16场、Megademon/venomlord12场、Baal3场；1114个既有场完全一致，
新增8场，无旧场丢失。full与final2完整JSONL相同；final3再次核验，见复测日志包。

专属oracle只接受主体自然AI施法：同一Casting里实际分配的飞弹owner/skill/寿命有效，
至少两发、calc3间隔正确、deadline末次发射、InfernoLen尾结束，再观察到新一轮施法。
不改原表概率、辅助单位不计主体、无强制技能事件、未关闭碰撞。
例如fetishshaman1：tick10/13/16三发，deadline15、间隔3；tick30尾结束、tick44新施法。
Megademon首轮tick9/11/13/15/17五发，尾长1；Baal far在tick311..321每2tick一发。

回归覆盖原表和真实COF入口、周期/最后一发/尾时钟、创建失败、死亡/移除目标、真实路径首点/
零坐标、施法者死亡、冻结、复用重置、SlowMissiles、本地动画与飞弹动画速率，
并回归前三批AI、玩家Inferno黄金伤害、SorceressFireArea、AssassinSkillSpecialization、
NativeAssassin矩阵、MissileNativePolicy及arena/巡图日志。最终295项（core265、d2gs30）
全部通过，失败/错误/跳过0，36个suite的XML见`regression-final.zip`；早期定向记录另存
`regression-earlier.zip`，不与最终数重复累加。三次完整JSONL SHA256均为
`46B48102EA6325A99ABB99D3ED05B0373660049FA243A5151C7DF32AA0AF77F9`。

扩展检查还暴露一个无关失败：
`BaalCrabSummonTargetLifecycleIntegrationTest.deadWallAndRetainedCursesNeedNewNodeInsertionBeforeNode9Fallback`
期望2实际0，line317。在未改动的18a5cc7b独立worktree复现同一失败，
XML保存在`baseline-known-failure.xml`。未修改断言或在本轮修目标链；临时worktree已归档可恢复。
本轮定向通过不代表全项目测试通过。

本轮新增链路与邻接技能可单独回归（最终295项还包含前三批的回归集）：

```powershell
.\gradlew.bat :core:test `
  --tests '*MonInferno*Test' --tests '*FetishShamanInfernoAiTest' `
  --tests '*AnimationStepperTest' --tests '*InfernoGoldenDamageTest' `
  --tests '*DiabloAiIntegrationTest' --tests '*BaalCrabAiIntegrationTest' `
  --tests '*BaalCrabCloneAiIntegrationTest' --tests '*MonsterAiParamParityTest' `
  --tests '*AssassinSkillSpecializationTest' --tests '*SorceressFireAreaIntegrationTest' `
  --tests '*NativeAssassinSkillMatrixTest' --tests '*NativeSorceressFireAreaDataTest' `
  --tests '*MissileNativePolicyTest' `
  :server:d2gs:test --tests '*MonsterAiArenaTest' `
  --tests '*MapTourAiLogTest' --tests '*MapTourProbeTest' --console=plain
```

## 明确保留的边界

- Diablo Do152、寒冰恐魔/哨兵MonSeq、完整Fn065及Megademon专用AI仍未完成。
- 冻结沿用项目现有动作时钟暂停；全局原生事件队列/慢速下首动作点量化、完整飞弹路径/
  速度单位与整数伤害、目标指针回收保护仍非逐帧原版验收。
- 本地动画已跟随权威回卷，TCP独立客户端的持续链表现/飞弹元数据同步仍需另一电脑验证。
- 本次没有全项目测试、三难度多seed矩阵、真实GL/TCP地图验收；空地动作不等于AI正确性PASS。

`logs.zip`含1153场日志；解压到本目录后`report.html`内日志链接可用。旧快照保持不变。
