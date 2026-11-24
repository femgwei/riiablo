# 召唤恐狼（Summon Fenris，237）：出生与吞尸狂暴表现

本轮接续狼獾之心，检查技能 237 的两段客户端表现：召唤出生与吞尸后的狂暴光效。没有启动原版进程、切换前台或发送全局输入。

## 数据和重建源码依据

本机有效 1.10F `Skills.txt` 指定：Summon Fenris 的 `SrvDoFunc=119`、`Summon=fenris`、`Summode=S1`、`PetMax=min(lvl,par3)`、Param3=3；`Calc2=ulvl` 决定召唤单位等级。

D2MOO `SkillDruid.cpp:SKILLS_SrvDo119_DruidSummon` 通过 `D2GAME_GetSummonIdFromSkill` 取得出生模式，将该值放进 summonArg.nMonMode 后调用召唤分配器。这是公开重建源码，不能称为官方原始源码或原版运行时采样。

恐狼的 MonStats 技能槽包含 Fenris Rage；该技能的 `SrvDoFunc=137` 使尸体进入 `CORPSE_NODRAW`，再对自身应用 `fenris_rage` 定时状态。状态 138 的 overlay1/overlay2 同为 `direwolfcharged`。有效表中这项资源为 `expansion/DireWolfCharged.dcc`，16 帧、单方向，AnimRate=16、Trans=3、PreDraw=false。状态持续 500 帧，伤害加成为 100%。

## 修正

### 出生 S1

Riiablo 的 `ServerEntityFactory.createMonster` 只读取 MonStats.spawnmode；`ServerSkillSystem.spawnDruidSummon` 之前没有将技能的 Summode 传递到动画生命周期。因此即使资源齐全，技能指定的 S1 出生动画也不能由这条路径可靠启动并结束。

现在 SrvDo119 召唤成功后，将非 NU 的有效 Summode 安装为 `Sequence(Summode, NU)`。使用现有 SequenceHandler、CofManager、AnimDataResolver 和 AnimStepper 播放一次再回到 NU。没有创建虚假的 Casting，也不会将出生动画的动作标记误作再施放一次召唤。

这个修正按表驱动，覆盖同一 SrvDo119 中指定特殊出生模式的召唤；NU 精灵不会获得多余序列。恐狼是本轮直接验证对象，共享召唤逻辑另做回归。

### 狂暴光效

`direwolfcharged` 之前使用通用 Overlay 策略，将 AnimRate=16 解释为 16 FPS、Trans=3 解释为 luminosity。现在持久 `FENRIS_RAGE` 覆盖层使用已有原版策略：25 FPS、additive，并保留原有高度/偏移处理。

两个同名状态配置继续由 OverlayManager 的幂等安装合并为一层；状态刷新保留动画进度，过期移除时只释放狂暴层，其他状态特效不受影响。本轮没有改动已有吞尸的单次消费、隐藏尸体或伤害加成公式。

## 验证

`FenrisBirthIntegrationTest` 从实际召唤事件开始，运行生产动画系统，检查 S1->NU、出生期间无 Casting、没有递归创建第二只狼，并确认没有退回 2048 帧默认记录。另读取本机 MPQ 的出生/吞尸 COF 及其对应身体 DCC 头，确认资源、帧数与方向存在；不创建 GPU 纹理。

有效资源读到 MonStats.spawnmode 为空、Code=wf；出生 S1 有 TR 与 S1 两个图层，DCC 都为 8 方向、13 帧。吞尸槽使用 A1，TR DCC 同为 8 方向、13 帧。Riiablo 动画记录的出生速度为 72，生产推进在本测试中第 47 tick 返回 NU；这是 Riiablo 的实际测试读数，没有作为原版持续时间。出生与待机的 CofReference 序列化均检查模式和重启 ID。

`FenrisRagePresentationTest` 在权威 World 执行真实 SrvDo137，检查尸体不可重复消费、500 帧状态、100% 伤害加成、CORPSE_NODRAW 快照同步、单层狂暴显示、实际 DCC 头、原版时钟/混合、刷新和过期清理。客户端绘制对象与 AssetManager 是 mock，因此不是实际画面对比。

客户端 World 安装 StateUpdater，验证 snapshotOnly 的狂暴持续时间不会被本地模拟自行扣减。最终清理由服务端状态过期及空快照触发。

同一组合回归包含通用召唤、宠物生命周期、狼獾共享表现和状态覆盖层；最终逐项 XML、日志、数据与源码哈希存放在 `tools/original-observer/fenris-presentation-validation-2026-11-23.json`。

首轮 59 项通过。增加客户端 StateUpdater 及动画网络字段检查后，扩大组合中的 3 项狂暴测试因夹具未注册 EntityFactory 而在初始化失败，已补齐客户端工厂/地图绑定并重跑；失败 XML 和日志单独保留，不计为通过。

最终 JDK 17 组合回归：9 个测试类共 **70 项通过，0 失败、0 错误、0 跳过**。

## 剩余边界

- 未新增原版后台运行时采集，S1 的实际调度帧和 GPU 输出仍待观察；Riiablo 生产动画推进通过不等于原版逐帧一致。
- DruidWolf 的恐狼 AI 分支仍是近似实现：找尸体、移动到尸体、吞尸概率和战斗优先级尚未按完整 Fn108_Fenris 重建。本轮没有宣称 AI 全部一致。
- 出生验证使用记录式实体分配器与真实下游动画系统，不重新验证动态碰撞分配；配额/死亡清理由已有宠物测试回归。
- 全方向 DCC 解码、吞尸动作到光效出现的原版关键帧时刻、狂暴强化后的实际命中伤害与 GPU 遮挡未在本轮完成。
