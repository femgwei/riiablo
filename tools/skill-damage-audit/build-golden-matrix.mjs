import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const repo = path.resolve(import.meta.dirname, "..", "..");
const sourcePath = path.join(repo, "docs", "skill-damage-golden-matrix.tsv");
const outputPath = path.join(repo, "docs", "skill-damage-golden-matrix.xlsx");
const previewDir = path.join(repo, "build", "skill-damage-audit-previews");
const font = "Arial";

const taskRows = [
  ["DMG-01", "口径、版本、等级域和场景", 0.05, 1, null, "COMPLETE", "1.10f；基础场景为硬点 1–20、无装备、无协同", ""],
  ["DMG-02", "210 技能清册和路径提示", 0.10, 1, null, "COMPLETE", "7×30×20=4,200 行；源字段自动导出", ""],
  ["DMG-03A", "D2MOO 通用伤害公式与取整", 0.03, 1, null, "COMPLETE", "五段曲线、HitShift、协同及长度顺序有源码证据和边界测试", ""],
  ["DMG-03B", "逐技能 Skills/Missiles/D2MOO 语义对齐", 0.12, 0.25, null, "IN_PROGRESS", "逐技能确认伤害所有者、调用参数和特殊路径", "Sorceress、Paladin 各 30/30 完成；下一职业 Necromancer"],
  ["DMG-04", "1–20 级基础黄金值", 0.20, 0, null, "NOT_STARTED", "4,200 行 expected_* 全部得到结论", "等待 DMG-03"],
  ["DMG-05", "全部硬点协同组合", 0.15, 0, null, "NOT_STARTED", "协同读取和组合用例完整", "等待 DMG-04"],
  ["DMG-06", "武器、SrcDam、ToHit、多段", 0.10, 0, null, "NOT_STARTED", "武器包和多次命中语义完整", "等待 DMG-03"],
  ["DMG-07", "毒素、周期、区域、父子导弹、召唤", 0.10, 0, null, "NOT_STARTED", "rate/duration/total 和继承链完整", "等待 DMG-03"],
  ["DMG-08", "抗性、免疫、难度、PvP", 0.05, 0, null, "NOT_STARTED", "基础值与最终结算分离", "等待基础黄金值"],
  ["DMG-09", "七职业逐等级回归", 0.07, 0, null, "NOT_STARTED", "黄金与生产路径逐行比较", "等待对应黄金值"],
  ["DMG-10", "完整性审计和交接", 0.03, 0, null, "NOT_STARTED", "场景、证据、测试、未决项可追溯", "等待全部审计项"],
];

const numericColumns = new Set([
  "schema_version", "skill_id", "skill_level", "req_level", "max_level", "srv_start_func",
  "srv_do_func", "src_damage_128", "hit_shift", "to_hit", "level_to_hit",
  "physical_base_min", "physical_base_max", "source_curve_physical_min",
  "source_curve_physical_max", "element_base_min", "element_base_max",
  "source_curve_element_min", "source_curve_element_max", "element_length_base_frames",
  "source_curve_length_frames", "expected_min", "expected_max", "expected_total",
  "riiablo_actual_min", "riiablo_actual_max", "riiablo_actual_total", "delta_min", "delta_max",
  "delta_total",
]);
const booleanColumns = new Set(["passive", "aura", "periodic"]);

function parseTsv(text) {
  const lines = text.replace(/^\uFEFF/, "").trimEnd().split(/\r?\n/);
  const headers = lines[0].split("\t");
  const rows = lines.slice(1).map((line) => {
    const values = line.split("\t");
    if (values.length !== headers.length) {
      throw new Error(`TSV column mismatch: expected ${headers.length}, got ${values.length}`);
    }
    return values.map((value, index) => {
      const key = headers[index];
      if (value === "") return null;
      if (numericColumns.has(key)) return Number(value);
      if (booleanColumns.has(key)) return value.toLowerCase() === "true";
      return value;
    });
  });
  return { headers, rows };
}

function styleTitle(sheet, range) {
  range.format.font = { name: font, size: 15, bold: true, color: "#172033" };
}

function styleHeader(range) {
  range.format.fill = "#1F4E78";
  range.format.font = { name: font, size: 10, bold: true, color: "#FFFFFF" };
  range.format.horizontalAlignment = "center";
  range.format.verticalAlignment = "center";
  range.format.wrapText = true;
}

const { headers, rows } = parseTsv(await fs.readFile(sourcePath, "utf8"));
if (rows.length !== 4200) throw new Error(`Expected 4,200 matrix rows, got ${rows.length}`);
const skills = new Set(rows.map((row) => row[headers.indexOf("skill_id")]));
if (skills.size !== 210) throw new Error(`Expected 210 unique skills, got ${skills.size}`);

const workbook = Workbook.create();
const summary = workbook.worksheets.add("Summary");
const tasks = workbook.worksheets.add("Task List");
const matrix = workbook.worksheets.add("Golden Matrix");
const dictionary = workbook.worksheets.add("Field Dictionary");

summary.showGridLines = false;
summary.tabColor = "#17365D";
summary.getRange("A2:F2").values = [["七职业逐等级伤害审计", null, null, null, null, null]];
styleTitle(summary, summary.getRange("A2:F2"));
summary.getRange("A4:B10").values = [
  ["指标", "当前值"],
  ["审计基线", "Diablo II 1.10f"],
  ["加权完成度", null],
  ["职业技能数", 210],
  ["技能—等级行数", 4200],
  ["GOLDEN_APPROVED 行", null],
  ["待 D2MOO 证据行", null],
];
styleHeader(summary.getRange("A4:B4"));
summary.getRange("B6").formulas = [[`=SUM('Task List'!E2:E${taskRows.length + 1})`]];
summary.getRange("B9").formulas = [[`=COUNTIF('Golden Matrix'!J2:J${rows.length + 1},"GOLDEN_APPROVED")`]];
summary.getRange("B10").formulas = [[`=COUNTIF('Golden Matrix'!J2:J${rows.length + 1},"PENDING_D2MOO_REFERENCE")`]];
summary.getRange("B6").format.numberFormat = "0.0%";
summary.getRange("A12:F16").values = [
  ["关键限制", null, null, null, null, null],
  ["当前 21.0% 代表口径、源清册、通用公式证据和 Sorceress/Paladin 所有者审计完成，不代表已有 21.0% 技能伤害正确。", null, null, null, null, null],
  ["source_curve_* 是未应用 HitShift、导弹归属、协同、武器包和最终结算的源表曲线。", null, null, null, null, null],
  ["expected_* 与 riiablo_actual_* 在获得独立证据前必须保持空白。", null, null, null, null, null],
  ["Amazon 只做证据核对和回归保护，未经差异证据不覆盖用户已验证实现。", null, null, null, null, null],
];
summary.getRange("A12:F12").format.fill = "#D9EAF7";
summary.getRange("A12:F12").format.font = { name: font, bold: true, color: "#17365D" };
summary.getRange("A13:F16").format.wrapText = true;
summary.getRange("A1:F16").format.font.name = font;
summary.getRange("A:A").format.columnWidth = 28;
summary.getRange("B:B").format.columnWidth = 22;
summary.getRange("C:F").format.columnWidth = 14;
summary.getRange("13:16").format.rowHeight = 28;

tasks.showGridLines = false;
tasks.tabColor = "#4472C4";
const taskHeaders = ["任务", "范围", "权重", "完成比例", "加权完成", "状态", "完成定义", "下一动作"];
tasks.getRange("A1:H1").values = [taskHeaders];
styleHeader(tasks.getRange("A1:H1"));
tasks.getRange(`A2:H${taskRows.length + 1}`).values = taskRows;
for (let row = 2; row <= taskRows.length + 1; row++) {
  tasks.getRange(`E${row}`).formulas = [[`=C${row}*D${row}`]];
}
tasks.getRange(`C2:E${taskRows.length + 1}`).format.numberFormat = "0.0%";
tasks.getRange(`A1:H${taskRows.length + 1}`).format.font.name = font;
tasks.getRange(`A2:H${taskRows.length + 1}`).format.verticalAlignment = "center";
tasks.getRange(`B2:B${taskRows.length + 1}`).format.wrapText = true;
tasks.getRange(`G2:H${taskRows.length + 1}`).format.wrapText = true;
tasks.getRange("A:A").format.columnWidth = 12;
tasks.getRange("B:B").format.columnWidth = 28;
tasks.getRange("C:F").format.columnWidth = 14;
tasks.getRange("G:H").format.columnWidth = 42;
tasks.freezePanes.freezeRows(1);
tasks.tables.add(`A1:H${taskRows.length + 1}`, true, "DamageAuditTasks");

matrix.showGridLines = false;
matrix.getRangeByIndexes(0, 0, 1, headers.length).values = [headers];
matrix.getRangeByIndexes(1, 0, 24, headers.length).values = rows.slice(0, 24);
styleHeader(matrix.getRangeByIndexes(0, 0, 1, headers.length));
matrix.getRangeByIndexes(0, 0, 25, headers.length).format.font.name = font;
matrix.getRangeByIndexes(1, 0, 24, headers.length).format.verticalAlignment = "center";
matrix.freezePanes.freezeRows(1);
matrix.freezePanes.freezeColumns(6);
matrix.getRange("A:A").format.columnWidth = 12;
matrix.getRange("B:D").format.columnWidth = 18;
matrix.getRange("E:E").format.columnWidth = 10;
matrix.getRange("F:F").format.columnWidth = 28;
matrix.getRange("G:G").format.columnWidth = 10;
matrix.getRange("H:J").format.columnWidth = 30;
matrix.getRange("K:BH").format.columnWidth = 16;

dictionary.showGridLines = false;
dictionary.getRange("A1:C1").values = [["字段/字段组", "含义", "准入约束"]];
styleHeader(dictionary.getRange("A1:C1"));
dictionary.getRange("A2:C14").values = [
  ["class + skill_id + skill_level + scenario", "矩阵唯一业务键", "不得重复；基础矩阵必须为 4,200 个组合"],
  ["damage_path_hint", "从 Skills.txt 自动推导的初步路径", "只用于分流，不是最终结论"],
  ["audit_status", "当前审计状态", "只有满足文档六项准入规则才可改为 GOLDEN_APPROVED"],
  ["physical_* / element_*", "Skills.txt 原始伤害字段", "保留原始单位和分段"],
  ["source_curve_*", "基值加五段等级增量", "未移位、未协同、不是黄金值"],
  ["candidate_unit", "候选列当前单位说明", "必须显式说明非黄金状态"],
  ["expected_*", "D2MOO 证据支持的黄金结果", "证据完成前留空"],
  ["riiablo_actual_*", "生产计算路径实际结果", "不得用 expected 公式回填"],
  ["delta_*", "实际值减黄金值", "两侧都有值后计算"],
  ["skills_txt_source", "技能表来源", "至少固定技能 ID"],
  ["missiles_txt_source", "关联 server missile 候选", "DMG-03 后固定实际所有者"],
  ["d2moo_reference", "原版执行语义证据", "必须指向具体函数或源码位置"],
  ["test_reference", "riiablo 自动化回归", "批准黄金值前必须存在"],
];
dictionary.getRange("A1:C14").format.font.name = font;
dictionary.getRange("A2:C14").format.wrapText = true;
dictionary.getRange("A:A").format.columnWidth = 34;
dictionary.getRange("B:C").format.columnWidth = 55;
dictionary.freezePanes.freezeRows(1);
dictionary.tables.add("A1:C14", true, "DamageAuditDictionary");

// Render the final layout with representative matrix rows before adding all
// 4,200 rows. The artifact renderer exits at native level on this 60×4,200
// sheet, while XLSX export and full-workbook inspection support the full size.
workbook.recalculate();
await fs.mkdir(previewDir, { recursive: true });
if (process.env.SKILL_DAMAGE_AUDIT_RENDER === "true") {
  for (const [sheetName, range] of [
    ["Summary", "A1:F16"],
    ["Task List", `A1:H${taskRows.length + 1}`],
    ["Golden Matrix", "A1:L25"],
    ["Field Dictionary", "A1:C14"],
  ]) {
    const preview = await workbook.render({ sheetName, range, scale: 1, format: "png" });
    const filename = sheetName.toLowerCase().replaceAll(" ", "-") + ".png";
    await fs.writeFile(path.join(previewDir, filename), new Uint8Array(await preview.arrayBuffer()));
  }
} else {
  console.warn("[RENDER_SKIPPED] Set SKILL_DAMAGE_AUDIT_RENDER=true where artifact rendering is available.");
}

matrix.getRangeByIndexes(1, 0, rows.length, headers.length).values = rows;
matrix.getRangeByIndexes(0, 0, rows.length + 1, headers.length).format.font.name = font;
matrix.getRangeByIndexes(1, 0, rows.length, headers.length).format.verticalAlignment = "center";
matrix.tables.add(`A1:BH${rows.length + 1}`, true, "SkillDamageGoldenMatrix");
matrix.getRange(`J2:J${rows.length + 1}`).conditionalFormats.addCustom('=J2="PENDING_D2MOO_REFERENCE"', {
  fill: "#FFF2CC", font: { color: "#7F6000", bold: true },
});
workbook.recalculate();

const inspection = await workbook.inspect({
  kind: "workbook,sheet,table",
  maxChars: 6000,
  tableMaxRows: 5,
  tableMaxCols: 10,
  tableMaxCellChars: 80,
});
console.log(inspection.ndjson);
const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!",
  options: { useRegex: true, maxResults: 300 },
  summary: "final formula error scan",
});
console.log(errors.ndjson);

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(JSON.stringify({ outputPath, rows: rows.length, skills: skills.size, previewDir }));
