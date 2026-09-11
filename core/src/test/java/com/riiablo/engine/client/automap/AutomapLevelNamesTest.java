package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AutomapLevelNamesTest {
  @Test void canonicalizesLvlTypesNames() {
    assertEquals("1 Wilderness", AutomapLevelNames.resolve(1, "Wilderness", false));
    assertEquals("1 Cave", AutomapLevelNames.resolve(1, "Cave", true));
    assertEquals("2 Desert", AutomapLevelNames.resolve(2, "Desert", false));
    assertEquals("5 Ice", AutomapLevelNames.resolve(5, "Ice Caves", true));
  }

  @Test void unknownTypeReturnsNull() {
    assertNull(AutomapLevelNames.resolve(1, "UnknownType", false));
    assertNull(AutomapLevelNames.resolve(0, "Town", false));
  }
}
