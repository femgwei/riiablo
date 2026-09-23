package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.map.Map;
import com.riiablo.map.MapManager;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.engine.server.monster.MonsterRank;
import org.junit.jupiter.api.Test;

class MonsterRoomActivationTest {
  @Test
  void nativeRoomActivationSleepsAndWakesMonsterAi() {
    AIStepper stepper = new AIStepper();
    World world = new World(new WorldConfigurationBuilder().with(new RoomActivationSystem(), stepper).build());
    try {
      Map map = new Map(0, 0);
      Map.Zone zone = nativeThreeRoomZone();
      CountingAI ai = new CountingAI();

      int monsterId = world.create();
      world.getMapper(Position.class).create(monsterId).position.set(90, 10);
      world.getMapper(Monster.class).create(monsterId).setSpawnAnchor(zone, 90, 10);
      world.getMapper(MapWrapper.class).create(monsterId).set(map, zone);
      world.getMapper(AIWrapper.class).create(monsterId).ai = ai;
      world.getMapper(Pathfind.class).create(monsterId);
      world.getMapper(Running.class).create(monsterId);
      world.getMapper(Velocity.class).create(monsterId).velocity.set(3, 0);

      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      Position playerPosition = world.getMapper(Position.class).create(playerId);
      playerPosition.position.set(10, 10);
      world.getMapper(MapWrapper.class).create(playerId).set(map, zone);

      world.process();
      assertEquals(0, ai.updates, "two RoomEx away must be outside CLIENT_IN_SIGHT");
      assertFalse(world.getMapper(Pathfind.class).has(monsterId));
      assertFalse(world.getMapper(Running.class).has(monsterId));
      assertTrue(world.getMapper(Velocity.class).get(monsterId).velocity.isZero());

      playerPosition.position.set(50, 10);
      world.process();
      assertEquals(1, ai.updates, "direct pRoomsNear room must wake AI");

      playerPosition.position.set(90, 10);
      world.process();
      assertEquals(2, ai.updates, "CLIENT_IN_ROOM must remain active");
    } finally {
      world.dispose();
    }
  }

  @Test
  void legacyZoneWithoutNativeTopologyKeepsAiActive() {
    AIStepper stepper = new AIStepper();
    World world = new World(new WorldConfigurationBuilder().with(new RoomActivationSystem(), stepper).build());
    try {
      Map map = new Map(0, 0);
      Map.Zone zone = new Map.Zone();
      zone.addRoomEx(0, 0, 40, 40);
      CountingAI ai = new CountingAI();
      int monsterId = world.create();
      world.getMapper(Position.class).create(monsterId).position.set(10, 10);
      world.getMapper(Monster.class).create(monsterId).setSpawnAnchor(zone, 10, 10);
      world.getMapper(MapWrapper.class).create(monsterId).set(map, zone);
      world.getMapper(AIWrapper.class).create(monsterId).ai = ai;

      world.process();
      assertEquals(1, ai.updates);
    } finally {
      world.dispose();
    }
  }

  @Test
  void multiplePlayersKeepRoomSightReferenceUntilBothLeave() {
    Map.Zone zone = nativeThreeRoomZone();
    zone.enterClientRoom(0);
    zone.enterClientRoom(0);
    assertEquals(2, zone.getRoomsEx().get(0).getClientInRoomRefs());
    assertEquals(Map.RoomEx.CLIENT_IN_ROOM, zone.getRoomsEx().get(0).getActivationStatus());
    assertEquals(2, zone.getRoomsEx().get(1).getClientInSightRefs());

    zone.leaveClientRoom(0);
    assertEquals(1, zone.getRoomsEx().get(0).getClientInRoomRefs());
    assertEquals(Map.RoomEx.CLIENT_IN_ROOM, zone.getRoomsEx().get(0).getActivationStatus());
    assertEquals(1, zone.getRoomsEx().get(1).getClientInSightRefs());

    zone.leaveClientRoom(0);
    assertEquals(0, zone.getRoomsEx().get(0).getClientInRoomRefs());
    assertEquals(Map.RoomEx.COUNT, zone.getRoomsEx().get(0).getActivationStatus());
    assertEquals(0, zone.getRoomsEx().get(1).getClientInSightRefs());
  }

  @Test
  void nativeClientRoomChangeKeepsIndependentReferencesAndSpawnClaims() {
    Map.Zone zone = nativeThreeRoomZone();
    Map.RoomEx source = zone.getRoomsEx().get(0);
    Map.RoomEx destination = zone.getRoomsEx().get(2);
    destination.addMonsterSpawn(7, 90, 10);
    assertTrue(destination.claimMonsterPopulation());
    destination.markPresetUnitsSpawned();

    zone.enterClientRoom(source.id);
    zone.enterClientRoom(source.id);
    zone.changeClientRoom(source.id, destination.id);
    assertEquals(1, source.getClientInRoomRefs(),
        "one player must keep the source room anchored");
    assertEquals(1, destination.getClientInRoomRefs());
    assertEquals(Map.RoomEx.CLIENT_IN_ROOM, source.getActivationStatus());
    assertEquals(Map.RoomEx.CLIENT_IN_ROOM, destination.getActivationStatus());

    zone.changeClientRoom(source.id, destination.id);
    assertEquals(0, source.getClientInRoomRefs());
    assertEquals(2, destination.getClientInRoomRefs());
    assertTrue(source.getActivationStatus() > Map.RoomEx.CLIENT_IN_SIGHT,
        "a non-adjacent source room must leave the active AI ring");
    assertTrue(destination.isMonsterPopulationSpawned());
    assertTrue(destination.isPresetUnitsSpawned());
    assertFalse(destination.claimMonsterPopulation(),
        "RoomEx transitions must not reset one-shot population claims");

    zone.changeClientRoom(destination.id, source.id);
    zone.changeClientRoom(destination.id, source.id);
    assertEquals(2, source.getClientInRoomRefs());
    assertEquals(0, destination.getClientInRoomRefs());
    assertTrue(destination.isMonsterPopulationSpawned());
    assertTrue(destination.isPresetUnitsSpawned());
  }

  @Test
  void deferredMonsterSpawnRetainsNativePackOwnerMetadata() {
    Map.Zone zone = nativeThreeRoomZone();
    Map.RoomEx room = zone.getRoomsEx().get(0);
    room.addMonsterSpawn(7, 10, 10, 41, true);

    Map.MonsterSpawn spawn = room.getPendingMonsterSpawns().get(0);
    assertEquals(41, spawn.packId);
    assertTrue(spawn.minion);
  }

  @Test
  void deferredSuperUniqueSpawnRetainsNativeIdentityAndRank() {
    Map map = new Map(0, 0);
    Map.Zone zone = nativeThreeRoomZone();
    zone.map = map;
    Map.RoomEx room = zone.getRoomsEx().get(0);
    room.addMonsterSpawn(7, 10, 10, -1, false, 42, "Blood Raven");

    RecordingFactory factory = new RecordingFactory();
    RoomActivationSystem activation = new RoomActivationSystem();
    World world = new World(new WorldConfigurationBuilder().with(activation, factory)
        .build().register("factory", factory).register("map", map));
    try {
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      world.getMapper(Position.class).create(playerId).position.set(10, 10);
      world.getMapper(MapWrapper.class).create(playerId).set(map, zone);

      world.process();

      assertEquals(1, factory.monstersCreated);
      assertEquals(MonsterRank.SUPER_UNIQUE, factory.lastRank);
      assertEquals(42, factory.lastUniqueId);
      assertEquals(42, world.getMapper(SuperUnique.class).get(factory.lastMonsterId).id);
      assertEquals("Blood Raven",
          world.getMapper(SuperUnique.class).get(factory.lastMonsterId).key);
      assertTrue(room.isMonsterPopulationSpawned());
    } finally {
      world.dispose();
    }
  }

  @Test
  void clientRoomReferencePropagatesAllFourD2MooStatuses() {
    Map.Zone zone = nativeFourRoomZone();
    zone.enterClientRoom(0);

    assertEquals(Map.RoomEx.CLIENT_IN_ROOM, zone.getRoomsEx().get(0).getActivationStatus());
    assertEquals(Map.RoomEx.CLIENT_IN_SIGHT, zone.getRoomsEx().get(1).getActivationStatus());
    assertEquals(Map.RoomEx.CLIENT_OUT_OF_SIGHT, zone.getRoomsEx().get(2).getActivationStatus());
    assertEquals(Map.RoomEx.UNTILE, zone.getRoomsEx().get(3).getActivationStatus());
    assertTrue(zone.isRoomActiveForAI(10, 10));
    assertTrue(zone.isRoomActiveForAI(50, 10));
    assertFalse(zone.isRoomActiveForAI(90, 10));
    assertFalse(zone.isRoomActiveForAI(130, 10));

    zone.leaveClientRoom(0);
    for (Map.RoomEx room : zone.getRoomsEx()) {
      assertEquals(Map.RoomEx.COUNT, room.getActivationStatus());
    }
  }

  @Test
  void roomPopulationSpawnsOnceWhenItFirstEntersClientSight() {
    Map map = new Map(0, 0);
    Map.Zone zone = nativeThreeRoomZone();
    zone.map = map;
    zone.getRoomsEx().get(2).addMonsterSpawn(7, 90, 10);
    RecordingFactory factory = new RecordingFactory();
    RoomActivationSystem activation = new RoomActivationSystem();
    World world = new World(new WorldConfigurationBuilder().with(activation, factory)
        .build().register("factory", factory).register("map", map));
    try {
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      Position playerPosition = world.getMapper(Position.class).create(playerId);
      playerPosition.position.set(10, 10);
      world.getMapper(MapWrapper.class).create(playerId).set(map, zone);

      world.process();
      assertEquals(0, factory.monstersCreated,
          "CLIENT_OUT_OF_SIGHT room population must remain deferred");

      playerPosition.position.set(50, 10);
      world.process();
      assertEquals(1, factory.monstersCreated,
          "room population must spawn on first CLIENT_IN_SIGHT transition");
      assertTrue(zone.getRoomsEx().get(2).isMonsterPopulationSpawned());

      playerPosition.position.set(10, 10);
      world.process();
      playerPosition.position.set(50, 10);
      world.process();
      assertEquals(1, factory.monstersCreated,
          "reactivation must not duplicate native room population");
    } finally {
      world.dispose();
    }
  }

  @Test
  void townExitPrewarmSpawnsEntranceSightRingThenReleasesActivation() {
    Map map = new Map(0, 0);
    Map.Zone zone = nativeThreeRoomZone();
    zone.map = map;
    zone.getRoomsEx().get(0).addMonsterSpawn(7, 10, 10);
    zone.getRoomsEx().get(1).addMonsterSpawn(7, 50, 10);
    zone.getRoomsEx().get(2).addMonsterSpawn(7, 90, 10);
    RecordingFactory factory = new RecordingFactory();
    RoomActivationSystem activation = new RoomActivationSystem();
    World world = new World(new WorldConfigurationBuilder().with(activation, factory)
        .build().register("factory", factory).register("map", map));
    try {
      assertEquals(zone.getRoomsEx().get(0), RoomActivationSystem.nearestRoom(zone, -5, 10));
      assertEquals(2, activation.prewarmZoneAt(zone, -5, 10));
      assertEquals(2, factory.monstersCreated,
          "the entrance and its direct sight ring must be generated");
      assertFalse(zone.getRoomsEx().get(2).isMonsterPopulationSpawned(),
          "rooms beyond direct sight must remain deferred");
      assertTrue(zone.isRoomActivationTracking());
      for (Map.RoomEx room : zone.getRoomsEx()) {
        assertEquals(Map.RoomEx.COUNT, room.getActivationStatus(),
            "temporary prewarm references must be released before gameplay");
      }
      assertFalse(zone.isRoomActiveForAI(10, 10));
      assertFalse(zone.isRoomActiveForAI(50, 10));
    } finally {
      world.dispose();
    }
  }

  @Test
  void townWithoutRoomTopologyPrewarmsDirectionalExteriorOnFirstPlayerTick() {
    Map map = new Map(0, 0);
    TestZone town = new TestZone(0, 0, 280, 200, true);
    town.level = level(1, 0, false, 3);
    town.map = map;
    town.townExitDirection = 2;

    TestZone bloodMoor = nativeThreeRoomZoneAt(280, 80);
    bloodMoor.level = level(2, 0, false);
    bloodMoor.map = map;
    bloodMoor.getRoomsEx().get(0).addMonsterSpawn(7, 290, 90);
    bloodMoor.getRoomsEx().get(1).addMonsterSpawn(7, 330, 90);
    bloodMoor.getRoomsEx().get(2).addMonsterSpawn(7, 370, 90);

    // Levels.txt may point at a later outdoor level. Geometry and the stored
    // town-gate direction must still select the directly touching entrance.
    TestZone coldPlains = nativeThreeRoomZoneAt(600, 80);
    coldPlains.level = level(3, 0, false);
    coldPlains.map = map;
    coldPlains.getRoomsEx().get(0).addMonsterSpawn(7, 610, 90);

    map.getZones().add(town);
    map.getZones().add(bloodMoor);
    map.getZones().add(coldPlains);

    RecordingFactory factory = new RecordingFactory();
    ResolvingMapManager mapManager = new ResolvingMapManager();
    RoomActivationSystem activation = new RoomActivationSystem();
    World world = new World(new WorldConfigurationBuilder().with(activation, factory)
        .build().register("factory", factory).register("map", map));
    activation.mapManager = mapManager;
    try {
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      world.getMapper(Position.class).create(playerId).position.set(100, 100);
      world.getMapper(MapWrapper.class).create(playerId).set(map, town);

      world.process();

      assertEquals(2, factory.monstersCreated,
          "the entrance room and its direct neighbor must spawn while the player is in town");
      assertEquals(2, mapManager.roomsSpawned,
          "preset objects in the same two rooms must be generated during prewarm");
      assertTrue(bloodMoor.getRoomsEx().get(0).isMonsterPopulationSpawned());
      assertTrue(bloodMoor.getRoomsEx().get(1).isMonsterPopulationSpawned());
      assertFalse(bloodMoor.getRoomsEx().get(2).isMonsterPopulationSpawned());
      assertFalse(coldPlains.getRoomsEx().get(0).isMonsterPopulationSpawned(),
          "a misleading Levels.Vis target must not replace the directly connected exterior");

      world.process();
      assertEquals(2, factory.monstersCreated, "town prewarm must run only once");
      assertEquals(2, mapManager.roomsSpawned);
    } finally {
      world.dispose();
    }
  }

  @Test
  void approachingConnectedOutdoorLevelPrewarmsItsEntranceSightRing() {
    Map map = new Map(0, 0);
    TestZone source = nativeThreeRoomZoneAt(0, 0);
    source.level = level(2, 0, false, 3);
    source.map = map;

    TestZone destination = nativeThreeRoomZoneAt(150, 0);
    destination.level = level(3, 0, false, 2);
    destination.map = map;
    destination.getRoomsEx().get(0).addMonsterSpawn(7, 160, 10);
    destination.getRoomsEx().get(1).addMonsterSpawn(7, 200, 10);
    destination.getRoomsEx().get(2).addMonsterSpawn(7, 240, 10);

    map.getZones().add(source);
    map.getZones().add(destination);

    RecordingFactory factory = new RecordingFactory();
    ResolvingMapManager mapManager = new ResolvingMapManager();
    RoomActivationSystem activation = new RoomActivationSystem();
    World world = new World(new WorldConfigurationBuilder().with(activation, factory)
        .build().register("factory", factory).register("map", map));
    activation.mapManager = mapManager;
    try {
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      Position playerPosition = world.getMapper(Position.class).create(playerId);
      playerPosition.position.set(10, 10);
      world.getMapper(MapWrapper.class).create(playerId).set(map, source);

      world.process();
      assertFalse(destination.getRoomsEx().get(0).isMonsterPopulationSpawned(),
          "a distant connected level must remain deferred");

      playerPosition.position.set(110, 10);
      world.process();

      assertTrue(destination.getRoomsEx().get(0).isMonsterPopulationSpawned());
      assertTrue(destination.getRoomsEx().get(1).isMonsterPopulationSpawned());
      assertFalse(destination.getRoomsEx().get(2).isMonsterPopulationSpawned(),
          "prewarm must stop after the destination entrance sight ring");
      assertEquals(Map.RoomEx.COUNT,
          destination.getRoomsEx().get(0).getActivationStatus());
      assertEquals(Map.RoomEx.COUNT,
          destination.getRoomsEx().get(1).getActivationStatus());

      int monstersAfterPrewarm = factory.monstersCreated;
      world.process();
      assertEquals(monstersAfterPrewarm, factory.monstersCreated,
          "a connected level transition must only be prewarmed once");
    } finally {
      world.dispose();
    }
  }

  @Test
  void nearbyUnconnectedOutdoorLevelIsNotPrewarmed() {
    Map map = new Map(0, 0);
    TestZone source = nativeThreeRoomZoneAt(0, 0);
    source.level = level(2, 0, false);
    source.map = map;

    TestZone destination = nativeThreeRoomZoneAt(130, 0);
    destination.level = level(17, 0, false);
    destination.map = map;
    destination.getRoomsEx().get(0).addMonsterSpawn(7, 140, 10);

    map.getZones().add(source);
    map.getZones().add(destination);

    RecordingFactory factory = new RecordingFactory();
    RoomActivationSystem activation = new RoomActivationSystem();
    World world = new World(new WorldConfigurationBuilder().with(activation, factory)
        .build().register("factory", factory).register("map", map));
    try {
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      world.getMapper(Position.class).create(playerId).position.set(110, 10);
      world.getMapper(MapWrapper.class).create(playerId).set(map, source);

      world.process();

      assertFalse(destination.getRoomsEx().get(0).isMonsterPopulationSpawned(),
          "geometry alone must not preload an unrelated outdoor level");
    } finally {
      world.dispose();
    }
  }

  @Test
  void presetObjectSpawnMayResolveRoomDuringActivationAndRunsOnlyOnce() {
    Map map = new Map(0, 0);
    Map.Zone zone = nativeThreeRoomZone();
    zone.map = map;
    RoomActivationSystem activation = new RoomActivationSystem();
    ResolvingMapManager mapManager = new ResolvingMapManager();
    World world = new World(new WorldConfigurationBuilder().with(activation).build());
    activation.mapManager = mapManager;
    try {
      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      Position playerPosition = world.getMapper(Position.class).create(playerId);
      playerPosition.position.set(10, 10);
      world.getMapper(MapWrapper.class).create(playerId).set(map, zone);

      world.process();
      assertEquals(2, mapManager.roomsSpawned,
          "CLIENT_IN_ROOM and CLIENT_IN_SIGHT rooms must activate together");
      assertFalse(zone.getRoomsEx().get(2).isPresetUnitsSpawned());

      playerPosition.position.set(50, 10);
      world.process();
      assertEquals(3, mapManager.roomsSpawned,
          "newly visible room must activate without duplicating earlier rooms");

      playerPosition.position.set(10, 10);
      world.process();
      assertEquals(3, mapManager.roomsSpawned,
          "reactivation must not duplicate native preset units");
    } finally {
      world.dispose();
    }
  }

  private static Map.Zone nativeThreeRoomZone() {
    Map.Zone zone = new Map.Zone();
    Map.RoomEx first = zone.addRoomEx(0, 0, 40, 40);
    Map.RoomEx second = zone.addRoomEx(40, 0, 40, 40);
    Map.RoomEx third = zone.addRoomEx(80, 0, 40, 40);
    first.setAdjacentRoomIds(new int[] {second.id});
    second.setAdjacentRoomIds(new int[] {first.id, third.id});
    third.setAdjacentRoomIds(new int[] {second.id});
    return zone;
  }

  private static TestZone nativeThreeRoomZoneAt(int x, int y) {
    TestZone zone = new TestZone(x, y, 120, 40, false);
    Map.RoomEx first = zone.addRoomEx(x, y, 40, 40);
    Map.RoomEx second = zone.addRoomEx(x + 40, y, 40, 40);
    Map.RoomEx third = zone.addRoomEx(x + 80, y, 40, 40);
    first.setAdjacentRoomIds(new int[] {second.id});
    second.setAdjacentRoomIds(new int[] {first.id, third.id});
    third.setAdjacentRoomIds(new int[] {second.id});
    return zone;
  }

  private static Levels.Entry level(int id, int act, boolean inside, int... vis) {
    Levels.Entry level = new Levels.Entry();
    level.Id = id;
    level.Act = act;
    level.IsInside = inside;
    level.Vis = vis;
    level.LevelName = "level-" + id;
    return level;
  }

  private static Map.Zone nativeFourRoomZone() {
    Map.Zone zone = nativeThreeRoomZone();
    Map.RoomEx third = zone.getRoomsEx().get(2);
    Map.RoomEx fourth = zone.addRoomEx(120, 0, 40, 40);
    third.setAdjacentRoomIds(new int[] {1, fourth.id});
    fourth.setAdjacentRoomIds(new int[] {third.id});
    return zone;
  }

  private static final class CountingAI extends AI {
    int updates;

    CountingAI() {
      super(-1);
    }

    @Override
    public void update(float delta) {
      updates++;
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    int monstersCreated;
    int lastMonsterId = Engine.INVALID_ENTITY;
    int lastRank = MonsterRank.NORMAL;
    int lastUniqueId = -1;

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }

    @Override
    public int createMonster(int monsterId, float x, float y) {
      monstersCreated++;
      int id = world.create();
      lastMonsterId = id;
      world.getMapper(Monster.class).create(id);
      world.getMapper(Position.class).create(id).position.set(x, y);
      return id;
    }

    @Override
    public int createMonster(int monsterId, float x, float y, int rank, long affixes,
        int championType, int uniqueId) {
      lastRank = rank;
      lastUniqueId = uniqueId;
      return createMonster(monsterId, x, y);
    }
  }

  private static final class ResolvingMapManager extends MapManager {
    int roomsSpawned;

    @Override
    public void createNativeObjects(Map.Zone zone, Map.RoomEx room) {
      // MapManager's real implementation resolves each object's owning room.
      // Keep that nested roomsEx traversal in this regression test.
      assertEquals(room, zone.findRoomEx(room.x + 1, room.y + 1));
      room.markPresetUnitsSpawned();
      roomsSpawned++;
    }
  }

  private static final class TestZone extends Map.Zone {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final boolean town;

    TestZone(int x, int y, int width, int height, boolean town) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.town = town;
    }

    @Override public int x() { return x; }
    @Override public int y() { return y; }
    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public boolean isTown() { return town; }
  }
}
