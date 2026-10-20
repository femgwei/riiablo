# A1～A5 任务模块与跨幕入口

本轮范围是一次性整理五幕任务模块、连接已有玩法入口并修复 Atma 提示，
不是宣称全部任务已达到 1.10f 完整通关一致性。用户此前人工验证仅 A1Q1、A1Q2。

## 模块边界

- 新增独立 Gradle 模块 `:quests`，42 个既有规则/状态类型物理移入，保留 Java 包名。
  依赖方向为 `core -> quests -> server-domain -> game-data -> shared`；
  `server-domain -> D2MOO_JAVA`，没有反向依赖 core。
- `QuestInventory`、`QuestExperience`、`QuestPlayer` 是窄接口；规则不依赖
  CharData、ItemData、经验表运行时、地图、窗口或全局 Riiablo。
- `QuestModule` 是 core 中的权威装配/网络消息入口；ECS、物品生成、地图对象、
  存档宿主适配器仍留在 core，不能声称整个任务运行时已脱离 core。
- 单机与 D2GS 保留原有权威系统顺序；每次装配创建独立实例。
  玩家持久记录与 A5 遭遇、任务 RNG、世界传送门等会话状态不合并。
  旧 `QuestManager` 保留兼容，但没有作为新的生产入口。
- 新模块接入 `verifyModuleBoundaries` 的依赖方向、唯一源码归属、零主机/表现/全局 RNG 门禁。

## 修复内容

- `QuestDialogController` 统一 NPC 自动任务提示与“Quest”菜单入口，A1 保留已验证的
  专用控制器；A2～A5 使用同一个 `QuestDialogue` 选择规则。
  自动交互只打开需要注意的分支，进度复述和幂等传送对话可由菜单主动打开。
  后几幕不在旧 TALKERS 名单的已支持 NPC 增加独立 Quest 按钮，不附带错误的 A1 闲聊键。
- 头顶感叹号沿用原 DCC 和资源生命周期，扩展至 A2～A5；Atma 新任务、领奖显示，
  已接取但未完成时不重复显示，领奖后消失。
- A2 接入 Cain 法杖物品对白、召唤者领奖、Duriel/Tyrael/Jerhyn/Meshif 收尾。
  Cain 已确认合成后的法杖不永久显示感叹号；召唤者校验精确匹配 NPC 与消息编号。
- A3 接入金鸟、吉德宾已有对话分支及联网校验；金鸟交换仍走原有物品原子交换，
  Ormus/Asheara 独立领奖，不用一条完成标记跳过另一个奖励。
- A4 统一 Izual/Hellforge/Act V 传送入口；Izual 待领奖优先。
  Diablo 收尾传送与巴尔终局首次显示注意标记，确认后只保留幂等菜单重放。
- A5 补齐 Larzuk 初始消息 `20077`、已有救兵/Malah/Anya/Ancients 对话入口和网络校验。
  Anya 新任务不再因尚无 STARTED 位而被拒绝。扩展 NPC 先按稳定 Id/NameStr 解析，
  避免原表序号偏移让 Malah 和 Anya 混淆。
- Malah 领奖消息改为原版回调处理的 `20132`；`20122` 是进度对白，不是领奖回调。
- A2～A5 直接按原版数字消息编号查询 StringTBLs，不再引用缺失的 Act I speech 映射而
  显示 unknown。没有伪造声音映射；尚未建立的非 A1 语音播放保持无声。
- 任务日志读取所属幕的当前难度记录，修复原来始终读 A1 的问题。
  A3 的资源顺序与项目存档槽位不同：Lam Esen=4、Khalim=3、Gidbinn=2、Golden Bird=1；
  `QuestCatalog` 显式映射，不按 UI 顺序/global QuestId 猜槽位，不迁移或重排旧存档。
  吉德宾在两个独立奖励完成前不提前显示完成图标。
- `QuestRecords` 统一 5×8 当前难度快照、修订哈希及原子回填，PlayerP、QuestResult、
  D2GS 重连共用；不足/超出 40 条、越界值或缺失目标数组均不部分写入。
  协议和 D2S 布局、哈希算法不变。远程对话发送失败不回退成客户端权威事件。

## 原版依据

优先核对本机 `F:\3rd_src\D2MOO\source\D2Game\src`，本轮无须搜索推测规则：

- `QUESTS/ACT2/A2Q1.cpp`：Atma ActiveFilter 与 304/334 消息；
  `A2Q2.cpp` Cain 物品对白，`A2Q5.cpp` 419～429 NPC 精确映射，`A2Q6.cpp` 收尾。
- `QUESTS/ACT3/A3Q4.cpp` 是 Golden Bird，`A3Q3.cpp` 是 Gidbinn；
  不能把本项目面向玩家的 Q1/Q2 名称直接当原版源码编号。
- `QUESTS/ACT4/A4Q1.cpp`、`A4Q2.cpp`、`A4Q3.cpp`。
- `QUESTS/ACT5/A5Q1.cpp` Larzuk 初始/收尾，`A5Q3.cpp` Malah 20116/20132，
  `A5Q4.cpp` Anya，`A5Q5.cpp` Qual-Kehk，`A5Q6.cpp` 终局。
- `OBJECTS/Objects.cpp / OBJECTS_InitFunction59_VileDogAfterglow`：
  生成为 frame+27，动画结束为 frame+(FrameCnt[1]>>8)+1，是独立事件。
  本轮只修正落后的测试夹具/时间期望，没有改动余晖生产逻辑。

## 可复验命令

```powershell
.\gradlew.bat :core:questRegression :server:d2gs:test --tests com.riiablo.server.d2gs.D2GSSimulationAssemblyTest :desktop:compileJava --no-daemon "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F"
```

独立规则可只执行 `:quests:test`；这条依赖链不构建 core。
真实资源烟测可执行：

```powershell
.\gradlew.bat :desktop:offscreenCamp --no-daemon "-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F" "-Pd2Version=1.10f" "-PvisualOutput=build/visual-tests/quest-module-20261020"
```

## 验收边界与另一台电脑检查

最终自动验证：`:core:questRegression` 61 类 222 项、`:quests:test` 17 项、
D2GS 装配 5 项，合计 244 项，失败/错误/跳过均为 0；模块边界检查与 core/D2GS/desktop
编译通过。真实 1.10f 营地最终烟测渲染 61 帧、稳定捕获 3 帧，manifest 为 PASS，
退出清理为 PASS（assets=0、输入/全局别名清除）。产物位于
`desktop/build/visual-tests/quest-module-final-20261020/rogue-encampment-manifest.txt`。
保留慢帧调整诊断，不把烟测视作性能验收。模块测试、模拟交互和营地烟测不能替代五幕实机通关。

扩大运行全部 D2GS 测试曾得到 48/50：`MapTourProbeTest` 的门前静态寻路和
`MapTourWalkingTest` 的占用接缝落点断言失败。本轮任务改动不涉及这些巡检路径，
未为消除断言修改地图/碰撞规则；不宣称全 D2GS 测试通过。

- 优先检查 A1Q1/Q2 没有退化，以及 A2 新角色 Atma `!`、开场任务文字、领取后隐藏、
  Radament 后再次出现、领奖后消失。奖励技能点仍由技能书消费，不是 Atma 重复发点。
- 逐幕检查任务菜单/日志、重进与联网同步；A3 重点金鸟物品交换、两份吉德宾奖励、
  日志槽位；A5 重点同名/重排 NPC、安雅入口、巴尔终局。
- `QuestDialogue` 使用当前项目持久记录推导可用分支，没有新增完整原版全局 fState
  复制协议；多人错峰入场的原版提示条件仍需要后续专门验证。
- A3 Lam Esen 完整任务和 Blackened Temple 专用链仍未实现；本轮没有新增假对话填满 UI。
  A5 Prison of Ice 仍沿用原有简化的解冻药状态位/奖励实现，不等于原版实际药瓶、
  抗性卷轴及 Anya 独立奖励完整链。Larzuk/Anya 服务权益也不等同所有服务 UI 已验收。
- 任务日志非 A1 文本继续使用既有阶段摘要，未移植所有原版 StatusFilter；非 A1
  任务语音回放未补全。A1 Charsi 的远程注入服务仍是既有缺口，不能当作已实现。
- 本轮不改变与任务无关、工作区中并存的战斗/伤害结算修改，不将其混入任务提交。
