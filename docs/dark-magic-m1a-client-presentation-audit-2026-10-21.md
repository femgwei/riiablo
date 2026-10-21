# M1-A 第一批：engine/client 反向依赖清单（2026-10-21）

本批只登记 `core/src/main/java/com/riiablo/engine/client` 对表现层的直接依赖，不改变代码行为。
扫描依据为 Java import 与限定类型引用；`engine/client` 当前仍是客户端表现适配与部分 ECS 系统的混合目录。

## 依赖分类与 owner

| 类别 | 文件（按当前直接引用） | 当前 owner | 迁移目标 |
|---|---|---|---|
| UI/widget | `Act1QuestDialogController`、`Act1QuestIndicatorSystem`、`ClientNetworkReceiver`、`ClientNpc`、`DialogManager`、`LabelManager`、`MenuManager`、`MonsterLabelManager`、`QuestDialogController`、`ZoneEntryDisplayer` | `core` presentation adapter | M8-C；由语义事件/快照订阅层创建 widget |
| screen/panel | `ClientGameplayContext`、`ClientNpc` | `core` presentation adapter | M8-C；上下文只持有 presentation port，不直接持有 screen/panel |
| audio | `BlockSoundEmitter`、`ClientAudioSession`、`ClientEntityFactory`、`ClientGameplayContext`、`ClientNpc`、`DamageHandler`、`FootstepEmitter`、`MissileImpactPresentationSystem`、`MonsterSoundEmitter`、`ObjectAmbientSoundSystem`、`ObjectBreakSoundEmitter`、`SoundEnvironmentSystem`、`SoundEventSystem`、`WeatherRenderSystem` | `core` client presentation | M8-B 已完成语义事件主链；M8-C 收口设备/资源依赖 |
| graphics/render | `Act1QuestIndicatorSystem`、`AutomapManager`、`AutomapTileRenderer`、`HackMapIconRenderer`、`GroundItemGleamSystem`、`ItemLoader`、`LabelManager`、`MissileLoader`、`MonsterLabelManager`、`OverlayManager`、`PathDebugger`、`PathfindDebugger`、`WeatherRenderSystem`、`ZoneEntryDisplayer` | `core` client presentation | M8-C；渲染系统订阅快照并保留 LibGDX 适配 |

同一文件可能跨多个类别；本表按“直接依赖”登记，不把它们错误归类为服务端规则。

## 反向依赖边界

1. `ClientGameplayContext` 同时持有 `Audio`、`PaletteIndexedBatch` 和 `GameScreen`，是最早的收口点；删除条件是引入 presentation port 后，engine 只发布快照/语义事件。
2. `ClientNpc`、`DialogManager`、`QuestDialogController` 和 `Act1QuestDialogController` 直接创建 `NpcDialogBox`/`NpcMenu`/`VendorPanel`；删除条件是 NPC/任务事件由 UI adapter 消费。
3. `ClientEntityFactory`、`DamageHandler`、`MissileImpactPresentationSystem`、`MonsterSoundEmitter`、`ObjectBreakSoundEmitter` 仍直接调用 `MonsterAudio` 或 `Audio.Instance`；M8-B 已将服务端声音改为 `SoundEvent`，剩余删除条件是所有客户端声音统一经 `SoundEventSystem`/audio port。
4. `LabelManager`、`MonsterLabelManager`、`ZoneEntryDisplayer` 和 `Act1QuestIndicatorSystem` 直接创建 widget/graphics 对象；删除条件是表现对象生命周期不再由 engine 规则系统直接管理。
5. `Automap*`、`*Loader`、`WeatherRenderSystem` 和 debug renderer 属于纯表现适配，但仍位于 engine/client；迁移时保留文件 owner，先禁止新规则代码依赖这些类，再逐步移动目录。

## 迁移约束

- 本批不移动文件、不改 import、不删除兼容调用。
- 新增仿真/服务端代码不得依赖上述 UI、音频设备或渲染对象。
- 现有客户端调用保留到 M8-C；M8-B 的声音语义事件链路继续作为唯一服务端入口。
- `core/gen/.../SoundP.java` 的未提交生成改动不属于本批，必须保持原样。

## 验证与出口

- 已完成静态扫描并逐文件登记 owner、迁移行和删除条件。
- M0 的 `verifyModuleBoundaries` 与 `core:architectureRegression` 基线已通过；本批不重复运行生产测试。
- M1-A 第一批完成度：约 50%（清单完成，尚未建立禁止新增反向依赖的硬门禁）。

下一批：M1-A 第二批，增加针对 `engine/client` 新增反向 import 的架构检查或可复算报告；M1-C 网络实现盘点可并行，边界限定为 `server/`。
