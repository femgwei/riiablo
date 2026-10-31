package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.presentation.component.AnimationWrapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs the production callback, not just its trail-configuration helper. No GL required. */
class FingerMageTrailLifecycleTest extends RiiabloTest {
  @Test void localAndNetworkHeadsEmitAtRemainingFrameModuloWithoutRecursiveChildren() {
    for (boolean authoritative : new boolean[] {true, false}) {
      try (Fixture f = new Fixture()) {
        int head = f.head(60, authoritative, true);
        for (int tick = 1; tick <= 9; tick++) {
          f.world.getMapper(Position.class).get(head).position.set(tick, 7);
          // The server can advance its own clock before presentation executes.
          f.world.getMapper(Missile.class).get(head).nativeFrame = tick;
          f.step();
        }
        assertEquals(3, f.children.size());
        assertEquals(new Vector2(1, 7), f.positions.get(0));
        assertEquals(new Vector2(4, 7), f.positions.get(1));
        assertEquals(new Vector2(7, 7), f.positions.get(2));
        for (int i = 0; i < f.children.size(); i++) {
          int child = f.children.get(i);
          Missile trail = f.world.getMapper(Missile.class).get(child);
          assertTrue(trail.presentationOnly);
          assertFalse(trail.authoritative);
          assertFalse(trail.homing);
          assertEquals(-1, trail.targetId);
          assertEquals(-1, trail.ownerId);
          assertTrue(f.world.getMapper(Velocity.class).get(child).velocity.isZero());
          assertEquals(f.positions.get(i),
              f.world.getMapper(Position.class).get(child).position);
        }
      }
    }
  }

  @Test void emissionPhaseComesFromLifetimeNotElapsedFrameModulo() {
    try (Fixture f = new Fixture()) {
      f.head(61, false, true);
      f.step();
      assertEquals(0, f.children.size()); // remaining=61
      f.step();
      assertEquals(1, f.children.size()); // remaining=60
      f.step();
      f.step();
      assertEquals(1, f.children.size());
      f.step();
      assertEquals(2, f.children.size()); // remaining=57
    }
  }

  @Test void headClockAndEmissionDoNotWaitForItsDccToLoad() {
    try (Fixture f = new Fixture()) {
      int head = f.head(60, false, false);
      for (int tick = 0; tick < 6; tick++) f.step();
      assertEquals(6, f.world.getMapper(Missile.class).get(head).clientFrame);
      assertEquals(2, f.children.size());
      f.world.getMapper(AnimationWrapper.class).create(head);
      f.step();
      assertEquals(3, f.children.size(), "loading the DCC must not restart the phase");
    }
  }

  @Test void childChecksMinusTwentyBeforeDecrementAndNeverWaitsForDcc() {
    for (boolean loaded : new boolean[] {true, false}) {
      try (Fixture f = new Fixture()) {
        int child = f.trail(loaded);
        for (int update = 1; update <= 20; update++) {
          f.step();
          assertTrue(f.world.getEntityManager().isActive(child), "update " + update);
          assertEquals(update, f.world.getMapper(Missile.class).get(child).nativeFrame);
        }
        f.step(); // callback sees TargetX=-20 and deletes before decrementing
        assertFalse(f.world.getEntityManager().isActive(child));
        assertEquals(0, f.children.size(), "trail cannot recursively create more trails");
      }
    }
  }

  @Test void deletingHeadLeavesExistingTrailsToExpireWithoutFurtherEmission() {
    try (Fixture f = new Fixture()) {
      int head = f.head(60, true, true);
      f.step();
      assertEquals(1, f.children.size());
      int child = f.children.get(0);
      f.world.delete(head);
      for (int update = 0; update < 20; update++) f.step();
      assertTrue(f.world.getEntityManager().isActive(child));
      f.step();
      assertFalse(f.world.getEntityManager().isActive(child));
      assertEquals(1, f.children.size());
    }
  }

  @Test void ordinaryImpactStillWaitsForItsFirstAnimation() {
    try (Fixture f = new Fixture()) {
      int id = f.trail(false);
      Missile missile = f.world.getMapper(Missile.class).get(id);
      missile.missile = new Missiles.Entry();
      missile.missile.pCltDoFunc = 1;
      missile.nativeLifetimeFrames = 1;
      for (int tick = 0; tick < 3; tick++) f.step();
      assertTrue(f.world.getEntityManager().isActive(id));
      assertEquals(0, missile.nativeFrame, "do not regress one-frame impact DCC loading");
      assertEquals(0, f.children.size());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final MissileImpactPresentationSystem system = new MissileImpactPresentationSystem();
    final EntityFactory factory = mock(EntityFactory.class);
    final World world;
    final Missiles.Entry row = Riiablo.files.Missiles.get("fingermagespider");
    final List<Integer> children = new ArrayList<>();
    final List<Vector2> positions = new ArrayList<>();
    final com.riiablo.Colors previousColors = Riiablo.colors;

    Fixture() {
      if (Riiablo.colors == null) Riiablo.colors = new com.riiablo.Colors();
      assertNotNull(row);
      assertEquals(15, row.pCltDoFunc);
      assertArrayEquals(new int[] {3, 20, 5, 20, 2}, row.CltParam);
      world = new World(new WorldConfigurationBuilder()
          .with(system, factory, new OverlayManager()).build()
          .register("factory", factory)
          .register("map", new com.riiablo.map.Map(0, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(-1))).thenAnswer(call -> {
            int child = missile(true);
            world.getMapper(Position.class).get(child).position.set(call.getArgument(2));
            // Deliberately nonzero: the production callback must make the child stationary.
            world.getMapper(Velocity.class).get(child).velocity.set(10, 2);
            positions.add(new Vector2(world.getMapper(Position.class).get(child).position));
            children.add(child);
            return child;
          });
    }

    int head(int lifetime, boolean authoritative, boolean loaded) {
      int id = missile(loaded);
      Missile missile = world.getMapper(Missile.class).get(id);
      missile.nativeLifetimeFrames = lifetime;
      missile.authoritative = authoritative;
      world.getMapper(Velocity.class).get(id).velocity.set(10, 0);
      return id;
    }

    int trail(boolean loaded) {
      int id = missile(loaded);
      MissileImpactPresentationSystem.configureFingerMageTrail(
          world.getMapper(Missile.class).get(id), row);
      return id;
    }

    int missile(boolean loaded) {
      int id = world.create();
      world.getMapper(Missile.class).create(id).missile = row;
      world.getMapper(Position.class).create(id).position.set(4, 7);
      world.getMapper(Velocity.class).create(id);
      if (loaded) world.getMapper(AnimationWrapper.class).create(id);
      return id;
    }

    void step() {
      world.setDelta(1f / 25f);
      world.process();
    }

    @Override public void close() {
      try { world.dispose(); } finally { Riiablo.colors = previousColors; }
    }
  }
}
