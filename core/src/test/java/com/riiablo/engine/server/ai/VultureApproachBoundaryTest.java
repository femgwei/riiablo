package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VultureApproachBoundaryTest extends RiiabloTest {
  @Test void negativeFractionalCoordinatesDoNotProduceZeroApproachOutsideMelee() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      f.world.getMapper(Size.class).get(f.actor).size = 2;
      f.world.getMapper(Size.class).get(f.target).size = 2;
      f.world.getMapper(Position.class).get(f.actor).position.set(83f, -45.9f);
      f.targetPosition().set(86.99f, -45.03f);
      assertFalse(ai.isInNativeMeleeRange(f.target));
      assertTrue(ai.walkInRadius(f.target, 9, 0, 0));
      ArgumentCaptor<Vector2> point = ArgumentCaptor.forClass(Vector2.class);
      verify(f.paths).findPath(eq(f.actor), point.capture(), eq(false), eq(Engine.INVALID_ENTITY));
      assertEquals(new Vector2(85, -45), point.getValue());
      f.world.getMapper(Position.class).get(f.actor).position.set(point.getValue());
      assertTrue(ai.isInNativeMeleeRange(f.target));
    }
  }

  @Test void fullUnitSizeDistanceIsNotTheCombatDistanceTable() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      f.world.getMapper(Size.class).get(f.actor).size = 2;
      f.world.getMapper(Size.class).get(f.target).size = 2;
      f.world.getMapper(Position.class).get(f.actor).position.set(80, -45);
      f.targetPosition().set(84, -45);
      ai.walkInRadius(f.target, 9, 0, 0);
      ArgumentCaptor<Vector2> point = ArgumentCaptor.forClass(Vector2.class);
      verify(f.paths).findPath(eq(f.actor), point.capture(), eq(false), eq(Engine.INVALID_ENTITY));
      // AIUTIL full-size: abs(4-2)=2, abs(0-2)=2 -> distance 3.
      assertEquals(new Vector2(83, -45), point.getValue());
    }
  }
}
