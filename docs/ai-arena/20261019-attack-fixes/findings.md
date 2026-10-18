# 攻击执行问题修复与复测

本轮修复已确认的执行缺口，保留未证实为错误的随机决策。对照基线见 ../20261018-all-monster-attack-timing/findings.md。

修复内容：

- 石像鬼陷阱：SequenceHandler 在初始化时执行 MonSeq 第 0 点事件，对照 D2Common UNITS_InitializeSequence。初始化只执行一次，冲锋的移动点仍不作为攻击处理。
- 爪蛇、爪蛇变种及复生尸：冲锋可以向被目标占据的位置寻找碰撞安全的接近点；带远程普攻的怪物在冲锋时不再按射程提前停止。按 SKILLS_SrvSt31_Charge 使用 MonStats.Run 和 Skills.Param1 加速度百分比，而非普通行走 Velocity；整数精度与普通坐标移动均接入该计算。没有放宽碰撞或修改攻击概率。
- 旧爪蛇 AI：活动 Casting/Sequence 阻止后续决策覆盖冲锋；敌人订阅改为实例字段，避免跨 World 复用。
- cr_archer6/7：缺少普通弹体时不走即时 A1 分支，改由已有技能选择逻辑射箭；被拒绝的技能不再退回空普攻。活动射击期间不重建动作。保留普通弹体弓箭手的现有射击分支，并复测其远距离射击。

最终验证使用独立输出目录、串行构建：557 个变体、110 种 AI 的种子 1 全量测试 1,757 场，加 29 个相关变体、6 种 AI 的种子 2/3 重点复测 184 场，合计 1,941 场。普通难度，每场最多 1,500 帧（60 秒）；本轮没有对全部变体重跑三个种子。48 个 core 测试与 22 个 d2gs 测试全部通过。

三种子重点结果：

- 石像鬼陷阱：201 次决定全部进入技能执行，原先为 0；原始日志确认实际创建 201 个弹体，决定至执行为 1 帧。弹体日志 skill=-1，因此通用报表不会把它们归到 skill=172 的 physical_actions，不能把该列的 0 理解为没有弹体。日志还保留通用技能处理的 Unsupported srvdofunc(93) 警告；本轮没有声称完整重写该技能的原版瞄准函数。
- ClawViper：3 次冲锋全部执行；ClawViperEx：15 次冲锋全部执行，最长 9 帧（0.36 秒）。基线共 162 次冲锋未执行；修复改变了怪物到达近战后的决策，所以修复前后的冲锋总次数不相同。
- ReanimatedHorde：22 次冲锋全部执行，最长 19 帧（0.76 秒）；基线有 4 次未执行，成功动作最长 138 帧。没有通过缩短超时或提前判定命中消除失败。
- cr_archer6/7：原来的 138 次空普攻消失，三种距离、三个种子共确认 506 次真实技能箭弹体，空普攻为 0。测试结束时未完成动作仍单列截尾。
- 食腐鸟：本轮没有改变 Vulture 的概率和等待。地面盘旋、起飞条件、飞行倒计时、近距离提前降落和攻击概率已对照 AITHINK_Fn023_Vulture。vulture1 在 8 格场景的首次实际攻击仍为 138/162/490 帧，决定后都是 5 帧；种子 1/3 的 18 格场景仍可能 60 秒无攻击。专用飞行落地攻击场三个种子均通过。它的接敌延迟尚未证明是实现错误，也没有验证用户实际地图中的停顿已消失。

全部 1,941 场共确认 60,965 次真实普通攻击；中位数/P95/最大起手为 8/16/21 帧。0 普攻动作在关键帧前丢失，337 次测试结束截尾。保留 119 场 WARNING；没有运行时错误或 GenericMonster fallback，不把 ACTION_OBSERVED 当作完整正确性通过。

剩余 38 次普攻回调缺少物理证据来自 Diablo/Clone 和 SandLeaper，与基线种子 1 的对应条目一致，本轮没有判为已修复。另 3 次 targeted_skill_failed 是 FrogDemon 专用浮出场中的潜伏保持动作，仍按既有语义保留。不能据此宣称所有怪物 AI 已无问题。

逐场统计见 cases.csv，原始计时见 seed1-results.jsonl.gz（全量）与 seed2/3-results.jsonl.gz（重点变体，未运行的条目为 NOT_RUN）。evidence.zip 保存相关怪物日志和 70 个测试的 XML；manifest.json 保存归档及提交源码（Git blob 内容）SHA256。完整运行输出保留在 build/attack-final2-all-seed1 和 build/attack-final-focused-seed2/3。isolated.init.gradle 保存构建隔离和测试来源限制；运行参数为 -ParenaAttackTiming=true -ParenaTicks=1500 -ParenaSeed=1/2/3，后两个种子通过 -ParenaMonsters 限制到上述 6 种 AI。

这些结果来自正式 AI、动画、技能和碰撞系统组成的合成场景；未代替正常游戏地图的人工复测。
