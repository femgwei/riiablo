package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Validates the retained native capture, independently of production's expectations. */
class OriginalMoltenBoulderFixtureTest {
  @Test void backgroundCastRetainsCompleteRollingFlightAndSeparateClientEffects() throws Exception {
    try (java.io.InputStream stream = getClass().getResourceAsStream("/original/molten-boulder-1.10f.json")) {
      assertNotNull(stream);
      JsonValue fixture = new JsonReader().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      assertTrue(fixture.getBoolean("background_verified"));
      assertTrue(fixture.getBoolean("owned_game_closed"));
      assertEquals(229, fixture.get("identity").getInt("skill"));
      assertEquals(1, fixture.get("identity").getInt("level"));
      assertEquals(1714, fixture.getInt("paired_draws"));
      Map<String, Integer> client = new HashMap<>();
      Set<String> flameVariants = new TreeSet<>();
      int serverTrail = 0, serverBurst = 0;
      for (JsonValue unit : fixture.get("units")) {
        String name = unit.getString("missile"), side = unit.getString("side");
        JsonValue states = unit.get("states"), first = states.child;
        if (side.equals("client")) {
          client.merge(name, 1, Integer::sum);
          if (Set.of("firewall", "firesmall", "firemedium").contains(name)) {
            flameVariants.add(name);
            assertEquals(90, first.getInt("total_frames"));
          }
        } else if (name.equals("moltenboulder")) {
          assertEquals(100, states.size);
          assertEquals(100, first.getInt("current_frame"));
          assertEquals(1, states.get(states.size - 1).getInt("current_frame"));
          assertEquals(12288, first.getInt("velocity_x"));
          for (int i = 1; i < states.size; i++) {
            assertEquals(12288, states.get(i).getInt("x_fixed") - states.get(i - 1).getInt("x_fixed"));
          }
        } else if (name.equals("moltenboulderfirepath")) {
          if (first.getInt("total_frames") == 7) serverBurst++;
          else { assertEquals(37, first.getInt("total_frames")); serverTrail++; }
        }
      }
      assertEquals(Set.of("firewall", "firesmall", "firemedium"), flameVariants);
      assertEquals(19, serverTrail);
      assertEquals(18, serverBurst);
      assertEquals(18, client.get("moltenboulderfirepath"));
      // This capture produced nine valid fragments; it is not a constant spawn count.
      assertEquals(9, client.get("moltenboulder-flyingrocks"));
      for (JsonValue sample : fixture.get("samples")) {
        assertEquals(sample.getInt("animation_cursor") >>> 8, sample.getInt("frame"));
        if (sample.getString("missile").equals("moltenboulder")) {
          assertEquals(sample.getInt("native_age") % 10, sample.getInt("frame"));
        }
      }
    }
  }
}
