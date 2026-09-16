package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.riiablo.Palettes;
import com.riiablo.codec.DC6;
import com.riiablo.codec.Palette;
import com.riiablo.codec.util.BBox;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Opt-in exporter for visually checking native MaxiMap.dc6 frame identities. */
class AutomapFrameExportIntegrationTest {
  private static final String MAXIMAP = "data\\global\\ui\\AUTOMAP\\MaxiMap.dc6";
  private static HeadlessApplication application;

  @BeforeAll
  static void setUpApplication() {
    application = new HeadlessApplication(new ApplicationAdapter() {});
  }

  @AfterAll
  static void tearDownApplication() {
    if (application != null) application.exit();
  }

  @Test
  void exportNamedNativeFrames() throws Exception {
    String home = value("D2_110F_HOME", "d2.110f.home");
    Assumptions.assumeTrue(home != null && !home.isEmpty(),
        "Set D2_110F_HOME or -Dd2.110f.home to export native Automap frames");
    Assumptions.assumeTrue(new File(home, "Patch_D2.mpq").isFile(),
        "Not a complete Diablo II MPQ directory: " + home);

    String configuredOutput = value("AUTOMAP_PREVIEW_OUTPUT", "automap.preview.output");
    File output = configuredOutput == null || configuredOutput.isEmpty()
        ? new File("build/automap-frame-preview") : new File(configuredOutput);
    Files.createDirectories(output.toPath());

    MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(home));
    FileHandle dc6File = resolver.resolve(MAXIMAP);
    FileHandle paletteFile = resolver.resolve(Palettes.ACT1);
    assertTrue(dc6File != null && dc6File.exists(), "Missing " + MAXIMAP);
    assertTrue(paletteFile != null && paletteFile.exists(), "Missing " + Palettes.ACT1);

    DC6 dc6 = DC6.loadFromFile(dc6File);
    Palette palette = Palette.loadFromFile(paletteFile);
    try {
      Map<String, Integer> frames = namedFrames();
      StringBuilder manifest = new StringBuilder();
      manifest.append("source=MaxiMap.dc6\n")
          .append("palette=ACT1/pal.dat\n")
          .append("note=Objects.txt AutoMap=0 means no Automap image; 火把_错误候选 shows the frame previously drawn by mistake.\n\n");
      for (Map.Entry<String, Integer> entry : frames.entrySet()) {
        int frame = entry.getValue();
        assertTrue(frame >= 0 && frame < dc6.getNumFramesPerDir(),
            "Frame out of range: " + frame);
        File png = new File(output, entry.getKey() + ".png");
        exportFrame(dc6, palette, frame, png);
        BBox box = dc6.getBox(0, frame);
        manifest.append(entry.getKey()).append(".png")
            .append(" cell=").append(frame)
            .append(" box=").append(box.xMin).append(',').append(box.yMin)
            .append("..").append(box.xMax).append(',').append(box.yMax)
            .append('\n');
      }
      Files.write(new File(output, "索引.txt").toPath(),
          manifest.toString().getBytes(StandardCharsets.UTF_8));
      assertTrue(new File(output, "桥_中段_cell79.png").isFile());
    } finally {
      dc6.dispose();
    }
  }

  private static Map<String, Integer> namedFrames() {
    Map<String, Integer> frames = new LinkedHashMap<>();
    frames.put("火把", 0);
    frames.put("火把_错误候选_cell0", 0);
    frames.put("河流_上沿_cell4", 4);
    frames.put("河流_下沿_cell5", 5);
    frames.put("河流_中段A_cell6", 6);
    frames.put("河流_中段B_cell7", 7);
    frames.put("河流_中段C_cell8", 8);
    frames.put("桥_下沿A_cell73", 73);
    frames.put("桥_下沿B_cell74", 74);
    frames.put("桥_下沿C_cell75", 75);
    frames.put("桥_上沿A_cell76", 76);
    frames.put("桥_上沿B_cell77", 77);
    frames.put("桥_上沿C_cell78", 78);
    frames.put("桥", 79);
    frames.put("桥_中段_cell79", 79);
    frames.put("火堆_cell405", 405);
    return frames;
  }

  private static void exportFrame(DC6 dc6, Palette palette, int frame, File output) {
    Pixmap indexed = dc6.getPixmap(0, frame);
    Pixmap rgba = new Pixmap(indexed.getWidth(), indexed.getHeight(), Pixmap.Format.RGBA8888);
    rgba.setBlending(Pixmap.Blending.None);
    ByteBuffer pixels = indexed.getPixels().duplicate();
    int[] colors = palette.get();
    for (int y = 0; y < indexed.getHeight(); y++) {
      for (int x = 0; x < indexed.getWidth(); x++) {
        int paletteIndex = pixels.get(y * indexed.getWidth() + x) & 0xff;
        rgba.drawPixel(x, y, colors[paletteIndex]);
      }
    }
    PixmapIO.writePNG(new FileHandle(output), rgba);
    rgba.dispose();
  }

  private static String value(String environment, String property) {
    String value = System.getProperty(property);
    if (value == null || value.isEmpty()) value = System.getenv(environment);
    return value;
  }
}
