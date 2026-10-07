package com.riiablo.map;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.riiablo.camera.IsometricCamera;

/**
 * Presentation-only RoomEx interest derived from the camera footprint.
 *
 * <p>This class deliberately does not touch RoomEx activation references.
 * Server visibility, AI and combat continue to use CLIENT_IN_ROOM and
 * CLIENT_IN_SIGHT independently.</p>
 */
final class RenderInterest {
  static final int DEFAULT_ADJACENT_RINGS = 1;

  private final int adjacentRings;
  private final ObjectMap<Map.Zone, ZoneState> states = new ObjectMap<>();
  private final ObjectSet<Map.Zone> nativeZones = new ObjectSet<>();
  private final Array<Map.Zone> staleZones = new Array<>(false, 8);
  private final Vector2 projected = new Vector2();
  private boolean initialized;
  private int seedRoomCount;
  private int interestedRoomCount;

  RenderInterest() {
    this(DEFAULT_ADJACENT_RINGS);
  }

  RenderInterest(int adjacentRings) {
    if (adjacentRings < 0) throw new IllegalArgumentException("adjacentRings < 0");
    this.adjacentRings = adjacentRings;
  }

  void update(Map map, Map.Zone anchor, IsometricCamera camera,
      float cameraMinX, float cameraMinY, float cameraMaxX, float cameraMaxY) {
    beginUpdate();
    if (map != null && camera != null) {
      Array<Map.Zone> zones = map.getZones();
      for (int zoneIndex = 0; zoneIndex < zones.size; zoneIndex++) {
        Map.Zone zone = zones.get(zoneIndex);
        if (zone == null || !zone.hasNativeRoomTopology()) continue;
        nativeZones.add(zone);
        if (anchor != null && zone != anchor && !map.areZonesAdjacent(anchor, zone)) continue;
        Array<Map.RoomEx> rooms = zone.getRoomsEx();
        for (int roomIndex = 0; roomIndex < rooms.size; roomIndex++) {
          Map.RoomEx room = rooms.get(roomIndex);
          if (intersectsCamera(room, camera,
              cameraMinX, cameraMinY, cameraMaxX, cameraMaxY, projected)) {
            seed(zone, room.id);
          }
        }
      }
    }
    endUpdate();
  }

  void beginUpdate() {
    for (ZoneState state : states.values()) state.used = false;
    nativeZones.clear();
    seedRoomCount = 0;
    interestedRoomCount = 0;
  }

  void seed(Map.Zone zone, int roomId) {
    if (zone == null || roomId < 0 || roomId >= zone.getRoomsEx().size) return;
    if (!nativeZones.contains(zone)) {
      if (!zone.hasNativeRoomTopology()) return;
      nativeZones.add(zone);
    }
    ZoneState state = states.get(zone);
    if (state == null) {
      state = new ZoneState();
      states.put(zone, state);
    }
    if (!state.used) state.prepare(zone.getRoomsEx().size);
    state.used = true;
    state.seeds.set(roomId);
  }

  void endUpdate() {
    staleZones.clear();
    for (ObjectMap.Entry<Map.Zone, ZoneState> entry : states.entries()) {
      if (!entry.value.used) {
        staleZones.add(entry.key);
        continue;
      }
      entry.value.expand(entry.key, adjacentRings);
      seedRoomCount += count(entry.value.seeds);
      interestedRoomCount += count(entry.value.interested);
    }
    for (int i = 0; i < staleZones.size; i++) states.remove(staleZones.get(i));
    initialized = true;
  }

  boolean contains(Map.Zone zone, int roomId) {
    if (!initialized || zone == null || !nativeZones.contains(zone)) return true;
    if (roomId < 0 || roomId >= zone.getRoomsEx().size) return true;
    ZoneState state = states.get(zone);
    return state != null && state.interested.get(roomId);
  }

  boolean contains(Map.Zone zone, float worldX, float worldY) {
    if (!initialized || zone == null || !nativeZones.contains(zone)) return true;
    Map.RoomEx room = zone.findRoomEx(worldX, worldY);
    return room == null || contains(zone, room.id);
  }

  int adjacentRings() {
    return adjacentRings;
  }

  int seedRoomCount() {
    return seedRoomCount;
  }

  int interestedRoomCount() {
    return interestedRoomCount;
  }

  static boolean intersectsCamera(Map.RoomEx room, IsometricCamera camera,
      float cameraMinX, float cameraMinY, float cameraMaxX, float cameraMaxY) {
    return intersectsCamera(room, camera,
        cameraMinX, cameraMinY, cameraMaxX, cameraMaxY, new Vector2());
  }

  private static boolean intersectsCamera(Map.RoomEx room, IsometricCamera camera,
      float cameraMinX, float cameraMinY, float cameraMaxX, float cameraMaxY,
      Vector2 point) {
    camera.toScreen(room.x, room.y, point);
    float minX = point.x;
    float maxX = point.x;
    float minY = point.y;
    float maxY = point.y;

    camera.toScreen(room.x + room.width, room.y, point);
    minX = Math.min(minX, point.x);
    maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y);
    maxY = Math.max(maxY, point.y);
    camera.toScreen(room.x, room.y + room.height, point);
    minX = Math.min(minX, point.x);
    maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y);
    maxY = Math.max(maxY, point.y);
    camera.toScreen(room.x + room.width, room.y + room.height, point);
    minX = Math.min(minX, point.x);
    maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y);
    maxY = Math.max(maxY, point.y);

    return minX <= cameraMaxX && maxX >= cameraMinX
        && minY <= cameraMaxY && maxY >= cameraMinY;
  }

  private static int count(Bits bits) {
    int count = 0;
    for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1)) count++;
    return count;
  }

  private static final class ZoneState {
    final Bits seeds = new Bits();
    final Bits interested = new Bits();
    final IntArray queue = new IntArray(false, 16);
    int[] depths = new int[0];
    boolean used;

    void prepare(int roomCount) {
      seeds.clear();
      interested.clear();
      queue.clear();
      if (depths.length < roomCount) depths = new int[roomCount];
    }

    void expand(Map.Zone zone, int adjacentRings) {
      interested.clear();
      queue.clear();
      int roomCount = zone.getRoomsEx().size;
      for (int i = 0; i < roomCount; i++) depths[i] = -1;
      for (int roomId = seeds.nextSetBit(0); roomId >= 0;
          roomId = seeds.nextSetBit(roomId + 1)) {
        depths[roomId] = 0;
        interested.set(roomId);
        queue.add(roomId);
      }

      for (int i = 0; i < queue.size; i++) {
        int roomId = queue.get(i);
        int depth = depths[roomId];
        if (depth >= adjacentRings) continue;
        Map.RoomEx room = zone.getRoomsEx().get(roomId);
        int[] adjacent = room.adjacentRoomIds();
        if (adjacent == null) continue;
        for (int adjacentId : adjacent) {
          if (adjacentId < 0 || adjacentId >= roomCount || depths[adjacentId] >= 0) continue;
          depths[adjacentId] = depth + 1;
          interested.set(adjacentId);
          queue.add(adjacentId);
        }
      }
    }
  }
}
