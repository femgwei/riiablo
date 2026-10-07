package com.riiablo.map;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DT1sAnimationTest {
  @Test
  public void resolvesFramesAcrossLoadedTileLibraries() throws Exception {
    DT1.Tile frame0 = tile(0, true);
    DT1.Tile frame1 = tile(1, false);
    DT1.Tile frame2 = tile(2, false);
    DT1s dt1s = new DT1s();
    add(dt1s, frame0, "cottages.dt1");
    add(dt1s, frame1, "other.dt1");
    add(dt1s, frame2, "other.dt1");

    assertTrue(dt1s.isAnimated(frame0));
    assertTrue(dt1s.isAnimated(frame2));
    assertEquals(3, dt1s.getAnimationFrameCount(frame0));
    assertSame(frame0, dt1s.getAnimationFrame(frame2, 0));
    assertSame(frame1, dt1s.getAnimationFrame(frame0, 1));
    assertSame(frame2, dt1s.getAnimationFrame(frame0, 2));
  }

  @Test
  public void leavesOrdinaryRarityGroupStatic() throws Exception {
    DT1.Tile common = tile(1, false);
    DT1.Tile rare = tile(10, false);
    DT1s dt1s = new DT1s();
    dt1s.add(common);
    dt1s.add(rare);

    assertFalse(dt1s.isAnimated(common));
    assertEquals(1, dt1s.getAnimationFrameCount(common));
  }

  private static void add(DT1s dt1s, DT1.Tile tile, String source) {
    dt1s.add(tile);
    IntMap<Array<DT1.Tile>> indexed = dt1s.sourceTiles.get(source);
    if (indexed == null) dt1s.sourceTiles.put(source, indexed = new IntMap<>());
    Array<DT1.Tile> variants = indexed.get(tile.id);
    if (variants == null) indexed.put(tile.id, variants = new Array<>());
    variants.add(tile);
    dt1s.tileSources.put(tile, source);
  }

  private static DT1.Tile tile(int frame, boolean animated) throws Exception {
    byte[] header = new byte[DT1.Tile.SIZE];
    ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(0); // light direction
    buffer.putShort((short) 0); // roof height
    buffer.put((byte) 0); // material flags, low byte
    buffer.put((byte) (animated ? 1 : 0)); // material flags, high byte
    buffer.putInt(-80); // height
    buffer.putInt(160); // width
    buffer.putInt(0); // reserved
    buffer.putInt(Orientation.FLOOR);
    buffer.putInt(7); // style
    buffer.putInt(3); // sequence
    buffer.putInt(frame);
    return new DT1.Tile(new ByteArrayInputStream(header));
  }
}
