package com.riiablo.map.d2moo;

import com.d2moo.common.d2cmp.D2Cmp;
import com.d2moo.common.d2cmp.D2TileLibraryHashStrc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2CmpTileLibraryDedupTest {
  @Test void tileHashKeepsAllAnimationFramesAcrossInterleavedHashCollisions() {
    java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(276 + 8 * 96)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(0, 7); bytes.putInt(4, 6);
    bytes.putInt(268, 8); bytes.putInt(272, 276);
    for (int i = 0; i < 8; i++) {
      int offset = 276 + i * 96;
      bytes.putInt(offset + 20, 0);
      // Different styles collide in the 128-entry hash, interleaving two
      // animation sequences. Each must retain its own frames 0..3.
      bytes.putInt(offset + 24, i % 2 == 0 ? 20 : 21);
      bytes.putInt(offset + 28, 0);
      bytes.putInt(offset + 32, i / 2);
    }
    Object[] slots = new Object[32];
    com.d2moo.common.util.D2FileReader.ArchiveReader archive = path -> bytes.array();
    D2Cmp.loadTileLibrarySlot(archive, slots, "test/all-lava-frames.dt1");
    for (int style : new int[] {20, 21}) {
      Object[] entries = new Object[40];
      int count = D2Cmp.getTiles(slots, 0, style, 0, entries, entries.length);
      assertEquals(4, count);
      java.util.Set<Integer> rarities = new java.util.HashSet<>();
      for (int i = 0; i < count; i++) rarities.add(D2Cmp.getTileRarity(entries[i]));
      assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(0, 1, 2, 3)), rarities);
    }
  }

  @Test void sameMpqLibraryIsNotAppendedAfterHashCollisionOrCaseChange() {
    Object[] slots = new Object[7];
    Object library = new D2TileLibraryHashStrc("DATA\\GLOBAL\\Tiles\\Act4\\Lava\\Ground.dt1");
    slots[6] = library;
    D2Cmp.loadTileLibrarySlot(null, slots, "data/global/tiles/act4/lava/ground.dt1");
    assertSame(library, slots[6]);
    int used = 0;
    for (Object slot : slots) if (slot != null) used++;
    assertEquals(1, used, "A repeated DT1 must not multiply tile choices or animation frames");
  }
}
