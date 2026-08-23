package com.d2moo.common.path;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class D2PathStepTest {
  @Test
  void convertsNativeUnsignedFp16Coordinates() {
    assertEquals(0x00008000, D2PathStep.toFp16Center(0));
    assertEquals(0x00018000, D2PathStep.toFp16Center(1));
    assertEquals(0xFFFF8000, D2PathStep.toFp16Center(0xFFFF));
    assertEquals(0xFFFF, D2PathStep.fromFp16(0xFFFF8000));
    assertEquals(0x12348000, D2PathStep.fitToFp16Center(0x12345678));
    assertEquals(63, D2PathStep.normalizeFacing(-1));
    assertEquals(0, D2PathStep.normalizeFacing(64));
  }

  @Test
  void matchesNativeCardinalAndDiagonalFacingBias() {
    assertEquals(56, D2PathStep.computeDirection(10, 10, 11, 10));
    assertEquals(7, D2PathStep.computeDirection(10, 10, 10, 11));
    assertEquals(23, D2PathStep.computeDirection(10, 10, 9, 10));
    assertEquals(40, D2PathStep.computeDirection(10, 10, 10, 9));
    assertEquals(0, D2PathStep.computeDirection(10, 10, 11, 11));
    assertEquals(15, D2PathStep.computeDirection(10, 10, 9, 11));
    assertEquals(32, D2PathStep.computeDirection(10, 10, 9, 9));
    assertEquals(47, D2PathStep.computeDirection(10, 10, 11, 9));
  }

  @Test
  void createsNativeLengthDirectionVectors() {
    D2PathStep.DirectionVector east = D2PathStep.directionVector(
        D2PathStep.toFp16Center(0), D2PathStep.toFp16Center(0),
        D2PathStep.toFp16Center(1), D2PathStep.toFp16Center(0));
    assertEquals(4096, east.x);
    assertEquals(0, east.y);
    assertEquals(56, east.direction);

    D2PathStep.DirectionVector diagonal = D2PathStep.directionVector(
        D2PathStep.toFp16Center(0), D2PathStep.toFp16Center(0),
        D2PathStep.toFp16Center(1), D2PathStep.toFp16Center(1));
    assertEquals(2896, diagonal.x);
    assertEquals(2896, diagonal.y);
    assertEquals(0, diagonal.direction);
  }

  @Test
  void preservesNativeTangentFacingThreshold() {
    D2PathStep.DirectionVector below = D2PathStep.directionVector(0, 0, 12, 127);
    assertEquals(385, below.x);
    assertEquals(4077, below.y);
    assertEquals(7, below.direction);

    D2PathStep.DirectionVector above = D2PathStep.directionVector(0, 0, 13, 127);
    assertEquals(417, above.x);
    assertEquals(4074, above.y);
    assertEquals(6, above.direction);
    assertEquals(7, D2PathStep.computeDirectionFromPreciseCoords(100, 100, 100, 100));
  }

  @Test
  void fitsStepDeltasWithoutChangingNativeBoundaryValues() {
    assertEquals(point(65536, -65536), D2PathStep.fitStepDelta(65536, -65536));
    assertEquals(point(32768, 16384), D2PathStep.fitStepDelta(65537, 32769));
    assertEquals(point(-32769, 16384), D2PathStep.fitStepDelta(-65537, 32769));
    assertEquals(point(32768, 65536), D2PathStep.fitStepDelta(131072, 262144));
  }

  private static D2Path.Point point(int x, int y) {
    return new D2Path.Point(x, y);
  }
}
