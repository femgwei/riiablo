# FrozenHorror / Horror Arctic Blast

## 实现与原版依据

- D2MOO `source/D2Game/src/AI/AiThink.cpp` Fn124 / `0x6FCE25D0`：技能存在、
  距离严格小于技能等级、第三参数概率且无INFERNO状态时优先施法；否则清除INFERNO，
  再走近战概率、非近战接近概率和第四参数停顿。保留原表概率，不在场景强制技能。
- `SkillMonst.cpp` St53 / Do95 / SetInfernoFrame：截止、周期、实际创建失败收尾；
  `Skills.monanim=xx` 是native序列模式14，成功发射后sequence point设为InfernoAnim-1。
  普通非SQ消费者仍使用原source-frame分支。独立ENDANIM时钟不被COF结束事件替代。
- `D2Common/src/Units/Units.cpp` UNITS_InitializeSequence / 更新函数：初始sequence speed256，
  独立sequence cursor和14点循环。此处只实现已核实的Horror映射，不宣称通用MonSeq。
- 本机真实1.10F Patch_D2原表：五种FrozenHorror，Skill1=323、Sk1mode=seq_horrorarcticblast；
  十四个A2点0..13，point9 action1；InfernoAnim9、InfernoLen1，技能calc3=2。
  对原表逐点断言见HorrorSequenceDataTest。
- 客户端跟随权威source frame。普通导弹命中DamageEvent补充missileId以核对伤害来源；
  未改变伤害值、碰撞、导弹概率或技能配置。

## 验证方法

在仓库根目录执行 `docs/ai-arena/20261015-horror-seed1/run-final.ps1`。
脚本设置真实D2_HOME，使用独立测试类及XML目录，避免共享master其他Gradle任务清理结果。
core用JUnitPlatform，d2gs用JUnit4。完整目录为seed1、普通难度、每场500tick。

- 新增3项动画/持续链集成、1项真实数据、4项AI测试；涵盖优先级、严格距离、忙碌保护、
  INFERNO清除/重新施法、失败不改普通攻击、冻结停钟、忽略COF marker、SQ回卷、死亡取消。
  AI分支测试会使用0/100概率隔离分支；生产场景不改概率。三难度仅用于AI分支测试。
- 新增五场自然inferno-cycle，要求真实导弹、周期、截止末发、尾动画、状态清除后新施法，
  并核对owner/skill323的真实寒冷伤害包和实际HP扣减。不能用普通近战伤害满足专属oracle。
- 基线失败：baseline-failure.xml（未接入323，持续链为null）。
  strict-first-failure.zip保留严格命中来源检查初失败：旧普通DamageEvent没有missileId，
  因而无法确认伤害身份；修补诊断字段后来源、寒冷包与HP均可核验。
- full-before-sequence-seek.zip / first-full-results.jsonl保留最终SQ回卷修正前的完整首轮证据。
  不是最终代码验收；最终及复跑另存。

## 最终结果

- 定向478项通过：core444项/58类、d2gs34项/3类，失败/错误/跳过0。
  XML见regression-final.zip。regression-and-existing-output.log的最终Gradle失败只因arena
  拒绝复用已有输出目录；全部JUnit通过。随后以新目录单独运行arena成功，未删旧证据。
- 最终完整目录557行/1168场：ACTION504、FALLBACK366、WARNING107、NO_ACTION180、
  MOVEMENT7、TERMINAL4；异常、ERROR、NOT_RUN、SPECIAL_NOT_TRIGGERED均0，54专属通过。
  五个新增Horror自然场全部通过。far场无动作按原表保留，不强制提高接近概率。
- 与上一批1163场逐字段比较：1051完全相同、112变化、新增5、丢失0。
  其中10条Horror近/远场由GenericMonster变为FrozenHorror；另外102条非Horror变化与当前
  master近期并行路径更新共同存在，不能归因或算成本项验收。详见comparison-final.txt。
  剩余46个AI名称/183表行仍fallback；107场WARNING并未消警。
- 最终输出：`server/d2gs/build/monster-ai-arena/20261015-horror-refined-final`；
  复跑使用独立`20261015-horror-repeat`。最终报告、JSONL及1168场日志归档保存。
  两次JSONL SHA-256一致：`35AE2012544E9ADC3F5584AFF88303FF3A17DF7DD16BD980887F3A592BCAB033`。

## 后续边界

此项仅完成FrozenHorror专用AI及其Horror MonSeq，不包括mon inferno sentry(311)。
Inferno Sentry已有独立AssassinTrapSystem，后续须核对序列、所有者协同等级、预算及生命周期，
不能仅放开St53白名单。完整Fn051/Fn065、Megademon专用AI、其余fallback和通用序列仍未完成。
合成地图headless/真实表验证不等于真实TCP、GL、全项目或三难度多seed验收。
