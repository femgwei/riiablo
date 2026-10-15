package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

class NativeSubmergeDataTest extends RiiabloTest {
  @Test
  void frogDemonRowsResolveNativeSubmergeAndEmergeSkills() {
    int rows = 0;
    for (MonStats.Entry monster : Riiablo.files.monstats) {
      if (!"FrogDemon".equalsIgnoreCase(monster.AI)) continue;
      rows++;
      Skills.Entry submerge = Riiablo.files.skills.get(monster.Skill1);
      Skills.Entry emerge = Riiablo.files.skills.get(monster.Skill2);
      assertNotNull(submerge, "missing FrogDemon Skill1 for " + monster.Id);
      assertNotNull(emerge, "missing FrogDemon Skill2 for " + monster.Id);
      assertEquals(51, submerge.srvstfunc, "Skill1 must use native Submerge");
      assertEquals(52, emerge.srvstfunc, "Skill2 must use native Emerge");
      assertEquals(94, submerge.srvdofunc);
      assertEquals(0, emerge.srvdofunc);
      assertEquals("seq_froghidden", monster.Sk1mode);
      assertEquals("S1", monster.Sk2mode);
      org.junit.jupiter.api.Assertions.assertArrayEquals(new int[] {9, 9, 9}, monster.aip8);
    }
    assertEquals(3, rows);
    int points = 0;
    for (String line : Riiablo.mpqs.resolve("data/global/excel/monseq.txt").readString("windows-1252").split("\\r?\\n")) {
      String[] col = line.split("\\t", -1);
      if (!"seq_froghidden".equals(col[0])) continue;
      assertEquals("S1", col[1]);
      assertEquals(points++, Integer.parseInt(col[2]));
      assertEquals("", col[4], "hidden sequence has no attack/missile markers");
    }
    assertEquals(3, points);
    com.riiablo.codec.D2 anim = com.riiablo.codec.D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"));
    assertEquals(16, anim.getEntry("FDS1HTH").framesPerDir);
  }
}
