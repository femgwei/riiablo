package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class MoltenBoulderOriginalParityTest extends RiiabloTest {
  private JsonValue original() throws Exception {
    try (java.io.InputStream in = getClass().getResourceAsStream("/original/molten-boulder-1.10f.json")) {
      assertNotNull(in);
      return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test void nativeFireGrowthLoopAndDecayMatchEveryCapturedState() throws Exception {
    int checked = 0;
    for (JsonValue unit : original().get("units")) {
      String name = unit.getString("missile");
      if (!"client".equals(unit.getString("side"))
          || !Set.of("firewall", "firesmall", "firemedium", "moltenboulderfirepath").contains(name)) continue;
      JsonValue states = unit.get("states");
      int lifetime = states.child.getInt("total_frames");
      JsonValue beforeRandom = states.child;
      // The snapshot immediately before CltDo5's random draw includes RNG
      // advances from other client callbacks (e.g. flame collision/sound).
      // This verifies the animation callback, not those unrelated RNG users.
      for (JsonValue state : states)
        if (lifetime - state.getInt("current_frame") == 11) beforeRandom = state;
      int seed = beforeRandom.getInt("seed_low"), high = beforeRandom.getInt("seed_high");
      for (JsonValue state : states) {
        int age = lifetime - state.getInt("current_frame");
        assertEquals(state.getInt("unit_44") >>> 8, NativeFirestormPresentation.groundFrame(
            Riiablo.files.Missiles.get(name), lifetime, seed, high, 37, age, true),
            name + " unit=" + unit.getInt("unit_id") + " age=" + age);
        checked++;
      }
    }
    assertTrue(checked > 2000);
  }

  @Test void productionCastMatchesCapturedRollMotionFramesAndSeparateFireLifetimes() throws Exception {
    JsonValue fixture = original();
    Map<String, Object> production = MoltenBoulderOriginalAnalysis.simulate(fixture);
    JsonValue nativeRoll = null;
    for (JsonValue unit : fixture.get("units"))
      if ("server".equals(unit.getString("side")) && "moltenboulder".equals(unit.getString("missile")))
        nativeRoll = unit;
    assertNotNull(nativeRoll);
    JsonValue first = nativeRoll.get("states").child;
    Map<Integer, Integer> frames = new HashMap<>();
    for (Object item : (List<?>) production.get("rolling_states")) {
      Map<?, ?> state = (Map<?, ?>) item;
      int age = ((Number) state.get("native_age")).intValue();
      List<?> at = (List<?>) state.get("position");
      assertEquals((first.getInt("x_fixed") + (long) age * first.getInt("velocity_x")) / 65536.0 - .5,
          ((Number) at.get(0)).doubleValue(), .00001, "roll x at " + age);
      assertEquals(first.getInt("y_fixed") / 65536.0 - .5,
          ((Number) at.get(1)).doubleValue(), .00001);
      frames.put(age, ((Number) state.get("frame")).intValue());
    }
    int compared = 0;
    for (JsonValue sample : fixture.get("samples")) {
      if (!"moltenboulder".equals(sample.getString("missile"))) continue;
      int age = sample.getInt("native_age");
      assertEquals(sample.getInt("frame"), frames.get(age), "DCC frame at age " + age);
      compared++;
    }
    assertEquals(80, compared);
    Map<?, ?> server = (Map<?, ?>) production.get("server_counts");
    assertEquals(37, server.get("moltenboulderfirepath"));
    int longFire = 0, shortFire = 0, trailVisuals = 0, impactVisuals = 0;
    Map<String, Integer> variants = new HashMap<>();
    List<Integer> flightFrames = new ArrayList<>();
    List<Map<?, ?>> fragments = new ArrayList<>();
    for (Object item : (List<?>) production.get("births")) {
      Map<?, ?> birth = (Map<?, ?>) item;
      String name = birth.get("missile").toString();
      int life = ((Number) birth.get("lifetime")).intValue();
      if (Boolean.TRUE.equals(birth.get("presentation_only"))) {
        if (Set.of("firewall", "firesmall", "firemedium").contains(name)) {
          trailVisuals++; variants.merge(name, 1, Integer::sum); assertEquals(90, life);
        } else if ("moltenboulderfirepath".equals(name)) { impactVisuals++; assertEquals(37, life); }
        else if ("moltenboulder-flyingrocks".equals(name)) { flightFrames.add(life); fragments.add(birth); }
      } else if ("moltenboulderfirepath".equals(name)) {
        if (life == 37) longFire++; else { assertEquals(7, life); shortFire++; }
      }
    }
    assertEquals(19, longFire); assertEquals(18, shortFire);
    assertEquals(19, trailVisuals); assertEquals(18, impactVisuals);
    assertEquals(Map.of("firewall", 8, "firesmall", 7, "firemedium", 4), variants);
    Collections.sort(flightFrames);
    assertEquals(List.of(14, 19, 22, 24, 28, 32, 36, 38, 39), flightFrames);
    int rock = 0;
    for (JsonValue unit : fixture.get("units")) {
      if (!"client".equals(unit.getString("side"))
          || !"moltenboulder-flyingrocks".equals(unit.getString("missile"))) continue;
      JsonValue state = unit.get("states").child;
      com.badlogic.gdx.math.Vector2 displacement = new com.badlogic.gdx.math.Vector2(
          state.getInt("velocity_x") / 65536f, state.getInt("velocity_y") / 65536f)
          .scl(state.getInt("total_frames"));
      com.badlogic.gdx.math.Vector2 pixels = com.riiablo.util.EngineUtils.worldToScreenCoords(
          displacement, new com.badlogic.gdx.math.Vector2());
      Map<?, ?> fragment = fragments.get(rock++);
      List<?> end = (List<?>) fragment.get("end_offset_pixels");
      assertEquals(pixels.x, ((Number) end.get(0)).floatValue(), .001f);
      assertEquals(pixels.y, ((Number) end.get(1)).floatValue(), .001f);
      int originalDirection = -1;
      for (JsonValue sample : fixture.get("samples"))
        if ("moltenboulder-flyingrocks".equals(sample.getString("missile"))
            && sample.getInt("unit_id") == unit.getInt("unit_id")) {
          originalDirection = sample.getInt("dcc_direction"); break;
        }
      assertTrue(originalDirection >= 0);
      assertEquals(originalDirection, fragment.get("dcc_direction"));
    }
  }

  @Test void flyingRockHeldAndFinalAnimationMatchEveryCapturedCursor() throws Exception {
    int compared = 0;
    for (JsonValue unit : original().get("units")) {
      if (!"client".equals(unit.getString("side"))
          || !"moltenboulder-flyingrocks".equals(unit.getString("missile"))) continue;
      for (JsonValue state : unit.get("states")) {
        int life = state.getInt("total_frames"), age = life - state.getInt("current_frame");
        assertEquals(state.getInt("unit_44") >>> 8,
            NativeMoltenBoulderPresentation.rockFrame(16, life, age), "flight=" + life + " age=" + age);
        compared++;
      }
    }
    assertTrue(compared > 200);
  }

  @Test void nativeDirectionsMatchCapturedBoulderAndRockDccSelections() throws Exception {
    int compared = 0;
    for (JsonValue sample : original().get("samples")) {
      if (!Set.of("moltenboulder", "moltenboulderemerge", "moltenboulder-flyingrocks")
          .contains(sample.getString("missile"))) continue;
      com.badlogic.gdx.math.Vector2 facing = new com.badlogic.gdx.math.Vector2(
          sample.getInt("velocity_x") / 65536f, sample.getInt("velocity_y") / 65536f);
      int direction = "moltenboulder-flyingrocks".equals(sample.getString("missile"))
          ? NativeMoltenBoulderPresentation.pathDirection(sample.getInt("path_direction"), 8)
          : NativeMoltenBoulderPresentation.direction(facing, 8);
      assertEquals(sample.getInt("dcc_direction"), direction,
          "native path direction " + sample.getInt("path_direction"));
      compared++;
    }
    assertEquals(200, compared, "80 rolling and 120 flying-rock DCC selections");
  }
}
