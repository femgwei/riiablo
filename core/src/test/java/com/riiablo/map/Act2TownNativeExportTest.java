package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.map.d2moo.Act2D2MOOLayoutBridge;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Exercises the actual MPQ-backed Lut Gholein exporter without a client window. */
class Act2TownNativeExportTest extends RiiabloTest {
  @Test void exportsTownNpcsFromTheMatchingNativePreset() {
    Map.Zone town = new Map.Zone();
    town.level = Riiablo.files.Levels.get(40);
    town.town = true;
    town.width = town.level.SizeX[0] * DT1.Tile.SUBTILE_SIZE;
    town.height = town.level.SizeY[0] * DT1.Tile.SUBTILE_SIZE;
    String source = null;
    for (LvlPrest.Entry preset : Riiablo.files.LvlPrest) {
      if (preset.LevelId != 40) continue;
      for (String file : preset.File) {
        if (file == null || file.isEmpty() || file.charAt(0) == '0') continue;
        if (Act2D2MOOLayoutBridge.populateTown(0xD2C1, Riiablo.NORMAL, town, file)) {
          source = file;
          break;
        }
      }
      if (source != null) break;
    }
    assertNotNull(source, "native town must export at least one matching LvlPrest variant");
    Set<Integer> ids = new HashSet<>();
    for (Map.NativeMonster npc : town.getNativeMonsters()) ids.add(npc.monsterId);
    assertFalse(ids.isEmpty(), "matching native town preset must export UNIT_MONSTER records");
    Set<Integer> legalAct2TownUnits = new HashSet<>();
    // D2MOO MonsterIds.h: Warriv2/Atma/Drognan/Fara, Act II residents,
    // Greiz/Elzix/Geglash/Jerhyn/Lysander, guards/vendors, Meshif1 and Cain2.
    for (int id : new int[] {
        175, 176, 177, 178, 195, 196, 197, 198, 199, 200, 201, 202,
        203, 204, 205, 210, 244, 331, 358, 377, 378}) {
      legalAct2TownUnits.add(id);
    }
    for (int id : ids) {
      assertTrue(legalAct2TownUnits.contains(id),
          "unexpected native Lut Gholein unit " + id + " exported=" + ids);
    }
    int count = town.getNativeMonsters().size;
    assertTrue(Act2D2MOOLayoutBridge.populateTown(0xD2C1, Riiablo.NORMAL, town, source));
    assertEquals(count, town.getNativeMonsters().size, "re-export must replace, not append");
    assertFalse(MapManager.shouldCreateCompatibilityPresetUnit(town, source, DS1.Object.DYNAMIC_TYPE));
    assertTrue(MapManager.shouldCreateCompatibilityPresetUnit(town, source, DS1.Object.STATIC_TYPE));
  }

  @Test void mismatchedExportPreservesExistingTownAndCompatibilityUnits() {
    Map.Zone town = new Map.Zone();
    town.level = Riiablo.files.Levels.get(40);
    town.town = true;
    town.width = town.height = 1000;
    Map.NativeMonster existing = new Map.NativeMonster(0, 178, 0, 10, 12, -1, null, false);
    town.getNativeMonsters().add(existing);
    assertFalse(Act2D2MOOLayoutBridge.populateTown(0xD2C1, Riiablo.NORMAL, town,
        "Act2/Town/not-the-selected-town.ds1"));
    assertSame(existing, town.getNativeMonsters().first());
    assertEquals(1, town.getNativeMonsters().size);
    assertNull(town.nativeTownMonsterSource());
    assertTrue(MapManager.shouldCreateCompatibilityPresetUnit(town, "Act2/Town/town.ds1",
        DS1.Object.DYNAMIC_TYPE));
  }

  @Test void sourceMatchingNormalizesOnlyTheMpqRootCaseAndSeparators() {
    assertTrue(Act2D2MOOLayoutBridge.sameSource("Act2/Town/town.ds1",
        "DATA\\GLOBAL\\TILES\\ACT2\\TOWN\\TOWN.DS1"));
    assertFalse(Act2D2MOOLayoutBridge.sameSource("Act2/Town/town.ds1", "Act2/Town/town2.ds1"));
    assertFalse(Act2D2MOOLayoutBridge.sameSource(null, null));
  }
}
