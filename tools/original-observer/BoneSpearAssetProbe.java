import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.codec.DCC;
import com.riiablo.codec.util.BBox;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.io.StringWriter;
import java.util.*;

/** Offline comparison of effective MPQ resources. Does not launch a game or upload textures. */
public class BoneSpearAssetProbe {
  static void writeJson(JsonWriter writer, Object value) throws Exception {
    if (value instanceof Map<?, ?> map) {
      writer.object();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        writer.name(entry.getKey().toString());
        writeJson(writer, entry.getValue());
      }
      writer.pop();
    } else if (value instanceof Iterable<?> items) {
      writer.array();
      for (Object item : items) writeJson(writer, item);
      writer.pop();
    } else {
      writer.value(value);
    }
  }
  static String hash(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
  static Map<String, Object> inspect(String home) throws Exception {
    MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(home));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("home", home);
    Map<String, Object> rows = new LinkedHashMap<>();
    FileHandle table = resolver.resolve("data/global/excel/missiles.txt");
    if (table != null) {
      String[] lines = new String(table.readBytes(), java.nio.charset.StandardCharsets.ISO_8859_1).split("\\r?\\n");
      String[] columns = lines[0].split("\\t", -1);
      for (String line : lines) {
        String[] fields = line.split("\\t", -1);
        if (!Set.of("bonespear", "bonespeartrail", "bonespearexplode").contains(fields[0])) continue;
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(columns.length, fields.length); i++)
          if (!columns[i].isEmpty()) row.put(columns[i], fields[i]);
        rows.put(fields[0], row);
      }
    }
    result.put("missile_rows", rows);
    List<Object> resources = new ArrayList<>();
    for (String name : new String[] {"BoneSpear", "BoneSpearTrail"}) {
      String path = "data/global/missiles/" + name + ".dcc";
      FileHandle file = resolver.resolve(path);
      if (file == null) throw new IllegalStateException("Missing " + path);
      byte[] bytes = file.readBytes();
      DCC dcc = DCC.loadFromArray(bytes);
      try {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("path", path);
        resource.put("sha256", hash(bytes));
        resource.put("directions", dcc.getNumDirections());
        resource.put("frames", dcc.getNumFramesPerDir());
        List<Object> frames = new ArrayList<>();
        for (int direction = 0; direction < dcc.getNumDirections(); direction++) {
          BBox box = dcc.getBox(direction);
          for (int frame = 0; frame < dcc.getNumFramesPerDir(); frame++) {
            Pixmap pixmap = dcc.getPixmap(direction, frame);
            ByteBuffer buffer = pixmap.getPixels().duplicate();
            buffer.clear();
            byte[] indices = new byte[pixmap.getWidth() * pixmap.getHeight()];
            buffer.get(indices);
            int count = 0;
            double sx = 0, sy = 0;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int y = 0; y < pixmap.getHeight(); y++) {
              for (int x = 0; x < pixmap.getWidth(); x++) {
                if (indices[y * pixmap.getWidth() + x] == 0) continue;
                int screenX = x + box.xMin, screenY = y + box.yMin;
                sx += screenX; sy += screenY; count++;
                minX = Math.min(minX, screenX); maxX = Math.max(maxX, screenX);
                minY = Math.min(minY, screenY); maxY = Math.max(maxY, screenY);
              }
            }
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("direction", direction); f.put("frame", frame);
            f.put("pixels_sha256", hash(indices)); f.put("opaque_pixels", count);
            f.put("centroid_x", count == 0 ? null : sx / count);
            f.put("centroid_y", count == 0 ? null : sy / count);
            f.put("bounds", count == 0 ? List.of() : List.of(minX, minY, maxX, maxY));
            frames.add(f);
          }
        }
        resource.put("decoded_frames", frames);
        resources.add(resource);
      } finally { dcc.dispose(); }
    }
    result.put("resources", resources);
    return result;
  }
  public static void main(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("home-a home-b output-json");
    HeadlessApplication app = new HeadlessApplication(new ApplicationAdapter() {});
    try {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("scope", "Offline effective MPQ bytes and decoded indexed pixels; no runtime or GPU equivalence claim");
      result.put("installations", List.of(inspect(args[0]), inspect(args[1])));
      StringWriter text = new StringWriter();
      JsonWriter writer = new JsonWriter(text);
      writer.setOutputType(JsonWriter.OutputType.json);
      writeJson(writer, result);
      writer.flush();
      Path output = Path.of(args[2]);
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(output, text.toString() + "\n");
      System.out.println(output.toAbsolutePath());
    } finally { app.exit(); }
  }
}
