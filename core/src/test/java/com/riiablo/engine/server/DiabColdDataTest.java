package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

class DiabColdDataTest extends RiiabloTest {
  @Test void originalDiabColdRows() {
    Skills.Entry skill = Riiablo.files.skills.get(194);
    assertEquals(100, skill.srvdofunc);
    assertTrue(NativeDiabCold.supports(skill));
    assertEquals(0, skill.srvstfunc);
    assertEquals("S2", skill.monanim);
    assertEquals("mag", skill.EType);
    assertEquals(15, skill.EMin); assertEquals(25, skill.EMax);
    assertArrayEquals(new int[] {9, 9, 9, 9, 9}, skill.EMinLev);
    assertArrayEquals(new int[] {12, 12, 12, 12, 12}, skill.EMaxLev);
    assertEquals(8, skill.HitShift);
    assertEquals(100, skill.ELen);
    assertArrayEquals(new int[] {50, 50, 50}, skill.ELevLen);
    assertEquals(0, skill.ResultFlags); assertEquals(0, skill.HitFlags); assertEquals(0, skill.HitClass);
    assertTrue(Riiablo.files.NativeSkills.get(194).string("srvoverlay").isEmpty());
    int users = 0;
    for (Skills.Entry entry : Riiablo.files.skills) if (entry.srvdofunc == 100) {
      assertEquals(194, entry.Id); users++;
    }
    assertEquals(1, users);
    D2 anims = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    for (String id : new String[] {"diablo", "diabloclone"}) {
      MonStats.Entry mon = Riiablo.files.monstats.get(id);
      D2.Entry anim = anims.getEntry((mon.Code + mon.Sk2mode + "HTH").toUpperCase());
      assertEquals("DiabCold", mon.Skill2); assertEquals("S2", mon.Sk2mode);
      assertEquals(id.equals("diablo") ? 4 : 2, mon.Sk2lvl);
      assertEquals(16, anim.framesPerDir); assertEquals(256, anim.speed);
      for (int i = 0; i < anim.framesPerDir; i++)
        assertEquals(i == 6 ? com.riiablo.engine.Engine.KEYFRAME_ATK : 0, anim.data[i]);
      assertEquals(id.equals("diablo") ? 250 : 150, NativeDiabCold.freezeLength(skill, mon.Sk2lvl));
    }
  }

  @Test void lengthHasThreeTiersAndPercentageSynergyNotFlatAddition() {
    Skills.Entry skill = new Skills.Entry();
    skill.ELen = 100; skill.ELevLen = new int[] {10, 20, 30};
    skill.ELenSymPerCalc = "50";
    assertEquals(0, NativeDiabCold.freezeLength(skill, 0));
    assertEquals(150, NativeDiabCold.freezeLength(skill, 1));
    assertEquals(255, NativeDiabCold.freezeLength(skill, 8));
    assertEquals(495, NativeDiabCold.freezeLength(skill, 16));
    assertEquals(540, NativeDiabCold.freezeLength(skill, 17));
  }
}
