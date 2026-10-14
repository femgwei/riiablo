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
2. **导弹速度与寿命分离**（待开始）。参考 `b7e5f680`、`8c87f64a`。
   分开验证每 tick 位移、初始化后寿命、删除时序和重连剩余寿命。
   Charged Bolt Sentry 的 77 tick 是需核实的 1.14d 观察，不能直接覆盖所有 helper。
3. **Blade Fury 发射时序**（待开始）。参考 `eecd52c9`。
   riiablo 已区分 srvmissilea/cltmissilea；现有测试允许 5 tick 后再发射，
   dark-magic 观察到 6 tick 间距。核查 1.10f SrvSt26/SrvDo48 的计数边界，
   首次发射、持续发射、释放和魔力消耗需分别验证。
4. **ItemRatio 选择**（待开始）。参考 `964d70e9`。
   riiablo 已有 NativeItemQualityResolver/ItemRatio.get，需先对照
   DATATBLS_GetItemRatioTxtRecord 核实 Version、Uber、Class Specific 的真实语义，
   避免照搬不同项目的数据约定。
5. **数据查询身份审计**（待开始）。参考 `6da9484a`、`06158fb3`。
   用 ID 与行号故意错位的夹具验证显式 ID、位置索引和名称引用；按原生消费者逐项核实。
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

- 第一批：体力耗尽保留终点短步长；专项验证结果将在完成后记录。
- 后续条目仍未完成，分析结论不计作行为验收。
