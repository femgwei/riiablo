package com.riiablo.map.d2moo;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.map.Map;
import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act5PopulationGroupResourceTest extends RiiabloTest {
  @Test void monPlaceGroupMarkersKeepTheClassAndGroupBudget() {
    Map.Zone zone = new Map.Zone();
    zone.map = new Map(1, 0);
    // Other tests use the same real bridge; the group metadata needs no GL.
    Map map = zone.map;
    com.riiablo.map.Act5MapBuilderD2MOD.INSTANCE.generate(map, 1, 0);
    zone = map.findZone(Riiablo.files.Levels.get(110));
    assertNotNull(zone);
    assertEquals(0, zone.level.MonDen[0], "normal Bloody Foothills depends on preset groups");
    for (int index : new int[]{5, 11}) {
      Array<Map.NativeMonster> output = new Array<>();
      assertTrue(Act5D2MOOLayoutBridge.queueNormalGroupMarker(zone, output, index, true, 0, 1, 20, 20));
      assertEquals(1, output.size);
      Map.NativeMonster marker = output.first();
      com.riiablo.codec.excel.MonStats.Entry monster = Riiablo.files.monstats.get(index == 5 ? "deathmauler1" : "minion1");
      assertEquals(monster.hcIdx, marker.monsterId);
      assertTrue(marker.populationGroupSize >= monster.MinGrp && marker.populationGroupSize <= monster.MaxGrp);
      assertEquals(0, Act5D2MOOLayoutBridge.queueNativeMonsters(zone, output),
          "group markers wait for terrain installation, not a single low-id monster");
    }
  }
  @Test void monPlaceNothingMustNotBecomeAnUnrelatedMonster() {
    assertEquals(-1, Act5D2MOOLayoutBridge.resolveNativeMonster(51, true).monsterId);
  }
}
