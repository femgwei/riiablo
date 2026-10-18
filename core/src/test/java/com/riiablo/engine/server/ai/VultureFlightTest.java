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

}
