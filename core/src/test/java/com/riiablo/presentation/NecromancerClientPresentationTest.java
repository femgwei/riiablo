package com.riiablo.presentation;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NecromancerClientPresentationTest extends RiiabloTest {
  @Test void nativeClientRows() {
    Missiles.Entry spear = Riiablo.files.Missiles.get("bonespear");
    assertEquals(18, spear.pCltDoFunc);
    assertEquals("bonespeartrail", spear.CltSubMissile[0]);
    assertEquals(3, spear.CltParam[0]);
    assertEquals(8, Riiablo.files.Missiles.get("bonespeartrail").Range);
    Missiles.Entry center = Riiablo.files.Missiles.get("cursecenter");
    assertEquals(17, center.pCltDoFunc);
    assertEquals(7, center.CltParam[0]);
    assertEquals(20, center.Range);
    for (int remaining = 1; remaining <= 20; remaining++)
      assertEquals(remaining == 3 || remaining == 6,
          MissileImpactPresentationSystem.cursePulseFrame(remaining, 7));
    for (Skills.Entry row : Riiablo.files.skills)
      if (row.Id >= 66 && row.Id <= 95 && row.cltdofunc == 30) {
        assertNotNull(Riiablo.files.Missiles.get(row.cltmissilea));
        assertTrue(SkillCastHandler.curseVisualRadius(row, 1) >= 2);
      }
  }
}
