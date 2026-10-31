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
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.presentation.component.AnimationWrapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Production callbacks with an independent uint64 RNG oracle; no GL or DCC required. */
class PlagueJavelinPresentationLifecycleTest extends RiiabloTest {
  @Test void smokeIsBornAtParentAndMovesTowardNativeRandomTargetsWithoutConsumingServerSeed() {
    for (boolean authoritative : new boolean[] {true, false}) {
      try (Fixture f = new Fixture(false)) {
        int parent = f.cloud(1, authoritative);
        Missile cloud = f.missile(parent);
        long oracle = seed(0x12345678, 999);
        cloud.rngState = 0x76543210;
        cloud.nativeSeed = new D2Seed((int) oracle, (int) (oracle >>> 32));
        boolean positive = false, negative = false;
        for (int tick = 0; tick < 12; tick++) {
          oracle = roll(oracle); // interval=1 still consumes one native roll
          oracle = roll(oracle);
          int dx = offset(oracle, 6);
          oracle = roll(oracle);
          int dy = offset(oracle, 6);
          f.step();
          assertEquals(tick + 1, f.children.size());
          int child = f.children.get(tick);
          assertEquals(new Vector2(10.25f, 20.75f), f.births.get(tick));
          Vector2 expected = new Vector2(10 + dx - 10.25f, 20 + dy - 20.75f).nor();
          assertTrue(expected.epsilonEquals(f.directions.get(tick), 0.00001f),
              "target direction " + expected + " actual " + f.directions.get(tick));
          Missile puff = f.missile(child);
          assertTrue(puff.presentationOnly);
          assertFalse(puff.authoritative);
          assertEquals(-1, puff.ownerId);
          assertEquals(25, puff.nativeLifetimeFrames);
          assertEquals((int) oracle, cloud.clientSeed.getNLowSeed());
          assertEquals((int) (oracle >>> 32), cloud.clientSeed.getNHighSeed());
          positive |= dx > 0;
          negative |= dx < 0;
        }
        assertTrue(positive && negative, "stationary parents must not send every puff along +X");
        assertEquals(0x76543210, cloud.rngState);
        assertEquals(0x12345678, cloud.nativeSeed.getNLowSeed());
        assertEquals(999, cloud.nativeSeed.getNHighSeed());
        assertNotSame(cloud.nativeSeed, cloud.clientSeed);
      }
    }
  }

  @Test void nativeChanceRunsOnFirstUpdateAndUsesRemainingNotElapsedFrameZero() {
    try (Fixture f = new Fixture(false)) {
      int parent = f.cloud(24, false);
      Missile cloud = f.missile(parent);
      cloud.rngState = 0x12345678;
      long oracle = seed(cloud.rngState, 666);
      int emitted = 0;
      for (int tick = 0; tick < 60; tick++) {
        oracle = roll(oracle);
        if ((oracle & 0xffffffffL) % 24 == 0) {
          emitted++;
          oracle = roll(roll(oracle)); // target X/Y
        }
        f.step();
        assertEquals(emitted, f.children.size(), "tick " + tick);
      }
      f.step(); // remaining=0: unconditional puff, no chance draw
      oracle = roll(roll(oracle));
      assertEquals(emitted + 1, f.children.size());
      assertEquals((int) oracle, cloud.clientSeed.getNLowSeed());
      assertEquals((int) (oracle >>> 32), cloud.clientSeed.getNHighSeed());
      assertEquals(61, cloud.clientFrame, "DCC readiness must not pause the callback");
    }
  }

  @Test void movingSmokeExpiresAfterTwentyFiveUpdatesEvenWithColdDcc() {
    for (boolean loaded : new boolean[] {true, false}) {
      try (Fixture f = new Fixture(loaded)) {
        int parent = f.cloud(1, true);
        f.step();
        int child = f.children.get(0);
        Vector2 birth = new Vector2(f.births.get(0));
        f.world.delete(parent);
        for (int update = 1; update < 25; update++) {
          f.step();
          assertTrue(f.active(child), "update " + update);
          assertEquals(update, f.missile(child).nativeFrame);
        }
        assertTrue(f.world.getMapper(Position.class).get(child).position.dst2(birth) > 0f);
        f.step();
        assertFalse(f.active(child));
        assertEquals(1, f.children.size(), "terminal smoke cannot emit more children");
      }
    }
  }

  @Test void plagueTrailRequiresIntegerPathMovementAndDoesNotWaitForDcc() {
    try (Fixture f = new Fixture(false)) {
      int parent = f.parent(Riiablo.files.Missiles.get("plaguejavelin"), true);
      Position at = f.world.getMapper(Position.class).get(parent);
      f.step();
      at.position.x = 10.9f;
      f.step();
      assertEquals(0, f.cloudCount());
      at.position.x = 11f;
      f.step();
      assertEquals(1, f.cloudCount());
      at.position.x = 11.2f;
      f.step();
      assertEquals(1, f.cloudCount());
      at.position.y = 21f;
      f.step();
      assertEquals(2, f.cloudCount());
      assertEquals(5, f.missile(parent).clientFrame);
    }
  }

  @Test void replicatedServerHitRingDoesNotCreateAnExtraCentralClientCloud() {
    try (Fixture f = new Fixture(false)) {
      Missiles.Entry row = Riiablo.files.Missiles.get("plaguejavelin");
      assertTrue(MissileImpactPresentationSystem.usesServerPoisonCloudFanout(row));
      f.system.onMissileImpact(MissileImpactEvent.obtain(1, row.Id, -1, -1,
          new Vector2(10, 20), Vector2.X));
      assertEquals(0, f.children.size());
      Missiles.Entry custom = new Missiles.Entry();
      custom.pCltHitFunc = 2;
      custom.pSrvHitFunc = 2;
      custom.CltHitSubMissile = new String[] {"plaguejavcloud"};
      custom.HitSubMissile = new String[] {"other"};
      assertFalse(MissileImpactPresentationSystem.usesServerPoisonCloudFanout(custom));
    }
  }

  @Test void pooledMissileClearsPresentationSeedAndPathCell() {
    class Resettable extends Missile { void recycle() { reset(); } }
    Resettable missile = new Resettable();
    missile.clientSeed = new D2Seed(123, 456);
    missile.clientFlightInitialized = true;
    missile.clientPathX = 10;
    missile.clientPathY = 20;
    missile.recycle();
    assertNull(missile.clientSeed);
    assertFalse(missile.clientFlightInitialized);
    assertEquals(0, missile.clientPathX);
    assertEquals(0, missile.clientPathY);
  }

  private static long seed(int low, int high) {
    return (Integer.toUnsignedLong(high) << 32) | Integer.toUnsignedLong(low);
  }
  private static long roll(long seed) {
    return (seed & 0xffffffffL) * 0x6AC690C5L + (seed >>> 32);
  }
  private static int offset(long seed, int radius) {
    int d = (int) ((seed & 0xffffffffL) % (radius * 2)) - radius;
    return d + (d < 0 ? -radius : radius);
  }

  private static final class Fixture implements AutoCloseable {
    final MissileImpactPresentationSystem system = new MissileImpactPresentationSystem();
    final EntityFactory factory = mock(EntityFactory.class);
    final World world;
    final List<Integer> children = new ArrayList<>();
    final List<Vector2> births = new ArrayList<>();
    final List<Vector2> directions = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    final com.riiablo.Colors previousColors = Riiablo.colors;

    Fixture(boolean loaded) {
      if (Riiablo.colors == null) Riiablo.colors = new com.riiablo.Colors();
      world = new World(new WorldConfigurationBuilder()
          .with(system, factory, new OverlayManager()).build()
          .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(-1))).thenAnswer(call -> {
            Missiles.Entry row = call.getArgument(0);
            Vector2 direction = new Vector2((Vector2) call.getArgument(1));
            Vector2 birth = new Vector2((Vector2) call.getArgument(2));
            int child = parent(row, false);
            missile(child).start.set(birth);
            world.getMapper(Position.class).get(child).position.set(birth);
            world.getMapper(Velocity.class).get(child).velocity.set(direction).scl(row.Vel);
            if (loaded) world.getMapper(AnimationWrapper.class).create(child);
            children.add(child);
            births.add(birth);
            directions.add(direction);
            names.add(row.Missile);
            return child;
          });
    }
    Missile missile(int id) { return world.getMapper(Missile.class).get(id); }
    boolean active(int id) { return world.getEntityManager().isActive(id); }
    long cloudCount() { return names.stream().filter("plaguejavcloud"::equals).count(); }
    int cloud(int interval, boolean authoritative) {
      Missiles.Entry row = new Missiles.Entry();
      row.Missile = "test-plague-cloud";
      row.pCltDoFunc = 4;
      row.CltSubMissile = new String[] {"poisonpuff"};
      row.CltParam = new int[] {interval, 1, 6};
      return parent(row, authoritative);
    }
    int parent(Missiles.Entry row, boolean authoritative) {
      int id = world.create();
      Missile missile = world.getMapper(Missile.class).create(id);
      missile.missile = row;
      missile.authoritative = authoritative;
      missile.nativeLifetimeFrames = 60;
      missile.start.set(10.25f, 20.75f);
      world.getMapper(Position.class).create(id).position.set(missile.start);
      world.getMapper(Velocity.class).create(id);
      return id;
    }
    void step() { world.setDelta(1f / 25f); world.process(); }
    @Override public void close() {
      try { world.dispose(); } finally { Riiablo.colors = previousColors; }
    }
  }
}
