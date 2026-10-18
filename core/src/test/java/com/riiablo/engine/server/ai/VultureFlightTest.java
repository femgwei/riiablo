package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;

class VultureFlightTest extends RiiabloTest {
  @Test void distantPlayerWithoutClassTriggersTakeoff() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      MovementModes modes = f.world.getMapper(MovementModes.class).create(f.actor)
          .set(Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      f.world.getMapper(NativeUnitFlags.class).create(f.actor).set(NativeUnitFlags.MONSTER_TARGET);
      f.seedFirstRoll(60, true);
      ai.update(.04f);
      assertTrue(ai.monster.vultureAirborne);
      assertTrue(ai.aiParam0 >= 24 && ai.aiParam0 <= 31);
      assertEquals("FLYING", ai.getState());
      assertEquals(Engine.Monster.MODE_S1, modes.WL);
      assertEquals(Engine.Monster.MODE_S1, modes.NU);
      assertFalse(f.world.getMapper(NativeUnitFlags.class).get(f.actor).has(NativeUnitFlags.MONSTER_TARGET));
      assertNull(f.casting());
    }
  }
  @Test void airborneTimerExpiresIntoLandingAnimation() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      f.world.getMapper(MapWrapper.class).get(f.actor).map = mock(com.riiablo.map.Map.class);
      ai.aiParam0 = 1;
      ai.monster.vultureAirborne = true;
      ai.update(.04f);
      assertFalse(ai.monster.vultureAirborne);
      assertEquals(-1, ai.aiParam0);
      assertNotNull(f.sequence());
    }
  }
  @Test void airborneMovementCountsDownWithoutCastingSkill() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      ai.aiParam0 = 8;
      ai.update(.04f);
      assertEquals(7, ai.aiParam0);
      assertTrue(ai.monster.vultureAirborne);
      assertTrue(ai.aiParam1 != 0 || ai.aiParam2 != 0);
      assertNull(f.casting());
    }
  }
  @Test void blockedGroundKeepsBirdAirborneForRetry() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      com.riiablo.map.Map map = mock(com.riiablo.map.Map.class);
      when(map.isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);
      f.world.getMapper(MapWrapper.class).get(f.actor).map = map;
      ai.aiParam0 = 1;
      ai.monster.vultureAirborne = true;
      ai.update(.04f);
      assertTrue(ai.monster.vultureAirborne);
      assertEquals(8, ai.aiParam0);
      assertNull(f.sequence());
    }
  }

  @Test void ownedMinionDoesNotTakeOff() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      ai.monster.minionOwnerId = f.target;
      ai.update(.04f);
      assertFalse(ai.monster.vultureAirborne);
      assertEquals(0, ai.aiParam0);
    }
  }
  @Test void deathRestoresGroundAnimationModes() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      MovementModes modes = f.world.getMapper(MovementModes.class).create(f.actor)
          .set(Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      f.seedFirstRoll(60, true);
      ai.update(.04f);
      ai.kill();
      assertFalse(ai.monster.vultureAirborne);
      assertEquals(Engine.Monster.MODE_WL, modes.WL);
      assertEquals(Engine.Monster.MODE_NU, modes.NU);
      assertEquals("DEAD", ai.getState());
    }
  }

  @Test void realPathfinderAcceptsAirWaypointWithoutGroundGraph() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      ai.pathfinder = f.world.getSystem(com.riiablo.engine.server.Pathfinder.class);
      ai.aiParam0 = 8;
      ai.aiParam1 = 60;
      ai.aiParam2 = 45;
      ai.update(.04f);
      Pathfind path = f.world.getMapper(Pathfind.class).get(f.actor);
      assertNotNull(path);
      assertEquals(2, path.path.getCount());
      assertEquals(60f, path.path.getNodePosition(1).x);
      assertEquals(45f, path.path.getNodePosition(1).y);
      assertEquals(7, ai.aiParam0);
    }
  }

  @Test void obstructedLandingUsesNearbyFreeGroundInsteadOfRepeatingFlight() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      com.riiablo.map.Map map = mock(com.riiablo.map.Map.class);
      when(map.isBlockedForPath(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
          .thenAnswer(call -> (int) call.getArgument(1) == 10 && (int) call.getArgument(2) == 10);
      f.world.getMapper(MapWrapper.class).get(f.actor).map = map;
      ai.aiParam0 = 1;
      ai.monster.vultureAirborne = true;
      ai.update(.04f);
      assertFalse(ai.monster.vultureAirborne);
      assertEquals(-1, ai.aiParam0);
      assertFalse(f.world.getMapper(Position.class).get(f.actor).position.epsilonEquals(10, 10, .01f));
      assertNotNull(f.sequence());
    }
  }

  @Test void distantTargetInOtherRoomApproachesOnGroundBeforeTakeoff() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      com.riiablo.map.Map.Zone zone = mock(com.riiablo.map.Map.Zone.class);
      com.riiablo.map.Map.RoomEx ownRoom = mock(com.riiablo.map.Map.RoomEx.class);
      com.riiablo.map.Map.RoomEx targetRoom = mock(com.riiablo.map.Map.RoomEx.class);
      when(zone.hasNativeRoomTopology()).thenReturn(true);
      when(zone.areRoomsAdjacent(anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenReturn(true);
      when(zone.findRoomEx(anyFloat(), anyFloat()))
          .thenAnswer(call -> (float) call.getArgument(0) < 20 ? ownRoom : targetRoom);
      MapWrapper own = f.world.getMapper(MapWrapper.class).get(f.actor);
      own.zone = zone;
      f.world.getMapper(MapWrapper.class).create(f.target).set(own.map, zone);
      f.seedFirstRoll(60, true);
      ai.update(.04f);
      assertFalse(ai.monster.vultureAirborne);
      assertEquals(0, ai.aiParam0);
      verify(f.paths).findPath(eq(f.actor), any(com.badlogic.gdx.math.Vector2.class), eq(false), eq(Engine.INVALID_ENTITY));
    }
  }

  @Test void realTerrainLandingSearchStaysInsideCurrentNativeRoom() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      com.riiablo.map.Map map = new com.riiablo.map.Map(1, 0);
      com.riiablo.map.Map.Zone zone = map.createSyntheticArena(1, 4, 4);
      com.riiablo.map.Map.RoomEx room = zone.addRoomEx(0, 0, 20, 20);
      zone.or(10, 10, com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK);
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone);
      ai.aiParam0 = 1;
      ai.monster.vultureAirborne = true;
      ai.think(f.target, 20, false);
      com.badlogic.gdx.math.Vector2 p = f.world.getMapper(Position.class).get(f.actor).position;
      assertFalse(ai.monster.vultureAirborne);
      assertFalse(p.epsilonEquals(10, 10, .01f));
      assertSame(room, zone.findRoomEx(p.x, p.y));
      assertFalse(map.isBlockedForPath(zone, (int) p.x, (int) p.y,
          com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK, 1, false));
      assertNotNull(f.sequence());
    }
  }

}
