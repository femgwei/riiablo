package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Test;

class HorrorSequenceDataTest extends RiiabloTest {
  @Test void originalSequenceHasFourteenA2PointsAndActionOneAtNine() {
    int point = 0;
    for (String line : Riiablo.mpqs.resolve("data/global/excel/monseq.txt")
        .readString("windows-1252").split("\\r?\\n")) {
      String[] cell = line.split("\t", -1);
      if (!"seq_horrorarcticblast".equals(cell[0])) continue;
      assertEquals("A2", cell[1]);
      assertEquals(Integer.toString(point), cell[2]);
      assertEquals(point == 9 ? "1" : "", cell[4]);
      point++;
    }
    assertEquals(14, point);
    assertEquals("xx", Riiablo.files.skills.get(323).monanim);
    assertEquals("2", Riiablo.files.skills.get(323).calc3);
    int rows = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"FrozenHorror".equals(mon.AI)) continue;
      rows++;
      assertEquals(323, Riiablo.files.skills.get(mon.Skill1).Id);
      assertEquals("seq_horrorarcticblast", mon.Sk1mode);
      assertEquals(9, Riiablo.files.monstats2.get(mon.MonStatsEx).InfernoAnim);
      assertEquals(1, Riiablo.files.monstats2.get(mon.MonStatsEx).InfernoLen);
    }
    assertEquals(5, rows);
  }
}
