# Raven 普通血量目标硬/软受击核对与修复

> 后续：已补齐盘旋、接近与攻击期限阶段，见 [Raven 移动节奏记录](druid-raven-motion-fix-2026-11-23.md)。真实路线及朝向帧序仍待核对。

## 结果

原版普通骷髅会因 Raven 啄击进入 GH，而之前最大生命被设为 10000 的目标只收到软受击反馈。Riiablo 的这条近战路径原先没有伤害门槛判断，且对正在攻击的怪物直接走软受击，导致两类展示差异。

本轮补入普通怪物物理近战的受击判定：按原版命中类型选择生命比例门槛，使用受击者的双字随机流执行两级随机判断；不足门槛保留动作并发送软受击事件，达到硬受击条件可中断普通 A1，切换 GH。致命伤害仍走已有死亡路径。

修改前的 Raven 集成测试在两个命中用例失败：高血量目标期望 NU（1），实际 GH（3）。日志为 `tools/original-observer/druid-raven-hit-recovery-before-fix-2026-11-23.log`。随后用原版实际函数输入/输出及随机种子校验了修复后的判定。

## 后台原版实验

全部继续使用已授权 broker PID 25512、私有非输入桌面与原有 loader，无桌面切换、前台激活或全局输入。两批均完成断点恢复、detach、移除实验目标、关闭自有游戏。

### 保留原始生命：20261123-091909-3788bd50

固定目标 class 0/GUID 20，原生生成的当前/最大生命均为 2304 定点，即 **9 HP**。本轮没有改写其当前或最大生命，仅关闭回血（1→0）和删除初始 AI think 事件。Raven GUID 21 由游戏自然行动，不强制命中或受击模式。

- frame 510：伤害 546 定点（2.1328125 HP）；HitClass=3；原版 `ShouldSkipHitRecovery` 返回 0，之后目标进入 GH=3。此例消耗一次 `random & 3`，受击者种子由 `(2401638653,139061005)` 变为 `(3025629630,1001705155)`。
- frame 597：伤害 874 定点（3.4140625 HP），函数返回 0，进入 GH；伤害超过该命中类型的最高随机门槛，不消耗受击随机数。
- frame 684：目标剩余 884 定点，伤害包总值为 989、实际物理扣血受剩余生命限制；原版直接进入 DT=0，再于 frame 708 进入 DD=12，没有调用受击门槛函数。
- 客户端观测到 NU、GH、A1、DT、DD，证实 GH 并非只有服务端标志。

原版致盲会重新调度目标的 AI，因此“删除初始 think”不等于全程冻结目标。采集中目标随后出现 A1，是原版状态逻辑自然产生的活动，不是 harness 强制模式。

### 高生命对照：20261123-092139-931b3718

目标最大/当前生命改为 10000，关闭回血并删除初始 think。4 次已采集的存活命中均返回 `ShouldSkipHitRecovery=1`，目标保持 NU，并设置 AI state 19。客户端目标模式也始终为 NU，没有 GH。4 次门槛判断均未消耗受击随机数。

其中一个包的 HitClass=19（基础 3 加临界伤害层 0x10）；原版是按完整值进行 switch，不先屏蔽高位。Riiablo 新规则保留这个细节。

## 原版函数与边界

新增观测 `D2Game+0x91870`，D2MOO 对应 `SUNITDMG_ShouldSkipHitRecovery`。实际原版 DLL hash 和入口机器码均校验，记录 ECX 目标、EDX 伤害包、栈上 HitClass、最大生命、调用前后双字随机种子及 EAX 返回值；仍是选定函数观测，不是完整堆栈。

从参考实现分析并用本轮运行验证的普通物理伤害分支：

1. 冻结、纯毒、总伤害不足 1 HP 时跳过硬受击。
2. 完整 HitClass 为 2/6/10/11 时除数为 8，5 为 64，4/8 为 32，其余为 16。
3. 伤害低于 `maxLife/divisor` 时直接跳过。
4. 低于 `maxLife/(divisor/2)` 时，`random & 1 == 0` 跳过。
5. 低于 `maxLife/(divisor/4)` 时，`random & 3 == 0` 跳过。
6. 最后还要求怪物拥有 GH 模式；已有 STUNNED 状态会在调用者处绕过这个门槛函数。

这些比较均为严格 `<`，使用原生 8.8 定点数和整数除法。随机属于受击者，不能使用攻击者的流，也不能在确定无需随机时多消费一次随机值。

## Riiablo 实现范围

- `NativeHitRecovery` 提供定点门槛与原版随机 mask 顺序。
- `HitReactionResolver.queueMonsterMeleeDamage` 在可识别的普通怪物物理近战中消费伤害包、MonStats2.HitClass 和受击者随机流。
- `Actioneer` 的普通近战存活结算不再丢弃伤害信息而只传 `false`。
- 软受击保留当前 Casting/Sequence；硬受击会清除普通攻击动作再进入 GH，取消“只要正在攻击就一定软受击”的粗略规则。
- 原有格挡、死亡保护和没有 GH 模式的回退保留；冻结/不可打断、快照状态不在该路径触发错误硬受击。

当前只接入普通怪物对怪物的纯物理近战（包含 Raven）。玩家、导弹、元素包、特殊技能中断规则仍保留原有处理，不宣称已完成全局 FHR 重构。改变的普通物理怪物近战会按原版多消耗必要的受击者随机值，这是预期行为。

## 可复用证据与测试

- 精简 JSON：`core/src/test/resources/original/druid-raven-hit-recovery-1.10f.json`，含 2 个硬受击、4 个软受击判定和客户端模式证据。
- 导出器：`tools/original-observer/export_druid_raven_hit_recovery.py`；独立重放双字种子，验证返回值、消耗 mask、最后种子和模式，并保留原始数据与当轮 helper 的 SHA-256。
- `DruidRavenOriginalParityTest` 在 Java 中再次以同一输入重放判定和双字种子。
- `NativeHitRecoveryTest` 覆盖 HitClass 分支、严格边界、随机顺序和确定拒绝不消耗随机。
- `HitReactionResolverTest` 覆盖普通/高生命目标在空闲与正在普通攻击时的硬/软反馈。
- `DruidSummonIntegrationTest` 用真实 Raven AI 和攻击关键帧验证高生命目标不再无条件进入 GH，并继续验证致盲、客户端特效和到期清理。

最终定向回归 **68 项 Java 测试通过，0 失败/错误**：Raven AI 4、致盲 3、受击纯计算 3、原版证据 6、德鲁伊召唤 14、受击 ECS 12、闪电强化软受击回调 3、普通近战 5、真实特效资源 5、状态特效 13。日志为 `tools/original-observer/druid-raven-hit-recovery-final-validation-2026-11-23.log`。另有 4 项 Python 采集离线回归通过，导出器对两批原始证据的断言通过，最终 C++ x86 helper 编译成功，相关 Python 模块语法检查通过。

不据此声称客户端像素或所有受击帧钟已对齐。下一步可转向 Raven 的盘旋、接近、攻击间隔与朝向动画；无需继续重复证明基础扣血、致盲和这组受击门槛。
