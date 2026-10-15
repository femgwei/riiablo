package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class D2PathTargetTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static List<D2Path.Point> points(int... xy) {
    List<D2Path.Point> result = new ArrayList<>();
    for (int i = 0; i < xy.length; i += 2) result.add(p(xy[i], xy[i + 1]));
    return result;
  }
  private static D2PathTarget.Result prepare(D2Path.Point target, boolean player,
      D2PathTarget.Collision collision) {
    return D2PathTarget.prepare(p(10, 10), target, 0x1000, player, collision);
  }

  @Test void absentAdjustmentFlagDoesNotEvenQueryABlockedTarget() {
    D2PathTarget.Result result = D2PathTarget.prepare(p(10, 10), p(12, 10),
        0x800 | 0x10 | 0x20, true, (x, y) -> { fail("flag 0x1000 required"); return true; });
    assertTrue(result.canSearch);
    assertEquals(p(12, 10), result.target);
  }

  @Test void clearTargetIsNotMovedOrExtendedEvenForAPlayer() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(12, 10), true,
        (x, y) -> { queries.add(p(x, y)); return false; });
    assertEquals(points(12, 10), queries);
    assertEquals(p(12, 10), result.target);
    assertTrue(result.canSearch);
  }

  @Test void primaryFreeCandidateWinsWithoutTestingTheOtherTwo() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(16, 10), false,
        (x, y) -> { queries.add(p(x, y)); return x == 16 && y == 10; });
    assertEquals(points(16, 10, 15, 10), queries);
    assertEquals(p(15, 10), result.target);
    assertTrue(result.canSearch);
  }

  @Test void thirdCandidateIsCheckedInNativeOrderWhenAvailable() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(12, 10), false, (x, y) -> {
      queries.add(p(x, y)); return x != 12 || y != 9;
    });
    assertEquals(points(12, 10, 11, 10, 12, 11, 12, 9), queries);
    assertEquals(p(12, 9), result.target);
    assertTrue(result.canSearch);
  }

  @Test void unavailableThirdDirectionIsSkippedRatherThanUsedAsAnOffset() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(12, 11), false,
        (x, y) -> { queries.add(p(x, y)); return true; });
    assertEquals(points(12, 11, 11, 11, 12, 10, 10, 11, 12, 9, 10, 10, 12, 8), queries);
    assertFalse(result.canSearch);
    assertEquals(p(12, 11), result.target, "no selected point leaves the original target unchanged");
  }

  @Test void onlyThePrimaryDirectionIsRecomputedAndAllCandidatesAccumulate() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(14, 12), false, (x, y) -> {
      queries.add(p(x, y)); return x != 14 || y != 8;
    });
    assertEquals(points(14, 12, 13, 12, 14, 11, 12, 12, 14, 10,
        11, 12, 14, 9, 11, 11, 14, 8), queries);
    assertEquals(p(14, 8), result.target);
    assertTrue(result.canSearch);
  }

  @Test void selectingTheSourceWritesItButRejectsTheSearch() {
    D2PathTarget.Result result = prepare(p(12, 10), false,
        (x, y) -> x != 10 || y != 10);
    assertFalse(result.canSearch);
    assertEquals(p(10, 10), result.target);
  }

  @Test void selectedSecondaryNotPrimaryControlsTheNativePostSelectionSourceCheck() {
    // DLL 8C75 compares selected AX/CX. Upstream instead tests candidates[0],
    // which has just reached the source here although the selected point has not.
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(12, 10), false, (x, y) -> {
      queries.add(p(x, y)); return x != 12 || y != 12;
    });
    assertEquals(points(12, 10, 11, 10, 12, 11, 12, 9, 10, 10, 12, 12), queries);
    assertEquals(p(12, 12), result.target);
    assertTrue(result.canSearch, "must not reproduce the upstream primary-slot comparison");
  }

  @Test void blockedCoincidentInputFailsThisPhaseWithoutSteppingAway() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(10, 10), true,
        (x, y) -> { queries.add(p(x, y)); return true; });
    assertFalse(result.canSearch);
    assertEquals(p(10, 10), result.target);
    assertEquals(points(10, 10), queries);
    // The full native wrapper rejects coincidence earlier; this API is only its target phase.
  }

  @Test void playerUsesTheNativeTransposedSnapTableToExtendToFive() {
    List<D2Path.Point> queries = new ArrayList<>();
    Set<D2Path.Point> blocked = new HashSet<>(points(12, 10, 11, 10));
    D2PathTarget.Result result = prepare(p(12, 10), true, (x, y) -> {
      queries.add(p(x, y)); return blocked.contains(p(x, y));
    });
    assertEquals(points(12, 10, 11, 10, 12, 11, 13, 11, 14, 11, 15, 11), queries);
    assertEquals(p(15, 11), result.target);
    assertTrue(result.canSearch);
    assertEquals(p(12, 11), prepare(p(12, 10), false,
        (x, y) -> blocked.contains(p(x, y))).target, "monster does not use the player extension");
  }

  @Test void playerStopsAtTheLastAcceptedExtensionPoint() {
    Set<D2Path.Point> blocked = new HashSet<>(points(12, 10, 11, 10, 14, 11));
    assertEquals(p(13, 11), prepare(p(12, 10), true, (x, y) -> blocked.contains(p(x, y))).target);
    // A primary point extended directly toward the blocked original target stops immediately.
    assertEquals(p(13, 10), prepare(p(14, 10), true, (x, y) -> x == 14 && y == 10).target);
  }

  @Test void snapTableCanExtendDiagonallyNotOnlyAlongCardinalAxes() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = prepare(p(13, 12), true, (x, y) -> {
      queries.add(p(x, y)); return x == 13 && y == 12;
    });
    assertEquals(points(13, 12, 12, 12, 13, 13, 14, 14, 15, 15), queries);
    assertEquals(p(15, 15), result.target);
  }

  @Test void distanceFiveIsNotExtendedAndTheWindowIncludesExactlyOneHundred() {
    List<D2Path.Point> queries = new ArrayList<>();
    assertEquals(p(15, 10), prepare(p(16, 10), true, (x, y) -> {
      queries.add(p(x, y)); return x == 16 && y == 10;
    }).target);
    assertEquals(points(16, 10, 15, 10), queries);
    assertEquals(p(109, 10), prepare(p(110, 10), true, (x, y) -> x == 110 && y == 10).target);
  }

  @Test void playerExtensionQueriesRemainInt32WhileAcceptedCoordinatesNarrowToUint16() {
    for (int edge : new int[] {0, 65535}) {
      D2Path.Point start = p(edge == 0 ? 1 : 65534, 10), target = p(edge, 12);
      List<D2Path.Point> queries = new ArrayList<>();
      D2PathTarget.Result result = D2PathTarget.prepare(start, target, 0x1000, true, (x, y) -> {
        queries.add(p(x, y)); return x == edge && y == 12;
      });
      int sign = edge == 0 ? -1 : 1;
      assertEquals(points(edge, 12, edge, 11, edge + sign, 12, edge + 2 * sign, 13,
          edge + 3 * sign, 14, edge + 4 * sign, 15), queries);
      assertEquals(p((edge + 4 * sign) & 0xffff, 15), result.target);
      assertTrue(result.canSearch);
    }
  }

  @Test void candidateTrajectoriesUnlikePlayerExtensionDoWrapBeforeQueryingCollision() {
    List<D2Path.Point> queries = new ArrayList<>();
    D2PathTarget.Result result = D2PathTarget.prepare(p(0, 10), p(0, 12), 0x1000, false, (x, y) -> {
      queries.add(p(x, y)); return x != 65535 || y != 12;
    });
    // Initial north, east, west candidates; north blocked, east blocked, west wraps.
    assertEquals(points(0, 12, 0, 11, 1, 12, 65535, 12), queries);
    assertEquals(p(65535, 12), result.target);
    assertTrue(result.canSearch);
  }

  @Test void doorApproachUsesOrientationAndTheSourceSideIncludingEqualityAndWrap() {
    assertEquals(p(18, 20), D2PathTarget.doorTarget(p(10, 10), p(20, 20), false));
    assertEquals(p(22, 20), D2PathTarget.doorTarget(p(20, 10), p(20, 20), false));
    assertEquals(p(22, 20), D2PathTarget.doorTarget(p(30, 10), p(20, 20), false));
    assertEquals(p(20, 18), D2PathTarget.doorTarget(p(10, 10), p(20, 20), true));
    assertEquals(p(20, 22), D2PathTarget.doorTarget(p(10, 20), p(20, 20), true));
    assertEquals(p(20, 22), D2PathTarget.doorTarget(p(10, 30), p(20, 20), true));
    assertEquals(p(65535, 20), D2PathTarget.doorTarget(p(0, 10), p(1, 20), false));
    assertEquals(p(1, 20), D2PathTarget.doorTarget(p(65535, 10), p(65535, 20), false));
    assertEquals(p(20, 65535), D2PathTarget.doorTarget(p(10, 0), p(20, 1), true));
  }

  @Test void preparedSearchTargetCanFeedBothReferenceKernelsWithoutOverwritingTheRequest() {
    D2Path.Point requested = p(16, 10);
    D2PathTarget.Collision field = (x, y) -> x == 16 && y == 10;
    D2PathTarget.Result prepared = prepare(requested, false, field);
    assertEquals(p(15, 10), prepared.target);
    D2Toward.Result toward = D2Toward.compute(p(10, 10), prepared.target, 14, 1, 0, field::blocked);
    D2WallFollow.Result wall = D2WallFollow.compute(p(10, 10), prepared.target, 14, false, field::blocked);
    assertEquals(points(15, 10), toward.points());
    assertEquals(points(15, 10), wall.points());
    assertTrue(toward.reachedTarget());
    assertTrue(wall.reachedTarget());
    assertEquals(p(16, 10), requested, "request, prepared final and emitted endpoint are different roles");
    assertNotEquals(requested, toward.points().get(0));
  }

  @Test void nativePatternAndMaskAreSuppliedByTheCallerNotHardCoded() {
    com.d2moo.common.drlg.D2ActiveRoom room = new com.d2moo.common.drlg.D2ActiveRoom();
    com.d2moo.common.drlg.D2DrlgCoords coords = new com.d2moo.common.drlg.D2DrlgCoords();
    coords.setNSubtileWidth(30);
    coords.setNSubtileHeight(30);
    room.setCoords(coords);
    com.d2moo.common.collision.D2CommonCollision.allocRoomCollisionGrid(room);
    com.d2moo.common.collision.D2CommonCollision.setMask(room, 16, 10,
        com.d2moo.common.collision.D2Collision.COLLIDE_WALL);
    D2PathTarget.Collision cross = (x, y) -> com.d2moo.common.collision.D2CommonCollision.checkMaskWithPattern(
        room, x, y, com.d2moo.common.collision.D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
        com.d2moo.common.collision.D2Collision.COLLIDE_WALL) != 0;
    assertEquals(p(14, 10), prepare(p(16, 10), false, cross).target);
    assertEquals(p(16, 10), prepare(p(16, 10), false,
        (x, y) -> com.d2moo.common.collision.D2CommonCollision.checkMaskWithPattern(room, x, y,
            com.d2moo.common.collision.D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
            com.d2moo.common.collision.D2Collision.COLLIDE_MISSILE_BARRIER) != 0).target);
  }

  @Test void deterministicFieldsTerminateWithinTheNativeManhattanBoundAndReplay() {
    Random random = new Random(0x110f11);
    for (int trial = 0; trial < 5000; trial++) {
      int base = trial % 3 == 0 ? 0 : trial % 3 == 1 ? 32720 : 65435;
      D2Path.Point start = p(base + random.nextInt(101), base + random.nextInt(101));
      D2Path.Point target = p(base + random.nextInt(101), base + random.nextInt(101));
      int salt = random.nextInt(), flags = trial % 2 == 0 ? 0x1000 : 0;
      boolean player = trial % 3 == 0;
      int[] queryCount = {0};
      int bound = 1 + 3 * (Math.abs(start.x - target.x) + Math.abs(start.y - target.y)) + 4;
      D2PathTarget.Collision field = (x, y) -> {
        assertTrue(++queryCount[0] <= bound, "primary candidate must move toward the source");
        int hash = x * 0x45d9f3b ^ Integer.rotateLeft(y * 0x119de1f3, 13) ^ salt;
        return trialBlocked(hash);
      };
      D2PathTarget.Result result = D2PathTarget.prepare(start, target, flags, player, field);
      int firstCount = queryCount[0];
      queryCount[0] = 0;
      D2PathTarget.Result replay = D2PathTarget.prepare(start, target, flags, player, field);
      assertEquals(firstCount, queryCount[0]);
      assertEquals(result.target, replay.target);
      assertEquals(result.canSearch, replay.canSearch);
      assertTrue(result.target.x >= 0 && result.target.x <= 65535
          && result.target.y >= 0 && result.target.y <= 65535);
    }
  }

  private static boolean trialBlocked(int hash) {
    hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
    return ((hash ^ (hash >>> 16)) & 7) < 6;
  }

  @Test void invalidInputsAreRejectedInsteadOfSilentlyWrappingThePublicRequest() {
    for (D2Path.Point bad : Arrays.asList(null, p(-1, 0), p(65536, 0))) {
      assertThrows(IllegalArgumentException.class,
          () -> D2PathTarget.prepare(bad, p(10, 10), 0, false, (x, y) -> false));
      assertThrows(IllegalArgumentException.class,
          () -> D2PathTarget.prepare(p(10, 10), bad, 0, false, (x, y) -> false));
      assertThrows(IllegalArgumentException.class, () -> D2PathTarget.doorTarget(bad, p(10, 10), false));
      assertThrows(IllegalArgumentException.class, () -> D2PathTarget.doorTarget(p(10, 10), bad, true));
    }
    assertThrows(IllegalArgumentException.class, () -> prepare(p(111, 10), false, (x, y) -> false));
    assertThrows(IllegalArgumentException.class, () -> prepare(p(10, 111), false, (x, y) -> false));
    assertThrows(IllegalArgumentException.class, () -> prepare(p(10, 10), false, null));
    assertThrows(IllegalArgumentException.class,
        () -> D2PathTarget.prepare(p(10, 10), p(12, 10), 0x41000, false, (x, y) -> false));
    assertThrows(IllegalArgumentException.class,
        () -> D2PathTarget.prepare(p(111, 111), p(10, 10), 0, false, (x, y) -> false));
  }
}
