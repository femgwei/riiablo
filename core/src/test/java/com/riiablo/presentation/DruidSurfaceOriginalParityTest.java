package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import java.util.*;
import org.junit.jupiter.api.Test;

class DruidSurfaceOriginalParityTest extends RiiabloTest {
  private JsonValue original() {
    return new JsonReader().parse(getClass().getResourceAsStream("/original/druid-surface-animation-1.10f.json"));
  }

  @Test void everyCapturedSurfaceDccFrameMatchesTheProductionClockIncludingOutro() {
    int compared = 0;
    for (JsonValue cast : original().get("cases")) for (JsonValue s : cast.get("samples")) {
      assertTrue(cast.getBoolean("background_verified"));
      assertEquals(s.getInt("frame"), NativeDruidSurfacePresentation.frame(
          Riiablo.files.Missiles.get(s.getString("missile")), s.getInt("frames"), s.getInt("lifetime"), s.getInt("age")),
          s.getString("missile") + " age=" + s.getInt("age"));
      compared++;
    }
    assertTrue(compared > 2500);
  }

  @Test void actualFissureHasFourteenPairedLongLivedVents() {
    JsonValue cast = original().get("cases").get("fissure");
    Map<String, Object> production = DruidSurfaceOriginalAnalysis.simulate(cast);
    Map<?, ?> counts = (Map<?, ?>) production.get("visual_counts");
    assertEquals(14, counts.get("erruption crack 1")); assertEquals(14, counts.get("erruption smoke 1"));
    List<Map<?, ?>> cracks = births(production, "erruption crack 1");
    List<Map<?, ?>> smoke = births(production, "erruption smoke 1");
    for (int i = 0; i < cracks.size(); i++) {
      Map<?, ?> crack = cracks.get(i);
      assertEquals(crack.get("tick"), smoke.get(i).get("tick"));
      assertEquals(crack.get("position"), smoke.get(i).get("position"));
      assertEquals(84, crack.get("lifetime")); assertEquals(84, smoke.get(i).get("lifetime"));
      if (i > 0) assertEquals(6, ((Number) crack.get("tick")).intValue() - ((Number) cracks.get(i - 1).get("tick")).intValue());
    }
    assertCapturedPoses(cast, production);
  }

  @Test void actualVolcanoRetainsBothResidentLayersThroughTheNativeOutro() {
    JsonValue cast = original().get("cases").get("volcano");
    Map<String, Object> production = DruidSurfaceOriginalAnalysis.simulate(cast);
    for (String name : new String[] {"volcano", "volcano overlay fire"}) {
      List<Map<?, ?>> rows = births(production, name); assertEquals(1, rows.size());
      Map<?, ?> layer = rows.get(0); assertEquals(150, layer.get("lifetime"));
      assertTrue(((Number) layer.get("removed_tick")).intValue() >= 149);
      List<?> states = (List<?>) layer.get("states"); Map<?, ?> last = (Map<?, ?>) states.get(states.size() - 1);
      assertEquals(29, last.get("frame"));
    }
    assertCapturedPoses(cast, production);
  }

  @Test void coldResourcesCannotExtendTheSurfaceEffects() {
    Map<String, Object> production = DruidSurfaceOriginalAnalysis.simulate(original().get("cases").get("fissure"), 1000);
    for (String name : new String[] {"erruption crack 1", "erruption smoke 1"}) {
      assertEquals(14, births(production, name).size());
      for (Map<?, ?> birth : births(production, name)) {
      assertEquals(84, birth.get("lifetime"));
      assertTrue(((Number) birth.get("removed_tick")).intValue() > 0);
      assertTrue(((Number) birth.get("removed_tick")).intValue() < 170);
      for (Object value : (List<?>) birth.get("states")) assertEquals(false, ((Map<?, ?>) value).get("loaded"));
      }
    }
  }

  private static List<Map<?, ?>> births(Map<String, Object> production, String name) {
    List<Map<?, ?>> rows = new ArrayList<>();
    for (Object value : (List<?>) production.get("births")) {
      Map<?, ?> row = (Map<?, ?>) value;
      if (Boolean.TRUE.equals(row.get("presentation_only")) && name.equals(row.get("missile"))) rows.add(row);
    }
    return rows;
  }
  private static void assertCapturedPoses(JsonValue cast, Map<String, Object> production) {
    Map<String, Map<Integer, Integer>> frames = new HashMap<>();
    for (JsonValue s : cast.get("samples")) frames.computeIfAbsent(s.getString("missile"), key -> new HashMap<>()).put(s.getInt("age"), s.getInt("frame"));
    int checked = 0;
    for (String name : frames.keySet()) for (Map<?, ?> birth : births(production, name)) for (Object value : (List<?>) birth.get("states")) {
      Map<?, ?> state = (Map<?, ?>) value; Integer expected = frames.get(name).get(((Number) state.get("age")).intValue());
      if (expected != null && Boolean.TRUE.equals(state.get("loaded"))) {assertEquals(expected, state.get("frame"), name); checked++;}
    }
    assertTrue(checked > 200);
  }
}
