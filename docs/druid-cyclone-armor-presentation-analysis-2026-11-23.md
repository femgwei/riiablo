# 飓风装甲（Cyclone Armor，235）：剩余吸收量与客户端特效

本轮从野性狂暴/撞槌继续到德鲁伊技能 235。目标是补全特效阶段、前后图层和权威状态同步，不以像素一致为验收标准。全程没有启动原版进程、切换前台或发送全局输入。

## 已定位的问题

`States.txt` 的 `cyclonearmor`（状态 151）指定 `setfunc=8`、`remfunc=5`、`nooverlays=1`。三个配置项为 `cyclonearmor1front`、`cyclonearmor2front`、`cyclonearmor3front`。Riiablo 原先把这三个前景作为普通持久覆盖层同时绘制：缺少后景半层，也不会随装甲被消耗而减少特效。

网络端同时存在阻碍：旧 `StateP.runtimeValue` 为 int16，超过 32767 的 8.8 吸收值会被截断，且没有同步 `bonearmormax`。这使接收端即使知道阶段公式，也无法正确还原协同技能加成后的剩余比例。

## 原版离线二进制依据

目标：`G:/BaiduNetdiskDownload/Diablo II 1.10F/D2Client.dll`，SHA-256：

`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`

本轮沿用 reverse-engineer-anything 技能的证据区分要求。当前 REA MCP 不可用，使用现有 PE/Capstone 离线读取 DLL；这些不是实时 REA 调用或原版运行时观测。

- ImageBase `0x6FAA0000`。
- Set 分发表 `0x6FB93EE0`，索引 8 -> `0x6FB4C560`（RVA `0xAC560`）。
- Remove 分发表 `0x6FB93F60`，索引 5 -> `0x6FB4C3D0`。
- 读取状态属性 132（bonearmor 当前量）、133（bonearmormax 最大量）、355（shortparam1，前次显示组数）。
- 令 `third=maximum/3`，先做整数除法。正的有效装甲显示：`current < third` 为一组；否则 `current < 2*third` 为两组；否则三组。阈值相等时保留较多组。
- `maximum=10240` 时，third=3413，两个阈值为 3413 和 6826。不能先做浮点比例或用另一种取整顺序。
- 每组创建配置的 Overlay 数字 ID 和 **ID+1**；有效 1.10F 表中后者是相应的 back 层。该行为不是按资源名补猜。
- 组数增加时先通过 remfunc5 清理全部组，再创建所需组；组数下降只清理较高组，保留低组动画。
- 原始函数的最低组清理分支在 `0x6FB4C7FB` 比较的是第一个 overlay 的数字 ID 与 1，存在原版自身的特殊分支；本轮对有效正吸收量保留至少一组，耗尽时沿用服务端移除状态。
- 原版移除代码按 `D2Common#10680` 返回的 1ofN 范围清理。本轮只实现零售数据中 1ofN=1 的六层，不宣称支持扩展数据的多变体选择。

完整指令记录：`tools/original-observer/cyclone-armor-static-1.10f-2026-11-23.json`；复现脚本：`analyze_cyclone_armor_static.py`，要求 DLL 哈希匹配。

重新施法另参考 D2MOO `SkillSor.cpp:SKILLS_SrvDo018_DefensiveBuff`、`Skills.cpp:SKILLS_ApplyStateEffect`：同技能同等级重施会复用原状态列表，因此满装甲同级重施不应自动重启全部动画；恢复吸收量导致显示组数增加时，仍执行客户端 setfunc8 的清理/重建分支。换等级的状态实例使用新的表现修订号。

## 修改

新增 `NativeCycloneArmorPresentation`，按当前/最大吸收量和原版整数边界选择一到三组前后层。`StateOverlaySystem` 单独维护组数与表现修订号，在增加/换实例时重建，下降时通过 `reconcilePersistent` 保留仍选中的层。

六层使用已有原版 Overlay 绘制策略：AnimRate=16 对应 25 FPS，Trans=3 对应 additive，PreDraw 将 back 层安排在角色之前。原版表的六个资源均为 11 帧；测试夹具使用对应的帧数，但没有执行 GPU 绘制。

`Sync.fbs` 在 StateP 末尾追加 `cycloneArmorCurrentFixed` 和 `cycloneArmorMaximumFixed` 两个 int32 向量，保留旧字段位置和含义；只有快照含有该状态时才发送新向量。重新生成 `StateP.java`，更新所有仓库内 createStateP 调用的尾部参数。

`StateSerializer` 发送精确 8.8 值并在接收时恢复状态属性。`AuthoritativeUnitLifecycleState` 保存独立值，`ClientUnitSnapshotWriter` 写入副本时恢复两项属性，避免缓冲区复用或副本重建丢失阶段信息。旧客户端可忽略末尾字段；旧服务器缺少最大量的快照不能精确重建阶段，本轮不猜测协同技能点数。

`DruidSkills.applyCycloneArmorState` 的同等级重施保留表现修订号，新等级使用新号。原来的消耗、伤害顺序、最大吸收量公式保持原样。本轮也没有修改原适配器接受较低等级重施的行为；D2MOO 对低等级覆盖高等级存在拒绝分支，这仍是后续服务端核对项。

## 验证

新增六项 `CycloneArmorPresentationTest`，覆盖：

1. 满量、两个整数阈值的等号/前一单位、一单位剩余和零量。
2. 受伤减少组数时保留低组动画并释放高组资源。
3. 组数增加重建、稳定状态不重载、同级满量重施保留时钟与换级重建。
4. 高等级且有协同技能的大数值，经 StateSerializer 往返后仍精确保留当前量、最大量和三种阶段。
5. 状态移除/组件移除清理六层，其他状态与瞬时特效保留。
6. 六个前后资源存在，使用原版帧速、混合与 PreDraw 配置。

`AuthoritativeUnitLifecycleStateTest` 新增大数值在独立快照、网络缓冲区清零和副本重新写入后的保留测试。现有装甲战斗、风系施法、召唤/旧快照、状态覆盖层及上一项 Feral/Maul 回归一并执行，另覆盖真实 AssetManager 的选中层替换。

初轮唯一失败来自测试用对象身份判断：池化 Overlay 会复用 Animation 对象，清理重建不保证 Java 对象更换。断言改为检查动画游标复位以及完整的资源释放/重新加载行为；没有弱化“重建动画”的要求。初轮与最终 XML、各轮日志及源码哈希分别保留，最终结果见 `tools/original-observer/cyclone-armor-validation-2026-11-23.json`。

最终 JDK 17 组合回归：8 个测试类共 **60 项通过，0 失败、0 错误、0 跳过**。初轮 58 项中 57 项通过、1 项为上述对象身份断言失败；后续新增两项真实 AssetManager 参数化回归，并保留初轮记录。

范围限制：没有原版实时特效创建/删除采样，没有实际 GPU 遮挡和全方向逐帧画面对比；同级重施列表复用依据公开重建源码，仍需原版后台运行时复核。本轮结论是客户端结构和状态消费修正，不能标记完整视觉一致。
