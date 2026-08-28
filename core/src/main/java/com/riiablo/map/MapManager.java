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
    for (IntMap.Entry<DS1.Cell> entry : specials.entries()) {
      DS1.Cell cell = entry.value;
      if (Map.ID.WARPS.contains(cell.id)) {
        int hash = entry.key;
        int x = zone.x + (Map.Zone.tileHashX(hash) * DT1.Tile.SUBTILE_SIZE);
        int y = zone.y + (Map.Zone.tileHashY(hash) * DT1.Tile.SUBTILE_SIZE);
        int id = factory.createWarp(cell.id, x, y);
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

  public void createEntities(Map.Zone zone) {
    createNativeObjects(zone);

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

  private void createNativeObjects(Map.Zone zone) {
    int created = 0;
    int failed = 0;
    int skipped = 0;
    IntSet roomsInBatch = new IntSet();
    for (Map.NativeObject object : zone.nativeObjects) {
      if (object.spawned) {
        // D2Game::SUNIT_SpawnPresetUnitsInRoom ignores units already marked
        // as spawned. Creating them again would duplicate generated objects.
        skipped++;
        continue;
      }

      int worldX = zone.x + object.x;
      int worldY = zone.y + object.y;
      Map.RoomEx room = zone.findRoomEx(worldX, worldY);
      if (room != null && room.isPresetUnitsSpawned() && !roomsInBatch.contains(room.id)) {
        skipped++;
        continue;
      }
      if (room != null) roomsInBatch.add(room.id);

      // DS1 stores Act as zero-based in the file and riiablo's loader exposes
      // it as one-based. Act I therefore uses table section 1 here. Units
      // generated after DS1 loading carry a direct Objects.txt class id.
      int objectId = object.ds1Raw
          ? resolveDs1ObjectId(1, object.presetIndex)
          : object.presetIndex;
      NativePresetObjectResolver.Resolution resolution =
          NativePresetObjectResolver.resolve(1, zone.level.Id, objectId,
              map.seed, object.x, object.y);
      if (!resolution.shouldCreate()) {
        skipped++;
        Gdx.app.debug(TAG, String.format(
            "Skipping D2MOO native object: level=%s(%d) presetIndex=%d classId=%d ds1Raw=%s spawned=%s",
            zone.level.LevelName, zone.level.Id, object.presetIndex, objectId,
            object.ds1Raw, object.spawned));
        continue;
      }
      int resolvedObjectId = resolution.classId;
      if (resolvedObjectId != objectId
          || resolution.kind != NativePresetObjectResolver.Kind.ORDINARY) {
        Gdx.app.log(TAG, String.format(
            "Resolved D2MOO native preset object: level=%s(%d) presetIndex=%d objectId=%d -> classId=%d kind=%s ds1Raw=%s local=(%d,%d)",
            zone.level.LevelName, zone.level.Id, object.presetIndex, objectId,
            resolvedObjectId, resolution.kind, object.ds1Raw, object.x, object.y));
      }
      int id = object.ds1Raw && resolvedObjectId == objectId
          ? factory.createObject(1, DS1.Object.STATIC_TYPE, object.presetIndex,
              worldX, worldY)
          : factory.createStaticObjectByClassId(
              resolvedObjectId, worldX, worldY);
      if (id == Engine.INVALID_ENTITY) {
        failed++;
        Gdx.app.error(TAG, String.format(
            "Unable to create D2MOO native object: level=%s(%d) presetIndex=%d objectId=%d resolvedObjectId=%d mode=%d "
                + "local=(%d,%d) world=(%d,%d)",
            zone.level.LevelName, zone.level.Id, object.presetIndex, objectId,
            resolvedObjectId, object.mode,
            object.x, object.y, zone.x + object.x, zone.y + object.y));
      } else {
        // Native exports already tell us the owning level. Do not resolve it
        // again from coordinates: adjacent/overlapping zone bounds can make a
        // waypoint activate the wrong Levels.txt record.
        mMapWrapper.create(id).set(map, zone);
        NativeObjectState nativeState = mNativeObjectState.create(id).set(object,
            object.presetIndex, objectId,
            resolvedObjectId, object.mode, object.ds1Raw, object.spawned, resolution.kind);
        CofReference nativeCof = mCofReference.get(id);
        if (nativeCof != null && nativeState.currentMode >= Engine.Object.MODE_NU
            && nativeState.currentMode <= Engine.Object.MODE_S5) {
          nativeCof.mode = nativeState.currentMode;
        }
        prepareWaypointInitialState(id, zone);
        zone.addEntity(id);
        created++;
      }
    }
    for (IntSet.IntSetIterator it = roomsInBatch.iterator(); it.hasNext; ) {
      int roomId = it.next();
      if (roomId >= 0 && roomId < zone.roomsEx.size) {
        zone.roomsEx.get(roomId).markPresetUnitsSpawned();
      }
    }
    if (zone.nativeObjects.size > 0) {
      Gdx.app.log(TAG, String.format(
          "D2MOO native objects: level=%s(%d) exported=%d created=%d failed=%d",
          zone.level.LevelName, zone.level.Id, zone.nativeObjects.size, created, failed));
      if (skipped > 0) {
        Gdx.app.debug(TAG, String.format(
            "D2MOO native objects skipped: level=%s(%d) skipped=%d",
            zone.level.LevelName, zone.level.Id, skipped));
      }
    }
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

    for (int i = 0; i < zone.entities.size; i++) {
      int entityId = zone.entities.get(i);
      Object object = mObject.get(entityId);
      Position position = mPosition.get(entityId);
      if (object == null || position == null
          || (object.base.SubClass & Engine.Object.SUBCLASS_WAYPOINT) == 0) {
        continue;
      }

      return copyWaypointCenter(position.position, out);
    }

    Gdx.app.error(TAG, "Waypoint entity not found: level=" + level.LevelName
        + "(" + level.Id + ")");
    return null;
  }

  static Vector2 copyWaypointCenter(Vector2 waypoint, Vector2 out) {
    return out.set(waypoint);
  }
}
