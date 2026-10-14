package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeObjectPopulationResourceTest extends com.riiablo.RiiabloTest {
  @Test void realObjGroupsGenerateLooseRocksChestsBarrelsAndUrnsExactlyOnce() {
    for (int group : new int[]{38,3,10,29}) {
      Map.Zone zone = zone(group);
      NativeObjectPopulation population = new NativeObjectPopulation();
      int count = population.populate(zone, null);
      assertTrue(count > 0, "missing native group " + group);
      for (Map.NativeObject object : zone.getNativeObjects()) {
        Objects.Entry base = Riiablo.files.objects.get(object.presetIndex);
        assertNotNull(base);
        assertTrue(object.sourceFile.startsWith("ObjGroup/"));
        assertFalse(object.ds1Raw);
        assertEquals(object.roomId, zone.findRoomEx(zone.x() + object.x, zone.y() + object.y).id);
        if (group == 38) assertTrue(object.presetIndex == 155 || object.presetIndex == 174 || object.presetIndex == 175);
        if (group == 3) assertTrue(object.presetIndex == 5 || object.presetIndex == 6);
        if (group == 10) {
          assertTrue(base.Description.equals("Barrel") || base.Description.equals("Barrel Exploding"), base.Description);
          assertTrue(object.presetIndex == 7 || object.presetIndex == 11);
        }
        if (group == 29) assertTrue(java.util.Arrays.stream(new int[]{4,9,52,94,95}).anyMatch(id -> id == object.presetIndex));
      }
      assertEquals(0, population.populate(zone, null), "room reactivation must not duplicate population");
      assertEquals(count, zone.getNativeObjects().size);
    }
  }
  @Test void blockedTerrainDoesNotGetForcedObjectsAndDensityUsesNativeShifts() {
    Map.Zone zone = zone(3);
    java.util.Arrays.fill(zone.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    assertEquals(0, new NativeObjectPopulation().populate(zone, null));
    assertEquals(3, NativeObjectPopulation.densityCount(64,40,40));
  }
  private static Map.Zone zone(int group) {
    Map.Zone zone = new Map.Zone();
    zone.map = new Map(1,0);
    zone.level = new Levels.Entry(); zone.level.Id = 2;
    zone.level.ObjGrp = new int[]{group}; zone.level.ObjPrb = new int[]{100};
    zone.width = zone.height = 400; zone.flags = new byte[400*400];
    for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
      zone.addRoomEx(x*100,y*100,100,100).setAdjacentRoomIds(new int[0]);
    }
    return zone;
  }
}
