# 分层改造剩余工作清单

更新时间：2026-10-17

## 当前结论

项目已经完成多批“上下文化”改造，服务器行为测试、架构回归测试和子系统回归测试可以独立运行；但整体仍未达到 dark-magic 式的完整独立分层。`core` 仍是一个大型 Gradle 编译模块，遗留全局入口也没有清零。

下面的引用数量是使用 `rg` 对源码文本扫描得到的基线，属于追踪指标，不等同于运行时依赖数量。每批改造后必须重新扫描并更新本文件。计数口径为 `rg -n` 的匹配行数（同一行多次引用只计一次），包含兼容入口。

## 固定执行批次（总计最多 3 批）

1. **第 1 批 / P0**：本文列出的核心系统和它们所需的数值、技能、导弹、战斗依赖传递；本批验收后关闭，不追加功能。
2. **第 2 批 / P1**：本文列出的任务、属性、物品、服务端对表现类型的反向依赖和 D2MOO / 地图兼容边界；不提前拆 Gradle 模块。
3. **第 3 批 / P2**：依赖图、循环依赖治理、物理 Gradle 子模块拆分和硬门禁。最终据实际独立编译及零遗留证据验收；无法达到的条件必须明确列出，不能降低标准或增加第 4 批。

只修复各批验收发现的范围内问题；清单外问题仅记录，不扩展实施。每批统一验证、更新记录、提交及推送。

## 已完成的局部改造

- `ServerGameplayContext` 已作为服务器数据、种子、动画、MPQ 和角色存储的显式绑定入口。
- AI 基类、多个怪物 AI 和 `NativeMonsterSequence` 已改为读取服务器上下文。
- `StateUpdater` 的技能、状态、导弹、难度表和随机种子读取已上下文化。
- Quest 请求路由已通过共享接口隔离客户端同步器。
- D2MOO Act II–V 地图生成已使用显式 `D2MOOLayoutContext`；`DataTbls` 已改为线程上下文缓存。
- 掉落系统 `LootManager` 已接入 `ServerGameplayContext`，TreasureClass 和兼容掉落路径使用单位级 `NativeRng`。
- 架构回归和子系统回归已建立，当前主要提供反向依赖和依赖增长门禁。

## 未完成工作（按优先级）

### P0：服务器核心路径继续上下文化（第 1 批）

- [x] `MissileCollisionSystem`：世界 Files 继续传入 MissileDamageResolver；导弹伤害、子导弹及命中特效解析使用显式数据；导弹级随机状态保留。当前类无直接 MPQ 依赖，无需新增资源解析入口。
- [x] `AssassinTrapSystem`：表访问绑定世界 Files；攻击判定、尸爆随机使用怪物级 NativeRng；技能/导弹伤害解析继续传入 Files。
- [x] `StateSerializer`：支持显式 Files，SerializationManager 优先传入世界 Files；显式绑定在全局 Files 为空时仍能恢复技能状态。
- [x] `NativeSkillResolver`、`MonsterStatsCalculator`：增加显式 Files 重载；服务器技能校验、工厂数值计算、导弹和怪物攻击解析调用显式入口。
- [x] `MercenarySkillSystem`、`ServerSkillSystem`：雇佣兵表访问绑定世界；核心技能校验、怪物数值、导弹初始化传入 Files；直接技能伤害随机状态按实体保存。
- [x] 战斗、状态、伤害实例归属：核查相关包无 `INSTANCE` 单例；核心系统按实例持有 CombatSystem，绑定世界时使用独立 NativeRng，并传递至被动防御计算。StatusEffectApplier 按 StateUpdater 实例持有。

以上完成项表示本批约定的局部上下文化，**不表示服务器全目录全局引用清零**。旧 API / 无上下文世界仍保留兼容入口；未改动的职业技能辅助函数等遗留调用不纳入本批扩改，也不能据此宣布完整独立分层。

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

- `Riiablo.files`：125 行
- `Riiablo.mpqs`：5 行
- `Riiablo.gameSeed`：14 行
- `MathUtils.random`：402 行
- `INSTANCE`：4 行

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

## 本聊天首批工作记录（2026-10-17）

- 起点：`master` / `35b9e8ea0`，工作区干净；HEAD 扫描与原清单的服务器 149/5/13/417/4 行及重点目录 6/9/18/81 行一致。
- 局部上下文化：见 P0 完成项；新增 SkillRandom 组件保存实体级技能随机状态，由 Artemis 随实体释放重置。
- 兼容全局入口：无参和旧签名重载保留全局回退；显式 Files / RNG 入口不经这些回退。新增兼容重载会影响文本计数，因此匹配行数不能单独衡量运行路径隔离。
- 物理 Gradle 模块拆分：本批未实施，`core` 仍是大型编译模块；未达到 dark-magic 式完整分层。
- 验证：最终串行运行 `:core:compileJava`、`:core:architectureRegression`、`:core:subsystemRegression` 和核心专项测试均通过。架构测试 2 项；子系统测试 765 项（2 项跳过）；核心专项 77 项。隔离测试覆盖两套数据、全局 Files 为空、实体随机状态和禁止读取客户端随机源。
- 第 1 批状态：验收完成；第 2、3 批尚未启动。
- 验证修复：Assassin 护甲耐久测试给目标添加 Player 后未声明 PvP 敌对，补齐测试夹具；生产友军保护规则保持原样。此前重叠 Gradle 运行的失败不作为验收结果。

## 最近提交

- `0d6cfa232 Contextualize server loot resolution`
- `1a0d07d07 Bind state updates to server gameplay context`
