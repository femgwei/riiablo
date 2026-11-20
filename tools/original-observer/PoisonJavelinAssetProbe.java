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

/** Effective original rows and DCC headers, without a game window or GPU. */
public class PoisonJavelinAssetProbe {
  public static void main(String[] args) throws Exception {
    HeadlessApplication app = new HeadlessApplication(new ApplicationAdapter() {});
    try {
      MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(args[0]));
      String[] lines = resolver.resolve("data/global/excel/missiles.txt").readString("ISO-8859-1").split("\\r?\\n");
      String[] columns = lines[0].split("\\t", -1);
      StringWriter text = new StringWriter();
      JsonWriter json = new JsonWriter(text);
      json.setOutputType(JsonWriter.OutputType.json);
      json.object().set("home", Path.of(args[0]).toRealPath().toString()).array("missiles");
      String mode = args.length > 2 ? args[2] : "poison-javelin";
      Set<String> selected = "firestorm".equals(mode)
          ? Set.of("firestormmaker", "firestorm")
          : "moltenboulder".equals(mode)
              ? Set.of("moltenboulderemerge", "moltenboulder", "moltenboulderfirepath",
                  "moltenboulderexplode", "moltenboulder-flyingrocks", "firewall", "firesmall", "firemedium")
              : Set.of("poisonjav", "poisonjavcloud", "plaguejavelin", "plaguejavcloud", "poisonpuff");
      for (String line : lines) {
        String[] values = line.split("\\t", -1);
        if (!selected.contains(values[0])) continue;
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(values.length, columns.length); i++) row.put(columns[i], values[i]);
        json.object().object("row");
        for (var field : row.entrySet()) if (!field.getKey().isEmpty()) json.set(field.getKey(), field.getValue());
        json.pop();
        if (row.get("CelFile") == null || row.get("CelFile").isEmpty()
            || "null".equalsIgnoreCase(row.get("CelFile"))) {
          json.set("visible_dcc", false).pop();
          continue;
        }
        byte[] data = resolver.resolve("data/global/missiles/" + row.get("CelFile") + ".dcc").readBytes();
        DCC dcc = DCC.loadFromArray(data);
        try {
          json.set("dcc_sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)))
              .set("directions", dcc.getNumDirections()).set("frames", dcc.getNumFramesPerDir());
        } finally { dcc.dispose(); }
        json.pop();
      }
      json.pop();
      if ("firestorm".equals(mode) || "moltenboulder".equals(mode)) {
        String[] skills = resolver.resolve("data/global/excel/skills.txt").readString("ISO-8859-1").split("\\r?\\n");
        String[] header = skills[0].split("\\t", -1);
        for (String line : skills) {
          String[] values = line.split("\\t", -1);
          if (!("firestorm".equals(mode) && "Firestorm".equalsIgnoreCase(values[0]))
              && !("moltenboulder".equals(mode) && "Molten Boulder".equalsIgnoreCase(values[0]))) continue;
          json.object("skill");
          for (int i = 0; i < Math.min(values.length, header.length); i++)
            if (!header[i].isEmpty()) json.set(header[i], values[i]);
          json.pop();
        }
      }
      json.pop().flush();
      Files.writeString(Path.of(args[1]), text + "\n");
    } finally { app.exit(); }
  }
}
