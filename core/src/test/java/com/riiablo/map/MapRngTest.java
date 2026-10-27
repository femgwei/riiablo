package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.server.NativeRng;
import org.junit.jupiter.api.Test;

/** Map generation uses an explicit, reproducible stream instead of global RNG state. */
class MapRngTest {
  @Test
  void streamIdentityAndSequenceAreStable() {
    NativeRng first = MapRng.open(0x110F, "act2", 1);
    NativeRng second = MapRng.open(0x110F, "act2", 1);
    assertEquals("map.act2", first.streamName());
    assertEquals(1L, first.contextId());
    for (int i = 0; i < 64; i++) assertEquals(first.nextInt(), second.nextInt());
    assertNotEquals(MapRng.open(0x110F, "act3", 1).streamName(), first.streamName());
  }

  @Test
  void inclusiveRangeNeverEscapesItsBounds() {
    NativeRng rng = MapRng.open(7, "act3", 2);
    for (int i = 0; i < 256; i++) {
      int value = MapRng.nextInt(rng, 3, 8);
      assertTrue(value >= 3 && value <= 8);
    }
  }
}
