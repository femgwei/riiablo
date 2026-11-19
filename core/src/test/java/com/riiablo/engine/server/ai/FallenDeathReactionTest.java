package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.NativeAiCommand;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.CofManager;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class FallenDeathReactionTest extends RiiabloTest {
  @Test void activeEscapeContinuesAfterCorpseFinishesWithoutReplacingItsPath() {
    try (Fixture f = fixture()) {
      int dead = corpse(f, 11, 10, Engine.Monster.MODE_DT);
      f.world.process();
      f.ai.update(.04f);
      assertEquals("ESCAPE", f.ai.getState());
      assertEquals(0f, ((Fallen) f.ai).time, .00001f);
      f.world.getMapper(com.riiablo.engine.server.component.Pathfind.class).create(f.actor);
      f.world.getMapper(CofReference.class).get(dead).mode = Engine.Monster.MODE_DD;
      clearInvocations(f.paths);
      // Longer than the previous maximum two-second random cooldown.
      for (int frame = 0; frame < 80; frame++) f.ai.update(.04f);
      assertEquals("ESCAPE", f.ai.getState());
      verifyNoInteractions(f.paths);
      assertNull(f.casting());
      assertEquals(.5f, f.world.getMapper(com.riiablo.engine.server.component.Velocity.class)
          .get(f.actor).modeSpeedBonusMultiplier, .00001f);
    }
  }

  @Test void completedEscapeCanResumeCombatWithoutRandomCooldown() {
    try (Fixture f = fixture()) {
      int dead = corpse(f, 11, 10, Engine.Monster.MODE_DT);
      f.world.process();
      f.ai.update(.04f);
      f.world.getMapper(com.riiablo.engine.server.component.Pathfind.class).create(f.actor);
      f.world.getMapper(CofReference.class).get(dead).mode = Engine.Monster.MODE_DD;
      f.ai.update(.04f);
      assertEquals("ESCAPE", f.ai.getState());
      f.world.getMapper(com.riiablo.engine.server.component.Pathfind.class).remove(f.actor);
      // Fixture is already NU/in melee, as after completed movement.
      f.ai.update(.04f);
      assertEquals("ATTACK", f.ai.getState());
      assertNotNull(f.casting());
      assertEquals(0f, f.world.getMapper(com.riiablo.engine.server.component.Velocity.class)
          .get(f.actor).modeSpeedBonusMultiplier, .00001f);
    }
  }

  @Test void escapeUsesTwelveOnEachAxisInsteadOfNormalizingDiagonal() {
    verifyEscapeDestination(13, 11, 10, 10, 25, 23);
  }

  @Test void escapeKeepsAlignedAxisAtNativeIntegerCoordinate() {
    verifyEscapeDestination(10.8f, 10.2f, 10.1f, 9.2f, 10, 22);
  }

  @Test void escapeCanMoveInNegativeAxisDirections() {
    verifyEscapeDestination(10, 10, 13, 11, -2, -2);
  }

  private void verifyEscapeDestination(float x, float y, float targetX, float targetY,
      float expectedX, float expectedY) {
    try (Fixture f = fixture()) {
      f.world.getMapper(com.riiablo.engine.server.component.Position.class).get(f.actor)
          .position.set(x, y);
      f.targetPosition().set(targetX, targetY);
      corpse(f, x + 1, y, Engine.Monster.MODE_DT);
      f.world.process();
      f.ai.update(.04f);
      assertEquals("ESCAPE", f.ai.getState());
      org.mockito.ArgumentCaptor<com.badlogic.gdx.math.Vector2> destination =
          org.mockito.ArgumentCaptor.forClass(com.badlogic.gdx.math.Vector2.class);
      verify(f.paths).findPath(eq(f.actor), destination.capture(), eq(true), eq(Engine.INVALID_ENTITY));
      assertEquals(expectedX, destination.getValue().x, .0001f);
      assertEquals(expectedY, destination.getValue().y, .0001f);
    }
  }

  @Test void fifthDeathEvictsTheOldestEvenWhenNewerDeathsFinishAnimating() {
    verifyDeathRing(5, true);
  }

  @Test void fourthDeathStillKeepsTheFirstRecentDeath() {
    verifyDeathRing(4, false);
  }

  private void verifyDeathRing(int count, boolean finishNewest) {
    try (Fixture f = fixture()) {
      Map map = f.world.getMapper(MapWrapper.class).get(f.actor).map;
      Map.Zone zone = new Map.Zone();
      Map.RoomEx room = zone.addRoomEx(0, 0, 100, 100);
      room.setAdjacentRoomIds(new int[0]);
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone);
      f.world.getMapper(MapWrapper.class).create(f.target).set(map, zone);
      int first = -1;
      for (int i = 0; i < count; i++) {
        int dead = corpse(f, i == 0 ? 11 : 60 + i, 10, Engine.Monster.MODE_NU);
        f.world.getMapper(MapWrapper.class).create(dead).set(map, zone);
        f.world.getMapper(NativePathIdentity.class).create(dead).allocated(f.world, dead);
        f.world.process();
        f.world.getSystem(CofManager.class).setMode(dead, Engine.Monster.MODE_DT);
        if (i == 0) first = dead;
        if (i == count - 1 && finishNewest)
          f.world.getSystem(CofManager.class).setMode(dead, Engine.Monster.MODE_DD);
      }
      assertEquals(count == 4, f.world.getSystem(CofManager.class).isRecentRoomDeath(room, first));
      f.ai.update(.04f);
      assertEquals(count == 4, "ESCAPE".equals(f.ai.getState()));
    }
  }

  @Test void forcedDeathAnimationRestartDoesNotEvictOtherRecentDeaths() {
    try (Fixture f = fixture()) {
      Map map = f.world.getMapper(MapWrapper.class).get(f.actor).map;
      Map.Zone zone = new Map.Zone();
      Map.RoomEx room = zone.addRoomEx(0, 0, 100, 100);
      int first = -1, last = -1;
      for (int i = 0; i < 4; i++) {
        int dead = corpse(f, 11 + i, 10, Engine.Monster.MODE_NU);
        f.world.getMapper(MapWrapper.class).create(dead).set(map, zone);
        f.world.process();
        f.world.getSystem(CofManager.class).setMode(dead, Engine.Monster.MODE_DT);
        if (i == 0) first = dead;
        last = dead;
      }
      for (int i = 0; i < 6; i++)
        f.world.getSystem(CofManager.class).setMode(last, Engine.Monster.MODE_DT, true);
      assertTrue(f.world.getSystem(CofManager.class).isRecentRoomDeath(room, first));
      Map.RoomEx rebuilt = new Map.Zone().addRoomEx(0, 0, 100, 100);
      assertFalse(f.world.getSystem(CofManager.class).isRecentRoomDeath(rebuilt, first),
          "recreating a room must not inherit its predecessor's active-room death slots");
      try (Fixture otherWorld = fixture()) {
        assertFalse(otherWorld.world.getSystem(CofManager.class).isRecentRoomDeath(room, first),
            "a World must not inherit another simulation's room history");
      }
    }
  }

  @Test void replacedEntityLifetimeDoesNotMatchAnOldDeathRecord() {
    try (Fixture f = fixture()) {
      Map map = f.world.getMapper(MapWrapper.class).get(f.actor).map;
      Map.Zone zone = new Map.Zone();
      Map.RoomEx room = zone.addRoomEx(0, 0, 100, 100);
      int dead = corpse(f, 11, 10, Engine.Monster.MODE_NU);
      f.world.getMapper(MapWrapper.class).create(dead).set(map, zone);
      NativePathIdentity identity = f.world.getMapper(NativePathIdentity.class).create(dead);
      identity.allocated(f.world, dead);
      f.world.process();
      f.world.getSystem(CofManager.class).setMode(dead, Engine.Monster.MODE_DT);
      assertTrue(f.world.getSystem(CofManager.class).isRecentRoomDeath(room, dead));
      identity.allocated(f.world, dead); // Simulate birth of a different lifetime at this ID.
      assertFalse(f.world.getSystem(CofManager.class).isRecentRoomDeath(room, dead));
    }
  }

  @Test void spatiallyCloseButUnlinkedRoomDoesNotTriggerEscape() {
    verifyRoomScope(false, false);
  }

  @Test void explicitlyAdjacentRoomDeathTriggersEscape() {
    verifyRoomScope(true, false);
  }

  @Test void deathInAnotherMapDoesNotTriggerEscapeEvenWithSharedZoneMetadata() {
    verifyRoomScope(true, true);
  }

  private void verifyRoomScope(boolean adjacent, boolean otherMap) {
    try (Fixture f = fixture()) {
      Map map = f.world.getMapper(MapWrapper.class).get(f.actor).map;
      Map.Zone zone = new Map.Zone();
      Map.RoomEx first = zone.addRoomEx(0, 0, 15, 30);
      Map.RoomEx second = zone.addRoomEx(15, 0, 15, 30);
      first.setAdjacentRoomIds(adjacent ? new int[] {second.id} : new int[0]);
      second.setAdjacentRoomIds(adjacent ? new int[] {first.id} : new int[0]);
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone);
      f.world.getMapper(MapWrapper.class).create(f.target).set(map, zone);
      int dead = corpse(f, 16, 10, Engine.Monster.MODE_NU);
      f.world.getMapper(MapWrapper.class).create(dead).set(otherMap ? new Map(1, 0) : map, zone);
      f.world.process();
      f.world.getSystem(CofManager.class).setMode(dead, Engine.Monster.MODE_DT);
      f.ai.update(.04f);
      assertEquals(adjacent && !otherMap, "ESCAPE".equals(f.ai.getState()));
    }
  }

  @Test void nearbyNonFallenDeathPopsOnlyTheCurrentOrderBeforeEscape() {
    try (Fixture f = fixture()) {
      corpse(f, 11, 10, Engine.Monster.MODE_DT);
      NativeAiCommand command = f.world.getMapper(NativeAiCommand.class).create(f.actor);
      command.set(f.actor, NativeAiCommand.MOVE, f.target);
      command.copy(f.world, f.actor, NativeAiCommand.ATTACK, f.target);
      f.world.process();
      f.ai.update(.04f);
      assertEquals("ESCAPE", f.ai.getState());
      assertTrue(f.world.getMapper(NativeAiCommand.class).has(f.actor));
      assertEquals(NativeAiCommand.MOVE, command.command);
    }
  }

  @Test void nativeDistanceFifteenDoesNotTriggerEscapeOnADiagonal() {
    try (Fixture f = fixture()) {
      // sqrt(10^2+10^2) < 15, but native max+min/2 is exactly 15.
      corpse(f, 20, 20, Engine.Monster.MODE_DT);
      f.world.process();
      f.ai.update(.04f);
      assertNotEquals("ESCAPE", f.ai.getState());
      assertNotNull(f.casting());
    }
  }

  @Test void nativeDistanceFourteenTriggersEscapeOnAnUnequalDiagonal() {
    try (Fixture f = fixture()) {
      // Original controlled distance-14 fixture: dx=13, dy=3.
      corpse(f, 23, 13, Engine.Monster.MODE_DT);
      f.world.process();
      f.ai.update(.04f);
      assertEquals("ESCAPE", f.ai.getState());
    }
  }

  @Test void finishedCorpseDoesNotTriggerTheDeathAnimationReaction() {
    try (Fixture f = fixture()) {
      corpse(f, 11, 10, Engine.Monster.MODE_DD);
      f.world.process();
      f.ai.update(.04f);
      assertNotEquals("ESCAPE", f.ai.getState());
    }
  }

  @Test void noTargetDoesNotReuseTheOrderTargetForMovement() {
    try (Fixture f = fixture()) {
      f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .copy(f.world, f.actor, NativeAiCommand.ATTACK, f.target);
      f.world.delete(f.target);
      f.world.process();
      f.ai.update(.04f);
      assertNull(f.casting());
      verify(f.paths, never()).findPath(eq(f.actor), notNull(), anyBoolean(), anyInt());
    }
  }

  private Fixture fixture() {
    Fixture f = new Fixture("fallen1", 0, 1);
    f.world.getMapper(Class.class).create(f.actor);
    f.world.getMapper(CofReference.class).create(f.actor).mode = Engine.Monster.MODE_NU;
    f.ai.params[0] = 0;
    f.ai.params[2] = 100;
    return f;
  }

  private int corpse(Fixture f, float x, float y, byte mode) {
    int id = f.ally("quillrat1", 0, x, y);
    f.world.getMapper(Class.class).create(id);
    f.world.getMapper(CofReference.class).create(id).mode = mode;
    return id;
  }
}
