package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Server-side projection of D2MOO DRLGACTIVATE client room references.
 * A player anchors CLIENT_IN_ROOM at its RoomEx; references propagate through
 * pRoomsNear to CLIENT_IN_SIGHT and one further ring to CLIENT_OUT_OF_SIGHT.
 */
@Wire(failOnNull = false)
@All({Player.class, Position.class, MapWrapper.class})
public class RoomActivationSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(RoomActivationSystem.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Monster> mMonster;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  private final IntMap<ClientRoom> clients = new IntMap<>();

  @Override
  protected void process(int entityId) {
    MapWrapper mapping = mMapWrapper.get(entityId);
    Map.Zone zone = mapping != null ? mapping.zone : null;
    Map map = mapping != null ? mapping.map : null;
    Map.RoomEx room = zone != null
        ? zone.findRoomEx(mPosition.get(entityId).position.x, mPosition.get(entityId).position.y)
        : null;
    int roomId = room != null ? room.id : -1;
    ClientRoom previous = clients.get(entityId);
    if (previous != null && previous.map == map && previous.zone == zone
        && previous.roomId == roomId) return;

    if (previous != null) {
      previous.zone.leaveClientRoom(previous.roomId);
      log.debug("[ROOM_ACTIVATE] player={} fromLevel={} fromRoom={} action=leave",
          entityId, levelId(previous.zone), previous.roomId);
    }
    if (zone != null && roomId >= 0 && zone.hasNativeRoomTopology()) {
      zone.enterClientRoom(roomId);
      clients.put(entityId, new ClientRoom(map, zone, roomId));
      spawnActiveRoomPopulations(zone);
      log.debug("[ROOM_ACTIVATE] player={} toLevel={} toRoom={} action=enter",
          entityId, levelId(zone), roomId);
    } else {
      clients.remove(entityId);
    }
  }

  @Override
  protected void removed(int entityId) {
    ClientRoom previous = clients.remove(entityId);
    if (previous != null) {
      previous.zone.leaveClientRoom(previous.roomId);
      log.debug("[ROOM_ACTIVATE] player={} fromLevel={} fromRoom={} action=remove",
          entityId, levelId(previous.zone), previous.roomId);
    }
  }

  private static int levelId(Map.Zone zone) {
    return zone != null && zone.level != null ? zone.level.Id : -1;
  }

  private void spawnActiveRoomPopulations(Map.Zone zone) {
    if (factory == null || zone == null) return;
    for (Map.RoomEx room : zone.getRoomsEx()) {
      if (room.getActivationStatus() > Map.RoomEx.CLIENT_IN_SIGHT
          || !room.claimMonsterPopulation()) continue;
      int spawned = 0;
      for (Map.MonsterSpawn spawn : room.getPendingMonsterSpawns()) {
        int monsterId = factory.createMonster(spawn.monsterId, spawn.x, spawn.y);
        if (monsterId == Engine.INVALID_ENTITY) continue;
        mMapWrapper.create(monsterId).set(zone.map, zone);
        if (mMonster.has(monsterId)) {
          mMonster.get(monsterId).setSpawnAnchor(zone, spawn.x, spawn.y);
        }
        spawned++;
      }
      log.info("[ROOM_MONSTER_POPULATION] level={} room={} queued={} spawned={} action=first_activate",
          levelId(zone), room.id, room.getPendingMonsterSpawns().size, spawned);
    }
  }

  private static final class ClientRoom {
    final Map map;
    final Map.Zone zone;
    final int roomId;

    ClientRoom(Map map, Map.Zone zone, int roomId) {
      this.map = map;
      this.zone = zone;
      this.roomId = roomId;
    }
  }
}
