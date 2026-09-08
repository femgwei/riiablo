package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.StatListReader;
import com.riiablo.item.ItemReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Optional integration gate for a real Diablo II 1.10f save file. */
class D2SRealSaveIntegrationTest extends RiiabloTest {
  @Test
  void parsesAndRewritesReal110fSave() throws Exception {
    String configured = System.getProperty("d2realSave");
    if (configured == null || configured.isEmpty()) configured = System.getenv("D2_REAL_SAVE");
    assumeTrue(configured != null && !configured.isEmpty(),
        "set -Dd2realSave=<path> or D2_REAL_SAVE to enable the real-save gate");

    Path path = Paths.get(configured);
    assumeTrue(Files.isRegularFile(path), "real save does not exist: " + path);
    byte[] source = Files.readAllBytes(path);
    D2S decoded = D2SReader.INSTANCE.readComplete(
        source, new StatListReader(), new ItemReader());

    assertEquals(D2S.VERSION_110, decoded.version);
    assertTrue(decoded.bodyRead());
    assertTrue(decoded.items != null && decoded.items.items.size > 0,
        "fixture should contain at least one equipped/inventory item");

    byte[] rewritten = new D2SWriter96().writeD2S(decoded);
    D2S roundTrip = D2SReader.INSTANCE.readComplete(
        rewritten, new StatListReader(), new ItemReader());
    assertEquals(decoded.items.items.size, roundTrip.items.items.size);
    assertEquals(decoded.corpse.items.size, roundTrip.corpse.items.size);
    assertEquals(decoded.merc.seed, roundTrip.merc.seed);
  }
}
