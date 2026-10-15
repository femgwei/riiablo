package com.riiablo.map;

import com.d2moo.common.path.D2PathRequest;
import com.d2moo.common.path.D2PathResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;

/** Request-local RoomEx projection for diagnostics, not a live active-room graph.
 * Rebuild after loading/unloading zones. Never feeds production movement. */
public final class NativePathRoomGraph {
  private final IdentityHashMap<Map.RoomEx, D2PathRequest.Room> rooms = new IdentityHashMap<>();

  public NativePathRoomGraph(Iterable<Map.Zone> zones) {
    HashMap<Long, D2PathRequest.Room> byKey = new HashMap<>();
    ArrayList<Map.RoomEx> sources = new ArrayList<>();
    for (Map.Zone zone : zones) {
      for (Map.RoomEx source : zone.getRoomsEx()) {
        if (source.nativePathNeighborKeys() == null) {
          throw new IllegalArgumentException("RoomEx native path topology was not exported");
        }
        D2PathRequest.Room room = new D2PathRequest.Room(
            new D2PathResult.Room(source.x, source.y, source.width, source.height), zone.isTown());
        long key = ((long) zone.levelId() << 32) | (source.id & 0xffffffffL);
        if (byKey.put(key, room) != null) throw new IllegalArgumentException("duplicate level/room key");
        rooms.put(source, room);
        sources.add(source);
      }
    }
    for (Map.RoomEx source : sources) {
      ArrayList<D2PathRequest.Room> near = new ArrayList<>();
      for (Long key : source.nativePathNeighborKeys()) {
        // Null native slots and links to zones outside this snapshot remain null.
        near.add(key == null ? null : byKey.get(key));
      }
      rooms.get(source).nearby = Collections.unmodifiableList(near);
    }
  }

  /** The exact bounds instance is shared with result/start-room projections. */
  public D2PathRequest.Room room(Map.RoomEx source) { return rooms.get(source); }
}
