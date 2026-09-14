package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.map.RenderSystem;
import org.junit.jupiter.api.Test;

class AutomapOptionsTest {
  @Test
  void sizeOptionCyclesThroughAllThreeRequestedLayouts() {
    int mode = RenderSystem.AUTOMAP_MODE_CENTER;
    mode = AutomapOptions.nextMode(mode);
    assertEquals(RenderSystem.AUTOMAP_MODE_TOP_LEFT, mode);
    mode = AutomapOptions.nextMode(mode);
    assertEquals(RenderSystem.AUTOMAP_MODE_TOP_RIGHT, mode);
    mode = AutomapOptions.nextMode(mode);
    assertEquals(RenderSystem.AUTOMAP_MODE_CENTER, mode);
  }

  @Test
  void menuUsesExplicitFullAndCornerLabels() {
    assertEquals("FULL SCREEN",
        AutomapOptions.modeLabel(RenderSystem.AUTOMAP_MODE_CENTER));
    assertEquals("MINI MAP (Left-Top)",
        AutomapOptions.modeLabel(RenderSystem.AUTOMAP_MODE_TOP_LEFT));
    assertEquals("MINI MAP (Right-Top)",
        AutomapOptions.modeLabel(RenderSystem.AUTOMAP_MODE_TOP_RIGHT));
  }

  @Test
  void invalidPersistedModeFallsBackToFullScreen() {
    assertEquals(RenderSystem.AUTOMAP_MODE_CENTER, AutomapOptions.normalizeMode(-1));
    assertEquals("FULL SCREEN", AutomapOptions.modeLabel(99));
  }
}
