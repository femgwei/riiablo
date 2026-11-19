package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.skill.NecromancerSkills;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** State evidence and shipped assets; deliberately does not assert draw parity. */
class BonePrisonOriginalStateTest extends RiiabloTest {
  @Test void nativeRingGeometryAndDeathSequenceAreRetained() {
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/bone-prison-states-1.10f.json"));
    assertTrue(fixture.getBoolean("background_verified"));
    assertTrue(fixture.getBoolean("owned_game_closed"));
    Set<String> expected = new HashSet<>();
    for (int i = 0; i < NecromancerSkills.getBonePrisonSegmentCount(); i++) {
      int[] offset = new int[2];
      NecromancerSkills.getBonePrisonOffset(i, offset);
      expected.add(offset[0] + "," + offset[1]);
    }
    for (String side : new String[] {"server", "client"}) {
      Set<String> actual = new HashSet<>();
      for (JsonValue unit : fixture.get("units")) {
        if (!side.equals(unit.getString("side"))) continue;
        assertEquals(344, unit.getInt("class_id"));
        actual.add(unit.getInt("cell_offset_x") + "," + unit.getInt("cell_offset_y"));
        JsonValue modes = unit.get("transitions");
        assertEquals(3, modes.size);
        assertEquals(1, modes.get(0).getInt("mode"));
        assertEquals(0, modes.get(1).getInt("mode"));
        assertEquals(12, modes.get(2).getInt("mode"));
        assertEquals(610, modes.get(1).getInt("game_frame") - unit.getInt("first_frame"));
        assertEquals(15, modes.get(2).getInt("game_frame") - modes.get(1).getInt("game_frame"));
        assertTrue(unit.getBoolean("still_present_at_capture_end"));
      }
      assertEquals(expected, actual, side + " ring offsets");
    }
  }

  @Test void reportActualMonsterArtMappingsAndDecodeObservedModes() {
    for (int id = 344; id <= 347; id++) {
      MonStats.Entry row = Riiablo.files.monstats.get(id);
      MonStats2.Entry appearance = Riiablo.files.monstats2.get(row.MonStatsEx);
      System.out.println("PRISON_ART class=" + id + " id=" + row.Id + " token=" + row.Code
          + " appearance=" + row.MonStatsEx + " spawn=" + row.spawnmode
          + " components=" + java.util.Arrays.toString(appearance.ComponentV));
      for (String mode : new String[] {"NU", "DT", "DD"}) {
        String path = "data/global/monsters/" + row.Code + "/cof/"
            + row.Code + mode + appearance.BaseW + ".cof";
        assertTrue(Riiablo.mpqs.contains(path), path);
        COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(path));
        System.out.println("PRISON_COF class=" + id + " mode=" + mode
            + " dirs=" + cof.getNumDirections() + " frames=" + cof.getNumFramesPerDir());
        if (id == 344) {
          assertEquals("DT".equals(mode) ? 15 : 1, cof.getNumFramesPerDir());
          assertEquals(4, cof.getNumDirections());
          for (int l = 0; l < cof.getNumLayers(); l++) {
            COF.Layer layer = cof.getLayer(l);
            String component = com.riiablo.engine.Engine.getComposite(layer.component);
            String dccPath = "data/global/monsters/" + row.Code + "/" + component
                + "/" + row.Code + component + "lit" + mode + layer.weaponClass + ".dcc";
            assertTrue(Riiablo.mpqs.contains(dccPath), dccPath);
            DCC dcc = DCC.loadFromFile(Riiablo.mpqs.resolve(dccPath));
            try {
              assertEquals(cof.getNumDirections(), dcc.getNumDirections(), dccPath);
              assertEquals(cof.getNumFramesPerDir(), dcc.getNumFramesPerDir(), dccPath);
              System.out.println("PRISON_DCC " + dccPath + " dirs=" + dcc.getNumDirections()
                  + " frames=" + dcc.getNumFramesPerDir());
            } finally {
              dcc.dispose();
            }
          }
        }
      }
    }
  }
}
