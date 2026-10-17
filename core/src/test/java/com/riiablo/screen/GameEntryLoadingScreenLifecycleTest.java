package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.riiablo.Client;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Palettes;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class GameEntryLoadingScreenLifecycleTest {
  private static final String LOADING = "data/local/ui/loadingscreen.dc6";
  private Application previousApp;
  private AssetManager previousAssets;
  private Fonts previousFonts;
  private Client previousClient;
  private Colors previousColors;
  private Palettes previousPalettes;
  private PaletteIndexedBatch previousBatch;
  private com.badlogic.gdx.utils.viewport.Viewport previousViewport;
  private AssetManager owner;
  private Fonts fonts;
  private Client client;
  private Animation animation;
  private MockedConstruction<Stage> stages;
  private MockedStatic<Animation> animations;
  private final List<GameEntryLoadingScreen> screens = new ArrayList<>();

  @BeforeEach void setup() {
    previousApp = Gdx.app;
    previousAssets = Riiablo.assets;
    previousFonts = Riiablo.fonts;
    previousClient = Riiablo.client;
    previousColors = Riiablo.colors;
    previousPalettes = Riiablo.palettes;
    previousBatch = Riiablo.batch;
    previousViewport = Riiablo.viewport;
    Gdx.app = mock(Application.class);
    Riiablo.assets = owner = mock(AssetManager.class);
    Riiablo.fonts = fonts = mock(Fonts.class);
    Riiablo.client = client = mock(Client.class);
    Riiablo.colors = new Colors();
    Riiablo.palettes = mock(Palettes.class);
    Riiablo.batch = mock(PaletteIndexedBatch.class);
    when(owner.contains(anyString())).thenReturn(true);
    when(owner.isLoaded(anyString())).thenReturn(true);
    DC6 dc6 = mock(DC6.class);
    when(owner.get(any(AssetDescriptor.class))).thenReturn(dc6);
    animation = mock(Animation.class);
    when(animation.getNumFramesPerDir()).thenReturn(10);
    stages = mockConstruction(Stage.class);
    animations = mockStatic(Animation.class);
    animations.when(() -> Animation.newAnimation(dc6)).thenReturn(animation);
  }

  @AfterEach void restore() {
    try {
      for (GameEntryLoadingScreen screen : screens) screen.dispose();
    } finally {
      animations.close();
      stages.close();
      Gdx.app = previousApp;
      Riiablo.assets = previousAssets;
      Riiablo.fonts = previousFonts;
      Riiablo.client = previousClient;
      Riiablo.colors = previousColors;
      Riiablo.palettes = previousPalettes;
      Riiablo.batch = previousBatch;
      Riiablo.viewport = previousViewport;
    }
  }

  @Test void unusedConstructionAndDisposalDoNotAcquireAnything() {
    GameEntryLoadingScreen screen = entry(() -> mock(Screen.class));
    screen.hide();
    screen.dispose();
    screen.dispose();
    screen.render(1);
    assertEquals(0, stages.constructed().size());
    verifyNoInteractions(owner, fonts, client);
    assertThrows(IllegalStateException.class, screen::show);
  }

  @Test void explicitHostOwnsStageDrawingAndNavigationAfterAllGlobalsAreReplaced() {
    com.badlogic.gdx.utils.viewport.Viewport viewport =
        mock(com.badlogic.gdx.utils.viewport.Viewport.class);
    PaletteIndexedBatch batch = Riiablo.batch;
    Palettes palettes = Riiablo.palettes;
    java.util.concurrent.atomic.AtomicInteger activations = new java.util.concurrent.atomic.AtomicInteger();
    LoadingScreenHost host = new LoadingScreenHost(viewport, batch, palettes,
        activations::incrementAndGet, () -> {}, client::clearAndSet);
    Screen game = mock(Screen.class);
    GameEntryLoadingScreen screen = new GameEntryLoadingScreen(() -> game, owner, fonts, host);
    screens.add(screen);
    AssetManager replacementAssets = mock(AssetManager.class);
    Fonts replacementFonts = mock(Fonts.class);
    Client replacementClient = mock(Client.class);
    PaletteIndexedBatch replacementBatch = mock(PaletteIndexedBatch.class);
    Riiablo.assets = replacementAssets;
    Riiablo.fonts = replacementFonts;
    Riiablo.client = replacementClient;
    Riiablo.batch = replacementBatch;
    Riiablo.palettes = null;
    screen.show();
    screen.render(0.04f);
    verify(batch).setPalette(palettes.loading);
    assertEquals(1, activations.get());
    when(fonts.finishQueueGameplayFonts()).thenReturn(true);
    when(owner.update()).thenReturn(true);
    screen.render(0.04f);
    verify(client).clearAndSet(game);
    screen.dispose();
    verifyNoInteractions(replacementAssets, replacementFonts, replacementClient, replacementBatch);
    verify(batch, never()).dispose();
  }

  @Test void repeatedShowHideDisposeBalanceOneTransientView() {
    GameEntryLoadingScreen screen = entry(() -> mock(Screen.class));
    screen.show();
    screen.show();
    screen.hide();
    screen.hide();
    screen.dispose();
    assertEquals(1, stages.constructed().size());
    verify(stages.constructed().get(0)).dispose();
    verify(owner).load(any(AssetDescriptor.class));
    verify(owner).unload(LOADING);
    verify(fonts).queueGameplayFonts();
    verify(Riiablo.batch, never()).dispose();
  }

  @Test void hiddenUnfinishedViewCanResumeButDisposedViewCannot() {
    GameEntryLoadingScreen screen = entry(() -> mock(Screen.class));
    screen.show();
    screen.hide();
    screen.show();
    screen.dispose();
    assertEquals(2, stages.constructed().size());
    for (Stage stage : stages.constructed()) verify(stage).dispose();
    verify(owner, times(2)).unload(LOADING);
    assertThrows(IllegalStateException.class, screen::show);
  }

  @Test void hiddenOrDisposedViewCannotEnterGameOrContinueProbingFonts() {
    AtomicInteger creates = new AtomicInteger();
    GameEntryLoadingScreen screen = entry(() -> { creates.incrementAndGet(); return mock(Screen.class); });
    ready();
    screen.render(1);
    screen.show();
    screen.hide();
    screen.render(1);
    screen.dispose();
    screen.render(1);
    assertEquals(0, creates.get());
    verify(fonts, never()).finishQueueGameplayFonts();
    verify(client, never()).clearAndSet(any());
  }

  @Test void fontProbePendingDrawsWithoutAdvancingManagerOrConstructingGame() {
    GameEntryLoadingScreen screen = entry(() -> { fail("not ready"); return null; });
    screen.show();
    screen.render(.5f);
    verify(stages.constructed().get(0)).act(.5f);
    verify(stages.constructed().get(0)).draw();
    verify(owner, never()).update();
    verify(fonts, never()).initializeGameplayFonts();
  }

  @Test void indexedCacheKeepsLoadingFrameBlackWhileAssetsArePending() {
    when(fonts.hasIndexedGameplayFontCache()).thenReturn(true);
    when(fonts.finishQueueGameplayFonts()).thenReturn(true);
    GameEntryLoadingScreen screen = entry(() -> { fail("assets pending"); return null; });
    screen.show();
    screen.render(.5f);
    verify(owner).update();
    verify(stages.constructed().get(0), never()).draw();
    verify(fonts, never()).initializeGameplayFonts();
  }

  @Test void successfulHandoffIsOnceOnlyEvenWhenClientReentersAndOnlyHides() {
    ready();
    Screen game = mock(Screen.class);
    AtomicInteger creates = new AtomicInteger();
    GameEntryLoadingScreen screen = entry(() -> { creates.incrementAndGet(); return game; });
    doAnswer(call -> {
      screen.render(1); // Client callbacks may re-enter before old-screen disposal.
      screen.hide(); // a pushed game loader can prevent Client.dispose(entry)
      return null;
    }).when(client).clearAndSet(game);
    screen.show();
    screen.render(1);
    screen.render(1);
    assertEquals(1, creates.get());
    verify(fonts).initializeGameplayFonts();
    verify(client).clearAndSet(game);
    verify(stages.constructed().get(0)).dispose();
    verify(owner).unload(LOADING);
    screen.dispose();
    verify(owner).unload(LOADING);
    assertThrows(IllegalStateException.class, screen::show);
    verify(game, never()).dispose();
  }

  @Test void originalAssetsFontsAndClientRemainTheHandoffHosts() {
    ready();
    Screen game = mock(Screen.class);
    GameEntryLoadingScreen screen = entry(() -> game);
    Riiablo.assets = mock(AssetManager.class);
    Riiablo.fonts = mock(Fonts.class);
    Riiablo.client = mock(Client.class);
    screen.show();
    screen.render(1);
    screen.dispose();
    verify(owner).unload(LOADING);
    verify(fonts).initializeGameplayFonts();
    verify(client).clearAndSet(game);
    verifyNoInteractions(Riiablo.assets, Riiablo.fonts, Riiablo.client);
  }

  @Test void queueFailureRollsBackStageAndLoadingReferenceAndStopsEntry() {
    RuntimeException failure = new RuntimeException("queue");
    doThrow(failure).when(fonts).queueGameplayFonts();
    GameEntryLoadingScreen screen = entry(() -> mock(Screen.class));
    assertSame(failure, assertThrows(RuntimeException.class, screen::show));
    verify(stages.constructed().get(0)).dispose();
    verify(owner).unload(LOADING);
    screen.render(1);
    assertThrows(IllegalStateException.class, screen::show);
    verifyNoInteractions(client);
  }

  @Test void loadSubmissionFailureDoesNotUnloadAnUnacquiredReference() {
    RuntimeException failure = new RuntimeException("load");
    doThrow(failure).when(owner).load(any(AssetDescriptor.class));
    GameEntryLoadingScreen screen = entry(() -> mock(Screen.class));
    assertSame(failure, assertThrows(RuntimeException.class, screen::show));
    verify(stages.constructed().get(0)).dispose();
    verify(owner, never()).unload(anyString());
  }

  @Test void fontProbeFailureRetiresViewWithoutConstructingGame() {
    RuntimeException failure = new RuntimeException("probe");
    when(fonts.finishQueueGameplayFonts()).thenThrow(failure);
    GameEntryLoadingScreen screen = entry(() -> { fail("probe failed"); return null; });
    screen.show();
    assertSame(failure, assertThrows(RuntimeException.class, () -> screen.render(1)));
    verify(owner).unload(LOADING);
    verify(stages.constructed().get(0)).dispose();
  }

  @Test void factoryFailureKeepsOriginalExceptionAndContinuesAllCleanup() {
    ready();
    RuntimeException failure = new RuntimeException("game constructor");
    RuntimeException stageFailure = new RuntimeException("stage");
    RuntimeException assetFailure = new RuntimeException("asset");
    GameEntryLoadingScreen screen = entry(() -> { throw failure; });
    screen.show();
    doThrow(stageFailure).when(stages.constructed().get(0)).dispose();
    doThrow(assetFailure).when(owner).unload(LOADING);
    assertSame(failure, assertThrows(RuntimeException.class, () -> screen.render(1)));
    assertArrayEquals(new Throwable[] {stageFailure}, failure.getSuppressed());
    assertArrayEquals(new Throwable[] {assetFailure}, stageFailure.getSuppressed());
    screen.dispose();
    screen.render(1);
    verify(owner).unload(LOADING);
    verifyNoInteractions(client);
  }

  @Test void animationProgressUsesOriginalManagerAndClampsToValidFrames() {
    GameEntryLoadingScreen screen = entry(() -> mock(Screen.class));
    screen.show();
    ArgumentCaptor<Actor> image = ArgumentCaptor.forClass(Actor.class);
    verify(stages.constructed().get(0)).addActor(image.capture());
    when(owner.getProgress()).thenReturn(-1f, 2f);
    image.getValue().act(0);
    image.getValue().act(0);
    verify(animation).setFrame(0);
    verify(animation).setFrame(9);
  }

  @Test void clientHandoffFailureDoesNotDestroyAPossiblyInstalledGameOrRetryEntry() {
    ready();
    Screen game = mock(Screen.class);
    RuntimeException failure = new RuntimeException("client handoff");
    GameEntryLoadingScreen screen = entry(() -> game);
    doAnswer(call -> { screen.hide(); throw failure; }).when(client).clearAndSet(game);
    screen.show();
    assertSame(failure, assertThrows(RuntimeException.class, () -> screen.render(1)));
    screen.render(1);
    screen.dispose();
    verify(client).clearAndSet(game);
    verify(owner).unload(LOADING);
    verify(game, never()).dispose();
    assertThrows(IllegalStateException.class, screen::show);
  }

  @Test void publicLocalAndNetworkFactoriesPreserveArgumentsAndBorrowSocket() {
    ready();
    CharData character = mock(CharData.class);
    Socket socket = mock(Socket.class);
    try (MockedConstruction<GameScreen> local = mockConstruction(GameScreen.class);
         MockedConstruction<NetworkedGameScreen> network = mockConstruction(NetworkedGameScreen.class,
             (screen, context) -> assertEquals(java.util.Arrays.asList(character, socket, true), context.arguments()))) {
      GameEntryLoadingScreen unusedNetwork = new GameEntryLoadingScreen(character, socket, true);
      unusedNetwork.dispose();
      GameEntryLoadingScreen localEntry = new GameEntryLoadingScreen(character);
      screens.add(localEntry);
      localEntry.show();
      localEntry.render(1);
      verify(client).clearAndSet(local.constructed().get(0));
      GameEntryLoadingScreen networkEntry = new GameEntryLoadingScreen(character, socket, true);
      screens.add(networkEntry);
      networkEntry.show();
      networkEntry.render(1);
      verify(client).clearAndSet(network.constructed().get(0));
      networkEntry.dispose();
      verifyNoInteractions(socket);
    }
  }

  private GameEntryLoadingScreen entry(Supplier<? extends Screen> factory) {
    GameEntryLoadingScreen screen = new GameEntryLoadingScreen(factory);
    screens.add(screen);
    return screen;
  }

  private void ready() {
    when(fonts.finishQueueGameplayFonts()).thenReturn(true);
    when(owner.update()).thenReturn(true);
  }
}
