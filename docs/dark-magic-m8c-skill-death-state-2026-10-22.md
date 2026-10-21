# M1-A/M8-C 技能、死亡与状态表现归属（2026-10-22）

本批集中迁移 `SkillCastHandler`、`DeathHandler`、`ElementalHitPresentationSystem`、`NativeShrinePresentationSystem`、`StateOverlaySystem` 至 `core/src/main/java/com/riiablo/presentation`，删除旧包文件，不保留兼容副本。客户端装配、输入系统、网络尸体恢复、GameScreen、Skill Viewer、桌面日志和集成测试使用同一组新类型；装配相对顺序保持不变。

神殿图标和本地化名称由 `ShrinePresentation` 统一解析，实体工厂仅委托初始标签生成，神殿快照系统不再反向调用工厂的显示辅助方法。删除无人调用的全局查询重载。状态颜色恢复和 COF 变换共用的 `MonsterTransform` 一并归入表现层，原 COF 适配器调用同一实现；调色板选择与资源缺失处理保持不变。

技能/状态/冰碎效果的包内测试随实现迁移；权威区域技能测试拆分为表现策略测试和网络只读快照测试，两者的包内方法均保持原可见性。客户端完整装配测试覆盖本地/远程与 Desktop/Android/iOS 的类型归属及完整顺序。架构门禁禁止旧位置重新出现，并禁止服务端/共享机制直接引用本批表现类。回归任务新增权威导弹复用、玩家尸体/死亡成长、Blessed Hammer、Fist of Heavens、COF 切换、实体工厂与组合动画可见性验收。

扩充回归发现两份历史尸体夹具只覆盖 `Map.find`，没有提供复活守卫要求的城镇 zone。夹具改为显式提供 `(99,101)` 的 `townPlayerSpawn` 和稳定的城镇 zone；保留死亡标记清除、装备分离/取回、HP/移动恢复、独立尸体和成长/掉落断言，不修改生产复活逻辑或放宽断言。

提交：`75d749a18`（技能/死亡/状态表现归属、辅助映射和回归门禁）。

验证命令：

```powershell
.\gradlew.bat :core:compileTestJava --no-daemon
.\gradlew.bat :core:clientUiBoundaryRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
.\gradlew.bat :desktop:compileJava :server:d2gs:compileTestJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

结果：完整客户端回归 369 项，其中 368 项通过、1 项因未配置 `D2_AUTOMAP_FIXTURES` 可选原版存档样本跳过；架构门禁 11 项、D2GS 定向回归 20 项通过。合计 399 项通过、1 项跳过，失败/错误均为 0。core 生产/测试、desktop、D2GS 生产/测试、Netty、Map Viewer、Skill Viewer 编译通过。直接表现扫描仍为 0；M1-B 生成层仍为 0、运行时适配器仍为 58 条，无基线漂移。本批暂存差异通过 `git diff --cached --check`；`SoundP.java` 仅保留原有尾部空行改动，不在提交中。

真实资源：使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F`；相关 headless 测试读取 MPQ 数据表、字符串和动画资源。未启动真实音频设备、GL 窗口或完整竞技场。

D2MOO 依据：沿用现有 1.10f Skills/Missiles 的客户端回调与服务端导弹复用、States/Overlay 视觉恢复、Shrines 的 Code 与 ShrId 本地化，以及 Clients.cpp 的城镇玩家出生点。保留尸体装备、DT/DD、冰碎效果和物理清理行为；服务端战斗、AI、任务、数值与协议均未修改。

本批完成的是归属迁移，M1-A/M8-C 仍为进行中，M1 整体尚未完成。剩余 owner 与删除条件：

- M8-C 客户端实体与 COF/动画适配：`ClientEntityFactory` 仍直接装配表现组件并调用资源/UI/overlay/飞行声适配器，COF/动画系统仍在 `engine/client`。在实体创建事实与只读快照由表现订阅层消费、资源等待和释放由表现会话统一管理后，删除这些直接调用。
- M8-C 技能与神殿兼容路径：`SkillCastHandler` 仍借助 `ClientNetworkReceiver`/`ServerSkillSystem` 的存在判断本地/联网权威导弹；神殿系统仍有确定性种类补齐及 COF 收敛写入。在表现输入显式携带权威归属与完整神殿状态、无须探测/补写仿真 world 后删除兼容路径。
- M8-C 与 M2/M3 死亡生命周期边界：`DeathHandler` 仍写入 PlayerCorpse、装备、HP、位置、组件和 Box2D，GameScreen、输入与网络恢复仍直接调用它。唯一权威生命周期入口承担这些状态变更、表现端仅消费死亡/复活/尸体快照后，删除混合职责及 host 直调入口。迁包不能视为该出口已完成。
- M8-C 表现会话与控件：`StateOverlaySystem`/`MonsterTransform` 仍有全局 colormaps 兼容读取，context/widget 仍保留 screen 和全局资源入口。在会话显式持有资源且可独立装配/释放后删除兼容读取。
- M8-D 协议、传输与权威会话收敛，以及既有 M1-B 地图运行时适配债务，继续按各自 owner 跟踪。

下一迁移：M1-A/M8-C 客户端实体与 COF/动画表现适配，集中处理装配、资源等待/释放和会话依赖；死亡权威职责与事件/快照出口按上述 M2/M3 依赖收敛。
