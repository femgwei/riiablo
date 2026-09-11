package com.riiablo.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.riiablo.save.CharData;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.map.Map;

/**
 * Production {@link GameScreen} smoke test driven by a 1x1 hidden LWJGL
 * window. Unlike {@link OffscreenRenderScreen}, this executes Act loading,
 * DRLG generation, Rogue Encampment entity creation and the real renderer.
 */
public final class OffscreenCampScreen extends GameScreen {
  private final String outputDirectory;
  private final int targetLevelId;
  private int renderedFrames;
  private boolean completed;
  private boolean targetApplied;

  public OffscreenCampScreen(CharData charData, String outputDirectory) {
    this(charData, outputDirectory, -1);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId) {
    super(charData);
    this.outputDirectory = outputDirectory;
    this.targetLevelId = targetLevelId;
  }

  @Override
  public void render(float delta) {
    super.render(delta);
    if (completed) return;
    renderedFrames++;
    if (targetLevelId >= 0 && !targetApplied && renderedFrames >= 3) {
      applyTargetLevel();
      return;
    }
    if (renderedFrames < (targetApplied ? 6 : 3)) return;
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
        + "targetLevel=" + targetLevelId + "\n"
        + "player=" + player + "\n"
        + "d2Version=" + System.getProperty("riiablo.d2-version", "unspecified") + "\n"
        + "result=PASS\n";
    output.child("rogue-encampment-manifest.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_CAMP] result=PASS act="
        + (map.getAct() + 1) + " player=" + player + " frames=" + renderedFrames);
    Gdx.app.exit();
  }

  private void applyTargetLevel() {
    if (targetLevelId < 0) return;
    Levels.Entry target = Riiablo.files.Levels.get(targetLevelId);
    if (target == null) throw new IllegalStateException("Unknown offscreen level id=" + targetLevelId);
    if (target.Act != map.getAct()) {
      throw new IllegalStateException("Offscreen level must be Act 1: id=" + targetLevelId
          + " act=" + (target.Act + 1));
    }
    Map.Zone targetZone = map.findZone(target);
    if (targetZone == null) {
      throw new IllegalStateException("Target level zone was not generated: " + target.LevelName
          + "(" + targetLevelId + ")");
    }
    Vector2 destination = new Vector2(targetZone.x() + targetZone.width() / 2f,
        targetZone.y() + targetZone.height() / 2f);
    Position position = engine.getMapper(Position.class).get(player);
    position.position.set(destination);
    Box2DBody body = engine.getMapper(Box2DBody.class).get(player);
    if (body != null && body.body != null) body.body.setTransform(destination, 0);
    engine.getMapper(MapWrapper.class).get(player).set(map, targetZone);
    engine.getSystem(net.mostlyoriginal.api.event.common.EventSystem.class)
        .dispatch(ZoneChangeEvent.obtain(player, targetZone));
    renderer.updatePosition(true);
    targetApplied = true;
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_LEVEL] target=" + target.LevelName
        + "(" + targetLevelId + ") position=" + destination);
  }
}
