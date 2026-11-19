package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Native capture contract; passing does not assert Riiablo presentation parity. */
class BlazeOriginalAnimationTest extends RiiabloTest {
  private JsonValue fixture() {
    return new JsonReader().parse(getClass().getResourceAsStream(
        "/original/blaze-client-animation-1.10f.json"));
  }

  @Test void capturedResourcesExistInRealMpqs() {
    Map<String, DCC> assets = new HashMap<>();
    int samples = 0;
    int selections = 0;
    try {
      JsonValue fixture = fixture();
      for (JsonValue trail : fixture.get("client_trails")) {
        String name = trail.getString("cell_name");
        assertEquals(name, Riiablo.files.Missiles.get(trail.getInt("class_id")).CelFile);
        DCC asset = assets.get(name);
        if (asset == null) {
          asset = DCC.loadFromFile(Riiablo.mpqs.resolve(
              "data\\global\\missiles\\" + name + ".dcc"));
          assets.put(name, asset);
        }
        assertEquals(1, asset.getNumDirections());
        assertEquals(37, asset.getNumFramesPerDir());
        for (int frame : trail.get("frames").asIntArray()) {
          assertNotNull(asset.getFrame(0, frame));
          samples++;
        }
        selections += trail.getInt("selection_count");
      }
      assertEquals(3, assets.size());
      assertEquals(1510, samples);
      assertEquals(fixture.getInt("selections_observed"), selections);
    } finally {
      for (DCC asset : assets.values()) asset.dispose();
    }
  }

  @Test void everyNativeTrailGrowsLoopsThenShrinks() {
    int[] shrink = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 1};
    int count = 0;
    for (JsonValue trail : fixture().get("client_trails")) {
      int[] frames = trail.get("frames").asIntArray();
      int phase = trail.getInt("observed_phase");
      assertEquals(trail.getInt("class_id") == 69 ? 90 : 115, frames.length);
      for (int age = 0; age < frames.length; age++) {
        int expected = age < 12 ? age : age >= frames.length - shrink.length
            ? shrink[age - (frames.length - shrink.length)]
            : 12 + Math.floorMod(age - 12 + phase, 24);
        assertEquals(expected, frames[age], "native unit " + trail.getInt("unit_id") + " age " + age);
      }
      count++;
    }
    assertEquals(14, count);
  }

  @Test void movingServerSegmentsHaveMatchingClientBirthPositions() {
    JsonValue fixture = fixture();
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    assertEquals(13 * 65536, fixture.getInt("player_movement_fixed_x"));
    int count = 0;
    for (JsonValue segment : fixture.get("server_segments")) {
      JsonValue matched = null;
      for (JsonValue trail : fixture.get("client_trails")) {
        if (trail.getInt("unit_id") == segment.getInt("client_unit_id")) matched = trail;
      }
      assertNotNull(matched);
      assertEquals(segment.getInt("first_frame"), matched.getInt("first_frame"));
      assertEquals(segment.getInt("x_fixed"), matched.getInt("x_fixed"));
      assertEquals(segment.getInt("y_fixed"), matched.getInt("y_fixed"));
      assertEquals(115, segment.getInt("lifetime_frames"));
      count++;
    }
    assertEquals(13, count);
  }
}
