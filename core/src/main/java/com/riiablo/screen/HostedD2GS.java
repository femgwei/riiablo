package com.riiablo.screen;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import com.riiablo.Riiablo;
import com.riiablo.util.Disposal;

/** Lifecycle owner for the local D2GS process started by TCP/IP Host Game. */
final class HostedD2GS {
  private static final long STOP_WAIT_MILLIS = 1000;
  private static Process process;
  private static boolean shutdownHookInstalled;

  private HostedD2GS() {}

  static synchronized void start() throws IOException {
    if (process != null && process.isAlive()) return;
    String executable = new File(System.getProperty("java.home"),
        "bin" + File.separator + (isWindows() ? "java.exe" : "java")).getPath();
    ProcessBuilder builder = new ProcessBuilder(
        executable,
        "-cp", System.getProperty("java.class.path"),
        "com.riiablo.server.d2gs.D2GS",
        "-home", Riiablo.home.path(),
        "-seed", Integer.toString(Riiablo.gameSeed),
        "-diff", "0");
    builder.inheritIO();
    process = builder.start();
    if (!shutdownHookInstalled) {
      Runtime.getRuntime().addShutdownHook(new Thread(HostedD2GS::stop, "d2gs-shutdown"));
      shutdownHookInstalled = true;
    }
  }

  static synchronized void stop() {
    stop(process);
  }

  /** A stale screen must not stop a replacement host started later. */
  static synchronized Runnable stopAction() {
    Process ownedProcess = process;
    return () -> stop(ownedProcess);
  }

  private static synchronized void stop(Process ownedProcess) {
    if (ownedProcess == null || process != ownedProcess) return;
    boolean[] interrupted = {false};
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> { if (ownedProcess.isAlive()) ownedProcess.destroy(); });
      cleanup.run(() -> awaitExit(ownedProcess, interrupted));
      cleanup.run(() -> { if (ownedProcess.isAlive()) ownedProcess.destroyForcibly(); });
      cleanup.run(() -> awaitExit(ownedProcess, interrupted));
      cleanup.run(() -> {
        if (ownedProcess.isAlive()) {
          // Keep the handle for a later stop attempt; never report a live
          // process as retired or silently permit a duplicate host.
          throw new IllegalStateException("Hosted D2GS did not exit after termination");
        }
        process = null;
      });
    } finally {
      if (interrupted[0]) Thread.currentThread().interrupt();
    }
  }

  private static void awaitExit(Process ownedProcess, boolean[] interrupted) {
    if (!ownedProcess.isAlive()) return;
    try {
      ownedProcess.waitFor(STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException interruption) {
      // Still attempt forced termination and confirmation before restoring
      // cancellation to the caller. Each wait is bounded, not an endless join.
      interrupted[0] = true;
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}
