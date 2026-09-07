package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;

class AuthoritativeInterpolationSystemTest {
  @Test
  void renderOverrideNeverWritesBackToAuthoritativeTransform() {
    AuthoritativeInterpolationSystem interpolation =
        new AuthoritativeInterpolationSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(interpolation)
        .build());
    try {
      int entityId = world.create();
      ComponentMapper<Position> positions = world.getMapper(Position.class);
      ComponentMapper<Angle> angles = world.getMapper(Angle.class);
      positions.create(entityId).position.set(4f, 0f);
      angles.create(entityId).set(new com.badlogic.gdx.math.Vector2(0f, 1f));
      world.process();

      interpolation.record(entityId, 1, 1000,
          true, 0f, 0f, true, 1f, 0f);
      interpolation.record(entityId, 2, 1040,
          true, 4f, 0f, true, 0f, 1f);
      interpolation.beginRender(0.02f);
      assertEquals(2f, positions.get(entityId).position.x, 0.0001f);
      assertEquals(0.7071f, angles.get(entityId).angle.x, 0.001f);

      interpolation.endRender();
      assertEquals(4f, positions.get(entityId).position.x, 0f);
      assertEquals(0f, angles.get(entityId).angle.x, 0f);
      assertEquals(1f, angles.get(entityId).angle.y, 0f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void localCorrectionFadesOnlyDuringRendering() {
    AuthoritativeInterpolationSystem interpolation =
        new AuthoritativeInterpolationSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(interpolation)
        .build());
    try {
      int entityId = world.create();
      ComponentMapper<Position> positions = world.getMapper(Position.class);
      positions.create(entityId).position.set(5f, 2f);
      world.process();

      interpolation.correctLocal(entityId, 1f, 0f, false);
      interpolation.beginRender(0.04f);
      assertEquals(6f, positions.get(entityId).position.x, 0.0001f);
      interpolation.endRender();
      assertEquals(5f, positions.get(entityId).position.x, 0f);

      interpolation.beginRender(0.04f);
      assertEquals(5.6667f, positions.get(entityId).position.x, 0.001f);
      interpolation.endRender();
      assertEquals(5f, positions.get(entityId).position.x, 0f);

      interpolation.correctLocal(entityId, 1f, 0f, true);
      interpolation.beginRender(0.04f);
      assertEquals(5f, positions.get(entityId).position.x, 0f);
      interpolation.endRender();
    } finally {
      world.dispose();
    }
  }
}
