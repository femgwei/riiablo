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
