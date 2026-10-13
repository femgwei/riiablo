package com.riiablo.map;

import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2JungleStrc;
import com.d2moo.common.drlg.DrlgOutPlace;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Act3JungleCoordinateParityTest {
  @Test void unsignedNativeCorrectionKeepsBranchesOnPresetGrid() {
    // D2MOO sub_6FD83970 declares nTemp unsigned: negative >> 31 must yield 1.
    D2DrlgCoord origin = new D2DrlgCoord();
    origin.setNPosX(1024);
    origin.setNPosY(2048);
    int[] dx = {0, -64, 64, -64, 64};
    int[] dy = {-192, -64, -64, -128, -128};
    for (int direction = 0; direction < 5; direction++) {
      D2JungleStrc jungle = new D2JungleStrc();
      DrlgOutPlace.sub_6FD83970(origin, jungle, direction, 64, 192);
      assertEquals(1024 + dx[direction], jungle.getPDrlgCoord().getNPosX());
      assertEquals(2048 + dy[direction], jungle.getPDrlgCoord().getNPosY());
      assertEquals(0, jungle.getPDrlgCoord().getNPosY() % 32);
    }
  }
}
