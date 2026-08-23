package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;

class Box2DPhysicsTest {
  @Test
  void walkBlockingTilesCollideWithPlayersAndMonsters() {
    assertEquals(
        Box2DPhysics.CATEGORY_PLAYER | Box2DPhysics.CATEGORY_MONSTER,
        Box2DPhysics.unitMaskForTileFlags(DT1.Tile.FLAG_BLOCK_WALK));
  }

  @Test
  void playerOnlyTilesDoNotBlockMonsters() {
    assertEquals(
        Box2DPhysics.CATEGORY_PLAYER,
        Box2DPhysics.unitMaskForTileFlags(DT1.Tile.FLAG_BLOCK_PLAYER_WALK));
  }

  @Test
  void nonMovementFlagsDoNotCreatePhysicalWalls() {
    assertEquals(0, Box2DPhysics.unitMaskForTileFlags(
        DT1.Tile.FLAG_BLOCK_LIGHT_LOS | DT1.Tile.FLAG_BLOCK_LIGHT));
  }

  @Test
  void waypointsDoNotCreateAPlayerBlockingObjectFixture() {
    Objects.Entry waypoint = new Objects.Entry();
    waypoint.SubClass = Engine.Object.SUBCLASS_WAYPOINT;

    assertTrue(Box2DPhysics.isNonBlockingObject(waypoint));
  }

  @Test
  void ordinaryInteractiveObjectsKeepTheirConfiguredCollision() {
    Objects.Entry object = new Objects.Entry();

    assertFalse(Box2DPhysics.isNonBlockingObject(object));
    assertFalse(Box2DPhysics.isNonBlockingObject(null));
  }
}
