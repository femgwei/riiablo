package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
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
}
