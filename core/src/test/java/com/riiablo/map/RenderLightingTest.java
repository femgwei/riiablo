package com.riiablo.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RenderLightingTest {
  @Test
  public void playerLightUsesNativeBaseAndItemModifier() {
    assertEquals(13, RenderSystem.playerLightRadius(0));
    assertEquals(16, RenderSystem.playerLightRadius(3));
    assertEquals(18, RenderSystem.playerLightRadius(99));
    assertEquals(1, RenderSystem.playerLightRadius(-99));
  }
}
