# 圣甲虫与狒狒停滞排查（2026-10-17）

本批优先处理 Scarab、Baboon。决策依据为本地 D2MOO
`source/D2Game/src/AI/AiThink.cpp` 的 AITHINK_Fn020_Scarab 与 AITHINK_Fn011_Baboon：
均消费 AI 核心提供的 pTarget，而不是在处理器内只扫描带 Class 的玩家。

修复两者额外的 Class + Player 订阅，改用公共 findNearestTargetWithAidist，
保留公共敌对、存活、区域、aiDist 与特殊目标判定。Baboon 同时移除静态订阅，
避免后续 World 读取已经销毁 World 的目标列表；恢复阶段的第二处扫描一并修改。
不修改共享移动/碰撞/动画/伤害执行链。

验证：隔离构建目录 build/ai-bighead-check，初始化脚本 build/ai-pursuit-check.init.gradle，
D2_HOME 指向安装的 1.10F。新测试验证三难度、没有 Class 的合法玩家会发出
带正确目标 ID 和坐标的追踪请求，以及换 World/不同实体 ID 后重新选敌。
修改前测试确实失败（包含 world 1 仍 WANDER）；修改后 3 项通过，0 失败/跳过。
日志 build/ai-pursuit-baseline.log 与 build/ai-pursuit-all.log。

真实服务器合成 arena：Normal、seed 1、每场 500 tick，baboon1..8 与 scarab1..7
共 30 个近/远场全部 ACTION_OBSERVED；全部 15 个远场均有实际位移。
其余 1165 场 NOT_RUN，不计作通过；特殊技能 oracle 未实现，不声明全分支正确。
输出 build/ai-scarab-baboon-all-arena。baboon1/scarab1 对照原 AI 源码运行的四场也有动作，
因此该合成场景没有复现用户报告的自然地图停滞；本批选敌修复不是已证明的实机根因。

工作区同时存在其他聊天的底层 Position/移动改动（部分在测试期间已提交）。
前后对照仅替换这两类 AI 源码，公共执行链保持当前状态；上述 arena 结果不能单独归功于本批。
仍需用户实机地图/难度/距离与受击后反应，或自然地图日志来定位剩余停滞。
Baboon 再生、受击特殊分支，Scarab minion 命令/jab 及等待仍有旧占位，本批未声称完成。

自然地图补充：Normal/seed1 的 level 42、43 TCP 巡游完成（1m21s），
但日志未出现 Baboon 或 Scarab，因此不构成这两类的自然地图验证。
输出 build/ai-scarab-baboon-natural；不以地图 PASS 冒充目标怪物已验证。
