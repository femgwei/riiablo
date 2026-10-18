# 全怪物攻击计时

覆盖目录：当前 enabled、Align=0、npc=false 的 MonStats 行。包含未用于普通地图的控制器、机关、召唤物和闲置数据行；不等于每行都应主动攻击。各场重新创建 World、角色和主体怪物，保留表定义的辅助仆从及已有专用场景。

普通难度，种子 1、2、3，每场最多 1500 帧（60 秒），正式服务端 25 Hz 模拟。基础距离为 near=3、approach=8、far=18；特殊场景可能移动角色或提前结束。far 可能超出感知距离，不能仅因没攻击判定 AI 错误。无真实地图地形、网络延迟、任务状态或完整 Boss 房间条件。

计时口径：

- decisionTick：正式 AI 已创建 Casting/Sequence 动作的帧。未统计进入动作创建之前就失败的意图。Casting.set 分配只用于诊断的唯一编号，组件复用、同技能重复使用及多阶段冲锋不会混淆。
- executionTick / callbacks：正式 SkillDoEvent 回调帧。多段技能只记录第一次回调。回调不等于命中，也不自动保证产生攻击实体或战斗判定。
- meleeAttemptTick：MeleeAttackEvent，包含未命中、格挡；与扣血分开。
- projectileTick：主体拥有的非 presentationOnly 弹道实体插入 ECS 的时刻；按同技能回调及最多 1 帧的 flush 差关联。弹道飞行至命中时间不计入起手。没有确定关联的弹道仍保存在原始日志，不伪造关联。分析脚本对三个种子的原始 ProjectileCreated 日志统一重放此关联规则，原始 JSONL 不改写；排除观察器初版曾把上一次技能的延迟子弹道归给尚未执行的新动作的情况。
- report.html / cases.csv 的 executed、first_execution、median、p95、maximum：仅 A1/A2 普攻的真实近战尝试或弹道创建。callbacks_without_physical 保留“只有回调，没有上述实际证据”的情况。
- targeted_skill_* 单列指向角色的技能回调，其内容可能是诅咒、孵化、潜伏或移动，不能一概当成伤害攻击。S1/S2 等模式下挂着 Attack 的示威动作也单列，不当作 A1/A2 普攻失败。
- CENSORED_AT_TEST_END：测试结束时动作仍进行中，不能判卡死。REMOVED / REPLACED_WITHOUT_EXECUTION 需要结合技能语义复核；例如青蛙潜伏动作原本没有攻击回调。

测试基础设施的单元测试覆盖未命中、连续脉冲、组件复用、冲锋阶段切换、孵化时自删除 Casting、末尾截断、未知回调及弹道创建归因。既有 MonsterAiArenaTest 一并运行。本轮没有修改任何 AI 的概率、动作选择或技能效果。

运行（D2_HOME 指向本地 1.10F 资源）：

```powershell
./gradlew.bat -I build/ai-attack-timing.init.gradle :server:d2gs:test --tests '*MonsterActionTimingTest' --tests '*MonsterAiArenaTest' :server:d2gs:monsterAiArena -ParenaAttackTiming=true -ParenaOutput=build/attack-timing-physical-seed1-20261018 -ParenaTicks=1500 -ParenaSeed=1 --max-workers=1 --console=plain
```

切换种子并使用新的输出目录。init 脚本只将构建输出隔离至 build/attack-timing-check，避免其他 chat 的构建覆盖 class 文件。也可使用正常 Gradle 输出目录运行相同任务。构建期间其他 chat 的未完成修改曾造成编译失败，失败尝试不计入报告；最终采用完整且有 COMPLETED 标记的运行。

```powershell
python tools/ai-arena/attack_timing_report.py <seed1目录> <seed2目录> <seed3目录> --output docs/ai-arena/20261018-all-monster-attack-timing
```

原始逐动作 JSONL 以 gzip 归档；可疑场景日志及测试 XML 另行归档。HTML 内的日志链接指向本机 build 中保留的完整日志。报告属于问题筛查，不是全怪物与原版等价认证，也没有完成真实游戏地图的手工验收。
