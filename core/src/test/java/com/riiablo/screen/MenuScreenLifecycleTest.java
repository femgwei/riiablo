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
