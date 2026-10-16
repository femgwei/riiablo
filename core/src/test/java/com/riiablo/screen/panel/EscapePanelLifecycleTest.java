package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.*;
import com.riiablo.audio.Audio;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.util.BBox;
import com.riiablo.save.CharData;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.*;

/** Real menu actors, key mapper and reference counts, without MPQ, GL or persisted preferences. */
class EscapePanelLifecycleTest {
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<EscapePanel> panels = new Array<>();
  private Application previousApp;
  private com.badlogic.gdx.Files previousFiles;
  private Input previousInput;
  private AssetManager assets;
  private GdxKeyMapper keys;
  private Preferences preferences;
  private Audio audio;

  @BeforeEach void setup() throws Exception {
    previousApp = Gdx.app; previousFiles = Gdx.files; previousInput = Gdx.input;
    Gdx.app = mock(Application.class); Gdx.files = mock(com.badlogic.gdx.Files.class); Gdx.input = mock(Input.class);
    when(Gdx.app.getClipboard()).thenReturn(mock(Clipboard.class));
    preferences = mock(Preferences.class); when(Gdx.app.getPreferences(anyString())).thenReturn(preferences);
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    }
    Riiablo.colors = new Colors(); Riiablo.game = null; Riiablo.language = D2Language.ENGLISH;
    Riiablo.charData = mock(CharData.class); keys = spy(new GdxKeyMapper()); Riiablo.keys = keys;
    audio = mock(Audio.class); Riiablo.audio = audio;
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(32); when(texture.getHeight()).thenReturn(32);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class); set(Textures.class, Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); FontTBL.BitmapFont font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16;
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font; Riiablo.fonts.font42 = font;
    Riiablo.bundle = mock(I18NBundle.class);
    when(Riiablo.bundle.get(anyString())).thenReturn("text");
    when(Riiablo.bundle.format(anyString(), any(Object[].class))).thenReturn("text");
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class);
        when(dc.getTexture()).thenReturn(region); when(dc.getTexture(anyInt())).thenReturn(region);
        when(dc.getTexture(anyInt(), anyInt())).thenReturn(region);
        when(dc.getNumDirections()).thenReturn(1); when(dc.getNumFramesPerDir()).thenReturn(27);
        TextureRegion[] frames = new TextureRegion[27]; java.util.Arrays.fill(frames, region);
        when(dc.getRegions()).thenReturn(new TextureRegion[][] {frames});
        when(dc.getBox(anyInt())).thenReturn(new BBox(0, 0, 32, 32)); return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
  }

  @AfterEach void restore() throws Exception {
    try { for (EscapePanel panel : panels) panel.dispose(); assets.dispose(); }
    finally {
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
      Gdx.app = previousApp; Gdx.files = previousFiles; Gdx.input = previousInput;
    }
  }

  @Test void sameCharacterOptionsCannotModifyReplacementGameOrWorld() {
    Riiablo.engine = null;
    com.riiablo.screen.GameScreen game = mock(com.riiablo.screen.GameScreen.class); Riiablo.game = game;
    EscapePanel panel = new EscapePanel(game); panels.add(panel);
    Array<Actor> actors = PanelSessionAssertions.snapshot(panel);
    Riiablo.game = mock(com.riiablo.screen.GameScreen.class);
    PanelSessionAssertions.assertInactive(panel, actors); verifyNoInteractions(Riiablo.game);
    Riiablo.game = game; Riiablo.engine = mock(com.artemis.World.class);
    PanelSessionAssertions.assertInactive(panel, actors); verifyNoInteractions(Riiablo.engine);
    panel.dispose(); PanelSessionAssertions.assertInactive(panel, actors);
  }

  @Test void exitBalancesFourSharedAssetsOnOriginalHostAndRetiresAllSixPages() throws Exception {
    for (String path : paths()) assets.load(path, DC6.class);
    assets.finishLoading(); EscapePanel panel = panel();
    com.riiablo.codec.Animation left = panel.pentspinL, right = panel.pentspin;
    Array<Actor> actors = new Array<>(); collect(panel, actors);
    assertEquals(6, panel.getChildren().size); assertTrue(actors.size > 100);
    for (Actor actor : actors) { actor.addListener(new InputListener()); actor.addCaptureListener(new InputListener()); }
    Group root = new Group(); root.addActor(panel);
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    Riiablo.charData = mock(CharData.class);
    panel.dispose(); panel.dispose(); assets.finishLoading();
    assertEquals(4, assets.getLoadedAssets());
    for (String path : paths()) assertEquals(1, assets.getReferenceCount(path), path);
    verifyNoInteractions(replacement); assertTrue(root.getChildren().isEmpty());
    for (Actor actor : actors) {
      assertNull(actor.getParent()); assertTrue(actor.getListeners().isEmpty());
      assertTrue(actor.getCaptureListeners().isEmpty()); assertTrue(actor.getActions().isEmpty());
    }
    assertNull(panel.pentspin); assertNull(panel.pentspinL); assertNull(panel.controlsPage);
    assertNull(left.getLayer(0)); assertNull(right.getLayer(0));
    assertTrue(panel.controlRows.isEmpty());
  }

  @Test void closeHideAndParentNavigationReleaseCaptureAndOwnFocus() throws Exception {
    EscapePanel panel = panel(); Stage stage = stage(panel);
    for (int transition = 0; transition < 3; transition++) {
      capture(panel); assertTrue(keys.isCaptureOwner(panel)); assertSame(panel, stage.getKeyboardFocus());
      if (transition == 0) panel.close();
      else if (transition == 1) panel.setVisible(false);
      else assertTrue(panel.navigateBack());
      assertFalse(keys.isCaptureOwner(panel)); assertNull(panel.capturingRow); assertNull(stage.getKeyboardFocus());
    }
    verifyNoInteractions(preferences);
  }

  @Test void escapeAndBackCancelWithoutPersistingOrReassigning() throws Exception {
    EscapePanel panel = panel(); Stage stage = stage(panel);
    for (int key : new int[] {Input.Keys.ESCAPE, Input.Keys.BACK}) {
      capture(panel); assertTrue(keyListener(panel).keyDown(null, key));
      assertFalse(keys.isCaptureOwner(panel)); assertNull(stage.getKeyboardFocus());
    }
    verifyNoInteractions(preferences);
  }

  @Test void savingAndClearingReleaseCaptureAndPersistOnlySelectedBinding() throws Exception {
    EscapePanel panel = panel(); stage(panel);
    int[] original = Keys.Inventory.getAssignments();
    try {
      capture(panel); assertTrue(keyListener(panel).keyDown(null, Input.Keys.F12));
      assertEquals(Input.Keys.F12, Keys.Inventory.getPrimaryAssignment());
      assertFalse(keys.isCaptureOwner(panel)); assertNull(panel.capturingRow);
      capture(panel); assertTrue(keyListener(panel).keyDown(null, Input.Keys.BACKSPACE));
      // MappedKey compacts a surviving secondary assignment into the primary slot.
      assertEquals(original[1], Keys.Inventory.getPrimaryAssignment());
      assertEquals(0, Keys.Inventory.getSecondaryMapping()); assertFalse(keys.isCaptureOwner(panel));
      verify(keys, times(2)).save(Keys.Inventory); verify(preferences, times(2)).flush();
    } finally { Keys.Inventory.assign(original); }
  }

  @Test void oldPanelCannotReleaseNewPanelsCaptureOrKeyboardFocus() throws Exception {
    EscapePanel old = panel(); Stage stage = stage(old); capture(old);
    EscapePanel next = panel(); set(Actor.class, next, "stage", stage); capture(next);
    assertTrue(keys.isCaptureOwner(next)); assertSame(next, stage.getKeyboardFocus());
    assertFalse(keyListener(old).keyDown(null, Input.Keys.F12)); verifyNoInteractions(preferences);
    old.dispose(); assertTrue(keys.isCaptureOwner(next)); assertSame(next, stage.getKeyboardFocus());
    next.dispose(); assertFalse(keys.isCaptureOwner(next)); assertNull(stage.getKeyboardFocus());
  }

  @Test void captureReleasePreservesFocusMovedToUnrelatedActor() throws Exception {
    EscapePanel panel = panel(); Stage stage = stage(panel); capture(panel);
    Actor nextFocus = new Actor(); stage.setKeyboardFocus(nextFocus);
    panel.setVisible(false); assertSame(nextFocus, stage.getKeyboardFocus());
    assertFalse(keys.isCaptureOwner(panel)); panel.dispose(); assertSame(nextFocus, stage.getKeyboardFocus());
  }

  @Test void defaultsResetReleasesCaptureAndFocus() throws Exception {
    EscapePanel panel = panel(); Stage stage = stage(panel); capture(panel);
    Array<Actor> children = panel.controlsPage.getChildren();
    actionClick(children.get(children.size - 2)).clicked(null, 0, 0);
    verify(keys).resetAll(); assertFalse(keys.isCaptureOwner(panel)); assertNull(stage.getKeyboardFocus());
  }

  @Test void menuBorrowsOriginalAudioHostWithoutDisposingIt() throws Exception {
    EscapePanel panel = panel(); Audio replacement = mock(Audio.class); Riiablo.audio = replacement;
    actionClick(panel.options).clicked(null, 0, 0);
    assertEquals(EscapePanel.Page.OPTIONS, panel.getCurrentPage());
    verify(audio).play(2, true); clearInvocations(audio);
    panel.dispose(); verifyNoInteractions(audio, replacement);
  }

  @Test void detachAndReparentStillReleaseFocusFromOriginalStage() throws Exception {
    EscapePanel panel = panel(); Stage original = stage(panel); capture(panel);
    Stage replacement = mock(Stage.class); set(Actor.class, panel, "stage", replacement);
    panel.dispose(); assertNull(original.getKeyboardFocus());
    verify(replacement, never()).setKeyboardFocus(any());
  }

  @Test void recaptureAfterReparentDoesNotLeaveOldStageFocus() throws Exception {
    EscapePanel panel = panel(); Stage original = stage(panel); capture(panel);
    Stage replacement = stage(panel);
    lastClick((Actor) get(panel.controlRows.get(0), "primary")).clicked(null, 0, 0);
    assertNull(original.getKeyboardFocus()); assertSame(panel, replacement.getKeyboardFocus());
    panel.dispose(); assertNull(replacement.getKeyboardFocus());
  }

  @Test void refusedFocusDoesNotLeaveGameplayCaptureEnabled() throws Exception {
    EscapePanel panel = panel(); Stage stage = mock(Stage.class); set(Actor.class, panel, "stage", stage);
    capture(panel); assertFalse(keys.isCaptureOwner(panel)); assertNull(panel.capturingRow);
  }

  @Test void focusAcquisitionFailureRollsBackCapture() throws Exception {
    EscapePanel panel = panel(); Stage stage = mock(Stage.class); set(Actor.class, panel, "stage", stage);
    Error failure = new AssertionError("focus"); doThrow(failure).when(stage).setKeyboardFocus(panel);
    assertSame(failure, assertThrows(AssertionError.class, () -> capture(panel)));
    assertFalse(keys.isCaptureOwner(panel)); assertNull(panel.capturingRow);
  }

  @Test void focusReleaseFailureStillReleasesAssetsAndActorsWithoutRetry() throws Exception {
    EscapePanel panel = panel(); Stage stage = stage(panel); capture(panel);
    Error failure = new AssertionError("release focus"); doThrow(failure).when(stage).setKeyboardFocus(null);
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose));
    assertFalse(keys.isCaptureOwner(panel)); assertEquals(0, assets.getLoadedAssets());
    assertTrue(panel.getChildren().isEmpty()); assertTrue(panel.getListeners().isEmpty()); panel.dispose();
    verify(stage).setKeyboardFocus(null);
  }

  @Test void failedEarlyLoadRollsBackSuccessfulRequestsOnly() {
    Error failure = new AssertionError("exit load");
    doThrow(failure).when(assets).load(argThat((AssetDescriptor<?> asset) -> asset.fileName.endsWith("/exit.dc6")));
    assertSame(failure, assertThrows(AssertionError.class, EscapePanel::new));
    assertEquals(0, assets.getLoadedAssets());
  }

  @Test void latePageConstructionFailureRollsBackAllSheetsAndKeepsBorrowedFonts() {
    Error failure = new AssertionError("controls page");
    when(Riiablo.bundle.get("controls_title")).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, EscapePanel::new));
    assertEquals(0, assets.getLoadedAssets()); verify(Riiablo.fonts.font16, never()).dispose();
  }

  @Test void assetReleaseFailureStillReleasesOtherReferencesWithoutRetry() {
    EscapePanel panel = panel(); Error failure = new AssertionError("unload");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).when(assets).unload(paths()[0]);
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose));
    assertEquals(0, assets.getLoadedAssets()); panel.dispose(); verify(assets).unload(paths()[0]);
  }

  @Test void disposedCallbacksCannotWriteSettingsKeysOrReachNewSession() throws Exception {
    EscapePanel panel = panel(); stage(panel); capture(panel);
    verify(audio, times(2)).play(2, true); clearInvocations(audio);
    Array<Actor> actors = new Array<>(); collect(panel, actors);
    Array<ClickListener> clicks = new Array<>();
    for (Actor actor : actors) for (EventListener listener : actor.getListeners())
      if (listener instanceof ClickListener) clicks.add((ClickListener) listener);
    InputListener input = keyListener(panel); panel.dispose();
    Audio replacement = mock(Audio.class); Riiablo.audio = replacement;
    Riiablo.charData = mock(CharData.class); Riiablo.keys = mock(GdxKeyMapper.class);
    for (ClickListener click : clicks) { click.clicked(null, 0, 0); click.enter(null, 0, 0, -1, null); click.exit(null, 0, 0, -1, null); }
    assertFalse(input.keyDown(null, Input.Keys.F12));
    Batch batch = mock(Batch.class); panel.act(1); panel.draw(batch, 1); panel.open(); panel.setVisible(true);
    assertFalse(panel.isVisible()); verifyNoInteractions(audio, replacement, Riiablo.charData, Riiablo.keys, preferences, batch);
  }

  @Test void replacedLiveSessionIgnoresExitAndSettingCallbacksButStillReleasesOwnCapture() throws Exception {
    EscapePanel panel = panel(); stage(panel); capture(panel);
    verify(audio, times(2)).play(2, true); clearInvocations(audio);
    ClickListener exit = actionClick(panel.exit);
    Actor value = (Actor) get(panel.soundEnabled, "value"); ClickListener setting = lastClick(value);
    Riiablo.charData = mock(CharData.class); exit.clicked(null, 0, 0); setting.clicked(null, 0, 0);
    assertFalse(keyListener(panel).keyDown(null, Input.Keys.F12));
    panel.setVisible(false); assertFalse(keys.isCaptureOwner(panel)); verifyNoInteractions(audio, preferences, Riiablo.charData);
  }

  private EscapePanel panel() { EscapePanel panel = new EscapePanel(); panels.add(panel); return panel; }
  private Stage stage(EscapePanel panel) throws Exception {
    Stage stage = mock(Stage.class); final Actor[] focus = {null};
    when(stage.getKeyboardFocus()).thenAnswer(call -> focus[0]);
    when(stage.setKeyboardFocus(any())).thenAnswer(call -> { focus[0] = call.getArgument(0); return true; });
    set(Actor.class, panel, "stage", stage); return stage;
  }
  private void capture(EscapePanel panel) throws Exception {
    panel.open();
    actionClick(panel.options).clicked(null, 0, 0);
    actionClick(panel.optionsPage.getChildren().get(3)).clicked(null, 0, 0);
    assertEquals(EscapePanel.Page.CONTROLS, panel.getCurrentPage());
    List<?> rows = panel.controlRows; lastClick((Actor) get(rows.get(0), "primary")).clicked(null, 0, 0);
  }
  private static InputListener keyListener(EscapePanel panel) {
    for (EventListener listener : panel.getListeners()) if (listener instanceof InputListener) return (InputListener) listener;
    throw new AssertionError("key listener");
  }
  private String[] paths() { return new String[] {"data/local/ui/eng/options.dc6", "data/local/ui/eng/exit.dc6",
      "data/local/ui/eng/returntogame.dc6", "data/global/ui/CURSOR/pentspin.DC6"}; }
  private static void collect(Actor actor, Array<Actor> result) {
    result.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, result);
  }
  private static ClickListener lastClick(Actor actor) {
    ClickListener result = null; for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) result = (ClickListener) listener;
    assertNotNull(result); return result;
  }
  private static ClickListener actionClick(Actor actor) throws Exception {
    ClickListener action = null;
    for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) {
      try { listener.getClass().getDeclaredMethod("clicked", InputEvent.class, float.class, float.class); }
      catch (NoSuchMethodException inherited) { continue; }
      action = (ClickListener) listener;
    }
    assertNotNull(action); return action;
  }
  private static Object get(Object object, String name) throws Exception {
    Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
  private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
}
