package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.NativeSkills;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

/**
 * Data-side contract for the dark-magic exact-ID client evidence manifest.
 *
 * <p>This deliberately verifies the lossless 1.10f client callback projection
 * ({@code cltstfunc/cltdofunc/cltmissile*}) against the decoded Skills table.
 * It is not a substitute for a d2client.dll disassembly: the binary/function
 * address evidence remains a separate gate and must not be inferred from a
 * matching Skills.txt row.
 */
class NativeClientSkillEvidenceTest extends RiiabloTest {
  /** dark-magic skill-behavior-coverage.v1.json exact IDs and families. */
  private static final Row[] MANIFEST = {
      row(0, "action.melee"),
      row(36, "missile.straight"), row(40, "state.self-timed"),
      row(50, "state.self-timed"), row(60, "state.self-timed"),
      row(45, "missile.straight-freeze"), row(47, "missile.straight-impact-area"),
      row(54, "movement.point-relocate"),
      row(55, "missile.straight-impact-area-freeze"), row(48, "missile.radial"),
      row(52, "state.targeted-timed"),
      row(66, "state.point-area-curse"), row(70, "summon.targeted-corpse"),
      row(75, "summon.golem"), row(80, "summon.targeted-corpse"),
      row(85, "summon.golem"), row(90, "summon.golem"),
      row(94, "summon.golem"), row(95, "summon.targeted-corpse"),
      row(72, "state.point-area-curse"),
      row(98, "aura.selected-party-stat"), row(99, "aura.selected-party-periodic"),
      row(100, "aura.selected-party-stat"), row(103, "aura.selected-party-stat"),
      row(104, "aura.selected-party-stat"), row(105, "aura.selected-party-stat"),
      row(108, "aura.selected-party-stat"), row(109, "aura.selected-party-periodic"),
      row(110, "aura.selected-party-stat"), row(115, "aura.selected-party-stat"),
      row(120, "aura.selected-party-periodic"), row(124, "aura.selected-corpse-periodic"),
      row(125, "aura.selected-party-stat"),
      row(251, "trap.assassin-family"), row(256, "trap.assassin-family"),
      row(257, "trap.assassin-family"), row(261, "trap.assassin-family"),
      row(262, "trap.assassin-family"), row(266, "trap.assassin-family"),
      row(271, "trap.assassin-family"), row(272, "trap.assassin-family"),
      row(276, "trap.assassin-family"), row(277, "trap.assassin-family"),
  };

  @Test
  void manifestRowsRetainExactLosslessClientProjection() {
    assertEquals(43, MANIFEST.length);
    for (Row expected : MANIFEST) {
      Skills.Entry skill = Riiablo.files.skills.get(expected.id);
      NativeSkills.Entry nativeRow = Riiablo.files.NativeSkills.get(expected.id);
      assertNotNull(skill, "missing Skills.txt row id=" + expected.id);
      assertNotNull(nativeRow, "missing lossless Skills.txt row id=" + expected.id);
      assertEquals(expected.id, nativeRow.txtId, expected.family + ":txt id");
      assertTrue(nativeRow.sourceLine > 0, expected.family + ":source line");

      assertEquals(nativeInt(nativeRow, "cltstfunc"), skill.cltstfunc,
          expected.id + ":cltstfunc");
      assertEquals(nativeInt(nativeRow, "cltdofunc"), skill.cltdofunc,
          expected.id + ":cltdofunc");
      assertEquals(nativeString(nativeRow, "cltmissile"), skill.cltmissile,
          expected.id + ":cltmissile");
      assertEquals(nativeString(nativeRow, "cltmissilea"), skill.cltmissilea,
          expected.id + ":cltmissilea");
      assertEquals(nativeString(nativeRow, "cltmissileb"), skill.cltmissileb,
          expected.id + ":cltmissileb");
      assertEquals(nativeString(nativeRow, "cltmissilec"), skill.cltmissilec,
          expected.id + ":cltmissilec");
      assertEquals(nativeString(nativeRow, "cltmissiled"), skill.cltmissiled,
          expected.id + ":cltmissiled");
    }
  }

  private static int nativeInt(NativeSkills.Entry row, String field) {
    Integer value = row.integer(field);
    return value == null ? 0 : value;
  }

  private static String nativeString(NativeSkills.Entry row, String field) {
    String value = row.string(field);
    return value == null ? "" : value;
  }

  private static Row row(int id, String family) {
    return new Row(id, family);
  }

  private static final class Row {
    final int id;
    final String family;

    Row(int id, String family) {
      this.id = id;
      this.family = family;
    }
  }
}
