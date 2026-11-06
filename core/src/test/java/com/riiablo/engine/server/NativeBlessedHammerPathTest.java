package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.missile.NativeBlessedHammerPath;
import org.junit.jupiter.api.Test;

class NativeBlessedHammerPathTest {
  @Test
  void acceptedNativeCellsRetryDuplicatesAndTranslateAtCellCentres() {
    Vector2 origin = new Vector2(10, 20);
    int[][] first = {{10,21}, {9,21}, {8,21}, {8,20}, {8,19}, {8,18}, {8,17}, {9,17}};
    Vector2 previous = new Vector2(origin);
    for (int i = 1; i <= NativeBlessedHammerPath.POINTS; i++) {
      Vector2 point = NativeBlessedHammerPath.point(origin, i, new Vector2());
      assertEquals((float) Math.rint(point.x), point.x);
      assertEquals((float) Math.rint(point.y), point.y);
      assertFalse(previous.equals(point), "adjacent accepted native points must differ");
      if (i <= first.length) assertEquals(new Vector2(first[i-1][0], first[i-1][1]), point);
      Vector2 translated = NativeBlessedHammerPath.point(new Vector2(0, 0), i, new Vector2());
      assertEquals(point, translated.add(origin));
      previous.set(point);
    }
  }
}
