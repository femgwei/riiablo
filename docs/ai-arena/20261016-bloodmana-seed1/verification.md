# Blood Mana life consumption — PARTIAL

原版依据：`SkillDruid.cpp D2GAME_SKILLS_BloodMana_6FD025E0`，
`Skills.cpp D2GAME_SKILLMANA_Consume_6FD10A50 / AuraConsume_6FD10C90`；
`SkillPal.cpp` 中有用光环脉冲先置 NOMANAREGEN 再调用 AuraConsume，忽略该返回值。

本批将服务端普通 SkillCast 消耗和玩家光环脉冲结算接到 Blood Mana 生命消耗：
保留8位小数、生命不足时移除状态/设为1HP/拒绝消耗，等于消耗时允许扣到0；
扣血后严格低于诅咒来源技能 Param5 才解除，法力不变。
光环预检不修改生命，也不阻止结算时的不足生命清理。
未受诅咒的普通施法/光环仍走原有法力路径，非玩家光环不套用本批玩家扣血逻辑。

## 测试及证据

- `baseline-tests.log`：最初测试工厂未实现抽象方法，编译失败，不计行为证据。
- 修正测试工厂后 `baseline-corrected-tests.log` / `baseline.xml`：10项、7失败、3通过。
  后续增加两个正常/非玩家光环控制测试，最终新测试共12项。
- `d0-baseline*`：七个怪物各有近/远/blood-mana场，共21次修前自然执行。
  Blood Mana由怪物自然施放，玩家随后通过Actioneer施放真实冰封装甲，动画动作点
  产生SkillDo；七个专用场都重现“生命不变，法力扣7”的错误。
- `fixed-tests.log` / `TEST-*.xml`：最终121项定向/相关回归全部通过，
  包含12项新边界/ECS测试、Succubus/诅咒、Nova、光环及服务端测试场回归。
  这不是项目完整测试套件。
- `d[0-2]-first/repeat*`：最终七行×三场×三难度×独立复跑，共126次执行；
  其中42个blood-mana场核验真实状态、普通玩家施法、立即资源变化及动画SkillDo。
  每次预期生命扣7、法力不变。核对结果见 `verification.log`。
  最终126次执行的JSONL及逐场日志均复跑一致，42次真实扣血核验通过，
  零fallback/警告/错误。

新夹具仅把专用场玩家换为女巫、最大法力设为2000000（最大生命1000000）、
当前法力100，并学习1级冰封装甲。这样真实Fn118进入Blood Mana分支；
不修改怪物表/概率/RNG，不直接添加自然场诅咒，不强制SkillDo或关闭碰撞。
挂上自然诅咒后只请求一次普通玩家施法；立即记录资源，避免生命再生及敌人伤害
混入扣血差值。后续SkillDo由正常动作序列产生。

默认目录新增七个场，journal变为1187条；筛选这七行时执行21条，
其余1166条NOT_RUN不能计作通过。自然场保持BASELINE_ONLY，未伪造全AI通过。
`verify.ps1` 核对修前缺陷、修后资源差值、真实状态/事件、复跑JSONL与逐场日志、
错误/警告/fallback、未选场数、测试结果及源码快照一致性。

## 复现与边界

固定提交见 `source-base-sha.txt`，使用 `build/bloodmana-source` 源码快照，仅覆盖
本批五个生产/测试文件。`prepare-source.ps1` 创建修前快照，`-Repaired` 覆盖修后文件；
当前脚本使用最终12项测试，修前应有相同7项行为失败，非原始10项的完全重放。
构建目录、JavaCompile/sourceSet输出与project cache均隔离；自然输出拒绝覆盖。
`run-arena.ps1 -Pass first/repeat -Difficulty 0/1/2` 运行最终矩阵。
共享工作区其他聊天的修改未纳入快照集成验证。

仍未验收：客户端低法力输入门、物品充能、Blade Fury等独立消耗入口、
完整技能动作点消耗时机、实际光环自然战斗脉冲及真实TCP/GL。
当前普通技能沿用已有服务端Cast验证时消耗的时机，本批不声称完全等价于原版
每个技能的SrvDo时机。等额生命扣到0按原生函数保留，不额外派发伤害或死亡事件；
后续死亡/同步全链还需专项验证。
Succubus无目标行为、共享RNG/全局调度及真实地形仍未完成，整体保持PARTIAL。
