package com.riiablo.map.pfa;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.map.MapGraph;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

class RaycastFootprintTest {
  @Test void booleanRayUsesTheSameFootprintAsMovementRay() {
    Map map = terrain((x, y) -> x == 11 && y == 10);
    RaycastCollisionDetector rays = new RaycastCollisionDetector(map, new MapGraph(map));
    for (int size = 1; size <= 3; size++) {
      Ray<Vector2> ray = ray(10, 10, 10, 10);
      assertEquals(rays.findCollision(ray, DT1.Tile.FLAG_BLOCK_WALK, size, collision()),
          rays.collides(ray, DT1.Tile.FLAG_BLOCK_WALK, size), "size=" + size);
    }
  }

  @Test void ordinaryBigFootprintFitsThreeCellDoorJustLikeDoorOpener() {
    Map map = terrain((x, y) -> x == 20 && (y < 9 || y > 11));
    RaycastCollisionDetector rays = new RaycastCollisionDetector(map, new MapGraph(map));
    for (boolean doors : new boolean[] {false, true}) {
      assertFalse(rays.findCollision(ray(18, 10, 22, 10), DT1.Tile.FLAG_BLOCK_WALK, 3, collision(), doors));
      assertTrue(rays.findCollision(ray(18, 9, 22, 9), DT1.Tile.FLAG_BLOCK_WALK, 3, collision(), doors));
    }
  }

  @Test void endpointChecksWholeFootprintEvenForZeroLengthRay() {
    Map map = terrain((x, y) -> x == 11 && y == 10);
    RaycastCollisionDetector rays = new RaycastCollisionDetector(map, new MapGraph(map));
    assertTrue(rays.findCollision(ray(10, 10, 10, 10), DT1.Tile.FLAG_BLOCK_WALK, 3, collision()));
    assertFalse(rays.findCollision(ray(10, 10, 10, 10), DT1.Tile.FLAG_BLOCK_WALK, 1, collision()));
  }

  @Test void smallCrossAndBigSquareRemainDistinctAtDiagonalBlocker() {
    Map map = terrain((x, y) -> x == 11 && y == 11);
    RaycastCollisionDetector rays = new RaycastCollisionDetector(map, new MapGraph(map));
    assertFalse(rays.findCollision(ray(10, 10, 10, 10), DT1.Tile.FLAG_BLOCK_WALK, 2, collision()));
    assertTrue(rays.findCollision(ray(10, 10, 10, 10), DT1.Tile.FLAG_BLOCK_WALK, 3, collision()));
  }

  @Test void unrelatedWalkBitDoesNotBlockJumpMask() {
    Map map = terrain((x, y) -> x == 11 && y == 10);
    RaycastCollisionDetector rays = new RaycastCollisionDetector(map, new MapGraph(map));
    assertFalse(rays.findCollision(ray(10, 10, 12, 10), DT1.Tile.FLAG_BLOCK_JUMP, 1, collision()));
    assertTrue(rays.findCollision(ray(10, 10, 12, 10), DT1.Tile.FLAG_BLOCK_WALK, 1, collision()));
    assertFalse(rays.collides(ray(10, 10, 12, 10), DT1.Tile.FLAG_BLOCK_JUMP, 1));
    assertTrue(rays.collides(ray(10, 10, 12, 10), DT1.Tile.FLAG_BLOCK_WALK, 1));
  }

  static Map terrain(BiPredicate<Integer, Integer> blocked) {
    Map map = mock(Map.class, withSettings().stubOnly());
    when(map.flags(anyInt(), anyInt())).thenAnswer(call ->
        blocked.test(call.getArgument(0), call.getArgument(1)) ? DT1.Tile.FLAG_BLOCK_WALK : 0);
    when(map.flags(any(Vector2.class))).thenCallRealMethod();
    when(map.isBlockedForPath(anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer(call ->
        (map.flags(call.getArgument(0), call.getArgument(1)) & (int) call.getArgument(2)) != 0);
    when(map.isBlockedForPath(anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenCallRealMethod();
    return map;
  }

  static Ray<Vector2> ray(float x, float y, float tx, float ty) {
    return new Ray<>(new Vector2(x, y), new Vector2(tx, ty));
  }
  static Collision<Vector2> collision() { return new Collision<>(new Vector2(), new Vector2()); }
}
