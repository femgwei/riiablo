package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
}
