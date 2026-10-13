package com.riiablo.engine.server;

import com.riiablo.map.DT1;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WarpArrivalCollisionTest {
  @Test void nativeEntranceCannotRecoverByJumpingToTheZoneCentre() {
    com.riiablo.map.Map.Zone destination = new com.riiablo.map.Map.Zone();
    assertTrue(WarpInteractor.allowsCompatibilityArrivalFallback(destination));
    destination.setNativeTileGrid(new com.riiablo.drlg.TileGrid(4, 4), 0);
    assertFalse(WarpInteractor.allowsCompatibilityArrivalFallback(destination));
    assertFalse(WarpInteractor.allowsCompatibilityArrivalFallback(null));
  }
  @Test void playerLandingRejectsPlayerOnlyTerrainJustLikeMovement() {
    assertEquals(NativeGroundPathProfile.PLAYER.terrainFlags,
        WarpInteractor.arrivalCollisionMask(true));
    assertNotEquals(0, WarpInteractor.arrivalCollisionMask(true) & DT1.Tile.FLAG_BLOCK_PLAYER_WALK);
    assertEquals(NativeGroundPathProfile.MONSTER.terrainFlags,
        WarpInteractor.arrivalCollisionMask(false));
  }
}
