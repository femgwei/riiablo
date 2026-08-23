package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

public class ServerSkillSystemTest {
  private static final float EPSILON = 0.0001f;

  @Test
  public void novaDirectionsCoverFullRingAtUnitLength() {
    Vector2 direction = new Vector2();
    for (int i = 0; i < 64; i++) {
      ServerSkillSystem.radialDirection(i, 64, direction);
      assertEquals(1f, direction.len(), EPSILON);
    }

    ServerSkillSystem.radialDirection(0, 64, direction);
    assertEquals(1f, direction.x, EPSILON);
    assertEquals(0f, direction.y, EPSILON);
    ServerSkillSystem.radialDirection(16, 64, direction);
    assertEquals(0f, direction.x, EPSILON);
    assertEquals(1f, direction.y, EPSILON);
    ServerSkillSystem.radialDirection(32, 64, direction);
    assertEquals(-1f, direction.x, EPSILON);
    assertEquals(0f, direction.y, EPSILON);
    ServerSkillSystem.radialDirection(48, 64, direction);
    assertEquals(0f, direction.x, EPSILON);
    assertEquals(-1f, direction.y, EPSILON);
  }
}
