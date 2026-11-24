# 亡灵巫师技能客户端展示：新 chat 交接

## 目标与边界

继续完善 Riiablo 的职业技能客户端动画、特效和相关权威事件链路。用户已将目标从像素级一致调整为定位并补齐原版实现中的缺失步骤，包括施法动作、弹体出生/方向/轨迹、命中反馈、持续效果和清理。伤害与耗蓝若影响展示或发现明确差异，也应记录并做有界修复。

**只允许后台或无窗口工作。不得把原版切到前台、模拟全局输入或为采样抢占焦点。** 首选现有 MPQ 数据、公开重建源码、已有原版后台采集记录和无窗口生产链路测试。确需原版运行时证据时，先复用已有接收器和已验证后台流程；不能保证不影响前台时，继续独立离线工作并保留待采集项。

仓库：`F:\3rd_src\riiablo`。原版资源：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。原版曾需 `D2v110各版本免CD全功能启动.exe` 启动，普通 Game.exe 启动结论不能直接套用。不要为了新 chat 从头启动游戏或重新建立 UAC 流程。

## 现有职业顺序

原定优先级：德鲁伊、亡灵巫师、野蛮人、圣骑士、刺客；亚马逊和法师放后。当前转向亡灵巫师。德鲁伊 30 个技能均已至少有一轮采集或有界研究记录，**不代表 30 个技能均已完整匹配原版**。各项剩余问题仍在清单和单项文档中。

先读：

- `tools/original-observer/class-skill-batch-2026-11-21/nec-manifest.json`
- `tools/original-observer/class-skill-batch-2026-11-21/class-skill-inventory.json`
- `tools/original-observer/class-skill-batch-2026-11-21/dru-manifest.json`（参考最近的记录方式）

亡灵巫师清单可能仍标记 `not_compared_in_class_batch`，这不能推断该技能从未研究过。此前已经处理过骨牢、骨矛及尾迹等问题；应先搜索 docs、测试、工具及 Git 历史，补齐已有证据引用，再选择真正未覆盖的技能，避免重做。现有文档包括：

- `docs/necromancer-client-fixes-2026-11-01.md`
- `docs/necromancer-client-area-trail-2026-11-01.md`
- `docs/skill-damage-necromancer-ownership.md`
- `docs/bone-prison-background-comparison-2026-11-19.md`
- `docs/bone-spear-background-comparison-2026-11-19.md`
- `docs/bone-spear-trail-motion-2026-11-20.md`
- `docs/class-skill-batch-workflow-2026-11-21.md`

## 刚完成的狂怒修复

`docs/druid-fury-presentation-analysis-2026-11-24.md` 是最新单项研究。

公开重建 D2MOO 的 SrvDo013 在攻击关键帧后立即调用 RewindUnitAnimation；Fury 的 Param2=100 是回卷百分比。Riiablo 原来等整段 A1 ENDANIM 后再重播，导致连续攻击中间多播尾段。

现已在 Actioneer 的 Fury 攻击结算/目标接续后调用 SequenceHandler.rewindFuryAnimation；零售 100% 回到 frame=0，保留当前连击次数与动作序列，并通过递增 CofReference.animationRestartId 发布同模式重播。最后一击或无后续目标时不回卷，正常播尾段、回到 NU。新增 getFuryRollbackPercent，旧命名接口以 deprecated 形式保留。非 100% 自定义回卷仍是本地固定帧近似，未宣称等价原版。

最终 JDK 17 定向回归 32 项通过，0 失败/错误/跳过。真实狼人 A1 夹具：五击 hit_ticks=[7,14,21,28,35]，finish_tick=41；包括首个目标死亡后换目标、全部目标死亡后退出、网络重播 ID 序列化。并非原版实时计时或 GPU 对比。

证据：`tools/original-observer/fury-presentation-validation-2026-11-24.json`，逐项 XML 在 `fury-test-results-2026-11-24`，成功日志为 `fury-animation-final-validation-2026-11-24.log`。交接前将提交并推送本次 Fury 修改；用 `git log -2` 确认最新提交。前一个已推送提交为 `8fd185d2b`，包含此前累积的技能实现、工具和研究证据。

## 可复用的实现与验证方法

- 状态持续效果用 StateP/StateSerializer 到 StateOverlaySystem/OverlayManager；不能用客户端本地持续时间代替 snapshotOnly 的权威移除。
- 独立目标覆盖层已有 UnitOverlayEvent/UnitOverlayP，当前 D2GS 支持按目标可见范围发送，客户端先映射服务器实体 ID 再发布表现事件。旧 server:netty 示例未同步扩展。
- 吸取闪光已可把权威选择的 steallife/stealmana 传给联机客户端，满资源池仍可能有有效闪光。
- Overlay 的 AnimRate=16 通常走现有原版策略 25 FPS、Trans=3 additive；弹体的 Trans=1 是另一套映射，不能混用。
- 资源表帧数未必等于实际 DCC 头。焰爪 FireHit 表中 8、实际 12；熊人 Hunger 身体 DCC 13 而 COF/AnimData 只用 10。先区分资源帧、动作长度和存活时间，再判断是否缺帧。
- 无 GPU 测试用真实 World、事件、状态/序列/加载器，加 mock AssetManager/DCC；另读实际 COF/DCC 头与 AnimData。明确这不等于已验证 GPU 调色、遮挡、全部方向像素朝向或真实网络延迟。
- 单项发现充分且已修复/验证后前进；复杂不确定部分记录证据和剩余问题，不无限重复一个技能。

## 工程与测试注意事项

使用 JDK 17，示例 PowerShell：

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-17' :core:test '-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F' --tests '*SpecificSkillTest' --no-daemon *> tools/original-observer/specific-skill-validation.log
```

Gradle 使用既有 `F:\Gradle` 缓存，需要允许相应缓存访问。core 与 server:d2gs 分开执行测试任务：在一个命令里列两个任务后统一写 --tests，会只筛选最后一个任务，曾意外触发整套 core 测试并遇到无关缺失素材失败。

别把 `*Fury*` 当作德鲁伊专用筛选，它还匹配 Blade Fury/Lightning Fury；应按具体测试类筛选。当前模块边界：部分通用类在 server-domain、表类在 game-data；core 中找不到时先用 rg --files。server/d2gs 有时被 rg 忽略，使用明确路径。不要对 .gradle 旧源码快照做无界搜索。

Artemis 夹具要注册 factory/map/clientContext 等实际依赖；即使字段标了可选，仍可能要求 OverlayManager 注册。`component.Object` 与 java.lang.Object 冲突时用显式类型。加载 AnimData 时保存并还原 Riiablo.anim；mock 资产/颜色等全局对象也要还原。

每次保留运行日志、逐项 XML、源码 SHA256、有效表摘录与限制。未实际采集原版时保持 runtime_status=not_run_in_class_batch，只更新 comparison_status/individual_progress。

## 工作区与协作

沿用当前主目录和 master 分支，先检查 git status，保留不相关改动。常驻接收器会持续改写 `tools/original-observer/broker/20261121-165133-4490d598/status.json` 的 heartbeat，不能把它误判为实现丢失，也不要为清空工作区而关闭接收器。

交接时发现新出现的 `.tools/` 未跟踪目录；它不属于本轮 Fury 修改，应先确认归属并保留，不随本轮交接提交。下一 chat 按用户明确请求进行后续 Git 提交/推送。

开始工作：先归并亡灵巫师已有记录到清单，再完成首个确实未覆盖技能的一轮分析、确定问题修复、定向验证和文档。下一职业工作在新 chat 进行，此旧 chat 留作历史参考。
