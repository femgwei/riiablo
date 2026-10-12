package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.map.Orientation;
import org.junit.jupiter.api.Test;

class NativeWallTileTypeTest {
  @Test void nativeLogicalWallTypeOverridesDt1Orientation() {
    assertEquals(Orientation.RIGHT_WALL,
        AutomapManager.nativeAutomapOrientation(Orientation.LEFT_WALL, Orientation.RIGHT_WALL));
    assertEquals(Orientation.LEFT_WALL,
        AutomapManager.nativeAutomapOrientation(Orientation.LEFT_WALL, -1));
    assertEquals(Orientation.LEFT_WALL,
        AutomapManager.nativeAutomapOrientation(Orientation.LEFT_WALL, Orientation.FLOOR));
  }
}
