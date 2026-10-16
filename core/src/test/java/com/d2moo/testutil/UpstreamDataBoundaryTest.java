package com.d2moo.testutil;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.NativeMissiles;
import com.riiablo.codec.excel.NativeMonStats;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Loading evidence for upstream 185ae45/4d45674; not an acceleration engine test. */
class UpstreamDataBoundaryTest extends RiiabloTest {
  private static final String[] MISSILES = {
      "MissA1", "MissA2", "MissS1", "MissS2", "MissS3", "MissS4", "MissC", "MissSQ"
  };

  @Test void blankMissileReferencesStayBlankAcrossAllEightColumns() throws Exception {
    String header = "Id\t" + String.join("\t", MISSILES) + "\n";
    NativeMonStats table = NativeMonStats.parse((header + "empty\t\t\t\t\t\t\t\t\n"
        + "ranged\tarrow\tbolt\ts1\ts2\ts3\ts4\tc\tsq\n")
        .getBytes(StandardCharsets.ISO_8859_1));
    for (String column : MISSILES) assertEquals("", table.get("empty").string(column), column);
    assertEquals("arrow", table.get("ranged").string("MissA1"));
    assertEquals("sq", table.get("ranged").string("MissSQ"));
  }

  @Test void signedAccelerationSurvivesShortBoundariesAndMissingValues() throws Exception {
    NativeMissiles table = NativeMissiles.parse(("Missile\tAccel\n"
        + "min\t-32768\nnegative\t-1\nzero\t0\nmax\t32767\nblank\t\n")
        .getBytes(StandardCharsets.ISO_8859_1));
    assertEquals(-32768, table.get("min").nativeInteger("Accel"));
    assertEquals(-1, table.get("negative").nativeInteger("Accel"));
    assertEquals(0, table.get("zero").nativeInteger("Accel"));
    assertEquals(32767, table.get("max").nativeInteger("Accel"));
    assertEquals(0, table.get("blank").nativeInteger("Accel"));
  }

  @Test void runtimeMonsterReferencesMatchOriginalTxtIncludingEmptyCells() throws Exception {
    NativeMonStats source = NativeMonStats.parse(
        Riiablo.mpqs.resolve("data\\global\\excel\\monstats.txt").readBytes());
    int rows = 0, empty = 0, named = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      NativeMonStats.Entry original = source.get(row.Id);
      if (original == null) continue;
      rows++;
      for (String column : MISSILES) {
        String value = (String) MonStats.Entry.class.getField(column).get(row);
        assertEquals(original.string(column), value, row.Id + "." + column);
        if (value.isEmpty()) empty++; else named++;
      }
    }
    assertTrue(rows > 100);
    assertTrue(empty > 0);
    assertTrue(named > 0);
  }

  @Test void runtimeMissileAccelerationMatchesSignedOriginalTxt() throws Exception {
    NativeMissiles source = NativeMissiles.parse(
        Riiablo.mpqs.resolve("data\\global\\excel\\missiles.txt").readBytes());
    int rows = 0;
    for (Missiles.Entry row : Riiablo.files.Missiles) {
      NativeMissiles.Entry original = source.get(row.Missile);
      if (original == null) continue;
      assertEquals(original.nativeInteger("Accel"), row.Accel, row.Missile);
      rows++;
    }
    assertTrue(rows > 100);
  }
}
