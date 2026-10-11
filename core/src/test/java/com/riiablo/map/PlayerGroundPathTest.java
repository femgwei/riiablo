package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Test;

class PlayerGroundPathTest {
  private Map map() {
    Map map = new Map(0, 0);
    Map.Zone zone = new Map.Zone();
    zone.map = map;
    zone.width = zone.height = 12;
    zone.tilesX = zone.tilesY = 3;
    zone.flags = new byte[144];
    map.zones.add(zone);
    for (int x = 0; x < 12; x++) zone.or(x, 6, DT1.Tile.FLAG_BLOCK_PLAYER_WALK);
    return map;
  }

  @Test
  void playerRayChecksFootprintAndEndpointAgainstNoPlayerTerrain() {
    Map map = map();
    int flags = NativeGroundPathProfile.PLAYER.terrainFlags;
    Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
    // Centre stays clear, but a size-two footprint touches the forbidden column.
    assertTrue(map.isBlockedForPath(6, 6, flags, false));
    assertFalse(map.isBlockedForPath(6, 6,
        NativeGroundPathProfile.MONSTER.terrainFlags, false));
    assertTrue(map.castRay(new Ray<>(new Vector2(3, 5), new Vector2(9, 7)),
        flags, 1, collision));
  }

  @Test
  void flyingMonsterUsesMissileBarrierProfile() {
    MonStats.Entry stats = new MonStats.Entry();
    stats.flying = true;
    assertEquals(DT1.Tile.FLAG_BLOCK_JUMP,
        NativeGroundPathProfile.forUnit(false, stats).terrainFlags);
  }

  @Test
  void nativeSpecialBasesHaveNoDynamicPresence() {
    MonStats.Entry stats = new MonStats.Entry();
    stats.BaseId = "Wraith1";
    assertTrue(NativeGroundPathProfile.hasNoPresence(stats));
  }

}
