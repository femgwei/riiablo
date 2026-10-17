# AI 固定点接入第一步：Position 的显式精度契约

基线：`f12346dc7`。本批不切换生产寻路或移动积分。

后续：首个精确整数生产分配入口已另批接入，见 [骨牢段分配](ai-position-spawn-2026-10-17.md)。
下文“尚无生产入口”描述本批完成时的历史边界。

## 已核对的入口

- ServerEntityFactory 的常规怪物初始化仍接受 float x/y；不能当作原版整格分配输入。
- VelocityAdder 仍按 world.delta 与浮点速度积分；不是 D2Common 原生逐帧步进。
- 跳跃、传送、复活、导弹等还有直接 Vector2 写入。
- PositionSerializer 的 PositionP 只携带浮点坐标，不携带 fp16 低位。

因此不对既有实体自动建立 raw 坐标，也不从浮点位置反推原版精度。

## 本批契约

Position 新增显式 initializeNativeCell / setNativePrecision 入口：

- initializeNativeCell 只接受明确的 unsigned 16-bit 整格输入，使用已移植的
  D2PathStep.toFp16Center。依据本地 D2MOO Path/Path.cpp 的 PATH_AllocDynamicPath
  （#10152，约 703–719 行）及原版中心坐标初始化规则。
- setNativePrecision 接受完整 uint32 位模式，原样保留低位，包括 Java 负 int。
  调用方必须提供真实权威来源，不能把浮点乘 65536 后传入以伪装来源。
- 显示投影为 unsigned(raw)/65536 - 0.5：这是适配当前 Map.round 以整数表示格心的
  宿主约定，不是修改原版 fp16 定义。投影是有损的，不用于回填 raw。
- 未显式初始化时精度未知，读取 raw 抛出异常，不返回伪造的零。
- 检测到 Vector2 偏离已知投影时，未知状态会锁存；恢复旧浮点值不会恢复信任。
- VelocityAdder 每次实际改变位置后显式失效，即使后来走回原处也不能重用旧 raw。
  静止帧不丢弃原有低位。
- PositionSerializer 收到仅浮点的同步包时无条件失效，即使投影值相同。
- 池复用 reset 清除状态。

## 边界

这是显式写入与读取契约，不是完整原生移动组件、DynamicPath Reader 或生产坐标源。
目前没有生产创建入口调用上述 raw 初始化，也没有将这个状态接到 NativePathUnitBinding。
已有 Vector2 保持兼容。公开字段意味着不能检测任意旧写入历史：如果其他写入者在
两次校验之间改动又恢复同一投影，且没有主动失效，则仅凭最终浮点值无法识别。
后续启用任何原生实体之前，必须收口该实体所有位移/传送/复活写入并明确生命周期；
不能把本批投影校验当成所有旧写入已迁移的证明。

## 验证

新增领域测试覆盖浮点非来源、显式格心、无符号/低位与有损投影、旧写入失效、
输入域与池重置；核心测试覆盖浮点积分失效/静止保留和同值浮点网络包失效。
同时复跑 PathfinderRecoveryTargetTest，包含上一批尚未执行的缓存回归。

首轮领域 5 项通过，核心编译因另一聊天当时尚未完成的 AI 方法调用失败；未更改其代码。
第二次尝试因并行构建写入相同输出目录而失败（`build/ai-position-contract-final.log`）。
改用独立 buildDir 与 project-cache-dir 重跑，日志：`build/ai-position-contract-isolated.log`。
独立首轮核心 36 项中 35 项通过；新增移动夹具漏注册 DynamicUnitCollisionSystem，
在补齐与既有夹具一致的禁用碰撞服务后复跑，最终日志：`build/ai-position-contract-verified.log`。
最终 41 项定向测试通过（领域坐标 5、移动 2、序列化 1、路径恢复 33），
失败/错误/跳过均为 0；architectureRegression 通过。
普通无并行构建环境可用以下命令复验：

```powershell
.\gradlew.bat :server-domain:test --tests com.riiablo.engine.server.component.PositionNativePrecisionTest :core:test --tests com.riiablo.engine.server.VelocityAdderTest --tests com.riiablo.engine.server.component.serializer.PositionSerializerTest --tests com.riiablo.engine.server.PathfinderRecoveryTargetTest :core:architectureRegression --no-daemon --max-workers=1 --console=plain
```

未新增原版 DLL 动态对照、自然地图、客户端或 Boss 行为验收。
