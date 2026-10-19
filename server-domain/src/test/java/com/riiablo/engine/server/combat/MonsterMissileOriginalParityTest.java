package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Source-derived, not original executable captures. Deliberately explicit audit entrance. */
@Tag("monster-parity-audit")
class MonsterMissileOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"2560,2816,0,2815", "2560,2560,0,2560", "2816,2560,0,2815",
      "2560,2816,75,4926", "2560,2816,-100,282", "128,128,0,128",
      "0,2816,0,0", "1280256,1280256,50,1920384"})
  void nativeMissilePhysicalSamples(int min, int max, int percent, int expected) {
    NativeDamagePacket result = NativeMissilePhysicalDamageTest.resolve(
        NativeMissilePhysicalDamageTest.attacker(min, max, percent),
        new NativeDefenseData(), true, new ArrayList<>());
    assertEquals(expected, result.physicalDamage);
    assertEquals(256, result.physicalScale);
  }
}
