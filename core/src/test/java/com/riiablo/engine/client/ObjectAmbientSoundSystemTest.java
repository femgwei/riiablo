package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.Engine;

class ObjectAmbientSoundSystemTest {
  @Test
  void resolvesNativeInvisibleRiverMarkers() {
    assertEquals(ObjectAmbientSoundSystem.RIVER_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.INVISIBLE_RIVER_SOUND_1, Engine.Object.MODE_NU));
    assertEquals(ObjectAmbientSoundSystem.RIVER_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.INVISIBLE_RIVER_SOUND_2, Engine.Object.MODE_NU));
    assertEquals(-1, ObjectAmbientSoundSystem.soundId(39, Engine.Object.MODE_ON));
  }

  @Test
  void resolvesLitNativeTorchesAndBraziers() {
    assertEquals(ObjectAmbientSoundSystem.TORCH_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.TORCH_TIKI, Engine.Object.MODE_ON));
    assertEquals(ObjectAmbientSoundSystem.TORCH_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.TORCH_WALL, Engine.Object.MODE_OP));
    assertEquals(ObjectAmbientSoundSystem.TORCH_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.JUNGLE_TORCH, Engine.Object.MODE_ON));
    assertEquals(ObjectAmbientSoundSystem.BRAZIER_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.BRAZIER, Engine.Object.MODE_ON));
    assertEquals(ObjectAmbientSoundSystem.BRAZIER_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.BRAZIER_3, Engine.Object.MODE_OP));
    assertEquals(ObjectAmbientSoundSystem.BRAZIER_SOUND,
        ObjectAmbientSoundSystem.soundId(
            ObjectAmbientSoundSystem.FLOOR_BRAZIER, Engine.Object.MODE_ON));
  }

  @Test
  void stopsFireLoopsWhenNativeObjectsAreExtinguished() {
    assertFalse(ObjectAmbientSoundSystem.isBurningMode(Engine.Object.MODE_NU));
    assertTrue(ObjectAmbientSoundSystem.isBurningMode(Engine.Object.MODE_OP));
    assertTrue(ObjectAmbientSoundSystem.isBurningMode(Engine.Object.MODE_ON));
    assertFalse(ObjectAmbientSoundSystem.isBurningMode(Engine.Object.MODE_S1));
    assertEquals(-1, ObjectAmbientSoundSystem.soundId(
        ObjectAmbientSoundSystem.TORCH_TIKI, Engine.Object.MODE_NU));
    assertEquals(-1, ObjectAmbientSoundSystem.soundId(
        ObjectAmbientSoundSystem.BRAZIER, Engine.Object.MODE_NU));
    assertTrue(ObjectAmbientSoundSystem.isAmbientSource(
        ObjectAmbientSoundSystem.TORCH_TIKI));
    assertFalse(ObjectAmbientSoundSystem.isAmbientSource(39));
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
