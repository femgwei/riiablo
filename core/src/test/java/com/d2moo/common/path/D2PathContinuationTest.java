package com.d2moo.common.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.d2moo.common.path.D2PathContinuation.Decision.*;
import static org.junit.jupiter.api.Assertions.*;

class D2PathContinuationTest {
  private static D2Path.Point p(int x, int y) { return new D2Path.Point(x, y); }

  private static D2PathContinuation.State state(int ownerType) {
    D2PathContinuation.State s = new D2PathContinuation.State(ownerType);
    s.currentPoint = p(10, 10);
    s.targetPoint = p(16, 10);
    s.previousTargetPoint = p(16, 10);
    s.finalTargetPoint = p(20, 10);
    s.pathType = 2;
    s.currentPointIndex = 2;
    s.pathPointCount = 2;
    s.setDistance(10);
    return s;
  }

  private static final class Target implements D2PathContinuation.Target {
    int type = 1, distance = 100;
    D2Path.Point sampled = p(16, 10);
    final List<String> calls = new ArrayList<>();
    @Override public int distanceToOwner() { calls.add("distance"); return distance; }
    @Override public D2Path.Point samplePoint() { calls.add("sample"); return sampled; }
    @Override public int unitType() { calls.add("type"); return type; }
  }

  private static final class Effects implements D2PathContinuation.Effects {
    int result = 7;
    final List<String> calls = new ArrayList<>();
    @Override public void refresh(D2PathContinuation.State s) { calls.add("refresh"); }
    @Override public int search(D2PathContinuation.State s) {
      calls.add("search:" + s.pathType);
      return result;
    }
  }

  @Test void exhaustedCirclesKeepBeforeAnyUnitOrFinalTargetCheck() {
    for (int type : new int[] {5, 6}) {
      D2PathContinuation.State s = state(1);
      s.pathType = type;
      Target t = new Target();
      t.distance = 0;
      s.targetUnit = t;
      Effects e = new Effects();
      assertEquals(KEEP, D2PathContinuation.decide(s));
      assertEquals(1, D2PathContinuation.checkAndResume(s, e));
      assertFalse(s.hasRemainingPoints(), "KEEP is not a movement success");
      assertTrue(t.calls.isEmpty());
      assertTrue(e.calls.isEmpty());
      s.currentPointIndex++;
      assertEquals(KEEP, D2PathContinuation.decide(s));
    }
  }

  @Test void nonExhaustedCircleStillChecksUnitStopDistance() {
    D2PathContinuation.State s = state(0);
    s.pathType = 5;
    s.currentPointIndex = 1;
    Target t = new Target();
    t.distance = 0;
    s.targetUnit = t;
    assertEquals(STOP, D2PathContinuation.decide(s));
    assertEquals(Arrays.asList("distance"), t.calls);
  }

  @Test void coordinateTargetOnlyReplansAfterExhaustionAwayFromFinalGoal() {
    D2PathContinuation.State s = state(0);
    assertEquals(REPLAN_COORDINATE, D2PathContinuation.decide(s));
    s.currentPointIndex++;
    assertEquals(REPLAN_COORDINATE, D2PathContinuation.decide(s));
    s.currentPointIndex = 1;
    assertEquals(KEEP, D2PathContinuation.decide(s));
    s.currentPointIndex = 2;
    s.currentPoint = s.finalTargetPoint;
    assertEquals(KEEP, D2PathContinuation.decide(s));
  }

  @Test void unitWithinUnsignedStopDistanceStopsBeforeSamplingOrSearch() {
    D2PathContinuation.State s = state(0);
    s.setStepNum(255);
    Target t = new Target();
    t.distance = 255;
    s.targetUnit = t;
    Effects e = new Effects();
    assertEquals(0, D2PathContinuation.checkAndResume(s, e));
    assertEquals(Arrays.asList("distance"), t.calls);
    assertTrue(e.calls.isEmpty());
    assertEquals(10, s.nDistance());
  }

  @Test void unitStopDistanceIsInclusiveAndUsesProvidedNativeDistance() {
    for (int distance : new int[] {-1, 0, 4, 5, 6}) {
      D2PathContinuation.State s = state(0);
      s.currentPointIndex = 1;
      s.setStepNum(5);
      Target t = new Target();
      t.distance = distance;
      s.targetUnit = t;
      assertEquals(distance <= 5 ? STOP : KEEP, D2PathContinuation.decide(s));
      assertEquals(distance <= 5 ? Arrays.asList("distance")
          : Arrays.asList("distance", "sample", "type"), t.calls);
    }
  }

  @Test void movingUnitRequiresStrictlyMoreThanFiveOnEitherAxis() {
    for (int type : new int[] {0, 1}) {
      for (int[] delta : new int[][] {{5, 5}, {-5, -5}, {6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
        D2PathContinuation.State s = state(0);
        s.currentPointIndex = 1;
        Target t = new Target();
        t.type = type;
        t.sampled = p(16 + delta[0], 10 + delta[1]);
        s.targetUnit = t;
        assertEquals(Math.abs(delta[0]) > 5 || Math.abs(delta[1]) > 5
            ? REPLAN_UNIT : KEEP, D2PathContinuation.decide(s));
        assertEquals(p(16, 10), s.previousTargetPoint, "decision must not advance previous snapshot");
      }
    }
  }

  @Test void displacementUsesSampledLeadPointNotCurrentOwnerOrSearchTarget() {
    D2PathContinuation.State s = state(0);
    s.currentPointIndex = 1;
    s.targetPoint = p(500, 500);
    s.previousTargetPoint = p(100, 100);
    Target t = new Target();
    t.sampled = p(106, 100);
    s.targetUnit = t;
    assertEquals(REPLAN_UNIT, D2PathContinuation.decide(s));
    assertEquals(p(500, 500), s.targetPoint);
  }

  @Test void uint16DisplacementIsNotShortestWrappedDistance() {
    D2PathContinuation.State s = state(0);
    s.currentPointIndex = 1;
    Target t = new Target();
    s.targetUnit = t;
    s.previousTargetPoint = p(65535, 0);
    t.sampled = p(0, 0);
    assertEquals(REPLAN_UNIT, D2PathContinuation.decide(s));
    s.previousTargetPoint = p(0, 65535);
    assertEquals(REPLAN_UNIT, D2PathContinuation.decide(s));
  }

  @Test void staticTargetsAreSampledButDoNotReplanJustForDisplacement() {
    for (int type : new int[] {2, 3, 4, 5}) {
      D2PathContinuation.State s = state(0);
      s.currentPointIndex = 1;
      Target t = new Target();
      t.type = type;
      t.sampled = p(100, 100);
      s.targetUnit = t;
      assertEquals(KEEP, D2PathContinuation.decide(s));
      assertEquals(Arrays.asList("distance", "sample", "type"), t.calls);
      s.currentPointIndex = s.pathPointCount;
      assertEquals(REPLAN_UNIT, D2PathContinuation.decide(s), "exhaustion also replans static targets");
    }
  }

  @Test void reachedFinalGoalKeepsUnlessMovingUnitDisplacementAlreadyTriggered() {
    D2PathContinuation.State s = state(0);
    s.currentPoint = s.finalTargetPoint;
    Target t = new Target();
    s.targetUnit = t;
    assertEquals(KEEP, D2PathContinuation.decide(s));
    t.sampled = p(22, 10);
    assertEquals(REPLAN_UNIT, D2PathContinuation.decide(s));
  }

  @Test void nullPathAndMonsterZeroBudgetReturnWithoutEffects() {
    assertEquals(0, D2PathContinuation.resume(null, false, null));
    D2PathContinuation.State s = state(1);
    s.ownerFlags = 0x80;
    s.setDistance(0);
    s.setNewDistance(77);
    Effects e = new Effects();
    assertEquals(0, D2PathContinuation.resume(s, false, e));
    assertTrue(e.calls.isEmpty());
    assertEquals(0x80, s.ownerFlags);
    assertEquals(2, s.pathType);
    assertEquals(p(16, 10), s.targetPoint);
    assertEquals(2, s.currentPointIndex);
    assertEquals(77, s.nDist(), "monster gate is nDistance, not nDist");
  }

  @Test void playerZeroBudgetStillRefreshesAndSearches() {
    D2PathContinuation.State s = state(0);
    s.setDistance(0);
    Effects e = new Effects();
    assertEquals(7, D2PathContinuation.resume(s, false, e));
    assertEquals(Arrays.asList("refresh", "search:2"), e.calls);
    assertEquals(1, s.ownerFlags);
  }

  @Test void flag10SkipsMonsterGateRefreshUpdateAndConsumption() {
    D2PathContinuation.State s = state(1);
    s.pathFlags = 0x10 | 0x800;
    s.ownerFlags = 0x80;
    s.setDistance(0);
    Effects e = new Effects();
    assertEquals(7, D2PathContinuation.resume(s, true, e));
    assertEquals(Arrays.asList("search:13"), e.calls);
    assertEquals(0x80, s.ownerFlags);
    assertEquals(0, s.nDistance());
    s.setDistance(20);
    D2PathContinuation.resume(s, true, e);
    assertEquals(20, s.nDistance());
  }

  @Test void refreshThenUpdateThenSubtractThenSearchAndZeroStillSearches() {
    D2PathContinuation.State s = state(1);
    s.ownerFlags = 0x80;
    s.setNewDistance(77);
    s.setDistance(1);
    List<String> calls = new ArrayList<>();
    assertEquals(11, D2PathContinuation.resume(s, false, new D2PathContinuation.Effects() {
      @Override public void refresh(D2PathContinuation.State actual) {
        assertSame(s, actual);
        calls.add("refresh");
        assertEquals(0x80, s.ownerFlags);
        assertEquals(1, s.nDistance());
        assertEquals(p(16, 10), s.targetPoint);
      }
      @Override public int search(D2PathContinuation.State actual) {
        calls.add("search");
        assertEquals(0x81, s.ownerFlags);
        assertEquals(0, s.nDistance());
        assertEquals(77, s.nDist());
        assertEquals(77, s.nDistMax());
        assertEquals(s.finalTargetPoint, s.targetPoint);
        return 11;
      }
    }));
    assertEquals(Arrays.asList("refresh", "search"), calls);
  }

  @Test void consumedBudgetUsesIndexNotPointCountAndNeverResetsIndexItself() {
    D2PathContinuation.State s = state(1);
    s.currentPointIndex = 3;
    s.pathPointCount = 77;
    Effects e = new Effects();
    D2PathContinuation.resume(s, true, e);
    assertEquals(7, s.nDistance());
    assertEquals(3, s.currentPointIndex);
    assertEquals(77, s.pathPointCount);
  }

  @Test void towardFamilyDirectlySelects2Or13WithoutReapplyingTypeFlags() {
    for (int type : new int[] {2, 13, 15}) {
      for (boolean towardUnit : new boolean[] {false, true}) {
        D2PathContinuation.State s = state(1);
        s.pathType = type;
        s.pathFlags = 0x10 | 0x1000 | 0x20;
        s.directionOffset = -2;
        Effects e = new Effects();
        assertEquals(7, D2PathContinuation.resume(s, towardUnit, e));
        assertEquals(Arrays.asList("search:" + (towardUnit ? 13 : 2)), e.calls);
        assertEquals(towardUnit ? p(16, 10) : p(20, 10), s.targetPoint);
        assertEquals(p(20, 10), s.finalTargetPoint);
        assertEquals(0x10 | 0x1000 | 0x20, s.pathFlags);
        assertEquals(-2, s.directionOffset);
      }
    }
  }

  @Test void otherPathTypesSearchOnlyOnceEvenOnFailure() {
    for (int type = 0; type <= 17; type++) {
      if (type == 2 || type == 13 || type == 15) continue;
      D2PathContinuation.State s = state(0);
      s.pathType = type;
      Effects e = new Effects();
      e.result = 0;
      assertEquals(0, D2PathContinuation.resume(s, false, e));
      assertEquals(Arrays.asList("refresh", "search:" + type), e.calls);
      assertEquals(type, s.pathType);
      assertEquals(p(16, 10), s.targetPoint);
    }
  }

  @Test void towardFailureTries15OnceAndPreservesAllFirstSearchSideEffects() {
    D2PathContinuation.State s = state(1);
    List<Integer> types = new ArrayList<>();
    int[] refreshes = {0};
    assertEquals(19, D2PathContinuation.resume(s, false, new D2PathContinuation.Effects() {
      @Override public void refresh(D2PathContinuation.State actual) { refreshes[0]++; }
      @Override public int search(D2PathContinuation.State actual) {
        types.add(s.pathType);
        if (types.size() == 1) {
          assertEquals(p(20, 10), s.targetPoint);
          s.targetPoint = p(21, 11);
          s.finalTargetPoint = p(22, 12);
          s.previousTargetPoint = p(23, 13);
          s.currentPointIndex = 0;
          s.pathPointCount = 3;
          s.pathFlags = 0x1100;
          s.directionOffset = 4;
          s.setDistance(99);
          s.pathType = 7;
          return 0;
        }
        assertEquals(p(21, 11), s.targetPoint);
        assertEquals(p(22, 12), s.finalTargetPoint);
        assertEquals(p(23, 13), s.previousTargetPoint);
        assertEquals(0, s.currentPointIndex);
        assertEquals(3, s.pathPointCount);
        assertEquals(0x1100, s.pathFlags);
        assertEquals(4, s.directionOffset);
        assertEquals(99, s.nDistance());
        return 19;
      }
    }));
    assertEquals(Arrays.asList(2, 15), types);
    assertEquals(1, refreshes[0]);
    assertEquals(15, s.pathType);
  }

  @Test void doubleFailureReturnsZeroWithoutThirdSearchOrRestoringType() {
    D2PathContinuation.State s = state(0);
    Effects e = new Effects();
    e.result = 0;
    assertEquals(0, D2PathContinuation.resume(s, true, e));
    assertEquals(Arrays.asList("refresh", "search:13", "search:15"), e.calls);
    assertEquals(15, s.pathType);
    assertEquals(p(16, 10), s.targetPoint);
  }

  @Test void nonzeroSearchResultIsReturnedVerbatimWithoutFallbackOrTypeRestoration() {
    D2PathContinuation.State s = state(0);
    int[] calls = {0};
    assertEquals(-7, D2PathContinuation.resume(s, true, new D2PathContinuation.Effects() {
      @Override public void refresh(D2PathContinuation.State actual) {}
      @Override public int search(D2PathContinuation.State actual) {
        calls[0]++;
        s.pathType = 7;
        return -7;
      }
    }));
    assertEquals(1, calls[0]);
    assertEquals(7, s.pathType);
  }

  @Test void combinedCoordinateDecisionDispatchesTowardWithFinalGoal() {
    D2PathContinuation.State s = state(1);
    Effects e = new Effects();
    assertEquals(7, D2PathContinuation.checkAndResume(s, e));
    assertEquals(Arrays.asList("refresh", "search:2"), e.calls);
    assertEquals(p(20, 10), s.targetPoint);
    assertEquals(8, s.nDistance());
    assertFalse(s.hasRemainingPoints(), "a search return alone does not install waypoints");
  }

  @Test void combinedUnitDecisionKeepsSearchTargetAndDispatches13() {
    D2PathContinuation.State s = state(1);
    s.targetUnit = new Target();
    Effects e = new Effects();
    assertEquals(7, D2PathContinuation.checkAndResume(s, e));
    assertEquals(Arrays.asList("refresh", "search:13"), e.calls);
    assertEquals(p(16, 10), s.targetPoint);
  }

  @Test void combinedKeepDoesNotConsumeOrCallEffectsEvenWhenNoPointsRemain() {
    D2PathContinuation.State s = state(0);
    s.currentPoint = s.finalTargetPoint;
    assertEquals(1, D2PathContinuation.checkAndResume(s, null));
    assertFalse(s.hasRemainingPoints());
    assertEquals(10, s.nDistance());
  }

  @Test void newDistanceTruncatesBefore77CapAndDoesNotTouchContinuationBudget() {
    D2PathContinuation.State s = state(0);
    s.setDistance(123);
    for (int value : new int[] {-257, -256, -1, 0, 76, 77, 78, 255, 256, 300, 333, 512,
        Integer.MIN_VALUE, Integer.MAX_VALUE}) {
      s.setNewDistance(value);
      assertEquals(Math.min(value & 255, 77), s.nDist());
      assertEquals(s.nDist(), s.nDistMax());
      assertEquals(123, s.nDistance());
    }
  }

  @Test void setDistWritesOnlyLowByteAndNotMaxOrBudget() {
    D2PathContinuation.State s = state(0);
    s.setNewDistance(40);
    s.setDistance(123);
    s.setDist(-1);
    assertEquals(255, s.nDist());
    assertEquals(40, s.nDistMax());
    assertEquals(123, s.nDistance());
    s.setDist(256);
    assertEquals(0, s.nDist());
  }

  @Test void continuationAndStepAssignmentsRequireUnsignedByteInsteadOfTruncation() {
    D2PathContinuation.State s = state(0);
    for (int value : new int[] {-1, 256, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> s.setDistance(value));
      assertThrows(IllegalArgumentException.class, () -> s.setStepNum(value));
    }
    s.setNewDistance(77);
    s.setDistance(255);
    s.setStepNum(255);
    assertEquals(255, s.nDistance());
    assertEquals(255, s.nStepNum());
    assertEquals(77, s.nDist());
    assertEquals(77, s.nDistMax());
  }

  @Test void addToDistanceSaturatesOnlyAfterNativeInt32Addition() {
    D2PathContinuation.State s = state(0);
    for (int value : new int[] {0, 1, 127, 255}) {
      for (int amount : new int[] {-1000, -256, -1, 0, 1, 255, 1000,
          Integer.MIN_VALUE, Integer.MAX_VALUE}) {
        s.setDistance(value);
        s.addToDistance(amount);
        long sum = (long) value + amount;
        int wrapped = (int) (sum & 0xffffffffL);
        assertEquals(wrapped < 0 ? 0 : wrapped > 255 ? 255 : wrapped, s.nDistance());
      }
    }
    s.setDistance(1);
    s.addToDistance(Integer.MAX_VALUE);
    assertEquals(0, s.nDistance(), "do not use long saturation (which would produce 255)");
  }

  @Test void allByteBudgetsAndAllNativeWaypointIndicesConsumeIndependently() {
    D2PathContinuation.State s = state(0);
    s.pathType = 1;
    s.setNewDistance(77);
    Effects e = new Effects();
    for (int budget = 0; budget <= 255; budget++) {
      for (int index = 0; index <= 78; index++) {
        s.setDistance(budget);
        s.currentPointIndex = index;
        D2PathContinuation.resume(s, false, e);
        assertEquals(Math.max(0, budget - index), s.nDistance());
        assertEquals(77, s.nDist());
        assertEquals(77, s.nDistMax());
      }
    }
    assertEquals(256 * 79 * 2, e.calls.size());
  }

  @Test void missilesAreRejectedRatherThanApplyingNonMissilePolicy() {
    D2PathContinuation.State s = state(0);
    s.pathFlags = 0x40000;
    Effects e = new Effects();
    assertThrows(IllegalArgumentException.class, () -> D2PathContinuation.decide(s));
    assertThrows(IllegalArgumentException.class, () -> D2PathContinuation.resume(s, false, e));
    assertTrue(e.calls.isEmpty());
  }
}
