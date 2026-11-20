package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Captured original vectors/positions through the production factory and mover. */
class PoisonJavelinOriginalMotionTest extends RiiabloTest {
  @Test void factoryAndFlightMatchOriginalServerPath() {
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/poison-javelin-1.10f.json"));
    replayHead(fixture);
  }

  private void replayHead(JsonValue fixture) {
    JsonValue states = null;
    for (JsonValue unit : fixture.get("units")) {
      if ("server".equals(unit.getString("side")) && "poisonjav".equals(unit.getString("missile"))) {
        assertNull(states, "one original server javelin");
        states = unit.get("states");
      }
    }
    assertNotNull(states);
    assertEquals(25, states.size);
    ServerEntityFactory factory = new ServerEntityFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ItemManager(), new ObjectInteractor(),
            new WarpInteractor(), new ItemInteractor(), factory, new MissileCollisionSystem())
        .build().register("factory", factory).register("map", new Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      JsonValue first = states.child;
      int id = factory.createMissile(Riiablo.files.Missiles.get("poisonjav"),
          new Vector2(first.getInt("velocity_x"), first.getInt("velocity_y")).nor(),
          new Vector2(first.getInt("x_fixed") / 65536f - .5f,
              first.getInt("y_fixed") / 65536f - .5f), -1, 1);
      world.setDelta(1f / 25f);
      for (JsonValue state : states) {
        if (state != first) world.process();
        Vector2 velocity = world.getMapper(Velocity.class).get(id).velocity;
        assertEquals(state.getInt("velocity_x"), Math.round(velocity.x * 65536f / 25f), "x speed");
        assertEquals(state.getInt("velocity_y"), Math.round(velocity.y * 65536f / 25f), "y speed");
        Position position = world.getMapper(Position.class).get(id);
        assertTrue(position.hasNativePrecision());
        assertEquals(state.getInt("x_fixed"), position.nativePrecisionX());
        assertEquals(state.getInt("y_fixed"), position.nativePrecisionY());
        Missile missile = world.getMapper(Missile.class).get(id);
        assertEquals(state.getInt("current_frame"), missile.nativeLifetimeFrames - missile.nativeFrame);
      }
      world.process();
      assertFalse(world.getEntityManager().isActive(id), "expires after twenty-five native updates");
    } finally { world.dispose(); }
  }

  @Test void allTwentyFourCloudBirthsPositionsAndExpirationsMatchOriginal() {
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/poison-javelin-1.10f.json"));
    replayClouds(fixture, 24);
  }

  @Test void cardinalAndDiagonalBatchMatchesOriginalMovementAndCloudGaps() {
    JsonValue batch = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/poison-javelin-directions-1.10f.json"));
    assertTrue(batch.getBoolean("background_verified"));
    assertTrue(batch.getBoolean("owned_game_closed"));
    assertEquals(2, batch.get("casts").size);
    for (JsonValue cast : batch.get("casts")) {
      assertTrue(cast.getBoolean("full_flight_observed"));
      replayHead(cast);
      replayClouds(cast, cast.get("identity").getInt("direction_index") == 4 ? 19 : 24);
    }
  }

  private void replayClouds(JsonValue fixture, int expectedClouds) {
    java.util.Map<Integer, java.util.List<String>> expected = new java.util.HashMap<>();
    int firstFrame = Integer.MAX_VALUE, lastFrame = 0, clouds = 0;
    for (JsonValue unit : fixture.get("units")) {
      if (!"server".equals(unit.getString("side"))) continue;
      String name = unit.getString("missile");
      if ("poisonjavcloud".equals(name)) clouds++;
      for (JsonValue state : unit.get("states")) {
        int frame = state.getInt("game_frame");
        firstFrame = Math.min(firstFrame, frame);
        lastFrame = Math.max(lastFrame, frame);
        expected.computeIfAbsent(frame, ignored -> new java.util.ArrayList<>()).add(
            signature(name, state.getInt("x_fixed"), state.getInt("y_fixed"), state.getInt("current_frame")));
      }
    }
    assertEquals(expectedClouds, clouds);
    ServerEntityFactory factory = new ServerEntityFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ItemManager(), new ObjectInteractor(), new WarpInteractor(),
            new ItemInteractor(), factory, new MissileCollisionSystem()).build()
        .register("factory", factory).register("map", new Map(0, 0))
        .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
    try {
      JsonValue cast = fixture.get("identity");
      int root = factory.createMissile(Riiablo.files.Missiles.get("poisonjav"),
          new Vector2(cast.getInt("target_x") - cast.getInt("x"),
              cast.getInt("target_y") - cast.getInt("y")).nor(),
          new Vector2(cast.getInt("x"), cast.getInt("y")), -1, 1);
      world.getMapper(Missile.class).get(root).skillId = 15;
      world.setDelta(1f / 25f);
      for (int frame = firstFrame + 1; frame <= lastFrame + 1; frame++) {
        world.process();
        java.util.List<String> actual = new java.util.ArrayList<>();
        com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager()
            .get(com.artemis.Aspect.all(Missile.class, Position.class)).getEntities();
        for (int i = 0; i < ids.size(); i++) {
          int id = ids.get(i);
          Missile missile = world.getMapper(Missile.class).get(id);
          Position at = world.getMapper(Position.class).get(id);
          assertTrue(at.hasNativePrecision(), missile.missile.Missile);
          actual.add(signature(missile.missile.Missile, at.nativePrecisionX(), at.nativePrecisionY(),
              missile.nativeLifetimeFrames - missile.nativeFrame));
        }
        java.util.List<String> want = expected.getOrDefault(frame, java.util.Collections.emptyList());
        java.util.Collections.sort(want);
        java.util.Collections.sort(actual);
        assertEquals(want, actual, "native frame " + frame);
      }
    } finally { world.dispose(); }
  }

  private static String signature(String name, int x, int y, int remaining) {
    return name + ":" + x + ":" + y + ":" + remaining;
  }
}
