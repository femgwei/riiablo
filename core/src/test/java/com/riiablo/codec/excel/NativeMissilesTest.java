package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NativeMissilesTest {
  @Test
  void preservesCollisionFlagsAsNativeBitAndByteFields() throws Exception {
    NativeMissiles missiles = NativeMissiles.parse(
        ("Missile\tId\tLastCollide\tExplosion\tPierce\tCollision\tCollideKill\t"
            + "NextHit\tRange\tSrvCalc1\tSubMissile1\tSkill\n"
            + "icebolt\t99\t1\t1\t0\t2\t3\t4\t12\t(param1)\tfrostnova\tCold\n")
            .getBytes(StandardCharsets.ISO_8859_1));

    NativeMissiles.Entry entry = missiles.get("ICEBOLT");
    assertEquals(0, entry.id);
    assertEquals(99, entry.txtId);
    assertTrue(entry.bool("LastCollide"));
    assertTrue(entry.bool("Explosion"));
    assertTrue(entry.nonZero("Collision"));
    assertEquals(Integer.valueOf(3), entry.integer("CollideKill"));
    assertEquals("(param1)", entry.string("SrvCalc1"));
    assertEquals("frostnova", entry.string("SubMissile1"));
  }
}
