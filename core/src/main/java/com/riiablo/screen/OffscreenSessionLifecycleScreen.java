package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Keys;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.map.DT1;
import com.riiablo.save.CharData;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Real local GameScreen loading/render/exit/re-entry, not a manual visual acceptance test. */
public final class OffscreenSessionLifecycleScreen extends ScreenAdapter {
  private final CharData character;
  private final String output;
  private final AssetManager assets = Riiablo.assets;
  private final int inputBaseline = Riiablo.input.getProcessors().size;
  private final Map<String, DT1> previousTiles = new HashMap<>();
  private final Map<String, Integer> baselineReferences = new HashMap<>();
  private final Map<String, Integer> panelReferencesBeforeConstruction = new HashMap<>();
  private boolean started;
  private int cycle;
  private int buttonReferencesBeforeConstruction;
  private int buttonReferencesAfterConstruction;
  private GameEntryLoadingScreen entryScreen;
  private Stage entryStage;
  private int entryLoadingBaseline;

  public OffscreenSessionLifecycleScreen(CharData character, String output) {
    this.character = character;
    this.output = output;
  }

  @Override public void show() {
    if (started) return;
    started = true;
    Gdx.app.postRunnable(this::enter);
  }

  private void enter() {
    cycle++;
    require(assets == Riiablo.assets, "same host AssetManager");
    buttonReferencesBeforeConstruction = references("data/global/sfx/cursor/button.wav");
    panelReferencesBeforeConstruction.clear();
    panelReferencesBeforeConstruction.put("data/global/ui/PANEL/invchar6Tab.DC6",
        references("data/global/ui/PANEL/invchar6Tab.DC6"));
    for (CharacterClass charClass : CharacterClass.values()) {
      String name = "data/global/ui/SPELLS/" + charClass.spellIcons + ".DC6";
      panelReferencesBeforeConstruction.put(name, references(name));
    }
    entryLoadingBaseline = references("data/local/ui/loadingscreen.dc6");
    entryScreen = new GameEntryLoadingScreen(this::createGameScreen);
    require(references("data/local/ui/loadingscreen.dc6") == entryLoadingBaseline,
        "entry constructor does not acquire loading image");
    Riiablo.client.setScreen(entryScreen);
  }

  private OffscreenCampScreen createGameScreen() {
    entryStage = field(entryScreen, "stage", Stage.class);
    require(entryStage != null && entryStage.getActors().size == 1, "entry stage shown");
    require(references("data/local/ui/loadingscreen.dc6") == entryLoadingBaseline + 1,
        "entry owns exactly one loading-image reference");
    OffscreenCampScreen screen = new OffscreenCampScreen(character, output + "/cycle-" + cycle);
    // The constructor also builds UI actors, which have their own asset loads.
    // Measure the preload scope immediately before show, not before those
    // separate owners exist. Report their remaining references separately.
    baselineReferences.clear();
    for (String name : assets.getAssetNames()) {
      baselineReferences.put(name, assets.getReferenceCount(name));
    }
    // The entry view is still active during construction, but owns no part
    // of the game's preload baseline after its hide-time handoff cleanup.
    baselineReferences.put("data/local/ui/loadingscreen.dc6", entryLoadingBaseline);
    buttonReferencesAfterConstruction = references("data/global/sfx/cursor/button.wav");
    screen.onComplete(() -> Gdx.app.postRunnable(() -> leave(screen)));
    return screen;
  }

  private void leave(OffscreenCampScreen screen) {
    require(Riiablo.client.getScreen() == screen, "production loading screen must have popped");
    require(screen.player >= 0 && screen.map.getZones().size > 0, "live player and map");
    require(entryStage.getActors().size == 0, "entry stage cleared during handoff");
    require(field(entryScreen, "stage", Stage.class) == null, "entry stage reference cleared");
    require(field(entryScreen, "disposed", Boolean.class), "entry retired by Client, not just hidden");
    int loadingReferencesAfterHandoff = references("data/local/ui/loadingscreen.dc6");
    entryScreen.dispose();
    entryScreen.dispose();
    require(references("data/local/ui/loadingscreen.dc6") == loadingReferencesAfterHandoff,
        "retiring entry again cannot unload the game's shared loading image");
    assets.finishLoading();
    int liveButtonReferences = references("data/global/sfx/cursor/button.wav");
    for (int rebuild = 0; rebuild < 3; rebuild++) {
      character.update(); // production quick-skill rebuild, including discarded buttons
      assets.finishLoading();
      require(references("data/global/sfx/cursor/button.wav") == liveButtonReferences,
          "quick-skill rebuild must not accumulate click-sound references");
    }
    Array<Pixmap> pixels = new Array<>();
    // These are unmanaged textures, absent from AssetManager diagnostics.
    Texture fill = field(screen.inventoryPanel, "fill", Texture.class);
    Object controlWidget = field(screen.controlPanel, "controlWidget", Object.class);
    Object experienceWidget = field(screen.controlPanel, "experienceWidget", Object.class);
    Texture[] ownedPanelTextures = {fill, field(controlWidget, "background", Texture.class),
        field(experienceWidget, "whitePixelTexture", Texture.class)};
    Array<Integer> panelHandles = new Array<>();
    for (Texture texture : ownedPanelTextures) {
      require(texture != null && Gdx.gl.glIsTexture(texture.getTextureObjectHandle()),
          "live owned panel texture");
      panelHandles.add(texture.getTextureObjectHandle());
      require(texture.getTextureData() instanceof com.badlogic.gdx.graphics.glutils.PixmapTextureData,
          "panel upload pixel fixture");
      require(texture.getTextureData().consumePixmap().isDisposed(),
          "temporary panel upload pixels already released");
    }
    Array<Integer> handles = new Array<>();
    Map<String, DT1> currentTiles = new HashMap<>();
    for (String name : assets.getAssetNames()) {
      if (assets.getAssetType(name) != DT1.class) continue;
      DT1 dt1 = assets.get(name, DT1.class);
      require(dt1 != previousTiles.get(name), "fresh DT1 on re-entry: " + name);
      currentTiles.put(name, dt1);
      for (int i = 0; i < dt1.getNumTiles(); i++) {
        com.badlogic.gdx.graphics.g2d.TextureRegion region = dt1.getTexture(i);
        if (region == null) continue;
        Texture texture = region.getTexture();
        handles.add(texture.getTextureObjectHandle());
        // DT1 uses PixmapTextureData(disposePixmap=false); consuming this
        // retained data just returns its original Pixmap, without decoding.
        require(texture.getTextureData() instanceof com.badlogic.gdx.graphics.glutils.PixmapTextureData,
            "DT1 retained pixel fixture");
        Pixmap pixel = texture.getTextureData().consumePixmap();
        require(pixel != null && !pixel.isDisposed(), "live native pixels");
        pixels.add(pixel);
        require(Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "live map texture");
      }
    }
    require(!currentTiles.isEmpty() && handles.size > 0, "real DT1 resources sampled");
    Riiablo.client.setScreen(this); // real Client.hide/dispose, host stays alive
    require(screen.map.getZones().size == 0, "Client retired the game before returning");
    screen.dispose(); // must be idempotent
    require(screen.map.getZones().size == 0, "old map cleared");
    require(screen.stage.getActors().size == 0 && screen.scaledStage.getActors().size == 0,
        "old stages cleared");
    require(!Keys.Esc.containsStateListener(screen.mappedKeyStateListener)
        && !Keys.DebugMode.containsStateListener(screen.debugKeyListener), "old keys detached");
    require(Riiablo.input.getProcessors().size == inputBaseline, "input processors balanced");
    require(Riiablo.game == null && Riiablo.engine == null, "old global aliases cleared");
    // Screen preloads use the pre-show baseline; Button's separately owned
    // references must return all the way to the pre-construction baseline.
    require(references("data/global/sfx/cursor/button.wav") == buttonReferencesBeforeConstruction,
        "all session click-sound references must return to the host baseline");
    for (Map.Entry<String, Integer> reference : panelReferencesBeforeConstruction.entrySet()) {
      require(references(reference.getKey()) == reference.getValue(),
          "panel references balanced: " + reference.getKey() + " expected=" + reference.getValue()
              + " actual=" + references(reference.getKey()));
    }
    for (int handle : panelHandles) require(!Gdx.gl.glIsTexture(handle), "owned panel texture deleted");
    for (com.badlogic.gdx.assets.AssetDescriptor asset : new com.badlogic.gdx.assets.AssetDescriptor[] {
        screen.loadingscreenDescriptor, screen.windowopenDescriptor, screen.automapDescriptor}) {
      int expected = baselineReferences.getOrDefault(asset.fileName, 0);
      int actual = assets.isLoaded(asset.fileName) ? assets.getReferenceCount(asset.fileName) : 0;
      require(actual == expected, "preload references balanced: " + asset.fileName
          + " expected=" + expected + " actual=" + actual);
    }
    for (String name : currentTiles.keySet()) require(!assets.contains(name), "map asset released: " + name);
    for (Pixmap pixel : pixels) require(pixel != null && pixel.isDisposed(), "native pixels released");
    for (int handle : handles) require(!Gdx.gl.glIsTexture(handle), "GL texture deleted");
    require(Gdx.gl.glGetError() == 0, "no GL errors");
    previousTiles.clear();
    previousTiles.putAll(currentTiles);
    // Total counts alone cannot distinguish lazy host caches from leaked session assets.
    // Retain names, types and reference counts for comparisons between exit/re-entry cycles.
    Map<String, String> remainingAssets = new TreeMap<>();
    for (String name : assets.getAssetNames()) {
      remainingAssets.put(name, assets.getAssetType(name).getName()
          + "\trefs=" + assets.getReferenceCount(name));
    }
    StringBuilder assetReport = new StringBuilder();
    for (Map.Entry<String, String> asset : remainingAssets.entrySet()) {
      assetReport.append(asset.getKey()).append('\t').append(asset.getValue()).append('\n');
    }
    Gdx.files.absolute(output).child("cycle-" + cycle + "-remaining-assets.txt")
        .writeString(assetReport.toString(), false, "UTF-8");
    String report = "cycle=" + cycle + "\nmapAssets=" + currentTiles.size()
        + "\ntextures=" + handles.size + "\ninputProcessors=" + inputBaseline
        + "\nuiButtonReferencesBeforeConstruction=" + buttonReferencesBeforeConstruction
        + "\nuiButtonReferencesAfterConstruction=" + buttonReferencesAfterConstruction
        + "\nuiButtonReferencesWhileLive=" + liveButtonReferences
        + "\nquickSkillRebuilds=3"
        + "\npanelAssetBaselinesBalanced=" + panelReferencesBeforeConstruction.size()
        + "\nownedPanelTexturesDeleted=" + panelHandles.size
        + "\npanelUploadPixelsFreed=true"
        + "\nentryStageReleased=true\nentryLoadingReferencesBalanced=true"
        + "\nentryRetiredByClient=true\ngameRetiredByClient=true"
        + "\nuiButtonReferencesAfterExit=" + references("data/global/sfx/cursor/button.wav")
        + "\nremainingAssets=" + assets.getLoadedAssets() + "\nresult=PASS\n";
    Gdx.files.absolute(output).child("cycle-" + cycle + "-lifecycle.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenSessionLifecycleScreen", "[SESSION_LIFECYCLE] " + report.replace('\n', ' '));
    if (cycle < 3) Gdx.app.postRunnable(this::enter);
    else Gdx.app.exit();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException("Session lifecycle: " + message);
  }

  private int references(String name) {
    return assets.isLoaded(name) ? assets.getReferenceCount(name) : 0;
  }

  private static <T> T field(Object owner, String name, Class<T> type) {
    require(owner != null, "missing panel owner for " + name);
    try {
      java.lang.reflect.Field field = owner.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(owner));
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Session lifecycle: panel fixture " + name, failure);
    }
  }
}
