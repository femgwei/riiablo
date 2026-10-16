package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.net.NetJavaSocketImpl;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.SnapshotArray;
import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real production disposal; only the heavy GameScreen constructor is bypassed. */
class NetworkedGameScreenLifecycleTest {
  private Application previousApp;
  private AssetManager previousAssets;
  private World previousEngine;
  private GameScreen previousGame;
  private AssetManager assets;

  @BeforeEach void setup() {
    previousApp = Gdx.app;
    previousAssets = Riiablo.assets;
    previousEngine = Riiablo.engine;
    previousGame = Riiablo.game;
    Gdx.app = mock(Application.class);
    Riiablo.assets = assets = mock(AssetManager.class);
    when(assets.contains(anyString())).thenReturn(true);
    when(assets.isLoaded(anyString())).thenReturn(true);
  }

  @AfterEach void restore() {
    Gdx.app = previousApp;
    Riiablo.assets = previousAssets;
    Riiablo.engine = previousEngine;
    Riiablo.game = previousGame;
  }

  @Test void remoteExitDisposesWorldThenSocketExactlyOnce() throws Exception {
    Socket socket = mock(Socket.class);
    NetworkedGameScreen screen = screen(socket, null);
    Riiablo.game = screen;
    Riiablo.engine = screen.engine;
    screen.dispose();
    screen.dispose();
    org.mockito.InOrder order = inOrder(screen.engine, screen.stage, screen.map, assets, socket);
    order.verify(screen.engine).dispose();
    order.verify(screen.stage).dispose();
    order.verify(screen.map).dispose();
    order.verify(assets).unload("network-session.wav");
    order.verify(socket).dispose();
    verify(screen.engine).dispose();
    verify(socket).dispose();
    assertNull(screen.socket);
    assertNull(Riiablo.game);
    assertNull(Riiablo.engine);
    assertThrows(IllegalStateException.class, screen::show);
  }

  @Test void hidingForLoadingDoesNotCloseConnectionOrStopHost() throws Exception {
    Socket socket = mock(Socket.class);
    Runnable stop = mock(Runnable.class);
    NetworkedGameScreen screen = screen(socket, stop);
    screen.hide();
    screen.hide();
    verifyNoInteractions(socket, stop);
    screen.dispose();
    screen.hide();
    verify(socket).dispose();
    verify(stop).run();
  }

  @Test void worldFailureStillClosesSocketAndStopsHostWithoutRetry() throws Exception {
    Socket socket = mock(Socket.class);
    Runnable stop = mock(Runnable.class);
    NetworkedGameScreen screen = screen(socket, stop);
    RuntimeException failure = new RuntimeException("world");
    doThrow(failure).when(screen.engine).dispose();
    assertSame(failure, assertThrows(RuntimeException.class, screen::dispose));
    verify(screen.stage).dispose();
    verify(screen.map).dispose();
    verify(socket).dispose();
    verify(stop).run();
    screen.dispose();
    verify(socket).dispose();
    verify(stop).run();
  }

  @Test void multipleFailuresKeepOriginalAndAttemptAllNetworkCleanup() throws Exception {
    Socket socket = mock(Socket.class);
    Runnable stop = mock(Runnable.class);
    NetworkedGameScreen screen = screen(socket, stop);
    Error worldFailure = new AssertionError("world");
    RuntimeException socketFailure = new RuntimeException("socket");
    RuntimeException hostFailure = new RuntimeException("host");
    doThrow(worldFailure).when(screen.engine).dispose();
    doThrow(socketFailure).when(socket).dispose();
    doThrow(hostFailure).when(stop).run();
    assertSame(worldFailure, assertThrows(AssertionError.class, screen::dispose));
    assertArrayEquals(new Throwable[] {socketFailure, hostFailure}, worldFailure.getSuppressed());
    assertNull(screen.socket);
    screen.dispose();
    verify(socket).dispose();
    verify(stop).run();
  }

  @Test void socketFailureDoesNotSkipHostAndOldScreenPreservesNewAliases() throws Exception {
    Socket socket = mock(Socket.class);
    Runnable stop = mock(Runnable.class);
    NetworkedGameScreen screen = screen(socket, stop);
    GameScreen replacement = mock(GameScreen.class);
    World replacementWorld = mock(World.class);
    Riiablo.game = replacement;
    Riiablo.engine = replacementWorld;
    RuntimeException failure = new RuntimeException("socket");
    doThrow(failure).when(socket).dispose();
    assertSame(failure, assertThrows(RuntimeException.class, screen::dispose));
    verify(stop).run();
    assertSame(replacement, Riiablo.game);
    assertSame(replacementWorld, Riiablo.engine);
    verifyNoInteractions(replacement, replacementWorld);
  }

  @Test void cleanupCallbacksCannotReenterDisposal() throws Exception {
    Socket socket = mock(Socket.class);
    Runnable stop = mock(Runnable.class);
    NetworkedGameScreen screen = screen(socket, stop);
    doAnswer(call -> { screen.dispose(); return null; }).when(screen.engine).dispose();
    doAnswer(call -> { screen.dispose(); return null; }).when(socket).dispose();
    screen.dispose();
    verify(screen.engine).dispose();
    verify(socket).dispose();
    verify(stop).run();
  }

  @Test void alreadyRetiredBaseStillReleasesNetworkOwnershipOnce() throws Exception {
    Socket socket = mock(Socket.class);
    Runnable stop = mock(Runnable.class);
    NetworkedGameScreen screen = screen(socket, stop);
    set(GameScreen.class, screen, "disposed", true);
    screen.dispose();
    screen.dispose();
    verify(screen.engine, never()).dispose();
    verify(socket).dispose();
    verify(stop).run();
  }

  @Test void absentSocketDoesNotPreventHostCleanup() throws Exception {
    Runnable stop = mock(Runnable.class);
    NetworkedGameScreen screen = screen(null, stop);
    screen.dispose();
    screen.dispose();
    verify(stop).run();
  }

  @Test void realJvmAndLibgdxSocketRetireOverThreeCyclesWithoutStaleHostStop() throws Exception {
    assertNull(hostProcess(), "must not replace an existing hosted process");
    Runnable staleStop = null;
    for (int cycle = 0; cycle < 3; cycle++) {
      String executable = new File(System.getProperty("java.home"),
          "bin/" + (System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java")).getPath();
      String classpath = new File(SocketProcess.class.getProtectionDomain().getCodeSource()
          .getLocation().toURI()).getPath();
      Process child = new ProcessBuilder(executable, "-cp", classpath,
          SocketProcess.class.getName()).redirectErrorStream(true).start();
      Socket socket = null;
      ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "hosted-process-test-reader");
        thread.setDaemon(true);
        return thread;
      });
      try {
        BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream()));
        java.util.concurrent.Callable<String> readPort = output::readLine;
        int port = Integer.parseInt(reader.submit(readPort).get(5, TimeUnit.SECONDS));
        installHost(child);
        SocketHints hints = new SocketHints();
        hints.connectTimeout = 1000;
        hints.socketTimeout = 1000;
        socket = new NetJavaSocketImpl(Net.Protocol.TCP, "127.0.0.1", port, hints);
        java.io.InputStream input = socket.getInputStream();
        assertEquals(42, input.read(), "actual child/socket handshake");
        Runnable stop = HostedD2GS.stopAction();
        if (staleStop != null) staleStop.run();
        assertTrue(child.isAlive(), "old screen must not stop replacement process");
        NetworkedGameScreen screen = screen(socket, stop);
        screen.dispose();
        screen.dispose();
        assertFalse(child.isAlive(), "stop confirms process exit before returning");
        assertNull(hostProcess());
        assertThrows(java.io.IOException.class, input::read, "client stream is closed");
        awaitPortClosed(port);
        staleStop = stop;
      } finally {
        try {
          if (socket != null) socket.dispose();
        } finally {
          child.destroyForcibly();
          assertTrue(child.waitFor(5, TimeUnit.SECONDS), "fixture must not leave a child behind");
          installHost(null);
          reader.shutdownNow();
        }
      }
    }
  }

  private static void awaitPortClosed(int port) throws Exception {
    // Windows may signal process exit before TCP teardown has become visible.
    // Require connection refusal within a bounded interval, not a single racy
    // immediate probe or an unbounded retry. This is not a D2GS startup retry.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
    do {
      try (java.net.Socket probe = new java.net.Socket()) {
        probe.connect(new InetSocketAddress("127.0.0.1", port), 100);
      } catch (java.net.ConnectException closed) {
        return;
      }
      Thread.sleep(10);
    } while (System.nanoTime() < deadline);
    fail("terminated child's port still accepts connections after 2 seconds: " + port);
  }

  /** Child fixture only: real TCP/process lifetime, not the D2GS protocol or launcher. */
  public static final class SocketProcess {
    public static void main(String[] args) throws Exception {
      try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
        System.out.println(server.getLocalPort());
        System.out.flush();
        try (java.net.Socket client = server.accept()) {
          client.getOutputStream().write(42);
          client.getOutputStream().flush();
          while (client.getInputStream().read() != -1) {}
        }
        // Socket closure alone must not end the child; HostedD2GS must stop it.
        Thread.sleep(60000);
      }
    }
  }

  private NetworkedGameScreen screen(Socket socket, Runnable stop) throws Exception {
    NetworkedGameScreen screen = mock(NetworkedGameScreen.class, CALLS_REAL_METHODS);
    set(GameScreen.class, screen, "visibleSubscriptions", new ScreenSubscriptionScope());
    set(GameScreen.class, screen, "sessionSubscriptions", new ScreenSubscriptionScope());
    ScreenAssetScope scope = new ScreenAssetScope(assets);
    scope.load(new AssetDescriptor<>("network-session.wav", Sound.class));
    set(GameScreen.class, screen, "sessionAssets", scope);
    set(NetworkedGameScreen.class, screen, "stopHostedServer", stop);
    screen.socket = socket;
    screen.charData = mock(CharData.class);
    screen.characterSavedForExit = true;
    screen.engine = mock(World.class);
    screen.map = mock(com.riiablo.map.Map.class);
    screen.stage = mock(Stage.class);
    screen.scaledStage = mock(Stage.class);
    when(screen.stage.getActors()).thenReturn(new SnapshotArray<>(Actor.class));
    when(screen.scaledStage.getActors()).thenReturn(new SnapshotArray<>(Actor.class));
    return screen;
  }

  private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  static Process hostProcess() throws Exception {
    Field field = HostedD2GS.class.getDeclaredField("process");
    field.setAccessible(true);
    return (Process) field.get(null);
  }

  static void installHost(Process process) throws Exception {
    set(HostedD2GS.class, null, "process", process);
  }
}
