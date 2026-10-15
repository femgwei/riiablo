# 1.10f 寻路与碰撞：D2MOO 和当前运行时详细比较

日期：2026-10-15。riiablo 审查起点：`8b687045e`，包含近期地图巡游、碰撞和 DiabRun 修改。
正文1–8节记录第四批审查边界；第五批已接入只读shadow，最新状态见文末后续章节。

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

## 后续第五批：只读 shadow 已接入，尚未切换生产路径

新增 [NativePathShadow](../core/src/main/java/com/riiablo/map/NativePathShadow.java)，
在普通/partial生产A*返回后、门规则及owning-zone作用域恢复前采样。
权威搜索先正常完成，诊断不调用findPath递归、不写入Position/Velocity/path，也不替换输出。
关闭时不创建快照、不执行重放；现有A*、碰撞尺寸、平滑、房间和恢复策略未改。

一次样本分三步：

1. 复制实际输出点值；冻结起终点包围框向外16格的effective collision，每格只读取一次。
   静态地形、门、动态障碍使用既有查询；不再保留live map/predicate给搜索重放。
   目标预检查与候选查询现有mover/target参数合同分别保留。
2. 用独立节点/heap重放**同一个生产AStarPathFinder及邻居代码**，再运行独立D2AStar。
   两者只读取同一个不可变快照；输出用原生同方向压缩方式比较，不执行生产视线平滑。
3. 核对生产重放与刚才真实输出完全一致，才比较runtime/reference的EXACT/PARTIAL/FAILED
   和压缩转向点序；不将不可比较的样本记作算法不一致。

默认关闭。JVM直接启动可用 `-Driiablo.path.shadow=true`，默认每128次请求采样一次，
每个MapGraph最多16次尝试；可分别设置 `.every`、`.maxSamples`。
maxSamples硬限制128；snapshot每边最多128格（最多16384个中心查询，另一次目标预检查）。
启用有额外同步捕获/分配成本，不建议以every=1常开或据此测帧率。

D2GS的JavaExec任务支持显式Gradle项目参数，避免误以为Gradle自身的-D会自动传给游戏JVM：

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat :server:d2gs:headlessMovementIntent `
  -PpathShadow=true -PpathShadowEvery=1 -PpathShadowMaxSamples=4 --console=plain
```

同样参数可用于 `:server:d2gs:run`；日常采样建议省略Every/MaxSamples以使用低频默认值。
采样停止后重启JVM可重新计数。程序内测试可在simulation线程使用
`map.setPathShadow(new NativePathShadow(every, maxSamples, sink))`；传null停用。
Obstacle/sink必须为只读、无副作用回调；诊断不会为任意第三方回调提供事务回滚。

日志前缀 `[NATIVE_PATH_SHADOW]`，显式启用时使用WARN确保默认日志阈值可见。
包含flags/size/owner/target、门规则、exact/partial入口、起终点、实际原始点序、
双方压缩转向点、候选碰撞查询次数、节点数、捕获及重放耗时。
runtimeNodes是本次重放创建的唯一Point2数；referenceNodes是原生参考分配节点数，
不是扩展次数。查询次数不含快照捕获/目标预检查；时间含实现各自的Java对象构造，
没有预热/统计/公平分配采样，**不是benchmark，不用于宣称快几倍**。
独立D2AStar目前不暴露压缩前原始点序，referenceTurns是其原生压缩输出，不伪造原始点序。

状态解释：

- `COMPARED`：生产重放一致、没有越界，differs才有比较意义。true可以只是结果合同/点序差异，
  不等同于游戏bug；false也不证明完整原版状态机等价。
- `SNAPSHOT_LIMIT`：范围太大或整数边界，跳过捕获/重放；计入采样尝试上限。
- `OUTSIDE_SNAPSHOT`：任一搜索访问快照外，说明有限边界可能影响结果，不给parity判定。
- `LIVE_REPLAY_DIFFERENCE`：实际输出与冻结快照重放不一致，不归罪于原生算法。
- `DIAGNOSTIC_ERROR`：捕获/重放/sink的RuntimeException被隔离，权威输出不变；
  VM Error不吞掉。诊断实例有errors计数，异常也占用采样额度，避免无限重试。

本批只比较共享的**当前effective collision**；日志明确 `targetMode=COORDINATE`。
即使targetId存在，也不擅自模拟native targetUnit八点approach或pTargetUnit状态。
size→native pattern的第二套适配、完整pathType dispatch、目标预测/重算/定点步进尚未接入。
具体回归与TCP结果见 [第五批记录](d2moo-upstream-port-2026-10-15.md#第五批只读寻路-shadow-差分)。

## 后续第六批：真实地形与动态坐标请求

结论：本轮未找到支持直接替换普通生产A*的证据。参考端更贴近原版的partial输出合同，
已经在真实地图中体现为“当前失败、参考给出接近路径”；但不能把它说成“原生成功到达”，
更不能跳过目标状态机/房间后置gate来安装这些partial。

新增诊断能力仅限只读：按来源关卡过滤（`-PpathShadowLevel=63`，默认-1全关卡），
记录脱离地图引用的来源/目标关卡、房间和障碍predicate有无，按OUTCOME/ENDPOINT/TURNS/NONE
分类。不可比较统一INCONCLUSIVE。日志标明`stage=MAP_SEARCH_RAW`，不是最终平滑/接受路径。
`dynamicObstacle=true`仅代表回调存在，不表示该样本的差异必由单位阻挡造成。
过滤不消耗其他关卡采样额度，原128次/128格上限和默认关闭均保留。

两组真实1.10F MPQ/TCP主采样，seed12345普通难度25Hz：

- 蛆虫二/三层及古代通道63/64/65任务通过，真实清障10次；连续入口1次、测试staging跳转1次。
  来源63采128次，92次可比较：45次双方EXACT且压缩输出一致、25次双方EXACT仅转向点不同，
  22次双方FAILED且输出一致；36次快照外。AI gate通过，fallbackRecords=0。
- 火焰之河107/108通行子检查通过，但整体任务因148条AI fallback记录失败（errors=0），
  **不能记完整通过**。来源107采109次，103次可比较：38次双方EXACT且压缩输出一致，
  32次双方EXACT仅转向点不同，33次当前FAILED/参考PARTIAL；6次快照外。
- 主统计237次中195次可比较：105输出一致、57仅转向点不同、33输出等级不同；
  42次快照外不用于结论，无诊断错误/live重放不一致。请求包含重复重算，不是独立缺陷计数。
  未出现可比较的FAILED/EXACT样本，也不能凭这点推断未来所有地图皆无此情形。

火焰之河sample1：megademon2、size3、允许开门，(2840,1266)→(2830,1290)，
生产重放FAILED（2408次候选查询），参考分配200节点、末点(2828,1288)，仅PARTIAL。
sample89在同一房间内同样出现FAILED/PARTIAL，不能把全部差异简单归因于跨房间。
这些调用来自当前运行中的AI，其中有GenericMonster fallback；不是原版AI完整场景oracle。

蛆虫mover180追同一玩家117，目标先后为(12572,1556)/(12565,1567)/(12557,1581)/
(12561,1587)，覆盖动态目标的多次坐标请求。shadow仍然只重放COORDINATE模式，
没有伪造native targetUnit八点approach/预测/重算时钟。
合成回归另说明：同为PARTIAL时，runtime可到(94,95)，参考200节点预算只到(44,44)，
目标是被挡(95,95)；所以参考端也并非普遍更靠近目标。

建议下一步先沿1.10f D2MOO/DLL对照部分路径的消费/失败恢复/重算调用链，
再选择原本允许partial的pathType做受控接入，保留普通exact及近期碰撞优化。
本批未改生产路径/位置/速度/RNG，未新增原版动态差分或性能benchmark；
GL/132图/多seed三难度、快照外补证和火焰之河AI fallback仍未完成。

最终133项/22类回归通过，失败/错误/跳过0；代码`033b2e9d9`已推送origin/master。
详细命令、样本端点、日志目录、首次启动/构建失败与共享工作区边界见
[第六批记录](d2moo-upstream-port-2026-10-15.md#第六批真实地图采样与差异分类)。

## 后续第七批：目标身份不能随恢复路径丢失

本轮找到并修复一个局部生产缺陷，不是将普通A*换成原生backend：内部动态恢复未经过
public入口的metadata赋值；stop→绕行→重建组件后targetEntityId变-1，之后不再跟踪单位。
直接恢复也未刷新目标坐标快照/timer。现在统一在核心路径接受后安装目标状态，
保留可达末点与单位身份的独立性；新坐标命令清旧跟踪，后置房间gate仍可拒绝raw path。

依据为固定D2MOO `0c0bbd1e6bef9181a3da1c046523af622a925416`（本轮未fetch）及本机
1.10f D2Common/D2Game局部静态反汇编：

- Path.cpp生成点后，仅无flags0x10且无pTargetUnit时才以末点改target；
  DLL `0x6FDA8D7E..8DB9`可见+58目标单位检查。非空点数不表示exact到达。
- PathMisc.cpp/10236及DLL `0x6FDAC8F0..C98F`：Toward2/13与绕墙15的恢复选择；
  `0x6FDACA5C..CA93`及`0x6FDACAAF..CB2C`支持耗尽但未到final时续算和单位目标位移判断。
  当前timer/阈值不据此改成5格，仍需完整distance/final goal/续算状态模型。
- MonsterMode.cpp及D2Game `0x6FC65680..65767`支持部分type失败转15；
  type15是PathWF绕墙，不是A* fallback。hint101默认TowardFinish13但AI可override，
  不能把coordinate A* shadow视为全体怪物的原生算法。
- 上游10142仍标BROKEN，必须交叉核验；以上不是原版DLL动态执行等价证明。
  DLL完整哈希、地址片段、源码和复验命令见第七批上游记录。

普通exact与DiabRun显式partial保持分离，后者仍保留固定技能goal、不追旧单位；
未放松房间前/后置gate，未改碰撞、平滑、步进或引入1.13+目标房间gate。
修前6项有4失败；新类最终9项全通过，合并153项/25类通过，失败/错误/跳过0。
新类真实ECS恢复/消费配scripted Map/mock动态服务，不是实际地形或原版DLL oracle。

真实1.10F MPQ/TCP巡游：25Hz、seed12345，蛆虫二/三层及古代通道63/64/65，
3图/10击杀/0通行失败，1连续入口+1 staging跳转，AI errors/fallbackRecords均0。
stalled日志0次，未触发本轮恢复分支，仅作非回归冒烟；不是完整AI/GL验收。
shadow128次中83可比较（55 NONE、28 TURNS），45越界；诊断错误/live重放差异0。
无性能提升或原版动态等价结论，火焰之河AI fallback仍未修复/重验。

代码/测试`b0a1f4912`已推送origin/master，远端SHA一致；未混入另一轮COF修改。
下一步是按实际pathType做受控原生接入，而非将普通失败一律升级partial成功。
详细证据及另一电脑复验命令见
[第七批记录](d2moo-upstream-port-2026-10-15.md#第七批部分路径消费核对与恢复目标状态修复)。

## 后续第八批：耗尽路径不等于完成追踪

第七批目标身份恢复后，仍存在末点耗尽时timer未到期便清路径的问题；本轮修复为：
保留有效实体目标且未进入既有攻击/交互范围时，无剩余路点、当前位置已到路点、
与live目标不同取整格，则复用原exact/动态绕行策略续算，失败停止，不自动partial。
到达中间路点/尚未到末点不新增重算；坐标命令及DiabRun不因本条件追单位。
新增DEBUG `[ENTITY_PATH_CONTINUE]`表示请求尝试，不是成功证明。

固定D2MOO`0c0bbd1e`（本轮未fetch）的PathMisc.cpp中，sub_6FD5DB70同样在耗尽且
未到tFinalTargetCoord时调用10236；本机1.10f D2Common重新核验`0x6FDACB0E..CB27`的
索引/点数、final/当前位置比较及恢复调用，哈希与第七批一致。上游10142标BROKEN，
以上仅为局部静态交叉证据；native distance预算、nStepNum/final状态和实际pathType仍未完整接入。
当前采用live目标及现有Map.round/范围规则，不宣称原版状态机动态等价，不替换周期/位移阈值。
房间gate、近期碰撞、平滑、移动和生产A*不变，不引入1.13+门槛。

新增9项中修前4失败，修后该类18项通过；合并162项/25类通过，失败/错误/跳过0。
实际ECS配scripted Map/mock动态服务，直接设置到达末点，非真实逐步碰撞/TCP或原版DLL oracle。
真实25Hz/seed12345三图MPQ/TCP巡游通过：蛆虫二/三层及古代通道63/64/65，
10击杀/0通行失败，1连续入口+1 staging跳转，AI errors/fallbackRecords=0。
续算和stalled日志均0，未触发新分支，仅作非回归冒烟；shadow65可比较、63越界，
无诊断错误/live重放差异，未出现FAILED/EXACT或FAILED/PARTIAL。

代码/测试`473bd6ba5`已推送origin/master且远端SHA一致；详细证据/复验命令见
[第八批记录](d2moo-upstream-port-2026-10-15.md#第八批绕行路点耗尽后的实体目标续算)。
仍无性能/GL/全图/原版动态等价结论，火焰之河AI fallback未修复/重跑。

## 后续第九批：寻路尚未完成，继续补 Toward 内核

按用户“先完成寻路再转其他D2MOO更新”的要求复核：生产仍是Map→AStarPathFinder，
原生库/只读shadow/两处目标恢复修复不等于完整原生路径系统接入，因此不转其他模块。

本轮新增D2Toward原始路点生成，补足之前只有布尔探针的Toward零偏移及旋转分支。
初始射线可裁剪、三候选及180度拒绝、局部预算、重复/源点、末次转向省略均按原分支保留。
结果不可变，原生pattern/mask/room由回调提供；不把“非空”“可移动”“末点等于请求坐标”混为一谈。
要求已准备目标及初始归一化后非零速度，不实现完整targetUnit/flags/distance/final包装器，
不将raw点直接安装生产路径，不替换现有Baal探针；近期碰撞/房间/平滑/步进均未改。

本轮已fetch D2MOO，origin/master仍为`0c0bbd1e`。依据PathMisc.cpp及本机1.10f
D2Common `0x6FDAA720..A87B`、`0x6FDAA9F0..ABE7`的局部静态交叉核验；
不是执行原版DLL，10142的BROKEN边界不变。源码与地址、API约束、复验命令见
[第九批记录](d2moo-upstream-port-2026-10-15.md#第九批toward-原始路点生成内核)。

新增16项路点回归+1项4000输入的零偏移探针对照，合并179项/26类通过，失败/错误/跳过0。
对照只证明既有布尔投影与新内核的相应结果一致，非全点序/状态机/原版动态等价证明。
含Java内存原生碰撞网格pattern/mask测试，本轮没有新增MPQ/TCP/GL、benchmark或全图验收。
代码/测试`74c968123`已推送origin/master、远端SHA一致。

寻路核心生产链仍需prepared target/unit身份、distance/final/索引及flags续算状态，
按实际pathType适配Toward2/13及WallFollow15等，再受控接入并验收真实消费/恢复分支。
这些未完成前不标寻路收口；火焰之河AI fallback仍未修复/重跑，其余验收覆盖保持单列。

## 后续第十批：WallFollow 原版内核与上游增强要区分

新增`D2WallFollow`准备坐标目标后的type15路点生成，补双侧贴墙、原路线回接、闭环、预算
截断和原生直线化；hasTargetUnit仅代表距离下限40，不代替单位目标准备或跟踪状态。
本轮仍不接入生产Map/Pathfinder，不改变近期碰撞、房间、平滑或步进，不将普通exact失败升级partial。

完整读取固定D2MOO`0c0bbd1e`的PathWF.cpp，本轮未fetch。核对本机1.10f D2Common
`0x6FDABAC0..C589`，SHA256与此前一致。结论不是“上游每行都比当前更原版”：
上游空cache距离返回INT_MAX是明确的非原版增强；DLL实际读取cache+0x20的finished槽，
按packed (0或1,0)参与int32平方乘加和有符号比较。本轮安全模拟原始读值，不照搬增强。
具体栈槽、初始化/写值/读取地址和复验命令见
[第十批记录](d2moo-upstream-port-2026-10-15.md#第十批wallfollowtype15-原生绕墙路点内核)。

同时保留任一侧finished即退出、同距选cache2、best/current计数不同、回接更新index后
再自增导致跳过一次碰撞查询的原版细节。最后一项已有阻挡夹具证明：raw可能含未检查的墙点，
因此该参考内核不能被当作可直接安装生产的碰撞安全路径，也不能据此宣称性能或体验更优。
D2PathMisc.simplify已静态核对1.10f `0x6FDAC170`，本轮只更新其旧版本注释，不改实现。

新增20项测试，含单项5000组稳定场的终止/边界/重放检查；合并199项/27类通过，失败/错误/跳过0。
夹具有手工分支预期及Java原生内存碰撞pattern/mask；不是原版DLL动态执行，不是5000项JUnit。
无新增MPQ/TCP/GL、全图/多seed难度或性能验收。代码/测试`9d6ee2754`已推送且远端SHA一致。

**寻路仍未完成**。下一步优先prepared target/unit、distance/final/索引及flags续算模型，再按实际
pathType受控接入Toward2/13、WallFollow15等，并验收真实消费、失败与恢复分支。
普通生产仍Map→AStarPathFinder；火焰之河AI fallback及其余覆盖继续单列，不宣称收口。

## 后续第十一批：搜索目标准备也不能把上游当无条件原版oracle

新增`D2PathTarget`已解析坐标后的阻挡终点修正及门目标偏移。flag0x1000控制目标碰撞查询和修正，
三条候选独立累计、只重算primary；玩家按原9x9表延伸到距离5，门按orientation及source侧±2。
返回search/final候选，不覆盖单位身份或previous快照；canSearch只描述该准备阶段。

固定D2MOO`0c0bbd1e`未fetch，核对Path.cpp/PathMisc.cpp/PathUtil.cpp与本机1.10f D2Common：

- `0x6FDA8C75..8C90`DLL写入selected后比较selected是否source，上游第二次却比较primary槽。
  新夹具将primary阻在source、secondary选到(12,12)，两者返回不同；本轮按DLL实现。
- `0x6FDA907B..90AC`玩家延伸查询坐标保留int32寄存器，只在接受后的WORD存储缩窄，
  不能照抄上游uint16局部坐标的边界查询语义。候选累计阶段本身仍是uint16回绕。
- `0x6FDA90C0..9182`门orientation=1沿Y、其余沿X，source>=door取+2（含相等），否则-2。
  容差2仍需后续单位目标准备包装器设置；本轮helper只返回门偏移坐标。

详细地址、原版DLL哈希和另一电脑复验命令见
[第十一批记录](d2moo-upstream-port-2026-10-15.md#第十一批阻挡终点准备及门目标偏移)。
新增20项、合并219项/28类通过，失败/错误/跳过0；含单项5000输入终止/边界/重放、
内存碰撞pattern/mask及与两个原生点生成库组合，并非生产接入或原版动态等价证明。
代码/测试`d77c85d83`已推送且远端SHA一致，无新MPQ/TCP/GL/全图/性能验收。

**寻路未完成**：仍需单位身份/位置/FOG LUT提前量/容差、distance消费、final/previous/索引/flags
续算状态及实际pathType受控生产接入。本轮不改普通A*、近期碰撞/房间/步进或普通exact合同；
火焰之河AI fallback及其余覆盖继续单列。

## 后续第十二批：续算状态必须与搜索内核分开核对

新增`D2PathContinuation`独立状态层，补耗尽/目标位移决策及10236调度。固定D2MOO
`0c0bbd1e`未fetch，结合本机1.10f D2Common静态核验：

- +0x90 nDist、+0x91 nDistMax、+0x94 nDistance不是同一预算。10236扣的是nDistance，
  按currentPointIdx扣，不是按输出点数或走过的格数；nDist步进尝试消费仍未在本轮接通。
  SetNewDistance先截低字节再cap77；AddToDistance先int32回绕再夹0..255。
- monster零nDistance在扣减前阻止续算；扣到0仍执行本次搜索。flag0x10跳过该门槛及
  refresh/DOUPDATE/消费。正确顺序不能简化成“预算<=0就失败”的统一前置判断。
- type2/13/15直接选2或13、失败直接改15重试；不调用SetType重新套flags/dirOffset，
  不回滚第一次10142的状态副作用。非该族失败不自动转15，也不自动升级普通partial。
- circle耗尽先KEEP；单位停止距离先于采样；静态目标也采样，但只有player/monster的
  previous→sampled任一轴位移严格>5才立即重算。KEEP与搜索非零返回均不等于有可消费路点。

地址、字段与另一电脑复验命令见
[第十二批记录](d2moo-upstream-port-2026-10-15.md#第十二批距离字段与耗尽续算调度)。
新增29项、合并248项/29类通过，失败/错误/跳过0；单项遍历256预算×79索引，不是DLL动态差分。
代码/测试`b09d49571`已推送且远端SHA一致；本轮无新增MPQ/TCP/GL/性能验收。

**寻路仍未完成**：本轮是状态投影，refresh/10142仍为回调；实际单位身份、10399距离、FOG LUT
提前量/种类容差、nDist步进消费、10142结果规范化/flags生命周期和实际pathType生产接入仍待。
不宣称普通生产已切换，仍为Map→AStarPathFinder；近期碰撞/房间/步进优化不变。
火焰之河AI fallback及其余真实覆盖继续单列。

## 后续第十三批：目标采样和停止距离不能用通用几何替代（2026-10-16）

新增`D2PathUnits`稳定Unit快照辅助层，补动态unsigned fp16/静态int32坐标、两种目标采样入口、
FOG提前量、初始种类容差/门偏移和10399尺寸停止距离；可给独立续算层提供lazy观察，尚非生产接入。
固定D2MOO`0c0bbd1e`未fetch，结合本机1.10f D2Common/Fog.dll核验：

- owner +0x68 speed是字节，不是移动velocity；lead角度(8*目标方向-256*speed)&511，
  使用FOG原float表、x87乘法后截零和uint16写入，不按“目标速度×时间”的经验公式推断。
- 初次prepare只有player/monster加lead；续算sample却对任意目标非零speed加lead。
  静态目标须提供path+0x64真实别名字节；不知道时显式拒绝该分支，不静默假设方向0。
  prepare的field_14容差不同于nStepNum，且不代替完整10142更早/更后的请求gate。
- 10399先按尺寸选择8x8短表/长距离分支；长分支先减各sizeX半径之和、逐轴夹0再算min+2*max。
  短表负项直接0，size3减一次、小单位加一次；不能拿无尺寸的PathMisc距离或欧氏距离替换。

新增27项、合并275项/30类通过，失败/错误/跳过0。两项实际读取本机匹配SHA256的原版DLL：
64个FOG位模式/65536组byte lead、7744组尺寸距离静态数据对照；不是DLL动态执行或数万项JUnit。
FOG位模式有细微非理想对称，但本轮未找到在byte输入域改变整数lead的理想sin对照样本，
不将静态位差当作已复现移动bug，也不据此宣称性能/体验更优。
具体VA/哈希、类型边界、条件跳过与另一电脑复验见
[第十三批记录](d2moo-upstream-port-2026-10-15.md#第十三批单位目标坐标fog提前量与10399尺寸距离2026-10-16)。
代码/测试`629176f44`已推送且远端SHA一致，无新增MPQ/TCP/GL/benchmark/原版动态差分。

**寻路仍未完成**：Unit是已解析元数据的稳定快照，生产身份/有效期/表格sizeX仍需适配；
10142其他gate/结果规范化/flags生命周期、nDist步进消费及实际pathType真实移动接入仍待。
普通生产仍Map→AStarPathFinder，近期碰撞/房间/步进不变；不加1.13+ gate或普通自动partial。
火焰之河AI fallback及其余真实覆盖继续单列。

## 后续第十四批：结果非零不等于可移动，room flag以DLL为准（2026-10-16）

新增`D2PathResult`独立结果/初始向量层，组合续算状态，不替换生产A*。固定D2MOO`0c0bbd1e`
未fetch，静态核对本机1.10f D2Common的10142结果、向量归一化、room/facing setter及方向表。

- 零搜索结果只清ACTIVE；精确重复前缀耗尽先清vectors/velocity，再common reject清index/count。
  common reject不代表所有失败：更早目标raw零轴分支直接返回，不能无条件清掉旧路径。
- 只跳过与当前精确fp16中心相同的点，不是同整数格去重。成功先previous=旧target，再按
  flag0x10/targetUnit决定target是否变末点；返回总count，不保证剩余点数量或实际移动。
- vector*velocity保留int32回绕/右移，反向flag只改facing；player/monster只调度新朝向，
  object/item直接改当前朝向。lookup只覆盖已核实的native非溢出域，域外显式拒绝。
- **原DLL优先于上游源码的一处修正**：`0x6FDA8220..8290`先清bit1；有点且room null时
  DLL从8240跳8282置bit1，上游外层if(room)则只清除。此次按DLL。扫描含已消费前缀、忽略
  stale tail，half-open bounds保留int32加法；该flag不拒绝路径，不是1.13+目标房间gate。

新增25项、合并300项/31类通过，失败/错误/跳过0。新静态oracle实际读取匹配哈希的原DLL，
逐项比较128项方向/64项turn表；不是DLL动态执行。真实Toward/WallFollow与结果/续算层组合
也只是库状态夹具，不据此宣称生产消费或性能更优。代码/测试`405e77494`已推送且远端一致。
原版地址/哈希、输入域、条件跳过及另机命令见
[第十四批记录](d2moo-upstream-port-2026-10-15.md#第十四批搜索结果初始向量与房间标记2026-10-16)。

**寻路仍未完成**：10142早期gate/城镇规则、footprint事务/dispatcher、生产身份/元数据适配、
nDist步进消费及实际pathType路径安装/速度/动态碰撞消费仍待。普通生产仍Map→AStarPathFinder，
近期碰撞/房间/步进不变，不加1.13+ gate或普通自动partial。本轮无新MPQ/TCP/GL/性能/
原版动态差分；火焰之河AI fallback未修/未重跑。

## 后续第十五批：请求失败副作用、城镇fallback与占位顺序（2026-10-16）

新增`D2PathRequest`组合Units/Target/Result及续算状态。固定D2MOO`0c0bbd1e`未fetch，
核对本机1.10f D2Common 10142前置/占位流程、46620房间查找与56C0怪物town资格。

- raw单位目标任一轴0先返回，仅清collidedMask、保留旧路径；源点两轴0及准备后的
  零值/重合/逐轴超±100才common reject，后续失败不回滚已写target。坐标单轴0不受raw gate限制。
- **DLL与上游差异**：缺startRoom时DLL `0x6FDA8979..8992`断言，上游outer if仅普通失败；
  本轮显式异常表达，不混入common cleanup。缺targetRoom却不拒绝：普通monster经中点
  （向零除法）再查，fallback room只做town判断，不替换初始搜索room；不能加1.13+ gate。
- CanBeInTown必须有有效MonStats，然后NPC/INTOWN/revived任一成立。占位owner移除返回值
  忽略，target移除受身份/sizeX/flag0x800控制，true才恢复target；之后无条件恢复owner，
  最后才做向量归一化。Java callback异常恢复只是宿主安全增强，不冒充原版异常/断言等价。

请求编排仍不是footprint的shape/mask/死亡模式实现或完整pathType dispatcher。测试包含
内存原生pattern网格移除/恢复与Toward、WallFollow/续算组合，但非真实地形或DLL动态执行。
新增31项、最终合并331项/32类通过，失败/错误/跳过0。首次扩大回归的一项新夹具误将1格
WallFollow预期为有路径；改为实际单位目标13→15场景复验通过，未改内核。
代码/测试/init `40d444c22`已推送origin/master、远端一致；其他聊天修改保留且未纳入本轮提交。
复验脚本、地址及实际测试/交付信息见
[第十五批记录](d2moo-upstream-port-2026-10-15.md#第十五批10142前置检查城镇规则与占位编排2026-10-16)。

**寻路仍未完成**：生产身份/元数据/房间图适配、实际10222/10223、原生dispatcher、nDist消费
及真实pathType安装/速度/动态碰撞消费仍待。普通生产A*及近期碰撞/房间/步进不变，不自动普通
partial。本轮无新MPQ/TCP/GL/性能/原版动态差分，其他AI更新与覆盖单列。

## 后续第十六批：占位移除返回true不等于网格改变（2026-10-16）

新增`D2PathFootprint`，固定D2MOO`0c0bbd1e`未fetch，结合本机1.10f D2Common
10222/10223、10338/10410、10177及Set/ResetSizeXY静态证据；请求层现在可以使用实际
内存网格占位adapter，而非人为配置每个移除/恢复回调。搜索仍abstract，不是完整dispatcher。

- player/monster用pattern，object用矩形，其余用size；Remove对player 0/17、monster 0/12
  及object当前模式无碰撞分别gate，force可覆盖；Add没有这些gate。Remove的true仅表示
  执行分支获准，即使没有room/grid、mask0或不支持size也不等于写入发生。
- object mask按door→vis/missile、非door portal→missile→普通对象优先，门不阻视/不阻弹
  是0x400而非0x800。动态null path mask为0xFFFF，raw32在网格ABI前截uint16，
  不让仅高位非零制造presence。对象模式保留八个raw uint8，非零不局限于1。
- 零尺寸object Add原生构造退化box、Remove则不写；既有Java helper都是no-op，adapter
  显式拒绝该Add域，不虚称等价。复用既有网格原语，未移植/证明矩形逐点路由与原生递归
  box切分、溢出域及病态房间图一致；跨房pattern测试只证明该夹具。
- `RequestEffects`校验稳定request/owner元数据和target快照身份，占位用各自room，查询用
  owner room与request pattern/moveMask。只比bounds，不验证完整房间图/生产身份/表格映射。

新增27项，合并**358项/33类通过，失败/错误/跳过0**，三项已有DLL静态表oracle执行。
包含真实内存网格移除/恢复、Toward搜索、独立target room和宿主异常恢复，不是原DLL动态函数
或生产消费验收。代码/测试`4d4112ef1`已推送origin/master并核验一致，其他聊天修改未混入。
地址、哈希、复验命令与报告见
[第十六批记录](d2moo-upstream-port-2026-10-15.md#第十六批单位占位写入与请求网格适配2026-10-16)。

**寻路仍未完成**：下一步优先原生pathType分派与必要入口副作用，再做生产身份/元数据/
房间图映射、nDist与真实移动消费/安装及地形验收；矩形递归网格边界单列。普通生产仍A*，
近期碰撞/房间/步进保持，不加1.13+ gate或普通自动partial。本轮无新MPQ/TCP/GL/性能/
原版动态差分，不据此声称整体性能或完整原生等价更优。

## 后续第十七批：纯路点内核不等于有状态原生入口（2026-10-16）

新增`D2PathTypes`与`D2PathDispatch`，固定D2MOO`0c0bbd1e`未fetch，结合本机1.10f D2Common
10185/10186、三张18项表、Toward/ABF0/向量及WallFollow入口静态证据。

- 10185保存条件读旧flags，再替换类型flags与offset；player SetType(2)写前断言，saved
  knockback写后断言且不回滚。10186先恢复velocity，再按player/0x2000决定恢复type。
  10236直接赋type的既有行为不改成SetType，不能把两种入口混用。
- 支持2/3/5/6/13/15快照分派，不猜owner类型，也不重套live offset。Toward零偏移会先写
  slot0、在count0时归一化初始向量，再做一次ray；不能直接调用纯compute省掉这段状态写入。
  零velocity跳ray但仍可生成局部点，非零offset绕开初始probe；type15用live nDistMax/身份
  min40，写buffer并返回count，由外层赋live count。
- 完整表项只是元数据。0/1/7/8/9/11/12/16在新dispatcher中尚未接通，4/10/14/17空项；
  显式拒绝，不用统一coordinate A*或普通partial兜底。具体Effects组合请求与真实内存占位，
  但没有生产ECS安装。纯内核最终前缀写入不声称宿主回调重入/中途异常时与native逐指令等价。

新增29项，最终合并**387项/35类通过，失败/错误/跳过0**；含请求/网格/2→15失败续算组合。
新增一项读取本机原DLL三表54 DWORD的静态oracle，与此前三项共四项执行；不是DLL动态函数差分。
代码/测试`2cb53ea37`已推送origin/master且远端一致。地址、边界、日志与另机命令见
[第十七批记录](d2moo-upstream-port-2026-10-15.md#第十七批类型生命周期与有状态toward分派2026-10-16)。

**寻路仍未完成**：下一步优先Straight7/A*/IDA入口，其余类型、生产映射/nDist/移动消费安装、
矩形递归网格与真实地形验收仍待。普通生产仍A*，近期优化保持，不加1.13+ gate或普通自动partial。
本轮无新MPQ/TCP/GL/性能/原版动态差分，不据此宣称整体更优或完整原生等价。
