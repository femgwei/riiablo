package com.riiablo.codec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.BlendMode;
import org.junit.jupiter.api.Test;

class AnimationLayerLifecycleTest {
  @Test void returnedLayerDropsBorrowedResourcesAndCanBeInitializedForAnotherSheet() {
    DC6 first = sheet(), second = sheet();
    Animation.Layer layer = new Animation.Layer().set(first, BlendMode.ID);
    assertSame(first, layer.dc); assertSame(first.getRegions(), layer.regions);
    layer.reset(); layer.reset();
    assertNull(layer.dc); assertNull(layer.regions); assertNull(layer.transform);
    verify(first, never()).dispose();
    layer.set(second, BlendMode.DARKEN);
    assertSame(second, layer.dc); assertSame(second.getRegions(), layer.regions);
    assertEquals(27, layer.numFrames); assertEquals(1, layer.numDirections);
    assertEquals(BlendMode.DARKEN, layer.blendMode);
  }

  @Test void animationResetReturnsLayerWithoutKeepingItsSheetOrDisposingTheSharedSheet() {
    Colors previous = Riiablo.colors; Riiablo.colors = new Colors();
    try {
      DC6 sheet = sheet(); Animation animation = Animation.newAnimation(sheet);
      Animation.Layer returned = animation.getLayer(0);
      animation.reset(); animation.reset();
      assertNull(animation.getLayer(0)); assertNull(returned.dc); assertNull(returned.regions);
      verify(sheet, never()).dispose();
      Animation replacement = Animation.newAnimation(sheet);
      assertSame(sheet, replacement.getLayer(0).dc); replacement.reset();
    } finally { Riiablo.colors = previous; }
  }

  private DC6 sheet() {
    DC6 sheet = mock(DC6.class);
    when(sheet.getNumDirections()).thenReturn(1); when(sheet.getNumFramesPerDir()).thenReturn(27);
    when(sheet.getRegions()).thenReturn(new TextureRegion[][] {new TextureRegion[27]});
    when(sheet.getBox(anyInt())).thenReturn(new BBox(0, 0, 32, 32)); return sheet;
  }
}
