package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlTypes;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapAssetLifecycleTest {
  private AssetManager previousAssets;
  private Application previousApp;
  private AssetManager assets;

  @BeforeEach void setup() {
    previousAssets = Riiablo.assets;
    previousApp = Gdx.app;
    Gdx.app = mock(Application.class);
    assets = new AssetManager(FileHandle::new, false);
    assets.setLoader(Resource.class, new ResourceLoader());
    Riiablo.assets = assets;
  }

  @AfterEach void restore() {
    try { assets.dispose(); }
    finally { Riiablo.assets = previousAssets; Gdx.app = previousApp; }
  }

  @Test void computingDependenciesWithoutLoadingDoesNotUnloadAnExternalReference() {
    assets.load("shared", Resource.class);
    assets.finishLoading();
    Resource resource = assets.get("shared");
    Map.Zone zone = zone("shared");
    zone.getDependencies();
    zone.dispose();
    zone.dispose();
    assertEquals(1, assets.getReferenceCount("shared"));
    assertEquals(0, resource.disposals);
    assertNull(zone.map);
    assertNull(zone.level);
    assertNull(zone.type);
  }

  @Test void repeatedLoadIsIdempotentButDuplicateRecipeEntriesRetainSeparateReferences() {
    Map.Zone zone = zone("shared", "shared");
    zone.load();
    zone.load();
    assets.finishLoading();
    assertEquals(2, assets.getReferenceCount("shared"));
    Resource resource = assets.get("shared");
    zone.dispose();
    zone.dispose();
    assertFalse(assets.contains("shared"));
    assertEquals(1, resource.disposals);
  }

  @Test void twoMapsAndExternalReferenceShareUntilLastReleaseThenCanReload() {
    assets.load("shared", Resource.class);
    assets.finishLoading();
    Resource original = assets.get("shared");
    Map first = map(zone("shared"));
    Map second = map(zone("shared"));
    first.load();
    second.load();
    assets.finishLoading();
    assertEquals(3, assets.getReferenceCount("shared"));
    first.dispose();
    first.dispose();
    assertEquals(2, assets.getReferenceCount("shared"));
    second.dispose();
    assertEquals(1, assets.getReferenceCount("shared"));
    assertEquals(0, original.disposals);
    assets.unload("shared");
    assertEquals(1, original.disposals);
    Map reentered = map(zone("shared"));
    reentered.load();
    assets.finishLoading();
    Resource replacement = assets.get("shared");
    assertNotSame(original, replacement);
    reentered.dispose();
    assertEquals(1, replacement.disposals);
  }

  @Test void disposalBeforeAssetUpdateDrainsAndReleasesOnlyOwnQueuedReferences() {
    assets.load("shared", Resource.class);
    assets.finishLoading();
    Map map = map(zone("shared", "shared", "not-started"));
    map.load();
    map.dispose();
    assets.finishLoading();
    assertEquals(1, assets.getReferenceCount("shared"));
    assertFalse(assets.contains("not-started"));
  }

  @Test void loadFailureRollsBackOnlySuccessfullySubmittedReferencesAndCanRetry() {
    Map.Zone zone = zone("first", "bad");
    zone.dependencies.set(1, new AssetDescriptor<>("bad", MissingLoader.class));
    assertThrows(RuntimeException.class, zone::load);
    assertFalse(assets.contains("first"));
    zone.dependencies.set(1, new AssetDescriptor<>("second", Resource.class));
    zone.load();
    assets.finishLoading();
    assertTrue(assets.isLoaded("first"));
    assertTrue(assets.isLoaded("second"));
    zone.dispose();
    assertFalse(assets.contains("first"));
    assertFalse(assets.contains("second"));
  }

  @Test void finishAndDisposalUseOriginalManagerAfterGlobalReplacement() {
    Map.Zone zone = zone("shared");
    zone.load();
    Resource resource = assets.finishLoadingAsset("shared");
    AssetManager replacement = mock(AssetManager.class);
    Riiablo.assets = replacement;
    zone.finishLoading();
    zone.dispose();
    verifyNoInteractions(replacement);
    assertEquals(1, resource.disposals);
  }

  @Test void presetRetrievalUsesTheParentManager() {
    AssetManager original = mock(AssetManager.class);
    Riiablo.assets = mock(AssetManager.class);
    Map.Preset preset = new Map.Preset();
    preset.ds1Descriptor = new AssetDescriptor<>("room.ds1", DS1.class);
    DS1 ds1 = mock(DS1.class);
    when(original.isLoaded(preset.ds1Descriptor)).thenReturn(true);
    when(original.get(preset.ds1Descriptor)).thenReturn(ds1);
    assertSame(ds1, preset.finishLoading(original));
    verifyNoInteractions(Riiablo.assets);
  }

  @Test void explicitMapsUseDifferentManagersFromTheirFirstLoadDespiteGlobalReplacement() {
    AssetManager other = new AssetManager(FileHandle::new, false);
    other.setLoader(Resource.class, new ResourceLoader());
    AssetManager replacement = mock(AssetManager.class);
    Map first = new Map(1, 0, new MapResources(mock(com.riiablo.Files.class), assets, null));
    Map second = new Map(2, 0, new MapResources(mock(com.riiablo.Files.class), other, null));
    Map.Zone a = zone("shared"), b = zone("shared");
    a.map = first; first.zones.add(a);
    b.map = second; second.zones.add(b);
    Riiablo.assets = replacement;
    try {
      first.load(); second.load();
      Resource firstResource = assets.finishLoadingAsset("shared");
      Resource secondResource = other.finishLoadingAsset("shared");
      assertNotSame(firstResource, secondResource);
      first.finishLoading(); second.finishLoading();
      first.dispose();
      assertEquals(1, firstResource.disposals);
      assertEquals(0, secondResource.disposals);
      assertSame(secondResource, other.get("shared"));
      second.dispose();
      assertEquals(1, secondResource.disposals);
      verifyNoInteractions(replacement);
    } finally { first.dispose(); second.dispose(); other.dispose(); }
  }

  @Test void unloadFailureStillClearsAllZonesAndIndicesWithoutRetry() {
    AssetManager owner = mock(AssetManager.class);
    when(owner.contains(anyString())).thenReturn(true);
    when(owner.isLoaded(anyString())).thenReturn(true);
    Riiablo.assets = owner;
    RuntimeException first = new RuntimeException("first");
    RuntimeException second = new RuntimeException("second");
    doThrow(first).when(owner).unload("first");
    doThrow(second).when(owner).unload("second");
    Map map = map(zone("first", "rest"), zone("second"));
    map.dt1s.put(1, new DT1s());
    map.load();
    assertSame(first, assertThrows(RuntimeException.class, map::dispose));
    assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    verify(owner).unload("rest");
    assertEquals(0, map.zones.size);
    assertEquals(0, map.dt1s.size);
    map.dispose();
    verify(owner).unload("first");
    verify(owner).unload("second");
  }

  @Test void returnedTileArraysDoNotRetainDisposedTileReferences() {
    DT1.Tile[] layer = Map.Zone.obtainTileArray(1);
    layer[layer.length - 1] = mock(DT1.Tile.class);
    Map.Zone.free(layer);
    assertNull(layer[layer.length - 1]);
  }

  @Test void finishingAnUnloadedZoneFailsExplicitly() {
    assertThrows(NullPointerException.class, () -> zone("never").finishLoading());
  }

  @Test void changingActsReleasesThePreviousReferencesAndAllowsTheNextLoad() {
    Map map = new Map(123, 0) {
      @Override public void generate(int act) {
        Map.Zone zone = zone("act-" + act);
        zone.map = this;
        zones.add(zone);
      }
    };
    map.setAct(0);
    map.load();
    Resource first = assets.finishLoadingAsset("act-0");
    map.setAct(1);
    assertEquals(1, first.disposals);
    assertFalse(assets.contains("act-0"));
    map.load();
    Resource second = assets.finishLoadingAsset("act-1");
    map.setAct(1);
    assertSame(second, assets.get("act-1"));
    map.dispose();
    assertEquals(1, second.disposals);
  }

  @Test void disposingDuringAnActiveAsyncLoadPreservesAnotherConsumersQueuedReference() throws Exception {
    CountDownLatch decoded = new CountDownLatch(1);
    assets.setLoader(Resource.class, new AsynchronousAssetLoader<Resource, AssetLoaderParameters<Resource>>(FileHandle::new) {
      private Resource resource;
      @Override public void loadAsync(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Resource> params) {
        resource = new Resource();
        decoded.countDown();
      }
      @Override public Resource loadSync(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Resource> params) {
        return resource;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Resource> params) {
        return null;
      }
    });
    Map map = map(zone("shared", "shared"));
    map.load();
    assets.load("shared", Resource.class); // An independent consumer still waiting in the queue.
    assertFalse(assets.update());
    assertTrue(decoded.await(5, TimeUnit.SECONDS), "worker must decode without publishing on the main thread");
    assertFalse(assets.isLoaded("shared"));
    map.dispose();
    assets.finishLoading();
    assertEquals(1, assets.getReferenceCount("shared"));
    Resource external = assets.get("shared");
    assertEquals(0, external.disposals);
    assets.unload("shared");
    assertEquals(1, external.disposals);
  }

  private static Map map(Map.Zone... zones) {
    Map map = new Map(123, 0);
    for (Map.Zone zone : zones) { zone.map = map; map.zones.add(zone); }
    return map;
  }

  private static Map.Zone zone(String... paths) {
    Map.Zone zone = new Map.Zone();
    zone.map = new Map(123, 0);
    zone.level = new Levels.Entry();
    zone.type = new LvlTypes.Entry();
    zone.type.Id = 1;
    zone.dependencies = new Array<>();
    for (String path : paths) zone.dependencies.add(new AssetDescriptor<>(path, Resource.class));
    return zone;
  }

  private static class MissingLoader {}
  private static class Resource implements Disposable {
    int disposals;
    @Override public void dispose() { disposals++; }
  }
  private static class ResourceLoader extends SynchronousAssetLoader<Resource, AssetLoaderParameters<Resource>> {
    ResourceLoader() { super(FileHandle::new); }
    @Override public Resource load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Resource> params) {
      return new Resource();
    }
    @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Resource> params) {
      return null;
    }
  }
}
