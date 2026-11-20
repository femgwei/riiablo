package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.RiiabloTest;
import com.riiablo.item.*;
import com.riiablo.attributes.StatListReader;
import org.junit.jupiter.api.Test;

class OriginalPoisonJavelinFixtureTest extends RiiabloTest {
  @Test void isolatedAmazonHasOneUnenchantedEquippedJavelin() throws Exception {
    byte[] bytes;
    try (java.io.InputStream in = getClass().getResourceAsStream("/native/poison-javelin-input-fixture.d2s")) {
      assertNotNull(in);
      bytes = in.readAllBytes();
    }
    D2S save = D2SReader.INSTANCE.readComplete(bytes, new StatListReader(), new ItemReader());
    assertEquals("CXPoison", save.name());
    assertEquals(1, save.items.items.size);
    Item javelin = save.items.items.first();
    assertEquals("jav", javelin.code);
    assertEquals(Location.EQUIPPED, javelin.location);
    assertEquals(BodyLoc.RARM, javelin.bodyLoc);
    assertEquals(Quality.LOW, javelin.quality); // Donor's nonmagical starting javelin.
    assertTrue(javelin.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt() > 0);
  }
}
