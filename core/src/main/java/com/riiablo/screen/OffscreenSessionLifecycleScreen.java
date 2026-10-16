package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
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
  private final boolean mobileUiLifecycle = Boolean.getBoolean("riiablo.offscreen-mobile-ui-lifecycle");
  private final Map<String, Integer> mobileReferencesBeforeConstruction = new TreeMap<>();
  private final Map<String, Integer> questReferencesBeforeConstruction = new TreeMap<>();
  private final Map<String, Integer> escapeReferencesBeforeConstruction = new TreeMap<>();
  private final Map<String, Integer> storageReferencesBeforeConstruction = new TreeMap<>();
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
    storageReferencesBeforeConstruction.clear();
    for (String path : new String[] {"data/global/ui/PANEL/supertransmogrifier.dc6",
        "data/global/ui/PANEL/miniconvert.DC6", "data/global/ui/PANEL/buysellbtn.DC6",
        "data/global/ui/PANEL/TradeStash.DC6", "data/global/ui/PANEL/goldcoinbtn.dc6",
        "data/global/ui/MENU/dialogbackground.dc6", "data/global/ui/PANEL/buysellbtn.dc6",
        "data/global/ui/BIGMENU/numberarrows.dc6"}) {
      storageReferencesBeforeConstruction.put(path, references(path));
    }
    presentationReferencesBeforeConstruction.clear();
    String language = Riiablo.language == null ? "eng" : Riiablo.language.resourceCode;
    escapeReferencesBeforeConstruction.clear();
    for (String path : new String[] {"data/local/ui/" + language + "/options.dc6",
        "data/local/ui/" + language + "/exit.dc6", "data/local/ui/" + language + "/returntogame.dc6",
        "data/global/ui/CURSOR/pentspin.DC6"}) {
      escapeReferencesBeforeConstruction.put(path, references(path));
    }
    for (String path : new String[] {"data/global/overlays/Gleam.dcc",
        "data/global/overlays/NPCSpeechBalloon.dcc", "data/global/overlays/ShrineManaRegen.dcc",
        "data/local/ui/" + language + "/act1/A1L1.dc6", "data/global/ui/MENU/boxpieces.DC6"}) {
      presentationReferencesBeforeConstruction.put(path, references(path));
    }
    buttonReferencesBeforeConstruction = references("data/global/sfx/cursor/button.wav");
    questReferencesBeforeConstruction.clear();
    for (String path : new String[] {"data/global/ui/MENU/questbackground.dc6",
        "data/global/ui/MENU/questlast.dc6", "data/global/ui/MENU/expquesttabs.dc6",
        "data/global/ui/MENU/questsockets.dc6", "data/global/ui/MENU/questdone.dc6",
        "data/global/ui/PANEL/buysellbtn.DC6"}) {
      questReferencesBeforeConstruction.put(path, references(path));
    }
    for (com.riiablo.codec.excel.Quests.Entry quest : Riiablo.files.quests) if (quest.visible) {
      String path = "data/global/ui/MENU/" + quest.icon + ".dc6";
      questReferencesBeforeConstruction.put(path, references(path));
    }
    mobileReferencesBeforeConstruction.clear();
    if (mobileUiLifecycle) for (String path : new String[] {
        "data/global/ui/PANEL/minipanelbtn.DC6", "data/global/ui/SPELLS/Skillicon.DC6",
        "data/global/ui/SPELLS/SoSkillicon.DC6"}) {
      mobileReferencesBeforeConstruction.put(path, references(path));
    }
    panelReferencesBeforeConstruction.clear();
    panelReferencesBeforeConstruction.put("data/global/ui/PANEL/invchar6Tab.DC6",
        references("data/global/ui/PANEL/invchar6Tab.DC6"));
    for (CharacterClass charClass : CharacterClass.values()) {
      String name = "data/global/ui/SPELLS/" + charClass.spellIcons + ".DC6";
      panelReferencesBeforeConstruction.put(name, references(name));
    }
    for (String name : new String[] {"data/global/ui/SPELLS/Skillicon.DC6",
        "data/global/ui/SPELLS/" + character.classId.spellIcons + ".dc6",
        "data/global/ui/SPELLS/" + character.classId.spellsBackground + ".dc6",
        "data/global/ui/PANEL/buysellbtn.DC6"}) {
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
    // Desktop-hosted production mobile widgets, not Android platform emulation.
    // The normal show/inject, first-frame escape reparenting and Client teardown
    // still run unchanged. Mobile coverage is opt-in; normal desktop is unchanged.
    if (mobileUiLifecycle) {
      require(screen.mobilePanel == null && screen.mobileControls == null, "desktop mobile fixture starts absent");
      screen.mobilePanel = new com.riiablo.screen.panel.MobilePanel();
      screen.mobilePanel.setWidth(screen.stage.getWidth());
      screen.stage.addActor(screen.mobilePanel);
      screen.mobileControls = new com.riiablo.screen.panel.MobileControls();
      screen.mobileControls.setPosition(screen.stage.getWidth() - screen.mobileControls.getWidth(),
          screen.mobilePanel.getHeight());
      screen.stage.addActor(screen.mobileControls);
    }
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
    Array<Actor> questActors = new Array<>();
    collectActors(screen.questsPanel, questActors);
    Array<com.riiablo.widget.DialogScroller> questDialogs = new Array<>();
    Actor firstQuest = null;
    for (Actor actor : questActors) {
      if (actor instanceof com.riiablo.widget.DialogScroller) questDialogs.add((com.riiablo.widget.DialogScroller) actor);
      if (actor.getClass().getSimpleName().equals("QuestButton")
          && field(actor, "nativeRecordIndex", Integer.class) == 1) firstQuest = actor;
    }
    require(firstQuest != null && questDialogs.size == 5, "Act I quest and five real scrollers sampled");
    click(firstQuest);
    screen.questsPanel.act(0); // consume initial record refresh before replaying
    com.riiablo.widget.DialogScroller replayDialog = questDialogs.first();
    Array<com.riiablo.audio.Audio.Instance> questSpeech = new Array<>();
    for (int replay = 0; replay < 3; replay++) {
      screen.questsPanel.setVisible(true);
      click(field(screen.questsPanel, "btnPlayQuest", Actor.class));
      com.riiablo.audio.Audio.Instance instance = field(replayDialog, "audio", com.riiablo.audio.Audio.Instance.class);
      require(instance != null, "quest replay owns audio instance");
      if (replay > 0) require(field(questSpeech.peek(), "stopped", Boolean.class), "replacement stopped old quest speech");
      questSpeech.add(instance);
      if (replay == 0) {
        screen.questsPanel.setVisible(false);
        require(field(instance, "stopped", Boolean.class) && field(replayDialog, "audio", Object.class) == null,
            "hiding quest log stops speech but keeps scroller reusable");
      }
    }
    Set<Integer> questHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : questReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey();
      require(references(path) > reference.getValue(), "live quest asset " + path);
      DC6 sheet = assets.get(path, DC6.class);
      for (int direction = 0; direction < sheet.getNumDirections(); direction++) {
        require(sheet.isLoaded(direction), "quest sheet uploaded " + path);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
          int handle = region.getTexture().getTextureObjectHandle();
          require(Gdx.gl.glIsTexture(handle), "live quest texture " + path);
          if (reference.getValue() == 0) questHandles.add(handle);
        }
      }
    }
    Array<Actor> mobileButtons = new Array<>();
    Set<Integer> mobileHandles = new HashSet<>();
    if (mobileUiLifecycle) {
      Actor escape = screen.mobilePanel.getEscapeButton();
      require(escape != null && escape.getParent() == screen.stage.getRoot(),
          "production first frame reparents mobile escape onto Stage");
      mobileButtons.addAll(screen.mobilePanel.getChildren());
      mobileButtons.add(escape);
      mobileButtons.add(field(screen.mobilePanel, "btnMap", Actor.class)); // never attached
      mobileButtons.addAll(screen.mobileControls.getChildren());
      require(mobileButtons.size == 15, "nine menu and six action buttons sampled");
      for (Map.Entry<String, Integer> reference : mobileReferencesBeforeConstruction.entrySet()) {
        String path = reference.getKey();
        require(references(path) > reference.getValue(), "live mobile asset " + path);
        DC6 dc = assets.get(path, DC6.class);
        for (int direction = 0; direction < dc.getNumDirections(); direction++) {
          require(dc.isLoaded(direction), "mobile sheet uploaded " + path);
          for (com.badlogic.gdx.graphics.g2d.TextureRegion region : dc.getRegions(direction)) {
            int handle = region.getTexture().getTextureObjectHandle();
            require(Gdx.gl.glIsTexture(handle), "live mobile texture " + path);
            if (reference.getValue() == 0) mobileHandles.add(handle);
          }
        }
      }
      // Leave the borrowed skill-slot observer active for production teardown.
      com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener gesture = field(screen.mobileControls,
          "gestureListener", com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener.class);
      screen.spellsQuickPanelR.setVisible(false);
      require(gesture.longPress(mobileButtons.peek(), 0, 0), "mobile long press opens chooser");
      require(screen.spellsQuickPanelR.isVisible(), "mobile chooser active at exit");
    }
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
    int retiredQuickButtons = 0;
    for (int rebuild = 0; rebuild < 3; rebuild++) {
      Array<Actor> previousButtons = new Array<>();
      collectButtons(screen.spellsQuickPanelL, previousButtons);
      collectButtons(screen.spellsQuickPanelR, previousButtons);
      require(previousButtons.size > 0, "live quick-skill buttons before rebuild");
      character.update(); // production quick-skill rebuild, including discarded buttons
      assets.finishLoading();
      for (Actor button : previousButtons) {
        require(button.getParent() == null && button.getListeners().size == 0,
            "rebuild retired previous quick-skill button");
      }
      retiredQuickButtons += previousButtons.size;
      require(references("data/global/sfx/cursor/button.wav") == liveButtonReferences,
          "quick-skill rebuild must not accumulate click-sound references");
    }
    Array<Actor> skillButtonsAtExit = new Array<>();
    collectButtons(screen.spellsPanel, skillButtonsAtExit);
    collectButtons(screen.spellsQuickPanelL, skillButtonsAtExit);
    collectButtons(screen.spellsQuickPanelR, skillButtonsAtExit);
    Array<Pixmap> pixels = new Array<>();
    // These are unmanaged textures, absent from AssetManager diagnostics.
    Texture fill = field(screen.inventoryPanel, "fill", Texture.class);
    Object controlWidget = field(screen.controlPanel, "controlWidget", Object.class);
    Object experienceWidget = field(screen.controlPanel, "experienceWidget", Object.class);
    Array<Texture> ownedPanelTextures = new Array<>();
    ownedPanelTextures.add(fill);
    ownedPanelTextures.add(field(controlWidget, "background", Texture.class));
    ownedPanelTextures.add(field(experienceWidget, "whitePixelTexture", Texture.class));
    ownedPanelTextures.add(field(screen.hirelingPanel, "fill", Texture.class));
    ownedPanelTextures.add(field(screen.mercenaryHud, "fill", Texture.class));
    collectGridTextures(screen.inventoryPanel, ownedPanelTextures);
    collectGridTextures(screen.stashPanel, ownedPanelTextures);
    collectGridTextures(screen.cubePanel, ownedPanelTextures);
    collectGridTextures(screen.vendorPanel, ownedPanelTextures);
    collectGridTextures(screen.controlPanel, ownedPanelTextures);
    require(ownedPanelTextures.size == 13, "all eight grid textures plus five private panel textures sampled");
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
    Array<Actor> storageActors = new Array<>();
    collectActors(screen.cubePanel, storageActors); collectActors(screen.stashPanel, storageActors);
    com.riiablo.widget.GoldAmountDialog stashGold = field(screen.stashPanel, "goldDialog",
        com.riiablo.widget.GoldAmountDialog.class);
    com.riiablo.widget.GoldAmountDialog inventoryGold = field(screen.inventoryPanel, "goldDialog",
        com.riiablo.widget.GoldAmountDialog.class);
    collectActors(inventoryGold, storageActors);
    Array<com.riiablo.widget.TextField> goldFields = new Array<>();
    Array<com.badlogic.gdx.scenes.scene2d.InputListener> goldInputs = new Array<>();
    for (com.riiablo.widget.GoldAmountDialog dialog : new com.riiablo.widget.GoldAmountDialog[] {stashGold, inventoryGold}) {
      goldFields.add(field(dialog, "amount", com.riiablo.widget.TextField.class));
      goldInputs.add((com.badlogic.gdx.scenes.scene2d.InputListener) dialog.getCaptureListeners().first());
    }
    screen.stashPanel.setVisible(true);
    clickAction(field(screen.stashPanel, "btnDropGold", Actor.class));
    require(stashGold.isOpen() && screen.stage.getKeyboardFocus() == goldFields.first(), "stash gold focus");
    screen.stashPanel.setVisible(false);
    require(!stashGold.isOpen() && screen.stage.getKeyboardFocus() != goldFields.first(), "stash hide releases modal focus");
    screen.stashPanel.setVisible(true);
    clickAction(field(screen.stashPanel, "btnDropGold", Actor.class));
    require(stashGold.isOpen(), "stash gold reusable after hiding");
    clickAction(field(screen.inventoryPanel, "btnDropGold", Actor.class));
    require(inventoryGold.isOpen() && stashGold.isOpen()
        && screen.stage.getKeyboardFocus() == goldFields.peek(), "both production gold owners open at exit");
    Set<Integer> storageHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : storageReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey();
      require(references(path) > reference.getValue(), "live storage asset " + path);
      DC6 sheet = assets.get(path, DC6.class);
      for (int direction = 0; direction < sheet.getNumDirections(); direction++) {
        require(sheet.isLoaded(direction), "storage sheet uploaded " + path);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
          int handle = region.getTexture().getTextureObjectHandle();
          require(Gdx.gl.glIsTexture(handle), "live storage texture " + path);
          if (reference.getValue() == 0) storageHandles.add(handle);
        }
      }
    }
    Array<Actor> escapeActors = new Array<>();
    collectActors(screen.escapePanel, escapeActors);
    Set<Integer> escapeHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : escapeReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey();
      require(references(path) > reference.getValue(), "live escape asset " + path);
      DC6 sheet = assets.get(path, DC6.class);
      for (int direction = 0; direction < sheet.getNumDirections(); direction++) {
        require(sheet.isLoaded(direction), "escape sheet uploaded " + path);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
          int handle = region.getTexture().getTextureObjectHandle();
          require(Gdx.gl.glIsTexture(handle), "live escape texture " + path);
          if (reference.getValue() == 0) escapeHandles.add(handle);
        }
      }
    }
    com.badlogic.gdx.scenes.scene2d.InputListener escapeInput = null;
    for (com.badlogic.gdx.scenes.scene2d.EventListener listener : screen.escapePanel.getListeners()) {
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.InputListener) {
        escapeInput = (com.badlogic.gdx.scenes.scene2d.InputListener) listener;
      }
    }
    require(escapeInput != null, "escape production key listener");
    for (int capture = 0; capture < 3; capture++) {
      screen.escapePanel.open();
      clickAction(field(screen.escapePanel, "options", Actor.class));
      Group options = field(screen.escapePanel, "optionsPage", Group.class);
      clickAction(options.getChildren().get(3));
      require(screen.escapePanel.getCurrentPage() == com.riiablo.screen.panel.EscapePanel.Page.CONTROLS,
          "production controls-page navigation");
      java.util.List<?> rows = field(screen.escapePanel, "controlRows", java.util.List.class);
      clickAction(field(rows.get(0), "primary", Actor.class));
      require(Riiablo.keys.isCaptureOwner(screen.escapePanel)
          && screen.stage.getKeyboardFocus() == screen.escapePanel, "production capture owns focus");
      if (capture == 0) {
        require(screen.escapePanel.navigateBack(), "escape parent navigation");
      } else if (capture == 1) {
        screen.escapePanel.setVisible(false);
      } else break; // Leave active capture to production Client teardown.
      require(!Riiablo.keys.isCaptureOwner(screen.escapePanel)
          && screen.stage.getKeyboardFocus() != screen.escapePanel, "capture released on navigation/hide");
    }
    Riiablo.client.setScreen(this); // real Client.hide/dispose, host stays alive
    require(screen.map.getZones().size == 0, "Client retired the game before returning");
    require(field(screen.cubePanel, "disposed", Boolean.class)
        && field(screen.stashPanel, "disposed", Boolean.class), "Client retired storage owners");
    screen.cubePanel.dispose(); screen.stashPanel.dispose();
    for (com.riiablo.widget.GoldAmountDialog dialog : new com.riiablo.widget.GoldAmountDialog[] {stashGold, inventoryGold}) {
      require(field(dialog, "disposed", Boolean.class) && field(dialog, "listener", Object.class) == null
          && field(dialog, "maximumProvider", Object.class) == null, "gold dialog callbacks retired");
      dialog.dispose(); dialog.dispose(); dialog.open(); require(!dialog.isOpen(), "retired gold dialog cannot reopen");
    }
    for (int i = 0; i < goldFields.size; i++) {
      require(screen.stage.getKeyboardFocus() != goldFields.get(i) && goldFields.get(i).isDisabled(), "gold focus and input retired");
      require(!goldInputs.get(i).keyDown(null, com.badlogic.gdx.Input.Keys.ENTER), "late gold submit ignored");
      require(!goldFields.get(i).getDefaultInputListener().keyTyped(null, '1'), "late gold typing ignored");
    }
    for (Actor actor : storageActors) {
      require(actor.getParent() == null && actor.getListeners().size == 0
          && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "storage actor retired");
    }
    for (Map.Entry<String, Integer> reference : storageReferencesBeforeConstruction.entrySet()) {
      require(references(reference.getKey()) == reference.getValue(), "storage asset baseline " + reference.getKey());
    }
    for (int handle : storageHandles) require(!Gdx.gl.glIsTexture(handle), "storage texture deleted");
    require(field(screen.escapePanel, "disposed", Boolean.class), "Client retired escape owner");
    require(!Riiablo.keys.isCaptureOwner(screen.escapePanel)
        && screen.stage.getKeyboardFocus() != screen.escapePanel, "Client released capture and focus");
    require(!escapeInput.keyDown(null, com.badlogic.gdx.Input.Keys.F12), "late escape key ignored");
    screen.escapePanel.dispose(); screen.escapePanel.dispose(); screen.escapePanel.open();
    require(!screen.escapePanel.isVisible(), "retired escape cannot reopen");
    for (Actor actor : escapeActors) {
      require(actor.getParent() == null && actor.getListeners().size == 0
          && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "escape actor retired");
    }
    for (Map.Entry<String, Integer> reference : escapeReferencesBeforeConstruction.entrySet()) {
      require(references(reference.getKey()) == reference.getValue(), "escape asset baseline " + reference.getKey());
    }
    for (int handle : escapeHandles) require(!Gdx.gl.glIsTexture(handle), "escape texture deleted");
    require(field(screen.questsPanel, "disposed", Boolean.class), "Client retired quest owner");
    screen.questsPanel.dispose(); screen.questsPanel.dispose();
    for (Actor actor : questActors) {
      require(actor.getParent() == null && actor.getListeners().size == 0
          && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "quest actor retired");
    }
    for (com.riiablo.widget.DialogScroller dialog : questDialogs) {
      require(field(dialog, "disposed", Boolean.class) && field(dialog, "audio", Object.class) == null
          && field(dialog, "listener", Object.class) == null, "quest scroller retired");
      dialog.act(10000); dialog.play("akara_act1_q1_init");
      require(field(dialog, "audio", Object.class) == null, "late quest playback ignored");
    }
    for (com.riiablo.audio.Audio.Instance instance : questSpeech) {
      require(field(instance, "stopped", Boolean.class), "quest speech stopped at exit");
    }
    for (Map.Entry<String, Integer> reference : questReferencesBeforeConstruction.entrySet()) {
      require(references(reference.getKey()) == reference.getValue(), "quest asset baseline " + reference.getKey());
    }
    for (int handle : questHandles) require(!Gdx.gl.glIsTexture(handle), "quest texture deleted");
    require(field(screen.spellsPanel, "disposed", Boolean.class)
        && field(screen.spellsQuickPanelL, "disposed", Boolean.class)
        && field(screen.spellsQuickPanelR, "disposed", Boolean.class), "Client retired all skill owners");
    screen.spellsPanel.dispose(); screen.spellsQuickPanelL.dispose(); screen.spellsQuickPanelR.dispose();
    for (Actor button : skillButtonsAtExit) {
      require(button.getParent() == null && button.getListeners().size == 0, "skill button retired at exit");
    }
    for (Object panel : new Object[] {screen.spellsQuickPanelL, screen.spellsQuickPanelR}) {
      com.riiablo.key.MappedKeyStateAdapter listener = field(panel, "mappedKeyListener",
          com.riiablo.key.MappedKeyStateAdapter.class);
      for (com.riiablo.key.MappedKey key : Keys.Skill) {
        require(!key.containsStateListener(listener), "skill hotkey listener retired");
      }
    }
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
    if (mobileUiLifecycle) {
      require(field(screen.mobilePanel, "disposed", Boolean.class)
          && field(screen.mobileControls, "disposed", Boolean.class), "Client retired both mobile owners");
      screen.mobilePanel.dispose(); screen.mobileControls.dispose();
      for (Actor button : mobileButtons) {
        require(button.getParent() == null && button.getListeners().size == 0, "mobile button retired");
      }
      require(screen.mobilePanel.getEscapeButton() == null, "retired escape is not exposed");
      require(field(screen.spellsQuickPanelR, "observer", Object.class) == null,
          "mobile chooser no longer borrows retired slot");
      require(!screen.spellsQuickPanelR.isVisible(), "retired mobile chooser hidden");
      for (Map.Entry<String, Integer> reference : mobileReferencesBeforeConstruction.entrySet()) {
        require(references(reference.getKey()) == reference.getValue(), "mobile asset baseline " + reference.getKey());
      }
      for (int handle : mobileHandles) require(!Gdx.gl.glIsTexture(handle), "mobile texture deleted");
    }
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
        + "\nquickSkillButtonsRetiredDuringRebuild=" + retiredQuickButtons
        + "\nskillButtonsRetiredAtExit=" + skillButtonsAtExit.size
        + "\nskillOwnersRetiredByClient=3"
        + "\nquestActorsRetired=" + questActors.size
        + "\nquestScrollersRetired=" + questDialogs.size
        + "\nquestSpeechReplays=" + questSpeech.size
        + "\nquestSpeechHideStops=1"
        + "\nquestAssetBaselinesBalanced=" + questReferencesBeforeConstruction.size()
        + "\nquestTexturesDeleted=" + questHandles.size()
        + "\nescapeActorsRetired=" + escapeActors.size
        + "\nescapeCaptures=3\nescapeNavigationRelease=true\nescapeHideRelease=true\nescapeExitRelease=true"
        + "\nescapeAssetBaselinesBalanced=" + escapeReferencesBeforeConstruction.size()
        + "\nescapeTexturesDeleted=" + escapeHandles.size()
        + "\nstorageActorsRetired=" + storageActors.size
        + "\ngoldDialogsRetired=2\nstashGoldHideAndReopen=true\ngoldDialogLateInputIgnored=true"
        + "\nstorageAssetBaselinesBalanced=" + storageReferencesBeforeConstruction.size()
        + "\nstorageTexturesDeleted=" + storageHandles.size()
        + "\npanelAssetBaselinesBalanced=" + panelReferencesBeforeConstruction.size()
        + "\nownedPanelTexturesDeleted=" + panelHandles.size
        + "\nmobileUiLifecycle=" + mobileUiLifecycle
        + "\nmobileButtonsRetired=" + mobileButtons.size
        + "\nmobileAssetBaselinesBalanced=" + mobileReferencesBeforeConstruction.size()
        + "\nmobileTexturesDeleted=" + mobileHandles.size()
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

  private static void collectGridTextures(Actor actor, Array<Texture> textures) {
    if (actor instanceof com.riiablo.screen.panel.ItemGrid) {
      textures.add(field(actor, "fill", Texture.class));
    }
    if (actor instanceof Group) {
      for (Actor child : ((Group) actor).getChildren()) collectGridTextures(child, textures);
    }
  }

  private static void collectButtons(Actor actor, Array<Actor> buttons) {
    if (actor instanceof com.riiablo.widget.Button) buttons.add(actor);
    if (actor instanceof Group) {
      for (Actor child : ((Group) actor).getChildren()) collectButtons(child, buttons);
    }
  }

  private static void collectActors(Actor actor, Array<Actor> actors) {
    actors.add(actor);
    if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collectActors(child, actors);
  }

  private static void click(Actor actor) {
    com.badlogic.gdx.scenes.scene2d.utils.ClickListener listener = null;
    for (com.badlogic.gdx.scenes.scene2d.EventListener candidate : actor.getListeners()) {
      if (candidate instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener) {
        listener = (com.badlogic.gdx.scenes.scene2d.utils.ClickListener) candidate;
      }
    }
    require(listener != null, "production quest click listener");
    listener.clicked(null, 0, 0);
  }

  /** Skip inherited no-op clicks (hover-only listeners) and invoke the actual menu action. */
  private static void clickAction(Actor actor) {
    com.badlogic.gdx.scenes.scene2d.utils.ClickListener action = null;
    for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners()) {
      if (!(listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)) continue;
      try {
        listener.getClass().getDeclaredMethod("clicked", com.badlogic.gdx.scenes.scene2d.InputEvent.class,
            float.class, float.class);
      } catch (NoSuchMethodException inherited) { continue; }
      action = (com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener;
    }
    require(action != null, "missing production menu action");
    action.clicked(null, 0, 0);
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
      Class<?> declaring = owner.getClass();
      while (declaring != null) {
        try { declaring.getDeclaredField(name); break; }
        catch (NoSuchFieldException missing) { declaring = declaring.getSuperclass(); }
      }
      if (declaring == null) throw new NoSuchFieldException(name);
      java.lang.reflect.Field field = declaring.getDeclaredField(name);
      field.setAccessible(true);
      return type.cast(field.get(owner));
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Session lifecycle: panel fixture " + name, failure);
    }
  }
}
