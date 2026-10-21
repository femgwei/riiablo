# M1-A/M8-C 客户端实体与 COF/动画归属（2026-10-22）

本批完成客户端实体工厂、COF 解析/加载/缓存/卸载、透明度与变换、动画/overlay 步进、方向解析、物品落地效果适配器的归属迁移。13 个生产适配器迁至 `core/src/main/java/com/riiablo/presentation`，8 个仅客户端组件迁至 `presentation/component`；6 个 COF/动画/实体回归测试及 1 个 1.10f 资源测试同步迁移。客户端装配、输入/网络接收、GameScreen、Map Viewer、Skill Viewer 和类型门禁统一使用新归属。

13 个适配器为 `ClientEntityFactory`、`AnimationStepper`、`CofAlphaHandler`、`CofLayerCacher`、`CofLayerLoader`、`CofLayerUnloader`、`CofLoader`、`CofResolver`、`CofTransformHandler`、`CofUnloader`、`DirectionResolver`、`OverlayStepper`、`ItemEffectManager`。8 个组件为 `AnimationWrapper`、`BBoxWrapper`、`CofComponentDescriptors`、`CofDescriptor`、`CofLoadingComponents`、`CofWrapper`、`Label`、`Overlay`，旧包文件删除，不保留兼容副本。`Hovered` 保持在输入组件归属。

本批只改变包归属和依赖引用，保留资源等待、引用计数、缺失 COF 回退、实体 ID 重用、对象动画起始帧及系统顺序。`RenderSystem` 的 3 条组件依赖改为精确 presentation 类型例外；没有扩大整个 presentation 包的允许范围。工厂仍继承 `ServerEntityFactory`，COF 管线仍读取同一 world 的可变状态；现有资源访问继续使用所属 `ClientGameplayContext`。本批没有新增会话隔离实现或独立的双 world COF 验收，完整事件/快照与会话收敛继续登记为 M8-C 债务。

架构门禁覆盖新旧位置唯一归属，禁止 `engine/server` 和 `engine/shared` 引用 presentation；装配测试核对本地/远程及 Desktop/Android/iOS 的新类型归属与原系统顺序。回归任务通过 `presentation.*` 纳入迁移后的所有 COF/动画测试，并补入对象/佣兵快照、原生野蛮人表现和客户端尸体测试。1.10f COF 测试与包内夹具一起迁移，没有扩大辅助方法可见性。

提交：待提交（本批）。

验证命令：

```powershell
.\gradlew.bat :core:compileTestJava --no-daemon
.\gradlew.bat :core:clientUiBoundaryRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
.\gradlew.bat :desktop:compileJava :server:d2gs:compileTestJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

结果：完整客户端回归共 383 项，382 项通过、1 项因未配置 `D2_AUTOMAP_FIXTURES` 的可选原版存档样本跳过；架构回归 12 项、D2GS 定向回归 20 项全部通过。合计 414 项通过、1 项跳过，失败/错误均为 0。core 生产/测试、desktop、D2GS 生产/测试、Netty、Map Viewer 和 Skill Viewer 编译通过。M1-A 扫描仍为 0；M1-B 生成层 0、运行时适配器 58，无基线漂移。

真实资源：使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F`；相关 headless 测试读取 MPQ COF/DCC、数据表和字符串，GPU 上传/提交使用既有 mock，没有启动 GL 窗口、真实音频设备或完整竞技场。`SoundP.java` 结尾原有空行保留，不属于本批提交。

D2MOO 依据：沿用现有 1.10f COF/组合动画、Objects.txt 的 Start/CycleAnim/FrameDelta、对象缺失模式回退、技能序列帧及权威快照行为；不修改战斗、AI、任务、地图生成、数值或网络协议。

剩余 owner 与删除条件：

- M8-C 客户端实体适配：`ClientEntityFactory` 仍继承服务端工厂，创建实体并写入 COF、交互、Selectable、Box2D 等组件，还直接调用标签、菜单、overlay、资源和声音适配器。实体创建事实与完整快照由表现订阅层消费、权威生命周期由 M2/M3 唯一入口承担后，删除这些混合写入和直调。
- M8-C COF/动画适配：管线已归入表现层，仍读取可变的 `CofReference`/`AnimData`/技能状态并通过 `CofManager` 更新共享元数据。建立独立的只读表现输入与资源生命周期所有权后，删除这些仿真 world 兼容路径；迁包不代表输入已完成快照化。
- M8-C 技能/神殿与 M2/M3 死亡生命周期：沿上一批记录，仍有本地/联网系统探测、神殿种类补齐、尸体/装备/HP/位置/Box2D 写入。显式权威归属和完整状态进入表现输入、死亡/复活由唯一权威入口承担后，删除兼容路径。
- M8-C 会话资源：`StateOverlaySystem`/`MonsterTransform` 的全局 colormaps 读取与 context/widget 的 host/全局兼容入口仍存在。显式会话资源绑定和独立装配/释放完成后删除，现有 COF 管线使用 context 不能替代完整隔离验收。
- M1-B 的运行时适配债务及 M8-D 的协议/传输/权威会话收敛保持原 owner。M1-A/M8-C 仍为进行中，M1 不标记完成。

下一迁移：集中处理客户端实体、COF/动画和死亡/技能适配器的事件/只读快照出口，不再按单个适配器拆批。
