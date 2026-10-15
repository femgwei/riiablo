package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

class DiabPrisonDataTest extends RiiabloTest {
  @Test void nativeRowsUseFourIdleMonstersWithoutPlayerBoneWallLifetime() {
    com.riiablo.codec.excel.Skills.Entry s = Riiablo.files.skills.get("DiabPrison");
    assertEquals(199, s.Id); assertEquals(104, s.srvdofunc);
    assertEquals(0, s.srvstfunc); assertEquals("boneprison1", s.summon);
    assertEquals("none", s.pettype); assertEquals("S3", s.monanim);
    for (int i = 1; i <= 4; i++) {
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("boneprison" + i);
      assertEquals(339 + i, row.hcIdx); assertEquals("Idle", row.AI);
      assertTrue(row.killable); assertTrue(row.neverCount); assertEquals(0, row.Velocity);
      assertEquals(1, Riiablo.files.monstats2.get(row.MonStatsEx).SizeX);
    }
    for (String name : new String[] {"diablo", "diabloclone"}) {
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(name);
      assertEquals("DiabPrison", row.Skill7); assertEquals("S3", row.Sk7mode);
      assertEquals(1, row.Sk7lvl);
    }
  }
}
