package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.map.Map;
import com.riiablo.map.MapManager;
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
  static final float LEVEL_EXIT_PREWARM_DISTANCE = 50f;

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<SuperUnique> mSuperUnique;
  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @Wire(failOnNull = false)
  protected MapManager mapManager;
  private final IntMap<ClientRoom> clients = new IntMap<>();
  private final IntSet prewarmedTownLevels = new IntSet();
  private final IntSet prewarmedOutdoorTransitions = new IntSet();

  @Override
  protected void process(int entityId) {
    MapWrapper mapping = mMapWrapper.get(entityId);
    Map.Zone zone = mapping != null ? mapping.zone : null;
    Map map = mapping != null ? mapping.map : null;
    Position playerPosition = mPosition.get(entityId);
    prewarmTownExitIfNeeded(zone);
    prewarmNearbyOutdoorExit(zone, playerPosition.position.x, playerPosition.position.y);
    Map.RoomEx room = zone != null
        ? zone.findRoomEx(playerPosition.position.x, playerPosition.position.y)
        : null;
    int roomId = room != null ? room.id : -1;
    ClientRoom previous = clients.get(entityId);
    if (previous != null && previous.map == map && previous.zone == zone
        && previous.roomId == roomId) return;

    if (zone != null && roomId >= 0 && zone.hasNativeRoomTopology()) {
      if (previous != null && previous.zone == zone) {
        zone.changeClientRoom(previous.roomId, roomId);
      } else {
        // Match DRLGACTIVATE_ChangeClientRoom: promote the destination before
        // releasing the source so shared rings never deactivate mid-change.
        zone.enterClientRoom(roomId);
        if (previous != null) previous.zone.leaveClientRoom(previous.roomId);
      }
      clients.put(entityId, new ClientRoom(map, zone, roomId));
      spawnActiveRoomObjects(zone);
      spawnActiveRoomPopulations(zone);
      log.debug("[ROOM_ACTIVATE] player={} fromLevel={} fromRoom={} toLevel={} toRoom={} action=change",
          entityId, previous == null ? -1 : levelId(previous.zone),
          previous == null ? -1 : previous.roomId, levelId(zone), roomId);
    } else {
      if (previous != null) {
        previous.zone.leaveClientRoom(previous.roomId);
        log.debug("[ROOM_ACTIVATE] player={} fromLevel={} fromRoom={} action=leave",
            entityId, levelId(previous.zone), previous.roomId);
      }
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

  private void prewarmTownExitIfNeeded(Map.Zone zone) {
    if (zone == null || !zone.isTown()) return;
    int townLevel = levelId(zone);
    if (townLevel < 0 || prewarmedTownLevels.contains(townLevel)) return;
    if (prewarmTownExit(zone)) prewarmedTownLevels.add(townLevel);
  }

  /**
   * Pre-generates the entrance sight ring of a connected outdoor level before
   * the player crosses its boundary. Native pRoomsNear activation is scoped to
   * one level in the Java projection, so without this bridge the destination
   * population appears on the exact frame of the level transition.
   */
  private void prewarmNearbyOutdoorExit(Map.Zone source, float playerX, float playerY) {
    if (source == null || source.isTown() || source.map == null || source.level == null
        || source.level.IsInside) return;

    float threshold2 = LEVEL_EXIT_PREWARM_DISTANCE * LEVEL_EXIT_PREWARM_DISTANCE;
    Array<Map.Zone> zones = source.map.getZones();
    for (int i = 0; i < zones.size; i++) {
      Map.Zone destination = zones.get(i);
      if (!isOutdoorTransitionCandidate(source, destination)) continue;
      int transition = transitionKey(levelId(source), levelId(destination));
      if (prewarmedOutdoorTransitions.contains(transition)) continue;

      float distance2 = distanceSquaredToRect(playerX, playerY,
          destination.x(), destination.y(), destination.width(), destination.height());
      if (distance2 > threshold2) continue;

      float entranceX = clamp(playerX, destination.x(), destination.x() + destination.width());
      float entranceY = clamp(playerY, destination.y(), destination.y() + destination.height());
      Map.RoomEx entrance = nearestRoom(destination, entranceX, entranceY);
      if (entrance == null) continue;

      int spawned = prewarmZoneAt(destination, entranceX, entranceY);
      prewarmedOutdoorTransitions.add(transition);
      log.info("[LEVEL_EXIT_PREWARM] source={} destination={} entranceRoom={} "
              + "distance={} spawnedRooms={} action=complete",
          levelId(source), levelId(destination), entrance.id,
          (float) Math.sqrt(distance2), spawned);
    }
  }

  private static boolean isOutdoorTransitionCandidate(
      Map.Zone source, Map.Zone destination) {
    return destination != null
        && destination != source
        && destination.level != null
        && destination.level.Act == source.level.Act
        && !destination.level.IsInside
        && !destination.isTown()
        && destination.hasNativeRoomTopology()
        && (isVisConnected(source, destination.level.Id)
            || isVisConnected(destination, source.level.Id));
  }

  private static int transitionKey(int firstLevel, int secondLevel) {
    int low = Math.min(firstLevel, secondLevel) & 0xFFFF;
    int high = Math.max(firstLevel, secondLevel) & 0xFFFF;
    return low << 16 | high;
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  /** Pre-generates the directly connected wilderness entrance while its AI stays dormant. */
  private boolean prewarmTownExit(Map.Zone town) {
    Map.Zone exterior = findTownExitZone(town);
    if (exterior == null || !exterior.hasNativeRoomTopology()) return false;

    float exitX = townExitX(town);
    float exitY = townExitY(town);
    Map.RoomEx entrance = nearestRoom(exterior, exitX, exitY);
    if (entrance == null) return false;
    int spawned = prewarmZoneAt(exterior, exitX, exitY);
    log.info("[TOWN_EXIT_PREWARM] town={} exterior={} entranceRoom={} spawnedRooms={} action=complete",
        levelId(town), levelId(exterior), entrance.id, spawned);
    return true;
  }

  private Map.Zone findTownExitZone(Map.Zone town) {
    if (town == null || town.map == null || town.level == null) return null;
    Map.Zone connected = nearestCandidate(town, false, true);
    if (connected != null) return connected;
    connected = nearestCandidate(town, true, false);
    return connected != null ? connected : nearestCandidate(town, false, false);
  }

  private Map.Zone nearestCandidate(
      Map.Zone town, boolean requireVisConnection, boolean requireExitDirection) {
    float exitX = townExitX(town);
    float exitY = townExitY(town);
    Map.Zone best = null;
    float bestDistance = Float.POSITIVE_INFINITY;
    Array<Map.Zone> zones = town.map.getZones();
    for (int i = 0; i < zones.size; i++) {
      Map.Zone candidate = zones.get(i);
      if (candidate == null || candidate == town || candidate.isTown()
          || candidate.level == null || candidate.level.Act != town.level.Act
          || candidate.level.IsInside
          || !candidate.hasNativeRoomTopology()) continue;
      if (requireVisConnection && !isVisConnected(town, candidate.level.Id)) continue;
      if (requireExitDirection && !isInExitDirection(town, candidate)) continue;
      float distance = distanceSquaredToRect(
          exitX, exitY, candidate.x(), candidate.y(), candidate.width(), candidate.height());
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return best;
  }

  private static boolean isInExitDirection(Map.Zone town, Map.Zone candidate) {
    float townCenterX = town.x() + town.width() * 0.5f;
    float townCenterY = town.y() + town.height() * 0.5f;
    float candidateCenterX = candidate.x() + candidate.width() * 0.5f;
    float candidateCenterY = candidate.y() + candidate.height() * 0.5f;
    switch (town.townExitDirection) {
      case 0: return candidateCenterX < townCenterX; // west
      case 1: return candidateCenterY < townCenterY; // north
      case 2: return candidateCenterX > townCenterX; // east
      case 3: return candidateCenterY > townCenterY; // south
      default: return false;
    }
  }

  private static float townExitX(Map.Zone town) {
    if (town.townExitDirection == 0) return town.x();
    if (town.townExitDirection == 2) return town.x() + town.width();
    return town.x() + town.width() * 0.5f;
  }

  private static float townExitY(Map.Zone town) {
    if (town.townExitDirection == 1) return town.y();
    if (town.townExitDirection == 3) return town.y() + town.height();
    return town.y() + town.height() * 0.5f;
  }

  private static boolean isVisConnected(Map.Zone zone, int levelId) {
    if (zone == null || zone.level == null || zone.level.Vis == null) return false;
    for (int visibleLevel : zone.level.Vis) if (visibleLevel == levelId) return true;
    return false;
  }

  static Map.RoomEx nearestRoom(Map.Zone zone, float x, float y) {
    if (zone == null) return null;
    Map.RoomEx nearest = null;
    float nearestDistance = Float.POSITIVE_INFINITY;
    final int roomCount = zone.getRoomsEx().size;
    for (int i = 0; i < roomCount; i++) {
      Map.RoomEx room = zone.getRoomsEx().get(i);
      float distance = distanceSquaredToRect(x, y, room.x, room.y, room.width, room.height);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = room;
      }
    }
    return nearest;
  }

  private static float distanceSquaredToRect(
      float x, float y, float rectX, float rectY, float width, float height) {
    float dx = x < rectX ? rectX - x : x > rectX + width ? x - (rectX + width) : 0f;
    float dy = y < rectY ? rectY - y : y > rectY + height ? y - (rectY + height) : 0f;
    return dx * dx + dy * dy;
  }

  /** Activates just long enough to consume the entrance and direct-sight spawn queues. */
  int prewarmZoneAt(Map.Zone zone, float x, float y) {
    Map.RoomEx entrance = nearestRoom(zone, x, y);
    if (entrance == null || !zone.hasNativeRoomTopology()) return 0;
    int before = countSpawnedPopulations(zone);
    zone.enterClientRoom(entrance.id);
    try {
      spawnActiveRoomObjects(zone);
      spawnActiveRoomPopulations(zone);
    } finally {
      zone.leaveClientRoom(entrance.id);
    }
    return countSpawnedPopulations(zone) - before;
  }

  private static int countSpawnedPopulations(Map.Zone zone) {
    int count = 0;
    final int roomCount = zone.getRoomsEx().size;
    for (int i = 0; i < roomCount; i++) {
      if (zone.getRoomsEx().get(i).isMonsterPopulationSpawned()) count++;
    }
    return count;
  }

  private void spawnActiveRoomPopulations(Map.Zone zone) {
    if (factory == null || zone == null) return;
    // Entity creation may resolve the owning RoomEx and therefore iterate the
    // same LibGDX Array. Array's reusable iterator rejects nested iteration,
    // so keep this activation pass iterator-free.
    final int roomCount = zone.getRoomsEx().size;
    for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) {
      Map.RoomEx room = zone.getRoomsEx().get(roomIndex);
      if (room.getActivationStatus() > Map.RoomEx.CLIENT_IN_SIGHT
          || !room.claimMonsterPopulation()) continue;
      int spawned = 0;
      for (Map.MonsterSpawn spawn : room.getPendingMonsterSpawns()) {
        boolean superUnique = spawn.superUniqueId >= 0;
        int monsterId = factory.createMonster(spawn.monsterId, spawn.x, spawn.y,
            superUnique ? com.riiablo.engine.server.monster.MonsterRank.SUPER_UNIQUE
                : com.riiablo.engine.server.monster.MonsterRank.NORMAL,
            0L, -1, superUnique ? spawn.superUniqueId : -1);
        if (monsterId == Engine.INVALID_ENTITY) continue;
        mMapWrapper.create(monsterId).set(zone.map, zone);
        if (mMonster.has(monsterId)) {
          mMonster.get(monsterId).setSpawnAnchor(zone, spawn.x, spawn.y);
          // D2Game records the pack leader before creating its PartyMin/
          // PartyMax companions. Preserve that relationship so AI callbacks
          // can restrict resurrection to the leader's own minions.
          if (spawn.packId >= 0) {
            mMonster.get(monsterId).setNativePack(spawn.packId, !spawn.minion);
          }
        }
        if (superUnique && mSuperUnique != null) {
          mSuperUnique.create(monsterId).set(spawn.superUniqueId, spawn.superUniqueKey);
        }
        spawned++;
      }
      // Resolve owners after the whole room batch is present. This also
      // handles a minion whose leader was activated in an adjacent RoomEx.
      if (spawned > 0) resolvePackOwners(zone);
      log.info("[ROOM_MONSTER_POPULATION] level={} room={} queued={} spawned={} action=first_activate",
          levelId(zone), room.id, room.getPendingMonsterSpawns().size, spawned);
    }
  }

  private void resolvePackOwners(Map.Zone zone) {
    if (zone == null || mMonster == null) return;
    IntMap<Integer> leaders = new IntMap<>();
    com.artemis.utils.IntBag entities = world.getAspectSubscriptionManager()
        .get(com.artemis.Aspect.all(Monster.class, MapWrapper.class)).getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      Monster value = mMonster.get(id);
      MapWrapper mapping = mMapWrapper.get(id);
      if (mapping == null || mapping.zone != zone || value.nativePackId < 0) continue;
      if (value.nativePackLeader) leaders.put(value.nativePackId, id);
    }
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      Monster value = mMonster.get(id);
      MapWrapper mapping = mMapWrapper.get(id);
      if (mapping == null || mapping.zone != zone || value.nativePackId < 0
          || value.nativePackLeader) continue;
      Integer owner = leaders.get(value.nativePackId);
      if (owner != null) value.setMinionOwner(owner);
    }
  }

  private void spawnActiveRoomObjects(Map.Zone zone) {
    if (mapManager == null || zone == null) return;
    // createNativeObjects calls Zone.findRoomEx for each preset. Using an
    // Array iterator here would nest iteration of Zone.roomsEx and crash on
    // first room activation (#iterator() cannot be used nested).
    final int roomCount = zone.getRoomsEx().size;
    for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) {
      Map.RoomEx room = zone.getRoomsEx().get(roomIndex);
      if (room.getActivationStatus() > Map.RoomEx.CLIENT_IN_SIGHT
          || room.isPresetUnitsSpawned()) continue;
      mapManager.createNativeObjects(zone, room);
      log.info("[ROOM_PRESET_UNITS] level={} room={} action=first_activate",
          levelId(zone), room.id);
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
