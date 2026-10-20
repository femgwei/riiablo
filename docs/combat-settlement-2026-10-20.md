# 生产战斗结算统一（2026-10-20）

## 范围与依据

本批修复实际运行的战斗入口，不以旧 `DamageCalculator` / `DamageApplier` 的测试通过
代替生产行为。D2MOO revision：`0c0bbd1e6bef9181a3da1c046523af622a925416`。

- `D2Game/src/UNIT/SUnitDmg.cpp::SUNITDMG_CalculateTotalDamage`（759 起）：
  PvP 17% 在各减免行之前；`dwPiercePct` 是固定减伤的 `/1024` 缩放值。
- `SUNITDMG_ApplyResistancesAndAbsorb`（939 起）：固定减伤 → 抗性 → 百分比吸收
  （上限 40%）→ 固定吸收；物抗玩家上限 50%，元素玩家通常 75%、最高 95%，
  怪物低于免疫的抗性不受玩家上限限制。魔法没有穿透列，毒素没有 MDR/吸收列。
- `SUNITDMG_ExecuteEvents`（1149–1306）：物理吸取以吸收回血前的生命封顶；
  吸取后再恢复吸收生命、扣血，零伤害不应清除不足 1 HP 的存活单位。
- `MONSTER/MonsterUnique.cpp::MONSTERUNIQUE_CalculatePercentage`（234 起）：
  大数提前除法，不能一律替换成 long 乘完再除；伤害和吸取分别保留该截断。
- `SKILLS/SkillSor.cpp::SKILLS_AuraCallback_StaticField`（439 起）：
  先按原始负抗性补偿，再走普通结算（包括难度惩罚、穿透、PvP、MDR 和吸收）。
  补偿不包括后加的难度惩罚；已折入 CharData 的惩罚须从补偿输入还原。
- `SKILLS/SkillPal.cpp::SKILLS_ApplyThornsDamage`（1555 起）：
  保留玩家/佣兵攻击者的反伤百分比 `(percent+4)/8`，随后用普通物理包减免；
  不改写各技能原有的反伤基数和来源过滤。

以上是逆向源码推导，不是原版进程逐击捕获。本批无须借助猜测或外站补足这些规则。

## 实现

`server-domain/NativeMitigationMath` 统一无随机数的减免内核；普通攻击、原生怪物元素、
直伤/反伤、固定点闪电、Static Field 与 DamageRate 包复用。保留各来源的端点/RNG
生成策略，不通过改抽样顺序“修正”最终数字。固定减伤、吸收和最大抗性的活动状态属性
在各生产适配器一致生效。Static Field 增加来源穿透接线。

`DamageEvent` 携带已经算好的吸收生命（HP），`DamageSettlement` 在吸取后、扣血前
唯一执行；Actioneer、MissileCollisionSystem、StateUpdater、技能/光环/陷阱入口移除
自行回血。纯吸收命中仍提交，防御回调可见完整元素包，DEATH_DELAY 禁止吸收回血。
不可变 `DamageAppliedEvent.lifeAbsorbed` 记录实际回血；`damage` 仍表示结算包，
`lifeLost` 才是实际损失，不把过量伤害误当实际扣血。死亡发布仍保留来源专属 shatter 阶段。
纯吸收的资源事实不会播放零伤害通用撞击声；既有疼痛/AI 反馈仍由实际生命损失门控。

吸取另外修复大数百分比截断，并允许有法力属性的佣兵使用原版百分比吸蓝分支；
不对无相应资源属性的佣兵凭空创建资源。

## 回归设计

- `NativeMitigationAuditTest`：独立字面算例，覆盖 PvP、MDR、DamageRate、大数截断、
  99% 怪物抗性、免疫/穿透、魔法/毒素例外、反伤、冰冷时长顺序。
- `NativeProductionMitigationIntegrationTest`：公共生产适配器及活动状态属性。
- `DamageSettlementIntegrationTest` / `LeechSettlementTest`：吸取与回血顺序、过量伤害、
  零伤害回血、上限、死亡延迟、快照、重入/重复提交、死亡去重。
- `ServerClientCombatSyncEcsScenarioTest`：吸收后存活的小数生命序列化并应用到客户端。
- `SorceressStaticFieldIntegrationTest`：真实技能事件的 40% 吸收上限与穿透/免疫。
- Nova / Telekinesis：纯吸收结算、死亡延迟、40% 上限和低生命死亡阈值；
  `DamagePresentationBoundaryTest`：纯吸收只写资源，不制造撞击音效或元素效果。
- 冻结迁移算法不改写。4000 个攻击快照仍检查命中/格挡/随机流与 host/domain 等价；
  冻结完整包比较限定在无减免/穿透的兼容子域。2000 个直伤及 2000 个持续/静态包
  改用独立 D2MOO 转写参考计算，原冻结函数保留作历史对照，不冒充原版预期。

## 仍未完成

本批不是“整个战斗系统完成”：玩家/佣兵武器系数和全部来源 8.8 端点、SrcMissDmg、
所有技能/精通/特殊回调、完整佣兵/召唤物/复活单位伤害倍率、原生 stat-list 事件队列
（包括防御技能事件与减免行的完整调度）、高生命值 float 事件精度仍需独立迁移。
现有 poison 原生快照与旧技能总毒伤路径也没有被本批强行合并。
DamageRate 此处对齐的是现有持续面积伤害入口，不表示所有带该字段的技能创建策略已放行。
保留现有 opt-out；不能把选定测试通过解释为所有游戏机制完全等价。

全 core、双机实机/联机和原版进程逐击采样须分别验收。

## 验证结果

- `:server-domain:monsterCombatMathTest`：271 项通过；
  `:server-domain:monsterCombatParityTest`：106 项通过（含本批新增 9 项独立审计）。
  `:server-domain:test`：282 项通过。以上失败/错误/跳过均为 0；数学与默认任务
  有覆盖重叠，不能直接相加当作不同用例数。
- `:core:monsterCombatAcceptance` 中全部 core 分层及架构测试通过：131 类、
  去重后 1317 项，失败/错误/跳过均为 0。分层包括普通攻击 41、怪物行为 831、
  战斗 62、数据 45、元素 113、导弹 200、模式元素 48、毒素 154、追击 82、
  特殊 38、召唤 10、架构 3 项；同一用例会被多个分层覆盖，不能直接相加。
- 定向 `:core:test`：`com.riiablo.engine.server.combat.*`、
  `SorceressStaticFieldIntegrationTest`、`ServerClientCombatSyncEcsScenarioTest`，
  首轮 161 项通过。最终追加 Nova、Telekinesis、AmazonSkillSpecialization 和
  DamagePresentationBoundary，全体 27 类、235 项通过，失败/错误/跳过均为 0；
  包括亚马逊 38、Nova 21、Telekinesis 11、表现边界 4 项。最后命令返回 BUILD SUCCESSFUL，
  同时重新请求 domain 数学/严格审计任务，均通过（UP-TO-DATE）。
- `:desktop:compileJava`、`:server:d2gs:compileJava` 通过。
  `:desktop:offscreenCamp -Pd2Version=1.10f` 使用本机真实 1.10f MPQ，
  营地 manifest `result=PASS`、60 帧，退出日志 `CLIENT_SHUTDOWN result=PASS`。
  这是启动/资源/退出烟测，不是战斗画面对照或原版 EXE 运行采样。

资源相关命令均附加 `-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F`。
并行任务共享 Gradle 项目缓存导致前两轮类输出被清理、出现 NoClassDefFoundError，
这些运行不计入验收。有效复验同时使用独立 `--project-cache-dir` 和本地 init 脚本
重定向所有模块 buildDir，未修改其他任务文件；init、对照源码、报告位于忽略的
`.gradle/` 下，不纳入提交。运行期间另遇并行任务暂态接口不匹配，待其更新后重跑。

扩大七职业/GoldenDamage/光环/陷阱/Vitals 回归共 181 类、701 项，694 通过、7 失败，
错误/跳过为 0，不宣称扩大回归全绿。其中特定亚马逊尸体穿透场景保留了全局随机
命中，单独用修改前代码运行可通过；现按同类碰撞测试固定 nextInt 输入，隔离独立的
95% 命中概率，不修改生产命中规则。
修正后亚马逊全类 38 项通过；本批导致的 Nova/Telekinesis 旧吸收预期已按源码修正，
最终分别 21/21、11/11 通过。扩大回归保留的 7 项失败记录不删除，最终未解决的是下列 6 项。

其余 6 项使用 Git 中修改前的战斗生产源码覆盖编译、优先装入独立测试 classpath 后，
在同一 1.10f 资源中仍逐项复现，未通过更改数值规则或放宽断言掩盖：

- `NecromancerExplosionPresentationTest`：客户端测试装配缺少 `clientContext`。
- `DruidShapeShiftTest.werebearUsesNativeDamageDefenseAndLycanthropyLife`：71/64 不符。
- `NativeDruidShapeDataTest.werewolfAndWerebearUseNativeSrvDo116AndAuraStats`：64/71 不符。
- `DruidShockWaveTest.srvDo008CreatesFiveDamageSnapshotsSharingOneHitSet`：共享命中集为空。
- `NativeNecromancerPoisonNovaDataTest`：EMin 断言 16、实际表值 14。
- `NativePaladinBlessedHammerDataTest`：dParam 第二项断言 50、实际表值 0。

后两项是在读取原始表字段时即失败，尚未进入本批减免逻辑；这份修改前战斗类对照
不是整个旧版本 checkout 的全量验收。上述问题继续保留为独立后续工作。

后续处理见 [七职业遗留回归收尾](player-combat-followup-2026-10-20.md)：上述 6 项
按 D2MOO 与本地原始 MPQ 区分为夹具遗漏、旧辅助数值及错误机制/表值断言；
保留本页历史失败记录，不将后续修正后的结果倒填为本批当时已通过。
