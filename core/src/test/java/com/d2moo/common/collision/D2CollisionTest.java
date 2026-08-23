package com.d2moo.common.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class D2CollisionTest {
  @Test
  void preservesNativeCompositeMasks() {
    assertEquals(0x0027, D2Collision.COLLIDE_MASK_INVALID);
    assertEquals(0x1C09, D2Collision.COLLIDE_MASK_PLAYER_PATH);
    assertEquals(0x3E01, D2Collision.COLLIDE_MASK_SPAWN);
    assertEquals(0x3F11, D2Collision.COLLIDE_MASK_PLACEMENT);
  }

  @Test
  void createsNativeOddAndEvenBoundingBoxes() {
    D2Collision.BoundingBox odd = D2Collision.createBoundingBox(10, 20, 3, 3);
    assertEquals(9, odd.left);
    assertEquals(19, odd.bottom);
    assertEquals(11, odd.right);
    assertEquals(21, odd.top);
    assertEquals(3, odd.width());
    assertEquals(3, odd.height());

    D2Collision.BoundingBox even = D2Collision.createBoundingBox(10, 20, 2, 4);
    assertEquals(9, even.left);
    assertEquals(18, even.bottom);
    assertEquals(10, even.right);
    assertEquals(21, even.top);
    assertTrue(even.contains(10, 20));
    assertFalse(even.contains(11, 20));
  }

  @Test
  void keepsMaskOperationsUnsigned16Bit() {
    assertEquals(D2Collision.COLLIDE_DOOR,
        D2Collision.checkMask(0xFFFF0800, D2Collision.COLLIDE_DOOR));
    assertEquals(0xFFFF, D2Collision.setMask(0x1FFFF, D2Collision.COLLIDE_WALL));
    assertEquals(0xF7FF, D2Collision.resetMask(0xFFFF, D2Collision.COLLIDE_DOOR));
  }

  @Test
  void rejectsEmptyBoundingBoxes() {
    assertThrows(IllegalArgumentException.class,
        () -> D2Collision.createBoundingBox(0, 0, 0, 1));
  }
}
