package com.riiablo.map;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RenderLightingTest {
  @Test
  public void playerLightUsesNativeBaseAndItemModifier() {
    assertEquals(13, RenderSystem.playerLightRadius(0));
    assertEquals(16, RenderSystem.playerLightRadius(3));
    assertEquals(18, RenderSystem.playerLightRadius(99));
    assertEquals(1, RenderSystem.playerLightRadius(-99));
  }

  @Test
  public void objectLightConvertsNativeDiameterToRadius() {
    assertEquals(5f, RenderSystem.objectLightRadius(10), 0f);
    assertEquals(4.5f, RenderSystem.objectLightRadius(9), 0f);
    assertEquals(0f, RenderSystem.objectLightRadius(0), 0f);
  }

  @Test
  public void missileLightConvertsDiameterAndFlickersWithinItsMaximum() {
    assertEquals(3.5f, RenderSystem.missileLightRadius(7, 0, 0, 0), 0f);
    assertEquals(0f, RenderSystem.missileLightRadius(0, 4, 0, 0), 0f);

    boolean changed = false;
    float first = RenderSystem.missileLightRadius(5, 4, 0, 1234);
    for (int frame = 0; frame < 64; frame++) {
      float radius = RenderSystem.missileLightRadius(5, 4, frame, 1234);
      assertTrue(radius >= 0.5f);
      assertTrue(radius <= 2.5f);
      changed |= radius != first;
    }
    assertTrue(changed);
    assertEquals(first, RenderSystem.missileLightRadius(5, 4, 0, 1234), 0f);
  }

  @Test
  public void overlayLightExpandsToItsNativeMaximum() {
    assertEquals(1f, RenderSystem.overlayLightRadius(1, 9, 0), 0f);
    assertEquals(5f, RenderSystem.overlayLightRadius(1, 9, 4), 0f);
    assertEquals(9f, RenderSystem.overlayLightRadius(1, 9, 20), 0f);
    assertEquals(14f, RenderSystem.overlayLightRadius(14, 14, 0), 0f);
    assertEquals(0f, RenderSystem.overlayLightRadius(0, 14, 0), 0f);
  }

  @Test
  public void nightIntensityUsesDisplaySpacePaletteApproximation() {
    Color ambient = RenderSystem.setAmbientLight(new Color(), 64, 125, 144, 243);

    assertEquals(0.2615f, ambient.r, 0.001f);
    assertEquals(0.3013f, ambient.g, 0.001f);
    assertEquals(0.5084f, ambient.b, 0.001f);
    // Regression: the old linear product produced only 0.123 red at night.
    assertTrue(ambient.r > 0.24f);
  }

  @Test
  public void fullDaylightRemainsWhite() {
    assertEquals(Color.WHITE,
        RenderSystem.setAmbientLight(new Color(), 255, 255, 255, 255));
  }
}
