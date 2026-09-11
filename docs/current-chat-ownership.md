# 当前 Chat 维护状态

更新时间：2026-09-11

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
- 当前功能提交：本文件所在 `HEAD`（复杂区域技能多人权威快照语义，待提交）
- 上一功能基线：`89c2e54a`（德鲁伊 Armageddon/Hurricane 原生状态链）
- 远程：完成本次提交后推送 `origin/master`，最终结果以交付报告中的 hash 为准
- 工作区：本次提交完成后应为干净
- 总体对齐进度：约 70%（详见路线图）
- 第一章最小可玩闭环：约 79%

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
