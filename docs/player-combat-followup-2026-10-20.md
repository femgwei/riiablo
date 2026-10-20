# 七职业战斗遗留回归收尾（2026-10-20）

## 范围与原版依据

本批承接生产结算批次中保留的 6 项失败，不把错误断言反向写入生产机制。
D2MOO revision：`0c0bbd1e6bef9181a3da1c046523af622a925416`；表值取自本机
`G:\BaiduNetdiskDownload\Diablo II 1.10F`，按 MPQ 优先级读取原始 TSV，
另由类型化表加载与实际技能状态交叉检查。未运行原版 EXE 逐击采样。
本次 `Patch_D2.mpq` SHA-256：
`245e7835650d4c2db8515f92eccdcafcb4331744443654eb4f7ef20af6782ffe`。

- `SKILLS/SkillDruid.cpp::sub_6FCFE0E0`（187 起）及
  `SKILLS_SrvDo116_Wearwolf_Wearbear`（262 起）：变身状态逐列计算 AuraStatCalc。
  本地 Wearbear 的 Param1..5 为 `50,7,25,5,50`，伤害 `ln12`、护甲 `ln34`、
  生命 `par5 + skill('Shape Shifting'.ln34)`。等级 3、变形术等级 2 的增益应为
  伤害 64%、护甲 35%、生命 75%。生产状态入口原本按表计算，修正的是旧数值辅助函数
  与错误测试预期；不声称本批重做了熊人生产状态逻辑。
- `SKILLS/SkillAma.cpp::SKILLS_SrvDo008_MultipleShot_Teeth_ShockWave`（476 起）
  创建独立弹道，没有共享整次施法的永久命中集。
  `MISSILES/MissMode.cpp`（5002、5070、5123 起）在目标上查询/安装 `STATE_JUSTHIT`，
  冷却值来自 NextDelay。本地 shockwave 为 NextHit=1、NextDelay=4；因此另一施法/弹道
  同样受这 4 帧限制，过期后未命中过该目标的弹道可以命中。保留现有生产门控，
  删除错误的共享命中集要求，新增跨施法与到期后行为断言。
- Poison Nova 原始行 EMin=14、EMax=25、HitShift=4、ELen=50、Param8=10。
  基础 8.8 每帧速率为 224..400；毒匕首 2 点、毒爆 3 点时最低速率 336。
  修正原始表及固定点算例，不修改正确的生产速率计算。
- `MISSILES/MissMode.cpp::MISSMODE_SrvDmg05_BlessedHammer`（4416 起）分别读取
  dParam1 的亡灵加成与 dParam2 的恶魔加成；本地 blessedhammer 为 `50,0`，
  不凭空增加恶魔加成。修正与已有真实锤子集成测试互相矛盾的原始表断言。
- 尸爆/毒爆表现测试补齐 `clientContext` 注册，保持客户端上下文为必需依赖，
  不通过放松生产注入约束掩盖夹具遗漏。
- 扩大入口额外纳入此前未被职业名称匹配选中的 `BlessedHammerIntegrationTest`，
  发现吸收算例仍按旧的“PvP 最后”顺序计算。依据
  `UNIT/SUnitDmg.cpp::SUNITDMG_CalculateTotalDamage/ApplyResistancesAndAbsorb`，
  100 魔法伤害 +50% 目标加成 → PvP 17% 得 25 → 50% 魔抗得 12 →
  20% 吸收得 2，最终扣血 10；修正旧预期的吸收 15，不改生产结算规则。

以上规则可由逆向源码与原始表直接确定，本批无须额外借助外站推测。

## 新增回归与可重复入口

`NativePlayerCombat110fAuditTest` 直接解析 MPQ 原始表，独立锁定上述数值；
熊人另用等级 1、2、3、20 的字面预期交叉检查实际状态与兼容辅助函数。

`DruidShockWaveTest` 扩展实际施法 → 弹体碰撞 → 结算事件 → 死亡事件 →
Vitals 序列化/客户端应用链路：五弹交叠不重复结算/死亡，结算事实先于死亡发布；
非致命命中另核对眩晕的持续帧、来源和技能 ID 经 StateP 到达客户端。
这里是内存中的真实 serializer 往返，不是双机网络或渲染验收。

新增 `:core:playerCombatRegression`，保留上一轮扩大七职业/GoldenDamage/光环/陷阱/
Vitals 选择，并补入祝福之锤、结算边界、表现边界、客户端同步和架构约束：

```powershell
./gradlew.bat :core:playerCombatRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --console=plain
```

这是选定战斗回归入口，不是全 core 或所有技能原版等价验收；各聚合任务存在覆盖重叠，
不能把计数简单相加。共享工作区并行验证时，必须同时隔离 Gradle 项目缓存和 buildDir。

## 验证结果

- `:core:playerCombatRegression`：207 类、873 个不同用例，失败/错误/跳过均为 0。
  包括 `DruidShockWaveTest` 6 项、原始表独立审计 4 项、祝福之锤集成 8 项；
  原有 6 项失败全部通过，未禁用或跳过。
- `:server-domain:monsterCombatMathTest`：24 类、271 项通过；
  `:server-domain:monsterCombatParityTest`：11 类、106 项通过，均无失败/错误/跳过。
  这两项在最终组合实际执行，不是 UP-TO-DATE；不同入口有重叠，不与 core 计数相加。
- 模块依赖门禁、`:desktop:compileJava`、`:server:d2gs:compileJava` 通过。
  最终统一命令返回 `BUILD SUCCESSFUL`，耗时 9 分 42 秒。
- 德鲁伊所有者审计同步纠正震波误判；TSV 保持 30 条技能记录、每行 12 列。
  本批未跑全 core、全怪物聚合、客户端烟测、双机联机或原版进程采样。

本机首次启动 Gradle 时发生 native memory allocation 失败，改用小堆、单 worker 和
独立缓存/输出后运行。首轮扩大回归 207 类、872 项中，旧 6 项全部通过；剩余
50 项是同一次 Mockito/Byte Buddy 外部附加失败的连带错误，另 1 项是新增纳入的
锤子旧吸收预期。修正算例，并仅在忽略的本地验证脚本中启用当前 JVM 自附加后复跑，
未改写测试框架或跳过这些测试。首轮尚未编译最后补入的致命震波同步场景，
最终计数以完整复验报告为准。

有效报告位于忽略的 `.gradle/combat-settlement-verified/<module>/test-results/<task>/`；
本地隔离/内存参数脚本不纳入提交。最终生产代码和测试完成后只补充本节文档结果，
未再修改业务实现或测试断言。

## 后续边界

仍需玩家/佣兵武器全链路 8.8 端点、SrcMissDmg、完整精通/特殊回调、召唤/复活倍率、
原版 stat-list 调度、初始单位随机种子及大生命 float 精度迁移。
本批不移除现有 opt-out；实机、联机与原版运行采样仍须单独验收。
