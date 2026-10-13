package com.riiablo.map;

import com.riiablo.codec.excel.Levels;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapTourPlanTest {
  @Test void focusedDiagnosticsAreExplicitOrderedSubsetsAndRejectUnknownOrRepeatedMaps() {
    List<Integer> complete = Arrays.asList(1, 2, 3, 4);
    assertSame(complete, MapTourPlan.focusedLevels(complete, null));
    assertEquals(Arrays.asList(3, 1), MapTourPlan.focusedLevels(complete, "3,1"));
    assertThrows(IllegalArgumentException.class, () -> MapTourPlan.focusedLevels(complete, "5"));
    assertThrows(IllegalArgumentException.class, () -> MapTourPlan.focusedLevels(complete, "1,1"));
    assertThrows(IllegalArgumentException.class, () -> MapTourPlan.focusedLevels(complete, ""));
  }
  @Test void followsLinksInsteadOfRawRowOrder() {
    assertEquals(Arrays.asList(1, 3, 2), MapTourPlan.levels(Arrays.asList(
        row(2, 0), row(3, 0), row(1, 0, 3)), 1, 5));
  }
  @Test void disconnectedBranchesAreNotLostAndCyclesDoNotRepeat() {
    List<Integer> tour = MapTourPlan.levels(Arrays.asList(
        row(1, 0, 2), row(2, 0, 1), row(8, 0), row(40, 1)), 1, 5);
    assertEquals(Arrays.asList(1, 2, 8, 40), tour);
    assertEquals(tour.size(), new HashSet<>(tour).size());
  }
  @Test void usesTableActRatherThanHistoricalIncorrectArcaneOrdinal() {
    assertEquals(Collections.singletonList(75), MapTourPlan.levels(
        Arrays.asList(row(75, 1), row(76, 2), row(103, 3)), 2, 2));
  }
  @Test void excludesLevelZeroAndLaterVersionAreas() {
    assertEquals(Collections.singletonList(132), MapTourPlan.levels(
        Arrays.asList(row(0, 0), row(132, 4), row(133, 4)), 1, 5));
  }
  @Test void visitsActFiveSideAreasBeforeTheTerminalWorldstoneChain() {
    assertEquals(Arrays.asList(120, 121, 128, 129, 132), MapTourPlan.levels(Arrays.asList(
        row(120, 4, 128), row(128, 4, 129), row(129, 4, 132), row(132, 4), row(121, 4)), 5, 5));
  }
  @Test void visOnlyLinkDoesNotClaimWarpAndMissingArraysAreSafe() {
    Levels.Entry row = row(1, 0, 2, 3);
    row.Warp = new int[] {-1, 0};
    assertFalse(MapTourPlan.declaresWarp(row, 2));
    assertTrue(MapTourPlan.declaresWarp(row, 3));
    assertFalse(MapTourPlan.declaresWarp(new Levels.Entry(), 3));
  }
  @Test void allFiveActsCoverEvery110fLevelExactlyOnce() {
    java.util.ArrayList<Levels.Entry> rows = new java.util.ArrayList<>();
    for (int i = 132; i > 0; i--) {
      rows.add(row(i, i <= 39 ? 0 : i <= 74 ? 1 : i <= 102 ? 2 : i <= 108 ? 3 : 4));
    }
    List<Integer> tour = MapTourPlan.levels(rows, 1, 5);
    assertEquals(132, tour.size());
    assertEquals(132, new HashSet<>(tour).size());
    assertEquals(1, tour.get(0));
    assertEquals(132, tour.get(131));
    assertDoesNotThrow(() -> MapTourPlan.requireCoverage(tour, 1, 5));
    tour.remove(Integer.valueOf(114));
    assertThrows(IllegalStateException.class, () -> MapTourPlan.requireCoverage(tour, 1, 5));
    assertThrows(IllegalArgumentException.class, () -> MapTourPlan.levels(rows, 5, 1));
  }
  private static Levels.Entry row(int id, int act, int... vis) {
    Levels.Entry row = new Levels.Entry(); row.Id = id; row.Act = act; row.Vis = vis;
    return row;
  }
}
