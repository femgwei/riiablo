package com.riiablo;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/** Starts the production Client with a 1x1 offscreen context and FBO visual tests. */
public final class OffscreenRenderClient {
  private OffscreenRenderClient() {}

  public static void main(String[] args) throws Exception {
    Options options = new Options()
        .addOption(Option.builder("d").longOpt("d2").hasArg().required().build())
        .addOption(Option.builder("s").longOpt("saves").hasArg().required().build())
        .addOption(Option.builder("o").longOpt("output").hasArg().build());
    CommandLine command = new DefaultParser().parse(options, args);
    String output = command.getOptionValue("output", "build/visual-tests");
    System.setProperty("riiablo.offscreen-render", "true");
    System.setProperty("riiablo.offscreen-output", output);

    LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
    config.title = "riiablo-offscreen";
    config.width = 1;
    config.height = 1;
    config.fullscreen = false;
    config.resizable = false;
    // Gdx.app.exit() shuts down the render loop; leaving forceExit disabled
    // lets Gradle receive a normal zero exit status on Windows.
    config.forceExit = false;
    Client client = new Client(new FileHandle(command.getOptionValue("d")),
        new FileHandle(command.getOptionValue("s")), 480);
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
