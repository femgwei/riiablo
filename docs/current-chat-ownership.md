# 当前 Chat 维护状态

更新时间：2026-10-16（当前状态索引；下方保留历史交接记录）

> 当前技能移植基线：非视觉核对清单 **100%**，严格总进度约 **97%**。dark-magic 的七职业
> exact-ID、行为 gate、测试模式、覆盖审计及 1.10F `d2client.dll` 静态分派证据均已收口；
> 剩余只是真实 1.10f 客户端窗口的 10 项 Assassin exact-ID 逐帧视觉验收（当前 **0/10**）。
> 接手者应先阅读 [`dark-magic-nonvisual-task-list.md`](dark-magic-nonvisual-task-list.md)、
> [`dark-magic-visual-validation-checklist.md`](dark-magic-visual-validation-checklist.md) 和
> [`dark-magic-visual-capture-plan.tsv`](dark-magic-visual-capture-plan.tsv)，不要把旧章节中的
> 阶段性“下一步”或单套调试素材当作当前未完成的非视觉代码任务。

## 2026-10-16 第十九批：确定性IDA type0入口与跨轮缓存

- `D2IDAStar.findNativePath`新增独立原生模式，旧通用findPath行为保持；type0现在通过带room的
  `D2PathDispatch.compute(Search,...)`接通，裸坐标重载仍显式拒绝0，不能猜target room。
  正常Search支持0/1/2/3/5/6/7/13/15，type16尚无单位RNG投影，不用确定性IDA冒充。
- 原生模式跨cutoff轮保留坐标缓存、接受unsigned cached>=new（含相等），子节点方向从父节点
  当前方向重新起算；根耗尽前会前移到相邻行首表项，按连续8×8表读取。h/g/f按signed16，
  邻居坐标uint16；type0上限max(signed root h, uint8初始分数)，cutoff每轮按int32加5。
- room wrapper按原版方向端点构造，不偷换为标准union；相同尺寸轴才±10，Y最大值确为Y+height。
  raw容差倍增后截byte；不改变持久nStepNum。仅写有效prefix并返回count，外层恢复占位后归一化。
  只支持有序非回绕room域和加padding后<=50000缓存；原版内存破坏域显式拒绝，无自动fallback。
- 固定D2MOO`0c0bbd1e`未fetch，本机1.10f DLL哈希未变；核对`7970..821B`静态证据。
  新增24项（15内核/9分派）；定向73项通过后再补1项续算，最终**434项/38类通过，失败/错误/
  跳过0**。新增IDA表静态oracle，五项原DLL静态数据oracle执行；独立递归200组逐query/终点/
  contour/节点数/flush比较在一项JUnit内，不是DLL动态函数执行。
- 代码/测试`a5a25972d994e238b4e33817a9e582a8be62f459`已推送origin/master且远端一致。
  日志`core/build/round19-full.log`，XML `.gradle/path-port/round19-full/core/test-results/pathPortTest`；
  沿用可选pathTestScope隔离测试源码，主源码完整编译。其他聊天DeathMaul/门修改与提交保留，
  未混入本轮四文件代码提交；四交接文档另行提交。
- **寻路仍未完成**：下一轮优先16的单位RNG/专用cutoff，再补8/9/11/12。生产身份/表格/room
  映射、nDist/移动消费安装、矩形递归网格及地形验收仍待；生产仍Map→AStarPathFinder。
  不改近期优化，不加1.13+ gate或普通自动partial；无新地形/MPQ/TCP/GL/性能/DLL动态函数验收。

## 2026-10-16 第十八批：原生A*与Straight入口（历史）

- `D2PathDispatch`新增1/7，现支持1/2/3/5/6/7/13/15。Straight先Toward；末点在容差内且
  不等于start才直接接受，否则原始起终点平方距离<=324时试A*。A*失败且第一次Toward非零才
  重新运行Toward；不是恢复旧buffer，也不要求A*返回精确到达。平方运算保留signed int32回绕。
- 新增`D2AStar.findNativePath`，节点坐标uint16、h/g/f赋值signed int16；保留旧findPath通用
  int坐标API及生产shadow比较。单位目标approach查询仍int32。A*只写有效prefix，不赋live
  index/count/vectors；Straight可能返回与live Toward count不同的数，外层Request才接回并归一化。
- 固定D2MOO`0c0bbd1e`未fetch，复核本机1.10f D2Common哈希未变；静态核对`B130..B1D9`、
  `69E0..7965`相关入口/节点/排序/flush，非DLL动态执行。新增23项，定向62项通过；最终
  **410项/36类通过，失败/错误/跳过0**，四项原DLL静态数据oracle执行。1,000组普通输入逐query/
  路点比较包含在一项测试中，不另计1,000项JUnit。日志`core/build/round18-full.log`，XML
  `.gradle/path-port/round18-full/core/test-results/pathPortTest`。
- 代码/测试/init `26952a9d94f449d9241e1d47870c20edd40cf592`已推送origin/master并核验远端一致。
  init新增可选`-PpathTestScope=path`隔离测试源码，默认不变；完整主源码仍编译。其他聊天物品/
  门/DeathMaul改动不混入本轮提交或专项验收，四交接文档另行提交推送。
- **寻路仍未完成**：0/16 IDA、8/9/11/12入口与生产映射/nDist/移动消费安装、矩形递归网格及
  地形验收仍待。下一轮优先IDA：DLL `7F34..7FD6`接受unsigned cached>=new（包括相等），
  不是上游源码的new>=cached；现Java仅new<cached也漏相等。已追踪跳转落点纠正初读，不能把
  `jae`当作跳过。尚未改IDA代码，另需入口容差倍增、room边界、type16 RNG/截止条件对照。
  普通生产仍Map→AStarPathFinder；不改近期优化，不加1.13+ gate或普通自动partial。
  本轮无新MPQ/TCP/GL/性能或原版DLL动态函数差分，不宣称寻路整体已收口。

## 2026-10-16 第十七批：类型生命周期与有状态Toward分派（历史）

- 新增`D2PathTypes`，补10185/10186及三张18项表，保存previousPathType/previousVelocity，
  按旧flags判断保存门槛；player SetType(2)写前断言、saved knockback写后断言的副作用不混用。
  10236仍直接写type，不改成调用SetType。越界type显式拒绝，不模拟越界原生内存。
- 新增`D2PathDispatch`接通2/3/5/6/13/15；快照type分派，live dirOffset不重套类型表。
  Toward零偏移先清index/count、写临时slot0，再允许count0初始归一化与单次射线；零velocity
  跳过ray但仍可生成局部点。非零offset不做该探测；type15读live nDistMax，只写buffer/返回count。
- 固定D2MOO`0c0bbd1e`未fetch，核对本机1.10f D2Common `A0E0..A23A`、`A9F0..AD01`、
  `B0B0..B0BE`、`C790..C8ED`、`C270`与`8CCB..8D0E`；原DLL哈希未变。
  `D2PathDispatch.Effects`可直接组合请求与footprint，不安装生产ECS backend。
- 新增29项；最终合并**387项/35类通过，失败/错误/跳过0**，含新增原版三表静态oracle，
  共四项原DLL静态数据oracle执行。此前67项定向/384项首轮合并亦通过，补3项边界后最终全范围复跑。
  XML `.gradle/path-port/round17-full/core/test-results/pathPortTest`，日志`core/build/round17-full-final.log`。
  代码/测试`2cb53ea37d3f2d53b887133f07184572665edfc3`已推送origin/master且远端一致；
  四文档另行提交。其他聊天物品/商人及DeathMaul测试保留，未纳入本轮提交或专项验收。
- **寻路仍未完成**：生产仍Map→AStarPathFinder；0/1/7/8/9/11/12/16有表项但本dispatcher
  尚不支持，4/10/14/17为空表项，不能统一回退A*。下一轮优先Straight7及A*/IDA必要入口，
  再补其他类型、生产映射、nDist与移动消费/安装；矩形递归网格及地形验收仍待。
  不改近期碰撞/房间/步进，不加1.13+ gate或普通自动partial；无新MPQ/TCP/GL/性能/DLL动态差分。

## 2026-10-16 第十六批：单位占位写入与请求网格适配（历史）

- 新增`D2PathFootprint`：10222/10223按类型选择pattern/矩形/size，保留player与monster
  不同死亡模式、object当前模式碰撞资格、force和返回值语义；Add不套Remove资格门槛。
  补10338碰撞mask优先级与10410资格，动态null path的mask为0xFFFF，网格ABI先截低16位。
- 固定D2MOO`0c0bbd1e`未fetch，结合本机1.10f D2Common `0x6FDA8320..85FB`、
  `0x6FDBDE10..DEB0`、`0x6FDBFB40..FB64`、`0x6FDA9FC0..9FD5`静态证据。
  `RequestEffects`连接上一批请求层与真实内存网格；owner/target使用各自room，搜索仍abstract。
- 复用既有`D2CommonCollision`写入原语，本轮没有移植/证明其原生递归矩形切分等价。
  object零宽/零高Add显式拒绝（native退化box不等于Java no-op），Remove仍可true且无写入。
  对象模式仅0..7；元数据、房间图一致性和快照稳定性由调用方保证，不解析生产实体/表格。
- 新增27项，合并**358项/33类通过，失败/错误/跳过0**；含实际网格移除/恢复、Toward搜索、
  独立target room与宿主异常恢复。三项已有原DLL静态表oracle执行，但不是DLL动态函数差分。
  XML `.gradle/path-port/round16-full/core/test-results/pathPortTest`，日志`core/build/round16-full.log`。
  代码/测试`4d4112ef11e7c98d7aee3fa6438c914532cc56d9`已推送origin/master并核验远端一致；
  四文档独立提交推送。其他聊天NPC/商人等修改保留、未纳入本轮提交或专项验收。
- **寻路仍未完成**：普通生产仍Map→AStarPathFinder。下一轮优先原生pathType分派及必要
  入口状态副作用；生产身份/元数据/房间图映射、nDist消费、路径安装/速度/动态碰撞消费及
  真实地形验收仍待，矩形递归网格边界另需核对。不改近期碰撞/房间/步进，不加1.13+ gate
  或普通自动partial；本轮无新MPQ/TCP/GL/性能/原版动态差分，其他AI进度与覆盖单列。

## 2026-10-16 第十五批：10142前置检查、城镇规则与占位事务编排（历史）

- 新增`D2PathRequest`独立请求编排层，组合Units→Target→Result与续算层；补collidedMask清零、
  raw目标零轴早退、源点/目标零值/重合/±100窗口、房间查找及怪物城镇规则。
  target坐标更新可能先于后续失败；早退、common reject和起始房间断言不混用清理。
- 固定D2MOO`0c0bbd1e`未fetch，结合本机1.10f D2Common `86F1..8A75`、`8CAA..8E09`、
  `46620..466B7`与`56C0..5740`。DLL缺startRoom触发断言，不照搬上游outer if的普通失败；
  缺targetRoom不直接拒绝，普通怪物经中点再查，fallback不改搜索原始targetRoom。
- owner移除返回值忽略；target须有身份、sizeX非零及flag0x800才移除，仅返回true才恢复target。
  搜索后先恢复target、再无条件恢复owner、最后向量归一化。Java异常finally恢复只是宿主安全增强，
  非原版异常等价/状态回滚。实际footprint写入、身份/元数据/房间图生产适配及搜索dispatcher仍为边界。
- 新增31项、最终合并331项/32类通过，失败/错误/跳过0；含内存pattern网格占位移除/恢复及
  真实Toward/WallFollow库组合，非生产消费或原版DLL动态执行。首次扩大回归一项续算夹具误用
  <=2格WallFollow，换成实际单位目标13→15场景后通过，未修改内核；详见第十五批上游记录。
  代码/测试/init `40d444c22683426922e90fd94cc0216a333899d9`已推送origin/master且远端SHA一致。
  独立脚本`docs/path-port-regression.init.gradle`隔离编译及报告，避免共享core/build并行清理；
  XML `.gradle/path-port/round15-full/core/test-results/pathPortTest`。不提交其他聊天AI/交互修改，四文档另行提交。
- **寻路仍未完成**：普通生产仍Map→AStarPathFinder。下一轮优先真实类型dispatcher及原生
  footprint适配，随后nDist步进消费、路径安装/速度/动态碰撞消费与真实地形验收。
  不改近期碰撞/房间/步进，不加1.13+ gate或普通自动partial；本轮无新MPQ/TCP/GL/性能/
  原版动态差分。火焰之河AI fallback是否被其他聊天处理须单独查其报告，本轮未修/未重跑。

## 2026-10-16 第十四批：搜索结果、初始向量与房间标记（历史）

- 新增`D2PathResult`独立状态层，补非missile 10142结果阶段、精确重复前缀跳过、初始方向/
  速度向量、facing调度及room bit1。与续算层共享index/count/target/final/previous/flags，
  但不是完整请求gate、碰撞footprint事务、dispatcher或生产路径安装；普通生产仍Map→AStarPathFinder。
- 零搜索结果仅清ACTIVE；全重复耗尽则先清vectors/velocity，再common reject清index/count。
  成功先保存previous，再按flag0x10/targetUnit决定是否改target；返回总count，不保证移动。
  固定78槽保留stale tail；只跳过精确fp16中心相同点，不按同一整数格去重。
- 固定D2MOO`0c0bbd1e`未fetch，核对本机1.10f D2Common `8CB6..8E09`、`8220..8290`、
  `C790..C8ED`、`9720..9762`。DLL对非空路径且null room置bit1，上游只清除；本轮按DLL，
  不是新增目标房间gate。direction lookup只支持已核对的native非溢出域，域外显式拒绝。
- 新增25项通过；连同Units定向52项/2类，合并300项/31类通过，失败/错误/跳过0。
  新静态oracle读取匹配哈希的原版128项方向表/64项turn表，不是DLL动态执行；无匹配原文件会显式跳过。
  XML：`core/build/test-results/upstreamRound4-round14-full`。代码/测试
  `405e77494123dbbf91267c8a84280f5380dd31f2`已推送origin/master并核验远端一致；四文档另行提交推送。
  仅提交本轮三个代码/测试文件，共享工作区对象交互/COF/服务端等其他聊天修改保留、未纳入。
- **寻路仍未完成**：10142早期gate/城镇规则、footprint事务/dispatcher、生产身份/元数据适配、
  nDist步进消费及真实pathType安装/速度/动态碰撞消费仍待。下一轮优先补请求包装器与事务顺序。
  不撤回近期碰撞/房间/步进优化，不加1.13+ gate或普通自动partial；本轮无新MPQ/TCP/GL/
  性能/原版动态差分，火焰之河AI fallback未修/未重跑。地址/边界/另机命令见第十四批上游记录。

## 2026-10-16 第十三批：单位坐标、FOG提前量与停止距离（历史）

- 新增`D2PathUnits`独立单位快照辅助层：静态int32/动态unsigned fp16坐标解析、空dynamicPath
  坐标/方向零值、PathUtil两种目标采样入口、初始单位目标种类容差/门偏移，以及10399尺寸距离。
  `observation`可组合上一批续算层，保持先距离停止、后lazy采样，不安装生产路径。
- FOG采用本机1.10f表的stride8共64个float位模式；角度为(8*目标方向-256*owner speed)&511，
  speed是+0x68字节，不是velocity。x87乘法/截零与uint16写入保留；prepare仅player/monster
  加提前量，sample则对任意目标非零speed加提前量。静态目标需显式提供其path+0x64别名字节，
  不知道该字节时拒绝执行该lead，不擅自补方向0。prepare的tolerance也不等于持久nStepNum。
- 固定D2MOO`0c0bbd1e`未fetch，核对本机D2Common `B890..BA4C`、`87AF..885C`、
  `2CB0..2E32`及Fog的10083/10084、原表数据。具体VA/哈希/边界见第十三批上游记录。
- 新增27项定向通过，合并275项/30类通过，失败/错误/跳过0。其中两项实际读取本机原版DLL：
  比较FOG 64位模式/65536组lead、10399表与7744组尺寸距离，均属静态数据oracle而非DLL动态执行。
  未配置匹配原版D2_HOME的机器会显式跳过这两项，本机两项均执行，无跳过。
- 代码/测试`629176f444f1b3bf83d5c4b88ad6cc4302abad2a`已推送origin/master且远端SHA一致；
  四文档另行提交推送。只提交两个本轮新文件；另一聊天NPC/Inferno已独立提交，
  Megademon代码/测试/报告等新文件保留，未修改或混入本轮提交。
- **寻路仍未完成**：新Unit只是已解析sizeX等数据的稳定快照，不负责entity-ID/有效期或表格元数据。
  10142早期/后期gate、结果规范化/flags生命周期、nDist步进消费及实际pathType受控生产接入仍待。
  普通生产仍Map→AStarPathFinder，近期碰撞/房间/步进不变；不加1.13+ gate或普通自动partial。
  本轮无新MPQ/TCP/GL/benchmark/原版动态差分；火焰之河AI fallback未修/未重跑。

## 2026-10-15 第十二批：距离字段隔离与耗尽续算状态（历史）

- 新增`D2PathContinuation`，以独立可变状态投影移植10236调度及10226中内联的续算决策。
  包括circle耗尽优先、单位停止距离/采样顺序、player/monster目标位移严格>5、coordinate/unit
  两类续算；KEEP或搜索非零返回都不能代替后续index<count及完整移动tick门槛。
- 明确分离native +0x90 nDist、+0x91 nDistMax、+0x93 nStepNum与+0x94 nDistance。
  10236只扣nDistance；怪物零预算先返回，扣到0仍执行本次搜索，flag0x10跳过此门槛/刷新/扣减。
  type2/13/15直接赋值、不重新套type flags/dirOffset，第一次失败的状态写入保留给type15重试。
- 固定D2MOO`0c0bbd1e`未fetch，结合本机1.10f D2Common `C8F0..C98F`、`CA41..CB2C`及
  `A250/A280/A4E0/A530`静态证据。SetNewDistance先截低字节再限制77；AddToDistance先int32
  回绕再夹0..255，不擅自换成long饱和。DLL哈希与此前一致，详见第十二批上游记录。
- 新增29项定向通过，合并248项/29类通过，失败/错误/跳过0；其中一项遍历256预算×79索引，
  不是20224项JUnit或DLL动态差分。XML：`core/build/test-results/upstreamRound4-round12-full`。
  代码/测试`b09d4957179e7dc51779cfad800d3ea114f3d6f8`已推送origin/master且远端SHA一致。
  四文档另行提交推送；另一聊天Inferno/AnimStepper/Assassin修改及其报告保留、未纳入本轮提交。
- **寻路仍未完成**：这只是独立状态层，refresh与10142搜索仍为回调；目标身份/原生10399距离、
  FOG LUT提前量/种类容差尚由适配层提供，不能用任意几何距离或live坐标冒充。nDist步进消费、
  10142结果规范化/flags生命周期、实际pathType与真实移动接入仍待。普通生产仍Map→AStarPathFinder。
  不改近期碰撞/房间/步进，不加1.13+ gate或普通自动partial；本轮未新跑MPQ/TCP/GL/性能/
  原版动态差分，火焰之河AI fallback未修/未重跑。

## 2026-10-15 第十一批：阻挡终点修正与门目标偏移（历史）

- 新增`D2PathTarget`：已解析目标坐标后的flag0x1000阻挡终点修正，三条独立累计候选、
  仅重算主方向、源点失败语义、玩家9x9表延伸、门orientation/源点侧±2偏移。
  返回search/final候选，不覆盖单位目标/previous快照；canSearch仅表示该准备阶段允许搜索。
- 固定D2MOO`0c0bbd1e`未fetch，读取Path.cpp/PathMisc.cpp/PathUtil.cpp相关实现并静态核验本机
  1.10f D2Common。DLL的选中点源点判定不同于上游第二次primary槽比较；玩家延伸保留int32
  查询寄存器、仅接受后的坐标写入缩窄为uint16。上述两处按DLL实现，有分支夹具。
- 新增20项测试，最终定向20项与合并219项/28类通过，失败/错误/跳过0；含单项5000输入
  终止/边界/重放检查、内存原生碰撞pattern/mask及与Toward/WallFollow库组合，不是生产接入。
  首轮一项夹具方向假设错误，纠正源坐标后复验通过；详见上游记录第十一批。
- 代码/测试`d77c85d8330c69c3d406952e83a8af479bbf95a7`已推送origin/master且远端SHA一致。
  四文档另行提交推送。本轮只提交两个新文件，另一轮FrozenHorror/Inferno提交`fc973fc50`保持独立。
- **寻路未完成**：本轮未实现单位身份解析、FOG LUT提前量/单位种类容差、distance消费、
  final/索引/flags续算状态或生产backend。下一步继续这些包装器门槛，再按实际pathType受控接入。
  普通生产仍Map→AStarPathFinder；不改变近期碰撞/房间/步进，不加1.13+房间gate或普通自动partial。
  本轮无新MPQ/TCP/GL/性能/原版动态差分验收；火焰之河AI fallback未修/未重跑。

## 2026-10-15 第十批：WallFollow/type15 路点内核；尚未接入生产（历史）

- 按用户要求继续未完成的寻路。本轮新增`D2WallFollow`，移植准备好坐标目标后的type15
  双侧绕墙、回接原路线、闭环检测、预算截断和原生直线化；不是普通A*替换或完整动态路径包装器。
  `hasTargetUnit`仅表达原生距离下限40规则，不解析/跟踪单位。碰撞pattern/mask/room由调用方提供。
- 固定D2MOO`0c0bbd1e`，本轮未fetch；读取完整PathWF.cpp，结合本机1.10f D2Common
  `0x6FDABAC0..C589`静态核对。空cache距离显式模拟前一槽packed finished，即(0或1,0)，
  不照搬上游非原版INT_MAX保护；保留int32距离回绕、任一侧完成即退出、同距选第二侧、
  返回current而非best计数、回接后跳过一次碰撞查询等细节。结果不保证所有输出段均无碰撞。
- 新增20项测试通过，含1项5000组固定输入的终止/边界/重放检查；合并199项/27类通过，
  失败/错误/跳过0。含内存原生碰撞网格pattern/mask，但非原版DLL动态oracle或5000项JUnit。
  XML：`core/build/test-results/upstreamRound4-round10-full`；本轮未跑新MPQ/TCP/GL或性能测试。
- 代码/测试`9d6ee2754952c0a215b616ee3aac70dc4da8514d`已推送origin/master、远端SHA一致。
  四文档独立提交推送；复验命令、地址和边界见上游记录第十批。另一轮Inferno/AnimStepper/
  FrozenHorror/AI arena修改保持独立，未暂存、修改或纳入本轮提交与专项验收。
- **寻路仍未完成**：普通生产仍Map→AStarPathFinder。下一步优先补prepared target/unit身份、
  distance/final goal/索引及flags续算模型，再按实际type受控接入Toward2/13、WallFollow15等，
  验收真实地形/动态碰撞的消费、失败、恢复与续算。保留近期碰撞/房间/平滑/步进优化，
  不加1.13+目标房间gate，不把普通exact失败升级partial；火焰之河AI fallback未修/未重跑。

## 2026-10-15 第九批：Toward 路点内核；寻路仍未完成（历史）

- 用户要求先完成寻路再转其他D2MOO更新。核对结果：**寻路未完成**，继续寻路，不转其他模块。
  普通生产入口仍为Map→AStarPathFinder；库中的原生算法及只读shadow不等于运行时已切换。
- 新增`D2MOO_JAVA/.../path/D2Toward.java`：准备好坐标目标、初始归一化后速度仍非零时，
  生成PATH_Toward及非零方向偏移分支的raw路点；射线裁剪、三候选、反转拒绝、局部预算、
  重复/源点及最后转向省略均保留。碰撞pattern/mask/room由只读回调提供。
  不安装生产路径，不替换Baal现有布尔探针，不新增RNG或修改近期碰撞/房间/步进逻辑。
- 16项新路点回归+1项4000组固定输入对照；最终179项/26类通过，失败/错误/跳过0。
  对照只证明与现有零偏移布尔投影一致，非4000项JUnit或原版DLL动态等价证明；
  包含内存原生碰撞网格pattern/mask测试，未新增真实MPQ/TCP/GL验收。
- 本轮已fetch D2MOO，origin/master仍为`0c0bbd1e6bef9181a3da1c046523af622a925416`；
  结合本机1.10f D2Common的`0x6FDAA720`、`0x6FDAA9F0`局部静态证据。
  代码/测试`74c968123852915169b668838146c3f793cc09e9`已推送origin/master、远端SHA一致。
  复验命令、原版边界和报告见上游记录第九批；四文档另行提交推送，最终SHA见git log。
  文档收尾时另一轮新增的`tools/verify-indexed-opacity.py`保留，未暂存/提交，不纳入本轮验收。
- 寻路核心生产链剩余门槛（完成前不标收口）：
  1. 补齐prepared target/unit身份、distance budget、final goal、路点索引/续算状态及必要flags。
  2. 按实际pathType适配Toward2/13、WallFollow15等，而非统一套coordinate A*或升级普通partial。
  3. 受控生产接入并验收实际地形/动态碰撞下的消费、失败、恢复和续算，继续保留1.10f房间规则。
  全图/GL/多seed三难度、性能和原版动态差分仍应分别标明覆盖，不从单次冒烟推断完成。
  火焰之河AI fallback也是未完成项，本轮未修/未重跑。

## 2026-10-15 第八批：绕行末点耗尽后的单位目标续算（历史）

- 代码/测试 `473bd6ba52d17fc8372c328f55f2f0da740d472f` 已推送origin/master，远端SHA一致。
  第七批保留目标身份后，仍有“到达绕行末点、未进目标范围、timer未到期便清路径”的缺口；
  现在耗尽时向仍有效的实体目标续算，不把可达末点视为已完成追踪。
- 仅在既有攻击/交互范围检查之后、路点确已耗尽且当前位置与live目标不在同一取整格时触发，
  复用原exact/动态绕行策略；失败停止，不升级partial。坐标走路/DiabRun无实体目标，不新增续算。
  房间gate、碰撞、平滑、Running/速度加成、周期/位移阈值均保留，未接入原生搜索backend。
- 新增9项，修前4项失败/5项通过（旧9项亦通过）；修后该类18项通过，合并162项/25类通过，
  失败/错误/跳过0。XML：`core/build/test-results/upstreamRound4-round8-full`。
  使用实际ECS处理与scripted Map/mock动态服务，含失败确实发起查询、攻击范围、静态交互、
  中间路点/未耗尽、坐标命令和DiabRun边界；非真实地形或原版DLL动态oracle。
- 真实MPQ/TCP：seed12345普通难度25Hz，蛆虫二/三层及古代通道63/64/65，
  3图/10击杀/0通行失败，1连续入口+1 staging跳转；AI errors/fallbackRecords=0。
  ENTITY_PATH_CONTINUE及MONSTER_PATH_STALLED日志均0次，未在TCP触发新分支，仅非回归冒烟。
  shadow128次：65可比较（55 NONE、10 TURNS），63越界；无诊断错误/live重放差异，6114已释放。
- 固定D2MOO `0c0bbd1e`，本轮未fetch；重新核对PathMisc.cpp及本机D2Common.dll
  `0x6FDACB0E..CB27`的耗尽/final目标/10236调用，与第七批证据衔接。
  本轮以live实体坐标和当前取整/范围规则做局部适配，不宣称完整native final goal状态机等价。
- 下一步仍需distance budget/final goal/续算状态及实际pathType受控接入；普通A*未切换，
  火焰之河AI fallback未修/未重跑，无性能/GL/全图/原版动态等价验收。
  本轮只定向暂存自己的文件，未改另一轮对象选择提交；文档独立提交推送，最终SHA见git log。

## 2026-10-15 第七批：恢复路径保留单位目标（历史）

- 代码/测试 `b0a1f4912a52f2ee4abf0ec4d78fab392d0003d3` 已推送origin/master，远端SHA一致。
  修复动态阻挡恢复绕过public入口时，stop→绕行→重建Pathfind丢失targetEntityId，
  以及直接恢复不刷新目标坐标快照/重算timer的问题。目标身份与可达末点分离。
- 只在核心路径接受后统一安装metadata；保留普通exact、显式DiabRun partial固定目标、
  房间前/后置gate及近期碰撞/平滑/移动优化，未接入原生搜索backend。
- 修改前新增6项中4项失败；最终新增9项通过，合并153项/25类通过，失败/错误/跳过0。
  XML：`core/build/test-results/upstreamRound4-round7-full`。新测试使用真实ECS恢复/消费，
  Map和动态碰撞服务为scripted/mock，不是实际地图碰撞或原版DLL执行。
- 真实1.10F MPQ/TCP复验：25Hz、seed12345，63蛆虫二层、64蛆虫三层、65古代通道，
  3图/10击杀/0通行失败，连续入口1次、staging跳转1次；AI errors/fallbackRecords均0。
  stalled日志0次，故仅为非回归冒烟，不声称真实TCP验收了本次stop/recreate分支。
  shadow128次：83可比较（55 NONE、28 TURNS），45越界；无诊断错误/live重放差异。
- 固定D2MOO `0c0bbd1e6bef9181a3da1c046523af622a925416`，本轮未fetch；结合本机
  D2Common/D2Game静态反汇编核实pTargetUnit独立于路径点、partial不等于到达、
  Toward失败转type15绕墙而非A*。上游标BROKEN的10142不单独当oracle。
- 下一步按实际pathType设计受控接入，补native distance/final goal/续算状态；不照搬5格阈值。
  普通A*未切换，火焰之河AI fallback仍未解决/重验；无benchmark/GL/全图/原版动态等价验收。
  本轮仅定向暂存自己的文件，未混入另一轮COF修改；文档独立提交推送，最终SHA见git log。
  证据、DLL地址及另一电脑复验命令见上游记录第七批和比较报告末尾。

## 2026-10-15 第六批：真实地图 shadow 采样（历史）

- `033b2e9d94e26b7d0c60f9be530e38c1b6282adb` 已推送origin/master且远端SHA一致。
  新增来源关卡过滤、脱离地图引用的关卡/房间上下文及OUTCOME/ENDPOINT/TURNS分类；
  默认关闭，仍不安装参考路径，不改生产碰撞、平滑、房间或移动逻辑。
- 最终133项/22类通过，失败/错误/跳过0；独立XML目录
  `core/build/test-results/upstreamRound4-round6-final`，显式指定本机1.10F资源。
- 两组主采样237次：195次COMPARED（105次输出一致、57次仅压缩转向点不同、
  33次runtime FAILED/reference PARTIAL），42次快照外不判定；诊断错误/live重放差异均0。
  33次部分路径不等于到达，也不是33个独立缺陷；没有可比较的FAILED/EXACT样本。
- 蛆虫二/三层及古代通道63/64/65在25Hz、seed12345普通难度通过：3图、10次真实TCP击杀、0通行失败，
  1次连续入口/1次测试staging跳转，不是两条连接都通过；AI gate通过，fallbackRecords=0。
- 火焰之河107/108通行子检查通过、连续入口1次；整个任务因AI fallbackRecords=148而失败
  （errors=0），不能标为完整通过。原生参考只给出部分接近路径，未证明能解决生产卡路。
- 新增4项回归覆盖过滤额度/作用域与元数据脱离、外层房间迭代器、动态目标两次快照、
  partial终点差异。场景中也观察到同一怪物对移动玩家多个坐标请求；不是完整targetUnit状态机验收。
- 下一步核对1.10f部分路径消费/重算及房间后置gate，再按pathType设计受控接入；
  42个越界样本仍需补证，无benchmark/GL/132图/多seed三难度/原版动态差分。
- 6114监听已释放；未改动/暂存另一轮的`CofModeTransitionTest.java`及随后出现的
  `CofLayerCacher.java`/`CofLayerLoader.java`修改；本轮回归不为这些COF修改背书。
  代码提交后本轮四份进度文档独立提交推送，最终文档SHA见git log；不宣称整个工作区clean。
  复验命令、失败运行和样本明细见上游记录第六批及详细比较报告末尾。

## 2026-10-15 第五批：只读寻路 shadow（历史）

- 代码/测试/开关 `08fa479b6` 已推送origin/master且远端SHA一致。默认关闭，实际A*
  返回后捕获有界effective collision，独立重放生产/参考A*；不改移动结果或最近碰撞优化。
- 生产重放与live点序一致后才比较；快照外/超大/变化输入分开标记，诊断RuntimeException
  隔离，未消耗RNG。每边最多128格、默认每128请求采1次、最多16次尝试（硬上限128）。
- 最终129项/22类通过，失败/错误/跳过0；指定本机1.10F资源，报告隔离在
  `core/build/test-results/upstreamRound4-round5-final`。含21项新类与1项接缝新增回归。
- 启用诊断的真实TCP移动意图冒烟通过：seed12345，(132,37)→(135,37)，双方EXACT/
  压缩输出一致，ACK tickLag1。服务端正常退出，6114监听已释放；非GL或原版动态对比。
- 另一电脑运行：`gradlew.bat :server:d2gs:headlessMovementIntent -PpathShadow=true
  -PpathShadowEvery=1 -PpathShadowMaxSamples=4 --console=plain`，先显式设置1.10F D2_HOME。
  完整复验命令、夹具结果、状态含义见上游记录第五批及详细比较报告后续章节。
- 下一步扩展真实狭路/怪群/移动目标样本，之后按pathType选择原生接入。
  尚未切换生产A*、native pattern或目标状态机/定点步进；没有性能benchmark、全图/GL验收。
- 代码提交后仅本轮四份文档未提交，无其他代码变更；文档独立提交推送，最终SHA见git log。

## 2026-10-15 第四批：寻路碰撞比较与非法尺寸（历史）

- 代码/测试 `776bd89c3` 已推送origin/master，远端SHA核对一致：非法size按1.10f普通small
  presence回退，先于town/interact转PET；动态写入/查询/诊断/回滚一致。
  保留正常尺寸、size0兼容及静态/生成接口。
- 107项/21类定向回归通过，失败/错误/跳过0。修改前3项全失败；包含2项当前差异夹具，
  不能当作执行原版DLL或benchmark。显式指定1.10F资源，XML按upstreamRound4隔离。
- 详细报告 `path-collision-upstream-comparison-2026-10-15.md`：用户确认近期D2Client与
  D2Common逆向来源；已保留近期原生候选pattern、门/飞行/占位/死亡等优化。
  普通生产仍为Map→AStarPathFinder，独立原生A*/IDA*/Step不等于已接入。
- 原生搜索/状态机更贴近原版，不代表无实测就能说性能更优。下一步建议只读shadow差分，
  尚未实施或替换生产搜索器；不混入1.13+gate，不直接改速度积分/重算阈值。
- 详细复验命令、源码基线、DLL哈希/地址与范围见报告及上游移植记录第四批。
  未作TCP/GL/132图/25Hz/多seed×三难度验收。代码提交后仅本轮五份文档未提交，
  无其他代码修改；文档另行提交推送，最终文档SHA见git log。

## 2026-10-15 第三批：A1 测试契约闭环（历史）

- 测试提交 `052a9730f` 已推送origin/master，远端SHA一致。只修改测试，不改生产地图
  或出口；第二批遗留的两项A1失败已在当前工作区复现并闭环，未作干净历史checkout验证。
- D2MOO地板层号同样适用于floor：Dark Wood的1对base/overlay不是同层重复。
  地下通道另有2011条隐藏style-30填充，不应计入可见地板；逐类精确记账，仍拒绝同层覆盖。
- 26↔27、27↔28是Vis/Warp=-1连续连接，改验原生边、目标slot、双端锚点及世界坐标，
  不再要求每关都存在交互Warp marker。
- 95项/18类通过，另7轮修道院接缝检查通过（普通五种子、默认种子噩梦/地狱）；
  失败/错误/跳过0。合计覆盖六种子/三难度，非全组合矩阵；无TCP/GL或全项目验收。
- 测试提交前无其他暂存变更；另一轮DiabPrison已独立提交为 `2bbb57778`，本轮未混入。
  文档提交前工作区除本轮三份进度文档外无未提交内容；详细证据/复验命令见
  `d2moo-upstream-port-2026-10-15.md` 第三批。
- 下一步继续筛查碰撞、寻路等尚未移植的上游1.10f修正，不将本轮测试闭环计为新生产移植。

## 2026-10-15 上游修正第二批交付（历史）

- `1c55883e3` 已推送origin/master，远端SHA一致：WarpDist普通怪群排除、Quill Rat
  额外弹体/双字种子及逐弹体A2快照；102项/29类定向回归通过，失败/错误/跳过0。
- 原始1.10f quillrat1–8三难度aip3均0，原版不增加弹数；非零分支使用夹具验收。
- 第二批提交时另一轮DiabPrison仍在工作区修改，只部分暂存Actioneer飞弹hunk，未纳入监牢代码。
- 第二批交付时额外A1测试两项失败未收口；现已按上方第三批闭环，不声称全项目通过。
- 出生点逐次RNG、原版unit ID分配、A1独立人口、无单位目标路径点入口以及真实TCP/GL
  仍有边界，详见 `d2moo-upstream-port-2026-10-15.md` 和路线图第二批章节。

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

### 2026-10-13 A1–A5 地图巡游修复进行中

- 当前优先：用户确认五城镇→野外强制通行及固定五种子1/2/3/12345/54321。
  `0a587cba`已推送origin/master，167项回归通过；pass45五种子100Hz批次seed1已完整
  132/132、80入口、204击杀、0失败PASS且逐图/五城镇审计通过，seed2运行中。
  当前禁止编译覆盖运行JAR；剩余四种子100Hz和最终五种子25Hz仍待完成。
  pass42全25Hz132图1失败（10），pass43等待TCP死亡/总是重采样修复后A1前16图25Hz PASS。
  pass44强制出口发现A1/A2/A3缺失；pass45原生orth锚点修复后五出口10图专项100Hz PASS。
  新增`tools/run-map-tour-seeds.ps1`，默认25Hz串行五种子、逐图移动/五城镇出口独立审计；
  完整地图回归进行中，五种子全图尚未执行，最终25Hz与实机视觉仍待。
- 最新完整结果：`4cebb868`的pass42-all 100Hz全132图PASS，210移动、78连续入口、
  53非连续跳转、183真实TCP击杀、0失败，BUILD SUCCESSFUL in 34m26s；独立审计
  确认132图唯一LOAD且都有LOCAL_WALK成功，无服务端ERROR/异常及无效落点。
  死机后发现原进程仍在，未重启、未覆盖JAR；25Hz全132图pass-42-all-25hz已启动，
  当前运行中禁止编译，25Hz完成与另一台电脑的真实视觉验收仍待。
- 最新：46091be4和cfa3cdd1已推送origin/master；pass37的18图专项18/18、11次入口、
  5次真实击杀、0失败PASS，确认A2下水道/古代通道及A4真实连续105→106→107→108。
  pass37-all仅进入28图，23通行失败，28陷入复活/错误目标近战gate循环后已停止，未完成。
  pass38修正原版近战射线、精确目标gate并加入真实移动攻击接近点，回归147 + 9 + 9全通过，
  `3f07799a`已推送；专项21→22→23→24通过，军营走过旧堵点，但发现动态堵路会绕向
  无关关闭门，已停止专项，记未完成。pass39静态目标已连通时只清障不选门，服务端9项
  回归通过，完整132图100Hz重新运行；探针修正提交推送master，准确hash见git log。
- pass39-all最终132/132、74入口、287击杀、4失败（31/34/76/87）。`6f42842d`已推送；
  A2/A4/A5未报失败，A1/A3仍未通过，25Hz未执行。继续四处定向诊断，不能宣布全图完成。
- pass41依原版DeathEvent释放deadCol=false live碰撞，12图专项只剩76；31/87入口通过。
  pass42恢复A3丛林head/tail固定文件选择，真实MPQ入口/head连通79290格回归通过；
  偏离格心首段受阻时只允许正常走回本格中心再前进，不snap、不豁免碰撞。
  自动回归148 + 40 + 9项通过；完整132图和25Hz仍待后续重测。
- pass42的12图专项12/12、8入口、18真实击杀、0失败PASS，四个旧失败点均定向复验通过。
  `4cebb868`已推送origin/master；完整132图100Hz重跑进行中，25Hz最终验收仍待。
  完整132图100Hz和25Hz最后验收仍待；build日志不提交，运行中禁止编译。
- 当前地图任务以 `docs/d2moo-progress-roadmap.md` 的“全 132 图 TCP 巡游”章节为准。
- pass17：132 计划、131 入图、23 失败；pass18：132 入图、19 失败，尚未通过。
- 当前迭代增加原生 Orth 出口锚点、静态出口归属、预热对象即时碰撞和真实 TCP 战斗清障；
  用户已授权杀死堵路怪物，禁止清怪/禁碰撞/移动位置包作弊恢复，focus 不算完整验收。
- 最新回归 129 + 41 + 2 项全部通过，真实渲染验收留给另一台电脑。
- pass19 focus 10 入图/3 失败；pass20 focus 19 入图/5 失败，仅定向诊断。
- pass21 focus 11 入图/2 失败（26/55），回归 136 + 38 + 4 项通过。
- pass22 全 132 入图、16 失败、35 次真实 TCP 清障，尚未通过；109→110 与 110→111 通过。
- pass23 修复：双向迷宫房间合并、原生 wall/roof 碰撞优先、门接近范围与有进展恢复预算；
  玩家百万生命在 D2S 加载后设置并校验，修正存档截断导致的后段死亡；静态路线只规划，
  实际移动仍受生产单位/地形碰撞限制。
- pass23/24/25 focus 均剩 8 项失败；pass25 为 17 入图/35 次真实清障，仍非完整验收。
- 两阶段原生瓦片导出修复已通过真实 MPQ 快照回归；修正墓穴/宫殿/蛆虫等门对象 ID，
  普通地板邻房链接不再重抽 DT1；pass26 完整132图进入、70次连续入口通过、83次击杀，
  剩8失败（26/28/29/31/63/76/105/107），尚未无错。此前回归141 + 4 + 6通过。
- 待验证修改包含原版修道院道路入口、精确 UNIT_TILE 坐标、静态门清障规划与 A4 原生
  seamless 锚点；巡游运行中禁止编译/覆盖运行 JAR，不把后写的修改计作本轮测试结果。
- 当前累计地图/碰撞/巡游代码未提交；起点 `20b0c587`，最终需 commit 并 push origin master。
- pass26 运行中另写入 WarpN/VisN 语义修复（依据 DrlgDrlgWarp.cpp），seamless -1
  回归和 combat snapshot baseline/身份/近战范围诊断，不属于 pass26 运行结果。
  pass27 显式指定1.10F D2_HOME回归145 + 59 + 7项通过；focus在军营发生同门递归
  StackOverflowError退出，26→27、27→28已通过。已修复递归，收紧nativeWarp落点，pass28待验证。
  机器默认资源目录是另一安装版本，不能混作验收。
- pass31 focus剩31/107；pass32确认107→108，pass33确认31→32且2图专项PASS。
  A2/A5兼容marker与native UNIT_TILE已分离，门frontier有界重规划，恢复上限32/总128。
  pass34全套自动回归通过，但完整132图100Hz TCP巡游最终7失败（28/40/47/48/44/76/107），
  205次移动、71次入口通过、75次真实击杀。106连续入107后仅43格连通，不能以直接staging通过代替。
- pass35对照LevelsTbls.cpp恢复LvlWarp direction=b通配/首行匹配，真实蛆虫入口单位已重新导出；
  相同Warp.Id按RoomEx的HAS_WARP槽flags消歧，sample探针30秒且保留异常链，Zone回收清理seam。
  指定1.10F自动回归通过，24图focus进行中；最终全132图、25Hz验收与commit/push仍待完成。
- pass35 focus最终24图进入、5失败（40/47/48/44/107）。pass36死机前回归未结束；重启后
  单worker真实MPQ3项定向测试通过，源码完整保留。A2下水道/古代通道专用房分支已补入，
  全声明UNIT_TILE入口导出通过。A4 WarpMesa原生碰撞证实D2MOO候选pattern可连通42305格，
  额外侧向完整footprint仅43格；已修正生产MapGraph/巡游BFS，完整回归与TCP连续链待完成。
- pass36指定1.10F单worker地图147 + 定向41 + 服务端8项全部通过，18图focus进行中。
  当前准备提交并推送累计地图巡游修复检查点；全132图与25Hz最终验收仍未完成。
- 检查点46091be4已推送origin/master（88文件）。pass36 focus通过A2下水道链和古代通道，
  随后105的怪物动画武器类-1数组越界停止服务端；已结束失效巡游，不算focus通过。
  pass37按D2Composit未知类HTH契约修正创建，并防御速度查询负索引；回归与连续链重测待完成。
- pass37指定1.10F回归147 + 17 + 8项通过；18图focus已通过105→106→107，真实进入后
  角色已走出107楼梯旧43格孤岛，107→108及最终全图结果待完成。

### 2026-10-03 Death Sentry 真实邻接 RoomEx 顺序 gate 完成

- [x] 新增 `-PareaDeathSentryAdjacentRoomGate=true`，从真实 MPQ 当前 hostile 的
  `pRoomsNear` 导出顺序选取两个邻房，创建两个不同 RoomEx 的死亡目标，并保留各自
  的 authoritative `MapWrapper.roomId`。
- [x] 真实双客户端 gate 通过：`targetRoom=0`、首个邻房 `13`、第二个邻房 `1`；
  服务端 `deathLastCorpseId=132` 与首个 `pRoomsNear` 尸体一致，第二具尸体未被消费，
  owner/observer 共享 Death Sentry 视觉，`animationFallback=false`。
- [x] 使用服务端 `SummonedPet.deathLastCorpseId` 判定消费结果，不再把所有死亡尸体
  都携带的 `CORPSE_NOSELECT` 状态误当作消费证据。
- [ ] 该专项 gate 跳过重复 reconnect；普通 276 gate 已独立覆盖 reconnect。
- 本轮提交 `ab7e7de2` 已推送到 `origin/master`。

### 2026-10-03 Death Sentry 邻接 RoomEx 顺序回归完成

- [x] `AssassinTrapSystem.corpseRoomRank` 现在按 `RoomEx.getAdjacentRoomIds()` 的
  导出顺序分配 rank，保留 D2MOO `pRoomsNear` 的顺序；当前房间仍为最高优先级，
  非邻接房间在 native topology 下不会被误判为直接邻房。
- [x] 新增 `deathSentryPreservesNativeAdjacentRoomOrderBeforeInsertionOrder`：让后插入
  的尸体位于 `pRoomsNear` 首项，验证首项尸体先于更早插入的后续邻房尸体被消费；
  `AssassinSkillSpecializationTest` 43 项通过。
- [ ] 目前是 D2MOO topology 注入的确定性 ECS 回归，尚未在真实 MPQ 中同时布置两个
  邻房尸体并观察选择顺序；下一步是构造真实跨邻房尸体场景或记录无法稳定构造的原因。
- 本轮提交 `fd642ec4` 已推送到 `origin/master`。

### 2026-10-03 Death Sentry(276) 真实墙体/null-hit 伤害 gate 完成

- [x] 新增真实 MPQ 双客户端 gate 选项：`-PareaDeathSentryWallGate=true` /
  `--area-death-sentry-wall-gate`。夹具通过生产地图 `FLAG_BLOCK_JUMP` 射线扫描，
  在 Blood Moor 生成墙后目标，并在放置后再次用生产射线确认阻挡。
- [x] gate 同时验证可见目标实际掉血、墙后目标生命不变、owner/observer 结果一致；
  本次结果为 `visibleLife=9.0`、`blockedLife=100000.0`、`blocked=true`，并通过
  observer reconnect（`active=[137]`、`expiredDuringReconnect=0`、`stale=false`）。
- [x] 普通 276 gate 复跑通过：`sharedMissiles=[135]`、双方
  `states=[118,104,108]`、`animationFallback=false`，重连保持
  `active=[135]`、`expiredDuringReconnect=0`、`stale=false`。
- [x] `./gradlew :core:test --tests
  com.riiablo.engine.server.AssassinSkillSpecializationTest --no-daemon`：42 项通过。
- [ ] 多个邻接 RoomEx 之间的 `pRoomsNear` 具体顺序仍未由真实 MPQ Death Sentry
  场景单独证明；下一步继续做跨邻房尸体候选顺序的可观测 gate。
- 本轮提交 `53a706f6` 已推送到 `origin/master`。
- 本轮修改仅涉及 `server/d2gs` gate/fixture 与三份进度文档；其他 agent 的
  `ItemEntry.java`、`MissileCollisionSystem.java`、`StatFormatterTest.java` 及日志/dump
  不得 stage、覆盖或清理。

### 2026-10-03 Death Sentry 当前 RoomEx 尸体优先级回归完成

- D2MOO `DrlgDrlgRoom.sub_6FD77BB0` 的近房数组包含当前房间，并经
  `sortRoomListByPosition` 整理；riiablo Act1/2/3/5 bridge 保留该 native 数组顺序。
- 新增 `deathSentryPrefersCurrentRoomBeforeNewerAdjacentCorpse`：当前 RoomEx 的旧尸体
  先于直接邻房的更新尸体被 `SrvDo055` 消费，证明 `corpseRoomRank` 高于
  `insertionOrder` 的层级契约。
- `./gradlew :core:test --tests
  com.riiablo.engine.server.AssassinSkillSpecializationTest --no-daemon`：42 项通过，
  `BUILD SUCCESSFUL`。
- 真实 MPQ 尚未证明多个邻房之间的具体顺序会改变 Death Sentry 选择；下一步为真实
  墙体/null-hit 伤害 gate。工作区其他 agent 的未提交文件仍不得 stage、覆盖或清理。
- 本轮测试与文档提交 `2157811b` 已推送到 `origin/master`；当前工作区仍只保留
  其他 agent 的未提交文件及本地日志/dump。

### 2026-10-03 Assassin Death Sentry(276) hostile target filter 回归完成

- `AssassinTrapSystem.nearestHostile` 已接入 D2MOO 的已知目标门槛：attached
  `NativeUnitFlags` 必须可攻击且为有效目标，Town 与跨 zone 目标排除；真实 map topology
  下执行 `FLAG_BLOCK_JUMP` missile-barrier 射线检查。
- 新增无效 flags/Town 与墙后目标回归；完整 `AssassinSkillSpecializationTest` 41 项通过，
  `BUILD SUCCESSFUL`。无拓扑 headless 夹具继续兼容放行，不把缺失地图数据当成墙体。
- 真实 1.10f `:server:d2gs:headlessAreaSkill -PareaSkill=276 -PareaTimeout=15
  -PareaVerbose --no-daemon` 已复跑通过：双端 `sharedMissiles=[135]`、
  `states=[118,104,108]`、`animationFallback=false`；observer reconnect 为
  `active=[135]`、`expiredDuringReconnect=0`、`stale=false`。最终提交复跑的精简日志
  保留在本地 `death-sentry-target-gate-final.log`，不纳入提交。
- `./gradlew :core:test` 已执行但基线全量结果为 `2167 tests completed, 133 failed,
  12 skipped`；失败集中在仓库缺失的 `test/*` 资源、外部 `G:\` MPQ 路径、未注册
  `CofManager` 夹具及既有地图/战斗断言，未见本轮 Assassin 专项失败，不把该全量结果
  标记为本次改动回归通过。
- 下一步：真实双客户端 owner/observer/reconnect gate；随后处理 Death Sentry 尸体候选
  的 D2MOO 房间链表优先级。工作区仍有其他 agent 的未提交文件，提交时只 stage 本轮
  `AssassinTrapSystem.java`、`AssassinSkillSpecializationTest.java` 和三份文档。

### 2026-10-03 Assassin Death Sentry(276) hostile target filter 静态差异（历史记录）

### 2026-10-03 Assassin Death Sentry(276) hostile target filter 静态差异

- D2MOO `sub_6FCF1A50` / `sub_6FCF1980` 要求目标存活、非 Town、具备
  `CANBEATTACKED`，并通过 `COLLIDE_MISSILE_BARRIER`；当前 `nearestHostile` 未显式
  复刻这些门槛，只按 Monster/Position/HP 过滤。
- 该差异是墙体/null-hit 的生产待办；本轮不修改仍有其他 agent 未提交内容的
  `AssassinTrapSystem.java`。下一步由其负责人接入 NativeUnitFlags/碰撞回归。
- 本轮为文档静态对照更新，无编译；提交：`6224438a`（`docs: record Death Sentry target filter gap`），
  已准备推送；工作区其他未提交文件保持不动。

### 2026-10-03 Assassin Death Sentry(276) 多尸体候选顺序迁移完成

- D2MOO `UNITROOM_AddUnitToRoomEx` 将新单位插入 `pUnitFirst`，
  `UNITFINDS_FindAllMatchingUnitsInNeighboredRooms` 按 RoomEx 链表扫描，
  `sub_6FD15210` 返回首个匹配尸体；当前实现已用 `Corpse.insertionOrder` 记录该头插入
  语义，并按当前房间/直接邻房层级优先。
- 回归使用更近的旧尸体和更远的新尸体，确认新尸体先被消费，第二发再消费旧尸体；
  `AssassinSkillSpecializationTest` 41 项通过。
- 真实 MPQ 仍需核验 `pRoomsNear` 邻接数组的具体顺序；在此之前不宣称跨邻房的最终
  顺序已经完全等同原生。工作区其他 agent 未提交文件仍保持不动。

### 2026-10-03 Assassin Death Sentry(276) null-target fail-closed 对照

- 新增无 hostile target 回归：Death Sentry 保持 idle，不生成 Skill2 missile，
  `shotsFired` 不增加且控制器仍存在。
- `AssassinSkillSpecializationTest` 39 项通过。下一步：同距尸体候选顺序、墙体阻断和
  真实双客户端 null-target/fallback。
- 本轮代码/测试提交：`b8e4c9c5`（`test: close Death Sentry null target gate`）；
  工作区仍保留其他 agent 的未提交文件，不得 stage、覆盖或清理。

### 2026-10-03 Assassin Death Sentry(276) Skill2 闪电 fallback 实际发射

- 新增无合法尸体的运行时回归：强制通过召唤体 `aip3` 后，实际生成 Skill2 的
  authoritative lightning missile，确认 `damageSnapshot=true`，并在碰撞后实际降低
  hostile 生命。
- 发现并锁定 MPQ missile 别名链：逻辑字段为 `sentrylightningbolt2`，不能用显示别名
  `sentry lightning` 直接比较 row 名；测试现在比较解析后的稳定 missile ID。
- `AssassinSkillSpecializationTest` 39 项通过。下一步：同距尸体候选顺序、墙体/null-hit
  和真实双客户端 fallback。
- 本轮代码/测试提交：`49516d7f`（`test: verify Death Sentry lightning collision damage`）；
  工作区仍保留其他 agent 的未提交文件，不得 stage、覆盖或清理。

### 2026-10-03 Assassin Death Sentry(276) 多尸体预算事务对照

- 新增两发 shot budget 回归：连续两个 `SrvDo055` 事务分别消费两个合法尸体，已消费
  尸体不可重复选择，控制器在第二次成功事务后按预算退出。
- 测试不假设 ECS 实体顺序等同于 D2MOO 房间链表顺序，只锁定一次成功事务只能消费
  一个尸体的原生契约；`AssassinSkillSpecializationTest` 37 项通过。
- 下一步：同距尸体的 D2MOO 候选顺序、Skill2 闪电逐目标伤害及墙体/null-hit。
- 本轮代码/测试提交：`09d26a1c`（`test: cover Death Sentry multi-corpse budget`）；
  工作区仍保留其他 agent 的未提交文件，不得 stage、覆盖或清理。

### 2026-10-03 Assassin Death Sentry(276) 原生尸体距离边界对照

- 对照 D2MOO `AITHINK_Fn104_DeathSentry` → `sub_6FD15210`，新增严格边界回归：
  10-tile 候选搜索后，尸体到 hostile 必须严格小于召唤体攻击行
  `Param3 + (skillLevel - 1) * Param4` 的一半，恰好边界不会被消费。
- 测试读取 `MonStats.Skill1 = "mon death sentry"` 的参数，而不是放置技能 276 的
  参数；`AssassinSkillSpecializationTest` 36 项通过。
- 下一步：正常 7 发预算下多尸体候选顺序，再核对 Skill2 闪电逐目标伤害和墙体/null-hit。
- 本轮代码/测试提交：`62710d25`（`test: lock Death Sentry corpse range boundary`）；
  工作区仍保留其他 agent 的未提交文件，不得 stage、覆盖或清理。

### 2026-10-03 Assassin Death Sentry(276) shot budget / 到期独立门槛

- 纯 ECS 夹具将 `Death Sentry` 的 `maxShots` 临时设置为 1；首次合法尸体爆炸后
  `shotsFired=1`，下一次系统 tick 删除 `SummonedPet` 控制器，确认 shot budget
  到期路径实际执行。
- 删除前验证无合法尸体时仍解析召唤体 `Skill2 = "death sentry ltng"` 及其权威
  闪电 missile；删除后的连续 tick 不再生成第二枚 `corpseexplosion`。
- 测试：`./gradlew :core:test --tests
  com.riiablo.engine.server.AssassinSkillSpecializationTest`，35 项通过，
  `BUILD SUCCESSFUL`。
- 未完成：正常 7 发预算下的多尸体选择顺序、Skill2 闪电逐目标伤害、墙体/null-hit
  和真实双客户端到期视觉。下一步为 Death Sentry 目标筛选/伤害边界。
- 本轮代码提交：`68bbab9c`（`test: close Death Sentry shot budget gate`）；交接 hash
  文档提交：`91c512c8`；状态对齐文档提交：`7063c1c6`；均已推送到 `origin/master`。
  工作区仍保留其他 agent 的未提交文件，不得 stage、覆盖或清理。

### 2026-10-03 Assassin Sentry AI attack-row fail-closed 对照

- 新增 `sentryAiResolvesNativeAttackRowsInsteadOfPlacementSkills`，覆盖 Wake of Fire、
  Inferno、Death Sentry 的 `MonStats.Skill1/Skill2` 攻击行解析，禁止回退执行放置技能。
- 新增 `AssassinSentry`/`DeathSentry` 专用 AI，真实 262/272/276 gate 均未再出现
  `AI_FALLBACK ... GenericMonster`；双端实体、重连、自然过期和 `animationFallback=false`
  均通过。
- `AssassinSkillSpecializationTest` 35 项通过；shot budget、到期、墙体/null-hit 和逐目标
  伤害仍未宣称完成；新增 Wake 单次预算退出和 Inferno channel+预算退出纯 ECS 断言。
- Death Sentry 已新增独立 one-shot corpse budget/到期门槛；正常多发预算、墙体/null-hit
  和逐目标伤害仍未宣称完成。
- 前一轮代码提交/推送：`1c38059b`（`fix: register Assassin sentry AI implementations`）。
- 本轮测试与文档提交/推送：`c9cf79d1`（`test: assert Assassin sentry shot budget retirement`），
  `origin/master` 已同步。

### 2026-10-03 Assassin Wake of Fire Sentry(262) gate 收口

- 真实 MPQ 双客户端已实际触发 `MIS` keyframe 和 `SrvDo045`，
  `animationFallback=false`；owner/observer 共享一个 `assassintrap` 控制实体和
  maker(517)→双波(518)，重连结果为 `active=[134]`、`expiredDuringReconnect=2`、
  `stale=false`。
- fallback 证据现在接受首个权威控制实体，避免 maker/波延迟期间重复创建陷阱；夹具保留
  目标动态碰撞并将目标放在落点外一格，零向量问题已消除。生产 `AssassinTrapSystem`
  未改动。
- 未完成：逐目标伤害、波形参数、墙体/null-hit、shot budget、到期和
  `AssassinSentry` AI fallback；下一步 Inferno Sentry(272)。
- 本轮提交/推送：`d99c5352`（`test: close Wake of Fire sentry keyframe gate`），
  `origin/master` 已同步到该提交。

### 2026-10-03 Assassin Inferno Sentry(272) gate 收口

- 真实 MPQ 双客户端实际触发 `MIS`/`SrvDo045`，`animationFallback=false`；双方共享
  `assassintrap` 控制实体和至少两枚 missile=523 通道导弹。
- 重连结果为 `active=[133]`、`expiredDuringReconnect=1`、`stale=false`；仍有
  `AssassinSentry` AI fallback 警告，通道参数、伤害、墙体/null-hit、shot budget 和
  到期清理尚未完成。下一步 Death Sentry(276)。

### 2026-10-03 Assassin Death Sentry(276) gate 收口

- 真实 MPQ 双客户端实际触发 `MIS`/`SrvDo045`，`animationFallback=false`；尸体事务和
  视觉导弹均同步。视觉导弹按真实 `MonStats.Skill1` 解析为 `skill=312/missile=115`，
  不再把放置技能 276 或旧的 641 当成视觉行。
- 重连结果为 `active=[135]`、`expiredDuringReconnect=0`、`stale=false`；仍有
  `DeathSentry` AI fallback 警告，爆炸伤害/范围、尸体重复消费、墙体/null-hit、shot
  budget 和到期清理尚未完成。下一步回收 Blade Shield(277) 边界。
- 本轮代码/文档提交：`acc3ccca`（`test: close Death Sentry keyframe gate`），已推送到
  `origin/master`；随后将补一个只更新交接 hash 的文档提交。

### 2026-10-03 Blade Shield RoomEx / target-filter 增量

- `StateUpdater.processBladeShield` 已按 D2MOO aura 扫描语义限制完整地图实体到同一
  Map/Zone 的当前或直接相邻 RoomEx；没有完整 `MapWrapper`/拓扑的 detached 夹具保持兼容。
- 墙体不作为额外射线阻断条件；Native 无效/null-hit 目标在伤害前拒绝。
- 78 项 Assassin/Trap 定向回归通过；真实 277 MPQ gate 仍需后续合并复核。
- 真实 277 MPQ gate 已复核：owner/observer 均收到 `BLADESHIELD`（`StateId=158`、
  level=20、`perdelay=25`），目标两端实际掉血，`animationFallback=false`；严格重连
  断言现在要求 owner 仍活动时恢复 `states=[158]`，不再把空状态集合判为通过。
- 本轮 277 验证：`:server:d2gs:headlessAreaSkill -PareaSkill=277 -PareaTimeout=15
  --no-daemon`，`BUILD SUCCESSFUL`；核心 AssassinSkillSpecializationTest 通过。
- 新增可控 `--area-skill-level`/`--require-area-skill-expiry` 入口；level=1 真实 MPQ
  gate 初始 `duration=499` 帧，双端先恢复 `states=[158]`，随后同时观察到状态清除并输出
  `area_skill_expiry_pass ... ownerStates=[] observerStates=[]`；状态清除后静默观察 1 秒，
  两端目标生命没有继续下降。
- 到期 gate 现在还检查 `duration < perdelay` 的最后延迟尾窗；本轮 `finalDelayTail=true`，
  尾窗到状态清除前两端目标生命均未下降。
- 最后一个可合法周期通过 `periodicCountdown` 重置计数严格收口：
  `finalPulseResets=1`；随后状态清除和 1 秒静默窗口继续通过。
- 客户端 `StateOverlaySystemTest` 已确认 `bladeshield` Overlay 资源存在；StateP duration
  从 3 更新到 1 时 Overlay 保持，只有权威 `StateId=158` 移除后才清除。
- 新增 `bladeShieldOverlayRowDeclaresNativeDccTiming`：确认 `Overlay.txt` 的文件名、帧数、
  `AnimRate` 和四个 Blade Shield DCC（front/back/fade）均能从 1.10f MPQ 解析。
- 本轮提交/推送：`790140f0`（`test: verify Blade Shield MPQ overlay assets`），
  `origin/master` 已同步。
- 仍待：真实 MPQ 客户端渲染窗口的逐帧视觉持续时间；静态资源存在不等价于窗口截图通过。
- Overlay 生命周期提交：`12069ba0`（`test: verify Blade Shield overlay lifetime`），已推送到
  `origin/master`。
- 最后延迟尾窗提交：`ee74ad02`（`test: assert Blade Shield final delay tail`），已推送到
  `origin/master`。
- 最后周期计数提交：`204d879f`（`test: count Blade Shield final pulse`），已推送到
  `origin/master`。
- 双端 `StateP` 现在还必须同步 `skill=277`、等级、`perdelay` 和 `AuraLen`（允许最多一帧
  网络快照偏差），本轮 level=20 gate 通过。
- 持续时间同步提交：`e58382b1`（`test: verify Blade Shield duration sync`），已推送到
  `origin/master`。
- 静默窗口加固提交：`15017c2e`（`test: verify Blade Shield post-expiry silence`），已推送到
  `origin/master`。
- 该增量已在前一提交完成；当前工作区仍有其他 agent 的未提交代码
  `ItemEntry.java`、`AssassinTrapSystem.java`、`MissileCollisionSystem.java`、
  `StatFormatterTest.java`，以及未跟踪日志/dump，均不属于本轮，接手者不得覆盖或提交。

### 2026-10-02 Assassin Fire Trauma(251) 真实 MPQ gate

- `:server:d2gs:headlessAreaSkill -PareaSkill=251 -PareaTimeout=25 --no-daemon`
  已通过真实 1.10f MPQ 双客户端 gate；owner/observer 共享 `bomb in air`(385)、
  `bomb on ground`(386) 及爆炸表现(387)，并确认两端目标实际掉血至死亡。
- observer reconnect 已通过一次性效果专用规则：断线期间短寿命实体自然过期，重连不恢复
  已删除导弹；目标已死亡时允许重连基线不再包含该实体，但禁止恢复为存活目标。
- 本轮只修改 `D2GSHeadlessClient` 的测试/验证断言和三份交接文档；没有改动 Fire Trauma
  生产碰撞、伤害、爆炸或 owner/damageOwner 逻辑。Assassin 其他 trap 技能仍需独立 gate。

### 2026-10-02 Assassin Shock Field(256) 真实 MPQ gate

- `headlessAreaSkill -PareaSkill=256 -PareaTimeout=25` 已通过：双方共享 11 枚 native
  `shock field in air`（388），`animationFallback=false`，并通过通用 area missile
  reconnect 水位/子集检查。
- 失败根因是 `cltdofunc=5` 被误当作投掷武器需求，导致真实 keyframe cast 被拒绝；已在
  `NativeSkillResolver.isThrowableSkill` 中按 Assassin trap 的精确 `SrvDo` 白名单排除，
  未放宽 Amazon 或 generic throw。`NativeSkillResolverTest` 定向回归通过。
- 当前只完成原生创建、双端同步和重连；Shock Field 墙体/null-hit、伤害和高等级协同
  仍是后续工作。下一项优先 Blade Sentinel(257) 的 trap owner/回旋导弹 gate。

### 2026-10-02 Assassin Blade Sentinel(257) 真实 MPQ gate

- `headlessAreaSkill -PareaSkill=257 -PareaTimeout=25` 已通过真实 1.10f MPQ 双客户端
  gate：双方共享 `assassintrap` 控制实体（owner=施法者、skill=257）和附着的
  `blade creeper` 导弹（skill=257、missile=392、damageLevel=20），且
  `animationFallback=false`。
- gate 同时检查控制实体与导弹不能只在客户端本地出现，并通过持久导弹的 observer
  reconnect 子集/删除一致性检查；本次重连没有复活已删除实体。
- 现有 `AssassinSkillSpecializationTest` 已覆盖 ECS 层起点→目标点→返回、附着跟随、
  NextHit 去重和控制实体删除；本次补的是真实 MPQ/双客户端入口。运行日志仍有
  `Actioneer Unsupported srvdofunc(44)` 警告，但原生 keyframe 已触发且未启用 fallback，
  该警告后续需单独清理。
- [ ] 墙体/不可行走终点、逐目标伤害与持续时间到期后的控制实体/导弹清理，仍需独立
  真实场景验收；下一项优先 Charged Bolt Sentry(261)。

### 2026-10-02 Assassin Charged Bolt Sentry(261) 真实 MPQ gate

- `headlessAreaSkill -PareaSkill=261 -PareaTimeout=25` 已通过真实 1.10f MPQ 双客户端
  gate：双方共享 `assassintrap` 控制实体（owner=施法者、skill=261），并共享至少两枚
  带位置的 `sentrychargedbolt` burst 导弹（missile=495、skill=261、damageLevel=20）。
- `animationFallback=false`；observer reconnect 通过活动导弹子集/删除一致性检查，短寿命
  charged-bolt 在断线期间自然过期时没有复活旧实体。
- 夹具现在把真实 Blood Moor 怪物放到 sentry 搜索半径内，避免“陷阱已创建但没有目标”
  的假失败；Actioneer 已把合法 SrvDo044/045 放置/攻击回调列为 no-op，不再输出
  `Unsupported srvdofunc`，但 AssassinSentry 的 AI fallback 警告仍需后续清理。
- [ ] 逐目标伤害、完整 `calc1` burst 数量/路径、墙体/null-hit、陷阱射击预算和控制实体
  到期清理仍需独立验收；下一项优先 Wake of Fire Sentry(262)。

### 2026-10-02 Assassin Wake of Fire Sentry(262) 真实 MPQ gate

- `headlessAreaSkill -PareaSkill=262 -PareaTimeout=15 --no-daemon` 已通过真实 1.10f
  MPQ 双客户端 gate：owner/observer 共享 `assassintrap` 控制实体（skill=262、owner=施法者），
  以及同一权威 maker（missile=517）和两枚 `wake of destruction` 子导弹（missile=518），
  共享导弹实体为 `[134,135,136]`，双方历史类型为 `[517,518]`。
- gate 要求 maker 到达目标后确实生成两条不同位置的火焰波，而不是只看到陷阱落地；
  observer reconnect 通过活动导弹子集/删除一致性检查（重连时仅保留活动实体，两个短寿命波
  已过期，`stale=false`）。
- Blood Moor 夹具把目标放到陷阱落点并移除动态碰撞 footprint，同时清除其他预置怪，避免
  `AssassinTrapSystem` 的 nearest-hostile 竞争和静态阻挡把 gate 误判为技能失败；这是测试夹具
  约束，不改变生产技能行为。
- 本次运行 `animationFallback=true`：当前旧客户端 COF 没有在 2 秒窗口内触发 262 的
  `SkillDoEvent`，测试随后调用同一服务端 dispatch 继续验证真实 MPQ 导弹/同步/重连路径；
  该 fallback 仍是后续动画门槛，不能宣称客户端 keyframe 已完成。
- [ ] 逐目标火焰伤害、两波间隔/方向/持续时间、墙体/null-hit、陷阱 shot budget、控制实体
  到期及 `AssassinSentry` AI fallback 仍需独立验收；下一项进入 Inferno Sentry(272)。

### 2026-10-02 Assassin Inferno Sentry(272) 真实 MPQ gate

- `headlessAreaSkill -PareaSkill=272 -PareaTimeout=15 --no-daemon` 已通过真实 1.10f
  MPQ 双客户端 gate：双方共享 `assassintrap` 控制实体（skill=272）和至少两枚
  `inferno sentry` channel 导弹（missile=523、skill=272、damageLevel=20），本次共享
  实体为 `[134,135]`。
- 目标被放在陷阱前方，测试夹具清除其他 Blood Moor 预置怪，确保 SrvDo095 的持续通道
  锁定同一目标；这只影响 headless 准备，不改变生产 AI 或伤害逻辑。
- observer reconnect 通过：断线期间一枚短寿命通道导弹过期，重连只保留活动实体
  `active=[134]`，`expiredDuringReconnect=1`，`stale=false`。
- 本次运行 `animationFallback=true`；262/272 的旧客户端 COF/keyframe 仍未在观察窗口内
  自动派发 SkillDoEvent，当前 gate 使用同一服务端 dispatch 验证真实导弹/同步/重连路径。
- [ ] 通道持续时间、pulse 间隔/方向、逐目标火焰伤害、墙体/null-hit、shot budget、控制
  实体到期和 AI fallback 仍需专项验收；下一项进入 Death Sentry(276)。

### 2026-10-02 Assassin Death Sentry(276) 真实 MPQ gate

- `headlessAreaSkill -PareaSkill=276 -PareaTimeout=15 --no-daemon` 已通过真实 1.10f MPQ
  双客户端 gate：双方共享 `assassintrap` 控制实体（skill=276）和 `corpseexplosion`
  视觉导弹（missile=641、skill=276、damageLevel=20）。
- 夹具创建 Blood Moor 原生尸体并将其置于陷阱附近；gate 明确检查 owner/observer 两端
  尸体均为死亡、生命为 0 且带 `CORPSE_NOSELECT`，因此不是只生成一个落地陷阱的假阳性。
- observer reconnect 通过：corpseexplosion 视觉实体为持久效果，重连仍保留活动实体
  `active=[136]`，`expiredDuringReconnect=0`，`stale=false`。
- 本次运行 `animationFallback=true`；旧客户端 COF/keyframe 未在窗口内自动派发 276 的
  `SkillDoEvent`，后半段使用同一服务端 dispatch 验证真实尸体事务/导弹同步/重连路径。
- 日志仍有两个 `DeathSentry ... AI_FALLBACK -> GenericMonster` 警告，需单独清理；[ ]
  corpse explosion 实际目标掉血范围、重复尸体消费、墙体/null-hit、shot budget 和控制实体
  到期仍需专项验收。下一项：Blade Shield(277)。

### 2026-10-02 Multiple Shot / Strafe 真实多目标 gate

- 已按本地 D2MOO `SKILLS_SrvDo008` 核对 Multiple Shot 的整数 lane 几何；本轮没有修改
  `ServerSkillSystem` 或 `MissileCollisionSystem` 的生产行为。
- 修复 headless fixture 的动态碰撞初始化竞态：lane 目标用原子去 `Size`/停用物理体/定位，
  并在首个网络 baseline 后复核定位，避免两个 lane 重叠。
- 为规避 D2MOO `NextHit` 接触即写入 `JUSTHIT` 与 5% miss 造成的随机 gate 抖动，cast 前
  只固定测试 RNG 通过序列，不改变命中公式。
- 已通过真实双客户端 MPQ：Multiple Shot(12) 多目标、Strafe(26) 多目标；两者均确认
  owner/observer 共享多枚导弹并至少两个不同 lane 目标掉血。
- 本轮提交范围为 `server/d2gs/src/main/java/com/riiablo/server/d2gs/D2GS.java`、
  `D2GSHeadlessClient.java` 及本文件/技能矩阵；核心测试仍存在既有
  `AmazonSkillSpecializationTest` corpse-pierce 失败，未把它归因于本轮 gate。

### 2026-10-01 Lightning Bolt 真实 MPQ 接手摘要

- `headlessAmazonMelee` 已纳入 Lightning Bolt(20)，并记录 `lightningjavelin` 的
  `Range/Vel/LastCollide/pSrvDmgFunc`；飞行技能使用足够的 swept path 观测窗口。
- 真实 1.10f MPQ gate 已通过：owner/observer 目标生命 `1000000 -> 999781`，实体
  `133 / missile=205` 创建并前进，observer 重连后仍恢复 `999781`。这次通过包含实际
  掉血和重连证据，不仅是导弹创建。
- 纯 ECS `lightningBoltSweptCollisionAppliesSnapshotDamage` 和整组
  `AmazonSkillSpecializationTest` 均通过；未修改生产伤害公式。
- 本轮提交 `fc231fae` 已推送到 `origin/master`；新增的 `lightning-bolt-current.log`
  仍是未跟踪诊断产物，不属于提交。
- 本轮新增的测试文件属于当前 Chat；其他 agent 的 `ItemEntry.java`、`AssassinTrapSystem.java`
  和 `StatFormatterTest.java` 以及未跟踪日志/截图均未纳入本轮提交。

### 2026-09-30 技能移植对照进度

#### 接手摘要（必须先读）

- 当前分支为 `master`；本轮提交后用 `git log -1 --oneline` 和
  `git ls-remote origin refs/heads/master` 刷新并确认 HEAD/远端 SHA。
- dark-magic manifest 当前有 43 个 exact-ID、16 个行为族；riiablo 已为 43/43
  建立行为对照入口或测试映射，但 dark-magic 自身条目仍全部是 `partial` evidence，
  因此不能把“已建立对照”写成“技能已完成”。
- 版本边界固定为：dark-magic 仅借鉴行为族、测试组织和 fail-closed/生命周期断言；
  数值、函数号、状态时序和技能表必须重新以本地 D2MOO 1.10f、1.10f MPQ，必要时
  The Phrozen Keep 为准。
- 当前最高优先级是 Amazon 30 行技能的真实 1.10f MPQ、失手/死亡/重连验收；Amazon
  没有 dark-magic exact-ID 配置，不能把其他职业的行为族直接套用到 Amazon。新的
  `headlessAmazonMelee` 已完成 Jab、Power Strike、Impale、Charged Strike、Fend、Lightning
  Strike 的真实双端伤害/重连门槛，其中 Lightning Strike 还确认链式导弹；本轮又补齐
  Impale 标枪 `quantity` 的真实 MPQ Calc2 消耗和 observer 重连保持；本轮又完成非堆叠
  `spr` 的真实 Calc3 durability `20 -> 19` 和重连保持。其余 Amazon exact-ID 行仍未
  完成。Jab 的真实目标死亡和高防 miss gate 也已通过。
- 最近已完成的真实门槛：Sorceress Fire Ball/Nova、Necromancer Poison Nova、
  Paladin 20 Aura 清单、Conviction/Holy Freeze hostile 过滤、Druid vine/尸体
  recycler 的部分双客户端流程。详细矩阵见 `docs/skill-porting-matrix.md`。
- 工作区中以下文件曾出现在 `git status`，接手时不得盲目回滚或覆盖：
  `core/src/main/java/com/riiablo/codec/excel/ItemEntry.java`、
  `core/src/main/java/com/riiablo/engine/server/AssassinTrapSystem.java`、
  `core/src/main/java/com/riiablo/item/ItemReader.java`、
  `core/src/test/java/com/riiablo/attributes/StatFormatterTest.java`、
  `core/src/test/java/com/riiablo/engine/server/AmazonSkillSpecializationTest.java`。
  逐个用 `git hash-object` 核对后，只有 `StatFormatterTest.java` 有实际内容差异，
  只是末尾空行；其余四个文件的工作区 blob 与 HEAD 相同，属于 Git 状态/时间戳假脏。
  `game.log`、`skill-viewer-window*.png` 和 `tools/skill-viewer/logs/` 是之前技能查看器/调试
  运行生成的未跟踪产物，不属于技能实现提交。
- 本轮 Amazon Power/Charged Strike 生命周期提交为 `8e74cb7d`；本轮真实 MPQ
  Amazon 六项近战 gate 与 fixture 提交为 `9b55bcc6`、`586be648`，Jab death gate 提交为
  `d40a6731`、`5bb65a7b`，相关专项运行均通过。Git credential helper
  偶尔输出 lock 警告，但应以
  `git ls-remote origin refs/heads/master` 返回的 SHA 为准。
- 本轮真实 MPQ Impale 非堆叠 Calc3 durability gate 已提交为
  `24b6fd9ca950756d63b56fd15b52c5c2731b11db` 并推送到 `origin/master`；`spr` 从
  durability `20 -> 19`，`jav` quantity `16 -> 15` 均已回归通过。

#### 接手后的唯一下一步

先不要重复 Paladin 已通过的矩阵。读取 `docs/skill-porting-matrix.md` 的 Amazon 段，
检查当前 Amazon 未提交修改的差异，在不触碰其他 agent 文件的前提下继续补：
真实 MPQ Jab/Impale/Fend/Power Strike/Charged Strike、失手/死亡/重连、弹药/耐久和
ToHit/SrcDam/Calc 边界；当前 ECS 耐久/死亡边界已完成，真实 MPQ
  `headlessAmazonMelee` 六项成功 gate、Impale quantity/Calc3 durability、Jab death 和高防 miss gate 已通过，下一步是弹药边界和剩余 Amazon 技能。每轮实现改动专项测试、提交并推送 `origin/master`，纯文档或
极小修改可不编译但仍需提交推送。

本轮后续已完成：Immolation Arrow(27) 与 Guided Arrow(22) 的真实 1.10f MPQ
双客户端导弹 gate。前者验证 `immolationfire` 持续区域、tick 伤害和删除一致性；后者
验证 `guidedarrow` 锁定目标、owner/observer 共享、箭袋消耗/恢复和重连。下一项转入
Multiple Shot/Strafe 的多目标、穿透与命中去重，随后继续其余 Amazon exact-ID 行。

#### 历史 Druid 记录（保留，当前接手优先级以“接手摘要”为准）

- Druid Firestorm 已补充 `SrvDo117` 多流硬点协同快照回归；与此前 Fissure/Volcano
  子导弹协同测试一起通过四个 Druid 区域技能专项类。
- Druid `SrvDo114/115/119` 召唤物等级已改为原生 `Skills.txt Calc2`，新增 Raven 等级
  回归，避免高等级角色把召唤物错误提升到角色等级。
- Druid Dire Wolf 的跨区跟随和技能来源元数据已有专项回归；本轮已完成 spirit aura
  的 `SumSkill` 关联行解析、owner/ally source-owned 投影及范围/删除撤销，不直接覆盖
  其他 agent 的未提交修改。
- 本轮提交已推送到 `origin/master`；Amazon、Assassin 及本地日志等其他未提交修改
  仍由原工作者保留，未纳入本轮提交。
- 本轮新增的 Druid aura 代码和专项测试已通过定向编译/测试，提交为 `bc748f2f` 并已推送
  到 `origin/master`；下一项是
  Druid vine 的真实 MPQ/双客户端门槛，随后进入其他职业尚未完成的 exact-ID 行为注册
  与四层验收。本轮已完成 vine 的 SumSkill/状态核对与等级传递测试，尚未做真实客户端
  导弹命中验收。
- 本轮继续完成三种 Druid vine 的真实 MPQ/双客户端 summon、`VINE_BEAST` 和 observer
  reconnect 门槛；`Plague Poppy` 通过 Vine Attack/trail，`Cycle of Life`/`Vines` 已确认
  走 D2MOO `SrvSt63_Corpse_VineCycler` 非 projectile 分支；本轮已移植 skill-start 的
  尸体选择、`CORPSE_NOSELECT` 同步和 owner missile 创建，补齐 `CycleOfLife` AI 别名，
  并加入 `DruidVineCorpseCyclerTest` 与 231/241 双客户端 fixture。真实尸体最终消费
  生命周期仍待继续对照，
  trail 本身也没有直接 poison 字段，因此不把毒素命中写成已完成。相关实现不覆盖
  Amazon、Assassin 或其他 agent 的未提交修改。

- 2026-09-30 继续核对 Druid vine 尸体生命周期：D2MOO `SrvSt63` 只设置
  `CORPSE_NOSELECT`、刷新尸体并创建 `SrvMissileA`，没有尸体删除/隐藏/消费调用；
  `MISSMODE_SrvDo33_VineRecyclerDelay` 只按 `Range=47` 帧倒计时并最终移除延迟导弹。
  riiablo 保留 corpse 实体和 `usable`，由零速度导弹的原生 lifetime 在第 47 帧结束，
  语义已锁定在 `DruidVineCorpseCyclerTest`。同时补齐 `pCltDoFunc=51`：第 20 帧创建
  `recycler vine`，第 45 帧创建 `recycler explosion`，两者均为客户端表现实体；
  `DruidVineRecyclerPresentationTest.vineRecyclerUsesNativeDelayedClientChildren` 锁定真实
  1.10f 行号和参数。当前剩余项是实际客户端资源观感及 poison trail 命中，不再把尸体
  删除作为待办。

- 同轮补齐 `plague vines trail` 的 D2MOO `SrvHit50`：该函数只在导弹总帧数开始的
  `Hit delay=15` 帧内允许接触，之后拒绝命中，不直接造成毒素/伤害，也不复用
  `SrvHit16` 的减速状态；trail 总帧数按 `Range + level*LevRange` 恢复。新增
  `DruidVineCorpseCyclerTest.plagueVinesTrailUsesNativeHitDelayWindowWithoutImplicitDamage`
  锁定这些 1.10f 数据和边界。

- 分支：`master`
- 当前功能提交：`5bb65a7bc4f2821aca3bda0ec8f421cc63ab90fe`（已与 `origin/master` 一致）
- 本轮开始基线：`e6e377b9`（Paladin hostile Aura ECS 过滤矩阵）
- 远程：已推送 `origin/master`；Git credential lock 警告出现但远端 SHA 已确认更新
- 工作区：本轮纳入文件已提交；Amazon、Assassin、日志和截图等其他未提交修改仍保留
- 总体对齐进度：约 70%（详见路线图）
- 第一章最小可玩闭环：约 79%

## Git 交接入口

新 Chat 应首先阅读 [`codex-handoff-2026-09-20.md`](codex-handoff-2026-09-20.md)。该文件
记录当前 HEAD、远程一致性、未跟踪日志处理规则、最近已完成模块、验证命令和唯一下一步，
用于避免上下文压缩后重复验证或回退已有工作。

## 最近更新（2026-09-20，交互/战斗/角色资源/Help）

- 点击选择、自动靠近、交互距离、近战与 Shift 施法路径已统一；交易/箱子面板移动关闭，
  传送点面板离开范围关闭。
- 体力、魔力、跑步状态、怪物追击/命中/生成、Fallen Shaman 导弹回收及城镇出口预加载已修正。
- Automap 探索恢复和裸地道路显示已补齐；HUD、角色属性、技能状态、升级反馈及按钮位置已对齐。
- 640x480 Help Overlay 已加入 `H`/`Esc` 控制并适配宽屏居中。
- 核心编译与 Help/UI 定向测试通过；完整核心测试仍有 109 项既有资源/数据类失败，详见当日交接。
- `game.log` 与 `_tmp_run.ps1` 仅供本地诊断，不提交、不删除。

## 最近更新（2026-09-15，Automap/UI）

- Options 子菜单 OptionRow 已统一为父菜单的 `font16` 与 24px 行高。
- 城镇 Automap 首次进入即揭示完整 Zone；野外/地下仍按 RoomEx 探索揭示。
- 小地图通过半尺寸视口实现原生内容 1/2 比例，相机 zoom 不再额外放大；玩家、队友和
  无 DC6 cell 的 NPC 使用统一投影的几何回退，有原生 cell 时避免重复绘制。
- 真实 1.10f `:desktop:offscreenAutomapDc6` 与 Automap 专项测试通过；全量核心测试中
  仅既有缺失 `test/*.d2s/.d2i` 资源项失败。

本轮修改仅限 Automap/UI 文件和测试，未改战斗、地图生成或网络协议；`84e052cf` 已推送
到 `origin/master`。历史未跟踪 `.log` 测试产物保持不变。

## 最近更新（2026-09-15，攻击动画防闪烁）

- `ModeChangeEvent.restart` 将连续攻击的同模式强制重启与真实 COF 切换区分开。
- 客户端收到同模式 restart 时保留现有 DCC 层，仅重置动画帧，不再因卸载/异步加载造成
  闪烁；服务器关键帧和伤害时序不变。
- 本地玩家在 NU↔攻击模式切换时保留已加载 DCC，已加载路径不重复 `load`，降低首次切换
  后的空白帧风险；非本地实体继续按原策略回收资源。
- `SequenceHandlerTest` 已覆盖该语义并通过；本轮提交为 `dd2b9af4`，已推送到
  `origin/master`。

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
- 对照 D2MOO 的技能协同公式后，修正 Fissure/Volcano 控制器生成子导弹时丢失施法者
  硬点的问题；子导弹现在沿用 `baseSkillLevel` 读取 `EDmgSymPerCalc`，并由
  `DruidVolcanoIntegrationTest.eruptionChildSnapshotsDruidFireSynergyFromOwnerHardPoints`
  锁定 Volcano 的 Eruption 协同快照。
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
  目标 Drain 和原生递减曲线回血；2026-10-15 按上游 `2bc0c20` 订正受击分担为扣主人生命，
  本机 1.10f `Param5=10`，不再将主人生命覆盖到石魔；Iron 聚合
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

### 2026-10-01 Amazon 可恢复箭袋耗尽边界（本轮完成）

- [x] 按 D2MOO `sub_6FD11340 -> sub_6FC51310 -> sub_6FC4A350` 核对：技能消耗把
  `quantity` 置 0，并为 `item_replenish_quantity` 注册恢复事件，不直接删除物品。
- [x] `ServerSkillSystem.consumeRangedAmmo` 对带 `item_replenish_quantity > 0` 的
  `BOWQ/XBOQ` 保留 quantity=0 的箭袋；普通箭袋继续沿用耗尽移除行为，避免扩大本轮
  行为变化。
- [x] 新增 `NativeAmazonAmmoReplenishTest`：一次消耗后物品仍在装备槽，按原生
  `max(125, 2500/rate + 1)` 帧恢复，并可继续作为弓的匹配弹药。
- 验证：`:core:test --tests com.riiablo.engine.server.NativeAmazonAmmoReplenishTest`
  通过。

当前下一项：把真实 1.10f MPQ 双客户端 bow gate 推广到 Cold Arrow，并继续审计 Amazon
  剩余导弹/区域行为。

### 2026-10-01 Amazon Fire Arrow 真实 MPQ 弹药 gate（本轮完成）

- [x] 新增 `headlessAmazonBow`：真实 1.10f MPQ 双客户端使用 `sbw` + `aqv` 施放
  `Fire Arrow(7)`，确认原生导弹在 owner/observer 两端出现。
- [x] gate 锁定箭袋 `quantity 1 -> 0`、`BOWQ` 类型和可恢复物品保留；observer 断开后
  重连，权威箭袋恢复为 `quantity=1,replenish=100`，未丢失或被错误删除。
- [x] 发现 `item_replenish_quantity` 当前 D2S 写入路径不会持久化，因此 fixture 在
  连接后的权威模拟线程注入该 stat；这只用于验证运行时恢复链，不宣称存档序列化已完成。
- [x] Fire Arrow 导弹命中当前 headless 目标的碰撞/伤害已通过；gate 等待实际目标
  生命下降，并在 owner/observer 两端观察到一致结果。
- 验证：`:server:d2gs:compileJava`、`:server:d2gs:headlessAmazonBow
  -PamazonBowSkill=7 -PamazonBowTimeout=6` 均通过。

当前下一项：把已通过的 Fire Arrow 真实命中门槛推广到其他 Amazon 导弹/区域技能，
并继续核对装备切换、D2S 弹药恢复属性持久化和其他职业的对照项。

### 2026-10-01 Amazon Cold Arrow 真实命中与减速 gate（本轮完成）

- [x] `headlessAmazonBow -PamazonBowSkill=11` 允许真实 MPQ 双客户端施放
  `Cold Arrow(11)`，并确认 owner/observer 共享原生 `coldarrow` 导弹。
- [x] gate 等待目标生命实际下降、箭袋 `quantity 1->0`，且两端都收到原生
  `COLD(state=11)`；未要求 `FREEZE`，符合 D2MOO 的 Cold Arrow 冷减速而非冻结语义。
- [x] observer 断开重连后箭袋恢复为 `quantity=1,replenish=100,BOWQ`，状态快照无陈旧
  数据；生产冷伤害/状态链未被测试桥接改写。
- 验证：`:server:d2gs:compileJava`、`:server:d2gs:headlessAmazonBow
  -PamazonBowSkill=11 -PamazonBowTimeout=12` 均通过。

### 2026-10-01 Amazon Ice Arrow Boss 冷冻规则 gate（本轮完成）

- [x] `headlessAmazonBow -PamazonBowSkill=21` 通过真实 MPQ 双客户端 Ice Arrow 命中、
  箭袋消耗/恢复与 observer reconnect。
- [x] 依据 D2MOO `SUNITDMG_ApplyFreezeState`，Boss 目标的 `SrvDmgFunc=2` 冻结包被
  转换为 `COLD(state=11)`，两端均未建立 `FREEZE`；这不是把普通怪物的冻结规则改成
  冷减速，而是覆盖原生 Boss/Unique/Hireling 例外。
- [x] bow gate fixture 仅移除目标的动态 footprint，避免玩家贴近时测试网格把目标搬走；
  生产移动与碰撞系统未改变。
- 验证：`:server:d2gs:compileJava`、`:server:d2gs:headlessAmazonBow
  -PamazonBowSkill=21 -PamazonBowTimeout=12`、`:core:test --tests
  com.riiablo.engine.server.AmazonSkillSpecializationTest` 均通过。

### 2026-10-01 Amazon Exploding Arrow 真实范围子导弹 gate（本轮完成）

- [x] `headlessAmazonBow -PamazonBowSkill=16` 接受 `Exploding Arrow(16)`，日志确认真实
  MPQ 行：`srvMissile=explodingarrow`、`pSrvHitFunc=4`、`HitSubMissile=explodingarrowexp2`、
  `EType=fire`、技能火焰范围 `2..6`。
- [x] 父箭命中后 `explodingarrowexp2` 子导弹在 owner/observer 两端以同一实体 ID 共享，
  爆炸范围目标实际掉血（`1,000,000 -> 999,850`），因此门槛不可能只由父箭创建通过。
- [x] 箭袋耗尽/可恢复属性和 observer reconnect 通过；本次验证使用权威线程注入的
  `item_replenish_quantity`，不宣称 D2S writer 已完成该属性持久化。
- 验证：`:server:d2gs:compileJava`、`:server:d2gs:headlessAmazonBow
  -PamazonBowSkill=16 -PamazonBowTimeout=15` 均通过；未覆盖用户已有 Amazon 技能公式。

当前下一项：继续按矩阵核对 Amazon 其余导弹（Freezing/Immolation/Multiple/Guided）及
穿透、爆炸半径和多目标伤害，再转入其他职业的 dark-magic 对照项。

### 2026-10-01 Amazon Freezing Arrow Boss 范围子导弹 gate（本轮完成）

- [x] `headlessAmazonBow -PamazonBowSkill=31` 接受 `Freezing Arrow(31)`，真实日志确认
  `srvMissile=freezingarrow`、`pSrvHitFunc=4`、`HitSubMissile=freezingarrowexp3`、
  `EType=cold`、`cold length=50`。
- [x] owner/observer 共享父箭和 `freezingarrowexp3` 子导弹，Boss 目标生命从
  `1,000,000` 降到 `999,644`，两端收到 `COLD(state=11)+SHATTER(state=107)`，且 gate
  明确拒绝 `FREEZE(state=1)`；箭袋消耗/恢复和 observer reconnect 通过。
- [x] 修复普通怪物致死冻结事件顺序：`resolveColdShatterDeath` 不再把冻结+冷伤害强制
  转成 SHATTER；现在沿 D2MOO 规则保留 `FREEZE` 至 DeathEvent，SHATTER 只由冷包应用
  时的 `ApplyColdState` 决定。`FreezingArrowDeathOrderTest` 已通过。
- 验证：`:server:d2gs:compileJava`、`:server:d2gs:headlessAmazonBow
  -PamazonBowSkill=31 -PamazonBowTimeout=15` 通过；core 定向测试命令已运行但因上述
  既有失败退出。

当前下一项：继续 Immolation Arrow 和 Multiple/Guided Arrow 的真实导弹门槛。

### 2026-10-01 Amazon Multiple Shot / Strafe（本轮完成）

- [x] D2MOO `SrvDo008` 与 `SrvSt08/SrvDo012` 已完成本轮源码对照；Multiple Shot
  lane/中心组和 Strafe Param1/2/3 目标流均以 1.10f MPQ 为准，没有用 dark-magic
  数值覆盖 Amazon 生产公式。
- [x] `headlessAmazonBow -PamazonBowSkill=12` 通过真实双客户端多箭、共享实体、
  目标掉血、单次箭袋消耗和 observer reconnect。
- [x] `headlessAmazonBow -PamazonBowSkill=26` 通过真实双客户端 Strafe 多箭目标流、
  单次箭袋消耗、共享实体、目标掉血和 reconnect；续发 keyframe 在零数量可恢复箭袋
  状态下不会再次触发通用“无弹药”拒绝。
- [x] 新增 `-PamazonBowMultiTarget=true` 显式真实多目标 gate：Multiple Shot(12)
  的 21 箭同一 volley 在两个不同 lane 目标上实际掉血；Strafe(26) 的连续 keyframe
  目标流在两个不同目标上实际掉血；owner/observer 的掉血集合一致，并继续验证共享
  导弹、单次箭袋消耗和 observer reconnect。目标 baseline 与生产 map ray 均先做硬断言。
- [x] 新增纯 ECS Multiple Shot 墙体门槛：每枚测试导弹安装真实 `MapWrapper` 后，
  `CollideType=3` 的 swept 路径遇到 `FLAG_BLOCK_JUMP` 会停止，墙后目标不掉血；这
  只确认生产 map collision，不宣称 MPQ 双客户端墙体 gate 已完成。
- [x] 新增 `-PamazonBowWallGate=true` 真实 MPQ 墙体 gate：Multiple Shot(12) 和
  Strafe(26) 都在开放目标实际掉血的同时，让真实地图扫描出的墙后目标在 owner/
  observer 两端保持满血；墙后目标先通过 owner→target 生产 ray 阻挡复核，且不进入
  Strafe 的可见目标序列。
- [x] headless COF 缺少完整 Strafe 后续关键帧时，`headlessDispatchAmazonMelee`
  只重放剩余 `AnimDataKeyframeEvent`，仍进入 `ServerSkillSystem.spawnStrafe`，不伪造
  一次性整轮导弹。
- [x] `Actioneer` 已显式接收 `srvstfunc=8` / `srvdofunc=12` 并委派到权威
  `SkillDoEvent`；本轮真实 gate 不再出现对应 unsupported warning。旧客户端动画、
  多目标穿透/墙碰撞的真实画面门槛仍待后续。当前工作区其他 agent 的未提交修改未动。

验证：`:core:test --tests com.riiablo.engine.server.AmazonSkillSpecializationTest
--tests com.riiablo.engine.server.NativeAmazonSkillMatrixTest --no-daemon`、
`:server:d2gs:headlessAmazonBow -PamazonBowSkill=12 -PamazonBowTimeout=15 --no-daemon`、
  `:server:d2gs:headlessAmazonBow -PamazonBowSkill=26 -PamazonBowTimeout=15 --no-daemon`；
  多目标：`:server:d2gs:headlessAmazonBow -PamazonBowSkill=12 -PamazonBowTimeout=25
  -PamazonBowMultiTarget=true --no-daemon`、`:server:d2gs:headlessAmazonBow
  -PamazonBowSkill=26 -PamazonBowTimeout=30 -PamazonBowMultiTarget=true --no-daemon`。
  纯 ECS 墙体：`:core:test --tests
  com.riiablo.engine.server.AmazonSkillSpecializationTest.multipleShotStopsAtNativeMapBarrierBeforeTarget
  --no-daemon`。
  MPQ 墙体：`:server:d2gs:headlessAmazonBow -PamazonBowSkill=12
  -PamazonBowTimeout=25 -PamazonBowWallGate=true --no-daemon`、`:server:d2gs:headlessAmazonBow
  -PamazonBowSkill=26 -PamazonBowTimeout=30 -PamazonBowWallGate=true --no-daemon`。

### 2026-10-01 Amazon Lightning Bolt(20) gate 诊断记录

- [ ] 按 D2MOO `MISSMODE_SrvDmg12_LightningJavelin` 尝试接入真实双客户端门槛；两次运行
  均创建 `lightningjavelin` 权威导弹（runtime missile id 205），但目标防御降至 1 后
  仍无双方生命下降，因此不能标记完成。
- [x] `AmazonSkillSpecializationTest.lightningBoltUsesNativeLightningJavelinDamageSnapshot`
  已通过，确认 `SrvDmgFunc=12`、`DmgCalc1=dl12`、闪电伤害快照和 ToHit 标志均正确；
  当前待诊断范围收窄到快照之后的 swept collision、命中判定或最终伤害结算。
- [x] 已撤回这次未通过的 gate 扩展，没有改动生产碰撞、ToHit 或伤害公式；下一步先诊断
  `lightningjavelin` 的 swept collision、命中判定和伤害快照，再重新决定是否接入真实 gate。

### 2026-10-01 Amazon Lightning Fury(35)（本轮完成真实分裂 gate）

- [x] 已按 D2MOO `MISSMODE_SrvHit20_LightningFury` 核对 `HitPar[0]/HitPar[1]`、
  `AuraFilter=0xA783`、`HitSubMissile=furylightning` 和 `NoAura/阻挡/敌对目标`筛选。
- [x] `headlessAmazonMelee -PamazonMeleeSkill=35 -PamazonMeleeWeapon=jav`
  通过真实双客户端根 javelin、第二/第三目标 `furylightning` 子导弹共享、三目标掉血和
  observer reconnect；重连时复用权威实体 baseline，验证三个目标生命与子导弹不恢复
  陈旧数据。
- [x] `isAmazonMeleeSkill` 已纳入 Lightning Fury，生成的 `jav` fixture 可直接用于
  后续 javelin 技能 gate。
- [x] 真实夹具增加同 RoomEx、5 格距离的第二个和第三个 durable monster；gate 要求至少
  两枚 `furylightning` 在 owner/observer 以同一实体 ID 出现，并要求两个分裂目标实际掉血。
- [x] `MissileCollisionSystem` 让子导弹继承根导弹共享命中集合，避免子导弹从根命中点
  出生时在第一帧重复命中根目标；纯 ECS 仍保留范围/NoAura/阻挡/去重门槛。
- [x] 当前 level-20 真实 gate 已覆盖根目标、第二目标和第三目标，并确认至少两枚共享
  `furylightning`；第三目标使用同一可见通道的 10 格纵向位置以避免地图静态阻挡。
- [x] 真实 MPQ gate 已支持 `-PamazonMeleeSkillLevel`，并通过 Lightning Fury 等级 1、
  10、20 三档；夹具按原生 `HitPar[1]/Calc1` 创建目标，等级 1 精确要求 2 枚、等级 10/20
  精确要求 11 枚共享 `furylightning`，且所有分裂目标在 owner/observer 两端掉血并通过
  重连生命检查。
- [x] 分裂生产路径抽出最近距离优先排序 helper，并由 ECS 测试锁定最近、次近、最远顺序。
- [x] 运行时已记录 `lightningfury`/`furylightning` 均为 `CollideType=3`、
  `Collision=false`、`LastCollide=true`，并由 Amazon 数据测试锁定；此前的墙后候选点实际
  位于 11 个目标之后，只证明了分裂上限排除，不能作为墙体 gate。
- [x] 真实 MPQ gate 通过 D2GS 应用线程桥接读取生产瞬间的目标 ID/距离快照，确认 11 个
  唯一目标按同 tick 距离非递减选中；动态碰撞后续重定位不再被误当成生产顺序证据。
- [ ] 墙后目标的真实 MPQ gate 仍待后续，不应将本轮等同于完整 Lightning Fury 目标选择矩阵。
  其他 agent 的未提交修改仍未动。

验证：`:core:test --tests com.riiablo.engine.server.AmazonSkillSpecializationTest
--tests com.riiablo.engine.server.NativeAmazonSkillMatrixTest --no-daemon`；
`:server:d2gs:headlessAmazonMelee -PamazonMeleeSkill=35 -PamazonMeleeWeapon=jav
-PamazonMeleeTimeout=15 --no-daemon`。

### 2026-10-01 Pierce gate handoff

- [x] `amazonBowPierceGate` 已接入 Gradle/Headless client；测试弓只在该 gate 注入
  `item_pierce=100`，避免把随机 Pierce 预滚误当作实现缺陷或成功证据。
- [x] Strafe(26) 已通过真实 MPQ 双客户端严格 gate：同一导弹的生产遥测记录第一目标
  `132`、第二目标 `133`，owner/observer 均看到第二目标实际掉血，箭袋、共享导弹和
  reconnect 仍通过。
- [x] Multiple Shot(12) 已通过严格同一导弹门槛：生产遥测记录第一目标 `132`、第二
  目标 `133`，owner/observer 两端均观察到第二目标实际掉血；`pairCount` 已非 0。
- 本轮新增生产只读桥接：`MissileCollisionSystem.headlessPierceState` 与
  `D2GS.headlessAmazonPierceState`；仅服务 headless gate，不改变生产伤害/概率公式。
- `headlessPierceState` 末字段现在明确表示该同一导弹命中第二目标时
  `pierceEnabled` 是否为真；Multiple Shot 与 Strafe 均已满足同一导弹 Pierce 证据，
  两目标分别掉血仍不能单独替代该断言。
- 交接回归：排除既有 corpse-pierce 失败后，Multiple Shot 墙体、Strafe 目标筛选、
  Guided/Strafe 唯一目标及 Native Amazon 矩阵共 6 项通过；不得将该既有失败归因于
  本轮穿透 gate。

验证命令：
`:server:d2gs:headlessAmazonBow -PamazonBowSkill=12 -PamazonBowTimeout=20
-PamazonBowMultiTarget=true -PamazonBowPierceGate=true --no-daemon`、
`:server:d2gs:headlessAmazonBow -PamazonBowSkill=26 -PamazonBowTimeout=20
-PamazonBowMultiTarget=true -PamazonBowPierceGate=true --no-daemon`。

### 2026-10-01 Amazon 不可恢复箭袋耗尽 gate（本轮完成）

- [x] 新增 `-PamazonBowAmmoGate=true`，使用真实 1.10f MPQ Fire Arrow、`quantity=1`
  且 `item_replenish_quantity=0` 的 `BOWQ` 箭袋；首发命中后箭袋被移除，空箭袋重试不
  创建导弹、不继续掉血。
- [x] gate 在第二次施法前等待权威 Actioneer 的首发动画序列结束，避免延迟 keyframe
  被误计入重试；observer 断开并重连后权威箭袋仍为空。
- 验证：`:server:d2gs:headlessAmazonBow -PamazonBowSkill=7
  -PamazonBowTimeout=15 -PamazonBowAmmoGate=true --no-daemon`。
### 2026-10-02 Sorceress Fire Ball(47) gate handoff

- [x] `headlessAreaSkill -PareaSkill=47` 真实 1.10f MPQ 双客户端通过：owner/observer
  共享 Fire Ball 父/爆炸子导弹实体，日志确认 `animationFallback=false`。
- 未改动 Sorceress 生产伤害或范围公式；本项只确认 D2MOO 父/子导弹生命周期和多人
  快照一致性。后续继续 Sorceress 复杂导弹或 Assassin 真实 MPQ gate。

### 2026-10-02 Sorceress Nova(48) gate handoff

- [x] 真实 1.10f MPQ 双客户端 Nova gate 通过，owner/observer 共享权威导弹，
  `animationFallback=false`。
- gate 已输出 `area_skill_dual_pass` 后才出现一次 observer socket close 异步异常，
  归类为测试清理噪声，不作为技能失败；生产公式未修改。
- Nova/Fire Ball/Native projectile 相关纯逻辑回归共 22 项全部通过；下一步转入
  Assassin 真实 MPQ gate 或继续 Sorceress 尚未覆盖的表现链。

### 2026-10-02 Assassin regression handoff

- [x] Assassin 行为层回归共 75 项通过：专项 30、武术 37、NativeTrapSystem 6、
  NativeTrapFireSystem 2。
- [x] 已建立 Assassin 专用真实 MPQ 双客户端入口；Fire Trauma(251)、Shock Field(256)、
  Blade Sentinel(257)、Charged Bolt Sentry(261)、Wake of Fire(262)、Inferno(272)、
  Death Sentry(276) 与 Blade Shield(277) 已分别进入技能 gate，仍不得把纯逻辑通过当成
  四层完成。

### 2026-10-02 Assassin Fire Trauma(251) air→ground gate

- [x] Assassin fixture 已通过严格双客户端 air→ground gate：共享 `bomb in air` (385)
  和 `bomb on ground` (386)，`animationFallback=false`。
- [x] reconnect 通过：已过期父实体不复活，活动地面实体正常恢复。
- [x] 严格爆炸/伤害 gate 通过：双方历史类型集合均为 `[385,386,387]`，且目标生命
  在 owner/observer 两端均下降。
- [ ] 仍需补一次性爆炸链的专用重连窗口及 owner 生命周期，尚不能宣称四层验收。

### 2026-10-02 Assassin Blade Shield(277) gate 交接

- [x] 已在 `server/d2gs/src/main/java/com/riiablo/server/d2gs/D2GSHeadlessClient.java`
  增加 Blade Shield 专用真实 MPQ 双客户端入口、近身耐久目标夹具、状态同步、目标掉血
  和 reconnect 断言；未改动其他 agent 的未提交文件。
- [x] 验证命令：`:server:d2gs:headlessAreaSkill -PareaSkill=277 -PareaTimeout=15
  --no-daemon`；退出码 0，日志包含 `area_skill_dual_pass skill=277`、两端
  `state=158`，以及 `area_skill_reconnect_pass skill=277 ... states=[158] stale=false`。
- [x] 结果：owner/observer 均观察到 `BLADESHIELD`，同一目标两端实际掉血检查通过，
  `animationFallback=false`；重连只恢复当前权威活动状态。
- [x] `AssassinSkillSpecializationTest` 新增并通过武器/受击方护甲耐久门槛；确认真实命中
  后分别走 4% weapon 与 10% armor 原生概率路径。
- [ ] 尚未完成周期脉冲墙体/null-hit、到期清理及真实视觉持续时间。下一位 agent 应先补
  这些 Blade Shield 边界，然后处理 262/272/276 的动画/keyframe 和
  `AssassinSentry` AI fallback。

### 2026-10-03 Death Sentry(276) 邻房顺序 gate（本轮完成）

- [x] `AssassinTrapSystem` 的 `corpseRoomRank` 继续使用 D2MOO 导出的 `pRoomsNear`
  顺序；headless 夹具新增 RoomEx pin，避免测试尸体被房间跟踪系统按近距离坐标改回当前房。
- [x] 真实 MPQ 双客户端 gate 在 seed=1、2、3 / difficulty=0 均通过：首项邻房尸体消费，
  第二具未消费；owner/observer 共享 Death Sentry 视觉。seed=1 邻房 `6 -> 1`，seed=2
  为 `8 -> 1`，seed=3 为 `13 -> 1`，均记录 `nativeTopology=true`。
- [x] 相关验证：`:core:test --tests com.riiablo.engine.server.AssassinSkillSpecializationTest`
  通过；`:server:d2gs:compileJava` 与三次真实 gate 均 BUILD SUCCESSFUL。
- [ ] 仍需继续 Death Sentry 的动画/keyframe、AI fallback 和 difficulty>0 地图覆盖；本轮
  `animationFallback=true` 仅表示 headless COF 缺关键帧，不作为视觉完成证据。
- 实现提交：`82616717`。
