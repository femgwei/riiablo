package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.badlogic.gdx.utils.IntArray;
import org.junit.jupiter.api.Test;

class RenderSpatialIndexTest {
  @Test
  void groupsEntitiesByFiveSubtileMapCell() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.beginFrame();
    index.add(11, 0f, 0f);
    index.add(12, 4.999f, 4.999f);
    index.add(13, 5f, 5f);

    assertArrayEquals(new int[] {11, 12}, values(index.entitiesAtSubtile(0, 0)));
    assertArrayEquals(new int[] {13}, values(index.entitiesAtSubtile(5, 5)));
    assertEquals(2, index.activeCellCount());
    assertEquals(3, index.entityCount());
  }

  @Test
  void usesFloorDivisionForNegativeWorldCoordinates() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.beginFrame();
    index.add(21, -0.001f, -0.001f);
    index.add(22, -5f, -5f);
    index.add(23, -5.001f, -5.001f);

    assertArrayEquals(new int[] {21, 22}, values(index.entitiesAtSubtile(-5, -5)));
    assertArrayEquals(new int[] {23}, values(index.entitiesAtSubtile(-10, -10)));
  }

  @Test
  void beginFrameRemovesStaleMembership() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.beginFrame();
    index.add(31, 2f, 2f);
    index.beginFrame();

    assertNull(index.entitiesAtSubtile(0, 0));
    assertEquals(0, index.activeCellCount());
    assertEquals(0, index.entityCount());

    index.add(32, 12f, 7f);
    assertArrayEquals(new int[] {32}, values(index.entitiesAtSubtile(10, 5)));
    assertEquals(1, index.activeCellCount());
  }

  @Test
  void ignoresNonFinitePositionsLikeThePreviousBoundsChecks() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.beginFrame();
    index.add(41, Float.NaN, 0f);
    index.add(42, 0f, Float.POSITIVE_INFINITY);

    assertNull(index.entitiesAtSubtile(0, 0));
    assertEquals(0, index.activeCellCount());
    assertEquals(0, index.entityCount());
  }

  private static int[] values(IntArray entities) {
    return entities == null ? null : entities.toArray();
  }
}
