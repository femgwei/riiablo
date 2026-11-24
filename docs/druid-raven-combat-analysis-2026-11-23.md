# Raven：后台命中与扣血链路

> 后续更新：致盲触发和客户端起始→持续特效顺序已修复，见 [Raven 受击反馈修复](druid-raven-feedback-fix-2026-11-23.md)。

## 结论

原版 Raven 会命中实验固定骷髅并造成物理伤害。此前低频采样中的“目标血量一直是 10000”不能用来判断不伤害：目标的 hpregen 为 1250，关闭回血后，血量下降与每包伤害严格相等。

Riiablo 本轮增加的真实 ECS 集成测试确认：召唤初始化 → Raven AI 选目标/建立普通 A1 → 攻击关键帧 → 伤害结算 → DamageAppliedEvent → 目标血量下降可以贯通，等级 1、5 均通过。本轮未发现需要修改 Raven 生产伤害代码的明确证据；现有生命周期修复继续保留。

这只证明上述链路与原版已采集成功命中样本相符，不代表全等级、随机序列、致盲、受击画面或整项技能动画已完整对齐。

## 实验范围和清理

- 复用已授权 broker PID 25512；原版通过现有 loader 在独立非输入桌面启动。
- 未 SwitchDesktop，未使用全局输入；施法通过私有 HWND，未将游戏切到前台。
- 实验只对生成的 class 0 骷髅修改 HP/maxHP=10000、hpregen=0、删除目标 AI think 事件。Raven 的行动、命中和伤害保持原版逻辑。
- 逐游戏更新线程断点读取服务端；客户端 collector 并行只读采样。
- 两批均确认移除实验目标、恢复断点并 detach、关闭自有游戏；没有终止持有调试器的 helper。

## 原版运行证据

运行 `20261123-003325-127f7bea`：技能进程内 `[221,1]`，Raven GUID 19、固定目标 GUID 18；frame 614/777 成功命中，伤害分别 805、786 个 8.8 定点单位。目标从 2560000 下降至 2558409，总损失 1591，即 6.21484375 HP。

运行 `20261123-003604-ec1a9231`：同为 `[221,1]`，Raven GUID 15、固定目标 GUID 14；5 次成功命中，总损失 3840 定点单位，即 15 HP。逐包与低频采样的首尾差一致，采样血量单调不增。

两个实验的目标 hpregen 均由 1250 改为 0，并读取确认。前一轮 `20261123-002830-9d86d7b9` 未关闭回血，伤害函数处能看到瞬时扣血，但后续采样恢复满血；这里只把它作为发现问题的历史数据，正式回归夹具使用上述两批受控数据。

## 已观测到的调用顺序

对每次成功啄击，都记录到以下三个选定入口及其返回，目标指针/GUID 均指向固定骷髅：

1. `D2Game+0x91E70`，D2MOO 对应名 `SUNITDMG_IsHitSuccessful`，返回 EAX=1；该调用前后 HP 不变。
2. `D2Game+0x91AC0`，对应 `SUNITDMG_DrainItemDurability`，进入时 HP 尚未扣除，返回时已扣除整包伤害。
3. 第二个调用内部到达 `D2Game+0x91260`，对应 `SUNITDMG_ExecuteMissileDamage`；此时 HP 已经下降，物理伤害=总伤害，result_flags=5，函数返回后 HP 不再变化。

因此 `0x91260` 不能称为“真正写入 HP 的入口”。本次只定位到 HP 扣除发生在第二个函数内、第三个函数前的区间，并记录调用者返回地址；没有采集该区间内全部函数或完整调用栈。名称来自 D2MOO 参考工程，运行结果来自本地 hash 固定的原版 DLL；不是 REA Evidence。

第二批 Raven 出生期的读取为 level=98、tohit=1057、mindamage/maxdamage=0。原版的有效技能伤害可经 `MonStats.SkillDamage` 单独供应，不能把出生基础伤害 0 解释成不会攻击。Riiablo 将该技能伤害加入召唤属性供普通攻击消费；目前确认已采集的等级 1 伤害包位于对应技能 2..4 物理伤害范围内，未证明两边内部存储或 RNG 序列一致。

## 可重复证据与回归

- 原版精简 JSON：`core/src/test/resources/original/druid-raven-combat-1.10f.json`。
- 导出器：`tools/original-observer/export_druid_raven_combat.py`，对调用嵌套顺序、A1/目标身份、逐包差、累计差、单调血量、清理和后台状态执行断言，并保存原始文件 SHA-256。
- native helper：`tools/original-observer/native-harness/d2game_raven_target.cpp`，固定 DLL hash、入口签名和实验角色后执行；增加目标回血干预、目标连接、攻击者属性、选定函数入口/返回及 HP 的观测。
- `DruidRavenOriginalParityTest` 消费原版夹具，验证物理伤害范围、目标身份与实际 HP 差。
- `DruidSummonIntegrationTest` 新增等级 1、5 的召唤→AI→A1关键帧→扣血测试；没有手工 dispatch DamageEvent。实体分配仍使用现有测试工厂，移动/动画时钟不由该测试覆盖，关键帧由测试投递。

最终定向验证 26 项 Java 测试通过、0 失败：德鲁伊召唤 13、Raven AI 4、普通怪物关键帧近战 5、原版 Raven 证据 4。日志为 `tools/original-observer/druid-raven-combat-final-validation-2026-11-23.log`。C++ x86 helper 编译成功，导出器对两批原始数据的所有断言通过。

## 下一处明确缺口

致盲状态及客户端发布/消费已由同日后续实验确认并补齐；下一处缺口是普通血量目标的受击模式阈值、攻击关键帧与受击反馈的相对时序。无需再次重复证明基础扣血链路，也无需前台像素采集。
