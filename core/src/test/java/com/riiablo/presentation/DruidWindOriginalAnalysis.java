package com.riiablo.presentation;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.RiiabloTest;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real SkillDo -> missile simulation -> loader/animation; no GPU or replayed scene. */
public final class DruidWindOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      Path directory = Path.of(args[0]);
      boolean staticBaseline = args.length > 1 && "--static-baseline".equals(args[1]);
      for (String name : new String[] {"twister", "tornado"}) {
        JsonValue original = staticBaseline ? new JsonReader().parse("{\"skill_id\":"
            + ("twister".equals(name) ? 240 : 245)
            + ",\"identity\":{\"x\":100,\"y\":100,\"target_x\":108,\"target_y\":100},"
            + "\"fixture\":{\"class_id\":5,\"equipment\":\"empty\",\"other_hard_points\":0,"
            + "\"hard_points\":{\"240\":1,\"245\":1}}}")
            : new JsonReader().parse(Files.readString(directory.resolve("druid-" + name + "-replay-input-1.10f.json")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source_kind", staticBaseline ? "synthetic_production_baseline_no_original_capture" : "original_runtime_capture");
        result.put("source_run_id", original.getString("run_id", null));
        result.put("skill_id", original.getInt("skill_id"));
        result.put("fixture_sha256", original.get("fixture").getString("sha256", null));
        result.put("scope", "Matched skill points and cast target; real production systems; empty map/no targets, no GPU or full caster sequence");
        result.put("production", DruidSurfaceOriginalAnalysis.simulate(original));
        StringWriter text = new StringWriter(); JsonWriter writer = new JsonWriter(text);
        writer.setOutputType(JsonWriter.OutputType.json); DruidSurfaceOriginalAnalysis.write(writer, result); writer.flush();
        Files.writeString(directory.resolve("druid-" + name + (staticBaseline
            ? "-production-static-baseline.json" : "-production-1.10f.json")), text + "\n");
      }
    } finally {RiiabloTest.teardown();}
  }
}
