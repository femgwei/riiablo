package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.map.Map;

/** Applies a committed level context without treating a Map object as authority. */
final class ClientMapSnapshotWriter {
  private ClientMapSnapshotWriter() {}

  static boolean apply(ComponentMapper<MapWrapper> mapper, Map map, int entityId,
      AuthoritativeMapState.Snapshot snapshot, java.util.function.IntFunction<Map.Zone> zone) {
    if (mapper == null || snapshot == null || map == null || zone == null
        || snapshot.levelId < 0) return false;
    Map.Zone resolved = zone.apply(snapshot.levelId);
    if (resolved == null) return false;
    MapWrapper wrapper = mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);
    if (wrapper.map != map || wrapper.zone != resolved) wrapper.set(map, resolved);
    return true;
  }
}
