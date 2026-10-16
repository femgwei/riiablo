package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.riiablo.codec.DC6;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class PanelTexturesTest {
  @Test void solidUploadReleasesNativePixelsAfterTextureCreation() {
    GdxNativesLoader.load();
    List<Pixmap> uploads = new ArrayList<>();
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class, (texture, context) -> {
      Pixmap pixels = (Pixmap) context.arguments().get(0);
      uploads.add(pixels);
      assertFalse(pixels.isDisposed());
      assertEquals(Color.rgba8888(Color.BLACK), pixels.getPixel(0, 0));
    })) {
      Texture texture = PanelTextures.solid(Color.BLACK);
      assertSame(textures.constructed().get(0), texture);
      assertTrue(uploads.get(0).isDisposed());
      verify(texture, never()).dispose(); // the panel, not the upload helper, owns GL data
    }
  }

  @Test void failedSolidTextureCreationStillReleasesNativePixels() {
    GdxNativesLoader.load();
    List<Pixmap> uploads = new ArrayList<>();
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class, (texture, context) -> {
      uploads.add((Pixmap) context.arguments().get(0));
      throw new IllegalStateException("upload");
    })) {
      assertThrows(RuntimeException.class, () -> PanelTextures.solid(Color.WHITE));
      assertEquals(1, uploads.size());
      assertTrue(uploads.get(0).isDisposed());
    }
  }

  @Test void composedBackgroundPreservesPixelsDimensionsAndBorrowedSourceFrames() {
    GdxNativesLoader.load();
    Pixmap left = new Pixmap(2, 3, Pixmap.Format.RGBA8888);
    Pixmap right = new Pixmap(3, 2, Pixmap.Format.RGBA8888);
    left.setColor(Color.RED);
    left.fill();
    right.setColor(Color.BLUE);
    right.fill();
    DC6 panel = panel(left, right);
    List<Pixmap> uploads = new ArrayList<>();
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class, (texture, context) -> {
      TextureData data = (TextureData) context.arguments().get(0);
      assertFalse(data.isManaged());
      assertFalse(data.disposePixmap());
      Pixmap pixels = data.consumePixmap();
      uploads.add(pixels);
      assertEquals(5, pixels.getWidth());
      assertEquals(3, pixels.getHeight());
      assertEquals(Color.rgba8888(Color.RED), pixels.getPixel(0, 0));
      assertEquals(Color.rgba8888(Color.BLUE), pixels.getPixel(2, 1));
      assertEquals(0, pixels.getPixel(2, 0));
    })) {
      Texture texture = PanelTextures.controlBackground(panel);
      assertTrue(uploads.get(0).isDisposed());
      assertFalse(left.isDisposed());
      assertFalse(right.isDisposed());
      verify(texture, never()).dispose();
    } finally { left.dispose(); right.dispose(); }
  }

  @Test void failedBackgroundUploadReleasesOnlyComposedPixels() {
    GdxNativesLoader.load();
    Pixmap source = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    List<Pixmap> uploads = new ArrayList<>();
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class, (texture, context) -> {
      uploads.add(((TextureData) context.arguments().get(0)).consumePixmap());
      throw new IllegalStateException("upload");
    })) {
      assertThrows(RuntimeException.class, () -> PanelTextures.controlBackground(panel(source, source)));
      assertTrue(uploads.get(0).isDisposed());
      assertFalse(source.isDisposed());
    } finally { source.dispose(); }
  }

  @Test void compositionFailureStillReleasesAllocatedDestination() {
    DC6 panel = mock(DC6.class);
    Pixmap source = mock(Pixmap.class);
    when(source.getWidth()).thenReturn(1);
    when(source.getHeight()).thenReturn(1);
    when(panel.getNumFramesPerDir()).thenReturn(4);
    RuntimeException failure = new RuntimeException("source read");
    when(panel.getPixmap(0, 1)).thenReturn(source).thenThrow(failure);
    try (MockedConstruction<Pixmap> destinations = mockConstruction(Pixmap.class)) {
      assertSame(failure, assertThrows(RuntimeException.class, () -> PanelTextures.controlBackground(panel)));
      verify(destinations.constructed().get(0)).dispose();
      verify(source, never()).dispose();
    }
  }

  private static DC6 panel(Pixmap left, Pixmap right) {
    DC6 panel = mock(DC6.class);
    when(panel.getNumFramesPerDir()).thenReturn(5);
    when(panel.getPixmap(0, 1)).thenReturn(left);
    when(panel.getPixmap(0, 2)).thenReturn(right);
    return panel;
  }
}
