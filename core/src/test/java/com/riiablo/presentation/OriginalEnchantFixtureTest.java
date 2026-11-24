package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Checks retained original evidence, not production parity. */
class OriginalEnchantFixtureTest {
  private JsonValue original() throws Exception {
    try (java.io.InputStream in = getClass().getResourceAsStream("/original/enchant-1.10f.json")) {
      assertNotNull(in);
      return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test void refreshRetainsOneFireBonusAndRenewsTheOriginalStateDeadline() throws Exception {
    JsonValue fixture = original();
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    assertEquals(52, fixture.get("identity").getInt("skill"));
    List<Integer> births = new ArrayList<>();
    for (JsonValue event : fixture.get("events")) if ("create".equals(event.getString("kind"))) {
      assertEquals(153, event.getInt("overlay_id")); births.add(event.getInt("game_frame"));
    }
    assertEquals(2, births.size()); assertEquals(45, births.get(1) - births.get(0));
    Set<Integer> deadlines = new HashSet<>(); int stateSamples = 0;
    for (JsonValue state : fixture.get("states")) {
      if (!state.getBoolean("enchant_state")) {
        assertEquals(0, state.getInt("fire_min")); assertEquals(0, state.getInt("fire_max")); continue;
      }
      assertEquals(8, state.getInt("fire_min")); assertEquals(10, state.getInt("fire_max"));
      assertEquals(20, state.getInt("attack_rating_percent"));
      if ("server".equals(state.getString("side"))) deadlines.add(state.getInt("expiry_frame"));
      stateSamples++;
    }
    assertEquals(new HashSet<>(Arrays.asList(births.get(0) + 3600, births.get(1) + 3600)), deadlines);
    assertEquals(186, stateSamples);
  }

  @Test void bothActivationDrawSequencesUseNativeClockAndDrawModeThree() throws Exception {
    JsonValue fixture = original(); int draws = 0, returned = 0, overlayStates = 0;
    List<Integer> frames = new ArrayList<>();
    for (JsonValue event : fixture.get("events")) {
      if ("draw".equals(event.getString("kind"))) {
        assertEquals("FireEnchant", event.getString("cell_name"));
        assertEquals(3, event.getInt("draw_mode"));
        assertEquals(event.getInt("frame"), event.get("cell_raw").getInt(2));
        assertEquals(153, event.get("cell_raw").getInt(11));
        frames.add(event.getInt("frame")); draws++;
      } else if ("draw_returned".equals(event.getString("kind"))) returned++;
    }
    assertEquals(32, draws); assertEquals(draws, returned);
    for (int i = 0; i < 32; i++) assertEquals(i % 16 + 1, frames.get(i));
    for (JsonValue overlay : fixture.get("overlays")) {
      assertEquals(153, overlay.getInt("overlay_id"));
      assertEquals(4, overlay.getInt("overlay_type"));
      assertEquals(256, overlay.getInt("frame_delta"));
      assertEquals(17 << 8, overlay.getInt("total_cursor"));
      assertEquals(0, overlay.getInt("cursor") & 255);
      overlayStates++;
    }
    assertEquals(33, overlayStates);
  }
}
