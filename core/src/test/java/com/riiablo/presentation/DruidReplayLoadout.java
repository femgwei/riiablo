package com.riiablo.presentation;

import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.save.CharData;
import java.util.LinkedHashMap;
import java.util.Map;

/** Applies recorded hard points, rather than treating every fixture as zero-synergy. */
final class DruidReplayLoadout {
  private DruidReplayLoadout() {}

  static Map<String, Integer> apply(CharData data, JsonValue original, int defaultSkill) {
    Map<String, Integer> points = new LinkedHashMap<>();
    JsonValue fixture = original.get("fixture");
    if (fixture == null) points.put(Integer.toString(defaultSkill), 1);
    else {
      if (fixture.getInt("class_id") != 5 || !"empty".equals(fixture.getString("equipment"))
          || fixture.getInt("other_hard_points") != 0)
        throw new IllegalArgumentException("Unsupported Druid replay loadout");
      for (JsonValue point : fixture.get("hard_points")) {
        int skill = Integer.parseInt(point.name), level = point.asInt();
        if (skill < 221 || skill > 250 || level < 1 || level > 20)
          throw new IllegalArgumentException("Invalid recorded Druid hard points");
        points.put(point.name, level);
      }
      if (!points.containsKey(Integer.toString(defaultSkill)))
        throw new IllegalArgumentException("Selected skill missing from recorded loadout");
    }
    for (Map.Entry<String, Integer> point : points.entrySet())
      data.setSkillLevel(Integer.parseInt(point.getKey()), point.getValue());
    return points;
  }
}
