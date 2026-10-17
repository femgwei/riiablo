# AI 固定点接入第二步：骨牢段的整数分配入口

## 范围与依据

为 ServerEntityFactory.createDiabPrisonSegment 接入上一批 Position 的显式精度契约。
本地 D2MOO `D2Game/src/MONSTER/MonsterSpawn.cpp` 的 sub_6FC6A810
（约 1517 行）使用目标的整数单位坐标，配合四个 (±1, ±1) 固定偏移创建骨牢段。
`D2Common/src/Path/Path.cpp` 的 PATH_AllocDynamicPath（#10152，约 703 行）
用 PATH_ToFP16Center(nX/nY) 建立新动态路径的初始精度。

现有宿主骨牢入口接受精确的 int 落点、执行静态与动态生成碰撞检查、不做自由落点漂移。
成功创建新段后，以这些分配参数建立 fp16 格心；宿主显示坐标保持原整数值。
这是新分配的坐标来源，不是恢复目标或已有骨牢段丢失的低位。
当前宿主目标取整方式仍沿用原实现，不由本批证明其与原版 live 坐标一致。

## 改动

- 骨牢入口在分配前拒绝负值、零与超过 unsigned 16-bit 域的坐标，不截断或绕回。
  零坐标原本已拒绝；uint16 范围检查是适配器的显式输入约束。
- 成功创建的新段调用 initializeNativeCell；检查失败时不建立精度状态。
- 普通 float 怪物初始化/重建显式失效旧 raw 状态，包括坐标投影完全相同的重建。
  不因 float 恰好为整数而将普通出生升级为可信 raw 来源。
- 不更改怪物决策、碰撞占地、伤害、骨牢联动死亡或存活时长。

## 回归与边界

扩展现有真实 ServerEntityFactory + 施法/动画/碰撞的合成竞技场回归：
Diablo 与 Clone 创建的四段均保留正确 fp16 格心；非法输入不增加怪物；
普通 float 出生未知，显式赋予 raw 后同位置重建仍会失效。
继续复验原有组死亡、阻挡、目标失效、town、对象目标和 ID 复用测试。
同时复跑移动失效、浮点网络同步失效及路径恢复/缓存回归。

构建使用上一批独立输出和缓存目录，避免与另一个聊天竞争默认编译输出。
首轮骨牢 10 项被旧夹具漏注册 townPortalRegistry 阻断，未进入行为断言；补齐命名注册后，
最终 46 项通过（骨牢 10、移动 2、网络同步 1、路径恢复 33），失败/错误/跳过为 0，
architectureRegression 通过。日志：`build/ai-position-spawn-final.log`；
首次失败日志：`build/ai-position-spawn-tests.log`。普通串行环境复验命令：

```powershell
.\gradlew.bat :core:test --tests com.riiablo.engine.server.DiabPrisonIntegrationTest --tests com.riiablo.engine.server.VelocityAdderTest --tests com.riiablo.engine.server.component.serializer.PositionSerializerTest --tests com.riiablo.engine.server.PathfinderRecoveryTargetTest :core:architectureRegression --no-daemon --max-workers=1 --console=plain
```

只接通上述精确整数分配入口，常规地图/任务出生仍走旧 float 接口。
骨牢本身不是通用追踪移动的验证对象；尚未接入 NativePathUnitBinding、原生逐帧移动、
live 路径状态/Act/Zone/shared seed，也未新增自然地图、图形客户端或 DLL 动态对照。
