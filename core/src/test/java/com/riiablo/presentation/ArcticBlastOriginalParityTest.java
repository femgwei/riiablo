package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Missile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Client polling parity only: does not establish native server damage or GPU parity. */
class ArcticBlastOriginalParityTest extends RiiabloTest {
  private JsonValue original() throws Exception {
    try (java.io.InputStream in = getClass().getResourceAsStream("/original/arctic-blast-1.10f.json")) {
      assertNotNull(in);
      return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test void retainedPollingCoversBothResourcesAndPostReleaseTailWithoutForeground() throws Exception {
    JsonValue fixture = original();
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    assertEquals(5, fixture.get("loadout").getInt("class_id"));
    assertEquals(230, fixture.get("loadout").getInt("skill_id"));
    int units = 0, states = 0;
    Set<Integer> classes = new HashSet<>();
    for (JsonValue capture : fixture.get("captures")) {
      int release = capture.getInt("release_ms_actual");
      boolean tail = false;
      for (JsonValue unit : capture.get("units")) {
        units++; classes.add(unit.getInt("class_id"));
        for (JsonValue sample : unit.get("states")) {
          assertEquals(17, sample.getInt("total_frames"));
          assertEquals(4096, sample.getInt("unit_48"));
          assertEquals(240, sample.getInt("unit_4c"));
          assertEquals((17 - sample.getInt("current_frame")) * 240, sample.getInt("unit_44"));
          if (sample.getInt("elapsed_ms") > release) tail = true;
          states++;
        }
      }
      assertTrue(tail);
    }
    assertEquals(Set.of(459, 460), classes);
    assertEquals(25, units); assertEquals(400, states);
  }

  @Test void clientStreamMatchesAllFourHundredObservedPositionsAndAnimationCursors() throws Exception {
    JsonValue fixture = original();
    Map<String, Object> replay = ArcticBlastOriginalAnalysis.simulate(fixture, true, 1f / 25f, 20);
    assertEquals(List.of(14.0625f, 0f), replay.get("velocity"));
    assertEquals(true, replay.get("native_precision"));
    assertEquals(17, replay.get("lifetime"));
    assertEquals(false, replay.get("alive_after_updates"));
    assertEquals(true, replay.get("damage_carrier_hidden"));
    assertEquals(false, replay.get("visual_networked"));
    assertEquals(2, replay.get("visual_count")); assertEquals(1, replay.get("server_count"));
    Map<Integer, Map<?, ?>> byAge = new HashMap<>();
    for (Object value : (List<?>) replay.get("states")) {
      Map<?, ?> state = (Map<?, ?>) value;
      byAge.put(((Number) state.get("native_age")).intValue(), state);
    }
    int compared = 0;
    for (JsonValue capture : fixture.get("captures")) {
      for (JsonValue unit : capture.get("units")) {
        for (JsonValue sample : unit.get("states")) {
          int age = sample.getInt("total_frames") - sample.getInt("current_frame");
          Map<?, ?> state = byAge.get(age);
          assertNotNull(state);
          List<?> position = (List<?>) state.get("position");
          assertEquals(sample.getLong("x_fixed"), Math.round((((Number) position.get(0)).doubleValue() + .5) * 65536));
          assertEquals(sample.getLong("y_fixed"), Math.round((((Number) position.get(1)).doubleValue() + .5) * 65536));
          assertEquals(sample.getInt("unit_44") >>> 8, ((Number) state.get("frame")).intValue());
          assertEquals(sample.getInt("unit_4c"), ((Number) state.get("frame_delta")).intValue());
          compared++;
        }
      }
    }
    assertEquals(400, compared);
  }

  @Test void coldAssetLoadRestoresExistingIceAgeAtSixtyHz() throws Exception {
    Map<String, Object> replay = ArcticBlastOriginalAnalysis.simulate(original(), true, 1f / 60f, 45, 12);
    boolean delayed = false, loaded = false;
    for (Object value : (List<?>) replay.get("states")) {
      Map<?, ?> state = (Map<?, ?>) value;
      int age = ((Number) state.get("native_age")).intValue();
      if (!Boolean.TRUE.equals(state.get("loaded"))) { delayed |= age > 0; continue; }
      loaded = true;
      assertEquals(age * 240 >>> 8, ((Number) state.get("frame")).intValue());
      assertEquals(240, state.get("frame_delta"));
    }
    assertTrue(delayed); assertTrue(loaded);
    assertEquals(false, replay.get("alive_after_updates"));
  }

  @Test void repeatedClientCallbacksSelectBothIceResourcesWithoutDuplicatingDamage() throws Exception {
    Map<String, Object> replay = ArcticBlastOriginalAnalysis.simulate(original(), true, 1f / 25f, 20, 0, 16);
    assertEquals(16, replay.get("visual_count"));
    assertEquals(15, replay.get("server_count")); // First server callback arms, while client CltDo24 already emits.
    assertEquals(Set.of("arcticblast1", "arcticblast2"), new HashSet<>((List<?>) replay.get("visual_rows")));
  }

  @Test void streamPolicyRejectsMismatchedSkillsAndKeepsServerOnlyFallback() throws Exception {
    Missile missile = new Missile();
    missile.missile = com.riiablo.Riiablo.files.Missiles.get("arcticblast1");
    missile.skillId = 41;
    assertFalse(NativeInfernoPresentation.handles(missile));
    missile.skillId = 230;
    assertTrue(NativeInfernoPresentation.handles(missile));
    Map<String, Object> replay = ArcticBlastOriginalAnalysis.simulate(original());
    assertEquals(0, replay.get("visual_count")); assertEquals(1, replay.get("server_count"));
    assertEquals(List.of(14.0625f, 0f), replay.get("velocity"));
  }
}
