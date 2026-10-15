package com.d2moo.common.path;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class D2PathResultTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static D2PathResult.State state(int ownerType) {
    D2PathContinuation.State path = new D2PathContinuation.State(ownerType);
    path.currentPoint = p(10, 10);
    path.targetPoint = p(30, 30);
    path.previousTargetPoint = p(29, 29);
    path.finalTargetPoint = p(40, 40);
    path.pathType = 2;
    path.pathFlags = 0x1000 | 1 | 0x20;
    path.setNewDistance(77);
    path.setDistance(12);
    D2PathResult.State state = new D2PathResult.State(path);
    state.velocity = 256;
    state.pendingUpdate = 123;
    state.room = new D2PathResult.Room(0, 0, 100, 100);
    return state;
  }

  @Test void beginWritesFinalAndResetsOnlyIndexAndCountNotTargetsFlagsOrBuffer() {
    D2PathResult.State s = state(0);
    s.storePoints(Arrays.asList(p(15, 10), p(20, 10)));
    s.path.currentPointIndex = 1;
    D2PathResult.beginSearch(s, p(25, 10));
    assertEquals(0, s.path.currentPointIndex);
    assertEquals(0, s.path.pathPointCount);
    assertEquals(p(25, 10), s.path.finalTargetPoint);
    assertEquals(p(30, 30), s.path.targetPoint);
    assertEquals(p(29, 29), s.path.previousTargetPoint);
    assertEquals(0x1021, s.path.pathFlags);
    assertEquals(p(20, 10), s.points[1]);
    assertEquals(123, s.pendingUpdate);
  }

  @Test void shorterStoredResultRetainsStaleTailAndDoesNotResetIndex() {
    D2PathResult.State s = state(0);
    s.storePoints(Arrays.asList(p(15, 10), p(20, 10)));
    s.path.currentPointIndex = 1;
    s.storePoints(Collections.singletonList(p(12, 10)));
    assertEquals(1, s.path.pathPointCount);
    assertEquals(1, s.path.currentPointIndex);
    assertEquals(p(12, 10), s.points[0]);
    assertEquals(p(20, 10), s.points[1]);
  }

  @Test void zeroSearchResultClearsOnlyActiveNotIndexVectorsVelocityOrSnapshots() {
    D2PathResult.State s = state(0);
    s.path.currentPointIndex = 2;
    s.directionVectorX = 77;
    s.velocityVectorY = 88;
    assertEquals(0, D2PathResult.finishSearch(s));
    assertEquals(2, s.path.currentPointIndex, "zero-result branch does not run common index cleanup");
    assertEquals(0x1001, s.path.pathFlags);
    assertEquals(256, s.velocity);
    assertEquals(77, s.directionVectorX);
    assertEquals(88, s.velocityVectorY);
    assertEquals(123, s.pendingUpdate);
    assertEquals(p(29, 29), s.path.previousTargetPoint);
  }

  @Test void sourceOnlyAndRepeatedSourceResultsExhaustWithoutSuccessfulSnapshotUpdates() {
    for (int count : new int[] {1, 3, 78}) {
      D2PathResult.State s = state(0);
      s.storePoints(Collections.nCopies(count, p(10, 10)));
      s.directionVectorX = 1;
      s.directionVectorY = 2;
      s.velocityVectorX = 3;
      s.velocityVectorY = 4;
      s.direction = 13;
      s.newDirection = 14;
      assertEquals(0, D2PathResult.finishSearch(s));
      assertEquals(0, s.path.currentPointIndex);
      assertEquals(0, s.path.pathPointCount);
      assertEquals(0, s.velocity);
      assertEquals(0, s.directionVectorX | s.directionVectorY | s.velocityVectorX | s.velocityVectorY);
      assertEquals(13, s.direction);
      assertEquals(14, s.newDirection);
      assertEquals(123, s.pendingUpdate);
      assertEquals(p(29, 29), s.path.previousTargetPoint);
      assertEquals(p(30, 30), s.path.targetPoint);
      assertEquals(0x1001, s.path.pathFlags, "room flag is untouched on normalization failure");
    }
  }

  @Test void normalizationSkipsOnlyCenterExactLeadingPointsAndReturnsTotalNotRemainingCount() {
    D2PathResult.State s = state(0);
    s.storePoints(Arrays.asList(p(10, 10), p(10, 10), p(20, 10)));
    assertEquals(3, D2PathResult.finishSearch(s));
    assertEquals(2, s.path.currentPointIndex);
    assertEquals(3, s.path.pathPointCount);
    assertEquals(4096, s.directionVectorX);
    assertEquals(0, s.directionVectorY);
    assertEquals(4096, s.velocityVectorX);
    assertEquals(0, s.velocityVectorY);
    assertEquals(56, s.newDirection);
    assertEquals(0, s.direction, "player facing is scheduled, not immediately replaced");
    assertEquals(255, s.diffDirection);
  }

  @Test void sameIntegerTileButDifferentPrecisionIsNotDiscardedAsDuplicate() {
    D2PathResult.State s = state(0);
    s.precisionX++;
    s.storePoints(Arrays.asList(p(10, 10), p(20, 10)));
    assertEquals(2, D2PathResult.finishSearch(s));
    assertEquals(0, s.path.currentPointIndex);
    assertEquals(-4096, s.directionVectorX);
    assertEquals(0, s.directionVectorY);
  }

  @Test void zeroVelocityWithANonDuplicatePointStillReturnsCountButDoesNotGuaranteeMovement() {
    D2PathResult.State s = state(0);
    s.velocity = 0;
    s.storePoints(Collections.singletonList(p(20, 10)));
    assertEquals(1, D2PathResult.finishSearch(s));
    assertEquals(4096, s.directionVectorX);
    assertEquals(0, s.velocityVectorX);
    assertEquals(0, s.velocity);
    assertTrue(s.path.hasRemainingPoints());
    assertNotEquals(0, s.path.pathFlags & 0x20);
  }

  @Test void velocityUsesInt32MultiplicationWrapThenArithmeticShift() {
    D2PathResult.State s = state(0);
    s.velocity = Integer.MAX_VALUE;
    s.storePoints(Collections.singletonList(p(20, 10)));
    D2PathResult.normalizeVectors(s, true, true);
    assertEquals(-16, s.velocityVectorX, "do not widen vector*velocity to long");
    assertEquals(Integer.MAX_VALUE, s.velocity);
  }

  @Test void reverseFacingFlagChangesFacingNotDirectionOrVelocityVectors() {
    D2PathResult.State normal = state(0), reverse = state(0);
    reverse.path.pathFlags |= 0x200;
    normal.storePoints(Collections.singletonList(p(20, 10)));
    reverse.storePoints(Collections.singletonList(p(20, 10)));
    D2PathResult.finishSearch(normal);
    D2PathResult.finishSearch(reverse);
    assertEquals(normal.directionVectorX, reverse.directionVectorX);
    assertEquals(normal.velocityVectorX, reverse.velocityVectorX);
    assertEquals((normal.newDirection - 32) & 63, reverse.newDirection);
  }

  @Test void facingNormalizationUsesForceOrPredictedTileAndNormalizeIfSamePoint() {
    for (boolean normalizeSame : new boolean[] {false, true}) {
      for (boolean force : new boolean[] {false, true}) {
        D2PathResult.State near = state(0), far = state(0);
        near.precisionX++;
        near.newDirection = 13;
        near.storePoints(Collections.singletonList(p(10, 10)));
        far.newDirection = 13;
        far.storePoints(Collections.singletonList(p(20, 10)));
        D2PathResult.normalizeVectors(near, normalizeSame, force);
        D2PathResult.normalizeVectors(far, normalizeSame, force);
        assertEquals(force || normalizeSame ? 23 : 13, near.newDirection);
        assertEquals(force ? 56 : 13, far.newDirection);
      }
    }
  }

  @Test void coordinateSuccessCopiesPreviousBeforeTargetBecomesLastPointAndKeepsFinalGoal() {
    D2PathResult.State s = state(0);
    s.storePoints(Arrays.asList(p(15, 10), p(20, 10)));
    assertEquals(2, D2PathResult.finishSearch(s));
    assertEquals(p(30, 30), s.path.previousTargetPoint);
    assertEquals(p(20, 10), s.path.targetPoint);
    assertEquals(p(40, 40), s.path.finalTargetPoint);
    assertEquals(0, s.pendingUpdate);
    assertEquals(0x1020, s.path.pathFlags);
    assertEquals(77, s.path.nDist());
    assertEquals(77, s.path.nDistMax());
    assertEquals(12, s.path.nDistance());
  }

  @Test void unitIdentityOrFlag10IndependentlyKeepsTargetRatherThanLastPoint() {
    for (boolean unit : new boolean[] {false, true}) {
      for (boolean flag10 : new boolean[] {false, true}) {
        D2PathResult.State s = state(0);
        if (flag10) s.path.pathFlags |= 0x10;
        if (unit) {
          D2PathUnits.Unit owner = D2PathUnits.Unit.dynamic(0, 2, null);
          D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(30, 30));
          s.path.targetUnit = D2PathUnits.observation(owner, target, 0);
        }
        D2PathContinuation.Target identity = s.path.targetUnit;
        s.storePoints(Collections.singletonList(p(20, 10)));
        assertEquals(1, D2PathResult.finishSearch(s));
        assertEquals(unit || flag10 ? p(30, 30) : p(20, 10), s.path.targetPoint);
        assertEquals(p(30, 30), s.path.previousTargetPoint);
        assertSame(identity, s.path.targetUnit);
      }
    }
  }

  @Test void roomFlagUsesHalfOpenBoundsAndDoesNotRejectAnOutsidePoint() {
    for (D2Path.Point point : Arrays.asList(p(10, 10), p(19, 19), p(9, 10), p(20, 10), p(10, 9), p(10, 20))) {
      D2PathResult.State s = state(0);
      s.room = new D2PathResult.Room(10, 10, 10, 10);
      s.precisionX = D2PathStep.toFp16Center(15);
      s.precisionY = D2PathStep.toFp16Center(15);
      s.path.currentPoint = p(15, 15);
      s.storePoints(Collections.singletonList(point));
      assertEquals(1, D2PathResult.finishSearch(s));
      assertEquals(point.x < 10 || point.x >= 20 || point.y < 10 || point.y >= 20 ? 1 : 0,
          s.path.pathFlags & 1);
      assertNotEquals(0, s.path.pathFlags & 0x20);
    }
  }

  @Test void dllNullRoomMarksANonemptyPathRatherThanUpstreamClearingTheFlag() {
    D2PathResult.State s = state(0);
    s.room = null;
    s.path.pathFlags &= ~1;
    s.storePoints(Collections.singletonList(p(20, 10)));
    assertEquals(1, D2PathResult.finishSearch(s));
    assertEquals(1, s.path.pathFlags & 1, "DLL 6FDA8240 -> 6FDA8282");
    s.path.pathPointCount = 0;
    D2PathResult.updateRoomFlag(s);
    assertEquals(0, s.path.pathFlags & 1, "no points means no null-room branch");
  }

  @Test void roomScanIncludesConsumedPrefixButIgnoresStaleTailBeyondCount() {
    D2PathResult.State s = state(0);
    s.room = new D2PathResult.Room(10, 10, 10, 10);
    s.storePoints(Arrays.asList(p(9, 10), p(15, 10), p(999, 999)));
    s.path.currentPointIndex = 1;
    s.path.pathPointCount = 2;
    D2PathResult.updateRoomFlag(s);
    assertEquals(1, s.path.pathFlags & 1);
    s.points[0] = p(10, 10);
    D2PathResult.updateRoomFlag(s);
    assertEquals(0, s.path.pathFlags & 1);
  }

  @Test void roomUpperBoundsKeepNativeInt32AdditionAndZeroWidthIsNotInclusive() {
    D2PathResult.State s = state(0);
    s.storePoints(Collections.singletonList(p(10, 10)));
    s.room = new D2PathResult.Room(10, 10, Integer.MAX_VALUE, 10);
    D2PathResult.updateRoomFlag(s);
    assertEquals(1, s.path.pathFlags & 1);
    s.room = new D2PathResult.Room(10, 10, 0, 10);
    D2PathResult.updateRoomFlag(s);
    assertEquals(1, s.path.pathFlags & 1);
  }

  @Test void commonRejectionDoesNotInventVelocityBufferOrTargetReset() {
    D2PathResult.State s = state(0);
    s.storePoints(Arrays.asList(p(15, 10), p(20, 10)));
    s.path.currentPointIndex = 1;
    s.velocityVectorX = 55;
    assertEquals(0, D2PathResult.reject(s));
    assertEquals(0, s.path.pathPointCount);
    assertEquals(0, s.path.currentPointIndex);
    assertEquals(0x1001, s.path.pathFlags);
    assertEquals(256, s.velocity);
    assertEquals(55, s.velocityVectorX);
    assertEquals(p(20, 10), s.points[1]);
    assertEquals(p(30, 30), s.path.targetPoint);
    assertEquals(p(29, 29), s.path.previousTargetPoint);
    assertEquals(p(40, 40), s.path.finalTargetPoint);
    assertEquals(123, s.pendingUpdate);
  }

  @Test void playerMonsterAndDefaultOwnerScheduleFacingOnlyWhenNewDirectionChanges() {
    for (int type : new int[] {0, 1, 5, 6}) {
      D2PathResult.State s = state(type);
      s.direction = 10;
      s.newDirection = 10;
      s.diffDirection = 99;
      D2PathResult.updateFacing(s, 74);
      assertEquals(99, s.diffDirection);
      D2PathResult.updateFacing(s, 19);
      assertEquals(10, s.direction);
      assertEquals(19, s.newDirection);
      assertEquals(4, s.diffDirection);
      D2PathResult.updateFacing(s, 2);
      assertEquals(255, s.diffDirection);
    }
  }

  @Test void objectAndItemSetCurrentFacingButMissileRespectsFlag40() {
    for (int type : new int[] {2, 3, 4}) {
      for (boolean flag40 : new boolean[] {false, true}) {
        D2PathResult.State s = state(type);
        if (flag40) s.path.pathFlags |= 0x40;
        s.direction = 7;
        s.newDirection = 8;
        s.diffDirection = 9;
        D2PathResult.updateFacing(s, 130);
        assertEquals(type == 3 && flag40 ? 7 : 2, s.direction);
        assertEquals(8, s.newDirection);
        assertEquals(9, s.diffDirection);
      }
    }
  }

  @Test void scheduledFacingRetainsAllNativeTurnTableBoundaries() {
    int[] delta = {0, 1, 8, 9, 16, 17, 31, 32, 47, 48, 55, 56, 63};
    int[] expected = {0, 1, 1, 4, 4, 8, 8, 248, 248, 252, 252, 255, 255};
    for (int i = 0; i < delta.length; i++) {
      D2PathResult.State s = state(0);
      s.newDirection = 255;
      D2PathResult.updateFacing(s, delta[i]);
      assertEquals(expected[i], s.diffDirection);
    }
  }

  @Test void actualTowardPointsCanBeNormalizedWithoutPretendingTheyInstallProductionMovement() {
    D2PathResult.State s = state(0);
    D2PathResult.beginSearch(s, p(20, 10));
    D2Toward.Result kernel = D2Toward.compute(s.path.currentPoint, s.path.finalTargetPoint,
        77, 1, 0, (x, y) -> false);
    s.storePoints(kernel.points());
    assertEquals(kernel.points().size(), D2PathResult.finishSearch(s));
    assertEquals(p(20, 10), s.path.targetPoint);
    assertEquals(p(30, 30), s.path.previousTargetPoint);
    assertEquals(4096, s.velocityVectorX);
    assertEquals(p(10, 10), s.path.currentPoint, "no movement was performed");
  }

  @Test void continuationFallbackSharesFirstResultStateThenNormalizesActualWallFollowPoints() {
    D2PathResult.State s = state(1);
    s.path.currentPointIndex = 2;
    s.path.pathPointCount = 2;
    s.path.finalTargetPoint = p(20, 10);
    List<String> calls = new ArrayList<>();
    int result = D2PathContinuation.resume(s.path, false, new D2PathContinuation.Effects() {
      @Override public void refresh(D2PathContinuation.State path) { calls.add("refresh"); }
      @Override public int search(D2PathContinuation.State path) {
        calls.add("search:" + path.pathType);
        if (path.pathType == 2) {
          D2PathResult.beginSearch(s, p(19, 10));
          s.storePoints(Collections.emptyList());
          return D2PathResult.finishSearch(s);
        }
        assertEquals(p(19, 10), path.finalTargetPoint);
        assertEquals(p(20, 10), path.targetPoint);
        assertEquals(0, path.pathFlags & 0x20);
        assertEquals(10, path.nDistance());
        D2PathResult.beginSearch(s, path.finalTargetPoint);
        s.storePoints(D2WallFollow.compute(path.currentPoint, path.finalTargetPoint,
            path.nDistMax(), false, (x, y) -> false).points());
        return D2PathResult.finishSearch(s);
      }
    });
    assertEquals(Arrays.asList("refresh", "search:2", "search:15"), calls);
    assertTrue(result > 0);
    assertEquals(p(19, 10), s.path.targetPoint);
    assertEquals(p(20, 10), s.path.previousTargetPoint);
    assertEquals(10, s.path.nDistance());
  }

  @Test void invalidCountsPointsAndMissilesAreRejectedWithoutGuessingAReplacementPath() {
    D2PathResult.State s = state(0);
    assertThrows(IllegalArgumentException.class, () -> s.storePoints(Collections.nCopies(79, p(1, 1))));
    assertThrows(IllegalArgumentException.class, () -> s.storePoints(Collections.singletonList(p(-1, 1))));
    assertThrows(IllegalArgumentException.class, () -> D2PathResult.beginSearch(s, p(65536, 0)));
    assertThrows(IllegalArgumentException.class, () -> D2PathResult.normalizeVectors(s, true, true));
    s.path.pathPointCount = 79;
    assertThrows(IllegalArgumentException.class, () -> D2PathResult.finishSearch(s));
    s.path.pathPointCount = 0;
    s.path.pathFlags |= 0x40000;
    assertThrows(IllegalArgumentException.class, () -> D2PathResult.finishSearch(s));
  }

  @Test void unsafeNativeDirectionIndexArithmeticIsExplicitlyOutsideThisProjection() {
    D2PathResult.State s = state(0);
    s.storePoints(Collections.singletonList(p(65535, 65535)));
    assertThrows(IllegalArgumentException.class, () -> D2PathResult.normalizeVectors(s, true, true));
    s.storePoints(Collections.singletonList(p(300, 300)));
    assertThrows(IllegalArgumentException.class, () -> D2PathResult.normalizeVectors(s, true, true));
    s.storePoints(Collections.singletonList(p(110, 110)));
    assertDoesNotThrow(() -> D2PathResult.normalizeVectors(s, true, true));
  }

  @Test void installed110fDirectionAndFacingTablesMatchTheHelpersStaticDataOracle() throws Exception {
    ByteBuffer common = D2PathUnitsTest.originalDll("D2Common.dll",
        "B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB");
    int vectors = D2PathUnitsTest.fileOffset(common, 0x6fdd2598L);
    for (int tangent = 0; tangent < 128; tangent++) {
      D2PathStep.DirectionVector actual = D2PathStep.directionVector(0, 0, tangent, 127);
      assertEquals(common.getInt(vectors + tangent * 12), actual.x);
      assertEquals(common.getInt(vectors + tangent * 12 + 4), actual.y);
      assertEquals((7 - common.getInt(vectors + tangent * 12 + 8)) & 63, actual.direction);
    }
    int turns = D2PathUnitsTest.fileOffset(common, 0x6fdd1f88L);
    for (int delta = 0; delta < 64; delta++) {
      D2PathResult.State s = state(0);
      s.newDirection = 255;
      D2PathResult.updateFacing(s, delta);
      assertEquals(common.getInt(turns + delta * 4) & 255, s.diffDirection);
    }
  }
}
