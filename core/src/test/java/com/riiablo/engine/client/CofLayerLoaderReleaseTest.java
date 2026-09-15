package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
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
}
