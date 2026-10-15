# Blade Fury / Blood Mana — PARTIAL

依据 `SkillAss.cpp SKILLS_SrvDo048_BladeFury / SKILLS_SrvSt26_BladeFury`：
初次启动按真实startmana检查法力；INFERNO状态续放绕过该启动门。
仅接受间隔后的飞刃请求，SrvDo048先创建飞刃，再调用技能消耗，且调用者忽略消耗返回值。
本批保留既有飞刃/伤害快照/间隔，在玩家受Blood Mana影响时把独立每刃消耗转为生命。
生命不足时，已创建飞刃和间隔保留，诅咒解除、生命设1；等于消耗时允许扣至0。
未生成飞刃、间隔拒绝、初次提交均不扣生命。普通未诅咒飞刃仍扣法力。

自然场发现Actioneer对已由ServerSkillSystem处理的SrvDo48误报unsupported；
将48加入已有事件分派的明确no-op列表，保留后续正常SkillDo，不过滤警告日志。

## 证据

- `baseline-tests.log` / `baseline.xml`：最初8项测试5失败；其中“不足生命”错误使用1HP。
  真实1级飞刃消耗为1点，因此1HP是等额边界，不是不足边界。
- `fixed-tests.log`：首次修后跑法暴露上述测试假设，保留日志。
  修正不足生命为0.5HP，恢复修前ServerSkillSystem后重跑，
  `baseline-corrected-tests.log` / `baseline-corrected.xml` 保存有效修前8项结果。
- `d0-baseline*`：七行×近/远/blood-mana/blade-blood-mana共28次修前自然执行。
  七个飞刃专用场均自然挂上Blood Mana，普通玩家提交启动不扣资源，动画动作点生成飞刃
  后仍错误扣1法力、生命不变；每场一条SrvDo48误报warning。
- `final-tests.log`：纠正生命边界后，新飞刃测试通过；167项core联合测试中
  Nova的50%闪电抗性碰撞断言仍失败（目标生命1000），联合套件不通过。
  首次`fixed-tests.log`也保留了同一Nova失败。本批没有修改该测试或随机概率。
- `isolated-regression-tests.log` / `isolated-assassin-tests.log` / `TEST-*.xml`：
  将刺客套件与其余回归分到独立Gradle测试进程，以检查共享状态影响。
  这些分组结果不能替代上述联合套件；最终分组统计由XML和`verification.log`核验。
  最终分组共181项通过（129项非刺客组、52项刺客组），其中8项为本批新测试。
- `d[0-2]-first/repeat*`：三难度、七行、四场及独立复跑，共168次最终自然执行，
  包括42个飞刃扣血专用场；逐场日志及JSONL复跑核验见`verification.log`。
  最终JSONL和逐场日志全部复跑一致，42次均新生成一枚飞刃后扣1生命、法力不变；
  168次最终自然执行均无fallback/警告/错误。

专用场沿用上一批真实Blood Mana玩家生命/最大法力设置，改为刺客并学习真实飞刃及
表内前置技能。仅一次普通Actioneer输入，目标为玩家正北16单位空地，避免飞刃杀掉
正在验证的怪物。怪物表、概率、RNG、碰撞不改，不直接添加自然场诅咒或强制SkillDo。
日志分别记录启动时资源、动画SkillDo前后资源、该段其他敌人伤害及新飞刃数量，
使飞刃自耗不与敌人攻击混淆。自然场仍BASELINE_ONLY。

默认目录新增七个场后journal为1194条，每轮执行筛选的28条，其余1166条NOT_RUN。
`verify.ps1` 核对修前缺陷、真实curse/正常SkillDo/新飞刃、资源差值、诊断、复跑、
未选计数、测试XML及源码快照一致性。

## 复现与限制

固定基准提交见`source-base-sha.txt`，`prepare-source.ps1`复制四个本批生产/测试文件，
`-Repaired`应用最终ServerSkillSystem/Actioneer；修前也复制场景观察代码。
buildDir、JavaCompile/sourceSet输出与project cache隔离；输出拒绝覆盖。
验证仅包含基准提交及本人修改，未集成共享工作区其他聊天未提交修改。
分组回归使用 `:core:test --tests '*BladeFuryBloodManaIntegrationTest' --tests '*BloodManaIntegrationTest'
--tests '*Succubus*Test' --tests '*NecromancerCurse*Test' --tests '*SorceressNovaIntegrationTest'
--tests '*Aura*Test' --tests '*ServerSkillSystemTest'` 和 `:server:d2gs:test --tests '*MonsterAiArenaTest'`；
另一独立Gradle调用只运行 `:core:test --tests '*AssassinSkillSpecializationTest'`。
每次XML先归档再运行下一组，避免将旧的成功结果误当作新进程结果。

本批未重做原版完整SrvSt26/INFERNO全局调度、持键输入/技能参数生命周期及每个
SrvDo动作点。当前架构仍在既有技能事件阶段生成飞刃并消费生命；没有声称整条刺客
持续技能链与原版完全等价。客户端低法力输入门、物品充能、实际光环自然战斗、
等额扣至0后的死亡/网络同步、真实地图/GL/TCP及多seed仍待。
Nova联合套件失败的根因尚未确认，需进一步审计全局随机状态与低伤害取整；
本批不能报告全套件通过。整体保持PARTIAL。
