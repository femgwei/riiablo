package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Real server movement plus independent client callbacks; no window or GPU. */
class PoisonJavelinOriginalClientTrailTest extends RiiabloTest {
  @Test void localClientCloudsMatchOriginalCardinalAndDiagonalLifecycles() {
    JsonValue batch = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/poison-javelin-directions-1.10f.json"));
    for (JsonValue cast : batch.get("casts")) {
      java.util.Map<Integer, List<String>> expected = new HashMap<>();
      int firstFrame = Integer.MAX_VALUE, lastFrame = 0;
      for (JsonValue unit : cast.get("units")) {
        for (JsonValue state : unit.get("states")) {
          int frame = state.getInt("game_frame");
          if ("server".equals(unit.getString("side"))
              && "poisonjav".equals(unit.getString("missile"))) firstFrame = Math.min(firstFrame, frame);
          if (!"client".equals(unit.getString("side"))
              || !"poisonjavcloud".equals(unit.getString("missile"))) continue;
          lastFrame = Math.max(lastFrame, frame);
          expected.computeIfAbsent(frame, ignored -> new ArrayList<>()).add(
              signature(state.getInt("x_fixed"), state.getInt("y_fixed"),
                  state.getInt("total_frames") - state.getInt("current_frame")));
        }
      }
      try (Fixture f = new Fixture(true)) {
        JsonValue identity = cast.get("identity");
        f.head(new Vector2(identity.getInt("x"), identity.getInt("y")),
            new Vector2(identity.getInt("target_x") - identity.getInt("x"),
                identity.getInt("target_y") - identity.getInt("y")).nor(), true);
        for (int frame = firstFrame + 1; frame <= lastFrame + 1; frame++) {
          f.step();
          List<String> actual = f.clouds(true);
          List<String> want = expected.getOrDefault(frame, Collections.emptyList());
          Collections.sort(want);
          assertEquals(want, actual, "direction " + identity.getInt("direction_index") + " frame " + frame);
        }
        com.artemis.utils.IntBag ids = f.world.getAspectSubscriptionManager()
            .get(Aspect.all(Missile.class)).getEntities();
        for (int i = 0; i < ids.size(); i++) {
          assertNotEquals("poisonjav", f.world.getMapper(Missile.class).get(ids.get(i)).missile.Missile,
              "the original head expired; its ECS id may be reused by smoke");
        }
      }
    }
  }

  @Test void sparseNetworkHistoryMatchesDenseCloudsAndDoesNotReplayRepeatedPackets() {
    try (Fixture dense = new Fixture(false); Fixture sparse = new Fixture(false)) {
      int a = dense.head(new Vector2(4521, 5642), Vector2.X, false);
      int b = sparse.head(new Vector2(4521, 5642), Vector2.X, false);
      for (int frame = 1; frame <= 24; frame++) {
        dense.snapshot(a, frame);
        dense.step();
      }
      sparse.snapshot(b, 24);
      sparse.step();
      assertEquals(dense.clouds(false), sparse.clouds(false));
      assertEquals(23, sparse.clouds(false).size());
      sparse.snapshot(b, 24);
      sparse.step();
      dense.snapshot(a, 24);
      dense.step();
      assertEquals(dense.clouds(false), sparse.clouds(false));
      assertEquals(23, sparse.clouds(false).size(), "repeated terminal packet creates no extra cloud");
    }
  }

  @Test void serverCloudSuppressionIsLimitedToPoisonJavelinDamageCarriers() {
    Missile cloud = new Missile();
    cloud.missile = Riiablo.files.Missiles.get("poisonjavcloud");
    cloud.skillId = 15;
    assertTrue(NativePoisonJavelinPresentation.serverTrailCloud(cloud));
    cloud.presentationOnly = true;
    assertFalse(NativePoisonJavelinPresentation.serverTrailCloud(cloud));
    cloud.presentationOnly = false;
    cloud.skillId = -1;
    assertFalse(NativePoisonJavelinPresentation.serverTrailCloud(cloud), "trap/legacy cloud retained");
    cloud.skillId = 15;
    cloud.missile = Riiablo.files.Missiles.get("plaguejavcloud");
    assertFalse(NativePoisonJavelinPresentation.serverTrailCloud(cloud));
  }

  @Test void restoredClientPathCacheDoesNotInventAStationaryTrailCell() {
    try (Fixture f = new Fixture(false)) {
      int id = f.head(new Vector2(4521, 5642), Vector2.X, false);
      // A deliberately slow projectile remains in the same integer cell.
      f.world.getMapper(Velocity.class).get(id).velocity.set(1, 0);
      for (int age = 1; age <= 3; age++) { f.snapshot(id, age); f.step(); }
      Missile root = f.world.getMapper(Missile.class).get(id);
      root.clientPathX = root.clientPathY = 0;
      root.clientSampleFrame = -1; // Server checkpoint omits local path caches.
      f.snapshot(id, 4);
      f.step();
      assertTrue(f.clouds(false).isEmpty());
    }
  }

  private static String signature(int x, int y, int age) { return x + ":" + y + ":" + age; }

  private static final class Fixture implements AutoCloseable {
    final com.riiablo.Colors oldColors = Riiablo.colors;
    final ServerEntityFactory factory = new ServerEntityFactory();
    final World world;
    Fixture(boolean collision) {
      if (Riiablo.colors == null) Riiablo.colors = new com.riiablo.Colors();
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(
          new EventSystem(), new ItemManager(), new ObjectInteractor(),
          new WarpInteractor(), new ItemInteractor(), factory);
      if (collision) builder.with(new MissileCollisionSystem());
      builder.with(new OverlayManager(), new MissileImpactPresentationSystem());
      world = new World(builder.build().register("factory", factory).register("map", new Map(0, 0))
          .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry())
          .register("clientContext", ClientGameplayContext.fromGlobals()));
    }
    int head(Vector2 at, Vector2 direction, boolean authoritative) {
      int id = factory.createMissile(Riiablo.files.Missiles.get("poisonjav"), direction, at, -1, 1);
      Missile m = world.getMapper(Missile.class).get(id);
      m.skillId = 15;
      m.authoritative = authoritative;
      m.clientSnapshotKnown = !authoritative;
      m.presentationSeedKnown = true;
      m.presentationSeed = 123456;
      return id;
    }
    void snapshot(int id, int age) {
      Missile m = world.getMapper(Missile.class).get(id);
      m.nativeFrame = age;
      Vector2 v = world.getMapper(Velocity.class).get(id).velocity;
      world.getMapper(Position.class).get(id).setNativePrecision(
          (4521 << 16) + 32768 + Math.round(v.x * 65536f / 25f) * age,
          (5642 << 16) + 32768 + Math.round(v.y * 65536f / 25f) * age);
    }
    List<String> clouds(boolean sampledOnly) {
      List<String> result = new ArrayList<>();
      com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager()
          .get(Aspect.all(Missile.class, Position.class)).getEntities();
      for (int i = 0; i < ids.size(); i++) {
        int id = ids.get(i);
        Missile m = world.getMapper(Missile.class).get(id);
        if (NativePoisonJavelinPresentation.serverTrailCloud(m)) {
          assertEquals(0, m.clientFrame, "server damage cloud must not run a second smoke callback");
          assertNull(m.clientSeed);
        }
        if (!m.presentationOnly || !"poisonjavcloud".equals(m.missile.Missile)) continue;
        if (sampledOnly && m.nativeFrame == 0) continue; // Native periodic snapshots first see age 1.
        assertFalse(m.authoritative);
        assertEquals(-1, m.ownerId);
        assertTrue(world.getMapper(Velocity.class).get(id).velocity.isZero());
        Position p = world.getMapper(Position.class).get(id);
        assertTrue(p.hasNativePrecision());
        result.add(signature(p.nativePrecisionX(), p.nativePrecisionY(), m.nativeFrame));
      }
      Collections.sort(result);
      return result;
    }
    void step() { world.setDelta(1f / 25f); world.process(); }
    @Override public void close() { try { world.dispose(); } finally { Riiablo.colors = oldColors; } }
  }
}
