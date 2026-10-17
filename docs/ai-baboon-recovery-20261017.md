# Baboon 再生期间的恢复与逃离判定（2026-10-17）

依据本地 D2MOO source/D2Game/src/AI/AiThink.cpp，AITHINK_Fn011_Baboon
（0x6FCD2A00）再生分支：近战先掷 <33 的攻击判定；只有非近战才会在生命 >75%
时清零倒计时并追踪。此前 Java 在近战未攻击时同样清零并追踪，现补上非近战条件。
此处 33% 使用 rollAiPercentage，避免 LibGDX 全局随机数影响原生分支重放。

D2MOO 以 Escape 函数的布尔返回值判失败，Java 改为消费 walkTo 返回值，
不再从 Pathfind 组件存在性反推成功/失败。底层投影动作可能接受请求但尚无组件，
测试替身也不安装该组件；反过来旧组件也不能证明本次请求成功。
不改变共享移动接口或路径实现。

新测试覆盖三难度高生命近战的逃离、请求接受但没有 Pathfind 时不误回退，
以及请求拒绝但残留 Pathfind 时仍用配置 A2 回退攻击。
修改前三项中两项失败（分别实际 ATTACK 与 APPROACH，预期 ESCAPE），
日志 build/ai-baboon-recovery-before.log。近战旧实现同时依赖全局随机流，
该前后对照不是原版完整 RNG 消费序列等价证明。

边界：非近战逃离失败后的 WalkCloseToUnit 尚被旧直接追踪代替；
逃离候选搜索、原生 path type、其余全局随机调用、等待帧/动作锁仍待。
保留原版高生命近战仍有倒计时时的 bonus 减法时机，不擅自改为一次性清理。
本批不新增自然地图、完整回血/动画/伤害验收，也未证明所有实机停滞已解决。

最终验证：BaboonRegenerationRecoveryTest 3 项、BaboonRegenerationDecisionTest 5 项、
ScarabBaboonPursuitTest 3 项，合计11项，0失败/错误/跳过。
D2_HOME 指向安装的1.10F，隔离脚本 build/ai-baboon-recovery.init.gradle；
日志 build/ai-baboon-recovery-final.log，BUILD SUCCESSFUL。
未运行全AI集，未重验既有Andariel失败，不声称全工程全绿。
