# D2MOO 最新增量同步：剩余工作（2026-10-16）

## 范围与基线

- 用户目标是同步上游增量更新，不是完整替换 riiablo 寻路后端。
- 本次已实际执行官方仓库 `git fetch --no-recurse-submodules origin`，随后核对远端 master。
- 官方仓库：https://github.com/ThePhrozenKeep/D2MOO
- 最新 master 仍为 `0c0bbd1e6bef9181a3da1c046523af622a925416`（2026-10-13）；相对之前固定审查版本，没有新增主线提交。
- 用户截图指出2026-10-16的分支更新，已确认：`origin/test_utilities=8390bfe`（主线外8个提交），
  `origin/visitor_framework=14110b3`（主线外17个提交，包含前述8个，再增加9个），不能重复计数。
- 新分支内容是C++测试工具、inline hook、DLL基址/二进制读取与对象字段访问/差异比较框架；
  没有改动游戏寻路算法。剩余工作增加这17个提交的适用性审查，区分Java可复用测试能力与C++专属实现。
- 比较起点为历史版本 `8322494ed1f715ad51552f169df76cf600fabc71`；共158个提交，排除merge后101个。
- 目标仍为1.10f；用户指出的上述分支纳入同步审查，主线与未合并分支分别记录。
- 本清单列的是剩余核销工作，不把“尚无逐提交验收记录”等同于“代码一定没实现”。
- 既有626项寻路/碰撞回归不是整批101个提交的同步验收。

## 完成标准

每个非merge提交必须归入“已移植”“当前实现已等价”“不适用”“待修复”之一，并给出当前代码位置、版本依据和必要回归证据。
只对确认存在的差异补最小修正，不要求重写已有架构；已移植项不重复实现。覆盖全部提交后再报告完成率。

## 最新分支同步第一批：测试工具（2026-10-16）

- `aec1395`：已添加Java测试专用readBinFile，按工作目录的data/global/excel路径读取，
  int32小端count及显式recordSize形成只读记录buffer，允许尾随数据；Java类型解码由调用者承担。
  截断、负count、乘法越界/不足数据显式失败，是宿主安全扩展，不复制上游未检查stream状态的问题。
- `da15a17`：已添加uint32闭区间随机fixture数据工具，用long表示unsigned并拒绝采样消除模偏差。
  Random由调用者提供以便复现；不保证与上游mt19937输出一致，不作为游戏RNG或D2Seed。
- `01c64d8`/`8bc96f2`/`0fadad7`：原生函数地址配对、inline hook、DLL基址依赖C++/Win32进程；
  当前Java测试后端不能直接移植，不建立假DLL地址调用。若后续明确要求真实DLL差分，再单列原生桥工程。
- `017e8ce`：空NoOpFixture用于C++fixture mixin，当前JUnit不需要同构空类。
- `2dd609b`/`8390bfe`：CMake模块/umbrella header为C++装配；Java工具放core/src/test/java/com/d2moo/testutil。
- VisitorFramework另外9个提交的Java适用部分见下节；不将测试工具验收视作主线101项完成。
- 本批仅新增测试基础设施，无生产游戏逻辑、寻路或协议变动。
- 定向4项/1类通过，失败/错误/跳过0；日志core/build/upstream-tools-01.log，
  XML .gradle/path-port/upstream-tools-01/core/test-results/pathPortTest。覆盖实际临时bin文件、
  小端记录/尾随数据/只读buffer、非法count/截断、uint32端点/非法范围及拒绝采样尾部。

## 最新分支同步第二批：VisitorFramework（2026-10-16）

已读取 `origin/test_utilities..origin/visitor_framework` 九个提交及最终全部源码，基准 `14110b3`。
Java适用能力集中于测试专用 `core/src/test/java/com/d2moo/testutil/ObjectDifferences.java`：

- `586fd95` / `5baef9d` / `2a21c28`：已移植字段/引用差异分类、结构化路径和值、每次比较独立上下文及只读结果。
  Java输出为普通文本，不复制ANSI颜色或C++ ostream装配。
- `a53f818` / `1842788` / `14110b3`：已移植显式Schema/Visitor选择字段、嵌套对象和数组。
  泛型接口替代traits/成员指针/宏；省略字段及union分支由schema显式编写Java逻辑。
  原生动态数组pointer/count在Java用数组长度替代；长度不等报告差异并只遍历共有元素，避免越界。
- `9ca6c7f`：已移植对象图比较、空引用、数组索引路径及精确/浮点字段规则。
  原版按单侧地址去重，Java按两侧对象身份对去重，避免共享actual对象掩盖不同expected对象；
  循环可终止，不检测引用别名拓扑是否完全相同。Java同步深度优先遍历，不保证上游延迟child visitor的输出顺序。
  原生地址与memcmp对象布局不适用于Java，引用仅显示null/present，普通字段按Java值比较。
- `ae3c0f1`：已移植独立浮点比较和有界byte[] C字符串比较；普通精确断言继续使用JUnit。
  **保留上游规则区别**：TestComparator浮点相等为 `abs(a-b) < 0.0001`；
  DiffVisitor浮点字段不同为 `abs(a-b) > 0.0001`，因此边界、NaN/无穷行为不同。
  数组浮点元素为数值精确比较（NaN不同、正负零相等），不自动套用容差。
  strcmp忽略NUL后内容；无终止符Java显式拒绝，不复制C++越界读取。
- `6288354`：CMake静态库/C++17模块不适用Java；由现有Gradle测试源集装配，无新增构建模块。

验证：`ObjectDifferencesTest` 五项，加第一批四项，共九项/两类；
日志 `core/build/upstream-visitor-01.log`，XML `.gradle/path-port/upstream-visitor-01/core/test-results/pathPortTest`。
以上是测试基础设施的Java适配，未执行原DLL差分，未改变生产游戏逻辑或精确回归断言。
主线数据字段及边界已完成本批审查，结论、调用证据与能力缺口见下方第1组。

## 剩余工作，按优先级核销

### 1. 数据符号与边界

- `185ae45`：当前实现已等价（本提交的空值/符号问题）。
  `codec/excel/MonStats.Entry`、`table/schema/MonStats`、`excel/txt/MonStats`八个Miss字段均为String，
  `NativeMonStats.SCHEMA`也保留String，不经过uint16 ID转换，不会把缺失值-1变成65535。
  生产 `Actioneer.spawnMonsterAttackMissile`（约6300行）对A1/A2空值返回false，查表失败也返回false；
  `hasMonsterAttackMissile`使用hasText判空。现有A1/A2互相fallback策略不由本次类型修正改变。
  回归 `UpstreamDataBoundaryTest` 对真实1.10f MPQ与运行时八列逐行比较，并验证空白及命名fixture。
- `4d45674`：当前实现已等价（字段符号修正）；生产加速度能力仍待修复/实现。
  `codec/excel/Missiles.Entry.Accel`及`table/schema/Missiles.Accel`为有符号int，
  `NativeMissiles`/`LosslessTxtTable.getNativeInt`保留负数，无unsigned mask。
  回归验证-32768/-1/0/32767/空白，并对真实MPQ与运行时Accel逐行比较。
  **不能宣称速度行为等价**：全生产源码无`.Accel`读取；`ServerEntityFactory.createMissile`
  当前由Vel与Slow Missiles计算初速（约1330行），`MissileData.acceleration`仅声明/重置。
  本提交仅改C++成员类型，Java无需重复类型补丁；接入完整加速度不是这个字段修正的默认范围。
- `606dce4`：原生位图越界修正不适用当前Java结构；唯一掉落去重能力待修复。
  上游`ITEMS_HasUniqueBeenDroppedAlready`把`>4096`改为`>=4096`，保护128个uint32位图；
  Java没有对应固定4096位表或按unique ID位移访问，无法触发该4096越界。
  生产`item/ItemGenerator.findUnique`按code/level/enabled筛候选，
  `engine/server/item/LootManager`按TC选择品质，均无game级already-dropped/nolimit检查。
  不添加未接线的假位图或把“没有越界”宣称为“已有唯一掉落去重”。

本批证据：新增`core/src/test/java/com/d2moo/testutil/UpstreamDataBoundaryTest.java`四项；
日志`core/build/upstream-data-01.log`，XML `.gradle/path-port/upstream-data-01/core/test-results/pathPortTest`。
这些结论仅核销上述增量问题；未完成的加速度/唯一去重作为明确能力差距保留，不重写生产系统。

### 2. 目标选择与动画速度

- `615b204`：普通追踪与circling路径的目标消息区别。
- `5be5503`：攻击检查沿monster owner链查找。
- `b634ef7`：目标列表slot8。
- `cce700b`：MonsterMsg标志检查。
- `6e58e94`、`996f2f3`：无used skill与monster fallthrough的attack-rate规则。
- `ae9fe6b`、`ae15c97`：velocity modifier类型分支、GetHit与block模式判断。

### 3. 技能、伤害与事件

- `3a47a6e`：Energy Shield提交须区分清理与行为差异。
- `ea5b045`：装备耐久消耗的随机范围。
- `dca46ac`：EventFunc04单位类型检查。
- `8d319cb`、`1084eb0`：Slow事件分支的break/fallthrough。
- 已有Charged Bolt、Blood Golem、Do085记录不重复实现；补逐提交生产调用与测试链接。

### 4. 任务与物品交易

- `3c3c30b`：ACT1Q4石柱排列发生碰撞时的处理。
- `b39358d`：任务日志flags赋值而非OR。
- `b0f13ed`、`e3e6075`：A1Q3状态cycler、A1Q2切关卡逻辑。
- `1f97a98`、`d7392e3`：NPC intro返回值、Wirt尸体的任务ID。
- `7d780e6`、`629a241`：错误卷轴处理、出售书籍时的skill ID字段。
- `edf97ee`：角色progression提取。

### 5. 怪物生成与AI

- `2008a22`：生成坐标索引与参数边界。
- `89a4696`：WanderToTarget行为与命名，按实际diff核销。
- `dbd2771`：怪物AI交互中的对象释放后使用，核对Java对应生命周期。
- 已有Quill Rat/WarpDist记录补生产证据；不重复做完整生成系统。

### 6. 网络、存档与校验

- `d68bf6a`：packet0x68的字段宽度/offset；若本项目协议不同，记录不适用依据。
- `eeb3362`：每次调用记录一次warp attempt。
- `f553315`：stat/guid包长度，按本项目协议实现判断。
- `9fd325c`：CRC32表、byte索引和最终取反；核对实际存档调用，避免与其他checksum算法混用。

### 7. 仅收口上游寻路增量

- `47687ce`：已有生产异常size修复记录，核验当前覆盖。
- `959db29`：物品/对象逐步追踪检查，核销现有静态目标策略与相关适配。
- `9afe3b5`：提前量写输出点而非原path target，核销实际调用及无对应原生状态的范围。
- `a145e73`：速度仅在超过最大值时钳制，区分独立D2PathStep与实际速度路径。
- `4009acb`：玩家按pattern铺碰撞足迹，关联现有生产网格证据。
- `e36bc70`与`ea5d6e5`按最终合并代码整体核销，1.10f不引入1.13+目标房间gate。
- 不以移植全部pathType、原生fp16移动循环或替换生产A*作为这几项更新的默认验收条件。
- 若某项修正依赖本项目尚无的原生状态，记录具体依赖与实际影响，单列待决，不借此默认启动完整迁移。

### 8. 版本、工具链与混合提交

- 1.11+/1.14充能技能查找门槛、Ubers等不直接引入1.10f；核对版本guard后记录不适用。
- C++构建、clang-tidy、调试器、地址注释、结构命名/布局等逐项分类，不机械翻译到Java。
- “Fix”“Another fix”等含混标题及混合提交必须读diff，不能只按标题忽略；一个提交可拆成适用与不适用子项。

## 暂停的扩展支线

完整替换生产A*、全套原生动态路径状态、Act/Zone长期运行时桥、共享seed接线及新增快照加固，不再作为每轮默认工作。
现有代码保留，不自动回退；仅在某个上游增量修正确有必要依赖时处理最小范围。

## 全量非merge提交索引

以下索引确保不漏项，状态仅代表当前证据，不是最终完成率。按上游日志顺序列出。

- `e36bc70` Gate the path solve's target room check on >= 1.13 — 版本门槛待核销；不把后版本规则引入1.10f
- `c64028e` Move the charged-skill lookup gate down to 1.11 — 版本门槛待核销；不把后版本规则引入1.10f
- `509c4ce` Skip charged skills in SKILLS_GetHighestLevelSkillFromSkillId on 1.14 — 版本门槛待核销；不把后版本规则引入1.10f
- `185ae45` Make MonStats.txt missile columns signed — 当前实现已等价；String空值及真实运行时八列回归见第1组
- `45b2f0c` Seed quill rat spread with the unit id — 有移植记录；最终验收需关联生产代码与测试
- `9a701ba` Reject out-of-range upgraded missiles in SKILLS_SrvDo085 — 有移植记录；最终验收需关联生产代码与测试
- `4d45674` Make D2MissilesTxt::wAccel signed — 字段符号已等价；生产加速度缺口另记第1组
- `8b424dc` Cap charged bolt lifetime before truncating it to a byte — 有移植记录；最终验收需关联生产代码与测试
- `47687ce` Reject out-of-range sizes in D2Common_11281_CollisionPatternFromSize — 有移植记录；最终验收需关联生产代码与测试
- `615b204` Send a unit target from sub_6FC65C70 unless the path is circling — 待逐项核销；不能据此断言当前代码缺失
- `5be5503` Follow the target's monster owner chain in sub_6FCBD900 — 待逐项核销；不能据此断言当前代码缺失
- `a145e73` Clamp path velocity only when it exceeds the maximum — 已有独立实现/审查；生产适用性待核销
- `9fd325c` Fix CRC32_Compute — 待逐项核销；不能据此断言当前代码缺失
- `b634ef7` Scan target list slot 8 in sub_6FCF2110 — 待逐项核销；不能据此断言当前代码缺失
- `d68bf6a` Write packet 0x68's a6 as a dword at offset 0x0B — 待逐项核销；不能据此断言当前代码缺失
- `959db29` Fix the per-step check for a path toward an item or object — 已有独立实现/审查；生产适用性待核销
- `eeb3362` Record one warp attempt per call in CLIENTS_NotifyWarpAttempt — 待逐项核销；不能据此断言当前代码缺失
- `1ee8230` Annotate 1.14d addresses of path, collision, AI and event functions — 待逐项核销；不能据此断言当前代码缺失
- `062e18d` Cast ENV_HALF_CIRCLE_DEGREES to double in ENVIRONMENT_UpdateLightIntensity — 待逐项核销；不能据此断言当前代码缺失
- `427946c` Fix the warp exclusion in sub_6FC66260 — 有移植记录；最终验收需关联生产代码与测试
- `9afe3b5` Lead the returned point in sub_6FDAB940, not the path target — 已有独立实现/审查；生产适用性待核销
- `4009acb` Lay a player's collision footprint by pattern — 已有独立实现/审查；生产适用性待核销
- `ea5d6e5` Keep the path solve's target room check on 1.14d — 版本门槛待核销；不把后版本规则引入1.10f
- `6e58e94` Handle a unit with no used skill in UNITS_CanAnimModeUseAttackRate — 待逐项核销；不能据此断言当前代码缺失
- `996f2f3` Fix monster fallthrough in UNITS_CanAnimModeUseAttackRate — 待逐项核销；不能据此断言当前代码缺失
- `ae9fe6b` Fix monster fallthrough in UNITS_CanAnimModeUseVelocityModifier — 待逐项核销；不能据此断言当前代码缺失
- `ea15c97` Fix UNITS_IsAnimModeGetHit testing the block modes — 待逐项核销；不能据此断言当前代码缺失
- `6d84b6d` Debugger: spawn SuperUnique and normal monsters by id — 待逐项核销；不能据此断言当前代码缺失
- `bf36b6b` Fix truncated and empty names in the debugger's UTF-8 conversion — 待逐项核销；不能据此断言当前代码缺失
- `7d780e6` Reject a mismatched scroll instead of exiting in sub_6FC49AE0 — 待逐项核销；不能据此断言当前代码缺失
- `edf97ee` Fix character progression extraction in PLRSAVE2_CheckPlayerFlags — 待逐项核销；不能据此断言当前代码缺失
- `3c3c30b` Fix ACT1Q4_SetMonolithOrder skipping a monolith on a collision — 待逐项核销；不能据此断言当前代码缺失
- `75bbbcb` Review comments — 待逐项核销；不能据此断言当前代码缺失
- `7d36c42` Type the game's 0x20 slot as D2ClientInfoStrc* — 待逐项核销；不能据此断言当前代码缺失
- `3841296` ci: keep the checks new to clang-tidy 18 out of WarningsAsErrors — 待逐项核销；不能据此断言当前代码缺失
- `6dd4879` ci: stop linting the D2.Detours patch sources instead of the whole build — 待逐项核销；不能据此断言当前代码缺失
- `802ec6e` ci: pin clang-tidy's analysis target to x86 — 待逐项核销；不能据此断言当前代码缺失
- `8d355e4` ci: drop AnalyzeTemporaryDtors from .clang-tidy — 待逐项核销；不能据此断言当前代码缺失
- `486f6b9` Update vswhere action version and path for clang-tidy — 待逐项核销；不能据此断言当前代码缺失
- `b39358d` Fix quest log flags being OR'd instead of assigned — 待逐项核销；不能据此断言当前代码缺失
- `1c7d34c` DRLG: address review feedback — 有移植记录；最终验收需关联生产代码与测试
- `b0f13ed` fix: use the Act 1 Quest 3 status cycler — 待逐项核销；不能据此断言当前代码缺失
- `e3e6075` Fix incorrect logic in ACT1Q2_Callback03_ChangedLevel — 待逐项核销；不能据此断言当前代码缺失
- `b5b0888` DRLG: fix nine bugs closing the gap to 1.10f — 有移植记录；最终验收需关联生产代码与测试
- `0478bf3` Clean up D2EventTimerQueueStrc and Event.cpp readability — 待逐项核销；不能据此断言当前代码缺失
- `f553315` Templatize D2GSPacketSrvStatAndGuid for correct packet sizes — 待逐项核销；不能据此断言当前代码缺失
- `89a4696` Fix and rename sub_6FCD09D0 to AITACTICS_WanderToTarget — 待逐项核销；不能据此断言当前代码缺失
- `2008a22` Fix multiple issues in sub_6FC6A8C0 — 待逐项核销；不能据此断言当前代码缺失
- `3a47a6e` Clean up decompiler output in SKILLS_EventFunc24_EnergyShield — 待逐项核销；不能据此断言当前代码缺失
- `ea5b045` Fix incorrect range calculation in SUNITDMG_DrainItemDurability — 待逐项核销；不能据此断言当前代码缺失
- `dca46ac` Fix incorrect unit type check in EventFunc04 — 待逐项核销；不能据此断言当前代码缺失
- `8d319cb` Fix missing break statements in SKILLITEM_EventFunc19_Slow — 待逐项核销；不能据此断言当前代码缺失
- `2bc0c20` Fix health removal from incorrect unit in EventFunc26 — 有移植记录；最终验收需关联生产代码与测试
- `dbd2771` Fix use-after-free in monster AI interact handling — 待逐项核销；不能据此断言当前代码缺失
- `1f97a98` Fix missing return in PLRINTRO_MaskNpcIntroFlag — 待逐项核销；不能据此断言当前代码缺失
- `629a241` Fix incorrect field for book skill ID in sell item function — 待逐项核销；不能据此断言当前代码缺失
- `d7392e3` Fix incorrect quest ID for Wirt's body in QUESTS_ObjectEvents — 待逐项核销；不能据此断言当前代码缺失
- `cce700b` Fix incorrect flag check in MonsterMsg — 待逐项核销；不能据此断言当前代码缺失
- `1084eb0` Fix — 待逐项核销；不能据此断言当前代码缺失
- `103a4ca` Add missing uber tristram portal check — 待逐项核销；不能据此断言当前代码缺失
- `f5519a1` Update D2.Detours for cmake 4.x — 待逐项核销；不能据此断言当前代码缺失
- `c776a77` Should now  be compatible with cmake 4 — 待逐项核销；不能据此断言当前代码缺失
- `731ac26` Add default generator preset — 待逐项核销；不能据此断言当前代码缺失
- `6a98122` ARRAYSIZE => ARRAY_SIZE — 待逐项核销；不能据此断言当前代码缺失
- `8016900` ITEMS_HasUniqueBeenDroppedAlready: use ARRAY_SIZE — 待逐项核销；不能据此断言当前代码缺失
- `2b694df` Small refactors — 待逐项核销；不能据此断言当前代码缺失
- `bc817e7` More 1.11 offsets — 待逐项核销；不能据此断言当前代码缺失
- `a73bb2e` Add 1.11 D2DrlgStrc reorder. — 待逐项核销；不能据此断言当前代码缺失
- `0ebe9ce` Add various 1.11 addresses — 待逐项核销；不能据此断言当前代码缺失
- `606dce4` fix: Fix OOB write — 原生位图修正不适用；Java缺game级唯一掉落去重，见第1组
- `833ccb4` feat: More drive by renames — 待逐项核销；不能据此断言当前代码缺失
- `4e39cbc` Add 1.14d references — 待逐项核销；不能据此断言当前代码缺失
- `f13a6fb` Another fix part 5 — 待逐项核销；不能据此断言当前代码缺失
- `d859413` Another fix part 4 — 待逐项核销；不能据此断言当前代码缺失
- `d8871a0` Another fix part 3 — 待逐项核销；不能据此断言当前代码缺失
- `8d99ff0` Are we there yet? — 待逐项核销；不能据此断言当前代码缺失
- `0981831` More build issues — 待逐项核销；不能据此断言当前代码缺失
- `cb63017` Fix quest build issues — 待逐项核销；不能据此断言当前代码缺失
- `1cf948b` Fix build issues for 1.14 — 待逐项核销；不能据此断言当前代码缺失
- `fa43e0b` Cleanup 1.11 build defines — 待逐项核销；不能据此断言当前代码缺失
- `d970ad6` Update source/D2Game/src/ITEMS/Items.cpp — 待逐项核销；不能据此断言当前代码缺失
- `3715c91` Rename — 待逐项核销；不能据此断言当前代码缺失
- `a364f9d` Fix grid item pointer names/accessors, add missing ITEMDROPFLAGs — 待逐项核销；不能据此断言当前代码缺失
- `2bcb057` Fix compiling (again) — 待逐项核销；不能据此断言当前代码缺失
- `1ad0d4f` Fix compiling — 待逐项核销；不能据此断言当前代码缺失
- `d1c4d1a` Update game creation stuff and game struct — 待逐项核销；不能据此断言当前代码缺失
- `32e6466` Some remaining uber checks — 待逐项核销；不能据此断言当前代码缺失
- `d6dbf43` Another teleport tweak for uber baal — 待逐项核销；不能据此断言当前代码缺失
- `87d32e9` Add uber prevent monster heal penalty — 待逐项核销；不能据此断言当前代码缺失
- `6b3b312` Add uber portal spawning code — 待逐项核销；不能据此断言当前代码缺失
- `ad5746c` Add random class skill for hellfire torch — 待逐项核销；不能据此断言当前代码缺失
- `298d156` Add uber torch drop — 待逐项核销；不能据此断言当前代码缺失
- `80501fb` Add uber boss modifiers and demon spawner stuff — 待逐项核销；不能据此断言当前代码缺失
- `e5d37f9` Add uber quest dungeon support — 待逐项核销；不能据此断言当前代码缺失
- `0730f6f` Add uber boss spawning code — 待逐项核销；不能据此断言当前代码缺失
- `d374418` Update game structure — 待逐项核销；不能据此断言当前代码缺失
- `8cfec82` Add uber izual ai — 待逐项核销；不能据此断言当前代码缺失
- `0c53522` Add uber baal ai param tweaks — 待逐项核销；不能据此断言当前代码缺失
- `51f01af` Add uber diablo ai — 待逐项核销；不能据此断言当前代码缺失
- `1a73877` Add uber mephisto ai — 待逐项核销；不能据此断言当前代码缺失
- `a722ba0` Add uber baal ai — 待逐项核销；不能据此断言当前代码缺失
