package com.riiablo.screen;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.riiablo.save.CharData;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;
import com.riiablo.engine.client.AutomapRenderer;
import com.riiablo.engine.client.automap.AutomapLayer;
import com.riiablo.engine.client.automap.AutomapCellAudit;
import com.riiablo.engine.client.automap.AutomapCell;
import com.riiablo.engine.client.automap.AutomapManager;
import com.riiablo.engine.client.automap.AutomapProjection;

/**
 * Production {@link GameScreen} smoke test driven by a 1x1 hidden LWJGL
 * window. Unlike {@link OffscreenRenderScreen}, this executes Act loading,
 * DRLG generation, Rogue Encampment entity creation and the real renderer.
 */
public final class OffscreenCampScreen extends GameScreen {
  private static final int AUTOMAP_CAPTURE_WIDTH = 854;
  private static final int AUTOMAP_CAPTURE_HEIGHT = 480;

  private final String outputDirectory;
  private final int targetLevelId;
  private final boolean validateWarpGraph;
  private final boolean validateContinuity;
  private final boolean validateWarpCollision;
  private final boolean validateNativeAutomap;
  private final boolean validateObjectAudit;
  private String objectAuditSummary = "";
  private int renderedFrames;
  private boolean completed;
  private boolean targetApplied;
  private Map.Zone targetZone;
  private int targetNativeCells;
  private int targetRoadCells;
  private int targetRoomCount;
  private int targetNativeObjects;
  private AutomapCellAudit.Result targetCellAudit;
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
  private DynamicCollisionProbe dynamicCollisionProbe;
  private int dynamicObjectCandidates;
  private int dynamicWarpRoomCandidates;
  private int dynamicDoors;
  private int dynamicCollisionChecks;
  private int dynamicCollisionCleared;
  private int dynamicCollisionRestored;
  private int dynamicCollisionLeaks;
  private int dynamicCollisionFailures;
  private boolean dynamicCollisionSkipped;
  private final StringBuilder dynamicCollisionDetails = new StringBuilder();

  public OffscreenCampScreen(CharData charData, String outputDirectory) {
    this(charData, outputDirectory, -1, false);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId) {
    this(charData, outputDirectory, targetLevelId, false, false);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId,
      boolean validateWarpGraph) {
    this(charData, outputDirectory, targetLevelId, validateWarpGraph, false, false);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId,
      boolean validateWarpGraph, boolean validateContinuity) {
    this(charData, outputDirectory, targetLevelId, validateWarpGraph, validateContinuity, false);
  }

  public OffscreenCampScreen(CharData charData, String outputDirectory, int targetLevelId,
      boolean validateWarpGraph, boolean validateContinuity, boolean validateWarpCollision) {
    super(charData);
    this.outputDirectory = outputDirectory;
    this.targetLevelId = targetLevelId;
    this.validateWarpGraph = validateWarpGraph;
    this.validateContinuity = validateContinuity;
    this.validateWarpCollision = validateWarpCollision;
    this.validateNativeAutomap = Boolean.getBoolean("riiablo.offscreen-automap-native");
    this.validateObjectAudit = Boolean.getBoolean("riiablo.offscreen-object-audit");
  }

  /** Select a requested non-Act-I level before the normal screen setup. */
  @Override
  public void show() {
    if (validateNativeAutomap) {
      int mode = Integer.getInteger("riiablo.offscreen-automap-mode",
          com.riiablo.map.RenderSystem.AUTOMAP_MODE_CENTER);
      if (mode < com.riiablo.map.RenderSystem.AUTOMAP_MODE_TOP_LEFT
          || mode > com.riiablo.map.RenderSystem.AUTOMAP_MODE_CENTER) {
        mode = com.riiablo.map.RenderSystem.AUTOMAP_MODE_CENTER;
      }
      com.riiablo.map.RenderSystem.AUTOMAP_MODE = mode;
    }
    if (map.getAct() == -1 && targetLevelId >= 0) {
      Levels.Entry target = Riiablo.files.Levels.get(targetLevelId);
      if (target == null) {
        throw new IllegalStateException("Unknown offscreen level id=" + targetLevelId);
      }
      // setAct delegates loading/generation to GameLoadingScreen, just like
      // the normal waypoint path; it must happen before GameScreen.show()
      // applies its default Act-I start.
      setAct(target.Act);
      return;
    }
    super.show();
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
    // Wait for the initial object pass to settle before sampling references;
    // newly activated rooms may attach their Box2D/object footprints for a few
    // fixed ticks after the loading screen is dismissed.
    if (validateWarpCollision && renderedFrames >= 8) {
      if (dynamicCollisionProbe == null) {
        dynamicCollisionProbe = beginDynamicCollisionProbe();
        return;
      }
      if (dynamicCollisionProbe.phase == 1) {
        checkSolidCollisionPhase(dynamicCollisionProbe);
        dynamicCollisionProbe.phase = 2;
        setOpenProbeModes(dynamicCollisionProbe);
        return;
      }
      if (dynamicCollisionProbe.phase == 2) {
        checkOpenCollisionPhase(dynamicCollisionProbe);
        dynamicCollisionProbe.phase = 3;
        restoreProbeModes(dynamicCollisionProbe);
        return;
      }
      if (dynamicCollisionProbe.phase == 3) {
        checkRestoredCollisionPhase(dynamicCollisionProbe);
        dynamicCollisionProbe.phase = 4;
      }
    }
    int minimumFrames = validateWarpCollision ? 12 : (targetApplied ? 6 : 3);
    if (renderedFrames < minimumFrames) return;
    if (validateWarpGraph) validateAct1WarpGraph();
    if (validateContinuity) validateAct1Continuity();
    if (validateWarpCollision && dynamicCollisionFailures > 0) {
      throw new IllegalStateException("Act1 dynamic Warp collision validation failed: candidates="
          + dynamicObjectCandidates + " checks=" + dynamicCollisionChecks
          + " cleared=" + dynamicCollisionCleared + " restored=" + dynamicCollisionRestored
          + " leaks=" + dynamicCollisionLeaks + " failures=" + dynamicCollisionFailures
          + " details=" + dynamicCollisionDetails);
    }
    validateTargetAutomap();
    if (validateNativeAutomap) validateNativeAutomapRendering();
    if (validateObjectAudit) validateObjectGeneration();
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
        + "targetRoadCells=" + targetRoadCells + "\n"
        + "targetRooms=" + targetRoomCount + "\n"
        + "targetNativeObjects=" + targetNativeObjects + "\n"
        + "automapAuditWithin=" + (targetCellAudit == null ? 0 : targetCellAudit.withinCategoryExact) + "\n"
        + "automapAuditCross=" + (targetCellAudit == null ? 0 : targetCellAudit.crossCategoryExact) + "\n"
        + "automapAuditPositionConflict=" + (targetCellAudit == null ? 0 : targetCellAudit.samePositionDifferentCell) + "\n"
        + "automapNative=" + validateNativeAutomap + "\n"
        + objectAuditSummary
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
        + "dynamicObjectCandidates=" + dynamicObjectCandidates + "\n"
        + "dynamicWarpRoomCandidates=" + dynamicWarpRoomCandidates + "\n"
        + "dynamicDoors=" + dynamicDoors + "\n"
        + "dynamicCollisionChecks=" + dynamicCollisionChecks + "\n"
        + "dynamicCollisionCleared=" + dynamicCollisionCleared + "\n"
        + "dynamicCollisionRestored=" + dynamicCollisionRestored + "\n"
        + "dynamicCollisionLeaks=" + dynamicCollisionLeaks + "\n"
        + "dynamicCollisionFailures=" + dynamicCollisionFailures + "\n"
        + "dynamicCollisionSkipped=" + dynamicCollisionSkipped + "\n"
        + dynamicCollisionDetails
        + "player=" + player + "\n"
        + "d2Version=" + System.getProperty("riiablo.d2-version", "unspecified") + "\n"
        + "result=PASS\n";
    output.child("rogue-encampment-manifest.txt").writeString(report, false, "UTF-8");
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_CAMP] result=PASS act="
        + (map.getAct() + 1) + " player=" + player + " frames=" + renderedFrames);
    Gdx.app.exit();
  }

  /**
   * Audits the two Act-I zones involved in the first town-to-outdoor
   * transition.  Objects are reported from the live ECS, not inferred from
   * Automap cells, so a missing chest/corpse/trap can be separated from a
   * missing marker.  Level 1 is Rogue Encampment and level 2 is Blood Moor.
   */
  private void validateObjectGeneration() {
    ComponentMapper<com.riiablo.engine.server.component.Object> objects =
        engine.getMapper(com.riiablo.engine.server.component.Object.class);
    ComponentMapper<Position> positions = engine.getMapper(Position.class);
    ComponentMapper<MapWrapper> wrappers = engine.getMapper(MapWrapper.class);
    IntBag entities = engine.getAspectSubscriptionManager()
        .get(Aspect.all(com.riiablo.engine.server.component.Object.class,
            Position.class, MapWrapper.class)).getEntities();
    StringBuilder csv = new StringBuilder(
        "entityId,levelId,zone,town,objectId,name,operateFn,initFn,autoMap,trapProb,"
            + "interactable,mode,stateFlags,x,y\n");
    int[] total = new int[3];
    int[] interactable = new int[3];
    int[] containers = new int[3];
    int[] sceneCorpses = new int[3];
    int[] traps = new int[3];
    int[] trapCapable = new int[3];
    int[] nativeDefinitions = new int[2];
    for (Map.Zone zone : map.getZones()) {
      if (zone.levelId() == 1) nativeDefinitions[0] += zone.getNativeObjects().size;
      if (zone.levelId() == 2) nativeDefinitions[1] += zone.getNativeObjects().size;
    }
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      com.riiablo.engine.server.component.Object object = objects.get(id);
      MapWrapper wrapper = wrappers.get(id);
      Position position = positions.get(id);
      if (object == null || object.base == null || wrapper == null || wrapper.zone == null) continue;
      int levelId = wrapper.zone.levelId();
      int bucket = levelId == 1 ? 0 : levelId == 2 ? 1 : 2;
      total[bucket]++;
      boolean canInteract = (object.stateFlags
          & com.riiablo.engine.server.component.Object.STATE_INTERACTABLE) != 0;
      if (canInteract) interactable[bucket]++;
      String name = object.base.Name == null ? "" : object.base.Name;
      String token = object.base.Token == null ? "" : object.base.Token;
      String lower = (name + " " + token).toLowerCase(java.util.Locale.ROOT);
      boolean corpse = lower.contains("corpse") || lower.contains("dead")
          || lower.contains("body") || lower.contains("casket") || lower.contains("coffin");
      boolean trap = object.base.OperateFn == 7 || lower.contains("trap");
      boolean container = object.base.OperateFn == 3 || object.base.OperateFn == 4
          || object.base.OperateFn == 57 || object.base.OperateFn == 58
          || object.base.OperateFn == 59 || lower.contains("chest")
          || lower.contains("barrel") || lower.contains("urn") || lower.contains("casket");
      if (corpse) sceneCorpses[bucket]++;
      if (trap) traps[bucket]++;
      if (object.base.TrapProb > 0) trapCapable[bucket]++;
      if (container) containers[bucket]++;
      csv.append(id).append(',').append(levelId).append(',')
          .append(csvValue(wrapper.zone.level == null ? "" : wrapper.zone.level.LevelName)).append(',')
          .append(wrapper.zone.isTown()).append(',').append(object.base.Id).append(',')
          .append(csvValue(name)).append(',').append(object.base.OperateFn).append(',')
          .append(object.base.InitFn).append(',').append(object.base.AutoMap).append(',')
          .append(object.base.TrapProb).append(',').append(canInteract).append(',')
          .append(object.mode).append(',').append(object.stateFlags).append(',')
          .append(position == null ? 0 : position.position.x).append(',')
          .append(position == null ? 0 : position.position.y).append('\n');
    }
    com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    output.child("act1-town-bloodmoor-objects.csv")
        .writeString(csv.toString(), false, "UTF-8");
    writeNativeObjectAudit(output);
    StringBuilder summary = new StringBuilder();
    summary.append("objectAudit=true\n");
    summary.append("objectAuditLevel1Total=").append(total[0]).append('\n');
    summary.append("objectAuditLevel1NativeDefinitions=").append(nativeDefinitions[0]).append('\n');
    summary.append("objectAuditLevel1Interactable=").append(interactable[0]).append('\n');
    summary.append("objectAuditLevel1Containers=").append(containers[0]).append('\n');
    summary.append("objectAuditLevel1SceneCorpses=").append(sceneCorpses[0]).append('\n');
    summary.append("objectAuditLevel1Traps=").append(traps[0]).append('\n');
    summary.append("objectAuditLevel1TrapCapable=").append(trapCapable[0]).append('\n');
    summary.append("objectAuditLevel2Total=").append(total[1]).append('\n');
    summary.append("objectAuditLevel2NativeDefinitions=").append(nativeDefinitions[1]).append('\n');
    summary.append("objectAuditLevel2Interactable=").append(interactable[1]).append('\n');
    summary.append("objectAuditLevel2Containers=").append(containers[1]).append('\n');
    summary.append("objectAuditLevel2SceneCorpses=").append(sceneCorpses[1]).append('\n');
    summary.append("objectAuditLevel2Traps=").append(traps[1]).append('\n');
    summary.append("objectAuditLevel2TrapCapable=").append(trapCapable[1]).append('\n');
    summary.append("objectAuditOtherLevels=").append(total[2]).append('\n');
    objectAuditSummary = summary.toString();
    Gdx.app.log("OffscreenCampScreen", "[OBJECT_AUDIT] level1 total=" + total[0]
        + " native=" + nativeDefinitions[0]
        + " interactable=" + interactable[0] + " containers=" + containers[0]
        + " corpses=" + sceneCorpses[0] + " traps=" + traps[0]
        + " trapCapable=" + trapCapable[0]
        + "; level2 total=" + total[1] + " native=" + nativeDefinitions[1]
        + " interactable=" + interactable[1]
        + " containers=" + containers[1] + " corpses=" + sceneCorpses[1]
        + " traps=" + traps[1] + " trapCapable=" + trapCapable[1]);
    if (total[0] == 0 || total[1] == 0) {
      throw new IllegalStateException("Act1 town/outdoor object generation is empty: level1="
          + total[0] + " level2=" + total[1]);
    }
  }

  /** Writes every exported RoomEx object, including native-only/deferred rows. */
  private void writeNativeObjectAudit(com.badlogic.gdx.files.FileHandle output) {
    StringBuilder csv = new StringBuilder(
        "levelId,roomId,presetIndex,objectId,unitType,mode,worldX,worldY,sourceFile,"
            + "ds1Raw,externalEntity,spawned,resolverKind,creationStatus,entityId,name,operateFn,trapProb\n");
    for (Map.Zone zone : map.getZones()) {
      if (zone == null || zone.level == null) continue;
      int act = zone.levelAct();
      for (Map.NativeObject nativeObject : zone.getNativeObjects()) {
        int objectId = nativeObject.ds1Raw ? -1 : nativeObject.presetIndex;
        try {
          if (nativeObject.ds1Raw && nativeObject.presetIndex >= 0
              && nativeObject.presetIndex < Riiablo.files.obj.getSize(act)) {
            objectId = Riiablo.files.obj.getObjectId(act, nativeObject.presetIndex);
          }
        } catch (RuntimeException ignored) {
          // Preserve the row even when a malformed DS1 index cannot be resolved.
        }
        com.riiablo.codec.excel.Objects.Entry base = objectId >= 0
            ? Riiablo.files.objects.get(objectId) : null;
        String resolverKind = nativeObject.resolverKind;
        if ("UNRESOLVED".equals(resolverKind) && objectId >= 0) {
          try {
            resolverKind = NativePresetObjectResolver.resolve(act, zone.level.Id, objectId,
                map.seed(), nativeObject.x, nativeObject.y).kind.name();
          } catch (RuntimeException ignored) {}
        }
        csv.append(zone.level.Id).append(',').append(nativeObject.roomId).append(',')
            .append(nativeObject.presetIndex).append(',').append(objectId).append(',')
            .append(com.d2moo.common.drlg.D2UnitTypes.UNIT_OBJECT).append(',')
            .append(nativeObject.mode).append(',').append(zone.x() + nativeObject.x).append(',')
            .append(zone.y() + nativeObject.y).append(',').append(csvValue(nativeObject.sourceFile))
            .append(',').append(nativeObject.ds1Raw).append(',').append(nativeObject.externalEntity)
            .append(',').append(nativeObject.spawned).append(',').append(csvValue(resolverKind))
            .append(',').append(csvValue(nativeObject.creationStatus)).append(',')
            .append(nativeObject.entityId).append(',')
            .append(csvValue(base == null || base.Name == null ? "" : base.Name)).append(',')
            .append(base == null ? -1 : base.OperateFn).append(',')
            .append(base == null ? 0 : base.TrapProb).append('\n');
      }
    }
    output.child("native-object-audit.csv").writeString(csv.toString(), false, "UTF-8");
  }

  private void validateNativeAutomapRendering() {
    AutomapRenderer automap = engine.getSystem(AutomapRenderer.class);
    if (automap == null || automap.getAutomapManager() == null) {
      throw new IllegalStateException("Native Automap renderer unavailable");
    }
    AutomapManager manager = automap.getAutomapManager();
    if (!manager.canUseSprites()) {
      throw new IllegalStateException("MaxiMap.dc6 was not loaded");
    }
    int terrain = manager.getNativeTerrainDrawCount();
    int entities = manager.getNativeEntityDrawCount();
    int fallback = manager.getGeometricFallbackDrawCount();
    if (terrain <= 0) {
      throw new IllegalStateException("Native Automap rendered zero DC6 terrain cells");
    }
    exportAutomapEntityMarkers(manager);
    int visiblePixels = captureNativeAutomap(manager);
    if (visiblePixels < 100) {
      throw new IllegalStateException("Native Automap DC6 cells were drawn outside the visible viewport"
          + ": terrain=" + terrain + " visiblePixels=" + visiblePixels);
    }
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_AUTOMAP_DC6] terrain=" + terrain
        + " entities=" + entities + " fallback=" + fallback
        + " visiblePixels=" + visiblePixels);
  }

  /**
   * Exports the native cell chosen for each runtime entity and the closest
   * terrain cell. This distinguishes a bad Objects.txt marker from a terrain
   * cell that is drawn a second time by the entity pass.
   */
  private void exportAutomapEntityMarkers(AutomapManager manager) {
    com.artemis.ComponentMapper<com.riiablo.engine.server.component.Object> objects =
        engine.getMapper(com.riiablo.engine.server.component.Object.class);
    StringBuilder csv = new StringBuilder(
        "entityId,type,name,nativeCell,worldX,worldY,levelId,objectId,objectAutoMap,"
            + "shrineFunction,openWarp,nearestCategory,nearestCell,nearestDistance,"
            + "sameCellWithin16\n");
    for (int i = 0; i < manager.getEntityMarkerCount(); i++) {
      AutomapManager.EntityMarker marker = manager.getEntityMarker(i);
      Map.Zone zone = map.getZone(marker.worldX, marker.worldY);
      int levelId = zone == null ? -1 : zone.levelId();
      com.riiablo.engine.server.component.Object object = marker.entityId >= 0
          && objects.has(marker.entityId) ? objects.get(marker.entityId) : null;
      com.riiablo.codec.excel.Objects.Entry base = object == null ? null : object.base;
      NearestAutomapCell nearest = nearestAutomapCell(manager.getLayer(levelId), marker);
      csv.append(marker.entityId).append(',').append(marker.type).append(',')
          .append(csvValue(marker.name)).append(',').append(marker.nativeCell).append(',')
          .append(marker.worldX).append(',').append(marker.worldY).append(',')
          .append(levelId).append(',')
          .append(base == null ? -1 : base.Id).append(',')
          .append(base == null ? -1 : base.AutoMap).append(',')
          .append(base == null ? -1 : base.ShrineFunction).append(',')
          .append(base != null && base.OpenWarp).append(',')
          .append(nearest.category).append(',').append(nearest.cellNo).append(',')
          .append(String.format(java.util.Locale.ROOT, "%.2f", nearest.distance)).append(',')
          .append(nearest.sameCellWithin16).append('\n');
    }
    com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    output.child("automap-entity-markers.csv").writeString(csv.toString(), false, "UTF-8");
  }

  private static NearestAutomapCell nearestAutomapCell(AutomapLayer layer,
      AutomapManager.EntityMarker marker) {
    NearestAutomapCell nearest = new NearestAutomapCell();
    if (layer == null) return nearest;
    scanNearest(nearest, "floors", layer.floors, marker);
    scanNearest(nearest, "roads", layer.roads, marker);
    scanNearest(nearest, "walls", layer.walls, marker);
    scanNearest(nearest, "objects", layer.objects, marker);
    scanNearest(nearest, "extras", layer.extras, marker);
    return nearest;
  }

  private static void scanNearest(NearestAutomapCell nearest, String category,
      com.badlogic.gdx.utils.Array<AutomapCell> cells, AutomapManager.EntityMarker marker) {
    Vector2 markerPoint = new Vector2();
    Vector2 cellPoint = new Vector2();
    AutomapProjection.worldToAutomap(marker.worldX, marker.worldY, markerPoint);
    for (AutomapCell cell : cells) {
      AutomapProjection.worldToAutomap(cell.xPixel, cell.yPixel, cellPoint);
      float distance = markerPoint.dst(cellPoint);
      if (cell.cellNo == marker.nativeCell && distance <= 16f) {
        nearest.sameCellWithin16++;
      }
      if (distance < nearest.distance) {
        nearest.category = category;
        nearest.cellNo = cell.cellNo;
        nearest.distance = distance;
      }
    }
  }

  private static String csvValue(String value) {
    if (value == null) return "";
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private static final class NearestAutomapCell {
    String category = "";
    int cellNo = -1;
    float distance = Float.POSITIVE_INFINITY;
    int sameCellWithin16;
  }

  /**
   * Renders only native Automap DC6 cells into a production-sized FBO. The
   * original 1x1 smoke image proved that draw calls happened, but could not
   * detect world coordinates being sent directly to a screen-space batch.
   */
  private int captureNativeAutomap(AutomapManager manager) {
    Position playerPosition = engine.getMapper(Position.class).get(player);
    if (playerPosition == null || playerPosition.position == null) {
      throw new IllegalStateException("Automap capture player position unavailable");
    }
    Vector2 center = new Vector2();
    AutomapProjection.worldToAutomap(
        playerPosition.position.x, playerPosition.position.y, center);
    OrthographicCamera camera = new OrthographicCamera(
        AUTOMAP_CAPTURE_WIDTH, AUTOMAP_CAPTURE_HEIGHT);
    camera.position.set(center.x, center.y, 0f);
    camera.update();

    FrameBuffer frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888,
        AUTOMAP_CAPTURE_WIDTH, AUTOMAP_CAPTURE_HEIGHT, false);
    Pixmap pixels = null;
    try {
      frameBuffer.begin();
      Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
      Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
      Riiablo.batch.setProjectionMatrix(camera.combined);
      Riiablo.batch.setPalette(automapPalette());
      Riiablo.batch.begin();
      try {
        manager.renderWithSprites(Riiablo.batch, map, 0, 0, 0, 0, 0, 0);
        manager.renderNativeEntitySprites(Riiablo.batch, manager.opacity);
      } finally {
        if (Riiablo.batch.isDrawing()) Riiablo.batch.end();
      }
      pixels = Pixmap.createFromFrameBuffer(
          0, 0, AUTOMAP_CAPTURE_WIDTH, AUTOMAP_CAPTURE_HEIGHT);
    } finally {
      frameBuffer.end();
      frameBuffer.dispose();
    }

    int visiblePixels = 0;
    Pixmap flipped = new Pixmap(AUTOMAP_CAPTURE_WIDTH, AUTOMAP_CAPTURE_HEIGHT,
        Pixmap.Format.RGBA8888);
    try {
      for (int y = 0; y < AUTOMAP_CAPTURE_HEIGHT; y++) {
        for (int x = 0; x < AUTOMAP_CAPTURE_WIDTH; x++) {
          int pixel = pixels.getPixel(x, y);
          flipped.drawPixel(x, AUTOMAP_CAPTURE_HEIGHT - y - 1, pixel);
          if ((pixel >>> 8) != 0) visiblePixels++;
        }
      }
      com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
      output.mkdirs();
      PixmapIO.writePNG(output.child("automap-native-dc6.png"), flipped);
    } finally {
      if (pixels != null) pixels.dispose();
      flipped.dispose();
    }
    return visiblePixels;
  }

  private Texture automapPalette() {
    if (Riiablo.palettes == null) return null;
    switch (map.getAct()) {
      case 1: return Riiablo.palettes.act2;
      case 2: return Riiablo.palettes.act3;
      case 3: return Riiablo.palettes.act4;
      case 4: return Riiablo.palettes.act5;
      case 0:
      default: return Riiablo.palettes.act1;
    }
  }

  /** Builds a bounded probe over loaded native objects whose modes differ in collision. */
  private DynamicCollisionProbe beginDynamicCollisionProbe() {
    com.artemis.ComponentMapper<com.riiablo.engine.server.component.Object> objects =
        engine.getMapper(com.riiablo.engine.server.component.Object.class);
    com.artemis.ComponentMapper<CofReference> cofs = engine.getMapper(CofReference.class);
    com.artemis.ComponentMapper<Position> positions = engine.getMapper(Position.class);
    com.artemis.ComponentMapper<MapWrapper> wrappers = engine.getMapper(MapWrapper.class);
    com.artemis.ComponentMapper<NativeObjectState> states = engine.getMapper(NativeObjectState.class);
    DynamicCollisionProbe probe = new DynamicCollisionProbe();
    int scanned = 0;
    int objectSeen = 0;
    for (Map.Zone zone : map.getZones()) {
      for (int entity : zone.getEntities().toArray()) {
        scanned++;
        if (objects.has(entity)) objectSeen++;
        if (probe.candidates.size >= 1 || !objects.has(entity) || !cofs.has(entity)
            || !positions.has(entity)) continue;
        com.riiablo.engine.server.component.Object object = objects.get(entity);
        if (object.base == null || object.base.HasCollision == null
            || object.base.SizeX <= 0 || object.base.SizeY <= 0) continue;
        int solid = -1, open = -1;
        for (int mode = 0; mode < object.base.HasCollision.length; mode++) {
          if (object.base.HasCollision[mode]) solid = mode;
          else if (open < 0) open = mode;
        }
        if (open < 0 && solid != com.riiablo.engine.Engine.Object.MODE_OP) {
          open = com.riiablo.engine.Engine.Object.MODE_OP;
        }
        if (solid < 0 || open < 0) continue;
        Map.Zone objectZone = wrappers.has(entity) && wrappers.get(entity).zone != null
            ? wrappers.get(entity).zone : zone;
        Position position = positions.get(entity);
        int x = Math.round(position.position.x - object.base.SizeX / 2f);
        int y = Math.round(position.position.y - object.base.SizeY / 2f);
        if (objectZone == null) continue;
        boolean warpRoom = false;
        Map.RoomEx room = objectZone.findRoomEx(position.position.x, position.position.y);
        if (room != null) warpRoom = containsWarp(objectZone, room, positions);
        Candidate candidate = new Candidate(entity, objectZone, object.base.SizeX,
            object.base.SizeY, cofs.get(entity).mode, solid, open,
            object.base.IsDoor, warpRoom);
        candidate.state = states.has(entity) ? states.get(entity) : null;
        candidate.object = object;
        candidate.baselineReferences = candidate.collisionReferences();
        probe.candidates.add(candidate);
        dynamicObjectCandidates++;
        if (warpRoom) dynamicWarpRoomCandidates++;
        if (candidate.door) dynamicDoors++;
      }
    }
    // Native object lists are persistent, while their ECS entity index can be
    // outside a Zone's bookkeeping array during room activation. Fall back to
    // the live world index so the probe still covers the actual updater.
    if (probe.candidates.isEmpty()) {
      for (int entity = 0; entity < 4096 && probe.candidates.isEmpty(); entity++) {
        if (!engine.getEntityManager().isActive(entity) || !objects.has(entity)
            || !cofs.has(entity) || !positions.has(entity)) continue;
        Map.Zone zone = wrappers.has(entity) ? wrappers.get(entity).zone : null;
        if (zone == null) zone = map.getZone(positions.get(entity).position);
        if (zone == null) {
          for (Map.Zone candidateZone : map.getZones()) {
            if (candidateZone.levelId() == 1) {
              zone = candidateZone;
              break;
            }
          }
        }
        com.riiablo.engine.server.component.Object object = objects.get(entity);
        if (zone == null || object.base == null
            || object.base.HasCollision == null || object.base.SizeX <= 0
            || object.base.SizeY <= 0) continue;
        int solid = -1, open = -1;
        for (int mode = 0; mode < object.base.HasCollision.length; mode++) {
          if (object.base.HasCollision[mode]) solid = mode;
          else if (open < 0) open = mode;
        }
        if (open < 0 && solid != com.riiablo.engine.Engine.Object.MODE_OP) {
          open = com.riiablo.engine.Engine.Object.MODE_OP;
        }
        if (solid < 0 || open < 0) continue;
        Position position = positions.get(entity);
        boolean warpRoom = false;
        Map.RoomEx room = zone.findRoomEx(position.position.x, position.position.y);
        if (room != null) warpRoom = containsWarp(zone, room, positions);
        Candidate candidate = new Candidate(entity, zone, object.base.SizeX,
            object.base.SizeY, cofs.get(entity).mode, solid, open,
            object.base.IsDoor, warpRoom);
        candidate.state = states.has(entity) ? states.get(entity) : null;
        candidate.object = object;
        candidate.baselineReferences = candidate.collisionReferences();
        probe.candidates.add(candidate);
        dynamicObjectCandidates++;
        if (warpRoom) dynamicWarpRoomCandidates++;
        if (candidate.door) dynamicDoors++;
      }
    }
    if (probe.candidates.isEmpty()) {
      System.err.println("[OFFSCREEN_WARP_COLLISION] no candidate objects; zones=" + map.getZones().size
          + " scanned=" + scanned + " objectSeen=" + objectSeen);
      Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_WARP_COLLISION] scanned=" + scanned
          + " zones=" + map.getZones().size);
      dynamicCollisionSkipped = true;
      dynamicCollisionDetails.append("noLoadedDynamicObjects\n");
      probe.phase = 4;
      return probe;
    }
    for (Candidate candidate : probe.candidates) setProbeMode(candidate, candidate.solidMode);
    probe.phase = 1;
    return probe;
  }

  private void checkSolidCollisionPhase(DynamicCollisionProbe probe) {
    for (Candidate candidate : probe.candidates) {
      int refs = candidate.collisionReferences();
      dynamicCollisionChecks++;
      if (refs > 0) candidate.solidReferences = refs;
      else recordDynamicFailure(candidate, "solid-not-added");
    }
  }

  private void checkOpenCollisionPhase(DynamicCollisionProbe probe) {
    for (Candidate candidate : probe.candidates) {
      int refs = candidate.collisionReferences();
      dynamicCollisionChecks++;
      if (refs == candidate.baselineReferences) dynamicCollisionCleared++;
      else {
        dynamicCollisionLeaks++;
        recordDynamicFailure(candidate, "open-still-blocked refs=" + refs);
      }
    }
  }

  private void checkRestoredCollisionPhase(DynamicCollisionProbe probe) {
    for (Candidate candidate : probe.candidates) {
      int refs = candidate.collisionReferences();
      dynamicCollisionChecks++;
      boolean expected = candidate.originalMode >= 0
          && candidate.originalMode < candidate.object.base.HasCollision.length
          && candidate.object.base.HasCollision[candidate.originalMode];
      if ((expected && refs > 0) || (!expected && refs == candidate.baselineReferences)) dynamicCollisionRestored++;
      else recordDynamicFailure(candidate, "restore-mismatch refs=" + refs
          + " expectedSolid=" + expected);
    }
  }

  private void setProbeModes(DynamicCollisionProbe probe, int mode) {
    for (Candidate candidate : probe.candidates) setProbeMode(candidate, mode);
  }

  private void setOpenProbeModes(DynamicCollisionProbe probe) {
    for (Candidate candidate : probe.candidates) setProbeMode(candidate, candidate.openMode);
  }

  private void restoreProbeModes(DynamicCollisionProbe probe) {
    for (Candidate candidate : probe.candidates) setProbeMode(candidate, candidate.originalMode);
  }

  private void setProbeMode(Candidate candidate, int mode) {
    CofReference cof = engine.getMapper(CofReference.class).get(candidate.entity);
    if (cof != null) cof.mode = (byte) mode;
    candidate.object.mode = (byte) mode;
    if (candidate.state != null) candidate.state.currentMode = (byte) mode;
  }

  private void recordDynamicFailure(Candidate candidate, String reason) {
    dynamicCollisionFailures++;
    if (dynamicCollisionDetails.length() < 8000) {
      dynamicCollisionDetails.append("entity=").append(candidate.entity)
          .append(" level=").append(candidate.zone.levelId())
          .append(" door=").append(candidate.door)
          .append(" baselineRefs=").append(candidate.baselineReferences)
          .append(" reason=").append(reason).append('\n');
    }
  }

  private final class DynamicCollisionProbe {
    final com.badlogic.gdx.utils.Array<Candidate> candidates = new com.badlogic.gdx.utils.Array<>();
    int phase;
  }

  private final class Candidate {
    final int entity, width, height, originalMode, solidMode, openMode;
    final Map.Zone zone;
    final boolean door, warpRoom;
    com.riiablo.engine.server.component.Object object;
    NativeObjectState state;
    int baselineReferences;
    int solidReferences;

    Candidate(int entity, Map.Zone zone, int width, int height, int originalMode,
        int solidMode, int openMode, boolean door, boolean warpRoom) {
      this.entity = entity;
      this.zone = zone;
      this.width = width;
      this.height = height;
      this.originalMode = originalMode;
      this.solidMode = solidMode;
      this.openMode = openMode;
      this.door = door;
      this.warpRoom = warpRoom;
    }

    int collisionReferences() {
      Position position = engine.getMapper(Position.class).get(entity);
      if (position == null) return 0;
      int x = Math.round(position.position.x - width / 2f);
      int y = Math.round(position.position.y - height / 2f);
      int max = 0;
      for (int dy = 0; dy < height; dy++) {
        for (int dx = 0; dx < width; dx++) {
          max = Math.max(max, zone.objectCollisionReferences(x + dx, y + dy));
        }
      }
      return max;
    }
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
      throw new IllegalStateException("Offscreen level act does not match generated map: id="
          + targetLevelId + " targetAct=" + (target.Act + 1)
          + " generatedAct=" + (map.getAct() + 1));
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
    targetNativeCells = layer.floors.size + layer.roads.size + layer.walls.size
        + layer.objects.size + layer.extras.size;
    targetRoadCells = layer.roads.size;
    targetCellAudit = AutomapCellAudit.audit(layer);
    exportAutomapCells(layer, manager);
    exportAutomapTable();
    if (targetNativeCells == 0) {
      throw new IllegalStateException("Automap has no native cells for target level=" + targetLevelId);
    }
    if (targetLevelId == 2 && targetRoadCells == 0) {
      throw new IllegalStateException("Blood Moor Automap has no native DirtPathGrid cells");
    }
    // Underground sub-levels must expose at least one exported entrance/object
    // record; otherwise a successful Zone switch would hide a broken cave link.
    if ((targetLevelId == 8 || targetLevelId == 10) && targetNativeObjects == 0) {
      throw new IllegalStateException("Cave target has no native entrance objects: level=" + targetLevelId);
    }
    Gdx.app.log("OffscreenCampScreen", "[OFFSCREEN_AUTOMAP] level=" + targetLevelId
        + " cells=" + targetNativeCells + " roads=" + targetRoadCells
        + " rooms=" + targetRoomCount
        + " nativeObjects=" + targetNativeObjects);
    Gdx.app.log("OffscreenCampScreen", "[AUTOMAP_CELL_AUDIT] level=" + targetLevelId
        + " total=" + targetCellAudit.total
        + " within=" + targetCellAudit.withinCategoryExact
        + " cross=" + targetCellAudit.crossCategoryExact
        + " positionConflict=" + targetCellAudit.samePositionDifferentCell
        + " sameCategoryConflict=" + targetCellAudit.sameCategoryPositionConflict
        + " crossCategoryConflict=" + targetCellAudit.crossCategoryPositionConflict
        + " samples=" + targetCellAudit.samples);
  }

  private void exportAutomapCells(AutomapLayer layer, AutomapManager manager) {
    StringBuilder csv = new StringBuilder(
        "category,cellNo,worldX,worldY,maX,maY,sourceMapLayer,tileName,orientation,style,sequence\n");
    appendAutomapCells(csv, "floors", layer.floors, manager);
    appendAutomapCells(csv, "roads", layer.roads, manager);
    appendAutomapCells(csv, "walls", layer.walls, manager);
    appendAutomapCells(csv, "objects", layer.objects, manager);
    appendAutomapCells(csv, "extras", layer.extras, manager);
    com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    output.child("automap-cells-level-" + targetLevelId + ".csv")
        .writeString(csv.toString(), false, "UTF-8");
    Gdx.app.log("OffscreenCampScreen", "[AUTOMAP_CELL_EXPORT] path=" + output.path()
        + " cells=" + (layer.floors.size + layer.roads.size + layer.walls.size
        + layer.objects.size + layer.extras.size));
  }

  private void appendAutomapCells(StringBuilder csv, String category,
      com.badlogic.gdx.utils.Array<AutomapCell> cells, AutomapManager manager) {
    for (AutomapCell cell : cells) {
      String source = automapCellSource(cell, manager);
      csv.append(category).append(',').append(cell.cellNo).append(',')
          .append(cell.xPixel).append(',').append(cell.yPixel).append(',')
          .append(AutomapCellAudit.maX(cell.xPixel, cell.yPixel)).append(',')
          .append(AutomapCellAudit.maY(cell.xPixel, cell.yPixel)).append(',')
          .append(source).append('\n');
    }
  }

  private String automapCellSource(AutomapCell cell, AutomapManager manager) {
    if (targetZone == null || targetZone.automapLevelName() == null) return ",,,,";
    int tx = AutomapProjection.tileIndex(cell.xPixel);
    int ty = AutomapProjection.tileIndex(cell.yPixel);
    long seed = targetLevelId ^ (cell.xPixel * 31L + cell.yPixel);
    for (int mapLayer = 0; mapLayer < Map.MAX_LAYERS; mapLayer++) {
      com.riiablo.map.DT1.Tile tile = targetZone.get(mapLayer, tx, ty);
      if (tile == null) continue;
      String tileName = com.riiablo.engine.client.automap.AutomapTileRenderer
          .tileNameForOrientation(tile.orientation);
      if (tileName == null) continue;
      int resolved = manager.getTileRenderer().getAutomapCellId(
          targetZone.automapLevelName(), tileName, tile.mainIndex, tile.subIndex, seed);
      if (resolved != cell.cellNo) continue;
      return mapLayer + "," + tileName + "," + tile.orientation + ","
          + tile.mainIndex + "," + tile.subIndex;
    }
    return ",,,,";
  }

  private void exportAutomapTable() {
    if (targetZone == null || targetZone.automapLevelName() == null
        || Riiablo.files == null || Riiablo.files.AutoMap == null) return;
    String levelName = targetZone.automapLevelName();
    StringBuilder csv = new StringBuilder(
        "row,levelName,tileName,style,startSequence,endSequence,cel1,cel2,cel3,cel4\n");
    int row = 0;
    for (com.riiablo.codec.excel.AutoMap.Entry entry : Riiablo.files.AutoMap) {
      if (entry.LevelName == null || !entry.LevelName.trim().equalsIgnoreCase(levelName.trim())) {
        row++;
        continue;
      }
      csv.append(row).append(',').append(csvValue(entry.LevelName)).append(',')
          .append(csvValue(entry.TileName)).append(',').append(entry.Style).append(',')
          .append(entry.StartSequence).append(',').append(entry.EndSequence);
      for (int i = 0; i < 4; i++) {
        csv.append(',').append(entry.Cel != null && i < entry.Cel.length ? entry.Cel[i] : -1);
      }
      csv.append('\n');
      row++;
    }
    com.badlogic.gdx.files.FileHandle output = Gdx.files.absolute(outputDirectory);
    output.mkdirs();
    output.child("automap-table-level-" + targetLevelId + ".csv")
        .writeString(csv.toString(), false, "UTF-8");
  }
}
