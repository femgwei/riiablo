# Scarab 追踪阶段与 Jab 决策（2026-10-17）

依据 D2MOO source/D2Game/src/AI/AiThink.cpp 的 AITHINK_Fn020_Scarab：
AiControl.dwAiParam[0] 是普通追踪阶段，与 AIGENERAL_GetCurrentAiCommandFromUnit
返回的命令是两种独立状态。原实现将前者等于 1 当成攻击命令，导致普通第二次追踪
安装 +100% 速度，且近战跳过概率直接用普通 A1。
删除这个错误别名入口；真正的队列命令/首领分发仍待独立接入，不声称已实现。
普通阶段清除条件按原始 AI_RollPercentage > 10 恢复，使用现有原生 AI RNG。

Jab 只能在 attack chance 成功后、Skill1 有配置且 jab chance 成功时选择。
原实现把 Jab 放在 attack chance 失败分支中，且仍发出普通 attack。
现在使用 useMonsterSkill(0) 发出配置技能、目标与 MonSeq 命令；拒绝攻击后按 Param3 等待。
技能执行、MonSeq、碰撞、伤害均使用现有共享实现，本批没有修改这些底层。

验证使用 D2_HOME 的安装 1.10F 数据、隔离输出 build/ai-bighead-check，
build/ai-scarab-branch.init.gradle 只选择本批及追踪测试与公共夹具。
修改前两项回归均失败：预期追踪加成为 0 实际为 1，预期待机实际为 Jab。
日志 build/ai-scarab-branch-before.log。
随后成功 Jab 回归首次暴露旧公共 assertSkill 夹具不适合 MonSeq（预期 SQ=14、
实际安装序列基准 A1=4）；改为断言技能 ID/目标/坐标、nativeMonster 与其基准模式，
没有放宽生产代码或技能 ID 断言。

范围边界：未修复普通追踪的 type/步数、完整动作锁/等待调度、无目标待机、
原生队列命令、首领分发或 Jab 的伤害执行。没有新增自然地图或客户端验收，
不能用本批决策修复声称用户报告的全部停滞已解决。

最终定向验证：ScarabDecisionBranchTest 3 项与 ScarabBaboonPursuitTest 3 项，
共 6 项通过，0 失败/跳过。日志 build/ai-scarab-branch-verified.log。

真实服务端补充：Normal/seed1、500 tick，scarab1、scarab7 的近/远四场均产生动作，
但全部为 WARNING，不作为通过验收。日志包含 AMAZON_JAB_ANIM 与
SCS1HTH 的 COF/keyframe 缺失；正确发出 Skill1 后暴露共享执行链中的怪物 Jab/
亚马逊 Jab 动画分派缺口，需负责执行链的聊天另查，不能通过改回普通攻击掩盖。
输出 build/ai-scarab-branches-arena，完整日志 build/ai-scarab-branches-arena.log。
本批未修改共享执行代码，未声称该技能动画或伤害已正确。
