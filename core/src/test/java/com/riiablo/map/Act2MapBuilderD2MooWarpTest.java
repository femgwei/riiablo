package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Regression tests for the runtime slot semantics used by D2Common's Act II links. */
class Act2MapBuilderD2MooWarpTest {

  @Test
  void appendsToFirstEmptyRuntimeSlotLikeDrlgSetWarpId() {
    int[] vis = {55, 55, 55, 55, 0, 0, 0, 0};
    int[] warp = {33, 34, 35, 36, -1, -1, -1, -1};

    assertEquals(4,
        Act2MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 40));
    vis[4] = 40;
    assertEquals(5,
        Act2MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 42));
  }

  @Test
  void reusesExistingDestinationSlotBeforeAppending() {
    int[] vis = {0, 0, 0, 0, 62, 0, 0, 0};
    int[] warp = {-1, -1, -1, -1, 47, -1, -1, -1};

    // A second request for the same destination must not consume another slot.
    assertEquals(4,
        Act2MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 62));
  }

  @Test
  void reportsExhaustedRuntimeWarpTable() {
    int[] vis = {1, 2, 3, 4, 5, 6, 7, 8};
    int[] warp = {0, 1, 2, 3, 4, 5, 6, 7};

    assertEquals(-1,
        Act2MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 40));
  }
}
