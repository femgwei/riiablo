package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.collision.D2Collision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgCoords;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class D2PathRequestTest {
  @Test void standardObservationRejectsSameTileDifferentFractionAtConstruction() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(20, 10));
    D2PathRequest.State s = request(result(0, p(10, 10), p(20, 10)), target,
        room(0, 0, 100, 100, false), false, 0);
    s.result.precisionX++;
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(s.result,
        target, s.startRoom, false, 0, 2, 3, 1, 70));
    assertEquals(p(10, 10), s.result.path.currentPoint);
  }
  @Test void fractionalMovementRequiresRecaptureBeforeCustomEffects() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(20, 10));
    D2PathRequest.State s = request(result(0, p(10, 10), p(20, 10)), target,
        room(0, 0, 100, 100, false), false, 0);
    s.result.precisionY ^= 1;
    Effects effects = new Effects();
    assertThrows(IllegalArgumentException.class, () -> D2PathRequest.compute(s, false, effects));
    assertTrue(effects.calls.isEmpty()); assertEquals(0x1234, s.collidedMask);
  }
  @Test void standardObservationRejectsDifferentOwnerStartAtConstruction() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(20, 10));
    D2PathResult.State r = result(0, p(10, 10), p(20, 10));
    D2PathRequest.Room room = room(0, 0, 100, 100, false); r.room = room.bounds;
    r.path.targetUnit = D2PathUnits.observation(D2PathUnits.Unit.dynamic(0, 2,
        new D2PathUnits.DynamicPath(11 << 16, 10 << 16, 0)), target, 0);
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(r,
        target, room, false, 0, 2, 3, 1, 70));
    assertEquals(2, r.path.pathPointCount); assertEquals(99, r.pendingUpdate);
  }

  @Test void movedRequestStartRequiresRecaptureBeforeAnyCustomEffect() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(20, 10));
    D2PathRequest.State s = request(result(0, p(10, 10), p(20, 10)), target,
        room(0, 0, 100, 100, false), false, 0);
    s.result.path.currentPoint = p(11, 10);
    Effects effects = new Effects();
    assertThrows(IllegalArgumentException.class, () -> D2PathRequest.compute(s, false, effects));
    assertTrue(effects.calls.isEmpty()); assertEquals(0x1234, s.collidedMask);
    assertEquals(2, s.result.path.pathPointCount);
  }
  @Test void standardObservationRejectsOwnerSizeMismatchBeforeEffectsExist() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(20, 10));
    D2PathRequest.State valid = request(result(0, p(10, 10), p(20, 10)), target,
        room(0, 0, 100, 100, false), false, 0);
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(valid.result,
        target, valid.startRoom, false, 0, 3, 3, 1, 70));
    assertEquals(2, valid.result.path.pathPointCount);
    assertEquals(99, valid.result.pendingUpdate);
  }

  @Test void standardObservationRejectsOwnerTypeMismatchEvenAtSameCoordinatesAndSize() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(20, 10));
    D2PathResult.State r = result(0, p(10, 10), p(20, 10));
    D2PathRequest.Room room = room(0, 0, 100, 100, false); r.room = room.bounds;
    r.path.targetUnit = D2PathUnits.observation(D2PathUnits.Unit.dynamic(1, 2,
        new D2PathUnits.DynamicPath(10 << 16, 10 << 16, 0)), target, 0);
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(r,
        target, room, false, 0, 2, 3, 1, 70));
    assertEquals(2, r.path.pathPointCount); assertEquals(99, r.pendingUpdate);
  }
  @Test void standardObservationMustMatchTheExactTargetSnapshotAndSpeed() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(2, 2, p(20, 10));
    D2PathRequest.State state = request(result(0, p(10, 10), p(20, 10)), target,
        room(0, 0, 100, 100, false), false, 0);
    D2PathUnits.Unit sameCoordinates = D2PathUnits.Unit.stationary(2, 2, p(20, 10));
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(state.result,
        sameCoordinates, state.startRoom, false, 0, 2, 3, 1, 70));
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(state.result,
        target, state.startRoom, false, 1, 2, 3, 1, 70));
  }

  @Test void replacingAnObservationAfterConstructionRequiresANewRequest() {
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(2, 2, p(20, 10));
    D2PathRequest.State state = request(result(0, p(10, 10), p(20, 10)), target,
        room(0, 0, 100, 100, false), false, 0);
    state.result.path.targetUnit = D2PathUnits.observation(D2PathUnits.Unit.dynamic(0, 2, null), target, 0);
    Effects effects = new Effects();
    assertThrows(IllegalArgumentException.class, () -> D2PathRequest.compute(state, false, effects));
    assertEquals(0x1234, state.collidedMask);
    assertEquals(2, state.result.path.pathPointCount);
    assertTrue(effects.calls.isEmpty());
  }

  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }
  private static D2PathRequest.Room room(int x, int y, int w, int h, boolean town) {
    return new D2PathRequest.Room(new D2PathResult.Room(x, y, w, h), town);
  }
  private static D2PathResult.State result(int owner, D2Path.Point start, D2Path.Point goal) {
    D2PathContinuation.State path = new D2PathContinuation.State(owner);
    path.currentPoint = start;
    path.targetPoint = goal;
    path.previousTargetPoint = p(8, 8);
    path.finalTargetPoint = p(9, 9);
    path.pathType = 2;
    path.pathFlags = 0x20;
    path.setNewDistance(77);
    D2PathResult.State result = new D2PathResult.State(path);
    result.storePoints(Arrays.asList(p(11, 11), p(12, 12)));
    path.currentPointIndex = 1;
    result.velocity = 256;
    result.pendingUpdate = 99;
    return result;
  }
  private static D2PathRequest.State request(D2PathResult.State result, D2PathUnits.Unit target,
      D2PathRequest.Room room, boolean canTown, int speed) {
    result.room = room == null ? null : room.bounds;
    if (target != null) result.path.targetUnit = D2PathUnits.observation(
        D2PathUnits.Unit.dynamic(result.path.ownerType, 2,
            new D2PathUnits.DynamicPath(result.precisionX, result.precisionY, 0)), target, speed);
    D2PathRequest.State s = new D2PathRequest.State(result, target, room, canTown, speed, 2, 3, 0x87654321, 70);
    s.collidedMask = 0x1234;
    return s;
  }
  private static D2PathRequest.State request(int owner) {
    return request(result(owner, p(10, 10), p(20, 10)), null, room(0, 0, 100, 100, false), false, 0);
  }

  private static class Effects implements D2PathRequest.Effects {
    final List<String> calls = new ArrayList<>();
    boolean ownerRemoved = true, targetRemoved = true;
    D2PathRequest.Search search;
    @Override public boolean removeOwner(D2PathRequest.State s) { calls.add("removeOwner"); return ownerRemoved; }
    @Override public boolean removeTarget(D2PathRequest.State s) { calls.add("removeTarget"); return targetRemoved; }
    @Override public void addTarget(D2PathRequest.State s) { calls.add("addTarget"); }
    @Override public void addOwner(D2PathRequest.State s) {
      calls.add("addOwner");
      assertEquals(99, s.result.pendingUpdate, "restoration precedes result normalization/success writes");
    }
    @Override public boolean blocked(D2PathRequest.State s, int x, int y) { calls.add("blocked"); return false; }
    @Override public int search(D2PathRequest.Search search) {
      this.search = search;
      calls.add("search:" + search.type);
      assertEquals(0, search.state.result.path.currentPointIndex);
      assertEquals(0, search.state.result.path.pathPointCount);
      assertEquals(search.target, search.state.result.path.finalTargetPoint);
      search.state.result.points[0] = search.target;
      return 1;
    }
  }

  @Test void nullPathReturnsWithoutEffectsAndMissileDoesNotClearCollisionMask() {
    assertEquals(0, D2PathRequest.compute(null, false, null));
    D2PathRequest.State s = request(0);
    s.result.path.pathFlags |= 0x40000;
    assertThrows(IllegalArgumentException.class, () -> D2PathRequest.compute(s, false, null));
    assertEquals(0x1234, s.collidedMask);
    assertEquals(2, s.result.path.pathPointCount);
  }

  @Test void rawZeroAxisTargetsReturnEarlyWithoutClearingOldPathOrSnapshots() {
    for (D2PathUnits.Unit target : Arrays.asList(
        D2PathUnits.Unit.stationary(4, 1, p(0, 20)),
        D2PathUnits.Unit.stationary(4, 1, p(20, 0)),
        D2PathUnits.Unit.dynamic(1, 2, null))) {
      D2PathResult.State r = result(0, p(10, 10), p(30, 30));
      D2PathRequest.State s = request(r, target, null, false, 0);
      assertEquals(0, D2PathRequest.compute(s, false, null));
      assertEquals(0, s.collidedMask);
      assertEquals(1, r.path.currentPointIndex);
      assertEquals(2, r.path.pathPointCount);
      assertEquals(0x20, r.path.pathFlags);
      assertEquals(p(30, 30), r.path.targetPoint);
      assertEquals(p(8, 8), r.path.previousTargetPoint);
      assertEquals(p(9, 9), r.path.finalTargetPoint);
    }
  }

  @Test void zeroStartRejectsBeforeUpdatingAnOtherwiseValidUnitTarget() {
    D2PathResult.State r = result(0, p(0, 0), p(30, 30));
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(20, 20)), null, false, 0);
    assertEquals(0, D2PathRequest.compute(s, false, null));
    assertEquals(p(30, 30), r.path.targetPoint);
    assertEquals(0, r.path.pathPointCount);
    assertEquals(0, r.path.currentPointIndex);
    assertEquals(0, r.path.pathFlags & 0x20);
    assertEquals(256, r.velocity);
    assertEquals(p(9, 9), r.path.finalTargetPoint);
  }

  @Test void bothZeroCoordinateGoalAndCoincidenceUseCommonCleanupNotEarlyPreservation() {
    for (D2Path.Point goal : Arrays.asList(p(0, 0), p(10, 10))) {
      D2PathRequest.State s = request(result(0, p(10, 10), goal), null, null, false, 0);
      assertEquals(0, D2PathRequest.compute(s, false, null));
      assertEquals(0, s.result.path.pathPointCount);
      assertEquals(0, s.result.path.currentPointIndex);
      assertEquals(0, s.result.path.pathFlags & 0x20);
      assertEquals(99, s.result.pendingUpdate);
    }
  }

  @Test void oneZeroAxisInCoordinateStartOrGoalIsAllowedUnlikeRawUnitZeroAxis() {
    for (D2Path.Point[] pair : Arrays.asList(new D2Path.Point[] {p(0, 10), p(10, 0)},
        new D2Path.Point[] {p(10, 0), p(0, 10)})) {
      D2PathRequest.State s = request(result(0, pair[0], pair[1]), null, room(0, 0, 100, 100, false), false, 0);
      assertEquals(1, D2PathRequest.compute(s, false, new Effects()));
    }
  }

  @Test void hundredWindowUsesEachAxisInclusiveAndDoesNotRequireADiagonalRadius() {
    for (int dx : new int[] {-101, -100, 100, 101}) {
      for (int dy : new int[] {-101, -100, 100, 101}) {
        D2PathRequest.State s = request(result(0, p(200, 200), p(200 + dx, 200 + dy)),
            null, room(0, 0, 500, 500, false), false, 0);
        Effects e = new Effects();
        boolean valid = Math.abs(dx) <= 100 && Math.abs(dy) <= 100;
        assertEquals(valid ? 1 : 0, D2PathRequest.compute(s, false, e));
        assertEquals(valid, e.search != null);
      }
    }
  }

  @Test void targetNarrowsBeforeWindowButRawNonzeroAxesDoNotGuaranteePreparedNonzero() {
    D2PathResult.State r = result(0, p(10, 10), p(30, 30));
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(65536, 65536)), null, false, 0);
    assertEquals(0, D2PathRequest.compute(s, false, null));
    assertEquals(p(0, 0), r.path.targetPoint);
    assertEquals(0, r.path.pathPointCount);
    assertEquals(p(8, 8), r.path.previousTargetPoint);
  }

  @Test void unitTargetUpdatePersistsEvenWhenTheLaterWindowGateRejects() {
    D2PathResult.State r = result(0, p(10, 10), p(30, 30));
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(200, 10)), null, false, 0);
    assertEquals(0, D2PathRequest.compute(s, false, null));
    assertEquals(p(200, 10), r.path.targetPoint);
    assertEquals(p(9, 9), r.path.finalTargetPoint);
    assertEquals(p(8, 8), r.path.previousTargetPoint);
  }

  @Test void dllMissingStartRoomAssertsAfterTargetPreparationRatherThanCommonRejecting() {
    D2PathResult.State r = result(0, p(10, 10), p(30, 30));
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(20, 10)), null, false, 0);
    assertThrows(IllegalStateException.class, () -> D2PathRequest.compute(s, true, null));
    assertEquals(p(20, 10), r.path.targetPoint);
    assertEquals(2, r.path.pathPointCount);
    assertEquals(1, r.path.currentPointIndex);
    assertEquals(0x20, r.path.pathFlags);
  }

  @Test void roomLookupUsesSelfThenOrderedNeighborsNullEntriesAndHalfOpenBounds() {
    D2PathRequest.Room a = room(0, 0, 10, 10, false), b = room(10, 0, 10, 10, true), c = room(10, 0, 20, 10, false);
    a.nearby = Arrays.asList(null, c, b);
    assertSame(a, D2PathRequest.findRoom(a, 9, 9));
    assertSame(c, D2PathRequest.findRoom(a, 10, 0));
    assertNull(D2PathRequest.findRoom(a, 30, 0));
    assertNull(D2PathRequest.findRoom(null, 1, 1));
    a.nearby = Arrays.asList(b, c);
    assertSame(b, D2PathRequest.findRoom(a, 10, 0));
    D2PathRequest.Room overflow = room(10, 0, Integer.MAX_VALUE, 10, false);
    assertNull(D2PathRequest.findRoom(overflow, 10, 0));
  }

  @Test void townEligibilityRequiresAMonStatsRecordEvenForRevivedAndAllFlagCombinations() {
    for (boolean record : new boolean[] {false, true})
      for (boolean npc : new boolean[] {false, true})
        for (boolean town : new boolean[] {false, true})
          for (boolean revive : new boolean[] {false, true})
            assertEquals(record && (npc || town || revive), D2PathRequest.monsterCanBeInTown(record, npc, town, revive));
  }

  @Test void onlyOrdinaryMonstersAreTownRestrictedAndAllowInTownCanOverride() {
    for (int owner : new int[] {0, 1, 2, 3, 4, 5})
      for (boolean canTown : new boolean[] {false, true})
        for (boolean allow : new boolean[] {false, true}) {
          D2PathRequest.State s = request(result(owner, p(10, 10), p(20, 10)),
              null, room(0, 0, 100, 100, true), canTown, 0);
          Effects e = new Effects();
          boolean valid = owner != 1 || canTown || allow;
          assertEquals(valid ? 1 : 0, D2PathRequest.compute(s, allow, e));
          assertEquals(valid, e.search != null);
        }
  }

  @Test void midpointLookupCanFindTownThroughASecondNeighborhoodWithoutReplacingInitialRoom() {
    D2PathRequest.Room a = room(0, 0, 11, 20, false), b = room(11, 0, 9, 20, false), c = room(20, 0, 10, 20, true);
    a.nearby = Collections.singletonList(b);
    b.nearby = Collections.singletonList(c);
    D2PathRequest.State s = request(result(1, p(10, 10), p(21, 10)), null, a, false, 0);
    assertNull(D2PathRequest.findRoom(a, 21, 10));
    Effects e = new Effects();
    assertEquals(0, D2PathRequest.compute(s, false, e));
    assertTrue(e.calls.isEmpty());
    assertEquals(1, D2PathRequest.compute(s, true, e));
    assertNull(e.search.targetRoom, "town fallback is local, not the native PathInfo targetRoom");
  }

  @Test void midpointUsesTruncationTowardZeroFromStartNotFloorOrAverageCoordinates() {
    D2PathRequest.Room a = room(0, 0, 1, 1, false), half = room(16, 16, 1, 1, false), town = room(11, 11, 1, 1, true);
    a.nearby = Collections.singletonList(half);
    half.nearby = Collections.singletonList(town);
    assertFalse(D2PathRequest.destinationAllowed(1, false, a, null, p(20, 20), p(11, 11)));
    assertTrue(D2PathRequest.destinationAllowed(0, false, a, null, p(20, 20), p(11, 11)));
    assertTrue(D2PathRequest.destinationAllowed(1, true, a, null, p(20, 20), p(11, 11)));
  }

  @Test void missingTargetRoomDoesNotIntroduceThe113GateEvenForAnOrdinaryMonster() {
    D2PathRequest.State s = request(result(1, p(10, 10), p(20, 10)), null, room(0, 0, 11, 20, false), false, 0);
    Effects e = new Effects();
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertNull(e.search.targetRoom);
    assertEquals(1, s.result.path.pathFlags & 1);
  }

  @Test void normalOrderRestoresTargetThenOwnerBeforeSuccessfulNormalization() {
    D2PathResult.State r = result(0, p(10, 10), p(30, 30));
    r.path.pathFlags |= 0x800 | 0x1000;
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(20, 10)), room(0, 0, 100, 100, false), false, 0);
    Effects e = new Effects();
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertEquals(Arrays.asList("removeOwner", "removeTarget", "blocked", "search:2", "addTarget", "addOwner"), e.calls);
    assertEquals(0, s.collidedMask);
    assertEquals(2, e.search.tolerance);
    assertEquals(77, e.search.maximumDistance);
    assertEquals(70, s.initialFScore);
    assertEquals(0x87654321, s.moveMask, "PathInfo captures the raw uint32 field");
    assertEquals(p(20, 10), r.path.previousTargetPoint);
    assertEquals(p(20, 10), r.path.targetPoint);
    assertEquals(4096, r.velocityVectorX);
    assertEquals(0, r.pendingUpdate);
  }

  @Test void targetRemovalRequiresIdentitySizeAndFlagAndItsReturnControlsRestoration() {
    for (int size : new int[] {0, 1})
      for (boolean flag : new boolean[] {false, true})
        for (boolean removed : new boolean[] {false, true}) {
          D2PathResult.State r = result(0, p(10, 10), p(30, 30));
          if (flag) r.path.pathFlags |= 0x800;
          D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, size, p(20, 10)), room(0, 0, 100, 100, false), false, 0);
          Effects e = new Effects(); e.targetRemoved = removed;
          assertEquals(1, D2PathRequest.compute(s, false, e));
          assertEquals(flag && size != 0, e.calls.contains("removeTarget"));
          assertEquals(flag && size != 0 && removed, e.calls.contains("addTarget"));
        }
    D2PathRequest.State noTarget = request(0); noTarget.result.path.pathFlags |= 0x800;
    Effects e = new Effects();
    assertEquals(1, D2PathRequest.compute(noTarget, false, e));
    assertFalse(e.calls.contains("removeTarget"));
  }

  @Test void ownerRestorationIsUnconditionalEvenWhenRemovalReturnsFalse() {
    Effects e = new Effects(); e.ownerRemoved = false;
    assertEquals(1, D2PathRequest.compute(request(0), false, e));
    assertEquals(Arrays.asList("removeOwner", "search:2", "addOwner"), e.calls);
  }

  @Test void blockedTargetPreparationFailureStillWritesFinalAndRestoresButNeverDispatches() {
    D2PathRequest.State s = request(1);
    s.result.path.pathFlags |= 0x1000;
    Effects e = new Effects() {
      @Override public boolean blocked(D2PathRequest.State state, int x, int y) { return true; }
    };
    assertEquals(0, D2PathRequest.compute(s, false, e));
    assertEquals(Arrays.asList("removeOwner", "addOwner"), e.calls);
    assertEquals(p(20, 10), s.result.path.finalTargetPoint);
    assertEquals(0, s.result.path.currentPointIndex);
    assertEquals(0, s.result.path.pathPointCount);
    assertEquals(256, s.result.velocity);
    assertEquals(99, s.result.pendingUpdate);
    assertEquals(p(8, 8), s.result.path.previousTargetPoint);
  }

  @Test void adjustedFinalDoesNotOverwriteUnitTargetAndCapturedRoomBelongsToUnadjustedGoal() {
    D2PathRequest.Room a = room(0, 0, 20, 100, false), b = room(20, 0, 80, 100, false);
    a.nearby = Collections.singletonList(b);
    D2PathResult.State r = result(1, p(10, 10), p(30, 30)); r.path.pathFlags |= 0x1000;
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(20, 10)), a, false, 0);
    Effects e = new Effects() {
      @Override public boolean blocked(D2PathRequest.State state, int x, int y) { return x == 20; }
    };
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertEquals(p(19, 10), e.search.target);
    assertSame(b, e.search.targetRoom);
    assertEquals(p(20, 10), r.path.targetPoint);
    assertEquals(p(20, 10), r.path.previousTargetPoint);
    assertEquals(p(19, 10), r.path.finalTargetPoint);
  }

  @Test void searchReturnCountOverridesCallbackCountAndFailureDoesNotAutomaticallyTryType15() {
    D2PathRequest.State s = request(0);
    Effects e = new Effects() {
      @Override public int search(D2PathRequest.Search search) {
        super.search(search);
        search.state.result.path.pathPointCount = 2;
        return 0;
      }
    };
    assertEquals(0, D2PathRequest.compute(s, false, e));
    assertEquals(Arrays.asList("removeOwner", "search:2", "addOwner"), e.calls);
    assertEquals(0, s.result.path.pathPointCount);
    assertEquals(p(8, 8), s.result.path.previousTargetPoint);
  }

  @Test void nativeSearchTypeIsPassedThroughNotInferredFromOwnerAndNoProductionDispatcherIsClaimed() {
    D2PathRequest.State s = request(0); s.result.path.pathType = 13;
    Effects e = new Effects();
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertEquals(13, e.search.type);
    assertEquals(1, e.search.tolerance);
  }

  @Test void callbackExceptionRestorationIsHostSafetyNotNativeRollback() {
    D2PathResult.State r = result(0, p(10, 10), p(30, 30)); r.path.pathFlags |= 0x800;
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(20, 10)), room(0, 0, 100, 100, false), false, 0);
    Effects e = new Effects() {
      @Override public int search(D2PathRequest.Search search) {
        calls.add("throw");
        throw new IllegalArgumentException("host search failure");
      }
    };
    assertThrows(IllegalArgumentException.class, () -> D2PathRequest.compute(s, false, e));
    assertEquals(Arrays.asList("removeOwner", "removeTarget", "throw", "addTarget", "addOwner"), e.calls);
    assertEquals(p(20, 10), r.path.targetPoint);
    assertEquals(p(20, 10), r.path.finalTargetPoint);
    assertEquals(0, r.path.pathPointCount);
    assertEquals(0x20, r.path.pathFlags & 0x20, "no invented native failure cleanup on a host exception");
  }

  @Test void actualTowardKernelCanBeCalledFromExplicitDispatcherWithResultNormalization() {
    D2PathRequest.State s = request(1);
    Effects e = new Effects() {
      @Override public int search(D2PathRequest.Search search) {
        calls.add("toward");
        List<D2Path.Point> points = D2Toward.compute(search.start, search.target,
            search.maximumDistance, search.tolerance, search.state.result.path.directionOffset,
            (x, y) -> blocked(search.state, x, y)).points();
        search.state.result.storePoints(points);
        return points.size();
      }
    };
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertEquals(p(20, 10), s.result.path.targetPoint);
    assertEquals(4096, s.result.velocityVectorX);
    assertEquals(p(10, 10), s.result.path.currentPoint, "still no production movement consumption");
  }

  @Test void unitDoorPreparationRunsBeforeTownAndSearchAndKeepsToleranceTwo() {
    D2PathRequest.State s = request(result(0, p(10, 10), p(30, 30)),
        D2PathUnits.Unit.door(2, p(20, 10), false), room(0, 0, 100, 100, false), false, 0);
    Effects e = new Effects();
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertEquals(p(18, 10), e.search.target);
    assertEquals(2, e.search.tolerance);
    assertEquals(p(18, 10), s.result.path.previousTargetPoint);
  }

  @Test void stateRejectsMismatchedIdentityRoomAndUnresolvedOwnerWithoutMutatingThePath() {
    D2PathResult.State r = result(0, p(10, 10), p(20, 10));
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(r,
        D2PathUnits.Unit.stationary(4, 1, p(20, 10)), null, false, 0, 2, 3, 9, 70));
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(r,
        null, room(0, 0, 100, 100, false), false, 0, 2, 3, 9, 70));
    D2PathResult.State noOwner = result(6, p(10, 10), p(20, 10));
    assertThrows(IllegalArgumentException.class, () -> new D2PathRequest.State(noOwner,
        null, null, false, 0, 2, 3, 9, 70));
    assertEquals(2, r.path.pathPointCount);
  }

  @Test void searchCapturesTypeAndDistanceBeforeFootprintEffectsButDoesNotRollbackTheirWrites() {
    D2PathRequest.State s = request(0);
    Effects e = new Effects() {
      @Override public boolean removeOwner(D2PathRequest.State state) {
        state.result.path.pathType = 15;
        state.result.path.setNewDistance(12);
        return super.removeOwner(state);
      }
    };
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertEquals(2, e.search.type);
    assertEquals(77, e.search.maximumDistance);
    assertEquals(15, s.result.path.pathType);
    assertEquals(12, s.result.path.nDistMax());
  }

  @Test void allSourceSearchResultsAreRestoredBeforeNormalizationZerosVelocityAndCommonRejects() {
    D2PathRequest.State s = request(0);
    Effects e = new Effects() {
      @Override public int search(D2PathRequest.Search search) {
        search.state.result.points[0] = search.start;
        return 1;
      }
      @Override public void addOwner(D2PathRequest.State state) {
        assertEquals(256, state.result.velocity);
        assertEquals(1, state.result.path.pathPointCount);
        super.addOwner(state);
      }
    };
    assertEquals(0, D2PathRequest.compute(s, false, e));
    assertEquals(0, s.result.velocity);
    assertEquals(0, s.result.path.pathPointCount);
    assertEquals(0, s.result.path.currentPointIndex);
    assertEquals(99, s.result.pendingUpdate);
  }

  @Test void hostTargetRestorationExceptionStillAttemptsOwnerButDoesNotFinishTheNativeResult() {
    D2PathResult.State r = result(0, p(10, 10), p(30, 30)); r.path.pathFlags |= 0x800;
    D2PathRequest.State s = request(r, D2PathUnits.Unit.stationary(4, 1, p(20, 10)), room(0, 0, 100, 100, false), false, 0);
    Effects e = new Effects() {
      @Override public void addTarget(D2PathRequest.State state) {
        super.addTarget(state);
        throw new IllegalStateException("host restoration failure");
      }
    };
    assertThrows(IllegalStateException.class, () -> D2PathRequest.compute(s, false, e));
    assertEquals(Arrays.asList("removeOwner", "removeTarget", "search:2", "addTarget", "addOwner"), e.calls);
    assertEquals(99, r.pendingUpdate);
    assertEquals(p(8, 8), r.path.previousTargetPoint);
  }

  @Test void realInMemoryPatternFootprintsAreAbsentDuringSearchAndRestoredAfterward() {
    D2ActiveRoom gridRoom = new D2ActiveRoom();
    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNSubtileWidth(40); coords.setNSubtileHeight(40);
    gridRoom.setCoords(coords);
    D2CommonCollision.allocRoomCollisionGrid(gridRoom);
    int pattern = D2Collision.PATTERN_SMALL_UNIT_PRESENCE;
    int mask = D2Collision.COLLIDE_PLAYER | D2Collision.COLLIDE_ITEM | D2Collision.COLLIDE_WALL;
    D2CommonCollision.setMaskWithPattern(gridRoom, 10, 10, pattern, D2Collision.COLLIDE_PLAYER);
    D2CommonCollision.setMaskWithPattern(gridRoom, 20, 10, pattern, D2Collision.COLLIDE_ITEM);
    D2CommonCollision.setMask(gridRoom, 30, 30, D2Collision.COLLIDE_WALL);
    D2PathResult.State r = result(0, p(10, 10), p(30, 30)); r.path.pathFlags |= 0x800 | 0x1000;
    D2PathRequest.Room room = room(0, 0, 40, 40, false); r.room = room.bounds;
    D2PathUnits.Unit target = D2PathUnits.Unit.stationary(4, 1, p(20, 10));
    r.path.targetUnit = D2PathUnits.observation(D2PathUnits.Unit.dynamic(0, 2,
        new D2PathUnits.DynamicPath(r.precisionX, r.precisionY, 0)), target, 0);
    D2PathRequest.State s = new D2PathRequest.State(r, target, room, false, 0, 2, pattern, mask, 70);
    Effects e = new Effects() {
      @Override public boolean removeOwner(D2PathRequest.State state) {
        D2CommonCollision.resetMaskWithPattern(gridRoom, 10, 10, pattern, D2Collision.COLLIDE_PLAYER);
        return super.removeOwner(state);
      }
      @Override public boolean removeTarget(D2PathRequest.State state) {
        D2CommonCollision.resetMaskWithPattern(gridRoom, 20, 10, pattern, D2Collision.COLLIDE_ITEM);
        return super.removeTarget(state);
      }
      @Override public boolean blocked(D2PathRequest.State state, int x, int y) {
        assertEquals(0, D2CommonCollision.checkMask(gridRoom, 10, 10, D2Collision.COLLIDE_PLAYER));
        assertEquals(0, D2CommonCollision.checkMask(gridRoom, 20, 10, D2Collision.COLLIDE_ITEM));
        return D2CommonCollision.checkMaskWithPattern(gridRoom, x, y,
            state.collisionPattern, state.moveMask) != 0;
      }
      @Override public int search(D2PathRequest.Search search) {
        List<D2Path.Point> points = D2Toward.compute(search.start, search.target,
            search.maximumDistance, search.tolerance, 0, (x, y) -> blocked(s, x, y)).points();
        r.storePoints(points);
        return points.size();
      }
      @Override public void addTarget(D2PathRequest.State state) {
        D2CommonCollision.setMaskWithPattern(gridRoom, 20, 10, pattern, D2Collision.COLLIDE_ITEM);
        super.addTarget(state);
      }
      @Override public void addOwner(D2PathRequest.State state) {
        assertNotEquals(0, D2CommonCollision.checkMask(gridRoom, 20, 10, D2Collision.COLLIDE_ITEM));
        D2CommonCollision.setMaskWithPattern(gridRoom, 10, 10, pattern, D2Collision.COLLIDE_PLAYER);
        super.addOwner(state);
      }
    };
    assertEquals(1, D2PathRequest.compute(s, false, e));
    assertEquals(p(20, 10), r.points[0]);
    assertEquals(D2Collision.COLLIDE_PLAYER, D2CommonCollision.checkMask(gridRoom, 10, 10, mask));
    assertEquals(D2Collision.COLLIDE_ITEM, D2CommonCollision.checkMask(gridRoom, 20, 10, mask));
    assertEquals(D2Collision.COLLIDE_WALL, D2CommonCollision.checkMask(gridRoom, 30, 30, mask));
  }

  @Test void continuationCanRetryThisWrapperWithWallFollowWithoutRollingBackTheFirstFinalWrite() {
    D2PathRequest.State s = request(result(1, p(10, 10), p(30, 30)),
        D2PathUnits.Unit.stationary(4, 1, p(20, 10)), room(0, 0, 100, 100, false), false, 0);
    s.result.path.setDistance(20);
    List<Integer> types = new ArrayList<>();
    int count = D2PathContinuation.resume(s.result.path, true, new D2PathContinuation.Effects() {
      @Override public void refresh(D2PathContinuation.State path) {}
      @Override public int search(D2PathContinuation.State path) {
        return D2PathRequest.compute(s, false, new Effects() {
          @Override public int search(D2PathRequest.Search search) {
            types.add(search.type);
            if (search.type == 13) return 0;
            assertEquals(15, search.type);
            assertEquals(p(20, 10), path.finalTargetPoint);
            assertEquals(search.target, path.finalTargetPoint);
            assertEquals(0, path.pathFlags & 0x20);
            List<D2Path.Point> points = D2WallFollow.compute(search.start, search.target,
                search.maximumDistance, true, (x, y) -> false).points();
            s.result.storePoints(points);
            return points.size();
          }
        });
      }
    });
    assertTrue(count > 0);
    assertEquals(Arrays.asList(13, 15), types);
    assertEquals(19, s.result.path.nDistance());
    assertEquals(p(20, 10), s.result.path.previousTargetPoint);
    assertEquals(15, s.result.path.pathType);
  }
}
