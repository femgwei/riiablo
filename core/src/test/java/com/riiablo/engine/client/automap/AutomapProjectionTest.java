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

  @Test void worldProjectionUsesIsometricScreenSpace() {
    Vector2 out = new Vector2();
    AutomapProjection.worldToAutomap(12.5f, -3.25f, out);
    assertEquals(25.2f, out.x, 0.001f);
    assertEquals(-7.4f, out.y, 0.001f);
  }

  @Test void worldProjectionMatchesTileAxes() {
    Vector2 out = new Vector2();
    AutomapProjection.worldToAutomap(1f, 0f, out);
    assertEquals(1.6f, out.x, 0.001f);
    assertEquals(-0.8f, out.y, 0.001f);
    AutomapProjection.worldToAutomap(0f, 1f, out);
    assertEquals(-1.6f, out.x, 0.001f);
    assertEquals(-0.8f, out.y, 0.001f);
  }

  @Test void adjacentDt1TilesUseNativeMaxiMapSpacing() {
    Vector2 origin = new Vector2();
    Vector2 east = new Vector2();
    Vector2 south = new Vector2();
    AutomapProjection.worldToAutomap(0f, 0f, origin);
    AutomapProjection.worldToAutomap(5f, 0f, east);
    AutomapProjection.worldToAutomap(0f, 5f, south);
    assertEquals(8f, east.x - origin.x, 0.001f);
    assertEquals(-4f, east.y - origin.y, 0.001f);
    assertEquals(-8f, south.x - origin.x, 0.001f);
    assertEquals(-4f, south.y - origin.y, 0.001f);
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
