# 当前 Chat 维护状态

更新时间：2026-09-10

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
- 当前功能提交：本文件所在 `HEAD`（召唤物跨房间/跨区域重组与传送边界）
- 上一功能基线：`c4e1f6b7`（召唤物主人目标继承与 PvP 所有者关系）
- 远程：完成本次提交后推送 `origin/master`，最终结果以交付报告中的 hash 为准
- 工作区：本次提交完成后应为干净
- 总体对齐进度：约 69%（详见路线图）
- 第一章最小可玩闭环：约 79%

## 本次更新

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
- 十项死灵法师诅咒现统一读取 1.10f `Skills.txt/States.txt`，覆盖原生
  `SrvDo030/059/061`、目标点范围、难度时长、状态 stat、免疫怪物 1/5 抗性削减和
  负物理抗性伤害；玩家与怪物不再走两套实现。
- 定向核心测试、D2GS 编译以及真实 1.10f `offscreenCamp` 均通过。
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

## 下一步

当前已完成 **P1 死灵法师召唤与诅咒链首轮**：Raise Skeleton/Skeletal Mage、Revive、
四类 Golem、Iron Maiden/Life Tap 及 Dim Vision/Attract/Confuse 特殊 AI 均已接入。
骨毒系已完成 Bone Armor、Poison Dagger、Corpse Explosion 和 Poison Explosion。
Bone Wall/Prison 已完成可破坏单位、地图碰撞和生命周期；Poison Nova 已完成原生
64 路导弹、固定毒率和多人快照；Bone Spear/Spirit 的魔法快照、穿透和追踪也已接通，
召唤物跟随与脱战、原生概率流/骷髅法师节奏、敌我筛选、目标连续性、主人目标继承、
PvP 所有者关系、跨房间/跨区域重组及 Revive/NecroPet 特殊 AI 已完成；下一步补齐四类
Golem 的命中减速、生命分配、来源物品属性和火焰/光环副作用，再进入跨职业技能验收。
战斗模块不再单独分派，相关修改均由本 Chat 负责。

当前阶段说明：项目已进入 **P2 执行阶段**，但 P0/P1 仍保留少量严格验收尾项；P2 与
这些尾项并行推进，不表示底层阶段被跳过或记录丢失。
