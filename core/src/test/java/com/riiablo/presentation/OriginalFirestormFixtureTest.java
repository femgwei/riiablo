package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Integrity of observed original behavior; this does not assert port parity. */
class OriginalFirestormFixtureTest {
  private JsonValue fixture() {
    JsonValue j = new JsonReader().parse(getClass().getResourceAsStream("/original/firestorm-1.10f.json"));
    assertTrue(j.getBoolean("background_verified"));
    assertTrue(j.getBoolean("owned_game_closed"));
    assertEquals(225, j.get("identity").getInt("skill"));
    assertEquals(1, j.get("identity").getInt("level"));
    return j;
  }

  @Test void originalHasOneStraightAndTwoCurvedStreamsPlusSeparateDamageFires() {
    int heads = 0, fires = 0, straight = 0, states = 0;
    for (JsonValue unit : fixture().get("units")) {
      if (!"server".equals(unit.getString("side"))) continue;
      states += unit.get("states").size;
      if ("firestormmaker".equals(unit.getString("missile"))) {
        heads++;
        assertEquals(40, unit.get("states").size);
        Set<String> velocities = new HashSet<>();
        for (JsonValue state : unit.get("states")) {
          assertEquals(40, state.getInt("total_frames"));
          velocities.add(state.getInt("velocity_x") + ":" + state.getInt("velocity_y"));
        }
        if (velocities.size() == 1) straight++;
      } else {
        assertEquals("firestorm", unit.getString("missile"));
        fires++;
        assertEquals(15, unit.get("states").size);
        for (JsonValue state : unit.get("states")) assertEquals(15, state.getInt("total_frames"));
      }
    }
    assertEquals(3, heads);
    assertEquals(1, straight);
    assertEquals(36, fires);
    assertEquals(660, states);
  }

  @Test void clientGroundFiresGrowThenReverseTheirAnimationBeforeExpiry() {
    int count = 0;
    int[] frames = {0, 1, 2, 3, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1};
    for (JsonValue unit : fixture().get("units")) {
      if (!"client".equals(unit.getString("side")) || !"firestorm".equals(unit.getString("missile"))) continue;
      count++;
      for (JsonValue state : unit.get("states")) {
        assertEquals(15, state.getInt("total_frames"));
        int age = 15 - state.getInt("current_frame");
        assertEquals(frames[age], state.getInt("unit_44") >> 8);
        assertEquals(0, state.getInt("velocity_x"));
        assertEquals(0, state.getInt("velocity_y"));
      }
    }
    assertEquals(17, count);
  }

  @Test void actualSpriteSelectionsRetainMissingNameEvidenceExplicitly() {
    JsonValue j = fixture();
    int makers = 0, fires = 0;
    assertEquals(206, j.getInt("paired_draws"));
    assertEquals(204, j.get("samples").size);
    for (JsonValue s : j.get("samples")) {
      assertEquals(s.getInt("animation_cursor") >> 8, s.getInt("frame"));
      assertEquals(0, s.getInt("dcc_direction"));
      assertEquals(1, s.getInt("dcc_directions"));
      if ("firestormmaker".equals(s.getString("missile"))) {
        makers++;
        assertEquals(16, s.getInt("dcc_frames"));
        assertEquals((s.getInt("native_age") * 2) % 16, s.getInt("frame"));
        assertFalse(s.getBoolean("cell_name_observed"));
      } else {
        fires++;
        assertEquals(37, s.getInt("dcc_frames"));
        assertEquals("groundFiresmall", s.getString("cell_name"));
      }
    }
    assertEquals(68, makers);
    assertEquals(136, fires);
  }
}
