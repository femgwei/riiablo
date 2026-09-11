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
import com.riiablo.map.NativePresetObjectResolver;
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
  private int continuityBoundaryPairs;
  private int continuityBoundaryWalkable;
  private int continuityBoundaryBlocked;
  private int continuityBoundaryUncheckable;
  private int continuityBoundaryBfsResolved;
  private int continuityBoundaryBfsBlocked;
  private int continuityBoundaryBfsUncheckable;
  private int continuityBoundaryObjectNearby;
  private int continuityBoundaryDoorNearby;
  private int continuityBoundaryBlockedDoor;
  private int continuityBoundaryBlockedObject;
  private int continuityBoundaryBlockedBare;
  private int continuityBoundaryWarpBlocked;
  private int continuityBoundaryWarpBare;
  private final StringBuilder continuityBoundaryDetails = new StringBuilder();

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

      validateRoomBoundaryTransitions(zone);

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
        && continuityWarpRoomMissing == 0 && continuityWarpOutsideMain == 0
        && continuityBoundaryWarpBare == 0;
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
        + "boundaryPairs=" + continuityBoundaryPairs + "\n"
        + "boundaryWalkable=" + continuityBoundaryWalkable + "\n"
        + "boundaryBlocked=" + continuityBoundaryBlocked + "\n"
        + "boundaryUncheckable=" + continuityBoundaryUncheckable + "\n"
        + "boundaryBfsResolved=" + continuityBoundaryBfsResolved + "\n"
        + "boundaryBfsBlocked=" + continuityBoundaryBfsBlocked + "\n"
        + "boundaryBfsUncheckable=" + continuityBoundaryBfsUncheckable + "\n"
        + "boundaryObjectNearby=" + continuityBoundaryObjectNearby + "\n"
        + "boundaryDoorNearby=" + continuityBoundaryDoorNearby + "\n"
        + "boundaryBlockedDoor=" + continuityBoundaryBlockedDoor + "\n"
        + "boundaryBlockedObject=" + continuityBoundaryBlockedObject + "\n"
        + "boundaryBlockedBare=" + continuityBoundaryBlockedBare + "\n"
        + "boundaryWarpBlocked=" + continuityBoundaryWarpBlocked + "\n"
        + "boundaryWarpBare=" + continuityBoundaryWarpBare + "\n"
        + "result=" + (passed ? "PASS" : "FAIL") + "\n"
        + continuityBoundaryDetails;
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
          + " warpOutsideMain=" + continuityWarpOutsideMain
          + " boundaryWarpBare=" + continuityBoundaryWarpBare);
    }
  }

  /** Checks that every native RoomEx adjacency has at least one walkable edge. */
  private void validateRoomBoundaryTransitions(Map.Zone zone) {
    int roomCount = zone.getRoomsEx().size;
    com.artemis.ComponentMapper<Position> positionMapper = engine.getMapper(Position.class);
    for (int roomId = 0; roomId < roomCount; roomId++) {
      Map.RoomEx room = zone.getRoomsEx().get(roomId);
      for (int adjacentId : room.getAdjacentRoomIds()) {
        if (adjacentId <= roomId || adjacentId < 0 || adjacentId >= roomCount) continue;
        Map.RoomEx adjacent = zone.getRoomsEx().get(adjacentId);
        continuityBoundaryPairs++;
        int result = findRoomBoundaryTransition(zone, room, adjacent);
        if (result > 0) continuityBoundaryWalkable++;
        else if (result == 0) {
          continuityBoundaryBlocked++;
          int bfs = findRoomLocalPath(zone, room, adjacent);
          if (bfs > 0) continuityBoundaryBfsResolved++;
          else if (bfs == 0) continuityBoundaryBfsBlocked++;
          else continuityBoundaryBfsUncheckable++;
          int objectKind = classifyBoundaryObjects(zone, room, adjacent);
          if (objectKind > 0) continuityBoundaryObjectNearby++;
          if (objectKind > 1) continuityBoundaryDoorNearby++;
          boolean warpBoundary = containsWarp(zone, room, positionMapper)
              || containsWarp(zone, adjacent, positionMapper);
          if (bfs == 0) {
            if (objectKind > 1) continuityBoundaryBlockedDoor++;
            else if (objectKind > 0) continuityBoundaryBlockedObject++;
            else continuityBoundaryBlockedBare++;
            if (warpBoundary) {
              continuityBoundaryWarpBlocked++;
              if (objectKind == 0) continuityBoundaryWarpBare++;
            }
          }
          if (continuityBoundaryDetails.length() < 12000) {
            continuityBoundaryDetails.append("level=").append(zone.levelId())
                .append(" rooms=").append(room.id).append(',').append(adjacent.id)
                .append(" a=").append(room.x).append(':').append(room.y).append('x')
                .append(room.width).append('x').append(room.height)
                .append(" b=").append(adjacent.x).append(':').append(adjacent.y).append('x')
                .append(adjacent.width).append('x').append(adjacent.height)
                .append(" bfs=").append(bfs).append(" objectKind=").append(objectKind)
                .append(" warpBoundary=").append(warpBoundary)
                .append('\n');
          }
        }
        else continuityBoundaryUncheckable++;
      }
    }
  }

  private static boolean containsWarp(Map.Zone zone, Map.RoomEx room,
      com.artemis.ComponentMapper<Position> positionMapper) {
    for (int entity : zone.getWarpEntities().toArray()) {
      if (!positionMapper.has(entity)) continue;
      Position position = positionMapper.get(entity);
      if (room.contains(position.position.x, position.position.y)) return true;
    }
    return false;
  }

  /** Classifies native objects near a candidate interface. */
  private static int classifyBoundaryObjects(Map.Zone zone, Map.RoomEx a, Map.RoomEx b) {
    int minX = Math.min(a.x, b.x) - zone.x() - 16;
    int minY = Math.min(a.y, b.y) - zone.y() - 16;
    int maxX = Math.max(a.x + a.width, b.x + b.width) - zone.x() + 16;
    int maxY = Math.max(a.y + a.height, b.y + b.height) - zone.y() + 16;
    int kind = 0;
    for (Map.NativeObject object : zone.getNativeObjects()) {
      if (object.x < minX || object.x > maxX || object.y < minY || object.y > maxY) continue;
      int classId = object.ds1Raw
          ? Riiablo.files.obj.getObjectId(1, object.presetIndex) : object.presetIndex;
      NativePresetObjectResolver.Resolution resolution = NativePresetObjectResolver.resolve(
          1, zone.levelId(), classId, zone.map.seed(), object.x, object.y);
      if (!resolution.shouldCreate()) continue;
      com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(resolution.classId);
      if (base != null && base.IsDoor) return 2;
      kind = 1;
    }
    return kind;
  }

  private static int findRoomLocalPath(Map.Zone zone, Map.RoomEx a, Map.RoomEx b) {
    int minX = Math.max(zone.x(), Math.min(a.x, b.x) - 8);
    int minY = Math.max(zone.y(), Math.min(a.y, b.y) - 8);
    int maxX = Math.min(zone.x() + zone.width() - 1,
        Math.max(a.x + a.width, b.x + b.width) + 8);
    int maxY = Math.min(zone.y() + zone.height() - 1,
        Math.max(a.y + a.height, b.y + b.height) + 8);
    int width = maxX - minX + 1;
    int height = maxY - minY + 1;
    if (width <= 0 || height <= 0 || width > 128 || height > 128) return -1;
    boolean[] visited = new boolean[width * height];
    com.badlogic.gdx.utils.IntArray queue = new com.badlogic.gdx.utils.IntArray();
    for (int y = minY; y <= maxY; y++) {
      for (int x = minX; x <= maxX; x++) {
        if (a.contains(x, y) && isWalkable(zone, x, y)) {
          int index = (y - minY) * width + x - minX;
          if (!visited[index]) {
            visited[index] = true;
            queue.add(index);
          }
        }
      }
    }
    if (queue.size == 0) return -1;
    while (queue.size > 0) {
      int index = queue.pop();
      int x = minX + index % width;
      int y = minY + index / width;
      if (b.contains(x, y)) return 1;
      if (x > minX) enqueueWalkable(zone, x - 1, y, minX, minY, width, height, visited, queue);
      if (x < maxX) enqueueWalkable(zone, x + 1, y, minX, minY, width, height, visited, queue);
      if (y > minY) enqueueWalkable(zone, x, y - 1, minX, minY, width, height, visited, queue);
      if (y < maxY) enqueueWalkable(zone, x, y + 1, minX, minY, width, height, visited, queue);
    }
    return 0;
  }

  private static void enqueueWalkable(Map.Zone zone, int x, int y, int minX, int minY,
      int width, int height, boolean[] visited, com.badlogic.gdx.utils.IntArray queue) {
    if (!isWalkable(zone, x, y)) return;
    int index = (y - minY) * width + x - minX;
    if (index < 0 || index >= visited.length || visited[index]) return;
    visited[index] = true;
    queue.add(index);
  }

  /** Returns 1 for a walkable transition, 0 for a blocked known interface, -1 if uncheckable. */
  private static int findRoomBoundaryTransition(Map.Zone zone, Map.RoomEx a, Map.RoomEx b) {
    int aRight = a.x + a.width;
    int bRight = b.x + b.width;
    int aBottom = a.y + a.height;
    int bBottom = b.y + b.height;
    if (aRight <= b.x || bRight <= a.x) {
      boolean aBefore = aRight <= b.x;
      int left = aBefore ? aRight - 1 : bRight - 1;
      int right = aBefore ? b.x : a.x;
      int overlapStart = Math.max(a.y, b.y);
      int overlapEnd = Math.min(aBottom, bBottom);
      if (overlapStart >= overlapEnd || right - left > 32) return -1;
      for (int y = overlapStart; y < overlapEnd; y += 2) {
        for (int offset = 0; offset <= 4; offset++) {
          int firstX = aBefore ? left - offset : left + offset;
          int secondX = aBefore ? right + offset : right - offset;
          if (a.contains(firstX, y) && b.contains(secondX, y)
              && isWalkable(zone, firstX, y) && isWalkable(zone, secondX, y)) return 1;
        }
      }
      return 0;
    }
    if (aBottom <= b.y || bBottom <= a.y) {
      boolean aBefore = aBottom <= b.y;
      int top = aBefore ? aBottom - 1 : bBottom - 1;
      int bottom = aBefore ? b.y : a.y;
      int overlapStart = Math.max(a.x, b.x);
      int overlapEnd = Math.min(aRight, bRight);
      if (overlapStart >= overlapEnd || bottom - top > 32) return -1;
      for (int x = overlapStart; x < overlapEnd; x += 2) {
        for (int offset = 0; offset <= 4; offset++) {
          int firstY = aBefore ? top - offset : top + offset;
          int secondY = aBefore ? bottom + offset : bottom - offset;
          if (a.contains(x, firstY) && b.contains(x, secondY)
              && isWalkable(zone, x, firstY) && isWalkable(zone, x, secondY)) return 1;
        }
      }
      return 0;
    }
    // Overlapping rectangles are valid for native rooms whose bounds include
    // a doorway; any walkable overlap is a valid transition.
    int startX = Math.max(a.x, b.x), endX = Math.min(aRight, bRight);
    int startY = Math.max(a.y, b.y), endY = Math.min(aBottom, bBottom);
    if (startX >= endX || startY >= endY) return -1;
    for (int y = startY; y < endY; y += 2) {
      for (int x = startX; x < endX; x += 2) {
        if (isWalkable(zone, x, y)) return 1;
      }
    }
    return 0;
  }

  private static boolean isWalkable(Map.Zone zone, int worldX, int worldY) {
    return zone.contains(worldX, worldY)
        && (zone.staticFlags(worldX - zone.x(), worldY - zone.y())
            & com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK) == 0;
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
