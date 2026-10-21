package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
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
      positions.create(entityId).initializeNativeCell(4, 0);
      angles.create(entityId).set(new com.badlogic.gdx.math.Vector2(0f, 1f));
      world.process();
      assertTrue(positions.get(entityId).hasNativePrecision());

      interpolation.record(entityId, 1, 1000,
          true, 0f, 0f, true, 1f, 0f);
      interpolation.record(entityId, 2, 1040,
          true, 4f, 0f, true, 0f, 1f);
      interpolation.beginRender(0.02f);
      Vector2 view = ClientTransformView.position(world, entityId, new Vector2());
      assertEquals(2f, view.x, 0.0001f);
      assertEquals(0.7071f, ClientTransformView.angle(world, entityId, new Vector2()).x, 0.001f);
      // Consumers can modify their copies without changing the next reader.
      view.set(99f, 99f);
      assertEquals(2f, interpolation.position(entityId, view).x, 0.0001f);
      assertEquals(4f, positions.get(entityId).position.x, 0f);
      assertEquals(0f, angles.get(entityId).angle.x, 0f);
      assertTrue(positions.get(entityId).hasNativePrecision());

      interpolation.endRender();
      assertEquals(4f, positions.get(entityId).position.x, 0f);
      assertTrue(positions.get(entityId).hasNativePrecision());
      assertEquals(4f, interpolation.position(entityId, view).x, 0f);
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
      positions.create(entityId).initializeNativeCell(5, 2);
      world.process();

      interpolation.correctLocal(entityId, 1f, 0f, false);
      interpolation.beginRender(0.04f);
      Vector2 view = new Vector2();
      assertEquals(6f, interpolation.position(entityId, view).x, 0.0001f);
      assertEquals(5f, positions.get(entityId).position.x, 0f);
      assertTrue(positions.get(entityId).hasNativePrecision());
      interpolation.endRender();
      assertEquals(5f, positions.get(entityId).position.x, 0f);
      assertTrue(positions.get(entityId).hasNativePrecision());

      interpolation.beginRender(0.04f);
      assertEquals(5.6667f, interpolation.position(entityId, view).x, 0.001f);
      interpolation.endRender();
      assertEquals(5f, interpolation.position(entityId, view).x, 0f);

      interpolation.correctLocal(entityId, 1f, 0f, true);
      interpolation.beginRender(0.04f);
      assertEquals(5f, positions.get(entityId).position.x, 0f);
      interpolation.endRender();
    } finally {
      world.dispose();
    }
  }
}
