package com.riiablo.screen;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.util.AssetReferenceScope;
import java.util.Objects;

/** One reference per successfully submitted load, owned by the original manager. */
final class ScreenAssetScope implements Disposable {
  final AssetManager assets;
  private final AssetReferenceScope references;

  ScreenAssetScope(AssetManager assets) {
    this.assets = Objects.requireNonNull(assets, "assets");
    references = new AssetReferenceScope(assets);
  }

  void load(AssetDescriptor asset) {
    references.load(asset);
  }

  @Override public void dispose() {
    references.dispose();
  }
}
