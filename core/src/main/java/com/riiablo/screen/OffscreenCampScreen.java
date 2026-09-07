package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.riiablo.save.CharData;

/**
 * Production {@link GameScreen} smoke test driven by a 1x1 hidden LWJGL
 * window. Unlike {@link OffscreenRenderScreen}, this executes Act loading,
 * DRLG generation, Rogue Encampment entity creation and the real renderer.
 */
public final class OffscreenCampScreen extends GameScreen {
  private final String outputDirectory;
  private int renderedFrames;
  private boolean completed;

  public OffscreenCampScreen(CharData charData, String outputDirectory) {
    super(charData);
    this.outputDirectory = outputDirectory;
  }

  @Override
  public void render(float delta) {
    super.render(delta);
    if (completed || ++renderedFrames < 3) return;
    completed = true;

    com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    Pixmap pixel = Pixmap.createFromFrameBuffer(0, 0, 1, 1);
    try {
      PixmapIO.writePNG(output.child("rogue-encampment-smoke.png"), pixel);
    } finally {
      pixel.dispose();
    }
    String report = "mode=rogue-encampment\n"
        + "window=1x1\n"
        + "renderedFrames=" + renderedFrames + "\n"
        + "act=" + (map.getAct() + 1) + "\n"
        + "player=" + player + "\n"
        + "d2Version=" + System.getProperty("riiablo.d2-version", "unspecified") + "\n"
        + "result=PASS\n";
    output.child("rogue-encampment-manifest.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_CAMP] result=PASS act="
        + (map.getAct() + 1) + " player=" + player + " frames=" + renderedFrames);
    Gdx.app.exit();
  }
}
