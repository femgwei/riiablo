package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Test;

class PlayerGroundPathTest {
  @Test void fractionalCornerStartCanWalkToItsCentreThenTakeTheNativeDiagonal() {
    Map map = map();
    Map.Zone zone = map.zones.first();
    java.util.Arrays.fill(zone.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    for (int center : new int[] {4, 5}) for (int[] offset : new int[][] {{0,0},{-1,0},{1,0},{0,-1},{0,1}})
      zone.flags[(center + offset[1]) * zone.width + center + offset[0]] = 0;
    int flags = NativeGroundPathProfile.PLAYER.terrainFlags;
    Vector2 start = new Vector2(4.69f, 4.535f);
    Vector2 sourceCentre = new Vector2(5, 5), next = new Vector2(4, 4);
    Collision<Vector2> hit = new Collision<>(new Vector2(), new Vector2());
    assertTrue(map.castRay(new Ray<>(start, next), flags, 2, hit));
    assertFalse(map.castRay(new Ray<>(start, sourceCentre), flags, 2, hit));
    assertFalse(map.castRay(new Ray<>(sourceCentre, next), flags, 2, hit));
    assertTrue(map.isBlockedForPath(5, 4, flags, 2, false), "do not exempt the forbidden side footprint");
  }

  @Test void nativeDiagonalChecksTheTargetCrossWithoutExpandingItIntoSideSquares() {
    Map map = map();
    Map.Zone zone = map.zones.first();
    java.util.Arrays.fill(zone.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    for (int center = 2; center <= 5; center++) {
      for (int[] offset : new int[][] {{0,0}, {-1,0}, {1,0}, {0,-1}, {0,1}}) {
        zone.flags[(center + offset[1]) * zone.width + center + offset[0]] = 0;
      }
    }
    int flags = NativeGroundPathProfile.PLAYER.terrainFlags;
    assertFalse(map.isBlockedForPath(3, 3, flags, 2, false));
    assertTrue(map.isBlockedForPath(3, 2, flags, 2, false), "side cross is not the candidate pattern");
    com.badlogic.gdx.utils.Array<com.riiablo.map.pfa.Point2> neighbors = new com.badlogic.gdx.utils.Array<>();
    MapGraph graph = new MapGraph(map);
    graph.getNeighbors(graph.getOrCreate(2, 2), flags, 2, null, -1, -1, neighbors);
    assertTrue(neighbors.contains(graph.getOrCreate(3, 3), true));
    zone.or(4, 3, DT1.Tile.FLAG_BLOCK_WALK);
    graph.getNeighbors(graph.getOrCreate(2, 2), flags, 2, null, -1, -1, neighbors);
    assertFalse(neighbors.contains(graph.getOrCreate(3, 3), true), "target full cross must still be clear");
  }

  @Test void nativeSmallFootprintIsACrossAndBigIsThreeByThree() {
    Map map = map();
    int flags = NativeGroundPathProfile.PLAYER.terrainFlags;
    map.zones.first().or(5, 3, DT1.Tile.FLAG_BLOCK_PLAYER_WALK);
    assertFalse(map.isBlockedForPath(4, 2, flags, 2, false), "diagonal is outside SMALL");
    assertTrue(map.isBlockedForPath(4, 2, flags, 3, false), "diagonal is inside BIG");
    assertTrue(map.isBlockedForPath(5, 2, flags, 2, false), "cardinal cell is inside SMALL");
    assertFalse(map.isBlockedForPath(3, 2, flags, 3, false), "BIG does not extend two cells");
  }
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

  @Test
  void scopedCollisionDoesNotReadAnOverlappingDetachedLevel() {
    Map map = map();
    Map.Zone detached = new Map.Zone();
    detached.map = map;
    detached.width = detached.height = 12;
    detached.tilesX = detached.tilesY = 3;
    detached.flags = new byte[144];
    map.zones.add(detached);
    int flags = NativeGroundPathProfile.PLAYER.terrainFlags;
    assertTrue(map.isBlockedForPath(6, 6, flags, 1, false));
    assertFalse(map.isBlockedForPath(detached, 6, 6, flags, 1, false));
    Ray<Vector2> ray = new Ray<>(new Vector2(3, 5), new Vector2(9, 7));
    Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
    assertFalse(map.castRay(detached, ray, flags, 1, collision, false));
    // Scope must be restored for the next ordinary query.
    assertTrue(map.castRay(ray, flags, 1, collision));
    assertTrue(map.isBlockedForPath(detached, 12, 6, flags, 1, false));
  }

}
