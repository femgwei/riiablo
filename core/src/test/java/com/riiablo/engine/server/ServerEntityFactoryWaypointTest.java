package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;

class ServerEntityFactoryWaypointTest {
  @Test
  void waypointRemainsInteractableWhenTableModeFlagsAreIncomplete() {
    Objects.Entry waypoint = new Objects.Entry();
    waypoint.SubClass = Engine.Object.SUBCLASS_WAYPOINT;
    waypoint.Selectable = new boolean[8];

    assertEquals(5f, ServerEntityFactory.resolveObjectInteractionRange(waypoint));
    waypoint.OperateRange = 7;
    assertEquals(7f, ServerEntityFactory.resolveObjectInteractionRange(waypoint));
  }

  @Test
  void ordinaryObjectStillRequiresAnOperableSelectableMode() {
    Objects.Entry object = new Objects.Entry();
    object.Selectable = new boolean[8];
    object.OperateRange = 7;

    assertEquals(0f, ServerEntityFactory.resolveObjectInteractionRange(object));
    object.Selectable[Engine.Object.MODE_NU] = true;
    assertEquals(7f, ServerEntityFactory.resolveObjectInteractionRange(object));
  }

  @Test
  void nativeDrawableOperateFnGetsFallbackInteractionRange() {
    Objects.Entry chest = new Objects.Entry();
    chest.Draw = true;
    chest.OperateFn = 4; // D2Game::OBJECTS_OperateFunction04_Chest
    chest.Selectable = new boolean[8];

    assertEquals(3f, ServerEntityFactory.resolveObjectInteractionRange(chest));
  }

  @Test
  void invisibleOperateFnDoesNotBecomeInteractableByFallback() {
    Objects.Entry trigger = new Objects.Entry();
    trigger.Draw = false;
    trigger.OperateFn = 8;
    trigger.Selectable = new boolean[8];

    assertEquals(0f, ServerEntityFactory.resolveObjectInteractionRange(trigger));
  }
}
