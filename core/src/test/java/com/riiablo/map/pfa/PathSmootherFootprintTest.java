package com.riiablo.map.pfa;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.map.MapGraph;
import org.junit.jupiter.api.Test;

class PathSmootherFootprintTest {
  @Test void bigUnitCanSmoothStraightThroughThreeCellDoor() {
    Map map = RaycastFootprintTest.terrain((x, y) -> x == 20 && (y < 9 || y > 11));
    GraphPath path = path(16, 10, 18, 10, 20, 10, 22, 10, 24, 10);
    smoother(map).smoothPath(DT1.Tile.FLAG_BLOCK_WALK, 3, path);
    assertEquals(2, path.getCount(), "size is a footprint enum, not a lateral ray radius");
    assertSafe(map, path, 3);
  }

  @Test void twoClearSideRaysCannotHideAWallOnTheCentreLine() {
    Map map = RaycastFootprintTest.terrain((x, y) -> x == 10 && y == 10);
    GraphPath path = path(5, 10, 5, 14, 15, 14, 15, 10);
    smoother(map).smoothPath(DT1.Tile.FLAG_BLOCK_WALK, 3, path);
    assertTrue(path.getCount() > 2, "must retain a route around the isolated wall");
    assertSafe(map, path, 3);
  }

  @Test void zeroOneAndTwoPointPathsRetainEndpoints() {
    Map map = RaycastFootprintTest.terrain((x, y) -> false);
    for (int[] coordinates : new int[][] {{}, {5, 10}, {5, 10, 15, 10}}) {
      GraphPath path = path(coordinates);
      assertEquals(0, smoother(map).smoothPath(DT1.Tile.FLAG_BLOCK_WALK, 3, path));
      assertEquals(coordinates.length / 2, path.getCount());
    }
  }

  static PathSmoother<Point2> smoother(Map map) {
    return new PathSmoother<>(new RaycastCollisionDetector(map, new MapGraph(map)));
  }

  static GraphPath path(int... coordinates) {
    GraphPath path = new GraphPath();
    for (int i = 0; i < coordinates.length; i += 2) path.add(new Point2(coordinates[i], coordinates[i + 1]));
    return path;
  }

  static void assertSafe(Map map, GraphPath path, int size) {
    RaycastCollisionDetector rays = new RaycastCollisionDetector(map, new MapGraph(map));
    for (int i = 1; i < path.getCount(); i++) {
      Vector2 start = new Vector2(path.getNodePosition(i - 1));
      Vector2 end = new Vector2(path.getNodePosition(i));
      assertFalse(rays.findCollision(new Ray<>(start, end), DT1.Tile.FLAG_BLOCK_WALK, size,
          new Collision<>(new Vector2(), new Vector2())), "unsafe segment " + start + " -> " + end);
    }
  }
}
