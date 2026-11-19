package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.StatListReader;
import com.riiablo.item.*;
import org.junit.jupiter.api.Test;

class OriginalPortalFixtureTest extends RiiabloTest {
  @Test void completeFixtureContainsEightDistinctInventoryPortalScrolls() throws Exception {
    byte[] bytes;
    try (java.io.InputStream stream = getClass().getResourceAsStream("/native/portal-input-fixture.d2s")) {
      assertNotNull(stream);
      bytes = stream.readAllBytes();
    }
    D2S save = D2SReader.INSTANCE.readComplete(bytes, new StatListReader(), new ItemReader());
    assertEquals("CXPortal", save.name());
    assertEquals(8, save.items.items.size);
    java.util.Set<Integer> cells = new java.util.HashSet<>();
    for (Item item : save.items.items) {
      assertEquals("tsc", item.code);
      assertEquals(Location.STORED, item.location);
      assertEquals(StoreLoc.INVENTORY, item.storeLoc);
      assertEquals(0, item.gridY);
      assertTrue(cells.add((int) item.gridX));
    }
  }
}
