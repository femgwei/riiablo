# D2MOO（C/C++ 实现）功能分析

> 检查对象：`F:\\3rd_src\\D2MOO`
>
> 检查日期：2026-08-23
>
> 说明：D2MOO 的源码以 C++（`.cpp`）为主，并不是纯 C 实现。本文档根据源码目录、CMake target 和关键实现文件整理，不把“存在文件”简单等同于“功能已经完整可用”。

## 总结

D2MOO 不是只有地图的项目，而是围绕 Diablo II 原版 DLL 接口的逆向重实现。地图/随机地牢（DRLG）只是 `D2Common` 中的一个子系统；`D2Common` 和 `D2Game` 还包含了绝大多数游戏规则和服务器逻辑，另外还有网络、图形、UI、资源处理、调试和补丁相关模块。

## 1. D2Common：通用数据与游戏规则

主要依据：[`source/D2Common/CMakeLists.txt`](F:/3rd_src/D2MOO/source/D2Common/CMakeLists.txt)

除 `src/Drlg` 地图生成、房间、迷宫、户外区域和瓦片替换代码外，还包含：

- 数据表读取和缓存：动画、竞技场、腰带、字段、赫拉迪克方块、背包、物品、等级、导弹、怪物、对象、覆盖层、序列、技能、Token、变换等。
- 物品和物品属性：物品创建、词缀/Mod、品质相关数据、插槽和物品属性计算。
- 怪物基础数据：怪物记录、区域和随机生成支持。
- 路径与导航：A*、IDA*、路径步骤、路径工具和寻路工作流。
- Unit 基础设施：玩家、怪物、物品、对象、导弹、导弹流、房间关联和 Unit 查找。
- 背包与装备：身体位置、背包网格、腰带、武器、盾牌、尸体、交易背包、物品放置/交换。
- 属性和状态：StatList、状态效果、临时状态过期、生命/法力/耐力、伤害/防御等派生属性。
- 技能基础：技能列表、技能等级、技能公式、被动技能、技能消耗、技能范围、左右键激活技能。
- 其他规则：碰撞、角色外观组合、聊天、文本、任务记录、传送点、随机种子、环境、日志。

对应源码集中在 `source/D2Common/src/Items`、`Monsters`、`Path`、`Units`，以及 `D2Inventory.cpp`、`D2Skills.cpp`、`D2States.cpp`、`D2StatList.cpp`、`D2QuestRecord.cpp` 等文件。

## 2. D2Game：实际游戏逻辑

主要依据：[`source/D2Game/CMakeLists.txt`](F:/3rd_src/D2MOO/source/D2Game/CMakeLists.txt)

### 游戏会话和调度

- 游戏创建、销毁和关卡切换
- 客户端列表和客户端消息
- 客户端/服务器命令与数据包
- 事件、任务队列、时间调度
- 目标选择和竞技场逻辑

### 玩家系统

- 玩家生命周期、玩家列表和玩家模式
- 属性点、升级、经验值和技能点相关逻辑
- 玩家消息和进入游戏流程
- 角色存档/读档（`PlrSave.cpp`、`PlrSave2.cpp`）
- 宠物/佣兵支持
- 组队、友好关系和队伍界面
- 玩家之间交易

### 怪物和 AI

- 怪物生成、区域管理、唯一怪物
- 怪物模式和消息
- 通用 AI、战术 AI、目标决策、Baal AI
- 怪物移动、攻击和技能决策

### 战斗、物品和对象

- 伤害计算、抗性、吸收、死亡、经验分配
- 物品生成、掉落、魔法/稀有/套装/暗金属性处理
- 物品耐久度和物品模式
- 导弹创建、移动、碰撞和命中
- 宝箱、机关、NPC 对象以及对象交互

### 技能

源码包含以下技能实现文件：

- 亚马逊：`SkillAma.cpp`
- 刺客：`SkillAss.cpp`
- 野蛮人：`SkillBar.cpp`
- 德鲁伊：`SkillDruid.cpp`
- 死灵法师：`SkillNec.cpp`
- 圣骑士：`SkillPal.cpp`
- 法师：`SkillSor.cpp`
- 怪物技能：`SkillMonst.cpp`
- 物品技能：`SkillItem.cpp`

### 任务

`D2Game` 中包含五幕任务代码：

- Act 1：`A1Q0`–`A1Q7`
- Act 2：`A2Q0`–`A2Q8`
- Act 3：`A3Q0`–`A3Q7`
- Act 4：`A4Q0`–`A4Q4`
- Act 5：`A5Q1`–`A5Q6`

同时有统一任务管理和任务特效代码（`Quests.cpp`、`QuestsFX.cpp`）。

## 3. 网络和多人游戏

主要依据：[`source/D2Net/CMakeLists.txt`](F:/3rd_src/D2MOO/source/D2Net/CMakeLists.txt)

- 客户端和服务端 Socket 通信
- 网络数据包收发和缓冲
- 客户端数据包校验
- 服务端回调和网络状态处理
- 与 `D2Game` 游戏数据包的连接

需要特别区分：`D2MCPClient` 目前是明显的占位模块，源码只有少量 Stub 函数，并不代表完整的 Battle.net/MCP 客户端实现。

## 4. 图形、资源和 UI

### D2CMP

包含 CEL/Tile 压缩解码、调色板、瓦片和精灵缓存、绘制上下文、Tile 查找等。

### D2Gfx

包含纹理、瓦片、窗口、缩放、绘制模式和子瓦片等客户端图形基础设施。见 [`source/D2Gfx/CMakeLists.txt`](F:/3rd_src/D2MOO/source/D2Gfx/CMakeLists.txt)。

### D2Win

包含客户端窗口和控件：

- 按钮、列表、文本框、输入框
- 字体、图片、动画图片
- 弹窗、进度条、滚动条、计时器
- 调色板、归档、Smack 视频和 JPEG 封装

见 [`source/D2Win/CMakeLists.txt`](F:/3rd_src/D2MOO/source/D2Win/CMakeLists.txt)。

### D2Lang、D2Hell、Fog、Storm

- `D2Lang`：字符串表、Unicode/UTF 转换、CRC16。
- `D2Hell`：文件、归档、CRC、故障处理。
- `Fog`：内存、位操作、字符串、Excel、异步数据、日志、异常、系统和 QServer/Socket。
- `Storm`：Storm API 和句柄兼容层。

## 5. 启动、补丁和调试

- `Game`：游戏入口和启动器逻辑。
- `D2.Detours.patches`：通过 Detours 将实现挂接到原版游戏 DLL 的补丁工程。
- `D2Debugger`：实验性调试器，包含 ImGui/D3D9 调试界面和 Common/Game 调试辅助。

顶层模块列表见 [`source/CMakeLists.txt`](F:/3rd_src/D2MOO/source/CMakeLists.txt)。

## 6. 完整度和使用限制

源码中存在功能实现、兼容层和 Stub，不能只根据目录名称判断完成度：

- 项目 README 说明 D2MOO 需要原版游戏资源和二进制文件：[`README.md:5-10`](F:/3rd_src/D2MOO/README.md:5)。
- `D2Common.dll` 可以尝试直接替换原版 DLL，但仍不保证无 Bug：[`README.md:69-74`](F:/3rd_src/D2MOO/README.md:69)。
- README 明确表示其他 DLL 目前不能直接替换：[`README.md:76-83`](F:/3rd_src/D2MOO/README.md:76)。
- `D2MCPClient` 源码标记为 `stubs`：[`D2MCPClient.cpp:3`](F:/3rd_src/D2MOO/source/D2MCPClient/src/D2MCPClient.cpp:3)。
- `D2Sound` 使用 `D2FUNC_DLL_STUB` 导出占位函数：[`D2Sound.cpp:5-9`](F:/3rd_src/D2MOO/source/D2Sound/src/D2Sound.cpp:5)。
- `D2Lang`、`D2CMP`、`Fog`、`Storm` 也存在显式 Stub 导出；Stub 宏定义见 [`D2Dll.h:18-29`](F:/3rd_src/D2MOO/source/D2CommonDefinitions/include/D2Dll.h:18)。

因此，D2MOO 可以概括为：**以 D2Common/D2Game 为核心、覆盖大部分 Diablo II 游戏规则的 DLL 兼容/逆向实现，同时附带网络、图形、UI、资源处理、启动、补丁和调试模块；但不是完全独立、所有 DLL 都已完成的游戏引擎。**
