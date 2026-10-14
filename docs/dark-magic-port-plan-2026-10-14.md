# dark-magic 最近提交：分析与顺序移植

日期：2026-10-14（北京时间）。审查远程默认分支 `3cc18677`。
10 月 14 日无新提交；本计划针对此前几天的提交。

## 依据与约束

以 D2MOO 1.10f 为首要行为依据；必要时分析本地
`G:/BaiduNetdiskDownload/Diablo II 1.10F` 的原版程序与资源。
dark-magic 的 1.14d 观察用于提出核查问题，不能直接成为 1.10f golden。
Go/Lua 代码不直接复制；迁移确认后的行为和有区分力的测试。
每批实现单独记录验证、提交并推送；小型低风险修改按用户要求可不编译。

## 移植顺序与状态

1. **移动终点、速度单位和体力耗尽**（进行中）。参考 `fcb6203e`、
   `fc5ba9e6`。riiablo 的 `Pathfinder.limitWaypointSpeed` 已有终点截断和测试，
   不重复添加。发现 StaminaSystem 耗尽分支会把较短终点速度放大到完整步行速度，
   改为只限制上限，并回归短步长、完整奔跑步长和零速度。
   玩家创建与两条复活路径的单位换算现已核查并修正（见后续验证记录）；状态加成、
   装备加速和系统执行顺序仍待独立核查，不全局乘速度常量。
2. **导弹速度与寿命分离**（基础导弹已修正，特殊处理器仍待核查）。参考
   `b7e5f680`、`8c87f64a`。撤回此前“移动导弹 Range 是距离”的结论。
   D2MOO `MISSILES_CreateMissileFromParams` 将 Range + skillLevel * LevRange
   写入 TotalFrames/CurrentFrame，与速度无关；显式范围、初始帧和循环标记可覆盖
   默认值。`MISSMODE_SrvDo01_BasicMissile`（0x6FC5F8C0）调用
   `MISSMODE_HandleMissileCollision`（0x6FC56D50），先更新移动路径，再将帧计数
   减一，<=0进入销毁命中处理。因此 Range 是帧寿命，而不是等数值的行走距离。
   已修正 pSrvDoFunc=1 基础导弹：移动/静止都初始化帧寿命，工厂加入 LevRange
   技能等级增量；到期使用既有 LastCollide/AlwaysExplode 处理，不再同时按距离
   提前销毁，也不将最终位置截到 Range 距离。原来的错误回归已替换；新增速度
   1/1000均在第5帧到期、前4帧存活的系统测试。
   其他移动处理器的旧距离策略、特殊覆盖、零/负寿命、固定步长和重连剩余寿命
   仍待逐项核查；本批没有宣称完成所有导弹的原生迁移。Charged Bolt Sentry
   的77帧已在下一批核实（见验证记录），不能直接覆盖所有helper。
3. **Blade Fury 发射时序**（已完成 1.10f 公式核查）。参考 `eecd52c9`。
   riiablo 已区分 srvmissilea/cltmissilea；现有测试允许 5 tick 后再发射，
   dark-magic 观察到 6 tick 间距。D2MOO `SKILLS_SrvDo048_BladeFury` 保存
   `当前帧 + prgcalc1 - 1`，并在下一次要求 `Param1 < 当前帧`，因此 1.10f 的
   实际间隔由 `prgcalc1` 决定，而不是固定使用 `Param4`。riiablo 已改为读取
   `prgcalc1`，并增加 Param4 与公式不一致时的回归测试。首次发射、持续发射、
   释放和魔力消耗仍需真实 1.10f 客户端门槛。
4. **ItemRatio 选择**（已完成 1.10f 核查与修正）。参考 `964d70e9`。
   D2MOO `ItemsTbls.cpp:DATATBLS_GetItemRatioTxtRecord`（0x6FD5C220）选择
   不超过请求版本的最高版本；原生调用使用 0/100，不强制把 100 映射为 1。
   `Items.cpp:ITEMS_GetQuestFromItemId`（函数名称容易误导）实际判断非任务的
   扩展级/精华级武器或护甲，并排除主类型 mpot；Uber 不是 quest 标记。
   已补充 ubercode/ultracode 列并修正该判断。装备类别使用现有 Weapons/Armor
   表记录类型；自定义跨表装备类型尚未验证。职业专属继续依据主类型 Class。
   相同版本选择最后源行，不依赖 IntMap 遍历顺序；新增版本、装备等级、任务、
   药剂、职业专属及缺失匹配测试。
5. **数据查询身份审计**（完成第一批：LvlTypes）。参考 `6da9484a`、`06158fb3`。
   D2MOO `LevelsTbls.cpp:DATATBLS_LoadLevelTypesTxt` 不加载 Id 列，
   `DATATBLS_GetLevelTypesTxtRecord`（0x6FD61460）
   直接查询源行数组。riiablo 的 Map/OutdoorGrid 使用 Levels.LevelType 查询，
   已将 LvlTypes.Entry 改为 Excel.Index，并同步新版 table schema 的 indexed
   标记；旧 codec 的 TXT/BIN 两条加载路径均按位置建索引。
   新增真实 TXT 加载夹具，Id=2 位于行1、Id=9 位于行2，确认查询2得到行2。
   MonPreset 已独立维护每幕位置，补充交错幕夹具锁定现有扩展行为；D2MOO
   `DATATBLS_LoadMonPresetTxt` 按连续幕分段，不把交错幕支持宣称为原生规则。
   第二批核查：D2MOO `SkillsTbls.cpp` 的 pSkillTbl 不加载 TXT Id，而由 skill
   名称通过 TXTFIELD_NAMETOINDEX 建立链接；GetSkillsTxtRecord 按编译数组索引
   查询。Skills 已改为 Excel.Index，并将旧 codec 的 Entry.Id 归一为查询行号，
   名称索引也指向该行。新版 Skills schema 同步 indexed 标记；这与 dark-magic
   显式 Id 的解释不同，不移植其结论。SkillDesc 名称关联保持现有实现，新增
   技能行1/描述行2/作者Id77 的错位测试。
   Objects 的旧 codec 已按源行查询并归一 Id，符合 D2MOO
   `ObjectsTbls.cpp:DATATBLS_GetObjectsTxtRecord`（0x6FD718F0）；删除其多余
   Key 标记并同步新版 schema，新增重复作者Id408不覆盖的回归。
   MapManager 的 DS1 静态引用先经 obj 每幕映射再查 Objects；仓库
   assets/data/obj.txt 的全部 Id 均等于幕内行号（错位数0）。obj 是项目映射表，
   目前未证明错位时应按显式Id还是幕内位置；保留现状，不把 dark-magic 的
   recovered 表约定当成原生证据。自定义错位 obj 表仍是待研究边界。
6. **音效重定向与分组权重**（完成版本边界核查，不移植新版字段）。参考 `b3715dd0`。
   D2MOO 的 D2Sound.cpp 仅为 stub，不能据此推断播放算法。直接读取用户本地
   1.10f 的 patch_d2.mpq、d2exp.mpq、d2data.mpq：三份 Sounds.txt 均只有
   Group Size，没有 Redirect/Group Weight；生效的 patch 表以 Sound 命名，
   下层两表以 Handle 命名。本地 D2Client.dll 可打印字段字符串也有 Group Size，
   未发现 Redirect/Group Weight。其他仓库 version_99 表存在这些字段，不能
   当成 1.10f 特性。故不添加权重、不照搬 dark-magic 重定向逻辑；Group Size
   的原生 RNG、选择时机和数值/名称入口一致性仍待客户端反汇编确认。
   审计中发现 riiablo Sounds.put 按 Index 存储，而默认名称索引指向源行号。
   已重新建立 Sound→Index 名称索引，补充 Index10/11 与源行0/1错位测试；
   不改变现有播放分组算法。新增可选原版资源测试 NativeSoundsArchiveTest，
   使用环境变量 D2_110F_HOME，无配置时跳过，不硬编码用户路径。

## 已有覆盖与不宜直接移植的部分

- 空物品类型 TC：`e208ae92`；riiablo 已有零概率返回空结果的保护，
  仍需核查递归空类别的 RNG 消耗。
- 区域怪物等级与经验：`299c1471`、`55802d50`；riiablo 已有
  MonsterStatsCalculatorNativeTest、ExperienceManagerNativeTest 等，优先补边界而非重写。
- 角色实时抗性：`dda677e4`；riiablo 已显示四种抗性，移植收益较小。
- Go/Lua 的 JSON 缓存：只作为性能审计线索，没有 Java 性能测量前不引入新缓存。

## 验证记录

- 第一批：体力耗尽保留终点短步长；提交 `c5c4f667`，定向测试通过。
- 第二批：移动导弹 Range 与静止导弹帧寿命分离；提交 `ae214d5c`，仅做
  diff 检查，未运行本批新增测试。该测试锁定现有实现，不代表原生机制已经核实；
  移动导弹 Range 的距离/帧寿命语义需重新审计，上述第二项不作为原生结论。
- 第三批：Blade Fury 使用 1.10f `prgcalc1` 发射间隔；本次提交记录在 Git 历史，
  定向测试通过。
- 第四批：修正 ItemRatio Uber 与同版本行选择；`ItemRatioTest`、
  `NativeItemQualityResolverTest` 定向测试通过（BUILD SUCCESSFUL），diff 检查通过。
- 第五批：LvlTypes 源行索引与 MonPreset 每幕索引夹具；
  `NativeTableIdentityTest`、`NativeExpansionIdsTest` 定向测试通过。
- 第五步第二批：Skills 源行、SkillDesc 名称、Objects 重复作者Id；
  `NativeTableIdentityTest`、`NativeAssassinSkillMatrixTest` 定向测试通过，
  diff 检查通过。未进行实机验收。
- 第六批：`NativeTableIdentityTest` 与 `NativeSoundsArchiveTest` 定向测试通过；
  原版资源测试实际执行1项、跳过0项，确认三份原版表头不含新版音效字段。
  `AudioInstanceTest` 通过，diff 检查通过。未进行实机听音验收。
- 导弹复核第一批：`MissileNativePolicyTest` 定向测试通过，撤回第二批的距离
  语义断言；新增基础移动导弹按帧寿命及速度无关的回归。
  `MissileRoomTrackingTest` 同时通过，diff 检查通过。尚未实机验收。
- 导弹复核第二批：D2MOO `SkillSor.cpp:SKILLS_MissileInit_ChargedBolt`
  （0x6FD15E50）读取 TotalFrames 的 uint8 视图；>=78时才将总帧数及当前帧
  改为77，否则保留已有寿命。SrvDo017 的陷阱和法师，以及 SkillAma.cpp 的
  Charged Strike 均使用该初始化回调。77不是固定寿命，也不是世界距离。
  已为陷阱/法师/Charged Strike 统一 Range + level * LevRange 后的条件截断，
  Charged Strike 不再强制延长短寿命到77；chargedBoltPath 的显式帧寿命不再
  被旧距离阈值提前删除。旧 range 字段暂保留兼容值，不作为这些导弹销毁阈值。
  uint8 边界也纳入测试（300帧的低字节44不触发截断），不直接推广到刺客武学
  的其他初始化回调；武学释放、特殊路径长度和重连剩余帧仍待后续核查。
  验证：MissileNativePolicyTest 18项及 Charged Bolt Sentry 发射回归1项均通过，
  无跳过，diff 检查通过；尚未实机验收。
- 导弹复核第三批（刺客武学初始投射物）：D2MOO SkillAss.cpp 的
  sub_6FCF6600 将技能等级传入创建参数，Claws of Thunder 第三层使用
  sub_6FCF6C10（0x6FCF6C10），按 uint8 TotalFrames 视图条件截为77；
  Phoenix Strike 第二层的 sub_6FCF6D70 使用 sub_6FCF7390（0x6FCF7390），
  后者用完整帧数判断>=78，不能直接复用 uint8 判断。
  已在 Actioneer 两条创建路径设置 Range + level * LevRange 的条件截断寿命；
  利用前批 chargedBoltPath 的帧到期策略，避免按旧距离提前删除。
  新增两者300帧边界差异及短寿命不延长测试，并在真实武学释放夹具检查初始寿命。
  本批不改种子、路径点算法或链式续弹；续弹、其他武学控制器及重连仍待审计。
  验证：MissileNativePolicyTest、Claws第三层释放、Phoenix各层释放和现有
  Phoenix连锁续弹定向测试通过，diff检查通过；尚未实机验收。
- 导弹复核第四批（SrvHit12 续弹）：D2MOO `MISSMODE_SrvHit12_ChainLightning_LightningStrike`
  （0x6FC5B020）创建续弹时只复制导弹类型、owner、skill、skill level 和目标，
  将 TargetX 减一；不复制父弹当前帧，也不调用 Claws/Phoenix 的特殊 init 回调。
  riiablo 续弹现在重置 nativeFrame，并按完整 `Range + level * LevRange` 重新计算
  nativeLifetimeFrames；不套用 77 截断、不继承父弹剩余寿命。新增父弹已运行10帧
  仍生成全新寿命的回归；续弹目标筛选和命中次数保持原实现。`MissileNativePolicyTest`
  与 Phoenix 连锁定向测试通过，diff 检查通过；尚未实机验收。
- 导弹复核第五批（同步寿命状态）：D2MOO Missiles.cpp 的原生0x73包传输
  MISSILE_GetCurrentFrame 的剩余帧数。riiablo 的 MissileP 原先只传身份、range、
  skill和level，无法恢复运行时寿命。现追加 nativeFrame/nativeLifetimeFrames，
  将当前服务器时钟与显式寿命覆盖同步到只读客户端；生成的 MissileP.java 已更新。
  新字段默认-1表示旧发送端缺省，避免旧包重置已有时钟；0为有效状态。
  测试验证77总帧/60已运行帧还原17剩余帧、重复快照、旧包及零值覆盖。
  MissileLifetimeSnapshotTest 与 AuthoritativeAreaSkillSnapshotTest 通过，diff检查
  通过。此批只修复寿命元数据，不宣称完成实机重连：独立clientFrame回调时钟、
  动画相位、延迟补偿、特殊路径/控制器状态和完整重连流程仍待审计；客户端仍不
  执行服务器伤害逻辑。
- 玩家速度复核第一批：`Units.cpp:UNITS_GetBaseVelocity` 返回 WalkSpeed<<8；
  Run 状态通过 RunSpeed/WalkSpeed 的百分比调整。`Step.cpp:PATH_GetDirectionVector`
  查表轴向向量为4096，`PathMisc.cpp:PATH_ComputeVelocityAndDirectionVectorsToNextPoint`
  用 (direction * velocity)>>8 计算16.16位移；`Step.cpp` 将其累加到精确位置。
  故速度S的轴向每帧位移为S/16子格，25帧/秒对应S*25/16子格/秒。
  CharStats 6/9应初始化为9.375/14.0625世界坐标单位/秒。
  新增 Velocity.setPlayer，统一玩家创建、服务器复活和客户端恢复三条路径；
  保留怪物、导弹和普通Velocity.set的既有合同，Engine回退值仍是原生6/9。
  测试覆盖原生轴向定点计算、重复初始化不重复换算及怪物/显式世界速度不变。
  原生整数舍入、方向查表量化、装备FRW、技能加速与端点系统顺序仍待后续；
  未进行实机位移验收。
  验证：PlayerVelocityUnitsTest、StaminaSystemTest、PathfinderTest定向测试通过，
  diff检查通过。随后仅调整注释及日志标签，按用户约定不再次编译。
- 玩家速度复核第二批（模式切换与端点）：VelocityModeChanger.begin/toggleRunWalk
  原先以setLength重设向量，会放大Pathfinder或碰撞处理留下的短步长；现只限制
  速度上限，不放大、不改变方向。奔跑加速由下一次Pathfinder计算，不在模式切换
  时覆盖终点截断。新增步长0.5在跑走切换后保持不变、满跑降至步行及零速回归。
  本地GameScreen中StateUpdater先于Pathfinder，模式更新位于碰撞同步之后；
  本地使用Box2D，D2GS使用VelocityAdder。后者再次乘stateSpeedMultiplier，而
  Pathfinder已对<=1的状态倍率调整速度，两条位移路径存在统一合同的待办。
  D2MOO UNITS_GetAnimStatBasePercentage 对装备FRW使用150*FRW/(150+FRW)
  （整数除法）；移动总百分比再与STAT_VELOCITYPERCENT相加、最低25。
  当前StateUpdater把慢速/Frenzy/光环相乘且尚未接入完整装备FRW公式，因此不能
  宣称加速审计完成；下一批优先统一位移倍率应用一次，再迁移玩家加速公式，
  避免把同一状态贡献重复计算。此批不改状态聚合或装备加速。
  验证：VelocityModeChangerTest、PathfinderTest、StaminaSystemTest定向测试通过，
  diff检查通过；尚未实机验收。
- 玩家速度复核第三批（位移倍率合同）：Pathfinder不再将<=1的状态倍率烘焙进
  请求速度，但终点限速仍使用delta*最终倍率。VelocityAdder应用一次，新增
  Box2DSynchronizerPre对复制的向量应用相同倍率，不修改请求向量；锁定移动时
  Box2D接收零速。修复服务器减速被平方、本地加速未应用以及减速终点越界风险。
  Velocity.movementMultiplier统一非负倍率/锁定语义；不改状态聚合及装备FRW公式。
  新测试覆盖0.5/1/1.5倍率、终点截断、锁定、请求向量不变，以及实际VelocityAdder
  位移与Box2D输入向量的数学一致性。未运行真实Box2D接触或实机验收；Whirlwind
  已按原始请求速度和倍率截断终点，保持其现有路径，其他特殊移动仍需独立审计。
  验证：MovementMultiplierContractTest、VelocityAdderTest、PathfinderTest、
  VelocityModeChangerTest、StaminaSystemTest共40项定向测试通过，diff检查通过。
- 玩家速度复核第四批（装备FRW与状态相加）：依据Units.cpp的150递减公式，
  StateUpdater玩家分支现读取item_fastermovevelocity，以整数除法求有效FRW，
  再加状态列表速度贡献、Frenzy和旧减速回退值；不再把这些效果连乘。
  明确velocitypercent贡献的Decrepify不再额外扣固定50。状态列表已排除Frenzy，
  因而单独加一次，不把最终属性的velocitypercent再次当作状态来源。
  Velocity.setPlayer标记原生玩家速度；Pathfinder保存最终跑走选择。跑步额外
  百分比以步行速度为基准相加，最低速度仍是步行基准25%，不是跑速的25%。
  测试覆盖75FRW→50有效FRW、整数截断、状态加减、跑步加成及极端减速最低值。
  怪物保留原聚合；当前负装备FRW按0防御性处理，不宣称支持自定义负值。
  原生装备重量、速度百分比其他属性来源、整数跑走比率/定点舍入及特殊移动仍待
  后续核查；没有实机验收。
  验证：PlayerMovementPercentageTest、MovementMultiplierContractTest、
  NativeBarbarianPassiveDataTest、PaladinSupportAuraIntegrationTest定向测试通过，
  diff检查通过。
- 玩家移动模块集中收尾（护甲重量与初始化边界）：D2MOO Items.cpp 装备读档
  护甲分支将 -dwSpeed 写入 STAT_VELOCITYPERCENT，武器分支则写入
  STAT_ATTACKRATE，不能将武器 speed 当作移动重量。ItemData 现从当前生效、
  未损坏的已装备 Armor.Entry 汇总 -speed，排除背包及备用武器组，重复物品索引
  只计一次；在玩家公式中与有效 FRW、状态贡献相加，不进入 FRW 的150递减公式。
  此查询只补基础护甲重量，不读取最终 velocitypercent，以免重复计算状态来源；
  不宣称已支持所有装备词缀或自定义 velocitypercent 来源。
  同时修复 Velocity.setMonster 直接复用玩家组件时残留玩家跑步公式标记的问题。
  模块回归覆盖护甲/盾牌叠加、切换备用组、损坏、背包、重复槽、武器排除、
  FRW与重量合算、玩家/怪物初始化、跑走切换、端点与倍率一次合同及状态回归。
  后续按完整模块交付，不再要求每个内部小步骤单独确认。
  剩余范围：其他 velocitypercent 来源与完整装备聚合、原生整数跑走比例及定点
  量化、特殊移动、真实Box2D接触与实机验收；怪物状态聚合仍保持既有实现。
  验证：EquipmentMovementModifierTest、PlayerMovementPercentageTest、
  PlayerVelocityUnitsTest、MovementMultiplierContractTest、VelocityAdderTest、
  PathfinderTest、VelocityModeChangerTest、StaminaSystemTest、
  NativeBarbarianPassiveDataTest、PaladinSupportAuraIntegrationTest 全部通过，
  git diff --check 通过。未进行实机验收。
- 特殊移动模块：旋风普通跑步状态隔离与位移合同。D2MOO SkillBar.cpp
  SKILLS_SrvSt38_Whirlwind 调用 PATH_SetVelocity(sub_6FD15500)；Skills.cpp
  sub_6FD15500 对玩家返回 CharStats.WalkSpeed<<8，而不是 RunSpeed。
  riiablo 旋风入口和每次路径推进现清除 movementRunning，避免继承普通奔跑
  标记而使积分选择跑步倍率。终点截断复用 Pathfinder.limitWaypointSpeed，
  碰撞预检和最终积分统一调用 Velocity.movementMultiplier，不再用0.0001倍率
  回退掩盖锁定或零倍率。攻击计时与武器速度断点保持原有逻辑。
  新增真实ECS旋风→Pathfinder→VelocityAdder回归，覆盖不同倍率下到达终点、
  不越界、请求向量不预乘倍率以及下一帧清理；原生数据与既有双持攻击回归保留。
  此批是已证实的基速选择与内部链路修复，不宣称已确认原生旋风期间全部FRW、
  状态刷新或定点规则。D2Common按动画模式/技能标记选择velocity更新分支，
  该条件链仍须追踪；旋风专用碰撞掩码、动态单位穿越和Box2D实机验收仍待完成。
- 旋风动态碰撞：D2Collision.h 的 COLLIDE_MASK_PLAYER_WW 只有 WALL/OBJECT/DOOR；
  SkillBar.cpp 在路径建立后进一步使用 WALL/MISSILE_BARRIER/BLANK/NO_PATH，
  不包含 PLAYER/MONSTER/PET。riiablo 普通动态占位把单位中心视为路径阻挡，
  因此旋风积分不能复用这一普通单位阻挡。DynamicUnitCollisionSystem.tryMove
  现仅在 WhirlwindRuntime 存在时忽略其他单位，仍通过 grid.move 更新自身占位；
  不改变落点探测、普通寻路、其他单位对旋风施法者的阻挡或静态地形检查。
  新增穿过占位、占位更新、技能结束立即恢复普通阻挡的回归。
  状态移除审计：原生 CurseStateCallback_Whirlwind 注销事件、关闭状态、刷新
  动画速度并调用 sub_6FD14C30；当前 finish 清理状态/施法/序列/运行时，StateUpdater
  每帧重算倍率。回调完整事件与动画刷新语义尚未移植，不能宣称等价。
  静态碰撞仍保留待办：当前地图合并 OBJECT/DOOR 到 BLOCK_WALK，不能直接把
  原生16位掩码套在DT1字节标志上；路径建立和运行阶段的差异需要原生标志查询层。
  本批不改变Box2D实体接触行为，实机穿越验收仍待进行。
- 旋风模块状态收尾：统一终止路径（到达、阻挡、死亡、状态/施法移除）清零
  velocity、movementRunning 与临时 modeSpeedBonus；死亡清理也使用同一速度清理
  合同，避免下一次动画或普通移动继承旋风状态。D2MOO 的状态回调仍包含事件注销
  与动画速度刷新，riiablo 当前由 ECS 状态移除和 StateUpdater 下一帧重算覆盖，
  尚未宣称逐指令等价。旋风模块当前源码审计与定向回归完成；静态碰撞标志映射、
  事件注销/动画即时刷新、Box2D及实机验收列为剩余缺口。
- 旋风整体复核（更正此前“模块完成”的表述）：源码链路复核不等于完整原生一致性。
  本轮核对 SrvSt38/SrvDo076、CurseStateCallback、sub_6FD14C30、
  ITEMS_GetWeaponAttackSpeed、ECS运行/终止、序列重复及动态占位路径。
  发现并修复攻击计时差异：原生扩展版首次服务回调 nCount=1 并预约当前帧+4；
  后续到期才允许双持 nCount=2。原实现首次就双持且while循环补发最多8个窗口。
  现首次回调只打一手，后续每次回调至多一个窗口；next=旧预约+武器间隔，
  若next严格小于当前帧则重置当前帧+间隔，不在长帧重放积压攻击。
  补充首发/第二窗口、长帧不补发、死亡速度标记与状态外部移除清理回归。
  保留浮点时间和25fps换算，不宣称实现原生整数tick调度；当前逻辑针对扩展版，
  经典版每回调一击及怪物随机攻击分支仍未独立支持。
  原生状态回调调用 sub_6FD14C30，后者实际刷新被动技能并对玩家调用sub_6FC7DF40；
  此前仅列“事件/动画刷新”不完整。当前未新增原生技能事件，不能伪造注销操作。
  仍未闭合：原生静态碰撞查询层及启动/运行掩码差异、本地Box2D接触一致性、
  回调即时被动/动画刷新、攻击无目标时的手序切换、武器合成动画选择、
  实机/网络恢复验收。以上属于同一旋风模块，未宣称完整移植完成。
