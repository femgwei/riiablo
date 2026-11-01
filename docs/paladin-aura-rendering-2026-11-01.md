# 光环绘制参数对齐 1.10f（2026-11-01）

## 范围

对齐 `States.txt aura=1` 的持久 Overlay 绘制。不改变光环属性、范围、伤害、
状态同步及多光环轮换；非 aura Overlay 保留原策略。

## 原版依据

原版 `D2Client.dll` SHA-256：
`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。

- `0x6FB5BA70` 创建 Overlay，读取 X/Yoffset 和 Height1–4；
  `D2Common #10430 / UNITS_GetOverlayHeight`：玩家返回 1，怪物返回
  `MonStats2.OverlayHeight - 1`，所以玩家使用 Height2。
- `0x6FB5BE13..0x6FB5BE1F`：读取行 +0x6C 的 AnimRate，左移 4 位保存；
  `0x6FB57F63..0x6FB57F75`：每次更新累加；绘制帧右移 8 位。
  因而每帧持续时间为 `16 / (25 * AnimRate)` 秒，不是 `1 / AnimRate`。
- `0x6FB57D55` 按 PreDraw 分层；`0x6FB57E2A..0x6FB57E4D` 把保存的
  X/Yoffset 加到单位坐标，并将行 +0x7C 的 Trans 直接传给绘制接口。
  Java 的屏幕坐标 Y 向上，因此应用 `-Yoffset - selectedHeight`。
- `Overlay.Trans` 是 D2Gfx DrawMode，不是 Missiles.Trans。
  D2MOO `source/D2Gfx/include/DrawMode.h` 和现有原版 D2Glide 核验说明
  draw mode 3 使用源色 + 目标色（ONE, ONE）。复用已经存在的 ADDITIVE
  路径，调色板索引 0 被遮罩，不再用亮度生成半透明 alpha。

上述是静态二进制/源码证据，不是原版运行时截图或逐像素一致性证明。
REA 证据保留在本机忽略文件 `.gradle/rea-paladin-aura-20261101-evidence.json`。
其中创建函数伪代码 Evidence 为
`ev_a3c71c0f0f068cd4a098a5f761f58c4ea303196e5289f42d8bf2659968e4e0c6`；
反编译存在寄存器/参数恢复问题，字段及调用参数以原始指令为准。

## 实现

- OverlayManager 根据同步状态的 aura 标志选择 AuraRenderPolicy。
- 保留原 DCC 文件、DCC 帧边界偏移及 PreDraw 前后层级；不另画几何圆环。
- 光环使用原版速率和加法混合；位置加上表偏移与单位高度。
- 清理/复用 Overlay 时清除 nativeAura 标记，避免影响后续非光环效果。
- 实际 MPQ 中 Might 前/后层 Xoffset=4；Prayer/Defiance 等 AnimRate=8，
  对应 12.5 FPS；AnimRate=16 对应 25 FPS。检查到的 aura Overlay 均为 Trans=3。

## 验证

使用 JDK 17：

```powershell
.\gradlew.bat :core:auraRenderRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

专用 source set 避开尚在迁移的旧 UI 测试。覆盖速率、坐标/高度、混合参数，
真实 MPQ 状态及 DCC 存在性，以及原有状态 Overlay 生命周期；另由真实
OverlayManager + mock DCC/AssetManager 验证生产加载路径，测试不创建 GPU 纹理。
本机 JDK 17 实测 3 类共 13 项，失败/错误/跳过均为 0；生产代码编译和模块边界检查通过。

仍需另一台电脑实机确认：力量环与脚底对齐，祈祷/反抗播放速度，信念前后层，
明暗地面上的颜色叠加。没有运行原版与 riiablo 的逐帧截图对照，不能声称
所有调色板、后端及光照条件下已通过逐像素验收。

## 入场恢复修正

玩家创建原来只恢复 CharData 的右手技能，未像 SpellsQuickPanel 切换技能那样调用
AuraEcsSystem.selectAura，导致首次入场没有权威 aura state。现在 ServerEntityFactory
完成属性、状态、位置初始化后调用 restoreSelectedAura，首个正常 aura pulse 即发布状态，
由现有 StateOverlaySystem 显示。网络纯表现世界未安装该权威系统，不自行创建状态。
新增存档右手 Might 恢复、重复恢复不重启、普通 Attack 不激活光环的回归断言。
本次是小范围初始化补漏，按约定未编译或执行测试，仅做差异及调用接口检查。

## 装备光环与队友宠物补全

后续扩展回归已包含上述入场恢复测试。装备自动光环与谜团的主动传送
（item_nonclassskill）是两条不同链路，不能通过恢复右键技能代替。

原版依据（本地 `F:\3rd_src\D2MOO`）：

- `source/D2Game/src/ITEMS/ItemMode.cpp:269`：item_aura 属性变更调用
  SKILLITEM_ActivateAura / SKILLITEM_DeactivateAura。
- `source/D2Game/src/SKILLS/SkillItem.cpp:959`：每个装备来源单独注册
  PERIODICSTATS；只有 Skills.txt Immediate 的行才立即执行。
- `source/D2Game/src/SKILLS/Skills.cpp:2809`：装备周期事件读取按技能编号
  汇总的 STAT_ITEM_AURA 等级，不读取右键技能等级或额外 +skills。
- `source/D2Game/src/UNIT/SUnit.cpp:2120`：SUNIT_AreUnitsAligned 先解析
  双方 minion owner，再判断玩家队伍；队友雇佣兵和召唤物也属于友方。
- `source/D2Game/src/MISSILES/MissMode.cpp:2996`：增益战吼命中同样使用
  SUNIT_AreUnitsAligned；`SkillBar.cpp:249` 仍创建有到期帧的限时状态，
  不应在登录时凭当前技能选择无条件重施。
- `source/D2Game/src/UNIT/SUnitMsg.cpp:178`：初次同步发送单位当前状态集合。

修正内容：

- 权威 AuraEcsSystem 每帧对齐已提交的装备属性及实际启用的装备槽。
  玩家与雇佣兵入场无需切技能即可重新建立装备光环调度。
- AuraManager 将选中技能与各装备来源分开调度；同类装备读取合计等级，
  不与主动技能等级相加。多件装备保留各自的周期来源，伤害脉冲不被
  简单折叠为一个来源；目标状态仍通过原有最高等级仲裁，避免增益重复叠加。
- 换右键技能只取消主动流；卸装、破损、切副手和死亡会撤销对应装备流。
  撤销时清理来源自身状态，队友保留的末次短状态按原有期限消失。
- 属性合并允许相同非零参数层求和，修复双装备同技能合并时的断言错误。
- 三种德鲁伊灵的友方筛选补上队友的雇佣兵与召唤物。
- 继续沿用状态快照和原生 Overlay 绘制，不在客户端伪造永久光环。

验收仍需另一台电脑实机检查：携带光环装备直接入场、雇佣兵携带无限/眼光、
右键光环与装备光环并存、双梦境、换副手、卸装，以及队友宠物首次进入视野。
自动测试覆盖权威状态与首次快照表现，不等同于 GPU 逐帧原版对照。

本轮 JDK 17 + 本地 1.10f MPQ 执行 `:core:auraRenderRegression`：
10 类共 75 项，失败/错误/跳过均为 0，生产代码编译与模块边界检查通过。
包括 20 种已注册圣骑士光环的装备激活/撤销、真实装备列表的双同类光环
合计与来源数量、武器切换/破损/卸装、三种灵对队友宠物的覆盖与撤销，
以及原有战吼、抗性/辅助/特殊光环回归。
