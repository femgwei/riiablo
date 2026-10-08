package com.riiablo.map;

import com.badlogic.gdx.graphics.Color;
import com.riiablo.codec.PL2;
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
  public void ambientTintRemainsIndependentFromPaletteIntensity() {
    Color ambient = RenderSystem.setAmbientLight(new Color(), 125, 144, 243);

    assertEquals(125f / 255f, ambient.r, 0.001f);
    assertEquals(144f / 255f, ambient.g, 0.001f);
    assertEquals(243f / 255f, ambient.b, 0.001f);
  }

  @Test
  public void fullDaylightRemainsWhite() {
    assertEquals(Color.WHITE,
        RenderSystem.setAmbientLight(new Color(), 255, 255, 255));
  }

  @Test
  public void nativeCelIntensitySelectsPl2ShadowRows() {
    assertEquals(0, PL2.shadowRow(0));
    assertEquals(0, PL2.shadowRow(7));
    assertEquals(1, PL2.shadowRow(8));
    assertEquals(8, PL2.shadowRow(64));
    assertEquals(31, PL2.shadowRow(254));
    assertEquals(-1, PL2.shadowRow(255));
    assertEquals(0, PL2.shadowRow(-1));
    assertEquals(-1, PL2.shadowRow(256));
  }
}
