# Baboon 普通近战与 A1/A2 选择（2026-10-17）

本批为最新结论，修正此前 recovery 测试/记录中把 Param4=100 解释为 A2 的错误。
本地 D2MOO source/D2Game/src/AI/AiThink.cpp 的静态辅助
AITACTICS_ChangeModeAndTargetUnitToAttack1Or2（约237行）明确：
AIRollChanceParam 成功选择 MONMODE_ATTACK1，失败选择 MONMODE_ATTACK2。
Baboon 原实现所有位置都反过来，本批统一修正为命中 A1、未命中 A2，
并以原生 AI 随机流代替这些 A1/A2 选择处的全局随机数。
新增0/100端点断言与固定种子37%断言，并修正原有恢复回退断言，未隐藏旧断言错误。

Fn011 普通近战：aiParam1=0 时强制第一次攻击，不掷攻击概率；
后续攻击掷 Param3，A1/A2 用 Param4。失败后掷 Param2 决定是否绕行。
即使绕行成功，函数最后仍无条件调用 IdleInNeutralMode(15)。
原代码此处把绕行替换成直接追踪玩家并早退，等待随机1..2秒。
本批改用已有 findNativeOrbit(target,3,direction)，消耗原版低字节方向随机数，
随后停止移动、诊断状态转IDLE、设置15帧的等待值；不擅自移除原版尾部等待。
未新增或修改共享路径/动画/伤害实现。

修改前首批三项均失败：普通攻击A1/A2不符、未发原生绕行请求、
未选择绕行时也没有发停止移动请求/原生概率消耗不符。
日志 build/ai-baboon-melee-before.log；扩展原版辅助证据后另补端点断言。

边界：15帧只验证所设置的等待值，Baboon仍使用旧浮点倒计时，
未声称精确第15帧唤醒或完整 ModeChange 中立动画等价。
受击状态20%绕行、再生远距33%绕行仍有旧投影/全局随机逻辑，留待后续。
原版单位RNG与本项目初始化种子仍不同，固定种子测试只证明分支消费与选择规则。
未新增自然地图/图形验收，未运行全AI集，不宣称所有实机停滞或Andariel失败已解决。

最终验证：BaboonMeleeDecisionTest 4项，加既有再生/恢复/局部回退/追踪13项，
合计17项，0失败/错误/跳过，BUILD SUCCESSFUL。
D2_HOME 指向安装1.10F；隔离脚本 build/ai-baboon-melee.init.gradle，
最终日志 build/ai-baboon-melee-final.log。三难度普通绕行/待机，固定种子首次攻击
与0/100端点均有覆盖；其余未验证范围保持上述限制。
