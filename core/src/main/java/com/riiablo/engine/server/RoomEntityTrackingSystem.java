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
  protected ComponentMapper<com.riiablo.engine.server.component.Warp> mWarp;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<com.riiablo.engine.server.component.Player> mPlayer;

  @Override
  protected void process(int entityId) {
    MapWrapper wrapper = mMapWrapper.get(entityId);
    if (wrapper == null || wrapper.map == null) return;
    if (wrapper.roomIdPinned) return;
    Vector2 position = mPosition.get(entityId).position;
    Map.Zone oldZone = wrapper.zone;
    int oldRoomId = wrapper.roomId;
    Map.Zone zone = oldZone;
    Map.RoomEx room = zone != null ? zone.findRoomEx(position.x, position.y) : null;
    // Keep the authoritative zone while the entity is still inside its
    // bounds but not yet inside a native RoomEx (common for generated tombs,
    // caves and sparse preset areas).  Falling back to Map#getZone solely on
    // a missing RoomEx is ambiguous because Acts intentionally reuse world
    // coordinate ranges; it can silently relabel an A2 player as an older A1
    // level before the baseline is serialized.  Only resolve another zone
    // after the player actually leaves the current zone bounds.
    boolean insideOldZone = oldZone != null
        && position.x >= oldZone.x() && position.y >= oldZone.y()
        && position.x < oldZone.x() + oldZone.width()
        && position.y < oldZone.y() + oldZone.height();
    // A5 reduced/native metadata-only zones can have no collision grid and a
    // zero-sized geometric envelope.  Do not relabel those players through
    // Map#getZone (or clear the wrapper) merely because no RoomEx was found;
    // the warp transaction already established the authoritative level.
    boolean syntheticA5 = oldZone != null && oldZone.level != null
        && oldZone.level.Id >= 109 && oldZone.level.Id <= 132
        && (!oldZone.hasNativeRoomTopology() || oldZone.width() <= 0
            || oldZone.height() <= 0);
    // DS1 triggers can sit on the exclusive edge, and LvlWarp offsets can
    // put their visual position in the next rectangle. They still belong to
    // the exporting level: reclassifying a 26->27 trigger as level 27 turns
    // it into a self-warp and removes level 26's only exit. Static map objects
    // likewise keep their preset owner; mobile units remain position-tracked.
    boolean mapOwned = mWarp.has(entityId) || mObject.has(entityId);
    if (room == null && !insideOldZone && !syntheticA5 && !mapOwned) {
      zone = wrapper.map.getZone(position);
      room = zone != null ? zone.findRoomEx(position.x, position.y) : null;
    }
    int nextRoomId = room != null ? room.id : -1;
    if (zone != oldZone || nextRoomId != wrapper.roomId) {
      wrapper.zone = zone;
      wrapper.roomId = nextRoomId;
      // WarpInteractor already publishes its transaction. Here only ordinary
      // walking changes the zone; room-only movement must not replay entry.
      if (zone != oldZone && mPlayer.has(entityId)) {
        net.mostlyoriginal.api.event.common.EventSystem events = world.getSystem(
            net.mostlyoriginal.api.event.common.EventSystem.class);
        if (events != null) events.dispatch(com.riiablo.engine.server.event.ZoneChangeEvent.obtain(entityId, zone));
      }
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
