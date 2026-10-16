package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.*;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.engine.server.quest.NativeQuestRecord;
import com.riiablo.item.Item;
import com.riiablo.key.MappedKey;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemController;
import com.riiablo.screen.GameScreen;
import com.riiablo.widget.HotkeyButton;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

/** Production panel/nested owners and real manager counts; only private GL uploads are intercepted. */
class ControlPanelLifecycleTest extends RiiabloTest {
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<ControlPanel> panels = new Array<>();
  private Application application;
  private AssetManager assets;
  private ItemController controller;
  private FontTBL.BitmapFont font;
  private Pixmap source;
  private MockedConstruction<Texture> uploads;
  private String failLoad;

  @BeforeEach void fixture() throws Exception {
    for (Field field : Riiablo.class.getFields())
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    application = Gdx.app; Gdx.app = spy(application); when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Desktop);
    Riiablo.colors = new Colors(); Riiablo.game = null; Riiablo.engine = null;
    Riiablo.charData = CharData.obtain().clear().set(Riiablo.NORMAL, false, "ControlOwner", Riiablo.AMAZON);
    Riiablo.charData.setAction(Input.Buttons.LEFT, -1); Riiablo.charData.setAction(Input.Buttons.RIGHT, -1);
    Riiablo.cursor = mock(Cursor.class); Riiablo.audio = mock(com.riiablo.audio.Audio.class);
    Riiablo.input = mock(Client.InputProcessor.class); controller = mock(ItemController.class);
    Texture texture = mock(Texture.class); when(texture.getWidth()).thenReturn(46); when(texture.getHeight()).thenReturn(41);
    TextureRegion region = new TextureRegion(texture); Riiablo.textures = mock(Textures.class); set(Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16; data.capHeight = 12;
    for (int c = 32; c < 127; c++) {
      BitmapFont.Glyph glyph = new BitmapFont.Glyph(); glyph.id = c; glyph.width = 8; glyph.height = 12; glyph.xadvance = 8;
      data.setGlyph(c, glyph);
    }
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font)); Riiablo.fonts.font16 = font;
    Riiablo.bundle = mock(I18NBundle.class, call -> "fixture");
    source = new Pixmap(100, 80, Pixmap.Format.RGBA8888);
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class); when(dc.getTexture()).thenReturn(region); when(dc.getTexture(anyInt())).thenReturn(region);
        when(dc.getNumFramesPerDir()).thenReturn(4); when(dc.getNumPages()).thenReturn(256);
        when(dc.getPixmap(anyInt(), anyInt())).thenReturn(source); return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    assets.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return mock(Sound.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
    doAnswer(call -> { AssetDescriptor<?> d = call.getArgument(0); if (d.fileName.equals(failLoad)) throw new AssertionError("load " + failLoad); return call.callRealMethod(); })
        .when(assets).load(any(AssetDescriptor.class));
    uploads = mockConstruction(Texture.class, (textureUpload, context) -> {
      when(textureUpload.getWidth()).thenReturn(100); when(textureUpload.getHeight()).thenReturn(80);
    });
  }

  @AfterEach void restore() throws Exception {
    try { for (ControlPanel panel : panels) panel.dispose(); assets.dispose(); source.dispose(); }
    finally { uploads.close(); Gdx.app = application; for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue()); }
  }

  @Test void independentExitReturnsEveryReferenceTextureTreeAndBeltListenerOnce() throws Exception {
    ControlPanel panel = panel(); assets.finishLoading(); Array<Actor> tree = actors(panel);
    Map<String, Integer> owned = counts(); for (String path : owned.keySet()) assets.load(path, assets.getAssetType(path)); assets.finishLoading();
    BeltGrid belt = belt(panel); MappedKey.AssignmentListener[] listeners = listeners(belt);
    for (int i = 0; i < 4; i++) assertTrue(Keys.Belt[i].containsAssignmentListener(listeners[i]));
    Group parent = new Group(); parent.addActor(panel);
    for (Actor actor : tree) { actor.addCaptureListener(new InputListener()); actor.addAction(new Action() { public boolean act(float dt) { return false; } }); }
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    panel.dispose(); panel.dispose(); retired(tree); assertTrue(parent.getChildren().isEmpty()); assertFalse(panel.isVisible());
    for (String path : owned.keySet()) assertEquals(1, assets.getReferenceCount(path), path);
    assertEquals(3, uploads.constructed().size()); for (Texture texture : uploads.constructed()) verify(texture).dispose();
    for (int i = 0; i < 4; i++) assertFalse(Keys.Belt[i].containsAssignmentListener(listeners[i]));
    assertNull(get(panel, "owner")); assertNull(get(panel, "world")); assertNull(panel.itemController);
    assertNull(panel.getLeftSkill()); assertNull(panel.getEscapeButton()); verify(font, never()).dispose(); verifyNoInteractions(replacement);
  }

  @Test void queuedExitPreservesAnExternalSharedSoundReference() {
    String sound = "data/global/sfx/cursor/button.wav"; assets.load(sound, Sound.class); assets.finishLoading();
    ControlPanel panel = panel(); panel.dispose(); assertEquals(1, assets.getLoadedAssets()); assertEquals(1, assets.getReferenceCount(sound));
  }

  @Test void earlyPanelFailureDoesNotUnloadAnotherOwnersAsset() {
    assets.load("data/global/ui/PANEL/hlthmana.DC6", DC6.class); assets.finishLoading();
    failLoad = "data/global/ui/PANEL/overlap.DC6"; assertThrows(AssertionError.class, () -> new ControlPanel());
    assertEquals(1, assets.getLoadedAssets()); assertEquals(1, assets.getReferenceCount("data/global/ui/PANEL/hlthmana.DC6"));
  }

  @Test void failedSkillButtonConstructionReturnsItsUnattachedSoundReference() {
    Error failure = new AssertionError("hotkey label");
    doAnswer(call -> { if (uploads.constructed().size() == 1) throw failure; return new BitmapFontCache(font); }).when(font).newFontCache();
    assertSame(failure, assertThrows(AssertionError.class, () -> new ControlPanel()));
    assertEquals(0, assets.getLoadedAssets()); verify(uploads.constructed().get(0)).dispose();
  }

  @Test void failedControlWidgetConstructionReleasesComposedBackgroundAndEarlierOwners() {
    failLoad = "data/global/ui/PANEL/runbutton.dc6"; assertThrows(AssertionError.class, () -> new ControlPanel());
    assertEquals(0, assets.getLoadedAssets()); assertEquals(2, uploads.constructed().size());
    for (Texture texture : uploads.constructed()) verify(texture).dispose();
  }

  @Test void failedMiniPanelConstructionReleasesNestedRequestsAndRunButton() {
    failLoad = "data/global/ui/PANEL/minipanelbtn.DC6"; assertThrows(AssertionError.class, () -> new ControlPanel());
    assertEquals(0, assets.getLoadedAssets()); for (Texture texture : uploads.constructed()) verify(texture).dispose();
  }

  @Test void failedLateMiniButtonRetiresAlreadyCreatedUnattachedButtons() {
    java.util.concurrent.atomic.AtomicInteger buttons = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(call -> { AssetDescriptor<?> d = call.getArgument(0);
      if (d.fileName.equals("data/global/sfx/cursor/button.wav") && buttons.incrementAndGet() == 12) throw new AssertionError("late mini button");
      return call.callRealMethod(); }).when(assets).load(any(AssetDescriptor.class));
    assertThrows(AssertionError.class, () -> new ControlPanel()); assertEquals(0, assets.getLoadedAssets());
    for (Texture texture : uploads.constructed()) verify(texture).dispose();
  }

  @Test void failedBeltLabelsRemoveEarlierKeyAssignmentsAndAllPrivateTextures() {
    java.util.concurrent.atomic.AtomicInteger labels = new java.util.concurrent.atomic.AtomicInteger();
    java.util.List<MappedKey.AssignmentListener> saved = new java.util.ArrayList<>();
    doAnswer(call -> {
      if (uploads.constructed().size() == 3 && labels.incrementAndGet() == 3) {
        for (int i = 0; i < 2; i++) {
          @SuppressWarnings("unchecked") java.util.Set<MappedKey.AssignmentListener> listeners =
              (java.util.Set<MappedKey.AssignmentListener>) get(Keys.Belt[i], "ASSIGNMENT_LISTENERS");
          for (MappedKey.AssignmentListener listener : listeners)
            if (listener.getClass().getName().startsWith(BeltGrid.class.getName())) saved.add(listener);
        }
        throw new AssertionError("belt label");
      }
      return new BitmapFontCache(font);
    }).when(font).newFontCache();
    assertThrows(AssertionError.class, () -> new ControlPanel()); assertEquals(0, assets.getLoadedAssets()); assertEquals(2, saved.size());
    for (MappedKey key : Keys.Belt) for (MappedKey.AssignmentListener listener : saved) assertFalse(key.containsAssignmentListener(listener));
    for (Texture texture : uploads.constructed()) verify(texture).dispose();
  }

  @Test void mobileBranchHasNoDesktopControlOwnerAndStillExitsCleanly() {
    when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Android); ControlPanel panel = panel();
    assertNull(panel.controlWidget); assertNull(panel.getLeftSkill()); assertNull(panel.getEscapeButton());
    panel.setMinipanelVisible(false); panel.dispose(); assertEquals(0, assets.getLoadedAssets());
    assertEquals(1, uploads.constructed().size()); verify(uploads.constructed().get(0)).dispose();
  }

  @Test void cleanupFailureStillRetiresOtherOwnersAndIsNotRetried() throws Exception {
    ControlPanel panel = panel(); assets.finishLoading(); Array<Actor> tree = actors(panel);
    Error failure = new AssertionError("fill dispose"); doThrow(failure).when(uploads.constructed().get(0)).dispose();
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose)); retired(tree); assertEquals(0, assets.getLoadedAssets());
    for (Texture texture : uploads.constructed()) verify(texture).dispose(); panel.dispose(); verify(uploads.constructed().get(0)).dispose();
    assertNull(get(panel, "owner")); assertNull(panel.controlWidget);
  }

  @Test void constructionFailureKeepsOriginalAndSuppressesCleanupError() {
    Error cleanup = new AssertionError("texture release");
    doAnswer(call -> { AssetDescriptor<?> d = call.getArgument(0); if (d.fileName.equals("data/global/ui/PANEL/runbutton.dc6")) {
      doThrow(cleanup).when(uploads.constructed().get(1)).dispose(); throw new AssertionError("original"); }
      return call.callRealMethod(); }).when(assets).load(any(AssetDescriptor.class));
    AssertionError failure = assertThrows(AssertionError.class, () -> new ControlPanel()); assertEquals("original", failure.getMessage());
    assertArrayEquals(new Throwable[] {cleanup}, failure.getSuppressed()); assertEquals(0, assets.getLoadedAssets());
  }

  @Test void currentBeltCallbacksKeepOriginalControllerAndSlotSemantics() throws Exception {
    ControlPanel panel = panel(); ItemGrid.GridListener callbacks = (ItemGrid.GridListener) panel.controlWidget;
    callbacks.onDrop(2, 3); assertTrue(callbacks.onPickup(9)); callbacks.onSwap(7, 1, 0);
    Item item = mock(Item.class); item.gridX = 3; assertTrue(callbacks.onUse(item));
    verify(controller).cursorToBelt(2, 3); verify(controller).beltToCursor(9); verify(controller).swapBeltItem(7); verify(controller).useBeltSlot(3);
  }

  @Test void currentPromptsAndMiniMenuKeepTheirPanelRouting() throws Exception {
    ControlPanel panel = panel(); GameScreen game = game(panel);
    game.characterPanel = mock(CharacterPanel.class); game.spellsPanel = mock(SpellsPanel.class); game.questsPanel = mock(QuestsPanel.class);
    click(panel.btnAddStats); verify(game).setLeftPanel(game.characterPanel); click(panel.btnAddSkills); verify(game).setRightPanel(game.spellsPanel);
    click(panel.btnQuestLog); verify(game).setLeftPanel(game.questsPanel);
    click((Actor) get(panel.controlWidget, "btnRun")); verify(game).toggleRunWalk();
    Object mini = get(panel.controlWidget, "minipanelWidget"); click((Actor) get(mini, "btnCharacter")); verify(game, times(2)).setLeftPanel(game.characterPanel);
    panel.setMinipanelVisible(false); assertFalse(((Actor) mini).isVisible()); click((Actor) get(panel.controlWidget, "btnMenu")); assertTrue(((Actor) mini).isVisible());
  }

  @Test void questAcknowledgementAndLevelSoundOnlyTrackTheCurrentCharacter() {
    ControlPanel panel = panel(); short[] quests = Riiablo.charData.getQuests(0); quests[0] = 1;
    panel.act(0); assertTrue(panel.btnQuestLog.isVisible()); panel.acknowledgeQuestLog();
    assertTrue(NativeQuestRecord.has(quests[0], NativeQuestRecord.UPDATE_QUEST_LOG)); assertFalse(panel.btnQuestLog.isVisible());
    Sound level = panel.levelUpSound; verify(level, never()).play();
    Riiablo.charData.getStats().aggregate().put(Stat.level, 2); panel.act(0); verify(level).play(); panel.act(0); verifyNoMoreInteractions(level);
  }

  @Test void normalAttackZeroAndHotkeyMappingArePreserved() {
    Riiablo.charData.setAction(Input.Buttons.LEFT, 0); Riiablo.charData.setHotkey(Input.Buttons.LEFT, 0, 3);
    ControlPanel panel = panel(); assertEquals(0, panel.getLeftSkill().getSkill()); assertSame(Keys.Skill[3], panel.getLeftSkill().getMapping());
  }

  @Test void currentSkillClicksToggleTheOriginalQuickPanels() throws Exception {
    ControlPanel panel = panel(); GameScreen game = game(panel);
    game.spellsQuickPanelL = mock(SpellsQuickPanel.class); game.spellsQuickPanelR = mock(SpellsQuickPanel.class);
    click(panel.leftSkill); click(panel.rightSkill); verify(game.spellsQuickPanelL).setVisible(true); verify(game.spellsQuickPanelR).setVisible(true);
  }

  @Test void disposedSavedClicksDrawsAssignmentsAndBeltCallbacksAreInert() throws Exception {
    ControlPanel panel = panel(); Array<Actor> tree = actors(panel); Array<ClickListener> saved = clicks(tree);
    BeltGrid belt = belt(panel); MappedKey.AssignmentListener[] assignments = listeners(belt);
    ItemGrid.GridListener callbacks = (ItemGrid.GridListener) panel.controlWidget; HotkeyButton skill = panel.leftSkill;
    panel.dispose(); late(panel, tree, saved, callbacks, skill); retired(tree);
    for (int i = 0; i < 4; i++) { assignments[i].onAssigned(Keys.Belt[i], 0, Input.Keys.A); assignments[i].onUnassigned(Keys.Belt[i], 0, Input.Keys.A); }
    assertFalse(panel.isVisible()); assertNull(panel.getEscapeButton());
  }

  @Test void replacedCharacterCannotReceiveAnyOldCallbacks() throws Exception { stale(() -> Riiablo.charData = mock(CharData.class)); verifyNoInteractions(Riiablo.charData); }
  @Test void replacedGameCannotReceiveAnyOldCallbacks() throws Exception { stale(() -> Riiablo.game = mock(GameScreen.class)); verifyNoInteractions(Riiablo.game); }
  @Test void replacedAssetsCannotReceiveAnyOldCallbacks() throws Exception { stale(() -> Riiablo.assets = mock(AssetManager.class)); verifyNoInteractions(Riiablo.assets); }
  @Test void replacedCursorCannotReceiveAnyOldCallbacks() throws Exception { stale(() -> Riiablo.cursor = mock(Cursor.class)); verifyNoInteractions(Riiablo.cursor); }
  @Test void replacedAudioCannotReceiveAnyOldCallbacks() throws Exception { stale(() -> Riiablo.audio = mock(com.riiablo.audio.Audio.class)); verifyNoInteractions(Riiablo.audio); }
  @Test void replacedInputCannotReceiveAnyOldCallbacks() throws Exception { stale(() -> Riiablo.input = mock(Client.InputProcessor.class)); verifyNoInteractions(Riiablo.input); }

  @Test void explicitOwnerMayConstructBeforePublicationButNotAdoptAnotherGame() throws Exception {
    GameScreen owner = mock(GameScreen.class); ControlPanel panel = new ControlPanel(owner); panels.add(panel); panel.itemController = controller; owner.controlPanel = panel;
    assertNull(panel.getEscapeButton()); Riiablo.game = owner; assertNotNull(panel.getEscapeButton());
    Riiablo.game = mock(GameScreen.class); late(panel, actors(panel), clicks(actors(panel)), (ItemGrid.GridListener) panel.controlWidget, panel.leftSkill);
    verifyNoInteractions(Riiablo.game);
  }

  @Test void worldBindingIsOnceOnlyAndOldWorldCannotQueryNewWorld() throws Exception {
    ControlPanel panel = panel(); World world = new World(new WorldConfiguration()); try {
      panel.bindWorld(world); panel.bindWorld(world); Riiablo.engine = world;
      World replacement = mock(World.class); assertThrows(IllegalStateException.class, () -> panel.bindWorld(replacement));
      Riiablo.engine = replacement; late(panel, actors(panel), clicks(actors(panel)), (ItemGrid.GridListener) panel.controlWidget, panel.leftSkill);
      verifyNoInteractions(replacement); panel.dispose(); panel.bindWorld(replacement);
    } finally { world.dispose(); }
  }

  private void stale(Runnable replace) throws Exception {
    ControlPanel panel = panel(); Array<Actor> tree = actors(panel); Array<ClickListener> saved = clicks(tree);
    BeltGrid belt = belt(panel); MappedKey.AssignmentListener[] assignments = listeners(belt);
    replace.run(); late(panel, tree, saved, (ItemGrid.GridListener) panel.controlWidget, panel.leftSkill);
    for (int i = 0; i < 4; i++) { String before = belt.hotkeys[i].getText().toString(); assignments[i].onUnassigned(Keys.Belt[i], 0, Input.Keys.A); assertEquals(before, belt.hotkeys[i].getText().toString()); }
  }

  private void late(ControlPanel panel, Array<Actor> tree, Array<ClickListener> clicks, ItemGrid.GridListener callbacks, HotkeyButton skill) {
    Batch batch = mock(Batch.class); for (ClickListener click : clicks) {
      click.clicked(null, 0, 0);
      if (click.getClass().getName().startsWith("com.riiablo.widget.Button$")) {
        InputEvent event = new InputEvent(); event.setButton(Input.Buttons.LEFT); click.touchDown(event, 0, 0, 0, Input.Buttons.LEFT);
      }
    }
    for (Actor actor : tree) if (actor.getClass().getName().startsWith(ControlPanel.class.getName()) || actor instanceof BeltGrid) actor.draw(batch, 1);
    panel.act(1); panel.acknowledgeQuestLog(); panel.setMinipanelVisible(true); panel.setVisible(true);
    callbacks.onDrop(0, 0); callbacks.onSwap(0, 0, 0); assertFalse(callbacks.onPickup(0)); assertFalse(callbacks.onUse(null));
    assertTrue(skill.refreshDisabled()); skill.map(Keys.Skill[0]); skill.copy(skill); assertNull(panel.hit(0, 0, true));
    com.riiablo.graphics.PaletteIndexedBatch paletteBatch = mock(com.riiablo.graphics.PaletteIndexedBatch.class);
    skill.draw(paletteBatch, 1);
    for (Actor actor : tree) if (actor instanceof com.riiablo.widget.Button) ((com.riiablo.widget.Button) actor).draw(paletteBatch, 1);
    verifyNoInteractions(paletteBatch);
    verifyNoInteractions(batch, controller, Riiablo.input, Riiablo.audio, Riiablo.cursor);
  }

  private ControlPanel panel() { ControlPanel panel = new ControlPanel(); panels.add(panel); panel.itemController = controller; return panel; }
  private GameScreen game(ControlPanel panel) { GameScreen game = mock(GameScreen.class); game.controlPanel = panel; Riiablo.game = game; return game; }
  private BeltGrid belt(ControlPanel panel) throws Exception { return (BeltGrid) get(panel.controlWidget, "belt"); }
  private MappedKey.AssignmentListener[] listeners(BeltGrid belt) throws Exception { return ((MappedKey.AssignmentListener[]) get(belt, "assignments")).clone(); }
  private Map<String, Integer> counts() { Map<String, Integer> result = new HashMap<>(); for (String path : assets.getAssetNames()) result.put(path, assets.getReferenceCount(path)); return result; }
  private Array<Actor> actors(Actor actor) { Array<Actor> result = new Array<>(); collect(actor, result); return result; }
  private void collect(Actor actor, Array<Actor> result) { result.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, result); }
  private void retired(Array<Actor> actors) { for (Actor actor : actors) { assertNull(actor.getParent()); assertEquals(0, actor.getListeners().size); assertEquals(0, actor.getCaptureListeners().size); assertEquals(0, actor.getActions().size); } }
  private Array<ClickListener> clicks(Array<Actor> actors) { Array<ClickListener> result = new Array<>(); for (Actor actor : actors) for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) result.add((ClickListener) listener); return result; }
  private void click(Actor actor) throws Exception { for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) {
    try { listener.getClass().getDeclaredMethod("clicked", InputEvent.class, float.class, float.class); }
    catch (NoSuchMethodException ignored) { continue; }
    if (!listener.getClass().getName().startsWith(ControlPanel.class.getName())) continue;
    InputEvent event = new InputEvent(); event.setListenerActor(actor); ((ClickListener) listener).clicked(event, 0, 0);
  } }
  private static Object get(Object object, String name) throws Exception { Field field = field(object.getClass(), name); return field.get(object); }
  private static void set(Object object, String name, Object value) throws Exception { field(object.getClass(), name).set(object, value); }
  private static void set(Class<?> type, Object object, String name, Object value) throws Exception { field(type, name).set(object, value); }
  private static Field field(Class<?> type, String name) throws Exception { while (type != null) { try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); } } throw new NoSuchFieldException(name); }
}
