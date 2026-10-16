package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
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
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.*;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.excel.Inventory;
import com.riiablo.codec.excel.ItemEntry;
import com.riiablo.item.Item;
import com.riiablo.item.ItemWrapper;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemController;
import com.riiablo.save.ItemData;
import com.riiablo.widget.GoldAmountDialog;
import com.riiablo.widget.TextField;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

/** Production storage panels/dialogs and real reference counts; private GL uploads are intercepted. */
class StoragePanelLifecycleTest {
  private static final String SOUND = "data/global/sfx/cursor/button.wav";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<Disposable> resources = new Array<>();
  private Application previousApp;
  private com.badlogic.gdx.Files previousFiles;
  private Input previousInput;
  private AssetManager assets;
  private ItemData items;
  private ItemController controller;
  private StatRef gold;
  private FontTBL.BitmapFont font;
  private MockedConstruction<Texture> uploads;

  @BeforeEach void setup() throws Exception {
    GdxNativesLoader.load();
    previousApp = Gdx.app; previousFiles = Gdx.files; previousInput = Gdx.input;
    Gdx.app = mock(Application.class); Gdx.files = mock(com.badlogic.gdx.Files.class); Gdx.input = mock(Input.class);
    when(Gdx.app.getClipboard()).thenReturn(mock(Clipboard.class));
    for (Field field : Riiablo.class.getFields())
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    Riiablo.colors = new Colors(); Riiablo.game = null;
    Riiablo.charData = mock(CharData.class); items = mock(ItemData.class); controller = mock(ItemController.class);
    when(Riiablo.charData.getItems()).thenReturn(items);
    when(items.getStore(any(StoreLoc.class))).thenReturn(new IntArray());
    when(items.toItemArray(any())).thenReturn(new Array<>());
    Attributes stats = mock(Attributes.class); when(Riiablo.charData.getStats()).thenReturn(stats);
    gold = mock(StatRef.class); when(gold.asInt()).thenReturn(100);
    when(stats.get(Stat.goldbank)).thenReturn(gold);
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(32); when(texture.getHeight()).thenReturn(32);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class); set(Textures.class, Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16; data.capHeight = 12;
    for (int c = 32; c < 127; c++) {
      BitmapFont.Glyph glyph = new BitmapFont.Glyph(); glyph.id = c; glyph.xadvance = 8; glyph.width = 8; glyph.height = 12;
      data.setGlyph(c, glyph);
    }
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font; Riiablo.fonts.fontformal11 = font;
    Riiablo.bundle = mock(I18NBundle.class); when(Riiablo.bundle.get(anyString())).thenReturn("text");
    Riiablo.files = mock(com.riiablo.Files.class); Inventory table = mock(Inventory.class);
    Inventory.Entry layout = new Inventory.Entry(); layout.gridX = layout.gridY = 3;
    layout.gridBoxWidth = layout.gridBoxHeight = 28;
    when(table.get(anyString())).thenReturn(layout); set(com.riiablo.Files.class, Riiablo.files, "inventory", table);
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class); when(dc.getTexture()).thenReturn(region);
        when(dc.getTexture(anyInt())).thenReturn(region); when(dc.getNumFramesPerDir()).thenReturn(27); return dc;
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
    finally {
      uploads.close();
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
      Gdx.app = previousApp; Gdx.files = previousFiles; Gdx.input = previousInput;
    }
  }

  @Test void twoPanelsReturnOnlyTheirReferencesOnOriginalHostAndClearWholeTrees() {
    for (String path : paths()) assets.load(path, DC6.class);
    assets.load(SOUND, Sound.class); assets.finishLoading();
    CubePanel cube = cube(); StashPanel stash = stash(); assets.finishLoading();
    assertEquals(9, assets.getReferenceCount(SOUND));
    Array<Actor> actors = new Array<>(); collect(cube, actors); collect(stash, actors);
    for (Actor actor : actors) { actor.addCaptureListener(new InputListener()); actor.addAction(new Action() { public boolean act(float dt) { return false; } }); }
    Group root = new Group(); root.addActor(cube); root.addActor(stash);
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement; Riiablo.charData = mock(CharData.class);
    cube.dispose(); stash.dispose(); cube.dispose(); stash.dispose(); assets.finishLoading();
    assertEquals(paths().length + 1, assets.getLoadedAssets());
    for (String path : paths()) assertEquals(1, assets.getReferenceCount(path), path);
    assertEquals(1, assets.getReferenceCount(SOUND)); verifyNoInteractions(replacement);
    assertRetired(actors); assertTrue(root.getChildren().isEmpty());
    for (Texture upload : uploads.constructed()) verify(upload).dispose();
    assertEquals(2, uploads.constructed().size()); verify(font, never()).dispose();
  }

  @Test void cubeIndependentExitReleasesBothButtonSoundsWithoutParentRecursion() {
    CubePanel cube = cube(); assets.finishLoading(); assertEquals(2, assets.getReferenceCount(SOUND));
    cube.dispose(); assertEquals(0, assets.getLoadedAssets());
  }

  @Test void stashIndependentExitReleasesBothButtonsAndItsFourDialogButtons() {
    StashPanel stash = stash(); assets.finishLoading(); assertEquals(6, assets.getReferenceCount(SOUND));
    stash.dispose(); assertEquals(0, assets.getLoadedAssets());
  }

  @Test void currentGridCallbacksKeepTheirOriginalStoreRoutes() {
    CubePanel cube = cube(); StashPanel stash = stash();
    cube.onDrop(1, 2); assertTrue(cube.onPickup(3)); cube.onSwap(4, 5, 6);
    stash.onDrop(7, 8); assertTrue(stash.onPickup(9)); stash.onSwap(10, 11, 12);
    verify(controller).cursorToStore(StoreLoc.CUBE, 1, 2); verify(controller).storeToCursor(3);
    verify(controller).swapStoreItem(4, StoreLoc.CUBE, 5, 6);
    verify(controller).cursorToStore(StoreLoc.STASH, 7, 8); verify(controller).storeToCursor(9);
    verify(controller).swapStoreItem(10, StoreLoc.STASH, 11, 12);
  }

  @Test void oldGridAndDialogCallbacksDoNotTouchReplacementSessionOrController() throws Exception {
    Item item = new Item(); item.base = new ItemEntry(); item.base.invwidth = item.base.invheight = 1;
    item.wrapper = mock(ItemWrapper.class); when(items.toItemArray(any())).thenReturn(Array.with(item));
    CubePanel cube = cube(); StashPanel stash = stash();
    GoldAmountDialog.Listener submit = (GoldAmountDialog.Listener) get(stash.goldDialog, "listener");
    GoldAmountDialog.MaximumProvider maximum = (GoldAmountDialog.MaximumProvider) get(stash.goldDialog, "maximumProvider");
    Array<Actor> actors = new Array<>(); collect(cube, actors); collect(stash, actors);
    Riiablo.charData = mock(CharData.class); clearInvocations(items, controller, item.wrapper);
    for (Actor actor : actors) {
      for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener)
        ((ClickListener) listener).clicked(null, 0, 0);
    }
    submit.submitted("10"); assertEquals(0, maximum.maximum(false));
    cube.onDrop(0, 0); cube.onSwap(0, 0, 0); assertFalse(cube.onPickup(0));
    stash.onDrop(0, 0); stash.onSwap(0, 0, 0); assertFalse(stash.onPickup(0));
    Batch batch = mock(Batch.class); cube.draw(batch, 1); stash.draw(batch, 1); cube.act(1); stash.act(1);
    verifyNoInteractions(Riiablo.charData, items, controller, item.wrapper, batch);
  }

  @Test void disposedPanelsAndDialogsCannotReopenOrDispatchSavedCallbacks() throws Exception {
    CubePanel cube = cube(); StashPanel stash = stash(); GoldAmountDialog dialog = stash.goldDialog;
    GoldAmountDialog.Listener submit = (GoldAmountDialog.Listener) get(dialog, "listener");
    InputListener input = (InputListener) dialog.getCaptureListeners().first();
    cube.dispose(); stash.dispose(); submit.submitted("10");
    cube.setVisible(true); stash.setVisible(true); dialog.open(); dialog.setVisible(true);
    assertFalse(cube.isVisible()); assertFalse(stash.isVisible()); assertFalse(dialog.isOpen());
    assertFalse(input.keyDown(null, Input.Keys.ENTER)); assertNull(get(dialog, "listener"));
    assertNull(get(dialog, "maximumProvider")); verifyNoInteractions(controller);
  }

  @Test void savedButtonTouchCallbacksCannotPlaySoundOrVibrateReplacementHost() {
    CubePanel cube = cube(); StashPanel stash = stash();
    Array<Actor> actors = new Array<>(); collect(cube, actors); collect(stash, actors);
    Riiablo.input = mock(Client.InputProcessor.class); Riiablo.charData = mock(CharData.class);
    InputEvent event = new InputEvent(); event.setButton(Input.Buttons.LEFT);
    for (Actor actor : actors) if (actor instanceof com.riiablo.widget.Button) {
      assertTrue(((com.riiablo.widget.Button) actor).isDisabled());
      for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener)
        ((ClickListener) listener).touchDown(event, 0, 0, 0, Input.Buttons.LEFT);
    }
    verifyNoInteractions(Riiablo.input, Riiablo.charData);
    cube.dispose(); stash.dispose();
    for (Actor actor : actors) if (actor instanceof com.riiablo.widget.Button)
      assertTrue(((com.riiablo.widget.Button) actor).isDisabled());
  }

  @Test void withdrawalClampsUsingOriginalCharacterAndSubmitsOnce() throws Exception {
    StashPanel stash = stash(); GoldAmountDialog dialog = stash.goldDialog;
    TextField amount = amount(dialog); dialog.open(); amount.setText("250");
    action(dialog, "okButton").clicked(null, 0, 0);
    verify(controller).withdrawGold(100); assertFalse(dialog.isOpen());
    action(dialog, "okButton").clicked(null, 0, 0); verifyNoMoreInteractions(controller);
  }

  @Test void hidingStashClosesReusableDialogAndReleasesOnlyItsFocus() throws Exception {
    StashPanel stash = stash(); GoldAmountDialog dialog = stash.goldDialog; Stage stage = stage(dialog);
    stash.setVisible(true); dialog.open(); assertSame(amount(dialog), stage.getKeyboardFocus());
    stash.setVisible(false); assertFalse(dialog.isOpen()); assertNull(stage.getKeyboardFocus());
    stash.setVisible(true); dialog.open(); assertTrue(dialog.isOpen());
    Actor unrelated = new Actor(); stage.setKeyboardFocus(unrelated);
    stash.setVisible(false); assertSame(unrelated, stage.getKeyboardFocus());
  }

  @Test void dialogReparentAndReopenReleaseOriginalStageFocus() throws Exception {
    GoldAmountDialog dialog = own(new GoldAmountDialog("gold", null)); Stage original = stage(dialog);
    dialog.open(); Stage replacement = stage(dialog); dialog.open();
    assertNull(original.getKeyboardFocus()); assertSame(amount(dialog), replacement.getKeyboardFocus());
    dialog.dispose(); assertNull(replacement.getKeyboardFocus());
  }

  @Test void dialogFocusRejectionAndAcquisitionFailureHideTheModal() throws Exception {
    GoldAmountDialog dialog = own(new GoldAmountDialog("gold", null)); Stage stage = mock(Stage.class);
    set(Actor.class, dialog, "stage", stage); dialog.open(); assertFalse(dialog.isOpen());
    Error failure = new AssertionError("focus"); doThrow(failure).when(stage).setKeyboardFocus(amount(dialog));
    assertSame(failure, assertThrows(AssertionError.class, dialog::open)); assertFalse(dialog.isOpen());
  }

  @Test void dialogExitAfterDetachReleasesOriginalFocusAndDisablesLateDefaultInput() throws Exception {
    GoldAmountDialog dialog = own(new GoldAmountDialog("gold", null)); Stage original = stage(dialog);
    TextField amount = amount(dialog); InputListener input = amount.getDefaultInputListener(); dialog.open();
    set(Actor.class, dialog, "stage", null); dialog.dispose();
    assertNull(original.getKeyboardFocus()); assertTrue(amount.isDisabled());
    assertFalse(input.keyDown(null, Input.Keys.A)); assertFalse(input.keyTyped(null, '1'));
  }

  @Test void focusReleaseErrorStillRetiresDialogButtonsAssetsAndTreeOnce() throws Exception {
    GoldAmountDialog dialog = own(new GoldAmountDialog("gold", null)); Stage stage = stage(dialog); dialog.open();
    Array<Actor> actors = new Array<>(); collect(dialog, actors);
    Error failure = new AssertionError("release focus"); doThrow(failure).when(stage).setKeyboardFocus(null);
    assertSame(failure, assertThrows(AssertionError.class, dialog::dispose));
    assertEquals(0, assets.getLoadedAssets()); assertRetired(actors); dialog.dispose();
    verify(stage).setKeyboardFocus(null);
  }

  @Test void earlyCubeLoadFailureRollsBackReturnedExitButtonAndAssets() {
    Error failure = new AssertionError("convert load");
    doThrow(failure).when(assets).load(argThat((AssetDescriptor<?> asset) -> asset.fileName.endsWith("/miniconvert.DC6")));
    assertSame(failure, assertThrows(AssertionError.class, CubePanel::new)); assertEquals(0, assets.getLoadedAssets());
  }

  @Test void cubePopulateFailureRollsBackPrivateFillAndBothButtons() {
    Error failure = new AssertionError("populate"); when(items.toItemArray(any())).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, CubePanel::new));
    assertEquals(0, assets.getLoadedAssets()); verify(uploads.constructed().get(0)).dispose();
  }

  @Test void lateStashFailureRollsBackPrivateFillAndReturnedButtons() {
    Error failure = new AssertionError("gold prompt"); when(Riiablo.bundle.get("stash_gold_prompt")).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, StashPanel::new));
    assertEquals(0, assets.getLoadedAssets()); verify(uploads.constructed().get(0)).dispose();
  }

  @Test void earlyDialogLoadFailureRollsBackOnlySuccessfullySubmittedRequests() {
    Error failure = new AssertionError("arrows");
    doThrow(failure).when(assets).load(argThat((AssetDescriptor<?> asset) -> asset.fileName.endsWith("/numberarrows.dc6")));
    assertSame(failure, assertThrows(AssertionError.class, () -> new GoldAmountDialog("gold", null)));
    assertEquals(0, assets.getLoadedAssets());
  }

  @Test void lateDialogFailureAlsoRollsBackStashGridAndAllSixButtons() {
    Error failure = new AssertionError("dialog title");
    BitmapFontCache cache = new BitmapFontCache(font);
    doReturn(cache).doThrow(failure).when(font).newFontCache();
    assertSame(failure, assertThrows(AssertionError.class, StashPanel::new));
    assertEquals(0, assets.getLoadedAssets()); verify(uploads.constructed().get(0)).dispose();
  }

  @Test void buttonSoundReleaseErrorStillReleasesBothPanelsAllAssetsAndPrivateFills() {
    CubePanel cube = cube(); StashPanel stash = stash(); assets.finishLoading();
    Error failure = new AssertionError("sound unload");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).doCallRealMethod().when(assets).unload(SOUND);
    assertSame(failure, assertThrows(AssertionError.class, cube::dispose)); stash.dispose();
    assertEquals(0, assets.getLoadedAssets()); cube.dispose(); stash.dispose();
    for (Texture texture : uploads.constructed()) verify(texture).dispose();
  }

  @Test void arrowsCancelAndNegativeInputKeepExistingRulesAndReusableClose() throws Exception {
    GoldAmountDialog.Listener listener = mock(GoldAmountDialog.Listener.class);
    GoldAmountDialog dialog = own(new GoldAmountDialog("gold", listener, true));
    dialog.setMaximumProvider(negative -> 10); dialog.open(); TextField amount = amount(dialog);
    amount.setText("-50"); action(dialog, "okButton").clicked(null, 0, 0); verify(listener).submitted("-10");
    dialog.open(); action(dialog, "decreaseButton").clicked(null, 0, 0); assertEquals("0", amount.getText());
    action(dialog, "increaseButton").clicked(null, 0, 0); assertEquals("1", amount.getText());
    action(dialog, "cancelButton").clicked(null, 0, 0); verify(listener).canceled(); assertFalse(dialog.isOpen());
    dialog.open(); assertTrue(dialog.isOpen()); assertEquals("", amount.getText());
  }

  private CubePanel cube() { CubePanel panel = own(new CubePanel()); panel.itemController = controller; return panel; }
  private StashPanel stash() { StashPanel panel = own(new StashPanel()); panel.itemController = controller; return panel; }
  private <T extends Disposable> T own(T resource) { resources.add(resource); return resource; }
  private TextField amount(GoldAmountDialog dialog) throws Exception { return (TextField) get(dialog, "amount"); }
  private Stage stage(GoldAmountDialog dialog) throws Exception {
    Stage stage = mock(Stage.class); final Actor[] focus = {null};
    when(stage.getKeyboardFocus()).thenAnswer(call -> focus[0]);
    when(stage.setKeyboardFocus(any())).thenAnswer(call -> { focus[0] = call.getArgument(0); return true; });
    set(Actor.class, dialog, "stage", stage); return stage;
  }
  private ClickListener action(GoldAmountDialog dialog, String field) throws Exception {
    Actor button = (Actor) get(dialog, field); ClickListener action = null;
    for (EventListener listener : button.getListeners()) if (listener instanceof ClickListener) {
      try { listener.getClass().getDeclaredMethod("clicked", InputEvent.class, float.class, float.class); }
      catch (NoSuchMethodException inherited) { continue; }
      action = (ClickListener) listener;
    }
    assertNotNull(action); return action;
  }
  private String[] paths() { return new String[] {"data/global/ui/PANEL/supertransmogrifier.dc6",
      "data/global/ui/PANEL/miniconvert.DC6", "data/global/ui/PANEL/buysellbtn.DC6",
      "data/global/ui/PANEL/TradeStash.DC6", "data/global/ui/PANEL/goldcoinbtn.dc6",
      "data/global/ui/MENU/dialogbackground.dc6", "data/global/ui/PANEL/buysellbtn.dc6",
      "data/global/ui/BIGMENU/numberarrows.dc6"}; }
  private static void collect(Actor actor, Array<Actor> result) {
    result.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, result);
  }
  private static void assertRetired(Array<Actor> actors) {
    for (Actor actor : actors) {
      assertNull(actor.getParent()); assertTrue(actor.getListeners().isEmpty()); assertTrue(actor.getCaptureListeners().isEmpty());
      assertTrue(actor.getActions().isEmpty());
    }
  }
  private static Object get(Object object, String name) throws Exception {
    Class<?> type = object.getClass();
    while (type != null) {
      try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
      catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
    }
    throw new NoSuchFieldException(name);
  }
  private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
}
