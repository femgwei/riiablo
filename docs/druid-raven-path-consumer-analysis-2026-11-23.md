# Raven 路径消费者与朝向核对

## 已确认的对应关系

原版 Fn107 的路径请求与 Riiablo 当前 NativePathRuntime 映射一致：

- 围绕主人使用 path type 5/6，对应方向偏移 `+2/-2`。
- 追击目标使用 path type 13，并保留动态目标 GUID。
- 原版 dynamic path 的 step budget 为 20；Riiablo native path request 也设置 distance=20。
- 原版路径推进先产生 path point，再由速度/位置消费者推进；Riiablo 由 `NativePathRuntime.advance` → `VelocityAdder.moveNativeMonster` 消费 native waypoint，未回退到普通 A*。
- Riiablo 的 `Pathfind` 保存 native waypoint、当前索引、bounded 标记和目标 token；路径完成或目标回收时不会继续追踪旧实体。

## 原版数据

`20261123-093625-8d3ad39b` 的逐服务端帧/决策采集显示：

- 目标接近期间出现 type 13；盘旋/主人移动期间出现 type 5/6。
- 盘旋样本 path distance=20、path step number=0；路径点数量通常为 3。
- client/server 模式均收到 WL（2）、A1（4）和 NU（1），A1 之外没有把移动错误地当成攻击。
- current direction 与 new_direction 并非同一字段：new_direction 在路径段变化时更新，不能直接把它当作已经提交的最终动画方向。

`20261123-094036-fb75741b` 的城镇采集没有目标，Raven 只在主人周围移动：26 次 Fn107 不消耗预算，主要使用 type 6，并观察到 218 个不同定点位置。

## Riiablo 消费链

Riiablo 当前已具备以下保护：

1. `NativePathRuntime.request` 在地图、碰撞、native precision 或 room topology 不满足时返回 unsupported/rejected，不偷偷把 native 路径降级成 A*。
2. `NativePathRuntime.advance` 只在 25 Hz authoritative tick、位置精度仍匹配旧 cursor 时推进；传送/校正会停止旧路径。
3. `VelocityAdder` 对 native waypoint 使用 fp16 center 和 native velocity，并把实际位移向量写入 Angle；不会把 host 浮点路径重新喂给 native movement。
4. `Pathfinder.updateMovementFacing` 对相邻方向施加稳定帧迟滞，大角度变化立即接受；当前方向由实际位移向量更新，避免把 D2 direction id 当成 radians 表索引。

## 本轮验证

定向执行通过：

- `NativePathRuntimeTest`
- `NativeMonsterMovementTest`
- `VelocityAdderTest`
- `PathfinderTerrainPursuitTest`
- `RavenAiIntegrationTest`

日志：`tools/original-observer/druid-raven-path-consumer-validation-2026-11-23.log`。这些测试覆盖 native path 请求、bounded waypoint、精度推进、动态阻挡、方向更新和 Raven 的盘旋/接近请求，共同通过。

## 尚未宣称的部分

这轮没有发现需要修改路径消费者的明确错误，因此没有强行改动 `NativePathRuntime` 或 `VelocityAdder`。仍缺少真实原版与 Riiablo 在同一障碍布局下的逐 waypoint 坐标配对；也没有证明所有 16 个方向的 DCC 动画帧、new_direction 提交时刻和客户端插值完全一致。下一步若要继续，应建立同地图障碍的 waypoint replay，再核对坐标、方向提交和动画帧，而不是重复空跑 Raven 攻击。
