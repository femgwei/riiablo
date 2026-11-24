# 狼獾之心：控制状态与受益光环

本轮检查 Heart of Wolverine（236），并覆盖共用代码的 Oak Sage（226）与 Spirit of Barbs（246）。目标是修正客户端技能表现；没有启动原版进程、切换前台或发送全局输入。

## 已确认的差异

Riiablo 已有正确的光环资源、原版帧速和 additive 混合配置。本轮发现的问题在状态来源：召唤流程把受益状态 `wolverine` 直接挂在狼獾之心自身，然后把它投影到友方。原版关联技能将 `wolverinecontrol` 用于自身，将 `wolverine` 用于受益目标。

有效 1.10F MPQ 表的关联关系：

- Heart of Wolverine（236）-> Wolverine Aura（297）：自身状态 162、目标状态 148。
- Oak Sage（226）-> Oak Sage Aura（298）：自身状态 161、目标状态 149。
- Spirit of Barbs（246）-> Barbs Aura（296）：自身状态 163、目标状态 147。

关联光环的 AuraFilter 均为 65795（0x10103），范围使用 `ln12`。三个控制状态没有 overlay；受益状态分别配置 `heart_of_wolverine`、`oak_sage`、`spirit_of_barbs`。混用状态使光环源自身多出受益光环。

公开重建源码 D2MOO `SkillPal.cpp:SKILLS_SrvDo065_BasicAura` 先用 AuraState 调用 `SKILLS_ApplyAuraToSelf`，再用 AuraTargetState 调用 `SKILLS_IterateAuraTargets(..., bCheckMonAuraFlag=1)`。后者在 `Skills.cpp` 中显式检查 `pTarget != pUnit`，并排除 MonStats.noAura 的怪物。因此范围扫描不会把受益状态加给源自身。

以上是本机有效数据表与公开重建源码证据，没有新增原版运行时调用或 GPU 证据，不能称为官方源码或实时采样。

## 修改

`DruidSkills.getSummonAuraControllerState` 从关联 SumSkill 的 AuraState 解析控制状态。`ServerSkillSystem` 在精灵身上安装该状态，继续将加成数值保存在源状态上。

`StateUpdater` 从控制状态读取源属性，向友方投影原有受益状态，并补齐 `noAura` 目标过滤。多来源身份和撤销逻辑保持原样；离开范围、死亡或删除后的受益状态仍由权威服务端移除。

控制状态经现有 StateSerializer 同步后，`StateOverlaySystem` 不创建受益光环；目标状态仍按原先配置绘制。本轮没有在客户端按角色类型硬隐藏特效，也没有修改通用 Aura 时钟或混合规则。

兼容边界：为了读取已有 checkpoint，StateUpdater 在控制状态缺失时仍允许从旧受益状态读取源属性。没有主动迁移已运行的旧召唤物；这些旧实例可能保留自身光环，重新召唤后使用正确状态。未修正完整 Fn109 Totem AI、周期刷新相位、不同强度光环的原版竞争规则或房间遍历顺序。

## 无窗口验证

`DruidSpiritPresentationTest` 使用实际 MPQ 数据、真实 Artemis/OverlayManager/StateOverlaySystem/OverlayStepper 与 StateSerializer，覆盖：

1. 三类精灵关联 AuraState/AuraTargetState 不混用，控制状态无覆盖层。
2. 控制快照不创建图层，受益快照恢复 PreDraw、混合和帧速；读取真实 DCC 字节头，验证狼獾 30 帧、橡木 11 帧、荆棘 12 帧及单方向。
3. 两个狼獾来源只显示一层光环，移除一个不重载/重启动画，移除最后来源释放资源并保留其他光环。
4. 资源仍在加载时，空状态快照取消光环；重入只加载一次；组件移除时清理。

其中 AssetManager/DCC 绘制对象为 mock；真实 DCC 头单独从 MPQ 读取。这不是解码后的 GPU 帧或完整客户端画面对比。

`DruidSummonIntegrationTest` 补充实际施法后拥有控制状态、没有自己的受益状态，以及 noAura 友方不获得受益状态的断言。保留等级 8 的狼獾攻击等级 +74、伤害 +69、范围撤销、荆棘反伤和旧 source-owned 快照等回归。

最终日志、逐项 XML、有效数据行与源码哈希见 `tools/original-observer/druid-spirit-presentation-validation-2026-11-23.json`。职业清单只更新局部进度，不标记原版运行时采集完成或完整视觉一致。

最终 JDK 17 组合回归：9 个测试类共 **75 项通过，0 失败、0 错误、0 跳过**。独立表关系预检的 1 项成功测试未重复计入 75 项。
