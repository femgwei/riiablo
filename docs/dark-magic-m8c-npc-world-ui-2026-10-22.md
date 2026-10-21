# M1-A/M8-C NPC 与世界 UI 迁移（2026-10-22）

本批实现提交：`7722a22a8`；M1-A/M8-C 整体仍进行中。

客户端 NPC、菜单/对话、任务对话与指示器、世界/怪物标签、名称解析和区域提示已从 `engine/client` 迁至 `presentation`。`MenuWrapper` 同步迁至 `presentation/component`，旧包路径删除；客户端装配、GameScreen、Offscreen lifecycle、widget 及 map/skill viewer 已更新为新类型。

NPC 不再从 `Riiablo.engine`、`Riiablo.game`、`Riiablo.charData`、`Riiablo.audio` 或 `Riiablo.files` 读取会话状态；通过所属 Artemis world、`ClientGameplayContext` 和原有 host panel 完成交互。任务消息提交、商店/复活、问候和 healer 音频语义保持原行为。怪物标签延迟到 Artemis 注入后创建控件，避免 headless 构造时读取未绑定字体/颜色。

Map Viewer 的 `Map.injectBuilders(World)` 和 Skill Viewer 的 `Map.setEntityFactory` 旧调用均改为 `MapRuntimeBindings.bind(...)`，并显式注册 factory/context；Skill Viewer 的 `CofDirtyComponents` 改用既有 shared 类型。M1-B 地图生成边界未重新引入 ECS 或实体工厂依赖。

验证命令：

```text
./gradlew.bat :core:clientUiBoundaryRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest :desktop:compileJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F" --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
```

结果：UI 回归 115 项、架构回归 8 项、D2GS 装配/竞技场 19 项全部通过；失败、错误、跳过均为 0；core、desktop、D2GS、Netty、Map Viewer、Skill Viewer 编译通过。表现扫描由 72 条降至 50 条（音频 40、图形 10），直接 screen/widget 违规为 0，基线只删除本批消失的 22 条。

真实资源：是。`Act1QuestIndicatorSystemTest`、`MonsterNameResolverTest` 和 `SharedBoxLifecycleTest` 通过 `RiiabloTest` 加载真实 MPQ 数据表/字符串；其余控件和设备使用测试替身，不启动实际 GL 客户端或完整竞技场。

D2MOO 依据：保留 1.10f NPC healer 声音事件、问候回退、商店/复活服务、任务消息提交、标签布局和有限避让语义；不修改服务端 NPC、任务规则、战斗或协议。

剩余范围：表现层仍有 40 条音频和 10 条图形依赖，部分 widget 仍读取兼容全局资源；下一批继续收口音频播放、环境/天气和渲染资源适配。
