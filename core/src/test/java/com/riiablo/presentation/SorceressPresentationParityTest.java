package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.net.packet.d2gs.StateP;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class SorceressPresentationParityTest extends RiiabloTest {
  private JsonValue original() throws Exception {
    try (java.io.InputStream in = getClass().getResourceAsStream("/original/inferno-1.10f.json")) {
      assertNotNull(in);
      return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test void infernoProductionMatchesEveryCapturedDccFrameAndNativeDisplacement() throws Exception {
    JsonValue fixture = original();
    Map<String, Object> replay = InfernoOriginalAnalysis.simulate(fixture);
    assertEquals(Arrays.asList(14.0625f, 0f), replay.get("velocity"));
    assertEquals(true, replay.get("native_precision"));
    Map<Integer, Map<?, ?>> states = new HashMap<>();
    for (Object value : (List<?>) replay.get("states")) {
      Map<?, ?> state = (Map<?, ?>) value;
      int age = ((Number) state.get("native_age")).intValue(); states.put(age, state);
      List<?> position = (List<?>) state.get("position");
      assertEquals(fixture.get("identity").getInt("x") + age * .5625,
          ((Number) position.get(0)).doubleValue(), .00001);
    }
    int checked = 0;
    for (JsonValue sample : fixture.get("samples")) {
      int age = sample.getInt("native_age");
      assertEquals(sample.getInt("frame"), ((Number) states.get(age).get("frame")).intValue()); checked++;
    }
    assertEquals(116, checked);
  }

  @Test void enchantProductionReplaysEachActivationWithNativeRateAndBlending() {
    Map<String, Object> result = EnchantOriginalAnalysis.simulate();
    assertEquals(3600, result.get("duration"));
    assertEquals(8, result.get("fire_min")); assertEquals(10, result.get("fire_max"));
    assertEquals(20, result.get("attack_rating_percent"));
    List<?> requests = (List<?>) result.get("activation_requests");
    assertEquals(2, requests.size());
    assertEquals(45, ((Map<?, ?>) requests.get(1)).get("tick"));
    int checked = 0;
    for (Object value : (List<?>) result.get("states")) {
      Map<?, ?> state = (Map<?, ?>) value;
      if (!Boolean.TRUE.equals(state.get("loaded"))) continue;
      assertEquals(true, state.get("native_rendering"));
      assertEquals(14, state.get("blend_mode"));
      assertEquals(.04f, ((Number) state.get("frame_duration")).floatValue(), .000001);
      checked++;
    }
    assertTrue(checked >= 25);
    assertEquals((byte) 3, result.get("restored_right_hand_transform"));
    assertEquals((byte) 7, result.get("restored_left_hand_transform"));
  }

  @Test void clientInfernoOwnsItsVisualsAndKeepsTwentyFiveHzAtSixtyHzUpdates() throws Exception {
    Map<String, Object> replay = InfernoOriginalAnalysis.simulate(original(), true, 1f / 60f, 18);
    assertEquals(2, replay.get("visual_count"));
    assertEquals(1, replay.get("server_count"));
    assertEquals(true, replay.get("damage_carrier_hidden"));
    assertEquals(false, replay.get("visual_networked"));
    List<?> states = (List<?>) replay.get("states");
    Map<?, ?> last = (Map<?, ?>) states.get(states.size() - 1);
    assertEquals(7, last.get("native_age"));
    assertEquals(10, last.get("frame"));
  }

  @Test void enchantActivationTokenSurvivesStatePacketsAndAnExpiryBetweenCasts() {
    UnitStates component = new UnitStates(); component.init(3);
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(52);
    StateSerializer serializer = new StateSerializer();
    for (int expected = 1; expected <= 3; expected++) {
      SorceressSkills.applyEnchantState(component.stateList, skill, 1, 3, name -> 0, 0);
      FlatBufferBuilder buffer = new FlatBufferBuilder(256);
      buffer.finish(serializer.putData(buffer, component));
      assertEquals(expected, StateP.getRootAsStateP(buffer.dataBuffer()).presentationRevision(0));
      if (expected == 2) component.stateList.removeState(StateId.ENCHANT);
    }
  }

  @Test void coldInfernoKeepsMovingAndExpiresWithoutWaitingForDcc() throws Exception {
    JsonValue fixture = original();
    Map<String, Object> replay = InfernoOriginalAnalysis.simulate(fixture, true, 1f / 60f, 30, 15);
    int cold = 0, loaded = 0;
    for (Object value : (List<?>) replay.get("states")) {
      Map<?, ?> state = (Map<?, ?>) value;
      int age = ((Number) state.get("native_age")).intValue();
      List<?> position = (List<?>) state.get("position");
      assertEquals(fixture.get("identity").getInt("x") + age * .5625,
          ((Number) position.get(0)).doubleValue(), .00001);
      if (Boolean.TRUE.equals(state.get("loaded"))) {
        assertEquals(age * 384 >> 8, ((Number) state.get("frame")).intValue()); loaded++;
      } else cold++;
    }
    assertTrue(cold >= 15); assertTrue(loaded > 0);
    assertEquals(false, replay.get("alive_after_updates"));
    Map<String, Object> neverLoaded = InfernoOriginalAnalysis.simulate(fixture, true, 1f / 60f, 30, 100);
    assertEquals(false, neverLoaded.get("alive_after_updates"));
    assertTrue(((List<?>) neverLoaded.get("states")).stream().allMatch(
        value -> !Boolean.TRUE.equals(((Map<?, ?>) value).get("loaded"))));
  }
}
