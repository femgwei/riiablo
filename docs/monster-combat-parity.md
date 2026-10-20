# 怪物测试分层与原版对照

## 运行入口

七职业技能与结算/客户端同步的扩大回归另使用 `:core:playerCombatRegression`，
原版表值核验与适用范围见 [七职业收尾记录](player-combat-followup-2026-10-20.md)。
该任务与下述怪物聚合互相补充，均不代表全 core 或完整原版等价。

数值层无需安装目录，不启动 MPQ、ECS 或客户端：

```powershell
./gradlew.bat :server-domain:monsterCombatMathTest --no-daemon
./gradlew.bat :server-domain:monsterCombatParityTest --no-daemon
```

前者是现有算术、随机消费契约的快速回归；后者是独立原版期望审计，**第四阶段已修正原有 17 项中的全部已知差异**。没有禁用失败用例、没有改成当前实现的预期值；这是选定样本通过，不代表所有怪物、技能与原版实测全部一致。普通 `:server-domain:test` 与快速入口仍显式排除 `monster-parity-audit` 标签；审计必须单独运行，绝不能用普通测试通过代替原版对照。

第五阶段另增物理弹体 8 项，第六阶段另增瞬时元素弹体 8 项，第七阶段另增毒速率/时长 6 项，第八阶段另增毒状态 8 项，第九阶段另增来源元素混合 8 项，第十阶段 A 另增模式元素 8 项，第十阶段 B 另增普通近战元素 8 项，第十一阶段另增撕裂伤口 15 项，第十二阶段另增基础属性 11 项，当前严格审计合计 97 项；下文各阶段的测试数量均为当时的历史记录。

一次执行全部怪物分层、独立审计、AI 行为及架构门禁：

```powershell
./gradlew.bat :core:monsterCombatAcceptance '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --console=plain
```

该入口不启动客户端，不等于整个 core 测试集，也不包含另一台电脑或原版进程验收；各分层仍保留独立报告，存在重叠，数量不能相加当作去重总数。可在同一命令追加 `:desktop:compileJava :server:d2gs:compileJava`；资源营地检查另行运行 `:desktop:offscreenCamp`。

以下 core 入口均可追加 `'-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F'`：

- `:core:monsterDataTest`：真实表格、等级、难度、A1/A2/S1、多人倍率及属性来源，以及 Fallen 近战动画动作点。
- `:core:monsterModeElementTest`：MonStats 模式规则、动作进入/清理/受击保留、原生序列第零动作点、多弹共享、近战实际扣血/状态/Vitals、双字单位随机流，以及真实工厂创建暴击与碰撞主人 Crit；不代表所有技能和事件队列的原版调度。
- `:core:monsterCombatTest`：结算包、命中/格挡阈值、宿主适配和 ECS 事件；不等待 AI 自己攻击。
- `:core:monsterAttackTest`：普通攻击及原生动画/复活链路。
- `:core:monsterPursuitTest`：静止/移动目标追击、全部 Pathfinder*Test，以及 RoomEx 休眠/唤醒回归。
- `:core:monsterSummonTest`：产怪、孵化、生成上限及阻挡。
- `:core:monsterMissileTest`：怪物物理/瞬时元素/纯表格毒素及选定来源混合弹体端点、发射快照、碰撞/状态扣血/事件/同步，以及刺毛鼠、FingerMage*Test 和已有弹体生命周期。
- `:core:monsterElementalTest`：瞬时元素及选定来源混合弹体全链路与真实萨满火弹表格/行为；不代表所有元素技能、毒云或地面持续伤害。
- `:core:monsterPoisonTest`：纯表格及选定来源混合毒速率/时长、实际状态逐帧扣血/到期、无毒回血/毒/燃烧/药水 HPREGEN 合算、普通怪物撕裂伤口触发/速率/刷新、难度适配，以及既有毒标枪/毒云/毒匕首/毒新星/狂犬病/淬毒兼容回归；不代表完整回血与毒伤调度已经原版化。
- `:core:monsterSpecialTest`：自爆、弹体、诅咒、火/冷/电强化回调及已有特殊技能。
- `:core:monsterAiTest`：已有 AI 决策回归，排除新增端到端场景类。
- `:core:monsterBehaviorTest`：保留原有广域聚合入口；不是所有层的别名，亦不包含严格原版审计。

每个任务有独立报告：对应模块 `build/reports/tests/<任务名>/index.html`。按修改范围选任务；跨模块或玩法阶段验收再跑广域聚合。共享的 RiiabloTest 每类重新加载资源仍然存在，本轮没有引入全局资源缓存或并行执行，以免污染可变全局状态。

初始分层阶段的历史验证：无资源快速层 47 项通过；core 数据 16、结算 62、攻击 23、追击 7、召唤 10、特殊 26 项均通过，AI 决策入口 719 项通过。首轮六个 core 任务一次执行含构建约 2 分 1 秒，之后补入两项格挡阈值并复跑结算。最新结果见第十二阶段。XML 的 testcase 时间不包括类级资源初始化，不能把它当任务总耗时；首次构建与后续增量执行也不可直接比较。

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

### 第三阶段：合并力量与增强伤害百分比

按 D2MOO `SUNITDMG_ApplyDamageBonuses`，适用的力量、增强伤害及当前目标类型加成先相加，再计算百分比，不再让力量和 ED 相乘。例如基础下端 100、力量 50、ED 50 现在为 200，不再是 225。合并后使用 -90% 下限，负增伤也会生效；整数核保留原版“基础值加上截断后的百分比”顺序，而非直接截断最终乘积。

这是**加成合并修正，不是完整物理原版迁移**：当前仍对已抽取的整数 HP 应用加成，原版则先缩放 8.8 上下端点再抽取；现有弹体不套用力量的宿主策略、最低基础伤害、武器力量/敏捷系数、元素及 PvP 顺序本轮没有扩展。不能把整数核的负百分比舍入当成 8.8 端点已经一致。

新增 13 项快速测试：基础 100 的独立下端样本覆盖组合加成、目标类型、负值及下限，并验证修正结果继续传入暴击、转换、减伤和吸血，且不新增随机抽取。实际 AI→近战→玩家 HP 场景由 5 项扩为 9 项，增加 200 HP、减伤后 95 HP，以及负增伤的 10/50 HP。旧 resolver 继续冻结：兼容子域限制为无物理减伤、力量为零且非负的单阶段加成；全部 4000 输入仍核对宿主/独立域及随机消费，没有把旧 oracle 跟着生产规则改写。

本阶段 domain 默认 96 项、快速数值 80 项通过。严格审计保留原有独立期望，17 项中 15 通过、2 失败：仅剩 `10–11` 的排他小数上端及 `10–10` 扩展上端。审计组合命令仍返回非零；普通回归通过不代表原版审计全绿。

当前工作区 core 验证遇到共享测试输出文件占用，以及 Fallen 动画模式断言失败，不能称该组合成功。复用上轮已通过的隔离基线，仅同步本轮公式和测试验证：战斗/迁移及 `*GoldenDamageTest` 174 项、攻击 32 项、特殊 26 项全部通过，失败/跳过均为 0。

隔离副本完整怪物聚合 812 项也全部通过，失败/跳过为 0；含增量编译、数值层及上述 core 组合耗时约 8 分 43 秒。该耗时包含重复资源加载与扩大验收，不是单个日常分层测试的耗时。最终夹具明确区分玩家攻击恶魔/亡灵目标，domain 默认与快速入口再次通过。

工作区另有伤害事件/结算层重构，本轮未覆盖或提交其改动，8.8 全链路应在明确事件与生命写入边界后继续。

### 第四阶段：怪物近战 8.8 物理包贯通生命、事件与同步

`CombatSnapshotFactory` 为非玩家、非弹体、非预计算的近战快照启用 `fixedPointPhysical`；直接构造的旧快照仍默认关闭，以明确保留旧工具的整数契约。生产普通怪物近战与使用同一入口的 A1/A2/S1 攻击不需要手动开关。玩家预计算武器/踢击、物理弹体、直接伤害和反伤策略没有一并迁移，佣兵武器系数等也不能据此宣称原版完整。

`NativePhysicalDamageMath` 按 D2MOO 无武器分支将 min/max 与 `item_normaldamage` 转为 8.8；在加成前修正最低值和相同/反转上限，再将合并增伤及单独的 min/max 百分比作用于端点，最后抽取排他上端 `[min,max)`。保留 `MONSTERUNIQUE_CalculatePercentage` 对较大数值提前除法的顺序；不可表示的合成/Mod 数值使用宿主饱和保护，不模拟原版有符号溢出。力量与 ED 不再对已经抽取的整数伤害进行事后缩放。

包新增 `physicalScale` 和物理/吸血的 HP 访问器；物理原始值及物理吸血在该尺度下保存。`totalDamage` 只作丢失小数的旧整数摘要，实际结算必须用 `totalDamageHp()`，它独立处理物理和元素尺度，并排除持续毒伤的即时扣血。普通整数元素仍用原抽取契约；物理转元素时才提升元素包尺度以保留转换小数，原有元素抗性/PvP/压碎等策略并未完整原版化。

Actioneer 的生命/事件读取、怪物 fire-hit、吸收恢复及物理比例使用 HP 访问器；弹体物理事件也改用访问器，但弹体尚未启用新物理抽取策略。毒伤速率按包尺度归一化并保留原有每帧 8.8 截断，避免放大转换包的毒伤或改变旧整数包的毒速率。刺客渐进伤害准备仍是玩家预计算整数包的修改点，未假装已经迁移成原版定点武器计算。

`DamageEvent` 继续承载 HP 单位的可修改请求，`DamageAppliedEvent` 新增同单位的物理通道事实。DamageSettlement 和 VitalsSerializer 原已支持小数，无需修改联网 schema；新增 9 项真实 AI→近战→原始 HP→请求事件→已结算事件→Vitals 序列化/客户端应用测试，逐位核对 8.8 HP。包括排他上端、相同端点、先缩放端点、负增伤、暴击、负抗性，以及减伤后只剩 `127/256 HP` 的非零命中。同步测试是实际 serializer 往返，不声称已跑双机联机。

本轮同步逐位断言使用 10000 HP 量级；现有事件/协议的 float 不是整个 32 位 8.8 范围的无损表示。超过 65536 HP 时不能保证每个 1/256 步进都可表示，大生命值的原始定点同步仍是单独待办，未借本轮修改联网格式。

快速层新增 19 项固定预期，覆盖端点、普通加伤、端点增伤、大值除法、转换/减伤/吸血、混合尺度、吸收与持续毒伤；首轮迁移回归发现负元素随机值被提前清零，已修正并保留旧路径专门回归。严格审计原有 17 项的数值期望保持不变，仅为怪物样本明确启用生产同款精度策略，两个小数端点不再失败。

4000 组迁移中至少 250 组成功落入原生定点分支；整数兼容子域继续比较旧冻结包与 RNG，定点分支比较宿主/独立域的包和 RNG，并由独立固定预期校验规则。3000 组特殊入口迁移继续比较冻结宿主适配输入；新增精度选择单独断言，定点输出用冻结输入的副本经过新域策略核对，未修改冻结适配器来迎合生产输出。

最终生产代码数值验证：domain 默认 115、快速 99、严格审计 17 全部通过；core 战斗/迁移/Golden 及 Vitals 合计 186、结算 62、特殊 26、怪物聚合 821 全部通过，失败/错误/跳过为 0。真实 1.10f 营地 manifest 与退出清理 PASS（`build/fixed-physical-verified.log`、`desktop/build/fixed-physical-smoke-final/rogue-encampment-manifest.txt`）。

扩大组合中攻击 41 项有一次 Fallen A1 动画模式断言失败（随机选择了 A2，其他 40 项通过），故该组合整体返回非零，不能称整个组合成功。该测试原断言要求 FA A1 的第 7 帧却未固定 A1/A2 选择；现在仅将夹具 `params[3]` 固定为 0，保留原 A1 模式、动作帧、真实伤害断言，不放宽成接受任意模式。相关入口收尾复验见 `build/fixed-physical-closeout.log`。

收尾复验返回 0：攻击 41 项、完整怪物聚合 821 项全部通过，失败/错误/跳过为 0，含增量编译约 4 分 6 秒。本轮未执行整个 core 测试集，未执行双机联机或原版进程逐击采样。

### 第五阶段：普通怪物物理弹体的独立 8.8 策略

本阶段迁移非技能、无 `SrcMissDmg`、无物理 `DmgSymPerCalc` 的普通怪物弹体。玩家、佣兵、技能/公式弹体继续保留旧入口；不能把这一阶段视为所有投射物已经与原版完全一致。

依据同一 D2MOO revision 的 `D2Common/src/Units/Missile.cpp`：`MISSILE_GetMinDamage` / `MISSILE_GetMaxDamage` 在表格伤害上应用 HitShift；`MISSILE_CalculateDamageData` 将来源 HP 先扩展至 8.8，再乘 `SrcDamage/128`。来源 1 HP、SrcDamage=64 现在保留为 128/256 HP，不会提前截成零。端点增伤取 min/max 百分比中的较大者，与 damagepercent 合为一个发射快照，而不是像近战一样分别作用于端点。普通无武器怪物不套用力量或投掷武器数值。

依据 `D2Game/src/MISSILES/MissMode.cpp`：`MISSMODE_RollDamageValue` 遇到任一非正端点即返回零；正端点反转时交换；相等时不扩展一 HP，也不消耗区间随机数。`MISSMODE_FillDamageParams` 在排他抽取后才合并目标类型百分比并应用 -90% 下限。例：10..11 HP 上端为 2815/256，+75% 后为 4926/256；-90% 后为 282/256，不能复用近战先缩放端点的 281/256 结果。大数百分比也按此函数直接乘除，不套近战百分比辅助函数的提前除法。异常 Mod 数值采用宿主饱和保护，不模拟原版溢出。

`NativeMissilePhysicalDamage` 是独立不可变标量值，保存端点、合并增伤、恶魔/亡灵加成及庇护所旁路；发射后修改主人属性或增伤状态不会改变这些已捕获值。A2 替换来源攻击档位时同步替换原始端点，保留捕获的百分比；复制既有伤害快照的子弹体同时传递该值，重新建立技能/表格包及组件回收时清除它。原始物理尺度贯通减伤、转换、生命、两阶段事件与 Vitals 往返；不足一 HP 的物理包仍可参与盾牌格挡。

明确的原生空/零伤害包也保留为已初始化快照，不退回主人普通攻击而凭空产生伤害。工厂在创建阶段即为佣兵关闭新策略，而非创建后仅清除精度标记；玩家/佣兵旧入口与空包处理另有保留契约测试。

新增独立分层入口 `:core:monsterMissileTest`，不把广域 `monsterBehaviorTest` 冒充弹体层。快速层新增 15 项固定预期，严格审计增加 `MonsterMissileOriginalParityTest` 8 项（与原近战 17 项分别报告）；实际弹体场景包含端点、小数来源、HitShift、负增伤、减伤、未命中、盾挡、发射状态冻结、A2 及真实刺毛鼠表格。端到端场景使用合成弹体行隔离算术，并真实经过碰撞/扣血/事件/序列化，不把手动扣血当生产链路；真实表格检查是另一类来源验证。

边界仍然明确：暴击/致命一击在原版创建时预掷、原版每弹体种子与碰撞时 RNG 的整体顺序尚未迁移；当前复用既有宿主命中/暴击调度。元素顺序、物理转化类特殊回调、SrcMissDmg 子弹体原生继承、武器/佣兵系数及双机实测仍待办。此处的“发射快照冻结”仅指本阶段捕获的物理端点与百分比，不宣称所有状态/特殊效果均已冻结或完整原版化。

阶段主回归 `build/missile-physical-verified.log` 返回 0（约 8 分钟）：domain 默认 130、快速 114、严格审计 25；core 战斗/迁移/Golden/Vitals 186、结算 62、弹体 49、近战 41、特殊 26、怪物聚合 821 全部通过，失败/错误/跳过为 0。首次验证 `build/missile-physical-tests.log` 因旧快照结构约束只允许基本字段而失败；新不可变值已补充“类 final、全部字段 final 且为基本类型”的约束，并在上述主回归通过。没有放宽成允许任意运行时对象。

主回归后额外修正明确零伤害包的 fallback，并将佣兵 opt-out 提前至创建入口；追加两个实际零伤害场景和一个玩家/佣兵保留契约。该收尾版本另行复验，不能把此前的 49 项和主回归日志当作这三个新增用例的结果。

最终收尾 `build/missile-physical-closeout.log` 返回 0（约 5 分 35 秒，含构建和启动检查）：弹体 52（其中新增物理场景/契约 19）、近战 41、特殊 26、怪物聚合 821 全部通过，失败/错误/跳过为 0。桌面端和 D2GS 编译通过；真实 1.10f 资源营地 manifest 与退出资源清理均 PASS，见 `desktop/build/missile-physical-smoke-final/rogue-encampment-manifest.txt`。未运行整个 core 测试集、双机联机或原版进程逐击采样；营地 smoke 是 riiablo 读取原版资源，不是执行原版 EXE 的战斗测试。

### 第六阶段：纯表格怪物瞬时元素弹体

`NativeMissileElementalDamage` 与物理包分开，只接入非技能怪物弹体的火、电、冰、魔法通道，且要求 `SrcDamage=0`、`SrcMissDmg=0`、无 `DmgSymPerCalc`/`EDmgSymPerCalc`、`ApplyMastery=false`、`pSrvDmgFunc=0`、`DamageRate=0`。负来源比例哨兵也不 opt-in。来源元素、技能公式、精通、特殊回调、毒伤及按帧伤害均保留旧策略，不用一个精度开关将它们混同。玩家与佣兵在创建/碰撞入口继续 opt-out；防御方佣兵分类也尚未完整迁移。

依据同一 D2MOO revision 的 `D2Common/Units/Missile.cpp` 中 `MISSILE_GetMinElemDamage` / `MISSILE_GetMaxElemDamage`，表格基础值与等级分段加成先相加，再按 HitShift 生成原始 8.8 端点，避免先截成整数 HP。`D2Game/MISSILES/MissMode.cpp` 的 `MISSMODE_RollDamageValue` 对元素同样要求两个端点均为正、交换反转端点、排他抽取，相同端点不扩展。不可变快照同时保存冰冷时长；重新建立伤害包和组件回收清除，继承既有快照的子弹体复制此值。

依据 `SUnitDmg.cpp` 的 `sgDamageStatTable` / `SUNITDMG_ApplyResistancesAndAbsorb`：先减固定魔法减伤的 `stat<<8`，再处理抗性；玩家最大抗性最高 95%，怪物不套玩家 75% 上限，负抗性最低 -100%。火/电/冰穿透保留正负号，但不能突破怪物原始 >=100 的免疫；魔法行没有穿透属性。百分比吸收上限 40%，先百分比再固定吸收，损伤减少与回血使用同一小数尺度。抗性与百分比吸收保留 `MONSTERUNIQUE_CalculatePercentage` 大值提前除法；例 5001 HP 的原始包按 50% 抗性剩 `640100/256 HP`，不是直接长乘除的 `640128/256`。异常 Mod 数值继续使用宿主饱和保护，不模拟原版溢出；本阶段不宣称负 MDR 等异常域或负伤害内部残值完全原版化。

冰冷时长使用独立冷抗性/穿透行，既不扣 MDR 也不吸收，之后保留不能冰冻、冰冻时长减半和既有难度处理。新增实际 StateUpdater→COLD 状态断言：100 帧、50% 冷抗、100 MDR 的命中即使瞬时伤害为零，仍安装 50 帧 COLD。防御状态层中的 MDR、百分比与固定吸收也接入新策略；不顺带改写旧 Nova、地面伤害或其他冻结回归的运算契约。

新增快速层 26 项、独立审计 8 项；ECS 场景/创建契约 39 项覆盖四通道事件、小数 HP/Vitals 往返、零/反转端点、HitShift、等级分段、减伤/穿透、先吸收回血后扣血（满血/缺血/零伤害）、状态属性、飞行中快照、真实 shafire1..5 及延后策略的 opt-out。实际碰撞使用合成行隔离运算，再用真实资源行单独验证，不把手动扣血当生产链路。

主人穿透继续保留现有宿主的发射快照契约；本阶段未迁移 D2MOO 结算时主人属性读取、完整 RNG 顺序/种子、创建时暴击、特殊旁路/PvP/佣兵，也没有获取原版 EXE/DLL 的逐击记录。原版资料依据充分覆盖本阶段选定数值子域，不等于全部怪物战斗已经完成。

数值层 `build/missile-elemental-domain.log` 返回 0：domain 默认 156、快速 140、严格审计 33 全过。扩大验证 `build/missile-elemental-verified.log` 返回 0（约 8 分 41 秒）：战斗/迁移/Golden/Vitals 186、元素 55、弹体 89、近战 41、特殊 26、怪物聚合 821 全过，失败/错误/跳过为 0；桌面/D2GS 编译及真实 1.10f 资源营地 manifest、退出清理 PASS（`desktop/build/missile-elemental-smoke-final/rogue-encampment-manifest.txt`）。普通 domain 任务在扩大组合中为已通过结果的 UP-TO-DATE，并非又执行了一次。

首次元素组合因冷状态夹具未安装 StateUpdater、回收测试误把 `set()` 当作 reset 而失败；之后补齐 StateUpdater 所需工厂系统装配，改为实际 ECS 删除/回收断言，单项冷状态入口通过，随后上述扩大组合全过。最初扩大启动命令有一次 PowerShell 未引用属性参数导致 `.10f` 被当作任务，修正参数引用后才得到上述成功日志。未运行整个 core 测试集、双机联机或原版进程逐击采样；营地 smoke 仍只是 riiablo 读取原版资源。

扩大验证构建后，复核又收紧了负 `SrcDamage` 哨兵及物理公式的 opt-in，并增加两个创建边界用例；上述 55/89 不包含这两个新增用例。最终版本的窄入口收尾验证单独记录，不能把扩大组合冒充最终新增用例的结果。

最终收尾 `build/missile-elemental-closeout.log` 返回 0（约 1 分 12 秒，含 core 增量编译）：元素 57（新增类 39）、弹体 91 全过，失败/错误/跳过为 0。最终两个 guard 未改变 API 或已覆盖普通弹体策略，未重复整个 AI/Golden 聚合、桌面/D2GS 编译或营地 smoke；这些扩大结果对应上一段明确说明的构建版本。

### 第七阶段：纯表格怪物毒素弹体的速率与时长

沿用第六阶段的严格创建边界，只为 `EType=pois` 增加策略：非技能、普通怪物、无来源伤害/来源弹体、公式、精通、特殊伤害回调及 `DamageRate`。不可变元素值新增独立 `poisonLength`，毒素端点仍按表格与等级加成后 HitShift 捕获，但其单位是 **8.8 HP/帧**，不是瞬时伤害或整段总伤害。复用既有快照传递、子弹体复制、重建和组件回收边界，不引入含 Attributes/ECS 引用的快照。

原版依据仍为 D2MOO revision `0c0bbd1e6bef9181a3da1c046523af622a925416`：`D2Common/Units/Missile.cpp` 的毒素表格字段、`D2Game/MISSILES/MissMode.cpp` 的排他抽取和毒长度读取，以及 `SUnitDmg.cpp` 的 `sgDamageStatTable`、`SUNITDMG_ApplyResistancesAndAbsorb`、`SUNITDMG_ApplyPoisonDamage`。不使用生产函数生成独立审计的预期，也没有取得原版 EXE/DLL 的逐击样本。

毒抗只降低速率，`item_poisonlengthresist` 单独降低时长；两行都读取带正负号的毒穿透，各自按原始抗性判断怪物免疫。玩家速率最大抗性最高 95%，时长抗性固定上限 75%；怪物不套玩家上限，负抗最低 -100%。毒素既不扣 MDR，也不使用元素吸收；保留原版百分比大值提前除法，零抗不额外丢低位。防御状态列表的时长抗性和抗毒神殿也接入快照。

时长行单独应用 Classic/LoD 与 DifficultyLevels 的难度惩罚，即使角色元素抗性已由 CharData 合入难度，也不漏掉或重复添加时长惩罚。新增六个快照适配用例，覆盖自定义表格惩罚、Classic 噩梦/地狱和已折叠元素抗性。防御方佣兵分类/系数仍沿用既有边界，未宣称完整迁移。

原生毒速率使用包的专用 `poisonDamagePerFrame`，不写入即时 `elementalDamage[POISON]`。碰撞后按 HP/帧直接安装 POISON，不再将已经是速率的值除以持续时间；零时长、神殿或零速率不安装状态，更不会把毒速率当即时伤害。混合物理/毒素命中的即时请求与已结算事件只包含物理伤害，后续状态 tick 单独结算。旧技能/毒云仍使用原有单位契约与入口，没有整体打开精度开关。

新增快速数值 21 项、独立原版推导审计 6 项。最终 ECS/创建场景 31 项，逐位检查碰撞当帧无毒扣血、每帧原始 HP/伤害请求/已结算事实/Vitals 往返、到期无额外一帧、未命中/格挡/免疫/零时长/无状态组件、物理与毒速率不混入同一即时通道、状态时长抗性、飞行中端点/穿透冻结、同一来源弱毒不刷新/等强毒可缩短、重建/回收和延后策略 opt-out。怪物免疫夹具明确使用已转化的友方怪物攻击敌方怪物，并追加 99% 抗性的非免疫控制，避免把阵营拒绝碰撞误报为免疫成功。实际安装的所有符合边界的毒素表格行另有遍历创建断言；这些与合成碰撞夹具分开，不声称每种真实怪物 AI 均已发射验证。

本阶段只保证选定速率/时长子域及宿主逐帧结算契约，**不是完整毒伤原版迁移**。继续调查时另确认了必须优先补齐的状态层差异：D2MOO `PLAYER/PlrModes.cpp::EVENTS_HpRegen` 将玩家生命下限钳为 256（1 HP），并将回血/毒速率作为 HPREGEN 合算；当前 StateUpdater 仍将毒素作为独立 DamageEvent，可在低生命时杀死玩家。怪物的 `MONSTER/MonsterMode.cpp::D2GAME_MONSTER_ApplyStatRegen_6FC63440` 又有不同的死亡与回血路径，不能套玩家下限。不同攻击者覆盖既有毒状态时，D2MOO 仅替换速率/到期时间、不更换原 stat-list owner；当前宿主会替换来源。上述低血、回血合算、跨攻击者归属与原版事件到期顺序尚未修复，本轮高生命逐帧测试不能证明这些边界一致。

来源混合也保持待办：`MISSILE_AddStatsToDamage` 的主人毒速率已是 8.8、冷/毒长度是相加；`MISSILE_CalculateFinalDamage` 对表格加来源的整个元素包乘 SrcDamage/128，毒长度本身不乘，poison_count 参与后续时长平均。当前旧路径只缩放来源贡献、部分长度取 max，不能直接放开第六/七阶段 guard 充当修复。完整 RNG/创建时暴击、结算时主人属性、技能/公式/精通、PVP/佣兵与大 HP float 精度也仍待办。

首次组合 `build/missile-poison-first.log` 返回 0（约 1 分 16 秒，含编译）：domain 默认 177、快速 161、严格审计 39；最初毒素场景/快照适配合计 35 项全部通过。追加真实资源遍历后 `build/missile-poison-table.log` 返回 0（约 27 秒），毒素 36 项通过。实际安装中有 32 条符合所选创建边界的 pois 行，含 andypoisonbolt、undeadmissile1、mummy1..4、goospit1..5；也包含毒云和药瓶行，这只是创建策略检查，不代表其所有特殊生命周期或玩家使用路径均已迁移。

扩大验证 `build/missile-poison-verified.log` 返回 0（约 8 分 45 秒）：domain 默认 177、快速 161、严格审计 39；core 战斗/迁移/Golden/Vitals 192、毒素 59（含旧技能兼容 23）、弹体 126、瞬时元素 56、近战 41、特殊 26、怪物聚合 821 全过，失败/错误/跳过为 0。第六阶段原来要求 pois opt-out 的一项已移到新毒素层验证，因此瞬时元素入口为 56 而非 57。桌面/D2GS 编译及真实 1.10f 资源营地 manifest、退出清理 PASS，见 `desktop/build/missile-poison-smoke-final/rogue-encampment-manifest.txt`。未跑整个 core 测试集、双机联机或原版进程采样；营地检查仍是 riiablo 读取原版资源，不是运行原版 EXE 战斗。

扩大构建编译测试类后，复核修正了免疫场景的阵营配置，并新增非免疫控制用例；生产代码未再改变。最终毒素/弹体层单独收尾复验，不能把上面的 59/126 冒充已包含最后的夹具修正。

最终收尾 `build/missile-poison-closeout.log` 返回 0（约 1 分钟，含增量编译）：毒素 60（新场景 31、快照适配 6、旧技能兼容 23）、弹体 127 全部通过，失败/错误/跳过为 0。最后只改测试夹具，没有再次重复整个 AI 聚合或桌面/营地检查；生产版本与扩大验证相同。

### 第八阶段：中毒 HPREGEN 合算、玩家生命下限与状态归属

本阶段继续使用 D2MOO revision `0c0bbd1e6bef9181a3da1c046523af622a925416`，以 `PLAYER/PlrModes.cpp::EVENTS_HpRegen`、`MONSTER/MonsterMode.cpp::D2GAME_MONSTER_ApplyStatRegen_6FC63440` 和 `UNIT/SUnitDmg.cpp::SUNITDMG_ApplyPoisonDamage/ApplyBurnDamage` 为依据。第七阶段列出的玩家下限、回血合算与重毒归属差异在此处理；其“尚未修复”描述是上一阶段历史边界。

新增纯 domain 的 `NativePoisonRegenMath`，用原始 8.8 HP/帧合算 aggregate HPREGEN、状态层回血、生命药水和负毒速率，不对 HPREGEN 再左移八位。非零净速率先限制 max HP，再对玩家保留 256（1 HP）；净速率为零不改生命，也不强制抬高原已不足一 HP 的玩家。宿主死亡单位不复活，异常超大合成输入使用 long 计算，未仿真原版有符号溢出。

StateUpdater 对**已有正速率 POISON 的单位**接入上述合算。怪物通过真实 States.txt 的 life 位判断是否扣除基础 HPREGEN，只抑制基础自然恢复，不抑制其余 aggregate/状态层恢复；PREVENTHEAL 拦非负净速率，不拦负速率，怪物没有玩家一 HP 下限，仍可毒死。保留宿主持续伤害的当前 PvP 关系拒绝门。

CharData 提供只消耗一帧 HEALTHPOT 速率、不独立写生命的窄接口；StateUpdater 与后续 PotionRecoverySystem 通过组件的单帧标记交接，不改变生产系统顺序。中毒最后一帧即使立即移除状态，也不会让生命药水重复推进；法力药水仍每帧推进一次。只有合并后生命严格超出 max HP 才取消生命药水，刚好等于 max 不取消。快照副本不推进药水；组件 init/reset 清空交接标记。

负净速率继续通过宿主两阶段 DOT 事件结算，通知的伤害是合并后的净生命损失；正净速率直接写原始生命，为零不产生伤害事件。玩家毒扣血在可变 prepare 回调之后再次限制为当前生命减一 HP，防止回调放大成毒杀。这个事件适配保留现有客户端/奖励边界，并非声称原版 HPREGEN 自身逐帧派发普通攻击事件。

重复中毒或燃烧的等强/更强覆盖只更新速率、到期及宿主 payload，保留最初 stat-list owner；弱速率不刷新。正常到期或移除后再次感染才能建立新 owner，怪物毒死的伤害/死亡事件沿用最初来源。燃烧只同步修正共享 helper 的 owner 策略，没有在此迁移其回血/死亡结算。

新增快速数值用例 14 项、独立字面源码预期 8 项；25 项 ECS 状态/真实生命及法力药水测试覆盖低生命、1/256 精度、净回血正/零/负、怪物自然恢复抑制、阻止回血、毒死一次、跨来源重毒/燃烧、到期及驱散交接、严格超限取消、快照/死亡/PvP 拒绝。另补 4 项真实毒弹碰撞后的一 HP 下限/零净速率及 Vitals 往返测试，原毒弹场景从 31 增至 35。`monsterPoisonTest` 同时纳入原有 PotionRecoverySystemTest，保持药水独立运行的兼容检查。

首轮 `build/poison-state-first.log`：domain 默认 191、快速 175、独立审计 47 全过；core 毒素 90 项中仅旧耐力药水夹具失败（不存在的物品码 `stam`），新增状态 25 项及其他毒素用例通过。夹具修正为实际 Misc 行 `vps` 并断言其 pSpell=9，再增加上述四个实际毒弹用例后，专项 `build/poison-state-focused.log` 返回 0（约 50 秒）：毒素 94（状态 25 + 毒弹 35 + 快照适配 6 + 药水 5 + 旧技能兼容 23）全过，无失败/错误/跳过。未用跳过测试或改生产药水规则来回避资源夹具问题。

最终扩大验证 `build/poison-state-verified.log` 返回 0（约 8 分 41 秒）：战斗/迁移/Golden/Vitals 192、毒素 94、弹体 131、元素 56、近战 41、特殊 26、怪物行为聚合 821 全过，无失败/错误/跳过。domain 默认/快速/独立审计在本组合为首轮通过结果的 UP-TO-DATE，不计作再次执行。桌面端、D2GS 编译及真实 1.10f 资源营地 manifest/退出清理 PASS，见 `desktop/build/poison-state-smoke-final/rogue-encampment-manifest.txt`；营地是 riiablo 读取原版资源，不是原版进程毒伤实测。扩大验证后只补文档，未再修改生产代码或测试。未运行整个 core 测试集、双机联机或原版 EXE/DLL 逐击采样。

本阶段**没有**新增无毒单位的通用自然回血 tick，也没有迁移燃烧/撕开伤口与毒素同时存在的整包 HPREGEN、原版事件队列的到期先后、怪物不足一 HP 时的房间/城镇保护、佣兵药水或完整 PvP 路径。现有状态先结算再减时长仍是宿主调度契约；大 HP 值经宿主 float 事件转换的精度边界仍在。样本是 source-derived，不是运行原版 EXE/DLL 获取的逐击记录。

### 第九阶段：普通怪物飞弹来源元素混合

依据仍为 D2MOO revision `0c0bbd1e6bef9181a3da1c046523af622a925416`：D2Common `Units/Missile.cpp::MISSILE_CalculateDamageData`、`MISSILE_AddStatsToDamage(owner, 8)`、`MISSILE_CalculateFinalDamage`、`MISSILE_SetDamageStats`，以及 D2Game `MISSILES/MissMode.cpp::MISSMODE_FillDamageParams`。MonStats 特殊元素另对照 `MONSTER/MonsterMode.cpp` 的模式/概率临时属性分支；尚未迁移的分支不套用普通来源策略。

`NativeMissileElementalDamage` 扩为不可变五通道发射快照，不保留调用方数组。普通火/电/冰/魔法来源属性先左移八位，与表内端点相加后，**整包**按 SrcDamage/128 缩放；来源毒端点本身已经是 8.8 HP/帧，不再左移。物理仍沿用第五阶段独立的“表内物理＋按比例的来源物理”，不能把元素整包缩放套到物理。

冷长度相加后同样缩放。毒长度相加但不缩放，poison_count 按来源比例缩放，碰撞填包时仅在 count>1 时先除长度，再经过独立 PoisLen 抗性；正的 skill_poison_override_length 替代来源毒长度且不加入来源 count，表内毒长度仍保留。所有元素沿用第六/七阶段各自的减伤、抗性、吸收或毒速率策略。混合毒速率不进入即时伤害，冷/毒无需作为表内主类型也能安装状态；原始 HP、事件及 Vitals 往返继续保留 1/256 HP。

创建时同时捕获来源属性和独立状态层贡献，飞行中修改主人不改变已捕获端点/长度。旧 damage 属性保留诊断/元数据投影，毒仍写原始速率及未平均长度，不从整数投影重新计算新包。技能重建和池回收复用原有清理边界，无联网 schema 改动。选定原生元素包按 FillDamageParams 的火→魔法→电→冰→毒顺序取值，另有 RNG bound 序列测试确保旧宿主元素顺序未变；这只修正通道局部顺序，不代表创建暴击、命中/格挡前后取值或完整种子调度已与原版一致。

新策略仅接入非玩家/非佣兵普通怪物、SrcDamage=1..255、空或已支持 EType、非技能且无 SrcMissDmg、公式、精通、DamageRate、特殊伤害回调的来源混合。旧 VENOMCLAWS、来源燃烧/火时长/眩晕/耐力伤害，及当前攻击模式存在正概率 MonStats El1/2/3 的情况显式 opt-out，仍走原兼容路径；并非把它们修好了。吸血/吸蓝、主人读取时机的完整审计及其他特殊伤害也未随本轮迁移。合成异常大值使用宿主饱和保护，不模拟原版有符号溢出。

新增快速数值 12 项、独立字面 source-derived 预期 8 项、实际创建/碰撞/状态/事件/同步及边界场景 33 项。安装资源中 81 条符合静态边界的来源飞弹行在普通合成怪物主人下通过创建检查；这是表行策略审计，不代表 81 种真实怪物 AI/发射/命中全生命周期验证，更不是原版 EXE 采集。原元素/毒场景中已过时的“正来源必须 opt-out”断言由本轮正来源覆盖替代，其余边界不放宽。

首轮 `build/missile-source-first.log` 返回 0（约 4 分 32 秒）：domain 默认 202 / 快速 186 / 独立审计 55，毒素 122 / 元素 84 / 弹体 158 全过。此轮尚不包含最后四项特殊来源 opt-out 用例及元素 RNG 顺序修正。

扩大组合 `build/missile-source-verified.log`（约 13 分 2 秒）：战斗/迁移/Golden/Vitals 192、毒素 126、弹体 162、元素 88、近战 41、特殊 26、怪物行为 821 全过，无失败/错误/跳过；domain 三入口沿用首轮 UP-TO-DATE，桌面及 D2GS 编译通过。**组合整体返回 1**：offscreenCamp 营地资源启动在 120 秒超时，没有成功 manifest/退出清理证据，不报告启动 PASS。日志有大量地图生成输出及慢帧，尚未证实超时根因。此轮编译后又补了原生元素通道局部取值顺序及其序列断言，最终代码另行窄入口复验。

最终收尾 `build/missile-source-closeout.log` 返回 0（约 5 分 22 秒）：domain 默认 203 / 快速 187 / 独立审计 55，毒素 126 / 弹体 162 / 元素 88 全过，无失败/错误/跳过；桌面及 D2GS 重新编译通过。未调整启动超时阈值，真实 1.10f 资源营地复试 manifest 与退出资源清理均 PASS，见 `desktop/build/missile-source-smoke-closeout/rogue-encampment-manifest.txt`。最终顺序修正后未重复 AI/Golden 广域组合；营地通过不等于原版战斗实测，全 core、双机联机及原版 EXE/DLL 逐击采样未运行。

复现最终收尾（PowerShell，整个命令单行）：
`./gradlew.bat :server-domain:test :server-domain:monsterCombatMathTest :server-domain:monsterCombatParityTest :core:monsterPoisonTest :core:monsterMissileTest :core:monsterElementalTest :desktop:compileJava :server:d2gs:compileJava :desktop:offscreenCamp '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' '-Pd2Version=1.10f' '-PvisualOutput=build/missile-source-smoke-closeout' --console=plain`。

### 第十阶段 A：MonStats 模式元素规则统一与 1.10f DLL 核对

调查确认必须先区分“单次模式刷新算什么”和“何时刷新/清理”。本阶段只完成前者及其既有调用方输入修复，没有把每颗弹体的抽取改为完整动作级缓存；第九阶段对有适用 MonStats 模式元素的原生来源混合 opt-out 继续保留。不能宣称临时 stat-list/发射时序整体已迁移。

首要依据为 D2MOO revision `0c0bbd1e6bef9181a3da1c046523af622a925416`：`MONSTER/MonsterMode.cpp::sub_6FC627B0`、模式切换调用处，以及 `DataTbls/MonsterTbls.cpp::DATATBLS_CalculateMonsterStatsByLevel`。对反直觉细节进一步只读反汇编本地 `G:\BaiduNetdiskDownload\Diablo II 1.10F` 的 DLL：

- D2Game.dll SHA256 `2BD92FB25E3E4B8269930712620BA9DB00CDC1AA44569D87DC4ABA76553272B6`。`0x6FC62ABA..0x6FC62B11` 核对模式和概率，零概率/不匹配/保证成功不抽概率 RNG；`0x6FC62B22..0x6FC62B47` 确认标志是 **0x40+i**，不是 0x40<<i。由于三次标志均含 0x40、而均不含 0x80/0x100，三个模式行采用各自类型/概率，但伤害及长度读取 El1 列。这是原版行为，未按直觉改成每行自己的伤害列。
- D2Common.dll SHA256 `B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。`0x6FD68682..0x6FD686B6` 的 noRatio 分支写输出偏移 0x24/0x28（S1），而模式调用读取 0x2C/0x30（El），每行前又清零输出。因此该模式调用的 noRatio 元素端点为零，长度仍存在。只在模式适配器保留此怪癖，不扩大修改通用表格计算器原有 noRatio 契约。
- D2Game `0x6FC62BE7..0x6FC62C24` 确认 rand 取原生 1..5 编号、长度为零时在多人缩放后回填 25；该编号为火/电/魔法/冰/毒，不是宿主火/电/冰/毒/魔法。`0x6FC62C3E..0x6FC62CC7` 为 setter 调用：同类后续成功行覆盖先前端点/时长，而非相加或取最大；毒端点乘 10、长度乘 2，均在多人缩放后执行。

新增纯 domain `NativeMonsterModeElements`，以显式 RNG、原生类型和原始来源属性为输入，统一模式概率门、取表标志、随机映射、覆盖/长度/毒倍率及整数截断；它不是最终伤害包，也不拥有 ECS 生命周期。特殊生命/法力/耐力/眩晕/燃烧/freeze 类型不被冒充成这五种元素，仍待独立处理。保留端点顺序，异常超大合成数值使用宿主饱和保护，不模拟有符号溢出。

`MonsterModeDamageResolver` 和旧飞弹来源投影改为共用此元素适配器，消除两份不同的选行逻辑；旧飞弹改用主人等级而非飞弹技能等级计算 MonLvl，并由真实 ServerEntityFactory 传入地图难度而非固定 0。飞弹兼容投影的总包比例、毒长度/count、元素抵抗以及概率在每颗弹体上重掷的旧生命周期并未借此宣布原版化；特殊分支仍不启用第九阶段新包。普通近战的大部分路径也未在本轮改成模式临时属性列表。

新增 domain 快速数值 24 项、独立字面源码预期 8 项、资源适配/真实工厂/实际碰撞生命及事件 17 项。合成数据的 El2/El3 故意与 El1 不同，避免错误的按行取表也能通过；noRatio、Classic/LoD、三难度、概率边界及 rand 的三种错位通道均显式检查。工厂测试替换实例资源表，不修改全局安装行。八项审计为 source-derived 并经静态 DLL 指令交叉核对，不是原版进程逐击捕获样本。

验证过程保留失败记录：`build/monster-mode-elements-first.log` 在新测试缺失 getValue 默认参数处编译失败，此前 domain 默认 227 / 快速 211 / 审计 63 已通过；`build/monster-mode-elements-focused.log` 因实例资源夹具反射写 final 字段未显式 setAccessible 而失败。均只修测试夹具/调用，不放宽数值断言。扩大复验 `build/monster-mode-elements-verified.log` 返回 0（约 1 分 39 秒）：数据 32、元素 104、飞弹 178、特殊 26、近战 41 全过，无失败/错误/跳过，桌面/D2GS 编译通过；domain 三入口沿用此前通过的 UP-TO-DATE。该轮尚不含最后新增的实际碰撞生命/事件用例，也未跑 Fire Hit 专项或资源启动；最终窄入口另行收尾。

最终 `build/monster-mode-elements-closeout.log` 返回 0（约 1 分 28 秒）：新窄入口 20 项（模式层 17 + FireHitIntegrationTest 2 + Act I Fire Hit ECS 1）全过，无失败/错误/跳过；最新测试类重新编译，桌面/D2GS 编译通过，真实 1.10f 资源营地 manifest 与退出清理均 PASS，见 `desktop/build/monster-mode-elements-smoke/rogue-encampment-manifest.txt`。生产代码与扩大复验相同，未重复三个广域专项；没有运行整个 core、完整 AI 聚合、双机或原版进程采样。

日常只测本层可运行 `./gradlew.bat :server-domain:monsterCombatMathTest :server-domain:monsterCombatParityTest :core:monsterModeElementTest '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --console=plain`；不需每次启动营地或全量 AI。

### 第十阶段 B：动作生命周期、普通近战元素、两类暴击与回血合算

本轮连续处理实现、数值审计、ECS 接线和扩大回归，不再每完成一个子步骤就等待“继续下一步”。依据仍为上文 D2MOO revision；前一阶段的静态 DLL 核对继续作为模式规则证据，没有新增原版进程捕获样本。

`CofManager` 在接受非 GH 模式进入/强制重启之后、发布模式事件之前构建怪物临时元素；普通同模式 no-op 不重选，GH 保留，其他模式清理后按匹配行重建。临时值保存在 Monster 组件，不修改永久 Attributes；删除/重用会清空。网络快照和非权威副本不抽随机数。实际等级、地图难度、人数及 Classic/LoD 由当前会话读取。原生 MonSeq 使用逻辑模式槽 14，展示模式 A1/A2 的内部切换不重选，实际 SequenceHandler 的第零动作点已有集成测试。

普通飞弹读取同一动作选择，再与永久/状态来源端点合成不可变发射快照；多颗弹体不再分别重掷模式概率。后续主人换模式不会改变已发射快照。只有受支持五通道选择才解除此前 opt-out，生命/法力/耐力吸取、眩晕、燃烧或 freeze 等特殊类型不会被静默当成普通五通道包。旧无生命周期夹具仍保留显式兼容回退；不把这一回退称为原版调度。

普通无武器怪物近战依据 `SUNITDMG_FillDamageValues/RollDamageValueInRange`，瞬时元素来源转 8.8、毒保持原始速率，先按原百分比算法作用精通于端点，再抽取排他上端。近战零下端允许、相等/反转端点不交换且不抽取，不能套用飞弹要求正端点/交换端点的规则。火→电→冰→魔法→毒为所选近战局部抽取顺序。临时与永久/状态属性只加一次，毒 count/override length、独立毒长抗性、状态 MDR/吸收沿用已抽离的标量策略。毒-only 零即时伤害仍安装状态；冰伤被 MDR 完全抵消时不丢掉有效冷长度。实际 Attack 动作点验证 HP、DamageAppliedEvent 和 Vitals，而非测试手动扣血。

区分两种不能混淆的暴击：

- D2Common `MISSILE_HasBonusStats/CalculateDamageData/SetDamageStats`：来源怪物在创建时抽 passive critical，即使 chance=0 也消费一次；失败后仅非零 deadly 再抽。结果冻结，碰撞只加倍物理，不重新判定。无来源表格包不继承这一暴击。真实 ServerEntityFactory 验证每颗弹体恰从主人流消费一次；碰撞数值从弹体自身流读取，不与其他弹体共享全局战斗 RNG。
- D2Game `MonsterMode::D2GAME_MONSTER_ApplyCriticalDamage_6FC62E70`：独立的 MonStats.Crit，非零才抽主人 RNG，在来源伤害与转换之后、减伤之前翻倍物理与五通道（包括毒速率，不翻倍长度）。可与上一类物理暴击叠加。碰撞使用临时 impact view，绝不改写发射快照；真实近战、飞弹 HP/事件和两条种子流均有检查。

StateUpdater 依据 `PlrModes::EVENTS_HpRegen` / `MonsterMode::ApplyStatRegen` 将无毒自然回血、聚合/状态 HPREGEN、实际生命药水、毒和燃烧合成一次净速率。已有原生负 HPREGEN 的状态不重复减 rate；原生撕开伤口列表也能合算，但旧 OW 的 level×2 兼容分支未被宣称完成原版公式。玩家任意负净回血仍保留 1 HP；零净速率不抬高不足一 HP 的生命。真实 MapWrapper 上不足一 HP 的怪物在无房间/城镇时受原版保护；缩减夹具没有 MapWrapper 时保留原契约。PREVENTHEAL 阻止非负而不阻止负净值；life 状态移除基础回血，包括负基础值。网络快照与死亡单位不推进。宿主 PvP 拒绝在各负来源合算前执行，被拒绝的毒不能抹去其他怪物造成的燃烧；无来源负回血也不会访问实体 -1。

明确边界：这里的 NativeRng 仍为宿主单字 LCG，并非 D2MOO `D2Seed.h` 的 low/high 双字种子算法；本轮只修所有权和选定局部消费，不声称相同原版 seed 会逐击重放。命中/格挡与原始伤害填包的完整先后、原版 stat-list 到期事件队列、怪物死亡延迟、特殊伤害/技能公式/精通弹体/佣兵/PvP 全路径、大生命值经过 float 事件的精度仍未全部迁移。新增八项严格近战元素预期为独立字面源码样本，当前审计 71 项，不是 executable-captured。

验证保留迭代边界：`build/monster-combined-broad-retry.log` 返回 0（6 分 23 秒），但只覆盖主人 Crit/通用回血合算之前的版本，不用于替代最终生产代码的验收。其前一次飞弹种子断言失败，是在实体删除后读取已重置为零的池化组件；改为从真实 DamageEvent 内捕获碰撞种子，未放宽预期流。最新合算首轮 `build/monster-combined-final-focused.log` 返回 1：无来源负回血实体 -1 越界为真实缺陷，已修复；另外修正碰撞前自然回血和 life 标记抑制负基础回血的夹具范围。`build/monster-combined-final-focused-retry.log` 返回 0（1 分 27 秒），其后追加真实工厂和混合 PvP 负来源保护，最终统一复验如下。

最终 `build/monster-combined-acceptance.log` 返回 0（6 分 50 秒）：模式 37、数据 33、元素 109、飞弹 192、毒/回血 140、结算 62、攻击 41、追击 7、召唤 10、特殊 26、AI 行为聚合 821、core 架构 3 项全部通过，无失败/错误/跳过。提取模块和项目依赖门禁通过，桌面及 D2GS 编译通过。domain 默认 250、快速 234、独立审计 71 在本组合为此前成功结果的 UP-TO-DATE，并非又执行一次；各 core 入口有重叠，不相加为去重数量。该聚合不包含所有 core 测试。

追加兼容与启动 `build/monster-combined-compat-smoke.log` 返回 0（4 分 5 秒）：core 战斗/迁移、所有 `*GoldenDamageTest` 及 VitalsSerializer 共 73 类、192 项通过，无失败/错误/跳过；真实 1.10f 资源隐藏营地及退出资源清理均 PASS，manifest 位于 `desktop/build/monster-combined-smoke/rogue-encampment-manifest.txt`。这仍是 riiablo 读取原版资源，不是运行原版 EXE 战斗。最后只更新文档，未再修改生产代码或测试。

复现追加验证（PowerShell 单行）：`./gradlew.bat :core:test --tests 'com.riiablo.engine.server.combat.*' --tests '*GoldenDamageTest' --tests '*VitalsSerializerTest' :desktop:offscreenCamp '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' '-Pd2Version=1.10f' '-PvisualOutput=build/monster-combined-smoke' --console=plain`。

另一台电脑拉取后先替换资源路径运行上面的统一门禁；再实机检查持续移动下的女猎人追击/转向/攻击，多弹发射与动作中断、毒/冷在零即时伤害下的状态，以及召唤、自爆延迟、服务端/客户端生命同步。原版逐击数据按文末字段另行采集，不把现有自动化报告当作双机或原版实测已完成。

### 第十一阶段：共享双字种子、三方随机调度与普通怪物撕裂伤口

本轮不把全量迁移当作已完成。以下是实际接入生产入口的范围，文末仍列出未迁移分支。主要依据仍为上述 D2MOO revision，未新增原版进程逐击数据。

`NativeUnitRandom` 复用已有 D2MOO_JAVA `D2Seed/Seed` 的 low/high 双字运算：`high + 0x6AC690C5 * unsigned(low)`，完整种子持续保存在单位上。AI、模式进入、普通近战、发射时暴击、怪物词缀和选定技能回调共用怪物种子；每颗弹体的伤害/已接入回调持有自己的双字流。玩家防御使用服务端临时 `UnitRandomSeed`，不写 D2S 或联网 schema。旧 `rngState` 仅作低字兼容/诊断镜像，不能在每次调用时丢弃 high 重建。回收/重新创建清空种子；原版单位分配器的初始种子分配过程仍未移植，初始 low 保留宿主 game/entity 混合。

AI `SEED_RollPercentage` 对完整 uint64 取模；战斗 `ITEMS_RollRandomNumber` 对截断后的 uint32 取模，不能混为一个百分比函数。非正 bound 返回零且不抽取；正的 guaranteed item/防御概率仍抽取。零 low 在已初始化种子内合法；旧工具仅写低字零仍有“未初始化”歧义，精确重放应同时设置 `D2Seed` 两字。没有全局替换用于掉落、商人和兼容技能的宿主 `NativeRng` LCG。

另用本地 1.10f D2Common.dll 静态核对 `MISSILE_HasBonusStats`：`0x6FDBAE07..0x6FDBAE27` 保存双字后清 EDX，再以 `div` 对低字取余；`0x6FDBAE4A..0x6FDBAE6A` 对 deadly 分支相同。虽然 D2MOO 该函数文本有 `(int)SEED_RollRandomNumber`，原 DLL 不是有符号 `idiv`。因此保留 uint32 `%100`，不按 Java 有符号低字取模。DLL SHA256 与第十阶段相同；这是静态指令证据，不是运行捕获。

普通已审计飞弹按 `HandleMissileCollision → FillDamageParams → SetDamageFlags` 拆成三条流：需要 ToHit 时从主人抽 AR；AR miss 不填伤害、不抽主人 Crit；成功进入后弹体先抽物理、火、魔法、电、冰、毒，再由防御者抽格挡/闪避，最后主人抽 MonStats.Crit。被格挡仍消费伤害和主人 Crit，但不扣血、不触发 OW；未设 ToHit 的弹体不再抽一次虚假的命中值。创建暴击仍冻结于发射包，主人 Crit 不改写快照。普通近战则在命中/防御通过后填伤害，跑动玩家走原版自动命中、不抽 AR，仍检查降低后的格挡。源代码追踪测试明确断言三方顺序；旧单流兼容快照保留原调度，未用新规则改写旧 oracle。

`SUNITDMG_ApplyBlockOrDodge/ApplyDodge` 的盾挡和被动防御从**防御者**抽数，不再推进普通攻击者流。原生被动概率不套旧宿主 75% 截断，正概率包括 100% 均抽一次；移动时只检查 Evade，静止时 Weapon Block 后按攻击类型检查 Dodge/Avoid。武器资格、所有技能生产入口和全量客户端动作仍受既有范围限制，未宣称整个玩家战斗都已切换到新流。

`NativeOpenWounds` 依照 `SkillItem.cpp::SKILLITEM_CalculateOpenWoundsHpRegen/EventFunc15_OpenWounds` 实现等级分段 `{9,18,27,36,45}` 加 40 的原始 8.8 HPREGEN，持续 200 帧；玩家 /4，远程对玩家再 /2，冠军/暗金目标 /2。例：40 级原速率 706，近战对玩家 176/256 HP 每帧；20 级远程对玩家 32/256；99 级原速率 3136。巨量 Mod 输入使用 long/饱和保护，不模拟原版整数溢出。

生产普通怪物命中事件读取当时 OW 概率并从主人抽取，chance<=0 不抽，100 仍抽。近战动作点与已审计带来源伤害标志的普通飞弹均能安装状态，包括瞬时伤害被完全抵消的命中；miss/block、纯表格飞弹、快照副本不触发。技能/SrcMissDmg 的完整事件标志不借此放开。NPC/明确不可攻击怪物不接受状态。按 `Skills.cpp::sub_6FD10EC0`，相同 skill=0/level=1 的现存列表只刷新到期，保留首次伤害速率与来源，即使后来攻击者更强或改用远程。不会因 OW 叠加多个负回血列表。

OW 作为负 HPREGEN 与自然回血、药水、毒/燃烧合算，玩家保留 1 HP，200 tick 后到期且没有额外一帧。旧手动状态入口不再使用 `level*2` 整数 HP/帧：在首次 tick 从来源等级补齐原生速率；由于该旧入口没有远程事件元数据，只能明确按近战兼容。状态队列仍是宿主 tick-before-expiry，不冒称原版整个事件优先级队列已移植。

新增独立 BigInteger 测试 oracle、字面 low/high 向量、AI/战斗交替抽取、零/负 bound、无符号低/高字组合、消费者重绑、高字保留、弹体隔离与实际回收测试；创建/碰撞旧 LCG 预期改为独立原式，不放宽为“任意种子”。另有普通飞弹三方调用序列 5 项（含五通道不同宽度区分原版顺序）、防御归属/阈值 3 项、OW 快速数值 15 项、严格字面审计 15 项，以及实际近战/碰撞/状态/HP/事件/Vitals 的 OW 场景 11 项。

窄入口 `build/monster-remainder-schedule-focused.log` 返回 0（1 分 12 秒）：domain 默认 272、严格审计 86，core 新随机/OW/近战/创建暴击 27 项及架构 3 项通过，无失败/错误/跳过。更早的统一组合 `build/monster-remainder-acceptance.log` 返回 1：新增直接访问 Riiablo.gameSeed 触发架构预算门禁，已改用注入上下文；该组合还不含最后的三方调度，不能作为最终版本通过证据。最终扩大回归结果另记在本节后续。

全 core 扩大运行发现怪物专项之外的失败，未将其忽略后称为全绿。用 `git archive dc1e1a355` 导出未修改基线到独立 build 目录，没有回退工作区。基线 `baseline-check.log` 的 5 项中，祝福之锤亡灵/恶魔加成（100/50）、熊人生命（71/64）、震波弹体为空共 3 项已失败；火焰强化与单独运行的牺牲之箭通过。扩大基线 `baseline-expanded-check.log` 的 117 项有 17 项失败，复现缺少存档/资源样本、系统分组旧断言、客户端 context 注入、物品标签、近战动画夹具缺 CofManager，以及房间激活断言。牺牲之箭在该基线组合仍通过，但使用全局随机流且只发射一次，不足以证明其在全部运行顺序下稳定；不能因此认定本轮全量中的失败已解释完毕。附加基线结果保留于 `build/monster-remainder-baseline-dc1e1a355/baseline-extra-check.log`。

火焰强化实际伤害测试保留固定源种子 123，改由独立 BigInteger 原式计算新双字流预期，并核对消费后的 low/high；不再沿用单字 LCG 数值。火/冷/电强化与诅咒测试加入 `monsterSpecialTest`，以后统一门禁会覆盖这些本轮触及的回调。牺牲之箭范围场景改为局部固定命中 RNG，并在 finally 恢复原对象；保留范围伤害的原 HP 断言，不再以一次随机发射判断范围功能是否存在，不替代独立命中概率测试。

陷阱跨服务器隔离测试也改为独立 BigInteger 双字预期，8 次交错抽取逐次核对两份世界种子的 low/high，并加入模式随机分层。基线附加检查另确认德鲁伊数据、客户端爆炸和天堂之拳注入、毒新星/祝福之锤资源字段、绕墙寻路、技能审计引用缺失、无全局表的弹体属性写入，以及 Smite 的旧整数生命断言已有失败；相关日志分别为 `baseline-extra-check.log`、`baseline-data-path-check.log`、`baseline-isolation-smite-check.log`。这些独立基线失败没有在本轮扩大修改其生产代码，也不被怪物所选子域的通过掩盖。

召唤宠物落地足印、非传送卷轴物品判断、A3 流浪者余光时序也分别在未修改基线复现，见 `baseline-pets-check.log`、`baseline-item-check.log`、`baseline-wanderer-check.log`；并未把这些未修问题排除后称为项目全量验收成功。

全 core 的 `build/monster-remainder-full.log` 返回 1（36 分 9 秒），保存的 XML 为 941 类、5508 项、147 失败、0 错误、14 跳过；报告备份在 `build/monster-remainder-full-core-results` 和 `build/monster-remainder-full-core-report`，不会被后续筛选入口覆盖。这一报告在上述火焰强化/陷阱旧 oracle、牺牲之箭夹具修正之前，**不是最终全 core 全绿，也没有重跑全 core 来宣称所有 147 项都已解决**。额外缺失样本还涉及物品/地图/MPQ/视频；其余未逐项复现的失败不擅自统一归因为环境。

后续 `build/monster-remainder-final.log` 返回 1（7 分 45 秒）：只有攻击层的沉沦魔实际动画场景失败，其余分层、编译与资源 smoke 通过。该旧夹具未绑定 serverContext，走 MathUtils 兼容战斗流，单次攻击可能落入 5% miss；未修改基线单独复跑通过。夹具现绑定真实服务端上下文与明确的单位初始种子，仍经过 AI 决策、实际序列/COF/关键帧和 HP/事件断言，不绕过生产伤害。修正后 `build/monster-remainder-fixture-retry.log` 返回 0（56 秒）：相关 core 4 类 45 项，以及攻击层 41 项均通过，无失败/错误/跳过。这里没有借夹具修正取消 AR miss 测试，独立命中测试仍保留。

1.10f 真实资源启动 `desktop/build/monster-remainder-final-smoke/rogue-encampment-manifest.txt` 为 PASS，退出 `CLIENT_SHUTDOWN` 为 PASS；这是相同生产代码的资源启动验证，不是运行原版 EXE 采样。

最终统一复验 `build/monster-remainder-verified.log` 返回 0（7 分 10 秒）：模式 46、数据 33、元素 109、飞弹 192、毒/回血/OW 151、结算 62、攻击 41、追击 7、召唤 10、特殊 38、AI 行为聚合 821、架构 3 项均通过，无失败/错误/跳过；桌面及 D2GS 编译通过。domain 默认 273、快速 257、严格审计 86 为当前源版本已通过的 UP-TO-DATE 结果，不声称在这个组合又执行一次。各 core 入口有重叠，不能相加当作去重数量。此后只更新文档，不再改生产代码或测试。

另一台电脑复验（替换资源路径）：`./gradlew.bat :core:monsterCombatAcceptance :desktop:compileJava :server:d2gs:compileJava '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --console=plain`。需要同版本资源启动时再加 `:desktop:offscreenCamp '-Pd2Version=1.10f' '-PvisualOutput=build/monster-remainder-final-smoke'`。本轮没有达到“剩余全部完成”：下面的特殊生产分支仍须迁移；全 core 的已记录失败也没有整体修完，不能仅凭此统一门禁通过替代整个项目的验收。

以下差异清单保留为**修复前基线**；当前原有 17 项全部通过，基线里的“当前”指首次审计时的实现，不是第四阶段状态。

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

`MonsterMeleeSettlementTest` 用固定单位 RNG 与默认整数快照验证命中、未命中、格挡和零伤害边界。伤害 20 的断言仍是旧快照兼容契约；严格审计、普通定点测试与生产属性适配/ECS 场景显式验证新精度策略。不把手动扣血冒充生产 ECS 生命写入，后者由攻击场景负责。

### 第十二阶段：基础属性 domain 快照

`server-domain` 新增 `NativeMonsterStatsCalculator` 与不可变 `MonsterStatsSnapshot`，
迁移既有 MonStats/MonLvl 比例、Classic/LoD 表列、noRatio、区域等级选择、人数和固定 rank
规则，以及 A1/A2/S1 档位。没有另外引入一套近似的 `MonsterDifficultyEstimator` 公式。
输入除 `NativeCombatContext` 和怪物行外，还需已解析的 MonLvl 行、基础等级及 rank；
显式人数重载保留 9+ 的 Mod 分支，context 便利重载仍遵守现有 1..8 上限。

`core.MonsterStatsCalculator` 保留兼容 facade，负责查表与 Attributes/词缀适配。
`ServerEntityFactory` 使用生成快照，但仍在宿主抽基础 HP，再应用人数/rank 倍率和写入 ECS；
不能在缩放后的 HP 端点间均匀抽取。快照区分基础等级、表格等级与 rank 调整后的等级，
并分别保存三种攻击 AR。`MonsterModeDamageResolver` 的物理档位委托 domain，
动作刷新继续按单位当时等级计算，不把出生快照当作永久战斗属性。
快照不保留可变表行，也不包含随机源、全局宿主、AI、碰撞或词缀执行。

原版依据继续为本地 D2MOO revision `0c0bbd1`：
`MonsterTbls.cpp::DATATBLS_CalculateMonsterStatsByLevel`、
`Monster.cpp::MONSTER_InitializeStatsAndSkills/MONSTER_GetHpBonus/MONSTER_GetExperienceBonus`、
`MonsterMode.cpp::sub_6FC627B0`，以及 MonsterUnique 的比例运算分支。
本次首先保证抽取前后的行为兼容，不顺便改玩法公式：宿主 HP/经验百分比仍用 long 乘法，
固定 rank HP 仍用整数 HP/float 倍率；这不等于原版完整 8.8 MonUMod 链路。
Classic 只迁移表列选择，不新增 `MONSTERS_ApplyClassicScaling` 的额外分支；
通用元素 API 保留既有 first-selected/noRatio 输出契约，动作元素仍走既有独立适配。
任意 MonUMod/MonProp、冠军子类型、技能加成及最终词缀后战力不属于该快照。

新增 domain 普通回归 13 项、严格源码字面审计 11 项；core 冻结 `776c385cd` 旧实现，
覆盖 2000 组表格/flag、500 组生成组合和 HP 端点/中间 roll，另有缺表失败语义。
真实 1.10f 工厂场景覆盖六组难度/人数/rank、HP/AC/经验/AR、石肤叠加和后续模式刷新。
冻结实现仅证明迁移兼容；11 项严格预期为源码推导，不是原版进程采样。

验证：domain 默认 286、严格审计 97 项通过；真实资源 `monsterDataTest` 42、
`monsterModeElementTest` 46、架构 3 项通过，无失败/错误/跳过。
首轮集成夹具遗漏 Pathfinder，6 项在 AI 注入阶段失败；补齐生产依赖后同组复验通过，
未通过修改计算预期或跳过用例消除失败。追加 `monsterAttackTest` 41 项及桌面/D2GS 编译通过；
同组合的严格审计为已通过的 UP-TO-DATE 结果，没有声称再次执行。未重跑全 core 或原版进程采样。

## 下一阶段生产修复与实测

2026-10-20 补充：普通攻击、直伤/反伤和现有固定点持续面积伤害入口的共享减免，
以及统一吸收回血/吸取/扣血边界已在 [生产结算批次](combat-settlement-2026-10-20.md)
记录。本轮修正实际生产调用链，不再把没有运行时调用者的旧 DamageCalculator /
DamageApplier 当作生产完成证据；后者此前两类测试实际只有 9 项，不是 21 项。

当前已完成分层测试和所选普通怪物命中/物理/五通道元素子域，并增加动作级元素、两类暴击、双字种子、普通攻击三方随机归属/选定调用顺序，以及普通怪物 OW 公式/触发/刷新和 HPREGEN 合算。**仍未全部完成，不能把剩余项说成仅需另一台电脑验证**。

仍需生产代码迁移及独立预期的分支：技能公式/弹体精通、SrcMissDmg、未放行的 DamageRate 创建策略与特殊伤害回调；眩晕、燃烧/freeze 特殊包；玩家/佣兵武器系数与完整 PvP/难度/多人组合；未迁移技能的随机流/初始单位种子分配/原版 stat-list 事件队列，以及大生命值经过 float 事件的精度。已有百分比吸取和普通怪物固定生命/法力/耐力包不能继续列为“全部未实现”，但也不代表所有技能来源都已验证。现存 opt-out 继续显式保留，不能删除 guard 冒充完成。普通 OW 不代表所有技能物品事件的 OW 已原版化。

外部验收仍缺原版 EXE/DLL 逐击运行采样和另一台电脑实机/联机记录。源码与静态 DLL 足以推导已选子域，但不足以宣称全部实机行为一致；不能用源码样本全绿替代原版采样，也不能用 riiablo 读取原版 MPQ 的 smoke 代替运行原版游戏。

原版实测时记录：1.10f EXE/DLL 与 MPQ 版本/哈希、Classic/LoD、难度、人数、区域、怪物 ID/等级/词缀/攻击模式、双方实际属性、运行/格挡/状态、动作帧、随机输入（若可捕获）、伤害包及命中前后原始 8.8 HP。只看屏幕整数血量无法区分 10 与 10.996 HP，少量随机攻击也无法证明命中概率。实测样本确认后新增来源为 executable-captured 的数据，与 source-derived 样本并存。
