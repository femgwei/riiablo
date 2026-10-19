# 怪物测试分层与原版对照

## 运行入口

数值层无需安装目录，不启动 MPQ、ECS 或客户端：

```powershell
./gradlew.bat :server-domain:monsterCombatMathTest --no-daemon
./gradlew.bat :server-domain:monsterCombatParityTest --no-daemon
```

前者是现有算术、随机消费契约的兼容性回归；后者是严格原版对照审计，**当前有已知失败，不能当作全绿门禁**。没有禁用失败用例、没有改成当前实现的预期值。普通 `:server-domain:test` 与快速入口显式排除 `monster-parity-audit` 标签；审计必须单独运行，绝不能用普通测试通过宣称原版一致。

以下 core 入口均可追加 `'-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F'`：

- `:core:monsterDataTest`：真实表格、等级、难度、A1/A2/S1、多人倍率及属性来源。
- `:core:monsterCombatTest`：结算包、命中/格挡阈值、宿主适配和 ECS 事件；不等待 AI 自己攻击。
- `:core:monsterAttackTest`：普通攻击及原生动画/复活链路。
- `:core:monsterPursuitTest`：静止/移动目标追击及重寻路回归。
- `:core:monsterSummonTest`：产怪、孵化、生成上限及阻挡。
- `:core:monsterSpecialTest`：自爆、弹体、诅咒及已有特殊技能。
- `:core:monsterAiTest`：已有 AI 决策回归，排除新增端到端场景类。
- `:core:monsterBehaviorTest`：保留原有广域聚合入口；不是所有层的别名，亦不包含严格原版审计。

每个任务有独立报告：对应模块 `build/reports/tests/<任务名>/index.html`。按修改范围选任务；跨模块或玩法阶段验收再跑广域聚合。共享的 RiiabloTest 每类重新加载资源仍然存在，本轮没有引入全局资源缓存或并行执行，以免污染可变全局状态。

本轮验证：无资源快速层 47 项通过；core 数据 16、结算 62、攻击 23、追击 7、召唤 10、特殊 26 项均通过，AI 决策入口 719 项通过。首轮六个 core 任务一次执行含构建约 2 分 1 秒，之后补入两项格挡阈值并复跑结算。XML 的 testcase 时间不包括类级资源初始化，不能把它当任务总耗时；首次构建与后续增量执行也不可直接比较。

## 样本来源与可信度

2026-10-19 在线核验：

- [暴雪 Arreat Summit：角色基础](https://classic.battle.net/diablo2exp/basics/characters.shtml)：明确命中公式 `100*AR/(AR+DR)*2*alvl/(alvl+dlvl)` 与 5%–95% 上下界。文字公式不能替代实现中的分步整数截断。
- [Amazon Basin：Attack Rating](https://www.theamazonbasin.com/wiki/index.php/Attack_Rating)：攻击等级及命中资料可访问，但现页包含新版内容，不作为 1.10f 特例的唯一依据。
- [Amazon Basin：Damage](https://www.theamazonbasin.com/wiki/index.php/Damage)：列出固定减伤先于抗性的顺序，并链接 1.10 伤害计算器。并未获得原版进程的逐击样本记录。
- The Phrozen Keep 的论坛及知识库页面本次请求超时，未把未读取的页面当证据。

主要可复核依据是本地 D2MOO，revision `0c0bbd1e6bef9181a3da1c046523af622a925416`：

- `source/D2Game/src/UNIT/SUnitDmg.cpp`：`SUNITDMG_IsHitSuccessful`（0x6FCC1E70），`SUNITDMG_ApplyDamageBonuses`，`SUNITDMG_ApplyResistancesAndAbsorb`。
- `source/D2Common/src/DataTbls/MonsterTbls.cpp`：`DATATBLS_CalculateMonsterStatsByLevel`（ordinal 11261）。
- `source/D2Game/src/MONSTER/MonsterMode.cpp`：`sub_6FC627B0` 的攻击档位和 LoD 噩梦/地狱多人倍率。

这些是**逆向源码推导样本**，不是已执行原版 EXE/DLL 后采集的 golden 数据；D2MOO 自身也须在歧义处用本地 1.10f DLL 校核。现有“旧实现冻结样本”只证明重构兼容性，名称带 Native/Golden 也不自动等于原版实测。

## 独立预期与当前差异

### 第二阶段：已修正命中零分母与攻击物理减伤

`NativeCombatMath.hitChance` 在 AR+防御为零时改用 100 的因子，再做等级比和 5%–95% 限制；保留合成夹具的最低等级 1 防除零保护，不宣称补齐负 AR/负防御等全部原版路径。

`NativeAttackResolver` 的攻击物理包复用 `NativeDirectDamageMath.nativePhysicalDamage`：固定减伤先于抗性；玩家物抗上限 50%、怪物 100%、负抗性下限 -100%；抵消后允许零伤害。庇护所只消除亡灵的正物抗，负抗及固定减伤仍保留。玩家原始物抗 >=100 时也不得误走怪物免疫。该改动共用于近战与物理弹体攻击；没有修改已有反伤/legacy direct 分支或元素结算顺序。

实际扣血场景发现 `CombatSnapshotFactory` 原先未把 `normal_damage_reduction` 放入攻击快照，导致装备固定减伤在实际近战/弹体攻击中被忽略。现在补齐属性和状态层贡献；这是本次新增 HP 验证揭示的接线缺口，不仅是运算顺序差异。

首轮严格审计由 17 项中 9 项失败降至 3 项：力量与增强伤害合并、两个 8.8 端点仍未修。新增普通快速门禁 20 项验证命中边界和物抗/减伤/庇护所；另补 5 项实际怪物攻击到玩家 HP 的 ECS 场景。旧冻结 resolver 不随生产公式改写：无减伤子域继续逐字段比较冻结包，全部 4000 输入继续比较 host/domain 及随机消费；独立原版期望负责已修正规则。

本阶段定向验证：domain 默认 83、快速层 67；core 战斗包/迁移 112、结算 62、攻击 28、特殊 26 项通过。严格审计 17 项中仍有 3 项失败，命令非零退出为已知差异，未隐藏为跳过。

工作区另一批 `DamageEvent` / `DamageAppliedEvent` 事件重构在扩大验证时尚未完成，曾阻塞测试编译。使用 HEAD 干净导出加本轮专属代码文件隔离验证：战斗包/迁移与所有 `*GoldenDamageTest` 合计 174 项、攻击 28 项、特殊 26 项通过。未回退、覆盖或提交另一批事件改动。真实 1.10f 营地 smoke manifest 与客户端退出清理均为 PASS；包含未完成测试编译的组合命令整体非零，不能称组合构建成功。

隔离副本完整怪物聚合 808 项通过，失败/跳过均为 0；包含完整冷编译的扩大验证组合返回 0，用时约 10 分钟，不代表日常单个分层入口耗时。

以下差异清单保留为**修复前基线**；其中命中零分母与前四种减伤差异已由本阶段修正，只有加成和小数抽取仍是当前待办。

`MonsterMeleeOriginalParityTest` 不调用生产公式生成期望值：

- AR=100、防御=200、同为 10 级：先取整 AR 因子为 33，最终 33%；随机值 32 命中，33 未命中。AR=2、防御=1、等级 20/10：先取整为 66，再计算得到 88%，而不是最终才取整。
- AR 与防御均 0：D2MOO 的因子默认为 100；等级 10/10 得 95%，10/90 得 20%。当前实现均为 5%。
- 物理伤害下端 100，固定减伤 10、物抗 50%：原版顺序 `(100-10)*50%=45`，当前 40。
- 伤害 100 被固定减伤 100 完全抵消：原版 0，当前最低仍保留 1。
- 玩家物抗 75%：原版按 50% 上限，伤害 100 应剩 50，当前剩 25。
- 伤害 100、固定减伤 10、物抗 -50%：原版 135，当前 140。
- 基础伤害下端 100、力量 50、增强伤害 50：原版合并百分比得到下端 200，当前两次相乘为 225。
- 原版物理伤害用 8.8 定点数抽取：10–11 的上端为 `2815/256=10.99609375`，不是 11；相同上下限 10–10 会先把上限扩成 11 再抽取。当前近战整数包丢失该精度。

严格审计复跑 17 项：8 通过、9 失败；默认 domain 回归 63 项通过、快速层 47 项通过，确认标签隔离。审计组合命令整体返回失败是预期行为，不代表普通回归失败。失败保留为生产结算迁移待办，而非资源缺失或随机不稳定。固定 RNG 是明确输入，不声称当前 NativeRng 的种子序列与原版相同。

`MonsterMeleeDataReferenceTest` 使用选定安装的实际 MonStats/MonLvl，六种怪物、Classic/LoD、三难度、三攻击档位、人数 1/2/8，共 324 组合，逐项核对三个属性（972 断言）。预期直接按 D2MOO 的表格运算推导，不调用生产比例/倍率辅助函数。等级显式为 5/40/85；区域生成等级选择另由已有测试覆盖。该层不是独立原版资源快照，安装版本/Mod 会改变输入。

`MonsterMeleeSettlementTest` 用固定单位 RNG 验证宿主结算包的命中、未命中、格挡和零伤害边界。伤害 20 的固定整数断言是兼容性契约，原版小数精度差异由严格审计单独报告；不把手动扣血冒充生产 ECS 生命写入，后者仍由攻击场景负责。

## 下一阶段生产修复与实测

建议按“命中边界 → 减伤顺序/上限 → 8.8 物理包全链路”的顺序修复，每步独立提交。小数精度涉及包、伤害应用、事件和联网，不宜只改一个公式；也不能通过修改旧快照预期掩盖受影响技能。

原版实测时记录：1.10f EXE/DLL 与 MPQ 版本/哈希、Classic/LoD、难度、人数、区域、怪物 ID/等级/词缀/攻击模式、双方实际属性、运行/格挡/状态、动作帧、随机输入（若可捕获）、伤害包及命中前后原始 8.8 HP。只看屏幕整数血量无法区分 10 与 10.996 HP，少量随机攻击也无法证明命中概率。实测样本确认后新增来源为 executable-captured 的数据，与 source-derived 样本并存。
