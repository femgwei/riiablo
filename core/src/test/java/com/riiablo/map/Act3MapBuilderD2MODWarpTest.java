package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act3MapBuilderD2MODWarpTest {
  @Test
  void warpApproachRequiresPlayerFootprintAndRealInteractionDistance() {
    Map.Zone zone = new Map.Zone();
    zone.tilesX = zone.tilesY = 3;
    zone.width = zone.height = 15;
    zone.flags = new byte[225];
    java.util.Arrays.fill(zone.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    zone.flags[7 * 15 + 7] = 0;
    org.junit.jupiter.api.Assertions.assertFalse(
        Act3MapBuilderD2MOD.hasPlayerWarpApproach(zone, 7, 4), "One free subtile cannot hold a player");
    for (int[] offset : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
      zone.flags[(7 + offset[1]) * 15 + 7 + offset[0]] = 0;
    }
    assertTrue(Act3MapBuilderD2MOD.hasPlayerWarpApproach(zone, 7, 4));
    org.junit.jupiter.api.Assertions.assertFalse(
        Act3MapBuilderD2MOD.hasPlayerWarpApproach(zone, 7, 2), "A distant free cell is outside Warp range");
    zone.flags[7 * 15 + 7] = (byte) DT1.Tile.FLAG_BLOCK_PLAYER_WALK;
    org.junit.jupiter.api.Assertions.assertFalse(Act3MapBuilderD2MOD.hasPlayerWarpApproach(zone, 7, 4));
  }
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

  @Test
  void classifiesStaticDungeonEdgesSeparatelyFromOutdoorAndDuranceLinks() {
    assertTrue(Act3MapBuilderD2MOD.isStaticAct3DungeonEdge(76, 84));
    assertTrue(Act3MapBuilderD2MOD.isStaticAct3DungeonEdge(78, 88));
    assertTrue(!Act3MapBuilderD2MOD.isStaticAct3DungeonEdge(76, 77));
    assertTrue(!Act3MapBuilderD2MOD.isStaticAct3DungeonEdge(100, 101));
  }
}
