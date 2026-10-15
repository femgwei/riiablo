package com.riiablo.engine.server.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeTowardPathProbeTest {
  @Test
  void fullTowardPointsMatchTheExistingZeroOffsetBooleanProjection() {
    java.util.Random random = new java.util.Random(0x110f);
    int[] budgets = {0, 1, 2, 14, 77};
    for (int sample = 0; sample < 4000; sample++) {
      int tx = 100 + random.nextInt(41) - 20;
      int ty = 100 + random.nextInt(41) - 20;
      int budget = budgets[sample % budgets.length];
      int tolerance = sample % 4;
      int salt = random.nextInt();
      NativeTowardPathProbe.Blocked blocked = (x, y) ->
          Math.floorMod((x * 73856093) ^ (y * 19349663) ^ salt, 5) < sampleDensity(salt);
      com.d2moo.common.path.D2Toward.Result result = com.d2moo.common.path.D2Toward.compute(
          new com.d2moo.common.path.D2Path.Point(100, 100),
          new com.d2moo.common.path.D2Path.Point(tx, ty), budget, tolerance, 0, blocked::at);
      assertTrue(result.points().size() <= com.d2moo.common.path.D2Path.MAX_PATH_LENGTH);
      assertEquals(tx != 100 || ty != 100 ? !result.points().isEmpty() : false,
          NativeTowardPathProbe.hasPathPoints(100, 100, tx, ty, budget, tolerance, blocked),
          "raw projection sample=" + sample);
      assertEquals(result.hasMovementPoints(), NativeTowardPathProbe.hasPathPointsAfterNormalization(
          100, 100, tx, ty, budget, tolerance, blocked), "movement projection sample=" + sample);
      NativeTowardPathProbe.RayResult oldRay = NativeTowardPathProbe.trace(100, 100, tx, ty, blocked);
      com.d2moo.common.path.D2Toward.RayResult ray = com.d2moo.common.path.D2Toward.trace(
          new com.d2moo.common.path.D2Path.Point(100, 100),
          new com.d2moo.common.path.D2Path.Point(tx, ty), blocked::at);
      assertEquals(oldRay.x, ray.endpoint.x);
      assertEquals(oldRay.y, ray.endpoint.y);
      assertEquals(oldRay.clear, ray.clear);
    }
  }

  private static int sampleDensity(int salt) { return Math.floorMod(salt, 6); }

  @Test
  void wrapperClearsSourceOnlyPointAfterVelocityNormalization() {
    assertTrue(NativeTowardPathProbe.hasPathPoints(10, 10, 13, 10, 14, 1, (x, y) -> true));
    assertFalse(NativeTowardPathProbe.hasPathPointsAfterNormalization(
        10, 10, 13, 10, 14, 1, (x, y) -> true));
    assertTrue(NativeTowardPathProbe.hasPathPointsAfterNormalization(
        10, 10, 13, 10, 14, 1, (x, y) -> x >= 12));
    assertTrue(NativeTowardPathProbe.hasPathPointsAfterNormalization(
        10, 10, 20, 10, 14, 1, (x, y) -> x != 11 || y != 9));
  }

  @Test
  void rayUsesNativeStaircaseOrderAndClipsAtLastCompletedIteration() {
    List<String> tested = new ArrayList<>();
    NativeTowardPathProbe.RayResult ray = NativeTowardPathProbe.trace(10, 10, 14, 12, (x, y) -> {
      tested.add(x + "," + y);
      return false;
    });
    assertTrue(ray.clear);
    assertEquals(14, ray.x);
    assertEquals(12, ray.y);
    assertEquals(Arrays.asList("11,10", "11,11", "12,11", "13,11", "13,12", "14,12"), tested);
    // (11,10) was free, but blocking the minor-axis cell rolls back the
    // WHOLE iteration to the source, not to that individually free cell.
    ray = NativeTowardPathProbe.trace(10, 10, 14, 12, (x, y) -> x == 11 && y == 11);
    assertFalse(ray.clear);
    assertEquals(10, ray.x);
    assertEquals(10, ray.y);
  }

  @Test
  void rayOrderReflectsAcrossAllOctantsAndHandlesAxes() {
    for (int signX : new int[] {-1, 1}) {
      for (int signY : new int[] {-1, 1}) {
        for (boolean transpose : new boolean[] {false, true}) {
          List<String> tested = new ArrayList<>();
          int tx = 10 + signX * (transpose ? 2 : 4);
          int ty = 10 + signY * (transpose ? 4 : 2);
          NativeTowardPathProbe.RayResult ray = NativeTowardPathProbe.trace(10, 10, tx, ty, (x, y) -> {
            tested.add((signX * (x - 10)) + "," + (signY * (y - 10)));
            return false;
          });
          assertTrue(ray.clear);
          assertEquals(transpose
              ? Arrays.asList("0,1", "1,1", "1,2", "1,3", "2,3", "2,4")
              : Arrays.asList("1,0", "1,1", "2,1", "3,1", "3,2", "4,2"), tested);
        }
      }
    }
    for (int[] target : new int[][] {{14, 10}, {6, 10}, {10, 14}, {10, 6}, {14, 14}, {6, 6}}) {
      final int[] count = {0};
      assertTrue(NativeTowardPathProbe.trace(10, 10, target[0], target[1], (x, y) -> {
        count[0]++;
        assertTrue(x >= 6 && x <= 14 && y >= 6 && y <= 14);
        return false;
      }).clear);
      assertEquals(4, count[0]);
    }
  }

  @Test
  void nearDistanceUsesNativeTableAndEightCellBoundary() {
    assertEquals(0, NativeTowardPathProbe.heuristic(2, 0));
    assertEquals(1, NativeTowardPathProbe.heuristic(3, 0));
    assertEquals(2, NativeTowardPathProbe.heuristic(3, 1));
    assertEquals(6, NativeTowardPathProbe.heuristic(3, 5));
    assertEquals(6, NativeTowardPathProbe.heuristic(5, 3));
    assertEquals(9, NativeTowardPathProbe.heuristic(-7, 0));
    assertEquals(16, NativeTowardPathProbe.heuristic(8, 0));
    assertEquals(21, NativeTowardPathProbe.heuristic(5, -8));
  }

  @Test
  void clippedRayAlreadyProvidesPointsEvenWithoutRemainingBudget() {
    for (int budget : new int[] {0, 1, 14}) {
      assertTrue(NativeTowardPathProbe.hasPathPoints(10, 10, 20, 10, budget, 1,
          (x, y) -> x >= 13), "partial progress is not complete reachability");
      assertTrue(NativeTowardPathProbe.hasPathPoints(10, 10, 20, 10, budget, 1,
          (x, y) -> false), "clear ray is not limited by local detour budget");
    }
  }

  @Test
  void nearBlockedEndpointCanStillStoreSourcePointWithoutTakingAStep() {
    for (int[] target : new int[][] {{11, 10}, {12, 10}, {13, 10}, {11, 11}, {11, 12}}) {
      assertTrue(NativeTowardPathProbe.hasPathPoints(10, 10, target[0], target[1], 0, 1,
          (x, y) -> true));
    }
    assertFalse(NativeTowardPathProbe.hasPathPoints(10, 10, 14, 10, 14, 1, (x, y) -> true));
    assertFalse(NativeTowardPathProbe.hasPathPoints(10, 10, 13, 10, 0, 0, (x, y) -> true));
  }

  @Test
  void immediateRayBlockRequiresTwoLocalIterationsToProduceFirstPoint() {
    for (int budget : new int[] {0, 1, 2, 14}) {
      assertEquals(budget > 1, NativeTowardPathProbe.hasPathPoints(10, 10, 20, 10, budget, 1,
          (x, y) -> x != 11 || y != 9));
      assertFalse(NativeTowardPathProbe.hasPathPoints(10, 10, 20, 10, budget, 1,
          (x, y) -> true));
    }
    assertFalse(NativeTowardPathProbe.hasPathPoints(10, 10, 10, 10, 14, 1,
        (x, y) -> fail("coincident target does not query collision")));
  }

  @Test
  void cardinalAndDiagonalNativeDirectionPrioritiesAreNotEightWayFloodFill() {
    int[][][] cases = {
        {{20, 10}, {11, 10}, {11, 11}, {11, 9}},
        {{0, 10}, {9, 10}, {9, 11}, {9, 9}},
        {{10, 20}, {10, 11}, {11, 11}, {9, 11}},
        {{10, 0}, {10, 9}, {11, 9}, {9, 9}},
        {{20, 20}, {11, 11}, {11, 10}, {10, 11}},
        {{0, 0}, {9, 9}, {9, 10}, {10, 9}}
    };
    for (int[][] row : cases) {
      List<String> tested = new ArrayList<>();
      assertFalse(NativeTowardPathProbe.hasStep(10, 10, row[0][0], row[0][1], (x, y) -> {
        tested.add(x + "," + y); return true;
      }));
      assertEquals(Arrays.asList(row[1][0] + "," + row[1][1], row[2][0] + "," + row[2][1],
          row[3][0] + "," + row[3][1]), tested);
    }
  }

  @Test
  void eachOfThreeNativeInitialCandidatesCanProveNonzeroPoints() {
    for (int freeY : new int[] {10, 11, 9}) {
      assertTrue(NativeTowardPathProbe.hasStep(10, 10, 20, 10,
          (x, y) -> x != 11 || y != freeY));
    }
    assertFalse(NativeTowardPathProbe.hasStep(10, 10, 20, 10,
        (x, y) -> x != 9)); // West is free, but not a native eastward candidate.
  }

  @Test
  void coincidentTargetHasNoStepAndAllSlopesRemainWithinAdjacentCells() {
    assertFalse(NativeTowardPathProbe.hasStep(10, 10, 10, 10, (x, y) -> fail("no collision query")));
    for (int dx = -20; dx <= 20; dx++) {
      for (int dy = -20; dy <= 20; dy++) {
        final int[] count = {0};
        assertFalse(NativeTowardPathProbe.hasStep(10, 10, 10 + dx, 10 + dy, (x, y) -> {
          assertTrue(Math.abs(x - 10) <= 1 && Math.abs(y - 10) <= 1);
          assertFalse(x == 10 && y == 10);
          count[0]++;
          return true;
        }));
        assertEquals(dx == 0 && dy == 0 ? 0 : 3, count[0]);
      }
    }
  }
}
