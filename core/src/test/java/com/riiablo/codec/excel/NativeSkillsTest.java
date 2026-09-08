package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeSkillsTest {
  @Test
  void exposesRawFormulaAndTypedD2mooFieldsWithoutDiscardingPartialRows() throws Exception {
    NativeSkills skills = NativeSkills.parse(
        ("skill\tID\tsrvstfunc\tsrvdofunc\tleftskill\tcalc1\n"
            + "Fire Bolt\t99\t3\t8\t1\t(skill('Warmth'.blvl)+1)\n")
            .getBytes(StandardCharsets.ISO_8859_1));

    NativeSkills.Entry fireBolt = skills.get(0);
    assertEquals(0, fireBolt.id);
    assertEquals(99, fireBolt.txtId);
    assertEquals(fireBolt, skills.get("fire bolt"));
    assertEquals(Integer.valueOf(3), fireBolt.integer("srvstfunc"));
    assertTrue(fireBolt.bool("leftskill"));
    assertEquals("(skill('Warmth'.blvl)+1)", fireBolt.string("calc1"));
    assertTrue(skills.schemaIssues().size() > 100,
        "A partial fixture must report every missing D2MOO projection field");
  }
}
