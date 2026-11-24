package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Integrity of the retained original observation, not production parity. */
class OriginalInfernoFixtureTest {
  private JsonValue original() throws Exception {
    try (java.io.InputStream in = getClass().getResourceAsStream("/original/inferno-1.10f.json")) {
      assertNotNull(in);
      return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test void capturedClientStreamsRetainTheirOwnBirthsMotionAndAnimationClock() throws Exception {
    JsonValue fixture = original();
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    JsonValue cast = fixture.get("identity");
    assertEquals(41, cast.getInt("skill")); assertEquals(1, cast.getInt("level"));
    assertEquals(40, cast.getInt("hold_ticks"));
    int server = 0, client = 0, states = 0;
    Set<String> variants = new HashSet<>();
    Set<Integer> clientBirths = new TreeSet<>();
    for (JsonValue unit : fixture.get("units")) {
      boolean visual = "client".equals(unit.getString("side"));
      if (visual) { client++; variants.add(unit.getString("missile")); }
      else { server++; assertEquals("infernoflame1", unit.getString("missile")); }
      JsonValue first = unit.get("states").child;
      int birth = first.getInt("game_frame") - (10 - first.getInt("current_frame"));
      if (visual) clientBirths.add(birth);
      for (JsonValue state : unit.get("states")) {
        assertEquals(10, state.getInt("total_frames"));
        int age = 10 - state.getInt("current_frame");
        assertEquals(36864, state.getInt("velocity_x"));
        assertEquals(0, state.getInt("velocity_y"));
        assertEquals(((long) cast.getInt("x") << 16) + 32768 + age * 36864L,
            state.getLong("x_fixed"));
        assertEquals(((long) cast.getInt("y") << 16) + 32768, state.getLong("y_fixed"));
        if (visual) {
          assertEquals(15 << 8, state.getInt("unit_48"));
          assertEquals(384, state.getInt("unit_4c"));
          assertEquals(age * 384, state.getInt("unit_44"));
          states++;
        }
      }
    }
    assertEquals(14, server); assertEquals(18, client); assertEquals(162, states);
    assertEquals(Set.of("infernoflame1", "infernoflame2"), variants);
    assertEquals(18, clientBirths.size());
    Integer previous = null;
    for (int birth : clientBirths) {
      if (previous != null) assertEquals(2, birth - previous);
      previous = birth;
    }
  }

  @Test void actualDccSelectionsMatchBothFlameResourcesAndFixedPointCursors() throws Exception {
    JsonValue fixture = original();
    int count = 0;
    Set<String> cells = new HashSet<>();
    for (JsonValue sample : fixture.get("samples")) {
      assertEquals(1, sample.getInt("dcc_directions"));
      assertEquals(0, sample.getInt("dcc_direction"));
      assertEquals(15, sample.getInt("dcc_frames"));
      assertEquals(sample.getInt("native_age") * 384 >>> 8, sample.getInt("frame"));
      String cell = sample.getString("cell_name");
      assertEquals("infernoflame1".equals(sample.getString("missile"))
          ? "Flamethrower" : "Flamethrower2", cell);
      cells.add(cell); count++;
    }
    assertEquals(116, count);
    assertEquals(Set.of("Flamethrower", "Flamethrower2"), cells);
  }
}
