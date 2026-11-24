# Summon Grizzly（召唤灰熊，技能 247）

本轮完成的是召唤熊 `druidbear` 的服务端 AI，不是玩家变身 `Wearbear`（技能 228）。原版入口为 `AITHINK_Fn112_DruidBear`。

已按 D2MOO 的 1.10F/1.14d 对照实现：

- 无主人时进入 10 帧中立待机；
- 主人距离使用 `AIUTIL_GetDistanceToCoordinates_FullUnitSize` 的 50/28/18/16 分支；
- 远距离重新聚拢，主人走路/跑步时使用对应的速度增益；Fn112 传给 PetMove 的 `bRun=0`，因此保留 WL 而不是强制切换 RN；
- 目标搜索限制为 28，候选目标增加清晰接近路径检查，并保留主人的潜在目标回退；
- 近战分支按 `aip3=50` 在 `BearSmite`（`Skill1`、`seq_bearsmite`）和普通 A1 间选择；普通攻击后使用 `aip1=15` 帧停顿；
- 非近战分支按 `aip2=40` 使用原版奔跑速度增益，再走向目标；没有目标时按距离进入 15 帧待机或向主人聚拢。

原版数据契约已加入 `NativeDruidSummonDataTest`：`Velocity=5`、`Run=9`、`aidel=15`、`aip1/aip2/aip3=15/40/50`、`BearSmite`、`seq_bearsmite`。

同时修正了前一轮变身辅助公式的错误记录：1.10F `Wearbear` 是 `Param1/2=50/7`、防御 `Param3/4=25/5`，等级 3 为伤害 64、防御 35；等级 2 Lycanthropy 联动生命为 75。生产状态此前读取表格，因此这次修正的是辅助公式、测试和文档基准。

验证：

- `NativeDruidSummonDataTest`：新增 Grizzly Fn112 数据契约通过；
- `DruidBearAiIntegrationTest` 新增 6 项无 Mockito 的 ECS 行为测试，验证 A1/MonSeq、跟随速度、追击增益、待机及三条接近射线和主人目标回退；
- 9 个测试类、68 项组合回归通过；记录于 `tools/original-observer/druid-bear-final-validation-2026-11-23.log` 和同名 JSON；
- 原版仍未启动前台，未新增需要用户操作的采集批次；当前验证是依据已采集的 1.10F 数据和 D2MOO 原生函数重构。

尚未完成的是 Grizzly 自然目标场景的原版后台逐帧 AI 采集，以及 BearSmite 客户端攻击动画和最终伤害的专属对照。PetMove 的主人路径目标周围偏移、方向候选/中点重试和 RegionId 选择仍使用 Riiablo 的通用跟随投影；超过 50 的真实重定位由 SummonedPetSystem 负责，缺少该系统的独立世界只能降级为步行。目标扫描仍复用通用最近敌人扫描，原版的威胁优先级与精确目标体型距离没有在本轮逐项重建。不能据此宣称完整 AI 或动画逐帧等价。
