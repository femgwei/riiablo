# M1-A/M8-C 音频与图形依赖收口（2026-10-22）

本批完成剩余 40 条音频和 10 条图形扫描项的迁移。音频会话、声音事件/环境/脚步/怪物/格挡/物件系统、伤害表现观察器、天气、overlay/item/missile 资源加载、地面物品光效、automap 与路径调试绘制移入 `core/src/main/java/com/riiablo/presentation`；仅由表现系统持有播放句柄的 `SoundEmitter` 移入 `presentation/component`。客户端实体工厂保留实体创建职责，导弹飞行声通过 `MissileTravelAudio` 委托，空间增益按所属 Artemis world 的 listener、位置和 zone 计算。

提交：`13ec540f3`（实现）

验证：

```text
.\gradlew.bat :core:clientUiBoundaryRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest :desktop:compileJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

结果：架构回归 10 项通过；客户端表现/音频/图形回归共 332 项，其中 331 项通过、1 项因未配置 `D2_AUTOMAP_FIXTURES` 可选原版存档样本跳过；D2GS 定向回归 20 项通过。合计 361 项通过、1 项跳过，失败/错误均为 0。core、desktop、D2GS、Netty、Map Viewer、Skill Viewer 编译通过。`check-client-presentation-boundary.ps1 -FailOnDrift` 报告 0 条，M1-B 生成层仍为 0、运行时适配器 58 条且无漂移。`SoundP.java` 仅保留尾部空行改动，不在提交中。

回归保留了原有声音键、触发顺序、暂停策略、半径端点、初始音量、环境循环和释放行为。新增测试在两个 world 使用相同实体编号、全局 world/audio 指向另一会话时验证独立播放，覆盖 live listener 切换、跨 zone 静音、缺失位置/设备/声音、飞行声句柄绑定和提交伤害后的怪物痛声；客户端完整装配断言验证新类型归属和原有系统顺序。物件声音测试显式注册所属 context；跨包 helper 断言随实现归属拆出，未为测试扩大 helper 可见性。

实际 GL 窗口测试仍为 `visual` 可选入口，原版 automap 帧导出器不属于回归任务；两者随包迁移保留，smoke 脚本已更新目标类名。缺少存档样本的用例未用自造样本冒充原版验收。

真实资源：是；使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F`。RiiabloTest 相关用例加载真实 MPQ 数据表/字符串；没有启动真实音频设备或完整 GL 客户端。

D2MOO 依据：沿用现有 1.10f Sound/MonSounds/Overlay/Missiles/Automap 表项和声音键；不改变服务端伤害、AI、任务、数值或协议。

剩余问题：表现扫描项已清零，但 M8-C 的最终出口仍要求表现仅订阅事件/快照；`ClientGameplayContext` 与现有客户端适配器仍保留兼容 host 端口，因此 M1-A/M8-C 继续进行中。M1 整体未完成。剩余调用的 owner 和删除条件明确如下：

- M8-C 的客户端实体表现适配：`ClientEntityFactory` 仍调用资源加载、NPC/UI、overlay 和飞行声适配器；在实体创建事实由表现订阅层消费、工厂不再直接调用这些适配器后删除兼容调用。
- M8-C 的技能/死亡/状态表现适配：`SkillCastHandler`、`DeathHandler`、`ElementalHitPresentationSystem`、`NativeShrinePresentationSystem`、`StateOverlaySystem` 等仍位于 engine/client，并调用 context/overlay；在已提交事件/只读快照成为其唯一输入后删除 engine 内表现入口。COF/动画资源适配归同一 owner，保持其等待资源和释放语义。
- M8-C 的 host/widget 兼容层：context 仍持有 screen，部分控件仍读取全局资源；在表现会话可独立装配和释放、控件显式接收服务后删除兼容全局读取。
- 地图运行时适配继续由既有 M1-B owner 负责；`RenderSystem` 的两个 automap 布局引用只是原有边更换包名，架构测试仅对这两个具体类型保留例外，不扩大到整个 presentation 包。
- M8-D 负责已登记的多代协议/transport/session 实现；本批没有修改网络生产入口。

下一迁移：M1-A/M8-C 客户端实体、技能/死亡/状态表现的事件/快照出口，集中处理上述适配器，不再按单个表现类拆批；M8-D 网络三层收敛另行处理。
