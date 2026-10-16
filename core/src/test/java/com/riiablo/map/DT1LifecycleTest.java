package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.riiablo.graphics.PaletteIndexedPixmap;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class DT1LifecycleTest {
  private final boolean previousLoadData = DT1.loadData;
  @AfterEach void restoreFlag() { DT1.loadData = previousLoadData; }

  @Test void metadataOnlyDt1CanBeDisposedTwice() throws Exception {
    DT1 dt1 = dt1(1);
    DT1.loadData = false;
    dt1.dispose();
    dt1.dispose();
    assertNull(dt1.tiles[0].pixmap);
    assertThrows(IllegalStateException.class, dt1::prepareTextures);
  }

  @Test void releasesTexturesAndCpuPixelsEvenIfGlobalLoadingFlagChanges() throws Exception {
    DT1 dt1 = dt1(1);
    Texture texture = mock(Texture.class);
    Pixmap pixmap = mock(Pixmap.class);
    dt1.textures = new Texture[] {texture};
    dt1.tiles[0].texture = new TextureRegion();
    dt1.tiles[0].pixmap = pixmap;
    DT1.loadData = false;
    dt1.dispose();
    dt1.dispose();
    verify(texture).dispose();
    verify(pixmap).dispose();
    assertNull(dt1.textures);
    assertNull(dt1.tiles[0].texture);
    assertNull(dt1.tiles[0].pixmap);
  }

  @Test void failureReleasesRemainingResourcesAndDoesNotRetry() throws Exception {
    DT1 dt1 = dt1(2);
    Texture first = mock(Texture.class);
    Texture second = mock(Texture.class);
    Pixmap pixels = mock(Pixmap.class);
    RuntimeException failure = new RuntimeException("texture");
    doThrow(failure).when(first).dispose();
    dt1.textures = new Texture[] {first, null, second};
    dt1.tiles[1].pixmap = pixels;
    assertSame(failure, assertThrows(RuntimeException.class, dt1::dispose));
    verify(second).dispose();
    verify(pixels).dispose();
    assertDoesNotThrow(dt1::dispose);
    verify(first).dispose();
  }

  @Test void realNativePixelsAreReleasedBeforeAnyTextureUpload() throws Exception {
    GdxNativesLoader.load();
    DT1 dt1 = dt1(1);
    Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.Alpha);
    dt1.tiles[0].pixmap = pixmap;
    dt1.dispose();
    assertTrue(pixmap.isDisposed());
  }

  @Test void partialTextureUploadFailureReleasesAlreadyCreatedTextureAndAllPixels() throws Exception {
    GdxNativesLoader.load();
    GL20 previousGl = Gdx.gl;
    Graphics previousGraphics = Gdx.graphics;
    Application previousApp = Gdx.app;
    Gdx.gl = mock(GL20.class);
    Gdx.graphics = mock(Graphics.class);
    Gdx.app = mock(Application.class);
    when(Gdx.gl.glGenTexture()).thenReturn(11, 12);
    RuntimeException failure = new RuntimeException("wrap");
    AtomicInteger wraps = new AtomicInteger();
    doAnswer(invocation -> {
      if (wraps.incrementAndGet() == 2) throw failure; // After Texture's constructor returns.
      return null;
    }).when(Gdx.gl).glTexParameteri(eq(GL20.GL_TEXTURE_2D), eq(GL20.GL_TEXTURE_WRAP_S), eq(Texture.TextureWrap.ClampToEdge.getGLEnum()));
    DT1 dt1 = dt1(2);
    Pixmap first = new Pixmap(2, 2, Pixmap.Format.Alpha);
    Pixmap second = new Pixmap(2, 2, Pixmap.Format.Alpha);
    dt1.tiles[0].pixmap = first;
    dt1.tiles[1].pixmap = second;
    try {
      DT1.loadData = true;
      assertSame(failure, assertThrows(RuntimeException.class, dt1::prepareTextures));
      verify(Gdx.gl).glDeleteTexture(11);
      assertTrue(first.isDisposed());
      assertTrue(second.isDisposed());
      assertNull(dt1.textures);
      dt1.dispose();
    } finally {
      dt1.dispose();
      Gdx.gl = previousGl;
      Gdx.graphics = previousGraphics;
      Gdx.app = previousApp;
    }
  }

  @Test void malformedLaterTileReleasesPixelsDecodedBeforeTheFailure() {
    ByteBuffer bytes = ByteBuffer.allocate(DT1.Header.SIZE + 2 * DT1.Tile.SIZE).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(7).putInt(6).position(268);
    bytes.putInt(2).putInt(DT1.Header.SIZE);
    bytes.putInt(DT1.Header.SIZE + 8, -80);
    bytes.putInt(DT1.Header.SIZE + 12, 160);
    bytes.putInt(DT1.Header.SIZE + DT1.Tile.SIZE + 80, 1); // A missing block header.
    DT1.loadData = true;
    try (MockedConstruction<PaletteIndexedPixmap> pixels = mockConstruction(PaletteIndexedPixmap.class)) {
      assertThrows(RuntimeException.class, () -> DT1.loadFromStream("truncated.dt1", new ByteArrayInputStream(bytes.array())));
      assertEquals(1, pixels.constructed().size());
      verify(pixels.constructed().get(0)).dispose();
    }
  }

  static DT1 dt1(int tileCount) throws Exception {
    ByteBuffer bytes = ByteBuffer.allocate(DT1.Header.SIZE).order(ByteOrder.LITTLE_ENDIAN);
    bytes.putInt(7).putInt(6).position(268);
    bytes.putInt(tileCount).putInt(DT1.Header.SIZE);
    DT1.Header header = new DT1.Header(new ByteArrayInputStream(bytes.array()));
    DT1.Tile[] tiles = new DT1.Tile[tileCount];
    for (int i = 0; i < tileCount; i++) tiles[i] = new DT1.Tile(new ByteArrayInputStream(new byte[DT1.Tile.SIZE]));
    Constructor<DT1> constructor = DT1.class.getDeclaredConstructor(String.class, DT1.Header.class, DT1.Tile[].class);
    constructor.setAccessible(true);
    return constructor.newInstance("fixture.dt1", header, tiles);
  }
}
