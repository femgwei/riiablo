# 七职业逐等级伤害审计交接（2026-10-08）

## 当前 Git 基线

- 仓库：`F:/3rd_src/riiablo`
- 分支：`master`
- 最近完成技能提交：`2216933ced28bfaae67e48f9f10ecd471630b290`
  （`Audit Magic Arrow damage levels 1 through 20`）。
- `origin/master` 已确认与上述提交一致；本交接文档位于其后的独立文档提交。
- 写入交接前工作树干净；新会话开始后仍须先执行 `git status --short`，保护其他 agent
  可能新增的未提交改动，只暂存本轮明确修改的文件。

## 当前审计进度

- 审计基线：Diablo II 1.10f。
- DMG-01、DMG-02、DMG-03A、DMG-03B 已完成；七职业 210/210 项伤害所有者已对齐。
- DMG-04 已批准 19 个技能、380/4,200 个技能—等级行；其余 3,820 行保持
  `PENDING_D2MOO_REFERENCE`。
- Amazon 已批准 Magic Arrow 一个技能，共 20/600 行。
- 当前总加权完成度为 `31.8095%`，展示为 `31.8%`；这不是技能正确率。
- 已批准技能：Fire Bolt、Ice Bolt、Fire Ball、Ice Blast、Glacial Spike、Lightning、
  Nova、Frost Nova、Charged Bolt、Chain Lightning、Thunder Storm、Static Field、
  Telekinesis、Blaze、Fire Wall、Inferno、Shiver Armor、Chilling Armor、Magic Arrow。

权威进度文件：

- `docs/skill-damage-audit-task-list.md`
- `docs/skill-damage-golden-matrix.tsv`
- `docs/skill-damage-golden-matrix.xlsx`
- `docs/skill-damage-d2moo-formula-evidence.md`
- `docs/skill-damage-amazon-ownership.md`
- `docs/skill-damage-amazon-ownership.tsv`

## 最近完成：Magic Arrow（技能 6）

- 1.10f 数据确认：`SrcDam=128`、`HitShift=8`、物理基值 `1/1`、五段增量均为
  `1/1/1/1/1`。
- `magicarrow` 的实际回调是 `SrvDmgFunc=1`；`SrvDmgFunc=12` 属于 Lightning Bolt，
  不要混用。
- `DmgCalc1=dl12`、`dParam1=1`、`dParam2=1`，等级 1–20 将 1%–20% 的物理伤害
  转为魔法伤害，但不改变 8.8 定点总量。
- 无装备、owner 武器伤害为 0 的基础场景中，总整数伤害为等级 1–20 的 `1..20`；矩阵
  20 行 `delta_min/max=0`。
- 新增 `MagicArrowGoldenDamageTest`，并通过 Amazon 所有者、弹药、行为矩阵及全局审计
  门禁。
- 本批只批准抗性前总整数伤害。D2MOO 保留物理/魔法通道的 8.8 小数，riiablo 当前通道
  以整数保存；固定武器包及分通道精度留在 DMG-06，抗性、穿透、吸收和 PvP 留在
  DMG-08。不要把本次 `delta=0` 解读为分通道最终结算完全等价。

## 下一项：Fire Arrow（技能 7）

优先继续 Amazon，下一项审核 Fire Arrow，不要回到此前计划中的 Blizzard。已知 1.10f
源字段：

- `SrcDam=128`、`HitShift=8`、`EType=fire`。
- 元素基值 `EMin=1`、`EMax=4`。
- 最小五段增量 `2/3/6/12/24`，最大五段增量 `2/3/7/14/27`。
- 无 Exploding Arrow 协同的源曲线：等级 1 为 `1–4`，等级 20 为 `63–70`。
- 协同公式为 `(skill('Exploding Arrow'.blvl)) * par8`，基础场景硬点协同固定为 0。
- `firearrow` 同样走 `MISSMODE_SrvDmg01_FireArrow_MagicArrow_ColdArrow`；现有测试确认
  `dl12` 在等级 1 使用 3%，等级 2 使用 5%，即 `dParam1=3`、`dParam2=2`。
- 基础无装备场景的 owner 武器物理包为 0，因此先审核 Skills.txt 火焰曲线；固定武器包
  的物理转火焰通道另归 DMG-06，不能混进 DMG-04 的 20 行。

建议实施顺序：

1. 用 D2MOO `SKILLS_GetMin/MaxElemDamage`、`MISSILE_CalculateDamageData` 和
   `MISSMODE_SrvDmg01_FireArrow_MagicArrow_ColdArrow` 再次确认公式、所有者和取整。
2. 新建 `FireArrowGoldenDamageTest`，用独立常量覆盖等级 1–20，并锁定
   `SrvDmgFunc=1`、`DmgCalc1=dl12`、`dParam=3/2`。
3. 对比 `MissileDamageResolver.initializeSkill` 的生产快照；只有证据支持时才改生产代码。
   Amazon 是用户近期验证过的实现，禁止仅凭经验覆盖。
4. 批准矩阵中技能 7 的 20 行，进度变为 400/4,200；总加权完成度应为
   `31.9048%`，展示为 `31.9%`，Amazon 为 40/600 行。
5. 更新任务清单、公式证据、Amazon 所有者文档、TSV/XLSX 及工作簿生成器，运行门禁，
   提交并推送 `origin/master`。

## 1.10f 和测试入口

所有真实数据测试必须显式设置：

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
```

不要用 `G:\BaiduNetdiskDownload\Diablo II` 的 1.14 数据填充 1.10f 黄金值。暗黑机制优先
参考 `F:/3rd_src/D2MOO`；D2MOO 仍不明确时再查 The Phrozen Keep，不要只凭经验回答。

Magic Arrow 完成时通过的门禁，可把首个测试替换为 Fire Arrow 新测试：

```powershell
./gradlew.bat --no-daemon :core:test `
  --tests com.riiablo.engine.server.MagicArrowGoldenDamageTest `
  --tests com.riiablo.engine.server.AmazonDamageOwnershipTest `
  --tests com.riiablo.engine.server.AmazonSkillSpecializationTest.elementalArrowsCaptureNativeSkillDamageAndFreezeSemantics `
  --tests com.riiablo.engine.server.AmazonSkillSpecializationTest.elementalArrowConversionUsesNativeBaseAtLevelOne `
  --tests com.riiablo.engine.server.NativeAmazonAmmoPolicyTest `
  --tests com.riiablo.engine.server.NativeAmazonSkillMatrixTest `
  --tests com.riiablo.engine.server.SkillDamageAuditMatrixTest `
  --tests com.riiablo.engine.server.SkillDamageOwnershipAuditTest
```

完整运行 `AmazonSkillSpecializationTest` 当前有两个与 Magic Arrow 无关的既有失败：

- `immolationArrowDescriptionIncludesFireFieldDetails`
- `immolationArrowCreatesPersistentFireField`

相关 Fire Arrow 方法和本轮全部门禁均已通过。接手时不要把这两个既有失败误判为新技能
审计回归，也不要隐瞒；若本轮修改触及 Immolation Arrow，则必须重新判断归属。

## XLSX 更新规则和大差异说明

- `docs/skill-damage-golden-matrix.tsv` 是版本控制真源，XLSX 是浏览和交接产物。
- 修改 XLSX 必须遵守 Spreadsheets skill：使用 `@oai/artifact-tool`，禁止 openpyxl。
- 在 `tools/skill-damage-audit/node_modules` 建立临时 junction，指向 bundled
  `node_modules`；首次写入前只执行一次 `mark_artifact_operation_started.mjs`，然后使用
  bundled Node 运行 `tools/skill-damage-audit/build-golden-matrix.mjs`。
- 导出后复读 Summary、Task List、目标技能等级 1/20，并执行公式错误扫描；最后删除
  `.xlsx.inspect.ndjson` 和临时 junction。
- `2216933c` 的 XLSX 已验证没有损坏：前后均为 18 个 ZIP 条目、4 个工作表和 4,200 行，
  没有新增/丢失条目或公式错误。Git 报告 `rewrite 85%` 是因为 Artifact Tool 每次从头生成
  workbook，并随机更换 relationship ID；压缩后的二进制因此大面积变化，实际文件只增加
  1,307 字节。后续必须以 TSV 行差异和导入复读结果判断内容，不把二进制相似度当损坏。

## 工作纪律

- 极小文档修改可不编译；技能审计必须运行专项和完整性门禁。
- 每完成一个技能都更新百分比、提交 Git 并推送，方便另一台电脑验证。
- 所有普通文件编辑使用 `apply_patch`；只暂存本轮文件，禁止 reset/checkout 覆盖其他
  agent 的修改。
- 提交前执行 `git diff --check`；推送后核对 `HEAD` 与 `origin/master`。
