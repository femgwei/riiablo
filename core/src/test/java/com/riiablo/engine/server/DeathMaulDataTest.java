package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;

class DeathMaulDataTest extends RiiabloTest {
  @Test void originalSequenceHasThirtyFiveA2PointsAndOneActionAtThirteen() {
    String text = Riiablo.mpqs.resolve("data/global/excel/monseq.txt").readString("windows-1252");
    List<Integer> frames = new ArrayList<>();
    List<Integer> actions = new ArrayList<>();
    for (String line : text.split("\\r?\\n")) {
      String[] cells = line.split("\\t", -1);
      if (!"seq_deathmaulerdig".equals(cells[0])) continue;
      assertEquals("A2", cells[1]);
      if (!cells[4].isEmpty()) {
        assertEquals("1", cells[4]);
        actions.add(frames.size());
      }
      frames.add(Integer.parseInt(cells[2]));
    }
    assertEquals(java.util.Arrays.asList(13), actions);
    assertArrayEquals(new Integer[] {0,0,1,1,2,2,3,3,4,4,5,6,7,7,7,7,
        8,8,8,8,9,9,9,9,9,10,10,10,10,11,11,11,11,12,13}, frames.toArray(new Integer[0]));
    int rows = 0;
    for (com.riiablo.codec.excel.MonStats.Entry row : Riiablo.files.monstats) {
      if (!"DeathMauler".equals(row.AI)) continue;
      assertEquals("seq_deathmaulerdig", row.Sk1mode);
      assertEquals(308, Riiablo.files.skills.get(row.Skill1).Id);
      rows++;
    }
    assertEquals(6, rows);
    assertEquals(136, Riiablo.files.skills.get(308).srvdofunc);
  }
}
