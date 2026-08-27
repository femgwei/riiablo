package com.riiablo.screen;

import java.io.File;
import java.io.IOException;
import com.riiablo.Riiablo;

/** Lifecycle owner for the local D2GS process started by TCP/IP Host Game. */
final class HostedD2GS {
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
    if (process == null) return;
    if (process.isAlive()) process.destroy();
    process = null;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }
}
