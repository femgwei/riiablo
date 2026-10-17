package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Client;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real Client navigation/input, without MPQ/GL-heavy screen constructors. */
class ClientScreenTransitionTest {
  private Application oldApp;
  private Graphics oldGraphics;
  private Input oldInput;
  private Client client;
  private Client.InputProcessor input;
  private final InputAdapter hostInput = new InputAdapter();
  private final List<String> events = new ArrayList<>();
  private final List<Probe> probes = new ArrayList<>();

  @BeforeEach void setup() throws Exception {
    oldApp = Gdx.app;
    oldGraphics = Gdx.graphics;
    oldInput = Gdx.input;
    Gdx.app = mock(Application.class);
    Gdx.graphics = mock(Graphics.class);
    Gdx.input = mock(Input.class);
    when(Gdx.graphics.getWidth()).thenReturn(800);
    when(Gdx.graphics.getHeight()).thenReturn(600);
    client = new Client(new FileHandle("unused-home"));
    input = new Client.InputProcessor();
    input.addProcessor(hostInput);
    field("input").set(client, input);
  }

  @AfterEach void restore() {
    try {
      for (Probe probe : probes) probe.resetCallbacks();
      client.dispose();
    } finally {
      Gdx.app = oldApp;
      Gdx.graphics = oldGraphics;
      Gdx.input = oldInput;
    }
  }

  @Test void replaceRetiresBeforeShowAndKeepsOnlyCurrentInput() {
    Probe a = probe("a"), b = probe("b");
    client.setScreen(a);
    events.clear();
    client.setScreen(b);
    assertEquals(Arrays.asList("a.hide", "a.dispose", "b.show", "b.resize"), events);
    assertInput(b);
    assertSame(b, client.getScreen());
    assertEquals(800, b.width);
    assertEquals(600, b.height);
  }

  @Test void pushRetainsAndPopRetiresEvenWithAnOlderStackEntry() {
    Probe a = probe("a"), b = probe("b"), c = probe("c");
    client.setScreen(a);
    client.pushScreen(b);
    client.pushScreen(c);
    client.popScreen();
    assertEquals(1, c.disposals);
    assertEquals(0, a.disposals);
    assertEquals(0, b.disposals);
    assertInput(b);
    client.popScreen();
    assertEquals(1, b.disposals);
    assertInput(a);
    assertEquals(0, stack().size);
  }

  @Test void successiveActLoadsCreateFreshViewsAndRetainSessionDependenciesOnce() {
    com.riiablo.Colors previousColors = com.riiablo.Riiablo.colors;
    com.riiablo.Riiablo.colors = new com.riiablo.Colors();
    AssetManager assets = mock(AssetManager.class);
    when(assets.contains(anyString())).thenReturn(true);
    when(assets.isLoaded(anyString())).thenReturn(true);
    when(assets.update()).thenReturn(true);
    com.riiablo.codec.DC6 dc6 = mock(com.riiablo.codec.DC6.class);
    when(assets.get(any(com.badlogic.gdx.assets.AssetDescriptor.class))).thenReturn(dc6);
    com.riiablo.codec.Animation animation = mock(com.riiablo.codec.Animation.class);
    com.riiablo.map.Map map = mock(com.riiablo.map.Map.class);
    ScreenAssetScope session = new ScreenAssetScope(assets);
    Array<com.badlogic.gdx.assets.AssetDescriptor> dependencies = new Array<>();
    com.badlogic.gdx.assets.AssetDescriptor<com.badlogic.gdx.audio.Sound> dependency =
        new com.badlogic.gdx.assets.AssetDescriptor<>("session.wav", com.badlogic.gdx.audio.Sound.class);
    dependencies.add(dependency);
    LoadingScreenHost host = new LoadingScreenHost(null, null, null,
        () -> {}, client::popScreen, client::clearAndSet);
    try (org.mockito.MockedConstruction<com.badlogic.gdx.scenes.scene2d.Stage> stages =
            mockConstruction(com.badlogic.gdx.scenes.scene2d.Stage.class);
         org.mockito.MockedStatic<com.riiablo.codec.Animation> animations =
            mockStatic(com.riiablo.codec.Animation.class)) {
      animations.when(() -> com.riiablo.codec.Animation.newAnimation(dc6)).thenReturn(animation);
      Probe game = probe("game");
      client.setScreen(game);
      GameLoadingScreen loader = new GameLoadingScreen(map, dependencies, session, host);
      for (int act = 0; act < 3; act++) {
        GameLoadingScreen previous = loader;
        loader = loader.nextLoad(); // same ownership boundary used by GameScreen.setAct
        assertNotSame(previous, loader);
        loader.loadAct(act);
        client.pushScreen(loader);
        loader.render(0.04f); // real pop hides and permanently disposes this loader
        assertSame(game, client.getScreen());
        loader.render(0.04f); // late frames cannot regenerate or pop the game
        verify(map).setAct(act);
      }
      assertEquals(3, stages.constructed().size());
      for (com.badlogic.gdx.scenes.scene2d.Stage stage : stages.constructed()) verify(stage).dispose();
      verify(map, times(3)).finishLoading();
      verify(map, times(3)).generate();
      verify(assets).load(dependency);
      verify(assets, never()).unload(dependency.fileName);
      session.dispose();
      verify(assets).unload(dependency.fileName);
    } finally {
      session.dispose();
      com.riiablo.Riiablo.colors = previousColors;
    }
  }

  @Test void replaceWithAnOlderStackEntryStillDisposesPrevious() {
    Probe a = probe("a"), b = probe("b"), c = probe("c");
    client.setScreen(a);
    client.pushScreen(b);
    client.setScreen(c);
    assertEquals(1, b.disposals);
    assertEquals(1, stack().size);
    client.popScreen();
    assertSame(a, client.getScreen());
    assertEquals(1, c.disposals);
  }

  @Test void nestedLoaderPushDoesNotLeakEntryOrRegisterHiddenGameInput() {
    Probe entry = probe("entry"), game = probe("game"), loader = probe("loader");
    client.setScreen(entry);
    game.onShow = () -> client.pushScreen(loader);
    client.clearAndSet(game);
    assertEquals(1, entry.disposals);
    assertEquals(0, game.resizes);
    assertEquals(0, game.disposals);
    assertInput(loader);
    game.onShow = () -> {};
    client.popScreen();
    assertInput(game);
    assertEquals(1, game.resizes);
    assertEquals(1, loader.disposals);
  }

  @Test void nestedReplacementDuringShowDoesNotResizeOrRegisterSupersededScreen() {
    Probe a = probe("a"), b = probe("b"), c = probe("c");
    b.onShow = () -> client.setScreen(c);
    client.setScreen(a);
    client.setScreen(b);
    assertEquals(1, a.disposals);
    assertEquals(1, b.disposals);
    assertEquals(0, b.resizes);
    assertInput(c);
  }

  @Test void nestedReplacementDuringResizeDoesNotRegisterSupersededScreen() {
    Probe a = probe("a"), b = probe("b");
    a.onResize = () -> client.setScreen(b);
    client.setScreen(a);
    assertEquals(1, a.disposals);
    assertInput(b);
  }

  @Test void nestedPushAndPopBackToSameIdentityDoesNotRepeatOuterResize() {
    Probe game = probe("game"), loader = probe("loader");
    game.onShow = () -> {
      game.onShow = () -> {};
      client.pushScreen(loader);
      client.popScreen();
    };
    client.setScreen(game);
    assertEquals(2, game.shows);
    assertEquals(1, game.resizes);
    assertInput(game);
  }

  @Test void sameScreenSetAndPushAreNoOps() {
    Probe a = probe("a");
    client.setScreen(a);
    events.clear();
    client.setScreen(a);
    client.pushScreen(a);
    assertTrue(events.isEmpty());
    assertTrue(stack().isEmpty());
    assertInput(a);
  }

  @Test void stackedTargetRequiresExplicitPopOrClearWithoutMutation() {
    Probe a = probe("a"), b = probe("b");
    client.setScreen(a);
    client.pushScreen(b);
    events.clear();
    assertThrows(IllegalArgumentException.class, () -> client.setScreen(a));
    assertThrows(IllegalArgumentException.class, () -> client.pushScreen(a));
    assertTrue(events.isEmpty());
    assertSame(a, stack().peek());
    assertInput(b);
  }

  @Test void emptyPopFailsWithoutChangingActiveScreen() {
    Probe a = probe("a");
    client.setScreen(a);
    assertThrows(IllegalStateException.class, client::popScreen);
    assertInput(a);
    assertEquals(0, a.hides);
  }

  @Test void nullReplacementClearsActiveInputAndRetiresPrevious() {
    Probe a = probe("a");
    client.setScreen(a);
    client.setScreen(null);
    assertNull(client.getScreen());
    assertEquals(1, a.disposals);
    assertInput(null);
  }

  @Test void clearRetiresActiveThenStackTopDownAndDetachesBeforeCallbacks() {
    Probe a = probe("a"), b = probe("b"), c = probe("c"), d = probe("d");
    client.setScreen(a);
    client.pushScreen(b);
    client.pushScreen(c);
    c.onHide = () -> {
      assertNull(client.getScreen());
      assertTrue(stack().isEmpty());
      assertInput(null);
    };
    events.clear();
    client.clearAndSet(d);
    assertEquals(Arrays.asList("c.hide", "c.dispose", "b.dispose", "a.dispose",
        "d.show", "d.resize"), events);
    assertInput(d);
  }

  @Test void clearCanRetainAStackedTargetWithoutDisposingIt() {
    Probe a = probe("a"), b = probe("b"), c = probe("c");
    client.setScreen(a);
    client.pushScreen(b);
    client.pushScreen(c);
    client.clearAndSet(a);
    assertEquals(0, a.disposals);
    assertEquals(1, b.disposals);
    assertEquals(1, c.disposals);
    assertTrue(stack().isEmpty());
    assertInput(a);
  }

  @Test void clearKeepingActiveDoesNotHideShowResizeOrDuplicateInput() {
    Probe a = probe("a"), b = probe("b");
    client.setScreen(a);
    client.pushScreen(b);
    events.clear();
    client.clearAndSet(b);
    assertEquals(Arrays.asList("a.dispose"), events);
    assertInput(b);
  }

  @Test void clearKeepingActiveInsideShowStillAllowsOuterInputAndResize() {
    Probe a = probe("a"), b = probe("b");
    client.setScreen(a);
    b.onShow = () -> client.clearAndSet(b);
    client.pushScreen(b);
    assertEquals(1, a.disposals);
    assertEquals(1, b.resizes);
    assertInput(b);
  }

  @Test void failuresInHideAndMultipleDisposalsContinueThenPropagateInOrder() {
    Probe a = probe("a"), b = probe("b"), c = probe("c"), d = probe("d");
    client.setScreen(a);
    client.pushScreen(b);
    client.pushScreen(c);
    RuntimeException first = new IllegalStateException("hide");
    Error second = new AssertionError("dispose");
    RuntimeException third = new IllegalArgumentException("stack");
    c.onHide = () -> { throw first; };
    c.onDispose = () -> { throw second; };
    b.onDispose = () -> { throw third; };
    assertSame(first, assertThrows(RuntimeException.class, () -> client.clearAndSet(d)));
    assertArrayEquals(new Throwable[] {second, third}, first.getSuppressed());
    assertEquals(1, a.disposals);
    assertEquals(1, b.disposals);
    assertEquals(1, c.disposals);
    assertTrue(stack().isEmpty());
    assertInput(d);
    client.setScreen(null); // failed cleanup does not leave navigation locked
  }

  @Test void firstErrorIsNotSwallowedAndNextScreenStillInstalls() {
    Probe a = probe("a"), b = probe("b");
    client.setScreen(a);
    Error failure = new AssertionError("retirement");
    a.onDispose = () -> { throw failure; };
    assertSame(failure, assertThrows(AssertionError.class, () -> client.setScreen(b)));
    assertInput(b);
  }

  @Test void failedShowRetainsOwnershipWithoutResizeOrAutomaticInput() {
    Probe a = probe("a"), b = probe("b");
    RuntimeException failure = new IllegalStateException("show");
    a.onShow = () -> { throw failure; };
    assertSame(failure, assertThrows(RuntimeException.class, () -> client.setScreen(a)));
    assertSame(a, client.getScreen());
    assertEquals(0, a.resizes);
    assertInput(null);
    client.setScreen(b);
    assertEquals(1, a.disposals);
    assertInput(b);
  }

  @Test void failedResizeRetainsOwnershipWithoutAutomaticInput() {
    Probe a = probe("a");
    a.onResize = () -> { throw new IllegalStateException("resize"); };
    assertThrows(IllegalStateException.class, () -> client.setScreen(a));
    assertSame(a, client.getScreen());
    assertInput(null);
    client.setScreen(null);
    assertEquals(1, a.disposals);
  }

  @Test void outerFailureAfterNestedSwitchDoesNotDisposeInstalledReplacement() {
    Probe a = probe("a"), b = probe("b");
    a.onShow = () -> {
      client.setScreen(b);
      throw new IllegalStateException("outer show");
    };
    assertThrows(IllegalStateException.class, () -> client.setScreen(a));
    assertSame(b, client.getScreen());
    assertEquals(0, b.disposals);
    assertInput(b);
  }

  @Test void navigationFromHideOrDisposeIsRejectedButRemainingCleanupContinues() {
    Probe a = probe("a"), b = probe("b"), c = probe("c");
    client.setScreen(a);
    client.pushScreen(b);
    b.onHide = () -> client.pushScreen(c);
    b.onDispose = client::popScreen;
    RuntimeException failure = assertThrows(IllegalStateException.class, () -> client.clearAndSet(c));
    assertEquals(1, failure.getSuppressed().length);
    assertEquals(1, a.disposals);
    assertEquals(1, b.disposals);
    assertInput(c);
  }

  @Test void shutdownFromHideDisposesUnshownTargetBeforeSharedResources() throws Exception {
    Probe a = probe("a"), b = probe("b");
    AssetManager assets = sharedAssets();
    client.setScreen(a);
    a.onHide = () -> {
      client.dispose();
      verify(assets, never()).dispose();
      assertThrows(IllegalStateException.class, () -> client.setScreen(b));
    };
    events.clear();
    client.setScreen(b);
    assertEquals(Arrays.asList("a.hide", "a.dispose", "b.dispose", "host.dispose"), events);
    assertNull(client.getScreen());
    verify(assets).dispose();
    assertEquals(0, b.hides);
    assertEquals(0, b.shows);
    assertThrows(IllegalStateException.class, () -> client.setScreen(null));
  }

  @Test void shutdownFromShowWaitsForCallbackThenRetiresInstalledStack() throws Exception {
    Probe a = probe("a"), b = probe("b");
    AssetManager assets = sharedAssets();
    client.setScreen(a);
    b.onShow = () -> {
      client.dispose();
      verify(assets, never()).dispose();
      // A deferred exit is already terminal to other application callbacks.
      client.resize(1, 1);
      client.render();
      client.pause();
      client.resume();
      events.add("show.return");
    };
    events.clear();
    client.pushScreen(b);
    assertEquals(Arrays.asList("a.hide", "b.show", "show.return", "b.hide", "b.dispose",
        "a.dispose", "host.dispose"), events);
    assertEquals(0, b.resizes);
    assertNull(client.getScreen());
    assertTrue(input.getProcessors().isEmpty());
  }

  @Test void shutdownFromRetirementWithFailureStillFinishesScreensAndHost() throws Exception {
    Probe a = probe("a"), b = probe("b"), c = probe("c");
    AssetManager assets = sharedAssets();
    client.setScreen(a);
    client.pushScreen(b);
    RuntimeException failure = new IllegalStateException("retire");
    b.onDispose = () -> {
      client.dispose();
      verify(assets, never()).dispose();
      throw failure;
    };
    assertSame(failure, assertThrows(RuntimeException.class, () -> client.clearAndSet(c)));
    assertEquals(1, a.disposals);
    assertEquals(1, b.disposals);
    assertEquals(1, c.disposals);
    verify(assets).dispose();
    client.dispose();
    verify(assets, times(1)).dispose();
  }

  @Test void reusableLoaderCanBePushedAndPoppedMultipleTimes() {
    Probe game = probe("game"), loader = probe("loader");
    client.setScreen(game);
    for (int i = 0; i < 3; i++) {
      client.pushScreen(loader);
      assertInput(loader);
      client.popScreen();
      assertInput(game);
    }
    assertEquals(3, loader.disposals);
    assertEquals(0, game.disposals);
  }

  @Test void clearDeduplicatesByIdentityNotEquality() {
    Probe a = probe("a"), b = probe("b"), c = probe("c");
    a.equalToEverything = b.equalToEverything = true;
    client.setScreen(a);
    client.pushScreen(b);
    stack().add(a); // legacy duplicate is defensively retired once
    client.clearAndSet(c);
    assertEquals(1, a.disposals);
    assertEquals(1, b.disposals);
    assertInput(c);
  }

  private AssetManager sharedAssets() throws Exception {
    AssetManager assets = mock(AssetManager.class);
    doAnswer(call -> { events.add("host.dispose"); return null; }).when(assets).dispose();
    field("assets").set(client, assets);
    return assets;
  }

  private Field field(String name) throws Exception {
    Field field = Client.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  @SuppressWarnings("unchecked") private Array<Screen> stack() {
    try { return (Array<Screen>) field("screens").get(client); }
    catch (Exception e) { throw new AssertionError(e); }
  }

  private void assertInput(Probe active) {
    assertEquals(active == null ? 1 : 2, input.getProcessors().size);
    assertSame(hostInput, input.getProcessors().get(0));
    if (active != null) assertSame(active, input.getProcessors().get(1));
  }

  private Probe probe(String name) {
    Probe probe = new Probe(name);
    probes.add(probe);
    return probe;
  }

  private class Probe extends InputAdapter implements Screen {
    final String name;
    int shows, hides, disposals, resizes, width, height;
    boolean equalToEverything;
    Runnable onShow, onHide, onDispose, onResize;
    Probe(String name) { this.name = name; resetCallbacks(); }
    void resetCallbacks() { onShow = onHide = onDispose = onResize = () -> {}; }
    @Override public void show() { shows++; events.add(name + ".show"); onShow.run(); }
    @Override public void hide() { hides++; events.add(name + ".hide"); onHide.run(); }
    @Override public void dispose() { disposals++; events.add(name + ".dispose"); onDispose.run(); }
    @Override public void resize(int width, int height) {
      resizes++; this.width = width; this.height = height;
      events.add(name + ".resize"); onResize.run();
    }
    @Override public void render(float delta) {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public boolean equals(Object other) { return equalToEverything || this == other; }
    @Override public int hashCode() { return equalToEverything ? 1 : System.identityHashCode(this); }
  }
}
