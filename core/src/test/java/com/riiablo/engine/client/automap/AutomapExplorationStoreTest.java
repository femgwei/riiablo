package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.badlogic.gdx.files.FileHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AutomapExplorationStoreTest {
  @Test void mapRoundTripMatchesNative24ByteLayout() throws Exception {
    byte[] sample = {12, 0, 0, 0, 1, 0, 0, 0, 15, (byte) 143, (byte) 159, 7,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    Path source = Files.createTempFile("native", ".map");
    Path target = Files.createTempFile("native-roundtrip", ".map");
    Files.write(source, sample);
    AutomapExplorationStore.MapSeeds map =
        AutomapExplorationStore.readMap(new FileHandle(source.toFile()));
    assertEquals(0x079f8f0f, map.seed(0));
    AutomapExplorationStore.writeMap(new FileHandle(target.toFile()), map);
    assertArrayEquals(sample, Files.readAllBytes(target));
  }

  @Test void maRoundTripPreservesNativeCellLists() throws Exception {
    AutomapExplorationStore.MaFile ma = new AutomapExplorationStore.MaFile();
    AutomapExplorationStore.Layer layer = new AutomapExplorationStore.Layer();
    layer.unknown = 0x43b9b75a;
    layer.floors.add(new AutomapExplorationStore.Cell(1, (short) -2032, (short) 7952));
    layer.walls.add(new AutomapExplorationStore.Cell(64, (short) -2016, (short) 7936));
    ma.layers[0] = layer;
    Path first = Files.createTempFile("native", ".ma0");
    Path second = Files.createTempFile("native-roundtrip", ".ma0");
    AutomapExplorationStore.writeMa(new FileHandle(first.toFile()), ma);
    AutomapExplorationStore.MaFile decoded =
        AutomapExplorationStore.readMa(new FileHandle(first.toFile()));
    assertEquals(0x43b9b75a, decoded.layers[0].unknown);
    assertEquals(-2032, decoded.layers[0].floors.first().x);
    AutomapExplorationStore.writeMa(new FileHandle(second.toFile()), decoded);
    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
  }

  @Test void nativeFixturesRoundTripByteForByteWhenAvailable() throws Exception {
    String fixtureDir = System.getenv("D2_AUTOMAP_FIXTURES");
    assumeTrue(fixtureDir != null && !fixtureDir.isEmpty());
    for (String character : new String[] {"aaa", "bb"}) {
      Path mapSource = Path.of(fixtureDir, character + ".map");
      Path maSource = Path.of(fixtureDir, character + ".ma0");
      Path mapTarget = Files.createTempFile(character, ".map");
      Path maTarget = Files.createTempFile(character, ".ma0");
      AutomapExplorationStore.writeMap(new FileHandle(mapTarget.toFile()),
          AutomapExplorationStore.readMap(new FileHandle(mapSource.toFile())));
      AutomapExplorationStore.writeMa(new FileHandle(maTarget.toFile()),
          AutomapExplorationStore.readMa(new FileHandle(maSource.toFile())));
      assertArrayEquals(Files.readAllBytes(mapSource), Files.readAllBytes(mapTarget));
      assertArrayEquals(Files.readAllBytes(maSource), Files.readAllBytes(maTarget));
    }
  }
}
