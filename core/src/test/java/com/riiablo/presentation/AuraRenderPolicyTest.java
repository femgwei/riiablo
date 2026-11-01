package com.riiablo.presentation;

import com.riiablo.codec.excel.Overlay;
import com.riiablo.graphics.BlendMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuraRenderPolicyTest {
  @Test void nativeRateIsSixteenUnitsPerFrameNotFramesPerSecond() {
    assertEquals(.04f, AuraRenderPolicy.frameDuration(16), .000001f);
    assertEquals(.08f, AuraRenderPolicy.frameDuration(8), .000001f);
    assertEquals(.02f, AuraRenderPolicy.frameDuration(32), .000001f);
    assertEquals(Float.POSITIVE_INFINITY, AuraRenderPolicy.frameDuration(0));
  }

  @Test void auraDrawModeThreePreservesPaletteRgbAndAddsDestination() {
    assertEquals(BlendMode.ADDITIVE, AuraRenderPolicy.blendMode(3));
    assertEquals(1f, AuraRenderPolicy.alpha(3));
    assertEquals(BlendMode.ID, AuraRenderPolicy.blendMode(5));
    assertEquals(.25f, AuraRenderPolicy.alpha(0));
    assertEquals(.5f, AuraRenderPolicy.alpha(1));
    assertEquals(.75f, AuraRenderPolicy.alpha(2));
  }

  @Test void playerUsesHeightTwoAndOffsetsAreConvertedToYUp() {
    Overlay.Entry row = new Overlay.Entry();
    row.Yoffset = -4;
    row.Height1 = 10; row.Height2 = 20; row.Height3 = 30; row.Height4 = 40;
    assertEquals(-16f, AuraRenderPolicy.offsetY(row, 1));
    assertEquals(-6f, AuraRenderPolicy.offsetY(row, 0));
    assertEquals(-26f, AuraRenderPolicy.offsetY(row, 2));
    assertEquals(-36f, AuraRenderPolicy.offsetY(row, 3));
    assertEquals(-71f, AuraRenderPolicy.offsetY(row, -1));
    assertEquals(4f, AuraRenderPolicy.offsetY(row, 4));
  }
}
