# 1.10f 寻路与碰撞：D2MOO 和当前运行时详细比较

日期：2026-10-15。riiablo 审查起点：`8b687045e`，包含近期地图巡游、碰撞和 DiabRun 修改。

## 结论与证据边界

**以复现原版行为为目标，D2MOO 的 D2Common 搜索规则、路径状态机和定点步进更接近原版；
不能据此推导出整包替换后性能更好，或者当前碰撞优化应当撤回。** 当前项目已经吸收大量
原版规则，但通用生产搜索器尚不是独立 Java 原生 A* 的直接调用者。

用户明确近期优化依据包括本机 `D2Client.dll`、`D2Common.dll` 逆向。本文不否定这些来源，
而是分别检查“来源”“独立移植”“当前生产调用链”。例如 `46091be40` 已将候选点碰撞规则
用于生产；独立 `D2AStar` 没有接入通用入口，并不等于生产代码没有原版逆向成果。
本轮没有找到将 D2Client 寻路函数地址逐项映射到当前生产代码的完整证据表，也没有重做
D2Client 的整条调用链逆向；因此不声称客户端行为整体不一致或整体已证明等价。

证据分三层：

- 源码：读取 D2MOO 已有远端引用 `0c0bbd1e6bef9181a3da1c046523af622a925416`。
  本轮没有重新 fetch；不代表已检查该日期远端所有新增提交。D2MOO 本地检出保持
  `8322494ed1f715ad51552f169df76cf600fabc71`，没有切换分支或覆盖文件。
- 二进制：静态反汇编本机 1.10f `D2Common.dll` 的几个关键分支，地址见文末。
  未执行原版 DLL 做动态逐帧差分。
- 回归：Java 原生参考实现与生产入口共享确定性夹具，检验具体差异。
  这不是对原版可执行程序做路径结果录制，也没有性能 benchmark。

## 1. 当前生产到底运行哪条链

普通移动的相关链路为：

```text
Pathfinder.findPath
  → Map.findPath
    → MapGraph.searchNodePath
      → com.riiablo.map.pfa.AStarPathFinder
  → Pathfinder.smoothPathWithDynamicCollision
  → 路点跟随、Velocity / VelocityAdder 或 Box2D 同步路径
```

`Map.java` 创建的是上述 `AStarPathFinder`。本轮在 `core/src/main/java` 搜索没有发现
对 `D2AStar`、`D2IDAStar`、`D2PathStep`、`D2PathMisc` 的直接调用。
这些独立参考实现在 `D2MOO_JAVA` 中存在，但不能用其单测通过代替生产已接入的证明。

其中 [D2AStar.java](../D2MOO_JAVA/src/main/java/com/d2moo/common/path/D2AStar.java)
类注释明确说明不依赖 riiablo 地图/运行时，允许调用者比较后再决定集成。
历史 `e70a93fd0`、`0f9121242`、`cfb4ae560` 分别已有 bounded A*、IDA* 和方向/步进原语移植。
后续应利用这些成果做差分，不必重写一份同类算法。

## 2. A* 搜索：D2MOO 更贴近原版，但不是天然更快

对照 D2MOO `Path/AStar.cpp` 与生产
[AStarPathFinder.java](../core/src/main/java/com/riiablo/map/pfa/AStarPathFinder.java)：

### 代价和启发式

- 原版：直走代价 2、斜走 3，启发式 `2 * max(dx,dy) + min(dx,dy)`。
- 生产：直走 1、斜走 `sqrt(2)`，启发式欧氏距离。

两套策略都可以产生合法路径，但不是同一套路径偏好，也不是仅把全部代价乘以常数。
相同障碍布局可能出现不同转弯点、不同绕行方向；只替换 heuristic 不足以复刻原版。
本机 DLL `0x6FDA7230` 已核实原版整数公式。

### 邻居顺序与同分处理

- 原版：先 `(-1,-1),(-1,1),(1,-1),(1,1)`，后 `(-1,0),(0,-1),(1,0),(0,1)`。
  pending 按 F-score 排序，新节点插到已有同分节点前面。
- 生产：先轴向、再对角，open list 为 `BinaryHeap`，没有显式复刻原版同分插入策略。

寻路中的同分不是无关细节：它会影响先探索哪边、预算内能走多远以及最终朝向。
另外原版 pending 节点降分没有采用生产代码的 remove/reinsert，visited 降分沿子节点传播；
这些也是要一起比较的行为，而不是仅比较“都叫 A*”。

### 预算与失败输出

- 原版 context：128 项哈希桶、200 个节点存储、200 项传播栈。
  200 是节点分配上限，不是扩展次数。分配失败后保留已访问的 best node。
- 生产：`do ... while (... && limit++ < 300)` 的循环预算；不能把 300 与原版 200
  当作同一指标，且该 do-while 有边界计数差异。
- 原版 best：优先更小 heuristic，同 heuristic 且 `g > best.g + 5` 也会换 best。
  生产 opt-in partial 只用更小欧氏距离，没有这个同距分支。

普通生产入口要求目标落点可走并精确到达。DiabRun 已有 opt-in partial 入口，
但仍使用生产 A*，不是完整替换为原生算法。这部分近期已经实现，不应重复移植。

新增 [NativePathRuntimeComparisonTest](../core/src/test/java/com/riiablo/map/NativePathRuntimeComparisonTest.java)
第一场景：24×24 地图，起点 `(3,3)`，目标 `(10,10)` 放墙，双方共享 size=2 碰撞查询。
独立 D2AStar 返回非空 best partial、`reachedTarget=false`；普通 `Map.findPath`
返回 false、路径为空。验证的是“结果契约不同”，不是要求普通移动无条件接受半程路径。
原版带单位目标还有周围 ±2 的八点 approach 检查，应与坐标目标分开评估。

### 输出压缩与平滑

原版 A* 输出只去掉同方向中间点、排除起点，并受到 78 项数组限制（点数达到 78 则失败）。
生产保留网格路径后，额外进行地形/动态碰撞感知的视线平滑，可跨过多个转角。
这能改善当前运行时行走观感，但并非同一个原生压缩操作。

因此原生适配必须区分“原始网格链”“原生转向压缩”“本项目额外平滑”；只比最终是否
到达，可能把路径选择和动画抖动差异全部隐藏起来。

## 3. 碰撞：多数近期修正应保留，剩余重点是 size 与 pattern 的边界

### 已经对齐的规则，不再重复移植

- 普通单位 NO_PATH 占位、玩家/佣兵 ground profile、飞行 profile。
- 可开门怪物只忽略门自身的动态对象阻挡，不忽略墙或重叠的非门对象。
- NPC/CanBeInTown/interact/runtime IS_REVIVE 等 presence 分类，不将 MonStats2.revive
  字段直接等同于运行时复活宠物。
- 小单位 presence 的写入中心/查询 cross，大单位写入 cross/查询 3×3；玩家也进入该网格。
- 旋风动态 mask、deadCol=false 的死亡释放、owner/target 忽略、地图与原生 seam 区分。
- A* 候选落点检查完整 footprint，不额外在两个正交侧格重复检查完整 footprint。
  该规则在 `46091be40` 已落到 `MapGraph`，保留它才能避免 River 对角楼梯等场景误断连。

相关历史包括 `338f2fe1d`、`a6b8c443c`、`a7e12c2b4`、`21ee60e81`、`b86dad607`、
`43cafdfb0`、`4cebb8681`、`6974478dc`、`9e1bde77c`。
原交接 `PATHFINDING_HANDOFF.md` 的多项“待实现”现已过时，不能直接当今天缺陷清单。

### 确认尚有区别：size API 不是 pattern API

原生 `D2Common_11281_CollisionPatternFromSize` 正常映射为 `[NONE, SMALL, SMALL, BIG]`。
因此 size=1 与 size=2 的 ordinary presence 都落到 SMALL pattern，而不是分别一点/cross。

目前生产 `Map.isBlockedForPath(..., size, ...)` 的静态查询沿用 size API：
size=1 检查单点、size=2 检查 cross、size=3 检查 3×3。
动态单位网格则使用 presence pattern。size=2 的常用路径已对齐，但 size=1 的两层语义不同。

新增第二个夹具：中心 `(4,4)`、墙 `(5,4)`。生产 size=1 与原生
`checkMaskWithSize(size=1)` 均不阻挡；原生 `SMALL_UNIT_PRESENCE` pattern 会阻挡；
生产 size=2 也会阻挡。它确认 API 层面的差异，不证明所有 size=1 的生产调用者都应改成
cross；仍要追踪实际单位、path type、场景使用的 pattern 后专项接入。

size=0 同样需要单独处理：原生映射 NONE，现动态网格有现存的非空兼容行为。
本轮保留该行为，未宣称完成全部尺寸/生成落点语义的统一。

### 本轮已修复：异常尺寸的原生回退

上游 `47687ceb404f99dae79da70cbb7757d4f5eb2fdf` 明确：size<0 或 size>=4 时直接返回
ordinary SMALL_UNIT_PRESENCE，并且发生在 town/interact 转 PET 之前。
本机 DLL `0x6FDA9250` 分支和 `[0,1,1,2]` 表均已静态核实。

旧网格将 size>=4 视为大单位，且 town 单位仍可被错误分类为 PET、被玩家忽略。
本轮仅统一动态网格写入、查询、诊断和 move/rollback 的 small 回退，并在非法尺寸时
阻止后续 PET 转换。未修改静态地形、生成落点 `isFreeWithSize` 或正常尺寸策略。

三个回归覆盖 `Integer.MIN_VALUE/-1/4/Integer.MAX_VALUE`、移除与回滚、规划/实际移动一致性，
并验证恢复合法 big town pet 后仍被玩家正常忽略。修改前 3/3 失败，修复后通过。

## 4. 目标跟踪：上游状态机更原生，不能只抄一个阈值

上游 `959db291` 修正逐步检查：玩家/怪物目标才做位移超过 5 的分支
（`abs(dx)>5 || abs(dy)>5`）；item/object 等不走这个检查，但仍保留剩余路点、final 目标判断。
本机 DLL `0x6FDACAC2..0x6FDACB22` 已核实类型范围与两个 cmp 5。

生产 `Pathfinder` 按是否有 Velocity 区分动态目标，位移欧氏距离>0.5 即重算，
另有 0.5 秒周期重算；动态阻挡累计 0.25 秒后的恢复也是本项目策略。
**静态目标已经不做周期重算**，不能将该上游修正标为又一次新增移植。

当前做法对 ECS 追踪/恢复有实际用途，但更新频率、目标分类、重算时机不等价。
应一起移植/适配目标类型、path index、final target、flags 和速度状态；直接将 0.5
改成 5 会留下混合状态机，可能降低追踪质量，不能称为“更原版”。

## 5. 提前量、加速度和逐帧移动：需要完整状态后再接

- `9afe3b579`：`sub_6FDAB940` 的提前量应写返回的输出坐标，而非改 path 自身 target。
  本机 DLL `0x6FDABA33/0x6FDABA45` 可见对输出点 `[esi]`、`[esi+2]` 的加法。
- `a145e734`：加速度计数达到 5 后更新 velocity；只有 `velocity > maxVelocity`
  才截断并清 acceleration，恰好相等仍保留。DLL `0x6FDACB90` 的 jle 可验证这一边界。
- 原版定点位置、64 向方向/向量表等在独立 `D2PathStep` 有移植，但通用生产仍以
  float Vector2 和运行时 delta 积分为主，没有完整 acceleration/max/counter/speed 状态。

生产 `Velocity` 已有原生玩家速度换算、怪物 75% 基础乘数、状态/AI速度修正等，
不能说“速度完全没对齐”；但这些标量换算不等于完整原生动态路径步进。
把上游的 > / >= 修正贴到一个 float 向量 `.limit()` 上，或给所有怪物加提前量，
均不能复现对应原版逻辑。本轮未改速度积分器。

原版还按 path type 区分 IDA*、A*、Toward、Circle、Straight、Leap、Knockback、WallFollow
等，不能把一个 A* 替换视为全部寻路完成。D2MOO `Path.cpp::D2Common_10142` 仍有
`BROKEN` 注释，近期本身也持续修错；应当作为高价值证据源，不是无需核验的绝对真值。

`e36bc705` 的目标房间非空 gate 仅对 >=1.13 启用，不移植到 1.10f。
当前生产的地图越界/房间拓扑保护不能仅凭名字相似就认定是该后版本 gate。

## 6. 性能与工程适配：存在结构差别，尚无跑分结论

原版房间局部碰撞网格用 16-bit masks 查询；读取已维护的占位不必每次扫描全体单位。
本项目动态 grid 虽先查占用格，`UnitCollisionGrid.hasOtherUnit` 在占用格仍扫描
`units.entries()` 以处理 owner/ignored target/过滤器；阻挡密集时，一次 pattern 最多约
9 次 O(U) 扫描，索引还有逐次刷新/重建成本。这是可测量的优化方向，不是已证实瓶颈。

二叉堆的 open-list 操作复杂度与原生 sorted list 不同，但原版搜索预算很小；Java 参考实现
又使用集合与对象分配，不等于原生栈数组的运行成本。没有同场景采样，不能宣布谁整体更快。

本项目的计数层、owner 元数据、对象/门 reference、失败回滚和 ECS 生命周期保护有其用途。
不能只复制 set/reset 位操作而丢失重叠单位或重叠对象计数，否则一个实体移走可能清掉另一个。
后续若优化，应考虑每格 owner 索引，同时保留这些语义。

## 7. 建议顺序与本轮边界

1. 已完成：异常 size 的小型 ordinary presence 回退，保留最近碰撞/房间/门优化。
2. 下一步建议（未实施）：利用现有 D2AStar 做只读 shadow 差分，共享不可变碰撞快照，
   不修改权威位置、不消耗生产 RNG；先比较同一碰撞 predicate，再比较原生 pattern 适配，
   避免将碰撞差异误归因于搜索器。
3. 对空地、窄门、对角楼梯、跨房间、被挡坐标目标、移动目标、密集怪群分别记录：
   exact/partial/失败、终点、原始点序/压缩点序、分配/扩展/碰撞查询数、耗时与分配量。
   不把“双方路径不同但都合法”全部归为 bug；原版一致性与当前恢复策略分别验收。
4. 再选择特定 path type 小范围切换，保留普通 exact 调用合同及必要的项目适配；
   随后单独处理目标状态机和定点步进，不同时替换物理积分。

本轮没有切换生产 A*、修改平滑、变更 size=1 静态查询或重算阈值，也没有运行 shadow
探针/性能压测。完整 TCP/GL、132 图巡游、25Hz、多seed×三难度和原版逐帧差分未执行。

## 8. 验证与复验

两项差异特征测试已单独通过。它们记录当前差别，不是要求未来永久保留差别的 parity
契约；将来原生接入时应同步调整/替换，测试类也明确注明这一点。
最终合并回归 **107项/21类全部通过，失败/错误/跳过0**。复验命令和独立报告目录见
[上游移植记录第四批](d2moo-upstream-port-2026-10-15.md#第四批寻路碰撞比较与异常-size-回退)。

测试使用指定的 1.10f `D2_HOME`、`D2_TEST`，报告隔离，未覆盖其他工作流。

## 附：固定参考与本机二进制

- [D2MOO AStar.cpp（固定基线）](https://github.com/ThePhrozenKeep/D2MOO/blob/0c0bbd1e6bef9181a3da1c046523af622a925416/source/D2Common/src/Path/AStar.cpp)
- [D2MOO AStar.h（固定基线）](https://github.com/ThePhrozenKeep/D2MOO/blob/0c0bbd1e6bef9181a3da1c046523af622a925416/source/D2Common/include/Path/AStar.h)
- [D2MOO Path.cpp（固定基线）](https://github.com/ThePhrozenKeep/D2MOO/blob/0c0bbd1e6bef9181a3da1c046523af622a925416/source/D2Common/src/Path/Path.cpp)
- [非法 size 修正](https://github.com/ThePhrozenKeep/D2MOO/commit/47687ceb404f99dae79da70cbb7757d4f5eb2fdf)
- [生产 MapGraph](../core/src/main/java/com/riiablo/map/MapGraph.java)、
  [Pathfinder](../core/src/main/java/com/riiablo/engine/server/Pathfinder.java)、
  [UnitCollisionGrid](../core/src/main/java/com/riiablo/engine/server/UnitCollisionGrid.java)、
  [DynamicUnitCollisionSystem](../core/src/main/java/com/riiablo/engine/server/DynamicUnitCollisionSystem.java)。

安装目录：`G:\BaiduNetdiskDownload\Diablo II 1.10F`。

- D2Common.dll SHA256：`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
  PE32，imagebase `0x6FD40000`；地址是未重定位 VA，不是文件偏移。
- D2Client.dll SHA256：`94466B84C0593AB682F1D3D8DC65EF4F8170F33E430865D802BC752458B2B282`。
  本轮只复核其哈希，不将既有技能分派证据冒充寻路调用链证据。

可复验的 D2Common 片段：`0x6FDA7230..7272`（heuristic）、
`0x6FDA9250..92EE`（非法 size）、`0x6FDD1DE4`（尺寸映射表）、
`0x6FDACAC2..CB24`（目标类型/阈值）、`0x6FDACB79..CBAA`（加速度边界）。
示例，不需要运行游戏：

```powershell
& 'C:\Strawberry\c\bin\objdump.exe' -d -Mintel `
  --start-address=0x6FDA9250 --stop-address=0x6FDA92F0 `
  'G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Common.dll'
```

上述已核验片段支持对应局部结论，不构成整个路径系统逐函数、逐帧等价证明。
