package com.riiablo.presentation.automap;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DC6;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the original MPQ bitmaps and native .ma town tree, without GL. */
class NativeTownAutomapResourceTest extends RiiabloTest {
  @TempDir Path temporary;

  @Test void everyCallbackPieceResolvesToANonemptyOriginalTownFrame() {
    for (int levelId : new int[] {40, 103, 109}) {
      DC6 sprite = DC6.loadFromFile(Riiablo.mpqs.resolve(NativeTownAutomap.spritePath(levelId)));
      try {
        for (int variant = 0; variant < (levelId == 40 ? 2 : 1); variant++) {
          for (AutomapExplorationStore.Cell cell : NativeTownAutomap.cells(levelId, variant, 28, 28)) {
            assertTrue(cell.cellNo < sprite.getNumFramesPerDir(), "town frame " + cell.cellNo);
            Pixmap pixmap = sprite.getPixmap(0, cell.cellNo);
            int nonempty = 0;
            for (int y = 0; y < pixmap.getHeight(); y++) {
              for (int x = 0; x < pixmap.getWidth(); x++) {
                if (pixmap.getPixel(x, y) != 0) nonempty++;
              }
            }
            assertTrue(nonempty > 0, "blank town frame level=" + levelId + " frame=" + cell.cellNo);
          }
        }
      } finally {
        sprite.dispose();
      }
    }
  }

  @Test void townTreesRoundTripWithoutWorldTileRoundingOrMaxiMapConversion() throws Exception {
    AutomapManager manager = new AutomapManager();
    AutomapManager restored = new AutomapManager();
    try {
      for (int levelId : new int[] {40, 103, 109}) {
        manager.getOrCreateLayer(levelId).townCells.addAll(
            NativeTownAutomap.cells(levelId, 0, 28, 28));
      }
      FileHandle save = new FileHandle(temporary.resolve("town.ma0").toFile());
      AutomapExplorationStore.writeMa(save, manager.createNativeAutomap());
      AutomapExplorationStore.MaFile file = AutomapExplorationStore.readMa(save);
      restored.loadNativeAutomap(file);
      for (int levelId : new int[] {40, 103, 109}) {
        AutomapLayer source = manager.getLayer(levelId);
        AutomapLayer target = restored.getLayer(levelId);
        assertNotNull(target);
        assertEquals(source.townCells.size, target.townCells.size);
        assertEquals(levelId == 40 ? 1 : levelId == 103 ? 2 : 3,
            file.layers[Riiablo.files.Levels.get(levelId).Layer].unknown);
        for (int i = 0; i < source.townCells.size; i++) {
          AutomapExplorationStore.Cell a = source.townCells.get(i), b = target.townCells.get(i);
          assertEquals(a.cellNo, b.cellNo);
          assertEquals(a.x, b.x);
          assertEquals(a.y, b.y);
        }
        assertEquals(0, target.extras.size);
      }
    } finally {
      manager.dispose();
      restored.dispose();
    }
  }
}
