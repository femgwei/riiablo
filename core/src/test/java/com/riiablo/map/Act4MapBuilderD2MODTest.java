package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2LevelIds;

class Act4MapBuilderD2MODTest {
  @Test
  void usesNativeAct4LevelIdsAndKeepsRiverOfFlameInMainChain() {
    assertEquals(D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS,
        Act4MapBuilderD2MOD.LEVEL_THEPANDEMONIUMFORTRESS);
    assertEquals(104, Act4MapBuilderD2MOD.LEVEL_OUTERSTEPPES);
    assertEquals(105, Act4MapBuilderD2MOD.LEVEL_PLAINSOFDESPAIR);
    assertEquals(106, Act4MapBuilderD2MOD.LEVEL_CITYOFTHEDAMNED);
    assertEquals(107, Act4MapBuilderD2MOD.LEVEL_RIVEROFFLAME);
    assertEquals(108, Act4MapBuilderD2MOD.LEVEL_CHAOSSANCTUM);
    assertEquals(6, Act4MapBuilderD2MOD.ACT4_CHAIN.length);
    assertEquals(5, Act4MapBuilderD2MOD.ACT4_CHAIN.length - 1);
  }

  @Test
  void runtimeWarpSlotUsesExistingDestinationThenFirstEmpty() {
    int[] vis = {0, 107, 0, 0};
    int[] warp = {-1, 3, -1, -1};
    assertEquals(1, Act4MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 107));
    assertEquals(0, Act4MapBuilderD2MOD.findRuntimeWarpSlot(vis, warp, 104));
  }
}
