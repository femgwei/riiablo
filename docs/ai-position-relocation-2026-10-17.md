# AI 固定点接入第三步：纠偏与传送的失效边界

骨牢段已能从精确整数分配参数初始化 raw fp16，但公开 Vector2 的投影比对只能发现
当前值不同，不能发现两次校验之间的“移动再返回”或同值传送。
本批只修宿主状态有效性，不改变原版移动算法或猜测传送后的低位。

## 改动

- DynamicUnitCollisionSystem 成功选择纠偏落点时，先使 raw 状态失效，再提交坐标。
  未被纠偏的其他单位保持原状态；没有候选落点时不新增失效写入。
- WarpInteractor.commitTransition 在实际提交传送时无条件使 raw 状态失效，
  即使 arrival 与旧显示坐标完全相同，也不把旧世界位置的低位带到新上下文。
- 不从新落点浮点值重新生成 raw，不改变传送选点、碰撞网格、动作、房间或事件顺序。

## 回归

PositionRelocationPrecisionTest 增加两项：

- 调用真实传送提交方法，arrival 与原投影相同，确认 raw 仍失效。
  命令停止使用 mock，直接配置真实 ECS mappers；不冒充完整门户操作集成测试。
- 真实动态碰撞系统将重叠的新生单位纠偏，在未查询 raw 的情况下再把 Vector2
  返回旧值，仍必须未知；未纠偏的玩家 raw 保持可信。
  这是宿主空地/无地图夹具，不是自然地图或原版碰撞差分。

同时复验 DynamicUnitCollisionSystemTest、WarpArrivalCollisionTest、
DiabPrisonIntegrationTest 与 architectureRegression。
日志：`build/ai-position-relocation-tests.log`，构建使用独立输出/缓存。
结果：26 项通过（精度失效 2、动态碰撞 12、传送落点 2、骨牢 10），
失败/错误/跳过均为 0；architectureRegression 通过。

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.server.PositionRelocationPrecisionTest --tests com.riiablo.engine.server.DynamicUnitCollisionSystemTest --tests com.riiablo.engine.server.WarpArrivalCollisionTest --tests com.riiablo.engine.server.DiabPrisonIntegrationTest :core:architectureRegression --no-daemon --max-workers=1 --console=plain
```

## 仍待

当前可信生产创建入口仍仅限骨牢段。复活、跳跃、技能落点、导弹及其他直接 Vector2
写入尚未全部迁移；不能对一般实体启用原生移动或宣称全部写入历史可被检测。
通用生产寻路与浮点积分不变，NativePathUnitBinding / live Act/Zone/seed 仍未接通。
