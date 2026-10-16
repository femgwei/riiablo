package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.riiablo.Riiablo;
import org.junit.jupiter.api.Test;

class ScreenAssetScopeTest {
  private final AssetDescriptor<Sound> asset = new AssetDescriptor<>("owned.wav", Sound.class);

  @Test void unusedScopeDoesNotUnloadExternalAssets() {
    AssetManager owner = mock(AssetManager.class);
    new ScreenAssetScope(owner).dispose();
    verifyNoInteractions(owner);
  }

  @Test void duplicateSubmissionsAreBalancedOnceAgainstOriginalManager() {
    AssetManager owner = mock(AssetManager.class);
    when(owner.contains(asset.fileName)).thenReturn(true);
    when(owner.isLoaded(asset.fileName)).thenReturn(true);
    ScreenAssetScope scope = new ScreenAssetScope(owner);
    scope.load(asset);
    scope.load(asset);
    AssetManager previous = Riiablo.assets;
    AssetManager replacement = mock(AssetManager.class);
    try {
      Riiablo.assets = replacement;
      scope.dispose();
      scope.dispose();
      verify(owner, times(2)).unload(asset.fileName);
      verifyNoInteractions(replacement);
      assertThrows(IllegalStateException.class, () -> scope.load(asset));
    } finally { Riiablo.assets = previous; }
  }

  @Test void rejectedSubmissionDoesNotAcquireAReference() {
    AssetManager owner = mock(AssetManager.class);
    ScreenAssetScope scope = new ScreenAssetScope(owner);
    doThrow(new IllegalArgumentException("load rejected")).when(owner).load(asset);
    assertThrows(IllegalArgumentException.class, () -> scope.load(asset));
    scope.dispose();
    verify(owner, never()).unload(anyString());
  }

  @Test void drainFailureStillAttemptsAllUnloadsAndPreservesFailures() {
    AssetManager owner = mock(AssetManager.class);
    when(owner.contains(anyString())).thenReturn(true);
    ScreenAssetScope scope = new ScreenAssetScope(owner);
    AssetDescriptor<Sound> second = new AssetDescriptor<>("second.wav", Sound.class);
    scope.load(asset);
    scope.load(second);
    RuntimeException decode = new RuntimeException("decode");
    RuntimeException unload = new RuntimeException("unload");
    when(owner.finishLoadingAsset(asset)).thenThrow(decode);
    doThrow(unload).when(owner).unload(asset.fileName);
    assertSame(decode, assertThrows(RuntimeException.class, scope::dispose));
    assertArrayEquals(new Throwable[] {unload}, decode.getSuppressed());
    org.mockito.InOrder order = inOrder(owner);
    order.verify(owner).finishLoadingAsset(asset);
    order.verify(owner).finishLoadingAsset(second);
    order.verify(owner).unload(asset.fileName);
    order.verify(owner).unload(second.fileName);
    scope.dispose();
    verify(owner).unload(second.fileName);
  }
}
