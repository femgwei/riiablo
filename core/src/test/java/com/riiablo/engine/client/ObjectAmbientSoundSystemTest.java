package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.drlg.TileGrid;

class ObjectAmbientSoundSystemTest {
  @Test
  void resolvesNativeInvisibleRiverMarkers() {
    assertEquals(ObjectAmbientSoundSystem.RIVER_SOUND,
        ObjectAmbientSoundSystem.soundId(ObjectAmbientSoundSystem.INVISIBLE_RIVER_SOUND_1));
    assertEquals(ObjectAmbientSoundSystem.RIVER_SOUND,
        ObjectAmbientSoundSystem.soundId(ObjectAmbientSoundSystem.INVISIBLE_RIVER_SOUND_2));
    assertEquals(-1, ObjectAmbientSoundSystem.soundId(39));
  }

  @Test
  void cullsSourcesOutsideTheirAudibleRadius() {
    assertTrue(ObjectAmbientSoundSystem.withinRadius(0f, 20f));
    assertTrue(ObjectAmbientSoundSystem.withinRadius(399f, 20f));
    assertFalse(ObjectAmbientSoundSystem.withinRadius(400f, 20f));
    assertFalse(ObjectAmbientSoundSystem.withinRadius(1f, 0f));
  }

  @Test
  void recognizesNativeRiverTileSources() {
    assertTrue(ObjectAmbientSoundSystem.isRiverSource(
        "DATA\\GLOBAL\\Tiles\\Act1\\Outdoors\\River.dt1"));
    assertTrue(ObjectAmbientSoundSystem.isRiverSource("River.dt1"));
    assertFalse(ObjectAmbientSoundSystem.isRiverSource(
        "DATA\\GLOBAL\\Tiles\\Act1\\Outdoors\\Grass1.dt1"));
    assertFalse(ObjectAmbientSoundSystem.isRiverSource(null));
  }

  @Test
  void measuresDistanceToNearestNativeRiverCell() {
    TileGrid grid = new TileGrid(4, 4);
    byte river = grid.registerSourceFile(
        "DATA\\GLOBAL\\Tiles\\Act1\\Outdoors\\River.dt1");
    grid.exportedFloorCells[1][2] = true;
    grid.floorSourceFiles[1][2] = river;

    assertEquals(0f, ObjectAmbientSoundSystem.nearestRiverDistance2(
        grid, 12f, 7f, 20f));
    assertEquals(25f, ObjectAmbientSoundSystem.nearestRiverDistance2(
        grid, 5f, 7f, 20f));
    assertEquals(Float.POSITIVE_INFINITY,
        ObjectAmbientSoundSystem.nearestRiverDistance2(grid, -100f, -100f, 20f));
  }
}
