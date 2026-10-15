# Inferno Sentry MonSeq / Skills311

## 原版与实现

- D2MOO AiThink.cpp Fn101 / 0x6FCE9D00及HasLostTarget / 0x6FCE9E60：在接受攻击时消耗一次
  射击预算；同次喷射的Do95周期不会重复扣预算。St53/Do95/SetInfernoFrame见SkillMonst.cpp，
  截止从进入序列开始，最后一次Do95在截止时或之后仍实际发射，然后等待InfernoLen结束。
- 本机真实1.10F表：infernosentry / Skill1=mon inferno sentry(311)，Sk1mode=seq_infernosentry；
  十四点均使用S2，point9显示source frame1并action1，其他点source frame0、无action。
  InfernoAnim9 / InfernoLen14；calc2=par1+Wake of Fire基础等级，calc3=3。
- 复用现有AssassinTrapSystem的目标选择、拥有者、协同、伤害快照；不同时创建Actioneer
  Casting，避免两个控制器重复发射。新增序列准备9帧、成功后回卷point8、周期3帧及14帧尾动画。
  AnimStepper让出此控制器的source frame，单机客户端跟随权威source frame；未做GL逐帧验收。
- 4级技能/3级Wake协同时，进入序列deadline18；实际Do95在frame9/12/15/18，frame32尾结束。
  一次预算贯穿准备/喷射/尾动画；不再从首发延长整个deadline，也不再跳过截止点末次发射。
- 冻结停钟；解召、控制器/拥有者死亡、拥有者移除/城镇不继续排队发射。目标死亡但坐标保留
  仍可喷射；位置移除或零坐标、创建失败进入原表尾动画。零方向采用默认方向，避免无限等待。
  单枚导弹保留calc1及协同长度、原生整数速度/Slow Missiles、实际火焰伤害与碰撞。

## 测试与复验

运行`docs/ai-arena/20261015-inferno-sentry-seed1/run-final.ps1`；输出目录须不存在，复跑传`-Output`。
脚本使用真实D2_HOME和独立测试编译/XML目录，不清理共享master其他任务输出。

新增InfernoSentrySequenceTest共7项：真实14点表、自然控制器首发/截止末发/尾动画/预算退出、
失败/目标移除、冻结/解召、状态关闭后的末次发射、死亡目标/新施法重新计时、拥有者及控制器死亡。
时钟测试只mock创建导弹，不注入SkillDo；既有AssassinSkillSpecializationTest继续实际火焰伤害、
免疫与静态墙体碰撞检查，更新首发等待以匹配真实序列，未放宽伤害或碰撞oracle。

fixture-first-failure.xml保留新测试最初缺少Map注入的fixture失败，并非修前游戏行为基线。
sentry-first.log记录更新后的旧专项初次通过。初次扩大相邻回归发现
SummonedPetSystemTest.wolvesReserveSeparateLandingFootprintsDuringWarpAndRegroup在line61失败，
见adjacent-wolf-failure.xml及adjacent-regression-first.log。该fixture只运行动态碰撞及宠物系统，
不运行本次陷阱/动画控制器。本项未修改该测试/落点逻辑，也未将该扩大回归标通过，
未以修前运行证明其为既有失败；该失败仍待独立追查。

真实TCP复验命令：
`./gradlew.bat :server:d2gs:headlessAreaSkill -PareaSkill=272 -PareaTimeout=25 -PareaVerbose --max-workers=1 --console=plain`
使用真实MPQ及自然玩家动画发射，需dual_pass、reconnect_pass且animationFallback=false。
该gate验证两客户端共享导弹/重连，不等于14点序列或整个预算退出均在TCP验收。

## 最终结果

- 最终定向161项通过（core127/8类、d2gs34/3类），失败/错误/跳过0。
  regression-final.zip保存11份XML；扩大相邻狼测试失败单独保留，未混入通过计数。
- 最终及复跑均为557表行/1168场：ACTION504/FALLBACK366/WARNING107/NO_ACTION180/
  MOVEMENT7/TERMINAL4；54个既有专属通过，异常/ERROR/NOT_RUN/SPECIAL_NOT_TRIGGERED0。
  与上一批Horror结果JSONL逐字节一致，SHA256均为
  `35AE2012544E9ADC3F5584AFF88303FF3A17DF7DD16BD980887F3A592BCAB033`。
  最终目录`server/d2gs/build/monster-ai-arena/20261015-sentry-final`，
  复跑`20261015-sentry-repeat`，日志及报告已归档。不是新增召唤陷阱的1168场专属验收。
- 最终真实MPQ/TCP两客户端gate通过：sharedMissiles=[144,146,147,142,143]，两端类型523，
  animationFallback=false；重连active=[147]、stale=false、fullyExpired=false。
  见tcp-final.zip，未声称重连时陷阱已完全到期或本次gate独立验收整个预算耗尽。

## 尚未覆盖

逐怪目录不包含玩家召唤的Sentry，完整目录只是其他怪物的非回归验证；不能把其54个专属场
算成本项Sentry自然专属场。本项专属证据为7项真实表/控制器测试、旧伤害/碰撞测试及真实TCP gate。
通用Actioneer St53白名单仍不接受任意311/任意SQ。其他陷阱动画、Fn101目标节点全顺序、完整
原生RNG/技能等级及三难度多seed/真实GL逐帧仍未全面验收；不宣称完整刺客陷阱等价。
