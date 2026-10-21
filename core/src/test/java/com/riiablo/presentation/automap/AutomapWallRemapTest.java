package com.riiablo.presentation.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.map.Orientation;
import org.junit.jupiter.api.Test;

class AutomapWallRemapTest {
  @Test void linkedRoomWallTypesUseD2MooMatrix() {
    assertEquals(Orientation.RIGHT_NORTH_CORNER_WALL,
        AutomapWallRemap.remap(Orientation.LEFT_WALL, Orientation.RIGHT_WALL));
    assertEquals(Orientation.LEFT_NORTH_CORNER_WALL,
        AutomapWallRemap.remap(Orientation.RIGHT_WALL, Orientation.LEFT_NORTH_CORNER_WALL));
    assertEquals(Orientation.SOUTH_CORNER_WALL,
        AutomapWallRemap.remap(Orientation.SOUTH_CORNER_WALL, Orientation.SOUTH_CORNER_WALL));
  }

  @Test void nonLinkedTypesRemainUnchanged() {
    assertEquals(Orientation.LEFT_WALL,
        AutomapWallRemap.remap(Orientation.LEFT_WALL, Orientation.PILLAR));
  }
}
