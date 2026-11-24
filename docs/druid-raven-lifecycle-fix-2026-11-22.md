# Raven 攻击预算与死亡展示修复

> 2026-11-23 补充：目标 GUID、成功命中和实际扣血现已确认，见 [Raven 伤害链路](druid-raven-combat-analysis-2026-11-23.md)。下文“HP 未下降”是旧轮次的低频采样结果；新实验关闭目标回血后确认它曾掩盖伤害，不能再将其解释成没有伤害。

## 本轮有效原版证据

全部实验在独立非输入桌面运行，复用 PID 25512 的常驻管理员 broker。没有切换桌面或使用全局输入。原版进程、调试器和目标均已清理。

- 等级 1：运行 `20261122-232949-a6d27f4d`，进程内选中技能为 `[221,1]`；服务端逐帧和客户端轮询均观察到 12 次 A1，随后 DT（0）→DD（12）。原版服务端死亡发生在 frame 1399，DD 在 frame 1413。
- 等级 5：运行 `20261122-233750-45964654`，进程内选中技能为 `[221,5]`；两侧均观察到 16 次 A1，随后 DT→DD。死亡在 frame 1708，DD 在 frame 1722。
- 技能的攻击尝试预算为 `Param5 + (level-1) * Param6`，这两级分别为 12 和 16。
- 原版有 S1 出生、NU/WL 活动、重复 A1、DT/DD 结束阶段；怪物模式 A1=4、WL=2、S1=8、DD=12。此前把 12 当攻击模式的解释错误。

可复用证据：`core/src/test/resources/original/druid-raven-lifecycle-1.10f.json`。导出器 `tools/original-observer/export_druid_raven_lifecycle.py` 从原始 native CSV/JSONL 和 client JSON 生成该文件，并保留各原始文件 SHA-256。

这些是现有仓库 native debugger harness 的采集结果，不是 REA 工具返回的 Evidence，也不包含画面像素或完整调用堆栈。

## 受控干预及结论范围

独立的 `d2game_raven_target.cpp` 只生成一个 class 0 骷髅，初始生命设为 10000，删除该目标的 AI think 事件；Raven 不由 harness 强制进入攻击或死亡。harness 在原版更新线程断点读取服务端状态，Python collector 在私有窗口施法并只读轮询客户端。

固定目标全程存活，但两轮中该目标 HP 都没有下降。不能把 A1 次数解释为成功命中次数，也不能据此断言 Raven 对固定骷髅造成伤害；现场仍可能有其他自然怪物。本轮确认攻击尝试预算及其结束生命周期，尚未确认被击中目标 GUID、伤害结算、随机攻击间隔、盘旋轨迹、全方向帧序或像素一致。

## 已修复 Riiablo 差异

1. `Raven.initialize()` 原先按怪物技能槽推断等级，高等级召唤也只有 12 次预算。实际定向回归在修改前失败：等级 5 期望 16，实际 12。
2. 现在按 `SummonedPet.skillLevel` 和 Skills.txt 参数计算，在第一次 AI 更新时初始化。真实工厂先 createMonster/初始化 AI，再绑定 SummonedPet，不能在分配时提前读取等级。
3. 预算用完后，旧实现永久停在 `EXHAUSTED`，没有 DT/DD。现在等待最后一次攻击完成后进入死亡流程、发布一次 DeathEvent、建立 DT→DD 序列，复用现有召唤死亡清理。
4. 增加 Raven checkpoint schema，保存剩余预算和继承的目标/状态，防止恢复后重新补满攻击次数。

## 修复采集错误与撤回旧结论

- 旧 collector 先过滤掉施法前已存在的 unit，导致目标采集为空。现在联合房间列表采集已有怪物，再单独判断哪些 Raven 是新生成的。
- 动画速度是 unit+0x4C 的 16 位值；原先读整个 DWORD 会混入 action 字节。现在正确屏蔽高位。
- 窗口鼠标 LPARAM 的 x/y 曾写反，现在按实际尺寸和 `(y<<16)|x` 发送。
- 原先 prepare 只输出新等级报告，没有覆盖现有 CXDruRaven.d2s。现在只在严格验证专用存档后备份、原子替换，并核对安装内容与报告一致；施法前再验证进程内选中等级。
- 因此旧的等级 5/8/20 数量夹具只保留为历史 raw capture，已标记 `evidence_valid_for_level_parity=false`；不能再证明高等级数量上限。新的等级 5 单只预算证据有效，数量上限需要独立重新验证。
- 上轮 60 秒只出现 2 次 A1 的原因：旧 Fallen 在 native frame step 63 即死亡，不是 Raven 预算耗尽。
- worker 不再在异常时直接 terminate 持有调试器的 helper。它发送结束信号、等待恢复和 detach；清理未证实则标记 attention 并保留会话，阻止后续批次复用。

## 验证

- Python 两项离线回归：实际安装等级/备份一致性、房间目标漏采与 16 位速度读取。
- C++ x86 独立目标 harness 编译成功；上述原版两批均完成并 detach、移除目标、关闭自有游戏。
- Java 定向测试使用已有 Gradle 7.3 + JDK 17 + `C:/Users/DELL/.gradle` 缓存，离线执行；无需新增依赖。先前 native-platform/缺缓存是沙箱写锁和所选 Gradle home 的问题，不能继续报告成项目依赖缺失。
- 最终日志：`tools/original-observer/druid-raven-lifecycle-final-validation-2026-11-22.log`；覆盖 Raven AI、原版生命周期样本、通用 AI checkpoint、德鲁伊召唤集成和召唤清理。
- 最终结果：51 项 Java 测试通过、0 失败（Raven AI 4、checkpoint 15、原版样本 3、德鲁伊召唤 11、召唤清理 18）；另有 2 项 Python 采集回归通过。

尚未把 Raven 标记为职业动画完整对齐。目标 GUID 和伤害 callback 已在 2026-11-23 后续实验确认；受击反馈、致盲状态和攻击帧的客户端展示仍待验证。
