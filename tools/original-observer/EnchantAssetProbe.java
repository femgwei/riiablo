import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.codec.DCC;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Original effective Enchant tables and overlay headers, without executing the game. */
public class EnchantAssetProbe {
  private static List<Map<String, String>> table(MPQFileHandleResolver resolver, String name) {
    String[] lines = resolver.resolve("data/global/excel/" + name + ".txt")
        .readString("ISO-8859-1").split("\\r?\\n");
    String[] header = lines[0].split("\\t", -1);
    List<Map<String, String>> rows = new ArrayList<>();
    for (int n = 1; n < lines.length; n++) {
      String[] values = lines[n].split("\\t", -1);
      Map<String, String> row = new LinkedHashMap<>();
      for (int i = 0; i < Math.min(header.length, values.length); i++)
        if (!header[i].isEmpty()) row.put(header[i], values[i]);
      rows.add(row);
    }
    return rows;
  }
  private static void row(JsonWriter json, Map<String, String> row) throws Exception {
    json.object(); for (var field : row.entrySet()) json.set(field.getKey(), field.getValue()); json.pop();
  }
  public static void main(String[] args) throws Exception {
    HeadlessApplication app = new HeadlessApplication(new ApplicationAdapter() {});
    try {
      MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(args[0]));
      StringWriter text = new StringWriter(); JsonWriter json = new JsonWriter(text);
      json.setOutputType(JsonWriter.OutputType.json);
      json.object().set("home", Path.of(args[0]).toRealPath().toString());
      for (String name : new String[] {"skills", "states"}) {
        for (Map<String, String> row : table(resolver, name)) {
          String key = row.get("skills".equals(name) ? "skill" : "state");
          if ("Enchant".equalsIgnoreCase(key)) { json.name(name); row(json, row); }
        }
      }
      json.array("overlays");
      for (Map<String, String> row : table(resolver, "overlay")) {
        if (!row.getOrDefault("overlay", "").toLowerCase(Locale.ROOT).contains("enchant")) continue;
        json.object().name("row"); row(json, row);
        byte[] bytes = resolver.resolve("data/global/overlays/" + row.get("Filename") + ".dcc").readBytes();
        DCC dcc = DCC.loadFromArray(bytes);
        try { json.set("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
            .set("directions", dcc.getNumDirections()).set("frames", dcc.getNumFramesPerDir()); }
        finally { dcc.dispose(); }
        json.pop();
      }
      json.pop().array("colors");
      int index = 0;
      for (Map<String, String> row : table(resolver, "colors")) {
        if (row.containsValue("cred")) { json.object().set("table_index", index).name("row"); row(json, row); json.pop(); }
        index++;
      }
      json.pop().pop().flush();
      Files.writeString(Path.of(args[1]), text + "\n");
    } finally { app.exit(); }
  }
}
