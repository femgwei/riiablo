package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.SnapshotArray;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.riiablo.Client;
import com.riiablo.Cvars;
import com.riiablo.Cursor;
import com.riiablo.Keys;
import com.riiablo.Riiablo;
import com.riiablo.cvar.Cvar;
import com.riiablo.cvar.CvarStateAdapter;
import com.riiablo.key.MappedKey;
import com.riiablo.key.MappedKeyStateAdapter;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.screen.panel.ControlPanel;
import com.riiablo.widget.TextArea;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs production subscription configuration/disposal with mocked host resources, without GL/MPQ. */
class GameScreenSubscriptionsTest {
  private Application previousApp;
  private Client previousClient;
  private Client.InputProcessor previousInput;
  private AssetManager previousAssets;
  private World previousEngine;
  private GameScreen previousGame;
  private Cursor previousCursor;
  private final List<GameScreen> screens = new ArrayList<>();

  @BeforeEach
  void installHost() {
    previousApp = Gdx.app;
    previousClient = Riiablo.client;
    previousInput = Riiablo.input;
    previousAssets = Riiablo.assets;
    previousEngine = Riiablo.engine;
    previousGame = Riiablo.game;
    previousCursor = Riiablo.cursor;
    Gdx.app = mock(Application.class);
    when(Gdx.app.getType()).thenReturn(Application.ApplicationType.Desktop);
    Riiablo.client = mock(Client.class);
    Riiablo.input = mock(Client.InputProcessor.class);
    Riiablo.assets = mock(AssetManager.class);
    Riiablo.cursor = mock(Cursor.class);
  }

  @AfterEach
  void cleanUp() throws Exception {
    for (GameScreen screen : screens) {
      scope(screen, "visibleSubscriptions").dispose();
      scope(screen, "sessionSubscriptions").dispose();
    }
    Gdx.app = previousApp;
    Riiablo.client = previousClient;
    Riiablo.input = previousInput;
    Riiablo.assets = previousAssets;
    Riiablo.engine = previousEngine;
    Riiablo.game = previousGame;
    Riiablo.cursor = previousCursor;
  }

  @Test
  void repeatedVisibilityCyclesKeepAllKeysAndProcessorsBalanced() throws Exception {
    GameScreen screen = screen();
    configure(screen);
    configure(screen);
    ScreenSubscriptionScope visible = scope(screen, "visibleSubscriptions");
    visible.activate();
    Client.ScreenBoundsListener firstBounds = screen.screenBoundsListener;
    visible.activate();
    for (MappedKey key : actionKeys()) assertTrue(key.containsStateListener(screen.mappedKeyStateListener));
    assertTrue(Keys.DebugMode.containsStateListener(screen.debugKeyListener));
    verify(Riiablo.input).addProcessor(screen.testingInputProcessor);
    verify(Riiablo.input).addProcessor(screen.stage);
    verify(Riiablo.input).addProcessor(screen.scaledStage);
    verify(Riiablo.client).addScreenBoundsListener(firstBounds);

    screen.hide();
    screen.hide();
    for (MappedKey key : actionKeys()) assertFalse(key.containsStateListener(screen.mappedKeyStateListener));
    assertFalse(Keys.DebugMode.containsStateListener(screen.debugKeyListener));
    verify(Riiablo.input).removeProcessor(screen.testingInputProcessor);
    verify(Riiablo.client).removeScreenBoundsListener(firstBounds);
    visible.activate();
    assertNotSame(firstBounds, screen.screenBoundsListener, "keyboard bounds state resets on each show");
    verify(Riiablo.input, times(2)).addProcessor(screen.stage);
  }

  @Test
  void hidingOldScreenPreservesOtherScreensAndUnrelatedCvarListeners() throws Exception {
    AtomicInteger changes = new AtomicInteger();
    Cvar.StateListener<Boolean> observer = new CvarStateAdapter<Boolean>() {
      @Override public void onChanged(Cvar<Boolean> cvar, Boolean from, Boolean to) {
        changes.incrementAndGet();
      }
    };
    Cvar<Boolean> grouped = Cvars.Client.Display.KeepControlPanelGrouped;
    boolean previous = grouped.get();
    grouped.addStateListener(false, observer);
    try {
      GameScreen old = screen();
      GameScreen replacement = screen();
      configure(old);
      configure(replacement);
      scope(old, "visibleSubscriptions").activate();
      scope(replacement, "visibleSubscriptions").activate();
      old.hide();
      assertTrue(grouped.containsStateListener(observer));
      assertTrue(Keys.Esc.containsStateListener(replacement.mappedKeyStateListener));
      clearInvocations(old.controlPanel, replacement.controlPanel);
      grouped.set(!previous);
      assertEquals(1, changes.get());
      verifyNoInteractions(old.controlPanel);
      if (!previous) verify(replacement.controlPanel).pack();
      else verify(replacement.controlPanel).layout();
    } finally {
      grouped.set(previous);
      grouped.removeStateListener(observer);
    }
  }

  @Test
  void removalTargetsTheOriginalHostEvenAfterGlobalHostReplacement() throws Exception {
    GameScreen screen = screen();
    Client originalClient = Riiablo.client;
    Client.InputProcessor originalInput = Riiablo.input;
    configure(screen);
    scope(screen, "visibleSubscriptions").activate();
    Riiablo.client = mock(Client.class);
    Riiablo.input = mock(Client.InputProcessor.class);
    screen.hide();
    verify(originalClient).removeScreenBoundsListener(screen.screenBoundsListener);
    verify(originalInput).removeProcessor(screen.stage);
    verifyNoInteractions(Riiablo.client, Riiablo.input);
  }

  @Test
  void directDisposalDetachesWithoutHideAndReleasesOwnedResourcesOnlyOnce() throws Exception {
    GameScreen screen = screen();
    configure(screen);
    scope(screen, "visibleSubscriptions").activate();
    ItemData playerItems = mock(ItemData.class);
    ItemData mercItems = mock(ItemData.class);
    CharData.MercData merc = mock(CharData.MercData.class);
    when(screen.charData.getItems()).thenReturn(playerItems);
    when(screen.charData.getMerc()).thenReturn(merc);
    when(merc.getItems()).thenReturn(mercItems);
    Method bind = GameScreen.class.getDeclaredMethod("bindItemLocationListeners");
    bind.setAccessible(true);
    bind.invoke(screen);
    ItemData.LocationListener cursor = Riiablo.cursor;
    verify(playerItems).addLocationListener(cursor);
    verify(mercItems).addLocationListener(cursor);
    Riiablo.game = screen;
    Riiablo.engine = screen.engine;
    screen.dispose();
    screen.dispose();
    screen.hide();
    screen.resume();
    verify(screen.engine).dispose();
    verify(screen.stage).dispose();
    verify(screen.scaledStage).dispose();
    verify(Riiablo.assets).unload("session-owned.wav");
    verify(Riiablo.input).removeProcessor(screen.scaledStage);
    assertFalse(Keys.Esc.containsStateListener(screen.mappedKeyStateListener));
    verify(playerItems).removeLocationListener(cursor);
    verify(mercItems).removeLocationListener(cursor);
    assertNull(Riiablo.game);
    assertNull(Riiablo.engine);
    assertThrows(IllegalStateException.class, screen::show);
  }

  @Test
  void disposingAnOldScreenCannotClearTheNewSessionsAliasesOrListeners() throws Exception {
    GameScreen old = screen();
    GameScreen replacement = screen();
    configure(old);
    configure(replacement);
    scope(old, "visibleSubscriptions").activate();
    scope(replacement, "visibleSubscriptions").activate();
    Riiablo.game = replacement;
    Riiablo.engine = replacement.engine;
    old.dispose();
    old.resume();
    assertSame(replacement, Riiablo.game);
    assertSame(replacement.engine, Riiablo.engine);
    assertTrue(Keys.Help.containsStateListener(replacement.mappedKeyStateListener));
    verify(replacement.engine, never()).dispose();
    verify(replacement.stage, never()).dispose();
    verify(replacement.scaledStage, never()).dispose();
  }

  @Test
  void unusedScreenDisposalDoesNotRemoveNeverAttachedProcessors() throws Exception {
    GameScreen screen = screen();
    screen.dispose();
    verifyNoInteractions(Riiablo.input, Riiablo.client);
    verify(screen.engine).dispose();
  }

  @Test
  void hidingKeepsBothInventorySubscriptionsUntilFinalDisposal() throws Exception {
    GameScreen screen = screen();
    ItemData playerItems = mock(ItemData.class);
    ItemData mercItems = mock(ItemData.class);
    CharData.MercData merc = mock(CharData.MercData.class);
    when(screen.charData.getItems()).thenReturn(playerItems);
    when(screen.charData.getMerc()).thenReturn(merc);
    when(merc.getItems()).thenReturn(mercItems);
    Method bind = GameScreen.class.getDeclaredMethod("bindItemLocationListeners");
    bind.setAccessible(true);
    bind.invoke(screen);
    ItemData.LocationListener originalCursor = Riiablo.cursor;
    screen.hide();
    verify(playerItems, never()).removeLocationListener(any());
    verify(mercItems, never()).removeLocationListener(any());
    Riiablo.cursor = mock(Cursor.class);
    screen.dispose();
    verify(playerItems).removeLocationListener(originalCursor);
    verify(mercItems).removeLocationListener(originalCursor);
  }

  @Test
  void worldFailureStillDisposesBothStagesAndAssetsAndClearsAliases() throws Exception {
    GameScreen screen = screen();
    RuntimeException failure = new RuntimeException("world cleanup");
    doThrow(failure).when(screen.engine).dispose();
    Riiablo.game = screen;
    Riiablo.engine = screen.engine;
    assertSame(failure, assertThrows(RuntimeException.class, screen::dispose));
    verify(screen.stage).dispose();
    verify(screen.scaledStage).dispose();
    verify(Riiablo.assets).unload("session-owned.wav");
    assertNull(Riiablo.game);
    assertNull(Riiablo.engine);
    screen.dispose();
    verify(screen.engine).dispose();
  }

  @Test
  void actorAndAssetFailuresDoNotSkipOtherResourcesOrLoseFailures() throws Exception {
    GameScreen screen = screen();
    DisposableActor first = mock(DisposableActor.class);
    DisposableActor second = mock(DisposableActor.class);
    DisposableActor scaled = mock(DisposableActor.class);
    screen.stage.getActors().addAll(first, second);
    screen.scaledStage.getActors().add(scaled);
    RuntimeException actorFailure = new RuntimeException("actor cleanup");
    RuntimeException stageFailure = new RuntimeException("stage cleanup");
    RuntimeException assetFailure = new RuntimeException("asset cleanup");
    doThrow(actorFailure).when(first).dispose();
    doThrow(stageFailure).when(screen.stage).dispose();
    doThrow(assetFailure).when(Riiablo.assets).unload("session-owned.wav");
    Array<AssetDescriptor> assets = new Array<>();
    assets.add(new AssetDescriptor<>("session-owned.wav", Sound.class));
    assets.add(new AssetDescriptor<>("second.wav", Sound.class));
    assets.add(new AssetDescriptor<>("second.wav", Sound.class));
    set(screen, "preloadedAssets", assets);

    assertSame(actorFailure, assertThrows(RuntimeException.class, screen::dispose));
    assertArrayEquals(new Throwable[] {stageFailure, assetFailure}, actorFailure.getSuppressed());
    verify(second).dispose();
    verify(scaled).dispose();
    verify(screen.scaledStage).dispose();
    verify(Riiablo.assets, times(2)).unload("second.wav");
  }

  @Test
  void actorSelfRemovalUsesSnapshotAndBorrowedBatchIsNeverDisposed() throws Exception {
    GdxNativesLoader.load();
    Graphics previousGraphics = Gdx.graphics;
    GL20 previousGl = Gdx.gl;
    Gdx.graphics = mock(Graphics.class);
    Gdx.gl = mock(GL20.class);
    when(Gdx.graphics.getWidth()).thenReturn(800);
    when(Gdx.graphics.getHeight()).thenReturn(600);
    try {
      GameScreen screen = screen();
      Batch sharedBatch = mock(Batch.class);
      screen.stage = new Stage(new ScreenViewport(), sharedBatch);
      screen.scaledStage = new Stage(new ScreenViewport(), sharedBatch);
      AtomicInteger releases = new AtomicInteger();
      for (Stage stage : new Stage[] {screen.stage, screen.scaledStage}) {
        stage.addActor(new RemovingActor(releases));
        stage.addActor(new RemovingActor(releases));
      }
      screen.map = mock(com.riiablo.map.Map.class);
      screen.hide();
      assertEquals(0, releases.get());
      screen.dispose();
      screen.dispose();
      assertEquals(4, releases.get());
      assertEquals(0, screen.stage.getActors().size);
      assertEquals(0, screen.scaledStage.getActors().size);
      verify(sharedBatch, never()).dispose();
      verify(screen.map, never()).dispose();
    } finally {
      Gdx.graphics = previousGraphics;
      Gdx.gl = previousGl;
    }
  }

  @Test
  void savingFailureStillCleansUpAndIdenticalStagesAreNotDisposedTwice() throws Exception {
    GameScreen screen = screen();
    screen.scaledStage = screen.stage;
    screen.characterSavedForExit = false;
    RuntimeException failure = new RuntimeException("save");
    doThrow(failure).when(screen).saveCharacter();
    assertSame(failure, assertThrows(RuntimeException.class, screen::dispose));
    verify(screen.engine).dispose();
    verify(screen.stage).dispose();
    verify(Riiablo.assets).unload("session-owned.wav");
  }

  private static class DisposableActor extends Actor implements Disposable {
    @Override public void dispose() {}
  }

  private static class RemovingActor extends Actor implements Disposable {
    private final AtomicInteger releases;
    RemovingActor(AtomicInteger releases) { this.releases = releases; }
    @Override public void dispose() {
      releases.incrementAndGet();
      remove();
    }
  }

  private GameScreen screen() throws Exception {
    // Bypass the asset-heavy constructor only; invoke the real production methods below.
    GameScreen screen = mock(GameScreen.class, CALLS_REAL_METHODS);
    set(screen, "visibleSubscriptions", new ScreenSubscriptionScope());
    set(screen, "sessionSubscriptions", new ScreenSubscriptionScope());
    screen.engine = mock(World.class);
    screen.stage = mock(Stage.class);
    screen.scaledStage = mock(Stage.class);
    when(screen.stage.getActors()).thenReturn(new SnapshotArray<>(Actor.class));
    when(screen.scaledStage.getActors()).thenReturn(new SnapshotArray<>(Actor.class));
    screen.charData = mock(CharData.class);
    screen.characterSavedForExit = true;
    screen.debugKeyListener = new MappedKeyStateAdapter();
    screen.mappedKeyStateListener = new MappedKeyStateAdapter();
    screen.testingInputProcessor = new InputAdapter();
    screen.input = mock(TextArea.class);
    screen.controlPanel = mock(ControlPanel.class);
    Array<AssetDescriptor> assets = new Array<>();
    assets.add(new AssetDescriptor<>("session-owned.wav", Sound.class));
    set(screen, "preloadedAssets", assets);
    screens.add(screen);
    return screen;
  }

  private static void configure(GameScreen screen) throws Exception {
    Method method = GameScreen.class.getDeclaredMethod("configureSubscriptions");
    method.setAccessible(true);
    method.invoke(screen);
  }

  private static ScreenSubscriptionScope scope(GameScreen screen, String name) throws Exception {
    Field field = GameScreen.class.getDeclaredField(name);
    field.setAccessible(true);
    return (ScreenSubscriptionScope) field.get(screen);
  }

  private static void set(GameScreen screen, String name, java.lang.Object value) throws Exception {
    Field field = GameScreen.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(screen, value);
  }

  private static MappedKey[] actionKeys() {
    return new MappedKey[] {Keys.Esc, Keys.Help, Keys.Enter, Keys.Run, Keys.Inventory,
        Keys.Character, Keys.Hireling, Keys.Spells, Keys.Quests, Keys.SwapWeapons,
        Keys.Stash, Keys.Vendor, Keys.Belt1, Keys.Belt2, Keys.Belt3, Keys.Belt4};
  }
}
