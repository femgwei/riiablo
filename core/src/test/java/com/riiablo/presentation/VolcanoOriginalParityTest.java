package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import java.util.*;
import org.junit.jupiter.api.Test;

class VolcanoOriginalParityTest extends RiiabloTest {
  private JsonValue fixture() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/volcano-flight-1.10f.json"));
  }
  private Map<Integer, NativeVolcanoPresentation.Flight> flights(JsonValue cast) {
    D2Seed seed = new D2Seed(cast.getInt("controller_target_seed"), 666);
    Map<Integer, NativeVolcanoPresentation.Flight> result = new LinkedHashMap<>();
    for (int age = 0; age < 150; age++) if (NativeVolcanoPresentation.emits(age, 2, 2, 128))
      result.put(age, NativeVolcanoPresentation.flight(Riiablo.files.Missiles.get("volcano debris 2"),
          1, cast.getInt("origin_x"), cast.getInt("origin_y"), 12, seed));
    assertEquals(62, result.size()); assertTrue(result.containsKey(4)); assertTrue(result.containsKey(126));
    return result;
  }

  @Test void bothNativeCastsReplayAllObservedFlightLifetimesVelocitiesAndPositions() {
    int checked = 0;
    for (JsonValue cast : fixture().get("cases")) {
      Map<Integer, NativeVolcanoPresentation.Flight> flights = flights(cast);
      for (JsonValue rock : cast.get("rocks")) {
        // Independently sampled global frame and unit countdown drift by one
        // tick. The nearest preceding even gate must still match the exact RNG,
        // lifetime, velocity and every recorded coordinate.
        NativeVolcanoPresentation.Flight flight = flights.get(rock.getInt("emission_age_estimate") & ~1);
        assertNotNull(flight); assertEquals(rock.getInt("lifetime"), flight.lifetime);
        assertEquals(rock.getInt("velocity_x"), flight.velocityX);
        assertEquals(rock.getInt("velocity_y"), flight.velocityY);
        for (JsonValue state : rock.get("states")) {
          int age = state.getInt("age");
          assertEquals(state.getInt("x_fixed"), (cast.getInt("origin_x") << 16) + 32768 + age * flight.velocityX);
          assertEquals(state.getInt("y_fixed"), (cast.getInt("origin_y") << 16) + 32768 + age * flight.velocityY);
          checked++;
        }
      }
    }
    assertEquals(1841, checked);
  }

  @Test void allCapturedGraphicsArcStatesIncludeTheImmediateCreationUpdate() {
    int checked = 0;
    for (JsonValue rock : fixture().get("cases").get("arc_hits").get("rocks")) {
      int life = rock.getInt("lifetime");
      for (JsonValue state : rock.get("states")) {
        int updates = state.getInt("age") + 1;
        int z = NativeVolcanoPresentation.heightFixed(life, 30, updates);
        assertEquals(state.getInt("z_fixed"), z);
        assertEquals(state.getInt("vz_fixed"), NativeVolcanoPresentation.heightFixed(life, 30, updates + 1) - z);
        checked++;
      }
    }
    assertEquals(931, checked);
  }

  @Test void everyObservedRockAndLandingDccSelectionMatchesRealAssetFrameCounts() {
    Map<String, Integer> frames = new HashMap<>();
    for (String name : new String[] {"volcano debris 2", "volcano explosion", "volcano small fire"}) {
      DCC dcc = DCC.loadFromFile(Riiablo.mpqs.resolve("data/global/missiles/" + Riiablo.files.Missiles.get(name).CelFile + ".dcc"));
      frames.put(name, dcc.getNumFramesPerDir()); dcc.dispose();
    }
    int checked = 0;
    for (JsonValue s : fixture().get("cases").get("draw").get("selections")) {
      String name = s.getString("missile");
      int actual = "volcano debris 2".equals(name)
          ? NativeVolcanoPresentation.rockFrame(frames.get(name), Riiablo.files.Missiles.get(name).AnimLen, s.getInt("lifetime"), s.getInt("age"))
          : NativeDruidSurfacePresentation.frame(Riiablo.files.Missiles.get(name), frames.get(name), s.getInt("lifetime"), s.getInt("age"));
      assertEquals(s.getInt("frame"), actual, name + " age=" + s.getInt("age")); checked++;
    }
    assertEquals(2591, checked);
  }

  @Test void hitTraceProvesTwoIndependentSuccessfulCreationsEvenWhenSnapshotsMissAChild() {
    JsonValue cast = fixture().get("cases").get("arc_hits");
    Map<Integer, Set<Integer>> slots = new HashMap<>(); int parents = 0;
    for (JsonValue event : cast.get("hits")) {
      if ("owner_return".equals(event.getString("kind"))) {
        assertNotEquals(0, event.getInt("owner")); assertEquals(0, event.getInt("current_frame")); parents++;
      } else if ("create_return".equals(event.getString("kind"))) {
        assertNotEquals(0, event.getInt("result"));
        assertEquals(482 + event.getInt("slot"), event.getInt("child_class"));
        slots.computeIfAbsent(event.getInt("unit_id"), id -> new HashSet<>()).add(event.getInt("slot"));
      }
    }
    assertEquals(58, parents); assertEquals(58, slots.size());
    for (Set<Integer> pair : slots.values()) assertEquals(new HashSet<>(Arrays.asList(0, 1)), pair);
    assertEquals(2, cast.get("created_but_unsampled").size);
    assertEquals(4, cast.get("observed_rocks_without_hit").size);
  }

  @Test void realProductionEmitsAllSixtyTwoFlightsAndMovesEvenWithColdResources() {
    JsonValue cast = fixture().get("cases").get("draw");
    JsonValue input = new JsonReader().parse(getClass().getResourceAsStream("/original/druid-surface-animation-1.10f.json"))
        .get("cases").get("volcano");
    Map<Integer, NativeVolcanoPresentation.Flight> expected = flights(cast);
    for (int loadTick : new int[] {0, 1000}) {
      Map<String, Object> output = DruidSurfaceOriginalAnalysis.simulate(input, loadTick, cast.getInt("controller_target_seed"));
      List<Map<?, ?>> rocks = new ArrayList<>();
      for (Object value : (List<?>) output.get("births")) {
        Map<?, ?> b = (Map<?, ?>) value;
        if (Boolean.TRUE.equals(b.get("presentation_only")) && "volcano debris 2".equals(b.get("missile"))) rocks.add(b);
      }
      assertEquals(62, rocks.size()); int i = 0, originTick = ((Number) rocks.get(0).get("tick")).intValue();
      for (NativeVolcanoPresentation.Flight flight : expected.values()) {
        Map<?, ?> rock = rocks.get(i);
        assertEquals(originTick + 2 * i++, rock.get("tick")); assertEquals(flight.lifetime, rock.get("lifetime"));
        assertTrue(((Number) rock.get("removed_tick")).intValue() > 0);
        for (Object value : (List<?>) rock.get("states")) {
          Map<?, ?> state = (Map<?, ?>) value; int age = ((Number) state.get("age")).intValue();
          assertEquals((cast.getInt("origin_x") << 16) + 32768 + age * flight.velocityX, state.get("x_fixed"));
          assertEquals((cast.getInt("origin_y") << 16) + 32768 + age * flight.velocityY, state.get("y_fixed"));
          List<?> offset = (List<?>) state.get("offset");
          assertEquals((float) (NativeVolcanoPresentation.heightFixed(flight.lifetime, 30, age + 1) >> 11), offset.get(1));
          if (loadTick > 0) assertEquals(false, state.get("loaded"));
        }
      }
    }
  }
}
