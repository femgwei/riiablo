package com.d2moo.common.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class D2CollisionGridTest {
  @Test
  void storesUnsigned16BitMasksAtWorldCoordinates() {
    D2CollisionGrid grid = new D2CollisionGrid(100, 200, 4, 3);

    grid.setMask(101, 201, 0x1FFFF);
    assertEquals(0xFFFF, grid.flagsAt(101, 201));
    assertEquals(D2Collision.COLLIDE_DOOR,
        grid.checkMask(101, 201, D2Collision.COLLIDE_DOOR));
    grid.resetMask(101, 201, D2Collision.COLLIDE_DOOR);
    assertEquals(0xF7FF, grid.flagsAt(101, 201));
    assertEquals(D2Collision.COLLIDE_MASK_INVALID, grid.checkMask(99, 200, 0));
  }

  @Test
  void appliesNativeSizeFootprints() {
    D2CollisionGrid grid = new D2CollisionGrid(0, 0, 7, 7);

    grid.setMaskWithSize(3, 3, D2Collision.UNIT_SIZE_SMALL, D2Collision.COLLIDE_MONSTER);
    assertEquals(D2Collision.COLLIDE_MONSTER, grid.flagsAt(2, 3));
    assertEquals(D2Collision.COLLIDE_MONSTER, grid.flagsAt(3, 2));
    assertEquals(0, grid.flagsAt(2, 2));

    grid.setMaskWithSize(3, 3, D2Collision.UNIT_SIZE_BIG, D2Collision.COLLIDE_PLAYER);
    assertEquals(D2Collision.COLLIDE_PLAYER, grid.flagsAt(2, 2));
    assertEquals(D2Collision.COLLIDE_PLAYER, grid.flagsAt(4, 4));
  }

  @Test
  void addsAndRemovesNativePatternPresenceBits() {
    D2CollisionGrid grid = new D2CollisionGrid(0, 0, 7, 7);

    grid.setMaskWithPattern(3, 3, D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
        D2Collision.COLLIDE_MONSTER);
    assertEquals(D2Collision.COLLIDE_MONSTER | D2Collision.COLLIDE_NO_PATH,
        grid.flagsAt(3, 3));
    assertEquals(D2Collision.COLLIDE_MONSTER, grid.flagsAt(2, 3));

    grid.resetMaskWithPattern(3, 3, D2Collision.PATTERN_SMALL_UNIT_PRESENCE,
        D2Collision.COLLIDE_MONSTER);
    assertEquals(0, grid.flagsAt(3, 3));
    assertEquals(0, grid.flagsAt(2, 3));
  }

  @Test
  void tryMoveOnlyRollsBackForNativeHardBarriers() {
    D2CollisionGrid grid = new D2CollisionGrid(0, 0, 8, 4);
    grid.setMaskWithSize(1, 1, D2Collision.UNIT_SIZE_POINT, D2Collision.COLLIDE_PLAYER);
    grid.setMask(3, 1, D2Collision.COLLIDE_OBJECT);

    int objectHit = grid.tryMoveUnit(1, 1, 3, 1, D2Collision.UNIT_SIZE_POINT,
        D2Collision.COLLIDE_PLAYER, D2Collision.COLLIDE_OBJECT);
    assertEquals(D2Collision.COLLIDE_OBJECT, objectHit);
    assertEquals(0, grid.flagsAt(1, 1));
    assertEquals(D2Collision.COLLIDE_OBJECT | D2Collision.COLLIDE_PLAYER, grid.flagsAt(3, 1));

    grid.setMask(5, 1, D2Collision.COLLIDE_WALL);
    int wallHit = grid.tryMoveUnit(3, 1, 5, 1, D2Collision.UNIT_SIZE_POINT,
        D2Collision.COLLIDE_PLAYER, D2Collision.COLLIDE_WALL);
    assertEquals(D2Collision.COLLIDE_WALL, wallHit);
    assertEquals(D2Collision.COLLIDE_OBJECT | D2Collision.COLLIDE_PLAYER, grid.flagsAt(3, 1));
    assertEquals(D2Collision.COLLIDE_WALL, grid.flagsAt(5, 1));
  }

  @Test
  void tryTeleportRestoresOldPatternOnAnyCollision() {
    D2CollisionGrid grid = new D2CollisionGrid(0, 0, 10, 5);
    grid.setMaskWithPattern(2, 2, D2Collision.PATTERN_SMALL_NO_PRESENCE,
        D2Collision.COLLIDE_PLAYER);
    grid.setMask(7, 2, D2Collision.COLLIDE_OBJECT);

    int hit = grid.tryTeleportUnit(2, 2, 7, 2,
        D2Collision.PATTERN_SMALL_NO_PRESENCE,
        D2Collision.COLLIDE_PLAYER, D2Collision.COLLIDE_OBJECT);

    assertEquals(D2Collision.COLLIDE_OBJECT, hit);
    assertEquals(D2Collision.COLLIDE_PLAYER, grid.flagsAt(2, 2));
    assertEquals(D2Collision.COLLIDE_OBJECT, grid.flagsAt(7, 2));
  }
}
