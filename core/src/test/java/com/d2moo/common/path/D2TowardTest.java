package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class D2TowardTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static D2Toward.Result toward(int distance, D2Toward.Collision collision) {
    return D2Toward.compute(p(10, 10), p(20, 10), distance, 1, 0, collision);
  }

  @Test void clearRayEmitsTheTargetEvenWithZeroLocalBudget() {
    for (int budget : new int[] {0, 1, 14, 77}) {
      D2Toward.Result result = toward(budget, (x, y) -> false);
      assertEquals(Collections.singletonList(p(20, 10)), result.points());
      assertTrue(result.reachedTarget());
      assertTrue(result.hasMovementPoints());
    }
  }

  @Test void clippedRayEmitsProgressWithoutExactArrivalOrRemainingBudget() {
    for (int budget : new int[] {0, 1, 14}) {
      D2Toward.Result result = toward(budget, (x, y) -> x >= 13);
      assertEquals(Collections.singletonList(p(12, 10)), result.points());
      assertTrue(result.hasMovementPoints());
      assertFalse(result.reachedTarget());
    }
  }

  @Test void nearBlockedRayCanEmitOnlyTheSourceWithoutUsableMovement() {
    D2Toward.Result result = D2Toward.compute(p(10, 10), p(13, 10), 14, 1, 0, (x, y) -> true);
    assertEquals(Collections.singletonList(p(10, 10)), result.points());
    assertFalse(result.hasMovementPoints());
    assertFalse(result.reachedTarget());
    assertTrue(D2Toward.compute(p(10, 10), p(13, 10), 14, 0, 0, (x, y) -> true).points().isEmpty());
  }

  @Test void rawCoincidentTargetIsSourceOnlyAndWrapperMustDecideWhetherToCallIt() {
    D2Toward.Result result = D2Toward.compute(p(10, 10), p(10, 10), 14, 1, 0,
        (x, y) -> { fail("coincident ray needs no collision query"); return false; });
    assertEquals(Collections.singletonList(p(10, 10)), result.points());
    assertFalse(result.hasMovementPoints());
    assertTrue(result.reachedTarget(), "coordinate equality is not movement readiness");
  }

  @Test void oneInitialLocalStepIsNotAutomaticallyAnEmittedPoint() {
    assertTrue(toward(0, (x, y) -> x == 11 && y == 10).points().isEmpty());
    assertTrue(toward(1, (x, y) -> x == 11 && y == 10).points().isEmpty());
    assertEquals(Collections.singletonList(p(11, 11)),
        toward(2, (x, y) -> x == 11 && y == 10).points());
  }

  @Test void aLaterBlockedStepAppendsTheLastCompletedLocalPoint() {
    D2Toward.Result result = toward(14, (x, y) -> x != 11 || y != 9);
    assertEquals(Collections.singletonList(p(11, 9)), result.points());
    assertTrue(result.hasMovementPoints());
    assertFalse(result.reachedTarget());
  }

  @Test void initialRayProgressIsNotDeduplicatedOnTheFirstLocalTurn() {
    D2Toward.Result result = toward(1, (x, y) -> x == 13 && y == 10);
    assertEquals(Arrays.asList(p(12, 10), p(12, 10)), result.points());
    assertTrue(result.hasMovementPoints());
    assertFalse(result.reachedTarget());
  }

  @Test void targetReachedOnANewDirectionCanBeOmittedByTheNativeExit() {
    for (int budget : new int[] {2, 3, 14}) {
      D2Toward.Result result = D2Toward.compute(p(10, 10), p(12, 11), budget, 0, 0,
          (x, y) -> x == 11 && y == 10);
      assertEquals(Collections.singletonList(p(11, 11)), result.points());
      assertFalse(result.reachedTarget(), "do not repair the native turn-ending quirk");
    }
  }

  @Test void sameDirectionRunDoesEmitItsLastPoint() {
    D2Toward.Result result = D2Toward.compute(p(10, 10), p(14, 11), 14, 0, 0,
        (x, y) -> x == 11 && y == 10);
    assertEquals(Arrays.asList(p(11, 11), p(14, 11)), result.points());
    assertTrue(result.reachedTarget());
  }

  @Test void nonzeroDirectionOffsetBypassesRayAndIncludesInitialSourceTurn() {
    for (int offset : new int[] {-2, 2}) {
      List<D2Path.Point> queries = new ArrayList<>();
      D2Toward.Result result = D2Toward.compute(p(10, 10), p(20, 10), 2, 255, offset,
          (x, y) -> { queries.add(p(x, y)); return false; });
      assertEquals(Arrays.asList(p(10, 10), p(10, 10 + offset)), result.points());
      assertEquals(Arrays.asList(p(10, 10 + offset / 2), p(10, 10 + offset)), queries);
      assertTrue(result.hasMovementPoints());
      assertFalse(result.reachedTarget());
    }
    assertEquals(Collections.singletonList(p(10, 10)),
        D2Toward.compute(p(10, 10), p(20, 10), 1, 1, 2, (x, y) -> false).points());
  }

  @Test void zeroBudgetWithOffsetDoesNotInvokeCollisionOrEmitASourcePoint() {
    assertTrue(D2Toward.compute(p(10, 10), p(20, 10), 0, 255, 2,
        (x, y) -> { fail("offset branch has no initial ray"); return false; }).points().isEmpty());
  }

  @Test void rayOrderAndIterationRollbackMatchTheNativeStaircase() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2Toward.RayResult ray = D2Toward.trace(p(10, 10), p(14, 12), (x, y) -> {
      queries.add(p(x, y)); return false;
    });
    assertTrue(ray.clear);
    assertEquals(p(14, 12), ray.endpoint);
    assertEquals(Arrays.asList(p(11, 10), p(11, 11), p(12, 11), p(13, 11), p(13, 12), p(14, 12)), queries);
    ray = D2Toward.trace(p(10, 10), p(14, 12), (x, y) -> x == 11 && y == 11);
    assertFalse(ray.clear);
    assertEquals(p(10, 10), ray.endpoint, "free major-axis cell is not a completed iteration");
  }

  @Test void firstFreeCandidateUsesNativePriorityAndDoesNotFloodFillEightDirections() {
    List<D2Path.Point> queries = new ArrayList<>();
    assertEquals(-1, D2Toward.firstFreeDirection(p(10, 10), p(20, 10), 0, (x, y) -> {
      queries.add(p(x, y)); return true;
    }));
    assertEquals(Arrays.asList(p(11, 10), p(11, 11), p(11, 9)), queries);
    assertEquals(7, D2Toward.firstFreeDirection(p(10, 10), p(20, 10), 0,
        (x, y) -> x != 11 || y != 9));
  }

  @Test void rotatedCoordinatesWrapLikeNativeUint16Fields() {
    D2Toward.Result result = D2Toward.compute(p(0, 10), p(10, 10), 2, 0, 4, (x, y) -> false);
    assertEquals(Arrays.asList(p(0, 10), p(65535, 10)), result.points());
    // After wrapping, the next candidate reverses direction and is rejected;
    // the last accepted point is appended instead of stepping back to the source.
    assertTrue(result.hasMovementPoints());
  }

  @Test void emittedPointsAreImmutableAndInvalidBudgetsAreNotSilentlyTruncated() {
    assertThrows(UnsupportedOperationException.class, () -> toward(14, (x, y) -> false).points().clear());
    for (int distance : new int[] {-1, 78, 256}) {
      assertThrows(IllegalArgumentException.class, () -> toward(distance, (x, y) -> false));
    }
    assertThrows(IllegalArgumentException.class, () -> D2Toward.compute(p(-1, 0), p(10, 10), 14, 1, 0, (x, y) -> false));
    assertThrows(IllegalArgumentException.class, () -> D2Toward.compute(p(10, 10), p(20, 10), 14, 256, 0, (x, y) -> false));
  }

  @Test void callerSuppliedNativePatternAndMaskRemainAuthoritative() {
    com.d2moo.common.drlg.D2ActiveRoom room = new com.d2moo.common.drlg.D2ActiveRoom();
    com.d2moo.common.drlg.D2DrlgCoords coords = new com.d2moo.common.drlg.D2DrlgCoords();
    coords.setNSubtileWidth(24);
    coords.setNSubtileHeight(24);
    room.setCoords(coords);
    com.d2moo.common.collision.D2CommonCollision.allocRoomCollisionGrid(room);
    com.d2moo.common.collision.D2CommonCollision.setMask(room, 13, 10,
        com.d2moo.common.collision.D2Collision.COLLIDE_WALL);
    D2Toward.Collision cross = (x, y) -> com.d2moo.common.collision.D2CommonCollision.checkMaskWithPattern(
        room, x, y, com.d2moo.common.collision.D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
        com.d2moo.common.collision.D2Collision.COLLIDE_WALL) != 0;
    assertEquals(p(11, 10), D2Toward.trace(p(10, 10), p(20, 10), cross).endpoint);
    D2Toward.Result clipped = toward(0, cross);
    assertEquals(Collections.singletonList(p(11, 10)), clipped.points());
    assertFalse(clipped.reachedTarget());
    D2Toward.Result ignoresWalls = toward(0, (x, y) ->
        com.d2moo.common.collision.D2CommonCollision.checkMaskWithPattern(room, x, y,
            com.d2moo.common.collision.D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
            com.d2moo.common.collision.D2Collision.COLLIDE_MISSILE_BARRIER) != 0);
    assertTrue(ignoresWalls.reachedTarget(), "kernel must not hard-code a terrain mask");
  }
}
