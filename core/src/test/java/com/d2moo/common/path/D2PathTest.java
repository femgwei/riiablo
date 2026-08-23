package com.d2moo.common.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class D2PathTest {
  @Test
  void matchesNativeAStarHeuristicSamples() {
    assertEquals(0, D2Path.aStarHeuristic(0, 0, 0, 0));
    assertEquals(2, D2Path.aStarHeuristic(0, 0, 1, 0));
    assertEquals(3, D2Path.aStarHeuristic(0, 0, 1, 1));
    assertEquals(8, D2Path.aStarHeuristic(0, 0, 3, 2));
    assertEquals(9, D2Path.aStarHeuristic(0, 0, 3, 3));
  }

  @Test
  void preservesNativeNeighborOrderAndCosts() {
    int[][] expected = {
        {-1, -1}, {-1, 1}, {1, -1}, {1, 1},
        {-1, 0}, {0, -1}, {1, 0}, {0, 1}
    };
    assertEquals(expected.length, D2Path.neighborCount());
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i][0], D2Path.neighborOffsetX(i));
      assertEquals(expected[i][1], D2Path.neighborOffsetY(i));
    }
    assertEquals(2, D2Path.neighborCost(2, 2, 3, 2));
    assertEquals(3, D2Path.neighborCost(2, 2, 3, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> D2Path.neighborOffsetX(8));
  }

  @Test
  void compressesStraightRunsLikeNativeFlush() {
    List<D2Path.Point> path = Arrays.asList(
        point(0, 0), point(1, 0), point(2, 0),
        point(2, 1), point(3, 1));

    assertEquals(Arrays.asList(point(2, 0), point(2, 1), point(3, 1)),
        D2Path.compressAStarPath(path));
  }

  private static D2Path.Point point(int x, int y) {
    return new D2Path.Point(x, y);
  }
}
