package com.d2moo.common.path;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathTypesTest {
  private static D2PathResult.State state(int owner) {
    D2PathContinuation.State path = new D2PathContinuation.State(owner);
    path.pathType = 7; path.previousPathType = 1; path.directionOffset = 99;
    path.setNewDistance(77); path.setDistance(55); path.setStepNum(3);
    D2PathResult.State state = new D2PathResult.State(path);
    state.velocity = 256; state.previousVelocity = 777;
    return state;
  }

  @Test void allEighteenEntriesApplyTheirTableFlagsOffsetsAndOldFlagSaveGates() {
    int[] flags = {0x21900,0x1900,0,0,0x60000,0,0,0x21900,0x1e600,0x1e800,0x60000,0x1e604,0,0x100,0x60000,0x800,0,0};
    int[] offsets = {0,0,0,0,0,2,-2,0,0,0,0,0,-4,0,0,0,0,0};
    for (int type = 0; type < 18; type++) for (int old : new int[] {0,0x4000,0x10000,0x14000,-1,0xa5f800a5}) {
      D2PathResult.State s = state(1); s.path.pathFlags = old;
      D2PathTypes.setType(s, type);
      assertEquals(type, s.path.pathType);
      assertEquals(flags[type] | (old & 0xfff800ff), s.path.pathFlags);
      assertEquals(offsets[type], s.path.directionOffset);
      assertEquals((flags[type] & 0x2000) != 0 && (old & 0x4000) == 0 ? 7 : 1, s.path.previousPathType);
      assertEquals((flags[type] & 0x8000) != 0 && (old & 0x10000) == 0 ? 256 : 777, s.previousVelocity);
      assertEquals(256, s.velocity); assertEquals(55, s.path.nDistance());
      assertEquals(77, s.path.nDist()); assertEquals(77, s.path.nDistMax()); assertEquals(3, s.path.nStepNum());
    }
  }

  @Test void nestedKnockbackAndLeapPreserveTheOriginalTypeAndVelocity() {
    D2PathResult.State s = state(1);
    D2PathTypes.setType(s, 8);
    s.velocity = 1024;
    D2PathTypes.setType(s, 9);
    assertEquals(7, s.path.previousPathType); assertEquals(256, s.previousVelocity);
    assertEquals(1024, s.velocity);
    D2PathTypes.resetToPreviousType(s);
    assertEquals(7, s.path.pathType); assertEquals(256, s.velocity);
    assertEquals(0x21900, s.path.pathFlags);
  }

  @Test void playerTowardAssertionPrecedesAllWritesButDirectContinuationAssignmentRemainsAllowed() {
    D2PathResult.State s = state(0); s.path.pathFlags = -1;
    assertThrows(IllegalStateException.class, () -> D2PathTypes.setType(s, 2));
    assertEquals(7, s.path.pathType); assertEquals(-1, s.path.pathFlags); assertEquals(99, s.path.directionOffset);
    s.path.pathType = 2; // 10236 does this directly; must not accidentally acquire SetType's assertion/flags.
    assertEquals(2, s.path.pathType);
  }

  @Test void savedKnockbackAssertionOccursAfterAssignmentsAndDoesNotRollback() {
    for (int previous : new int[] {8,11}) {
      D2PathResult.State s = state(1); s.path.previousPathType = previous; s.path.pathFlags = 0x4001;
      assertThrows(IllegalStateException.class, () -> D2PathTypes.setType(s, 5));
      assertEquals(5, s.path.pathType); assertEquals(1, s.path.pathFlags); assertEquals(2, s.path.directionOffset);
      assertEquals(previous, s.path.previousPathType);
    }
  }

  @Test void playerResetAlwaysUsesStraightEvenWithoutPreviousTypeFlag() {
    D2PathResult.State s = state(0); s.path.pathType = 12; s.path.previousPathType = 2; s.path.pathFlags = 0x8000;
    D2PathTypes.resetToPreviousType(s);
    assertEquals(7, s.path.pathType); assertEquals(777, s.velocity); assertEquals(0, s.path.directionOffset);
  }

  @Test void nonPlayerResetRestoresVelocityIndependentlyOfTypeRestoreGate() {
    D2PathResult.State s = state(1); s.path.pathFlags = 0x8000; s.path.pathType = 12;
    D2PathTypes.resetToPreviousType(s);
    assertEquals(777, s.velocity); assertEquals(12, s.path.pathType); assertEquals(99, s.path.directionOffset);
    s.path.pathFlags = 0x2000; s.path.previousPathType = 6; s.velocity = 444;
    D2PathTypes.resetToPreviousType(s);
    assertEquals(444, s.velocity); assertEquals(6, s.path.pathType); assertEquals(-2, s.path.directionOffset);
  }

  @Test void resetVelocityWriteSurvivesAFollowingSetTypeAssertion() {
    D2PathResult.State s = state(0); s.path.pathFlags = 0x8000; s.path.previousPathType = 8;
    assertThrows(IllegalStateException.class, () -> D2PathTypes.resetToPreviousType(s));
    assertEquals(777, s.velocity); assertEquals(7, s.path.pathType); assertEquals(0x21900, s.path.pathFlags);
  }

  @Test void nullEntriesAndAliasesAreDescribedWithoutPretendingEveryEntryIsImplemented() {
    for (int type : new int[] {4,10,14,17}) assertEquals(D2PathTypes.Entry.NONE, D2PathTypes.entry(type));
    for (int type : new int[] {2,5,6,13}) assertEquals(D2PathTypes.Entry.TOWARD, D2PathTypes.entry(type));
    assertEquals(D2PathTypes.Entry.TOWARD_ALIAS, D2PathTypes.entry(3));
    assertEquals(D2PathTypes.entry(0), D2PathTypes.entry(16));
    assertEquals(D2PathTypes.Entry.STRAIGHT, D2PathTypes.entry(7));
  }

  @Test void invalidTypesAndMissingOwnerAreRejectedWithoutGuessingNativeMemory() {
    D2PathResult.State s = state(1);
    for (int type : new int[] {-1,18,Integer.MAX_VALUE}) {
      assertThrows(IllegalArgumentException.class, () -> D2PathTypes.entry(type));
      assertThrows(IllegalArgumentException.class, () -> D2PathTypes.setType(s, type));
    }
    assertEquals(7, s.path.pathType);
    D2PathResult.State missing = state(-1); missing.path.pathFlags = 0x8000;
    assertThrows(IllegalStateException.class, () -> D2PathTypes.resetToPreviousType(missing));
    assertEquals(256, missing.velocity);
  }

  @Test void installed110fDispatchFlagsAndOffsetsMatchAllThreeStaticTables() throws Exception {
    ByteBuffer pe = D2PathUnitsTest.originalDll("D2Common.dll",
        "B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB");
    int entries = D2PathUnitsTest.fileOffset(pe, 0x6fdd1f40L);
    int flags = D2PathUnitsTest.fileOffset(pe, 0x6fdd2088L);
    int offsets = D2PathUnitsTest.fileOffset(pe, 0x6fdd20d0L);
    for (int i = 0; i < 18; i++) {
      assertEquals(pe.getInt(entries + 4*i), D2PathTypes.entry(i).address);
      assertEquals(pe.getInt(flags + 4*i), D2PathTypes.flags(i));
      assertEquals(pe.getInt(offsets + 4*i), D2PathTypes.directionOffset(i));
    }
  }
}
