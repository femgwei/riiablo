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

  @Test void nativeBoneTrailHasNoSubLoopToExtend() {
    Missiles.Entry spear = Riiablo.files.Missiles.get("bonespear");
    Missiles.Entry trail = Riiablo.files.Missiles.get(spear.CltSubMissile[0]);
    assertEquals(3, spear.CltParam[0]);
    // D2MOO applies nLoops only to a child with SubLoop enabled. The
    // native trail has no subloop, so CltParam1 is neither extra lifetime
    // nor a three-frame emission interval for this row.
    assertEquals(0, trail.SubLoop);
    assertEquals(0, trail.SubStart);
    assertEquals(0, trail.SubStop);
    assertEquals(0, trail.LoopAnim);
    assertEquals(9, trail.AnimLen);
    assertEquals(0, trail.Vel);
    assertEquals(8, MissileImpactPresentationSystem.nativePresentationLifetimeFrames(trail));
  }
}
