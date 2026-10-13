package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonPlaceResourceTest extends RiiabloTest {
  @Test void readsTheNativePlacementDirectiveTable() {
    com.badlogic.gdx.files.FileHandle file = Riiablo.mpqs.resolve("data/global/excel/monplace.txt");
    assertTrue(file.exists());
    String table = new String(file.readBytes(), StandardCharsets.ISO_8859_1);
    assertTrue(table.contains("place_fallen"));
  }

  @Test void nativeTerrainPresetIdsResolveToTheCorrectAct() {
    for (int def : new int[] {301, 362, 388, 414, 481, 510, 529, 605, 652, 753, 799, 881}) {
      com.riiablo.codec.excel.LvlPrest.Entry preset = Riiablo.files.LvlPrest.get(def);
      assertNotNull(preset, "native Def=" + def);
      System.out.println("[NATIVE_PRESET] def=" + def + " name=" + preset.Name
          + " level=" + preset.LevelId + " files=" + java.util.Arrays.toString(preset.File));
    }
  }
}
