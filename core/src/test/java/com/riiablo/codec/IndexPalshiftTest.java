package com.riiablo.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.engine.server.component.CofTransforms;
import org.junit.jupiter.api.Test;

class IndexPalshiftTest {
  @Test
  void palshiftRowsUseNativeVariationNumbers() {
    byte[] data = new byte[8 * Palette.COLORS];
    for (int row = 0; row < 8; row++) {
      for (int color = 0; color < Palette.COLORS; color++) {
        data[row * Palette.COLORS + color] = (byte) ((color + row) & 0xFF);
      }
    }

    Index index = Index.fromPalshift("FS", data);
    assertEquals(10, index.get(3)[7] & 0xFF);
    assertEquals(7, index.get(7)[0] & 0xFF);
    // Rows beyond the seven native variations remain identity mappings.
    assertEquals(31, index.get(8)[31] & 0xFF);
  }

  @Test
  void monsterTransLvlUsesPaletteRowOffset() {
    // Monster TransLvl values select palshift rows after the two reserved rows.
    assertEquals(2, CofTransforms.monsterPaletteRow(0));
    assertEquals(5, CofTransforms.monsterPaletteRow(3));
    assertEquals(9, CofTransforms.monsterPaletteRow(7));
    assertEquals(CofTransforms.TRANSFORM_NULL, CofTransforms.monsterPaletteRow(8));
  }
}
