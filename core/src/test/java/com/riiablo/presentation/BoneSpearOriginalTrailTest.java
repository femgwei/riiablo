package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.presentation.component.AnimationWrapper;
import java.util.HashMap;
import java.util.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Movement -> client emission -> real asset publication -> animation stepping. No GL. */
class BoneSpearOriginalTrailTest extends RiiabloTest {
  @Test void newestVisibleTrailStaysOneNativeStepBehindInsteadOfCoveringTheHead() {
    JsonValue oracle = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/bone-spear-1.10f.json"));
    JsonValue originalHead = null;
    for (JsonValue unit : oracle.get("units")) {
      if ("client".equals(unit.getString("side")) && "bonespear".equals(unit.getString("missile")))
        originalHead = unit;
    }
    assertNotNull(originalHead);
    Map<Integer, JsonValue> heads = new HashMap<>();
    for (JsonValue state : originalHead.get("states")) heads.put(state.getInt("game_frame"), state);
    int comparisons = 0;
    for (JsonValue unit : oracle.get("units")) {
      if (!"client".equals(unit.getString("side")) || !"bonespeartrail".equals(unit.getString("missile"))) continue;
      for (JsonValue trail : unit.get("states")) {
        JsonValue head = heads.get(trail.getInt("game_frame"));
        if (head == null) continue;
        int age = trail.getInt("total_frames") - trail.getInt("current_frame");
        assertTrue(age >= 1 && age < 8, "captured visible trail excludes birth and expiry");
        assertEquals(head.getInt("velocity_x") * age, head.getInt("x_fixed") - trail.getInt("x_fixed"));
        comparisons++;
      }
    }
    assertTrue(comparisons > 200);
    try (Fixture f = new Fixture()) {
      for (int tick = 1; tick <= 12; tick++) {
        f.step();
        float headX = f.position(f.head).x;
        int visible = 0;
        float nearest = Float.POSITIVE_INFINITY;
        for (int id : f.trails()) {
          if (!f.world.getMapper(AnimationWrapper.class).has(id)) continue;
          Missile trail = f.world.getMapper(Missile.class).get(id);
          assertTrue(trail.nativeFrame >= 1, "newborn trail must not be drawn at the head anchor");
          float gap = headX - f.position(id).x;
          assertEquals(1.125f * trail.nativeFrame, gap, .0001f);
          nearest = Math.min(nearest, gap);
          visible++;
        }
        if (tick >= 3) {
          assertTrue(visible > 0);
          assertEquals(1.125f, nearest, .0001f, "nearest visible tail is one tick behind");
        }
      }
    }
  }

  @Test void warmAndColdAssetsUseTheSameAgeForTrailFramesAndExpiry() {
    for (int loadAt : new int[] {0, 5, 100}) {
      try (Fixture f = new Fixture()) {
        for (int tick = 1; tick <= 12; tick++) {
          f.trailLoaded = tick >= loadAt;
          f.step();
          Missile head = f.world.getMapper(Missile.class).get(f.head);
          assertEquals(head.nativeFrame % 6, f.animation(f.head).getFrame(), "head clock");
          for (int id : f.trails()) {
            Missile trail = f.world.getMapper(Missile.class).get(id);
            assertTrue(trail.nativeFrame < 8, "cold DCC cannot suspend expiry");
            if (!f.world.getMapper(AnimationWrapper.class).has(id)) continue;
            assertEquals(trail.nativeFrame, f.animation(id).getFrame(), "publication must not step twice");
          }
        }
        f.world.delete(f.head);
        for (int tick = 0; tick < 8; tick++) f.step();
        assertEquals(0, f.trails().length, "all trails expire even if their DCC never loads");
      }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final com.riiablo.Colors previousColors = Riiablo.colors;
    final ServerEntityFactory factory = new ServerEntityFactory();
    final Map<String, DCC> assets = new HashMap<>();
    final World world;
    final int head;
    boolean trailLoaded = true;

    @SuppressWarnings("unchecked")
    Fixture() {
      Riiablo.colors = new com.riiablo.Colors();
      AssetManager manager = mock(AssetManager.class);
      for (String name : new String[] {"bonespear", "bonespeartrail"}) {
        String cell = Riiablo.files.Missiles.get(name).CelFile;
        DCC dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + cell + ".dcc")));
        doNothing().when(dcc).loadDirection(anyInt());
        assets.put(cell, dcc);
      }
      when(manager.isLoaded(any(AssetDescriptor.class))).thenAnswer(call -> {
        AssetDescriptor<?> descriptor = call.getArgument(0);
        return !descriptor.fileName.toLowerCase().contains("trail") || trailLoaded;
      });
      when(manager.get(any(AssetDescriptor.class))).thenAnswer(call -> {
        AssetDescriptor<?> descriptor = call.getArgument(0);
        for (Map.Entry<String, DCC> asset : assets.entrySet())
          if (descriptor.fileName.replace('\\', '/').endsWith("/" + asset.getKey() + ".dcc")) return asset.getValue();
        throw new AssertionError(descriptor.fileName);
      });
      ClientGameplayContext context = new ClientGameplayContext(null, () -> -1, () -> null,
          null, manager, null, null, Riiablo.files, null, null, Riiablo.colors,
          Riiablo.string, null, Riiablo.home);
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new ItemManager(), new ObjectInteractor(),
              new WarpInteractor(), new ItemInteractor(), factory, new MissileCollisionSystem(),
              new MissileImpactPresentationSystem(), new OverlayManager(),
              new MissileLoader(), new AnimationStepper())
          .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
          .register("clientContext", context)
          .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
      head = factory.createMissile(Riiablo.files.Missiles.get("bonespear"), Vector2.X,
          new Vector2(4522, 5642), -1, 1);
    }
    Vector2 position(int id) { return world.getMapper(Position.class).get(id).position; }
    Animation animation(int id) { return world.getMapper(AnimationWrapper.class).get(id).animation; }
    int[] trails() {
      IntBag entities = world.getAspectSubscriptionManager().get(Aspect.all(Missile.class)).getEntities();
      return java.util.Arrays.stream(java.util.Arrays.copyOf(entities.getData(), entities.size()))
          .filter(id -> world.getEntityManager().isActive(id)
              && "bonespeartrail".equals(world.getMapper(Missile.class).get(id).missile.Missile)).toArray();
    }
    void step() { world.setDelta(1f / 25f); world.process(); }
    @Override public void close() {
      world.dispose();
      for (DCC asset : assets.values()) asset.dispose();
      Riiablo.colors = previousColors;
    }
  }
}
