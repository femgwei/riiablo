# D2MOO 寻路迁移续聊交接（2026-10-16）

用户要求保存进度、归档旧聊天并在新聊天继续。第二十一批已在新聊天完成；无需重做第二十/二十一批。

## 第五十批续接结果（优先于下方历史起点）

- NativePathUnitMetadata捕获真实NativeUnitFlags.IS_REVIVE(0x80000000)，新增无bool的
  ownerCanBeInTown()/collisionPatternFromSize(size)入口；原显式bool入口保留供独立已验证来源。
- NativePathAllocationCollision.read(world,id)使用同次元数据捕获的真实flag；已有显式入口保留。
  缺失monster flags拒绝，不当作false，不从resurrected/playerRevive/MonStats2.revive或佣兵归属推断。
- Monster仍需有效MonStats；#11281无效size早退不强求flag；player等非monster不要求该组件。
  仅不可变操作快照，flag后续变化需重新捕获；不把分配默认值冒充live path字段。
- 新增4项覆盖真实bit、IS_RESURRECT/mercenary及复活经历非替代物、缺失数据、快照冻结、
  npc/interact与分配pet pattern、Wraith优先级。固定上游0c0bbd1核对Monsters.cpp与Units.h常量。
- 无新DLL动态执行，生产仍Map→AStarPathFinder；实际raw/live字段、Act与Zone映射、共享seed、WRP来源仍待。
- 完整626项/53类通过，失败/错误/跳过0；core/build/round50-full.log，
  XML `.gradle/path-port/round50-full/core/test-results/pathPortTest`。

## 第四十九批续接结果（历史）

- 新增NativePathItemFootprint：真实ECS Class.ITM→既有type4/size1元数据→静态size足迹，
  复用原有COLLIDE_ITEM(0x200)规则；非ITM/WRP拒绝，不读取Size或猜测表行。
- raw int32坐标、实际D2ActiveRoom及可选path +0x64方向由调用者提供；不从浮点还原、不判断
  ground/inventory资格、不自动绑定身份或安装生产碰撞。null room保持原契约。
- 新增4项验证raw坐标/显式方向与物品Request无lead且tolerance2、输入拒绝、单格足迹保留其他bit，
  以及真实ECS→observeFootprints→Request/Dispatch移除/搜索/恢复并在目标token替换后副作用前拒绝。
- 固定上游0c0bbd1核对Units.cpp的尺寸和mask分支，复用已移植算法，无新DLL动态证据。
  生产仍Map→AStarPathFinder；实际raw/live字段、Act与Zone映射、共享seed、WRP类型来源仍待。
- 完整622项/53类通过，失败/错误/跳过0；core/build/round49-full.log，
  XML `.gradle/path-port/round49-full/core/test-results/pathPortTest`；新类已加入隔离编译清单。

## 第四十八批续接结果（历史）

- D2PathActiveRooms在grid对象身份之外捕获cells/row-offset数组身份、width/height与有效行偏移值，
  requireCurrent拒绝内部存储替换或布局变动，避免旧RequestEffects继续操作新存储/错误布局。
- 不复制或冻结cells碰撞位，不冻结seed；未使用row-offset容量的值变化允许，null grid语义保留。
  仅检测当前变化，不追踪改回原值的历史，也不新增初始化网格有效性或原生内存生命周期证明。
- 新增3项覆盖五种存储/布局变动在effects构造、删除/添加/查询前拒绝，旧/新cells均保留；
  碰撞位/seed/闲置容量仍可变，替换cells后重捕获允许只操作新存储。
- 宿主适配快照修复，无新DLL算法或动态执行证据；生产仍Map→AStarPathFinder，
  实际raw/live字段、Act与Zone映射、共享seed、WRP类型来源仍待。
- 拒绝测试修复前复现（core/build/round48-red.log）；修复后完整618项/52类通过，失败/错误/跳过0。
  core/build/round48-full.log，XML `.gradle/path-port/round48-full/core/test-results/pathPortTest`。

## 第四十七批续接结果（历史）

- Search捕获自身创建时result.precisionX/Y，dispatch前在既有Request复检后核对完整原值。
  补齐无Observation的纯坐标目标保存Search在同格fraction变化后仍使用旧快照的缺口。
- 新增3项覆盖0/2/16：双轴fraction变化在查询/点缓冲/索引/计数/向量/stream seed变化前拒绝；
  未变化的纯坐标Search仍可执行，重新进入Request捕获新Search后可使用更新后的precision。
- 仅保存Search的快照一致性；低层Result直接dispatch保留即时投影语义，无精度历史检查。
  不猜测ECS raw来源、不冻结shared seed、不宣称新DLL算法证据；生产仍Map→AStarPathFinder。
- raw/live字段、实际Act与Zone映射、共享seed、WRP类型来源仍待。
- 拒绝测试修复前复现（core/build/round47-red.log）；修复后完整615项/52类通过，失败/错误/跳过0。
  core/build/round47-full.log，XML `.gradle/path-port/round47-full/core/test-results/pathPortTest`。

## 第四十六批续接结果（历史）

- 修复Request构造后result.room被替换却继续执行的房间投影混用：已有Request/Search/effects复检
  入口重新核对result.room与startRoom.bounds同一对象；相同几何的新对象、null和不同房间均须重建请求。
- 此检查独立于标准Observation，坐标目标/legacy调用者同样保留构造时房间契约。
  低层Result直接dispatch无保存的startRoom，不宣称能检测替换历史。
- 新增3项覆盖custom effects前拒绝、保存Search的0/2/16分支及直接footprint操作/构造拒绝，
  断言网格、点缓冲、索引/计数、向量及stream seed保留。仅适配快照一致性，无新DLL算法证据。
- 生产仍Map→AStarPathFinder；raw/live字段、实际Act与Zone映射、共享seed、WRP类型来源仍待。
- 三项新增测试在修复前均失败（core/build/round46-red.log），修复后完整612项/52类通过，
  失败/错误/跳过0；core/build/round46-full.log，XML `.gradle/path-port/round46-full/core/test-results/pathPortTest`。

## 第四十五批续接结果（历史）

- 审查ServerEntityFactory.createWarp、QuestWarp及TownPortalRegistry：WRP含普通出口、
  合成地图标记、任务/城镇传送门，Warp.index、bounds、owner/link只表述宿主路由，非原生类型来源。
- 保留WRP元数据拒绝；新增2项跨入口回归，覆盖完整路由数据、任务编码、城镇链接及混入Objects行，
  并验证分配碰撞适配和生命周期绑定观察不能绕过拒绝。不猜测tile5或object2。
- 本批只固化适配边界，无新算法移植或DLL动态证据；生产仍Map→AStarPathFinder。
  可靠WRP类型来源、raw fp16/live字段/Act与Zone/shared seed仍待。
- 完整609项/52类通过，失败/错误/跳过0；core/build/round45-full.log，
  XML `.gradle/path-port/round45-full/core/test-results/pathPortTest`。

## 第四十四批续接结果（历史）

- D2PathUnits.dynamicPath提供只读不可变原始DynamicPath快照，null保留缺失路径，静态单位拒绝。
  Request标准Observation对已分配动态owner核对完整precisionX/Y，不再只比较整格坐标。
- 构造及既有Request/Search/effects观察复检入口覆盖同格内fraction变化；不从浮点或格点中心猜测。
  组合测试夹具从原owner快照复制precision，修正此前result构造默认居中与显式整数fp16混用。
- 新增2项，完整607项/52类通过，失败/错误/跳过0；core/build/round44-full.log，
  XML `.gradle/path-port/round44-full/core/test-results/pathPortTest`。
- 仅快照一致性，未接通真实ECS raw fp16来源；缺失路径/静态owner不伪造precision，legacy契约保留。
  生产仍A*，live字段/Act与Zone/shared seed仍待，无新增DLL算法证据或动态执行。

## 第四十三批续接结果（历史）

- NativePathUnitMetadata对MonStats2.SizeX/SizeY及Missiles.Size要求uint8域0..255，
  防止宿主int字段越界被当作原生记录；不截断/夹紧。Objects尺寸仍保留int32，不套用byte限制。
- 依据固定上游MonsterTbls.h nSizeX/Y +0x08/+0x09、MissilesTbls.h nSize +0x18A。
  原生size byte可大于pattern映射范围，255仍合法元数据，#11281仍按已有规则返回pattern1。
- 新增3项，完整605项/52类通过，失败/错误/跳过0；core/build/round43-full.log，
  XML `.gradle/path-port/round43-full/core/test-results/pathPortTest`。
- 本批为host输入域校验，不模拟Excel loader截断，不新增DLL动态证据；生产仍A*，
  raw数据/live字段/Act与Zone/shared seed仍待。

## 第四十二批续接结果（历史）

- NativePathAllocationCollision从真实Class/尺寸/MonStats推导#10152分配时的pattern、footprintMask、
  moveMask：玩家0x80/0x1C09，导弹0/0；怪物0x100，Wraith优先pattern5/mask0x804，
  再flying 0x1804，再opendoors 0x3401，否则0x3C01。一般pattern复用#11281。
- 怪物必须有明确BaseId，不把缺失当普通怪物；ISREVIVE显式提供。返回不可变初始化快照，
  不分配路径/修改网格，不读取live字段，不覆盖技能、死亡或后续修改。
- 固定上游0c0bbd1并静态核对本地1.10f 0x6FDA9480..96DF相关分支/立即数；非动态DLL执行。
- 新增3项，完整602项/52类通过，失败/错误/跳过0；core/build/round42-full.log，
  XML `.gradle/path-port/round42-full/core/test-results/pathPortTest`。
- 未移植完整#10152（分配、速度/类型/房间插入等），生产仍A*；raw数据/live字段/Act与Zone/shared seed仍待。

## 第四十一批续接结果（历史）

- D2PathFootprint.patternFromSize移植#11281运行时size推导：0/1/2/3→0/1/1/2；
  仅monster且canBeInTown且非interact转换1→3/2→4；越界size提前返回1，不做pet转换。
- NativePathUnitMetadata捕获真实MonStats.interact，collisionPatternFromSize(nativeSize,nativeIsRevive)
  复用npc/inTown快照和显式ISREVIVE；valid size缺表拒绝，invalid size按原生提前返回。
- 这不是live pattern读取；参数为显式native path size，不默认以当前表size替代；Wraith分配/技能等
  运行时覆盖仍需实际字段。未使用MonStats2.revive/small等unused #11282规则。
- 固定上游0c0bbd1，并静态核对本地1.10f DLL 0x6FDA9250..92ED与0x6FDD1DE4表，
  确认早返回/映射/转换分支；不是动态DLL执行或全寻路差分。
- 新增4项，完整599项/51类通过，失败/错误/跳过0；core/build/round41-full.log，
  XML `.gradle/path-port/round41-full/core/test-results/pathPortTest`。
- 生产仍A*，raw数据/live pattern字段/Act与Zone/shared seed仍待。

## 第四十批续接结果（历史）

- Request.State标准Observation构造时校验owner原生坐标与path.currentPoint一致；
  Request/公开Search及RequestEffects既有复检入口也验证此条件，移动后需重新捕获和构造request。
- 仅比较已提供原生坐标快照，不从ECS浮点推断；不宣称检查fp16低字或实时移动来源。
  legacy自定义Target契约保留，低层Result.State Dispatch重载仍不具备完整request身份。
- 新增2项，完整595项/51类通过，失败/错误/跳过0；core/build/round40-full.log，
  XML `.gradle/path-port/round40-full/core/test-results/pathPortTest`。
- 验证不一致在构造或custom effects执行前拒绝，collidedMask/旧点数保持；不自动刷新快照。
  生产仍A*，raw数据/Act与Zone/shared seed仍待，无新增DLL算法证据或动态执行。

## 第三十九批续接结果（历史）

- 只新增组合回归：真实World玩家/对象组件，经NativePathDynamicFootprint与NativePathObjectFootprint
  读取后，由observeFootprints生成exact共享坐标观察，再交给Request/Dispatch具体effects。
- 验证Orientation==1门目标偏移、owner/target各仅读一次、搜索后完整网格恢复；另验证目标token
  替换后旧request在collidedMask/index及网格写入前拒绝。未新增生产功能。
- 新增2项，完整593项/51类通过，失败/错误/跳过0；core/build/round39-full.log，
  XML `.gradle/path-port/round39-full/core/test-results/pathPortTest`。
- raw坐标/room/pattern/mask仍为显式测试输入，不冒充已完成自动来源；token替换测试不冒充
  另一项新的实际删除/ID复用证据。生产仍A*，Act/Zone/shared seed桥仍待，无新增DLL证据或动态执行。

## 第三十八批续接结果（历史）

- NativePathDynamicFootprint读取实际Class/表尺寸及CofReference.mode，组合动态footprint；
  保留mode unsigned byte，不用visualType等显示覆盖改写逻辑单位类型/模式。
- raw DynamicPath、实际D2ActiveRoom、native pattern及uint32 footprint mask仍须显式提供，
  原样保留，不从浮点、Size、宿主动态碰撞索引推测；null path保留既有0xFFFF mask语义。
- 新增3项，完整591项/51类通过，失败/错误/跳过0；core/build/round38-full.log，
  XML `.gradle/path-port/round38-full/core/test-results/pathPortTest`。
- 可在observeFootprints内使用以绑定生命周期，但本入口自身不绑定身份/安装生产碰撞。
  本批只接通逻辑mode及元数据组合；动态原生字段实际来源仍未完成，不宣称完整Reader。
- 生产仍A*；raw数据、Act/Zone/shared seed仍待；无新增DLL算法证据或动态执行。

## 第三十七批续接结果（历史）

- 本批只补公开Search重载的直接回归证据，不新增生产功能：保存真实Request生成的Search，
  在type0/2/16分支直接再次调用Dispatch，验证失效guard与有效但被替换的观察均先于写入/查询拒绝。
- type16同时断言live owner seed不被消耗；路径buffer/count/index保持拒绝前状态。
  当前有效观察的type0/2/16均可正常执行，避免测试只证明入口一律拒绝。
- 新增3项（内部覆盖三个分支），完整588项/50类通过，失败/错误/跳过0；
  core/build/round37-final.log，XML `.gradle/path-port/round37-final/core/test-results/pathPortTest`。
  首轮有效分支夹具房间从0开始，IDA外扩后越界；修正为合法域后完整重跑。
  有效分支断言正常返回合法计数，允许原算法零路径结果，不强行要求寻路成功。
- 这些是Java适配回归，不是动态DLL差分或新原生算法证据。未宣称Search可跨移动保留，
  未扩大低层State重载的历史身份保证。生产仍A*，raw数据/Act与Zone/shared seed来源仍待。

## 第三十六批续接结果（历史）

- Request.State构造对标准Observation增加owner.type/sizeX与path.ownerType/unitSize一致性检查，
  避免使用自定义effects时绕过具体RequestEffects的owner元数据校验。失败先于request执行和副作用。
- 原有target/speed/exact owner对象及生命周期校验保留；legacy自定义Target仍按原调用方契约。
- 新增2项，完整585项/50类通过，失败/错误/跳过0；core/build/round36-full.log，
  XML `.gradle/path-port/round36-full/core/test-results/pathPortTest`。
- 不校验未包含在Observation内的pattern/mask/mode，不自动刷新位置或元数据。
  生产仍Map→AStarPathFinder；raw坐标/动态字段/Act与Zone/shared seed仍待，无新增DLL证据或动态执行。

## 第三十五批续接结果（历史）

- D2PathDispatch两个公开compute入口在路径写入前验证标准Observation生命周期；Search入口
  还通过Request.State校验原观察对象未被替换，type0/16房间/seed分支同样先检查。
- Request.compute复用同一包内观察校验，原missile优先检查保留。低层Result.State重载没有保存
  request身份，只能验证当前安装的标准观察，不能声称检测到历史观察替换；legacy Target契约不变。
- 新增2项，完整583项/50类通过，失败/错误/跳过0；core/build/round35-full.log，
  XML `.gradle/path-port/round35-full/core/test-results/pathPortTest`。
- 本批为host入口一致性校验，未拦截调用期间任意自定义回调修改，也不检测元数据/位置刷新。
  raw坐标/动态字段/Act与Zone/shared seed仍待，生产仍A*，无新增DLL算法证据或动态执行。

## 第三十四批续接结果（历史）

- RequestEffects对标准Observation校验exact owner.coordinates对象；同类型/坐标/尺寸但另一份
  owner快照不再可混入。target原有exact检查保留，legacy自定义Target仍按原调用方契约处理。
- 构造及每次removeOwner/removeTarget/addOwner/addTarget/blocked前复检原观察对象仍安装且
  生命周期guard有效，直接调用effects也不能绕过compute入口的身份检查；失败先于网格操作。
- 新增3项，完整581项/50类通过，失败/错误/跳过0；core/build/round34-full.log，
  XML `.gradle/path-port/round34-full/core/test-results/pathPortTest`。
- 仅补host一致性校验，不把相同Unit对象下的mode/mask等额外字段宣称为已验证身份，
  不拦截任意直接静态footprint调用或自定义search；操作间数据变化仍须重捕获。
- 生产仍Map→AStarPathFinder；raw坐标/动态字段/Act与Zone/shared seed仍待，无新增DLL算法证据。

## 第三十三批续接结果（历史）

- NativePathUnitBinding新增observeFootprints(speed, FootprintReader)，按操作读取owner/target
  footprint并直接复用其coordinates构造带身份guard的Observation，不再二次读取独立坐标快照。
  返回FootprintObservation持有exact owner/target及observation；自目标只读取一次并共用对象。
- 该入口只调用显式FootprintReader，不调用构造时的坐标Reader；读取前后检查原owner/target token，
  后续requireCurrent及Observation采样仍拒绝身份替换。旧observe接口兼容保留。
- 新增4项，完整578项/50类通过，失败/错误/跳过0；core/build/round33-full.log，
  XML `.gradle/path-port/round33-full/core/test-results/pathPortTest`。
- 返回的exact footprints供RequestEffects使用；没有自动安装effects或拦截直接调用footprint静态函数。
  生命周期有效不等于坐标/元数据实时刷新，操作间变化仍需重新捕获；Reader仍负责提供正确原生数据。
- raw坐标自动来源、动态footprint字段、Act/Zone/shared seed仍待；生产仍Map→AStarPathFinder。
  本批为host快照一致性适配，无新增DLL算法证据或动态DLL执行。

## 第三十二批续接结果（历史）

- NativePathObjectFootprint从实际World对象读取Object.mode及Objects表字段，与显式raw静态坐标、
  实际D2ActiveRoom组合为D2PathFootprint.Unit；自动复用元数据的door/尺寸，方向字节可显式传入。
- 宿主IsDoor/BlocksVis/BlockMissile/HasCollision为boolean，只按现有非零测试编码0/1，
  不声称恢复DLL原始字节。SubClass必须uint8，HasCollision恰8项，mode取unsigned后要求0..7。
- 依据固定上游0c0bbd1的#10338/#10410；沿用既有#10222/#10223及网格实现，不新增DLL算法证据。
- 新增6项，完整574项/50类通过，失败/错误/跳过0；core/build/round32-full.log，
  XML `.gradle/path-port/round32-full/core/test-results/pathPortTest`。
- 此入口只捕获数据，不自行绑定身份或安装生产碰撞；调用方须将返回coordinates与本操作的
  lifecycle-bound observation共用，不能混用另一个快照。room可保留原生null，不用RoomEx替代。
- raw坐标自动来源、原始字节无损来源、动态footprint字段、Act/Zone/shared seed仍待；生产仍A*。

## 第三十一批续接结果（历史）

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

## 第二十一批补充：技能拥有移动的拒绝边界（2026-10-30）

提交 `62a45a907` 明确了 `NativePathRuntime.request` 的 owner 边界：实体带有
`Casting`、`Leap` 或 `WhirlwindRuntime` 时返回显式拒绝，`Pathfinder` 不得将该结果解释为
普通未知元数据并回退到主机 A*。这样可以避免技能移动状态被普通路径规划抢占。

新增 `NativePathRuntimeTest` 回归覆盖 Casting 与 Whirlwind 两种情况，验证不会创建普通
路径；定向测试 14 项全部通过。该提交只完成拒绝与隔离，不代表特殊路径的原版速度、朝向、
owner 字段和完整运行时已经接入。

## 回归基线记录：Whirlwind 与 Native Seam fixture（2026-10-30）

在提交 `62b02258c` 后单独复跑 `WhirlwindCollisionTest` 与 `NativeSeamCollisionTest`，结果为
5 项中 2 项失败：`sweepClipsConstructionAtObjectsButMovementOnlyAtStaticBarriers` 和
`scopedCollisionOnlyCrossesExplicitNativeSeamsAndKeepsFootprint`。失败分别发生在 fixture
的 cast 阻挡结果与邻区 footprint 断言；与 `NativePathRuntime` 的技能 owner 拒绝分流无关，
需要后续单独核对碰撞掩码/测试夹具基线。NativePathRuntime、PathfinderRecoveryTarget 和
DiabRunIntegration 定向测试仍全部通过。
