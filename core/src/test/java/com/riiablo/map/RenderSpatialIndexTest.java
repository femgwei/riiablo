package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.IntArray;
import org.junit.jupiter.api.Test;

class RenderSpatialIndexTest {
  @Test
  void groupsEntitiesByFiveSubtileMapCell() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    assertTrue(index.update(11, 0f, 0f));
    assertTrue(index.update(12, 4.999f, 4.999f));
    assertTrue(index.update(13, 5f, 5f));

    assertArrayEquals(new int[] {11, 12}, values(index.entitiesAtSubtile(0, 0)));
    assertArrayEquals(new int[] {13}, values(index.entitiesAtSubtile(5, 5)));
    assertEquals(2, index.activeCellCount());
    assertEquals(3, index.entityCount());
  }

  @Test
  void usesFloorDivisionForNegativeWorldCoordinates() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.update(21, -0.001f, -0.001f);
    index.update(22, -5f, -5f);
    index.update(23, -5.001f, -5.001f);

    assertArrayEquals(new int[] {21, 22}, values(index.entitiesAtSubtile(-5, -5)));
    assertArrayEquals(new int[] {23}, values(index.entitiesAtSubtile(-10, -10)));
  }

  @Test
  void leavesStaticAndWithinCellEntitiesUntouched() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    assertTrue(index.update(31, 1f, 1f));
    assertFalse(index.update(31, 1f, 1f));
    assertFalse(index.update(31, 4.9f, 4.9f));

    assertArrayEquals(new int[] {31}, values(index.entitiesAtSubtile(0, 0)));
    assertEquals(1, index.entityCount());
  }

  @Test
  void movesOnlyEntitiesThatCrossCellBoundaries() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.update(41, 2f, 2f);
    index.update(42, 3f, 3f);

    assertTrue(index.update(41, 7f, 2f));
    assertArrayEquals(new int[] {42}, values(index.entitiesAtSubtile(0, 0)));
    assertArrayEquals(new int[] {41}, values(index.entitiesAtSubtile(5, 0)));
    assertEquals(2, index.activeCellCount());
    assertEquals(2, index.entityCount());

    assertTrue(index.update(41, 12f, 2f));
    assertNull(index.entitiesAtSubtile(5, 0));
    assertArrayEquals(new int[] {41}, values(index.entitiesAtSubtile(10, 0)));
    assertEquals(2, index.activeCellCount());
  }

  @Test
  void removesEntityAndReleasesEmptyCell() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.update(51, 2f, 2f);

    assertTrue(index.remove(51));
    assertFalse(index.remove(51));
    assertNull(index.entitiesAtSubtile(0, 0));
    assertEquals(0, index.activeCellCount());
    assertEquals(0, index.entityCount());
  }

  @Test
  void nonFinitePositionRemovesExistingMembership() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.update(61, 2f, 2f);

    assertTrue(index.update(61, Float.NaN, 0f));
    assertFalse(index.update(62, 0f, Float.POSITIVE_INFINITY));
    assertNull(index.entitiesAtSubtile(0, 0));
    assertEquals(0, index.entityCount());
  }

  @Test
  void clearRemovesMembershipAndAllowsReuse() {
    RenderSpatialIndex index = new RenderSpatialIndex();
    index.update(71, 2f, 2f);
    index.update(72, 7f, 7f);
    index.clear();

    assertEquals(0, index.activeCellCount());
    assertEquals(0, index.entityCount());
    index.update(73, 12f, 7f);
    assertArrayEquals(new int[] {73}, values(index.entitiesAtSubtile(10, 5)));
  }

  private static int[] values(IntArray entities) {
    return entities == null ? null : entities.toArray();
  }
}
