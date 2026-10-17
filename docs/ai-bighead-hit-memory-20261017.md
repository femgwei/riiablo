# Bighead 受击记忆决策入口核对（2026-10-17）

本批仅修复 Bighead 的 `checkSpecialAiState` 永远返回 false 的问题。
依据本地 D2MOO `source/D2Game/src/AI/AiUtil.cpp` 的 `sub_6FCF2E70`
（0x6FCF2E70）：仅 `MONSTER_GetAiState` 为 3 或 19 时返回真。
`AiThink.cpp` 的 `AITHINK_Fn004_Bighead`在非近战时优先据此发出 A2。
使用现有 NativeAiState 组件读取所属实体记忆，不将 Terror/Taunt 当作这两个值。
本批不修改移动、碰撞、动画、伤害或共享接口。

回归覆盖状态 0–31、组件缺失、中立保留受击记忆、攻击替换记忆。
这是决策条件回归，不是完整命令链、原版 DLL 或自然地图逐帧等价证明。

尚未完成：Bighead 的即时攻击逻辑会抢在正常概率/等待判断之前执行，
健康远程分支的 sub_6FCF2CC0 选敌条件、精确等待和失败重试仍需单独移植。
Baboon、QuillRat、SkeletonBow、Vampire、Zombie 仍有同类状态占位；
Baboon 还缺再生 bonus 加减，不直接启用后声称完整。
37 类处理器已存在不等于逐类决策完成，本批没有完成全清单验收。

验证：默认测试入口在 compileTestJava 被无关资产测试缺失类型阻挡，未执行测试。
使用 build/ai-bighead-check.init.gradle 隔离构建输出并仅编译本批测试后，
`:core:test --tests '*BigheadHitMemoryTest' --max-workers=1` 构建成功，
2 项测试通过，无失败或跳过；日志 build/ai-bighead-isolated.log。
没有运行全 AI 测试集，既有 Andariel 三项失败未重新验证或宣称修复。
