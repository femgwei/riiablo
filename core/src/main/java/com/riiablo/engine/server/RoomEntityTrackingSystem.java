package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;

/** Keeps every server entity's MapWrapper aligned with its native RoomEx. */
@All({Position.class, MapWrapper.class})
public class RoomEntityTrackingSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(RoomEntityTrackingSystem.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  @Override
  protected void process(int entityId) {
    MapWrapper wrapper = mMapWrapper.get(entityId);
    if (wrapper == null || wrapper.map == null) return;
    Vector2 position = mPosition.get(entityId).position;
    Map.Zone oldZone = wrapper.zone;
    int oldRoomId = wrapper.roomId;
    Map.Zone zone = oldZone;
    Map.RoomEx room = zone != null ? zone.findRoomEx(position.x, position.y) : null;
    if (room == null) {
      zone = wrapper.map.getZone(position);
      room = zone != null ? zone.findRoomEx(position.x, position.y) : null;
    }
    int nextRoomId = room != null ? room.id : -1;
    if (zone != oldZone || nextRoomId != wrapper.roomId) {
      wrapper.zone = zone;
      wrapper.roomId = nextRoomId;
      log.debug("[ROOM_ENTITY] entity={} level={} fromZone={} fromRoom={} toZone={} toRoom={} pos=({}, {})",
          entityId,
          zone != null && zone.level != null ? zone.level.Id : -1,
          oldZone != null && oldZone.level != null ? oldZone.level.Id : -1,
          oldRoomId,
          zone != null && zone.level != null ? zone.level.Id : -1,
          nextRoomId, position.x, position.y);
    }
  }
}
