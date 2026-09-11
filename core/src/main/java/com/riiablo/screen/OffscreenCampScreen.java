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
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.map.Map;
import com.riiablo.engine.client.AutomapRenderer;
import com.riiablo.engine.client.automap.AutomapLayer;
import com.riiablo.engine.client.automap.AutomapManager;

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
  private Map.Zone targetZone;
  private int targetNativeCells;
  private int targetRoomCount;
  private int targetNativeObjects;
  private int targetWarpCount;
  private int targetReverseWarpCount;
  private int targetWarpWalkable;
  private String targetWarpRooms = "";
  private String targetWarpTargets = "";

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
    validateTargetAutomap();
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
        + "targetNativeCells=" + targetNativeCells + "\n"
        + "targetRooms=" + targetRoomCount + "\n"
        + "targetNativeObjects=" + targetNativeObjects + "\n"
        + "targetWarpCount=" + targetWarpCount + "\n"
        + "targetReverseWarpCount=" + targetReverseWarpCount + "\n"
        + "targetWarpWalkable=" + targetWarpWalkable + "\n"
        + "targetWarpRooms=" + targetWarpRooms + "\n"
        + "targetWarpTargets=" + targetWarpTargets + "\n"
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
    this.targetZone = targetZone;
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

  private void validateTargetAutomap() {
    if (!targetApplied) return;
    if (targetZone == null || targetZone.width() <= 0 || targetZone.height() <= 0) {
      throw new IllegalStateException("Target Zone has invalid bounds: level=" + targetLevelId);
    }
    targetRoomCount = targetZone.getRoomsEx().size;
    targetNativeObjects = targetZone.getNativeObjects().size;

    StringBuilder warpTargets = new StringBuilder();
    StringBuilder warpRooms = new StringBuilder();
    boolean hasExpectedCaveReturn = false;
    com.artemis.ComponentMapper<Warp> warpMapper = engine.getMapper(Warp.class);
    com.artemis.ComponentMapper<Position> positionMapper = engine.getMapper(Position.class);
    for (int i = 0; i < targetZone.getEntities().size; i++) {
      int warpEntity = targetZone.getEntities().get(i);
      Warp warp = warpMapper.get(warpEntity);
      if (warp == null) continue;
      if (warp.dstLevel == null || map.findZone(warp.dstLevel) == null) {
        throw new IllegalStateException("Warp target is unresolved: source=" + targetLevelId);
      }
      if (targetWarpCount++ > 0) warpTargets.append(',');
      warpTargets.append(warp.dstLevel.Id);

      // Native warp objects may be placed on a blocked doorway.  Validate the
      // source coordinate and use the same expanding free-coordinate search
      // as D2Common to prove that a player can actually stand by the entry.
      Position warpPosition = positionMapper.get(warpEntity);
      if (warpPosition == null || !targetZone.contains(Math.round(warpPosition.position.x),
          Math.round(warpPosition.position.y))) {
        throw new IllegalStateException("Warp position is outside source zone: level="
            + targetLevelId + " index=" + warp.index);
      }
      Map.RoomEx warpRoom = targetZone.findRoomEx(warpPosition.position.x,
          warpPosition.position.y);
      if (warpRooms.length() > 0) warpRooms.append(',');
      warpRooms.append(warp.index).append(':').append(warpRoom == null ? -1 : warpRoom.id);
      Vector2 landing = new Vector2();
      if (targetZone.findFreeCoordinates(warpPosition.position, 1, 8, true, landing)) {
        targetWarpWalkable++;
      } else {
        throw new IllegalStateException("No walkable coordinates near warp: level="
            + targetLevelId + " index=" + warp.index + " position=" + warpPosition.position);
      }

      // A bad Vis/Warp import often creates a one-way dead end.  Verify that
      // the destination zone has an edge back to this source level.
      Map.Zone destinationZone = map.findZone(warp.dstLevel);
      boolean reverseFound = false;
      for (int j = 0; j < destinationZone.getEntities().size; j++) {
        Warp reverse = warpMapper.get(destinationZone.getEntities().get(j));
        if (reverse != null && reverse.dstLevel != null
            && reverse.dstLevel.Id == targetLevelId) {
          reverseFound = true;
          break;
        }
      }
      if (reverseFound) {
        targetReverseWarpCount++;
      } else if (targetLevelId == 8 || targetLevelId == 10) {
        throw new IllegalStateException("Warp has no reverse edge: source=" + targetLevelId
            + " destination=" + warp.dstLevel.Id + " index=" + warp.index);
      }
      if (targetLevelId == 8 && warp.dstLevel.Id == 2) hasExpectedCaveReturn = true;
      if (targetLevelId == 10 && (warp.dstLevel.Id == 5 || warp.dstLevel.Id == 6)) {
        hasExpectedCaveReturn = true;
      }
    }
    targetWarpTargets = warpTargets.toString();
    targetWarpRooms = warpRooms.toString();
    if (targetReverseWarpCount != targetWarpCount) {
      throw new IllegalStateException("Warp reverse edge count mismatch: level=" + targetLevelId
          + " forward=" + targetWarpCount + " reverse=" + targetReverseWarpCount);
    }
    if (targetWarpWalkable != targetWarpCount) {
      throw new IllegalStateException("Warp walkability mismatch: level=" + targetLevelId
          + " total=" + targetWarpCount + " walkable=" + targetWarpWalkable);
    }
    if ((targetLevelId == 8 || targetLevelId == 10) && !hasExpectedCaveReturn) {
      throw new IllegalStateException("Cave warp target does not match Act 1 topology: level="
          + targetLevelId + " targets=" + targetWarpTargets);
    }

    AutomapRenderer automap = engine.getSystem(AutomapRenderer.class);
    if (automap == null || automap.getAutomapManager() == null) {
      throw new IllegalStateException("AutomapRenderer unavailable for target level=" + targetLevelId);
    }
    Position position = engine.getMapper(Position.class).get(player);
    automap.updatePlayerPosition(targetLevelId, Math.round(position.position.x),
        Math.round(position.position.y));
    AutomapManager manager = automap.getAutomapManager();
    AutomapLayer layer = manager.getLayer(targetLevelId);
    if (layer == null) {
      throw new IllegalStateException("Automap layer missing for target level=" + targetLevelId);
    }
    targetNativeCells = layer.floors.size + layer.walls.size + layer.objects.size + layer.extras.size;
    if (targetNativeCells == 0) {
      throw new IllegalStateException("Automap has no native cells for target level=" + targetLevelId);
    }
    // Underground sub-levels must expose at least one exported entrance/object
    // record; otherwise a successful Zone switch would hide a broken cave link.
    if ((targetLevelId == 8 || targetLevelId == 10) && targetNativeObjects == 0) {
      throw new IllegalStateException("Cave target has no native entrance objects: level=" + targetLevelId);
    }
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_AUTOMAP] level=" + targetLevelId
        + " cells=" + targetNativeCells + " rooms=" + targetRoomCount
        + " nativeObjects=" + targetNativeObjects);
  }
}
