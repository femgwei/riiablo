package com.riiablo.engine.server.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeTowardPathProbeTest {
  @Test
  void cardinalAndDiagonalNativeDirectionPrioritiesAreNotEightWayFloodFill() {
    int[][][] cases = {
        {{20, 10}, {11, 10}, {11, 11}, {11, 9}},
        {{0, 10}, {9, 10}, {9, 11}, {9, 9}},
        {{10, 20}, {10, 11}, {11, 11}, {9, 11}},
        {{10, 0}, {10, 9}, {11, 9}, {9, 9}},
        {{20, 20}, {11, 11}, {11, 10}, {10, 11}},
        {{0, 0}, {9, 9}, {9, 10}, {10, 9}}
    };
    for (int[][] row : cases) {
      List<String> tested = new ArrayList<>();
      assertFalse(NativeTowardPathProbe.hasStep(10, 10, row[0][0], row[0][1], (x, y) -> {
        tested.add(x + "," + y); return true;
      }));
      assertEquals(Arrays.asList(row[1][0] + "," + row[1][1], row[2][0] + "," + row[2][1],
          row[3][0] + "," + row[3][1]), tested);
    }
  }

  @Test
  void eachOfThreeNativeInitialCandidatesCanProveNonzeroPoints() {
    for (int freeY : new int[] {10, 11, 9}) {
      assertTrue(NativeTowardPathProbe.hasStep(10, 10, 20, 10,
          (x, y) -> x != 11 || y != freeY));
    }
    assertFalse(NativeTowardPathProbe.hasStep(10, 10, 20, 10,
        (x, y) -> x != 9)); // West is free, but not a native eastward candidate.
  }

  @Test
  void coincidentTargetHasNoStepAndAllSlopesRemainWithinAdjacentCells() {
    assertFalse(NativeTowardPathProbe.hasStep(10, 10, 10, 10, (x, y) -> fail("no collision query")));
    for (int dx = -20; dx <= 20; dx++) {
      for (int dy = -20; dy <= 20; dy++) {
        final int[] count = {0};
        assertFalse(NativeTowardPathProbe.hasStep(10, 10, 10 + dx, 10 + dy, (x, y) -> {
          assertTrue(Math.abs(x - 10) <= 1 && Math.abs(y - 10) <= 1);
          assertFalse(x == 10 && y == 10);
          count[0]++;
          return true;
        }));
        assertEquals(dx == 0 && dy == 0 ? 0 : 3, count[0]);
      }
    }
  }
}
