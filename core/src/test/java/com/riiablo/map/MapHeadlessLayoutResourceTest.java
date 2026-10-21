package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises each production Act layout against 1.10f MPQs without an ECS world. */
class MapHeadlessLayoutResourceTest extends RiiabloTest {
  @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
  void actLayoutAndNativeDescriptionsGenerateWithoutEntityAssembly(int act) {
    Map map = new Map(127897359, 0, new MapResources(Riiablo.files, null, Riiablo.mpqs));
    try {
      map.generate(act);
      assertTrue(map.getZones().size > 0, "act=" + act);
      int nativeRooms = 0;
      for (Map.Zone zone : map.getZones()) {
        assertTrue(zone.width() > 0 && zone.height() > 0, "level=" + zone.levelId());
        assertEquals(0, zone.getEntities().size, "layout must not allocate units");
        nativeRooms += zone.getRoomsEx().size;
      }
      assertTrue(nativeRooms > 0, "production layout must export native rooms, act=" + act);
    } finally { map.dispose(); }
  }
}
