package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.d2moo.common.d2cmp.D2Cmp;
import com.d2moo.common.d2cmp.D2TileData;
import com.d2moo.common.d2cmp.D2TileLibrary;

class D2CmpDt1ParserTest {
  private static final int HEADER_SIZE = 276;
  private static final int TILE_HEADER_SIZE = 96;

  @Test
  void parsesRealDt1HeaderOffsetsAndFixedTileHeaders() {
    ByteBuffer data = ByteBuffer
        .allocate(HEADER_SIZE + 2 * TILE_HEADER_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(0, 7);
    data.putInt(4, 6);
    data.putInt(268, 2);
    data.putInt(272, HEADER_SIZE);

    putTile(data, HEADER_SIZE, 0, 2, 3, 160, 80, 4, 0x1234);
    putTile(data, HEADER_SIZE + TILE_HEADER_SIZE, 13, 7, 9, 320, 160, 11, 0x00A5);

    D2TileLibrary library = D2Cmp.parseDT1FileData(data.array(), "synthetic.dt1");
    assertNotNull(library);
    assertEquals(2, library.getNTiles());

    D2TileData floor = library.getPTiles()[0];
    assertEquals(0, floor.getNOrientation());
    assertEquals(2, floor.getNTileId());
    assertEquals(3, floor.getNSequence());
    assertEquals(160, floor.getNWidth());
    assertEquals(80, floor.getNHeight());
    assertEquals(4, floor.getNRarity());
    assertEquals(0x1234, floor.getDwFlags());

    D2TileData shadow = library.getPTiles()[1];
    assertEquals(13, shadow.getNOrientation());
    assertEquals(7, shadow.getNTileId());
    assertEquals(9, shadow.getNSequence());
    assertEquals(11, shadow.getNRarity());
  }

  @Test
  void rejectsTileHeaderArrayOutsideFileBounds() {
    ByteBuffer data = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    data.putInt(0, 7);
    data.putInt(4, 6);
    data.putInt(268, 1);
    data.putInt(272, HEADER_SIZE);

    assertNull(D2Cmp.parseDT1FileData(data.array(), "truncated.dt1"));
  }

  private static void putTile(ByteBuffer data, int offset, int orientation, int style,
      int sequence, int width, int height, int rarity, int flags) {
    data.putShort(offset + 4, (short) 0);
    data.putShort(offset + 6, (short) flags);
    data.putInt(offset + 8, height);
    data.putInt(offset + 12, width);
    data.putInt(offset + 16, 0);
    data.putInt(offset + 20, orientation);
    data.putInt(offset + 24, style);
    data.putInt(offset + 28, sequence);
    data.putInt(offset + 32, rarity);
  }
}
