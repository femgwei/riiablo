package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class D2WallFollowTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static List<D2Path.Point> points(int... coordinates) {
    List<D2Path.Point> points = new ArrayList<>();
    for (int i = 0; i < coordinates.length; i += 2) points.add(p(coordinates[i], coordinates[i + 1]));
    return points;
  }
  private static D2WallFollow.Result wall(int distance, D2WallFollow.Collision collision) {
    return D2WallFollow.compute(p(10, 10), p(16, 10), distance, false, collision);
  }

  @Test void zeroOneAndTwoStepLinesReturnNothingWithoutQueryingCollision() {
    for (int steps = 0; steps <= 2; steps++) {
      D2WallFollow.Result result = D2WallFollow.compute(p(10, 10), p(10 + steps, 10),
          77, true, (x, y) -> { fail("native <=2 early exit"); return false; });
      assertTrue(result.rawPoints().isEmpty());
      assertTrue(result.points().isEmpty());
      assertFalse(result.reachedTarget());
      assertEquals(0, result.detours);
    }
  }

  @Test void lineMustFitDistanceMinusOneAndIsNotClippedToBudget() {
    for (int budget = 0; budget <= 6; budget++) {
      assertTrue(wall(budget, (x, y) -> { fail("no fitted line"); return false; }).points().isEmpty());
    }
    D2WallFollow.Result result = wall(7, (x, y) -> false);
    assertEquals(points(11, 10, 12, 10, 13, 10, 14, 10, 15, 10, 16, 10), result.rawPoints());
    assertEquals(points(16, 10), result.points());
    assertTrue(result.reachedTarget());
  }

  @Test void targetUnitRaisesOnlySmallBudgetsToForty() {
    D2Path.Point start = p(10, 10), target = p(49, 10);
    assertTrue(D2WallFollow.compute(start, target, 0, true, (x, y) -> false).reachedTarget());
    assertTrue(D2WallFollow.compute(start, target, 39, true, (x, y) -> false).reachedTarget());
    assertTrue(D2WallFollow.compute(start, target, 39, false, (x, y) -> false).points().isEmpty());
    assertTrue(D2WallFollow.compute(start, p(50, 10), 0, true, (x, y) -> false).points().isEmpty());
    assertTrue(D2WallFollow.compute(start, p(50, 10), 41, true, (x, y) -> false).reachedTarget());
  }

  @Test void clearLinesPreserveAllFourMajorDirectionsAndDiagonals() {
    for (int[] offset : new int[][] {{6, 0}, {0, 6}, {-6, 0}, {0, -6},
        {6, 6}, {-6, 6}, {-6, -6}, {6, -6}}) {
      D2Path.Point target = p(10 + offset[0], 10 + offset[1]);
      List<D2Path.Point> queries = new ArrayList<>();
      D2WallFollow.Result result = D2WallFollow.compute(p(10, 10), target, 7, false,
          (x, y) -> { queries.add(p(x, y)); return false; });
      assertEquals(6, result.rawPoints().size());
      assertEquals(result.rawPoints(), queries);
      assertEquals(Collections.singletonList(target), result.points());
      assertEquals(0, result.detours);
    }
  }

  @Test void singleObstacleRejoinsAndPreservesTheNativeSkippedQuery() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2WallFollow.Result result = wall(20, (x, y) -> {
      queries.add(p(x, y)); return x == 12 && y == 10;
    });
    assertEquals(points(11, 10, 12, 11, 13, 11, 13, 10, 14, 10, 15, 10, 16, 10), result.rawPoints());
    assertEquals(points(11, 10, 12, 11, 13, 10, 16, 10), result.points());
    assertEquals(points(11, 10, 12, 10, 12, 11, 12, 9, 12, 10,
        13, 11, 13, 9, 13, 10, 15, 10, 16, 10), queries);
    assertTrue(result.reachedTarget());
    assertEquals(1, result.detours);
  }

  @Test void rejoinedNextPointIsNotRecheckedEvenWhenItIsBlocked() {
    // The original main loop advances the rewritten index once more after a rejoin.
    D2WallFollow.Result result = wall(20, (x, y) -> y == 10 && (x == 12 || x == 14));
    assertTrue(result.rawPoints().contains(p(14, 10)));
    assertTrue(result.reachedTarget());
    assertEquals(1, result.detours, "do not silently fix the native query skip in this reference kernel");
  }

  @Test void rejoinReplacementUsesTheCorrectMajorAxisInAllFourDirections() {
    List<D2Path.Point> east = points(11, 10, 12, 11, 13, 11, 13, 10, 14, 10, 15, 10, 16, 10);
    for (int rotation = 0; rotation < 4; rotation++) {
      List<D2Path.Point> expected = new ArrayList<>();
      for (D2Path.Point point : east) expected.add(rotate(point, rotation));
      D2Path.Point obstacle = rotate(p(12, 10), rotation);
      D2WallFollow.Result result = D2WallFollow.compute(p(10, 10), rotate(p(16, 10), rotation),
          20, false, (x, y) -> obstacle.equals(p(x, y)));
      assertEquals(expected, result.rawPoints());
      assertTrue(result.reachedTarget());
      assertEquals(1, result.detours);
    }
  }

  private static D2Path.Point rotate(D2Path.Point point, int turns) {
    int x = point.x - 10, y = point.y - 10;
    for (int i = 0; i < turns; i++) {
      int oldX = x;
      x = -y;
      y = oldX;
    }
    return p(x + 10, y + 10);
  }

  @Test void secondSideCanRejoinBeforeTheFirstSide() {
    D2WallFollow.Result result = wall(20, (x, y) -> x == 12 && (y == 10 || y == 11));
    assertEquals(points(11, 10, 12, 9, 13, 9, 13, 10, 14, 10, 15, 10, 16, 10), result.rawPoints());
    assertTrue(result.reachedTarget());
    assertEquals(1, result.detours);
  }

  @Test void rejoinCanContinueIntoAnotherObstacleWithoutLosingTheOriginalTail() {
    D2WallFollow.Result result = D2WallFollow.compute(p(10, 10), p(20, 10),
        40, false, (x, y) -> y == 10 && (x == 12 || x == 16));
    assertEquals(points(11, 10, 12, 11, 13, 11, 13, 10, 14, 10, 15, 10,
        16, 11, 17, 11, 17, 10, 18, 10, 19, 10, 20, 10), result.rawPoints());
    assertTrue(result.reachedTarget());
    assertEquals(2, result.detours);
  }

  @Test void exhaustedBudgetCanCopyOneSideButReturnTheOtherSidesZeroCount() {
    // length=6, index=1, maxLength=6 => threshold=-1. Cache1 stops after one point,
    // then swaps to empty cache2. Best is cache1, but the helper returns cache2's 0.
    D2WallFollow.Result result = wall(7, (x, y) -> x == 12 && y == 10);
    assertEquals(points(11, 10, 12, 11), result.rawPoints());
    assertEquals(result.rawPoints(), result.points());
    assertFalse(result.reachedTarget());
  }

  @Test void emptyCacheUsesTheNativePackedFlagAndInt32DistanceNotIntMaxOrLong() {
    // Empty unfinished cache2 reads (0,0). (50006^2 + 10^2) wraps negative,
    // so the original signed compare selects the empty cache over (50002,11).
    D2WallFollow.Result result = D2WallFollow.compute(p(50000, 10), p(50006, 10),
        7, false, (x, y) -> x == 50002 && y == 10);
    assertEquals(points(50001, 10), result.rawPoints());
    assertFalse(result.reachedTarget());
  }

  @Test void equalDistanceFallbackChoosesTheSecondSide() {
    Set<D2Path.Point> blocked = new HashSet<>(points(12, 10, 13, 11, 12, 12, 11, 11));
    D2WallFollow.Result result = wall(20, (x, y) -> blocked.contains(p(x, y)));
    // Each cache has one point, (12,11)/(12,9), before cache1 hits its dead end.
    assertEquals(points(11, 10, 12, 9), result.rawPoints());
    assertFalse(result.reachedTarget());
    assertEquals(1, result.detours);
  }

  @Test void fourthCandidateMaySucceedDespiteTheNativeDeadEndDebugFlag() {
    List<D2Path.Point> queries = new ArrayList<>();
    Set<D2Path.Point> blocked = new HashSet<>(points(11, 10, 11, 11, 10, 11, 9, 10));
    D2WallFollow.Result result = D2WallFollow.compute(p(10, 10), p(16, 8), 7, false,
        (x, y) -> { queries.add(p(x, y)); return blocked.contains(p(x, y)); });
    assertEquals(points(11, 10, 11, 11, 10, 11, 9, 10, 10, 9), queries);
    assertEquals(points(10, 9), result.points());
  }

  @Test void allFourCandidatesBlockedStopsBeforeTryingTheSecondSide() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2WallFollow.Result result = wall(77, (x, y) -> { queries.add(p(x, y)); return true; });
    assertEquals(points(11, 10, 11, 11, 10, 11, 9, 10, 10, 9), queries);
    assertTrue(result.rawPoints().isEmpty());
    assertEquals(1, result.detours);
  }

  @Test void aDetourFartherThanTheGlobalStartReturnsOnlyTheOriginalPrefix() {
    D2WallFollow.Result result = wall(7, (x, y) -> !(x == 10 && y == 11));
    assertTrue(result.points().isEmpty(), "(10,11) is farther from (16,10) than (10,10)");
  }

  @Test void enclosedWalkersStopWhenTheyStartReversingAlongTheOtherPath() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2WallFollow.Result result = wall(77, (x, y) -> {
      queries.add(p(x, y));
      return x <= 9 || x >= 12 || y <= 8 || y >= 12;
    });
    assertEquals(points(11, 10), result.rawPoints());
    assertFalse(result.reachedTarget());
    assertTrue(queries.size() < 50, "loop detection, not exhaustion of the 77-point budget");
  }

  @Test void rotatedStepsUseUint16CoordinatesIncludingZeroX() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2WallFollow.Result result = D2WallFollow.compute(p(0, 10), p(0, 16), 7, false,
        (x, y) -> { queries.add(p(x, y)); return x == 0 && y == 11; });
    // South obstacle => cache1's initial SW step wraps X to 65535.
    assertEquals(points(0, 11, 65535, 11), queries);
    assertEquals(points(65535, 11), result.rawPoints());
    assertFalse(result.reachedTarget());
    // X=0 is not scanned as a terminator: native code uses the explicit point count.
    assertTrue(D2WallFollow.compute(p(0, 10), p(0, 16), 7, false, (x, y) -> false).reachedTarget());
  }

  @Test void stableCollisionFieldsTerminateAndRemainBoundedForDeterministicInputs() {
    Random random = new Random(0x110f15);
    for (int trial = 0; trial < 5000; trial++) {
      int base = trial % 3 == 0 ? 0 : trial % 3 == 1 ? 32760 : 65520;
      D2Path.Point start = p(base + random.nextInt(12), base + random.nextInt(12));
      D2Path.Point target = p(base + random.nextInt(12), base + random.nextInt(12));
      int salt = random.nextInt();
      int budget = random.nextInt(78);
      int[] queries = {0};
      D2WallFollow.Collision collision = (x, y) -> {
        assertTrue(++queries[0] < 10000, "nonterminating stable field");
        int hash = (x * 0x45d9f3b) ^ Integer.rotateLeft(y * 0x119de1f3, 13) ^ salt;
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
        return ((hash ^ (hash >>> 16)) & 7) < 3;
      };
      D2WallFollow.Result result = D2WallFollow.compute(start, target, budget, trial % 2 == 0, collision);
      assertTrue(result.rawPoints().size() < D2Path.MAX_PATH_LENGTH);
      assertTrue(result.points().size() <= result.rawPoints().size());
      for (D2Path.Point point : result.rawPoints()) {
        assertTrue(point.x >= 0 && point.x <= 65535 && point.y >= 0 && point.y <= 65535);
      }
      D2WallFollow.Result replay = D2WallFollow.compute(start, target, budget, trial % 2 == 0, collision);
      assertEquals(result.rawPoints(), replay.rawPoints());
      assertEquals(result.points(), replay.points());
    }
  }

  @Test void callerControlsTheNativePatternAndMask() {
    com.d2moo.common.drlg.D2ActiveRoom room = new com.d2moo.common.drlg.D2ActiveRoom();
    com.d2moo.common.drlg.D2DrlgCoords coords = new com.d2moo.common.drlg.D2DrlgCoords();
    coords.setNSubtileWidth(30);
    coords.setNSubtileHeight(30);
    room.setCoords(coords);
    com.d2moo.common.collision.D2CommonCollision.allocRoomCollisionGrid(room);
    com.d2moo.common.collision.D2CommonCollision.setMask(room, 12, 10,
        com.d2moo.common.collision.D2Collision.COLLIDE_WALL);
    D2WallFollow.Collision cross = (x, y) -> com.d2moo.common.collision.D2CommonCollision.checkMaskWithPattern(
        room, x, y, com.d2moo.common.collision.D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
        com.d2moo.common.collision.D2Collision.COLLIDE_WALL) != 0;
    D2WallFollow.Result result = wall(20, cross);
    assertTrue(result.detours > 0);
    assertTrue(cross.blocked(11, 10), "pattern, not just the single wall cell");
    assertNotEquals(p(11, 10), result.rawPoints().get(0));
    assertTrue(wall(20, (x, y) -> com.d2moo.common.collision.D2CommonCollision.checkMaskWithPattern(
        room, x, y, com.d2moo.common.collision.D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
        com.d2moo.common.collision.D2Collision.COLLIDE_MISSILE_BARRIER) != 0).reachedTarget());
  }

  @Test void outputsAreImmutableAndInvalidInputsAreRejected() {
    D2WallFollow.Result result = wall(20, (x, y) -> false);
    assertThrows(UnsupportedOperationException.class, () -> result.points().clear());
    assertThrows(UnsupportedOperationException.class, () -> result.rawPoints().clear());
    for (int budget : new int[] {-1, 78, 256}) {
      assertThrows(IllegalArgumentException.class, () -> wall(budget, (x, y) -> false));
    }
    for (D2Path.Point bad : Arrays.asList(null, p(-1, 0), p(0, 65536))) {
      assertThrows(IllegalArgumentException.class,
          () -> D2WallFollow.compute(bad, p(10, 10), 20, false, (x, y) -> false));
      assertThrows(IllegalArgumentException.class,
          () -> D2WallFollow.compute(p(10, 10), bad, 20, false, (x, y) -> false));
    }
    assertThrows(IllegalArgumentException.class,
        () -> D2WallFollow.compute(p(10, 10), p(16, 10), 20, false, null));
  }
}
