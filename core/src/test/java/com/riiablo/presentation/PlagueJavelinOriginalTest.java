package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.presentation.component.AnimationWrapper;
import java.util.*;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Replays the isolated-desktop 1.10f cast through production movement and presentation. */
class PlagueJavelinOriginalTest extends RiiabloTest {
  private JsonValue original() {
    JsonValue data = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/plague-javelin-1.10f.json"));
    assertTrue(data.getBoolean("background_verified"));
    assertTrue(data.getBoolean("owned_game_closed"));
    return data;
  }

  @Test void headAndAllTwentyThreeBurstCloudsMatchEveryOriginalServerState() {
    Map<Integer, List<String>> expected = new HashMap<>();
    int begin = Integer.MAX_VALUE, end = 0, clouds = 0, states = 0;
    for (JsonValue unit : original().get("units")) {
      if (!"server".equals(unit.getString("side"))) continue;
      if ("plaguejavcloud".equals(unit.getString("missile"))) clouds++;
      for (JsonValue state : unit.get("states")) {
        int frame = state.getInt("game_frame");
        begin = Math.min(begin, frame);
        end = Math.max(end, frame);
        expected.computeIfAbsent(frame, ignored -> new ArrayList<>()).add(signature(
            unit.getString("missile"), state.getInt("x_fixed"), state.getInt("y_fixed"),
            state.getInt("velocity_x"), state.getInt("velocity_y"), state.getInt("current_frame")));
        states++;
      }
    }
    assertEquals(23, clouds);
    assertEquals(2016, states);
    try (Flight f = new Flight(false)) {
      // Include birth, all flight ticks, the natural-expiry fanout and its final removal.
      for (int frame = begin; frame <= end + 1; frame++) {
        if (frame != begin) f.step();
        List<String> actual = new ArrayList<>();
        for (int id : frame == begin ? Collections.singletonList(f.head) : f.ids()) {
          Missile m = f.missile(id);
          Position p = f.world.getMapper(Position.class).get(id);
          Vector2 v = f.world.getMapper(Velocity.class).get(id).velocity;
          assertTrue(p.hasNativePrecision());
          actual.add(signature(m.missile.Missile, p.nativePrecisionX(), p.nativePrecisionY(),
              Math.round(v.x * 65536f / 25f), Math.round(v.y * 65536f / 25f),
              m.nativeLifetimeFrames - m.nativeFrame));
        }
        List<String> want = expected.getOrDefault(frame, Collections.emptyList());
        Collections.sort(want);
        Collections.sort(actual);
        assertEquals(want, actual, "server frame " + frame);
      }
    }
  }

  @Test void thirteenStationaryClientTrailsMatchOriginalBirthCellsAgesAndExpiry() {
    Map<Integer, List<String>> expected = new HashMap<>();
    int begin = Integer.MAX_VALUE, end = 0, count = 0;
    for (JsonValue unit : original().get("units")) {
      if ("server".equals(unit.getString("side"))
          && "plaguejavelin".equals(unit.getString("missile")))
        begin = unit.get("states").child.getInt("game_frame");
      if (!"client".equals(unit.getString("side"))
          || !"plaguejavcloud".equals(unit.getString("missile"))
          || unit.get("states").child.getInt("total_frames") != 60) continue;
      count++;
      for (JsonValue state : unit.get("states")) {
        int frame = state.getInt("game_frame");
        end = Math.max(end, frame);
        expected.computeIfAbsent(frame, ignored -> new ArrayList<>()).add(signature("trail",
            state.getInt("x_fixed"), state.getInt("y_fixed"), 0, 0,
            60 - state.getInt("current_frame")));
      }
    }
    assertEquals(13, count);
    try (Flight f = new Flight(true)) {
      for (int frame = begin + 1; frame <= end + 1; frame++) {
        f.step();
        List<String> actual = new ArrayList<>();
        for (int id : f.ids()) {
          Missile m = f.missile(id);
          if (!m.presentationOnly || !"plaguejavcloud".equals(m.missile.Missile)) continue;
          assertFalse(m.authoritative);
          assertFalse(m.poisonCloudTraveling);
          assertEquals(-1, m.ownerId);
          assertEquals(60, m.nativeLifetimeFrames);
          assertTrue(f.world.getMapper(Velocity.class).get(id).velocity.isZero());
          if (m.nativeFrame == 0) continue; // Original periodic snapshots first see age 1.
          Position p = f.world.getMapper(Position.class).get(id);
          assertTrue(p.hasNativePrecision());
          actual.add(signature("trail", p.nativePrecisionX(), p.nativePrecisionY(), 0, 0, m.nativeFrame));
        }
        List<String> want = expected.getOrDefault(frame, Collections.emptyList());
        Collections.sort(want);
        Collections.sort(actual);
        assertEquals(want, actual, "client trail frame " + frame);
      }
    }
  }

  @Test void warmDccPlaybackMatchesOriginalCloudDirectionsAndAllAnimationFrames() {
    replaySprites(false, false);
  }

  @Test void delayedDccPublicationMatchesOriginalWithoutRestartingAnimation() {
    replaySprites(true, false);
  }

  @Test void replicatedBurstFacingDoesNotRequireTheServerOnlyTravelingFlag() {
    replaySprites(false, true);
  }

  private void replaySprites(boolean cold, boolean replicated) {
    JsonValue data = original();
    Map<String, Vector2> ringAngles = new HashMap<>();
    // Facing comes from the real production fanout, never from the expected DCC/path direction.
    try (Flight f = new Flight(false)) {
      for (int i = 0; i < 15; i++) f.step();
      for (int id : f.ids()) {
        Vector2 v = f.world.getMapper(Velocity.class).get(id).velocity;
        ringAngles.put(velocityKey(Math.round(v.x * 65536f / 25f), Math.round(v.y * 65536f / 25f)),
            new Vector2(f.world.getMapper(Angle.class).get(id).angle));
      }
    }
    Map<Integer, JsonValue> units = new HashMap<>();
    Map<Integer, List<JsonValue>> selections = new TreeMap<>();
    for (JsonValue unit : data.get("units")) if ("client".equals(unit.getString("side")))
      units.put(unit.getInt("unit_id"), unit);
    for (JsonValue sample : data.get("samples"))
      selections.computeIfAbsent(sample.getInt("unit_id"), ignored -> new ArrayList<>()).add(sample);
    Map<String, DCC> assets = new HashMap<>();
    Riiablo.colors = new com.riiablo.Colors();
    World world = new World(new WorldConfigurationBuilder().with(new DirectionResolver(), new AnimationStepper()).build());
    world.setDelta(1f / 25f);
    int count = 0, cloudDirections = 0;
    try {
      for (Map.Entry<Integer, List<JsonValue>> entry : selections.entrySet()) {
        JsonValue unit = units.get(entry.getKey()), birth = unit.get("states").child;
        String name = unit.getString("missile");
        boolean cloud = "plaguejavcloud".equals(name), burst = cloud && birth.getInt("total_frames") == 87;
        Missiles.Entry row = Riiablo.files.Missiles.get(name);
        DCC dcc = assets.get(name);
        if (dcc == null) {
          dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + row.CelFile + ".dcc")));
          doNothing().when(dcc).loadDirection(anyInt());
          assets.put(name, dcc);
        }
        Vector2 facing = burst ? ringAngles.get(velocityKey(birth.getInt("velocity_x"), birth.getInt("velocity_y")))
            : "poisonpuff".equals(name) ? new Vector2(birth.getInt("velocity_x"), birth.getInt("velocity_y")).nor()
            : Vector2.X;
        assertNotNull(facing, "each original cloud has a production ring path");
        int id = -1, age = 0;
        for (JsonValue sample : entry.getValue()) {
          if (cold || id < 0) {
            if (id >= 0) world.delete(id);
            id = world.create();
            Missile m = world.getMapper(Missile.class).create(id);
            m.missile = row;
            m.presentationOnly = cloud && !burst;
            m.authoritative = !replicated;
            m.poisonCloudTraveling = burst && !replicated;
            m.nativeLifetimeFrames = birth.getInt("total_frames");
            m.nativeFrame = age = cold ? sample.getInt("native_age") : 0;
            world.getMapper(Angle.class).create(id).set(facing);
            Animation a = world.getMapper(AnimationWrapper.class).create(id).animation;
            a.edit().layer(dcc).build();
            a.setMode(row.LoopAnim > 0 ? Animation.Mode.LOOP : Animation.Mode.CLAMP);
            a.setFrameDelta(MissileLoader.nativeFrameDelta(null, m));
            if (row.SubLoop > 0 && a.isLooping()) a.setSubLoop(row.SubStart, row.SubStop);
            MissileLoader.initializePlayback(m, row, a, row.RandStart);
            world.process();
          }
          Missile m = world.getMapper(Missile.class).get(id);
          while (age < sample.getInt("native_age")) { m.nativeFrame = ++age; world.process(); }
          world.process(); // A second render pass cannot advance the simulation-owned animation.
          Animation a = world.getMapper(AnimationWrapper.class).get(id).animation;
          String context = name + " unit " + entry.getKey() + " age " + age;
          assertEquals(sample.getInt("frame"), a.getFrame(), context);
          assertEquals(sample.getInt("dcc_direction"), a.getDirection(), context);
          if (cloud) cloudDirections++;
          assertNotNull(dcc.getFrame(a.getDirection(), a.getFrame()));
          count++;
        }
        world.delete(id);
        world.process();
      }
      assertEquals(2721, count);
      assertEquals(1367, cloudDirections);
    } finally {
      world.dispose();
      for (DCC dcc : assets.values()) dcc.dispose();
    }
  }

  @Test void actualLoaderPublishesStationaryTrailDccAtItsCurrentAge() {
    Riiablo.colors = new com.riiablo.Colors();
    DCC dcc = spy(DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/PoisonSparks.dcc")));
    doNothing().when(dcc).loadDirection(anyInt());
    AssetManager saved = Riiablo.assets;
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenReturn(dcc);
    World world = new World(new WorldConfigurationBuilder().with(new MissileLoader()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int id = world.create();
      Missile m = world.getMapper(Missile.class).create(id);
      m.missile = Riiablo.files.Missiles.get("plaguejavcloud");
      m.presentationOnly = true;
      m.nativeLifetimeFrames = 60;
      m.nativeFrame = 19;
      world.getMapper(Angle.class).create(id).set(Vector2.X);
      world.process();
      AnimationWrapper wrapper = world.getMapper(AnimationWrapper.class).get(id);
      assertNotNull(wrapper, "stationary trails must have a visible PoisonSparks base layer");
      assertEquals(4, wrapper.animation.getDirection());
      assertEquals(10, wrapper.animation.getFrame(), "intro wraps directly into native middle loop");
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      dcc.dispose();
    }
  }

  private static String velocityKey(int x, int y) { return x + ":" + y; }
  private static String signature(String name, int x, int y, int vx, int vy, int clock) {
    return name + ":" + x + ":" + y + ":" + vx + ":" + vy + ":" + clock;
  }

  private static final class Flight implements AutoCloseable {
    final com.riiablo.Colors oldColors = Riiablo.colors;
    final ServerEntityFactory factory = new ServerEntityFactory();
    final World world;
    final int head;
    Flight(boolean client) {
      if (Riiablo.colors == null) Riiablo.colors = new com.riiablo.Colors();
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(new EventSystem(),
          new ItemManager(), new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
          factory, new MissileCollisionSystem());
      if (client) builder.with(new OverlayManager(), new MissileImpactPresentationSystem());
      world = new World(builder.build().register("factory", factory)
          .register("map", new com.riiablo.map.Map(0, 0))
          .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry())
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      int owner = world.create();
      world.getMapper(Position.class).create(owner).position.set(4522, 5642);
      head = factory.createMissile(Riiablo.files.Missiles.get("plaguejavelin"),
          Vector2.X, new Vector2(4522, 5642), owner, 1);
      missile(head).skillId = 25;
      world.setDelta(1f / 25f);
    }
    Missile missile(int id) { return world.getMapper(Missile.class).get(id); }
    List<Integer> ids() {
      com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager()
          .get(Aspect.all(Missile.class, Position.class, Velocity.class)).getEntities();
      List<Integer> result = new ArrayList<>();
      for (int i = 0; i < ids.size(); i++) result.add(ids.get(i));
      return result;
    }
    void step() { world.process(); }
    @Override public void close() { try { world.dispose(); } finally { Riiablo.colors = oldColors; } }
  }
}
