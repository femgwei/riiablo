package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.*;
import com.riiablo.audio.Audio;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.server.npc.HirelingOffer;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.mpq.MPQFileHandleResolver;
import com.riiablo.net.packet.d2gs.*;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemController;
import com.riiablo.screen.GameScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

/** Production controls and reference counts, with only GL uploads intercepted. */
class MercenaryPanelsLifecycleTest extends RiiabloTest {
  private static final String SOUND = "data/global/sfx/cursor/button.wav";
  private static final String ROSTER = "data/global/ui/MENU/textslid.dc6";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<Disposable> resources = new Array<>();
  private AssetManager assets;
  private CharData character;
  private FontTBL.BitmapFont font;
  private MockedConstruction<Texture> uploads;
  private Input previousInput;

  @BeforeEach void fixture() throws Exception {
    previousInput = Gdx.input; Gdx.input = mock(Input.class);
    for (Field field : Riiablo.class.getFields())
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    Riiablo.colors = new Colors(); Riiablo.game = null; Riiablo.engine = null;
    character = CharData.obtain().clear().set(Riiablo.NORMAL, false, "MercOwner", Riiablo.AMAZON);
    Riiablo.charData = character; Riiablo.cursor = mock(Cursor.class); Riiablo.audio = mock(Audio.class);
    Texture texture = mock(Texture.class); when(texture.getWidth()).thenReturn(320); when(texture.getHeight()).thenReturn(240);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class); set(Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16; data.capHeight = 12;
    for (int c = 32; c < 127; c++) {
      BitmapFont.Glyph glyph = new BitmapFont.Glyph(); glyph.id = c; glyph.width = 8; glyph.height = 12; glyph.xadvance = 8;
      data.setGlyph(c, glyph);
    }
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font; Riiablo.fonts.font8 = font; Riiablo.fonts.ReallyTheLastSucker = font;
    Riiablo.bundle = mock(I18NBundle.class, call -> "fixture");
    Riiablo.mpqs = mock(MPQFileHandleResolver.class);
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class); when(dc.getTexture()).thenReturn(region); when(dc.getTexture(anyInt())).thenReturn(region); return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    assets.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return mock(Sound.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
    uploads = mockConstruction(Texture.class);
  }

  @AfterEach void restore() throws Exception {
    try { for (Disposable resource : resources) resource.dispose(); assets.dispose(); }
    finally { uploads.close(); for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue()); Gdx.input = previousInput; }
  }

  @Test void equipmentIndependentExitReturnsFiveSheetsOneSoundAndPrivateFillOnce() throws Exception {
    for (String path : equipmentPaths()) assets.load(path, DC6.class); assets.load(SOUND, Sound.class); assets.finishLoading();
    HirelingPanel panel = equipment(); Array<Actor> actors = actors(panel); Group parent = new Group(); parent.addActor(panel);
    for (Actor actor : actors) { actor.addCaptureListener(new InputListener()); actor.addAction(new Action() { public boolean act(float dt) { return false; } }); }
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement; Riiablo.charData = mock(CharData.class);
    panel.dispose(); panel.dispose(); retired(actors); assertTrue(parent.getChildren().isEmpty());
    assertEquals(6, assets.getLoadedAssets()); for (String path : equipmentPaths()) assertEquals(1, assets.getReferenceCount(path));
    assertEquals(1, assets.getReferenceCount(SOUND)); assertEquals(1, uploads.constructed().size()); verify(uploads.constructed().get(0)).dispose();
    verifyNoInteractions(replacement); verify(font, never()).dispose(); assertNull(get(panel, "world")); assertNull(panel.itemController);
    for (Object part : (Object[]) get(panel, "bodyParts")) if (part != null) { assertNull(get(part, "item")); assertNull(get(part, "background")); }
  }

  @Test void equipmentCurrentSlotsKeepMercenaryControllerRouting() throws Exception {
    HirelingPanel panel = equipment(); panel.itemController = mock(ItemController.class);
    Actor head = part(panel, BodyLoc.HEAD); action(head).clicked(null, 0, 0); verify(panel.itemController).bodyToCursor(BodyLoc.HEAD, true);
    Item helmet = new Item(); helmet.reset(); helmet.setBase(Riiablo.files.armor.get("cap")); when(Riiablo.cursor.getItem()).thenReturn(helmet);
    action(head).clicked(null, 0, 0); verify(panel.itemController).cursorToBody(BodyLoc.HEAD, true); assertSame(helmet, get(head, "item"));
    action(head).clicked(null, 0, 0); verify(panel.itemController).swapBodyItem(BodyLoc.HEAD, true);
  }

  @Test void equipmentRejectsIncompatibleCursorWithoutChangingSlots() throws Exception {
    HirelingPanel panel = equipment(); panel.itemController = mock(ItemController.class);
    Item helmet = new Item(); helmet.reset(); helmet.setBase(Riiablo.files.armor.get("cap")); when(Riiablo.cursor.getItem()).thenReturn(helmet);
    Actor torso = part(panel, BodyLoc.TORS); action(torso).clicked(null, 0, 0);
    verifyNoInteractions(panel.itemController, Riiablo.audio); assertNull(get(torso, "item"));
  }

  @Test void equipmentOldCallbacksCannotUseReplacementCharacterOrInputHost() throws Exception {
    HirelingPanel panel = equipment(); ItemController controller = panel.itemController = mock(ItemController.class);
    Array<ClickListener> clicks = clicks(actors(panel)); Actor head = part(panel, BodyLoc.HEAD);
    Riiablo.charData = mock(CharData.class); Riiablo.game = mock(GameScreen.class); Riiablo.input = mock(Client.InputProcessor.class);
    Batch batch = mock(Batch.class); invoke(clicks); panel.act(1); panel.draw(batch, 1); head.draw(batch, 1); panel.setVisible(true);
    assertFalse(panel.isVisible()); verifyNoInteractions(Riiablo.charData, Riiablo.game, Riiablo.input, Riiablo.cursor, Riiablo.audio, controller, batch);
  }

  @Test void equipmentSameCharacterCannotAdoptReplacementGameAudioOrCursor() {
    HirelingPanel panel = equipment(); Riiablo.game = mock(GameScreen.class); panel.setVisible(true); assertFalse(panel.isVisible());
    Riiablo.game = null; Audio audio = Riiablo.audio; Riiablo.audio = mock(Audio.class); panel.setVisible(true); assertFalse(panel.isVisible());
    Riiablo.audio = audio; Riiablo.cursor = mock(Cursor.class); panel.setVisible(true); assertFalse(panel.isVisible());
  }

  @Test void equipmentNeverMixesOldSubscriptionWithReplacementWorld() throws Exception {
    World original = mock(World.class); Riiablo.engine = original; HirelingPanel panel = equipment();
    World replacement = mock(World.class); Riiablo.engine = replacement; Riiablo.game = mock(GameScreen.class); Riiablo.game.hirelingPanel = panel;
    panel.act(1); panel.setVisible(true); assertFalse(panel.isVisible()); verifyNoInteractions(original, replacement);
  }

  @Test void explicitOwnersAllowPrepublicationConstructionAndWorldBinding() throws Exception {
    GameScreen owner = mock(GameScreen.class); Riiablo.game = mock(GameScreen.class);
    HirelingPanel panel = own(new HirelingPanel(owner)); HireMercenaryPanel roster = own(new HireMercenaryPanel(owner));
    World world = new World(new WorldConfiguration().register("itemController", mock(ItemController.class)));
    try {
      Riiablo.engine = world; panel.bindWorld(world); roster.bindWorld(world); world.inject(panel); Riiablo.game = owner;
      panel.setVisible(true); roster.presentOffers(Array.with(offer(1))); assertTrue(panel.isVisible()); assertTrue(roster.isVisible());
      assertSame(world, get(panel, "world")); assertSame(world, get(roster, "world"));
      Riiablo.game = mock(GameScreen.class); panel.setVisible(false); roster.close(); panel.setVisible(true); roster.open(1, 0);
      assertFalse(panel.isVisible()); assertFalse(roster.isVisible());
    } finally { world.dispose(); }
  }

  @Test void equipmentConstructorFailureReturnsSubmittedReferencesAndFill() {
    assets.load(equipmentPaths()[0], DC6.class); assets.finishLoading(); Error failure = new AssertionError("armor load");
    doThrow(failure).when(assets).load(argThat((AssetDescriptor<?> a) -> a.fileName.endsWith("/inv_armor.DC6")));
    assertSame(failure, assertThrows(AssertionError.class, HirelingPanel::new)); assertEquals(1, assets.getLoadedAssets());
    assertEquals(1, assets.getReferenceCount(equipmentPaths()[0])); verify(uploads.constructed().get(0)).dispose();
  }

  @Test void equipmentLateConstructionFailureRetiresReturnedChildrenAndSound() {
    Error failure = new AssertionError("last label");
    Riiablo.string = mock(com.riiablo.codec.StringTBLs.class);
    when(Riiablo.string.lookup(anyString())).thenReturn("fixture");
    when(Riiablo.string.lookup("strchrpos")).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, HirelingPanel::new)); assertEquals(0, assets.getLoadedAssets());
    verify(uploads.constructed().get(0)).dispose();
  }

  @Test void equipmentUnloadFailureStillRetiresTreeAndReleasesOtherAssets() throws Exception {
    HirelingPanel panel = equipment(); assets.finishLoading(); Array<Actor> actors = actors(panel); Error failure = new AssertionError("sound unload");
    doThrow(failure).when(assets).unload(SOUND); assertSame(failure, assertThrows(AssertionError.class, panel::dispose));
    retired(actors); assertEquals(1, assets.getLoadedAssets()); assertNull(get(panel, "owner")); assertNull(get(panel, "NpcInv"));
    verify(uploads.constructed().get(0)).dispose(); panel.dispose(); verify(assets).unload(SOUND); doCallRealMethod().when(assets).unload(SOUND);
  }

  @Test void boundWorldCannotBeReplacedAndDisposedPanelsCannotRetainNewWorld() throws Exception {
    HirelingPanel equipment = equipment(); HireMercenaryPanel roster = roster(); World first = mock(World.class), next = mock(World.class);
    equipment.bindWorld(first); roster.bindWorld(first); equipment.bindWorld(first); roster.bindWorld(first);
    assertThrows(IllegalStateException.class, () -> equipment.bindWorld(next)); assertThrows(IllegalStateException.class, () -> roster.bindWorld(next));
    assertSame(first, get(equipment, "world")); assertSame(first, get(roster, "world")); equipment.dispose(); roster.dispose();
    equipment.bindWorld(next); roster.bindWorld(next); assertNull(get(equipment, "world")); assertNull(get(roster, "world")); verifyNoInteractions(first, next);
  }

  @Test void equipmentRejectsNullWorldBinding() {
    HirelingPanel equipment = equipment(); HireMercenaryPanel roster = roster();
    assertThrows(NullPointerException.class, () -> equipment.bindWorld(null)); assertThrows(NullPointerException.class, () -> roster.bindWorld(null));
  }

  @Test void disposedEquipmentSavedButtonsCannotReopenOrAccessNewInput() throws Exception {
    HirelingPanel panel = equipment(); Array<ClickListener> saved = clicks(actors(panel)); panel.dispose();
    Riiablo.input = mock(Client.InputProcessor.class); invoke(saved); panel.setVisible(true); assertFalse(panel.isVisible()); verifyNoInteractions(Riiablo.input);
  }

  @Test void rosterLoadsLazilyAndReturnsOnlyItsOwnReferenceOnce() throws Exception {
    assets.load(ROSTER, DC6.class); assets.finishLoading(); HireMercenaryPanel panel = roster(); assertEquals(1, assets.getReferenceCount(ROSTER));
    panel.presentOffers(Array.with(offer(1))); panel.presentOffers(Array.with(offer(2))); assets.finishLoading(); assertEquals(2, assets.getReferenceCount(ROSTER));
    Array<Actor> actors = actors(panel); panel.dispose(); panel.dispose(); retired(actors); assertEquals(1, assets.getReferenceCount(ROSTER));
    assertNull(get(panel, "selected")); assertNull(get(panel, "pieces")); assertNull(get(panel, "world")); assertTrue(uploads.constructed().isEmpty()); verify(font, never()).dispose();
  }

  @Test void rosterLoadFailureRollsBackAndCanRetryWithoutDuplicatingReferences() {
    HireMercenaryPanel panel = roster(); Error failure = new AssertionError("finish load");
    doThrow(failure).when(assets).finishLoadingAsset(any(AssetDescriptor.class));
    assertSame(failure, assertThrows(AssertionError.class, () -> panel.presentOffers(Array.with(offer(1)))));
    assertFalse(assets.contains(ROSTER)); doCallRealMethod().when(assets).finishLoadingAsset(any(AssetDescriptor.class));
    panel.presentOffers(Array.with(offer(1))); assertEquals(1, assets.getReferenceCount(ROSTER));
  }

  @Test void rosterDescriptionFailureReturnsSharedReferenceAndRetries() {
    assets.load(ROSTER, DC6.class); assets.finishLoading(); HireMercenaryPanel panel = roster(); Error failure = new AssertionError("HireDesc");
    when(Riiablo.mpqs.resolve(anyString())).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, () -> panel.presentOffers(Array.with(offer(1))))); assertEquals(1, assets.getReferenceCount(ROSTER));
    doReturn(null).when(Riiablo.mpqs).resolve(anyString()); panel.presentOffers(Array.with(offer(1))); assets.finishLoading(); assertEquals(2, assets.getReferenceCount(ROSTER));
  }

  @Test void rosterRebuildRetiresBothLinesAndSavedCallbacks() throws Exception {
    HireMercenaryPanel panel = roster(); character.getMerc().seed = 7; HirelingOffer offer = offer(1);
    panel.presentOffers(Array.with(offer)); Array<Actor> old = actors(rows(panel)); old.removeIndex(0); Array<ClickListener> saved = clicks(old);
    panel.presentOffers(Array.with(offer)); invoke(saved); retired(old); assertNull(get(panel, "selected")); assertFalse((Boolean) get(panel, "confirming"));
    action(rows(panel).getChildren().first()).clicked(null, 0, 0); assertTrue((Boolean) get(panel, "confirming")); assertSame(offer, get(panel, "selected"));
    invoke(saved); assertTrue((Boolean) get(panel, "confirming"));
  }

  @Test void rosterSecondLineSelectsSameCandidateAndNoReturnsToList() throws Exception {
    HireMercenaryPanel panel = roster(); character.getMerc().seed = 7; HirelingOffer offer = offer(1); panel.presentOffers(Array.with(offer));
    action(rows(panel).getChildren().get(2)).clicked(null, 0, 0); assertSame(offer, get(panel, "selected")); assertTrue((Boolean) get(panel, "confirming"));
    action((Actor) get(panel, "no")).clicked(null, 0, 0); assertFalse((Boolean) get(panel, "confirming")); assertEquals(3, rows(panel).getChildren().size);
  }

  @Test void rosterHideClearsPendingSelectionAndAllowsReopen() throws Exception {
    HireMercenaryPanel panel = roster(); panel.presentOffers(Array.with(offer(1))); set(panel, "pending", 42L); set(panel, "selected", offer(1));
    Array<ClickListener> old = clicks(actors(rows(panel))); panel.setVisible(false); invoke(old);
    assertEquals(0L, get(panel, "pending")); assertNull(get(panel, "selected")); assertFalse(panel.isVisible());
    panel.presentOffers(Array.with(offer(2))); assertTrue(panel.isVisible()); assertEquals(3, rows(panel).getChildren().size);
  }

  @Test void rosterReparentAndDetachReleaseOriginalStageAndRestoreBothFocusOwners() throws Exception {
    HireMercenaryPanel panel = roster(); Stage first = stage(panel); Actor keyboard = focusedActor(first), scroll = focusedActor(first);
    first.setKeyboardFocus(keyboard); first.setScrollFocus(scroll); panel.open(1, 0); assertSame(panel, first.getKeyboardFocus()); assertSame(panel, first.getScrollFocus());
    Stage second = stage(panel); panel.open(2, 0); assertSame(keyboard, first.getKeyboardFocus()); assertSame(scroll, first.getScrollFocus());
    assertSame(panel, second.getKeyboardFocus()); set(Actor.class, panel, "stage", null); panel.dispose();
    assertNull(second.getKeyboardFocus()); assertNull(second.getScrollFocus()); assertNull(get(panel, "focusStage"));
  }

  @Test void rosterCloseDoesNotStealFocusOrRestoreDetachedActors() throws Exception {
    HireMercenaryPanel panel = roster(); Stage stage = stage(panel); Actor prior = focusedActor(stage); stage.setKeyboardFocus(prior); stage.setScrollFocus(prior);
    panel.open(1, 0); Actor other = focusedActor(stage); stage.setKeyboardFocus(other); set(Actor.class, prior, "stage", null); panel.close();
    assertSame(other, stage.getKeyboardFocus()); assertNull(stage.getScrollFocus());
  }

  @Test void rosterFocusRejectionClosesWithoutOpeningService() throws Exception {
    HireMercenaryPanel panel = roster(); Stage stage = stage(panel); doReturn(false).when(stage).setScrollFocus(panel);
    panel.open(1, 0); assertFalse(panel.isVisible()); assertNull(stage.getKeyboardFocus()); assertNull(get(panel, "focusStage"));
  }

  @Test void rosterFocusAcquisitionThrowRollsBackBothFocusAndVisibility() throws Exception {
    HireMercenaryPanel panel = roster(); Stage stage = stage(panel); Error failure = new AssertionError("scroll focus");
    doAnswer(call -> { setFocus(stage, "scrollFocus", panel); throw failure; }).when(stage).setScrollFocus(panel);
    assertSame(failure, assertThrows(AssertionError.class, () -> panel.open(1, 0))); assertFalse(panel.isVisible());
    assertNull(stage.getKeyboardFocus()); assertNull(stage.getScrollFocus()); assertNull(get(panel, "focusStage"));
  }

  @Test void rosterReleaseFailureStillRetiresControlsAndAssets() throws Exception {
    HireMercenaryPanel panel = roster(); Stage stage = stage(panel); panel.open(1, 0); Array<Actor> actors = actors(panel);
    Error failure = new AssertionError("keyboard release"); doThrow(failure).when(stage).setKeyboardFocus(null);
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose)); assertFalse(panel.isVisible()); retired(actors);
    assertEquals(0, assets.getLoadedAssets()); assertNull(stage.getScrollFocus()); assertNull(get(panel, "focusStage")); panel.dispose();
  }

  @Test void rosterDetachedOpenDoesNotAcquireAssetsOrThrow() { HireMercenaryPanel panel = roster(); panel.open(1, 0); assertFalse(panel.isVisible()); assertEquals(0, assets.getLoadedAssets()); }

  @Test void rosterOldResultsInputsAndRowsCannotReachReplacementSession() throws Exception {
    HireMercenaryPanel panel = roster(); panel.presentOffers(Array.with(offer(1))); Array<ClickListener> saved = clicks(actors(panel)); InputListener input = input(panel);
    set(panel, "pending", 42L); NpcServiceResult result = mock(NpcServiceResult.class); Riiablo.charData = mock(CharData.class); Riiablo.game = mock(GameScreen.class);
    World replacement = mock(World.class); Riiablo.engine = replacement; Batch batch = mock(Batch.class);
    invoke(saved); assertFalse(input.touchDown(null, 0, 0, 0, 0)); input.touchDragged(null, 0, 0, 0); assertFalse(input.scrolled(null, 0, 0, 0, 1)); assertFalse(input.keyDown(null, Input.Keys.ENTER));
    panel.applyNetworkResult(result); panel.open(1, 0); panel.presentOffers(new Array<>()); panel.act(16); panel.draw(batch, 1); panel.cancel();
    assertEquals(42L, get(panel, "pending")); verifyNoInteractions(result, Riiablo.charData, Riiablo.game, replacement, batch);
  }

  @Test void rosterSameCharacterCannotAdoptReplacementGameOrAssets() throws Exception {
    HireMercenaryPanel panel = roster(); Riiablo.game = mock(GameScreen.class); panel.presentOffers(Array.with(offer(1))); assertFalse(panel.isVisible());
    Riiablo.game = null; Riiablo.assets = mock(AssetManager.class); panel.presentOffers(Array.with(offer(1))); assertFalse(panel.isVisible()); verifyNoInteractions(Riiablo.assets);
  }

  @Test void rosterSameGameCannotAdoptReplacementWorldAndHiddenInputsStayInactive() throws Exception {
    HireMercenaryPanel panel = roster(); panel.presentOffers(Array.with(offer(1))); InputListener input = input(panel);
    panel.close(); assertFalse(input.keyDown(null, Input.Keys.ENTER)); assertFalse(input.touchDown(null, 0, 0, 0, 0));
    World other = mock(World.class); Riiablo.engine = other; panel.presentOffers(Array.with(offer(2))); panel.open(1, 0);
    assertFalse(panel.isVisible()); verifyNoInteractions(other);
  }

  @Test void rosterDisposedSavedCallbacksAndResultsCannotReopen() throws Exception {
    HireMercenaryPanel panel = roster(); panel.presentOffers(Array.with(offer(1))); Array<Actor> actors = actors(panel); Array<ClickListener> saved = clicks(actors); InputListener input = input(panel);
    panel.dispose(); invoke(saved); panel.open(1, 0); panel.presentOffers(Array.with(offer(1))); panel.setVisible(true); NpcServiceResult result = mock(NpcServiceResult.class); panel.applyNetworkResult(result);
    assertFalse(input.keyDown(null, Input.Keys.ENTER)); assertFalse(input.scrolled(null, 0, 0, 0, 1)); assertFalse(panel.isVisible()); retired(actors); verifyNoInteractions(result); assertEquals(0, assets.getLoadedAssets());
  }

  @Test void rosterBothLinesShareOneHoverAndClickTarget() throws Exception {
    HireMercenaryPanel panel = roster(); panel.presentOffers(Array.with(offer(1)));
    WidgetGroup rows = rows(panel); Actor hit = rows.getChildren().get(0);
    Actor entry = rows.getChildren().get(1), description = rows.getChildren().get(2);
    assertEquals(Touchable.disabled, entry.getTouchable());
    assertEquals(Touchable.disabled, description.getTouchable());
    assertSame(hit, rows.hit(entry.getX() + 1, entry.getY() + 8, true));
    assertSame(hit, rows.hit(description.getX() + 1, description.getY() + 8, true));
    ClickListener selection = action(hit);
    for (float y : new float[] {8, 23}) {
      selection.enter(null, 1, y, -1, null); hit.act(0);
      assertEquals(Riiablo.colors.blue, entry.getColor());
      assertEquals(entry.getColor(), description.getColor());
      selection.exit(null, 1, y, -1, null); hit.act(0);
      assertEquals(Riiablo.colors.white, entry.getColor());
      assertEquals(entry.getColor(), description.getColor());
    }
  }

  @Test void rosterNetworkOpenAndHireKeepRequestFieldsAndSingleSubmission() throws Exception {
    World world = mock(World.class); Riiablo.engine = world; ClientNetworkSynchronizer sync = mock(ClientNetworkSynchronizer.class);
    when(world.getSystem(ClientNetworkSynchronizer.class)).thenReturn(sync); when(sync.requestNpcService(anyInt(), anyByte(), anyByte(), anyInt(), anyInt(), anyLong())).thenReturn(42L, 43L);
    GameScreen game = mock(GameScreen.class); game.vendorPanel = mock(VendorPanel.class); when(game.vendorPanel.isNetworked()).thenReturn(true); Riiablo.game = game;
    HireMercenaryPanel panel = roster(); game.hireMercenaryPanel = panel; stage(panel); character.getMerc().seed = -1; panel.open(9, 1);
    verify(sync).requestNpcService(9, NpcServiceType.HIRE, NpcServiceOperation.OPEN, 0, -1, 0xFFFFFFFFL);
    NpcServiceResult result = mock(NpcServiceResult.class); when(result.requestId()).thenReturn(42L); when(result.success()).thenReturn(true); panel.applyNetworkResult(result);
    HirelingOffer offer = offer(12); panel.presentOffers(Array.with(offer)); action(rows(panel).getChildren().first()).clicked(null, 0, 0);
    ClickListener yes = action((Actor) get(panel, "yes")); yes.clicked(null, 0, 0); yes.clicked(null, 0, 0);
    verify(sync).requestNpcService(9, NpcServiceType.HIRE, NpcServiceOperation.HIRE, offer.seed, offer.index, 0xFFFFFFFFL); assertEquals(43L, get(panel, "pending"));
    when(result.requestId()).thenReturn(43L); panel.applyNetworkResult(result); assertFalse(panel.isVisible()); assertEquals(0L, get(panel, "pending"));
  }

  @Test void rosterTimeoutNeverResendsUnacknowledgedPayment() throws Exception {
    HireMercenaryPanel panel = roster(); stage(panel); panel.presentOffers(Array.with(offer(1))); set(panel, "pending", 42L); set(panel, "purchasing", true);
    panel.act(16); assertEquals(0L, get(panel, "pending")); assertEquals("CONNECTION_UNAVAILABLE", get(panel, "failure"));
    assertTrue(((Array<?>) get(panel, "offers")).isEmpty()); NpcServiceResult result = mock(NpcServiceResult.class); panel.applyNetworkResult(result); verifyNoInteractions(result);
  }

  @Test void rosterLocalHireKeepsSelectedIndexSeedPreviousSeedAndPlayer() throws Exception {
    World world = mock(World.class); Riiablo.engine = world; NativeMercenaryRewardSystem rewards = mock(NativeMercenaryRewardSystem.class); when(world.getSystem(NativeMercenaryRewardSystem.class)).thenReturn(rewards);
    GameScreen game = mock(GameScreen.class); game.player = 17; Riiablo.game = game; HireMercenaryPanel panel = roster(); game.hireMercenaryPanel = panel;
    HirelingOffer offer = offer(3); when(rewards.hirelingOffers(17, 2)).thenReturn(Array.with(offer)); character.getMerc().seed = 5; stage(panel); panel.open(9, 2);
    action(rows(panel).getChildren().first()).clicked(null, 0, 0); action((Actor) get(panel, "yes")).clicked(null, 0, 0);
    verify(rewards).hireSelectedMercenary(17, 2, offer.index, offer.seed, 5); assertFalse(panel.isVisible());
  }

  private HirelingPanel equipment() { return own(new HirelingPanel()); }
  private HireMercenaryPanel roster() { return own(new HireMercenaryPanel()); }
  private <T extends Disposable> T own(T value) { resources.add(value); return value; }
  private HirelingOffer offer(int index) { HirelingOffer offer = new HirelingOffer(); offer.index = index; offer.seed = index + 100; offer.level = 3; offer.cost = 100; return offer; }
  private Actor part(HirelingPanel panel, BodyLoc loc) throws Exception { return (Actor) ((Object[]) get(panel, "bodyParts"))[loc.ordinal()]; }
  private WidgetGroup rows(HireMercenaryPanel panel) throws Exception { return (WidgetGroup) get(panel, "rows"); }
  private InputListener input(Actor actor) { for (EventListener listener : actor.getListeners()) if (listener instanceof InputListener) return (InputListener) listener; throw new AssertionError("input"); }
  private ClickListener action(Actor actor) {
    ClickListener action = null;
    for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) {
      try { listener.getClass().getDeclaredMethod("clicked", InputEvent.class, float.class, float.class); }
      catch (NoSuchMethodException inherited) { continue; } action = (ClickListener) listener;
    }
    assertNotNull(action); return action;
  }
  private Stage stage(Actor panel) throws Exception {
    Stage stage = mock(Stage.class); Actor[] focus = new Actor[2]; Group root = new Group(); when(stage.getRoot()).thenReturn(root);
    when(stage.getKeyboardFocus()).thenAnswer(call -> focus[0]); when(stage.getScrollFocus()).thenAnswer(call -> focus[1]);
    when(stage.setKeyboardFocus(any())).thenAnswer(call -> { focus[0] = call.getArgument(0); return true; });
    when(stage.setScrollFocus(any())).thenAnswer(call -> { focus[1] = call.getArgument(0); return true; });
    // Store the mock focus independently so a throwing acquisition can still mutate then fail.
    stageFocus.put(stage, focus); set(Actor.class, panel, "stage", stage); return stage;
  }
  private final Map<Stage, Actor[]> stageFocus = new HashMap<>();
  private void setFocus(Stage stage, String field, Actor value) { stageFocus.get(stage)[field.equals("scrollFocus") ? 1 : 0] = value; }
  private Actor focusedActor(Stage stage) throws Exception { Actor actor = new Actor(); set(Actor.class, actor, "stage", stage); return actor; }
  private Array<Actor> actors(Actor actor) { Array<Actor> values = new Array<>(); collect(actor, values); return values; }
  private void collect(Actor actor, Array<Actor> values) { values.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, values); }
  private Array<ClickListener> clicks(Array<Actor> actors) { Array<ClickListener> values = new Array<>(); for (Actor actor : actors) for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) values.add((ClickListener) listener); return values; }
  private void invoke(Array<ClickListener> clicks) { InputEvent event = new InputEvent(); event.setButton(Input.Buttons.LEFT); for (ClickListener click : clicks) { click.clicked(null, 0, 0); click.touchDown(event, 0, 0, 0, Input.Buttons.LEFT); } }
  private void retired(Array<Actor> actors) { for (Actor actor : actors) { assertNull(actor.getParent()); assertTrue(actor.getListeners().isEmpty()); assertTrue(actor.getCaptureListeners().isEmpty()); assertTrue(actor.getActions().isEmpty()); } }
  private String[] equipmentPaths() { return new String[] {"data/global/ui/PANEL/NpcInv.dc6", "data/global/ui/PANEL/buysellbtn.DC6", "data/global/ui/PANEL/inv_armor.DC6", "data/global/ui/PANEL/inv_helm_glove.DC6", "data/global/ui/PANEL/inv_weapons.DC6"}; }
  private Object get(Object value, String name) throws Exception {
    for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
      try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(value); } catch (NoSuchFieldException missing) { }
    } throw new NoSuchFieldException(name);
  }
  private void set(Object value, String name, Object data) throws Exception {
    for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
      try { set(type, value, name, data); return; } catch (NoSuchFieldException missing) { }
    } throw new NoSuchFieldException(name);
  }
  private void set(Class<?> type, Object value, String name, Object data) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(value, data); }
}
