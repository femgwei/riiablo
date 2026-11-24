# 德鲁伊职业批次：生产回放、三种地火与新增采集

后续更新：[地裂、火山已完成生产回放并修复生命周期/本体收尾](druid-surface-animation-analysis-2026-11-21.md)。当前为 4/30 已采集、4/30 已做限定对照，剩余布局及飞石差异单列；本文保留此前批次的研究范围。

2026-11-21：职业流程推进到 **4/30 已采集、2/30 已做限定生产对照**。修复火焰风暴缺少小火/中火的客户端步骤；地裂与火山已完成下一批原版后台采集，尚未做匹配配置的生产回放。亚马逊、法师仍后置。

## 同配置回放已接通

`DruidReplayLoadout` 从冻结 fixture 读取技能硬点、职业、裸装及其他硬点为零的约束；本批 Firestorm(225) 与 Molten Boulder(229) 都是一点，原生协同条件保留。既有单项无协同 fixture 没有配置块时继续只学习被测技能，原基准不被覆盖。

`prepare_class_batch_replay.py` 从冻结原始记录生成两个 replay input；`DruidBatchOriginalAnalysis` 在一次 JVM/MPQ 初始化后，分别建立独立生产世界运行两个案例。比较从 SkillDo 开始，使用真实技能、移动、展示和动画系统，不模拟人物输入序列、地图碰撞、伤害目标或 GPU。它并不是同一 Riiablo 世界连续施放的端到端输入测试。

结果在 `tools/original-observer/class-skill-batch-2026-11-21/`：`druid-*-production-1.10f.json`、`druid-*-comparison-1.10f.json` 及 `batch-comparison-summary.json`。生成器校验原版 run、存档哈希和生产实际硬点，manifest 对比状态与证据哈希对应，不靠静态表自动判定通过。

熔岩巨球 **14 项限定检查通过**，包括出土/滚动寿命、100 个年龄检查点的位移、88 个滚动 DCC 样本、独立服务端火区、客户端子特效数量/寿命，以及 8 个碎石位移。本次按记录中的 rolling 客户端 seed 调节展示诊断，不能扩大为全局 RNG 已同步或所有火焰相位一致。

## 火焰风暴：修正采集口径后定位缺失步骤

首批 run `20261121-090722-a91508f2` 的旧采集器只观察客户端 class 457/458，遗漏 CltDo6 的其他两个子行。因此“原版 17 个地火、生产 34 个”不是可直接比较的总量，不能据此将发射率减半。此前单项研究也保留同样的覆盖缺口。

补充动态探针使用哈希固定的原版 D2Client：

- RVA `0x9083B`：D2Common#10234 路径标记 getter 返回后，记录实际 EAX、path.flags、父 RNG low/high。
- RVA `0x90A9A`：子单位创建函数返回，记录真实返回值、单位类型、行号和 id。
- RVA `0x90AC1`：在当前回调结束阶段重新布置下一次探针，避免在同一 EIP 上立即重置断点导致自陷循环。

最终成功 run `20261121-100617-f1e0bced`：117 次路径条件检查，35 次阳性条件均对应一个成功创建返回；其中 **16 个 firestorm、8 个 firesmall、11 个 firemedium**。三类创建 id 与快照捕获的客户端单位完全对应。该次起点 X=4522，首批 X=4523，不能再把两个不同起点/随机流的总量硬比较。

原版所有三种子火在本技能下的实际寿命都是 **15 tick**，共记录 **490 个客户端动画光标状态**。小火/中火独立表行虽有 Range=90、LevRange=25，不能直接拿它们的普通寿命替代本次 CltDo6 的技能覆盖。地火生长、倒播结束时钟一致；该回调补采没有安装绘制探针，不能把光标状态称为新增 DCC/GPU 绘制证据。

## 生产修复与验证

`createFirestormVisual` 原先固定取 CltSubMissile1，现在复用已审计的 CltDo6 三选一规则：roll=0 取第二行、roll=1 取第三行、roll=2 保留第一行，再执行独立的灯光 RNG。三者使用主火焰的 15 tick 寿命约束，保留 fp16 出生位置、零速度和客户端展示标记。

`NativeFirestormPresentation` 只把 Firestorm 技能生成的小火/中火接入原生时钟；不会全局更改其他技能的小火/中火。服务端伤害子火仍使用原 `firestorm` 行。修复后首批生产示例为 8 个 firestorm、12 个 firesmall、14 个 firemedium，三类均为 15 tick；此分布只说明生产路径已经包含三种资源，不等于原版分配 RNG 已对齐。

新增回归逐条验证 **35 次实际选择**，以记录中的原始 RNG 为输入调用生产选择器；另核对 **490 个原版地火光标**，并运行真实生产案例验证三类资源、寿命和伤害行。最终限定回归 **50 项全部通过，0 失败、0 错误、0 跳过**；覆盖 Firestorm、Molten Boulder、ReferenceElementalPresentation、Loader 等。旧单项 fixture 未覆盖，新 fixture 为 `core/src/test/resources/original/firestorm-variants-1.10f.json`。

火焰风暴的限定对照检查通过，**完整客户端创建起止节奏、不同方向、地图碰撞、所有等级以及全局 RNG 分配仍未验收**。既有 17 条单资源观察不再作为总量验收条件，比较 JSON 明确标为不可比较，而非伪造一项总量匹配。

## 新的地裂、火山批次

run `20261121-095015-f801398c`，`druid-surface-batch-private-desktop`，独立裸装角色 `CXDruSurface`，Eruption/Fissure(234) 与 Volcano(244) 各一点。一次启动自动切换两项，记录同一 PID/游戏地址，末尾均无本技能残留。

- 地裂：1256 条服务端、2404 条客户端状态；2376 对绘制调用/返回、2370 次 DCC 选帧。观察到服务端一个 center 与 14 个 crack，客户端一个 center、14 个 crack、14 个 smoke。
- 火山：2007 条服务端、3033 条客户端状态；2880 对绘制调用/返回、2875 次 DCC 选帧。客户端包含山体、火口、飞石、爆炸和岩浆子层，不能只比较山体 DCC。

逐技能JSON与 `runtime-surface-batch-summary.json` 已导出，现已完成地裂/火山限定生产对照：地裂数量/寿命/循环、火山本体收尾和飞石发射/轨迹/高度/选帧均已修复。详细见 [表面时钟记录](druid-surface-animation-analysis-2026-11-21.md) 与 [火山飞石记录](volcano-flight-background-analysis-2026-11-21.md)。四个技能均保留剩余差异，不扩大为完整一致。

## 隔离、失败记录与复现

所有新原版运行均使用未显示的私有桌面；没有切换输入桌面或唤醒游戏到前台。成功和失败采集均已解除调试器并关闭所属游戏。常驻 broker 继续可用，无新增 UAC。

失败记录保留：`095537-51f4e492` 在启动游戏前因原版将空热键从 FFFFFFFF 归一化为 0000FFFF 被严格存档检查拒绝；现在只归一这两种空值，分配的技能及按钮位仍严格比较。`095725-5e67a789` 因同 EIP 断点重置超时；`100013-a6811ebf` 误将非 457 的合法子火视为身份失败。修复探针后 `100206-a3b6202a` 确认三种子火，最终 `100617-f1e0bced` 增加选择前 RNG 和完整变体状态。失败结果均未作为通过证据。

首轮变体测试错误沿用了小火/中火的独立表寿命；原版新记录证实本技能覆盖为 15，已修正并重跑。初轮 50 项中 4 项失败的日志 `druid-variant-fix-validation.log` 保留，最终日志为 `druid-variant-fix-final-validation.log`。验证汇总 `druid-class-batch-validation-2026-11-21.json` 保存来源、测试和源码哈希。

本轮使用本地 Win32/Capstone 和仓库诊断程序；当前未连接 REA 服务，不将这些记录标为 REA Evidence。
