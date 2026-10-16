package com.riiablo.util;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.util.Objects;

/** Owns one reference per successful load on the original manager; close on the GL thread. */
public final class AssetReferenceScope implements Disposable {
  private final AssetManager assets;
  private final Array<AssetDescriptor> submitted = new Array<>();
  private boolean disposed;

  public AssetReferenceScope(AssetManager assets) {
    this.assets = Objects.requireNonNull(assets, "assets");
  }

  public void load(AssetDescriptor asset) {
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
      // Cancelling a pending libGDX load by name can cancel another owner's request.
      // Resolve submitted requests before giving back this scope's references.
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
