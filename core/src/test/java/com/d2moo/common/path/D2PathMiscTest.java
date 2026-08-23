package com.d2moo.common.path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class D2PathMiscTest {
  @Test
  void preservesNativeEightDirectionOffsetsAndChoices() {
    int[][] offsets = {
        {1, 0}, {1, 1}, {0, 1}, {-1, 1},
        {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };
    for (int direction = 0; direction < offsets.length; direction++) {
      assertEquals(offsets[direction][0], D2PathMisc.directionOffsetX(direction));
      assertEquals(offsets[direction][1], D2PathMisc.directionOffsetY(direction));
    }
    assertThrows(IndexOutOfBoundsException.class, () -> D2PathMisc.directionOffsetX(8));

    D2Path.Point origin = point(0, 0);
    assertEquals(0, D2PathMisc.primaryDirection(origin, point(10, 0)));
    assertEquals(1, D2PathMisc.primaryDirection(origin, point(10, 10)));
    assertEquals(2, D2PathMisc.primaryDirection(origin, point(0, 10)));
    assertEquals(4, D2PathMisc.primaryDirection(origin, point(-10, 0)));
    assertEquals(6, D2PathMisc.primaryDirection(origin, point(0, -10)));
    assertArrayEquals(new int[] {0, 1, 7},
        D2PathMisc.directions(origin, point(10, 0)));
    assertArrayEquals(new int[] {0, 2, 6},
        D2PathMisc.cardinalDirections(origin, point(10, 0)));
  }

  @Test
  void matchesNativeShortAndLongDistanceRules() {
    D2Path.Point origin = point(0, 0);
    assertEquals(0, D2PathMisc.approximateDistance(origin, origin));
    assertEquals(0, D2PathMisc.approximateDistance(origin, point(1, 1)));
    assertEquals(1, D2PathMisc.approximateDistance(origin, point(3, 0)));
    assertEquals(3, D2PathMisc.approximateDistance(origin, point(4, 0)));
    assertEquals(16, D2PathMisc.approximateDistance(origin, point(8, 0)));
    assertEquals(24, D2PathMisc.approximateDistance(origin, point(8, 8)));
  }

  @Test
  void simplifiesNativeStraightRunsAndTurns() {
    D2Path.Point start = point(0, 0);
    assertEquals(Collections.singletonList(point(3, 0)),
        D2PathMisc.simplifyToLines(start,
            Arrays.asList(point(1, 0), point(2, 0), point(3, 0))));
    assertEquals(Arrays.asList(point(2, 0), point(2, 2)),
        D2PathMisc.simplifyToLines(start,
            Arrays.asList(point(1, 0), point(2, 0), point(2, 1), point(2, 2))));
    assertEquals(Collections.singletonList(point(1, 1)),
        D2PathMisc.simplifyToLines(start, Collections.singletonList(point(1, 1))));
  }

  @Test
  void buildsBoundedNativeBresenhamLines() {
    D2PathMisc.LineResult line = D2PathMisc.bresenhamLine(
        point(0, 0), point(3, 1), 4);
    assertEquals(1, line.majorDirection());
    assertEquals(Arrays.asList(point(1, 0), point(2, 0), point(3, 1)), line.points());

    D2PathMisc.LineResult south = D2PathMisc.bresenhamLine(
        point(2, 2), point(2, 5), 4);
    assertEquals(2, south.majorDirection());
    assertEquals(point(2, 5), south.points().get(south.points().size() - 1));

    assertTrue(D2PathMisc.bresenhamLine(point(0, 0), point(3, 0), 3)
        .points().isEmpty());
    assertTrue(D2PathMisc.bresenhamLine(point(0, 0), point(0, 0), 10)
        .points().isEmpty());
  }

  private static D2Path.Point point(int x, int y) {
    return new D2Path.Point(x, y);
  }
}
