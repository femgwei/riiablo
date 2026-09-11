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
  private final boolean validateWarpGraph;
  private final boolean validateContinuity;
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
  private int graphZoneCount;
  private int graphWarpCount;
  private int graphReverseWarpCount;
  private int graphWalkableCount;
  private int graphUnresolvedCount;
  private int graphMissingReverseCount;
  private String graphEdges = "";
  private int continuityZones;
  private int continuityRoomZones;
  private int continuityComponents;
  private int continuityInvalidAdjacency;
  private int continuityNoWalkableZones;
  private int continuityWarpRoomMissing;
  private int continuityWarpOutsideMain;
  private long continuitySampledCells;
  private long continuityWalkableCells;

  public OffscreenCampScreen(CharData charData, String outputDirectory) {
    this(charData, outputDirectory, -1, false);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId) {
    this(charData, outputDirectory, targetLevelId, false, false);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId,
      boolean validateWarpGraph) {
    this(charData, outputDirectory, targetLevelId, validateWarpGraph, false);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId,
      boolean validateWarpGraph, boolean validateContinuity) {
    super(charData);
    this.outputDirectory = outputDirectory;
    this.targetLevelId = targetLevelId;
    this.validateWarpGraph = validateWarpGraph;
    this.validateContinuity = validateContinuity;
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
    if (validateWarpGraph) validateAct1WarpGraph();
    if (validateContinuity) validateAct1Continuity();
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
        + "warpGraphZones=" + graphZoneCount + "\n"
        + "warpGraphWarps=" + graphWarpCount + "\n"
        + "warpGraphReverseWarps=" + graphReverseWarpCount + "\n"
        + "warpGraphWalkable=" + graphWalkableCount + "\n"
        + "warpGraphUnresolved=" + graphUnresolvedCount + "\n"
        + "warpGraphMissingReverse=" + graphMissingReverseCount + "\n"
        + "continuityZones=" + continuityZones + "\n"
        + "continuityRoomZones=" + continuityRoomZones + "\n"
        + "continuityComponents=" + continuityComponents + "\n"
        + "continuityInvalidAdjacency=" + continuityInvalidAdjacency + "\n"
        + "continuityNoWalkableZones=" + continuityNoWalkableZones + "\n"
        + "continuityWarpRoomMissing=" + continuityWarpRoomMissing + "\n"
        + "continuityWarpOutsideMain=" + continuityWarpOutsideMain + "\n"
        + "continuitySampledCells=" + continuitySampledCells + "\n"
        + "continuityWalkableCells=" + continuityWalkableCells + "\n"
        + "player=" + player + "\n"
        + "d2Version=" + System.getProperty("riiablo.d2-version", "unspecified") + "\n"
        + "result=PASS\n";
    output.child("rogue-encampment-manifest.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_CAMP] result=PASS act="
        + (map.getAct() + 1) + " player=" + player + " frames=" + renderedFrames);
    Gdx.app.exit();
  }

  /**
   * Walk every generated Act 1 zone and validate the native Warp graph.  This
   * is deliberately an offline assertion: production Warp resolution remains
   * unchanged, while a single run catches bad Levels.txt Vis/Warp imports in
   * all caves, passages, towers and quest sub-levels.
   */
  private void validateAct1WarpGraph() {
    StringBuilder edges = new StringBuilder();
    com.artemis.ComponentMapper<Warp> warpMapper = engine.getMapper(Warp.class);
    com.artemis.ComponentMapper<Position> positionMapper = engine.getMapper(Position.class);
    if (map.getZones().size > 200) {
      throw new IllegalStateException("Act1 Warp graph has excessive zones: "
          + map.getZones().size);
    }
    int warpEntitySlots = 0;
    for (Map.Zone zone : map.getZones()) {
      if (zone.levelAct() == 1) warpEntitySlots += zone.getWarpEntities().size;
    }
    if (warpEntitySlots > 5000) {
      throw new IllegalStateException("Act1 Warp graph has excessive Warp slots: "
          + warpEntitySlots);
    }
    com.badlogic.gdx.utils.IntMap<Map.Zone> zonesByLevel = new com.badlogic.gdx.utils.IntMap<>();
    com.badlogic.gdx.utils.IntMap<com.badlogic.gdx.utils.IntSet> destinationsByLevel =
        new com.badlogic.gdx.utils.IntMap<>();
    for (Map.Zone zone : map.getZones()) {
      if (zone.levelAct() != 1) continue;
      zonesByLevel.put(zone.levelId(), zone);
      com.badlogic.gdx.utils.IntSet destinations = new com.badlogic.gdx.utils.IntSet();
      for (int entity : zone.getWarpEntities().toArray()) {
        if (!warpMapper.has(entity)) continue;
        Warp warp = warpMapper.get(entity);
        if (warp.dstLevel != null) destinations.add(warp.dstLevel.Id);
      }
      destinationsByLevel.put(zone.levelId(), destinations);
    }
    for (Map.Zone sourceZone : map.getZones()) {
      if (sourceZone.levelAct() != 1) continue;
      graphZoneCount++;
      if (sourceZone.getWarpEntities().size > 1000) {
        throw new IllegalStateException("Act1 Zone has excessive Warp entities: level="
            + sourceZone.levelId() + " count=" + sourceZone.getWarpEntities().size);
      }
      com.badlogic.gdx.utils.IntSet seenEntities = new com.badlogic.gdx.utils.IntSet();
      int[] sourceWarpEntities = sourceZone.getWarpEntities().toArray();
      for (int entity : sourceWarpEntities) {
        if (!seenEntities.add(entity)) continue;
        if (!warpMapper.has(entity)) continue;
        Warp warp = warpMapper.get(entity);
        graphWarpCount++;
        int sourceId = sourceZone.levelId();
        int destinationId = warp.dstLevel == null ? -1 : warp.dstLevel.Id;
        if (warp.dstLevel == null || !zonesByLevel.containsKey(destinationId)) {
          graphUnresolvedCount++;
          throw new IllegalStateException("Act1 Warp graph unresolved: source=" + sourceId
              + " destination=" + destinationId + " index=" + warp.index);
        }
        Position position = positionMapper.get(entity);
        if (position == null || !sourceZone.contains(Math.round(position.position.x),
            Math.round(position.position.y))) {
          throw new IllegalStateException("Act1 Warp graph position outside zone: source="
              + sourceId + " index=" + warp.index);
        }
        // Tall stair and tower transition tiles can place the Warp anchor
        // farther inside their blocked DT1 footprint than cave doorways.
        boolean walkable = hasWalkableCoordinate(sourceZone, position.position, 32);
        if (!walkable) {
          throw new IllegalStateException("Act1 Warp graph has no walkable landing: source="
              + sourceId + " index=" + warp.index);
        }
        graphWalkableCount++;
        com.badlogic.gdx.utils.IntSet reverseDestinations =
            destinationsByLevel.get(destinationId);
        boolean reverse = reverseDestinations != null && reverseDestinations.contains(sourceId);
        if (reverse) graphReverseWarpCount++;
        else graphMissingReverseCount++;
        // Keep the manifest bounded even if a malformed export duplicates an
        // entity. Counts still cover the complete graph; only the first 1000
        // edges are emitted as diagnostics.
        if (graphWarpCount <= 1000) {
          if (edges.length() > 0) edges.append('\n');
          Map.RoomEx room = sourceZone.findRoomEx(position.position.x, position.position.y);
          edges.append(sourceId).append("->").append(destinationId)
              .append(" index=").append(warp.index)
              .append(" room=").append(room == null ? -1 : room.id)
              .append(" reverse=").append(reverse)
              .append(" walkable=").append(walkable);
        }
      }
    }
    graphEdges = edges.toString();
    com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    boolean passed = graphUnresolvedCount == 0 && graphMissingReverseCount == 0
        && graphWalkableCount == graphWarpCount;
    String report = "mode=act1-warp-graph\n"
        + "d2Version=" + System.getProperty("riiablo.d2-version", "unspecified") + "\n"
        + "zones=" + graphZoneCount + "\n"
        + "warps=" + graphWarpCount + "\n"
        + "reverseWarps=" + graphReverseWarpCount + "\n"
        + "walkable=" + graphWalkableCount + "\n"
        + "unresolved=" + graphUnresolvedCount + "\n"
        + "missingReverse=" + graphMissingReverseCount + "\n"
        + "result=" + (passed ? "PASS" : "FAIL") + "\n\n" + graphEdges + "\n";
    output.child("act1-warp-graph-manifest.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_WARP_GRAPH] zones=" + graphZoneCount
        + " warps=" + graphWarpCount + " reverse=" + graphReverseWarpCount
        + " walkable=" + graphWalkableCount + " missingReverse=" + graphMissingReverseCount);
    if (!passed) {
      throw new IllegalStateException("Act1 Warp graph validation failed: warps=" + graphWarpCount
          + " reverse=" + graphReverseWarpCount + " walkable=" + graphWalkableCount
          + " unresolved=" + graphUnresolvedCount
          + " missingReverse=" + graphMissingReverseCount);
    }
  }

  /** Fast size-one collision probe used by the full graph audit. */
  private static boolean hasWalkableCoordinate(Map.Zone zone, Vector2 origin, int distance) {
    int originX = Math.round(origin.x);
    int originY = Math.round(origin.y);
    for (int radius = 0; radius <= distance; radius++) {
      for (int y = originY - radius; y <= originY + radius; y++) {
        for (int x = originX - radius; x <= originX + radius; x++) {
          if (radius > 0 && Math.abs(x - originX) != radius
              && Math.abs(y - originY) != radius) continue;
          if (!zone.contains(x, y)) continue;
          if ((zone.flags(x - zone.x(), y - zone.y())
              & com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK) == 0) return true;
        }
      }
    }
    return false;
  }

  /**
   * Computes lightweight continuity indicators from the generated Act 1 map.
   * RoomEx adjacency is authoritative; collision coverage is sampled every
   * four subtiles so the audit stays bounded while still detecting all-black
   * regions and disconnected entrance rooms.
   */
  private void validateAct1Continuity() {
    com.artemis.ComponentMapper<Warp> warpMapper = engine.getMapper(Warp.class);
    com.artemis.ComponentMapper<Position> positionMapper = engine.getMapper(Position.class);
    for (Map.Zone zone : map.getZones()) {
      if (zone.levelAct() != 1) continue;
      continuityZones++;
      int width = zone.getRoomsEx().size;
      if (width > 0) {
        continuityRoomZones++;
        int[] components = new int[width];
        java.util.Arrays.fill(components, -1);
        int largestComponent = -1;
        int largestSize = 0;
        for (int start = 0; start < width; start++) {
          if (components[start] >= 0) continue;
          int componentId = continuityComponents++;
          com.badlogic.gdx.utils.IntArray queue = new com.badlogic.gdx.utils.IntArray();
          queue.add(start);
          components[start] = componentId;
          int componentSize = 0;
          while (queue.size > 0) {
            int roomId = queue.pop();
            componentSize++;
            Map.RoomEx room = zone.getRoomsEx().get(roomId);
            for (int adjacentId : room.getAdjacentRoomIds()) {
              if (adjacentId < 0 || adjacentId >= width) {
                continuityInvalidAdjacency++;
                continue;
              }
              if (components[adjacentId] < 0) {
                components[adjacentId] = componentId;
                queue.add(adjacentId);
              }
            }
          }
          if (componentSize > largestSize) {
            largestSize = componentSize;
            largestComponent = componentId;
          }
        }

        for (int entity : zone.getWarpEntities().toArray()) {
          if (!warpMapper.has(entity) || !positionMapper.has(entity)) continue;
          Position position = positionMapper.get(entity);
          Map.RoomEx room = zone.findRoomEx(position.position.x, position.position.y);
          if (room == null) {
            continuityWarpRoomMissing++;
          } else if (components[room.id] != largestComponent) {
            continuityWarpOutsideMain++;
          }
        }
      }

      boolean hasWalkable = false;
      for (int y = 0; y < zone.height(); y += 4) {
        for (int x = 0; x < zone.width(); x += 4) {
          continuitySampledCells++;
          if ((zone.staticFlags(x, y) & com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK) == 0) {
            continuityWalkableCells++;
            hasWalkable = true;
          }
        }
      }
      if (!hasWalkable) continuityNoWalkableZones++;
    }

    com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    boolean passed = continuityInvalidAdjacency == 0 && continuityNoWalkableZones == 0
        && continuityWarpRoomMissing == 0 && continuityWarpOutsideMain == 0;
    String report = "mode=act1-map-continuity\n"
        + "d2Version=" + System.getProperty("riiablo.d2-version", "unspecified") + "\n"
        + "zones=" + continuityZones + "\n"
        + "roomZones=" + continuityRoomZones + "\n"
        + "components=" + continuityComponents + "\n"
        + "invalidAdjacency=" + continuityInvalidAdjacency + "\n"
        + "noWalkableZones=" + continuityNoWalkableZones + "\n"
        + "warpRoomMissing=" + continuityWarpRoomMissing + "\n"
        + "warpOutsideMain=" + continuityWarpOutsideMain + "\n"
        + "sampledCells=" + continuitySampledCells + "\n"
        + "walkableCells=" + continuityWalkableCells + "\n"
        + "walkableRatio=" + (continuitySampledCells == 0 ? 0
            : (double) continuityWalkableCells / continuitySampledCells) + "\n"
        + "result=" + (passed ? "PASS" : "FAIL") + "\n";
    output.child("act1-map-continuity-manifest.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_CONTINUITY] zones=" + continuityZones
        + " components=" + continuityComponents + " invalidAdjacency="
        + continuityInvalidAdjacency + " warpOutsideMain=" + continuityWarpOutsideMain
        + " walkableRatio=" + (continuitySampledCells == 0 ? 0
            : (double) continuityWalkableCells / continuitySampledCells));
    if (!passed) {
      throw new IllegalStateException("Act1 map continuity validation failed: invalidAdjacency="
          + continuityInvalidAdjacency + " noWalkableZones=" + continuityNoWalkableZones
          + " warpRoomMissing=" + continuityWarpRoomMissing
          + " warpOutsideMain=" + continuityWarpOutsideMain);
    }
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
    for (int i = 0; i < targetZone.getWarpEntities().size; i++) {
      int warpEntity = targetZone.getWarpEntities().get(i);
      if (!warpMapper.has(warpEntity)) continue;
      Warp warp = warpMapper.get(warpEntity);
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
      for (int j = 0; j < destinationZone.getWarpEntities().size; j++) {
        int reverseEntity = destinationZone.getWarpEntities().get(j);
        if (!warpMapper.has(reverseEntity)) continue;
        Warp reverse = warpMapper.get(reverseEntity);
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
