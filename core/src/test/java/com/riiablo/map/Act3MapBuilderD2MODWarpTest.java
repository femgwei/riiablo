package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act3MapBuilderD2MODWarpTest {
  @Test
  void runtimeSlotsPreferExistingDestinationThenFirstEmptySlot() {
    int[] vis = {0, 81, 0, 0, 0, 0, 0, 0};
    int[] warp = {-1, 80, -1, -1, -1, -1, -1, -1};
    assertEquals(1, Act3MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 81));
    assertEquals(0, Act3MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 80));
  }

  @Test
  void nativeChainHasEveryAdjacentAct3OutdoorLink() {
    assertEquals(Act3MapBuilderD2MOD.ACT3_OUTDOOR_CHAIN.length - 1,
        Act3MapBuilderD2MOD.ACT3_OUTDOOR_LINKS.length);
    for (int i = 0; i < Act3MapBuilderD2MOD.ACT3_OUTDOOR_LINKS.length; i++) {
      assertEquals(Act3MapBuilderD2MOD.ACT3_OUTDOOR_CHAIN[i],
          Act3MapBuilderD2MOD.ACT3_OUTDOOR_LINKS[i][0]);
      assertEquals(Act3MapBuilderD2MOD.ACT3_OUTDOOR_CHAIN[i + 1],
          Act3MapBuilderD2MOD.ACT3_OUTDOOR_LINKS[i][1]);
      assertTrue(Act3MapBuilderD2MOD.ACT3_OUTDOOR_LINKS[i][0]
          < Act3MapBuilderD2MOD.ACT3_OUTDOOR_LINKS[i][1]);
    }
  }
}
