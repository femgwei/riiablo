package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Completeness gate for the DMG-03B Sorceress damage-owner audit. */
class SorceressDamageOwnershipTest extends RiiabloTest {
  private static final String FILE = "skill-damage-sorceress-ownership.tsv";
  private static final Set<Integer> CONFIRMED_GAPS = Set.of(43, 58, 63, 65);

  @Test
  void everySorceressSkillHasOneEvidenceBackedOwnerDecision() throws IOException {
    List<String> lines = Files.readAllLines(resolveAuditFile(), StandardCharsets.UTF_8);
    assertEquals(31, lines.size(), "header plus 30 Sorceress skills");

    String[] header = lines.get(0).split("\\t", -1);
    Map<String, Integer> columns = new HashMap<>();
    for (int i = 0; i < header.length; i++) columns.put(header[i], i);
    for (String required : new String[] {
        "skill_id", "skill_name", "damage_role", "damage_owner", "owner_record",
        "native_path", "d2moo_reference", "riiablo_reference", "test_reference",
        "audit_status", "riiablo_status", "notes"}) {
      assertTrue(columns.containsKey(required), "missing column " + required);
    }

    Set<Integer> ids = new HashSet<>();
    Set<Integer> observedGaps = new HashSet<>();
    for (int row = 1; row < lines.size(); row++) {
      String[] values = lines.get(row).split("\\t", -1);
      assertEquals(header.length, values.length, "column count at TSV row " + (row + 1));
      int id = Integer.parseInt(value(values, columns, "skill_id"));
      assertTrue(ids.add(id), "duplicate skill id " + id);
      assertTrue(id >= CharacterClass.SORCERESS.firstSpell
          && id < CharacterClass.SORCERESS.lastSpell, "non-Sorceress skill id " + id);

      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill, "missing 1.10f Skills.txt row " + id);
      assertEquals(skill.skill, value(values, columns, "skill_name"), "skill name " + id);
      assertEquals("OWNERSHIP_CONFIRMED", value(values, columns, "audit_status"),
          "owner decision " + id);
      assertFalse(value(values, columns, "native_path").isEmpty(), "native path " + id);
      assertFalse(value(values, columns, "d2moo_reference").isEmpty(),
          "D2MOO evidence " + id);
      assertFalse(value(values, columns, "riiablo_reference").isEmpty(),
          "riiablo path " + id);
      assertFalse(value(values, columns, "test_reference").isEmpty(),
          "test evidence " + id);

      String role = value(values, columns, "damage_role");
      String owner = value(values, columns, "damage_owner");
      if ("no_outgoing_damage".equals(role)) {
        assertEquals("n/a", owner, "non-damage owner " + id);
        assertTrue(value(values, columns, "notes").contains("N/A"),
            "non-damage reason " + id);
      } else {
        assertFalse("n/a".equals(owner), "damage-affecting skill needs an owner " + id);
        assertFalse(value(values, columns, "owner_record").isEmpty(), "owner record " + id);
      }

      if ("RIIABLO_GAP".equals(value(values, columns, "riiablo_status"))) {
        observedGaps.add(id);
        assertTrue(value(values, columns, "notes").contains("gap")
                || value(values, columns, "notes").contains("缺口"),
            "gap reason " + id);
      }
    }

    assertEquals(30, ids.size());
    for (int id = CharacterClass.SORCERESS.firstSpell;
        id < CharacterClass.SORCERESS.lastSpell; id++) {
      assertTrue(ids.contains(id), "missing Sorceress skill id " + id);
    }
    assertEquals(CONFIRMED_GAPS, observedGaps,
        "known implementation gaps must not be silently marked implemented");
  }

  private static String value(String[] values, Map<String, Integer> columns, String name) {
    return values[columns.get(name)].trim();
  }

  private static Path resolveAuditFile() {
    Path cwd = Paths.get("").toAbsolutePath();
    Path direct = cwd.resolve("docs").resolve(FILE);
    if (Files.exists(direct)) return direct;
    Path parent = cwd.resolve("..").resolve("docs").resolve(FILE).normalize();
    assertTrue(Files.exists(parent), "missing Sorceress ownership audit: " + direct);
    return parent;
  }
}
