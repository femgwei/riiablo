# Codex 任务交接基线

更新时间：2026-09-21

仓库：`D:/code/riiablo`

分支：`master`

## 事实基线

- 本轮开始提交：`79407891`（开始时与 `origin/master` 同步）。
- 本轮提交：以本文件所在提交的 `HEAD` 为准。
- 原版资源目录：`D:/Games/Diablo.II.v1.10F`。
- 原版代码参考：`D:/code/D2MOO`，数据与交叉实现参考 `dark-magic`、`Diablerie`。
- `game.log` 和 `_tmp_run.ps1` 是本地诊断文件，不加入 Git。

## 2026-09-21 完成内容

### 地图、房间与移动

- 城镇加载时按出口方向预生成直接连接的野外入口房间及相邻房间；玩家靠近野外区域出口时，
  提前预生成下一区域入口附近的怪物和物件，避免跨区瞬间集中出现。
- 延迟生成的传送点在读档后首次传送前会主动实例化所属 RoomEx，修复野外传送点已激活但城镇中
  无法传出的情况。
- D2MOO_JAVA 导出具体 DT1 来源文件，运行时按来源文件和 tile id 选择图块及替代帧，避免不同
  DT1 中相同 id 导致洞穴入口等图块在悬停或动画替换时切错资源。
- 移动模式改为读取碰撞后的实际位移；角色被火把或墙体挡住后速度归零，不再原地保持跑步动画。

### 物品、药水与交易

- 按原版 ItemTypes `TreasureClass` 动态建立 `armoN`、`weapN` 等物品类型掉落类，并接入怪物、
  箱子和 Countess 奖励解析，修复低级怪物长期只掉药水、箭矢和金币的问题。
- `Alt` 可显示全部地面物品标签；背包打开时点击地面装备会拾取到光标，背包关闭时仍自动放入
  腰带或背包。空间不足时物品在原地重新播放弹起动画和掉落声音。
- 本地与网络商店均可出售装备和药水；服务快照刷新后保持出售/修理模式，并防止出售请求与
  `STORE_TO_CURSOR` 请求冲突。
- 红蓝药水改为按 `Misc.txt` 数值和持续帧逐步恢复，紫药水保持立即按百分比恢复；腰带右键使用
  正确播放声音并由权威状态更新生命或魔力。
- 控制面板 Automap 按钮已连接到实际地图开关。

### 任务、弓箭与导弹

- Charsi 的 Horadric Malus（Act I 第五个界面任务）增加原版角色等级 8 门槛；服务器对话、NPC
  提示和任务日志统一校验，低等级不能提前接取。
- 普通弓/弩 Attack 在本地权威模式中创建实际 `arrow`/`bolt`，保留伤害快照并扣除弹药，不再
  只有射击动画而没有投射物。
- 按 Diablo II 1.10f 修正穿透：`Missiles.txt.Pierce` 仅允许读取穿透属性，不再直接赋予 100%；
  穿透率为 `item_pierce + skill_pierce`，发射时预掷并最多保存 4 次穿透。无穿透属性的普通箭
  命中首个目标即消失，Guided Arrow 不错误继承穿透，骨矛等固有持续穿透导弹保持原行为。
- 导弹不再被碰撞后同步阶段误判为零速度，修复玩家箭矢和 Fallen Shaman 火球悬停半空、随后
  战斗更新停止的问题。

## 验证状态

- `:core:compileJava` 与 `:server:d2gs:compileJava` 通过。
- 普通弓箭、Guided Arrow、Strafe、穿透次数上限、投掷武器和骨矛定向测试通过。
- `AmazonSkillSpecializationTest` 共 21 项，20 项通过；唯一失败仍为既有的
  `immolationArrowCreatesPersistentFireField`，不属于本轮穿透修改。
- 本轮新增和修改的地图、物品、药水、任务、拾取、房间预热测试随提交保留。

## 后续建议

1. 用真实角色连续测试 Fallen、Fallen Shaman 和 Zombie 的装备掉落分布，确认物品类型 TC
   在实际 seed 下有装备、药水、金币和弹药混合结果。
2. 分别验证本地和 D2GS 商店的装备/药水出售、满包拾取弹起、背包开启拾取到光标。
3. 从 Rogue Encampment 进入 Blood Moor，再从 Blood Moor 接近 Cold Plains，观察
   `[TOWN_EXIT_PREWARM]` 与 `[LEVEL_EXIT_PREWARM]` 日志及跨区前后的怪物出现时机。
4. 后续单独处理 Immolation Arrow 持续火焰场测试，以及仓库完整测试集中的既有失败。

## 2026-09-21 商人交易修正

- 对照 D2MOO `D2Common/src/Items/Items.cpp` 修正箭/弩矢售价：quiver 使用
  `quantity * dwCost / 1024`，不再把整叠弹药直接乘出几千金币；本地交易同时使用对应 NPC 的买卖倍率。
- 对照 D2MOO `D2Game/src/UNIT/SUnitNpc.cpp`，出售的普通装备会进入当前 NPC 的公共库存，
  在库存刷新前可以从商人货物栏买回；买回后从库存移除。
- 本地模式缓存普通商人库存，关闭交易窗口不会重新生成；网络模式继续使用 NPC 共享 session 和 revision。
- 增加城镇库存刷新监听：城镇内最后一名玩家离开后清空普通交易/赌博库存并重新生成；多人同城时，
  单个玩家关闭窗口或离开不会刷新其他玩家正在使用的公共库存。
- 新增 `NpcVendorSessionSystem`，同时接入本地 `GameScreen` 和 D2GS；新增 quiver 定价回归测试源码。

## 本轮验证与待测

- 已通过：`:desktop:compileJava`、`:server:d2gs:compileJava`、`:core:compileTestJava`。
- 尚未进行运行时验证，需要重点确认：
  1. 出售完整十字弓弹药堆，售价应为几十金币量级，而不是几千或上万；同时检查普通装备、药水和不同 NPC 的价格倍率。
  2. 出售普通装备后，在未离开城镇前关闭并重新打开交易窗口，确认物品仍在货物栏且只能买回一次；买回后确认物品回到背包、商人库存移除。
  3. 分别测试本地模式和 D2GS 模式的出售/买回、库存 revision 变化及背包空间不足时的失败处理。
  4. 多人场景下让一名玩家离开城镇、另一名玩家仍留在城镇，确认商人库存不刷新；最后一名玩家离开后再进入，确认库存重新生成。
  5. 观察 `[VENDOR_SELL]`、NPC service result 和区域切换日志，确认出售金额、库存快照和刷新时机一致。
