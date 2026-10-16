package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Client;
import com.riiablo.Colormaps;
import com.riiablo.Cvars;
import com.riiablo.Fonts;
import com.riiablo.GdxCvarManager;
import com.riiablo.GdxKeyMapper;
import com.riiablo.GdxLoggerManager;
import com.riiablo.Palettes;
import com.riiablo.Riiablo;
import com.riiablo.Textures;
import com.riiablo.audio.MusicController;
import com.riiablo.console.Console;
import com.riiablo.console.RenderedConsole;
import com.riiablo.cvar.Cvar;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.save.CharData;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Production Client shutdown without invoking MPQ/GL-heavy Client.create(). */
class ClientShutdownLifecycleTest {
  private Application previousApp;
  private Graphics previousGraphics;
  private Input previousInput;
  private PrintStream previousOut, previousErr;
  private final Map<Field, Object> aliases = new java.util.HashMap<>();
  private final List<Client> clients = new ArrayList<>();
  private Client client;
  private Client.InputProcessor input;
  private AssetManager assets;
  private PaletteIndexedBatch batch;
  private ShapeRenderer shapes;
  private ShaderProgram shader;
  private Palettes palettes;
  private Colormaps colormaps;
  private Textures textures;
  private Fonts fonts;
  private MusicController music;
  private RenderedConsole console;
  private PrintStream output;
  private GdxLoggerManager logs;
  private GdxCvarManager cvars;
  private GdxKeyMapper keys;

  @BeforeEach void setup() throws Exception {
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
        aliases.put(field, field.get(null));
      }
    }
    previousApp = Gdx.app;
    previousGraphics = Gdx.graphics;
    previousInput = Gdx.input;
    previousOut = System.out;
    previousErr = System.err;
    Gdx.app = mock(Application.class);
    Gdx.graphics = mock(Graphics.class);
    Gdx.input = mock(Input.class);
    client = newClient();
    input = mock(Client.InputProcessor.class);
    assets = mock(AssetManager.class);
    batch = mock(PaletteIndexedBatch.class);
    shapes = mock(ShapeRenderer.class);
    shader = mock(ShaderProgram.class);
    palettes = mock(Palettes.class);
    colormaps = mock(Colormaps.class);
    textures = mock(Textures.class);
    fonts = mock(Fonts.class);
    music = mock(MusicController.class);
    console = mock(RenderedConsole.class);
    output = mock(PrintStream.class);
    set(Console.class, console, "out", output);
    logs = mock(GdxLoggerManager.class);
    cvars = mock(GdxCvarManager.class);
    keys = mock(GdxKeyMapper.class);
    for (Object[] binding : new Object[][] {{"input", input}, {"assets", assets}, {"batch", batch},
        {"shapes", shapes}, {"shader", shader}, {"palettes", palettes}, {"colormaps", colormaps},
        {"textures", textures}, {"fonts", fonts}, {"music", music}, {"console", console},
        {"logs", logs}, {"cvars", cvars}, {"keys", keys}}) {
      set(Client.class, client, (String) binding[0], binding[1]);
      Riiablo.class.getField((String) binding[0]).set(null, binding[1]);
    }
    Riiablo.client = client;
    when(Gdx.input.getInputProcessor()).thenReturn(input);
  }

  @AfterEach void restore() throws Exception {
    try {
      for (Client owned : clients) owned.dispose();
    } finally {
      System.setOut(previousOut);
      System.setErr(previousErr);
      Gdx.app = previousApp;
      Gdx.graphics = previousGraphics;
      Gdx.input = previousInput;
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
    }
  }

  @Test void retiresActiveAndEntireStackTopDownBeforeSharedResources() throws Exception {
    Screen active = mock(Screen.class), top = mock(Screen.class), bottom = mock(Screen.class);
    active(active);
    stack().addAll(bottom, top);
    client.dispose();
    org.mockito.InOrder order = inOrder(active, top, bottom, music, logs, cvars, keys,
        console, shapes, batch, shader, palettes, colormaps, textures, assets, output);
    order.verify(active).hide();
    order.verify(active).dispose();
    order.verify(top).dispose();
    order.verify(bottom).dispose();
    order.verify(music).stop();
    order.verify(logs).saveAll();
    order.verify(cvars).saveAll();
    order.verify(keys).saveAll();
    order.verify(console).dispose();
    order.verify(shapes).dispose();
    order.verify(batch).dispose();
    order.verify(shader).dispose();
    order.verify(palettes).dispose();
    order.verify(colormaps).dispose();
    order.verify(textures).dispose();
    order.verify(assets).dispose();
    order.verify(output).flush();
    order.verify(output).close();
    verify(top, never()).hide();
    verify(bottom, never()).hide();
    assertNull(client.getScreen());
    assertEquals(0, stack().size);
    client.dispose();
    verify(active).dispose();
    verify(assets).dispose();
  }

  @Test void deduplicatesByIdentityNotEqualsIncludingActiveStackAliases() throws Exception {
    List<String> retired = new ArrayList<>();
    EqualScreen a = new EqualScreen("a", retired), b = new EqualScreen("b", retired);
    assertEquals(a, b);
    active(a);
    stack().addAll(a, b, b, a);
    client.dispose();
    assertEquals(Arrays.asList("a", "b"), retired);
  }

  @Test void hideFailureStillRetiresAllScreensAndResources() throws Exception {
    Screen active = mock(Screen.class), lower = mock(Screen.class);
    active(active);
    stack().add(lower);
    RuntimeException failure = new RuntimeException("hide");
    doThrow(failure).when(active).hide();
    assertSame(failure, assertThrows(RuntimeException.class, client::dispose));
    verify(active).dispose();
    verify(lower).dispose();
    verify(shapes).dispose();
    verify(assets).dispose();
    verify(output).close();
    assertNull(client.getScreen());
    assertEquals(0, stack().size);
    client.dispose();
    verify(active).dispose();
  }

  @Test void errorsAndLaterFailuresArePreservedWhileCleanupContinues() throws Exception {
    Screen active = mock(Screen.class), lower = mock(Screen.class);
    active(active);
    stack().add(lower);
    Error first = new AssertionError("screen");
    RuntimeException second = new RuntimeException("lower"), third = new RuntimeException("batch");
    doThrow(first).when(active).dispose();
    doThrow(second).when(lower).dispose();
    doThrow(third).when(batch).dispose();
    assertSame(first, assertThrows(AssertionError.class, client::dispose));
    assertArrayEquals(new Throwable[] {second, third}, first.getSuppressed());
    verify(shader).dispose();
    verify(assets).dispose();
    verify(output).close();
    assertNull(Riiablo.client);
    assertNull(Riiablo.assets);
  }

  @Test void callbacksSeeDetachedStackButLiveHostAndCannotReenterShutdown() throws Exception {
    Screen active = mock(Screen.class);
    active(active);
    stack().add(active);
    doAnswer(call -> {
      assertNull(client.getScreen());
      assertEquals(0, stack().size);
      assertSame(assets, Riiablo.assets);
      assertSame(batch, Riiablo.batch);
      client.dispose();
      return null;
    }).when(active).hide();
    doAnswer(call -> { client.dispose(); return null; }).when(active).dispose();
    client.dispose();
    verify(active).hide();
    verify(active).dispose();
    verify(assets).dispose();
  }

  @Test void shutdownRejectsScreenInstallationButStillCleansRemainingHost() throws Exception {
    Screen active = mock(Screen.class), replacement = mock(Screen.class);
    active(active);
    doAnswer(call -> { client.pushScreen(replacement); return null; }).when(active).dispose();
    assertThrows(IllegalStateException.class, client::dispose);
    assertNull(client.getScreen());
    assertEquals(0, stack().size);
    verifyNoInteractions(replacement);
    verify(assets).dispose();
  }

  @Test void disposedClientCannotResumeRenderOrMutateScreens() {
    client.dispose();
    Screen replacement = mock(Screen.class);
    assertThrows(IllegalStateException.class, () -> client.setScreen(replacement));
    assertThrows(IllegalStateException.class, () -> client.pushScreen(replacement));
    assertThrows(IllegalStateException.class, client::popScreen);
    assertThrows(IllegalStateException.class, () -> client.clearAndSet(replacement));
    assertThrows(IllegalStateException.class, client::create);
    clearInvocations(Gdx.graphics, batch, shapes, music);
    client.resume();
    client.pause();
    client.render();
    client.resize(640, 480);
    assertNull(Riiablo.client);
    verifyNoInteractions(replacement, Gdx.graphics, batch, shapes, music);
  }

  @Test void unusedClientWithNoInitializedResourcesCanBeDisposedTwice() {
    Client unused = newClient();
    unused.dispose();
    unused.dispose();
    assertNull(unused.getScreen());
    assertSame(client, Riiablo.client);
    assertSame(assets, Riiablo.assets);
  }

  @Test void detachesAllScreenInputAndOnlyItsOwnPlatformInput() throws Exception {
    InteractiveScreen active = mock(InteractiveScreen.class), stacked = mock(InteractiveScreen.class);
    active(active);
    stack().add(stacked);
    client.dispose();
    verify(input).removeProcessor(active);
    verify(input).removeProcessor(stacked);
    verify(input).dispose();
    verify(Gdx.input).setInputProcessor(null);
  }

  @Test void oldClientCannotClearReplacementAliasesOrPlatformInput() {
    Client replacement = newClient();
    AssetManager newAssets = mock(AssetManager.class);
    PaletteIndexedBatch newBatch = mock(PaletteIndexedBatch.class);
    Client.InputProcessor newInput = mock(Client.InputProcessor.class);
    Riiablo.client = replacement;
    Riiablo.assets = newAssets;
    Riiablo.batch = newBatch;
    Riiablo.input = newInput;
    when(Gdx.input.getInputProcessor()).thenReturn(newInput);
    client.dispose();
    assertSame(replacement, Riiablo.client);
    assertSame(newAssets, Riiablo.assets);
    assertSame(newBatch, Riiablo.batch);
    assertSame(newInput, Riiablo.input);
    verify(Gdx.input, never()).setInputProcessor(null);
    verifyNoInteractions(newAssets, newBatch, newInput);
  }

  @Test void removesClientCvarBindingsButKeepsUnrelatedObservers() throws Exception {
    Cvar<Float> gamma = Cvars.Client.Display.Gamma;
    float previous = gamma.get();
    Cvar.StateListener<Float> observer = mock(Cvar.StateListener.class);
    gamma.addStateListener(false, observer);
    try {
      invoke("bindCvars");
      assertEquals(4, ((Array<?>) get(Client.class, client, "cvarUnbinders")).size);
      client.addScreenBoundsListener((x, y, width, height) -> fail("stale bounds listener"));
      client.dispose();
      assertEquals(0, ((Array<?>) get(Client.class, client, "cvarUnbinders")).size);
      assertEquals(0, ((Array<?>) get(Client.class, client, "screenBoundsListeners")).size);
      clearInvocations(batch);
      float changed = previous == 1f ? 1.1f : 1f;
      boolean wasLoaded = gamma.isLoaded();
      gamma.set(changed);
      if (wasLoaded) verify(observer).onChanged(gamma, previous, changed);
      else verify(observer).onLoaded(gamma, changed);
      verifyNoInteractions(batch);
      assertTrue(gamma.containsStateListener(observer));
    } finally {
      gamma.removeStateListener(observer);
      gamma.set(previous);
    }
  }

  @Test void inputDisposalRemovesOnlyItsVibrationListenerAndClearsProcessors() throws Exception {
    Client.InputProcessor old = new Client.InputProcessor(), replacement = new Client.InputProcessor();
    boolean previous = Cvars.Client.Input.Vibration.get();
    try {
      Object oldListener = get(Client.InputProcessor.class, old, "vibrationListener");
      Object newListener = get(Client.InputProcessor.class, replacement, "vibrationListener");
      old.addProcessor(mock(com.badlogic.gdx.InputProcessor.class));
      old.dispose();
      old.dispose();
      assertEquals(0, old.getProcessors().size);
      assertFalse(Cvars.Client.Input.Vibration.containsStateListener(oldListener));
      assertTrue(Cvars.Client.Input.Vibration.containsStateListener(newListener));
      Cvars.Client.Input.Vibration.set(!previous);
      assertEquals(previous, get(Client.InputProcessor.class, old, "vibration"));
      assertEquals(!previous, get(Client.InputProcessor.class, replacement, "vibration"));
    } finally {
      old.dispose();
      replacement.dispose();
      Cvars.Client.Input.Vibration.set(previous);
    }
  }

  @Test void restoresBothOriginalStreamsWithoutClosingBorrowedStdout() throws Exception {
    TrackingOutput borrowed = new TrackingOutput();
    PrintStream originalOut = new PrintStream(borrowed), originalErr = new PrintStream(new ByteArrayOutputStream());
    System.setOut(originalOut);
    System.setErr(originalErr);
    invoke("createConsoleOutput");
    RenderedConsole realConsole = (RenderedConsole) get(Client.class, client, "console");
    set(RenderedConsole.class, realConsole, "cursorTexture", mock(Texture.class));
    set(RenderedConsole.class, realConsole, "modalBackground", mock(Texture.class));
    System.setOut(realConsole.out);
    System.setErr(realConsole.out);
    System.out.print("before/");
    client.dispose();
    assertSame(originalOut, System.out);
    assertSame(originalErr, System.err);
    assertFalse(borrowed.closed);
    originalOut.print("after");
    assertEquals("before/after", borrowed.toString("UTF-8"));
  }

  @Test void doesNotOverwriteStreamsRedirectedByAnotherOwner() throws Exception {
    set(Client.class, client, "originalOut", previousOut);
    set(Client.class, client, "originalErr", previousErr);
    PrintStream replacementOut = new PrintStream(new ByteArrayOutputStream());
    PrintStream replacementErr = new PrintStream(new ByteArrayOutputStream());
    System.setOut(replacementOut);
    System.setErr(replacementErr);
    client.dispose();
    assertSame(replacementOut, System.out);
    assertSame(replacementErr, System.err);
    assertFalse(replacementOut.checkError());
  }

  @Test void saveFailuresAreReportedAndDoNotSkipOtherSavesOrResources() {
    RuntimeException returned = new RuntimeException("cvar save");
    when(cvars.saveAll()).thenReturn(Arrays.asList(returned));
    client.dispose();
    verify(Gdx.app).error("Client", "cvar save", returned);
    verify(keys).saveAll();
    verify(assets).dispose();
  }

  @Test void throwingSaveAndAssetCleanupStillCloseOutputAndClearAliases() {
    RuntimeException first = new RuntimeException("logs"), second = new RuntimeException("assets");
    doThrow(first).when(logs).saveAll();
    doThrow(second).when(assets).dispose();
    assertSame(first, assertThrows(RuntimeException.class, client::dispose));
    assertArrayEquals(new Throwable[] {second}, first.getSuppressed());
    verify(cvars).saveAll();
    verify(keys).saveAll();
    verify(output).close();
    assertNull(Riiablo.assets);
    assertNull(Riiablo.fonts);
    assertNull(Riiablo.console);
  }

  @Test void closingWhileLoaderIsActiveDisposesStackedProductionGameScreen() throws Exception {
    Screen loader = mock(Screen.class);
    GameScreen game = mock(GameScreen.class, CALLS_REAL_METHODS);
    set(GameScreen.class, game, "visibleSubscriptions", new ScreenSubscriptionScope());
    set(GameScreen.class, game, "sessionSubscriptions", new ScreenSubscriptionScope());
    set(GameScreen.class, game, "sessionAssets", new ScreenAssetScope(assets));
    game.charData = mock(CharData.class);
    game.characterSavedForExit = true;
    game.engine = mock(World.class);
    game.map = mock(com.riiablo.map.Map.class);
    Riiablo.game = game;
    Riiablo.engine = game.engine;
    active(loader);
    stack().add(game);
    client.dispose();
    org.mockito.InOrder order = inOrder(loader, game.engine, game.map, batch, assets);
    order.verify(loader).hide();
    order.verify(loader).dispose();
    order.verify(game.engine).dispose();
    order.verify(game.map).dispose();
    order.verify(batch).dispose();
    order.verify(assets).dispose();
    assertNull(Riiablo.game);
    assertNull(Riiablo.engine);
  }

  private Client newClient() {
    Client fresh = new Client(new FileHandle("client-shutdown-test"));
    clients.add(fresh);
    return fresh;
  }

  private void active(Screen screen) throws Exception { set(Game.class, client, "screen", screen); }
  @SuppressWarnings("unchecked") private Array<Screen> stack() throws Exception {
    return (Array<Screen>) get(Client.class, client, "screens");
  }
  private void invoke(String name) throws Exception {
    Method method = Client.class.getDeclaredMethod(name);
    method.setAccessible(true);
    method.invoke(client);
  }
  private static Object get(Class<?> owner, Object target, String name) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(target);
  }
  private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
  private interface InteractiveScreen extends Screen, com.badlogic.gdx.InputProcessor {}
  private static final class EqualScreen extends ScreenAdapter {
    final String name;
    final List<String> retired;
    EqualScreen(String name, List<String> retired) { this.name = name; this.retired = retired; }
    @Override public boolean equals(Object other) { return other instanceof EqualScreen; }
    @Override public int hashCode() { return 0; }
    @Override public void dispose() { retired.add(name); }
  }
  private static final class TrackingOutput extends ByteArrayOutputStream {
    boolean closed;
    @Override public void close() { closed = true; }
  }
}
