package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Regression tests for the native D2 full/mini Automap projection contract. */
class AutomapCameraScaleTest {
  @Test
  void miniMapKeepsCameraScaleForHalfViewportContent() {
    AutomapCamera camera = new AutomapCamera();
    camera.setAutomapZoom(0.5f);

    camera.setMiniMapMode(false);
    assertEquals(0.5f, camera.zoom, 0.0001f);

    // AutomapViewport supplies the 1/2 viewport.  The camera zoom must not be
    // doubled, otherwise native cells and markers become 1/4 size overall.
    camera.setMiniMapMode(true);
    assertEquals(0.5f, camera.zoom, 0.0001f);
  }
}
