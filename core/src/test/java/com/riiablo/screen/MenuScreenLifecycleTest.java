package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.I18NBundle;
import com.riiablo.Client;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.Textures;
import com.riiablo.audio.MusicController;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.StringTBLs;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.widget.Button;
import com.riiablo.widget.CharacterSelectButton;
import com.riiablo.entity.CharacterPreview;
import com.riiablo.codec.COF;
import com.riiablo.codec.COFD2;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.save.D2S;
import com.riiablo.save.D2SReader;
import com.riiablo.io.ByteInput;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Real constructors/buttons/asset reference counts; Stage, GL, fonts and animation are fixtures. */
class MenuScreenLifecycleTest {
  private static final String BUTTON = "data/global/sfx/cursor/button.wav";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final List<Screen> screens = new ArrayList<>();
  private final List<ControllerListener> listeners = new ArrayList<>();
  private Application oldApp;
  private com.badlogic.gdx.Files oldFiles;
  private AssetManager owner;
  private Client.InputProcessor input;
  private PaletteIndexedBatch batch;
  private Animation logo;
  private FontTBL.BitmapFont font;
  private MockedConstruction<Stage> stages;
  private MockedStatic<Controllers> controllers;
  private MockedStatic<Animation> animations;
  private RuntimeException controllerAttachFailure;
  private Error controllerDetachFailure;

  @BeforeEach void setup() throws Exception {
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
        aliases.put(field, field.get(null));
      }
    }
    oldApp = Gdx.app;
    oldFiles = Gdx.files;
    Gdx.app = mock(Application.class);
    Gdx.files = mock(com.badlogic.gdx.Files.class);
    Riiablo.colors = new Colors();
    Riiablo.input = input = new Client.InputProcessor();
    Riiablo.batch = batch = mock(PaletteIndexedBatch.class);
    Riiablo.client = mock(Client.class);
    Riiablo.music = mock(MusicController.class);
    Riiablo.string = mock(StringTBLs.class);
    when(Riiablo.string.lookup(anyInt())).thenReturn("menu");
    Riiablo.bundle = mock(I18NBundle.class);
    when(Riiablo.bundle.get(anyString())).thenReturn("continue");
    when(Riiablo.bundle.format(anyString(), any())).thenReturn("version");
    Riiablo.fonts = mock(Fonts.class);
    font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
    data.lineHeight = 16;
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(16);
    when(texture.getHeight()).thenReturn(16);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class);
    set(Textures.class, Riiablo.textures, "white", texture);
    set(BitmapFont.class, font, "data", data);
    set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data);
    when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region));
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = Riiablo.fonts.fontformal12 = Riiablo.fonts.fontexocet10 = font;
    owner = new AssetManager(FileHandle::new, false);
    owner.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc6 = mock(DC6.class);
        when(dc6.getTexture()).thenReturn(region);
        when(dc6.getTexture(anyInt())).thenReturn(region);
        return dc6;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    owner.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return mock(Sound.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
    Riiablo.assets = owner;
    stages = mockConstruction(Stage.class, (stage, context) -> {
      Group root = new Group();
      when(stage.getRoot()).thenReturn(root);
      when(stage.getWidth()).thenReturn(800f);
      when(stage.getHeight()).thenReturn(600f);
      doAnswer(call -> { root.addActor(call.getArgument(0)); return null; }).when(stage).addActor(any());
    });
    controllers = mockStatic(Controllers.class);
    controllers.when(() -> Controllers.addListener(any())).thenAnswer(call -> {
      listeners.add(call.getArgument(0));
      if (controllerAttachFailure != null) throw controllerAttachFailure;
      return null;
    });
    controllers.when(() -> Controllers.removeListener(any())).thenAnswer(call -> {
      listeners.remove(call.getArgument(0));
      if (controllerDetachFailure != null) throw controllerDetachFailure;
      return null;
    });
    logo = mock(Animation.class);
    Animation.Builder builder = mock(Animation.Builder.class, RETURNS_SELF);
    when(builder.build()).thenReturn(logo);
    animations = mockStatic(Animation.class);
    animations.when(Animation::builder).thenReturn(builder);
  }

  @AfterEach void restore() throws Exception {
    try {
      for (Screen screen : screens) screen.dispose();
      owner.dispose();
      input.dispose();
    } finally {
      animations.close();
      controllers.close();
      stages.close();
      Gdx.app = oldApp;
      Gdx.files = oldFiles;
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
    }
  }

  @ParameterizedTest @ValueSource(strings = {"splash", "menu", "multiplayer"})
  void repeatedVisibilityDoesNotAcquireOrReleaseConstructionReferences(String kind) {
    Screen screen = screen(kind);
    owner.finishLoading();
    Map<String, Integer> baseline = references();
    for (int i = 0; i < 3; i++) {
      screen.show(); screen.show();
      assertEquals(kind.equals("splash") ? 2 : 1, input.getProcessors().size);
      assertEquals(kind.equals("multiplayer") ? 0 : 1, listeners.size());
      assertEquals(baseline, references());
      screen.hide(); screen.hide();
      assertTrue(input.getProcessors().isEmpty());
      assertTrue(listeners.isEmpty());
      assertEquals(baseline, references());
      verify(stages.constructed().get(0), never()).dispose();
    }
    screen.dispose(); screen.dispose();
    assertEquals(0, owner.getLoadedAssets());
    verify(stages.constructed().get(0)).dispose();
    verify(batch, never()).dispose();
    verify(font, never()).dispose();
    assertThrows(IllegalStateException.class, screen::show);
  }

  @ParameterizedTest @ValueSource(strings = {"splash", "menu", "multiplayer"})
  void directDisposeDetachesVisibleInputAndDoesNotTouchReplacementHosts(String kind) {
    Screen screen = screen(kind);
    screen.show();
    Client.InputProcessor replacement = mock(Client.InputProcessor.class);
    AssetManager replacementAssets = mock(AssetManager.class);
    Riiablo.input = replacement;
    Riiablo.assets = replacementAssets;
    screen.dispose(); screen.hide(); screen.dispose();
    assertTrue(input.getProcessors().isEmpty());
    assertTrue(listeners.isEmpty());
    assertEquals(0, owner.getLoadedAssets());
    verifyNoInteractions(replacement, replacementAssets);
  }

  @ParameterizedTest @ValueSource(strings = {"splash", "menu", "multiplayer"})
  void disposalBeforeFirstShowReleasesQueuedReferencesWithoutBorrowedDisposal(String kind) {
    Screen screen = screen(kind);
    screen.dispose();
    assertEquals(0, owner.getLoadedAssets());
    assertTrue(input.getProcessors().isEmpty());
    assertTrue(listeners.isEmpty());
    verify(stages.constructed().get(0)).dispose();
    verify(batch, never()).dispose();
    verify(font, never()).dispose();
  }

  @Test void twoScreensKeepSharedLogoAliveAndButtonReferencesReturnToExternalOwner() {
    owner.load(BUTTON, Sound.class);
    MenuScreen menu = (MenuScreen) screen("menu");
    MultiplayerScreen multiplayer = new MultiplayerScreen(menu.D2logoLeft, menu.D2logoRight);
    screens.add(multiplayer);
    owner.finishLoading();
    DC6 shared = owner.get(menu.D2logoBlackLeftDescriptor);
    assertEquals(10, owner.getReferenceCount(BUTTON)); // external 1 + menu 5 + multiplayer 4
    assertEquals(2, owner.getReferenceCount(menu.D2logoBlackLeftDescriptor.fileName));
    menu.dispose();
    assertEquals(5, owner.getReferenceCount(BUTTON));
    assertEquals(1, owner.getReferenceCount(menu.D2logoBlackLeftDescriptor.fileName));
    verify(shared, never()).dispose();
    multiplayer.dispose();
    verify(shared).dispose();
    assertEquals(1, owner.getReferenceCount(BUTTON));
    assertEquals(1, owner.getLoadedAssets());
  }

  @Test void constructorFailureRetiresButtonThatWasNotYetParented() {
    RuntimeException failure = new IllegalStateException("second button label");
    when(Riiablo.string.lookup(5107)).thenThrow(failure);
    assertSame(failure, assertThrows(RuntimeException.class, () -> new MenuScreen(logo, logo)));
    assertEquals(0, owner.getLoadedAssets());
    verify(stages.constructed().get(0)).dispose();
    assertTrue(input.getProcessors().isEmpty());
  }

  @ParameterizedTest @ValueSource(strings = {"splash", "menu", "multiplayer"})
  void stageFailureStillReleasesAllAssetsAndDoesNotRetry(String kind) {
    Screen screen = screen(kind);
    screen.show();
    RuntimeException failure = new IllegalStateException("stage");
    doThrow(failure).when(stages.constructed().get(0)).dispose();
    assertSame(failure, assertThrows(RuntimeException.class, screen::dispose));
    assertEquals(0, owner.getLoadedAssets());
    assertTrue(input.getProcessors().isEmpty());
    assertTrue(listeners.isEmpty());
    screen.dispose();
    verify(stages.constructed().get(0)).dispose();
  }

  @Test void controllerAttachFailureRollsBackStageAndCanBeRetriedWithoutReloading() {
    MenuScreen menu = (MenuScreen) screen("menu");
    owner.finishLoading();
    Map<String, Integer> baseline = references();
    RuntimeException failure = new IllegalStateException("controller");
    controllerAttachFailure = failure;
    assertSame(failure, assertThrows(RuntimeException.class, menu::show));
    assertTrue(input.getProcessors().isEmpty());
    assertTrue(listeners.isEmpty());
    assertEquals(baseline, references());
    controllerAttachFailure = null;
    menu.show();
    assertEquals(1, input.getProcessors().size);
  }

  @Test void controllerDetachErrorDoesNotSkipStageButtonsOrAssets() {
    MenuScreen menu = (MenuScreen) screen("menu");
    menu.show();
    Error failure = new AssertionError("controller detach");
    controllerDetachFailure = failure;
    assertSame(failure, assertThrows(AssertionError.class, menu::dispose));
    assertEquals(0, owner.getLoadedAssets());
    assertTrue(input.getProcessors().isEmpty());
    verify(stages.constructed().get(0)).dispose();
    menu.dispose();
  }

  @Test void hiddenAndDisposedScreensDoNotRenderOrNavigateFromStaleSplashInput() {
    SplashScreen splash = (SplashScreen) screen("splash");
    splash.render(1);
    splash.show(); splash.hide();
    assertFalse(splash.inputProcessor.keyDown(com.badlogic.gdx.Input.Keys.ENTER));
    splash.render(1); splash.dispose(); splash.render(1);
    verify(batch, never()).begin(any());
    verify(stages.constructed().get(0), never()).draw();
    verifyNoInteractions(Riiablo.client);
  }

  @Test void helperDoesNotCallArbitraryActorDisposalAndClosesUnparentedButtons() {
    MenuScreenResources resources = new MenuScreenResources(owner, input);
    Button button = resources.own(new Button(new Button.ButtonStyle()));
    Stage stage = resources.own(new Stage());
    class BorrowedActor extends com.badlogic.gdx.scenes.scene2d.Actor implements com.badlogic.gdx.utils.Disposable {
      int disposals;
      @Override public void dispose() { disposals++; }
    }
    BorrowedActor actor = new BorrowedActor();
    stage.getRoot().addActor(actor);
    InputAdapter other = new InputAdapter();
    input.addProcessor(other);
    resources.useStageInput(); resources.show(); resources.dispose();
    assertEquals(1, input.getProcessors().size);
    assertSame(other, input.getProcessors().first());
    assertEquals(0, owner.getLoadedAssets());
    assertEquals(0, actor.disposals);
    button.dispose(); // the same sound owner is idempotent
    verify(stage).dispose();
  }

  @Test void platformShutdownDoesNotReinitializeControllerFacadeDuringScreenDisposal() {
    MenuScreen menu = (MenuScreen) screen("menu");
    menu.show();
    org.mockito.ArgumentCaptor<com.badlogic.gdx.LifecycleListener> lifecycle =
        org.mockito.ArgumentCaptor.forClass(com.badlogic.gdx.LifecycleListener.class);
    verify(Gdx.app).addLifecycleListener(lifecycle.capture());
    lifecycle.getValue().dispose(); // backend closes its controller registry before Client.dispose
    listeners.clear(); // the platform now owns retirement of those registrations
    assertThrows(IllegalStateException.class, menu::show);
    menu.dispose();
    controllers.verify(() -> Controllers.removeListener(any()), never());
    assertTrue(input.getProcessors().isEmpty());
    assertEquals(0, owner.getLoadedAssets());
    verify(Gdx.app).removeLifecycleListener(lifecycle.getValue());
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void characterSelectionVisibilityRetainsPreviewsAndReferences(int variant) throws Exception {
    prepareCharacters(headerFile("First"), headerFile("Second"));
    Screen screen = selection(variant);
    owner.finishLoading();
    Array<CharacterSelectButton> characters = field(screen, "characters");
    assertEquals(2, characters.size);
    CharacterPreview preview = field(characters.first(), "character");
    preview.draw(batch); owner.finishLoading(); preview.draw(batch);
    Map<String, Integer> baseline = references();
    assertEquals(6, owner.getReferenceCount(BUTTON));
    assertEquals(2, owner.getReferenceCount("data/global/ui/CharSelect/charselectbox.dc6"));
    for (int i = 0; i < 3; i++) {
      screen.show(); screen.show();
      assertEquals(1, input.getProcessors().size);
      screen.hide(); screen.hide(); screen.render(1);
      assertTrue(input.getProcessors().isEmpty());
      assertSame(preview, field(characters.first(), "character"));
      assertEquals(baseline, references());
    }
    screen.dispose(); screen.dispose();
    assertEquals(0, owner.getLoadedAssets());
    verify(stages.constructed().get(0)).dispose();
    assertThrows(IllegalStateException.class, screen::show);
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void characterSelectionRefreshRetiresPreviousListAndKeepsExternalBox(int variant) throws Exception {
    prepareCharacters(headerFile("First"), headerFile("Second"));
    String box = "data/global/ui/CharSelect/charselectbox.dc6";
    owner.load(box, DC6.class);
    Screen screen = selection(variant);
    owner.finishLoading();
    for (int cycle = 0; cycle < 3; cycle++) {
      Array<CharacterSelectButton> characters = field(screen, "characters");
      CharacterSelectButton previous = characters.first();
      CharacterPreview preview = field(previous, "character");
      preview.draw(batch); owner.finishLoading(); preview.draw(batch);
      refresh(screen);
      owner.finishLoading();
      assertNull(field(previous, "character"));
      assertThrows(IllegalStateException.class, previous::select);
      assertEquals(3, owner.getReferenceCount(box));
      assertEquals(6, owner.getReferenceCount(BUTTON));
      assertTrue(owner.getAssetNames().toString().indexOf(".dcc") < 0);
    }
    screen.dispose();
    assertEquals(1, owner.getLoadedAssets());
    assertEquals(1, owner.getReferenceCount(box));
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void characterSelectionDisposeBeforeShowAndReplacementHostProtection(int variant) throws Exception {
    prepareCharacters(headerFile("First"));
    Screen screen = selection(variant);
    AssetManager replacement = mock(AssetManager.class);
    Client.InputProcessor replacementInput = mock(Client.InputProcessor.class);
    Riiablo.assets = replacement; Riiablo.input = replacementInput;
    screen.dispose(); screen.hide(); screen.dispose();
    assertEquals(0, owner.getLoadedAssets());
    verifyNoInteractions(replacement, replacementInput);
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void characterSelectionMalformedSecondSaveRollsBackAlreadyBuiltList(int variant) throws Exception {
    FileHandle broken = headerFile("ZBroken");
    when(broken.readBytes()).thenReturn(new byte[2]);
    prepareCharacters(headerFile("First"), broken);
    assertThrows(RuntimeException.class, () -> selection(variant));
    assertEquals(0, owner.getLoadedAssets());
    verify(stages.constructed().get(0)).dispose();
    assertTrue(input.getProcessors().isEmpty());
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void characterSelectionStageErrorStillDisposesAllChildrenAndAssets(int variant) throws Exception {
    prepareCharacters(headerFile("First"));
    Screen screen = selection(variant);
    screen.show();
    Error failure = new AssertionError("selection Stage");
    doThrow(failure).when(stages.constructed().get(0)).dispose();
    assertSame(failure, assertThrows(AssertionError.class, screen::dispose));
    assertEquals(0, owner.getLoadedAssets());
    assertTrue(input.getProcessors().isEmpty());
    screen.dispose();
  }

  @Test void previewRepeatedInvalidationSharesOneLayerReferenceAndRetiresOnOriginalManager() throws Exception {
    prepareCharacters();
    D2S d2s = D2SReader.INSTANCE.readD2S(headerFile("First"));
    CharacterPreview preview = new CharacterPreview(d2s);
    preview.draw(batch); owner.finishLoading(); preview.draw(batch);
    String path = owner.getAssetNames().first();
    DCC layer = owner.get(path, DCC.class);
    for (int cycle = 0; cycle < 3; cycle++) {
      preview.invalidate(); preview.draw(batch); owner.finishLoading();
      assertEquals(1, owner.getReferenceCount(path));
    }
    AssetManager replacement = mock(AssetManager.class);
    Riiablo.assets = replacement;
    preview.dispose(); preview.dispose(); preview.draw(batch); preview.act(1);
    assertEquals(0, owner.getLoadedAssets());
    verify(layer).dispose();
    verifyNoInteractions(replacement);
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void emptySelectionDoesNotAcquireASelectionBoxOrRenderWhenHidden(int variant) throws Exception {
    prepareCharacters();
    Screen screen = selection(variant);
    owner.finishLoading();
    assertFalse(owner.contains("data/global/ui/CharSelect/charselectbox.dc6"));
    assertTrue(((Array<?>) field(screen, "characters")).isEmpty());
    assertTrue(((com.badlogic.gdx.scenes.scene2d.ui.Button) field(screen, "btnOK")).isDisabled());
    screen.render(1); screen.show(); screen.hide(); screen.render(1);
    verify(stages.constructed().get(0), never()).draw();
    screen.dispose(); assertEquals(0, owner.getLoadedAssets());
  }

  @ParameterizedTest @ValueSource(ints = {1, 2, 3})
  void hiddenSelectionIgnoresStaleActionCallbacks(int variant) throws Exception {
    prepareCharacters(headerFile("First"));
    Screen screen = selection(variant);
    screen.show(); screen.hide();
    com.badlogic.gdx.scenes.scene2d.ui.Button exit = field(screen, "btnExit");
    com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent event =
        new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent();
    exit.fire(event);
    verifyNoInteractions(Riiablo.client);
    screen.dispose();
  }

  @Test void previewDisposalDrainsItsQueuedLayerWithoutCancellingAnotherPreview() throws Exception {
    prepareCharacters();
    D2S d2s = D2SReader.INSTANCE.readD2S(headerFile("First"));
    CharacterPreview first = new CharacterPreview(d2s);
    CharacterPreview second = new CharacterPreview(d2s);
    first.draw(batch); second.draw(batch);
    first.dispose();
    owner.finishLoading();
    assertEquals(1, owner.getLoadedAssets());
    String name = owner.getAssetNames().first();
    assertEquals(1, owner.getReferenceCount(name));
    second.draw(batch); second.dispose();
    assertEquals(0, owner.getLoadedAssets());
  }

  @Test void characterButtonReplacementDisposesOldPreviewButNotOtherConsumers() throws Exception {
    prepareCharacters();
    D2S d2s = D2SReader.INSTANCE.readD2S(headerFile("First"));
    CharacterSelectButton first = new CharacterSelectButton(d2s, null);
    CharacterSelectButton second = new CharacterSelectButton(d2s, null);
    CharacterPreview old = field(first, "character");
    old.draw(batch); owner.finishLoading(); old.draw(batch);
    CharacterPreview shared = field(second, "character");
    shared.draw(batch); owner.finishLoading(); shared.draw(batch);
    String path = null;
    for (String name : owner.getAssetNames()) if (name.endsWith(".dcc")) path = name;
    assertNotNull(path);
    DCC dcc = owner.get(path, DCC.class);
    first.set(d2s, null);
    assertEquals(1, owner.getReferenceCount(path));
    verify(dcc, never()).dispose();
    first.dispose(); second.select();
    assertEquals(1, owner.getReferenceCount("data/global/ui/CharSelect/charselectbox.dc6"));
    second.dispose(); second.dispose();
    assertEquals(0, owner.getLoadedAssets());
    verify(dcc).dispose();
  }

  @Test void characterButtonFailedReplacementKeepsPreviousPreviewAndFinalDisposal() throws Exception {
    prepareCharacters();
    CharacterSelectButton button = new CharacterSelectButton(D2SReader.INSTANCE.readD2S(headerFile("First")), null);
    CharacterPreview previous = field(button, "character");
    previous.draw(batch); owner.finishLoading(); previous.draw(batch);
    D2S invalid = mock(D2S.class);
    when(invalid.composites()).thenReturn(new byte[16]);
    when(invalid.colors()).thenReturn(new byte[16]);
    when(invalid.getProgressionString()).thenThrow(new IllegalStateException("label"));
    assertThrows(IllegalStateException.class, () -> button.set(invalid, null));
    assertSame(previous, field(button, "character"));
    button.dispose();
    assertEquals(0, owner.getLoadedAssets());
  }

  @Test void explicitlyOwnedChildErrorDoesNotSkipSiblingsOrStage() {
    MenuScreenResources resources = new MenuScreenResources(owner, input);
    com.badlogic.gdx.utils.Disposable first = mock(com.badlogic.gdx.utils.Disposable.class);
    com.badlogic.gdx.utils.Disposable second = mock(com.badlogic.gdx.utils.Disposable.class);
    Error failure = new AssertionError("child");
    doThrow(failure).when(first).dispose();
    resources.ownResource(first); resources.ownResource(second);
    Stage stage = resources.own(new Stage());
    resources.useStageInput(); resources.show();
    assertSame(failure, assertThrows(AssertionError.class, resources::dispose));
    verify(second).dispose(); verify(stage).dispose();
    assertTrue(input.getProcessors().isEmpty());
    resources.dispose();
    verify(first).dispose();
  }

  private void prepareCharacters(FileHandle... saves) throws Exception {
    Riiablo.saves = mock(FileHandle.class);
    when(Riiablo.saves.list(D2S.EXT)).thenReturn(saves);
    Riiablo.files = mock(com.riiablo.Files.class);
    set(com.riiablo.Files.class, Riiablo.files, "weapons", mock(Weapons.class));
    Riiablo.cofs = mock(com.riiablo.COFs.class);
    COFD2 cofs = mock(COFD2.class);
    set(com.riiablo.COFs.class, Riiablo.cofs, "chars_cof", cofs);
    COF cof = mock(COF.class);
    COF.Layer head = mock(COF.Layer.class);
    head.component = 0; head.weaponClass = "HTH";
    when(cofs.lookup(anyString())).thenReturn(cof);
    when(cof.getNumLayers()).thenReturn(1);
    when(cof.getLayer(0)).thenReturn(head);
    when(logo.getNumDirections()).thenReturn(8);
    when(logo.setLayer(eq(head), any(), eq(false))).thenReturn(mock(Animation.Layer.class, RETURNS_SELF));
    animations.when(() -> Animation.newAnimation(cof)).thenReturn(logo);
    owner.setLoader(DCC.class, new SynchronousAssetLoader<DCC, AssetLoaderParameters<DCC>>(FileHandle::new) {
      @Override public DCC load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DCC> params) { return mock(DCC.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DCC> params) { return null; }
    });
  }

  private Screen selection(int variant) {
    Screen screen = variant == 1 ? new SelectCharacterScreen()
        : variant == 2 ? new SelectCharacterScreen2(null) : new SelectCharacterScreen3(mock(com.badlogic.gdx.net.Socket.class));
    screens.add(screen);
    return screen;
  }

  private static FileHandle headerFile(String name) {
    // A checksum-valid 1.10 header, not a playable full-body save.
    byte[] bytes = new byte[0x14f];
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(0, 0xaa55aa55).putInt(4, 96).putInt(8, bytes.length);
    for (int i = 0; i < name.length(); i++) bytes[0x14 + i] = (byte) name.charAt(i);
    bytes[0x2b] = 1; bytes[0x88] = 1;
    buffer.putInt(12, D2SReader.INSTANCE.calculateChecksum(ByteInput.wrap(bytes)));
    FileHandle file = mock(FileHandle.class);
    when(file.name()).thenReturn(name + ".d2s");
    when(file.readBytes()).thenReturn(bytes);
    return file;
  }

  @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(owner);
  }

  private static void refresh(Screen screen) throws Exception {
    java.lang.reflect.Method method = screen.getClass().getDeclaredMethod("refreshCharacters");
    method.setAccessible(true);
    method.invoke(screen);
  }

  private Screen screen(String kind) {
    Screen screen = kind.equals("splash") ? new SplashScreen()
        : kind.equals("menu") ? new MenuScreen(logo, logo) : new MultiplayerScreen(logo, logo);
    screens.add(screen);
    return screen;
  }

  private Map<String, Integer> references() {
    Map<String, Integer> refs = new HashMap<>();
    for (String name : owner.getAssetNames()) refs.put(name, owner.getReferenceCount(name));
    return refs;
  }

  private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
