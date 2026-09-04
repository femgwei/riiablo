package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class NativeFrenzyDataTest extends RiiabloTest {
  @Test
  void nativeRowsDrivePlayerAndMonsterFrenzy() {
    Skills.Entry player = Riiablo.files.skills.get("Frenzy");
    Skills.Entry monster = Riiablo.files.skills.get("MonFrenzy");
    Skills.Entry bloodLord = Riiablo.files.skills.get("BloodLordFrenzy");
    assertNotNull(player);
    assertNotNull(monster);
    assertNotNull(bloodLord);
    assertEquals(9, player.srvdofunc);
    assertEquals("frenzy", player.aurastate);
    assertEquals("dm34", player.aurastatcalc[0]);
    assertEquals("dm56", player.aurastatcalc[1]);
    assertEquals(150, BarbarianSkills.getFrenzyDuration(player, 1));
    assertEquals(48, BarbarianSkills.getFrenzyMovementPercent(player, 1));
    assertEquals(7, BarbarianSkills.getFrenzyAnimationRatePercent(player, 1));
    assertEquals(109, monster.srvdofunc);
    assertEquals(200, BarbarianSkills.getFrenzyDuration(monster, 1));
    assertEquals(225, BarbarianSkills.getFrenzyDuration(monster, 2));
    assertEquals(42, BarbarianSkills.getFrenzyMovementPercent(monster, 1));
    assertEquals(109, bloodLord.srvdofunc);
    assertEquals("monfrenzy", bloodLord.aurastate);
  }
}
