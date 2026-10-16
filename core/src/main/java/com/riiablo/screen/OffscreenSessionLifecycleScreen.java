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
  private final Map<String, Integer> inventoryReferencesBeforeConstruction = new TreeMap<>();
  private final Map<String, Integer> vendorReferencesBeforeConstruction = new TreeMap<>();
  private final Map<String, Integer> mercenaryReferencesBeforeConstruction = new TreeMap<>();
  private final Map<String, Integer> hudReferencesBeforeConstruction = new TreeMap<>();
  private final Map<String, Integer> controlReferencesBeforeConstruction = new TreeMap<>();
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
    controlReferencesBeforeConstruction.clear();
    for (String path : new String[] {"data/global/ui/PANEL/ctrlpnl7.DC6",
        "data/global/ui/PANEL/hlthmana.DC6", "data/global/ui/PANEL/overlap.DC6",
        "data/global/ui/PANEL/invwarn.dc6", "data/global/ui/PANEL/level.DC6",
        "data/global/sfx/cursor/levelup.wav", "data/global/ui/PANEL/ctrlpnl_popbelt.DC6",
        "data/global/ui/SPELLS/Skillicon.DC6", "data/global/ui/PANEL/runbutton.dc6",
        "data/global/ui/PANEL/minipanel.dc6", "data/global/ui/PANEL/menubutton.DC6",
        "data/global/ui/PANEL/minipanelbtn.DC6"}) controlReferencesBeforeConstruction.put(path, references(path));
    for (CharacterClass charClass : CharacterClass.values()) {
      String path = "data/global/ui/SPELLS/" + charClass.spellIcons + ".DC6";
      controlReferencesBeforeConstruction.put(path, references(path));
    }
    hudReferencesBeforeConstruction.clear();
    for (String icon : new String[] {"rogueicon.dc6", "act2hireableicon.dc6", "act3hireableicon.dc6",
        "barbhirable_icon.dc6", "Raven.dc6", "Wolf.dc6", "OakSage.dc6", "Vines.dc6", "Bear.dc6",
        "valkarieicon.dc6", "amazonicon.dc6", "skeletonicon.dc6", "skeletonmageicon.dc6", "golemicon.dc6",
        "revivedicon.dc6", "ShadowAssassin.dc6", "sorceressicon.dc6"}) {
      String path = "data/global/ui/HIREABLES/" + icon;
      hudReferencesBeforeConstruction.put(path, references(path));
    }
    mercenaryReferencesBeforeConstruction.clear();
    for (String path : new String[] {"data/global/ui/PANEL/NpcInv.dc6",
        "data/global/ui/PANEL/buysellbtn.DC6", "data/global/ui/PANEL/inv_armor.DC6",
        "data/global/ui/PANEL/inv_helm_glove.DC6", "data/global/ui/PANEL/inv_weapons.DC6",
        "data/global/ui/MENU/textslid.dc6"}) mercenaryReferencesBeforeConstruction.put(path, references(path));
    vendorReferencesBeforeConstruction.clear();
    for (String path : new String[] {"data/global/ui/PANEL/buysell.dc6",
        "data/global/ui/PANEL/buyselltabs.DC6", "data/global/ui/PANEL/buysellbtn.DC6",
        "data/global/ui/MENU/boxpieces.DC6"}) vendorReferencesBeforeConstruction.put(path, references(path));
    inventoryReferencesBeforeConstruction.clear();
    for (String path : new String[] {"data/global/ui/PANEL/invchar6.DC6",
        "data/global/ui/PANEL/invchar6Tab.DC6", "data/global/ui/PANEL/buysellbtn.DC6",
        "data/global/ui/PANEL/goldcoinbtn.dc6", "data/global/ui/PANEL/inv_armor.DC6",
        "data/global/ui/PANEL/inv_belt.DC6", "data/global/ui/PANEL/inv_boots.DC6",
        "data/global/ui/PANEL/inv_helm_glove.DC6", "data/global/ui/PANEL/inv_ring_amulet.DC6",
        "data/global/ui/PANEL/inv_weapons.DC6"}) {
      inventoryReferencesBeforeConstruction.put(path, references(path));
    }
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
    Array<Actor> retiredPartyMessages = new Array<>();
    for (int reopen = 0; reopen < 3; reopen++) {
      screen.setLeftPanel(screen.partyPanel);
      screen.partyPanel.act(0);
      for (Actor old : retiredPartyMessages) {
        require(old.getParent() == null && old.getListeners().size == 0 && old.getActions().size == 0,
            "reopening offline party retires old roster message");
        old.act(1); old.draw(Riiablo.batch, 1);
      }
      if (reopen < 2) retiredPartyMessages.add(field(screen.partyPanel, "roster", Group.class).getChild(0));
      screen.stage.draw(); // Real fonts, shared batch and normal Stage layout; no network requests.
      screen.setLeftPanel(null);
    }
    screen.setLeftPanel(screen.tradePanel); screen.tradePanel.act(0); screen.stage.draw();
    screen.setLeftPanel(null);
    Array<Actor> networkPanelActors = new Array<>();
    collectActors(screen.partyPanel, networkPanelActors); collectActors(screen.tradePanel, networkPanelActors);
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> networkPanelClicks = new Array<>();
    for (Actor actor : networkPanelActors) for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners())
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
        networkPanelClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
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
    com.riiablo.screen.panel.ItemGrid dynamicGrid = field(screen.inventoryPanel, "grid", com.riiablo.screen.panel.ItemGrid.class);
    Array<Actor> oldItemViews = new Array<>();
    Array<com.riiablo.item.Item> displayedItems = new Array<>();
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> oldItemClicks = new Array<>();
    for (Actor child : dynamicGrid.getChildren()) {
      com.riiablo.item.Item item = com.riiablo.screen.panel.ItemGrid.targetItem(child);
      if (item == null) continue;
      oldItemViews.add(child); displayedItems.add(item);
      for (com.badlogic.gdx.scenes.scene2d.EventListener listener : child.getListeners())
        if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
          oldItemClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
    }
    require(oldItemViews.size > 0, "real inventory item views for dynamic retirement");
    dynamicGrid.syncItems(null); // Display-only removal: ItemData and item ownership are unchanged.
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : oldItemClicks) click.clicked(null, 0, 0);
    for (int i = 0; i < oldItemViews.size; i++) {
      Actor old = oldItemViews.get(i); com.riiablo.item.Item item = displayedItems.get(i);
      require(old.getParent() == null && old.getListeners().size == 0
          && old.getCaptureListeners().size == 0 && old.getActions().size == 0, "removed item view retired");
      require(com.riiablo.screen.panel.ItemGrid.targetItem(old) == null && old.hit(0, 0, true) == null,
          "removed item cannot be targeted");
      float x = item.getX(), y = item.getY(); old.setPosition(999, 999);
      require(item.getX() == x && item.getY() == y, "removed item view cannot move borrowed wrapper");
      old.act(1); old.draw(Riiablo.batch, 1); // No begin(): inactive views must not touch the real GL batch.
    }
    dynamicGrid.syncItems(displayedItems);
    require(dynamicGrid.getChildren().size == oldItemViews.size, "inventory views restored without changing ItemData");
    for (Actor current : dynamicGrid.getChildren()) {
      require(!oldItemViews.contains(current, true)
          && com.riiablo.screen.panel.ItemGrid.targetItem(current) != null, "fresh inventory view is active");
    }
    int retiredQuickButtons = 0;
    int retiredQuickCallbacks = 0;
    for (int rebuild = 0; rebuild < 3; rebuild++) {
      Array<Actor> previousButtons = new Array<>();
      collectButtons(screen.spellsQuickPanelL, previousButtons);
      collectButtons(screen.spellsQuickPanelR, previousButtons);
      require(previousButtons.size > 0, "live quick-skill buttons before rebuild");
      Array<Actor> previousSkillTrees = new Array<>();
      Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> previousSkillClicks = new Array<>();
      int leftAction = character.getAction(com.badlogic.gdx.Input.Buttons.LEFT);
      int rightAction = character.getAction(com.badlogic.gdx.Input.Buttons.RIGHT);
      for (Actor button : previousButtons) {
        collectActors(button, previousSkillTrees);
        collectActors(field(button, "details", Actor.class), previousSkillTrees);
        for (com.badlogic.gdx.scenes.scene2d.EventListener listener : button.getListeners())
          if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
            previousSkillClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
      }
      character.update(); // production quick-skill rebuild, including discarded buttons
      assets.finishLoading();
      for (Actor button : previousButtons) {
        require(button.getParent() == null && button.getListeners().size == 0,
            "rebuild retired previous quick-skill button");
        com.riiablo.widget.HotkeyButton hotkey = (com.riiablo.widget.HotkeyButton) button;
        require(hotkey.refreshDisabled() && hotkey.isDisabled() && hotkey.getMapping() == null,
            "rebuilt skill button cannot refresh or retain mapping");
        require(field(hotkey, "chargedSkill", Object.class) == null, "rebuilt skill releases borrowed charge");
        hotkey.map(Keys.Skill[0]); hotkey.copy(hotkey);
        hotkey.draw((com.badlogic.gdx.graphics.g2d.Batch) Riiablo.batch, 1); hotkey.draw(Riiablo.batch, 1);
      }
      for (Actor old : previousSkillTrees) require(old.getParent() == null && old.getListeners().size == 0
          && old.getCaptureListeners().size == 0 && old.getActions().size == 0, "skill subtree and details retired");
      for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : previousSkillClicks) click.clicked(null, 0, 0);
      retiredQuickCallbacks += previousSkillClicks.size;
      require(character.getAction(com.badlogic.gdx.Input.Buttons.LEFT) == leftAction
          && character.getAction(com.badlogic.gdx.Input.Buttons.RIGHT) == rightAction, "old skill callbacks cannot select");
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
    Array<Actor> inventoryActors = new Array<>(); collectActors(screen.inventoryPanel, inventoryActors);
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> inventoryClicks = new Array<>();
    for (Actor actor : inventoryActors) for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners()) {
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
        inventoryClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
    }
    com.riiablo.save.ItemData.AlternateListener inventoryAlternate = field(screen.inventoryPanel,
        "alternateListener", com.riiablo.save.ItemData.AlternateListener.class);
    Array<?> alternateListeners = field(character.getItems(), "alternateListeners", Array.class);
    require(containsIdentity(alternateListeners, inventoryAlternate), "inventory alternate listener registered");
    Set<Integer> inventoryHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : inventoryReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey(); require(references(path) > reference.getValue(), "live inventory asset " + path);
      DC6 sheet = assets.get(path, DC6.class);
      for (int direction = 0; direction < sheet.getNumDirections(); direction++) {
        require(sheet.isLoaded(direction), "inventory sheet uploaded " + path);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
          int handle = region.getTexture().getTextureObjectHandle();
          require(Gdx.gl.glIsTexture(handle), "live inventory texture " + path);
          if (reference.getValue() == 0) inventoryHandles.add(handle);
        }
      }
    }
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
    // Exercise only the production confirmation UI, not a purchase or NPC stock generation.
    com.riiablo.item.Item vendorItem = new com.riiablo.item.Item(); vendorItem.reset();
    vendorItem.setBase(Riiablo.files.misc.get("hp1")); vendorItem.quality = com.riiablo.item.Quality.NORMAL;
    vendorItem.flags2 |= com.riiablo.item.Item.ITEMFLAG2_INSTORE; vendorItem.vendorPrice = 50;
    screen.vendorPanel.config(com.riiablo.screen.panel.VendorPanel.TRADER, Array.with(vendorItem));
    com.riiablo.screen.panel.VendorGrid vendorGrid = field(screen.vendorPanel, "activeGrid",
        com.riiablo.screen.panel.VendorGrid.class);
    com.riiablo.screen.panel.VendorGrid.PurchaseListener vendorPurchase = field(vendorGrid, "purchaseListener",
        com.riiablo.screen.panel.VendorGrid.PurchaseListener.class);
    com.badlogic.gdx.scenes.scene2d.InputListener vendorDismiss = field(screen.vendorPanel,
        "purchasePromptDismissListener", com.badlogic.gdx.scenes.scene2d.InputListener.class);
    Group vendorStageRoot = screen.stage.getRoot();
    for (int opening = 0; opening < 2; opening++) {
      screen.vendorPanel.setVisible(true); vendorPurchase.onPurchase(vendorItem, false);
      require(vendorStageRoot.getCaptureListeners().contains(vendorDismiss, true), "vendor prompt capture acquired");
      require(field(screen.vendorPanel, "purchasePrompt", Actor.class).isVisible(), "vendor prompt visible");
      if (opening == 0) {
        screen.vendorPanel.setVisible(false);
        require(!vendorStageRoot.getCaptureListeners().contains(vendorDismiss, true), "vendor hide releases capture");
      }
    }
    Array<Actor> vendorActors = new Array<>(); collectActors(screen.vendorPanel, vendorActors);
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> vendorClicks = new Array<>();
    for (Actor actor : vendorActors) for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners())
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
        vendorClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
    Set<Integer> vendorHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : vendorReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey(); require(references(path) > reference.getValue(), "live vendor asset " + path);
      DC6 sheet = assets.get(path, DC6.class);
      for (int direction = 0; direction < sheet.getNumDirections(); direction++) {
        require(sheet.isLoaded(direction), "vendor sheet uploaded " + path);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
          int handle = region.getTexture().getTextureObjectHandle(); require(Gdx.gl.glIsTexture(handle), "live vendor texture " + path);
          if (reference.getValue() == 0) vendorHandles.add(handle);
        }
      }
    }
    // Invalid NPC type deliberately exercises the production modal/focus path without
    // generating candidates, hiring, charging, or changing the saved mercenary.
    Actor hireKeyboard = new Actor(), hireScroll = new Actor();
    screen.stage.addActor(hireKeyboard); screen.stage.addActor(hireScroll);
    screen.stage.setKeyboardFocus(hireKeyboard); screen.stage.setScrollFocus(hireScroll);
    screen.hireMercenaryPanel.open(-1, -1);
    require(screen.stage.getKeyboardFocus() == screen.hireMercenaryPanel
        && screen.stage.getScrollFocus() == screen.hireMercenaryPanel, "hire modal owns both focus slots");
    screen.hireMercenaryPanel.setVisible(false);
    require(screen.stage.getKeyboardFocus() == hireKeyboard && screen.stage.getScrollFocus() == hireScroll,
        "hire hide restores original focus");
    com.riiablo.engine.server.npc.HirelingOffer candidate = new com.riiablo.engine.server.npc.HirelingOffer();
    candidate.index = -1; candidate.seed = 123; candidate.level = 3; candidate.cost = 100;
    screen.hireMercenaryPanel.presentOffers(Array.with(candidate));
    Group hireRows = field(screen.hireMercenaryPanel, "rows", Group.class);
    Array<Actor> oldHireRows = new Array<>();
    for (Actor row : hireRows.getChildren()) collectActors(row, oldHireRows);
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> retiredHireClicks = new Array<>();
    for (Actor actor : oldHireRows) for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners())
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
        retiredHireClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
    screen.hireMercenaryPanel.presentOffers(Array.with(candidate));
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : retiredHireClicks) click.clicked(null, 0, 0);
    require(field(screen.hireMercenaryPanel, "selected", Object.class) == null, "retired hire rows cannot select");
    for (Actor row : oldHireRows) require(row.getParent() == null && row.getListeners().size == 0,
        "hire rebuild retires row tree");
    Array<Actor> mercenaryActors = new Array<>(); collectActors(screen.hirelingPanel, mercenaryActors);
    collectActors(screen.hireMercenaryPanel, mercenaryActors);
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> mercenaryClicks = new Array<>();
    for (Actor actor : mercenaryActors) for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners())
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
        mercenaryClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
    com.badlogic.gdx.scenes.scene2d.InputListener hireInput = null;
    for (com.badlogic.gdx.scenes.scene2d.EventListener listener : screen.hireMercenaryPanel.getListeners())
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.InputListener)
        hireInput = (com.badlogic.gdx.scenes.scene2d.InputListener) listener;
    require(hireInput != null, "hire production input listener");
    screen.hireMercenaryPanel.open(-1, -1); // Leave focus ownership to real Client teardown.
    Set<Integer> mercenaryHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : mercenaryReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey(); require(references(path) > reference.getValue(), "live mercenary asset " + path);
      DC6 sheet = assets.get(path, DC6.class);
      for (int direction = 0; direction < sheet.getNumDirections(); direction++) {
        require(sheet.isLoaded(direction), "mercenary sheet uploaded " + path);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
          int handle = region.getTexture().getTextureObjectHandle(); require(Gdx.gl.glIsTexture(handle), "live mercenary texture " + path);
          if (reference.getValue() == 0) mercenaryHandles.add(handle);
        }
      }
    }
    screen.mercenaryHud.showTemporaryMessage("Lifecycle");
    require(field(screen.mercenaryHud, "feedback", Actor.class).isVisible(), "HUD feedback active before exit");
    require(field(screen.mercenaryHud, "mercenaries", Object.class) != null
        && field(screen.mercenaryHud, "summonedPets", Object.class) != null, "HUD borrowed world subscriptions active");
    Array<Actor> hudActors = new Array<>(); collectActors(screen.mercenaryHud, hudActors);
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> hudClicks = new Array<>();
    for (Actor actor : hudActors) for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners())
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
        hudClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
    Set<Integer> hudHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : hudReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey(); require(references(path) > reference.getValue(), "live HUD asset " + path);
      DC6 sheet = assets.get(path, DC6.class);
      for (int direction = 0; direction < sheet.getNumDirections(); direction++) {
        require(sheet.isLoaded(direction), "HUD sheet uploaded " + path);
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
          int handle = region.getTexture().getTextureObjectHandle(); require(Gdx.gl.glIsTexture(handle), "live HUD texture " + path);
          if (reference.getValue() == 0) hudHandles.add(handle);
        }
      }
    }
    Array<Actor> controlActors = new Array<>(); collectActors(screen.controlPanel, controlActors);
    // Keep coverage even if an owned escape button is reparented by its host.
    Actor controlEscape = screen.controlPanel.getEscapeButton();
    require(controlEscape != null, "control escape button still borrowed by EscapePanel");
    if (!controlActors.contains(controlEscape, true)) collectActors(controlEscape, controlActors);
    com.riiablo.widget.HotkeyButton controlLeft = screen.controlPanel.getLeftSkill();
    com.riiablo.widget.HotkeyButton controlRight = screen.controlPanel.getRightSkill();
    Object controlBelt = field(controlWidget, "belt", Object.class);
    com.riiablo.key.MappedKey.AssignmentListener[] controlAssignments =
        field(controlBelt, "assignments", com.riiablo.key.MappedKey.AssignmentListener[].class).clone();
    Array<com.badlogic.gdx.scenes.scene2d.utils.ClickListener> controlClicks = new Array<>();
    for (Actor actor : controlActors) for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners())
      if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener)
        controlClicks.add((com.badlogic.gdx.scenes.scene2d.utils.ClickListener) listener);
    Set<Integer> controlHandles = new HashSet<>();
    for (Map.Entry<String, Integer> reference : controlReferencesBeforeConstruction.entrySet()) {
      String path = reference.getKey(); require(references(path) > reference.getValue(), "live control asset " + path);
      if (!path.endsWith(".wav")) {
        DC6 sheet = assets.get(path, DC6.class);
        // Skill sheets for other classes may have been loaded but not uploaded yet.
        for (int direction = 0; direction < sheet.getNumDirections(); direction++) if (sheet.isLoaded(direction))
          for (com.badlogic.gdx.graphics.g2d.TextureRegion region : sheet.getRegions(direction)) {
            int handle = region.getTexture().getTextureObjectHandle(); require(Gdx.gl.glIsTexture(handle), "live control texture " + path);
            if (reference.getValue() == 0) controlHandles.add(handle);
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
    require(field(screen.partyPanel, "disposed", Boolean.class) && field(screen.tradePanel, "disposed", Boolean.class),
        "Client retired both network panel owners");
    screen.partyPanel.dispose(); screen.tradePanel.dispose();
    screen.partyPanel.setVisible(true); screen.tradePanel.setVisible(true);
    screen.partyPanel.act(1); screen.tradePanel.act(1);
    screen.partyPanel.draw(Riiablo.batch, 1); screen.tradePanel.draw(Riiablo.batch, 1);
    screen.tradePanel.applyNetworkState(new com.riiablo.engine.client.ClientNetworkReceiver());
    require(!screen.partyPanel.isVisible() && !screen.tradePanel.isVisible()
        && screen.partyPanel.hit(0, 0, true) == null && screen.tradePanel.hit(0, 0, true) == null,
        "retired network panels cannot reopen or hit-test");
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : networkPanelClicks) click.clicked(null, 0, 0);
    for (Actor actor : networkPanelActors) {
      require(actor.getParent() == null && actor.getListeners().size == 0
          && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "network panel actor retired");
      if (actor instanceof com.riiablo.widget.Label) {
        actor.act(1); actor.draw(Riiablo.batch, 1);
        ((com.riiablo.widget.Label) actor).draw(Riiablo.batch, 1);
      }
    }
    for (Actor panel : new Actor[] {screen.partyPanel, screen.tradePanel})
      require(field(panel, "owner", Object.class) == null && field(panel, "world", Object.class) == null
          && field(panel, "receiver", Object.class) == null && field(panel, "synchronizer", Object.class) == null,
          "network panel borrowed session links cleared");
    require(field(screen.controlPanel, "disposed", Boolean.class), "Client retired control panel");
    screen.controlPanel.dispose(); screen.controlPanel.dispose(); screen.controlPanel.setVisible(true);
    screen.controlPanel.act(1); screen.controlPanel.acknowledgeQuestLog(); screen.controlPanel.setMinipanelVisible(true);
    require(!screen.controlPanel.isVisible() && screen.controlPanel.hit(0, 0, true) == null
        && screen.controlPanel.getEscapeButton() == null, "retired control cannot reopen or hit-test");
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : controlClicks) click.clicked(null, 0, 0);
    for (Actor actor : controlActors) require(actor.getParent() == null && actor.getListeners().size == 0
        && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "control actor retired");
    for (int i = 0; i < controlAssignments.length; i++) {
      require(!Keys.Belt[i].containsAssignmentListener(controlAssignments[i]), "control belt assignment detached");
      controlAssignments[i].onAssigned(Keys.Belt[i], 0, com.badlogic.gdx.Input.Keys.A);
      controlAssignments[i].onUnassigned(Keys.Belt[i], 0, com.badlogic.gdx.Input.Keys.A);
    }
    require(controlLeft.refreshDisabled() && controlRight.refreshDisabled(), "retired HUD skills inactive");
    require(field(screen.controlPanel, "itemController", Object.class) == null
        && field(screen.controlPanel, "owner", Object.class) == null
        && field(screen.controlPanel, "world", Object.class) == null, "control borrowed state cleared");
    for (Map.Entry<String, Integer> reference : controlReferencesBeforeConstruction.entrySet())
      require(references(reference.getKey()) == reference.getValue(), "control asset baseline " + reference.getKey());
    for (int handle : controlHandles) require(!Gdx.gl.glIsTexture(handle), "control texture deleted");
    require(field(screen.mercenaryHud, "disposed", Boolean.class), "Client retired mercenary HUD");
    screen.mercenaryHud.dispose(); screen.mercenaryHud.dispose(); screen.mercenaryHud.setVisible(true);
    screen.mercenaryHud.showTemporaryMessage("late"); screen.mercenaryHud.act(1);
    require(!screen.mercenaryHud.isVisible() && !screen.mercenaryHud.useCursorPotion()
        && !screen.mercenaryHud.containsStagePoint(10, 30), "retired HUD cannot reopen, feed or hit-test");
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : hudClicks) {
      click.clicked(null, 10, 30); click.enter(null, 10, 30, 0, null); click.exit(null, 10, 30, 0, null);
      require(!click.mouseMoved(null, 10, 30), "retired HUD hover ignored");
    }
    for (Actor actor : hudActors) require(actor.getParent() == null && actor.getListeners().size == 0
        && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "HUD actor retired");
    require(field(screen.mercenaryHud, "feedback", Actor.class) == null
        && field(screen.mercenaryHud, "mercenaries", Object.class) == null
        && field(screen.mercenaryHud, "summonedPets", Object.class) == null
        && field(screen.mercenaryHud, "itemController", Object.class) == null, "HUD borrowed state cleared");
    for (Map.Entry<String, Integer> reference : hudReferencesBeforeConstruction.entrySet())
      require(references(reference.getKey()) == reference.getValue(), "HUD asset baseline " + reference.getKey());
    for (int handle : hudHandles) require(!Gdx.gl.glIsTexture(handle), "HUD texture deleted");
    require(field(screen.hirelingPanel, "disposed", Boolean.class)
        && field(screen.hireMercenaryPanel, "disposed", Boolean.class), "Client retired mercenary panels");
    screen.hirelingPanel.dispose(); screen.hireMercenaryPanel.dispose();
    screen.hirelingPanel.setVisible(true); screen.hireMercenaryPanel.setVisible(true);
    screen.hireMercenaryPanel.open(-1, -1); screen.hireMercenaryPanel.presentOffers(Array.with(candidate));
    require(!screen.hirelingPanel.isVisible() && !screen.hireMercenaryPanel.isVisible(), "retired mercenary panels cannot reopen");
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : mercenaryClicks) click.clicked(null, 0, 0);
    require(!hireInput.keyDown(null, com.badlogic.gdx.Input.Keys.ENTER)
        && !hireInput.scrolled(null, 0, 0, 0, 1), "late hire input ignored");
    require(field(screen.hireMercenaryPanel, "focusStage", Stage.class) == null, "hire original focus stage released");
    require(screen.stage.getKeyboardFocus() != screen.hireMercenaryPanel
        && screen.stage.getScrollFocus() != screen.hireMercenaryPanel, "hire no focus retained after exit");
    for (Actor actor : mercenaryActors) require(actor.getParent() == null && actor.getListeners().size == 0
        && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "mercenary actor retired");
    for (Map.Entry<String, Integer> reference : mercenaryReferencesBeforeConstruction.entrySet())
      require(references(reference.getKey()) == reference.getValue(), "mercenary asset baseline " + reference.getKey());
    for (int handle : mercenaryHandles) require(!Gdx.gl.glIsTexture(handle), "mercenary texture deleted");
    require(field(screen.vendorPanel, "disposed", Boolean.class), "Client retired vendor owner");
    require(!vendorStageRoot.getCaptureListeners().contains(vendorDismiss, true), "vendor exit releases original capture");
    screen.vendorPanel.dispose(); screen.vendorPanel.dispose(); screen.vendorPanel.setVisible(true);
    require(!screen.vendorPanel.isVisible(), "retired vendor cannot reopen");
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : vendorClicks) click.clicked(null, 0, 0);
    require(!vendorDismiss.touchDown(null, 0, 0, 0, 0) && !vendorPurchase.onPurchase(vendorItem, true)
        && !vendorPurchase.onSell(vendorItem), "late vendor callbacks ignored");
    for (Actor actor : vendorActors) require(actor.getParent() == null && actor.getListeners().size == 0
        && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "vendor actor retired");
    for (Map.Entry<String, Integer> reference : vendorReferencesBeforeConstruction.entrySet())
      require(references(reference.getKey()) == reference.getValue(), "vendor asset baseline " + reference.getKey());
    for (int handle : vendorHandles) require(!Gdx.gl.glIsTexture(handle), "vendor texture deleted");
    require(field(screen.cubePanel, "disposed", Boolean.class)
        && field(screen.stashPanel, "disposed", Boolean.class), "Client retired storage owners");
    screen.cubePanel.dispose(); screen.stashPanel.dispose();
    require(field(screen.inventoryPanel, "disposed", Boolean.class), "Client retired inventory owner");
    require(!containsIdentity(alternateListeners, inventoryAlternate), "inventory alternate listener independently removed");
    screen.inventoryPanel.dispose(); screen.inventoryPanel.dispose(); screen.inventoryPanel.setVisible(true);
    require(!screen.inventoryPanel.isVisible(), "retired inventory cannot reopen");
    inventoryAlternate.onAlternated(character.getItems(), 1, null, null);
    for (com.badlogic.gdx.scenes.scene2d.utils.ClickListener click : inventoryClicks) click.clicked(null, 0, 0);
    screen.inventoryPanel.onDrop(0, 0); screen.inventoryPanel.onSwap(0, 0, 0);
    require(!screen.inventoryPanel.onPickup(0) && !screen.inventoryPanel.onUse(null), "retired inventory routes inactive");
    for (Actor actor : inventoryActors) {
      require(actor.getParent() == null && actor.getListeners().size == 0
          && actor.getCaptureListeners().size == 0 && actor.getActions().size == 0, "inventory actor retired");
    }
    for (Map.Entry<String, Integer> reference : inventoryReferencesBeforeConstruction.entrySet())
      require(references(reference.getKey()) == reference.getValue(), "inventory asset baseline " + reference.getKey());
    for (int handle : inventoryHandles) require(!Gdx.gl.glIsTexture(handle), "inventory texture deleted");
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
        + "\nnetworkPanelActorsRetired=" + networkPanelActors.size
        + "\nnetworkPanelLateClicksIgnored=" + networkPanelClicks.size
        + "\nnetworkPanelBorrowedSystemsCleared=true"
        + "\nnetworkPanelLiveStageDraws=4"
        + "\nofflinePartyMessagesRetiredDuringReopen=" + retiredPartyMessages.size
        + "\nquickSkillRebuilds=3"
        + "\nquickSkillButtonsRetiredDuringRebuild=" + retiredQuickButtons
        + "\nquickSkillLateCallbacksIgnored=" + retiredQuickCallbacks
        + "\nquickSkillDetailsAndChargeBorrowRetired=true"
        + "\ndynamicItemViewsRetiredAndRecreated=" + oldItemViews.size
        + "\ndynamicItemLateClicksIgnored=" + oldItemClicks.size
        + "\ndynamicItemBorrowedWrappersUnchanged=true"
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
        + "\ninventoryActorsRetired=" + inventoryActors.size
        + "\ninventoryAlternateListenerRemoved=true"
        + "\ninventoryLateClicksIgnored=" + inventoryClicks.size
        + "\ninventoryAssetBaselinesBalanced=" + inventoryReferencesBeforeConstruction.size()
        + "\ninventoryTexturesDeleted=" + inventoryHandles.size()
        + "\nvendorActorsRetired=" + vendorActors.size
        + "\nvendorLateClicksIgnored=" + vendorClicks.size
        + "\nvendorPromptHideAndReopen=true\nvendorPromptCaptureReleased=true"
        + "\nvendorAssetBaselinesBalanced=" + vendorReferencesBeforeConstruction.size()
        + "\nvendorTexturesDeleted=" + vendorHandles.size()
        + "\nmercenaryActorsRetired=" + mercenaryActors.size
        + "\nmercenaryLateClicksIgnored=" + mercenaryClicks.size
        + "\nhireRowsRetiredDuringRebuild=" + oldHireRows.size
        + "\nhireModalHideRestoresBothFocus=true\nhireModalExitReleasesFocus=true\nhireLateInputIgnored=true"
        + "\nmercenaryAssetBaselinesBalanced=" + mercenaryReferencesBeforeConstruction.size()
        + "\nmercenaryTexturesDeleted=" + mercenaryHandles.size()
        + "\nhudActorsRetired=" + hudActors.size
        + "\nhudLateClicksAndHoverIgnored=" + hudClicks.size
        + "\nhudFeedbackAndSubscriptionsCleared=true"
        + "\nhudAssetBaselinesBalanced=" + hudReferencesBeforeConstruction.size()
        + "\nhudTexturesDeleted=" + hudHandles.size()
        + "\ncontrolActorsRetired=" + controlActors.size
        + "\ncontrolLateClicksIgnored=" + controlClicks.size
        + "\ncontrolBeltAssignmentListenersRemoved=" + controlAssignments.length
        + "\ncontrolOwnerWorldCleared=true\ncontrolHudSkillsInactive=true"
        + "\ncontrolAssetBaselinesBalanced=" + controlReferencesBeforeConstruction.size()
        + "\ncontrolTexturesDeleted=" + controlHandles.size()
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

  private static boolean containsIdentity(Array<?> values, Object expected) {
    for (Object value : values) if (value == expected) return true;
    return false;
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
