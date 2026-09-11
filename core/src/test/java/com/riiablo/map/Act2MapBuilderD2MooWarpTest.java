package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.Levels;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

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

  @Test
  void placesCanyonAfterValleyInsteadOfUsingDisconnectedLevelOffset() {
    int[] placement = Act2MapBuilderD2MOD.resolveCanyonPlacement(
        1200, -80, 640, 12, 34);

    assertEquals(1840, placement[0]);
    assertEquals(-80, placement[1]);
    assertEquals(0, placement[2]);
  }

  @Test
  void fallsBackWhenValleySizeIsUnavailable() {
    int[] placement = Act2MapBuilderD2MOD.resolveCanyonPlacement(
        1200, -80, 0, 12, 34);

    assertEquals(12, placement[0]);
    assertEquals(34, placement[1]);
    assertEquals(1, placement[2]);
  }

  @Test
  void discoversOnlyRealAct2WarpEdgesFromLevelsVisAndWarp() {
    Levels.Entry rocky = level(41, new int[] {42, 63, 0}, new int[] {0, 7, -1});
    Levels.Entry dry = level(42, new int[] {41}, new int[] {0});
    Levels.Entry maggot = level(63, new int[] {41}, new int[] {0});
    Levels.Entry sightOnly = level(44, new int[] {75}, new int[] {-1});

    List<Act2MapBuilderD2MOD.Act2WarpEdge> edges =
        Act2MapBuilderD2MOD.discoverAct2WarpEdges(Arrays.asList(rocky, dry, maggot, sightOnly));

    assertEquals(4, edges.size());
    assertTrue(edges.stream().anyMatch(e -> e.sourceLevelId == 41
        && e.destinationLevelId == 63 && e.mainIndex == 1));
    assertTrue(edges.stream().noneMatch(e -> e.sourceLevelId == 44));
  }

  @Test
  void topologyReportFlagsMissingGeneratedDungeonAndReverseRoute() {
    Levels.Entry rocky = level(41, new int[] {63}, new int[] {0});
    Levels.Entry maggot = level(63, new int[] {41}, new int[] {0});

    Act2MapBuilderD2MOD.TopologyReport report =
        Act2MapBuilderD2MOD.validateAct2Topology(Arrays.asList(rocky, maggot),
            new HashSet<>(Arrays.asList(41)));

    assertEquals(2, report.edgeCount);
    assertEquals(2, report.missingGenerated);
    assertEquals(0, report.missingReverse);
  }

  private static Levels.Entry level(int id, int[] vis, int[] warp) {
    Levels.Entry level = new Levels.Entry();
    level.Id = id;
    level.Vis = vis;
    level.Warp = warp;
    level.LevelName = "Act2-" + id;
    return level;
  }
}
