package com.riiablo.engine.server;

import com.d2moo.common.drlg.D2Seed;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonsterSpawnSearchTest {
  @Test void exactSearchStillConsumesThreeRoomRolls() {
    D2Seed seed = new D2Seed(0, 666);
    assertTrue(NativeMonsterSpawnSearch.search(seed, 100, 200, 0, (x, y) -> x == 100 && y == 200));
    assertEquals(3979650335L, Integer.toUnsignedLong(seed.getNLowSeed()));
    assertEquals(1403592407, seed.getNHighSeed());
  }

  @Test void binaryVerifiedStartAndAdvanceBeforeProbeArePreserved() {
    D2Seed seed = new D2Seed(0, 666);
    ArrayList<String> visited = new ArrayList<>();
    assertTrue(NativeMonsterSpawnSearch.search(seed, 100, 200, 6, (x, y) -> {
      visited.add(x + "," + y);
      return visited.size() == 6;
    }));
    // 1.10F uses initial +X even on the positive Y edge; do not normalize
    // the direction or deduplicate its probes into a different traversal.
    assertEquals(java.util.Arrays.asList("99,203", "100,203", "101,203", "102,203", "103,203", "102,203"), visited);
    assertEquals(101162930, seed.getNLowSeed());
    assertEquals(1659881787, seed.getNHighSeed());
  }

  @Test void failedRingsHaveNativeRadiusAndAttemptBounds() {
    int[] attempts = {0};
    assertFalse(NativeMonsterSpawnSearch.search(new D2Seed(1, 666), 100, 100, 6, (x, y) -> {
      assertTrue(Math.max(Math.abs(x - 100), Math.abs(y - 100)) == 3
          || Math.max(Math.abs(x - 100), Math.abs(y - 100)) == 6);
      attempts[0]++; return false;
    }));
    assertEquals(24 + 48, attempts[0]);
  }

  @Test void spawnColumnSelectsNativeMaskNotMovementProfile() {
    assertEquals(0x3c01, NativeMonsterSpawnSearch.collisionMask(0));
    assertEquals(0x1c0, NativeMonsterSpawnSearch.collisionMask(1));
    assertEquals(0x3f11, NativeMonsterSpawnSearch.collisionMask(2));
    assertEquals(0, NativeMonsterSpawnSearch.collisionMask(3));
  }
}
