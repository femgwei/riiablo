# M1-A/M8-C UI host 端口迁移（2026-10-22）

本批已完成，M1-A/M8-C 整体仍进行中。实现提交：`f7961320d`。

## 边界与行为

- 新增 `presentation/ClientUiServices`，提供游戏界面、背包、移动阻断、佣兵头像查询，以及药剂、雇佣兵面板和交易面板意图。`ClientGameplayContext` 作为会话内适配器持有具体 screen/panel，键盘移动系统只依赖端口。
- 鼠标输入、物品管理、网络接收、实体标签创建及神殿标签更新共六个消费者不再直接引用 `screen/widget`，也不再调用 `clientContext.game()`。世界标签仍使用原字体、居中、背景和文本更新逻辑；这些消费者仍有 Scene2D/context 等旧表现依赖，本批不声称其已成为纯仿真系统。
- 网络消息改为 `GameScreen` 注入 `Consumer<String>`；缺少输出绑定时安全跳过。NPC/交易结果仍优先使用 `ClientNetworkSession.ResultRouter`，旧 fixture 的兼容回调由表现适配器承担。
- D2GS 和怪物竞技场删除无服务端调用者的 `MenuManager`/`DialogManager` 注册，其他系统相对顺序保持不变。新架构检查禁止两个配方重新引入客户端/UI 依赖。
- 表现依赖基线从 83 条降至 72 条，只删除本批消失的 11 条，未增加允许项。新架构检查禁止六个消费者重新直接访问 screen/widget。

## 验证

```text
./gradlew.bat :core:clientUiBoundaryRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest :desktop:compileJava :server:netty:compileJava "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F" --no-daemon
powershell -NoProfile -ExecutionPolicy Bypass -File tools/architecture/check-client-presentation-boundary.ps1 -FailOnDrift
```

共 136 项：UI 回归 111 项、架构回归 6 项、D2GS 装配 5 项、竞技场测试 14 项。失败、错误、跳过均为 0；core、desktop、D2GS 和 Netty 编译通过。

新增测试覆盖无 screen/panel、动态界面查询、两个会话间 UI 意图与结果回调隔离，以及已迁移消费者和 dedicated 配方的依赖方向。已有回归覆盖鼠标输入、拾取、任务表现、共享控件/资源生命周期和客户端装配顺序。

本批门禁暴露并修正两处既有测试问题：装配期望补上已经存在的 `SoundEventSystem`；标签重叠辅助函数改为既有的纵向 0px/横向 2px 间距，密集样本由 18 个增至 80 个以验证有限搜索耗尽后的重叠回退。生产标签布局没有修改。

扫描结果：`M1-A findings: 72`，`M1-A boundary drift: none`。同一构建内 M1-B 生成层仍为 0 条，运行时适配器 58 条，无漂移。

真实资源说明更正：命令指定 1.10F 路径，任务指示器和共享控件生命周期测试继承 `RiiabloTest`，会加载真实 MPQ 数据表与字符串；实际控件/设备仍使用测试替身，不启动真实 GL 客户端或完整竞技场。本次通过不等于真实客户端视觉验收，地图资源验收沿用 M1-B 的单独记录。

后续迁移已继续完成 NPC/世界 UI 的表现归属，当前状态见 `dark-magic-m8c-npc-world-ui-2026-10-22.md`；本记录中的 72 条为本批完成时的历史值。

## D2MOO 与后续范围

本批保留既有 1.10f/D2MOO 实现的物品拾取、佣兵药剂、NPC/交易结果、权威 Tick 与数值规则，只改变 UI/host 的调用归属和装配依赖。没有新增 D2 公式或修改协议、战斗结算。

剩余 owner 和删除条件：NPC/menu/dialog、标签显示、区域提示及渲染/音频由 M1-A/M8-C 继续迁入表现 owner；当语义事件/快照及会话端口覆盖原路径后删除客户端系统中的直接设备、资源、widget 访问与兼容全局入口。当前具体 context 和 Scene2D 依赖仍在，尚未达到表现只能订阅事件/快照的完成条件。

下一步集中处理 NPC/menu/dialog 和标签/区域提示的表现依赖，沿现有 session/port 收口；M8-D 网络三层收敛、M7-A 规则策略和 M7-B 地图代际继续按原计划跟踪。
