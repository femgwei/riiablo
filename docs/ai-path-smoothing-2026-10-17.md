# AI 移动精度第五批：路径平滑碰撞契约

基线：`fa86f78bd`。

## 范围与依据

接入点核查仍未找到可信的运行期 raw fp16 来源；不能从 Position 浮点坐标
反推原生低位精度并宣称原版等价。本批不新增虚假的 raw 组件或切换寻路。

上一批修正了 findCollision，而 collides 仍只检查中心格；PathSmoother 则使用
size/2 的两条横向偏移射线，将单位尺寸当成半径。两条接口的占地语义不一致。

本地 D2MOO `source/D2Common/src/D2Collision.cpp` 的
COLLISION_CheckMaskWithSize（约 775 行）定义：POINT 为中心格、SMALL 为十字、
BIG 为 3×3。这里仅据此统一当前宿主碰撞契约，不宣称 PathSmoother 是原版算法。

collides 现复用 findCollision 的原生尺寸/掩码/终点检查，使用实例私有 scratch，
不逐次分配输出对象。PathSmoother 直接检测中心线的完整占地，不再叠加横向偏移。
采样仍是每格五次的浮点射线，不是原版 fp16 移动或逐格 Bresenham 精度。

## 修正前证据

新增回归首轮 8 项中两项失败：

- 合法三格门的五点直线路径应简化为两点，原实现保留五点。
- 尺寸 2 的零长度射线，findCollision 检出邻格占地阻挡，但 collides 返回无阻挡。

保留另外的绕孤立墙、安全线段、空/单点/双点路径回归；没有声称这些都在旧版失败。

## 生产边界

调用链是旧 Entity → Map.smoothPath → MapGraph → PathSmoother。
当前 ECS Pathfinder 使用独立的 smoothPathWithDynamicCollision，没有改为调用它，
也没有把本批当作 ECS 怪物寻路升级。通用生产搜索仍为 Map → AStarPathFinder。
PathSmoother 不接动态单位层；本批也未添加开门策略、原生运行时 Act/Zone、
raw/live 字段、共享 owner seed 或客户端原版逐帧对照。

后续应优先为 raw 坐标建立可核验的生成/更新来源，再连接现有 native Reader；
只读快照、独立移植算法测试和浮点投影不能替代这个来源。

## 验证

修正后定向 70 项通过：射线占地 5、平滑器 3、人工地形追踪 6、
大型单位接近点 1、恢复/生命周期 27、房间边界 2、Pathfinder 14、动态碰撞 12。
人工地形和宿主契约测试不等于原版运行对照，也没有扩大自然地图/Boss 覆盖。
203 项地图回归通过（无失败、错误或跳过），architectureRegression 通过。

复验（D2_HOME 指向合法安装目录）：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.map.pfa.RaycastFootprintTest --tests com.riiablo.map.pfa.PathSmootherFootprintTest --tests com.riiablo.engine.server.PathfinderTerrainPursuitTest --tests com.riiablo.engine.server.PathfinderLargeTargetApproachTest --tests com.riiablo.engine.server.PathfinderRecoveryTargetTest --tests com.riiablo.engine.server.PathfinderRoomBoundaryTest --tests com.riiablo.engine.server.PathfinderTest --tests com.riiablo.engine.server.DynamicUnitCollisionSystemTest :core:mapRegression :core:architectureRegression --no-daemon --max-workers=1
```

本机日志：`build/ai-smoothing-20261017-tests.log`。
