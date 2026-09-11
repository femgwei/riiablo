package com.riiablo;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import java.util.Locale;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/** Starts the production Client with a 1x1 hidden context. */
public final class OffscreenRenderClient {
  private OffscreenRenderClient() {}

  public static void main(String[] args) throws Exception {
    Options options = new Options()
        .addOption(Option.builder("d").longOpt("d2").hasArg().required().build())
        .addOption(Option.builder("s").longOpt("saves").hasArg().required().build())
        .addOption(Option.builder("o").longOpt("output").hasArg().build())
        .addOption(Option.builder("m").longOpt("mode").hasArg().build())
        .addOption(Option.builder("l").longOpt("level").hasArg().build())
        .addOption(Option.builder().longOpt("d2-version").hasArg().build());
    CommandLine command = new DefaultParser().parse(options, args);
    String output = command.getOptionValue("output", "build/visual-tests");
    String mode = command.getOptionValue("mode", "visual").toLowerCase(Locale.ROOT);
    if (!"visual".equals(mode) && !"camp".equals(mode)) {
      throw new IllegalArgumentException("Unsupported offscreen mode: " + mode);
    }
    FileHandle saves = new FileHandle(command.getOptionValue("saves"));
    saves.mkdirs();
    System.setProperty("riiablo.offscreen-render", Boolean.toString("visual".equals(mode)));
    System.setProperty("riiablo.offscreen-camp", Boolean.toString("camp".equals(mode)));
    System.setProperty("riiablo.offscreen-output", output);
    System.setProperty("riiablo.offscreen-level",
        command.getOptionValue("level", "-1"));
    System.setProperty("riiablo.d2-version",
        command.getOptionValue("d2-version", "unspecified"));

    // LWJGL 2 executes the game on its own thread. Without this handler an
    // exception in Client.create()/render() terminates only that thread and
    // Gradle incorrectly reports BUILD SUCCESSFUL.
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      System.err.println("[OFFSCREEN_FATAL] thread=" + thread.getName());
      throwable.printStackTrace(System.err);
      System.err.flush();
      Runtime.getRuntime().halt(1);
    });

    if ("camp".equals(mode)) {
      Thread timeout = new Thread(() -> {
        try {
          Thread.sleep(120_000L);
          System.err.println("[OFFSCREEN_FATAL] Rogue Encampment smoke test timed out");
          System.err.flush();
          Runtime.getRuntime().halt(2);
        } catch (InterruptedException ignored) {
          Thread.currentThread().interrupt();
        }
      }, "offscreen-camp-timeout");
      timeout.setDaemon(true);
      timeout.start();
    }

    LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
    config.title = "riiablo-offscreen";
    config.width = 1;
    config.height = 1;
    config.fullscreen = false;
    config.resizable = false;
    // Gdx.app.exit() shuts down the render loop; leaving forceExit disabled
    // lets Gradle receive a normal zero exit status on Windows.
    config.forceExit = false;
    Client client = new Client(new FileHandle(command.getOptionValue("d")), saves, 480);
    new LwjglApplication(client, config);
    // Keep the tiny context outside the desktop work area while the FBO test
    // is running. The rendered output never depends on the default window.
    try {
      org.lwjgl.opengl.Display.setLocation(-32768, -32768);
    } catch (Throwable ignored) {
      // Some CI OpenGL drivers do not expose a movable native window.
    }
  }
}
