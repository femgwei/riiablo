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
   玩家创建与两条复活路径仍直接采用 CharStats 的 6/9 值，需要独立核查单位换算、
   状态加成和系统执行顺序。D2MOO `Units.cpp:UNITS_GetBaseVelocity` 返回
   `nWalkSpeed << 8`，`PathMisc.cpp:sub_6FD5CEB0` 将速度转换为路径向量；
   完整位移链核实前不全局乘速度常量。
2. **导弹速度与寿命分离**（已完成初步核查）。参考 `b7e5f680`、`8c87f64a`。
   分开验证每 tick 位移、初始化后寿命、删除时序和重连剩余寿命。
   riiablo 的 `Missile.set` 已仅为零速度导弹设置 `nativeLifetimeFrames`，移动导弹的
   `Range` 保持距离终点；现有静止导弹压力回收测试通过。本批新增回归，锁定移动导弹
   `Range` 不得变成帧寿命。Charged Bolt Sentry 的 77 tick 是需核实的 1.14d 观察，
   不能直接覆盖所有 helper。下一步仍需逐类核对特殊 helper 的寿命覆盖。
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
6. **音效重定向与分组权重**（待开始）。参考 `b3715dd0`。
   Audio 当前均匀选择 Index 附近记录；Sounds.Entry 没有 Redirect/Group Weight。
   先检查 1.10f 表和客户端实现，再处理重定向目标组、权重和非法引用。

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
