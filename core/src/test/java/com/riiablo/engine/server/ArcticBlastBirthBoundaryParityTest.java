package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.RiiabloTest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class ArcticBlastBirthBoundaryParityTest extends RiiabloTest {
  private JsonValue original(String resource)throws Exception {
    try(java.io.InputStream in=getClass().getResourceAsStream("/original/"+resource)) {
      assertNotNull(in);return new JsonReader().parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
    }
  }
  @Test void releasingOnAnObservedBirthRetainsAllThreeNativeTailMissiles()throws Exception {
    JsonValue fixture=original("arctic-blast-birth-boundary-1.10f.json");
    assertTrue(fixture.getBoolean("background_verified"));assertTrue(fixture.getBoolean("owned_game_closed"));
    for(JsonValue capture:fixture.get("captures")) {
      int count=capture.get("identity").getInt("release_after_births");
      Map<String,Object> replay=ArcticBlastBirthBoundaryOriginalAnalysis.simulate(count);
      assertEquals(count,capture.get("births_before_release").size);
      assertEquals(3,capture.get("births_after_release").size);
      List<?> births=(List<?>)replay.get("births");int release=((Number)replay.get("release_tick")).intValue();
      assertEquals(capture.get("units").size,births.size());
      List<Integer> tail=new ArrayList<>();
      for(Object value:births) {int tick=((Number)((Map<?,?>)value).get("tick")).intValue();if(tick>release)tail.add(tick-release);}
      assertEquals(List.of(2,4,6),tail);
      assertEquals(0,replay.get("final_streams"));assertEquals(1,replay.get("final_mode"));
    }
  }
  @Test void elapsedHoldCalibrationExcludesTheInitializedFrameZeroPose()throws Exception {
    JsonValue fixture=original("arctic-blast-sequence-1.10f.json");
    for(JsonValue capture:fixture.get("captures")) {
      int intervals=capture.get("identity").getInt("hold_ms_requested")/40;
      Map<String,Object> replay=ArcticBlastBirthBoundaryOriginalAnalysis.simulateElapsedIntervals(intervals);
      assertEquals(0,replay.get("time_zero_source_frame"));
      assertEquals(intervals+1,replay.get("release_tick"));
      assertEquals(capture.get("units").size,((List<?>)replay.get("births")).size());
      assertEquals(0,replay.get("final_streams"));
    }
  }
}
