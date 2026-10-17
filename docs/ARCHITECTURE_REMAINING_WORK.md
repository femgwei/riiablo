# 分层改造剩余工作清单

更新时间：2026-10-17

## 当前结论

项目已经完成多批“上下文化”改造，服务器行为测试、架构回归测试和子系统回归测试可以独立运行；但整体仍未达到 dark-magic 式的完整独立分层。`core` 仍是一个大型 Gradle 编译模块，遗留全局入口也没有清零。

下面的引用数量是使用 `rg` 对源码文本扫描得到的基线，属于追踪指标，不等同于运行时依赖数量。每批改造后必须重新扫描并更新本文件。

## 已完成的局部改造

- `ServerGameplayContext` 已作为服务器数据、种子、动画、MPQ 和角色存储的显式绑定入口。
- AI 基类、多个怪物 AI 和 `NativeMonsterSequence` 已改为读取服务器上下文。
- `StateUpdater` 的技能、状态、导弹、难度表和随机种子读取已上下文化。
- Quest 请求路由已通过共享接口隔离客户端同步器。
- D2MOO Act II–V 地图生成已使用显式 `D2MOOLayoutContext`；`DataTbls` 已改为线程上下文缓存。
- 掉落系统 `LootManager` 已接入 `ServerGameplayContext`，TreasureClass 和兼容掉落路径使用单位级 `NativeRng`。
- 架构回归和子系统回归已建立，当前主要提供反向依赖和依赖增长门禁。

## 未完成工作（按优先级）

### P0：服务器核心路径继续上下文化

- [ ] `MissileCollisionSystem`：注入 Files、MPQ、随机源和相关解析器，移除约 60 处旧式全局访问。
- [ ] `AssassinTrapSystem`：注入表访问和随机源，覆盖约 14 处全局表/随机访问。
- [ ] `StateSerializer`：注入序列化所需数据源，覆盖约 13 处全局访问。
- [ ] `NativeSkillResolver`、`MonsterStatsCalculator`、`MercenarySkillSystem`、`ServerSkillSystem`：继续清理全局表、种子和单例入口。
- [ ] 战斗、状态、伤害系统：逐步移除 `INSTANCE` 单例，改为显式依赖或按世界创建。

### P1：服务端与数据/表现层边界

- [ ] 任务系统剩余全局访问，重点是 `Act5QuestMessageValidator` 和 `ContextualQuestSystem`。
- [ ] 属性层 `Stat` 对 `Riiablo.files.ItemStatCost` 的访问改为显式表上下文。
- [ ] `item.Type` 的静态初始化改为显式数据初始化，避免类加载阶段读取全局表。
- [ ] `item` 目录兼容入口继续清理；当前扫描仍有约 81 处相关全局/随机/单例引用。
- [ ] 服务端反向依赖客户端/UI 类型，例如 NPC、CofManager；改为共享数据接口或服务器专用类型。

### P1：D2MOO 和地图兼容入口

- [ ] `Act5D2MOOLayoutBridge` 的 Files/MPQ/`DataTbls` 兼容入口继续收口。
- [ ] `NativeShrineDistributionAudit` 等最后的地图全局资源入口改为上下文参数。
- [ ] `map/d2moo` 当前仍有约 9 处相关遗留引用。

### P2：物理模块边界

- [ ] 设计依赖方向：shared/data → server domain → server systems → presentation/adapters。
- [ ] 将可独立编译的共享接口、数据访问、服务器域逻辑和表现适配器拆成 Gradle 子模块。
- [ ] 每次拆分前先绘制依赖图并处理循环依赖，避免直接移动包导致反向依赖。
- [ ] 将架构测试升级为“禁止新增”加“目标模块不得存在遗留反向依赖”的硬门禁。

## 当前基线扫描

在 `core/src/main/java/com/riiablo/engine/server` 下：

- `Riiablo.files`：约 149 处
- `Riiablo.mpqs`：约 5 处
- `Riiablo.gameSeed`：约 13 处
- `MathUtils.random`：约 417 处
- `INSTANCE`：约 4 处

其他重点目录：

- `engine/server/quest`：约 6 处
- `map/d2moo`：约 9 处
- `attributes`：约 18 处
- `item`：约 81 处

## 每批改造的验收条件

1. 业务代码不再新增全局 Files/MPQ/种子/随机访问。
2. 新增依赖通过构造器、上下文或服务器接口传入。
3. 至少运行：`:core:compileJava`、`:core:architectureRegression`、`:core:subsystemRegression`。
4. 更新本文件的扫描基线和已完成项。
5. 只有在所有目标模块达到零遗留并形成独立 Gradle 编译边界后，才能宣布达到 dark-magic 式完整分层。

## 最近提交

- `0d6cfa232 Contextualize server loot resolution`
- `1a0d07d07 Bind state updates to server gameplay context`
