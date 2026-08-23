package com.d2moo.common.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class D2AStarTest {
  @Test
  void findsAndCompressesStraightNativePath() {
    D2AStar.Result result = D2AStar.findPath(point(0, 0), point(5, 0), (x, y) -> false);

    assertTrue(result.reachedTarget());
    assertEquals(Collections.singletonList(point(5, 0)), result.path());
    assertEquals(point(5, 0), result.bestPoint());
    assertTrue(result.allocatedNodes() <= D2AStar.MAX_NODES);
  }

  @Test
  void routesAroundBlockedCells() {
    Set<D2Path.Point> blocked = new HashSet<>();
    blocked.add(point(1, -1));
    blocked.add(point(1, 0));
    blocked.add(point(1, 1));

    D2AStar.Result result = D2AStar.findPath(
        point(0, 0), point(3, 0), (x, y) -> blocked.contains(point(x, y)));

    assertTrue(result.reachedTarget());
    assertEquals(point(3, 0), result.path().get(result.path().size() - 1));
    for (D2Path.Point point : result.path()) {
      assertFalse(blocked.contains(point));
    }
  }

  @Test
  void appliesNativeTargetUnitApproachCheck() {
    D2Path.Point target = point(10, 10);
    D2AStar.Result result = D2AStar.findPath(
        point(0, 0), target,
        (x, y) -> Math.max(Math.abs(x - target.x), Math.abs(y - target.y)) == 2,
        true);

    assertFalse(result.reachedTarget());
    assertTrue(result.path().isEmpty());
    assertEquals(0, result.allocatedNodes());
  }

  @Test
  void returnsBestPartialPathAtNativeNodeLimit() {
    D2AStar.Result result = D2AStar.findPath(
        point(0, 0), point(10_000, 0), (x, y) -> false);

    assertFalse(result.reachedTarget());
    assertEquals(D2AStar.MAX_NODES, result.allocatedNodes());
    assertFalse(result.path().isEmpty());
    assertTrue(result.bestPoint().x > 0);
  }

  private static D2Path.Point point(int x, int y) {
    return new D2Path.Point(x, y);
  }
}
