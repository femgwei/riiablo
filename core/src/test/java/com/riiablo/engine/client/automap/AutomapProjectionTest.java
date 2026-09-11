package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

class AutomapProjectionTest {
  @Test void tileCenterUsesSubtileCoordinateSpace() {
    Vector2 out = new Vector2();
    AutomapProjection.tileCenter(4, -2, out);
    assertEquals(22.5f, out.x, 0.001f);
    assertEquals(-7.5f, out.y, 0.001f);
  }

  @Test void worldProjectionIsSharedCoordinateSpace() {
    Vector2 out = new Vector2();
    AutomapProjection.worldToAutomap(12.5f, -3.25f, out);
    assertEquals(12.5f, out.x, 0.001f);
    assertEquals(-3.25f, out.y, 0.001f);
  }

  @Test void negativeWorldCoordinatesUseFloorTileDivision() {
    assertEquals(-1, AutomapProjection.tileIndex(-1));
    assertEquals(-1, AutomapProjection.tileIndex(-5));
    assertEquals(-2, AutomapProjection.tileIndex(-6));
  }

  @Test void zoneBoundsUseExclusiveCeilingAcrossTileEdges() {
    assertEquals(1, AutomapProjection.tileEndExclusive(1));
    assertEquals(1, AutomapProjection.tileEndExclusive(5));
    assertEquals(2, AutomapProjection.tileEndExclusive(6));
    assertEquals(0, AutomapProjection.tileEndExclusive(0));
    assertEquals(0, AutomapProjection.tileEndExclusive(-1));
  }
}
