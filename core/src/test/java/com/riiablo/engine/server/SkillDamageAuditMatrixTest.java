package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Builds the source-capture layer of the seven-class skill damage audit.
 *
 * <p>The exported curve values are deliberately labelled as unshifted
 * Skills.txt source values. They are not golden damage: D2MOO path evidence,
 * fixed-point scaling, missile ownership, synergies, weapon packets and final
 * combat settlement are audited in later stages.</p>
 */
class SkillDamageAuditMatrixTest extends RiiabloTest {
  private static final int FIRST_AUDIT_LEVEL = 1;
  private static final int LAST_AUDIT_LEVEL = 20;
  private static final int EXPECTED_CLASSES = 7;
  private static final int EXPECTED_SKILLS = 210;
  private static final int EXPECTED_ROWS = EXPECTED_SKILLS * LAST_AUDIT_LEVEL;
  private static final String OUTPUT_ENV = "SKILL_DAMAGE_AUDIT_TSV";
  private static final String COMMIT_ENV = "SKILL_DAMAGE_AUDIT_COMMIT";

  private static final String[] HEADER = {
      "schema_version", "baseline", "generated_from_commit", "class", "skill_id",
      "skill_name", "skill_level", "scenario", "damage_path_hint", "audit_status",
      "req_level", "max_level", "srv_start_func", "srv_do_func", "passive", "aura",
      "periodic", "summon", "srv_missile", "srv_missile_a", "srv_missile_b",
      "srv_missile_c", "srv_missile_d", "src_damage_128", "hit_shift", "to_hit",
      "level_to_hit", "physical_base_min", "physical_base_max",
      "physical_level_segments_min", "physical_level_segments_max",
      "source_curve_physical_min", "source_curve_physical_max", "damage_synergy_formula",
      "element_type", "element_base_min", "element_base_max",
      "element_level_segments_min", "element_level_segments_max",
      "source_curve_element_min", "source_curve_element_max", "element_synergy_formula",
      "element_length_base_frames", "element_length_level_segments",
      "source_curve_length_frames", "candidate_unit", "expected_min", "expected_max",
      "expected_total", "riiablo_actual_min", "riiablo_actual_max", "riiablo_actual_total",
      "delta_min", "delta_max", "delta_total", "skills_txt_source",
      "missiles_txt_source", "d2moo_reference", "test_reference", "notes"
  };

  @Test
  void exportsCompleteSevenClassLevelOneToTwentySourceMatrix() throws IOException {
    List<String[]> rows = new ArrayList<>(EXPECTED_ROWS);
    Set<Integer> skillIds = new LinkedHashSet<>();
    Set<String> rowKeys = new LinkedHashSet<>();
    int classCount = 0;

    for (CharacterClass characterClass : CharacterClass.values()) {
      classCount++;
      int classSkills = 0;
      for (int id = characterClass.firstSpell; id < characterClass.lastSpell; id++) {
        Skills.Entry skill = Riiablo.files.skills.get(id);
        assertNotNull(skill, characterClass + " is missing Skills.txt id=" + id);
        assertEquals(id, skill.Id, "Skills.txt id/index mismatch for " + skill.skill);
        assertTrue(skill.skill != null && !skill.skill.isEmpty(), "blank skill name id=" + id);
        skillIds.add(id);
        classSkills++;
        for (int level = FIRST_AUDIT_LEVEL; level <= LAST_AUDIT_LEVEL; level++) {
          String key = id + ":" + level;
          assertTrue(rowKeys.add(key), "duplicate audit row " + key);
          rows.add(row(characterClass, skill, level));
        }
      }
      assertEquals(30, classSkills, characterClass + " skill count");
    }

    assertEquals(EXPECTED_CLASSES, classCount, "character class count");
    assertEquals(EXPECTED_SKILLS, skillIds.size(), "unique character skill count");
    assertEquals(EXPECTED_ROWS, rows.size(), "level 1-20 matrix row count");
    assertEquals(EXPECTED_ROWS, rowKeys.size(), "unique skill-level key count");

    String output = System.getenv(OUTPUT_ENV);
    if (output != null && !output.trim().isEmpty()) {
      write(Paths.get(output), rows);
      System.out.println("[SKILL_DAMAGE_AUDIT_EXPORT] path=" + output
          + " skills=" + skillIds.size() + " rows=" + rows.size());
    }
  }

  private static String[] row(CharacterClass characterClass, Skills.Entry skill, int level) {
    String commit = valueOr(System.getenv(COMMIT_ENV), "UNSPECIFIED");
    int physicalMin = Math.max(0, skill.MinDam + damageBonusByLevel(level, skill.MinLevDam));
    int physicalMax = Math.max(physicalMin,
        Math.max(0, skill.MaxDam + damageBonusByLevel(level, skill.MaxLevDam)));
    int elementMin = Math.max(0, skill.EMin + damageBonusByLevel(level, skill.EMinLev));
    int elementMax = Math.max(elementMin,
        Math.max(0, skill.EMax + damageBonusByLevel(level, skill.EMaxLev)));
    int length = Math.max(0, skill.ELen + damageBonusByLevel(level, skill.ELevLen));
    String missiles = joinedMissiles(skill);
    return new String[] {
        "1", "Diablo II 1.10f", commit, characterClass.name(), integer(skill.Id),
        skill.skill, integer(level), "base_hard_points_no_equipment_no_synergy",
        damagePathHint(skill), "PENDING_D2MOO_REFERENCE", integer(skill.reqlevel),
        integer(skill.maxlvl), integer(skill.srvstfunc), integer(skill.srvdofunc),
        bool(skill.passive), bool(skill.aura), bool(skill.periodic), text(skill.summon),
        text(skill.srvmissile), text(skill.srvmissilea), text(skill.srvmissileb),
        text(skill.srvmissilec), text(skill.srvmissiled), integer(skill.SrcDam),
        integer(skill.HitShift), integer(skill.ToHit), integer(skill.LevToHit),
        integer(skill.MinDam), integer(skill.MaxDam), ints(skill.MinLevDam),
        ints(skill.MaxLevDam), integer(physicalMin), integer(physicalMax),
        text(skill.DmgSymPerCalc), text(skill.EType), integer(skill.EMin), integer(skill.EMax),
        ints(skill.EMinLev), ints(skill.EMaxLev), integer(elementMin), integer(elementMax),
        text(skill.EDmgSymPerCalc), integer(skill.ELen), ints(skill.ELevLen), integer(length),
        "Skills.txt_unshifted_source_units_not_golden", "", "", "", "", "", "",
        "", "", "", "Skills.txt#Id=" + skill.Id, missiles, "", "",
        "Golden and riiablo_actual columns remain blank until the damage path is evidenced."
    };
  }

  private static int damageBonusByLevel(int level, int[] values) {
    int l1 = arrayValue(values, 0);
    int l2 = arrayValue(values, 1);
    int l3 = arrayValue(values, 2);
    int l4 = arrayValue(values, 3);
    int l5 = arrayValue(values, 4);
    if (level > 28) return 7 * l1 + 8 * l2 + 6 * (l3 + l4) + (level - 28) * l5;
    if (level > 22) return 7 * l1 + 8 * l2 + 6 * l3 + (level - 22) * l4;
    if (level > 16) return 7 * l1 + 8 * l2 + (level - 16) * l3;
    if (level > 8) return 7 * l1 + (level - 8) * l2;
    return (level - 1) * l1;
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }

  private static String damagePathHint(Skills.Entry skill) {
    List<String> paths = new ArrayList<>();
    if (skill.MinDam != 0 || skill.MaxDam != 0 || anyNonZero(skill.MinLevDam)
        || anyNonZero(skill.MaxLevDam) || skill.EMin != 0 || skill.EMax != 0
        || anyNonZero(skill.EMinLev) || anyNonZero(skill.EMaxLev)) {
      paths.add("skills-table");
    }
    if (skill.SrcDam > 0) paths.add("weapon-source");
    if (!joinedMissiles(skill).isEmpty()) paths.add("server-missile");
    if (skill.periodic) paths.add("periodic");
    if (skill.aura) paths.add("aura");
    if (skill.passive) paths.add("passive");
    if (hasText(skill.summon)) paths.add("summon");
    if (skill.srvdofunc >= 0) paths.add("server-handler-" + skill.srvdofunc);
    if (paths.isEmpty()) paths.add("unresolved");
    return String.join("+", paths);
  }

  private static boolean anyNonZero(int[] values) {
    if (values == null) return false;
    for (int value : values) if (value != 0) return true;
    return false;
  }

  private static String joinedMissiles(Skills.Entry skill) {
    LinkedHashSet<String> missiles = new LinkedHashSet<>(Arrays.asList(
        text(skill.srvmissile), text(skill.srvmissilea), text(skill.srvmissileb),
        text(skill.srvmissilec), text(skill.srvmissiled)));
    missiles.remove("");
    return String.join("|", missiles);
  }

  private static void write(Path output, List<String[]> rows) throws IOException {
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) Files.createDirectories(parent);
    try (BufferedWriter writer = Files.newBufferedWriter(
        output, StandardCharsets.UTF_8)) {
      writeLine(writer, HEADER);
      for (String[] row : rows) {
        assertEquals(HEADER.length, row.length, "matrix column count");
        writeLine(writer, row);
      }
    }
  }

  private static void writeLine(BufferedWriter writer, String[] values) throws IOException {
    for (int i = 0; i < values.length; i++) {
      if (i > 0) writer.write('\t');
      writer.write(tsv(values[i]));
    }
    writer.newLine();
  }

  private static String tsv(String value) {
    return text(value).replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
  }

  private static String ints(int[] values) {
    if (values == null || values.length == 0) return "";
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) builder.append('|');
      builder.append(values[i]);
    }
    return builder.toString();
  }

  private static String integer(int value) { return Integer.toString(value); }
  private static String bool(boolean value) { return Boolean.toString(value); }
  private static String text(String value) { return value == null ? "" : value; }
  private static String valueOr(String value, String fallback) {
    return value == null || value.trim().isEmpty() ? fallback : value;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
