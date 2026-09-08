package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.StatListReader;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.item.Item;
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
    assertEquals(decoded.name, roundTrip.name);
    assertEquals(decoded.flags, roundTrip.flags);
    assertEquals(decoded.charClass, roundTrip.charClass);
    assertEquals(decoded.level, roundTrip.level);
    assertEquals(decoded.alternate, roundTrip.alternate);
    assertArrayEquals(decoded.hotkeys, roundTrip.hotkeys);
    assertArrayEquals(decoded.towns, roundTrip.towns);
    assertArrayEquals(decoded.skills.skills, roundTrip.skills.skills);
    assertMatrixEquals(decoded.quests.flags, roundTrip.quests.flags);
    assertMatrixEquals(decoded.waypoints.flags, roundTrip.waypoints.flags);
    assertNpcFlagsEqual(decoded.npcs.flags, roundTrip.npcs.flags);
    assertStatsEqual(decoded.stats.attrs.base(), roundTrip.stats.attrs.base());
    assertEquals(decoded.items.items.size, roundTrip.items.items.size);
    for (int i = 0; i < decoded.items.items.size; i++) {
      assertItemEqual(decoded.items.items.get(i), roundTrip.items.items.get(i), i);
    }
    assertEquals(decoded.corpse.items.size, roundTrip.corpse.items.size);
    assertEquals(decoded.merc.seed, roundTrip.merc.seed);
  }

  private static void assertMatrixEquals(byte[][] expected, byte[][] actual) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) assertArrayEquals(expected[i], actual[i]);
  }

  private static void assertNpcFlagsEqual(byte[][][] expected, byte[][][] actual) {
    assertEquals(expected.length, actual.length);
    for (int greeting = 0; greeting < expected.length; greeting++) {
      assertMatrixEquals(expected[greeting], actual[greeting]);
    }
  }

  private static void assertStatsEqual(StatListRef expected, StatListRef actual) {
    assertEquals(expected.size(), actual.size());
    for (StatRef stat : expected) {
      StatRef restored = actual.get(stat.id(), stat.encodedParams());
      assertNotNull(restored, "missing stat " + stat.id() + ":" + stat.encodedParams());
      assertEquals(stat.encodedValues(), restored.encodedValues(),
          "stat value " + stat.id() + ":" + stat.encodedParams());
    }
  }

  private static void assertItemEqual(Item expected, Item actual, int index) {
    String item = "item " + index + " (" + expected.code + ")";
    assertEquals(expected.code, actual.code, item);
    assertEquals(expected.flags, actual.flags, item);
    assertEquals(expected.version, actual.version, item);
    assertEquals(expected.location, actual.location, item);
    assertEquals(expected.bodyLoc, actual.bodyLoc, item);
    assertEquals(expected.storeLoc, actual.storeLoc, item);
    assertEquals(expected.gridX, actual.gridX, item);
    assertEquals(expected.gridY, actual.gridY, item);
    assertEquals(expected.socketsFilled, actual.socketsFilled, item);
    assertEquals(expected.id, actual.id, item);
    assertEquals(expected.ilvl, actual.ilvl, item);
    assertEquals(expected.quality, actual.quality, item);
    assertEquals(expected.qualityId, actual.qualityId, item);
  }
}
