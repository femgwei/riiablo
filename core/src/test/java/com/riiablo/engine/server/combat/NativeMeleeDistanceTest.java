package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeMeleeDistanceTest {
  @Test
  void reproducesNativeSmallDistanceTableAndSizeCorrections() {
    assertEquals(0, NativeMeleeDistance.between(10, 10, 2, 10, 10, 2));
    assertEquals(2, NativeMeleeDistance.between(0, 0, 2, 4, 0, 2));
    assertEquals(1, NativeMeleeDistance.between(0, 0, 3, 4, 0, 2));
    assertEquals(3, NativeMeleeDistance.between(0, 0, 1, 4, 0, 2));
  }

  @Test
  void reproducesNativeLargeDistanceApproximation() {
    assertEquals(18, NativeMeleeDistance.between(0, 0, 2, 10, 4, 2));
    assertEquals(18, NativeMeleeDistance.between(10, 4, 2, 0, 0, 2));
  }

  @Test
  void appliesNativeMeleeRangeBoundary() {
    assertTrue(NativeMeleeDistance.isInRange(0, 0, 2, 5, 0, 2, 0, 3));
    assertFalse(NativeMeleeDistance.isInRange(0, 0, 2, 6, 0, 2, 0, 3));
    assertTrue(NativeMeleeDistance.isInRange(0, 0, 2, 0, 0, 2, 0, 0));
  }
}
