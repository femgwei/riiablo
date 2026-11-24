# 极地风暴目标移除与待动作生命周期

本轮修复了目标已退出场景、但组件尚未回收时，客户端冰流仍读取旧目标坐标的问题。展示目标现在必须仍在有效 Position 实体订阅中；已移除目标使用保留的对地点，仍存在的零生命目标不被额外过滤。改动位于 Inferno/Arctic 共用的客户端发射入口，不改变服务端伤害、耗蓝、施法状态或冰流数量。

## 原版解析链的边界

对固定 SHA256 的原版 D2Client.dll 做离线 PE/Capstone 检查：

- `896A0` 先调用 `ADEC0` 校验目标，再读取路径目标单位指针。
- `ADEC0` 经 D2Common ordinal10171 取目标类型/编号，调用当前单位表查询，并与存储的目标指针比较；不匹配则经 ordinal10179 清除路径目标单位。
- `1A950` 对有效目标取当前位置；没有目标单位时使用当前路径对地点。
- 该解析链自身没有 HP 判定。因此不能把“HP归零”直接等同于“目标引用已经失效”。输入、死亡或其他逻辑可能另外清除引用，本轮不将这段静态代码扩大成完整死亡场景的行为证明。

本轮没有启动原版进程，也没有新增目标死亡场景的原版运行时采集。静态检查记录在 `arctic-blast-target-lifetime-static-1.10f-2026-11-22.txt`，不是原版函数堆栈或GPU证据。

## 本地复现与真正原因

`ArcticBlastTargetLifecycleTest` 使用实际 Actioneer、序列、待动作消费和展示导弹系统。在第一个动作已排队、尚未生成冰流时删除目标：本地保留的对地点在东侧，旧实现却仍生成北向冰流。最初13项检查中仅这一项失败，另一次5项诊断仍能复现。

简单增加 `EntityManager.isActive` 检查没有解决问题。依赖版本 artemis-odb 2.3.0 的源码说明：

1. 删除会先通知实体订阅，目标从 activeEntityIds 中移除。
2. 组件和实体编号的清理在 `World.process` 整轮结束时进行。
3. `EntityManager.isActive` 只检查编号是否已回收；在这一时间间隔里它和组件 mapper 仍可能返回旧目标存在。

这与原版“单位表已无有效引用，但旧内存仍可读”是不同层面的同类问题。修复让 `InfernoPresentationSystem.emit` 先检查自身 Position 订阅的有效成员位，再使用目标组件。没有提前强制回收 ECS，也没有改动其他系统的删除顺序。

## 覆盖的五个边界

- 消费前目标移动：冰流使用消费时的目标位置。
- HP归零但位置仍保留：解析器仍能使用该目标位置，不新增死亡过滤。
- 目标退出场景：不使用等待回收的旧 Position，回到本地保留的对地点；尾流最终清理。
- 施法者真实 DeathEvent：Actioneer 清除 Casting 后，待动作不再发布冰流。
- 施法者实体被删除：待动作不会产生无主冰流，后续没有残留。

这些是精简 ECS 中的实际事件/输入管线回归，不是完整游戏画面或原版死亡样本。施法者 DeathEvent 检查待动作和 Casting 清理，没有重新验收身体 DT/DD 播放、尸体装备、死亡声音或复活流程。

## 验证与后续范围

最终40项限定回归通过：新增5项，加上既有35项阶段/转向、数量、松手、起手层、400个单冰流状态、冷资源，以及法师地狱之火/强化和声音生命周期。失败基线和无效的 isActive 尝试日志均保留，没有将它们当作成功结果。

当前修复覆盖目标移除尚未回收的更新窗口。数字实体ID回收后被另一对象复用、联网目标重绑定、完整目标怪物死亡操作、所有角度/碰撞和GPU朝向仍未验收；不能宣称已重建原版全部目标生命周期规则。

- 测试：`core/src/test/java/com/riiablo/engine/server/ArcticBlastTargetLifecycleTest.java`。
- 静态入口：`tools/original-observer/inspect_arctic_target_lifetime.py`。
- 最终日志：`tools/original-observer/arctic-blast-target-lifecycle-final-validation-2026-11-22.log`。
- XML：`tools/original-observer/arctic-blast-target-lifecycle-test-results-2026-11-22/`。
- 来源/清理/哈希清单：`tools/original-observer/arctic-blast-target-lifecycle-validation-2026-11-22.json`。

前一阶段的原版转向/收尾记录继续有效，见[转向与收尾](arctic-blast-retarget-release-analysis-2026-11-21.md)；本轮没有新的技能或DCC/GPU覆盖。
