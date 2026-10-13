package com.riiablo.engine.client.automap;

import com.badlogic.gdx.utils.Array;

/** D2Client 1.10f town callback (0x6FACD840), separate from MaxiMap cells. */
public final class NativeTownAutomap {
  private static final int[] LUT_GHOLEIN_BLANKS = {0, 10, 15, 16, 19, 20, 25, 30, 35, 36, 39};

  private NativeTownAutomap() {}

  public static String spritePath(int levelId) {
    switch (levelId) {
      case 40: return AutomapTileRenderer.PATH_ACT2_MAP;
      case 103: return AutomapTileRenderer.PATH_ACT4_MAP;
      case 109: return AutomapTileRenderer.PATH_EXTN_MAP;
      default: return null;
    }
  }

  /** Positions remain native Automap pixels, including offsets between DT1 tiles. */
  public static Array<AutomapExplorationStore.Cell> cells(int levelId, int pickedFile,
      int centerTileX, int centerTileY) {
    Array<AutomapExplorationStore.Cell> result = new Array<>();
    int columns, rows, stepX, stepY, offsetX, offsetY, frame;
    switch (levelId) {
      case 40:
        columns = 5; rows = 4; stepX = 160; stepY = 100;
        offsetX = 53; offsetY = -81; frame = pickedFile == 0 ? 20 : 0;
        break;
      case 103:
        columns = 2; rows = 2; stepX = 136; stepY = 90;
        offsetX = -3; offsetY = -50; frame = 0;
        break;
      case 109:
        columns = 3; rows = 2; stepX = 180; stepY = 170;
        offsetX = -20; offsetY = -155; frame = 0;
        break;
      default: return result;
    }
    int originX = 8 * (centerTileX - centerTileY) - stepX * columns / 2 - offsetX;
    int originY = 4 * (centerTileX + centerTileY) - stepY * rows / 2 - offsetY;
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++, frame++) {
        if (levelId == 40 && blankLutGholeinFrame(frame)) continue;
        result.add(new AutomapExplorationStore.Cell(frame,
            (short) (originX + column * stepX), (short) (originY + row * stepY)));
      }
    }
    return result;
  }

  private static boolean blankLutGholeinFrame(int frame) {
    for (int blank : LUT_GHOLEIN_BLANKS) if (frame == blank) return true;
    return false;
  }
}
