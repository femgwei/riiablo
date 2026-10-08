# 七职业技能伤害所有者完整性复核

本复核完成 DMG-03B 的最后一个子项。输入为七份
`skill-damage-*-ownership.tsv`，基线为 Diablo II 1.10f。本步骤只核对所有者结论、状态分类、
D2MOO 证据位置和测试引用的完整性，不把源表曲线升级为逐等级黄金伤害值。

## 复核结果

- 七份清单使用完全相同的 12 列表头，每职业 30 项，共 210 项。
- 210 个技能 ID 全部唯一，并分别覆盖 Amazon 6–35、Sorceress 36–65、
  Necromancer 66–95、Paladin 96–125、Barbarian 126–155、Druid 221–250、
  Assassin 251–280；技能名称与 1.10f `Skills.txt` 一致。
- `damage_role`、`damage_owner`、`owner_record`、原版路径、D2MOO 位置、riiablo 路径、
  测试引用、状态和说明均无空值。每个 D2MOO 引用都包含源码位置。
- 189 个唯一测试引用均能解析到现有测试类/方法，或明确标注的 headless 场景。
- 40 项技能不拥有输出伤害；其中 29 项标记 `OUT_OF_SCOPE_NO_DAMAGE`，另有 9 项因防御、
  召唤或行为语义差异保留为 `RIIABLO_GAP`，2 项相关行为已实现并测试。无输出伤害不等于
  整个技能无需审计。

## 统一状态分类

- `IMPLEMENTED_TESTED`：118 项。生产路径存在，并已有与所有者语义对应的聚焦测试。
- `IMPLEMENTED_TEST_GAP`：27 项。生产路径存在，但缺最终伤害消费或完整端到端断言。
- `OUT_OF_SCOPE_NO_DAMAGE`：29 项。技能不拥有输出伤害，清单明确记录 N/A 原因。
- `RIIABLO_GAP`：36 项。已有 D2MOO/1.10f 证据支持的实现或语义差异。

四类合计 210 项。各职业测试继续锁定具体缺口 ID；新增
`SkillDamageOwnershipAuditTest` 负责跨职业表头、ID、状态总数、证据非空、无伤害语义和测试引用
解析，防止后续清单在单职业测试仍通过时发生跨文件漂移。

## 结论和边界

DMG-03B 已完成。完成含义是 210 个技能的实际伤害所有者和原版调用职责均有可追溯结论，
不是 210 个技能的逐等级伤害已经正确。黄金矩阵的 4,200 行仍全部为
`PENDING_D2MOO_REFERENCE`，`expected_*`、`riiablo_actual_*` 和 `delta_*` 仍为空。

下一步进入 DMG-04：从明确的所有者路径读取 D2MOO 公式，按等级 1–20 生成独立期望值，
并与 riiablo 生产路径输出分开记录。武器包、多段、毒素、周期和召唤继续留在各自专项维度，
不能用单一 `source_curve_*` 数值代替。
