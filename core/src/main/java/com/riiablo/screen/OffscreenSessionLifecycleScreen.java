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
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.map.DT1;
import com.riiablo.save.CharData;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Set;

/** Real local GameScreen loading/render/exit/re-entry, not a manual visual acceptance test. */
public final class OffscreenSessionLifecycleScreen extends ScreenAdapter {
  private final CharData character;
  private final String output;
  private final AssetManager assets = Riiablo.assets;
  private final int inputBaseline = Riiablo.input.getProcessors().size;
  private final Map<String, DT1> previousTiles = new HashMap<>();
  private final Map<String, Integer> baselineReferences = new HashMap<>();
  private final Map<String, Integer> panelReferencesBeforeConstruction = new HashMap<>();
  private final Map<String, Integer> presentationReferencesBeforeConstruction = new TreeMap<>();
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
    presentationReferencesBeforeConstruction.clear();
    String language = Riiablo.language == null ? "eng" : Riiablo.language.resourceCode;
    for (String path : new String[] {"data/global/overlays/Gleam.dcc",
        "data/global/overlays/NPCSpeechBalloon.dcc", "data/global/overlays/ShrineManaRegen.dcc",
        "data/local/ui/" + language + "/act1/A1L1.dc6", "data/global/ui/MENU/boxpieces.DC6"}) {
      presentationReferencesBeforeConstruction.put(path, references(path));
    }
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
    // Exercise actual dialog replacement, explicit close, and World-owned final teardown.
    // These add their own border references on top of the already-live NPC/panel consumers.
    com.riiablo.engine.client.DialogManager dialogs =
        screen.engine.getSystem(com.riiablo.engine.client.DialogManager.class);
    String speech = "akara_act1_intro";
    require(Riiablo.files.speech.get(speech) != null, "real speech fixture");
    int boxReferencesWhileLive = references("data/global/ui/MENU/boxpieces.DC6");
    Array<com.riiablo.widget.NpcMenu> sessionMenus = new Array<>();
    com.artemis.utils.IntBag menuOwners = screen.engine.getAspectSubscriptionManager()
        .get(com.artemis.Aspect.all(com.riiablo.engine.server.component.MenuWrapper.class)).getEntities();
    for (int i = 0; i < menuOwners.size(); i++) {
      com.riiablo.widget.NpcMenu menu = screen.engine
          .getMapper(com.riiablo.engine.server.component.MenuWrapper.class).get(menuOwners.get(i)).menu;
      require(menu != null, "live NPC root menu");
      collectMenus(menu, sessionMenus);
    }
    require(sessionMenus.size > 0 && boxReferencesWhileLive == sessionMenus.size + 2,
        "every NPC menu and both panels own one shared-box reference");
    dialogs.setDialog(new com.riiablo.widget.NpcDialogBox(speech, d -> dialogs.setDialog(null)));
    assets.finishLoading();
    require(references("data/global/ui/MENU/boxpieces.DC6") == boxReferencesWhileLive + 1,
        "initial dialog owns exactly one border");
    for (int replacement = 0; replacement < 3; replacement++) {
      dialogs.setDialog(new com.riiablo.widget.NpcDialogBox(speech, d -> dialogs.setDialog(null)));
      assets.finishLoading();
      require(references("data/global/ui/MENU/boxpieces.DC6") == boxReferencesWhileLive + 1,
          "dialog replacement owns exactly one border");
    }
    dialogs.setDialog(null);
    require(references("data/global/ui/MENU/boxpieces.DC6") == boxReferencesWhileLive,
        "dialog close restores NPC/panel border baseline");
    dialogs.setDialog(new com.riiablo.widget.NpcDialogBox(speech, d -> dialogs.setDialog(null)));
    assets.finishLoading();
    Set<Integer> presentationHandles = new HashSet<>();
    Set<Pixmap> presentationPixels = new HashSet<>();
    for (Map.Entry<String, Integer> reference : presentationReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey();
      require(references(path) > reference.getValue(), "session owns presentation asset: " + path);
      DC dc = (DC) assets.get(path);
      Set<Integer> liveHandles = new HashSet<>();
      Set<Pixmap> livePixels = new HashSet<>();
      for (int direction = 0; direction < dc.getNumDirections(); direction++) {
        require(dc.isLoaded(direction), "presentation direction loaded: " + path);
        for (int frame = 0; frame < dc.getNumFramesPerDir(); frame++) {
          livePixels.add(dc.getPixmap(direction, frame));
        }
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : dc.getRegions(direction)) {
          Texture texture = region.getTexture();
          require(Gdx.gl.glIsTexture(texture.getTextureObjectHandle()), "live presentation texture: " + path);
          liveHandles.add(texture.getTextureObjectHandle());
        }
      }
      if (dc instanceof DC6) {
        Pixmap[][] uploads = field(dc, "pixmaps", Pixmap[][].class);
        if (uploads != null) for (Pixmap[] direction : uploads) {
          if (direction != null) for (Pixmap pixel : direction) livePixels.add(pixel);
        }
      }
      require(!liveHandles.isEmpty() && !livePixels.isEmpty(), "presentation GL/pixel coverage: " + path);
      for (Pixmap pixel : livePixels) require(pixel != null && !pixel.isDisposed(), "live presentation pixels: " + path);
      // External owners keep their GL resources; only zero-baseline assets must be destroyed.
      if (reference.getValue() == 0) {
        presentationHandles.addAll(liveHandles); presentationPixels.addAll(livePixels);
      }
    }
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
    for (com.riiablo.widget.NpcMenu menu : sessionMenus) {
      require(menu.isDisposed() && menu.getParent() == null, "visible or unattached NPC menu retired");
    }
    require(dialogs.getDialog() == null, "World retired the active dialog");
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
    for (Map.Entry<String, Integer> reference : presentationReferencesBeforeConstruction.entrySet()) {
      require(references(reference.getKey()) == reference.getValue(),
          "presentation references balanced: " + reference.getKey() + " expected=" + reference.getValue()
              + " actual=" + references(reference.getKey()));
    }
    for (int handle : presentationHandles) require(!Gdx.gl.glIsTexture(handle), "presentation texture deleted");
    for (Pixmap pixel : presentationPixels) require(pixel.isDisposed(), "presentation pixels released");
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
        + "\npresentationAssetBaselinesBalanced=" + presentationReferencesBeforeConstruction.size()
        + "\npresentationTexturesDeleted=" + presentationHandles.size()
        + "\npresentationPixelsFreed=" + presentationPixels.size()
        + "\nsharedBoxReferencesBeforeConstruction="
            + presentationReferencesBeforeConstruction.get("data/global/ui/MENU/boxpieces.DC6")
        + "\nsharedBoxReferencesWhileLive=" + boxReferencesWhileLive
        + "\nsharedBoxReferencesAfterExit=" + references("data/global/ui/MENU/boxpieces.DC6")
        + "\ndialogReplacements=3\ndialogRetiredByWorld=true"
        + "\nnpcMenusRetired=" + sessionMenus.size
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

  @SuppressWarnings("unchecked") private static void collectMenus(com.riiablo.widget.NpcMenu menu,
      Array<com.riiablo.widget.NpcMenu> menus) {
    require(!menu.isDisposed(), "live NPC menu");
    menus.add(menu);
    Array<com.riiablo.widget.NpcMenu> children = field(menu, "subMenus", Array.class);
    for (com.riiablo.widget.NpcMenu child : children) collectMenus(child, menus);
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
