# Baboon 非近战逃离失败后的近走回退（2026-10-17）

证据：本地 D2MOO source/D2Game/src/AI/AiThink.cpp 的 AITHINK_Fn011_Baboon
在再生、非近战、Escape 返回失败时调用 AITACTICS_WalkCloseToUnit(pGame,pUnit,5)。
AiTactics.cpp 的 AITACTICS_WalkCloseToUnit（0x6FCD0840）以自身整数坐标为中心，
一次 uint32 随机数选轴、一次 limited(5) 选偏移、两次随机数选正负号，
以 WALK、空目标单位、step=1/flags=0 调用 MoveToTarget。
MoveToTarget 在失败且 flags=0 时直接返回0，不循环重试，不改为追踪目标。

此前 Java 在这条分支直接请求玩家坐标并保留玩家目标ID，且随后额外等待1..2秒。
现在发出自身周围坐标请求，目标ID为 INVALID_ENTITY；局部请求失败不再重试或追踪。
按实际接受结果记录 APPROACH/IDLE，保留本次已设置的基础 AI cadence，
移除该回退后额外的全局随机1..2秒等待。
这是消除多加等待，不是原生 ModeChange/事件调度精度的完整还原。

修改前两个测试均失败：预期空目标ID -1，实际为玩家ID 1。
修改后每项内部覆盖种子0/1/2/123456，验证实际请求坐标、总共两次请求
（先Escape再局部近走）、不发技能、接受/拒绝结果、没有额外等待，
以及四次消耗后的下一次单位随机数仍与独立 D2Seed 相同。
原版依据而非当前实现作为坐标算法的来源；测试不证明底层真实寻路结果。

最终：BaboonEscapeFallbackTest 2项 + 既有Baboon再生/恢复与ScarabBaboon追踪11项，
合计13项，0失败/错误/跳过，BUILD SUCCESSFUL。
D2_HOME 使用安装1.10F，隔离脚本 build/ai-baboon-fallback.init.gradle，
日志 build/ai-baboon-fallback-before.log、build/ai-baboon-fallback-after.log。

范围：仅本条回退，未修改共享移动、碰撞或动画代码。
步数1、原版ModeChange失败事件、固定点坐标/寻路精度仍为底层依赖，
未新增自然地图/客户端验收，未运行全AI集，不宣称全部停滞或既有Andariel失败已解决。
