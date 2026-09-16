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
import com.badlogic.gdx.utils.Array;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
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
      Map<Integer, String> nativeCells = exportNativeCellsFromSave(dc6, palette, output, manifest);
      exportNativeRiiabloComparison(nativeCells, output, manifest);
      exportNativeCompositeFromSave(dc6, palette, output, manifest);
      exportBridgeFromSave(dc6, palette, output, manifest);
      Files.write(new File(output, "索引.txt").toPath(),
          manifest.toString().getBytes(StandardCharsets.UTF_8));
      assertTrue(new File(output, "桥_中段_cell79.png").isFile());
    } finally {
      dc6.dispose();
    }
  }

  /** Exports every unique DC6 cell referenced by an original .maN fixture. */
  private static Map<Integer, String> exportNativeCellsFromSave(DC6 dc6, Palette palette,
      File output, StringBuilder manifest) throws Exception {
    Map<Integer, String> cells = new TreeMap<>();
    String fixtures = value("D2_AUTOMAP_FIXTURES", "d2.automap.fixtures");
    if (fixtures == null || fixtures.isEmpty()) return cells;
    String character = value("D2_AUTOMAP_CHARACTER", "d2.automap.character");
    if (character == null || character.isEmpty()) character = "aaa";
    File save = new File(fixtures, character + ".ma0");
    if (!save.isFile()) return cells;

    AutomapExplorationStore.MaFile ma = AutomapExplorationStore.readMa(new FileHandle(save));
    for (int layerNo = 0; layerNo < ma.layers.length; layerNo++) {
      if (!includeLayer(layerNo)) continue;
      AutomapExplorationStore.Layer layer = ma.layers[layerNo];
      if (layer == null) continue;
      collectNativeCells(cells, "floors", layer.floors);
      collectNativeCells(cells, "walls", layer.walls);
      collectNativeCells(cells, "objects", layer.objects);
      collectNativeCells(cells, "extras", layer.extras);
    }
    for (Map.Entry<Integer, String> entry : cells.entrySet()) {
      int frame = entry.getKey();
      if (frame < 0 || frame >= dc6.getNumFramesPerDir()) continue;
      exportFrame(dc6, palette, frame,
          new File(output, "native-cell-" + frame + ".png"));
      manifest.append("native-cell-").append(frame).append(".png cell=")
          .append(frame).append(" refs=").append(entry.getValue()).append('\n');
    }
    return cells;
  }

  /**
   * Compares the native cell set recorded by D2Client (.ma0) with the cell set
   * exported by the Riiablo off-screen renderer.  The PNGs are decoded from
   * the same native MaxiMap.dc6, so differences here identify cell selection
   * or coordinate issues rather than palette/texture decoding differences.
   */
  private static void exportNativeRiiabloComparison(Map<Integer, String> nativeCells,
      File output, StringBuilder manifest) throws Exception {
    String configured = value("RIABLO_AUTOMAP_CSV", "riiablo.automap.csv");
    if (configured == null || configured.isEmpty()) {
      configured = "build/automap-bb-town/automap-cells-level-1.csv";
    }
    File riiabloCsv = new File(configured);
    if (!riiabloCsv.isFile()) {
      manifest.append("comparison=skipped missing Riiablo CSV ")
          .append(riiabloCsv.getPath()).append('\n');
      return;
    }

    Map<Integer, String> riiabloCells = new TreeMap<>();
    Map<Integer, Integer> riiabloCounts = new TreeMap<>();
    for (String line : Files.readAllLines(riiabloCsv.toPath(), StandardCharsets.UTF_8)) {
      if (line.isEmpty() || line.startsWith("category,")) continue;
      String[] fields = line.split(",", -1);
      if (fields.length < 6) continue;
      int cell;
      try {
        cell = Integer.parseInt(fields[1].trim());
      } catch (NumberFormatException ignored) {
        continue;
      }
      riiabloCounts.put(cell, riiabloCounts.containsKey(cell) ? riiabloCounts.get(cell) + 1 : 1);
      if (!riiabloCells.containsKey(cell)) {
        riiabloCells.put(cell, fields[0] + "@" + fields[4] + "," + fields[5]);
      }
    }

    Map<Integer, String> allCells = new TreeMap<>();
    allCells.putAll(nativeCells);
    allCells.putAll(riiabloCells);
    File report = new File(output, "native-vs-riiablo-cells.csv");
    StringBuilder csv = new StringBuilder("cell,nativeRefs,riiabloCount,riiabloFirst,status\n");
    int nativeOnly = 0;
    int riiabloOnly = 0;
    int both = 0;
    for (Map.Entry<Integer, String> entry : allCells.entrySet()) {
      int cell = entry.getKey();
      boolean inNative = nativeCells.containsKey(cell);
      boolean inRiiablo = riiabloCells.containsKey(cell);
      String status = inNative && inRiiablo ? "both"
          : inNative ? "native-only" : "riiablo-only";
      if ("both".equals(status)) both++;
      else if ("native-only".equals(status)) nativeOnly++;
      else riiabloOnly++;
      csv.append(cell).append(',')
          .append(csvValue(nativeCells.get(cell))).append(',')
          .append(riiabloCounts.containsKey(cell) ? riiabloCounts.get(cell) : 0).append(',')
          .append(csvValue(riiabloCells.get(cell))).append(',').append(status).append('\n');
    }
    Files.write(report.toPath(), csv.toString().getBytes(StandardCharsets.UTF_8));
    manifest.append("comparisonCsv=").append(report.getPath())
        .append(" nativeOnly=").append(nativeOnly)
        .append(" riiabloOnly=").append(riiabloOnly)
        .append(" both=").append(both).append('\n');
  }

  private static String csvValue(String value) {
    if (value == null) return "";
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  private static void collectNativeCells(Map<Integer, String> cells, String category,
      Array<AutomapExplorationStore.Cell> source) {
    for (AutomapExplorationStore.Cell cell : source) {
      String ref = category + "@" + cell.x + "," + cell.y;
      String previous = cells.get(cell.cellNo);
      if (previous == null) cells.put(cell.cellNo, ref);
      else if (!previous.contains(ref)) cells.put(cell.cellNo, previous + ";" + ref);
    }
  }

  /** Composes every cell recorded by D2Client so relative placement can be compared visually. */
  private static void exportNativeCompositeFromSave(DC6 dc6, Palette palette, File output,
      StringBuilder manifest) throws Exception {
    String fixtures = value("D2_AUTOMAP_FIXTURES", "d2.automap.fixtures");
    if (fixtures == null || fixtures.isEmpty()) return;
    String character = value("D2_AUTOMAP_CHARACTER", "d2.automap.character");
    if (character == null || character.isEmpty()) character = "aaa";
    File save = new File(fixtures, character + ".ma0");
    if (!save.isFile()) return;

    AutomapExplorationStore.MaFile ma = AutomapExplorationStore.readMa(new FileHandle(save));
    Array<AutomapExplorationStore.Cell> cells = new Array<>();
    for (int layerNo = 0; layerNo < ma.layers.length; layerNo++) {
      if (!includeLayer(layerNo)) continue;
      AutomapExplorationStore.Layer layer = ma.layers[layerNo];
      if (layer == null) continue;
      cells.addAll(layer.floors);
      cells.addAll(layer.walls);
      cells.addAll(layer.objects);
      cells.addAll(layer.extras);
    }
    if (cells.size == 0) return;

    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
    for (AutomapExplorationStore.Cell cell : cells) {
      if (cell.cellNo < 0 || cell.cellNo >= dc6.getNumFramesPerDir()) continue;
      BBox box = dc6.getBox(0, cell.cellNo);
      minX = Math.min(minX, cell.x + box.xMin);
      minY = Math.min(minY, cell.y + box.yMin);
      maxX = Math.max(maxX, cell.x + box.xMax);
      maxY = Math.max(maxY, cell.y + box.yMax);
    }
    if (minX == Integer.MAX_VALUE) return;
    Pixmap composite = new Pixmap(maxX - minX + 1, maxY - minY + 1,
        Pixmap.Format.RGBA8888);
    composite.setBlending(Pixmap.Blending.SourceOver);
    for (AutomapExplorationStore.Cell cell : cells) {
      if (cell.cellNo < 0 || cell.cellNo >= dc6.getNumFramesPerDir()) continue;
      BBox box = dc6.getBox(0, cell.cellNo);
      drawFrame(dc6.getPixmap(0, cell.cellNo), palette, composite,
          cell.x + box.xMin - minX, cell.y + box.yMin - minY);
    }
    File png = new File(output, "native-ma0-composite.png");
    PixmapIO.writePNG(new FileHandle(png), composite);
    composite.dispose();
    manifest.append("nativeComposite=").append(png.getPath())
        .append(" cells=").append(cells.size)
        .append(" bounds=").append(minX).append(',').append(minY)
        .append("..").append(maxX).append(',').append(maxY).append('\n');
  }

  /** Composes bridge cells 73..79 from an original character .maN sidecar. */
  private static void exportBridgeFromSave(DC6 dc6, Palette palette, File output,
      StringBuilder manifest) throws Exception {
    String fixtures = value("D2_AUTOMAP_FIXTURES", "d2.automap.fixtures");
    if (fixtures == null || fixtures.isEmpty()) return;
    String character = value("D2_AUTOMAP_CHARACTER", "d2.automap.character");
    if (character == null || character.isEmpty()) character = "aaa";
    File save = new File(fixtures, character + ".ma0");
    if (!save.isFile()) return;

    AutomapExplorationStore.MaFile ma = AutomapExplorationStore.readMa(new FileHandle(save));
    Array<AutomapExplorationStore.Cell> bridge = new Array<>();
    for (int layerNo = 0; layerNo < ma.layers.length; layerNo++) {
      if (!includeLayer(layerNo)) continue;
      AutomapExplorationStore.Layer layer = ma.layers[layerNo];
      if (layer == null) continue;
      addBridgeCells(layer.floors, bridge);
      addBridgeCells(layer.walls, bridge);
      addBridgeCells(layer.objects, bridge);
      addBridgeCells(layer.extras, bridge);
    }
    if (bridge.size == 0) return;

    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
    for (AutomapExplorationStore.Cell cell : bridge) {
      BBox box = dc6.getBox(0, cell.cellNo);
      minX = Math.min(minX, cell.x + box.xMin);
      minY = Math.min(minY, cell.y + box.yMin);
      maxX = Math.max(maxX, cell.x + box.xMax);
      maxY = Math.max(maxY, cell.y + box.yMax);
    }
    Pixmap composite = new Pixmap(maxX - minX + 1, maxY - minY + 1,
        Pixmap.Format.RGBA8888);
    composite.setBlending(Pixmap.Blending.None);
    for (AutomapExplorationStore.Cell cell : bridge) {
      BBox box = dc6.getBox(0, cell.cellNo);
      drawFrame(dc6.getPixmap(0, cell.cellNo), palette, composite,
          cell.x + box.xMin - minX, cell.y + box.yMin - minY);
      manifest.append("桥存档 cell=").append(cell.cellNo)
          .append(" x=").append(cell.x).append(" y=").append(cell.y).append('\n');
    }
    PixmapIO.writePNG(new FileHandle(new File(output, "桥_存档拼接.png")), composite);
    composite.dispose();
  }

  private static void addBridgeCells(Array<AutomapExplorationStore.Cell> source,
      Array<AutomapExplorationStore.Cell> target) {
    for (AutomapExplorationStore.Cell cell : source) {
      if (cell.cellNo >= 73 && cell.cellNo <= 79) target.add(cell);
    }
  }

  private static boolean includeLayer(int layerNo) {
    String configured = value("D2_AUTOMAP_LEVEL", "d2.automap.level");
    if (configured == null || configured.trim().isEmpty()) return true;
    try {
      return Integer.parseInt(configured.trim()) == layerNo;
    } catch (NumberFormatException ignored) {
      return true;
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
    frames.put("Riiablo额外墙体_cell65", 65);
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
    drawFrame(indexed, palette, rgba, 0, 0);
    PixmapIO.writePNG(new FileHandle(output), rgba);
    rgba.dispose();
  }

  private static void drawFrame(Pixmap indexed, Palette palette, Pixmap rgba, int offsetX,
      int offsetY) {
    ByteBuffer pixels = indexed.getPixels().duplicate();
    int[] colors = palette.get();
    for (int y = 0; y < indexed.getHeight(); y++) {
      for (int x = 0; x < indexed.getWidth(); x++) {
        int paletteIndex = pixels.get(y * indexed.getWidth() + x) & 0xff;
        rgba.drawPixel(offsetX + x, offsetY + y, colors[paletteIndex]);
      }
    }
  }

  private static String value(String environment, String property) {
    String value = System.getProperty(property);
    if (value == null || value.isEmpty()) value = System.getenv(environment);
    return value;
  }
}
