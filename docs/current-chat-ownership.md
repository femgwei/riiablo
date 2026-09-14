# 当前 Chat 维护状态

更新时间：2026-09-15

## 唯一负责人

当前由本 Chat 维护 riiablo/D2MOO 对齐的全部模块。已关闭的独立战斗 Chat 不再作为
代码、进度或责任来源；本地和远程访问都以仓库中的本文件和
`docs/d2moo-progress-roadmap.md` 为准。原始 P0–P3 优先级清单保存在
[`d2moo-minimal-port-priority.md`](d2moo-minimal-port-priority.md)。

包含的范围：

- 地图、DRLG、Warp、对象、碰撞和渲染验证
- 任务、NPC、交易、Party、多人网络和重连恢复
- 战斗、伤害、状态、技能、导弹、怪物 AI、经验和掉落
- 装备、背包、角色存档、TXT 数据层、离屏/双客户端测试

## 每次更新必须同步

完成一个功能模块后，必须在 `d2moo-progress-roadmap.md` 同时记录：

1. 本次完成的模块和实际行为；
2. 测试命令及结果；
3. 当前模块完成度或剩余项；
4. 明确的下一步目标；
5. 提交 hash、远程推送结果和工作区状态。

若代码历史与聊天记忆冲突，以 `git log`、当前 `HEAD` 和本文件为准。未通过测试的
模块不得标记为完成；发现其他访问方式产生的旧状态时，先做 Git 差异核对，不回退
或覆盖已有修改。

## 当前基线

- 分支：`master`
- 当前功能提交：本文件所在 `HEAD`（本轮 Automap 选项、城镇揭示、比例与实体标记修改待提交）
- 上一功能基线：`89c2e54a`（德鲁伊 Armageddon/Hurricane 原生状态链）
- 远程：完成本次提交后推送 `origin/master`，最终结果以交付报告中的 hash 为准
- 工作区：本次提交完成后应为干净
- 总体对齐进度：约 70%（详见路线图）
- 第一章最小可玩闭环：约 79%

## 最近更新（2026-09-15，Automap/UI）

- Options 子菜单 OptionRow 已统一为父菜单的 `font16` 与 24px 行高。
- 城镇 Automap 首次进入即揭示完整 Zone；野外/地下仍按 RoomEx 探索揭示。
- 小地图通过半尺寸视口实现原生内容 1/2 比例，相机 zoom 不再额外放大；玩家、队友和
  无 DC6 cell 的 NPC 使用统一投影的几何回退，有原生 cell 时避免重复绘制。
- 真实 1.10f `:desktop:offscreenAutomapDc6` 与 Automap 专项测试通过；全量核心测试中
  仅既有缺失 `test/*.d2s/.d2i` 资源项失败。

本轮修改仅限 Automap/UI 文件和测试，未改战斗、地图生成或网络协议；提交后会更新此处
的 commit 与远程推送结果。

## 最近更新（2026-09-15，城镇技能限制）

- 客户端技能快捷键现在读取原生 `Skills.txt:InTown`，在营地对攻击、投掷和禁止
  城镇施放的法术显示红色禁用图标；离开城镇后按区域状态自动恢复。
- 服务端施法入口同步执行 `InTown` 校验，避免网络请求绕过限制。
- 1.10f 原生技能标志集成测试及无资源基础动作回退测试已通过。

## 最近更新（2026-09-15，1.10f 启动与音频容错）

- 正确日志 `game.log.###` 显示：1.10f MPQ 能正常加载，启动退出由缺失的可选
  `Act4/diablo.wav` 触发。`MusicController` 现在跳过失败曲目并继续队列，不再让
  单个版本资源差异终止 Splash。
- `Audio.Instance` 现在具备停止标记和空委托保护；异步旅行音效在标枪等实体提前
  移除时不会再触发 `Sound.stop` 空指针，也不会在停止后被延迟队列重新播放。
- 真实 1.10f `:desktop:offscreenCamp`、核心标枪回归测试和桌面编译均通过。
- 本轮未修改共享地图/战斗接口、网络 schema 或生成网络文件；工作区中的历史
  未跟踪 `.log` 产物保持不变。

## 最近更新（2026-09-15，城镇技能点击拦截）

- `CursorMovementSystem` 和 `MobileControls` 现在在普通点击、输入队列、按住/释放及联机发送前复用
  原生 `Skills.txt:InTown` 规则。红色 `Throw` 等技能在营地点击不会启动
  `Actioneer` 攻击动画、不会发送施法请求，也不会自动走向怪物；NPC/传送点交互
  路径保持优先。
- 服务端 `ServerSkillSystem` 的权威校验未移除；本轮只是客户端副作用防护。
- 验证：`:core:test --tests com.riiablo.engine.server.skill.NativeSkillResolverTest`
  和 `:core:compileJava` 通过。下一步为 1.10f 营地离屏点击回归及野外攻击回归。

## 最近更新（2026-09-14，A4Q2 重连）

- 当前 Chat 继续统一负责地图、战斗、技能、物品、任务、NPC、网络和存档。
- A4Q2 Diablo → Tyrael → Harrogath 传送门已加入断线重连回归：断线客户端恢复
  `REWARD_GRANTED`、共享传送门和目标 Level；跨 Act Warp 由 D2GS 原子切换目标地图并
  发送新的权威快照。
- 验证命令：`./gradlew.bat :server:d2gs:headlessA4SealDual --no-daemon`，真实 1.10f
  MPQ 通过；结果记录在 `d2moo-progress-roadmap.md`。
- 本轮唯一共享逻辑修改为 `server/d2gs/.../D2GS.java` 的跨 Act Warp 分支；
  `WarpInteractor` 同 Act 路径和战斗系统注册保持不变。

## 最近更新（2026-09-14，本地标枪）

- 修复本地 `ServerSkillSystem(true)` 提前过滤玩家 Throw 的问题；标枪现在由服务端
  在 `MIS` 关键帧创建权威 Missile，客户端不再只播放空动画。
- 已运行 `CombatPipelineIntegrationTest`；本轮未修改网络 schema 或生成网络文件。

## 最近更新（2026-09-11）

- Automap 原生 DC6 实体 SpriteBatch 阶段已加入 ShapeRenderer 投影矩阵保存/恢复、异常
  finally 清理和 batch 嵌套保护；`AutomapRenderStateTest` 定向测试通过。
- 当前 Chat 继续统一负责地图、战斗、技能、物品、任务、NPC、网络和存档；真实 1.10f
  画面验收仍因环境条件暂缓。
- 下一步：隔离 `RenderSystem` 旧墙体固定帧回退，并验证实体探索过滤与原生/回退绘制不重复。

## 最近更新（2026-09-11，续）

- `RenderSystem` 的旧墙体固定帧精灵路径已通过 `LEGACY_AUTOMAP_SPRITE_FALLBACK=false`
  隔离，运行时不会与 AutoMap.txt/DC6 原生 cell 重复绘制；Automap 定向测试与核心编译通过。
- 下一步：补充对象/怪物 RoomEx 探索过滤回归，验证原生图标和几何回退不会重复出现。

## 最近更新（2026-09-11，再续）

- 新增 `AutomapVisibility` 并接入实体收集：RoomEx 未探索区域的对象、怪物和 NPC 不再
  泄漏到 Automap；无原生拓扑时保持旧兼容行为。
- `AutomapManager` 同一实体 ID 刷新采用更新而非追加，避免原生 DC6 与几何标记重复累积；
  Automap 全套无资源测试和核心编译通过。
- 下一步：验证地形/实体坐标投影、负坐标、跨 Zone 边界和 RoomEx 邻接投影。

## 最近更新（2026-09-11，四续）

- Automap DT1 tile 边界统一采用 floor division，修复负坐标 Zone 漏掉首个 tile 的问题；
  新增负坐标和跨边界投影测试，Automap 全套测试及核心编译通过。
- 下一步：补充 RoomEx 邻接房间探索/投影集成回归，检查跨 Zone 切换和实体图标可见范围。

## 最近更新（2026-09-11，五续）

- 新增 RoomEx 邻接探索集成回归：当前房间和 `CLIENT_IN_SIGHT` 邻接房间可见，两跳之外
  房间隐藏；`changeClientRoom` 后可见环和边界坐标保持稳定。
- Automap 全套无资源测试及核心编译通过；真实资源画面验收仍待 MPQ 环境。
- 下一步：补充 cell 去重、边界裁剪和激活快照断言，为第一章真实 Automap 验收做准备。

## 最近更新（2026-09-11，六续）

- AutomapLayer 的 terrain/object/extra cell 现在按 `cellNo + 坐标` 去重；对象和特殊图标
  也遵循 RoomEx 探索掩码，未探索区域不会直接绘制。
- Automap 全套无资源测试、RoomEx 集成测试和核心编译通过。
- 下一步：增加 RoomEx 激活快照一致性断言，并准备第一章真实 MPQ 画面验收入口。

## 最近更新（2026-09-11，七续）

- 新增 RoomEx 激活快照一致性断言：跨房间移动只更新探索/可见状态，不修改已生成的
  floor/wall/object cell 数量和坐标。
- Automap 全套无资源测试与核心编译通过；真实验收入口为
  `:desktop:offscreenCamp -Pd2Home=<1.10f目录>`，当前环境仍未执行真实画面测试。
- 下一步：在 MPQ 就绪后运行隐藏营地并扩展鲜血荒地/洞穴入口离屏场景。

## 最近更新（2026-09-11，八续）

- 已使用完整 1.10f MPQ 通过真实 1×1 隐藏营地烟测：Act 1 营地加载、3 帧渲染和退出均
  正常，输出 `desktop/build/visual-tests/camp-latest/rogue-encampment-manifest.txt`。
- 下一步：扩展离屏入口支持鲜血荒地及洞穴/地下通道目标 Level，验证区域切换和 Automap
  连续性。

## 最近更新（2026-09-11，九续）

- `OffscreenRenderClient`/`OffscreenCampScreen` 新增目标 Level 参数，真实 1.10f MPQ 下
  Level 2（Blood Moor）、Level 8（Den of Evil）、Level 10（Underground Passage）三项
  隐藏切换测试均通过，目标 Zone 生成、玩家绑定和 6 帧渲染正常。
- 下一步：在离屏目标场景中增加 Automap cell 数量、RoomEx 拓扑及入口对象存在性断言。

## 最近更新（2026-09-11，十一续）

- 已增加 Warp 目标校验并通过真实 1.10f：Den of Evil → Blood Moor（2）；Underground
  Passage 的目标为 5、4、14，所有目标 Zone 均可解析。
- 下一步：继续校验入口/出口 Warp 双向配对、反向坐标和 RoomEx 可通行区域。

## 最近更新（2026-09-11，十续）

- 离屏入口现已断言目标 Zone 的 Automap cell、RoomEx 和原生入口对象；真实 1.10f 下
  Blood Moor、Den of Evil、Underground Passage 三项均通过，分别生成 1082/544/1251
  个 Automap cells。
- 下一步：继续校验洞穴/地下通道入口对象的具体 Warp 目标 Level ID 与 D2MOO 拓扑一致。

## 本次更新

- AutoMap 查询层已按 D2MOO `DATATBLS_GetAutomapCellId` 对齐：新增 LevelName/TileName
  严格匹配、Style/Sequence 通配、完整缓存键和 seed 稳定单帧选择；新增无资源单元测试。
  当前仍保留现有 AutomapLayer 探索半径逻辑，下一步接入 DRLG 房间揭示和离屏连续性回归。
- 本轮提交：`e51c3037`、`ef6c1398`；`:core:test --tests
  com.riiablo.engine.client.automap.AutomapTileRendererTest` 与 `:core:compileJava`
  均通过。`origin/master` 推送仍被 GitHub OAuth 缺少 `workflow` scope 拒绝，工作区仅保留
  未跟踪运行日志，未纳入提交。
- 本轮继续完成 RoomEx 探索揭示：`AutomapRenderer` 玩家位置更新优先使用 Zone 原生房间
  激活状态，`AutomapLayer` 按当前/邻接 CLIENT_IN_SIGHT 房间矩形标记探索；无原生拓扑时
  自动回退旧半径逻辑。新增 `AutomapLayerTest`，待下一步接入真实 DS1 单元绘制。
- 本轮新增 `AutomapManager.rebuildNativeCells`，把 Zone 的 DT1 单元转换为 AutoMap 查询
  后的 DC6 cells；尚未自动接入首次地图生成，需先完成 LevelId→Automap LevelName 映射。
- 已接入首次玩家位置更新和 `renderWithSprites`：按 Zone 的 `LvlTypes.Name` 构建并绘制已探索
  floor/wall/object DC6 cells，未探索单元过滤；几何线条仅作为兼容回退。待真实 1.10f
  资源校验 LevelName 映射。
- 新增 `AutomapLevelNames` 并接入 Zone，将 Act/LvlTypes 规范化为 D2MOO 35 类 LevelName；
  第一幕全部类型和 Act 2–5 兼容分支已有无资源测试。下一步执行真实 1.10f 离屏截图回归。
- 按当前资源限制跳过真实截图，LevelName 解析改为纯 Java 自动接入首次玩家位置更新。
  下一项转为 `Objects.AutoMap` / `MonStats2.automapCel` 原生对象与怪物图标接口，保留几何
  标记作为无 DC6 帧时的回退。
- 已新增 `AutomapEntityCells` 解析对象/怪物原生 DC6 帧，并通过无资源测试；下一步将其
  接入实体 Automap 绘制与位置投影，暂不影响战斗或实体同步逻辑。
- `EntityMarker` 已支持 `nativeCell`，并新增独立 `renderNativeEntitySprites` 批次接口；
  需要在 ShapeRenderer 结束后调用，本轮未改动 RenderSystem 生命周期。下一步接入客户端
  Monster/Object/NPC 收集和 RoomEx 可见性过滤。
- 已完成客户端 ECS 实体收集、RoomEx 可见性过滤及独立 DC6 批次绘制接线；Monster/Object
  分别读取 `MonStats2.automapCel`/`Objects.AutoMap`，无帧时回退几何标记。未修改战斗与
  网络协议，真实画面验证按当前条件跳过。
- 对象图标解析已补充 D2MOO Waypoint/Shrine/Well/Stash 固定帧回退，并通过测试；下一步
  统一 Automap 实体坐标投影/缩放。
- 新增 `AutomapProjection`，地形 cell 使用 DT1 footprint 中心坐标，完成正负坐标测试；
  真实缩放比例仍待资源运行条件恢复后确认。
- 清理 `RenderSystem` 未使用的 Waypoint/Player 固定帧常量，旧墙体帧仅保留兼容回退路径；
  下一步验证 Automap SpriteBatch/ShapeRenderer 状态切换。

- 本轮重新核对第一章怪物生成和 AI：运行时已接通难度怪物池、密度、Rarity、普通群组、
  Party minion 与 RoomEx 延迟激活，但 Champion/Unique pack 和六类第一章专用 AI 仍未完整
  接线，不能宣称怪物 AI 已全部与 D2MOO 对齐。
- 已修复普通/Champion Fallen Shaman 错误复活 Fallen Shaman；只有 Unique/Super Unique
  Shaman 可以选择普通 Shaman 尸体。普通/Champion Shaman 的 pack owner 约束已在本轮接通，
  下一步转入第一章特殊怪物 Generic fallback（优先 BloodRaven）。
- 已补齐第一轮 Fallen/Shaman minion owner 关系：D2MOO 延迟 RoomEx 生成记录 pack key，
  激活时把 Party minion 绑定到同包 leader；普通/Champion Shaman 现在拒绝带有其他
  owner 的 Fallen 尸体。旧无 owner 地图数据保留兼容 fallback，待完整旧地图重导出后
  再收紧为强制 owner。
- 已修复地面药水自动拾取：服务端按 D2MOO 家族/列顺序优先填充同类药水的上层腰带格，
  再找第一层空位，满腰带回退背包；多人 revision、地面归属和失败回滚不变。定向测试、
  D2GS 编译和真实 1.10f `headlessFallenDual` 均通过。
- 对照 D2MOO 修正 A1Q5 队伍资格：整个 Act I 均合格，Rogue Encampment 不再被错误
  排除；Act II 队员与无关玩家只获得本局完成标记。
- 补齐同层、营地/野外队员、跨幕、无关玩家、已领奖记录和重复死亡的核心测试。
- 新增 `headlessCountessQuestDual` 三客户端真实无窗口门槛，验证奖励隔离、无
  `REWARD_PENDING`、quest revision 及旧存档断线重连恢复；1.10f 资源测试通过。
- 修正 A1Q6 Andariel 传播为整个 Act I，补齐 Act II/无关玩家的 `COMPLETED_NOW`，并
  通过 Warriv 领取、重复请求和两阶段断线重连真实测试 `headlessAndarielQuestDual`。
- 对象 `mode/stateFlags` 现在完整写回客户端对象，并权威添加/移除 `Interactable` 与
  `Selectable`；神殿冷却恢复会重建原生范围和对象交互器，旧协议全零快照保持兼容。
- 对象/神殿定向测试、D2GS 编译和 1.10f 双客户端 `headlessReconnectVisibility`
  已通过；对象模块按 95% 记录，剩余复杂神殿效果及实机观感验收。
- 当前 Chat 继续统一维护全部模块，包括战斗，不存在需要避让的独立战斗 Chat。
- 法师 Static Field 已接入 `SrvDo020`：服务端读取 1.10f 半径、当前生命百分比、AuraFilter
  与难度生命下限，统一处理电抗、电免、吸收、PvP、阵营、Zone 和 LOS；不生成伤害导弹。
  Frost Nova 也已接入 `SrvDo022` 固定 64 方向、原生速度/范围、共享命中、冰伤/时长和
  Cold Mastery/冰免/无法冰冻链。Blaze/Fire Wall 已补齐 `SrvDo023/024` 状态移动轨迹、
  双 maker/中心段/子段及 8.8 定点周期火伤。
- Enchant 已接入 `SrvDo025` 友方目标/自身回退、附火、命中和持续时间；Fire Mastery 使用
  永久状态 stat-list，并覆盖普通技能导弹及持续火场快照。Frozen/Shiver/Chilling Armor
  互斥且分别响应受伤近战、包含 miss/block 的攻击近战和 `ReturnFire` 导弹事件；客户端
  可从 StateP 恢复三种护甲状态和持久 Overlay。Teleport `SrvDo027` 已完成原生安全落点与
  多人同步，下一项转入复杂技能的多人表现验收。
- 圣骑士 Cleansing、Meditation、Redemption、Sacrifice、Smite、Zeal 已完成首轮原生链；
  Charge 已接入 `SrvSt31/SrvDo067`（路径追击、Param1 速度、当前 tick 命中和 calc1 增伤）；
  Vengeance 已接入 `SrvSt35/SrvDo002` 的火/冰/电同时附伤、HitClass 轮换与权威结算；Holy
  Shield 已接入 `SrvSt36/SrvDo018` 的盾牌门槛、状态生命周期、block/defense stat-list 和
  多人快照重建；Conversion 已接入 `SrvSt32/SrvDo079` 的概率、阵营/AI、等级生命保存恢复、
  死亡/离线/跨区清理及耐久收尾。圣骑士服务端技能首轮完成，下一项转入 Sorceress 单体
  弹道/元素状态链专项核对，随后统一做全职业表现验收。
- 十项死灵法师诅咒现统一读取 1.10f `Skills.txt/States.txt`，覆盖原生
  `SrvDo030/059/061`、目标点范围、难度时长、状态 stat、免疫怪物 1/5 抗性削减和
  负物理抗性伤害；玩家与怪物不再走两套实现。
- 定向核心测试、D2GS 编译以及真实 1.10f `offscreenCamp` 均通过。
- 法师 Blizzard 已按 D2MOO `SrvDo028/MISSMODE_SrvDo10` 接入中心控制导弹、固定节拍
  区域随机冰雹、地图阻挡检查和冰冷伤害快照；定向回归、D2GS 编译及隐藏营地通过。
- 法师 Frozen Orb 已按 D2MOO `SrvDo15/SrvHit29/SrvDo16` 接入主体、bolt 与 Nova 三段
  权威导弹链，覆盖 64 点方向环、16 枚命中分裂、Ice Bolt 协同、Cold Mastery、冻结时长、
  冰抗和尾段轨迹调整；新增 `SorceressFrozenOrbIntegrationTest`。
- 法师 Meteor 已按 D2MOO `SrvDo028/SrvHit14` 接入 `meteorcenter` 60 帧延迟、一次性
  范围火焰冲击和 18 个 `meteorfire` 持续火场；火场时长来自 1.10f `Param3/Param4`，
  新增 `SorceressMeteorIntegrationTest`，没有新增网络 schema。
- 本次验证：Meteor/Frozen Orb 及全部法师专项测试、`:server:d2gs:compileJava` 和
  1.10f `:desktop:offscreenCamp` 均通过；完整 `:core:test` 仍有 109 个仓库既有资源
  fixture/旧断言失败（缺少 `core/src/test/resources/test` 文件及已记录的旧职业断言），
  与本次 Frozen Orb 定向用例无关，未据此回退其他模块。
- 当前下一项：复杂技能的统一多人表现验收（优先 Hydra/区域导弹的客户端状态与实机观感）；
  本 Chat 继续统一负责全部地图、战斗、技能、物品、任务和多人模块。
- Hydra `SrvSt14/SrvDo144` 已完成首轮：服务端按原生三点偏移创建三个有所有权、
  固定持续时间和 PetType 上限的 Hydra 实体，客户端只消费权威实体；新增 Hydra 原生
  数据/召唤契约测试。离屏营地本轮因环境未提供 MPQ 未启动，需补资源后复验。
- 德鲁伊 Firestorm `SrvDo117` 已完成首轮：按 `Calc1` 生成多条服务端火焰流，保存
  火焰伤害/精通/穿透/DamageRate 快照并同步到多人客户端；新增 Firestorm 无渲染测试。
- 德鲁伊 Fissure `SrvDo028` 已完成首轮：创建原生控制导弹并从其 SubMissile 数据周期
  生成火焰裂缝子导弹；服务端固定火焰伤害快照并同步生命周期，新增 Fissure 无渲染测试。
- 德鲁伊 Volcano `SrvDo123` 已完成首轮：目标点控制导弹、确定性喷发种子、周期火焰
  子导弹及服务端伤害快照均已接通，新增 Volcano 无渲染测试。
- 德鲁伊 Armageddon/Hurricane `SrvDo124` 已完成首轮：状态持续时间、周期延迟、合法
  目标筛选、区域导弹和多人状态快照已接通，新增无渲染状态测试。

### 2026-09-11 复杂区域技能多人快照语义

- `MissileP` 追加 `skillId/damageLevel`，`StateP` 追加来源实体、来源技能和周期时钟；
  均为尾部字段，保留旧客户端读取既有字段的兼容性。
- 客户端导弹副本统一设为 `authoritative=false`，只呈现服务端位置和生命周期；反序列化
  的 `UnitStates` 统一设为 `snapshotOnly=true`，不会再次递减状态或生成区域导弹。
- Firestorm、Fissure、Volcano、Armageddon、Hurricane 明确复用服务端导弹；Hurricane
  虽只在 `cltMissileA` 声明表现资源，但 `SrvDo124` 已由服务端周期发射，因此也禁止本地
  重建。Hydra 保持原生三只 Monster 实体同步，不错误套入 MissileP。
- 1.10f 的 hurricane/armageddon States 行 Overlay 均为空，表现来自
  `hurricaneswoosh/armageddoncontrol` 导弹，未添加虚假 Overlay 映射。
- 7 个区域技能专项类共 17 个用例、6 个状态序列化相邻类共 33 个用例，以及
  `:server:d2gs:compileJava` 通过；`offscreenCamp` 因
  `build/riiablo-offscreen-d2` 缺少 MPQ 而未启动，属于本机测试资源门槛。
- 下一项：增加真实 D2GS 无窗口双客户端区域技能门槛，分别核对 Hydra 的 MonsterP
  三实体，以及 Hurricane/Volcano 等导弹和状态在两端的实体 ID、技能 ID 与删除时序。
- Iron Maiden/Life Tap 已接入服务端权威 `DamageEvent`：近战/导弹携带结算后的物理
  分量；反伤只响应近战且不会递归，Life Tap 按 `calc1` 恢复攻击者生命。定向测试和
  D2GS 编译通过，真实 1.10f `offscreenCamp` 营地启动门槛通过。
- Dim Vision/Attract/Confuse 已接入统一怪物 AI：失明怪不再释放远程技能，Attract 固定
  周围怪物目标，Confuse 按单位种子选择另一只合法怪物；到期、死亡、状态移除和跨地图
  会恢复普通 AI，怪物导弹也能命中临时敌对目标。定向与第一章怪物联合回归已通过。
- Bone Armor 已接入原生 `SrvDo018`：容量读取 1.10f `AuraStatCalc`，物理伤害在扣血前
  消耗护盾，耗尽移除、重施恢复，并由现有状态快照同步剩余容量。
- Poison Dagger 已接入原生 `SrvSt16/SrvDo032`：施法前验证非投掷匕首，固定 tick 位置
  快照生成一次性命中记录，按 1.10f 表计算 8.8 毒率、持续帧和双技能协同，命中时原子
  结算伤害、毒状态和耐久；没有新增网络 schema。
- Corpse Explosion / Poison Explosion 已接入原生 `SrvSt17/SrvDo055/SrvDo063`：共用
  单消费者尸体预留，尸爆按基础尸体生命拆分物理/火焰内外半径，毒爆创建八枚服务端
  权威漂移毒云并快照 8.8 毒率、协同、精通和穿透；客户端仅渲染同步云与尸体爆裂。
- 召唤物跨房间/跨区域重组已对齐 D2MOO 的 28/50 格行为边界：跨 Zone、远距或非相邻
  RoomEx 无路径时，在主人周围确定性选择可通行落点；成功后清理旧目标、施法、路径、
  动画和速度，重置骷髅 AI 缓存并同步 RoomEx/Box2D/`SYNC_WARPED`。专项、死灵/PvP/
  导弹回归、D2GS 编译与真实 1.10f 离屏营地均通过。
- Revive 与普通怪物复活现已分流：玩家 Revive 保留尸体原 `MonStats/Skill1..8`，切换
  到专用 `NecroPet`，不再误执行原 AI 或自复活技能；Fallen Shaman 仍保留普通复活链。
- 四类 Golem 已命中 `NecroPet` 专用 AI，并对齐主人 regroup、目标范围、80% 攻击概率、
  城镇禁攻和 Warp 后 AI 缓存清理；未修改网络 schema。
- 四类 Golem 的原生战斗副作用已补齐：Clay 被近战攻击时反向减速攻击者；Blood 使用
  目标 Drain 和原生递减曲线回血，并保留 1.10f `Param5=0` 的受击生命同步；Iron 聚合
  被消费物品并执行 Thorns；Fire 从 `SumSkill1/SumSk1Calc` 获得 25-frame Holy Fire。
  跨区域重组后永久光环和 Iron Golem 来源物品继续存在。
- 圣骑士 Might、Prayer、Holy Fire、Concentration、Conviction 已统一接入真实 1.10f
  `Skills.txt` 范围、属性、周期和持续时间；状态记录来源实体/技能/等级，并按目标、
  state、skill 完成高等级覆盖、同等级刷新、低等级拒绝和弱光环恢复。
- 光环目标按玩家 root owner 处理队伍、敌对、佣兵和召唤物关系，并排除跨 Zone、城镇
  房间、`NoAura` 怪物；Holy Fire LOS、定点周期火伤、近战附火及 Prayer 治疗进入统一
  战斗/状态链。108 个联合测试、D2GS 编译和真实 1.10f 离屏营地均通过；没有生成网络
  文件差异。
- Blessed Hammer 已接入服务端 `SrvDo073`：77 点固定方向外扩螺旋按 120 frame 运行，
  每段分别执行地图屏障和单位扫掠；`CollideKill=0` 可命中不同目标，同一目标终身去重。
- 基础魔法伤害、Vigor/Blessed Aim 硬点协同和施法时 Concentration 加成固化到导弹；
  不死/恶魔各 50% 可叠加，并在魔法抗性、吸收和 PvP 缩放前结算。单机/多人表现复用
  同一权威实体。23 个联合测试套件共 107 个用例、D2GS 编译和真实 1.10f 离屏营地通过；
  没有修改网络 schema 或生成文件。
- Fist of the Heavens 已接入 `SrvDo080/SrvHit22`：必须选择存活敌对 Unit；10-frame 延迟
  后对保存的主目标结算闪电，再按 1.10f 范围、数量、过滤器和 LOS 向不死目标分裂 Holy
  Bolt。普通 Holy Bolt 共用 `SrvHit07`，完成合法盟友/佣兵/召唤物治疗、MaxHP 限幅、
  怪物类型门槛及四项硬点协同；单机/多人复用权威实体并显示 `handofgod` Overlay。
- FoH/Holy Bolt 专项及联合回归共 22 个测试套件、121 个用例通过；D2GS 编译和真实
  1.10f 离屏营地均通过，没有修改网络 schema 或生成文件。
- Resist Fire、Resist Cold、Resist Lightning 与 Salvation 已按原生 `SrvDo065` 接入范围、
  周期、过滤和抗性公式；三种单抗的激活最大抗性与硬点永久被动使用独立 stat-list。
  两条权威元素伤害路径已聚合状态最大抗性，并按 D2MOO 修正玩家高抗上限与怪物免疫
  分流。44 个联合用例、D2GS 编译和真实 1.10f 离屏营地通过，无网络生成文件差异。
- Holy Freeze、Holy Shock 与 Sanctuary 已按原生 `SrvDo081/066` 接入：施法者附伤与目标
  状态分层，Holy Freeze 的 `ColdEffect` 门槛、三类减速、目标独立 20% 碎尸 RNG 和无
  可用尸体死亡链，以及 Sanctuary 的非 Boss 亡灵筛选、亡灵命中/伤害和绕过物抗均已
  完成。12 个测试套件 121 个用例、D2GS 编译和真实 1.10f 离屏营地通过；无生成网络
  文件差异。
- Defiance、Blessed Aim、Vigor、Fanaticism 与 Thorns 已按原生 `SrvDo065` 接入；
  Blessed Aim `penetrate` 只按硬点更新，Fanaticism 自身全伤覆盖队伍半伤，Thorns
  聚合永久/状态来源并统一处理普通单位与钢铁石魔的近战反伤。54 个专项用例、D2GS
  编译和真实 1.10f 离屏营地通过；未修改网络 schema 或生成文件。

## 下一步

当前已完成 **P1 死灵法师服务端技能首轮、圣骑士服务端技能首轮**，以及法师基础单体
弹道、Static Field、Frost Nova、Blaze/Fire Wall、Enchant/Fire Mastery 与三种冰甲
原生权威链首轮。Teleport `SrvDo027` 与 Chain Lightning `SrvDo026/SrvHit12` 原生链已完成，
下一步进入全职业复杂区域技能统一表现验收，并继续补齐 Hydra/Firestorm/Fissure/Volcano/
Armageddon/Hurricane 的实机观感验证。
战斗模块不再单独分派，相关修改与进度文档均由本 Chat 负责。

当前阶段说明：项目已进入 **P2 执行阶段**，但 P0/P1 仍保留少量严格验收尾项；P2 与
这些尾项并行推进，不表示底层阶段被跳过或记录丢失。

### 2026-09-11 地图 Warp 双向与可通行验收

- 当前 Chat 继续统一负责地图、战斗、技能、物品、任务、NPC、网络和存档。
- Act 1 Level 8/10 的 Warp 已完成真实 1.10f 离屏双向配对、RoomEx 归属和入口附近
  可通行坐标校验；未修改战斗目录或网络生成文件。
- 下一项为自动遍历 Act 1 全部洞穴/地道/塔楼 Warp 图，并把结果写入进度文档。

### 2026-09-11 Act 1 全量 Warp 图

- 当前 Chat 已加入 `:desktop:offscreenWarpGraph`，真实 1.10f 固定种子共验收 39 个 Zone、
  53 个 Warp；全部具有反向边、RoomEx 记录和入口附近可通行坐标。
- 下一项转入 Act 1 RoomEx/碰撞连续性指标，继续由当前 Chat 负责地图与全部战斗模块；
  不再存在独立战斗 Chat 的避让范围。

### 2026-09-11 Act 1 地图连续性扫描

- 新增 `:desktop:offscreenMapContinuity`，真实 1.10f 扫描 39 个 Zone、1,160,896 个
  采样 subtile；RoomEx 非法邻接、Warp 孤立房间和无可行走 Zone 均为 0，结果 PASS。
- 下一步是地图块边界过渡与碰撞连续性断言，继续由当前 Chat 统一维护全部模块。

### 2026-09-11 RoomEx 边界过渡指标

- 连续性任务已增加相邻 RoomEx 接口采样：真实 1.10f 共 3,090 对边界，其中 1,525 对
  直接找到双方可行走过渡，128 对为矩形接口存在但当前采样不到可行走点，1,437 对
  因几何边界无法直接判断。后两类暂作为诊断数据，不改变生产碰撞。
- 下一步细化 128 个候选接口的门口/BFS 检测，优先核对地下通道和 Act 1 野外连接。

### 2026-09-11 RoomEx 边界局部 BFS 复核

- 已对 128 个直接边界候选执行局部碰撞 BFS：5 个可由局部路径解释，68 个被静态碰撞
  阻断，55 个因几何范围无法可靠判断。由于 `pRoomsNear` 是视野/激活邻接，不等同于
  可直接行走连接，当前只记录诊断，不擅自修改地图碰撞。
- 精确交叉分类后，68 个 BFS 阻断接口为 2 个门、25 个其他对象、41 个普通无对象邻接；
  涉及 Warp 房间的 8 个接口全部有原生对象，`boundaryWarpBare=0`，并已作为严格门槛。
- 下一步验证这 8 个 Warp 房间对象在打开/切换状态后的动态碰撞更新。

### 2026-09-11 当前工作进度

- 已实现真实 D2GS 无窗口双客户端复杂区域技能门槛：`D2GSHeadlessClient` 的
  `--require-area-skill --area-skill <id>` 会生成 caster/observer 存档、发送真实施法包，
  并比对两端 `MissileP`、`StateP`、Hydra `MonsterP` 的权威实体身份和删除时序。
- 当前完成代码级实现、D2GS 编译和专项测试；本机缺少完整 1.10f MPQ，尚未进行真实资源运行。
  资源就绪后优先执行 Volcano、Armageddon、Hurricane、Hydra 四项。

### 2026-09-11 真实 1.10f 双客户端区域技能验收结果

- `D2GSHeadlessClient --require-area-skill --area-skill <id>` 已在真实 1.10f MPQ 下通过
  Volcano(244)、Hydra(62)、Armageddon(249)、Hurricane(250) 四项门槛。
- 两个 TCP 客户端只看到同一组服务端实体：Hydra 使用三个 `MonsterP`，其余区域效果使用
  `MissileP`/`StateP`；技能 ID、伤害等级、来源和删除时序均一致。
- Armageddon/Hurricane 的周期导弹由服务端保留两个模拟帧，修复一帧导弹在快照前被清理的
  问题；客户端仍保持 `snapshotOnly`，不会本地再生成第二套效果。
- 下一项是 Meteor/Thunder Storm 的真实双客户端表现验收；当前 Chat 继续负责地图、战斗、
  技能、物品、任务、NPC、网络和存档全部修改。

### 2026-09-11 Meteor / Thunder Storm 验收结果

- Meteor(56) 与 Thunder Storm(57) 已使用真实 1.10f MPQ 通过双 TCP 客户端门槛；两端
  共享服务端导弹 ID、技能/伤害等级和删除时序，Thunder Storm 的 `StateP` 周期字段也一致。
- Thunder Storm 的一次性导弹改为只结算一次、跨过一个网络快照后再删除，避免命中逻辑
  与展示实体在同一固定帧被清理。
- 下一项：Blizzard/Frozen Orb/Meteor 子导弹的删除与重连门槛，以及测试入口 CI 化。

### 2026-09-11 子导弹双端一致性结果

- Blizzard(59)、Frozen Orb(64)、Meteor(56) 已通过真实 1.10f 双客户端测试；门槛要求
  根导弹和子导弹均在两端出现，并比较两端实体集合的交集/并集、技能 ID、等级与删除标记。
- 任何只在一个客户端生成或删除的 `MissileP` 现在都会被判定为失败。
- 下一项是断线重连后的复杂子导弹快照恢复，随后继续其它多段导弹技能验收。

### 2026-09-11 复杂区域技能断线重连结果

- Meteor(56)、Blizzard(59)、Frozen Orb(64) 已通过断线重连门槛：观察端断开并用同一
  角色重连后，服务端基线只恢复断开前已存在且仍存活的导弹，不恢复未知/已删除实体。
- 重连期间自然过期的短生命周期子导弹按原生寿命处理，不误报为同步错误。
- 下一项是 Hydra、Volcano、Armageddon、Hurricane、Thunder Storm 的同类重连门槛。

### 2026-09-11 Hydra/风暴技能重连验收结果

- Hydra(62)、Volcano(244)、Armageddon(249)、Hurricane(250)、Thunder Storm(57) 已使用
  真实 1.10f MPQ 完成无窗口双客户端断线重连验证。
- Hydra 校验 `MonsterP` 所有权和实体集合；其余技能校验 `MissileP`，并对三种带状态的
  技能校验 `StateP` 来源。短生命周期导弹在重连期间自然过期不算同步错误，但不得恢复
  未知或已删除实体。
- 下一项是收敛 headless 日志并将全部区域技能双端/重连门槛接入 CI；战斗、地图和网络
  仍由当前 Chat 统一维护。

### 2026-09-11 headless 回归任务

- 默认 headless 区域技能测试进入 quiet 模式，仅输出阶段结果、警告和错误；`--verbose`
  保留原始 DRLG/网络同步调试日志。
- 新增 `:server:d2gs:headlessAreaSkillRegression`，覆盖 Hydra、Volcano、Armageddon、
  Hurricane、Thunder Storm、Meteor、Blizzard、Frozen Orb 的双客户端快照与断线重连。
- 真实 1.10f MPQ 回归全绿；下一项是接入 CI，资源继续由 `D2_HOME` 注入，不提交 MPQ。

### 2026-09-11 CI 工作流

- 新增 `.github/workflows/d2gs-headless.yml`。公共 runner 只执行 D2GS 编译和无资源专项
  测试；真实 MPQ 验收必须通过手动 workflow dispatch、Windows self-hosted runner 和
  `D2_HOME` 仓库变量启用。
- 工作流不会下载、缓存或提交暴雪 MPQ；真实任务先检查 `d2data.mpq`，再调用
  `headlessAreaSkillRegression`。

### 2026-09-14 Automap 原生 DC6 可见性修复

- 当前 Chat 继续统一负责地图、战斗、技能、物品、任务、NPC、网络和存档；本轮只修改
  Automap 客户端绘制与离屏验证，没有独立战斗 Chat 需要避让。
- Automap 地形/对象/怪物/NPC cell 已统一使用原生 8x4-per-DT1-tile 投影和 DC6 BBox
  锚点；修复了有效实体 cell 被条件反转跳过的问题。
- 真实 1.10f 的 854x480 离屏截图通过，结果为
  `terrain=600 entities=58 fallback=1 visiblePixels=17910`。
- 下一项恢复 A4Q2 传送门重连，当前已知失败点为 Harrogath 目标 Zone 尚未注册导致
  `WARP_DESTINATION_MISSING`；相关半成品保持未提交，未混入 Automap 提交。

### 2026-09-15 ESC Options / Automap 三态菜单（本轮完成）

- 当前 Chat 继续统一负责地图、战斗、技能、物品、任务、NPC、网络、存档和 UI；没有
  独立战斗 Chat 需要避让。
- ESC `OPTIONS` 已接入二级页面和返回栈；Automap Options 提供并持久化：
  `AUTOMAP SIZE`、`FADE`、`CENTER WHEN CLEARED`、`SHOW PARTY`、`SHOW NAMES`。
- `AUTOMAP SIZE` 是三态选择：`FULL SCREEN`、`MINI MAP (Left-Top)`、
  `MINI MAP (Right-Top)`。全屏模式除 12px margin 外使用整个画面；左右上角模式使用
  画面宽高各 1/2，并保留外侧 margin。
- 原生 DC6 与几何回退共享 `AutomapViewport`；Tab 关闭/打开保留所选模式，选项改变时
  已打开地图立即切换。Party 标记只接受权威 `PARTY_MEMBER`，名称使用 Automap 投影。
- 验证：Automap 全套无资源测试、RoomEx 集成测试和 `:core:compileJava` 通过；真实 1.10f
  `:desktop:offscreenAutomapDc6` 通过，输出
  `terrain=600 entities=58 fallback=1 visiblePixels=17910`。测试中发现并修复了单机没有
  `ClientNetworkReceiver` 时的可选依赖注入崩溃。

下一项：实现 Sound Options 的声音/音乐开关和音量控件，直接绑定已有音频 Cvar；随后
实现 Video Options 中已有后端支持的 Gamma/VSync，并把暂不支持的选项明确禁用。

### 2026-09-15 ESC Sound Options（本轮完成）

- 当前 Chat 继续统一负责地图、战斗、技能、物品、任务、NPC、网络、存档和 UI；没有
  独立战斗 Chat 需要避让。
- [x] ~~将 `SOUND OPTIONS` 占位页替换为可操作页面~~：提供总声音、音效、音乐开关，
  以及音效/音乐 0%–100% 的 10% 循环音量控件。
- [x] ~~直接绑定并持久化现有 `Client.Sounds.*` Cvar~~；页面每次打开都会从 Cvar
  刷新，音乐控制器会立即更新当前曲目，新播放音效使用更新后的音量。
- [x] ~~补充音量步进、边界归一化和百分比标签测试~~；音频专项测试、核心编译以及
  真实 1.10f 隐藏营地测试均通过，营地结果为 `result=PASS act=1 player=99 frames=3`。
- 已知后端限制：已经开始播放的短音效暂不支持中途调音，`SoundVolumeController` 原有
  TODO 保留；这不影响后续音效，也不影响当前音乐即时刷新。

下一项：实现 Video Options 中已有后端支持的 Gamma/VSync，并将当前没有后端支持的
视频选项明确显示为禁用，避免菜单给出虚假的可操作状态。

### 2026-09-15 ESC Video Options（本轮完成）

- [x] ~~将 `VIDEO OPTIONS` 占位页替换为可操作页面~~：Gamma 和 Vertical Sync 直接
  绑定现有显示 Cvar，修改后立即作用于调色板批次和 LibGDX 图形上下文。
- [x] ~~Gamma 按 50%–400% 每次 10% 循环~~，并对异常持久化值做边界归一化。
- [x] ~~将当前没有运行时后端的 `RESOLUTION` 显示为 `NOT AVAILABLE` 且不可点击~~，
  避免菜单提供无效操作。
- [x] ~~补充 VideoOptions 纯逻辑测试~~；专项测试、核心编译、真实 1.10f 隐藏营地
  回归均通过，营地结果为 `result=PASS act=1 player=99 frames=3`。

下一项：实现 Configure Controls 键位编辑页面，沿用现有 `GdxKeyMapper` 持久化和
冲突处理能力；暂不支持的控制项继续明确标记为禁用。

### 2026-09-15 ESC Configure Controls（本轮完成）

- [x] ~~将 `CONFIGURE CONTROLS` 占位页替换为可操作按键页面~~；常用游戏、技能、腰带、
  移动和 Automap 映射均显示主键/副键。
- [x] ~~点击主键或副键后捕获下一次键盘输入~~；Esc 取消，Backspace 清除，重复占用会
  显示冲突并保留原绑定。
- [x] ~~接入 `GdxKeyMapper` 的即时保存和默认值恢复~~；默认值在加载持久化覆盖前捕获，
  `RESET DEFAULTS` 可恢复并写回配置。
- [x] ~~录入期间暂时抑制全局快捷键分发~~，避免捕获已有按键时误触发移动、施法等游戏动作。
- [x] ~~完成核心编译与真实 1.10f 隐藏营地回归~~；营地结果为
  `result=PASS act=1 player=99 frames=3`。

下一项：继续补齐 Options 中尚未覆盖的客户端显示/输入细节，并把键位编辑的离屏 UI
状态纳入专项截图测试；战斗、地图和网络逻辑保持不变。

### 2026-09-15 Configure Controls 离屏状态回归（本轮完成）

- [x] ~~抽取 Configure Controls 状态契约~~：统一描述空闲、等待主/副键、冲突、已保存、
  清除和恢复默认状态。
- [x] ~~隐藏渲染入口增加三种状态场景~~：`controls-capture`、`controls-conflict`、
  `controls-defaults`，与实际菜单共用状态标签。
- [x] ~~生成 854x480 PNG 并写入 manifest~~；三种新增场景及原有六种界面场景全部 PASS。
- [x] ~~补充 `ControlsOptionsStateTest` 状态转换测试~~，核心编译和测试均通过。

下一项：继续核对并实现 Options 中尚未覆盖的客户端输入/显示细节；若无新的 UI 缺口，
转入 A1–A5 游戏流程的离屏回归收敛。

### 2026-09-15 Options 显示/输入细节（本轮完成）

- [x] ~~补齐 `SHOW FPS` 五态显示位置~~（OFF、左上、右上、左下、右下），直接绑定
  `Client.Display.ShowFPS`，沿用客户端实时绘制后端。
- [x] ~~补齐 `VIBRATION` 开关~~，直接绑定 `Client.Input.Vibration`。
- [x] ~~将 Android 专属 `STATUS BAR` 在桌面端显示为不可用~~，不提供无效操作。
- [x] ~~增加 FPS 模式循环/非法值归一化测试~~；核心编译和真实 1.10f 隐藏营地回归通过。

下一项：进入 A1–A5 游戏流程离屏回归收敛，优先检查区域切换、任务奖励、多人快照和
重连状态，Options 暂不再扩张。

### 2026-09-15 A1–A5 区域切换双客户端回归（本轮完成）

- [x] ~~新增 `headlessAreaTransitionDual` 离屏双客户端门槛~~：A1 通过权威区域切换
  验证，A2/A3/A4/A5 各选一条原生 1.10f 静态 Warp 边并双向往返。
- [x] ~~每条边均经正常 `WARP_INTERACTION` 网络请求~~，两个客户端同时移动到目标区，
  检查目标区快照和反向 Warp，避免仅调用服务端测试钩子掩盖网络/同步问题。
- 验证：`D2_HOME=G:\\BaiduNetdiskDownload\\Diablo II 1.10F`
  `:server:d2gs:headlessAreaTransitionDual` 通过，日志为
  `area_transition_dual_pass pairs=4 transitions=9 clients=true,true`。

下一项：在区域切换基线之上收敛 A1–A5 任务奖励快照，优先检查奖励 pending/granted
状态、重复请求幂等和多人断线重连恢复。

### 2026-09-15 A1–A5 任务奖励回归入口与生命周期稳定性（本轮完成）

- [x] ~~新增 `headlessQuestRewardRegression` 聚合任务~~，统一串联 Den、Countess、
  Andariel、A2Q6、A4Q1、A4Q3、A5Q5 和 Baal 奖励快照/幂等/重连夹具。
- [x] ~~修复任务完成后实体移除的竞态崩溃~~：`AnimStepper` 对已移除的 `AnimData` 做
  空组件保护，`NetworkSynchronizer` 清理 recipient 快照时容忍并发失效的空缓存。
- 验证：核心/D2GS 编译通过；真实 1.10f `headlessCountessQuestDual` 通过，日志确认
  `rewardGranted=true pending=false duplicate=true reconnect=true`。整合回归曾在修复前
  暴露上述 NPE，修复后针对性回归无再现。

下一项：补齐多人任务奖励的统一快照断言（跨 Act 同一玩家在不同区域、奖励 revision
  单调性），再进入多人快照与断线重连专项收敛。

### 2026-09-15 多人任务快照 revision 单调性（本轮完成）

- [x] ~~新增 `headlessQuestRevisionDual` 离屏门槛~~：同一玩家在 A1、A2、A4、A5
  区域间切换并请求 Quest 快照，双方 40 条任务记录逐项一致。
- [x] ~~验证 revision 单调和重连恢复~~：跨区域 revision 不回退；断线重连后 revision
  与奖励标志均保持一致。
- 验证：真实 1.10f `:server:d2gs:headlessQuestRevisionDual` 通过，日志为
  `quest_revision_dual_pass levels=4 reconnect=true flagsEqual=true`。

下一项：进入多人快照专项，验证区域切换、任务奖励和战斗实体更新不会互相覆盖，随后
补齐断线重连后的增量快照/旧实体清理检查。

### 2026-09-15 多人快照与断线重连专项（本轮完成）

- [x] ~~新增 `headlessMultiplayerSnapshotRegression` 聚合入口~~，统一运行快照顺序、
  定向重同步、RoomEx 可见性/实体清理和地面金币重连四项门槛。
- [x] ~~验证跨区域旧快照丢弃与增量基线~~：区域切换后旧 level snapshot 不覆盖当前位置，
  recipient 基线只发送给请求方；断线后旧玩家/召唤物删除，掉落物和对象保持可见。
- 验证：真实 1.10f 聚合任务通过，日志包含
  `snapshot_resync_pass ... oldLevelDrops=167`、
  `room_persistence_pass ... prematureUnload=false` 和
  `reconnect_ground_loot_pass ... ownerWindowPreserved=true`。

下一项：继续补齐断线重连后的战斗实体增量（导弹、状态和死亡事件）专项，重点确认
旧实体 ID 不复活、短生命周期实体自然过期不会污染重连基线。

### 2026-09-15 断线重连战斗实体增量（本轮完成）

- [x] ~~修正 `headlessMissileCombat` 1.10f 夹具初始场景~~：生成角色先通过权威
  `headlessEnterLevel` 进入 Blood Moor，再选择原生敌对目标，避免在 Rogue Encampment
  等待不存在的怪物。
- [x] ~~验证导弹、AreaSkill、State 和死亡/复活实体的重连增量~~：允许断线期间合法
  生成的新子实体，仍拒绝已删除或未知实体重新出现；Fallen 双客户端死亡/复活、掉落
  和对端拾取保持一致。
- 验证：真实 1.10f `:server:d2gs:headlessCombatReconnectRegression` 通过，包含
  `headlessMissileCombat`、`headlessFallenDual`、`headlessAreaSkillRegression`；
  基础标枪命中 `life=5.00->3.00`，Frozen Orb 重连 `stale=false`，Fallen 重连复活/掉落
  与拾取均通过。

下一项：继续完善多人战斗快照的边界场景（多投射物并发、状态过期与实体删除同 tick、
  重连后死亡事件幂等），并保持固定 40ms Sim Tick 与渲染线程隔离。

### 2026-09-15 战斗实体全量过期边界（本轮完成）

- [x] ~~放宽短生命周期实体的重连终态断言~~：当权威 missile/state 与替代客户端集合
  同时为空时，按原生自然过期处理，不再把合法空基线误判为超时；未知或已删除实体
  仍无法通过集合子集校验。
- 验证：真实 1.10f `headlessAreaSkill -PareaSkill=62`（Hydra）通过，重连期间实体
  清理无 stale；完整 `headlessCombatReconnectRegression` 仍保持通过。

下一项：补充多 missile 并发和死亡事件幂等的独立离屏门槛，继续检查同一 Sim Tick 内
  生成、命中、删除与重连基线的顺序一致性。

### 2026-09-15 多 missile 并发与死亡奖励边界（本轮完成）

- [x] ~~`headlessMissileCombat` 增加并发投射物断言~~：至少 2 个不同 missile entity、
  owner 字段有效、同一 tick 的重复非删除同步直接失败。
- [x] ~~新增 `headlessCombatEntityEdges` 聚合任务~~：与 `headlessFallenDual` 一起
  验证复活 Fallen 的第二次死亡不会重复经验/掉落，且双客户端死亡、复活和拾取一致。
- 验证：1.10f `headlessMissileCombat` 通过（4 个 missile entity）；
  `headlessFallenDual` 通过（`dual_revive_pass`、`dual_native_no_reward_pass`、
  `dual_pickup_pass`）。

当前下一项：在上述门槛上增加固定 40ms Sim Tick 的生成→命中→删除顺序观测，并将
  missile 生命周期水位纳入重连基线比较；继续避免修改伤害和技能公式。

### 2026-09-15 固定 Tick 的 missile 生命周期顺序（本轮完成）

- [x] ~~记录 missile 创建、目标受伤和删除 tick~~：创建/命中/删除顺序单调，
  同一 tick 的重复非删除帧会被测试拒绝。
- [x] ~~固定步长回归~~：真实 1.10f `headlessSimulationTick` 通过，观测 25 TPS、
  `step=0.04s`；`headlessMissileCombat` 通过，4 个 missile 的生命周期顺序通过。

当前下一项：继续把死亡队列和短生命周期 state 的过期 tick 纳入顺序观测，并扩展到
断线重连实体水位比较，防止删除事件在重连后重复投递。

### 2026-09-15 State 过期与重连水位（本轮完成）

- [x] ~~实体级 State 生命周期记录~~：记录 StateP 创建/过期 Tick，并输出
  `area_state_expire` 日志。
- [x] ~~重连水位校验~~：重连客户端的 state 创建 Tick 不得早于原客户端；与 missile
  活动集合校验共同阻止旧状态复活。
- 验证：1.10f `headlessAreaSkill -PareaSkill=57` 与 Hydra 62 重连均通过。

当前下一项：暴露死亡队列的入队、处理、实体删除 Tick，验证重复死亡事件只结算一次，
并把死亡/掉落水位纳入重连比较。

### 2026-09-15 死亡队列 Tick 水位（本轮完成）

- [x] ~~新增 `headlessUnitLifecycleState` 只读接口~~：提供生命周期阶段、死亡序号、
  当前 Sim Tick 和 `deathHandled` 标记。
- [x] ~~Fallen 双客户端接入~~：死亡进入 DEATH 阶段后双方观察到复活，复活后的再次
  死亡继续保持无重复经验/掉落。
- 验证：1.10f `headlessFallenDual` 通过，`death_queue_pass ... handled=true`。

当前下一项：将死亡/掉落生命周期水位接入断线重连，比较死亡实体、尸体和地面掉落的
创建/删除 Tick，防止重连重复死亡事件或复制掉落。

### 2026-09-15 死亡/掉落重连水位（本轮完成）

- [x] ~~地面掉落创建 Tick 与 incarnation 记录~~：同一掉落实体重连前后 incarnation
  保持为 1，创建 Tick 不回退。
- [x] ~~`headlessReconnectGroundLoot` 水位断言~~：部分拾取后的金币数量、归属和实体
  生命周期在重连后保持一致，不会复制掉落。
- 验证：1.10f 通过，`creationTick=11->19 deletionTick=-1 incarnation=1`。

当前下一项：覆盖死亡实体本身的断线重连，比较尸体保留、旧实体删除帧和死亡事件水位，
并加入重复死亡请求的协议级幂等断言。

### 2026-09-15 死亡实体断线重连（本轮完成）

- [x] ~~新增 `headlessDeathReconnect` 双客户端门槛~~：击杀后断开并重连同一角色，
  尸体保持死亡状态，实体 incarnation=1，地面掉落数量不增加。
- [x] ~~死亡水位跨连接校验~~：`deathTick`/`deathHandled` 已记录，重连后的 Sim Tick
  单调前进；XP 以持久化快照为准，不跨连接比较内存玩家实体。
- 验证：1.10f 通过，`death_reconnect_pass ... corpseDead=true rewardsStable=true`。

当前下一项：补充重复死亡请求的协议级幂等断言，并覆盖尸体删除/保留窗口的重连边界。

### 2026-09-15 重复死亡事件协议幂等（本轮完成）

- [x] ~~`headlessReplayDeathEvent`~~：在 D2GS 权威应用线程重放 DeathEvent，模拟
  近战/投射物重复通知，避免测试线程直接访问 ECS。
- [x] ~~`headlessDeathIdempotency`~~：连续重放两次后，死亡阶段、水位、奖励 claim、
  XP、掉落数量及尸体状态均保持稳定；不会把旧实体复活。
- 验证：1.10f 离屏测试通过，`death_idempotency_pass ... duplicateEvents=2`；
  `headlessDeathReconnect` 在独立时序下仍保持通过。

当前下一项：继续覆盖尸体保留窗口与删除窗口的断线重连边界，确认删除帧只投递一次、
旧实体 incarnation 不复用。

### 2026-09-15 尸体保留/删除窗口（本轮完成）

- [x] ~~保留窗口重连~~：死亡实体仍在权威 RoomEx 时重连，快照保持死亡状态且
  incarnation 不变。
- [x] ~~删除窗口~~：NetworkSynchronizer 在 ECS 组件移除前缓存原接收者并发送一次
  删除终态；离开 RoomEx 的客户端按原生规则可不接收 tombstone。
- 验证：1.10f `headlessDeathReconnect` 通过，输出
  `death_delete_window_pass ... deletionFrames=1 incarnation=1`。

当前下一项：验证删除后重连基线，以及延迟 tombstone 与新实体同 ID/incarnation 复用的
快照顺序。

### 2026-09-15 延迟 tombstone 与重连基线（本轮完成）

- [x] ~~`headlessDelayedDeleteFrames`~~：注入 3 tick 删除延迟和重复删除帧，客户端
  通过删除水位忽略过期 tombstone，不回退当前快照。
- [x] ~~删除后重连~~：掉落实体断线重连基线保持数量、归属与 incarnation 稳定，删除后
  不会在新基线复现旧实体。
- 验证：1.10f 离屏测试通过，`stale_delete_ignored`、
  `reconnect_ground_loot_pass ... incarnation=1`。

当前下一项：增加同一数字实体 ID 快速复用测试，确保旧 tombstone 到达时不会误删新
incarnation，并继续检查跨区域实体基线恢复。

### 2026-09-15 同一数字实体 ID 快速复用（本轮完成）

- [x] 新增 `headlessEntityIdReuse`：死亡实体 A 删除并回收后，实体 B 复用相同数字
  ID，客户端观察到 incarnation 从 1 递增到 2。
- [x] 延迟重复 tombstone 在 B 快照之后到达时被忽略，B 保持可见且未被误删。
- 验证：真实 1.10f 离屏任务通过，输出
  `entity_id_reuse_pass ... delayedTombstoneIgnored=true`。

当前下一项：跨区域 RoomEx 切换与重连基线恢复，校验旧区域删除帧不会影响新区域
实体，且 level/creation watermark 保持单调。

### 2026-09-15 跨区域实体基线恢复（本轮完成）

- [x] `headlessCrossAreaBaseline` 覆盖 Level 10 → Level 2 的同数字 ID 复用。
- [x] 旧 Level 延迟 tombstone 不会删除目标 Level 的新 incarnation。
- [x] 重连后在目标 RoomEx 请求原子 BEGIN/END 基线，实体、level 和 creationTick 水位
  保持正确且不回退。
- 验证：真实 1.10f 离屏测试通过，输出
  `cross_area_baseline_pass ... oldLevelTombstoneIgnored=true reconnect=true`。

当前下一项：检查跨区域切换时在途 missile/state 与 owner 引用的清理和重连基线隔离。

### 2026-09-15 跨区域 missile/state 引用清理（本轮完成）

- [x] 新增 `ZoneTransitionCleanupSystem`：迁移玩家离开 Level 后，旧区域中引用该玩家的
  missile 及附着控制器排队删除；旧目标上的 source-owned 周期状态同步移除。
- [x] 新增 `headlessCrossAreaMissileState` 离屏回归，验证服务器清理结果为
  `serverState=[0, 0, 0]`，重连基线不恢复旧 missile/state。
- 仅修改地图/同步所需的服务端系统与测试夹具；未改动技能伤害公式。

当前下一项：补充多客户端处于不同 Level 时的负向同步断言，并将 owner incarnation
 水位并入跨区域快照校验。
