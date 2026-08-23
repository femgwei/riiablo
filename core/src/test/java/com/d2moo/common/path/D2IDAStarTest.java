package com.d2moo.common.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class D2IDAStarTest {
  @Test
  void findsStraightPathButPreservesNativeFlushRejection() {
    D2IDAStar.Result result = D2IDAStar.findPath(
        point(0, 0), point(5, 0), bounds(-10, -10, 10, 10), (x, y) -> false);

    assertTrue(result.reachedTarget());
    assertTrue(result.nativePathFound());
    assertTrue(result.path().isEmpty());
    assertEquals(point(5, 0), result.bestPoint());
  }

  @Test
  void routesAroundWallAndEmitsDirectionChanges() {
    Set<D2Path.Point> blocked = new HashSet<>();
    blocked.add(point(1, -1));
    blocked.add(point(1, 0));
    blocked.add(point(1, 1));

    D2IDAStar.Result result = D2IDAStar.findPath(
        point(0, 0), point(4, 0), bounds(-10, -10, 10, 10),
        (x, y) -> blocked.contains(point(x, y)), 100, 0);

    assertTrue(result.reachedTarget());
    assertTrue(result.path().size() >= 2);
    assertEquals(point(4, 0), result.path().get(result.path().size() - 1));
    for (D2Path.Point pathPoint : result.path()) {
      assertFalse(blocked.contains(pathPoint));
    }
  }

  @Test
  void raisesCutoffInNativeFivePointSteps() {
    Set<D2Path.Point> blocked = new HashSet<>();
    blocked.add(point(1, -1));
    blocked.add(point(1, 0));
    blocked.add(point(1, 1));

    int initial = D2Path.aStarHeuristic(0, 0, 4, 0);
    D2IDAStar.Result result = D2IDAStar.findPath(
        point(0, 0), point(4, 0), bounds(-10, -10, 10, 10),
        (x, y) -> blocked.contains(point(x, y)), 100, 0);

    assertTrue(result.reachedTarget());
    assertTrue(result.cutoffIterations() > 1);
    assertEquals(0, (result.finalCutoff() - initial) % D2IDAStar.F_SCORE_INCREMENT);
  }

  @Test
  void stopsAtInclusiveSearchBoundary() {
    D2IDAStar.Result result = D2IDAStar.findPath(
        point(0, 0), point(10, 0), bounds(0, 0, 2, 2),
        (x, y) -> y != 0, 100, 0);

    assertFalse(result.reachedTarget());
    assertTrue(result.nativePathFound());
    assertTrue(result.bestPoint().x > 2);
  }

  @Test
  void acceptsNativeTargetDistanceThreshold() {
    D2IDAStar.Result result = D2IDAStar.findPath(
        point(0, 0), point(5, 0), bounds(-10, -10, 10, 10),
        (x, y) -> false, 20, 3);

    assertTrue(result.nativePathFound());
    assertFalse(result.reachedTarget());
    assertEquals(point(4, 0), result.bestPoint());
  }

  @Test
  void stopsAtNativeNodeStorageLimit() {
    D2IDAStar.Result result = D2IDAStar.findPath(
        point(0, 0), point(2_000, 0), bounds(0, 0, 9_999, 4),
        (x, y) -> y != 0, 5_000, 0);

    assertFalse(result.nativePathFound());
    assertFalse(result.reachedTarget());
    assertEquals(D2IDAStar.MAX_NODES, result.allocatedNodes());
    assertEquals(D2IDAStar.MAX_NODES, result.maximumAllocatedNodes());
  }

  @Test
  void returnsEmptyWhenTargetIsUnreachable() {
    D2IDAStar.Result result = D2IDAStar.findPath(
        point(0, 0), point(3, 0), bounds(-5, -5, 5, 5),
        (x, y) -> x != 0 || y != 0, 50, 0);

    assertFalse(result.nativePathFound());
    assertFalse(result.reachedTarget());
    assertTrue(result.path().isEmpty());
  }

  @Test
  void validatesNativeRoomAreaAndStartBounds() {
    bounds(0, 0, 250, 200);
    assertThrows(IllegalArgumentException.class,
        () -> bounds(0, 0, 251, 200));
    assertThrows(IllegalArgumentException.class,
        () -> D2IDAStar.findPath(
            point(-1, 0), point(1, 0), bounds(0, 0, 5, 5), (x, y) -> false));
  }

  private static D2IDAStar.Bounds bounds(int minX, int minY, int maxX, int maxY) {
    return new D2IDAStar.Bounds(minX, minY, maxX, maxY);
  }

  private static D2Path.Point point(int x, int y) {
    return new D2Path.Point(x, y);
  }
}
