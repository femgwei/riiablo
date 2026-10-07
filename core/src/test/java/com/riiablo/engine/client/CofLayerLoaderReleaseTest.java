package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

/** Regression contract for stale asynchronous COF asset releases. */
class CofLayerLoaderReleaseTest {
  @Test
  void staleReleaseIsNoopWhenAssetManagerEntryAlreadyGone() {
    AssetManager assets = new AssetManager(new FileHandleResolver() {
      @Override public FileHandle resolve(String fileName) { return null; }
    });
    // AssetManager.unload used to throw "Asset not loaded" in this state.
    // The production release helper is exercised through an empty descriptor
    // table in the same package without requiring native audio/texture setup.
    AssetDescriptor descriptor = new AssetDescriptor<>("stale.dcc", Texture.class);
    CofLayerLoader.releaseAsset(assets, descriptor);
    assertDoesNotThrow(() -> CofLayerLoader.releaseAsset(assets, descriptor));
  }

  @Test
  void queuedReleaseCancelsBeforeAsyncLoadStarts() {
    AssetManager assets = new AssetManager(new FileHandleResolver() {
      @Override public FileHandle resolve(String fileName) { return new FileHandle(fileName); }
    });
    AssetDescriptor<Texture> descriptor = new AssetDescriptor<>("queued.png", Texture.class);
    assets.load(descriptor);
    assertDoesNotThrow(() -> CofLayerLoader.releaseAsset(assets, descriptor));
    assertFalse(assets.contains(descriptor.fileName));
    assets.dispose();
  }

  @Test
  void objectAnimationUsesObjectsTxtCycleFlag() {
    Objects.Entry base = new Objects.Entry();
    base.CycleAnim = new boolean[8];
    base.CycleAnim[Engine.Object.MODE_NU] = false;
    base.CycleAnim[Engine.Object.MODE_ON] = true;

    assertEquals(Animation.Mode.CLAMP,
        CofLayerLoader.objectAnimationMode(base, Engine.Object.MODE_NU));
    assertEquals(Animation.Mode.LOOP,
        CofLayerLoader.objectAnimationMode(base, Engine.Object.MODE_ON));
    assertEquals(Animation.Mode.CLAMP,
        CofLayerLoader.objectAnimationMode(base, Engine.Object.MODE_OP));
  }

  @Test
  void objectAnimationUsesNativeStartFrame() {
    Objects.Entry base = new Objects.Entry();
    base.Start = new int[8];
    base.Start[Engine.Object.MODE_NU] = 3;
    assertEquals(3, CofLayerLoader.objectAnimationStartFrame(
        base, Engine.Object.MODE_NU, 8));
    assertEquals(3, CofLayerLoader.objectAnimationStartFrame(
        base, Engine.Object.MODE_NU, 4));
    base.Start[Engine.Object.MODE_NU] = 7;
    assertEquals(3, CofLayerLoader.objectAnimationStartFrame(
        base, Engine.Object.MODE_NU, 4));
  }

  @Test
  void synchronizedObjectUsesExactObjectsTxtFrameDelta() {
    Objects.Entry base = new Objects.Entry();
    base.Sync = true;
    base.FrameDelta = new int[8];
    base.FrameDelta[Engine.Object.MODE_ON] = 256;

    assertEquals(256, CofLayerLoader.objectAnimationRate(
        base, Engine.Object.MODE_ON, 100, 7));
  }

  @Test
  void unsynchronizedFlamesReceiveStablePerObjectRateVariation() {
    Objects.Entry fire = new Objects.Entry();
    fire.Sync = false;
    fire.FrameDelta = new int[8];
    fire.FrameDelta[Engine.Object.MODE_ON] = 256;

    boolean foundDifferentRate = false;
    int first = CofLayerLoader.objectAnimationRate(
        fire, Engine.Object.MODE_ON, 100, 1);
    assertEquals(first, CofLayerLoader.objectAnimationRate(
        fire, Engine.Object.MODE_ON, 100, 1));
    for (int seed = 2; seed <= 32; seed++) {
      int rate = CofLayerLoader.objectAnimationRate(
          fire, Engine.Object.MODE_ON, 100, seed);
      assertTrue(rate >= 240 && rate <= 271);
      foundDifferentRate |= rate != first;
    }
    assertTrue(foundDifferentRate);
  }
}
