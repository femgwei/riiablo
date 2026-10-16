package com.riiablo.map;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlTypes;
import com.riiablo.mpq.MPQFileHandleResolver;

/** Real MPQ/native/GL verification, deliberately not a full GameScreen re-entry test. */
public final class OffscreenMapAssetLifecycle extends ApplicationAdapter {
  private static final String PATH = "data/global/tiles/Act1/Caves/Cavedr.dt1";
  private final FileHandle home;

  private OffscreenMapAssetLifecycle(String home) { this.home = new FileHandle(home); }

  public static void main(String[] args) {
    if (args.length != 1) throw new IllegalArgumentException("Expected D2 home directory");
    Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
      failure.printStackTrace(System.err);
      System.err.flush();
      Runtime.getRuntime().halt(1);
    });
    Thread timeout = new Thread(() -> {
      try {
        Thread.sleep(120_000L);
        System.err.println("[MAP_ASSETS] timeout");
        Runtime.getRuntime().halt(2);
      } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }, "map-assets-timeout");
    timeout.setDaemon(true);
    timeout.start();
    LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
    config.title = "riiablo-map-assets";
    config.width = config.height = 1;
    config.resizable = false;
    config.forceExit = false;
    new LwjglApplication(new OffscreenMapAssetLifecycle(args[0]), config);
  }

  @Override public void create() {
    org.lwjgl.opengl.Display.setLocation(-32768, -32768);
    MPQFileHandleResolver resolver = new MPQFileHandleResolver(home);
    AssetManager owner = new AssetManager(resolver, false);
    AssetManager replacement = new AssetManager(resolver, false);
    owner.setLoader(DT1.class, new DT1Loader(resolver));
    boolean previousLoadData = DT1.loadData;
    AssetManager previousAssets = Riiablo.assets;
    DT1 previous = null;
    try {
      DT1.loadData = true;
      for (int cycle = 1; cycle <= 3; cycle++) {
        Riiablo.assets = owner;
        owner.load(PATH, DT1.class); // An asset consumer outside either map.
        DT1 current = owner.finishLoadingAsset(PATH);
        require(current != previous, "reload must create a fresh DT1");
        require(current.getNumTiles() > 0, "fixture must contain tiles");
        Pixmap[] pixels = new Pixmap[current.tiles.length];
        Texture[] textures = current.textures.clone();
        for (int i = 0; i < pixels.length; i++) {
          pixels[i] = current.tiles[i].pixmap;
          require(pixels[i] != null && !pixels[i].isDisposed(), "CPU pixels must be live");
          require(Gdx.gl.glIsTexture(textures[i].getTextureObjectHandle()), "GL texture must be live");
        }
        Map first = map(2);
        Map second = map(1);
        first.load();
        first.load(); // Must not add another pair of references.
        second.load();
        Riiablo.assets = replacement;
        first.finishLoading();
        second.finishLoading();
        owner.finishLoading(); // finishLoadingAsset returns early for an already-loaded asset.
        require(owner.getReferenceCount(PATH) == 4, "external + 2 + 1 references");
        require(first.dt1s.get(1).dt1s.contains(current), "first index must use owner DT1");
        require(second.dt1s.get(1).dt1s.contains(current), "second index must share owner DT1");
        first.dispose();
        first.dispose();
        require(owner.getReferenceCount(PATH) == 2, "first map must release exactly two references");
        require(current.textures != null && !pixels[0].isDisposed(), "second map must stay usable");
        second.dispose();
        require(owner.getReferenceCount(PATH) == 1, "external reference must remain");
        require(Gdx.gl.glIsTexture(textures[0].getTextureObjectHandle()), "external texture must stay live");
        int[] handles = new int[textures.length];
        for (int i = 0; i < handles.length; i++) handles[i] = textures[i].getTextureObjectHandle();
        owner.unload(PATH);
        for (int i = 0; i < handles.length; i++) {
          require(pixels[i].isDisposed(), "last reference must free CPU pixels");
          require(!Gdx.gl.glIsTexture(handles[i]), "last reference must delete GL texture");
          require(current.tiles[i].texture == null, "tile must not retain a dead texture");
        }
        require(owner.getLoadedAssets() == 0 && owner.getQueuedAssets() == 0, "owner must return to empty");
        require(replacement.getLoadedAssets() == 0, "replacement host must be untouched");
        require(Gdx.gl.glGetError() == 0, "GL errors during lifecycle verification");
        Gdx.app.log("MapAssets", "[MAP_ASSETS] cycle=" + cycle + " result=PASS tiles=" + pixels.length
            + " refs=4->2->1->0 pixelsFreed=true texturesDeleted=true");
        previous = current;
      }
      Gdx.app.log("MapAssets", "[MAP_ASSETS] result=PASS cycles=3 scope=map-assets-not-full-session");
    } finally {
      owner.dispose();
      replacement.dispose();
      Riiablo.assets = previousAssets;
      DT1.loadData = previousLoadData;
    }
    Gdx.app.exit();
  }

  private static Map map(int references) {
    Map map = new Map(123, 0);
    Map.Zone zone = new Map.Zone();
    zone.map = map;
    zone.level = new Levels.Entry();
    zone.level.LevelName = "asset lifecycle fixture";
    zone.type = new LvlTypes.Entry();
    zone.type.Id = 1;
    zone.dependencies = new Array<>();
    for (int i = 0; i < references; i++) zone.dependencies.add(new AssetDescriptor<>(PATH, DT1.class));
    map.zones.add(zone);
    return map;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
