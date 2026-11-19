package com.riiablo.engine.server.ai;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.VelocityAdder;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FallenAttackTransitionTest extends RiiabloTest {
  @Test void attackEntryCentersOriginalLastWalkingPositionAndPreventsAnotherStep() {
    verifyAttackEntry(false, true);
  }

  @Test void packCommandAttackUsesTheSameStopBoundary() {
    verifyAttackEntry(true, true);
  }

  @Test void legacyPositionIsNotPromotedToNativePrecisionOnAttack() {
    verifyAttackEntry(false, false);
  }

  private void verifyAttackEntry(boolean command, boolean nativePrecision) {
    try (Act1NativeAiIntegrationTest.Fixture f =
        new Act1NativeAiIntegrationTest.Fixture("fallen1", 0, 1f, new com.riiablo.engine.server.DynamicUnitCollisionSystem(), new VelocityAdder())) {
      f.ai.pathfinder = f.world.getSystem(Pathfinder.class);
      Fallen fallen = (Fallen) f.ai;
      fallen.params[0] = 0;
      fallen.params[2] = 100;
      fallen.params[3] = 100;
      fallen.aiParam0 = true;
      // Recorded native step 177, immediately before A2 entry at step 178.
      Position position = f.world.getMapper(Position.class).get(f.actor)
          .setNativePrecision(296542873, 369942479);
      float legacyX = position.position.x, legacyY = position.position.y;
      if (!nativePrecision) position.invalidateNativePrecision();
      f.world.getMapper(Position.class).get(f.target).initializeNativeCell(4523, 5642);
      Velocity velocity = f.world.getMapper(Velocity.class).get(f.actor)
          .setMonsterFromNativeTable(5);
      velocity.velocity.set(-1, -1);
      GraphPath graph = new GraphPath();
      graph.add(new Point2(4524, 5644));
      graph.add(new Point2(4523, 5642));
      f.world.getMapper(Pathfind.class).create(f.actor).set(graph);
      if (command) f.world.getMapper(NativeAiCommand.class).create(f.actor)
          .copy(f.world, f.actor, NativeAiCommand.ATTACK, f.target);
      fallen.update(.04f);
      assertEquals("ATTACK", fallen.getState());
      assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
      assertNotNull(f.casting());
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
      assertTrue(velocity.velocity.isZero());
      assertPosition(position, nativePrecision, legacyX, legacyY);
      f.world.setDelta(.04f);
      f.world.process();
      assertPosition(position, nativePrecision, legacyX, legacyY);
    }
  }

  private void assertPosition(Position position, boolean nativePrecision, float x, float y) {
    assertEquals(nativePrecision, position.hasNativePrecision());
    if (nativePrecision) {
      assertEquals(296517632, position.nativePrecisionX());
      assertEquals(369917952, position.nativePrecisionY());
    } else {
      assertEquals(x, position.position.x);
      assertEquals(y, position.position.y);
    }
  }
}

