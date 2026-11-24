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

/** One JVM/MPQ load, independent worlds per case; real skill and presentation systems. */
public final class DruidBatchOriginalAnalysis {
  public static void main(String[] args) throws Exception {
    RiiabloTest.setup();
    try {
      Path directory = Path.of(args[0]);
      for (String name : new String[] {"firestorm", "molten-boulder"}) {
        JsonValue original = new JsonReader().parse(Files.readString(
            directory.resolve("druid-" + name + "-replay-input-1.10f.json")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source_run_id", original.getString("run_id"));
        result.put("skill_id", original.getInt("skill_id"));
        result.put("fixture_sha256", original.get("fixture").getString("sha256"));
        result.put("scope", "Recorded two-skill hard points, real production systems; independent worlds; no map collision, caster input sequence, target damage or GPU comparison");
        result.put("production", name.equals("firestorm")
            ? FirestormOriginalAnalysis.simulate(original) : MoltenBoulderOriginalAnalysis.simulate(original));
        StringWriter text = new StringWriter(); JsonWriter writer = new JsonWriter(text);
        writer.setOutputType(JsonWriter.OutputType.json); write(writer, result); writer.flush();
        Files.writeString(directory.resolve("druid-" + name + "-production-1.10f.json"), text + "\n");
      }
    } finally { RiiabloTest.teardown(); }
  }

  private static void write(JsonWriter writer, Object value) throws Exception {
    if (value instanceof Map) {
      writer.object();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        writer.name(entry.getKey().toString()); write(writer, entry.getValue());
      }
      writer.pop();
    } else if (value instanceof Iterable) {
      writer.array(); for (Object entry : (Iterable<?>) value) write(writer, entry); writer.pop();
    } else writer.value(value);
  }
}
