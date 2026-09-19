package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HelpPanelLayoutTest {
  @Test
  void originalHelpArtFitsAndCentersInWide480pViewport() {
    float scale = HelpPanel.contentScale(854f, 480f);

    assertEquals(1f, scale, 0.0001f);
    assertEquals(640f, HelpPanel.LOGICAL_WIDTH * scale, 0.0001f);
    assertEquals(480f, HelpPanel.LOGICAL_HEIGHT * scale, 0.0001f);
    assertEquals(107f, HelpPanel.contentX(854f, scale), 0.0001f);
  }

  @Test
  void originalHelpArtKeepsNativeSizeAt640By480() {
    float scale = HelpPanel.contentScale(640f, 480f);

    assertEquals(1f, scale, 0.0001f);
    assertEquals(0f, HelpPanel.contentX(640f, scale), 0.0001f);
  }
}
