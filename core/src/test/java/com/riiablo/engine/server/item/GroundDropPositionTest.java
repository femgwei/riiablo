package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

class GroundDropPositionTest {
  @Test
  void choosesAnUnusedSubtileNearTheDropOrigin() {
    Set<Long> occupied = new HashSet<>();
    occupied.add(GroundDropPosition.key(10, 10));
    occupied.add(GroundDropPosition.key(9, 10));

    Vector2 result = GroundDropPosition.findFree(10.2f, 10.1f, occupied, 2, new Vector2());

    assertEquals(9, Math.round(result.x));
    assertEquals(9, Math.round(result.y));
  }

  @Test
  void preservesTheNativeFallbackWhenTheSearchAreaIsFull() {
    Set<Long> occupied = new HashSet<>();
    for (int y = 8; y <= 12; y++) {
      for (int x = 8; x <= 12; x++) {
        occupied.add(GroundDropPosition.key(x, y));
      }
    }

    Vector2 result = GroundDropPosition.findFree(10, 10, occupied, 2, new Vector2());

    assertEquals(10, Math.round(result.x));
    assertEquals(10, Math.round(result.y));
  }
}
