package com.riiablo.map;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.object.NativeObjectOperateTable;
import com.riiablo.engine.server.object.NativeObjectOperateTable.Lifecycle;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;

import net.mostlyoriginal.api.system.core.PassiveSystem;

public class MapManager extends PassiveSystem {
  private static final String TAG = "MapManager";

  @Wire(name = "map")
  protected Map map;

  @Wire(name = "factory")
  protected EntityFactory factory;

  protected ComponentMapper<Object> mObject;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;

  public void createEntities() {
    for (Map.Zone zone : new Array.ArrayIterator<>(map.zones)) {
      createWarps(zone);
      createEntities(zone);
    }
  }

  private void createWarps(Map.Zone zone) {
    IntMap<DS1.Cell> specials = zone.specials;
    IntSet act3WarpSlots = zone.level != null && zone.level.Id >= 75 && zone.level.Id <= 102
        ? new IntSet() : null;
    for (IntMap.Entry<DS1.Cell> entry : specials.entries()) {
      DS1.Cell cell = entry.value;
      if (Map.ID.WARPS.contains(cell.id)) {
        int mainIndex = DT1.Tile.Index.mainIndex(cell.id);
        int destinationOverride = zone.level == null ? -1
            : map.getWarpDestinationOverride(zone.level.Id, mainIndex);
        if (!hasWarpDestination(zone.level, mainIndex, destinationOverride)) {
          Gdx.app.debug(TAG, String.format(
              "Skipping unbound warp marker level=%s(%d) mainIndex=%d special=0x%08X",
              zone.level == null ? "null" : zone.level.LevelName,
              zone.level == null ? -1 : zone.level.Id, mainIndex, cell.id));
          continue;
        }
        if (act3WarpSlots != null) {
          // RoomEx exports may contain several wall components for one
          // logical LvlWarp slot.  D2Game creates one interactive warp per
          // slot; deduplicate the visual components before creating entities.
          if (!act3WarpSlots.add(mainIndex)) continue;
        }
        int hash = entry.key;
        int x = zone.x + (Map.Zone.tileHashX(hash) * DT1.Tile.SUBTILE_SIZE);
        int y = zone.y + (Map.Zone.tileHashY(hash) * DT1.Tile.SUBTILE_SIZE);
        int id = factory.createWarp(zone, cell.id, x, y);
        // Native wall markers may be authored on the exclusive outer edge of
        // a Zone. Keep the owning Zone hint on every retry; only the visual
        // anchor moves inward, otherwise overlapping Act I rectangles can
        // resolve the retry as a different level.
        if (id == Engine.INVALID_ENTITY) {
          int inwardX = x > zone.x() ? x - 1 : x + 1;
          int inwardY = y > zone.y() ? y - 1 : y + 1;
          if (zone.contains(inwardX, y)) id = factory.createWarp(zone, cell.id, inwardX, y);
          if (id == Engine.INVALID_ENTITY && zone.contains(x, inwardY)) {
            id = factory.createWarp(zone, cell.id, x, inwardY);
          }
          // A reduced DS1 export can put a Warp marker on a corner where no
          // neighboring subtile belongs to the Zone (for example the native
          // Frigid Highlands barricade). Resolve the source metadata from a
          // guaranteed interior coordinate as a final fallback. The visual
          // marker remains at its authored location; only the factory lookup
          // coordinate is adjusted.
          if (id == Engine.INVALID_ENTITY && zone.width() > 2 && zone.height() > 2) {
            int centerX = zone.x() + zone.width() / 2;
            int centerY = zone.y() + zone.height() / 2;
            if (zone.contains(centerX, centerY)) {
              id = factory.createWarp(zone, cell.id, centerX, centerY);
            }
          }
        }
        if (id != Engine.INVALID_ENTITY) {
          zone.addWarp(id);
        } else {
          Gdx.app.error(TAG, String.format(
              "Unable to create warp level=%s(%d) special=0x%08X mainIndex=%d pos=(%d,%d)",
              zone.level.LevelName, zone.level.Id, cell.id, cell.mainIndex, x, y));
        }
      }
    }
  }

  static boolean hasWarpDestination(
      Levels.Entry level, int mainIndex, int destinationOverride) {
    if (destinationOverride > 0) return true;
    return level != null
        && level.Vis != null
        && level.Warp != null
        && mainIndex >= 0
        && mainIndex < level.Vis.length
        && mainIndex < level.Warp.length
        && level.Vis[mainIndex] > 0
        && level.Warp[mainIndex] >= 0;
  }

  static boolean isWaypointOwnedByZone(Map.Zone entityZone, Map.Zone requestedZone) {
    return entityZone != null && entityZone == requestedZone;
  }

  public void createEntities(Map.Zone zone) {
    if (shouldCreateNativeObjectsImmediately(zone)) {
      createNativeObjects(zone);
    } else {
      // Native D2Game calls SUNIT_SpawnPresetUnitsInRoom as RoomEx enters
      // the active client ring. Only malformed exports outside every RoomEx
      // need an eager compatibility pass here.
      createNativeObjects(zone, null, true);
    }

    // 只对城镇区域创建 NPC 和其他对象
    // 野外区域的对象应该通过 generator 或其他方式创建
    if (!zone.town) {
      // Outdoor waypoint presets contain the actual selectable DS1 object.
      // Keep skipping monsters/NPCs here, but do not discard the waypoint.
      createPresetEntities(zone, true);
      return;
    }

    createPresetEntities(zone, false);
  }

  private void createPresetEntities(Map.Zone zone, boolean waypointsOnly) {
    for (int x = 0, gridX = 0, gridY = 0; x < zone.gridsX; x++, gridX += zone.gridSizeX, gridY = 0) {
      for (int y = 0; y < zone.gridsY; y++, gridY += zone.gridSizeY) {
        Map.Preset preset = zone.presets[x][y];
        if (preset == null) continue;
        createEntities(zone, preset, gridX, gridY, waypointsOnly);
      }
    }
  }

  static boolean shouldCreateNativeObjectsImmediately(Map.Zone zone) {
    return zone == null || zone.town || !zone.hasNativeRoomTopology();
  }

  public void createNativeObjects(Map.Zone zone) {
    createNativeObjects(zone, null, false);
  }

  /** D2Game SUNIT_SpawnPresetUnitsInRoom equivalent for one activated RoomEx. */
  public void createNativeObjects(Map.Zone zone, Map.RoomEx onlyRoom) {
    if (zone == null || onlyRoom == null || onlyRoom.isPresetUnitsSpawned()) return;
    createNativeObjects(zone, onlyRoom, false);
    // The native flag records that the room was processed, including rooms
    // which contained no preset objects.
    onlyRoom.markPresetUnitsSpawned();
  }

  private void createNativeObjects(
      Map.Zone zone, Map.RoomEx onlyRoom, boolean outsideRoomsOnly) {
    // Objects.txt is split by act. Native DS1 exports carry the zero-based
    // Levels.txt act, while the table loader uses the original one-based
    // section (Act I = 1).
    final int objectAct = zone.level != null ? zone.level.Act + 1 : 1;
    int created = 0;
    int failed = 0;
    int skipped = 0;
    for (Map.NativeObject object : zone.nativeObjects) {
      if (!object.externalEntity) {
        object.creationStatus = "skipped_external_entity_false";
        skipped++;
        continue;
      }
      // Offline D2MOO export initializes every RoomEx to obtain its final
      // preset list. That native initialization can make a room look
      // "spawned" before any ECS object exists. The per-preset status is the
      // authoritative idempotence guard for the Java bridge; using the room
      // flag here would permanently drop deferred objects in streamed levels
      // (including Den of Evil's interactive corpses).
      if (!"PENDING".equals(object.creationStatus)) {
        skipped++;
        continue;
      }
      int worldX = zone.x + object.x;
      int worldY = zone.y + object.y;
      Map.RoomEx room = zone.findRoomEx(worldX, worldY);
      if (outsideRoomsOnly && room != null) continue;
      if (!outsideRoomsOnly && onlyRoom != null && room != onlyRoom) continue;
      if (object.spawned) {
        // D2Game::SUNIT_SpawnPresetUnitsInRoom ignores units already marked
        // as spawned. Creating them again would duplicate generated objects.
        skipped++;
        object.creationStatus = "skipped_spawned";
        continue;
      }

      // DS1 stores Act as zero-based in the file and riiablo's loader exposes
      // it as one-based. Act I therefore uses table section 1 here. Units
      // generated after DS1 loading carry a direct Objects.txt class id.
      int objectId = object.ds1Raw
          ? resolveDs1ObjectId(objectAct, object.presetIndex)
          : object.presetIndex;
      NativePresetObjectResolver.Resolution resolution =
          NativePresetObjectResolver.resolve(objectAct, zone.level.Id, objectId,
              map.seed, object.x, object.y);
      if (!resolution.shouldCreate()) {
        skipped++;
        object.resolvedObjectId = resolution.classId;
        object.resolverKind = resolution.kind.name();
        object.creationStatus = "skipped_resolver";
        Gdx.app.debug(TAG, String.format(
            "Skipping D2MOO native object: level=%s(%d) presetIndex=%d classId=%d ds1Raw=%s spawned=%s",
            zone.level.LevelName, zone.level.Id, object.presetIndex, objectId,
            object.ds1Raw, object.spawned));
        continue;
      }
      int resolvedObjectId = resolution.classId;
      object.resolvedObjectId = resolvedObjectId;
      object.resolverKind = resolution.kind.name();
      if (resolvedObjectId != objectId
          || resolution.kind != NativePresetObjectResolver.Kind.ORDINARY) {
        Gdx.app.log(TAG, String.format(
            "Resolved D2MOO native preset object: level=%s(%d) presetIndex=%d objectId=%d -> classId=%d kind=%s ds1Raw=%s local=(%d,%d)",
            zone.level.LevelName, zone.level.Id, object.presetIndex, objectId,
            resolvedObjectId, resolution.kind, object.ds1Raw, object.x, object.y));
      }
      int id = object.ds1Raw && resolvedObjectId == objectId
          ? factory.createObject(objectAct, DS1.Object.STATIC_TYPE, object.presetIndex,
              worldX, worldY)
          : factory.createStaticObjectByClassId(
              resolvedObjectId, worldX, worldY);
      if (id == Engine.INVALID_ENTITY) {
        failed++;
        object.creationStatus = "failed_factory";
        Gdx.app.error(TAG, String.format(
            "Unable to create D2MOO native object: level=%s(%d) presetIndex=%d objectId=%d resolvedObjectId=%d mode=%d "
                + "local=(%d,%d) world=(%d,%d)",
            zone.level.LevelName, zone.level.Id, object.presetIndex, objectId,
            resolvedObjectId, object.mode,
            object.x, object.y, zone.x + object.x, zone.y + object.y));
      } else {
        object.entityId = id;
        object.creationStatus = "created";
        // Native exports already tell us the owning level. Do not resolve it
        // again from coordinates: adjacent/overlapping zone bounds can make a
        // waypoint activate the wrong Levels.txt record.
        mMapWrapper.create(id).set(map, zone);
        NativeObjectState nativeState = mNativeObjectState.create(id).set(object,
            object.presetIndex, objectId,
            resolvedObjectId, object.mode, object.ds1Raw, object.spawned, resolution.kind);
        // Resolve the gameplay shrine before the client presentation is built.
        // The DS1 preset class (574..579) is only a spawn selector; the
        // renderable object is the shared Shrine row.  Keeping the selected
        // Shrines.txt row in the map-owned state makes the type deterministic
        // across room re-entry and lets the client show the matching native
        // shrine glyph before the first interaction.
        if (resolution.kind == NativePresetObjectResolver.Kind.SHRINE
            && nativeState.shrineId < 0 && Riiablo.files.Shrines != null) {
          int shrineId = com.riiablo.engine.server.object.NativeShrineResolver.resolve(
              Riiablo.files.Shrines, nativeBaseFor(resolvedObjectId), objectId,
              zone.level.Id, map.seed, object.x, object.y);
          nativeState.persistShrineId(shrineId);
        }
        CofReference nativeCof = mCofReference.get(id);
        // D2MOO's ShrineW/ShrineD/ShrineF/ShrineH substitutions can carry the
        // transient OP mode from their DS1 preset.  OP is the activation
        // sequence, not the idle visual state; a fresh/unactivated shrine must
        // always enter NU before its COF is exposed to the renderer.
        Objects.Entry nativeBase = Riiablo.files.objects.get(resolvedObjectId);
        if (!nativeState.activated
          && NativeObjectOperateTable.resolve(nativeBase, resolution.kind)
                == Lifecycle.SHRINE) {
          nativeState.persistMode(Engine.Object.MODE_NU);
        }
        if (nativeCof != null && nativeState.currentMode >= Engine.Object.MODE_NU
            && nativeState.currentMode <= Engine.Object.MODE_S5) {
          nativeCof.mode = nativeState.currentMode;
        }
        prepareWaypointInitialState(id, zone);
        zone.addEntity(id);
        created++;
      }
    }
    if (onlyRoom != null) {
      // Keep the native one-shot room marker for RoomActivationSystem. It no
      // longer decides whether exported objects are created; their individual
      // creationStatus values make re-entry idempotent without losing the
      // initial room snapshot.
      onlyRoom.markPresetUnitsSpawned();
    }
    if (zone.nativeObjects.size > 0 && (onlyRoom == null || created + failed + skipped > 0)) {
      Gdx.app.log(TAG, String.format(
          "D2MOO native objects: level=%s(%d) room=%d exported=%d created=%d failed=%d",
          zone.level.LevelName, zone.level.Id, onlyRoom != null ? onlyRoom.id : -1,
          zone.nativeObjects.size, created, failed));
      if (skipped > 0) {
        Gdx.app.debug(TAG, String.format(
            "D2MOO native objects skipped: level=%s(%d) skipped=%d",
            zone.level.LevelName, zone.level.Id, skipped));
      }
    }
  }

  private static com.riiablo.codec.excel.Objects.Entry nativeBaseFor(int objectId) {
    return Riiablo.files == null || Riiablo.files.objects == null
        ? null : Riiablo.files.objects.get(objectId);
  }

  private static int resolveDs1ObjectId(int act, int presetIndex) {
    if (presetIndex < 0 || presetIndex >= Riiablo.files.obj.getSize(act)) return 573;
    return Riiablo.files.obj.getObjectId(act, presetIndex);
  }

  /** Backwards-compatible test hook for D2Game native object resolution. */
  static int resolveNativeObjectClassId(int levelId, int objectId, int seed,
      int localX, int localY) {
    return NativePresetObjectResolver.resolve(1, levelId, objectId, seed, localX, localY).classId;
  }

  private void createEntities(
      Map.Zone zone, Map.Preset preset, int gridX, int gridY, boolean waypointsOnly) {
    final int x = zone.x + (gridX * DT1.Tile.SUBTILE_SIZE);
    final int y = zone.y + (gridY * DT1.Tile.SUBTILE_SIZE);
    DS1 ds1 = preset.ds1;
    for (int i = 0, size = ds1.numObjects; i < size; i++) {
      DS1.Object object = ds1.objects[i];
      if (waypointsOnly && !isWaypoint(ds1, object)) continue;
      int id = factory.createObject(preset, object, x + object.x, y + object.y);
      if (id != Engine.INVALID_ENTITY) {
        prepareWaypointInitialState(id, zone);
        zone.entities.add(id);
      }
    }
  }

  /**
   * Finalizes a waypoint's owning level and persisted visual mode before the
   * first ECS process cycle starts loading its COF.
   *
   * <p>New characters already own the town waypoint. Previously every object
   * was inserted as NU and {@code ObjectInitializer} immediately changed the
   * town waypoint to ON. That queued two different COFs in one insertion
   * cycle and could leave the object interactable but visually absent. Native
   * outdoor exports also need the explicit {@code zone}, because overlapping
   * bounds make coordinate-only ownership ambiguous.</p>
   */
  private void prepareWaypointInitialState(int id, Map.Zone zone) {
    Object object = mObject.get(id);
    if (object == null || object.base == null
        || (object.base.SubClass & Engine.Object.SUBCLASS_WAYPOINT) == 0) {
      return;
    }

    mMapWrapper.create(id).set(map, zone);
    CofReference reference = mCofReference.get(id);
    if (reference == null) return;

    Levels.Entry level = zone != null ? zone.level : null;
    boolean active = level != null
        && level.Waypoint != 0xFF
        && Riiablo.charData != null
        && Riiablo.charData.isWaypointActivated(level.Act, level.Waypoint);
    reference.mode = resolveWaypointInitialMode(active);
    Gdx.app.log(TAG, String.format(
        "Waypoint initial visual state: entity=%d level=%s(%d) active=%s mode=%s",
        id, level == null ? "null" : level.LevelName, level == null ? -1 : level.Id,
        active, active ? "ON" : "NU"));
  }

  static byte resolveWaypointInitialMode(boolean active) {
    return active ? Engine.Object.MODE_ON : Engine.Object.MODE_NU;
  }

  private boolean isWaypoint(DS1 ds1, DS1.Object object) {
    if (object.type != DS1.Object.STATIC_TYPE) return false;
    int objectId = Riiablo.files.obj.getObjectId(ds1.getAct(), object.id);
    Objects.Entry base = Riiablo.files.objects.get(objectId);
    return base != null
        && (base.SubClass & Engine.Object.SUBCLASS_WAYPOINT) != 0;
  }

  /** Finds the exact object center of the waypoint in {@code level}. */
  public Vector2 findWaypointPosition(Levels.Entry level, Vector2 out) {
    Map.Zone zone = map.findZone(level);
    if (zone == null) return null;

    Vector2 position = findSpawnedWaypointPosition(zone, out);
    if (position != null) return position;

    Map.RoomEx waypointRoom = findNativeWaypointRoom(zone);
    if (waypointRoom != null && !waypointRoom.isPresetUnitsSpawned()) {
      Gdx.app.log(TAG, "Materializing waypoint room for travel: level=" + level.LevelName
          + "(" + level.Id + ") room=" + waypointRoom.id);
      createNativeObjects(zone, waypointRoom);
      position = findSpawnedWaypointPosition(zone, out);
      if (position != null) return position;
    }

    Gdx.app.error(TAG, "Waypoint entity not found: level=" + level.LevelName
        + "(" + level.Id + ")");
    return null;
  }

  private Vector2 findSpawnedWaypointPosition(Map.Zone zone, Vector2 out) {
    for (int i = 0; i < zone.entities.size; i++) {
      int entityId = zone.entities.get(i);
      Object object = mObject.get(entityId);
      Position position = mPosition.get(entityId);
      MapWrapper wrapper = mMapWrapper.get(entityId);
      if (object == null || position == null
          || wrapper == null || !isWaypointOwnedByZone(wrapper.zone, zone)
          || (object.base.SubClass & Engine.Object.SUBCLASS_WAYPOINT) == 0) {
        if (object != null && position != null
            && (object.base.SubClass & Engine.Object.SUBCLASS_WAYPOINT) != 0
            && wrapper != null && !isWaypointOwnedByZone(wrapper.zone, zone)) {
          Gdx.app.debug(TAG, String.format(
              "Ignoring waypoint entity from another zone: entity=%d expected=%s(%d) actual=%s(%d)",
              entityId,
              zone.level == null ? "null" : zone.level.LevelName,
              zone.level == null ? -1 : zone.level.Id,
              wrapper.zone == null || wrapper.zone.level == null
                  ? "null" : wrapper.zone.level.LevelName,
              wrapper.zone == null || wrapper.zone.level == null
                  ? -1 : wrapper.zone.level.Id));
        }
        continue;
      }

      return copyWaypointCenter(position.position, out);
    }
    return null;
  }

  /** Locates the deferred native RoomEx containing this level's waypoint. */
  private Map.RoomEx findNativeWaypointRoom(Map.Zone zone) {
    if (zone == null || zone.level == null || !zone.hasNativeRoomTopology()) return null;
    final int objectAct = zone.level.Act + 1;
    for (int i = 0; i < zone.nativeObjects.size; i++) {
      Map.NativeObject nativeObject = zone.nativeObjects.get(i);
      int objectId = nativeObject.ds1Raw
          ? resolveDs1ObjectId(objectAct, nativeObject.presetIndex)
          : nativeObject.presetIndex;
      NativePresetObjectResolver.Resolution resolution =
          NativePresetObjectResolver.resolve(objectAct, zone.level.Id, objectId,
              map.seed, nativeObject.x, nativeObject.y);
      if (!resolution.shouldCreate()) continue;
      Objects.Entry base = Riiablo.files.objects.get(resolution.classId);
      if (base == null || (base.SubClass & Engine.Object.SUBCLASS_WAYPOINT) == 0) continue;
      return zone.findRoomEx(zone.x + nativeObject.x, zone.y + nativeObject.y);
    }
    return null;
  }

  static Vector2 copyWaypointCenter(Vector2 waypoint, Vector2 out) {
    return out.set(waypoint);
  }
}
