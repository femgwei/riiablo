package com.riiablo.screen;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.util.Disposal;
import java.util.Objects;

/** One reference per successfully submitted load, owned by the original manager. */
final class ScreenAssetScope implements Disposable {
  final AssetManager assets;
  private final Array<AssetDescriptor> submitted = new Array<>();
  private boolean disposed;

  ScreenAssetScope(AssetManager assets) {
    this.assets = Objects.requireNonNull(assets, "assets");
  }

  void load(AssetDescriptor asset) {
    if (disposed) throw new IllegalStateException("asset scope is disposed");
    assets.load(asset);
    submitted.add(asset);
  }

  @Override public void dispose() {
    if (disposed) return;
    disposed = true;
    AssetDescriptor[] loaded = submitted.toArray(AssetDescriptor.class);
    submitted.clear();
    try (Disposal cleanup = new Disposal()) {
      // As with Map.Zone, finish active loads before unload: libGDX 1.10
      // cancels an active task by name, not by the requesting consumer.
      // Must run on the GL thread, before the host AssetManager is disposed.
      for (AssetDescriptor asset : loaded) {
        cleanup.run(() -> {
          if (assets.contains(asset.fileName) && !assets.isLoaded(asset.fileName)) {
            assets.finishLoadingAsset(asset);
          }
        });
      }
      for (AssetDescriptor asset : loaded) {
        cleanup.run(() -> {
          if (assets.contains(asset.fileName)) assets.unload(asset.fileName);
        });
      }
    }
  }
}
