package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeMonStatsTest {
  @Test
  void keepsNativeRowIndexSeparateFromDiagnosticHcIdx() throws Exception {
    NativeMonStats monsters = NativeMonStats.parse(
        ("Id\thcIdx\tBaseId\tCode\tAI\tenabled\tisMelee\tVelocity\tTreasureClass1\t"
            + "Skill1\tSk1mode\tSk1lvl\n"
            + "fallen\t27\tfallen1\tFA\tFallen\t1\t2\t6\tAct 1 H2H A\tResurrect\tS1\t3\n")
            .getBytes(StandardCharsets.ISO_8859_1));

    NativeMonStats.Entry fallen = monsters.get("FALLEN");
    assertEquals(0, fallen.id);
    assertEquals(27, fallen.hcIdx);
    assertEquals("fallen", fallen.monster);
    assertEquals("Fallen", fallen.string("AI"));
    assertEquals("Resurrect", fallen.string("Skill1"));
    assertEquals(6, fallen.nativeInteger("Velocity"));
    assertTrue(fallen.bool("enabled"));
    assertTrue(fallen.bool("isMelee"));
    assertFalse(fallen.bool("boss"));
  }

  @Test
  void matchesEveryFieldLoadedByD2moo() {
    assertEquals(253, NativeMonStats.SCHEMA.columns().size());
  }
}
