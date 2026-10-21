# M1-A/M8-C 音频与图形依赖收口（2026-10-22）

本批完成剩余 40 条音频和 10 条图形扫描项的迁移。音频会话、声音事件/环境/脚步/怪物/格挡/物件系统、伤害表现观察器、天气、overlay/item/missile 资源加载、地面物品光效、automap 与路径调试绘制移入 `core/src/main/java/com/riiablo/presentation`；仅由表现系统持有播放句柄的 `SoundEmitter` 移入 `presentation/component`。客户端实体工厂保留实体创建职责，导弹飞行声通过 `MissileTravelAudio` 委托，空间增益按所属 Artemis world 的 listener、位置和 zone 计算。

提交：`13ec540f3`（实现）

验证：

```text
.\gradlew.bat :core:clientUiBoundaryRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest --tests com.riiablo.server.d2gs.MonsterAiArenaTest --tests com.riiablo.server.d2gs.SoundEventTransportTest :desktop:compileJava :server:netty:compileJava :tools:map-viewer:compileJava :tools:skill-viewer:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

结果：架构回归 10 项通过；客户端表现/音频/图形回归 332 项通过、1 项因未配置可选 automap 存档样本跳过；D2GS 定向回归 20 项通过；core、desktop、D2GS、Netty、Map Viewer、Skill Viewer 编译通过。`check-client-presentation-boundary.ps1 -FailOnDrift` 报告 0 条，M1-B 生成层仍为 0、运行时适配器 58 条且无漂移。`SoundP.java` 仅保留尾部空行改动，不在提交中。

真实资源：是；使用 `G:\BaiduNetdiskDownload\Diablo II 1.10F`。RiiabloTest 相关用例加载真实 MPQ 数据表/字符串；没有启动真实音频设备或完整 GL 客户端。

D2MOO 依据：沿用现有 1.10f Sound/MonSounds/Overlay/Missiles/Automap 表项和声音键；不改变服务端伤害、AI、任务、数值或协议。

剩余问题：表现扫描项已清零，但 M8-C 的最终出口仍要求表现仅订阅事件/快照；`ClientGameplayContext` 与现有客户端适配器仍保留兼容 host 端口，因此 M1-A/M8-C 继续进行中。M1 整体未完成。

下一迁移：M3-B/M4-A；M8-C 后续继续收敛事件/快照出口，M8-D 网络三层收敛另行处理。
