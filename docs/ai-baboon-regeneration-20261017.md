# Baboon 受击再生决策（2026-10-17）

依据本地 D2MOO source/D2Game/src/AI/AiThink.cpp 的 AITHINK_Fn011_Baboon
（0x6FCD2A00）、AiUtil.cpp 的 sub_6FCF2E70（0x6FCF2E70）。

修复内容：
- 接入 NativeAiState 的 3/19 受击记忆，不使用 Terror/Taunt 代替。
- 当前/最大生命分别取值，避免 attrs.get 返回的临时 StatRef 被第二次查询覆盖，
  导致低生命始终读成 100%；百分比按整数截断。
- 低生命、近战、受击记忆成立且原生百分比 <50 时开始 2..6 次决策倒计时。
  倒计时随机数使用原生 uint32 随机数 %5+2。
- 再生 bonus 为当前 hpregen * Param5 /8（整数），同时写入 base/aggregate；
  倒计时归零或整数生命百分比 >75 时减去保存的 bonus，保留其他加成。
- 修正两处逃离坐标的 Vector2 自别名错误：原先计算方向后又以同一个 tmpVec2
  覆盖方向，导致落点依赖世界坐标绝对值。现在保留归一方向再加回自身坐标。
  这只是 AI 发出坐标的修复，未改共享路径/碰撞/固定点执行。

修改前三项回归失败，分别是期望 ESCAPE 实际 ATTACK，期望 hpregen 20 实际31，
期望13实际24，见 build/ai-baboon-regen-before.log。
第一轮修复后触发分支仍失败，从而定位了生命查询临时引用覆盖问题；
修正取值后通过，未放宽分支断言。

边界：沿用原版保存的 bonus 字段，不额外清零；原版在高生命近战且倒计时
仍活动的情形可能多次执行减法，本批未擅自改成一次性清理。
仍未完整移植 Baboon 的其他随机调用、等待/动作锁、逃离候选与失败回退、
近战回血后分支结构及 native path type。测试只证明相应决策与数值写入，
不是自然地图、实际回血速率或客户端表现验收，不能声明已解决全部停滞。

最终验证：BaboonRegenerationDecisionTest 5 项、ScarabBaboonPursuitTest 3 项，
合计8项，0失败/错误/跳过，BUILD SUCCESSFUL。
D2_HOME 使用安装的 1.10F；build/ai-baboon-regen.init.gradle 隔离输出并只编译相关夹具。
日志 build/ai-baboon-regen-final.log。覆盖状态3/19、再生比例整数截断、到期和回血撤销、
75.9%不提前退出、零基础再生、逃离请求坐标，以及三难度追踪与World更换。
未运行整个AI测试集，未重新验证既有Andariel失败，不宣称全绿。
