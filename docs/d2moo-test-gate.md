# D2MOO 双层测试门槛

每个原生对齐模块必须同时提供两层回归：

1. **纯数据/边界层**：只构造 Excel 行或原生值，验证难度索引、缺失列、概率边界、固定点溢出和 RNG 消耗等确定性规则。
2. **ECS/地图集成层**：启动 headless Artemis world 或地图构造器，验证数据进入实体、碰撞、事件和网络快照后的可观察结果。

测试命名约定：纯数据测试使用 `Native*Test`，集成测试使用 `*IntegrationTest`；两者都必须运行在 `:core:test` 的 headless 配置下。需要真实 D2 资源的测试通过 `D2_TEST`/`D2_HOME` 注入，不得把本机绝对路径写入测试。

当前门槛样例：

- `NativeDataTablesTest`：难度列和群组边界的纯数据测试。
- `NativeUnitFlagsTest`：UnitFlags 原生位和 ECS 组件生命周期测试。
- `FallenShamanAutoCombatIntegrationTest`、`DualClientFallenLootIntegrationTest`：怪物复活、掉落和双客户端集成测试。

提交前至少运行对应模块的两层测试；任一层失败时，路线图中的模块保持未完成状态。

## 真实画面启动门槛

合成 UI 场景不能覆盖 `GameScreen`、DRLG、营地实体和第一帧 ECS。涉及客户端世界接线、
任务、AI、地图或渲染的修改，还必须运行 1 像素隐藏窗口测试。此外，从 2026-09-07
起，每完成一个较大的、完整功能模块移植，无论当前是否具备人工验证条件，都必须运行
一次该真实营地测试：

```powershell
.\gradlew.bat :desktop:offscreenCamp `
  '-Pd2Home=<Diablo II 1.10f 目录>' `
  '-PsavesDir=<可写的空目录>' `
  '-PvisualOutput=<结果目录>' `
  '-Pd2Version=1.10f'
```

该测试使用固定地图种子创建亚马逊，执行 Act 1 全地图生成、Rogue Encampment 对象/NPC/
玩家创建及三个真实渲染帧。成功时输出 `rogue-encampment-manifest.txt` 和真实 1x1 帧缓冲
截图；客户端线程异常、120 秒超时和初始化失败均必须让 Gradle 返回非零退出码。

`offscreenRender` 仍用于 854x480 FBO 合成 UI 场景，两者不能互相替代。

`libd2` / `dark-magic` 可借鉴的测试模式及 1.14d fixture 限制见
[`external-test-reference-audit.md`](external-test-reference-audit.md)。
