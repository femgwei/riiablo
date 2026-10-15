# D2MOO 寻路迁移续聊交接（2026-10-16）

用户要求保存进度、归档旧聊天并在新聊天继续。第二十一批已在新聊天完成；无需重做第二十/二十一批。

## 第三十一批续接结果（优先于下方历史起点）

- NativePathUnitMetadata捕获真实Monster.monstats的npc/inTown及表行是否已提供，
  ownerCanBeInTown(nativeIsRevive)复用既有原生谓词供Request.State使用；非怪物无此城镇限制。
- 依据固定上游0c0bbd1的MONSTERS_CanBeInTown：必须有MonStats且npc/inTown/ISREVIVE之一成立。
  缺失宿主MonStats视为unknown并拒绝，包括显式ISREVIVE为true时；不伪称原生查表确认无记录。
- ISREVIVE仍由调用方明确提供；宿主resurrected/playerRevive、所有权或MonStats2.revive均不替代该位。
  原生该位也用于部分宠物/佣兵，不能只凭“复活过”推定。没有自动接通运行时unit flags来源。
- 新增4项，完整568项/49类通过，失败/错误/跳过0；core/build/round31-full.log，
  XML `.gradle/path-port/round31-full/core/test-results/pathPortTest`。
- 生产仍Map→AStarPathFinder；raw坐标自动来源、碰撞原始字节、运行期Act/Zone、shared seed仍待。
  无新增DLL算法证据或动态DLL执行。

## 第三十批续接结果（历史）

- NativePathUnitMetadata捕获Objects.IsDoor与Orientation==1两个谓词，依据固定上游#10392及
  UNITS_CheckIfObjectOrientationIs1。Orientation要求uint8域，不把2/255等非零值当1。
  IsDoor来自宿主boolean表字段，仅声称谓词结果，不声称保留原DLL记录字节。
- 新增dynamicUnit/staticUnit组合方法：将已解析元数据与显式原始DynamicPath或静态int32坐标
  组合为D2PathUnits.Unit；对象自动选door分支。静态path+0x64方向仍需显式提供，缺失不补零。
  可用于NativePathUnitBinding.Reader，集成测试覆盖读取/门偏移及删除后拒绝旧观察。
- 新增6项，完整564项/49类通过，失败/错误/跳过0；core/build/round30-full.log，
  XML `.gradle/path-port/round30-full/core/test-results/pathPortTest`。
- 尚无真实raw坐标自动来源；碰撞完整原始字节、town/revive等其余元数据、运行期Act/Zone、shared seed
  仍待。生产仍Map→AStarPathFinder；无新增DLL算法证据或动态DLL执行。

## 第二十九批续接结果（历史）

- NativePathUnitMetadata.read从实际World的Class和Monster.monstats2、Object.base、Missile.missile
  捕获不可变type/sizeX/sizeY；显式映射PLR/MON/OBJ/MIS/ITM到原生0/1/2/3/4，不用宿主枚举序号。
- 依据固定D2MOO origin/master 0c0bbd1的UNITS_GetUnitSizeX/Y (#10336/#10337)：玩家2×2、物品1×1，
  怪物/对象独立X/Y，导弹Size用于两轴；不用粗粒度Size组件，保留对象int32尺寸，不截为byte。
- 缺失宿主表行视为unknown并拒绝，不能等同原生查表确实返回null的零尺寸；WRP混有tile warp与portal，
  不按一个Class猜原生类型。该类仅解析类型/尺寸，尚不是完整Reader，未接入原生移动消费。
- 新增5项；完整558项/49类通过，失败/错误/跳过0。日志`core/build/round29-full.log`，
  XML `.gradle/path-port/round29-full/core/test-results/pathPortTest`。
- 生产仍Map→AStarPathFinder；raw fp16/静态路径方向、door/town等其余元数据、运行期Act/Zone、
  shared owner seed仍待。无新增DLL算法证据或动态DLL执行。

## 第二十八批续接结果（历史）

- NativePathUnitBinding将实际ECS owner/target生命周期token绑定到调用方已解析的原生Unit数据。
  新选择可捕获当前token；保存的选择可显式传入原token，禁止按旧数字ID自动重绑定替代实体。
- observe按操作捕获一次owner/target；D2PathUnits.Observation实现continuation Target，距离/lead/raw坐标
  共用该快照并检查token。操作间移动后需显式重新observe，不是后台自动刷新。
- Request.State对标准Observation校验target快照对象与speed一致；compute在副作用/路径写入前复检
  原观察对象和生命周期。任意legacy自定义Target仍由调用方保证一致性，不伪称已验证其身份。
- 原生数据Reader仍必须提供已核实的size/type/raw fp16或静态int32/path方向；不从Position浮点猜测。
  尚未把该入口安装到ECS原生移动消费，生产仍Map→AStarPathFinder；Act/Zone、元数据、共享seed仍待。
- 新增15项，首轮定向98项/4类通过后补2项边界；完整553项/48类通过，失败/错误/跳过0。
  日志`core/build/round28-full-final.log`，XML `.gradle/path-port/round28-full-final/core/test-results/pathPortTest`。

## 第二十七批续接结果（历史）

- NativePathIdentity在公共EntityFactory实际创建边界分配不可变本地身份token，组件池化重用不复用token。
  Pathfinder安装实体目标时捕获token，续算/动态阻塞恢复前拒绝旧身份，停止旧追踪，不误追同ID新实体。
  坐标命令/换目标/Pathfind.reset正确清理或替换；死亡及原地复活不重建身份。
- 未经工厂管理的旧实体身份为unknown/null，保留原行为；这是host生命周期身份，不是原生unitId、
  类型映射、网络ID或RNG。尚未绑定D2PathUnits/Continuation的live target观察。
- 地图桥导出后freeDrlg/releaseDataTables，不能把已释放Act直接挂到Zone。
  ECS/Zone运行期Act桥、fp16、元数据及共享owner seed仍待，生产仍Map→AStarPathFinder。
- 新增10项测试，真实玩家工厂测试另补身份断言并纳入path回归；定向29项/3类通过。
  完整538项/47类通过，失败/错误/跳过0；日志`core/build/round27-full.log`，
  XML `.gradle/path-port/round27-full/core/test-results/pathPortTest`。

## 第二十六批续接结果（历史）

- 新增D2PathActiveRooms，从真实D2DrlgAct活动房间链按对象身份投影raw subtile bounds、town、
  ppRoomList有效前缀；不再用RoomEx顺序代替live列表。分配压缩/删除尾项换位均由现有Dungeon提供。
- RequestEffects/Dispatch.Effects可绑定该图，要求startRoom为owner房间的同一投影对象；
  在副作用和search入口验证链/邻接/坐标/元数据/网格引用是否失效。旧三参数构造器保留。
- 新增12项，定向59项/3类通过；完整527项/45类通过，失败/错误/跳过0。
  日志`core/build/round26-full.log`，XML `.gradle/path-port/round26-full/core/test-results/pathPortTest`。
- 仅串行game-thread当前状态校验，不是历史版本计数/并发锁，也不冻结碰撞位或RNG。
  核对固定D2MOO分配/删除/邻接/town接口，未新增DLL算法证据或动态DLL执行。
- common层live图适配已可组合请求，但ECS/Zone尚未绑定运行期Act；实体生命周期身份、fp16、
  共享owner seed与移动状态消费仍待。下一轮优先运行期Act/Zone桥或真实实体身份映射。
  生产仍Map→AStarPathFinder，不整体切换。

## 第二十五批续接结果（历史）

- RoomEx新增独立有序路径链接导出，保留跨层level/room键、自身、重复项与null槽。
  NativePathRoomGraph将真实Zone/RoomEx投影成请求局部room快照，共享bounds身份；缺失Zone链接保持null。
- 这是RoomEx诊断适配，不是live D2ActiveRoom.ppRoomList，尚未安装到生产请求或移动。
  原本本层邻接ID过滤自身/跨层，不能作为原生near-list；下一轮优先live active-room生命周期映射。
- NativeRng是另一套算法，不能用作D2Seed；真实实体生命周期身份、fp16坐标和共享owner RNG仍待。
- 新增4项测试；完整回归515项/44类通过，失败/错误/跳过0。
  日志`core/build/round25-full.log`，XML `.gradle/path-port/round25-full/core/test-results/pathPortTest`。
  生产仍Map→AStarPathFinder。

## 第二十四批续接结果（历史）

- small cross mask先解析中心room；缺中心/窄房间返回请求mask，Any返回1。外臂使用中心room hint，
  写入仍从原hint逐点寻房。新增#10122 pattern Any入口，RequestEffects.blocked改用Any。
- cross逐臂短路，矩形先完整扫描本地再短路右/上子树，point补缺mask指针gate。
  固定D2MOO/DLL基线不变；新增15项，定向82项/5类通过；详细证据见上游记录第二十四批。
- 全范围511项/43类通过，失败/错误/跳过0，六项DLL静态oracle执行。
  日志`core/build/round24-full.log`；XML `.gradle/path-port/round24-full/core/test-results/pathPortTest`。
- 下一包优先真实身份/元数据/room图/共享RNG生产映射，再做nDist/索引/续算/类型恢复消费。
  当前grid安全域的矩形/cross边界已有回归，不代表全部碰撞API或房间生命周期动态等价。
  生产仍A*，真实地图与性能/另机验收仍待，不擅自整体切换。

## 第二十三批续接结果（历史）

- 矩形mask/set/reset（SizeXY及big size/pattern）改为DLL左下角寻房、本地→右→上DFS分区，
  每个子区沿当前room的near-list继续，替代逐点root hint路由。缺anchor整子区跳过写入，
  read返回未过滤0x27；部分写入保留，危险端点回绕域明确拒绝。
- 固定D2MOO与DLL基线未变，详细地址/限制见上游记录第二十三批。新增15项，定向71项/5类通过。
- 全范围496项/42类通过，失败/错误/跳过0，六项DLL静态oracle执行。
  日志`core/build/round23-full.log`；XML `.gradle/path-port/round23-full/core/test-results/pathPortTest`。
- 此包未全收口：下一轮优先small cross中心room路由、mask/Any短路和窄房间边界；
  不把矩形部分完成等同整个碰撞原语完成。生产仍A*，其余生产映射/消费/验收工作包仍待。

## 第二十二批续接结果（历史）

- type9/12已接通，全部非空表项有受限分派；0/16仍需room，16需live seed。
- Leap局部终点与slot0别名差异、BackupTurn保留count多pass追加及uint16碰撞候选均按DLL。
  新增15项，定向47项/3类通过；详细证据及首轮旧断言失败见上游记录第二十二批。
- 全范围481项/41类通过，失败/错误/跳过0，六项DLL静态oracle执行。
  日志`core/build/round22-full.log`；XML `.gradle/path-port/round22-full/core/test-results/pathPortTest`。
- 下一包优先矩形碰撞网格/跨房间递归及边界；原七包中的8/11和9/12已完成，其余五包仍待。
  普通生产仍A*，不整体切换，不宣称寻路完成或实机验收。

## 第二十一批续接结果（历史）

- type8/11服务端/客户端击退已移植到D2PathDispatch，Search与直接重载均支持。
  Target新增raw coordinates观察，D2PathUnits提供实现；没有raw观察的自定义target显式拒绝8。
- DLL哈希及D2MOO基线不变，核对B240..B3B0、ABF0..AD01、9E90..9EB1；详细副作用见
  `docs/d2moo-upstream-port-2026-10-15.md`第二十一批。静态核对，不是动态执行DLL。
- 新增13项；定向59项/3类、全范围466项/40类通过，失败/错误/跳过0，六项静态oracle执行。
  日志`core/build/round21-full.log`；XML `.gradle/path-port/round21-full/core/test-results/pathPortTest`。
- 下一批优先type9/12（Leap/BackupTurn）及其DLL证据。下方七包中的type8/11包已完成，
  其余六包仍待；生产仍Map→AStarPathFinder，不整体切换，不宣称寻路完成。
- 本轮保留Spirit AI及其文档/测试等其他聊天修改，只提交本轮路径文件和进度记录。

## 以下为第二十一批开始前的历史交接

## 当前基线

- 工作目录 `F:\3rd_src\riiablo`，PowerShell，分支 master；多人聊天共享同一工作目录。
  交接时 HEAD 为 `c80a934cb`，近期 NPC/Minion 提交属于其他聊天，不回退、不混入专项修改。
  每次开始、提交前重新检查 status 和暂存区，保留其他聊天的未提交修改。
- 第二十批代码 `bd2fdc2dd5ddbf45845a2e633904036914244831`，文档
  `09b9faeb405bbbba6f1cdd9b0b88c672a776fcf6`，均已推送 origin/master。
- 第二十批完成 type16 随机 IDA、live owner RNG 和专用 cutoff。Search 支持
  0/1/2/3/5/6/7/13/15/16；0/16 需房间投影，16 另需真实共享 owner seed。
- 最终寻路/碰撞回归 453 项、39 类，失败/错误/跳过 0；包含六项匹配原 DLL 的静态数据 oracle。
  独立递归 300 组随机查询/路径/最终 seed 对照包含在一项 JUnit 中，不是 DLL 动态执行。
  日志 `core/build/round20-full-final.log`；XML
  `.gradle/path-port/round20-full/core/test-results/pathPortTest`。
- 普通生产仍 `Map → AStarPathFinder`，原生有状态请求链未安装到 ECS 实际移动消费。
  不能宣称寻路完成、完整原生等价或性能优于现有实现。

## 用户已确认的继续方向

下一轮为第二十一批，优先 **type8/11 服务端/客户端击退入口**：

1. 阅读 `D2PathTypes`、`D2PathDispatch`、现有击退 helper/测试，以及进度文档最新章节。
2. 对照 D2MOO `Path` 源码和本机 1.10f D2Common.dll；表地址 type8=`0x6FDAB270`，
   type11=`0x6FDAB240`。先核对实际控制流、状态字段及调用链，不凭名称推测。
3. 在支持域明确的前提下实现、补相关回归，保持其他已优化寻路/碰撞/房间/步进行为。
4. 只提交本轮文件，推送 origin/master 并核验；同步交接和进度记录。

尚未实现第二十一批代码，不要把工作包列表当成已经完成的任务。

## 剩余范围：7 个工作包，不是固定 7 轮

1. type8/11：服务端和客户端击退入口。
2. type9/12：跳跃、后退转向入口。
3. 矩形碰撞网格、跨房间递归与边界核对。
4. 真实实体身份、单位元数据、房间图、共享 RNG 的生产映射。
5. 生产搜索分派及移动消费：nDist、索引推进、耗尽续算、类型恢复。
6. 真实地图综合验收：门、窄道、跨房间、动态单位阻挡、技能移动。
7. 性能对照、受控切换、用户另一台电脑实机验证。

前三包偏移植收尾，后四包偏生产接入/验收。此计数是规划粒度，不是精确工期或完成百分比。
完整飞弹专用请求链另算；type16 表名 MISSILE_STREAM 不代表本轮接通了飞弹 dispatcher。

## 不变的用户要求与证据源

- 代码改完须 git commit 并 push，用户在另一台电脑验证；极小改动可不编译，算法变化跑相关回归。
- 暗黑机制先查 D2MOO；不明确再分析原 DLL，必要时搜 The Phrozen Keep，不能只凭经验。
- D2MOO 仓库 `F:\3rd_src\D2MOO`，前几轮固定读取
  `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，未 fetch；若更新基线须明确记录，
  不 reset 其他工作。源文件优先 `git show origin/master:source/D2Common/src/Path/...`。
- 原版目录 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，含 D2Common.dll/D2Client.dll 和 exe。
  D2Common SHA256=`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
  objdump=`C:\Strawberry\c\bin\objdump.exe`；静态反汇编/表核对不等于动态函数差分。
- 保持近期寻路、碰撞、房间、步进优化；不加 1.13+ target-room gate，不将 ordinary exact 自动转 partial。
- 原生 IDA：跨 contour 保留 cache，unsigned cached>=new 含相等；child 方向从 parent 当前方向起算；
  type16 每次 advance（含耗尽前未使用推进）都 roll live seed，root 设置不 roll，失败/异常不回滚。
  type0 不消费 seed；保留通用 findPath API 旧语义。安全域拒绝 padding>50000/回绕/cache越界，
  不模拟 DLL 内存破坏。生产不应在未经验收时整体切换。
- 文件编辑用 apply_patch；不要主动创建子代理。共享进度文件存在别人的修改时按 hunk 提交，
  不用 `git add .`，不清理其他聊天的暂存或未跟踪文件。

## 复验命令

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round21-full -PpathTestScope=path --console=plain
```

pathRun 必须使用新标签避免与其他聊天构建输出冲突；pathTestScope 只隔离测试源码，主源码仍完整编译。
先运行相关定向测试，再全范围回归；按 XML 统计实际 tests/failures/errors/skipped，不沿用旧数字冒充新结果。
推送输出需脱敏潜在 URL 凭证：

```powershell
git push origin master 2>&1 | ForEach-Object { $_.ToString() -replace 'https://[^/@\s]+@', 'https://[redacted]@' }
```

详细历史见 `docs/current-chat-ownership.md`、`docs/d2moo-upstream-port-2026-10-15.md`、
`docs/path-collision-upstream-comparison-2026-10-15.md` 和 `docs/d2moo-progress-roadmap.md`。
current-chat-ownership 顶部 dark-magic 技能进度是另一工作流，不是寻路完成度。
