package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;

class CaveEntranceDs1VisibilityTest extends RiiabloTest {
  @Test
  void distinguishesVisibleCaveGraphicsFromHiddenDenWarpMarker() {
    assertSpecialVisibility("data/global/tiles/act1/caves/clfcave.ds1", 2, 0);
    assertSpecialVisibility("data/global/tiles/act1/caves/clfcave2.ds1", 2, 0);
    assertSpecialVisibility("data/global/tiles/act1/caves/denent2.ds1", 0, 1);
  }

  private static void assertSpecialVisibility(
      String path, int expectedVisible, int expectedHidden) {
    FileHandle file = Riiablo.mpqs.resolve(path);
    DS1 ds1 = DS1.loadFromFile(file);
    int visible = 0;
    int hidden = 0;
    for (DS1.Cell wall : ds1.walls) {
      if (wall.orientation != Orientation.SPECIAL_10
          && wall.orientation != Orientation.SPECIAL_11) continue;
      if ((wall.value & DS1.Cell.HIDDEN_MASK) != 0) hidden++;
      else visible++;
    }
    assertEquals(expectedVisible, visible, path + " visible special count");
    assertEquals(expectedHidden, hidden, path + " hidden special count");
  }
}
