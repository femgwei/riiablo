package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NativeMonsterModeElementsTest {
  @ParameterizedTest
  @CsvSource({"0,0,100,0,false", "4,5,100,0,false", "4,4,0,0,false",
      "4,4,50,49,true", "4,4,50,50,false", "4,4,100,99,true", "4,4,255,99,true"})
  void modeAndChanceHaveNativeDrawBoundaries(int rowMode, int mode, int chance,
      int roll, boolean expected) {
    int[] draws = {0};
    assertEquals(expected, NativeMonsterModeElements.selects(rowMode, mode, chance, n -> {
      assertEquals(100, n); draws[0]++; return roll;
    }));
    assertEquals(rowMode > 0 && rowMode == mode && chance > 0 && chance < 100 ? 1 : 0,
        draws[0]);
  }

  @ParameterizedTest
  @CsvSource({"0,1,0,0", "1,2,0,0", "2,5,0,0", "3,3,25,0", "4,4,0,50"})
  void randomTypeUsesNativeNumberingAndZeroLengthFallback(int roll, int type,
      int cold, int poison) {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(10, 3, 4, 0, 0, n -> { assertEquals(5, n); return roll; });
    assertEquals(type == DAMAGE_POISON ? 30 : 3, p.minimum[type]);
    assertEquals(type == DAMAGE_POISON ? 40 : 4, p.maximum[type]);
    assertEquals(cold, p.coldLength); assertEquals(poison, p.poisonLength);
  }

  @Test void laterSuccessfulRowOverwritesEvenWhenSmallerOrZero() {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(4, 10, 20, 100, 0, n -> fail("no RNG for cold"));
    p.set(4, 2, 3, 5, 0, n -> fail("no RNG for cold"));
    assertEquals(2, p.minimum[DAMAGE_COLD]); assertEquals(5, p.coldLength);
    p.set(4, 0, 0, 0, 0, n -> 0);
    assertEquals(0, p.maximum[DAMAGE_COLD]); assertEquals(0, p.coldLength);
    assertEquals(3, p.matchedRows);
  }

  @Test void poisonScalesBeforeTimesTenAndLengthBeforeTimesTwo() {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(5, 15, 31, 17, 8, n -> fail("no RNG for poison"));
    assertEquals(150, p.minimum[DAMAGE_POISON]);
    assertEquals(320, p.maximum[DAMAGE_POISON]);
    assertEquals(36, p.poisonLength);
  }

  @Test void randomFallbackOccursAfterMultiplayerScaling() {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(10, 10, 20, 0, 128, n -> 3);
    assertEquals(25, p.coldLength);
  }

  @Test void independentChannelsSurviveButNewRefreshHasNoOldStats() {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(1, 2, 3, 0, 0, n -> 0); p.set(3, 4, 5, 0, 0, n -> 0);
    assertEquals(2, p.minimum[DAMAGE_FIRE]); assertEquals(4, p.minimum[DAMAGE_MAGIC]);
    assertEquals(0, new NativeMonsterModeElements().maximum[DAMAGE_MAGIC]);
  }

  @Test void endpointOrderIsNotNormalizedInTheTemporaryStatList() {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(1, 20, 10, 0, 0, n -> 0);
    assertEquals(20, p.minimum[DAMAGE_FIRE]); assertEquals(10, p.maximum[DAMAGE_FIRE]);
  }

  @Test void originalModeFlagsAreAddedNotShifted() {
    assertEquals(0x40, NativeMonsterModeElements.tableFlags(0));
    assertEquals(0x41, NativeMonsterModeElements.tableFlags(1));
    assertEquals(0x42, NativeMonsterModeElements.tableFlags(2));
    assertThrows(IllegalArgumentException.class, () -> NativeMonsterModeElements.tableFlags(3));
  }

  @ParameterizedTest
  @CsvSource({"9", "11", "12"})
  void unsupportedSpecialTypesDoNotInventOneOfTheFiveElements(int type) {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(type, 10, 20, 25, 0, n -> fail("not rand"));
    assertEquals(0, p.matchedRows);
    for (int value : p.maximum) assertEquals(0, value);
  }

  @Test void resourceRowsSetIndependentDrainEndpoints() {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    for (int i = 0; i < 3; i++) {
      p.set(6 + i, 10, 20, 25, 128, n -> fail("drain does not select random type"));
      assertEquals(20, p.drainMinimum[i]);
      assertEquals(40, p.drainMaximum[i]);
    }
    p.set(7, 2, 3, 0, 0, n -> 0);
    assertEquals(2, p.drainMinimum[1]);
    assertEquals(4, p.matchedRows);
    assertFalse(p.unsupportedType);
    for (int value : p.maximum) assertEquals(0, value);
  }
}
