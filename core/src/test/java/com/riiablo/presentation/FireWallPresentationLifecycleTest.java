package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.skill.SkillId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the real ECS callback, without GL/DCC readiness masking layout bugs. */
class FireWallPresentationLifecycleTest extends RiiabloTest {
  @Test void seedsChangeSizeButNeverOmitCellsOrChangeWidth() {
    List<Vector2> baseline = null;
    List<String> sizes = new ArrayList<>();
    for (int seed = 1; seed <= 64; seed++) {
      try (Fixture f = new Fixture()) {
        f.pair(new Vector2(1, 0), seed, true, 0);
        for (int tick = 0; tick < 9; tick++) f.step(1);
        if (baseline == null) baseline = new ArrayList<>(f.births);
        assertEquals(baseline, f.births, "seed " + seed);
        assertEquals(10, f.births.size());
        assertEquals(1, f.births.stream().filter(new Vector2(10, 20)::equals).count());
        for (int x = 6; x <= 15; x++) assertTrue(f.births.contains(new Vector2(x, 20)));
        sizes.addAll(f.names);
      }
    }
    assertTrue(sizes.contains("firewall"));
    assertTrue(sizes.contains("firesmall"));
    assertTrue(sizes.contains("firemedium"));
  }

  @Test void centrePrecedesOutwardGrowthAndSubcellMotionDoesNotStackFlames() {
    try (Fixture f = new Fixture()) {
      f.pair(new Vector2(1, 1), 123, true, 0);
      f.step(1);
      assertEquals(java.util.Arrays.asList(new Vector2(10, 20)), f.births);
      f.step(1); // 0.3977 of a subtile: still the centre cell
      assertEquals(1, f.births.size());
      f.step(1);
      assertEquals(3, f.births.size());
      assertTrue(f.births.contains(new Vector2(11, 21)));
      assertTrue(f.births.contains(new Vector2(9, 19)));
      for (int tick = 3; tick < 9; tick++) f.step(1);
      assertEquals(7, f.births.size());
      assertTrue(f.births.contains(new Vector2(13, 23)));
      assertTrue(f.births.contains(new Vector2(7, 17)));
      f.step(9);
      assertEquals(7, f.births.size(), "expired makers must stop emitting");
    }
  }

  @Test void catchupAndNetworkSnapshotsProduceTheSameCellsAsDenseLocalUpdates() {
    for (Vector2 axis : new Vector2[] {new Vector2(1, 0), new Vector2(1, 1), new Vector2(0, 1)}) {
      try (Fixture dense = new Fixture(); Fixture batch = new Fixture(); Fixture network = new Fixture()) {
        dense.pair(axis, 456, true, 0);
        batch.pair(axis, 456, true, 0);
        network.pair(axis, 456, false, 5);
        for (int tick = 0; tick < 9; tick++) dense.step(1);
        batch.step(9);
        network.step(1); // first packet is already five native frames old
        for (int tick = 5; tick < 9; tick++) network.step(1);
        assertEquals(sorted(dense.births), sorted(batch.births));
        assertEquals(sorted(dense.births), sorted(network.births));
        int count = network.births.size();
        network.step(9);
        assertEquals(count, network.births.size());
        for (int id : network.makers) {
          assertEquals(5, network.missile(id).nativeFrame,
              "presentation must not write the accepted server clock");
        }
      }
    }
  }

  @Test void clientLayoutDoesNotConsumeAuthoritativeRandomStateOrMoveReplicas() {
    try (Fixture f = new Fixture()) {
      f.pair(new Vector2(1, 0), 987, false, 0);
      for (int id : f.makers) {
        f.missile(id).rngState = 1234;
        f.missile(id).nativeSeed = new D2Seed(5678, 999);
      }
      f.step(9);
      for (int id : f.makers) {
        Missile m = f.missile(id);
        assertEquals(1234, m.rngState);
        assertEquals(5678, m.nativeSeed.getNLowSeed());
        assertEquals(999, m.nativeSeed.getNHighSeed());
        assertEquals(new Vector2(10, 20), f.world.getMapper(Position.class).get(id).position);
      }
      for (int id : f.children) {
        assertTrue(f.missile(id).presentationOnly);
        assertFalse(f.missile(id).authoritative);
        assertEquals(-1, f.missile(id).ownerId);
        assertTrue(f.world.getMapper(Velocity.class).get(id).velocity.isZero());
      }
    }
  }

  private static List<String> sorted(List<Vector2> positions) {
    List<String> result = new ArrayList<>();
    for (Vector2 position : positions) result.add(position.toString());
    java.util.Collections.sort(result);
    return result;
  }

  private static final class Fixture implements AutoCloseable {
    final EntityFactory factory = mock(EntityFactory.class);
    final World world;
    final List<Vector2> births = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    final List<Integer> children = new ArrayList<>();
    final List<Integer> makers = new ArrayList<>();
    final com.riiablo.Colors previousColors = Riiablo.colors;

    Fixture() {
      if (Riiablo.colors == null) Riiablo.colors = new com.riiablo.Colors();
      world = new World(new WorldConfigurationBuilder()
          .with(new MissileImpactPresentationSystem(), factory, new OverlayManager()).build()
          .register("factory", factory)
          .register("map", new com.riiablo.map.Map(0, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(-1))).thenAnswer(call -> {
            Missiles.Entry row = call.getArgument(0);
            Vector2 at = new Vector2((Vector2) call.getArgument(2));
            int id = world.create();
            Missile child = world.getMapper(Missile.class).create(id);
            child.missile = row;
            child.start.set(at);
            world.getMapper(Position.class).create(id).position.set(at);
            world.getMapper(Velocity.class).create(id);
            births.add(at);
            names.add(row.Missile);
            children.add(id);
            return id;
          });
    }

    void pair(Vector2 axis, int seed, boolean local, int frame) {
      for (int side : new int[] {-1, 1}) {
        int id = world.create();
        makers.add(id);
        Missile maker = world.getMapper(Missile.class).create(id);
        maker.missile = Riiablo.files.Missiles.get("firewallmaker");
        maker.skillId = SkillId.FIRE_WALL;
        maker.damageLevel = 1;
        maker.authoritative = false;
        maker.presentationOnly = local;
        maker.clientSnapshotKnown = !local;
        maker.nativeFrame = frame;
        maker.nativeLifetimeFrames = Missile.nativeRangeFrames(maker.missile, 1);
        maker.clientSeed = new D2Seed(seed + side, 666);
        Vector2 velocity = world.getMapper(Velocity.class).create(id).velocity;
        velocity.set(axis).nor().scl(side * maker.missile.Vel);
        Vector2 at = world.getMapper(Position.class).create(id).position;
        at.set(10, 20).mulAdd(velocity, frame / 25f);
        maker.start.set(at);
      }
    }

    Missile missile(int id) { return world.getMapper(Missile.class).get(id); }
    void step(int frames) { world.setDelta(frames / 25f); world.process(); }
    @Override public void close() {
      try { world.dispose(); } finally { Riiablo.colors = previousColors; }
    }
  }
}
