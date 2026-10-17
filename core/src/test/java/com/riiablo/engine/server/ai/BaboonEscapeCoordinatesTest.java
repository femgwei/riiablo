package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.NativeAiState;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;

class BaboonEscapeCoordinatesTest extends RiiabloTest {
  @Test void regenerationEscapeUsesIntegerAxisSigns() { verifyEscape(false); }
  @Test void hitRegenerationEscapeUsesIntegerAxisSigns() { verifyEscape(true); }

  private void verifyEscape(boolean hit) {
    Vector2 source = new Vector2(10.8f, 10.2f);
    for (Vector2 target : new Vector2[] {
        new Vector2(11.1f, 11.9f), new Vector2(10.1f, 11.3f),
        new Vector2(9.2f, 9.1f), new Vector2(10.1f, 10.9f)}) {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        try (Fixture f = new Fixture("baboon1", difficulty, 1)) {
          Baboon ai = (Baboon) f.ai;
          f.world.getMapper(Position.class).get(f.actor).position.set(source);
          f.targetPosition().set(target);
          f.health(25, 100);
          if (hit) {
            ai.params[0] = 50;
            f.world.getMapper(NativeAiState.class).create(f.actor).previousMode = 3;
            f.seedFirstRoll(50, true);
          } else {
            ai.aiParam0 = 4;
            f.seedFirstRoll(33, false);
          }
          ai.update(0.04f);
          Vector2 expected = new Vector2(
              10 + Integer.compare(10, (int) target.x) * 15,
              10 + Integer.compare(10, (int) target.y) * 15);
          verify(f.paths).findPath(f.actor, expected, false, Engine.INVALID_ENTITY);
          assertEquals("ESCAPE", ai.getState());
          assertEquals(source, f.world.getMapper(Position.class).get(f.actor).position);
          assertEquals(target, f.targetPosition());
          assertNull(f.casting());
          assertEquals(ai.SLEEP, ai.time);
        }
      }
    }
  }
}
