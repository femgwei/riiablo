package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SkillFormula;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Fifty-fifth DMG-04 audit: Energy Shield's incoming-damage-to-mana state. */
class EnergyShieldGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_ABSORB_PERCENT = {
      20, 25, 30, 35, 40, 45, 50, 55, 57, 59,
      61, 63, 65, 67, 69, 71, 72, 73, 74, 75
  };

  @Test
  void levelOneToTwentyMatchesD2mooStateFormulas() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.ENERGY_SHIELD);
    assertNotNull(skill);
    assertEquals("Energy Shield", skill.skill);
    assertEquals(58, skill.Id);
    assertEquals(0, skill.srvstfunc);
    assertEquals(23, skill.srvdofunc);
    assertEquals("energyshield", skill.aurastate);
    assertEquals("ln12", skill.auralencalc);
    assertArrayEquals(new String[] {"absorbdamage", "", ""}, skill.auraevent);
    assertArrayEquals(new int[] {24, 0, 0}, skill.auraeventfunc);
    assertTrue(Arrays.stream(skill.aurastat).allMatch(value -> value == null || value.isEmpty()));
    assertTrue(Arrays.stream(skill.aurastatcalc)
        .allMatch(value -> value == null || value.isEmpty()));
    assertEquals("\"min(edmn,95)\"", skill.calc1);
    assertEquals("par5-skill('Telekinesis'.blvl)", skill.calc2);
    assertArrayEquals(new int[] {3600, 1500, 0, 0, 32, 0, 0, 0}, skill.Param);
    assertEquals(20, skill.EMin);
    assertArrayEquals(new int[] {5, 2, 1, 1, 1}, skill.EMinLev);

    for (int level = 1; level <= 20; level++) {
      assertEquals(D2MOO_ABSORB_PERCENT[level - 1],
          SkillFormula.evaluate(skill.calc1, skill, level, name -> 0),
          "absorb percent level " + level);
      assertEquals(3600 + (level - 1) * 1500,
          SkillFormula.evaluate(skill.auralencalc, skill, level),
          "duration level " + level);
      assertEquals(32, SkillFormula.evaluate(skill.calc2, skill, level, name -> 0),
          "zero-Telekinesis divisor level " + level);
    }
    assertEquals(12,
        SkillFormula.evaluate(skill.calc2, skill, 1,
            name -> "Telekinesis".equals(name) ? 20 : 0));
  }
}
