package com.riiablo.engine.client.automap;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTownAutomapTest {
  @Test void lutGholeinVariantsMatchNativeCallbackFramesAndPixelOrigins() {
    // 1.10f D2Client callback skips different blank pieces in each 20-frame bank.
    Array<AutomapExplorationStore.Cell> west = NativeTownAutomap.cells(40, 0, 28, 28);
    Array<AutomapExplorationStore.Cell> east = NativeTownAutomap.cells(40, 1, 28, 28);
    assertArrayEquals(new int[] {21,22,23,24,26,27,28,29,31,32,33,34,37,38}, frames(west));
    assertArrayEquals(new int[] {1,2,3,4,5,6,7,8,9,11,12,13,14,17,18}, frames(east));
    assertCell(west.first(), 21, -293, 105);
    assertCell(west.peek(), 38, 27, 405);
    assertCell(east.first(), 1, -293, 105);
    assertCell(east.peek(), 18, 27, 405);
  }

  @Test void fortressAndHarrogathUseSeparateSpriteBanks() {
    Array<AutomapExplorationStore.Cell> fortress = NativeTownAutomap.cells(103, 0, 20, 20);
    assertArrayEquals(new int[] {0,1,2,3}, frames(fortress));
    assertCell(fortress.first(), 0, -133, 120);
    assertCell(fortress.peek(), 3, 3, 210);
    Array<AutomapExplorationStore.Cell> harrogath = NativeTownAutomap.cells(109, 0, 20, 20);
    assertArrayEquals(new int[] {0,1,2,3,4,5}, frames(harrogath));
    assertCell(harrogath.first(), 0, -250, 145);
    assertCell(harrogath.peek(), 5, 110, 315);
    assertNull(NativeTownAutomap.spritePath(55));
    assertTrue(NativeTownAutomap.cells(55, 0, 20, 20).isEmpty());
  }

  private static int[] frames(Array<AutomapExplorationStore.Cell> cells) {
    int[] result = new int[cells.size];
    for (int i = 0; i < result.length; i++) result[i] = cells.get(i).cellNo;
    return result;
  }

  private static void assertCell(AutomapExplorationStore.Cell cell, int frame, int x, int y) {
    assertEquals(frame, cell.cellNo);
    assertEquals(x, cell.x);
    assertEquals(y, cell.y);
  }
}
